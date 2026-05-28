package com.luckledger.domain.mechanic;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class GridSizeTest {

    @Test
    void shouldDeclareAllGridSizesInExpectedOrder() {
        assertThat(GridSize.values())
                .containsExactly(GridSize.THREE, GridSize.FOUR, GridSize.FIVE);
    }

    @ParameterizedTest
    @CsvSource({"THREE,3", "FOUR,4", "FIVE,5"})
    void shouldReportTheCorrectDimension(GridSize size, int expectedDimension) {
        assertThat(size.dimension()).isEqualTo(expectedDimension);
    }
}
