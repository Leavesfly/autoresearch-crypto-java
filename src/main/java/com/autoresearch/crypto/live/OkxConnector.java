package com.autoresearch.crypto.live;

import com.autoresearch.crypto.data.MarketData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * OKX 交易所连接器。
 * 实现与 OKX V5 API 的交互：获取行情、下单、查询持仓。
 *
 * OKX 特点：
 * - 使用 HMAC-SHA256 签名认证
 * - 支持 POST_ONLY 挂单（Maker）和 IOC 市价单（Taker）
 * - 永续合约交易
 */
public class OkxConnector implements ExchangeConnector {

    private static final Logger logger = LoggerFactory.getLogger(OkxConnector.class);
    private static final String BASE_URL = "https://www.okx.com";

    private final String apiKey;
    private final String apiSecret;
    private final String passphrase;
    private final String instId; // 合约ID，如 "ETH-USDT-SWAP"
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public OkxConnector(String apiKey, String apiSecret, String passphrase, String instId) {
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.passphrase = passphrase;
        this.instId = instId;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "OKX";
    }

    @Override
    public MarketData fetchLatestData(String interval, int limit) {
        try {
            // OKX K线周期映射
            String bar = mapInterval(interval);
            String url = String.format("%s/api/v5/market/candles?instId=%s&bar=%s&limit=%d",
                    BASE_URL, instId, bar, limit);

            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    logger.error("获取K线数据失败: HTTP {}", response.code());
                    return null;
                }
                return parseKlineResponse(response.body().string());
            }
        } catch (Exception e) {
            logger.error("获取K线数据异常: {}", e.getMessage());
            return null;
        }
    }

    @Override
    public double fetchCurrentPrice() {
        try {
            String url = String.format("%s/api/v5/market/ticker?instId=%s", BASE_URL, instId);
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode node = objectMapper.readTree(response.body().string());
                    return node.path("data").get(0).path("last").asDouble();
                }
            }
        } catch (Exception e) {
            logger.error("获取当前价格失败: {}", e.getMessage());
        }
        return 0;
    }

    @Override
    public double[] fetchBestBidAsk() {
        try {
            String url = String.format("%s/api/v5/market/ticker?instId=%s", BASE_URL, instId);
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode data = objectMapper.readTree(response.body().string()).path("data").get(0);
                    double bid = data.path("bidPx").asDouble();
                    double ask = data.path("askPx").asDouble();
                    return new double[]{bid, ask};
                }
            }
        } catch (Exception e) {
            logger.error("获取买卖盘失败: {}", e.getMessage());
        }
        return new double[]{0, 0};
    }

    @Override
    public boolean openLong(double price, double notional) {
        return placeOrder("buy", "long", price, notional);
    }

    @Override
    public boolean openShort(double price, double notional) {
        return placeOrder("sell", "short", price, notional);
    }

    @Override
    public boolean closePosition(double price) {
        PositionInfo pos = queryPosition();
        if (pos.side() == 0) return true;
        String side = pos.side() == 1 ? "sell" : "buy";
        String posSide = pos.side() == 1 ? "long" : "short";
        return placeOrder(side, posSide, price, pos.size() * price);
    }

    @Override
    public PositionInfo queryPosition() {
        try {
            String path = "/api/v5/account/positions?instId=" + instId;
            String timestamp = Instant.now().toString();
            String signature = sign(timestamp, "GET", path, "");

            Request request = new Request.Builder()
                    .url(BASE_URL + path)
                    .header("OK-ACCESS-KEY", apiKey)
                    .header("OK-ACCESS-SIGN", signature)
                    .header("OK-ACCESS-TIMESTAMP", timestamp)
                    .header("OK-ACCESS-PASSPHRASE", passphrase)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode data = objectMapper.readTree(response.body().string()).path("data");
                    if (data.isEmpty()) return PositionInfo.empty();

                    JsonNode pos = data.get(0);
                    String posSide = pos.path("posSide").asText();
                    int side = "long".equals(posSide) ? 1 : "short".equals(posSide) ? -1 : 0;
                    double size = pos.path("pos").asDouble();
                    double entry = pos.path("avgPx").asDouble();
                    double pnl = pos.path("upl").asDouble();
                    return new PositionInfo(side, size, entry, pnl);
                }
            }
        } catch (Exception e) {
            logger.error("查询持仓失败: {}", e.getMessage());
        }
        return PositionInfo.empty();
    }

    /** 下单 */
    private boolean placeOrder(String side, String posSide, double price, double notional) {
        try {
            // 计算合约张数（假设面值为0.01 ETH）
            double contractSize = notional / price;
            String sz = String.valueOf(Math.max(1, (int) contractSize));

            Map<String, String> orderParams = Map.of(
                    "instId", instId,
                    "tdMode", "cross",
                    "side", side,
                    "posSide", posSide,
                    "ordType", "limit",
                    "px", String.format("%.2f", price),
                    "sz", sz
            );

            String body = objectMapper.writeValueAsString(orderParams);
            String path = "/api/v5/trade/order";
            String timestamp = Instant.now().toString();
            String signature = sign(timestamp, "POST", path, body);

            Request request = new Request.Builder()
                    .url(BASE_URL + path)
                    .header("OK-ACCESS-KEY", apiKey)
                    .header("OK-ACCESS-SIGN", signature)
                    .header("OK-ACCESS-TIMESTAMP", timestamp)
                    .header("OK-ACCESS-PASSPHRASE", passphrase)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode result = objectMapper.readTree(response.body().string());
                    String code = result.path("code").asText();
                    if ("0".equals(code)) {
                        logger.info("OKX下单成功: {} {} @ {}", side, posSide, String.format("%.2f", price));
                        return true;
                    }
                    logger.error("OKX下单失败: {}", result.path("msg").asText());
                }
            }
        } catch (Exception e) {
            logger.error("OKX下单异常: {}", e.getMessage());
        }
        return false;
    }

    /** OKX HMAC-SHA256 签名 */
    private String sign(String timestamp, String method, String path, String body) {
        try {
            String preSign = timestamp + method.toUpperCase() + path + body;
            Mac mac = Mac.getInstance("HmacSHA256");
            SecretKeySpec key = new SecretKeySpec(apiSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            mac.init(key);
            byte[] hash = mac.doFinal(preSign.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            logger.error("签名生成失败: {}", e.getMessage());
            return "";
        }
    }

    /** K线周期映射 */
    private String mapInterval(String interval) {
        return switch (interval) {
            case "1m" -> "1m";
            case "5m" -> "5m";
            case "15m" -> "15m";
            case "1h" -> "1H";
            case "4h" -> "4H";
            case "1d" -> "1D";
            default -> "5m";
        };
    }

    /** 解析 OKX K 线响应 */
    private MarketData parseKlineResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.path("data");
        int length = data.size();
        if (length == 0) return null;

        // OKX 返回格式: [ts, o, h, l, c, vol, ...]，按时间倒序
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        for (int i = 0; i < length; i++) {
            // 倒序转正序
            JsonNode candle = data.get(length - 1 - i);
            timestamps[i] = candle.get(0).asLong();
            open[i] = candle.get(1).asDouble();
            high[i] = candle.get(2).asDouble();
            low[i] = candle.get(3).asDouble();
            close[i] = candle.get(4).asDouble();
            volume[i] = candle.get(5).asDouble();
        }

        return new MarketData(open, high, low, close, volume, timestamps);
    }
}
