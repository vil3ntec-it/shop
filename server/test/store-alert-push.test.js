'use strict';
/**
 * WEB-10 روی پوشِ وب: «خبرم کن وقتی موجود/ارزان شد» با اعلانِ همین مرورگر (`lib/store-alert-push.js`،
 * `routes/store-push.js`، مهاجرتِ ۴۷۵؛ پشتِ همان پرچم‌های خاموشِ `shop.storefront.alerts` + `shop.storefront.web_push`
 * + `shop.customer_notify`). هرگز شبکه: `useTransport`؛ پیام با کلیدِ خصوصیِ «مرورگرِ» آزمون باز می‌شود.
 *
 *   ۱) ⛔ هر پرچمی خاموش ⇒ درِ اشتراک همان پاسخِ مسیرِ ناشناخته، هیچ ردیف، «هشدارهای من» بی `push` و کارگر پوش نمی‌زند.
 *   ۲) ⛔ ورودی و SSRF: بی شمارهٔ تأییدشده ۴۰۱؛ endpoint فقط سرویسِ پوشِ مرورگرها (همان فهرستِ `store-push`)؛ صفحه فقط همان ویترین.
 *   ۳) سرتاسر: گذار ⇒ یک پیامِ رمزشده (aes128gcm + VAPID ِ همین سرور) فقط با نامِ کالا و قیمت (بی شماره، بی نامِ ویترین)،
 *      لمس فقط برگهٔ همان کالا در همان مبدأ؛ کلیدهای پیام دقیقاً همان شش.
 *   ۴) (دکان، شماره، مرورگر): یکتا؛ حداکثر ۳ مرورگر حتی هم‌زمان؛ شمارهٔ دیگر و دکانِ دیگر نه می‌بینند، نه پاک می‌کنند، نه پیام می‌گیرند.
 *   ۵) ⛔ اعتبار: بلیتِ باطل‌شده، بلیتِ منقضی، نشستِ بسته و شمارهٔ عوض‌شدهٔ حساب ⇒ هیچ پیام و اشتراک پاک؛ بازپخشِ بلیتِ باطل ⇒ ۴۰۱.
 *   ۶) سقفِ هر دکان در ۲۴ ساعت (هر خبرِ «خبرم کن»)، سقفِ نوشتنِ هر شماره ⇒ ۴۲۹.
 *   ۷) پاسخِ سرویس: ۴۰۴/۴۱۰ ⇒ اشتراک پاک؛ ۵۰۰ ⇒ `failed` و اشتراک می‌ماند (همان قاعدهٔ «شاید رفته باشد»).
 *   ۸) SEC-10 (`alertPushSubs`) و پاک کردنِ حساب ⇒ اشتراک‌ها پاک؛ دکانِ دیگر دست نمی‌خورد.
 *   ۹) ⛔ کارِ مشتری هیچ کانالی (پولی یا رایگان) را برنمی‌انگیزد: اشتراک هیچ پیامی نمی‌فرستد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const ece = require('http_ece');
const { h, boot, flag, liveStore, setStock, setPrice, ticketFor } = require('./storefront-finder-fixture');
const { query, one, many } = require('../src/db');
const A = require('../src/lib/storefront-alerts');
const P = require('../src/lib/store-push');
const N = require('../src/lib/customer-notify');
const sf = require('../src/lib/storefront');
const customers = require('../src/lib/store-customers');

boot();
test.afterEach(() => { P.useTransport(null); N.setProviders({}); });

let seq = 0;
const FLAGS = ['shop.storefront.alerts', 'shop.storefront.web_push', 'shop.customer_notify'];
async function store(label, flags = FLAGS) {
  const s = await liveStore(label, { catalog: true });
  for (const k of flags) await flag(s.shopId, k, true);
  return s;
}
/** «مرورگرِ» آزمون: کلیدِ P-256 ِ واقعی + auth ِ ۱۶ بایتی؛ `open(body)` پیامِ رمزشده را باز می‌کند */
function browser(host = 'fcm.googleapis.com') {
  const ecdh = crypto.createECDH('prime256v1');
  ecdh.generateKeys();
  const auth = crypto.randomBytes(16).toString('base64url');
  const sub = { endpoint: `https://${host}/fcm/send/a-${Date.now().toString(36)}-${++seq}`, keys: { p256dh: ecdh.getPublicKey('base64url'), auth } };
  return { sub, open: (body) => JSON.parse(ece.decrypt(Buffer.from(body), { version: 'aes128gcm', privateKey: ecdh, authSecret: auth }).toString('utf8')) };
}
/** سرویسِ پوشِ ساختگی: هر درخواست را نگه می‌دارد؛ `script` وضعیتِ پاسخ‌های بعدی (پیش‌فرض ۲۰۱) */
function service(script = []) {
  const calls = [];
  P.useTransport(async (req) => { calls.push(req); return { status: script.length ? script.shift() : 201 }; });
  return calls;
}
const on = (s, k, body) => h.post(`/api/store/${s.slug}/alerts/push`, body, k);
const off = (s, k, body) => h.api('DELETE', `/api/store/${s.slug}/alerts/push`, { body, ...k });
const arm = (s, k, id, kind) => h.put(`/api/store/${s.slug}/alerts/${id}/${kind}`, {}, k);
const rowsOf = (shopId) => many('SELECT * FROM store_alert_push WHERE shop_id = $1 ORDER BY created_at, id', [shopId]);
const alertOf = (shopId, productId, kind) => one(
  'SELECT * FROM storefront_alerts WHERE shop_id = $1 AND product_id = $2 AND kind = $3', [shopId, productId, kind]
);
const code = (r) => (r.body && r.body.error && r.body.error.code) || '';
const ticketOfK = (k) => k.headers['X-Store-Ticket'];

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('⛔ هر پرچمی خاموش ⇒ همان پاسخِ مسیرِ ناشناخته، هیچ ردیف، «هشدارهای من» بی push و کارگر پوش نمی‌زند', async () => {
  for (const missing of FLAGS) {
    const s = await store(`خاموش ${missing}`, FLAGS.filter((f) => f !== missing));
    const k = await ticketFor(s.slug, '0791260001');
    const unknown = await h.post(`/api/store/${s.slug}/alerts/no-such-door`, { subscription: browser().sub }, k);
    for (const r of [await on(s, k, { subscription: browser().sub }), await off(s, k, { endpoint: browser().sub.endpoint })]) {
      assert.equal(r.status, unknown.status, missing);
      assert.deepEqual(r.body, unknown.body, missing);
    }
    assert.equal((await rowsOf(s.shopId)).length, 0, `${missing}: هیچ نوشتنی`);
    if (missing !== 'shop.storefront.alerts') {
      const mine = await h.get(`/api/store/${s.slug}/alerts`, k);
      assert.equal(mine.status, 200);
      assert.equal('push' in mine.body, false, `${missing}: «هشدارهای من» بی کلیدِ push`);
    }
  }
  //  اشتراکِ زنده + پرچمِ پوش خاموش ⇒ کارگر پوش نمی‌زند (همان no_channel ِ پیشین)
  const s = await store('خاموش پس از اشتراک');
  const k = await ticketFor(s.slug, '0791260002');
  assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201);
  assert.equal((await on(s, k, { subscription: browser().sub })).status, 200);
  await flag(s.shopId, 'shop.storefront.web_push', false);
  const calls = service();
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 0, 'پرچمِ خاموش ⇒ هیچ درخواستی به سرویسِ پوش');
  assert.deepEqual([(await alertOf(s.shopId, 'tea-a', 'stock')).state, (await alertOf(s.shopId, 'tea-a', 'stock')).notice], ['fired', 'no_channel']);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) ورودی و SSRF
   ══════════════════════════════════════════════════════════════════ */

