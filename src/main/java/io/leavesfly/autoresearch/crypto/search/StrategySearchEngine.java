package io.leavesfly.autoresearch.crypto.search;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;
import io.leavesfly.autoresearch.crypto.strategy.EvaluationResult;
import io.leavesfly.autoresearch.crypto.strategy.StrategyEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * 策略参数搜索引擎。
 * 支持网格搜索和随机搜索两种模式，对每种策略类型在指定参数空间中寻找最优参数组合。
 */
public class StrategySearchEngine {

    private static final Logger logger = LoggerFactory.getLogger(StrategySearchEngine.class);

    private final StrategyEvaluator evaluator;
    private final Random rng;
    private final int timeBudgetSeconds;

    public StrategySearchEngine(int timeBudgetSeconds) {
        this.evaluator = new StrategyEvaluator();
        this.rng = new Random(42);
        this.timeBudgetSeconds = timeBudgetSeconds;
    }

    public StrategySearchEngine() {
        this(TradingConfig.TIME_BUDGET);
    }

    /**
     * 搜索结果记录。
     */
    public record SearchResult(
            String strategyName,
            Map<String, Object> bestParams,
            double bestScore,
            double totalReturn,
            double sharpeRatio,
            double maxDrawdown,
            double winRate,
            int tradeCount,
            int totalTrials
    ) {}

    /**
     * 对单个策略类型执行随机搜索。
     *
     * @param strategyFactory 策略工厂函数（接受参数Map，返回策略实例）
     * @param paramSpace      参数搜索空间
     * @param data            市场数据
     * @param enableShort     是否允许做空
     * @param maxTrials       最大试验次数
     * @return 搜索结果
     */
    public SearchResult randomSearch(
            StrategyFactory strategyFactory,
            Map<String, double[]> paramSpace,
            MarketData data,
            boolean enableShort,
            int maxTrials) {

        double bestScore = 0;
        Map<String, Object> bestParams = new HashMap<>();
        double bestReturn = 0, bestSharpe = 0, bestDD = 0, bestWR = 0;
        int bestTrades = 0;
        int trials = 0;

        long startTime = System.currentTimeMillis();
        long deadline = startTime + (long) timeBudgetSeconds * 1000;

        logger.info("开始随机搜索，最大试验次数: {}，时间预算: {}s", maxTrials, timeBudgetSeconds);

        for (int trial = 0; trial < maxTrials; trial++) {
            // 检查时间预算
            if (System.currentTimeMillis() > deadline) {
                logger.info("时间预算耗尽，已完成 {} 次试验", trials);
                break;
            }

            // 随机采样参数
            Map<String, Object> params = sampleParams(paramSpace);
            trials++;

            try {
                BaseStrategy strategy = strategyFactory.create(params);
                int[] signals = strategy.generateSignals(data, enableShort);
                EvaluationResult result = evaluator.evaluate(signals, data.close());

                if (result.score() > bestScore) {
                    bestScore = result.score();
                    bestParams = new HashMap<>(params);
                    bestReturn = result.metrics().totalReturn();
                    bestSharpe = result.metrics().sharpeRatio();
                    bestDD = result.metrics().maxDrawdown();
                    bestWR = result.metrics().winRate();
                    bestTrades = result.trades().size();

                    logger.info("  [{}] 新最优: score={}, ret={}%, sharpe={}, dd={}%",
                            trials, String.format("%.4f", bestScore),
                            String.format("%.2f", bestReturn * 100),
                            String.format("%.2f", bestSharpe),
                            String.format("%.2f", bestDD * 100));
                }
            } catch (Exception e) {
                // 跳过无效参数组合
            }
        }

        String strategyName = "";
        try {
            strategyName = strategyFactory.create(bestParams).name();
        } catch (Exception ignored) {}

        return new SearchResult(strategyName, bestParams, bestScore,
                bestReturn, bestSharpe, bestDD, bestWR, bestTrades, trials);
    }

    /**
     * 从参数空间中随机采样一组参数。
     * 参数空间格式：key -> [min, max]（连续）或 [val1, val2, val3...]（离散候选值）
     */
    private Map<String, Object> sampleParams(Map<String, double[]> paramSpace) {
        Map<String, Object> params = new HashMap<>();
        for (Map.Entry<String, double[]> entry : paramSpace.entrySet()) {
            String key = entry.getKey();
            double[] bounds = entry.getValue();

            if (bounds.length == 2) {
                // 连续范围 [min, max]
                double value = bounds[0] + rng.nextDouble() * (bounds[1] - bounds[0]);
                // 整数参数自动取整
                if (isIntegerParam(key)) {
                    params.put(key, (int) Math.round(value));
                } else {
                    params.put(key, Math.round(value * 1000.0) / 1000.0);
                }
            } else {
                // 离散候选值列表
                int idx = rng.nextInt(bounds.length);
                double value = bounds[idx];
                if (isIntegerParam(key)) {
                    params.put(key, (int) value);
                } else {
                    params.put(key, value);
                }
            }
        }
        return params;
    }

    /** 判断参数是否应为整数类型 */
    private boolean isIntegerParam(String key) {
        return Set.of("window", "atrPeriod", "maxHoldBars", "rsiPeriod",
                "rsiLow", "rsiHigh", "maPeriod", "adxPeriod", "adxThreshold",
                "gridLevels", "trendMaPeriod", "longMaPeriod", "pullMaPeriod",
                "maFast", "maSlow", "rsiOversold", "rsiOverbought"
        ).contains(key);
    }

    /**
     * 对多个策略类型执行并行搜索，返回所有策略的最优结果。
     */
    public List<SearchResult> searchAll(
            Map<String, StrategyFactory> strategies,
            Map<String, Map<String, double[]>> paramSpaces,
            MarketData data,
            boolean enableShort,
            int trialsPerStrategy) {

        List<SearchResult> results = new ArrayList<>();

        for (Map.Entry<String, StrategyFactory> entry : strategies.entrySet()) {
            String name = entry.getKey();
            StrategyFactory factory = entry.getValue();
            Map<String, double[]> space = paramSpaces.getOrDefault(name, Map.of());

            logger.info("===== 搜索策略: {} =====", name);
            SearchResult result = randomSearch(factory, space, data, enableShort, trialsPerStrategy);
            results.add(result);

            logger.info("  最终: score={}, ret={}%, sharpe={}",
                    String.format("%.4f", result.bestScore()),
                    String.format("%.2f", result.totalReturn() * 100),
                    String.format("%.2f", result.sharpeRatio()));
        }

        // 按得分降序排列
        results.sort(Comparator.comparingDouble(SearchResult::bestScore).reversed());

        logger.info("===== 搜索结果汇总 =====");
        for (int i = 0; i < results.size(); i++) {
            SearchResult r = results.get(i);
            logger.info("  #{}: {} | score={} | ret={}% | sharpe={} | dd={}% | trades={}",
                    i + 1, r.strategyName(),
                    String.format("%.4f", r.bestScore()),
                    String.format("%.2f", r.totalReturn() * 100),
                    String.format("%.2f", r.sharpeRatio()),
                    String.format("%.2f", r.maxDrawdown() * 100),
                    r.tradeCount());
        }

        return results;
    }

    /**
     * 策略工厂接口。
     */
    @FunctionalInterface
    public interface StrategyFactory {
        BaseStrategy create(Map<String, Object> params);
    }
}
