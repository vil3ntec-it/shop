'use strict';
/**
 * RPT-18 (§۶.۱۰، فاز ۶) — گزارشِ همهٔ شعبه‌ها (دکان‌های همین صاحب) در یک جا: `GET /api/shop/branches-report`
 * (`lib/branches-report.js`). روی API و دیتابیسِ واقعی (PGlite/PostgreSQL).
 *
 *   - پرچمِ `shop.branches_report` خاموش (پیش‌فرض) ⇒ فقط `{enabled:false}`؛ هر شعبه فقط با پرچمِ خودش.
 *   - فقط دکان‌هایی که کاربرِ توکن صاحبِ فعالِ آن‌هاست؛ دکانِ دیگران و دکانی که فقط مدیرش است هرگز.
 *   - عددها همان خلاصهٔ گزارشِ خودکارِ صاحب (`owner-report.buildSummary`) با روزِ محلیِ هر دکان؛ جمعِ همه = جمعِ شعبه‌ها.
 *   - مدیر/کارمند ⇒ بی عدد؛ بازهٔ نادرست ⇒ ۴۰۰؛ سقفِ نرخِ IP پیش از احراز.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const br = require('../src/lib/branches-report');
const { resetAll } = require('../src/middleware/ratelimit');

//  ۲۰۲۶-۱۰-۳۱ در کابل (+۴:۳۰): نیمه‌شبِ محلی ۱۹:۳۰ ِ UTC ِ روزِ پیش
const DAY = '2026-10-31';
const MIDNIGHT = Date.UTC(2026, 9, 30, 19, 30);
const at = (hh) => MIDNIGHT + hh * 3_600_000;
let admin = '';
let O; let S1; let S2; let S3; let X; let SX; let S4; let staff;

async function setFlag(scope, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: br.FLAG_KEY, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function newShop(token, name) {
  const made = await h.post('/api/shop', { name }, { token });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return made.body.shop.id;
}
/** دکانِ تازه با سازندهٔ خودش و O به‌عنوانِ صاحبِ دوم (API هر حساب را به یک دکانِ ساختنی محدود می‌کند) */
async function branchOf(name) {
  const u = await h.newUser(`سازندهٔ ${name}`, 'shop');
  const id = await newShop(u.accessToken, name);
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'owner','active',$4,$4)`,
    [newId('mem'), id, O.user.id, now()]);
  return id;
}
async function activate(token, shopId) {
  const r = await h.post('/api/shop/active', { shopId }, { token });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function sale(shopId, id, finalTotal, createdAt, paymentMethod = 'cash') {
  await query(
    `INSERT INTO sync_rows (app, account_kind, account_id, table_name, row_id, data, created_at, updated_at)
     VALUES ('shop','shop',$1,'sales',$2,$3::jsonb,1,1)`,
    [shopId, id, JSON.stringify({ total: finalTotal, discount: 0, finalTotal, paymentMethod, status: 'completed', createdAt, date: '' })]);
}
const get = (token, qs = '') => h.get(`/api/shop/branches-report${qs}`, { token });

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  O = await h.newUser('صاحبِ سه شعبه', 'shop');
  S1 = await newShop(O.accessToken, 'شعبهٔ مرکز');
  S2 = await branchOf('شعبهٔ کارته');
  S3 = await branchOf('شعبهٔ خاموش');
  await activate(O.accessToken, S1);
  X = await h.newUser('صاحبِ دیگر', 'shop');
  SX = await newShop(X.accessToken, 'دکانِ بیگانه');
  S4 = await newShop((await h.newUser('صاحبِ چهارم', 'shop')).accessToken, 'دکانی که O فقط مدیرش است');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'manager','active',$4,$4)`,
    [newId('mem'), S4, O.user.id, now()]);
  staff = await h.newUser('کارمندِ مرکز', 'shop');
  await query(`INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), S1, staff.user.id, now()]);
  await sale(S1, 's1-a', 900, at(9));
  await sale(S1, 's1-b', 500, at(15), 'credit');
  await sale(S1, 's1-old', 7000, at(-3));          //  دیروزِ محلی ⇒ بیرون
  await sale(S2, 's2-a', 300, at(23.9), 'card');
  await sale(S2, 's2-next', 4000, at(24.1));        //  فردای محلی ⇒ بیرون
  await sale(S3, 's3-a', 111, at(10));
  await sale(SX, 'sx-a', 999, at(10));
  await sale(S4, 's4-a', 555, at(10));
  //  پرچم پیش‌فرض‌روشن است (`lib/flag-defaults.js`، ۱۴۰۵/۰۸/۲۲) ⇒ «خاموش» این‌جا `false` ِ صریح است
  for (const s of [S1, S2, S3, SX, S4]) await setFlag(s, false);
});
test.after(async () => { await h.stop(); });

test('پرچم خاموش (false ِ صریح) ⇒ فقط {enabled:false}، بی هیچ عدد', async () => {
  const r = await get(O.accessToken, `?from=${DAY}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { enabled: false });
});

