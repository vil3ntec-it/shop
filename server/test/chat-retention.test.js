'use strict';
/**
 * ══ سرور فقط رله است — پیام و رسانه CHAT_RELAY_DAYS روز ═══════════════════
 *   ۱) پیامِ کهنه‌تر از ۱۵ روز و رسانه‌اش می‌رود؛ تازه‌ها می‌مانند
 *   ۲) رسانهٔ بی‌پیامِ کهنه‌تر از یک ساعت (یتیم) می‌رود؛ یتیمِ تازه و رسانهٔ پیامِ زنده نه
 *   ۳) پیامِ کهنهٔ پشتیبانیِ پمپ می‌رود؛ رشته و شمارنده‌ها می‌مانند؛ رشتهٔ دکان دست نمی‌خورد
 *   ۴) دنباله از نو نمی‌شود: پیامِ بعدی seqِ بزرگ‌تر می‌گیرد و after کار می‌کند
 *   ۵) رسانهٔ رفته ⇒ ۴۰۴ با کدِ media_gone، از هر دو در
 *   ۶) relayDays: 15 در پاسخ‌های رشته و صندوق
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, one, newId, now } = require('../src/db');
const relay = require('../src/lib/chat-relay');

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
});
test.after(async () => { await h.stop(); });

const KEY = 'abcdef0123456789abcd';
const DAY = 24 * 3600 * 1000;

async function pump(uid, code) {
  const login = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  const vip = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 60 }, { token: login.body.token });
  const r = await h.post('/api/pump/device/activate', {
    code: vip.body.code, device: { uid, name: 'pc', platform: 'windows' }, station: { code },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.deviceToken;
  await h.put('/api/pump/device/files/acct-d7', { data: { v: 1, k: KEY, at: 1, d: { n: 'محمد' } } }, { token });
  return { token, code: r.body.station.code, stationId: r.body.station.id };
}

const pub = (code, path) => `/api/pump/public/${code}/acct/d7/chat${path}?k=${KEY}`;

async function upload(token, mime = 'image/png') {
  const res = await fetch(`${h.base()}/api/pump/device/chat/d7/media`, {
    method: 'POST', headers: { 'Content-Type': mime, Authorization: `Bearer ${token}` },
    body: Buffer.from('fake-image-bytes'),
  });
  const body = await res.json();
  assert.equal(res.status, 201, JSON.stringify(body));
  return body.mediaId;
}

async function age(table, id, ms) {
  await query(`UPDATE ${table} SET created_at=$2 WHERE id=$1`, [id, now() - ms]);
}

test('CHAT_RELAY_DAYS یک عدد است: ۱۵، و محیط فقط جایش را می‌گیرد', () => {
  assert.equal(relay.CHAT_RELAY_DAYS, 15);
  assert.equal(relay.relayDays(), 15);
  process.env.CHAT_RELAY_DAYS = '3';
  try { assert.equal(relay.relayDays(), 3); } finally { delete process.env.CHAT_RELAY_DAYS; }
  process.env.CHAT_RELAY_DAYS = 'x';
  try { assert.equal(relay.relayDays(), 15); } finally { delete process.env.CHAT_RELAY_DAYS; }
});

test('پاک‌سازی: کهنه می‌رود، تازه می‌ماند، یتیم می‌رود، دنباله پیوسته، رسانهٔ رفته media_gone', async () => {
  const p = await pump('pc-relay-1', 'relay-one');

  //  ── پیام‌ها ────────────────────────────────────────────────────
  const oldText = (await h.post(pub(p.code, ''), { name: 'محمد', text: 'پیامِ کهنه' })).body.message;
  const oldMedia = await upload(p.token);
  const oldImg = (await h.post('/api/pump/device/chat/d7', { kind: 'image', mediaId: oldMedia }, { token: p.token })).body.message;
  const freshText = (await h.post(pub(p.code, ''), { name: 'محمد', text: 'پیامِ تازه' })).body.message;
  const freshMedia = await upload(p.token);
  const freshImg = (await h.post('/api/pump/device/chat/d7', { kind: 'image', mediaId: freshMedia }, { token: p.token })).body.message;
  const orphanOld = await upload(p.token);     //  بارگذاری شد، هرگز فرستاده نشد
  const orphanFresh = await upload(p.token);   //  همین حالا بارگذاری شد — شاید در راهِ فرستادن

  await age('station_chat_messages', oldText.id, 16 * DAY);
  await age('station_chat_messages', oldImg.id, 16 * DAY);
  await age('station_chat_media', oldMedia, 16 * DAY);
  await age('station_chat_messages', freshText.id, 14 * DAY);   //  هنوز در پنجره
  await age('station_chat_media', orphanOld, 2 * 3600 * 1000);
  await age('station_chat_media', orphanFresh, 10 * 60 * 1000);

  //  ── پشتیبانیِ پمپ ↔ مدیر ─────────────────────────────────────
  const s1 = await h.post('/api/pump/device/support/messages', { body: 'پرسشِ کهنه' }, { token: p.token });
  assert.equal(s1.status, 201, JSON.stringify(s1.body));
  const s2 = await h.post('/api/pump/device/support/messages', { body: 'پرسشِ تازه' }, { token: p.token });
  const pumpThread = s1.body.thread.id;
  await age('support_messages', s1.body.message.id, 20 * DAY);
  const unreadBefore = Number((await one('SELECT unread_admin FROM support_threads WHERE id=$1', [pumpThread])).unread_admin);

  //  رشتهٔ دکان: قاعدهٔ رله مالِ پمپ است و این دست نمی‌خورد
  const shopThread = newId('thr');
  await query(
    `INSERT INTO support_threads (id, app, user_id, device_uid, status, created_at, updated_at)
     VALUES ($1,'shop','','dev-shop','open',$2,$2)`, [shopThread, now()]
  );
  const shopMsg = newId('msg');
  await query(
    `INSERT INTO support_messages (id, thread_id, sender, body, created_at) VALUES ($1,$2,'user','دکانِ کهنه',$3)`,
    [shopMsg, shopThread, now() - 40 * DAY]
  );

  const maxSeq = Number((await one('SELECT max(seq) AS s FROM station_chat_messages')).s);

  //  ── پاک‌سازی ───────────────────────────────────────────────────
  const out = await relay.sweep();
  assert.equal(out.chatMessages, 2, JSON.stringify(out));
  assert.equal(out.chatMedia, 2, JSON.stringify(out));       //  رسانهٔ پیامِ کهنه + یتیمِ کهنه
  assert.equal(out.supportMessages, 1, JSON.stringify(out));

  const ids = (await query('SELECT id FROM station_chat_messages')).rows.map(r => r.id);
  assert.ok(!ids.includes(oldText.id) && !ids.includes(oldImg.id), 'پیامِ کهنه ماند');
  assert.ok(ids.includes(freshText.id) && ids.includes(freshImg.id), 'پیامِ تازه رفت');

  const media = (await query('SELECT id FROM station_chat_media')).rows.map(r => r.id);
  assert.ok(!media.includes(oldMedia), 'رسانهٔ پیامِ کهنه ماند');
  assert.ok(!media.includes(orphanOld), 'یتیمِ کهنه ماند');
  assert.ok(media.includes(freshMedia), 'رسانهٔ پیامِ زنده رفت');
  assert.ok(media.includes(orphanFresh), 'بارگذاریِ تازهٔ در راه رفت');

  const sup = (await query('SELECT id FROM support_messages WHERE thread_id=$1', [pumpThread])).rows.map(r => r.id);
  assert.deepEqual(sup, [s2.body.message.id]);
  assert.ok(await one('SELECT id FROM support_threads WHERE id=$1', [pumpThread]), 'رشتهٔ پمپ پاک شد');
  assert.equal(Number((await one('SELECT unread_admin FROM support_threads WHERE id=$1', [pumpThread])).unread_admin), unreadBefore);
  assert.ok(await one('SELECT id FROM support_messages WHERE id=$1', [shopMsg]), 'پیامِ دکان پاک شد');

  //  رشتهٔ چت هم سرِ جایش است (بلاک و «تا کجا خوانده شد»)
  assert.ok(await one('SELECT acct FROM station_chat_threads WHERE station_id=$1 AND acct=$2', [p.stationId, 'd7']));

  //  ── دنباله ─────────────────────────────────────────────────────
  const next = (await h.post(pub(p.code, ''), { name: 'محمد', text: 'پس از پاک‌سازی' })).body.message;
  assert.ok(next.seq > maxSeq, `seq از نو شد: ${next.seq} ≤ ${maxSeq}`);

  const inbox0 = await h.get('/api/pump/device/chat/inbox?after=0', { token: p.token });
  assert.equal(inbox0.status, 200);
  assert.deepEqual(inbox0.body.messages.map(m => m.id), [freshText.id, freshImg.id, next.id]);
  assert.equal(inbox0.body.relayDays, 15);

  const inboxOld = await h.get(`/api/pump/device/chat/inbox?after=${oldImg.seq}`, { token: p.token });
  assert.deepEqual(inboxOld.body.messages.map(m => m.id), [freshText.id, freshImg.id, next.id]);
  const inboxNew = await h.get(`/api/pump/device/chat/inbox?after=${freshImg.seq}`, { token: p.token });
  assert.deepEqual(inboxNew.body.messages.map(m => m.id), [next.id]);

  const thread = await h.get(`/api/pump/device/chat/d7?after=${oldText.seq}`, { token: p.token });
  assert.equal(thread.status, 200);
  assert.equal(thread.body.relayDays, 15);
  assert.equal(thread.body.messages[0].id, freshText.id);

  //  ── رسانهٔ رفته ────────────────────────────────────────────────
  const goneDev = await h.get(`/api/pump/device/chat/media/${oldMedia}`, { token: p.token });
  assert.equal(goneDev.status, 404);
  assert.equal(goneDev.body.error.code, 'media_gone', JSON.stringify(goneDev.body));
  assert.match(goneDev.body.error.message, /منقضی/);

  const gonePub = await h.get(`/api/pump/public/${p.code}/acct/d7/chat/media/${orphanOld}?k=${KEY}`);
  assert.equal(gonePub.status, 404);
  assert.equal(gonePub.body.error.code, 'media_gone', JSON.stringify(gonePub.body));

  //  رسانهٔ زنده همچنان می‌آید
  const live = await fetch(`${h.base()}/api/pump/device/chat/media/${freshMedia}`, {
    headers: { Authorization: `Bearer ${p.token}` },
  });
  assert.equal(live.status, 200);
  assert.equal(Buffer.from(await live.arrayBuffer()).toString(), 'fake-image-bytes');
});

test('relayDays: 15 در رشته‌ها، صندوق، صفحهٔ کیو‌آر و پشتیبانیِ پمپ', async () => {
  const p = await pump('pc-relay-2', 'relay-two');
  await h.post(pub(p.code, ''), { name: 'محمد', text: 'سلام' });

  const threads = await h.get('/api/pump/device/chat/threads', { token: p.token });
  assert.equal(threads.body.relayDays, 15);
  const pubThread = await h.get(pub(p.code, ''));
  assert.equal(pubThread.status, 200);
  assert.equal(pubThread.body.relayDays, 15);
  const support = await h.get('/api/pump/device/support/thread', { token: p.token });
  assert.equal(support.status, 200, JSON.stringify(support.body));
  assert.equal(support.body.relayDays, 15);
});

test('پاک‌سازیِ دوباره کاری نمی‌کند و چیزِ تازه را نمی‌برد', async () => {
  const again = await relay.sweep();
  assert.deepEqual(again, { chatMessages: 0, chatMedia: 0, supportMessages: 0 });
});
