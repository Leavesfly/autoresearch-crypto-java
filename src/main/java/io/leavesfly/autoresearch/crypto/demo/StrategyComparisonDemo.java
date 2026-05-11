package io.leavesfly.autoresearch.crypto.demo;

import io.leavesfly.autoresearch.crypto.backtest.BacktestEngine;
import io.leavesfly.autoresearch.crypto.backtest.BacktestResult;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;
import io.leavesfly.autoresearch.crypto.strategy.PerformanceMetrics;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 多策略对比 Demo：对所有内置策略进行回测对比，按综合得分排序。
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="demo.io.leavesfly.autoresearch.crypto.StrategyComparisonDemo"
 * </pre>
 */
public class StrategyComparisonDemo {

    private static final String[] ALL_STRATEGIES = {
            "trend", "scalp", "hybridmm", "pureaction", "pureactionv2",
            "grid", "trendfollow", "adaptive", "hybrid", "multitf"
    };

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - 多策略对比 Demo");
        System.out.println("═══════════════════════════════════════════════════════════════");

        // 加载数据
        String symbol = args.length > 0 ? args[0] : "ETHUSDT";
        String interval = args.length > 1 ? args[1] : "5m";
        boolean enableShort = true;

        MarketData data = MarketDataLoader.loadDefault(symbol, interval);
        System.out.printf("\n[数据] %s %s，共 %d 根K线%n%n", symbol, interval, data.length());

        // 对所有策略进行回测
        BacktestEngine engine = new BacktestEngine();
        List<BacktestResult> results = new ArrayList<>();

        for (String strategyName : ALL_STRATEGIES) {
            try {
                BaseStrategy strategy = BacktestEngine.createStrategy(strategyName);
                BacktestResult result = engine.run(strategy, data, enableShort);
                results.add(result);
            } catch (Exception e) {
                System.err.printf("[WARN] 策略 %s 回测失败: %s%n", strategyName, e.getMessage());
            }
        }

        // 按综合得分排序
        results.sort(Comparator.comparingDouble(BacktestResult::compositeScore).reversed());

        // 输出对比表格
        System.out.println("\n┌────────────────────────────┬──────────┬──────────┬──────────┬──────────┬────────┬────────┐");
        System.out.println("│ 策略                       │ 综合得分 │ 总收益%  │ 夏普比率 │ 最大回撤%│ 胜率%  │ 交易数 │");
        System.out.println("├────────────────────────────┼──────────┼──────────┼──────────┼──────────┼────────┼────────┤");

        for (BacktestResult result : results) {
            PerformanceMetrics metrics = result.metrics();
            System.out.printf("│ %-26s │ %8.4f │ %7.2f%% │ %8.3f │ %7.2f%% │ %5.1f%% │ %6d │%n",
                    result.strategyName(),
                    result.compositeScore(),
                    metrics.totalReturn() * 100,
                    metrics.sharpeRatio(),
                    metrics.maxDrawdown() * 100,
                    metrics.winRate() * 100,
                    result.tradeCount());
        }

        System.out.println("└────────────────────────────┴──────────┴──────────┴──────────┴──────────┴────────┴────────┘");

        // 输出冠军策略
        if (!results.isEmpty()) {
            BacktestResult best = results.get(0);
            System.out.printf("%n🏆 最优策略: %s (得分 %.4f, 夏普 %.3f)%n",
                    best.strategyName(), best.compositeScore(), best.metrics().sharpeRatio());
        }
    }
}
