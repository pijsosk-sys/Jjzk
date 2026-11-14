// Placeholder shim that re-exposes chess.js as a global `Chess` constructor.
// Real distributions should replace this file with a bundled chess.js build.
// In content-script context, the surrounding chess.com / lichess.org pages
// already ship a chess.js-like constructor; this file is a fallback.
(function () {
  'use strict';
  if (typeof window === 'undefined') return;
  if (typeof window.Chess === 'function') return; // host-provided
  // If running inside a chess.com-style page that exposes a different
  // name, map it. Otherwise warn and leave Chess undefined — the
  // adapter functions in Ashina.js / lichess.js guard this.
  try {
    if (window.ChessJS && typeof window.ChessJS.Chess === 'function') {
      window.Chess = window.ChessJS.Chess;
    }
  } catch (_) {}
})();
