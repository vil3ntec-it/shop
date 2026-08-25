'use strict';
/* دوره آزمایشی، پلن‌ها و قفل قابلیت‌ها. */
const test = require('node:test');
const assert = require('node:assert');
const fs = require('fs'); const os = require('os'); const path = require('path');

const TMP = fs.mkdtempSync(path.join(os.tmpdir(), 'tohid-trial-'));
process.env.DATA_DIR = TMP; process.env.KEYS_DIR = path.join(TMP, 'keys');
process.env.DB_PATH = path.join(TMP, 'test.db');
process.env.RATE_AUTH_MAX = '500'; process.env.RATE_GENERAL_MAX = '100000';
process.env.LOGIN_LOCKOUT_TRIES = '500';

const config = require('../src/config');
const ck = require('../src/lib/crypto-keys');
const { getDb } = require('../src/db');
const entLib = require('../src/lib/entitlement');
const plansLib = require('../src/lib/plans');

let server, base;
async function boot() {
  ck.writeKeyPair(config.keysDir, await ck.generateKeyPair());
  const { createApp } = require('../src/app');
  const app = await createApp();
  await new Promise(r => { server = app.listen(0, '127.0.0.1', r); });
  base = `http://127.0.0.1:${server.address().port}`;
}
async function req(p, { method = 'GET', body, token } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  if (token) headers.Authorization = 'Bearer ' + token;
  const r = await fetch(base + p, { method, headers, body: body ? JSON.stringify(body) : undefined });
  let data = null; try { data = await r.json(); } catch {}
  return { status: r.status, data };
}
async function signup(email) {
  await req('/api/v1/auth/register', { method: 'POST', body: { email, password: 'strong-pass-123', name: email.split('@')[0] } });
  const r = await req('/api/v1/auth/login', { method: 'POST', body: { identifier: email, password: 'strong-pass-123' } });
  return { token: r.data.accessToken, userId: r.data.user.id };
}
const DAY = 24 * 60 * 60 * 1000;

test.before(async () => { await boot(); });
test.after(() => { if (server) server.close(); fs.rmSync(TMP, { recursive: true, force: true }); });

// ============ پلن‌ها ============
test('پلن‌ها با قیمت‌های درست ساخته می‌شوند', async () => {
  const r = await req('/api/v1/billing/plans');
  assert.equal(r.status, 200);
  const byCode = Object.fromEntries(r.data.plans.map(p => [p.code, p]));
  assert.equal(r.data.plans.length, 3, 'دقیقاً سه اشتراک');
  assert.equal(byCode.m1.price, 500);
  assert.equal(byCode.m6.price, 2000);
  assert.equal(byCode.y1.price, 3000);
  assert.equal(r.data.trialDays, 7);
});

test('قیمت روزانه هرچه مدت بیشتر شود کمتر می‌شود', async () => {
  const r = await req('/api/v1/billing/plans');
  const order = ['m1', 'm6', 'y1'];
  const byCode = Object.fromEntries(r.data.plans.map(p => [p.code, p]));
  for (let i = 1; i < order.length; i++) {
    const prev = byCode[order[i - 1]].pricePerDay;
    const cur = byCode[order[i]].pricePerDay;
    assert.ok(cur < prev, `${order[i]} (${cur}) باید ارزان‌تر از ${order[i - 1]} (${prev}) باشد`);
  }
});

test('لینک واتساپ با شماره درست ساخته می‌شود', async () => {
  const r = await req('/api/v1/billing/plans');
  assert.match(r.data.whatsapp.url, /wa\.me\/93792236008/);
  assert.equal(r.data.whatsapp.number, '0792236008');
});

