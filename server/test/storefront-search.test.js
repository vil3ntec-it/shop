'use strict';
/**
 * SRCH-01 (§۳۸): «نامِ محصول با حروف/اعدادِ جایگزین‌شدهٔ موردِ پشتیبانی جست‌وجو می‌شود؛ نتایج درست
 * و قابلِ توضیح هستند» — جست‌وجوی ویترین (`GET /api/store/:slug/products?q=`).
 *
 * صندوق از پیش با هستهٔ `CatalogMatch`/`TextNormalizer` جست‌وجو می‌کند (POS-04، پشتِ `PosSearchPrefs`).
 * ویترین تا این‌جا فقط «ي/ك» را یکی می‌کرد، نه رقم را، و کلمه‌ها را به همان ترتیب می‌خواست. با پرچمِ
 * دکانِ `shop.storefront.search_core` همان قاعده‌های `text-core.js` (بردارهای مشترکِ
 * `core/test-vectors/text-normalize.json`) در SQL اجرا می‌شود. پس نتیجه با یک قاعدهٔ نوشته‌شده
 * توضیح داده می‌شود: «هر کلمهٔ پرس‌وجو، پس از کلیدِ جست‌وجو، جایی در کلیدِ مقایسهٔ نام هست».
 *
 *   ۱) کلیدِ SQL برای **همهٔ** ورودی‌های بردارهای مرجع دقیقاً همان `compareKey` ِ text-core است
 *      (روی PGlite و PostgreSQL، همان پایگاهی که آزمون رویش می‌دود).
 *   ۲) پرچمِ روشن: هر بردارِ `matches` (بی مترادف) روی ویترینِ واقعی همان پاسخ را می‌دهد، رقمِ فارسی
 *      و عربی با لاتین یکی است، و ترتیبِ کلمه مهم نیست.
 *   ۳) پرچمِ خاموش: همان رفتارِ پیشین (رقمِ فارسی با لاتین جور نمی‌شود).
 *   ۴) پرس‌وجوی پر از نویسهٔ ویژهٔ LIKE (`%`، `_`، `\`) چیزی بیش از متنِ خودش نمی‌گیرد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const sf = require('../src/lib/storefront');
const tc = require('../src/lib/text-core');

const V = JSON.parse(fs.readFileSync(path.join(__dirname, '..', '..', 'core', 'test-vectors', 'text-normalize.json'), 'utf8'));

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

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** ویترینِ روشن با این نام‌ها، همه منتشرشده و قیمت‌دار. */
async function storeWith(names) {
  const u = await h.newUser(`ویترینِ جست‌وجو ${uniq()}`, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${uniq()}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `q-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ویترینِ جست‌وجو', contactPhone: '0700000001' },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const ids = names.map((_, i) => `p${i}`);
  const p = await h.api('POST', '/api/sync/v1/push', {
    token: u.accessToken, headers: { 'X-App': 'shop', 'X-Device': 'dev-search' },
    body: {
      device_id: 'dev-search', schema_version: 2,
      ops: names.map((name, i) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: ids[i], type: 'insert',
        fields: { name, unit: 'دانه', salePrice: 10 + i } })),
    },
  });
  assert.equal(p.status, 200, JSON.stringify(p.body));
  for (const id of ids) {
    const r = await h.put(`/api/shop/storefront/products/${id}`, { published: true }, { token: u.accessToken });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  return { shopId, slug, byId: Object.fromEntries(ids.map((id, i) => [id, names[i]])) };
}

async function search(s, q) {
  const r = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent(q)}&limit=100`);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.items.map((p) => s.byId[p.id]).sort();
}

test('SRCH-01 ۱) کلیدِ SQL ِ ویترین برای همهٔ ورودی‌های بردارهای مرجع همان compareKey ِ text-core است', async () => {
  const inputs = new Set();
  for (const v of V.searchKey) inputs.add(v.in);
  for (const v of V.compareKey) inputs.add(v.in);
  for (const v of V.matches) { inputs.add(v.text); inputs.add(v.query); }
  //  لبه‌ها: همهٔ رقم‌ها، حروفِ بزرگِ لاتین، نامرئی‌ها و فاصله‌های یونیکد
  inputs.add('۰۱۲۳۴۵۶۷۸۹ ٠١٢٣٤٥٦٧٨٩ 0123456789');
  inputs.add('ABCXYZ abcxyz ÉÀ');
  inputs.add('‎شیر‏﻿­⁦۲⁩ لیتر　 ٫۵,۰');
  inputs.add('مُحَمَّد ـــ قرآن ٰ');
  let n = 0;
  for (const s of inputs) {
    const row = await one('SELECT translate($1::text, $2::text, $3::text) AS k', [s, sf.SEARCH_KEY.from, sf.SEARCH_KEY.to]);
    assert.equal(row.k, tc.compareKey(s), `کلیدِ «${JSON.stringify(s)}»`);
    n++;
  }
  assert.ok(n >= 30, `بردارها خوانده نشدند (${n})`);
  //  جدولِ translate: هر نویسهٔ «به» دقیقاً یک «از» دارد، بقیهٔ «از» حذف‌اند
  assert.equal(new Set(sf.SEARCH_KEY.from).size, sf.SEARCH_KEY.from.length, 'نویسهٔ تکراری در جدول');
});

