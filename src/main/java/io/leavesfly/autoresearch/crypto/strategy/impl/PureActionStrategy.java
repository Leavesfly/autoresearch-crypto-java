package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 纯价格行为策略 - 无额外因子过滤的纯价格行为。
 * 
 * 入场：价格触及布林带极端区域 + 连续K线动量检测。
 * 出场：ATR 追踪止损 + MA 反转 + 超时。
 */
public class PureActionStrategy extends BaseStrategy {

    private final int window;
    private final double stdDev;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final double entryZone;
    private final boolean enableShort;

    public PureActionStrategy(Map<String, Object> params) {
        super(params);
        this.window = getInt("window", 20);
        this.stdDev = getDouble("stdDev", 2.0);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.0);
        this.maxHoldBars = getInt("maxHoldBars", 36);
        this.entryZone = getDouble("entryZone", 0.0);
        this.enableShort = getBool("enableShort", true);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShortParam) {
        int length = data.length();
        int[] signals = new int[length];

        if (length < Math.max(window, atrPeriod) + 5) {
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        double[] open = data.open();

        // 计算指标
        double[][] bb = Indicators.computeBollingerBands(close, window, stdDev);
        double[] upperBand = bb[0];
        double[] middleBand = bb[1];
        double[] lowerBand = bb[2];

        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);
        double[] ma = Indicators.computeSma(close, window);

        // 跟踪仓位状态
        int position = 0; // 0=平仓, 1=做多, -1=做空
        int holdCount = 0;
        double entryPrice = 0;
        double stopLoss = 0;

        for (int i = window; i < length; i++) {
            signals[i] = 0; // 默认平仓

            double currentClose = close[i];
            double currentOpen = open[i];
            double currentHigh = high[i];
            double currentLow = low[i];
            double currentAtr = atr[i];
            double currentUpper = upperBand[i];
            double currentLower = lowerBand[i];
            double currentMiddle = middleBand[i];
            double currentMa = ma[i];

            // 计算布林带宽度用于入场区域
            double bandWidth = currentUpper - currentLower;
            double entryUpperZone = currentUpper - entryZone * bandWidth;
            double entryLowerZone = currentLower + entryZone * bandWidth;

            // 检查出场条件
            if (position != 0) {
                boolean shouldExit = false;

                // 超时出场
                if (holdCount >= maxHoldBars) {
                    shouldExit = true;
                }

                // ATR 追踪止损
                if (position == 1 && currentClose < stopLoss) {
                    shouldExit = true;
                } else if (position == -1 && currentClose > stopLoss) {
                    shouldExit = true;
                }

                // MA 反转
                if (position == 1 && currentClose < currentMa) {
                    shouldExit = true;
                } else if (position == -1 && currentClose > currentMa) {
                    shouldExit = true;
                }

                if (shouldExit) {
                    signals[i] = 0; // 出场平仓
                    position = 0;
                    holdCount = 0;
                    continue;
                }

                // 持仓
                signals[i] = 1;
                holdCount++;
                continue;
            }

            // 动量检测：检查连续K线
            boolean bullishMomentum = false;
            boolean bearishMomentum = false;

            if (i >= 2) {
                // 看涨动量：最后2根K线都收盘高于开盘
                bullishMomentum = (close[i - 1] > open[i - 1]) && (close[i - 2] > open[i - 2]);
                // 看跌动量：最后2根K线都收盘低于开盘
                bearishMomentum = (close[i - 1] < open[i - 1]) && (close[i - 2] < open[i - 2]);
            }

            // 做多入场：价格接近下轨 + 看涨动量
            if (currentClose <= entryLowerZone && bullishMomentum) {
                signals[i] = 2; // 做多
                position = 1;
                entryPrice = currentClose;
                stopLoss = entryPrice - atrMultiplier * currentAtr;
                holdCount = 1;
            }
            // 做空入场：价格接近上轨 + 看跌动量
            else if (enableShortParam && enableShort && currentClose >= entryUpperZone && bearishMomentum) {
                signals[i] = 3; // 做空
                position = -1;
                entryPrice = currentClose;
                stopLoss = entryPrice + atrMultiplier * currentAtr;
                holdCount = 1;
            }
        }

        return signals;
    }

    @Override
    public String name() {
        return "PureAction";
    }
}
