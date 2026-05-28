package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.mechanic.Cell;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.mechanic.Position;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TicketLayoutTest {

    private static final UUID ID = UUID.randomUUID();

    private static Grid grid() {
        int dim = GridSize.THREE.dimension();
        Cell[][] cells = new Cell[dim][dim];
        for (int r = 0; r < dim; r++) {
            for (int c = 0; c < dim; c++) {
                cells[r][c] = new Cell(new Position(r, c), "X", 0.0);
            }
        }
        return new Grid(GridSize.THREE, cells);
    }

    @Test
    void holdsItsComponents() {
        Grid grid = grid();
        TicketLayout layout = new TicketLayout(ID, grid, MechanicType.DEMON_SEAL, new BigDecimal("25"));

        assertThat(layout.outcomeId()).isEqualTo(ID);
        assertThat(layout.grid()).isSameAs(grid);
        assertThat(layout.mechanicType()).isEqualTo(MechanicType.DEMON_SEAL);
        assertThat(layout.prizeAmount()).isEqualByComparingTo("25");
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> new TicketLayout(null, grid(), MechanicType.DEMON_SEAL, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketLayout(ID, null, MechanicType.DEMON_SEAL, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketLayout(ID, grid(), null, BigDecimal.ZERO))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new TicketLayout(ID, grid(), MechanicType.DEMON_SEAL, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void negativePrizeIsRejected() {
        assertThatThrownBy(() -> new TicketLayout(ID, grid(), MechanicType.DEMON_SEAL, new BigDecimal("-1")))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
