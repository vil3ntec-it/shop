'use strict';
/**
 * S9 (docs/MARKETPLACE-CONTRACT-fa.md §۳) — گرهِ گفت‌وگو به حسابِ مشتری و تغییرِ شماره بی گم شدنِ گفت‌وگو.
 *
 * درهای `/api/market/account/**` نامِ دومِ حسابِ ویترین (`/api/store/:slug/account/*`، همان `store_customers` ِ سراسری)
 * بودند و صدازننده نداشتند ⇒ برداشته شدند (`dead-doors.test.js`، B15). حساب این‌جا از همان درِ زندهٔ ویترین ساخته
 * می‌شود؛ منطقِ تغییرِ شماره (`store-customers.changePhone`، §۲۳.۴) این‌جا مستقیم سنجیده می‌شود (گرهِ گفت‌وگو)؛ درِ زندهٔ آن
 * `POST /api/store/:slug/account/phone` و قراردادش در `storefront-account-phone.test.js`.
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `market.account` چتِ ویترین نشستِ حساب را نادیده می‌گیرد (فقط بلیت؛ `customer_id`
 *      همیشه NULL — رفتارِ امروز).
 *   ۲) ورود فقط با بلیتِ تازه؛ همان `store_customers` (یک شماره ⇒ یک حساب، از هر ویترین).
 *   ۳) ⛔ تغییرِ شماره گفت‌وگوهای پیشین را گم نمی‌کند: گفت‌وگوی مهمانِ پیش از حساب و
 *      گفت‌وگوی با حساب، هر دو پس از تغییر دیده می‌شوند و پیام می‌پذیرند؛ بستنِ دکان می‌ماند.
 *   ۴) ⛔ جداسازی: دارندهٔ تازهٔ شمارهٔ کهنه (با بلیت یا حسابِ تازه) و مشتریِ دیگر هیچ گفت‌وگو،
 *      پیام یا نشانی‌ای از این حساب نمی‌بینند؛ بلیت‌های کهنه و نشست‌های دیگرِ حساب لغو می‌شوند.
 *   ۵) مهاجرتِ ۱۲۹ برگشت‌پذیر است (DROP INDEX/COLUMN در تراکنش).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now, tx } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  await setFlag(o.shopId, 'shop.chat', true);
  const slug = `ma-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}` }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

/** بلیتِ تازه (هر بار نو — حساب فقط بلیتِ چنددقیقه‌ای را می‌پذیرد). */
async function ticketFor(phone) {
  const s = await h.post('/api/market/verify/start', { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post('/api/market/verify/check', { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}
const T = (t) => ({ headers: { 'X-Store-Ticket': t } });
const S = (s) => ({ headers: { 'X-Store-Session': s } });

/** ورود از درِ زندهٔ حسابِ ویترین (هر ویترین همان حسابِ سراسری) */
async function signIn(slug, phone, name) {
  const r = await h.post(`/api/store/${slug}/account`, { name }, T(await ticketFor(phone)));
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.session;
}
const customers = require('../src/lib/store-customers');
/** همان «نشستِ همین حساب + بلیتِ تازهٔ شمارهٔ نو» ِ §۲۳.۴، بی در: نشست از سرور، شمارهٔ نو از بلیتِ تازه */
async function changePhone(session, newLocal) {
  const a = await customers.auth(session);
  assert.ok(a, 'نشستِ زنده');
  const sf = require('../src/lib/storefront');
  const phone = await sf.verifiedPhoneOfTicket(await ticketFor(newLocal), { maxAgeMs: require('../src/config').storefront.accountTicketMaxMs });
  assert.ok(phone, 'بلیتِ تازهٔ شمارهٔ نو');
  return customers.changePhone(a.customer, a.sessionId, phone);
}
const meOf = async (slug, session) => h.get(`/api/store/${slug}/account`, S(session));

const chatUrl = (slug, rest = '') => `/api/store/${slug}/chat/conversations${rest}`;
async function say(slug, who, convId, text) {
  const r = await h.post(chatUrl(slug, `/${convId}/messages`), { clientId: cid(), kind: 'text', body: text }, who);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body;
}
const listOf = async (slug, who) => {
  const r = await h.get(chatUrl(slug), who);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.conversations.map((c) => c.id).sort();
};

let n = 0;
/** شماره‌های یکتا برای هر آزمون (حساب سراسری است). */
const phones = () => {
  n += 1;
  const base = 790000000 + (Date.now() % 100000) * 10 + n;
  return [`0${base}`, `0${base + 1000000}`, `0${base + 2000000}`];
};
const intl = (local) => `+93${local.slice(1)}`;

test('۱) پرچمِ خاموش: چت نشستِ حساب را نادیده می‌گیرد و customer_id نمی‌نشیند', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'market.account', false);
  const s = await liveStore('خاموش');
  const [P] = phones();
  const t = await ticketFor(P);
  //  نشستِ ساخته‌شده از ویترین، با پرچمِ خاموش، در چت فقط سرآیندِ نادیده است
  const sess = (await h.post(`/api/store/${s.slug}/account`, {}, T(t))).body.session;
  assert.ok(sess);
  assert.equal((await h.get(chatUrl(s.slug), S(sess))).status, 401, 'بی بلیت ⇒ همان «شماره را تأیید کنید»');
  const both = { headers: { 'X-Store-Ticket': t, 'X-Store-Session': sess } };
  const conv = (await h.post(chatUrl(s.slug), {}, both)).body.conversation;
  assert.ok(conv.id);
  const row = await one('SELECT customer_id FROM shop_chat_conversations WHERE id=$1', [conv.id]);
  assert.equal(row.customer_id, null);
});

test('۲) یک شماره ⇒ یک حساب از هر ویترین؛ نامِ دومِ بازار نیست', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'market.account', true);
  try {
    const s = await liveStore('حساب');
    const s2 = await liveStore('حسابِ دوم');
    const [P] = phones();
    const A = await signIn(s.slug, P, 'زهره');
    const B = await signIn(s2.slug, P, 'دیگری');
    const a = (await meOf(s.slug, A)).body.customer;
    const b = (await meOf(s2.slug, B)).body.customer;
    assert.equal(a.id, b.id, 'همان store_customers');
    assert.equal(b.name, 'زهره', 'نامِ حسابِ موجود عوض نمی‌شود');
    //  ⛔ پرچمِ حساب روشن هم: `/api/market/account` دیگر در نیست (همان ۴۰۴ ِ درِ ناشناخته با هر نشست)
    for (const [m, u] of [['GET', '/api/market/account'], ['POST', '/api/market/account/phone'], ['GET', '/api/market/account/orders']]) {
      const r = await h.api(m, u, { headers: { 'X-Store-Session': A, 'X-Store-Ticket': await ticketFor(P) }, body: m === 'GET' ? null : {} });
      assert.notEqual(r.status, 200, `${m} ${u}`);
      assert.notEqual(r.status, 201, `${m} ${u}`);
      assert.ok([401, 404].includes(r.status), `${m} ${u} ⇒ ${r.status}`);
    }
  } finally {
    await setFlag('all', 'market.account', false);
  }
});

