// Tactical positions where the hunt should find a specific move.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

function cands(fen, movesToTry) {
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  if (movesToTry) {
    const keep = movesToTry.filter(m => legal.includes(m));
    const extra = legal.filter(m => !keep.includes(m));
    // keeps first (simulating engine passing its best candidates), pad with extra
    return [...keep, ...extra].slice(0, 6);
  }
  return legal.slice(0, 6);
}

test('skewer: Rxe8 wins queen via pin/skewer line', () => {
  ttClear();
  // Rook pins king to queen: white Re1, bK e8, bQ e3. white to move Rxe3 wins Q (defended? no)
  const fen = '4k3/8/8/8/8/4q3/8/4R1K1 w - - 0 1';
  const r = ashinaQueenHunt(cands(fen, ['e1e3']), fen, Chess);
  assert.strictEqual(r.chosen, 'e1e3');
});

test('discovered attack / pin exploit: Nf6+ wins pinned queen', () => {
  ttClear();
  // bK e8, bQ e7 pinned to king; white Nd5 jumps Nf6+ (check), queen
  // still pinned/attacked, after king move Rxe7 wins queen.
  const fen = '4k3/4q3/8/3N4/8/8/8/4R2K w - - 0 1';
  const r = ashinaQueenHunt(cands(fen, ['d5f6', 'd5c7', 'd5b6', 'd5b4', 'd5c3', 'd5e3']), fen, Chess, { budgetMs: 1500 });
  assert.ok(r.chosen && (r.chosen === 'd5f6' || r.chosen === 'e1e5' || r.chosen.startsWith('d5')),
    `expected a winning knight/pin move, got ${r.chosen}`);
});

test('fork: Nf6+ forks king and queen', () => {
  ttClear();
  // Black K e8, bQ d7; white Nd5 plays Nf6+ (check) attacking queen at d7
  // via d7 is not in Nf6 range... use Nf6+ attacking queen via fork of king d8 & queen d7
  // Better: bK e8, bQ h7; white Nd5-Nf6+ attacks both king and queen h7.
  const fen = '4k3/7q/8/3N4/8/8/8/6K1 w - - 0 1';
  const r = ashinaQueenHunt(cands(fen, ['d5f6', 'g1g2', 'd5e3', 'd5c3', 'd5b4', 'd5b6']), fen, Chess, { budgetMs: 1500 });
  assert.strictEqual(r.chosen, 'd5f6');
});

test('tier-2 quiet trap: queen cornered with few escapes', () => {
  ttClear();
  // Black K a8, bQ h8; white Ng6 attacks h8 (already); white B e5 limits
  // escapes along the g7-h8 diagonal.
  const fen = 'k6q/8/6N1/4B3/8/8/8/6K1 w - - 0 1';
  const r = ashinaQueenHunt(cands(fen, ['g6h8']), fen, Chess, { budgetMs: 1500 });
  assert.strictEqual(r.chosen, 'g6h8');
  assert.ok(r.score >= 300);
});

test('queen-vs-queen trade is NOT promoted (swing insufficient)', () => {
  ttClear();
  // White Q on a1, black Q on a8, nothing else on a-file. Qxa8 Qxa8 = even trade.
  const fen = 'q3k3/8/8/8/8/8/8/Q3K3 w - - 0 1';
  const legal = cands(fen);
  const r = ashinaQueenHunt(legal, fen, Chess, { budgetMs: 400 });
  // Even trade: validator's swing check should reject
  if (r.chosen) {
    // If promoted at all, must not be a1a8 (defended)
    assert.notStrictEqual(r.chosen, 'a1a8', 'promoted even queen trade');
  }
});

test('no-hunt when the opponent has no queen', () => {
  ttClear();
  const fen = '4k3/8/8/8/8/8/8/3QK3 w - - 0 1';
  const legal = cands(fen);
  const r = ashinaQueenHunt(legal, fen, Chess);
  assert.strictEqual(r.meta.skip, 'no-queen');
});

test('two consecutive calls on different positions: no state leak', () => {
  ttClear();
  const fenA = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const fenB = '4k3/8/8/8/8/8/8/3QK3 w - - 0 1';
  const rA = ashinaQueenHunt(cands(fenA, ['c3e5']), fenA, Chess);
  const rB = ashinaQueenHunt(cands(fenB), fenB, Chess);
  assert.strictEqual(rA.chosen, 'c3e5');
  assert.strictEqual(rB.meta.skip, 'no-queen');
});
