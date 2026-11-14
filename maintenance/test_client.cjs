// Integration-style tests: simulate the chess.com/lichess call site
// (the engine ships 5–8 near-best UCI candidates; the hunt reorders).
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

function lenientInRange(x, lo, hi, label) {
  assert.ok(x >= lo && x <= hi, `${label} out of range: ${x} not in [${lo},${hi}]`);
}

test('client call site: typical 5-candidate reorder leaves input shape', () => {
  ttClear();
  const fen = 'r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const cands = legal.slice(0, 5);
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.moves.length, cands.length);
  const s = new Set(cands);
  for (const m of r.moves) assert.ok(s.has(m));
});

test('client call site: opts.budgetMs clamps total time', () => {
  ttClear();
  const fen = 'r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const t0 = Date.now();
  ashinaQueenHunt(legal.slice(0, 6), fen, Chess, { budgetMs: 100 });
  lenientInRange(Date.now() - t0, 0, 600, 'budget=100');
});

test('client call site: moves[0] kept when no promotion happens', () => {
  ttClear();
  // A quiet opening position — nothing to hunt. First move preserved.
  const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
  const cands = ['e2e4', 'd2d4', 'c2c4', 'g1f3', 'b1c3'];
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.moves[0], 'e2e4');
});

test('client call site: handles SAN-shaped moves in options string (uci path)', () => {
  ttClear();
  // Only uci-form; just confirm behavior
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const r = ashinaQueenHunt(['c3e5', 'e1e2'], fen, Chess);
  assert.strictEqual(r.chosen, 'c3e5');
});

test('client call site: single candidate passed is still valid', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const r = ashinaQueenHunt(['c3e5'], fen, Chess);
  assert.strictEqual(r.moves[0], 'c3e5');
});

test('client call site: no crash on checkmate position (no legal moves)', () => {
  ttClear();
  // A mated position: it's impossible to call with real candidates, but
  // test the resilience path.
  const fen = 'rnb1k1nr/pppp1ppp/8/4p3/1b2P3/8/PPPPqPPP/RNBQKBNR w KQkq - 0 4';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const r = ashinaQueenHunt(legal.length ? legal : ['a1a1'], fen, Chess);
  assert.ok(r.moves);
});

test('client call site: 8 candidates (lichess multipv=8)', () => {
  ttClear();
  const fen = 'rnbqkbnr/ppp1pppp/8/3p4/4P3/8/PPPP1PPP/RNBQKBNR w KQkq - 0 2';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const cands = legal.slice(0, 8);
  const r = ashinaQueenHunt(cands, fen, Chess);
  assert.strictEqual(r.moves.length, cands.length);
});
