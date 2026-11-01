'use strict';
/**
 * پرداختِ حساب‌پی (PAY-21، PAY-08، S35) — `lib/payment-intents.js`، `routes/shop-payments.js`،
 * آداپتورِ `lib/payment-hesabpay.js` (روی سرورِ ساختگیِ حساب‌پی) و ارائه‌دهندهٔ `fake`.
 *
 * ماشینِ وضعیت، کلیدِ یکتایی، وب‌هوکِ تکراری، امضای بد، اختلافِ مبلغ/ارز، وب‌هوکِ دیر پس از
 * انقضا (S35)، دسترسیِ دکانِ دیگر، قاعدهٔ لغو، سرتاسریِ ساختگی با انتظارِ بلند، جمعِ سفارش.
 */
process.env.PAYMENTS_PROVIDER = 'fake';
//  رازِ ساختگی فقط از محیط (پیش‌فرضی در کد نیست)؛ helpers خودش NODE_ENV=test می‌گذارد
process.env.PAYMENTS_FAKE_SECRET = 'test-only-fake-secret-payment-intents';
process.env.RATE_PAY_CREATE_MAX = '100000';
process.env.RATE_PAY_POLL_MAX = '100000';
const test = require('node:test');
const assert = require('node:assert/strict');
const http = require('node:http');
const crypto = require('node:crypto');
const h = require('./helpers');
const { one, query, newId, now } = require('../src/db');
const P = require('../src/lib/payment-providers');
const pay = require('../src/lib/payment-intents');
const orders = require('../src/lib/orders');

let admin = '';
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'pay-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'pay-admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });

let seq = 0;
const key = () => `key-${Date.now()}-${++seq}-pay`;
const URL_INT = '/api/v1/shop/payments/intents';

async function setFlag(scope, k, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key: k, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shop(label, { flag = true } = {}) {
  const owner = await h.newUser(`صاحب-${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  if (flag) await setFlag(shopId, pay.FLAG_KEY, true);
  return { owner, shopId, t: owner.accessToken };
}

const createPos = (s, extra = {}) => h.post(URL_INT, {
  kind: 'pos_sale', refId: `sale-${++seq}`, amount: 145000, currency: 'AFN', idempotencyKey: key(), ...extra,
}, { token: s.t });

async function hook(body, { provider = 'fake', sig } = {}) {
  const buf = Buffer.from(JSON.stringify(body));
  return h.raw('POST', `/api/payments/webhook/${provider}`, buf,
    { headers: { 'Content-Type': 'application/json', 'x-fake-signature': sig ?? P.fake.sign(buf) } });
}
const rowOf = (id) => one('SELECT * FROM payment_intents WHERE id=$1', [id]);
/** «ارائه‌دهنده» می‌گوید پرداخت شد (تأییدِ دوم) و وب‌هوکش را می‌فرستد. */
async function paid(intent, { event = {}, status = {} } = {}) {
  const sid = (await rowOf(intent.id)).provider_session_id;
  P.fake.settle(sid, { status: 'paid', amountMinor: intent.amount, currency: 'AFN', providerRef: 'HP-TX-1', ...status });
  return hook({ eventId: `ev-${++seq}`, sessionId: sid, status: 'paid', amountMinor: intent.amount, currency: 'AFN', providerRef: 'HP-TX-1', ...event });
}

// ════════════════════════════════════════════════════════════════════

test('ماشینِ وضعیت: فقط گذارهای مجاز؛ پایانی برنمی‌گردد؛ دیر ⇒ paid فقط با تأییدِ ارائه‌دهنده', () => {
  const all = Object.keys(pay.TRANSITIONS);
  const ok = new Set(['created>pending', 'created>failed', 'created>expired', 'created>cancelled',
    'pending>paid', 'pending>failed', 'pending>expired', 'pending>cancelled', 'paid>refunded']);
  for (const a of all) for (const b of all) {
    assert.equal(pay.canTransition(a, b), ok.has(`${a}>${b}`), `${a} → ${b}`);
  }
  for (const a of ['expired', 'cancelled', 'failed']) {
    assert.equal(pay.canTransition(a, 'paid'), false);
    assert.equal(pay.canTransition(a, 'paid', { confirmedLate: true }), true);
  }
  for (const a of ['paid', 'refunded', 'created']) assert.equal(pay.canTransition(a, 'paid', { confirmedLate: true }), false, a);
});

test('پول: پول ⇄ دهدهیِ ارائه‌دهنده دقیق و بی گرد', () => {
  assert.equal(P.minorToDecimal(145050), '1450.50');
  assert.equal(P.minorToDecimal(5), '0.05');
  assert.equal(P.decimalToMinor('1450.5'), 145050);
  assert.equal(P.decimalToMinor(1450), 145000);
  assert.equal(P.decimalToMinor('1450.505'), null);
  assert.equal(P.decimalToMinor('-1'), null);
  assert.equal(P.decimalToMinor('1e3'), null);
  assert.equal(P.decimalToMinor(1e21), null);
});

test('ساختگی هرگز در تولید: PAYMENTS_PROVIDER=fake + NODE_ENV=production ⇒ حساب‌پیِ واقعی', () => {
  const prev = process.env.NODE_ENV;
  try {
    process.env.NODE_ENV = 'production';
    assert.equal(P.fakeAllowed(), false);
    assert.equal(P.activeName(), 'hesabpay');
    assert.equal(P.webhookAdapter('fake'), null);
  } finally { process.env.NODE_ENV = prev; }
  assert.equal(P.activeName(), 'fake');
});

test('پیکربندی: بی کلید یا بی پرچم ⇒ خاموش و ساختن ۴۰۳ hesabpay_disabled', async () => {
  const off = await shop('بی‌پرچم', { flag: false });
  assert.deepEqual((await h.get('/api/v1/shop/payments/config', { token: off.t })).body, { hesabpay: { enabled: false } });
  const r = await createPos(off);
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'hesabpay_disabled');

  const on = await shop('با‌پرچم');
  assert.deepEqual((await h.get('/api/shop/payments/config', { token: on.t })).body, { hesabpay: { enabled: true } });
  //  حساب‌پیِ واقعی بی HESABPAY_* ⇒ «پیکربندی نشده»، حتی با پرچم
  process.env.PAYMENTS_PROVIDER = '';
  try {
    assert.deepEqual((await h.get('/api/shop/payments/config', { token: on.t })).body, { hesabpay: { enabled: false } });
    assert.equal((await createPos(on)).body.error.code, 'hesabpay_disabled');
  } finally { process.env.PAYMENTS_PROVIDER = 'fake'; }
  assert.equal((await h.get('/api/shop/payments/config')).status, 401);
});

test('ساختن: قرارداد، کلیدِ یکتایی، اعتبارسنجی و قصدِ زندهٔ همان فروش', async () => {
  const s = await shop('ساختن');
  const k = key();
  const body = { kind: 'pos_sale', refId: 'sale-A1', amount: 145000, currency: 'AFN', idempotencyKey: k };
  const a = await h.post(URL_INT, body, { token: s.t });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  for (const f of ['id', 'status', 'checkoutUrl', 'qrPayload', 'expiresAt', 'amount', 'currency']) assert.ok(f in a.body, f);
  assert.equal(a.body.status, 'pending');
  assert.equal(a.body.amount, 145000);
  assert.equal(a.body.currency, 'AFN');
  assert.equal(a.body.qrPayload, a.body.checkoutUrl);
  assert.match(a.body.checkoutUrl, /^https:\/\//);
  assert.ok(a.body.expiresAt > Date.now());

  const again = await h.post(URL_INT, body, { token: s.t });
  assert.equal(again.status, 200);
  assert.equal(again.body.id, a.body.id);
  assert.equal(again.headers.get('idempotent-replayed'), 'true');
  const reused = await h.post(URL_INT, { ...body, amount: 999 }, { token: s.t });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'idempotency_key_reused');

  //  کلیدِ دیگر، همان فروش: همان مبلغ ⇒ همان قصدِ زنده؛ مبلغِ دیگر ⇒ ۴۰۹ با شناسه
  const same = await h.post(URL_INT, { ...body, idempotencyKey: key() }, { token: s.t });
  assert.equal(same.status, 200);
  assert.equal(same.body.id, a.body.id);
  const other = await h.post(URL_INT, { ...body, amount: 150000, idempotencyKey: key() }, { token: s.t });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'intent_active');
  assert.equal(other.body.error.intentId, a.body.id);

  const bad = [
    [{ amount: 0 }, 'bad_amount'], [{ amount: 1450.5 }, 'bad_amount'], [{ amount: '145000' }, 'bad_amount'],
    [{ amount: -5 }, 'bad_amount'], [{ currency: 'USD' }, 'bad_currency'], [{ currency: undefined }, 'bad_currency'],
    [{ kind: 'refund' }, 'bad_kind'], [{ refId: '' }, 'bad_ref'], [{ refId: 'a b' }, 'bad_ref'],
    [{ idempotencyKey: undefined }, 'idempotency_key_required'], [{ idempotencyKey: 'short' }, 'bad_idempotency_key'],
  ];
  for (const [patch, code] of bad) {
    const r = await h.post(URL_INT, { ...body, refId: 'sale-bad', idempotencyKey: key(), ...patch }, { token: s.t });
    assert.equal(r.status, 400, `${JSON.stringify(patch)} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, code, JSON.stringify(patch));
  }
  //  shop_id ِ بدنه هرگز خوانده نمی‌شود
  const b = await shop('ساختنِ ب');
  const viaBody = await h.post(URL_INT, { ...body, idempotencyKey: key(), refId: 'sale-X', shopId: s.shopId, shop_id: s.shopId }, { token: b.t });
  assert.equal(viaBody.status, 201);
  assert.equal((await rowOf(viaBody.body.id)).shop_id, b.shopId);
});

