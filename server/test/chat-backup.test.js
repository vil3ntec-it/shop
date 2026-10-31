'use strict';
/**
 * CHAT-12 (v3 §۳۳.۴ و §۳۳.۳): پیام و رسانهٔ چت **جدا** پشتیبان و بازگردانده
 * می‌شوند و پس از بازگردانی رابطهٔ پیام ⇐ رسانه درست می‌ماند.
 *
 *   ۱) دو فایلِ جدا: پیام بی بایتِ رسانه، رسانه با بایت و sha256. پس از پاک شدنِ
 *      همه‌چیز: «فقط پیام» ⇒ verify رسانه‌های نیامده را نام می‌برد؛ «سپس رسانه» ⇒
 *      سالم و همهٔ جدول‌ها بایت‌به‌بایت همان (سنگِ قبر، «حذف برای من»، بلاک،
 *      گزارش). اجرای دوباره چیزی را دوتا یا بازنویسی نمی‌کند.
 *   ۲) فایلِ رسانهٔ دست‌خورده: همان یک رسانه نوشته نمی‌شود و verify آن پیام را نشان می‌دهد.
 *   ۳) دکانِ نبوده در مقصد ⇒ خطا و هیچ ردیف.
 *   ۴) با `BACKUP_PASSPHRASE` هر دو فایل رمزدارند (قالبِ SHOPBK1 ِ lib/backup.js)؛ رمزِ غلط ⇒ خطا.
 *   ۵) verify رسانهٔ گفت‌وگوی دیگر/نقشِ غلط و بایتِ خراب را می‌گیرد.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, one } = require('../src/db');
const config = require('../src/config');
const chat = require('../src/lib/shop-chat');
const chatBackup = require('../src/lib/chat-backup');
const backup = require('../src/lib/backup');

let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;
const dirs = [];
const tmpDir = () => { const d = fs.mkdtempSync(path.join(os.tmpdir(), 'chat-backup-')); dirs.push(d); return d; };

test.before(async () => { await h.start(); });
test.after(async () => {
  for (const d of dirs) fs.rmSync(d, { recursive: true, force: true });
  await h.stop();
});

function seg(marker, payload) {
  const head = Buffer.alloc(4);
  head.writeUInt16BE(0xff00 | marker, 0);
  head.writeUInt16BE(payload.length + 2, 2);
  return Buffer.concat([head, payload]);
}
const jpeg = (fill) => Buffer.concat([Buffer.from([0xff, 0xd8]), seg(0xdb, Buffer.alloc(65, fill)),
  seg(0xda, Buffer.from([1, 1, 0, 0, 0x3f, 0])), Buffer.alloc(300, fill), Buffer.from([0xff, 0xd9])]);
const ogg = (fill) => Buffer.concat([Buffer.from('OggS', 'latin1'), Buffer.alloc(400, fill)]);
const pdf = () => Buffer.concat([Buffer.from('%PDF-1.4\n', 'latin1'), Buffer.alloc(200, 0x20), Buffer.from('\n%%EOF', 'latin1')]);

async function shopWithChat(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  //  پرچمِ `shop.chat` فقط درهای HTTP را می‌بندد؛ این آزمون مستقیم با هسته کار می‌کند
  return { shopId, userId: u.user.id, viewer: { side: 'shop', shopId, userId: u.user.id, role: 'owner' } };
}

/** یک دکان با همهٔ گونه‌های دادهٔ چت: متن، عکس با پیش‌نمایش، صدا، PDF، سنگِ قبر، پنهان، بلاک، گزارش. */
async function seed(name) {
  const o = await shopWithChat(name);
  const ali = { side: 'customer', shopId: o.shopId, phone: '+93790000401' };
  const reza = { side: 'customer', shopId: o.shopId, phone: '+93790000402' };
  const c1 = (await chat.open(ali, { name: 'علی' })).conversation.id;
  const c2 = (await chat.open(reza, { name: 'رضا' })).conversation.id;
  const ids = {};
  ids.text = (await chat.send(ali, c1, { clientId: cid(), body: 'سلام، نان دارید؟' })).message.id;
  const pic = await chat.putMedia(ali, c1, jpeg(3));
  const thumb = await chat.putMedia(ali, c1, jpeg(4), { role: 'thumb' });
  ids.image = (await chat.send(ali, c1, { clientId: cid(), kind: 'image', body: 'عکسِ نان', mediaId: pic.mediaId, thumbId: thumb.mediaId, meta: { width: 10, height: 8 } })).message.id;
  const voice = await chat.putMedia(o.viewer, c1, ogg(5));
  ids.audio = (await chat.send(o.viewer, c1, { clientId: cid(), kind: 'audio', mediaId: voice.mediaId, meta: { durationMs: 2300 } })).message.id;
  const doc = await chat.putMedia(reza, c2, pdf());
  ids.file = (await chat.send(reza, c2, { clientId: cid(), kind: 'file', body: 'فاکتور', mediaId: doc.mediaId })).message.id;
  const gonePic = await chat.putMedia(reza, c2, jpeg(6));
  ids.gone = (await chat.send(reza, c2, { clientId: cid(), kind: 'image', mediaId: gonePic.mediaId })).message.id;
  await chat.remove(reza, c2, ids.gone, 'all');
  ids.hidden = (await chat.send(reza, c2, { clientId: cid(), body: 'پنهان برای دکان' })).message.id;
  await chat.remove(o.viewer, c2, ids.hidden, 'me');
  await chat.edit(ali, c1, ids.text, { body: 'سلام، نانِ تازه دارید؟' });
  await chat.receipts(o.viewer, c1, { deliveredSeq: 2, readSeq: 1 });
  await chat.report(o.viewer, c2, { reason: 'spam', messageId: ids.file, note: 'آزمون' });
  await chat.setBlock(o.viewer, c2, true);
  return { ...o, c1, c2, ids, media: { pic: pic.mediaId, thumb: thumb.mediaId, voice: voice.mediaId, doc: doc.mediaId } };
}

