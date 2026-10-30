'use strict';
/**
 * S7/S8 ِ docs/MARKETPLACE-CONTRACT-fa.md §۳ — جداسازیِ بارِ بازار از دکان (`lib/market-load.js`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) ⛔ پیش‌فرض خاموش: بی `market.load` (all) سطلِ نرخِ `general` همان پیشین است (بازار و
 *      `/api/shop` یکی)، سقفِ هم‌زمانی نیست، ضربان ۴۰۴ و پاسخ‌های ویترین `no-store`.
 *   ۲) S8 روشن: شلوغیِ `/api/market` و `/api/store` سطلِ `/api/shop` را نمی‌خورد؛ سقفِ هم‌زمانیِ
 *      پُرِ بازار ⇒ ۵۰۳ `market_busy` برای بازار، و همگام‌سازی و `/api/shop` همان دم ۲۰۰.
 *   ۳) S7 روشن: ویترین و برشِ بازار بی هیچ پرسشی از `sync_devices` (جدول کنار گذاشته می‌شود و
 *      پاسخ‌ها باز ۲۰۰ و «تماسِ تأییدشده» می‌دهند)؛ نوشتن هر ۳۰ ثانیه یک بار؛ ضربانِ دستگاه.
 *   ۴) ETag: پروفایل و نمای کالا `no-cache` و ۳۰۴ با `If-None-Match`.
 */
process.env.RATE_GENERAL_MAX = '40';
process.env.RATE_PUBLIC_MAX = '1000';
process.env.MARKET_PUBLIC_CONCURRENCY = '2';
process.env.MARKET_PUBLIC_QUEUE = '0';
process.env.MARKET_GATE_CACHE_MS = '0';
process.env.MARKET_ELIGIBLE_CACHE_MS = '0';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const ml = require('../src/lib/market-load');
const sfLib = require('../src/lib/storefront');

let admin = '';
let seq = 0;
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
  assert.ok(admin);
});
test.after(async () => { await h.stop(); });
test.beforeEach(() => { resetAll(); sfLib._forgetSeen(); });

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

const push = (token, ops, device) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});
const pull = (token, device) => h.api('GET', `/api/sync/v1/pull?device_id=${device}&since=0`, {
  token, headers: { 'X-App': 'shop' },
});

