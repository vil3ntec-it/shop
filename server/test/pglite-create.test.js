'use strict';
/**
 * ساختنِ PGlite با حلقهٔ رویدادِ زنده (`src/lib/pglite-create.js`) — گیرِ «آزمون‌های سرور زیرِ
 * Electron» که پس از آخرین آزمونِ یک پرونده تا ۴۵ دقیقه می‌ایستاد (پروندهٔ بعدی در ساختنِ PGlite
 * با رشتهٔ اصلیِ گیر در `NodePlatform::DrainTasks`).
 *
 *   ۱) در کدِ سرور و ابزارهایش (src/، scripts/) هیچ `PGlite.create` ِ خامی جز همان یاور نیست.
 *   ۲) تا ساختن تمام نشده یک تایمرِ ref‌دار هست (uv_run برنمی‌گردد)؛ پس از آن برداشته شده و
 *      فرآیند را نگه نمی‌دارد؛ نتیجه همان PGlite ِ کارا است.
 *
 * خودِ گیر در یک اجرا بازتولید نمی‌شود (حدودِ ۲٪ از فرآیندهای تازه زیرِ Electron 38)؛ این آزمون
 * شرطی را می‌سنجد که بی آن گیر ممکن است.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { createPglite } = require('../src/lib/pglite-create');

const SERVER = path.join(__dirname, '..');

function jsFiles(dir) {
  return fs.readdirSync(dir, { withFileTypes: true }).flatMap((e) => {
    const p = path.join(dir, e.name);
    if (e.isDirectory()) return e.name === 'node_modules' ? [] : jsFiles(p);
    return /\.(c|m)?js$/.test(e.name) ? [p] : [];
  });
}

test('src/ و scripts/: هیچ PGlite.create ِ خامی جز lib/pglite-create.js', () => {
  const helper = path.join(SERVER, 'src', 'lib', 'pglite-create.js');
  const raw = [...jsFiles(path.join(SERVER, 'src')), ...jsFiles(path.join(SERVER, 'scripts'))]
    .filter((f) => f !== helper && /await\s+PGlite\.create\(|new\s+PGlite\(/.test(fs.readFileSync(f, 'utf8')))
    .map((f) => path.relative(SERVER, f));
  assert.deepEqual(raw, []);
});

test('تا ساختن تمام نشده تایمرِ ref‌دار هست؛ پس از آن نیست؛ PGlite کار می‌کند', async () => {
  const timers = () => process.getActiveResourcesInfo().filter((r) => r === 'Timeout').length;
  const before = timers();
  const pending = createPglite({ dataDir: 'memory://' });
  assert.equal(timers(), before + 1, 'هنگامِ ساختن حلقه زنده نماند');
  const db = await pending;
  try {
    assert.equal(timers(), before, 'تایمر پس از ساختن ماند');
    assert.equal((await db.query('SELECT 1 AS ok')).rows[0].ok, 1);
  } finally { await db.close(); }
});

test('شکستِ ساختن هم تایمر را برمی‌دارد', async () => {
  const timers = () => process.getActiveResourcesInfo().filter((r) => r === 'Timeout').length;
  const before = timers();
  await assert.rejects(createPglite({ dataDir: 'memory://', loadDataDir: new Blob([Buffer.from('not a tarball')]) }));
  assert.equal(timers(), before);
});
