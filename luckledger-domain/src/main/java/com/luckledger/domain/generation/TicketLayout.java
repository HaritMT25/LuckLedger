package com.luckledger.domain.generation;

import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.MechanicType;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

/**
 * Layer 3 of the generation pipeline: a {@link TicketOutcome} after a mechanic has reverse-engineered
 * a {@link Grid} that encodes its prize, but before any theme skins it. It ties the outcome's
 * {@code outcomeId} to the concrete grid, the {@link MechanicType} that built (and will evaluate) it,
 * and the predetermined {@code prizeAmount} the grid encodes.
 *
 * <p>The prize is carried denormalized here (it is also recoverable by evaluating the grid) so that
 * downstream aggregation — e.g. a book's total value — is O(1) per ticket rather than a re-evaluation.
 *
 * @param outcomeId the originating outcome's id; never {@code null}
 * @param grid the mechanic grid encoding the outcome; never {@code null}
 * @param mechanicType the mechanic that produced and reads this grid; never {@code null}
 * @param prizeAmount the predetermined prize this layout encodes; never {@code null}, {@code >= 0}
 */
public record TicketLayout(UUID outcomeId, Grid grid, MechanicType mechanicType, BigDecimal prizeAmount) {

    public TicketLayout {
        Objects.requireNonNull(outcomeId, "outcomeId must not be null");
        Objects.requireNonNull(grid, "grid must not be null");
        Objects.requireNonNull(mechanicType, "mechanicType must not be null");
        Objects.requireNonNull(prizeAmount, "prizeAmount must not be null");
        if (prizeAmount.signum() < 0) {
            throw new IllegalArgumentException(
                    "prizeAmount must be >= 0, was " + prizeAmount.toPlainString());
        }
    }
}
