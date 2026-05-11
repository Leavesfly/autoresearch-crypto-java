package io.leavesfly.autoresearch.crypto.scoring;

/**
 * 标记有问题的评估结果。
 */
public enum EdgeFlag {
    OVERFIT,   // 交易次数过少 — 可能过拟合
    RISKY,     // 回撤过大 — 风险过高
    DEAD       // 多轮无改进 — 策略已耗尽
}
