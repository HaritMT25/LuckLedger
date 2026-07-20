package com.luckledger.api.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** Spring Data repository for persisted shops (dealers). */
public interface DealerRepository extends JpaRepository<DealerEntity, UUID> {

    /**
     * Atomically bumps a dealer's depleted-book counter in place ({@code booksDepleted + 1}). Done as a
     * single UPDATE rather than a load-increment-save so two transactions depleting different books of
     * the same dealer cannot lost-update each other.
     *
     * @return the number of rows updated (1 if the dealer exists)
     */
    @Modifying
    @Query("update DealerEntity d set d.booksDepleted = d.booksDepleted + 1 where d.id = :id")
    int incrementBooksDepleted(@Param("id") UUID id);
}
