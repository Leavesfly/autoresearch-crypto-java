package io.leavesfly.autoresearch.crypto.scoring;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DisplayName("RiskAdjustedScoring - 风险调整评分")
class RiskAdjustedScoringTest {

    // -----------------------------------------------------------------------
    // riskAdjustedScore 测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("正常场景：良好指标应产生正分数")
    void goodMetrics_shouldProducePositiveScore() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                2.0,    // sharpe
                0.15,   // totalReturn
                -0.10,  // maxDrawdown
                0.55,   // winRate
                50,     // numTrades
                0.05,   // marketReturn
                10,     // minTrades
                0.30,   // maxDd
                0.30, 0.25, 0.20, 0.15, 0.10  // weights
        );

        assertThat(result.getScore()).isGreaterThan(0.0);
        assertThat(result.isValid()).isTrue();
        assertThat(result.getFlags()).isEmpty();
    }

    @Test
    @DisplayName("硬性失败：总收益 <= -90% 应返回零分")
    void catastrophicLoss_shouldReturnZeroScore() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                -3.0, -0.95, -0.80, 0.20, 100,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getScore()).isEqualTo(0.0);
        assertThat(result.getFlags()).contains(EdgeFlag.RISKY);
        assertThat(result.isValid()).isFalse();
    }

    @Test
    @DisplayName("回撤超限：回撤超过 maxDd 应标记 RISKY 并归零")
    void excessiveDrawdown_shouldFlagRiskyAndZeroScore() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                1.5, 0.10, -0.40, 0.50, 30,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getFlags()).contains(EdgeFlag.RISKY);
        assertThat(result.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("交易次数不足：应标记 OVERFIT 并归零")
    void tooFewTrades_shouldFlagOverfitAndZeroScore() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                2.0, 0.20, -0.05, 0.60, 5,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getFlags()).contains(EdgeFlag.OVERFIT);
        assertThat(result.getScore()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("默认权重重载：应正常工作")
    void defaultWeightsOverload_shouldWork() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                1.5, 0.10, -0.05, 0.55, 30
        );

        assertThat(result.getScore()).isGreaterThan(0.0);
        assertThat(result.isValid()).isTrue();
    }

    @Test
    @DisplayName("Sharpe 组件：Sharpe=3 应产生最大组件值 1.0")
    void sharpeComponentClamping_shouldClampCorrectly() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                3.0, 0.20, -0.05, 0.60, 50,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getSharpeComponent()).isCloseTo(1.0, within(0.01));
    }

    @Test
    @DisplayName("Sharpe 组件：负 Sharpe 应被截断在 0")
    void negativeSharpe_shouldClampToZero() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                -2.0, 0.10, -0.05, 0.55, 50,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getSharpeComponent()).isGreaterThanOrEqualTo(0.0);
    }

    @Test
    @DisplayName("回撤组件：零回撤应产生最大惩罚因子")
    void zeroDrawdown_shouldMaxDdComponent() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                1.0, 0.05, 0.0, 0.50, 50,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getDdComponent()).isCloseTo(1.0, within(0.01));
    }

    @Test
    @DisplayName("同时触发 RISKY + OVERFIT 应归零")
    void bothRiskyAndOverfit_shouldZeroScore() {
        ScoredResult result = RiskAdjustedScoring.riskAdjustedScore(
                1.0, 0.10, -0.50, 0.50, 3,
                0.0, 10, 0.30,
                0.30, 0.25, 0.20, 0.15, 0.10
        );

        assertThat(result.getFlags()).contains(EdgeFlag.RISKY, EdgeFlag.OVERFIT);
        assertThat(result.getScore()).isEqualTo(0.0);
    }

    // -----------------------------------------------------------------------
    // detectDeadAgent 测试
    // -----------------------------------------------------------------------

    @Test
    @DisplayName("停滞检测：分数无变化应返回 true")
    void stagnantScores_shouldDetectDead() {
        List<Double> scores = List.of(0.5, 0.5, 0.5, 0.5, 0.5);
        assertThat(RiskAdjustedScoring.detectDeadAgent(scores)).isTrue();
    }

    @Test
    @DisplayName("停滞检测：分数有变化应返回 false")
    void changingScores_shouldNotDetectDead() {
        List<Double> scores = List.of(0.5, 0.6, 0.7, 0.8, 0.9);
        assertThat(RiskAdjustedScoring.detectDeadAgent(scores)).isFalse();
    }

    @Test
    @DisplayName("停滞检测：历史记录不足应返回 false")
    void shortHistory_shouldNotDetectDead() {
        List<Double> scores = List.of(0.5, 0.5, 0.5);
        assertThat(RiskAdjustedScoring.detectDeadAgent(scores)).isFalse();
    }

    @Test
    @DisplayName("停滞检测：带自定义阈值")
    void customThreshold_shouldDetectDead() {
        List<Double> scores = List.of(0.1, 0.1, 0.1);
        assertThat(RiskAdjustedScoring.detectDeadAgent(scores, 3, 0.01)).isTrue();
    }

    @Test
    @DisplayName("停滞检测：微小波动在 epsilon 范围内应视为停滞")
    void tinyFluctuation_withinEpsilon_shouldDetectDead() {
        List<Double> scores = List.of(0.500, 0.5005, 0.5002, 0.5001, 0.5003);
        assertThat(RiskAdjustedScoring.detectDeadAgent(scores)).isTrue();
    }
}
