package com.iacuityfintech.service;

import com.iacuityfintech.dto.ChildNodeDto;
import com.iacuityfintech.dto.ChildTransactions;
import com.iacuityfintech.dto.ChildrenTransactionsResponse;
import com.iacuityfintech.dto.ChildrenTransactionsResponse.AppliedFilters;
import com.iacuityfintech.dto.LevelAggregate;
import com.iacuityfintech.dto.NodeResponse;
import com.iacuityfintech.dto.NodeSummary;
import com.iacuityfintech.dto.TreeNodeDto;
import com.iacuityfintech.exception.CycleDetectedException;
import com.iacuityfintech.exception.InvalidParameterException;
import com.iacuityfintech.exception.NodeNotFoundException;
import com.iacuityfintech.model.GraphIndex;
import com.iacuityfintech.model.GraphNode;
import com.iacuityfintech.model.NodeTransaction;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

@Service
public class GraphNodeService {

    /** Upper bound for maxDepth. 0 means "do not expand children". */
    public static final int MAX_DEPTH_LIMIT = 5;
    private static final Set<String> ALLOWED_TXN_TYPES = Set.of("POS", "ATM", "TRANSFER", "SALARY");

    private final GraphIndex index;

    public GraphNodeService(GraphIndex index) {
        this.index = index;
    }

    // ------------------------------------------------------------------ main API

    public NodeResponse getNode(String id, int maxDepth) {
        if (maxDepth < 0 || maxDepth > MAX_DEPTH_LIMIT) {
            throw new InvalidParameterException(
                    "maxDepth must be between 0 and " + MAX_DEPTH_LIMIT + " but was " + maxDepth);
        }
        GraphNode node = requireNode(id);
        List<GraphNode> chain = resolveParentChain(node);
        int level = chain.size();

        List<GraphNode> directChildren = maxDepth >= 1 ? index.childrenOf(id) : List.of();
        List<ChildNodeDto> children = new ArrayList<>();
        List<ChildTransactions> nextLevelTransactions = new ArrayList<>();
        for (GraphNode child : directChildren) {
            children.add(toChildDto(child, level + 1));
            nextLevelTransactions.add(new ChildTransactions(child.id(), child.name(), child.transactions()));
        }

        Subtree subtree = walkSubtree(node, level, maxDepth);

        return new NodeResponse(
                node.id(), node.parentId(), node.name(), node.accountNumber(),
                level,
                chain.isEmpty(),                       // isRoot: parentId null OR parent missing
                index.childrenOf(id).isEmpty(),        // isLeaf: really has no children
                chain.stream().map(GraphNodeService::toSummary).toList(),
                node.transactions(),
                children,
                nextLevelTransactions,
                maxDepth > 1 ? subtree.tree() : null,  // childrenTree only when depth > 1
                subtree.aggregates());
    }

    public ChildrenTransactionsResponse getChildrenTransactions(String id, BigDecimal minAmount,
                                                                BigDecimal maxAmount, String txnType) {
        if (minAmount != null && maxAmount != null && minAmount.compareTo(maxAmount) > 0) {
            throw new InvalidParameterException("minAmount must not be greater than maxAmount");
        }
        String type = null;
        if (txnType != null && !txnType.isBlank()) {
            type = txnType.trim().toUpperCase(Locale.ROOT);
            if (!ALLOWED_TXN_TYPES.contains(type)) {
                throw new InvalidParameterException(
                        "txnType must be one of " + new java.util.TreeSet<>(ALLOWED_TXN_TYPES) + " but was " + txnType);
            }
        }

        GraphNode node = requireNode(id);
        List<GraphNode> chain = resolveParentChain(node);

        List<ChildTransactions> result = new ArrayList<>();
        int matched = 0;
        for (GraphNode child : index.childrenOf(id)) {
            final String typeFilter = type;
            List<NodeTransaction> filtered = child.transactions().stream()
                    .filter(t -> matches(t, minAmount, maxAmount, typeFilter))
                    .toList();
            if (!filtered.isEmpty()) {
                result.add(new ChildTransactions(child.id(), child.name(), filtered));
                matched += filtered.size();
            }
        }

        return new ChildrenTransactionsResponse(
                node.id(), node.parentId(), node.name(), node.accountNumber(),
                chain.size(), chain.isEmpty(), index.childrenOf(id).isEmpty(),
                new AppliedFilters(minAmount, maxAmount, type),
                matched, result);
    }

    // ------------------------------------------------------------------ level / parentChain

