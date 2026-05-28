package com.luckledger.domain.generation;

import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PrizeTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Layer 2 of the generation pipeline: expands a {@link PoolContract} into the flat list of
 * {@link TicketOutcome}s the pool will sell — one per ticket, fully determined before any ticket
 * exists (the "pre-generated pools" invariant).
 *
 * <p>For each winning tier it emits exactly {@code tier.count()} outcomes at {@code tier.value()},
 * then {@code pool.getLoserCount()} losing outcomes at {@code pool.minPayout()} (zero for pure-loser
 * pools). The result therefore has exactly {@code pool.totalTickets()} entries and is
 * <strong>unshuffled</strong> — winners are grouped by tier; {@link ShuffleService} randomizes the
 * arrangement afterwards.
 *
 * <p>The expansion is deterministic in structure (same contract → same per-tier counts); only the
 * fresh {@link UUID} per outcome varies. This class is stateless and holds no dependencies.
 */
public final class OutcomeGenerator {

    /**
     * Expands the contract into its full, unshuffled list of outcomes.
     *
     * @param pool the pool specification; never {@code null}
     * @return a mutable list of exactly {@code pool.totalTickets()} outcomes, winners grouped by tier
     *     (value descending) followed by losers
     */
    public List<TicketOutcome> generate(PoolContract pool) {
        Objects.requireNonNull(pool, "pool must not be null");

        List<TicketOutcome> outcomes = new ArrayList<>(pool.totalTickets());
        for (PrizeTier tier : pool.prizeTiers()) {
            for (int i = 0; i < tier.count(); i++) {
                outcomes.add(new TicketOutcome(UUID.randomUUID(), tier.value()));
            }
        }
        int loserCount = pool.getLoserCount();
        for (int i = 0; i < loserCount; i++) {
            outcomes.add(new TicketOutcome(UUID.randomUUID(), pool.minPayout()));
        }
        return outcomes;
    }
}
