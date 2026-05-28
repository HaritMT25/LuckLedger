package com.luckledger.domain.player;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Objects;
import java.util.UUID;

/**
 * A player's identity and bankroll state within the simulator.
 *
 * <p>Holds the coin balance plus O(1) running totals ({@code totalBorrowed},
 * {@code totalSpent}, {@code totalWon}, {@code ticketCount}) that are updated in
 * lock-step with each ledger entry. Reads such as {@link #getNetPosition()} and
 * {@link #getRollingReturnRate()} are derived from these pre-computed totals so the
 * hot path never aggregates the transaction history.
 *
 * <p>All monetary amounts are {@link BigDecimal}; coins have no real-world value and
 * are only ever borrowed (never purchased) and spent (never cashed out).
 */
public class Player {

    private static final int RETURN_RATE_SCALE = 4;

    private final UUID playerId;
    private final String displayName;
    private BigDecimal coinBalance;
    private BigDecimal totalBorrowed;
    private BigDecimal totalSpent;
    private BigDecimal totalWon;
    private int ticketCount;

    /**
     * Creates a new player with an empty bankroll and zeroed running totals.
     *
     * @param playerId    stable public identifier; never {@code null}
     * @param displayName human-readable name; never {@code null} or blank
     * @throws NullPointerException     if {@code playerId} or {@code displayName} is {@code null}
     * @throws IllegalArgumentException if {@code displayName} is blank
     */
    public Player(UUID playerId, String displayName) {
        this.playerId = Objects.requireNonNull(playerId, "playerId must not be null");
        Objects.requireNonNull(displayName, "displayName must not be null");
        if (displayName.isBlank()) {
            throw new IllegalArgumentException("displayName must not be blank");
        }
        this.displayName = displayName;
        this.coinBalance = BigDecimal.ZERO;
        this.totalBorrowed = BigDecimal.ZERO;
        this.totalSpent = BigDecimal.ZERO;
        this.totalWon = BigDecimal.ZERO;
        this.ticketCount = 0;
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public BigDecimal getCoinBalance() {
        return coinBalance;
    }

    public BigDecimal getTotalBorrowed() {
        return totalBorrowed;
    }

    public BigDecimal getTotalSpent() {
        return totalSpent;
    }

    public BigDecimal getTotalWon() {
        return totalWon;
    }

    public int getTicketCount() {
        return ticketCount;
    }

    /**
     * Returns {@code totalWon - totalSpent}; negative for a player who is down overall.
     *
     * @return the net position, derived from running totals
     */
    public BigDecimal getNetPosition() {
        return totalWon.subtract(totalSpent);
    }

    /**
     * Returns {@code totalWon / totalSpent} as a return-to-player ratio.
     *
     * @return the rolling return rate, or {@link BigDecimal#ZERO} if nothing has been spent
     */
    public BigDecimal getRollingReturnRate() {
        if (totalSpent.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return totalWon.divide(totalSpent, RETURN_RATE_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * Reports whether the current balance covers {@code amount}.
     *
     * @param amount the amount to test; never {@code null}
     * @return {@code true} if the balance is greater than or equal to {@code amount}
     * @throws NullPointerException if {@code amount} is {@code null}
     */
    public boolean canAfford(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        return coinBalance.compareTo(amount) >= 0;
    }

    /**
     * Spends coins on a ticket purchase: reduces the balance, increments
     * {@code totalSpent}, and increments {@code ticketCount}.
     *
     * @param amount the ticket price; must be positive
     * @throws NullPointerException         if {@code amount} is {@code null}
     * @throws IllegalArgumentException     if {@code amount} is not positive
     * @throws InsufficientBalanceException if the balance does not cover {@code amount}
     */
    public void debit(BigDecimal amount) {
        requirePositive(amount);
        if (!canAfford(amount)) {
            throw new InsufficientBalanceException(playerId, coinBalance, amount);
        }
        coinBalance = coinBalance.subtract(amount);
        totalSpent = totalSpent.add(amount);
        ticketCount++;
    }

    /**
     * Awards winnings: increases the balance and increments {@code totalWon}.
     *
     * @param amount the prize amount; must be positive
     * @throws NullPointerException     if {@code amount} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not positive
     */
    public void credit(BigDecimal amount) {
        requirePositive(amount);
        coinBalance = coinBalance.add(amount);
        totalWon = totalWon.add(amount);
    }

    /**
     * Records a loan from the bank: increases the balance and increments {@code totalBorrowed}.
     *
     * @param amount the borrowed amount; must be positive
     * @throws NullPointerException     if {@code amount} is {@code null}
     * @throws IllegalArgumentException if {@code amount} is not positive
     */
    public void recordBorrow(BigDecimal amount) {
        requirePositive(amount);
        coinBalance = coinBalance.add(amount);
        totalBorrowed = totalBorrowed.add(amount);
    }

    private static void requirePositive(BigDecimal amount) {
        Objects.requireNonNull(amount, "amount must not be null");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("amount must be positive but was " + amount);
        }
    }
}
