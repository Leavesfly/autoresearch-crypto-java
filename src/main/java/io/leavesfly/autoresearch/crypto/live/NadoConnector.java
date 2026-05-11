package io.leavesfly.autoresearch.crypto.live;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.concurrent.TimeUnit;

/**
 * Nado DEX 交易所连接器。
 * 实现与 Nado 永续合约 API 的交互：获取行情、下单、查询持仓。
 *
 * Nado 特点：
 * - 使用 POST_ONLY 挂单获取 Maker 手续费
 * - 使用 IOC 市价单执行止损
 * - 支持 ETH/SOL 等永续合约
 */
public class NadoConnector implements ExchangeConnector {

    private static final Logger logger = LoggerFactory.getLogger(NadoConnector.class);

    private final String baseUrl;
    private final String apiKey;
    private final String apiSecret;
    private final String ticker;
    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;

    public NadoConnector(String baseUrl, String apiKey, String apiSecret, String ticker) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.apiSecret = apiSecret;
        this.ticker = ticker;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
    }

    @Override
    public String exchangeName() {
        return "Nado DEX";
    }

    @Override
    public MarketData fetchLatestData(String interval, int limit) {
        // 从 Nado API 获取 K 线数据
        try {
            String url = String.format("%s/api/v1/klines?ticker=%s&interval=%s&limit=%d",
                    baseUrl, ticker, interval, limit);
            Request request = new Request.Builder().url(url)
                    .header("X-API-KEY", apiKey).build();

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
            String url = String.format("%s/api/v1/ticker?ticker=%s", baseUrl, ticker);
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode node = objectMapper.readTree(response.body().string());
                    return node.path("last_price").asDouble();
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
            String url = String.format("%s/api/v1/orderbook?ticker=%s", baseUrl, ticker);
            Request request = new Request.Builder().url(url).build();
            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode node = objectMapper.readTree(response.body().string());
                    double bid = node.path("best_bid").asDouble();
                    double ask = node.path("best_ask").asDouble();
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
        return placeOrder("buy", price, notional, "POST_ONLY");
    }

    @Override
    public boolean openShort(double price, double notional) {
        return placeOrder("sell", price, notional, "POST_ONLY");
    }

    @Override
    public boolean closePosition(double price) {
        // 使用 IOC 市价单平仓
        PositionInfo pos = queryPosition();
        if (pos.side() == 0) return true;

        String side = pos.side() == 1 ? "sell" : "buy";
        return placeOrder(side, price, pos.size() * price, "IOC");
    }

    @Override
    public PositionInfo queryPosition() {
        try {
            String url = String.format("%s/api/v1/position?ticker=%s", baseUrl, ticker);
            String signature = sign(url);
            Request request = new Request.Builder().url(url)
                    .header("X-API-KEY", apiKey)
                    .header("X-SIGNATURE", signature)
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    JsonNode node = objectMapper.readTree(response.body().string());
                    int side = node.path("side").asInt();
                    double size = node.path("size").asDouble();
                    double entry = node.path("entry_price").asDouble();
                    double pnl = node.path("unrealized_pnl").asDouble();
                    return new PositionInfo(side, size, entry, pnl);
                }
            }
        } catch (Exception e) {
            logger.error("查询持仓失败: {}", e.getMessage());
        }
        return PositionInfo.empty();
    }

    /** 下单 */
    private boolean placeOrder(String side, double price, double notional, String orderType) {
        try {
            String body = objectMapper.writeValueAsString(java.util.Map.of(
                    "ticker", ticker,
                    "side", side,
                    "price", price,
                    "notional", notional,
                    "order_type", orderType
            ));

            String signature = sign(body);
            Request request = new Request.Builder()
                    .url(baseUrl + "/api/v1/order")
                    .header("X-API-KEY", apiKey)
                    .header("X-SIGNATURE", signature)
                    .header("Content-Type", "application/json")
                    .post(RequestBody.create(body, MediaType.parse("application/json")))
                    .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (response.isSuccessful()) {
                    logger.info("下单成功: {} {} @ {}", side, orderType, String.format("%.2f", price));
                    return true;
                } else {
                    logger.error("下单失败: HTTP {} - {}", response.code(), response.body().string());
                }
            }
        } catch (Exception e) {
            logger.error("下单异常: {}", e.getMessage());
        }
        return false;
    }

    /** 生成请求签名 */
    private String sign(String payload) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String data = payload + apiSecret;
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : hash) {
                hex.append(String.format("%02x", b));
            }
            return hex.toString();
        } catch (Exception e) {
            return "";
        }
    }

    /** 解析 K 线响应 */
    private MarketData parseKlineResponse(String json) throws IOException {
        JsonNode root = objectMapper.readTree(json);
        JsonNode data = root.path("data");
        int length = data.size();
        if (length == 0) return null;

        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        for (int i = 0; i < length; i++) {
            JsonNode candle = data.get(i);
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
