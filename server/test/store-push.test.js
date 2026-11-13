'use strict';
/**
 * پوشِ وب ِ مشتریِ ویترین — کانالِ `push` ِ اعلانِ مشتری برای سفارشِ وب (`lib/store-push.js`، `routes/store-push.js`،
 * مهاجرتِ ۴۷۰؛ CHK-20 پیامِ تأیید، NTF-01، ORD-16؛ §۳.۱۲ «مشتری: لینکِ پیگیری و پوشِ PWA …»).
 *
 * آن‌چه این پرونده قفل می‌کند (هرگز شبکه: `useTransport`؛ پیام با کلیدِ خصوصیِ «مرورگرِ» آزمون باز می‌شود):
 *   ۱) ⛔ پیش‌فرض خاموش: بی `shop.storefront.web_push` یا بی `shop.customer_notify` ⇒ درِ اشتراک ۴۰۴، نمای پیگیری بی
 *      `push`، هیچ ردیف و کانالِ `push` همان `not_configured`.
 *   ۲) ⛔ SSRF و ورودی: فقط https ِ سرویسِ پوشِ مرورگرها؛ کلیدِ مرورگر نقطهٔ واقعیِ P-256 و auth ِ ۱۶ بایتی؛ صفحه فقط
 *      همان ویترین. نادرست ⇒ ۴۰۰ و هیچ ردیف.
 *   ۳) سرتاسر: «ثبت شد» پیش از «خبرم کن» صف و `no_channel` ⇒ «خبرم کن» همان پیام را یک بار و فقط از پوش می‌فرستد؛ پیام
 *      رمزِ aes128gcm با VAPID ِ همین سرور، متنِ NTF-03 بی شماره و نام، نشانیِ لمس فقط مسیرِ همین ویترین؛ گذارهای بعدی
 *      مستقیم؛ رویدادِ فوری `Urgency: high`؛ پیامِ پایانی ⇒ اشتراک پاک.
 *   ۴) یکتا و محدود: همان مرورگر ⇒ یک ردیف؛ حداکثر ۳ مرورگر حتی با درخواستِ هم‌زمان؛ «خاموش» فقط همان.
 *   ۵) ⛔ کارِ مشتری هرگز پیامکِ پولی برنمی‌انگیزد: فرستادنِ دوباره فقط از پوش.
 *   ۶) پاسخِ سرویس: ۴۱۰ ⇒ اشتراک پاک و زنجیره ادامه؛ ۵۰۰ ⇒ دوباره با همان کلید و همان Topic، کانالِ بعدی نه.
 *   ۷) TEN-03: لینکِ باطل‌شده ⇒ اشتراکِ آن هیچ پیامی نمی‌گیرد و پاک می‌شود. دکانِ دیگر/توکنِ نادرست ⇒ ۴۰۴.
 *   ۸) SEC-10: حذفِ دادهٔ مشتری اشتراک‌های همان سفارش‌ها را پاک می‌کند (`pushSubs`).
 *   ۹) کلیدِ VAPID یک بار ساخته می‌شود، پایدار است، جفتِ خراب از نو؛ کلیدِ خصوصی در پشتیبان مهر می‌شود.
 *  ۱۰) ORD-16: «فرستادنِ دوباره» ِ دکان پیامِ ناموفق را از پوش می‌رساند.
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
  //  جداسازیِ آزمون‌ها: ردیف‌های هنوز در صفِ آزمونِ پیشین در `drain` ِ این آزمون نمی‌آیند
  await query(`UPDATE customer_notify_outbox SET status='cancelled' WHERE status IN ('pending','sending')`);
});

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const syncPush = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-push' }, body: { device_id: 'dev-push', schema_version: 2, ops },
});

async function liveShop(label, { notify = true, push = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  //  شمارهٔ جدا برای هر دکان: سقفِ هر مشتری (`PER_HOUR`) میانِ آزمون‌ها پر نمی‌شود
  const phone = `0791${String(100000 + (++phoneSeq)).slice(-6)}`;
  const s = { token: u.accessToken, shopId: made.body.shop.id, slug: `wp-${uniq()}`, title: `ویترینِ ${label}`, phone };
  for (const k of ['shop.orders', 'shop.storefront']) await setFlag(s.shopId, k, true);
  if (notify) await setFlag(s.shopId, N.FLAG_KEY, true);
  if (push) await setFlag(s.shopId, P.FLAG, true);
  const put = await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: s.title, contactPhone: '0700000001', acceptPickup: true }, { token: s.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await syncPush(s.token, [{ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'بسته', salePrice: 120, category: 'خوراکی' } }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

const NAME = 'مشتریِ پوش';
const tickets = new Map();
async function ticketFor(s) {
  if (tickets.has(s.slug)) return tickets.get(s.slug);
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(s.phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(s.phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(s.slug, c.body.ticket);
  return c.body.ticket;
}
/** سفارشِ ویترین (برداشت از دکان) ⇒ `{id, token}` */
async function checkout(s) {
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: NAME, phone: s.phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }], lang: 'fa',
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': await ticketFor(s) } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token, no: r.body.order.orderNo };
}
const go = async (s, o, to, extra = {}) => {
  const r = await h.post(`/api/shop/orders/${o.id}/transition`, { to, ...extra }, { token: s.token });
  assert.equal(r.status, 200, `${to}: ${JSON.stringify(r.body)}`);
};
const view = (s, o) => h.get(`/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}`);
const on = (s, o, body) => h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}/push`, body);
const off = (s, o, body) => h.api('DELETE', `/api/store/${s.slug}/orders/${encodeURIComponent(o.token)}/push`, { body });
const rowOf = (orderId, event) => one('SELECT * FROM customer_notify_outbox WHERE order_id=$1 AND event=$2', [orderId, event]);
const subsOf = (orderId) => many('SELECT * FROM store_push_subs WHERE order_id=$1 ORDER BY created_at, id', [orderId]);
const code = (r) => (r.body && r.body.error && r.body.error.code) || '';
/** همهٔ ردیف‌های سررسیده — ساعتِ ساختگی برای گذشتنِ درنگِ تلاشِ دوباره */
const drain = async (at = now()) => { while (await N.tick(at)) { /* تا خالی */ } };

/** «مرورگرِ» آزمون: کلیدِ P-256 ِ واقعی + auth ِ ۱۶ بایتی؛ `open(body)` پیامِ رمزشده را باز می‌کند */
function browser(host = 'fcm.googleapis.com') {
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.generateKeys();
  const auth = crypto.randomBytes(16).toString('base64url');
  const sub = { endpoint: `https://${host}/fcm/send/t-${uniq()}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth } };
  return { sub, open: (body) => JSON.parse(ece.decrypt(Buffer.from(body), { version: 'aes128gcm', privateKey: ecdh, authSecret: auth }).toString('utf8')) };
}
/** سرویسِ پوشِ ساختگی: هر درخواست را نگه می‌دارد؛ `script` وضعیتِ پاسخ‌های بعدی (پیش‌فرض ۲۰۱) */
function service(script = []) {
  const calls = [];
  P.useTransport(async (req) => { calls.push(req); return { status: script.length ? script.shift() : 201 }; });
  return calls;
}
/** سرویس‌دهندهٔ جعلیِ کانالِ دیگر (همان شکلِ customer-notify.test.js) */
function fake(script = []) {
  const calls = [];
  return { calls, send: async (msg) => { calls.push(msg); return script.length ? script.shift() : { status: 'accepted', ref: `r-${calls.length}` }; } };
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ پرچمِ خاموش (هر کدام از دو پرچم): نمای پیگیری بی `push`، درِ اشتراک ۴۰۴، هیچ ردیف و پوش همان not_configured', async () => {
  const calls = service();
  for (const [label, opts] of [['بی‌پوش', { push: false }], ['بی‌اعلان', { notify: false }]]) {
    const s = await liveShop(label, opts);
    const o = await checkout(s);
    const v = await view(s, o);
    assert.equal(v.status, 200, JSON.stringify(v.body));
    assert.equal('push' in v.body.order, false, `${label}: نمای پیگیری بی کلیدِ push`);
    const r = await on(s, o, { subscription: browser().sub });
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(code(r), 'order_not_found', 'همان «سفارش پیدا نشد»، بی نشتِ پرچم');
    assert.equal(code(await off(s, o, { endpoint: browser().sub.endpoint })), 'order_not_found');
    assert.equal((await subsOf(o.id)).length, 0);
    if (opts.push === false) {
      await drain();
      const row = await rowOf(o.id, 'order_received');
      assert.equal(row.status, 'no_channel');
      assert.deepEqual(row.tried.map((t) => [t.channel, t.result])[0], ['push', 'not_configured'], 'بی پرچمِ پوش: همان رفتارِ پیشین');
      assert.equal(row.only_channel, '');
    }
  }
  assert.equal(calls.length, 0, 'هیچ درخواستی به سرویسِ پوش');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) SSRF و ورودی
   ══════════════════════════════════════════════════════════════════ */

