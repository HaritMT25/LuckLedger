package com.luckledger.domain.generation.verification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class VerificationReportTest {

    @Test
    void fromAllPassingChecksPasses() {
        VerificationReport report =
                VerificationReport.from(List.of(CheckResult.passed("A"), CheckResult.passed("B")));

        assertThat(report.passed()).isTrue();
        assertThat(report.allPassed()).isTrue();
        assertThat(report.checks()).hasSize(2);
    }

    @Test
    void fromAnyFailingCheckFails() {
        VerificationReport report =
                VerificationReport.from(List.of(CheckResult.passed("A"), CheckResult.failed("B", "boom")));

        assertThat(report.passed()).isFalse();
    }

    @Test
    void checksAreAnUnmodifiableCopy() {
        List<CheckResult> source = new ArrayList<>(List.of(CheckResult.passed("A")));
        VerificationReport report = VerificationReport.from(source);

        source.add(CheckResult.failed("B", "x"));
        assertThat(report.checks()).hasSize(1);
        assertThatThrownBy(() -> report.checks().add(CheckResult.passed("C")))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void inconsistentPassedFlagIsRejected() {
        // passed=true but a check failed
        assertThatThrownBy(
                        () -> new VerificationReport(true, List.of(CheckResult.failed("A", "boom"))))
                .isInstanceOf(IllegalArgumentException.class);
        // passed=false but all checks passed
        assertThatThrownBy(() -> new VerificationReport(false, List.of(CheckResult.passed("A"))))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void emptyChecksIsRejected() {
        assertThatThrownBy(() -> new VerificationReport(true, List.of()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullChecksIsRejected() {
        assertThatThrownBy(() -> new VerificationReport(true, null))
                .isInstanceOf(NullPointerException.class);
    }
}
