package com.luckledger.domain.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CheckResultTest {

    @Test
    void holdsNamePassedAndMessage() {
        CheckResult result = new CheckResult("Payout Ratio", true, "within tolerance");

        assertThat(result.name()).isEqualTo("Payout Ratio");
        assertThat(result.passed()).isTrue();
        assertThat(result.message()).isEqualTo("within tolerance");
    }

    @Test
    void passedFactoryHasEmptyMessage() {
        CheckResult result = CheckResult.passed("Tier Counts");

        assertThat(result.passed()).isTrue();
        assertThat(result.name()).isEqualTo("Tier Counts");
        assertThat(result.message()).isEmpty();
    }

    @Test
    void failedFactoryCarriesReason() {
        CheckResult result = CheckResult.failed("No False Positives", "ticket 42 won unexpectedly");

        assertThat(result.passed()).isFalse();
        assertThat(result.message()).isEqualTo("ticket 42 won unexpectedly");
    }

    @Test
    void nullNameIsRejected() {
        assertThatThrownBy(() -> new CheckResult(null, true, "")).isInstanceOf(NullPointerException.class);
    }

    @Test
    void blankNameIsRejected() {
        assertThatThrownBy(() -> new CheckResult("  ", true, ""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullMessageIsRejected() {
        assertThatThrownBy(() -> new CheckResult("x", true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
