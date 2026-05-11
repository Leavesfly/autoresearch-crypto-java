package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 趋势跟踪策略。
 * 使用长周期 EMA 确定趋势方向，短周期 EMA 作为回调入场信号。
 * 在上升趋势中：当价格回调至 pullMA 时做多。
 * 在下降趋势中：当价格反弹至 pullMA 时做空。
 * 出场：ATR 追踪止损 + MA 反转 + 超时。
 */
public class TrendFollowStrategy extends BaseStrategy {

    private final int longMaPeriod;
    private final int pullMaPeriod;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final double entryZone;
    private final boolean enableShort;

    public TrendFollowStrategy(Map<String, Object> params) {
        super(params);
        this.longMaPeriod = getInt("longMaPeriod", 100);
        this.pullMaPeriod = getInt("pullMaPeriod", 20);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.0);
        this.maxHoldBars = getInt("maxHoldBars", 24);
        this.entryZone = getDouble("entryZone", 0.002);
        this.enableShort = getBool("enableShort", true);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShortParam) {
        int length = data.length();
        int[] signals = new int[length];

        if (length < Math.max(longMaPeriod, Math.max(pullMaPeriod, atrPeriod))) {
            for (int i = 0; i < length; i++) {
                signals[i] = 0;
            }
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();

        // 计算指标
        double[] longMa = Indicators.computeEma(close, longMaPeriod);
        double[] pullMa = Indicators.computeEma(close, pullMaPeriod);
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);

        // 跟踪仓位状态
        int currentPosition = 0; // 0=平仓, 2=做多, 3=做空
        int entryBar = -1;
        double entryPrice = 0;
        double stopLoss = 0;

        for (int i = Math.max(longMaPeriod, Math.max(pullMaPeriod, atrPeriod)); i < length; i++) {
            double price = close[i];
            double currentAtr = atr[i];
            boolean canShort = enableShort && enableShortParam;

            // 从长周期 MA 确定趋势方向
            boolean isUptrend = price > longMa[i];
            boolean isDowntrend = price < longMa[i];

            // 检查出场条件
            if (currentPosition == 2) { // 做多仓位
                boolean shouldExit = false;

                // ATR 追踪止损
                double trailStop = entryPrice - atrMultiplier * currentAtr;
                if (price < trailStop) {
                    shouldExit = true;
                }

                // MA 反转（价格跌破 pullMA）
                if (price < pullMa[i]) {
                    shouldExit = true;
                }

                // 超时
                if (entryBar >= 0 && (i - entryBar) >= maxHoldBars) {
                    shouldExit = true;
                }

                if (shouldExit) {
                    signals[i] = 0; // 平仓
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

                // ATR 追踪止损
                double trailStop = entryPrice + atrMultiplier * currentAtr;
                if (price > trailStop) {
                    shouldExit = true;
                }

                // MA 反转（价格突破 pullMA）
                if (price > pullMa[i]) {
                    shouldExit = true;
                }

                // 超时
                if (entryBar >= 0 && (i - entryBar) >= maxHoldBars) {
                    shouldExit = true;
                }

                if (shouldExit) {
                    signals[i] = 0; // 平仓
                    currentPosition = 0;
                    entryBar = -1;
                    continue;
                } else {
                    signals[i] = 1; // 持有空头
                    continue;
                }
            }

            // 入场逻辑
            if (currentPosition == 0) {
                // 上升趋势：寻找回调入场
                if (isUptrend) {
                    double pullbackZone = pullMa[i] * (1 + entryZone);
                    if (price <= pullbackZone && price >= pullMa[i] * (1 - entryZone)) {
                        signals[i] = 2; // 做多信号
                        currentPosition = 2;
                        entryBar = i;
                        entryPrice = price;
                        stopLoss = price - atrMultiplier * currentAtr;
                        continue;
                    }
                }

                // 下降趋势：寻找反弹入场（如果允许做空）
                if (isDowntrend && canShort) {
                    double rallyZone = pullMa[i] * (1 - entryZone);
                    if (price >= rallyZone && price <= pullMa[i] * (1 + entryZone)) {
                        signals[i] = 3; // 做空信号
                        currentPosition = 3;
                        entryBar = i;
                        entryPrice = price;
                        stopLoss = price + atrMultiplier * currentAtr;
                        continue;
                    }
                }
            }

            signals[i] = 0; // 平仓
        }

        // 初始K线填充平仓信号
        for (int i = 0; i < Math.max(longMaPeriod, Math.max(pullMaPeriod, atrPeriod)); i++) {
            signals[i] = 0;
        }

        return signals;
    }

    @Override
    public String name() {
        return "TrendFollow";
    }
}
