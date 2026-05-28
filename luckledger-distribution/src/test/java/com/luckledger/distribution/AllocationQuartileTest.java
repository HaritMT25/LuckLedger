package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class AllocationQuartileTest {

    @Test
    void mapsEachTierToItsBand() {
        assertThat(AllocationQuartile.fromTier(DealerTier.TIER_1)).isEqualTo(AllocationQuartile.LOWER);
        assertThat(AllocationQuartile.fromTier(DealerTier.TIER_2)).isEqualTo(AllocationQuartile.MIDDLE);
        assertThat(AllocationQuartile.fromTier(DealerTier.TIER_3)).isEqualTo(AllocationQuartile.UPPER);
    }

    @Test
    void nullTierIsRejected() {
        assertThatThrownBy(() -> AllocationQuartile.fromTier(null))
                .isInstanceOf(NullPointerException.class);
    }
}
