'use strict';
/**
 * CHK-10 — پیامِ خودکارِ «رسیدیم» به ایمیلِ تأییدشدهٔ «خبرم کن» ِ بیرونِ محدوده (`lib/storefront-area-email.js`،
 * پرچمِ خاموشِ `shop.storefront.area_email`، مهاجرتِ ۹۴۵).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پرچمِ خاموش ⇒ بایت‌به‌بایت همان پیشین: هیچ نامه، ردیف‌ها و فهرستِ دکان همان — حتی با ناحیهٔ پوشاننده و ایمیلِ آماده.
 *   ۲) ناحیهٔ تازه خانه را پوشاند ⇒ دقیقاً **یک** نامه برای هر درخواست، به همان ایمیلِ تأییدشده و به زبانِ ویترین؛ سپس
 *      «انجام شد» (از فهرستِ دکان بیرون)؛ تیک‌ها و ذخیره‌های دوبارهٔ ناحیه هیچ نامهٔ دومی نمی‌سازند؛ درخواستِ تازهٔ همان
 *      مشتری دوباره مسلح می‌کند. متن بی نشانی، پین، ایمیل یا نامِ ناحیه.
 *   ۳) سقف‌ها: سقفِ روزانهٔ هر دکان (`STORE_AREA_EMAIL_SHOP_PER_DAY`) و سقفِ مشترکِ هزینهٔ NTF-02 ⇒ منتظر، نه رد؛ روزِ بعد.
 *   ۴) ایمیلِ سرور پیکربندی‌نشده ⇒ هیچ نامه و هیچ نوشتن؛ پس از پیکربندی همان درخواست.
 *   ۵) ردِ سرویس ⇒ `failed`، هرگز دوباره، در فهرستِ دکان می‌ماند؛ «پس گرفتن» ⇒ هیچ نامه؛ کلیدِ کهنهٔ شماره ⇒ هیچ نامه.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailer = require('../src/lib/mailer');
const A = require('../src/lib/storefront-area-email');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;

const READY = Object.freeze({
  provider: 'smtp', host: 'smtp.test.local', port: '587', user: 'u', pass: 'p', from: 'shop@test.local', fromName: 'دکان', secure: 'starttls',
});
const keep = { send: mailer.send, current: mailer.current };
let admin = '';
let seq = 0;
let mails = [];
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
});
test.after(async () => { Object.assign(mailer, keep); await h.stop(); });
test.beforeEach(() => resetAll());

function mailWith(cfg, fail = null) {
  mailer.current = async () => cfg;
  mailer.send = async (m) => { if (fail) throw fail; mails.push(m); return { delivered: true }; };
}
const restoreMail = () => Object.assign(mailer, keep);

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const OUTSIDE = { lat: 34.62, lng: 69.31 };
const ZONE = { name: 'کارته‌سه', kind: 'polygon', polygon: [[34.50, 69.12], [34.50, 69.22], [34.56, 69.22], [34.56, 69.12]], feeMinor: 3000, minOrderMinor: 0 };
const FAR = { name: 'خیرخانه', kind: 'polygon', polygon: [[34.60, 69.28], [34.60, 69.34], [34.64, 69.34], [34.64, 69.28]], feeMinor: 6000, minOrderMinor: 0 };

async function liveStore(label, { email = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.storefront.zones', 'shop.storefront.notify_area']) await setFlag(o.shopId, k, true);
  if (email) await setFlag(o.shopId, A.FLAG_KEY, true);
  const slug = `ae-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, minOrderMinor: 0 }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  assert.equal((await zonesTo(o, [ZONE])).status, 200);
  return { ...o, slug };
}
const zonesTo = (s, zones) => h.put('/api/shop/storefront/delivery', { zones }, { token: s.token });

const tickets = new Map();
async function ticketFor(slug, phone) {
  const k = `${slug}|${phone}`;
  if (tickets.has(k)) return tickets.get(k);
  const st = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(k, c.body.ticket);
  return c.body.ticket;
}
/** «خبرم کن» ِ بیرون با بلیتِ تأییدشده (پیش از نشاندنِ ایمیلِ ساختگی: کدِ تأیید از راهِ `log` می‌آید) */
async function ask(s, phone, extra = {}) {
  const r = await h.post(`/api/store/${s.slug}/area-request`, { ...OUTSIDE, ...extra }, { headers: { 'X-Store-Ticket': await ticketFor(s.slug, phone) } });
  assert.ok([200, 201].includes(r.status), JSON.stringify(r.body));
  return r;
}
const rowsOf = (shopId) => many('SELECT * FROM storefront_area_requests WHERE shop_id = $1 ORDER BY created_at, id', [shopId]);
const mailsTo = (phone) => mails.filter((m) => m.to === mailOf(phone));
const list = (s) => h.get('/api/shop/storefront/area-requests', { token: s.token });

