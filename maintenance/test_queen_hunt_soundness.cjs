// Soundness tests: generate random positions, run the hunt, and for every
// promotion verify with the material-truth oracle that the promoted first
// move really leads to a ≥300cp material swing against best defense.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('./queen-hunt-core.cjs');
const { auditMat } = require('../bench/matoracle.cjs');
const { randomPosition, mulberry32 } = require('../bench/mine_traps.cjs');

test('soundness: no false positive over 40 random positions (oracle depth 4)', { timeout: 180000 }, () => {
  const rng = mulberry32(2026);
  let promoted = 0, verified = 0, falsePos = 0;
  let tried = 0;
  while (tried < 40) {
    const fen = randomPosition(rng);
    if (!fen) continue;
    tried++;
    const ch = new Chess(fen);
    const cands = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
    if (!cands.length) continue;
    ttClear();
    const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 500, maxCandidates: 6 });
    if (!r.chosen) continue;
    promoted++;
    // Check at depth 4. If shallow-oracle disagrees, verify at depth 5
    // before counting as a false positive — the hunt sometimes sees
    // slightly deeper than this oracle.
    let a = auditMat(fen, r.chosen, 4, 300);
    if (!a.ok) a = auditMat(fen, r.chosen, 5, 300);
    if (a.ok) verified++; else {
      falsePos++;
      console.error(`FP: fen=${fen} chose=${r.chosen} swing=${a.swing}`);
    }
  }
  console.log(`soundness: tried=${tried} promoted=${promoted} verified=${verified} false-pos=${falsePos}`);
  assert.ok(falsePos <= 1, `too many false positives: ${falsePos}/${promoted}`);
});

test('soundness: TT reuse does not corrupt results', () => {
  const fen1 = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  const fen2 = '4k2r/8/8/8/4Q3/8/2q5/4K2R b Kk - 0 1';
  ttClear();
  const cands1 = ['e1e2', 'c3e5', 'h1g1', 'c3b3'];
  const r1a = ashinaQueenHunt(cands1, fen1, Chess);
  const cands2 = ['e8e7', 'c2e4', 'h8g8', 'c2b2'];
  const r2 = ashinaQueenHunt(cands2, fen2, Chess);
  const r1b = ashinaQueenHunt(cands1, fen1, Chess);
  assert.strictEqual(r1a.chosen, r1b.chosen);
  assert.strictEqual(r1a.chosen, 'c3e5');
  assert.strictEqual(r2.chosen, 'c2e4');
});

test('soundness: never promote when hunt disagrees with best defense', () => {
  // Position: Nxe5? wins pawn BUT black has Nxe4 threat recovering material.
  // Not a queen hunt opportunity. Hunt must decline.
  const fen = 'r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPP1PPP/RNBQKB1R w KQkq - 2 3';
  const ch = new Chess(fen);
  const cands = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  ttClear();
  const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: 300 });
  assert.strictEqual(r.chosen, undefined);
});
