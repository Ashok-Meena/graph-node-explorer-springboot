package com.iacuityfintech.model;

import java.util.List;

// A node of the hierarchy. The hierarchy is defined only by {@code parentId}.
// parentId == null (or pointing to a missing node) means root-like.
public record GraphNode(
        String id,
        String parentId,
        String name,
        String accountNumber,
        List<NodeTransaction> transactions) {

    public GraphNode {
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }
}
