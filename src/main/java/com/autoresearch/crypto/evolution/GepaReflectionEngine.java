package com.autoresearch.crypto.evolution;

import com.autoresearch.crypto.scoring.RiskAdjustedScoring;

import java.util.*;

/**
 * GEPA 反思进化引擎。
 * 遵循科学方法循环：
 * 1. 观察 → 回顾最近的实验日志
 * 2. 假设 → 提出因果假设
 * 3. 实验 → 运行有针对性的参数变更
 * 4. 反思 → 记录结果并更新信念
 *
 * 每5次实验，元反思循环会识别盲点。
 */
public class GepaReflectionEngine {

    private final List<Agent> agents;
    private final List<ExperimentLog> experimentLogs;
    private final int reflectionInterval;
    private final Random rng;
    private int cycleCount;

    public GepaReflectionEngine(List<Agent> agents, int reflectionInterval, long seed) {
        this.agents = agents;
        this.experimentLogs = new ArrayList<>();
        this.reflectionInterval = reflectionInterval;
        this.rng = new Random(seed);
        this.cycleCount = 0;
    }

    public GepaReflectionEngine(List<Agent> agents) {
        this(agents, 5, 42);
    }

    /**
     * 基于最近的实验历史为给定代理生成假设。
     */
    public Hypothesis generateHypothesis(Agent agent) {
        List<ExperimentLog> agentLogs = experimentLogs.stream()
                .filter(log -> log.getAgent().equals(agent.getName()))
                .toList();

        if (agentLogs.isEmpty()) {
            return getDefaultHypothesis(agent);
        }

        // 分析最近的失败
        List<ExperimentLog> recentFails = agentLogs.stream()
                .filter(log -> !log.isImprovement())
                .toList();

        if (recentFails.size() > 3) {
            return createExploratoryHypothesis(agent);
        }

        return createIncrementalHypothesis(agent, agentLogs);
    }

    private Hypothesis getDefaultHypothesis(Agent agent) {
        Map<String, Object> changes = new HashMap<>();
        switch (agent.getStyle()) {
            case "趋势跟踪" -> {
                changes.put("atrMultiplier", 2.5);
                changes.put("maxHoldBars", 36);
                return new Hypothesis("增加止损宽度以适应趋势波动",
                        agent.getName(), changes, "趋势策略需要更宽的止损避免被噪音震出");
            }
            case "均值回归" -> {
                changes.put("stdDev", 2.2);
                changes.put("entryZone", 0.2);
                return new Hypothesis("收紧入场阈值减少假信号",
                        agent.getName(), changes, "均值回归需要更严格的极端值确认");
            }
            case "网格交易" -> {
                changes.put("gridSpacingPct", 0.006);
                changes.put("gridLevels", 6);
                return new Hypothesis("增加网格密度以捕获更多小波动",
                        agent.getName(), changes, "更密的网格适合低波动横盘市场");
            }
            default -> {
                changes.put("atrMultiplier", 3.0);
                return new Hypothesis("放宽止损以适应高波动",
                        agent.getName(), changes, "默认策略需要更宽容的止损");
            }
        }
    }

    private Hypothesis createExploratoryHypothesis(Agent agent) {
        Map<String, Object> changes = new HashMap<>();
        // 尝试根本不同的参数组合
        changes.put("maxHoldBars", 12 + rng.nextInt(36));
        changes.put("atrMultiplier", 1.5 + rng.nextDouble() * 3.0);
        return new Hypothesis("多次失败后尝试大幅度参数变化",
                agent.getName(), changes, "当前参数空间可能已被充分探索，需要跳出局部最优");
    }

    private Hypothesis createIncrementalHypothesis(Agent agent, List<ExperimentLog> agentLogs) {
        ExperimentLog lastSuccess = agentLogs.stream()
                .filter(ExperimentLog::isImprovement)
                .reduce((a, b) -> b)
                .orElse(null);

        Map<String, Object> changes = new HashMap<>();
        if (lastSuccess != null) {
            // 沿着有效的方向继续
            for (Map.Entry<String, Object> entry : lastSuccess.getParamsAfter().entrySet()) {
                if (rng.nextDouble() < 0.3) {
                    changes.put(entry.getKey(), entry.getValue());
                }
            }
        }
        if (changes.isEmpty()) {
            changes.put("atrMultiplier", 2.0 + rng.nextGaussian() * 0.5);
        }
        return new Hypothesis("基于上次成功实验继续微调",
                agent.getName(), changes, "沿着有效的方向继续小步探索");
    }

    /**
     * 记录实验及其结果。
     */
    public void recordExperiment(ExperimentLog log) {
        experimentLogs.add(log);
        cycleCount++;
    }

    /**
     * 运行元反思：分析最近 N 次实验的模式。
     */
    public String metaReflect() {
        if (experimentLogs.size() < reflectionInterval) {
            return "元反思数据不足。";
        }

        List<ExperimentLog> recent = experimentLogs.subList(
                Math.max(0, experimentLogs.size() - reflectionInterval),
                experimentLogs.size());

        long improvements = recent.stream().filter(ExperimentLog::isImprovement).count();
        double improvementRate = (double) improvements / recent.size();

        StringBuilder reflection = new StringBuilder();
        reflection.append(String.format("元反思（第 %d 轮）：", cycleCount));
        reflection.append(String.format("最近 %d 次实验的改进率为 %.0f%%。",
                recent.size(), improvementRate * 100));

        if (improvementRate < 0.2) {
            reflection.append("策略探索陷入停滞。考虑切换策略类型或时间框架。");
        } else if (improvementRate > 0.6) {
            reflection.append("进展良好。继续沿当前方向进行更小的变异。");
        } else {
            reflection.append("进展适中。平衡探索和利用。");
        }

        return reflection.toString();
    }

    /**
     * 检查是否需要进行元反思。
     */
    public boolean shouldReflect() {
        return cycleCount > 0 && cycleCount % reflectionInterval == 0;
    }

    public List<ExperimentLog> getExperimentLogs() { return experimentLogs; }
    public int getCycleCount() { return cycleCount; }
    public List<Agent> getAgents() { return agents; }

    /**
     * 研究假设。
     */
    public record Hypothesis(String text, String agent, Map<String, Object> paramChanges, String rationale) {}
}
