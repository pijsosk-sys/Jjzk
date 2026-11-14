// Hand-picked positions where a queen win is known and verifiable, used
// to measure the hunt's detection rate against a ground-truth list rather
// than a random-oracle disagreement.
'use strict';
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('../maintenance/queen-hunt-core.cjs');
const { auditMat } = require('./matoracle.cjs');

const SUITE = [
  {
    name: 'hanging queen (same diagonal)',
    fen: '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1',
    winning: ['c3e5'],
  },
  {
    name: 'rook skewer: king behind queen on e-file',
    fen: '4k3/8/8/8/8/4q3/8/4R1K1 w - - 0 1',
    winning: ['e1e3'],
  },
  {
    name: 'knight fork of king + queen',
    fen: '4k3/7q/8/3N4/8/8/8/6K1 w - - 0 1',
    winning: ['d5f6'],
  },
  {
    name: 'knight captures corner queen (supported)',
    fen: 'k6q/8/6N1/4B3/8/8/8/6K1 w - - 0 1',
    winning: ['g6h8'],
  },
  {
    name: 'pin exploit: Nf6+ wins pinned queen',
    fen: '4k3/4q3/8/3N4/8/8/8/4R2K w - - 0 1',
    winning: ['d5f6', 'd5c7', 'd5e7'],
  },
  {
    name: 'knight range captures bQ',
    fen: '4k3/8/8/4N3/6q1/8/8/4K3 w - - 0 1',
    winning: ['e5g4'],
  },
  {
    name: 'defended queen capture (should NOT promote)',
    fen: '4r1k1/8/8/4q3/8/8/4Q3/4K3 w - - 0 1',
    winning: [], // no win; any promotion is a false positive
  },
  {
    name: 'queen trade (should NOT promote)',
    fen: 'q3k3/8/8/8/8/8/8/Q3K3 w - - 0 1',
    winning: [], // even trade, insufficient swing
  },
  {
    name: 'black to move hunt: Qxe4',
    fen: '4k2r/8/8/8/4Q3/8/2q5/4K2R b Kk - 0 1',
    winning: ['c2e4'],
  },
  {
    name: 'promotion capturing queen',
    fen: 'q6k/1P6/8/8/8/8/8/4K3 w - - 0 1',
    winning: ['b7a8n', 'b7a8b', 'b7a8r', 'b7a8q'],
  },
];

function runOne(entry) {
  ttClear();
  const ch = new Chess(entry.fen);
  const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const keep = (entry.winning || []).filter(m => legal.includes(m));
  const extra = legal.filter(m => !keep.includes(m));
  const cands = [...keep, ...extra].slice(0, 6);
  const t0 = Date.now();
  const r = ashinaQueenHunt(cands, entry.fen, Chess, { budgetMs: 700 });
  const dt = Date.now() - t0;
  const found = r.chosen;
  const expected = new Set(entry.winning || []);
  let verdict;
  if (expected.size === 0) {
    verdict = found ? 'FALSE_POSITIVE' : 'PASS';
  } else {
    verdict = found && expected.has(found) ? 'PASS' : (found ? 'WRONG_MOVE' : 'MISSED');
  }
  return { name: entry.name, fen: entry.fen, expected: entry.winning, got: found, dt, verdict, score: r.score };
}

function run() {
  const results = SUITE.map(runOne);
  let pass = 0, fail = 0;
  for (const r of results) {
    const mark = r.verdict === 'PASS' ? 'OK' : 'NO';
    console.log(`[${mark}] ${r.name} -> got=${r.got} expected=${r.expected.join('|') || '(none)'} ${r.dt}ms`);
    if (r.verdict === 'PASS') pass++; else fail++;
  }
  console.log(`\nsummary: ${pass}/${results.length} pass`);
  require('fs').writeFileSync(require('path').join(__dirname, 'known_tactics_result.json'), JSON.stringify(results, null, 2));
}

if (require.main === module) run();
module.exports = { SUITE, runOne };
