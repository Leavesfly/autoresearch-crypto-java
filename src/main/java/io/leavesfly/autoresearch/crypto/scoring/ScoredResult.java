package io.leavesfly.autoresearch.crypto.scoring;

import java.util.ArrayList;
import java.util.List;

/**
 * 带有边界情况诊断的完整评分输出。
 */
public class ScoredResult {

    private final double score;
    private final double rawSharpe;
    private final double totalReturn;
    private final double maxDrawdown;
    private final double winRate;
    private final int numTrades;
    private final List<EdgeFlag> flags;

    private final double sharpeComponent;
    private final double ddComponent;
    private final double tradeComponent;
    private final double excessComponent;

    public ScoredResult(double score, double rawSharpe, double totalReturn,
                        double maxDrawdown, double winRate, int numTrades,
                        List<EdgeFlag> flags, double sharpeComponent,
                        double ddComponent, double tradeComponent, double excessComponent) {
        this.score = score;
        this.rawSharpe = rawSharpe;
        this.totalReturn = totalReturn;
        this.maxDrawdown = maxDrawdown;
        this.winRate = winRate;
        this.numTrades = numTrades;
        this.flags = flags != null ? flags : new ArrayList<>();
        this.sharpeComponent = sharpeComponent;
        this.ddComponent = ddComponent;
        this.tradeComponent = tradeComponent;
        this.excessComponent = excessComponent;
    }

    public boolean isValid() {
        return !flags.contains(EdgeFlag.RISKY) && !flags.contains(EdgeFlag.OVERFIT);
    }

    public boolean isDead() {
        return flags.contains(EdgeFlag.DEAD);
    }

    public double getScore() { return score; }
    public double getRawSharpe() { return rawSharpe; }
    public double getTotalReturn() { return totalReturn; }
    public double getMaxDrawdown() { return maxDrawdown; }
    public double getWinRate() { return winRate; }
    public int getNumTrades() { return numTrades; }
    public List<EdgeFlag> getFlags() { return flags; }
    public double getSharpeComponent() { return sharpeComponent; }
    public double getDdComponent() { return ddComponent; }
    public double getTradeComponent() { return tradeComponent; }
    public double getExcessComponent() { return excessComponent; }
}
