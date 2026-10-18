'use strict';
/**
 * صفحهٔ کنترلِ مدیر — ضمانت‌های سمتِ سرورِ `docs/ADMIN-CONTROL-PLANE.md`
 * (بندِ ۳۹.۸ و ۲۹.۴ِ سند).
 *
 *   • توکنِ مدیر توکنِ دکان نیست — مدیر از درِ کاربر به دادهٔ دکان نمی‌رسد
 *   • «خروجیِ» دادهٔ خصوصیِ یک حساب (دانلودِ پشتیبانش) و «بازگرداندنِ»
 *     تعارضِ همگام‌سازی: فقط مدیرِ ارشد، با دلیلِ نوشته، با ردیفِ سابقه
 *   • برداشتنِ افزونهٔ پولی: فقط مدیرِ ارشد (مثلِ افزودنش) و فقط از همان
 *     اشتراکی که در نشانی آمده
 *   • کارهای مدیر که ردیفِ سابقه نداشتند: ردِ درخواستِ خرید، شهرِ کاربر،
 *     وضعیتِ گفت‌وگوی پشتیبانی
 *   • دیدنِ پروندهٔ کاربر (لوکیشنِ دقیق، دستگاه‌ها) و پروندهٔ دکان سابقه دارد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword('Admin!12345');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'arshad','ارشد',$2,'superadmin','active',$3), ($4,'poshtiban','پشتیبان',$2,'admin','active',$3)`,
    [newId('adm'), hash, now(), newId('adm')]
  );
});
test.after(async () => { await h.stop(); });

const login = async (username) =>
  (await h.post('/api/admin/login', { username, password: 'Admin!12345' })).body.token;
const superToken = () => login('arshad');
const plainToken = () => login('poshtiban');

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

const auditRows = (action, targetId) => many(
  `SELECT * FROM audit_logs WHERE action=$1 AND ($2 = '' OR target_id=$2) ORDER BY created_at`,
  [action, String(targetId || '')]
);
const detailOf = (row) => (typeof row.detail === 'string' ? JSON.parse(row.detail) : row.detail);

/* ── ۱) جداییِ tenant حتی برای مدیر ───────────────────────────────── */

test('توکنِ مدیر توکنِ دکان نیست: مسیرهای دادهٔ کاربر ۴۰۱ می‌دهند', async () => {
  const t = await superToken();
  for (const path of ['/api/me', '/api/me/backups']) {
    const r = await h.get(path, { token: t });
    assert.equal(r.status, 401, `${path} ⇒ ${r.status}`);
  }
});

/* ── ۲) خروجیِ دادهٔ خصوصی: دانلودِ پشتیبانِ یک حساب ─────────────── */

test('دانلودِ پشتیبانِ حساب: فقط ارشد، فقط با دلیل، با ردیفِ سابقه', async () => {
  const o = await shopOwner('صاحبِ پشتیبان');
  const data = Buffer.from('دفترِ خصوصیِ دکان');
  const up = await h.raw('POST', '/api/me/backups?ext=json', data, { token: o.accessToken });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const path = `/api/admin/accounts/shop/${o.shopId}/backups/${up.body.backup.id}`;

  const weak = await h.download(`${path}?reason=${encodeURIComponent('درخواستِ مشتری')}`, { token: await plainToken() });
  assert.equal(weak.status, 403, '⛔ مدیرِ عادی (پشتیبانی) خروجیِ دادهٔ کامل نمی‌گیرد');

  const t = await superToken();
  const noReason = await h.get(path, { token: t });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');
  assert.equal((await auditRows('admin.account_backup_downloaded', up.body.backup.id)).length, 0);

  const ok = await h.download(`${path}?reason=${encodeURIComponent('بازیابی به درخواستِ صاحب دکان')}`, { token: t });
  assert.equal(ok.status, 200);
  assert.ok(ok.buffer.equals(data));

  const rows = await auditRows('admin.account_backup_downloaded', up.body.backup.id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].actor_type, 'admin');
  assert.equal(rows[0].shop_id, o.shopId, 'ردیف به همان tenant بسته است');
  const d = detailOf(rows[0]);
  assert.equal(d.reason, 'بازیابی به درخواستِ صاحب دکان');
  assert.equal(d.app, 'shop');
  assert.equal(d.tenantId, o.shopId);
});

/* ── ۳) بازگرداندنِ تعارض (restore) ──────────────────────────────── */

test('بازگرداندنِ تعارضِ همگام‌سازی: فقط ارشد، فقط با دلیل', async () => {
  const weak = await h.post('/api/admin/sync/conflicts/1/restore', { reason: 'آزمون' }, { token: await plainToken() });
  assert.equal(weak.status, 403);

  const t = await superToken();
  const noReason = await h.post('/api/admin/sync/conflicts/1/restore', {}, { token: t });
  assert.equal(noReason.status, 400);
  assert.equal(noReason.body.error.code, 'reason_required');

  //  با دلیل از نگهبان می‌گذرد و به خودِ تعارض می‌رسد (که این‌جا نیست)
  const missing = await h.post('/api/admin/sync/conflicts/999999/restore', { reason: 'پیامِ صاحب دکان' }, { token: t });
  assert.equal(missing.status, 404);
});

