'use strict';
/**
 * ⛔ بازبینیِ خصمانهٔ درهای تازه/عوض‌شدهٔ دستهٔ ۳۱ — حمله‌های مشخصی که آزمون‌های فهرستی (`route-authz-inventory`،
 * `tenant-matrix`، `shop-route-inventory`) نمی‌بینند:
 *   ۱) IDOR ِ میانِ دکان‌ها با شناسه‌های نشت‌کرده از پاسخِ خودِ دکانِ «الف» (و همسایهٔ حدس‌زدنی‌شان): همان پاسخِ
 *      «نیست»، بی داده و بی نوشتن؛
 *   ۲) سطحِ دسترسی: شاگرد/مدیر/صاحب/حسابدار/انباردار، لایهٔ نقش و کاربر، عضوِ معلق با توکنِ هنوز باز، نشستِ پس از
 *      «خروج از همه»، رازِ دستگاه پس از «باطل کردنِ این دستگاه» (#1009)؛
 *   ۳) نوعِ حسابِ نادرست: پمپ، پیک، بلیتِ مشتریِ ویترین روی درهای دکان، و توکنِ دکان روی درِ مشتری؛
 *   ۴) ورودیِ بدخواه: نوعِ نادرست (آرایه/شیء)، `__proto__`، عددِ منفی/بزرگ، نویسهٔ یونیکدِ فریبنده در شناسه، بدنهٔ
 *      بزرگ، تکرارِ کلیدِ یکتا با بدنهٔ دیگر؛
 *   ۵) نشت: پاسخِ خطا، لاگ و سابقه بی راز، توکن، HMAC، شماره و نامِ دکانِ دیگر.
 * یافته‌های واقعی (هر سه با رفع و همین آزمون):
 *   ف۱ `GET /api/shop/conflict-inbox/mine` ⇐ `settle.base` شمارهٔ کامل و نشانیِ قرض‌دار را به نقشِ بی `customers.contact`
 *      می‌داد (همان ردیفی که pull برایش بی شماره است) — `lib/conflict-inbox.settleOf`؛
 *   ف۲ `GET /api/shop/orders/customer` ⇐ `?phone=x&phone=<شماره>` یا `customerId` ِ ساختگی «سابقهٔ دسترسی» (CRM-15) را
 *      از پروندهٔ همان مشتری پنهان می‌کرد — `lib/customer-access.noteLookup`؛
 *   ف۳ push ِ v1 ⇐ `table: 'Products'` یا `op_type` سنجشِ `purchaseUnits` (PRD-07) را دور می‌زد — `lib/purchase-units.v1Guard`.
 * سقفِ نرخ (IP پیش از احراز، حساب پس از آن) در `routes-idor-31-limits.test.js` (سقف‌ها از محیط، پیش از بارِ config).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const util = require('node:util');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const staffCodes = require('../src/lib/staff-codes');
const deviceKeys = require('../src/lib/staff-device-keys');
const access = require('../src/lib/customer-access');
const photos = require('../src/lib/product-photos');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

// ── لاگِ سرور در همهٔ این پرونده (همان لاگ، به‌علاوهٔ نسخه‌ای برای سنجهٔ نشت) ─────────────────────────
const METHODS = ['log', 'info', 'warn', 'error', 'debug', 'trace'];
const lines = [];
const real = {};
for (const m of METHODS) {
  real[m] = console[m];
  console[m] = (...a) => { lines.push(util.format(...a)); real[m](...a); };
}
const secrets = new Map();   //  راز ⇒ برچسب
const remember = (label, value) => { const s = String(value ?? ''); if (s.length >= 8) secrets.set(s, label); };

let admin = '';
let A;
let B;
let pump;
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const P1 = '0793310001';
const P2 = '0793310002';
const ADDR = 'کوچهٔ نهان، خانهٔ ۷';

const errOf = (r) => r.body?.error?.code;
const tok = (who) => ({ token: who.token });
const call = (who, method, path, body) => h.api(method, path, { token: who ? who.token : null, ...(body === undefined ? {} : { body }) });
const push1 = (who, ops, device = 'dev-idor') => h.api('POST', '/api/sync/v1/push', {
  token: who.token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
});
const op = (table, rowId, type, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table, row_id: rowId, type, fields });
/** «نیست» ِ بی نشت: همان وضعیت، کد و پیام برای شناسهٔ واقعیِ دکانِ دیگر و شناسهٔ ساختگی */
function sameMiss(a, b, what) {
  assert.equal(a.status, b.status, `${what}: ${JSON.stringify(a.body)} / ${JSON.stringify(b.body)}`);
  assert.deepEqual(a.body, b.body, `${what}: پاسخِ شناسهٔ واقعی و ساختگی باید یکی باشد`);
}
/** پاسخ هیچ‌کدام از این‌ها را ندارد (نام، شناسه، شماره) */
function noLeak(r, needles, what) {
  const text = JSON.stringify(r.body ?? '');
  for (const n of needles.filter(Boolean)) assert.ok(!text.includes(n), `${what}: «${n}» در پاسخ آمد: ${text.slice(0, 300)}`);
}
/** شناسهٔ همسایه (حدس‌زدنی): نویسهٔ آخر عوض */
const neighbour = (id) => id.slice(0, -1) + (id.slice(-1) === '0' ? '1' : '0');

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  const id = newId('mem');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,$4,'active',$5,$5)`, [id, shopId, u.user.id, role, now()]);
  remember(`token:${name}`, u.accessToken);
  remember(`refresh:${name}`, u.refreshToken);
  return { ...u, token: u.accessToken, userId: u.user.id, memberId: id, name };
}

/** شاگردِ کدی روی دستگاهِ `uid` — با رازِ دستگاه (برنامهٔ تازه) یا بی آن (برنامهٔ کهنه) */
async function codeStaff(S, uid, name, { key = true } = {}) {
  const made = await staffCodes.create(S.id, S.owner.userId, { role: 'staff', maxUses: 1 });
  const device = { uid, name: `دستگاهِ ${name}`, platform: 'android', ...(key ? { keyVersion: 1 } : {}) };
  const r = await h.post('/api/auth/staff', { code: made.code, name, device });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(typeof r.body.deviceKey === 'string', key, 'رازِ دستگاه فقط برای برنامهٔ تازه');
  remember(`token:${name}`, r.body.accessToken);
  remember(`refresh:${name}`, r.body.refreshToken);
  if (r.body.deviceKey) { remember(`key:${name}`, r.body.deviceKey); remember(`keyhash:${name}`, deviceKeys.hashKey(r.body.deviceKey)); }
  remember(`code:${name}`, made.code);
  return { uid, name, key: r.body.deviceKey || '', token: r.body.accessToken, refreshToken: r.body.refreshToken, userId: r.body.user.id, code: made.code };
}

async function shopWith(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  remember(`token:owner ${label}`, o.accessToken);
  const name = `دکانِ ${label}-${uniq()}`;
  const made = await h.post('/api/shop', { name }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.shop.id;
  await query('UPDATE shops SET max_members=50 WHERE id=$1', [id]);
  const ownerMember = await one(`SELECT id FROM shop_members WHERE shop_id=$1 AND role='owner'`, [id]);
  const S = { id, name, label, owner: { ...o, token: o.accessToken, userId: o.user.id, memberId: ownerMember.id, name: `صاحبِ ${label}` } };
  for (const [k, role] of [['manager', 'manager'], ['staff', 'staff'], ['storekeeper', 'storekeeper'], ['cashier', 'cashier'],
    ['accountant', 'accountant'], ['courier', 'courier']]) {
    S[k] = await member(id, role, `${role}-${label}`);
  }
  for (const f of ['shop.settings_sync', 'shop.orders']) await setFlag(id, f, true);
  return S;
}

/** ردیفِ صندوقِ سندِ ردشده (مهاجرتِ ۳۱۵) — همان شکلی که push ِ ردشده می‌سازد */
async function conflictRow(S, { requester, ops, status = 'open' }) {
  const id = newId('cf');
  await query(
    `INSERT INTO sync_v3_conflicts (id, app, account_kind, account_id, kind, table_name, row_id, loser_value, loser_op_id,
                                    loser_device, detail, status, at)
     VALUES ($1,'shop','shop',$2,'perm_denied','sales',$3,$4::jsonb,$5,'dev-idor',$6::jsonb,$7,$8)`,
    [id, S.id, ops[0].row_id, JSON.stringify({ v: 1, path: 'v1', ops }), ops[0].op_id,
      JSON.stringify({ requester, reason: 'perm_denied', perm: 'sales.discount' }), status, now()]);
  return id;
}
const saleOps = (rowId) => [
  op('sales', rowId, 'insert', { invoiceNumber: 7, total: 100, finalTotal: 100, paymentMethod: 'credit', debtorId: 'd-ev', createdAt: Date.now() }),
  op('debtors', 'd-ev', 'update', { balance: 100 }),
];

const shopGets = [
  '/api/shop/conflict-inbox', '/api/shop/conflict-inbox/mine', '/api/shop/conflict-inbox/cf_000000000000000000000000',
  '/api/shop/customer-access?customerId=d-ev', '/api/shop/staff-keys', '/api/shop/settings-sync',
  '/api/shop/ledger-merge/config?v=3', '/api/shop/stock-ledger/config',
];
const shopWrites = [
  ['POST', '/api/shop/conflict-inbox/cf_000000000000000000000000/resubmit', {}],
  ['POST', '/api/shop/conflict-inbox/cf_000000000000000000000000/dismiss', { reason: 'آزمون' }],
  ['POST', '/api/shop/staff-keys/sdk_000000000000000000000000/revoke', {}],
  ['POST', '/api/shop/staff-keys/members/mem_000000000000000000000000/logout-all', {}],
  ['PUT', '/api/shop/settings-sync', { entries: [{ store: 'tohid-policy', key: 'cash_rounding', t: 'l', v: 5 }] }],
];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
               VALUES ($1,'idor-admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'idor-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  A = await shopWith('الف');
  B = await shopWith('ب');
  for (const f of ['shop.count_absorb', 'shop.catalog', 'shop.sync_shadow_file']) await setFlag(A.id, f, true);
  pump = await h.newUser('کاربرِ پمپ', 'pump');
  pump.token = pump.accessToken;
  remember('token:pump', pump.accessToken);
  //  دفترِ سرورِ «الف»: قرض‌دار با دو شماره و نشانی، و یک کالا
  const seeded = await push1(A.owner, [
    op('debtors', 'd-ev', 'insert', { name: 'احمدِ نهان', phone: P1, phone2: P2, deliveryAddress: ADDR, balance: 0 }),
    op('products', 'p-1', 'insert', { name: 'برنج', salePrice: 100 }),
  ]);
  assert.equal(seeded.status, 200, JSON.stringify(seeded.body));
  assert.deepEqual(seeded.body.results.map((x) => x.status), ['applied', 'applied']);
  A.staffKey = await codeStaff(A, 'u-idor-sa-0001', 'شاگردِ کلیددار');
  B.staffKey = await codeStaff(B, 'u-idor-sb-0001', 'شاگردِ کلیددارِ ب');
});
test.after(async () => {
  for (const m of METHODS) console[m] = real[m];
  await h.stop();
});
test.beforeEach(() => resetAll());

// ───────────────────────────────────────────────────────────────── ۳) نوعِ حسابِ نادرست

test('نوعِ حساب: بی توکن، توکنِ دست‌خورده، پمپ، پیک و بلیتِ ویترین روی هر درِ دکان ⇐ ۴۰۱/۴۰۳ بی داده', async () => {
  const courier = A.courier;
  const forged = { token: h.tampered(A.owner.token) };
  for (const path of shopGets) {
    for (const [who, want, what] of [[null, 401, 'بی توکن'], [forged, 401, 'دست‌خورده'], [pump, 401, 'پمپ'], [courier, 403, 'پیک']]) {
      const r = await call(who, 'GET', path);
      assert.equal(r.status, want, `${what} ${path}: ${JSON.stringify(r.body)}`);
      noLeak(r, [A.name, 'احمدِ نهان', P1], `${what} ${path}`);
    }
  }
  for (const [method, path, body] of shopWrites) {
    for (const [who, want, what] of [[null, 401, 'بی توکن'], [pump, 401, 'پمپ'], [courier, 403, 'پیک']]) {
      const r = await call(who, method, path, body);
      assert.equal(r.status, want, `${what} ${method} ${path}: ${JSON.stringify(r.body)}`);
    }
  }
  //  هیچ نوشتنی: تنظیمِ دکان همان
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM shop_setting_keys WHERE shop_id=$1`, [A.id])).n, 0);
});

