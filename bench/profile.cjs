// Quick latency profiler: run the hunt on a fixed set of positions,
// instrument the few expensive subroutines, print rough breakdowns.
'use strict';
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('../maintenance/queen-hunt-core.cjs');

const POSITIONS = [
  '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1',
  '4k3/8/8/8/8/4q3/8/4R1K1 w - - 0 1',
  '4k3/7q/8/3N4/8/8/8/6K1 w - - 0 1',
  'r1bqkbnr/pppp1ppp/2n5/1B2p3/4P3/5N2/PPPP1PPP/RNBQK2R b KQkq - 3 3',
  'r1bq1rk1/pp2bppp/2n1pn2/3p4/3P4/2NBPN2/PPQB1PPP/R4RK1 w - - 0 10',
  '4k2r/8/8/8/4Q3/8/2q5/4K2R b Kk - 0 1',
  '4k3/8/8/8/8/8/8/3QK3 w - - 0 1', // no-queen quick path
  '4k3/4q3/8/3N4/8/8/8/4R2K w - - 0 1',
];

function run() {
  for (const budgetMs of [200, 400, 700]) {
    const times = [];
    for (const fen of POSITIONS) {
      const ch = new Chess(fen);
      const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
      if (!legal.length) continue;
      const cands = legal.slice(0, 6);
      ttClear();
      const t0 = Date.now();
      const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs });
      const dt = Date.now() - t0;
      times.push({ fen: fen.slice(0, 40) + '...', dt, chosen: r.chosen, meta: r.meta?.skip || r.meta?.tension });
    }
    times.sort((a, b) => a.dt - b.dt);
    const mean = Math.round(times.reduce((a, b) => a + b.dt, 0) / times.length);
    const p50 = times[Math.floor(times.length * 0.5)].dt;
    const p90 = times[Math.min(times.length - 1, Math.floor(times.length * 0.9))].dt;
    const p99 = times[Math.min(times.length - 1, Math.floor(times.length * 0.99))].dt;
    console.log(`budget=${budgetMs}ms  mean=${mean}ms p50=${p50}ms p90=${p90}ms p99=${p99}ms`);
    for (const t of times) console.log(`  ${t.dt}ms chosen=${t.chosen || '-'} ${t.meta}`);
  }
}

run();
