package com.iacuityfintech.model;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Immutable in-memory index of the graph, built once at startup.
 * 
 * nodeById: O(1) lookup of a node
 * childrenByParentId: O(1) lookup of direct children
 * 
 * The dataset is NOT assumed to be ordered. Orphans and cycles are tolerated at load time
 * (they are logged) and handled per request by the service.
 */
public class GraphIndex {

    private static final Logger log = LoggerFactory.getLogger(GraphIndex.class);
    private static final Pattern PREFIX_NUMBER = Pattern.compile("^(\\D*)(\\d+)$");

    // Sorts N2 before N10 (natural order) so responses are deterministic.
    private static final Comparator<GraphNode> NODE_ORDER = (a, b) -> naturalCompare(a.id(), b.id());

    private final Map<String, GraphNode> nodeById;
    private final Map<String, List<GraphNode>> childrenByParentId;

    public GraphIndex(List<GraphNode> nodes) {
        Map<String, GraphNode> byId = new LinkedHashMap<>();
        for (GraphNode node : nodes == null ? List.<GraphNode>of() : nodes) {
            if (node == null || node.id() == null || node.id().isBlank()) {
                throw new IllegalStateException("Graph data contains a node without an id");
            }
            if (byId.putIfAbsent(node.id(), node) != null) {
                throw new IllegalStateException("Duplicate node id in graph data: " + node.id());
            }
        }

        Map<String, List<GraphNode>> children = new HashMap<>();
        for (GraphNode node : byId.values()) {
            if (node.parentId() != null) {
                children.computeIfAbsent(node.parentId(), k -> new ArrayList<>()).add(node);
            }
        }
        children.replaceAll((k, list) -> {
            list.sort(NODE_ORDER);
            return Collections.unmodifiableList(list);
        });

        this.nodeById = Collections.unmodifiableMap(byId);
        this.childrenByParentId = Collections.unmodifiableMap(children);
        logDataQualityWarnings();
    }

    public Optional<GraphNode> findById(String id) {
        return Optional.ofNullable(nodeById.get(id));
    }

    public List<GraphNode> childrenOf(String id) {
        return childrenByParentId.getOrDefault(id, List.of());
    }

    public int size() {
        return nodeById.size();
    }

    private void logDataQualityWarnings() {
        List<String> orphans = new ArrayList<>();
        List<String> cyclic = new ArrayList<>();
        for (GraphNode node : nodeById.values()) {
            if (node.parentId() != null && !nodeById.containsKey(node.parentId())) {
                orphans.add(node.id());
            }
            if (walksIntoCycle(node)) {
                cyclic.add(node.id());
            }
        }
        log.info("Loaded {} graph nodes", nodeById.size());
        if (!orphans.isEmpty()) {
            log.warn("Orphan nodes (parent missing, treated as root-like): {}", orphans);
        }
        if (!cyclic.isEmpty()) {
            log.warn("Nodes whose parent chain contains a cycle (requests will return CYCLE_DETECTED): {}", cyclic);
        }
    }

    private boolean walksIntoCycle(GraphNode start) {
        Set<String> visited = new HashSet<>();
        GraphNode current = start;
        while (current != null) {
            if (!visited.add(current.id())) {
                return true;
            }
            current = current.parentId() == null ? null : nodeById.get(current.parentId());
        }
        return false;
    }

    static int naturalCompare(String a, String b) {
        Matcher ma = PREFIX_NUMBER.matcher(a);
        Matcher mb = PREFIX_NUMBER.matcher(b);
        if (ma.matches() && mb.matches()) {
            int prefix = ma.group(1).compareTo(mb.group(1));
            if (prefix != 0) {
                return prefix;
            }
            return new java.math.BigInteger(ma.group(2)).compareTo(new java.math.BigInteger(mb.group(2)));
        }
        return a.compareTo(b);
    }
}
