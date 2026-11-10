'use strict';
/**
 * بازبینیِ امنیتیِ دومِ دستهٔ ۴۴ (استودیوی عکسِ AI-02/AI-05 ِ `claude/missing-rows`، مهاجرتِ ۵۹۰؛ ریشهٔ مشترک با IMG-15).
 *
 *   SR44-1  PNG با IHDR ِ دوم: `image-bytes.inspectPng` ابعاد را از IHDR ِ نخست می‌خواند و IHDR ِ دوم را «خراب» نمی‌دانست،
 *           ولی رمزگشاها (`image-codec.decodePng` ِ استودیو و `image-quality.pngLuma` ِ IMG-15) آخرین IHDR را برمی‌داشتند ⇒
 *           فایلِ ۶۲ کیلوبایتیِ «۱۰۰×۱۰۰» در کارگرِ استودیو ۶۴ مگاپیکسل باز می‌کرد (~۳۰۰ مگابایت بیرون از سقفِ heap ِ رشته،
 *           چهار برابرِ `MAX_PIXELS`) و در بارگذاری با نشانِ کیفیت ~۳۲ مگابایت روی حلقهٔ اصلیِ سرور.
 *   SR44-2  سقفِ روزانه (۲۰۰۰) و صفِ باز (۵۰۰۰) ِ `createBatch` بی قفلِ دکان شمرده می‌شد ⇒ چند درخواستِ هم‌زمان هر کدام
 *           شمارِ پیش از دیگری را می‌دید و همه پذیرفته می‌شدند (فقط PostgreSQL؛ PGlite تراکنش‌ها را پشتِ هم می‌برد).
 *   SR44-3  دو کارِ هم‌`digest` (همان عکس، نسخه و تنظیمات در دو دسته) که هم‌زمان برداشته شوند هر دو نسخه می‌ساختند ⇒
 *           «دوباره‌کاری هرگز نسخهٔ تکراری نمی‌سازد» نمی‌ماند (سقفِ ۳۰ نسخهٔ هر عکس با تکرار پر می‌شد).
 *   R12     (پیگیری، `claude/sec-fix-44`) کارِ دستهٔ لغوشده که به `complete` نرسید (فرآیند افتاد ⇒ «در کار» با اجارهٔ تمام‌شده؛
 *           شکستِ گذرای هم‌زمان با لغو ⇒ «در صف») هرگز برداشته نمی‌شد (`claim` فقط دستهٔ `active`) ⇒ تا همیشه در سقفِ ۵۰۰۰.
 *   R11     (پیگیری) سقفِ heap ِ رشته ArrayBuffer را نمی‌بندد ⇒ بودجهٔ بایتِ پیکسلِ بازشده (`IMAGE_STUDIO_DECODE_MB`) میانِ کارگرها.
 *   R10     (پیگیری) خانهٔ CSV ِ سرور (`finance-reports.cell`، همزادِ `license/csv-cell.js`): متنِ فقط-عدد `^-?[0-9]+(\.[0-9]+)?$` (همان
 *           خانهٔ `g` ِ xlsx) فرمول نیست ⇒ عدد می‌ماند؛ هر متنِ دیگرِ آغازشده با = + - @ تب یا CR همان «'».
 *   R18     (پیگیری) گزارشِ خطای دستهٔ بانکِ مرکزی (`image-bank.reportCsv`، CSV ِ مدیر) خانه را بی نگهبانِ فرمول می‌نوشت ⇒ همان `cell`.
 */
process.env.IMAGE_STUDIO_WORKERS = '2';
//  R11: بودجهٔ ۲ مگابایت ⇒ سهمِ هر کار ۱ مگابایت؛ عکسِ ۶۰۰×۴۵۰ ِ آزمون‌های دیگر (۸۱۰ هزار بایت RGB) زیرِ آن می‌ماند
process.env.IMAGE_STUDIO_DECODE_MB = '2';

