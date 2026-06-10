package com.luckledger.api.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted tickets. */
public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByBookIdOrderByPositionInBookAsc(UUID bookId);

    List<TicketEntity> findByGameId(UUID gameId);

    /** The ticket at a given sale position within a book (used to draw the next ticket). */
    Optional<TicketEntity> findByBookIdAndPositionInBook(UUID bookId, int positionInBook);

    /** A player's bought-but-unscratched tickets (recovery after a refresh); stable id order. */
    List<TicketEntity> findByPlayerIdAndRevealedFalseOrderByIdAsc(UUID playerId);
}
