// TT-accelerated material oracle for mining hunt positions. Returns the
// best move + its material line against best defense, or null if no swing.
'use strict';
const { Chess } = require('chess.js');

const PV = { p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 };
const INF = 1e9;

function material(ch, us) {
  const b = ch.board();
  let m = 0;
  for (let r = 0; r < 8; r++) for (let f = 0; f < 8; f++) {
    const p = b[r][f]; if (!p) continue;
    m += (p.color === us ? 1 : -1) * (PV[p.type] | 0);
  }
  return m;
}

function tkey(ch, d) {
  const f = ch.fen().split(' ');
  return f[0] + '|' + f[1] + '|' + d;
}

function minimaxTT(ch, depth, alpha, beta, us, tt) {
  if (ch.isCheckmate()) {
    const sideUs = ch.turn() === us;
    return { v: sideUs ? -INF : INF, line: [] };
  }
  if (ch.isStalemate()) return { v: 0, line: [] };
  if (depth === 0 || ch.isDraw()) return { v: material(ch, us), line: [] };
  const k = tkey(ch, depth);
  const cached = tt.get(k);
  if (cached && cached.alpha <= alpha && cached.beta >= beta) return cached;
  const sideUs = ch.turn() === us;
  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return { v: material(ch, us), line: [] }; }
  if (!moves.length) return { v: 0, line: [] };
  moves.sort((a, b) => (b.captured ? (PV[b.captured] || 0) : 0) - (a.captured ? (PV[a.captured] || 0) : 0));
  let best = sideUs ? -INF : INF;
  let bestLine = [];
  let bestMv = null;
  for (const mv of moves) {
    let did; try { did = ch.move(mv); } catch (_) { continue; }
    if (!did) continue;
    const r = minimaxTT(ch, depth - 1, alpha, beta, us, tt);
    ch.undo();
    const v = r.v;
    if (sideUs) {
      if (v > best) { best = v; bestLine = [mv, ...r.line]; bestMv = mv; }
      if (v > alpha) alpha = v;
    } else {
      if (v < best) { best = v; bestLine = [mv, ...r.line]; bestMv = mv; }
      if (v < beta) beta = v;
    }
    if (alpha >= beta) break;
  }
  const res = { v: best, line: bestLine, alpha, beta };
  if (tt.size < 500000) tt.set(k, res);
  return res;
}

// trapWin(fen, depth): returns {trulyQuietFirst, firstIsCheck, captureMove,
// line, val} iff the best line from fen is a win of ≥swingCp whose eventual
// winning capture is a queen capture. `trulyQuietFirst` is true only when
// the first move is neither a capture, nor a promotion, nor a check.
function trapWin(fen, depth = 8, swingCp = 900) {
  const ch = new Chess(fen);
  const us = ch.turn();
  const mat0 = material(ch, us);
  const r = minimaxTT(ch, depth, -INF, INF, us, new Map());
  if (r.v - mat0 < swingCp) return null;
  const line = r.line;
  if (!line.length) return null;
  const first = line[0];
  // Determine whether the first move delivers check.
  const ch2 = new Chess(fen);
  let firstIsCheck = false;
  try {
    ch2.move({ from: first.from, to: first.to, promotion: first.promotion });
    firstIsCheck = ch2.isCheck();
    ch2.undo();
  } catch (_) {}
  const trulyQuietFirst = !first.captured && !first.promotion && !firstIsCheck;
  let captureMove = null;
  for (let i = 0; i < line.length; i++) {
    const mv = line[i];
    if (i % 2 === 0 && mv.captured === 'q') { captureMove = mv; break; }
  }
  if (!captureMove) return null;
  return {
    quietFirst: !first.captured && !first.promotion, // legacy
    trulyQuietFirst,
    firstIsCheck,
    captureMove: captureMove.from + captureMove.to + (captureMove.promotion || ''),
    line: line.map(m => m.from + m.to + (m.promotion || '')),
    val: r.v - mat0,
  };
}

function bestLine(fen, depth = 6) {
  const ch = new Chess(fen);
  const us = ch.turn();
  const r = minimaxTT(ch, depth, -INF, INF, us, new Map());
  return { v: r.v - material(ch, us), line: r.line.map(m => m.from + m.to + (m.promotion || '')) };
}

module.exports = { trapWin, bestLine, minimaxTT, material };
