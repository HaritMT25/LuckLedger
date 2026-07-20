package com.luckledger.api.persistence;

import com.luckledger.distribution.DealerTier;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.List;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * JPA mapping for an NPC <em>shop</em>: a storefront with a name, a human owner, and an (optional)
 * avatar image. A shop may stock several games — {@code stockedGames} lists the games whose books it
 * carries — so the same shop appears once across all games it sells, not once per game. {@code tier},
 * {@code rankScore}, and {@code booksDepleted} are its aggregate lifetime distribution state.
 */
@Entity
@Table(name = "dealer")
public class DealerEntity {

    @Id
    private UUID id;

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    @Column(name = "owner_name", nullable = false)
    private String ownerName;

    /** Placeholder for a future avatar image (URL/path); null means "render initials". */
    @Column(name = "avatar")
    private String avatar;

    /** Ids of the games this shop stocks (the player can buy any of these games' books here). */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "stocked_games", nullable = false, columnDefinition = "jsonb")
    private List<UUID> stockedGames;

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

    public DealerEntity(UUID id, String shopName, String ownerName, String avatar, List<UUID> stockedGames,
            DealerTier tier, int rankScore, int booksPerCycle, int booksDepleted) {
        this.id = id;
        this.shopName = shopName;
        this.ownerName = ownerName;
        this.avatar = avatar;
        this.stockedGames = stockedGames;
        this.tier = tier;
        this.rankScore = rankScore;
        this.booksPerCycle = booksPerCycle;
        this.booksDepleted = booksDepleted;
    }

    public UUID getId() { return id; }
    public String getShopName() { return shopName; }
    public String getOwnerName() { return ownerName; }
    public String getAvatar() { return avatar; }
    public List<UUID> getStockedGames() { return stockedGames; }
    public DealerTier getTier() { return tier; }
    public int getRankScore() { return rankScore; }
    public int getBooksPerCycle() { return booksPerCycle; }
    public int getBooksDepleted() { return booksDepleted; }

    public void setTier(DealerTier tier) { this.tier = tier; }
    public void setBooksDepleted(int booksDepleted) { this.booksDepleted = booksDepleted; }

    /**
     * Adds a game to this shop's stocked set (idempotent), so a newly created campaign's books allocated
     * here are reachable. Replaces the JSONB list with a fresh mutable copy — the field may have been
     * loaded as an immutable list — so the change is picked up on save.
     *
     * @param gameId the game to stock; never {@code null}
     */
    public void addStockedGame(UUID gameId) {
        List<UUID> updated = new java.util.ArrayList<>(stockedGames);
        if (!updated.contains(gameId)) {
            updated.add(gameId);
        }
        this.stockedGames = updated;
    }
}
