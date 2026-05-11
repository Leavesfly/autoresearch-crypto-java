package io.leavesfly.autoresearch.crypto.data;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 加密货币数据下载器。
 * 从 Binance 公开 API 下载 K 线数据并保存为 CSV 格式。
 *
 * 数据存储在项目目录下的 data/crypto/，文件名格式：{symbol}_{interval}_{days}d.csv
 */
public class DataDownloader {

    private static final Logger logger = LoggerFactory.getLogger(DataDownloader.class);

    /** Binance K 线 API 端点 */
    private static final String BINANCE_KLINE_URL = "https://api.binance.com/api/v3/klines";
    /** 备用端点（Binance US） */
    private static final String BINANCE_US_KLINE_URL = "https://api.binance.us/api/v3/klines";
    /** 每次请求最大K线条数 */
    private static final int BATCH_SIZE = 1000;
    /** 请求间隔毫秒数（避免被限频） */
    private static final long REQUEST_DELAY_MS = 200;
    /** 最大重试次数 */
    private static final int MAX_RETRIES = 3;

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private String baseUrl;

    public DataDownloader() {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = BINANCE_KLINE_URL;
    }

    /**
     * 下载指定交易对的 K 线数据。
     *
     * @param symbol   交易对，如 "ETHUSDT"
     * @param interval K线周期，如 "5m", "1h"
     * @param days     下载天数
     * @param force    是否强制重新下载
     * @return 保存的文件路径，失败返回 null
     */
    public Path download(String symbol, String interval, int days, boolean force) throws IOException {
        // 确保数据目录存在
        Files.createDirectories(TradingConfig.DATA_DIR);

        Path outputFile = TradingConfig.DATA_DIR.resolve(
                String.format("%s_%s_%dd.csv", symbol, interval, days));

        // 如果文件已存在且不强制下载，跳过
        if (Files.exists(outputFile) && !force) {
            logger.info("{}: {}天数据已存在 ({})，跳过", symbol, days, outputFile.getFileName());
            return outputFile;
        }

        long endTime = System.currentTimeMillis();
        long startTime = endTime - (long) days * 24 * 3600 * 1000;

        logger.info("{}: 使用 Binance API 下载 {} 天 {} 数据...", symbol, days, interval);

        // 下载所有K线
        List<double[]> allCandles = downloadKlines(symbol, interval, startTime, endTime);

        if (allCandles.isEmpty()) {
            logger.warn("{}: 未获取到数据", symbol);
            return null;
        }

        logger.info("  共获取 {} 根K线", allCandles.size());

        // 保存为 CSV
        saveToCsv(allCandles, outputFile);
        logger.info("  保存至 {}", outputFile.getFileName());

        return outputFile;
    }

    /**
     * 从 Binance API 批量下载 K 线数据。
     */
    private List<double[]> downloadKlines(String symbol, String interval,
                                          long startTime, long endTime) {
        List<double[]> allCandles = new ArrayList<>();
        long currentStart = startTime;

        double totalDays = (endTime - startTime) / (24.0 * 3600 * 1000);
        logger.info("    开始下载 {} {} 从 {} 天前...", symbol, interval, String.format("%.1f", totalDays));

        while (currentStart < endTime) {
            List<double[]> batch = fetchBatch(symbol, interval, currentStart, endTime);

            if (batch == null || batch.isEmpty()) {
                break;
            }

            allCandles.addAll(batch);
            logger.info("    +{} (累计 {})", batch.size(), allCandles.size());

            // 更新下次起始时间为最后一根K线时间 + 1ms
            currentStart = (long) batch.get(batch.size() - 1)[0] + 1;

            // 如果返回条数小于批次大小，说明已到末尾
            if (batch.size() < BATCH_SIZE) {
                break;
            }

            // 请求间隔
            sleep(REQUEST_DELAY_MS);
        }

        return allCandles;
    }

