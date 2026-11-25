package com.iacuityfintech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

/** A direct child of the requested node. */
public record ChildNodeDto(
        String id,
        String parentId,
        String name,
        String accountNumber,
        int level,
        @JsonProperty("isLeaf") boolean isLeaf,
        int transactionCount) {
}
