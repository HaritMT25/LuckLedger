package com.luckledger.api.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted tickets. */
public interface TicketRepository extends JpaRepository<TicketEntity, UUID> {

    List<TicketEntity> findByBookIdOrderByPositionInBookAsc(UUID bookId);

    List<TicketEntity> findByGameId(UUID gameId);
}
