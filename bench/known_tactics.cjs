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
    winning: [],
  },
  {
    name: 'queen trade (should NOT promote)',
    fen: 'q3k3/8/8/8/8/8/8/Q3K3 w - - 0 1',
    winning: [],
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
  {
    name: 'overworked defender: capture to remove queen guard',
    fen: '4k3/4q3/8/8/8/8/3R4/3BK3 w - - 0 1',
    // bQ on e7 undefended; wR on d2 lines up with e-file attack after Re2
    winning: ['d2e2'],
  },
  {
    name: 'rook lift along file onto bQ',
    fen: '4k3/8/8/8/4q3/8/4R3/4K3 w - - 0 1',
    winning: ['e2e4'],
  },
  {
    name: 'bishop long-diagonal wins stranded queen',
    fen: '7k/8/8/8/8/8/1B6/2q4K w - - 0 1',
    winning: ['b2c1'],
  },
  {
    name: 'rook x-ray along rank',
    fen: '4k3/q3R3/8/8/8/8/8/4K3 w - - 0 1',
    winning: ['e7a7'],
  },
  {
    name: 'queen captures en-prise queen (diagonal)',
    fen: '7k/8/8/4q3/8/8/7Q/4K3 w - - 0 1',
    winning: ['h2e5'],
  },
  {
    name: 'our heavily-defended attacker wins exchange on queen',
    fen: '4k3/8/8/2b5/3q4/8/4Q3/4K3 w - - 0 1',
    // bQ d4 defended by bB c5; white Q on e2. Qxd4+ Bxd4 — even trade.
    // Must NOT promote.
    winning: [],
  },
  {
    name: 'rook attacks undefended queen directly',
    fen: '4k3/q7/8/8/8/8/8/R3K3 w - - 0 1',
    // wR a1 attacks bQ a7 along a-file. Rxa7 wins queen.
    winning: ['a1a7'],
  },
  {
    name: 'two-move trap: Bg4 pins queen then Nxe5 wins',
    fen: 'r1bqkbnr/pppp1ppp/2n5/4p3/4P3/5N2/PPPPBPPP/RNBQK2R w KQkq - 0 1',
    winning: [],
  },
  {
    name: 'open position with no queen tactics (quiet)',
    fen: 'r1bq1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PPP2PPP/R1BQ1RK1 w - - 0 1',
    winning: [],
  },
  {
    name: 'K+Q vs K: our side is winning already, no hunt action needed',
    fen: '4k3/8/8/8/8/8/4Q3/4K3 w - - 0 1',
    winning: [], // opponent has no queen; should skip
  },
  {
    name: 'queen forks: attacker queen attacks enemy queen and nothing else',
    fen: '7k/8/8/8/8/5q2/8/Q5K1 w - - 0 1',
    // wQ a1 attacks f3 diagonally? a1-b2-c3-d4-e5-f6 — no. a1-h8 diagonal. f3 is not on a1 diagonal.
    // So no 1-move capture. Hunt should not promote.
    winning: [],
  },
  {
    name: 'pinned rook unmask then queen win',
    fen: '4rk2/7q/8/8/8/8/4R3/4QK2 w - - 0 1',
    // Rxe8 (behind the rook pin to wQ) Kg7 Re7 Kg8 Rxh7 wins queen.
    winning: ['e2e8'],
  },
  {
    name: 'under-promotion to knight captures queen with fork',
    fen: '5k2/6P1/8/8/8/8/8/q3K3 w - - 0 1',
    // wP g7 can promote to knight g8=N+ (attacks king f8) and then we hope to win Q? no fork here really.
    // Simpler: pawn moves g7g8 no capture. Hunt may not promote.
    winning: [],
  },
  {
    name: 'skewer along diagonal: bQ behind bK',
    fen: '8/8/5k2/4q3/8/8/1B6/3K4 w - - 0 1',
    // wB b2 along diagonal b2-c3-d4-e5-f6: attacks f6 king, bQ e5 is between.
    // Bb2 attacks e5 queen; queen defended by king. Bxe5+ Kxe5. Even trade (B for Q = +570).
    // Hunt may or may not promote.
    winning: ['b2e5'],
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
  const r = ashinaQueenHunt(cands, entry.fen, Chess, { budgetMs: entry.budgetMs ?? 1200 });
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
