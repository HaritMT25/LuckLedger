package com.luckledger.api.persistence;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for persisted games. */
public interface GameRepository extends JpaRepository<GameEntity, UUID> {}
