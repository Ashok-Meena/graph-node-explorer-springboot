package com.iacuityfintech.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Runs against the bundled dataset (src/main/resources/transactions-graph-nodes.json). 
@SpringBootTest
@AutoConfigureMockMvc
class GraphNodeControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void rootNodeHasLevelZeroAndDirectChildren() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("N1"))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.isRoot").value(true))
                .andExpect(jsonPath("$.isLeaf").value(false))
                .andExpect(jsonPath("$.root").doesNotExist())
                .andExpect(jsonPath("$.leaf").doesNotExist())
                .andExpect(jsonPath("$.parentChain", hasSize(0)))
                .andExpect(jsonPath("$.transactions", hasSize(5)))
                .andExpect(jsonPath("$.children[*].id", contains("N2", "N3", "N4")))
                .andExpect(jsonPath("$.children[0].level").value(1))
                .andExpect(jsonPath("$.nextLevelTransactions", hasSize(3)))
                .andExpect(jsonPath("$.nextLevelTransactions[0].nodeId").value("N2"))
                .andExpect(jsonPath("$.nextLevelTransactions[0].transactions", hasSize(5)))
                .andExpect(jsonPath("$.childrenTree").doesNotExist());
    }

    @Test
    void middleNodeHasParentChainOrderedRootFirst() throws Exception {
        mvc.perform(get("/api/graph/nodes/N7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(2))
                .andExpect(jsonPath("$.isRoot").value(false))
                .andExpect(jsonPath("$.isLeaf").value(false))
                .andExpect(jsonPath("$.parentChain[*].id", contains("N1", "N3")))
                .andExpect(jsonPath("$.children[*].id", contains("N12")));
    }

    @Test
    void deepLeafNode() throws Exception {
        mvc.perform(get("/api/graph/nodes/N15"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(5))
                .andExpect(jsonPath("$.isLeaf").value(true))
                .andExpect(jsonPath("$.children", hasSize(0)))
                .andExpect(jsonPath("$.nextLevelTransactions", hasSize(0)))
                .andExpect(jsonPath("$.parentChain[*].id", contains("N1", "N2", "N5", "N10", "N14")));
    }

    @Test
    void orphanNodeIsTreatedAsRoot() throws Exception {
        mvc.perform(get("/api/graph/nodes/N19"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.parentId").value("N404"))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.isRoot").value(true))
                .andExpect(jsonPath("$.isLeaf").value(false))
                .andExpect(jsonPath("$.parentChain", hasSize(0)))
                .andExpect(jsonPath("$.children[*].id", contains("N20")));

        // a child of the orphan starts counting from the orphan
        mvc.perform(get("/api/graph/nodes/N20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.isRoot").value(false))
                .andExpect(jsonPath("$.parentChain[*].id", contains("N19")));
    }

    @Test
    void unknownNodeReturns404() throws Exception {
        mvc.perform(get("/api/graph/nodes/N999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NODE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Graph node N999 does not exist"));
    }

    @Test
    void invalidMaxDepthReturns400() throws Exception {
        for (String bad : new String[]{"-1", "6", "100", "abc"}) {
            mvc.perform(get("/api/graph/nodes/N1").param("maxDepth", bad))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));
        }
    }

    @Test
    void maxDepthZeroDoesNotExpandChildren() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1").param("maxDepth", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isLeaf").value(false))
                .andExpect(jsonPath("$.children", hasSize(0)))
                .andExpect(jsonPath("$.childrenTree").doesNotExist());
    }

    @Test
    void maxDepthBuildsNestedChildrenTree() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1").param("maxDepth", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.children", hasSize(3)))               // flat list is still there
                .andExpect(jsonPath("$.childrenTree[*].id", contains("N2", "N3", "N4")))
                .andExpect(jsonPath("$.childrenTree[0].level").value(1))
                .andExpect(jsonPath("$.childrenTree[0].children[*].id", contains("N5", "N6")))
                .andExpect(jsonPath("$.childrenTree[0].children[0].children[*].id", contains("N10", "N11")))
                // depth 3 reached: N10's own child N14 (depth 4) is not expanded
                .andExpect(jsonPath("$.childrenTree[0].children[0].children[0].children", hasSize(0)))
                .andExpect(jsonPath("$.childrenTree[0].children[0].children[0].isLeaf").value(false));
    }

    @Test
    void levelAggregatesCoverWholeSubtree() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.levelAggregates", hasSize(6)))
                .andExpect(jsonPath("$.levelAggregates[0].level").value(0))
                .andExpect(jsonPath("$.levelAggregates[0].nodeCount").value(1))
                .andExpect(jsonPath("$.levelAggregates[0].transactionCount").value(5))
                .andExpect(jsonPath("$.levelAggregates[1].nodeCount").value(3))
                .andExpect(jsonPath("$.levelAggregates[1].transactionCount").value(15))
                .andExpect(jsonPath("$.levelAggregates[2].nodeCount").value(5))
                .andExpect(jsonPath("$.levelAggregates[5].level").value(5))
                .andExpect(jsonPath("$.levelAggregates[5].nodeCount").value(1));

        // aggregates start at the requested node's own level
        mvc.perform(get("/api/graph/nodes/N2"))
                .andExpect(jsonPath("$.levelAggregates[0].level").value(1))
                .andExpect(jsonPath("$.levelAggregates[0].nodeCount").value(1));
    }

    @Test
    void childrenTransactionsFilteredByType() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1/children-transactions").param("txnType", "atm"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("N1"))
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.filters.txnType").value("ATM"))
                .andExpect(jsonPath("$.matchedTransactionCount").value(3)) // 1 ATM txn x 3 children
                .andExpect(jsonPath("$.childTransactions", hasSize(3)))
                .andExpect(jsonPath("$.childTransactions[*].transactions[*].txnType", everyItem(is("ATM"))));
    }

    @Test
    void childrenTransactionsFilteredByAmountRange() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1/children-transactions")
                        .param("minAmount", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedTransactionCount").value(0))
                .andExpect(jsonPath("$.childTransactions", hasSize(0)));

        mvc.perform(get("/api/graph/nodes/N1/children-transactions")
                        .param("minAmount", "0").param("maxAmount", "10000000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matchedTransactionCount").value(15));
    }

    @Test
    void childrenTransactionsRejectsBadInput() throws Exception {
        mvc.perform(get("/api/graph/nodes/N1/children-transactions")
                        .param("minAmount", "500").param("maxAmount", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        mvc.perform(get("/api/graph/nodes/N1/children-transactions").param("txnType", "UPI"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("INVALID_PARAMETER"));

        mvc.perform(get("/api/graph/nodes/N1/children-transactions").param("minAmount", "abc"))
                .andExpect(status().isBadRequest());

        mvc.perform(get("/api/graph/nodes/N999/children-transactions"))
                .andExpect(status().isNotFound());
    }

    @Test
    void unknownEndpointReturnsJson404() throws Exception {
        mvc.perform(get("/api/graph/unknown"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }
}
