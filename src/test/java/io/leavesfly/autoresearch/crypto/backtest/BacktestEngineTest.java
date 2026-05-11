package io.leavesfly.autoresearch.crypto.backtest;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;
import io.leavesfly.autoresearch.crypto.strategy.impl.TrendStrategy;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DisplayName("BacktestEngine - 回测引擎")
class BacktestEngineTest {

    private static MarketData createSyntheticData(int length) {
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        double base = 100.0;
        for (int i = 0; i < length; i++) {
            double price = base + i * 0.3 + Math.sin(i * 0.1) * 3;
            open[i] = price - 0.2;
            high[i] = price + 1.5;
            low[i] = price - 1.5;
            close[i] = price;
            volume[i] = 1000 + i;
            timestamps[i] = 1700000000L + i * 300L;
        }
        return new MarketData(open, high, low, close, volume, timestamps);
    }

    @Test
    @DisplayName("完整回测应返回有效结果")
    void run_shouldReturnValidResult() {
        BacktestEngine engine = new BacktestEngine();
        MarketData data = createSyntheticData(500);
        Map<String, Object> params = new HashMap<>(Map.of(
                "window", 20, "stdDev", 2.0,
                "atrMultiplier", 2.5, "maxHoldBars", 48,
                "adxThreshold", 25, "rsiThreshold", 30,
                "entryZone", 1.0
        ));
        TrendStrategy strategy = new TrendStrategy(params);

        BacktestResult result = engine.run(strategy, data, true);

        assertThat(result).isNotNull();
        assertThat(result.strategyName()).isEqualTo("Trend");
        Assertions.assertThat(result.metrics()).isNotNull();
        assertThat(result.equityCurve()).isNotEmpty();
        Assertions.assertThat(result.regime()).isNotNull();
    }

    @Test
    @DisplayName("自定义资金/手续费/滑点的引擎")
    void customCapital_shouldWorkCorrectly() {
        BacktestEngine engine = new BacktestEngine(50000, 0.001, 0.001);
        MarketData data = createSyntheticData(500);
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());

        BacktestResult result = engine.run(strategy, data, false);

        assertThat(result).isNotNull();
        Assertions.assertThat(result.metrics()).isNotNull();
    }

    // -----------------------------------------------------------------------
    // createStrategy 工厂方法测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("createStrategy 应创建所有已知策略")
    void createStrategy_shouldCreateAllKnownStrategies() {
        String[] names = {
                "trend", "scalp", "hybridmm", "hybrid_mm",
                "pureaction", "pure_action", "pureactionv2", "pure_action_v2",
                "grid", "trendfollow", "trend_follow",
                "adaptive", "hybrid", "multitf", "multi_tf_ensemble"
        };

        for (String name : names) {
            BaseStrategy strategy = BacktestEngine.createStrategy(name);
            assertThat(strategy).as("策略 '%s' 应被创建", name).isNotNull();
            assertThat(strategy.name()).isNotBlank();
        }
    }

    @Test
    @DisplayName("createStrategy 对未知策略应抛出异常")
    void createStrategy_unknownName_shouldThrow() {
        assertThatThrownBy(() -> BacktestEngine.createStrategy("nonexistent"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("未知策略");
    }
}
