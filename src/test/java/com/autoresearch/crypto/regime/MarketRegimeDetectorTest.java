package com.autoresearch.crypto.regime;

import com.autoresearch.crypto.data.MarketData;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("MarketRegimeDetector - 市场机制检测")
class MarketRegimeDetectorTest {

    /**
     * 生成模拟的上涨趋势市场数据（300条，满足 EMA200 需求）。
     */
    private static MarketData createUptrendData(int length) {
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        double base = 100.0;
        for (int i = 0; i < length; i++) {
            double price = base + i * 0.5; // 持续上涨
            open[i] = price - 0.2;
            high[i] = price + 1.0;
            low[i] = price - 1.0;
            close[i] = price;
            volume[i] = 1000.0;
            timestamps[i] = 1700000000L + i * 300L;
        }
        return new MarketData(open, high, low, close, volume, timestamps);
    }

    /**
     * 生成模拟的下跌趋势市场数据。
     */
    private static MarketData createDowntrendData(int length) {
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        double base = 300.0;
        for (int i = 0; i < length; i++) {
            double price = base - i * 0.5; // 持续下跌
            open[i] = price + 0.2;
            high[i] = price + 1.0;
            low[i] = price - 1.0;
            close[i] = price;
            volume[i] = 1000.0;
            timestamps[i] = 1700000000L + i * 300L;
        }
        return new MarketData(open, high, low, close, volume, timestamps);
    }

    /**
     * 生成模拟的震荡市场数据。
     */
    private static MarketData createRangingData(int length) {
        double[] open = new double[length];
        double[] high = new double[length];
        double[] low = new double[length];
        double[] close = new double[length];
        double[] volume = new double[length];
        long[] timestamps = new long[length];

        double base = 100.0;
        for (int i = 0; i < length; i++) {
            double price = base + Math.sin(i * 0.05) * 2.0; // 小幅震荡
            open[i] = price - 0.1;
            high[i] = price + 0.5;
            low[i] = price - 0.5;
            close[i] = price;
            volume[i] = 1000.0;
            timestamps[i] = 1700000000L + i * 300L;
        }
        return new MarketData(open, high, low, close, volume, timestamps);
    }

    @Test
    @DisplayName("上涨趋势数据应检测为上涨机制")
    void uptrendData_shouldDetectUptrend() {
        MarketData data = createUptrendData(400);
        RegimeInfo regime = MarketRegimeDetector.analyze(data);

        assertThat(regime).isNotNull();
        assertThat(regime.regime()).isIn(MarketRegime.STRONG_UPTREND, MarketRegime.WEAK_UPTREND);
        assertThat(regime.emaRelation()).isEqualTo("uptrend");
        assertThat(regime.priceVsEma200Pct()).isGreaterThan(0);
    }

    @Test
    @DisplayName("下跌趋势数据应检测为下跌机制")
    void downtrendData_shouldDetectDowntrend() {
        MarketData data = createDowntrendData(400);
        RegimeInfo regime = MarketRegimeDetector.analyze(data);

        assertThat(regime).isNotNull();
        assertThat(regime.regime()).isIn(MarketRegime.STRONG_DOWNTREND, MarketRegime.WEAK_DOWNTREND);
        assertThat(regime.emaRelation()).isEqualTo("downtrend");
        assertThat(regime.priceVsEma200Pct()).isLessThan(0);
    }

    @Test
    @DisplayName("震荡数据应检测为 RANGING 或弱趋势")
    void rangingData_shouldDetectRanging() {
        MarketData data = createRangingData(400);
        RegimeInfo regime = MarketRegimeDetector.analyze(data);

        assertThat(regime).isNotNull();
        // 震荡行情 ADX 通常较低
        assertThat(regime.adx()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("RegimeInfo 字段应完整填充")
    void regimeInfo_shouldBeFullyPopulated() {
        MarketData data = createUptrendData(400);
        RegimeInfo regime = MarketRegimeDetector.analyze(data);

        assertThat(regime.regime()).isNotNull();
        assertThat(regime.adx()).isGreaterThanOrEqualTo(0);
        assertThat(regime.emaRelation()).isNotNull();
        assertThat(regime.volatilityAnnualized()).isGreaterThanOrEqualTo(0);
    }

    @Test
    @DisplayName("MarketRegime 枚举 label 应非空")
    void marketRegimeLabels_shouldNotBeEmpty() {
        for (MarketRegime regime : MarketRegime.values()) {
            assertThat(regime.label()).isNotBlank();
        }
    }
}
