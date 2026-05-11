package io.leavesfly.autoresearch.crypto.data;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 数据管理工具。
 * 提供 Walk-Forward 分割、数据验证、文件管理等功能。
 */
public class DataManager {

    private static final Logger logger = LoggerFactory.getLogger(DataManager.class);

    /**
     * Walk-Forward 窗口定义。
     * 训练集用于策略优化，验证集用于样本外测试。
     */
    public record WalkForwardWindow(
            MarketData trainData,
            MarketData validData,
            int windowIndex
    ) {}

    /**
     * 将市场数据分割为多个 Walk-Forward 窗口。
     * 每个窗口包含训练集（70%）和验证集（30%）。
     *
     * @param data      完整市场数据
     * @param nWindows  窗口数量（默认3）
     * @return Walk-Forward 窗口列表
     */
    public static List<WalkForwardWindow> walkForwardSplit(MarketData data, int nWindows) {
        int totalBars = data.length();
        int windowSize = totalBars / nWindows;
        int trainSize = (int) (windowSize * 0.7);
        int validSize = windowSize - trainSize;

        List<WalkForwardWindow> windows = new ArrayList<>();

        for (int i = 0; i < nWindows; i++) {
            int windowStart = i * windowSize;
            int trainEnd = windowStart + trainSize;
            int validEnd = Math.min(windowStart + windowSize, totalBars);

            // 训练集
            MarketData trainData = MarketDataLoader.slice(data, windowStart, trainEnd);
            // 验证集
            MarketData validData = MarketDataLoader.slice(data, trainEnd, validEnd);

            windows.add(new WalkForwardWindow(trainData, validData, i));
            logger.debug("窗口 {}: 训练 [{}, {}), 验证 [{}, {})",
                    i, windowStart, trainEnd, trainEnd, validEnd);
        }

        return windows;
    }

    /**
     * 使用默认窗口数进行 Walk-Forward 分割。
     */
    public static List<WalkForwardWindow> walkForwardSplit(MarketData data) {
        return walkForwardSplit(data, TradingConfig.WF_N_WINDOWS);
    }

    /**
     * 验证市场数据的完整性和合理性。
     *
     * @param data 待验证的市场数据
     * @return 验证结果（包含错误信息列表）
     */
    public static ValidationResult validate(MarketData data) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 基础检查：数据不能为空
        if (data == null || data.length() == 0) {
            errors.add("数据为空");
            return new ValidationResult(false, errors, warnings);
        }

        int length = data.length();
        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        double[] open = data.open();
        double[] volume = data.volume();

        // 检查数据长度一致性
        if (high.length != length || low.length != length
                || open.length != length || volume.length != length) {
            errors.add("OHLCV 数组长度不一致");
        }

        // 检查价格合理性
        int invalidPrices = 0;
        int invalidHloc = 0;
        int zeroVolume = 0;

        for (int i = 0; i < length; i++) {
            // 价格必须为正
            if (close[i] <= 0 || high[i] <= 0 || low[i] <= 0 || open[i] <= 0) {
                invalidPrices++;
            }
            // High >= Low 必须成立
            if (high[i] < low[i]) {
                invalidHloc++;
            }
            // 成交量为0可能是问题
            if (volume[i] == 0) {
                zeroVolume++;
            }
        }

        if (invalidPrices > 0) {
            errors.add(String.format("发现 %d 个非正价格", invalidPrices));
        }
        if (invalidHloc > 0) {
            errors.add(String.format("发现 %d 个 High < Low 的异常K线", invalidHloc));
        }
        if (zeroVolume > length * 0.1) {
            warnings.add(String.format("%.1f%% 的K线成交量为0", (double) zeroVolume / length * 100));
        }

        // 检查时间戳递增
        long[] timestamps = data.timestamps();
        if (timestamps != null && timestamps.length == length) {
            int outOfOrder = 0;
            for (int i = 1; i < length; i++) {
                if (timestamps[i] <= timestamps[i - 1]) {
                    outOfOrder++;
                }
            }
            if (outOfOrder > 0) {
                errors.add(String.format("发现 %d 个时间戳乱序", outOfOrder));
            }
        }

        // 检查极端跳价（单根K线涨跌超过20%）
        int extremeJumps = 0;
        for (int i = 1; i < length; i++) {
            double change = Math.abs(close[i] - close[i - 1]) / close[i - 1];
            if (change > 0.20) {
                extremeJumps++;
            }
        }
        if (extremeJumps > 0) {
            warnings.add(String.format("发现 %d 个极端跳价（单K线涨跌>20%%）", extremeJumps));
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    /**
     * 列出数据目录下所有可用的数据文件。
     */
    public static List<Path> listDataFiles() throws IOException {
        Path dataDir = TradingConfig.DATA_DIR;
        if (!Files.exists(dataDir)) {
            return List.of();
        }

        try (Stream<Path> paths = Files.list(dataDir)) {
            return paths.filter(p -> p.toString().endsWith(".csv"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 数据验证结果。
     */
    public record ValidationResult(
            boolean valid,
            List<String> errors,
            List<String> warnings
    ) {
        public void logResults() {
            Logger log = LoggerFactory.getLogger(DataManager.class);
            if (valid) {
                log.info("数据验证通过");
            } else {
                log.error("数据验证失败:");
                errors.forEach(e -> log.error("  [ERROR] {}", e));
            }
            warnings.forEach(w -> log.warn("  [WARN] {}", w));
        }
    }
}
