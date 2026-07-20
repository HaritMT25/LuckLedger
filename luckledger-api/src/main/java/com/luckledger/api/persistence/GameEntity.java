package com.luckledger.api.persistence;

import com.luckledger.domain.generation.NearMissMode;
import com.luckledger.domain.generation.verification.NearMissReport;
import com.luckledger.domain.generation.verification.VerificationReport;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping for a generated game: its mechanic, theme, economics, distribution shape, and the
 * verification/near-miss evidence the batch passed with. One row per seeded game.
 */
@Entity
@Table(name = "game")
public class GameEntity {

    @Id
    private UUID id;

    /** Operator-chosen campaign name. Null for legacy rows (displayed from the mechanic instead). */
    @Column(name = "name", length = 120)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "mechanic_type", nullable = false, length = 40)
    private MechanicType mechanicType;

    @Column(name = "theme_id", nullable = false)
    private String themeId;

    @Column(name = "ticket_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal ticketPrice;

    @Column(name = "total_tickets", nullable = false)
    private int totalTickets;

    @Column(name = "payout_ratio", nullable = false, precision = 19, scale = 4)
    private BigDecimal payoutRatio;

    @Column(name = "book_count", nullable = false)
    private int bookCount;

    @Column(name = "dealer_count", nullable = false)
    private int dealerCount;

    /** The mode the pool was generated with (drives the awareness layer's near-miss narration). */
    @Enumerated(EnumType.STRING)
    @Column(name = "near_miss_mode", nullable = false, length = 20)
    private NearMissMode nearMissMode;

    /** Lifecycle state: ACTIVE (sellable/restockable) or RETIRED (withdrawn). Never null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private GameStatus status;

    /**
     * The pool contract this game was generated from, so a restock can regenerate identical economics.
     * Null for legacy rows (restock falls back to the ApiConfig statics). Stored as a persistence
     * document, never the domain {@link com.luckledger.domain.pool.PoolContract} directly.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "pool_contract", columnDefinition = "jsonb")
    private PoolContractDoc poolContract;

    @Column(name = "verification_passed", nullable = false)
    private boolean verificationPassed;

    @Column(name = "generation_time_ms", nullable = false)
    private long generationTimeMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "near_miss", nullable = false, columnDefinition = "jsonb")
    private NearMissReport nearMiss;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "verification_report", nullable = false, columnDefinition = "jsonb")
    private VerificationReport verificationReport;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected GameEntity() {}

    /**
     * Backwards-compatible constructor for a legacy (unnamed) game: no campaign name, no persisted pool
     * contract, and {@link GameStatus#ACTIVE}. Retained so the existing seed/round-trip callers compile
     * and behave exactly as before.
     */
    public GameEntity(UUID id, MechanicType mechanicType, String themeId, BigDecimal ticketPrice, int totalTickets,
            BigDecimal payoutRatio, int bookCount, int dealerCount, NearMissMode nearMissMode,
            boolean verificationPassed, long generationTimeMs,
            NearMissReport nearMiss, VerificationReport verificationReport, Instant createdAt) {
        this(id, null, mechanicType, themeId, ticketPrice, totalTickets, payoutRatio, bookCount, dealerCount,
                nearMissMode, GameStatus.ACTIVE, null, verificationPassed, generationTimeMs, nearMiss,
                verificationReport, createdAt);
    }

    /** Full constructor carrying the campaign name, lifecycle status, and persisted pool contract. */
    public GameEntity(UUID id, String name, MechanicType mechanicType, String themeId, BigDecimal ticketPrice,
            int totalTickets, BigDecimal payoutRatio, int bookCount, int dealerCount, NearMissMode nearMissMode,
            GameStatus status, PoolContractDoc poolContract, boolean verificationPassed, long generationTimeMs,
            NearMissReport nearMiss, VerificationReport verificationReport, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.mechanicType = mechanicType;
        this.themeId = themeId;
        this.ticketPrice = ticketPrice;
        this.totalTickets = totalTickets;
        this.payoutRatio = payoutRatio;
        this.bookCount = bookCount;
        this.dealerCount = dealerCount;
        this.nearMissMode = nearMissMode;
        this.status = status;
        this.poolContract = poolContract;
        this.verificationPassed = verificationPassed;
        this.generationTimeMs = generationTimeMs;
        this.nearMiss = nearMiss;
        this.verificationReport = verificationReport;
        this.createdAt = createdAt;
    }

    /** Folds a restock cycle's new batch into the running totals; nothing else changes. */
    public void recordRestock(int ticketsAdded, int booksAdded) {
        this.totalTickets += ticketsAdded;
        this.bookCount += booksAdded;
    }

    /** Flips the lifecycle status (retire/activate). The only mutable field — nothing else changes. */
    public void setStatus(GameStatus status) { this.status = status; }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public GameStatus getStatus() { return status; }
    public PoolContractDoc getPoolContract() { return poolContract; }
    public MechanicType getMechanicType() { return mechanicType; }
    public String getThemeId() { return themeId; }
    public BigDecimal getTicketPrice() { return ticketPrice; }
    public int getTotalTickets() { return totalTickets; }
    public BigDecimal getPayoutRatio() { return payoutRatio; }
    public int getBookCount() { return bookCount; }
    public int getDealerCount() { return dealerCount; }
    public NearMissMode getNearMissMode() { return nearMissMode; }
    public boolean isVerificationPassed() { return verificationPassed; }
    public long getGenerationTimeMs() { return generationTimeMs; }
    public NearMissReport getNearMiss() { return nearMiss; }
    public VerificationReport getVerificationReport() { return verificationReport; }
    public Instant getCreatedAt() { return createdAt; }
}