    /**
     * 请求单批次 K 线数据（带重试）。
     * 返回格式：[timestamp, open, high, low, close, volume]
     */
    private List<double[]> fetchBatch(String symbol, String interval,
                                      long startTime, long endTime) {
        for (int attempt = 0; attempt < MAX_RETRIES; attempt++) {
            try {
                String url = String.format("%s?symbol=%s&interval=%s&startTime=%d&endTime=%d&limit=%d",
                        baseUrl, symbol, interval, startTime, endTime, BATCH_SIZE);

                Request request = new Request.Builder()
                        .url(url)
                        .header("User-Agent", "autoresearch-crypto-java/1.0")
                        .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    // 遇到地区限制，切换备用端点
                    if (response.code() == 451) {
                        logger.warn("    451 错误，切换备用端点...");
                        baseUrl = BINANCE_US_KLINE_URL;
                        continue;
                    }

                    if (!response.isSuccessful()) {
                        logger.warn("    HTTP {} 错误，重试 {}/{}",
                                response.code(), attempt + 1, MAX_RETRIES);
                        sleep((long) Math.pow(2, attempt) * 1000);
                        continue;
                    }

                    String body = response.body().string();
                    return parseKlineResponse(body, startTime, endTime);
                }
            } catch (Exception e) {
                if (attempt < MAX_RETRIES - 1) {
                    logger.warn("    请求失败: {}，重试 {}/{}", e.getMessage(), attempt + 1, MAX_RETRIES);
                    sleep((long) Math.pow(2, attempt) * 1000);
                } else {
                    logger.error("    请求最终失败: {}", e.getMessage());
                }
            }
        }
        return null;
    }

    /**
     * 解析 Binance K 线 JSON 响应。
     * Binance 返回格式: [open_time, open, high, low, close, volume, close_time, ...]
     */
    private List<double[]> parseKlineResponse(String json, long startTime, long endTime)
            throws IOException {
        JsonNode root = objectMapper.readTree(json);
        List<double[]> candles = new ArrayList<>();

        for (JsonNode node : root) {
            long timestamp = node.get(0).asLong();
            if (timestamp >= startTime && timestamp <= endTime) {
                candles.add(new double[]{
                        timestamp,
                        node.get(1).asDouble(), // open
                        node.get(2).asDouble(), // high
                        node.get(3).asDouble(), // low
                        node.get(4).asDouble(), // close
                        node.get(5).asDouble()  // volume
                });
            }
        }
        return candles;
    }

    /**
     * 将 K 线数据保存为 CSV 文件。
     * 格式：timestamp,open,high,low,close,volume
     */
    private void saveToCsv(List<double[]> candles, Path outputFile) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(outputFile)) {
            writer.write("timestamp,open,high,low,close,volume");
            writer.newLine();
            for (double[] candle : candles) {
                writer.write(String.format("%d,%.8f,%.8f,%.8f,%.8f,%.8f",
                        (long) candle[0], candle[1], candle[2],
                        candle[3], candle[4], candle[5]));
                writer.newLine();
            }
        }
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // -----------------------------------------------------------------------
    // CLI 入口
    // -----------------------------------------------------------------------

    /**
     * 命令行入口：下载加密货币 K 线数据。
     * 用法：java DataDownloader --symbol ETHUSDT --interval 5m --days 60
     */
    public static void main(String[] args) {
        String symbol = null;
        String interval = TradingConfig.DEFAULT_INTERVAL;
        int days = TradingConfig.DEFAULT_START_DAYS;
        boolean force = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--symbol" -> symbol = args[++i];
                case "--interval" -> interval = args[++i];
                case "--days" -> days = Integer.parseInt(args[++i]);
                case "--force" -> force = true;
            }
        }

        List<String> symbols = symbol != null
                ? List.of(symbol)
                : TradingConfig.DEFAULT_SYMBOLS;

        logger.info("数据目录: {}", TradingConfig.DATA_DIR);
        logger.info("下载周期: {}, 从 {} 天前开始", interval, days);
        logger.info("交易对: {}", symbols);

        DataDownloader downloader = new DataDownloader();
        for (String sym : symbols) {
            try {
                downloader.download(sym, interval, days, force);
            } catch (IOException e) {
                logger.error("{}: 下载失败 - {}", sym, e.getMessage());
            }
        }

        logger.info("数据准备完成!");
    }
}
