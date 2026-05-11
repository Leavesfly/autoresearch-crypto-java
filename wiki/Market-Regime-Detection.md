# 市场机制检测

## 概述

框架提供两种互补的市场机制检测器：

- **MarketRegimeDetector**：基于本地 K 线技术指标的离线检测
- **MultiSourceRegimeDetector**：聚合多个网络数据源的实时综合牛熊评分

---

## MarketRegimeDetector（本地检测）

### 用途

在回测和实盘中分析当前市场状态，帮助策略选择和信号过滤。仅使用本地 K 线数据，不依赖网络。

### 调用方式

```java
RegimeInfo regime = MarketRegimeDetector.analyze(data);
```

### 检测指标

| 指标 | 计算方式 | 用途 |
|------|----------|------|
| EMA50 vs EMA200 | 指数移动平均 | 趋势方向 |
| ADX(14) | 平均趋向指数 | 趋势强度 |
| 7日年化波动率 | 收益率标准差 × √(288×365) | 波动程度 |

### 机制分类规则

```
EMA50 > EMA200 → 上升趋势
EMA50 < EMA200 → 下降趋势
否则 → 中性

ADX > 25 → 强趋势
ADX 15~25 → 弱趋势
ADX < 15 → 震荡市
```

### 最终分类

| 机制 | 条件 |
|------|------|
| `STRONG_UPTREND` | 上升趋势 + ADX > 25 |
| `STRONG_DOWNTREND` | 下降趋势 + ADX > 25 |
| `WEAK_UPTREND` | 上升趋势 + ADX 15~25 |
| `WEAK_DOWNTREND` | 下降趋势 + ADX 15~25 |
| `RANGING` | ADX < 15 |

### RegimeInfo 返回值

```java
record RegimeInfo(
    MarketRegime regime,          // 机制类型枚举（可通过 regime.label() 获取中文名）
    double adx,                   // ADX 值
    String emaRelation,           // EMA 关系描述
    double priceVsEma200Pct,      // 价格相对 EMA200 偏离百分比
    double volatilityAnnualized   // 7日年化波动率
)
```

`MarketRegime` 枚举值及中文标签：

| 枚举值 | label() |
|--------|---------|
| `STRONG_UPTREND` | 强势上涨 |
| `WEAK_UPTREND` | 弱势上涨 |
| `RANGING` | 震荡 |
| `WEAK_DOWNTREND` | 弱势下跌 |
| `STRONG_DOWNTREND` | 强势下跌 |

---

## MultiSourceRegimeDetector（多源实时检测）

### 用途

实盘交易中聚合多个免费网络数据源信号，计算综合牛熊评分（-1 到 +1），用于方向过滤，避免逆势交易。

### 调用方式

```java
MultiSourceRegimeDetector detector = new MultiSourceRegimeDetector();
RegimeReport report = detector.analyze();       // 使用缓存
RegimeReport report = detector.analyze(true);   // 强制刷新

boolean[] filter = detector.directionFilter();
// filter[0] = allowLong, filter[1] = allowShort
```

### 三大数据源

| 数据源 | 权重 | API | 解读方式 |
|--------|------|-----|----------|
| Fear & Greed 指数 | 35% | alternative.me | 逆向：极度恐惧=看多，极度贪婪=看空 |
| 宏观数据（DXY + Nasdaq） | 35% | Yahoo Finance | DXY 走弱/Nasdaq 上涨=看多 |
| BTC Dominance | 30% | CoinGecko | >55% = risk-off 看空，<45% = 山寨季看多 |

### Fear & Greed 评分映射

| 指数值 | 市场情绪 | 信号评分 |
|--------|----------|----------|
| 0~25 | 极度恐惧 | +0.5 ~ +1.0（看多） |
| 25~45 | 恐惧 | +0.1 ~ +0.5（偏多） |
| 45~55 | 中性 | 0（中性） |
| 55~75 | 贪婪 | -0.1 ~ -0.5（偏空） |
| 75~100 | 极度贪婪 | -0.5 ~ -1.0（看空） |

### 宏观数据评分

- **DXY（美元指数）**：30 日变化为负 → 看多加密货币
- **Nasdaq**：30 日变化为正 → 风险偏好上升，看多

### BTC Dominance 评分

| BTC 占比 | 含义 | 信号 |
|----------|------|------|
| > 55% | Risk-off，资金回流 BTC | 看空山寨币 |
| 45% ~ 55% | 中性 | 无方向性 |
| < 45% | 山寨季，资金流向小币 | 看多山寨币 |

### 综合评分计算

```
composite = fearGreedScore × 0.35
          + macroScore × 0.35
          + btcDominanceScore × 0.30
```

### 机制判定

| 综合评分 | 判定 | 方向过滤 |
|----------|------|----------|
| > +0.15 | BULLISH | 仅允许多头 |
| < -0.15 | BEARISH | 仅允许空头 |
| -0.15 ~ +0.15 | NEUTRAL | 双向交易 |

### 缓存机制

- 默认缓存有效期：3600 秒（1 小时）
- 避免频繁请求外部 API
- 可通过 `analyze(true)` 强制刷新

### RegimeReport 内容

报告包含：
- 各数据源的原始值和转换后的评分
- 综合评分和置信度
- 最终机制判定
- 方向过滤建议
- 各指标的一致性分析

---

## 两种检测器的选择

| 场景 | 推荐检测器 | 原因 |
|------|-----------|------|
| 回测 | MarketRegimeDetector | 仅使用本地数据，无网络依赖 |
| 实盘方向过滤 | MultiSourceRegimeDetector | 综合宏观/情绪/链上数据 |
| 策略选择 | 两者结合 | 本地判断趋势强度 + 网络判断大方向 |
