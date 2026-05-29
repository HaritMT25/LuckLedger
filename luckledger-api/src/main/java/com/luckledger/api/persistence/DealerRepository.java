package com.luckledger.api.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted shops (dealers). */
public interface DealerRepository extends JpaRepository<DealerEntity, UUID> {
}
