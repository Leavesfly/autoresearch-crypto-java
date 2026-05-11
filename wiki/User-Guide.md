# 用户使用指南

## 概述

本文档面向 `autoresearch-crypto-java` 的使用者，按照实际操作流程，从环境准备到实盘上线，逐步引导你完成完整的量化交易策略研究工作流。

---

## 一、环境准备

### 1.1 前置要求

- **JDK 17+**（推荐 OpenJDK 17 或 GraalVM 17）
- **Maven 3.8+**
- 稳定的网络连接（数据下载需要访问 Binance API）

### 1.2 构建项目

```bash
cd autoresearch-crypto-java

# 编译并打包
mvn clean package -DskipTests

# 确认构建成功
ls target/autoresearch-crypto-1.0.0-SNAPSHOT.jar
```

### 1.3 目录结构

构建完成后，你需要关注以下目录：

```
autoresearch-crypto-java/
├── data/crypto/         # 市场数据（CSV 文件）
├── checkpoints/         # 策略搜索结果 / 最优参数
├── logs/                # 运行日志 + 实盘状态文件
├── search_results/      # 搜索结果输出
└── target/              # 构建产物
```

> 这些目录会在首次运行时自动创建，无需手动建立。

---

## 二、下载市场数据

### 2.1 命令行下载

数据下载器从 Binance 公开 API 获取 K 线数据并保存为 CSV 格式。

```bash
# 下载 ETH 5分钟数据（60天）
java -cp target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    com.autoresearch.crypto.data.DataDownloader \
    --symbol ETHUSDT --interval 5m --days 60

# 下载 BTC 1小时数据（30天）
java -cp target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    com.autoresearch.crypto.data.DataDownloader \
    --symbol BTCUSDT --interval 1h --days 30

# 强制重新下载（覆盖已有文件）
java -cp target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    com.autoresearch.crypto.data.DataDownloader \
    --symbol ETHUSDT --interval 5m --days 60 --force
```

**参数说明**：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--symbol` | 交易对，如 ETHUSDT、BTCUSDT | 默认下载 BTCUSDT 和 ETHUSDT |
| `--interval` | K 线周期：1m / 5m / 15m / 1h / 4h / 1d | `5m` |
| `--days` | 回溯天数 | `60` |
| `--force` | 强制重新下载（覆盖已有文件） | 不覆盖 |

### 2.2 下载机制细节

- 数据来源：Binance 公开 API（`api.binance.com`），无需 API 密钥
- 遇到 **451 地区限制**时会自动切换备用端点（`api.binance.us`）
- 分批下载，每批最多 1000 根 K 线，批次间间隔 200ms 避免限频
- 失败自动重试，最多 3 次，指数退避等待
- 输出文件命名格式：`{symbol}_{interval}_{days}d.csv`
  - 例如：`data/crypto/ETHUSDT_5m_60d.csv`

### 2.3 数据格式

CSV 列格式（含表头）：

```csv
timestamp,open,high,low,close,volume
1700000000000,2050.50000000,2055.00000000,2048.20000000,2053.80000000,1234.56000000
```

- `timestamp`：Unix 毫秒时间戳
- `open/high/low/close`：价格（8 位小数）
- `volume`：成交量

---

## 三、策略回测

### 3.1 运行回测

```bash
# 使用默认配置（ETHUSDT / 5m / hybridmm 策略）
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar

# 指定策略和参数
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    --symbol ETHUSDT --interval 5m --strategy trend

