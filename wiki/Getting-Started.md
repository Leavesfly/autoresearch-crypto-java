# 快速开始

## 环境要求

- **JDK 17+**（推荐 OpenJDK 17 或 GraalVM 17）
- **Maven 3.8+**
- 网络连接（用于下载市场数据）

## 构建项目

```bash
cd autoresearch-crypto-java

# 编译
mvn clean compile

# 打包（跳过测试）
mvn package -DskipTests

# 运行测试
mvn test
```

## 运行回测

### 使用默认策略

```bash
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar
```

默认配置：
- 交易对：`ETHUSDT`
- K线周期：`5m`
- 策略：`hybridmm`（混合均值回归动量）
- 允许做空：是

### 自定义参数

```bash
java -jar target/autoresearch-crypto-1.0.0-SNAPSHOT.jar \
    --symbol BTCUSDT \
    --interval 5m \
    --strategy trend \
    --no-short
```

### 可用策略名称

| 名称 | 策略类 | 说明 |
|------|--------|------|
| `trend` | TrendStrategy | 趋势策略 |
| `scalp` | ScalpStrategy | 剥头皮策略 |
| `hybridmm` | HybridMeanRevMomentumStrategy | 混合均值回归动量 |
| `pureaction` | PureActionStrategy | 纯价格行为 |
| `pureactionv2` | PureActionV2Strategy | 纯价格行为 V2 |
| `grid` | GridStrategy | 网格交易 |
| `trendfollow` | TrendFollowStrategy | 趋势跟踪 |
| `adaptive` | AdaptiveStrategy | 自适应策略 |
| `hybrid` | HybridStrategy | 混合策略 |
| `multitf` | MultiTfEnsembleStrategy | 多时间框架集成 |

## 数据准备

市场数据以 CSV 格式存储在 `data/crypto/` 目录下。数据格式：

```csv
timestamp,open,high,low,close,volume
1700000000000,2050.5,2055.0,2048.2,2053.8,1234.56
```

使用 `DataDownloader` 从交易所下载数据：

```java
DataDownloader downloader = new DataDownloader();
downloader.download("ETHUSDT", "5m", 60, false); // 下载60天的5分钟K线，不强制覆盖
```

## 代码示例

### 运行单策略回测

```java


// 从 CSV 文件加载数据
MarketData data=MarketDataLoader.loadCsv(Path.of("data/crypto/ETHUSDT_5m_60d.csv"));

// 或通过默认路径加载（自动拼接 data/crypto/{symbol}_{interval}.csv）
// MarketData data = MarketDataLoader.loadDefault("ETHUSDT", "5m");

// 创建策略
        BaseStrategy strategy=BacktestEngine.createStrategy("hybridmm");

// 运行回测
        BacktestEngine engine=new BacktestEngine();
        BacktestResult result=engine.run(strategy,data,true);

        System.out.println("综合评分: "+result.compositeScore());
        System.out.println("交易次数: "+result.tradeCount());
```

### 自定义策略参数

```java


TrendStrategy strategy=new TrendStrategy();
        strategy.setParams(Map.of(
        "window",25,
        "stdDev",1.8,
        "atrMultiplier",3.0,
        "maxHoldBars",36
        ));
```

### 策略搜索

```java


StrategySearchEngine searchEngine=new StrategySearchEngine(600); // 600秒预算

        Map<String, double[]>paramSpace=new HashMap<>();
        paramSpace.put("window",new double[]{10,50});
        paramSpace.put("stdDev",new double[]{1.0,3.0});
        paramSpace.put("atrMultiplier",new double[]{1.5,4.0});

        SearchResult result=searchEngine.randomSearch(
        params->new TrendStrategy(new HashMap<>(params)),
        paramSpace,data,true,1000
        );
```

## 输出示例

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

## 下一步

- 了解 [交易策略](Strategies.md) 的工作原理
- 探索 [回测引擎](Backtesting.md) 的高级用法
- 配置 [实盘交易](Live-Trading.md) 连接真实交易所
