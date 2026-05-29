package com.luckledger.api;

import com.luckledger.api.persistence.DealerEntity;
import com.luckledger.distribution.AllocationQuartile;
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
        return gameStore.dealers().stream().map(this::dto).toList();
    }

    @GetMapping("/{dealerId}")
    public DealerDto get(@PathVariable UUID dealerId) {
        return dto(gameStore.dealer(dealerId));
    }

    private DealerDto dto(DealerEntity dealer) {
        return new DealerDto(
                dealer.getId(),
                dealer.getName(),
                dealer.getTier().name(),
                AllocationQuartile.fromTier(dealer.getTier()).name(),
                gameStore.activeBookCount(dealer.getId()),
                dealer.getBooksDepleted());
    }

    public record DealerDto(
            UUID dealerId, String name, String tier, String quartile, int activeBooks, int booksDepleted) {}
}
