# 交易策略

## 策略框架

所有策略继承自 `BaseStrategy` 抽象基类，必须实现：

```java
public abstract int[] generateSignals(MarketData data, boolean enableShort);
public abstract String name();
```

**信号编码**：`0`=平仓, `1`=持仓, `2`=做多, `3`=做空

---

## 策略一览

| # | 策略 | 类型 | 适用市场 | 复杂度 |
|---|------|------|----------|--------|
| 1 | Trend | 趋势确认 | 强趋势 | ★★☆ |
| 2 | TrendFollow | 趋势跟踪 | 中强趋势 | ★★☆ |
| 3 | Scalp | 剥头皮 | 震荡市 | ★☆☆ |
| 4 | Grid | 网格交易 | 低波动震荡 | ★★☆ |
| 5 | Hybrid | 市场自适应 | 全市场 | ★★★ |
| 6 | Adaptive | RSI自适应 | 全市场 | ★★★ |
| 7 | PureAction | 纯价格行为 | 全市场 | ★★☆ |
| 8 | HybridMeanRevMomentum | 均值回归+动量 | 震荡偏趋势 | ★★★ |
| 9 | MultiTfEnsemble | 多时间框架集成 | 全市场 | ★★★★ |
| 10 | PureActionV2 | 价格行为增强 | 全市场 | ★★★ |

---

## 1. TrendStrategy — 趋势策略

**核心逻辑**：布林带入场 + ADX 趋势确认 + RSI 超买超卖过滤

- **做多**：价格触及布林带下轨 + ADX ≥ 25 + RSI ≤ 30
- **做空**：价格触及布林带上轨 + ADX ≥ 25 + RSI ≥ 70
- **出场**：ATR 追踪止损 / MA 反转 / 超时（48 根 K 线）

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window` | 20 | 布林带周期 |
| `stdDev` | 2.0 | 布林带标准差倍数 |
| `atrPeriod` | 14 | ATR 周期 |
| `atrMultiplier` | 2.5 | ATR 止损倍数 |
| `adxThreshold` | 25 | ADX 趋势确认阈值 |
| `rsiThreshold` | 30 | RSI 超卖阈值 |
| `maxHoldBars` | 48 | 最大持仓 K 线数 |

---

## 2. TrendFollowStrategy — 趋势跟踪策略

**核心逻辑**：长周期 EMA 确定方向，短周期 EMA 回调入场

- **做多**：价格 > 长周期 EMA + 回调至短周期 EMA 附近
- **做空**：价格 < 长周期 EMA + 反弹至短周期 EMA 附近
- **出场**：ATR 追踪止损 / MA 反转 / 超时

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `longMaPeriod` | 100 | 长周期 EMA |
| `pullMaPeriod` | 20 | 回调 EMA |
| `atrMultiplier` | 2.0 | ATR 止损倍数 |
| `entryZone` | 0.002 | 入场区域 ±0.2% |
| `maxHoldBars` | 24 | 最大持仓 K 线数 |

---

## 3. ScalpStrategy — 剥头皮策略

**核心逻辑**：紧布林带均值回归，快进快出

- **做多**：价格触及/跌破下布林带（可选 RSI 超卖过滤）
- **做空**：价格触及/突破上布林带（可选 RSI 超买过滤）
- **出场**：固定止盈 0.5% / 固定止损 0.3% / 回归中线 / 超时 6 根 K 线

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window` | 10 | 布林带周期（短） |
| `stdDev` | 1.2 | 布林带标准差（紧） |
| `takeProfitPct` | 0.005 | 止盈 0.5% |
| `stopLossPct` | 0.003 | 止损 0.3% |
| `maxHoldBars` | 6 | 超时出场 |
| `useRsiFilter` | false | RSI 过滤开关 |

---

## 4. GridStrategy — 网格交易策略

**核心逻辑**：在移动中价周围维持网格买卖价位