test('⛔ بی شمارهٔ تأییدشده ۴۰۱؛ SSRF: فقط سرویسِ پوشِ مرورگرها؛ کلیدِ بد یا صفحهٔ دیگر ⇒ ۴۰۰ و هیچ ردیف', async () => {
  const s = await store('ورودی');
  const b = browser();
  const anon = await on(s, {}, { subscription: b.sub });
  assert.deepEqual([anon.status, code(anon)], [401, 'verification_required']);
  const forged = await on(s, { headers: { 'X-Store-Ticket': 'v2.forged' } }, { subscription: b.sub });
  assert.equal(forged.status, 401);
  assert.equal((await off(s, {}, { endpoint: b.sub.endpoint })).status, 401);

  const k = await ticketFor(s.slug, '0791260003');
  const bad = [
    { ...b.sub, endpoint: 'http://fcm.googleapis.com/fcm/send/x-1234567' },
    { ...b.sub, endpoint: 'https://127.0.0.1/fcm/send/x-1234567' },
    { ...b.sub, endpoint: 'https://169.254.169.254/latest/meta-data' },
    { ...b.sub, endpoint: 'https://evil.example/fcm/send/x-1234567' },
    { ...b.sub, endpoint: 'https://fcm.googleapis.com.evil.example/x-1234567' },
    { ...b.sub, endpoint: 'https://fcm.googleapis.com:8443/fcm/send/x-1234567' },
    { ...b.sub, endpoint: 'https://u:p@fcm.googleapis.com/fcm/send/x-1234567' },
    { ...b.sub, keys: { p256dh: 'A'.repeat(87), auth: b.sub.keys.auth } },
    { ...b.sub, keys: { p256dh: b.sub.keys.p256dh, auth: 'short' } },
  ];
  for (const sub of bad) {
    const r = await on(s, k, { subscription: sub });
    assert.deepEqual([r.status, code(r)], [400, 'bad_subscription'], sub.endpoint);
  }
  const page = await on(s, k, { subscription: b.sub, page: '/store/another-shop' });
  assert.deepEqual([page.status, code(page)], [400, 'bad_page']);
  assert.equal((await rowsOf(s.shopId)).length, 0, 'هیچ ردیف');

  //  «هشدارهای من» کلیدِ عمومیِ همان سرور را می‌دهد (همان کلیدِ پوشِ پیگیریِ سفارش)
  const mine = await h.get(`/api/store/${s.slug}/alerts`, k);
  assert.deepEqual(mine.body.push, { key: await P.publicKey() });
});

