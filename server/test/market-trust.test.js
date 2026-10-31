'use strict';
/**
 * اعتماد، شکایت و تخلفِ بازارچه — هستهٔ سرور (v3 §۳۸.۶؛ سناریوی ABUSE-01).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۰) ⛔ پیش‌فرض خاموش: بی `market.trust` درهای مشتری و دکان ۴۰۴ و هیچ ردیفی ساخته نمی‌شود.
 *   ۱) مشتری: شکایت از سفارشِ خودش (خریدِ واقعی) یا دکان، گزارشِ کالای ویترین، دسته،
 *      مدرکِ عکس (نوع از بایت، EXIF ِ مکان پاک)، پیگیری با توکنِ حدس‌نزدنی، تکرار ⇒ همان پرونده.
 *   ۲) دکان: فهرست و پرونده بی هیچ نشانی از هویتِ شاکی؛ پاسخ (تکرار بی اثر)؛ اعتراض.
 *   ۳) مدیرِ ارشد: صف، مسئول، تصمیم فقط با دلیلِ نوشته و در سابقه؛ جریمه فقط سه نوع.
 *   ۴) اعتراضِ پذیرفته جریمه را برمی‌گرداند (کالا دوباره در ویترین).
 *   ۵) تعلیقِ ویترین ⇒ ۴۰۴؛ برداشتن با دلیل ⇒ ویترین برمی‌گردد؛ پیگیریِ شکایت در تعلیق ممکن.
 *   ۶) جداسازیِ tenant: دکانِ دیگر با شناسه/توکن به پرونده نمی‌رسد.
 *   ۷) سوءاستفاده: سقفِ روزانهٔ ماندگار به‌ازای شماره و IP، سقفِ حافظه‌ای، برچسبِ متنِ یکسان
 *      فقط برای مدیر، درخواستِ هم‌زمان یک پرونده.
 */
process.env.RATE_TRUST_FILE_MAX = '6';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const config = require('../src/config');

let admin = '';
let plainAdmin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword('Admin!12345');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3), ($4,'helper','پشتیبان',$2,'admin','active',$3)`,
    [newId('adm'), hash, now(), newId('adm')]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  plainAdmin = (await h.post('/api/admin/login', { username: 'helper', password: 'Admin!12345' })).body.token;
  assert.ok(admin && plainAdmin);
  //  همهٔ درخواست‌های آزمون از یک IP می‌آیند؛ سقفِ IP در آزمونِ خودش پایین می‌آید
  config.trust.dailyPerIp = 10_000;
});
test.after(() => h.stop());
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id, userId: u.user.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken, userId: u.user.id };
}

const push = (token, ops, device = 'dev-trust') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

/** دکانِ روشن با ویترین، دو کالای منتشرشده (rice، oil) و یکی منتشرنشده (soap). */
async function liveStore(label, { trust = true } = {}) {
  const o = await shopOwner(label);
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  if (trust) await setFlag(o.shopId, 'market.trust', true);
  const slug = `t-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await push(o.token, [
    op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' }),
    op('oil', { name: 'روغن', unit: 'بوتل', salePrice: 300, category: 'خوراکی' }),
    op('soap', { name: 'صابون', unit: 'دانه', salePrice: 40, category: 'بهداشتی' }),
  ]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ['rice', 'oil']) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: o.token });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { ...o, slug };
}

const tickets = new Map();
async function ticketFor(slug, phone) {
  if (tickets.has(phone)) return tickets.get(phone);
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  tickets.set(phone, c.body.ticket);
  return c.body.ticket;
}

