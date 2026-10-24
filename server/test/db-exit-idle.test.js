'use strict';
/**
 * استخرِ PostgreSQL فرآیند را زنده نگه نمی‌دارد.
 *
 * سنجشِ پس‌زمینهٔ `app.js` هر ۵ ثانیه `healthy()` را می‌زند. اگر پس از `closeDb` برسد،
 * `getPool()` استخرِ تازه‌ای می‌سازد؛ تیکِ بعدی پیش از ۳۰ ثانیه بیکاری می‌رسد، پس اتصال هرگز
 * بسته نمی‌شد و پروندهٔ آزمون پس از آخرین آزمونش تا ۴۵ دقیقهٔ CI باز می‌ماند (لرزشی، فقط روی
 * PostgreSQL ِ واقعی). این آزمون همان را بی‌درنگ می‌سازد: بستن، دوباره پرسیدن، و فرآیند باید
 * خودش بیرون برود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { spawn } = require('node:child_process');
const path = require('node:path');

const url = process.env.TEST_DATABASE_URL || '';
const onPg = /^postgres(ql)?:\/\//.test(url);

test('استخرِ ساخته‌شده پس از closeDb فرآیند را باز نگه نمی‌دارد', { skip: !onPg && 'فقط روی PostgreSQL' }, async () => {
  const db = path.join(__dirname, '..', 'src', 'db.js');
  const code = `
    const { query, closeDb, healthy } = require(${JSON.stringify(db)});
    (async () => {
      await query('SELECT 1');
      await closeDb();
      //  همان تیکِ دیرآمدهٔ app.js
      if (!(await healthy())) process.exit(2);
    })().catch(() => process.exit(3));
  `;
  const child = spawn(process.execPath, ['-e', code], {
    env: { ...process.env, NODE_ENV: 'test', DATABASE_URL: url },
    stdio: 'ignore',
  });
  const t0 = Date.now();
  const out = await Promise.race([
    new Promise((resolve) => child.on('exit', (c) => resolve({ code: c }))),
    new Promise((resolve) => setTimeout(() => resolve(null), 10_000)),
  ]);
  if (!out) child.kill('SIGKILL');
  assert.ok(out, 'فرآیند پس از ۱۰ ثانیه هنوز باز است (اتصالِ بیکار آن را نگه داشته)');
  assert.equal(out.code, 0);
  assert.ok(Date.now() - t0 < 10_000);
});
