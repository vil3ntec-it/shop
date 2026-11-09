'use strict';
/**
 * CHAT-07 — پیامِ صوتیِ مشتری از ویترین (`store/chat-voice.js`) روی همان درِ رسانهٔ گفت‌وگو (`routes/shop-chat-store.js`،
 * `lib/shop-chat.js` ‏`putMedia`/`getMedia`/`send`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) `limits` ِ گشودنِ گفت‌وگو: `audioMax` همان سقفِ سرور و `voice` فقط با پرچمِ خاموشِ `shop.chat.voice`.
 *   ۲) نوع از بایت (WebM، Ogg، MP4 ِ صوتی پذیرفته؛ متن، HTML، WAV، QuickTime و SVG با هر Content-Type ⇒ ۴۱۵)؛ بزرگ‌تر از
 *      `audioMax` ⇒ ۴۱۳ و بزرگ‌تر از بزرگ‌ترین سقف پیش از خواندن ⇒ ۴۱۳؛ مدت فقط ۱ تا ۱۵ دقیقه؛ رسانهٔ عکس پیامِ صوتی نمی‌شود.
 *   ۳) بی بلیت یا بلیتِ دست‌خورده ⇒ ۴۰۱ و هیچ ردیف؛ همان clientId ⇒ همان پیام؛ همان بایت ⇒ همان رسانه.
 *   ۴) مشتریِ شمارهٔ دیگر، ویترینِ دیگر و دکانِ دیگر به صدای این مشتری نمی‌رسند (نه خواندن، نه بارگذاری، نه پیام با شناسه‌اش).
 *   ۵) سطلِ بارگذاری به‌ازای (دکان، شماره): بایتِ تکراری سهم را پس می‌دهد؛ شمارهٔ دیگر سطلِ خودش.
 *   ۶) SEC-10: حذفِ دادهٔ مشتری صدای او را هم می‌برد (ردیف و بایت)؛ صدای مشتریِ دیگر می‌ماند.
 *   ۷) Permissions-Policy: ویترین `microphone=(self)` (دوربین و پرداخت بسته)؛ پنلِ مدیر همان `microphone=()`.
 *   ۸) ماشینِ ضبطِ `store/chat-voice.js` همان `VoiceFlow.kt` (موردهای `VoiceFlowTest`، راهِ زدن).
 */
process.env.CHAT_AUDIO_MAX_KB = '64';
process.env.CHAT_IMAGE_MAX_KB = '96';
process.env.CHAT_FILE_MAX_KB = '128';
process.env.RATE_CHAT_UPLOAD_MAX = '6';
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const { resetAll } = require('../src/middleware/ratelimit');
const F = require('./image-fixtures');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;

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
test.after(() => h.stop());
test.beforeEach(() => resetAll());

