package com.autoresearch.crypto.live;

import com.autoresearch.crypto.data.MarketData;

/**
 * 交易所连接器接口。
 * 定义了与交易所交互的标准方法，具体交易所通过实现此接口完成集成。
 */
public interface ExchangeConnector {

    /** 获取交易所名称 */
    String exchangeName();

    /** 获取最新K线数据 */
    MarketData fetchLatestData(String interval, int limit);

    /** 获取当前最新价格 */
    double fetchCurrentPrice();

    /** 获取最优买一/卖一价 */
    double[] fetchBestBidAsk();

    /** 开多头仓位 */
    boolean openLong(double price, double notional);

    /** 开空头仓位 */
    boolean openShort(double price, double notional);

    /** 平仓 */
    boolean closePosition(double price);

    /** 查询当前持仓 */
    PositionInfo queryPosition();

    /**
     * 持仓信息。
     */
    record PositionInfo(
            int side,         // 1=多, -1=空, 0=无
            double size,      // 持仓数量
            double entryPrice,// 入场均价
            double unrealizedPnl // 未实现盈亏
    ) {
        public static PositionInfo empty() {
            return new PositionInfo(0, 0, 0, 0);
        }
    }
}
