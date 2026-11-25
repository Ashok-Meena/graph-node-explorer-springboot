package com.iacuityfintech.controller;

import com.iacuityfintech.dto.ChildrenTransactionsResponse;
import com.iacuityfintech.dto.NodeResponse;
import com.iacuityfintech.service.GraphNodeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;

@RestController
@RequestMapping("/api/graph/nodes")
public class GraphNodeController {

    private final GraphNodeService service;

    public GraphNodeController(GraphNodeService service) {
        this.service = service;
    }

    /** Node details, level, parentChain, direct children, own and next-level transactions. */
    @GetMapping("/{id}")
    public NodeResponse getNode(@PathVariable String id,
                                @RequestParam(defaultValue = "1") int maxDepth) {
        return service.getNode(id, maxDepth);
    }

    /** Child-node transactions filtered by amount range and/or txnType. */
    @GetMapping("/{id}/children-transactions")
    public ChildrenTransactionsResponse getChildrenTransactions(
            @PathVariable String id,
            @RequestParam(required = false) BigDecimal minAmount,
            @RequestParam(required = false) BigDecimal maxAmount,
            @RequestParam(required = false) String txnType) {
        return service.getChildrenTransactions(id, minAmount, maxAmount, txnType);
    }
}