const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('zlib');
const path = require('path');
const { execFileSync } = require('child_process');
const h = require('./helpers');
const F = require('./image-fixtures');
const bytes = require('../src/lib/image-bytes');
const codec = require('../src/lib/image-codec');
const quality = require('../src/lib/image-quality');
const studio = require('../src/lib/image-studio');
const jobs = require('../src/lib/image-studio-jobs');
const { ulid } = require('../src/lib/ulid');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const finance = require('../src/lib/finance-reports');
const bank = require('../src/lib/image-bank');

let root = '';
let A; let C;

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
const upload = (who, buf, pid) => h.raw('POST', `/api/shop/images/?product_id=${pid}`, buf, { token: who.token });
const jpg = (w = 600, h0 = 450) => F.jpegImage(w, h0, F.scene(w, h0), { quality: 90, color: true });

/** PNG ِ خاکستری با IHDR ِ دوم: سرآیندِ نخست «۱۰۰×۱۰۰»، رمزگشا `bw×bh` ِ صفر (zlib ~۱۰۰۰ برابر) */
function twoHeaderPng(bw, bh, first = 100) {
  const ihdr = (w, hh) => { const b = Buffer.alloc(13); b.writeUInt32BE(w, 0); b.writeUInt32BE(hh, 4); b[8] = 8; b[9] = 0; return b; };
  return Buffer.concat([
    Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]),
    F.pngChunk('IHDR', ihdr(first, first)), F.pngChunk('IHDR', ihdr(bw, bh)),
    F.pngChunk('IDAT', zlib.deflateSync(Buffer.alloc((bw + 1) * bh), { level: 9 })),
    F.pngChunk('IEND', Buffer.alloc(0)),
  ]);
}

/** هر `zlib.inflateSync` (همان شیءِ ماژول که رمزگشاها صدا می‌زنند) ⇒ سقفِ خروجیِ خواسته‌شده */
async function inflates(fn) {
  const real = zlib.inflateSync, seen = [];
  zlib.inflateSync = (buf, o) => { seen.push((o && o.maxOutputLength) || 0); return real(buf, o); };
  try { await fn(); } finally { zlib.inflateSync = real; }
  return seen;
}

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]);
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
  A = await shopOwner('الف');
  C = await shopOwner('ج');
  await setFlag('all', 'shop.image_bank', true);
  await setFlag(A.shopId, 'shop.image_studio', true);
  await setFlag(C.shopId, 'shop.image_studio', true);
});
test.after(async () => { jobs._setIsolate(null); await h.stop(); });
test.beforeEach(() => resetAll());

test('SR44-1: PNG ِ دو-IHDR نه بارگذاری می‌شود، نه رمزگشا ابعادِ دیگرش را باز می‌کند (استودیو و IMG-15)', async () => {
  const bomb = twoHeaderPng(8000, 8000);
  assert.ok(bomb.length < 80 * 1024, `فایلِ کوچک: ${bomb.length}`);
  //  درِ بارگذاری: سرآیندِ دوم با ابعادِ دیگر «خراب» است (همان قاعدهٔ JPEG ِ دو-SOF)
  assert.throws(() => bytes.inspect(bomb), (e) => e.code === 'image_corrupt' && /png-multi-ihdr/.test(e.message));
  const up = await upload(A, bomb, 'bomb-1');
  assert.equal(up.status, 400, JSON.stringify(up.body));
  assert.equal(up.body.error.code, 'image_corrupt');
  assert.equal(Number((await one(`SELECT COUNT(*)::int AS n FROM images WHERE owner_shop_id=$1`, [A.shopId])).n), 0);

  //  بلابِ پیشین (پیش از این درِ بارگذاری) هم: رمزگشا IHDR ِ دوم را نمی‌پذیرد و هیچ zlib ای باز نمی‌کند
  const info = { mime: 'image/png', width: 100, height: 100, orientation: 0 };
  const m0 = process.memoryUsage().arrayBuffers;
  let out;
  const studioInflates = await inflates(() => { out = codec.decode(bomb, info); });
  const grew = process.memoryUsage().arrayBuffers - m0;
  assert.equal(out.reason, 'undecodable');
  assert.deepEqual(studioInflates, [], 'استودیو: هیچ inflate');
  assert.ok(grew < 16 * 1024 * 1024, `حافظهٔ بیرون از heap: ${Math.round(grew / 1048576)} مگابایت`);
  assert.throws(() => studio.render(bomb, info, { preset: 'catalog' }, 1 << 20), (e) => e.code === 'unsupported_format');
  let q;
  const lumaInflates = await inflates(() => { q = quality.analyze(twoHeaderPng(5600, 5600, 400), { ...info, width: 400, height: 400 }); });
  assert.deepEqual(lumaInflates, [], 'IMG-15: هیچ inflate روی حلقهٔ اصلی');
  assert.ok(q.flags.includes('quality_unmeasured'));

  //  PNG ِ درست همان دیروز: یک inflate به اندازهٔ دقیقِ خام، رمزگشایی و بارگذاری
  const good = F.pngImage(120, 90, (x, y) => [x * 2, y * 2, 128], { mode: 'rgb' });
  let dec;
  assert.deepEqual(await inflates(() => { dec = codec.decode(good, bytes.inspect(good)); }), [(120 * 3 + 1) * 90]);
  assert.deepEqual([dec.w, dec.h], [120, 90]);
  assert.equal((await upload(A, good, 'good-1')).status, 201);
});

