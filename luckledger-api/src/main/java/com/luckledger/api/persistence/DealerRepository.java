package com.luckledger.api.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted dealers. */
public interface DealerRepository extends JpaRepository<DealerEntity, UUID> {

    List<DealerEntity> findByGameId(UUID gameId);
}
