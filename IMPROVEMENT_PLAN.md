# Improvement Plan — ASHINA Queen Hunt

Ranked by expected impact, with explicit measurement criteria. Nothing ships
without the numbers.

## A. Web Worker move (high value, needs browser)

**Problem.** The selector today is bounded by the main-thread budget
(≈700 ms under tension). On lichess/chess.com the engine thread already
competes with the UI. A worker lets us hunt for seconds without jank.

**What to do.** Move the whole selector into a `new Worker(...)` that
receives `{ fen, candidates }`, returns `{ moves, meta }` via postMessage.
Keep the main-thread entry as a thin proxy with a timeout fallback.

**Why not here.** This cannot be verified inside the sandbox — it needs a
real chess.com/lichess session with the engine pipeline and MV3 permissions.
Ship it behind a feature flag, measure in a real session: median hunt time
vs today's 400 ms budget, main-thread longest-task, and the worker's own
per-call deadline.

**Done when.** p50 hunt latency ≤ 50 ms on the main thread (proxy only),
worker p90 depth reaches ≥ 12 plies on tension positions, no soundness
regression on `bench/run_bench.cjs`.

## B. Cross-move TT persistence tuning

**Status.** Persistent TT is in. `_TT` is a `Map` with 300k entries and
insertion-order eviction. Needs real-world workload data to tune the cap
and eviction policy.

**What to do.** In a session recorded from a real game (lichess PGN replay
driving the selector), measure cache hit rate, memory high-water mark, and
the p99 call that evicts. If hit rate is < 10 % at move 20+, raise the
cap; if memory > 20 MB in the content script, lower it or move to
LRU-on-read with a `Map` of `Map`s.

**Done when.** Hit rate ≥ 25 % after move 15 in a typical game, memory
≤ 10 MB, no latency regression.

## C. SEE-based capture ordering

**Status.** `see()` exists in the core as a scaffold but is not wired into
`orderMoves`. Current ordering uses MVV-LVA + TT + killers + history +
queen-attack bonus. That's usually enough, but SEE would kill noisy
"bad captures first" traversal in dense middlegame positions.

**What to do.** Replace the `m.captured` term in `orderMoves` with
`see(ch, m.to, usColor, ChessCtor) * 100` (capped). Re-run the known-tactics
suite and the random bench. Only ship if p50 latency drops on the random
bench without any test regression.

**Done when.** p50 ≤ 350 ms on the current bench, no change in promotion
quality.

## D. Widen candidate window

**Status.** The hunt takes the engine's top 6 candidates (`maxCandidates`).
If the engine's own ordering buries the queen-winning move past rank 6, the
hunt can't help.

**What to do.** When tension is HIGH, ask the engine for MultiPV = 8 or 10
and pass that wider set. Measure whether the extra candidates ever change
the hunt's output (they will, rarely) and the latency cost.

**Done when.** On a hand-collected set of "queen-win-at-rank-7-or-8"
positions, MultiPV = 10 lifts recall without increasing false positives.

## E. Deepen the full-width confirm

**Status.** `shortLineConfirm` runs iterative deepening to depth 6 with a
400 ms cap. In dense positions it may only reach depth 4. The last-known
false-positive class (from v9.4 engineering report) was a quiet
refutation at depth 5–6.

**What to do.** Let confirmation deepen up to 8 plies under a configurable
budget; keep the default conservative. Re-audit soundness on an enlarged
random bench (200 positions, depth-6 oracle).

**Done when.** 0 false positives across ≥ 200 random positions audited at
oracle depth 6.

## F. Tier-2 instrumentation and tuning

**Status.** Tier-2 fires when `queen_attacked && queen_safe_escapes ≤ 2`.
Current mining (at depth 4, 60 positions) finds no *truly-quiet*
(non-check, non-capture, non-promotion) first-move queen traps — the
handoff's "rare and maybe vanishing" prediction.

**What to do.** Run extended mining (`node bench/mine_traps.cjs 2000
--depth 6`) and classify every trap by `trulyQuietFirst`. If < 1 % of
positions have truly-quiet traps, leave tier-2 gated as is. If > 5 %,
loosen the gate to `escapes ≤ 3` and widen the keep-filter.

**Done when.** A measured number — truly-quiet-trap density — is in
`bench/mining_summary.json`, and the gate parameters are chosen from that.

## Rejected (keep rejected)

- **Per-leaf queen scans.** The v9-era rewrite proved the cost shallower
  the search more than the signal deepened it.
- **Uniform larger budget.** Zero extra wins, only latency. Already learned.
- **Trusting short capturing lines without exchange settlement.** The v10
  validator settles the exchange on the capture square; do not weaken it.
