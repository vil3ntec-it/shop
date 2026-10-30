'use strict';
/**
 * ماتریسِ جداسازیِ دکان‌ها — §۸ برنامهٔ امنیت، TEN-01..04 (`docs/security/tenant-fa.md`).
 *
 * سه آزمونِ دیگر هر کدام یک خانواده را **از روی فهرستِ زندهٔ مسیرها** قفل می‌کنند:
 *   `shop-route-inventory.test.js` ⇐ هر درِ `/api/shop/*` (دکانِ «ب» با شناسهٔ واقعیِ «الف»)
 *   `role-route-isolation.test.js` ⇐ درهای مدیر، پیک و مشتری
 *   `courier-scope.test.js`        ⇐ توکنِ پیک روی هر در
 * این پرونده بقیه را می‌بندد و نمی‌گذارد مسیری بی‌صاحب بماند:
 *
 *   ۱) ردهٔ همهٔ مسیرها: هر درِ `/api` (از روترِ ساخته‌شده از همین سورس) دقیقاً در
 *      یک خانواده است. خانواده‌های پیشوندی آزمونِ خودکارِ خودشان را دارند؛ بقیه
 *      **یک‌به‌یک** نام برده شده‌اند — مسیرِ تازه‌ای که در هیچ‌کدام نباشد آزمون را سرخ می‌کند.
 *   ۲) ماتریسِ درهای نشست‌دارِ بیرون از `/api/shop` (دفترِ مستقیم `/api/<مجموعه>`،
 *      همگام‌سازیِ v1/v3/قدیمی، خبرها، «من»، درگاهِ حساب، پشتیبانی، پشتیبانِ حساب،
 *      عکس و صندوقِ عکس، اعلان، پرداخت و رسید، لوکیشن، مجوز): دکانِ «الف» در هر
 *      خانواده دادهٔ واقعی دارد؛ صاحبِ «ب» — که **شاگردِ «الف» هم هست** (کاربرِ
 *      چنددکانی) — با شناسه‌های واقعیِ «الف» در نشانی/بدنه/opها و `X-Shop-Id`/`?shopId=`ِ
 *      «الف» هیچ نشانی از «الف» نمی‌گیرد و هیچ ردیفی از «الف» عوض نمی‌شود؛ و خودِ
 *      «الف» با همان نشانی‌ها داده‌اش را می‌بیند (آزمون بی‌اثر نیست).
 *   ۳) همان کاربرِ چنددکانی با نشستِ بسته به «الف» (شاگرد): دادهٔ «الف» را می‌بیند، دادهٔ
 *      «ب» را نه، کارِ مالک/مدیر ۴۰۳؛ عضویتش که معلق شد هیچ دری دادهٔ «الف» یا «ب» نمی‌دهد
 *      (افتادنِ بی‌صدا به دکانِ دیگر نیست).
 *   ۴) TEN-04: هر درِ GETِ مدیر که یک حساب/کاربر/گفت‌وگو را با شناسه باز می‌کند یا
 *      ردیفِ سابقه می‌نویسد یا با دلیل کنار گذاشته شده؛ هر دری که محتوای خصوصیِ دکان
 *      را برمی‌گرداند ردیفِ سابقه دارد؛ و هیچ درِ مدیر دفترِ دکان (کالا، قرض، فروش…) را نمی‌دهد.
 */
process.env.SYNC_V3_ENABLED = '1';

const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const inventory = require('./route-inventory');

inventory.trackMounts();   //  پیش از ساختنِ برنامه

const h = require('./helpers');
const F = require('./image-fixtures');
const { many, one, query, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const DATA = require('../src/routes/data').ROUTES;

let admin = { token: '', id: '' };
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const id = newId('adm');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ten-admin','مدیرِ ماتریس',$2,'superadmin','active',$3)`,
    [id, await pw.hashPassword('Admin!12345'), now()]
  );
  const t = (await h.post('/api/admin/login', { username: 'ten-admin', password: 'Admin!12345' })).body.token;
  assert.ok(t, 'ورودِ مدیر');
  admin = { token: t, id };
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => resetAll());

/* ══════════════════════════════════════════════════════════════════════
   ۱) ردهٔ مسیرها
   ══════════════════════════════════════════════════════════════════════ */

