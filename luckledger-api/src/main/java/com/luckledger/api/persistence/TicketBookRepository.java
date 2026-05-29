package com.luckledger.api.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted ticket books. */
public interface TicketBookRepository extends JpaRepository<TicketBookEntity, UUID> {

    List<TicketBookEntity> findByGameId(UUID gameId);

    List<TicketBookEntity> findByDealerId(UUID dealerId);
}
