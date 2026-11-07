'use strict';
/**
 * ابزارِ جابه‌جاییِ عکس (`lib/image-store-move.js`، `scripts/image-store-migrate.js`) و مهاجرتِ ۲۵۵.
 *
 *   ۱) بی انبارِ پیکربندی‌شده هیچ کاری نمی‌کند؛ `status` شمارِ هر جدول.
 *   ۲) copy ادامه‌پذیر است: اجرای بریده (`limit`)، شکستِ بازخوانی پس از نوشتنِ شیء، و اجرای دوباره ⇒ همهٔ ردیف‌ها یک بار،
 *      شمارِ شیءها = شمارِ ردیف‌های کلیددار (کلیدِ قطعی ⇒ شیءِ یتیمِ اجرای بریده نمی‌ماند)؛ اجرای سوم هیچ.
 *      ردیفِ غیرِ عکس جابه‌جا نمی‌شود؛ ردیفی که میانِ نوشتنِ شیء و ثبت عوض شد دست نمی‌خورد و شیءش پاک می‌شود.
 *   ۳) ⛔ `data` تا `purge --confirm` می‌ماند؛ purge فقط ردیفی را که شیءش همین دم همان بایت است خالی می‌کند.
 *   ۴) gc فقط شیءِ بی‌ردیف و کهنه‌تر از مهلت را، و فقط با `apply`.
 *   ۵) rollback فقط در حالتِ db با `IMAGE_STORE_READ`: همهٔ بایت‌ها به پایگاه، همان بایت. پایینِ ۲۵۵ پیش از آن خطا
 *      می‌دهد و هیچ تغییری نمی‌دهد؛ پس از آن ستون‌ها و ردیفِ `schema_migrations` می‌روند و مهاجرت دوباره می‌نشیند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('fs');
const os = require('os');
const path = require('path');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, tx } = require('../src/db');
const config = require('../src/config');
const store = require('../src/lib/image-store');
const A = require('../src/lib/image-store-adapters');
const move = require('../src/lib/image-store-move');
const photos = require('../src/lib/product-photos');
const sfm = require('../src/lib/storefront-media');
const { putBlob } = require('../src/lib/image-originals');

const DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'img-move-'));
const prev = { ...config.imageStore };
const setMode = (mode, read = '') => Object.assign(config.imageStore, { mode, read, dir: DIR });

test.before(async () => { await h.start(); });
test.after(async () => {
  Object.assign(config.imageStore, prev);
  store.useBackend(null);
  await h.stop();
  fs.rmSync(DIR, { recursive: true, force: true });
});

/** انبارِ دیسک با قلاب: `hooks.afterPut(key)` و `hooks.failGet` برای شبیه‌سازیِ بریدگی */
const hooks = { afterPut: null, failGet: 0 };
const disk = A.disk({ dir: DIR });
const flaky = {
  kind: 'disk',
  async put(k, b, m) { await disk.put(k, b, m); if (hooks.afterPut) await hooks.afterPut(k); },
  async get(k) { if (hooks.failGet > 0) { hooks.failGet--; throw new Error('شبکه برید'); } return disk.get(k); },
  del: (k) => disk.del(k),
  list: () => disk.list(),
};
const objectCount = async () => (await disk.list()).length;
const keyed = async () => {
  let n = 0;
  for (const t of move.TABLES) n += (await one(`SELECT COUNT(*)::int AS n FROM ${t.table} WHERE store_key <> ''`)).n;
  return n;
};

const orig = new Map();
let shops = [];