test('سرتاسریِ ساختگی: انتظارِ بلند با وب‌هوک «paid» برمی‌گردد؛ ردیفِ پرداختِ فروش با مرجع', async () => {
  const s = await shop('سرتاسری');
  const a = (await createPos(s)).body;
  const t0 = Date.now();
  const poll = h.get(`${URL_INT}/${a.id}?wait=20`, { token: s.t });
  await new Promise((r) => setTimeout(r, 300));
  const w = await paid(a);
  assert.equal(w.status, 200, JSON.stringify(w.body));
  assert.equal(w.body.outcome, 'applied');
  const got = await poll;
  assert.equal(got.status, 200);
  assert.equal(got.body.status, 'paid');
  assert.ok(Date.now() - t0 < 10_000, 'انتظار با رویداد تمام شد، نه با مهلت');
  assert.equal(got.body.providerRef, 'HP-TX-1');
  assert.ok(got.body.paidAt > 0);
  assert.deepEqual(got.body.payment, {
    method: 'hesabpay', kind: 'wallet', amount: 145000, ref: 'HP-TX-1', status: 'confirmed', verification: 'gateway', intentId: a.id,
  });
  //  پرداخت‌شده ⇒ قصدِ تازه برای همان فروش نه
  const again = await createPos(s, { refId: a.refId });
  assert.equal(again.status, 409);
  assert.equal(again.body.error.code, 'already_paid');
  //  انتظار روی قصدِ پایانی فوری برمی‌گردد
  const t1 = Date.now();
  assert.equal((await h.get(`${URL_INT}/${a.id}?wait=25`, { token: s.t })).body.status, 'paid');
  assert.ok(Date.now() - t1 < 2000);
});

test('وب‌هوکِ تکراری و ترتیبِ بی‌اهمیت: یک بار اعمال؛ «failed» ِ بعدی paid را پایین نمی‌آورد', async () => {
  const s = await shop('تکراری');
  const a = (await createPos(s)).body;
  const sid = (await rowOf(a.id)).provider_session_id;
  P.fake.settle(sid, { status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'R-1' });
  const ev = { eventId: 'dup-1', sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'R-1' };
  const [x, y] = await Promise.all([hook(ev), hook(ev)]);
  //  یکی اعمال؛ دیگری «تکراری»، یا اگر وسطِ پردازشِ اولی رسید ۵۰۳ ِ «در حالِ پردازش» (ارائه‌دهنده دوباره می‌فرستد)
  const outs = [x, y].map((r) => (r.status === 503 ? 'retry' : r.body.duplicate ? 'duplicate' : r.body.outcome));
  assert.equal(outs.filter((o) => o === 'applied').length, 1, JSON.stringify(outs));
  assert.ok(outs.every((o) => ['applied', 'duplicate', 'retry'].includes(o)), JSON.stringify(outs));
  const v1 = (await rowOf(a.id)).version;
  assert.equal((await hook(ev)).body.duplicate, true);
  //  رویدادِ دیگرِ هم‌میلی‌ثانیه با «failed» و «paid» ِ دوم
  const [f, p2] = await Promise.all([
    hook({ eventId: 'late-fail', sessionId: sid, status: 'failed' }),
    hook({ ...ev, eventId: 'paid-2' }),
  ]);
  assert.equal(f.body.outcome, 'duplicate_state');
  assert.equal(p2.body.outcome, 'duplicate_state');
  const row = await rowOf(a.id);
  assert.equal(row.status, 'paid');
  assert.equal(row.version, v1);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM payment_events WHERE intent_id=$1', [a.id])).n), 3);
});

