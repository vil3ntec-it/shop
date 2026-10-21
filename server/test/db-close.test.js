'use strict';
/**
 * بستنِ دیتابیس قطعی است: سنجشِ پس‌زمینهٔ `healthy()` (تیکِ ۵ثانیه‌ایِ
 * app.js) پس از `closeDb()` استخرِ تازه نمی‌سازد.
 *
 * ⛔ ریشهٔ «آزمون‌های سرور روی PostgreSQL» که گاهی ۴۵ دقیقه می‌ماند و لغو
 * می‌شد (#350): اگر پس از `h.stop()` چیزی حلقهٔ رویداد را بیش از ۵ ثانیه
 * زنده نگه می‌داشت، تیک استخرِ تازه‌ای باز می‌کرد و اتصالش (هر ۵ ثانیه یک
 * `SELECT 1`، بیکاریِ مجاز ۳۰ ثانیه) فرآیندِ فایلِ آزمون را تا ابد زنده
 * نگه می‌داشت. روی PGlite دیده نمی‌شد، چون آن‌جا سوکتی نیست.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const path = require('path');
const { spawn } = require('child_process');
const h = require('./helpers');
const db = require('../src/db');

test('پس از بستن، سنجشِ سلامت دیتابیس را دوباره باز نمی‌کند؛ کارِ واقعی باز می‌کند', async () => {
  await h.start();
  assert.equal(await db.healthy(), true);
  await h.stop();
  assert.equal(await db.healthy(), false, 'healthy() پس از closeDb() استخرِ تازه ساخت');
  await h.start();
  assert.equal(await db.healthy(), true);
  await h.stop();
});

test('فرآیند پس از stop() تمام می‌شود حتی وقتی چیزی حلقه را ۶ ثانیه زنده نگه دارد', async () => {
  //  مثلِ پایانِ یک فایلِ آزمون: برنامه کار کرده، کارهای سرِ بالا آمدن تمام
  //  شده، بعد stop() — و چیزی (مثلاً یک زمان‌سنج) حلقه را از یک تیک بیشتر نگه می‌دارد
  const script = `
    const h = require(${JSON.stringify(path.join(__dirname, 'helpers'))});
    (async () => {
      await h.start();
      await h.get('/api/health');
      await new Promise((r) => setTimeout(r, 1000));
      await h.stop();
      process.stdout.write('stopped\\n');
      setTimeout(() => {}, 6000);
    })().catch((e) => { console.error(e); process.exit(2); });`;
  const { code, afterStopMs } = await new Promise((resolve) => {
    const child = spawn(process.execPath, ['-e', script], { cwd: path.join(__dirname, '..'), stdio: ['ignore', 'pipe', 'ignore'] });
    let stoppedAt = 0;
    child.stdout.on('data', (d) => { if (!stoppedAt && String(d).includes('stopped')) stoppedAt = Date.now(); });
    const kill = setTimeout(() => { child.kill('SIGKILL'); resolve({ code: 'hang', afterStopMs: Date.now() - stoppedAt }); }, 180_000);
    child.on('exit', (c) => { clearTimeout(kill); resolve({ code: c, afterStopMs: Date.now() - stoppedAt }); });
  });
  assert.equal(code, 0, `فرآیند پس از بستنِ دیتابیس تمام نشد (${afterStopMs}ms پس از stop)`);
  assert.ok(afterStopMs < 20_000, `${afterStopMs}ms`);
});
