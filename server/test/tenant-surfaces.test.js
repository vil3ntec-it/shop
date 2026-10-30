'use strict';
/**
 * TEN-02 — «جداسازی در search، cache، فایل، export، WebSocket و job هم برقرار باشد» (§۲۹.۸).
 *
 * `shop-route-inventory.test.js` درهای شناسه‌دار را با جانشینیِ مستقیمِ شناسه می‌سنجد؛ این‌جا
 * سطح‌های دیگر، روی API و سوکتِ واقعی:
 *   • جست‌وجو — هر GET ِ بی‌شناسهٔ /api/shop با نام و بارکدِ کالای «الف» در هر پارامترِ
 *     جست‌وجو، با توکنِ «ب» هیچ ردی از «الف» نمی‌دهد
 *   • کش — هیچ پاسخِ درخواستِ دارای اعتبارنامه در کشِ مشترک نمی‌نشیند (`private` یا `no-store`،
 *     و `Vary: Authorization`)
 *   • WebSocket — «تغییر کرد» ِ همگام‌سازیِ «الف» فقط به دستگاه‌های «الف» (صاحب و شاگرد) می‌رسد؛
 *     نه به «ب»، نه به کاربرِ چنددکانیِ بسته به دکانِ دیگر، و پارامترِ دست‌کاری‌شدهٔ وصل شدن اتاق
 *     را عوض نمی‌کند؛ سوکتِ نشستِ باطل‌شده (عضوِ معلق) دیگر چیزی نمی‌گیرد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const { WebSocket } = require('ws');
const h = require('./helpers');
const inv = require('./route-inventory');
const live = require('../src/lib/sync-v1-live');
const { query, newId, now } = require('../src/db');

let base = '';
const FLAGS = ['shop.orders', 'shop.orders.refunds', 'shop.delivery', 'shop.storefront', 'shop.storefront.zones',
  'shop.chat', 'shop.catalog', 'shop.image_bank', 'shop.order_notify', 'market.trust'];
test.before(async () => {
  base = await h.start();
  live.attach(h.server());
  //  همهٔ درهای دکان باز، تا جست‌وجو و کش روی پاسخِ واقعی سنجیده شوند
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ten-admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  const admin = (await h.post('/api/admin/login', { username: 'ten-admin', password: 'Admin!12345' })).body.token;
  for (const key of FLAGS) {
    const r = await h.put('/api/admin/pump/live-config', { scope: 'all', key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
});
const opened = [];
//  سوکتِ بازِ آزمونِ شکست‌خورده فرآیند را زنده نگه نمی‌دارد
test.after(async () => { for (const ws of opened) { try { ws.terminate(); } catch { /* رفته */ } } await h.stop(); });

