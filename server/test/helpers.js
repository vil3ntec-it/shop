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
process.env.RATE_JOIN_MAX = '10000';
process.env.LOGIN_LOCKOUT_TRIES = '10000';
//  شمارنده‌ی دومِ قفل هم باید باز باشد، وگرنه سنجه‌هایی که عمداً رمزِ
//  غلط می‌زنند همدیگر را می‌بندند — همه از یک IP می‌آیند (127.0.0.1)
process.env.LOGIN_LOCKOUT_GLOBAL_TRIES = '10000';
process.env.OTP_RESEND_SECONDS = '0';
//  پیامِ همگانی سقفِ ۵تایی دارد و چند آزمون پشتِ سرِ هم می‌زنندش؛
//  خودِ سقف در `pump-support.test.js` جدا و با عددِ کوچک سنجیده می‌شود.
process.env.RATE_BROADCAST_MAX = '10000';

const { createApp } = require('../src/app');
const { query, closeDb } = require('../src/db');
const migrate = require('../src/migrate');

let server = null;
let base = '';

async function resetDatabase() {
  await query('DROP SCHEMA public CASCADE');
  await query('CREATE SCHEMA public');
  await migrate.run();
}

async function start() {
  if (server) return base;
  await resetDatabase();
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

module.exports = {
  start, stop, resetDatabase, api, get, post, put, patch, del, raw, download, newUser, signIn, query,
  base: () => base,
};
