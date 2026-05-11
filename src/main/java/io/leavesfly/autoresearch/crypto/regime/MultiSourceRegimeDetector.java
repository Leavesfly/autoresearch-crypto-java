package io.leavesfly.autoresearch.crypto.regime;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 多源市场机制检测器（网络版）。
 * 聚合多个免费数据源的信号，计算综合牛熊评分（-1 到 +1）。
 * 用于实盘交易中的方向过滤，避免逆势交易。
 *
 * 数据源：
 * 1. Fear & Greed 指数（alternative.me API）— 加密市场情绪，逆向信号
 * 2. 宏观数据（DXY 美元指数 + Nasdaq）— 风险偏好
 * 3. BTC Dominance（CoinGecko API）— 资金轮动
 *
 * 使用方式：
 * <pre>
 *   MultiSourceRegimeDetector detector = new MultiSourceRegimeDetector();
 *   RegimeReport report = detector.analyze();
 *   if ("BEARISH".equals(report.getRegime())) {
 *       // 仅允许做空
 *   }
 * </pre>
 */
public class MultiSourceRegimeDetector {

    private static final Logger logger = LoggerFactory.getLogger(MultiSourceRegimeDetector.class);

    private final OkHttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final int cacheTtlSeconds;

    /** 缓存的报告 */
    private RegimeReport cachedReport;
    /** 缓存时间戳 */
    private long cacheTimestamp;

    public MultiSourceRegimeDetector(int cacheTtlSeconds) {
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(15, TimeUnit.SECONDS)
                .readTimeout(15, TimeUnit.SECONDS)
                .build();
        this.objectMapper = new ObjectMapper();
        this.cacheTtlSeconds = cacheTtlSeconds;
        this.cacheTimestamp = 0;
    }

    public MultiSourceRegimeDetector() {
        this(3600); // 默认缓存1小时
    }

    /**
     * 执行完整的多源市场机制分析。
     * 结果会被缓存，在 cacheTtlSeconds 内不会重复请求网络。
     */
    public RegimeReport analyze(boolean forceRefresh) {
        long now = System.currentTimeMillis() / 1000;
        if (!forceRefresh && cachedReport != null && (now - cacheTimestamp) < cacheTtlSeconds) {
            return cachedReport;
        }

        RegimeReport report = new RegimeReport();
        List<Double> scores = new ArrayList<>();
        List<Double> weights = new ArrayList<>();

        // 指标1：Fear & Greed 指数
        fetchFearGreed(report, scores, weights);

        // 指标2：宏观数据（通过 Yahoo Finance 的替代公开 API）
        fetchMacroData(report, scores, weights);

        // 指标3：BTC Dominance
        fetchBtcDominance(report, scores, weights);

        // 计算综合评分
        computeComposite(report, scores, weights);

        cachedReport = report;
        cacheTimestamp = now;
        return report;
    }

    public RegimeReport analyze() {
        return analyze(false);
    }

    /**
     * 获取方向过滤结果。
     * @return [allowLong, allowShort]，true 表示允许该方向交易
     */
    public boolean[] directionFilter() {
        RegimeReport report = analyze();
        if ("BULLISH".equals(report.getRegime()) && report.getConfidence() > 0.5) {
            return new boolean[]{true, false};
        } else if ("BEARISH".equals(report.getRegime()) && report.getConfidence() > 0.5) {
            return new boolean[]{false, true};
        }
        return new boolean[]{true, true};
    }

    // =========================================================================
    // 指标1：Fear & Greed 指数
    // =========================================================================

