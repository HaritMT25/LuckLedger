package com.luckledger.domain.pool;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

class ValidationResultTest {

    @Test
    void validResultShouldBeValidWithNoErrors() {
        ValidationResult result = ValidationResult.valid();

        assertThat(result.isValid()).isTrue();
        assertThat(result.errors()).isEmpty();
    }

    @Test
    void invalidFromListShouldBeInvalidAndCarryErrors() {
        ValidationResult result = ValidationResult.invalid(List.of("e1", "e2"));

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly("e1", "e2");
    }

    @Test
    void invalidFromVarargsShouldBeInvalidAndCarryErrors() {
        ValidationResult result = ValidationResult.invalid("only one");

        assertThat(result.isValid()).isFalse();
        assertThat(result.errors()).containsExactly("only one");
    }

    @Test
    void errorsShouldBeDefensivelyCopiedFromSource() {
        List<String> source = new ArrayList<>();
        source.add("e1");
        ValidationResult result = ValidationResult.invalid(source);

        source.add("mutated");

        assertThat(result.errors()).containsExactly("e1");
    }

    @Test
    void returnedErrorsListShouldRejectMutation() {
        ValidationResult result = ValidationResult.invalid("e1");

        assertThatThrownBy(() -> result.errors().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void validResultErrorsShouldRejectMutation() {
        ValidationResult result = ValidationResult.valid();

        assertThatThrownBy(() -> result.errors().add("nope"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
