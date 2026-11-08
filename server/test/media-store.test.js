'use strict';
/**
 * رسانه‌های دیگر در انبارِ عکس (`lib/media-store.js`، مهاجرتِ ۲۷۵، `claude/scale-4`): رسانهٔ چتِ دکان، رسانهٔ
 * پشتیبانی و مدرکِ پروندهٔ اعتماد.
 *
 *   • حالتِ `db` (پیش‌فرض): بایت در `data` و `store_key` = '' — همان پیشین، هیچ شیئی.
 *   • حالتِ `disk`: ردیفِ تازه بی `data` و با کلید در بخشِ tenant ِ خودش؛ همان بایت از همان درها؛ تکرارِ همان
 *     بایت شیءِ یتیم نمی‌سازد؛ «حذف برای همه»، پاک‌سازیِ یتیم، حذفِ دادهٔ مشتری و رلهٔ پشتیبانی شیء را هم پاک
 *     می‌کنند؛ پشتیبان و `verify` ِ چت بایتِ انبار را می‌خوانند.
 *   • ⛔ جدایی و سنجش: ردیفی که به شیءِ دکانِ دیگر اشاره کند، شیءِ دست‌خورده، یا شیئی که نوعش با ردیف نمی‌خواند
 *     ⇒ «نیست» (`media_gone`)، نه بایتِ دیگر. نوعی که پشتیبانی می‌پذیرد ولی بایتش شناخته نیست ⇒ همان پایگاه.
 *   • ابزارِ جابه‌جایی (`copy` ⇐ `verify` ⇐ `purge` ⇐ `rollback`) هر سه جدول را می‌شناسد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const h = require('./helpers');
const F = require('./image-fixtures');
const { query, one, newId, now } = require('../src/db');
const config = require('../src/config');
const store = require('../src/lib/image-store');
const move = require('../src/lib/image-store-move');
const media = require('../src/lib/media-store');
const chat = require('../src/lib/shop-chat');
const support = require('../src/lib/support');
const trust = require('../src/lib/market-trust');
const liveConfig = require('../src/lib/live-config');

const DIR = fs.mkdtempSync(path.join(os.tmpdir(), 'media-store-'));
const setMode = (mode, read = '') => { config.imageStore.mode = mode; config.imageStore.read = read; config.imageStore.dir = DIR; };
const objectPath = (key) => path.join(DIR, ...key.split('/'));
const OGG = Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(400, 3)]);
const PDF = Buffer.concat([Buffer.from('%PDF-1.4\n', 'latin1'), Buffer.alloc(300, 32)]);
let seq = 0;
const jpg = () => F.jpeg(8, 8, { seed: ++seq });

async function shopWithChat(label) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  await liveConfig.set(shopId, 'shop.chat', true, 'test');
  return { shopId, userId: u.user.id };
}
const rowOf = (table, id) => one(`SELECT data, store_key FROM ${table} WHERE id=$1`, [id]);

test.before(async () => { await h.start(); setMode('db'); });
test.after(async () => {
  setMode('db');
  await h.stop();
  fs.rmSync(DIR, { recursive: true, force: true });
});

test('چتِ دکان: db همان پیشین؛ disk ⇒ شیء، همان بایت، بی یتیم، و پاک با حذف/یتیم/حذفِ دادهٔ مشتری', async () => {
  const a = await shopWithChat('رسانهٔ الف');
  const me = { side: 'customer', shopId: a.shopId, phone: '+93791112233' };
  const conv = (await chat.open(me, {})).conversation.id;

  setMode('db');
  const inDb = await chat.putMedia(me, conv, jpg());
  const r0 = await rowOf('shop_chat_media', inDb.mediaId);
  assert.ok(r0.data && r0.store_key === '', 'حالتِ db: بایت در پایگاه، بی کلید');

  setMode('disk');
  const bufs = { image: jpg(), audio: OGG, file: PDF };
  const up = {};
  for (const [kind, buf] of Object.entries(bufs)) {
    up[kind] = await chat.putMedia(me, conv, buf);
    assert.equal(up[kind].kind, kind);
    const r = await rowOf('shop_chat_media', up[kind].mediaId);
    assert.equal(r.data, null, `${kind}: در حالتِ disk بایت در پایگاه نیست`);
    assert.ok(store.owns(store.shopTenant(a.shopId), r.store_key), 'کلید در بخشِ همان دکان');
    assert.ok(fs.existsSync(objectPath(r.store_key)));
    up[kind].key = r.store_key;
  }
  //  همان بایت‌ها دوباره ⇒ همان ردیف و هیچ شیءِ تازه (شیءِ آماده‌شده همان دم پاک)
  const files = () => fs.readdirSync(path.join(DIR, 'i1', store.segOf(store.shopTenant(a.shopId)))).length;
  const before = files();
  const again = await chat.putMedia(me, conv, bufs.audio);
  assert.equal(again.mediaId, up.audio.mediaId);
  assert.equal(again.reused, true);
  assert.equal(files(), before, 'تکرار شیءِ یتیم نساخت');
  //  همان بایت‌ها از همان در (و ردیفِ قدیمیِ db هنوز خوانده می‌شود)
  for (const kind of Object.keys(bufs)) {
    const m = await chat.getMedia(me, conv, up[kind].mediaId);
    assert.ok(m.data.equals(kind === 'image' ? require('../src/lib/chat-media').stripImageMetadata(bufs.image, 'image/jpeg') : bufs[kind]));
  }
  assert.ok((await chat.getMedia(me, conv, inDb.mediaId)).data.length > 0);
  //  پشتیبان و سنجشِ چت بایتِ انبار را می‌خوانند
  const v = await require('../src/lib/chat-backup').verify({ shopId: a.shopId });
  assert.deepEqual(v.corrupt, [], 'verify ِ چت بایتِ انبار را درست می‌خواند');

  //  حذف برای همه ⇒ ردیف و شیء
  const sent = await chat.send(me, conv, { clientId: `c-${Date.now()}-0001`, kind: 'image', body: 'عکس', mediaId: up.image.mediaId });
  await chat.remove(me, conv, sent.message.id, 'all');
  assert.equal(await rowOf('shop_chat_media', up.image.mediaId), null);
  assert.equal(fs.existsSync(objectPath(up.image.key)), false, 'شیءِ رسانهٔ پاک‌شده رفت');
  //  پاک‌سازیِ یتیم (بارگذاریِ بی پیام) ⇒ شیء هم
  await chat.sweep({ at: now() + 2 * 3600 * 1000 });
  assert.equal(await rowOf('shop_chat_media', up.file.mediaId), null);
  assert.equal(fs.existsSync(objectPath(up.file.key)), false, 'شیءِ یتیم رفت');

  //  حذفِ دادهٔ مشتری ⇒ رسانه و شیءش
  const keep = await chat.putMedia(me, conv, jpg());
  await chat.send(me, conv, { clientId: `c-${Date.now()}-0002`, kind: 'image', mediaId: keep.mediaId });
  const k = (await rowOf('shop_chat_media', keep.mediaId)).store_key;
  await require('../src/lib/customer-erasure').erase(a.shopId, { phone: me.phone }, { by: 'user', userId: a.userId });
  assert.equal(await rowOf('shop_chat_media', keep.mediaId), null);
  assert.equal(fs.existsSync(objectPath(k)), false, 'شیءِ رسانهٔ مشتریِ حذف‌شده رفت');
});

test('⛔ جدایی و سنجش: کلیدِ دکانِ دیگر، شیءِ دست‌خورده و نوعِ ناجور ⇒ «نیست»', async () => {
  setMode('disk');
  const a = await shopWithChat('جدایی الف');
  const b = await shopWithChat('جدایی ب');
  const meA = { side: 'customer', shopId: a.shopId, phone: '+93791110001' };
  const meB = { side: 'customer', shopId: b.shopId, phone: '+93791110002' };
  const convA = (await chat.open(meA, {})).conversation.id;
  const convB = (await chat.open(meB, {})).conversation.id;
  const secret = await chat.putMedia(meA, convA, jpg());
  const keyA = (await rowOf('shop_chat_media', secret.mediaId)).store_key;
  const mine = await chat.putMedia(meB, convB, jpg());
  //  ردیفِ «ب» به شیءِ «الف» (کلیدِ دزدیده) ⇒ هیچ بایت
  await query('UPDATE shop_chat_media SET store_key=$2 WHERE id=$1', [mine.mediaId, keyA]);
  await assert.rejects(chat.getMedia(meB, convB, mine.mediaId), (e) => e.code === 'media_gone');
  assert.ok((await chat.getMedia(meA, convA, secret.mediaId)).data.length, '«الف» دست‌نخورده');
  //  شیءِ دست‌خورده (همان کلید، بایتِ دیگر) ⇒ «نیست»
  fs.writeFileSync(objectPath(keyA), jpg());
  await assert.rejects(chat.getMedia(meA, convA, secret.mediaId), (e) => e.code === 'media_gone');
  //  نوعِ ناجور با ردیف: PDF ِ سالم زیرِ ردیفی که «عکس» است ⇒ «نیست»
  const pdf = await chat.putMedia(meA, convA, PDF);
  const pdfKey = (await rowOf('shop_chat_media', pdf.mediaId)).store_key;
  const pic = await chat.putMedia(meA, convA, jpg());
  await query('UPDATE shop_chat_media SET store_key=$2 WHERE id=$1', [pic.mediaId, pdfKey]);
  await assert.rejects(chat.getMedia(meA, convA, pic.mediaId), (e) => e.code === 'media_gone');
  assert.equal(media.chatCheck('image/jpeg')(PDF), '');
  assert.equal(media.chatCheck('application/pdf')(PDF), 'application/pdf');
  //  ردیف‌های دست‌خورده پاک (ابزارِ پایین همهٔ جدول را می‌سنجد)
  await query('DELETE FROM shop_chat_media WHERE id = ANY($1::text[])', [[secret.mediaId, mine.mediaId, pic.mediaId]]);
});

test('پشتیبانی: شیء با tenant ِ رشته، رله پس از رسیدن شیء را هم پاک می‌کند؛ نوعِ ناشناس ⇒ همان پایگاه', async () => {
  setMode('disk');
  const u = await h.newUser('پشتیبانیِ انبار', 'shop');
  const thread = await support.threadFor({ app: 'shop', userId: u.user.id, who: 'آزمون' });
  const threadId = thread.id;
  const png = F.png(4, 4, { seed: 7 });
  const put = await support.putMedia({ threadId, uploader: 'user', mime: 'image/png', buf: png });
  const r = await rowOf('support_media', put.mediaId);
  assert.equal(r.data, null);
  assert.ok(store.owns(media.supportTenant(threadId), r.store_key));
  const got = await support.getMedia({ id: put.mediaId, threadId });
  assert.ok(got.data.equals(png));
  assert.equal(got.store_key, undefined, 'کلیدِ انبار بیرون نمی‌رود');
  //  ویدیو (جعبهٔ MP4) هم شیء می‌شود؛ بایتِ ناشناس (WAV) در پایگاه می‌ماند، مثلِ پیش
  const mp4 = Buffer.concat([Buffer.from([0, 0, 0, 0x18]), Buffer.from('ftypisom', 'latin1'), Buffer.alloc(200, 1)]);
  const vid = await support.putMedia({ threadId, uploader: 'user', mime: 'video/mp4', buf: mp4 });
  assert.ok((await rowOf('support_media', vid.mediaId)).store_key);
  const wav = Buffer.concat([Buffer.from('RIFF\0\0\0\0WAVEfmt ', 'latin1'), Buffer.alloc(100, 2)]);
  const odd = await support.putMedia({ threadId, uploader: 'user', mime: 'audio/wav', buf: wav });
  const oddRow = await rowOf('support_media', odd.mediaId);
  assert.ok(oddRow.data && oddRow.store_key === '', 'نوعِ ناشناس: بایت در پایگاه');
  assert.ok((await support.getMedia({ id: odd.mediaId, threadId })).data.equals(wav));
  //  رله: گیرنده (مدیر) گرفت ⇒ ردیف و شیء
  const pw = require('../src/lib/password');
  await query(`INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'media-admin','مدیر',$2,'superadmin','active',$3)`, [newId('adm'), await pw.hashPassword('Admin!12345'), now()]);
  const admin = (await h.post('/api/admin/login', { username: 'media-admin', password: 'Admin!12345' })).body.token;
  const dl = await h.download(`/api/admin/support/media/${put.mediaId}`, { token: admin });
  assert.equal(dl.status, 200);
  assert.ok(dl.buffer.equals(png));
  for (let i = 0; i < 50 && await rowOf('support_media', put.mediaId); i++) await new Promise((res) => setTimeout(res, 20));
  assert.equal(await rowOf('support_media', put.mediaId), null);
  for (let i = 0; i < 50 && fs.existsSync(objectPath(r.store_key)); i++) await new Promise((res) => setTimeout(res, 20));
  assert.equal(fs.existsSync(objectPath(r.store_key)), false, 'شیءِ رسانهٔ رسیده رفت');
});

test('پروندهٔ اعتماد: مدرک در انبار با همان سنجش و همان تکرار', async () => {
  setMode('disk');
  const a = await shopWithChat('اعتماد');
  const id = `tc_${crypto.randomBytes(12).toString('hex')}`;
  await query(`INSERT INTO trust_cases (id, shop_id, kind, subject, category, severity, reporter_key, created_at, updated_at)
     VALUES ($1,$2,'complaint','shop','other','low','rk',$3,$3)`, [id, a.shopId, now()]);
  const st = { shop_id: a.shopId };
  const token = trust.tokenOf(a.shopId, id);
  const img = jpg();
  const up = await trust.putEvidence(st, token, img);
  const r = await rowOf('trust_evidence', up.evidenceId);
  assert.equal(r.data, null);
  assert.ok(store.owns(store.shopTenant(a.shopId), r.store_key));
  const again = await trust.putEvidence(st, token, img);
  assert.equal(again.evidenceId, up.evidenceId);
  assert.equal(again.reused, true);
  const got = await trust.customerEvidence(st, token, up.evidenceId);
  assert.ok(got.data.length > 0 && got.mime === 'image/jpeg');
  fs.writeFileSync(objectPath(r.store_key), PDF);
  await assert.rejects(trust.customerEvidence(st, token, up.evidenceId), (e) => e.code === 'media_gone');
  await query('DELETE FROM trust_evidence WHERE id=$1', [up.evidenceId]);
});

test('ابزارِ جابه‌جایی: copy ⇐ verify ⇐ purge ⇐ rollback برای هر سه جدول', async () => {
  setMode('db');
  const a = await shopWithChat('ابزار');
  const me = { side: 'customer', shopId: a.shopId, phone: '+93791119999' };
  const conv = (await chat.open(me, {})).conversation.id;
  const c1 = await chat.putMedia(me, conv, OGG);
  const u = await h.newUser('پشتیبانیِ ابزار', 'shop');
  const thread = await support.threadFor({ app: 'shop', userId: u.user.id, who: 'ابزار' });
  const s1 = await support.putMedia({ threadId: thread.id, uploader: 'user', mime: 'image/png', buf: F.png(3, 3, { seed: 9 }) });
  const s2 = await support.putMedia({ threadId: thread.id, uploader: 'user', mime: 'audio/wav', buf: Buffer.from('RIFF....WAVE-not-sniffed', 'latin1') });
  const tables = ['shop_chat_media', 'support_media', 'trust_evidence'];

  setMode('db', 'disk');                      //  رونوشت پیش از گردانیدنِ ترافیک
  const copied = await move.copy({ tables });
  assert.ok(copied.shop_chat_media.copied >= 1 && copied.support_media.copied >= 1);
  assert.ok(copied.support_media.skipped >= 1, 'بایتِ ناشناس جابه‌جا نمی‌شود');
  assert.ok((await rowOf('shop_chat_media', c1.mediaId)).store_key);
  assert.equal((await rowOf('support_media', s2.mediaId)).store_key, '');
  const again = await move.copy({ tables });
  assert.equal(again.shop_chat_media.copied + again.support_media.copied, 0, 'تکرار بی‌اثر');
  const ver = await move.verify({ tables });
  for (const t of tables) assert.equal(ver[t].missing + ver[t].mismatch, 0, t);

  setMode('disk');
  const purged = await move.purge({ tables, confirm: true });
  assert.ok(purged.shop_chat_media.purged >= 1);
  assert.equal((await rowOf('shop_chat_media', c1.mediaId)).data, null);
  assert.ok((await chat.getMedia(me, conv, c1.mediaId)).data.equals(OGG), 'پس از purge از انبار');
  assert.ok((await support.getMedia({ id: s1.mediaId, threadId: thread.id })).data.length);

  setMode('db', 'disk');
  const back = await move.rollback({ tables });
  assert.ok(back.shop_chat_media.restored >= 1);
  const row = await rowOf('shop_chat_media', c1.mediaId);
  assert.ok(row.data && Buffer.from(row.data).equals(OGG) && row.store_key === '');
  const status = await move.status({ tables });
  for (const t of tables) assert.equal(status.tables[t].storeOnly, 0, t);
  setMode('db');
});