test('CHK-10 ⛔ پرچمِ «رسیدیم» خاموش ⇒ هیچ نامه؛ ردیف‌ها و فهرستِ دکان بایت‌به‌بایت همان — حتی با ناحیهٔ پوشاننده', async () => {
  const s = await liveStore('رسیدیمِ خاموش', { email: false });
  await ask(s, '0791300001');
  assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
  const rows0 = await rowsOf(s.shopId);
  const list0 = (await list(s)).body;
  assert.equal(list0.contacts.length, 1, 'دکان همان «تماس» ِ دستی را دارد');
  mails = [];
  mailWith(READY);
  try {
    await A.tick(now());
    await A.tick(now() + 60_000);
    assert.equal(mails.length, 0, 'بی پرچم هیچ نامه');
    assert.deepEqual(await rowsOf(s.shopId), rows0, 'هیچ ردیفی عوض نشد');
    assert.deepEqual((await list(s)).body, list0, 'فهرستِ دکان همان');
    assert.deepEqual(await A.checkShop(s.shopId), { covered: 0, sent: 0, failed: 0, waiting: 0 });
  } finally { restoreMail(); }
});

test('CHK-10: ناحیهٔ تازه ⇒ دقیقاً یک نامه برای هر درخواست به همان ایمیل و زبان؛ سپس «انجام شد»؛ تکرار بی نامهٔ دوم', async () => {
  const s = await liveStore('رسیدیم');
  await ask(s, '0791300002');
  await ask(s, '0791300003', { lang: 'ps' });
  mails = [];
  mailWith(READY);
  try {
    //  هنوز بیرون ⇒ هیچ
    await A.tick(now());
    assert.equal(mails.length, 0);
    assert.ok((await rowsOf(s.shopId)).every((r) => r.notice === ''));

    assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
    const out = await A.checkShop(s.shopId);
    assert.deepEqual(out, { covered: 2, sent: 2, failed: 0, waiting: 0 });
    const [fa] = mailsTo('0791300002');
    const [ps] = mailsTo('0791300003');
    assert.equal(mails.length, 2, 'یک نامه برای هر درخواست');
    assert.equal(fa.subject, 'ویترینِ رسیدیم');
    assert.match(fa.text, /حالا به محلِ شما هم ارسال دارد/);
    assert.match(fa.text, /تنها پیامِ درخواستِ «خبرم کن»/);
    assert.match(ps.text, /ستاسو سیمې ته هم لېږد لري/, 'زبانِ ویترینِ همان درخواست');
    for (const m of mails) {
      const all = `${m.subject}\n${m.text}\n${m.html}`;
      for (const secret of ['خیرخانه', '34.6', '69.3', '@test.local', '0791300']) assert.ok(!all.includes(secret), `در نامه: ${secret}`);
    }
    const rows = await rowsOf(s.shopId);
    assert.ok(rows.every((r) => r.notice === 'sent' && r.sent_at), 'هر دو «انجام شد»');
    const shopView = (await list(s)).body;
    assert.deepEqual([shopView.total, shopView.covered, shopView.contacts.length], [0, 0, 0], 'رفته‌ها دیگر در فهرستِ دکان نیستند');

    //  تیک‌ها و ذخیرهٔ دوبارهٔ ناحیه ⇒ هیچ نامهٔ دومی
    for (let i = 0; i < 3; i++) {
      assert.equal((await zonesTo(s, i % 2 ? [ZONE, FAR] : [FAR])).status, 200);
      await A.tick(now() + i * 60_000);
    }
    assert.equal(mailsTo('0791300002').length + mailsTo('0791300003').length, 2, 'تکرار ممنوع');

    //  درخواستِ تازهٔ همان مشتری (ناحیهٔ دور برداشته شد) ⇒ دوباره مسلح و دوباره یک نامه پس از پوشش
    assert.equal((await zonesTo(s, [ZONE])).status, 200);
    await ask(s, '0791300002');
    const again = (await rowsOf(s.shopId)).find((r) => r.phone === mailOf('0791300002'));
    assert.deepEqual([again.notice, again.sent_at], ['', null]);
    assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
    await A.tick(now());
    assert.equal(mailsTo('0791300002').length, 2);

    //  ردیفِ رفته پس از ۲ روز پاک می‌شود (کمینه‌سازیِ داده)
    await A.tick(now() + A.KEEP_SENT_MS + 60_000);
    assert.equal((await rowsOf(s.shopId)).length, 0);
  } finally { restoreMail(); }
});

