package com.luckledger.api;

import com.luckledger.api.persistence.PlayerEntity;
import com.luckledger.api.persistence.PlayerMapper;
import com.luckledger.api.persistence.PlayerRepository;
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
 */
@Service
public class RevealGateway {

    private final PlayerRepository players;
    private final TicketRepository tickets;
    private final TransactionRecorder recorder;

    public RevealGateway(PlayerRepository players, TicketRepository tickets, TransactionRecorder recorder) {
        this.players = players;
        this.tickets = tickets;
        this.recorder = recorder;
    }

    /** The outcome of a reveal, enough for the masked/revealed ticket view. */
    public record RevealOutcome(UUID ticketId, MechanicType mechanicType, boolean winner, BigDecimal prizeAmount) {}

    /**
     * Reveals a ticket, crediting {@code playerId} and recording a WIN if it wins. Idempotent per ticket.
     *
     * @throws NoSuchElementException if the ticket (or, for a winning reveal, the player) does not exist
     */
    @Transactional
    public RevealOutcome reveal(UUID ticketId, UUID playerId) {
        Objects.requireNonNull(ticketId, "ticketId");
        Objects.requireNonNull(playerId, "playerId");

        TicketEntity ticket = tickets.findById(ticketId)
                .orElseThrow(() -> new NoSuchElementException("no ticket with id " + ticketId));
        if (ticket.isRevealed()) {
            return new RevealOutcome(
                    ticketId, ticket.getMechanicType(), Boolean.TRUE.equals(ticket.getRevealedIsWinner()),
                    ticket.getRevealedPrize());
        }

        BigDecimal prize = ticket.getPrizeAmount();
        boolean winner = prize.signum() > 0;
        if (winner) {
            PlayerEntity playerEntity = players.findById(playerId)
                    .orElseThrow(() -> new NoSuchElementException("no player with id " + playerId));
            Player player = PlayerMapper.toDomain(playerEntity);
            player.credit(prize);
            PlayerMapper.applyTo(player, playerEntity);
            players.save(playerEntity);

            recorder.record(new Transaction(
                    UUID.randomUUID(), playerId, TransactionType.WIN, prize, null, null, ticketId, Instant.now()));
        }

        ticket.markRevealed(winner, prize);
        tickets.save(ticket);
        return new RevealOutcome(ticketId, ticket.getMechanicType(), winner, prize);
    }
}
