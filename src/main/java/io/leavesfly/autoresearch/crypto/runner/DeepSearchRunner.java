package io.leavesfly.autoresearch.crypto.runner;

import io.leavesfly.autoresearch.crypto.backtest.BacktestEngine;
import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;
import io.leavesfly.autoresearch.crypto.search.StrategySearchEngine;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 深度参数搜索运行器。
 * 对应 Python 版本的 search_deep.py。
 *
 * 功能：
 * 1. 先对所有策略做粗粒度随机搜索，找出 Top-N 策略
 * 2. 对 Top-N 策略在最优参数附近展开更密集的精细网格搜索
 * 3. 输出最终排名和最优参数
 *
 * 用法：java DeepSearchRunner --topN 3 --coarseTrials 200 --fineTrials 500
 */
public class DeepSearchRunner {

    private static final Logger logger = LoggerFactory.getLogger(DeepSearchRunner.class);

    private final int topN;
    private final int coarseTrials;
    private final int fineTrials;

    public DeepSearchRunner(int topN, int coarseTrials, int fineTrials) {
        this.topN = topN;
        this.coarseTrials = coarseTrials;
        this.fineTrials = fineTrials;
    }

    /**
     * 执行深度搜索流程。
     */
    public void run() throws IOException {
        // 1. 加载数据
        MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("  深度参数搜索运行器");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("数据: {} 根K线", data.length());
        logger.info("粗搜索: {} 次/策略 | 精细搜索: {} 次/策略 | Top-{}", coarseTrials, fineTrials, topN);

        // 2. 粗粒度搜索：所有策略
        logger.info("\n===== 阶段1: 粗粒度搜索（全部策略）=====");
        StrategySearchEngine searchEngine = new StrategySearchEngine();

        Map<String, StrategySearchEngine.StrategyFactory> strategies = buildStrategyFactories();
        Map<String, Map<String, double[]>> paramSpaces = buildCoarseParamSpaces();

        List<StrategySearchEngine.SearchResult> coarseResults = searchEngine.searchAll(strategies, paramSpaces, data, true, coarseTrials);

        // 3. 取 Top-N 策略
        List<StrategySearchEngine.SearchResult> topResults = coarseResults.subList(0, Math.min(topN, coarseResults.size()));
        logger.info("\n===== 阶段2: 精细搜索（Top-{} 策略）=====", topN);

        List<StrategySearchEngine.SearchResult> fineResults = new ArrayList<>();
        for (StrategySearchEngine.SearchResult top : topResults) {
            String name = top.strategyName();
            logger.info("精细搜索策略: {} (粗搜索得分={})", name, String.format("%.4f", top.bestScore()));

            // 基于粗搜索最优参数生成精细搜索空间
            Map<String, double[]> fineSpace = buildFineParamSpace(top.bestParams());
            StrategySearchEngine.StrategyFactory factory = strategies.get(name);

            if (factory != null && !fineSpace.isEmpty()) {
                StrategySearchEngine.SearchResult fineResult = searchEngine.randomSearch(factory, fineSpace, data, true, fineTrials);
                fineResults.add(fineResult);
                logger.info("  精细搜索完成: score={} (提升={})",
                        String.format("%.4f", fineResult.bestScore()),
                        String.format("%+.4f", fineResult.bestScore() - top.bestScore()));
            } else {
                fineResults.add(top);
            }
        }

        // 4. 最终排名
        fineResults.sort(Comparator.comparingDouble(StrategySearchEngine.SearchResult::bestScore).reversed());
        logger.info("\n═══════════════════════════════════════════════════════");
        logger.info("  最终排名");
        logger.info("═══════════════════════════════════════════════════════");
        for (int i = 0; i < fineResults.size(); i++) {
            StrategySearchEngine.SearchResult r = fineResults.get(i);
            logger.info("  #{}: {} | score={} | ret={}% | sharpe={} | dd={}%",
                    i + 1, r.strategyName(),
                    String.format("%.4f", r.bestScore()),
                    String.format("%.2f", r.totalReturn() * 100),
                    String.format("%.2f", r.sharpeRatio()),
                    String.format("%.2f", r.maxDrawdown() * 100));
        }

        // 5. 保存结果
        saveResults(fineResults);
    }