test('⛔ SSRF: فقط https ِ سرویسِ پوشِ مرورگرها؛ کلیدِ نادرست یا صفحهٔ دیگر ⇒ ۴۰۰ و هیچ ردیف', async () => {
  const s = await liveShop('سنجش');
  const o = await checkout(s);
  const good = browser().sub;
  const BAD_ENDPOINTS = [
    'http://fcm.googleapis.com/fcm/send/x', 'https://evil.example/fcm/send/x', 'https://fcm.googleapis.com.evil.example/x',
    'https://127.0.0.1/x', 'https://localhost/x', 'https://[::1]/x', 'https://169.254.169.254/latest', 'https://fcm.googleapis.com:8443/x',
    'https://user:pass@fcm.googleapis.com/x', 'https://notify.windows.com/x', 'file:///etc/passwd', 'https://fcm.googleapis.com/a b',
    `https://fcm.googleapis.com/${'x'.repeat(1100)}`, 42, '',
  ];
  for (const endpoint of BAD_ENDPOINTS) {
    const r = await on(s, o, { subscription: { ...good, endpoint } });
    assert.equal(r.status, 400, `${String(endpoint).slice(0, 60)} ⇒ ${r.status}`);
    assert.equal(code(r), 'bad_subscription');
  }
  //  کلیدِ مرورگر: طولِ نادرست، نقطه‌ای که روی منحنی نیست، auth ِ کوتاه/بلند
  const notOnCurve = Buffer.alloc(65, 7); notOnCurve[0] = 4;
  for (const keys of [
    { ...good.keys, p256dh: good.keys.p256dh.slice(0, 40) }, { ...good.keys, p256dh: notOnCurve.toString('base64url') },
    { ...good.keys, auth: crypto.randomBytes(8).toString('base64url') }, { ...good.keys, auth: crypto.randomBytes(32).toString('base64url') },
    { p256dh: good.keys.p256dh }, {},
  ]) {
    const r = await on(s, o, { subscription: { endpoint: good.endpoint, keys } });
    assert.equal(code(r), 'bad_subscription', JSON.stringify(keys).slice(0, 80));
  }
  for (const page of ['/admin/', `/store/${s.slug}-x`, 'https://evil.example/', '//evil.example']) {
    assert.equal(code(await on(s, o, { subscription: good, page })), 'bad_page', page);
  }
  assert.equal(code(await on(s, o, {})), 'bad_subscription');
  assert.equal((await subsOf(o.id)).length, 0, 'هیچ ردیفی');
  //  همان مرورگرِ درست ⇒ پذیرفته، با میزبان‌های دیگرِ فهرست
  for (const host of ['fcm.googleapis.com', 'updates.push.services.mozilla.com', 'web.push.apple.com']) {
    const r = await on(s, o, { subscription: browser(host).sub, page: `/store/${s.slug}` });
    assert.equal(r.status, 200, `${host}: ${JSON.stringify(r.body)}`);
  }
  assert.equal(P.endpointOk('https://wns2-par02p.notify.windows.com/w/?token=x'), true);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) سرتاسر: ثبت ⇒ «خبرم کن» ⇒ همان «ثبت شد» فقط از پوش ⇒ گذارها
   ══════════════════════════════════════════════════════════════════ */

test('سرتاسر: «ثبت شد» ِ ماندهٔ پیش از «خبرم کن» یک بار و فقط از پوش؛ پیامِ رمزشده بی شماره و نام؛ گذارهای بعدی مستقیم', async () => {
  const s = await liveShop('سرتاسر');
  const o = await checkout(s);
  const calls = service();
  await drain();
  let row = await rowOf(o.id, 'order_received');
  assert.equal(row.status, 'no_channel', 'پیش از «خبرم کن» هیچ مرورگری نیست');
  assert.deepEqual(row.tried.map((t) => [t.channel, t.result])[0], ['push', 'rejected'], 'کانالِ پوش روشن، ولی برای این سفارش اشتراکی نیست');
  assert.equal(calls.length, 0);

  const v = await view(s, o);
  const key = v.body.order.push && v.body.order.push.key;
  assert.match(key || '', /^[A-Za-z0-9_-]{87}$/, 'کلیدِ عمومیِ VAPID (۶۵ بایت)');
  assert.equal((await view(s, o)).body.order.push.key, key, 'کلید پایدار است');

  const b = browser();
  const r = await on(s, o, { subscription: b.sub, page: `/store/${s.slug}` });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { push: { on: true } });
  row = await rowOf(o.id, 'order_received');
  assert.equal(row.status, 'pending', 'همان ردیف دوباره در صف');
  assert.equal(row.only_channel, 'push');

  await drain();
  assert.equal(calls.length, 1, 'یک درخواست به سرویسِ پوش');
  const req = calls[0];
  assert.equal(req.endpoint, b.sub.endpoint, 'فقط همان نشانیِ مرورگر');
  assert.equal(req.method, 'POST');
  assert.equal(req.headers['Content-Encoding'], 'aes128gcm');
  assert.ok(Number(req.headers.TTL) > 0 && Number(req.headers.TTL) <= 6 * 3600);
  assert.match(req.headers.Topic, /^[A-Za-z0-9_-]{1,32}$/);
  assert.equal(req.headers.Urgency, 'normal', '«ثبت شد» فوری نیست');
  assert.match(req.headers.Authorization, new RegExp(`^vapid t=[^,]+, k=${key}$`), 'VAPID با کلیدِ همین سرور');
  const jwt = JSON.parse(Buffer.from(req.headers.Authorization.split('t=')[1].split(',')[0].split('.')[1], 'base64url').toString('utf8'));
  assert.equal(jwt.aud, 'https://fcm.googleapis.com');
  assert.ok(!('content-length' in Object.fromEntries(Object.entries(req.headers).map(([k, x]) => [k.toLowerCase(), x]))));
  const msg = b.open(req.body);
  assert.equal(msg.title, s.title);
  assert.equal(msg.body, N.render('order_received', 'fa', { no: o.no, shop: s.title }), 'همان متنِ NTF-03');
  assert.equal(msg.url, `/store/${s.slug}#/o/${encodeURIComponent(o.token)}`, 'لمس ⇒ پیگیری روی همین مسیرِ ویترین');
  assert.match(msg.tag, /^o-[A-Za-z0-9_-]{12}$/);
  //  شناسهٔ سفارش فقط درونِ همان توکنِ پیگیریِ نشانی (همان لینکِ «سفارشِ من»)، نه در متن یا برچسب
  const text = JSON.stringify({ ...msg, url: '' });
  for (const pii of [s.phone, s.phone.slice(1), NAME, o.id]) assert.ok(!text.includes(pii), `بی دادهٔ شخصی/درونی: ${pii}`);
  assert.ok(!JSON.stringify(msg).includes(s.phone.slice(1)), 'شماره هیچ‌جا، حتی در نشانی');
  assert.ok(!Buffer.from(req.body).toString('latin1').includes(s.title), 'بدنه رمزشده است');
  row = await rowOf(o.id, 'order_received');
  assert.equal(row.status, 'sent');
  assert.equal(row.channel, 'push');
  assert.deepEqual(row.tried.map((t) => [t.channel, t.result]), [['push', 'accepted']], 'فقط پوش آزموده شد');
  assert.ok(Number((await subsOf(o.id))[0].last_ok_at) > 0);

  //  نمای دکان: پوش برای این دکان «وصل»
  const nv = await h.get(`/api/shop/orders/${o.id}/notices`, { token: s.token });
  assert.equal(nv.status, 200, JSON.stringify(nv.body));
  assert.equal(nv.body.channels.push, 'configured');
  assert.equal(nv.body.items[0].channel, 'push');

  //  گذارِ بعدی مستقیم، با Topic ِ تازه
  await go(s, o, 'accepted');
  await drain();
  assert.equal(calls.length, 2);
  assert.equal(b.open(calls[1].body).body, N.render('order_accepted', 'fa', { no: o.no, shop: s.title }));
  assert.notEqual(calls[1].headers.Topic, calls[0].headers.Topic);
  assert.equal((await rowOf(o.id, 'order_accepted')).only_channel, '', 'گذارِ عادی: همان زنجیرهٔ کامل');
  //  رویدادِ فوری (آماده برای برداشت) ⇒ Urgency: high
  await go(s, o, 'picking'); await go(s, o, 'packed'); await go(s, o, 'ready_for_pickup');
  await drain();
  assert.equal(calls.length, 3);
  assert.equal(calls[2].headers.Urgency, 'high');
  //  پیامِ پایانی (برداشته شد) ⇒ اشتراک پاک؛ نمای پیگیری بی push؛ اشتراکِ تازه ۴۰۹
  await go(s, o, 'picked_up');
  await drain();
  assert.equal(calls.length, 4);
  assert.equal((await subsOf(o.id)).length, 0, 'سفارشِ بی خبرِ دیگر ⇒ اشتراک پاک');
  assert.equal('push' in (await view(s, o)).body.order, false);
  const late = await on(s, o, { subscription: browser().sub });
  assert.equal(late.status, 409);
  assert.equal(code(late), 'order_closed');
});

