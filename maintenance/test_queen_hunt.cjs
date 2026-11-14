// Core tests for the ASHINA Queen Hunt selector.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

function movesOf(fen) {
  const ch = new Chess(fen);
  return ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
}

test('no-queen position: hunt skips (opponent has no queen)', () => {
  const fen = '4k3/8/8/8/8/8/8/4K2R w K - 0 1';
  const m = movesOf(fen);
  const r = ashinaQueenHunt(m, fen, Chess);
  assert.strictEqual(r.chosen, undefined);
  assert.strictEqual(r.meta.skip, 'no-queen');
  assert.deepStrictEqual(r.moves, m);
});

test('empty moves: returns empty moves array', () => {
  const r = ashinaQueenHunt([], 'startpos', Chess);
  assert.deepStrictEqual(r.moves, []);
});

test('null-ish args: returns moves unchanged', () => {
  const r = ashinaQueenHunt(['e2e4'], null, Chess);
  assert.deepStrictEqual(r.moves, ['e2e4']);
});

test('mate preserved: never override a mating move', () => {
  // Scholar's mate: Qxf7#. Black still has queen on d8.
  const fen = 'r1bqkb1r/pppp1ppp/2n2n2/4p2Q/2B1P3/8/PPPP1PPP/RNB1K1NR w KQkq - 0 1';
  const m = movesOf(fen);
  // Qxf7 is mate; put it first
  const ordered = ['h5f7', ...m.filter(x => x !== 'h5f7')];
  const r = ashinaQueenHunt(ordered, fen, Chess);
  assert.strictEqual(r.meta.skip, 'mate0');
  assert.strictEqual(r.moves[0], 'h5f7');
});

test('hanging queen: Qxe5 is promoted when in top candidates', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['e1e2', 'c3c8', 'h1g1', 'c3e5', 'c3b3', 'e1d2'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.chosen, 'c3e5');
  assert.ok(r.score >= 300);
  assert.strictEqual(r.moves[0], 'c3e5');
  assert.strictEqual(r.meta.tension, 'HIGH');
});

test('reordering is a permutation of input moves', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['e1e2', 'c3c8', 'h1g1', 'c3e5', 'c3b3', 'e1d2'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.moves.length, cands.length);
  const s = new Set(cands);
  for (const m of r.moves) assert.ok(s.has(m));
});

test('never invents a move', () => {
  ttClear();
  const fen = 'rnb1kbnr/pppp1ppp/8/4p3/4P1q1/5N2/PPPP1PPP/RNBQKB1R w KQkq - 0 1';
  const cands = movesOf(fen).slice(0, 8);
  const r = ashinaQueenHunt(cands, fen, Chess);
  for (const m of r.moves) assert.ok(cands.includes(m), `invented ${m}`);
});

test('defended queen capture is not promoted (exchange refutes)', () => {
  ttClear();
  // Position: black queen on e5 defended by black rook on e8.
  const fen = '4r1k1/8/8/4q3/8/8/4Q3/4K3 w - - 0 1';
  const cands = movesOf(fen);
  // Qxe5 is defended by Re8 → recapture leaves us down a queen for a queen
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.notStrictEqual(r.chosen, 'e2e5');
});

test('deterministic across repeat calls', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['e1e2', 'c3c8', 'h1g1', 'c3e5', 'c3b3', 'e1d2'];
  const r1 = ashinaQueenHunt(cands, fen, Chess);
  ttClear();
  const r2 = ashinaQueenHunt(cands, fen, Chess);
  ttClear();
  const r3 = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r1.chosen, r2.chosen);
  assert.strictEqual(r2.chosen, r3.chosen);
});

test('low tension: fast path, instant-ish', () => {
  ttClear();
  // Quiet opening-like position: no queen attacks pending, no captures threatening
  const fen = 'rnbqkbnr/ppp1pppp/8/3p4/8/5N2/PPPPPPPP/RNBQKB1R w KQkq - 0 2';
  const cands = ['b1c3', 'e2e3', 'd2d4', 'g2g3', 'f3d4', 'f3e5'];
  const t0 = Date.now();
  const r = ashinaQueenHunt(cands, fen, Chess);
  const dt = Date.now() - t0;
  assert.ok(dt < 1200, `too slow ${dt}ms`);
  // whatever the result, moves must still be a permutation
  assert.strictEqual(r.moves.length, cands.length);
});

test('no-queen skip: side has queen but opponent does not', () => {
  // white has queen, black has none
  const fen = '4k3/8/8/8/8/8/8/3QK3 w - - 0 1';
  const cands = movesOf(fen);
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.meta.skip, 'no-queen');
});

test('black-to-move hunt: Qxe4 captures white queen', () => {
  ttClear();
  // Mirror of the hanging-queen test, black to move
  const fen = '4k2r/8/8/8/4Q3/8/2q5/4K2R b Kk - 0 1';
  const cands = ['e8e7', 'c2e4', 'h8g8', 'c2c3', 'c2b2', 'e8d7'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.chosen, 'c2e4');
  assert.strictEqual(r.meta.tension, 'HIGH');
});

test('budget override: opts.budgetMs is honored', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['e1e2', 'c3c8', 'h1g1', 'c3e5', 'c3b3', 'e1d2'];
  const t0 = Date.now();
  const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 50 });
  const dt = Date.now() - t0;
  // may or may not find the win; budget + confirm overhead ≤ ~300ms
  assert.ok(dt < 800, `too slow ${dt}ms`);
  assert.strictEqual(r.moves.length, cands.length);
});
