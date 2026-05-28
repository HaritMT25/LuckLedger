package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class EvaluationResultTest {

    @Test
    void shouldExposeAllComponentsForWinningResult() {
        List<Position> positions = List.of(new Position(0, 0), new Position(1, 1), new Position(2, 2));
        Map<String, Integer> details = Map.of("STAR", 3, "MOON", 1);

        EvaluationResult result = new EvaluationResult(true, new BigDecimal("50.00"), positions, details);

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("50.00");
        assertThat(result.winningPositions()).containsExactlyElementsOf(positions);
        assertThat(result.matchDetails()).containsEntry("STAR", 3).containsEntry("MOON", 1);
    }

    @Test
    void shouldRepresentLosingResultWithNoWinningPositions() {
        Map<String, Integer> details = Map.of("STAR", 2, "MOON", 2);

        EvaluationResult result = new EvaluationResult(false, BigDecimal.ZERO, List.of(), details);

        assertThat(result.isWinner()).isFalse();
        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
        assertThat(result.winningPositions()).isEmpty();
        assertThat(result.matchDetails()).containsEntry("STAR", 2);
    }

    @Test
    void shouldAcceptZeroPrizeAmount() {
        EvaluationResult result = new EvaluationResult(false, BigDecimal.ZERO, List.of(), Map.of());

        assertThat(result.prizeAmount()).isEqualByComparingTo("0");
    }

    @Test
    void shouldRejectNullPrizeAmount() {
        assertThatThrownBy(() -> new EvaluationResult(true, null, List.of(), Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("prizeAmount");
    }

    @Test
    void shouldRejectNegativePrizeAmount() {
        assertThatThrownBy(() -> new EvaluationResult(true, new BigDecimal("-0.01"), List.of(), Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("prizeAmount");
    }

    @Test
    void shouldRejectNullWinningPositions() {
        assertThatThrownBy(() -> new EvaluationResult(true, BigDecimal.ONE, null, Map.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("winningPositions");
    }

    @Test
    void shouldRejectNullMatchDetails() {
        assertThatThrownBy(() -> new EvaluationResult(true, BigDecimal.ONE, List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("matchDetails");
    }

    @Test
    void shouldDefensivelyCopyWinningPositions() {
        List<Position> source = new ArrayList<>();
        source.add(new Position(0, 0));

        EvaluationResult result = new EvaluationResult(true, BigDecimal.ONE, source, Map.of());
        source.add(new Position(1, 1));

        assertThat(result.winningPositions()).hasSize(1);
    }

    @Test
    void shouldDefensivelyCopyMatchDetails() {
        Map<String, Integer> source = new HashMap<>();
        source.put("STAR", 2);

        EvaluationResult result = new EvaluationResult(false, BigDecimal.ZERO, List.of(), source);
        source.put("MOON", 3);

        assertThat(result.matchDetails()).hasSize(1);
    }

    @Test
    void shouldReturnUnmodifiableWinningPositions() {
        EvaluationResult result =
                new EvaluationResult(true, BigDecimal.ONE, List.of(new Position(0, 0)), Map.of());

        assertThatThrownBy(() -> result.winningPositions().add(new Position(2, 2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableMatchDetails() {
        EvaluationResult result =
                new EvaluationResult(false, BigDecimal.ZERO, List.of(), Map.of("STAR", 2));

        assertThatThrownBy(() -> result.matchDetails().put("MOON", 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
