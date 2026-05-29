package com.luckledger.api.persistence;

import com.luckledger.distribution.Dealer;
import com.luckledger.distribution.GameSetupResult;
import com.luckledger.distribution.TicketBook;
import com.luckledger.domain.generation.GenerationResult;
import com.luckledger.domain.generation.TicketCard;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.scratch.TicketStatus;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Flattens an in-memory {@link GameSetupResult} (plus the {@link GameConfig} it was built from) into
 * the row graph the persistence layer stores: one {@link GameEntity}, its {@link DealerEntity dealers},
 * every partitioned {@link TicketBookEntity book}, and every {@link TicketEntity ticket}.
 *
 * <p>All books from the partition are persisted, not only allocated ones — an unallocated book gets a
 * null {@code dealerId} (schema-permitted) so that every generated ticket is reachable by id, matching
 * the in-memory store's behaviour. A freshly seeded game's tickets are all {@code AVAILABLE} with a
 * zero sale cursor.
 */
public final class GamePersistenceMapper {

    private GamePersistenceMapper() {}

    /** The full row graph for one game, ready to be saved. */
    public record PersistedGame(
            GameEntity game,
            List<DealerEntity> dealers,
            List<TicketBookEntity> books,
            List<TicketEntity> tickets) {}

    /**
     * Maps a game's setup into its persistable entities.
     *
     * @param gameId the id to assign the game; never {@code null}
     * @param config the config the game was built from; never {@code null}
     * @param setup the generated/partitioned/allocated game; never {@code null}
     * @param createdAt the creation timestamp to stamp on the game; never {@code null}
     */
    public static PersistedGame toPersisted(
            UUID gameId, GameConfig config, GameSetupResult setup, Instant createdAt) {
        GenerationResult generation = setup.generationResult();

        GameEntity game = new GameEntity(
                gameId,
                config.mechanicType(),
                config.themeId(),
                config.poolContract().ticketPrice(),
                generation.tickets().size(),
                config.poolContract().payoutRatio(),
                setup.partitionResult().books().size(),
                setup.dealers().size(),
                generation.verificationReport().passed(),
                generation.generationTimeMs(),
                generation.nearMissReport(),
                generation.verificationReport(),
                createdAt);

        List<DealerEntity> dealers = new ArrayList<>();
        for (Dealer dealer : setup.dealers()) {
            dealers.add(new DealerEntity(
                    dealer.dealerId(),
                    gameId,
                    dealer.name(),
                    dealer.tier(),
                    dealer.rankScore(),
                    dealer.booksPerCycle(),
                    dealer.booksDepleted()));
        }

        // Reverse the allocation map so each book knows its owning dealer (null if unallocated).
        Map<UUID, UUID> bookToDealer = new HashMap<>();
        setup.allocationMap().forEach(
                (dealer, books) -> books.forEach(book -> bookToDealer.put(book.bookId(), dealer.dealerId())));

        List<TicketBookEntity> bookEntities = new ArrayList<>();
        List<TicketEntity> ticketEntities = new ArrayList<>();
        for (TicketBook book : setup.partitionResult().books()) {
            bookEntities.add(new TicketBookEntity(
                    book.bookId(),
                    gameId,
                    bookToDealer.get(book.bookId()),
                    book.poolContractId(),
                    book.getTotalTickets(),
                    book.nextIndex()));

            List<TicketCard> cards = book.tickets();
            for (int position = 0; position < cards.size(); position++) {
                TicketCard card = cards.get(position);
                ticketEntities.add(new TicketEntity(
                        card.ticketId(),
                        book.bookId(),
                        gameId,
                        card.layout().outcomeId(),
                        card.layout().mechanicType(),
                        card.layout().prizeAmount(),
                        position,
                        TicketStatus.AVAILABLE,
                        GridCodec.toDto(card.layout().grid()),
                        GridCodec.toDto(card.skinnedGrid())));
            }
        }

        return new PersistedGame(game, dealers, bookEntities, ticketEntities);
    }
}
