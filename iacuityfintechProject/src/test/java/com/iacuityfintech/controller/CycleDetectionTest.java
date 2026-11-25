package com.iacuityfintech.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Uses src/test/resources/cycle-graph.json which contains a 3-node cycle and a self-parent node. 
@SpringBootTest(properties = "graph.data.location=classpath:cycle-graph.json")
@AutoConfigureMockMvc
class CycleDetectionTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void nodeInsideCycleIsRejectedWithoutLooping() throws Exception {
        for (String id : new String[]{"C1", "C2", "C3"}) {
            mvc.perform(get("/api/graph/nodes/" + id))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").value("CYCLE_DETECTED"));
        }
    }

    @Test
    void nodeHangingOffACycleIsRejected() throws Exception {
        mvc.perform(get("/api/graph/nodes/C4"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CYCLE_DETECTED"));
    }

    @Test
    void selfParentIsACycle() throws Exception {
        mvc.perform(get("/api/graph/nodes/S1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CYCLE_DETECTED"));
    }

    @Test
    void childrenTransactionsEndpointAlsoDetectsCycle() throws Exception {
        mvc.perform(get("/api/graph/nodes/C1/children-transactions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("CYCLE_DETECTED"));
    }

    @Test
    void healthyNodeInSameDatasetStillWorks() throws Exception {
        mvc.perform(get("/api/graph/nodes/R1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.level").value(0))
                .andExpect(jsonPath("$.isRoot").value(true))
                .andExpect(jsonPath("$.isLeaf").value(true));
    }
}
