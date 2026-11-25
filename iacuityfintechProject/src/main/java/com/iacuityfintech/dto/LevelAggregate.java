package com.iacuityfintech.dto;

import java.math.BigDecimal;

/**
 * Aggregate for one hierarchy level of the requested node's subtree (the node itself included).
 * <ul>
 *   <li>totalAmount = sum of ABSOLUTE amounts of all transactions (debit + credit)</li>
 *   <li>debitAmount / creditAmount = the same sum split by direction</li>
 * </ul>
 */
public record LevelAggregate(
        int level,
        int nodeCount,
        int transactionCount,
        BigDecimal totalAmount,
        BigDecimal debitAmount,
        BigDecimal creditAmount) {
}
