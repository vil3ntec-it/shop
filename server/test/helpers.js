'use strict';
/**
 * راه‌اندازی سرور آزمایشی روی یک دیتابیس جدا.
 *
 * تست‌ها روی PostgreSQL واقعی اجرا می‌شوند، نه روی یک ساختگی — چون
 * چیزی که باید ثابت شود «این سیستم واقعاً کار می‌کند» است.
 */
process.env.NODE_ENV = 'test';
process.env.DATABASE_URL = process.env.TEST_DATABASE_URL
  || 'postgres://shop:shoppass@127.0.0.1:5432/shop_test';
process.env.API_SECRET = 'test-secret-test-secret-test-sec';
process.env.OTP_SECRET = 'test-otp-secret-test-otp-secret1';
process.env.BACKUP_ENABLED = 'false';
/*
 *  پشتیبانِ هر حساب روی دیسک می‌نشیند، پس آزمون باید پوشه‌ی خودش را
 *  داشته باشد — وگرنه فایل‌های آزمون در پوشه‌ی واقعیِ سرور می‌مانند.
 */
process.env.BACKUP_PATH = require('path').join(
  require('os').tmpdir(), `shop-test-backups-${process.pid}`
);
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_AUTH_MAX = '10000';
process.env.RATE_OTP_MAX ||= '10000';
process.env.RATE_VERIFY_MAX ||= '10000';
//  سقفِ «هر IP» و «هر کد» ِ ورودِ شاگرد — خودشان در `staff-login-limits.test.js` با عددِ کوچک سنجیده می‌شوند
process.env.RATE_JOIN_MAX ||= '10000';
process.env.RATE_JOIN_CODE_MAX ||= '10000';
process.env.LOGIN_LOCKOUT_TRIES = '10000';
//  شمارنده‌ی دومِ قفل هم باید باز باشد، وگرنه سنجه‌هایی که عمداً رمزِ
//  غلط می‌زنند همدیگر را می‌بندند — همه از یک IP می‌آیند (127.0.0.1)
process.env.LOGIN_LOCKOUT_GLOBAL_TRIES = '10000';
process.env.OTP_RESEND_SECONDS = '0';
//  F0-16: کفِ تأخیرِ یکسان و سقفِ هر IP — خودشان در `account-enumeration.test.js` سنجیده می‌شوند
process.env.OTP_UNIFORM_MS ||= '0';
process.env.RATE_PROBE_IP_MAX ||= '10000';
//  پیامِ همگانی سقفِ ۵تایی دارد و چند آزمون پشتِ سرِ هم می‌زنندش؛
//  خودِ سقف در `pump-support.test.js` جدا و با عددِ کوچک سنجیده می‌شود.
process.env.RATE_BROADCAST_MAX = '10000';
//  SEC-AUTH-01: هزینهٔ scrypt در production `p=5` است (~۲۰۰ms)؛ صدها حسابِ آزمون با آن
//  دقیقه‌ها کند می‌شوند. خودِ پیش‌فرض و بازنویسیِ هشِ کهنه در `auth-hardening.test.js` با p=5 سنجیده می‌شوند.
process.env.PASSWORD_SCRYPT_P ||= '1';

const { createApp } = require('../src/app');
const { query, closeDb } = require('../src/db');
const migrate = require('../src/migrate');

let server = null;
let base = '';

async function resetDatabase() {
  //  پاک‌سازیِ آزمون زیرِ سقفِ پرس‌وجوی استخر (`PG_STATEMENT_TIMEOUT_MS` ِ `request-deadline.test.js`: ۴۰۰ms) نمی‌رود:
  //  DROP SCHEMA ِ پایگاهِ پر گاهی بیش از آن است ⇒ 57014 و آزمونِ سرخ (۱ از ۶ روی PostgreSQL). مهاجرت‌ها خودشان بی سقف‌اند.
  await require('../src/db').tx(async (c) => {
    await c.query('SET LOCAL statement_timeout = 0');
    await c.query('DROP SCHEMA public CASCADE');
    await c.query('CREATE SCHEMA public');
  });
  await migrate.run();
}

/**
 * `reset: false` همان دیتابیسِ موجود را برمی‌دارد (بی پاک کردن) — برای آزمونی که دیتابیس را
 * فرآیندِ دیگری ساخته است (`crash-kill.test.js`: سرورِ کشته‌شده، سپس بازگشتِ سرورِ تازه).
 */
