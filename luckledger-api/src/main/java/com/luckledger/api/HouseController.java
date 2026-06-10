package com.luckledger.api;

import com.luckledger.api.persistence.GameEntity;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.api.persistence.TicketRepository.GameTicketStats;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The operator's ("house") side of the table, deliberately public: per-game pool economics —
 * predetermined prize fund vs maximum revenue, tickets sold, books active/depleted — and the global
 * revenue/payout totals. A real lottery operator guards these numbers; an awareness simulator
 * surfaces them, because seeing that every pool is built to keep a fixed share IS the lesson.
 * Read-only; there is no admin mutation surface (and no auth in the project yet).
 */
@RestController
@RequestMapping("/api/house")
public class HouseController {

    private final GameStore gameStore;
    private final TicketRepository tickets;
    private final TicketBookRepository books;
    private final PlayerRepository players;

    public HouseController(GameStore gameStore, TicketRepository tickets, TicketBookRepository books,
            PlayerRepository players) {
        this.gameStore = gameStore;
        this.tickets = tickets;
        this.books = books;
        this.players = players;
    }

    @GetMapping("/overview")
    public HouseOverview overview() {
        Map<UUID, GameTicketStats> statsByGame = tickets.aggregateByGame().stream()
                .collect(Collectors.toMap(GameTicketStats::getGameId, Function.identity()));
        Map<UUID, List<TicketBookEntity>> booksByGame = books.findAll().stream()
                .collect(Collectors.groupingBy(TicketBookEntity::getGameId));

        List<HouseGame> games = gameStore.games().stream()
                .map(g -> houseGame(g, statsByGame.get(g.getId()), booksByGame.getOrDefault(g.getId(), List.of())))
                .toList();

        long ticketsSold = games.stream().mapToLong(HouseGame::ticketsSold).sum();
        long ticketsRevealed = games.stream().mapToLong(HouseGame::ticketsRevealed).sum();
        BigDecimal revenue = sum(games, HouseGame::revenue);
        BigDecimal paidOut = sum(games, HouseGame::paidOut);
        int activeBooks = games.stream().mapToInt(g -> g.books().active()).sum();
        int depletedBooks = games.stream().mapToInt(g -> g.books().depleted()).sum();

        return new HouseOverview(
                new Totals(players.count(), ticketsSold, ticketsRevealed, revenue, paidOut,
                        revenue.subtract(paidOut), activeBooks, depletedBooks),
                games);
    }

    private HouseGame houseGame(GameEntity game, GameTicketStats stats, List<TicketBookEntity> gameBooks) {
        long total = stats == null ? game.getTotalTickets() : stats.getTotalTickets();
        long sold = stats == null ? 0 : stats.getSoldTickets();
        long revealed = stats == null ? 0 : stats.getRevealedTickets();
        BigDecimal prizeFund = stats == null ? BigDecimal.ZERO : stats.getPrizeFund();
        BigDecimal topPrize = stats == null ? BigDecimal.ZERO : stats.getTopPrize();
        BigDecimal paidOut = stats == null ? BigDecimal.ZERO : stats.getPaidOut();
        BigDecimal revenue = game.getTicketPrice().multiply(BigDecimal.valueOf(sold));
        BigDecimal maxRevenue = game.getTicketPrice().multiply(BigDecimal.valueOf(total));

        int allocated = (int) gameBooks.stream().filter(b -> b.getDealerId() != null).count();
        int active = (int) gameBooks.stream()
                .filter(b -> b.getDealerId() != null && b.getNextIndex() < b.getTotalTickets())
                .count();

        return new HouseGame(
                game.getId(),
                DealerController.gameName(game),
                game.getMechanicType().name(),
                game.getTicketPrice(),
                game.getPayoutRatio(),
                game.isVerificationPassed(),
                game.getNearMiss().nearMissRate(),
                total,
                sold,
                total - sold,
                revealed,
                topPrize,
                prizeFund,
                maxRevenue,
                revenue,
                paidOut,
                new BookCounts(gameBooks.size(), allocated, active, allocated - active),
                game.getGenerationTimeMs(),
                game.getCreatedAt());
    }

    private static BigDecimal sum(List<HouseGame> games, Function<HouseGame, BigDecimal> field) {
        return games.stream().map(field).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Everything the house sees, in one read. */
    public record HouseOverview(Totals totals, List<HouseGame> games) {}

    public record Totals(long players, long ticketsSold, long ticketsRevealed, BigDecimal revenue,
            BigDecimal paidOut, BigDecimal houseProfit, int activeBooks, int depletedBooks) {}

    /**
     * One game's pool economics. {@code prizeFund} (what all tickets together were built to pay) vs
     * {@code maxRevenue} (price × every ticket) restates the payout ratio in coins — fixed before
     * the first sale.
     */
    public record HouseGame(UUID gameId, String gameName, String mechanic, BigDecimal ticketPrice,
            BigDecimal payoutRatio, boolean verificationPassed, double nearMissRate, long totalTickets,
            long ticketsSold, long ticketsRemaining, long ticketsRevealed, BigDecimal topPrize,
            BigDecimal prizeFund, BigDecimal maxRevenue, BigDecimal revenue, BigDecimal paidOut,
            BookCounts books, long generationTimeMs, Instant createdAt) {}

    public record BookCounts(int total, int allocated, int active, int depleted) {}
}