test('۳ و ۴) تغییرِ شماره گفت‌وگوها را نگه می‌دارد؛ دارندهٔ تازهٔ شمارهٔ کهنه و مشتریِ دیگر هیچ نمی‌بینند', async () => {
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'market.account', true);
  try {
    const s = await liveStore('تغییر');
    const s2 = await liveStore('دومی');
    const [P, Q, R] = phones();

    //  الف) گفت‌وگوی مهمان با بلیتِ P، پیش از ساختنِ حساب (دکانِ دوم)
    const guest = (await h.post(chatUrl(s2.slug), {}, T(await ticketFor(P)))).body.conversation;
    await say(s2.slug, T(await ticketFor(P)), guest.id, 'سلام از مهمان');

    //  ب) حساب با P و گفت‌وگو با نشست (دکانِ اول) ⇒ customer_id می‌نشیند
    const A = await signIn(s.slug, P, 'مشتریِ الف');
    const A2 = await signIn(s2.slug, P);
    const opened = await h.post(chatUrl(s.slug), {}, S(A));
    assert.equal(opened.status, 201, JSON.stringify(opened.body));
    const conv = opened.body.conversation;
    await say(s.slug, S(A), conv.id, 'پیامِ پیش از تغییر');
    const custId = (await meOf(s.slug, A)).body.customer.id;
    assert.equal((await one('SELECT customer_id FROM shop_chat_conversations WHERE id=$1', [conv.id])).customer_id, custId);
    //  همان گفت‌وگو با بلیت ⇒ همان (بازگشتِ سازگار)
    assert.deepEqual(await listOf(s.slug, T(await ticketFor(P))), [conv.id]);

    //  دکان مشتری را می‌بندد (در گفت‌وگوی دکانِ اول)
    const blk = await h.put(`/api/shop/chat/conversations/${conv.id}/block`, {}, { token: s.token });
    assert.equal(blk.status, 200, JSON.stringify(blk.body));

    //  ج) تغییرِ شماره (§۲۳.۴): شمارهٔ حسابِ دیگر ⇒ ۴۰۹؛ همان شماره ⇒ ۴۰۰
    const oldTicket = await ticketFor(P);
    const B = await signIn(s.slug, R, 'مشتریِ دیگر');
    await assert.rejects(changePhone(A, R), (e) => e.status === 409 && e.code === 'phone_taken');
    await assert.rejects(changePhone(A, P), (e) => e.status === 400 && e.code === 'same_phone');

    const ch = await changePhone(A, Q);
    assert.equal(ch.customer.phone, intl(Q));
    assert.equal(ch.customer.id, custId);
    assert.equal(ch.sessionsRevoked, 1);
    assert.equal((await meOf(s2.slug, A2)).status, 401, 'نشستِ دیگرِ حساب لغو شد');
    assert.equal((await h.get(chatUrl(s.slug), T(oldTicket))).status, 401, 'بلیتِ کهنهٔ P بی‌اعتبار شد');

    //  د) ⛔ هیچ گفت‌وگویی گم نشد — هر دو دکان، با همین نشست
    assert.deepEqual(await listOf(s.slug, S(A)), [conv.id]);
    assert.deepEqual(await listOf(s2.slug, S(A)), [guest.id]);
    const msgs = await h.get(chatUrl(s2.slug, `/${guest.id}/messages?after=0`), S(A));
    assert.equal(msgs.status, 200);
    assert.ok(msgs.body.messages.some((m) => m.body === 'سلام از مهمان'));
    //  گفت‌وگوی مهمان هم پیامِ تازه می‌پذیرد و حالا مالِ حساب است
    await say(s2.slug, S(A), guest.id, 'پس از تغییر');
    assert.equal((await one('SELECT customer_id FROM shop_chat_conversations WHERE id=$1', [guest.id])).customer_id, custId);
    //  باز کردنِ دوباره همان را می‌دهد، نه گفت‌وگوی دوم
    const again = await h.post(chatUrl(s2.slug), {}, S(A));
    assert.equal(again.status, 200);
    assert.equal(again.body.conversation.id, guest.id);
    //  بستنِ دکان با عوض کردنِ شماره دور زده نمی‌شود
    const blocked = await h.post(chatUrl(s.slug, `/${conv.id}/messages`), { clientId: cid(), kind: 'text', body: 'x' }, S(A));
    assert.equal(blocked.status, 403);
    assert.equal((await h.post(chatUrl(s.slug), { name: 'n' }, T(await ticketFor(Q)))).status, 403, 'شمارهٔ نو هم بسته است');
    //  customer_phone برای سازگاری همان شمارهٔ پیشین ماند
    assert.equal((await one('SELECT customer_phone FROM shop_chat_conversations WHERE id=$1', [conv.id])).customer_phone, intl(P));

    //  ه) ⛔ دارندهٔ تازهٔ P (بلیت یا حسابِ تازه) هیچ نمی‌بیند
    const newP = await ticketFor(P);
    assert.deepEqual(await listOf(s2.slug, T(newP)), []);
    assert.equal((await h.get(chatUrl(s2.slug, `/${guest.id}`), T(newP))).status, 404);
    assert.equal((await h.get(chatUrl(s2.slug, `/${guest.id}/messages?after=0`), T(newP))).status, 404);
    assert.equal((await h.post(chatUrl(s2.slug, `/${guest.id}/messages`), { clientId: cid(), kind: 'text', body: 'نفوذ' }, T(newP))).status, 404);
    const srch = await h.get(`/api/store/${s2.slug}/chat/search?q=${encodeURIComponent('مهمان')}`, T(newP));
    assert.equal(srch.status, 200);
    assert.deepEqual(srch.body.results, []);
    const C = await signIn(s2.slug, P, 'دارندهٔ تازه');
    assert.notEqual((await meOf(s2.slug, C)).body.customer.id, custId);
    assert.deepEqual(await listOf(s2.slug, S(C)), []);
    assert.deepEqual(await listOf(s.slug, S(C)), []);
    assert.equal((await h.get(chatUrl(s2.slug, `/${guest.id}`), S(C))).status, 404);
    //  کلیدِ یکتای شمارهٔ P در دکانِ دوم هنوز مالِ گفت‌وگوی حساب است ⇒ ۴۰۹ ِ روشن، نه نشت
    const clash = await h.post(chatUrl(s2.slug), {}, T(newP));
    assert.equal(clash.status, 409);
    assert.equal(clash.body.error.code, 'chat_unavailable');

    //  و) مشتریِ دیگر (R)
    assert.deepEqual(await listOf(s.slug, S(B)), []);
    assert.deepEqual(await listOf(s2.slug, S(B)), []);
    assert.equal((await h.get(chatUrl(s.slug, `/${conv.id}`), S(B))).status, 404);

    //  ز) دکان همان گفت‌وگوها را دارد
    const inbox = await h.get('/api/shop/chat/conversations', { token: s2.token });
    assert.ok(inbox.body.conversations.some((c) => c.id === guest.id));
  } finally {
    await setFlag('all', 'market.account', false);
  }
});

test('۵) مهاجرتِ ۱۲۹ برگشت‌پذیر است', async () => {
  await assert.rejects(tx(async (c) => {
    await c.query('DROP INDEX IF EXISTS ix_shop_chat_conv_customer');
    await c.query('ALTER TABLE shop_chat_conversations DROP COLUMN IF EXISTS customer_id');
    const col = (await c.query(
      "SELECT 1 FROM information_schema.columns WHERE table_name='shop_chat_conversations' AND column_name='customer_id'"
    )).rows[0];
    assert.equal(col, undefined);
    throw new Error('rollback-probe');
  }), /rollback-probe/);
  const col = await one("SELECT 1 AS ok FROM information_schema.columns WHERE table_name='shop_chat_conversations' AND column_name='customer_id'");
  assert.ok(col, 'تراکنش برگشت و ستون سرِ جاست');
});
