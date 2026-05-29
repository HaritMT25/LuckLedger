package com.luckledger.api.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persistent player state. */
public interface PlayerRepository extends JpaRepository<PlayerEntity, UUID> {}
