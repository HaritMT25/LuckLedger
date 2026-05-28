package com.luckledger.distribution;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class BookDepletedExceptionTest {

    @Test
    void isUncheckedAndCarriesItsMessage() {
        BookDepletedException ex = new BookDepletedException("book 7 depleted");

        assertThat(ex).isInstanceOf(RuntimeException.class);
        assertThat(ex.getMessage()).isEqualTo("book 7 depleted");
    }

    @Test
    void carriesACause() {
        Throwable cause = new IllegalStateException("underflow");
        BookDepletedException ex = new BookDepletedException("depleted", cause);

        assertThat(ex.getCause()).isSameAs(cause);
    }
}
