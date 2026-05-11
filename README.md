# 🚀 AutoResearch Crypto Java

> **自主加密货币量化交易策略研究框架** — 高性能 Java 实现

[![Java](https://img.shields.io/badge/Java-17+-orange.svg)](https://openjdk.org/)
[![Maven](https://img.shields.io/badge/Maven-3.8+-blue.svg)](https://maven.apache.org/)
[![License](https://img.shields.io/badge/License-Research-green.svg)](#免责声明)

AutoResearch Crypto 是一个功能完备的加密货币量化交易研究框架，集成了 **策略开发、回测验证、参数优化、策略进化和实盘交易** 的完整工作流。基于 Java 17 构建，专注于高性能和可扩展性。

---

## ✨ 核心特性

| 模块 | 能力 | 说明 |
|------|------|------|
| 📈 **10 种内置策略** | 开箱即用 | 趋势跟踪、均值回归、剥头皮、网格、多时间框架集成等 |
| 🔬 **高保真回测** | 模拟真实市场 | 含手续费、滑点、异常保护的精确交易模拟 |
| 🏪 **实盘交易** | 双交易所支持 | Nado DEX + OKX 交易所连接器 |
| 🧬 **ATLAS 进化引擎** | 自动优化 | 多 Agent 竞争性进化（变异/交叉/权重调整） |
| 🧠 **GEPA 反思引擎** | 科学方法驱动 | 假设→实验→反思循环改进策略 |
| 🌊 **市场机制检测** | 智能适应 | 自动识别趋势/震荡/高波动状态 |
| ⏱️ **多时间框架分析** | 信号增强 | 跨 15m/1h/4h/1d 集成交易信号 |
| 📊 **风险评分系统** | 多维评估 | 风险调整后的综合策略评分 |
| 🔍 **策略搜索引擎** | 参数寻优 | 随机搜索 + 时间预算控制 |

---

## 🏗️ 系统架构

```
┌─────────────────────────────────────────────────────────────────┐
│                         用户入口层                                │
│     BacktestEngine.main()    │    LiveTradingEngine.start()      │
└──────────────┬───────────────┴───────────────┬──────────────────┘
               │                               │
┌──────────────▼──────────────┐  ┌─────────────▼─────────────────┐
│        回测引擎              │  │        实盘交易引擎             │
│  BacktestEngine             │  │  LiveTradingEngine             │
│  StrategyEvaluator          │  │  ExchangeConnector (interface) │
│  BacktestResult             │  │  ├── NadoConnector             │
└──────────────┬──────────────┘  │  └── OkxConnector              │
               │                 └───────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│                          策略层                                   │
│  BaseStrategy (abstract)                                         │
│  ├── TrendStrategy         ├── GridStrategy                      │
│  ├── ScalpStrategy         ├── HybridStrategy                    │
│  ├── AdaptiveStrategy      ├── PureActionStrategy                │
│  ├── TrendFollowStrategy   ├── PureActionV2Strategy              │
│  ├── HybridMeanRevMomentumStrategy                               │
│  └── MultiTfEnsembleStrategy                                     │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│                          分析层                                   │
│  Indicators (技术指标)      │  MultiTimeframeAnalyzer            │
│  MarketRegimeDetector       │  MultiSourceRegimeDetector         │
│  RiskAdjustedScoring        │  StrategySearchEngine              │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│                          数据层                                   │
│  MarketData (record)        │  DataManager                       │
│  DataDownloader             │  MarketDataLoader                  │
└──────────────┬──────────────────────────────────────────────────┘
               │
┌──────────────▼──────────────────────────────────────────────────┐
│                          进化层                                   │
│  AtlasEvolutionEngine       │  GepaReflectionEngine              │
│  Agent                      │  ExperimentLog                     │
└─────────────────────────────────────────────────────────────────┘
```

---

## 📦 项目结构

```
autoresearch-crypto-java/
├── pom.xml                          # Maven 构建配置
├── wiki/                            # 详细文档
└── src/
    ├── main/java/io/leavesfly/autoresearch/crypto/
    │   ├── backtest/                # 回测引擎
    │   ├── config/                  # 全局配置（TradingConfig）
    │   ├── data/                    # 数据管理（下载/加载/分割）
    │   ├── demo/                    # 演示入口
    │   ├── evolution/               # 进化引擎（ATLAS + GEPA）
    │   ├── indicator/               # 11种技术指标
    │   ├── live/                    # 实盘交易引擎
    │   ├── multitf/                 # 多时间框架分析
    │   ├── regime/                  # 市场机制检测
    │   ├── runner/                  # 运行器
    │   ├── scoring/                 # 风险评分系统
    │   ├── search/                  # 策略参数搜索
    │   └── strategy/                # 策略框架
    │       └── impl/                # 10种策略实现
    ├── main/resources/              # 配置资源
    └── test/                        # 单元测试
```

---

## 🚀 快速开始

### 环境要求

- **JDK 17+**（推荐 OpenJDK 17 或 GraalVM 17）
- **Maven 3.8+**
- 网络连接（用于下载市场数据）

### 构建项目

```bash
# 克隆项目
git clone <repo-url>
cd autoresearch-crypto-java

# 编译
mvn clean compile

# 打包（跳过测试）
mvn package -DskipTests

# 运行测试
mvn test
```

### 运行回测

```bash
# 使用默认配置（ETHUSDT, 5m, hybridmm 策略）
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar

# 自定义参数
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    --symbol BTCUSDT \
    --interval 5m \
    --strategy trend \
    --no-short
```

### 输出示例

```
[INFO] === Backtest: HybridMeanRevMomentum ===
[INFO] Data length: 17280, Enable short: true
[INFO] Regime: STRONG_UPTREND (ADX=32.5)
[INFO] --- Results ---
[INFO]   Total Return:    12.34%
[INFO]   Annual Return:   45.67%
[INFO]   Sharpe Ratio:    2.15
[INFO]   Max Drawdown:    -8.42%
[INFO]   Win Rate:        62.5%
[INFO]   Trade Count:     87
[INFO]   Composite Score: 0.724
```

---

## 📈 内置交易策略

| # | 策略名称 | CLI 名称 | 类型 | 适用市场 | 复杂度 |
|---|----------|----------|------|----------|--------|
| 1 | TrendStrategy | `trend` | 趋势确认 | 强趋势市 | ★★☆☆ |
| 2 | TrendFollowStrategy | `trendfollow` | 趋势跟踪 | 中强趋势 | ★★☆☆ |
| 3 | ScalpStrategy | `scalp` | 剥头皮 | 震荡市 | ★☆☆☆ |
| 4 | GridStrategy | `grid` | 网格交易 | 低波动震荡 | ★★☆☆ |
| 5 | HybridStrategy | `hybrid` | 市场自适应 | 全市场 | ★★★☆ |
| 6 | AdaptiveStrategy | `adaptive` | RSI 自适应 | 全市场 | ★★★☆ |
| 7 | PureActionStrategy | `pureaction` | 纯价格行为 | 全市场 | ★★☆☆ |
| 8 | PureActionV2Strategy | `pureactionv2` | 价格行为增强 | 全市场 | ★★★☆ |
| 9 | HybridMeanRevMomentumStrategy | `hybridmm` | 均值回归+动量 | 震荡偏趋势 | ★★★☆ |
| 10 | MultiTfEnsembleStrategy | `multitf` | 多时间框架集成 | 全市场 | ★★★★ |

### 信号编码

| 信号值 | 含义 |
|--------|------|
| `0` | 平仓（FLAT） |
| `1` | 持仓（HOLD） |
| `2` | 做多（LONG） |
| `3` | 做空（SHORT） |

---

## 🔬 技术指标库

框架内置 11 种技术分析指标的纯 Java 高性能实现：

| 指标 | 方法 | 类型 | 用途 |
|------|------|------|------|
| ATR | `computeAtr` | 波动率 | 动态止损 |
| ADX / ±DI | `computeAdx` | 趋势强度 | 趋势/震荡判定 |
| RSI | `computeRsi` | 动量 | 超买超卖 |
| EMA | `computeEma` | 趋势 | 趋势方向 |
| SMA | `computeSma` | 趋势 | 均线支撑/阻力 |
| MACD | `computeMacd` | 动量 | 趋势动能 |
| MFI | `computeMfi` | 量价 | 资金流向 |
| Stochastic %K | `computeStochastic` | 动量 | 超买超卖 |
| OBV | `computeObv` | 量价 | 量价背离 |
| VWAP | `computeVwap` | 量价 | 成交量加权价 |
| Bollinger Bands | `computeBollingerBands` | 波动率 | 通道突破 |

---

## 🌊 市场机制检测

框架提供两种互补的市场机制检测方式：

### 本地检测（MarketRegimeDetector）

基于 EMA50/EMA200 交叉和 ADX 强度判定：

| 机制 | 条件 | 建议策略 |
|------|------|----------|
| `STRONG_UPTREND` | EMA50 > EMA200 + ADX > 25 | 趋势跟踪 |
| `STRONG_DOWNTREND` | EMA50 < EMA200 + ADX > 25 | 趋势跟踪（做空） |
| `WEAK_UPTREND` | EMA50 > EMA200 + ADX 15~25 | 自适应 |
| `WEAK_DOWNTREND` | EMA50 < EMA200 + ADX 15~25 | 自适应 |
| `RANGING` | ADX < 15 | 均值回归/网格 |

### 多源检测（MultiSourceRegimeDetector）

聚合多个网络数据源的实时综合牛熊评分，适用于实盘交易。

---

## 🧬 进化引擎

### ATLAS — 多 Agent 竞争进化

维护多个交易 Agent，通过自然选择机制持续优化策略参数：

- **变异**：在参数边界内添加高斯噪声探索新解
- **交叉**：表现差的 Agent 学习表现好的 Agent 参数
- **权重调整**：Softmax 温度控制的动态权重分配
- **死亡检测**：长期停滞的 Agent 被替换

### GEPA — 科学方法驱动反思

通过 **假设 → 实验 → 分析 → 反思** 的科学方法循环改进策略：

1. 基于历史表现提出改进假设
2. 设计对照实验验证假设
3. 分析实验结果
4. 反思并形成新的改进方向

---

## 💹 实盘交易

### 支持的交易所

| 交易所 | 连接器 | 特性 |
|--------|--------|------|
| **Nado DEX** | `NadoConnector` | 永续合约，POST_ONLY 开仓降低费率 |
| **OKX** | `OkxConnector` | V5 API，支持全品种合约交易 |

### 风险管理

- **仓位控制**：95% 可用资金开仓
- **止损**：默认 3%
- **止盈**：默认 5%
- **状态持久化**：交易状态自动保存，断点续行

---

## ⚙️ 配置参考

### 核心交易参数

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `INITIAL_CAPITAL` | 10,000 USDT | 初始资金 |
| `COMMISSION` | 0.02% | 佣金费率（单边） |
| `SLIPPAGE` | 0.02% | 滑点 |
| `MIN_NOTIONAL` | 100 USDT | 最小下单金额 |

### 数据配置

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `DEFAULT_SYMBOLS` | BTCUSDT, ETHUSDT | 默认交易对 |
| `DEFAULT_INTERVAL` | 5m | 默认 K 线周期 |
| `DEFAULT_START_DAYS` | 60 | 默认回溯天数 |

### 评估阈值

| 参数 | 默认值 | 说明 |
|------|--------|------|
| `SEARCH_MIN_TRADES` | 50 | 搜索模式最少交易次数 |
| `EVAL_MAX_DRAWDOWN` | 30% | 最大允许回撤 |
| `EVAL_MIN_EQUITY_RATIO` | 70% | 最低权益比率 |
| `TIME_BUDGET` | 600 秒 | 搜索时间预算 |

---

## 💻 代码示例

### 运行单策略回测

```java
// 加载数据
MarketData data = MarketDataLoader.loadCsv(Path.of("data/crypto/ETHUSDT_5m_60d.csv"));

// 创建策略
BaseStrategy strategy = BacktestEngine.createStrategy("hybridmm");

// 运行回测
BacktestEngine engine = new BacktestEngine();
BacktestResult result = engine.run(strategy, data, true);

System.out.println("综合评分: " + result.compositeScore());
System.out.println("交易次数: " + result.tradeCount());
```

### 自定义策略参数

```java
TrendStrategy strategy = new TrendStrategy();
strategy.setParams(Map.of(
    "window", 25,
    "stdDev", 1.8,
    "atrMultiplier", 3.0,
    "maxHoldBars", 36
));
```

### 策略参数搜索

```java
StrategySearchEngine searchEngine = new StrategySearchEngine(600); // 600秒预算

Map<String, double[]> paramSpace = new HashMap<>();
paramSpace.put("window", new double[]{10, 50});
paramSpace.put("stdDev", new double[]{1.0, 3.0});
paramSpace.put("atrMultiplier", new double[]{1.5, 4.0});

SearchResult result = searchEngine.randomSearch(
    params -> new TrendStrategy(new HashMap<>(params)),
    paramSpace, data, true, 1000
);
```

### 下载市场数据

```java
DataDownloader downloader = new DataDownloader();
downloader.download("ETHUSDT", "5m", 60, false); // 60天，不强制覆盖
```

### 市场机制检测

```java
RegimeInfo regime = MarketRegimeDetector.analyze(data);
System.out.println("当前机制: " + regime.regime().label());
System.out.println("ADX: " + regime.adx());
```

---

## 🛠️ 技术栈

| 技术 | 版本 | 用途 |
|------|------|------|
| **Java** | 17+ | 核心语言 |
| **Maven** | 3.8+ | 构建管理 |
| **Jackson** | 2.15.3 | JSON 序列化/反序列化 |
| **OkHttp** | 4.12.0 | HTTP 客户端（交易所 API） |
| **Commons Math** | 3.6.1 | 统计计算 |
| **SLF4J + Logback** | 2.0.9 / 1.4.11 | 日志 |
| **JUnit 5** | 5.10.1 | 单元测试 |
| **AssertJ** | 3.24.2 | 测试断言 |

---

## 📖 Wiki 文档

项目提供了详尽的 Wiki 文档，覆盖所有模块的使用细节：

- [🏠 首页](wiki/Home.md) — 项目总览
- [🏗️ 架构概览](wiki/Architecture.md) — 系统分层设计
- [🚀 快速开始](wiki/Getting-Started.md) — 环境搭建与首次运行
- [📘 用户指南](wiki/User-Guide.md) — 完整使用手册
- [📈 交易策略](wiki/Strategies.md) — 10 种策略详解
- [🔬 回测引擎](wiki/Backtesting.md) — 回测机制与高级用法
- [💹 实盘交易](wiki/Live-Trading.md) — 交易所连接配置
- [🧬 进化引擎](wiki/Evolution-Engines.md) — ATLAS 与 GEPA 详解
- [📊 技术指标](wiki/Technical-Indicators.md) — 11 种指标说明
- [🌊 市场机制检测](wiki/Market-Regime-Detection.md) — 机制分类规则
- [⚙️ 配置参考](wiki/Configuration-Reference.md) — 全部配置项
- [📚 API 参考](wiki/API-Reference.md) — 类与方法文档

---

## 🗺️ 路线图

- [ ] 更多交易所连接器（Binance、Bybit）
- [ ] Web Dashboard 可视化回测结果
- [ ] 分布式策略搜索
- [ ] 机器学习特征工程
- [ ] 多币种组合策略

---

## ⚠️ 免责声明

> **本项目仅供研究和教育用途。**
>
> - 加密货币交易具有重大风险，可能导致本金全部损失
> - 过往表现不能保证未来收益
> - 回测结果不代表实盘表现
> - **使用本框架进行实盘交易的风险由用户自行承担**

---

## 📄 许可证

本项目仅供学术研究与个人学习使用。
