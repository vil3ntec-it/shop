'use strict';
/**
 * امنیتِ ویترین (ممیزیِ ۱۴۰۵/۰۸/۱۶) — سه چیزی که پیش از این رفع شکسته بود:
 *
 *   ۱) ⛔ سقفِ «هر دکان» ِ جست‌وجو با عکس (`storefront-visual-store.js`) با نشانیِ **خام** کلید می‌خورد،
 *      ولی `sf.open` نشانی را trim و کوچک می‌کند ⇒ `/ABC/`، `/abc%20/`، `/%09abc/` همان دکان‌اند با
 *      سطلِ تازه و سقفِ ۳۰۰ ِ دکان با چند IP بی‌اثر بود. حالا کلید = شناسهٔ دکان.
 *   ۲) ⛔ همان خطا در سقفِ «هر سفارش» ِ گزارشِ مشکل و نظر (`order-feedback-store.js`): کلید نشانیِ
 *      بهنجار + توکن.
 *   ۳) جعلِ نشانیِ ویترین: دکانی که نشانی‌اش را عوض می‌کند، نشانیِ پیشین (QR ِ چاپی، پیوندِ پیگیری،
 *      نشانک) همان دم به دستِ دکانِ دیگر می‌افتاد. با پرچمِ `market.slug_hold` (پیش‌فرض خاموش ⇒
 *      همان رفتارِ S3) سه نشانیِ تازه‌ترِ پیشینِ هر دکان یک سال برای همان دکان نگه داشته می‌شود.
 */
process.env.TRUST_PROXY = '1';
process.env.RATE_STORE_FEEDBACK_MAX = '2';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
let ipSeq = 0;
const nextIp = () => { ipSeq += 1; return `10.${(ipSeq >> 16) & 255}.${(ipSeq >> 8) & 255}.${ipSeq & 255}`; };

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
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function liveStore(label, flags = []) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const key of ['shop.orders', 'shop.storefront', ...flags]) await setFlag(o.shopId, key, true);
  o.slug = `sec-${uniq()}`;
  const put = await h.put('/api/shop/storefront', {
    slug: o.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001', acceptPickup: true,
  }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return o;
}

const setSlug = (o, slug) => h.put('/api/shop/storefront', { slug }, { token: o.token });

/** سه شکلِ دیگرِ همان نشانی که `sf.open` همه را به همان ویترین می‌برد */
const spellings = (slug) => [slug, slug.toUpperCase(), `${slug}%20`, `%09${slug.toUpperCase()}`];

/* ══════════════════════════════════════════════════════════════════
   ۱) سقفِ هر دکانِ جست‌وجو با عکس
   ══════════════════════════════════════════════════════════════════ */

test('⛔ جست‌وجو با عکس: سقفِ هر دکان با شکلِ دیگرِ نشانی (حروفِ بزرگ، فاصله) و IP ِ دیگر دور زده نمی‌شود', async () => {
  const vs = require('../src/lib/visual-search');
  const s = await liveStore('عکس', [vs.FLAG, vs.STORE_FLAG]);
  const forms = spellings(s.slug);
  const hit = (form) => h.raw('POST', `/api/store/${form}/visual-search`, Buffer.alloc(0), { headers: { 'X-Forwarded-For': nextIp() } });
  //  نخست: هر شکل واقعاً همان ویترین است (پاسخِ یکسان، نه ۴۰۴)
  const base = await hit(forms[0]);
  assert.notEqual(base.status, 404, JSON.stringify(base.body));
  for (const f of forms.slice(1)) {
    const r = await hit(f);
    assert.equal(r.status, base.status, `«${f}» همان ویترین نیست: ${JSON.stringify(r.body)}`);
  }
  //  تا ۳۰۰ (سقفِ دکان) با همهٔ شکل‌ها و هر بار از IP ِ تازه
  for (let i = forms.length; i < 300; i++) {
    const r = await hit(forms[i % forms.length]);
    assert.notEqual(r.status, 429, `درخواستِ ${i + 1} زودتر از سقف ۴۲۹ شد`);
  }
  const over = await hit(forms[0]);
  assert.equal(over.status, 429, `درخواستِ ۳۰۱ ِ همان دکان باید ۴۲۹ شود: ${over.status} ${JSON.stringify(over.body)}`);
  //  دکانِ دیگر سطلِ خودش را دارد
  const other = await liveStore('عکس-۲', [vs.FLAG, vs.STORE_FLAG]);
  assert.notEqual((await hit(other.slug)).status, 429);
});

/* ══════════════════════════════════════════════════════════════════
   ۲) سقفِ هر سفارشِ گزارشِ مشکل و نظر
   ══════════════════════════════════════════════════════════════════ */

const push = (token, ops, device = 'dev-sec') => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': device },
  body: { device_id: device, schema_version: 2, ops },
});

