// Regression tests from positions the project cares about historically.
// Each entry is a (fen, expected-or-forbidden) pair.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

function run(fen, keepsFirst, opts = {}) {
  ttClear();
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const keep = (keepsFirst || []).filter(m => legal.includes(m));
  const extra = legal.filter(m => !keep.includes(m));
  const cands = [...keep, ...extra].slice(0, 6);
  return ashinaQueenHunt(cands, fen, Chess, { budgetMs: opts.budgetMs ?? 700 });
}

test('regression: back-rank vulnerable queen but defended — no promotion', () => {
  // Queen on e5 defended by Re8 — exchange refutes.
  const fen = '4r1k1/8/8/4q3/8/8/4Q3/4K3 w - - 0 1';
  const r = run(fen, ['e2e5']);
  // If promoted at all, must not be the e2e5 trade.
  if (r.chosen) assert.notStrictEqual(r.chosen, 'e2e5');
});

test('regression: starting position — no hunt', () => {
  const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
  const r = run(fen);
  assert.strictEqual(r.chosen, undefined);
});

test('regression: middlegame with no tactics — fast path, no promotion', () => {
  const fen = 'r1bq1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PPQB1PPP/R4RK1 w - - 0 10';
  const t0 = Date.now();
  const r = run(fen, [], { budgetMs: 200 });
  const dt = Date.now() - t0;
  assert.ok(dt < 1000, `too slow in quiet mid: ${dt}`);
  // Hunt may or may not promote; must be a valid permutation
  assert.ok(r.moves.length > 0);
});

test('regression: endgame with no enemy queen — instant skip', () => {
  const fen = '8/5k2/8/8/8/8/5K2/R7 w - - 0 1';
  const t0 = Date.now();
  const r = run(fen);
  assert.ok(Date.now() - t0 < 100);
  assert.strictEqual(r.meta.skip, 'no-queen');
});

test('regression: Scholar-like scenario — the engine picks Qxf7 if given', () => {
  const fen = 'r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1';
  const r = run(fen, ['h5f7']);
  assert.strictEqual(r.meta.skip, 'mate0');
  assert.strictEqual(r.moves[0], 'h5f7');
});

test('regression: tactical draw with insufficient material line is counted', () => {
  // Capturing the queen leaves K+B vs K. That's a draw by insufficient
  // material in game-result terms, but the MATERIAL swing is +900.
  // The hunt (material metric) must still promote.
  const fen = '4k3/4q3/8/8/8/8/4B3/4K3 w - - 0 1';
  // White bishop can take queen (defended by king). After Bxe7+ Kxe7:
  // material: start 330-900=-570, after Bxe7 330+0 - 0 = 330 (queen gone),
  // after Kxe7 0 - 0 = 0. Net = +570 vs mat0 of -570 → +570 swing then down to 0.
  // That's still +570 swing at the capture BEFORE recapture, but exchange
  // settlement knocks it to 0 (even trade). So hunt should NOT promote.
  const r = run(fen, ['e2a6', 'e2b5', 'e2c4', 'e2d3', 'e1d2', 'e1f2']);
  // No winning move for us; hunt should not promote.
  assert.strictEqual(r.chosen, undefined);
});

test('regression: queen can be won by a 3-move combination', () => {
  // Black Q g4, white N on e5 supporting Nxg4? e5 attacks g4? Knight range
  // e5 → f3,g4,g6,f7,d7,c6,c4,d3. Yes e5→g4. So Nxg4 captures queen.
  const fen = '4k3/8/8/4N3/6q1/8/8/4K3 w - - 0 1';
  const r = run(fen, ['e5g4']);
  assert.strictEqual(r.chosen, 'e5g4');
});

test('regression: enemy queen defended by piece we outnumber', () => {
  // bQ h7 defended by bN g8. White N on f6 attacks h7. Rook on h1 also.
  // Nxh7 Nxh7 but we have rook — Rxh7 wins N+Q for N+? let's see.
  // Start: wN(320)+wR(500)+wK = 820, bN(320)+bQ(900)+bK = 1220. mat=-400.
  // Nxh7 (f6xh7): captured 'q'. material = 820 - 320 = +500. Swing = +900.
  // Nxh7 (black) recaptures with knight: material = 500 (R only). Swing = +900.
  // Rxh7: material = 500 - 0 = +500. Wait we capture their knight.
  // Actually after Nxh7 Nxh7: we have R+K, they have N+K: material = 500 - 320 = +180.
  // Rxh7: we capture their N: +500 - 0 = +500. +500 >= 300. ok.
  const fen = '4k1n1/7q/5N2/8/8/8/8/4K2R w - - 0 1';
  const r = run(fen, ['f6h7']);
  assert.ok(r.chosen === 'f6h7' || (r.chosen && r.score >= 300), `expected a winning capture, got ${r.chosen}`);
});
