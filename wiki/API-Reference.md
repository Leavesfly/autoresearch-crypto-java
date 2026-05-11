# API 参考

## 包结构总览

```
com.autoresearch.crypto
├── backtest       回测引擎
├── config         全局配置
├── data           数据管理
├── evolution      进化引擎
├── indicator      技术指标
├── live           实盘交易
├── multitf        多时间框架分析
├── regime         市场机制检测
├── scoring        风险评分
├── search         策略搜索
└── strategy       策略框架
    └── impl       策略实现
```

---

## com.autoresearch.crypto.data

### MarketData (record)

```java
public record MarketData(
    double[] open,
    double[] high,
    double[] low,
    double[] close,
    double[] volume,
    long[] timestamps
) {
    public int length();
}
```

### DataManager

```java
public final class DataManager {
    public static List<WalkForwardWindow> walkForwardSplit(MarketData data, int nWindows);
    public static List<WalkForwardWindow> walkForwardSplit(MarketData data);
    public static ValidationResult validate(MarketData data);
    public static List<Path> listDataFiles() throws IOException;

    public record WalkForwardWindow(MarketData train, MarketData validation, int index) {}
    public record ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
        public void logResults();
    }
}
```

### MarketDataLoader

```java
public class MarketDataLoader {
    public static MarketData loadCsv(Path filePath) throws IOException;
    public static MarketData loadDefault(String symbol, String interval) throws IOException;
    public static MarketData slice(MarketData data, int start, int end);
}
```

### DataDownloader

```java
public class DataDownloader {
    public DataDownloader();
    public Path download(String symbol, String interval, int days, boolean force) throws IOException;
    public static void main(String[] args);
}
```

---

## com.autoresearch.crypto.strategy

### BaseStrategy (abstract)

```java
public abstract class BaseStrategy {
    protected Map<String, Object> params;

    public abstract int[] generateSignals(MarketData data, boolean enableShort);
    public abstract String name();

    public Map<String, Object> getParams();
    public void setParams(Map<String, Object> params);

    protected int getInt(String key, int defaultValue);
    protected double getDouble(String key, double defaultValue);
    protected boolean getBool(String key, boolean defaultValue);
}
```

### Signal (enum)

```java
public enum Signal {
    FLAT(0), HOLD(1), LONG(2), SHORT(3);

    public int code();
    public static Signal fromCode(int code);
}
```

### StrategyEvaluator

```java
public class StrategyEvaluator {
    public StrategyEvaluator();
    public StrategyEvaluator(double initialCapital, double commission, double slippage);

    public EvaluationResult evaluate(int[] signals, double[] prices);
}
```

### PerformanceMetrics (record)

```java
public record PerformanceMetrics(
    double totalReturn,
    double annualizedReturn,
    double annualizedVol,
    double sharpeRatio,
    double maxDrawdown,
    double winRate
) {
    public static PerformanceMetrics empty();
}
```

### EvaluationResult (record)

```java
public record EvaluationResult(
    double score,
    PerformanceMetrics metrics,
    List<TradeRecord> trades,
    double[] equityCurve
) {
    public static EvaluationResult empty();
}
```

### TradeRecord (record)

```java
public record TradeRecord(
    String type,    // "buy", "sell", "sell_short", "buy_cover", "sell_final"
    int step,
    Double pnl      // nullable for entry trades
) {
    public static TradeRecord entry(String type, int step);
    public static TradeRecord exit(String type, int step, double pnl);
    public boolean hasPnl();
}
```

---

## com.autoresearch.crypto.backtest

### BacktestEngine

```java
public class BacktestEngine {
    public BacktestEngine();
    public BacktestEngine(double initialCapital, double commission, double slippage);

    public BacktestResult run(BaseStrategy strategy, MarketData data, boolean enableShort);
    public static BaseStrategy createStrategy(String name);
    public static void main(String[] args);
}
```

### BacktestResult (record)