test('امضای بد، بدنهٔ خالی یا بی امضا ⇒ ۴۰۱ و هیچ تغییری؛ قصدِ ناشناخته ⇒ ۲۰۰ و لاگ', async () => {
  const s = await shop('امضا');
  const a = (await createPos(s)).body;
  const sid = (await rowOf(a.id)).provider_session_id;
  const ev = { eventId: 'sig-1', sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN' };
  assert.equal((await hook(ev, { sig: 'deadbeef' })).status, 401);
  assert.equal((await hook(ev, { sig: '' })).status, 401);
  assert.equal((await h.raw('POST', '/api/payments/webhook/fake', Buffer.alloc(0))).status, 401);
  assert.equal((await h.post('/api/payments/webhook/hesabpay', ev)).status, 401, 'حساب‌پیِ پیکربندی‌نشده هم ۴۰۱');
  assert.equal((await rowOf(a.id)).status, 'pending');
  assert.equal(await one('SELECT 1 AS x FROM payment_events WHERE provider_event_id=$1', ['fake:sig-1']), null);

  const u = await hook({ eventId: 'unk-1', sessionId: 'fake_nope', status: 'paid', amountMinor: 1, currency: 'AFN' });
  assert.equal(u.status, 200);
  assert.equal(u.body.ignored, 'unknown_intent');
  assert.equal((await hook({ eventId: 'unk-1', sessionId: 'fake_nope', status: 'paid', amountMinor: 1, currency: 'AFN' })).body.duplicate, true);
});

test('اختلافِ مبلغ یا ارز ⇒ paid نمی‌شود، بررسی و هشدار؛ تأییدِ دوم با مبلغِ دیگر هم همین', async () => {
  const s = await shop('اختلاف');
  for (const [i, ev, st] of [
    [1, { amountMinor: 100 }, {}],
    [2, { currency: 'USD' }, {}],
    [3, {}, { amountMinor: 1 }],
    [4, {}, { currency: 'USD' }],
  ]) {
    const a = (await createPos(s)).body;
    const r = await paid(a, { event: { eventId: `mm-${i}-${Date.now()}`, ...ev }, status: st });
    assert.equal(r.status, 200);
    assert.equal(r.body.outcome, 'mismatch', `${i}`);
    const row = await rowOf(a.id);
    assert.equal(row.status, 'pending', `${i}`);
    assert.equal(row.needs_review, true, `${i}`);
    assert.ok(await one(`SELECT 1 AS x FROM audit_logs WHERE action='payment.review' AND target_id=$1`, [a.id]));
  }
});

test('تأییدِ دوم: «نه» ِ قطعیِ ارائه‌دهنده ⇒ unconfirmed؛ دست‌نیافتنی ⇒ ۵۰۳ و همان رویداد بعداً اعمال می‌شود', async () => {
  const s = await shop('تأیید');
  const a = (await createPos(s)).body;
  const r1 = await paid(a, { event: { eventId: 'cf-1' }, status: { status: 'failed' } });
  assert.equal(r1.status, 200);
  assert.equal(r1.body.outcome, 'unconfirmed');
  assert.equal((await rowOf(a.id)).status, 'pending');

  process.env.PAYMENTS_FAKE_STATUS_DOWN = '1';
  let r2;
  try { r2 = await paid(a, { event: { eventId: 'cf-2' } }); } finally { delete process.env.PAYMENTS_FAKE_STATUS_DOWN; }
  assert.equal(r2.status, 503);
  assert.equal(r2.body.error.code, 'retry_later');
  assert.equal((await rowOf(a.id)).status, 'pending');
  const r3 = await paid(a, { event: { eventId: 'cf-2' } });
  assert.equal(r3.status, 200);
  assert.equal(r3.body.outcome, 'applied');
  assert.equal((await rowOf(a.id)).status, 'paid');
});

//  بازبینِ مستقل (وارونهٔ zz-rev-pay): پیش از این «paid» ِ امضاشده‌ای که درِ وضعیت هنوز pending می‌گفت
//  `unconfirmed` با ۲۰۰ می‌گرفت و رویدادش نهایی می‌شد ⇒ ارسالِ دوباره «تکراری» و پولِ گرفته‌شده بی پرداخت می‌ماند.
test('تأییدِ دومِ دیرکرده (درِ وضعیت هنوز pending/unknown) ⇒ ۵۰۳ و رویداد نهایی نمی‌شود؛ ارسالِ دوباره پس از هم‌گامی ⇒ paid', async () => {
  const s = await shop('تأییدِ دیرکرده');
  const a = (await createPos(s)).body;
  const sid = (await rowOf(a.id)).provider_session_id;
  P.fake.settle(sid, { status: 'pending', amountMinor: a.amount, currency: 'AFN', providerRef: 'TX' });
  const ev = { eventId: `lag-${++seq}`, sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'TX' };
  const outcomeOf = async () => (await one('SELECT outcome FROM payment_events WHERE provider_event_id=$1', [`fake:${ev.eventId}`])).outcome;

  const r1 = await hook(ev);
  assert.equal(r1.status, 503, JSON.stringify(r1.body));
  assert.equal(r1.body.error.code, 'retry_later');
  assert.equal(await outcomeOf(), 'deferred');
  assert.equal((await rowOf(a.id)).status, 'pending');
  //  «unknown» (ارائه‌دهنده جلسه را هنوز نمی‌شناسد) هم همین
  P.fake.settle(sid, { status: 'unknown' });
  assert.equal((await hook(ev)).status, 503);
  assert.equal(await outcomeOf(), 'deferred');

  //  هم‌گام شد و همان رویداد دوباره آمد ⇒ اعمال، نه «تکراری»
  P.fake.settle(sid, { status: 'paid' });
  const r2 = await hook(ev);
  assert.equal(r2.status, 200, JSON.stringify(r2.body));
  assert.notEqual(r2.body.duplicate, true);
  assert.equal(r2.body.outcome, 'applied');
  assert.equal((await rowOf(a.id)).status, 'paid');
  assert.equal(await outcomeOf(), 'applied');
  //  پس از اعمال، ارسالِ سوم واقعاً تکراری است
  assert.equal((await hook(ev)).body.duplicate, true);

  //  امضا روی بدنهٔ خام: همان JSON با فاصلهٔ دیگر و امضای بدنهٔ فشرده ⇒ ۴۰۱
  const other = { ...ev, eventId: `lag-${++seq}` };
  const spaced = Buffer.from(JSON.stringify(other, null, 1));
  const r3 = await h.raw('POST', '/api/payments/webhook/fake', spaced,
    { headers: { 'Content-Type': 'application/json', 'x-fake-signature': P.fake.sign(Buffer.from(JSON.stringify(other))) } });
  assert.equal(r3.status, 401);
});

const reviewsOf = (id, reason) => one(
  `SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='payment.review' AND target_id=$1 AND detail->>'reason'=$2`, [id, reason]
).then((r) => Number(r.n));
const lastReview = (id, reason) => one(
  `SELECT detail FROM audit_logs WHERE action='payment.review' AND target_id=$1 AND detail->>'reason'=$2
    ORDER BY created_at DESC LIMIT 1`, [id, reason]
);
const eventOutcome = async (eventId) => (await one('SELECT outcome FROM payment_events WHERE provider_event_id=$1', [`fake:${eventId}`])).outcome;

//  بازبینِ مستقل، دورِ ۲: درِ وضعیت همیشه unknown ⇒ تطبیق قصد را expired می‌کرد، needs_review=false، بی هیچ سابقه
test('«paid» ِ امضاشده‌ای که هرگز تأیید نشد بی‌صدا منقضی نمی‌شود: needs_review و هشدارِ paid_unconfirmed؛ هرگز خودکار پرداخت‌شده نه', async () => {
  const s = await shop('هرگز-تأیید');
  const a = (await createPos(s)).body;
  const sid = (await rowOf(a.id)).provider_session_id;
  P.fake.settle(sid, { status: 'unknown', amountMinor: null, currency: '' });
  const ev = { eventId: `nv-${++seq}`, sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'TX-NV' };
  assert.equal((await hook(ev)).status, 503);
  assert.equal((await rowOf(a.id)).needs_review, false, 'تا قصد زنده است فقط «بعداً»');

  //  زمانِ قصد گذشت؛ زمان‌بندِ تطبیق آن را می‌بندد (ارائه‌دهنده هنوز unknown)
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [a.id, Date.now() - 1000]);
  const out = await pay.reconcileTick({ wide: false });
  assert.ok(out && out.closed >= 1, JSON.stringify(out));
  let row = await rowOf(a.id);
  assert.equal(row.status, 'expired');
  assert.equal(row.needs_review, true, 'دکان باید ببیند');
  const al = await lastReview(a.id, 'paid_unconfirmed');
  assert.ok(al, 'سابقهٔ payment.review');
  assert.equal(al.detail.cause, 'never_confirmed');
  assert.equal(al.detail.eventId, `fake:${ev.eventId}`);
  assert.equal(await eventOutcome(ev.eventId), 'deferred', 'رویداد هنوز دوباره‌پذیر است');

  //  ارسالِ دوباره با همان unknown ⇒ باز ۵۰۳، بی هشدارِ دوم و بی پرداخت
  assert.equal((await hook(ev)).status, 503);
  assert.equal(await reviewsOf(a.id, 'paid_unconfirmed'), 1);
  assert.equal((await rowOf(a.id)).status, 'expired');

  //  ارائه‌دهنده سرانجام تأیید کرد ⇒ همان مسیرِ S35 (پرداختِ دیر با بررسی)
  P.fake.settle(sid, { status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'TX-NV' });
  assert.equal((await hook(ev)).body.outcome, 'applied');
  row = await rowOf(a.id);
  assert.equal(row.status, 'paid');
  assert.equal(row.needs_review, true);

  //  بستن بیرون از تطبیق (لغوِ صندوقدار، یا انقضای تنبل با خواندن) ⇒ همان دم، بی انتظار برای نوبتِ تطبیق
  for (const how of ['cancel', 'poll']) {
    const c = (await createPos(s)).body;
    const csid = (await rowOf(c.id)).provider_session_id;
    P.fake.settle(csid, { status: 'unknown', amountMinor: null, currency: '' });
    assert.equal((await hook({ ...ev, eventId: `nv-${++seq}`, sessionId: csid, amountMinor: c.amount })).status, 503);
    if (how === 'cancel') {
      assert.equal((await h.post(`${URL_INT}/${c.id}/cancel`, {}, { token: s.t })).status, 200);
    } else {
      await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [c.id, Date.now() - 1000]);
      assert.equal((await h.get(`${URL_INT}/${c.id}`, { token: s.t })).body.status, 'expired');
    }
    const cr = await rowOf(c.id);
    assert.equal(cr.needs_review, true, how);
    assert.equal((await lastReview(c.id, 'paid_unconfirmed')).detail.source, how === 'cancel' ? 'cancelled_by_staff' : 'timeout', how);
    assert.notEqual(cr.status, 'paid', how);
  }
});