test('درِ رازِ دستگاه (`/api/auth/staff/device-key`): پمپ، صاحب، مدیر و پیک ⇐ «حسابِ کد نیست»، بی توکن ۴۰۱ — هیچ رازی ساخته نمی‌شود', async () => {
  const before = (await one('SELECT COUNT(*)::int AS n FROM staff_device_keys')).n;
  assert.equal((await call(null, 'POST', '/api/auth/staff/device-key', {})).status, 401);
  assert.equal((await call({ token: h.tampered(A.staffKey.token) }, 'POST', '/api/auth/staff/device-key', {})).status, 401);
  //  پیک: توکنِ پیش از پیک شدن (بی scope) ⇐ همان «حسابِ کد نیست»؛ توکنِ scope دار ⇐ ۴۰۳ (`courierScopeDenied`)
  for (const who of [pump, A.owner, A.manager, A.courier]) {
    const r = await call(who, 'POST', '/api/auth/staff/device-key', { device: { uid: A.staffKey.uid } });
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.deepEqual(r.body, { deviceKey: null, reason: 'not_staff_account' });
  }
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM staff_device_keys')).n, before);
});

test('درِ کارتِ ویترین (`PUT /sync/v1/photos/:id/thumb`): بی توکن ۴۰۱، پمپ و پیک و صندوق‌دار ۴۰۳ — پیش از هر خواندنِ عکس', async () => {
  const body = { data: 'AAAA', of: '0'.repeat(32) };
  assert.equal((await call(null, 'PUT', '/api/sync/v1/photos/p-1/thumb', body)).status, 401);
  for (const who of [pump, A.courier, A.cashier]) {
    const r = await call(who, 'PUT', '/api/sync/v1/photos/p-1/thumb', body);
    assert.equal(r.status, 403, `${who.name || 'پمپ'}: ${JSON.stringify(r.body)}`);
    assert.notEqual(errOf(r), 'photo_not_found', 'پیش از خواندنِ عکس');
  }
});

// ───────────────────────────────────────────────────────────────── ۱) IDOR ِ میانِ دکان‌ها