```java
public record BacktestResult(
    String strategyName,
    PerformanceMetrics metrics,
    double compositeScore,
    int tradeCount,
    double[] equityCurve,
    RegimeInfo regime
) {}
```

---

## com.autoresearch.crypto.live

### ExchangeConnector (interface)

```java
public interface ExchangeConnector {
    String exchangeName();
    MarketData fetchLatestData(String interval, int limit);
    double fetchCurrentPrice();
    double[] fetchBestBidAsk();
    boolean openLong(double price, double notional);
    boolean openShort(double price, double notional);
    boolean closePosition(double price);
    PositionInfo queryPosition();

    record PositionInfo(int side, double size, double entryPrice, double unrealizedPnl) {}
}
```

### LiveTradingEngine

```java
public class LiveTradingEngine {
    public LiveTradingEngine(
        BaseStrategy strategy, ExchangeConnector connector,
        double capital, String interval, boolean enableShort
    );

    public void start();
    public void stop();
}
```

### NadoConnector

```java
public class NadoConnector implements ExchangeConnector {
    public NadoConnector(String baseUrl, String apiKey, String apiSecret, String ticker);
    // 实现 ExchangeConnector 全部方法
}
```

### OkxConnector

```java
public class OkxConnector implements ExchangeConnector {
    public OkxConnector(String apiKey, String apiSecret, String passphrase, String instId);
    // 实现 ExchangeConnector 全部方法
}
```

---

## com.autoresearch.crypto.evolution

### AtlasEvolutionEngine

```java
public class AtlasEvolutionEngine {
    public AtlasEvolutionEngine(List<Agent> agents, StrategyEvaluator evaluator);

    public Map<String, Object> mutate(Map<String, Object> params, double scale);
    public Map<String, Object> crossover(Map<String, Object> worst, Map<String, Object> best);
    public void rebalanceWeights();
    public void evolve(MarketData data);
    public List<String> checkDeadAgents();
    public static List<Agent> createDefaultAgents();
}
```

### GepaReflectionEngine

```java
public class GepaReflectionEngine {
    public GepaReflectionEngine(List<Agent> agents);

    public Hypothesis generateHypothesis(Agent agent);
    public void recordExperiment(ExperimentLog log);
    public String metaReflect();
    public boolean shouldReflect();

    public record Hypothesis(
        String text, String agent,
        Map<String, Object> paramChanges, String rationale
    ) {}
}
```

### Agent

```java
public class Agent {
    public Agent(String name, String style, String strategyClassName, Map<String, Object> params);

    public String getName();
    public String getStyle();
    public String getStrategyClassName();
    public Map<String, Object> getParams();
    public void setParams(Map<String, Object> params);
    public List<Double> getScoreHistory();
    public double getWeight();
    public void setWeight(double weight);
    public int getGeneration();
    public void setGeneration(int generation);
    public void addScore(double score);
    public double recentScore(int window);
    public double recentScore();              // 默认 window=3
    public Agent copy();
}
```

### ExperimentLog

```java
public class ExperimentLog {
    public ExperimentLog();

    public String getTimestamp();
    public void setTimestamp(String timestamp);
    public String getAgent();
    public void setAgent(String agent);
    public String getHypothesis();
    public void setHypothesis(String hypothesis);
    public String getTried();
    public void setTried(String tried);
    public Map<String, Object> getParamsBefore();
    public void setParamsBefore(Map<String, Object> paramsBefore);
    public Map<String, Object> getParamsAfter();
    public void setParamsAfter(Map<String, Object> paramsAfter);
    public double getScoreBefore();
    public void setScoreBefore(double scoreBefore);
    public double getScoreAfter();
    public void setScoreAfter(double scoreAfter);
    public double getSharpeBefore();
    public void setSharpeBefore(double sharpeBefore);
    public double getSharpeAfter();
    public void setSharpeAfter(double sharpeAfter);
    public double getReturnBefore();
    public void setReturnBefore(double returnBefore);
    public double getReturnAfter();
    public void setReturnAfter(double returnAfter);
    public double getDdBefore();
    public void setDdBefore(double ddBefore);
    public double getDdAfter();
    public void setDdAfter(double ddAfter);
    public String getResultSummary();
    public void setResultSummary(String resultSummary);
    public String getReflection();
    public void setReflection(String reflection);
    public List<String> getEdgeFlags();
    public void setEdgeFlags(List<String> edgeFlags);

    public boolean isImprovement();
    public double scoreChange();
}
```

