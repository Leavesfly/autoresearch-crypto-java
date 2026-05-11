package com.autoresearch.crypto.strategy;

/**
 * Record of a single trade event.
 */
public record TradeRecord(
        String type,    // "buy", "sell", "sell_short", "buy_cover", "sell_final"
        int step,
        Double pnl      // nullable for entry trades
) {
    public static TradeRecord entry(String type, int step) {
        return new TradeRecord(type, step, null);
    }

    public static TradeRecord exit(String type, int step, double pnl) {
        return new TradeRecord(type, step, pnl);
    }

    public boolean hasPnl() {
        return pnl != null;
    }
}
