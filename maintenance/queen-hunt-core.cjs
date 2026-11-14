// ASHINA Queen Hunt — selector core.
// Reorders engine-listed candidate moves to prefer a forced queen win
// within 8 of our moves. Never invents a move. Never claims mate.
// Public entry: ashinaQueenHunt(moves, fen, Chess, opts?)
//   moves:    string[] of UCI moves (engine's near-best, best-first)
//   fen:      string
//   Chess:    chess.js constructor (must expose move/undo/moves/turn/
//             fen/board/get/isCheck/isCheckmate/isGameOver/isAttacked)
//   opts:     { budgetMs?, maxCandidates?, debug? }
// Returns { moves, chosen?, lineUci?, score?, meta? }
//   moves is always a permutation of the input moves.
//   chosen is set iff the hunt promoted a move and the validator passed.

'use strict';

const HORIZON_PLIES = 16;         // 8 of our moves
const SWING_CP = 300;             // material swing threshold
const Q_VAL = 900;
const PV = Object.freeze({ p: 100, n: 320, b: 330, r: 500, q: 900, k: 0 });
const INF = 1e9;

// ---------- persistent TT (across calls; gated by key) ----------
const _TT = new Map();
const TT_MAX = 300000;
const FLAG_EXACT = 0, FLAG_LOWER = 1, FLAG_UPPER = 2;

function ttKey(ch, depth, side) {
  const p = ch.fen().split(' ');
  // position + side + castling + ep file (ignore clocks)
  return p[0] + '|' + side + '|' + p[2] + '|' + (p[3] === '-' ? '-' : p[3][0]) + '|' + depth;
}

function ttStore(key, depth, val, flag, bestMove) {
  if (_TT.size >= TT_MAX) {
    // evict the oldest 10% (insertion order)
    const kill = Math.floor(TT_MAX * 0.1);
    let i = 0;
    for (const k of _TT.keys()) { if (i++ >= kill) break; _TT.delete(k); }
  }
  _TT.set(key, { depth, val, flag, bestMove });
}
function ttGet(key) { return _TT.get(key); }
function ttClear() { _TT.clear(); }

// ---------- helpers ----------
function uciToMove(uci) {
  if (!uci || uci.length < 4) return null;
  const from = uci.slice(0, 2);
  const to = uci.slice(2, 4);
  const promo = uci.length >= 5 ? uci[4] : undefined;
  return promo ? { from, to, promotion: promo } : { from, to };
}
function moveToUci(m) {
  if (!m) return null;
  return m.from + m.to + (m.promotion || '');
}
function queenSq(ch, color) {
  const b = ch.board();
  for (let r = 0; r < 8; r++) {
    for (let f = 0; f < 8; f++) {
      const p = b[r][f];
      if (p && p.type === 'q' && p.color === color) {
        return String.fromCharCode(97 + f) + (8 - r);
      }
    }
  }
  return null;
}
function materialDelta(ch, usColor) {
  const b = ch.board();
  let m = 0;
  for (let r = 0; r < 8; r++) {
    for (let f = 0; f < 8; f++) {
      const p = b[r][f];
      if (!p) continue;
      const v = PV[p.type] | 0;
      m += (p.color === usColor) ? v : -v;
    }
  }
  return m;
}
function pval(t) { return PV[t] | 0; }

function safeIsAttacked(ch, sq, byColor) {
  try { return ch.isAttacked(sq, byColor); } catch (_) { return false; }
}

// Check if moving `mv` is forcing (capture, promotion, check, attacks enemy Q).
function isForcing(ch, mv, enemyQSq) {
  if (mv.captured || mv.promotion) return true;
  const didMove = safeMove(ch, mv);
  if (!didMove) return false;
  let r = ch.isCheck();
  if (!r && enemyQSq) {
    const defender = ch.turn();           // side to move after our move
    const us = defender === 'w' ? 'b' : 'w';
    const eqNow = queenSq(ch, defender);  // queen may have been captured
    if (eqNow && eqNow === enemyQSq) {
      r = safeIsAttacked(ch, enemyQSq, us);
    } else if (eqNow) {
      r = safeIsAttacked(ch, eqNow, us);
    }
  }
  ch.undo();
  return r;
}

