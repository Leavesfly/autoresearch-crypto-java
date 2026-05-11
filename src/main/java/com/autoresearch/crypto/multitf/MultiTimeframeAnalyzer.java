package com.autoresearch.crypto.multitf;

import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.indicator.Indicators;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * 多时间框架趋势分析器。
 * 将5分钟K线重采样到更高时间框架（1h, 4h, 1d），
 * 计算各时间框架的EMA趋势方向，综合得出共识信号。
 *
 * 共识规则：
 *   1 = 看多共识（多数时间框架均看多）
 *  -1 = 看空共识（多数时间框架均看空）
 *   0 = 中性/分歧
 */
public class MultiTimeframeAnalyzer {

    private static final Logger logger = LoggerFactory.getLogger(MultiTimeframeAnalyzer.class);

    /** 各时间框架对应的5分钟K线数量 */
    private static final Map<String, Integer> TF_BARS = Map.of(
            "15m", 3,
            "1h", 12,
            "4h", 48,
            "1d", 288
    );

    private final String[] timeframes;
    private final int maPeriod;
    private final double minConsensus;

    /** 各时间框架的信号缓存，映射回5分钟分辨率 */
    private final Map<String, int[]> signalCache = new HashMap<>();

    public MultiTimeframeAnalyzer(String[] timeframes, int maPeriod, double minConsensus) {
        this.timeframes = timeframes;
        this.maPeriod = maPeriod;
        this.minConsensus = minConsensus;
    }

    /**
     * 使用默认参数创建分析器。
     * 默认时间框架：1h, 4h, 1d；EMA周期：20；共识阈值：0.67
     */
    public MultiTimeframeAnalyzer() {
        this(new String[]{"1h", "4h", "1d"}, 20, 0.67);
    }

    /**
     * 计算多时间框架趋势信号。
     *
     * @param data 5分钟级别的市场数据
     * @return 与输入等长的信号数组：1=看多, -1=看空, 0=中性
     */
    public int[] compute(MarketData data) {
        int length = data.length();
        int[] trend = new int[length];
        signalCache.clear();

        for (String tfName : timeframes) {
            Integer tfBars = TF_BARS.get(tfName);
            if (tfBars == null) {
                logger.warn("未知时间框架: {}，跳过", tfName);
                continue;
            }

            // 重采样到高时间框架
            double[] htfClose = resampleClose(data.close(), tfBars);
            int htfLength = htfClose.length;

            if (htfLength < maPeriod + 1) {
                signalCache.put(tfName, new int[length]);
                continue;
            }

            // 计算高时间框架的 EMA
            double[] emaHtf = Indicators.computeEma(htfClose, maPeriod);

            // 将高时间框架信号映射回5分钟分辨率
            int[] htfSignal5m = new int[length];
            for (int i = maPeriod; i < htfLength; i++) {
                int barIdx = Math.min((i + 1) * tfBars - 1, length - 1);
                int direction = htfClose[i] > emaHtf[i] ? 1 : -1;
                // 从该K线开始向后填充
                for (int j = barIdx; j < length && j < (i + 2) * tfBars - 1; j++) {
                    htfSignal5m[j] = direction;
                }
            }

            signalCache.put(tfName, htfSignal5m);
        }

        // 计算共识：对所有时间框架的信号求和
        for (int i = 0; i < length; i++) {
            int score = 0;
            for (String tf : timeframes) {
                int[] cache = signalCache.get(tf);
                if (cache != null) {
                    score += cache[i];
                }
            }

            int numTf = timeframes.length;
            if (score >= numTf * minConsensus) {
                trend[i] = 1;  // 看多共识
            } else if (score <= -numTf * minConsensus) {
                trend[i] = -1; // 看空共识
            }
            // 否则保持 0（中性）
        }

        return trend;
    }

    /**
     * 生成人类可读的多时间框架状态描述。
     */
    public String describe(MarketData data) {
        int[] trend = compute(data);
        int lastSignal = trend[trend.length - 1];

        StringBuilder details = new StringBuilder();
        for (String tf : timeframes) {
            int[] cache = signalCache.get(tf);
            if (cache != null) {
                int val = cache[cache.length - 1];
                if (!details.isEmpty()) details.append(", ");
                details.append(String.format("%s=%+d", tf, val));
            }
        }

        String label = switch (lastSignal) {
            case 1 -> "看多";
            case -1 -> "看空";
            default -> "中性";
        };

        return String.format("多时间框架: %s (%s)", label, details);
    }

    /**
     * 将5分钟收盘价重采样到高时间框架。
     * 取每个窗口的最后一个收盘价作为高时间框架收盘价。
     */
    private double[] resampleClose(double[] close, int tfBars) {
        int htfLength = close.length / tfBars;
        if (htfLength < 1) return new double[0];

        double[] htfClose = new double[htfLength];
        for (int i = 0; i < htfLength; i++) {
            int endIdx = Math.min((i + 1) * tfBars - 1, close.length - 1);
            htfClose[i] = close[endIdx];
        }
        return htfClose;
    }

    /**
     * 将5分钟OHLCV重采样为高时间框架的完整OHLCV数据。
     */
    public static MarketData resampleOhlcv(MarketData data, int tfBars) {
        int length = data.length();
        int htfLength = length / tfBars;
        if (htfLength < 1) return data;

        double[] open = new double[htfLength];
        double[] high = new double[htfLength];
        double[] low = new double[htfLength];
        double[] close = new double[htfLength];
        double[] volume = new double[htfLength];
        long[] timestamps = new long[htfLength];

        for (int i = 0; i < htfLength; i++) {
            int start = i * tfBars;
            int end = Math.min(start + tfBars, length);

            open[i] = data.open()[start];
            close[i] = data.close()[end - 1];
            timestamps[i] = data.timestamps()[end - 1];

            double maxHigh = Double.MIN_VALUE;
            double minLow = Double.MAX_VALUE;
            double sumVol = 0;

            for (int j = start; j < end; j++) {
                if (data.high()[j] > maxHigh) maxHigh = data.high()[j];
                if (data.low()[j] < minLow) minLow = data.low()[j];
                sumVol += data.volume()[j];
            }

            high[i] = maxHigh;
            low[i] = minLow;
            volume[i] = sumVol;
        }

        return new MarketData(open, high, low, close, volume, timestamps);
    }
}