test('IDOR ِ صندوقِ سندِ ردشده: «ب» با شناسهٔ نشت‌کرده و همسایهٔ آن همان «نیست» ِ شناسهٔ ساختگی را می‌گیرد؛ ردیفِ «الف» دست نمی‌خورد', async () => {
  const cf = await conflictRow(A, { requester: A.staff.userId, ops: saleOps(`s-${uniq()}`) });
  const listed = await call(A.owner, 'GET', '/api/shop/conflict-inbox');
  assert.equal(listed.status, 200);
  assert.ok(listed.body.conflicts.some((c) => c.id === cf), 'شناسه از فهرستِ «الف» نشت می‌کند');
  const fake = newId('cf');
  for (const [method, suffix, body] of [['GET', '', undefined], ['POST', '/resubmit', { note: 'ب' }], ['POST', '/dismiss', { reason: 'کارِ ب' }]]) {
    const miss = await call(B.owner, method, `/api/shop/conflict-inbox/${fake}${suffix}`, body);
    assert.equal(miss.status, 404, JSON.stringify(miss.body));
    for (const id of [cf, neighbour(cf), cf.toUpperCase()]) {
      const r = await call(B.owner, method, `/api/shop/conflict-inbox/${id}${suffix}`, body);
      sameMiss(r, miss, `${method} ${suffix || '/:id'} ${id}`);
      noLeak(r, [A.name, cf, 'احمدِ نهان'], 'IDOR صندوق');
    }
  }
  //  فهرست و «مال من» ِ «ب» چیزی از «الف» ندارند — حتی با `?status=all` و سقفِ بزرگ
  for (const q of ['?status=all&limit=200', '?status=closed']) {
    const r = await call(B.owner, 'GET', `/api/shop/conflict-inbox${q}`);
    assert.equal(r.status, 200);
    assert.ok(!r.body.conflicts.some((c) => c.id === cf), q);
  }
  const row = await one('SELECT status, detail, resolved_by FROM sync_v3_conflicts WHERE id=$1', [cf]);
  assert.equal(row.status, 'open');
  assert.equal(row.resolved_by, '');
  assert.equal(row.detail.resubmitting, undefined, 'هیچ نشانِ «در حالِ ثبت» از «ب»');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE target_id=$1`, [cf])).n, 0);
});

test('IDOR ِ دستگاه‌های کارمند: «ب» با شناسهٔ راز و عضوِ «الف» (نشت از فهرستِ «الف») همان ۴۰۴ ِ ساختگی؛ راز و نشستِ «الف» زنده', async () => {
  const ov = await call(A.owner, 'GET', '/api/shop/staff-keys');
  assert.equal(ov.status, 200, JSON.stringify(ov.body));
  const dev = ov.body.devices.find((d) => d.userId === A.staffKey.userId);
  assert.ok(dev, 'رازِ شاگردِ «الف» در فهرست');
  for (const [path, fakePath] of [
    [`/api/shop/staff-keys/${dev.id}/revoke`, `/api/shop/staff-keys/${newId('sdk')}/revoke`],
    [`/api/shop/staff-keys/${neighbour(dev.id)}/revoke`, `/api/shop/staff-keys/${newId('sdk')}/revoke`],
    [`/api/shop/staff-keys/members/${dev.memberId}/logout-all`, `/api/shop/staff-keys/members/${newId('mem')}/logout-all`],
    [`/api/shop/staff-keys/members/${A.owner.memberId}/logout-all`, `/api/shop/staff-keys/members/${newId('mem')}/logout-all`],
  ]) {
    const miss = await call(B.owner, 'POST', fakePath, {});
    assert.equal(miss.status, 404, JSON.stringify(miss.body));
    const r = await call(B.owner, 'POST', path, {});
    sameMiss(r, { ...miss }, path);
    noLeak(r, [A.name, A.staffKey.userId, dev.memberId], path);
  }
  //  «ب» در فهرستِ خودش هیچ ردی از «الف» نمی‌بیند (نه راز، نه نشانهٔ «شاید همان»)
  const bov = await call(B.owner, 'GET', '/api/shop/staff-keys');
  assert.equal(bov.status, 200);
  noLeak(bov, [A.staffKey.userId, dev.id, dev.memberId, A.name, 'شاگردِ کلیددار"'], 'فهرستِ «ب»');
  //  راز و نشستِ «الف» زنده
  assert.equal((await one('SELECT revoked_at FROM staff_device_keys WHERE id=$1', [dev.id])).revoked_at, null);
  assert.equal((await call(A.staffKey, 'GET', '/api/shop/conflict-inbox/mine')).status, 200);
});

test('IDOR ِ سابقهٔ دسترسی و کارتِ ویترین: شناسهٔ پرونده/کالا و hash ِ «الف» از «ب» ⇐ همان پاسخِ ساختگی، بی نوشتن', async () => {
  await access.record({ shopId: A.id, viewerId: A.owner.userId, role: 'owner', customerRef: 'd-ev', kind: 'detail', door: 'debtor' });
  const mine = await call(A.owner, 'GET', '/api/shop/customer-access?customerId=d-ev');
  assert.ok(mine.body.entries.length >= 1);
  const r = await call(B.owner, 'GET', '/api/shop/customer-access?customerId=d-ev');
  const fake = await call(B.owner, 'GET', '/api/shop/customer-access?customerId=d-nope');
  assert.deepEqual(r.body.entries, []);
  assert.deepEqual({ ...r.body, customerId: '' }, { ...fake.body, customerId: '' });
  noLeak(r, [A.owner.name, A.owner.userId], 'سابقهٔ «ب»');

  //  کارت: عکسِ «الف» با hash ِ واقعی
  const put = await h.api('PUT', '/api/sync/v1/photos/p-1', { token: A.owner.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-idor' },
    body: { data: F.jpeg(800, 600, { seed: 31 }).toString('base64') } });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const card = F.jpeg(320, 240, { seed: 32 }).toString('base64');
  const theirs = await call(B.owner, 'PUT', '/api/sync/v1/photos/p-1/thumb', { data: card, of: put.body.hash });
  const nothing = await call(B.owner, 'PUT', `/api/sync/v1/photos/p-${uniq()}/thumb`, { data: card, of: put.body.hash });
  assert.equal(theirs.status, 404, JSON.stringify(theirs.body));
  assert.deepEqual([theirs.status, errOf(theirs)], [nothing.status, errOf(nothing)]);
  assert.equal(theirs.body.error.message, nothing.body.error.message);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM product_photo_thumbs WHERE account_id=ANY($1::text[])', [[A.id, B.id]])).n, 0);
});

// ───────────────────────────────────────────────────────────────── ۲) سطحِ دسترسی

test('سطح: صندوقِ صاحب فقط صاحب و مدیر؛ شاگرد، انباردار، صندوق‌دار و حسابدار ۴۰۳ روی فهرست، جزئیات، ثبت و کنار گذاشتن — بی نوشتن', async () => {
  const cf = await conflictRow(A, { requester: A.staff.userId, ops: saleOps(`s-${uniq()}`) });
  for (const who of [A.staff, A.storekeeper, A.cashier, A.accountant]) {
    for (const [method, suffix, body] of [['GET', '', undefined], ['GET', `/${cf}`, undefined],
      ['POST', `/${cf}/resubmit`, {}], ['POST', `/${cf}/dismiss`, { reason: 'بستنِ من' }]]) {
      const r = await call(who, method, `/api/shop/conflict-inbox${suffix}`, body);
      assert.equal(r.status, 403, `${who.name} ${method} ${suffix}: ${JSON.stringify(r.body)}`);
    }
    //  «مال من» باز است ولی فقط ردیفِ خودش
    const mine = await call(who, 'GET', '/api/shop/conflict-inbox/mine');
    assert.equal(mine.status, 200);
    assert.equal(mine.body.conflicts.some((c) => c.id === cf), who === A.staff, who.name);
  }
  assert.equal((await one('SELECT status FROM sync_v3_conflicts WHERE id=$1', [cf])).status, 'open');
  assert.equal((await call(A.manager, 'GET', `/api/shop/conflict-inbox/${cf}`)).status, 200);
});

test('سطح: دستگاه‌های کارمند — مدیر می‌بیند ولی نه باطل می‌کند نه بیرون می‌اندازد (صاحب هم نه)؛ صاحب خودش را نه؛ دیگران ۴۰۳', async () => {
  const ov = await call(A.manager, 'GET', '/api/shop/staff-keys');
  assert.equal(ov.status, 200);
  assert.equal(ov.body.canRevoke, false);
  const dev = ov.body.devices.find((d) => d.userId === A.staffKey.userId);
  for (const path of [`/api/shop/staff-keys/${dev.id}/revoke`, `/api/shop/staff-keys/members/${dev.memberId}/logout-all`,
    `/api/shop/staff-keys/members/${A.owner.memberId}/logout-all`]) {
    assert.equal((await call(A.manager, 'POST', path, {})).status, 403, path);
  }
  const self = await call(A.owner, 'POST', `/api/shop/staff-keys/members/${A.owner.memberId}/logout-all`, {});
  assert.deepEqual([self.status, errOf(self)], [400, 'owner_immutable']);
  for (const who of [A.staff, A.storekeeper, A.cashier, A.accountant, A.staffKey]) {
    assert.equal((await call(who, 'GET', '/api/shop/staff-keys')).status, 403, who.name);
  }
  assert.equal((await one('SELECT revoked_at FROM staff_device_keys WHERE id=$1', [dev.id])).revoked_at, null);
});

test('سطح: سابقهٔ دسترسی و همگام‌سازیِ تنظیمات پشتِ لایهٔ کاربر (نه فقط نقش)؛ صاحب هرگز بسته نمی‌شود', async () => {
  const ca = (who) => call(who, 'GET', '/api/shop/customer-access?customerId=d-ev');
  assert.equal((await ca(A.accountant)).status, 200);
  assert.equal((await ca(A.storekeeper)).status, 403);
  const layer = (m, perms) => h.put(`/api/shop/permissions/members/${m.memberId}`, { perms }, tok(A.owner));
  const ok = (r) => assert.equal(r.status, 200, JSON.stringify(r.body));
  ok(await layer(A.accountant, { 'audit.view': { mode: 'deny' } }));
  ok(await layer(A.manager, { 'settings.write': { mode: 'deny' } }));
  try {
    assert.equal((await ca(A.accountant)).status, 403, 'لایهٔ کاربر');
    assert.equal((await ca(A.manager)).status, 200, 'لایهٔ کاربرِ دیگر به مدیر نمی‌رسد');
    const g = await call(A.manager, 'GET', '/api/shop/settings-sync');
    assert.equal(g.body.permissions.write, false);
    const w = await call(A.manager, 'PUT', '/api/shop/settings-sync', { entries: [{ store: 'tohid-policy', key: 'cash_rounding', t: 'l', v: 5 }] });
    assert.deepEqual([w.status, errOf(w)], [403, 'permission_denied']);
    assert.equal((await call(A.staff, 'GET', '/api/shop/settings-sync')).body.permissions.write, false);
    assert.equal((await call(A.staff, 'PUT', '/api/shop/settings-sync', { entries: [{ store: 'tohid-policy', key: 'x', t: 'l', v: 1 }] })).status, 403);
    assert.equal((await call(A.owner, 'GET', '/api/shop/settings-sync')).body.permissions.write, true);
  } finally {
    ok(await layer(A.accountant, {}));
    ok(await layer(A.manager, {}));
  }
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM shop_setting_keys WHERE shop_id=$1`, [A.id])).n, 0);
});

