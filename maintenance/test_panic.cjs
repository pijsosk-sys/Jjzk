// Ensure the hunt never throws for any pathological input — the content
// script wraps the call in try/catch but defense-in-depth is cheap.
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const { Chess } = require('chess.js');
const { ashinaQueenHunt } = require('./queen-hunt-core.cjs');

function tryCall(moves, fen, Ctor, opts) {
  let err = null;
  let result = null;
  try { result = ashinaQueenHunt(moves, fen, Ctor, opts); }
  catch (e) { err = e; }
  return { err, result };
}

test('panic: undefined moves input', () => {
  const r = tryCall(undefined, 'startpos', Chess);
  assert.strictEqual(r.err, null);
  assert.ok(r.result);
});

test('panic: ChessCtor that throws on construction', () => {
  class Bad { constructor() { throw new Error('bad'); } }
  const r = tryCall(['e2e4'], 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1', Bad);
  assert.strictEqual(r.err, null);
});

test('panic: moves with only illegal-in-position UCI', () => {
  const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
  const r = tryCall(['z9z9', 'q1q1'], fen, Chess);
  assert.strictEqual(r.err, null);
  assert.ok(r.result);
});

test('panic: very weird FEN variants', () => {
  for (const fen of [
    '',
    'not a fen at all',
    '8/8/8/8/8/8/8/8 w - - 0 1', // no kings
    'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR x KQkq - 0 1', // bad side
  ]) {
    const r = tryCall(['e2e4'], fen, Chess);
    assert.strictEqual(r.err, null, `fen "${fen}" threw`);
  }
});

test('panic: opts with impossible values', () => {
  const fen = '4k2r/1pp2ppp/8/4q3/8/2Q5/8/4K2R w Kk - 0 1';
  for (const opts of [
    { budgetMs: -100 },
    { budgetMs: Infinity },
    { maxCandidates: 0 },
    { maxCandidates: -5 },
    { budgetMs: 'xxx' },
  ]) {
    const r = tryCall(['c3e5', 'e1e2'], fen, Chess, opts);
    assert.strictEqual(r.err, null, `opts ${JSON.stringify(opts)} threw`);
  }
});

test('panic: huge moves list', () => {
  const fen = 'rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1';
  const legal = new Chess(fen).moves({ verbose: true }).map(m => m.from + m.to + (m.promotion || ''));
  const huge = [];
  for (let i = 0; i < 500; i++) huge.push(legal[i % legal.length]);
  const r = tryCall(huge, fen, Chess, { budgetMs: 100 });
  assert.strictEqual(r.err, null);
});
