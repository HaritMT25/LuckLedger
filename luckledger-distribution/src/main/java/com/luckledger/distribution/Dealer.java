package com.luckledger.distribution;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * An NPC storefront that receives books and sells their tickets. A dealer's tier (set by the
 * {@link DealerTierResolver} from its lifetime {@code booksDepleted}) decides <em>which</em> slice of
 * the book-value distribution it is eligible for; {@code booksPerCycle} — the same for every dealer —
 * caps <em>how many</em> books it can hold at once (§3.6).
 *
 * <p>This is a mutable entity: {@link #addBook}, {@link #onBookDepleted}, and {@link #setTier} change
 * its state over the game's lifetime.
 */
public final class Dealer {

    private final UUID dealerId;
    private final String name;
    private DealerTier tier;
    private final int rankScore;
    private final int booksPerCycle;
    private final List<TicketBook> activeBooks = new ArrayList<>();
    private int booksDepleted;

    /**
     * @param dealerId stable public id; never {@code null}
     * @param name human-readable name; non-blank
     * @param tier starting tier; never {@code null}
     * @param rankScore an auxiliary ranking metric; {@code >= 0}
     * @param booksPerCycle throughput cap (same for all dealers); {@code >= 1}
     * @param booksDepleted lifetime depleted-book count; {@code >= 0}
     */
    public Dealer(UUID dealerId, String name, DealerTier tier, int rankScore, int booksPerCycle, int booksDepleted) {
        this.dealerId = Objects.requireNonNull(dealerId, "dealerId must not be null");
        Objects.requireNonNull(name, "name must not be null");
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
        if (name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (rankScore < 0) {
            throw new IllegalArgumentException("rankScore must be >= 0, was " + rankScore);
        }
        if (booksPerCycle < 1) {
            throw new IllegalArgumentException("booksPerCycle must be >= 1, was " + booksPerCycle);
        }
        if (booksDepleted < 0) {
            throw new IllegalArgumentException("booksDepleted must be >= 0, was " + booksDepleted);
        }
        this.name = name;
        this.rankScore = rankScore;
        this.booksPerCycle = booksPerCycle;
        this.booksDepleted = booksDepleted;
    }

    public UUID dealerId() {
        return dealerId;
    }

    public String name() {
        return name;
    }

    public DealerTier tier() {
        return tier;
    }

    public int rankScore() {
        return rankScore;
    }

    public int booksPerCycle() {
        return booksPerCycle;
    }

    public int booksDepleted() {
        return booksDepleted;
    }

    /** The books this dealer currently holds (unmodifiable view). */
    public List<TicketBook> activeBooks() {
        return Collections.unmodifiableList(activeBooks);
    }

    /** This dealer's eligible allocation band, derived from its tier. */
    public AllocationQuartile getAllocationQuartile() {
        return AllocationQuartile.fromTier(tier);
    }

    /** Whether the dealer is below its throughput cap and can take another book. */
    public boolean canAcceptBooks() {
        return activeBooks.size() < booksPerCycle;
    }

    /**
     * Adds a book to this dealer's active inventory.
     *
     * @throws IllegalStateException if the dealer is already at its {@code booksPerCycle} cap
     */
    public void addBook(TicketBook book) {
        Objects.requireNonNull(book, "book must not be null");
        if (!canAcceptBooks()) {
            throw new IllegalStateException(
                    "dealer " + dealerId + " is at capacity (" + booksPerCycle + ")");
        }
        activeBooks.add(book);
    }

    /** Records that a held book has been fully sold: increments the lifetime count and drops it. */
    public void onBookDepleted(TicketBook book) {
        Objects.requireNonNull(book, "book must not be null");
        if (activeBooks.remove(book)) {
            booksDepleted++;
        }
    }

    /** Updates the tier; called by {@link DealerTierResolver#resolveAll(List)} each cycle. */
    public void setTier(DealerTier tier) {
        this.tier = Objects.requireNonNull(tier, "tier must not be null");
    }
}
