package com.autoresearch.crypto;

import com.autoresearch.crypto.indicator.Indicators;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class IndicatorsTest {

    private static final double[] CLOSE = {
        100, 102, 101, 103, 105, 104, 106, 108, 107, 109,
        110, 108, 106, 107, 109, 111, 113, 112, 114, 116
    };
    private static final double[] HIGH = {
        101, 103, 102, 104, 106, 105, 107, 109, 108, 110,
        111, 109, 107, 108, 110, 112, 114, 113, 115, 117
    };
    private static final double[] LOW = {
        99, 101, 100, 102, 104, 103, 105, 107, 106, 108,
        109, 107, 105, 106, 108, 110, 112, 111, 113, 115
    };

    @Test
    void computeEma_shouldReturnCorrectLength() {
        double[] ema = Indicators.computeEma(CLOSE, 5);
        assertThat(ema).hasSize(CLOSE.length);
        assertThat(ema[0]).isEqualTo(CLOSE[0]);
    }

    @Test
    void computeRsi_shouldBeBetween0And100() {
        double[] rsi = Indicators.computeRsi(CLOSE, 14);
        assertThat(rsi).hasSize(CLOSE.length);
        for (double val : rsi) {
            assertThat(val).isBetween(0.0, 100.0);
        }
    }

    @Test
    void computeAtr_shouldBePositive() {
        double[] atr = Indicators.computeAtr(HIGH, LOW, CLOSE, 5);
        assertThat(atr).hasSize(CLOSE.length);
        // 预热期后，ATR 应为正值
        assertThat(atr[4]).isGreaterThan(0);
    }

    @Test
    void computeAdx_shouldReturnThreeArrays() {
        double[][] adx = Indicators.computeAdx(HIGH, LOW, CLOSE, 5);
        assertThat(adx.length).isEqualTo(3);
        assertThat(adx[0]).hasSize(CLOSE.length); // ADX
        assertThat(adx[1]).hasSize(CLOSE.length); // +DI
        assertThat(adx[2]).hasSize(CLOSE.length); // -DI
    }

    @Test
    void computeMacd_shouldReturnThreeArrays() {
        double[][] macd = Indicators.computeMacd(CLOSE, 5, 10, 3);
        assertThat(macd.length).isEqualTo(3);
        assertThat(macd[0]).hasSize(CLOSE.length); // MACD 线
        assertThat(macd[1]).hasSize(CLOSE.length); // 信号线
        assertThat(macd[2]).hasSize(CLOSE.length); // 柱状图
    }

    @Test
    void computeBollingerBands_upperAboveLower() {
        double[][] bb = Indicators.computeBollingerBands(CLOSE, 5, 2.0);
        assertThat(bb.length).isEqualTo(3);
        // 预热期后，上轨 > 中轨 > 下轨
        for (int i = 4; i < CLOSE.length; i++) {
            assertThat(bb[0][i]).isGreaterThanOrEqualTo(bb[1][i]); // 上轨 >= 中轨
            assertThat(bb[1][i]).isGreaterThanOrEqualTo(bb[2][i]); // 中轨 >= 下轨
        }
    }

    @Test
    void computeSma_shouldConverge() {
        double[] sma = Indicators.computeSma(CLOSE, 5);
        assertThat(sma).hasSize(CLOSE.length);
        // 索引4处的SMA应为前5个值的平均值
        double expected = (100 + 102 + 101 + 103 + 105) / 5.0;
        assertThat(sma[4]).isCloseTo(expected, within(0.01));
    }

    @Test
    void computeStochastic_shouldBeBetween0And100() {
        double[] stoch = Indicators.computeStochastic(HIGH, LOW, CLOSE, 5);
        assertThat(stoch).hasSize(CLOSE.length);
        for (int i = 4; i < stoch.length; i++) {
            assertThat(stoch[i]).isBetween(0.0, 100.0);
        }
    }

    @Test
    void computeObv_shouldChangeWithPrice() {
        double[] volume = new double[CLOSE.length];
        java.util.Arrays.fill(volume, 1000.0);
        double[] obv = Indicators.computeObv(CLOSE, volume);
        assertThat(obv).hasSize(CLOSE.length);
        // 价格从100上涨到102，因此OBV应该增加
        assertThat(obv[1]).isGreaterThan(obv[0]);
    }
}
