package com.iacuityfintech.exception;

public class CycleDetectedException extends RuntimeException {

    public CycleDetectedException(String startNodeId, String repeatedNodeId) {
        super("Cycle detected in graph data while resolving node " + startNodeId
                + ": node " + repeatedNodeId + " was reached twice");
    }
}
