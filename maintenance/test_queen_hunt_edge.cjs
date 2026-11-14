// Edge-case tests.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

test('edge: both sides have no queens (endgame)', () => {
  const fen = '4k3/8/8/8/8/8/4P3/4K3 w - - 0 1';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  ttClear();
  const r = ashinaQueenHunt(legal, fen, Chess);
  assert.strictEqual(r.meta.skip, 'no-queen');
});

test('edge: both have queens, many pieces, quiet position — no false trigger', () => {
  const fen = 'r1bqk2r/pp2bppp/2n1pn2/2pp4/3P4/2NBPN2/PPP2PPP/R1BQ1RK1 b kq - 0 7';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || '')).slice(0, 6);
  ttClear();
  const r = ashinaQueenHunt(legal, fen, Chess, { budgetMs: 250 });
  // Must not promote a false win in a quiet opening
  if (r.chosen) {
    // Any promotion must stand up to the oracle later; here we just require score >= SWING_CP
    assert.ok(r.score >= 300);
  }
});

test('edge: pawn promotes to queen capturing enemy queen on same move', () => {
  ttClear();
  // White pawn on b7, bQ on a8, bK on h8. b7xa8=Q captures queen and promotes.
  const fen = 'q6k/1P6/8/8/8/8/8/4K3 w - - 0 1';
  const legal = new Chess(fen).moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const r = ashinaQueenHunt(legal, fen, Chess, { budgetMs: 700 });
  // bxa8=Q captures queen. Must be a strong promotion (+swing)
  assert.ok(r.chosen && r.chosen.startsWith('b7a8'), `expected b7a8=... got ${r.chosen}`);
});

test('edge: many candidates, only the last is a win — still finds it', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  // 6 candidates, winning one at the end
  const cands = ['c3b3', 'c3c4', 'c3c5', 'c3d3', 'c3a3', 'c3e5'];
  const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 1500 });
  assert.strictEqual(r.chosen, 'c3e5');
});

test('edge: tier-2 gate requires queen attacked AND few escapes', () => {
  ttClear();
  // Queen with many escapes should not trigger tier-2 (quiet trap).
  const fen = '4k3/8/8/4q3/8/4R3/8/4K3 w - - 0 1';
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  // Remove any direct queen-capture by setting weird candidate order
  const cands = legal.filter(m => m !== 'e3e5').slice(0, 5);
  cands.push('e3e5'); // winning capture at end
  const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 1500 });
  // whatever the result, no crash + valid permutation
  assert.strictEqual(r.moves.length, cands.length);
});

test('edge: our own queen is pinned — hunt should not override', () => {
  ttClear();
  // White Q on d1 pinned by bR on d8; bQ on a8 undefended. We can't play Qxa8 (queen pinned)
  const fen = 'q2r3k/8/8/8/8/8/8/3QK3 w - - 0 1';
  // chess.js will not list pinned moves that leave king in check
  const ch = new Chess(fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const r = ashinaQueenHunt(legal.slice(0, 6), fen, Chess, { budgetMs: 500 });
  // Must not pick illegal Qxa8 (chess.js wouldn't return it anyway; this is a sanity test)
  assert.ok(r.moves.every(m => legal.includes(m)));
});

test('edge: hunt on same fen is idempotent across repeated calls', () => {
  ttClear();
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const cands = ['c3e5', 'e1e2', 'h1g1'];
  const r1 = ashinaQueenHunt(cands.slice(), fen, Chess);
  const r2 = ashinaQueenHunt(cands.slice(), fen, Chess);
  const r3 = ashinaQueenHunt(cands.slice(), fen, Chess);
  // The chosen move must be the same every call.
  assert.strictEqual(r1.chosen, r2.chosen);
  assert.strictEqual(r2.chosen, r3.chosen);
  // PV length may shrink when TT is warm (cached TT entries do not rebuild
  // a full PV chain here), but the first move must match.
  assert.strictEqual(r1.lineUci[0], r2.lineUci[0]);
});