test('پیش از انبار: status، و copy بی انبار ⇒ خطا بی اثر', async () => {
  setMode('db');
  store.useBackend(null);
  for (const label of ['یک', 'دو']) {
    const u = await h.newUser(label, 'shop');
    const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
    assert.equal(made.status, 201);
    shops.push({ id: made.body.shop.id, user: u.user ? u.user.id : '' });
  }
  for (const [i, s] of shops.entries()) {
    for (let j = 0; j < 4; j++) {
      const buf = j % 2 ? F.png(30 + j, 30 + i) : F.jpeg(40 + j, 40 + i, { seed: 10 * i + j });
      await photos.put({ app: 'shop', accountId: s.id }, `p${j}`, buf);
      orig.set(`${s.id}/p${j}`, buf);
    }
    await sfm.put(s.id, 'logo', F.jpeg(256, 256, { seed: 70 + i }), 'u');
    await photos.inboxPut({ app: 'shop', accountId: s.id }, Buffer.concat([F.webp(20, 20), Buffer.alloc(150, i)]), { pair: '654321' });
  }
  await photos.put({ app: 'station', accountId: 'st-1' }, 'x1', F.jpeg(33, 33, { seed: 99 }));
  orig.set('st-1/x1', F.jpeg(33, 33, { seed: 99 }));
  const blob = F.png(50, 50, { seed: 5 });
  await tx((c) => putBlob(c, blob, { mime: 'image/png', width: 50, height: 50 }));
  //  ردیفِ کهنه با بایتِ غیرِ عکس (پیش از سنجشِ امروز): جابه‌جا نمی‌شود
  await query(`INSERT INTO product_photos (app, account_id, product_id, data, hash, size, rev, updated_at)
               VALUES ('shop',$1,'junk',$2,'j',300,nextval('product_photo_rev'),1)`, [shops[0].id, Buffer.alloc(300, 0x41)]);

  const st = await move.status({});
  assert.equal(st.mode, 'db');
  assert.equal(st.backend, null);
  assert.deepEqual(st.tables.product_photos, { dbOnly: 10, copied: 0, storeOnly: 0, dbBytes: st.tables.product_photos.dbBytes });
  assert.equal(st.tables.storefront_media.dbOnly, 2);
  assert.equal(st.tables.photo_inbox.dbOnly, 2);
  assert.equal(st.tables.image_blobs.dbOnly, 1);
  assert.equal(st.tables.order_issue_photos.dbOnly, 0);
  await assert.rejects(move.copy({}), /انبارِ شیء پیکربندی نشده/);
  await assert.rejects(move.purge({ confirm: true }), /purge فقط/);
  await assert.rejects(move.status({ tables: ['station_chat_media'] }), /جدولِ ناشناخته/, '⛔ جدولِ پمپ در فهرست نیست');
});

