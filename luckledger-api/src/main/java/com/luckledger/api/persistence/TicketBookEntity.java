package com.luckledger.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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

    protected TicketBookEntity() {}

    public TicketBookEntity(UUID id, UUID gameId, UUID dealerId, UUID poolContractId, int totalTickets,
            int nextIndex) {
        this.id = id;
        this.gameId = gameId;
        this.dealerId = dealerId;
        this.poolContractId = poolContractId;
        this.totalTickets = totalTickets;
        this.nextIndex = nextIndex;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public UUID getDealerId() { return dealerId; }
    public UUID getPoolContractId() { return poolContractId; }
    public int getTotalTickets() { return totalTickets; }
    public int getNextIndex() { return nextIndex; }

    public void setNextIndex(int nextIndex) { this.nextIndex = nextIndex; }
}