async function orderOf(s) {
  const p = await push(s.token, [{
    op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
    fields: { name: 'برنج', unit: 'کیلو', salePrice: 100, category: 'خوراکی' },
  }]);
  assert.equal(p.status, 200, JSON.stringify(p.body));
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  const phone = '0791113344';
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(st.status, 200, JSON.stringify(st.body));
  const ck = await h.post(`/api/store/${s.slug}/verify/check`, { email: mailOf(phone), code: st.body.devCode });
  assert.equal(ck.status, 200, JSON.stringify(ck.body));
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 1000 }],
  }, { headers: { 'Idempotency-Key': `sec-${uniq()}-key`, 'X-Store-Ticket': ck.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.order.token;
}

test('⛔ گزارشِ مشکل: سقفِ هر سفارش با شکلِ دیگرِ نشانی و IP ِ دیگر دور زده نمی‌شود', async () => {
  const s = await liveStore('گزارش', [require('../src/lib/order-issues').FLAG]);
  const token = await orderOf(s);
  const forms = spellings(s.slug);
  const report = (form) => h.post(`/api/store/${form}/orders/${token}/issues`, {}, { headers: { 'X-Forwarded-For': nextIp() } });
  const first = await report(forms[0]);
  assert.notEqual(first.status, 429, JSON.stringify(first.body));
  assert.notEqual(first.status, 404, 'شکلِ اصلی همان سفارش است');
  const second = await report(forms[1]);
  assert.equal(second.status, first.status, `«${forms[1]}» همان سفارش است: ${JSON.stringify(second.body)}`);
  for (const f of forms.slice(2)) {
    const r = await report(f);
    assert.equal(r.status, 429, `«${f}» از سقفِ ۲ ِ همین سفارش گذشت: ${r.status} ${JSON.stringify(r.body)}`);
  }
});

/* ══════════════════════════════════════════════════════════════════
   ۳) نگه‌داشتنِ نشانیِ پیشین (`market.slug_hold`)
   ══════════════════════════════════════════════════════════════════ */

test('نشانیِ پیشین: پرچمِ خاموش ⇒ همان S3 (دکانِ دیگر همان دم می‌گیرد)', async () => {
  const a = await liveStore('نگه-خاموش-۱');
  const b = await liveStore('نگه-خاموش-۲');
  const freed = a.slug;
  assert.equal((await setSlug(a, `sec-${uniq()}`)).status, 200);
  const r = await setSlug(b, freed);
  assert.equal(r.status, 200, JSON.stringify(r.body));
});

test('⛔ نشانیِ پیشین با پرچمِ market.slug_hold: یک سال برای همان دکان؛ فقط سه تای تازه‌تر؛ پس از آن آزاد', async () => {
  const a = await liveStore('نگه-۱');
  const b = await liveStore('نگه-۲');
  await setFlag(a.shopId, 'market.slug_hold', true);
  const first = a.slug;
  const renames = [`sec-${uniq()}`, `sec-${uniq()}`, `sec-${uniq()}`, `sec-${uniq()}`];
  assert.equal((await setSlug(a, renames[0])).status, 200);

  //  دکانِ دیگر نشانیِ تازه‌رهاشده را نمی‌گیرد — همان ۴۰۹ ِ «گرفته شده»، بی نوشتن
  const taken = await setSlug(b, first);
  assert.equal(taken.status, 409, JSON.stringify(taken.body));
  assert.equal(taken.body.error.code, 'slug_taken');
  assert.equal((await one('SELECT slug FROM storefronts WHERE shop_id=$1', [b.shopId])).slug, b.slug);
  assert.equal((await one('SELECT shop_id FROM storefront_slug_redirects WHERE old_slug=$1', [first])).shop_id, a.shopId);
  //  با حروفِ بزرگ یا فاصله هم نه (نشانی پیش از سنجش بهنجار می‌شود)
  const upper = await setSlug(b, ` ${first.toUpperCase()} `);
  assert.equal(upper.status, 409, JSON.stringify(upper.body));

  //  خودِ دکان آزادانه برمی‌گردد
  assert.equal((await setSlug(a, first)).status, 200);
  assert.equal((await setSlug(a, renames[0])).status, 200);

  //  ضدِ احتکار: فقط سه نشانیِ تازه‌ترِ پیشین نگه داشته می‌شوند
  for (const [i, next] of renames.slice(1).entries()) {
    await query('UPDATE storefront_slug_redirects SET created_at = created_at - $2 WHERE shop_id = $1', [a.shopId, (i + 1) * 1000]);
    assert.equal((await setSlug(a, next)).status, 200);
  }
  //  نشانی‌های پیشینِ «الف» از تازه به کهنه: renames[2]، renames[1]، renames[0]، first ⇒ first دیگر نگه داشته نیست
  const oldest = await setSlug(b, first);
  assert.equal(oldest.status, 200, `چهارمین نشانیِ پیشین آزاد است: ${JSON.stringify(oldest.body)}`);
  assert.equal((await setSlug(b, renames[0])).status, 409, 'سه نشانیِ تازه‌تر هنوز نگه داشته‌اند');

  //  پس از یک سال آزاد
  await query('UPDATE storefront_slug_redirects SET created_at = $2 WHERE old_slug = $1', [renames[0], now() - 366 * 24 * 60 * 60 * 1000]);
  const late = await setSlug(b, renames[0]);
  assert.equal(late.status, 200, JSON.stringify(late.body));
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM storefront_slug_redirects WHERE old_slug=$1', [renames[0]])).n, 0);
});
