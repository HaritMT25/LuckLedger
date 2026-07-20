package com.luckledger.api.persistence;

import com.luckledger.domain.ledger.TransactionType;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for the append-only ledger. */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByPlayerIdOrderByCreatedAtAsc(UUID playerId);

    List<TransactionEntity> findByPlayerIdAndTypeOrderByCreatedAtAsc(UUID playerId, TransactionType type);

    List<TransactionEntity> findByPlayerIdOrderByCreatedAtDesc(UUID playerId, Pageable pageable);

    /**
     * Per-shop sales for one campaign, joining the ledger to its books by {@code book_id} so only
     * transactions on <em>this game's</em> books are counted. {@code grossSales} sums the SPEND
     * (purchases), {@code paidOut} sums the WIN (payouts), and {@code ticketsSold} counts the SPENDs.
     * The join is by book because a transaction carries no game id directly; every SPEND/WIN of an
     * allocated book carries that book's shop id, so grouping by shop is exact.
     *
     * @param gameId the campaign whose sales to break down; never {@code null}
     * @return one row per shop that has transacted on this game's books
     */
    @Query("""
            select t.dealerId as shopId,
                   coalesce(sum(case when t.type = com.luckledger.domain.ledger.TransactionType.SPEND
                       then t.amount else 0 end), 0) as grossSales,
                   coalesce(sum(case when t.type = com.luckledger.domain.ledger.TransactionType.WIN
                       then t.amount else 0 end), 0) as paidOut,
                   coalesce(sum(case when t.type = com.luckledger.domain.ledger.TransactionType.SPEND
                       then 1 else 0 end), 0) as ticketsSold
            from TransactionEntity t, TicketBookEntity b
            where t.bookId = b.id and b.gameId = :gameId
            group by t.dealerId
            """)
    List<ShopSales> salesByShopForGame(@Param("gameId") UUID gameId);

    /** Projection for {@link #salesByShopForGame(UUID)}. */
    interface ShopSales {
        UUID getShopId();
        BigDecimal getGrossSales();
        BigDecimal getPaidOut();
        long getTicketsSold();
    }
}
