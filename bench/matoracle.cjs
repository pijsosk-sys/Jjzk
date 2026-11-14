// Material-truth oracle for the queen hunt. Unlike oracle.cjs this treats
// insufficient-material endings as still-material-positive if we have the
// winning material; a hunt "wins the queen" is a material statement, not
// a draw/win classification. Stalemate still scores 0.
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

function minimaxMat(ch, depth, alpha, beta, us) {
  if (ch.isCheckmate()) {
    const sideUs = ch.turn() === us;
    return sideUs ? -INF : INF;
  }
  if (ch.isStalemate()) return 0;
  // 50-move and threefold and insufficient-material: use current material
  // (hunt metric — we care about material, not game result).
  if (depth === 0 || ch.isDraw()) return material(ch, us);
  const sideUs = ch.turn() === us;
  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return material(ch, us); }
  if (!moves.length) return 0;
  moves.sort((a, b) => (b.captured ? (PV[b.captured] || 0) : 0) - (a.captured ? (PV[a.captured] || 0) : 0));
  let best = sideUs ? -INF : INF;
  for (const mv of moves) {
    let did; try { did = ch.move(mv); } catch (_) { continue; }
    if (!did) continue;
    const v = minimaxMat(ch, depth - 1, alpha, beta, us);
    ch.undo();
    if (sideUs) { if (v > best) best = v; if (v > alpha) alpha = v; }
    else        { if (v < best) best = v; if (v < beta)  beta  = v; }
    if (alpha >= beta) break;
  }
  return best;
}

function auditMat(fen, uci, depth = 6, swingCp = 300) {
  const ch = new Chess(fen);
  const us = ch.turn();
  const mat0 = material(ch, us);
  const from = uci.slice(0, 2), to = uci.slice(2, 4);
  const promo = uci.length >= 5 ? uci[4] : undefined;
  let did; try { did = ch.move(promo ? { from, to, promotion: promo } : { from, to }); } catch (_) { return { ok: false, why: 'illegal' }; }
  if (!did) return { ok: false, why: 'illegal' };
  const v = minimaxMat(ch, depth - 1, -INF, INF, us);
  ch.undo();
  const swing = v - mat0;
  return { ok: swing >= swingCp, swing, mat0, after: v };
}

module.exports = { auditMat, minimaxMat, material };
