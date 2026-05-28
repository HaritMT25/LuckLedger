package com.luckledger.domain.player;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Thrown when a player attempts to debit more than their current balance.
 *
 * <p>Raised by {@code Player.debit} when the requested amount exceeds the
 * available balance. Carries the player identifier together with the balance
 * and requested amount at the moment of failure so callers can report or log
 * the shortfall without re-querying state.
 */
public class InsufficientBalanceException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final UUID playerId;
    private final BigDecimal currentBalance;
    private final BigDecimal requestedAmount;

    /**
     * Creates an exception describing a failed debit attempt.
     *
     * @param playerId        the identifier of the player whose debit failed
     * @param currentBalance  the player's balance at the time of the attempt
     * @param requestedAmount the amount the player attempted to debit
     */
    public InsufficientBalanceException(UUID playerId, BigDecimal currentBalance, BigDecimal requestedAmount) {
        super("Player " + playerId + " has balance " + currentBalance + " but requested " + requestedAmount);
        this.playerId = playerId;
        this.currentBalance = currentBalance;
        this.requestedAmount = requestedAmount;
    }

    /**
     * Returns the identifier of the player whose debit failed.
     *
     * @return the player identifier
     */
    public UUID getPlayerId() {
        return playerId;
    }

    /**
     * Returns the player's balance at the time the debit was attempted.
     *
     * @return the current balance
     */
    public BigDecimal getCurrentBalance() {
        return currentBalance;
    }

    /**
     * Returns the amount the player attempted to debit.
     *
     * @return the requested amount
     */
    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}
