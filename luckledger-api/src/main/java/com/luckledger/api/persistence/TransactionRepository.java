package com.luckledger.api.persistence;

import com.luckledger.domain.ledger.TransactionType;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

/** Spring Data repository for the append-only ledger. */
public interface TransactionRepository extends JpaRepository<TransactionEntity, UUID> {

    List<TransactionEntity> findByPlayerIdOrderByCreatedAtAsc(UUID playerId);

    List<TransactionEntity> findByPlayerIdAndTypeOrderByCreatedAtAsc(UUID playerId, TransactionType type);

    List<TransactionEntity> findByPlayerIdOrderByCreatedAtDesc(UUID playerId, Pageable pageable);
}
