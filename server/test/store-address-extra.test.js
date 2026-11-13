'use strict';
/**
 * CRM-09 (§۴.۴) — دفترِ نشانیِ حسابِ مشتری (ویترین و بازارچه) همان کلیدهای کمکیِ فرمِ ثبت را نگه می‌دارد:
 * ناحیه، رنگِ دروازه، ولایت، گذر یا کوچه، منزل و طبقه، شمارهٔ دوم و Plus Code (مهاجرتِ ۴۹۰).
 *
 *   ۱) همان سنجش و سقفِ `orders.parseAddress` ِ ثبت (یک قاعده): شمارهٔ دومِ نادرست ⇒ ۴۰۰ `bad_phone2`،
 *      Plus Code ِ نادرست ⇒ ۴۰۰ `bad_plus_code` و هیچ ردیفی؛ درست ⇒ یکدست (شمارهٔ بین‌المللی، Plus Code ِ بزرگ).
 *   ۲) ⛔ برنامهٔ پیشین (برنامهٔ خرید: `AddressInput` فقط نام/متن/نشانه/پین) با `PUT` این کلیدها را پاک نمی‌کند؛
 *      کلیدِ فرستاده‌شده (حتی خالی) جایگزین می‌شود.
 *   ۳) کلیدِ دیگر (از جمله کدِ پستی، LOC-10) دور ریخته می‌شود؛ هنوز فقط پین/نشانه/متن نشانی را می‌سازند.
 *   ۴) فقط مالِ خودِ مشتری؛ پاک شدنِ حساب همه را می‌برد (همان cascade).
 *   ۵) rollback ِ ۴۹۰ ستون‌ها و ردیفِ خودش را برمی‌دارد و نشانی‌های پیشین می‌مانند.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '1000';
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const mailOf = (p) => `c${String(p).replace(/\D/g, '').slice(-9)}@test.local`;   //  «فقط ایمیل»: بلیتِ مشتری با ایمیل (شمارهٔ تماس جدا)

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
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

async function liveStore(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await setFlag(shopId, 'shop.orders', true);
  await setFlag(shopId, 'shop.storefront', true);
  const slug = `ax-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, deliveryFeeMinor: 0, minOrderMinor: 0 },
    { token: u.accessToken });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { shopId, slug };
}

async function sessionFor(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { email: mailOf(phone) });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { email: mailOf(phone), code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  const a = await h.post(`/api/store/${slug}/account`, {}, { headers: { 'X-Store-Ticket': c.body.ticket } });
  assert.ok(a.body.session, JSON.stringify(a.body));
  return { headers: { 'X-Store-Session': a.body.session } };
}

const FULL = {
  label: 'خانه', text: 'کارتهٔ ۴، سرکِ دوم', landmark: 'روبه‌روی نانوایی', lat: 34.5553, lng: 69.2075,
  area: ' کارتهٔ چهار ', gateColor: 'سبز', province: 'کابل', street: 'کوچهٔ گل‌ها', house: 'منزلِ ۳، طبقهٔ دوم',
  phone2: '0792 223 344', plusCode: '8fvc9g8f+6w کابل',
};
const EXTRA = {
  area: 'کارتهٔ چهار', gateColor: 'سبز', province: 'کابل', street: 'کوچهٔ گل‌ها', house: 'منزلِ ۳، طبقهٔ دوم',
  phone2: '+93792223344', plusCode: '8FVC9G8F+6W کابل',
};
const pick = (a) => Object.fromEntries(Object.keys(EXTRA).map((k) => [k, a[k]]));

test('CRM-09: نشانیِ نام‌دار ناحیه، دروازه، ولایت، گذر، منزل/طبقه، شمارهٔ دوم و Plus Code را نگه می‌دارد (یکدست)', async () => {
  const s = await liveStore('نشانیِ کامل');
  const A = await sessionFor(s.slug, '0791112201');
  const url = `/api/store/${s.slug}/account/addresses`;
  const r = await h.post(url, FULL, A);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.deepEqual(pick(r.body.address), EXTRA);
  const list = await h.get(url, A);
  assert.deepEqual(pick(list.body.addresses[0]), EXTRA, 'خواندنِ دوباره همان');
  //  همان در «حسابِ من» ِ بازارچه (یک دفترِ نشانی برای همان شماره)
  const row = await one('SELECT area, gate_color, province, street, house, phone2, plus_code FROM store_customer_addresses WHERE id=$1', [r.body.address.id]);
  assert.deepEqual(row, { area: EXTRA.area, gate_color: EXTRA.gateColor, province: EXTRA.province, street: EXTRA.street,
    house: EXTRA.house, phone2: EXTRA.phone2, plus_code: EXTRA.plusCode });
  //  بی کلیدهای کمکی ⇒ خالی، نه null (شکلِ ثابتِ پاسخ)
  const bare = await h.post(url, { label: 'دکان', landmark: 'چوکِ گل‌فروشی' }, A);
  assert.equal(bare.status, 201, JSON.stringify(bare.body));
  assert.deepEqual(pick(bare.body.address), Object.fromEntries(Object.keys(EXTRA).map((k) => [k, ''])));
});

test('CRM-09: شمارهٔ دوم یا Plus Code ِ نادرست ⇒ ۴۰۰ با کدِ همان ثبت و هیچ ردیف؛ کلیدهای کمکی جای پین/نشانه را نمی‌گیرند', async () => {
  const s = await liveStore('نشانیِ نادرست');
  const A = await sessionFor(s.slug, '0791112202');
  const url = `/api/store/${s.slug}/account/addresses`;
  const count = async () => (await one(
    `SELECT COUNT(*)::int AS n FROM store_customer_addresses a JOIN store_customers c ON c.id=a.customer_id WHERE c.phone='+93791112202'`)).n;
  const p2 = await h.post(url, { ...FULL, phone2: '12' }, A);
  assert.equal(p2.status, 400);
  assert.equal(p2.body.error.code, 'bad_phone2');
  const pc = await h.post(url, { ...FULL, plusCode: 'XYZ' }, A);
  assert.equal(pc.status, 400);
  assert.equal(pc.body.error.code, 'bad_plus_code');
  const only = await h.post(url, { label: 'فقط کمکی', area: 'خیرخانه', gateColor: 'آبی', house: 'طبقهٔ اول' }, A);
  assert.equal(only.status, 400, 'ناحیه/دروازه/منزل نشانی نیستند');
  assert.equal(only.body.error.code, 'bad_address');
  assert.equal(await count(), 0, 'هیچ نشانیِ نیمه‌کاره‌ای ننشست');
  //  کلیدِ ناشناخته (کدِ پستی و …) دور ریخته می‌شود (LOC-10)
  const z = await h.post(url, { label: 'خانه', landmark: 'مسجد', postalCode: '1001', zip: '1001', note: 'x' }, A);
  assert.equal(z.status, 201, JSON.stringify(z.body));
  assert.ok(!('postalCode' in z.body.address) && !('zip' in z.body.address) && !('note' in z.body.address));
  //  سقفِ همان ثبت: ناحیه ۸۰، دروازه ۴۰، ولایت ۶۰، گذر ۱۲۰، منزل ۶۰ (بریده، نه ۴۰۰)
  const long = await h.post(url, { label: 'بلند', landmark: 'مسجد', area: 'ا'.repeat(200), gateColor: 'ب'.repeat(200),
    province: 'پ'.repeat(200), street: 'ت'.repeat(200), house: 'ث'.repeat(200) }, A);
  assert.equal(long.status, 201, JSON.stringify(long.body));
  assert.deepEqual([long.body.address.area.length, long.body.address.gateColor.length, long.body.address.province.length,
    long.body.address.street.length, long.body.address.house.length], [80, 40, 60, 120, 60]);
});

test('CRM-09: PUT ِ برنامهٔ پیشین (بی کلیدهای کمکی) آن‌ها را پاک نمی‌کند؛ کلیدِ فرستاده (حتی خالی) جایگزین می‌شود', async () => {
  const s = await liveStore('نشانیِ برنامهٔ پیشین');
  const A = await sessionFor(s.slug, '0791112203');
  const B = await sessionFor(s.slug, '0791112204');
  const url = `/api/store/${s.slug}/account/addresses`;
  const id = (await h.post(url, FULL, A)).body.address.id;
  //  همان بدنهٔ `AddressInput` ِ برنامهٔ خرید (core/shopper/ShopperAddress.kt)
  const old = await h.put(`${url}/${id}`, { label: 'خانهٔ نو', text: 'کارتهٔ ۴', landmark: 'نانوایی', lat: 34.5, lng: 69.2 }, A);
  assert.equal(old.status, 200, JSON.stringify(old.body));
  assert.equal(old.body.address.label, 'خانهٔ نو');
  assert.deepEqual(pick(old.body.address), EXTRA, 'کلیدهای کمکی ماندند');
  const cleared = await h.put(`${url}/${id}`, { label: 'خانهٔ نو', landmark: 'نانوایی', gateColor: '', phone2: null, plusCode: '9G8F+6W' }, A);
  assert.equal(cleared.status, 200, JSON.stringify(cleared.body));
  assert.deepEqual(pick(cleared.body.address), { ...EXTRA, gateColor: '', phone2: '', plusCode: '9G8F+6W' });
  //  نادرست در PUT ⇒ ۴۰۰ و ردیف همان
  assert.equal((await h.put(`${url}/${id}`, { label: 'x', landmark: 'y', phone2: 'abc' }, A)).body.error.code, 'bad_phone2');
  assert.equal((await h.get(url, A)).body.addresses[0].label, 'خانهٔ نو');
  //  مشتریِ دیگر نه می‌بیند نه عوض می‌کند
  assert.equal((await h.put(`${url}/${id}`, { label: 'دزدی', landmark: 'x', area: 'y' }, B)).status, 404);
  assert.deepEqual((await h.get(url, B)).body.addresses, []);
  assert.equal((await h.get(url, A)).body.addresses[0].area, EXTRA.area);
});

test('مهاجرتِ ۴۹۰: rollback ستون‌ها و ردیفِ خودش را برمی‌دارد، نشانی‌ها می‌مانند؛ دوباره نشستن همان', async () => {
  const s = await liveStore('rollback ِ نشانی');
  const A = await sessionFor(s.slug, '0791112205');
  const url = `/api/store/${s.slug}/account/addresses`;
  assert.equal((await h.post(url, FULL, A)).status, 201);
  const dir = path.join(__dirname, '..', 'migrations');
  const name = '490_store_customer_address_extra.sql';
  const down = fs.readFileSync(path.join(dir, 'rollback', name.replace('.sql', '.down.sql')), 'utf8');
  const up = fs.readFileSync(path.join(dir, name), 'utf8');
  const cols = async () => (await query(
    `SELECT column_name FROM information_schema.columns WHERE table_name='store_customer_addresses'
       AND column_name IN ('area','gate_color','province','street','house','phone2','plus_code')`)).rows.length;
  const mark = await one('SELECT * FROM schema_migrations WHERE version = $1', [name]);
  assert.ok(mark, 'مهاجرت نشسته است');
  assert.equal(await cols(), 7);
  await query(down);
  assert.equal(await cols(), 0);
  assert.equal(await one('SELECT 1 AS x FROM schema_migrations WHERE version = $1', [name]), null);
  const after = await h.get(url, A);
  assert.equal(after.status, 200, 'بی ستون‌ها هم خواندن نمی‌افتد');
  assert.equal(after.body.addresses[0].landmark, FULL.landmark);
  assert.equal(after.body.addresses[0].area, '');
  await query(up);
  await query('INSERT INTO schema_migrations (version, checksum, applied_at) VALUES ($1,$2,$3)', [mark.version, mark.checksum, mark.applied_at]);
  assert.equal(await cols(), 7);
  assert.equal((await h.post(url, { ...FULL, label: 'دوم' }, A)).status, 201);
});