async function owner(label) {
  const u = await h.newUser(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}
async function loginOn(u, deviceId) {
  const r = await h.post('/api/auth/login', { identifier: u.email, password: u.password, device: { deviceId, name: deviceId, platform: 'test' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.accessToken;
}
let opN = 0;
const op = (rowId, fields, type = 'insert') => ({
  op_id: `01JTEN${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`, ts: Date.now(), table: 'products', row_id: rowId, type, fields,
});
const push = (token, device, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device }, body: { device_id: device, schema_version: 2, ops },
});
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** سوکتِ زنده با بلیتِ یک‌بارمصرف؛ پارامترهای دلخواه برای آزمونِ دست‌کاری */
async function socket(token, deviceId, extra = '') {
  const t = await h.api('POST', '/api/sync/v1/live/ticket', { token, body: {}, headers: { 'X-App': 'shop' } });
  assert.equal(t.status, 200, JSON.stringify(t.body));
  return new Promise((resolve, reject) => {
    const ws = new WebSocket(`${base.replace('http', 'ws')}/api/sync/v1/live?ticket=${encodeURIComponent(t.body.ticket)}&device_id=${deviceId}&app=shop${extra}`);
    opened.push(ws);
    ws.msgs = [];
    ws.closedWith = null;
    ws.on('message', (m) => ws.msgs.push(JSON.parse(String(m))));
    ws.on('close', (code) => { ws.closedWith = code; });
    ws.on('open', () => resolve(ws));
    ws.on('error', reject);
  });
}
const changes = (ws) => ws.msgs.filter((m) => m.event !== 'hello' && m.event !== 'pong');

test('جست‌وجو: هر GET ِ بی‌شناسهٔ /api/shop با نام، بارکد و شناسهٔ کالای «الف» برای «ب» همان پاسخِ یک نامِ ناموجود را دارد', async () => {
  const a = await owner('الفِ جست‌وجو');
  const b = await owner('بِ جست‌وجو');
  const NAME = 'زعفرانِ-رازِ-الف-۹۱۷';
  const CODE = '6260000917917';
  const PID = 'ten-a-p1';
  const CATEGORY = 'دستهٔ-رازِ-الف-۴۴۲';
  const r = await push(a.accessToken, 'dev-a-search', [op(PID, { name: NAME, unit: 'دانه', salePrice: 70, category: CATEGORY, barcodes: [CODE], barcode: CODE, active: true, createdAt: Date.now() })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  //  «الف» خودش پیدا می‌کند — آزمون بی‌اثر نیست
  const own = await h.get(`/api/shop/catalog/products?q=${encodeURIComponent(NAME)}`, { token: a.accessToken });
  assert.equal(own.status, 200, JSON.stringify(own.body));
  assert.ok(JSON.stringify(own.body).includes(CATEGORY), 'کالای «الف» در جست‌وجوی خودِ «الف»');

  /*
   *  مقایسهٔ دوتایی: همان درخواست یک بار با نام/بارکد/شناسهٔ واقعیِ «الف» و یک بار با همتای
   *  ناموجود (هم‌طول). اگر پاسخ پس از پوشاندنِ خودِ ورودی (بازتابِ پارامتر) و عددهای زمان
   *  فرق کند، دادهٔ «الف» به «ب» رسیده است. و هیچ‌جا دسته یا شناسهٔ دکانِ «الف» (که فرستاده نشده‌اند).
   */
  const probe = (name, code, pid) => ['q', 'search', 'query', 'term', 'name', 'barcode', 'code', 'productId', 'product_id', 'id']
    .map((k) => `${k}=${encodeURIComponent(k === 'barcode' || k === 'code' ? code : /id$/i.test(k) ? pid : name)}`).join('&');
  const mask = (body, name, code, pid) => JSON.stringify(body ?? '')
    .split(name).join('¤n').split(code).join('¤c').split(pid).join('¤p').replace(/\d{10,}/g, 'T');
  const gets = inv.routes('/shop').filter((x) => x.method === 'GET' && !x.path.includes(':'));
  assert.ok(gets.length >= 20, `فقط ${gets.length} GET`);
  const leaks = [];
  const unstable = [];
  for (const g of gets) {
    const call = (name, code, pid) => h.api('GET', `/api${g.path}?${probe(name, code, pid)}`, { token: b.accessToken, headers: { 'X-Shop-Id': a.shopId } });
    const G = ['زعفرانِ-هیچ‌جا-۰۰۰۰۰', '6260000000000', 'ten-z-p9'];
    const ghost = await call(...G);
    const real = await call(NAME, CODE, PID);
    const again = await call(...G);
    if (real.status >= 500) leaks.push(`${g.path} ⇒ ${real.status}`);
    const text = JSON.stringify(real.body ?? '');
    for (const s of [CATEGORY, a.shopId]) if (text.includes(s)) leaks.push(`${g.path} دادهٔ «الف» را داد (${s.slice(0, 12)})`);
    //  پاسخی که خودش دو بار پشتِ هم یکی نیست (ساعت، شمارنده) فقط با سنجهٔ مطلقِ بالا
    const g1 = `${ghost.status}${mask(ghost.body, ...G)}`;
    if (g1 !== `${again.status}${mask(again.body, ...G)}`) { unstable.push(g.path); continue; }
    if (`${real.status}${mask(real.body, NAME, CODE, PID)}` !== g1) {
      leaks.push(`${g.path} پاسخ به نامِ کالای «الف» با پاسخِ نامِ ناموجود فرق دارد: ${text.slice(0, 160)}`);
    }
  }
  assert.deepEqual(leaks, [], leaks.join('\n'));
  assert.ok(unstable.length <= gets.length / 4, `پاسخِ ناپایدارِ زیاد: ${unstable.join('، ')}`);
});

test('کش: پاسخِ درخواستِ دارای اعتبارنامه هرگز برای کشِ مشترک نیست (private/no-store و Vary)', async () => {
  const a = await owner('الفِ کش');
  const gets = [
    ...inv.routes('/shop').filter((x) => x.method === 'GET' && !x.path.includes(':')).map((x) => `/api${x.path}`),
    '/api/me', '/api/me/subscription', '/api/events', '/api/sync/v1/status',
  ];
  const bad = [];
  for (const url of gets) {
    const res = await fetch(`${base}${url}`, { headers: { Authorization: `Bearer ${a.accessToken}`, 'X-App': 'shop' } });
    if (res.status !== 200) continue;
    const cc = String(res.headers.get('cache-control') || '');
    if (!/\bprivate\b|\bno-store\b/.test(cc)) bad.push(`${url} ⇒ Cache-Control: «${cc}»`);
    if (/\bprivate\b/.test(cc) && !/authorization/i.test(String(res.headers.get('vary') || ''))) bad.push(`${url} ⇒ Vary بی Authorization`);
    if (/\bpublic\b|s-maxage/.test(cc)) bad.push(`${url} ⇒ عمومی: «${cc}»`);
  }
  assert.deepEqual(bad, [], bad.join('\n'));
  //  آزمون بی‌اثر نبود: درهای اصلی ۲۰۰ دادند
  const me = await fetch(`${base}/api/me`, { headers: { Authorization: `Bearer ${a.accessToken}` } });
  assert.equal(me.status, 200);
  assert.match(String(me.headers.get('cache-control')), /private/);
  //  نشانیِ عمومیِ بی اعتبارنامه همان رفتارِ امروز (این‌جا: بی سرآیندِ تازه)
  const health = await fetch(`${base}/api/health`);
  assert.ok(!/private/.test(String(health.headers.get('cache-control') || '')));
});

test('WebSocket: «تغییر کرد» ِ «الف» فقط به دستگاه‌های «الف»؛ «ب»، کاربرِ چنددکانیِ بسته به «ب» و پارامترِ دست‌کاری‌شده هیچ', async () => {
  const a = await owner('الفِ سوکت');
  const b = await owner('بِ سوکت');
  //  شاگردِ «الف»
  const code = (await h.get('/api/shop/staff-code', { token: a.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ سوکت');
  assert.ok((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status < 300);
  //  «م»: صاحبِ «ب»ِ دوم و شاگردِ «الف»، نشستِ بسته به دکانِ خودش
  const m = await owner('میمِ سوکت');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'staff','active',$4,$4)`,
    [newId('mem'), a.shopId, m.user.id, now()]
  );
  const mOwnToken = await loginOn(m, 'dev-m-own');
  assert.equal((await h.post('/api/shop/active', { shopId: m.shopId }, { token: mOwnToken })).status, 200);
  const mInAToken = await loginOn(m, 'dev-m-a');
  assert.equal((await h.post('/api/shop/active', { shopId: a.shopId }, { token: mInAToken })).status, 200);

  const aDesk = await socket(await loginOn(a, 'dev-a-desk'), 'dev-a-desk');
  const aStaff = await socket(staff.accessToken, 'dev-a-staff');
  const mInA = await socket(mInAToken, 'dev-m-a');
  const bDesk = await socket(b.accessToken, 'dev-b-desk', `&account_id=${a.shopId}&shop_id=${a.shopId}`);
  const mOwn = await socket(mOwnToken, 'dev-m-own', `&shop_id=${a.shopId}`);
  await sleep(200);

  const r = await push(a.accessToken, 'dev-a-phone', [op('ten-a-ws1', { name: 'کالای سوکت', unit: 'دانه', salePrice: 10, active: true, createdAt: Date.now() })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await sleep(400);

  for (const [who, ws] of [['کامپیوترِ «الف»', aDesk], ['شاگردِ «الف»', aStaff], ['«م» بسته به «الف»', mInA]]) {
    assert.ok(changes(ws).some((x) => x.event === 'changed'), `${who} خبر نگرفت: ${JSON.stringify(ws.msgs)}`);
  }
  for (const [who, ws] of [['«ب» با account_id/shop_id ِ «الف»', bDesk], ['«م» بسته به دکانِ خودش', mOwn]]) {
    assert.deepEqual(changes(ws), [], `${who} خبرِ «الف» را گرفت`);
  }
  //  بارکدِ گوشیِ «الف» هم فقط به «الف»
  aStaff.send(JSON.stringify({ type: 'relay', event: 'scan', code: '6269999000017', pair: '111222' }));
  await sleep(300);
  assert.ok(changes(aDesk).some((x) => x.event === 'scan' && x.code === '6269999000017'));
  assert.ok(!changes(bDesk).some((x) => x.event === 'scan') && !changes(mOwn).some((x) => x.event === 'scan'));
  for (const ws of [aDesk, aStaff, mInA, bDesk, mOwn]) ws.close();
});

test('WebSocket: عضوِ معلق‌شده (نشستِ باطل) سوکتش بسته می‌شود و دیگر خبرِ دکان را نمی‌گیرد', async () => {
  const a = await owner('الفِ تعلیق');
  const code = (await h.get('/api/shop/staff-code', { token: a.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ تعلیق');
  assert.ok((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status < 300);
  const members = (await h.get('/api/shop/members', { token: a.accessToken })).body.members;
  const mid = members.find((x) => x.userId === staff.user.id || x.user_id === staff.user.id).id;

  const gone = await socket(staff.accessToken, 'dev-gone');
  const desk = await socket(await loginOn(a, 'dev-a-desk2'), 'dev-a-desk2');
  await sleep(150);
  const sus = await h.patch(`/api/shop/members/${mid}`, { status: 'suspended' }, { token: a.accessToken });
  assert.equal(sus.status, 200, JSON.stringify(sus.body));

  //  همان تپشِ ۳۰ثانیه‌ای، این‌جا مستقیم
  const dropped = await live.dropRevoked(h.server().__syncLive);
  assert.ok(dropped >= 1, `سوکتِ نشستِ باطل بسته نشد (${dropped})`);
  const r = await push(a.accessToken, 'dev-a-phone2', [op('ten-a-ws2', { name: 'پس از تعلیق', unit: 'دانه', salePrice: 10, active: true, createdAt: Date.now() })]);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await sleep(400);
  assert.deepEqual(changes(gone), [], 'عضوِ معلق پس از تعلیق خبرِ دکان را گرفت');
  assert.equal(gone.closedWith, 4401);
  assert.ok(changes(desk).some((x) => x.event === 'changed'), 'صاحب هنوز می‌گیرد');
  //  وصلِ دوباره هم نمی‌شود
  const t = await h.api('POST', '/api/sync/v1/live/ticket', { token: staff.accessToken, body: {}, headers: { 'X-App': 'shop' } });
  assert.equal(t.status, 401);
  desk.close();
});
