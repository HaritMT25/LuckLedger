package com.luckledger.player.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TransactionRecorderTest {

    private static final UUID PLAYER_A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID PLAYER_B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    private TransactionRecorder recorder;

    @BeforeEach
    void setUp() {
        recorder = new InMemoryTransactionRecorder();
    }

    @Test
    void shouldReturnEmptyList_whenPlayerHasNoTransactions() {
        assertThat(recorder.getTransactions(PLAYER_A)).isEmpty();
    }

    @Test
    void shouldRecordTransaction_andReturnItForThePlayer() {
        Transaction borrow = tx(PLAYER_A, TransactionType.BORROW);

        recorder.record(borrow);

        assertThat(recorder.getTransactions(PLAYER_A)).containsExactly(borrow);
    }

    @Test
    void shouldReturnTransactions_inInsertionOrder() {
        Transaction first = tx(PLAYER_A, TransactionType.BORROW);
        Transaction second = tx(PLAYER_A, TransactionType.SPEND);
        Transaction third = tx(PLAYER_A, TransactionType.WIN);

        recorder.record(first);
        recorder.record(second);
        recorder.record(third);

        assertThat(recorder.getTransactions(PLAYER_A)).containsExactly(first, second, third);
    }

    @Test
    void shouldIsolateTransactions_byPlayer() {
        Transaction aTx = tx(PLAYER_A, TransactionType.SPEND);
        Transaction bTx = tx(PLAYER_B, TransactionType.SPEND);

        recorder.record(aTx);
        recorder.record(bTx);

        assertThat(recorder.getTransactions(PLAYER_A)).containsExactly(aTx);
        assertThat(recorder.getTransactions(PLAYER_B)).containsExactly(bTx);
    }

    @Test
    void shouldFilterTransactions_byType() {
        Transaction borrow = tx(PLAYER_A, TransactionType.BORROW);
        Transaction spend1 = tx(PLAYER_A, TransactionType.SPEND);
        Transaction win = tx(PLAYER_A, TransactionType.WIN);
        Transaction spend2 = tx(PLAYER_A, TransactionType.SPEND);

        recorder.record(borrow);
        recorder.record(spend1);
        recorder.record(win);
        recorder.record(spend2);

        assertThat(recorder.getTransactions(PLAYER_A, TransactionType.SPEND))
                .containsExactly(spend1, spend2);
        assertThat(recorder.getTransactions(PLAYER_A, TransactionType.BORROW))
                .containsExactly(borrow);
    }

    @Test
    void shouldReturnEmptyList_whenNoTransactionsOfRequestedType() {
        recorder.record(tx(PLAYER_A, TransactionType.SPEND));

        assertThat(recorder.getTransactions(PLAYER_A, TransactionType.WIN)).isEmpty();
    }

    @Test
    void shouldReturnRecentTransactions_mostRecentFirst_limitedToCount() {
        Transaction first = tx(PLAYER_A, TransactionType.BORROW);
        Transaction second = tx(PLAYER_A, TransactionType.SPEND);
        Transaction third = tx(PLAYER_A, TransactionType.WIN);

        recorder.record(first);
        recorder.record(second);
        recorder.record(third);

        assertThat(recorder.getRecentTransactions(PLAYER_A, 2)).containsExactly(third, second);
    }

    @Test
    void shouldReturnAllRecentTransactions_whenLimitExceedsCount() {
        Transaction first = tx(PLAYER_A, TransactionType.BORROW);
        Transaction second = tx(PLAYER_A, TransactionType.SPEND);

        recorder.record(first);
        recorder.record(second);

        assertThat(recorder.getRecentTransactions(PLAYER_A, 10)).containsExactly(second, first);
    }

    @Test
    void shouldReturnEmptyRecentTransactions_whenPlayerHasNone() {
        assertThat(recorder.getRecentTransactions(PLAYER_A, 5)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1, -10})
    void shouldRejectRecentTransactions_whenLimitNotPositive(int limit) {
        assertThatThrownBy(() -> recorder.getRecentTransactions(PLAYER_A, limit))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldReturnUnmodifiableList_fromGetTransactions() {
        recorder.record(tx(PLAYER_A, TransactionType.BORROW));

        List<Transaction> result = recorder.getTransactions(PLAYER_A);

        assertThatThrownBy(() -> result.add(tx(PLAYER_A, TransactionType.SPEND)))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void shouldNotAffectPreviouslyReturnedList_whenMoreTransactionsRecorded() {
        recorder.record(tx(PLAYER_A, TransactionType.BORROW));
        List<Transaction> snapshot = recorder.getTransactions(PLAYER_A);

        recorder.record(tx(PLAYER_A, TransactionType.SPEND));

        assertThat(snapshot).hasSize(1);
    }

    @Test
    void shouldRejectNullTransaction_onRecord() {
        assertThatThrownBy(() -> recorder.record(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPlayerId_onGetTransactions() {
        assertThatThrownBy(() -> recorder.getTransactions(null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullType_onGetTransactionsByType() {
        assertThatThrownBy(() -> recorder.getTransactions(PLAYER_A, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void shouldRejectNullPlayerId_onGetRecentTransactions() {
        assertThatThrownBy(() -> recorder.getRecentTransactions(null, 5))
                .isInstanceOf(NullPointerException.class);
    }

    private static Transaction tx(UUID playerId, TransactionType type) {
        return new Transaction(
                UUID.randomUUID(),
                playerId,
                type,
                new BigDecimal("10.00"),
                null,
                null,
                null,
                Instant.now());
    }
}
