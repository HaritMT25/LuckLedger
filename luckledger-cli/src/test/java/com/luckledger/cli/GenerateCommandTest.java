package com.luckledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.luckledger.distribution.GameSetupResult;
import com.luckledger.domain.mechanic.GridSize;
import com.luckledger.domain.mechanic.MechanicType;
import com.luckledger.domain.orchestration.GameConfig;
import com.luckledger.domain.generation.theme.ThemeRef;
import com.luckledger.mechanic.GameMechanic;
import org.junit.jupiter.api.Test;

class GenerateCommandTest {

    private static GameSetupResult generate(MechanicType type) {
        GameMechanic mechanic = CliApp.mechanic(type);
        GridSize gridSize = type == MechanicType.CELESTIAL_FORTUNE ? GridSize.FOUR : GridSize.THREE;
        ThemeRef theme = CliApp.defaultTheme(mechanic);
        GameOrchestrator orchestrator = CliApp.buildOrchestrator(mechanic, gridSize, theme);
        GameConfig config = new GameConfig(CliApp.defaultPool(type), type, theme.themeId(), 4, 3);
        return new GenerateCommand(orchestrator).generate(config);
    }

    @Test
    void demonSealGenerationIsVerified() {
        GameSetupResult result = generate(MechanicType.DEMON_SEAL);

        assertThat(result.generationResult().verificationReport().passed()).isTrue();
        assertThat(result.generationResult().tickets()).hasSize(20);
        assertThat(result.dealers()).hasSize(3);
    }

    @Test
    void celestialFortuneGenerationIsVerified() {
        GameSetupResult result = generate(MechanicType.CELESTIAL_FORTUNE);

        assertThat(result.generationResult().verificationReport().passed()).isTrue();
        assertThat(result.generationResult().tickets()).hasSize(200);
    }

    @Test
    void nullConfigIsRejected() {
        GameMechanic mechanic = CliApp.mechanic(MechanicType.DEMON_SEAL);
        GameOrchestrator orchestrator =
                CliApp.buildOrchestrator(mechanic, GridSize.THREE, CliApp.defaultTheme(mechanic));

        assertThatThrownBy(() -> new GenerateCommand(orchestrator).generate(null))
                .isInstanceOf(NullPointerException.class);
    }
}
