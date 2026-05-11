package com.autoresearch.crypto.demo;

import com.autoresearch.crypto.backtest.BacktestEngine;
import com.autoresearch.crypto.backtest.BacktestResult;
import com.autoresearch.crypto.data.MarketData;
import com.autoresearch.crypto.data.MarketDataLoader;
import com.autoresearch.crypto.strategy.BaseStrategy;
import com.autoresearch.crypto.strategy.PerformanceMetrics;

import java.nio.file.Path;

/**
 * 基础回测 Demo：演示如何加载数据并运行单策略回测。
 *
 * <p>使用方式：
 * <pre>
 *   mvn compile exec:java -Dexec.mainClass="com.autoresearch.crypto.demo.BasicBacktestDemo"
 * </pre>
 *
 * <p>前置条件：需要在 data/crypto/ 目录下准备好 ETHUSDT_5m.csv 数据文件。
 * 可通过 DataDownloader 下载，或运行 prepare_crypto.py 脚本。
 */
public class BasicBacktestDemo {

    public static void main(String[] args) throws Exception {
        System.out.println("═══════════════════════════════════════════════════");
        System.out.println("  AutoResearch Crypto - 基础回测 Demo");
        System.out.println("═══════════════════════════════════════════════════");

        // 1. 加载市场数据
        String symbol = "ETHUSDT";
        String interval = "5m";
        MarketData data = MarketDataLoader.loadDefault(symbol, interval);
        System.out.printf("\n[数据] 已加载 %s %s，共 %d 根K线%n", symbol, interval, data.length());

        // 2. 创建策略（使用默认参数）
        String strategyName = "hybridmm";
        BaseStrategy strategy = BacktestEngine.createStrategy(strategyName);
        System.out.printf("[策略] %s%n", strategy.name());

        // 3. 运行回测（允许做空）
        BacktestEngine engine = new BacktestEngine();
        BacktestResult result = engine.run(strategy, data, true);

        // 4. 输出关键指标
        PerformanceMetrics metrics = result.metrics();
        System.out.println("\n╔══════════════════════════════════════╗");
        System.out.println("║        回测结果摘要                  ║");
        System.out.println("╠══════════════════════════════════════╣");
        System.out.printf("║  综合得分:   %-20s  ║%n", String.format("%.4f", result.compositeScore()));
        System.out.printf("║  总收益:     %-20s  ║%n", String.format("%.2f%%", metrics.totalReturn() * 100));
        System.out.printf("║  年化收益:   %-20s  ║%n", String.format("%.2f%%", metrics.annualizedReturn() * 100));
        System.out.printf("║  夏普比率:   %-20s  ║%n", String.format("%.3f", metrics.sharpeRatio()));
        System.out.printf("║  最大回撤:   %-20s  ║%n", String.format("%.2f%%", metrics.maxDrawdown() * 100));
        System.out.printf("║  胜率:       %-20s  ║%n", String.format("%.1f%%", metrics.winRate() * 100));
        System.out.printf("║  交易次数:   %-20s  ║%n", result.tradeCount());
        System.out.printf("║  市场机制:   %-20s  ║%n", result.regime().regime().label());
        System.out.println("╚══════════════════════════════════════╝");

        // 5. 自定义参数示例
        System.out.println("\n--- 自定义参数回测 ---");
        BacktestEngine customEngine = new BacktestEngine(
                50000.0,  // 初始资金 50000 USDT
                0.0005,   // 佣金 0.05%
                0.0003    // 滑点 0.03%
        );
        BacktestResult customResult = customEngine.run(strategy, data, false);
        System.out.printf("仅做多模式综合得分: %.4f%n", customResult.compositeScore());
    }
}