# 禁用做空
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    --strategy scalp --no-short
```

**参数说明**：

| 参数 | 说明 | 默认值 |
|------|------|--------|
| `--symbol` | 交易对 | `ETHUSDT` |
| `--interval` | K 线周期 | `5m` |
| `--strategy` | 策略名称（见下表） | `hybridmm` |
| `--no-short` | 禁用做空 | 允许做空 |

### 3.2 可用策略

| 名称 | 说明 | 适用场景 |
|------|------|----------|
| `trend` | 布林带 + ADX 趋势确认 | 强趋势市场 |
| `trendfollow` | 双 EMA 趋势跟踪 | 中强趋势 |
| `scalp` | 紧布林带剥头皮 | 震荡市场 |
| `grid` | 网格交易 | 低波动震荡 |
| `hybrid` | ADX 自动切换趋势/震荡模式 | 全市场 |
| `adaptive` | RSI/EMA 自适应切换 | 全市场 |
| `pureaction` | 纯价格行为（K 线形态） | 全市场 |
| `pureactionv2` | 价格行为增强版 | 全市场 |
| `hybridmm` | RSI 均值回归 + EMA 动量过滤 | 震荡偏趋势 |
| `multitf` | 多时间框架加权投票 | 全市场 |

### 3.3 回测输出解读

运行后你会看到类似以下输出：

```
[INFO] 开始回测: strategy=HybridMeanRevMomentum, bars=17280, enableShort=true
[INFO] 市场机制: STRONG_UPTREND (ADX=32.5, Vol=45.2%)
[INFO] === 回测结果 ===
[INFO] 策略:         HybridMeanRevMomentum
[INFO] 总收益:       12.34%
[INFO] 年化收益:     45.67%
[INFO] 夏普比率:     2.150
[INFO] 最大回撤:     -8.42%
[INFO] 胜率:         62.5%
[INFO] 交易次数:     87
[INFO] 综合得分:     0.7240
```

**指标含义**：

| 指标 | 说明 | 理想范围 |
|------|------|----------|
| **总收益** | 回测周期内的累计收益率 | > 0% |
| **年化收益** | 折算成一年的收益率 | > 20% |
| **夏普比率** | 风险调整后的收益（越高越好） | > 1.5 |
| **最大回撤** | 峰值到谷值的最大跌幅 | < 15% 优秀，< 30% 可接受 |
| **胜率** | 盈利交易占总交易的比例 | > 50% |
| **交易次数** | 回测期间的交易总数 | > 50 有统计意义 |
| **综合得分** | 多维度加权评分，0~1 | > 0.6 较好 |

### 3.4 注意事项

回测引擎会对策略施加**硬性过滤**，不满足以下条件的策略综合得分直接为 0：

- 最大回撤不超过 30%
- 最终权益不低于初始资金的 70%
- 总收益不低于 -0.5%

模拟的交易成本：佣金 0.02%（单边）+ 滑点 0.02%。

---

## 四、策略参数搜索

当你想找到某个策略的最优参数时，可以使用策略搜索引擎。

### 4.1 编程方式调用

```java
import com.autoresearch.crypto.search.StrategySearchEngine;
import com.autoresearch.crypto.search.StrategySearchEngine.SearchResult;
import com.autoresearch.crypto.strategy.impl.TrendStrategy;
import com.autoresearch.crypto.data.MarketDataLoader;
import com.autoresearch.crypto.data.MarketData;
import java.util.Map;
import java.util.HashMap;

// 加载数据
MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");

// 创建搜索引擎（600 秒时间预算）
StrategySearchEngine searchEngine = new StrategySearchEngine(600);

// 定义参数搜索空间：key -> [min, max]
Map<String, double[]> paramSpace = new HashMap<>();
paramSpace.put("window", new double[]{10, 50});         // 布林带周期
paramSpace.put("stdDev", new double[]{1.0, 3.0});       // 布林带倍数
paramSpace.put("atrMultiplier", new double[]{1.5, 4.0}); // ATR 止损倍数
paramSpace.put("maxHoldBars", new double[]{12, 96});     // 最大持仓

// 执行随机搜索
SearchResult result = searchEngine.randomSearch(
    params -> new TrendStrategy(new HashMap<>(params)),  // 策略工厂
    paramSpace,
    data,
    true,   // 允许做空
    1000    // 最大试验次数
);

