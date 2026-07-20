package com.luckledger.domain.orchestration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.MetadataVisibility;
import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PrizeTier;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class GameConfigTest {

    private static PoolContract pool() {
        return PoolContract.builder()
                .totalTickets(100)
                .ticketPrice(new BigDecimal("5"))
                .payoutRatio(new BigDecimal("0.65"))
                .addPrizeTier(new PrizeTier(new BigDecimal("10"), 10, "Mid"))
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED)
                .build();
    }

    @Test
    void holdsItsFields() {
        GameConfig config = new GameConfig(pool(), MechanicType.DEMON_SEAL, "demon", 10, 4);

        assertThat(config.mechanicType()).isEqualTo(MechanicType.DEMON_SEAL);
        assertThat(config.themeId()).isEqualTo("demon");
        assertThat(config.bookCount()).isEqualTo(10);
        assertThat(config.dealerCount()).isEqualTo(4);
        assertThat(config.poolContract()).isNotNull();
    }

    @Test
    void blankThemeIsRejected() {
        assertThatThrownBy(() -> new GameConfig(pool(), MechanicType.DEMON_SEAL, " ", 10, 4))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nonPositiveCountsAreRejected() {
        assertThatThrownBy(() -> new GameConfig(pool(), MechanicType.DEMON_SEAL, "demon", 0, 4))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new GameConfig(pool(), MechanicType.DEMON_SEAL, "demon", 10, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void legacyConstructorDefaultsVisibilityToPartial() {
        GameConfig config = new GameConfig(pool(), MechanicType.DEMON_SEAL, "demon", 10, 4);

        assertThat(config.bookMetadataVisibility()).isEqualTo(MetadataVisibility.PARTIAL);
        assertThat(config.nearMissMode()).isEqualTo(NearMissMode.CLEAN);
    }

    @Test
    void nearMissConstructorDefaultsVisibilityToPartial() {
        GameConfig config = new GameConfig(
                pool(), MechanicType.DEMON_SEAL, "demon", 10, 4, NearMissMode.REALISTIC);

        assertThat(config.nearMissMode()).isEqualTo(NearMissMode.REALISTIC);
        assertThat(config.bookMetadataVisibility()).isEqualTo(MetadataVisibility.PARTIAL);
    }

    @Test
    void carriesAnExplicitVisibility() {
        GameConfig config = new GameConfig(
                pool(), MechanicType.DEMON_SEAL, "demon", 10, 4,
                NearMissMode.REALISTIC, MetadataVisibility.FULL);

        assertThat(config.bookMetadataVisibility()).isEqualTo(MetadataVisibility.FULL);
    }

    @Test
    void nullVisibilityIsRejected() {
        assertThatThrownBy(() -> new GameConfig(
                        pool(), MechanicType.DEMON_SEAL, "demon", 10, 4,
                        NearMissMode.CLEAN, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new GameConfig(null, MechanicType.DEMON_SEAL, "demon", 10, 4))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GameConfig(pool(), null, "demon", 10, 4))
                .isInstanceOf(NullPointerException.class);
    }
}