test('«paid» ِ deferred روی قصدی که پیش‌تر بسته شده ⇒ همان دم نشانِ بررسی؛ تورِ ایمنیِ تطبیق برای قصدِ بستهٔ بی‌نشان', async () => {
  const s = await shop('دیرِ-بعداً');
  //  قصدِ منقضی که «paid» ِ دیرش می‌رسد و درِ وضعیت هنوز pending است
  const a = (await createPos(s)).body;
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [a.id, Date.now() - 1000]);
  assert.equal((await h.get(`${URL_INT}/${a.id}`, { token: s.t })).body.status, 'expired');
  const sid = (await rowOf(a.id)).provider_session_id;
  P.fake.settle(sid, { status: 'pending', amountMinor: a.amount, currency: 'AFN' });
  const r = await hook({ eventId: `lt-${++seq}`, sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN' });
  assert.equal(r.status, 503);
  assert.equal((await rowOf(a.id)).needs_review, true);
  assert.equal((await lastReview(a.id, 'paid_unconfirmed')).detail.source, 'webhook');
  assert.equal((await rowOf(a.id)).status, 'expired');

  //  قصدِ بسته‌ای که رویدادِ deferred دارد ولی نشانش نخورده (فرآیند میانِ بستن و نشان مُرد) ⇒ تطبیق نشانش می‌زند
  const b = (await createPos(s)).body;
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [b.id, Date.now() - 1000]);
  assert.equal((await h.get(`${URL_INT}/${b.id}`, { token: s.t })).body.status, 'expired');
  await query(
    `INSERT INTO payment_events (id, provider, provider_event_id, intent_id, type, outcome, raw, received_at)
     VALUES ($1,'fake',$2,$3,'paid','deferred','{}'::jsonb,$4)`, [newId('pe'), `fake:stuck-${++seq}`, b.id, Date.now()]
  );
  assert.equal((await rowOf(b.id)).needs_review, false);
  const out = await pay.reconcile({ sinceMs: 60 * 60 * 1000 });
  assert.ok(out.flagged >= 1, JSON.stringify(out));
  assert.equal((await rowOf(b.id)).needs_review, true);
  assert.equal((await lastReview(b.id, 'paid_unconfirmed')).detail.source, 'reconcile');
  assert.equal((await rowOf(b.id)).status, 'expired', 'هرگز خودکار پرداخت‌شده نه');
  //  دورِ بعد همان را دوباره نشان نمی‌زند
  await pay.reconcile({ sinceMs: 60 * 60 * 1000 });
  assert.equal(await reviewsOf(b.id, 'paid_unconfirmed'), 1);
});