System.out.println("最优参数: " + result.bestParams());
System.out.println("最优得分: " + result.bestScore());
System.out.println("总收益: " + result.totalReturn() * 100 + "%");
System.out.println("夏普比率: " + result.sharpeRatio());
```

### 4.2 参数空间格式

搜索引擎支持两种参数空间定义：

**连续范围**（2 个元素）：在 [min, max] 内均匀随机采样
```java
paramSpace.put("stdDev", new double[]{1.0, 3.0});
```

**离散候选值**（3 个以上元素）：从候选值中随机选择
```java
paramSpace.put("rsiPeriod", new double[]{5, 7, 14, 21});
```

以下参数名称会自动识别为整数并取整：`window`、`atrPeriod`、`maxHoldBars`、`rsiPeriod`、`rsiLow`、`rsiHigh`、`maPeriod`、`adxPeriod`、`adxThreshold`、`gridLevels`、`trendMaPeriod`、`longMaPeriod`、`pullMaPeriod`、`maFast`、`maSlow`、`rsiOversold`、`rsiOverbought`。

### 4.3 多策略批量搜索

```java
Map<String, StrategySearchEngine.StrategyFactory> strategies = Map.of(
    "trend", params -> new TrendStrategy(new HashMap<>(params)),
    "scalp", params -> new ScalpStrategy(new HashMap<>(params)),
    "hybridmm", params -> new HybridMeanRevMomentumStrategy(new HashMap<>(params))
);

Map<String, Map<String, double[]>> paramSpaces = Map.of(
    "trend", trendParamSpace,
    "scalp", scalpParamSpace,
    "hybridmm", hybridMmParamSpace
);

// 搜索所有策略，每个策略 500 次试验
List<SearchResult> results = searchEngine.searchAll(
    strategies, paramSpaces, data, true, 500
);

// 结果按得分降序排列
for (SearchResult r : results) {
    System.out.printf("%s: score=%.4f, ret=%.2f%%, sharpe=%.2f%n",
        r.strategyName(), r.bestScore(), r.totalReturn() * 100, r.sharpeRatio());
}
```

搜索结束后会输出汇总排名，方便对比不同策略的最优表现。

---

## 五、实盘交易

### 5.1 准备工作

实盘交易前请确保：

1. ✅ 策略在回测中表现良好（综合得分 > 0.6，最大回撤 < 15%）
2. ✅ 已准备好交易所 API 密钥
3. ✅ 从小资金开始测试（建议初始 100 USDT）

### 5.2 Nado DEX 实盘

```java
import com.autoresearch.crypto.backtest.BacktestEngine;
import com.autoresearch.crypto.live.LiveTradingEngine;
import com.autoresearch.crypto.live.NadoConnector;
import com.autoresearch.crypto.strategy.BaseStrategy;

// 创建策略
BaseStrategy strategy = BacktestEngine.createStrategy("hybridmm");

// 创建 Nado DEX 连接器
NadoConnector connector = new NadoConnector(
    "https://api.nado.xyz",   // API 基础 URL
    "your-api-key",            // API Key
    "your-api-secret",         // API Secret
    "ETH-PERP"                 // 永续合约标识
);

// 启动实盘引擎
LiveTradingEngine engine = new LiveTradingEngine(
    strategy,
    connector,
    100.0,     // 可用资金（USDT）
    "5m",      // K 线周期
    true       // 允许做空
);

engine.start();  // 启动交易循环（阻塞主线程）
```

Nado DEX 的下单策略：
- **开仓**使用 `POST_ONLY` 挂单，确保以 Maker 费率成交（更低手续费）
- **平仓**使用 `IOC`（Immediate or Cancel）市价单，确保快速成交

### 5.3 OKX 实盘

```java
import com.autoresearch.crypto.live.OkxConnector;

OkxConnector connector = new OkxConnector(
    "your-api-key",
    "your-api-secret",
    "your-passphrase",  // OKX 特有的 passphrase
    "ETH-USDT-SWAP"     // 永续合约 ID
);

