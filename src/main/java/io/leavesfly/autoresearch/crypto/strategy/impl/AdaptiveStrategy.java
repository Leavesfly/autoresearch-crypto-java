package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 自适应策略，在均值回归和趋势跟踪模式之间切换。
 * 使用 ADX 确定市场状态：
 * - ADX <= threshold: 震荡模式（RSI 均值回归）
 * - ADX > threshold: 趋势模式（EMA 趋势跟踪）
 * 出场：ATR 追踪止损 + EMA 反转 + 超时。
 */
public class AdaptiveStrategy extends BaseStrategy {

    private final int rsiPeriod;
    private final double rsiLow;
    private final double rsiHigh;
    private final int maPeriod;
    private final int trendLongMa;
    private final int trendPullMa;
    private final int adxPeriod;
    private final double adxThreshold;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final boolean enableShort;

    public AdaptiveStrategy(Map<String, Object> params) {
        super(params);
        this.rsiPeriod = getInt("rsiPeriod", 14);
        this.rsiLow = getDouble("rsiLow", 30);
        this.rsiHigh = getDouble("rsiHigh", 70);
        this.maPeriod = getInt("maPeriod", 20);
        this.trendLongMa = getInt("trendLongMa", 100);
        this.trendPullMa = getInt("trendPullMa", 20);
        this.adxPeriod = getInt("adxPeriod", 14);
        this.adxThreshold = getDouble("adxThreshold", 25);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.0);
        this.maxHoldBars = getInt("maxHoldBars", 24);
        this.enableShort = getBool("enableShort", true);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShortParam) {
        int length = data.length();
        int[] signals = new int[length];

        int warmupPeriod = Math.max(Math.max(rsiPeriod, adxPeriod), Math.max(trendLongMa, atrPeriod));
        if (length < warmupPeriod) {
            for (int i = 0; i < length; i++) {
                signals[i] = 0;
            }
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();

        // 计算指标
        double[] rsi = Indicators.computeRsi(close, rsiPeriod);
        double[][] adxResult = Indicators.computeAdx(high, low, close, adxPeriod);
        double[] adx = adxResult[0];
        double[] emaMid = Indicators.computeEma(close, maPeriod);
        double[] emaLong = Indicators.computeEma(close, trendLongMa);
        double[] emaPull = Indicators.computeEma(close, trendPullMa);
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);

        // 跟踪仓位状态
        int currentPosition = 0; // 0=平仓, 2=做多, 3=做空
        int entryBar = -1;
        double entryPrice = 0;

        for (int i = warmupPeriod; i < length; i++) {
            double price = close[i];
            double currentAtr = atr[i];
            double currentAdx = adx[i];
            boolean canShort = enableShort && enableShortParam;

            // 确定市场状态
            boolean isRanging = currentAdx <= adxThreshold;
            boolean isTrending = currentAdx > adxThreshold;

            // 检查出场条件
            if (currentPosition == 2) { // 做多仓位
                boolean shouldExit = false;

                // ATR trailing stop
                double trailStop = entryPrice - atrMultiplier * currentAtr;
                if (price < trailStop) {
                    shouldExit = true;
                }

                // EMA 反转（在趋势模式中，检查 pull_ma 下穿 mid_ma）
                if (isTrending && emaPull[i] < emaMid[i]) {
                    shouldExit = true;
                }

                // Timeout
                if (entryBar >= 0 && (i - entryBar) >= maxHoldBars) {
                    shouldExit = true;
                }

                if (shouldExit) {
                    signals[i] = 0; // Flatten
                    currentPosition = 0;
                    entryBar = -1;
                    continue;
                } else {
                    signals[i] = 1; // 持有多头
                    continue;
                }
            }

            if (currentPosition == 3) { // 做空仓位
                boolean shouldExit = false;

                // ATR trailing stop
                double trailStop = entryPrice + atrMultiplier * currentAtr;
                if (price > trailStop) {
                    shouldExit = true;
                }

                // EMA 反转（在趋势模式中，检查 pull_ma 上穿 mid_ma）
                if (isTrending && emaPull[i] > emaMid[i]) {
                    shouldExit = true;
                }

                // Timeout
                if (entryBar >= 0 && (i - entryBar) >= maxHoldBars) {
                    shouldExit = true;
                }

                if (shouldExit) {
                    signals[i] = 0; // Flatten
                    currentPosition = 0;
                    entryBar = -1;
                    continue;
                } else {
                    signals[i] = 1; // 持有空头
                    continue;
                }
            }

            // 根据市场状态的入场逻辑
            if (currentPosition == 0) {
                if (isRanging) {
                    // 震荡模式：RSI 均值回归
                    // 当 RSI < rsiLow 且上升时做多
                    if (rsi[i] < rsiLow && i > 0 && rsi[i] > rsi[i - 1]) {
                        signals[i] = 2; // Long signal
                        currentPosition = 2;
                        entryBar = i;
                        entryPrice = price;
                        continue;
                    }

                    // 当 RSI > rsiHigh 且下降时做空（如果允许做空）
                    if (canShort && rsi[i] > rsiHigh && i > 0 && rsi[i] < rsi[i - 1]) {
                        signals[i] = 3; // Short signal
                        currentPosition = 3;
                        entryBar = i;
                        entryPrice = price;
                        continue;
                    }
                } else {
                    // 趋势模式：EMA 趋势跟踪
                    // 当 pull_ma > mid_ma 时（上升趋势）
                    if (emaPull[i] > emaMid[i]) {
                        signals[i] = 2; // Long signal
                        currentPosition = 2;
                        entryBar = i;
                        entryPrice = price;
                        continue;
                    }

                    // 当 pull_ma < mid_ma 时（下降趋势，如果允许做空）
                    if (canShort && emaPull[i] < emaMid[i]) {
                        signals[i] = 3; // Short signal
                        currentPosition = 3;
                        entryBar = i;
                        entryPrice = price;
                        continue;
                    }
                }
            }

            signals[i] = 0; // Flat
        }

        // 初始K线填充平仓信号
        for (int i = 0; i < warmupPeriod; i++) {
            signals[i] = 0;
        }

        return signals;
    }

    @Override
    public String name() {
        return "Adaptive";
    }
}
