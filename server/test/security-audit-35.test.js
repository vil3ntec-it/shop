'use strict';
/**
 * ممیزیِ امنیتیِ دستهٔ ۳۵ (`docs/SECURITY-AUDIT-fa.md` بخشِ B2) — هر بند پیش از رفع روی همین پایه سرخ بود.
 *
 *   B1  پشتیبانِ کاملِ دکان (`GET /api/me/backups/:id`) شماره و نشانیِ مشتری را به نقشی می‌داد که CRM-15
 *       (`customers.contact`) آن را از همه‌جای دیگر بسته است (جمع‌آور، انباردار، کارمندی که صاحب بسته).
 *       رفع پشتِ پرچمِ خاموشِ `shop.backup_contact_guard` (جمع‌آور و انباردار امروز تنظیم و عکس را از همین برمی‌گردانند).
 *   B2  `POST /api/me/backups` بدنهٔ تا ۶۵ مگابایتی را **پیش از** سنجیدنِ دکان و نقش در حافظه می‌خواند.
 *   B3  `POST /api/errors` (بی‌توکن) با `schema_version` ِ بیرون از int یا NUL در متن ۵۰۰ می‌داد؛ قرارش «هیچ‌وقت خطا نمی‌دهد» است.
 *   B4  NUL (`%00`) در شناسهٔ مسیر ⇒ ۵۰۰ ِ پایگاه‌داده (۲۲۰۲۱) به‌جای ۴۰۰.
 */
//  S18: سقفِ هر IP ِ `otp/request` در این پرونده کوچک است تا سنجیده شود (هر بند سطل‌ها را خالی می‌کند)
process.env.RATE_OTP_IP_MAX = '6';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let shopId = '';
const T = {};

async function member(role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { ...u, token: u.accessToken, userId: u.user.id };
}
async function setFlag(key, value) {
  await query(
    `INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,$2,$3,$4)
     ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`,
    [shopId, key, JSON.stringify(value), now()]
  );
  require('../src/lib/hot-cache').clear?.();
}

