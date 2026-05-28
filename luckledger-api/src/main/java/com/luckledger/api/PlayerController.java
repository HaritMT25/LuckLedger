package com.luckledger.api;

import com.luckledger.domain.player.Player;
import com.luckledger.player.bank.BankService;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Player lifecycle: create a player, read their state, and borrow free coins from the bank. */
@RestController
@RequestMapping("/api/players")
public class PlayerController {

    private final PlayerRegistry players;
    private final BankService bankService;

    public PlayerController(PlayerRegistry players, BankService bankService) {
        this.players = players;
        this.bankService = bankService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PlayerDto create(@RequestBody CreatePlayerRequest request) {
        return dto(players.create(request.displayName()));
    }

    @GetMapping("/{playerId}")
    public PlayerDto get(@PathVariable UUID playerId) {
        return dto(players.get(playerId));
    }

    @PostMapping("/{playerId}/borrow")
    public PlayerDto borrow(@PathVariable UUID playerId, @RequestBody BorrowRequest request) {
        Player player = players.get(playerId);
        bankService.borrow(player, request.amount());
        return dto(player);
    }

    private static PlayerDto dto(Player p) {
        return new PlayerDto(
                p.getPlayerId(),
                p.getDisplayName(),
                p.getCoinBalance(),
                p.getTotalBorrowed(),
                p.getTotalSpent(),
                p.getTotalWon(),
                p.getNetPosition());
    }

    public record CreatePlayerRequest(String displayName) {}

    public record BorrowRequest(BigDecimal amount) {}

    public record PlayerDto(
            UUID playerId,
            String displayName,
            BigDecimal coinBalance,
            BigDecimal totalBorrowed,
            BigDecimal totalSpent,
            BigDecimal totalWon,
            BigDecimal netPosition) {}
}
