'use strict';
/**
 * سربرگ و لوگوی فاکتورِ دکان — `/api/shop/receipt-profile` (فاکتورِ نسخهٔ ۲).
 *
 * هر عضو می‌خواند (دستگاه برای چاپِ بی اینترنت نگه می‌دارد)؛ فقط `settings.write`
 * (صاحب و مدیر) می‌نویسد. نوشتن فقط کلیدِ `receipt` را ادغام می‌کند و
 * `PUT /api/shop/settings` (جایگزینیِ کلِ سند) آن را نه می‌نویسد نه پاک می‌کند.
 * لوگو: نوع از محتوا، سقفِ حجم و ابعاد، بی فراداده، با ETag.
 */
process.env.RATE_LOGIN_EDGE_MAX = process.env.RATE_LOGIN_EDGE_MAX || '100000';
process.env.RATE_RECEIPT_LOGO_DAILY_MAX = '8';

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { one, query } = require('../src/db');
const profiles = require('../src/lib/receipt-profile');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

async function shopWithTeam(label) {
  const owner = await h.newUser(`صاحب ${label}`);
  const made = await h.post('/api/shop', { name: `دکان ${label}` }, { token: owner.accessToken });
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = await h.newUser(`شاگرد ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code }, { token: staff.accessToken })).status));
  const inv = await h.post('/api/shop/invite', { role: 'manager' }, { token: owner.accessToken });
  const manager = await h.newUser(`مدیر ${label}`);
  assert.ok([200, 201].includes((await h.post('/api/shop/join', { code: inv.body.code }, { token: manager.accessToken })).status));
  return { shopId: made.body.shop.id, owner: owner.accessToken, staff: staff.accessToken, manager: manager.accessToken };
}

const P = '/api/shop/receipt-profile';

test('هر عضو می‌خواند؛ فقط صاحب و مدیر می‌نویسند؛ پیش‌فرض‌ها همان فاکتورِ امروز', async () => {
  const t = await shopWithTeam('الف');
  const first = await h.get(P, { token: t.staff });
  assert.equal(first.status, 200);
  assert.deepEqual(first.body, { profile: { ...profiles.DEFAULTS }, rev: 0 });

  assert.equal((await h.put(P, { name: 'x' }, { token: t.staff })).status, 403);
  const byManager = await h.put(P, { name: '  دکانِ نمونه  ', footer: 'تشکر' }, { token: t.manager });
  assert.equal(byManager.status, 200, JSON.stringify(byManager.body));
  assert.equal(byManager.body.profile.name, 'دکانِ نمونه');
  const byOwner = await h.put(P, { address: 'کابل', qr: false, logoMode: 'line', digits: 'fa', symbol: 'sign' }, { token: t.owner });
  assert.equal(byOwner.status, 200);
  assert.deepEqual(byOwner.body.profile, {
    name: 'دکانِ نمونه', address: 'کابل', phone: '', footer: 'تشکر', qr: false,
    logoMode: 'line', digits: 'fa', symbol: 'sign', logo: null,
  });
  assert.ok(byOwner.body.rev > byManager.body.rev);
  assert.deepEqual((await h.get(P, { token: t.staff })).body, byOwner.body);
});

test('بدنهٔ نادرست ⇒ ۴۰۰ و هیچ تغییری', async () => {
  const t = await shopWithTeam('ب');
  for (const body of [{}, { qr: 'yes' }, { logoMode: 'fancy' }, { digits: 'roman' }, { symbol: '$' }, { logo: { hash: 'f'.repeat(64) } }]) {
    const r = await h.put(P, body, { token: t.owner });
    assert.equal(r.status, 400, JSON.stringify(body));
  }
  assert.equal((await h.get(P, { token: t.owner })).body.rev, 0);
  const long = await h.put(P, { name: 'ن'.repeat(500) }, { token: t.owner });
  assert.equal(long.body.profile.name.length, 80);
});

test('PUT /api/shop/settings کلیدِ receipt را نه می‌نویسد نه پاک می‌کند؛ سربرگ بقیهٔ تنظیمات را نگه می‌دارد', async () => {
  const t = await shopWithTeam('پ');
  await h.put(P, { name: 'سربرگ' }, { token: t.owner });
  const s = await h.put('/api/shop/settings', { settings: { mark: 1, receipt: { name: 'دست‌کاری' } } }, { token: t.owner });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  assert.equal((await h.get(P, { token: t.owner })).body.profile.name, 'سربرگ');
  await h.put(P, { phone: '0700' }, { token: t.owner });
  const all = (await h.get('/api/shop/settings', { token: t.owner })).body.settings;
  assert.equal(all.mark, 1);
  assert.equal(all.receipt.name, 'سربرگ');

  //  دکانی که تا امروز سربرگ نداشت: همان جایگزینیِ قدیم، بی receipt
  const u = await shopWithTeam('ت');
  await h.put('/api/shop/settings', { settings: { receipt: { name: 'x' }, a: 2 } }, { token: u.owner });
  assert.deepEqual((await h.get('/api/shop/settings', { token: u.owner })).body.settings, { a: 2 });
});

test('لوگو: فقط عکسِ سالم، بی فراداده، با سقف؛ ETag و ۳۰۴؛ حذف', async () => {
  const t = await shopWithTeam('ث');
  const L = `${P}/logo`;
  assert.equal((await h.get(L, { token: t.staff })).status, 404);
  assert.equal((await h.raw('PUT', L, F.png(40, 20), { token: t.staff })).status, 403);
  assert.equal((await h.raw('PUT', L, Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"></svg>'.repeat(3)), { token: t.owner })).status, 400);
  assert.equal((await h.raw('PUT', L, F.png(2000, 20), { token: t.owner })).status, 400);
  assert.equal((await h.raw('PUT', L, Buffer.alloc(profiles.LOGO_MAX_BYTES + 4096, 1), { token: t.owner })).status, 413);
  assert.equal((await h.put(L, { data: 'x' }, { token: t.owner })).status, 400);

  const src = F.png(120, 60);
  assert.ok(src.includes(Buffer.from('GPS')));
  const up = await h.raw('PUT', L, src, { token: t.owner });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  const logo = up.body.profile.logo;
  assert.deepEqual({ mime: logo.mime, width: logo.width, height: logo.height }, { mime: 'image/png', width: 120, height: 60 });

  const got = await h.download(L, { token: t.staff });
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/png');
  assert.equal(got.headers.get('etag'), `"${logo.hash}"`);
  assert.ok(!got.buffer.includes(Buffer.from('GPS')), 'فراداده برداشته شد');
  const again = await fetch(`${h.base()}${L}`, { headers: { Authorization: `Bearer ${t.staff}`, 'If-None-Match': `"${logo.hash}"` } });
  assert.equal(again.status, 304);

  const audit = await one("SELECT action FROM audit_logs WHERE shop_id=$1 AND action='receipt.logo'", [t.shopId]);
  assert.ok(audit, 'بارگذاریِ لوگو در سابقهٔ عملیات');

  assert.equal((await h.del(L, { token: t.staff })).status, 403);
  const gone = await h.del(L, { token: t.manager });
  assert.equal(gone.status, 200);
  assert.equal(gone.body.profile.logo, null);
  assert.equal((await h.get(L, { token: t.owner })).status, 404);
});

const blobExists = async (hash) => !!(await one('SELECT 1 AS x FROM image_blobs WHERE hash=$1', [hash]));

test('لوگوی جایگزین‌شده و حذف‌شده از دیسک پاک می‌شود؛ لوگوی مشترک با دکانِ دیگر می‌ماند', async () => {
  const t = await shopWithTeam('ج');
  const u = await shopWithTeam('چ');
  const L = `${P}/logo`;
  const a = (await h.raw('PUT', L, F.png(30, 30, { seed: 11 }), { token: t.owner })).body.profile.logo.hash;
  const b = (await h.raw('PUT', L, F.png(30, 30, { seed: 12 }), { token: t.owner })).body.profile.logo.hash;
  assert.notEqual(a, b);
  assert.equal(await blobExists(a), false, 'لوگوی پیشین پاک شد');
  assert.equal(await blobExists(b), true);
  //  همان بایت‌ها دوباره ⇒ همان لوگو، چیزی پاک نمی‌شود
  assert.equal((await h.raw('PUT', L, F.png(30, 30, { seed: 12 }), { token: t.owner })).body.profile.logo.hash, b);
  assert.equal(await blobExists(b), true);

  //  دکانِ دیگر همان تصویر را دارد ⇒ با عوض شدنِ لوگوی این دکان نمی‌رود
  assert.equal((await h.raw('PUT', L, F.png(30, 30, { seed: 12 }), { token: u.owner })).body.profile.logo.hash, b);
  await h.del(L, { token: t.owner });
  assert.equal(await blobExists(b), true, 'دکانِ دیگر هنوز به آن اشاره دارد');
  assert.equal((await h.download(L, { token: u.staff })).status, 200);
  await h.del(L, { token: u.owner });
  assert.equal(await blobExists(b), false, 'دیگر کسی اشاره ندارد');
});

test('سقفِ شبانه‌روزیِ بارگذاریِ لوگو به‌ازای دکان ⇒ ۴۲۹؛ دکانِ دیگر آزاد', async () => {
  const t = await shopWithTeam('ح');
  const u = await shopWithTeam('خ');
  const L = `${P}/logo`;
  const hashes = [];
  for (let i = 0; i < 8; i++) {
    const r = await h.raw('PUT', L, F.png(20, 20, { seed: 100 + i }), { token: i % 2 ? t.manager : t.owner });
    assert.equal(r.status, 200, `بارِ ${i + 1}`);
    hashes.push(r.body.profile.logo.hash);
  }
  const ninth = await h.raw('PUT', L, F.png(20, 20, { seed: 200 }), { token: t.owner });
  assert.equal(ninth.status, 429);
  assert.equal((await h.raw('PUT', L, F.png(20, 20, { seed: 201 }), { token: u.owner })).status, 200);
  //  از هشت تصویرِ پیاپیِ این دکان فقط آخری روی دیسک است
  assert.equal(new Set(hashes).size, 8);
  const { rows } = await query('SELECT hash FROM image_blobs WHERE hash = ANY($1::text[])', [hashes]);
  assert.deepEqual(rows.map((r) => r.hash), [hashes[7]]);
});

test('لوگوی JPEG ِ عکس‌گرفته: چرخش می‌ماند (در فایل و سربرگ)، بقیهٔ فراداده نه', async () => {
  const bytes = require('../src/lib/image-bytes');
  const t = await shopWithTeam('د');
  const src = F.jpeg(40, 20, { orientation: 6 });
  assert.ok(src.includes(Buffer.from('34.5,69.1')));
  const up = await h.raw('PUT', `${P}/logo`, src, { token: t.owner });
  assert.equal(up.status, 200, JSON.stringify(up.body));
  assert.equal(up.body.profile.logo.orientation, 6);
  const got = await h.download(`${P}/logo`, { token: t.owner });
  assert.equal(bytes.inspect(got.buffer).orientation, 6);
  assert.ok(!got.buffer.includes(Buffer.from('34.5,69.1')), 'یادداشت و جا برداشته شد');
  //  PNG چرخش ندارد
  const png = await h.raw('PUT', `${P}/logo`, F.png(40, 20), { token: t.owner });
  assert.equal(png.body.profile.logo.orientation, 1);
});

test('پرچمِ shop.receipt_v2: پیش‌فرض خاموش؛ روشن برای همین دکان یا all', async () => {
  const t = await shopWithTeam('ذ');
  const u = await shopWithTeam('ر');
  const C = `${P}/config`;
  assert.deepEqual((await h.get(C, { token: t.staff })).body, { enabled: false });
  await query("INSERT INTO live_config (scope, key, value, updated_at) VALUES ($1,'shop.receipt_v2','true',1)", [t.shopId]);
  assert.deepEqual((await h.get(C, { token: t.staff })).body, { enabled: true });
  assert.deepEqual((await h.get(C, { token: u.owner })).body, { enabled: false });
});
