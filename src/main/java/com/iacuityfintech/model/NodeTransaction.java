package com.iacuityfintech.model;

import java.math.BigDecimal;

// A single transaction that belongs to a GraphNode.
// Amount is a BigDecimal to avoid floating point errors when summing money.
public record NodeTransaction(
        String txnId,
        String direction,     // DEBIT | CREDIT
        String txnType,       // SALARY | TRANSFER | POS | ATM
        BigDecimal amount,
        String currency,
        String timestamp,     // ISO-8601 string, kept as-is
        String description) {
}
