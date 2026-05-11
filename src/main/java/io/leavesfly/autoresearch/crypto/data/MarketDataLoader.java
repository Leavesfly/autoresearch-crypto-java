package io.leavesfly.autoresearch.crypto.data;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 市场数据加载器，支持 CSV 格式。
 * CSV 列格式：timestamp,open,high,low,close,volume
 */
public class MarketDataLoader {

    private static final Logger logger = LoggerFactory.getLogger(MarketDataLoader.class);

    /**
     * 从 CSV 文件加载市场数据。
     */
    public static MarketData loadCsv(Path filePath) throws IOException {
        List<Long> timestamps = new ArrayList<>();
        List<Double> opens = new ArrayList<>();
        List<Double> highs = new ArrayList<>();
        List<Double> lows = new ArrayList<>();
        List<Double> closes = new ArrayList<>();
        List<Double> volumes = new ArrayList<>();

        try (BufferedReader reader = Files.newBufferedReader(filePath)) {
            String header = reader.readLine();
            if (header == null) {
                throw new IOException("Empty CSV file: " + filePath);
            }

            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                if (parts.length < 6) continue;

                try {
                    timestamps.add(Long.parseLong(parts[0].trim()));
                    opens.add(Double.parseDouble(parts[1].trim()));
                    highs.add(Double.parseDouble(parts[2].trim()));
                    lows.add(Double.parseDouble(parts[3].trim()));
                    closes.add(Double.parseDouble(parts[4].trim()));
                    volumes.add(Double.parseDouble(parts[5].trim()));
                } catch (NumberFormatException e) {
                    logger.warn("跳过格式错误的行: {}", line);
                }
            }
        }

        logger.info("已加载 {} 根K线，来自 {}", closes.size(), filePath.getFileName());

        return new MarketData(
                opens.stream().mapToDouble(Double::doubleValue).toArray(),
                highs.stream().mapToDouble(Double::doubleValue).toArray(),
                lows.stream().mapToDouble(Double::doubleValue).toArray(),
                closes.stream().mapToDouble(Double::doubleValue).toArray(),
                volumes.stream().mapToDouble(Double::doubleValue).toArray(),
                timestamps.stream().mapToLong(Long::longValue).toArray()
        );
    }

    /**
     * 加载指定交易对和周期的默认数据文件。
     */
    public static MarketData loadDefault(String symbol, String interval) throws IOException {
        Path dataFile = TradingConfig.DATA_DIR.resolve(symbol + "_" + interval + ".csv");
        if (!Files.exists(dataFile)) {
            throw new IOException("Data file not found: " + dataFile);
        }
        return loadCsv(dataFile);
    }

    /**
     * 将市场数据切片到指定范围。
     */
    public static MarketData slice(MarketData data, int start, int end) {
        int length = end - start;
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        System.arraycopy(data.open(), start, open, 0, length);
        System.arraycopy(data.high(), start, high, 0, length);
        System.arraycopy(data.low(), start, low, 0, length);
        System.arraycopy(data.close(), start, close, 0, length);
        System.arraycopy(data.volume(), start, volume, 0, length);
        System.arraycopy(data.timestamps(), start, timestamps, 0, length);

        return new MarketData(open, high, low, close, volume, timestamps);
    }
}
