package com.autoresearch.crypto.runner;

import com.autoresearch.crypto.backtest.BacktestEngine;
import com.autoresearch.crypto.config.TradingConfig;
import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.data.MarketDataLoader;
import com.autoresearch.crypto.evolution.Agent;
import com.autoresearch.crypto.evolution.AtlasEvolutionEngine;
import com.autoresearch.crypto.evolution.ExperimentLog;
import com.autoresearch.crypto.evolution.GepaReflectionEngine;
import com.autoresearch.crypto.strategy.BaseStrategy;
import com.autoresearch.crypto.strategy.EvaluationResult;
import com.autoresearch.crypto.strategy.StrategyEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.*;

/**
 * ATLAS 多策略进化 + GEPA 反思式进化运行器。
 * 对应 Python 版本的 scripts/evolve.py + scripts/evolve_gepa.py。
 *
 * 功能：
 * 1. 加载市场数据
 * 2. 运行 ATLAS 4-Agent 进化
 * 3. 评估每个 Agent 的独立表现
 * 4. 评估集成表现
 * 5. 可选：运行 GEPA 反思式进化进一步优化冠军策略
 * 6. 保存结果到 JSON
 *
 * 用法：java EvolutionRunner --generations 15 --days 60 --gepa
 */
public class EvolutionRunner {

    private static final Logger logger = LoggerFactory.getLogger(EvolutionRunner.class);

    private final StrategyEvaluator evaluator;
    private final int generations;
    private final int days;
    private final boolean enableGepa;

    public EvolutionRunner(int generations, int days, boolean enableGepa) {
        this.evaluator = new StrategyEvaluator();
        this.generations = generations;
        this.days = days;
        this.enableGepa = enableGepa;
    }

    /**
     * 执行完整的进化流程。
     */
    public void run() throws IOException {
        // 1. 加载数据
        MarketData fullData = MarketDataLoader.loadDefault("ETHUSDT", "5m");
        int barsToUse = Math.min(TradingConfig.BARS_PER_DAY_5M * days, fullData.length());
        MarketData data = MarketDataLoader.slice(fullData, fullData.length() - barsToUse, fullData.length());

        double startPrice = data.close()[0];
        double endPrice = data.close()[data.length() - 1];
        double marketReturn = (endPrice / startPrice - 1);

        logger.info("═══════════════════════════════════════════════════════");
        logger.info("  ATLAS 多策略进化运行器");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("数据: {} 根K线 ({} 天)", data.length(), days);
        logger.info("价格: {} -> {} ({})%", String.format("%.1f", startPrice),
                String.format("%.1f", endPrice), String.format("%+.2f", marketReturn * 100));

        // 2. 运行 ATLAS 进化
        logger.info("\n===== 阶段1: ATLAS 4-Agent 进化 ({} 代) =====", generations);
        AtlasEvolutionEngine engine = new AtlasEvolutionEngine();
        List<Agent> agents = engine.getAgents();

        for (int gen = 0; gen < generations; gen++) {
            // 每代进化：评估每个代理得分，然后运行一轮进化
            for (Agent agent : agents) {
                EvaluationResult result = evaluateAgent(agent, data);
                agent.addScore(result.score());
            }

            engine.evolve(data);

            if ((gen + 1) % 5 == 0) {
                logger.info("第 {}/{} 代完成", gen + 1, generations);
                for (Agent a : agents) {
                    logger.info("  {} [{}]: weight={}, score={}",
                            a.getName(), a.getStyle(),
                            String.format("%.3f", a.getWeight()),
                            String.format("%.4f", a.recentScore()));
                }
            }
        }

        // 3. 评估每个 Agent 独立表现
        logger.info("\n═══════════════════════════════════════════════════════");
        logger.info("各 Agent 独立表现");
        logger.info("═══════════════════════════════════════════════════════");
        logger.info("{}\t{}\t{}\t{}\t{}\t{}",
                padRight("Agent", 8), padRight("权重", 7), padRight("收益", 8),
                padRight("夏普", 7), padRight("回撤", 7), padRight("交易", 6));
        logger.info("─".repeat(52));

        List<Map<String, Object>> agentResults = new ArrayList<>();
        for (Agent agent : agents) {
            EvaluationResult result = evaluateAgent(agent, data);
            Map<String, Object> info = new LinkedHashMap<>();
            info.put("name", agent.getName());
            info.put("style", agent.getStyle());
            info.put("weight", agent.getWeight());
            info.put("totalReturn", result.metrics().totalReturn());
            info.put("sharpeRatio", result.metrics().sharpeRatio());
            info.put("maxDrawdown", result.metrics().maxDrawdown());
            info.put("trades", result.trades().size());
            info.put("params", agent.getParams());
            agentResults.add(info);

            logger.info("{}\t{}\t{}%\t{}\t{}%\t{}",
                    padRight(agent.getName(), 8),
                    String.format("%.3f", agent.getWeight()),
                    String.format("%+.2f", result.metrics().totalReturn() * 100),
                    String.format("%.2f", result.metrics().sharpeRatio()),
                    String.format("%.1f", result.metrics().maxDrawdown() * 100),
                    result.trades().size());
        }

        // 4. 可选：GEPA 反思式进化
        if (enableGepa) {
            logger.info("\n===== 阶段2: GEPA 反思式进化 =====");
            Agent champion = agents.stream()
                    .max(Comparator.comparingDouble(Agent::recentScore))
                    .orElse(agents.get(0));

            logger.info("冠军策略: {} (score={})", champion.getName(),
                    String.format("%.4f", champion.recentScore()));

            GepaReflectionEngine gepa = new GepaReflectionEngine(agents);
            Map<String, Object> originalParams = new HashMap<>(champion.getParams());
            double bestScore = champion.recentScore();

            for (int cycle = 0; cycle < 10; cycle++) {
                GepaReflectionEngine.Hypothesis hypothesis = gepa.generateHypothesis(champion);

                // 应用假设参数并评估
                Map<String, Object> testParams = new HashMap<>(champion.getParams());
                testParams.putAll(hypothesis.paramChanges());
                champion.setParams(testParams);
                EvaluationResult result = evaluateAgent(champion, data);
                double score = result.score();

                // 记录实验
                boolean improved = score > bestScore;
                ExperimentLog log = new ExperimentLog();
                log.setAgent(champion.getName());
                log.setHypothesis(hypothesis.text());
                log.setParamsBefore(originalParams);
                log.setParamsAfter(testParams);
                log.setScoreBefore(bestScore);
                log.setScoreAfter(score);
                log.setResultSummary(improved ? "改进" : "未改进");
                gepa.recordExperiment(log);

                if (improved) {
                    bestScore = score;
                    originalParams = new HashMap<>(testParams);
                    logger.info("  周期 {}: 发现更优参数 score={}", cycle + 1, String.format("%.4f", score));
                } else {
                    champion.setParams(originalParams);
                }

                if (gepa.shouldReflect()) {
                    String reflection = gepa.metaReflect();
                    logger.info("  元反思: {}", reflection);
                }
            }

            logger.info("GEPA 进化完成，最终 score={}", String.format("%.4f", bestScore));
        }

        // 5. 保存结果
        saveResults(agentResults, marketReturn);
        logger.info("\n进化完成！");
    }

