package com.luckledger.domain.generation;

import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.MechanicType;
import java.util.Objects;
import java.util.UUID;

/**
 * Layer 3 of the generation pipeline: a {@link TicketOutcome} after a mechanic has reverse-engineered
 * a {@link Grid} that encodes its prize, but before any theme skins it. It ties the outcome's
 * {@code outcomeId} to the concrete grid and the {@link MechanicType} that built (and will evaluate)
 * it.
 *
 * @param outcomeId the originating outcome's id; never {@code null}
 * @param grid the mechanic grid encoding the outcome; never {@code null}
 * @param mechanicType the mechanic that produced and reads this grid; never {@code null}
 */
public record TicketLayout(UUID outcomeId, Grid grid, MechanicType mechanicType) {

    public TicketLayout {
        Objects.requireNonNull(outcomeId, "outcomeId must not be null");
        Objects.requireNonNull(grid, "grid must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
    }
}
