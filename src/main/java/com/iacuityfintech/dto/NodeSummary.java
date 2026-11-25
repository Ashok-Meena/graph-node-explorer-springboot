package com.iacuityfintech.dto;

/** Lightweight node details, used for entries of parentChain. */
public record NodeSummary(String id, String parentId, String name, String accountNumber) {
}