function isQueenAttack(ch, mv, enemyQSq) {
  if (!enemyQSq || mv.captured) return false;
  const didMove = safeMove(ch, mv);
  if (!didMove) return false;
  const defender = ch.turn();
  const us = defender === 'w' ? 'b' : 'w';
  const eqNow = queenSq(ch, defender);
  let r = false;
  if (eqNow) r = safeIsAttacked(ch, eqNow, us);
  ch.undo();
  return r;
}

function safeMove(ch, mv) {
  try {
    return ch.move({ from: mv.from, to: mv.to, promotion: mv.promotion });
  } catch (_) { return null; }
}

// Queen safe-escape count: how many squares the enemy queen can move to
// without landing on a square we attack. Returns 999 if we can't compute.
function queenSafeEscapes(ch, qSq, ChessCtor) {
  const piece = ch.get(qSq);
  if (!piece || piece.type !== 'q') return 999;
  const qColor = piece.color;
  const myTurn = ch.turn();
  let qCh = ch;
  let flipped = false;
  if (myTurn !== qColor) {
    const parts = ch.fen().split(' ');
    parts[1] = qColor;
    parts[3] = '-';
    parts[4] = '0';
    try { qCh = new ChessCtor(parts.join(' ')); flipped = true; }
    catch (_) { return 999; }
  }
  let mvs;
  try { mvs = qCh.moves({ square: qSq, verbose: true }); }
  catch (_) { return 999; }
  const us = qColor === 'w' ? 'b' : 'w';
  let safe = 0;
  for (const m of mvs) {
    const did = safeMove(qCh, m);
    if (!did) continue;
    const landed = safeIsAttacked(qCh, m.to, us);
    qCh.undo();
    if (!landed) safe++;
  }
  return safe;
}

// Static exchange eval (greedy approximation): after the capture lands on
// `toSq`, how much material do we end with (us POV, cp)?
function see(ch, toSq, usColor, ChessCtor) {
  // snapshot material; let both sides keep capturing on `toSq`; stop when
  // next LVA either side has no attacker or losing.
  const before = materialDelta(ch, usColor);
  let turn = ch.turn();
  const gains = [];
  // find least-valued attacker on toSq by `turn`
  const allMoves = (side) => {
    try { return ch.moves({ verbose: true }).filter(m => m.to === toSq && m.color === side); }
    catch (_) { return []; }
  };
  while (true) {
    const atks = allMoves(turn).sort((a, b) => pval(a.piece) - pval(b.piece));
    if (!atks.length) break;
    const did = safeMove(ch, atks[0]);
    if (!did) break;
    gains.push(materialDelta(ch, usColor));
    turn = ch.turn();
  }
  // restore
  for (let i = 0; i < gains.length; i++) ch.undo();
  const final = gains.length ? gains[gains.length - 1] : before;
  return final;
}

// ---------- alpha-beta with selective pruning ----------
// Full width for opponent. For us: capture/check/promo/queen-attack after
// depth 2 in TIER1; also allow quiet queen attacks in TIER2.
function orderMoves(ch, moves, enemyQSq, ttMv, killers, history, usColor) {
  const scored = moves.map(m => {
    let s = 0;
    if (ttMv && m.from === ttMv.from && m.to === ttMv.to && (m.promotion || '') === (ttMv.promotion || '')) s += 1e8;
    if (m.captured) s += 1e5 + 10 * pval(m.captured) - pval(m.piece);
    if (m.promotion) s += 9e4;
    if (killers) {
      for (const k of killers) {
        if (k && k.from === m.from && k.to === m.to) { s += 7e4; break; }
      }
    }
    if (history) {
      const h = history[(m.from + m.to)] | 0;
      s += h;
    }
    if (enemyQSq) {
      const did = safeMove(ch, m);
      if (did) {
        const def = ch.turn();
        const us = def === 'w' ? 'b' : 'w';
        const eq = queenSq(ch, def);
        if (eq && safeIsAttacked(ch, eq, us)) s += 2e4;
        ch.undo();
      }
    }
    return { m, s };
  });
  scored.sort((a, b) => b.s - a.s);
  return scored.map(x => x.m);
}

