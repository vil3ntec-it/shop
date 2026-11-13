'use strict';
/**
 * بازبینیِ امنیتیِ دستهٔ ۳۹ (docs/SECURITY-AUDIT-fa.md، «بازبینیِ ۳۹»): پوشِ وب ِ مشتریِ ویترین (`lib/store-push.js`).
 * هر آزمون پیش از رفع سرخ بود؛ روی PGlite و PostgreSQL. هرگز شبکه: `useTransport`.
 *
 *   SR39-1  TEN-03: «باطل کردنِ لینک» که میانِ سنجشِ لینک و قفلِ سفارش (اشتراک) یا میانِ سنجشِ اشتراک‌ها و ساختنِ نشانیِ لمس
 *           (فرستادن) بنشیند، لینکِ **تازه** را با پوش به دارندهٔ لینکِ باطل می‌داد.
 *   SR39-2  خطای ساختنِ درخواست روی خودِ سرور (VAPID_SUBJECT ِ بی mailto:) «اشتراکِ مرده» شمرده می‌شد ⇒ همهٔ اشتراک‌ها پاک.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.RATE_STORE_FEEDBACK_MAX = '500';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const ece = require('http_ece');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const N = require('../src/lib/customer-notify');
const P = require('../src/lib/store-push');
const sf = require('../src/lib/storefront');
const orders = require('../src/lib/orders');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
let phoneSeq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { P.useTransport(null); N.setProviders(null); await h.stop(); });
test.beforeEach(async () => {
  resetAll(); N.setProviders(null); P.useTransport(null);
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE status IN ('pending','sending')`);
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/flags', { scope, key, value, reason: 'آزمونِ بازبینیِ ۳۹' }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ زنده با ویترین، اعلانِ مشتری، پوشِ وب و لینکِ باطل‌شدنی (TEN-03) */
async function liveShop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const phone = `0792${String(100000 + (++phoneSeq)).slice(-6)}`;
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `sr-${uniq()}`, phone };
  for (const k of ['shop.orders', 'shop.storefront', N.FLAG_KEY, P.FLAG, sf.LINK_FLAG]) await setFlag(s.shopId, k, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: s.token, headers: { 'X-App': 'shop', 'X-Device': 'dev-sr39' },
    body: { device_id: 'dev-sr39', schema_version: 2, ops: [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی' } }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function checkout(s) {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(s.phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(s.phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone: s.phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }], lang: 'fa',
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}
const go = async (s, o, to) => {
  const r = await h.post(`/api/shop/orders/${o.id}/transition`, { to }, { token: s.token });
  assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
};
const on = (s, o, body) => h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}/push`, body);
const subsOf = (orderId) => many('SELECT * FROM store_push_subs WHERE order_id=$1', [orderId]);
const code = (r) => (r.body && r.body.error && r.body.error.code) || '';
const drain = async (at = now()) => { while (await N.tick(at)) { /* تا خالی */ } };

function browser() {
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.generateKeys();
  const auth = crypto.randomBytes(16).toString('base64url');
  const sub = { endpoint: `https://fcm.googleapis.com/fcm/send/sr39-${uniq()}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth } };
  return { sub, open: (body) => JSON.parse(ece.decrypt(Buffer.from(body), { version: 'aes128gcm', privateKey: ecdh, authSecret: auth }).toString('utf8')) };
}
function service() {
  const calls = [];
  P.useTransport(async (req) => { calls.push(req); return { status: 201 }; });
  return calls;
}

