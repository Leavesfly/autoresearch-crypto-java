package io.leavesfly.autoresearch.crypto.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * 带有边界情况保护的风险调整评分。
 * 用风险惩罚指标替代简单的综合评分。
 */
public final class RiskAdjustedScoring {

    private RiskAdjustedScoring() {}

    /**
     * 计算带有边界情况保护的风险调整综合评分。
     */
    public static ScoredResult riskAdjustedScore(
            double sharpe, double totalReturn, double maxDrawdown,
            double winRate, int numTrades, double marketReturn,
            int minTrades, double maxDd,
            double sharpeWeight, double ddWeight,
            double tradeWeight, double excessWeight, double wrWeight) {

        List<EdgeFlag> flags = new ArrayList<>();

        // 硬性失败条件
        if (totalReturn <= -0.90) {
            return new ScoredResult(0.0, sharpe, totalReturn, maxDrawdown, winRate, numTrades,
                    List.of(EdgeFlag.RISKY), 0, 0, 0, 0);
        }

        if (Math.abs(maxDrawdown) > maxDd) {
            flags.add(EdgeFlag.RISKY);
        }
        if (numTrades < minTrades) {
            flags.add(EdgeFlag.OVERFIT);
        }

        // Sharpe 组件：0..1 范围，Sharpe=3.0 → 1.0
        double sharpeComp = Math.max(0.0, Math.min(1.0, (sharpe + 1.0) / 4.0));

        // 回撤惩罚：1 / (1 + dd/scale)
        double ddScale = 0.15;
        double ddComp = 1.0 / (1.0 + Math.abs(maxDrawdown) / ddScale);

        // 交易次数：平方根缩放
        double tradeComp = Math.min(1.0, Math.pow((double) numTrades / Math.max(minTrades, 1), 0.5));

        // 相对于市场的超额收益
        double excess = totalReturn - marketReturn;
        double excessComp = Math.max(0.0, Math.min(1.0, (excess + 0.10) / 0.30));

        // 胜率
        double wrComp = Math.max(0.0, Math.min(1.0, (winRate - 0.35) / 0.30));

        // 综合评分
        double score = sharpeComp * sharpeWeight
                + ddComp * ddWeight
                + tradeComp * tradeWeight
                + excessComp * excessWeight
                + wrComp * wrWeight;

        // 硬性门槛
        if (flags.contains(EdgeFlag.RISKY) || flags.contains(EdgeFlag.OVERFIT)) {
            score = 0.0;
        }

        return new ScoredResult(score, sharpe, totalReturn, maxDrawdown, winRate, numTrades,
                flags, sharpeComp, ddComp, tradeComp, excessComp);
    }

    /**
     * 使用默认权重的便捷重载方法。
     */
    public static ScoredResult riskAdjustedScore(
            double sharpe, double totalReturn, double maxDrawdown,
            double winRate, int numTrades) {
        return riskAdjustedScore(sharpe, totalReturn, maxDrawdown, winRate, numTrades,
                0.0, 10, 0.30, 0.30, 0.25, 0.20, 0.15, 0.10);
    }

    /**
     * 检查代理的评分是否已停滞。
     */
    public static boolean detectDeadAgent(List<Double> scoreHistory, int deadThreshold, double epsilon) {
        if (scoreHistory.size() < deadThreshold) {
            return false;
        }
        List<Double> recent = scoreHistory.subList(scoreHistory.size() - deadThreshold, scoreHistory.size());
        double max = recent.stream().mapToDouble(Double::doubleValue).max().orElse(0);
        double min = recent.stream().mapToDouble(Double::doubleValue).min().orElse(0);
        return (max - min) < epsilon;
    }

    public static boolean detectDeadAgent(List<Double> scoreHistory) {
        return detectDeadAgent(scoreHistory, 5, 0.001);
    }
}
