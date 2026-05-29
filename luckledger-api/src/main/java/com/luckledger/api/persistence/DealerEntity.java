package com.luckledger.api.persistence;

import com.luckledger.distribution.DealerTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

/** JPA mapping for an NPC dealer storefront and its lifetime book-depletion state. */
@Entity
@Table(name = "dealer")
public class DealerEntity {

    @Id
    private UUID id;

    @Column(name = "game_id", nullable = false)
    private UUID gameId;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private DealerTier tier;

    @Column(name = "rank_score", nullable = false)
    private int rankScore;

    @Column(name = "books_per_cycle", nullable = false)
    private int booksPerCycle;

    @Column(name = "books_depleted", nullable = false)
    private int booksDepleted;

    protected DealerEntity() {}

    public DealerEntity(UUID id, UUID gameId, String name, DealerTier tier, int rankScore, int booksPerCycle,
            int booksDepleted) {
        this.id = id;
        this.gameId = gameId;
        this.name = name;
        this.tier = tier;
        this.rankScore = rankScore;
        this.booksPerCycle = booksPerCycle;
        this.booksDepleted = booksDepleted;
    }

    public UUID getId() { return id; }
    public UUID getGameId() { return gameId; }
    public String getName() { return name; }
    public DealerTier getTier() { return tier; }
    public int getRankScore() { return rankScore; }
    public int getBooksPerCycle() { return booksPerCycle; }
    public int getBooksDepleted() { return booksDepleted; }

    public void setTier(DealerTier tier) { this.tier = tier; }
    public void setBooksDepleted(int booksDepleted) { this.booksDepleted = booksDepleted; }
}
