package io.leavesfly.autoresearch.crypto.strategy.impl;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.indicator.Indicators;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 趋势对齐的纯价格行为策略 V2 (Pure Action V2)
 * 
 * 相比 V1 的改进：
 * a. trendAlignedOnly: 完全禁止逆势交易
 * b. RSI 过滤: 避免抄底抄顶（做多需 RSI < rsiOversold 确认，做空需 RSI > rsiOverbought 确认）
 * c. 不对称入场: 做空可以有额外的 shortEntryBonus 使入场更容易
 * 
 * 入场：价格触及布林带极端 + 趋势对齐（可选）+ RSI 确认（可选）
 * 出场：ATR 追踪止损 + MA 反转 + 超时
 */
public class PureActionV2Strategy extends BaseStrategy {

    private final int window;
    private final double stdDev;
    private final int atrPeriod;
    private final double atrMultiplier;
    private final int maxHoldBars;
    private final double entryZone;
    private final double shortEntryBonus;
    private final boolean enableShortParam;
    private final int trendMaPeriod;
    private final boolean trendAlignedOnly;
    private final boolean useRsiFilter;
    private final int rsiPeriod;
    private final double rsiOversold;
    private final double rsiOverbought;
    private final Double adxThreshold;
    private final int adxPeriod;

    public PureActionV2Strategy(Map<String, Object> params) {
        super(params);
        
        this.window = getInt("window", 10);
        this.stdDev = getDouble("stdDev", 2.5);
        this.atrPeriod = getInt("atrPeriod", 7);
        this.atrMultiplier = getDouble("atrMultiplier", 3.0);
        this.maxHoldBars = getInt("maxHoldBars", 12);
        this.entryZone = getDouble("entryZone", 0.0);
        this.shortEntryBonus = getDouble("shortEntryBonus", 0.0);
        this.enableShortParam = getBool("enableShort", true);
        this.trendMaPeriod = getInt("trendMaPeriod", 100);
        this.trendAlignedOnly = getBool("trendAlignedOnly", false);
        this.useRsiFilter = getBool("useRsiFilter", false);
        this.rsiPeriod = getInt("rsiPeriod", 14);
        this.rsiOversold = getDouble("rsiOversold", 30);
        this.rsiOverbought = getDouble("rsiOverbought", 70);
        
        // ADX 阈值，null 或 0 表示不启用
        Object adxObj = params.get("adxThreshold");
        if (adxObj instanceof Number num) {
            double val = num.doubleValue();
            this.adxThreshold = val > 0 ? val : null;
        } else {
            this.adxThreshold = null;
        }
        this.adxPeriod = getInt("adxPeriod", 14);
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
        
        // 计算布林带
        double[][] bb = Indicators.computeBollingerBands(close, window, stdDev);
        double[] upperBand = bb[0];
        double[] middleBand = bb[1];
        double[] lowerBand = bb[2];
        
        // 计算 ATR
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);
        
        // 计算趋势均线（SMA）
        double[] trendMa = Indicators.computeSma(close, trendMaPeriod);
        
        // 计算 RSI（如果需要）
        double[] rsi = null;
        if (useRsiFilter) {
            rsi = Indicators.computeRsi(close, rsiPeriod);
        }
        
        // 计算 ADX（如果需要）
        double[] adx = null;
        if (adxThreshold != null) {
            double[][] adxResult = Indicators.computeAdx(high, low, close, adxPeriod);
            adx = adxResult[0];
        }
        
        // 跟踪持仓状态
        int position = 0; // 0=空仓, 1=多头, -1=空头
        double entryPrice = 0;
        int holdBars = 0;
        double trailingStop = 0;
        
