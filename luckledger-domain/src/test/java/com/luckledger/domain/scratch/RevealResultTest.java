package com.luckledger.domain.scratch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.theme.ThemedCell;
import com.luckledger.domain.generation.theme.ThemedGrid;
import com.luckledger.domain.generation.theme.ThemedSymbol;
import com.luckledger.domain.mechanic.EvaluationResult;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevealResultTest {

    private static final UUID T = UUID.randomUUID();

    private static ThemedGrid grid() {
        int dim = GridSize.THREE.dimension();
        ThemedSymbol sym = new ThemedSymbol("X", "x", null, "X");
        ThemedCell[][] cells = new ThemedCell[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                cells[r][c] = new ThemedCell(new Position(r, c), sym);
            }
        }
        return new ThemedGrid(GridSize.THREE, cells);
    }

    private static EvaluationResult eval(boolean winner, String prize) {
        return new EvaluationResult(winner, new BigDecimal(prize), List.of(), Map.of());
    }

    @Test
    void mirrorsAWinningEvaluation() {
        EvaluationResult e = eval(true, "25");
        RevealResult result = new RevealResult(T, grid(), e, new BigDecimal("25"), true);

        assertThat(result.isWinner()).isTrue();
        assertThat(result.prizeAmount()).isEqualByComparingTo("25");
        assertThat(result.evaluationResult()).isSameAs(e);
    }

    @Test
    void mirrorsALosingEvaluation() {
        RevealResult result = new RevealResult(T, grid(), eval(false, "0"), BigDecimal.ZERO, false);

        assertThat(result.isWinner()).isFalse();
    }

    @Test
    void inconsistentWinnerFlagIsRejected() {
        assertThatThrownBy(() -> new RevealResult(T, grid(), eval(true, "25"), new BigDecimal("25"), false))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void prizeNotMatchingEvaluationIsRejected() {
        assertThatThrownBy(() -> new RevealResult(T, grid(), eval(true, "25"), new BigDecimal("10"), true))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new RevealResult(null, grid(), eval(false, "0"), BigDecimal.ZERO, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RevealResult(T, null, eval(false, "0"), BigDecimal.ZERO, false))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new RevealResult(T, grid(), null, BigDecimal.ZERO, false))
                .isInstanceOf(NullPointerException.class);
    }
}
