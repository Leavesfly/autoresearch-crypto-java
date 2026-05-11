package io.leavesfly.autoresearch.crypto.indicator;

/**
 * 技术指标计算工具类。
 * 所有方法接受 double 数组并返回 double 数组。
 * 对应 Python 版本的 dex/indicators.py。
 */
public final class Indicators {

    private Indicators() {}

    /**
     * 计算平均真实波幅（ATR）。
     */
    public static double[] computeAtr(double[] high, double[] low, double[] close, int period) {
        int length = high.length;
        double[] tr = new double[length];
        tr[0] = high[0] - low[0];

        for (int i = 1; i < length; i++) {
            double tr1 = high[i] - low[i];
            double tr2 = Math.abs(high[i] - close[i - 1]);
            double tr3 = Math.abs(low[i] - close[i - 1]);
            tr[i] = Math.max(tr1, Math.max(tr2, tr3));
        }

        double[] atr = new double[length];
        double sum = 0;
        for (int i = 0; i < period; i++) {
            sum += tr[i];
        }
        atr[period - 1] = sum / period;

        for (int i = period; i < length; i++) {
            atr[i] = (atr[i - 1] * (period - 1) + tr[i]) / period;
        }
        return atr;
    }

    /**
     * 计算 ADX、+DI、-DI 用于判断趋势强度和方向。
     * 返回三元素数组：[adx[], plusDi[], minusDi[]]。
     */
    public static double[][] computeAdx(double[] high, double[] low, double[] close, int period) {
        int length = high.length;
        double[] plusDm = new double[length];
        double[] minusDm = new double[length];

        for (int i = 1; i < length; i++) {
            double up = high[i] - high[i - 1];
            double down = low[i - 1] - low[i];
            plusDm[i] = (up > down && up > 0) ? up : 0;
            minusDm[i] = (down > up && down > 0) ? down : 0;
        }

        double[] atr = computeAtr(high, low, close, period);

        double[] plusDi = new double[length];
        double[] minusDi = new double[length];
        for (int i = period; i < length; i++) {
            if (atr[i] > 0) {
                double plusDmSum = 0;
                double minusDmSum = 0;
                for (int j = i - period + 1; j <= i; j++) {
                    plusDmSum += plusDm[j];
                    minusDmSum += minusDm[j];
                }
                plusDi[i] = 100 * plusDmSum / period / atr[i];
                minusDi[i] = 100 * minusDmSum / period / atr[i];
            }
        }

        double[] dx = new double[length];
        for (int i = period; i < length; i++) {
            double diSum = plusDi[i] + minusDi[i];
            if (diSum > 0) {
                dx[i] = 100 * Math.abs(plusDi[i] - minusDi[i]) / diSum;
            }
        }

        double[] adx = new double[length];
        if (period * 2 - 1 < length) {
            double dxSum = 0;
            for (int i = period; i < period * 2; i++) {
                dxSum += dx[i];
            }
            adx[period * 2 - 1] = dxSum / period;
        }
        for (int i = period * 2; i < length; i++) {
            adx[i] = (adx[i - 1] * (period - 1) + dx[i]) / period;
        }

        return new double[][]{adx, plusDi, minusDi};
    }

    /**
     * 计算相对强弱指标（RSI）。
     */
    public static double[] computeRsi(double[] close, int period) {
        int length = close.length;
        double[] gain = new double[length];
        double[] loss = new double[length];

        for (int i = 1; i < length; i++) {
            double delta = close[i] - close[i - 1];
            gain[i] = delta > 0 ? delta : 0;
            loss[i] = delta < 0 ? -delta : 0;
        }

        double[] avgGain = new double[length];
        double[] avgLoss = new double[length];

        double gainSum = 0, lossSum = 0;
        for (int i = 1; i <= period; i++) {
            gainSum += gain[i];
            lossSum += loss[i];
        }
        avgGain[period] = gainSum / period;
        avgLoss[period] = lossSum / period;

        for (int i = period + 1; i < length; i++) {
            avgGain[i] = (avgGain[i - 1] * (period - 1) + gain[i]) / period;
            avgLoss[i] = (avgLoss[i - 1] * (period - 1) + loss[i]) / period;
        }

        double[] rsi = new double[length];
        java.util.Arrays.fill(rsi, 50.0);
        for (int i = period; i < length; i++) {
            if (avgLoss[i] > 0) {
                rsi[i] = 100.0 - 100.0 / (1.0 + avgGain[i] / avgLoss[i]);
            } else {
                rsi[i] = 100.0;
            }
        }
        return rsi;
    }

    /**
     * 计算指数移动平均线（EMA）。
     */
    public static double[] computeEma(double[] series, int period) {
        int length = series.length;
        double alpha = 2.0 / (period + 1);
        double[] ema = new double[length];
        ema[0] = series[0];
        for (int i = 1; i < length; i++) {
            ema[i] = alpha * series[i] + (1 - alpha) * ema[i - 1];
        }
        return ema;
    }