/** خانواده‌هایی که آزمونِ خودشان **هر** مسیرِ زیرِ پیشوند را خودکار می‌سنجد. */
const PREFIX = [
  { id: 'shop', re: /^\/api\/shop(\/|$)/, by: 'shop-route-inventory.test.js (+ کاربرِ چنددکانیِ همین پرونده، آزمونِ ۳)' },
  { id: 'admin', re: /^\/api\/admin\//, by: 'role-route-isolation.test.js، admin-control-plane.test.js، آزمونِ ۴ِ همین پرونده' },
  { id: 'courier', re: /^\/api\/courier\//, by: 'courier-scope.test.js، role-route-isolation.test.js' },
  { id: 'customer', re: /^\/api\/(store|market)(\/|$)/, by: 'role-route-isolation.test.js — سمتِ مشتری، بیرون از دامنهٔ این برنامه (§۴۰.۵)' },
  { id: 'pump', re: /^\/api\/pump(\/|$)/, by: 'pump-isolation.test.js (فقط PostgreSQL) — بخشِ پمپ، دکان نیست' },
];

/** بی دکان: عمومی یا هویتِ خودِ آدم — هر کدام با دلیل. */
const NO_TENANT = {
  'GET /api/health': 'سلامت', 'GET /api/ready': 'آمادگی', 'GET /api/config': 'پیکربندیِ عمومی',
  'GET /api/plans': 'پلن‌های عمومی', 'GET /api/terms': 'شرایط', 'GET /api/downloads': 'فهرستِ دانلود',
  'GET /api/license/public-key': 'کلیدِ عمومی', 'POST /api/visit': 'شمارشِ بازدید', 'POST /api/errors': 'گزارشِ خطا، فقط نوشتن',
  'POST /api/location': 'لوکیشنِ دستگاه، فقط نوشتن (claimDevice فقط ردیف‌های بی‌صاحب — TEN-L3)',
  'GET /api/releases/:tag/latest': 'انتشارِ عمومی (?shop= فقط کانالِ بتا — TEN-L4)',
  'GET /api/releases/:tag/file/:name': 'فایلِ انتشارِ عمومی',
  'GET /api/receipt/:tag/:token': 'رسیدِ QR با توکنِ HMAC — receipt-public.test.js، receipt-token.test.js',
  'POST /api/auth/staff': 'پیوستن با کد — staff-code.test.js', 'POST /api/auth/staff-login': 'ورودِ شاگرد با کد — staff-login.test.js',
  'POST /api/auth/register': 'ثبت‌نام (بسته)', 'POST /api/auth/register/start': 'ثبت‌نام', 'POST /api/auth/register/verify': 'ثبت‌نام',
  'POST /api/auth/register/complete': 'ثبت‌نام', 'GET /api/auth/terms': 'شرایط', 'POST /api/auth/login': 'ورود',
  'POST /api/auth/otp/request': 'کدِ ورود', 'POST /api/auth/otp/verify': 'کدِ ورود', 'POST /api/auth/google': 'ورود با گوگل',
  'POST /api/auth/google/link-code': 'ورود با گوگل', 'POST /api/auth/refresh': 'تازه‌سازیِ نشست (دکانِ برگزیده می‌ماند — active-shop.test.js)',
  'POST /api/auth/logout': 'خروج', 'POST /api/auth/logout-all': 'خروج از همه — هویت', 'POST /api/auth/reauth/code': 'تأییدِ دوباره',
  'POST /api/auth/reauth': 'تأییدِ دوباره', 'POST /api/auth/password': 'تغییرِ رمز', 'POST /api/auth/password/forgot': 'بازیابی',
  'POST /api/auth/password/reset': 'بازیابی', 'POST /api/auth/:app/request-code': 'ورودِ برنامه با کد',
  'GET /api/auth/:app/request-status': 'ورودِ برنامه با کد', 'POST /api/auth/:app/verify': 'ورودِ برنامه با کد',
};

/* ── دادهٔ واقعیِ دکان‌ها ─────────────────────────────────────────────── */

let seq = 0;
const uniq = () => `${Date.now().toString(36)}${(++seq).toString(36)}${crypto.randomBytes(3).toString('hex')}`;
const jpegB64 = (seed) => F.jpeg(64, 48, { seed }).toString('base64');
let opN = 0;
const opId = () => `01JTEN${String(Date.now()).slice(-8)}${String(++opN).padStart(12, '0')}`.slice(0, 26);
let hlcTick = 0;
const hlc = require('../src/lib/hlc');
const H = (node) => hlc.format({ wall: Date.now() - 3_600_000 + (++hlcTick) * 10, counter: 0, node });

async function owner(label) {
  const u = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id, userId: u.user.id };
}

/** کدِ جفت‌شدنِ گوشی و کامپیوتر — عمداً برای هر دو دکان یکی، تا جدایی فقط از حساب بیاید */
const PAIR = '482913';
const v1push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
});
const v1op = (type, table, rowId, fields) => ({ op_id: opId(), ts: Date.now(), table, row_id: rowId, type, fields });
const v3push = (token, device, ops) => h.api('POST', '/api/sync/v3/push', { token, body: { device_id: device, protocol: 3, ops } });

/**
 * یک دکانِ کامل با یک نشانهٔ یکتا در هر خانواده. `M` نشانه‌ها (متن)، `I` شناسه‌ها.
 * نشانه‌ها متن‌اند و در هیچ درخواستِ «ب» نمی‌آیند؛ پس پیدا شدنشان در پاسخِ «ب» یعنی نشت.
 */
async function seedShop(label) {
  const o = await owner(label);
  const tag = uniq();
  const M = {};
  const I = {};
  const mark = (k) => (M[k] = `رازِ-${k}-${label}-${tag}`);
  //  نامِ دکان خودش نشانه است — در «من»، درگاه و رسیدِ پرداخت می‌آید
  mark('shopName');
  assert.equal((await h.put('/api/shop', { name: M.shopName }, { token: o.token })).status, 200);

  //  دفترِ مستقیم: هر ده مجموعه یک ردیف
  I.data = {};
  for (const path of Object.keys(DATA)) {
    I.data[path] = `ten-${path}-${tag}`;
    const r = await h.post(`/api/${path}`, { id: I.data[path], data: { name: mark(`data.${path}`), note: M[`data.${path}`] } }, { token: o.token });
    assert.equal(r.status, 201, `${path}: ${JSON.stringify(r.body)}`);
  }

  //  همگام‌سازیِ قدیمی
  I.legacy = `ten-legacy-${tag}`;
  const lp = await h.post('/api/sync/push', { deviceId: 'dev-legacy', changes: [{ collection: 'products', id: I.legacy, data: { name: mark('legacy') } }] }, { token: o.token });
  assert.equal(lp.status, 200, JSON.stringify(lp.body));

  //  Sync v1: کالا + عکس + صندوقِ عکس
  I.device = `dev-v1-${tag}`.slice(0, 60);
  I.v1prod = `ten-v1-${tag}`;
  const p1 = await v1push(o.token, I.device, [v1op('insert', 'products', I.v1prod, { name: mark('v1'), unit: 'دانه', salePrice: 10 })]);
  assert.equal(p1.status, 200, JSON.stringify(p1.body));
  assert.equal(p1.body.results[0].status, 'applied', JSON.stringify(p1.body));
  const ph = await h.put(`/api/sync/v1/photos/${I.v1prod}`, { data: jpegB64(label.length + 3), device_id: I.device }, { token: o.token });
  assert.equal(ph.status, 200, JSON.stringify(ph.body));
  M.photoHash = ph.body.hash;
  assert.ok(M.photoHash, JSON.stringify(ph.body));
  const inbox = await h.post('/api/sync/v1/photo-inbox', { data: jpegB64(label.length + 9), pair: PAIR, product_id: I.v1prod, device_id: I.device }, { token: o.token });
  assert.equal(inbox.status, 200, JSON.stringify(inbox.body));
  I.inbox = inbox.body.id;
  M.inboxId = inbox.body.id;

  //  Sync v3: کالا + تعارضِ واقعی (نوشتهٔ دیده‌نشدهٔ دستگاهِ دیگر)
  I.v3prod = `ten-v3-${tag}`;
  const p3 = await v3push(o.token, 'v3-a', [{ op_id: opId(), hlc: H('v3-a'), table: 'products', row_id: I.v3prod, kind: 'insert', fields: { name: mark('v3') } }]);
  assert.equal(p3.status, 200, JSON.stringify(p3.body));
  I.v3debtor = `ten-v3d-${tag}`;
  await v3push(o.token, 'v3-a', [{ op_id: opId(), hlc: H('v3-a'), table: 'debtors', row_id: I.v3debtor, kind: 'insert', fields: { name: mark('v3conflict') } }]);
  const cf = await v3push(o.token, 'v3-b', [{ op_id: opId(), hlc: H('v3-b'), table: 'debtors', row_id: I.v3debtor, kind: 'update', fields: { name: `بعدی-${tag}` } }]);
  assert.equal(cf.body.results[0].conflicts, 1, JSON.stringify(cf.body));
  const box = (await h.get('/api/sync/v3/conflicts?status=open', { token: o.token })).body.conflicts.find((c) => c.row_id === I.v3debtor);
  assert.ok(box, 'تعارضِ v3');
  I.v3conflict = box.id;

  //  خبرِ دکان
  const ev = await h.post('/api/events', { events: [{ kind: 'note', title: 'یادداشت', body: mark('event'), clientId: `ev-${tag}` }] }, { token: o.token });
  assert.equal(ev.status, 201, JSON.stringify(ev.body));

  //  پشتیبانِ حساب
  const bk = await h.raw('POST', '/api/me/backups?ext=json&kind=manual', Buffer.from(JSON.stringify({ secret: mark('backup') })), { token: o.token });
  assert.equal(bk.status, 201, JSON.stringify(bk.body));
  I.backup = bk.body.backup.id;
  M.backupId = I.backup;

  //  پشتیبانی، لوکیشن، دستگاه
  const sp = await h.post('/api/support/messages', { body: mark('support') }, { token: o.token });
  assert.equal(sp.status, 201, JSON.stringify(sp.body));
  I.supportThread = sp.body.thread.id;
  const loc = await h.post('/api/location', { device: { uid: `loc-${tag}` }, location: { lat: 34.5, lng: 69.1, accuracy: 5, label: mark('location') } }, { token: o.token });
  assert.equal(loc.status, 201, JSON.stringify(loc.body));
  const devs = await h.get('/api/me/devices', { token: o.token });
  I.deviceRow = devs.body.devices[0].id;
  M.deviceRow = I.deviceRow;
  I.deviceUid = devs.body.devices[0].device_uid;

  //  اعلانِ مدیر به همین آدم، پرداختِ اشتراکِ همین دکان
  const nt = await h.post('/api/admin/notices', {
    app: 'shop', audience: { kind: 'user', user_id: o.userId }, channels: ['inapp'], title: mark('notice'), body: 'متن',
  }, { token: admin.token });
  assert.equal(nt.status, 201, JSON.stringify(nt.body));
  assert.equal((await h.post(`/api/admin/notices/${nt.body.notice.id}/send`, {}, { token: admin.token })).status, 200);
  const pay = await h.post('/api/admin/payments', { app: 'shop', tenantId: o.shopId, amount: 1500 }, { token: admin.token });
  assert.equal(pay.status, 201, JSON.stringify(pay.body));
  I.payment = pay.body.payment.id;
  M.paymentId = I.payment;
  //  شناسهٔ اعلان در صندوقِ همین آدم (ردیفِ تحویل)
  I.notice = (await h.get('/api/me/notices', { token: o.token })).body.notices[0].id;
  return { ...o, M, I, tag };
}

