// Full-width material minimax oracle. SLOW. Use for small depths only.
// Evaluates leaves by net material from our POV; considers draws (stalemate,
// 50-move, threefold, insufficient material) as 0 — this is the "eval"
// variant. For material-truth (hunt metric), see matoracle.cjs.
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

function minimax(ch, depth, alpha, beta, us) {
  if (ch.isGameOver()) {
    if (ch.isCheckmate()) {
      const sideUs = ch.turn() === us;
      return sideUs ? -INF : INF;
    }
    return 0;
  }
  if (depth === 0) return material(ch, us);
  const sideUs = ch.turn() === us;
  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return 0; }
  if (!moves.length) return 0;
  // order: captures first
  moves.sort((a, b) => (b.captured ? (PV[b.captured] || 0) : 0) - (a.captured ? (PV[a.captured] || 0) : 0));
  let best = sideUs ? -INF : INF;
  for (const mv of moves) {
    let did; try { did = ch.move(mv); } catch (_) { continue; }
    if (!did) continue;
    const v = minimax(ch, depth - 1, alpha, beta, us);
    ch.undo();
    if (sideUs) { if (v > best) best = v; if (v > alpha) alpha = v; }
    else        { if (v < best) best = v; if (v < beta)  beta  = v; }
    if (alpha >= beta) break;
  }
  return best;
}

// auditMove(fen, uci, depth): does playing `uci` from `fen` lead to a
// material swing of ≥ swingCp against best defense within `depth` plies?
function auditMove(fen, uci, depth = 6, swingCp = 300) {
  const ch = new Chess(fen);
  const us = ch.turn();
  const mat0 = material(ch, us);
  const from = uci.slice(0, 2), to = uci.slice(2, 4);
  const promo = uci.length >= 5 ? uci[4] : undefined;
  let did; try { did = ch.move(promo ? { from, to, promotion: promo } : { from, to }); } catch (_) { return { ok: false, why: 'illegal' }; }
  if (!did) return { ok: false, why: 'illegal' };
  const v = minimax(ch, depth - 1, -INF, INF, us);
  ch.undo();
  const swing = v - mat0;
  return { ok: swing >= swingCp, swing, mat0, after: v };
}

function bestMove(fen, depth = 4) {
  const ch = new Chess(fen);
  const us = ch.turn();
  let moves = ch.moves({ verbose: true });
  moves.sort((a, b) => (b.captured ? (PV[b.captured] || 0) : 0) - (a.captured ? (PV[a.captured] || 0) : 0));
  let best = null, bestV = -INF;
  for (const mv of moves) {
    const did = ch.move(mv);
    const v = minimax(ch, depth - 1, -INF, INF, us);
    ch.undo();
    if (v > bestV) { bestV = v; best = mv; }
  }
  return { uci: best ? best.from + best.to + (best.promotion || '') : null, score: bestV };
}

module.exports = { auditMove, bestMove, minimax, material };
