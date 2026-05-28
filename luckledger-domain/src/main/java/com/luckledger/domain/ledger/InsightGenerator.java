package com.luckledger.domain.ledger;

import java.util.Optional;

/**
 * Strategy that derives a single educational {@link Insight} from a player's ledger state.
 *
 * <p>Each implementation encapsulates exactly one educational observation (loss rate, loss
 * chasing, book variance, ...). Implementations are stateless and pure: they receive a
 * {@link LedgerSnapshot} and return an insight when their trigger condition is met, or
 * {@link Optional#empty()} otherwise. They do not track whether an insight has been shown
 * before — de-duplication across sessions is a frontend concern, not a domain concern.
 *
 * <p>New insight types plug in by implementing this interface without modifying the ledger
 * (Open/Closed). The ledger service runs every registered generator against a snapshot and
 * collects the non-empty results.
 */
@FunctionalInterface
public interface InsightGenerator {

    /**
     * Examines the supplied snapshot and produces an insight if this generator's trigger
     * condition is met.
     *
     * @param snapshot read-only aggregate of the player's ledger state; never {@code null}
     * @return the insight when the trigger condition holds, otherwise {@link Optional#empty()}
     */
    Optional<Insight> evaluate(LedgerSnapshot snapshot);
}
