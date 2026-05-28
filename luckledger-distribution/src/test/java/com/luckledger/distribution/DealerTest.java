package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealerTest {

    private static Dealer dealer(int booksPerCycle) {
        return new Dealer(UUID.randomUUID(), "Lucky's", DealerTier.TIER_1, 0, booksPerCycle, 0);
    }

    @Test
    void exposesItsState() {
        UUID id = UUID.randomUUID();
        Dealer dealer = new Dealer(id, "Corner Store", DealerTier.TIER_2, 7, 5, 20);

        assertThat(dealer.dealerId()).isEqualTo(id);
        assertThat(dealer.name()).isEqualTo("Corner Store");
        assertThat(dealer.tier()).isEqualTo(DealerTier.TIER_2);
        assertThat(dealer.rankScore()).isEqualTo(7);
        assertThat(dealer.booksPerCycle()).isEqualTo(5);
        assertThat(dealer.booksDepleted()).isEqualTo(20);
        assertThat(dealer.activeBooks()).isEmpty();
    }

    @Test
    void allocationQuartileFollowsTier() {
        assertThat(dealer(1).getAllocationQuartile()).isEqualTo(AllocationQuartile.LOWER);
    }

    @Test
    void acceptsBooksUpToTheCapThenRefuses() {
        Dealer dealer = dealer(2);

        assertThat(dealer.canAcceptBooks()).isTrue();
        dealer.addBook(Cards.book(10));
        dealer.addBook(Cards.book(2));
        assertThat(dealer.canAcceptBooks()).isFalse();
        assertThat(dealer.activeBooks()).hasSize(2);
        assertThatThrownBy(() -> dealer.addBook(Cards.book(1)))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void onBookDepletedIncrementsCountAndFreesCapacity() {
        Dealer dealer = dealer(1);
        TicketBook book = Cards.book(10);
        dealer.addBook(book);

        dealer.onBookDepleted(book);

        assertThat(dealer.booksDepleted()).isEqualTo(1);
        assertThat(dealer.activeBooks()).isEmpty();
        assertThat(dealer.canAcceptBooks()).isTrue();
    }

    @Test
    void activeBooksViewIsUnmodifiable() {
        Dealer dealer = dealer(3);
        dealer.addBook(Cards.book(1));

        assertThatThrownBy(() -> dealer.activeBooks().add(Cards.book(2)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void setTierUpdatesTheTier() {
        Dealer dealer = dealer(1);

        dealer.setTier(DealerTier.TIER_3);

        assertThat(dealer.tier()).isEqualTo(DealerTier.TIER_3);
        assertThat(dealer.getAllocationQuartile()).isEqualTo(AllocationQuartile.UPPER);
    }

    @Test
    void invalidConstructionIsRejected() {
        UUID id = UUID.randomUUID();
        assertThatThrownBy(() -> new Dealer(id, " ", DealerTier.TIER_1, 0, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dealer(id, "x", DealerTier.TIER_1, -1, 1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dealer(id, "x", DealerTier.TIER_1, 0, 0, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dealer(id, "x", DealerTier.TIER_1, 0, 1, -1))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new Dealer(null, "x", DealerTier.TIER_1, 0, 1, 0))
                .isInstanceOf(NullPointerException.class);
    }
}