LiveTradingEngine engine = new LiveTradingEngine(
    strategy, connector, 100.0, "5m", true
);
engine.start();
```

### 5.4 实盘运行机制

交易引擎启动后的工作流程：

```
┌──────────────────────────┐
│  等待当前 K 线结束 (+15s)  │ ← 对齐到 K 线边界，额外等 15 秒确保数据更新
└────────────┬─────────────┘
             ▼
┌──────────────────────────┐
│  获取最新 500 根 K 线数据   │ ← connector.fetchLatestData(interval, 500)
└────────────┬─────────────┘
             ▼
┌──────────────────────────┐
│  策略生成交易信号          │ ← strategy.generateSignals(data, enableShort)
└────────────┬─────────────┘
             ▼
┌──────────────────────────┐
│  执行最新一根 K 线的信号    │ ← 0=平仓 / 1=持有 / 2=做多 / 3=做空
└────────────┬─────────────┘
             ▼
┌──────────────────────────┐
│  保存交易状态到 JSON 文件   │ ← logs/live_state.json
└────────────┬─────────────┘
             ▼
        回到等待步骤
```

**风险控制参数**：
- 开仓使用 95% 可用资金（保留 5% 缓冲）
- 默认止损：3%
- 默认止盈：5%

### 5.5 停止实盘

调用 `engine.stop()` 可优雅终止交易循环。引擎在当前周期完成后退出，不会中断正在执行的交易操作。

### 5.6 状态持久化

交易状态持久化到 `logs/live_state.json`，包含：
- 当前持仓方向和数量
- 入场价格
- 止损/止盈价位
- 累计已实现盈亏
- 交易历史记录

引擎重启时会自动加载上次状态，实现断点续跑。

---

## 六、进阶：策略进化

### 6.1 ATLAS 进化

适用于希望自动优化多种策略并发现最优组合的场景。

```java
import com.autoresearch.crypto.evolution.AtlasEvolutionEngine;
import com.autoresearch.crypto.evolution.Agent;
import com.autoresearch.crypto.strategy.StrategyEvaluator;

// 创建默认 4 个代理（Alpha/Beta/Gamma/Delta）
List<Agent> agents = AtlasEvolutionEngine.createDefaultAgents();
StrategyEvaluator evaluator = new StrategyEvaluator();

AtlasEvolutionEngine atlas = new AtlasEvolutionEngine(agents, evaluator);

// 执行多轮进化
for (int round = 0; round < 50; round++) {
    atlas.evolve(data);

    // 检查并处理停滞代理
    List<String> deadAgents = atlas.checkDeadAgents();
    if (!deadAgents.isEmpty()) {
        System.out.println("停滞代理: " + deadAgents);
    }
}
```

### 6.2 GEPA 反思

适用于希望深入分析策略为何表现好/差，并获得智能调参建议的场景。

```java
import com.autoresearch.crypto.evolution.GepaReflectionEngine;

GepaReflectionEngine gepa = new GepaReflectionEngine(agents);

for (Agent agent : agents) {
    // 生成假设（调参建议）
    var hypothesis = gepa.generateHypothesis(agent);
    System.out.println("建议: " + hypothesis.text());
    System.out.println("理由: " + hypothesis.rationale());
    System.out.println("参数调整: " + hypothesis.paramChanges());

    // 执行实验后记录结果...
    // gepa.recordExperiment(log);
}

// 每 5 次实验执行一次元反思
if (gepa.shouldReflect()) {
    String insight = gepa.metaReflect();
    System.out.println("元反思洞察: " + insight);
}
```

---

## 七、常用代码片段

### 7.1 加载数据并验证

```java
import com.autoresearch.crypto.data.*;

// 从 CSV 文件加载
MarketData data = MarketDataLoader.loadCsv(Path.of("data/crypto/ETHUSDT_5m_60d.csv"));

// 或通过默认路径加载（自动拼接路径）
MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");