/* ── ماتریسِ درهای نشست‌دار ─────────────────────────────────────────────
   هر مسیر ⇒ درخواستِ واقعی با شناسه‌های «الف». `own` = نشانه‌هایی که خودِ
   «الف» با همین درخواست باید ببیند (آزمون بی‌اثر نیست). */

function casesFor(I) {
  const C = {};
  const add = (key, spec) => { C[key] = spec; };
  for (const [path] of Object.entries(DATA)) {
    add(`GET /api/${path}`, { url: `/api/${path}?limit=500`, own: [`data.${path}`] });
    add(`GET /api/${path}/:id`, { url: `/api/${path}/${I.data[path]}`, own: [`data.${path}`] });
    add(`POST /api/${path}`, { url: `/api/${path}`, body: { id: I.data[path], data: { name: 'نوشتهٔ دیگری' } } });
    add(`PUT /api/${path}/:id`, { url: `/api/${path}/${I.data[path]}`, body: { data: { name: 'رونویسیِ دیگری' } } });
    add(`DELETE /api/${path}/:id`, { url: `/api/${path}/${I.data[path]}` });
  }
  add('POST /api/sales/full', { url: '/api/sales/full', body: { sale: { id: I.data.sales }, items: [{ id: I.data['sale-items'], name: 'x' }] } });

  //  همگام‌سازیِ قدیمی (همان درها زیرِ /api/shop/sync را shop-route-inventory می‌سنجد)
  const legacyChanges = () => ({ deviceId: 'dev-x', changes: [{ collection: 'products', id: I.legacy, data: { name: 'دیگری' } }, { collection: 'products', id: I.legacy, deleted: true }] });
  add('GET /api/sync/status', { url: '/api/sync/status' });
  add('POST /api/sync', { url: '/api/sync', body: legacyChanges });
  add('GET /api/sync', { url: '/api/sync?since=0', own: ['legacy'] });
  add('POST /api/sync/push', { url: '/api/sync/push', body: legacyChanges });
  add('GET /api/sync/pull', { url: '/api/sync/pull?since=0', own: ['legacy'] });

  //  Sync v1
  const v1ops = () => ({ device_id: 'dev-other', schema_version: 2, ops: [
    v1op('update', 'products', I.v1prod, { name: 'دیگری' }), v1op('delete', 'products', I.v1prod, {}),
  ] });
  add('POST /api/sync/v1/live/ticket', { url: '/api/sync/v1/live/ticket', body: {} });
  add('POST /api/sync/v1/push', { url: '/api/sync/v1/push', body: v1ops, headers: { 'X-App': 'shop' } });
  add('GET /api/sync/v1/pull', { url: '/api/sync/v1/pull?device_id=dev-reader&since=0', own: ['v1'] });
  add('GET /api/sync/v1/snapshot', { url: '/api/sync/v1/snapshot?device_id=dev-reader', own: ['v1'] });
  add('GET /api/sync/v1/status', { url: `/api/sync/v1/status?device_id=${I.device}` });
  add('GET /api/sync/v1/conflicts', { url: '/api/sync/v1/conflicts?all=1' });
  add('GET /api/sync/v1/deleted', { url: '/api/sync/v1/deleted' });
  add('GET /api/sync/v1/photos', { url: '/api/sync/v1/photos?since=0', own: ['photoHash'] });
  add('GET /api/sync/v1/photos/:productId', { url: `/api/sync/v1/photos/${I.v1prod}`, own: ['photoHash'] });
  add('PUT /api/sync/v1/photos/:productId', { url: `/api/sync/v1/photos/${I.v1prod}`, body: () => ({ data: jpegB64(77) }) });
  add('DELETE /api/sync/v1/photos/:productId', { url: `/api/sync/v1/photos/${I.v1prod}` });
  add('POST /api/sync/v1/photo-inbox', { url: '/api/sync/v1/photo-inbox', body: () => ({ data: jpegB64(78), pair: PAIR, product_id: I.v1prod }) });
  add('GET /api/sync/v1/photo-inbox', { url: `/api/sync/v1/photo-inbox?pair=${PAIR}`, own: ['inboxId'] });
  add('GET /api/sync/v1/photo-inbox/:id', { url: `/api/sync/v1/photo-inbox/${I.inbox}`, ownStatus: 200 });
  add('DELETE /api/sync/v1/photo-inbox/:id', { url: `/api/sync/v1/photo-inbox/${I.inbox}` });

  //  Sync v3
  const v3ops = () => ({ device_id: 'v3-other', protocol: 3, ops: [
    { op_id: opId(), hlc: H('v3-other'), table: 'products', row_id: I.v3prod, kind: 'update', fields: { name: 'دیگری' } },
    { op_id: opId(), hlc: H('v3-other'), table: 'products', row_id: I.v3prod, kind: 'delete', fields: {} },
  ] });
  add('GET /api/sync/v3/hello', { url: '/api/sync/v3/hello' });
  add('POST /api/sync/v3/push', { url: '/api/sync/v3/push', body: v3ops });
  add('GET /api/sync/v3/pull', { url: '/api/sync/v3/pull?device_id=v3-reader&since=0', own: ['v3'] });
  add('GET /api/sync/v3/snapshot', { url: '/api/sync/v3/snapshot?device_id=v3-reader', own: ['v3'] });
  add('GET /api/sync/v3/status', { url: '/api/sync/v3/status?device_id=v3-a' });
  add('POST /api/sync/v3/report', { url: '/api/sync/v3/report', body: { device_id: 'v3-other', kind: 'stats' } });
  add('GET /api/sync/v3/conflicts', { url: '/api/sync/v3/conflicts?status=open', own: ['v3conflict'] });
  add('POST /api/sync/v3/conflicts/:id/resolve', { url: `/api/sync/v3/conflicts/${I.v3conflict}/resolve`, body: { action: 'keep' } });

  //  خبرها
  add('POST /api/events', { url: '/api/events', body: { events: [{ kind: 'note', body: 'خبرِ دیگری' }] } });
  add('GET /api/events', { url: '/api/events?since=0', own: ['event'] });
  add('POST /api/events/seen', { url: '/api/events/seen', body: { at: Date.now() } });

  //  «من»
  add('GET /api/me', { url: '/api/me', own: ['shopName'] });
  add('PUT /api/me', { url: '/api/me', body: { name: 'نامِ دیگری' } });
  add('GET /api/me/notices', { url: '/api/me/notices', own: ['notice'] });
  add('POST /api/me/notices/:id/read', { url: `/api/me/notices/${I.notice}/read`, body: {} });
  add('GET /api/me/payments', { url: '/api/me/payments', own: ['paymentId'] });
  add('GET /api/me/settings', { url: '/api/me/settings' });
  add('PUT /api/me/settings', { url: '/api/me/settings', body: { cloudSync: true } });
  add('POST /api/me/redeem', { url: '/api/me/redeem', body: { code: 'ZZZZ-ZZZZ' } });
  add('GET /api/me/heartbeat', { url: '/api/me/heartbeat' });
  add('POST /api/me/errors', { url: '/api/me/errors', body: { message: 'آزمون' } });
  add('GET /api/me/subscription', { url: '/api/me/subscription' });
  add('GET /api/me/plans', { url: '/api/me/plans' });
  add('POST /api/me/purchase-request', { url: '/api/me/purchase-request', body: { plan: 'basic' } });
  add('GET /api/me/devices', { url: '/api/me/devices', own: ['deviceRow'] });
  add('DELETE /api/me/devices/:id', { url: `/api/me/devices/${I.deviceRow}` });
  add('POST /api/me/push', { url: '/api/me/push', body: () => ({ token: `push-${uniq()}` }) });
  add('DELETE /api/me/push', { url: '/api/me/push', body: () => ({ token: `push-${uniq()}` }) });
  add('GET /api/me/backups', { url: '/api/me/backups', own: ['backupId'] });
  add('POST /api/me/backups', { url: '/api/me/backups?ext=json', raw: true });
  add('GET /api/me/backups/:id', { url: `/api/me/backups/${I.backup}`, own: ['backup'] });
  add('DELETE /api/me/backups/:id', { url: `/api/me/backups/${I.backup}` });

  //  درگاهِ حساب
  add('GET /api/portal/me', { url: '/api/portal/me', own: ['shopName'] });
  add('GET /api/portal/subscriptions', { url: '/api/portal/subscriptions' });
  add('GET /api/portal/payments', { url: '/api/portal/payments', own: ['paymentId'] });
  add('GET /api/portal/payments/:id/receipt', { url: `/api/portal/payments/${I.payment}/receipt`, own: ['shopName'] });
  add('GET /api/portal/devices', { url: '/api/portal/devices', own: ['deviceRow'] });
  add('DELETE /api/portal/devices/:id', { url: `/api/portal/devices/${I.deviceRow}` });
  add('GET /api/portal/support', { url: '/api/portal/support', own: ['support'] });
  add('POST /api/portal/support/messages', { url: '/api/portal/support/messages', body: { body: 'سلام از دیگری' } });
  add('GET /api/portal/notices', { url: '/api/portal/notices', own: ['notice'] });
  add('POST /api/portal/notices/:id/read', { url: `/api/portal/notices/${I.notice}/read`, body: {} });
  add('GET /api/portal/downloads', { url: '/api/portal/downloads' });
  add('GET /api/portal/settings', { url: '/api/portal/settings' });
  add('PUT /api/portal/settings', { url: '/api/portal/settings', body: { cloudSync: true } });
  add('POST /api/portal/redeem', { url: '/api/portal/redeem', body: { code: 'ZZZZ-ZZZZ' } });
  add('GET /api/portal/heartbeat', { url: '/api/portal/heartbeat' });
  add('POST /api/portal/errors', { url: '/api/portal/errors', body: { message: 'آزمون' } });
  add('GET /api/rep/me', { url: '/api/rep/me', ownStatus: 403 });   //  «الف» نماینده نیست؛ ردِ همسان

  //  اشتراک، کد، مجوز، لوکیشن، پشتیبانی
  add('GET /api/billing/status', { url: '/api/billing/status' });
  add('GET /api/billing/plans', { url: '/api/billing/plans' });
  add('POST /api/billing/request', { url: '/api/billing/request', body: { plan: 'basic' } });
  add('POST /api/vip/redeem', { url: '/api/vip/redeem', body: { code: 'ZZZZ-ZZZZ' } });
  add('POST /api/vip/offline-code', { url: '/api/vip/offline-code', body: { code: 'ZZZZ', computer: 'PC-1' } });
  add('POST /api/license/sync', { url: '/api/license/sync', body: { deviceUid: I.deviceUid } });
  add('GET /api/location/mine', { url: '/api/location/mine', own: ['location'] });
  add('GET /api/support/thread', { url: '/api/support/thread', own: ['support'] });
  add('POST /api/support/messages', { url: '/api/support/messages', body: { body: 'سلام از دیگری' } });
  add('POST /api/support/read', { url: '/api/support/read', body: {} });
  add('POST /api/support/push', { url: '/api/support/push', body: () => ({ token: `push-${uniq()}` }) });
  add('DELETE /api/support/push', { url: '/api/support/push', body: () => ({ token: `push-${uniq()}` }) });
  return C;
}

