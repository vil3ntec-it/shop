'use strict';
/**
 * S109 (§۲۰.۱۲، §۲۰.۱۱، §۲۰.۴) — شمارشِ منطقه‌ای: فقط فروشگاهِ عمومیِ فعال به تفکیکِ ولایت/ولسوالی؛ حسابِ آزمایشی،
 * تکراری، تعلیق‌شده و دکانِ بی وضعیتِ عمومی در شمارشِ فعال/سفارش‌پذیر نمی‌آیند؛ «آنلاینِ همین لحظه» گفته نمی‌شود.
 * و ولایت/ولسوالیِ دکان در «نمایش در بازارچه» (فهرستِ مرجعِ ۳۴ ولایت، ولایتِ ناشناس ⇒ ۴۰۰).
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');
const market = require('../src/lib/market');

let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  const hash = await pw.hashPassword('Admin!12345');
  const t = now();
  for (const [u, role, at] of [['arshad', 'superadmin', t - 9000], ['modir', 'admin', t - 8000], ['mali', 'finance', t - 7000], ['binande', 'viewer', t - 6000]]) {
    await query(
      `INSERT INTO admins (id, username, name, password_hash, role, status, created_at) VALUES ($1,$2,$2,$3,$4,'active',$5)`,
      [newId('adm'), u, hash, role, at]
    );
  }
});
test.after(async () => { await h.stop(); });

const tokens = {};
async function tok(u) {
  if (!tokens[u]) tokens[u] = (await h.post('/api/admin/login', { username: u, password: 'Admin!12345' })).body.token;
  return tokens[u];
}

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: await tok('arshad') });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانی با ویترینِ روشن و یک کالای منتشرشده؛ `listing` همان بدنهٔ «نمایش در بازارچه» */
async function shop(label, listing, { storefront = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { ...u, token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'market.directory']) await setFlag(o.shopId, k, true);
  const put = await h.put('/api/shop/storefront', { slug: `r-${uniq()}`, enabled: storefront, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: o.token, headers: { 'X-App': 'shop', 'X-Device': `dev-${o.shopId}` },
    body: { device_id: `dev-${o.shopId}`, schema_version: 2, ops: [{
      op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: 'rice', type: 'insert',
      fields: { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' },
    }] },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  if (storefront) assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: o.token })).status, 200);
  const l = await h.put('/api/shop/market', { listed: true, category: 'grocery', ...listing }, { token: o.token });
  assert.equal(l.status, 200, JSON.stringify(l.body));
  return { ...o, listing: l.body.listing };
}

const regions = async (u = 'binande') => h.get('/api/admin/market/regions', { token: await tok(u) });
const census = async (shopId, body, u = 'modir') => h.put(`/api/admin/shops/${shopId}/census`, body, { token: await tok(u) });
const prov = (r, code) => r.body.provinces.find((p) => p.code === code);

test('S109: ولایت و ولسوالیِ دکان در «نمایش در بازارچه» — فهرستِ ۳۴ ولایت، املاهای دیگر، ولایتِ ناشناس ۴۰۰', async () => {
  const a = await shop('ولایت', { province: 'Herat', district: '  انجيل ' });
  assert.equal(a.listing.province, 'HER');
  assert.equal(a.listing.provinceName, 'هرات');
  assert.equal(a.listing.district, 'انجیل', 'ی ِ عربی ⇒ فارسی و فاصله‌ها یکدست');
  assert.equal(a.listing.provinces.length, 34);
  assert.equal(new Set(a.listing.provinces.map((p) => p.code)).size, 34);
  const bad = await h.put('/api/shop/market', { province: 'مزارشریف' }, { token: a.token });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_province', 'نام ِ شهر هرگز به نزدیک‌ترین ولایت حدس زده نمی‌شود');
  const badD = await h.put('/api/shop/market', { district: 42 }, { token: a.token });
  assert.equal(badD.body.error.code, 'bad_district');
  //  فقط فیلدِ آمده عوض می‌شود؛ تهی ⇒ «نگفته»
  const keep = await h.put('/api/shop/market', { area: 'سرکِ اول' }, { token: a.token });
  assert.equal(keep.body.listing.province, 'HER');
  assert.equal((await h.put('/api/shop/market', { province: 'قندهار' }, { token: a.token })).body.listing.province, 'KAN');
  const cleared = await h.put('/api/shop/market', { province: '', district: '' }, { token: a.token });
  assert.equal(cleared.body.listing.province, '');
  assert.equal(cleared.body.listing.provinceName, '');
});

