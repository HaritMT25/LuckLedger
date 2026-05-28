package com.luckledger.player.bank;

import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.player.Player;
import com.luckledger.player.ledger.TransactionRecorder;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * The in-game bank — the project's legal shield (DESIGN §3.1).
 *
 * <p>The bank lends virtual coins freely: there is no credit limit, no interest, and no repayment
 * obligation. Borrowing is a one-way transfer, so there is no {@code Loan} entity — a single
 * {@code BORROW} ledger entry per loan is sufficient. Removing the "money in / money out" pressure
 * is what keeps the simulator outside gambling law.
 *
 * <p>Each {@link #borrow} updates the player's pre-computed running total via
 * {@link Player#recordBorrow} and appends a matching {@code BORROW} transaction to the ledger, so
 * {@link #getBorrowedTotal} is an O(1) read rather than an aggregation over history.
 */
public class BankService {

    private final TransactionRecorder transactionRecorder;

    /**
     * Creates a bank backed by the given append-only ledger store.
     *
     * @param transactionRecorder the recorder every {@code BORROW} is appended to; never {@code null}
     * @throws NullPointerException if {@code transactionRecorder} is {@code null}
     */
    public BankService(TransactionRecorder transactionRecorder) {
        this.transactionRecorder = Objects.requireNonNull(transactionRecorder, "transactionRecorder must not be null");
    }

    /**
     * Grants the player a free loan: credits their balance, bumps their borrowed running total, and
     * records a {@code BORROW} transaction. The bank never refuses.
     *
     * <p>The amount is validated by {@link Player#recordBorrow} before any state changes, so an
     * invalid amount leaves the player untouched and writes nothing to the ledger.
     *
     * @param player the borrower; never {@code null}
     * @param amount the amount to lend; must be strictly positive
     * @return the recorded {@code BORROW} transaction
     * @throws NullPointerException     if {@code player} or {@code amount} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not strictly positive
     */
    public Transaction borrow(Player player, BigDecimal amount) {
        Objects.requireNonNull(player, "player must not be null");
        player.recordBorrow(amount);
        Transaction transaction = new Transaction(
                UUID.randomUUID(),
                player.getPlayerId(),
                TransactionType.BORROW,
                amount,
                null,
                null,
                null,
                Instant.now());
        transactionRecorder.record(transaction);
        return transaction;
    }

    /**
     * Returns how much the player has borrowed in total, read from the player's running total.
     *
     * @param player the player to read; never {@code null}
     * @return the player's total borrowed amount
     * @throws NullPointerException if {@code player} is {@code null}
     */
    public BigDecimal getBorrowedTotal(Player player) {
        Objects.requireNonNull(player, "player must not be null");
        return player.getTotalBorrowed();
    }
}
