package com.luckledger.api.persistence;

import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for persisted tickets. */
public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByBookIdOrderByPositionInBookAsc(UUID bookId);

    /**
     * Loads a ticket under a pessimistic write lock so a reveal's check-then-act on the reveal flags
     * is atomic — two threads racing to scratch the same ticket cannot both pass the "not yet
     * revealed" gate. The ticket is locked before the player, per the writer lock-order rule.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from TicketEntity t where t.id = :id")
    Optional<TicketEntity> findByIdForUpdate(@Param("id") UUID id);

    List<TicketEntity> findByGameId(UUID gameId);

    /** The ticket at a given sale position within a book (used to draw the next ticket). */
    Optional<TicketEntity> findByBookIdAndPositionInBook(UUID bookId, int positionInBook);

    /** A player's bought-but-unscratched tickets (recovery after a refresh); stable id order. */
    List<TicketEntity> findByPlayerIdAndRevealedFalseOrderByIdAsc(UUID playerId);

    /**
     * Per-game pool economics in one pass, for the operator ("house") view: how many tickets exist /
     * sold / revealed, the predetermined prize fund and top prize, and what has actually been paid
     * out so far. The prize fund is the sum every ticket was BUILT to pay — known before the first
     * sale, which is the educational point.
     */
    @Query("""
            select t.gameId as gameId,
                   count(t) as totalTickets,
                   coalesce(sum(case when t.status <> com.luckledger.domain.scratch.TicketStatus.AVAILABLE
                       then 1 else 0 end), 0) as soldTickets,
                   coalesce(sum(case when t.revealed = true then 1 else 0 end), 0) as revealedTickets,
                   coalesce(sum(t.prizeAmount), 0) as prizeFund,
                   coalesce(max(t.prizeAmount), 0) as topPrize,
                   coalesce(sum(case when t.revealed = true then t.revealedPrize else 0 end), 0) as paidOut
            from TicketEntity t
            group by t.gameId
            """)
    List<GameTicketStats> aggregateByGame();

    /** Bought-but-unscratched ticket counts per player, for the master's player oversight. */
    @Query("""
            select t.playerId as playerId, count(t) as pending
            from TicketEntity t
            where t.playerId is not null and t.revealed = false
            group by t.playerId
            """)
    List<PendingByPlayer> countPendingByPlayer();

    /** Projection for {@link #countPendingByPlayer()}. */
    interface PendingByPlayer {
        UUID getPlayerId();
        long getPending();
    }

    /** Projection for {@link #aggregateByGame()}. */
    interface GameTicketStats {
        UUID getGameId();
        long getTotalTickets();
        long getSoldTickets();
        long getRevealedTickets();
        BigDecimal getPrizeFund();
        BigDecimal getTopPrize();
        BigDecimal getPaidOut();
    }
}