test('SR44-2: سقفِ روزانهٔ استودیو زیرِ درخواست‌های هم‌زمان هم سقف است (قفلِ دکان)', async () => {
  const id = (await upload(A, jpg(), 'cap-src')).body.id;
  const blob = (await one('SELECT blob_hash FROM image_versions WHERE image_id=$1 AND version=1', [id])).blob_hash;
  const ids = Array.from({ length: 700 }, () => 'img_' + ulid());
  const t = now();
  await query(
    `INSERT INTO images (id, space, owner_shop_id, status, role, rev, created_at, updated_at)
     SELECT x, 'shop', $2, 'APPROVED', 'main', nextval('image_bank_rev'), $3, $3 FROM unnest($1::text[]) AS x`, [ids, A.shopId, t]);
  await query(
    `INSERT INTO image_versions (image_id, version, kind, parent, blob_hash, review, created_at)
     SELECT x, 1, 'original', 0, $2, 'approved', $3 FROM unnest($1::text[]) AS x`, [ids, blob, t]);
  //  چهار دستهٔ ۷۰۰تایی با هم: سقفِ ۲۰۰۰ ⇒ فقط دو پذیرفته، بقیه ۴۲۹ ِ `studio_daily_limit`
  const runs = await Promise.allSettled([1, 2, 3, 4].map((k) => jobs.createBatch(A.shopId, A.user.id, { key: `cap-race-${k}`, image_ids: ids, preset: 'catalog' })));
  const ok = runs.filter((r) => r.status === 'fulfilled').length;
  const why = runs.filter((r) => r.status === 'rejected').map((r) => r.reason.code);
  const total = Number((await one('SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1', [A.shopId])).n);
  assert.ok(total <= jobs.LIMITS.DAILY_MAX, `کارهای امروزِ دکان ${total} ≤ ${jobs.LIMITS.DAILY_MAX} (پذیرفته: ${ok})`);
  assert.equal(ok, 2);
  assert.deepEqual(why, ['studio_daily_limit', 'studio_daily_limit']);
  //  همان کلید دوباره ⇒ همان دسته (قفل یک‌بارگی را عوض نکرد)
  const first = runs.findIndex((r) => r.status === 'fulfilled') + 1;
  const again = await jobs.createBatch(A.shopId, A.user.id, { key: `cap-race-${first}`, image_ids: ids, preset: 'catalog' });
  assert.equal(again.existing, true);
  //  همان کلید در دکانِ دیگر ⇒ دستهٔ خودِ همان دکان، نه دستهٔ «الف» (یکتاییِ (shop_id, idem_key))
  const cid = (await upload(C, jpg(), 'cap-c')).body.id;
  const other = await jobs.createBatch(C.shopId, C.user.id, { key: `cap-race-${first}`, image_ids: [cid], preset: 'catalog' });
  assert.equal(other.existing, false);
  assert.notEqual(other.id, again.id);
  await jobs.batchAction(C.shopId, C.user.id, other.id, 'cancel');
});

