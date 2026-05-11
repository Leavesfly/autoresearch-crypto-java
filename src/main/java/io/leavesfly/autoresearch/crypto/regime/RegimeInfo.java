package io.leavesfly.autoresearch.crypto.regime;

/**
 * 市场状态分析结果。
 */
public record RegimeInfo(
        MarketRegime regime,
        double adx,
        String emaRelation,
        double priceVsEma200Pct,
        double volatilityAnnualized
) {}
