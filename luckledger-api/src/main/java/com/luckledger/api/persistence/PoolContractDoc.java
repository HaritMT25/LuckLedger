package com.luckledger.api.persistence;

import com.luckledger.domain.pool.BookProfile;
import com.luckledger.domain.pool.PoolContract;
import com.luckledger.domain.pool.PrizeTier;
import java.math.BigDecimal;
import java.util.List;

/**
 * The JSON-persistable shape of a {@link PoolContract}, stored in {@code game.pool_contract} so a
 * restock can regenerate an identical-economics batch for a campaign game.
 *
 * <p><strong>Why a separate document.</strong> {@link PoolContract} carries several <em>derived</em>
 * getters ({@code getPrizeBudget}, {@code getWinnerCount}, {@code getTotalRevenue}, ...) that Jackson
 * would serialize as if they were state and then fail to read back — poisoning the round-trip. This
 * record mirrors only the contract's stored components, so {@code toDomain()}/{@code fromDomain(...)}
 * are lossless. It lives in the persistence layer, not the domain (which stays Spring-free); Jackson
 * maps records field-by-field with no annotations required.
 *
 * @param totalTickets the pool's total ticket count
 * @param ticketPrice the price per ticket
 * @param payoutRatio the target return-to-player ratio the contract was built with
 * @param prizeTiers the winning tiers
 * @param minPayout the guaranteed minimum payout per ticket ({@code 0} = pure losers exist)
 * @param bookProfile the prize-distribution shape
 */
public record PoolContractDoc(
        int totalTickets,
        BigDecimal ticketPrice,
        BigDecimal payoutRatio,
        List<TierDoc> prizeTiers,
        BigDecimal minPayout,
        BookProfile bookProfile) {

    /** The JSON-persistable shape of a single {@link PrizeTier}. */
    public record TierDoc(BigDecimal value, int count, String label) {}

    /**
     * Rebuilds the immutable domain {@link PoolContract} this document was captured from.
     *
     * @return an equivalent {@link PoolContract}
     */
    public PoolContract toDomain() {
        PoolContract.Builder builder = PoolContract.builder()
                .totalTickets(totalTickets)
                .ticketPrice(ticketPrice)
                .payoutRatio(payoutRatio)
                .minPayout(minPayout)
                .bookProfile(bookProfile);
        prizeTiers.forEach(t -> builder.addPrizeTier(new PrizeTier(t.value(), t.count(), t.label())));
        return builder.build();
    }

    /**
     * Captures a domain {@link PoolContract} as a persistable document.
     *
     * @param contract the contract to capture; never {@code null}
     * @return the equivalent {@link PoolContractDoc}
     */
    public static PoolContractDoc fromDomain(PoolContract contract) {
        List<TierDoc> tiers = contract.prizeTiers().stream()
                .map(t -> new TierDoc(t.value(), t.count(), t.label()))
                .toList();
        return new PoolContractDoc(
                contract.totalTickets(),
                contract.ticketPrice(),
                contract.payoutRatio(),
                tiers,
                contract.minPayout(),
                contract.bookProfile());
    }
}