test('تناقض: «paid» ِ امضاشده و درِ وضعیت قطعاً failed ⇒ unconfirmed با needs_review و هشدار، بی پرداخت', async () => {
  const s = await shop('تناقض');
  const a = (await createPos(s)).body;
  const r = await paid(a, { event: { eventId: `ct-${++seq}` }, status: { status: 'failed' } });
  assert.equal(r.status, 200);
  assert.equal(r.body.outcome, 'unconfirmed');
  const row = await rowOf(a.id);
  assert.equal(row.status, 'pending');
  assert.equal(row.needs_review, true);
  const al = await lastReview(a.id, 'paid_unconfirmed');
  assert.ok(al);
  assert.equal(al.detail.cause, 'provider_says_failed');
  //  تکرارِ تناقض ⇒ همان یک سابقه
  await paid(a, { event: { eventId: `ct-${++seq}` }, status: { status: 'failed' } });
  assert.equal(await reviewsOf(a.id, 'paid_unconfirmed'), 1);
});

test('رویدادِ در حالِ پردازش: ارسالِ دوباره ۵۰۳ (نه «تکراری»)؛ پردازشی که مُرد پس از اجاره دوباره‌پذیر است، فقط یک بار', async () => {
  const s = await shop('در-کار');
  const mk = async () => {
    const a = (await createPos(s)).body;
    const sid = (await rowOf(a.id)).provider_session_id;
    P.fake.settle(sid, { status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'IP' });
    const ev = { eventId: `ip-${++seq}`, sessionId: sid, status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'IP' };
    return { a, ev, key: `fake:${ev.eventId}` };
  };
  const plant = (key, outcome, receivedAt) => query(
    `INSERT INTO payment_events (id, provider, provider_event_id, type, outcome, raw, received_at)
     VALUES ($1,'fake',$2,'paid',$3,'{}'::jsonb,$4)`, [newId('pe'), key, outcome, receivedAt]
  );
  const set = (key, outcome, receivedAt) => query(
    'UPDATE payment_events SET outcome=$2, received_at=$3 WHERE provider_event_id=$1', [key, outcome, receivedAt]
  );

  const x = await mk();
  //  فرآیندی که رویداد را گرفته و هنوز در کار است (اجارهٔ زنده)
  await plant(x.key, `processing:${Date.now() + 60_000}`, Date.now());
  const r1 = await hook(x.ev);
  assert.equal(r1.status, 503, JSON.stringify(r1.body));
  assert.equal(r1.body.error.code, 'retry_later');
  //  ردیفِ پیش از این نسخه: outcome ِ تهی و تازه ⇒ همان ۵۰۳
  await set(x.key, '', Date.now());
  assert.equal((await hook(x.ev)).status, 503);
  assert.equal((await rowOf(x.a.id)).status, 'pending');
  //  فرآیند مُرد: پس از اجاره همین ارسال اعمال می‌شود؛ بعد واقعاً تکراری
  await set(x.key, '', Date.now() - 3 * 60_000);
  const r3 = await hook(x.ev);
  assert.equal(r3.status, 200, JSON.stringify(r3.body));
  assert.equal(r3.body.outcome, 'applied');
  assert.equal((await rowOf(x.a.id)).status, 'paid');
  assert.equal(await eventOutcome(x.ev.eventId), 'applied');
  assert.equal((await hook(x.ev)).body.duplicate, true);

  //  اجارهٔ گذشته با قالبِ تازه
  const y = await mk();
  await plant(y.key, `processing:${Date.now() - 1}`, Date.now() - 1000);
  assert.equal((await hook(y.ev)).body.outcome, 'applied');

  //  دو ارسالِ هم‌زمانِ یک ردیفِ مانده ⇒ فقط یکی می‌گیرد و اعمال می‌کند
  const z = await mk();
  await plant(z.key, '', Date.now() - 3 * 60_000);
  const v0 = (await rowOf(z.a.id)).version;
  const both = await Promise.all([hook(z.ev), hook(z.ev)]);
  const outs = both.map((r) => (r.status === 503 ? 'retry' : r.body.duplicate ? 'duplicate' : r.body.outcome));
  assert.equal(outs.filter((o) => o === 'applied').length, 1, JSON.stringify(outs));
  assert.ok(outs.every((o) => ['applied', 'retry', 'duplicate'].includes(o)), JSON.stringify(outs));
  assert.equal((await rowOf(z.a.id)).version, v0 + 1, 'یک گذار');
});

test('ساختگی فقط با NODE_ENV ِ صریحِ test/development و رازِ داده‌شده؛ وگرنه حساب‌پی و /webhook/fake ۴۰۱', async () => {
  const prevEnv = process.env.NODE_ENV;
  const prevSecret = process.env.PAYMENTS_FAKE_SECRET;
  const body = { eventId: `env-${++seq}`, sessionId: 'x', status: 'paid', amountMinor: 1, currency: 'AFN' };
  const buf = Buffer.from(JSON.stringify(body));
  const sig = P.fake.sign(buf);
  const restore = () => {
    if (prevEnv === undefined) delete process.env.NODE_ENV; else process.env.NODE_ENV = prevEnv;
    process.env.PAYMENTS_FAKE_SECRET = prevSecret;
  };
  try {
    for (const env of ['production', undefined, '', 'staging', 'prod', 'Production', 'testing']) {
      if (env === undefined) delete process.env.NODE_ENV; else process.env.NODE_ENV = env;
      assert.equal(P.fakeAllowed(), false, `NODE_ENV=${env}`);
      assert.equal(P.activeName(), 'hesabpay', `NODE_ENV=${env}`);
      assert.equal(P.webhookAdapter('fake'), null, `NODE_ENV=${env}`);
      assert.equal((await hook(body, { sig })).status, 401, `NODE_ENV=${env}`);
    }
    process.env.NODE_ENV = 'development';
    assert.equal(P.fakeAllowed(), true);
    //  بی راز ⇒ «پیکربندی نشده»؛ رازِ پیش‌فرضِ قدیمی دیگر هیچ دری را باز نمی‌کند
    delete process.env.PAYMENTS_FAKE_SECRET;
    assert.equal(P.fake.configured(), false);
    assert.equal(P.fakeAllowed(), false);
    assert.equal(P.activeName(), 'hesabpay');
    assert.throws(() => P.fake.sign(buf), /PAYMENTS_FAKE_SECRET/);
    const legacy = crypto.createHmac('sha256', 'fake-webhook-secret-dev-only').update(buf).digest('hex');
    assert.equal((await P.fake.verifyWebhook(buf, { 'x-fake-signature': legacy })).ok, false);
    assert.equal((await hook(body, { sig: legacy })).status, 401);
  } finally { restore(); }
  assert.equal(P.activeName(), 'fake');
});

