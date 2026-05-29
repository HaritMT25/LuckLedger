package com.luckledger.api.persistence;

import com.luckledger.domain.ledger.Transaction;

/** Maps between the domain {@link Transaction} value object and its JPA {@link TransactionEntity}. */
public final class TransactionMapper {

    private TransactionMapper() {}

    public static TransactionEntity toEntity(Transaction t) {
        return new TransactionEntity(
                t.transactionId(),
                t.playerId(),
                t.type(),
                t.amount(),
                t.dealerId(),
                t.bookId(),
                t.ticketId(),
                t.timestamp());
    }

    public static Transaction toDomain(TransactionEntity e) {
        return new Transaction(
                e.getId(),
                e.getPlayerId(),
                e.getType(),
                e.getAmount(),
                e.getDealerId(),
                e.getBookId(),
                e.getTicketId(),
                e.getCreatedAt());
    }
}
