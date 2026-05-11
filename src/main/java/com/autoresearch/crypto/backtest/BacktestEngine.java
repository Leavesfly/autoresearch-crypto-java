package com.autoresearch.crypto.backtest;

import com.autoresearch.crypto.config.TradingConfig;
import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.data.MarketDataLoader;
import com.autoresearch.crypto.regime.MarketRegimeDetector;
import com.autoresearch.crypto.regime.RegimeInfo;
import com.autoresearch.crypto.strategy.BaseStrategy;
import com.autoresearch.crypto.strategy.EvaluationResult;
import com.autoresearch.crypto.strategy.PerformanceMetrics;
import com.autoresearch.crypto.strategy.StrategyEvaluator;
import com.autoresearch.crypto.strategy.impl.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;

/**
 * 回测引擎。加载市场数据、运行策略并报告结果。
 */
public class BacktestEngine {

    private static final Logger logger = LoggerFactory.getLogger(BacktestEngine.class);

    private final StrategyEvaluator evaluator;

    public BacktestEngine() {
        this.evaluator = new StrategyEvaluator();
    }

    public BacktestEngine(double initialCapital, double commission, double slippage) {
        this.evaluator = new StrategyEvaluator(initialCapital, commission, slippage);
    }

    /**
     * 使用给定策略对市场数据执行回测。
     */
    public BacktestResult run(BaseStrategy strategy, MarketData data, boolean enableShort) {
        logger.info("开始回测: strategy={}, bars={}, enableShort={}",
                strategy.name(), data.length(), enableShort);

        // 分析市场机制
        RegimeInfo regime = MarketRegimeDetector.analyze(data);
        logger.info("市场机制: {} (ADX={}, Vol={}%)",
                regime.regime().label(),
                String.format("%.1f", regime.adx()),
                String.format("%.1f", regime.volatilityAnnualized() * 100));

        // 生成交易信号
        int[] signals = strategy.generateSignals(data, enableShort);

        // 评估
        EvaluationResult result = evaluator.evaluate(signals, data.close());

        PerformanceMetrics metrics = result.metrics();
        logger.info("=== 回测结果 ===");
        logger.info("策略:         {}", strategy.name());
        logger.info("总收益:       {}%", String.format("%.2f", metrics.totalReturn() * 100));
        logger.info("年化收益:     {}%", String.format("%.2f", metrics.annualizedReturn() * 100));
        logger.info("夏普比率:     {}", String.format("%.3f", metrics.sharpeRatio()));
        logger.info("最大回撤:     {}%", String.format("%.2f", metrics.maxDrawdown() * 100));
        logger.info("胜率:         {}%", String.format("%.1f", metrics.winRate() * 100));
        logger.info("交易次数:     {}", result.trades().size());
        logger.info("综合得分:     {}", String.format("%.4f", result.score()));

        return new BacktestResult(strategy.name(), metrics, result.score(),
                result.trades().size(), result.equityCurve(), regime);
    }

    /**
     * 根据策略名称创建策略实例（使用默认参数）。
     */
    public static BaseStrategy createStrategy(String name) {
        return switch (name.toLowerCase()) {
            case "trend" -> new TrendStrategy(new HashMap<>(TradingConfig.DEFAULT_TREND_PARAMS));
            case "scalp" -> new ScalpStrategy(new HashMap<>(TradingConfig.DEFAULT_SCALP_PARAMS));
            case "hybridmm", "hybrid_mm" -> new HybridMeanRevMomentumStrategy(
                    new HashMap<>(TradingConfig.DEFAULT_HYBRID_MM_PARAMS));
            case "pureaction", "pure_action" -> new PureActionStrategy(
                    new HashMap<>(TradingConfig.DEFAULT_PURE_ACTION_PARAMS));
            case "pureactionv2", "pure_action_v2" -> new PureActionV2Strategy(new HashMap<>());
            case "grid" -> new GridStrategy(new HashMap<>());
            case "trendfollow", "trend_follow" -> new TrendFollowStrategy(new HashMap<>());
            case "adaptive" -> new AdaptiveStrategy(new HashMap<>());
            case "hybrid" -> new HybridStrategy(new HashMap<>());
            case "multitf", "multi_tf_ensemble" -> new MultiTfEnsembleStrategy(new HashMap<>());
            default -> throw new IllegalArgumentException("未知策略: " + name);
        };
    }

    /**
     * 命令行入口：回测指定策略。
     * 用法：java BacktestEngine --symbol ETHUSDT --interval 5m --strategy hybridmm
     */
    public static void main(String[] args) {
        String symbol = "ETHUSDT";
        String interval = "5m";
        String strategyName = "hybridmm";
        boolean enableShort = true;

        // 解析命令行参数
        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "--symbol" -> symbol = args[++i];
                case "--interval" -> interval = args[++i];
                case "--strategy" -> strategyName = args[++i];
                case "--no-short" -> enableShort = false;
            }
        }

        try {
            MarketData data = MarketDataLoader.loadDefault(symbol, interval);
            BaseStrategy strategy = createStrategy(strategyName);
            BacktestEngine engine = new BacktestEngine();
            engine.run(strategy, data, enableShort);
        } catch (IOException e) {
            logger.error("加载数据失败: {}", e.getMessage());
            System.exit(1);
        }
    }
}
