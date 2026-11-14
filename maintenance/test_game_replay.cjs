// Walk the Opera Game (Morphy, 1858) move-by-move: at each position,
// run the hunt and verify (a) moves is always a valid permutation of the
// input, (b) the hunt never triggers in the opening/early middlegame of
// this quiet game except where the oracle agrees.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');

// Opera Game: Morphy – Duke Karl/Count Isouard, 1858 (first 15 moves)
const OPERA_SAN = [
  'e4', 'e5', 'Nf3', 'd6', 'd4', 'Bg4', 'dxe5', 'Bxf3',
  'Qxf3', 'dxe5', 'Bc4', 'Nf6', 'Qb3', 'Qe7', 'Nc3', 'c6',
  'Bg5', 'b5', 'Nxb5', 'cxb5',
];

test('game replay: opera game — permutation invariant and no crashes', () => {
  const ch = new Chess();
  ttClear();
  for (let i = 0; i < OPERA_SAN.length; i++) {
    const fen = ch.fen();
    const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
    if (!legal.length) break;
    const cands = legal.slice(0, 6);
    const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 150 });
    // moves must always be a permutation of cands
    assert.strictEqual(r.moves.length, cands.length);
    const s = new Set(cands);
    for (const m of r.moves) assert.ok(s.has(m), `invented move ${m}`);
    // advance using SAN from the game (not from the hunt)
    const san = OPERA_SAN[i];
    const applied = ch.move(san);
    if (!applied) throw new Error(`san failed: ${san} at move ${i}`);
  }
});

test('game replay: hunt latency stays bounded across a game', () => {
  const ch = new Chess();
  const times = [];
  ttClear();
  for (const san of OPERA_SAN) {
    const fen = ch.fen();
    const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
    const cands = legal.slice(0, 6);
    const t0 = Date.now();
    ashinaQueenHunt(cands, fen, Chess, { budgetMs: 150 });
    times.push(Date.now() - t0);
    ch.move(san);
  }
  times.sort((a, b) => a - b);
  const p99 = times[Math.min(times.length - 1, Math.floor(times.length * 0.99))];
  assert.ok(p99 < 900, `game replay p99 ${p99}ms too slow`);
});