function qsearch(ch, alpha, beta, usColor, depth) {
  const stand = materialDelta(ch, usColor);
  const sideUs = ch.turn() === usColor;
  if (sideUs) {
    if (stand >= beta) return beta;
    if (stand > alpha) alpha = stand;
  } else {
    if (stand <= alpha) return alpha;
    if (stand < beta) beta = stand;
  }
  if (depth >= 8) return sideUs ? alpha : beta;
  let moves;
  try { moves = ch.moves({ verbose: true }).filter(m => m.captured || m.promotion); }
  catch (_) { return sideUs ? alpha : beta; }
  if (!moves.length) return sideUs ? alpha : beta;
  moves.sort((a, b) => (10 * pval(b.captured || 'p') - pval(b.piece)) - (10 * pval(a.captured || 'p') - pval(a.piece)));
  for (const mv of moves) {
    const did = safeMove(ch, mv);
    if (!did) continue;
    const v = qsearch(ch, alpha, beta, usColor, depth + 1);
    ch.undo();
    if (sideUs) { if (v > alpha) alpha = v; if (alpha >= beta) return beta; }
    else        { if (v < beta)  beta  = v; if (alpha >= beta) return alpha; }
  }
  return sideUs ? alpha : beta;
}

function search(state, depth, alpha, beta, pvOut) {
  const { ch, usColor, deadline, tier, ChessCtor } = state;
  if (Date.now() > deadline) { state.aborted = true; return 0; }
  if (ch.isCheckmate()) {
    const us = ch.turn() === usColor;
    return us ? -INF + (state.plyRoot - depth) : INF - (state.plyRoot - depth);
  }
  if (ch.isStalemate()) return 0;
  // Threefold, 50-move, insufficient-material: treat as material leaf
  // (the hunt is about material swing, not game result).
  if (ch.isDraw && ch.isDraw()) return materialDelta(ch, usColor);
  if (depth <= 0) {
    return qsearch(ch, alpha, beta, usColor, 0);
  }
  const sideUs = ch.turn() === usColor;
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  const defColor = sideUs ? enemyColor : usColor;
  const enemyQ = sideUs ? queenSq(ch, enemyColor) : null;

  const isRoot = pvOut && depth === state.plyRoot;
  const key = ttKey(ch, depth, ch.turn());
  const tt = ttGet(key);
  if (!isRoot && tt && tt.depth >= depth) {
    if (tt.flag === FLAG_EXACT) return tt.val;
    if (tt.flag === FLAG_LOWER && tt.val >= beta) return tt.val;
    if (tt.flag === FLAG_UPPER && tt.val <= alpha) return tt.val;
  }
  const ttMv = tt ? tt.bestMove : null;

  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return 0; }
  if (!moves.length) return 0;

  // Selective filtering of OUR moves beyond root distance 2.
  const plyFromRoot = state.plyRoot - depth;
  if (sideUs && plyFromRoot >= 2) {
    // Stand-pat guard: if we are already up material, we can take the
    // material rather than force continuations that may hang a piece.
    const stand = materialDelta(ch, usColor);
    if (state.mat0 !== undefined && stand - state.mat0 >= SWING_CP) {
      return qsearch(ch, alpha, beta, usColor, 0);
    }
    const rawEnemyQ = enemyQ || queenSq(ch, enemyColor);
    const filt = [];
    for (const m of moves) {
      if (m.captured || m.promotion) { filt.push(m); continue; }
      const u = safeMove(ch, m);
      if (!u) continue;
      const isChk = ch.isCheck();
      let qAtk = false;
      if (rawEnemyQ) {
        const eq = queenSq(ch, enemyColor);
        if (eq) qAtk = safeIsAttacked(ch, eq, usColor);
      }
      ch.undo();
      if (isChk) { filt.push(m); continue; }
      if (qAtk) {
        // TIER1 only keeps quiet queen attacks within first 4 plies, TIER2 keeps them deeper.
        if (tier === 'TIER2' || plyFromRoot < 4) filt.push(m);
      }
    }
    if (filt.length) moves = filt;
    else return qsearch(ch, alpha, beta, usColor, 0);
  }

  const killers = state.killers[plyFromRoot] || (state.killers[plyFromRoot] = [null, null]);
  moves = orderMoves(ch, moves, enemyQ, ttMv, killers, state.history, usColor);

  let bestVal = sideUs ? -INF : INF;
  let bestMv = null;
  let flag = sideUs ? FLAG_UPPER : FLAG_LOWER;
  const pvLine = [];

  for (const mv of moves) {
    const did = safeMove(ch, mv);
    if (!did) continue;
    const childPv = [];
    const v = search(state, depth - 1, alpha, beta, childPv);
    ch.undo();
    if (state.aborted) return 0;

    if (sideUs) {
      if (v > bestVal) { bestVal = v; bestMv = mv; pvLine.length = 0; pvLine.push(mv, ...childPv); }
      if (v > alpha) { alpha = v; flag = FLAG_EXACT; }
      if (alpha >= beta) {
        if (!mv.captured && !mv.promotion) {
          if (!killers[0] || killers[0].from !== mv.from || killers[0].to !== mv.to) {
            killers[1] = killers[0]; killers[0] = mv;
          }
          const k = mv.from + mv.to;
          state.history[k] = (state.history[k] | 0) + depth * depth;
        }
        flag = FLAG_LOWER;
        break;
      }
    } else {
      if (v < bestVal) { bestVal = v; bestMv = mv; pvLine.length = 0; pvLine.push(mv, ...childPv); }
      if (v < beta) { beta = v; flag = FLAG_EXACT; }
      if (alpha >= beta) {
        if (!mv.captured && !mv.promotion) {
          if (!killers[0] || killers[0].from !== mv.from || killers[0].to !== mv.to) {
            killers[1] = killers[0]; killers[0] = mv;
          }
          const k = mv.from + mv.to;
          state.history[k] = (state.history[k] | 0) + depth * depth;
        }
        flag = FLAG_UPPER;
        break;
      }
    }
  }

  if (!state.aborted) {
    ttStore(key, depth, bestVal, flag, bestMv);
    if (pvOut) { pvOut.length = 0; for (const m of pvLine) pvOut.push(m); }
  }
  return bestVal;
}