        for (int i = 0; i < length; i++) {
            // 跳过数据不足的区域
            if (i < Math.max(window, Math.max(atrPeriod, trendMaPeriod))) {
                signals[i] = 0;
                continue;
            }
            
            double currentClose = close[i];
            double currentHigh = high[i];
            double currentLow = low[i];
            double currentAtr = atr[i];
            
            // 判断是否触及布林带极端
            boolean touchUpper = currentHigh >= upperBand[i] - entryZone;
            boolean touchLower = currentLow <= lowerBand[i] + entryZone;
            
            // 判断趋势方向
            boolean uptrend = currentClose > trendMa[i];
            boolean downtrend = currentClose < trendMa[i];
            
            // RSI 确认
            boolean rsiConfirmLong = true;
            boolean rsiConfirmShort = true;
            if (useRsiFilter && rsi != null) {
                rsiConfirmLong = rsi[i] < rsiOversold;
                rsiConfirmShort = rsi[i] > rsiOverbought;
            }
            
            // ADX 过滤（如果启用）
            boolean adxConfirm = true;
            if (adxThreshold != null && adx != null) {
                adxConfirm = adx[i] >= adxThreshold;
            }
            
            // 生成原始信号
            int rawSignal = 0;
            
            // 做多信号：触及下轨 + 趋势对齐（可选）+ RSI 确认（可选）+ ADX 确认（可选）
            if (touchLower) {
                boolean trendOk = !trendAlignedOnly || uptrend;
                if (trendOk && rsiConfirmLong && adxConfirm) {
                    rawSignal = 1; // 看多
                }
            }
            
            // 做空信号：触及上轨 + 趋势对齐（可选）+ RSI 确认（可选）+ ADX 确认（可选）
            if (touchUpper && (enableShort && enableShortParam)) {
                boolean trendOk = !trendAlignedOnly || downtrend;
                // 做空有额外的 bonus，使入场更容易
                double adjustedZone = entryZone - shortEntryBonus;
                boolean touchAdjusted = currentHigh >= upperBand[i] - adjustedZone;
                
                if (touchAdjusted && trendOk && rsiConfirmShort && adxConfirm) {
                    rawSignal = -1; // 看空
                }
            }
            
            // 应用风险管理逻辑
            signals[i] = applyRiskManagement(
                    rawSignal, position, entryPrice, holdBars, trailingStop,
                    currentClose, currentHigh, currentLow, currentAtr,
                    middleBand[i], enableShort && enableShortParam
            );
            
            // 更新持仓状态
            if (signals[i] == 2) { // 开多
                position = 1;
                entryPrice = currentClose;
                holdBars = 0;
                trailingStop = currentClose - atrMultiplier * currentAtr;
            } else if (signals[i] == 3) { // 开空
                position = -1;
                entryPrice = currentClose;
                holdBars = 0;
                trailingStop = currentClose + atrMultiplier * currentAtr;
            } else if (signals[i] == 1) { // 持有
                holdBars++;
                // 更新追踪止损
                if (position == 1) {
                    trailingStop = Math.max(trailingStop, currentClose - atrMultiplier * currentAtr);
                } else if (position == -1) {
                    trailingStop = Math.min(trailingStop, currentClose + atrMultiplier * currentAtr);
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
     * 应用风险管理逻辑（止损、超时、MA 反转）
     * 
     * @param rawSignal 原始信号
     * @param position 当前持仓
     * @param entryPrice 入场价格
     * @param holdBars 持仓 K 线数
     * @param trailingStop 追踪止损价
     * @param currentClose 当前收盘价
     * @param currentHigh 当前最高价
     * @param currentLow 当前最低价
     * @param currentAtr 当前 ATR
     * @param middleBand 布林带中轨
     * @param enableShort 是否允许做空
     * @return 最终信号 (0=flat, 1=hold, 2=long, 3=short)
     */
    private int applyRiskManagement(
            int rawSignal, int position, double entryPrice, int holdBars, double trailingStop,
            double currentClose, double currentHigh, double currentLow, double currentAtr,
            double middleBand, boolean enableShort
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
        
        // 检查 MA 反转（价格穿越中轨）
        boolean maReverse = false;
        if (position == 1 && currentClose < middleBand) {
            maReverse = true; // 多头持仓时价格跌破中轨
        } else if (position == -1 && currentClose > middleBand) {
            maReverse = true; // 空头持仓时价格突破中轨
        }
        
        // 检查反向信号平仓
        boolean reverseSignal = false;
        if (position == 1 && rawSignal == -1) {
            reverseSignal = true;
        } else if (position == -1 && rawSignal == 1) {
            reverseSignal = true;
        }
        
        // 如果触发任何退出条件，平仓
        if (stopLossHit || timeoutExit || maReverse || reverseSignal) {
            return 0; // 平仓
        }
        
        // 否则继续持有
        return 1; // 持有
    }

    @Override
    public String name() {
        return "PureActionV2";
    }
}
