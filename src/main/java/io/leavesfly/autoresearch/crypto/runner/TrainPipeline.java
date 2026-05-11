package io.leavesfly.autoresearch.crypto.runner;

import io.leavesfly.autoresearch.crypto.backtest.BacktestEngine;
import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import io.leavesfly.autoresearch.crypto.data.DataManager;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;
import io.leavesfly.autoresearch.crypto.evolution.Agent;
import io.leavesfly.autoresearch.crypto.evolution.ExperimentLog;
import io.leavesfly.autoresearch.crypto.evolution.GepaReflectionEngine;
import io.leavesfly.autoresearch.crypto.search.StrategySearchEngine;
import io.leavesfly.autoresearch.crypto.search.StrategySearchEngine.SearchResult;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;
import io.leavesfly.autoresearch.crypto.strategy.EvaluationResult;
import io.leavesfly.autoresearch.crypto.strategy.StrategyEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 完整训练流水线。
 * 对应 Python 版本的 scripts/train_all.py。
 *
 * 四阶段流程：
 * Phase 1: 网格搜索全部策略（粗粒度）
 * Phase 2: 对 Top-2 策略做精细搜索
 * Phase 3: GEPA V2 反思式进化优化冠军策略
 * Phase 4: Walk-Forward 验证 + 保存最佳 checkpoint
 *
 * 用法：java TrainPipeline
 *       java TrainPipeline --quick    (快速模式，每策略30s)
 */
public class TrainPipeline {

    private static final Logger logger = LoggerFactory.getLogger(TrainPipeline.class);

    private final boolean quickMode;
    private final StrategyEvaluator evaluator;

    public TrainPipeline(boolean quickMode) {
        this.quickMode = quickMode;
        this.evaluator = new StrategyEvaluator();
    }

