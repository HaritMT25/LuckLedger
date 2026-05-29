package com.luckledger.api.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** JPA mapping for a player's persistent state (running totals + balance). */
@Entity
@Table(name = "player")
public class PlayerEntity {

    @Id
    private UUID id;

    @Column(name = "display_name", nullable = false)
    private String displayName;

    @Column(name = "coin_balance", nullable = false, precision = 19, scale = 4)
    private BigDecimal coinBalance;

    @Column(name = "total_borrowed", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalBorrowed;

    @Column(name = "total_spent", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalSpent;

    @Column(name = "total_won", nullable = false, precision = 19, scale = 4)
    private BigDecimal totalWon;

    @Column(name = "ticket_count", nullable = false)
    private int ticketCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PlayerEntity() {}

    public PlayerEntity(UUID id, String displayName, BigDecimal coinBalance, BigDecimal totalBorrowed,
            BigDecimal totalSpent, BigDecimal totalWon, int ticketCount, Instant createdAt) {
        this.id = id;
        this.displayName = displayName;
        this.coinBalance = coinBalance;
        this.totalBorrowed = totalBorrowed;
        this.totalSpent = totalSpent;
        this.totalWon = totalWon;
        this.ticketCount = ticketCount;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getDisplayName() { return displayName; }
    public BigDecimal getCoinBalance() { return coinBalance; }
    public BigDecimal getTotalBorrowed() { return totalBorrowed; }
    public BigDecimal getTotalSpent() { return totalSpent; }
    public BigDecimal getTotalWon() { return totalWon; }
    public int getTicketCount() { return ticketCount; }
    public Instant getCreatedAt() { return createdAt; }

    public void setDisplayName(String displayName) { this.displayName = displayName; }
    public void setCoinBalance(BigDecimal coinBalance) { this.coinBalance = coinBalance; }
    public void setTotalBorrowed(BigDecimal totalBorrowed) { this.totalBorrowed = totalBorrowed; }
    public void setTotalSpent(BigDecimal totalSpent) { this.totalSpent = totalSpent; }
    public void setTotalWon(BigDecimal totalWon) { this.totalWon = totalWon; }
    public void setTicketCount(int ticketCount) { this.ticketCount = ticketCount; }
}
