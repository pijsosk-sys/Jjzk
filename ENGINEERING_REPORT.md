# Engineering Report — ASHINA Queen Hunt v10

## Scope

This report describes the v10 selector that lives in
`maintenance/queen-hunt-core.cjs`, the invariants it enforces, the measurement
harness in `bench/`, and the test suite in `maintenance/test_*.cjs`. It is
written to be read before changing anything; the measurement protocol is
non-negotiable.

## What the selector does

Given the engine's near-best candidate moves (UCI strings, best-first), a
FEN, and a `chess.js`-compatible constructor, the selector returns the
same moves reordered so that a move it has proven leads to a forced
queen win within 8 of our moves is placed first. It never invents a move,
never claims mate, and when it has no confident win it returns the input
unchanged.

```
ashinaQueenHunt(moves, fen, Chess, opts?) ->
  { moves, chosen?, lineUci?, score?, meta? }
```

The `meta` field carries one of:
- `skip: "no-queen" | "mate0" | "no-win" | "validate:*" | "short-confirm"`
- or `tension, tier, swing, captureAt, tier2` on a promoted move.

## Invariants (test-enforced)

1. **No invention.** `r.moves ⊆ cands` — every test that exercises
   promotion checks it.
2. **Never override mate.** If moves[0] already mates, the hunt returns
   `meta.skip = "mate0"` and preserves `moves[0]`.
3. **No-queen is instant.** When the opponent has no queen, return in
   O(ms) with `meta.skip = "no-queen"`.
4. **Promotion requires queen capture by us within 8 of our moves.** The
   validator replays the line and refuses any shorter or non-queen path.
5. **Exchange-settled soundness.** After the queen capture, we let the
   opponent recapture on that square while profitable; the net swing must
   remain ≥ SWING_CP (300 cp). Even trades do not promote.
6. **Triplication.** The core text (everything before `module.exports`) is
   byte-identical in `maintenance/queen-hunt-core.cjs`, `js/Ashina.js`,
   and `js/lichess.js` (verified by `maintenance/test_sync.cjs`).

## Search design

### Two-tier selective alpha-beta

For each candidate, play it, then run iterative deepening alpha-beta from
2 to 15 plies behind the move. Opponent full width always; our tries are
thinned to forcing moves (capture / promotion / check / queen-attack) after
plyFromRoot ≥ 2.

- **TIER1** keeps quiet queen-attacks up to plyFromRoot < 4. Deeper than
  that, only captures, promotions and checks remain for us.
- **TIER2** is only entered when tier-1 found nothing AND the enemy queen
  is attacked AND has ≤ 2 safe escape squares. It keeps quiet queen-
  attacks all the way down. Overhead stays zero in the common case.

### Stand-pat guard (new in v10)

At `sideUs && plyFromRoot ≥ 2`, if `materialDelta(us) - mat0 ≥ SWING_CP`,
the node returns the stand-pat value via `qsearch` instead of continuing to
hunt for forcing moves. This fixed a class of missed wins where the filter
kept only forcing moves and every forcing move hung a piece (classic
quiet-safe-move-blocked bug). The guard is sound because (a) we're already
up enough material to call it a win, and (b) qsearch will still settle
any pending captures.

### Transposition table

Keyed on `position|side|castling|ep_file|depth`. 300k-entry cap with
insertion-order eviction. Cutoffs are honored except at the search root
(where `pvOut` is non-null), so the PV we return for validation is always
built from a real search, not a TT short-circuit. The TT persists across
hunt calls — later moves reuse earlier work.

### Ordering

TT-move > MVV-LVA on captures > killers (2/ply) > history score > queen-
attack bonus. Standard and cheap. The `see()` helper is present but not
wired into ordering yet (see `IMPROVEMENT_PLAN.md` §C).

### Quiescence

Capture-only quiescence to depth 8 at horizon. Draw / insufficient-material
nodes return material (not 0): the hunt is about material swing, not game
result, so a queen capture that leaves K+B vs K should still register as
+swing.