const CHAT_TABLES = ['shop_chat_conversations', 'shop_chat_messages', 'shop_chat_hidden', 'shop_chat_blocks', 'shop_chat_reports', 'shop_chat_media'];

/** همهٔ ردیف‌های چتِ یک دکان، بی‌ترتیب‌وابستگی؛ بایت‌ها با sha256. */
async function snapshot(shopId) {
  const out = {};
  for (const t of CHAT_TABLES) {
    const rows = t === 'shop_chat_hidden'
      ? await many('SELECT x.* FROM shop_chat_hidden x JOIN shop_chat_messages m ON m.id = x.message_id WHERE m.shop_id=$1', [shopId])
      : await many(`SELECT * FROM ${t} WHERE shop_id=$1`, [shopId]);
    out[t] = rows.map((r) => JSON.stringify(Object.fromEntries(Object.entries(r).sort(([a], [b]) => a.localeCompare(b))
      .map(([k, v]) => [k, k === 'data' ? require('crypto').createHash('sha256').update(Buffer.from(v)).digest('hex') : (v == null ? null : String(typeof v === 'object' ? JSON.stringify(v) : v))])))).sort();
  }
  return out;
}

async function wipe(shopId) {
  await query('DELETE FROM shop_chat_conversations WHERE shop_id=$1', [shopId]);
  await query('DELETE FROM shop_chat_blocks WHERE shop_id=$1', [shopId]);
  await query('DELETE FROM shop_chat_media WHERE shop_id=$1', [shopId]);
}

const decoded = async (file) => (await backup.decode(file)).toString('utf8');

