package com.luckledger.api.persistence;

import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.scratch.TicketStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping for a single ticket. The mechanic grid and themed grid are stored as {@code jsonb}; the
 * predetermined {@code prizeAmount} is the source of truth at reveal (the grid is never re-evaluated).
 * Reveal state ({@code revealed}, {@code revealedIsWinner}, {@code revealedPrize}) is filled in when
 * the player scratches; it is null/false until then.
 */
@Entity
@Table(name = "ticket")
public class TicketEntity {

    @Id
    private UUID id;

    @Column(name = "book_id")
    private UUID bookId;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(name = "outcome_id", nullable = false)
    private UUID outcomeId;

    @Enumerated(EnumType.STRING)
    @Column(name = "mechanic_type", nullable = false, length = 40)
    private MechanicType mechanicType;

    @Column(name = "prize_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal prizeAmount;

    @Column(name = "position_in_book")
    private Integer positionInBook;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TicketStatus status;

    /** The buyer; null until the ticket is sold. Lets a player recover unscratched tickets. */
    @Column(name = "player_id")
    private UUID playerId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(nullable = false, columnDefinition = "jsonb")
    private GridCodec.GridDto grid;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "skinned_grid", nullable = false, columnDefinition = "jsonb")
    private GridCodec.ThemedGridDto skinnedGrid;

    @Column(nullable = false)
    private boolean revealed;

    @Column(name = "revealed_is_winner")
    private Boolean revealedIsWinner;

    @Column(name = "revealed_prize", precision = 19, scale = 4)
    private BigDecimal revealedPrize;

    /**
     * Commit-reveal proof: SHA-256 over the ticket's mechanic grid (see {@link GridCommitment}). Public
     * from purchase — it is served on the masked pre-reveal view. Null for legacy rows generated before
     * the scheme existed.
     */
    @Column(name = "grid_commitment", length = 64)
    private String gridCommitment;

    /**
     * The salt feeding {@link #gridCommitment}. <strong>Secret until reveal</strong>: it must never be
     * serialized on a pre-reveal view, or a client could brute-force the small grid space from the
     * commitment and learn the outcome before scratching. Null for legacy rows.
     */
    @Column(name = "commitment_salt", length = 32)
    private String commitmentSalt;

    protected TicketEntity() {}

    public TicketEntity(UUID id, UUID bookId, UUID gameId, UUID outcomeId, MechanicType mechanicType,
            BigDecimal prizeAmount, Integer positionInBook, TicketStatus status, GridCodec.GridDto grid,
            GridCodec.ThemedGridDto skinnedGrid) {
        this(id, bookId, gameId, outcomeId, mechanicType, prizeAmount, positionInBook, status, grid,
                skinnedGrid, null, null);
    }

    public TicketEntity(UUID id, UUID bookId, UUID gameId, UUID outcomeId, MechanicType mechanicType,
            BigDecimal prizeAmount, Integer positionInBook, TicketStatus status, GridCodec.GridDto grid,
            GridCodec.ThemedGridDto skinnedGrid, String gridCommitment, String commitmentSalt) {
        this.id = id;
        this.bookId = bookId;
        this.gameId = gameId;
        this.outcomeId = outcomeId;
        this.mechanicType = mechanicType;
        this.prizeAmount = prizeAmount;
        this.positionInBook = positionInBook;
        this.status = status;
        this.grid = grid;
        this.skinnedGrid = skinnedGrid;
        this.gridCommitment = gridCommitment;
        this.commitmentSalt = commitmentSalt;
        this.revealed = false;
    }

    public UUID getId() { return id; }
    public UUID getBookId() { return bookId; }
    public UUID getGameId() { return gameId; }
    public UUID getOutcomeId() { return outcomeId; }
    public MechanicType getMechanicType() { return mechanicType; }
    public BigDecimal getPrizeAmount() { return prizeAmount; }
    public Integer getPositionInBook() { return positionInBook; }
    public TicketStatus getStatus() { return status; }
    public UUID getPlayerId() { return playerId; }
    public GridCodec.GridDto getGrid() { return grid; }
    public GridCodec.ThemedGridDto getSkinnedGrid() { return skinnedGrid; }
    public boolean isRevealed() { return revealed; }
    public Boolean getRevealedIsWinner() { return revealedIsWinner; }
    public BigDecimal getRevealedPrize() { return revealedPrize; }
    public String getGridCommitment() { return gridCommitment; }
    public String getCommitmentSalt() { return commitmentSalt; }

    public void setStatus(TicketStatus status) { this.status = status; }

    /** Records the buyer at sale time, in the same transaction that marks the ticket SOLD. */
    public void setPlayerId(UUID playerId) { this.playerId = playerId; }

    /** Marks the ticket revealed and records the outcome. Idempotent callers should check first. */
    public void markRevealed(boolean isWinner, BigDecimal prize) {
        this.revealed = true;
        this.revealedIsWinner = isWinner;
        this.revealedPrize = prize;
        this.status = TicketStatus.REVEALED;
    }
}
