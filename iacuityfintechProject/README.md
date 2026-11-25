# Transaction Graph Node Explorer

Spring Boot REST service that explores a hierarchical transaction graph loaded from a static JSON file.

## Versions
| Item | Version |
|---|---|
| Java | 17 |
| Spring Boot | 3.5.7 (3.x) |
| Build | Maven |

## Run
```bash
mvn spring-boot:run          # starts on http://localhost:8080
mvn test                     # runs the unit/integration tests
./scripts/curl-examples.sh   # sample calls (app must be running)
```
In IntelliJ / STS: import as an existing Maven project and run `IacuityfintechProjectApplication`.

To use a different dataset replace `src/main/resources/transactions-graph-nodes.json`
(same structure) or set `graph.data.location=file:/path/to/file.json`.

## Endpoints

### `GET /api/graph/nodes/{id}`
Query param `maxDepth` (optional, default `1`, allowed `0..5`).

Returns: node details, `level`, `isRoot`, `isLeaf`, `parentChain` (root → direct parent),
`transactions` of the node, direct `children`, `nextLevelTransactions` (transactions of the direct children,
grouped per child), `levelAggregates` for the whole subtree and — only when `maxDepth > 1` — a nested `childrenTree`.

```bash
curl http://localhost:8080/api/graph/nodes/N7
```
```json
{
  "id": "N7", "parentId": "N3", "name": "Fixed Deposit", "accountNumber": "ACC3003",
  "level": 2, "isRoot": false, "isLeaf": false,
  "parentChain": [ { "id": "N1", ... }, { "id": "N3", ... } ],
  "transactions": [ ... ],
  "children": [ { "id": "N12", "level": 3, "isLeaf": true, "transactionCount": 5, ... } ],
  "nextLevelTransactions": [ { "nodeId": "N12", "nodeName": "FD Interest", "transactions": [ ... ] } ],
  "levelAggregates": [
    { "level": 2, "nodeCount": 1, "transactionCount": 5, "totalAmount": 0.00, "debitAmount": 0.00, "creditAmount": 0.00 },
    { "level": 3, "nodeCount": 1, "transactionCount": 5, ... }
  ]
}
```

### `GET /api/graph/nodes/{id}/children-transactions` (optional requirement)
Query params (all optional): `minAmount`, `maxAmount`, `txnType` (`POS|ATM|TRANSFER|SALARY`, case-insensitive).
Returns node details + level + only the **direct-child** transactions matching all given filters
(children with no matching transaction are omitted) and `matchedTransactionCount`.

### Errors
All errors use `{ "error": "<CODE>", "message": "<text>" }`.

| Situation | HTTP | error |
|---|---|---|
| Node id not in dataset | 404 | `NODE_NOT_FOUND` |
| `maxDepth` < 0, > 5 or not a number; `minAmount > maxAmount`; unknown `txnType`; non-numeric amount | 400 | `INVALID_PARAMETER` |
| Cycle found in parent chain or subtree | 400 | `CYCLE_DETECTED` |
| Unknown URL | 404 | `NOT_FOUND` |
| Anything unexpected | 500 | `INTERNAL_ERROR` |

## Assumptions & decisions
- **Level**: `parentId == null` or parent missing → level 0 (root-like); otherwise `parent.level + 1`.
  A node below an orphan counts from the orphan (orphan = level 0, its child = level 1).
- **isRoot** = parentId is null OR parent not found. **isLeaf** = node has no children in the dataset.
- **maxDepth**: the assignment mentions both "max 5" and "400 if > 10". Stricter rule used: `0..5` valid, everything else 400.
  `0` means "do not expand children" (`children` and `nextLevelTransactions` are empty). `maxDepth=1` returns the flat
  `children` list only; `maxDepth>1` returns the flat list **and** `childrenTree`.
- **Aggregates** (`levelAggregates`): cover the *whole* subtree of the requested node (independent of `maxDepth`),
  include the node itself at its own level.
  `totalAmount` = sum of **absolute** amounts of all transactions (debit + credit); `debitAmount` / `creditAmount`
  split the same sum by direction. Rounded to 2 decimals (HALF_UP).
- **Amount filters** compare the transaction amount as stored (inclusive bounds).
- Money is `BigDecimal`, timestamps stay ISO-8601 strings.
- Cycles are returned as **400 `CYCLE_DETECTED`** (data problem reported clearly instead of looping); the app still starts
  and logs a warning listing the affected nodes. Healthy nodes keep working.
- Children are sorted by natural id order (N2 before N10) so responses are deterministic.

## Sample data
`transactions-graph-nodes.json` has 20 nodes / 100 transactions, **unordered**, with levels 0–5, two roots (N1, N16)
and an orphan branch (N19 → missing parent `N404`, with child N20).
`src/test/resources/cycle-graph.json` contains a 3-node cycle, a node hanging off it and a self-parent node.

## Project layout
```
config/      GraphDataConfig        loads the JSON once at startup
model/       GraphNode, NodeTransaction, GraphIndex (in-memory maps)
service/     GraphNodeService       level, parentChain, BFS tree, aggregates, filters
controller/  GraphNodeController
dto/         response records
exception/   custom exceptions + GlobalExceptionHandler
```
