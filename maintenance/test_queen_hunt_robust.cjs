// Robustness tests: illegal inputs, unusual FEN shapes, partial uci.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

test('robust: illegal uci in candidate list is skipped, not fatal', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['garbage1', 'e1e2', 'c3e5', 'xxxx'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.ok(r.moves.length <= cands.length);
  // Must still find c3e5 if it's a win
  assert.strictEqual(r.chosen, 'c3e5');
});

test('robust: too-short uci string ignored', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['e2', '', 'c3e5'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.chosen, 'c3e5');
});

test('robust: bad FEN is non-fatal, returns moves unchanged', () => {
  const r = ashinaQueenHunt(['e2e4'], 'this is not a fen', Chess);
  assert.deepStrictEqual(r.moves, ['e2e4']);
});

test('robust: non-array moves input returns gracefully', () => {
  const r = ashinaQueenHunt(null, '4k3/8/8/8/8/8/8/4K3 w - - 0 1', Chess);
  assert.ok(Array.isArray(r.moves));
});

test('robust: missing ChessCtor returns moves unchanged', () => {
  const r = ashinaQueenHunt(['e2e4'], '4k3/8/8/8/8/8/8/4K3 w - - 0 1', null);
  assert.deepStrictEqual(r.moves, ['e2e4']);
});

test('robust: no crash when opts is unexpected shape', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const r = ashinaQueenHunt(['c3e5', 'e1e2'], fen, Chess, 'not-an-object');
  assert.ok(r.moves);
});

test('robust: budgetMs = 0 returns gracefully', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const t0 = Date.now();
  const r = ashinaQueenHunt(['c3e5', 'e1e2'], fen, Chess, { budgetMs: 0 });
  const dt = Date.now() - t0;
  assert.ok(dt < 500, `bad budget override took ${dt}ms`);
  assert.ok(r.moves);
});

test('robust: very large maxCandidates clamped by input length', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const r = ashinaQueenHunt(['c3e5', 'e1e2'], fen, Chess, { maxCandidates: 10000 });
  assert.strictEqual(r.moves.length, 2);
});

test('robust: tt persists across calls on same fen (chosen stable)', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const r1 = ashinaQueenHunt(['c3e5'], fen, Chess);
  const r2 = ashinaQueenHunt(['c3e5'], fen, Chess);
  assert.strictEqual(r1.chosen, r2.chosen);
});

test('robust: no false positive on a position with material imbalance but no hunt', () => {
  ttClear();
  // White down material (bR + bB + bN vs no white minor/major). Black queens.
  const fen = 'r1b1k3/8/8/8/8/8/8/4K3 w - - 0 1';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  if (legal.length === 0) return; // trivial
  const r = ashinaQueenHunt(legal.slice(0, 6), fen, Chess);
  // No black queen → skip
  assert.strictEqual(r.meta.skip, 'no-queen');
});
