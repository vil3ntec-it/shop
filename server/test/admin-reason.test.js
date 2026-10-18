'use strict';
/**
 * دلیلِ اختیاری برای کارهای حساسِ مدیر — شکافِ G2ِ `docs/spec/ADMIN-CONTROL-PLANE.md`
 * (v3 ۱۹.۱۰ بندِ ۳ و ۳۹.۸).
 *
 *   • اگر مدیر دلیل بفرستد، همان در ردیفِ سابقهٔ همان کار می‌نشیند
 *   • اگر نفرستد، رفتار دقیقاً همان قبلی است: همان پاسخ و ردیفی بی کلیدِ `reason`
 *
 * کارها: بستن/بازکردنِ کاربر و پمپ، دادن/تغییرِ اشتراک (دکان و پمپ)،
 * تأییدِ درخواستِ خرید، ثبت/ویرایش/حذفِ پرداخت. هنوز **اجباری نیست**.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'arshad','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const token = async () =>
  (await h.post('/api/admin/login', { username: 'arshad', password: 'Admin!12345' })).body.token;

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

async function station(uid) {
  const t = await token();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 30 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: uid, name: 'پمپِ دلیل' },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  return on.body.station.id;
}

const parse = (d) => (typeof d === 'string' ? JSON.parse(d) : (d || {}));
const auditIds = async (action) =>
  new Set((await many('SELECT id FROM audit_logs WHERE action=$1', [action])).map((r) => r.id));

/**
 * ردیفِ سابقه‌ای که همین درخواست ساخت — با مقایسهٔ شناسه‌ها پیش و پس از
 * آن، نه با «آخرین زمان» (دو درخواست می‌توانند در یک میلی‌ثانیه باشند).
 */
async function newDetail(action, targetId, before) {
  const rows = (await many(
    'SELECT id, detail FROM audit_logs WHERE action=$1 AND target_id=$2', [action, String(targetId)]
  )).filter((r) => !before.has(r.id));
  assert.equal(rows.length, 1, `باید دقیقاً یک ردیفِ تازهٔ ${action} برای ${targetId} باشد — ${rows.length}`);
  return parse(rows[0].detail);
}

/** هر کار دو بار: یک بار بی دلیل (کلیدِ reason نیست)، یک بار با دلیل. */
async function both(label, run, action, targetOf) {
  let before = await auditIds(action);
  const plain = await run({});
  assert.ok(plain.status < 300, `${label} بی دلیل ⇒ ${plain.status} ${JSON.stringify(plain.body)}`);
  const d1 = await newDetail(action, targetOf(plain), before);
  assert.ok(!('reason' in d1), `${label}: بی دلیل نباید کلیدِ reason داشته باشد — ${JSON.stringify(d1)}`);

  const why = `دلیلِ ${label}`;
  before = await auditIds(action);
  const given = await run({ reason: `  ${why}  ` });
  assert.ok(given.status < 300, `${label} با دلیل ⇒ ${given.status} ${JSON.stringify(given.body)}`);
  const d2 = await newDetail(action, targetOf(given), before);
  assert.equal(d2.reason, why, `${label}: دلیل باید (پیراسته) در سابقه بنشیند`);
  return { plain, given };
}

test('بستن و بازکردنِ کاربر', async () => {
  const t = await token();
  const u = await h.newUser('بسته‌شدنی', 'shop');
  let flip = 'disabled';
  await both('وضعیتِ کاربر', async (extra) => {
    const r = await h.post(`/api/admin/users/${u.user.id}/status`, { status: flip, ...extra }, { token: t });
    flip = flip === 'disabled' ? 'active' : 'disabled';
    return r;
  }, 'admin.user_status', () => u.user.id);
});

test('دادن، تغییر و وضعیتِ اشتراکِ دکان', async () => {
  const t = await token();
  const o = await shopOwner('اشتراکی');
  const { given } = await both('دادنِ اشتراک',
    (extra) => h.post('/api/admin/subscriptions', { shopId: o.shopId, plan: 'm1', days: 30, ...extra }, { token: t }),
    'admin.subscription_granted', (r) => r.body.subscription.id);
  const subId = given.body.subscription.id;

  await both('تغییرِ اشتراک',
    (extra) => h.put(`/api/admin/subscriptions/${subId}`, { note: 'ویرایش', ...extra }, { token: t }),
    'admin.subscription_updated', () => subId);

  let flip = 'suspended';
  await both('وضعیتِ اشتراک', async (extra) => {
    const r = await h.post(`/api/admin/subscriptions/${subId}/status`, { status: flip, ...extra }, { token: t });
    flip = flip === 'suspended' ? 'active' : 'suspended';
    return r;
  }, 'admin.subscription_status', () => subId);
});