test('SR44-3: دو کارِ هم‌digest ِ هم‌زمان (دو دسته، همان عکس و تنظیمات) فقط یک نسخه می‌سازند', async () => {
  //  صفِ آزمونِ پیشین (دکانِ «الف»، سرخ یا سبز) در این کارگر برداشته نشود
  await query(`UPDATE image_studio_batches SET state='cancelled' WHERE shop_id=$1`, [A.shopId]);
  const id = (await upload(C, jpg(), 'dup-1')).body.id;
  const versions = async () => Number((await one(`SELECT COUNT(*)::int AS n FROM image_versions WHERE image_id=$1 AND kind='processed'`, [id])).n);
  const b1 = await jobs.createBatch(C.shopId, C.user.id, { key: 'dup-batch-1', image_ids: [id], preset: 'enhance' });
  const b2 = await jobs.createBatch(C.shopId, C.user.id, { key: 'dup-batch-2', image_ids: [id], preset: 'enhance' });
  let active = 0, peak = 0;
  jobs._setIsolate(async (task) => {
    active++; peak = Math.max(peak, active);
    await new Promise((res) => setTimeout(res, 30));
    active--;
    return studio.render(task.buf, task.info, task.request, task.maxBytes);
  });
  try { await jobs.drain({ timeoutMs: 30000 }); } finally { jobs._setIsolate(null); }
  assert.equal(peak, 2, 'هر دو کار با هم برداشته شدند');
  assert.equal(await versions(), 1, 'یک نسخهٔ پردازش‌شده، نه دو');
  const rows = await Promise.all([b1, b2].map((b) => one('SELECT status, result_version, reused FROM image_studio_jobs WHERE batch_id=$1', [b.id])));
  assert.deepEqual(rows.map((r) => r.status), ['done', 'done']);
  assert.equal(Number(rows[0].result_version), Number(rows[1].result_version));
  assert.deepEqual(rows.map((r) => !!r.reused).sort(), [false, true]);
});

