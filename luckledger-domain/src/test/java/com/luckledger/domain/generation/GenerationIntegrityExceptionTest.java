package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;

import com.luckledger.domain.generation.verification.CheckResult;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.util.List;
import org.junit.jupiter.api.Test;

class GenerationIntegrityExceptionTest {

    @Test
    void isUnchecked() {
        assertThat(new GenerationIntegrityException("boom")).isInstanceOf(RuntimeException.class);
    }

    @Test
    void carriesMessageAndNoReportByDefault() {
        GenerationIntegrityException ex = new GenerationIntegrityException("verification failed");

        assertThat(ex.getMessage()).isEqualTo("verification failed");
        assertThat(ex.getReport()).isNull();
    }

    @Test
    void carriesTheFailingReport() {
        VerificationReport report =
                VerificationReport.from(List.of(CheckResult.failed("Payout Ratio", "off by 3%")));
        GenerationIntegrityException ex = new GenerationIntegrityException("aborted", report);

        assertThat(ex.getReport()).isSameAs(report);
        assertThat(ex.getReport().passed()).isFalse();
    }
}
