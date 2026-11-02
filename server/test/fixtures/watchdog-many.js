'use strict';
/**
 * نمونه برای `test/test-watchdog.test.js` — `.test.js` نیست تا در `npm test` اجرا نشود. ۳۰۰ آزمونِ تهی:
 * با `--test-force-exit` و stdout ِ ناهم‌زمان، گزارشِ آزمون‌های پایانی با process.exit گم می‌شد.
 */
const test = require('node:test');

for (let i = 1; i <= 300; i++) test(`آزمونِ تهیِ ${i}`, () => {});
