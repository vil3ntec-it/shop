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
 */
process.env.IMAGE_STUDIO_WORKERS = '2';

const test = require('node:test');
const assert = require('node:assert/strict');
const zlib = require('zlib');
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
