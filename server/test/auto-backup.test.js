'use strict';
/**
 * پشتیبانِ ابریِ خودکارِ نسخهٔ وب — بندِ «هر ۱۲ ساعت، بی تایید».
 *
 * ── چرا این پرونده هست ──────────────────────────────────────────────
 * خواستهٔ صریحِ صاحب سامانه: «هر دو برنامه بک‌اپ خودکار داشته باشن بدون
 * نیاز به تایید… هر ۱۲ ساعت… از هر حساب کاربری جداگانه و در هم بر هم
 * نشه.»
 *
 * برنامهٔ اندروید از قبل یکی می‌فرستاد (شبانه؛ حالا هر دوازده ساعت).
 * **نسخهٔ وب هیچ‌وقت هیچ پشتیبانِ خودکاری نمی‌فرستاد** — تنها راهش دکمهٔ
 * دستی بود. یعنی کاربرِ آیفون، که فقط همین را دارد، اگر یادش می‌رفت هیچ
 * نسخه‌ای روی سرور نداشت و هیچ آزمونی هم این را نمی‌گفت.
 *
 * ⚠️ کدِ زیرِ آزمون **همان فایلی است که در مرورگر می‌دود** —
 * `license/auto-backup.js`، بی هیچ نسخهٔ دوم. برای همین با `vm` و یک
 * `window`ِ کوچک اجرا می‌شود، نه با یک بازنویسیِ ساختگی.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const vm = require('node:vm');

const FILE = path.join(__dirname, '..', '..', 'license', 'auto-backup.js');
const SOURCE = fs.readFileSync(FILE, 'utf8');

/** یک مرورگرِ کوچک — فقط آن‌چه این فایل واقعاً لمس می‌کند */
function browser({ loggedIn = true, label = 'ali@example.com', online = true } = {}) {
  const store = new Map();
  const uploads = [];
  let failNext = 0;

  const win = {
    navigator: { onLine: online },
    localStorage: {
      getItem: (k) => (store.has(k) ? store.get(k) : null),
      setItem: (k, v) => store.set(k, String(v)),
      removeItem: (k) => store.delete(k),
    },
    addEventListener: () => {},
    setTimeout: () => 0,
    clearTimeout: () => {},
    setInterval: () => 0,
    clearInterval: () => {},
    TohidLicense: {
      isLoggedIn: () => loggedIn,
      userLabel: () => label,
      backups: {
        upload: async (text, opts) => {
          if (failNext > 0) { failNext--; throw new Error('نت نیست'); }
          uploads.push({ text, opts });
          return { ok: true };
        },
      },
    },
  };
  win.window = win;

  const ctx = vm.createContext(win);
  vm.runInContext(SOURCE, ctx, { filename: FILE });

  return {
    win,
    api: win.TohidAutoBackup,
    uploads,
    store,
    failOnce: () => { failNext = 1; },
    setLoggedIn: (v) => { loggedIn = v; },
    setLabel: (v) => { label = v; },
    setOnline: (v) => { win.navigator.onLine = v; },
  };
}

/* ------------------------------------------------------------------ ۱ */

test('۱) فایل خودش را روی window می‌نشاند و دوازده ساعت است', () => {
  const b = browser();
  assert.ok(b.api, 'TohidAutoBackup باید ساخته شود');
  assert.equal(b.api.EVERY_MS, 12 * 60 * 60 * 1000, 'خواستهٔ صریح: هر دوازده ساعت');
});

/* ------------------------------------------------------------------ ۲ */

test('۲) تا دوازده ساعت نگذشته، چیزی نمی‌رود', async () => {
  const b = browser();
  b.api.start(() => JSON.stringify({ a: 1 }));

  //  بارِ اول مهری نیست ⇒ همان اول باید برود
  assert.equal(await b.api.runOnce(false), true, 'بارِ اول باید برود');
  assert.equal(b.uploads.length, 1);

  //  بلافاصله دوباره ⇒ نه
  assert.equal(await b.api.runOnce(false), false, 'هنوز نوبتش نیست');
  assert.equal(b.uploads.length, 1, 'و هیچ چیزِ تازه‌ای نرفته');
});

/* ------------------------------------------------------------------ ۳ */

test('۳) بعد از دوازده ساعت، خودش می‌رود — و «خودکار» ثبت می‌شود', async () => {
  const b = browser();
  b.api.start(() => JSON.stringify({ a: 1 }));
  await b.api.runOnce(false);

  //  ساعت را دوازده ساعت و یک دقیقه عقب می‌بریم
  const key = [...b.store.keys()].find((k) => k.startsWith('tohid.autobackup.at'));
  assert.ok(key, 'مهر باید کلیدِ خودش را داشته باشد');
  b.store.set(key, String(Date.now() - (12 * 60 * 60 * 1000 + 60_000)));

  assert.equal(await b.api.runOnce(false), true, 'حالا نوبتش است');
  assert.equal(b.uploads.length, 2);

  /*
   *  ⛔ `manual: false` — وگرنه در فهرستِ پشتیبان‌ها کنارِ نسخه‌های دستی
   *  می‌نشیند و سهمِ نگهداریِ همان‌ها را می‌خورد.
   */
  assert.equal(b.uploads[1].opts.manual, false);
  assert.equal(b.uploads[1].opts.ext, 'json');
});

/* ------------------------------------------------------------------ ۴ */

