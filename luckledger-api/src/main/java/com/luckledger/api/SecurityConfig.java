package com.luckledger.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.function.Supplier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Auth for the operator surface. There is exactly one account — the master — held in memory with a
 * BCrypt-hashed password from {@link MasterAccountProperties}; players remain anonymous (their id in
 * localStorage), as before. Only the mutating operator routes are gated: {@code /api/master/**}
 * requires {@code ROLE_MASTER}. The read-only {@code /api/house/**} overview is deliberately public —
 * surfacing that every pool is built to keep a fixed share IS the lesson — as is every other endpoint.
 *
 * <p>Login is session-based ({@code POST /api/auth/login}, form fields, JSON responses — no redirect
 * dance for the SPA). CSRF protection follows the documented SPA cookie-to-header pattern
 * ({@code XSRF-TOKEN} cookie → {@code X-XSRF-TOKEN} header) and covers the session-backed surface:
 * login/logout and the master endpoints. The anonymous player endpoints are exempt — they carry no
 * session or credentials, so there is no authority for a cross-site request to ride.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@EnableConfigurationProperties(MasterAccountProperties.class)
public class SecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityConfig.class);

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    public UserDetailsService masterAccount(MasterAccountProperties props, PasswordEncoder encoder) {
        String password = resolvePassword(props);
        return new InMemoryUserDetailsManager(User.withUsername(props.username())
                .password(encoder.encode(password))
                .roles("MASTER")
                .build());
    }

    /**
     * Fails closed: if no master password is configured, mint a random one-time password and surface
     * it once at WARN so the demo can still boot without a guessable committed default. Only the
     * BCrypt hash is retained; the plaintext lives no longer than this startup log line.
     */
    private static String resolvePassword(MasterAccountProperties props) {
        if (props.hasConfiguredPassword()) {
            return props.password();
        }
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        String generated = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        log.warn("No master password configured (luckledger.master.password). Generated a one-time "
                + "password for user '{}': {}  — set LUCKLEDGER_MASTER_PASSWORD to pin your own.",
                props.username(), generated);
        return generated;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/master/**").hasRole("MASTER")
                        .anyRequest().permitAll())
                .csrf(csrf -> csrf
                        .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                        .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler())
                        // Anonymous player flows: no session, no credentials — nothing to forge.
                        .ignoringRequestMatchers("/api/players/**", "/api/books/**", "/api/tickets/**"))
                .addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
                .formLogin(form -> form
                        .loginProcessingUrl("/api/auth/login")
                        .successHandler((request, response, authentication) ->
                                writeJson(response, HttpServletResponse.SC_OK,
                                        "{\"username\":\"" + authentication.getName() + "\",\"role\":\"MASTER\"}"))
                        .failureHandler((request, response, exception) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "{\"code\":\"BAD_CREDENTIALS\",\"message\":\"Wrong username or password.\"}")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .logoutSuccessHandler((request, response, authentication) ->
                                response.setStatus(HttpServletResponse.SC_NO_CONTENT)))
                .exceptionHandling(handling -> handling
                        // The SPA expects status codes, never a redirect to a login page.
                        .authenticationEntryPoint((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_UNAUTHORIZED,
                                        "{\"code\":\"UNAUTHORIZED\",\"message\":\"Master login required.\"}"))
                        .accessDeniedHandler((request, response, ex) ->
                                writeJson(response, HttpServletResponse.SC_FORBIDDEN,
                                        "{\"code\":\"FORBIDDEN\",\"message\":\"Master role required.\"}")));
        return http.build();
    }

    private static void writeJson(HttpServletResponse response, int status, String body) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write(body);
    }

    /**
     * The Spring Security reference's SPA handler: tokens are rendered XORed (BREACH protection) but
     * a raw token arriving in the {@code X-XSRF-TOKEN} header — read by JS from the cookie — is
     * resolved plainly.
     */
    static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {
        private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
        private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

        @Override
        public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
            this.xor.handle(request, response, csrfToken);
            csrfToken.get(); // resolve the deferred token so the repository writes the cookie
        }

        @Override
        public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
            String headerValue = request.getHeader(csrfToken.getHeaderName());
            return (StringUtils.hasText(headerValue) ? this.plain : this.xor)
                    .resolveCsrfTokenValue(request, csrfToken);
        }
    }

    /** Touches the CSRF token on every request so the {@code XSRF-TOKEN} cookie is always present. */
    static final class CsrfCookieFilter extends OncePerRequestFilter {
        @Override
        protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
                throws ServletException, IOException {
            CsrfToken token = (CsrfToken) request.getAttribute("_csrf");
            if (token != null) {
                token.getToken();
            }
            chain.doFilter(request, response);
        }
    }
}
