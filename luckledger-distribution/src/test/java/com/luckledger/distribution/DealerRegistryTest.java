package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DealerRegistryTest {

    @Test
    void initializesNpcDealersAtTierOneWithTheConfiguredCap() {
        DealerRegistry registry = new DealerRegistry(8);

        List<Dealer> created = registry.initializeDealers(5);

        assertThat(created).hasSize(5);
        assertThat(registry.getAllDealers()).hasSize(5);
        assertThat(created).allSatisfy(d -> {
            assertThat(d.tier()).isEqualTo(DealerTier.TIER_1);
            assertThat(d.booksPerCycle()).isEqualTo(8);
            assertThat(d.booksDepleted()).isZero();
            assertThat(d.name()).isNotBlank();
        });
    }

    @Test
    void getDealerLooksUpById() {
        DealerRegistry registry = new DealerRegistry(3);
        Dealer dealer = registry.initializeDealers(1).get(0);

        assertThat(registry.getDealer(dealer.dealerId())).isSameAs(dealer);
        assertThatThrownBy(() -> registry.getDealer(UUID.randomUUID()))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    void getDealersByTierFiltersOnCurrentTier() {
        DealerRegistry registry = new DealerRegistry(3);
        List<Dealer> dealers = registry.initializeDealers(3);
        dealers.get(0).setTier(DealerTier.TIER_3);

        assertThat(registry.getDealersByTier(DealerTier.TIER_1)).hasSize(2);
        assertThat(registry.getDealersByTier(DealerTier.TIER_3)).hasSize(1);
        assertThat(registry.getDealersByTier(DealerTier.TIER_2)).isEmpty();
    }

    @Test
    void getAllDealersIsUnmodifiable() {
        DealerRegistry registry = new DealerRegistry(3);
        registry.initializeDealers(1);

        assertThatThrownBy(() -> registry.getAllDealers().clear())
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void invalidConfigurationOrCountIsRejected() {
        assertThatThrownBy(() -> new DealerRegistry(0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new DealerRegistry(3).initializeDealers(0))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
