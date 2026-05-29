package com.luckledger.api.persistence;

import com.luckledger.domain.ledger.TransactionType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for an append-only ledger transaction (BORROW / SPEND / WIN). Immutable after insert. */
@Entity
@Table(name = "ledger_transaction")
public class TransactionEntity {

    @Id
    private UUID id;

    @Column(name = "player_id", nullable = false)
    private UUID playerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "dealer_id")
    private UUID dealerId;

    @Column(name = "book_id")
    private UUID bookId;

    @Column(name = "ticket_id")
    private UUID ticketId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected TransactionEntity() {}

    public TransactionEntity(UUID id, UUID playerId, TransactionType type, BigDecimal amount,
            UUID dealerId, UUID bookId, UUID ticketId, Instant createdAt) {
        this.id = id;
        this.playerId = playerId;
        this.type = type;
        this.amount = amount;
        this.dealerId = dealerId;
        this.bookId = bookId;
        this.ticketId = ticketId;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getPlayerId() { return playerId; }
    public TransactionType getType() { return type; }
    public BigDecimal getAmount() { return amount; }
    public UUID getDealerId() { return dealerId; }
    public UUID getBookId() { return bookId; }
    public UUID getTicketId() { return ticketId; }
    public Instant getCreatedAt() { return createdAt; }
}
