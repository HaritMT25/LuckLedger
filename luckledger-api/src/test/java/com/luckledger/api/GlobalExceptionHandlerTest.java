package com.luckledger.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.luckledger.distribution.BookDepletedException;
import com.luckledger.domain.generation.GenerationIntegrityException;
import com.luckledger.domain.player.InsufficientBalanceException;
import com.luckledger.domain.pool.InvalidPoolException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

    /** A throwaway controller that raises whichever domain exception the path names. */
    @RestController
    @RequestMapping("/boom")
    static class ThrowingController {
        @GetMapping("/{kind}")
        String boom(@PathVariable String kind) {
            return switch (kind) {
                case "balance" -> throw new InsufficientBalanceException(
                        UUID.randomUUID(), BigDecimal.ZERO, BigDecimal.TEN);
                case "ownership" -> throw new TicketOwnershipException(
                        UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());
                case "depleted" -> throw new BookDepletedException("book depleted");
                case "missing" -> throw new NoSuchElementException("no such player");
                case "state" -> throw new IllegalStateException("dealer at capacity");
                case "pool" -> throw new InvalidPoolException("bad pool");
                case "validation" -> throw new IllegalArgumentException("bad input");
                case "generation" -> throw new GenerationIntegrityException("verify failed");
                case "unexpected" -> throw new RuntimeException("secret internal stack detail");
                default -> "ok";
            };
        }

        @PostMapping("/validate")
        String validate(@Valid @RequestBody Body body) {
            return "ok:" + body.name();
        }

        record Body(@NotBlank String name) {}
    }

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new ThrowingController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private void expect(String kind, int statusCode, String code) throws Exception {
        mockMvc.perform(get("/boom/" + kind))
                .andExpect(status().is(statusCode))
                .andExpect(jsonPath("$.code").value(code))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void mapsInsufficientBalanceTo402() throws Exception {
        expect("balance", 402, "INSUFFICIENT_BALANCE");
    }

    @Test
    void mapsTicketOwnershipTo403() throws Exception {
        expect("ownership", 403, "NOT_TICKET_OWNER");
    }

    @Test
    void mapsBookDepletedTo410() throws Exception {
        expect("depleted", 410, "BOOK_DEPLETED");
    }

    @Test
    void mapsNotFoundTo404() throws Exception {
        expect("missing", 404, "NOT_FOUND");
    }

    @Test
    void mapsIllegalStateTo409() throws Exception {
        expect("state", 409, "CONFLICT");
    }

    @Test
    void mapsInvalidPoolTo422() throws Exception {
        expect("pool", 422, "INVALID_POOL");
    }

    @Test
    void mapsIllegalArgumentTo422() throws Exception {
        expect("validation", 422, "VALIDATION_ERROR");
    }

    @Test
    void mapsGenerationIntegrityTo500() throws Exception {
        expect("generation", 500, "GENERATION_FAILED");
    }

    @Test
    void mapsBeanValidationFailureTo400() throws Exception {
        mockMvc.perform(post("/boom/validate")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error").isNotEmpty());
    }

    @Test
    void mapsMalformedBodyTo400() throws Exception {
        mockMvc.perform(post("/boom/validate")
                        .contentType(MediaType.APPLICATION_JSON).content("{ not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    void mapsUnexpectedTo500WithoutLeakingInternals() throws Exception {
        String body = mockMvc.perform(get("/boom/unexpected"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.code").value("INTERNAL_ERROR"))
                .andReturn().getResponse().getContentAsString();
        // The opaque envelope must not leak the underlying exception message.
        assertThat(body).doesNotContain("secret internal stack detail");
    }
}