test('سطح: عضوِ معلق با توکنِ هنوز باز (بی باطل شدنِ نشست) ⇐ هر درِ دکان ۴۰۳ و رازِ دستگاه نمی‌گیرد', async () => {
  const legacy = await codeStaff(A, `u-idor-susp-${uniq()}`, 'شاگردِ معلق', { key: false });
  await query(`UPDATE shop_members SET status='suspended' WHERE shop_id=$1 AND user_id IN ($2,$3)`, [A.id, A.manager.userId, legacy.userId]);
  try {
    for (const path of shopGets) {
      const r = await call(A.manager, 'GET', path);
      assert.equal(r.status, 403, `${path}: ${JSON.stringify(r.body)}`);
      noLeak(r, [A.name, P1], path);
    }
    const k = await call(legacy, 'POST', '/api/auth/staff/device-key', {});
    assert.deepEqual([k.status, k.body], [200, { deviceKey: null, reason: 'not_member' }]);
    assert.equal(await deviceKeys.hasLive({ shopId: A.id, userId: legacy.userId, deviceUid: legacy.uid }), false);
  } finally {
    await query(`UPDATE shop_members SET status='active' WHERE shop_id=$1 AND user_id IN ($2,$3)`, [A.id, A.manager.userId, legacy.userId]);
  }
});

test('سطح: رازِ دستگاه از نشستِ باز فقط برای دستگاهِ همان نشست (بدنه نادیده)، یک بار؛ پس از «خروج از همه» نشستِ کهنه ۴۰۱', async () => {
  const legacy = await codeStaff(A, `u-idor-old-${uniq()}`, 'شاگردِ کهنه', { key: false });
  const got = await call(legacy, 'POST', '/api/auth/staff/device-key', { deviceUid: 'u-evil', device: { uid: 'u-evil', key: 'x' } });
  assert.equal(got.status, 201, JSON.stringify(got.body));
  remember('key:enrolled', got.body.deviceKey);
  const rows = await many('SELECT device_uid FROM staff_device_keys WHERE user_id=$1', [legacy.userId]);
  assert.deepEqual(rows.map((r) => r.device_uid), [legacy.uid], 'شناسهٔ بدنه هرگز');
  const again = await call(legacy, 'POST', '/api/auth/staff/device-key', {});
  assert.deepEqual([again.status, again.body], [200, { deviceKey: null, reason: 'exists' }]);

  const out = await call(legacy, 'POST', '/api/auth/logout-all', {});
  assert.equal(out.status, 200, JSON.stringify(out.body));
  assert.equal(out.body.deviceKeys, 1);
  assert.equal((await call(legacy, 'POST', '/api/auth/staff/device-key', {})).status, 401);
  for (const path of ['/api/shop/conflict-inbox/mine', '/api/shop/settings-sync', '/api/shop/stock-ledger/config']) {
    assert.equal((await call(legacy, 'GET', path)).status, 401, path);
  }
  assert.equal((await h.post('/api/auth/refresh', { refreshToken: legacy.refreshToken })).status, 401);
});