    /**
     * 执行完整训练流程。
     */
    public void run() throws IOException {
        long startTime = System.currentTimeMillis();

        // 加载数据
        MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("  完整训练流水线 ({})", quickMode ? "快速模式" : "标准模式");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("数据: {} 根K线", data.length());

        int trialsPerStrategy = quickMode ? 50 : 200;
        int fineTrials = quickMode ? 100 : 500;
        int gepaCycles = quickMode ? 5 : 30;

        // ===== Phase 1: 粗粒度搜索全部策略 =====
        logger.info("\n╔═══════════════════════════════════════════╗");
        logger.info("║  Phase 1: 粗粒度网格搜索（全部策略）      ║");
        logger.info("╚═══════════════════════════════════════════╝");

        // 使用后15%数据做验证
        int valStart = (int) (data.length() * 0.85);
        MarketData valData = MarketDataLoader.slice(data, valStart, data.length());

        StrategySearchEngine searchEngine = new StrategySearchEngine();
        Map<String, StrategySearchEngine.StrategyFactory> strategies = buildStrategyFactories();
        Map<String, Map<String, double[]>> paramSpaces = buildParamSpaces();

        List<SearchResult> phase1Results = searchEngine.searchAll(
                strategies, paramSpaces, valData, true, trialsPerStrategy);

        // ===== Phase 2: 对 Top-2 做精细搜索 =====
        logger.info("\n╔═══════════════════════════════════════════╗");
        logger.info("║  Phase 2: 精细搜索（Top-2 策略）          ║");
        logger.info("╚═══════════════════════════════════════════╝");

        List<SearchResult> top2 = phase1Results.subList(0, Math.min(2, phase1Results.size()));
        SearchResult champion = top2.get(0);

        for (SearchResult top : top2) {
            String name = top.strategyName();
            Map<String, double[]> fineSpace = buildFineSpace(top.bestParams());
            StrategySearchEngine.StrategyFactory factory = strategies.get(name);

            if (factory != null && !fineSpace.isEmpty()) {
                SearchResult fineResult = searchEngine.randomSearch(factory, fineSpace, valData, true, fineTrials);
                if (fineResult.bestScore() > champion.bestScore()) {
                    champion = fineResult;
                }
                logger.info("  {} 精细搜索: score={} (原={})",
                        name, String.format("%.4f", fineResult.bestScore()),
                        String.format("%.4f", top.bestScore()));
            }
        }

        logger.info("Phase 2 冠军: {} (score={})", champion.strategyName(),
                String.format("%.4f", champion.bestScore()));

        // ===== Phase 3: GEPA V2 反思式进化 =====
        logger.info("\n╔═══════════════════════════════════════════╗");
        logger.info("║  Phase 3: GEPA V2 反思式进化              ║");
        logger.info("╚═══════════════════════════════════════════╝");

        Map<String, Object> bestParams = new HashMap<>(champion.bestParams());
        double bestScore = champion.bestScore();

        // 构造一个虚拟 Agent 用于 GEPA 进化
        Agent gepaAgent = new Agent("Champion", "冠军策略",
                champion.strategyName(), bestParams);
        List<Agent> gepaAgents = List.of(gepaAgent);
        GepaReflectionEngine gepa = new GepaReflectionEngine(gepaAgents);

        for (int cycle = 0; cycle < gepaCycles; cycle++) {
            GepaReflectionEngine.Hypothesis hypothesis = gepa.generateHypothesis(gepaAgent);

            // 应用假设参数并评估
            Map<String, Object> testParams = new HashMap<>(bestParams);
            testParams.putAll(hypothesis.paramChanges());
            double score = evaluateWithParams(champion.strategyName(), testParams, valData);

            // 记录实验
            boolean improved = score > bestScore;
            ExperimentLog log = new ExperimentLog();
            log.setAgent(gepaAgent.getName());
            log.setHypothesis(hypothesis.text());
            log.setParamsBefore(bestParams);
            log.setParamsAfter(testParams);
            log.setScoreBefore(bestScore);
            log.setScoreAfter(score);
            log.setResultSummary(improved ? "改进" : "未改进");
            gepa.recordExperiment(log);

            if (improved) {
                bestParams = testParams;
                bestScore = score;
                gepaAgent.setParams(bestParams);
                logger.info("  周期 {}/{}: 发现更优 score={}", cycle + 1, gepaCycles,
                        String.format("%.4f", bestScore));
            }

            // 每5个周期做元反思
            if (gepa.shouldReflect()) {
                gepa.metaReflect();
            }
        }

        logger.info("GEPA 进化完成: 最终 score={} (提升={})",
                String.format("%.4f", bestScore),
                String.format("%+.4f", bestScore - champion.bestScore()));

        // ===== Phase 4: Walk-Forward 验证 + 保存 =====
        logger.info("\n╔═══════════════════════════════════════════╗");
        logger.info("║  Phase 4: Walk-Forward 验证 + 保存        ║");
        logger.info("╚═══════════════════════════════════════════╝");

        List<DataManager.WalkForwardWindow> windows = DataManager.walkForwardSplit(data, TradingConfig.WF_N_WINDOWS);
        double totalWfScore = 0;
        int passedWindows = 0;

        for (int w = 0; w < windows.size(); w++) {
            DataManager.WalkForwardWindow window = windows.get(w);
            MarketData trainData = window.trainData();
            MarketData testData = window.validData();

            // 在验证集上评估
            double wfScore = evaluateWithParams(champion.strategyName(), bestParams, testData);
            totalWfScore += wfScore;

            boolean passed = wfScore > 0;
            if (passed) passedWindows++;

            logger.info("  窗口 {}/{}: train={} bars, test={} bars, score={} {}",
                    w + 1, windows.size(), trainData.length(), testData.length(),
                    String.format("%.4f", wfScore), passed ? "✓" : "✗");
        }

        double avgWfScore = windows.isEmpty() ? 0 : totalWfScore / windows.size();
        boolean wfPassed = passedWindows >= (windows.size() / 2 + 1);

        logger.info("Walk-Forward 结果: 平均 score={}, 通过 {}/{} 窗口 {}",
                String.format("%.4f", avgWfScore), passedWindows, windows.size(),
                wfPassed ? "✓ 通过" : "✗ 未通过");

        // 保存 checkpoint
        saveCheckpoint(champion.strategyName(), bestParams, bestScore, avgWfScore, wfPassed);

        // 总结
        long elapsed = (System.currentTimeMillis() - startTime) / 1000;
        logger.info("\n═══════════════════════════════════════════════════════");
        logger.info("  训练完成！耗时 {}m {}s", elapsed / 60, elapsed % 60);
        logger.info("  冠军策略: {}", champion.strategyName());
        logger.info("  最优得分: {}", String.format("%.4f", bestScore));
        logger.info("  WF 验证: {} (avg={})",
                wfPassed ? "通过" : "未通过", String.format("%.4f", avgWfScore));
        logger.info("═══════════════════════════════════════════════════════");
    }

    /** 使用指定参数评估策略得分 */
    private double evaluateWithParams(String strategyName, Map<String, Object> params, MarketData data) {
        try {
            BaseStrategy strategy = BacktestEngine.createStrategy(strategyName);
            strategy.setParams(params);
            int[] signals = strategy.generateSignals(data, true);
            EvaluationResult result = evaluator.evaluate(signals, data.close());
            return result.score();
        } catch (Exception e) {
            return 0;
        }
    }

    /** 基于粗搜索参数构建精细空间（±20%） */
    private Map<String, double[]> buildFineSpace(Map<String, Object> bestParams) {
        Map<String, double[]> space = new HashMap<>();
        for (Map.Entry<String, Object> entry : bestParams.entrySet()) {
            if (entry.getValue() instanceof Number num) {
                double v = num.doubleValue();
                if (v == 0) continue;
                space.put(entry.getKey(), new double[]{v * 0.8, v * 1.2});
            }
        }
        return space;
    }