    /**
     * 计算简单移动平均线（SMA）。
     */
    public static double[] computeSma(double[] series, int period) {
        int length = series.length;
        double[] sma = new double[length];
        double sum = 0;
        for (int i = 0; i < length; i++) {
            sum += series[i];
            if (i >= period) {
                sum -= series[i - period];
                sma[i] = sum / period;
            } else if (i == period - 1) {
                sma[i] = sum / period;
            }
        }
        return sma;
    }

    /**
     * 计算 MACD 线、信号线和柱状图。
     * 返回 [macdLine[], signalLine[], histogram[]]。
     */
    public static double[][] computeMacd(double[] close, int fast, int slow, int signal) {
        double[] emaFast = computeEma(close, fast);
        double[] emaSlow = computeEma(close, slow);
        int length = close.length;

        double[] macdLine = new double[length];
        for (int i = 0; i < length; i++) {
            macdLine[i] = emaFast[i] - emaSlow[i];
        }

        double[] signalLine = computeEma(macdLine, signal);

        double[] histogram = new double[length];
        for (int i = 0; i < length; i++) {
            histogram[i] = macdLine[i] - signalLine[i];
        }

        return new double[][]{macdLine, signalLine, histogram};
    }

    /**
     * 计算资金流量指标（MFI）。
     */
    public static double[] computeMfi(double[] high, double[] low, double[] close, double[] volume, int period) {
        int length = close.length;
        double[] typicalPrice = new double[length];
        double[] moneyFlow = new double[length];
        double[] posFlow = new double[length];
        double[] negFlow = new double[length];

        for (int i = 0; i < length; i++) {
            typicalPrice[i] = (high[i] + low[i] + close[i]) / 3.0;
            moneyFlow[i] = typicalPrice[i] * volume[i];
        }

        for (int i = 1; i < length; i++) {
            if (typicalPrice[i] > typicalPrice[i - 1]) {
                posFlow[i] = moneyFlow[i];
            } else if (typicalPrice[i] < typicalPrice[i - 1]) {
                negFlow[i] = moneyFlow[i];
            }
        }

        double[] mfi = new double[length];
        java.util.Arrays.fill(mfi, 50.0);
        for (int i = period; i < length; i++) {
            double posSum = 0, negSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                posSum += posFlow[j];
                negSum += negFlow[j];
            }
            if (negSum > 0) {
                mfi[i] = 100.0 - 100.0 / (1.0 + posSum / negSum);
            } else {
                mfi[i] = 100.0;
            }
        }
        return mfi;
    }

    /**
     * 计算随机震荡指标 %K（KDJ 中的 K 值）。
     */
    public static double[] computeStochastic(double[] high, double[] low, double[] close, int period) {
        int length = close.length;
        double[] stochK = new double[length];
        java.util.Arrays.fill(stochK, 50.0);

        for (int i = period - 1; i < length; i++) {
            double lowest = Double.MAX_VALUE;
            double highest = Double.MIN_VALUE;
            for (int j = i - period + 1; j <= i; j++) {
                if (low[j] < lowest) lowest = low[j];
                if (high[j] > highest) highest = high[j];
            }
            double denom = highest - lowest;
            stochK[i] = denom > 0 ? 100.0 * (close[i] - lowest) / denom : 50.0;
        }
        return stochK;
    }

    /**
     * 计算能量潮指标（OBV）。
     */
    public static double[] computeObv(double[] close, double[] volume) {
        int length = close.length;
        double[] obv = new double[length];
        obv[0] = volume[0];
        for (int i = 1; i < length; i++) {
            if (close[i] > close[i - 1]) {
                obv[i] = obv[i - 1] + volume[i];
            } else if (close[i] < close[i - 1]) {
                obv[i] = obv[i - 1] - volume[i];
            } else {
                obv[i] = obv[i - 1];
            }
        }
        return obv;
    }

    /**
     * 计算滚动成交量加权平均价（VWAP）。
     */
    public static double[] computeVwap(double[] high, double[] low, double[] close, double[] volume, int period) {
        int length = close.length;
        double[] typicalPrice = new double[length];
        for (int i = 0; i < length; i++) {
            typicalPrice[i] = (high[i] + low[i] + close[i]) / 3.0;
        }

        double[] vwap = new double[length];
        java.util.Arrays.fill(vwap, Double.NaN);
        for (int i = period - 1; i < length; i++) {
            double tpvSum = 0, volSum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                tpvSum += typicalPrice[j] * volume[j];
                volSum += volume[j];
            }
            vwap[i] = volSum > 1e-10 ? tpvSum / volSum : typicalPrice[i];
        }
        return vwap;
    }

    /**
     * 计算布林带。
     * 返回 [upper[], middle[], lower[]]。
     */
    public static double[][] computeBollingerBands(double[] close, int period, double stdDevMultiplier) {
        int length = close.length;
        double[] middle = computeSma(close, period);
        double[] upper = new double[length];
        double[] lower = new double[length];

        for (int i = period - 1; i < length; i++) {
            double sum = 0;
            for (int j = i - period + 1; j <= i; j++) {
                double diff = close[j] - middle[i];
                sum += diff * diff;
            }
            double stdDev = Math.sqrt(sum / period);
            upper[i] = middle[i] + stdDevMultiplier * stdDev;
            lower[i] = middle[i] - stdDevMultiplier * stdDev;
        }

        return new double[][]{upper, middle, lower};
    }
}
