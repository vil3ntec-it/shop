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

const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { one } = require('../src/db');
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