async function start({ reset = true } = {}) {
  if (server) return base;
  if (reset) await resetDatabase();
  const app = await createApp({ runMigrations: false });
  await new Promise((resolve) => {
    server = app.listen(0, '127.0.0.1', resolve);
  });
  base = `http://127.0.0.1:${server.address().port}`;
  return base;
}

async function stop() {
  if (server) await new Promise(r => server.close(r));
  server = null;
  await closeDb();
}

/** درخواست ساده با پشتیبانی از توکن. */
async function api(method, path, { body = null, token = null, headers = {} } = {}) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body === null ? undefined : JSON.stringify(body),
  });
  let json = null;
  const text = await res.text();
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: res.status, body: json, headers: res.headers };
}

/**
 * درخواست با بدنه‌ی **خام** — برای مسیرهایی که JSON نمی‌گیرند.
 *
 * ⚠️ `Content-Type` عمداً `application/octet-stream` است: اگر
 * `application/json` بود، `express.json`ِ سراسری بدنه را می‌خورد و
 * مسیر یک بافرِ خالی می‌دید. همین یک سرآیند فرقِ «کار می‌کند» و
 * «بی‌صدا خالی می‌آید» است.
 */
async function raw(method, path, buffer, { token = null, headers = {} } = {}) {
  const res = await fetch(`${base}${path}`, {
    method,
    headers: {
      'Content-Type': 'application/octet-stream',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: buffer,
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text }; }
  return { status: res.status, body: json, headers: res.headers };
}

/** دانلودِ بدنه‌ی دودویی — همان بایت‌هایی که سرور داد، دست‌نخورده. */
async function download(path, { token = null } = {}) {
  const res = await fetch(`${base}${path}`, {
    headers: token ? { Authorization: `Bearer ${token}` } : {},
  });
  const buf = Buffer.from(await res.arrayBuffer());
  return { status: res.status, buffer: buf, headers: res.headers };
}

const get = (p, o) => api('GET', p, o);
const post = (p, body, o = {}) => api('POST', p, { ...o, body });
const put = (p, body, o = {}) => api('PUT', p, { ...o, body });
const patch = (p, body, o = {}) => api('PATCH', p, { ...o, body });
const del = (p, o) => api('DELETE', p, o);

/**
 * ساخت یک کاربر تازه با رمز و برگرداندن نشست او.
 *
 * `app` می‌گوید نشست مالِ کدام بخش است. توکنِ یک بخش روی مسیرهای بخشِ
 * دیگر اصلاً پیدا نمی‌شود، پس آزمونِ پمپ باید `'pump'` بدهد — همان
 * کاری که خودِ برنامهٔ پمپ می‌کند.
 */
let seq = 0;
async function newUser(name = 'کاربر', app = 'shop') {
  seq += 1;
  const email = `user${seq}@test.local`;
  const password = 'Passw0rd!test';
  const device = { deviceId: `dev-${seq}`, name: 'تست', platform: 'test' };

  /*
   *  همان سه پله‌ای که برنامه می‌رود — نه یک میان‌بر.
   *
   *  پیش‌تر این کمکی `/auth/register` را صدا می‌زد؛ همان مسیری که حساب
   *  را **بدونِ تأییدِ ایمیل** می‌ساخت و حالا بسته شده. اگر تست‌ها از
   *  میان‌بر بروند، هیچ‌وقت معلوم نمی‌شود راهِ واقعی سالم است یا نه.
   */
  const started = await post('/api/auth/register/start', { name, email, password });
  if (started.status >= 300) throw new Error(`پله‌ی یک نشد: ${JSON.stringify(started.body)}`);
  const code = started.body.devCode;
  if (!code) throw new Error('کدِ آزمایشی برنگشت — سرور در حالتِ log نیست؟');

  const verified = await post('/api/auth/register/verify', { email, code });
  if (verified.status >= 300) throw new Error(`پله‌ی دو نشد: ${JSON.stringify(verified.body)}`);

  const done = await post('/api/auth/register/complete', {
    ticket: verified.body.ticket,
    name, password, device, app,
    terms: { accepted: true },
  });
  if (done.status >= 300) throw new Error(`ثبت‌نام نشد: ${JSON.stringify(done.body)}`);
  return { ...done.body, email, phone: done.body.user?.phone ?? null, password, app };
}

/**
 * نشستِ دوم برای همان آدم، در بخشِ دیگر.
 *
 * از وقتی توکن به بخشش مهر می‌خورد، یک نشست هر دو بخش را باز نمی‌کند —
 * و این عمدی است. آدمی که هم دکان دارد هم پمپ، در هر برنامه جدا وارد
 * می‌شود، دقیقاً مثلِ دو برنامهٔ بی‌ربط.
 */
async function signIn(user, app = 'shop') {
  const r = await post('/api/auth/login', {
    identifier: user.email,
    password: user.password,
    device: { deviceId: `dev-${app}-${user.email.replace(/[^A-Za-z0-9]/g, '-')}`, name: 'تست', platform: 'test' },
    app,
  });
  if (r.status >= 300) throw new Error(`ورود نشد: ${JSON.stringify(r.body)}`);
  return { ...user, ...r.body, app };
}

/*
 *  متن برای جست‌وجوی **مختصات** در پاسخ: زمان‌های epoch (عددِ صحیحِ میلی‌ثانیه یا ثانیهٔ میانِ ۲۰۲۰ و ۲۰۳۹،
 *  مثلِ `lastConfirmedAt`) و نشانِ دکان‌ها (base36 ِ تصادفی) کنار می‌روند. بی این، خودِ `Date.now()` گاهی
 *  «…3453…» است (مثلاً ۳۱ اکتبر ۲۰۲۶، ۱۳:۲۳ تا ۱۳:۴۰ UTC) و سنجهٔ نشتِ مختصات بی هیچ نشتی سرخ می‌شد.
 *  هر شکلِ دیگرِ مختصات می‌ماند: `m_lat` ِ صدمِ درجه (3453)، 34.531، 34.5310000000001، E7 و E10 —
 *  رقمِ پس از ممیز و عددِ بیرون از بازهٔ زمان دست نمی‌خورد.
 *  ⛔ فقط برای مختصات؛ شناسه و شماره را روی متنِ خام بسنجید.
 */
function withoutEpochs(text, slugs = []) {
  const epoch = (n) => (n >= 1.6e12 && n < 2.2e12) || (n >= 1.6e9 && n < 2.2e9);
  let out = String(text).replace(/(?<![\d.])\d{10,13}(?![\d.])/g, (m) => (epoch(Number(m)) ? '#' : m));
  for (const s of slugs) if (s) out = out.split(String(s)).join('#');
  return out;
}

/*
 *  نسخهٔ دست‌خوردهٔ یک بلیت/توکن که **همیشه** با اصل فرق دارد: نویسه‌های آخر (به درازای `suffix`) هر کدام به
 *  `suffix[i]` می‌شوند، و اگر اصل همان نویسه را داشت به نویسهٔ دیگری. «دو نویسهٔ آخر ⇒ xx» وقتی HMAC ِ base64url
 *  خودش به xx ختم می‌شد (یک بار در ۶۴² = ۴۰۹۶) همان بلیتِ درست بود و سرور درست ۲۰۱ می‌داد: سرخیِ «زیرِ Electron»
 *  روی 83dcd9f2 (run 41585590213، بلیتِ صادرشده در 06:03:43.249Z با HMAC ِ «…WWQxx»). نویسهٔ یکی‌مانده به آخر در
 *  base64url همیشه شش بیتِ کامل است ⇒ بایت‌های رمزگشوده هم فرق می‌کنند.
 */
function tampered(token, suffix = 'xx') {
  const s = String(token);
  const tail = s.slice(-suffix.length);
  const alt = [...suffix].map((c, i) => (c !== tail[i] ? c : c === 'A' ? 'B' : 'A')).join('');
  return s.slice(0, -suffix.length) + alt;
}

/*
 *  ── کاشتن و خواندنِ دادهٔ دکان از راهِ زنده ─────────────────────────────
 *  ⛔ درِ مستقیمِ داده (`/api/products`، `/api/sales/full`، `/api/debtors/:id`… با PUT و DELETE) برداشته شد
 *  (۱۴۰۵/۰۸/۲۲، `dead-doors.test.js`). آزمون‌ها همان راهی را می‌روند که برنامهٔ کهنه می‌رود: `POST`/`GET /api/sync`
 *  — همان `sync.pushChanges` با همهٔ نگهبان‌ها (نقش، سقف‌ها، پوشاندنِ شماره، فاکتورِ ثبت‌شده با دستگاهِ زنجیره‌دار).
 *  ⚠️ پاسخِ نوشتن ۲۰۰ است (نه ۲۰۱) و ردِ ردیف در `conflicts` می‌آید؛ ردِ کلِ دسته همان ۴۰۳/۴۰۹ ِ پیشین است.
 */
/** ردیفِ `{ id, ...fields }` (یا `{ id, data }`) ⇒ یک تغییرِ راهِ قدیم */
function change(collection, row, { updatedAt, deleted } = {}) {
  const { id, data, updatedAt: at, ...rest } = row || {};
  const c = { collection, id, updatedAt: updatedAt ?? at ?? Date.now() };
  if (deleted) c.deleted = true; else c.data = data && typeof data === 'object' ? data : rest;
  return c;
}

/** `POST /api/sync` ِ چند تغییر (`change(...)`) */
async function legacyPush(token, changes, { deviceId, operationId, headers = {} } = {}) {
  return api('POST', '/api/sync', { token, headers, body: { deviceId, operationId, changes } });
}

/** کاشتنِ یک یا چند ردیفِ یک مجموعه: `seed(token, 'products', { id, name })` */
async function seed(token, collection, rows, opts = {}) {
  return legacyPush(token, [rows].flat().map((r) => change(collection, r, opts)), opts);
}

/** ردیف‌های یک مجموعه از pull ِ راهِ قدیم (آخرین نسخهٔ هر شناسه) به شکلِ `{ id, rev, updatedAt, userId, deleted, ...data }` */
async function legacyRows(token, collection, { headers = {}, includeDeleted = false } = {}) {
  const out = new Map();
  for (let since = 0; ;) {
    const r = await api('GET', `/api/sync?since=${since}&limit=5000`, { token, headers });
    if (r.status !== 200) throw new Error(`pull ِ راهِ قدیم نشد: ${r.status} ${JSON.stringify(r.body)}`);
    for (const c of r.body.changes) if (c.collection === collection) { out.delete(c.id); out.set(c.id, c); }
    if (!r.body.hasMore) break;
    since = r.body.rev;
  }
  return [...out.values()].filter((c) => includeDeleted || !c.deleted)
    .map((c) => ({ id: c.id, rev: c.rev, updatedAt: c.updatedAt, userId: c.userId, deleted: c.deleted, ...(c.data || {}) }));
}

/** یک ردیف (زنده) یا null */
async function legacyRow(token, collection, id, opts = {}) {
  return (await legacyRows(token, collection, opts)).find((r) => r.id === id) || null;
}

/**
 * لایهٔ نقشِ دکانِ همین توکن — درِ دکانِ `PUT /api/shop/permissions/roles/:role` برداشته شد (هیچ برنامه‌ای
 * صدایش نمی‌زد)؛ همان `staff-perms.setRoleLayer` که درِ ارشدِ پنل (`/api/admin/shops/:id/staff-perms/roles/:role`)
 * می‌زند. فقط برای چیدنِ آزمون؛ مجوزِ «چه کسی لایه را عوض می‌کند» سنجهٔ درِ ارشد است.
 */
async function roleLayer(token, role, perms) {
  const me = await api('GET', '/api/shop/me', { token });
  const shopId = me.body?.shop?.id;
  if (!shopId) throw new Error(`دکانِ این توکن پیدا نشد: ${JSON.stringify(me.body)}`);
  try {
    return { status: 200, body: { role, perms: await require('../src/lib/staff-perms').setRoleLayer(shopId, role, perms, 'test') } };
  } catch (e) {
    if (!e.status) throw e;
    return { status: e.status, body: { error: { code: e.code, message: e.message } } };
  }
}

/**
 * پرچم‌های پیش‌فرض‌روشنِ `lib/flag-defaults.js` (۱۴۰۵/۰۸/۲۲) برای یک دکان `false` ِ صریح ⇒ همان رفتارِ پیش از آن تاریخ.
 * برای آزمونی که «خاموش» یا شکلِ دقیقِ پاسخِ پیشین را می‌سنجد؛ آزمون هر کلیدی را که می‌خواهد پس از این خودش روشن می‌کند.
 */
async function defaultOnOff(shopId, keys = [...require('../src/lib/flag-defaults').DEFAULT_ON]) {
  for (const key of keys) {
    await query(
      `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,'false',$3)
       ON CONFLICT (scope, key) DO UPDATE SET value=EXCLUDED.value`, [String(shopId), key, Date.now()],
    );
  }
  require('../src/lib/hot-cache').invalidate('flag');
}

module.exports = {
  withoutEpochs, tampered, defaultOnOff, change, legacyPush, seed, legacyRows, legacyRow, roleLayer,
  start, stop, resetDatabase, api, get, post, put, patch, del, raw, download, newUser, signIn, query,
  base: () => base,
  //  سرورِ HTTPِ آزمون — برای سوار کردنِ سوکتِ زنده (‎index.js‎ در تولید همین کار را می‌کند)
  server: () => server,
};
