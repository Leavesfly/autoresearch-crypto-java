package io.leavesfly.autoresearch.crypto.demo;

import io.leavesfly.autoresearch.crypto.backtest.BacktestEngine;
import io.leavesfly.autoresearch.crypto.backtest.BacktestResult;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.data.MarketDataLoader;
import io.leavesfly.autoresearch.crypto.regime.MarketRegime;
import io.leavesfly.autoresearch.crypto.regime.MarketRegimeDetector;
import io.leavesfly.autoresearch.crypto.regime.RegimeInfo;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;

import java.util.Map;

/**
 * 市场机制检测 Demo：演示如何分析当前市场状态并据此选择合适的策略。
 *
 * <p>市场机制分为 5 种：强势上涨、弱势上涨、震荡、弱势下跌、强势下跌。
 * 不同机制下适合不同的策略类型：
 * <ul>
 *   <li>趋势市场（ADX > 25）→ 趋势跟踪策略</li>
 *   <li>震荡市场（ADX < 20）→ 网格/均值回归策略</li>
 *   <li>过渡期 → 混合策略</li>
 * </ul>
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="demo.io.leavesfly.autoresearch.crypto.MarketRegimeDemo"
 * </pre>
 */
public class MarketRegimeDemo {

    /** 根据市场机制推荐策略 */
    private static final Map<MarketRegime, String> REGIME_STRATEGY_MAP = Map.of(
            MarketRegime.STRONG_UPTREND, "trendfollow",
            MarketRegime.WEAK_UPTREND, "hybrid",
            MarketRegime.RANGING, "grid",
            MarketRegime.WEAK_DOWNTREND, "hybridmm",
            MarketRegime.STRONG_DOWNTREND, "scalp"
    );

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - 市场机制检测 Demo");
        System.out.println("═══════════════════════════════════════════════════");

        // 加载数据
        String symbol = args.length > 0 ? args[0] : "ETHUSDT";
        MarketData data = MarketDataLoader.loadDefault(symbol, "5m");
        System.out.printf("[数据] %s 5m，共 %d 根K线%n%n", symbol, data.length());

        // 分析市场机制
        RegimeInfo regime = MarketRegimeDetector.analyze(data);

        System.out.println("┌──────────────────────────────────────────┐");
        System.out.println("│        📊 市场机制分析结果               │");
        System.out.println("├──────────────────────────────────────────┤");
        System.out.printf("│  机制分类:       %-22s │%n", regime.regime().label());
        System.out.printf("│  ADX 强度:       %-22s │%n", String.format("%.2f", regime.adx()));
        System.out.printf("│  EMA 关系:       %-22s │%n", regime.emaRelation());
        System.out.printf("│  价格偏离EMA200: %-22s │%n", String.format("%.2f%%", regime.priceVsEma200Pct()));
        System.out.printf("│  年化波动率:     %-22s │%n", String.format("%.2f%%", regime.volatilityAnnualized() * 100));
        System.out.println("└──────────────────────────────────────────┘");

        // 根据机制推荐策略
        String recommendedStrategy = REGIME_STRATEGY_MAP.getOrDefault(regime.regime(), "hybridmm");
        System.out.printf("%n💡 当前机制推荐策略: %s%n", recommendedStrategy);

        // 对比推荐策略与默认策略
        System.out.println("\n── 推荐策略 vs 默认策略对比回测 ──");
        BacktestEngine engine = new BacktestEngine();

        BaseStrategy recommended = BacktestEngine.createStrategy(recommendedStrategy);
        BacktestResult recommendedResult = engine.run(recommended, data, true);

        BaseStrategy defaultStrategy = BacktestEngine.createStrategy("hybridmm");
        BacktestResult defaultResult = engine.run(defaultStrategy, data, true);

        System.out.printf("%n  %-20s 得分=%.4f  收益=%.2f%%  夏普=%.3f%n",
                "[推荐] " + recommendedResult.strategyName(),
                recommendedResult.compositeScore(),
                recommendedResult.metrics().totalReturn() * 100,
                recommendedResult.metrics().sharpeRatio());
        System.out.printf("  %-20s 得分=%.4f  收益=%.2f%%  夏普=%.3f%n",
                "[默认] " + defaultResult.strategyName(),
                defaultResult.compositeScore(),
                defaultResult.metrics().totalReturn() * 100,
                defaultResult.metrics().sharpeRatio());

        double improvement = recommendedResult.compositeScore() - defaultResult.compositeScore();
        if (improvement > 0) {
            System.out.printf("%n  ✅ 机制自适应策略选择带来 %.4f 的得分提升%n", improvement);
        } else {
            System.out.printf("%n  ℹ️  默认策略在当前机制下表现更优（差距 %.4f）%n", Math.abs(improvement));
        }
    }
}