/* ══════════════════════════════════════════════════════════════════
   ۳) سرتاسر
   ══════════════════════════════════════════════════════════════════ */

test('سرتاسر: گذار ⇒ یک پیامِ رمزشده فقط با نام و قیمتِ کالا؛ لمس فقط برگهٔ همان کالا در همین مبدأ', async () => {
  const s = await store('سرتاسر');
  const k = await ticketFor(s.slug, '0791260004');
  const b = browser();
  const calls = service();
  N.setProviders({ sms: { send: async () => { throw new Error('پیامک هرگز'); } } });
  assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201);
  assert.equal((await arm(s, k, 'tea-c', 'price')).status, 201);
  const r = await on(s, k, { subscription: b.sub, page: `/store/${s.slug}` });
  assert.deepEqual([r.status, r.body], [200, { push: { on: true } }]);
  assert.equal(calls.length, 0, 'اشتراک خودش هیچ پیامی نمی‌فرستد');
  const [row] = await rowsOf(s.shopId);
  assert.deepEqual([row.phone, row.proof, row.endpoint, row.page], ['+93791260004', 'ticket', b.sub.endpoint, `/store/${s.slug}`]);

  await setStock(s, 'tea-a', 2000);
  await A.tick();
  assert.equal(calls.length, 1, 'یک گذار ⇒ یک پیام');
  const req = calls[0];
  assert.equal(req.endpoint, b.sub.endpoint);
  assert.equal(req.headers['Content-Encoding'], 'aes128gcm');
  assert.match(req.headers.Authorization, /^vapid t=[\w-]+\.[\w-]+\.[\w-]+, k=/);
  assert.equal(req.headers.Urgency, 'normal');
  assert.ok(req.headers.Topic && req.headers.Topic.length <= 32);
  const msg = b.open(req.body);
  assert.deepEqual(Object.keys(msg).sort(), ['body', 'lang', 'tag', 'title', 'url', 'v'], 'فقط همین کلیدها');
  assert.equal(msg.title, 'چای الف');
  assert.equal(msg.body, 'دوباره موجود است — ۱۲۰ افغانی');
  assert.equal(msg.url, `/store/${s.slug}#/p/tea-a`);
  assert.match(msg.tag, /^a-[A-Za-z0-9_-]{12}$/);
  const raw = JSON.stringify(msg);
  assert.doesNotMatch(raw, /791260004|0791|\+93/, 'بی شماره');
  assert.equal(raw.includes('ویترینِ سرتاسر'), false, 'بی نامِ ویترین');
  const fired = await alertOf(s.shopId, 'tea-a', 'stock');
  assert.deepEqual([fired.state, fired.notice, fired.channel], ['fired', 'sent', 'push']);
  assert.ok(Number((await rowsOf(s.shopId))[0].last_ok_at) > 0, 'last_ok_at');

  //  ارزان شدن: قیمتِ تازه در پیام؛ انگلیسی با رقمِ لاتین
  await query("UPDATE storefront_alerts SET lang = 'en' WHERE shop_id = $1 AND product_id = 'tea-c'", [s.shopId]);
  assert.equal((await setPrice(s, 'tea-c', 25050)).status, 200);
  await A.tick();
  assert.equal(calls.length, 2);
  const m2 = b.open(calls[1].body);
  assert.deepEqual([m2.title, m2.body, m2.lang, m2.url], ['چای پ', 'Price dropped — 250.5 AFN', 'en', `/store/${s.slug}#/p/tea-c`]);
  assert.notEqual(m2.tag, msg.tag, 'برچسبِ جدا برای هر کالا و نوع');
  assert.notEqual(calls[1].headers.Topic, calls[0].headers.Topic);
  await A.tick();
  assert.equal(calls.length, 2, 'هشدارِ سوخته دوباره پیام نمی‌دهد');

  //  نشانیِ خودِ دکان: page="/" ⇒ لمس به `/#/p/…`
  const k2 = await ticketFor(s.slug, '0791260005');
  const b2 = browser();
  assert.equal((await arm(s, k2, 'tea-b', 'stock')).status, 201);
  assert.equal((await on(s, k2, { subscription: b2.sub, page: '/' })).status, 200);
  await setStock(s, 'tea-b', 9000);
  await A.tick();
  assert.equal(calls.length, 3);
  assert.equal(b2.open(calls[2].body).url, '/#/p/tea-b');
});