test('دیدنِ تعارض‌های یک حساب (مقدارهای خصوصی) سابقه دارد', async () => {
  const o = await shopOwner('تعارض‌دار');
  const t = await superToken();
  const r = await h.get(`/api/admin/sync/conflicts?app=shop&account=${o.shopId}`, { token: t });
  assert.equal(r.status, 200);
  const rows = await auditRows('admin.sync_conflicts_viewed', o.shopId);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].shop_id, o.shopId);
});

/* ── ۴) افزونهٔ پولی ───────────────────────────────────────────── */

test('برداشتنِ افزونه: فقط ارشد و فقط از همان اشتراک', async () => {
  const t = await superToken();
  const a = await shopOwner('افزونهٔ الف');
  const b = await shopOwner('افزونهٔ ب');
  const subA = (await h.post('/api/admin/subscriptions', { shopId: a.shopId, plan: 'm1', days: 30, features: ['sales'] }, { token: t })).body.subscription.id;
  const subB = (await h.post('/api/admin/subscriptions', { shopId: b.shopId, plan: 'm1', days: 30, features: ['sales'] }, { token: t })).body.subscription.id;
  const add = await h.post(`/api/admin/subscriptions/${subA}/addons`, { feature: 'barcode', price: 500 }, { token: t });
  assert.equal(add.status, 201, JSON.stringify(add.body));
  const addonId = add.body.addon.id;

  const weak = await h.del(`/api/admin/subscriptions/${subA}/addons/${addonId}`, { token: await plainToken() });
  assert.equal(weak.status, 403, '⛔ برداشتنِ قابلیتِ پولی مثلِ افزودنش کارِ ارشد است');

  const cross = await h.del(`/api/admin/subscriptions/${subB}/addons/${addonId}`, { token: t });
  assert.equal(cross.status, 404, '⛔ افزونهٔ اشتراکِ الف از نشانیِ اشتراکِ ب برداشته نمی‌شود');
  const still = await h.get(`/api/admin/subscriptions/${subA}/addons`, { token: t });
  assert.equal(still.body.addons.length, 1);
  assert.equal((await auditRows('admin.addon_removed', subB)).length, 0, 'ردیفِ سابقهٔ دروغ ساخته نمی‌شود');

  const gone = await h.del(`/api/admin/subscriptions/${subA}/addons/${addonId}`, { token: t });
  assert.equal(gone.status, 200);
  assert.equal((await auditRows('admin.addon_removed', subA)).length, 1);
});

/* ── ۵) کارهایی که ردیفِ سابقه نداشتند ───────────────────────────── */

test('ردِ درخواستِ خرید ردیفِ سابقه با دلیل دارد — و دوباره‌زدن ردیفِ دوم نمی‌سازد', async () => {
  const o = await shopOwner('خریدار');
  const made = await h.post('/api/me/purchase-request', { plan: 'm6' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const id = made.body.request.id;
  const t = await plainToken();

  const r = await h.post(`/api/admin/purchase-requests/${id}/reject`, { reason: 'رسیدِ پرداخت نرسید' }, { token: t });
  assert.equal(r.status, 200);
  assert.equal(r.body.ok, true);
  const again = await h.post(`/api/admin/purchase-requests/${id}/reject`, {}, { token: t });
  assert.equal(again.status, 200, 'رفتارِ قبلی: پاسخ همان ok است');

  const rows = await auditRows('admin.request_rejected', id);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].shop_id, o.shopId);
  assert.equal(detailOf(rows[0]).reason, 'رسیدِ پرداخت نرسید');
});

