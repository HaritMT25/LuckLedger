package com.luckledger.api;

/**
 * The uniform error envelope returned by every failed API call: a human-readable {@code error}
 * message and a stable machine-readable {@code code}.
 *
 * @param error human-readable description of what went wrong
 * @param code stable machine code (e.g. {@code INSUFFICIENT_BALANCE}) for clients to branch on
 */
public record ErrorResponse(String error, String code) {}
