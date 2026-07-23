package com.luckledger.api.persistence;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for persisted games. */
public interface GameRepository extends JpaRepository<GameEntity, UUID> {

    /**
     * Loads a game under a pessimistic write lock so a restock serializes against a concurrent restock
     * of the same game. Restock is the <em>only</em> writer that locks a game row, and it holds the lock
     * for its whole transaction (generate + persist the new books/tickets + fold the totals in), so two
     * restocks of one game run strictly one-after-the-other instead of both reading the same totals and
     * clobbering each other's increment. Because restock takes no book, ticket, or player row locks
     * (it only inserts brand-new book/ticket rows and updates the already-locked game row), and no other
     * write path (purchase, reveal, borrow) ever locks a game row, this lock cannot participate in a
     * cross-ordering deadlock cycle with the book → player lock order used elsewhere.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select g from GameEntity g where g.id = :id")
    Optional<GameEntity> findByIdForUpdate(@Param("id") UUID id);
}