test('تغییرِ شهرِ کاربر ردیفِ سابقه دارد', async () => {
  const u = await h.newUser('شهری', 'shop');
  const r = await h.put(`/api/admin/users/${u.user.id}/city`, { city: 'هرات' }, { token: await plainToken() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await auditRows('admin.user_city', u.user.id);
  assert.equal(rows.length, 1);
  assert.equal(detailOf(rows[0]).city, 'هرات');
});

test('تغییرِ وضعیتِ گفت‌وگوی پشتیبانی ردیفِ سابقه دارد', async () => {
  const u = await h.newUser('پرسنده', 'shop');
  const th = await h.get('/api/support/thread', { token: u.accessToken });
  assert.equal(th.status, 200, JSON.stringify(th.body));
  const id = th.body.thread.id;
  const r = await h.post(`/api/admin/support/threads/${id}/status`, { status: 'closed' }, { token: await plainToken() });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await auditRows('admin.support_status', id);
  assert.equal(rows.length, 1);
  assert.equal(detailOf(rows[0]).status, 'closed');
});

/* ── ۶) دیدنِ دادهٔ شخصی ───────────────────────────────────────── */

test('دیدنِ پروندهٔ کاربر و پروندهٔ دکان سابقه دارد', async () => {
  const o = await shopOwner('دیده‌شده');
  const t = await plainToken();
  const u = await h.get(`/api/admin/users/${o.user.id}`, { token: t });
  assert.equal(u.status, 200);
  const s = await h.get(`/api/admin/shops/${o.shopId}`, { token: t });
  assert.equal(s.status, 200);

  const uv = await auditRows('admin.user_viewed', o.user.id);
  assert.equal(uv.length, 1);
  assert.equal(uv[0].actor_type, 'admin');
  const sv = await auditRows('admin.shop_viewed', o.shopId);
  assert.equal(sv.length, 1);
  assert.equal(sv[0].shop_id, o.shopId);

  //  پیدا نشده ⇒ ردیفی هم نیست
  await h.get('/api/admin/users/usr_nist', { token: t });
  assert.equal((await auditRows('admin.user_viewed', 'usr_nist')).length, 0);
});

/* ── ۷) فهرستِ کاملِ APIهای مدیر: هیچ‌کدام بی‌نگهبان نیست ─────────── */

/*
 *  مسیرها از خودِ سورس خوانده می‌شوند (نه فهرستِ دستی) تا مسیرِ تازه‌ای
 *  که فراموش شود هم این‌جا بیاید. هر فایلِ `routes/admin*.js` باید در
 *  نقشهٔ سوارشدن باشد — فایلِ تازهٔ بی‌نقشه خودش آزمون را می‌شکند.
 */
const fs = require('node:fs');
const nodePath = require('node:path');
const MOUNTS = {
  'admin.js': '/admin', 'admin-2fa.js': '/admin/2fa', 'admin-logins.js': '/admin/logins',
  'admin-shop-desk.js': '/admin/shop-desk', 'admin-shop-offline.js': '/admin/shop-offline-codes',
  'admin-staff-codes.js': '/admin/shops/:id/staff-codes', 'admin-otp.js': '/admin/otp',
  'admin-sync.js': '/admin/sync', 'admin-platform.js': '/admin', 'admin-pump.js': '/admin/pump',
  'admin-notices.js': '/admin', 'admin-sales.js': '/admin',
  'admin-image-bank.js': '/admin/image-bank',
};
const PUBLIC = new Set(['POST /admin/login']);   // تنها درِ بی‌توکن: خودِ ورود

function adminRoutes() {
  const dir = nodePath.join(__dirname, '..', 'src', 'routes');
  const files = fs.readdirSync(dir).filter(f => /^admin.*\.js$/.test(f));
  for (const f of files) assert.ok(MOUNTS[f], `routes/${f} در نقشهٔ سوارشدن نیست`);
  const out = [];
  const re = /router\.(get|post|put|patch|delete)\(\s*[`'"]([^`'"]+)[`'"]\s*,([^\n]*)/g;
  for (const f of files) {
    const src = fs.readFileSync(nodePath.join(dir, f), 'utf8');
    for (const m of src.matchAll(re)) {
      const paths = m[2].includes('${prefix}')
        ? ['/subscriptions', '/pump/subscriptions'].map(p => m[2].replace('${prefix}', p))
        : [m[2]];
      for (const p of paths) {
        out.push({
          method: m[1].toUpperCase(),
          path: MOUNTS[f] + (p === '/' ? '' : p),
          superOnly: m[3].includes('requireSuperAdmin'),
        });
      }
    }
  }
  return out;
}
const concrete = (p) => p.replace(/:app\b/g, 'shop').replace(/:action\b/g, 'revoke').replace(/:[A-Za-z]+/g, 'x1');

test('هر API مدیر بی‌توکن و با توکنِ دکان ۴۰۱ می‌دهد؛ مسیرهای ارشد برای مدیرِ عادی ۴۰۳', async () => {
  const routes = adminRoutes();
  assert.ok(routes.length > 150, `فقط ${routes.length} مسیر پیدا شد`);
  const shopUser = await shopOwner('بی‌اجازه');
  const plain = await plainToken();
  let supers = 0;
  for (const r of routes) {
    const key = `${r.method} ${r.path}`;
    if (PUBLIC.has(key)) continue;
    const url = `/api${concrete(r.path)}`;
    const anon = await h.api(r.method, url, {});
    assert.equal(anon.status, 401, `${key} بی‌توکن ⇒ ${anon.status}`);
    const asUser = await h.api(r.method, url, { token: shopUser.accessToken });
    assert.equal(asUser.status, 401, `${key} با توکنِ دکان ⇒ ${asUser.status}`);
    if (r.superOnly) {
      supers += 1;
      const weak = await h.api(r.method, url, { token: plain, body: r.method === 'GET' ? null : {} });
      assert.equal(weak.status, 403, `${key} مدیرِ عادی ⇒ ${weak.status}`);
    }
  }
  assert.ok(supers >= 30, `فقط ${supers} مسیرِ ارشد`);
});