test('#1009: پس از «باطل کردنِ این دستگاه» ِ صاحب، توکن و refresh ِ همان دستگاه مرده‌اند، رازِ تازه از نشست نمی‌آید و رازِ کهنه حسابی نمی‌دهد', async () => {
  const s = await codeStaff(A, `u-idor-rev-${uniq()}`, 'شاگردِ باطل');
  const dev = (await call(A.owner, 'GET', '/api/shop/staff-keys')).body.devices.find((d) => d.userId === s.userId);
  const r = await call(A.owner, 'POST', `/api/shop/staff-keys/${dev.id}/revoke`, {});
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.revoked, 1);
  assert.ok(r.body.sessions >= 1);
  noLeak(r, [s.key, deviceKeys.hashKey(s.key), s.uid], 'پاسخِ باطل کردن');
  assert.equal((await call(s, 'GET', '/api/shop/conflict-inbox/mine')).status, 401);
  assert.equal((await call(s, 'POST', '/api/auth/staff/device-key', {})).status, 401);
  assert.equal((await h.post('/api/auth/refresh', { refreshToken: s.refreshToken })).status, 401);
  const standing = (await call(A.owner, 'GET', '/api/shop/staff-code')).body.code;
  for (const code of [s.code, standing]) {
    const back = await h.post('/api/auth/staff', { code, device: { uid: s.uid, key: s.key, keyVersion: 1 } });
    assert.equal(back.status, 403, JSON.stringify(back.body));
    assert.notEqual(back.body.user?.id, s.userId);
    noLeak(back, [s.key, s.userId], 'ورودِ رازِ باطل');
  }
  //  دوباره زدن ⇒ ۲۰۰ و هیچ (دوباره‌زدنی)، و رازِ مرده زنده نمی‌شود
  const again = await call(A.owner, 'POST', `/api/shop/staff-keys/${dev.id}/revoke`, {});
  assert.deepEqual([again.status, again.body.revoked], [200, 0]);
  assert.equal(await deviceKeys.hasLive({ shopId: A.id, userId: s.userId, deviceUid: s.uid }), false);
});

// ───────────────────────────────────────────────────────────────── ۴) ورودیِ بدخواه

test('ورودی: صندوق — دلیل/یادداشت/فاکتور با نوعِ نادرست ۴۰۰ (نه ۵۰۰)؛ `__proto__` آلوده نمی‌کند؛ تکرار با بدنهٔ دیگر ۴۰۹', async () => {
  const cf = await conflictRow(A, { requester: A.staff.userId, ops: saleOps(`s-${uniq()}`) });
  const url = `/api/shop/conflict-inbox/${cf}`;
  for (const [body, code] of [
    [{ reason: ['کنار', 'بگذار'] }, 'bad_note'], [{ reason: { toString: 'x' } }, 'bad_note'], [{ reason: 42 }, 'bad_note'],
    [{ reason: 'خطِ یک\nخطِ دو' }, 'bad_note'], [{ reason: 'ا'.repeat(201) }, 'bad_note'], [{ reason: ' ' }, 'note_required'],
    [{ reason: 'درست', fixedDoc: ['sales/x'] }, 'bad_fixed_doc'], [{ reason: 'درست', fixedDoc: { id: 'x' } }, 'bad_fixed_doc'],
    [{ reason: 'درست', fixedDoc: 'sales/../../etc' }, 'bad_fixed_doc'], [{ reason: 'درست', fixedDoc: 'sales/x‮y' }, 'bad_fixed_doc'],
  ]) {
    const r = await call(A.owner, 'POST', `${url}/dismiss`, body);
    assert.deepEqual([r.status, errOf(r)], [400, code], JSON.stringify(body));
  }
  for (const note of [['x'], { a: 1 }, 7]) {
    const r = await call(A.owner, 'POST', `${url}/resubmit`, { note });
    assert.deepEqual([r.status, errOf(r)], [400, 'bad_note'], JSON.stringify(note));
  }
  //  `__proto__` و `constructor` در بدنهٔ خام
  const raw = await fetch(`${h.base()}/api/shop/conflict-inbox/${cf}/dismiss`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${A.owner.token}` },
    body: '{"__proto__":{"polluted":"yes","reason":"از پروتوتایپ"},"constructor":{"prototype":{"polluted":"yes"}}}',
  });
  assert.equal(raw.status, 400, 'دلیل از پروتوتایپ خوانده نمی‌شود');
  assert.equal(({}).polluted, undefined);
  assert.equal(Object.prototype.polluted, undefined);
  assert.equal((await one('SELECT status FROM sync_v3_conflicts WHERE id=$1', [cf])).status, 'open');

  //  تکرار: همان دلیل ⇒ همان پاسخ، بی سابقهٔ دوم؛ دلیلِ دیگر یا «دوباره ثبت» ⇒ ۴۰۹
  const first = await call(A.owner, 'POST', `${url}/dismiss`, { reason: 'تکراری بود' });
  assert.equal(first.status, 200, JSON.stringify(first.body));
  const same = await call(A.owner, 'POST', `${url}/dismiss`, { reason: 'تکراری بود' });
  assert.equal(same.body.duplicate, true);
  const other = await call(A.owner, 'POST', `${url}/dismiss`, { reason: 'دلیلِ دیگر' });
  assert.deepEqual([other.status, errOf(other)], [409, 'already_resolved']);
  const fix = await call(A.owner, 'POST', `${url}/dismiss`, { reason: 'تکراری بود', fixedDoc: 'sales/s-new' });
  assert.deepEqual([fix.status, errOf(fix)], [409, 'already_resolved'], 'همان دلیل با «اصلاح» کارِ دیگری است');
  const re = await call(A.owner, 'POST', `${url}/resubmit`, {});
  assert.deepEqual([re.status, errOf(re)], [409, 'already_resolved']);
  const row = await one('SELECT status, resolution_note, detail FROM sync_v3_conflicts WHERE id=$1', [cf]);
  assert.deepEqual([row.status, row.resolution_note, row.detail.fixed_doc], ['dismissed', 'تکراری بود', undefined]);
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE target_id=$1`, [cf])).n, 1);
});

test('ورودی: پرس‌وجوی فهرست (status/limit آرایه، منفی، بی‌نهایت، تزریقی) و شناسه‌های یونیکدیِ فریبنده ⇐ هرگز ۵۰۰ و هرگز ردیفِ دیگری', async () => {
  //  Express 5: پارسرِ «ساده» ⇐ کلیدِ تکراری آرایه است (`?limit=1&limit=2`)، `limit[]` کلیدِ دیگری
  for (const q of ['?limit=-5', '?limit=1e309', '?limit=Infinity', '?limit=1&limit=2', '?status=all&status=open', '?status[]=all', "?status=all' OR '1'='1",
    '?status=__proto__', '?limit=99999999999999999999']) {
    const r = await call(A.owner, 'GET', `/api/shop/conflict-inbox${q}`);
    assert.equal(r.status, 200, `${q}: ${JSON.stringify(r.body)}`);
    assert.ok(r.body.conflicts.length <= 200, q);
    assert.ok(r.body.conflicts.every((c) => c.id.startsWith('cf_') || c.id), q);
    const m = await call(A.staff, 'GET', `/api/shop/conflict-inbox/mine${q}`);
    assert.equal(m.status, 200, q);
  }
  const tricky = ['cf_١٢٣', 'cf​000', 'cf_‮000', 'ｃｆ_000', 'cf_000%00', '..%2F..%2Fstaff-keys', '%F0%9F%92%A9', 'a'.repeat(300)];
  for (const id of tricky) {
    const g = await call(A.owner, 'GET', `/api/shop/conflict-inbox/${encodeURI(id)}`);
    assert.ok([400, 404].includes(g.status), `${id}: ${g.status} ${JSON.stringify(g.body)}`);
    const k = await call(A.owner, 'POST', `/api/shop/staff-keys/${encodeURI(id)}/revoke`, {});
    assert.ok([400, 404].includes(k.status), `${id}: ${k.status}`);
    const mm = await call(A.owner, 'POST', `/api/shop/staff-keys/members/${encodeURI(id)}/logout-all`, {});
    assert.ok([400, 404].includes(mm.status), `${id}: ${mm.status}`);
  }
  //  شناسهٔ پرونده: آرایه، شیء، `__proto__`، `erased:` و `ph:` ⇐ فهرستِ خالی و ۲۰۰؛ سقفِ نادرست ⇐ ۴۰۰
  for (const q of ['customerId=d-ev&customerId=d-ev', 'customerId[]=d-ev', 'customerId[a]=d-ev', 'customerId=__proto__', 'customerId=erased:x', 'customerId=ph:abc']) {
    const r = await call(A.owner, 'GET', `/api/shop/customer-access?${q}`);
    assert.equal(r.status, 200, q);
    assert.deepEqual(r.body.entries, [], q);
  }
  //  ممیزیِ ۳۵ (X2): شناسهٔ NUL‌دار «نیافته» نیست، نادرست است ⇒ ۴۰۰ ِ سراسریِ `bad_query` پیش از در (همان شناسه در مسیر
  //  `bad_path` است)؛ هنوز نه ۵۰۰ و نه ردیفِ `d-ev`
  const nul = await call(A.owner, 'GET', '/api/shop/customer-access?customerId=d-ev%00');
  assert.deepEqual([nul.status, nul.body.error?.code, nul.body.entries], [400, 'bad_query', undefined]);
  for (const q of ['limit=-1', 'limit=201', 'limit=abc', 'limit=5&limit=6']) {
    const r = await call(A.owner, 'GET', `/api/shop/customer-access?customerId=d-ev&${q}`);
    assert.equal(r.status, 400, q);
  }
});

