package com.luckledger.api;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Credentials for the single operator ("master") account. Defaults let the demo log in out of the
 * box; real deployments override via environment variables ({@code LUCKLEDGER_MASTER_USERNAME} /
 * {@code LUCKLEDGER_MASTER_PASSWORD}) so no secret ever needs to live in a committed config file.
 * The password is BCrypt-hashed at startup and only the hash is held in memory afterwards.
 */
@ConfigurationProperties(prefix = "luckledger.master")
public record MasterAccountProperties(String username, String password) {

    public MasterAccountProperties {
        if (username == null || username.isBlank()) {
            username = "master";
        }
        if (password == null || password.isBlank()) {
            password = "scratch-the-truth";
        }
    }
}
