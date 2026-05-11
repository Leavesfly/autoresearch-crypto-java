package io.leavesfly.autoresearch.crypto.strategy;

import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.strategy.impl.TrendStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TrendStrategy - 趋势策略")
class TrendStrategyTest {

    private static MarketData createSyntheticData(int length) {
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        double base = 100.0;
        for (int i = 0; i < length; i++) {
            double price = base + i * 0.2 + Math.sin(i * 0.05) * 5;
            open[i] = price - 0.3;
            high[i] = price + 2.0;
            low[i] = price - 2.0;
            close[i] = price;
            volume[i] = 1000 + Math.random() * 500;
            timestamps[i] = 1700000000L + i * 300L;
        }
        return new MarketData(open, high, low, close, volume, timestamps);
    }

    @Test
    @DisplayName("name 应返回 Trend")
    void name_shouldReturnTrend() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());
        assertThat(strategy.name()).isEqualTo("Trend");
    }

    @Test
    @DisplayName("generateSignals 输出长度应等于数据长度")
    void generateSignals_shouldMatchDataLength() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());
        MarketData data = createSyntheticData(300);

        int[] signals = strategy.generateSignals(data, true);

        assertThat(signals).hasSize(data.length());
    }

    @Test
    @DisplayName("信号值应在合法范围内 [0,3]")
    void signalValues_shouldBeInValidRange() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());
        MarketData data = createSyntheticData(300);

        int[] signals = strategy.generateSignals(data, true);

        for (int signal : signals) {
            assertThat(signal).isBetween(0, 3);
        }
    }

    @Test
    @DisplayName("禁用做空时不应产生做空信号")
    void disableShort_shouldNotProduceShortSignals() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());
        MarketData data = createSyntheticData(300);

        int[] signals = strategy.generateSignals(data, false);

        for (int signal : signals) {
            assertThat(signal).isNotEqualTo(3); // 不应有做空信号
        }
    }

    @Test
    @DisplayName("数据不足时应返回全零信号")
    void insufficientData_shouldReturnAllZeros() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());
        MarketData data = createSyntheticData(10); // 数据量太少

        int[] signals = strategy.generateSignals(data, true);

        assertThat(signals).hasSize(10);
        for (int signal : signals) {
            assertThat(signal).isEqualTo(0);
        }
    }

    @Test
    @DisplayName("自定义参数应被正确解析")
    void customParams_shouldBeParsedCorrectly() {
        Map<String, Object> params = new HashMap<>(Map.of(
                "window", 30,
                "stdDev", 1.5,
                "atrMultiplier", 3.0,
                "maxHoldBars", 24,
                "adxThreshold", 20,
                "rsiThreshold", 25,
                "entryZone", 0.5
        ));
        TrendStrategy strategy = new TrendStrategy(params);

        assertThat(strategy.getParams()).containsEntry("window", 30);
        assertThat(strategy.getParams()).containsEntry("stdDev", 1.5);
    }

    @Test
    @DisplayName("BaseStrategy getInt/getDouble/getBool 默认值测试")
    void baseStrategy_paramHelpers_shouldUseDefaults() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());

        // 空 params 时应使用默认值，不抛异常
        MarketData data = createSyntheticData(300);
        int[] signals = strategy.generateSignals(data, true);
        assertThat(signals).isNotNull();
    }

    @Test
    @DisplayName("setParams 应允许动态更新参数")
    void setParams_shouldUpdateParams() {
        TrendStrategy strategy = new TrendStrategy(new HashMap<>());

        Map<String, Object> newParams = new HashMap<>(Map.of("window", 50));
        strategy.setParams(newParams);

        assertThat(strategy.getParams()).containsEntry("window", 50);
    }
}