test('ورودی: کارتِ ویترین — data/of با نوعِ نادرست ۴۰۰، بدنهٔ بزرگ ۴۱۳ پیش از رمزگشایی، شناسهٔ فریبنده ۴۰۰؛ هیچ نوشتنی', async () => {
  const of = '0'.repeat(32);
  for (const [body, status, code] of [
    [{ data: ['AAAA'], of }, 400, 'thumb_empty'], [{ data: { length: 4 }, of }, 400, 'thumb_empty'], [{ data: '', of }, 400, 'thumb_empty'],
    [{ data: 'A'.repeat(Math.ceil(photos.THUMB_MAX_BYTES * 4 / 3) + 100), of }, 413, 'thumb_too_large'],
    [{ data: 'AAAA', of: [of] }, 400, 'thumb_bad_of'], [{ data: 'AAAA', of: '__proto__' }, 400, 'thumb_bad_of'],
    [{ data: 'AAAA', of: 'G'.repeat(32) }, 400, 'thumb_bad_of'],
  ]) {
    const r = await call(A.owner, 'PUT', '/api/sync/v1/photos/p-1/thumb', body);
    assert.deepEqual([r.status, errOf(r)], [status, code], JSON.stringify(body).slice(0, 80));
  }
  for (const id of ['p‮1', 'p%00', 'p١', 'a'.repeat(200)]) {
    const r = await call(A.owner, 'PUT', `/api/sync/v1/photos/${encodeURIComponent(id)}/thumb`, { data: 'AAAA', of });
    assert.ok([400, 404].includes(r.status), `${id}: ${r.status} ${JSON.stringify(r.body)}`);
  }
  //  بدنهٔ بزرگ‌تر از سقفِ JSON ِ سراسری ⇐ ۴۱۳، نه ۵۰۰
  const huge = await call(A.owner, 'PUT', '/api/sync/v1/photos/p-1/thumb', { data: 'A'.repeat(3 * 1024 * 1024), of });
  assert.equal(huge.status, 413, JSON.stringify(huge.body).slice(0, 200));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM product_photo_thumbs WHERE account_id=$1', [A.id])).n, 0);
});

test('ورودی: همگام‌سازیِ تنظیمات — شکل و نوعِ نادرست ۴۰۰؛ کلیدِ «__proto__» فقط یک نامِ تنظیم است، نه پروتوتایپ', async () => {
  for (const body of [{ entries: { 0: {} } }, { entries: 'x' }, [], { entries: [] },
    { entries: [{ store: 'tohid-policy', key: 'k', t: 'i', v: 1e20 }] }, { entries: [{ store: 'tohid-policy', key: 'k', t: 'i', v: '5' }] },
    { entries: [{ store: 'tohid-policy', key: 'k', t: 'b', v: 'true' }] }, { entries: [{ store: 'tohid-policy', key: 'K-ey', t: 's', v: 'x' }] },
    { entries: [{ store: '__proto__', key: 'k', t: 's', v: 'x' }] }, { entries: [{ store: 'tohid-policy', key: 'k', t: 'set', v: [{}] }] },
    { entries: [['tohid-policy', 'k', 's', 'x']] }]) {
    const r = await call(A.owner, 'PUT', '/api/shop/settings-sync', body);
    assert.deepEqual([r.status, errOf(r)], [400, 'bad_setting'], JSON.stringify(body));
  }
  const r = await call(A.owner, 'PUT', '/api/shop/settings-sync', { entries: [
    { store: 'tohid-policy', key: '__proto__', t: 's', v: 'polluted' }, { store: 'tohid-policy', key: 'constructor', t: 's', v: 'x' }] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(({}).polluted, undefined);
  const g = await call(A.owner, 'GET', '/api/shop/settings-sync');
  assert.deepEqual(g.body.rows.map((x) => x.key).sort(), ['__proto__', 'constructor']);
  assert.equal(Object.getPrototypeOf(g.body.rows[0]), Object.prototype);
  //  «ب» هیچ‌کدام را نمی‌بیند
  assert.deepEqual((await call(B.owner, 'GET', '/api/shop/settings-sync')).body.rows, []);
  await query('DELETE FROM shop_setting_keys WHERE shop_id=$1', [A.id]);
  await query('DELETE FROM shop_setting_keys_log WHERE shop_id=$1', [A.id]);
});

test('ورودی: پرچم‌های پیکربندی (`ledger-merge/config?v=3`، `stock-ledger/config`) فقط از دکانِ توکن؛ v ِ آرایه/منفی/ناجور ⇐ شکلِ کهنه', async () => {
  const lm = async (who, q) => (await call(who, 'GET', `/api/shop/ledger-merge/config${q}`)).body;
  assert.equal((await lm(A.owner, '?v=3')).shadowFile, true);
  assert.equal((await lm(B.owner, '?v=3')).shadowFile, false, 'پرچمِ «الف» به «ب» نمی‌رسد');
  for (const q of ['?v=3&v=3', '?v[]=3', '?v=-1', '?v=', '?v=__proto__', '?v=%E2%80%AE3']) {
    const body = await lm(A.owner, q);
    assert.deepEqual(Object.keys(body), ['enabled'], `${q}: ${JSON.stringify(body)}`);
  }
  assert.deepEqual(Object.keys(await lm(A.staff, '?v=99999999999999999999')).sort(), ['base', 'enabled', 'shadowFile', 'v']);
  const sl = async (who) => (await call(who, 'GET', '/api/shop/stock-ledger/config')).body;
  assert.equal((await sl(A.cashier)).countAbsorb, true);
  assert.deepEqual(await sl(B.owner), { enabled: false }, '«ب» بی کلیدِ countAbsorb، بیت‌به‌بیت همان');
});

test('ف۳ — push ِ v1: `purchaseUnits` ِ نادرست با نامِ دیگرِ جدول یا نوع (Products، pro_ducts، op_type، opType) هم رد می‌شود؛ ردیف دست نمی‌خورد', async () => {
  const bad = { purchaseUnits: 'نه فهرست' };
  const ops = [
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'Products', row_id: 'p-1', type: 'update', fields: bad },
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'pro_ducts', row_id: 'p-1', type: 'update', fields: { purchaseUnits: [{ name: '', factorMilli: 1 }] } },
    { op_id: `op-${uniq()}`, ts: Date.now(), table_name: 'PRODUCTS', row_id: 'p-1', op_type: 'update', fields: { purchaseUnits: [{ name: 'کارتن', factorMilli: -5 }] } },
    { opId: `op-${uniq()}`, ts: Date.now(), tableName: 'products', rowId: 'p-1', opType: 'update', fields: { purchaseUnits: { __proto__: null, 0: 1 } } },
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'p-1', type: 'update', fields: { purchaseUnits: [{ name: 'کارتن', factorMilli: 1e12 }] } },
  ];
  const r = await push1(A.owner, ops);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body.results.map((x) => [x.status, x.reason]), ops.map(() => ['rejected', 'bad_purchase_units']));
  const row = await one(`SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='products' AND row_id='p-1'`, [A.id]);
  assert.equal(row.data.purchaseUnits, undefined, 'هیچ شکلِ نادرستی ننشست');
  //  شکلِ درست با همان نامِ دیگر همچنان می‌نشیند (بی پسرفت)
  const good = await push1(A.owner, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'Products', row_id: 'p-1', op_type: 'update',
    fields: { purchaseUnits: [{ name: 'کارتن', factorMilli: 12000 }] } }]);
  assert.deepEqual(good.body.results.map((x) => x.status), ['applied']);
  const after = await one(`SELECT data FROM sync_rows WHERE account_id=$1 AND table_name='products' AND row_id='p-1'`, [A.id]);
  assert.deepEqual(after.data.purchaseUnits, [{ name: 'کارتن', factorMilli: 12000 }]);
});

