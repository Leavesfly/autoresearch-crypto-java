package io.leavesfly.autoresearch.crypto.strategy;

import io.leavesfly.autoresearch.crypto.config.TradingConfig;

import java.util.ArrayList;
import java.util.List;

/**
 * 交易模拟和性能评估引擎。
 * 支持带佣金和滑点的多空仓位。
 */
public class StrategyEvaluator {

    private final double initialCapital;
    private final double commission;
    private final double slippage;

    public StrategyEvaluator() {
        this(TradingConfig.INITIAL_CAPITAL, TradingConfig.COMMISSION, TradingConfig.SLIPPAGE);
    }

    public StrategyEvaluator(double initialCapital, double commission, double slippage) {
        this.initialCapital = initialCapital;
        this.commission = commission;
        this.slippage = slippage;
    }

    /**
     * 使用信号和价格数组模拟交易。
     * 信号：0=平仓，1=持仓，2=做多，3=做空。
     */
    public SimulationResult simulate(int[] signals, double[] prices) {
        double capital = initialCapital;
        double shares = 0.0;
        int position = 0; // 1=long, -1=short, 0=flat
        List<Double> equityList = new ArrayList<>();
        List<TradeRecord> trades = new ArrayList<>();
        double entryCostBasis = 0.0;
        double entryPrice = 0.0;

        for (int i = 0; i < signals.length; i++) {
            int signal = signals[i];
            double price = prices[i];

            int targetPos;
            if (signal == 2) {
                targetPos = 1;
            } else if (signal == 3) {
                targetPos = -1;
            } else if (signal == 0) {
                targetPos = 0;
            } else {
                targetPos = position;
            }

            if (targetPos != position) {
                // Close existing long position
                if (position == 1 && targetPos <= 0) {
                    double execPrice = price * (1 - slippage);
                    double gross = shares * execPrice;
                    double cost = gross * commission;
                    capital = gross - cost;
                    double pnl = capital - entryCostBasis;
                    trades.add(TradeRecord.exit("sell", i, pnl));
                    shares = 0.0;
                    position = 0;
                }

                // Close existing short position
                if (position == -1 && targetPos >= 0) {
                    double execPrice = price * (1 + slippage);
                    double buyCost = Math.abs(shares) * execPrice;
                    double buyCostTotal = buyCost * (1 + commission);
                    double pnl = entryCostBasis - buyCostTotal;
                    capital = capital + pnl;
                    if (capital < 0) capital = 0;
                    trades.add(TradeRecord.exit("buy_cover", i, pnl));
                    shares = 0.0;
                    position = 0;
                }

                // Open new long
                if (targetPos == 1 && position == 0 && capital > 0) {
                    double execPrice = price * (1 + slippage);
                    shares = capital * (1 - commission) / execPrice;
                    entryCostBasis = capital;
                    entryPrice = execPrice;
                    capital = 0.0;
                    trades.add(TradeRecord.entry("buy", i));
                    position = 1;
                }

                // Open new short
                if (targetPos == -1 && position == 0 && capital > 0) {
                    double execPrice = price * (1 - slippage);
                    shares = -(capital * (1 - commission) / execPrice);
                    entryCostBasis = capital;
                    entryPrice = execPrice;
                    capital = capital * (1 - commission);
                    trades.add(TradeRecord.entry("sell_short", i));
                    position = -1;
                }
            }

            // Compute current equity
            double currentEquity;
            if (position == 1) {
                currentEquity = capital + shares * price;
            } else if (position == -1) {
                currentEquity = capital + Math.abs(shares) * (entryPrice - price);
            } else {
                currentEquity = capital;
            }

            if (!Double.isFinite(currentEquity) || currentEquity > 1e15 || currentEquity < 0) {
                equityList.add(Math.max(0, Double.isFinite(currentEquity) ? currentEquity : 0));
                break;
            }
            equityList.add(currentEquity);
        }

        // Settle open positions at the last price
        if (position == 1 && !equityList.isEmpty()) {
            double execPrice = prices[prices.length - 1] * (1 - slippage);
            double gross = shares * execPrice;
            double cost = gross * commission;
            capital = gross - cost;
            double pnl = capital - entryCostBasis;
            trades.add(TradeRecord.exit("sell_final", signals.length - 1, pnl));
            equityList.set(equityList.size() - 1, capital);
        } else if (position == -1 && !equityList.isEmpty()) {
            double execPrice = prices[prices.length - 1] * (1 + slippage);
            double buyCost = Math.abs(shares) * execPrice * (1 + commission);
            capital = capital + (entryCostBasis - buyCost);
            equityList.set(equityList.size() - 1, capital);
        }

        double[] equityCurve = equityList.stream().mapToDouble(Double::doubleValue).toArray();
        return new SimulationResult(equityCurve, trades);
    }

