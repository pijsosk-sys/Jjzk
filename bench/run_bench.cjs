// Benchmark: audit v10 hunt against the material-truth oracle over N
// random positions. Reports (promoted, oracle-confirmed, false-positive,
// missed-wins, timing).
// Usage: node bench/run_bench.cjs [N] [--depth 4] [--budget 400] [--seed 11]
'use strict';
const { Chess } = require('chess.js');
const { ashinaQueenHunt, ttClear } = require('../maintenance/queen-hunt-core.cjs');
const { auditMat } = require('./matoracle.cjs');
const { auditQ } = require('./queen_oracle.cjs');
const { bestLine } = require('./oracle_tt.cjs');
const { randomPosition, mulberry32 } = require('./mine_traps.cjs');

function parseArgs() {
  const args = process.argv.slice(2);
  const n = parseInt(args.find(a => /^\d+$/.test(a)) || '100', 10);
  const idx = (f) => { const i = args.indexOf(f); return i > 0 ? parseInt(args[i + 1], 10) : null; };
  return {
    n,
    depth: idx('--depth') ?? 4,
    budget: idx('--budget') ?? 400,
    seed: idx('--seed') ?? 11,
    timings: args.includes('--timings'),
  };
}

function materialCp(ch, us) {
  const PV = { p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 };
  const b = ch.board();
  let m = 0;
  for (let r = 0; r < 8; r++) for (let f = 0; f < 8; f++) {
    const p = b[r][f]; if (!p) continue;
    m += (p.color === us ? 1 : -1) * (PV[p.type] | 0);
  }
  return m;
}

function run() {
  const opts = parseArgs();
  const rng = mulberry32(opts.seed);
  let tried = 0;
  const times = [];
  let promoted = 0, verified = 0, falsePos = 0;
  let missedWins = 0;        // oracle sees ≥300 swing on a move the hunt had but did not promote
  let huntSkipped = 0;
  const falsePosEntries = [];
  const promotedEntries = [];

  const t0 = Date.now();
  while (tried < opts.n) {
    const fen = randomPosition(rng);
    if (!fen) continue;
    tried++;
    const ch = new Chess(fen);
    if (ch.isGameOver()) continue;
    const legal = ch.moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
    if (!legal.length) continue;
    const cands = legal.slice(0, 6);
    ttClear();
    const t1 = Date.now();
    const r = ashinaQueenHunt(cands, fen, Chess, { budgetMs: opts.budget, maxCandidates: 6 });
    const dt = Date.now() - t1;
    times.push(dt);
    if (!r.chosen) {
      huntSkipped++;
      // Scan the candidates: does the queen-specific oracle see a win the
      // hunt missed? (any-material misses are outside the hunt's scope.)
      for (const c of cands) {
        const a = auditQ(fen, c, opts.depth, 300);
        if (a.ok) {
          missedWins++;
          if (falsePosEntries.length < 10) falsePosEntries.push({ kind: 'MISSED_QUEEN', fen, move: c, swing: a.swing });
          break;
        }
      }
      continue;
    }
    promoted++;
    const a = auditMat(fen, r.chosen, opts.depth, 300);
    if (a.ok) verified++; else {
      falsePos++;
      if (falsePosEntries.length < 10) falsePosEntries.push({ fen, chose: r.chosen, swing: a.swing });
    }
    if (promotedEntries.length < 10) promotedEntries.push({ fen, chose: r.chosen, score: r.score, time: dt });
    if (tried % 20 === 0) {
      process.stdout.write(`  ... ${tried}/${opts.n} prom=${promoted} ver=${verified} fp=${falsePos} missed=${missedWins}\n`);
    }
  }
  times.sort((a, b) => a - b);
  const pct = (p) => times[Math.min(times.length - 1, Math.floor(times.length * p))];
  const total = Date.now() - t0;
  console.log('\n=== BENCH RESULT ===');
  console.log('config:', opts);
  console.log('positions:', tried);
  console.log('promoted:', promoted, '(hunt said "win found")');
  console.log('verified:', verified, '(oracle agreed, true positives)');
  console.log('false-pos:', falsePos, '(hunt wrong)');
  console.log('skipped:', huntSkipped, '(hunt returned no-win)');
  console.log('missed-wins:', missedWins, '(hunt said no but oracle found a win in cands)');
  console.log('latency ms p50/p90/p99:', pct(0.5), '/', pct(0.9), '/', pct(0.99));
  console.log('total:', total, 'ms');
  if (falsePosEntries.length) {
    console.log('\nsample positions (false pos + missed wins):');
    for (const e of falsePosEntries) console.log('  ', e);
  }
  if (opts.timings) {
    console.log('\npromoted samples:');
    for (const e of promotedEntries) console.log('  ', e);
  }
  const fs = require('fs');
  const path = require('path');
  fs.writeFileSync(
    path.join(__dirname, 'bench_result.json'),
    JSON.stringify({ opts, tried, promoted, verified, falsePos, huntSkipped, missedWins, latency: { p50: pct(0.5), p90: pct(0.9), p99: pct(0.99) }, total }, null, 2)
  );
  console.log('wrote bench_result.json');
}

run();
