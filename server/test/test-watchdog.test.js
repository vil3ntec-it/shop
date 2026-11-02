'use strict';
/**
 * نگهبانِ پرونده‌ای که بی‌صدا ماند (`scripts/test-watchdog.js`) — برای گیرِ «آزمون‌های سرور زیرِ
 * Electron» که پس از آخرین آزمونِ یک پرونده بی هیچ نامی تا سقفِ ۴۵ دقیقه می‌ایستاد.
 *
 *   ۱) قلابِ after که هرگز تمام نمی‌شود ⇒ در چند ثانیه نامِ پرونده، مرحله، آخرین آزمون و دستگیرهٔ
 *      ref‌دار (گزارشِ Node) چاپ می‌شود؛ فرآیندش کشته می‌شود؛ پروندهٔ بعدی اجرا می‌شود؛ کدِ خروج ۱.
 *   ۲) خروجی که پس از test:summary در رشتهٔ اصلی گیر می‌کند ⇒ همان، با «گزارشِ Node نرسید».
 *   ۳) گیرِ دوم ⇒ کلِ اجرا بسته می‌شود و پرونده‌های بعدی اجرا نمی‌شوند.
 *   ۴) اجرای سبز ⇒ کدِ ۰ و بی پیامِ نگهبان؛ و هیچ فرآیندی از نمونه‌ها زنده نمی‌ماند.
 *
 * نمونه‌ها `test/fixtures/watchdog-*.js` اند (نه `.test.js`، پس خودشان در اجرای اصلی نیستند)؛
 * سقفِ سکوت این‌جا ۵ ثانیه است و چهار اجرا هم‌زمان‌اند.
 */
const { describe, it, test } = require('node:test');
const assert = require('node:assert/strict');
const path = require('node:path');
const { spawn } = require('node:child_process');
const { listProcesses } = require('../scripts/test-watchdog');

const SERVER = path.join(__dirname, '..');
const RUN = path.join(SERVER, 'scripts', 'test-run.js');
const SECONDS = 5;
const fx = (name) => `test/fixtures/watchdog-${name}.js`;
const esc = (s) => s.replace(/[.*+?^${}()|[\]\\/]/g, '\\$&');
const noProcList = process.platform === 'win32' && 'فهرستِ فرآیندها در ویندوز نیست (نگهبان آن‌جا کلِ اجرا را می‌بندد)';

function run(...files) {
  const env = { ...process.env, TEST_WATCHDOG_SECONDS: String(SECONDS) };
  //  ⚠️ بی پاک کردنِ NODE_TEST_CONTEXT، `node --test` ِ تودرتو خودش را «فرزند» می‌پندارد؛ و شواهدِ
  //  نمونه‌ها نباید به پروندهٔ شواهدِ CI برود
  delete env.NODE_TEST_CONTEXT;
  delete env.TEST_EVIDENCE_FILE;
  const t0 = Date.now();
  return new Promise((resolve) => {
    const c = spawn(process.execPath, [RUN, '--test-concurrency=1', '--test-force-exit', ...files], { cwd: SERVER, env });
    let out = '';
    c.stdout.on('data', (d) => { out += d; });
    c.stderr.on('data', (d) => { out += d; });
    c.on('close', (status) => resolve({ status, out, ms: Date.now() - t0 }));
  });
}

const header = (file) => new RegExp(`⏱ نگهبانِ آزمون: پروندهٔ ${esc(file)} ${SECONDS} ثانیه هیچ پیشرفتی نداشت — `);

