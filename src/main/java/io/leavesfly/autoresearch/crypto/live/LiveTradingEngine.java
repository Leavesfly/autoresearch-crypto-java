package io.leavesfly.autoresearch.crypto.live;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;
import io.leavesfly.autoresearch.crypto.data.MarketData;
import io.leavesfly.autoresearch.crypto.strategy.BaseStrategy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;

/**
 * 实盘交易引擎。
 * 管理交易循环：获取数据 → 生成信号 → 执行订单 → 更新状态。
 * 具体交易所的下单逻辑由 ExchangeConnector 子类实现。
 */
public class LiveTradingEngine {

    private static final Logger logger = LoggerFactory.getLogger(LiveTradingEngine.class);

    private final BaseStrategy strategy;
    private final ExchangeConnector connector;
    private final TradingState state;
    private final Path statePath;
    private final double capital;
    private final String interval;
    private final boolean enableShort;
    private volatile boolean running;

    public LiveTradingEngine(BaseStrategy strategy, ExchangeConnector connector,
                             double capital, String interval, boolean enableShort) {
        this.strategy = strategy;
        this.connector = connector;
        this.capital = capital;
        this.interval = interval;
        this.enableShort = enableShort;
        this.statePath = TradingConfig.LOG_DIR.resolve("live_state.json");
        this.state = TradingState.load(statePath);
        this.running = false;
    }

    /**
     * 启动交易循环。
     * 每个 K 线周期结束后唤醒，获取最新数据、生成信号、执行交易。
     */
    public void start() {
        running = true;
        int intervalSeconds = TradingConfig.INTERVAL_SECONDS.getOrDefault(interval, 300);

        logger.info("═══════════════════════════════════════════");
        logger.info("  实盘交易启动");
        logger.info("  策略: {}  周期: {}  资金: {}", strategy.name(), interval, capital);
        logger.info("  做空: {}  交易所: {}", enableShort, connector.exchangeName());
        logger.info("═══════════════════════════════════════════");

        while (running) {
            try {
                // 等待到下一根K线结束
                long nextWake = alignNextWakeTime(intervalSeconds, 15);
                long sleepMs = nextWake - System.currentTimeMillis();
                if (sleepMs > 0) {
                    logger.info("等待下一根K线... 预计 {}s 后唤醒", sleepMs / 1000);
                    Thread.sleep(sleepMs);
                }

                // 执行一次交易决策
                executeCycle();

            } catch (InterruptedException e) {
                logger.info("交易循环被中断，正在退出...");
                running = false;
            } catch (Exception e) {
                logger.error("交易循环异常: {}", e.getMessage(), e);
                sleep(5000);
            }
        }

        logger.info("交易引擎已停止");
    }

    /** 停止交易循环 */
    public void stop() {
        running = false;
    }

    /**
     * 执行单次交易决策循环。
     */
    private void executeCycle() {
        // 1. 获取最新市场数据
        MarketData data = connector.fetchLatestData(interval, 500);
        if (data == null || data.length() < 100) {
            logger.warn("数据不足，跳过本轮");
            return;
        }

        // 2. 生成交易信号
        int[] signals = strategy.generateSignals(data, enableShort);
        int latestSignal = signals[signals.length - 1];
        double latestPrice = data.close()[data.length() - 1];

        logger.info("信号: {} | 价格: {} | 持仓: {}",
                signalLabel(latestSignal), String.format("%.2f", latestPrice), positionLabel());

        // 3. 根据信号执行交易
        executeSignal(latestSignal, latestPrice);

        // 4. 保存状态
        state.save(statePath);
    }

    /**
     * 根据信号执行交易逻辑。
     */
    private void executeSignal(int signal, double price) {
        int currentPos = state.getPosition();

        // 信号 0 = 平仓
        if (signal == 0 && currentPos != 0) {
            logger.info(">>> 平仓信号，执行平仓");
            closePosition(price);
            return;
        }

        // 信号 2 = 做多
        if (signal == 2 && currentPos != 1) {
            // 先平空头（如果有）
            if (currentPos == -1) {
                closePosition(price);
            }
            // 开多
            openLong(price);
            return;
        }

        // 信号 3 = 做空
        if (signal == 3 && currentPos != -1 && enableShort) {
            // 先平多头（如果有）
            if (currentPos == 1) {
                closePosition(price);
            }
            // 开空
            openShort(price);
            return;
        }

        // 信号 1 = 持有，不操作
    }

    /** 开多头仓位 */
    private void openLong(double price) {
        double size = capital * 0.95; // 保留5%作为缓冲
        double stopLoss = price * 0.97; // 默认3%止损
        double takeProfit = price * 1.05; // 默认5%止盈

        boolean success = connector.openLong(price, size);
        if (success) {
            state.openLong(price, size, stopLoss, takeProfit);
            logger.info("✓ 开多成功: 价格={}, 金额={}", String.format("%.2f", price), String.format("%.2f", size));
        } else {
            logger.error("✗ 开多失败: 价格={}", String.format("%.2f", price));
        }
    }

    /** 开空头仓位 */
    private void openShort(double price) {
        double size = capital * 0.95;
        double stopLoss = price * 1.03;
        double takeProfit = price * 0.95;

        boolean success = connector.openShort(price, size);
        if (success) {
            state.openShort(price, size, stopLoss, takeProfit);
            logger.info("✓ 开空成功: 价格={}, 金额={}", String.format("%.2f", price), String.format("%.2f", size));
        } else {
            logger.error("✗ 开空失败: 价格={}", String.format("%.2f", price));
        }
    }

    /** 平仓 */
    private void closePosition(double price) {
        boolean success = connector.closePosition(price);
        if (success) {
            state.closePosition(price);
            logger.info("✓ 平仓成功: 价格={}, 累计PnL={}", String.format("%.2f", price), String.format("%.4f", state.getRealizedPnl()));
        } else {
            logger.error("✗ 平仓失败: 价格={}", String.format("%.2f", price));
        }
    }

    /** 计算下一次唤醒时间（对齐到K线边界 + 偏移） */
    private long alignNextWakeTime(int intervalSeconds, int offsetSeconds) {
        long nowMs = System.currentTimeMillis();
        long intervalMs = (long) intervalSeconds * 1000;
        long nextBoundary = ((nowMs / intervalMs) + 1) * intervalMs;
        return nextBoundary + (long) offsetSeconds * 1000;
    }

    private String signalLabel(int signal) {
        return switch (signal) {
            case 0 -> "平仓";
            case 1 -> "持有";
            case 2 -> "做多";
            case 3 -> "做空";
            default -> "未知";
        };
    }

    private String positionLabel() {
        return switch (state.getPosition()) {
            case 1 -> "多头";
            case -1 -> "空头";
            default -> "空仓";
        };
    }

    private void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
