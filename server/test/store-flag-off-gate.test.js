'use strict';
/**
 * ⛔ نگهبانِ «پرچمِ خاموش ⇒ همان درِ ناشناخته» برای **هر** درِ `/api/store/:slug/…` (CHK-13 و قاعدهٔ ویترین در `docs/RULES-fa.md`:
 * «نیست»، «خاموش» و «پرچم نیست» یک جواب دارند).
 *
 * دکانی با فقط `shop.orders` + `shop.storefront`، همهٔ پرچم‌های پیش‌فرض‌روشن (`lib/flag-defaults.js`) **خاموش**، «پرداخت پس از
 * پذیرش» خاموش، یک سفارشِ واقعی (توکنِ درست)، بلیتِ شمارهٔ تأییدشده و نشستِ حسابِ وب. هر در با بدنهٔ بد و بدنهٔ تهی زده
 * می‌شود؛ پاسخ باید ۴۰۴ باشد یا دقیقاً پاسخِ درِ ناشناختهٔ همین نشانی (`next('route')`). ۴۰۰/۴۰۹/۲۰۰ یعنی پرچم پس از سنجشِ
 * بدنه یا اصلاً خوانده نمی‌شود — همان باگِ `pay-choice` (۴۰۰ `bad_payment_choice` با تنظیمِ خاموش).
 *
 * درِ بی‌پرچم (پایهٔ ویترین، حسابِ مشترکِ برنامهٔ خرید، پاسخِ خنثای «بی ناحیه/بی بازه») در `UNFLAGGED` با دلیل؛ درِ تازه‌ای که نه
 * این‌جاست و نه با پرچمِ خاموش ۴۰۴ می‌دهد ⇒ سرخ: یا پرچمش را پیش از هر سنجش بگذار، یا با دلیل به فهرست بیفزا.
 */
process.env.SYNC_V3_ENABLED = '1';
process.env.RATE_STORE_CHECKOUT_MAX = '100000';
process.env.RATE_VERIFY_MAX = '100000';
process.env.OTP_RESEND_SECONDS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const inv = require('./route-inventory');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const { KEYS: DEFAULT_ON } = require('../src/lib/flag-defaults');

const ROUTES = nodePath.join(__dirname, '..', 'src', 'routes');
const baseOf = (file) => (fs.readFileSync(nodePath.join(ROUTES, file), 'utf8').match(/const base = '([^']+)'/) || [])[1] || '';

