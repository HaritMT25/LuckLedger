package com.luckledger.domain.generation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.generation.verification.CheckResult;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class GenerationResultTest {

    private static final VerificationReport REPORT =
            VerificationReport.from(List.of(CheckResult.passed("Tier Counts")));
    private static final NearMissReport NEAR_MISS = new NearMissReport(0, 0, 0.0, Map.of());

    @Test
    void holdsItsComponents() {
        GenerationResult result = new GenerationResult(List.of(), REPORT, NEAR_MISS, 1234L);

        assertThat(result.verificationReport()).isSameAs(REPORT);
        assertThat(result.nearMissReport()).isSameAs(NEAR_MISS);
        assertThat(result.generationTimeMs()).isEqualTo(1234L);
    }

    @Test
    void ticketsAreAnUnmodifiableCopy() {
        List<TicketCard> source = new ArrayList<>();
        GenerationResult result = new GenerationResult(source, REPORT, NEAR_MISS, 0L);

        assertThatThrownBy(() -> result.tickets().add(null))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void negativeGenerationTimeIsRejected() {
        assertThatThrownBy(() -> new GenerationResult(List.of(), REPORT, NEAR_MISS, -1L))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullComponentsAreRejected() {
        assertThatThrownBy(() -> new GenerationResult(null, REPORT, NEAR_MISS, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GenerationResult(List.of(), null, NEAR_MISS, 0L))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new GenerationResult(List.of(), REPORT, null, 0L))
                .isInstanceOf(NullPointerException.class);
    }
}
