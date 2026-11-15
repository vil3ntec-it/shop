'use strict';
/**
 * OPS-09 — «مصرف و هزینهٔ پیامِ مشتری» در زبانهٔ «عملیات» ِ پنلِ مدیر (`GET /api/admin/system/notify-usage`،
 * `lib/notify-usage.js`).
 *
 *   ۱) فقط مدیر؛ بی توکن و با توکنِ کاربرِ دکان ۴۰۱؛ سقفِ نرخِ IP پیش از احراز ⇒ ۴۲۹.
 *   ۲) هر دکان و هر کانال: شمارِ پیامِ «فرستاده» از هر دو صندوق (اعلانِ سفارش و خبرِ «موجود شد») و هزینه با همان
 *      `notify-cost.spentOf` ِ سقفِ NTF-02 (ردیفِ بی `costMicros` ⇒ قیمتِ یک پیام)؛ پیامِ نرفته هزینه ندارد.
 *   ۳) نشانه‌ها: «ردشده با سقفِ هزینه» (`cost_cap`)، «ردشده با سقفِ مشتری» (`rate_limited`) و «نزدیکِ سقف» (۲۴ ساعتِ
 *      گذشتهٔ کانالِ پولی ≥ ۸۰٪ سقف).
 *   ۴) بازه ۱ تا ۳۱ روز (نادرست ⇒ ۷)؛ پیامِ بیرونِ بازه شمرده نمی‌شود؛ پرهزینه‌ترین دکان اول.
 *   ۵) ⛔ هیچ شماره، `phone_key`، شناسهٔ سفارش یا متنی در پاسخ نیست.
 * «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): پیامک از زنجیره رفت و هیچ کانالی قیمتِ پیش‌فرض ندارد ⇒ «کانالِ پولیِ» این آزمون همان ایمیل
 * است با قیمت و سقفِ env (همان عددهای پیامکِ پیشین تا حسابِ هزینه و «نزدیکِ سقف» همان بماند).
 */
process.env.CUSTOMER_NOTIFY_COST_MICROS_EMAIL = '446000';
process.env.CUSTOMER_NOTIFY_DAILY_CAP_MICROS_EMAIL = '2230000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const usage = require('../src/lib/notify-usage');

const DAY = 24 * 60 * 60 * 1000;
let admin = '';
let A; let B; let C;
let seq = 0;
const made = [];   //  هر شناسهٔ سفارش، صف و خبر ⇒ هیچ‌کدام در پاسخ نیست
const uid = (p) => { const id = `${p}${Date.now().toString(36)}${++seq}`.toUpperCase().slice(0, 26); made.push(id); return id; };
const get = (q = '', opts = {}) => h.get(`/api/admin/system/notify-usage${q}`, opts);
const PHONE = '+93791112233';
const PHONE_KEY = 'pk-secret-hmac-of-phone';

async function shop(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id, name: `دکانِ ${label}`, n: 0 };
}

async function order(s) {
  const id = uid('ORD');
  s.n += 1;
  await query(
    `INSERT INTO online_orders (id, shop_id, order_no, client_key, request_hash, source, fulfillment, customer_phone, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'h','storefront','pickup',$5,$6,$6)`,
    [id, s.shopId, s.n, `ck-${id}`, PHONE, now()]
  );
  return id;
}

const EVENTS = ['order_received', 'order_accepted', 'order_ready', 'order_completed', 'order_rejected'];
/** یک ردیفِ صندوقِ اعلانِ سفارش؛ هر ردیف سفارشِ خودش (یکتاییِ order_id + event) */
async function outbox(s, { status = 'sent', channel = '', tried = [], sentAt = null, at = now() }) {
  await query(
    `INSERT INTO customer_notify_outbox (id, shop_id, order_id, event, lang, phone_key, status, channel, next_attempt_at,
       tried, sent_at, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'fa',$5,$6,$7,$8,$9::jsonb,$10,$8,$8)`,
    [uid('NTF'), s.shopId, await order(s), EVENTS[seq % EVENTS.length], PHONE_KEY, status, channel, at, JSON.stringify(tried), sentAt]
  );
}

async function alert(s, { notice = 'sent', channel = '', tried = [], sentAt = null, at = now() }) {
  await query(
    `INSERT INTO storefront_alerts (id, shop_id, phone, product_id, kind, state, notice, channel, tried, sent_at, checked_at,
       created_at, updated_at)
     VALUES ($1,$2,$3,'rice','stock','fired',$4,$5,$6::jsonb,$7,$8,$8,$8)`,
    [uid('ALR'), s.shopId, PHONE, notice, channel, JSON.stringify(tried), sentAt, at]
  );
}

