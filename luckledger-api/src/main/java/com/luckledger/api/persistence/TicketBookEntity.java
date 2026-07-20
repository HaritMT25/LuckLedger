package com.luckledger.api.persistence;

import com.luckledger.domain.generation.MetadataVisibility;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/**
 * JPA mapping for an ordered book of tickets. {@code dealerId} is null for an unallocated book;
 * {@code nextIndex} is the sale cursor (how many tickets have been sold).
 */
@Entity
@Table(name = "ticket_book")
public class TicketBookEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "dealer_id")
    private UUID dealerId;

    @Column(name = "pool_contract_id", nullable = false)
    private UUID poolContractId;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(name = "next_index", nullable = false)
    private int nextIndex;

    /** How much of this book's depletion state the operator reveals to players (education dial). */
    @Enumerated(EnumType.STRING)
    @Column(name = "metadata_visibility", nullable = false, length = 20)
    private MetadataVisibility metadataVisibility;

    protected TicketBookEntity() {}

    public TicketBookEntity(UUID id, UUID gameId, UUID dealerId, UUID poolContractId, int totalTickets,
            int nextIndex, MetadataVisibility metadataVisibility) {
        this.id = id;
        this.gameId = gameId;
        this.dealerId = dealerId;
        this.poolContractId = poolContractId;
        this.totalTickets = totalTickets;
        this.nextIndex = nextIndex;
        this.metadataVisibility = metadataVisibility;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public UUID getDealerId() { return dealerId; }
    public UUID getPoolContractId() { return poolContractId; }
    public int getTotalTickets() { return totalTickets; }
    public int getNextIndex() { return nextIndex; }
    public MetadataVisibility getMetadataVisibility() { return metadataVisibility; }

    public void setNextIndex(int nextIndex) { this.nextIndex = nextIndex; }
}
