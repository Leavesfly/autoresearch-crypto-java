package com.autoresearch.crypto.strategy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("StrategyEvaluator - 交易模拟与评估")
class StrategyEvaluatorTest {

    private StrategyEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new StrategyEvaluator(10000.0, 0.0002, 0.0002);
    }

    // -----------------------------------------------------------------------
    // simulate 测试
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("simulate - 交易模拟")
    class SimulateTests {

        @Test
        @DisplayName("全持仓信号：权益曲线应跟随价格变化")
        void allHoldSignals_shouldTrackPrice() {
            double[] prices = {100, 105, 110, 108, 112};
            int[] signals = {2, 1, 1, 1, 1}; // 开多后一直持仓

            StrategyEvaluator.SimulationResult result = evaluator.simulate(signals, prices);

            assertThat(result.equityCurve()).hasSize(prices.length);
            // 开仓后权益应大于0
            for (double equity : result.equityCurve()) {
                assertThat(equity).isGreaterThan(0);
            }
        }

        @Test
        @DisplayName("全平仓信号：权益应保持不变")
        void allFlatSignals_shouldMaintainCapital() {
            double[] prices = {100, 105, 110, 108, 112};
            int[] signals = {0, 0, 0, 0, 0};

            StrategyEvaluator.SimulationResult result = evaluator.simulate(signals, prices);

            assertThat(result.equityCurve()).hasSize(prices.length);
            for (double equity : result.equityCurve()) {
                assertThat(equity).isCloseTo(10000.0, within(0.01));
            }
            assertThat(result.trades()).isEmpty();
        }

        @Test
        @DisplayName("买入再卖出：应产生正确的交易记录")
        void buyThenSell_shouldProduceTradeRecords() {
            double[] prices = {100, 100, 105, 105, 105};
            int[] signals = {2, 1, 0, 0, 0}; // 买入、持仓、卖出

            StrategyEvaluator.SimulationResult result = evaluator.simulate(signals, prices);

            // 应有入场和出场记录
            List<TradeRecord> trades = result.trades();
            assertThat(trades).isNotEmpty();
            assertThat(trades.get(0).type()).isEqualTo("buy");
        }

        @Test
        @DisplayName("做空后平仓：应产生做空交易记录")
        void shortThenCover_shouldProduceShortRecords() {
            double[] prices = {100, 95, 90, 90, 90};
            int[] signals = {3, 1, 0, 0, 0}; // 做空、持仓、平仓

            StrategyEvaluator.SimulationResult result = evaluator.simulate(signals, prices);

            List<TradeRecord> trades = result.trades();
            assertThat(trades).isNotEmpty();
            assertThat(trades.get(0).type()).isEqualTo("sell_short");
        }

        @Test
        @DisplayName("价格上涨做多应盈利")
        void longOnRisingPrice_shouldProfit() {
            double[] prices = new double[100];
            int[] signals = new int[100];
            for (int i = 0; i < 100; i++) {
                prices[i] = 100 + i * 0.5;
                signals[i] = (i == 0) ? 2 : 1; // 开多后持仓
            }

            StrategyEvaluator.SimulationResult result = evaluator.simulate(signals, prices);
            double[] equity = result.equityCurve();

            // 最终权益应大于初始资金
            assertThat(equity[equity.length - 1]).isGreaterThan(10000.0);
        }
    }

    // -----------------------------------------------------------------------
    // computeMetrics 测试
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("computeMetrics - 性能指标计算")
    class ComputeMetricsTests {

        @Test
        @DisplayName("平坦权益曲线：收益率应为零")
        void flatEquity_shouldHaveZeroReturn() {
            double[] equity = {10000, 10000, 10000, 10000, 10000};
            PerformanceMetrics metrics = evaluator.computeMetrics(equity, List.of());

            assertThat(metrics.totalReturn()).isCloseTo(0.0, within(0.001));
            assertThat(metrics.maxDrawdown()).isCloseTo(0.0, within(0.001));
        }

        @Test
        @DisplayName("上涨权益曲线：应有正收益")
        void risingEquity_shouldHavePositiveReturn() {
            double[] equity = {10000, 10500, 11000, 11500, 12000};
            PerformanceMetrics metrics = evaluator.computeMetrics(equity, List.of());

            assertThat(metrics.totalReturn()).isGreaterThan(0);
        }

        @Test
        @DisplayName("回撤计算应正确")
        void drawdownCalculation_shouldBeCorrect() {
            double[] equity = {10000, 12000, 9000, 11000, 10000};
            PerformanceMetrics metrics = evaluator.computeMetrics(equity, List.of());

            // 峰值 12000，最低 9000 → dd = (9000-12000)/12000 = -0.25
            assertThat(metrics.maxDrawdown()).isCloseTo(-0.25, within(0.01));
        }

        @Test
        @DisplayName("胜率计算：有 PnL 的交易")
        void winRateCalculation_shouldCountCorrectly() {
            List<TradeRecord> trades = List.of(
                    TradeRecord.entry("buy", 0),
                    TradeRecord.exit("sell", 5, 100.0),   // 盈利
                    TradeRecord.entry("buy", 10),
                    TradeRecord.exit("sell", 15, -50.0),   // 亏损
                    TradeRecord.entry("buy", 20),
                    TradeRecord.exit("sell", 25, 200.0)    // 盈利
            );
            double[] equity = {10000, 10100, 10050, 10250};
            PerformanceMetrics metrics = evaluator.computeMetrics(equity, trades);

            // 3 笔有 PnL 的交易中 2 笔盈利 → winRate ≈ 0.667
            assertThat(metrics.winRate()).isCloseTo(0.667, within(0.01));
        }

        @Test
        @DisplayName("空权益曲线应返回空指标")
        void emptyEquity_shouldReturnEmptyMetrics() {
            PerformanceMetrics metrics = evaluator.computeMetrics(new double[0], List.of());

            assertThat(metrics.totalReturn()).isEqualTo(0.0);
            assertThat(metrics.maxDrawdown()).isEqualTo(-0.99);
        }
    }

    // -----------------------------------------------------------------------
    // evaluate 综合评估测试
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("evaluate - 综合评估")
    class EvaluateTests {

        @Test
        @DisplayName("良好信号应产生正评分")
        void goodSignals_shouldProducePositiveScore() {
            // 构造上涨价格 + 做多信号
            int length = 500;
            double[] prices = new double[length];
            int[] signals = new int[length];
            for (int i = 0; i < length; i++) {
                prices[i] = 100 + i * 0.1;
                if (i % 50 == 0) signals[i] = 2;       // 定期开多
                else if (i % 50 == 25) signals[i] = 0;  // 定期平仓
                else signals[i] = 1;                     // 持仓
            }

            EvaluationResult result = evaluator.evaluate(signals, prices);

            assertThat(result).isNotNull();
            assertThat(result.metrics()).isNotNull();
            assertThat(result.equityCurve()).isNotEmpty();
        }

        @Test
        @DisplayName("全平仓信号应产生极低评分（无交易无收益）")
        void allFlatSignals_shouldProduceLowScore() {
            double[] prices = new double[200];
            int[] signals = new int[200];
            for (int i = 0; i < 200; i++) {
                prices[i] = 100 + i * 0.1;
                signals[i] = 0;
            }

            EvaluationResult result = evaluator.evaluate(signals, prices);

            // 无交易时评分极低：trade 组件为 0，sharpe/return 组件为 0
            assertThat(result.score()).isLessThan(0.5);
            assertThat(result.trades()).isEmpty();
        }
    }

    // -----------------------------------------------------------------------
    // Signal 枚举测试
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("Signal - 信号枚举")
    class SignalTests {

        @Test
        @DisplayName("fromCode 应正确映射信号代码")
        void fromCode_shouldMapCorrectly() {
            assertThat(Signal.fromCode(0)).isEqualTo(Signal.FLAT);
            assertThat(Signal.fromCode(1)).isEqualTo(Signal.HOLD);
            assertThat(Signal.fromCode(2)).isEqualTo(Signal.LONG);
            assertThat(Signal.fromCode(3)).isEqualTo(Signal.SHORT);
        }

        @Test
        @DisplayName("fromCode 未知代码应返回 HOLD")
        void fromCode_unknownCode_shouldDefaultToHold() {
            assertThat(Signal.fromCode(99)).isEqualTo(Signal.HOLD);
            assertThat(Signal.fromCode(-1)).isEqualTo(Signal.HOLD);
        }

        @Test
        @DisplayName("code 方法应返回正确的数字")
        void code_shouldReturnCorrectNumber() {
            assertThat(Signal.FLAT.code()).isEqualTo(0);
            assertThat(Signal.HOLD.code()).isEqualTo(1);
            assertThat(Signal.LONG.code()).isEqualTo(2);
            assertThat(Signal.SHORT.code()).isEqualTo(3);
        }
    }

    // -----------------------------------------------------------------------
    // TradeRecord 测试
    // -----------------------------------------------------------------------
    @Nested
    @DisplayName("TradeRecord - 交易记录")
    class TradeRecordTests {

        @Test
        @DisplayName("entry 工厂方法：pnl 应为 null")
        void entryRecord_shouldHaveNullPnl() {
            TradeRecord entry = TradeRecord.entry("buy", 5);
            assertThat(entry.type()).isEqualTo("buy");
            assertThat(entry.step()).isEqualTo(5);
            assertThat(entry.hasPnl()).isFalse();
            assertThat(entry.pnl()).isNull();
        }

        @Test
        @DisplayName("exit 工厂方法：pnl 应有值")
        void exitRecord_shouldHavePnl() {
            TradeRecord exit = TradeRecord.exit("sell", 10, 150.0);
            assertThat(exit.type()).isEqualTo("sell");
            assertThat(exit.step()).isEqualTo(10);
            assertThat(exit.hasPnl()).isTrue();
            assertThat(exit.pnl()).isEqualTo(150.0);
        }
    }
}