test('آداپتورِ حساب‌پی فقط https: http ِ بیرونی هرگز (کلید نمی‌رود)؛ http ِ loopback فقط در آزمون/توسعه', async () => {
  const HP = require('../src/lib/payment-hesabpay');
  const names = ['NODE_ENV', 'HESABPAY_API_KEY', 'HESABPAY_WEBHOOK_SECRET', 'HESABPAY_BASE_URL'];
  const prev = Object.fromEntries(names.map((k) => [k, process.env[k]]));
  Object.assign(process.env, { HESABPAY_API_KEY: 'test-key-123', HESABPAY_WEBHOOK_SECRET: 'whsec-test' });
  try {
    const cases = [
      ['https://api.hesab.com/api/v1', 'production', true],
      ['https://api.hesab.com/api/v1', 'test', true],
      ['HTTPS://api.hesab.com/api/v1', 'production', true],
      ['http://api.hesab.com/api/v1', 'production', false],
      ['http://api.hesab.com/api/v1', 'test', false],
      ['http://127.0.0.1:9/api/v1', 'test', true],
      ['http://localhost:9/api/v1', 'development', true],
      ['http://[::1]:9/api/v1', 'test', true],
      ['http://127.0.0.1:9/api/v1', 'production', false],
      ['http://127.0.0.1:9/api/v1', 'staging', false],
      ['http://localhost.evil.example/api', 'test', false],
      ['http://127.0.0.1.nip.io/api', 'test', false],
      ['http://127.0.0.1@evil.example/api', 'test', false],
      ['ftp://api.hesab.com/api/v1', 'test', false],
      ['https://', 'production', false],
    ];
    for (const [base, env, ok] of cases) {
      process.env.HESABPAY_BASE_URL = base;
      process.env.NODE_ENV = env;
      assert.equal(HP.configured(), ok, `${env} ${base}`);
    }
    //  حتی اگر کسی بی configured() صدا بزند: درخواستی با کلید روی http نمی‌رود
    process.env.NODE_ENV = 'production';
    process.env.HESABPAY_BASE_URL = 'http://api.hesab.invalid/api/v1';
    await assert.rejects(HP.fetchStatus('HS-1'), /https/);
  } finally {
    for (const [k, v] of Object.entries(prev)) { if (v === undefined) delete process.env[k]; else process.env[k] = v; }
  }
});

test('مهلتِ هر درخواست به حساب‌پی کف و سقف دارد و همیشه زیرِ اجارهٔ پردازشِ رویداد می‌ماند', () => {
  const HP = require('../src/lib/payment-hesabpay');
  const prev = process.env.HESABPAY_TIMEOUT_MS;
  try {
    const cases = [
      [undefined, 10_000], ['', 10_000], ['abc', 10_000], ['0', 10_000],
      ['-5', 1000], ['500', 1000], ['20000', 20_000], ['30000', 30_000],
      ['120000', 30_000], ['600000', 30_000], ['Infinity', 30_000],
    ];
    for (const [raw, want] of cases) {
      if (raw === undefined) delete process.env.HESABPAY_TIMEOUT_MS; else process.env.HESABPAY_TIMEOUT_MS = raw;
      assert.equal(HP.requestTimeoutMs(), want, `HESABPAY_TIMEOUT_MS=${raw}`);
    }
  } finally {
    if (prev === undefined) delete process.env.HESABPAY_TIMEOUT_MS; else process.env.HESABPAY_TIMEOUT_MS = prev;
  }
  //  پردازشِ یک رویداد (تأییدِ دوم) با بیشترین مهلت هم پیش از پایانِ اجاره تمام می‌شود
  assert.ok(HP.MAX_TIMEOUT_MS * 2 < pay.EVENT_LEASE_MS, `${HP.MAX_TIMEOUT_MS} در برابرِ ${pay.EVENT_LEASE_MS}`);
});

test('S35: وب‌هوکِ دیر پس از انقضا ⇒ paid فقط با تأییدِ ارائه‌دهنده، با هشدارِ تطبیق', async () => {
  const s = await shop('دیر');
  const a = (await createPos(s)).body;
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [a.id, Date.now() - 1000]);
  assert.equal((await h.get(`${URL_INT}/${a.id}`, { token: s.t })).body.status, 'expired');

  //  ارائه‌دهنده هنوز نمی‌گوید ⇒ همان expired
  const no = await paid(a, { event: { eventId: 'late-0' }, status: { status: 'expired' } });
  assert.equal(no.body.outcome, 'unconfirmed');
  assert.equal((await rowOf(a.id)).status, 'expired');

  const yes = await paid(a, { event: { eventId: 'late-1' } });
  assert.equal(yes.body.outcome, 'applied');
  const row = await rowOf(a.id);
  assert.equal(row.status, 'paid');
  assert.equal(row.needs_review, true);
  assert.equal(row.status_reason, 'late_payment_after_expired');
  const al = await one(`SELECT detail FROM audit_logs WHERE action='payment.review' AND target_id=$1 ORDER BY created_at DESC LIMIT 1`, [a.id]);
  assert.equal(al.detail.reason, 'late_payment');

  //  دیر حتی با تأییدِ خاموش هم تأیید می‌خواهد: «نه» ِ قطعی ⇒ unconfirmed؛ هنوز pending ⇒ ۵۰۳ (بعداً)
  const b = (await createPos(s)).body;
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [b.id, Date.now() - 1000]);
  process.env.PAYMENTS_CONFIRM_WEBHOOK = '0';
  try {
    const r = await paid(b, { event: { eventId: 'late-2' }, status: { status: 'cancelled' } });
    assert.equal(r.body.outcome, 'unconfirmed');
    const w = await paid(b, { event: { eventId: 'late-3' }, status: { status: 'pending' } });
    assert.equal(w.status, 503);
  } finally { delete process.env.PAYMENTS_CONFIRM_WEBHOOK; }
  assert.equal((await rowOf(b.id)).status, 'expired');
});

test('لغو: فقط از created/pending؛ تکرار بی‌اثر؛ پرداخت‌شده ۴۰۹؛ پرداختِ دیرِ لغوشده ⇒ بررسی', async () => {
  const s = await shop('لغو');
  const a = (await createPos(s)).body;
  const c1 = await h.post(`${URL_INT}/${a.id}/cancel`, {}, { token: s.t });
  assert.equal(c1.status, 200);
  assert.equal(c1.body.status, 'cancelled');
  assert.equal((await h.post(`${URL_INT}/${a.id}/cancel`, {}, { token: s.t })).body.status, 'cancelled');
  //  پس از لغو، قصدِ تازه برای همان فروش مجاز است
  assert.equal((await createPos(s, { refId: a.refId })).status, 201);
  const late = await paid(a, { event: { eventId: 'cx-late' } });
  assert.equal(late.body.outcome, 'applied');
  assert.equal((await rowOf(a.id)).needs_review, true);

  const b = (await createPos(s)).body;
  await paid(b);
  const c2 = await h.post(`${URL_INT}/${b.id}/cancel`, {}, { token: s.t });
  assert.equal(c2.status, 409);
  assert.equal(c2.body.error.code, 'not_cancellable');
  assert.equal(c2.body.error.status, 'paid');
});

