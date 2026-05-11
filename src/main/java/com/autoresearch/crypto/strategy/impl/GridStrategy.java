package com.autoresearch.crypto.strategy.impl;

import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.indicator.Indicators;
import com.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 网格交易策略。
 * 在移动的中价周围维持虚拟网格买卖价位。
 * 当价格穿越网格价位时调整仓位。
 * 适用于震荡/低波动市场。
 */
public class GridStrategy extends BaseStrategy {

    private final double gridSpacingPct;
    private final int gridLevels;
    private final int atrPeriod;
    private final double atrSpacingMult;
    private final double maxPosition;
    private final int trendMaPeriod;

    public GridStrategy(Map<String, Object> params) {
        super(params);
        this.gridSpacingPct = getDouble("gridSpacingPct", 0.005);
        this.gridLevels = getInt("gridLevels", 5);
        this.atrPeriod = getInt("atrPeriod", 14);
        this.atrSpacingMult = getDouble("atrSpacingMult", 0.5);
        this.maxPosition = getDouble("maxPosition", 1.0);
        this.trendMaPeriod = getInt("trendMaPeriod", 100);
    }

    @Override
    public int[] generateSignals(MarketData data, boolean enableShort) {
        int length = data.length();
        int[] signals = new int[length];

        if (length < Math.max(atrPeriod, trendMaPeriod) + gridLevels) {
            // 数据不足，返回平仓
            for (int i = 0; i < length; i++) {
                signals[i] = 0;
            }
            return signals;
        }

        double[] close = data.close();
        double[] high = data.high();
        double[] low = data.low();

        // 计算 ATR 用于动态网格间距
        double[] atr = Indicators.computeAtr(high, low, close, atrPeriod);

        // 计算长周期 MA 用于趋势检测
        double[] trendMa = Indicators.computeSma(close, trendMaPeriod);

        // 跟踪当前仓位：正数为做多，负数为做空，0 为平仓
        double currentPosition = 0.0;

        for (int i = trendMaPeriod; i < length; i++) {
            double price = close[i];
            double midPrice = trendMa[i];
            double currentAtr = atr[i];

            // 基于 ATR 的动态网格间距
            double dynamicSpacing = currentAtr * atrSpacingMult / midPrice;
            if (dynamicSpacing < gridSpacingPct) {
                dynamicSpacing = gridSpacingPct;
            }

            // 检查强趋势 - 在强趋势中禁用网格交易
            double trendThreshold = 0.02; // 2% deviation from trend MA indicates strong trend
            double deviation = Math.abs(price - midPrice) / midPrice;

            if (deviation > trendThreshold) {
                // 检测到强趋势，平仓
                signals[i] = 0;
                currentPosition = 0.0;
                continue;
            }

            // 计算网格价位
            // 做多入场价位：在中价下方
            // 做空入场价位：在中价上方
            int targetPosition = 0;

            // 检查做多网格价位（价格低于中价）
            for (int level = 1; level <= gridLevels; level++) {
                double longLevel = midPrice * (1 - dynamicSpacing * level);
                if (price <= longLevel) {
                    targetPosition = Math.min(level, (int) maxPosition);
                    break;
                }
            }

            // 检查做空网格价位（价格高于中价）
            if (targetPosition == 0 && enableShort) {
                for (int level = 1; level <= gridLevels; level++) {
                    double shortLevel = midPrice * (1 + dynamicSpacing * level);
                    if (price >= shortLevel) {
                        targetPosition = -Math.min(level, (int) maxPosition);
                        break;
                    }
                }
            }

            // 根据仓位变化生成信号
            if (targetPosition > 0) {
                if (currentPosition <= 0) {
                    signals[i] = 2; // 做多信号
                } else {
                    signals[i] = 1; // 持有多头
                }
            } else if (targetPosition < 0) {
                if (currentPosition >= 0) {
                    signals[i] = 3; // 做空信号
                } else {
                    signals[i] = 1; // 持有空头
                }
            } else {
                if (currentPosition != 0) {
                    signals[i] = 0; // 平仓
                } else {
                    signals[i] = 0; // 保持平仓
                }
            }

            currentPosition = targetPosition;
        }

        // 初始K线填充平仓信号
        for (int i = 0; i < trendMaPeriod; i++) {
            signals[i] = 0;
        }

        return signals;
    }

    @Override
    public String name() {
        return "Grid";
    }
}
