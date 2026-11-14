// Minimal options wiring: persist each checkbox to chrome.storage.sync.
// The content scripts read these on injection and toggle features.
(function () {
  'use strict';
  const KEYS = ['option-queen-hunt', 'option-voice-coach', 'option-board-arrows'];

  function load() {
    try {
      chrome.storage.sync.get(KEYS, (vals) => {
        for (const k of KEYS) {
          const el = document.getElementById(k);
          if (el) el.checked = !!vals[k];
        }
      });
    } catch (_) {}
  }
  function save(k, v) {
    try { chrome.storage.sync.set({ [k]: v }); } catch (_) {}
  }
  document.addEventListener('DOMContentLoaded', () => {
    load();
    for (const k of KEYS) {
      const el = document.getElementById(k);
      if (!el) continue;
      el.addEventListener('change', () => save(k, el.checked));
    }
  });
})();