## Validator

After the search returns a candidate PV, `validate()`:

1. Replays the PV move by move.
2. Finds the first move where `did.captured === 'q'` AND the mover is us.
3. Requires `ourMoves ≤ 8` at that point.
4. Requires `material(after capture) - material(start) ≥ SWING_CP`.
5. Runs exchange settlement on the capture square: opponent recaptures
   while profitable; we counter-recapture while profitable. Final value
   must still clear SWING_CP.
6. If no queen capture is seen → refuse.

The short-line full-width confirmation (`shortLineConfirm`) deepens 2→4→6
plies under a time cap and confirms the swing against best full-width
defense. This is what killed the "selective search missed a quiet
refutation" class.

## Why the v9-era rewrites were rejected (still true)

The handoff documents an "aggressive per-node queen-aware rewrite" that
made the engine *worse* by adding per-node cost that shallowed the search.
v10 follows the lesson:

- Per-node work is cheap or gated. The stand-pat guard is one comparison;
  tier-2 is behind a static scan; the TT cutoff is one lookup.
- The expensive things (full-width confirm, tier-2 search) only run on
  promoted candidates or rare position shapes.

## Measurement protocol (do not deviate)

1. **Known tactics** (`bench/known_tactics.cjs`): a hand-picked list of
   positions with the winning move(s) and a couple of negative controls.
   Must be 10/10 on every change.
2. **Soundness** (`bench/run_bench.cjs`): random positions, hunt vs.
   material-truth oracle (`matoracle.cjs`). Report:
   - promoted (hunt claimed a win),
   - verified (oracle agreed),
   - **false-pos must stay at 0.**
3. **Mining** (`bench/mine_traps.cjs`): generate positions, find ones with
   a quiet-first-move queen win. Classify by `trulyQuietFirst` (no
   capture, no promotion, **no check**). These are tier-2's purpose.

The generator deliberately creates messy middlegame-ish positions
with both kings, both queens, and a handful of pawns/pieces. Positions
with no pawns can produce insufficient-material draws that look like
false positives to the *eval* oracle but are real material wins — audit
with **`matoracle`** for the hunt.

## Known limits (be honest)

- The hunt only improves when the engine's own list contains the winner
  somewhere in the top ~6. A move at rank 10 is beyond our reach without
  widening the input (IMPROVEMENT_PLAN §D).
- Latency is bounded by the main-thread budget (default ~400 ms). The
  only path to genuinely deep hunts is a Web Worker
  (IMPROVEMENT_PLAN §A), which this sandbox cannot verify.
- The quiescence doesn't do SEE pruning; it accepts all captures. On
  pathological positions this can bloat the leaf count. The budget
  catches it.

## Files

- `maintenance/queen-hunt-core.cjs` — the selector, the single source of
  truth.
- `maintenance/sync_core.cjs` — propagates the core body into the two
  integration files.
- `maintenance/test_queen_hunt.cjs` — core API tests.
- `maintenance/test_queen_hunt_tactics.cjs` — fork / pin / skewer tests.
- `maintenance/test_queen_hunt_edge.cjs` — edge-case tests.
- `maintenance/test_queen_hunt_soundness.cjs` — randomized soundness.
- `maintenance/test_client.cjs` — integration-style call-site tests.
- `maintenance/test_veteran.cjs` — regression positions.
- `maintenance/test_sync.cjs` — triplication enforcement.
- `bench/oracle.cjs`, `bench/matoracle.cjs`, `bench/oracle_tt.cjs` — three
  oracles. Use `matoracle` for soundness audits.
- `bench/mine_traps.cjs` — trap generator.
- `bench/run_bench.cjs` — the random-position auditor.
- `bench/known_tactics.cjs` — the hand-picked suite.
- `js/Ashina.js`, `js/lichess.js` — content-script skeletons.
- `html/options.html` — option page with the Queen Hunt toggle.
- `manifest.json` — MV3 manifest.
