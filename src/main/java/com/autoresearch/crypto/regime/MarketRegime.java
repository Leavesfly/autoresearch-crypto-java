package com.autoresearch.crypto.regime;

/**
 * 市场状态分类。
 */
public enum MarketRegime {
    STRONG_UPTREND("强势上涨"),
    WEAK_UPTREND("弱势上涨"),
    RANGING("震荡"),
    WEAK_DOWNTREND("弱势下跌"),
    STRONG_DOWNTREND("强势下跌");

    private final String label;

    MarketRegime(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
