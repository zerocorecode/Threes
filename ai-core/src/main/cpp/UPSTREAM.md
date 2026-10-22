# nneonneo timed engine integration

Upstream: https://github.com/nneonneo/threes-ai

Pinned commit: `c554820ad848a73b7cd34e358c974ab2d85bd792` (2026-08-23, MIT).

The files in `upstream/` remain unchanged. Git blob IDs:

| File | Git blob SHA-1 |
| --- | --- |
| threes.cpp | fed979c6ff675d7344198bf0767a69acbdb5f3e1 |
| threes.h | fbe9af28a96dea8124da18ff32702e57d25cf8c6 |
| platdefs.h | 2a6459dd80e57053c02b029d778365cb5b335288 |
| LICENSE | 121d553f623753c21fe17b6843ffaa56f5d08641 |
| README.md | 144c8bbb55e6d9881bfe9f68167cd2b46f725fe2 |

`threes.cpp` SHA-256: `ee8f7ff5ff054e57f9bd37e7f92dbe64f26001cd59280b4aed4a68d19547f436`.

## Only timed mode is exposed

`NneonneoTimedAi` is the only AI implementation. Android calls it “AI 模式”; the
benchmark ID is `nneonneo-timed`. Other algorithms and the uncapped original JNI
entry point have been removed. The pristine upstream source remains a dependency
and license/reference artifact, rather than another selectable mode.

The adaptation preserves upstream row tables, heuristic weights, chance model,
probability cutoff, U/D/L/R tie-breaking and per-root board-only cache. CMake checks
the source hash and generates a build-directory copy with two hooks: polling search
budgets/cancellation and overriding the adaptive depth for iterative deepening.
The CLI main is renamed for library linking. In particular, the original board-only
cache key and the fixed future maximum-rank treatment are preserved.

The driver completes depths 1, 2, ... up to `SearchLimits.maxDepth`. Timed depth
includes the root move; upstream curdepth does not, so the forced limit is public
depth minus one. Every round evaluates all four roots with fresh per-root caches.
Only complete rounds are published; an expired time/node budget discards the entire
partial round. The first one-move round always completes as a legal fallback.
Explicit cancellation discards all results and propagates to the caller.

Defaults match the tested UI budget: 200ms, depth 8, no practical node cap
(`Long.MAX_VALUE`). A native steady-clock deadline is checked every 64 visited
player nodes and between roots; JVM cancellation is checked every 1,024 nodes and
between roots. These are cooperative limits, not hard real-time guarantees. Nodes
count visited player positions across all rounds, including discarded work.

The Kotlin adapter packs the board into 64 bits, restores a low preview card to the
pre-draw deck required by upstream, and encodes a bonus preview as the visible set
of candidates. It never supplies the actual hidden bonus rank.

## Verification and builds

Host tests compile a pristine, uninstrumented source copy in `upstream_oracle.cpp`
and invoke it with explicit fixed depths to compare all four raw scores and ties.
Tests also verify complete-round fallback, deadlines, cancellation, hidden bonuses,
terminal states, every 65,536 horizontal row state, and random full-board moves.
The oracle is excluded from Android builds.

Android uses NDK r28c, CMake 3.22.1, and static C++ runtime libraries for arm64-v8a,
armeabi-v7a and x86_64. Desktop tests use the same timed engine and JNI adapter; on
Windows they require a C++17 MinGW compiler (`g++`). Override the compiler through
`-Pthrees.cxx=/absolute/path/to/g++` if needed. The MIT license is included in APKs
under `assets/licenses/nneonneo-threes-ai/`.

Historical comparison results remain under `reports/benchmarks/`. Their removed
algorithm IDs cannot be run using the current benchmark task. A source snapshot
from before cleanup is saved at `.tmp/ai-before-single-mode-20261022.zip`.