test('S109: آزمایشی، تکراری، تعلیق‌شده و بی وضعیتِ عمومی در شمارش نمی‌آیند؛ سه وضعیتِ جدا، نه «آنلاین»', async () => {
  const base = await regions();
  assert.equal(base.status, 200, JSON.stringify(base.body));
  const h0 = prov(base, 'HER');
  const k0 = prov(base, 'KAB');

  const a = await shop('هرات‌۱', { province: 'هرات', district: 'انجیل' });
  const b = await shop('هرات‌۲', { province: 'HER', district: 'انجيل' });   //  همان ولسوالی با «ي» ِ عربی
  const closed = await h.post('/api/shop/storefront/presence', { eventId: `ev-${uniq()}`, mode: 'closed' }, { token: b.token });
  assert.equal(closed.status, 200, JSON.stringify(closed.body));
  const t = await shop('آزمایشی', { province: 'کابل', district: 'دهسبز' });
  const d = await shop('تکراری', { province: 'کابل', district: 'دهسبز' });
  const off = await shop('بی‌ویترین', { province: 'کابل' }, { storefront: false });
  const sus = await shop('تعلیقی', { province: 'کابل' });
  const unknown = await shop('بی‌ولایت', {});
  //  تعلیق با همان درِ واقعیِ مدیر (حالتِ «دسترسی بسته»)
  const hold = await h.post(`/api/admin/shops/${sus.shopId}/holds`, { kind: 'access_suspended', reason: 'آزمونِ تعلیق' }, { token: await tok('arshad') });
  assert.equal(hold.status, 201, JSON.stringify(hold.body));
  market.clearCache();

  //  پیش از نشان: آزمایشی و تکراری هنوز «عمومی‌اند» و شمرده می‌شوند
  const mid = await regions();
  assert.equal(prov(mid, 'KAB').registered, k0.registered + 2, JSON.stringify(prov(mid, 'KAB')));

  //  نشان با دلیل و سابقه؛ تکراری دکانِ اصل می‌خواهد؛ بی دلیل ۴۰۰؛ نقشِ فقط‌خواندن نمی‌نویسد
  assert.equal((await census(t.shopId, { kind: 'test' })).body.error.code, 'reason_required');
  assert.equal((await census(t.shopId, { kind: 'test', reason: 'حسابِ آزمونِ پشتیبانی' }, 'binande')).status, 403);
  assert.equal((await census(t.shopId, { kind: 'test', reason: 'حسابِ آزمونِ پشتیبانی' }, 'mali')).status, 403);
  assert.equal((await census(t.shopId, { kind: 'odd', reason: 'نامعتبر است' })).body.error.code, 'bad_kind');
  assert.equal((await census(d.shopId, { kind: 'duplicate', reason: 'همان صاحب، همان دکان' })).body.error.code, 'bad_duplicate_of');
  assert.equal((await census(d.shopId, { kind: 'duplicate', duplicateOf: d.shopId, reason: 'همان صاحب، همان دکان' })).body.error.code, 'bad_duplicate_of');
  const mt = await census(t.shopId, { kind: 'test', reason: 'حسابِ آزمونِ پشتیبانی' });
  assert.equal(mt.status, 200, JSON.stringify(mt.body));
  assert.equal(mt.body.mark.kind, 'test');
  const md = await census(d.shopId, { kind: 'duplicate', duplicateOf: a.shopId, reason: 'همان صاحب، همان دکان' });
  assert.equal(md.body.mark.duplicateOf, a.shopId);
  assert.equal((await h.get(`/api/admin/shops/${d.shopId}/census`, { token: await tok('binande') })).body.mark.kind, 'duplicate');
  const logs = await many(`SELECT detail FROM audit_logs WHERE action = 'admin.shop_census_mark' AND shop_id = ANY($1::text[])`, [[t.shopId, d.shopId]]);
  assert.equal(logs.length, 2, 'هر نشان در سابقه');

  const r = await regions();
  const her = prov(r, 'HER');
  const kab = prov(r, 'KAB');
  assert.equal(her.registered, h0.registered + 2);
  assert.equal(her.accepting, h0.accepting + 1);
  assert.equal(her.temporarilyClosed, h0.temporarilyClosed + 1, 'بستهٔ اعلام‌شده «موقتاً بسته» است، نه فعال');
  const injil = her.districts.find((x) => x.name === 'انجیل');
  assert.ok(injil && injil.registered >= 2, `«انجیل» و «انجيل» یک ولسوالی: ${JSON.stringify(her.districts)}`);
  assert.equal(kab.registered, k0.registered, 'آزمایشی، تکراری، بی‌ویترین و تعلیقی در کابل شمرده نمی‌شوند');
  assert.ok(r.body.excluded.test >= 1 && r.body.excluded.duplicate >= 1, JSON.stringify(r.body.excluded));
  assert.ok(r.body.excluded.suspended >= 1 && r.body.excluded.notPublic >= 1, JSON.stringify(r.body.excluded));
  assert.ok(r.body.unknownProvince.registered >= base.body.unknownProvince.registered + 1);
  assert.equal(r.body.provinces.length, 34);
  assert.equal(r.body.onlineNow, null, '«آنلاینِ همین لحظه» بی تعریفِ heartbeat گفته نمی‌شود');
  assert.deepEqual(r.body.statuses, ['registered', 'accepting', 'temporarilyClosed']);
  const sum = r.body.provinces.reduce((n, p) => n + p.registered, 0) + r.body.unknownProvince.registered;
  assert.equal(sum, r.body.totals.registered);
  //  هیچ دادهٔ شخصی: نه نامِ دکان، نه شناسه، نه شماره
  const text = JSON.stringify(r.body);
  for (const s of [a, b, t, d, off, sus, unknown]) assert.ok(!text.includes(s.shopId), 'شناسهٔ دکان در شمارش نیست');
  assert.ok(!text.includes('0700000001'));

  //  برداشتنِ نشان ⇒ دوباره شمرده می‌شود
  assert.equal((await census(t.shopId, { kind: 'none', reason: 'دکانِ واقعی شد' })).body.mark, null);
  assert.equal(prov(await regions(), 'KAB').registered, k0.registered + 1);
});

test('S109: درِ شمارش و نشان فقط با نشستِ مدیر', async () => {
  assert.equal((await h.get('/api/admin/market/regions')).status, 401);
  const u = await h.newUser('بی‌مدیر', 'shop');
  assert.equal((await h.get('/api/admin/market/regions', { token: u.accessToken })).status, 401);
  assert.equal((await h.put('/api/admin/shops/x1/census', { kind: 'test', reason: 'سه نویسه' })).status, 401);
  assert.equal((await h.get('/api/admin/shops/nope/census', { token: await tok('binande') })).status, 404);
});
