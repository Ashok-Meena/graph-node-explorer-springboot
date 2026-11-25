package com.iacuityfintech.dto;

import com.iacuityfintech.model.NodeTransaction;

import java.util.List;

/** Transactions that belong to one child node. */
public record ChildTransactions(String nodeId, String nodeName, List<NodeTransaction> transactions) {
}
