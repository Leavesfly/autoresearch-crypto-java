package com.autoresearch.crypto.evolution;

import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.scoring.RiskAdjustedScoring;
import com.autoresearch.crypto.strategy.BaseStrategy;
import com.autoresearch.crypto.strategy.EvaluationResult;
import com.autoresearch.crypto.strategy.StrategyEvaluator;

import java.util.*;

/**
 * 多策略进化引擎（ATLAS 超级投资者模型）。
 * 维护4个独立的代理，每个代理都有自己的策略类型。
 * 每个周期：评估 → 排名 → 交叉（最差的向最好的学习）→ 变异（最好的进行探索）。
 */
public class AtlasEvolutionEngine {

    private static final Map<String, double[]> PARAM_BOUNDS = Map.ofEntries(
            Map.entry("window", new double[]{5, 50}),
            Map.entry("stdDev", new double[]{1.0, 4.0}),
            Map.entry("atrPeriod", new double[]{5, 30}),
            Map.entry("atrMultiplier", new double[]{1.0, 5.0}),
            Map.entry("maxHoldBars", new double[]{6, 72}),
            Map.entry("rsiThreshold", new double[]{20, 45}),
            Map.entry("entryZone", new double[]{0.0, 1.5}),
            Map.entry("adxThreshold", new double[]{15, 40}),
            Map.entry("gridSpacingPct", new double[]{0.002, 0.03}),
            Map.entry("gridLevels", new double[]{2, 12}),
            Map.entry("atrSpacingMult", new double[]{0.2, 1.5}),
            Map.entry("maxPosition", new double[]{0.5, 2.0}),
            Map.entry("trendMaPeriod", new double[]{50, 300}),
            Map.entry("rsiPeriod", new double[]{3, 21}),
            Map.entry("rsiLow", new double[]{15, 40}),
            Map.entry("rsiHigh", new double[]{60, 85}),
            Map.entry("maPeriod", new double[]{10, 50})
    );

    private static final Set<String> DISCRETE_PARAMS = Set.of(
            "window", "atrPeriod", "maxHoldBars", "rsiThreshold", "adxThreshold",
            "gridLevels", "trendMaPeriod", "rsiPeriod", "rsiLow", "rsiHigh", "maPeriod"
    );

    private final List<Agent> agents;
    private final StrategyEvaluator evaluator;
    private final double mutationScale;
    private final double crossoverRate;
    private final double temperature;
    private final Random rng;

    public AtlasEvolutionEngine(List<Agent> agents, StrategyEvaluator evaluator,
                                double mutationScale, double crossoverRate,
                                double temperature, long seed) {
        this.agents = agents;
        this.evaluator = evaluator != null ? evaluator : new StrategyEvaluator();
        this.mutationScale = mutationScale;
        this.crossoverRate = crossoverRate;
        this.temperature = temperature;
        this.rng = new Random(seed);
    }

    public AtlasEvolutionEngine() {
        this(createDefaultAgents(), new StrategyEvaluator(), 0.10, 0.30, 2.0, 42);
    }

    public static List<Agent> createDefaultAgents() {
        return List.of(
                new Agent("Alpha", "趋势跟踪", "TrendStrategy",
                        Map.of("window", 15, "stdDev", 2.5, "atrMultiplier", 2.0,
                                "maxHoldBars", 36, "rsiThreshold", 35, "entryZone", 0.3,
                                "useAdx", true, "adxThreshold", 20)),
                new Agent("Beta", "均值回归", "PureActionStrategy",
                        Map.of("window", 20, "stdDev", 2.0, "atrPeriod", 14,
                                "atrMultiplier", 2.5, "maxHoldBars", 24, "entryZone", 0.0,
                                "enableShort", true)),
                new Agent("Gamma", "网格交易", "GridStrategy",
                        Map.of("gridSpacingPct", 0.008, "gridLevels", 5,
                                "atrPeriod", 14, "atrSpacingMult", 0.5,
                                "maxPosition", 1.0, "trendMaPeriod", 100)),
                new Agent("Delta", "事件驱动", "HybridMeanRevMomentumStrategy",
                        Map.of("rsiPeriod", 5, "rsiLow", 28, "rsiHigh", 72,
                                "maPeriod", 25, "atrPeriod", 12, "atrMultiplier", 3.5,
                                "maxHoldBars", 24, "enableShort", true))
        );
    }

