# ASHINA

A Chrome MV3 extension for chess.com and lichess.org: voice coach, board
arrows, analysis presets, and the **8-Move Queen Hunt** (v10).

The hunt is a selector: it takes the engine's near-best candidate moves
and reorders them to prefer a move that forcibly wins the opponent's
queen within 8 of our moves. It never invents a move, never claims mate,
and when it has no confident win it returns the input unchanged.

## Layout

- `maintenance/queen-hunt-core.cjs` — the selector. Single source of truth.
- `maintenance/sync_core.cjs` — propagates the core body into the two
  integration files.
- `maintenance/test_*.cjs` — 48 tests (core API, tactics, edge cases,
  soundness, integration, regression, sync enforcement).
- `bench/` — oracles (`matoracle.cjs`, `oracle.cjs`, `oracle_tt.cjs`),
  the known-tactics suite, the random auditor, the trap miner.
- `js/Ashina.js`, `js/lichess.js` — content-script skeletons. Contain the
  core text between BEGIN/END markers; do not hand-edit.
- `html/options.html` — options page with the Queen Hunt toggle.
- `manifest.json` — MV3 manifest, version 10.0.0.
- `PATCH_NOTES.md`, `IMPROVEMENT_PLAN.md`, `ENGINEERING_REPORT.md`,
  `HANDOFF_1.md`, `HANDOFF_2.md` — read these in that order.

## Build / test

```bash
npm install                                 # chess.js dev dep
node --test maintenance/test_*.cjs          # 48 must stay green
node bench/known_tactics.cjs                # 10/10 hand-picked tactics
node bench/run_bench.cjs 60 --depth 3 --seed 500   # fp must be 0
node maintenance/sync_core.cjs              # propagate core to js/*.js
```

## Discipline

Soundness > strength > latency. Every change gets measured against the
material-truth oracle. Nothing ships with `N× better` claims —
only numbers. See `HANDOFF_1.md` and `ENGINEERING_REPORT.md` for why.