const ok = (channel, costMicros) => ({ channel, result: 'accepted', ...(costMicros === undefined ? {} : { costMicros }) });

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

  A = await shop('پرپیام');
  B = await shop('کم‌پیام');
  C = await shop('بی‌پیام');
  const t = now();
  //  «الف»: دو ایمیلِ پولی (یکی با هزینهٔ ثبت‌شده، یکی پیش از NTF-02 بی هزینه ⇒ قیمتِ یک پیام)، یک تلگرامِ رایگان،
  //  یک خبرِ پوش؛ یکی با سقفِ مشتری نگه داشته شد و یکی پیامکش با سقفِ هزینه رد و هیچ کانالی نماند؛ یکی هنوز در صف
  await outbox(A, { channel: 'email', tried: [{ channel: 'push', result: 'not_configured' }, ok('email', 1_338_000)], sentAt: t - 60_000 });
  await outbox(A, { channel: 'email', tried: [ok('email')], sentAt: t - 2 * 60 * 60_000 });
  await outbox(A, { channel: 'telegram', tried: [ok('telegram', 0)], sentAt: t - 3 * DAY });
  await outbox(A, { status: 'rate_limited', at: t - DAY });
  await outbox(A, { status: 'no_channel', tried: [{ channel: 'email', result: 'cost_cap' }], at: t - 2 * 60_000 });
  await outbox(A, { status: 'pending', at: t });
  await alert(A, { channel: 'push', tried: [ok('push', 0)], sentAt: t - 5 * 60_000 });
  //  «الف»، ۱۰ روز پیش: بیرون از ۷ روز، درونِ ۳۱ روز
  await outbox(A, { channel: 'email', tried: [ok('email', 446_000)], sentAt: t - 10 * DAY, at: t - 10 * DAY });
  //  «ب»: یک واتس‌اپ با هزینهٔ دلخواه؛ یک خبر با سقفِ هزینه
  await outbox(B, { channel: 'whatsapp', tried: [ok('whatsapp', 5_000)], sentAt: t - DAY / 2 });
  await alert(B, { notice: 'cost_cap', tried: [{ channel: 'push', result: 'not_configured' }], at: t - 60_000 });
  //  «ج»: فقط پیامِ نرفته ⇒ در گزارش نیست
  await outbox(C, { status: 'no_channel', tried: [{ channel: 'push', result: 'not_configured' }] });
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

test('فقط مدیر: بی توکن و کاربرِ دکان ۴۰۱؛ سقفِ نرخِ IP پیش از احراز ⇒ ۴۲۹', async () => {
  assert.equal((await get()).status, 401);
  assert.equal((await get('', { token: A.token })).status, 401);
  let last = 0;
  for (let i = 0; i < 40; i++) last = (await get()).status;
  assert.equal(last, 429, 'سطلِ IP پیش از احراز');
  resetAll();
  assert.equal((await get('', { token: admin })).status, 200);
});

