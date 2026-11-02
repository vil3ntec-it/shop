'use strict';
/**
 * NTF-07 (گزارشِ مشکلِ سفارش با عکس، `lib/order-issues.js`) و NTF-09 (امتیاز و نظر با بازبینی، `lib/order-reviews.js`).
 *
 *   ۱) ⛔ پرچمِ خاموش ⇒ پاسخِ پیگیری بیت‌به‌بیت همان (بی `issueReports`/`review`)، درهای مشتری ۴۰۴، دکان ۴۰۳، بی ردیف.
 *   ۲) فقط سفارشِ خودِ مشتری: توکنِ دکانِ دیگر، توکنِ نادرست و گزارشِ سفارشِ دیگر ⇒ ۴۰۴؛ دکانِ دیگر نمی‌بیند.
 *   ۳) عکس: نوع از محتوا (بایتِ جادوییِ نادرست ⇒ ۴۰۰)، بزرگ ⇒ ۴۰۰، ابعادِ بمب ⇒ ۴۰۰؛ EXIF/GPS پاک؛ بایت‌ها خصوصی.
 *   ۴) سقف: ۳ گزارش، ۳ عکس، طولِ متن، و سطلِ هر سفارش (۴۲۹).
 *   ۵) نظر فقط پس از تحویل، یکی برای هر سفارش، و پیش از تأییدِ دکان هیچ‌جا منتشر نمی‌شود.
 */
process.env.RATE_STORE_CHECKOUT_MAX = '500';
process.env.RATE_STORE_FEEDBACK_MAX = '6';
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const { MAX_BYTES } = require('../src/lib/product-photos');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const ISSUES = 'shop.orders.issue_reports';
const REVIEWS = 'shop.orders.reviews';

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
const push = (token, ops) => h.api('POST', '/api/sync/v1/push', {
  token, headers: { 'X-App': 'shop', 'X-Device': 'dev-store' },
  body: { device_id: 'dev-store', schema_version: 2, ops },
});
const op = (rowId, fields) => ({ op_id: `op-${uniq()}`, ts: Date.now(), table: 'products', row_id: rowId, type: 'insert', fields });

async function shop(label, flags = [ISSUES, REVIEWS]) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const s = { ...u, token: u.accessToken, shopId: made.body.shop.id, slug: `f-${uniq()}` };
  for (const k of ['shop.orders', 'shop.storefront', ...flags]) await setFlag(s.shopId, k, true);
  assert.equal((await h.put('/api/shop/storefront', { slug: s.slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: s.token })).status, 200);
  assert.equal((await push(s.token, [op('rice', { name: 'برنج', unit: 'کیلو', salePrice: 120, category: 'خوراکی' })])).status, 200);
  assert.equal((await h.put('/api/shop/storefront/products/rice', { published: true }, { token: s.token })).status, 200);
  return s;
}

async function storeOrder(s, phone = '0791112233') {
  const st = await h.post(`/api/store/${s.slug}/verify/start`, { phone });
  const c = await h.post(`/api/store/${s.slug}/verify/check`, { phone, code: st.body.devCode });
  const r = await h.post(`/api/store/${s.slug}/checkout`, {
    fulfillment: 'pickup', customer: { name: 'مشتری', phone }, lines: [{ productId: 'rice', qtyMilli: 5000 }],
  }, { headers: { 'Idempotency-Key': `ck-${uniq()}-key`, 'X-Store-Ticket': c.body.ticket } });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const id = (await one('SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2', [s.shopId, r.body.order.orderNo])).id;
  return { id, token: r.body.order.token };
}
const deliver = async (s, id) => {
  for (const to of ['accepted', 'picking', 'packed', 'ready_for_pickup', 'picked_up']) {
    const t = await h.post(`/api/shop/orders/${id}/transition`, { to }, { token: s.token });
    assert.equal(t.status, 200, `${to}: ${JSON.stringify(t.body)}`);
  }
};
const trackRaw = (s, token) => h.get(`/api/store/${s.slug}/orders/${token}`);
const track = async (s, token) => {
  const r = await trackRaw(s, token);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.order;
};
const report = (s, token, body = {}) => h.post(`/api/store/${s.slug}/orders/${token}/issues`,
  { kind: 'damaged', text: 'کیسهٔ برنج پاره بود', requestKey: `rk-${uniq()}`, ...body });