// Drive a single candidate: play it, search deeper by iterative deepening.
function huntCandidate(fen, firstUci, usColor, ChessCtor, budgetMs, tier) {
  const ch0 = new ChessCtor(fen);
  const mat0 = materialDelta(ch0, usColor);
  const ch = new ChessCtor(fen);
  const first = uciToMove(firstUci);
  if (!first) return null;
  const did = safeMove(ch, first);
  if (!did) return null;
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  const state = {
    ch, usColor, deadline: Date.now() + budgetMs,
    killers: [], history: {}, aborted: false, plyRoot: 0, tier, ChessCtor, mat0,
  };
  let bestPv = null;
  let bestVal = -INF;
  // iterative deepening: 2, 4, 6, 8, ..., up to 16 plies (excluding the first move already played)
  for (let d = 2; d <= HORIZON_PLIES - 1; d += 2) {
    state.plyRoot = d;
    state.aborted = false;
    const pv = [];
    const v = search(state, d, -INF, INF, pv);
    if (state.aborted) break;
    bestVal = v; bestPv = pv;
    if (v > SWING_CP + Q_VAL / 2) break;  // comfortable win found, no need deeper
  }
  ch.undo();
  if (!bestPv || bestVal < SWING_CP) return null;
  // check there is a real queen capture by us somewhere in [first, ...pv]
  const line = [first, ...bestPv];
  if (!lineCapturesQueen(fen, line, usColor, ChessCtor)) return null;
  return {
    firstUci,
    lineUci: line.map(moveToUci),
    score: bestVal,
    tier,
  };
}

function lineCapturesQueen(fen, line, usColor, ChessCtor) {
  const ch = new ChessCtor(fen);
  let i = 0;
  for (const mv of line) {
    const us = ch.turn() === usColor;
    const did = safeMove(ch, mv);
    if (!did) return false;
    if (us && did.captured === 'q') return true;
    i++;
  }
  return false;
}