/** دکانِ روشن با ویترین، یک کالای منتشرشده و ردیفِ بازار. */
async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  await setFlag(o.shopId, 'shop.orders', true);
  await setFlag(o.shopId, 'shop.storefront', true);
  const slug = `l-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001',
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const device = `dev-${uniq()}`;
  const p = await push(o.token, [{
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'دانه', salePrice: 120, category: 'خوراکی' },
  }], device);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  const pub = await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token });
  assert.equal(pub.status, 200, JSON.stringify(pub.body));
  const m = await h.put('/api/shop/market', { listed: true, category: 'grocery', area: 'کارتهٔ سه' }, { token: o.token });
  assert.equal(m.status, 200, JSON.stringify(m.body));
  return { ...o, slug, device };
}

/** `sync_devices` را موقتاً کنار می‌گذارد: هر پرسشی از آن ⇒ خطا. */
async function withoutSyncDevices(fn) {
  await query('ALTER TABLE sync_devices RENAME TO sync_devices_hidden');
  try { return await fn(); } finally { await query('ALTER TABLE sync_devices_hidden RENAME TO sync_devices'); }
}

/* ══════════════════════════════════════════════════════════════════
   ۱) پیش‌فرض خاموش
   ══════════════════════════════════════════════════════════════════ */

test('بی market.load: همان رفتارِ پیشین — سطلِ مشترک، بی سقفِ هم‌زمانی، ضربان ۴۰۴، no-store', async () => {
  const s = await liveStore('خاموش');
  assert.equal(await ml.enabled(), false);
  resetAll();
  //  شلوغیِ بازار (۴۰ درخواست) همان سطلِ `general` ِ این IP را پر می‌کند ⇒ `/api/shop` هم ۴۲۹
  for (let i = 0; i < 40; i++) await h.get('/api/market');
  const shop = await h.get('/api/shop/storefront/config', { token: s.token });
  assert.equal(shop.status, 429, 'رفتارِ پیشین: سطلِ مشترک');
  resetAll();

  //  سقفِ هم‌زمانی بی‌اثر است
  assert.equal(await ml.gate.acquire(), true);
  assert.equal(await ml.gate.acquire(), true);
  assert.notEqual((await h.get('/api/market')).status, 503);
  ml.gate.release(); ml.gate.release();

  const hb = await h.post('/api/shop/storefront/heartbeat', {}, { token: s.token });
  assert.equal(hb.status, 404, JSON.stringify(hb.body));
  assert.equal(hb.body.error.code, 'heartbeat_disabled');
  const page = await h.get(`/api/store/${s.slug}`);
  assert.equal(page.status, 200);
  assert.equal(page.headers.get('cache-control'), 'no-store');

  //  خاموش ⇒ حضور هنوز از `sync_devices` خوانده می‌شود (کنار گذاشتنش ⇒ خطا)
  const broken = await withoutSyncDevices(() => h.get(`/api/store/${s.slug}`));
  assert.equal(broken.status, 500, 'خاموش: همان پرسشِ پیشین');
});

/* ══════════════════════════════════════════════════════════════════
   ۲) S8 — جداسازیِ سطل و سقفِ هم‌زمانی
   ══════════════════════════════════════════════════════════════════ */

test('market.load روشن: شلوغیِ بازار سطلِ /api/shop را نمی‌خورد', async () => {
  const s = await liveStore('سطل');
  await setFlag('all', 'market.load', true);
  try {
    resetAll();
    for (let i = 0; i < 60; i++) {
      const r = await h.get('/api/market');
      assert.ok([404, 429].includes(r.status), `${r.status}`);   //  ۴۲۹ از سطلِ خودِ بازار (`market-read`)
    }
    for (let i = 0; i < 60; i++) await h.get(`/api/store/${s.slug}/products/rice`);
    const shop = await h.get('/api/shop/storefront/config', { token: s.token });
    assert.equal(shop.status, 200, 'سطلِ دکان دست نخورده');
    const sync = await pull(s.token, s.device);
    assert.equal(sync.status, 200);
  } finally { await setFlag('all', 'market.load', false); }
});

test('market.load روشن: سقفِ هم‌زمانیِ پُرِ بازار ⇒ ۵۰۳ برای بازار؛ همگام‌سازی و فروش همان دم ۲۰۰', async () => {
  const s = await liveStore('هم‌زمانی');
  await setFlag('all', 'market.load', true);
  try {
    resetAll();
    //  دو درخواستِ کُندِ بازار در جریان (سقف = ۲، صف = ۰)
    assert.equal(await ml.gate.acquire(), true);
    assert.equal(await ml.gate.acquire(), true);
    try {
      for (const p of ['/api/market', `/api/store/${s.slug}`, '/api/v1/market/shops']) {
        const busy = await h.get(p);
        assert.equal(busy.status, 503, `${p} ${JSON.stringify(busy.body)}`);
        assert.equal(busy.body.error.code, 'market_busy');
        assert.equal(busy.headers.get('retry-after'), '1');
      }
      assert.equal((await pull(s.token, s.device)).status, 200, 'همگام‌سازی در سقفِ بازار نیست');
      const p = await push(s.token, [{
        op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'oil', type: 'insert',
        fields: { name: 'روغن', unit: 'دانه', salePrice: 300 },
      }], s.device);
      assert.equal(p.status, 200, JSON.stringify(p.body));
      assert.equal((await h.get('/api/shop/storefront/config', { token: s.token })).status, 200);
    } finally { ml.gate.release(); ml.gate.release(); }
    assert.equal(ml.gate.active, 0);
    const page = await h.get(`/api/store/${s.slug}`);
    assert.equal(page.status, 200, 'پس از آزاد شدن');
    assert.equal(ml.gate.active, 0, 'هر درخواست جایش را پس می‌دهد');
  } finally { await setFlag('all', 'market.load', false); }
});

test('دروازهٔ هم‌زمانی: صفِ کوتاه، مهلت و آزاد شدنِ منتظر', async () => {
  const g = ml.createGate({ max: 1, queue: 1, waitMs: 30 });
  assert.equal(await g.acquire(), true);
  const waiting = g.acquire();
  assert.equal(await g.acquire(), false, 'صف پُر ⇒ همان دم رد');
  g.release();
  assert.equal(await waiting, true, 'منتظر جای آزادشده را می‌گیرد');
  assert.equal(g.active, 1);
  assert.equal(await g.acquire(), false, 'مهلتِ انتظار تمام ⇒ رد');
  g.release();
  assert.equal(g.active, 0);
});

/* ══════════════════════════════════════════════════════════════════
   ۳) S7 — آخرین تماس روی ردیفِ ویترین
   ══════════════════════════════════════════════════════════════════ */

test('market.load روشن: ویترین و بازار بی هیچ پرسشی از sync_devices', async () => {
  const a = await liveStore('الف');
  const b = await liveStore('ب');
  const seen = await one('SELECT last_seen_at FROM storefronts WHERE shop_id = $1', [a.shopId]);
  assert.ok(Number(seen.last_seen_at) > 0, 'همگام‌سازی ستون را پر کرد');
  await setFlag('all', 'market.directory', true);
  await setFlag('all', 'market.load', true);
  try {
    const [page, shops] = await withoutSyncDevices(async () => [
      await h.get(`/api/store/${a.slug}`), await h.get('/api/market/shops'),
    ]);
    assert.equal(page.status, 200, JSON.stringify(page.body));
    assert.equal(page.body.store.status.connection, 'confirmed');
    assert.equal(page.body.store.status.lastConfirmedAt, Number(seen.last_seen_at));
    assert.equal(shops.status, 200, JSON.stringify(shops.body));
    const slugs = shops.body.shops.map((x) => x.slug);
    assert.ok(slugs.includes(a.slug) && slugs.includes(b.slug), slugs.join(','));

    //  دکانِ بی تماس (ستون خالی) ⇒ همان «بی تماس» ِ پیشین، نه خطا
    await query('UPDATE storefronts SET last_seen_at = NULL WHERE shop_id = $1', [b.shopId]);
    const cold = await h.get(`/api/store/${b.slug}`);
    assert.equal(cold.status, 200);
    assert.equal(cold.body.store.status.lastConfirmedAt, null, 'همان «بی تماس» ِ پیشین');
  } finally {
    await setFlag('all', 'market.load', false);
    await setFlag('all', 'market.directory', false);
  }
});

test('نوشتنِ آخرین تماس حداکثر هر ۳۰ ثانیه یک بار و هرگز عقب‌تر', async () => {
  const s = await liveStore('سقف');
  sfLib._forgetSeen();
  const t0 = Date.now() + 60_000;
  assert.equal(await sfLib.noteSeen(s.shopId, t0), t0);
  assert.equal(await sfLib.noteSeen(s.shopId, t0 + 10_000), null, 'زیرِ ۳۰ ثانیه ⇒ بی نوشتن');
  const at = async () => Number((await one('SELECT last_seen_at FROM storefronts WHERE shop_id = $1', [s.shopId])).last_seen_at);
  assert.equal(await at(), t0);
  assert.equal(await sfLib.noteSeen(s.shopId, t0 + 30_000), t0 + 30_000);
  assert.equal(await at(), t0 + 30_000);
  sfLib._forgetSeen();
  await sfLib.noteSeen(s.shopId, t0);   //  فرایندِ تازه با زمانِ کهنه‌تر
  assert.equal(await at(), t0 + 30_000, 'عقب نمی‌رود');
});

test('ضربانِ دستگاهِ باز: با market.load روشن تماس ثبت می‌شود، با سقفِ ۳۰ ثانیه', async () => {
  const s = await liveStore('ضربان');
  await query('UPDATE storefronts SET last_seen_at = NULL WHERE shop_id = $1', [s.shopId]);
  sfLib._forgetSeen();
  await setFlag('all', 'market.load', true);
  try {
    const first = await h.post('/api/shop/storefront/heartbeat', {}, { token: s.token });
    assert.equal(first.status, 200, JSON.stringify(first.body));
    assert.equal(first.body.recorded, true);
    assert.equal(first.body.nextInMs, 30_000);
    const row = await one('SELECT last_seen_at FROM storefronts WHERE shop_id = $1', [s.shopId]);
    assert.equal(Number(row.last_seen_at), first.body.serverTime);
    const again = await h.post('/api/shop/storefront/heartbeat', {}, { token: s.token });
    assert.equal(again.status, 200);
    assert.equal(again.body.recorded, false, 'زیرِ ۳۰ ثانیه ⇒ بی نوشتن');
    //  بی توکن ⇒ ۴۰۱؛ ویترینِ بی پرچم ⇒ همان ۴۰۳ ِ storefront_disabled
    assert.equal((await h.post('/api/shop/storefront/heartbeat', {})).status, 401);
    await setFlag(s.shopId, 'shop.storefront', false);
    const off = await h.post('/api/shop/storefront/heartbeat', {}, { token: s.token });
    assert.equal(off.status, 403);
    assert.equal(off.body.error.code, 'storefront_disabled');
  } finally { await setFlag('all', 'market.load', false); }
});

/* ══════════════════════════════════════════════════════════════════
   ۴) ETag
   ══════════════════════════════════════════════════════════════════ */

test('market.load روشن: پروفایل و نمای کالا no-cache و ۳۰۴ با ETag', async () => {
  const s = await liveStore('کش');
  await setFlag('all', 'market.load', true);
  try {
    for (const p of [`/api/store/${s.slug}`, `/api/store/${s.slug}/products/rice`]) {
      const r = await h.get(p);
      assert.equal(r.status, 200, JSON.stringify(r.body));
      assert.equal(r.headers.get('cache-control'), 'no-cache', p);
      let etag = r.headers.get('etag');
      assert.ok(etag, p);
      //  ⚠️ `fetch` ِ Node برای درخواستِ شرطی خودش `Cache-Control: no-cache` می‌گذارد (و Express آن را
      //  «تازه نیست» می‌خواند)؛ مرورگرِ واقعی در بازپرسی `max-age=0` می‌فرستد.
      const cond = (tag) => ({ headers: { 'If-None-Match': tag, 'Cache-Control': 'max-age=0' } });
      let again = await h.get(p, cond(etag));
      //  پروفایل ساعتِ محلیِ دکان را دارد ⇒ مرزِ دقیقه ETag ِ تازه می‌دهد؛ یک بار دیگر
      if (again.status === 200) { etag = again.headers.get('etag'); again = await h.get(p, cond(etag)); }
      assert.equal(again.status, 304, p);
    }
    //  فهرست و سفارش همان no-store می‌مانند
    assert.equal((await h.get(`/api/store/${s.slug}/products`)).headers.get('cache-control'), 'no-store');
  } finally { await setFlag('all', 'market.load', false); }
});
