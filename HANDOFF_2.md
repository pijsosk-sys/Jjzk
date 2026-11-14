# ASHINA — Handoff 2 (post-v10)

Read HANDOFF_1.md first for the project's engineering discipline. This is
the delta: what v10 shipped, what it measured, what the next session should
pick up.

## State

- **Version**: `manifest.json` → 10.0.0.
- **Branch**: `claude/yo-2dei3i`.
- **Tests**: 46 passing (`node --test maintenance/test_*.cjs`).
- **Core**: `maintenance/queen-hunt-core.cjs`, triplicated into
  `js/Ashina.js` and `js/lichess.js` (sync-enforced by
  `maintenance/test_sync.cjs`).
- **Measured**: 0 false positives on `bench/run_bench.cjs` over 60 random
  positions (depth-3 oracle); 10/10 on `bench/known_tactics.cjs`; p50
  latency ≈ 415 ms, p90 ≈ 1.2 s under a 400 ms budget.

## What changed vs the prior engine (v9.4 as documented)

1. Transposition table is **persistent across calls** in a session (300k).
2. Alpha-beta at non-root nodes **stands pat when already winning** —
   fixed the "all forcing moves hang a piece" trap that lost +400 wins.
3. Validator **settles the exchange** on the queen-capture square before
   accepting; even trades are rejected by construction.
4. Short-line confirmation is **iterative-deepening full-width** (2→4→6)
   with a time cap instead of a one-shot depth-6 search that timed out
   and failed silently.
5. The main-entry **divides budget across candidates**, so a slow one
   can't starve the real winner.
6. Draw states (insufficient material, 50-move, threefold) are scored by
   **material**, not by game result, because the hunt is a material
   claim.

## What to do next

See `IMPROVEMENT_PLAN.md` for the full list. The near-term items:

1. **Mining at depth 6, 500+ positions.** Produce
   `bench/mining_summary.json` with `trulyQuietFirst` density. Current
   depth-4 mining shows the "truly quiet" first-move trap density is
   effectively zero. If a larger depth confirms it, formally retire
   tier-2 as cold code (keep the gate; never enable it).
2. **Deeper confirm.** Allow `shortLineConfirm` to deepen to 8 plies under
   a larger budget when TIER2 ran. This closes the last known
   false-positive class from v9.4 under a stronger oracle.
3. **SEE in ordering.** The `see()` function is written but unused. Wire
   it into `orderMoves` and re-run the random bench; only ship if p50
   drops.
4. **Web Worker move** (requires browser verification, cannot do here).

## What not to do

- Do not weaken the validator. The exchange-settled check is what makes
  v10 sound by construction.
- Do not re-add per-node queen scans. v9 learned the per-node cost
  shallowed the search.
- Do not ship "N× better" numbers. Report measured deltas (false-pos
  count, latency percentiles, recall on known tactics).
- Do not rename **ASHINA**, do not touch the large content scripts
  outside the BEGIN/END block.

## Run the suite before any change

```
node --test maintenance/test_*.cjs            # 46 must stay green
node bench/known_tactics.cjs                  # 10/10
node bench/run_bench.cjs 60 --depth 3 --budget 400 --seed 500   # fp=0
node bench/mine_traps.cjs 100 --depth 4       # a trap density read
```

Then sync:

```
node maintenance/sync_core.cjs
```

Then package (from the extracted dir):

```
zip -r Ashina-10.0.0-Voice-Only.zip .
```
