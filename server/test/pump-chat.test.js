'use strict';
/**
 * ══ چتِ پشتیبانی: مشتریِ کیو‌آر ↔ صاحبِ پمپ ══════════════════════════════
 *   ۱) مشتری با رمزِ حساب می‌نویسد، صاحبِ پمپ در صندوق می‌بیند — با نامش
 *   ۲) صاحبِ پمپ جواب می‌دهد ⇒ پوش به مرورگرِ مشتری (فرستندهٔ ساختگی)
 *   ۳) عکس/صدا بالا می‌رود و از دو طرف خوانده می‌شود؛ ویدیوی بزرگ رد می‌شود
 *   ۴) پاک کردن نرم است؛ مشتری فقط مالِ خودش را
 *   ۵) بلاک: مشتری نمی‌تواند بنویسد، می‌تواند بخواند؛ رفعِ بلاک
 *   ۶) رمزِ غلط ⇒ ۴۰۴؛ پمپِ دیگر رسانهٔ این پمپ را نمی‌بیند
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');
const chat = require('../src/lib/station-chat');

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

async function pump(uid, code) {
  const login = await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' });
  const vip = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 60 }, { token: login.body.token });
  const r = await h.post('/api/pump/device/activate', {
    code: vip.body.code, device: { uid, name: 'pc', platform: 'windows' }, station: { code },
  });
  assert.equal(r.status, 201, JSON.stringify(r.body));
  const token = r.body.deviceToken;
  //  کیو‌آرِ زندهٔ حسابِ d7 — همان قفل
  await h.put('/api/pump/device/files/acct-d7', { data: { v: 1, k: KEY, at: 1, d: { n: 'محمد هارون' } } }, { token });
  return { token, code: r.body.station.code };
}

const pub = (code, path, k = KEY) => `/api/pump/public/${code}/acct/d7/chat${path}?k=${k}`;

async function raw(path, mime, buf, token) {
  const res = await fetch(`${h.base()}${path}`, {
    method: 'POST', headers: { 'Content-Type': mime, ...(token ? { Authorization: `Bearer ${token}` } : {}) }, body: buf,
  });
  return { status: res.status, body: await res.json().catch(() => null) };
}

test('مشتری می‌نویسد، صاحبِ پمپ با نامِ او می‌بیند؛ جوابِ پمپ پوش می‌شود', async () => {
  const p = await pump('pc-chat-1', 'chat-one');

  const c1 = await h.post(pub(p.code, ''), { name: 'هارون', text: 'سلام، حسابم درست است؟' });
  assert.equal(c1.status, 201, JSON.stringify(c1.body));
  assert.equal(c1.body.message.from, 'c');

  const threads = await h.get('/api/pump/device/chat/threads', { token: p.token });
  assert.equal(threads.body.threads.length, 1);
  assert.equal(threads.body.threads[0].name, 'هارون');
  assert.equal(threads.body.threads[0].unread, 1);
  assert.equal(threads.body.threads[0].last.text, 'سلام، حسابم درست است؟');

  //  اشتراکِ پوشِ مرورگرِ مشتری
  const sub = await h.post(pub(p.code, '/push'), {
    subscription: { endpoint: 'https://push.example/abc', keys: { p256dh: 'x', auth: 'y' } }, url: 'https://yaqobipump.top/view/#d=1',
  });
  assert.equal(sub.status, 201);

  //  فرستندهٔ ساختگی به جای web-push
  const sent = [];
  const st = (await query('SELECT id FROM stations WHERE code=$1', [p.code])).rows[0];
  const o1 = await h.post('/api/pump/device/chat/d7', { name: 'پمپ یعقوبی', text: 'بله، ۶۴۰ لیتر مانده' }, { token: p.token });
  assert.equal(o1.status, 201);
  const n = await chat.pushTo(st.id, 'd7', { title: 'پمپ', body: 'آزمون' }, {
    sender: async (s, body) => { sent.push({ s, body: JSON.parse(body) }); },
  });
  assert.equal(n, 1);
  assert.equal(sent[0].s.endpoint, 'https://push.example/abc');
  assert.equal(sent[0].body.url, 'https://yaqobipump.top/view/#d=1');

  //  مشتری هر دو پیام را می‌بیند و «خوانده شد» می‌گوید
  const list = await h.get(pub(p.code, ''));
  assert.equal(list.status, 200);
  assert.equal(list.body.messages.length, 2);
  assert.equal(list.body.messages[1].from, 'o');
  assert.equal(list.body.messages[1].name, 'پمپ یعقوبی');
  assert.equal(list.headers.get('access-control-allow-origin'), '*');
  assert.ok(typeof list.body.vapid === 'string');

  const after = await h.get(pub(p.code, '') + '&after=' + list.body.messages[0].seq);
  assert.equal(after.body.messages.length, 1);

  await h.post('/api/pump/device/chat/d7/seen', { seq: list.body.messages[0].seq }, { token: p.token });
  const t2 = await h.get('/api/pump/device/chat/threads', { token: p.token });
  assert.equal(t2.body.threads[0].unread, 0);
});

test('عکس و صدا از دو طرف؛ ویدیوی بزرگ‌تر از حد رد می‌شود', async () => {
  const p = await pump('pc-chat-2', 'chat-two');
  const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47]), Buffer.alloc(2000, 7)]);
  const up = await raw(pub(p.code, '/media'), 'image/png', png);
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.kind, 'image');

  const m = await h.post(pub(p.code, ''), { name: 'هارون', kind: 'image', mediaId: up.body.mediaId });
  assert.equal(m.status, 201, JSON.stringify(m.body));

  //  صاحبِ پمپ همان عکس را می‌گیرد
  const got = await fetch(`${h.base()}/api/pump/device/chat/media/${up.body.mediaId}`, {
    headers: { Authorization: `Bearer ${p.token}` },
  });
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/png');
  assert.equal((await got.arrayBuffer()).byteLength, png.length);

  //  صاحبِ پمپ صدا می‌فرستد، مشتری می‌گیرد
  const voice = await raw('/api/pump/device/chat/d7/media', 'audio/webm', Buffer.alloc(500, 1), p.token);
  assert.equal(voice.status, 201, JSON.stringify(voice.body));
  const vm = await h.post('/api/pump/device/chat/d7', { kind: 'audio', mediaId: voice.body.mediaId }, { token: p.token });
  assert.equal(vm.status, 201, JSON.stringify(vm.body));
  const back = await fetch(`${h.base()}${pub(p.code, '/media/' + voice.body.mediaId)}`);
  assert.equal(back.status, 200);
  assert.equal(back.headers.get('content-type'), 'audio/webm');

  //  نوعِ اشتباه و فایلِ بزرگ
  const wrongKind = await h.post(pub(p.code, ''), { kind: 'video', mediaId: up.body.mediaId });
  assert.equal(wrongKind.status, 400);
  const big = await raw(pub(p.code, '/media'), 'video/mp4', Buffer.alloc(chat.LIMITS.video + 1, 0));
  assert.ok(big.status === 400 || big.status === 413, String(big.status));
  const doc = await raw(pub(p.code, '/media'), 'application/pdf', Buffer.alloc(10, 0));
  assert.equal(doc.status, 400);
});

test('پاک کردن نرم است و مشتری فقط مالِ خودش را پاک می‌کند', async () => {
  const p = await pump('pc-chat-3', 'chat-three');
  const c = await h.post(pub(p.code, ''), { name: 'هارون', text: 'اشتباه فرستادم' });
  const o = await h.post('/api/pump/device/chat/d7', { text: 'باشد' }, { token: p.token });

  const notMine = await h.del(pub(p.code, '/' + o.body.message.id));
  assert.equal(notMine.status, 403);

  const mine = await h.del(pub(p.code, '/' + c.body.message.id));
  assert.equal(mine.status, 200);
  assert.equal(mine.body.message.deleted, true);
  assert.equal(mine.body.message.text, '');

  const ownerDel = await h.del('/api/pump/device/chat/message/' + o.body.message.id, { token: p.token });
  assert.equal(ownerDel.status, 200);

  const list = await h.get(pub(p.code, ''));
  assert.equal(list.body.messages.length, 2);
  assert.ok(list.body.messages.every(m => m.deleted && m.text === ''));
});

test('بلاک: مشتری نمی‌نویسد ولی می‌خواند؛ رفعِ بلاک', async () => {
  const p = await pump('pc-chat-4', 'chat-four');
  await h.post(pub(p.code, ''), { name: 'مزاحم', text: 'x' });
  const b = await h.post('/api/pump/device/chat/d7/block', {}, { token: p.token });
  assert.equal(b.body.blocked, true);

  const denied = await h.post(pub(p.code, ''), { text: 'باز هم' });
  assert.equal(denied.status, 403);
  assert.equal(denied.body.error.code, 'blocked');
  const read = await h.get(pub(p.code, ''));
  assert.equal(read.status, 200);
  assert.equal(read.body.blocked, true);

  await h.del('/api/pump/device/chat/d7/block', { token: p.token });
  const again = await h.post(pub(p.code, ''), { text: 'ببخشید' });
  assert.equal(again.status, 201);
});

test('رمزِ غلط ⇒ ۴۰۴، و پمپِ دیگر به رسانهٔ این پمپ نمی‌رسد', async () => {
  const p = await pump('pc-chat-5', 'chat-five');
  const q = await pump('pc-chat-6', 'chat-six');
  const up = await raw(pub(p.code, '/media'), 'image/jpeg', Buffer.alloc(100, 3));
  assert.equal(up.status, 201);

  assert.equal((await h.get(pub(p.code, '', 'abcdef0123456789abce'))).status, 404);
  assert.equal((await h.post(pub(p.code, '', 'zzzz'), { text: 'x' })).status, 404);

  const other = await fetch(`${h.base()}/api/pump/device/chat/media/${up.body.mediaId}`, {
    headers: { Authorization: `Bearer ${q.token}` },
  });
  assert.equal(other.status, 404);
  const noTok = await fetch(`${h.base()}/api/pump/device/chat/threads`);
  assert.equal(noTok.status, 401);
});