test('copy: بریده، شکستِ بازخوانی، تغییرِ هم‌زمان و ادامه ⇒ یک بار، بی شیءِ یتیم', async () => {
  setMode('disk');
  store.useBackend(flaky);
  const first = await move.copy({ tables: ['product_photos'], limit: 3, batch: 2 });
  assert.equal(first.product_photos.copied + first.product_photos.skipped, 3, 'اجرای بریده: فقط ۳ ردیف');
  assert.equal(await keyed(), first.product_photos.copied);

  hooks.failGet = 1;
  const second = await move.copy({ tables: ['product_photos'], batch: 2 });
  assert.equal(second.product_photos.failed, 1, 'شیء نوشته شد ولی بازخوانی برید ⇒ ردیف بی کلید');
  assert.equal(second.product_photos.skipped, 1, 'ردیفِ غیرِ عکس هر بار رد');
  assert.equal(second.product_photos.copied, 8 - first.product_photos.copied);
  assert.equal(await objectCount(), (await keyed()) + 1, 'یک شیءِ بی‌ردیف از اجرای بریده');
  const third = await move.copy({ tables: ['product_photos'], batch: 3 });
  assert.equal(third.product_photos.copied, 1, 'همان ردیفِ بریده');
  assert.equal(third.product_photos.failed, 0);
  assert.equal(await objectCount(), await keyed(), '⛔ کلیدِ قطعی ⇒ شیءِ اجرای بریده همان شیءِ ثبت‌شده است، نه یتیم');

  //  ردیفی که میانِ خواندن/نوشتنِ شیء و ثبت عوض می‌شود
  const target = shops[1].id;
  hooks.afterPut = async () => {
    hooks.afterPut = null;
    await query(`UPDATE photo_inbox SET data=$2 WHERE account_id=$1`, [target, Buffer.concat([F.webp(21, 21), Buffer.alloc(150, 9)])]);
  };
  const inbox = await move.copy({ tables: ['photo_inbox'], batch: 3 });
  assert.equal(inbox.photo_inbox.changed, 1, 'عوض‌شده ⇒ دست نخورد');
  assert.equal(inbox.photo_inbox.copied, 1);
  assert.equal(await objectCount(), await keyed(), 'شیءِ ردیفِ عوض‌شده پاک شد');

  const fourth = await move.copy({});
  assert.equal(fourth.photo_inbox.copied, 1, 'ردیفِ عوض‌شده در اجرای بعد با بایتِ تازه');
  const fifth = await move.copy({});
  for (const c of Object.values(fifth)) assert.equal(c.copied + c.changed + c.failed, 0, JSON.stringify(fifth));
  assert.equal(await objectCount(), await keyed());
  const st = await move.status({});
  assert.equal(st.tables.product_photos.copied, 9);
  assert.equal(st.tables.product_photos.dbOnly, 1, 'فقط ردیفِ غیرِ عکس');
  assert.equal(st.tables.image_blobs.copied, 1);
  assert.equal(st.tables.storefront_media.copied, 2);
  //  ⛔ هر شیء در پوشهٔ tenant ِ خودش؛ station جدا از shop
  const row = await one(`SELECT store_key FROM product_photos WHERE app='station' AND account_id='st-1'`);
  assert.ok(store.owns('station:st-1', row.store_key));
  const shopRow = await one(`SELECT store_key FROM product_photos WHERE app='shop' AND account_id=$1 AND product_id='p0'`, [shops[0].id]);
  assert.ok(store.owns(store.shopTenant(shops[0].id), shopRow.store_key));
  assert.ok(!store.owns(store.shopTenant(shops[1].id), shopRow.store_key));
});

test('verify و purge: فقط با confirm، فقط ردیفی که شیءش همان بایت است؛ سپس خواندن از انبار', async () => {
  setMode('disk');
  store.useBackend(flaky);
  const v = await move.verify({});
  assert.equal(v.product_photos.ok, 9);
  assert.equal(v.product_photos.missing + v.product_photos.mismatch, 0);

  const dry = await move.purge({});
  assert.equal(dry.product_photos.wouldPurge, 9);
  assert.equal((await move.status({})).tables.product_photos.storeOnly, 0, 'بی confirm هیچ');

  //  یک شیءِ دست‌خورده ⇒ verify «نیست»، purge آن ردیف را نگه می‌دارد
  const k = (await one(`SELECT store_key FROM product_photos WHERE account_id=$1 AND product_id='p1'`, [shops[0].id])).store_key;
  const file = path.join(DIR, ...k.split('/'));
  const good = fs.readFileSync(file);
  fs.writeFileSync(file, F.png(31, 31, { seed: 77 }));
  assert.equal((await move.verify({ tables: ['product_photos'] })).product_photos.missing, 1);
  const p1 = await move.purge({ confirm: true });
  assert.equal(p1.product_photos.purged, 8);
  assert.equal(p1.product_photos.kept, 1);
  assert.deepEqual((await photos.get({ app: 'shop', accountId: shops[0].id }, 'p1')).data, orig.get(`${shops[0].id}/p1`),
    'شیءِ خراب ⇒ همان رونوشتِ پایگاه');
  fs.writeFileSync(file, good);
  assert.equal((await move.purge({ confirm: true })).product_photos.purged, 1);

  const st = await move.status({});
  assert.equal(st.tables.product_photos.storeOnly, 9);
  assert.equal(st.tables.product_photos.copied, 0);
  assert.equal(st.tables.image_blobs.storeOnly, 1);
  for (const [id, buf] of orig) {
    const [acc, pid] = id.split('/');
    const got = await photos.get({ app: acc === 'st-1' ? 'station' : 'shop', accountId: acc }, pid);
    assert.deepEqual(got && got.data, buf, id);
  }
  assert.equal((await sfm.file(shops[1].id, 'logo', 1)).data.length > 0, true);
});

