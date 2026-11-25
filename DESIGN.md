# Design

## 1. Data loading
`GraphDataConfig` reads `transactions-graph-nodes.json` from the classpath once at startup with Jackson and builds a
`GraphIndex` bean. The index holds two immutable maps:
- `nodeById : Map<String, GraphNode>` – O(1) node lookup
- `childrenByParentId : Map<String, List<GraphNode>>` – O(1) direct-children lookup (sorted by natural id order)

The input order is irrelevant because everything is resolved through the maps. Startup fails fast on a missing/malformed
file, a node without id, or duplicate ids. Orphans and cycles do **not** fail startup; they are logged as warnings
and handled per request.

## 2. Level and parentChain
Walk **up** from the node following `parentId`, collecting parents in a list, with a `visited` set:
- `parentId == null` → stop (root)
- parent not in `nodeById` → stop (orphan, root-like)
- parent already in `visited` → `CycleDetectedException` (covers `parentId == own id` as well)

The list is reversed so it reads root → direct parent. `level = chain.size()`, `isRoot = chain.isEmpty()`.
Cost: O(depth) per request, no caching needed for this size.

## 3. Children, next-level transactions, tree, aggregates
- Flat `children` / `nextLevelTransactions` come straight from `childrenByParentId`.
- `childrenTree` and `levelAggregates` come from **one iterative BFS** over the subtree (a `Deque`, no recursion → no
  stack overflow on deep data). The tree is built only down to `maxDepth`; aggregates cover the full subtree.
- In a tree each node has exactly one parent, so it must be reached exactly once. A `visited` set therefore turns
  "reached twice" into `CYCLE_DETECTED`, so the BFS can never loop forever.

## 4. Orphans and cycles
| Case | Behaviour |
|---|---|
| Parent missing | level 0, `parentChain = []`, `isRoot = true`, `isLeaf` reflects real children |
| Cycle / self-parent | 400 `CYCLE_DETECTED`, no infinite loop; other nodes unaffected |
| Duplicate ids | startup failure (ambiguous data) |

## 5. Error handling
A single `@RestControllerAdvice` maps exceptions to `{ "error", "message" }` bodies, so controllers stay free of
try/catch. Unknown URLs and unexpected exceptions also return JSON (no HTML error page, no stack trace leaked).

## 6. Money
`BigDecimal` for amounts, aggregates rounded to 2 decimals. Aggregation rule is documented in the README.

## 7. How it would scale / be extended
- **Larger data**: move to a database (`graph_node(id, parent_id)` indexed on `parent_id`, `txn(node_id)` indexed);
  subtree and ancestor queries via recursive CTEs, or store a materialized path / closure table for O(1) ancestors.
- **Caching**: cache computed level / parentChain per node (the dataset is immutable) and subtree aggregates.
- **Pagination**: paginate transactions (`page`, `size`) and cap the size of `childrenTree` for wide nodes.
- **Filters**: add date range, direction, currency and sorting to the filter endpoint; push them into SQL.
- **Hot reload / writes**: swap the immutable index atomically (`AtomicReference<GraphIndex>`) when data changes.
- **Operability**: Actuator health/metrics, request logging with a correlation id, OpenAPI docs via springdoc.
- **Validation of data at load**: optionally refuse to start in a "strict" profile when cycles exist.
