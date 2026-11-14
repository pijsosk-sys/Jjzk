// Generate random-ish positions and find ones where the material-truth
// oracle says a forced queen win exists, with a QUIET first move. These
// are the §6A "deep quiet queen-trap" positions the TIER1 engine misses.
// Usage: node bench/mine_traps.cjs <nPositions> [--depth 6]
'use strict';
const { Chess } = require('chess.js');
const { trapWin } = require('./oracle_tt.cjs');

function randomPosition(rng) {
  // Place the two kings, both queens, a handful of pieces and pawns.
  const used = new Set();
  const sq = () => {
    while (true) {
      const f = Math.floor(rng() * 8), r = Math.floor(rng() * 8);
      const s = String.fromCharCode(97 + f) + (r + 1);
      if (!used.has(s) && r !== 0 && r !== 7) { used.add(s); return s; }
      if (!used.has(s)) { used.add(s); return s; }
    }
  };
  // Build an 8x8
  const g = Array.from({ length: 8 }, () => Array(8).fill(null));
  const place = (sqStr, piece, color) => {
    const f = sqStr.charCodeAt(0) - 97, r = 8 - parseInt(sqStr[1]);
    g[r][f] = { p: piece, c: color };
  };
  // kings on back ranks-ish
  const wk = ['e1', 'd1', 'c1', 'g1', 'b1'][Math.floor(rng() * 5)]; used.add(wk); place(wk, 'k', 'w');
  const bk = ['e8', 'd8', 'c8', 'g8', 'b8'][Math.floor(rng() * 5)]; used.add(bk); place(bk, 'k', 'b');
  // queens
  const wq = sq(); place(wq, 'q', 'w');
  const bq = sq(); place(bq, 'q', 'b');
  // a few minor/major and pawns
  const nPieces = 3 + Math.floor(rng() * 4);
  const pieces = ['n', 'b', 'r', 'n', 'b', 'p', 'p', 'p'];
  for (let i = 0; i < nPieces; i++) {
    const t = pieces[Math.floor(rng() * pieces.length)];
    if (t === 'p') {
      // pawns only on ranks 2..7
      while (true) {
        const f = Math.floor(rng() * 8);
        const r = 1 + Math.floor(rng() * 6);
        const s = String.fromCharCode(97 + f) + (r + 1);
        if (!used.has(s)) { used.add(s); place(s, 'p', rng() < 0.5 ? 'w' : 'b'); break; }
      }
    } else {
      const s = sq();
      place(s, t, rng() < 0.5 ? 'w' : 'b');
    }
  }
  // build FEN
  const rows = [];
  for (let r = 0; r < 8; r++) {
    let row = ''; let empty = 0;
    for (let f = 0; f < 8; f++) {
      const p = g[r][f];
      if (!p) { empty++; continue; }
      if (empty) { row += empty; empty = 0; }
      let c = p.p;
      if (p.c === 'w') c = c.toUpperCase();
      row += c;
    }
    if (empty) row += empty;
    rows.push(row);
  }
  const turn = rng() < 0.5 ? 'w' : 'b';
  const fen = rows.join('/') + ' ' + turn + ' - - 0 1';
  try {
    const ch = new Chess(fen);
    // reject if side-to-move has no moves, or king-in-check-and-illegal
    if (ch.isCheck()) return null;
    if (ch.moves().length === 0) return null;
    return fen;
  } catch (_) { return null; }
}

function mulberry32(seed) {
  let a = seed >>> 0;
  return function () {
    a |= 0; a = a + 0x6d2b79f5 | 0;
    let t = a;
    t = Math.imul(t ^ t >>> 15, t | 1);
    t ^= t + Math.imul(t ^ t >>> 7, t | 61);
    return ((t ^ t >>> 14) >>> 0) / 4294967296;
  };
}

function main() {
  const N = parseInt(process.argv[2] || '200', 10);
  const depthIdx = process.argv.indexOf('--depth');
  const depth = depthIdx > 0 ? parseInt(process.argv[depthIdx + 1], 10) : 6;
  const seedIdx = process.argv.indexOf('--seed');
  const seed = seedIdx > 0 ? parseInt(process.argv[seedIdx + 1], 10) : 42;
  const rng = mulberry32(seed);
  const traps = [];
  let tried = 0, trulyQuiet = 0, quietButCheck = 0;
  const t0 = Date.now();
  while (tried < N) {
    const fen = randomPosition(rng);
    if (!fen) continue;
    tried++;
    const r = trapWin(fen, depth);
    if (r && r.quietFirst) {
      traps.push({ fen, ...r });
      if (r.trulyQuietFirst) trulyQuiet++; else quietButCheck++;
      if (traps.length <= 5) console.log('TRAP', traps.length, 'trulyQuiet=', r.trulyQuietFirst, fen, 'first=', r.line[0], 'val=', r.val);
    }
    if (tried % 25 === 0) console.log('tried', tried, 'traps', traps.length, 'trulyQuiet', trulyQuiet, 'elapsed', Date.now() - t0, 'ms');
  }
  const outPath = require('path').join(__dirname, 'traps.json');
  require('fs').writeFileSync(outPath, JSON.stringify({ meta: { N, depth, seed, trulyQuiet, quietButCheck }, traps }, null, 2));
  console.log('wrote', outPath, 'count', traps.length, 'trulyQuiet', trulyQuiet);
}

if (require.main === module) main();
module.exports = { randomPosition, mulberry32 };