// ───────────────────────────────────────────────────────────────── ۶) نشتِ داده

test('ف۱ — «مال من» ِ صندوق: `settle.base` ِ قرض‌دار برای نقشِ بی `customers.contact` بی شماره و نشانی (همان pull)؛ مدیر همان پیشین', async () => {
  const sk = await conflictRow(A, { requester: A.storekeeper.userId, ops: saleOps(`s-${uniq()}`) });
  const mg = await conflictRow(A, { requester: A.manager.userId, ops: saleOps(`s-${uniq()}`) });
  for (const cf of [sk, mg]) {
    const d = await call(A.owner, 'POST', `/api/shop/conflict-inbox/${cf}/dismiss`, { reason: 'کنار رفت' });
    assert.equal(d.status, 200, JSON.stringify(d.body));
  }
  const seen = await call(A.storekeeper, 'GET', '/api/shop/conflict-inbox/mine');
  assert.equal(seen.status, 200);
  const row = seen.body.conflicts.find((c) => c.id === sk);
  assert.ok(row && row.settle, JSON.stringify(seen.body));
  const base = row.settle.base['debtors/d-ev'];
  assert.ok(base, 'مقدارِ امروزِ سرور برای هم‌سو شدن می‌آید');
  assert.equal(base.name, 'احمدِ نهان');
  for (const k of ['phone', 'phone2', 'deliveryAddress']) assert.equal(k in base, false, `انباردار ${k} را دید`);
  noLeak(seen, [P1, P2, ADDR, P1.slice(1), P1.slice(-7)], '«مال من» ِ انباردار');
  //  pull ِ همان انباردار هم همین را می‌دهد (شکلِ یکسان)
  //  مدیر (`customers.contact` دارد) همان پیشین را می‌گیرد
  const m = (await call(A.manager, 'GET', '/api/shop/conflict-inbox/mine')).body.conflicts.find((c) => c.id === mg);
  assert.equal(m.settle.base['debtors/d-ev'].phone, P1);
  //  و صاحب لایهٔ کاربرِ همین مدیر را می‌بندد ⇒ مدیر هم نه
  const layer = await h.put(`/api/shop/permissions/members/${A.manager.memberId}`, { perms: { 'customers.contact': { mode: 'deny' } } }, tok(A.owner));
  assert.equal(layer.status, 200, JSON.stringify(layer.body));
  try {
    const m2 = await call(A.manager, 'GET', '/api/shop/conflict-inbox/mine');
    noLeak(m2, [P1, P2, ADDR], '«مال من» ِ مدیرِ بسته');
  } finally {
    assert.equal((await h.put(`/api/shop/permissions/members/${A.manager.memberId}`, { perms: {} }, tok(A.owner))).status, 200);
  }
});

test('ف۲ — سابقهٔ دسترسی (CRM-15): شمارهٔ دوم یا `customerId` ِ ساختگی دیدنِ سفارش‌های مشتری را از پرونده‌اش پنهان نمی‌کند؛ پرسشِ درست یک ردیف', async () => {
  await query('DELETE FROM customer_access_log WHERE shop_id=$1', [A.id]);
  const ask = (q) => call(A.manager, 'GET', `/api/shop/orders/customer?${q}`);
  //  پیش از رفع: اولی هیچ ردیفی نمی‌ساخت (شمارهٔ نخستِ نادرست) و دومی فقط «zz-not-a-file» ⇐ پروندهٔ «d-ev» خالی
  for (const q of [`phone=x&phone=${P1}`, `phone=${P2}&customerId=zz-not-a-file`, `phone=%20&phone=${P2}`]) {
    const r = await ask(q);
    assert.equal(r.status, 200, `${q}: ${JSON.stringify(r.body)}`);
  }
  //  `phone[]` در Express 5 کلیدِ دیگری است ⇐ هیچ شماره‌ای خوانده نشد و هیچ ردیفی لازم نیست
  assert.deepEqual((await ask(`phone[]=${P1}`)).body.items, []);
  await access.settle();
  const seen = await call(A.owner, 'GET', '/api/shop/customer-access?customerId=d-ev');
  const orders = seen.body.entries.filter((e) => e.door === 'orders' && e.viewerId === A.manager.userId);
  assert.equal(orders.reduce((n, e) => n + e.count, 0), 3, `هر سه پرسش در پروندهٔ همان مشتری: ${JSON.stringify(seen.body.entries)}`);
  //  پرسشِ درستِ برنامه (شناسهٔ پرونده + شمارهٔ همان پرونده) همان یک ردیفِ پیشین است
  await query('DELETE FROM customer_access_log WHERE shop_id=$1', [A.id]);
  assert.equal((await ask(`phone=${P1}&customerId=d-ev`)).status, 200);
  await access.settle();
  const rows = await many('SELECT customer_ref, door FROM customer_access_log WHERE shop_id=$1', [A.id]);
  assert.deepEqual(rows.map((r) => r.customer_ref), ['d-ev']);
  //  پرونده‌ای که شماره‌اش در دفترِ سرور نیست (فقط روی دستگاه) ⇒ شناسه + کلیدِ شماره، و فهرستِ پرونده یک ردیف نشان می‌دهد
  await query('DELETE FROM customer_access_log WHERE shop_id=$1', [A.id]);
  assert.equal((await ask(`phone=0793319999&customerId=d-device-only`)).status, 200);
  await access.settle();
  const refs = (await many('SELECT customer_ref FROM customer_access_log WHERE shop_id=$1 ORDER BY customer_ref', [A.id])).map((r) => r.customer_ref);
  assert.deepEqual(refs, ['d-device-only', access.phoneRef('0793319999')].sort());
  assert.equal((await call(A.owner, 'GET', '/api/shop/customer-access?customerId=d-device-only')).body.entries.length, 1);
  //  هیچ شمارهٔ خامی در سابقه
  const all = JSON.stringify(await many('SELECT * FROM customer_access_log WHERE shop_id=$1', [A.id]));
  for (const p of ['0793319999', '793319999', P1, P2]) assert.ok(!all.includes(p), p);
});