test('RPT-18: هر شعبهٔ پرچم‌روشنِ همین صاحب جدا، با روزِ محلیِ خودش، و جمعِ همه؛ دکانِ دیگران و دکانِ مدیری‌شده هرگز', async () => {
  for (const s of [S1, S2, SX, S4]) await setFlag(s, true);
  const r = await get(O.accessToken, `?from=${DAY}&to=${DAY}`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const b = r.body;
  assert.equal(b.enabled, true);
  assert.equal(b.owner, true);
  assert.equal(b.from, DAY);
  assert.deepEqual(b.shops.map((s) => [s.shop_id, s.name, s.current]), [[S1, 'شعبهٔ مرکز', true], [S2, 'شعبهٔ کارته', false]]);
  assert.equal(b.skipped, 1, 'شعبهٔ پرچم‌خاموش فقط شمرده می‌شود');
  assert.equal(b.truncated, false);
  const [m, k] = b.shops;
  assert.equal(m.summary.invoices, 2);
  assert.equal(m.summary.netSales, 140000);
  assert.equal(m.summary.cashNet, 90000);
  assert.equal(m.summary.otherNet, 50000);
  assert.deepEqual(m.summary.payments.map((p) => [p.method, p.netSales]).sort(), [['cash', 90000], ['credit', 50000]]);
  assert.equal(k.summary.invoices, 1);
  assert.equal(k.summary.netSales, 30000);
  assert.equal(m.timezone, 'Asia/Kabul');
  assert.deepEqual(b.totals, {
    shops: 2, partial: false, invoices: 3, sales: 170000, discount: 0, returnCount: 0, returns: 0,
    netSales: 170000, cashNet: 90000, otherNet: 80000,
  });
  const text = JSON.stringify(b);
  for (const foreign of [SX, S4, S3, 'دکانِ بیگانه', 'sx-a']) assert.ok(!text.includes(foreign), foreign);
});

test('فقط صاحب: کارمند و مدیر ⇒ {enabled:true, owner:false} بی عدد؛ دکانِ فعالِ نشست «current» است', async () => {
  const s = await get(staff.accessToken, `?from=${DAY}`);
  assert.equal(s.status, 200);
  assert.deepEqual(s.body, { enabled: true, owner: false });
  //  O در S4 فقط مدیر است ⇒ با دکانِ فعالِ S4 هیچ عددی
  await activate(O.accessToken, S4);
  assert.deepEqual((await get(O.accessToken, `?from=${DAY}`)).body, { enabled: true, owner: false });
  await activate(O.accessToken, S2);
  const r = await get(O.accessToken, `?from=${DAY}`);
  assert.deepEqual(r.body.shops.map((x) => [x.shop_id, x.current]), [[S2, true], [S1, false]]);
  //  دکانِ فعالِ پرچم‌خاموش ⇒ خاموش
  await activate(O.accessToken, S3);
  assert.deepEqual((await get(O.accessToken, `?from=${DAY}`)).body, { enabled: false });
  await activate(O.accessToken, S1);
});

test('بی بازه ⇒ امروزِ هر دکان؛ بازهٔ نادرست ⇒ ۴۰۰ و بیش از ۳۱ روز رد', async () => {
  const today = await get(O.accessToken);
  assert.equal(today.status, 200, JSON.stringify(today.body));
  assert.equal(today.body.from, null);
  assert.ok(today.body.shops.every((x) => x.from === x.to && /^\d{4}-\d{2}-\d{2}$/.test(x.from)));
  assert.equal((await get(O.accessToken, '?from=2026-13-01')).body.error.code, 'bad_date');
  assert.equal((await get(O.accessToken, '?from=2026-02-30')).body.error.code, 'bad_date');
  assert.equal((await get(O.accessToken, '?from=2026-10-31&to=2026-10-01')).body.error.code, 'bad_range');
  assert.equal((await get(O.accessToken, '?from=2026-10-01&to=2026-11-01')).body.error.code, 'range_too_long');
  const month = await get(O.accessToken, '?from=2026-10-01&to=2026-10-31');
  assert.equal(month.status, 200, JSON.stringify(month.body));
  assert.equal(month.body.totals.invoices, 4, 'سی‌ویک روز: فروشِ دیروزِ مرکز هم');
});

test('بی توکن ۴۰۱؛ سقفِ نرخِ IP پیش از احراز ⇒ ۴۲۹', async () => {
  resetAll();
  assert.equal((await h.get('/api/shop/branches-report')).status, 401);
  let last = 0;
  for (let i = 0; i < 60; i++) last = (await h.get('/api/shop/branches-report')).status;
  assert.equal(last, 429);
  resetAll();
});
