'use strict';
/**
 * PRM-13 — قاعدهٔ یکسانِ امتیازِ وفاداری برای همهٔ دستگاه‌های دکان (`/api/shop/loyalty`، `lib/loyalty-rule.js`) و امتیاز
 * در «حسابِ من» ِ ویترین (CRM-13، `lib/store-account-history.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموشِ `shop.loyalty_rule` ⇒ `GET` فقط `{enabled:false}`، `PUT` ۴۰۳ و هیچ ردیفی؛ «حسابِ من» بی کلیدِ امتیاز.
 *   ۲) نوشتن فقط صاحب و مدیر، روی سرور؛ صندوق‌دار و شاگرد ۴۰۳ ولی قاعده را می‌خوانند. عددِ ناصحیح ۴۰۰؛ نسخهٔ کهنه ۴۰۹.
 *   ۳) «حسابِ من»: امتیاز و سطح فقط با قاعدهٔ روشن، فقط از ردیف‌های همین شماره (تطبیقِ دقیق) با انقضای FIFO.
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const DAY = 24 * 60 * 60 * 1000;
const RULE = { everyAfn: 100, pointAfn: 1, maxSharePct: 20, silverAfn: 50000, goldAfn: 200000, expiryMonths: 0 };

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: u.accessToken, shopId: made.body.shop.id, device: `dev-${uniq()}` };
}

async function member(s, role, label) {
  const u = await h.newUser(label, 'shop');
  const code = (await h.post('/api/shop/staff-code', { role }, { token: s.token })).body.code;
  assert.ok(code, `کدِ ${role}`);
  const j = await h.post('/api/shop/staff/join', { code }, { token: u.accessToken });
  assert.ok([200, 201].includes(j.status), JSON.stringify(j.body));
  return u.accessToken;
}

const rowOf = (shopId) => one('SELECT * FROM shop_loyalty_rules WHERE shop_id=$1', [shopId]);

test('PRM-13 ⛔: پرچمِ خاموش ⇒ GET فقط enabled:false، PUT ۴۰۳ و هیچ ردیفی', async () => {
  const s = await shop('بی‌پرچم');
  const g = await h.get('/api/shop/loyalty', { token: s.token });
  assert.equal(g.status, 200, JSON.stringify(g.body));
  assert.deepEqual(g.body, { enabled: false });
  const p = await h.put('/api/shop/loyalty', { on: true, rule: RULE }, { token: s.token });
  assert.equal(p.status, 403, JSON.stringify(p.body));
  assert.equal(p.body.error.code, 'loyalty_rule_disabled');
  assert.ok(!(await rowOf(s.shopId)), "هیچ ردیفی");
  assert.equal((await h.get('/api/shop/loyalty')).status, 401);
});

test('PRM-13: قاعده را فقط صاحب و مدیر می‌نویسند (سرور)؛ صندوق‌دار و شاگرد می‌خوانند و ۴۰۳ می‌گیرند', async () => {
  const s = await shop('قاعدهٔ یکسان');
  await setFlag(s.shopId, 'shop.loyalty_rule', true);
  const manager = await member(s, 'manager', 'مدیرِ امتیاز');
  const staff = await member(s, 'staff', 'شاگردِ امتیاز');

  const empty = await h.get('/api/shop/loyalty', { token: staff });
  assert.equal(empty.status, 200, JSON.stringify(empty.body));
  assert.deepEqual(empty.body, { enabled: true, on: false, rule: null, version: 0, updatedAt: 0, permissions: { manage: false } });

  const own = await h.put('/api/shop/loyalty', { on: true, rule: { ...RULE, expiryMonths: 12 } }, { token: s.token });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.equal(own.body.version, 1);
  assert.deepEqual(own.body.rule, { ...RULE, expiryMonths: 12 });

  const mgr = await h.put('/api/shop/loyalty', { on: true, rule: { ...RULE, everyAfn: 50 }, expectedVersion: 1 }, { token: manager });
  assert.equal(mgr.status, 200, JSON.stringify(mgr.body));
  assert.equal(mgr.body.version, 2);

  const deny = await h.put('/api/shop/loyalty', { on: false, rule: RULE }, { token: staff });
  assert.equal(deny.status, 403, JSON.stringify(deny.body));
  assert.equal(deny.body.error.code, 'permission_denied');

  const seen = await h.get('/api/shop/loyalty', { token: staff });
  assert.equal(seen.body.on, true);
  assert.equal(seen.body.rule.everyAfn, 50);
  assert.equal(seen.body.permissions.manage, false);
  assert.equal((await h.get('/api/shop/loyalty', { token: manager })).body.permissions.manage, true);

  //  نسخهٔ کهنه ⇒ ۴۰۹ و همان قاعده
  const stale = await h.put('/api/shop/loyalty', { on: false, rule: RULE, expectedVersion: 1 }, { token: s.token });
  assert.equal(stale.status, 409, JSON.stringify(stale.body));
  assert.equal(stale.body.error.code, 'version_conflict');
  assert.equal(Number((await rowOf(s.shopId)).every_afn), 50);
});

test('PRM-13: هر عدد صحیح و در بازهٔ هستهٔ برنامه — رشته، اعشار، طلایی ≤ نقره‌ای، انقضای بیش از ۱۲۰ ⇒ ۴۰۰', async () => {
  const s = await shop('سنجشِ قاعده');
  await setFlag(s.shopId, 'shop.loyalty_rule', true);
  const bad = [
    { on: 'yes', rule: RULE },
    { on: true },
    { on: true, rule: { ...RULE, everyAfn: '100' } },
    { on: true, rule: { ...RULE, pointAfn: 1.5 } },
    { on: true, rule: { ...RULE, goldAfn: RULE.silverAfn } },
    { on: true, rule: { ...RULE, maxSharePct: 101 } },
    { on: true, rule: { ...RULE, expiryMonths: 121 } },
    { on: true, rule: { ...RULE, expiryMonths: -1 } },
  ];
  for (const body of bad) {
    const r = await h.put('/api/shop/loyalty', body, { token: s.token });
    assert.equal(r.status, 400, JSON.stringify([body, r.body]));
  }
  assert.ok(!(await rowOf(s.shopId)), "هیچ ردیفی");
});

// ── «حسابِ من» ِ ویترین ──────────────────────────────────────────────

const op = (table, rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type: 'insert', fields });

async function push(s, ops) {
  const r = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': s.device }, body: { device_id: s.device, schema_version: 2, ops },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  for (const x of r.body.results) assert.ok(['applied', 'duplicate'].includes(x.status), JSON.stringify(x));
}

async function liveStore(label) {
  const s = await shop(label);
  await setFlag(s.shopId, 'shop.orders', true);
  await setFlag(s.shopId, 'shop.storefront', true);
  await setFlag(s.shopId, 'shop.storefront.account_history', true);
  s.slug = `lp-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return s;
}

async function ticket(slug, phone) {
  const st = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return { headers: { 'X-Store-Ticket': c.body.ticket } };
}

const history = (slug, auth) => { resetAll(); return h.get(`/api/store/${slug}/account/history`, auth); };

/** مشتری با یک فاکتورِ ۶۰٬۰۰۰ (نقره‌ای)، کسبِ ۶۰۰، مصرفِ ۱۰۰ و اصلاحِ دستیِ +۵ و −۵ */
async function seedPoints(s, id, phone, at) {
  await push(s, [
    op('debtors', id, { name: 'مشتریِ امتیاز', phone, createdAt: at - 10 * DAY }),
    op('sales', `${id}-s1`, { invoiceNumber: 1, series: 'L1', total: 60000, discount: 0, finalTotal: 60000, paymentMethod: 'cash',
      paidAmount: 60000, status: 'completed', createdAt: at - 5 * DAY, date: '2026-10-28', debtorId: id }),
    op('auditLog', `${id}-s1-lp-e`, { type: 'loyalty_earn', refId: `${id}-s1`, payRef: id, points: 600, amount: 0, createdAt: at - 5 * DAY, notes: 'یادداشتِ داخلی' }),
    op('auditLog', `${id}-s2-lp-s`, { type: 'loyalty_spend', refId: `${id}-s2`, payRef: id, points: 100, amount: 100, createdAt: at - 4 * DAY }),
    op('auditLog', `lpa-${id}-1`, { type: 'loyalty_adjust', payRef: id, points: 5, amount: 0, createdAt: at - 3 * DAY, notes: 'هدیه' }),
    op('auditLog', `lpa-${id}-2`, { type: 'loyalty_adjust', payRef: id, points: -5, amount: 0, createdAt: at - 2 * DAY, notes: 'اصلاح' }),
    //  ردیفِ غیرِ امتیاز و ردیفِ مشتریِ دیگر نباید چیزی را عوض کنند
    op('auditLog', `${id}-x`, { type: 'sale', refId: `${id}-s1`, payRef: id, points: 999, createdAt: at - 5 * DAY }),
  ]);
}