describe('نگهبانِ آزمون', { concurrency: true }, () => {
  it('قلابِ after ِ بی‌پایان ⇒ نام، مرحله و دستگیره؛ کشته می‌شود و پروندهٔ بعد اجرا می‌شود', { skip: noProcList }, async () => {
    const r = await run(fx('hang-after'), fx('ok'));
    assert.equal(r.status, 1, r.out);
    assert.ok(r.ms < 40_000, `${r.ms}ms`);
    assert.match(r.out, new RegExp(header(fx('hang-after')).source + 'پس از پایانِ آزمونِ «آخرین آزمونِ پیش از گیر»: قلابِ after'));
    assert.match(r.out, /فرآیندِ پرونده: pid \d+/);
    //  گزارشِ Node: حلقهٔ رویداد بیکار است و تایمرِ ref‌دار فرآیند را نگه داشته
    assert.match(r.out, /گزارشِ Node \(حلقهٔ رویداد هنوز می‌چرخد\)/);
    assert.match(r.out, /دستگیره‌های فعال و ref‌دار \([1-9]\d*\): [^\n]*timer/);
    assert.match(r.out, new RegExp(`not ok \\d+ - ${esc(fx('hang-after'))}`));
    assert.match(r.out, /signal: 'SIGKILL'/);
    assert.match(r.out, /^ok \d+ - پروندهٔ پس از گیر/m, 'پروندهٔ بعدی اجرا نشد');
    assert.match(r.out, new RegExp(`── ⏱ نگهبانِ آزمون: 1 پرونده بی‌صدا ماند و کشته شد ──\\n✖ ${esc(fx('hang-after'))} — `));
  });

  it('خروجِ گیرکرده پس از test:summary ⇒ «گزارشِ Node نرسید»؛ کشته می‌شود و پروندهٔ بعد اجرا می‌شود', { skip: noProcList }, async () => {
    const r = await run(fx('hang-exit'), fx('ok'));
    assert.equal(r.status, 1, r.out);
    assert.ok(r.ms < 40_000, `${r.ms}ms`);
    assert.match(r.out, new RegExp(header(fx('hang-exit')).source + 'همهٔ آزمون‌ها و قلاب‌ها تمام شدند \\(test:summary رسید\\)'));
    assert.match(r.out, /آخرین آزمونِ تمام‌شده: «همه سبز، ولی خروج گیر می‌کند»/);
    assert.match(r.out, /گزارشِ Node نرسید/);
    if (process.platform === 'linux') assert.match(r.out, /فرآیندِ پرونده: pid \d+ \[حالت \S+، رشتهٔ اصلی futex/);
    assert.match(r.out, new RegExp(`not ok \\d+ - ${esc(fx('hang-exit'))}`));
    assert.match(r.out, /^ok \d+ - پروندهٔ پس از گیر/m, 'پروندهٔ بعدی اجرا نشد');
  });

  it('گیرِ دوم ⇒ کلِ اجرا بسته می‌شود', { skip: noProcList }, async () => {
    const r = await run(fx('hang-after'), fx('hang-load'), fx('ok'));
    assert.equal(r.status, 1, r.out);
    assert.ok(r.ms < 60_000, `${r.ms}ms`);
    assert.match(r.out, header(fx('hang-after')));
    assert.match(r.out, new RegExp(header(fx('hang-load')).source + 'بارگذاریِ پرونده'));
    assert.match(r.out, /⛔ کلِ اجرا بسته می‌شود/);
    assert.ok(!/پروندهٔ پس از گیر/.test(r.out), 'پس از گیرِ دوم پرونده‌ای اجرا شد');
    assert.match(r.out, /── ⏱ نگهبانِ آزمون: 2 پرونده بی‌صدا ماند و کشته شد ──/);
  });

  it('اجرای سبز ⇒ کدِ ۰، بی پیامِ نگهبان؛ فقط خطِ آمار', async () => {
    const r = await run(fx('ok'));
    assert.equal(r.status, 0, r.out);
    assert.ok(!r.out.includes('⏱'), r.out);
    assert.match(r.out, new RegExp(`^نگهبان: طولانی‌ترین سکوت [\\d.]+ ثانیه[^\\n]*؛ سقفِ سکوت ${SECONDS} ثانیه$`, 'm'));
  });
});

test('هیچ فرآیندی از نمونه‌های گیرکرده زنده نمی‌ماند', { skip: noProcList }, () => {
  const left = (listProcesses() || []).filter((p) => p.pid !== process.pid && p.args.includes('test/fixtures/watchdog-'));
  assert.deepEqual(left.map((p) => `${p.pid} ${p.args}`), []);
});