async function placeOrder(store, phone) {
  const ticket = await ticketFor(store.slug, phone);
  const r = await h.post(`/api/store/${store.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order;
}

const pub = (slug, method, path, body = null, ticket = '') => h.api(method, `/api/store/${slug}/trust${path}`, {
  body, headers: ticket ? { 'X-Store-Ticket': ticket } : {},
});
const shopApi = (token, method, path, body = null) => h.api(method, `/api/shop/trust${path}`, { token, body });
const adm = (method, path, body = null, token = admin) => h.api(method, `/api/admin/trust${path}`, { token, body });
const rows = async (table, shopId) => Number((await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n);
const auditRow = (action, targetId) => one(
  'SELECT * FROM audit_logs WHERE action=$1 AND target_id=$2 ORDER BY created_at DESC LIMIT 1', [action, targetId]
);

// ── بایت‌های آزمایشی ────────────────────────────────────────────────

function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}

/** JPEG با Exif ِ GPS و توضیح — ساختار درست، تصویرِ ساختگی. */
function jpegWithGps(tag = 'x') {
  const exif = Buffer.concat([Buffer.from('Exif\0\0MM\0*\0\0\0\x08', 'latin1'), Buffer.from('LATLONG-SECRET-1', 'latin1')]);
  return Buffer.concat([
    Buffer.from([0xff, 0xd8]),
    seg(0xe0, Buffer.from([0x4a, 0x46, 0x49, 0x46, 0, 1, 1, 0, 0, 1, 0, 1, 0, 0])),
    seg(0xe1, exif),
    seg(0xfe, Buffer.from(`comment-secret-${tag}`, 'latin1')),
    seg(0xdb, Buffer.alloc(65, 1)),
    seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])),
    Buffer.alloc(200, tag.charCodeAt(0)),
    Buffer.from([0xff, 0xd9]),
  ]);
}

/* ══════════════════════════════════════════════════════════════════
   ۰) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('پرچمِ خاموش ⇒ همهٔ درهای مشتری و دکان ۴۰۴ و هیچ ردیفی', async () => {
  const s = await liveStore('خاموش', { trust: false });
  const t = await ticketFor(s.slug, '0790000101');
  const order = await placeOrder(s, '0790000101');
  const calls = [
    await pub(s.slug, 'GET', '/config'),
    await pub(s.slug, 'POST', '/complaints', { subject: 'order', orderNo: order.orderNo, category: 'damaged', body: 'خراب بود' }, t),
    await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'rude' }, t),
    await pub(s.slug, 'POST', '/reports', { productId: 'rice', category: 'counterfeit' }, t),
    await pub(s.slug, 'GET', '/cases', null, t),
    await pub(s.slug, 'GET', `/cases/tc_${'0'.repeat(24)}.${'A'.repeat(32)}`),
    await h.raw('POST', `/api/store/${s.slug}/trust/cases/x/evidence`, jpegWithGps(), {}),
    await shopApi(s.token, 'GET', '/cases'),
    await shopApi(s.token, 'GET', `/cases/tc_${'0'.repeat(24)}`),
    await shopApi(s.token, 'POST', `/cases/tc_${'0'.repeat(24)}/responses`, { body: 'پاسخ' }),
    await shopApi(s.token, 'POST', `/cases/tc_${'0'.repeat(24)}/appeal`, { body: 'اعتراضِ آزمایشی دکان' }),
    await shopApi(s.token, 'GET', '/sanctions'),
  ];
  for (const r of calls) assert.equal(r.status, 404, JSON.stringify(r.body));
  //  پرچمِ خاموش و «ویترین نیست» یک جواب‌اند
  assert.equal(calls[0].body.error.code, 'store_not_found');
  const cfg = await shopApi(s.token, 'GET', '/config');
  assert.equal(cfg.status, 200);
  assert.equal(cfg.body.enabled, false);
  for (const tb of ['trust_cases', 'trust_case_events', 'trust_evidence', 'trust_sanctions']) {
    assert.equal(await rows(tb, s.shopId), 0, tb);
  }
  //  ویترین و ثبتِ سفارش همان رفتارِ قبلی را دارند
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 200);
  assert.equal((await h.get(`/api/store/${s.slug}/products`)).body.items.length, 2);
});

/* ══════════════════════════════════════════════════════════════════
   ۱–۴) جریانِ کامل: شکایت از سفارش، مدرک، پاسخِ دکان، تصمیم، اعتراض
   ══════════════════════════════════════════════════════════════════ */

test('ABUSE-01: شکایت از سفارش ⇒ پرونده، مدرک، پاسخ، مسئول، تصمیم با دلیل، اعتراض', async () => {
  const s = await liveStore('سفارش');
  const PHONE = '0790000201';
  const order = await placeOrder(s, PHONE);
  const t = await ticketFor(s.slug, PHONE);

  //  بی بلیت ⇒ ۴۰۱؛ دستهٔ نادرست ⇒ ۴۰۰؛ سفارشِ شمارهٔ دیگر ⇒ ۴۰۴
  assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'order', orderNo: order.orderNo, category: 'damaged' })).status, 401);
  assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'order', orderNo: order.orderNo, category: 'bogus' }, t)).body.error.code, 'bad_category');
  const other = await ticketFor(s.slug, '0790000202');
  const foreign = await pub(s.slug, 'POST', '/complaints', { subject: 'order', orderNo: order.orderNo, category: 'damaged' }, other);
  assert.equal(foreign.status, 404);
  assert.equal(foreign.body.error.code, 'order_not_found');

  const body = { subject: 'order', orderNo: order.orderNo, category: 'damaged', body: 'برنج خیس و خراب رسید' };
  const made = await pub(s.slug, 'POST', '/complaints', body, t);
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const c0 = made.body.case;
  assert.equal(made.body.duplicate, false);
  assert.match(c0.token, /^tc_[0-9a-f]{24}\.[A-Za-z0-9_-]{32}$/);
  assert.equal(c0.status, 'open');
  assert.equal(c0.orderNo, order.orderNo);
  assert.equal(c0.category, 'damaged');

  //  تکرار ⇒ همان پرونده، بی ردیفِ دوم
  const again = await pub(s.slug, 'POST', '/complaints', body, t);
  assert.equal(again.status, 200);
  assert.equal(again.body.duplicate, true);
  assert.equal(again.body.case.token, c0.token);
  assert.equal(await rows('trust_cases', s.shopId), 1);

  //  پیگیری با توکن، بی بلیت؛ توکنِ دست‌خورده ⇒ ۴۰۴
  const tr = await pub(s.slug, 'GET', `/cases/${c0.token}`);
  assert.equal(tr.status, 200);
  assert.equal(tr.body.case.status, 'open');
  assert.equal((await pub(s.slug, 'GET', `/cases/${c0.token.slice(0, -2)}xx`)).status, 404);
  const mine = await pub(s.slug, 'GET', '/cases', null, t);
  assert.deepEqual(mine.body.cases.map((x) => x.token), [c0.token]);

  //  مدرک: نوع از بایت، EXIF پاک، تکرارِ همان بایت ⇒ همان ردیف، غیرِ عکس ⇒ ۴۱۵
  const up = await h.raw('POST', `/api/store/${s.slug}/trust/cases/${c0.token}/evidence`, jpegWithGps('a'), { headers: { 'Content-Type': 'image/png' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.mime, 'image/jpeg');
  const re = await h.raw('POST', `/api/store/${s.slug}/trust/cases/${c0.token}/evidence`, jpegWithGps('a'), {});
  assert.equal(re.status, 200);
  assert.equal(re.body.evidenceId, up.body.evidenceId);
  for (const bad of [Buffer.from('MZ\x90\0'.padEnd(64, '\0'), 'latin1'), Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>x</script></svg>'),
    Buffer.concat([Buffer.from('%PDF-1.4\n'), Buffer.alloc(40, 32)])]) {
    const r = await h.raw('POST', `/api/store/${s.slug}/trust/cases/${c0.token}/evidence`, bad, { headers: { 'Content-Type': 'image/jpeg' } });
    assert.equal(r.status, 415, JSON.stringify(r.body));
  }
  const stored = await one('SELECT data FROM trust_evidence WHERE id=$1', [up.body.evidenceId]);
  assert.ok(!Buffer.from(stored.data).includes(Buffer.from('LATLONG-SECRET')), 'EXIF ِ مکان باید پاک شده باشد');
  assert.ok(!Buffer.from(stored.data).includes(Buffer.from('comment-secret')));
  const dl = await h.download(`/api/store/${s.slug}/trust/cases/${c0.token}/evidence/${up.body.evidenceId}`);
  assert.equal(dl.status, 200);
  const savedMax = config.trust.evidenceMax;
  config.trust.evidenceMax = 2;
  assert.equal((await h.raw('POST', `/api/store/${s.slug}/trust/cases/${c0.token}/evidence`, jpegWithGps('b'), {})).status, 201);
  const over = await h.raw('POST', `/api/store/${s.slug}/trust/cases/${c0.token}/evidence`, jpegWithGps('c'), {});
  config.trust.evidenceMax = savedMax;
  assert.equal(over.status, 409);
  assert.equal(over.body.error.code, 'evidence_limit');

  //  دکان: فهرست و پرونده — هیچ نشانی از هویتِ شاکی
  const manager = await member(s.shopId, 'manager', 'مدیرِ دکان');
  const staff = await member(s.shopId, 'staff', 'شاگرد');
  assert.equal((await shopApi(staff.token, 'GET', '/cases')).status, 403);
  const list = await shopApi(manager.token, 'GET', '/cases');
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.cases.length, 1);
  const caseId = list.body.cases[0].id;
  const view = await shopApi(s.token, 'GET', `/cases/${caseId}`);
  assert.equal(view.status, 200);
  assert.equal(view.body.case.body, body.body);
  assert.equal(view.body.case.evidenceCount, 2);
  const json = JSON.stringify(view.body) + JSON.stringify(list.body);
  for (const leak of ['790000201', '+93790', c0.token.split('.')[1], 'reporter', 'ip_key', 'ipKey', 'flags', 'LATLONG']) {
    assert.ok(!json.includes(leak), `نمای دکان نباید «${leak}» داشته باشد`);
  }
  //  دکان مدرک را نمی‌گیرد (فقط شمارش)
  assert.equal(view.body.case.evidence, undefined);
  //  ⛔ بازبینیِ حریم: شمارهٔ سفارش شاکی را به دکان می‌شناساند (دکان سفارش‌هایش را با
  //  نام و شماره دارد) ⇒ نه در پرونده، نه در فهرست؛ فقط «دربارهٔ یک سفارش»
  assert.equal(view.body.case.subject, 'order');
  assert.ok(!('orderNo' in view.body.case), JSON.stringify(view.body.case));
  assert.ok(list.body.cases.every((c) => !('orderNo' in c)), JSON.stringify(list.body.cases));
  assert.ok(!json.includes('"orderNo"') && !json.includes('order_no') && !json.includes('orderId'), 'هیچ شمارهٔ سفارشی در نمای دکان');

  //  پاسخِ دکان؛ تکرار با همان clientId ⇒ پاسخِ دوم نمی‌سازد؛ شاکی پاسخ را می‌بیند
  const reply = { body: 'عذرخواهی؛ جایگزین آماده است', clientId: `cl-${uniq()}-000` };
  const r1 = await shopApi(s.token, 'POST', `/cases/${caseId}/responses`, reply);
  const r2 = await shopApi(s.token, 'POST', `/cases/${caseId}/responses`, reply);
  assert.equal(r1.status, 201, JSON.stringify(r1.body));
  assert.equal(r2.status, 200);
  assert.equal(r2.body.replayed, true);
  assert.equal(r1.body.case.history.filter((e) => e.type === 'shop_response').length, 1);
  assert.ok(await auditRow('trust.case_responded', caseId));
  const seen = (await pub(s.slug, 'GET', `/cases/${c0.token}`)).body.case;
  assert.deepEqual(seen.history.filter((e) => e.type === 'shop_response').map((e) => e.body), [reply.body]);

  //  پیش از تصمیم اعتراض نمی‌شود
  assert.equal((await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'هنوز تصمیمی نیست ولی اعتراض' })).body.error.code, 'not_appealable');

  //  مدیر: صف (مدیرِ عادی ۴۰۳)، پرونده با شمارهٔ پوشیده و سابقهٔ دیدن
  assert.equal((await adm('GET', '/cases', null, plainAdmin)).status, 403);
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'آزمون' }, plainAdmin)).status, 403);
  const q = await adm('GET', '/cases');
  assert.equal(q.status, 200);
  assert.ok(q.body.cases.some((x) => x.id === caseId));
  const full = await adm('GET', `/cases/${caseId}`);
  assert.equal(full.status, 200);
  assert.match(full.body.case.reporterHint, /^\+937•+201$/);
  assert.equal(full.body.case.evidence.length, 2);
  assert.ok(await auditRow('admin.trust_case_viewed', caseId));
  const ev = await h.download(`/api/admin/trust/cases/${caseId}/evidence/${up.body.evidenceId}`, { token: admin });
  assert.equal(ev.status, 200);
  assert.ok(await auditRow('admin.trust_evidence_viewed', up.body.evidenceId));

  //  مسئول ⇒ «در بررسی»
  const as = await adm('POST', `/cases/${caseId}/assign`);
  assert.equal(as.status, 200);
  assert.equal(as.body.status, 'in_review');

  //  تصمیم بی دلیل ⇒ ۴۰۰؛ جریمهٔ نامعتبر ⇒ ۴۰۰؛ جریمه با «رد» ⇒ ۴۰۰
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld' })).body.error.code, 'reason_required');
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'بررسی شد', sanction: { kind: 'ban_forever' } })).body.error.code, 'bad_sanction');
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'dismissed', reason: 'بررسی شد', sanction: { kind: 'warning' } })).body.error.code, 'bad_sanction');

  const dec = await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'عکس‌ها خرابی را نشان می‌دهند', sanction: { kind: 'warning' } });
  assert.equal(dec.status, 200, JSON.stringify(dec.body));
  assert.equal(dec.body.sanction.kind, 'warning');
  const a = await auditRow('admin.trust_case_decided', caseId);
  assert.ok(a);
  assert.equal(a.actor_type, 'admin');
  const detail = typeof a.detail === 'string' ? JSON.parse(a.detail) : a.detail;
  assert.equal(detail.reason, 'عکس‌ها خرابی را نشان می‌دهند');
  assert.equal(detail.outcome, 'upheld');
  //  دوباره تصمیم ⇒ ۴۰۹
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'dismissed', reason: 'دوباره' })).status, 409);

  //  شاکی نتیجه را می‌بیند، دلیلِ داخلی را نه؛ دکان دلیل و جریمه را می‌بیند
  const after = (await pub(s.slug, 'GET', `/cases/${c0.token}`)).body.case;
  assert.equal(after.outcome, 'upheld');
  assert.ok(!JSON.stringify(after).includes('عکس‌ها خرابی'));
  const sv = (await shopApi(s.token, 'GET', `/cases/${caseId}`)).body.case;
  assert.equal(sv.decisionReason, 'عکس‌ها خرابی را نشان می‌دهند');
  assert.equal(sv.canAppeal, true);
  assert.deepEqual(sv.sanctions.map((x) => [x.kind, x.active]), [['warning', true]]);
  const adminId = (await adm('GET', `/cases/${caseId}`)).body.case.decision.by;
  assert.match(adminId, /^adm_/);
  assert.ok(!JSON.stringify(sv).includes(adminId), 'شناسهٔ مدیر به دکان نمی‌رسد');

  //  اعتراض: کوتاه ⇒ ۴۰۰؛ درست ⇒ appealed؛ متنِ دیگر ⇒ ۴۰۹؛ همان متن ⇒ تکرارِ بی‌اثر
  assert.equal((await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'نه' })).body.error.code, 'appeal_reason_required');
  assert.equal((await shopApi(staff.token, 'POST', `/cases/${caseId}/appeal`, { body: 'اعتراضِ شاگرد پذیرفته نیست' })).status, 403);
  const ap = await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'برنج هنگامِ تحویل سالم بود؛ رسید داریم' });
  assert.equal(ap.status, 201, JSON.stringify(ap.body));
  assert.equal(ap.body.case.status, 'appealed');
  assert.equal((await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'برنج هنگامِ تحویل سالم بود؛ رسید داریم' })).status, 200);
  assert.equal((await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'یک اعتراضِ دیگر با متنِ دیگر' })).body.error.code, 'already_appealed');
  assert.ok(await auditRow('trust.case_appealed', caseId));
  assert.equal((await adm('GET', '/cases')).body.cases[0].id, caseId, 'اعتراضِ در انتظار سرِ صف است');

  //  رأیِ اعتراض بی دلیل ⇒ ۴۰۰؛ «تأیید» ⇒ بسته و جریمه می‌ماند
  assert.equal((await adm('POST', `/cases/${caseId}/appeal-decision`, { outcome: 'upheld' })).body.error.code, 'reason_required');
  const keep = await adm('POST', `/cases/${caseId}/appeal-decision`, { outcome: 'upheld', reason: 'رسید تاریخِ دیگر دارد' });
  assert.equal(keep.status, 200, JSON.stringify(keep.body));
  assert.deepEqual(keep.body.lifted, []);
  const closed = (await shopApi(s.token, 'GET', `/cases/${caseId}`)).body.case;
  assert.equal(closed.status, 'closed');
  assert.equal(closed.sanctions[0].active, true);
  assert.equal((await shopApi(s.token, 'POST', `/cases/${caseId}/responses`, { body: 'باز هم' })).body.error.code, 'case_closed');
  assert.equal((await adm('POST', `/cases/${caseId}/appeal-decision`, { outcome: 'reversed', reason: 'دوباره' })).status, 409);
  const hist = (await adm('GET', `/cases/${caseId}`)).body.case.history.map((e) => e.type);
  assert.deepEqual(hist.filter((x) => x !== 'evidence_added'),
    ['filed', 'shop_response', 'assigned', 'decided', 'sanction_applied', 'appealed', 'appeal_decided']);
});

test('اعتراضِ پذیرفته جریمهٔ «پنهان کردنِ کالا» را برمی‌گرداند؛ گزارشِ کالا', async () => {
  const s = await liveStore('گزارشِ کالا');
  const t = await ticketFor(s.slug, '0790000301');
  //  فقط کالای منتشرشده گزارش می‌شود
  assert.equal((await pub(s.slug, 'POST', '/reports', { productId: 'soap', category: 'counterfeit' }, t)).status, 404);
  assert.equal((await pub(s.slug, 'POST', '/reports', { productId: 'nope', category: 'counterfeit' }, t)).status, 404);
  assert.equal((await pub(s.slug, 'POST', '/reports', { productId: 'oil', category: 'rude' }, t)).body.error.code, 'bad_category');
  const rep = await pub(s.slug, 'POST', '/reports', { productId: 'oil', category: 'counterfeit', body: 'برچسبِ تقلبی' }, t);
  assert.equal(rep.status, 201, JSON.stringify(rep.body));
  assert.equal(rep.body.case.subject, 'product');
  assert.equal(rep.body.case.productId, 'oil');
  const caseId = (await shopApi(s.token, 'GET', '/cases')).body.cases[0].id;
  assert.equal((await adm('GET', `/cases/${caseId}`)).body.case.severity, 'high');

  const dec = await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'برچسب با اصل نمی‌خواند', sanction: { kind: 'hide_listing' } });
  assert.equal(dec.status, 200, JSON.stringify(dec.body));
  assert.equal(dec.body.sanction.productId, 'oil');
  //  کالا از کاتالوگ، عکس و ثبتِ سفارش کنار رفت؛ نشانِ «منتشرشده» ِ دکان دست نخورد
  const cat = await h.get(`/api/store/${s.slug}/products`);
  assert.deepEqual(cat.body.items.map((p) => p.id), ['rice']);
  const ck = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'م', phone: '0790000301' }, lines: [{ productId: 'oil', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': t } });
  assert.equal(ck.status, 409);
  assert.equal(ck.body.error.code, 'product_unavailable');
  assert.equal((await one('SELECT published FROM storefront_products WHERE shop_id=$1 AND product_id=$2', [s.shopId, 'oil'])).published, true);
  const sanc = await shopApi(s.token, 'GET', '/sanctions');
  assert.deepEqual(sanc.body.sanctions.map((x) => [x.kind, x.productId, x.active]), [['hide_listing', 'oil', true]]);

  const ap = await shopApi(s.token, 'POST', `/cases/${caseId}/appeal`, { body: 'فاکتورِ واردکننده را داریم و اصل است' });
  assert.equal(ap.status, 201, JSON.stringify(ap.body));
  const rev = await adm('POST', `/cases/${caseId}/appeal-decision`, { outcome: 'reversed', reason: 'فاکتورِ اصل ارائه شد' });
  assert.equal(rev.status, 200, JSON.stringify(rev.body));
  assert.equal(rev.body.lifted.length, 1);
  const audited = await auditRow('admin.trust_appeal_decided', caseId);
  assert.equal((typeof audited.detail === 'string' ? JSON.parse(audited.detail) : audited.detail).reason, 'فاکتورِ اصل ارائه شد');
  //  کالا برگشت؛ جریمه پاک نشد، «برداشته‌شده» با دلیل
  assert.deepEqual((await h.get(`/api/store/${s.slug}/products`)).body.items.map((p) => p.id).sort(), ['oil', 'rice']);
  const sanc2 = (await shopApi(s.token, 'GET', '/sanctions')).body.sanctions[0];
  assert.equal(sanc2.active, false);
  assert.equal(sanc2.liftReason, 'فاکتورِ اصل ارائه شد');
  const seen = (await pub(s.slug, 'GET', `/cases/${rep.body.case.token}`)).body.case;
  assert.equal(seen.outcome, 'reversed');
  assert.equal(seen.status, 'closed');
});

test('تعلیقِ ویترین ⇒ ۴۰۴؛ پیگیری در تعلیق ممکن؛ برداشتن با دلیل ⇒ برمی‌گردد', async () => {
  const s = await liveStore('تعلیق');
  const t = await ticketFor(s.slug, '0790000401');
  const made = await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'fraud', body: 'پول گرفت و جنس نداد' }, t);
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const caseId = (await shopApi(s.token, 'GET', '/cases')).body.cases[0].id;
  //  پنهان کردنِ کالایی که مالِ این دکان نیست ⇒ ۴۰۴
  assert.equal((await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'آزمون', sanction: { kind: 'hide_listing', productId: 'nope' } })).status, 404);
  const dec = await adm('POST', `/cases/${caseId}/decide`, { outcome: 'upheld', reason: 'سه شاکیِ هم‌زمان با رسید', sanction: { kind: 'suspend_storefront' } });
  assert.equal(dec.status, 200, JSON.stringify(dec.body));
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 404);
  assert.equal((await h.get(`/api/store/${s.slug}/products`)).status, 404);
  //  شکایت و پیگیری از ویترینِ معلق هم ممکن است
  assert.equal((await pub(s.slug, 'GET', `/cases/${made.body.case.token}`)).status, 200);
  //  تنظیماتِ ویترین دست نخورده
  assert.equal((await one('SELECT enabled FROM storefronts WHERE shop_id=$1', [s.shopId])).enabled, true);

  const sid = dec.body.sanction.id;
  assert.equal((await adm('GET', '/sanctions?active=1')).body.sanctions.filter((x) => x.id === sid).length, 1);
  assert.equal((await adm('POST', `/sanctions/${sid}/lift`, {})).body.error.code, 'reason_required');
  assert.equal((await adm('POST', `/sanctions/${sid}/lift`, { reason: 'آزمون' }, plainAdmin)).status, 403);
  const lift = await adm('POST', `/sanctions/${sid}/lift`, { reason: 'دکان پول را برگرداند' });
  assert.equal(lift.status, 200, JSON.stringify(lift.body));
  assert.ok(await auditRow('admin.trust_sanction_lifted', sid));
  assert.equal((await h.get(`/api/store/${s.slug}`)).status, 200);
  assert.equal((await adm('POST', `/sanctions/${sid}/lift`, { reason: 'دوباره' })).body.error.code, 'already_lifted');
  assert.equal((await adm('POST', '/sanctions/tsn_nope/lift', { reason: 'نیست' })).status, 404);
});

/* ══════════════════════════════════════════════════════════════════
   ۶) جداسازیِ tenant
   ══════════════════════════════════════════════════════════════════ */

test('دکانِ دیگر با شناسه یا توکن به پرونده نمی‌رسد', async () => {
  const a = await liveStore('الف');
  const b = await liveStore('ب');
  const t = await ticketFor(a.slug, '0790000501');
  const made = await pub(a.slug, 'POST', '/complaints', { subject: 'shop', category: 'rude', body: 'بدرفتاری' }, t);
  assert.equal(made.status, 201);
  const caseId = (await shopApi(a.token, 'GET', '/cases')).body.cases[0].id;
  assert.equal((await shopApi(b.token, 'GET', '/cases')).body.cases.length, 0);
  assert.equal((await shopApi(b.token, 'GET', `/cases/${caseId}`)).status, 404);
  assert.equal((await shopApi(b.token, 'POST', `/cases/${caseId}/responses`, { body: 'دخالت' })).status, 404);
  assert.equal((await shopApi(b.token, 'POST', `/cases/${caseId}/appeal`, { body: 'اعتراض از دکانِ دیگر' })).status, 404);
  //  توکنِ پروندهٔ «الف» زیرِ نشانیِ «ب» ⇒ ۴۰۴ (توکن به دکان بسته است)
  assert.equal((await pub(b.slug, 'GET', `/cases/${made.body.case.token}`)).status, 404);
  assert.equal((await h.raw('POST', `/api/store/${b.slug}/trust/cases/${made.body.case.token}/evidence`, jpegWithGps(), {})).status, 404);
  //  پروندهٔ «الف» در فهرستِ شمارهٔ خودش زیرِ «ب» نیست
  assert.equal((await pub(b.slug, 'GET', '/cases', null, t)).body.cases.length, 0);
  //  پرچمِ «ب» خاموش ⇒ «ب» ۴۰۴ و «الف» سالم
  await setFlag(b.shopId, 'market.trust', false);
  assert.equal((await shopApi(b.token, 'GET', '/cases')).status, 404);
  assert.equal((await shopApi(a.token, 'GET', '/cases')).status, 200);
});

/* ══════════════════════════════════════════════════════════════════
   ۷) سوءاستفاده
   ══════════════════════════════════════════════════════════════════ */

test('سقفِ روزانهٔ ماندگار به‌ازای شماره و IP', async () => {
  const s = await liveStore('سقف');
  const t = await ticketFor(s.slug, '0790000601');
  const saved = config.trust.dailyPerPhone;
  config.trust.dailyPerPhone = 2;
  try {
    assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'rude' }, t)).status, 201);
    assert.equal((await pub(s.slug, 'POST', '/reports', { productId: 'rice', category: 'misleading' }, t)).status, 201);
    const third = await pub(s.slug, 'POST', '/reports', { productId: 'oil', category: 'misleading' }, t);
    assert.equal(third.status, 429);
    assert.equal(third.body.error.code, 'too_many_reports');
    //  تکرارِ پروندهٔ باز سقف نمی‌خورد (همان پرونده برمی‌گردد)
    assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'rude' }, t)).status, 200);
  } finally { config.trust.dailyPerPhone = saved; }

  config.trust.dailyPerIp = Number((await one('SELECT COUNT(*)::int AS n FROM trust_cases WHERE created_at > $1', [now() - 86400000])).n) + 1;
  try {
    const t2 = await ticketFor(s.slug, '0790000602');
    const t3 = await ticketFor(s.slug, '0790000603');
    assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'other' }, t2)).status, 201);
    const blocked = await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'other' }, t3);
    assert.equal(blocked.status, 429);
    assert.equal(blocked.body.error.code, 'too_many_reports');
  } finally { config.trust.dailyPerIp = 10_000; }
});

test('سقفِ حافظه‌ای به‌ازای شماره', async () => {
  const s = await liveStore('سقفِ نرخ');
  const t = await ticketFor(s.slug, '0790000701');
  const body = { subject: 'shop', category: 'rude' };
  for (let i = 0; i < Number(process.env.RATE_TRUST_FILE_MAX); i++) {
    assert.ok([200, 201].includes((await pub(s.slug, 'POST', '/complaints', body, t)).status));
  }
  const r = await pub(s.slug, 'POST', '/complaints', body, t);
  assert.equal(r.status, 429);
  assert.equal(r.body.error.code, 'rate_limited');
  assert.equal(await rows('trust_cases', s.shopId), 1);
});

test('متنِ یکسان از شماره‌های گوناگون فقط برچسب برای مدیر است، نه حکم؛ درخواستِ هم‌زمان یک پرونده', async () => {
  const s = await liveStore('تکرار');
  const text = 'این فروشگاه جنسِ تقلبی می‌فروشد، مراقب باشید!';
  const t1 = await ticketFor(s.slug, '0790000801');
  const t2 = await ticketFor(s.slug, '0790000802');
  const first = await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'fraud', body: text }, t1);
  const second = await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'fraud', body: `  ${text.replace('،', ' ')}  ` }, t2);
  assert.equal(first.status, 201);
  assert.equal(second.status, 201);
  const cases = (await adm('GET', `/cases?shopId=${s.shopId}`)).body.cases;
  const flagged = cases.find((x) => x.flags.includes('same_text'));
  assert.ok(flagged, JSON.stringify(cases));
  assert.equal(flagged.status, 'open', 'برچسب پرونده را خودکار نمی‌بندد');
  assert.ok(flagged.duplicateOf);
  assert.ok(flagged.flags.includes('same_ip'));
  const full = (await adm('GET', `/cases/${flagged.id}`)).body.case;
  assert.equal(full.sameText.length, 1);
  //  دکان برچسب نمی‌بیند
  const sv = await shopApi(s.token, 'GET', `/cases/${flagged.id}`);
  assert.ok(!JSON.stringify(sv.body).includes('same_text'));
  assert.ok(!sv.body.case.history.some((e) => e.type === 'flagged'));

  //  دو درخواستِ هم‌زمانِ یک شاکی ⇒ یک پرونده
  const t3 = await ticketFor(s.slug, '0790000803');
  const both = await Promise.all([1, 2].map(() => pub(s.slug, 'POST', '/reports', { productId: 'rice', category: 'wrong_price' }, t3)));
  assert.deepEqual(both.map((r) => r.status).sort(), [200, 201]);
  assert.equal(both[0].body.case.token, both[1].body.case.token);
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM trust_cases WHERE shop_id=$1 AND subject='product'`, [s.shopId])).n), 1);
});