async function setFlag(scope, key, value) {
  const r = await h.put('/api/admin/pump/live-config', { scope, key, value }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}

/** دکانِ آزمایشی با ویترینِ روشن و چت (دادهٔ ساختگی) */
async function chatStore(label, { voice = true } = {}) {
  const u = await h.newUser(label, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const o = { token: u.accessToken, shopId: made.body.shop.id };
  for (const k of ['shop.orders', 'shop.storefront', 'shop.chat']) await setFlag(o.shopId, k, true);
  if (voice) await setFlag(o.shopId, 'shop.chat.voice', true);
  const slug = `v-${uniq()}`;
  const put = await h.put('/api/shop/storefront', { slug, enabled: true, title: `ویترینِ ${label}`, contactPhone: '0700000001' }, { token: o.token });
  assert.equal(put.status, 200, JSON.stringify(put.body));
  return { ...o, slug };
}

async function ticketOf(slug, phone) {
  const s = await h.post(`/api/store/${slug}/verify/start`, { phone });
  assert.equal(s.status, 200, JSON.stringify(s.body));
  const c = await h.post(`/api/store/${slug}/verify/check`, { phone, code: s.body.devCode });
  assert.equal(c.status, 200, JSON.stringify(c.body));
  return c.body.ticket;
}

/** مشتریِ ویترین: بلیت، گفت‌وگوی عمومی و درهای خودش */
async function shopper(s, phone) {
  const ticket = await ticketOf(s.slug, phone);
  const hd = (t = ticket) => (t ? { 'X-Store-Ticket': t } : {});
  const pub = (method, p, body, t) => h.api(method, `/api/store/${s.slug}/chat${p}`, { body: body || null, headers: hd(t) });
  const opened = await pub('POST', '/conversations', {});
  assert.ok(opened.status === 201 || opened.status === 200, JSON.stringify(opened.body));
  const conv = opened.body.conversation.id;
  const up = (buf, { conv: c = conv, t, type = 'audio/webm' } = {}) =>
    h.raw('POST', `/api/store/${s.slug}/chat/conversations/${c}/media`, buf, { headers: { ...hd(t), 'Content-Type': type } });
  const bytes = async (mid, { conv: c = conv, t } = {}) => {
    const r = await fetch(`${h.base()}/api/store/${s.slug}/chat/conversations/${c}/media/${mid}`, { headers: hd(t) });
    return { status: r.status, buf: Buffer.from(await r.arrayBuffer()) };
  };
  const say = (mediaId, durationMs, { conv: c = conv, clientId = cid(), kind = 'audio' } = {}) =>
    pub('POST', `/conversations/${c}/messages`, { clientId, kind, mediaId, meta: { durationMs } });
  return { ticket, conv, pub, up, bytes, say, limits: opened.body.limits };
}

//  بایت‌های صوتیِ ساختگی با نشانهٔ درستِ آغاز (سرور صدا را فقط از سرآیند می‌شناسد و دست نمی‌زند)
const webm = (n = 2048) => Buffer.concat([Buffer.from([0x1a, 0x45, 0xdf, 0xa3]), crypto.randomBytes(n)]);
const ogg = (n = 2048) => Buffer.concat([Buffer.from('OggS\0\x02', 'latin1'), crypto.randomBytes(n)]);
const m4a = (n = 2048) => Buffer.concat([Buffer.from([0, 0, 0, 0x20]), Buffer.from('ftypM4A \0\0\0\0', 'latin1'), crypto.randomBytes(n)]);
const mediaOf = (conv) => one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [conv]);

test('CHAT-07: `limits` ِ گشودن — `audioMax` ِ سرور و `voice` فقط با پرچمِ خاموشِ shop.chat.voice', async () => {
  const s = await chatStore('پرچمِ صدا', { voice: false });
  const a = await shopper(s, '0790001101');
  assert.equal(a.limits.audioMax, 64 * 1024);
  assert.equal(a.limits.voice, false, 'پرچمِ خاموش ⇒ دکمهٔ ضبط پنهان');
  await setFlag(s.shopId, 'shop.chat.voice', true);
  const again = await a.pub('POST', '/conversations', {});
  assert.equal(again.body.limits.voice, true);
  assert.equal(again.body.limits.imageMax, 96 * 1024, 'سقف‌های پیشینِ عکس دست نخوردند');
});

test('CHAT-07: نوع از بایت، سقفِ حجم و مدت، بی بلیت ۴۰۱، همان بایت و همان clientId یک بار', async () => {
  const s = await chatStore('نوعِ صدا');
  const a = await shopper(s, '0790001102');

  //  پذیرفته: WebM/Opus ِ کروم، Ogg ِ فایرفاکس، MP4 ِ صوتیِ سافاری — نوع از بایت، نه از Content-Type
  const w = await a.up(webm(), { type: 'application/octet-stream' });
  assert.equal(w.status, 201, JSON.stringify(w.body));
  assert.deepEqual([w.body.kind, w.body.mime, w.body.reused], ['audio', 'audio/webm', false]);
  assert.equal((await a.up(ogg(), { type: 'audio/ogg' })).body.mime, 'audio/ogg');
  assert.equal((await a.up(m4a(), { type: 'audio/mp4' })).body.mime, 'audio/mp4');

  //  ردشده با هر Content-Type: متن، HTML ِ جعلی، WAV، QuickTime ِ ویدیویی، SVG ⇒ ۴۱۵ و هیچ ردیف
  //  (ردشده هم سهمِ سطل را می‌خورد — سطل جدا در آزمونِ «سطلِ بارگذاری»؛ این‌جا میانِ گروه‌ها از نو)
  resetAll();
  const before = (await mediaOf(a.conv)).n;
  const wav = Buffer.concat([Buffer.from('RIFF\0\0\0\0WAVEfmt ', 'latin1'), crypto.randomBytes(512)]);
  const mov = Buffer.concat([Buffer.from([0, 0, 0, 0x14]), Buffer.from('ftypqt  \0\0\0\0', 'latin1'), crypto.randomBytes(512)]);
  for (const [buf, type] of [[Buffer.from('this is not audio at all, just text'), 'audio/webm'],
    [Buffer.from('<html><script>alert(1)</script></html>'), 'audio/ogg'], [wav, 'audio/wav'], [mov, 'audio/mp4'],
    [Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>1</script></svg>'), 'audio/webm']]) {
    const r = await a.up(buf, { type });
    assert.equal(r.status, 415, `${type}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'unsupported_media');
  }
  //  بزرگ‌تر از `audioMax` ⇒ ۴۱۳ از `putMedia`؛ بزرگ‌تر از بزرگ‌ترین سقف ⇒ ۴۱۳ پیش از خواندنِ بدنه
  resetAll();
  const over = await a.up(webm(64 * 1024));
  assert.equal(over.status, 413, JSON.stringify(over.body));
  assert.equal(over.body.error.code, 'too_large');
  const huge = await a.up(webm(200 * 1024));
  assert.equal(huge.status, 413);
  assert.equal(huge.body.error.code, 'too_large');
  assert.ok((await a.up(webm(60 * 1024))).status === 201, 'زیرِ سقف پذیرفته');
  assert.equal((await mediaOf(a.conv)).n, before + 1, 'ردشده‌ها هیچ ردیفی نساختند');

  //  بی بلیت / بلیتِ دست‌خورده ⇒ ۴۰۱ و هیچ ردیف
  for (const t of ['', h.tampered(a.ticket)]) {
    const r = await h.raw('POST', `/api/store/${s.slug}/chat/conversations/${a.conv}/media`, webm(), { headers: t ? { 'X-Store-Ticket': t } : {} });
    assert.equal(r.status, 401, JSON.stringify(r.body));
    assert.equal(r.body.error.code, 'verification_required');
  }
  assert.equal((await mediaOf(a.conv)).n, before + 1);

  //  همان بایت ⇒ همان رسانه
  resetAll();
  const clip = webm(3000);
  const u1 = await a.up(clip);
  const u2 = await a.up(clip);
  assert.equal(u1.body.mediaId, u2.body.mediaId);
  assert.equal(u2.body.reused, true);

  //  پیام: مدت ۱ تا ۱۵ دقیقه؛ رسانهٔ عکس پیامِ صوتی نمی‌شود و برعکس
  for (const ms of [0, 15 * 60 * 1000 + 1, 'دو']) {
    const r = await a.say(u1.body.mediaId, ms);
    assert.equal(r.status, 400, `${ms}: ${JSON.stringify(r.body)}`);
  }
  const pic = await a.up(F.jpeg(64, 48, { seed: 7 }), { type: 'image/jpeg' });
  assert.equal(pic.status, 201, JSON.stringify(pic.body));
  assert.equal((await a.say(pic.body.mediaId, 4000)).body.error.code, 'bad_media');
  assert.equal((await a.say(u1.body.mediaId, undefined, { kind: 'image' })).body.error.code, 'bad_media');

  const key = cid();
  const m1 = await a.say(u1.body.mediaId, 4200, { clientId: key });
  assert.equal(m1.status, 201, JSON.stringify(m1.body));
  assert.equal(m1.body.message.kind, 'audio');
  assert.equal(m1.body.message.meta.durationMs, 4200);
  assert.equal(m1.body.message.media.mime, 'audio/webm');
  const m2 = await a.say(u1.body.mediaId, 4200, { clientId: key });
  assert.equal(m2.status, 200);
  assert.equal(m2.body.message.id, m1.body.message.id, 'همان clientId ⇒ همان پیام');
  assert.equal((await one("SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1 AND kind='audio'", [a.conv])).n, 1);

  //  دکان و خودِ مشتری همان بایت را می‌گیرند
  const shopGot = await h.download(`/api/shop/chat/conversations/${a.conv}/media/${u1.body.mediaId}`, { token: s.token });
  assert.equal(shopGot.status, 200);
  assert.ok(shopGot.buffer.equals(clip));
  const mine = await a.bytes(u1.body.mediaId);
  assert.equal(mine.status, 200);
  assert.ok(mine.buf.equals(clip));
});

test('CHAT-07: صدای این مشتری برای شمارهٔ دیگر، ویترینِ دیگر و دکانِ دیگر نیست', async () => {
  const s = await chatStore('مرزِ صدا');
  const t = await chatStore('دکانِ دیگرِ صدا');
  const a = await shopper(s, '0790001103');
  const b = await shopper(s, '0790001104');
  const clip = webm(2500);
  const up = await a.up(clip);
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const mid = up.body.mediaId;
  assert.equal((await a.say(mid, 3000)).status, 201);
  const rows = (await mediaOf(a.conv)).n;

  //  شمارهٔ دیگر در همان ویترین: نه خواندنِ گفت‌وگوی «الف»، نه بارگذاری در آن، نه شناسه‌اش در گفت‌وگوی خودش
  assert.equal((await b.bytes(mid, { conv: a.conv })).status, 404);
  assert.equal((await b.up(webm(), { conv: a.conv })).status, 404);
  const gone = await b.bytes(mid);
  assert.equal(gone.status, 404);
  assert.ok(!gone.buf.equals(clip));
  assert.equal((await b.say(mid, 3000)).body.error.code, 'bad_media', 'رسانهٔ دیگری در پیامِ خودش نمی‌نشیند');
  assert.equal((await mediaOf(a.conv)).n, rows, 'هیچ ردیفی در گفت‌وگوی «الف» ساخته نشد');

  //  همان شماره در ویترینِ دکانِ دیگر (بلیتِ همان ویترین) و ویترینِ ناموجود ⇒ ۴۰۴
  const other = await ticketOf(t.slug, '0790001103');
  const r1 = await fetch(`${h.base()}/api/store/${t.slug}/chat/conversations/${a.conv}/media/${mid}`, { headers: { 'X-Store-Ticket': other } });
  assert.equal(r1.status, 404);
  const r2 = await fetch(`${h.base()}/api/store/no-such-${uniq()}/chat/conversations/${a.conv}/media/${mid}`, { headers: { 'X-Store-Ticket': a.ticket } });
  assert.equal(r2.status, 404);
  //  کارمندِ دکانِ دیگر از درِ دکان ⇒ ۴۰۴
  assert.equal((await h.download(`/api/shop/chat/conversations/${a.conv}/media/${mid}`, { token: t.token })).status, 404);

  //  «حذف برای همه» ِ مشتری ⇒ دکان هم دیگر به بایت نمی‌رسد (رسانهٔ بی‌پیامِ زنده)
  const list = await a.pub('GET', `/conversations/${a.conv}/messages?after=0`);
  const msg = list.body.messages.find((m) => m.kind === 'audio');
  const del = await a.pub('DELETE', `/conversations/${a.conv}/messages/${msg.id}?for=all`);
  assert.equal(del.status, 200, JSON.stringify(del.body));
  assert.equal((await h.download(`/api/shop/chat/conversations/${a.conv}/media/${mid}`, { token: s.token })).status, 404);
});

test('CHAT-07: سطلِ بارگذاری به‌ازای (دکان، شماره) — بایتِ تکراری سهم را پس می‌دهد، شمارهٔ دیگر سطلِ خودش', async () => {
  const s = await chatStore('سطلِ صدا');
  const a = await shopper(s, '0790001105');
  const b = await shopper(s, '0790001106');
  const same = webm(1500);
  for (let i = 0; i < 4; i++) assert.equal((await a.up(same)).status, 201, 'تکرارِ همان بایت سهم نمی‌خورد');
  for (let i = 0; i < 5; i++) assert.equal((await a.up(webm())).status, 201, `بارگذاریِ ${i + 2}`);
  const limited = await a.up(webm());
  assert.equal(limited.status, 429, JSON.stringify(limited.body));
  assert.equal((await mediaOf(a.conv)).n, 6);
  assert.equal((await b.up(webm())).status, 201, 'مشتریِ دیگرِ همان دکان سطلِ خودش را دارد');
});

test('SEC-10: حذفِ دادهٔ مشتری صدای او را هم می‌برد؛ صدای مشتریِ دیگر می‌ماند', async () => {
  const s = await chatStore('حذفِ صدا');
  await setFlag(s.shopId, 'shop.customer_erasure', true);
  const a = await shopper(s, '0790001107');
  const b = await shopper(s, '0790001108');
  const clipA = webm(2200);
  const clipB = webm(2300);
  const ua = (await a.up(clipA)).body.mediaId;
  const orphan = (await a.up(ogg())).body.mediaId;   //  بارگذاری‌شده ولی هنوز بی پیام
  const ub = (await b.up(clipB)).body.mediaId;
  assert.equal((await a.say(ua, 2000)).status, 201);
  assert.equal((await b.say(ub, 2000)).status, 201);
  const hashA = crypto.createHash('sha256').update(clipA).digest('hex');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE sha256=$1', [hashA])).n, 1);

  const done = await h.post('/api/shop/customer-erasure', { phone: '0790001107' }, { token: s.token });
  assert.equal(done.status, 200, JSON.stringify(done.body));
  assert.ok(done.body.counts.chatMedia >= 2, JSON.stringify(done.body.counts));
  assert.equal((await mediaOf(a.conv)).n, 0, 'هیچ ردیفِ رسانه‌ای از گفت‌وگوی «الف» نماند');
  assert.equal((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE sha256=$1 OR id = ANY($2::text[])', [hashA, [ua, orphan]])).n, 0);
  const msg = await one("SELECT media_id, meta, deleted_at FROM shop_chat_messages WHERE conversation_id=$1 AND kind='audio'", [a.conv]);
  assert.equal(msg.media_id, null);
  assert.deepEqual(msg.meta, {});
  assert.ok(msg.deleted_at);
  assert.equal((await h.download(`/api/shop/chat/conversations/${a.conv}/media/${ua}`, { token: s.token })).status, 404);
  //  همان شماره با بلیتِ تازه هم دیگر به گفت‌وگوی پاک‌شده نمی‌رسد
  const fresh = await ticketOf(s.slug, '0790001107');
  const r = await fetch(`${h.base()}/api/store/${s.slug}/chat/conversations/${a.conv}/media/${ua}`, { headers: { 'X-Store-Ticket': fresh } });
  assert.equal(r.status, 404);
  //  مشتریِ دیگر دست نخورد
  const kept = await b.bytes(ub);
  assert.equal(kept.status, 200);
  assert.ok(kept.buf.equals(clipB));
});

test('CHAT-07: Permissions-Policy ِ ویترین فقط میکروفونِ همین مبدأ را باز می‌کند؛ پنلِ مدیر بسته می‌ماند', async () => {
  const s = await chatStore('سرآیندِ صدا', { voice: false });
  const page = await fetch(`${h.base()}/store/${s.slug}`);
  const pp = page.headers.get('permissions-policy') || '';
  assert.match(pp, /(^|, )microphone=\(self\)(,|$)/, pp);
  assert.match(pp, /(^|, )camera=\(\)(,|$)/, pp);
  assert.match(pp, /(^|, )payment=\(\)(,|$)/, pp);
  assert.match(page.headers.get('content-security-policy') || '', /frame-ancestors 'none'/, 'قابِ بیرونی میکروفون نمی‌گیرد');
  const panel = await fetch(`${h.base()}/admin/`);
  assert.match(panel.headers.get('permissions-policy') || '', /(^|, )microphone=\(\)(,|$)/);
});

test('CHAT-07: ماشینِ ضبطِ ویترین همان `VoiceFlow.kt` است (راهِ زدن) و هیچ گذری میکروفون را روشن رها نمی‌کند', () => {
  const vm = require('node:vm');
  const fs = require('node:fs');
  const path = require('node:path');
  const win = { addEventListener() {} };
  vm.runInNewContext(fs.readFileSync(path.join(__dirname, '..', 'public', 'store', 'chat-voice.js'), 'utf8'), {
    window: win, document: { addEventListener() {} }, navigator: {}, K: { $: () => null, el: () => ({}), t: (k) => k, fa: String },
  });
  const flow = win.StoreChatVoice.flow;
  const J = (x) => JSON.parse(JSON.stringify(x));
  const next = (p, i) => J(flow.next(p, i));
  const settle = (...a) => J(flow.settle(...a));
  const limit = 1000000;
  assert.equal(win.StoreChatVoice.MAX_MS, 5 * 60 * 1000, 'سقفِ ۵ دقیقه همان `ChatMedia.VOICE_MAX_MS`');
  //  tapRecordsUntilFinishAndSends
  assert.deepEqual(next('idle', 'start'), { phase: 'tap', effect: 'start', say: 'recording' });
  assert.deepEqual(next('tap', 'start'), { phase: 'tap', effect: 'none' });
  assert.deepEqual(next('tap', 'finish'), { phase: 'ready', effect: 'stop_send' });
  assert.deepEqual(settle(true, 40000, limit, true), { phase: 'idle', effect: 'send', say: 'sent' });
  //  holdRecords… ⇒ READY ⇒ SEND
  assert.deepEqual(settle(false, 40000, limit, true), { phase: 'ready', effect: 'none', say: 'ready' });
  assert.deepEqual(next('ready', 'send'), { phase: 'idle', effect: 'send', say: 'sent' });
  //  shortPressRecordsNothing
  for (const send of [false, true]) assert.deepEqual(settle(send, 0, limit, true), { phase: 'idle', effect: 'none', say: 'empty' });
  //  finishDoesNotSendOverLimitOrWhenClosed
  assert.deepEqual(settle(true, limit + 1, limit, true), { phase: 'ready', effect: 'none', say: 'too_big' });
  assert.deepEqual(settle(true, 40000, limit, false), { phase: 'ready', effect: 'none', say: 'ready' });
  assert.deepEqual(settle(true, limit, limit, true), { phase: 'idle', effect: 'send', say: 'sent' });
  assert.equal(flow.tooBig(limit + 1, limit), true);
  assert.equal(flow.tooBig(limit, limit), false);
  assert.equal(flow.tooBig(Number.MAX_SAFE_INTEGER, 0), false, 'سقفِ صفر یعنی سقفی نیست');
  //  cancelStopsOrDiscards
  assert.deepEqual(next('tap', 'cancel'), { phase: 'idle', effect: 'cancel', say: 'cancelled' });
  assert.deepEqual(next('ready', 'cancel'), { phase: 'idle', effect: 'discard', say: 'cancelled' });
  assert.deepEqual(next('idle', 'cancel'), { phase: 'idle', effect: 'none' });
  //  limitStopsBothModes / recordingAgainFromReady
  assert.deepEqual(next('tap', 'limit'), { phase: 'ready', effect: 'stop' });
  assert.deepEqual(next('idle', 'limit'), { phase: 'idle', effect: 'none' });
  assert.deepEqual(next('ready', 'start'), { phase: 'tap', effect: 'start', say: 'recording' });
  assert.deepEqual(next('idle', 'send'), { phase: 'idle', effect: 'none' });
  assert.deepEqual(next('tap', 'send'), { phase: 'tap', effect: 'none' });
  //  recorderIsNeverLeftRunning: بیرون رفتن از ضبط همیشه ایستادن/لغو؛ ورود به ضبط فقط با `start`
  for (const p of ['idle', 'tap', 'ready']) {
    for (const i of ['start', 'finish', 'send', 'cancel', 'limit']) {
      const s = next(p, i);
      if (p === 'tap' && s.phase !== 'tap') assert.ok(['stop', 'stop_send', 'cancel'].includes(s.effect), `${p}/${i}`);
      if (p !== 'tap' && s.phase === 'tap') assert.equal(s.effect, 'start', `${p}/${i}`);
    }
  }
});