---

## com.autoresearch.crypto.indicator

### Indicators (utility)

```java
public final class Indicators {
    public static double[] computeAtr(double[] high, double[] low, double[] close, int period);
    public static double[][] computeAdx(double[] high, double[] low, double[] close, int period);
    public static double[] computeRsi(double[] close, int period);
    public static double[] computeEma(double[] series, int period);
    public static double[] computeSma(double[] series, int period);
    public static double[][] computeMacd(double[] close, int fast, int slow, int signal);
    public static double[] computeMfi(double[] high, double[] low, double[] close, double[] volume, int period);
    public static double[] computeStochastic(double[] high, double[] low, double[] close, int period);
    public static double[] computeObv(double[] close, double[] volume);
    public static double[] computeVwap(double[] high, double[] low, double[] close, double[] volume, int period);
    public static double[][] computeBollingerBands(double[] close, int period, double stdDevMultiplier);
}
```

---

## com.autoresearch.crypto.regime

### MarketRegimeDetector

```java
public class MarketRegimeDetector {
    public static RegimeInfo analyze(MarketData data);
}
```

### MultiSourceRegimeDetector

```java
public class MultiSourceRegimeDetector {
    public MultiSourceRegimeDetector();

    public RegimeReport analyze();
    public RegimeReport analyze(boolean forceRefresh);
    public boolean[] directionFilter();
}
```

### RegimeInfo (record)

```java
public record RegimeInfo(
    MarketRegime regime,
    double adx,
    String emaRelation,
    double priceVsEma200Pct,
    double volatilityAnnualized
) {}
```

### MarketRegime (enum)

```java
public enum MarketRegime {
    STRONG_UPTREND("强势上涨"),
    WEAK_UPTREND("弱势上涨"),
    RANGING("震荡"),
    WEAK_DOWNTREND("弱势下跌"),
    STRONG_DOWNTREND("强势下跌");

    public String label();
}
```

### RegimeReport

```java
public class RegimeReport {
    public RegimeReport();

    public String getTimestamp();
    public double getCompositeScore();
    public String getRegime();               // "BULLISH" / "BEARISH" / "NEUTRAL"
    public double getConfidence();
    public int getFearGreedValue();
    public String getFearGreedLabel();
    public double getFearGreedScore();
    public String getMacroLabel();
    public double getMacroScore();
    public double getDxy();
    public double getDxyChange30d();
    public double getNasdaq();
    public double getNasdaqChange30d();
    public double getBtcDominance();
    public double getBtcDominanceScore();
    public int getIndicatorsAvailable();
    public int getIndicatorsTotal();
    public List<String> getDetails();
    public String getRecommendation();
    // 对应的 setter 方法省略
}
```

---

## com.autoresearch.crypto.scoring

### RiskAdjustedScoring

```java
public final class RiskAdjustedScoring {
    public static ScoredResult riskAdjustedScore(
        double sharpe, double totalReturn, double maxDrawdown,
        double winRate, int numTrades, double marketReturn,
        int minTrades, double maxDd,
        double sharpeWeight, double ddWeight,
        double tradeWeight, double excessWeight, double wrWeight
    );
    public static ScoredResult riskAdjustedScore(
        double sharpe, double totalReturn, double maxDrawdown,
        double winRate, int numTrades
    );
    public static boolean detectDeadAgent(List<Double> scoreHistory, int deadThreshold, double epsilon);
    public static boolean detectDeadAgent(List<Double> scoreHistory);
}
```