// 验证数据质量
DataManager.ValidationResult validation = DataManager.validate(data);
validation.logResults();  // 输出错误/警告到日志
if (!validation.valid()) {
    System.err.println("数据验证失败: " + validation.errors());
}
```

### 7.2 Walk-Forward 验证

```java
import com.autoresearch.crypto.data.DataManager;

// 将数据分割为 3 个窗口（每个窗口 70% 训练 + 30% 验证）
List<DataManager.WalkForwardWindow> windows = DataManager.walkForwardSplit(data, 3);

for (DataManager.WalkForwardWindow window : windows) {
    // 在训练集上搜索最优参数
    SearchResult result = searchEngine.randomSearch(
        factory, paramSpace, window.train(), true, 200
    );

    // 在验证集上检验
    BaseStrategy strategy = factory.create(result.bestParams());
    int[] signals = strategy.generateSignals(window.validation(), true);
    EvaluationResult eval = evaluator.evaluate(signals, window.validation().close());

    System.out.printf("窗口 %d: 训练得分=%.4f, 验证得分=%.4f%n",
        window.index(), result.bestScore(), eval.score());
}
```

### 7.3 自定义策略参数

```java
import com.autoresearch.crypto.strategy.impl.HybridMeanRevMomentumStrategy;
import java.util.HashMap;
import java.util.Map;

Map<String, Object> params = new HashMap<>();
params.put("rsiPeriod", 7);
params.put("rsiLow", 22);
params.put("rsiHigh", 78);
params.put("maPeriod", 15);
params.put("atrMultiplier", 2.5);
params.put("maxHoldBars", 36);
params.put("takeProfitPct", 0.04);
params.put("stopLossPct", 0.015);

HybridMeanRevMomentumStrategy strategy = new HybridMeanRevMomentumStrategy(params);
```

### 7.4 分析市场状态

```java
import com.autoresearch.crypto.regime.*;

// 基于本地 K 线分析（回测/实盘通用）
RegimeInfo regime = MarketRegimeDetector.analyze(data);
System.out.println("市场机制: " + regime.regime().label());
System.out.println("ADX: " + regime.adx());
System.out.println("波动率: " + regime.volatilityAnnualized());

// 基于多源网络数据分析（仅实盘使用）
MultiSourceRegimeDetector detector = new MultiSourceRegimeDetector();
RegimeReport report = detector.analyze();
boolean[] filter = detector.directionFilter();
System.out.println("允许做多: " + filter[0]);
System.out.println("允许做空: " + filter[1]);
```

---

## 八、故障排查

| 问题 | 原因 | 解决方案 |
|------|------|----------|
| `Data file not found` | 未下载数据或文件名不匹配 | 先运行 `DataDownloader` 下载数据 |
| `HTTP 451 错误` | 地区限制，无法访问 Binance | 下载器会自动切换备用端点 |
| 综合得分为 0 | 触发硬性过滤（回撤 > 30% 等） | 调整策略参数或换策略 |
| 实盘连接失败 | API 密钥错误或网络问题 | 检查密钥配置和网络 |
| 数据验证警告 "零成交量 > 10%" | 数据可能来自低流动性时段 | 通常可忽略，不影响回测 |
| `未知策略: xxx` | 策略名称拼写错误 | 参考第三节的可用策略名称列表 |

---

## 九、推荐工作流

```
1. 下载数据
   └── DataDownloader --symbol ETHUSDT --interval 5m --days 60

2. 快速回测（选策略）
   └── 依次尝试 hybridmm / adaptive / multitf 等策略
   └── 选择综合得分最高、回撤最小的策略

3. 参数搜索（调参数）
   └── 对选中的策略执行随机搜索
   └── 注意搜索空间不要过大，保持合理边界

4. Walk-Forward 验证（防过拟合）
   └── 使用 3 窗口 Walk-Forward 分割
   └── 确保验证集得分与训练集接近

5. 小资金实盘测试
   └── 100 USDT 起步
   └── 观察 1~2 周实际表现

6. 逐步加仓
   └── 确认实盘与回测结果一致后逐步增加资金
```