test('هر دکان و هر کانال: شمار و هزینه با همان قیمتِ سقفِ NTF-02؛ نشانه‌ها و «نزدیکِ سقف»', async () => {
  const r = await get('', { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const b = r.body;
  assert.equal(b.days, 7);
  assert.deepEqual(b.shops.map((s) => s.shopId), [A.shopId, B.shopId], 'پرهزینه‌ترین اول؛ دکانِ بی پیامِ رفته نیست');
  const [a, bb] = b.shops;
  assert.equal(a.name, A.name);
  assert.equal(a.sent, 4);
  assert.equal(a.costMicros, 1_338_000 + 446_000, 'ردیفِ بی هزینه ⇒ قیمتِ یک بخش؛ تلگرام و پوش رایگان');
  assert.deepEqual(a.byChannel.email, { sent: 2, costMicros: 1_784_000, last24hMicros: 1_784_000 });
  assert.deepEqual(a.byChannel.telegram, { sent: 1, costMicros: 0, last24hMicros: 0 });
  assert.deepEqual(a.byChannel.push, { sent: 1, costMicros: 0, last24hMicros: 0 });
  assert.equal(a.costCap, 1);
  assert.equal(a.rateLimited, 1);
  assert.deepEqual(a.nearCap, ['email'], '۱٫۷۸۴ از ۲٫۲۳ دلار = ۸۰٪');
  assert.equal(bb.sent, 1);
  assert.equal(bb.costMicros, 5_000);
  assert.equal(bb.costCap, 1, 'خبرِ ردشده با سقف');
  assert.deepEqual(bb.nearCap, [], 'واتس‌اپ امروز قیمتی ندارد ⇒ هرگز «نزدیکِ سقف»');
  assert.deepEqual(b.totals, { sent: 5, costMicros: 1_789_000, costCap: 2, rateLimited: 1, shops: 2 });
  const mail = b.channels.find((c) => c.channel === 'email');
  assert.deepEqual(mail, { channel: 'email', unitMicros: 446_000, capMicros: 2_230_000 });
  assert.equal(b.channels.find((c) => c.channel === 'sms'), undefined, 'پیامک دیگر کانال نیست');
  assert.equal(b.truncated, false);
  assert.equal(b.shopsTruncated, false);
  //  ⛔ حریم
  const raw = JSON.stringify(b);
  assert.ok(made.length >= 20);
  for (const secret of [PHONE, PHONE.slice(1), PHONE_KEY, 'rice', ...made]) {
    assert.equal(raw.includes(secret), false, `پاسخ «${secret}» را ندارد`);
  }
});

test('بازه: ۳۱ روز پیامِ کهنه را هم می‌شمارد، ۱ روز فقط ۲۴ ساعت؛ مقدارِ نادرست ⇒ ۷', async () => {
  const month = (await get('?days=31', { token: admin })).body;
  assert.equal(month.days, 31);
  assert.equal(month.shops[0].byChannel.email.sent, 3);
  assert.equal(month.shops[0].byChannel.email.last24hMicros, 1_784_000, '۲۴ ساعت همان');
  const day = (await get('?days=1', { token: admin })).body;
  assert.equal(day.shops[0].sent, 3, 'تلگرامِ سه روز پیش بیرون');
  assert.equal(day.shops[0].rateLimited, 0, 'سقفِ مشتریِ ۲۴ ساعت و یک دقیقه پیش بیرون');
  for (const bad of ['0', '32', '7.5', 'x', '-1']) {
    const r = await get(`?days=${bad}`, { token: admin });
    assert.equal(r.status, 200);
    assert.equal(r.body.days, 7, bad);
  }
  assert.equal(usage.daysOf(undefined), 7);
});

test('هزینهٔ تازه با قیمتِ همین env؛ هیچ دکانی ⇒ فهرستِ خالی', async () => {
  const at = now();
  const r = await usage.report({ days: 7, at, env: { CUSTOMER_NOTIFY_COST_MICROS_EMAIL: '100' } });
  assert.equal(r.shops[0].shopId, A.shopId);
  assert.equal(r.shops[0].byChannel.email.costMicros, 1_338_000 + 100, 'ردیفِ بی هزینه با قیمتِ امروز؛ ثبت‌شده همان');
  const empty = await usage.report({ days: 1, at: at - 30 * DAY });
  assert.deepEqual(empty.shops, []);
  assert.deepEqual(empty.totals, { sent: 0, costMicros: 0, costCap: 0, rateLimited: 0, shops: 0 });
});

test('OPS-09: پیامکِ کدِ ورود و ویترین در همین گزارش — جمع و به تفکیکِ کار، با هزینهٔ همان دفتر و بی شماره', async () => {
  const sms = require('../src/lib/sms-usage');
  await sms.setUnitCost(250, 'test');
  const at = now();
  await sms.record({ purpose: 'login', via: 'sms', ok: true, at: at - 1000 });
  await sms.record({ purpose: 'login', via: 'sms', ok: true, at: at - 900 });
  await sms.record({ purpose: 'login', via: 'sms', ok: false, at: at - 800 });
  await sms.record({ shopId: A.shopId, purpose: 'store_phone', via: 'sms', ok: true, parts: 2, at: at - 700 });
  await sms.record({ purpose: 'register', via: 'log', ok: true, at: at - 600 });
  await sms.record({ purpose: 'login', via: 'sms', ok: true, at: at - 40 * DAY });   //  بیرونِ بازه

  const r = await get('?days=7', { token: admin });
  assert.equal(r.status, 200);
  const c = r.body.codes;
  assert.ok(c, '⛔ هزینهٔ پیامکِ کدها در همان گزارش');
  assert.deepEqual([c.currency, c.unitCostMinor], [sms.CURRENCY, 250]);
  assert.deepEqual([c.total.sent, c.total.failed, c.total.mock, c.total.costMinor], [3, 1, 1, 250 * 4]);
  const kind = (p, shop) => c.byPurpose.find((x) => x.purpose === p && x.shop === shop);
  assert.deepEqual([kind('login', false).sent, kind('login', false).failed, kind('login', false).costMinor], [2, 1, 500]);
  assert.deepEqual([kind('store_phone', true).sent, kind('store_phone', true).costMinor], [1, 500]);
  assert.equal(kind('register', false).mock, 1, 'آزمایشی شمرده می‌شود ولی هزینه ندارد');
  assert.equal(kind('register', false).costMinor, 0);
  const raw = JSON.stringify(r.body);
  for (const secret of [PHONE, PHONE_KEY]) assert.ok(!raw.includes(secret));
  assert.ok(!/\+93\d{6,}/.test(raw), 'هیچ شماره‌ای');
  assert.equal(c.byPurpose.some((x) => 'shopId' in x), false, 'بی شناسهٔ دکان در تفکیکِ کدها');
});