// ---------- validator (soundness gate) ----------
// Replay line, require real queen capture by us within 8 of our moves,
// ≥SWING_CP swing at that point, AND exchange settles in our favor.
function validate(fen, lineUci, usColor, ChessCtor) {
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  const ch = new ChessCtor(fen);
  const mat0 = materialDelta(ch, usColor);
  let ourMoves = 0;
  let captureAt = -1;
  let captureTo = null;
  for (let i = 0; i < lineUci.length; i++) {
    const us = ch.turn() === usColor;
    const mv = uciToMove(lineUci[i]);
    const did = safeMove(ch, mv);
    if (!did) return { ok: false, why: 'illegal' };
    if (us) {
      ourMoves++;
      if (did.captured === 'q') { captureAt = i; captureTo = did.to; break; }
      if (ourMoves > 8) return { ok: false, why: 'too-long' };
    }
  }
  if (captureAt < 0) return { ok: false, why: 'no-queen-capture' };
  // Measure swing right after capture.
  const matAfter = materialDelta(ch, usColor);
  if (matAfter - mat0 < SWING_CP) return { ok: false, why: 'insufficient-swing' };
  // Settle exchange: let opponent recapture if profitable, then score again.
  let settled = matAfter;
  // Simple SEE-style loop on captureTo until no more profitable captures.
  while (true) {
    const stm = ch.turn();
    let caps;
    try { caps = ch.moves({ verbose: true }).filter(m => m.to === captureTo); }
    catch (_) { break; }
    if (!caps.length) break;
    caps.sort((a, b) => pval(a.piece) - pval(b.piece));
    const before = materialDelta(ch, usColor);
    const did = safeMove(ch, caps[0]);
    if (!did) break;
    const after = materialDelta(ch, usColor);
    // Only recapture if it helps the side-to-move
    const sideWas = (stm === usColor);
    if (sideWas && after <= before) { ch.undo(); break; }
    if (!sideWas && after >= before) { ch.undo(); break; }
    settled = after;
  }
  if (settled - mat0 < SWING_CP) return { ok: false, why: 'exchange-refutes' };
  return { ok: true, swing: settled - mat0, captureAt };
}

// ---------- short-line full-width confirmation ----------
// Re-run a conservative full-width search from the root with iterative
// deepening, bailing on time. Keep the last completed depth's score.
function shortLineConfirm(fen, lineUci, usColor, ChessCtor, budgetMs = 400) {
  const ch = new ChessCtor(fen);
  const firstUci = lineUci[0];
  const first = uciToMove(firstUci);
  if (!safeMove(ch, first)) return { ok: false };
  const state = {
    ch, usColor, deadline: Date.now() + budgetMs,
    killers: [], history: {}, aborted: false, plyRoot: 0, tier: 'FULL', ChessCtor,
  };
  let best = -INF;
  let completed = 0;
  for (let d = 2; d <= 6; d += 2) {
    state.aborted = false;
    const v = fullWidthSearch(state, d, -INF, INF);
    if (state.aborted) break;
    best = v;
    completed = d;
  }
  ch.undo();
  if (completed < 2) return { ok: false };
  return { ok: best >= SWING_CP, depth: completed, score: best };
}

function fullWidthSearch(state, depth, alpha, beta) {
  const { ch, usColor } = state;
  if (Date.now() > state.deadline) { state.aborted = true; return 0; }
  if (ch.isCheckmate()) {
    const us = ch.turn() === usColor;
    return us ? -INF : INF;
  }
  if (ch.isStalemate()) return 0;
  if (ch.isDraw && ch.isDraw()) return materialDelta(ch, usColor);
  if (depth <= 0) return qsearch(ch, alpha, beta, usColor, 0);
  const sideUs = ch.turn() === usColor;
  let moves;
  try { moves = ch.moves({ verbose: true }); } catch (_) { return 0; }
  if (!moves.length) return 0;
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  const enemyQ = sideUs ? queenSq(ch, enemyColor) : null;
  moves = orderMoves(ch, moves, enemyQ, null, null, state.history || {}, usColor);
  let best = sideUs ? -INF : INF;
  for (const mv of moves) {
    const did = safeMove(ch, mv);
    if (!did) continue;
    const v = fullWidthSearch(state, depth - 1, alpha, beta);
    ch.undo();
    if (state.aborted) return 0;
    if (sideUs) { if (v > best) best = v; if (v > alpha) alpha = v; if (alpha >= beta) break; }
    else        { if (v < best) best = v; if (v < beta) beta = v;  if (alpha >= beta) break; }
  }
  return best;
}