test('CHAT-12: پیام و رسانه دو فایلِ جدا؛ «فقط پیام» ⇒ رسانه‌های نیامده نام برده می‌شوند؛ «سپس رسانه» ⇒ رابطه و همهٔ جدول‌ها بایت‌به‌بایت همان؛ تکرار بی‌اثر', async () => {
  const s = await seed('پشتیبانِ چت');
  const before = await snapshot(s.shopId);
  assert.equal((await chatBackup.verify({ shopId: s.shopId })).ok, true, 'پیش از هر کاری سالم است');

  const out = await chatBackup.exportChat({ dir: tmpDir(), shopId: s.shopId });
  assert.notEqual(out.messages.file, out.media.file);
  assert.match(path.basename(out.messages.file), /^chat-messages-.*\.json\.gz$/);
  assert.match(path.basename(out.media.file), /^chat-media-.*\.jsonl\.gz$/);
  assert.equal(out.counts.shop_chat_media, 4, 'عکس، پیش‌نمایش، صدا و PDF — رسانهٔ پیامِ «حذف برای همه» همان دم رفته است');
  const msgText = await decoded(out.messages.file);
  const mediaText = await decoded(out.media.file);
  const picB64 = jpeg(3).toString('base64');
  assert.ok(!msgText.includes(picB64), 'فایلِ پیام بایتِ رسانه ندارد');
  assert.ok(mediaText.includes(picB64), 'فایلِ رسانه بایت دارد');
  assert.ok(!mediaText.includes('نانِ تازه'), 'فایلِ رسانه متنِ پیام ندارد');
  assert.ok(!msgText.includes('عکسِ پاک‌شده') && !msgText.includes(jpeg(6).toString('base64')), 'سنگِ قبر محتوایی ندارد');

  await wipe(s.shopId);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE shop_id=$1', [s.shopId])).n), 0);

  //  فقط پیام: رابطه ناقص و دقیقاً همان رسانه‌ها نام برده می‌شوند
  const rm = await chatBackup.restoreMessages(out.messages.file);
  assert.equal(rm.restored.shop_chat_messages, 6);
  const half = await chatBackup.verify({ shopId: s.shopId });
  assert.equal(half.ok, false);
  assert.deepEqual(half.missing.map((x) => `${x.messageId}:${x.role}`).sort(),
    [`${s.ids.image}:main`, `${s.ids.image}:thumb`, `${s.ids.audio}:main`, `${s.ids.file}:main`].sort());
  assert.deepEqual(half.mismatched, []);

  //  سپس رسانه
  const rd = await chatBackup.restoreMedia(out.media.file);
  assert.deepEqual({ restored: rd.restored, corrupt: rd.corrupt, noConversation: rd.noConversation }, { restored: 4, corrupt: [], noConversation: [] });
  const full = await chatBackup.verify({ shopId: s.shopId });
  assert.deepEqual({ ok: full.ok, missing: full.missing, mismatched: full.mismatched, corrupt: full.corrupt },
    { ok: true, missing: [], mismatched: [], corrupt: [] });
  assert.equal(full.references, 4);
  assert.deepEqual(await snapshot(s.shopId), before, 'همهٔ جدول‌های چت بایت‌به‌بایت همان');

  //  از دیدِ برنامه هم همان: پنهان برای دکان پنهان، سنگِ قبر سنگِ قبر، بایت‌ها سالم
  const shopView = await chat.changes(s.viewer, s.c2, { after: 0 });
  assert.equal(shopView.messages.find((m) => m.id === s.ids.hidden).hidden, true);
  assert.equal(shopView.messages.find((m) => m.id === s.ids.gone).deleted, true);
  assert.equal(shopView.conversation.blockedCustomer, true);
  const got = await chat.getMedia(s.viewer, s.c1, s.media.pic);
  assert.deepEqual(got.data, jpeg(3));

  //  دوباره: هیچ ردیفِ تازه و هیچ بازنویسی
  const again = await chatBackup.restoreMessages(out.messages.file);
  assert.ok(Object.values(again.restored).every((x) => x === 0), JSON.stringify(again.restored));
  const againMedia = await chatBackup.restoreMedia(out.media.file);
  assert.equal(againMedia.restored, 0);
  assert.equal(againMedia.existing, 4);
  assert.deepEqual(await snapshot(s.shopId), before);
});

test('CHAT-12: رسانهٔ دست‌خورده یا بی‌گفت‌وگو نوشته نمی‌شود و verify همان پیام را نشان می‌دهد', async () => {
  const s = await seed('رسانهٔ دست‌خورده');
  const out = await chatBackup.exportChat({ dir: tmpDir(), shopId: s.shopId });
  const lines = (await decoded(out.media.file)).split('\n').filter(Boolean);
  const i = lines.findIndex((l) => l.includes(`"id":"${s.media.voice}"`));
  assert.ok(i > 0);
  const rec = JSON.parse(lines[i]);
  const bytes = Buffer.from(rec.data, 'base64');
  bytes[bytes.length - 1] ^= 0xff;
  lines[i] = JSON.stringify({ ...rec, data: bytes.toString('base64') });
  const j = lines.findIndex((l) => l.includes(`"id":"${s.media.doc}"`));
  lines[j] = JSON.stringify({ ...JSON.parse(lines[j]), conversation_id: 'sch_no_such_conversation' });
  const tampered = path.join(path.dirname(out.media.file), 'tampered.jsonl.gz');
  fs.writeFileSync(tampered, zlib.gzipSync(`${lines.join('\n')}\n`));

  await query('DELETE FROM shop_chat_media WHERE shop_id=$1', [s.shopId]);
  const r = await chatBackup.restoreMedia(tampered);
  assert.deepEqual(r.corrupt, [s.media.voice]);
  assert.deepEqual(r.noConversation, [s.media.doc]);
  assert.equal(r.restored, 2);
  const v = await chatBackup.verify({ shopId: s.shopId });
  assert.equal(v.ok, false);
  assert.deepEqual(v.missing.map((x) => x.messageId).sort(), [s.ids.audio, s.ids.file].sort());
  assert.deepEqual(v.corrupt, []);
});