test('CRM-13 + PRM-13 ⛔: بی قاعدهٔ روشنِ دکان «حسابِ من» بایت‌به‌بایت همان پیشین (بی کلیدِ loyalty)', async () => {
  const s = await liveStore('امتیازِ خاموش');
  const phone = '+93799200001';
  await seedPoints(s, 'off-lp', phone, Date.now());
  const auth = await ticket(s.slug, phone);
  const before = await history(s.slug, auth);
  assert.equal(before.status, 200, JSON.stringify(before.body));
  assert.equal(before.body.loyalty, undefined);
  //  پرچم روشن ولی قاعده ذخیره نشده، یا ذخیره ولی خاموش ⇒ همان
  await setFlag(s.shopId, 'shop.loyalty_rule', true);
  assert.deepEqual((await history(s.slug, auth)).body, before.body);
  assert.equal((await h.put('/api/shop/loyalty', { on: false, rule: RULE }, { token: s.token })).status, 200);
  assert.deepEqual((await history(s.slug, auth)).body, before.body);
});

test('CRM-13 + PRM-13: امتیاز و سطح فقط مالِ همین شماره (تطبیقِ دقیق)؛ شمارهٔ دیگر بی امتیاز', async () => {
  const s = await liveStore('امتیازِ روشن');
  const at = Date.now();
  const phone = '+93799200002';
  await seedPoints(s, 'a-lp', phone, at);
  await seedPoints(s, 'b-lp', '+93799200003', at);   //  مشتریِ دیگرِ همین دکان
  await setFlag(s.shopId, 'shop.loyalty_rule', true);
  assert.equal((await h.put('/api/shop/loyalty', { on: true, rule: RULE }, { token: s.token })).status, 200);

  const r = await history(s.slug, await ticket(s.slug, phone));
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.loyalty, { points: 500, tier: 'silver', soon: 0, soonAt: null });
  const text = JSON.stringify(r.body);
  for (const secret of ['یادداشتِ داخلی', 'هدیه', 'a-lp', 'b-lp', 'lpa-']) assert.ok(!text.includes(secret), secret);

  //  شمارهٔ تأییدشدهٔ دیگری که قرض‌دار ندارد ⇒ بی پیوند و بی امتیاز
  const stranger = await history(s.slug, await ticket(s.slug, '+93799200009'));
  assert.equal(stranger.body.linked, false);
  assert.equal(stranger.body.loyalty, undefined);

  //  انقضای ۱ ماهه: کسبِ ۶۰۰ (۵ روز پیش) هنوز زنده و «به‌زودی می‌سوزد»
  assert.equal((await h.put('/api/shop/loyalty', { on: true, rule: { ...RULE, expiryMonths: 1 } }, { token: s.token })).status, 200);
  const soon = (await history(s.slug, await ticket(s.slug, phone))).body.loyalty;
  assert.equal(soon.points, 500);
  assert.equal(soon.soon, 500);
  assert.equal(soon.soonAt, at - 5 * DAY + 2628000000);
});
