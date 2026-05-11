package com.autoresearch.crypto.strategy.impl;

import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.indicator.Indicators;
import com.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 多时间框架嵌套投票集成策略 (Multi-TF Ensemble)
 * 
 * 各时间框架独立角色：
 * - 1d: 方向过滤
 * - 4h: 仓位大小
 * - 1h: 入场时机
 * - 15m: 出场/反转
 * 
 * 每个时间框架基于 EMA 快慢线 + RSI 超买超卖生成独立信号 (1=看多, -1=看空, 0=中性)
 * 加权投票聚合：各 TF 信号乘以权重求和，超过阈值则产生交易信号
 * 使用 ATR 追踪止损 + 超时退出
 */
public class MultiTfEnsembleStrategy extends BaseStrategy {

    // 各时间框架对应的 K 线数量（基于 5 分钟基础数据）
    private static final Map<String, Integer> TF_BARS = Map.of(
            "15m", 3,    // 15分钟 = 3个5分钟
            "1h", 12,    // 1小时 = 12个5分钟
            "4h", 48,    // 4小时 = 48个5分钟
            "1d", 288    // 1天 = 288个5分钟
    );

    private final Map<String, Double> weights;
    private final double voteThreshold;
    private final int maFast;
    private final int maSlow;
    private final int rsiPeriod;
    private final double rsiOversold;
    private final double rsiOverbought;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;

    public MultiTfEnsembleStrategy(Map<String, Object> params) {
        super(params);
        
        // 解析权重参数
        @SuppressWarnings("unchecked")
        Map<String, Double> w = (Map<String, Double>) params.getOrDefault("weights", Map.of(
                "1d", 0.35, "4h", 0.25, "1h", 0.25, "15m", 0.15
        ));
        this.weights = w;
        
        this.voteThreshold = getDouble("voteThreshold", 0.5);
        this.maFast = getInt("maFast", 10);
        this.maSlow = getInt("maSlow", 30);
        this.rsiPeriod = getInt("rsiPeriod", 14);
        this.rsiOversold = getDouble("rsiOversold", 30);
        this.rsiOverbought = getDouble("rsiOverbought", 70);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrMultiplier = getDouble("atrMultiplier", 2.5);
        this.maxHoldBars = getInt("maxHoldBars", 48);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];
        
        if (length == 0) {
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();
        
        // 计算基础 ATR（用于止损）
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);
        
        // 为每个时间框架生成信号
        String[] timeframes = {"1d", "4h", "1h", "15m"};
        int[][] tfSignals = new int[timeframes.length][length];
        
        for (int tfIdx = 0; tfIdx < timeframes.length; tfIdx++) {
            String tf = timeframes[tfIdx];
            int barsPerTF = TF_BARS.get(tf);
            
            // 重采样：将 5 分钟数据聚合为高时间框架的收盘价
            double[] resampledClose = resampleClose(close, barsPerTF);
            double[] resampledHigh = resampleHigh(high, barsPerTF);
            double[] resampledLow = resampleLow(low, barsPerTF);
            
            if (resampledClose.length < Math.max(maSlow, rsiPeriod) + 1) {
                // 数据不足，跳过此时间框架
                continue;
            }
            
            // 计算 EMA 快慢线
            double[] emaFast = Indicators.computeEma(resampledClose, maFast);
            double[] emaSlow = Indicators.computeEma(resampledClose, maSlow);
            
            // 计算 RSI
            double[] rsi = Indicators.computeRsi(resampledClose, rsiPeriod);
            
            // 将重采样后的信号映射回原始长度
            for (int i = 0; i < length; i++) {
                int resampledIdx = i / barsPerTF;
                if (resampledIdx >= resampledClose.length) {
                    break;
                }
                
                // 生成该时间框架的信号
                int signal = generateTfSignal(
                        emaFast, emaSlow, rsi, resampledIdx,
                        resampledClose.length
                );
                tfSignals[tfIdx][i] = signal;
            }
        }
        
        // 跟踪持仓状态用于止损逻辑
        int position = 0; // 0=空仓, 1=多头, -1=空头
        double entryPrice = 0;
        int holdBars = 0;
        double trailingStop = 0;
        
        // 加权投票聚合信号
        for (int i = 0; i < length; i++) {
            // 计算加权投票得分
            double weightedSum = 0;
            for (int tfIdx = 0; tfIdx < timeframes.length; tfIdx++) {
                String tf = timeframes[tfIdx];
                double weight = weights.getOrDefault(tf, 0.0);
                weightedSum += tfSignals[tfIdx][i] * weight;
            }
            
            // 根据投票结果生成交易信号
            int rawSignal = 0;
            if (weightedSum > voteThreshold) {
                rawSignal = 1; // 看多
            } else if (weightedSum < -voteThreshold) {
                rawSignal = -1; // 看空
            }
            
            // 应用止损和超时逻辑
            signals[i] = applyRiskManagement(
                    rawSignal, position, entryPrice, holdBars, trailingStop,
                    close[i], high[i], low[i], atr[i], enableShort
            );
            
            // 更新持仓状态
            if (signals[i] == 2) { // 开多
                position = 1;
                entryPrice = close[i];
                holdBars = 0;
                trailingStop = close[i] - atrMultiplier * atr[i];
            } else if (signals[i] == 3) { // 开空
                position = -1;
                entryPrice = close[i];
                holdBars = 0;
                trailingStop = close[i] + atrMultiplier * atr[i];
            } else if (signals[i] == 1) { // 持有
                holdBars++;
                // 更新追踪止损
                if (position == 1) {
                    trailingStop = Math.max(trailingStop, close[i] - atrMultiplier * atr[i]);
                } else if (position == -1) {
                    trailingStop = Math.min(trailingStop, close[i] + atrMultiplier * atr[i]);
                }
            } else if (signals[i] == 0) { // 平仓
                position = 0;
                entryPrice = 0;
                holdBars = 0;
                trailingStop = 0;
            }
        }
        