// ---------- tension classifier ----------
function classifyTension(ch, moves, enemyQSq, usColor, ChessCtor) {
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  if (!enemyQSq) return 'NONE';
  const alreadyHit = safeIsAttacked(ch, enemyQSq, usColor);
  if (alreadyHit) return 'HIGH';
  for (let i = 0; i < Math.min(moves.length, 4); i++) {
    const mv = uciToMove(moves[i]);
    if (!mv) continue;
    if (!safeMove(ch, mv)) continue;
    const def = ch.turn();
    const us = def === 'w' ? 'b' : 'w';
    const eq = queenSq(ch, def);
    let hits = false;
    if (eq) hits = safeIsAttacked(ch, eq, us);
    const isCheck = ch.isCheck();
    const captured = eq === null; // queen gone => captured
    ch.undo();
    if (hits || isCheck || captured) return 'HIGH';
  }
  return 'MED';
}

// ---------- main entry ----------
function ashinaQueenHunt(moves, fen, ChessCtor, opts = {}) {
  if (!moves || !moves.length) return { moves: moves || [] };
  if (!fen || !ChessCtor) return { moves };
  let ch;
  try { ch = new ChessCtor(fen); } catch (_) { return { moves }; }
  const usColor = ch.turn();
  const enemyColor = usColor === 'w' ? 'b' : 'w';
  const enemyQSq = queenSq(ch, enemyColor);
  if (!enemyQSq) return { moves, meta: { skip: 'no-queen' } };

  // Preserve mate: if moves[0] delivers mate, never override.
  const first = uciToMove(moves[0]);
  if (first && safeMove(ch, first)) {
    if (ch.isCheckmate()) { ch.undo(); return { moves, meta: { skip: 'mate0' } }; }
    ch.undo();
  }

  const tension = classifyTension(ch, moves, enemyQSq, usColor, ChessCtor);
  const budgetMs = opts.budgetMs ?? (tension === 'HIGH' ? 700 : tension === 'MED' ? 300 : 140);
  const maxCand = opts.maxCandidates ?? 6;

  const start = Date.now();
  const candidates = moves.slice(0, Math.min(moves.length, maxCand));
  let best = null;
  for (let i = 0; i < candidates.length; i++) {
    const uci = candidates[i];
    if (Date.now() - start > budgetMs) break;
    const remain = budgetMs - (Date.now() - start);
    // Share the remaining budget among the candidates still to try.
    const left = candidates.length - i;
    const slice = Math.max(40, Math.floor(remain / left));
    const r = huntCandidate(fen, uci, usColor, ChessCtor, slice, 'TIER1');
    if (r && (!best || r.score > best.score)) best = r;
  }

  // Tier 2: gated quiet-trap scanner.
  let usedTier2 = false;
  if (!best) {
    const escapes = queenSafeEscapes(ch, enemyQSq, ChessCtor);
    const hit = safeIsAttacked(ch, enemyQSq, usColor);
    if (hit && escapes <= 2 && tension !== 'NONE') {
      usedTier2 = true;
      const tier2Budget = budgetMs * 2;
      const start2 = Date.now();
      for (let i = 0; i < candidates.length; i++) {
        const uci = candidates[i];
        if (Date.now() - start2 > tier2Budget) break;
        const remain = tier2Budget - (Date.now() - start2);
        const left = candidates.length - i;
        const slice = Math.max(60, Math.floor(remain / left));
        const r = huntCandidate(fen, uci, usColor, ChessCtor, slice, 'TIER2');
        if (r && (!best || r.score > best.score)) best = r;
      }
    }
  }

  if (!best) return { moves, meta: { tension, tier2: usedTier2, skip: 'no-win' } };

  const v = validate(fen, best.lineUci, usColor, ChessCtor);
  if (!v.ok) return { moves, meta: { tension, skip: 'validate:' + v.why } };

  if (best.lineUci.length < 6) {
    const confirmBudget = Math.min(200, Math.max(60, Math.floor(budgetMs / 3)));
    const conf = shortLineConfirm(fen, best.lineUci, usColor, ChessCtor, confirmBudget);
    if (!conf.ok) return { moves, meta: { tension, skip: 'short-confirm' } };
  }

  const out = [best.firstUci, ...moves.filter(m => m !== best.firstUci)];
  return {
    moves: out,
    chosen: best.firstUci,
    lineUci: best.lineUci,
    score: best.score,
    meta: { tension, tier: best.tier, swing: v.swing, captureAt: v.captureAt, tier2: usedTier2 },
  };
}

module.exports = { ashinaQueenHunt, ttClear, _TT, PV };
