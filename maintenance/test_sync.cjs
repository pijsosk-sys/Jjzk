// Enforce that the core text is byte-identical between
// maintenance/queen-hunt-core.cjs (minus module.exports) and the two
// integration files (between their BEGIN/END markers).
'use strict';
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs');
const path = require('path');
const { coreBody, BEGIN, END } = require('./sync_core.cjs');

const TARGETS = [
  path.resolve(__dirname, '..', 'js', 'Ashina.js'),
  path.resolve(__dirname, '..', 'js', 'lichess.js'),
];

function extract(src) {
  const bIdx = src.indexOf(BEGIN);
  const eIdx = src.indexOf(END);
  if (bIdx < 0 || eIdx < 0) return null;
  // The synced body begins on the line after BEGIN and ends on the line
  // before END. Trim both ends to compare content, not surrounding whitespace.
  const inner = src.slice(bIdx + BEGIN.length, eIdx);
  return inner.replace(/^\n/, '').replace(/\s+$/, '') + '\n';
}

test('sync: core body matches js/Ashina.js', () => {
  const body = coreBody();
  const src = fs.readFileSync(TARGETS[0], 'utf8');
  const inner = extract(src);
  assert.ok(inner, 'markers missing in Ashina.js');
  assert.strictEqual(inner, body, 'drift between core and Ashina.js — run sync_core.cjs');
});

test('sync: core body matches js/lichess.js', () => {
  const body = coreBody();
  const src = fs.readFileSync(TARGETS[1], 'utf8');
  const inner = extract(src);
  assert.ok(inner, 'markers missing in lichess.js');
  assert.strictEqual(inner, body, 'drift between core and lichess.js — run sync_core.cjs');
});

test('sync: exactly one BEGIN and one END marker per integration file', () => {
  for (const t of TARGETS) {
    const src = fs.readFileSync(t, 'utf8');
    const b = (src.match(new RegExp(BEGIN.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
    const e = (src.match(new RegExp(END.replace(/[.*+?^${}()|[\]\\]/g, '\\$&'), 'g')) || []).length;
    assert.strictEqual(b, 1, `BEGIN count in ${t}: ${b}`);
    assert.strictEqual(e, 1, `END count in ${t}: ${e}`);
  }
});
