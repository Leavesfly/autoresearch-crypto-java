package com.autoresearch.crypto.demo;

import com.autoresearch.crypto.backtest.BacktestEngine;
import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.data.MarketDataLoader;
import com.autoresearch.crypto.evolution.Agent;
import com.autoresearch.crypto.evolution.AtlasEvolutionEngine;
import com.autoresearch.crypto.strategy.BaseStrategy;
import com.autoresearch.crypto.strategy.EvaluationResult;
import com.autoresearch.crypto.strategy.StrategyEvaluator;

import java.util.List;
import java.util.Map;

/**
 * 进化引擎 Demo：演示 ATLAS 多代理进化系统的使用。
 *
 * <p>ATLAS 模型维护 4 个独立的交易代理（Alpha/Beta/Gamma/Delta），
 * 每轮进化中：评估 → 排名 → 交叉（最差向最好学习）→ 变异（最好探索新方向）。
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="com.autoresearch.crypto.demo.EvolutionDemo"
 * </pre>
 */
public class EvolutionDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - ATLAS 进化引擎 Demo");
        System.out.println("═══════════════════════════════════════════════════");

        // 加载数据
        MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");
        System.out.printf("[数据] 已加载 %d 根K线%n%n", data.length());

        // 创建进化引擎（使用默认的 4 个代理）
        AtlasEvolutionEngine engine = new AtlasEvolutionEngine();
        StrategyEvaluator evaluator = engine.getEvaluator();

        // 打印初始代理信息
        System.out.println("── 初始代理配置 ──");
        for (Agent agent : engine.getAgents()) {
            System.out.printf("  [%s] 风格=%s, 策略=%s, 权重=%.2f%n",
                    agent.getName(), agent.getStyle(),
                    agent.getStrategyClassName(), agent.getWeight());
        }

        // 运行多轮进化
        int generations = 5;
        System.out.printf("%n── 开始进化 (%d 轮) ──%n", generations);

        for (int gen = 1; gen <= generations; gen++) {
            System.out.printf("%n=== 第 %d 轮 ===%n", gen);

            // 评估每个代理
            for (Agent agent : engine.getAgents()) {
                BaseStrategy strategy = BacktestEngine.createStrategy(
                        mapStrategyClassName(agent.getStrategyClassName()));
                strategy.setParams(agent.getParams());

                int[] signals = strategy.generateSignals(data, true);
                EvaluationResult result = evaluator.evaluate(signals, data.close());
                agent.addScore(result.score());

                System.out.printf("  [%s] 得分=%.4f, 收益=%.2f%%, 夏普=%.3f%n",
                        agent.getName(), result.score(),
                        result.metrics().totalReturn() * 100,
                        result.metrics().sharpeRatio());
            }

            // 执行进化操作
            engine.evolve(data);

            // 检查死亡代理
            List<String> deadAgents = engine.checkDeadAgents();
            if (!deadAgents.isEmpty()) {
                System.out.println("  ⚠️  死亡代理: " + deadAgents);
            }

            // 显示权重重分配
            System.out.print("  权重: ");
            for (Agent agent : engine.getAgents()) {
                System.out.printf("%s=%.2f  ", agent.getName(), agent.getWeight());
            }
            System.out.println();
        }

        // 最终结果
        System.out.println("\n═══════════════════════════════════════════════════");
        System.out.println("  进化完成 - 最终代理状态");
        System.out.println("═══════════════════════════════════════════════════");
        for (Agent agent : engine.getAgents()) {
            System.out.printf("  [%s] 代数=%d, 最近得分=%.4f, 权重=%.3f%n",
                    agent.getName(), agent.getGeneration(),
                    agent.recentScore(), agent.getWeight());
            System.out.printf("         参数: %s%n", agent.getParams());
        }
    }

    /**
     * 将策略类名映射为 createStrategy 可识别的短名称。
     */
    private static String mapStrategyClassName(String className) {
        return switch (className) {
            case "TrendStrategy" -> "trend";
            case "PureActionStrategy" -> "pureaction";
            case "GridStrategy" -> "grid";
            case "HybridMeanRevMomentumStrategy" -> "hybridmm";
            case "ScalpStrategy" -> "scalp";
            case "TrendFollowStrategy" -> "trendfollow";
            default -> "trend";
        };
    }
}
