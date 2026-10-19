'use strict';
/**
 * فهرستِ کاملِ مسیرهای خصوصیِ دکان (`/api/shop/*`) — بندِ ۲۹.۲ قاعدهٔ ۲ و ۱۱،
 * ۲۹.۴ و آزمون‌های TEN-01 / AUTH-09.
 *
 * مسیرها از **خودِ سورس** خوانده می‌شوند، نه از فهرستِ دستی: سوار شدن‌ها از
 * `src/app.js` (‎`api.use('/shop…', require('./routes/…'))`‎) و روترهای تودرتو از
 * `router.use(require('./…'))`. پس مسیرِ تازه‌ای که فردا اضافه شود — یا فایلِ
 * تازه‌ای که زیرِ `/shop` سوار شود — بی هیچ کارِ دستی همین‌جا سنجیده می‌شود.
 *
 * برای هر مسیر:
 *   ۱) بی توکن ⇒ ۴۰۱
 *   ۲) با توکنِ بخشِ پمپ (همان آدم) ⇒ ۴۰۱ — توکنِ بخشِ دیگر این‌جا وجود ندارد
 *   ۳) با توکنِ صاحبِ دکانِ «ب» و شناسه‌های واقعیِ دکانِ «الف» در نشانی و بدنه ⇒
 *      هیچ پاسخی شناسه یا دادهٔ «الف» را برنمی‌گرداند و هیچ خطای ۵۰۰ی نیست
 *   ۴) در پایان، همهٔ ردیف‌های «الف» در **هر** جدولی که ستونِ `shop_id` دارد
 *      بایت‌به‌بایت همان پیش از آزمون‌اند — توکنِ «ب» چیزی از «الف» عوض نکرد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const nodePath = require('node:path');
const h = require('./helpers');
const { many, one } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

const SRC = nodePath.join(__dirname, '..', 'src');
const ROUTES = nodePath.join(SRC, 'routes');

/** `/shop…` ← فایل، از `app.js`؛ روترِ تودرتو همان پیشوندِ پدر را می‌گیرد */
function shopMounts() {
  const app = fs.readFileSync(nodePath.join(SRC, 'app.js'), 'utf8');
  const out = [];
  for (const m of app.matchAll(/api\.use\(\s*'(\/shop(?:\/[^']*)?)'\s*,\s*require\('\.\/routes\/([\w-]+)'\)\s*\)/g)) {
    out.push({ prefix: m[1], file: `${m[2]}.js` });
  }
  for (let i = 0; i < out.length; i++) {
    const src = fs.readFileSync(nodePath.join(ROUTES, out[i].file), 'utf8');
    for (const n of src.matchAll(/router\.use\(\s*require\('\.\/([\w-]+)'\)\s*\)/g)) {
      if (!out.some((o) => o.file === `${n[1]}.js` && o.prefix === out[i].prefix)) out.push({ prefix: out[i].prefix, file: `${n[1]}.js` });
    }
  }
  return out;
}