test('CHK-10: سقفِ روزانهٔ هر دکان و سقفِ مشترکِ هزینهٔ NTF-02 ⇒ منتظر (نه رد)؛ روزِ بعد همان درخواست', async (t) => {
  const s = await liveStore('سقفِ رسیدیم');
  process.env[A.SHOP_PER_DAY_ENV] = '1';
  t.after(() => { delete process.env[A.SHOP_PER_DAY_ENV]; });
  assert.equal(A.shopPerDay(), 1);
  for (const p of ['0791300011', '0791300012', '0791300013']) await ask(s, p);
  assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
  mails = [];
  mailWith(READY);
  try {
    const t0 = now();
    assert.deepEqual(await A.checkShop(s.shopId, t0), { covered: 3, sent: 1, failed: 0, waiting: 2 });
    assert.deepEqual(await A.checkShop(s.shopId, t0 + 3600_000), { covered: 2, sent: 0, failed: 0, waiting: 2 }, 'همان روز ⇒ منتظر');
    const waiting = (await rowsOf(s.shopId)).filter((r) => r.notice === '');
    assert.equal(waiting.length, 2, 'نه failed، نه گرفته‌شده');

    //  روزِ بعد، سقفِ دکان ۱۰ ولی سقفِ هزینهٔ ایمیل فقط یک نامهٔ دیگر (قیمتِ ۰٫۶ دلار، سقفِ ۱ دلار)
    process.env[A.SHOP_PER_DAY_ENV] = '10';
    process.env.CUSTOMER_NOTIFY_COST_MICROS_EMAIL = '600000';
    try {
      const t1 = t0 + 25 * 3600_000;
      assert.deepEqual(await A.checkShop(s.shopId, t1), { covered: 2, sent: 1, failed: 0, waiting: 1 }, 'سقفِ هزینه');
      const sentRow = (await rowsOf(s.shopId)).filter((r) => r.notice === 'sent').sort((a, b) => Number(b.sent_at) - Number(a.sent_at))[0];
      const tried = Array.isArray(sentRow.tried) ? sentRow.tried : JSON.parse(sentRow.tried);
      assert.deepEqual(tried, [{ channel: 'email', result: 'accepted', costMicros: 600000 }]);
      //  سقفِ مشترک: همان هزینه در `customer-notify.spentToday` (اعلانِ مشتری و «خبرم کن» ِ کالا هم همین را می‌بینند)
      assert.equal(await require('../src/lib/customer-notify').spentToday(s.shopId, 'email', t1 + 1), 600000);
      assert.deepEqual(await A.checkShop(s.shopId, t1 + 1), { covered: 1, sent: 0, failed: 0, waiting: 1 });
    } finally { delete process.env.CUSTOMER_NOTIFY_COST_MICROS_EMAIL; }
    assert.equal(mails.length, 2);
    for (const bad of [0, 1001, 1.5, 'x', '', '-3']) assert.equal(A.perDayOf(bad), A.DEFAULT_SHOP_PER_DAY, String(bad));
    assert.equal(A.perDayOf('250'), 250);
  } finally { restoreMail(); }
});