    /** 评估单个 Agent 的策略表现 */
    private EvaluationResult evaluateAgent(Agent agent, MarketData data) {
        try {
            // 根据 Agent 的策略类名和参数创建策略实例
            String className = agent.getStrategyClassName().toLowerCase()
                    .replace("strategy", "");
            BaseStrategy strategy = BacktestEngine.createStrategy(className);
            int[] signals = strategy.generateSignals(data, true);
            return evaluator.evaluate(signals, data.close());
        } catch (Exception e) {
            return EvaluationResult.empty();
        }
    }

    /** 保存进化结果到 JSON */
    private void saveResults(List<Map<String, Object>> agentResults, double marketReturn) {
        try {
            Path outDir = TradingConfig.SEARCH_RESULTS_DIR;
            Files.createDirectories(outDir);
            Path outFile = outDir.resolve("evolution_result.json");

            Map<String, Object> output = new LinkedHashMap<>();
            output.put("timestamp", LocalDateTime.now().toString());
            output.put("agents", agentResults);
            output.put("marketReturn", marketReturn);

            ObjectMapper mapper = new ObjectMapper();
            String json = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(output);
            Files.writeString(outFile, json);
            logger.info("结果已保存: {}", outFile);
        } catch (Exception e) {
            logger.error("保存结果失败: {}", e.getMessage());
        }
    }

    private String padRight(String s, int width) {
        return String.format("%-" + width + "s", s);
    }

    /**
     * 命令行入口。
     * 用法：java EvolutionRunner --generations 15 --days 60 --gepa
     */
    public static void main(String[] args) {
        int generations = 15;
        int days = 60;
        boolean enableGepa = false;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--generations" -> generations = Integer.parseInt(args[++i]);
                case "--days" -> days = Integer.parseInt(args[++i]);
                case "--gepa" -> enableGepa = true;
            }
        }

        try {
            new EvolutionRunner(generations, days, enableGepa).run();
        } catch (IOException e) {
            logger.error("进化运行失败: {}", e.getMessage());
            System.exit(1);
        }
    }
}