test('تأییدِ درخواستِ خرید', async () => {
  const t = await token();
  const o = await shopOwner('خریدار');
  const ids = [];
  for (let i = 0; i < 2; i += 1) {
    const made = await h.post('/api/me/purchase-request', { plan: 'm1' }, { token: o.accessToken });
    assert.equal(made.status, 201, JSON.stringify(made.body));
    ids.push(made.body.request.id);
  }
  let i = 0;
  await both('تأییدِ خرید', (extra) => {
    const id = ids[i]; i += 1;
    return h.post(`/api/admin/purchase-requests/${id}/approve`, { ...extra }, { token: t })
      .then((r) => ({ ...r, id }));
  }, 'admin.request_approved', (r) => r.id);
});

test('ثبت، ویرایش و حذفِ پرداخت', async () => {
  const t = await token();
  const o = await shopOwner('پرداخت‌کننده');
  const { given } = await both('ثبتِ پرداخت',
    (extra) => h.post('/api/admin/payments', { app: 'shop', tenantId: o.shopId, amount: 1500, ...extra }, { token: t }),
    'admin.payment_created', (r) => r.body.payment.id);
  const payId = given.body.payment.id;

  await both('ویرایشِ پرداخت',
    (extra) => h.put(`/api/admin/payments/${payId}`, { amount: 1600, ...extra }, { token: t }),
    'admin.payment_updated', () => payId);

  //  حذف فقط یک بار شدنی است ⇒ دو پرداختِ جدا
  const second = await h.post('/api/admin/payments', { app: 'shop', tenantId: o.shopId, amount: 700 }, { token: t });
  const doomed = [payId, second.body.payment.id];
  let k = 0;
  await both('حذفِ پرداخت', (extra) => {
    const id = doomed[k]; k += 1;
    return h.del(`/api/admin/payments/${id}`, { token: t, body: Object.keys(extra).length ? extra : null })
      .then((r) => ({ ...r, id }));
  }, 'admin.payment_deleted', (r) => r.id);
});

test('بستنِ پمپ و دادن/وضعیتِ اشتراکِ پمپ', async () => {
  const t = await token();
  const stationId = await station('pc-reason-1');
  let flip = 'disabled';
  await both('وضعیتِ پمپ', async (extra) => {
    const r = await h.post(`/api/admin/pump/stations/${stationId}/status`, { status: flip, ...extra }, { token: t });
    flip = flip === 'disabled' ? 'active' : 'disabled';
    return r;
  }, 'admin.station_status', () => stationId);

  const { given } = await both('دادنِ اشتراکِ پمپ',
    (extra) => h.post('/api/admin/pump/subscriptions', { stationId, plan: 'vip', days: 30, ...extra }, { token: t }),
    'admin.pump_subscription_granted', () => stationId);
  const subId = given.body.subscription.id;

  let s = 'suspended';
  await both('وضعیتِ اشتراکِ پمپ', async (extra) => {
    const r = await h.post(`/api/admin/pump/subscriptions/${subId}/status`, { status: s, ...extra }, { token: t });
    s = s === 'suspended' ? 'active' : 'suspended';
    return r;
  }, 'admin.pump_subscription_status', () => subId);
});

test('دلیل از querystring هم پذیرفته می‌شود و بلندش بریده می‌شود', async () => {
  const t = await token();
  const u = await h.newUser('بلنددلیل', 'shop');
  const long = 'ب'.repeat(260);
  let before = await auditIds('admin.user_status');
  const r = await h.post(`/api/admin/users/${u.user.id}/status?reason=${encodeURIComponent('از نشانی')}`, { status: 'disabled' }, { token: t });
  assert.equal(r.status, 200);
  assert.equal((await newDetail('admin.user_status', u.user.id, before)).reason, 'از نشانی');
  before = await auditIds('admin.user_status');
  const r2 = await h.post(`/api/admin/users/${u.user.id}/status`, { status: 'active', reason: long }, { token: t });
  assert.equal(r2.status, 200);
  assert.equal((await newDetail('admin.user_status', u.user.id, before)).reason.length, 200);
});