test('دکانِ دیگر و پیک: نه می‌بیند، نه لغو می‌کند؛ بی توکن ۴۰۱', async () => {
  const a = await shop('مرزِ الف');
  const b = await shop('مرزِ ب');
  const it = (await createPos(a)).body;
  for (const [m, p] of [['GET', `${URL_INT}/${it.id}`], ['POST', `${URL_INT}/${it.id}/cancel`]]) {
    const r = await h.api(m, p, { token: b.t, body: m === 'POST' ? { shopId: a.shopId } : null, headers: { 'X-Shop-Id': a.shopId } });
    assert.equal(r.status, 404, `${m} ${p}`);
    assert.ok(!JSON.stringify(r.body).includes(it.id));
    assert.equal((await h.api(m, p, { body: m === 'POST' ? {} : null })).status, 401);
  }
  assert.equal((await rowOf(it.id)).status, 'pending');
});

test('سفارشِ آنلاین: مبلغ = جمعِ سرور، فقط دکانِ مشترک؛ paid ⇒ پرداختِ سفارش «آنلاین/پرداخت‌شده»', async () => {
  const s = await shop('سفارش');
  const { order } = await orders.create(s.shopId, {
    key: key(), actor: { type: 'customer', id: '' },
    body: { source: 'phone', fulfillment: 'pickup', paymentMethod: 'cod', customer: { name: 'مشتری', phone: '0791234567' },
      lines: [{ name: 'برنج', qtyMilli: 2000, unitPriceMinor: 5000 }] },
  });
  const total = order.totalMinor;
  assert.equal(total, 10000);
  const body = (extra = {}) => ({ kind: 'online_order', refId: order.id, amount: total, currency: 'AFN', idempotencyKey: key(), ...extra });

  //  دورهٔ آزمایشی ⇒ نه
  const trial = await h.post(URL_INT, body(), { token: s.t });
  assert.equal(trial.status, 403);
  assert.equal(trial.body.error.code, 'online_payment_unavailable');
  const g = await h.post('/api/admin/subscriptions', { shopId: s.shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));

  const wrong = await h.post(URL_INT, body({ amount: total - 1 }), { token: s.t });
  assert.equal(wrong.status, 409);
  assert.equal(wrong.body.error.code, 'amount_mismatch');
  assert.equal(wrong.body.error.totalMinor, total);
  assert.equal((await h.post(URL_INT, body({ refId: 'no-such-order' }), { token: s.t })).body.error.code, 'order_not_found');

  const it = await h.post(URL_INT, body(), { token: s.t });
  assert.equal(it.status, 201, JSON.stringify(it.body));
  let o = await one('SELECT payment_status, payment_method, cod_status FROM online_orders WHERE id=$1', [order.id]);
  assert.equal(o.payment_status, 'pending');
  assert.equal((await paid(it.body)).body.outcome, 'applied');
  o = await one('SELECT payment_status, payment_method, cod_status FROM online_orders WHERE id=$1', [order.id]);
  assert.deepEqual({ ...o }, { payment_status: 'paid', payment_method: 'online', cod_status: '' });
  const evs = (await query(`SELECT from_status, to_status FROM online_order_events WHERE order_id=$1 AND kind='payment' ORDER BY seq`, [order.id])).rows;
  assert.deepEqual(evs.map((e) => `${e.from_status}>${e.to_status}`), ['unpaid>pending', 'pending>paid']);
  //  رویدادهای سفارش پیوسته ماندند (verify-data)
  const seqs = (await query('SELECT seq FROM online_order_events WHERE order_id=$1 ORDER BY seq', [order.id])).rows.map((r) => r.seq);
  assert.deepEqual(seqs, seqs.map((_, i) => i + 1));
  const again = await h.post(URL_INT, body(), { token: s.t });
  assert.equal(again.status, 409);
  assert.ok(['already_paid', 'order_not_payable'].includes(again.body.error.code));
});

test('آداپتورِ حساب‌پی روی سرورِ ساختگیِ حساب‌پی: جلسه با کلید، امضای HMAC، تأییدِ دوم و ردِ امضای بد', async () => {
  const seen = [];
  const srv = http.createServer((req, res) => {
    let b = '';
    req.on('data', (d) => { b += d; });
    req.on('end', () => {
      seen.push({ method: req.method, url: req.url, auth: req.headers.authorization, body: b ? JSON.parse(b) : null });
      res.setHeader('Content-Type', 'application/json');
      if (req.method === 'POST' && req.url === '/api/v1/payment/create-session') {
        return res.end(JSON.stringify({ url: 'https://pay.hesab.example/s/abc', session_id: 'HS-1' }));
      }
      if (req.method === 'GET' && req.url === '/api/v1/payment/status/HS-1') {
        return res.end(JSON.stringify({ data: { status: 'completed', amount: '1450.00', transaction_id: 'TX-9' } }));
      }
      res.statusCode = 404;
      res.end('{}');
    });
  });
  await new Promise((r) => srv.listen(0, '127.0.0.1', r));
  const env = {
    PAYMENTS_PROVIDER: '', HESABPAY_API_KEY: 'test-key-123', HESABPAY_WEBHOOK_SECRET: 'whsec-test',
    HESABPAY_BASE_URL: `http://127.0.0.1:${srv.address().port}/api/v1`,
  };
  const prev = Object.fromEntries(Object.keys(env).map((k) => [k, process.env[k]]));
  Object.assign(process.env, env);
  try {
    const s = await shop('حساب‌پیِ واقعی');
    assert.equal((await h.get('/api/shop/payments/config', { token: s.t })).body.hesabpay.enabled, true);
    const a = await createPos(s);
    assert.equal(a.status, 201, JSON.stringify(a.body));
    assert.equal(a.body.checkoutUrl, 'https://pay.hesab.example/s/abc');
    assert.equal((await rowOf(a.body.id)).provider, 'hesabpay');
    const made = seen.find((x) => x.url === '/api/v1/payment/create-session');
    assert.equal(made.auth, 'API-KEY test-key-123');
    assert.equal(made.body.items[0].price, '1450.00');
    assert.equal(made.body.reference, a.body.id);
    assert.ok(!JSON.stringify(await rowOf(a.body.id)).includes('test-key-123'));

    const raw = Buffer.from(JSON.stringify({ event_id: 'HE-1', session_id: 'HS-1', status: 'success', amount: '1450.00', transaction_id: 'TX-9' }));
    const sig = crypto.createHmac('sha256', 'whsec-test').update(raw).digest('hex');
    const bad = await h.raw('POST', '/api/v1/payments/webhook/hesabpay', raw, { headers: { 'Content-Type': 'application/json', 'x-hesab-signature': sig.replace(/^./, sig[0] === 'a' ? 'b' : 'a') } });
    assert.equal(bad.status, 401);
    assert.equal((await rowOf(a.body.id)).status, 'pending');
    const ok = await h.raw('POST', '/api/v1/payments/webhook/hesabpay', raw, { headers: { 'Content-Type': 'application/json', 'x-hesab-signature': `sha256=${sig}` } });
    assert.equal(ok.status, 200, JSON.stringify(ok.body));
    assert.equal(ok.body.outcome, 'applied');
    assert.ok(seen.some((x) => x.url === '/api/v1/payment/status/HS-1'), 'تأییدِ دوم صدا زده شد');
    const row = await rowOf(a.body.id);
    assert.equal(row.status, 'paid');
    assert.equal(row.provider_ref, 'TX-9');
    const ev = await one('SELECT raw FROM payment_events WHERE intent_id=$1', [a.body.id]);
    assert.ok(!JSON.stringify(ev.raw).includes('whsec') && !JSON.stringify(ev.raw).includes(sig), 'رازی در raw نیست');
  } finally {
    for (const [k, v] of Object.entries(prev)) { if (v === undefined) delete process.env[k]; else process.env[k] = v; }
    process.env.PAYMENTS_PROVIDER = 'fake';
    await new Promise((r) => srv.close(r));
  }
});

