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

- **Tests**: 58/58 passing (`node --test maintenance/test_*.cjs`), including
  the sync enforcement that the core text is byte-identical between
  `maintenance/queen-hunt-core.cjs` and the two integration files
  `js/Ashina.js`, `js/lichess.js`, plus robustness tests for bad inputs.
- **Known tactics (bench/known_tactics.cjs)**: 24/24 hand-picked positions
  pass. Covers hanging-queen, pin, skewer, fork, promotion-capture,
  overworked defender, rook lift, bishop diagonal, x-ray, black-to-move,
  and six negative controls (even queen trade, defended queen, opening
  position, etc.).
- **Random audit, depth-3 oracle** (`bench/run_bench.cjs 60 --depth 3
  --seed 500 --budget 400`): 10 promotions, 10/10 verified, **0 false
  positives**, p50 / p90 / p99 ≈ 415 / 1210 / 1234 ms.
- **Random audit, depth-4 oracle, budget 700** (`bench/run_bench.cjs 40
  --depth 4 --seed 777 --budget 700`): 40 legal positions, 2 promoted,
  2/2 verified by the queen-specific oracle, **0 false positives**,
  2 queen-wins missed (those needed >700 ms budget to find).
- **Oracle note**: `bench/queen_oracle.cjs` audits specifically for
  lines where OUR move captures the enemy queen. The earlier
  "any-material" oracle counted non-queen material gains as "missed"
  hunts, which is outside the hunt's scope.
- **Confirm cap**: short-line confirmation is bounded by
  `min(200ms, max(60ms, budget/3))` so total wall time ≈ 1.3 × budget
  instead of the 2 × budget of the first draft. Depth 2 is always run
  (widened cushion) so complex positions don't silently fail the
  confirmation step.
- **Pre-scoring of candidates**: the entry filter ranks the engine's
  candidates by how likely each is to win a queen (direct capture >
  big-piece capture > promotion > check > queen-attack > rest) and
  processes the high-value ones first. Critical when the engine
  surfaces the winning move at the back of its MultiPV list.
- **PV extension from TT**: when the search returns a short PV because
  a cached cutoff fired, walk the TT best-move chain to extend it so
  the validator sees the eventual queen capture.
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