test('نشانیِ خودِ دکان: page="/" ⇒ لمس به `/#/o/…`', async () => {
  const s = await liveShop('میزبان');
  const o = await checkout(s);
  const calls = service();
  await drain();
  const b = browser();
  assert.equal((await on(s, o, { subscription: b.sub, page: '/' })).status, 200);
  await drain();
  assert.equal(b.open(calls[0].body).url, `/#/o/${encodeURIComponent(o.token)}`);
});

/* ══════════════════════════════════════════════════════════════════
   ۴) یکتا و محدود
   ══════════════════════════════════════════════════════════════════ */

test('همان مرورگر ⇒ یک ردیف؛ حداکثر ۳ مرورگر (هم‌زمان هم)؛ «خاموش» فقط همان؛ «خبرم کن» ِ دوباره پیام را دوباره نمی‌فرستد', async () => {
  const s = await liveShop('سقف');
  const o = await checkout(s);
  const calls = service();
  await drain();
  const a = browser();
  assert.equal((await on(s, o, { subscription: a.sub })).status, 200);
  await drain();
  assert.equal(calls.length, 1);
  //  همان مرورگر با کلیدِ تازه ⇒ همان ردیف (کلید عوض)، و پیامِ رفته دوباره نمی‌رود
  const a2 = browser(); a2.sub.endpoint = a.sub.endpoint;
  assert.equal((await on(s, o, { subscription: a2.sub })).status, 200);
  const subs = await subsOf(o.id);
  assert.equal(subs.length, 1);
  assert.equal(subs[0].p256dh, a2.sub.keys.p256dh);
  await drain();
  assert.equal(calls.length, 1, 'پیامِ رفته دوباره نمی‌رود');

  //  سقف با درخواستِ هم‌زمان
  const o2 = await checkout(s);
  await drain();
  const many5 = Array.from({ length: 5 }, () => browser());
  const out = await Promise.all(many5.map((x) => on(s, o2, { subscription: x.sub })));
  assert.equal(out.filter((r) => r.status === 200).length, P.MAX_SUBS, out.map((r) => r.status).join(','));
  assert.ok(out.filter((r) => r.status === 409).every((r) => code(r) === 'push_limit'));
  assert.equal((await subsOf(o2.id)).length, P.MAX_SUBS);
  //  «خاموش» فقط همان مرورگر؛ نبود ⇒ همان پاسخ
  const kept = (await subsOf(o2.id)).map((x) => x.endpoint);
  const r = await off(s, o2, { endpoint: kept[0] });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.deepEqual(r.body, { push: { on: false } });
  assert.deepEqual((await subsOf(o2.id)).map((x) => x.endpoint), kept.slice(1));
  assert.equal((await off(s, o2, { endpoint: browser().sub.endpoint })).status, 200);
  assert.equal(code(await off(s, o2, { endpoint: 'https://evil.example/x' })), 'bad_subscription');
  //  «خاموش» ِ مرورگرِ سفارشِ دیگر روی این سفارش اثر ندارد
  assert.equal((await off(s, o2, { endpoint: a.sub.endpoint })).status, 200);
  assert.equal((await subsOf(o.id)).length, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) کارِ مشتری هرگز پیامکِ پولی برنمی‌انگیزد
   ══════════════════════════════════════════════════════════════════ */

test('⛔ فرستادنِ دوبارهٔ مشتری فقط از پوش: پیامکِ پولی که پیش‌تر رد شده بود دوباره صدا زده نمی‌شود', async () => {
  const s = await liveShop('پیامک');
  const o = await checkout(s);
  const calls = service();
  const sms = fake([{ status: 'rejected', code: 'unreachable' }, { status: 'rejected', code: 'unreachable' }]);
  N.setProviders({ sms });
  await drain();
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup']) await go(s, o, to);
  await drain();
  const before = await rowOf(o.id, 'order_ready');
  assert.equal(before.status, 'no_channel');
  assert.deepEqual(before.tried.map((t) => t.channel), ['push', 'whatsapp', 'telegram', 'sms'], 'رویدادِ فوری: پیامک هم آزموده شد');
  const smsCalls = sms.calls.length;
  assert.ok(smsCalls >= 1);

  const b = browser();
  assert.equal((await on(s, o, { subscription: b.sub })).status, 200);
  await drain();
  const row = await rowOf(o.id, 'order_ready');
  assert.equal(row.status, 'sent');
  assert.deepEqual(row.tried.map((t) => [t.channel, t.result]), [['push', 'accepted']]);
  assert.equal(sms.calls.length, smsCalls, 'پیامک دوباره صدا زده نشد');
  assert.equal(calls.length, 1);
  assert.equal(b.open(calls[0].body).body, N.render('order_ready', 'fa', { no: o.no, shop: s.title }), 'آخرین پیامِ نرفته');
  //  پوش ِ از کار افتاده هم پیامک را برنمی‌انگیزد
  const o2 = await checkout(s);
  await drain();
  service([500, 500, 500, 500, 500]);
  assert.equal((await on(s, o2, { subscription: browser().sub })).status, 200);
  for (let i = 0; i < 6; i++) await drain(now() + i * 10 * 60_000);
  const r2 = await rowOf(o2.id, 'order_received');
  assert.equal(r2.status, 'failed', JSON.stringify(r2));
  assert.ok(r2.tried.every((t) => t.channel === 'push'));
  assert.equal(sms.calls.length, smsCalls);
  //  اشتراکِ مرده (۴۱۰) در فرستادنِ دوبارهٔ مشتری ⇒ همان‌جا تمام: نه واتس‌اپ/تلگرام، نه پیامکِ پولی
  const o3 = await checkout(s);
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup']) await go(s, o3, to);
  sms.calls.length = 0;
  sms.send = async (msg) => { sms.calls.push(msg); return { status: 'rejected', code: 'unreachable' }; };
  await drain();
  assert.equal((await rowOf(o3.id, 'order_ready')).status, 'no_channel');
  const smsBefore = sms.calls.length;
  service([410]);
  assert.equal((await on(s, o3, { subscription: browser().sub })).status, 200);
  await drain();
  const r3 = await rowOf(o3.id, 'order_ready');
  assert.equal(r3.status, 'no_channel', JSON.stringify(r3));
  assert.deepEqual(r3.tried.map((t) => [t.channel, t.result]), [['push', 'rejected']], 'فقط پوش آزموده شد');
  assert.equal(sms.calls.length, smsBefore, 'پیامکِ پولی با کارِ مشتری صدا زده نشد');
  assert.equal((await subsOf(o3.id)).length, 0, 'اشتراکِ مرده پاک شد');
});

test('ORD-16: «فرستادنِ دوباره» ِ دکان پیامِ ناموفق را پس از «خبرم کن» واقعاً از پوش می‌رساند (زنجیرهٔ کامل، نه فقط پوش)', async () => {
  const s = await liveShop('دوباره');
  await setFlag(s.shopId, require('../src/lib/notice-resend').FLAG_KEY, true);
  const o = await checkout(s);
  await drain();
  service([500, 500, 500, 500, 500]);
  const b = browser();
  assert.equal((await on(s, o, { subscription: b.sub })).status, 200);
  for (let i = 0; i < 6; i++) await drain(now() + i * 10 * 60_000);
  const failed = await rowOf(o.id, 'order_received');
  assert.equal(failed.status, 'failed');
  const calls = service();
  const r = await h.post(`/api/shop/orders/${o.id}/notices/${failed.id}/resend`, {}, { token: s.token, headers: { 'Idempotency-Key': `rs-${uniq()}-key` } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const queued = await rowOf(o.id, 'order_received');
  assert.equal(queued.status, 'pending');
  assert.equal(queued.only_channel, '', 'فرستادنِ دوبارهٔ دکان همان زنجیرهٔ کامل است');
  await drain();
  const done = await rowOf(o.id, 'order_received');
  assert.equal(done.status, 'sent');
  assert.equal(done.channel, 'push');
  assert.equal(calls.length, 1);
  assert.equal(b.open(calls[0].body).body, N.render('order_received', 'fa', { no: o.no, shop: s.title }));
});

/* ══════════════════════════════════════════════════════════════════
   ۶) پاسخِ سرویسِ پوش
   ══════════════════════════════════════════════════════════════════ */

test('۴۱۰ ⇒ اشتراک پاک و زنجیره ادامه؛ ۵۰۰ ⇒ دوباره با همان Topic و کانالِ بعدی نه؛ بی سرویس در آزمون ⇒ هیچ درخواستِ شبکه', async () => {
  const s = await liveShop('پاسخ');
  const o = await checkout(s);
  await drain();
  const b = browser();
  const calls = service([500]);
  const wa = fake();
  N.setProviders({ whatsapp: wa });
  assert.equal((await on(s, o, { subscription: b.sub })).status, 200);
  await drain();
  let row = await rowOf(o.id, 'order_received');
  assert.equal(row.status, 'pending', '۵۰۰ ⇒ دوباره');
  assert.equal(wa.calls.length, 0, 'پاسخِ مبهم کانالِ بعدی را صدا نمی‌زند (catch-up هم فقط پوش است)');
  await drain(now() + 10 * 60_000);
  row = await rowOf(o.id, 'order_received');
  assert.equal(row.status, 'sent');
  assert.equal(calls.length, 2);
  assert.equal(calls[1].headers.Topic, calls[0].headers.Topic, 'همان Topic ⇒ سرویس تکرار را جای پیامِ پیشین می‌نشاند');

  //  ۴۱۰ ⇒ اشتراک پاک؛ رویدادِ تازه به کانالِ بعدی (واتس‌اپِ جعلی) می‌رسد
  service([410]);
  await go(s, o, 'accepted');
  await drain();
  assert.equal((await subsOf(o.id)).length, 0, 'اشتراکِ مرده پاک شد');
  row = await rowOf(o.id, 'order_accepted');
  assert.equal(row.status, 'sent');
  assert.deepEqual(row.tried.map((t) => [t.channel, t.result]), [['push', 'rejected'], ['whatsapp', 'accepted']]);

  //  ⛔ بی `useTransport` در NODE_ENV=test: هیچ درخواستی بیرون نمی‌رود (همان «دوباره»)
  P.useTransport(null);
  N.setProviders(null);
  const o2 = await checkout(s);
  await drain();
  assert.equal((await on(s, o2, { subscription: browser().sub })).status, 200);
  await drain();
  assert.equal((await rowOf(o2.id, 'order_received')).status, 'pending');
  assert.equal((await rowOf(o2.id, 'order_received')).last_error, 'push_unavailable');
});

/* ══════════════════════════════════════════════════════════════════
   ۷) TEN-03 و جداسازی
   ══════════════════════════════════════════════════════════════════ */

test('TEN-03: لینکِ باطل‌شده ⇒ اشتراکِ آن پیامی نمی‌گیرد و پاک می‌شود؛ دکانِ دیگر و توکنِ نادرست ⇒ ۴۰۴', async () => {
  const s = await liveShop('باطل');
  const t = await liveShop('دیگر');
  const o = await checkout(s);
  const calls = service();
  await drain();
  assert.equal((await on(s, o, { subscription: browser().sub })).status, 200);
  await drain();
  assert.equal(calls.length, 1);
  //  باطل کردنِ لینک (همان نسلِ track_gen ِ TEN-03 و SEC-10)
  await query('UPDATE online_orders SET track_gen = track_gen + 1 WHERE id=$1', [o.id]);
  await go(s, o, 'accepted');
  await drain();
  assert.equal(calls.length, 1, 'هیچ پیامی به اشتراکِ لینکِ باطل');
  assert.equal((await subsOf(o.id)).length, 0, 'پاک شد');
  assert.equal((await rowOf(o.id, 'order_accepted')).status, 'no_channel');
  //  توکنِ دکانِ دیگر روی این نشانی، توکنِ نادرست
  const other = await checkout(t);
  assert.equal(code(await h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(other.token)}/push`, { subscription: browser().sub })), 'order_not_found');
  assert.equal(code(await h.post(`/api/store/${s.slug}/orders/${encodeURIComponent(h.tampered(o.token))}/push`, { subscription: browser().sub })), 'order_not_found');
  assert.equal((await subsOf(other.id)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۸) SEC-10
   ══════════════════════════════════════════════════════════════════ */

test('SEC-10: حذفِ دادهٔ مشتری اشتراکِ پوشِ همان سفارش را پاک می‌کند (`pushSubs`)', async () => {
  const s = await liveShop('حذف');
  const o = await checkout(s);
  service([500, 500, 500, 500, 500, 500]);
  await drain();
  assert.equal((await on(s, o, { subscription: browser().sub })).status, 200);
  await go(s, o, 'accepted');
  await go(s, o, 'cancelled', { reason: 'out_of_stock' });
  await drain();
  assert.equal((await subsOf(o.id)).length, 1, 'پیامِ پایانی نرفت ⇒ اشتراک هنوز هست');
  const erase = require('../src/lib/customer-erasure').erase;
  const out = await erase(s.shopId, { orderId: o.id }, { by: 'owner', userId: 'u-test' });
  assert.equal(out.counts.pushSubs, 1, JSON.stringify(out.counts));
  assert.equal((await subsOf(o.id)).length, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۹) کلیدِ VAPID
   ══════════════════════════════════════════════════════════════════ */

test('کلیدِ VAPID: یک بار، پایدار، جفتِ خراب از نو؛ کلیدِ خصوصی رازِ پشتیبان است و از درِ مدیر بیرون نمی‌رود', async () => {
  const k1 = await P.vapid();
  const row = await one('SELECT value FROM app_config WHERE key=$1', [P.PRIV_KEY]);
  assert.equal(row.value, k1.privateKey);
  assert.ok(require('../src/lib/backup-secrets').SECRET_KEYS.includes(P.PRIV_KEY), 'در پشتیبان مهر می‌شود');
  assert.ok(!require('../src/lib/plans').PUBLIC_CONFIG_KEYS.includes(P.PRIV_KEY));
  //  جفتِ ناجور (مثلاً بازیابیِ نیمه) ⇒ جفتِ تازهٔ درست
  await query('UPDATE app_config SET value=$2 WHERE key=$1', [P.PRIV_KEY, crypto.randomBytes(32).toString('base64url')]);
  await new Promise((r) => setTimeout(r, 5));
  const fresh = require('../src/lib/store-push');
  const realNow = Date.now;
  Date.now = () => realNow() + 2 * 60_000;   //  گذشتنِ حافظهٔ یک‌دقیقه‌ای
  try {
    const k2 = await fresh.vapid();
    assert.notEqual(k2.publicKey, k1.publicKey);
    const ecdh = crypto.createECDH('prime256v1');
    ecdh.setPrivateKey(Buffer.from(k2.privateKey, 'base64url'));
    assert.equal(ecdh.getPublicKey('base64url'), k2.publicKey, 'جفتِ درست');
  } finally { Date.now = realNow; }
});