test('۴) ⛔ مهرِ هر حساب جداست — دو دکان‌دار روی یک مرورگر', async () => {
  /*
   *  بی این، دومی «تازه گرفته شده» می‌بیند و دکانش دوازده ساعت بی
   *  پشتیبان می‌ماند. همان قاعدهٔ «حالِ Sync هر حساب جداست».
   */
  const b = browser({ label: 'yeki@example.com' });
  b.api.start(() => JSON.stringify({ a: 1 }));
  await b.api.runOnce(false);
  assert.equal(b.uploads.length, 1);

  //  حسابِ دوم روی همان مرورگر
  b.setLabel('digari@example.com');
  assert.equal(await b.api.runOnce(false), true, 'حسابِ دوم مهرِ خودش را ندارد، پس باید برود');
  assert.equal(b.uploads.length, 2);

  const keys = [...b.store.keys()].filter((k) => k.startsWith('tohid.autobackup.at'));
  assert.equal(keys.length, 2, 'دو مهرِ جدا، نه یکی');
});

/* ------------------------------------------------------------------ ۵ */

test('۵) ⛔ نرفت ⇒ مهر زده نمی‌شود، پس دوباره تلاش می‌کند', async () => {
  /*
   *  وگرنه یک خطای گذرا یعنی دوازده ساعتِ دیگر بی پشتیبان — و کاربر
   *  هیچ‌وقت نمی‌فهمد.
   */
  const b = browser();
  b.api.start(() => JSON.stringify({ a: 1 }));
  b.failOnce();

  assert.equal(await b.api.runOnce(false), false, 'نرفت');
  assert.equal(b.uploads.length, 0);
  const keys = [...b.store.keys()].filter((k) => k.startsWith('tohid.autobackup.at'));
  assert.equal(keys.length, 0, 'و مهری هم نخورده');

  assert.equal(await b.api.runOnce(false), true, 'تلاشِ بعدی باید برود');
  assert.equal(b.uploads.length, 1);
});

/* ------------------------------------------------------------------ ۶ */

test('۶) وارد نشده یا آفلاین ⇒ هیچ‌کاری، و هیچ استثنایی', async () => {
  const out = browser({ loggedIn: false });
  out.api.start(() => JSON.stringify({ a: 1 }));
  assert.equal(await out.api.runOnce(false), false, 'بی حساب هیچ');
  assert.equal(out.uploads.length, 0);

  const off = browser({ online: false });
  off.api.start(() => JSON.stringify({ a: 1 }));
  assert.equal(await off.api.runOnce(false), false, 'آفلاین هیچ');
  assert.equal(off.uploads.length, 0);
});

/* ------------------------------------------------------------------ ۷ */

test('۷) بی دفتر یا بی تامین‌کننده هیچ‌چیزِ خالی نمی‌فرستد', async () => {
  const b = browser();
  //  هنوز `start` صدا زده نشده
  assert.equal(await b.api.runOnce(true), false, 'بی تامین‌کننده هیچ');

  b.api.start(() => '');
  assert.equal(await b.api.runOnce(true), false, 'دفترِ خالی نمی‌رود');
  assert.equal(b.uploads.length, 0);
});

/* ------------------------------------------------------------------ ۸ */

test('۸) حال، خوانا و درست است', async () => {
  const b = browser();
  b.api.start(() => JSON.stringify({ a: 1 }));
  const before = b.api.status();
  assert.equal(before.ready, true);
  assert.equal(before.last, 0);
  assert.equal(before.due, true, 'بی مهر، همان اول نوبتش است');

  await b.api.runOnce(false);
  const after = b.api.status();
  assert.ok(after.last > 0, 'مهر خورد');
  assert.equal(after.due, false, 'و تا دوازده ساعتِ دیگر نوبتی نیست');
});

/* ------------------------------------------------------------------ ۹ */

test('۹) ⛔ هر فایلِ license که index.html بار می‌کند، در سرویس‌ورکر هم هست', () => {
  /*
   *  ── چرا این بند لازم بود ──────────────────────────────────────────
   *  `sw.js` خودش بالای فهرست نوشته: «با هر فایلِ تازه‌ای که به
   *  index.html اضافه می‌شود، همین فهرست و شمارهٔ VERSION هم باید عوض
   *  شوند — وگرنه نصبِ روی گوشی فایلِ تازه را ندارد و آفلاین نصفه بالا
   *  می‌آید.»
   *
   *  ولی آن قاعده هیچ نگهبانی نداشت، و همان‌جا یکی از قلم افتاده بود:
   *  `thermal-print.js` در فهرست نبود، پس چاپِ مستقیمِ حرارتی روی نصبِ
   *  آفلاین اصلاً بار نمی‌شد و هیچ‌کس نفهمیده بود.
   *
   *  ⛔ این سنجه همان قاعده است، حالا با دندان.
   */
  const root = path.join(__dirname, '..', '..');
  const html = fs.readFileSync(path.join(root, 'index.html'), 'utf8');
  const sw = fs.readFileSync(path.join(root, 'sw.js'), 'utf8');

  const wanted = [...new Set(html.match(/license\/[a-z0-9-]+\.js/g) || [])];
  assert.ok(wanted.length > 5, 'باید چند فایل پیدا شود، وگرنه خودِ سنجه خراب است');

  const missing = wanted.filter((f) => !sw.includes(f));
  assert.deepEqual(missing, [], `این فایل‌ها در sw.js نیستند: ${missing.join(', ')}`);
});
