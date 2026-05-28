package com.luckledger.cli;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.luckledger.domain.mechanic.MechanicType;
import org.junit.jupiter.api.Test;

class CliAppTest {

    @Test
    void parsesDefaultsWhenNoArgs() {
        CliApp.Options options = CliApp.Options.parse(new String[] {});

        assertThat(options.mechanic()).isEqualTo(MechanicType.DEMON_SEAL);
        assertThat(options.books()).isEqualTo(4);
        assertThat(options.dealers()).isEqualTo(3);
    }

    @Test
    void parsesProvidedFlags() {
        CliApp.Options options = CliApp.Options.parse(
                new String[] {"--mechanic", "CELESTIAL_FORTUNE", "--books", "10", "--dealers", "5"});

        assertThat(options.mechanic()).isEqualTo(MechanicType.CELESTIAL_FORTUNE);
        assertThat(options.books()).isEqualTo(10);
        assertThat(options.dealers()).isEqualTo(5);
    }

    @Test
    void mainRunsAGenerationWithoutError() {
        assertThatCode(() -> CliApp.main(new String[] {"--mechanic", "DEMON_SEAL", "--books", "4", "--dealers", "3"}))
                .doesNotThrowAnyException();
    }
}
