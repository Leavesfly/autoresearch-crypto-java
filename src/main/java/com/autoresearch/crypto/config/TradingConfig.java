package com.autoresearch.crypto.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;

/**
 * 交易框架集中式配置常量。
 * 对应 Python 版本的 dex/config.py。
 */
public final class TradingConfig {

    private TradingConfig() {}

    // -----------------------------------------------------------------------
    // 路径配置
    // -----------------------------------------------------------------------
    public static final Path PROJECT_DIR = Paths.get(System.getProperty("user.dir"));
    public static final Path DATA_DIR = PROJECT_DIR.resolve("data").resolve("crypto");
    public static final Path CHECKPOINT_DIR = PROJECT_DIR.resolve("checkpoints");
    public static final Path LOG_DIR = PROJECT_DIR.resolve("logs");
    public static final Path SEARCH_RESULTS_DIR = PROJECT_DIR.resolve("search_results");

    // 数据相关
    public static final List<String> DEFAULT_SYMBOLS = List.of("BTCUSDT", "ETHUSDT");
    public static final String DEFAULT_INTERVAL = "5m";
    public static final int DEFAULT_START_DAYS = 60;

    // -----------------------------------------------------------------------
    // 交易默认参数
    // -----------------------------------------------------------------------
    public static final double INITIAL_CAPITAL = 10000.0;
    public static final double COMMISSION = 0.0002;      // 0.02% — DEX Maker 手续费
    public static final double SLIPPAGE = 0.0002;        // 0.02% — 执行滑点
    public static final double MIN_NOTIONAL = 100.0;     // 最小下单金额（USDT）

    // -----------------------------------------------------------------------
    // 时间常量（以5分钟K线为基准）
    // -----------------------------------------------------------------------
    public static final int BARS_PER_DAY_5M = 288;      // 每天K线数 = 24 * 60 / 5
    public static final int TRADING_DAYS_PER_YEAR = 365;
    public static final int BARS_PER_YEAR = BARS_PER_DAY_5M * TRADING_DAYS_PER_YEAR;

    /** K线周期对应的秒数映射 */
    public static final Map<String, Integer> INTERVAL_SECONDS = Map.of(
            "1m", 60,
            "5m", 300,
            "15m", 900,
            "1h", 3600,
            "4h", 14400,
            "1d", 86400
    );

    // -----------------------------------------------------------------------
    // 评估阈值
    // -----------------------------------------------------------------------
    public static final int SEARCH_MIN_TRADES = 50;       // 搜索时最少交易次数
    public static final int EVAL_MIN_TRADES = 10;         // 评估时最少交易次数
    public static final double EVAL_MAX_DRAWDOWN = 0.30;  // 最大允许回撤
    public static final double EVAL_MIN_EQUITY_RATIO = 0.70; // 最低权益比率
    public static final double EVAL_MIN_RETURN = -0.005;  // 最低允许收益率

    // -----------------------------------------------------------------------
    // 训练 / 搜索
    // -----------------------------------------------------------------------
    public static final int TIME_BUDGET = 600;           // 搜索时间预算（秒）
    public static final int WF_N_WINDOWS = 3;            // Walk-Forward 滚动窗口数

    // -----------------------------------------------------------------------
    // 策略默认参数
    // -----------------------------------------------------------------------
    public static final Map<String, Object> DEFAULT_TREND_PARAMS = Map.ofEntries(
            Map.entry("window", 20),
            Map.entry("stdDev", 2.0),
            Map.entry("atrMultiplier", 2.5),
            Map.entry("maxHoldBars", 48),
            Map.entry("adxThreshold", 25),
            Map.entry("rsiThreshold", 30),
            Map.entry("entryZone", 1.0),
            Map.entry("takeProfitPct", 0.05),
            Map.entry("stopLossPct", 0.03)
    );

    public static final Map<String, Object> DEFAULT_SCALP_PARAMS = Map.of(
            "window", 10,
            "stdDev", 1.2,
            "takeProfitPct", 0.005,
            "stopLossPct", 0.003,
            "maxHoldBars", 6,
            "rsiPeriod", 14
    );

    public static final Map<String, Object> DEFAULT_HYBRID_MM_PARAMS = Map.ofEntries(
            Map.entry("rsiPeriod", 5),
            Map.entry("rsiLow", 28),
            Map.entry("rsiHigh", 72),
            Map.entry("maPeriod", 25),
            Map.entry("atrPeriod", 12),
            Map.entry("atrMultiplier", 3.5),
            Map.entry("maxHoldBars", 48),
            Map.entry("takeProfitPct", 0.03),
            Map.entry("stopLossPct", 0.02)
    );

    public static final Map<String, Object> DEFAULT_PURE_ACTION_PARAMS = Map.of(
            "window", 20,
            "stdDev", 2.0,
            "atrPeriod", 14,
            "atrMultiplier", 2.0,
            "maxHoldBars", 36,
            "entryZone", 0.0,
            "enableShort", true
    );
}