test('CHAT-12: دکانی که در مقصد نیست ⇒ خطا و هیچ ردیف', async () => {
  const s = await seed('دکانِ گم');
  const out = await chatBackup.exportChat({ dir: tmpDir(), shopId: s.shopId });
  const doc = JSON.parse(await decoded(out.messages.file));
  doc.tables.shop_chat_conversations[0].shop_id = 'shop_not_here';
  const bad = path.join(path.dirname(out.messages.file), 'other-server.json.gz');
  fs.writeFileSync(bad, zlib.gzipSync(JSON.stringify(doc)));
  await wipe(s.shopId);
  await assert.rejects(chatBackup.restoreMessages(bad), /دکانِ این پشتیبان در دیتابیسِ مقصد نیست/);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE shop_id=$1', [s.shopId])).n), 0);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_conversations WHERE shop_id=$1', [s.shopId])).n), 0);
  //  فایلِ رسانه جای فایلِ پیام (و برعکس) پذیرفته نمی‌شود
  await assert.rejects(chatBackup.restoreMessages(out.media.file), /پشتیبانِ پیام‌های چت نیست/);
  await assert.rejects(chatBackup.restoreMedia(out.messages.file), /پشتیبانِ رسانهٔ چت نیست/);
});

test('CHAT-12: با BACKUP_PASSPHRASE هر دو فایل رمزدارند (قالبِ lib/backup.js)؛ رمزِ غلط ⇒ خطا', async () => {
  const s = await seed('رمز');
  const keep = config.backup.passphrase;
  config.backup.passphrase = 'رمزِ-آزمونِ-پشتیبانِ-چت';
  try {
    const before = await snapshot(s.shopId);
    const out = await chatBackup.exportChat({ dir: tmpDir(), shopId: s.shopId });
    assert.equal(out.encrypted, true);
    for (const f of [out.messages.file, out.media.file]) {
      assert.match(f, /\.enc$/);
      const raw = fs.readFileSync(f);
      assert.equal(raw.subarray(0, 7).toString('latin1'), 'SHOPBK1');
      assert.ok(!raw.includes(Buffer.from('نانِ تازه')), 'متن در فایلِ رمزدار پیدا نیست');
      assert.equal((fs.statSync(f).mode & 0o077), 0, 'فقط صاحبِ فایل می‌خواند');
    }
    await wipe(s.shopId);
    await chatBackup.restoreMessages(out.messages.file);
    await chatBackup.restoreMedia(out.media.file);
    assert.equal((await chatBackup.verify({ shopId: s.shopId })).ok, true);
    assert.deepEqual(await snapshot(s.shopId), before);
    config.backup.passphrase = 'رمزِ-غلط';
    await assert.rejects(chatBackup.restoreMessages(out.messages.file));
    await assert.rejects(chatBackup.restoreMedia(out.media.file));
  } finally {
    config.backup.passphrase = keep;
  }
});

test('CHAT-12: verify رسانهٔ گفت‌وگوی دیگر، نقشِ غلط و بایتِ خراب را می‌گیرد', async () => {
  const s = await seed('سنجشِ رابطه');
  assert.equal((await chatBackup.verify({ shopId: s.shopId })).ok, true);
  //  رسانهٔ پیامِ عکس به گفت‌وگوی دیگر «نسبت داده شد»
  await query('UPDATE shop_chat_media SET conversation_id=$2 WHERE id=$1', [s.media.pic, s.c2]);
  //  پیش‌نمایش جای رسانهٔ اصلیِ صدا
  await query('UPDATE shop_chat_messages SET media_id=$2 WHERE id=$1', [s.ids.audio, s.media.thumb]);
  //  بایتِ PDF خراب شد
  await query("UPDATE shop_chat_media SET data = decode('00ff', 'hex') WHERE id=$1", [s.media.doc]);
  const v = await chatBackup.verify({ shopId: s.shopId });
  assert.equal(v.ok, false);
  assert.deepEqual(v.mismatched.map((x) => `${x.messageId}:${x.role}`).sort(), [`${s.ids.image}:main`, `${s.ids.audio}:main`].sort());
  assert.deepEqual(v.corrupt, [s.media.doc]);
  assert.deepEqual(v.missing, []);
  //  verify ِ یک دکان به دکانِ دیگر کاری ندارد
  const other = await seed('دکانِ سالم');
  assert.equal((await chatBackup.verify({ shopId: other.shopId })).ok, true);
  assert.ok(!JSON.stringify(v).includes('نانِ تازه'), 'گزارش بی متنِ پیام');
});