- 当价格穿越网格价位时调整仓位
- **强趋势保护**：价格偏离中价超过 2% 时平仓并禁用
- 适合震荡/低波动市场

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `gridSpacingPct` | 0.005 | 网格间距 0.5% |
| `gridLevels` | 5 | 网格层级数 |
| `atrSpacingMult` | 0.5 | ATR 动态间距系数 |
| `maxPosition` | 1.0 | 最大仓位 |
| `trendMaPeriod` | 100 | 趋势检测 MA 周期 |

---

## 5. HybridStrategy — 混合策略

**核心逻辑**：ADX 分类市场状态，自动切换交易模式

- **震荡模式**（ADX ≤ 25）：布林带均值回归
- **趋势模式**（ADX > 25）：MA 回调入场
- **出场**：ATR 追踪止损 / MA 反转 / 超时

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `adxThreshold` | 25 | 模式切换阈值 |
| `window` | 20 | 布林带周期 |
| `trendMaPeriod` | 100 | 趋势 MA 周期 |
| `atrMultiplier` | 2.0 | ATR 止损倍数 |
| `maxHoldBars` | 24 | 最大持仓 |

---

## 6. AdaptiveStrategy — 自适应策略

**核心逻辑**：ADX 切换模式 + RSI/EMA 独立信号

- **震荡模式**（ADX ≤ 25）：RSI 超卖做多 / 超买做空
- **趋势模式**（ADX > 25）：EMA 快慢线交叉
- **出场**：ATR 追踪止损 / EMA 反转 / 超时

**关键参数**：

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `rsiPeriod` | 14 | RSI 周期 |
| `rsiLow` | 30 | RSI 超卖 |
| `rsiHigh` | 70 | RSI 超买 |
| `maPeriod` | 20 | EMA 快线 |
| `trendLongMa` | 100 | EMA 慢线 |
| `adxThreshold` | 25 | ADX 切换阈值 |

---

## 7. PureActionStrategy — 纯价格行为策略

**核心逻辑**：无指标过滤的纯价格行为判断

- **做多**：价格接近布林带下轨 + 看涨动量（连续 2 根阳线）
- **做空**：价格接近布林带上轨 + 看跌动量（连续 2 根阴线）
- **出场**：ATR 追踪止损 / MA 反转 / 超时

**关键参数**：`window`=20, `stdDev`=2.0, `atrMultiplier`=2.0, `maxHoldBars`=36

---

## 8. HybridMeanRevMomentumStrategy — 混合均值回归动量

**核心逻辑**：RSI 均值回归信号 + EMA 动量过滤

- **做多**：RSI 从超卖区回升 + 价格 > EMA 快线
- **做空**：RSI 从超买区回落 + 价格 < EMA 快线
- **出场**：ATR 追踪止损 + 固定止盈 3% + 固定止损 2% + MA 反转

**关键参数**：`rsiLow`=25, `rsiHigh`=75, `takeProfitPct`=0.03, `stopLossPct`=0.02

---

## 9. MultiTfEnsembleStrategy — 多时间框架集成

**核心逻辑**：多时间框架加权投票

- **1d**（权重 0.35）：方向过滤
- **4h**（权重 0.25）：仓位大小
- **1h**（权重 0.25）：入场时机
- **15m**（权重 0.15）：出场/反转

各 TF 基于 EMA 快慢线 + RSI 生成独立信号 → 加权投票超过阈值则交易

**关键参数**：`voteThreshold`=0.5, `maFast`=10, `maSlow`=30, `atrMultiplier`=2.5

---

## 通用出场机制

所有策略共享以下出场逻辑：

1. **ATR 追踪止损**：动态跟随价格，回撤超过 N×ATR 则平仓
2. **MA 反转**：价格跌破/突破关键均线
3. **超时退出**：持仓超过 `maxHoldBars` 根 K 线强制平仓
4. **反向信号**：出现反向交易信号时平仓（部分策略）