/* ══════════════════════════════════════════════════════════════════
   ۴) (دکان، شماره، مرورگر)
   ══════════════════════════════════════════════════════════════════ */

test('یکتا و محدود: همان مرورگر یک ردیف؛ حداکثر ۳ مرورگر هم‌زمان هم؛ شمارهٔ دیگر و دکانِ دیگر جدا', async () => {
  const s = await store('جدایی');
  const t = await store('جدایی دیگر');
  const k = await ticketFor(s.slug, '0791260006');
  const other = await ticketFor(s.slug, '0791260007');
  const b = browser();
  assert.equal((await on(s, k, { subscription: b.sub })).status, 200);
  const fresh = browser();
  const again = await on(s, k, { subscription: { ...b.sub, keys: fresh.sub.keys } });
  assert.equal(again.status, 200, 'بازپخش/تازه‌سازی همان مرورگر');
  let rows = await rowsOf(s.shopId);
  assert.equal(rows.length, 1, 'همان مرورگر ⇒ یک ردیف');
  assert.equal(rows[0].p256dh, fresh.sub.keys.p256dh, 'کلیدِ تازهٔ همان مرورگر');

  //  سه مرورگر برای هر (دکان، شماره)، حتی با درخواستِ هم‌زمان
  const many3 = await Promise.all([1, 2, 3, 4].map(() => on(s, k, { subscription: browser().sub })));
  assert.deepEqual(many3.map((r) => r.status).sort(), [200, 200, 409, 409]);
  assert.ok(many3.filter((r) => r.status === 409).every((r) => code(r) === 'alert_push_limit'));
  assert.equal((await rowsOf(s.shopId)).filter((r) => r.phone === '+93791260006').length, 3);

  //  شمارهٔ دیگر: همان مرورگرِ k را «خاموش» نمی‌تواند (ردیفِ k می‌ماند) و ردیفِ خودش جداست
  assert.deepEqual((await off(s, other, { endpoint: b.sub.endpoint })).body, { push: { on: false } });
  assert.ok((await rowsOf(s.shopId)).some((r) => r.endpoint === b.sub.endpoint && r.phone === '+93791260006'), 'ردیفِ شمارهٔ دیگر دست نخورد');
  const ob = browser();
  assert.equal((await on(s, other, { subscription: ob.sub })).status, 200);

  //  دکانِ دیگر: «خاموش» ِ همان مرورگر در دکانِ دیگر به این دکان نمی‌رسد؛ هشدارِ همان شماره در دکانِ دیگر به مرورگرِ این دکان نمی‌رود
  const kt = await ticketFor(t.slug, '0791260006');
  assert.deepEqual((await off(t, kt, { endpoint: b.sub.endpoint })).body, { push: { on: false } });
  assert.ok((await rowsOf(s.shopId)).some((r) => r.endpoint === b.sub.endpoint), 'دکانِ دیگر پاک نکرد');
  assert.equal((await arm(t, kt, 'tea-a', 'stock')).status, 201);
  const calls = service();
  await setStock(t, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 0, 'هشدارِ دکانِ دیگر به مرورگرِ این دکان نمی‌رود');
  assert.equal((await alertOf(t.shopId, 'tea-a', 'stock')).notice, 'no_channel');

  //  هشدارِ شمارهٔ دیگر فقط به مرورگرِ همان شماره
  assert.equal((await arm(s, other, 'tea-a', 'stock')).status, 201);
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.deepEqual(calls.map((c) => c.endpoint), [ob.sub.endpoint]);

  //  «خاموش» ِ خودش: فقط همان مرورگر
  assert.deepEqual((await off(s, k, { endpoint: b.sub.endpoint })).body, { push: { on: false } });
  rows = await rowsOf(s.shopId);
  assert.equal(rows.filter((r) => r.phone === '+93791260006').length, 2);
  assert.equal((await off(s, k, { endpoint: 'https://evil.example/x-1234567' })).status, 400);
});

