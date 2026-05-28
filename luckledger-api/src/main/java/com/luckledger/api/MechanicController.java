package com.luckledger.api;

import com.luckledger.mechanic.CelestialFortuneMechanic;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.GameMechanic;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Read-only metadata about the implemented game mechanics. */
@RestController
@RequestMapping("/api/mechanics")
public class MechanicController {

    private static final List<GameMechanic> MECHANICS =
            List.of(new CelestialFortuneMechanic(), new DemonSealMechanic());

    @GetMapping
    public List<MechanicSummary> list() {
        return MECHANICS.stream()
                .map(m -> new MechanicSummary(m.getType().name(), m.getDefaultSymbolPool().size()))
                .toList();
    }

    public record MechanicSummary(String type, int symbolPoolSize) {}
}