/** درخواست؛ `spoof` = شناسهٔ دکانِ دیگر در سرآیند، querystring و بدنه. */
function send(spec, token, spoof = '') {
  resetAll();
  let url = spec.url;
  const headers = { 'Idempotency-Key': `ten-${uniq()}`, ...(spec.headers || {}) };
  let body = typeof spec.body === 'function' ? spec.body() : spec.body;
  if (spoof) {
    url += `${url.includes('?') ? '&' : '?'}shopId=${spoof}&shop_id=${spoof}&account_id=${spoof}&tenantId=${spoof}`;
    headers['X-Shop-Id'] = spoof;
    headers['X-Account-Id'] = spoof;
    if (body && typeof body === 'object' && !Array.isArray(body)) body = { ...body, shopId: spoof, shop_id: spoof, account_id: spoof };
  }
  const method = spec.method;
  if (spec.raw) {
    return h.raw(method, url, Buffer.from(JSON.stringify({ from: 'other' })), { token, headers }).then((r) => ({ ...r, sent: url }));
  }
  return h.api(method, url, { token, headers, body: body ?? (method === 'GET' || method === 'DELETE' ? null : {}) })
    .then((r) => ({ ...r, sent: `${url} ${JSON.stringify(body ?? '')}` }));
}

/** نشانه‌هایی از `marks` که در پاسخ آمده‌اند و خودِ درخواست نیاورده بود. */
function leaked(res, marks) {
  const text = JSON.stringify(res.body ?? '');
  return Object.entries(marks).filter(([, s]) => s && text.includes(s) && !res.sent.includes(s)).map(([k]) => k);
}