// ============ مهمان ============
test('مهمان بدون حساب فقط قابلیت‌های رایگان دارد', async () => {
  const r = await req('/api/v1/billing/status');
  assert.equal(r.status, 200);
  const e = r.data.entitlement;
  assert.equal(e.source, 'guest');
  assert.ok(e.features.includes('warehouse'), 'انبار باید رایگان باشد');
  assert.ok(e.features.includes('reports'), 'گزارشات باید رایگان باشد');
  assert.ok(!e.features.includes('sales'), 'فروش نباید رایگان باشد');
  assert.ok(!e.features.includes('debtors'), 'قرض‌داران نباید رایگان باشد');
  assert.ok(!e.features.includes('barcode'), 'اسکنر نباید رایگان باشد');
  assert.ok(!e.features.includes('multi_device'), 'چند کاربر نباید رایگان باشد');
});

// ============ دوره آزمایشی ============
let user;
test('ثبت‌نام دوره آزمایشی ۷ روزه را شروع می‌کند', async () => {
  user = await signup('trial@test.af');
  const r = await req('/api/v1/billing/status', { token: user.token });
  const e = r.data.entitlement;
  assert.equal(e.source, 'trial');
  assert.equal(e.trial.used, true);
  assert.equal(e.trial.active, true);
  assert.equal(e.trial.daysLeft, 7);
  const span = e.trial.endsAt - e.trial.startedAt;
  assert.ok(Math.abs(span - 7 * DAY) < 1000, 'مدت باید دقیقاً ۷ روز باشد');
});

test('در دوره آزمایشی همه قابلیت‌ها باز است', async () => {
  const r = await req('/api/v1/billing/status', { token: user.token });
  const f = r.data.entitlement.features;
  for (const k of ['sales', 'debtors', 'barcode', 'multi_device', 'warehouse', 'reports']) {
    assert.ok(f.includes(k), `${k} باید در دوره آزمایشی باز باشد`);
  }
  const prot = await req('/api/v1/protected/reports', { token: user.token });
  assert.equal(prot.status, 200);
});

test('پیام روزهای باقی‌مانده درست است', () => {
  const t0 = Date.now();
  const mk = (msLeft) => ({ used: true, active: msLeft > 0, msLeft,
    daysLeft: Math.max(0, Math.ceil(msLeft / DAY)), startedAt: t0, endsAt: t0 + msLeft });
  assert.match(entLib.trialMessage(mk(5 * DAY)), /۵ روز/);
  assert.match(entLib.trialMessage(mk(12 * 60 * 60 * 1000)), /کمتر از یک روز/);
  assert.match(entLib.trialMessage(mk(0)), /به پایان رسیده/);
});

test('پس از پایان دوره، قابلیت‌های اشتراکی قفل می‌شوند ولی داده می‌ماند', async () => {
  // دوره را به گذشته می‌بریم (کاری که فقط سرور می‌تواند بکند)
  getDb().prepare('UPDATE users SET trial_started_at=?, trial_ends_at=? WHERE id=?')
    .run(Date.now() - 8 * DAY, Date.now() - DAY, user.userId);

  const r = await req('/api/v1/billing/status', { token: user.token });
  const e = r.data.entitlement;
  assert.equal(e.source, 'free');
  assert.equal(e.trial.active, false);
  assert.ok(!e.features.includes('sales'), 'فروش باید قفل شود');
  assert.ok(!e.features.includes('debtors'), 'قرض‌داران باید قفل شود');
  assert.ok(e.features.includes('warehouse'), 'انبار باید باز بماند');
  assert.match(e.message, /به پایان رسیده/);

  // حساب هنوز کار می‌کند
  const me = await req('/api/v1/auth/me', { token: user.token });
  assert.equal(me.status, 200);
  assert.equal(me.data.user.id, user.userId);
});

test('API قابلیت قفل‌شده را رد می‌کند', async () => {
  const r = await req('/api/v1/protected/backup', { token: user.token });
  // backup رایگان است، پس باید باز باشد
  assert.equal(r.status, 200);
  // reports هم رایگان است
  const rep = await req('/api/v1/protected/reports', { token: user.token });
  assert.equal(rep.status, 200);
});

