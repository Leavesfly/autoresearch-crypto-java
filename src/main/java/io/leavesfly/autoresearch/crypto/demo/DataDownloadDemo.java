package io.leavesfly.autoresearch.crypto.demo;

import io.leavesfly.autoresearch.crypto.data.DataDownloader;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;

import java.nio.file.Path;

/**
 * 数据下载 Demo：演示如何从 Binance 下载 K 线数据并加载。
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="demo.io.leavesfly.autoresearch.crypto.DataDownloadDemo"
 * </pre>
 *
 * <p>注意：需要网络连接，数据来自 Binance 公开 API。
 */
public class DataDownloadDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - 数据下载 Demo");
        System.out.println("═══════════════════════════════════════════════════");

        String symbol = args.length > 0 ? args[0] : "ETHUSDT";
        String interval = args.length > 1 ? args[1] : "5m";
        int days = args.length > 2 ? Integer.parseInt(args[2]) : 30;

        System.out.printf("[配置] 交易对=%s, 周期=%s, 天数=%d%n%n", symbol, interval, days);

        // 1. 下载数据
        System.out.println("── 开始下载K线数据 ──");
        DataDownloader downloader = new DataDownloader();
        Path dataFile = downloader.download(symbol, interval, days, false);
        System.out.printf("[完成] 数据已保存至: %s%n%n", dataFile);

        // 2. 加载并验证
        System.out.println("── 加载并验证数据 ──");
        MarketData data = MarketDataLoader.loadCsv(dataFile);

        int length = data.length();
        double firstClose = data.close()[0];
        double lastClose = data.close()[length - 1];
        double priceChange = (lastClose - firstClose) / firstClose * 100;

        System.out.printf("  K线总数:     %d%n", length);
        System.out.printf("  起始价格:    %.2f%n", firstClose);
        System.out.printf("  最新价格:    %.2f%n", lastClose);
        System.out.printf("  区间涨跌幅:  %.2f%%%n", priceChange);
        System.out.printf("  预期K线数:   %d (每天 %d 根 × %d 天)%n",
                days * dailyBars(interval), dailyBars(interval), days);

        // 3. 数据质量检查
        System.out.println("\n── 数据质量检查 ──");
        int missingCount = 0;
        long expectedIntervalMs = intervalToMs(interval);
        for (int i = 1; i < length; i++) {
            long gap = data.timestamps()[i] - data.timestamps()[i - 1];
            if (gap > expectedIntervalMs * 1.5) {
                missingCount++;
            }
        }
        double completeness = 1.0 - (double) missingCount / length;
        System.out.printf("  数据完整度:  %.1f%% (%d 处间断)%n", completeness * 100, missingCount);
        System.out.println(completeness > 0.99 ? "  ✅ 数据质量良好" : "  ⚠️  存在数据缺口，建议检查");
    }

    private static int dailyBars(String interval) {
        return switch (interval) {
            case "1m" -> 1440;
            case "5m" -> 288;
            case "15m" -> 96;
            case "1h" -> 24;
            case "4h" -> 6;
            case "1d" -> 1;
            default -> 288;
        };
    }

    private static long intervalToMs(String interval) {
        return switch (interval) {
            case "1m" -> 60_000L;
            case "5m" -> 300_000L;
            case "15m" -> 900_000L;
            case "1h" -> 3_600_000L;
            case "4h" -> 14_400_000L;
            case "1d" -> 86_400_000L;
            default -> 300_000L;
        };
    }
}
