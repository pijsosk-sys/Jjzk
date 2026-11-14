// Queen-specific audit: does `uci` from `fen` lead to a forced ≥300cp
// material swing that includes a real queen capture BY US within `depth`
// plies against best defense? Returns {ok, swing, capturedQ}.
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

// Minimax returning {v, capturedQByUs}: ok only if swing AND the best line
// had our capture of their queen.
function mm(ch, depth, alpha, beta, us) {
  if (ch.isCheckmate()) {
    const sideUs = ch.turn() === us;
    return { v: sideUs ? -INF : INF, q: false };
  }
  if (ch.isStalemate()) return { v: 0, q: false };
  if (depth === 0 || ch.isDraw()) return { v: material(ch, us), q: false };
  const sideUs = ch.turn() === us;
  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return { v: material(ch, us), q: false }; }
  if (!moves.length) return { v: 0, q: false };
  moves.sort((a, b) => (b.captured ? (PV[b.captured] || 0) : 0) - (a.captured ? (PV[a.captured] || 0) : 0));
  let best = sideUs ? -INF : INF;
  let bestQ = false;
  for (const mv of moves) {
    let did; try { did = ch.move(mv); } catch (_) { continue; }
    if (!did) continue;
    const r = mm(ch, depth - 1, alpha, beta, us);
    ch.undo();
    const capturedQNow = (did.captured === 'q') && sideUs; // only count OUR queen captures
    const qThisLine = r.q || capturedQNow;
    if (sideUs) {
      if (r.v > best) { best = r.v; bestQ = qThisLine; }
      if (r.v > alpha) alpha = r.v;
    } else {
      if (r.v < best) { best = r.v; bestQ = qThisLine; }
      if (r.v < beta) beta = r.v;
    }
    if (alpha >= beta) break;
  }
  return { v: best, q: bestQ };
}

function auditQ(fen, uci, depth = 4, swingCp = 300) {
  const ch = new Chess(fen);
  const us = ch.turn();
  const mat0 = material(ch, us);
  const from = uci.slice(0, 2), to = uci.slice(2, 4);
  const promo = uci.length >= 5 ? uci[4] : undefined;
  let did; try { did = ch.move(promo ? { from, to, promotion: promo } : { from, to }); } catch (_) { return { ok: false, why: 'illegal' }; }
  if (!did) return { ok: false, why: 'illegal' };
  const isQCapture = did.captured === 'q';
  const r = mm(ch, depth - 1, -INF, INF, us);
  ch.undo();
  const swing = r.v - mat0;
  const capturedQByUs = isQCapture || r.q;
  return { ok: swing >= swingCp && capturedQByUs, swing, capturedQByUs };
}

module.exports = { auditQ, material };
