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
process.env.RATE_GENERAL_MAX = '100000';
process.env.RATE_AUTH_MAX = '10000';
process.env.RATE_OTP_MAX = '10000';
process.env.RATE_JOIN_MAX = '10000';
process.env.LOGIN_LOCKOUT_TRIES = '10000';
process.env.OTP_RESEND_SECONDS = '0';

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
  return { status: res.status, body: json };
}

const get = (p, o) => api('GET', p, o);
const post = (p, body, o = {}) => api('POST', p, { ...o, body });
const put = (p, body, o = {}) => api('PUT', p, { ...o, body });
const patch = (p, body, o = {}) => api('PATCH', p, { ...o, body });
const del = (p, o) => api('DELETE', p, o);

/** ساخت یک کاربر تازه با رمز و برگرداندن نشست او. */
let seq = 0;
async function newUser(name = 'کاربر') {
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
    name, password, device,
    terms: { accepted: true },
  });
  if (done.status >= 300) throw new Error(`ثبت‌نام نشد: ${JSON.stringify(done.body)}`);
  return { ...done.body, email, phone: done.body.user?.phone ?? null, password };
}

module.exports = { start, stop, resetDatabase, api, get, post, put, patch, del, newUser, query };
