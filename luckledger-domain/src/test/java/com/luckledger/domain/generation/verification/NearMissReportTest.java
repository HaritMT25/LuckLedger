package com.luckledger.domain.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;

class NearMissReportTest {

    private static Map<Integer, Integer> distribution() {
        return new HashMap<>(Map.of(1, 45, 2, 120, 3, 8));
    }

    @Test
    void holdsItsFields() {
        NearMissReport report = new NearMissReport(1000, 173, 0.173, distribution());

        assertThat(report.totalLosers()).isEqualTo(1000);
        assertThat(report.nearMissCount()).isEqualTo(173);
        assertThat(report.nearMissRate()).isEqualTo(0.173);
        assertThat(report.distribution()).containsEntry(2, 120);
    }

    @Test
    void distributionIsAnUnmodifiableCopy() {
        Map<Integer, Integer> source = distribution();
        NearMissReport report = new NearMissReport(1000, 173, 0.173, source);

        source.put(9, 999); // mutating the source must not affect the report
        assertThat(report.distribution()).doesNotContainKey(9);
        assertThatThrownBy(() -> report.distribution().put(4, 1))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void zeroLosersIsValid() {
        NearMissReport report = new NearMissReport(0, 0, 0.0, Map.of());

        assertThat(report.totalLosers()).isZero();
    }

    @Test
    void negativeTotalLosersIsRejected() {
        assertThatThrownBy(() -> new NearMissReport(-1, 0, 0.0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nearMissCountExceedingLosersIsRejected() {
        assertThatThrownBy(() -> new NearMissReport(10, 11, 1.0, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rateOutsideUnitIntervalIsRejected() {
        assertThatThrownBy(() -> new NearMissReport(10, 5, 1.5, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new NearMissReport(10, 5, -0.1, Map.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeDistanceIsRejected() {
        assertThatThrownBy(() -> new NearMissReport(10, 1, 0.1, Map.of(-1, 1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void negativeCountIsRejected() {
        Map<Integer, Integer> bad = new HashMap<>();
        bad.put(1, -5);
        assertThatThrownBy(() -> new NearMissReport(10, 1, 0.1, bad))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullDistributionIsRejected() {
        assertThatThrownBy(() -> new NearMissReport(10, 1, 0.1, null))
                .isInstanceOf(NullPointerException.class);
    }
}
