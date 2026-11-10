'use strict';
/**
 * CRM-15 — «سابقهٔ دسترسی»: چه کسی پروندهٔ مشتری را دید (`lib/customer-access.js`، مهاجرتِ ۳۸۰).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) هر خواندنِ یک مشتری از درِ سرور ثبت می‌شود: پروندهٔ قرض‌دار (`GET /api/debtors/:id`؛ `phone` با شمارهٔ
 *      کامل، `detail` بی آن) و سفارش‌های یک مشتری (`GET /api/shop/orders/customer`؛ با `customerId` همان پرونده،
 *      بی آن کلیدِ یک‌طرفهٔ `ph:`)؛ نوعِ `export` هم پذیرفته است.
 *   ۲) یکی کردنِ تکرار: همان بیننده + مشتری + نوع در ۱۰ دقیقه ⇐ یک ردیف با شمار؛ پس از آن ردیفِ تازه.
 *   ۳) نوشتنِ ناموفق خواندن را نمی‌اندازد (پاسخ همان) و فقط شمرده می‌شود.
 *   ۴) هیچ دادهٔ شخصیِ مشتری (نام، شماره، نشانی) در ردیف‌ها نیست.
 *   ۵) جداسازیِ دکان؛ ۶) مجوز: `audit.view` (صاحب، مدیر)، شاگرد و صندوق‌دار ۴۰۳.
 *   ۷) SEC-10: حذفِ دادهٔ مشتری ردیف‌های همان مشتری را ناشناس می‌کند (`erased:`)، مشتریِ دیگر دست نمی‌خورد.
 *   ۸) فقط‌افزودنی (تریگر)، نگه‌داری، و rollback ِ مهاجرت.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const access = require('../src/lib/customer-access');
const { resetAll } = require('../src/middleware/ratelimit');

const PHONE = '0799123456';
const PHONE2 = '+93 700 111 222';
const NAME = 'رحیمِ پرونده‌دار';
const ADDR = { area: 'کارتهٔ چهار', landmark: 'مسجدِ جامع' };
const SECRETS = [PHONE, '799123456', '700111222', NAME, 'کارتهٔ چهار', 'مسجدِ جامع'];

let admin = '';
const T = {};
const B = {};

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id };
}
async function shop(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...o, token: o.accessToken, userId: o.user.id, shopId: made.body.shop.id };
}
async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const tok = (who) => ({ token: who.token });
const rowsOf = (shopId) => many('SELECT * FROM customer_access_log WHERE shop_id=$1 ORDER BY first_at, id', [shopId]);
async function debtor(owner, id, data) {
  const r = await h.post('/api/debtors', { id, data }, tok(owner));
  assert.equal(r.status, 201, JSON.stringify(r.body));
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'access-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'access-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
  Object.assign(T, { owner: await shop('الف') });
  for (const role of ['manager', 'staff', 'cashier', 'storekeeper', 'accountant']) T[role] = await member(T.owner.shopId, role, `کارمندِ ${role}`);
  Object.assign(B, { owner: await shop('ب') });
  await setFlag(T.owner.shopId, 'shop.orders', true);
  await debtor(T.owner, 'd-file', { name: NAME, phone: PHONE, phone2: PHONE2, deliveryAddress: ADDR, notes: 'بدهیِ کهنه' });
  await debtor(T.owner, 'd-bare', { name: 'بی شماره' });
});
test.after(async () => { await access.settle(); await h.stop(); });
test.beforeEach(() => resetAll());

test('هر نوعِ خواندن ثبت می‌شود: پرونده با شماره (phone)، بی شماره (detail)، سفارش‌های مشتری (detail) و export', async () => {
  await query('DELETE FROM customer_access_log WHERE shop_id=$1', [T.owner.shopId]);
  const full = await h.get('/api/debtors/d-file', tok(T.owner));
  assert.equal(full.status, 200);
  assert.equal(full.body.phone, PHONE, 'پاسخ همان پیشین');
  //  انباردار `customers.contact` ندارد ⇒ شمارهٔ پوشیده ⇒ فقط «پرونده»
  const masked = await h.get('/api/debtors/d-file', tok(T.storekeeper));
  assert.equal(masked.status, 200);
  assert.notEqual(masked.body.phone, PHONE);
  //  پروندهٔ بی شماره و نشانی ⇒ «پرونده»، حتی برای صاحب
  assert.equal((await h.get('/api/debtors/d-bare', tok(T.owner))).status, 200);
  //  سفارش‌های یک مشتری (CRM-07): برنامه شناسهٔ پرونده را می‌فرستد؛ API ِ مستقیم بی آن ⇒ کلیدِ یک‌طرفه
  const ord = await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent(PHONE)}&customerId=d-file`, tok(T.manager));
  assert.equal(ord.status, 200, JSON.stringify(ord.body));
  const bare = await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent(PHONE)}`, tok(T.manager));
  assert.equal(bare.status, 200);
  //  فهرست (نه یک مشتری) ثبت نمی‌شود
  assert.equal((await h.get('/api/debtors?limit=50', tok(T.owner))).status, 200);
  await access.settle();

  const rows = await rowsOf(T.owner.shopId);
  const pick = (who, door, ref) => rows.filter((r) => r.viewer_id === who.userId && r.door === door && r.customer_ref === ref);
  assert.deepEqual(pick(T.owner, 'debtor', 'd-file').map((r) => r.kind), ['phone']);
  assert.deepEqual(pick(T.storekeeper, 'debtor', 'd-file').map((r) => r.kind), ['detail']);
  assert.deepEqual(pick(T.owner, 'debtor', 'd-bare').map((r) => r.kind), ['detail']);
  assert.deepEqual(pick(T.manager, 'orders', 'd-file').map((r) => r.kind), ['detail']);
  assert.deepEqual(pick(T.manager, 'orders', access.phoneRef(PHONE)).map((r) => r.kind), ['detail']);
  assert.equal(rows.length, 5, JSON.stringify(rows));
  const o = pick(T.owner, 'debtor', 'd-file')[0];
  assert.equal(o.role, 'owner');
  assert.match(o.device_id, /^dev-/, 'دستگاهِ نشست');
  assert.equal(Number(o.hits), 1);
  assert.equal(pick(T.storekeeper, 'debtor', 'd-file')[0].role, 'storekeeper');
  //  نوعِ «خروجی» (برنامه در دفترِ دستگاه می‌نویسد؛ لایهٔ سرور هم می‌پذیرد) و نوعِ ناشناخته رد
  const ex = await access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, role: 'owner', customerRef: 'd-file', kind: 'export', door: 'debtor' });
  assert.equal(ex.hits, 1);
  await assert.rejects(access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, customerRef: 'd-file', kind: 'peek', door: 'debtor' }));
  //  هر دو شمارهٔ پرونده (اول و دوم) به همان پرونده می‌رسند (خواندنِ با شمارهٔ دوم)
  const seen = await h.get('/api/shop/customer-access?customerId=d-file', tok(T.owner));
  assert.equal(seen.status, 200, JSON.stringify(seen.body));
  const kinds = seen.body.entries.map((e) => `${e.door}/${e.kind}/${e.role}`).sort();
  assert.deepEqual(kinds, ['debtor/detail/storekeeper', 'debtor/export/owner', 'debtor/phone/owner', 'orders/detail/manager', 'orders/detail/manager']);
  const e = seen.body.entries.find((x) => x.kind === 'phone');
  assert.equal(e.viewerId, T.owner.userId);
  assert.equal(e.viewerName, 'صاحبِ الف');
  assert.equal(e.count, 1);
  assert.ok(e.firstAt > 0 && e.lastAt >= e.firstAt);
  assert.equal(seen.body.retentionDays, 0);
});

test('تکرار در ۱۰ دقیقه یک ردیف با شمار است؛ پس از آن ردیفِ تازه؛ نوع یا بینندهٔ دیگر جدا', async () => {
  const base = { shopId: T.owner.shopId, viewerId: T.manager.userId, role: 'manager', customerRef: 'd-dedupe', door: 'debtor' };
  const t0 = 1_800_000_000_000;
  const a = await access.record({ ...base, kind: 'detail', at: t0 });
  const b = await access.record({ ...base, kind: 'detail', at: t0 + 60_000 });
  const c = await access.record({ ...base, kind: 'detail', at: t0 + access.WINDOW_MS - 1 });
  assert.equal(b.id, a.id);
  assert.equal(c.id, a.id);
  assert.equal(c.hits, 3);
  const d = await access.record({ ...base, kind: 'detail', at: t0 + access.WINDOW_MS + 1 });
  assert.notEqual(d.id, a.id, 'پس از ۱۰ دقیقه از نخستین دیدن ⇒ ردیفِ تازه');
  const p = await access.record({ ...base, kind: 'phone', at: t0 + 1000 });
  assert.notEqual(p.id, a.id, 'نوعِ دیگر');
  const q = await access.record({ ...base, viewerId: T.owner.userId, role: 'owner', kind: 'detail', at: t0 + 1000 });
  assert.notEqual(q.id, a.id, 'بینندهٔ دیگر');
  const row = await one('SELECT * FROM customer_access_log WHERE id=$1', [a.id]);
  assert.equal(Number(row.hits), 3);
  assert.equal(Number(row.first_at), t0);
  assert.equal(Number(row.last_at), t0 + access.WINDOW_MS - 1);
  //  از درِ واقعی: دو بار پشتِ هم ⇒ یک ردیف با شمارِ ۲ (دو درخواستِ هم‌زمان هم یک ردیف — قفلِ همان کلید)
  await query('DELETE FROM customer_access_log WHERE shop_id=$1 AND customer_ref=$2', [T.owner.shopId, 'd-file']);
  await Promise.all([h.get('/api/debtors/d-file', tok(T.cashier)), h.get('/api/debtors/d-file', tok(T.cashier)), h.get('/api/debtors/d-file', tok(T.cashier))]);
  await access.settle();
  const mine = await many('SELECT hits FROM customer_access_log WHERE shop_id=$1 AND customer_ref=$2 AND viewer_id=$3', [T.owner.shopId, 'd-file', T.cashier.userId]);
  assert.deepEqual(mine.map((r) => Number(r.hits)), [3]);
});

test('نوشتنِ ناموفقِ سابقه خواندن را نمی‌اندازد: پاسخ همان، خطا فقط شمرده می‌شود', async () => {
  const before = access.stats().failed;
  const real = access.record;
  access.record = async () => { throw Object.assign(new Error('disk full'), { code: '53100' }); };
  try {
    const r = await h.get('/api/debtors/d-file', tok(T.owner));
    assert.equal(r.status, 200);
    assert.equal(r.body.phone, PHONE);
    const o = await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent(PHONE)}&customerId=d-file`, tok(T.owner));
    assert.equal(o.status, 200);
    await access.settle();
  } finally { access.record = real; }
  assert.equal(access.stats().failed, before + 2);
  assert.equal(access.stats().lastError, '53100');
  //  خطای واقعیِ پایگاه داده (جدول نیست) هم همان
  await query('ALTER TABLE customer_access_log RENAME TO customer_access_log_off');
  try {
    const r = await h.get('/api/debtors/d-file', tok(T.manager));
    assert.equal(r.status, 200);
    assert.equal(r.body.name, NAME);
    await access.settle();
  } finally { await query('ALTER TABLE customer_access_log_off RENAME TO customer_access_log'); }
  assert.equal(access.stats().failed, before + 3);
  //  و در «سلامت» ِ پنلِ مرکزی دیده می‌شود
  const health = await require('../src/lib/ops-overview').health();
  assert.equal(health.customerAccessLog.failed, before + 3);
});

test('هیچ دادهٔ شخصیِ مشتری در ردیف‌ها نیست (نام، شماره، نشانی، یادداشت)', async () => {
  await h.get('/api/debtors/d-file', tok(T.owner));
  await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent(PHONE)}`, tok(T.owner));
  await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent(PHONE2)}`, tok(T.owner));
  await access.settle();
  const dump = JSON.stringify(await rowsOf(T.owner.shopId));
  for (const s of [...SECRETS, 'بدهیِ کهنه']) assert.ok(!dump.includes(s), `«${s}» در سابقه آمد`);
  for (const r of await rowsOf(T.owner.shopId)) assert.match(r.customer_ref, /^(d-[a-z-]+|ph:[0-9a-f]{32}|erased:.+)$/);
  //  همان شماره با شکلِ دیگر همان کلید است (‎+93…‎ و 07…)
  assert.equal(access.phoneRef('+93799123456'), access.phoneRef(PHONE));
  assert.notEqual(access.phoneRef(PHONE2), access.phoneRef(PHONE));
  assert.equal(access.phoneRef('نه شماره'), null);
});

test('جداسازیِ دکان: «ب» با شناسهٔ پروندهٔ «الف» هیچ نمی‌بیند؛ نوشتن‌های «ب» در دکانِ خودش', async () => {
  await h.get('/api/debtors/d-file', tok(T.owner));
  await access.settle();
  const other = await h.get('/api/shop/customer-access?customerId=d-file', tok(B.owner));
  assert.equal(other.status, 200);
  assert.deepEqual(other.body.entries, []);
  //  «ب» نمی‌تواند پروندهٔ «الف» را بخواند (۴۰۴) و ردیفی هم نمی‌سازد
  const before = (await rowsOf(T.owner.shopId)).length;
  assert.equal((await h.get('/api/debtors/d-file', tok(B.owner))).status, 404);
  await debtor(B.owner, 'd-file', { name: 'همنامِ ب' });
  assert.equal((await h.get('/api/debtors/d-file', tok(B.owner))).status, 200);
  await access.settle();
  assert.equal((await rowsOf(T.owner.shopId)).length, before);
  const mineB = await rowsOf(B.owner.shopId);
  assert.equal(mineB.length, 1);
  assert.equal(mineB[0].viewer_id, B.owner.userId);
  const own = await h.get('/api/shop/customer-access?customerId=d-file', tok(B.owner));
  assert.deepEqual(own.body.entries.map((e) => e.viewerId), [B.owner.userId]);
  //  `X-Shop-Id` ِ «الف» در درخواستِ «ب» دکان را عوض نمی‌کند
  const spoof = await h.get('/api/shop/customer-access?customerId=d-file', { token: B.owner.token, headers: { 'X-Shop-Id': T.owner.shopId } });
  assert.ok(spoof.status === 200 || spoof.status === 403, String(spoof.status));
  if (spoof.status === 200) assert.ok(spoof.body.entries.every((e) => e.viewerId === B.owner.userId));
});

test('مجوز: فقط audit.view (صاحب، مدیر، حسابدار)؛ شاگرد، صندوق‌دار و انباردار ۴۰۳؛ بی توکن ۴۰۱؛ شناسهٔ نادرست ⇒ خالی', async () => {
  for (const who of ['owner', 'manager', 'accountant']) {
    const r = await h.get('/api/shop/customer-access?customerId=d-file', tok(T[who]));
    assert.equal(r.status, 200, `${who}: ${JSON.stringify(r.body)}`);
  }
  for (const who of ['staff', 'cashier', 'storekeeper']) {
    const r = await h.get('/api/shop/customer-access?customerId=d-file', tok(T[who]));
    assert.equal(r.status, 403, who);
    assert.equal(r.body.error.code, 'permission_denied');
  }
  assert.equal((await h.get('/api/shop/customer-access?customerId=d-file')).status, 401);
  //  لایهٔ صاحب «سابقه» را برای مدیر می‌بندد ⇒ ۴۰۳
  const close = await h.put(`/api/shop/permissions/members/${T.manager.userId}`, { perms: { 'audit.view': { mode: 'deny' } } }, tok(T.owner));
  assert.equal(close.status, 200, JSON.stringify(close.body));
  assert.equal((await h.get('/api/shop/customer-access?customerId=d-file', tok(T.manager))).status, 403);
  for (const bad of ['', 'erased:era1', 'ph:abc', '../x', 'a'.repeat(81)]) {
    const r = await h.get(`/api/shop/customer-access?customerId=${encodeURIComponent(bad)}`, tok(T.owner));
    assert.equal(r.status, 200);
    assert.deepEqual(r.body.entries, [], bad);
  }
});

test('SEC-10: حذفِ دادهٔ مشتری ردیف‌های همان مشتری را ناشناس می‌کند؛ مشتریِ دیگر و دکانِ دیگر دست نمی‌خورند', async () => {
  const erasure = require('../src/lib/customer-erasure');
  await debtor(T.owner, 'd-keep', { name: 'دیگری', phone: '0700999888' });
  await h.get('/api/debtors/d-file', tok(T.manager));
  await h.get('/api/debtors/d-keep', tok(T.manager));
  await h.get(`/api/shop/orders/customer?phone=${encodeURIComponent('+93799123456')}`, tok(T.manager));
  await access.settle();
  //  پرونده، شماره، و شمارهٔ دومِ همان پرونده (خواندنی با شمارهٔ دوم در آزمونِ پیشین)
  const mine = async () => (await rowsOf(T.owner.shopId))
    .filter((r) => ['d-file', access.phoneRef(PHONE), access.phoneRef(PHONE2)].includes(r.customer_ref));
  const before = await mine();
  assert.ok(before.length >= 2);
  const keep = (await rowsOf(T.owner.shopId)).filter((r) => r.customer_ref === 'd-keep');
  const bRows = await rowsOf(B.owner.shopId);
  //  پیش‌نمایش (dryRun) هیچ چیز را عوض نمی‌کند
  await erasure.erase(T.owner.shopId, { phone: '+93799123456' }, { dryRun: true, by: 'owner', userId: T.owner.userId });
  assert.equal((await mine()).length, before.length);
  const out = await erasure.erase(T.owner.shopId, { phone: '+93799123456' }, { by: 'owner', userId: T.owner.userId });
  assert.ok(out.erasureId);
  assert.equal((await mine()).length, 0, 'پیوند به پرونده برید');
  const erased = await many('SELECT * FROM customer_access_log WHERE shop_id=$1 AND customer_ref=$2', [T.owner.shopId, `erased:${out.erasureId}`]);
  assert.equal(erased.length, before.length);
  //  بیننده، نقش، زمان و شمار همان
  assert.deepEqual(erased.map((r) => [r.id, r.viewer_id, r.kind, r.hits]).sort(), before.map((r) => [r.id, r.viewer_id, r.kind, r.hits]).sort());
  assert.deepEqual((await rowsOf(T.owner.shopId)).filter((r) => r.customer_ref === 'd-keep'), keep);
  assert.deepEqual(await rowsOf(B.owner.shopId), bRows, 'دکانِ «ب» (هم‌شناسهٔ d-file) دست نخورد');
  //  صاحب دیگر هیچ ردیفی زیرِ این پرونده نمی‌بیند
  const seen = await h.get('/api/shop/customer-access?customerId=d-file', tok(T.owner));
  assert.deepEqual(seen.body.entries, []);
  //  پاسخِ حذف همان شکلِ پیشین (شمارِ دادهٔ شخصی)؛ سابقهٔ دسترسی در `counts` نیست
  assert.equal(Object.hasOwn(out.counts, 'accessLog'), false);
});

test('فقط‌افزودنی: جز بالا رفتنِ شمار/زمان و ناشناس شدن هیچ ویرایشی پذیرفته نیست؛ نگه‌داری فقط با عدد', async () => {
  const r = await access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, role: 'owner', customerRef: 'd-guard', kind: 'detail', door: 'debtor', at: 1000 });
  for (const sql of [
    "UPDATE customer_access_log SET viewer_id='someone' WHERE id=$1",
    "UPDATE customer_access_log SET kind='phone' WHERE id=$1",
    "UPDATE customer_access_log SET first_at=5 WHERE id=$1",
    'UPDATE customer_access_log SET hits=0 WHERE id=$1',
    "UPDATE customer_access_log SET customer_ref='d-other' WHERE id=$1",
  ]) await assert.rejects(query(sql, [r.id]), /append-only/, sql);
  await query("UPDATE customer_access_log SET customer_ref='erased:era_x' WHERE id=$1", [r.id]);
  //  نگه‌داری: ۰ (پیش‌فرض) ⇒ هیچ؛ با عدد فقط کهنه‌تر از آن
  assert.equal(await access.sweep({ at: 10 * 86_400_000, days: 0 }), 0);
  assert.ok(await one('SELECT 1 AS x FROM customer_access_log WHERE id=$1', [r.id]));
  const fresh = await access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, customerRef: 'd-guard2', kind: 'detail', door: 'debtor', at: 9 * 86_400_000 });
  assert.ok(await access.sweep({ at: 10 * 86_400_000, days: 7 }) >= 1);
  assert.equal(await one('SELECT 1 AS x FROM customer_access_log WHERE id=$1', [r.id]), null);
  assert.ok(await one('SELECT 1 AS x FROM customer_access_log WHERE id=$1', [fresh.id]));
});

test('مهاجرتِ ۳۸۰: rollback جدول، تریگر و ردیفِ خودش را برمی‌دارد؛ دوباره نشستن همان', async () => {
  await access.settle();
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '380_customer_access_log.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const has = async () => (await query("SELECT 1 FROM information_schema.tables WHERE table_name='customer_access_log'")).rows.length;
  const fn = async () => (await query("SELECT 1 FROM pg_proc WHERE proname='customer_access_log_guard'")).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  const debtors = (await one('SELECT COUNT(*)::int AS n FROM debtors')).n;
  await query(down);
  assert.equal(await has(), 0);
  assert.equal(await fn(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM debtors')).n, debtors, 'دفتر دست نخورد');
  //  بی جدول هم خواندن می‌افتد نه
  assert.equal((await h.get('/api/debtors/d-bare', tok(T.owner))).status, 200);
  await access.settle();
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await has(), 1);
  assert.equal(await fn(), 1);
  await h.get('/api/debtors/d-bare', tok(T.owner));
  await access.settle();
  assert.equal((await rowsOf(T.owner.shopId)).length, 1);
});

test('SR46-3: پرونده‌ای که فقط پینِ تحویل (CRM-09، فیلدِ تماس) دارد هم «تماس» ثبت می‌شود، نه «پرونده»', async () => {
  await debtor(T.owner, 'd-pin', { name: 'فقط پین', deliveryPin: { lat: 34.523456, lng: 69.181234 } });
  await query('DELETE FROM customer_access_log WHERE shop_id=$1', [T.owner.shopId]);
  assert.deepEqual((await h.get('/api/debtors/d-pin', tok(T.owner))).body.deliveryPin, { lat: 34.523456, lng: 69.181234 });
  //  انبارداری که `customers.contact` ندارد پین را نمی‌بیند ⇒ همان «پرونده»
  assert.equal((await h.get('/api/debtors/d-pin', tok(T.storekeeper))).body.deliveryPin, undefined);
  await access.settle();
  const rows = (await rowsOf(T.owner.shopId)).filter((r) => r.customer_ref === 'd-pin');
  assert.deepEqual(rows.map((r) => [r.viewer_id === T.owner.userId ? 'owner' : 'storekeeper', r.kind]).sort(),
    [['owner', 'phone'], ['storekeeper', 'detail']]);
});