test('R12: کارِ بازِ دستهٔ لغوشده پس از افتادنِ فرآیند بسته می‌شود؛ اجارهٔ زنده، دستهٔ فعال و متوقف دست نمی‌خورند', async () => {
  const D = await shopOwner('د');
  await setFlag(D.shopId, 'shop.image_studio', true);
  const img = [];
  for (const k of [1, 2, 3]) img.push((await upload(D, jpg(), `r12-${k}`)).body.id);
  const K = await jobs.createBatch(D.shopId, D.user.id, { key: 'r12-cancelled', image_ids: img, preset: 'enhance' });
  const L = await jobs.createBatch(D.shopId, D.user.id, { key: 'r12-active', image_ids: [img[0]], preset: 'catalog' });
  const P = await jobs.createBatch(D.shopId, D.user.id, { key: 'r12-paused', image_ids: [img[1]], preset: 'ad' });
  const t = now();
  const set = (batch, image, status, lease, until) => query(
    `UPDATE image_studio_jobs SET status=$3, lease=$4, lease_until=$5, attempts=1 WHERE batch_id=$1 AND image_id=$2`,
    [batch, image, status, lease, until]);
  //  هر سه کارِ K «در کار» هنگامِ لغو (لغو فقط «در صف» را می‌بندد)
  for (const x of img) await set(K.id, x, 'running', `l_${x}`, t + 60000);
  await jobs.batchAction(D.shopId, D.user.id, K.id, 'cancel');
  await set(K.id, img[0], 'running', 'l_dead', t - 1);           //  فرآیند افتاد: اجاره تمام شد، `complete` هرگز
  await query(`UPDATE image_studio_jobs SET status='queued', lease='', lease_until=0, next_at=0, error='timeout'
    WHERE batch_id=$1 AND image_id=$2`, [K.id, img[1]]);          //  `failOrRetry` ِ گذرا پس از لغو ⇒ دوباره «در صف»
  await set(K.id, img[2], 'running', 'l_live', t + 60000);       //  کارگرِ زنده هنوز روی آن است
  await set(L.id, img[0], 'running', 'l_active', t + 60000);     //  دستهٔ فعال، اجارهٔ زنده
  await jobs.batchAction(D.shopId, D.user.id, P.id, 'pause');
  await set(P.id, img[1], 'running', 'l_paused', t - 1);         //  دستهٔ متوقف: «ادامه» دوباره برمی‌دارد
  const openOf = async () => Number((await one(
    `SELECT COUNT(*)::int AS n FROM image_studio_jobs WHERE shop_id=$1 AND status IN ('queued','running')`, [D.shopId])).n);
  const row = (batch, image) => one('SELECT status, lease FROM image_studio_jobs WHERE batch_id=$1 AND image_id=$2', [batch, image]);
  assert.equal(await openOf(), 5);

  assert.deepEqual(await jobs.claim(4), [], 'هیچ کاری برداشته نشد');
  assert.deepEqual(await row(K.id, img[0]), { status: 'cancelled', lease: '' }, 'اجارهٔ تمام‌شدهٔ دستهٔ لغوشده ⇒ لغو');
  assert.deepEqual(await row(K.id, img[1]), { status: 'cancelled', lease: '' }, '«در صف» ِ دستهٔ لغوشده ⇒ لغو');
  assert.deepEqual(await row(K.id, img[2]), { status: 'running', lease: 'l_live' }, 'اجارهٔ زنده دست نخورد');
  assert.deepEqual(await row(L.id, img[0]), { status: 'running', lease: 'l_active' }, 'دستهٔ فعال دست نخورد');
  assert.deepEqual(await row(P.id, img[1]), { status: 'running', lease: 'l_paused' }, 'دستهٔ متوقف دست نخورد');
  assert.equal(await openOf(), 3, 'در سقفِ «کارِ باز» دیگر شمرده نمی‌شوند');
  const view = await jobs.batchView(D.shopId, K.id);
  assert.deepEqual([view.progress.cancelled, view.progress.running], [2, 1]);

  //  کارگرِ زنده هم افتاد ⇒ جاروی بعدی؛ دوباره ⇒ هیچ
  await set(K.id, img[2], 'running', 'l_live', now() - 1);
  assert.equal(await jobs.sweep(), 1);
  assert.equal(await jobs.sweep(), 0);
  assert.deepEqual([(await jobs.batchView(D.shopId, K.id)).progress.cancelled], [3]);
  //  «ادامه» ِ دستهٔ متوقف ⇒ همان `claim` اجارهٔ تمام‌شده را دوباره برمی‌دارد (کار گم نمی‌شود)
  await jobs.batchAction(D.shopId, D.user.id, P.id, 'resume');
  const again = await jobs.claim(4);
  assert.deepEqual(again.map((j) => [j.batch_id, j.image_id]), [[P.id, img[1]]]);
  for (const b of [L, P]) await jobs.batchAction(D.shopId, D.user.id, b.id, 'cancel');
  await query(`UPDATE image_studio_jobs SET lease_until=0 WHERE shop_id=$1 AND status='running'`, [D.shopId]);
  await jobs.sweep();
  assert.equal(await openOf(), 0);
});

