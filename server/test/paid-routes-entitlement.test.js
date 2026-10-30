'use strict';
/**
 * SUB-10 (و با آن SUB-02 و F0-25): آزمونِ مستقیمِ **همهٔ** APIهای پولی با اشتراکِ
 * منقضی، معلق یا لغوشده — §۲۹.۸ و §۲.۳.
 *
 * مسیرها از خودِ سورس خوانده می‌شوند (`route-inventory.js`، همان روشِ
 * `shop-route-inventory.test.js`). هر مسیرِ نوشتنیِ `/api/shop` باید دقیقاً در یکی
 * از دسته‌های زیر باشد؛ مسیرِ تازه‌ای که دسته نداشته باشد آزمون را سرخ می‌کند:
 *   PAID       — کلیدِ پولیِ کاتالوگ (online_orders، storefront، delivery، multi_device):
 *                برای **صاحبِ دکان هم** ۴۰۳ subscription_expired|subscription_required
 *   JOIN       — پیوستنِ شاگرد با کد (multi_device در lib/staff-codes): ۴۰۳ با کدِ واقعی
 *   STAFF      — نوشتنِ دادهٔ دکان (همگام‌سازی): شاگرد ۴۰۳، صاحب همیشه می‌نویسد
 *                (قاعدهٔ requireDataWrite؛ F0-25 / D-05: دادهٔ فروشنده گروگان نمی‌شود)
 *   FREE       — بی کلیدِ پولی؛ اشتراکِ تمام‌شده هیچ‌گاه پاسخِ subscription_* نمی‌دهد
 * و پیش از هر حالت، با اشتراکِ فعال همان مسیرهای PAID بسته نیستند (ردِ بالا از اشتراک است).
 */
process.env.SYNC_V3_ENABLED = '1';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const inv = require('./route-inventory');
const { query, newId, now } = require('../src/db');
const { PAID_KEYS } = require('../src/lib/features');

const DAY = 24 * 60 * 60 * 1000;
const PAID = /^(POST|PUT|PATCH|DELETE) \/shop\/(orders|storefront|market|delivery)(\/|$)|^POST \/shop\/(staff-code|invite|staff-code\/rotate)$/;
const JOIN = ['POST /shop/staff/join', 'POST /shop/join'];
const STAFF = ['POST /shop/sync', 'POST /shop/sync/push'];
/** نوشتنی‌های بی کلیدِ پولی — هر کدام با دلیل؛ فهرست باید با سورس یکی بماند. */
const FREE = [
  ['هستهٔ دکان (core: settings): نام، تنظیمات، شماره‌گذاری و بستنِ دسترسیِ دیگران پس از پایانِ اشتراک هم باز است', [
    'POST /shop', 'POST /shop/create', 'PUT /shop', 'PUT /shop/settings', 'POST /shop/series',
    'PATCH /shop/members/:id', 'DELETE /shop/members/:id', 'POST /shop/members/:id/remove', 'POST /shop/leave',
    'POST /shop/staff-code/disable', 'DELETE /shop/staff-codes/:id',
    'PUT /shop/permissions/roles/:role', 'PUT /shop/permissions/members/:id',
    'POST /shop/devices/:id/disable', 'POST /shop/devices/:id/enable',
    'POST /shop/approver-keys', 'DELETE /shop/approver-keys/:id']],
  ['چت و شکایت کلیدِ کاتالوگ ندارند؛ فقط پرچم', [
    'POST /shop/chat/conversations', 'PUT /shop/chat/conversations/:id/status', 'POST /shop/chat/conversations/:id/messages',
    'PATCH /shop/chat/conversations/:id/messages/:mid', 'DELETE /shop/chat/conversations/:id/messages/:mid',
    'POST /shop/chat/conversations/:id/receipts', 'POST /shop/chat/conversations/:id/media',
    'PUT /shop/chat/conversations/:id/block', 'DELETE /shop/chat/conversations/:id/block', 'POST /shop/chat/conversations/:id/reports',
    'POST /shop/trust/cases/:id/responses', 'POST /shop/trust/cases/:id/appeal']],
  ['موجودیِ سرور، بانکِ عکس، قاعدهٔ تخفیف و رسیدِ اعلان کلیدِ کاتالوگ ندارند', [
    'PUT /shop/catalog/settings', 'POST /shop/catalog/refresh', 'PUT /shop/catalog/products/:id/low',
    'POST /shop/catalog/tasks/:id/resolve', 'POST /shop/catalog/moves',
    'POST /shop/catalog/transfers', 'POST /shop/catalog/transfers/:id/receive', 'POST /shop/catalog/transfers/:id/reject',
    'POST /shop/images/select', 'POST /shop/images', 'POST /shop/images/:id/versions', 'PUT /shop/images/:id/v/:version/:size',
    'POST /shop/images/:id/propose', 'POST /shop/images/:id/report', 'POST /shop/images/:id/process',
    'PUT /shop/promos/settings', 'PUT /shop/promos/rules/:id', 'DELETE /shop/promos/rules/:id',
    'POST /shop/order-notify/:id/ack']],
  ['سربرگ و لوگوی فاکتورِ نسخهٔ ۲ بخشی از صندوقِ پایه است و کلیدِ کاتالوگ ندارد (#379؛ فقط پرچمِ shop.receipt_v2)', [
    'PUT /shop/receipt-profile', 'PUT /shop/receipt-profile/logo', 'DELETE /shop/receipt-profile/logo']],
];
const FREE_KEYS = FREE.flatMap(([, keys]) => keys);
const FLAGS = ['shop.orders', 'shop.orders.refunds', 'shop.delivery', 'shop.storefront', 'shop.storefront.zones',
  'shop.chat', 'shop.catalog', 'shop.image_bank', 'shop.order_notify', 'market.trust'];

