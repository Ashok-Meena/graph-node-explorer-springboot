#!/usr/bin/env bash
# Usage: ./scripts/curl-examples.sh [base-url]   (app must be running; default http://localhost:8080)
BASE="${1:-http://localhost:8080}/api/graph/nodes"
show() { echo; echo "### $1"; shift; curl -s -w '\nHTTP %{http_code}\n' "$@"; }

show "Root node N1"                                   "$BASE/N1"
show "Middle node N7 (parentChain = N1, N3)"          "$BASE/N7"
show "Orphan node N19 (level 0, isRoot true)"         "$BASE/N19"
show "Nested tree, maxDepth=3"                        "$BASE/N1?maxDepth=3"
show "Unknown node -> 404"                            "$BASE/N999"
show "Invalid maxDepth -> 400"                        "$BASE/N1?maxDepth=9"
show "Children txns, type=ATM"                        "$BASE/N1/children-transactions?txnType=ATM"
show "Children txns, amount 1000..50000, TRANSFER"    "$BASE/N1/children-transactions?minAmount=1000&maxAmount=50000&txnType=TRANSFER"
