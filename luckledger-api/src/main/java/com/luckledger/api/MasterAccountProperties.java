package com.luckledger.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the single operator ("master") account. The username defaults to {@code master};
 * the password has <strong>no default</strong> and <em>fails closed</em> — if none is configured,
 * {@link SecurityConfig} mints a random one-time password at startup and logs it at WARN, so the demo
 * still boots but nothing guessable is ever baked into a committed config. Real deployments set both
 * via environment variables ({@code LUCKLEDGER_MASTER_USERNAME} / {@code LUCKLEDGER_MASTER_PASSWORD}).
 * Whatever the source, the password is BCrypt-hashed at startup and only the hash is held afterwards.
 *
 * @param username the operator login; blank/unset falls back to {@code master}
 * @param password the operator secret; blank/unset means "generate a one-time password" (never a
 *                 fixed default), so a forgotten config cannot silently open a known-password account
 */
@ConfigurationProperties(prefix = "luckledger.master")
public record MasterAccountProperties(String username, String password) {

    public MasterAccountProperties {
        if (username == null || username.isBlank()) {
            username = "master";
        }
        // No password default on purpose: a blank password is resolved to a random one-time secret in
        // SecurityConfig, so the account never falls open to a committed/guessable value.
    }

    /** Whether a password was actually configured (blank means "mint a one-time password"). */
    boolean hasConfiguredPassword() {
        return password != null && !password.isBlank();
    }
}