test('R11: بودجهٔ بایتِ پیکسلِ بازشده میانِ کارگرها — پیش‌فرض همان دیروز، بیشتر از سهم ⇒ `memory_budget` بی رشته و بی تلاشِ دوباره', async () => {
  const limits = (env) => JSON.parse(execFileSync(process.execPath, ['-e',
    "process.stdout.write(JSON.stringify(require('./src/lib/image-studio-jobs').LIMITS))"], {
    cwd: path.join(__dirname, '..'), encoding: 'utf8',
    env: { ...process.env, IMAGE_STUDIO_WORKERS: '', IMAGE_STUDIO_DECODE_MB: '', ...env },
  }));
  const MIB = 1048576, MAX = codec.MAX_PIXELS * 4;                //  بزرگ‌ترین عکسِ رمزگشاشدنی، RGBA
  for (const [env, workers, mb] of [[{}, 2, 256], [{ IMAGE_STUDIO_WORKERS: '4' }, 4, 256], [{ IMAGE_STUDIO_WORKERS: '1' }, 1, 256],
    [{ IMAGE_STUDIO_WORKERS: '4', IMAGE_STUDIO_DECODE_MB: '96' }, 4, 96]]) {
    const L = limits(env);
    assert.equal(L.WORKERS, workers, JSON.stringify(env));
    assert.equal(L.DECODE_BYTES, Math.floor((mb * MIB) / workers), JSON.stringify(env));
    assert.ok(L.DECODE_BYTES * L.WORKERS <= mb * MIB, 'جمعِ همهٔ کارگرها ≤ بودجه');
    if (mb === 256) assert.ok(L.DECODE_BYTES >= MAX, 'پیش‌فرض: ۱۶ مگاپیکسل RGBA همان دیروز');
  }
  assert.equal(jobs.LIMITS.DECODE_BYTES, MIB, 'همین پرونده: ۲ مگابایت ÷ ۲ کارگر');

  //  JPEG ِ ۸۰۰×۶۰۰ (۱٫۴۴ مگابایت RGB) و PNG ِ ۵۲۰×۵۲۰ (رمزگشا همیشه RGBA ⇒ ۱٫۰۸) بیشتر از سهم؛ JPEG ِ ۵۲۰×۵۲۰ (۰٫۸۱) نه
  const big = (await upload(C, jpg(800, 600), 'r11-big')).body.id;
  const wide = (await upload(C, F.pngImage(520, 520, F.scene(520, 520), { mode: 'rgb' }), 'r11-png')).body.id;
  const fits = (await upload(C, jpg(520, 520), 'r11-fits')).body.id;
  for (const x of [big, wide, fits]) assert.ok(x, 'بارگذاری');
  const b = await jobs.createBatch(C.shopId, C.user.id, { key: 'r11-budget', image_ids: [big, wide, fits], preset: 'enhance' });
  const sent = [];
  jobs._setIsolate(async (task) => { sent.push(task.image_id); return studio.render(task.buf, task.info, task.request, task.maxBytes); });
  try { await jobs.drain({ timeoutMs: 30000 }); } finally { jobs._setIsolate(null); }
  const rows = await Promise.all([big, wide, fits].map((x) => one(
    'SELECT status, error, attempts FROM image_studio_jobs WHERE batch_id=$1 AND image_id=$2', [b.id, x])));
  assert.deepEqual(rows.map((r) => [r.status, r.error, Number(r.attempts)]),
    [['failed', 'memory_budget', 1], ['failed', 'memory_budget', 1], ['done', '', 1]]);
  assert.deepEqual(sent, [fits], 'فقط عکسِ زیرِ سهم به رشته رفت');
});

test('R10: خانهٔ CSV ِ سرور — متنِ فقط-عدد عدد می‌ماند، هر شکلِ دیگر «\'» (همان CSV ِ «خروجیِ مالی» ِ پنلِ مرکزی)', async () => {
  const plain = ['-500', '-500.00', '-0', '-0.5'];
  const prefixed = { '-2+3': "'-2+3", '-1e5': "'-1e5", '+500': "'+500", '-.5': "'-.5", '- 500': "'- 500", '-500.': "'-500.",
    '-150.00%': "'-150.00%", '-1,000': `"'-1,000"`, '=500': "'=500", '@500': "'@500", '\t500': "'\t500", '-500\n': `"'-500\n"` };
  for (const v of plain) assert.equal(finance.cell(v), v, `فقط-عدد: ${v}`);
  for (const [v, want] of Object.entries(prefixed)) assert.equal(finance.cell(v), want, `فرمول‌نما: ${JSON.stringify(v)}`);
  assert.equal(finance.cell(-500), '-500');

  //  همان قاعده در تنها CSV ِ سرور که `cell` را می‌خواند: GET /api/admin/finance/export
  const t = now(), ids = { '-500.00': 'r10-plain', '-2+3': 'r10-minus', '=HYPERLINK("http://evil.example/","x")': 'r10-link' };
  for (const [tenant, id] of Object.entries(ids)) {
    await query(`INSERT INTO sub_payments (id, app, tenant_kind, tenant_id, amount, currency, method, receipt_no, paid_at, created_at)
                 VALUES ($1,'shop','shop',$2,500,'AFN','cash',$1,$3,$3)`, [id, tenant, t]);
  }
  const r = await h.get('/api/admin/finance/export?dataset=payments', { token: root });
  assert.equal(r.status, 200, JSON.stringify(r.body).slice(0, 300));
  const lines = r.body.csv.replace(/^﻿/, '').split('\r\n');
  const row = (id) => lines.find((l) => l.startsWith(`${id},`));
  assert.equal(row('r10-plain').split(',')[4], '-500.00', 'متنِ فقط-عدد بی «\'»');
  assert.equal(row('r10-minus').split(',')[4], "'-2+3");
  assert.ok(row('r10-link').includes(`"'=HYPERLINK(""http://evil.example/"",""x"")"`), row('r10-link'));
});

