'use strict';
/**
 * IMG-PRO-17 (§۳۶.۱۲): «کاربر چند تغییرِ دسته‌ای را اعمال و سپس rollback می‌کند؛ همهٔ عکس‌ها به نسخهٔ قبلی
 * برمی‌گردند و audit log کامل است» — روی API و دیتابیسِ واقعی (PGlite/PostgreSQL). شرح: ‎lib/image-changes.js‎.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let root = '';
let clerk = '';
let A; let B; let cashierA;
let seed = 4100;
const img = (w = 800, hh = 600) => F.jpeg(w, hh, { seed: ++seed });

async function shopOwner(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  return { ...u, token: u.accessToken, shopId: made.body.shop.id };
}
async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
const C = (method, path, who, body) => h.api(method, '/api/shop/images/changes' + path, { token: who.token, body });
const S = (method, path, who, body) => h.api(method, '/api/shop/images' + path, { token: who.token, body });
const shown = async (who, pid) => (await S('GET', `/products/${pid}`, who)).body;
const pickOf = (r) => ({ source: r.source, image_id: r.image_id || '', version: Number(r.version || 0) });
//  ردیف‌های سابقهٔ یک تراکنش اغلب همان میلی‌ثانیه‌اند و `id` ِ سابقه تصادفی است (`newId`)، پس `ORDER BY created_at, id`
//  ترتیبِ کالاهای یک دسته را تصادفی می‌کرد (CI ِ #883: p3 پیش از p2). درونِ همان لحظه به ترتیبِ کالا، همان ترتیبِ نوشتن.
const audits = (shopId, action) => many(
  'SELECT target_id, detail FROM audit_logs WHERE shop_id=$1 AND action=$2 ORDER BY created_at, target_id, id', [shopId, action]);
const detailOf = (r) => (typeof r.detail === 'string' ? JSON.parse(r.detail) : r.detail);

let y1 = ''; let y2 = ''; let central = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  const made = await h.post('/api/admin/admins', { username: 'clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  clerk = (await h.post('/api/admin/login', { username: 'clerk', password: 'Clerk!12345' })).body.token;
  A = await shopOwner('الف');
  B = await shopOwner('ب');
  const u = await h.newUser('صندوقدار', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'cashier','active',$4,$4)`,
    [newId('mem'), A.shopId, u.user.id, now()]);
  cashierA = { ...u, token: u.accessToken };
});
test.after(async () => { await h.stop(); });

test('پرچمِ shop.image_bank خاموش (پیش‌فرض): ۴۰۳ و هیچ ردیفی', async () => {
  const r = await C('POST', '', A, { key: 'k0', items: [{ product_id: 'p1', source: 'none' }] });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'image_bank_disabled');
  assert.equal((await C('GET', '', A)).status, 403);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_image_changes')).n), 0);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_product_images')).n), 0);
});

test('آماده‌سازی: دو عکسِ خودِ دکان و یک عکسِ مرکزیِ تأییدشده', async () => {
  await setFlag('all', 'shop.image_bank', true);
  await setFlag('all', 'image_bank.central', true);
  const a = await h.raw('POST', '/api/shop/images/?product_id=p1', img(), { token: A.token });
  const b = await h.raw('POST', '/api/shop/images/?product_id=p1', img(), { token: A.token });
  assert.equal(a.status, 201, JSON.stringify(a.body));
  assert.equal(b.status, 201, JSON.stringify(b.body));
  y1 = a.body.id; y2 = b.body.id;
  const p = await h.api('POST', '/api/admin/image-bank/products', { token: clerk, body: { name: 'چای سبز', brand: 'باغ' } });
  assert.equal(p.status, 201, JSON.stringify(p.body));
  const up = await h.raw('POST', `/api/admin/image-bank/images?bank_product_id=${p.body.id}&license_status=licensed&commercial_use=true&license_source=supplier-feed`, img(), { token: clerk });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const ok = await h.api('POST', `/api/admin/image-bank/images/${up.body.id}/approve`, { token: root, body: { reason: 'مجوزِ تأمین‌کننده بررسی شد' } });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  central = up.body.id;
  //  کالای p1 پیش از همهٔ دسته‌ها عکسِ y1 را دارد؛ p2 و p3 هیچ ردیفی ندارند
  const sel = await S('POST', '/select', A, { product_id: 'p1', source: 'shop', image_id: y1 });
  assert.equal(sel.status, 200, JSON.stringify(sel.body));
});

test('IMG-PRO-17: دو دستهٔ پیاپی، برگرداندنِ دستهٔ تازه‌تر و سپس اولی ⇒ همهٔ کالاها به وضعِ پیشین، با سابقهٔ کامل', async () => {
  const start = { p1: pickOf(await shown(A, 'p1')), p2: await shown(A, 'p2'), p3: await shown(A, 'p3') };
  assert.deepEqual(start.p1, { source: 'shop', image_id: y1, version: 1 });
  assert.equal(start.p2.source, 'none');

  const first = await C('POST', '', A, { key: 'batch-1', note: 'عکس‌های تازهٔ قفسهٔ چای', items: [
    { product_id: 'p1', source: 'shop', image_id: y2 },
    { product_id: 'p2', source: 'shop', image_id: y2 },
    { product_id: 'p3', source: 'none' },
  ] });
  assert.equal(first.status, 201, JSON.stringify(first.body));
  const ch1 = first.body.change;
  assert.equal(ch1.status, 'applied');
  assert.equal(ch1.item_count, 3);
  assert.deepEqual(ch1.items.map((x) => x.product_id), ['p1', 'p2', 'p3']);
  assert.equal(ch1.items[0].before.image_id, y1);
  assert.equal(ch1.items[1].before, null);
  assert.deepEqual(pickOf(await shown(A, 'p1')), { source: 'shop', image_id: y2, version: 1 });
  assert.deepEqual(pickOf(await shown(A, 'p2')), { source: 'shop', image_id: y2, version: 1 });

  const second = await C('POST', '', A, { key: 'batch-2', items: [
    { product_id: 'p1', source: 'central', image_id: central, confirm: true },
    { product_id: 'p2', source: 'shop', image_id: y1 },
  ] });
  assert.equal(second.status, 201, JSON.stringify(second.body));
  const ch2 = second.body.change;
  assert.equal(pickOf(await shown(A, 'p1')).source, 'central');

  //  ⛔ اولی را پیش از دومی برگرداندن ⇒ هیچ: p1 و p2 پس از آن دوباره عوض شده‌اند
  const early = await C('POST', `/${ch1.id}/rollback`, A, { reason: 'اشتباه' });
  assert.equal(early.status, 409, JSON.stringify(early.body));
  assert.equal(early.body.error.code, 'changed_since');
  assert.deepEqual(early.body.error.products, ['p1', 'p2']);
  assert.equal(pickOf(await shown(A, 'p1')).source, 'central', 'هیچ کالایی برنگشت');
  assert.equal((await C('GET', `/${ch1.id}`, A)).body.change.status, 'applied');

  //  دومی ⇒ دقیقاً وضعِ پس از اولی
  const r2 = await C('POST', `/${ch2.id}/rollback`, A, { reason: 'عکسِ مرکزی بستهٔ قدیمی است' });
  assert.equal(r2.status, 200, JSON.stringify(r2.body));
  assert.equal(r2.body.change.status, 'rolled_back');
  assert.equal(r2.body.change.rollback_reason, 'عکسِ مرکزی بستهٔ قدیمی است');
  assert.deepEqual(pickOf(await shown(A, 'p1')), { source: 'shop', image_id: y2, version: 1 });
  assert.deepEqual(pickOf(await shown(A, 'p2')), { source: 'shop', image_id: y2, version: 1 });

  //  اولی ⇒ دقیقاً وضعِ پیش از همهٔ دسته‌ها (کالای بی ردیف دوباره بی ردیف)
  const r1 = await C('POST', `/${ch1.id}/rollback`, A, {});
  assert.equal(r1.status, 200, JSON.stringify(r1.body));
  assert.deepEqual(pickOf(await shown(A, 'p1')), start.p1);
  assert.deepEqual(await shown(A, 'p2'), start.p2);
  assert.deepEqual(await shown(A, 'p3'), start.p3);
  assert.equal(await one('SELECT 1 FROM shop_product_images WHERE shop_id=$1 AND product_id=$2', [A.shopId, 'p2']), null);
  const row1 = await one('SELECT * FROM shop_product_images WHERE shop_id=$1 AND product_id=$2', [A.shopId, 'p1']);
  assert.equal(row1.updated_by, A.user.id);

  //  تکرارِ برگشت (پس از قطعِ شبکه) ⇒ همان پاسخ، بی نوشتن
  const again = await C('POST', `/${ch1.id}/rollback`, A, {});
  assert.equal(again.status, 200);
  assert.equal(again.body.already, true);
  assert.equal((await audits(A.shopId, 'image.changes_rolled_back')).length, 2);

  //  سابقهٔ کامل: هر انتخاب و هر برگشت با شناسهٔ دسته
  const sel = (await audits(A.shopId, 'image.selected')).map(detailOf).filter((d) => d.change_id);
  assert.deepEqual(sel.map((d) => d.change_id), [ch1.id, ch1.id, ch1.id, ch2.id, ch2.id]);
  const restored = await audits(A.shopId, 'image.restored');
  assert.deepEqual(restored.map((r) => [r.target_id, detailOf(r).change_id]),
    [['p1', ch2.id], ['p2', ch2.id], ['p1', ch1.id], ['p2', ch1.id], ['p3', ch1.id]]);
  assert.equal(detailOf(restored[2]).image_id, y1);
  assert.equal(detailOf(restored[3]).source, 'previous_default');
  const applied = await audits(A.shopId, 'image.changes_applied');
  assert.deepEqual(applied.map((r) => r.target_id), [ch1.id, ch2.id]);
  //  `audit.scrub` فهرست را شیءِ شماره‌دار نگه می‌دارد
  assert.deepEqual(Object.values(detailOf(applied[0]).products), ['p1', 'p2', 'p3']);

  const list = await C('GET', '', A);
  assert.equal(list.status, 200);
  assert.deepEqual(list.body.changes.map((x) => [x.id, x.status]), [[ch2.id, 'rolled_back'], [ch1.id, 'rolled_back']]);
});

test('یا همه یا هیچ: یک قلمِ نادرست ⇒ هیچ کالایی عوض نمی‌شود، نه دسته‌ای، نه سابقه‌ای؛ خطا شمارهٔ قلم را دارد', async () => {
  const before = pickOf(await shown(A, 'p1'));
  const n0 = (await audits(A.shopId, 'image.selected')).length;
  const bad = await C('POST', '', A, { key: 'batch-bad', items: [
    { product_id: 'p1', source: 'shop', image_id: y2 },
    { product_id: 'p4', source: 'shop', image_id: 'img-not-mine' },
  ] });
  assert.equal(bad.status, 404, JSON.stringify(bad.body));
  assert.equal(bad.body.error.index, 1);
  assert.equal(bad.body.error.product_id, 'p4');
  assert.deepEqual(pickOf(await shown(A, 'p1')), before);
  assert.equal(await one('SELECT 1 FROM shop_image_changes WHERE client_key=$1', ['batch-bad']), null);
  assert.equal((await audits(A.shopId, 'image.selected')).length, n0);
  //  همان کالا دو بار ⇒ ۴۰۰؛ فهرستِ خالی یا بیش از ۱۰۰ ⇒ ۴۰۰
  assert.equal((await C('POST', '', A, { key: 'dup', items: [{ product_id: 'p1', source: 'none' }, { product_id: 'p1', source: 'none' }] })).body.error.code, 'duplicate_product');
  assert.equal((await C('POST', '', A, { key: 'empty', items: [] })).body.error.code, 'bad_items');
  const many101 = Array.from({ length: 101 }, (_, i) => ({ product_id: `q${i}`, source: 'none' }));
  assert.equal((await C('POST', '', A, { key: 'big', items: many101 })).body.error.code, 'bad_items');
  assert.equal((await C('POST', '', A, { items: [{ product_id: 'p1', source: 'none' }] })).body.error.code, 'key_required');
});

test('کلیدِ تکرار: همان کلید و همان قلم‌ها ⇒ همان دسته (۲۰۰)؛ همان کلید با قلمِ دیگر ⇒ ۴۰۹', async () => {
  const body = { key: 'batch-idem', items: [{ product_id: 'p5', source: 'shop', image_id: y1 }] };
  const a = await C('POST', '', A, body);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const b = await C('POST', '', A, body);
  assert.equal(b.status, 200, JSON.stringify(b.body));
  assert.equal(b.body.replayed, true);
  assert.equal(b.body.change.id, a.body.change.id);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_image_changes WHERE client_key=$1', ['batch-idem'])).n), 1);
  const c = await C('POST', '', A, { key: 'batch-idem', items: [{ product_id: 'p5', source: 'none' }] });
  assert.equal(c.status, 409);
  assert.equal(c.body.error.code, 'key_reused');
  //  همان کلید در دکانِ دیگر کلیدِ همان دکان است
  const other = await C('POST', '', B, { key: 'batch-idem', items: [{ product_id: 'p5', source: 'none' }] });
  assert.equal(other.status, 201, JSON.stringify(other.body));
});

test('جداسازی و مجوز: دکانِ دیگر دستهٔ «الف» را نه می‌بیند نه برمی‌گرداند؛ نقشِ بی اجازهٔ کالا ۴۰۳؛ بی توکن ۴۰۱', async () => {
  const mine = await C('POST', '', A, { key: 'batch-iso', items: [{ product_id: 'p6', source: 'shop', image_id: y2 }] });
  assert.equal(mine.status, 201, JSON.stringify(mine.body));
  const id = mine.body.change.id;
  assert.equal((await C('GET', `/${id}`, B)).status, 404);
  const steal = await C('POST', `/${id}/rollback`, B, {});
  assert.equal(steal.status, 404);
  assert.ok(!(await C('GET', '', B)).body.changes.some((x) => x.id === id));
  assert.equal((await C('GET', `/${id}`, A)).body.change.status, 'applied');
  //  عکسِ خصوصیِ «الف» در دستهٔ «ب» ⇒ «نیست»
  const foreign = await C('POST', '', B, { key: 'batch-foreign', items: [{ product_id: 'p6', source: 'shop', image_id: y2 }] });
  assert.equal(foreign.status, 404);
  //  صندوقدار عکسِ کالا نمی‌نویسد ⇒ نه دسته، نه برگشت؛ دیدنِ فهرست با `data.read` آزاد است
  assert.equal((await C('POST', '', cashierA, { key: 'batch-cashier', items: [{ product_id: 'p6', source: 'none' }] })).status, 403);
  assert.equal((await C('POST', `/${id}/rollback`, cashierA, {})).status, 403);
  assert.equal((await C('GET', `/${id}`, A)).body.change.status, 'applied');
  assert.equal((await h.api('GET', '/api/shop/images/changes', {})).status, 401);
});

test('سقفِ نرخِ IP پیش از احراز: درخواستِ بی توکن پس از ۱۲۰ بار ۴۲۹ می‌گیرد، نه ۴۰۱', async () => {
  resetAll();
  let last = 0;
  for (let i = 0; i < 121; i++) last = (await h.api('POST', '/api/shop/images/changes/x1/rollback', { body: {} })).status;
  assert.equal(last, 429);
  resetAll();
});