### ScoredResult

```java
public class ScoredResult {
    public double getScore();
    public double getRawSharpe();
    public double getTotalReturn();
    public double getMaxDrawdown();
    public double getWinRate();
    public int getNumTrades();
    public List<EdgeFlag> getFlags();
    public double getSharpeComponent();
    public double getDdComponent();
    public double getTradeComponent();
    public double getExcessComponent();
    public boolean isValid();
    public boolean isDead();
}
```

### EdgeFlag (enum)

```java
public enum EdgeFlag {
    OVERFIT,   // 交易次数过少 — 可能过拟合
    RISKY,     // 回撤过大 — 风险过高
    DEAD       // 多轮无改进 — 策略已耗尽
}
```

---

## com.autoresearch.crypto.multitf

### MultiTimeframeAnalyzer

```java
public class MultiTimeframeAnalyzer {
    public MultiTimeframeAnalyzer();
    public MultiTimeframeAnalyzer(List<String> timeframes, int maPeriod, double minConsensus);

    public int[] compute(MarketData data);
    public String describe(MarketData data);
    public static MarketData resampleOhlcv(MarketData data, int tfBars);
}
```

---

## com.autoresearch.crypto.search

### StrategySearchEngine

```java
public class StrategySearchEngine {
    public StrategySearchEngine(int timeBudgetSeconds);

    public SearchResult randomSearch(
        StrategyFactory factory, Map<String, double[]> paramSpace,
        MarketData data, boolean enableShort, int maxTrials
    );
    public List<SearchResult> searchAll(
        Map<String, StrategyFactory> strategies,
        Map<String, Map<String, double[]>> paramSpaces,
        MarketData data, boolean enableShort, int trialsPerStrategy
    );

    @FunctionalInterface
    public interface StrategyFactory {
        BaseStrategy create(Map<String, Object> params);
    }

    public record SearchResult(
        String strategyName, Map<String, Object> bestParams,
        double bestScore, double totalReturn, double sharpeRatio,
        double maxDrawdown, double winRate, int tradeCount, int totalTrials
    ) {}
}
```

---

## com.autoresearch.crypto.config

### TradingConfig (final utility)

```java
public final class TradingConfig {
    // 路径配置
    public static final Path PROJECT_DIR;
    public static final Path DATA_DIR;
    public static final Path CHECKPOINT_DIR;
    public static final Path LOG_DIR;
    public static final Path SEARCH_RESULTS_DIR;

    // 数据配置
    public static final List<String> DEFAULT_SYMBOLS;
    public static final String DEFAULT_INTERVAL;
    public static final int DEFAULT_START_DAYS;

    // 交易参数
    public static final double INITIAL_CAPITAL;
    public static final double COMMISSION;
    public static final double SLIPPAGE;
    public static final double MIN_NOTIONAL;

    // 时间常量
    public static final int BARS_PER_DAY_5M;
    public static final int TRADING_DAYS_PER_YEAR;
    public static final int BARS_PER_YEAR;
    public static final Map<String, Integer> INTERVAL_SECONDS;

    // 评估阈值
    public static final int SEARCH_MIN_TRADES;
    public static final int EVAL_MIN_TRADES;
    public static final double EVAL_MAX_DRAWDOWN;
    public static final double EVAL_MIN_EQUITY_RATIO;
    public static final double EVAL_MIN_RETURN;

    // 训练/搜索
    public static final int TIME_BUDGET;
    public static final int WF_N_WINDOWS;

    // 策略默认参数
    public static final Map<String, Object> DEFAULT_TREND_PARAMS;
    public static final Map<String, Object> DEFAULT_SCALP_PARAMS;
    public static final Map<String, Object> DEFAULT_HYBRID_MM_PARAMS;
    public static final Map<String, Object> DEFAULT_PURE_ACTION_PARAMS;
}
```
