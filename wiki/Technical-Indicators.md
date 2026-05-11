# 技术指标

## 概述

`Indicators` 工具类（`com.autoresearch.crypto.indicator`）提供 11 种常用技术分析指标的纯 Java 实现。所有方法为静态方法，接受 `double[]` 数组输入，返回相同长度的 `double[]` 数组。

## 指标列表

| # | 指标 | 方法 | 类型 |
|---|------|------|------|
| 1 | ATR | `computeAtr` | 波动率 |
| 2 | ADX / +DI / -DI | `computeAdx` | 趋势强度 |
| 3 | RSI | `computeRsi` | 动量 |
| 4 | EMA | `computeEma` | 趋势 |
| 5 | SMA | `computeSma` | 趋势 |
| 6 | MACD | `computeMacd` | 动量 |
| 7 | MFI | `computeMfi` | 量价 |
| 8 | Stochastic %K | `computeStochastic` | 动量 |
| 9 | OBV | `computeObv` | 量价 |
| 10 | VWAP | `computeVwap` | 量价 |
| 11 | Bollinger Bands | `computeBollingerBands` | 波动率 |

---

## 1. ATR — 平均真实波幅

**用途**：衡量市场波动程度，用于动态止损

```java
double[] atr = Indicators.computeAtr(high, low, close, 14);
```

**计算逻辑**：
1. 真实波幅 TR = max(High-Low, |High-PrevClose|, |Low-PrevClose|)
2. ATR = Wilder's 平滑法：`ATR[i] = (ATR[i-1] × (period-1) + TR[i]) / period`

---

## 2. ADX / +DI / -DI — 趋势强度

**用途**：判断趋势强弱，区分趋势/震荡市

```java
double[][] result = Indicators.computeAdx(high, low, close, 14);
// result[0] = ADX, result[1] = +DI, result[2] = -DI
```

**计算逻辑**：
1. +DM = High[i] - High[i-1]（为正时取值，否则 0）
2. -DM = Low[i-1] - Low[i]（为正时取值，否则 0）
3. +DI = 100 × Smoothed(+DM) / ATR
4. -DI = 100 × Smoothed(-DM) / ATR
5. DX = 100 × |+DI - -DI| / (+DI + -DI)
6. ADX = Wilder's 平滑(DX)

**解读**：ADX > 25 = 强趋势，ADX < 15 = 震荡

---

## 3. RSI — 相对强弱指数

**用途**：超买超卖判断，均值回归入场

```java
double[] rsi = Indicators.computeRsi(close, 14);
```

**计算逻辑**：
1. 计算每日涨跌幅
2. 分别累积平均收益（avgGain）和平均损失（avgLoss）
3. RS = avgGain / avgLoss
4. RSI = 100 - 100/(1+RS)
5. 使用 Wilder's 平滑法更新 avgGain/avgLoss

**解读**：RSI < 30 = 超卖，RSI > 70 = 超买

---

## 4. EMA — 指数移动平均

**用途**：趋势方向判断，动态支撑/阻力

```java
double[] ema = Indicators.computeEma(close, 20);
```

**计算逻辑**：
```
α = 2 / (period + 1)
EMA[0] = close[0]
EMA[i] = α × close[i] + (1-α) × EMA[i-1]
```

---

## 5. SMA — 简单移动平均

**用途**：趋势方向判断，布林带中轨

```java
double[] sma = Indicators.computeSma(close, 20);
```

**计算逻辑**：前 period 个值的算术平均

---

## 6. MACD — 平滑异同移动平均

**用途**：趋势动量确认，金叉/死叉信号

```java
double[][] macd = Indicators.computeMacd(close, 12, 26, 9);
// macd[0] = MACD线, macd[1] = Signal线, macd[2] = 柱状图(Histogram)
```

**计算逻辑**：
1. MACD线 = EMA(fast) - EMA(slow)
2. Signal线 = EMA(MACD线, signal)
3. 柱状图 = MACD线 - Signal线

---

## 7. MFI — 资金流量指标

**用途**：结合量价判断资金流入/流出

```java
double[] mfi = Indicators.computeMfi(high, low, close, volume, 14);
```

**计算逻辑**：
1. 典型价格 TP = (High + Low + Close) / 3
2. 资金流 MF = TP × Volume
3. 正资金流：TP 上升时的 MF 之和
4. 负资金流：TP 下降时的 MF 之和
5. MFI = 100 - 100/(1 + 正资金流/负资金流)

**解读**：类似 RSI 但加入成交量权重

---

## 8. Stochastic %K — 随机震荡

**用途**：超买超卖，短期反转信号

```java
double[] stochK = Indicators.computeStochastic(high, low, close, 14);
```

**计算逻辑**：
```
%K = (Close - LowestLow(N)) / (HighestHigh(N) - LowestLow(N)) × 100
```

---

## 9. OBV — 能量潮

**用途**：量价背离检测

```java
double[] obv = Indicators.computeObv(close, volume);
```

**计算逻辑**：
- 价格上涨：OBV += Volume
- 价格下跌：OBV -= Volume
- 价格不变：OBV 不变

---

## 10. VWAP — 成交量加权平均价

**用途**：机构交易基准，支撑/阻力

```java
double[] vwap = Indicators.computeVwap(high, low, close, volume, 20);
```

**计算逻辑**（滚动窗口）：
```
VWAP = Σ(TP × Volume) / Σ(Volume)    // 在 period 窗口内
TP = (High + Low + Close) / 3
```

---

## 11. Bollinger Bands — 布林带

**用途**：波动区间、均值回归入场

```java
double[][] bb = Indicators.computeBollingerBands(close, 20, 2.0);
// bb[0] = 上轨, bb[1] = 中轨(SMA), bb[2] = 下轨
```

**计算逻辑**：
```
中轨 = SMA(close, period)
上轨 = 中轨 + stdDevMultiplier × StdDev(close, period)
下轨 = 中轨 - stdDevMultiplier × StdDev(close, period)
```

---

## 边界处理

所有指标在前期数据不足 `period` 时的处理方式：
- 填充第一个有效值或 0
- 不会产生 NaN（已内部处理）
- 返回数组长度始终等于输入数组长度
