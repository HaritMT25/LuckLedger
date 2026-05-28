package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealerTierResolverTest {

    private final DealerTierResolver resolver = new DealerTierResolver();

    private static Dealer withDepleted(int booksDepleted) {
        return new Dealer(UUID.randomUUID(), "d", DealerTier.TIER_1, 0, 5, booksDepleted);
    }

    @Test
    void mapsDepletedCountToTierAtTheBoundaries() {
        assertThat(resolver.resolve(withDepleted(0))).isEqualTo(DealerTier.TIER_1);
        assertThat(resolver.resolve(withDepleted(9))).isEqualTo(DealerTier.TIER_1);
        assertThat(resolver.resolve(withDepleted(10))).isEqualTo(DealerTier.TIER_2);
        assertThat(resolver.resolve(withDepleted(49))).isEqualTo(DealerTier.TIER_2);
        assertThat(resolver.resolve(withDepleted(50))).isEqualTo(DealerTier.TIER_3);
        assertThat(resolver.resolve(withDepleted(500))).isEqualTo(DealerTier.TIER_3);
    }

    @Test
    void resolveAllUpdatesEveryDealerInPlace() {
        Dealer a = withDepleted(5);   // -> TIER_1
        Dealer b = withDepleted(30);  // -> TIER_2
        Dealer c = withDepleted(80);  // -> TIER_3
        // start them all mislabeled
        a.setTier(DealerTier.TIER_3);
        b.setTier(DealerTier.TIER_3);
        c.setTier(DealerTier.TIER_1);

        resolver.resolveAll(List.of(a, b, c));

        assertThat(a.tier()).isEqualTo(DealerTier.TIER_1);
        assertThat(b.tier()).isEqualTo(DealerTier.TIER_2);
        assertThat(c.tier()).isEqualTo(DealerTier.TIER_3);
    }
}
