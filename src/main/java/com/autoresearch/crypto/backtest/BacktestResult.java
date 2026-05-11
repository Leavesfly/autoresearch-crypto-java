package com.autoresearch.crypto.backtest;

import com.autoresearch.crypto.regime.RegimeInfo;
import com.autoresearch.crypto.strategy.PerformanceMetrics;

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