/* ══════════════════════════════════════════════════════════════════
   ۵) اعتبارِ شمارهٔ تأییدشده
   ══════════════════════════════════════════════════════════════════ */

test('⛔ بلیتِ باطل یا منقضی ⇒ هیچ پیام و اشتراک پاک؛ بازپخشِ بلیتِ باطل ⇒ ۴۰۱', async () => {
  const s = await store('اعتبار بلیت');
  const calls = service();
  //  باطل: «خروج از همه‌جا» نسلِ بلیت‌های این شماره را بالا می‌برد
  const k = await ticketFor(s.slug, '0791260008');
  assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201);
  assert.equal((await on(s, k, { subscription: browser().sub })).status, 200);
  await customers.revokeTickets('+93791260008');
  const replay = await on(s, k, { subscription: browser().sub });
  assert.deepEqual([replay.status, code(replay)], [401, 'verification_required'], 'بلیتِ باطل بازپخش نمی‌شود');
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 0, 'بلیتِ باطل ⇒ هیچ پیام');
  assert.equal((await rowsOf(s.shopId)).length, 0, 'اشتراکِ بلیتِ باطل پاک شد');
  assert.equal((await alertOf(s.shopId, 'tea-a', 'stock')).notice, 'no_channel');

  //  منقضی: همان بلیتِ ۱۴ روزه گذشت ⇒ هیچ پیام
  const k2 = await ticketFor(s.slug, '0791260009');
  assert.equal((await arm(s, k2, 'tea-b', 'price')).status, 201);
  assert.equal((await on(s, k2, { subscription: browser().sub })).status, 200);
  const [row] = await rowsOf(s.shopId);
  const t = sf.parseTicket(ticketOfK(k2));
  assert.equal(Number(row.expires_at), t.expiresAt, 'اشتراک تا انقضای همان بلیت');
  assert.equal((await setPrice(s, 'tea-b', 100)).status, 200);
  await A.tick(t.expiresAt + 1);
  assert.equal(calls.length, 0, 'بلیتِ منقضی ⇒ هیچ پیام');
  assert.equal((await rowsOf(s.shopId)).length, 0, 'اشتراکِ منقضی پاک شد');

  //  منقضی بی هیچ گذاری: دورِ کارگر خودش پاک می‌کند
  const k3 = await ticketFor(s.slug, '0791260023');
  assert.equal((await on(s, k3, { subscription: browser().sub })).status, 200);
  await A.tick();
  assert.equal((await rowsOf(s.shopId)).length, 1, 'زنده می‌ماند');
  await A.tick(sf.parseTicket(ticketOfK(k3)).expiresAt + 1);
  assert.equal((await rowsOf(s.shopId)).length, 0, 'منقضی ⇒ دورِ کارگر پاکش کرد');
});

