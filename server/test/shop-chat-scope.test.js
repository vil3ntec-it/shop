'use strict';
/**
 * چتِ مشتری ↔ دکان — محدودهٔ دیدنِ هر بیننده (v3 §۳۳.۴، CHAT-08 و CHAT-09؛ دورِ ۲۵).
 *
 *   CHAT-08  «حذف برای من» فقط در نمای همان بیننده — **صندوقِ گفت‌وگوها هم**: پیش‌نمایشِ آخرین پیام و شمارِ
 *            نخوانده پیامی را که همین بیننده پنهان کرده نمی‌آورند (پیش‌تر متنش در `lastMessage.preview` ِ
 *            فهرست و نمای گفت‌وگو می‌ماند)؛ طرفِ دیگر و کارمندِ دیگر دست‌نخورده.
 *   CHAT-09  هر درِ نوشتن/خواندنِ دیگر هم (ویرایش با PATCH و POST، سنجاق، رسید، گزارش، بلاک، وضعیت، تاریخچه،
 *            «مشکل دارم») برای کارمندِ دکانِ دیگر و مشتریِ شمارهٔ دیگر همان ۴۰۴ است و هیچ ردیفی عوض نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const chat = require('../src/lib/shop-chat');

let admin = '';
let seq = 0;
const uniq = () => `${Date.now().toString(36)}${++seq}`;
const cid = () => `client-${uniq()}-0000`;
const base = '/api/shop/chat';

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

async function chatShop(name) {
  const u = await h.newUser(name, 'shop');
  const made = await h.post('/api/shop', { name: `دکانِ ${name}` }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const shopId = made.body.shop.id;
  const r = await h.put('/api/admin/pump/live-config', { scope: shopId, key: 'shop.chat', value: true }, { token: admin });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return { token: u.accessToken, shopId, userId: u.user.id };
}

async function member(shopId, role, name) {
  const u = await h.newUser(name, 'shop');
  await query(
    `INSERT INTO shop_members (id, shop_id, user_id, role, status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5)`,
    [newId('mem'), shopId, u.user.id, role, now()]
  );
  return { token: u.accessToken, userId: u.user.id };
}

const customer = (shopId, phone) => ({ side: 'customer', shopId, phone });
const send = (token, conv, body) => h.post(`${base}/conversations/${conv}/messages`, { clientId: cid(), body }, { token });
const inbox = async (token, conv) => (await h.get(`${base}/conversations`, { token })).body.conversations.find((c) => c.id === conv);

test('CHAT-08: «حذف برای من» پیش‌نمایش و شمارِ نخواندهٔ صندوقِ همان بیننده را هم برمی‌دارد؛ طرفِ دیگر دست‌نخورده', async () => {
  const o = await chatShop('صندوقِ پنهان');
  const staff = await member(o.shopId, 'staff', 'شاگردِ صندوق');
  const me = customer(o.shopId, '+93790002508');
  const conv = (await chat.open(me, {})).conversation.id;
  await chat.send(me, conv, { clientId: cid(), body: 'پیامِ نخستِ مشتری' });
  const c2 = (await chat.send(me, conv, { clientId: cid(), body: 'پیامِ دومِ مشتری' })).message;
  const s1 = (await send(o.token, conv, 'رازِ دکان برای پنهان')).body.message;

  //  پیش از پنهان: مشتری پیامِ دکان را در صندوق می‌بیند و یک نخوانده دارد
  let mine = (await chat.list(me)).conversations.find((c) => c.id === conv);
  assert.equal(mine.lastMessage.preview, 'رازِ دکان برای پنهان');
  assert.equal(mine.unread, 1);

  //  مشتری پیامِ دکان را «برای من» پاک می‌کند ⇒ نه در فهرست، نه در نمای گفت‌وگو، نه در پاسخِ همگام
  await chat.remove(me, conv, s1.id, 'me');
  mine = (await chat.list(me)).conversations.find((c) => c.id === conv);
  assert.equal(mine.lastMessage.preview, 'پیامِ دومِ مشتری', 'پیش‌نمایش به پیامِ دیدنیِ پیشین برمی‌گردد');
  assert.equal(mine.unread, 0, 'پیامِ پنهان نخوانده شمرده نمی‌شود');
  const view = await chat.viewOf(me, conv);
  assert.equal(view.lastMessage.preview, 'پیامِ دومِ مشتری');
  const sync = await chat.changes(me, conv, { after: 0 });
  assert.equal(sync.conversation.lastMessage.preview, 'پیامِ دومِ مشتری');
  for (const out of [mine, view, sync]) assert.ok(!JSON.stringify(out).includes('رازِ دکان'), 'متنِ پنهان هیچ‌جای نمای مشتری نیست');

  //  دکان (فرستنده) همان را می‌بیند
  assert.equal((await inbox(o.token, conv)).lastMessage.preview, 'رازِ دکان برای پنهان');

  //  پنهانِ کارمند فقط برای خودش: شاگرد پیامِ دومِ مشتری را پنهان می‌کند ⇒ شمارِ نخواندهٔ خودش کم، صاحب همان
  assert.equal((await inbox(staff.token, conv)).unread, 2);
  const hid = await h.del(`${base}/conversations/${conv}/messages/${c2.id}?for=me`, { token: staff.token });
  assert.equal(hid.status, 200, JSON.stringify(hid.body));
  assert.equal((await inbox(staff.token, conv)).unread, 1);
  assert.equal((await inbox(o.token, conv)).unread, 2);

  //  همه‌چیز پنهان ⇒ بی پیش‌نمایش (نه متنِ پنهان)
  const solo = customer(o.shopId, '+93790002509');
  const conv2 = (await chat.open(solo, {})).conversation.id;
  const only = (await chat.send(solo, conv2, { clientId: cid(), body: 'تنها پیام' })).message;
  await chat.remove(solo, conv2, only.id, 'me');
  assert.equal((await chat.viewOf(solo, conv2)).lastMessage, null);
  assert.equal((await chat.list(solo)).conversations.find((c) => c.id === conv2).lastMessage, null);
  //  تاریخچهٔ دکان (همان نما برای کارمند) هم با پنهانِ همان کارمند
  const hist = await h.get(`${base}/conversations/${conv2}/history`, { token: o.token });
  assert.equal(hist.status, 200, JSON.stringify(hist.body));
});

test('CHAT-09: هر درِ دیگرِ گفت‌وگو برای دکانِ دیگر و مشتریِ شمارهٔ دیگر همان ۴۰۴ است و چیزی عوض نمی‌شود', async () => {
  const a = await chatShop('محدودهٔ الف');
  const b = await chatShop('محدودهٔ ب');
  const meA = customer(a.shopId, '+93790002510');
  const conv = (await chat.open(meA, {})).conversation.id;
  const mine = (await chat.send(meA, conv, { clientId: cid(), body: 'فقط برای دکانِ الف' })).message;
  const theirs = (await send(a.token, conv, 'پاسخِ الف')).body.message;
  const snapshot = async () => JSON.stringify(await one(
    `SELECT c.status, c.rev, c.seq, c.shop_read_seq, c.customer_read_seq, c.shop_delivered_seq, c.customer_delivered_seq, c.issue_opened_at,
            (SELECT COUNT(*)::int FROM shop_chat_pins p WHERE p.conversation_id = c.id) AS pins,
            (SELECT COUNT(*)::int FROM shop_chat_reports r WHERE r.conversation_id = c.id) AS reports,
            (SELECT COUNT(*)::int FROM shop_chat_hidden x JOIN shop_chat_messages m ON m.id = x.message_id WHERE m.conversation_id = c.id) AS hidden,
            (SELECT COUNT(*)::int FROM shop_chat_blocks k WHERE k.shop_id = c.shop_id) AS blocks,
            (SELECT string_agg(m.body || ':' || COALESCE(m.edited_at, 0), '|' ORDER BY m.seq) FROM shop_chat_messages m WHERE m.conversation_id = c.id) AS bodies
       FROM shop_chat_conversations c WHERE c.id = $1`, [conv]));
  const before = await snapshot();

  //  کارمندِ دکانِ ب (صاحب، پس همهٔ مجوزها) با شناسه‌های درستِ گفت‌وگو و پیامِ دکانِ الف
  const doors = [
    ['GET', `/conversations/${conv}/history`],
    ['PUT', `/conversations/${conv}/status`, { status: 'closed' }],
    ['PATCH', `/conversations/${conv}/messages/${theirs.id}`, { body: 'دست‌کاری' }],
    ['POST', `/conversations/${conv}/messages/${theirs.id}/edit`, { body: 'دست‌کاری' }],
    ['DELETE', `/conversations/${conv}/messages/${theirs.id}?for=all`],
    ['PUT', `/conversations/${conv}/pins/${mine.id}`],
    ['DELETE', `/conversations/${conv}/pins/${mine.id}`],
    ['POST', `/conversations/${conv}/receipts`, { deliveredSeq: 9, readSeq: 9 }],
    ['POST', `/conversations/${conv}/reports`, { reason: 'spam', messageId: mine.id }],
    ['PUT', `/conversations/${conv}/block`],
    ['DELETE', `/conversations/${conv}/block`],
  ];
  for (const [method, path, body] of doors) {
    const r = await h.api(method, `${base}${path}`, { token: b.token, body: body || null });
    assert.equal(r.status, 404, `${method} ${path} ⇒ ${r.status} ${JSON.stringify(r.body)}`);
    assert.ok(!JSON.stringify(r.body).includes('فقط برای دکانِ الف'), `${method} ${path} متن را لو داد`);
  }

  //  مشتریِ شمارهٔ دیگر در همان دکان، و همان شماره در دکانِ دیگر
  for (const v of [customer(a.shopId, '+93790002511'), customer(b.shopId, '+93790002510')]) {
    const tries = [
      () => chat.viewOf(v, conv),
      () => chat.edit(v, conv, mine.id, { body: 'دست‌کاری' }),
      () => chat.remove(v, conv, mine.id, 'all'),
      () => chat.remove(v, conv, theirs.id, 'me'),
      () => chat.pin(v, conv, theirs.id, true),
      () => chat.receipts(v, conv, { deliveredSeq: 9, readSeq: 9 }),
      () => chat.report(v, conv, { reason: 'abuse', messageId: theirs.id }),
      () => chat.setBlock(v, conv, true),
      () => chat.issue(v, conv),
    ];
    for (const run of tries) await assert.rejects(run(), (e) => e.status === 404, String(run));
    const listed = await chat.list(v);
    assert.ok(!listed.conversations.some((c) => c.id === conv));
    assert.equal((await chat.search(v, 'دکانِ الف')).results.length, 0);
  }
  assert.equal(await snapshot(), before, 'هیچ ردیفی عوض نشد');
});

test('CHAT-10: درِ مشتری (ویترین) هم فایلِ اجرایی، MIME ِ جعلی، SVG/HTML و فایلِ بزرگ را رد می‌کند و سرپا می‌ماند', async () => {
  const o = await chatShop('ردِ ویترین');
  for (const key of ['shop.orders', 'shop.storefront']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: o.shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const slug = `scope-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'ردِ ویترین', contactPhone: '0700000025' }, { token: o.token })).status, 200);
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790002525' });
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790002525', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const headers = { 'X-Store-Ticket': check.body.ticket };
  const conv = (await h.api('POST', `/api/store/${slug}/chat/conversations`, { body: {}, headers })).body.conversation.id;
  const up = (buf, type) => h.raw('POST', `/api/store/${slug}/chat/conversations/${conv}/media`, buf, { headers: { ...headers, 'Content-Type': type } });
  const jpeg = Buffer.concat([Buffer.from([0xff, 0xd8, 0xff, 0xdb, 0x00, 0x43]), Buffer.alloc(65, 1), Buffer.from([0xff, 0xd9])]);
  const cases = [
    [Buffer.concat([Buffer.from('MZ', 'latin1'), Buffer.alloc(400, 0x90)]), 'image/jpeg', 415],
    [Buffer.concat([Buffer.from('\x7fELF', 'latin1'), Buffer.alloc(200)]), 'audio/ogg', 415],
    [Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>'), 'image/svg+xml', 415],
    [Buffer.from('<!doctype html><script>x()</script>'), 'image/png', 415],
    [Buffer.concat([jpeg, Buffer.alloc(1600 * 1024)]), 'image/jpeg', 413],
    [Buffer.alloc(6 * 1024 * 1024, 1), 'application/octet-stream', 413],
  ];
  for (const [buf, type, status] of cases) {
    const r = await up(buf, type);
    assert.equal(r.status, status, `${type} ${buf.length}: ${JSON.stringify(r.body)}`);
  }
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [conv])).n), 0);
  assert.equal((await h.api('GET', `/api/store/${slug}/chat/conversations/${conv}`, { headers })).status, 200, 'سرویس سرپاست');
});

test('CHAT-05: عکسِ مشتری از ویترین — سقف‌ها با گشودنِ گفت‌وگو؛ بمبِ ابعاد (PNG/JPEG/WebP، پیش‌نمایشِ بزرگ) پیش از ذخیره ۴۱۳ از هر دو طرف؛ عکسِ درست بی EXIF، تکرارِ همان بایت همان رسانه و همان پیام', async () => {
  const F = require('./image-fixtures');
  const o = await chatShop('عکسِ ویترین');
  for (const key of ['shop.orders', 'shop.storefront']) {
    const r = await h.put('/api/admin/pump/live-config', { scope: o.shopId, key, value: true }, { token: admin });
    assert.equal(r.status, 200, JSON.stringify(r.body));
  }
  const slug = `photo-${uniq()}`;
  assert.equal((await h.put('/api/shop/storefront', { slug, enabled: true, title: 'عکسِ ویترین', contactPhone: '0700000026' }, { token: o.token })).status, 200);
  const start = await h.post(`/api/store/${slug}/verify/start`, { phone: '0790002626' });
  const check = await h.post(`/api/store/${slug}/verify/check`, { phone: '0790002626', code: start.body.devCode });
  assert.equal(check.status, 200, JSON.stringify(check.body));
  const headers = { 'X-Store-Ticket': check.body.ticket };
  const opened = await h.api('POST', `/api/store/${slug}/chat/conversations`, { body: {}, headers });
  assert.equal(opened.status, 201, JSON.stringify(opened.body));
  //  CHAT-07: سقفِ صدا هم از سرور؛ `voice` فقط با پرچمِ خاموشِ `shop.chat.voice` (این‌جا خاموش)
  assert.deepEqual(opened.body.limits, { imageMax: 1536 * 1024, thumbMax: 64 * 1024, audioMax: 3072 * 1024, voice: false }, 'ویترین سقفِ سرور را می‌گیرد، نه عددِ ثابتِ خودش');
  const conv = opened.body.conversation.id;
  const up = (buf, role = 'main') => h.raw('POST', `/api/store/${slug}/chat/conversations/${conv}/media?role=${role}`, buf, { headers: { ...headers, 'Content-Type': 'image/jpeg' } });
  const media = async () => Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_media WHERE conversation_id=$1', [conv])).n);

  const bombs = [
    [F.pngBomb(16000, 16000), 'main', 'PNG ِ یک‌بیتیِ ۱۶۰۰۰² (چند ده KB، ۱ گیگابایت پس از رمزگشایی)'],
    [F.jpeg(100, 100, { sof: [20000, 100] }), 'main', 'JPEG با دو SOF (رمزگشا اولی را می‌خواند)'],
    [F.webp(50, 40, { bits: [12000, 9000] }), 'main', 'WebP ِ بومِ کوچک با جریانِ بزرگ'],
    [F.jpeg(7000, 7000), 'main', 'JPEG ِ ۴۹ مگاپیکسل'],
    [F.jpeg(2000, 1500), 'thumb', 'پیش‌نمایشِ ۲۰۰۰px'],
  ];
  for (const [buf, role, what] of bombs) {
    const r = await up(buf, role);
    assert.equal(r.status, 413, `${what}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'image_too_large_dimensions', what);
  }
  const shopBomb = await h.raw('POST', `${base}/conversations/${conv}/media`, F.pngBomb(16000, 16000), { token: o.token });
  assert.equal(shopBomb.status, 413, 'همان سقف برای کارمند هم');
  assert.equal(await media(), 0, 'هیچ ردیفِ رسانه‌ای از بمب نماند');

  //  همان شکلِ خروجیِ ویترین: ≤ ۱۶۰۰px و پیش‌نمایشِ ۳۲۰px؛ EXIF ِ GPS و توضیح را سرور هم برمی‌دارد
  const main = F.jpeg(1600, 1200, { gps: true, seed: 7 });
  const a = await up(main);
  assert.equal(a.status, 201, JSON.stringify(a.body));
  const again = await up(main);
  assert.equal(again.status, 201);
  assert.ok(again.body.reused && again.body.mediaId === a.body.mediaId, 'بارگذاریِ دوبارهٔ همان بایت ⇒ همان رسانه (سهم پس داده می‌شود)');
  const thumb = await up(F.jpeg(320, 240, { seed: 8 }), 'thumb');
  assert.equal(thumb.status, 201, JSON.stringify(thumb.body));
  assert.equal(await media(), 2);
  const msg = { clientId: cid(), kind: 'image', mediaId: a.body.mediaId, thumbId: thumb.body.mediaId, meta: { width: 1600, height: 1200 } };
  const s1 = await h.api('POST', `/api/store/${slug}/chat/conversations/${conv}/messages`, { body: msg, headers });
  const s2 = await h.api('POST', `/api/store/${slug}/chat/conversations/${conv}/messages`, { body: msg, headers });
  assert.equal(s1.status, 201, JSON.stringify(s1.body));
  assert.equal(s2.status, 200, 'تلاشِ دوباره با همان clientId ⇒ همان پیام');
  assert.equal(s2.body.message.id, s1.body.message.id);
  assert.equal(Number((await one('SELECT COUNT(*)::int AS n FROM shop_chat_messages WHERE conversation_id=$1', [conv])).n), 1);

  const seen = await h.get(`${base}/conversations/${conv}/messages?after=0`, { token: o.token });
  const m = seen.body.messages.find((x) => x.id === s1.body.message.id);
  assert.ok(m && m.kind === 'image' && m.media.thumbId === thumb.body.mediaId && m.meta.width === 1600, JSON.stringify(m));
  const bytes = await h.download(`${base}/conversations/${conv}/media/${m.media.id}`, { token: o.token });
  assert.equal(bytes.status, 200);
  assert.equal(bytes.headers.get('content-type'), 'image/jpeg');
  const text = bytes.buffer.toString('latin1');
  assert.ok(!text.includes('Exif') && !text.includes('34.5,69.1'), 'دکان عکس را بی EXIF و بی توضیحِ مکان می‌گیرد');
  assert.deepEqual(require('../src/lib/chat-media').dimsOf(bytes.buffer, 'image/jpeg'), { width: 1600, height: 1200 });
});
