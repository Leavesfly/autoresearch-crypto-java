package com.autoresearch.crypto.strategy.impl;

import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.indicator.Indicators;
import com.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 混合策略（市场机制自适应策略）
 * 
 * 使用 ADX 分类市场状态：
 * - ADX <= adxThreshold: 震荡市，使用布林带均值回归逻辑
 * - ADX > adxThreshold: 趋势市，使用MA回调入场逻辑
 */
public class HybridStrategy extends BaseStrategy {

    private final int window;
    private final double stdDev;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final double entryZone;
    private final boolean enableShort;
    private final int trendMaPeriod;
    private final double adxThreshold;
    private final int adxPeriod;

    public HybridStrategy(Map<String, Object> params) {
        super(params);
        this.window = getInt("window", 20);
        this.stdDev = getDouble("stdDev", 2.0);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.0);
        this.maxHoldBars = getInt("maxHoldBars", 24);
        this.entryZone = getDouble("entryZone", 0.0);
        this.enableShort = getBool("enableShort", true);
        this.trendMaPeriod = getInt("trendMaPeriod", 100);
        this.adxThreshold = getDouble("adxThreshold", 25);
        this.adxPeriod = getInt("adxPeriod", 14);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];

        // 计算所需指标
        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        
        // 计算 ATR
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);
        
        // 计算 ADX
        double[][] adxResult = Indicators.computeAdx(high, low, close, adxPeriod);
        double[] adx = adxResult[0];
        
        // 计算布林带
        double[][] bbResult = Indicators.computeBollingerBands(close, window, stdDev);
        double[] bbUpper = bbResult[0];
        double[] bbMiddle = bbResult[1];
        double[] bbLower = bbResult[2];
        
        // 计算趋势 MA（快线和慢线）
        double[] fastMa = Indicators.computeEma(close, trendMaPeriod / 2);
        double[] slowMa = Indicators.computeEma(close, trendMaPeriod);

        // 持仓计数器，用于超时退出
        int holdCount = 0;
        int position = 0; // 0=无持仓, 1=多头, -1=空头

        for (int i = Math.max(window, Math.max(atrPeriod, adxPeriod * 2)); i < length; i++) {
            double currentClose = close[i];
            double currentAtr = atr[i];
            double currentAdx = adx[i];
            
            // 判断市场状态
            boolean isTrending = currentAdx > adxThreshold;
            
            // 出场逻辑检查
            if (position != 0) {
                boolean shouldExit = false;
                
                // 超时退出
                if (holdCount >= maxHoldBars) {
                    shouldExit = true;
                }
                
                // ATR 追踪止损
                if (position == 1) { // 多头
                    double stopLoss = currentClose - atrMultiplier * currentAtr;
                    if (currentClose < stopLoss) {
                        shouldExit = true;
                    }
                    // MA 反转信号：快线下穿慢线
                    if (fastMa[i] < slowMa[i]) {
                        shouldExit = true;
                    }
                } else if (position == -1) { // 空头
                    double stopLoss = currentClose + atrMultiplier * currentAtr;
                    if (currentClose > stopLoss) {
                        shouldExit = true;
                    }
                    // MA 反转信号：快线上穿慢线
                    if (fastMa[i] > slowMa[i]) {
                        shouldExit = true;
                    }
                }
                
                if (shouldExit) {
                    signals[i] = 1; // 平仓信号
                    position = 0;
                    holdCount = 0;
                    continue;
                }
                
                holdCount++;
                signals[i] = 1; // 持有
                continue;
            }

            // 入场逻辑
            int signal = 0;
            
            if (!isTrending) {
                // 震荡模式：布林带均值回归
                signal = generateRangingSignal(currentClose, bbUpper[i], bbLower[i], close, i);
            } else {
                // 趋势模式：MA 回调入场
                signal = generateTrendingSignal(currentClose, fastMa[i], slowMa[i], close, i);
            }
            
            // 检查是否允许做空
            if (signal == 3 && !enableShort) {
                signal = 0;
            }
            
            if (signal != 0) {
                position = (signal == 2) ? 1 : -1;
                holdCount = 1;
            }
            
            signals[i] = signal;
        }

        return signals;
    }

    /**
     * 震荡市场信号生成：布林带均值回归
     * 
     * @param currentClose 当前收盘价
     * @param bbUpper 布林带上轨
     * @param bbLower 布林带下轨
     * @param close 收盘价数组
     * @param idx 当前索引
     * @return 信号：0=flat, 2=long, 3=short
     */
    private int generateRangingSignal(double currentClose, double bbUpper, double bbLower, 
                                       double[] close, int idx) {
        // 价格触及下轨 + 连续K线下跌确认 → 做多
        if (currentClose <= bbLower) {
            // 检查连续下跌确认（至少2根K线）
            if (idx >= 2 && close[idx - 1] < close[idx - 2] && currentClose < close[idx - 1]) {
                return 2; // 做多
            }
        }
        
        // 价格触及上轨 + 连续K线上涨确认 → 做空
        if (currentClose >= bbUpper) {
            // 检查连续上涨确认（至少2根K线）
            if (idx >= 2 && close[idx - 1] > close[idx - 2] && currentClose > close[idx - 1]) {
                return 3; // 做空
            }
        }
        
        return 0; // 无信号
    }

    /**
     * 趋势市场信号生成：MA 回调入场
     * 
     * @param currentClose 当前收盘价
     * @param fastMa 快线 MA
     * @param slowMa 慢线 MA
     * @param close 收盘价数组
     * @param idx 当前索引
     * @return 信号：0=flat, 2=long, 3=short
     */
    private int generateTrendingSignal(double currentClose, double fastMa, double slowMa,
                                        double[] close, int idx) {
        // 上升趋势：快MA > 慢MA，价格回调至快MA附近 → 做多
        if (fastMa > slowMa) {
            // 价格回调至快MA附近（在快MA的 entryZone 范围内）
            double maDistance = Math.abs(currentClose - fastMa);
            double threshold = fastMa * entryZone / 100.0; // entryZone 作为百分比
            
            if (maDistance <= threshold || currentClose <= fastMa) {
                // 确认是回调而非破位：前一根K线收盘价高于当前
                if (idx >= 1 && close[idx - 1] > currentClose) {
                    return 2; // 做多
                }
            }
        }
        
        // 下降趋势：快MA < 慢MA，价格反弹至快MA附近 → 做空
        if (fastMa < slowMa) {
            // 价格反弹至快MA附近
            double maDistance = Math.abs(currentClose - fastMa);
            double threshold = fastMa * entryZone / 100.0;
            
            if (maDistance <= threshold || currentClose >= fastMa) {
                // 确认是反弹而非突破：前一根K线收盘价低于当前
                if (idx >= 1 && close[idx - 1] < currentClose) {
                    return 3; // 做空
                }
            }
        }
        
        return 0; // 无信号
    }

    @Override
    public String name() {
        return "Hybrid";
    }
}
