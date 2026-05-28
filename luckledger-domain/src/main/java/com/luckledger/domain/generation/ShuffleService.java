package com.luckledger.domain.generation;

import java.security.SecureRandom;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Random;
import java.util.random.RandomGenerator;

/**
 * Layer 2 shuffle: randomizes the <em>arrangement</em> of a pre-generated outcome list with an
 * in-place Fisher-Yates pass, never the outcomes themselves (the payout ratio is sacred). This is a
 * separate responsibility from {@link OutcomeGenerator}: generation is deterministic, shuffling is
 * stochastic, and keeping them apart keeps each trivially testable (SRP).
 *
 * <p>Production callers use {@link #shuffle(List)} (a {@link SecureRandom} source); tests use
 * {@link #shuffle(List, long)} for a reproducible ordering.
 */
public final class ShuffleService {

    /**
     * Shuffles the list in place using a cryptographically strong, non-reproducible source.
     *
     * @param outcomes the list to reorder; never {@code null}, mutated in place
     * @return the same list instance, reordered
     */
    public List<TicketOutcome> shuffle(List<TicketOutcome> outcomes) {
        return shuffle(outcomes, new SecureRandom());
    }

    /**
     * Shuffles the list in place using a seeded source, for reproducible test runs.
     *
     * @param outcomes the list to reorder; never {@code null}, mutated in place
     * @param seed the seed fixing the permutation
     * @return the same list instance, reordered
     */
    public List<TicketOutcome> shuffle(List<TicketOutcome> outcomes, long seed) {
        return shuffle(outcomes, new Random(seed));
    }

    private List<TicketOutcome> shuffle(List<TicketOutcome> outcomes, RandomGenerator random) {
        Objects.requireNonNull(outcomes, "outcomes must not be null");
        for (int i = outcomes.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Collections.swap(outcomes, i, j);
        }
        return outcomes;
    }
}
