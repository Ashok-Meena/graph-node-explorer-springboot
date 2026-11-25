package com.iacuityfintech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.math.BigDecimal;
import java.util.List;

/** Response of GET /api/graph/nodes/{id}/children-transactions. */
public record ChildrenTransactionsResponse(
        String id,
        String parentId,
        String name,
        String accountNumber,
        int level,
        @JsonProperty("isRoot") boolean isRoot,
        @JsonProperty("isLeaf") boolean isLeaf,
        AppliedFilters filters,
        int matchedTransactionCount,
        List<ChildTransactions> childTransactions) {

    public record AppliedFilters(BigDecimal minAmount, BigDecimal maxAmount, String txnType) {
    }
}