const photo = (s, token, issueId, b64) => h.post(`/api/store/${s.slug}/orders/${token}/issues/${issueId}/photos`, { imageBase64: b64 });
const img = (seed = 1, o = {}) => F.jpeg(320, 240, { seed, ...o }).toString('base64');
const review = (s, token, body) => h.post(`/api/store/${s.slug}/orders/${token}/review`, body);
const count = async (table, shopId) => (await one(`SELECT COUNT(*)::int AS n FROM ${table} WHERE shop_id=$1`, [shopId])).n;

// ════════════════════════════════════════════════════════════════════
//  پرچمِ خاموش
// ════════════════════════════════════════════════════════════════════

test('NTF-07/09 ⛔ پرچمِ خاموش: پیگیری بیت‌به‌بیت همان، درها ۴۰۴/۴۰۳ و هیچ ردیفی', async () => {
  const s = await shop('خاموش', []);
  const o = await storeOrder(s);
  await deliver(s, o.id);
  const before = JSON.stringify((await trackRaw(s, o.token)).body);
  const other = await shop('همسایهٔ روشن');   // پرچمِ دکانِ دیگر این دکان را عوض نمی‌کند
  assert.equal(JSON.stringify((await trackRaw(s, o.token)).body), before);
  const order = JSON.parse(before).order;
  assert.equal('issueReports' in order, false);
  assert.equal('review' in order, false);
  for (const r of [await report(s, o.token), await review(s, o.token, { stars: 5 }),
    await photo(s, o.token, '01ARZ3NDEKTSV4RRFFQ69G5FAV', img())]) {
    assert.equal(r.status, 404, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'order_not_found');
  }
  assert.equal((await h.get(`/api/store/${s.slug}/reviews`)).status, 404);
  const li = await h.get('/api/shop/order-feedback/issues', { token: s.token });
  assert.equal(li.status, 403);
  assert.equal(li.body.error.code, 'issue_reports_disabled');
  const lr = await h.get('/api/shop/order-feedback/reviews', { token: s.token });
  assert.equal(lr.status, 403);
  assert.equal(lr.body.error.code, 'reviews_disabled');
  assert.equal(await count('order_issues', s.shopId), 0);
  assert.equal(await count('order_reviews', s.shopId), 0);
  //  روشن ⇒ فقط همان دو کلیدِ تازه افزوده می‌شود؛ بقیهٔ پاسخ همان
  for (const k of [ISSUES, REVIEWS]) await setFlag(s.shopId, k, true);
  const on = (await trackRaw(s, o.token)).body;
  assert.ok(on.order.issueReports && on.order.review);
  delete on.order.issueReports;
  delete on.order.review;
  assert.equal(JSON.stringify(on), before);
  assert.ok(other.shopId);
});

test('NTF-09 ⛔ ویترین: `features.reviews` فقط با پرچمِ نظر؛ خاموش ⇒ پاسخِ ویترین بیت‌به‌بیت همان', async () => {
  const s = await shop('ویترینِ نظر', []);
  const info = async () => (await h.get(`/api/store/${s.slug}`)).body;
  const before = JSON.stringify(await info());
  assert.equal('reviews' in (JSON.parse(before).features || {}), false);
  await setFlag(s.shopId, ISSUES, true);   // پرچمِ گزارش کلیدِ ویترین نمی‌سازد
  assert.equal(JSON.stringify(await info()), before);
  await setFlag(s.shopId, REVIEWS, true);
  const on = await info();
  assert.equal(on.features.reviews, true);
  delete on.features.reviews;
  if (!Object.keys(on.features).length) delete on.features;
  assert.equal(JSON.stringify(on), before, 'روشن ⇒ فقط همین کلید');
  await setFlag(s.shopId, REVIEWS, null);
  assert.equal(JSON.stringify(await info()), before, 'دوباره خاموش ⇒ همان');
});

