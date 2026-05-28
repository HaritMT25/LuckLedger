package com.luckledger.distribution;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Assigns partitioned books to dealers by rank (§3.6). Books are sorted by value ascending and split
 * into three bands; the lowest-value band goes to {@link DealerTier#TIER_1} dealers
 * ({@link AllocationQuartile#LOWER}), the middle to {@code TIER_2}, the highest to {@code TIER_3}.
 * Within a band, the receiving dealer is chosen at random — no same-tier dealer holds a persistent
 * advantage. Every dealer's cap ({@code booksPerCycle}) is equal, so rank governs quality, not volume.
 *
 * <p>Books that find no eligible dealer with capacity are left unallocated (overflow for the next
 * cycle); they are simply absent from the returned map.
 */
public final class DealerAllocator {

    private final DealerTierResolver tierResolver;
    private final RandomGenerator random;

    public DealerAllocator(DealerTierResolver tierResolver) {
        this(tierResolver, new SecureRandom());
    }

    public DealerAllocator(DealerTierResolver tierResolver, RandomGenerator random) {
        this.tierResolver = Objects.requireNonNull(tierResolver, "tierResolver must not be null");
        this.random = Objects.requireNonNull(random, "random must not be null");
    }

    /**
     * Allocates books to dealers for one cycle.
     *
     * @param books the books to distribute; never {@code null}
     * @param dealers the dealers to distribute to; never {@code null}
     * @return each dealer mapped to the books it received this cycle (possibly empty); overflow books
     *     are absent from every list
     */
    public Map<Dealer, List<TicketBook>> allocate(List<TicketBook> books, List<Dealer> dealers) {
        Objects.requireNonNull(books, "books must not be null");
        Objects.requireNonNull(dealers, "dealers must not be null");

        tierResolver.resolveAll(dealers);

        Map<Dealer, List<TicketBook>> allocation = new LinkedHashMap<>();
        dealers.forEach(dealer -> allocation.put(dealer, new ArrayList<>()));

        List<TicketBook> sorted = new ArrayList<>(books);
        sorted.sort(Comparator.comparing(TicketBook::getBookValue));

        int n = sorted.size();
        int lowerEnd = n / 3;
        int middleEnd = 2 * n / 3;
        assignBand(sorted.subList(0, lowerEnd), AllocationQuartile.LOWER, dealers, allocation);
        assignBand(sorted.subList(lowerEnd, middleEnd), AllocationQuartile.MIDDLE, dealers, allocation);
        assignBand(sorted.subList(middleEnd, n), AllocationQuartile.UPPER, dealers, allocation);
        return allocation;
    }

    private void assignBand(
            List<TicketBook> band,
            AllocationQuartile quartile,
            List<Dealer> dealers,
            Map<Dealer, List<TicketBook>> allocation) {
        List<Dealer> eligible = new ArrayList<>();
        for (Dealer dealer : dealers) {
            if (dealer.getAllocationQuartile() == quartile) {
                eligible.add(dealer);
            }
        }
        for (TicketBook book : band) {
            List<Dealer> withCapacity = new ArrayList<>();
            for (Dealer dealer : eligible) {
                if (dealer.canAcceptBooks()) {
                    withCapacity.add(dealer);
                }
            }
            if (withCapacity.isEmpty()) {
                continue; // overflow — no eligible dealer can take this book this cycle
            }
            Dealer chosen = withCapacity.get(random.nextInt(withCapacity.size()));
            chosen.addBook(book);
            allocation.get(chosen).add(book);
        }
    }
}