test('CHK-10: ایمیلِ سرور پیکربندی‌نشده ⇒ هیچ نامه و هیچ نوشتن؛ پس از پیکربندی همان درخواست یک نامه می‌گیرد', async () => {
  const s = await liveStore('بی ایمیلِ سرور');
  await ask(s, '0791300021');
  assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
  const rows0 = await rowsOf(s.shopId);
  mails = [];
  try {
    for (const cfg of [{ provider: 'log' }, { ...READY, pass: '' }, { provider: 'api', url: '', from: '' }]) {
      mailWith(cfg);
      assert.deepEqual(await A.checkShop(s.shopId), { covered: 0, sent: 0, failed: 0, waiting: 0 });
      await A.tick(now());
    }
    assert.equal(mails.length, 0, 'هیچ تلاشی');
    assert.deepEqual(await rowsOf(s.shopId), rows0, 'هیچ ردیفی عوض نشد');
    assert.equal((await list(s)).body.contacts.length, 1, 'دکان همان راهِ دستی را دارد');
    mailWith(READY);
    assert.equal((await A.checkShop(s.shopId)).sent, 1);
    assert.equal(mailsTo('0791300021').length, 1);
  } finally { restoreMail(); }
});

test('CHK-10: ردِ سرویس ⇒ failed و هرگز دوباره (در فهرستِ دکان می‌ماند)؛ «پس گرفتن» و کلیدِ کهنهٔ شماره ⇒ هیچ نامه', async () => {
  const s = await liveStore('ردِ رسیدیم');
  await ask(s, '0791300031');
  await ask(s, '0791300032');
  const t = await ticketFor(s.slug, '0791300032');
  assert.equal((await h.del(`/api/store/${s.slug}/area-request`, { headers: { 'X-Store-Ticket': t } })).status, 200);
  //  کلیدِ کهنهٔ شماره (پیش از «فقط ایمیل») ⇒ فقط راهِ دستی
  await query(
    `INSERT INTO storefront_area_requests (id, shop_id, phone, cell_lat, cell_lng, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$6)`,
    [newId('ar'), s.shopId, '+93791300033', 34.62, 69.31, now()]
  );
  assert.equal((await zonesTo(s, [ZONE, FAR])).status, 200);
  mails = [];
  mailWith(READY, Object.assign(new Error('mailbox unavailable for c791300031@test.local'), { smtpCode: 550 }));
  try {
    assert.deepEqual(await A.checkShop(s.shopId), { covered: 1, sent: 0, failed: 1, waiting: 0 });
    const bad = (await rowsOf(s.shopId)).find((r) => r.phone === mailOf('0791300031'));
    assert.equal(bad.notice, 'failed');
    const tried = Array.isArray(bad.tried) ? bad.tried : JSON.parse(bad.tried);
    assert.deepEqual(tried, [{ channel: 'email', result: 'rejected', code: 'smtp_550' }], 'بی متنِ خطا و بی نشانی');
    mailWith(READY);
    await A.tick(now());
    await A.tick(now() + 60_000);
    //  (کارگر دکان‌های آزمون‌های پیشین را هم می‌گرداند ⇒ فقط نامه‌های همین سه مشتری)
    const mine = mails.filter((m) => ['0791300031', '0791300032', '0791300033'].some((p) => m.to === mailOf(p) || m.to.includes(p.slice(1))));
    assert.equal(mine.length, 0, 'failed دوباره گرفته نمی‌شود؛ پس‌گرفته و کلیدِ شماره هیچ');
    const view = (await list(s)).body;
    assert.deepEqual(view.contacts.map((c) => c.email || c.phone).sort(), ['+93791300033', mailOf('0791300031')].sort(), 'دکان هر دو را برای خبرِ دستی می‌بیند');
    assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_area_requests WHERE phone = $1', [mailOf('0791300032')])).n, 0);
  } finally { restoreMail(); }
});
