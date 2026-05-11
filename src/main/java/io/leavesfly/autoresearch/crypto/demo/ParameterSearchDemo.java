package io.leavesfly.autoresearch.crypto.demo;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;
import io.leavesfly.autoresearch.crypto.search.StrategySearchEngine;
import io.leavesfly.autoresearch.crypto.search.StrategySearchEngine.SearchResult;
import io.leavesfly.autoresearch.crypto.strategy.impl.TrendStrategy;
import io.leavesfly.autoresearch.crypto.strategy.impl.ScalpStrategy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 参数搜索 Demo：演示如何使用 StrategySearchEngine 寻找最优策略参数。
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="demo.io.leavesfly.autoresearch.crypto.ParameterSearchDemo"
 * </pre>
 */
public class ParameterSearchDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - 参数搜索 Demo");
        System.out.println("═══════════════════════════════════════════════════");

        // 加载数据
        MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");
        System.out.printf("[数据] 已加载 %d 根K线%n%n", data.length());

        // 创建搜索引擎（时间预算 120 秒）
        StrategySearchEngine searchEngine = new StrategySearchEngine(120);

        // ====== 单策略随机搜索 ======
        System.out.println("── 单策略随机搜索: TrendStrategy ──");

        Map<String, double[]> trendParamSpace = new HashMap<>();
        trendParamSpace.put("window", new double[]{10, 40});
        trendParamSpace.put("stdDev", new double[]{1.2, 3.5});
        trendParamSpace.put("atrMultiplier", new double[]{1.5, 4.5});
        trendParamSpace.put("maxHoldBars", new double[]{12, 72});
        trendParamSpace.put("rsiThreshold", new double[]{20, 45});
        trendParamSpace.put("adxThreshold", new double[]{15, 35});

        SearchResult trendResult = searchEngine.randomSearch(
                params -> new TrendStrategy(new HashMap<>(params)),
                trendParamSpace,
                data,
                true,   // 允许做空
                500     // 最多尝试 500 组参数
        );

        printSearchResult("Trend", trendResult);

        // ====== 多策略并行搜索 ======
        System.out.println("\n── 多策略并行搜索 ──");

        Map<String, StrategySearchEngine.StrategyFactory> strategies = Map.of(
                "trend", params -> new TrendStrategy(new HashMap<>(params)),
                "scalp", params -> new ScalpStrategy(new HashMap<>(params))
        );

        Map<String, double[]> scalpParamSpace = new HashMap<>();
        scalpParamSpace.put("window", new double[]{8, 30});
        scalpParamSpace.put("stdDev", new double[]{1.5, 3.0});
        scalpParamSpace.put("atrMultiplier", new double[]{1.0, 3.5});
        scalpParamSpace.put("maxHoldBars", new double[]{6, 24});

        Map<String, Map<String, double[]>> paramSpaces = Map.of(
                "trend", trendParamSpace,
                "scalp", scalpParamSpace
        );

        List<SearchResult> allResults = searchEngine.searchAll(
                strategies, paramSpaces, data, true, 300
        );

        System.out.println("\n┌──────────────────┬──────────┬──────────┬──────────┬────────┐");
        System.out.println("│ 策略             │ 最优得分 │ 收益%    │ 夏普     │ 试验数 │");
        System.out.println("├──────────────────┼──────────┼──────────┼──────────┼────────┤");
        for (SearchResult result : allResults) {
            System.out.printf("│ %-16s │ %8.4f │ %7.2f%% │ %8.3f │ %6d │%n",
                    result.strategyName(),
                    result.bestScore(),
                    result.totalReturn() * 100,
                    result.sharpeRatio(),
                    result.totalTrials());
        }
        System.out.println("└──────────────────┴──────────┴──────────┴──────────┴────────┘");

        // 输出最优参数
        if (!allResults.isEmpty()) {
            SearchResult best = allResults.get(0);
            System.out.printf("%n🏆 全局最优: %s (得分 %.4f)%n", best.strategyName(), best.bestScore());
            System.out.println("   最优参数: " + best.bestParams());
        }
    }

    private static void printSearchResult(String label, SearchResult result) {
        System.out.printf("  [%s] 最优得分: %.4f%n", label, result.bestScore());
        System.out.printf("  收益: %.2f%%, 夏普: %.3f, 回撤: %.2f%%, 胜率: %.1f%%%n",
                result.totalReturn() * 100,
                result.sharpeRatio(),
                result.maxDrawdown() * 100,
                result.winRate() * 100);
        System.out.printf("  交易次数: %d, 总试验: %d%n", result.tradeCount(), result.totalTrials());
        System.out.println("  最优参数: " + result.bestParams());
    }
}
