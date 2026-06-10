package com.luckledger.api;

import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Session introspection for the SPA. Login and logout themselves are handled by the Spring Security
 * filter chain ({@code POST /api/auth/login} / {@code POST /api/auth/logout} — see
 * {@link SecurityConfig}); this endpoint only answers "who am I right now", so the frontend can
 * restore the master session after a page refresh. Public by design.
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @GetMapping("/me")
    public SessionView me(Authentication authentication) {
        boolean authenticated = authentication != null
                && authentication.isAuthenticated()
                && !(authentication instanceof AnonymousAuthenticationToken);
        return new SessionView(authenticated, authenticated ? authentication.getName() : null);
    }

    public record SessionView(boolean authenticated, String username) {}
}