    /**
     * 从权益曲线和交易记录计算性能指标。
     */
    public PerformanceMetrics computeMetrics(double[] equity, List<TradeRecord> trades) {
        if (equity.length == 0) {
            return PerformanceMetrics.empty();
        }

        double totalReturn = (equity[equity.length - 1] / equity[0]) - 1;
        totalReturn = Math.max(-1.0, Math.min(100.0, totalReturn));

        int nSteps = equity.length;
        double years = nSteps * 5.0 / (288.0 * 365.0);
        if (years < 0.01) years = 0.01;

        double annualizedReturn;
        try {
            annualizedReturn = Math.pow(1 + totalReturn, 1.0 / years) - 1;
            annualizedReturn = Math.max(-10.0, Math.min(10.0, annualizedReturn));
        } catch (Exception e) {
            annualizedReturn = 0.0;
        }

        // Compute returns
        double[] returns = new double[equity.length - 1];
        for (int i = 0; i < returns.length; i++) {
            returns[i] = equity[i] > 0 ? (equity[i + 1] - equity[i]) / equity[i] : 0;
        }

        double annualizedVol = 0;
        if (returns.length > 0) {
            double mean = 0;
            for (double r : returns) mean += r;
            mean /= returns.length;
            double sumSq = 0;
            for (double r : returns) sumSq += (r - mean) * (r - mean);
            annualizedVol = Math.sqrt(sumSq / returns.length) * Math.sqrt(288.0 * 365.0);
        }

        double sharpe = annualizedVol > 0 ? annualizedReturn / annualizedVol : 0;

        // Max drawdown
        double peak = equity[0];
        double maxDrawdown = 0;
        for (double e : equity) {
            if (e > peak) peak = e;
            double dd = (e - peak) / peak;
            if (dd < maxDrawdown) maxDrawdown = dd;
        }

        // Win rate
        long totalTrades = trades.stream().filter(TradeRecord::hasPnl).count();
        long winningTrades = trades.stream().filter(t -> t.hasPnl() && t.pnl() > 0).count();
        double winRate = totalTrades > 0 ? (double) winningTrades / totalTrades : 0.5;

        return new PerformanceMetrics(totalReturn, annualizedReturn, annualizedVol, sharpe, maxDrawdown, winRate);
    }

    /**
     * 评估信号并返回综合评分及指标。
     */
    public EvaluationResult evaluate(int[] signals, double[] prices) {
        SimulationResult sim = simulate(signals, prices);
        double[] equity = sim.equityCurve();
        List<TradeRecord> trades = sim.trades();

        if (equity.length == 0 || !allFinite(equity)) {
            return EvaluationResult.empty();
        }

        PerformanceMetrics metrics = computeMetrics(equity, trades);

        if (!Double.isFinite(metrics.sharpeRatio()) || !Double.isFinite(metrics.totalReturn())) {
            return new EvaluationResult(0.0, metrics, trades, equity);
        }
        if (metrics.maxDrawdown() < -TradingConfig.EVAL_MAX_DRAWDOWN) {
            return new EvaluationResult(0.0, metrics, trades, equity);
        }
        if (equity[equity.length - 1] < initialCapital * TradingConfig.EVAL_MIN_EQUITY_RATIO) {
            return new EvaluationResult(0.0, metrics, trades, equity);
        }
        if (metrics.totalReturn() <= TradingConfig.EVAL_MIN_RETURN) {
            return new EvaluationResult(0.0, metrics, trades, equity);
        }

        long nTrades = trades.stream().filter(TradeRecord::hasPnl).count();

        double ddPenalty = metrics.maxDrawdown() < 0
                ? Math.max(0, 1 - Math.abs(metrics.maxDrawdown()) / 0.20)
                : 1.0;
        double minTradePenalty = nTrades < TradingConfig.EVAL_MIN_TRADES
                ? Math.min(1.0, nTrades / (double) TradingConfig.EVAL_MIN_TRADES)
                : 1.0;

        double sharpeClamped = Math.max(0, Math.min(5.0, metrics.sharpeRatio()));
        double returnClamped = Math.max(0, Math.min(2.0, metrics.totalReturn()));
        double winRateClamped = Math.max(0, Math.min(1.0, metrics.winRate()));

        double score = sharpeClamped * 0.25
                + returnClamped * 0.15
                + winRateClamped * 0.10
                + ddPenalty * (1 + metrics.maxDrawdown()) * 0.15
                + Math.min(1.0, nTrades / 40.0) * 0.25
                + minTradePenalty * 0.10;

        return new EvaluationResult(score, metrics, trades, equity);
    }

    private boolean allFinite(double[] arr) {
        for (double v : arr) {
            if (!Double.isFinite(v)) return false;
        }
        return true;
    }

    public record SimulationResult(double[] equityCurve, List<TradeRecord> trades) {}
}