    /**
     * 从 alternative.me API 获取加密市场恐惧贪婪指数。
     * 极度恐惧 = 逆向看多，极度贪婪 = 逆向看空。
     */
    private void fetchFearGreed(RegimeReport report, List<Double> scores, List<Double> weights) {
        try {
            String url = "https://api.alternative.me/fng/?limit=1";
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "autoresearch-crypto/1.0").build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return;

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode data = root.path("data");
                if (data.isEmpty()) return;

                int value = data.get(0).path("value").asInt();
                String classification = data.get(0).path("value_classification").asText();

                report.setFearGreedValue(value);

                // 逆向解读：极度恐惧=看多信号，极度贪婪=看空信号
                double score;
                String label;
                if (value <= 25) {
                    label = "极度恐惧 (逆向看多)";
                    score = 0.8;
                } else if (value <= 40) {
                    label = "恐惧 (谨慎看多)";
                    score = 0.3;
                } else if (value <= 60) {
                    label = "中性";
                    score = 0.0;
                } else if (value <= 75) {
                    label = "贪婪 (谨慎看空)";
                    score = -0.3;
                } else {
                    label = "极度贪婪 (逆向看空)";
                    score = -0.8;
                }

                report.setFearGreedLabel(label);
                report.setFearGreedScore(score);
                scores.add(score);
                weights.add(0.35); // 最高权重：最具加密市场特异性
                report.setIndicatorsAvailable(report.getIndicatorsAvailable() + 1);
                report.getDetails().add(String.format("Fear&Greed: %d (%s) [%s]", value, classification, label));

                logger.debug("Fear&Greed: value={}, classification={}, score={}", value, classification, score);
            }
        } catch (Exception e) {
            logger.warn("获取 Fear&Greed 指数失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 指标2：宏观数据（DXY + Nasdaq）
    // =========================================================================

    /**
     * 获取宏观经济数据：美元指数 DXY 和纳斯达克指数。
     * DXY 走强 → 风险资产承压 → 看空加密；
     * 纳斯达克走强 → 风险偏好上升 → 看多加密。
     *
     * 使用 Yahoo Finance 的公开图表 API 获取数据。
     */
    private void fetchMacroData(RegimeReport report, List<Double> scores, List<Double> weights) {
        try {
            // 获取 DXY（美元指数）30日变化
            double dxyChange = fetchYahooChange("DX-Y.NYB", "1mo");
            // 获取 Nasdaq 30日变化
            double nasdaqChange = fetchYahooChange("%5EIXIC", "1mo");

            if (Double.isNaN(dxyChange) && Double.isNaN(nasdaqChange)) return;

            double score = 0.0;
            List<String> reasons = new ArrayList<>();

            // DXY: 美元走强 = 对加密看空
            if (!Double.isNaN(dxyChange)) {
                report.setDxyChange30d(dxyChange);
                if (dxyChange < -0.02) {
                    score += 0.4;
                    reasons.add("DXY走弱(看多)");
                } else if (dxyChange > 0.02) {
                    score -= 0.4;
                    reasons.add("DXY走强(看空)");
                }
            }

            // Nasdaq: 科技股上涨 = 风险偏好
            if (!Double.isNaN(nasdaqChange)) {
                report.setNasdaqChange30d(nasdaqChange);
                if (nasdaqChange > 0.03) {
                    score += 0.4;
                    reasons.add("Nasdaq上涨(看多)");
                } else if (nasdaqChange < -0.03) {
                    score -= 0.4;
                    reasons.add("Nasdaq下跌(看空)");
                }
            }

            String label = reasons.isEmpty() ? "宏观中性" : String.join(" + ", reasons);
            report.setMacroLabel(label);
            report.setMacroScore(Math.max(-1.0, Math.min(1.0, score)));
            scores.add(report.getMacroScore());
            weights.add(0.35); // 高权重：宏观环境驱动加密市场
            report.setIndicatorsAvailable(report.getIndicatorsAvailable() + 1);
            report.getDetails().add(String.format("宏观: %s (DXY变化=%.1f%%, Nasdaq变化=%.1f%%)",
                    label,
                    Double.isNaN(dxyChange) ? 0 : dxyChange * 100,
                    Double.isNaN(nasdaqChange) ? 0 : nasdaqChange * 100));

        } catch (Exception e) {
            logger.warn("获取宏观数据失败: {}", e.getMessage());
        }
    }

    /**
     * 从 Yahoo Finance Chart API 获取指定标的近期涨跌幅。
     * @param symbol Yahoo Finance 股票代码
     * @param range 时间范围（如 "1mo"）
     * @return 涨跌幅比例（如 0.05 表示上涨5%），获取失败返回 NaN
     */
    private double fetchYahooChange(String symbol, String range) {
        try {
            String url = String.format(
                    "https://query1.finance.yahoo.com/v8/finance/chart/%s?range=%s&interval=1d",
                    symbol, range);
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "autoresearch-crypto/1.0").build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return Double.NaN;

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode closes = root.path("chart").path("result").get(0)
                        .path("indicators").path("quote").get(0).path("close");

                if (closes.isEmpty() || closes.size() < 2) return Double.NaN;

                // 取第一个和最后一个有效收盘价
                double firstClose = Double.NaN;
                double lastClose = Double.NaN;

                for (int i = 0; i < closes.size(); i++) {
                    if (!closes.get(i).isNull()) {
                        if (Double.isNaN(firstClose)) {
                            firstClose = closes.get(i).asDouble();
                        }
                        lastClose = closes.get(i).asDouble();
                    }
                }

                if (Double.isNaN(firstClose) || firstClose == 0) return Double.NaN;
                return (lastClose - firstClose) / firstClose;
            }
        } catch (Exception e) {
            logger.debug("获取 Yahoo Finance 数据失败 ({}): {}", symbol, e.getMessage());
            return Double.NaN;
        }
    }

    // =========================================================================
    // 指标3：BTC Dominance
    // =========================================================================

    /**
     * 从 CoinGecko API 获取 BTC 市值占比。
     * BTC 占比上升 → 资金流出山寨币（risk-off）→ 看空山寨；
     * BTC 占比下降 → 山寨季来临（risk-on）→ 看多山寨。
     */
    private void fetchBtcDominance(RegimeReport report, List<Double> scores, List<Double> weights) {
        try {
            String url = "https://api.coingecko.com/api/v3/global";
            Request request = new Request.Builder().url(url)
                    .header("User-Agent", "autoresearch-crypto/1.0").build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) return;

                JsonNode root = objectMapper.readTree(response.body().string());
                JsonNode marketCapPct = root.path("data").path("market_cap_percentage");
                if (marketCapPct.isMissingNode()) return;

                double dominance = marketCapPct.path("btc").asDouble();
                report.setBtcDominance(dominance);

                // BTC dominance > 55% = risk-off, < 45% = alt season
                double score;
                String label;
                if (dominance > 55) {
                    label = "BTC主导高位 (risk-off)";
                    score = -0.3;
                } else if (dominance < 45) {
                    label = "BTC主导低位 (山寨季)";
                    score = 0.3;
                } else {
                    label = "BTC主导中性";
                    score = 0.0;
                }

                report.setBtcDominanceScore(score);
                scores.add(score);
                weights.add(0.30);
                report.setIndicatorsAvailable(report.getIndicatorsAvailable() + 1);
                report.getDetails().add(String.format("BTC Dominance: %.1f%% (%s)", dominance, label));

                logger.debug("BTC Dominance: {}%, score={}", String.format("%.1f", dominance), score);
            }
        } catch (Exception e) {
            logger.warn("获取 BTC Dominance 失败: {}", e.getMessage());
        }
    }

    // =========================================================================
    // 综合评分计算
    // =========================================================================

    /**
     * 根据各指标得分和权重，计算综合评分、置信度和最终机制判定。
     */
    private void computeComposite(RegimeReport report, List<Double> scores, List<Double> weights) {
        if (scores.isEmpty()) {
            report.setRecommendation("无可用数据源，使用技术面判断");
            return;
        }

        // 加权平均
        double totalWeight = 0;
        double weightedSum = 0;
        for (int i = 0; i < scores.size(); i++) {
            weightedSum += scores.get(i) * weights.get(i);
            totalWeight += weights.get(i);
        }
        double compositeScore = weightedSum / totalWeight;
        report.setCompositeScore(compositeScore);

        // 计算一致性（置信度）
        if (scores.size() >= 2) {
            int bullish = 0, bearish = 0;
            for (double s : scores) {
                if (s > 0.1) bullish++;
                else if (s < -0.1) bearish++;
            }
            int dominant = Math.max(bullish, bearish);
            int nonNeutral = bullish + bearish;
            report.setConfidence(nonNeutral > 0 ? (double) dominant / nonNeutral : 0);
        }

        // 机制分类
        if (compositeScore > 0.15) {
            report.setRegime("BULLISH");
        } else if (compositeScore < -0.15) {
            report.setRegime("BEARISH");
        } else {
            report.setRegime("NEUTRAL");
        }

        // 操作建议
        if ("BULLISH".equals(report.getRegime()) && report.getConfidence() > 0.6) {
            report.setRecommendation("偏多：优先做多，减少/停止做空");
        } else if ("BEARISH".equals(report.getRegime()) && report.getConfidence() > 0.6) {
            report.setRecommendation("偏空：优先做空，减少/停止做多");
        } else {
            report.setRecommendation("中性/低置信度：双向交易，收紧止损");
        }

        logger.info("多源机制检测完成: regime={}, score={}, confidence={}, 可用指标={}/{}",
                report.getRegime(),
                String.format("%.2f", report.getCompositeScore()),
                String.format("%.1f%%", report.getConfidence() * 100),
                report.getIndicatorsAvailable(), report.getIndicatorsTotal());
    }
}