test('ارائه‌دهنده جواب نداد ⇒ ۵۰۲ provider_unavailable و قصدِ failed؛ همان کلید همان failed', async () => {
  const s = await shop('خطای‌درگاه');
  const k = key();
  process.env.PAYMENTS_FAKE_FAIL = '1';
  let r;
  try { r = await createPos(s, { idempotencyKey: k, refId: 'sale-pf' }); } finally { delete process.env.PAYMENTS_FAKE_FAIL; }
  assert.equal(r.status, 502);
  assert.equal(r.body.error.code, 'provider_unavailable');
  assert.equal((await rowOf(r.body.error.intentId)).status, 'failed');
  const again = await createPos(s, { idempotencyKey: k, refId: 'sale-pf' });
  assert.equal(again.status, 200);
  assert.equal(again.body.status, 'failed');
  assert.equal((await createPos(s, { refId: 'sale-pf' })).status, 201, 'کلیدِ تازه ⇒ تلاشِ تازه');
});

test('تطبیق: قصدِ زنده/منقضی با «paid» ِ ارائه‌دهنده درست می‌شود؛ «failed» بسته می‌شود', async () => {
  const s = await shop('تطبیق');
  const a = (await createPos(s)).body;
  const b = (await createPos(s)).body;
  const c = (await createPos(s)).body;
  await query('UPDATE payment_intents SET expires_at=$2 WHERE id=$1', [c.id, Date.now() - 1000]);
  await h.get(`${URL_INT}/${c.id}`, { token: s.t });
  P.fake.settle((await rowOf(a.id)).provider_session_id, { status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'RC-1' });
  P.fake.settle((await rowOf(b.id)).provider_session_id, { status: 'failed' });
  P.fake.settle((await rowOf(c.id)).provider_session_id, { status: 'paid', amountMinor: c.amount, currency: 'AFN', providerRef: 'RC-3' });
  const out = await pay.reconcile({ sinceMs: 60 * 60 * 1000 });
  assert.ok(out.paid >= 2 && out.closed >= 1, JSON.stringify(out));
  assert.equal((await rowOf(a.id)).status, 'paid');
  assert.equal((await rowOf(b.id)).status, 'failed');
  const cr = await rowOf(c.id);
  assert.equal(cr.status, 'paid');
  assert.equal(cr.needs_review, true);
});

test('زمان‌بندِ تطبیق: «paid» ِ بی وب‌هوک با یک نوبت درست می‌شود؛ بی پیکربندی، با قفلِ نمونهٔ دیگر یا نوبتِ هم‌زمان ⇒ اجرا نمی‌شود', async () => {
  const s = await shop('زمان‌بند');
  const a = (await createPos(s)).body;
  P.fake.settle((await rowOf(a.id)).provider_session_id, { status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'SCH-1' });
  const lease = () => one('SELECT value, updated_at FROM app_config WHERE key=$1', [pay.RECONCILE_LEASE_KEY]);

  //  بی پیکربندی (حساب‌پیِ بی کلید) ⇒ هیچ پرسشی
  process.env.PAYMENTS_PROVIDER = '';
  try { assert.equal(await pay.reconcileTick({ wide: false }), null); } finally { process.env.PAYMENTS_PROVIDER = 'fake'; }
  assert.equal((await rowOf(a.id)).status, 'pending');

  //  نمونهٔ دیگری اجاره دارد ⇒ این نمونه اجرا نمی‌کند
  await query(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1, 'other-instance', $2)
     ON CONFLICT (key) DO UPDATE SET value = EXCLUDED.value, updated_at = EXCLUDED.updated_at`,
    [pay.RECONCILE_LEASE_KEY, Date.now() + 60_000]
  );
  assert.equal(await pay.reconcileTick({ wide: false }), null);
  assert.equal((await rowOf(a.id)).status, 'pending');
  assert.equal((await lease()).value, 'other-instance');

  //  اجارهٔ او گذشت (نمونه مُرد) ⇒ این نمونه می‌گیرد، تطبیق می‌کند و آزاد می‌کند
  await query('UPDATE app_config SET updated_at=$2 WHERE key=$1', [pay.RECONCILE_LEASE_KEY, Date.now() - 1]);
  const out = await pay.reconcileTick({ wide: false });
  assert.ok(out && out.paid >= 1 && out.wide === false, JSON.stringify(out));
  assert.equal((await rowOf(a.id)).status, 'paid');
  assert.equal(Number((await lease()).updated_at), 0, 'پس از نوبت اجاره آزاد است');

  //  دو نوبتِ هم‌زمان در همین نمونه ⇒ فقط یکی
  const both = await Promise.all([pay.reconcileTick({ wide: false }), pay.reconcileTick({ wide: false })]);
  assert.equal(both.filter((x) => x === null).length, 1, JSON.stringify(both));
});

test('توسعه: رویدادِ ساختگیِ امضاشده با settle (scripts/fake-pay.js) خودش تأییدِ دوم را می‌گذراند', async () => {
  const s = await shop('توسعه');
  const a = (await createPos(s)).body;
  const sessionId = P.fake.sessionOf(a.id);
  assert.equal((await rowOf(a.id)).provider_session_id, sessionId);
  const r = await hook({ eventId: `dev-${++seq}`, sessionId, status: 'paid', amountMinor: a.amount, currency: 'AFN', providerRef: 'FAKE-1', settle: true });
  assert.equal(r.body.outcome, 'applied', JSON.stringify(r.body));
  assert.equal((await h.get(`${URL_INT}/${a.id}`, { token: s.t })).body.status, 'paid');
});
