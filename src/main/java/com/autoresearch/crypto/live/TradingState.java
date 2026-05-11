package com.autoresearch.crypto.live;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 实盘交易状态持久化管理。
 * 在程序重启时恢复交易状态，避免重复开仓或丢失仓位信息。
 */
public class TradingState {

    private static final Logger logger = LoggerFactory.getLogger(TradingState.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 当前持仓方向：0=空仓, 1=多头, -1=空头 */
    private int position;
    /** 入场价格 */
    private double entryPrice;
    /** 入场时间 */
    private String entryTime;
    /** 持仓数量 */
    private double positionSize;
    /** 止损价格 */
    private double stopLoss;
    /** 止盈价格 */
    private double takeProfit;
    /** 累计已实现盈亏 */
    private double realizedPnl;
    /** 交易次数 */
    private int tradeCount;
    /** 额外自定义字段 */
    private Map<String, Object> extra;

    public TradingState() {
        this.position = 0;
        this.entryPrice = 0;
        this.entryTime = "";
        this.positionSize = 0;
        this.stopLoss = 0;
        this.takeProfit = 0;
        this.realizedPnl = 0;
        this.tradeCount = 0;
        this.extra = new HashMap<>();
    }

    /**
     * 从 JSON 文件加载交易状态。
     */
    public static TradingState load(Path filePath) {
        if (!Files.exists(filePath)) {
            logger.info("状态文件不存在，使用默认状态: {}", filePath);
            return new TradingState();
        }
        try {
            String json = Files.readString(filePath);
            return MAPPER.readValue(json, TradingState.class);
        } catch (IOException e) {
            logger.warn("加载状态文件失败: {}，使用默认状态", e.getMessage());
            return new TradingState();
        }
    }

    /**
     * 保存交易状态到 JSON 文件。
     */
    public void save(Path filePath) {
        try {
            Files.createDirectories(filePath.getParent());
            String json = MAPPER.writerWithDefaultPrettyPrinter().writeValueAsString(this);
            Files.writeString(filePath, json);
        } catch (IOException e) {
            logger.error("保存状态文件失败: {}", e.getMessage());
        }
    }

    /** 开多头仓位 */
    public void openLong(double price, double size, double sl, double tp) {
        this.position = 1;
        this.entryPrice = price;
        this.positionSize = size;
        this.stopLoss = sl;
        this.takeProfit = tp;
        this.entryTime = LocalDateTime.now().toString();
    }

    /** 开空头仓位 */
    public void openShort(double price, double size, double sl, double tp) {
        this.position = -1;
        this.entryPrice = price;
        this.positionSize = size;
        this.stopLoss = sl;
        this.takeProfit = tp;
        this.entryTime = LocalDateTime.now().toString();
    }

    /** 平仓 */
    public void closePosition(double exitPrice) {
        double pnl = 0;
        if (position == 1) {
            pnl = (exitPrice - entryPrice) / entryPrice * positionSize;
        } else if (position == -1) {
            pnl = (entryPrice - exitPrice) / entryPrice * positionSize;
        }
        this.realizedPnl += pnl;
        this.tradeCount++;
        this.position = 0;
        this.entryPrice = 0;
        this.positionSize = 0;
        this.stopLoss = 0;
        this.takeProfit = 0;
    }

    /** 是否有持仓 */
    public boolean hasPosition() {
        return position != 0;
    }

    // Getter/Setter
    public int getPosition() { return position; }
    public void setPosition(int position) { this.position = position; }
    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }
    public String getEntryTime() { return entryTime; }
    public void setEntryTime(String entryTime) { this.entryTime = entryTime; }
    public double getPositionSize() { return positionSize; }
    public void setPositionSize(double positionSize) { this.positionSize = positionSize; }
    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }
    public double getTakeProfit() { return takeProfit; }
    public void setTakeProfit(double takeProfit) { this.takeProfit = takeProfit; }
    public double getRealizedPnl() { return realizedPnl; }
    public void setRealizedPnl(double realizedPnl) { this.realizedPnl = realizedPnl; }
    public int getTradeCount() { return tradeCount; }
    public void setTradeCount(int tradeCount) { this.tradeCount = tradeCount; }
    public Map<String, Object> getExtra() { return extra; }
    public void setExtra(Map<String, Object> extra) { this.extra = extra; }
}