/** ردپای کاملِ یک دکان و صاحبش: هر ردیفِ هر جدولی که ستونِ دکان/حساب یا کاربر دارد. */
async function footprint(shopId, userId) {
  const cols = await many(
    `SELECT table_name, column_name FROM information_schema.columns
      WHERE table_schema='public'
        AND column_name IN ('shop_id','account_id','tenant_id','used_shop_id','user_id','subject_id','owner_user_id')
      ORDER BY table_name, column_name`
  );
  const out = {};
  for (const { table_name: t, column_name: c } of cols) {
    const val = ['user_id', 'subject_id', 'owner_user_id'].includes(c) ? userId : shopId;
    const r = await one(
      `SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), '') AS h FROM "${t}" x WHERE x."${c}"::text = $1`, [val]
    );
    out[`${t}.${c}`] = `${r.n}:${r.h}`;
  }
  out['shops'] = JSON.stringify(await one('SELECT * FROM shops WHERE id=$1', [shopId]));
  out['users'] = JSON.stringify(await one('SELECT * FROM users WHERE id=$1', [userId]));
  return out;
}
const diff = (a, b) => Object.keys({ ...a, ...b }).filter((k) => a[k] !== b[k]);

let world = null;
/** دو دکان، و صاحبِ «ب» که شاگردِ «الف» هم هست (عضویتِ دوم، مثلِ active-shop.test.js). */
async function getWorld() {
  if (world) return world;
  const a = await seedShop('الف');
  const b = await seedShop('ب');
  const t = now();
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, b.userId, t]
  );
  world = { a, b };
  return world;
}

test('ردهٔ مسیرها: هر درِ /api دقیقاً یک خانوادهٔ جداسازی دارد؛ مسیرِ تازهٔ بی‌رده سرخ است', async () => {
  const routes = inventory.live(h.server());
  assert.ok(routes.length > 600, `فقط ${routes.length} در`);
  const cases = casesFor({ data: Object.fromEntries(Object.keys(DATA).map((k) => [k, 'x'])) });
  const bad = [];
  const tally = {};
  for (const r of routes) {
    const fams = [
      ...PREFIX.filter((f) => f.re.test(r.path)).map((f) => f.id),
      ...(NO_TENANT[r.key] ? ['no-tenant'] : []),
      ...(cases[r.key] ? ['session-matrix'] : []),
    ];
    if (fams.length !== 1) bad.push(`${r.key} ⇒ ${fams.length ? fams.join(' + ') : 'بی‌رده: به NO_TENANT (با دلیل) یا casesFor (با درخواستِ واقعی) یا آزمونِ پیشوندی اضافه کنید'}`);
    for (const f of fams) tally[f] = (tally[f] || 0) + 1;
  }
  //  رده‌ای که دیگر مسیرش نیست هم سرخ — فهرست با سورس جلو می‌رود
  const keys = new Set(routes.map((r) => r.key));
  for (const k of [...Object.keys(NO_TENANT), ...Object.keys(cases)]) if (!keys.has(k)) bad.push(`${k} در رده هست ولی در سورس نیست`);
  assert.deepEqual(bad, [], bad.join('\n'));
  //  هر خانواده واقعاً مسیر دارد — فهرستِ زنده شکسته نیست
  for (const f of [...PREFIX.map((p) => p.id), 'no-tenant', 'session-matrix']) assert.ok(tally[f] > 0, `خانوادهٔ ${f} خالی است`);
  assert.ok(tally['session-matrix'] >= 120, `ماتریسِ نشست‌دار: ${tally['session-matrix']}`);
});

