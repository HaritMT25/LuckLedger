package com.luckledger.domain.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LedgerSnapshotTest {

    private static final UUID PLAYER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID DEALER_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    private static final UUID BOOK_ID = UUID.fromString("00000000-0000-0000-0000-000000000003");
    private static final UUID TX_ID = UUID.fromString("00000000-0000-0000-0000-000000000004");

    private static DealerStats dealerStats() {
        return new DealerStats(
                DEALER_ID,
                "Joe's Corner Store",
                12,
                new BigDecimal("4800.00"),
                new BigDecimal("3456.00"),
                new BigDecimal("0.72"));
    }

    private static BookStats bookStats() {
        return new BookStats(
                BOOK_ID,
                5,
                new BigDecimal("25.00"),
                new BigDecimal("20.00"),
                new BigDecimal("0.80"));
    }

    private static Transaction borrowTransaction() {
        return new Transaction(
                TX_ID,
                PLAYER_ID,
                TransactionType.BORROW,
                new BigDecimal("100.00"),
                null,
                null,
                null,
                Instant.parse("2026-05-28T10:15:30.00Z"));
    }

    @Test
    void shouldConstructValidSnapshot_andExposeAccessors() {
        LedgerSnapshot snapshot = new LedgerSnapshot(
                new BigDecimal("5000.00"),
                new BigDecimal("4800.00"),
                new BigDecimal("3200.00"),
                new BigDecimal("-1600.00"),
                12,
                new BigDecimal("0.6667"),
                Map.of(DEALER_ID, dealerStats()),
                Map.of(BOOK_ID, bookStats()),
                List.of(borrowTransaction()),
                List.of(borrowTransaction()));

        assertThat(snapshot.totalBorrowed()).isEqualByComparingTo("5000.00");
        assertThat(snapshot.totalSpent()).isEqualByComparingTo("4800.00");
        assertThat(snapshot.totalWon()).isEqualByComparingTo("3200.00");
        assertThat(snapshot.netPosition()).isEqualByComparingTo("-1600.00");
        assertThat(snapshot.ticketCount()).isEqualTo(12);
        assertThat(snapshot.rollingReturnRate()).isEqualByComparingTo("0.6667");
        assertThat(snapshot.perDealerStats()).containsEntry(DEALER_ID, dealerStats());
        assertThat(snapshot.perBookStats()).containsEntry(BOOK_ID, bookStats());
        assertThat(snapshot.recentTransactions()).containsExactly(borrowTransaction());
        assertThat(snapshot.sessionBorrowEvents()).containsExactly(borrowTransaction());
    }

    @Test
    void shouldAcceptNegativeNetPosition_forLosingPlayer() {
        LedgerSnapshot snapshot = newSnapshotWithNetPosition(new BigDecimal("-2500.00"));

        assertThat(snapshot.netPosition()).isEqualByComparingTo("-2500.00");
    }

    @Test
    void shouldAcceptReturnRateAboveOne_forLuckyPlayer() {
        LedgerSnapshot snapshot = new LedgerSnapshot(
                new BigDecimal("100.00"),
                new BigDecimal("100.00"),
                new BigDecimal("250.00"),
                new BigDecimal("150.00"),
                3,
                new BigDecimal("2.50"),
                Map.of(),
                Map.of(),
                List.of(),
                List.of());

        assertThat(snapshot.rollingReturnRate()).isEqualByComparingTo("2.50");
    }

    @Test
    void shouldAcceptZeroAndEmptyValues_forFreshPlayer() {
        LedgerSnapshot snapshot = new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of());

        assertThat(snapshot.ticketCount()).isZero();
        assertThat(snapshot.perDealerStats()).isEmpty();
        assertThat(snapshot.perBookStats()).isEmpty();
        assertThat(snapshot.recentTransactions()).isEmpty();
        assertThat(snapshot.sessionBorrowEvents()).isEmpty();
    }

    @Test
    void shouldDefensivelyCopyInputCollections() {
        Map<UUID, DealerStats> dealers = new HashMap<>(Map.of(DEALER_ID, dealerStats()));
        Map<UUID, BookStats> books = new HashMap<>(Map.of(BOOK_ID, bookStats()));
        List<Transaction> recent = new ArrayList<>(List.of(borrowTransaction()));
        List<Transaction> borrows = new ArrayList<>(List.of(borrowTransaction()));

        LedgerSnapshot snapshot = newSnapshot(dealers, books, recent, borrows);

        dealers.clear();
        books.clear();
        recent.clear();
        borrows.clear();

        assertThat(snapshot.perDealerStats()).hasSize(1);
        assertThat(snapshot.perBookStats()).hasSize(1);
        assertThat(snapshot.recentTransactions()).hasSize(1);
        assertThat(snapshot.sessionBorrowEvents()).hasSize(1);
    }

    @Test
    void shouldReturnUnmodifiablePerDealerStats() {
        LedgerSnapshot snapshot = newSnapshot(
                Map.of(DEALER_ID, dealerStats()), Map.of(), List.of(), List.of());

        assertThatThrownBy(() -> snapshot.perDealerStats().put(DEALER_ID, dealerStats()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiablePerBookStats() {
        LedgerSnapshot snapshot = newSnapshot(
                Map.of(), Map.of(BOOK_ID, bookStats()), List.of(), List.of());

        assertThatThrownBy(() -> snapshot.perBookStats().put(BOOK_ID, bookStats()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableRecentTransactions() {
        LedgerSnapshot snapshot = newSnapshot(
                Map.of(), Map.of(), List.of(borrowTransaction()), List.of());

        assertThatThrownBy(() -> snapshot.recentTransactions().add(borrowTransaction()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldReturnUnmodifiableSessionBorrowEvents() {
        LedgerSnapshot snapshot = newSnapshot(
                Map.of(), Map.of(), List.of(), List.of(borrowTransaction()));

        assertThatThrownBy(() -> snapshot.sessionBorrowEvents().add(borrowTransaction()))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldRejectNullTotalBorrowed() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalBorrowed");
    }

    @Test
    void shouldRejectNegativeTotalBorrowed() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                new BigDecimal("-0.01"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalBorrowed");
    }

    @Test
    void shouldRejectNullTotalSpent() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNegativeTotalSpent() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                new BigDecimal("-1"),
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalSpent");
    }

    @Test
    void shouldRejectNullTotalWon() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNegativeTotalWon() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                new BigDecimal("-5"),
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalWon");
    }

    @Test
    void shouldRejectNullNetPosition() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                null,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("netPosition");
    }

    @Test
    void shouldRejectNegativeTicketCount() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                -1,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ticketCount");
    }

    @Test
    void shouldRejectNullRollingReturnRate() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                null,
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("rollingReturnRate");
    }

    @Test
    void shouldRejectNegativeRollingReturnRate() {
        assertThatThrownBy(() -> new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                new BigDecimal("-0.10"),
                Map.of(),
                Map.of(),
                List.of(),
                List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("rollingReturnRate");
    }

    @Test
    void shouldRejectNullPerDealerStats() {
        assertThatThrownBy(() -> newSnapshot(null, Map.of(), List.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("perDealerStats");
    }

    @Test
    void shouldRejectNullPerBookStats() {
        assertThatThrownBy(() -> newSnapshot(Map.of(), null, List.of(), List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("perBookStats");
    }

    @Test
    void shouldRejectNullRecentTransactions() {
        assertThatThrownBy(() -> newSnapshot(Map.of(), Map.of(), null, List.of()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("recentTransactions");
    }

    @Test
    void shouldRejectNullSessionBorrowEvents() {
        assertThatThrownBy(() -> newSnapshot(Map.of(), Map.of(), List.of(), null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("sessionBorrowEvents");
    }

    @Test
    void shouldDefaultNearMissFieldsToZero_viaConvenienceConstructor() {
        LedgerSnapshot snapshot = newSnapshot(Map.of(), Map.of(), List.of(), List.of());

        assertThat(snapshot.revealedLoserCount()).isZero();
        assertThat(snapshot.nearMissCount()).isZero();
    }

    @Test
    void shouldExposeNearMissFields_whenSupplied() {
        LedgerSnapshot snapshot = new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                10,
                4);

        assertThat(snapshot.revealedLoserCount()).isEqualTo(10);
        assertThat(snapshot.nearMissCount()).isEqualTo(4);
    }

    @Test
    void shouldRejectNegativeRevealedLoserCount() {
        assertThatThrownBy(() -> newSnapshotWithNearMiss(-1, 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("revealedLoserCount");
    }

    @Test
    void shouldRejectNegativeNearMissCount() {
        assertThatThrownBy(() -> newSnapshotWithNearMiss(0, -1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearMissCount");
    }

    @Test
    void shouldRejectNearMissCountExceedingRevealedLoserCount() {
        assertThatThrownBy(() -> newSnapshotWithNearMiss(3, 4))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nearMissCount");
    }

    private static LedgerSnapshot newSnapshotWithNearMiss(int revealedLoserCount, int nearMissCount) {
        return new LedgerSnapshot(
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                0,
                BigDecimal.ZERO,
                Map.of(),
                Map.of(),
                List.of(),
                List.of(),
                revealedLoserCount,
                nearMissCount);
    }

    private static LedgerSnapshot newSnapshotWithNetPosition(BigDecimal netPosition) {
        return new LedgerSnapshot(
                new BigDecimal("5000.00"),
                new BigDecimal("4800.00"),
                new BigDecimal("3200.00"),
                netPosition,
                12,
                new BigDecimal("0.6667"),
                Map.of(),
                Map.of(),
                List.of(),
                List.of());
    }

    private static LedgerSnapshot newSnapshot(
            Map<UUID, DealerStats> perDealerStats,
            Map<UUID, BookStats> perBookStats,
            List<Transaction> recentTransactions,
            List<Transaction> sessionBorrowEvents) {
        return new LedgerSnapshot(
                new BigDecimal("5000.00"),
                new BigDecimal("4800.00"),
                new BigDecimal("3200.00"),
                new BigDecimal("-1600.00"),
                12,
                new BigDecimal("0.6667"),
                perDealerStats,
                perBookStats,
                recentTransactions,
                sessionBorrowEvents);
    }
}
