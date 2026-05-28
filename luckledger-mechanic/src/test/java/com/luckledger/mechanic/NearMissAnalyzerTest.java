package com.luckledger.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.params.provider.Arguments.arguments;

import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.NearMissResult;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class NearMissAnalyzerTest {

    private final NearMissAnalyzer analyzer = new NearMissAnalyzer();

    private static EvaluationResult loser(Map<String, Integer> matchDetails) {
        return new EvaluationResult(false, BigDecimal.ZERO, List.of(), matchDetails);
    }

    private static EvaluationResult winner(Map<String, Integer> matchDetails) {
        return new EvaluationResult(
                true, new BigDecimal("20"), List.of(new Position(0, 0), new Position(0, 1)), matchDetails);
    }

    @Test
    void match3LoserWithTwoOfThreeIsNearMiss() {
        NearMissResult result = analyzer.analyze(loser(Map.of("A", 2, "B", 1)), MechanicType.MATCH_3);

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
        assertThat(result.description()).isNotBlank();
    }

    @Test
    void match3LoserWithOneMatchIsNotNearMiss() {
        NearMissResult result = analyzer.analyze(loser(Map.of("A", 1, "B", 1, "C", 1)), MechanicType.MATCH_3);

        assertThat(result.isNearMiss()).isFalse();
        assertThat(result.distance()).isEqualTo(2);
    }

    @Test
    void match3LoserWithNoMatchesUsesFullDistance() {
        NearMissResult result = analyzer.analyze(loser(Map.of()), MechanicType.MATCH_3);

        assertThat(result.isNearMiss()).isFalse();
        assertThat(result.distance()).isEqualTo(3);
    }

    @Test
    void winnerIsNeverANearMiss() {
        NearMissResult result = analyzer.analyze(winner(Map.of("A", 3)), MechanicType.MATCH_3);

        assertThat(result.isNearMiss()).isFalse();
        assertThat(result.distance()).isZero();
    }

    @Test
    void ticTacToeUsesThreeInARowThreshold() {
        assertThat(analyzer.analyze(loser(Map.of("X", 2)), MechanicType.TIC_TAC_TOE).isNearMiss()).isTrue();
        assertThat(analyzer.analyze(loser(Map.of("X", 1)), MechanicType.TIC_TAC_TOE).isNearMiss()).isFalse();
    }

    @Test
    void bingoUsesLineOfFiveThreshold() {
        NearMissResult nearMiss = analyzer.analyze(loser(Map.of("row1", 4)), MechanicType.BINGO);
        NearMissResult far = analyzer.analyze(loser(Map.of("row1", 3)), MechanicType.BINGO);

        assertThat(nearMiss.isNearMiss()).isTrue();
        assertThat(nearMiss.distance()).isEqualTo(1);
        assertThat(far.isNearMiss()).isFalse();
        assertThat(far.distance()).isEqualTo(2);
    }

    @Test
    void celestialFortuneNeedsTwoMatchesForLowestPrize() {
        NearMissResult oneMatch = analyzer.analyze(loser(Map.of("winningNumbers", 1)), MechanicType.CELESTIAL_FORTUNE);
        NearMissResult noMatch = analyzer.analyze(loser(Map.of("winningNumbers", 0)), MechanicType.CELESTIAL_FORTUNE);

        assertThat(oneMatch.isNearMiss()).isTrue();
        assertThat(oneMatch.distance()).isEqualTo(1);
        assertThat(noMatch.isNearMiss()).isFalse();
        assertThat(noMatch.distance()).isEqualTo(2);
    }

    @Test
    void numberMatchLoserIsOneOverlapAwayFromWinning() {
        NearMissResult result = analyzer.analyze(loser(Map.of()), MechanicType.NUMBER_MATCH);

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
    }

    @Test
    void keySymbolLoserIsOneMatchAwayFromWinning() {
        NearMissResult result = analyzer.analyze(loser(Map.of()), MechanicType.KEY_SYMBOL);

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
    }

    @Test
    void demonSealIsNotSupportedByMatchCountAnalysis() {
        assertThatThrownBy(() -> analyzer.analyze(loser(Map.of("points", 3)), MechanicType.DEMON_SEAL))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("DEMON_SEAL");
    }

    @Test
    void crosswordIsNotSupportedByMatchCountAnalysis() {
        assertThatThrownBy(() -> analyzer.analyze(loser(Map.of("words", 2)), MechanicType.CROSSWORD))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("CROSSWORD");
    }

    @Test
    void rejectsNullResult() {
        assertThatThrownBy(() -> analyzer.analyze(null, MechanicType.MATCH_3))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void rejectsNullMechanicType() {
        assertThatThrownBy(() -> analyzer.analyze(loser(Map.of("A", 2)), null))
                .isInstanceOf(NullPointerException.class);
    }

    /** Every match-count mechanic paired with the match count its lowest prize requires. */
    static Stream<Arguments> matchCountMechanics() {
        return Stream.of(
                arguments(MechanicType.MATCH_3, 3),
                arguments(MechanicType.TIC_TAC_TOE, 3),
                arguments(MechanicType.BINGO, 5),
                arguments(MechanicType.CELESTIAL_FORTUNE, 2),
                arguments(MechanicType.NUMBER_MATCH, 1),
                arguments(MechanicType.KEY_SYMBOL, 1));
    }

    @ParameterizedTest(name = "{0}: one short of {1} matches is a near-miss")
    @MethodSource("matchCountMechanics")
    void oneMatchShortOfThresholdIsAlwaysNearMiss(MechanicType mechanic, int threshold) {
        NearMissResult result = analyzer.analyze(loser(Map.of("best", threshold - 1)), mechanic);

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}: zero matches is {1} away from a win")
    @MethodSource("matchCountMechanics")
    void zeroMatchesYieldsDistanceEqualToThreshold(MechanicType mechanic, int threshold) {
        NearMissResult result = analyzer.analyze(loser(Map.of()), mechanic);

        assertThat(result.distance()).isEqualTo(threshold);
        // A single-match threshold means zero matches is itself one short — i.e. a near-miss.
        assertThat(result.isNearMiss()).isEqualTo(threshold == 1);
    }

    @ParameterizedTest(name = "{0}: matches above threshold on a loser clamp distance to 0")
    @MethodSource("matchCountMechanics")
    void matchesAtOrAboveThresholdOnNonWinnerClampDistanceToZero(MechanicType mechanic, int threshold) {
        NearMissResult result = analyzer.analyze(loser(Map.of("best", threshold + 2)), mechanic);

        assertThat(result.distance()).isZero();
        assertThat(result.isNearMiss()).isFalse();
    }

    @ParameterizedTest(name = "{0}: a winner is never a near-miss")
    @MethodSource("matchCountMechanics")
    void winnerIsNeverANearMissForAnyMatchCountMechanic(MechanicType mechanic, int threshold) {
        NearMissResult result = analyzer.analyze(winner(Map.of("best", threshold)), mechanic);

        assertThat(result.isNearMiss()).isFalse();
        assertThat(result.distance()).isZero();
    }

    @Test
    void usesHighestSymbolMatchCountAcrossAllSymbols() {
        NearMissResult result = analyzer.analyze(loser(Map.of("A", 1, "B", 2, "C", 1)), MechanicType.MATCH_3);

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
    }

    @Test
    void nearMissDescriptionExplainsItIsOneShort() {
        NearMissResult result = analyzer.analyze(loser(Map.of("A", 2)), MechanicType.MATCH_3);

        assertThat(result.description())
                .containsIgnoringCase("near-miss")
                .contains("2 of 3");
    }

    @Test
    void nonNearMissDescriptionReportsRemainingDistance() {
        NearMissResult result = analyzer.analyze(loser(Map.of("A", 1)), MechanicType.MATCH_3);

        assertThat(result.description()).contains("2 away from a win");
    }

    @Test
    void winnerDescriptionStatesItIsNotANearMiss() {
        NearMissResult result = analyzer.analyze(winner(Map.of("A", 3)), MechanicType.MATCH_3);

        assertThat(result.description()).containsIgnoringCase("not a near-miss");
    }
}