test('دوره آزمایشی دوباره فعال نمی‌شود', async () => {
  // تلاش مستقیم روی خود تابع: باید بگوید قبلاً استفاده شده
  const again = entLib.startTrialIfEligible(user.userId);
  assert.equal(again.fresh, false, 'نباید دوره تازه بدهد');

  const r = await req('/api/v1/billing/status', { token: user.token });
  assert.equal(r.data.entitlement.trial.active, false, 'هنوز باید تمام‌شده باشد');
});

test('ورود دوباره دوره آزمایشی را برنمی‌گرداند', async () => {
  const again = await req('/api/v1/auth/login', {
    method: 'POST', body: { identifier: 'trial@test.af', password: 'strong-pass-123' },
  });
  const r = await req('/api/v1/billing/status', { token: again.data.accessToken });
  assert.equal(r.data.entitlement.trial.active, false);
  assert.equal(r.data.entitlement.source, 'free');
});

// ============ خرید اشتراک ============
let adminToken;
test('مدیر درخواست خرید را تأیید می‌کند و همان حساب فعال می‌شود', async () => {
  const pw = require('../src/lib/password');
  const { newId, now } = require('../src/db');
  getDb().prepare(`INSERT INTO admins (id,username,name,password_hash,role,status,created_at)
                   VALUES (?,?,?,?,?,'active',?)`)
    .run(newId('adm'), 'boss', 'مدیر', await pw.hashPassword('admin-pass-9876'), 'superadmin', now());
  const login = await req('/api/v1/admin/login', { method: 'POST', body: { username: 'boss', password: 'admin-pass-9876' } });
  adminToken = login.data.token;

  const reqRes = await req('/api/v1/billing/request', {
    method: 'POST', token: user.token, body: { planCode: 'y1' },
  });
  assert.equal(reqRes.status, 201);
  assert.match(reqRes.data.whatsappUrl, /wa\.me\/93792236008/);

  const list = await req('/api/v1/admin/purchase-requests', { token: adminToken });
  const pending = list.data.requests.find(x => x.status === 'pending');
  assert.ok(pending);

  const ok = await req(`/api/v1/admin/purchase-requests/${pending.id}/approve`, {
    method: 'POST', token: adminToken,
  });
  assert.equal(ok.status, 200);

  // همان حساب حالا اشتراک دارد
  const st = await req('/api/v1/billing/status', { token: user.token });
  assert.equal(st.data.entitlement.source, 'subscription');
  assert.ok(st.data.entitlement.features.includes('sales'));
  assert.ok(st.data.entitlement.features.includes('debtors'));
});

test('مدیر قیمت را بدون تغییر کد عوض می‌کند', async () => {
  const upd = await req('/api/v1/admin/plans/m1', {
    method: 'PATCH', token: adminToken, body: { price: 350 },
  });
  assert.equal(upd.status, 200);
  assert.equal(upd.data.plan.price, 350);

  const pub = await req('/api/v1/billing/plans');
  assert.equal(pub.data.plans.find(p => p.code === 'm1').price, 350);

  // برگرداندن
  await req('/api/v1/admin/plans/m1', { method: 'PATCH', token: adminToken, body: { price: 500 } });
});

test('مدیر مدت دوره آزمایشی و شماره واتساپ را عوض می‌کند', async () => {
  const upd = await req('/api/v1/admin/config', {
    method: 'PATCH', token: adminToken, body: { trial_days: 14, whatsapp_number: '0700000000' },
  });
  assert.equal(upd.status, 200);
  assert.equal(plansLib.trialDays(getDb()), 14);

  const pub = await req('/api/v1/billing/plans');
  assert.equal(pub.data.trialDays, 14);
  assert.match(pub.data.whatsapp.url, /wa\.me\/93700000000/);

  await req('/api/v1/admin/config', { method: 'PATCH', token: adminToken,
    body: { trial_days: 7, whatsapp_number: '0792236008' } });
});

test('کاربر نمی‌تواند قیمت یا پلن را تغییر دهد', async () => {
  const r = await req('/api/v1/admin/plans/m1', {
    method: 'PATCH', token: user.token, body: { price: 1 },
  });
  assert.ok(r.status === 401 || r.status === 403);
});
