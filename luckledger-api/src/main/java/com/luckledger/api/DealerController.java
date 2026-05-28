package com.luckledger.api;

import com.luckledger.distribution.Dealer;
import java.util.List;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only access to the NPC dealers across all seeded games. */
@RestController
@RequestMapping("/api/dealers")
public class DealerController {

    private final GameStore gameStore;

    public DealerController(GameStore gameStore) {
        this.gameStore = gameStore;
    }

    @GetMapping
    public List<DealerDto> list() {
        return gameStore.dealers().stream().map(DealerController::dto).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerDto get(@PathVariable UUID dealerId) {
        return dto(gameStore.dealer(dealerId));
    }

    private static DealerDto dto(Dealer dealer) {
        return new DealerDto(
                dealer.dealerId(),
                dealer.name(),
                dealer.tier().name(),
                dealer.getAllocationQuartile().name(),
                dealer.activeBooks().size(),
                dealer.booksDepleted());
    }

    public record DealerDto(
            UUID dealerId, String name, String tier, String quartile, int activeBooks, int booksDepleted) {}
}
