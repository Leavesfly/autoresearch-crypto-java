package com.autoresearch.crypto.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TradingConfig - 交易配置常量")
class TradingConfigTest {

    @Test
    @DisplayName("路径常量应非空")
    void pathConstants_shouldNotBeNull() {
        assertThat(TradingConfig.PROJECT_DIR).isNotNull();
        assertThat(TradingConfig.DATA_DIR).isNotNull();
        assertThat(TradingConfig.CHECKPOINT_DIR).isNotNull();
        assertThat(TradingConfig.LOG_DIR).isNotNull();
        assertThat(TradingConfig.SEARCH_RESULTS_DIR).isNotNull();
    }

    @Test
    @DisplayName("交易参数应为正数")
    void tradingParams_shouldBePositive() {
        assertThat(TradingConfig.INITIAL_CAPITAL).isGreaterThan(0);
        assertThat(TradingConfig.COMMISSION).isGreaterThanOrEqualTo(0);
        assertThat(TradingConfig.SLIPPAGE).isGreaterThanOrEqualTo(0);
        assertThat(TradingConfig.MIN_NOTIONAL).isGreaterThan(0);
    }

    @Test
    @DisplayName("时间常量应一致：BARS_PER_YEAR = BARS_PER_DAY_5M * 365")
    void timeConstants_shouldBeConsistent() {
        assertThat(TradingConfig.BARS_PER_YEAR)
                .isEqualTo(TradingConfig.BARS_PER_DAY_5M * TradingConfig.TRADING_DAYS_PER_YEAR);
        assertThat(TradingConfig.BARS_PER_DAY_5M).isEqualTo(288);
    }

    @Test
    @DisplayName("INTERVAL_SECONDS 应包含常见K线周期")
    void intervalSeconds_shouldContainCommonIntervals() {
        assertThat(TradingConfig.INTERVAL_SECONDS).containsKeys("1m", "5m", "15m", "1h", "4h", "1d");
        assertThat(TradingConfig.INTERVAL_SECONDS.get("5m")).isEqualTo(300);
        assertThat(TradingConfig.INTERVAL_SECONDS.get("1d")).isEqualTo(86400);
    }

    @Test
    @DisplayName("评估阈值应在合理范围内")
    void evaluationThresholds_shouldBeReasonable() {
        assertThat(TradingConfig.SEARCH_MIN_TRADES).isGreaterThan(0);
        assertThat(TradingConfig.EVAL_MIN_TRADES).isGreaterThan(0);
        assertThat(TradingConfig.EVAL_MAX_DRAWDOWN).isBetween(0.0, 1.0);
        assertThat(TradingConfig.EVAL_MIN_EQUITY_RATIO).isBetween(0.0, 1.0);
    }

    @Test
    @DisplayName("默认策略参数 Map 应非空")
    void defaultStrategyParams_shouldNotBeEmpty() {
        assertThat(TradingConfig.DEFAULT_TREND_PARAMS).isNotEmpty();
        assertThat(TradingConfig.DEFAULT_SCALP_PARAMS).isNotEmpty();
        assertThat(TradingConfig.DEFAULT_HYBRID_MM_PARAMS).isNotEmpty();
        assertThat(TradingConfig.DEFAULT_PURE_ACTION_PARAMS).isNotEmpty();
    }

    @Test
    @DisplayName("默认符号列表应包含 BTC 和 ETH")
    void defaultSymbols_shouldContainMainCoins() {
        assertThat(TradingConfig.DEFAULT_SYMBOLS).contains("BTCUSDT", "ETHUSDT");
    }

    @Test
    @DisplayName("默认间隔应为 5m")
    void defaultInterval_shouldBe5m() {
        assertThat(TradingConfig.DEFAULT_INTERVAL).isEqualTo("5m");
    }
}