test.before(async () => {
  await h.start();
  const o = await h.newUser('صاحبِ ممیزی');
  const made = await h.post('/api/shop', { name: 'دکانِ ممیزیِ ۳۵' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  shopId = made.body.shop.id;
  T.owner = { ...o, token: o.accessToken, userId: o.user.id };
  for (const role of ['manager', 'staff', 'cashier', 'accountant', 'storekeeper', 'picker']) T[role] = await member(role, `${role}-۳۵`);
  T.cashierX = await member('cashier', 'صندوق‌دارِ بسته');
  const closed = await h.put(`/api/shop/permissions/members/${T.cashierX.userId}`,
    { perms: { 'customers.contact': { mode: 'deny' } } }, { token: T.owner.token });
  assert.equal(closed.status, 200, JSON.stringify(closed.body));
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

/* ── B1 ──────────────────────────────────────────────────────────── */

const LEDGER = Buffer.from(JSON.stringify({ debtors: [{ id: 'd1', name: 'مشتری', phone: '0799123456', deliveryAddress: { area: 'کارتهٔ چهار' } }] }));

test('B1: پرچمِ shop.backup_contact_guard روشن ⇒ فقط کسی که customers.contact دارد پشتیبانِ کامل را برمی‌دارد', async () => {
  const up = await h.raw('POST', '/api/me/backups?ext=json&kind=manual', LEDGER, { token: T.owner.token });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const id = up.body.backup.id;
  const read = async (who) => h.download(`/api/me/backups/${id}`, { token: T[who].token });

  //  مرجع: پرچمِ خاموش ⇒ همان رفتارِ امروز (هر نقش برمی‌دارد — جمع‌آور و انباردار تنظیم و عکس را از همین می‌آورند)
  await setFlag('shop.backup_contact_guard', false);
  for (const who of ['picker', 'storekeeper', 'cashierX']) assert.equal((await read(who)).status, 200, `خاموش: ${who}`);

  await setFlag('shop.backup_contact_guard', true);
  for (const who of ['owner', 'manager', 'staff', 'cashier', 'accountant']) {
    const r = await read(who);
    assert.equal(r.status, 200, who);
    assert.ok(r.buffer.equals(LEDGER), who);
  }
  for (const who of ['picker', 'storekeeper', 'cashierX']) {
    const r = await h.get(`/api/me/backups/${id}`, { token: T[who].token });
    assert.equal(r.status, 403, `${who}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'permission_denied', who);
    assert.ok(!JSON.stringify(r.body).includes('0799123456'), who);
  }
  //  فهرست همچنان برای همه باز است (staff-roles.test.js)
  assert.equal((await h.get('/api/me/backups', { token: T.picker.token })).status, 200);
  await setFlag('shop.backup_contact_guard', false);
});

/* ── B2 ──────────────────────────────────────────────────────────── */

/** درخواستی که ۶۰ مگابایت اعلام می‌کند ولی فقط ۱ کیلوبایت می‌فرستد؛ پاسخ در مهلت آمد؟ */
function declaredHuge(token, ms = 2500) {
  const { port } = h.server().address();
  return new Promise((resolve) => {
    const req = http.request({
      host: '127.0.0.1', port, method: 'POST', path: '/api/me/backups?ext=json',
      headers: { Authorization: `Bearer ${token}`, 'Content-Type': 'application/octet-stream', 'Content-Length': String(60 * 1024 * 1024) },
    });
    const timer = setTimeout(() => { req.destroy(); resolve({ status: 0 }); }, ms);
    req.on('response', (res) => {
      let text = '';
      res.on('data', (c) => { text += c; });
      res.on('end', () => { clearTimeout(timer); req.destroy(); let body = null; try { body = JSON.parse(text); } catch { body = text; } resolve({ status: res.statusCode, body }); });
    });
    req.on('error', () => {});
    req.write(Buffer.alloc(1024, 0x41));
  });
}

test('B2: بی دکان یا نقشِ بی اجازهٔ فرستادن ⇒ ۴۰۳ پیش از خواندنِ بدنهٔ بزرگ', async () => {
  const lone = await h.newUser('بی‌دکان-۳۵');
  const a = await declaredHuge(lone.accessToken);
  assert.equal(a.status, 403, `بی دکان: پاسخ پیش از بدنه نیامد ${JSON.stringify(a.body)}`);
  assert.equal(a.body.error.code, 'no_shop');
  for (const who of ['picker', 'cashier', 'accountant', 'storekeeper']) {
    const r = await declaredHuge(T[who].token);
    assert.equal(r.status, 403, `${who}: پاسخ پیش از بدنه نیامد`);
    assert.equal(r.body.error.code, 'permission_denied', who);
  }
  //  نقشِ مجاز همان راهِ پیشین را می‌رود (بدنهٔ کوچک ⇒ ۲۰۱)
  const ok = await h.raw('POST', '/api/me/backups?ext=json&kind=manual', Buffer.from('{}'), { token: T.staff.token });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
});

/* ── B3 ──────────────────────────────────────────────────────────── */

test('B3: POST /api/errors با schema_version ِ بیرون از int، منفی یا NUL ⇒ همان ۲۰۲، هرگز ۵۰۰', async () => {
  const bad = [
    { message: 'x', schema_version: 9007199254740992 },
    { message: 'x', schema_version: -1e300 },
    { message: 'x', schema_version: 3000000000 },
    { message: 'a\u0000b', stack: 's\u0000', log_tail: '\u0000', device_id: 'd\u0000', app_version: '1\u0000' },
  ];
  for (const body of bad) {
    resetAll();
    const r = await h.post('/api/errors', body);
    assert.equal(r.status, 202, `${JSON.stringify(body)} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    const row = await one('SELECT schema_version, message, device_id FROM client_errors WHERE id=$1', [r.body.id]);
    assert.ok(row, 'ردیف ثبت شد');
    assert.ok(Number.isSafeInteger(Number(row.schema_version)));
    assert.ok(!String(row.message).includes('\u0000') && !String(row.device_id).includes('\u0000'));
  }
  const good = await h.post('/api/errors', { message: 'y', schema_version: 7 });
  assert.equal((await one('SELECT schema_version FROM client_errors WHERE id=$1', [good.body.id])).schema_version, 7);
});

/* ── B4 ──────────────────────────────────────────────────────────── */

test('B4: NUL در مسیر ⇒ ۴۰۰ ِ bad_path، نه ۵۰۰ ِ پایگاه‌داده', async () => {
  for (const [method, path] of [
    ['GET', '/api/shop/catalog/products/a%00b'],
    ['GET', '/api/me/backups/%00'],
    ['DELETE', '/api/shop/promos/rules/a%00'],
  ]) {
    const r = await h.api(method, path, { token: T.owner.token });
    assert.equal(r.status, 400, `${method} ${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_path');
  }
  //  `%2500` (یعنی متنِ «%00») NUL نیست و دست نمی‌خورد
  assert.notEqual((await h.get('/api/me/backups/x%2500', { token: T.owner.token })).status, 500);
});

/* ── B5 ──────────────────────────────────────────────────────────── */

test('B5: کدِ خطای درونی (SQLSTATE، ECONNREFUSED) در پاسخِ ۵xx نمی‌آید؛ کدِ خطای نمایش‌دادنی می‌ماند', () => {
  const { errorHandler, busy, upstream } = require('../src/middleware/errors');
  const run = (err) => {
    const out = {};
    const res = { status(s) { out.status = s; return this; }, set() { return this; }, json(b) { out.body = b; return this; } };
    errorHandler(err, { method: 'GET', originalUrl: '/api/x', path: '/api/x' }, res, () => {});
    return out;
  };
  for (const code of ['22003', '23505', 'ECONNREFUSED']) {
    const r = run(Object.assign(new Error('درونی'), { code }));
    assert.equal(r.status, 500);
    assert.equal(r.body.error.code, 'internal', code);
    assert.ok(!JSON.stringify(r.body).includes(code), code);
  }
  assert.equal(run(busy(3)).body.error.code, 'db_busy');
  assert.equal(run(upstream('پیامک نرفت')).body.error.code, 'delivery_failed');
  assert.equal(run(Object.assign(new Error('x'), { status: 413, type: 'entity.too.large' })).body.error.code, 'error');
});

/* ── B6 ──────────────────────────────────────────────────────────── */

test('B6: خبرِ آینده‌دارِ کارمند «تازه‌ترین» نمی‌ماند و «دیده شد» ِ صاحب را به آینده نمی‌برد', async () => {
  const t0 = Date.now();
  const future = t0 + 30 * 365 * 24 * 3600 * 1000;
  const posted = await h.post('/api/events', { events: [
    { kind: 'note', title: 'آینده', at: future, clientId: `b6-f-${t0}` },
    { kind: 'note', title: 'منفی', at: -5, clientId: `b6-n-${t0}` },
    { kind: 'note', title: 'بزرگ', at: 1e300, clientId: `b6-b-${t0}` },
    { kind: 'note', title: 'دیروز', at: t0 - 86400000, clientId: `b6-y-${t0}` },
  ] }, { token: T.picker.token });
  assert.equal(posted.status, 201, JSON.stringify(posted.body));
  const at = Object.fromEntries(posted.body.events.map((e) => [e.title, e.at]));
  for (const k of ['آینده', 'منفی', 'بزرگ']) assert.ok(at[k] >= t0 && at[k] <= Date.now() + 1000, `${k}: ${at[k]}`);
  assert.equal(at['دیروز'], t0 - 86400000, 'زمانِ گذشتهٔ صفِ بی‌اینترنت می‌ماند');

  //  صاحب «تا تازه‌ترین» را دید؛ خبرِ واقعیِ بعدی باز «نخوانده» است و با `since` می‌رسد
  const feed = await h.get('/api/events', { token: T.owner.token });
  const newest = Math.max(...feed.body.events.map((e) => e.at));
  assert.ok(newest <= Date.now() + 1000);
  const seen = await h.post('/api/events/seen', { at: future }, { token: T.owner.token });
  assert.ok(seen.body.seenAt <= Date.now() + 5 * 60 * 1000 + 1000, `seen ${seen.body.seenAt}`);
  await new Promise((r) => setTimeout(r, 5));
  const later = await h.post('/api/events', { kind: 'stock_out', title: 'تمام شد', clientId: `b6-l-${t0}` }, { token: T.staff.token });
  assert.equal(later.status, 201);
  const next = await h.get(`/api/events?since=${newest}`, { token: T.owner.token });
  assert.ok(next.body.events.some((e) => e.title === 'تمام شد'), JSON.stringify(next.body.events.map((e) => e.title)));
});

/* ── B7 ──────────────────────────────────────────────────────────── */

test('B7: درهای مدیر با limit ِ منفی یا since ِ بیرون از bigint ⇒ ۲۰۰، نه ۵۰۰', async () => {
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'audit35','audit35',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now() - 9000]);
  const admin = (await h.post('/api/admin/login', { username: 'audit35', password: 'Admin!12345' })).body.token;
  for (const path of ['/api/admin/otp?limit=-1', '/api/admin/ops/alerts?since=99999999999999999999']) {
    const r = await h.get(path, { token: admin });
    assert.equal(r.status, 200, `${path} ⇒ ${r.status} ${JSON.stringify(r.body).slice(0, 200)}`);
  }
});

/* ── B8 (رقابت؛ روی PostgreSQL ِ واقعی پیش از رفع سرخ) ─────────────── */

test('B8: هشت «ساختنِ دکان» ِ هم‌زمانِ یک حساب ⇒ یک دکان، بقیه ۴۰۹ already_member', async () => {
  const u = await h.newUser('رقابتِ دکان-۳۵');
  const rs = await Promise.all(Array.from({ length: 8 }, (_, i) =>
    h.post('/api/shop', { name: `دکانِ هم‌زمان ${i}` }, { token: u.accessToken })));
  const codes = rs.map((r) => r.status).sort();
  assert.deepEqual(codes, [201, 409, 409, 409, 409, 409, 409, 409], JSON.stringify(rs.map((r) => r.body?.error?.code || r.status)));
  for (const r of rs) if (r.status === 409) assert.equal(r.body.error.code, 'already_member');
  const owned = await query('SELECT id FROM shops WHERE owner_user_id=$1', [u.user.id]);
  assert.equal(owned.rows.length, 1);
  const mem = await query(`SELECT 1 FROM shop_members WHERE user_id=$1 AND status='active'`, [u.user.id]);
  assert.equal(mem.rows.length, 1);
});

test('B4: NUL در رشته‌های بدنه ⇒ همان کارِ validate.text (انداخته می‌شود)، نه ۵۰۰ ِ پایگاه‌داده', async () => {
  for (const [path, body] of [
    ['/api/vip/redeem', { code: 'a\u0000b' }],
    ['/api/portal/redeem', { code: 'a\u0000b' }],
    ['/api/shop/customer-erasure', { debtorId: 'a\u0000b', phone: '\u0000', reason: 'x\u0000' }],
  ]) {
    resetAll();
    const r = await h.post(path, body, { token: T.owner.token });
    assert.ok(r.status < 500, `${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
});

/* ── S18 (بازبینیِ بندِ باز) ───────────────────────────────────────── */

test('S18: otp/request ِ ایمیلی از یک IP با ایمیل‌های گوناگون ⇒ پس از سقفِ IP ۴۲۹؛ سطلِ هر مقصد سرِ جایش', async () => {
  resetAll();
  const out = [];
  for (let i = 0; i < 8; i++) out.push((await h.post('/api/auth/otp/request', { email: `s18-${i}-${Date.now()}@test.local` })).status);
  assert.deepEqual(out, [200, 200, 200, 200, 200, 200, 429, 429], out.join(','));
  //  پنجرهٔ تازه (یا IP ِ دیگر) ⇒ دوباره باز؛ سطلِ هر مقصد (`otpMax`) و فاصلهٔ «ارسالِ دوباره» جدا سرِ جایشان‌اند (login-hardening)
  resetAll();
  assert.equal((await h.post('/api/auth/otp/request', { email: `s18-after-${Date.now()}@test.local` })).status, 200);
});

test('B4: NUL در پرسشِ درِ بی‌توکنِ request-status و گزارشِ کاتالوگ ⇒ ۴۰۰، نه ۵۰۰', async () => {
  const rs = await h.get('/api/auth/shop/request-status?request_id=a%00b');
  assert.equal(rs.status, 400, JSON.stringify(rs.body));
  await setFlag('shop.catalog', true);
  const rep = await h.get('/api/shop/catalog/report?productId=a%00b', { token: T.owner.token });
  assert.equal(rep.status, 400, JSON.stringify(rep.body));
  //  X2 (`security-audit-35b`): NUL در پرسش حالا پیش از هر در ⇒ `bad_query`؛ `bad_product_id` ِ خودِ در لایهٔ دوم است
  assert.equal(rep.body.error.code, 'bad_query');
  assert.equal((await h.get('/api/shop/catalog/report', { token: T.owner.token })).status, 200);
  await setFlag('shop.catalog', false);
});
