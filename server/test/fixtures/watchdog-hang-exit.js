'use strict';
/**
 * نمونهٔ عمداً گیرکرده برای `test/test-watchdog.test.js` — `.test.js` نیست تا در `npm test` اجرا نشود.
 * همهٔ آزمون‌ها و قلاب‌ها تمام می‌شوند (test:summary می‌رسد) ولی خروجِ فرآیند گیر می‌کند: رشتهٔ اصلی
 * در `Atomics.wait` می‌خوابد (بی CPU) — مثلِ گیرِ بومی در خروج؛ گزارشِ Node هم نمی‌رسد.
 */
const test = require('node:test');

test('همه سبز، ولی خروج گیر می‌کند', () => {});

process.on('exit', () => { Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0); });
