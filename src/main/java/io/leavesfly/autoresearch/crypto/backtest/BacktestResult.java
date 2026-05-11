package io.leavesfly.autoresearch.crypto.backtest;

import io.leavesfly.autoresearch.crypto.regime.RegimeInfo;
import io.leavesfly.autoresearch.crypto.strategy.PerformanceMetrics;

/**
 * 完整的回测结果。
 */
public record BacktestResult(
        String strategyName,
        PerformanceMetrics metrics,
        double compositeScore,
        int tradeCount,
        double[] equityCurve,
        RegimeInfo regime
) {}
