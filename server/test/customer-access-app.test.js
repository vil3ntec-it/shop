'use strict';
/**
 * CRM-15 — «سابقهٔ دسترسی» برای دیدنی که **برنامه از دفترِ خودش** گزارش می‌دهد: `POST /api/shop/customer-access`
 * (درِ `app`، مهاجرتِ ۶۱۰، `lib/customer-access.noteApp`). پیش‌تر نسخهٔ وب (دفترِ مرورگر) و نقشی که `auditLog` نمی‌نویسد
 * (حسابدار؛ `CustomerAccessNote` روی دستگاه «ننوشتن» برمی‌گرداند) هیچ ردی نمی‌گذاشتند.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ردیفِ `app` با بیننده، نقش، دکان و دستگاهِ **توکن**؛ هر فیلدِ دیگرِ بدنه (بیننده، دکان، نقش، در، دستگاه، شمار) بی‌اثر.
 *   ۲) `phone` ِ اصلِ بی `customers.contact` (پیش‌فرضِ انباردار، یا لایهٔ صاحب روی حسابدار) ⇐ `detail`؛ با مجوز همان `phone`.
 *   ۳) یکی کردنِ ۱۰ دقیقه، همان قاعدهٔ درهای سرور.
 *   ۴) `*` (برگهٔ همهٔ مشتریان) فقط با `export` و در پروندهٔ هر مشتری دیده می‌شود؛ شناسه یا نوعِ بد ⇒ ۴۰۰ و هیچ ردیف.
 *   ۵) جداسازیِ دکان؛ هر عضو می‌نویسد (شاگرد هم) ولی فقط `audit.view` فهرست را می‌بیند؛ بی توکن ۴۰۱.
 *   ۶) سطلِ هر کاربر (۱۲۰) پس از احراز؛ کاربرِ دیگرِ همان دکان باز. (سطلِ IP ِ پیش از احراز: `routes-idor-31-limits`)
 *   ۷) SEC-10 ردیف‌های `app` ِ همان مشتری را هم `erased:` می‌کند؛ ۸) rollback ِ ۶۱۰.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const access = require('../src/lib/customer-access');
const { resetAll } = require('../src/middleware/ratelimit');

const PHONE = '0799555123';
const T = {};
const B = {};

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken, userId: u.user.id };
}
async function shop(label) {
  const o = await h.newUser(`صاحبِ ${label}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { token: o.accessToken, userId: o.user.id, shopId: made.body.shop.id };
}
const tok = (who) => ({ token: who.token });
const note = (who, body) => h.post('/api/shop/customer-access', body, tok(who));
const appRows = (shopId) => many("SELECT * FROM customer_access_log WHERE shop_id=$1 AND door='app' ORDER BY first_at, id", [shopId]);
const wipe = () => query("DELETE FROM customer_access_log WHERE door='app'");

test.before(async () => {
  await h.start();
  Object.assign(T, { owner: await shop('الف') });
  for (const role of ['manager', 'staff', 'cashier', 'storekeeper', 'accountant']) T[role] = await member(T.owner.shopId, role, `کارمندِ ${role}`);
  Object.assign(B, { owner: await shop('ب') });
  const d = await h.post('/api/debtors', { id: 'd-app', data: { name: 'مشتریِ وب', phone: PHONE } }, tok(T.owner));
  assert.equal(d.status, 201, JSON.stringify(d.body));
});
test.after(async () => { await access.settle(); await h.stop(); });
test.beforeEach(() => resetAll());

test('ردیفِ «app» با بیننده، نقش، دکان و دستگاهِ توکن؛ فیلدهای دیگرِ بدنه بی‌اثر', async () => {
  await wipe();
  const r = await note(T.owner, {
    customerId: 'd-app', kind: 'phone',
    viewerId: T.manager.userId, shopId: B.owner.shopId, role: 'accountant', door: 'debtor', deviceId: 'dev-fake', hits: 99,
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { recorded: true, kind: 'phone' });
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const rows = await appRows(T.owner.shopId);
  assert.equal(rows.length, 1, JSON.stringify(rows));
  const [x] = rows;
  assert.equal(x.viewer_id, T.owner.userId);
  assert.equal(x.role, 'owner');
  assert.equal(x.customer_ref, 'd-app');
  assert.equal(x.kind, 'phone');
  assert.equal(x.door, 'app');
  assert.match(x.device_id, /^dev-/);
  assert.notEqual(x.device_id, 'dev-fake');
  assert.equal(Number(x.hits), 1);
  assert.deepEqual(await appRows(B.owner.shopId), [], 'شناسهٔ دکانِ بدنه دکان را عوض نمی‌کند');
  //  فهرستِ صاحب همان ردیف را با درِ «app» دارد
  const seen = await h.get('/api/shop/customer-access?customerId=d-app', tok(T.owner));
  assert.equal(seen.status, 200);
  const e = seen.body.entries.find((y) => y.door === 'app');
  assert.ok(e, JSON.stringify(seen.body));
  assert.equal(e.viewerId, T.owner.userId);
  assert.equal(e.kind, 'phone');
  assert.equal(e.role, 'owner');
});

test('«phone» ِ اصلِ بی customers.contact ⇐ «detail» (انباردارِ پیش‌فرض، حسابدارِ بسته)؛ با مجوز همان', async () => {
  await wipe();
  const sk = await note(T.storekeeper, { customerId: 'd-app', kind: 'phone' });
  assert.equal(sk.status, 200, JSON.stringify(sk.body));
  assert.equal(sk.body.kind, 'detail');
  const acc = await note(T.accountant, { customerId: 'd-app', kind: 'phone' });
  assert.equal(acc.body.kind, 'phone', 'حسابدار به‌طورِ پیش‌فرض شماره را می‌بیند');
  const close = await h.put(`/api/shop/permissions/members/${T.accountant.userId}`, { perms: { 'customers.contact': { mode: 'deny' } } }, tok(T.owner));
  assert.equal(close.status, 200, JSON.stringify(close.body));
  const closed = await note(T.accountant, { customerId: 'd-app', kind: 'phone' });
  assert.equal(closed.body.kind, 'detail');
  const reopen = await h.put(`/api/shop/permissions/members/${T.accountant.userId}`, { perms: {} }, tok(T.owner));
  assert.equal(reopen.status, 200, JSON.stringify(reopen.body));
  const kinds = (await appRows(T.owner.shopId)).map((r) => `${r.role}/${r.kind}`).sort();
  assert.deepEqual(kinds, ['accountant/detail', 'accountant/phone', 'storekeeper/detail']);
});

test('تکرار در ۱۰ دقیقه یک ردیف با شمار؛ نوعِ دیگر ردیفِ خودش', async () => {
  await wipe();
  for (let i = 0; i < 3; i++) assert.equal((await note(T.manager, { customerId: 'd-app', kind: 'detail' })).status, 200);
  assert.equal((await note(T.manager, { customerId: 'd-app', kind: 'export' })).status, 200);
  const rows = await appRows(T.owner.shopId);
  assert.deepEqual(rows.map((r) => [r.kind, Number(r.hits)]).sort(), [['detail', 3], ['export', 1]]);
});

test('«*» فقط با export و در پروندهٔ هر مشتری؛ شناسه یا نوعِ بد ⇒ ۴۰۰ و هیچ ردیف', async () => {
  await wipe();
  const all = await note(T.accountant, { customerId: '*', kind: 'export' });
  assert.equal(all.status, 200, JSON.stringify(all.body));
  const seen = await h.get('/api/shop/customer-access?customerId=d-app', tok(T.owner));
  const sheet = seen.body.entries.filter((e) => e.door === 'app' && e.kind === 'export' && e.viewerId === T.accountant.userId);
  assert.equal(sheet.length, 1, JSON.stringify(seen.body));
  assert.equal(sheet[0].all, true, 'برنامه «برگهٔ همه» می‌نویسد');
  assert.ok(seen.body.entries.filter((e) => e !== sheet[0]).every((e) => !Object.hasOwn(e, 'all')), 'ردیفِ یک مشتری بی «all»');
  const other = await h.get('/api/shop/customer-access?customerId=d-unknown', tok(T.owner));
  assert.equal(other.body.entries.filter((e) => e.door === 'app').length, 1, 'برگهٔ همه در پروندهٔ هر مشتری');
  assert.deepEqual((await h.get('/api/shop/customer-access?customerId=d-app', tok(B.owner))).body.entries, [], 'نه در دکانِ دیگر');
  const before = (await appRows(T.owner.shopId)).length;
  const bad = [
    [{ customerId: '*', kind: 'detail' }, 'bad_customer'], [{ customerId: '*', kind: 'phone' }, 'bad_customer'],
    [{ customerId: 'erased:era1', kind: 'detail' }, 'bad_customer'], [{ customerId: 'ph:abc', kind: 'detail' }, 'bad_customer'],
    [{ customerId: '../x', kind: 'detail' }, 'bad_customer'], [{ customerId: 'a'.repeat(81), kind: 'detail' }, 'bad_customer'],
    [{ customerId: ['d-app'], kind: 'detail' }, 'bad_customer'],
    [{ customerId: { id: 'd-app' }, kind: 'detail' }, 'bad_customer'], [{ kind: 'detail' }, 'bad_customer'],
    [{ customerId: 'd-app', kind: 'peek' }, 'bad_kind'], [{ customerId: 'd-app' }, 'bad_kind'], [{ customerId: 'd-app', kind: ['detail'] }, 'bad_kind'],
  ];
  for (const [body, code] of bad) {
    const r = await note(T.owner, body);
    assert.equal(r.status, 400, `${JSON.stringify(body)}: ${r.status}`);
    assert.equal(r.body.error.code, code, JSON.stringify(body));
  }
  const arr = await h.api('POST', '/api/shop/customer-access', { body: [{ customerId: 'd-app', kind: 'detail' }], token: T.owner.token });
  assert.equal(arr.status, 400);
  assert.equal((await appRows(T.owner.shopId)).length, before);
});

test('هر عضو می‌نویسد ولی فقط audit.view فهرست را می‌بیند؛ بی توکن ۴۰۱؛ جداسازیِ دکان', async () => {
  await wipe();
  for (const who of ['owner', 'manager', 'staff', 'cashier', 'storekeeper', 'accountant']) {
    const r = await note(T[who], { customerId: 'd-app', kind: 'detail' });
    assert.equal(r.status, 200, `${who}: ${JSON.stringify(r.body)}`);
  }
  assert.deepEqual((await appRows(T.owner.shopId)).map((r) => r.role).sort(),
    ['accountant', 'cashier', 'manager', 'owner', 'staff', 'storekeeper']);
  for (const who of ['staff', 'cashier', 'storekeeper']) {
    assert.equal((await h.get('/api/shop/customer-access?customerId=d-app', tok(T[who]))).status, 403, who);
  }
  assert.equal((await h.post('/api/shop/customer-access', { customerId: 'd-app', kind: 'detail' })).status, 401);
  //  «ب» با شناسهٔ پروندهٔ «الف»: ردیف در دکانِ خودِ «ب»؛ «الف» آن را نمی‌بیند
  const fromB = await note(B.owner, { customerId: 'd-app', kind: 'phone' });
  assert.equal(fromB.status, 200);
  const bRows = await appRows(B.owner.shopId);
  assert.equal(bRows.length, 1);
  assert.equal(bRows[0].viewer_id, B.owner.userId);
  const seenA = await h.get('/api/shop/customer-access?customerId=d-app', tok(T.owner));
  assert.ok(seenA.body.entries.every((e) => e.viewerId !== B.owner.userId));
  const spoof = await h.api('POST', '/api/shop/customer-access', {
    body: { customerId: 'd-app', kind: 'detail' }, token: B.owner.token, headers: { 'X-Shop-Id': T.owner.shopId },
  });
  assert.ok(spoof.status === 200 || spoof.status === 403, String(spoof.status));
  assert.ok((await appRows(T.owner.shopId)).every((r) => r.viewer_id !== B.owner.userId), 'X-Shop-Id دکان را عوض نمی‌کند');
});

test('سطلِ هر کاربر پس از احراز: ۱۲۰ و بعد ۴۲۹؛ کاربرِ دیگرِ همان دکان باز', async () => {
  for (let i = 1; i <= 120; i++) {
    const r = await note(T.cashier, { customerId: 'd-app', kind: 'detail' });
    assert.equal(r.status, 200, `تلاشِ ${i}: ${JSON.stringify(r.body)}`);
  }
  assert.equal((await note(T.cashier, { customerId: 'd-app', kind: 'detail' })).status, 429);
  assert.equal((await note(T.manager, { customerId: 'd-app', kind: 'detail' })).status, 200);
  //  خواندنِ فهرست سطلِ خودش را دارد
  assert.equal((await h.get('/api/shop/customer-access?customerId=d-app', tok(T.owner))).status, 200);
});

test('SEC-10: ردیف‌های «app» ِ همان مشتری هم ناشناس می‌شوند؛ «*» و مشتریِ دیگر می‌مانند', async () => {
  await wipe();
  const erasure = require('../src/lib/customer-erasure');
  await h.post('/api/debtors', { id: 'd-stay', data: { name: 'دیگری', phone: '0700111999' } }, tok(T.owner));
  await note(T.manager, { customerId: 'd-app', kind: 'phone' });
  await note(T.manager, { customerId: 'd-stay', kind: 'detail' });
  await note(T.accountant, { customerId: '*', kind: 'export' });
  const out = await erasure.erase(T.owner.shopId, { phone: PHONE }, { by: 'owner', userId: T.owner.userId });
  assert.ok(out.erasureId);
  const refs = (await appRows(T.owner.shopId)).map((r) => r.customer_ref).sort();
  assert.deepEqual(refs, ['*', 'd-stay', `erased:${out.erasureId}`]);
});

test('مهاجرتِ ۶۱۰: rollback ردیف‌های «app» و CHECK ِ تازه را برمی‌دارد؛ درهای سرور دست نمی‌خورند؛ دوباره نشستن همان', async () => {
  await access.settle();
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '610_customer_access_app_door.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  await note(T.owner, { customerId: 'd-app', kind: 'detail' });
  //  (همان بیننده + مشتری + نوع در ۱۰ دقیقه یک ردیف است، هر دری که باشد ⇒ درِ سرور با مشتریِ دیگر)
  await access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, role: 'owner', customerRef: 'd-srv', kind: 'detail', door: 'debtor' });
  const serverRows = async () => (await one("SELECT COUNT(*)::int AS n FROM customer_access_log WHERE door <> 'app'")).n;
  const kept = await serverRows();
  assert.ok(kept >= 1);
  await query(down);
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM customer_access_log WHERE door = 'app'")).n, 0);
  assert.equal(await serverRows(), kept);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  await assert.rejects(access.record({ shopId: T.owner.shopId, viewerId: T.owner.userId, customerRef: 'd-app', kind: 'detail', door: 'app' }), /check/i);
  //  بی CHECK ِ تازه: درِ app ۵xx (خواندنِ پرونده نمی‌افتد)
  assert.ok((await note(T.owner, { customerId: 'd-app', kind: 'export' })).status >= 500);
  assert.equal((await h.get('/api/debtors/d-app', tok(T.owner))).status, 200);
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal((await note(T.owner, { customerId: 'd-app', kind: 'export' })).status, 200);
});
