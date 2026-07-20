package com.luckledger.api;

import com.luckledger.api.persistence.GridCodec;
import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerMapper;
import com.luckledger.api.persistence.PlayerRepository;
import com.luckledger.api.persistence.TicketBookEntity;
import com.luckledger.api.persistence.TicketBookRepository;
import com.luckledger.api.persistence.TicketEntity;
import com.luckledger.api.persistence.TicketRepository;
import com.luckledger.domain.ledger.Transaction;
import com.luckledger.domain.ledger.TransactionType;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.player.Player;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import com.luckledger.player.ledger.TransactionRecorder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reveal half of the scratch flow, persisted: a player scratches a previously-bought ticket. The
 * win is decided from the ticket's predetermined {@code prizeAmount} (verified at generation time) —
 * the grid is never re-evaluated. A winning reveal credits the player and appends a {@code WIN} to the
 * ledger.
 *
 * <p>Reveal is <strong>idempotent</strong>: the first reveal flips the ticket's persisted reveal flags;
 * any later reveal returns those stored flags without crediting or recording again, keeping the ledger
 * append-only and preventing double payouts.
 *
 * <p><strong>Ownership:</strong> reveal is where money moves into an account, so it verifies the
 * caller owns the ticket. An unsold ticket has no owner ({@code 409}); a ticket owned by someone else
 * is refused ({@code 403}). Purchase itself is <em>not</em> gated: players are anonymous, so the buyer
 * simply is whoever holds the id — there is no foreign account to protect at buy time.
 *
 * <p><strong>Lock order (writer rule): ticket first, player LAST.</strong> The ticket row is taken
 * under a pessimistic write lock so the check-then-act on the reveal flags is atomic — two threads
 * racing to scratch the same ticket cannot both credit. On the winning path the player row is locked
 * afterwards, matching the order every other writer uses, so deadlock is impossible.
 */
@Service
public class RevealGateway {

    private final PlayerRepository players;
    private final TicketRepository tickets;
    private final TicketBookRepository books;
    private final TransactionRecorder recorder;

    public RevealGateway(PlayerRepository players, TicketRepository tickets, TicketBookRepository books,
            TransactionRecorder recorder) {
        this.players = players;
        this.tickets = tickets;
        this.books = books;
        this.recorder = recorder;
    }

    /**
     * The outcome of a reveal: the win/prize flags, the ticket's themed grid (so the frontend can draw
     * the player's real symbols under the scratch coating), and the underlying mechanic {@code grid}
     * (so the reveal narrative can be derived from it). Both grids were verified at generation time;
     * serving them after reveal leaks nothing about unsold tickets.
     */
    public record RevealOutcome(UUID ticketId, UUID gameId, MechanicType mechanicType, boolean winner,
            BigDecimal prizeAmount, GridCodec.ThemedGridDto skinnedGrid, GridCodec.GridDto grid) {}

    /**
     * Reveals a ticket, crediting {@code playerId} and recording a WIN if it wins. Idempotent per ticket.
     *
     * @throws NoSuchElementException if the ticket (or, for a winning reveal, the player) does not exist
     * @throws IllegalStateException if the ticket was never sold (has no owner)
     * @throws TicketOwnershipException if {@code playerId} is not the ticket's buyer
     */
    @Transactional
    public RevealOutcome reveal(UUID ticketId, UUID playerId) {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(playerId, "playerId");

        // Lock the ticket FIRST so the not-yet-revealed check-then-act cannot race a second scratch.
        TicketEntity ticket = tickets.findByIdForUpdate(ticketId)
                .orElseThrow(() -> new NoSuchElementException("no ticket with id " + ticketId));

        UUID owner = ticket.getPlayerId();
        if (owner == null) {
            throw new IllegalStateException("ticket " + ticketId + " has not been sold");
        }
        if (!owner.equals(playerId)) {
            throw new TicketOwnershipException(ticketId, owner, playerId);
        }

        if (ticket.isRevealed()) {
            return new RevealOutcome(
                    ticketId, ticket.getGameId(), ticket.getMechanicType(),
                    Boolean.TRUE.equals(ticket.getRevealedIsWinner()), ticket.getRevealedPrize(),
                    ticket.getSkinnedGrid(), ticket.getGrid());
        }

        BigDecimal prize = ticket.getPrizeAmount();
        boolean winner = prize.signum() > 0;
        if (winner) {
            // Attribute the WIN to the shop and book the ticket came from, so the dealer-comparison and
            // lucky-store insights can see winnings by shop. Old rows keep their nulls (append-only).
            UUID bookId = ticket.getBookId();
            UUID dealerId = bookId == null ? null : books.findById(bookId)
                    .map(TicketBookEntity::getDealerId)
                    .orElse(null);

            // Player locked LAST, after the ticket, per the writer lock-order rule.
            PlayerEntity playerEntity = players.findByIdForUpdate(playerId)
                    .orElseThrow(() -> new NoSuchElementException("no player with id " + playerId));
            Player player = PlayerMapper.toDomain(playerEntity);
            player.credit(prize);
            PlayerMapper.applyTo(player, playerEntity);
            players.save(playerEntity);

            recorder.record(new Transaction(
                    UUID.randomUUID(), playerId, TransactionType.WIN, prize, dealerId, bookId, ticketId,
                    Instant.now()));
        }

        ticket.markRevealed(winner, prize);
        tickets.save(ticket);
        return new RevealOutcome(
                ticketId, ticket.getGameId(), ticket.getMechanicType(), winner, prize,
                ticket.getSkinnedGrid(), ticket.getGrid());
    }
}
