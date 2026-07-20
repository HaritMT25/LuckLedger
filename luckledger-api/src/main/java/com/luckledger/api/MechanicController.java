package com.luckledger.api;

import com.luckledger.api.persistence.GridCodec;
import com.luckledger.domain.mechanic.Grid;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.LoserLayout;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.mechanic.CelestialFortuneEvaluator;
import com.luckledger.mechanic.CelestialFortuneMechanic;
import com.luckledger.mechanic.CelestialFortunePopulator;
import com.luckledger.mechanic.DemonSealEvaluator;
import com.luckledger.mechanic.DemonSealMechanic;
import com.luckledger.mechanic.DemonSealPopulator;
import com.luckledger.mechanic.GameMechanic;
import com.luckledger.mechanic.GridUtils;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Random;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Read-only metadata about the implemented game mechanics.
 *
 * <p>The list endpoint gives a thin summary; the detail endpoint ({@code GET /api/mechanics/{type}})
 * exposes the full prize ladder (read from the real evaluators, not hardcoded copy) plus two example
 * grids — one winner, one loser — built once at construction time from a deterministically seeded
 * populator so the documentation never shifts between requests. Seeing the ladder is purely
 * educational: it is fixed at print time and does not change the sealed pool.
 */
@RestController
@RequestMapping("/api/mechanics")
public class MechanicController {

    private static final List<GameMechanic> MECHANICS =
            List.of(new CelestialFortuneMechanic(), new DemonSealMechanic());

    /** Fixed seed so the example grids are identical on every request. */
    private static final long EXAMPLE_SEED = 42L;

    private final Map<MechanicType, MechanicDetail> details;

    public MechanicController() {
        this.details = buildDetails();
    }

    /**
     * Lists the implemented mechanics with a thin summary (type and symbol-pool size).
     *
     * @return one summary per implemented mechanic
     */
    @GetMapping
    public List<MechanicSummary> list() {
        return MECHANICS.stream()
                .map(m -> new MechanicSummary(m.getType().name(), m.getDefaultSymbolPool().size()))
                .toList();
    }

    /**
     * Returns the full detail for one mechanic: display name, grid dimension, description, the prize
     * ladder derived from the evaluator, and two deterministic example grids (a win and a loss).
     *
     * @param type the mechanic type name (e.g. {@code CELESTIAL_FORTUNE}); resolved case-sensitively
     * @return the mechanic detail
     * @throws NoSuchElementException if {@code type} names no implemented mechanic (yields a 404)
     */
    @GetMapping("/{type}")
    public MechanicDetail detail(@PathVariable String type) {
        for (Map.Entry<MechanicType, MechanicDetail> entry : details.entrySet()) {
            if (entry.getKey().name().equals(type)) {
                return entry.getValue();
            }
        }
        throw new NoSuchElementException("no implemented mechanic named " + type);
    }

    private static Map<MechanicType, MechanicDetail> buildDetails() {
        Map<MechanicType, MechanicDetail> map = new LinkedHashMap<>();
        map.put(MechanicType.CELESTIAL_FORTUNE, celestialDetail());
        map.put(MechanicType.DEMON_SEAL, demonDetail());
        return map;
    }

    private static MechanicDetail celestialDetail() {
        List<WinRule> rules = new ArrayList<>();
        for (int matches = 2; matches <= CelestialFortuneEvaluator.WINNING_NUMBER_COUNT; matches++) {
            BigDecimal prize = CelestialFortuneEvaluator.prizeForMatches(matches);
            rules.add(new WinRule(
                    matches,
                    scale(prize),
                    "Match %d of the %d winning numbers → %s coins"
                            .formatted(matches, CelestialFortuneEvaluator.WINNING_NUMBER_COUNT, coins(prize))));
        }

        CelestialFortuneMechanic mechanic = new CelestialFortuneMechanic();
        CelestialFortunePopulator populator =
                new CelestialFortunePopulator(new GridUtils(new Random(EXAMPLE_SEED)));
        List<String> pool = mechanic.getDefaultSymbolPool();
        Grid win = populator.populate(GridSize.FOUR, 20.0, pool, LoserLayout.CLEAN);
        Grid loss = populator.populate(GridSize.FOUR, 0.0, pool, LoserLayout.CLEAN);

        return new MechanicDetail(
                MechanicType.CELESTIAL_FORTUNE.name(),
                "Celestial Fortune",
                GridSize.FOUR.dimension(),
                "Pick numbers and match them against the four winning numbers on the ticket. Match two or "
                        + "more to win — the more you match, the bigger the prize. The whole prize pool was fixed "
                        + "when the cards were printed; the ladder below never changes.",
                rules,
                GridCodec.toDto(win),
                GridCodec.toDto(loss));
    }

    private static MechanicDetail demonDetail() {
        List<WinRule> rules = new ArrayList<>();
        for (int points = 4; points <= 2 * DemonSealEvaluator.SEAL_COUNT; points++) {
            BigDecimal prize = DemonSealEvaluator.prizeForPoints(points);
            rules.add(new WinRule(
                    points,
                    scale(prize),
                    "Reach %d seal points → %s coins".formatted(points, coins(prize))));
        }

        DemonSealMechanic mechanic = new DemonSealMechanic();
        DemonSealPopulator populator = new DemonSealPopulator(new Random(EXAMPLE_SEED));
        List<String> pool = mechanic.getDefaultSymbolPool();
        Grid win = populator.populate(GridSize.THREE, 10.0, pool, LoserLayout.CLEAN);
        Grid loss = populator.populate(GridSize.THREE, 0.0, pool, LoserLayout.CLEAN);

        return new MechanicDetail(
                MechanicType.DEMON_SEAL.name(),
                "Demon Seal",
                GridSize.THREE.dimension(),
                "Reveal six seals. Each gold seal is worth 2 points and each silver seal 1; broken seals "
                        + "score nothing. Reach four points to seal the demon and win. The ladder below was set at "
                        + "print time and never changes.",
                rules,
                GridCodec.toDto(win),
                GridCodec.toDto(loss));
    }

    /** A whole-coin prize amount for a human line (drops trailing scale zeros). */
    private static String coins(BigDecimal prize) {
        return prize.signum() == 0 ? "0" : prize.stripTrailingZeros().toPlainString();
    }

    /** Money at the API boundary: fixed scale, half-up. */
    private static BigDecimal scale(BigDecimal amount) {
        return amount.setScale(4, RoundingMode.HALF_UP);
    }

    public record MechanicSummary(String type, int symbolPoolSize) {}

    /**
     * The full public description of one mechanic.
     *
     * @param type          the mechanic type name
     * @param displayName   the human-facing game name
     * @param gridDimension the square grid's side length
     * @param description   a short educational blurb
     * @param winRules      the prize ladder, read from the evaluator
     * @param exampleWin    a deterministic winning grid
     * @param exampleLoss   a deterministic losing grid
     */
    public record MechanicDetail(
            String type,
            String displayName,
            int gridDimension,
            String description,
            List<WinRule> winRules,
            GridCodec.GridDto exampleWin,
            GridCodec.GridDto exampleLoss) {}

    /**
     * One rung of a mechanic's prize ladder.
     *
     * @param threshold   the matches (Celestial) or seal points (Demon) this rung requires
     * @param prize       the coins this rung pays
     * @param description a human-readable line describing the rung
     */
    public record WinRule(int threshold, BigDecimal prize, String description) {}
}
