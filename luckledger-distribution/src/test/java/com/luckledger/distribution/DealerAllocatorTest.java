package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealerAllocatorTest {

    private final DealerAllocator allocator =
            new DealerAllocator(new DealerTierResolver(), new Random(42L));

    private static Dealer dealer(int booksDepleted, int cap) {
        // tier is reset by resolveAll from booksDepleted, so booksDepleted picks the tier:
        // 0->TIER_1(LOWER), 20->TIER_2(MIDDLE), 60->TIER_3(UPPER)
        return new Dealer(UUID.randomUUID(), "d", DealerTier.TIER_1, 0, cap, booksDepleted);
    }

    private static double valueOf(TicketBook book) {
        return book.getBookValue().doubleValue();
    }

    @Test
    void lowValueBooksGoToLowTierAndHighValueToHighTier() {
        Dealer low = dealer(0, 3);   // TIER_1 -> LOWER
        Dealer mid = dealer(20, 3);  // TIER_2 -> MIDDLE
        Dealer high = dealer(60, 3); // TIER_3 -> UPPER
        // 9 books valued 1..9 -> LOWER {1,2,3}, MIDDLE {4,5,6}, UPPER {7,8,9}
        List<TicketBook> books = Cards.books(1, 2, 3, 4, 5, 6, 7, 8, 9);

        Map<Dealer, List<TicketBook>> result = allocator.allocate(books, List.of(low, mid, high));

        assertThat(result.get(low)).extracting(DealerAllocatorTest::valueOf).containsExactlyInAnyOrder(1.0, 2.0, 3.0);
        assertThat(result.get(mid)).extracting(DealerAllocatorTest::valueOf).containsExactlyInAnyOrder(4.0, 5.0, 6.0);
        assertThat(result.get(high)).extracting(DealerAllocatorTest::valueOf).containsExactlyInAnyOrder(7.0, 8.0, 9.0);
    }

    @Test
    void capIsRespectedAndExcessOverflows() {
        Dealer low = dealer(0, 3); // TIER_1, cap 3
        // 4 low-value books but only one TIER_1 dealer with cap 3 -> 3 allocated, 1 overflow
        List<TicketBook> books = Cards.books(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12);

        Map<Dealer, List<TicketBook>> result = allocator.allocate(books, List.of(low));

        // n=12 -> LOWER band is first 4 (values 1..4); only TIER_1 dealer exists, cap 3
        assertThat(result.get(low)).hasSize(3);
        int allocated = result.values().stream().mapToInt(List::size).sum();
        assertThat(allocated).isEqualTo(3); // MIDDLE/UPPER have no matching dealers; all overflow
    }

    @Test
    void everyDealerIsAKeyEvenWithNoBooks() {
        Dealer low = dealer(0, 2);
        Dealer high = dealer(60, 2);
        List<TicketBook> books = Cards.books(1, 2, 3); // n=3 -> LOWER{1}, MIDDLE{2}, UPPER{3}

        Map<Dealer, List<TicketBook>> result = allocator.allocate(books, List.of(low, high));

        assertThat(result).containsKeys(low, high);
        assertThat(result.get(low)).extracting(DealerAllocatorTest::valueOf).containsExactly(1.0);
        assertThat(result.get(high)).extracting(DealerAllocatorTest::valueOf).containsExactly(3.0);
        // MIDDLE book (value 2) has no TIER_2 dealer -> overflow
        int allocated = result.values().stream().mapToInt(List::size).sum();
        assertThat(allocated).isEqualTo(2);
    }

    @Test
    void sameSeedProducesSameAllocationAmongSameTierDealers() {
        // two TIER_1 dealers competing for the LOWER band; a fixed seed must reproduce the split
        Map<Dealer, List<TicketBook>> first = allocate2x();
        Map<Dealer, List<TicketBook>> second = allocate2x();

        // compare allocation sizes by dealer name index (fresh dealers each run, same seed/order)
        List<Integer> firstSizes = first.values().stream().map(List::size).toList();
        List<Integer> secondSizes = second.values().stream().map(List::size).toList();
        assertThat(firstSizes).isEqualTo(secondSizes);
    }

    private Map<Dealer, List<TicketBook>> allocate2x() {
        DealerAllocator seeded = new DealerAllocator(new DealerTierResolver(), new Random(7L));
        Dealer a = dealer(0, 5);
        Dealer b = dealer(0, 5);
        return seeded.allocate(Cards.books(1, 2, 3, 4, 5, 6), List.of(a, b));
    }

    @Test
    void resolvesTiersBeforeAllocating() {
        // dealer constructed as TIER_3 but with 0 depleted -> resolveAll resets to TIER_1 (LOWER)
        Dealer d = new Dealer(UUID.randomUUID(), "d", DealerTier.TIER_3, 0, 5, 0);
        List<TicketBook> books = Cards.books(1, 2, 3);

        Map<Dealer, List<TicketBook>> result = allocator.allocate(books, List.of(d));

        assertThat(d.tier()).isEqualTo(DealerTier.TIER_1);
        // only the LOWER band (value 1) matches the now-TIER_1 dealer
        assertThat(result.get(d)).extracting(DealerAllocatorTest::valueOf).containsExactly(1.0);
    }
}