test('gc: فقط شیءِ بی‌ردیف و کهنه، فقط با apply', async () => {
  setMode('disk');
  store.useBackend(flaky);
  const orphan = F.jpeg(12, 12, { seed: 55 });
  const ok = store.keyFor(store.shopTenant(shops[0].id), orphan);
  await disk.put(ok, orphan);
  assert.equal((await move.gc({})).listed, 0, 'همه تازه‌تر از مهلتِ ۲۴ ساعت');
  const later = Date.now() + 48 * 3600 * 1000;
  const report = await move.gc({ now: later });
  assert.equal(report.orphans, 1);
  assert.equal(report.deleted, 0);
  assert.equal(report.referenced, await keyed());
  assert.ok(await disk.get(ok), 'بی apply هیچ');
  const done = await move.gc({ now: later, apply: true });
  assert.equal(done.deleted, 1);
  assert.equal(await disk.get(ok), null);
  assert.equal(await objectCount(), await keyed());
});

test('rollback: فقط db + IMAGE_STORE_READ؛ همان بایت؛ پایینِ ۲۵۵ پیش از آن نگهبان، پس از آن پاک و دوباره', async () => {
  const down = fs.readFileSync(path.join(__dirname, '..', 'migrations', 'rollback', '255_image_store.down.sql'), 'utf8');
  const hasCol = async () => (await one(
    `SELECT COUNT(*)::int AS n FROM information_schema.columns WHERE column_name='store_key' AND table_name='product_photos'`)).n;
  await assert.rejects(tx((c) => c.query(down)), /null values|violated by some row/, 'عکسِ فقط-انبار ⇒ پایینِ ۲۵۵ خطا');
  assert.equal(await hasCol(), 1, 'و هیچ تغییری');

  setMode('disk');
  store.useBackend(flaky);
  await assert.rejects(move.rollback({}), /rollback فقط با IMAGE_STORE=db/);
  setMode('db', 'disk');
  const out = await move.rollback({ batch: 4 });
  assert.equal(out.product_photos.restored, 9);
  assert.equal(out.image_blobs.restored, 1);
  assert.equal(out.storefront_media.restored, 2);
  const st = await move.status({});
  for (const [t, c] of Object.entries(st.tables)) assert.equal(c.storeOnly + c.copied, 0, t);
  store.useBackend(null);
  setMode('db');
  for (const [id, buf] of orig) {
    const [acc, pid] = id.split('/');
    assert.deepEqual((await photos.get({ app: acc === 'st-1' ? 'station' : 'shop', accountId: acc }, pid)).data, buf, id);
  }

  //  نگهبانِ عکسِ کالا جدا (ستونش nullable است): حتی یک عکسِ فقط-انبار ⇒ همان خطا، هیچ تغییر
  await assert.rejects(tx(async (c) => {
    await c.query(`UPDATE product_photos SET data=NULL, store_key=$2 WHERE account_id=$1 AND product_id='p3'`,
      [shops[1].id, store.keyFor(store.shopTenant(shops[1].id), F.jpeg(9, 9))]);
    await c.query(down);
  }), /violated by some row/);
  assert.equal(await hasCol(), 1);
  await query(down);
  assert.equal(await hasCol(), 0);
  assert.equal(await one(`SELECT 1 FROM schema_migrations WHERE version='255_image_store.sql'`), null);
  const nn = await one(`SELECT is_nullable FROM information_schema.columns WHERE table_name='storefront_media' AND column_name='data'`);
  assert.equal(nn.is_nullable, 'NO');
  assert.equal((await one(`SELECT COUNT(*)::int AS n FROM product_photos`)).n, 10);
  await require('../src/migrate').run();
  assert.equal(await hasCol(), 1, 'مهاجرت دوباره نشست');
  assert.deepEqual((await photos.get({ app: 'shop', accountId: shops[0].id }, 'p2')).data, orig.get(`${shops[0].id}/p2`));
});
