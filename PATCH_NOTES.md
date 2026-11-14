# ASHINA — Patch Notes

## v10.0.0 (2026-11-14)

**Full rewrite of the Queen Hunt selector**, measured against the material-truth
oracle and gated by a comprehensive test suite. The hunt still *only reorders*
engine-listed candidates — it never invents a move, never claims mate.

### What's new

- **Persistent transposition table** across calls in a session (300k entries,
  insertion-order LRU eviction). Later moves in a game reuse earlier search
  work.
- **Stand-pat guard** inside the selective search: if we are already up
  material ≥ SWING_CP at a non-root node, the search returns immediately
  instead of forcing continuations that may hang a piece. This fixed a class
  of missed wins where the previous filter kept only check/capture replies,
  all of which hung the rook for no reason.
- **Gated two-tier search.** TIER1 is the fast, selective alpha-beta:
  full-width for the opponent, forcing-only for us after 2 plies. TIER2 is
  only triggered when the enemy queen is already attacked AND has ≤ 2 safe
  escape squares, and widens our tries to include quiet queen-attacks deeper
  in the tree. TIER2 overhead never touches the common case.
- **Full-width short-line confirmation** with iterative deepening (depth
  2→4→6, time-bounded). Replaces the previous capture-quiescence confirm
  with a search that actually sees quiet refutations.
- **Exchange-settled validator.** The validator replays the chosen line,
  requires a real queen capture by us within 8 of our moves, requires a
  ≥ SWING_CP net material swing AFTER the opponent's best recapture
  sequence on the capture square. No more "promoted an even trade."
- **Game-over handling** respects the hunt's metric: insufficient-material
  and 50-move/threefold "draws" after a queen capture are scored by
  **material**, not by the chess.js game result. The hunt is about material,
  not game outcome.
- **Budget scheduling**: the main entry divides its budget evenly across the
  remaining candidates so a single slow candidate can't consume everything
  and starve the actual winner.
- **No-queen fast path**: if the opponent has no queen, the hunt returns
  the input unchanged in ~1 ms.
- **Preserves mate**: if moves[0] is a mating move, the hunt refuses to
  override it (`meta.skip = 'mate0'`).

### Measured on this build

- **Tests**: 46/46 passing (`node --test maintenance/test_*.cjs`), including
  the sync enforcement that the core text is byte-identical between
  `maintenance/queen-hunt-core.cjs` and the two integration files
  `js/Ashina.js`, `js/lichess.js`.
- **Known tactics (bench/known_tactics.cjs)**: 10/10 hand-picked positions
  pass. Covers hanging-queen, pin, skewer, fork, promotion-capture,
  black-to-move, and two negative controls (even queen trade, defended
  queen).
- **Random audit (bench/run_bench.cjs, 60 positions, depth-3 oracle, 400 ms
  budget)**: 10 promotions, 10/10 verified by the material-truth oracle,
  **0 false positives**, latency p50 / p90 / p99 ≈ 415 / 1210 / 1234 ms.
- **Quiet-trap mining (bench/mine_traps.cjs, depth 4, 60 positions)**: 1
  position where the oracle's winning line has a non-capture first move
  was found, and that move is a check (TIER1 catches it). No truly-quiet
  (non-check, non-capture, non-promotion) first-move queen traps appeared
  in this seed. **Tier-2 is on the shelf** for the rare case it's needed;
  tier-1 handles everything observed so far.

### What hasn't changed

- The selector still only reorders engine-listed candidates.
- Mate lines are preserved verbatim.
- No-queen positions skip instantly.
- The public API is unchanged: `ashinaQueenHunt(moves, fen, Chess, opts?)`
  returning `{ moves, chosen?, lineUci?, score?, meta? }`.

### Upgrade notes

Edit `maintenance/queen-hunt-core.cjs` as the single source of truth and run
`node maintenance/sync_core.cjs` to propagate the pre-`module.exports` block
into `js/Ashina.js` and `js/lichess.js`. `node --test maintenance/test_*.cjs`
enforces the triplication.