test('⛔ نشستِ حساب: بسته شدن یا عوض شدنِ شمارهٔ حساب ⇒ هیچ پیام؛ نشستِ زنده ⇒ پیام', async () => {
  const s = await store('اعتبار نشست');
  const calls = service();
  const signIn = async (phone) => {
    const k = await ticketFor(s.slug, phone);
    const r = await h.post(`/api/store/${s.slug}/account`, {}, k);
    assert.equal(r.status, 201, JSON.stringify(r.body));
    return { headers: { 'X-Store-Session': r.body.session } };
  };
  //  نشستِ زنده (بی بلیت) ⇒ اشتراکِ «نشست» و پیام
  const a = await signIn('0791260010');
  assert.equal((await arm(s, a, 'tea-a', 'stock')).status, 201);
  const ba = browser();
  assert.equal((await on(s, a, { subscription: ba.sub })).status, 200);
  const [row] = await rowsOf(s.shopId);
  assert.deepEqual([row.proof, row.phone, Boolean(row.session_id)], ['session', '+93791260010', true]);
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 1, 'نشستِ زنده ⇒ پیام');

  //  خروج ⇒ نشست بسته ⇒ هیچ پیام و اشتراک پاک
  assert.equal((await arm(s, a, 'tea-c', 'price')).status, 201);
  assert.equal((await h.post(`/api/store/${s.slug}/account/logout`, {}, a)).status, 200);
  await setPrice(s, 'tea-c', 100);
  await A.tick();
  assert.equal(calls.length, 1, 'نشستِ بسته ⇒ هیچ پیام');
  assert.equal((await rowsOf(s.shopId)).length, 0);

  //  عوض شدنِ شمارهٔ حساب: اشتراکِ شمارهٔ پیشین (با همان نشستِ زنده) دیگر پیامی نمی‌گیرد
  const b = await signIn('0791260011');
  assert.equal((await arm(s, b, 'tea-b', 'price')).status, 201);
  assert.equal((await on(s, b, { subscription: browser().sub })).status, 200);
  const sess = await customers.auth(b.headers['X-Store-Session']);
  await customers.changePhone(sess.customer, sess.sessionId, '+93791260012');
  await setPrice(s, 'tea-b', 100);
  await A.tick();
  assert.equal(calls.length, 1, 'شمارهٔ حساب عوض شد ⇒ هیچ پیام به اشتراکِ شمارهٔ پیشین');
  assert.equal((await rowsOf(s.shopId)).length, 0);
});

test('تأییدِ شماره: `verification` همان تصمیمِ `verifiedPhone` است (بلیت، نشست، باطل، خالی)', async () => {
  const s = await store('هم‌سانیِ تأیید');
  const k = await ticketFor(s.slug, '0791260013');
  const ticket = ticketOfK(k);
  const session = (await h.post(`/api/store/${s.slug}/account`, {}, k)).body.session;
  const cases = [[ticket, ''], ['', session], [ticket, session], ['', ''], ['v2.bad', 'sc1.bad'], [h.tampered(ticket), '']];
  for (const [t, ss] of cases) {
    const v = await sf.verification(t, ss);
    assert.equal(v ? v.phone : null, await sf.verifiedPhone(t, ss), `${t.slice(0, 8)} / ${ss.slice(0, 6)}`);
  }
  assert.equal((await sf.verification(ticket, session)).proof, 'ticket', 'همان ترتیب: بلیت جلوتر');
  await customers.revokeTickets('+93791260013');
  assert.equal(await sf.verification(ticket, ''), null);
  assert.equal((await sf.verification(ticket, '')), await sf.verifiedPhone(ticket, ''));
});