/** درهایی که پرچمِ اختیاری ندارند — پاسخشان هر چه باشد این نگهبان نمی‌سنجد (آزمونِ خودشان می‌سنجد). */
const UNFLAGGED = Object.freeze({
  'GET /store/:slug/highlights': 'پایهٔ ویترین',
  'GET /store/:slug/delivery': 'بی پرچمِ ناحیه پاسخِ خنثای «بی ناحیه» (CHK-07/10)',
  'GET /store/:slug/slots': 'بی پرچمِ بازه پاسخِ خنثای «بی بازه» (CHK-12)',
  'GET /store/:slug/products': 'پایهٔ ویترین',
  'POST /store/:slug/verify/start': 'پایهٔ ویترین',
  'POST /store/:slug/verify/check': 'پایهٔ ویترین',
  'POST /store/:slug/checkout': 'پایهٔ ویترین',
  'GET /store/:slug/orders': 'پایهٔ ویترین',
  'GET /store/:slug/orders/:token': 'پایهٔ ویترین',
  'POST /store/:slug/orders/:token/pay': 'پایهٔ ویترین (حساب‌پی سدِ خودش را دارد)',
  'GET /store/:slug/orders/:token/reorder': 'پایهٔ ویترین',
  'POST /store/:slug/orders/:token/cancel': 'پایهٔ ویترین',
  //  حسابِ مشتری درِ مشترکِ برنامهٔ خرید است؛ `shop.storefront.web_account` فقط رابطِ وب را نشان می‌دهد
  'POST /store/:slug/account': 'حسابِ مشترکِ برنامهٔ خرید',
  'GET /store/:slug/account': 'حسابِ مشترکِ برنامهٔ خرید',
  'PUT /store/:slug/account': 'حسابِ مشترکِ برنامهٔ خرید',
  'DELETE /store/:slug/account': 'حسابِ مشترکِ برنامهٔ خرید',
  'POST /store/:slug/account/logout': 'حسابِ مشترکِ برنامهٔ خرید',
  'POST /store/:slug/account/logout-all': 'حسابِ مشترکِ برنامهٔ خرید',
  'GET /store/:slug/account/orders': 'حسابِ مشترکِ برنامهٔ خرید',
  'GET /store/:slug/account/addresses': 'دفترِ نشانیِ حساب (CRM-09)',
  'POST /store/:slug/account/addresses': 'دفترِ نشانیِ حساب (CRM-09)',
  'PUT /store/:slug/account/addresses/:id': 'دفترِ نشانیِ حساب (CRM-09)',
  'DELETE /store/:slug/account/addresses/:id': 'دفترِ نشانیِ حساب (CRM-09)',
  //  CHK-24: پرچم پیش از بدنه سنجیده می‌شود ولی پاسخِ مستند ۴۰۳ `delivery_prefs_disabled` است (همان درِ کارمند، پیامِ برنامهٔ خرید)
  'POST /store/:slug/orders/:token/delivery': '۴۰۳ ِ مستندِ CHK-24 (پرچم پیش از بدنه)',
});
/** درهای ویران‌گر (حساب، نشست، سفارش) آخر — تا درهای دیگر با همان نشست و توکن زده شوند */
const LAST = /^(DELETE \/store\/:slug\/account|POST \/store\/:slug\/account\/(logout|logout-all|erase)|POST .*\/cancel)$/;
const BAD = { method: 7, phone: [], text: {}, kind: 9, lines: 'x', rating: 'z', items: 3, fulfillment: 8, delivery: 'x', subscription: 1 };

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const flag = (scope, key, value = true) => h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,'gate-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  admin = (await h.post('/api/admin/login', { username: 'gate-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function ticketOf(slug, phone) {
  const v = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(v.status, 200, JSON.stringify(v.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: v.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

test('⛔ هر درِ پرچم‌دارِ ویترین با پرچمِ خاموش و بدنهٔ بد/تهی ⇒ ۴۰۴ یا همان درِ ناشناخته (نه ۴۰۰ پیش از پرچم)', async () => {
  const u = await h.newUser('صاحبِ نگهبان', 'shop');
  const token = u.accessToken;
  const shopId = (await h.post('/api/shop', { name: 'دکانِ نگهبان' }, { token })).body.shop.id;
  for (const k of ['shop.orders', 'shop.storefront']) await flag(shopId, k);
  for (const k of DEFAULT_ON) await flag(shopId, k, false);
  const slug = `gate-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترین', contactPhone: '0700000001', payAfterAccept: false }, { token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const dev = `dev-${uniq()}`;
  const p = await h.api('POST', '/api/sync/v1/push', { token, headers: { 'X-App': 'shop', 'X-Device': dev }, body: { device_id: dev, schema_version: 3, ops: [
    { op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert', fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی', active: true } }] } });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token })).status, 200);
  const phone = '0796610001';
  const ticket = await ticketOf(slug, phone);
  const ck = await h.post(`/api/store/${slug}/checkout`, { fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }] },
    { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(ck.status, 201, JSON.stringify(ck.body));
  const acc = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': await ticketOf(slug, phone) } });
  assert.equal(acc.status, 201, JSON.stringify(acc.body));
  const headers = () => ({ 'Idempotency-Key': `k-${newId('k')}`, 'X-Store-Ticket': ticket, 'X-Store-Session': acc.body.session });

  //  پاسخِ درِ ناشناخته همین نشانی (هر روش) — `next('route')` ِ پرچمِ خاموش باید دقیقاً همین باشد
  const unknown = {};
  for (const m of ['GET', 'POST', 'PUT', 'PATCH', 'DELETE']) {
    resetAll();
    const r = await h.api(m, `/api/store/${slug}/zz-no-door`, { body: m === 'GET' ? null : {}, headers: headers() });
    unknown[m] = `${r.status} ${r.body?.error?.code || ''}`;
  }

  const rs = inv.routes('/store')
    .map((r) => (r.path.includes('${base}') ? { ...r, path: r.path.replace('${base}', baseOf(r.file)) } : r))
    .map((r) => ({ ...r, key: `${r.method} ${r.path}` }))
    .filter((r) => r.path.startsWith('/store/:slug/'));
  assert.ok(rs.length > 80, `فهرستِ درها از سورس خوانده شد (${rs.length})`);
  for (const k of Object.keys(UNFLAGGED)) assert.ok(rs.some((r) => r.key === k), `UNFLAGGED ِ کهنه: ${k} دیگر نیست`);
  rs.sort((a, b) => Number(LAST.test(a.key)) - Number(LAST.test(b.key)));

  const bad = [];
  let probed = 0;
  for (const r of rs) {
    if (UNFLAGGED[r.key]) continue;
    probed++;
    const url = `/api${r.path.replace(':slug', slug).replace(/:tok(en)?\b/, r.path.includes('/trust/') ? 'x1' : ck.body.order.token).replace(/:[A-Za-z_]+/g, 'x1')}`;
    for (const body of [BAD, {}]) {
      resetAll();
      const get = r.method === 'GET';
      const res = await h.api(r.method, get && body === BAD ? `${url}?limit=abc&q=${'x'.repeat(300)}` : url, { body: get ? null : body, headers: headers() });
      const got = `${res.status} ${res.body?.error?.code || ''}`;
      if (res.status !== 404 && got !== unknown[r.method]) bad.push(`${r.key} (${r.file}) ⇐ ${got} با بدنهٔ ${body === BAD ? 'بد' : 'تهی'}`);
    }
  }
  assert.ok(probed > 50, `درهای پرچم‌دار سنجیده شدند (${probed})`);
  assert.deepEqual(bad, [], `درِ پرچم‌دار پیش از پرچم پاسخ داد (پرچم را پیش از هر سنجش بگذار، یا درِ بی‌پرچم را با دلیل به UNFLAGGED بیفزا):\n${bad.join('\n')}`);
});
