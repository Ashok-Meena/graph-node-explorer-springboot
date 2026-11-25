package com.iacuityfintech.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.iacuityfintech.model.NodeTransaction;

import java.util.List;

/** Response of GET /api/graph/nodes/{id}. */
public record NodeResponse(
        String id,
        String parentId,
        String name,
        String accountNumber,
        int level,
        @JsonProperty("isRoot") boolean isRoot,
        @JsonProperty("isLeaf") boolean isLeaf,
        List<NodeSummary> parentChain,
        List<NodeTransaction> transactions,
        List<ChildNodeDto> children,
        List<ChildTransactions> nextLevelTransactions,
        @JsonInclude(JsonInclude.Include.NON_NULL) List<TreeNodeDto> childrenTree,
        List<LevelAggregate> levelAggregates) {
}