/* ══════════════════════════════════════════════════════════════════
   ۶) سقف‌ها
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ هر دکان در ۲۴ ساعت برای هر «خبرم کن» و سقفِ نوشتنِ هر شماره (۴۲۹)', async () => {
  const s = await store('سقف دکان');
  const t = await store('سقف دکان دیگر');
  const calls = service();
  const saved = process.env.STORE_ALERTS_SHOP_PER_DAY;
  process.env.STORE_ALERTS_SHOP_PER_DAY = '2';
  try {
    for (const [i, phone] of ['0791260014', '0791260015', '0791260016'].entries()) {
      const k = await ticketFor(s.slug, phone);
      assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201, `${i}`);
      assert.equal((await on(s, k, { subscription: browser().sub })).status, 200);
    }
    const kt = await ticketFor(t.slug, '0791260017');
    assert.equal((await arm(t, kt, 'tea-a', 'stock')).status, 201);
    assert.equal((await on(t, kt, { subscription: browser().sub })).status, 200);
    await setStock(s, 'tea-a', 1000);
    await setStock(t, 'tea-a', 1000);
    await A.tick();
    const notices = (await many("SELECT notice, tried FROM storefront_alerts WHERE shop_id = $1", [s.shopId]));
    assert.deepEqual(notices.map((r) => r.notice).sort(), ['rate_limited', 'sent', 'sent']);
    assert.deepEqual(notices.find((r) => r.notice === 'rate_limited').tried, [{ channel: '*', result: 'shop_cap' }]);
    assert.equal((await alertOf(t.shopId, 'tea-a', 'stock')).notice, 'sent', 'سقفِ هر دکان جداست');
    assert.equal(calls.length, 3);
  } finally {
    if (saved === undefined) delete process.env.STORE_ALERTS_SHOP_PER_DAY; else process.env.STORE_ALERTS_SHOP_PER_DAY = saved;
  }

  //  سقفِ نوشتنِ هر شماره (جدا از سقفِ IP)
  const k = await ticketFor(s.slug, '0791260018');
  const b = browser();
  for (let i = 0; i < A.WRITES_PER_HOUR; i++) assert.equal((await on(s, k, { subscription: b.sub })).status, 200, `${i}`);
  const r = await on(s, k, { subscription: b.sub });
  assert.equal(r.status, 429);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) پاسخِ سرویس
   ══════════════════════════════════════════════════════════════════ */

test('پاسخِ سرویس: ۴۱۰/۴۰۴ ⇒ اشتراک پاک؛ ۵۰۰ ⇒ failed (دوباره نه) و اشتراک می‌ماند؛ بی سرویس در آزمون هیچ شبکه', async () => {
  const s = await store('پاسخ');
  const k = await ticketFor(s.slug, '0791260019');
  const [gone, missing, busy] = [browser(), browser(), browser()];
  for (const b of [gone, missing, busy]) assert.equal((await on(s, k, { subscription: b.sub })).status, 200);
  const status = new Map([[gone.sub.endpoint, 410], [missing.sub.endpoint, 404], [busy.sub.endpoint, 500]]);
  const calls = [];
  P.useTransport(async (req) => { calls.push(req); return { status: status.get(req.endpoint) }; });
  assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201);
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal(calls.length, 3, 'سه مرورگر هم‌زمان');
  assert.deepEqual((await rowsOf(s.shopId)).map((r) => r.endpoint), [busy.sub.endpoint], '۴۰۴/۴۱۰ پاک؛ ۵۰۰ می‌ماند');
  const a = await alertOf(s.shopId, 'tea-a', 'stock');
  assert.deepEqual([a.notice, a.tried.map((t) => `${t.channel}:${t.result}`)], ['failed', ['push:retry']]);
  await A.tick();
  assert.equal(calls.length, 3, 'پاسخِ مبهم دوباره فرستاده نمی‌شود');

  //  ⛔ بی `useTransport` در NODE_ENV=test: هیچ درخواستی بیرون نمی‌رود
  P.useTransport(null);
  assert.equal((await arm(s, k, 'tea-c', 'price')).status, 201);
  await setPrice(s, 'tea-c', 100);
  await A.tick();
  assert.equal((await alertOf(s.shopId, 'tea-c', 'price')).notice, 'failed');
});