const writes = () => inv.routes('/shop').filter((r) => r.method !== 'GET');
const paidGate = (r) => /\bneedWrite\b/.test(r.head)
  || [...`${r.uses} ${r.head}`.matchAll(/requireFeature\(\s*'([^']+)'/g)].some((m) => PAID_KEYS.includes(m[1]));
const code = (r) => r.body?.error?.code || '';
const blocked = (r) => r.status === 403 && ['subscription_expired', 'subscription_required'].includes(code(r));
const url = (r) => `/api${r.path.replace(/:[A-Za-z_]+/g, 'x1')}`;

let admin = '';
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

/** دکانِ با اشتراکِ فعال، همهٔ پرچم‌ها، یک شاگرد و یک کدِ دعوتِ آماده؛ دورهٔ آزمایشی دیرزمانی تمام شده. */
async function paidShop(label) {
  const owner = await h.newUser(`صاحب-${label}`);
  const shopId = (await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken })).body.shop.id;
  const g = await h.post('/api/admin/subscriptions', { shopId, plan: 'm1', days: 30 }, { token: admin });
  assert.ok(g.status < 300, JSON.stringify(g.body));
  for (const key of FLAGS) {
    const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const first = (await h.post('/api/shop/staff-code', {}, { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد-${label}`);
  assert.ok((await h.post('/api/shop/staff/join', { code: first }, { token: staff.accessToken })).status < 300);
  const spare = await h.post('/api/shop/invite', { role: 'staff' }, { token: owner.accessToken });
  assert.equal(spare.status, 201, JSON.stringify(spare.body));
  await query('UPDATE shops SET created_at=$2 WHERE id=$1', [shopId, Date.now() - 400 * DAY]);
  return { owner, staff, shopId, subId: g.body.subscription.id, spare: spare.body.code };
}

const setStatus = (s, status) => h.post(`/api/admin/subscriptions/${s.subId}/status`, { status }, { token: admin })
  .then((r) => assert.equal(r.status, 200, JSON.stringify(r.body)));
const STATES = {
  'منقضی با گذشتِ زمان': (s) => query('UPDATE subscriptions SET ends_at=$2 WHERE id=$1', [s.subId, Date.now() - 1000]),
  'منقضی (وضعیتِ expired)': (s) => setStatus(s, 'expired'),
  'معلق (suspended)': (s) => setStatus(s, 'suspended'),
  'لغوشده (cancelled)': (s) => setStatus(s, 'cancelled'),
};

let seq = 0;
const op = () => ({ op_id: `01JS${String(Date.now()).slice(-9)}${String(++seq).padStart(7, '0')}`,
  ts: Date.now(), table: 'products', row_id: `p-${seq}`, type: 'insert', fields: { name: 'کالا' } });
/** راه‌های نوشتنِ دادهٔ دکان (STAFF و بیرون از /api/shop) با بدنهٔ درست. */
const DATA_WRITES = {
  'POST /shop/sync': (t) => h.post('/api/shop/sync', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /shop/sync/push': (t) => h.post('/api/shop/sync/push', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /sync': (t) => h.post('/api/sync', { deviceId: 'd1', changes: [{ collection: 'sales', id: `s-${++seq}`, updatedAt: Date.now(), data: { total: 100 } }] }, { token: t }),
  'POST /sync/v1/push': (t, dev) => h.api('POST', '/api/sync/v1/push', { token: t, headers: { 'X-App': 'shop', 'X-Device': dev },
    body: { device_id: dev, schema_version: 2, ops: [op()] } }),
  'POST /sync/v3/push': (t, dev) => h.post('/api/sync/v3/push', { device_id: dev, protocol: 3, ops: [] }, { token: t }),
  'POST /products': (t) => h.post('/api/products', { data: { name: 'کالا' } }, { token: t }),
};

test('SUB-10: هر مسیرِ نوشتنیِ /api/shop دقیقاً یک دسته دارد و دروازهٔ سورس با دسته می‌خواند', () => {
  const all = writes();
  assert.ok(all.length > 60, `فقط ${all.length} مسیرِ نوشتنی`);
  const keys = all.map((r) => r.key);
  const problems = [];
  for (const r of all) {
    const cats = [PAID.test(r.key), JOIN.includes(r.key), STAFF.includes(r.key), FREE_KEYS.includes(r.key)].filter(Boolean).length;
    if (cats !== 1) problems.push(`${r.key} (${r.file}): ${cats} دسته — PAID است یا FREE؟ دلیلش را این‌جا بنویسید`);
    if (PAID.test(r.key) && !paidGate(r)) problems.push(`${r.key}: PAID است ولی در سورس needWrite/requireFeature(کلیدِ پولی) ندارد`);
    if (!PAID.test(r.key) && paidGate(r)) problems.push(`${r.key}: دروازهٔ پولی دارد ولی در PAID نیست`);
  }
  for (const k of [...JOIN, ...STAFF, ...FREE_KEYS]) if (!keys.includes(k)) problems.push(`${k} دیگر در سورس نیست — فهرست را به‌روز کنید`);
  assert.deepEqual(problems, [], problems.join('\n'));
  assert.ok(all.filter((r) => PAID.test(r.key)).length >= 19, 'سفارش، ویترین، بازارچه، پیک و کدِ شاگرد');
});

test('SUB-10: با اشتراکِ فعال هیچ مسیرِ PAID برای صاحب، و هیچ راهِ نوشتنِ داده برای شاگرد، subscription_* نمی‌دهد', async () => {
  const s = await paidShop('فعال');
  const bad = [];
  for (const r of writes().filter((x) => PAID.test(x.key))) {
    const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
    if (/^subscription_/.test(code(res)) || res.status >= 500) bad.push(`${r.key} ⇒ ${res.status} ${code(res)}`);
  }
  for (const [k, call] of Object.entries(DATA_WRITES)) {
    const res = await call(s.staff.accessToken, 'dev-staff');
    if (res.status >= 300) bad.push(`شاگرد ${k} ⇒ ${res.status} ${code(res)}`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
});

for (const [name, apply] of Object.entries(STATES)) {
  test(`SUB-10 / F0-25: اشتراکِ ${name} — هر API پولی بسته، صاحب داده‌اش را می‌نویسد، بقیه دست‌نخورده`, async () => {
    const s = await paidShop(name);
    await apply(s);
    const bad = [];
    const all = writes();
    //  ۱) PAID: صاحبِ دکان هم رد می‌شود
    for (const r of all.filter((x) => PAID.test(x.key))) {
      const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
      if (!blocked(res)) bad.push(`PAID ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۲) JOIN با کدِ واقعی که پیش از پایانِ اشتراک ساخته شده
    for (const k of JOIN) {
      const newcomer = await h.newUser(`تازه-${name}`);
      const res = await h.post(`/api${k.split(' ')[1]}`, { code: s.spare }, { token: newcomer.accessToken });
      if (!blocked(res)) bad.push(`JOIN ${k} ⇒ ${res.status} ${code(res)}`);
    }
    //  ۳) دادهٔ دکان: شاگرد بسته، صاحب همیشه می‌نویسد (D-05)
    for (const [k, call] of Object.entries(DATA_WRITES)) {
      const st = await call(s.staff.accessToken, 'dev-staff');
      if (!blocked(st)) bad.push(`شاگرد ${k} ⇒ ${st.status} ${code(st)}`);
      const ow = await call(s.owner.accessToken, 'dev-owner');
      if (ow.status >= 300) bad.push(`صاحب ${k} ⇒ ${ow.status} ${code(ow)}`);
    }
    //  ۴) FREE: هرگز پاسخِ اشتراکی، هرگز ۵۰۰ (ساختِ دکانِ تازه آخرِ کار)
    const free = all.filter((x) => FREE_KEYS.includes(x.key)).sort((a, b) => (a.key.startsWith('POST /shop/create') || a.key === 'POST /shop') - (b.key.startsWith('POST /shop/create') || b.key === 'POST /shop'));
    for (const r of free) {
      const res = await h.api(r.method, url(r), { body: {}, token: s.owner.accessToken });
      if (/^subscription_/.test(code(res)) || res.status >= 500) bad.push(`FREE ${r.key} ⇒ ${res.status} ${code(res)}`);
    }
    assert.deepEqual(bad, [], bad.join('\n'));
  });
}
