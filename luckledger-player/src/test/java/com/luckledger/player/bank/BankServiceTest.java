package com.luckledger.player.bank;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.player.Player;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class BankServiceTest {

    private TransactionRecorder recorder;
    private BankService bank;
    private Player player;

    @BeforeEach
    void setUp() {
        recorder = new TransactionRecorder();
        bank = new BankService(recorder);
        player = new Player(UUID.randomUUID(), "Ada");
    }

    @Test
    void borrowCreditsBalanceAndAccumulatesBorrowedTotal() {
        bank.borrow(player, new BigDecimal("100"));
        bank.borrow(player, new BigDecimal("50"));

        assertThat(player.getCoinBalance()).isEqualByComparingTo("150");
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("150");
    }

    @Test
    void borrowReturnsBorrowTransactionWithNoDealerBookOrTicket() {
        Instant before = Instant.now();
        Transaction tx = bank.borrow(player, new BigDecimal("75"));
        Instant after = Instant.now();

        assertThat(tx.transactionId()).isNotNull();
        assertThat(tx.playerId()).isEqualTo(player.getPlayerId());
        assertThat(tx.type()).isEqualTo(TransactionType.BORROW);
        assertThat(tx.amount()).isEqualByComparingTo("75");
        assertThat(tx.dealerId()).isNull();
        assertThat(tx.bookId()).isNull();
        assertThat(tx.ticketId()).isNull();
        assertThat(tx.timestamp()).isBetween(before, after);
    }

    @Test
    void borrowRecordsExactlyOneTransactionPerCall() {
        Transaction first = bank.borrow(player, new BigDecimal("10"));
        Transaction second = bank.borrow(player, new BigDecimal("20"));

        List<Transaction> recorded = recorder.getTransactions(player.getPlayerId());
        assertThat(recorded).containsExactly(first, second);
        assertThat(recorded).allMatch(t -> t.type() == TransactionType.BORROW);
    }

    @Test
    void bankNeverSaysNoToLargeOrRepeatedBorrowing() {
        BigDecimal huge = new BigDecimal("1000000000");
        for (int i = 0; i < 100; i++) {
            bank.borrow(player, huge);
        }

        assertThat(player.getTotalBorrowed()).isEqualByComparingTo(huge.multiply(new BigDecimal("100")));
        assertThat(recorder.getTransactions(player.getPlayerId())).hasSize(100);
    }

    @Test
    void getBorrowedTotalReadsPlayerRunningTotal() {
        assertThat(bank.getBorrowedTotal(player)).isEqualByComparingTo("0");

        bank.borrow(player, new BigDecimal("250"));

        assertThat(bank.getBorrowedTotal(player)).isEqualByComparingTo(player.getTotalBorrowed());
        assertThat(bank.getBorrowedTotal(player)).isEqualByComparingTo("250");
    }

    @ParameterizedTest
    @ValueSource(strings = {"1", "200", "0.01", "12.50", "1000000000"})
    void borrowGrantsTheExactCoinsRequestedAndRecordsOneBorrow(String amount) {
        BigDecimal requested = new BigDecimal(amount);

        Transaction tx = bank.borrow(player, requested);

        assertThat(player.getCoinBalance()).isEqualByComparingTo(requested);
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo(requested);
        assertThat(tx.type()).isEqualTo(TransactionType.BORROW);
        assertThat(tx.amount()).isEqualByComparingTo(requested);
        assertThat(recorder.getTransactions(player.getPlayerId(), TransactionType.BORROW))
                .containsExactly(tx);
    }

    @Test
    void borrowAccumulatesBorrowedTotalWithoutLosingDecimalPrecision() {
        bank.borrow(player, new BigDecimal("0.01"));
        bank.borrow(player, new BigDecimal("0.02"));
        bank.borrow(player, new BigDecimal("0.10"));

        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("0.13");
        assertThat(player.getCoinBalance()).isEqualByComparingTo("0.13");
        assertThat(bank.getBorrowedTotal(player)).isEqualByComparingTo("0.13");
    }

    @Test
    void getBorrowedTotalEqualsTheSumOfEveryBorrow() {
        bank.borrow(player, new BigDecimal("25"));
        bank.borrow(player, new BigDecimal("75.50"));
        bank.borrow(player, new BigDecimal("0.50"));

        assertThat(bank.getBorrowedTotal(player)).isEqualByComparingTo("101.00");
    }

    @Test
    void eachBorrowProducesAUniqueTransactionId() {
        bank.borrow(player, new BigDecimal("10"));
        bank.borrow(player, new BigDecimal("10"));
        bank.borrow(player, new BigDecimal("10"));

        assertThat(recorder.getTransactions(player.getPlayerId()))
                .extracting(Transaction::transactionId)
                .doesNotHaveDuplicates();
    }

    @Test
    void borrowRejectsNullPlayer() {
        assertThatNullPointerException().isThrownBy(() -> bank.borrow(null, BigDecimal.TEN));
    }

    @Test
    void borrowRejectsNonPositiveAmountWithoutMutatingStateOrRecording() {
        assertThatThrownBy(() -> bank.borrow(player, BigDecimal.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> bank.borrow(player, new BigDecimal("-5")))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(player.getCoinBalance()).isEqualByComparingTo("0");
        assertThat(player.getTotalBorrowed()).isEqualByComparingTo("0");
        assertThat(recorder.getTransactions(player.getPlayerId())).isEmpty();
    }

    @Test
    void borrowRejectsNullAmount() {
        assertThatNullPointerException().isThrownBy(() -> bank.borrow(player, null));
    }

    @Test
    void constructorRejectsNullRecorder() {
        assertThatNullPointerException().isThrownBy(() -> new BankService(null));
    }
}
