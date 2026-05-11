package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 混合均值回归 + 动量策略。
 * 
 * 入场逻辑：
 * - 做多信号：RSI 进入超卖区（< rsiLow）然后回升穿过阈值，
 *   且价格 > EMA 快线。
 * - 做空信号：RSI 进入超买区（> rsiHigh）然后回落穿过阈值，
 *   且价格 < EMA 快线。
 * 
 * 出场逻辑：
 * - ATR 追踪止损
 * - MA 反转
 * - 超时出场（maxHoldBars）
 */
public class HybridMeanRevMomentumStrategy extends BaseStrategy {

    private final int rsiPeriod;
    private final int rsiLow;
    private final int rsiHigh;
    private final int maPeriod;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final boolean enableShort;
    private final double takeProfitPct;
    private final double stopLossPct;

    public HybridMeanRevMomentumStrategy(Map<String, Object> params) {
        super(params);
        this.rsiPeriod = getInt("rsiPeriod", 14);
        this.rsiLow = getInt("rsiLow", 25);
        this.rsiHigh = getInt("rsiHigh", 75);
        this.maPeriod = getInt("maPeriod", 20);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.0);
        this.maxHoldBars = getInt("maxHoldBars", 24);
        this.enableShort = getBool("enableShort", true);
        this.takeProfitPct = getDouble("takeProfitPct", 0.03);
        this.stopLossPct = getDouble("stopLossPct", 0.02);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];
        
        if (length < Math.max(rsiPeriod, Math.max(maPeriod, atrPeriod))) {
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();

        // 计算指标
        double[] rsi = Indicators.computeRsi(close, rsiPeriod);
        double[] emaFast = Indicators.computeEma(close, maPeriod);
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);

        // 跟踪仓位状态
        int position = 0; // 0=平仓, 1=做多, -1=做空
        int holdBars = 0;
        double entryPrice = 0;
        double stopPrice = 0;

        for (int i = Math.max(rsiPeriod, Math.max(maPeriod, atrPeriod)); i < length; i++) {
            double price = close[i];
            double emaVal = emaFast[i];
            double rsiVal = rsi[i];
            double atrVal = atr[i];

            boolean canShort = enableShort && this.enableShort;

            // 检查出场条件
            if (position != 0) {
                boolean shouldExit = false;

                // 超时出场
                if (holdBars >= maxHoldBars) {
                    shouldExit = true;
                }

                // ATR 追踪止损
                if (position == 1) {
                    // 做多仓位：止损在价格下方
                    double currentStop = price - atrMultiplier * atrVal;
                    if (currentStop > stopPrice) {
                        stopPrice = currentStop;
                    }
                    if (price <= stopPrice) {
                        shouldExit = true;
                    }
                    // 止盈
                    // 止损
                    // MA 反转：价格跌破 EMA
                } else if (position == -1) {
                    // 做空仓位：止损在价格上方
                    double currentStop = price + atrMultiplier * atrVal;
                    if (currentStop < stopPrice) {
                        stopPrice = currentStop;
                    }
                    if (price >= stopPrice) {
                        shouldExit = true;
                    }
                    // 止盈
                    if (price <= entryPrice * (1 - takeProfitPct)) {
                        shouldExit = true;
                    }
                    // 止损
                    if (price >= entryPrice * (1 + stopLossPct)) {
                        shouldExit = true;
                    }
                    // MA 反转：价格突破 EMA
                    if (price > emaVal && close[i - 1] <= emaFast[i - 1]) {
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

            // 做多：RSI 曾在超卖区且现在回升穿过 rsiLow
            if (i > 0 && rsi[i - 1] < rsiLow && rsiVal >= rsiLow && price > emaVal) {
                longSignal = true;
            }

            // 做空：RSI 曾在超买区且现在回落穿过 rsiHigh
            if (canShort && i > 0 && rsi[i - 1] > rsiHigh && rsiVal <= rsiHigh && price < emaVal) {
                shortSignal = true;
            }

            if (longSignal) {
                signals[i] = 2; // 做多
                position = 1;
                entryPrice = price;
                stopPrice = price - atrMultiplier * atrVal;
                holdBars = 1;
            } else if (shortSignal) {
                signals[i] = 3; // 做空
                position = -1;
                entryPrice = price;
                stopPrice = price + atrMultiplier * atrVal;
                holdBars = 1;
            } else {
                signals[i] = 0; // 平仓
            }
        }

        return signals;
    }

    @Override
    public String name() {
        return "HybridMeanRevMomentum";
    }
}
