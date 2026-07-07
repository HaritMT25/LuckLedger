package com.luckledger.api.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for persistent player state. */
public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {

    /**
     * Loads a player under a pessimistic write lock so a balance change (debit on purchase, credit on
     * a winning reveal, borrow) serializes against concurrent writers. Per the writer lock-order rule
     * the player is always locked <em>last</em> — after the book/ticket — to prevent deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from PlayerEntity p where p.id = :id")
    Optional<PlayerEntity> findByIdForUpdate(@Param("id") UUID id);
}
