package com.luckledger.domain.pool;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * Immutable specification for a batch of scratch tickets — the mathematical foundation
 * (Layer 1) from which every other subsystem derives its behaviour.
 *
 * <p>A {@code PoolContract} fixes the economics of a pool before any ticket exists:
 * how many tickets, their price, the target payout ratio (RTP), the winning prize tiers,
 * and an optional minimum payout floor. It is built via {@link Builder} and is immutable
 * once constructed; {@code prizeTiers} are held as an unmodifiable list sorted by value
 * descending.
 *
 * <p>This value object enforces only structural invariants (non-null components, no null
 * tiers). The economic constraints — {@code tierCost + floorCost == prizeBudget},
 * {@code payoutRatio} in {@code (0,1)}, positive ticket counts, no duplicate tier values —
 * are intentionally left to {@code PoolValidator} so that an invalid pool can be constructed
 * and reported on rather than rejected at construction time.
 *
 * <p>The payout ratio is sacred: nothing downstream of this contract may alter the RTP.
 *
 * @param totalTickets the total number of tickets in the pool
 * @param ticketPrice  the price paid per ticket; never {@code null}
 * @param payoutRatio  the target return-to-player ratio; never {@code null}
 * @param prizeTiers   the winning tiers only, held sorted by value descending; never {@code null}
 *                     and never containing {@code null} elements
 * @param minPayout    the guaranteed minimum payout per ticket ({@code 0} = pure losers exist,
 *                     {@code > 0} = floor); never {@code null}
 * @param bookProfile  the prize-distribution shape; never {@code null}
 */
public record PoolContract(
        int totalTickets,
        BigDecimal ticketPrice,
        BigDecimal payoutRatio,
        List<PrizeTier> prizeTiers,
        BigDecimal minPayout,
        BookProfile bookProfile) {

    /**
     * Validates structural invariants, defensively copies the prize tiers, and sorts them
     * by value descending.
     *
     * @throws NullPointerException if any component is {@code null} or {@code prizeTiers}
     *                              contains a {@code null} element
     */
    public PoolContract {
        Objects.requireNonNull(ticketPrice, "ticketPrice must not be null");
        Objects.requireNonNull(payoutRatio, "payoutRatio must not be null");
        Objects.requireNonNull(prizeTiers, "prizeTiers must not be null");
        Objects.requireNonNull(minPayout, "minPayout must not be null");
        Objects.requireNonNull(bookProfile, "bookProfile must not be null");

        List<PrizeTier> sorted = new ArrayList<>(prizeTiers);
        sorted.forEach(tier -> Objects.requireNonNull(tier, "prizeTiers must not contain null elements"));
        sorted.sort(Comparator.comparing(PrizeTier::value).reversed());
        prizeTiers = List.copyOf(sorted);
    }

    /**
     * @return total revenue if every ticket is sold ({@code totalTickets × ticketPrice})
     */
    public BigDecimal getTotalRevenue() {
        return ticketPrice.multiply(BigDecimal.valueOf(totalTickets));
    }

    /**
     * @return the prize budget ({@code totalRevenue × payoutRatio})
     */
    public BigDecimal getPrizeBudget() {
        return getTotalRevenue().multiply(payoutRatio);
    }

    /**
     * @return the number of winning tickets ({@code sum of tier counts})
     */
    public int getWinnerCount() {
        return prizeTiers.stream().mapToInt(PrizeTier::count).sum();
    }

    /**
     * @return the number of losing tickets ({@code totalTickets − winnerCount})
     */
    public int getLoserCount() {
        return totalTickets - getWinnerCount();
    }

    /**
     * @return the combined cost of all winning tiers ({@code sum of tier.getTierCost()})
     */
    public BigDecimal getTierCost() {
        return prizeTiers.stream()
                .map(PrizeTier::getTierCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /**
     * @return the cost of the minimum-payout floor across all losers
     *         ({@code loserCount × minPayout})
     */
    public BigDecimal getFloorCost() {
        return minPayout.multiply(BigDecimal.valueOf(getLoserCount()));
    }

    /**
     * @return the fraction of tickets that win ({@code winnerCount / totalTickets})
     */
    public double getWinFrequency() {
        return (double) getWinnerCount() / totalTickets;
    }

    /**
     * @return a new {@link Builder} for assembling a {@code PoolContract}
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for {@link PoolContract}. Tiers may be supplied in any order; the
     * resulting contract holds them sorted by value descending. {@code minPayout} defaults
     * to {@link BigDecimal#ZERO} (no floor) when not set.
     */
    public static final class Builder {

        private int totalTickets;
        private BigDecimal ticketPrice;
        private BigDecimal payoutRatio;
        private final List<PrizeTier> prizeTiers = new ArrayList<>();
        private BigDecimal minPayout = BigDecimal.ZERO;
        private BookProfile bookProfile;

        private Builder() {
        }

        public Builder totalTickets(int totalTickets) {
            this.totalTickets = totalTickets;
            return this;
        }

        public Builder ticketPrice(BigDecimal ticketPrice) {
            this.ticketPrice = ticketPrice;
            return this;
        }

        public Builder payoutRatio(BigDecimal payoutRatio) {
            this.payoutRatio = payoutRatio;
            return this;
        }

        public Builder addPrizeTier(PrizeTier prizeTier) {
            this.prizeTiers.add(prizeTier);
            return this;
        }

        /**
         * Replaces all currently staged tiers with the supplied list.
         *
         * @throws NullPointerException if {@code prizeTiers} is {@code null}
         */
        public Builder prizeTiers(List<PrizeTier> prizeTiers) {
            Objects.requireNonNull(prizeTiers, "prizeTiers must not be null");
            this.prizeTiers.clear();
            this.prizeTiers.addAll(prizeTiers);
            return this;
        }

        public Builder minPayout(BigDecimal minPayout) {
            this.minPayout = minPayout;
            return this;
        }

        public Builder bookProfile(BookProfile bookProfile) {
            this.bookProfile = bookProfile;
            return this;
        }

        /**
         * @return the assembled immutable {@link PoolContract}
         */
        public PoolContract build() {
            return new PoolContract(totalTickets, ticketPrice, payoutRatio, prizeTiers, minPayout, bookProfile);
        }
    }
}
