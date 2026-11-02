'use strict';
/**
 * نمونهٔ عمداً گیرکرده برای `test/test-watchdog.test.js` — `.test.js` نیست تا در `npm test` اجرا نشود.
 * همان شکلِ گیرِ CI ِ Electron: آخرین آزمون سبز، سپس قلابِ after که هرگز تمام نمی‌شود و
 * تایمرِ ref‌داری که فرآیند را زنده نگه می‌دارد (`--test-force-exit` هرگز نمی‌رسد).
 */
const test = require('node:test');

test('آخرین آزمونِ پیش از گیر', () => {});

test.after(() => new Promise(() => { setInterval(() => {}, 1000); }));