test('R18: گزارشِ خطای دستهٔ بانک (CSV ِ مدیر) هر خانه را از همان `finance-reports.cell` می‌گذراند؛ ستون‌ها همان', async () => {
  const LINK = '=HYPERLINK("http://evil.example/","x")', LINK_CSV = `"'=HYPERLINK(""http://evil.example/"",""x"")"`;
  const lines = (csv) => csv.replace(/^﻿/, '').split('\r\n');
  //  هر خانهٔ ردیف و فایل، بی پایگاه
  const rep = {
    rows: [
      { row_no: 7, status: 'failed', error: '=1+1', image_id: '@img', product_id: LINK, candidates: ['-2+3', 'bp_1'] },
      { row_no: 8, status: 'ok', error: '', image_id: 'i', product_id: 'p', candidates: [] },
      { row_no: 9, status: 'needs_review', error: '', image_id: '', product_id: '-500.00', candidates: [] },
    ],
    files: { failed: [{ name: 'bad.jpg', error: '+cmd' }] },
  };
  assert.deepEqual(lines(bank.reportCsv(rep)), [
    'row_no,status,error,image_id,product_id,candidates',
    `7,failed,'=1+1,'@img,${LINK_CSV},'-2+3|bp_1`,
    '9,needs_review,,,-500.00,',
    "file:bad.jpg,failed,'+cmd,,,",
    '',
  ]);

  //  همان درِ پنل: شناسهٔ کالای «=HYPERLINK(…)» در CSV ِ بارگذاری‌شده
  await setFlag('all', 'image_bank.central', true);
  const AD = (method, p, body) => h.api(method, '/api/admin/image-bank' + p, { token: root, body });
  const b = await AD('POST', '/batches', { license_source: 'کاتالوگِ تأمین‌کننده', license_status: 'licensed', commercial_use: true });
  assert.equal(b.status, 201, JSON.stringify(b.body));
  const put = await h.raw('PUT', `/api/admin/image-bank/batches/${b.body.id}/files/r18.jpg`, jpg(), { token: root });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  const rows = await AD('POST', `/batches/${b.body.id}/rows`, { csv: `product_id,image_path\n"${LINK.replace(/"/g, '""')}",r18.jpg\n` });
  assert.equal(rows.status, 200, JSON.stringify(rows.body));
  assert.deepEqual(rows.body.rows.map((r) => [r.status, r.error, r.product_id]), [['failed', 'product_not_found', '']]);
  const report = async () => lines((await h.download(`/api/admin/image-bank/batches/${b.body.id}/report?format=csv`, { token: root })).buffer.toString('utf8'));
  let out = await report();
  assert.equal(out[0], 'row_no,status,error,image_id,product_id,candidates');
  assert.ok(out.every((l) => !l.includes('HYPERLINK')), 'شناسهٔ ناشناس در گزارش برنمی‌گردد');
  //  مقدارِ ذخیره‌شده (هر راهی که روزی آن را بنویسد) هم در CSV فرمول نمی‌شود
  await query(`UPDATE image_batch_rows SET product_id=$2, error='-2+3', candidates='["@x"]' WHERE batch_id=$1`, [b.body.id, LINK]);
  out = await report();
  const row = out.find((l) => /^\d+,failed,/.test(l));
  assert.ok(row && row.endsWith(`,'-2+3,,${LINK_CSV},'@x`), JSON.stringify(out));
  assert.ok(out.every((l) => !/(^|,)[=+\-@]/.test(l.replace(/"[^"]*(""[^"]*)*"/g, 'Q'))), JSON.stringify(out));
});
