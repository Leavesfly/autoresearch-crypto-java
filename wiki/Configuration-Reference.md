# 配置参考

## 概述

所有配置集中定义在 `TradingConfig` 类（`com.autoresearch.crypto.config`）中，采用静态常量方式管理，避免魔法数字散落在代码中。

---

## 路径配置

| 常量 | 值 | 说明 |
|------|-----|------|
| `PROJECT_DIR` | 项目根目录 | 自动检测 |
| `DATA_DIR` | `data/crypto` | 市场数据存储目录 |
| `CHECKPOINT_DIR` | `checkpoints` | 策略检查点目录 |
| `LOG_DIR` | `logs` | 日志输出目录 |
| `SEARCH_RESULTS_DIR` | `search_results` | 搜索结果保存目录 |

---

## 数据相关

| 常量 | 值 | 说明 |
|------|-----|------|
| `DEFAULT_SYMBOLS` | `[BTCUSDT, ETHUSDT]` | 默认交易对列表 |
| `DEFAULT_INTERVAL` | `5m` | 默认 K 线周期 |
| `DEFAULT_START_DAYS` | `60` | 默认回溯天数 |

---

## 交易参数

| 常量 | 值 | 说明 |
|------|-----|------|
| `INITIAL_CAPITAL` | `10000.0` | 初始资金（USDT） |
| `COMMISSION` | `0.0002` | 佣金费率（0.02%，单边） |
| `SLIPPAGE` | `0.0002` | 滑点（0.02%） |
| `MIN_NOTIONAL` | `100.0` | 最小下单金额（USDT） |

---

## 时间常量

基于 5 分钟 K 线计算：

| 常量 | 值 | 说明 |
|------|-----|------|
| `BARS_PER_DAY_5M` | `288` | 每日 K 线数（24×60/5） |
| `TRADING_DAYS_PER_YEAR` | `365` | 年交易日数（加密货币 24/7） |
| `BARS_PER_YEAR` | `105120` | 年 K 线总数（288×365） |

### K 线周期秒数映射

| 周期 | 秒数 |
|------|------|
| `1m` | 60 |
| `5m` | 300 |
| `15m` | 900 |
| `1h` | 3600 |
| `4h` | 14400 |
| `1d` | 86400 |

---

## 评估阈值

| 常量 | 值 | 说明 |
|------|-----|------|
| `SEARCH_MIN_TRADES` | `50` | 搜索模式最少交易次数 |
| `EVAL_MIN_TRADES` | `10` | 评估模式最少交易次数 |
| `EVAL_MAX_DRAWDOWN` | `0.30` | 最大允许回撤（30%） |
| `EVAL_MIN_EQUITY_RATIO` | `0.70` | 最低权益比率（70%） |
| `EVAL_MIN_RETURN` | `-0.005` | 最低允许收益率（-0.5%） |

---

## 训练/搜索

| 常量 | 值 | 说明 |
|------|-----|------|
| `TIME_BUDGET` | `600` | 搜索时间预算（秒） |
| `WF_N_WINDOWS` | `3` | Walk-Forward 窗口数 |

---

## 策略默认参数

### Trend 策略

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window` | 20 | 布林带周期 |
| `stdDev` | 2.0 | 布林带标准差倍数 |
| `atrPeriod` | 14 | ATR 计算周期 |
| `atrMultiplier` | 2.5 | ATR 止损倍数 |
| `maxHoldBars` | 48 | 最大持仓 K 线数 |
| `adxThreshold` | 25 | ADX 趋势确认阈值 |
| `rsiThreshold` | 30 | RSI 超卖/超买阈值 |
| `entryZone` | 1.0 | 入场区域系数 |
| `useAdx` | true | 是否启用 ADX 过滤 |

### Scalp 策略

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window` | 10 | 布林带周期（短） |
| `stdDev` | 1.2 | 布林带标准差（紧） |
| `takeProfitPct` | 0.005 | 止盈 0.5% |
| `stopLossPct` | 0.003 | 止损 0.3% |
| `maxHoldBars` | 6 | 超时出场 |
| `rsiPeriod` | 14 | RSI 周期 |
| `useRsiFilter` | false | RSI 过滤开关 |
| `rsiOversold` | 30 | RSI 超卖阈值 |
| `rsiOverbought` | 70 | RSI 超买阈值 |

### HybridMeanRevMomentum 策略

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `rsiPeriod` | 5 | RSI 周期 |
| `rsiLow` | 28 | RSI 超卖阈值 |
| `rsiHigh` | 72 | RSI 超买阈值 |
| `maPeriod` | 20 | EMA 快线周期 |
| `atrPeriod` | 14 | ATR 周期 |
| `atrMultiplier` | 2.0 | ATR 止损倍数 |
| `maxHoldBars` | 24 | 最大持仓 K 线数 |
| `takeProfitPct` | 0.03 | 止盈 3% |
| `stopLossPct` | 0.02 | 止损 2% |

### PureAction 策略

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `window` | 20 | 布林带周期 |
| `stdDev` | 2.0 | 布林带标准差 |
| `atrPeriod` | 14 | ATR 周期 |
| `atrMultiplier` | 2.0 | ATR 止损倍数 |
| `maxHoldBars` | 36 | 最大持仓 |
| `enableShort` | true | 允许做空 |
| `entryZone` | 0.0 | 入场区域 |

---

## 风险评分权重

`RiskAdjustedScoring` 的默认权重配置：

| 组件 | 权重 | 说明 |
|------|------|------|
| `sharpeWeight` | 0.30 | 夏普比率权重 |
| `ddWeight` | 0.30 | 回撤惩罚权重 |
| `tradeWeight` | 0.25 | 交易次数权重 |
| `excessWeight` | 0.20 | 超额收益权重 |
| `wrWeight` | 0.15 | 胜率权重 |

---

## 日志配置

日志配置文件位于 `src/main/resources/logback.xml`，使用 Logback 框架。默认输出到控制台，格式为：

```
%d{HH:mm:ss.SSS} [%thread] %-5level %logger{36} - %msg%n
```
