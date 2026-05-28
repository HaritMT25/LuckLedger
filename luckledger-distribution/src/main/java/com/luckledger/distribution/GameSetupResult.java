package com.luckledger.distribution;

import com.luckledger.domain.generation.GenerationResult;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The full result of setting up a game: the generated batch, how it was partitioned into books, the
 * dealers it was allocated to, and the allocation map itself. This is the hand-off from orchestration
 * (Subsystem 12) to the running game.
 *
 * <p>Lives in {@code luckledger-distribution} because it aggregates distribution types ({@link Dealer},
 * {@link PartitionResult}, {@link TicketBook}) that the pure-domain module cannot reference.
 *
 * @param dealers the dealers, with books assigned; non-null, no nulls (copied)
 * @param generationResult the generated, verified batch; never {@code null}
 * @param partitionResult the books and their value spread; never {@code null}
 * @param allocationMap each dealer to the books it received; non-null, deeply copied immutable
 */
public record GameSetupResult(
        List<Dealer> dealers,
        GenerationResult generationResult,
        PartitionResult partitionResult,
        Map<Dealer, List<TicketBook>> allocationMap) {

    public GameSetupResult {
        Objects.requireNonNull(dealers, "dealers must not be null");
        Objects.requireNonNull(generationResult, "generationResult must not be null");
        Objects.requireNonNull(partitionResult, "partitionResult must not be null");
        Objects.requireNonNull(allocationMap, "allocationMap must not be null");
        dealers.forEach(d -> Objects.requireNonNull(d, "dealers must not contain null elements"));
        dealers = List.copyOf(dealers);

        Map<Dealer, List<TicketBook>> copy = new LinkedHashMap<>();
        allocationMap.forEach((dealer, books) -> {
            Objects.requireNonNull(dealer, "allocationMap must not contain null keys");
            Objects.requireNonNull(books, "allocationMap must not contain null values");
            copy.put(dealer, List.copyOf(books));
        });
        allocationMap = Collections.unmodifiableMap(copy);
    }
}
