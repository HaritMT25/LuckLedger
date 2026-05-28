package com.luckledger.domain.pool;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Creates validated {@link PoolContract} instances.
 *
 * <p>Kept separate from {@link PoolValidator} because building a valid pool is a different
 * responsibility from checking whether one is valid (ISP); the factory composes the
 * validator rather than re-implementing its rules. Pure domain logic — no Spring.
 *
 * <p>Losing tickets are not a distinct object: a {@code PoolContract} derives them from its
 * total ticket count (see {@link PoolContract#getLoserCount()} and
 * {@link PoolContract#getFloorCost()}), so the factory never constructs a separate losing
 * tier — it simply returns a contract whose losing layer is already implied by the math.
 */
public final class PoolFactory {

    private final PoolValidator validator;

    /**
     * @param validator the validator used to enforce economic constraints; must not be null
     * @throws NullPointerException if {@code validator} is null
     */
    public PoolFactory(PoolValidator validator) {
        this.validator = Objects.requireNonNull(validator, "validator must not be null");
    }

    /**
     * Builds a contract from the supplied builder and validates it.
     *
     * @param builder the partially assembled contract; must not be null
     * @return the immutable, validated {@link PoolContract}
     * @throws NullPointerException if {@code builder} is null
     * @throws InvalidPoolException if the assembled pool violates any validation rule;
     *                              the exception carries every collected error
     */
    public PoolContract create(PoolContract.Builder builder) {
        Objects.requireNonNull(builder, "builder must not be null");
        PoolContract pool = builder.build();
        ValidationResult result = validator.validate(pool);
        if (!result.isValid()) {
            throw new InvalidPoolException("pool failed validation", result);
        }
        return pool;
    }

    /**
     * Builds a contract whose lowest-value tier's count is computed so the winning tiers
     * consume the prize budget exactly.
     *
     * <p>Useful when the creator sets the high tiers manually and lets the system fill the
     * rest with the cheapest prize. The high tiers are taken as given; the lowest-value tier
     * absorbs the remaining budget ({@code prizeBudget − cost of the other tiers}), divided
     * by its value and rounded to the nearest whole ticket. The result defaults to no payout
     * floor ({@code minPayout = 0}) and a {@link BookProfile#BALANCED} profile, then runs
     * through the standard validation in {@link #create(PoolContract.Builder)}.
     *
     * @param totalTickets the total number of tickets in the pool
     * @param ticketPrice  the price per ticket; must not be null
     * @param payoutRatio  the target return-to-player ratio; must not be null
     * @param prizeTiers   the winning tiers; must not be null or empty. The count on the
     *                     lowest-value tier is treated as a placeholder and recomputed
     * @return the immutable, validated {@link PoolContract}
     * @throws NullPointerException     if {@code ticketPrice}, {@code payoutRatio}, or
     *                                  {@code prizeTiers} is null
     * @throws IllegalArgumentException if {@code prizeTiers} is empty
     * @throws InvalidPoolException     if the remaining budget cannot fund a whole ticket of
     *                                  the lowest tier, or the balanced pool fails validation
     */
    public PoolContract createWithAutoBalance(int totalTickets, BigDecimal ticketPrice,
            BigDecimal payoutRatio, List<PrizeTier> prizeTiers) {
        Objects.requireNonNull(ticketPrice, "ticketPrice must not be null");
        Objects.requireNonNull(payoutRatio, "payoutRatio must not be null");
        Objects.requireNonNull(prizeTiers, "prizeTiers must not be null");
        if (prizeTiers.isEmpty()) {
            throw new IllegalArgumentException("at least one prize tier is required to auto-balance");
        }

        PrizeTier lowest = prizeTiers.stream()
                .min(Comparator.comparing(PrizeTier::value))
                .orElseThrow();

        BigDecimal prizeBudget = ticketPrice
                .multiply(BigDecimal.valueOf(totalTickets))
                .multiply(payoutRatio);
        BigDecimal fixedTierCost = prizeTiers.stream()
                .filter(tier -> tier != lowest)
                .map(PrizeTier::getTierCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal remainingBudget = prizeBudget.subtract(fixedTierCost);

        int balancedCount = remainingBudget.divide(lowest.value(), 0, RoundingMode.HALF_UP).intValueExact();
        if (balancedCount < 1) {
            throw new InvalidPoolException("cannot auto-balance: remaining prize budget " + remainingBudget
                    + " cannot fund a whole ticket of the lowest tier '" + lowest.label()
                    + "' (value " + lowest.value() + ")");
        }

        PoolContract.Builder builder = PoolContract.builder()
                .totalTickets(totalTickets)
                .ticketPrice(ticketPrice)
                .payoutRatio(payoutRatio)
                .minPayout(BigDecimal.ZERO)
                .bookProfile(BookProfile.BALANCED);
        for (PrizeTier tier : prizeTiers) {
            PrizeTier toAdd = tier == lowest
                    ? new PrizeTier(lowest.value(), balancedCount, lowest.label())
                    : tier;
            builder.addPrizeTier(toAdd);
        }
        return create(builder);
    }
}
