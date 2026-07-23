package com.luckledger.domain.scratch;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseResultTest {

    private static final UUID T = UUID.randomUUID();
    private static final UUID D = UUID.randomUUID();
    private static final UUID B = UUID.randomUUID();

    @Test
    void holdsItsFields() {
        PurchaseResult result =
                new PurchaseResult(T, TicketStatus.SOLD, new BigDecimal("5"), D, B, "abc123");

        assertThat(result.ticketId()).isEqualTo(T);
        assertThat(result.ticketStatus()).isEqualTo(TicketStatus.SOLD);
        assertThat(result.coinsDeducted()).isEqualByComparingTo("5");
        assertThat(result.dealerId()).isEqualTo(D);
        assertThat(result.bookId()).isEqualTo(B);
        assertThat(result.gridCommitment()).isEqualTo("abc123");
    }

    @Test
    void gridCommitmentIsOptionalForLegacyTickets() {
        PurchaseResult result = new PurchaseResult(T, TicketStatus.SOLD, new BigDecimal("5"), D, B, null);

        assertThat(result.gridCommitment()).isNull();
    }

    @Test
    void nonPositivePriceIsRejected() {
        assertThatThrownBy(() -> new PurchaseResult(T, TicketStatus.SOLD, BigDecimal.ZERO, D, B, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullsAreRejected() {
        assertThatThrownBy(() -> new PurchaseResult(null, TicketStatus.SOLD, BigDecimal.TEN, D, B, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PurchaseResult(T, null, BigDecimal.TEN, D, B, null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new PurchaseResult(T, TicketStatus.SOLD, null, D, B, null))
                .isInstanceOf(NullPointerException.class);
    }
}
