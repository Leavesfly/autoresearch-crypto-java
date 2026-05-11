package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 趋势策略 - 多因子确认的布林带均值回归策略。
 * 
 * 入场：价格触及布林带下轨（做多）或上轨（做空），
 *       并有 ADX 趋势确认和 RSI 极值区确认。
 * 出场：ATR 追踪止损 + MA 反转 + 超时出场。
 */
public class TrendStrategy extends BaseStrategy {

    private final int window;
    private final double stdDev;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final double adxThreshold;
    private final double rsiThreshold;
    private final double entryZone;
    private final boolean useAdx;

    public TrendStrategy(Map<String, Object> params) {
        super(params);
        this.window = getInt("window", 20);
        this.stdDev = getDouble("stdDev", 2.0);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.5);
        this.maxHoldBars = getInt("maxHoldBars", 48);
        this.adxThreshold = getDouble("adxThreshold", 25);
        this.rsiThreshold = getDouble("rsiThreshold", 30);
        this.entryZone = getDouble("entryZone", 1.0);
        this.useAdx = getBool("useAdx", true);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];

        if (length < Math.max(window, atrPeriod) + 10) {
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        double[] volume = data.volume();

        // 计算指标
        double[][] bb = Indicators.computeBollingerBands(close, window, stdDev);
        double[] upperBand = bb[0];
        double[] middleBand = bb[1];
        double[] lowerBand = bb[2];

        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);
        double[][] adxData = Indicators.computeAdx(high, low, close, 14);
        double[] adx = adxData[0];
        double[] rsi = Indicators.computeRsi(close, 14);
        double[] ma = Indicators.computeSma(close, window);

        // 跟踪仓位状态
        int position = 0; // 0=平仓, 1=做多, -1=做空
        int holdCount = 0;
        double entryPrice = 0;
        double stopLoss = 0;

        for (int i = window; i < length; i++) {
            signals[i] = 0; // 默认平仓

            double currentClose = close[i];
            double currentAtr = atr[i];
            double currentAdx = adx[i];
            double currentRsi = rsi[i];
            double currentUpper = upperBand[i];
            double currentLower = lowerBand[i];
            double currentMiddle = middleBand[i];
            double currentMa = ma[i];

            // 计算布林带宽度用于入场区域
            double bandWidth = currentUpper - currentLower;
            double entryUpperZone = currentUpper - entryZone * (bandWidth / 2.0);
            double entryLowerZone = currentLower + entryZone * (bandWidth / 2.0);

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

            // 入场逻辑
            boolean adxConfirmed = !useAdx || currentAdx >= adxThreshold;

            // 做多入场：价格接近下轨 + ADX 确认 + RSI 超卖
            if (currentClose <= entryLowerZone && adxConfirmed && currentRsi <= rsiThreshold) {
                signals[i] = 2; // 做多
                position = 1;
                entryPrice = currentClose;
                stopLoss = entryPrice - atrMultiplier * currentAtr;
                holdCount = 1;
            }
            // 做空入场：价格接近上轨 + ADX 确认 + RSI 超买
            else if (enableShort && currentClose >= entryUpperZone && adxConfirmed && currentRsi >= (100 - rsiThreshold)) {
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
        return "Trend";
    }
}