/** هر لینکِ پیگیری‌ای که پوش به این مرورگر داد ⇒ پاسخِ نمای پیگیری با همان لینک */
async function linksSeen(s, b, calls) {
  const out = [];
  for (const c of calls.filter((x) => x.endpoint === b.sub.endpoint)) {
    const m = b.open(c.body);
    const token = decodeURIComponent(String(m.url).split('#/o/')[1] || '');
    out.push((await h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(token)}`)).status);
  }
  return out;
}

/* ══════════════════════════════════════════════════════════════════
   SR39-1: باطل کردنِ لینک وسطِ اشتراک یا وسطِ فرستادن
   ══════════════════════════════════════════════════════════════════ */

test('SR39-1a باطل کردنِ لینک میانِ سنجشِ لینک و قفلِ سفارش ⇒ ۴۱۰ و هیچ اشتراک؛ دارندهٔ لینکِ باطل لینکِ تازه نمی‌گیرد', async () => {
  const s = await liveShop('اشتراکِ هم‌زمان');
  const o = await checkout(s);
  const calls = service();
  await drain();
  const leaked = browser();
  //  دکان درست پس از سنجشِ لینکِ لورفته (و پیش از قفلِ ردیفِ سفارش) «باطل کردن» را می‌زند
  const real = sf.orderIdOfLink;
  sf.orderIdOfLink = async (store, token) => {
    const id = await real(store, token);
    sf.orderIdOfLink = real;
    await sf.revokeLink(store.shop_id, id);
    return id;
  };
  let r;
  try { r = await on(s, o, { subscription: leaked.sub }); } finally { sf.orderIdOfLink = real; }
  await go(s, o, 'accepted');
  await drain();
  assert.deepEqual((await linksSeen(s, leaked, calls)).filter((st) => st === 200), [], 'لینکِ تازهٔ معتبر به دارندهٔ لینکِ باطل نرسید');
  assert.equal(r.status, 410, JSON.stringify(r.body));
  assert.equal(code(r), 'link_revoked');
  assert.equal((await subsOf(o.id)).length, 0, 'هیچ اشتراکی با نسلِ تازه');
});

test('SR39-1b باطل کردنِ لینک وسطِ ساختنِ پوش ⇒ نشانیِ لمس همان نسلِ سنجیده (باطل)، نه لینکِ تازه', async () => {
  const s = await liveShop('فرستادنِ هم‌زمان');
  const o = await checkout(s);
  const calls = service();
  await drain();
  const leaked = browser();
  assert.equal((await on(s, o, { subscription: leaked.sub })).status, 200);
  await drain();
  const before = calls.length;
  assert.ok(before >= 1, 'پیامِ «ثبت شد» پیش از باطل کردن رسید');
  await go(s, o, 'accepted');
  //  دکان «باطل کردن» را درست وقتی می‌زند که پوشِ همین گذار اشتراک‌ها را سنجیده و هنوز نشانیِ لمس را نساخته است
  const real = orders.flagOf;
  let fired = false;
  orders.flagOf = async (shopId, key, run) => {
    if (!fired && key === sf.LINK_FLAG && /store-push\.js/.test(new Error().stack)) {
      fired = true;
      await sf.revokeLink(s.shopId, o.id);
    }
    return real(shopId, key, run);
  };
  try { await drain(); } finally { orders.flagOf = real; }
  assert.ok(fired, 'همان درهم‌رفتگی رخ داد');
  const seen = await linksSeen(s, leaked, calls.slice(before));
  assert.ok(seen.length >= 1, 'پیامِ در راه رسید');
  assert.deepEqual(seen.filter((st) => st === 200), [], 'لینکِ تازهٔ معتبر به دارندهٔ لینکِ باطل نرسید');
});

/* ══════════════════════════════════════════════════════════════════
   SR39-2: خطای خودِ سرور اشتراکِ مشتری را پاک نمی‌کند
   ══════════════════════════════════════════════════════════════════ */

test('SR39-2 VAPID_SUBJECT ِ نادرست (بی mailto:) ⇒ اشتراکِ مرورگر می‌ماند، نه «مرده» و پاک', async () => {
  const s = await liveShop('موضوعِ نادرست');
  const o = await checkout(s);
  const calls = service();
  await drain();
  const realNow = Date.now;
  const saved = process.env.VAPID_SUBJECT;
  try {
    process.env.VAPID_SUBJECT = 'support@example.com';   //  غلطِ رایجِ تنظیم: بی `mailto:`
    Date.now = () => realNow() + 2 * 60_000;            //  گذشتنِ حافظهٔ یک‌دقیقه‌ایِ `vapid()`
    assert.equal((await on(s, o, { subscription: browser().sub })).status, 200);
    await drain();
  } finally {
    if (saved === undefined) delete process.env.VAPID_SUBJECT; else process.env.VAPID_SUBJECT = saved;
    Date.now = () => realNow() + 4 * 60_000;
    await P.vapid();                                     //  حافظه دوباره با موضوعِ درست
    Date.now = realNow;
  }
  assert.equal(calls.length, 0, 'درخواستی ساخته نشد');
  assert.equal((await subsOf(o.id)).length, 1, 'اشتراکِ مشتری پاک نشد');
});
