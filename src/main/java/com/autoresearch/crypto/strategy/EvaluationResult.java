package com.autoresearch.crypto.strategy;

import java.util.List;

/**
 * 完整的评估结果，包含评分、指标和交易历史。
 */
public record EvaluationResult(
        double score,
        PerformanceMetrics metrics,
        List<TradeRecord> trades,
        double[] equityCurve
) {
    public static EvaluationResult empty() {
        return new EvaluationResult(0.0, PerformanceMetrics.empty(), List.of(), new double[0]);
    }
}
