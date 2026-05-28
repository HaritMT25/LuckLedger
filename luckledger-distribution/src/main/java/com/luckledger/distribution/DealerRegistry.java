package com.luckledger.distribution;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;

/**
 * In-memory store of the game's NPC dealers. Created once at system setup via
 * {@link #initializeDealers(int)} and queried thereafter by id or tier. The shared throughput cap
 * ({@code booksPerCycle}, §3.6) is fixed for the registry and stamped onto every dealer it creates.
 */
public final class DealerRegistry {

    private final int booksPerCycle;
    private final Map<UUID, Dealer> dealers = new LinkedHashMap<>();

    /**
     * @param booksPerCycle the per-cycle book cap applied to every dealer; {@code >= 1}
     */
    public DealerRegistry(int booksPerCycle) {
        if (booksPerCycle < 1) {
            throw new IllegalArgumentException("booksPerCycle must be >= 1, was " + booksPerCycle);
        }
        this.booksPerCycle = booksPerCycle;
    }

    /** All registered dealers, in registration order (unmodifiable). */
    public List<Dealer> getAllDealers() {
        return List.copyOf(dealers.values());
    }

    /**
     * @param dealerId the id to look up; never {@code null}
     * @return the dealer
     * @throws NoSuchElementException if no dealer is registered with that id
     */
    public Dealer getDealer(UUID dealerId) {
        Objects.requireNonNull(dealerId, "dealerId must not be null");
        Dealer dealer = dealers.get(dealerId);
        if (dealer == null) {
            throw new NoSuchElementException("no dealer registered with id " + dealerId);
        }
        return dealer;
    }

    /** All dealers currently at the given tier (unmodifiable; may be empty). */
    public List<Dealer> getDealersByTier(DealerTier tier) {
        Objects.requireNonNull(tier, "tier must not be null");
        List<Dealer> matches = new ArrayList<>();
        for (Dealer dealer : dealers.values()) {
            if (dealer.tier() == tier) {
                matches.add(dealer);
            }
        }
        return List.copyOf(matches);
    }

    /**
     * Creates and registers {@code count} fresh NPC dealers — all at {@link DealerTier#TIER_1} with
     * no depleted books and the registry's {@code booksPerCycle}.
     *
     * @param count how many dealers to create; {@code >= 1}
     * @return the newly created dealers
     */
    public List<Dealer> initializeDealers(int count) {
        if (count < 1) {
            throw new IllegalArgumentException("count must be >= 1, was " + count);
        }
        List<Dealer> created = new ArrayList<>(count);
        for (int i = 1; i <= count; i++) {
            Dealer dealer = new Dealer(UUID.randomUUID(), "Dealer " + i, DealerTier.TIER_1, 0, booksPerCycle, 0);
            dealers.put(dealer.dealerId(), dealer);
            created.add(dealer);
        }
        return created;
    }
}
