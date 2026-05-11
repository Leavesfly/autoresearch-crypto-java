package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 剥头皮策略 - 使用紧布林带进行均值回归。
 * 
 * 入场逻辑：
 * - 做多信号：价格触及或跌破下布林带。
 * - 做空信号：价格触及或突破上布林带。
 * - 可选 RSI 极值过滤以避免在强趋势中入场。
 * 
 * 出场逻辑：
 * - 固定止盈（0.5%）
 * - 固定止损（0.3%）
 * - 超时出场（maxHoldBars，默认 6 根K线）
 */
public class ScalpStrategy extends BaseStrategy {

    private final int window;
    private final double stdDev;
    private final double takeProfitPct;
    private final double stopLossPct;
    private final int maxHoldBars;
    private final int rsiPeriod;
    private final boolean useRsiFilter;
    private final int rsiOversold;
    private final int rsiOverbought;

    public ScalpStrategy(Map<String, Object> params) {
        super(params);
        this.window = getInt("window", 10);
        this.stdDev = getDouble("stdDev", 1.2);
        this.takeProfitPct = getDouble("takeProfitPct", 0.005);
        this.stopLossPct = getDouble("stopLossPct", 0.003);
        this.maxHoldBars = getInt("maxHoldBars", 6);
        this.rsiPeriod = getInt("rsiPeriod", 14);
        this.useRsiFilter = getBool("useRsiFilter", false);
        this.rsiOversold = getInt("rsiOversold", 30);
        this.rsiOverbought = getInt("rsiOverbought", 70);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];

        if (length < Math.max(window, rsiPeriod)) {
            return signals;
        }

        double[] close = data.close();

        // 计算布林带
        double[][] bb = Indicators.computeBollingerBands(close, window, stdDev);
        double[] upperBand = bb[0];
        double[] middleBand = bb[1];
        double[] lowerBand = bb[2];

        // 如果启用过滤则计算 RSI
        double[] rsi = null;
        if (useRsiFilter) {
            rsi = Indicators.computeRsi(close, rsiPeriod);
        }

        // 跟踪仓位状态
        int position = 0; // 0=平仓, 1=做多, -1=做空
        int holdBars = 0;
        double entryPrice = 0;

        for (int i = Math.max(window, rsiPeriod); i < length; i++) {
            double price = close[i];
            double prevPrice = close[i - 1];
            double upper = upperBand[i];
            double lower = lowerBand[i];

            boolean canShort = enableShort;

            // 检查出场条件
            if (position != 0) {
                boolean shouldExit = false;

                // 超时出场
                if (holdBars >= maxHoldBars) {
                    shouldExit = true;
                }

                if (position == 1) {
                    // 做多仓位
                    // 止盈
                    if (price >= entryPrice * (1 + takeProfitPct)) {
                        shouldExit = true;
                    }
                    // 止损
                    if (price <= entryPrice * (1 - stopLossPct)) {
                        shouldExit = true;
                    }
                    // 当价格回归中线时出场（均值回归目标）
                    if (price >= middleBand[i]) {
                        shouldExit = true;
                    }
                } else if (position == -1) {
                    // 做空仓位
                    // 止盈
                    if (price <= entryPrice * (1 - takeProfitPct)) {
                        shouldExit = true;
                    }
                    // Stop loss
                    if (price >= entryPrice * (1 + stopLossPct)) {
                        shouldExit = true;
                    }
                    // Exit when price returns to middle band (mean reversion target)
                    if (price <= middleBand[i]) {
                        shouldExit = true;
                    }
                }

                if (shouldExit) {
                    signals[i] = 0; // 平仓
                    position = 0;
                    holdBars = 0;
                    continue;
                }

                // 持仓
                signals[i] = 1;
                holdBars++;
                continue;
            }

            // 检查入场条件
            boolean longSignal = false;
            boolean shortSignal = false;

            // 做多：价格触及或跌破下轨
            if (price <= lower && prevPrice > lowerBand[i - 1]) {
                // RSI 过滤：仅在 RSI 超卖时入场
                if (!useRsiFilter || (rsi != null && rsi[i] <= rsiOversold)) {
                    longSignal = true;
                }
            }

            // 做空：价格触及或突破上轨
            if (canShort && price >= upper && prevPrice < upperBand[i - 1]) {
                // RSI 过滤：仅在 RSI 超买时入场
                if (!useRsiFilter || (rsi != null && rsi[i] >= rsiOverbought)) {
                    shortSignal = true;
                }
            }

            if (longSignal) {
                signals[i] = 2; // 做多
                position = 1;
                entryPrice = price;
                holdBars = 1;
            } else if (shortSignal) {
                signals[i] = 3; // 做空
                position = -1;
                entryPrice = price;
                holdBars = 1;
            } else {
                signals[i] = 0; // 平仓
            }
        }

        return signals;
    }

    @Override
    public String name() {
        return "Scalp";
    }
}