test('نشت: پاسخ‌های دستگاه‌های کارمند و رازِ دستگاه هرگز راز، HMAC یا شناسهٔ کاملِ دستگاه ندارند', async () => {
  const ov = await call(A.owner, 'GET', '/api/shop/staff-keys');
  noLeak(ov, [A.staffKey.key, deviceKeys.hashKey(A.staffKey.key), A.staffKey.uid, 'key_hash', 'keyHash'], 'فهرستِ دستگاه‌ها');
  for (const d of ov.body.devices) assert.deepEqual(Object.keys(d).sort(), ['createdAt', 'device', 'id', 'label', 'lastUsedAt', 'memberId', 'platform', 'userId']);
});

// ───────────────────────────────────────────────────────────────── چتِ ویترین (#1004، deleteUntil)

async function liveChatStore(S) {
  await setFlag(S.id, 'shop.storefront', true);
  await setFlag(S.id, 'shop.chat', true);
  const slug = `idor-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${S.label}`, contactPhone: '0700000001' }, tok(S.owner));
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const pub = await h.put('/api/shop/storefront/products/p-1', { published: true }, tok(S.owner));
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  return slug;
}
async function ticketFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  remember(`ticket:${phone}`, c.body.ticket);
  return c.body.ticket;
}

test('چتِ ویترین: مشتریِ دیگر به گفت‌وگو و پیامِ مشتریِ اول نمی‌رسد؛ مهلتِ «حذف برای همه» از ساعتِ سرور؛ توکنِ دکان روی درِ مشتری و بلیتِ مشتری روی درِ دکان ۴۰۱', async () => {
  const config = require('../src/config');
  const slug = await liveChatStore(A);
  const base = `/api/store/${slug}/chat`;
  const t1 = await ticketFor(slug, '0794410001');
  const t2 = await ticketFor(slug, '0794410002');
  const as = (t) => ({ headers: { 'X-Store-Ticket': t } });
  const c1 = (await h.post(`${base}/conversations`, {}, as(t1))).body.conversation.id;
  const c2 = (await h.post(`${base}/conversations`, {}, as(t2))).body.conversation.id;
  const sent = await h.post(`${base}/conversations/${c1}/messages`, { clientId: `client-${uniq()}-0000`, body: 'پیامِ یک' }, as(t1));
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  const m1 = sent.body.message;
  assert.equal(m1.deleteUntil, m1.createdAt + config.shopChat.deleteWindowMs);

  //  مشتریِ دوم: گفت‌وگوی اول و پیامش (از راهِ گفت‌وگوی خودش هم) هیچ؛ پیامِ اول دست نمی‌خورد
  for (const [method, path] of [['GET', `${base}/conversations/${c1}`], ['GET', `${base}/conversations/${c1}/messages?after=0`],
    ['DELETE', `${base}/conversations/${c1}/messages/${m1.id}?for=all`], ['DELETE', `${base}/conversations/${c2}/messages/${m1.id}?for=all`],
    ['PATCH', `${base}/conversations/${c2}/messages/${m1.id}`], ['PUT', `${base}/conversations/${c2}/pins/${m1.id}`]]) {
    const r = await h.api(method, path, { ...as(t2), body: method === 'PATCH' ? { body: 'دست‌کاری' } : method === 'PUT' ? {} : null });
    assert.ok([403, 404].includes(r.status), `${method} ${path}: ${r.status} ${JSON.stringify(r.body)}`);
    noLeak(r, ['پیامِ یک', '0794410001'], path);
  }
  const still = await one('SELECT body, deleted_at FROM shop_chat_messages WHERE id=$1', [m1.id]);
  assert.deepEqual([still.body, still.deleted_at], ['پیامِ یک', null]);

  //  توکنِ دکان روی درِ مشتری ⇒ ۴۰۱ (بلیت لازم)؛ بلیتِ مشتری روی درِ دکان ⇒ ۴۰۱
  const shopOnCustomer = await h.get(`${base}/conversations`, tok(A.owner));
  assert.equal(shopOnCustomer.status, 401, JSON.stringify(shopOnCustomer.body));
  for (const path of ['/api/shop/chat/conversations', '/api/shop/conflict-inbox/mine', '/api/shop/staff-keys']) {
    assert.equal((await h.get(path, { token: t1 })).status, 401, path);
    assert.equal((await h.get(path, as(t1))).status, 401, path);
  }
  //  بلیتِ دست‌خورده ⇒ ۴۰۱
  assert.equal((await h.get(`${base}/conversations`, as(h.tampered(t1)))).status, 401);

  //  پیامِ دکان را مشتری برای همه پاک نمی‌کند؛ `for` ِ آرایه ⇒ ۴۰۰
  const s1 = await h.post(`/api/shop/chat/conversations/${c1}/messages`, { clientId: `client-${uniq()}-0000`, body: 'پیامِ دکان' }, tok(A.owner));
  assert.equal(s1.status, 201, JSON.stringify(s1.body));
  const no = await h.del(`${base}/conversations/${c1}/messages/${s1.body.message.id}?for=all`, as(t1));
  assert.deepEqual([no.status, errOf(no)], [403, 'delete_not_allowed']);
  const arr = await h.del(`${base}/conversations/${c1}/messages/${m1.id}?for[]=all`, as(t1));
  assert.deepEqual([arr.status, errOf(arr)], [400, 'bad_scope']);
  //  همان clientId با متنِ دیگر ⇒ ۴۰۹، نه پیامِ تازه و نه بازنویسی
  const reuse = await h.post(`${base}/conversations/${c1}/messages`, { clientId: m1.clientId, body: 'متنِ دیگر' }, as(t1));
  assert.deepEqual([reuse.status, errOf(reuse)], [409, 'client_id_reused']);

  //  مهلت از ساعتِ سرور: پیامِ کهنه ⇒ ۴۰۳ هرچه برنامه بگوید
  await query('UPDATE shop_chat_messages SET created_at=$2 WHERE id=$1', [m1.id, now() - config.shopChat.deleteWindowMs - 60_000]);
  const late = await h.del(`${base}/conversations/${c1}/messages/${m1.id}?for=all`, as(t1));
  assert.deepEqual([late.status, errOf(late)], [403, 'delete_window_passed']);
  assert.equal((await one('SELECT deleted_at FROM shop_chat_messages WHERE id=$1', [m1.id])).deleted_at, null);
});

// ───────────────────────────────────────────────────────────────── نشت در لاگ و سابقه (آخرِ پرونده)

test('نشت: هیچ توکن، refresh، رازِ دستگاه، HMAC ِ آن، کدِ شاگرد، بلیت یا شمارهٔ مشتری در لاگِ سرور و سابقهٔ عملیات نیامد', async () => {
  assert.ok(secrets.size >= 20, `رازهای گردآمده: ${secrets.size}`);
  const phones = [P1, P2, '0794410001', '0794410002', '0793319999'];
  for (const p of phones) secrets.set(p, `phone:${p}`);
  const logText = lines.join('\n');
  for (const [s, label] of secrets) assert.ok(!logText.includes(s), `${label} در لاگِ سرور آمد`);
  const audits = JSON.stringify(await many('SELECT action, detail, target_id FROM audit_logs WHERE shop_id = ANY($1::text[])', [[A.id, B.id]]));
  for (const [s, label] of secrets) {
    if (label.startsWith('phone:')) continue;   //  شمارهٔ مشتری در سابقهٔ سفارش/گفت‌وگو جای خودش را دارد؛ این‌جا فقط رازها
    assert.ok(!audits.includes(s), `${label} در سابقهٔ عملیات آمد`);
  }
});
