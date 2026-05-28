package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class NearMissResultTest {

    @Test
    void shouldExposeAllComponentsForValidResult() {
        NearMissResult result = new NearMissResult(true, 1, "2-of-3 matching symbols");

        assertThat(result.isNearMiss()).isTrue();
        assertThat(result.distance()).isEqualTo(1);
        assertThat(result.description()).isEqualTo("2-of-3 matching symbols");
    }

    @Test
    void shouldReportNotANearMiss() {
        NearMissResult result = new NearMissResult(false, 3, "no matching symbols");

        assertThat(result.isNearMiss()).isFalse();
    }

    @Test
    void shouldAcceptZeroDistance() {
        NearMissResult result = new NearMissResult(true, 0, "winner-adjacent");

        assertThat(result.distance()).isZero();
    }

    @Test
    void shouldRejectNegativeDistance() {
        assertThatThrownBy(() -> new NearMissResult(true, -1, "invalid"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldRejectNullDescription() {
        assertThatThrownBy(() -> new NearMissResult(false, 2, null))
                .isInstanceOf(NullPointerException.class);
    }
}
