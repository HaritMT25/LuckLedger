package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TransactionTest {

    private static final UUID TX_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID DEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID BOOK_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");
    private static final UUID TICKET_ID = UUID.fromString("00000000-0000-0000-0000-000000000005");
    private static final Instant TIMESTAMP = Instant.parse("2026-05-28T10:15:30Z");

    @Test
    void shouldConstructSpendTransaction_andExposeAccessors() {
        Transaction tx = new Transaction(
                TX_ID,
                PLAYER_ID,
                TransactionType.SPEND,
                new BigDecimal("5.00"),
                DEALER_ID,
                BOOK_ID,
                TICKET_ID,
                TIMESTAMP);

        assertThat(tx.transactionId()).isEqualTo(TX_ID);
        assertThat(tx.playerId()).isEqualTo(PLAYER_ID);
        assertThat(tx.type()).isEqualTo(TransactionType.SPEND);
        assertThat(tx.amount()).isEqualByComparingTo("5.00");
        assertThat(tx.dealerId()).isEqualTo(DEALER_ID);
        assertThat(tx.bookId()).isEqualTo(BOOK_ID);
        assertThat(tx.ticketId()).isEqualTo(TICKET_ID);
        assertThat(tx.timestamp()).isEqualTo(TIMESTAMP);
    }

    @Test
    void shouldAllowNullDealerBookAndTicketIds_forBorrowTransaction() {
        Transaction tx = new Transaction(
                TX_ID,
                PLAYER_ID,
                TransactionType.BORROW,
                new BigDecimal("1000.00"),
                null,
                null,
                null,
                TIMESTAMP);

        assertThat(tx.dealerId()).isNull();
        assertThat(tx.bookId()).isNull();
        assertThat(tx.ticketId()).isNull();
        assertThat(tx.amount()).isEqualByComparingTo("1000.00");
    }

    @Test
    void shouldRejectNullTransactionId() {
        assertThatThrownBy(() -> new Transaction(
                null, PLAYER_ID, TransactionType.WIN, BigDecimal.ONE,
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("transactionId");
    }

    @Test
    void shouldRejectNullPlayerId() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, null, TransactionType.WIN, BigDecimal.ONE,
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("playerId");
    }

    @Test
    void shouldRejectNullType() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, PLAYER_ID, null, BigDecimal.ONE,
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("type");
    }

    @Test
    void shouldRejectNullAmount() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, PLAYER_ID, TransactionType.WIN, null,
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectZeroAmount() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, PLAYER_ID, TransactionType.WIN, BigDecimal.ZERO,
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNegativeAmount() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, PLAYER_ID, TransactionType.WIN, new BigDecimal("-0.01"),
                DEALER_ID, BOOK_ID, TICKET_ID, TIMESTAMP))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNullTimestamp() {
        assertThatThrownBy(() -> new Transaction(
                TX_ID, PLAYER_ID, TransactionType.WIN, BigDecimal.ONE,
                DEALER_ID, BOOK_ID, TICKET_ID, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("timestamp");
    }
}
