'use strict';
/**
 * F0-16 — از `register/start` و `password/forgot` نباید بشود فهمید کدام
 * ایمیل حساب دارد.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) `register/start`: ایمیلِ دارای حساب همان پاسخِ ایمیلِ تازه را می‌گیرد
 *      (۲۰۱ با همان کلیدها) — نه ۴۰۹ `already_registered`.
 *   ۲) `register/verify`: کدِ غلط برای هر دو یک خطا دارد؛ «از قبل ثبت شده»
 *      فقط **پس از کدِ درست** گفته می‌شود — یعنی فقط به صاحبِ صندوق.
 *   ۳) `password/forgot`: درخواستِ دوباره در مهلتِ «ارسالِ دوباره» برای هر دو
 *      همان ۲۰۰ است (پیش‌تر حسابِ موجود ۴۲۹ می‌گرفت).
 *   ۴) `password/forgot`: تأخیرِ یکسان — فرستادنِ کندِ ایمیل زمانِ پاسخ را لو نمی‌دهد.
 *   ۵) محدودیتِ دفعات به‌ازای هر IP — با ایمیل‌های گوناگون هم.
 *   ۶) راهِ برگشتِ سازگاری: `SIGNUP_LEGACY_CONFLICT` همان ۴۰۹ِ قدیم را برمی‌گرداند.
 */
//  سقفِ هر IP در این پرونده کوچک است تا بندِ ۵ سنجیده شود؛ هر بند سطل‌ها را خالی می‌کند
process.env.RATE_PROBE_IP_MAX = '12';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const config = require('../src/config');
const otp = require('../src/lib/otp');
const { resetAll } = require('../src/middleware/ratelimit');

const PASS = 'Passw0rd!test';

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

const sortedKeys = (o) => Object.keys(o || {}).sort();

test('F0-16 ۱) register/start: ایمیلِ دارای حساب همان پاسخِ ایمیلِ تازه', async () => {
  const u = await h.newUser('شمارش-۱');
  const taken = await h.post('/api/auth/register/start', { name: 'الف', email: u.email, password: PASS });
  const fresh = await h.post('/api/auth/register/start', { name: 'الف', email: 'enum-fresh-1@test.local', password: PASS });
  assert.equal(fresh.status, 201, JSON.stringify(fresh.body));
  assert.equal(taken.status, fresh.status, `حسابِ موجود: ${JSON.stringify(taken.body)}`);
  assert.deepEqual(sortedKeys(taken.body), sortedKeys(fresh.body));
  assert.equal(taken.body.step, fresh.body.step);
});

test('F0-16 ۲) register/verify: کدِ غلط برای هر دو یک خطا؛ «ثبت شده» فقط پس از کدِ درست', async () => {
  const u = await h.newUser('شمارش-۲');
  const freshEmail = 'enum-fresh-2@test.local';
  const s1 = await h.post('/api/auth/register/start', { name: 'ب', email: u.email, password: PASS });
  const s2 = await h.post('/api/auth/register/start', { name: 'ب', email: freshEmail, password: PASS });
  assert.equal(s2.status, 201, JSON.stringify(s2.body));

  const wrongTaken = await h.post('/api/auth/register/verify', { email: u.email, code: '000000' });
  const wrongFresh = await h.post('/api/auth/register/verify', { email: freshEmail, code: '000000' });
  assert.equal(wrongTaken.status, wrongFresh.status, JSON.stringify({ wrongTaken: wrongTaken.body, wrongFresh: wrongFresh.body }));
  assert.equal(wrongTaken.body.error?.code, wrongFresh.body.error?.code);

  //  صاحبِ صندوق با کدِ درست می‌فهمد که حساب دارد — و هیچ بلیتی نمی‌گیرد
  assert.ok(s1.body.devCode, JSON.stringify(s1.body));
  const right = await h.post('/api/auth/register/verify', { email: u.email, code: s1.body.devCode });
  assert.equal(right.status, 409, JSON.stringify(right.body));
  assert.equal(right.body.error.code, 'already_registered');
  assert.equal(right.body.ticket, undefined);
});

test('F0-16 ۳) password/forgot: درخواستِ دوباره در مهلت برای هر دو همان ۲۰۰', async () => {
  const u = await h.newUser('شمارش-۳');
  const keep = config.otp.resendMs;
  config.otp.resendMs = 120 * 1000;
  try {
    const ask = (email) => h.post('/api/auth/password/forgot', { email });
    const t1 = await ask(u.email);
    const t2 = await ask(u.email);
    const n1 = await ask('enum-nobody-3@test.local');
    const n2 = await ask('enum-nobody-3@test.local');
    assert.equal(t1.status, 200, JSON.stringify(t1.body));
    assert.equal(n1.status, 200, JSON.stringify(n1.body));
    assert.equal(t2.status, n2.status, `حسابِ موجود: ${JSON.stringify(t2.body)}`);
    const { email: _a, devCode: _b, ...shapeT } = t2.body;
    const { email: _c, devCode: _d, ...shapeN } = n2.body;
    assert.deepEqual(shapeT, shapeN);
  } finally {
    config.otp.resendMs = keep;
  }
});

test('F0-16 ۴) password/forgot: فرستادنِ کند زمانِ پاسخ را لو نمی‌دهد', async () => {
  const u = await h.newUser('شمارش-۴');
  const origLog = otp.senders.log;
  const keepFloor = config.otp.uniformMs;
  otp.senders.log = async (...args) => {
    await new Promise((r) => setTimeout(r, 400));
    return origLog(...args);
  };
  config.otp.uniformMs = 700;
  try {
    const timed = async (email) => {
      const t = Date.now();
      const r = await h.post('/api/auth/password/forgot', { email });
      assert.equal(r.status, 200, JSON.stringify(r.body));
      return Date.now() - t;
    };
    const taken = await timed(u.email);
    const nobody = await timed('enum-nobody-4@test.local');
    assert.ok(Math.abs(taken - nobody) < 200,
      `زمانِ پاسخ نباید وجودِ حساب را لو دهد: موجود ${taken}ms، ناموجود ${nobody}ms`);
  } finally {
    otp.senders.log = origLog;
    config.otp.uniformMs = keepFloor;
  }
});

test('F0-16 ۵) محدودیتِ دفعات به‌ازای هر IP — با ایمیل‌های گوناگون هم', async () => {
  const statuses = [];
  for (let i = 0; i < 14; i++) {
    const r = i % 2
      ? await h.post('/api/auth/password/forgot', { email: `enum-probe-${i}@test.local` })
      : await h.post('/api/auth/register/start', { name: 'ج', email: `enum-probe-${i}@test.local`, password: PASS });
    statuses.push(r.status);
  }
  assert.ok(statuses.slice(0, 12).every((s) => s < 400), JSON.stringify(statuses));
  assert.deepEqual(statuses.slice(12), [429, 429], JSON.stringify(statuses));
});

test('F0-16 ۶) سازگاری: SIGNUP_LEGACY_CONFLICT همان ۴۰۹ِ قدیم را برمی‌گرداند', async () => {
  const u = await h.newUser('شمارش-۶');
  const keep = config.signupLegacyConflict;
  config.signupLegacyConflict = true;
  try {
    const r = await h.post('/api/auth/register/start', { name: 'د', email: u.email, password: PASS });
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'already_registered');
  } finally {
    config.signupLegacyConflict = keep;
  }
});
