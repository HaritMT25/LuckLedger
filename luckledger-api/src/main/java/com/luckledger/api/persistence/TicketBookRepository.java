package com.luckledger.api.persistence;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for persisted ticket books. */
public interface TicketBookRepository extends JpaRepository<TicketBookEntity, UUID> {

    List<TicketBookEntity> findByGameId(UUID gameId);

    List<TicketBookEntity> findByDealerId(UUID dealerId);

    /**
     * Loads a book under a pessimistic write lock so a purchase can advance the sale cursor without
     * racing another buyer. Per the writer lock-order rule, the book is locked <em>first</em> (before
     * the player) in every write path — purchase, reveal, and any borrow — to prevent deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select b from TicketBookEntity b where b.id = :id")
    Optional<TicketBookEntity> findByIdForUpdate(@Param("id") UUID id);
}
