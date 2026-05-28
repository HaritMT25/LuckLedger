package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MechanicTypeTest {

    @Test
    void shouldDeclareAllMechanicTypesInExpectedOrder() {
        assertThat(MechanicType.values())
                .containsExactly(
                        MechanicType.CELESTIAL_FORTUNE,
                        MechanicType.DEMON_SEAL,
                        MechanicType.MATCH_3,
                        MechanicType.NUMBER_MATCH,
                        MechanicType.KEY_SYMBOL,
                        MechanicType.BINGO,
                        MechanicType.CROSSWORD,
                        MechanicType.TIC_TAC_TOE);
    }

    @Test
    void shouldDeclareExactlyEightMechanicTypes() {
        assertThat(MechanicType.values()).hasSize(8);
    }

    @Test
    void shouldIncludeTheTwoBuiltMechanics() {
        assertThat(MechanicType.values())
                .contains(MechanicType.CELESTIAL_FORTUNE, MechanicType.DEMON_SEAL);
    }
}
