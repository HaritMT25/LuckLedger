package com.luckledger.domain.pool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

/**
 * Validates the economic constraints of a {@link PoolContract}.
 *
 * <p>Kept separate from {@code PoolContract} so the rules can evolve independently of the
 * data structure (SRP) and so an invalid pool can be constructed and reported on rather
 * than rejected at construction time. {@code PoolFactory} composes this validator.
 *
 * <p>Runs seven checks and collects every failure into a single {@link ValidationResult}
 * so a caller sees all problems at once rather than fixing them one at a time.
 */
public final class PoolValidator {

    /**
     * Allowed gap between the prize budget and what the tiers plus floor actually pay out.
     *
     * <p>The budget ({@code revenue × payoutRatio}) can carry fractional cents that integer
     * tier counts cannot reproduce exactly, so an exact equality check would reject otherwise
     * sound pools. One cent is the smallest meaningful monetary unit here.
     */
    private static final BigDecimal BUDGET_TOLERANCE = new BigDecimal("0.01");

    /**
     * Runs all economic checks against the supplied contract.
     *
     * @param pool the contract to validate; must not be null
     * @return a passing {@link ValidationResult} when every check holds, otherwise a failing
     *         result carrying one message per violated check
     * @throws NullPointerException if {@code pool} is null
     */
    public ValidationResult validate(PoolContract pool) {
        Objects.requireNonNull(pool, "pool must not be null");

        List<String> errors = new ArrayList<>();

        checkBudgetBalances(pool, errors);
        checkTierCountsPositive(pool, errors);
        checkTierValuesExceedFloor(pool, errors);
        checkTotalTicketsPositive(pool, errors);
        checkPayoutRatioInOpenUnitInterval(pool, errors);
        checkLoserCountNonNegative(pool, errors);
        checkNoDuplicateTierValues(pool, errors);

        return errors.isEmpty() ? ValidationResult.valid() : ValidationResult.invalid(errors);
    }

    /** Check: {@code tierCost + floorCost == prizeBudget} within {@link #BUDGET_TOLERANCE}. */
    private void checkBudgetBalances(PoolContract pool, List<String> errors) {
        BigDecimal payout = pool.getTierCost().add(pool.getFloorCost());
        BigDecimal budget = pool.getPrizeBudget();
        if (payout.subtract(budget).abs().compareTo(BUDGET_TOLERANCE) > 0) {
            errors.add("prize budget mismatch: tierCost + floorCost = " + payout
                    + " but prizeBudget = " + budget + " (tolerance " + BUDGET_TOLERANCE + ")");
        }
    }

    /** Check: every tier has a strictly positive count. */
    private void checkTierCountsPositive(PoolContract pool, List<String> errors) {
        for (PrizeTier tier : pool.prizeTiers()) {
            if (tier.count() <= 0) {
                errors.add("tier count must be positive for tier '" + tier.label()
                        + "', was: " + tier.count());
            }
        }
    }

    /** Check: every winning tier value strictly exceeds the minimum payout floor. */
    private void checkTierValuesExceedFloor(PoolContract pool, List<String> errors) {
        for (PrizeTier tier : pool.prizeTiers()) {
            if (tier.value().compareTo(pool.minPayout()) <= 0) {
                errors.add("tier '" + tier.label() + "' value " + tier.value()
                        + " must exceed the minimum payout floor " + pool.minPayout());
            }
        }
    }

    /** Check: the pool has at least one ticket. */
    private void checkTotalTicketsPositive(PoolContract pool, List<String> errors) {
        if (pool.totalTickets() <= 0) {
            errors.add("totalTickets must be positive, was: " + pool.totalTickets());
        }
    }

    /** Check: {@code payoutRatio} lies in the open interval (0, 1). */
    private void checkPayoutRatioInOpenUnitInterval(PoolContract pool, List<String> errors) {
        BigDecimal ratio = pool.payoutRatio();
        if (ratio.compareTo(BigDecimal.ZERO) <= 0 || ratio.compareTo(BigDecimal.ONE) >= 0) {
            errors.add("payoutRatio must be in the open interval (0, 1), was: " + ratio);
        }
    }

    /** Check: winning tier counts do not exceed the total ticket count. */
    private void checkLoserCountNonNegative(PoolContract pool, List<String> errors) {
        if (pool.getLoserCount() < 0) {
            errors.add("loser count must not be negative: winning tier counts ("
                    + pool.getWinnerCount() + ") exceed totalTickets (" + pool.totalTickets() + ")");
        }
    }

    /** Check: no two winning tiers share the same value (numerically, via compareTo). */
    private void checkNoDuplicateTierValues(PoolContract pool, List<String> errors) {
        Set<BigDecimal> seen = new TreeSet<>();
        for (PrizeTier tier : pool.prizeTiers()) {
            if (!seen.add(tier.value())) {
                errors.add("duplicate tier value not allowed: " + tier.value());
            }
        }
    }
}
