# 架构概览

## 系统架构图

```
┌─────────────────────────────────────────────────────────────┐
│                      用户入口层                               │
│  BacktestEngine.main()  │  LiveTradingEngine.start()         │
└────────────┬────────────┴──────────────┬────────────────────┘
             │                           │
┌────────────▼────────────┐  ┌───────────▼───────────────────┐
│      回测引擎            │  │       实盘交易引擎              │
│  BacktestEngine         │  │  LiveTradingEngine             │
│  StrategyEvaluator      │  │  ExchangeConnector (interface) │
│  BacktestResult         │  │  ├── NadoConnector             │
└────────────┬────────────┘  │  └── OkxConnector              │
             │               │  TradingState                   │
             │               └───────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────┐
│                        策略层                                  │
│  BaseStrategy (abstract)                                      │
│  ├── TrendStrategy        ├── GridStrategy                    │
│  ├── ScalpStrategy        ├── HybridStrategy                  │
│  ├── AdaptiveStrategy     ├── PureActionStrategy              │
│  ├── TrendFollowStrategy  ├── PureActionV2Strategy            │
│  ├── HybridMeanRevMomentumStrategy                            │
│  └── MultiTfEnsembleStrategy                                  │
└────────────┬─────────────────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────┐
│                      分析层                                    │
│  Indicators (技术指标)  │  MultiTimeframeAnalyzer             │
│  MarketRegimeDetector   │  MultiSourceRegimeDetector          │
│  RiskAdjustedScoring    │  StrategySearchEngine               │
└────────────┬─────────────────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────┐
│                      数据层                                    │
│  MarketData (record)    │  DataManager                        │
│  DataDownloader         │  MarketDataLoader                   │
└──────────────────────────────────────────────────────────────┘
             │
┌────────────▼─────────────────────────────────────────────────┐
│                      进化层                                    │
│  AtlasEvolutionEngine   │  GepaReflectionEngine               │
│  Agent                  │  ExperimentLog                      │
└──────────────────────────────────────────────────────────────┘
```

## 模块说明

### 数据层 (`com.autoresearch.crypto.data`)

| 类 | 职责 |
|----|------|
| `MarketData` | 不可变 OHLCV 数据容器（Java record） |
| `DataManager` | Walk-Forward 分割、数据验证、文件管理 |
| `DataDownloader` | 从交易所 API 下载历史 K 线 |
| `MarketDataLoader` | CSV 文件解析与数据切片 |

### 策略层 (`com.autoresearch.crypto.strategy`)

| 类 | 职责 |
|----|------|
| `BaseStrategy` | 策略抽象基类，定义统一接口 |
| `Signal` | 交易信号枚举（FLAT/HOLD/LONG/SHORT） |
| `StrategyEvaluator` | 交易模拟 + 性能评估 + 综合评分 |
| `impl/*` | 10 种具体策略实现 |

### 回测引擎 (`com.autoresearch.crypto.backtest`)

| 类 | 职责 |
|----|------|
| `BacktestEngine` | 回测主流程协调器 + 策略工厂 + CLI 入口 |
| `BacktestResult` | 回测结果封装（record） |

### 实盘交易 (`com.autoresearch.crypto.live`)

| 类 | 职责 |
|----|------|
| `LiveTradingEngine` | 实盘交易主循环控制器 |
| `ExchangeConnector` | 交易所连接器接口 |
| `NadoConnector` | Nado DEX 永续合约实现 |
| `OkxConnector` | OKX V5 API 实现 |
| `TradingState` | 交易状态持久化 |

### 进化引擎 (`com.autoresearch.crypto.evolution`)

| 类 | 职责 |
|----|------|
| `AtlasEvolutionEngine` | 多 Agent 竞争进化（变异/交叉/权重调整） |
| `GepaReflectionEngine` | 科学方法驱动的反思进化 |
| `Agent` | 进化代理（参数集 + 评分历史） |
| `ExperimentLog` | 实验日志记录 |

### 分析层

| 包 | 类 | 职责 |
|----|-----|------|
| `indicator` | `Indicators` | 11 种技术指标计算 |
| `regime` | `MarketRegimeDetector` | 本地 K 线市场机制检测 |
| `regime` | `MultiSourceRegimeDetector` | 多源网络数据综合牛熊评分 |
| `scoring` | `RiskAdjustedScoring` | 风险调整综合评分 |
| `multitf` | `MultiTimeframeAnalyzer` | 多时间框架趋势分析 |
| `search` | `StrategySearchEngine` | 参数随机搜索 |

## 核心数据流

```
CSV/API → MarketData → BaseStrategy.generateSignals() → int[]
    → StrategyEvaluator.evaluate() → EvaluationResult(score, metrics, trades, equityCurve)
    → BacktestResult
```

## 设计模式

- **模板方法**: `BaseStrategy` 定义骨架，子类实现 `generateSignals()` 和 `name()`
- **策略模式**: 统一接口 + 多种实现，运行时可切换
- **工厂方法**: `BacktestEngine.createStrategy()` 根据名称动态创建策略
- **接口隔离**: `ExchangeConnector` 接口解耦交易所实现
- **不可变对象**: `MarketData`、`BacktestResult` 等使用 Java record
- **观察者**: 进化引擎中 Agent 的评分/排名/调整循环

## 信号编码规范

| 信号值 | 枚举 | 含义 |
|--------|------|------|
| 0 | `Signal.FLAT` | 平仓/空仓 |
| 1 | `Signal.HOLD` | 持仓/保持 |
| 2 | `Signal.LONG` | 做多入场 |
| 3 | `Signal.SHORT` | 做空入场 |