/* ══════════════════════════════════════════════════════════════════
   ۸) SEC-10 و پاک کردنِ حساب
   ══════════════════════════════════════════════════════════════════ */

test('SEC-10: حذفِ دادهٔ مشتری اشتراک‌های همین شماره در همین دکان را پاک می‌کند (`alertPushSubs`)؛ پاک کردنِ حساب همه را', async () => {
  const s = await store('حذف');
  const t = await store('حذف دیگر');
  const k = await ticketFor(s.slug, '0791260020');
  const kt = await ticketFor(t.slug, '0791260020');
  const other = await ticketFor(s.slug, '0791260021');
  for (const [st, kk] of [[s, k], [s, k], [t, kt], [s, other]]) assert.equal((await on(st, kk, { subscription: browser().sub })).status, 200);
  const erase = require('../src/lib/customer-erasure');
  assert.ok(erase.COUNT_KEYS.includes('alertPushSubs'));
  const dry = await erase.erase(s.shopId, { phone: '0791260020' }, { dryRun: true, by: 'owner', userId: 'u-test' });
  assert.equal(dry.counts.alertPushSubs, 2, JSON.stringify(dry.counts));
  assert.equal((await rowsOf(s.shopId)).length, 3, 'پیش‌نمایش چیزی پاک نمی‌کند');
  const out = await erase.erase(s.shopId, { phone: '0791260020' }, { by: 'owner', userId: 'u-test' });
  assert.equal(out.counts.alertPushSubs, 2);
  assert.deepEqual((await rowsOf(s.shopId)).map((r) => r.phone), ['+93791260021'], 'شمارهٔ دیگر می‌ماند');
  assert.equal((await rowsOf(t.shopId)).length, 1, 'دکانِ دیگر دست نمی‌خورد');

  //  پاک کردنِ حسابِ مشتری ⇒ اشتراک‌های همان شماره در همهٔ دکان‌ها
  const session = (await h.post(`/api/store/${t.slug}/account`, {}, await ticketFor(t.slug, '0791260020'))).body.session;
  assert.equal((await h.api('DELETE', `/api/store/${t.slug}/account`, { headers: { 'X-Store-Session': session } })).status, 200);
  assert.equal((await rowsOf(t.shopId)).length, 0);
  assert.equal((await rowsOf(s.shopId)).length, 1);
});

/* ══════════════════════════════════════════════════════════════════
   ۹) کارِ مشتری هیچ کانالی را برنمی‌انگیزد
   ══════════════════════════════════════════════════════════════════ */

test('⛔ اشتراک هیچ پیامی برنمی‌انگیزد (نه پیامک، نه پوش)، حتی وقتی هشدارِ پیشین `no_channel` مانده بود', async () => {
  const s = await store('بی پیامک');
  const k = await ticketFor(s.slug, '0791260022');
  const sent = [];
  const spy = (channel) => ({ send: async (m) => { sent.push(channel); return { status: 'rejected', code: 'x' }; } });
  assert.equal((await arm(s, k, 'tea-a', 'stock')).status, 201);
  await setStock(s, 'tea-a', 1000);
  await A.tick();
  assert.equal((await alertOf(s.shopId, 'tea-a', 'stock')).notice, 'no_channel');
  N.setProviders({ sms: spy('sms'), whatsapp: spy('whatsapp'), telegram: spy('telegram') });
  const calls = service();
  assert.equal((await on(s, k, { subscription: browser().sub })).status, 200);
  await A.tick();
  await N.tick();
  assert.deepEqual([sent, calls.length], [[], 0], 'کارِ مشتری ⇒ هیچ کانالی');
  assert.equal(A.CHANNELS.includes('sms'), false, 'زنجیرهٔ «خبرم کن» پیامک ندارد');
});
