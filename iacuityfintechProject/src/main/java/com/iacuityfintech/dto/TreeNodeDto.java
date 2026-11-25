package com.iacuityfintech.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Node of the nested childrenTree (bonus: maxDepth &gt; 1). */
public record TreeNodeDto(
        String id,
        String parentId,
        String name,
        String accountNumber,
        int level,
        @JsonProperty("isLeaf") boolean isLeaf,
        int transactionCount,
        List<TreeNodeDto> children) {
}