test('SRCH-01 ۲) پرچمِ روشن: بردارهای matches روی ویترینِ واقعی، رقمِ فارسی/عربی = لاتین، ترتیبِ کلمه آزاد', async () => {
  const plain = V.matches.filter((v) => !v.synonyms);
  assert.ok(plain.length >= 5, 'بردارِ matches ِ بی مترادف کم است');
  const extra = ['شیر ۲ لیتری', 'رب گوجه‌فرنگی ٨٠٠ گرم', 'Coca Cola 1.5 لیتر', 'كيك شكلاتي', 'خانۀ چای'];
  const names = [...new Set([...plain.map((v) => v.text), ...extra])];
  const s = await storeWith(names);
  await setFlag(s.shopId, sf.SEARCH_FLAG, true);

  //  هر بردار: کالا با همان نام در نتیجه هست ⇔ out
  for (const v of plain) {
    const got = await search(s, v.query);
    assert.equal(got.includes(v.text), v.out, `«${v.text}» با «${v.query}» ⇒ ${v.out}`);
    //  و نتیجه دقیقاً همان کالاهایی است که text-core می‌گیرد — نه بیشتر، نه کمتر
    assert.deepEqual(got, names.filter((x) => tc.matches(x, v.query)).sort(), `پرس‌وجوی «${v.query}»`);
  }
  for (const q of ['2 لیتر', '۲ لیتر', '٢ لیتر', 'لیتری شیر', '800', 'گوجهفرنگی ۸۰۰', 'coca 1٫5', 'COLA', 'کیک شکلاتی', 'خانه']) {
    assert.deepEqual(await search(s, q), names.filter((x) => tc.matches(x, q)).sort(), `پرس‌وجوی «${q}»`);
  }
  assert.deepEqual(await search(s, '۲ لیتر'), ['شیر ۲ لیتری']);
  assert.deepEqual(await search(s, '800'), ['رب گوجه‌فرنگی ٨٠٠ گرم'], 'رقمِ عربی در نام با رقمِ لاتین در پرس‌وجو');
  assert.deepEqual(await search(s, 'لیتری شیر'), ['شیر ۲ لیتری'], 'ترتیبِ کلمه مهم نیست');
});

test('SRCH-01 ۳) پرچمِ خاموش: همان جست‌وجوی پیشینِ ویترین (رقمِ فارسی با لاتین جور نمی‌شود)', async () => {
  const s = await storeWith(['شیر ۲ لیتری', 'كيك شكلاتي']);
  assert.deepEqual(await search(s, '2 لیتر'), [], 'خاموش ⇒ رفتارِ پیشین');
  assert.deepEqual(await search(s, 'لیتری شیر'), [], 'خاموش ⇒ همان ترتیب لازم است');
  assert.deepEqual(await search(s, 'کیک'), ['كيك شكلاتي'], 'ي/ك پیش‌تر هم یکی بود');
  assert.deepEqual(await search(s, ''), ['شیر ۲ لیتری', 'كيك شكلاتي'].sort());
  //  همان پاسخِ بایت‌به‌بایت با پرچمِ صریحِ false
  const before = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent('شیر')}`);
  await setFlag(s.shopId, sf.SEARCH_FLAG, false);
  const after = await h.get(`/api/store/${s.slug}/products?q=${encodeURIComponent('شیر')}`);
  assert.deepEqual(after.body, before.body);
});

test('SRCH-01 ۴) نویسه‌های ویژهٔ LIKE در پرس‌وجو فقط خودشان‌اند', async () => {
  const s = await storeWith(['صابون ۱۰۰٪', 'پودر_ماشین', 'بسته a\\b', 'صابون عادی']);
  await setFlag(s.shopId, sf.SEARCH_FLAG, true);
  assert.deepEqual(await search(s, '%'), []);
  assert.deepEqual(await search(s, '_'), ['پودر_ماشین']);
  assert.deepEqual(await search(s, 'a\\b'), ['بسته a\\b']);
  assert.deepEqual(await search(s, 'صابون'), ['صابون ۱۰۰٪', 'صابون عادی'].sort());
});