    /**
     * 基于粗搜索最优参数构建精细搜索空间。
     * 在最优参数附近 ±20% 范围内展开更密集的搜索。
     */
    private Map<String, double[]> buildFineParamSpace(Map<String, Object> bestParams) {
        Map<String, double[]> fineSpace = new HashMap<>();
        for (Map.Entry<String, Object> entry : bestParams.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            if (value instanceof Number num) {
                double v = num.doubleValue();
                if (v == 0) continue;
                // 在最优值 ±20% 范围
                double low = v * 0.8;
                double high = v * 1.2;
                fineSpace.put(key, new double[]{low, high});
            }
        }
        return fineSpace;
    }

    /** 构建策略工厂映射（工厂接收搜索参数并应用到策略实例） */
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

    /** 构建粗粒度参数搜索空间 */
    private Map<String, Map<String, double[]>> buildCoarseParamSpaces() {
        Map<String, Map<String, double[]>> spaces = new LinkedHashMap<>();

        spaces.put("HybridMM", Map.of(
                "rsiPeriod", new double[]{5, 7, 10, 14},
                "rsiLow", new double[]{25, 28, 30, 35},
                "rsiHigh", new double[]{70, 72, 75, 78},
                "maPeriod", new double[]{20, 25, 30, 40},
                "atrPeriod", new double[]{10, 12, 14},
                "atrMultiplier", new double[]{2.0, 2.5, 3.0, 3.5},
                "maxHoldBars", new double[]{12, 24, 36, 48}
        ));

        spaces.put("Adaptive", Map.of(
                "rsiLow", new double[]{25, 30, 35},
                "rsiHigh", new double[]{65, 70, 75},
                "adxThreshold", new double[]{20, 25, 30},
                "atrPeriod", new double[]{7, 14},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36}
        ));

        spaces.put("PureAction", Map.of(
                "window", new double[]{10, 15, 20, 24},
                "stdDev", new double[]{1.5, 2.0, 2.5, 3.0},
                "atrPeriod", new double[]{7, 14},
                "atrMultiplier", new double[]{1.5, 2.0, 2.5, 3.0},
                "maxHoldBars", new double[]{12, 24, 36}
        ));

        spaces.put("TrendFollow", Map.of(
                "longMaPeriod", new double[]{50, 100, 200},
                "pullMaPeriod", new double[]{10, 15, 20},
                "atrPeriod", new double[]{7, 14},
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

    /** 保存搜索结果 */
    private void saveResults(List<StrategySearchEngine.SearchResult> results) {
        try {
            Path outDir = TradingConfig.SEARCH_RESULTS_DIR;
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve("deep_search_result.json");

            List<Map<String, Object>> output = new ArrayList<>();
            for (StrategySearchEngine.SearchResult r : results) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("strategy", r.strategyName());
                item.put("score", r.bestScore());
                item.put("totalReturn", r.totalReturn());
                item.put("sharpeRatio", r.sharpeRatio());
                item.put("maxDrawdown", r.maxDrawdown());
                item.put("winRate", r.winRate());
                item.put("trades", r.tradeCount());
                item.put("params", r.bestParams());
                output.add(item);
            }

            Map<String, Object> wrapper = new LinkedHashMap<>();
            wrapper.put("timestamp", LocalDateTime.now().toString());
            wrapper.put("results", output);

            ObjectMapper mapper = new ObjectMapper();
            Files.writeString(outFile, mapper.writerWithDefaultPrettyPrinter().writeValueAsString(wrapper));
            logger.info("结果已保存: {}", outFile);
        } catch (Exception e) {
            logger.error("保存结果失败: {}", e.getMessage());
        }
    }

    /**
     * 命令行入口。
     */
    public static void main(String[] args) {
        int topN = 3;
        int coarseTrials = 200;
        int fineTrials = 500;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--topN" -> topN = Integer.parseInt(args[++i]);
                case "--coarseTrials" -> coarseTrials = Integer.parseInt(args[++i]);
                case "--fineTrials" -> fineTrials = Integer.parseInt(args[++i]);
            }
        }

        try {
            new DeepSearchRunner(topN, coarseTrials, fineTrials).run();
        } catch (IOException e) {
            logger.error("深度搜索失败: {}", e.getMessage());
            System.exit(1);
        }
    }
}
