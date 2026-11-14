// Sync the pre-module.exports core text into js/Ashina.js and js/lichess.js.
// Each target has a BEGIN/END marker block; this script replaces its content
// byte-identical with the core's. Asserts exactly one occurrence per file.
'use strict';
const fs = require('fs');
const path = require('path');

const BEGIN = '// === ASHINA_QUEEN_HUNT_CORE_BEGIN ===';
const END = '// === ASHINA_QUEEN_HUNT_CORE_END ===';

const CORE = path.resolve(__dirname, 'queen-hunt-core.cjs');
const TARGETS = [
  path.resolve(__dirname, '..', 'js', 'Ashina.js'),
  path.resolve(__dirname, '..', 'js', 'lichess.js'),
];

function coreBody() {
  const text = fs.readFileSync(CORE, 'utf8');
  const i = text.indexOf('module.exports');
  if (i < 0) throw new Error('no module.exports in core');
  return text.slice(0, i).replace(/\s+$/, '') + '\n';
}

function syncFile(targetPath, body) {
  if (!fs.existsSync(targetPath)) {
    throw new Error(`target missing: ${targetPath}`);
  }
  const src = fs.readFileSync(targetPath, 'utf8');
  const bIdx = src.indexOf(BEGIN);
  const eIdx = src.indexOf(END);
  if (bIdx < 0 || eIdx < 0 || eIdx < bIdx) {
    throw new Error(`markers missing/inverted in ${targetPath}`);
  }
  // Ensure only one of each marker.
  if (src.indexOf(BEGIN, bIdx + 1) >= 0) throw new Error(`duplicate BEGIN in ${targetPath}`);
  if (src.indexOf(END, eIdx + 1) >= 0) throw new Error(`duplicate END in ${targetPath}`);

  const prefix = src.slice(0, bIdx + BEGIN.length);
  const suffix = src.slice(eIdx);
  const next = `${prefix}\n${body}${suffix}`;
  if (next !== src) {
    fs.writeFileSync(targetPath, next);
    console.log('synced', targetPath);
  } else {
    console.log('up to date', targetPath);
  }
}

function main() {
  const body = coreBody();
  for (const t of TARGETS) syncFile(t, body);
}

if (require.main === module) main();
module.exports = { coreBody, syncFile, BEGIN, END };