    /** 构建策略工厂（工厂接收搜索参数并应用到策略实例） */
    private Map<String, StrategySearchEngine.StrategyFactory> buildStrategyFactories() {
        Map<String, StrategySearchEngine.StrategyFactory> factories = new LinkedHashMap<>();
        factories.put("HybridMM", params -> { var s = BacktestEngine.createStrategy("hybridmm"); s.setParams(params); return s; });
        factories.put("Adaptive", params -> { var s = BacktestEngine.createStrategy("adaptive"); s.setParams(params); return s; });
        factories.put("PureAction", params -> { var s = BacktestEngine.createStrategy("pureaction"); s.setParams(params); return s; });
        factories.put("TrendFollow", params -> { var s = BacktestEngine.createStrategy("trendfollow"); s.setParams(params); return s; });
        factories.put("Trend", params -> { var s = BacktestEngine.createStrategy("trend"); s.setParams(params); return s; });
        factories.put("Scalp", params -> { var s = BacktestEngine.createStrategy("scalp"); s.setParams(params); return s; });
        factories.put("Grid", params -> { var s = BacktestEngine.createStrategy("grid"); s.setParams(params); return s; });
        return factories;
    }

    /** 构建参数搜索空间 */
    private Map<String, Map<String, double[]>> buildParamSpaces() {
        Map<String, Map<String, double[]>> spaces = new LinkedHashMap<>();
        spaces.put("HybridMM", Map.of(
                "rsiPeriod", new double[]{5, 7, 10, 14},
                "rsiLow", new double[]{25, 28, 30, 35},
                "rsiHigh", new double[]{70, 72, 75, 78},
                "atrMultiplier", new double[]{2.0, 2.5, 3.0, 3.5},
                "maxHoldBars", new double[]{12, 24, 36, 48}
        ));
        spaces.put("Adaptive", Map.of(
                "rsiLow", new double[]{25, 30, 35},
                "rsiHigh", new double[]{65, 70, 75},
                "adxThreshold", new double[]{20, 25, 30},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36}
        ));
        spaces.put("PureAction", Map.of(
                "window", new double[]{10, 15, 20, 24},
                "stdDev", new double[]{1.5, 2.0, 2.5, 3.0},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36}
        ));
        spaces.put("TrendFollow", Map.of(
                "longMaPeriod", new double[]{50, 100, 200},
                "pullMaPeriod", new double[]{10, 15, 20},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36}
        ));
        spaces.put("Trend", Map.of(
                "window", new double[]{12, 15, 20, 24, 30},
                "stdDev", new double[]{1.5, 2.0, 2.5, 3.0},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36, 48}
        ));
        spaces.put("Scalp", Map.of(
                "window", new double[]{8, 10, 12, 15},
                "stdDev", new double[]{1.0, 1.2, 1.5, 1.8},
                "maxHoldBars", new double[]{6, 8, 12, 18}
        ));
        spaces.put("Grid", Map.of(
                "gridLevels", new double[]{3, 5, 7, 10},
                "atrPeriod", new double[]{7, 14},
                "atrMultiplier", new double[]{1.0, 1.5, 2.0}
        ));
        return spaces;
    }

    /** 保存训练 checkpoint */
    private void saveCheckpoint(String strategyName, Map<String, Object> params,
                                double score, double wfScore, boolean wfPassed) {
        try {
            Path checkpointDir = TradingConfig.CHECKPOINT_DIR;
            Files.createDirectories(checkpointDir);
            Path outFile = checkpointDir.resolve("best_checkpoint.json");

            Map<String, Object> checkpoint = new LinkedHashMap<>();
            checkpoint.put("timestamp", LocalDateTime.now().toString());
            checkpoint.put("strategy", strategyName);
            checkpoint.put("params", params);
            checkpoint.put("score", score);
            checkpoint.put("walkForwardScore", wfScore);
            checkpoint.put("walkForwardPassed", wfPassed);

            ObjectMapper mapper = new ObjectMapper();
            Files.writeString(outFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(checkpoint));
            logger.info("Checkpoint 已保存: {}", outFile);
        } catch (Exception e) {
            logger.error("保存 checkpoint 失败: {}", e.getMessage());
        }
    }

    /**
     * 命令行入口。
     * 用法：java TrainPipeline [--quick]
     */
    public static void main(String[] args) {
        boolean quickMode = false;
        for (String arg : args) {
            if ("--quick".equals(arg)) quickMode = true;
        }

        try {
            new TrainPipeline(quickMode).run();
        } catch (IOException e) {
            logger.error("训练流水线失败: {}", e.getMessage());
            System.exit(1);
        }
    }
}
