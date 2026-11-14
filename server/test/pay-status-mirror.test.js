'use strict';
/**
 * PAY-07 (د۱۸) — آینهٔ وضعیتِ بعدیِ قصدِ حساب‌پیِ خودکار روی فاکتورِ دستگاه: `GET /api/shop/payments/mirror`.
 *
 * دستگاه پس از هر دورِ همگام‌سازی وضعیتِ قصدهای فروشِ صندوقِ همین دکان را که پس از cursor ِ خودش عوض شد می‌گیرد
 * (`paid`، `paid` + نیازمند بررسی، `refunded`) و روی ردیفِ درگاهِ همان فاکتور نشان می‌دهد (سرور مرجع است؛ سندِ
 * دفتر نمی‌سازد). پشتِ پرچمِ خاموشِ `shop.pay_status_mirror` (خاموش ⇒ ۴۰۴، دستگاه هیچ نمی‌کند — همان امروز).
 * ⛔ ۴۰۱ بی توکن، ۴۰۳ نقشِ بی `data.write`، ۴۰۰ cursor ِ بدشکل، ۴۲۹ سقفِ نرخ؛ فقط همین دکان.
 */
process.env.PAYMENTS_PROVIDER = 'fake';
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-pay-mirror';
process.env.RATE_PAY_CREATE_MAX = '100000';
process.env.RATE_PAY_POLL_MAX = '100000';
process.env.RATE_PAY_MIRROR_MAX = '40';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const P = require('../src/lib/payment-providers');
const pay = require('../src/lib/payment-intents');

const FLAG = 'shop.pay_status_mirror';
const URL_INT = '/api/v1/shop/payments/intents';
const URL_MIR = '/api/shop/payments/mirror';
let admin = '';
let seq = 0;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'mir-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'mir-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
async function shop(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, pay.FLAG_KEY, true);
  return { owner, shopId, t: owner.accessToken };
}
const createPos = (s, extra = {}) => h.post(URL_INT, {
  kind: 'pos_sale', refId: `sale-${++seq}`, amount: 145000, currency: 'AFN', idempotencyKey: `key-${Date.now()}-${++seq}-mir`, ...extra,
}, { token: s.t });
async function hook(body) {
  const buf = Buffer.from(JSON.stringify(body));
  return h.raw('POST', '/api/payments/webhook/fake', buf, { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(buf) } });
}
async function paid(intent) {
  const sid = (await one('SELECT provider_session_id FROM payment_intents WHERE id=$1', [intent.id])).provider_session_id;
  P.fake.settle(sid, { status: 'paid', amountMinor: intent.amount, currency: 'AFN', providerRef: `HP-${seq}` });
  const w = await hook({ eventId: `ev-${++seq}`, sessionId: sid, status: 'paid', amountMinor: intent.amount, currency: 'AFN', providerRef: `HP-${seq}` });
  assert.equal(w.status, 200, JSON.stringify(w.body));
}
const mirror = (s, q = '') => h.get(`${URL_MIR}${q}`, { token: s.t });

test('پرچمِ خاموش (پیش‌فرض) ⇒ ۴۰۴ و هیچ داده‌ای — دستگاه همان امروز', async () => {
  const s = await shop('خاموش');
  const a = (await createPos(s)).body;
  await paid(a);
  const r = await mirror(s);
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'pay_mirror_off');
});

test('روشن: فقط قصدهای فروشِ صندوقِ پرداخت‌شده/بازپرداخت‌شدهٔ همین دکان، به ترتیبِ تغییر، با cursor ِ پیوسته', async () => {
  const s = await shop('آینه');
  const other = await shop('دیگر');
  await setFlag(s.shopId, FLAG, true);
  await setFlag(other.shopId, FLAG, true);
  const a = (await createPos(s)).body;
  const b = (await createPos(s)).body;
  const live = (await createPos(s)).body;           //  هنوز در انتظار ⇒ نه
  const x = (await createPos(other)).body;
  await paid(a);
  await paid(b);
  await paid(x);
  let r = await mirror(s);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  assert.deepEqual(r.body.intents.map((i) => [i.refId, i.status, i.needsReview]), [[a.refId, 'paid', false], [b.refId, 'paid', false]]);
  assert.ok(r.body.intents.every((i) => i.method === 'hesabpay' && i.id && i.updatedAt > 0));
  assert.ok(!JSON.stringify(r.body).includes(live.refId) && !JSON.stringify(r.body).includes(x.refId), 'فقط پرداخت‌شدهٔ همین دکان');
  const cur = r.body.cursor;
  assert.match(cur, /^\d+~[A-Za-z0-9_]+$/);
  //  هیچ تغییری پس از cursor ⇒ خالی و همان cursor
  r = await mirror(s, `?after=${encodeURIComponent(cur)}`);
  assert.deepEqual(r.body.intents, []);
  assert.equal(r.body.cursor, cur);
  //  بازپرداختِ مدیر و نشانِ بررسی پس از cursor ⇒ فقط همان دو، با وضعیتِ تازه
  await new Promise((res) => setTimeout(res, 5));
  await pay.markRefunded(a.id, { note: 'آزمون' });
  await query('UPDATE payment_intents SET needs_review=true, updated_at=$2 WHERE id=$1', [b.id, now() + 1]);
  r = await mirror(s, `?after=${encodeURIComponent(cur)}`);
  assert.deepEqual(r.body.intents.map((i) => [i.refId, i.status, i.needsReview]), [[a.refId, 'refunded', false], [b.refId, 'paid', true]]);
  //  صفحه‌بندی: limit=1 ⇒ یکی‌یکی، cursor جلو می‌رود و تکرار نمی‌شود
  const p1 = await mirror(s, `?after=${encodeURIComponent(cur)}&limit=1`);
  const p2 = await mirror(s, `?after=${encodeURIComponent(p1.body.cursor)}&limit=1`);
  const p3 = await mirror(s, `?after=${encodeURIComponent(p2.body.cursor)}&limit=1`);
  assert.deepEqual([...p1.body.intents, ...p2.body.intents].map((i) => i.refId), [a.refId, b.refId]);
  assert.equal(p1.body.more, true);
  assert.deepEqual(p3.body.intents, []);
});

test('۴۰۱ بی توکن؛ ۴۰۳ نقشِ بی `data.write`؛ ۴۰۰ cursor/limit ِ بدشکل', async () => {
  const s = await shop('درها');
  await setFlag(s.shopId, FLAG, true);
  assert.equal((await h.get(URL_MIR)).status, 401);
  const inv = await h.post('/api/shop/invite', { role: 'accountant' }, { token: s.t });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const u = await h.newUser('حسابدارِ آینه', 'shop');
  const j = await h.post('/api/shop/join', { code: inv.body.code }, { token: u.accessToken });
  assert.ok([200, 201].includes(j.status), JSON.stringify(j.body));
  const v = await h.signIn(u, 'shop');
  const r = await h.get(URL_MIR, { token: v.accessToken });
  assert.equal(r.status, 403, JSON.stringify(r.body));
  for (const q of ['?after=abc', '?after=1~a%20b', '?after=99999999999999999~x', '?limit=0', '?limit=999', '?limit=x']) {
    const r = await mirror(s, q);
    assert.equal(r.status, 400, `${q} ${JSON.stringify(r.body)}`);
  }
});

test('۴۲۹: سقفِ نرخِ هر کاربر', async () => {
  const s = await shop('سقف');
  await setFlag(s.shopId, FLAG, true);
  let last = 0;
  for (let i = 0; i < 45; i++) { last = (await mirror(s)).status; if (last === 429) break; }
  assert.equal(last, 429);
});
