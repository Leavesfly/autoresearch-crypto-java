package io.leavesfly.autoresearch.crypto.regime;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;

/**
 * 市场机制检测器（技术面）。
 * 基于 EMA 交叉和 ADX 指标，将市场分类为趋势/震荡状态。
 * 该类仅使用 K 线数据进行分析，不依赖网络。
 * 多源网络数据检测参见 {@link MultiSourceRegimeDetector}。
 */
public class MarketRegimeDetector {

    /**
     * 基于 OHLCV 数据分析市场机制。
     * 使用 EMA50/EMA200 判断趋势方向，ADX 判断趋势强度，
     * 结合年化波动率给出综合机制判断。
     */
    public static RegimeInfo analyze(MarketData data) {
        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        int length = close.length;

        double[] ema50 = Indicators.computeEma(close, 50);
        double[] ema200 = Indicators.computeEma(close, 200);

        // ADX
        double[][] adxResult = Indicators.computeAdx(high, low, close, 14);
        double adxVal = adxResult[0][length - 1];

        boolean isUptrend = ema50[length - 1] > ema200[length - 1];
        boolean isDowntrend = ema50[length - 1] < ema200[length - 1];
        double priceDev = (close[length - 1] - ema200[length - 1]) / ema200[length - 1] * 100;

        // 7-day annualised volatility
        double[] returns = new double[length - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = close[i] > 0 ? (close[i + 1] - close[i]) / close[i] : 0;
        }
        int volWindow = Math.min(288 * 7, returns.length);
        double mean = 0, sumSq = 0;
        int start = returns.length - volWindow;
        for (int i = start; i < returns.length; i++) {
            mean += returns[i];
        }
        mean /= volWindow;
        for (int i = start; i < returns.length; i++) {
            double diff = returns[i] - mean;
            sumSq += diff * diff;
        }
        double vol = Math.sqrt(sumSq / volWindow) * Math.sqrt(288.0 * 365.0);

        // Classify
        MarketRegime regime;
        if (adxVal > 25) {
            if (isUptrend) regime = MarketRegime.STRONG_UPTREND;
            else if (isDowntrend) regime = MarketRegime.STRONG_DOWNTREND;
            else regime = priceDev > 0 ? MarketRegime.STRONG_UPTREND : MarketRegime.STRONG_DOWNTREND;
        } else if (adxVal > 15) {
            if (isUptrend) regime = MarketRegime.WEAK_UPTREND;
            else if (isDowntrend) regime = MarketRegime.WEAK_DOWNTREND;
            else regime = priceDev > 0 ? MarketRegime.WEAK_UPTREND : MarketRegime.WEAK_DOWNTREND;
        } else {
            regime = MarketRegime.RANGING;
        }

        String emaRelation = isUptrend ? "uptrend" : isDowntrend ? "downtrend" : "neutral";
        return new RegimeInfo(regime, adxVal, emaRelation, priceDev, vol);
    }
}