// ════════════════════════════════════════════════════════════════════
//  NTF-07 — گزارشِ مشکل
// ════════════════════════════════════════════════════════════════════

test('NTF-07 گزارش با عکس: پیگیری می‌بیند، دکان فهرست و عکسِ بی GPS را می‌بیند و با پاسخ می‌بندد', async () => {
  const s = await shop('گزارش');
  const o = await storeOrder(s);
  const r = await report(s, o.token, { requestKey: 'rk-fixed-0001' });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  assert.equal(r.body.issue.status, 'open');
  const again = await report(s, o.token, { requestKey: 'rk-fixed-0001' });
  assert.equal(again.status, 200);
  assert.equal(again.body.replayed, true);
  assert.equal(again.body.issue.id, r.body.issue.id);
  const reused = await report(s, o.token, { requestKey: 'rk-fixed-0001', text: 'متنِ دیگر' });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'request_key_reused');

  const raw = F.jpeg(320, 240, { gps: true, seed: 3 });
  const p = await photo(s, o.token, r.body.issue.id, raw.toString('base64'));
  assert.equal(p.status, 201, JSON.stringify(p.body));
  assert.equal(p.body.photo.mime, 'image/jpeg');
  assert.equal(p.body.photo.width, 320);
  assert.equal((await photo(s, o.token, r.body.issue.id, raw.toString('base64'))).body.replayed, true, 'همان عکس ⇒ همان');

  const t = await track(s, o.token);
  assert.equal(t.issueReports.issues.length, 1);
  assert.equal(t.issueReports.issues[0].photos, 1, 'مشتری فقط شمار را می‌بیند');
  assert.equal(t.issueReports.canReport, true);

  const list = await h.get('/api/shop/order-feedback/issues', { token: s.token });
  assert.equal(list.status, 200, JSON.stringify(list.body));
  assert.equal(list.body.issues.length, 1);
  const it = list.body.issues[0];
  assert.equal(it.text, 'کیسهٔ برنج پاره بود');
  assert.equal(it.orderId, o.id);
  assert.equal(it.photos.length, 1);
  const got = await h.download(`/api/shop/order-feedback/issues/${it.id}/photos/${it.photos[0].id}`, { token: s.token });
  assert.equal(got.status, 200);
  assert.match(got.headers.get('content-type'), /image\/jpeg/);
  assert.equal(got.headers.get('cache-control'), 'private, no-store');
  assert.equal(got.headers.get('x-content-type-options'), 'nosniff');
  assert.ok(!got.buffer.includes(Buffer.from('shot at', 'latin1')), 'توضیحِ جا پاک شد');
  assert.ok(!got.buffer.includes(Buffer.from([0x88, 0x25])), 'برچسبِ GPS پاک شد');
  assert.ok(got.buffer.length < raw.length);
  const viewed = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='order.issue.photo.view' AND shop_id=$1`, [s.shopId]);
  assert.equal(viewed.n, 1, 'دیدنِ عکس در سابقه');

  assert.equal((await h.post(`/api/shop/order-feedback/issues/${it.id}/resolve`, {}, { token: s.token })).body.error.code, 'bad_reply');
  const done = await h.post(`/api/shop/order-feedback/issues/${it.id}/resolve`, { reply: 'یک کیسهٔ تازه با پیک می‌فرستیم' }, { token: s.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.equal(done.body.issue.status, 'resolved');
  assert.equal((await h.post(`/api/shop/order-feedback/issues/${it.id}/resolve`, { reply: 'یک کیسهٔ تازه با پیک می‌فرستیم' }, { token: s.token })).body.unchanged, true);
  const other = await h.post(`/api/shop/order-feedback/issues/${it.id}/resolve`, { reply: 'پاسخِ دیگر' }, { token: s.token });
  assert.equal(other.status, 409);
  assert.equal(other.body.error.code, 'already_resolved');
  const t2 = await track(s, o.token);
  assert.equal(t2.issueReports.issues[0].status, 'resolved');
  assert.equal(t2.issueReports.issues[0].reply, 'یک کیسهٔ تازه با پیک می‌فرستیم');
  const late = await photo(s, o.token, it.id, img(9));
  assert.equal(late.status, 409);
  assert.equal(late.body.error.code, 'issue_closed');
  assert.equal((await h.get('/api/shop/order-feedback/issues', { token: s.token })).body.issues.length, 0, 'پیش‌فرض فقط باز');
  assert.equal((await h.get('/api/shop/order-feedback/issues?status=resolved', { token: s.token })).body.issues.length, 1);
});

test('NTF-07 ⛔ فقط سفارشِ خودِ مشتری: توکنِ دکانِ دیگر، توکنِ نادرست و گزارشِ سفارشِ دیگر ۴۰۴؛ دکانِ دیگر نمی‌بیند', async () => {
  const a = await shop('الف');
  const b = await shop('ب');
  const oa = await storeOrder(a, '0791110001');
  const oa2 = await storeOrder(a, '0791110002');
  const ob = await storeOrder(b, '0791110003');
  assert.equal((await report(a, ob.token)).status, 404, 'توکنِ دکانِ دیگر');
  assert.equal((await report(a, `${oa.token}x`)).status, 404, 'توکنِ دست‌کاری‌شده');
  const r = await report(a, oa.token);
  assert.equal(r.status, 201);
  const cross = await photo(a, oa2.token, r.body.issue.id, img());
  assert.equal(cross.status, 404, 'گزارشِ سفارشِ دیگرِ همان دکان');
  assert.equal(cross.body.error.code, 'issue_not_found');
  assert.equal((await photo(b, ob.token, r.body.issue.id, img())).status, 404);
  assert.equal((await photo(a, oa.token, r.body.issue.id, img())).status, 201);
  const pid = (await one('SELECT id FROM order_issue_photos WHERE issue_id=$1', [r.body.issue.id])).id;
  assert.equal((await h.get('/api/shop/order-feedback/issues', { token: b.token })).body.issues.length, 0);
  assert.equal((await h.raw('GET', `/api/shop/order-feedback/issues/${r.body.issue.id}/photos/${pid}`, undefined, { token: b.token })).status, 404);
  assert.equal((await h.post(`/api/shop/order-feedback/issues/${r.body.issue.id}/resolve`, { reply: 'نه' }, { token: b.token })).status, 404);
  assert.equal((await track(a, oa2.token)).issueReports.issues.length, 0, 'سفارشِ دیگر گزارشِ این را نمی‌بیند');
  assert.equal(await count('order_issues', b.shopId), 0);
});

test('NTF-07 ⛔ سنجشِ عکس: بایتِ جادوییِ نادرست، بزرگ، ابعادِ بمب و base64 ِ بد رد؛ هیچ ردیفی', async () => {
  const s = await shop('عکس');
  const o = await storeOrder(s);
  const r = await report(s, o.token);
  const id = r.body.issue.id;
  const fake = Buffer.concat([Buffer.from('<html><script>alert(1)</script>', 'latin1'), Buffer.alloc(200, 1)]);
  const cases = [
    [fake.toString('base64'), 'image_type'],
    [Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xe0]), Buffer.alloc(300, 0)]).toString('base64'), 'image_corrupt'],
    [Buffer.alloc(MAX_BYTES + 10, 0xff).toString('base64'), 'photo_too_large'],
    [F.pngBomb(12000, 4).toString('base64'), 'image_too_large_dimensions'],
    ['@@not-base64@@', 'image_type'],
  ];
  for (const [b64, code] of cases) {
    const p = await photo(s, o.token, id, b64);
    assert.equal(p.status, 400, `${code}: ${JSON.stringify(p.body)}`);
    assert.equal(p.body.error.code, code);
  }
  resetAll();   // شش نوشتن تا این‌جا = سقفِ آزمون
  assert.equal((await photo(s, o.token, id, '')).body.error.code, 'photo_required');
  assert.equal(await count('order_issue_photos', s.shopId), 0);
});

test('NTF-07 ⛔ سقف‌ها: ۳ گزارش، ۳ عکس، طولِ متن و نوع؛ سطلِ هر سفارش ۴۲۹', async () => {
  const s = await shop('سقف');
  const o = await storeOrder(s);
  assert.equal((await report(s, o.token, { text: 'اه' })).body.error.code, 'bad_text');
  assert.equal((await report(s, o.token, { text: 'ب'.repeat(1001) })).body.error.code, 'bad_text');
  assert.equal((await report(s, o.token, { kind: 'refund_me' })).body.error.code, 'bad_kind');
  assert.equal((await report(s, o.token, { requestKey: 'x' })).body.error.code, 'bad_request_key');
  resetAll();
  for (let i = 0; i < 3; i++) assert.equal((await report(s, o.token)).status, 201);
  const fourth = await report(s, o.token);
  assert.equal(fourth.status, 409);
  assert.equal(fourth.body.error.code, 'too_many_issues');
  assert.equal((await track(s, o.token)).issueReports.canReport, false);

  resetAll();
  const o2 = await storeOrder(s, '0791119999');
  const id = (await report(s, o2.token)).body.issue.id;
  for (let i = 1; i <= 3; i++) assert.equal((await photo(s, o2.token, id, img(i))).status, 201);
  const extra = await photo(s, o2.token, id, img(4));
  assert.equal(extra.status, 409);
  assert.equal(extra.body.error.code, 'too_many_photos');
  //  ششمین نوشتنِ همین سفارش در ساعت (سقفِ آزمون ۶) هنوز می‌رسد؛ هفتمی ۴۲۹ — حتی با بدنهٔ بد
  assert.equal((await photo(s, o2.token, id, img(5))).status, 409);
  const limited = await photo(s, o2.token, id, img(6));
  assert.equal(limited.status, 429, JSON.stringify(limited.body));
  assert.ok(limited.headers.get('retry-after'));
  assert.equal((await report(s, o.token)).status, 409, 'سفارشِ دیگر سطلِ خودش را دارد');
});

// ════════════════════════════════════════════════════════════════════
//  NTF-09 — نظرِ سفارش
// ════════════════════════════════════════════════════════════════════

test('NTF-09 ⛔ نظر فقط پس از تحویل؛ یکی برای هر سفارش (تکرار همان، دیگر ۴۰۹)', async () => {
  const s = await shop('نظر');
  const o = await storeOrder(s);
  assert.deepEqual((await track(s, o.token)).review, { canReview: false, commentMax: 500, review: null });
  const early = await review(s, o.token, { stars: 5, comment: 'عالی' });
  assert.equal(early.status, 409);
  assert.equal(early.body.error.code, 'not_delivered');
  await deliver(s, o.id);
  assert.equal((await track(s, o.token)).review.canReview, true);
  assert.equal((await review(s, o.token, { stars: 0 })).body.error.code, 'bad_stars');
  assert.equal((await review(s, o.token, { stars: 4.5 })).body.error.code, 'bad_stars');
  assert.equal((await review(s, o.token, { stars: 4, comment: 'خ'.repeat(501) })).body.error.code, 'bad_comment');
  resetAll();   // سطلِ هر سفارش (سقفِ آزمون ۶)
  const ok = await review(s, o.token, { stars: 4, comment: 'تازه و تمیز' });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  assert.equal(ok.body.review.status, 'pending');
  const same = await review(s, o.token, { stars: 4, comment: 'تازه و تمیز' });
  assert.equal(same.status, 200);
  assert.equal(same.body.replayed, true);
  const second = await review(s, o.token, { stars: 1, comment: 'بد' });
  assert.equal(second.status, 409);
  assert.equal(second.body.error.code, 'already_reviewed');
  assert.equal(await count('order_reviews', s.shopId), 1);
  const t = await track(s, o.token);
  assert.equal(t.review.canReview, false);
  assert.equal(t.review.review.status, 'pending');
  assert.equal(t.review.review.comment, 'تازه و تمیز');
});

test('NTF-09 ⛔ بازبینی: پیش از تأیید هیچ‌جا منتشر نیست؛ تأیید ⇒ عمومی بی نشانِ مشتری؛ رد ⇒ برداشته', async () => {
  const s = await shop('بازبینی');
  const b = await shop('بازبینیِ همسایه');
  const o = await storeOrder(s);
  await deliver(s, o.id);
  const html = '<img src=x onerror=alert(1)>';
  assert.equal((await review(s, o.token, { stars: 5, comment: html })).status, 201);
  const pub0 = await h.get(`/api/store/${s.slug}/reviews`);
  assert.equal(pub0.status, 200);
  assert.deepEqual(pub0.body, { count: 0, avgTenths: null, reviews: [] }, 'در انتظار ⇒ منتشر نشده');
  const pending = await h.get('/api/shop/order-feedback/reviews', { token: s.token });
  assert.equal(pending.body.reviews.length, 1);
  const rid = pending.body.reviews[0].id;
  assert.equal((await h.post(`/api/shop/order-feedback/reviews/${rid}/moderate`, { decision: 'publish' }, { token: s.token })).body.error.code, 'bad_decision');
  assert.equal((await h.post(`/api/shop/order-feedback/reviews/${rid}/moderate`, { decision: 'approve' }, { token: b.token })).status, 404, 'دکانِ دیگر');
  const ok = await h.post(`/api/shop/order-feedback/reviews/${rid}/moderate`, { decision: 'approve' }, { token: s.token });
  assert.equal(ok.status, 200, JSON.stringify(ok.body));
  assert.equal(ok.body.review.status, 'approved');
  assert.equal((await h.post(`/api/shop/order-feedback/reviews/${rid}/moderate`, { decision: 'approve' }, { token: s.token })).body.unchanged, true);
  const pub = (await h.get(`/api/store/${s.slug}/reviews`)).body;
  assert.equal(pub.count, 1);
  assert.equal(pub.avgTenths, 50);
  assert.deepEqual(Object.keys(pub.reviews[0]).sort(), ['at', 'comment', 'stars'], 'بی شماره، نام و شمارهٔ سفارش');
  assert.equal(pub.reviews[0].comment, html, 'متن خام می‌رود؛ صفحه با textContent نشان می‌دهد');
  assert.equal((await h.get(`/api/store/${b.slug}/reviews`)).body.count, 0, 'ویترینِ دیگر');
  const no = await h.post(`/api/shop/order-feedback/reviews/${rid}/moderate`, { decision: 'reject' }, { token: s.token });
  assert.equal(no.body.review.status, 'rejected');
  assert.equal((await h.get(`/api/store/${s.slug}/reviews`)).body.count, 0, 'رد ⇒ برداشته');
  assert.equal((await track(s, o.token)).review.review.status, 'rejected');
  const audit = await one(`SELECT COUNT(*)::int AS n FROM audit_logs WHERE action='order.review.moderate' AND shop_id=$1`, [s.shopId]);
  assert.equal(audit.n, 2);
});

test('NTF-09 ⛔ فقط سفارشِ خودِ مشتری: توکنِ دکانِ دیگر ۴۰۴', async () => {
  const a = await shop('نظرِ الف');
  const b = await shop('نظرِ ب');
  const ob = await storeOrder(b);
  await deliver(b, ob.id);
  const r = await review(a, ob.token, { stars: 5 });
  assert.equal(r.status, 404);
  assert.equal(r.body.error.code, 'order_not_found');
  assert.equal(await count('order_reviews', a.shopId) + await count('order_reviews', b.shopId), 0);
});
