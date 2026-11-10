'use strict';
/**
 * استودیوی عکس روی API و پایگاهِ واقعی (PGlite/PostgreSQL) — AI-02، AI-05، IMG-04…06، IMG-PRO-08، IMG-PRO-14.
 * شرح: ‎lib/image-studio-jobs.js‎ و ‎routes/shop-image-studio.js‎ (مهاجرتِ ۵۹۰).
 *
 *   - پرچم‌ها خاموش ⇒ ۴۰۳ و هیچ ردیف؛ سقفِ IP پیش از احراز.
 *   - دسته: یک‌بارگیِ کلید، فقط عکسِ خودِ دکان، سقف‌ها، مجوزِ نوشتن.
 *   - خروجی نسخهٔ تازهٔ «نیازمندِ بررسی»؛ اصل دست نمی‌خورد؛ انتخاب پیش از «تأیید» ۴۰۹ (درِ انتشار)؛
 *     پرریسک فقط با «تأییدِ ریسک»؛ ردشده هرگز؛ تصویرِ تبلیغ هرگز عکسِ کالا؛ بازگشت به اصل همان نسخهٔ ۱.
 *   - IMG-PRO-14: هزار عکس با چند کارگر — همزمانیِ محدود، پیشرفتِ دقیق، تلاشِ دوباره و اجارهٔ گم‌شده بی نسخهٔ تکراری.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const F = require('./image-fixtures');
const bytes = require('../src/lib/image-bytes');
const studio = require('../src/lib/image-studio');
const jobs = require('../src/lib/image-studio-jobs');
const { ulid } = require('../src/lib/ulid');
const { query, one, many, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');

let root = '';
let A; let B; let cashierA;
const IMG = {};
const W = 600, H = 450;
const scene = (k = [1, 1, 1], label = true, w = W, h = H) => (x, y) => {
  const u = x / w, v = y / h;
  let c = [245, 245, 245];
  if (u > 0.3 && u < 0.7 && v > 0.2 && v < 0.85) {
    c = label && u > 0.38 && u < 0.62 && v > 0.45 && v < 0.6
      ? (Math.floor((v - 0.45) * 200) % 6 < 3 && Math.floor((u - 0.38) * 200) % 5 < 3 ? [20, 20, 20] : [250, 250, 250])
      : [200, 30, 40];
  }
  return c.map((v2, i) => v2 * k[i]);
};
const jpg = (k, label, w = W, h = H) => F.jpegImage(w, h, scene(k, label, w, h), { quality: 90, color: true });

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
const S = (method, path, who, body) => h.api(method, '/api/shop/images/studio' + path, { token: who && who.token, body });
const I = (method, path, who, body) => h.api(method, '/api/shop/images' + path, { token: who.token, body });
const upload = async (who, buf, pid) => {
  const r = await h.raw('POST', `/api/shop/images/?product_id=${pid}`, buf, { token: who.token });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  return r.body.id;
};
const count = async (sql, p = []) => Number((await one(sql, p)).n);
const versionsOf = (id) => count('SELECT COUNT(*)::int AS n FROM image_versions WHERE image_id=$1', [id]);
const audits = (shopId, action) => many('SELECT detail FROM audit_logs WHERE shop_id=$1 AND action=$2', [shopId, action]);

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  A = await shopOwner('الف');
  B = await shopOwner('ب');
  const u = await h.newUser('صندوقدار', 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at) VALUES ($1,$2,$3,'cashier','active',$4,$4)`,
    [newId('mem'), A.shopId, u.user.id, now()]);
  cashierA = { ...u, token: u.accessToken };
});
test.after(async () => { jobs._setIsolate(null); await h.stop(); });
test.beforeEach(() => resetAll());

test('پرچم‌ها خاموش (پیش‌فرض) ⇒ ۴۰۳ و هیچ ردیف؛ بی توکن ۴۰۱', async () => {
  assert.equal((await S('GET', '/config', null)).status, 401);
  let r = await S('POST', '/batches', A, { key: 'off-key-1', image_ids: ['img_01HZZZZZZZZZZZZZZZZZZZZZZZ'] });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'image_bank_disabled');
  await setFlag('all', 'shop.image_bank', true);
  r = await S('POST', '/batches', A, { key: 'off-key-1', image_ids: ['img_01HZZZZZZZZZZZZZZZZZZZZZZZ'] });
  assert.equal(r.status, 403);
  assert.equal(r.body.error.code, 'image_studio_disabled');
  assert.equal((await S('GET', '/batches', A)).status, 403);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM image_studio_batches'), 0);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM image_studio_jobs'), 0);
});

test('سقفِ نرخِ IP پیش از احراز: بی توکن پس از ۱۲۰ بار ۴۲۹، نه ۴۰۱', async () => {
  let last = 0;
  for (let i = 0; i < 121; i++) last = (await S('POST', '/batches', null, {})).status;
  assert.equal(last, 429);
});

test('آماده‌سازی: پرچم برای «الف» و «ب»، عکس‌های خودِ دکان', async () => {
  await setFlag(A.shopId, 'shop.image_studio', true);
  await setFlag(B.shopId, 'shop.image_studio', true);
  const cfg = await S('GET', '/config', A);
  assert.equal(cfg.status, 200);
  assert.deepEqual(Object.keys(cfg.body.presets), ['catalog', 'enhance', 'ad']);
  IMG.plain = await upload(A, jpg(undefined, true, 1200, 900), 'p1');   //  بزرگ ⇒ نسخهٔ فهرست (۴۰۰) هم ساخته می‌شود
  IMG.yellow = await upload(A, jpg([1, 0.93, 0.75]), 'p2');
  IMG.dark = await upload(A, jpg([0.45, 0.45, 0.45]), 'p3');
  IMG.webp = await upload(A, F.webpLossy(640, 480), 'p4');
  IMG.foreign = await upload(B, jpg(), 'q1');
});

test('دسته: کلیدِ یکتایی، فقط عکسِ خودِ دکان (بی نشت)، سقف‌ها و گزینه‌ها، مجوزِ نوشتن', async () => {
  const bad = [
    [{ key: 'short', image_ids: [IMG.plain] }, 400, 'bad_key'],
    [{ key: 'batch-bad-01', image_ids: [] }, 400, 'bad_images'],
    [{ key: 'batch-bad-01', image_ids: ['../etc'] }, 400, 'bad_images'],
    [{ key: 'batch-bad-01', image_ids: [IMG.plain, IMG.plain] }, 400, 'duplicate_image'],
    [{ key: 'batch-bad-01', image_ids: Array.from({ length: 1001 }, () => 'img_' + ulid()) }, 400, 'bad_images'],
    [{ key: 'batch-bad-01', image_ids: [IMG.plain], preset: 'poster' }, 400, 'bad_preset'],
    [{ key: 'batch-bad-01', image_ids: [IMG.plain], options: { shadow: true, background: 'keep' } }, 400, 'shadow_needs_white'],
    [{ key: 'batch-bad-01', image_ids: [IMG.plain, IMG.foreign] }, 404, 'image_not_found'],
  ];
  for (const [body, status, code] of bad) {
    const r = await S('POST', '/batches', A, body);
    assert.equal(r.status, status, JSON.stringify([body.key, r.body]));
    assert.equal(r.body.error.code, code);
  }
  //  شناسهٔ عکسِ «ب» در پاسخِ ۴۰۴ همان است که «الف» فرستاد — چیزی از «ب» برنمی‌گردد
  assert.equal((await S('POST', '/batches', cashierA, { key: 'batch-cashier', image_ids: [IMG.plain] })).status, 403);
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM image_studio_batches'), 0, 'هیچ درخواستِ نادرستی ردیف نساخت');
});

let batch1 = '';
test('دسته‌ای از سه عکس: پیشرفتِ دقیق، نسخهٔ تازهٔ «نیازمندِ بررسی»، اصل دست‌نخورده؛ همان کلید ⇒ همان دسته', async () => {
  const before = await one('SELECT b.hash FROM image_versions v JOIN image_blobs b ON b.hash=v.blob_hash WHERE v.image_id=$1 AND v.version=1', [IMG.plain]);
  const body = { key: 'batch-three-1', image_ids: [IMG.plain, IMG.yellow, IMG.dark] };
  const r = await S('POST', '/batches', A, body);
  assert.equal(r.status, 201, JSON.stringify(r.body));
  batch1 = r.body.id;
  assert.equal(r.body.progress.queued, 3);
  assert.equal(r.body.progress.percent, 0);
  const again = await S('POST', '/batches', A, body);
  assert.equal(again.status, 200);
  assert.equal(again.body.id, batch1);
  assert.equal(again.body.existing, true);
  const reused = await S('POST', '/batches', A, { ...body, image_ids: [IMG.plain] });
  assert.equal(reused.status, 409);
  assert.equal(reused.body.error.code, 'key_reused');
  await jobs.drain();
  const v = await S('GET', `/batches/${batch1}`, A);
  assert.equal(v.status, 200);
  assert.deepEqual([v.body.progress.done, v.body.progress.failed, v.body.progress.finished, v.body.progress.percent], [3, 0, 3, 100]);
  assert.equal(v.body.review.needs_review, 3);
  for (const it of v.body.items) {
    assert.equal(it.status, 'done');
    assert.equal(it.result_version, 2);
    assert.equal(it.review, 'needs_review');
    assert.match(it.before, /\/1\/original$/);
    assert.match(it.after, /\/2\/original$/);
  }
  const yellow = v.body.items.find((x) => x.image_id === IMG.yellow);
  assert.ok(yellow.risk.includes('color_changed'), JSON.stringify(yellow));
  //  اصل همان بایت‌ها؛ نسخهٔ ۲ JPEG ِ مربعِ بی EXIF با نسخهٔ فهرست
  const after = await one('SELECT b.hash FROM image_versions v JOIN image_blobs b ON b.hash=v.blob_hash WHERE v.image_id=$1 AND v.version=1', [IMG.plain]);
  assert.equal(after.hash, before.hash);
  const out = await h.download(`/api/shop/images/file/${IMG.plain}/2/original`, { token: A.token });
  assert.equal(out.status, 200);
  const info = bytes.inspect(out.buffer);
  assert.equal(info.width, info.height);
  assert.equal(info.orientation, 0);
  assert.equal((await h.download(`/api/shop/images/file/${IMG.plain}/2/thumb`, { token: A.token })).status, 200);
  const row = await one('SELECT kind, parent, tool, review, settings FROM image_versions WHERE image_id=$1 AND version=2', [IMG.plain]);
  assert.deepEqual([row.kind, Number(row.parent), row.tool, row.review], ['processed', 1, jobs.TOOL, 'needs_review']);
  assert.equal(JSON.parse(row.settings).preset, 'catalog');
  assert.equal((await audits(A.shopId, 'image.studio_output')).length, 3);
});

test('درِ انتشار: نسخهٔ تأییدنشده انتخاب نمی‌شود؛ پرریسک فقط با «تأییدِ ریسک»؛ ردشده هرگز؛ بازگشت به اصل', async () => {
  //  پیش از این تغییر، `select` هر نسخهٔ عکسِ دکان را بی نگاه به `review` می‌پذیرفت
  let r = await I('POST', '/select', A, { product_id: 'p1', source: 'shop', image_id: IMG.plain, version: 2 });
  assert.equal(r.status, 409, JSON.stringify(r.body));
  assert.equal(r.body.error.code, 'version_not_approved');
  r = await S('POST', `/images/${IMG.plain}/versions/2/review`, A, { decision: 'approve' });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.review, 'approved');
  r = await I('POST', '/select', A, { product_id: 'p1', source: 'shop', image_id: IMG.plain, version: 2 });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal((await I('GET', '/products/p1', A)).body.version, 2);
  //  بازگشت به اصل = همان نسخهٔ ۱
  r = await I('POST', '/select', A, { product_id: 'p1', source: 'shop', image_id: IMG.plain, version: 1 });
  assert.equal(r.status, 200);
  assert.equal((await I('GET', '/products/p1', A)).body.version, 1);
  //  پرریسک (ضریبِ بزرگِ رنگ): بی confirm_risk ۴۰۹ و هیچ تغییر
  r = await S('POST', `/images/${IMG.yellow}/versions/2/review`, A, { decision: 'approve' });
  assert.equal(r.status, 409);
  assert.equal(r.body.error.code, 'risk_confirmation_required');
  assert.equal((await one('SELECT review FROM image_versions WHERE image_id=$1 AND version=2', [IMG.yellow])).review, 'needs_review');
  r = await S('POST', `/images/${IMG.yellow}/versions/2/review`, A, { decision: 'approve', confirm_risk: true });
  assert.equal(r.status, 200);
  const conf = await audits(A.shopId, 'image.studio_approved');
  assert.ok(conf.some((x) => (typeof x.detail === 'string' ? JSON.parse(x.detail) : x.detail).confirm_risk === true));
  //  رد: انتخاب ۴۰۹؛ تصمیمِ دیگر ۴۰۹؛ همان تصمیم بی تغییر
  r = await S('POST', `/images/${IMG.dark}/versions/2/review`, A, { decision: 'reject' });
  assert.equal(r.status, 200);
  assert.equal((await I('POST', '/select', A, { product_id: 'p3', source: 'shop', image_id: IMG.dark, version: 2 })).body.error.code, 'version_not_approved');
  assert.equal((await S('POST', `/images/${IMG.dark}/versions/2/review`, A, { decision: 'approve', confirm_risk: true })).body.error.code, 'already_reviewed');
  assert.equal((await S('POST', `/images/${IMG.dark}/versions/2/review`, A, { decision: 'reject' })).body.unchanged, true);
  //  اصل (نسخهٔ ۱) خروجیِ استودیو نیست و این‌جا بررسی نمی‌شود
  assert.equal((await S('POST', `/images/${IMG.plain}/versions/1/review`, A, { decision: 'reject' })).body.error.code, 'not_studio_output');
  assert.equal((await S('POST', `/images/${IMG.plain}/versions/2/review`, A, { decision: 'maybe' })).status, 400);
  assert.equal((await S('POST', `/images/${IMG.plain}/versions/2/review`, cashierA, { decision: 'reject' })).status, 403);
  //  نسخه‌ها برای مقایسهٔ قبل/بعد
  const vs = await S('GET', `/images/${IMG.yellow}/versions`, A);
  assert.equal(vs.status, 200);
  assert.deepEqual(vs.body.versions.map((x) => [x.version, x.kind, x.review]), [[1, 'original', 'approved'], [2, 'processed', 'approved']]);
  assert.ok(vs.body.versions[1].studio.risk.includes('color_changed'));
  //  خروجیِ کوچک‌تر از ۴۰۰ پیکسل نسخهٔ فهرستِ جدا ندارد (هرگز بزرگ‌نمایی)؛ عکسِ بزرگ دارد
  assert.ok(vs.body.versions[1].width <= 400 ? vs.body.versions[1].urls.thumb === null : vs.body.versions[1].urls.thumb);
  assert.ok((await S('GET', `/images/${IMG.plain}/versions`, A)).body.versions[1].urls.thumb);
});

test('جداییِ دکان‌ها: «ب» نه دستهٔ «الف» را می‌بیند، نه کاری با آن، نه نسخه‌ها یا بازبینیِ عکسِ «الف»', async () => {
  for (const [m, p, body] of [
    ['GET', `/batches/${batch1}`], ['POST', `/batches/${batch1}/action`, { action: 'cancel' }],
    ['GET', `/images/${IMG.plain}/versions`], ['POST', `/images/${IMG.plain}/versions/2/review`, { decision: 'reject' }],
  ]) {
    const r = await S(m, p, B, body);
    assert.equal(r.status, 404, `${m} ${p}`);
  }
  assert.deepEqual((await S('GET', '/batches', B)).body.items, []);
  assert.equal((await h.download(`/api/shop/images/file/${IMG.plain}/2/original`, { token: B.token })).status, 404);
  assert.equal((await one('SELECT state FROM image_studio_batches WHERE id=$1', [batch1])).state, 'active');
  assert.equal((await one('SELECT review FROM image_versions WHERE image_id=$1 AND version=2', [IMG.plain])).review, 'approved');
});

test('دوباره‌کاری بی نسخهٔ تکراری: همان عکس و همان تنظیمات در دستهٔ تازه ⇒ همان خروجی؛ خطای یک عکس دسته را نمی‌خواباند', async () => {
  const n0 = await versionsOf(IMG.plain);
  const r = await S('POST', '/batches', A, { key: 'batch-again-1', image_ids: [IMG.plain, IMG.webp] });
  assert.equal(r.status, 201);
  await jobs.drain();
  const v = (await S('GET', `/batches/${r.body.id}`, A)).body;
  const plain = v.items.find((x) => x.image_id === IMG.plain), webp = v.items.find((x) => x.image_id === IMG.webp);
  assert.deepEqual([plain.status, plain.reused, plain.result_version], ['done', true, 2]);
  assert.equal(await versionsOf(IMG.plain), n0, 'هیچ نسخهٔ تازه‌ای');
  //  WebP روی سرور پردازش‌شدنی نیست: همیشگی، بی تلاشِ دوباره؛ بقیهٔ دسته تمام شد
  assert.deepEqual([webp.status, webp.error, webp.attempts], ['failed', 'unsupported_format', 1]);
  assert.deepEqual([v.progress.done, v.progress.failed, v.progress.percent], [1, 1, 100]);
  //  «فقط ناموفق‌ها» دوباره: همان شکستِ همیشگی، و هنوز یک نسخه برای WebP (اصل)
  const retry = await S('POST', `/batches/${r.body.id}/action`, A, { action: 'retry' });
  assert.deepEqual([retry.status, retry.body.retried], [200, 1]);
  await jobs.drain();
  assert.equal((await S('GET', `/batches/${r.body.id}`, A)).body.progress.failed, 1);
  assert.equal(await versionsOf(IMG.webp), 1);
  //  تصویرِ تبلیغ: برچسبِ promo و هرگز جای عکسِ کالا، حتی پس از تأیید
  const ad = await S('POST', '/batches', A, { key: 'batch-ad-001', image_ids: [IMG.plain], preset: 'ad' });
  await jobs.drain();
  const adItem = (await S('GET', `/batches/${ad.body.id}`, A)).body.items[0];
  assert.equal(adItem.label, 'promo');
  assert.equal((await S('POST', `/images/${IMG.plain}/versions/${adItem.result_version}/review`, A, { decision: 'approve', confirm_risk: true })).status, 200);
  const sel = await I('POST', '/select', A, { product_id: 'p1', source: 'shop', image_id: IMG.plain, version: adItem.result_version });
  assert.equal(sel.status, 409);
  assert.equal(sel.body.error.code, 'promo_not_product_photo');
});

test('توقف، ادامه و لغو: دستهٔ متوقف برداشته نمی‌شود؛ لغو هنگامِ کار هیچ نسخه‌ای نمی‌سازد', async () => {
  const r = await S('POST', '/batches', A, { key: 'batch-pause-1', image_ids: [IMG.dark], preset: 'enhance' });
  assert.equal((await S('POST', `/batches/${r.body.id}/action`, A, { action: 'pause' })).body.state, 'paused');
  assert.equal((await S('POST', `/batches/${r.body.id}/action`, A, { action: 'pause' })).body.unchanged, true);
  assert.deepEqual(await jobs.claim(4), []);
  assert.equal((await S('POST', `/batches/${r.body.id}/action`, A, { action: 'resume' })).body.state, 'active');
  await jobs.drain();
  assert.equal((await S('GET', `/batches/${r.body.id}`, A)).body.progress.done, 1);
  assert.equal((await S('POST', `/batches/${r.body.id}/action`, A, { action: 'explode' })).status, 400);
  //  لغو درست هنگامِ پردازش: کار تمام می‌شود ولی ثبت می‌بیند دسته لغو شده
  const n0 = await versionsOf(IMG.yellow);
  const c = await S('POST', '/batches', A, { key: 'batch-cancel-1', image_ids: [IMG.yellow], preset: 'enhance', options: { sharpen: false } });
  jobs._setIsolate(async (task) => {
    await jobs.batchAction(A.shopId, A.user.id, c.body.id, 'cancel');
    return studio.render(task.buf, task.info, task.request, task.maxBytes);
  });
  try { await jobs.drain(); } finally { jobs._setIsolate(null); }
  const v = (await S('GET', `/batches/${c.body.id}`, A)).body;
  assert.deepEqual([v.state, v.progress.cancelled, v.progress.done], ['cancelled', 1, 0]);
  assert.equal(await versionsOf(IMG.yellow), n0);
  assert.equal((await S('POST', `/batches/${c.body.id}/action`, A, { action: 'retry' })).body.error.code, 'bad_state');
});

test('IMG-PRO-14: هزار عکس با چند کارگر — همزمانیِ محدود، برداشتنِ جدا، پیشرفتِ دقیق، تلاشِ دوباره و اجارهٔ گم‌شده بی نسخهٔ تکراری', async () => {
  //  هزار عکسِ دکانِ «ب» روی همان یک بلاب (ردیف‌های واقعیِ images/image_versions)
  const src = await one('SELECT * FROM images WHERE id=$1', [IMG.foreign]);
  const blob = (await one('SELECT blob_hash FROM image_versions WHERE image_id=$1 AND version=1', [IMG.foreign])).blob_hash;
  const ids = Array.from({ length: 1000 }, () => 'img_' + ulid());
  const t = now();
  for (let k = 0; k < ids.length; k += 250) {
    const part = ids.slice(k, k + 250);
    await query(
      `INSERT INTO images (id, space, owner_shop_id, status, role, rev, created_at, updated_at)
       SELECT x, 'shop', $2, 'APPROVED', 'main', nextval('image_bank_rev'), $3, $3 FROM unnest($1::text[]) AS x`, [part, src.owner_shop_id, t]);
    await query(
      `INSERT INTO image_versions (image_id, version, kind, parent, blob_hash, review, created_at)
       SELECT x, 1, 'original', 0, $2, 'approved', $3 FROM unnest($1::text[]) AS x`, [part, blob, t]);
  }
  const file = await one('SELECT data FROM image_blobs WHERE hash=$1', [blob]);
  const one0 = studio.render(Buffer.from(file.data), bytes.inspect(Buffer.from(file.data)), { preset: 'enhance' }, 600 * 1024);
  const r = await S('POST', '/batches', B, { key: 'batch-thousand', image_ids: ids, preset: 'enhance' });
  assert.equal(r.status, 201, JSON.stringify(r.body).slice(0, 300));
  assert.equal(r.body.total, 1000);
  //  چند «نمونهٔ سرور» هم‌زمان برمی‌دارند: هیچ کاری دو بار
  const claims = await Promise.all([jobs.claim(40), jobs.claim(40), jobs.claim(40)]);
  const seen = claims.flat().map((j) => j.id);
  assert.equal(new Set(seen).size, seen.length, 'برداشتنِ جدا');
  assert.equal(seen.length, 120);
  //  اجارهٔ گم‌شده: کارگرِ کند پس از تمام شدنِ اجاره‌اش ثبت نمی‌کند؛ دیگری کار را دوباره برمی‌دارد
  await query(`UPDATE image_studio_jobs SET lease_until=0 WHERE id = ANY($1::text[])`, [seen]);
  let active = 0, peak = 0, calls = 0;
  const flaky = new Set(ids.filter((_, i) => i % 10 === 0));
  jobs._setIsolate(async (task) => {
    calls++;
    active++; peak = Math.max(peak, active);
    await new Promise((res) => setTimeout(res, 1));
    active--;
    if (flaky.delete(task.image_id)) {
      //  یکی از هر ده عکس بارِ اول شکستِ گذرا دارد
      throw Object.assign(new Error('timeout'), { code: 'timeout' });
    }
    return { buf: one0.buf, width: one0.width, height: one0.height, derivatives: one0.derivatives, report: one0.report };
  });
  try {
    await jobs.drain({ timeoutMs: 600000 });
    const mid = (await S('GET', `/batches/${r.body.id}`, B)).body.progress;
    assert.equal(mid.done + mid.queued + mid.running + mid.failed + mid.cancelled, 1000, 'پیشرفتِ دقیق از خودِ ردیف‌ها');
    assert.ok(mid.queued > 0, 'شکستِ گذرا ⇒ دوباره در صف با backoff');
    const retried = await many(`SELECT error, next_at FROM image_studio_jobs WHERE batch_id=$1 AND status='queued'`, [r.body.id]);
    for (const x of retried) assert.ok(x.error === 'timeout' && Number(x.next_at) > now(), JSON.stringify(x));
    await query(`UPDATE image_studio_jobs SET next_at=0 WHERE batch_id=$1 AND status='queued'`, [r.body.id]);
    await jobs.drain({ timeoutMs: 600000 });
  } finally { jobs._setIsolate(null); }
  assert.ok(peak <= jobs.LIMITS.WORKERS, `همزمانی ${peak} ≤ ${jobs.LIMITS.WORKERS}`);
  const fin = (await S('GET', `/batches/${r.body.id}`, B)).body;
  assert.deepEqual([fin.progress.done, fin.progress.failed, fin.progress.percent], [1000, 0, 100]);
  assert.equal(fin.items.length, jobs.LIMITS.ITEMS_PAGE);
  assert.ok(fin.next);
  assert.equal((await S('GET', `/batches/${r.body.id}?after=${fin.next}`, B)).body.items.length, jobs.LIMITS.ITEMS_PAGE);
  //  هر عکس دقیقاً یک نسخهٔ پردازش‌شده — نه تلاشِ دوباره، نه اجارهٔ گم‌شده نسخهٔ دوم نساخت
  const per = await many(
    `SELECT image_id, COUNT(*)::int AS n FROM image_versions WHERE image_id = ANY($1::text[]) AND kind='processed' GROUP BY image_id`, [ids]);
  assert.equal(per.length, 1000);
  assert.ok(per.every((x) => Number(x.n) === 1));
  assert.ok(calls >= 1000 + 100 && calls < 1300, `فراخوان‌ها ${calls}`);   //  هر عکس یک بار + یک شکستِ گذرا برای هر دهمی
  //  سقفِ روزانه (۲۰۰۰): هزارتاییِ دوم درست تا سقف پذیرفته می‌شود، یک عکسِ بیشتر ۴۲۹ و هیچ ردیف
  const second = await S('POST', '/batches', B, { key: 'batch-thousand-2', image_ids: ids, preset: 'catalog' });
  assert.equal(second.status, 201);
  assert.equal((await S('POST', `/batches/${second.body.id}/action`, B, { action: 'cancel' })).body.state, 'cancelled');
  const before = await count('SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1', [B.shopId]);
  assert.equal(before, jobs.LIMITS.DAILY_MAX);
  const over = await S('POST', '/batches', B, { key: 'batch-thousand-3', image_ids: [IMG.foreign], preset: 'catalog' });
  assert.equal(over.status, 429);
  assert.equal(over.body.error.code, 'studio_daily_limit');
  assert.equal(await count('SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1', [B.shopId]), before);
});

test('اجارهٔ ازدست‌رفته در میانهٔ کار ⇒ ثبتِ کارگرِ کند هیچ اثری ندارد', async () => {
  const r = await S('POST', '/batches', A, { key: 'batch-lease-1', image_ids: [IMG.dark], preset: 'catalog', options: { sharpen: false } });
  const n0 = await versionsOf(IMG.dark);
  jobs._setIsolate(async (task) => {
    //  کارگرِ دیگری کار را برداشت (اجارهٔ تازه)
    await query(`UPDATE image_studio_jobs SET lease='other', lease_until=$2 WHERE batch_id=$1`, [r.body.id, now() + 60000]);
    return studio.render(task.buf, task.info, task.request, task.maxBytes);
  });
  try { await jobs.tick(); await new Promise((res) => setTimeout(res, 50)); await jobs.drain({ timeoutMs: 5000 }); } finally { jobs._setIsolate(null); }
  assert.equal(await versionsOf(IMG.dark), n0, 'هیچ نسخه');
  const j = await one('SELECT status, lease, result_version FROM image_studio_jobs WHERE batch_id=$1', [r.body.id]);
  assert.deepEqual([j.status, j.lease, Number(j.result_version)], ['running', 'other', 0]);
  //  اجارهٔ آن دیگری هم تمام شد ⇒ دوباره برداشته و یک بار ثبت می‌شود
  await query(`UPDATE image_studio_jobs SET lease_until=0 WHERE batch_id=$1`, [r.body.id]);
  await jobs.drain();
  assert.equal(await versionsOf(IMG.dark), n0 + 1);
});
