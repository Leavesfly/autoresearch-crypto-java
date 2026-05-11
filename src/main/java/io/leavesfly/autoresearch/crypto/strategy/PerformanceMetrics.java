package io.leavesfly.autoresearch.crypto.strategy;

/**
 * 从回测结果计算的性能指标。
 */
public record PerformanceMetrics(
        double totalReturn,
        double annualizedReturn,
        double annualizedVol,
        double sharpeRatio,
        double maxDrawdown,
        double winRate
) {
    public static PerformanceMetrics empty() {
        return new PerformanceMetrics(0, 0, 0, 0, -0.99, 0);
    }
}