test('در دیتابیس شماره و IP ِ شاکی نیست — فقط HMAC و نشانهٔ پوشیده', async () => {
  const s = await liveStore('حریم');
  const t = await ticketFor(s.slug, '0790000901');
  assert.equal((await pub(s.slug, 'POST', '/complaints', { subject: 'shop', category: 'other', body: 'متن' }, t)).status, 201);
  const row = await one('SELECT * FROM trust_cases WHERE shop_id=$1', [s.shopId]);
  const dump = JSON.stringify(row);
  assert.ok(!dump.includes('790000901'));
  assert.ok(!dump.includes('127.0.0.1') && !dump.includes('::1'));
  assert.equal(row.reporter_hint, '+937•••••901');
  const ev = await one('SELECT COUNT(*)::int AS n FROM trust_case_events WHERE case_id=$1 AND actor_id<>$2', [row.id, '']);
  assert.equal(ev.n, 0, 'رویدادِ مشتری شناسه ندارد');
});

test('تاریخچه به ترتیبِ رخداد است — «تصمیم» پیش از «جریمه»، حتی هر دو در یک میلی‌ثانیه', async (t) => {
  //  دو رویدادِ یک تراکنش با همان `now()` و شناسهٔ تصادفی پیش‌تر به ترتیبِ شناسه خوانده می‌شدند
  //  (ABUSE-01 در CI ِ تند گاهی «جریمه» را پیش از «تصمیم» دید)
  const trust = require('../src/lib/market-trust');
  const o = await shopOwner('ترتیبِ رویداد');
  const at = now();
  const ids = [];
  for (let i = 0; i < 16; i++) {
    const id = newId('tc');
    await query(
      `INSERT INTO trust_cases (id, shop_id, kind, subject, category, severity, reporter_key, created_at, updated_at)
       VALUES ($1,$2,'complaint','shop','fraud','medium',$3,$4,$4)`,
      [id, o.shopId, `order-${i}`, at]
    );
    ids.push(id);
  }
  t.mock.method(Date, 'now', () => at);
  try {
    for (const id of ids) await trust.decide('adm_order', id, { outcome: 'upheld', sanction: { kind: 'warning' } }, 'آزمونِ ترتیب');
  } finally { t.mock.restoreAll(); }
  for (const id of ids) {
    assert.deepEqual((await trust.adminView(id)).history.map((e) => e.type), ['decided', 'sanction_applied'], id);
  }
});
