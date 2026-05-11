package io.leavesfly.autoresearch.crypto.regime;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 多源市场机制综合评估报告。
 * 聚合 Fear & Greed、宏观数据、BTC Dominance 等多个指标，
 * 计算综合牛熊评分，供实盘交易作为方向过滤器使用。
 */
public class RegimeReport {

    /** 报告生成时间 */
    private String timestamp;
    /** 综合评分：-1（看空）到 +1（看多） */
    private double compositeScore;
    /** 机制分类：BULLISH / BEARISH / NEUTRAL */
    private String regime;
    /** 置信度：0-1，表示多个指标一致性 */
    private double confidence;

    // Fear & Greed 指标
    private int fearGreedValue;
    private String fearGreedLabel;
    private double fearGreedScore;

    // 宏观指标（DXY + Nasdaq）
    private String macroLabel;
    private double macroScore;
    private double dxy;
    private double dxyChange30d;
    private double nasdaq;
    private double nasdaqChange30d;

    // BTC Dominance
    private double btcDominance;
    private double btcDominanceScore;

    // 统计
    private int indicatorsAvailable;
    private int indicatorsTotal;
    private List<String> details;
    private String recommendation;

    public RegimeReport() {
        this.timestamp = LocalDateTime.now().toString();
        this.compositeScore = 0;
        this.regime = "NEUTRAL";
        this.confidence = 0;
        this.fearGreedValue = 50;
        this.fearGreedLabel = "";
        this.fearGreedScore = 0;
        this.macroLabel = "";
        this.macroScore = 0;
        this.btcDominance = 50;
        this.btcDominanceScore = 0;
        this.indicatorsAvailable = 0;
        this.indicatorsTotal = 3;
        this.details = new ArrayList<>();
        this.recommendation = "";
    }

    // Getter/Setter
    public String getTimestamp() { return timestamp; }
    public void setTimestamp(String timestamp) { this.timestamp = timestamp; }
    public double getCompositeScore() { return compositeScore; }
    public void setCompositeScore(double compositeScore) { this.compositeScore = compositeScore; }
    public String getRegime() { return regime; }
    public void setRegime(String regime) { this.regime = regime; }
    public double getConfidence() { return confidence; }
    public void setConfidence(double confidence) { this.confidence = confidence; }
    public int getFearGreedValue() { return fearGreedValue; }
    public void setFearGreedValue(int fearGreedValue) { this.fearGreedValue = fearGreedValue; }
    public String getFearGreedLabel() { return fearGreedLabel; }
    public void setFearGreedLabel(String fearGreedLabel) { this.fearGreedLabel = fearGreedLabel; }
    public double getFearGreedScore() { return fearGreedScore; }
    public void setFearGreedScore(double fearGreedScore) { this.fearGreedScore = fearGreedScore; }
    public String getMacroLabel() { return macroLabel; }
    public void setMacroLabel(String macroLabel) { this.macroLabel = macroLabel; }
    public double getMacroScore() { return macroScore; }
    public void setMacroScore(double macroScore) { this.macroScore = macroScore; }
    public double getDxy() { return dxy; }
    public void setDxy(double dxy) { this.dxy = dxy; }
    public double getDxyChange30d() { return dxyChange30d; }
    public void setDxyChange30d(double dxyChange30d) { this.dxyChange30d = dxyChange30d; }
    public double getNasdaq() { return nasdaq; }
    public void setNasdaq(double nasdaq) { this.nasdaq = nasdaq; }
    public double getNasdaqChange30d() { return nasdaqChange30d; }
    public void setNasdaqChange30d(double nasdaqChange30d) { this.nasdaqChange30d = nasdaqChange30d; }
    public double getBtcDominance() { return btcDominance; }
    public void setBtcDominance(double btcDominance) { this.btcDominance = btcDominance; }
    public double getBtcDominanceScore() { return btcDominanceScore; }
    public void setBtcDominanceScore(double btcDominanceScore) { this.btcDominanceScore = btcDominanceScore; }
    public int getIndicatorsAvailable() { return indicatorsAvailable; }
    public void setIndicatorsAvailable(int indicatorsAvailable) { this.indicatorsAvailable = indicatorsAvailable; }
    public int getIndicatorsTotal() { return indicatorsTotal; }
    public void setIndicatorsTotal(int indicatorsTotal) { this.indicatorsTotal = indicatorsTotal; }
    public List<String> getDetails() { return details; }
    public void setDetails(List<String> details) { this.details = details; }
    public String getRecommendation() { return recommendation; }
    public void setRecommendation(String recommendation) { this.recommendation = recommendation; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("===== 多源市场机制报告 =====\n");
        sb.append(String.format("机制: %s | 综合评分: %.2f | 置信度: %.1f%%\n",
                regime, compositeScore, confidence * 100));
        sb.append(String.format("可用指标: %d/%d\n", indicatorsAvailable, indicatorsTotal));
        for (String detail : details) {
            sb.append("  • ").append(detail).append("\n");
        }
        sb.append("建议: ").append(recommendation).append("\n");
        return sb.toString();
    }
}