test('TEN-01/02: هر درِ نشست‌دارِ بیرون از /api/shop — صاحبِ «ب» (شاگردِ «الف» هم) با شناسه، سرآیند، querystring، بدنه و opهای «الف» هیچ دادهٔ «الف» نمی‌گیرد و هیچ ردیفش را عوض نمی‌کند', async () => {
  const { a, b } = await getWorld();
  const routes = inventory.live(h.server());
  const casesA = casesFor(a.I);
  const keyed = new Map(routes.map((r) => [r.key, r]));
  const specs = Object.entries(casesA).map(([key, spec]) => ({ key, ...spec, method: key.split(' ')[0], route: keyed.get(key) }));
  assert.ok(specs.every((s) => s.route), 'هر درخواستِ ماتریس مسیرِ زنده دارد');

  //  الف) خودِ «الف» با همین نشانی‌ها داده‌اش را می‌بیند — نشانه‌ها واقعی‌اند
  const own = [];
  let ownChecked = 0;
  for (const s of specs.filter((x) => x.method === 'GET')) {
    const res = await send(s, a.token);
    if (s.ownStatus ? res.status !== s.ownStatus : res.status >= 300) { own.push(`${s.key} برای «الف» ⇒ ${res.status} ${JSON.stringify(res.body).slice(0, 160)}`); continue; }
    for (const k of s.own || []) {
      ownChecked += 1;
      if (!JSON.stringify(res.body ?? '').includes(a.M[k])) own.push(`${s.key}: «الف» نشانهٔ ${k} را ندید`);
    }
  }
  assert.deepEqual(own, [], own.join('\n'));
  assert.ok(ownChecked >= 30, `فقط ${ownChecked} نشانه سنجیده شد`);
  //  هر نشانه دست‌کم در یک درِ GET دیده می‌شود — نشانهٔ بی‌خواننده سنجهٔ نشت را بی‌اثر می‌کرد
  const unread = Object.keys(a.M).filter((k) => !specs.some((s) => (s.own || []).includes(k)));
  assert.deepEqual(unread, [], `این نشانه‌ها را هیچ درِ GET نمی‌سنجد: ${unread.join('، ')}`);

  //  ب) صاحبِ «ب» — هر بردارِ جعل؛ ردپای «الف» پیش و پس
  const before = await footprint(a.shopId, a.userId);
  const bad = [];
  for (const s of specs) {
    for (const spoof of ['', a.shopId]) {
      const res = await send(s, b.token, spoof);
      if (res.status >= 500) bad.push(`${s.key}${spoof ? ' (جعل)' : ''} ⇒ ${res.status} ${JSON.stringify(res.body).slice(0, 160)}`);
      const l = leaked(res, a.M);
      if (l.length) bad.push(`${s.key}${spoof ? ' (جعل)' : ''} دادهٔ «الف» را داد: ${l.join('، ')}`);
      //  شناسهٔ «الف» فقط در خودِ درخواستِ جعلی بود؛ پاسخِ موفقی که آن را دارد یعنی سرور به آن تکیه کرد
      if (spoof && res.status < 300 && JSON.stringify(res.body ?? '').includes(a.shopId)) bad.push(`${s.key} (جعل) شناسهٔ دکانِ «الف» را در پاسخِ موفق آورد`);
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  const after = await footprint(a.shopId, a.userId);
  assert.deepEqual(diff(before, after), [], 'هیچ ردیفی از «الف» یا صاحبش عوض نشد');

  //  ج) نوشته‌های «ب» با شناسه‌های «الف» در فضای خودِ «ب» نشستند، نه در «الف»
  const bLegacy = await one('SELECT shop_id FROM products WHERE id=$1 AND shop_id=$2', [a.I.legacy, b.shopId]);
  assert.ok(bLegacy, 'نوشتنِ «ب» با شناسهٔ «الف» ردیفِ خودِ «ب» ساخت');
  const aStill = await h.get(`/api/products/${a.I.data.products}`, { token: a.token });
  assert.equal(aStill.status, 200);
  assert.ok(JSON.stringify(aStill.body).includes(a.M['data.products']), 'کالای «الف» سرِ جایش است');
  const v3 = await h.get('/api/sync/v3/conflicts?status=open', { token: a.token });
  assert.ok(v3.body.conflicts.some((c) => c.id === a.I.v3conflict), 'تعارضِ «الف» باز ماند');
});

test('TEN-01 کاربرِ چنددکانی: نشستِ بسته به «الف» (شاگرد) دادهٔ «ب» را نمی‌بیند و کارِ مالک ۴۰۳؛ عضویتِ معلق ⇒ هیچ دری دادهٔ هیچ دکانی نمی‌دهد', async () => {
  const { a, b } = await getWorld();
  const s = await h.signIn(b);
  const pick = await h.post('/api/shop/active', { shopId: a.shopId }, { token: s.accessToken });
  assert.equal(pick.status, 200, JSON.stringify(pick.body));
  assert.equal(pick.body.shop.role, 'staff');
  const token = s.accessToken;

  //  دادهٔ «الف» را (به اندازهٔ شاگرد) می‌بیند — نشست واقعاً به «الف» بسته است
  const sees = await h.get(`/api/products/${a.I.data.products}`, { token });
  assert.equal(sees.status, 200, JSON.stringify(sees.body));
  assert.ok(JSON.stringify(sees.body).includes(a.M['data.products']));

  const specs = Object.entries(casesFor(a.I)).map(([key, spec]) => ({ key, ...spec, method: key.split(' ')[0] }));
  //  مارکِ دکانِ «ب» (نه هویتِ خودِ آدم: پشتیبانی، دستگاه، لوکیشن، اعلان مالِ خودِ اوست)
  const shopMarks = (o) => Object.fromEntries(Object.entries(o.M).filter(([k]) => k.startsWith('data.') || ['legacy', 'v1', 'v3', 'v3conflict', 'event', 'backup', 'backupId', 'photoHash', 'inboxId', 'shopName', 'paymentId'].includes(k)));
  const bad = [];
  for (const sp of specs.filter((x) => x.method === 'GET')) {
    const res = await send(sp, token, b.shopId);
    const l = leaked(res, shopMarks(b));
    if (l.length) bad.push(`${sp.key} (نشستِ «الف») دادهٔ «ب» را داد: ${l.join('، ')}`);
    if (res.status >= 500) bad.push(`${sp.key} ⇒ ${res.status}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));

  //  مالک‌بودن در «ب» در «الف» اجازه نمی‌دهد
  const denied = [
    ['DELETE', `/api/products/${a.I.data.products}`],
    ['POST', `/api/sync/v3/conflicts/${a.I.v3conflict}/resolve`, { action: 'keep' }],
    ['DELETE', `/api/me/backups/${a.I.backup}`],
  ];
  for (const [m, url, body] of denied) {
    const r = await h.api(m, url, { token, body: body ?? null });
    assert.equal(r.status, 403, `${m} ${url} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }

  //  عضویت معلق شد ⇒ نه «الف»، نه افتادنِ بی‌صدا به «ب»
  await query(`UPDATE shop_members SET status='suspended' WHERE shop_id=$1 AND user_id=$2`, [a.shopId, b.userId]);
  const marks = { ...shopMarks(a), ...Object.fromEntries(Object.entries(shopMarks(b)).map(([k, v]) => [`ب:${k}`, v])) };
  const after = [];
  let closed = 0;
  for (const sp of specs) {
    const res = await send(sp, token);
    const l = leaked(res, marks);
    if (l.length) after.push(`${sp.key} پس از تعلیق داده داد: ${l.join('، ')}`);
    if (res.status >= 500) after.push(`${sp.key} ⇒ ${res.status}`);
    if (res.status === 403) closed += 1;
  }
  assert.deepEqual(after, [], after.join('\n'));
  for (const url of [`/api/products/${a.I.data.products}`, '/api/events', '/api/sync/pull?since=0']) {
    const r = await h.get(url, { token });
    assert.equal(r.status, 403, `${url} ⇒ ${r.status}`);
    assert.equal(r.body.error.code, 'active_shop_unavailable', url);
  }
  for (const url of ['/api/sync/v1/pull?device_id=x&since=0', '/api/sync/v3/pull?device_id=x&since=0']) {
    const r = await h.get(url, { token });
    assert.equal(r.status, 403, `${url} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
  }
  assert.ok(closed >= 60, `فقط ${closed} در ۴۰۳ داد`);
  await query(`UPDATE shop_members SET status='active' WHERE shop_id=$1 AND user_id=$2`, [a.shopId, b.userId]);
});

/* ══════════════════════════════════════════════════════════════════════
   ۴) TEN-04 — دسترسیِ پشتیبانی/مدیر به دادهٔ خصوصی: دامنهٔ محدود + سابقه
   ══════════════════════════════════════════════════════════════════════
   قاعده (`docs/spec/ADMIN-CONTROL-PLANE.md` بندِ ۲ و ۳، شکافِ G4): دیدِ پیش‌فرضِ مدیر
   فراداده است؛ هر جا **یک** حساب/کاربر/گفت‌وگو با شناسه باز می‌شود، ردیفِ سابقه با
   شناسهٔ همان موضوع می‌نشیند. فهرست‌های پرتکرار سابقه نمی‌خواهند (سیلابِ سابقه). */

/** درهای GETِ مدیر با شناسهٔ موضوع ⇒ سابقه‌دار (`audited`) یا کنار با دلیل (`exempt`). */
const ADMIN_SUBJECT = {
  'GET /api/admin/users/:id': { audited: 'admin.user_viewed' },
  'GET /api/admin/users/:id/delete-preview': { audited: 'admin.user_delete_previewed' },
  'GET /api/admin/shops/:id': { audited: 'admin.shop_viewed' },
  'GET /api/admin/shops/:id/staff-perms': { audited: 'admin.shop_staff_perms_viewed' },
  'GET /api/admin/support/threads/:id': { audited: 'admin.support_thread_viewed' },
  'GET /api/admin/accounts/:app/:tenantId/backups/:id': { audited: 'admin.account_backup_downloaded' },
  'GET /api/admin/pump/stations/:id': { audited: 'admin.station_viewed' },
  'GET /api/admin/pump/stations/:id/events': { audited: 'admin.station_events_viewed' },
  'GET /api/admin/trust/cases/:id': { elsewhere: 'market-trust.test.js (admin.trust_case_viewed، فقط ارشد)' },
  'GET /api/admin/trust/cases/:id/evidence/:evidenceId': { elsewhere: 'market-trust.test.js (admin.trust_evidence_viewed، فقط ارشد)' },
  'GET /api/admin/shops/:id/history': { exempt: 'تاریخچهٔ اشتراک — دفترِ صورت‌حسابِ خودِ پلتفرم، نه دفترِ دکان' },
  'GET /api/admin/pump/stations/:id/history': { exempt: 'تاریخچهٔ اشتراکِ پمپ — صورت‌حسابِ پلتفرم' },
  'GET /api/admin/shops/:id/staff-codes': { exempt: 'فقط فرادادهٔ کد؛ خودِ کد فقط با reveal ِ ارشد و سابقه (ACP-08)' },
  'GET /api/admin/accounts/:app/:tenantId/backups': { exempt: 'فهرستِ اندازه و تاریخ؛ محتوا فقط با دانلودِ سابقه‌دار' },
  'GET /api/admin/support/media/:mid': { exempt: 'رسانهٔ همان گفت‌وگویی که باز کردنش سابقه دارد؛ فقط در عبور و پس از گرفتن پاک' },
  'GET /api/admin/notices/:id': { exempt: 'اعلانِ خودِ مدیر' },
  'GET /api/admin/notices/:id/report': { exempt: 'گزارشِ تحویلِ اعلانِ خودِ مدیر' },
  'GET /api/admin/payments/:id/receipt': { exempt: 'رسیدِ صورت‌حسابِ خودِ پلتفرم' },
  'GET /api/admin/subscriptions/:id/addons': { exempt: 'افزونهٔ صورت‌حساب' },
  'GET /api/admin/pump/subscriptions/:id/addons': { exempt: 'افزونهٔ صورت‌حساب' },
  'GET /api/admin/reps/:id/report': { exempt: 'نمایندهٔ فروشِ خودِ پلتفرم' },
  'GET /api/admin/plans/:code/price-history': { exempt: 'سراسری' },
  'GET /api/admin/campaigns/:id/stats': { exempt: 'کارزارِ خودِ مدیر' },
  'GET /api/admin/image-bank/images/:id': { exempt: 'بانکِ مشترکِ تصویر — فقط عکسِ پیشنهادشده با رضایتِ دکان' },
  'GET /api/admin/image-bank/file/:id/:version/:size': { exempt: 'بانکِ مشترکِ تصویر' },
  'GET /api/admin/image-bank/batches/:id/report': { exempt: 'بانکِ مشترکِ تصویر' },
};
/** درهای GETی که محتوای خصوصیِ یک دکان را **می‌توانند** بدهند، بی سابقه، با دلیل. */
const CONTENT_LIST_OK = {
  'GET /api/admin/support/threads': 'صندوقِ پشتیبانی: پیش‌نمایشِ پیامی که خودِ کاربر به پشتیبانی نوشته؛ باز کردنِ گفت‌وگو سابقه دارد',
  'GET /api/admin/shop-desk/overview': 'همان صندوقِ پشتیبانی (ده گفت‌وگوی باز) روی میزِ فروشگاه',
};

test('TEN-04: هر درِ GETِ مدیر با شناسهٔ موضوع سابقه‌دار یا با دلیل کنار است؛ محتوای خصوصیِ دکان فقط از درِ سابقه‌دار؛ دفترِ دکان از هیچ درِ مدیر بیرون نمی‌آید', async () => {
  const { a } = await getWorld();
  const pumpUser = await h.newUser('پمپ‌دارِ ماتریس', 'pump');
  const st = await h.post('/api/pump', { name: 'پمپِ ماتریس', code: '' }, { token: pumpUser.accessToken });
  assert.equal(st.status, 201, JSON.stringify(st.body));
  const stationId = st.body.station.id;

  const routes = inventory.live(h.server()).filter((r) => r.method === 'GET' && r.path.startsWith('/api/admin/'));
  const subject = routes.filter((r) => r.path.includes(':'));
  const unclassified = subject.filter((r) => !ADMIN_SUBJECT[r.key]).map((r) => r.key);
  assert.deepEqual(unclassified, [], `درِ موضوع‌دارِ تازهٔ مدیر — سابقه بنویسد یا با دلیل کنار رود:\n${unclassified.join('\n')}`);
  const stale = Object.keys(ADMIN_SUBJECT).filter((k) => !routes.some((r) => r.key === k));
  assert.deepEqual(stale, [], 'رده‌ای که دیگر مسیرش نیست');

  const ids = (r) => {
    const p = r.path;
    if (p.startsWith('/api/admin/users/')) return { id: a.userId };
    if (p.startsWith('/api/admin/shops/')) return { id: a.shopId };
    if (p.startsWith('/api/admin/support/threads/')) return { id: a.I.supportThread };
    if (p.startsWith('/api/admin/accounts/')) return { app: 'shop', tenantId: a.shopId, id: a.I.backup };
    if (p.startsWith('/api/admin/pump/stations/')) return { id: stationId };
    if (p.startsWith('/api/admin/payments/')) return { id: a.I.payment };
    return {};
  };
  const fillA = (r) => r.path.replace(/:([A-Za-z_]+)/g, (_, k) => ids(r)[k] ?? 'x1')
    + `?account=${a.shopId}&shopId=${a.shopId}&app=shop&reason=${encodeURIComponent('بررسیِ آزمونِ جداسازی')}`;
  const auditCount = async () => Number((await one(
    `SELECT COUNT(*)::int AS n FROM audit_logs WHERE actor_type='admin' AND user_id=$1`, [admin.id]
  )).n);
  //  دفترِ دکان: هیچ درِ مدیر — حتی ارشد — این‌ها را نمی‌دهد (جز دانلودِ پشتیبانِ سابقه‌دار)
  const LEDGER = Object.fromEntries(Object.entries(a.M).filter(([k]) => k.startsWith('data.') || ['legacy', 'v1', 'v3', 'event'].includes(k)));
  //  محتوای خصوصیِ دیگر: پیامِ پشتیبانی، پشتیبان، لوکیشنِ دقیق، تعارض
  const PRIVATE = { support: a.M.support, backup: a.M.backup, location: a.M.location, v3conflict: a.M.v3conflict };

  const bad = [];
  for (const r of routes) {
    const before = await auditCount();
    resetAll();
    const res = await h.api('GET', fillA(r), { token: admin.token });
    const after = await auditCount();
    const text = JSON.stringify(res.body ?? '');
    if (res.status >= 500) bad.push(`${r.key} ⇒ ${res.status}`);
    const ledger = Object.entries(LEDGER).filter(([, s]) => text.includes(s)).map(([k]) => k);
    const backupDownload = r.key === 'GET /api/admin/accounts/:app/:tenantId/backups/:id';
    if (ledger.length && !backupDownload) bad.push(`${r.key} دفترِ دکان را به مدیر داد: ${ledger.join('، ')}`);
    const priv = Object.entries(PRIVATE).filter(([, s]) => text.includes(s)).map(([k]) => k);
    const cls = ADMIN_SUBJECT[r.key];
    if (priv.length && !(cls?.audited && after > before) && !CONTENT_LIST_OK[r.key]) {
      bad.push(`${r.key} محتوای خصوصی (${priv.join('، ')}) را بی ردیفِ سابقه داد`);
    }
    if (cls?.audited) {
      if (res.status !== 200) bad.push(`${r.key} برای ارشد ⇒ ${res.status} ${text.slice(0, 160)} — شناسه‌ها واقعی‌اند؟`);
      const row = await one(
        `SELECT action, target_id, shop_id, detail FROM audit_logs WHERE actor_type='admin' AND user_id=$1 ORDER BY created_at DESC, id DESC LIMIT 1`, [admin.id]
      );
      if (after <= before) bad.push(`${r.key} ردیفِ سابقه ننوشت`);
      else if (row.action !== cls.audited) bad.push(`${r.key} سابقهٔ «${row.action}» نوشت، نه «${cls.audited}»`);
      else {
        const subjectId = Object.values(ids(r)).find((x) => x && x !== 'shop') || '';
        const mention = JSON.stringify(row);
        if (!subjectId || !mention.includes(subjectId)) bad.push(`${r.key} سابقه شناسهٔ موضوع را ندارد: ${mention.slice(0, 200)}`);
      }
    }
  }
  assert.deepEqual(bad, [], bad.join('\n'));

  //  گفت‌وگوی پشتیبانی: فقط «باز کردن» سابقه دارد، نه هر نظرسنجیِ پیامِ تازه
  const before = await auditCount();
  const poll = await h.get(`/api/admin/support/threads/${a.I.supportThread}?after=1`, { token: admin.token });
  assert.equal(poll.status, 200);
  assert.equal(await auditCount(), before, 'نظرسنجیِ پیامِ تازه (after) سابقهٔ تازه نمی‌نویسد');

  //  مدیرِ عادی (پشتیبانی): پرونده و گفت‌وگو را با سابقه می‌بیند؛ دانلودِ پشتیبان نه
  const made = await h.post('/api/admin/admins', { username: 'ten-clerk', password: 'Clerk!12345', role: 'admin' }, { token: admin.token });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const clerk = (await h.post('/api/admin/login', { username: 'ten-clerk', password: 'Clerk!12345' })).body.token;
  const dl = await h.get(`/api/admin/accounts/shop/${a.shopId}/backups/${a.I.backup}?reason=x`, { token: clerk });
  assert.equal(dl.status, 403, 'دانلودِ پشتیبانِ حساب فقط کارِ ارشد است');
  const thread = await h.get(`/api/admin/support/threads/${a.I.supportThread}`, { token: clerk });
  assert.equal(thread.status, 200);
  const row = await one(`SELECT a.action, a.target_id FROM audit_logs a JOIN admins d ON d.id=a.user_id
    WHERE d.username='ten-clerk' ORDER BY a.created_at DESC LIMIT 1`);
  assert.deepEqual(row && { ...row }, { action: 'admin.support_thread_viewed', target_id: a.I.supportThread });
});
