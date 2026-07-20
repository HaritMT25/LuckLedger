package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.api.OutcomeNarrative.CellRef;
import com.luckledger.api.persistence.GridCodec;
import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.Position;
import com.luckledger.mechanic.NearMissAnalyzer;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RevealNarratorTest {

    private final RevealNarrator narrator = new RevealNarrator(new NearMissAnalyzer());

    @Test
    void celestialWinner_reportsMatchCountPositionsAndSummaryConsistentWithPrize() {
        // Winning row {1,2,3,4}; player rows match three of them (1,2,3) → 3 matches → 20 coins.
        Grid grid = grid(GridSize.FOUR, new String[][] {
                {"1", "2", "3", "4"},
                {"1", "2", "3", "5"},
                {"6", "7", "8", "9"},
                {"10", "11", "12", "13"},
        });

        OutcomeNarrative narrative = narrator.narrate(
                GridCodec.toDto(grid), MechanicType.CELESTIAL_FORTUNE, new BigDecimal("20"), UUID.randomUUID());

        assertThat(narrative).isNotNull();
        assertThat(narrative.matchCount()).isEqualTo(3);
        assertThat(narrative.matchesNeeded()).isEqualTo(2);
        assertThat(narrative.sealScore()).isNull();
        assertThat(narrative.nearMiss()).isFalse();
        assertThat(narrative.summary()).isEqualTo("Matched 3 of the 4 winning numbers — 20 coins.");
        assertThat(narrative.matchedPositions())
                .containsExactlyInAnyOrder(new CellRef(1, 0), new CellRef(1, 1), new CellRef(1, 2));
    }

    @Test
    void celestialCleanLoser_hasZeroMatchesAndIsNotANearMiss() {
        Grid grid = grid(GridSize.FOUR, new String[][] {
                {"1", "2", "3", "4"},
                {"5", "6", "7", "8"},
                {"9", "10", "11", "12"},
                {"13", "14", "15", "16"},
        });

        OutcomeNarrative narrative = narrator.narrate(
                GridCodec.toDto(grid), MechanicType.CELESTIAL_FORTUNE, BigDecimal.ZERO, UUID.randomUUID());

        assertThat(narrative).isNotNull();
        assertThat(narrative.matchCount()).isZero();
        assertThat(narrative.nearMiss()).isFalse();
        assertThat(narrative.matchedPositions()).isEmpty();
        assertThat(narrative.summary()).isEqualTo("None of your numbers matched the winning row.");
    }

    @Test
    void demonThreePointLoser_isNearMissWithPointsNeededOne() {
        // Three silver seals score T = 3 — one short of the 4-point win floor. Prize is 0 (a loser).
        Grid grid = grid(GridSize.THREE, new String[][] {
                {"SILVER", "SILVER", "SILVER"},
                {"BROKEN", "BROKEN", "BROKEN"},
                {"RUNE", "SKULL", "CHAIN"},
        });

        OutcomeNarrative narrative = narrator.narrate(
                GridCodec.toDto(grid), MechanicType.DEMON_SEAL, BigDecimal.ZERO, UUID.randomUUID());

        assertThat(narrative).isNotNull();
        assertThat(narrative.matchCount()).isNull();
        assertThat(narrative.sealScore()).isEqualTo(3);
        assertThat(narrative.gold()).isZero();
        assertThat(narrative.silver()).isEqualTo(3);
        assertThat(narrative.pointsNeeded()).isEqualTo(4);
        assertThat(narrative.nearMiss()).isTrue();
        assertThat(narrative.summary())
                .isEqualTo("3 seal points — one short of the 4-point floor. That near-miss is by design.");
        assertThat(narrative.matchedPositions())
                .containsExactlyInAnyOrder(new CellRef(0, 0), new CellRef(0, 1), new CellRef(0, 2));
    }

    @Test
    void demonWinner_sealsTheDemonWithPrizeConsistentSummary() {
        // Two gold + two silver = T = 6 → 10 coins.
        Grid grid = grid(GridSize.THREE, new String[][] {
                {"GOLD", "GOLD", "SILVER"},
                {"SILVER", "BROKEN", "BROKEN"},
                {"RUNE", "SKULL", "CHAIN"},
        });

        OutcomeNarrative narrative = narrator.narrate(
                GridCodec.toDto(grid), MechanicType.DEMON_SEAL, new BigDecimal("10"), UUID.randomUUID());

        assertThat(narrative).isNotNull();
        assertThat(narrative.sealScore()).isEqualTo(6);
        assertThat(narrative.nearMiss()).isFalse();
        assertThat(narrative.summary()).isEqualTo("6 seal points (2 gold, 2 silver) — 10 coins. The demon is sealed.");
        assertThat(narrative.matchedPositions()).hasSize(4);
    }

    @Test
    void guardTrips_whenEvaluatedPrizeDisagreesWithStoredPrize_returnsNull() {
        // A grid that evaluates to 20 coins, but the ticket's stored prize is 2 — a data-integrity
        // anomaly. The narrator must suppress the narrative (payout path is untouched either way).
        Grid grid = grid(GridSize.FOUR, new String[][] {
                {"1", "2", "3", "4"},
                {"1", "2", "3", "5"},
                {"6", "7", "8", "9"},
                {"10", "11", "12", "13"},
        });

        OutcomeNarrative narrative = narrator.narrate(
                GridCodec.toDto(grid), MechanicType.CELESTIAL_FORTUNE, new BigDecimal("2"), UUID.randomUUID());

        assertThat(narrative).isNull();
    }

    @Test
    void nullInputs_yieldNullNarrative() {
        Grid grid = grid(GridSize.FOUR, new String[][] {
                {"1", "2", "3", "4"},
                {"5", "6", "7", "8"},
                {"9", "10", "11", "12"},
                {"13", "14", "15", "16"},
        });
        assertThat(narrator.narrate(null, MechanicType.CELESTIAL_FORTUNE, BigDecimal.ZERO, UUID.randomUUID())).isNull();
        assertThat(narrator.narrate(GridCodec.toDto(grid), null, BigDecimal.ZERO, UUID.randomUUID())).isNull();
        assertThat(narrator.narrate(GridCodec.toDto(grid), MechanicType.CELESTIAL_FORTUNE, null, UUID.randomUUID()))
                .isNull();
    }

    private static Grid grid(GridSize size, String[][] symbols) {
        int dimension = size.dimension();
        Cell[][] cells = new Cell[dimension][dimension];
        for (int row = 0; row < dimension; row++) {
            for (int col = 0; col < dimension; col++) {
                cells[row][col] = new Cell(new Position(row, col), symbols[row][col], 0.0);
            }
        }
        return new Grid(size, cells);
    }
}
