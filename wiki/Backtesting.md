# 回测引擎

## 概述

回测引擎（`BacktestEngine`）是框架的核心组件，负责协调数据加载、策略执行和结果评估的完整流程。

## 核心组件

### BacktestEngine

主入口类，提供：
- **回测执行**：`run(strategy, data, enableShort)` 完整回测流程
- **策略工厂**：`createStrategy(name)` 根据名称创建策略实例
- **CLI 入口**：`main(args)` 命令行运行回测

### StrategyEvaluator

交易模拟与性能评估引擎：

```java
// 核心方法
EvaluationResult evaluate(int[] signals, double[] prices)
```

**默认参数**：
- 初始资金：10,000 USDT
- 佣金费率：0.02%（单边）
- 滑点：0.02%

## 回测流程

```
1. 加载市场数据 (MarketData)
2. 分析市场机制 (MarketRegimeDetector.analyze)
3. 生成交易信号 (strategy.generateSignals)
4. 综合评估 (evaluator.evaluate)
   ├── 逐K线遍历信号，模拟交易执行（含手续费+滑点）
   ├── 计算每步权益值
   ├── 回测结束自动平仓
   └── 计算性能指标 + 综合评分
5. 封装结果 (BacktestResult)
```

## 交易模拟细节

### 执行价格

| 操作 | 执行价 |
|------|--------|
| 开多 | `price × (1 + slippage)` |
| 开空 | `price × (1 - slippage)` |
| 平多 | `price × (1 - slippage)` |
| 平空 | `price × (1 + slippage)` |

每次执行还需扣除 `佣金 = 名义金额 × commission`

### 异常处理

- 权益值为非有限值（NaN/Infinity）→ 终止模拟
- 权益超出合理范围（< 0 或 > 100× 初始资金）→ 终止模拟

## 性能指标（PerformanceMetrics）

| 字段 | 说明 | 计算方式 |
|------|------|----------|
| `totalReturn` | 总收益率 | `(最终权益 / 初始权益) - 1` |
| `annualizedReturn` | 年化收益率 | 基于 5min K 线换算（288根/天 × 365天） |
| `annualizedVol` | 年化波动率 | 收益率标准差年化 |
| `sharpeRatio` | 夏普比率 | `annualizedReturn / annualizedVol` |
| `maxDrawdown` | 最大回撤 | 峰值到谷值的最大跌幅比例 |
| `winRate` | 胜率 | `盈利交易数 / 总交易数` |

## 综合评分公式

评分采用多维度加权，范围 [0, 1]：

```
score = sharpe_clamped × 0.25        # 夏普比率 (0~5)
      + return_clamped × 0.15        # 收益率 (0~2)
      + winrate_clamped × 0.10       # 胜率 (0~1)
      + dd_penalty × 0.15            # 回撤惩罚
      + trade_activity × 0.25        # 交易活跃度
      + min_trade_penalty × 0.10     # 最小交易惩罚
```

### 硬性过滤条件

不满足以下条件的策略直接得 0 分：
- 权益曲线必须全部为有限值
- 最大回撤 ≤ 30%
- 最终权益 ≥ 初始资金的 70%
- 总收益 ≥ -0.5%

## Walk-Forward 验证

`DataManager` 提供 Walk-Forward 分割功能：

```java
List<WalkForwardWindow> windows = DataManager.walkForwardSplit(data, 3);
// 每个窗口: 70% 训练集 + 30% 验证集
// 窗口间不重叠，滑动前进
```

## 数据验证

`DataManager.validate()` 执行 6 层检查：

1. **空数据检查**：data 非 null 且长度 > 0
2. **数组长度一致性**：OHLCV 五组数组长度相同
3. **价格合理性**：正数、High ≥ Low
4. **成交量检查**：零成交量不超过 10%（警告）
5. **时间戳递增**：无乱序（错误）
6. **极端跳价**：单 K 线涨跌 ≤ 20%（警告）

## 命令行参数

```
--symbol <string>      交易对，默认 ETHUSDT
--interval <string>    K线周期，默认 5m
--strategy <string>    策略名称，默认 hybridmm
--no-short             禁用做空
```