        return signals;
    }

    /**
     * 为单个时间框架生成信号
     * 
     * @param emaFast EMA 快线
     * @param emaSlow EMA 慢线
     * @param rsi RSI 值
     * @param idx 当前索引
     * @param length 数据长度
     * @return 1=看多, -1=看空, 0=中性
     */
    private int generateTfSignal(double[] emaFast, double[] emaSlow, double[] rsi, int idx, int length) {
        if (idx < maSlow || idx >= length) {
            return 0;
        }
        
        double fastVal = emaFast[idx];
        double slowVal = emaSlow[idx];
        double rsiVal = rsi[idx];
        
        // EMA 金叉且 RSI 未超买 -> 看多
        boolean bullish = fastVal > slowVal && rsiVal < rsiOverbought;
        // EMA 死叉且 RSI 未超卖 -> 看空
        boolean bearish = fastVal < slowVal && rsiVal > rsiOversold;
        
        if (bullish) {
            return 1;
        } else if (bearish) {
            return -1;
        }
        return 0;
    }

    /**
     * 应用风险管理逻辑（止损和超时）
     * 
     * @param rawSignal 原始信号
     * @param position 当前持仓
     * @param entryPrice 入场价格
     * @param holdBars 持仓K线数
     * @param trailingStop 追踪止损价
     * @param currentClose 当前收盘价
     * @param currentHigh 当前最高价
     * @param currentLow 当前最低价
     * @param currentAtr 当前 ATR
     * @param enableShort 是否允许做空
     * @return 最终信号 (0=flat, 1=hold, 2=long, 3=short)
     */
    private int applyRiskManagement(
            int rawSignal, int position, double entryPrice, int holdBars, double trailingStop,
            double currentClose, double currentHigh, double currentLow, double currentAtr,
            boolean enableShort
    ) {
        // 如果无持仓，根据原始信号开仓
        if (position == 0) {
            if (rawSignal == 1) {
                return 2; // 开多
            } else if (rawSignal == -1 && enableShort) {
                return 3; // 开空
            }
            return 0; // 保持空仓
        }
        
        // 检查止损触发
        boolean stopLossHit = false;
        if (position == 1) { // 多头
            if (currentLow <= trailingStop) {
                stopLossHit = true;
            }
        } else if (position == -1) { // 空头
            if (currentHigh >= trailingStop) {
                stopLossHit = true;
            }
        }
        
        // 检查超时退出
        boolean timeoutExit = holdBars >= maxHoldBars;
        
        // 检查反向信号平仓
        boolean reverseSignal = false;
        if (position == 1 && rawSignal == -1) {
            reverseSignal = true;
        } else if (position == -1 && rawSignal == 1) {
            reverseSignal = true;
        }
        
        // 如果触发任何退出条件，平仓
        if (stopLossHit || timeoutExit || reverseSignal) {
            return 0; // 平仓
        }
        
        // 否则继续持有
        return 1; // 持有
    }

    /**
     * 重采样收盘价：将低时间框架数据聚合为高时间框架的最后一个收盘价
     */
    private double[] resampleClose(double[] original, int barsPerTF) {
        int originalLength = original.length;
        int resampledLength = originalLength / barsPerTF;
        
        if (resampledLength == 0) {
            return new double[0];
        }
        
        double[] resampled = new double[resampledLength];
        for (int i = 0; i < resampledLength; i++) {
            // 取每个时间窗口的最后一个收盘价
            resampled[i] = original[(i + 1) * barsPerTF - 1];
        }
        return resampled;
    }

    /**
     * 重采样最高价：取每个时间窗口内的最高价
     */
    private double[] resampleHigh(double[] original, int barsPerTF) {
        int originalLength = original.length;
        int resampledLength = originalLength / barsPerTF;
        
        if (resampledLength == 0) {
            return new double[0];
        }
        
        double[] resampled = new double[resampledLength];
        for (int i = 0; i < resampledLength; i++) {
            double max = Double.MIN_VALUE;
            for (int j = i * barsPerTF; j < (i + 1) * barsPerTF; j++) {
                if (original[j] > max) {
                    max = original[j];
                }
            }
            resampled[i] = max;
        }
        return resampled;
    }

    /**
     * 重采样最低价：取每个时间窗口内的最低价
     */
    private double[] resampleLow(double[] original, int barsPerTF) {
        int originalLength = original.length;
        int resampledLength = originalLength / barsPerTF;
        
        if (resampledLength == 0) {
            return new double[0];
        }
        
        double[] resampled = new double[resampledLength];
        for (int i = 0; i < resampledLength; i++) {
            double min = Double.MAX_VALUE;
            for (int j = i * barsPerTF; j < (i + 1) * barsPerTF; j++) {
                if (original[j] < min) {
                    min = original[j];
                }
            }
            resampled[i] = min;
        }
        return resampled;
    }

    @Override
    public String name() {
        return "MultiTfEnsemble";
    }
}