function shopRoutes() {
  const re = /router\.(get|post|put|patch|delete)\(\s*(\[[^\]]*\]|[`'"][^`'"]+[`'"])\s*,/g;
  const out = [];
  const seen = new Set();
  for (const { prefix, file } of shopMounts()) {
    const src = fs.readFileSync(nodePath.join(ROUTES, file), 'utf8');
    for (const m of src.matchAll(re)) {
      const paths = [...m[2].matchAll(/[`'"]([^`'"]+)[`'"]/g)].map((x) => x[1]);
      for (const p of paths) {
        const path = prefix + (p === '/' ? '' : p);
        const key = `${m[1].toUpperCase()} ${path}`;
        if (seen.has(key)) continue;
        seen.add(key);
        out.push({ method: m[1].toUpperCase(), path, file });
      }
    }
  }
  return out;
}

async function owner(label) {
  const u = await h.newUser(label);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, shopId: made.body.shop.id };
}

/** اثرِ کاملِ یک دکان: همهٔ ردیف‌هایش در هر جدولی که `shop_id` دارد */
async function footprint(shopId) {
  const tables = await many(
    `SELECT DISTINCT table_name FROM information_schema.columns
      WHERE table_schema='public' AND column_name='shop_id' ORDER BY table_name`
  );
  const out = {};
  for (const { table_name: t } of tables) {
    const r = await one(
      `SELECT COUNT(*)::int AS n, COALESCE(md5(string_agg(x::text, '|' ORDER BY x::text)), '') AS h
         FROM "${t}" x WHERE x.shop_id = $1`, [shopId]
    );
    out[t] = `${r.n}:${r.h}`;
  }
  return out;
}

test('هر مسیرِ خصوصیِ /api/shop: بی توکن و با توکنِ پمپ ۴۰۱؛ توکنِ دکانِ دیگر هیچ دادهٔ این دکان را نمی‌بیند و عوض نمی‌کند', async () => {
  const routes = shopRoutes();
  const files = new Set(routes.map((r) => r.file));
  for (const f of ['shop.js', 'shop-staff.js', 'orders.js', 'storefront-admin.js', 'delivery.js', 'shop-chat.js', 'sync.js']) {
    assert.ok(files.has(f), `routes/${f} در فهرستِ سوارشده‌ها نیست`);
  }
  assert.ok(routes.length > 60, `فقط ${routes.length} مسیر پیدا شد`);

  //  دکانِ «الف» با دادهٔ واقعی: تنظیمات، کارمند، کدِ کارمند
  const a = await owner('الف');
  const MARK = 'رازِ-دکانِ-الف-۷۳۱';
  assert.equal((await h.put('/api/shop/settings', { settings: { mark: MARK } }, { token: a.accessToken })).status, 200);
  const code = (await h.get('/api/shop/staff-code', { token: a.accessToken })).body.code;
  const staff = await h.newUser('شاگردِ الف');
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const members = (await h.get('/api/shop/members', { token: a.accessToken })).body.members;
  const memberId = members.find((m) => m.role === 'staff').id;
  const inv = await h.post('/api/shop/invite', { role: 'staff' }, { token: a.accessToken });
  assert.equal(inv.status, 201, JSON.stringify(inv.body));
  const codeId = (await h.get('/api/shop/staff-codes', { token: a.accessToken })).body.codes[0]?.id;
  assert.ok(codeId, 'کدِ واقعیِ دکانِ «الف» برای نشانی');

  const b = await owner('ب');
  const pump = await h.signIn(b, 'pump');
  const secrets = [a.shopId, MARK, memberId, codeId, inv.body.code].filter(Boolean);

  const concrete = (p) => p
    .replace(/\/members\/:id\b/, `/members/${memberId}`)
    .replace(/\/staff-codes\/:id\b/, `/staff-codes/${codeId}`)
    .replace(/:[A-Za-z_]+/g, 'x1');

  const before = await footprint(a.shopId);
  const failures = [];
  for (const r of routes) {
    const key = `${r.method} ${r.path}`;
    const url = `/api${concrete(r.path)}`;
    const body = r.method === 'GET' || r.method === 'DELETE' ? null : { shopId: a.shopId, shop_id: a.shopId, id: memberId };

    const anon = await h.api(r.method, url, { body });
    if (anon.status !== 401) failures.push(`${key} بی‌توکن ⇒ ${anon.status}`);

    const other = await h.api(r.method, url, { body, token: pump.accessToken });
    if (other.status !== 401) failures.push(`${key} با توکنِ پمپ ⇒ ${other.status}`);

    const cross = await h.api(r.method, url, {
      body, token: b.accessToken, headers: { 'X-Shop-Id': a.shopId },
    });
    if (cross.status >= 500) failures.push(`${key} با توکنِ دکانِ «ب» ⇒ ${cross.status}`);
    const text = JSON.stringify(cross.body ?? '');
    for (const s of secrets) if (text.includes(s)) failures.push(`${key} با توکنِ دکانِ «ب» دادهٔ «الف» را برگرداند (${s.slice(0, 8)}…)`);
  }
  assert.deepEqual(failures, [], failures.join('\n'));

  const after = await footprint(a.shopId);
  assert.deepEqual(after, before, 'توکنِ دکانِ «ب» هیچ ردیفی از دکانِ «الف» را عوض نکرد');
  //  و خودِ «الف» هنوز به همه‌چیزش می‌رسد — آزمون بی‌اثر نبود
  const mine = await h.get('/api/shop/settings', { token: a.accessToken });
  assert.equal(mine.status, 200);
  assert.ok(JSON.stringify(mine.body).includes(MARK));
});
