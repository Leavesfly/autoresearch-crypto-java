package com.autoresearch.crypto.data;

/**
 * 不可变的市场数据容器（OHLCV）。
 */
public record MarketData(
        double[] open,
        double[] high,
        double[] low,
        double[] close,
        double[] volume,
        long[] timestamps
) {
    public int length() {
        return close.length;
    }
}