    /**
     * 通过在边界内应用随机噪声来变异参数集。
     */
    public Map<String, Object> mutate(Map<String, Object> params, double scale) {
        Map<String, Object> mutated = new HashMap<>(params);
        for (Map.Entry<String, Object> entry : params.entrySet()) {
            String key = entry.getKey();
            Object val = entry.getValue();
            if (!(val instanceof Number)) continue;

            double[] bounds = PARAM_BOUNDS.get(key);
            if (bounds == null) continue;

            double current = ((Number) val).doubleValue();
            double range = bounds[1] - bounds[0];
            double noise = rng.nextGaussian() * scale * range;
            double newVal = Math.max(bounds[0], Math.min(bounds[1], current + noise));

            if (DISCRETE_PARAMS.contains(key)) {
                mutated.put(key, (int) Math.round(newVal));
            } else {
                mutated.put(key, newVal);
            }
        }
        return mutated;
    }

    /**
     * 交叉操作：最差的代理从最好的代理的参数中学习。
     */
    public Map<String, Object> crossover(Map<String, Object> worst, Map<String, Object> best) {
        Map<String, Object> child = new HashMap<>(worst);
        for (Map.Entry<String, Object> entry : best.entrySet()) {
            if (rng.nextDouble() < crossoverRate && worst.containsKey(entry.getKey())) {
                child.put(entry.getKey(), entry.getValue());
            }
        }
        return child;
    }

    /**
     * 使用基于最近分数的 softmax 重新平衡代理权重。
     */
    public void rebalanceWeights() {
        double[] scores = new double[agents.size()];
        double maxScore = Double.NEGATIVE_INFINITY;
        for (int i = 0; i < agents.size(); i++) {
            scores[i] = agents.get(i).recentScore();
            maxScore = Math.max(maxScore, scores[i]);
        }

        double sumExp = 0;
        double[] expScores = new double[agents.size()];
        for (int i = 0; i < agents.size(); i++) {
            expScores[i] = Math.exp((scores[i] - maxScore) / temperature);
            sumExp += expScores[i];
        }

        for (int i = 0; i < agents.size(); i++) {
            agents.get(i).setWeight(sumExp > 0 ? expScores[i] / sumExp : 0.25);
        }
    }

    /**
     * 运行一个进化周期：评估所有代理，进化最差的，探索最好的。
     */
    public void evolve(MarketData data) {
        // Sort agents by recent score
        agents.sort(Comparator.comparingDouble(Agent::recentScore));

        Agent worst = agents.get(0);
        Agent best = agents.get(agents.size() - 1);

        // Worst learns from best
        Map<String, Object> crossedParams = crossover(worst.getParams(), best.getParams());
        worst.setParams(mutate(crossedParams, mutationScale));
        worst.setGeneration(worst.getGeneration() + 1);

        // Best explores with larger mutation
        best.setParams(mutate(best.getParams(), mutationScale * 1.5));
        best.setGeneration(best.getGeneration() + 1);

        // Rebalance
        rebalanceWeights();
    }

    /**
     * 检查死亡代理并触发复活机制。
     */
    public List<String> checkDeadAgents() {
        List<String> deadAgents = new ArrayList<>();
        for (Agent agent : agents) {
            if (RiskAdjustedScoring.detectDeadAgent(agent.getScoreHistory())) {
                deadAgents.add(agent.getName());
            }
        }
        return deadAgents;
    }

    public List<Agent> getAgents() { return agents; }
    public StrategyEvaluator getEvaluator() { return evaluator; }
}