    /**
     * Walks UP the parentId chain. Returns the chain ordered root -> direct parent.
     * <ul>
     *   <li>parentId == null            -> empty chain (root)</li>
     *   <li>parent missing in dataset   -> chain stops there (orphan, root-like, level 0)</li>
     *   <li>a node seen twice           -> CycleDetectedException (also covers parentId == own id)</li>
     * </ul>
     * level == chain.size().
     */
    private List<GraphNode> resolveParentChain(GraphNode node) {
        List<GraphNode> chain = new ArrayList<>();
        Set<String> visited = new HashSet<>();
        visited.add(node.id());

        String parentId = node.parentId();
        while (parentId != null) {
            GraphNode parent = index.findById(parentId).orElse(null);
            if (parent == null) {
                break; // orphan: treat the last known node as root-like
            }
            if (!visited.add(parent.id())) {
                throw new CycleDetectedException(node.id(), parent.id());
            }
            chain.add(parent);
            parentId = parent.parentId();
        }
        Collections.reverse(chain);
        return chain;
    }

    // ------------------------------------------------------------------ subtree traversal

    private record Subtree(List<TreeNodeDto> tree, List<LevelAggregate> aggregates) {
    }

    private record QueueItem(GraphNode node, TreeNodeDto dto, int depth) {
    }

    /** Mutable accumulator for one level. */
    private static final class LevelAcc {
        int nodes;
        int txns;
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
    }

    /**
     * One iterative BFS over the WHOLE subtree of {@code root} (no recursion, so no stack overflow).
     * <ul>
     *   <li>builds the nested childrenTree for nodes up to {@code maxDepth}</li>
     *   <li>builds per-level aggregates for the entire subtree</li>
     *   <li>a visited set guards against cycles: in a tree every node is reached exactly once,
     *       so reaching a node twice means the data has a cycle</li>
     * </ul>
     */
    private Subtree walkSubtree(GraphNode root, int rootLevel, int maxDepth) {
        List<TreeNodeDto> rootChildren = new ArrayList<>();
        Map<Integer, LevelAcc> perLevel = new TreeMap<>();
        Set<String> visited = new HashSet<>();
        visited.add(root.id());

        Deque<QueueItem> queue = new ArrayDeque<>();
        queue.add(new QueueItem(root, null, 0));

        while (!queue.isEmpty()) {
            QueueItem item = queue.poll();
            accumulate(perLevel.computeIfAbsent(rootLevel + item.depth(), k -> new LevelAcc()), item.node());

            for (GraphNode child : index.childrenOf(item.node().id())) {
                if (!visited.add(child.id())) {
                    throw new CycleDetectedException(root.id(), child.id());
                }
                int childDepth = item.depth() + 1;
                TreeNodeDto dto = null;
                if (childDepth <= maxDepth) {
                    dto = toTreeDto(child, rootLevel + childDepth);
                    (item.dto() == null ? rootChildren : item.dto().children()).add(dto);
                }
                queue.add(new QueueItem(child, dto, childDepth));
            }
        }

        List<LevelAggregate> aggregates = new ArrayList<>();
        perLevel.forEach((level, acc) -> aggregates.add(new LevelAggregate(
                level, acc.nodes, acc.txns,
                scale(acc.total), scale(acc.debit), scale(acc.credit))));
        return new Subtree(rootChildren, aggregates);
    }

    private void accumulate(LevelAcc acc, GraphNode node) {
        acc.nodes++;
        for (NodeTransaction t : node.transactions()) {
            BigDecimal amount = t.amount() == null ? BigDecimal.ZERO : t.amount().abs();
            acc.txns++;
            acc.total = acc.total.add(amount);
            if ("DEBIT".equalsIgnoreCase(t.direction())) {
                acc.debit = acc.debit.add(amount);
            } else if ("CREDIT".equalsIgnoreCase(t.direction())) {
                acc.credit = acc.credit.add(amount);
            }
        }
    }

    // ------------------------------------------------------------------ helpers

    private GraphNode requireNode(String id) {
        return index.findById(id).orElseThrow(() -> new NodeNotFoundException(id));
    }

    private static boolean matches(NodeTransaction t, BigDecimal min, BigDecimal max, String type) {
        BigDecimal amount = t.amount() == null ? BigDecimal.ZERO : t.amount();
        if (min != null && amount.compareTo(min) < 0) {
            return false;
        }
        if (max != null && amount.compareTo(max) > 0) {
            return false;
        }
        return type == null || type.equalsIgnoreCase(t.txnType());
    }

    private ChildNodeDto toChildDto(GraphNode n, int level) {
        return new ChildNodeDto(n.id(), n.parentId(), n.name(), n.accountNumber(), level,
                index.childrenOf(n.id()).isEmpty(), n.transactions().size());
    }

    private TreeNodeDto toTreeDto(GraphNode n, int level) {
        return new TreeNodeDto(n.id(), n.parentId(), n.name(), n.accountNumber(), level,
                index.childrenOf(n.id()).isEmpty(), n.transactions().size(), new ArrayList<>());
    }

    private static NodeSummary toSummary(GraphNode n) {
        return new NodeSummary(n.id(), n.parentId(), n.name(), n.accountNumber());
    }

    private static BigDecimal scale(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
