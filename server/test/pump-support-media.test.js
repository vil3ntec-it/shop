'use strict';
/**
 * ══ رسانه در پشتیبانیِ پمپ — عکس، ویدیو و پیامِ صوتی، فقط در عبور ═══════
 *
 * خواستهٔ صاحب سامانه: «در چتِ پشتیبانیِ برنامهٔ پمپ عکس، صدا و ویدیو
 * نمی‌رود… این‌ها نباید روی سرور بمانند — سرور فقط به طرفِ دیگر می‌رساند
 * و هر طرف روی دستگاهِ خودش نگه می‌دارد.»
 *
 *   ۱) پمپ عکس می‌فرستد ⇒ مدیر می‌گیرد ⇒ از سرور پاک؛ گرفتنِ خودِ پمپ پاک نمی‌کند
 *   ۲) مدیر صدا می‌فرستد ⇒ پمپ می‌گیرد ⇒ پاک؛ HEAD پاک نمی‌کند؛ گوشیِ صاحب هم همان در
 *   ۳) پمپِ دیگر نه رسانه را می‌بیند نه در پیامش می‌نشاند؛ رسانهٔ طرفِ دیگر هم نه
 *   ۴) نوعِ نادرست ⇒ ۴۰۰ bad_media؛ بزرگ‌تر از ۲۵ مگابایت ⇒ ۴۱۳ too_large
 *   ۵) رشتهٔ دکان رسانه نمی‌گیرد
 *   ۶) پاک‌سازی: کهنه و یتیمِ کهنه می‌روند؛ تازه و یتیمِ تازه می‌مانند
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

const adminToken = async () =>
  (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;

async function pumpDevice(uid) {
  const t = await adminToken();
  const made = await h.post('/api/admin/pump/vip-codes', { plan: 'custom', days: 90 }, { token: t });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const on = await h.post('/api/pump/device/activate', {
    code: made.body.code,
    device: { uid, name: 'کامپیوترِ پمپ', platform: 'windows' },
    station: { code: uid, name: `پمپِ ${uid}` },
  });
  assert.equal(on.status, 201, JSON.stringify(on.body));
  return { token: on.body.deviceToken, stationId: on.body.station.id };
}

const PNG = Buffer.from('89504e470d0a1a0a-fake-png-bytes-for-test', 'utf8');
const OGG = Buffer.from('OggS-fake-voice-note-bytes', 'utf8');

async function mediaRow(id) {
  return one('SELECT id, uploader, size FROM support_media WHERE id=$1', [id]);
}

/** پاک شدن پس از `finish` است — چند لحظه صبر، بی ساعتِ ثابت. */
async function gone(id) {
  for (let i = 0; i < 50; i++) {
    if (!(await mediaRow(id))) return true;
    await new Promise(r => setTimeout(r, 20));
  }
  return false;
}

async function threadIdOf(stationId) {
  return (await one(`SELECT id FROM support_threads WHERE app='pump' AND station_id=$1`, [stationId])).id;
}

test('۱) پمپ عکس می‌فرستد؛ مدیر که گرفت از سرور پاک می‌شود، گرفتنِ خودِ پمپ نه', async () => {
  const d = await pumpDevice('pc-media-1');
  const t = await adminToken();

  const up = await h.raw('POST', '/api/pump/device/support/media', PNG,
    { token: d.token, headers: { 'Content-Type': 'image/png' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.ok, true);
  assert.match(up.body.mediaId, /^smd/);
  assert.equal(up.body.kind, 'image');
  const mid = up.body.mediaId;

  const sent = await h.post('/api/pump/device/support/messages',
    { kind: 'image', mediaId: mid }, { token: d.token });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.equal(sent.body.message.kind, 'image');
  assert.equal(sent.body.message.mediaId, mid);
  assert.equal(sent.body.message.body, '');
  assert.equal(sent.body.thread.lastMessage, '📷 عکس');
  assert.equal(sent.body.thread.unreadAdmin, 1);

  //  پیامِ متنی هم mediaId دارد — null
  const txt = await h.post('/api/pump/device/support/messages', { body: 'این عکسِ خطاست' }, { token: d.token });
  assert.equal(txt.status, 201, JSON.stringify(txt.body));
  assert.equal(txt.body.message.kind, 'text');
  assert.equal(txt.body.message.mediaId, null);

  //  فرستنده رسانهٔ خودش را می‌گیرد ⇒ چیزی پاک نمی‌شود
  const own = await h.download(`/api/pump/device/support/media/${mid}`, { token: d.token });
  assert.equal(own.status, 200);
  assert.ok(own.buffer.equals(PNG));
  await new Promise(r => setTimeout(r, 100));
  assert.ok(await mediaRow(mid), 'گرفتنِ فرستنده نباید پاک کند');

  //  مدیر: رشته پیام را با mediaId نشان می‌دهد
  const tid = await threadIdOf(d.stationId);
  const view = await h.get(`/api/admin/support/threads/${tid}`, { token: t });
  assert.equal(view.status, 200);
  const m = view.body.messages.find(x => x.kind === 'image');
  assert.equal(m.mediaId, mid);

  //  HEAD هیچ بایتی نمی‌برد ⇒ پاک نمی‌کند
  const head = await fetch(`${h.base()}/api/admin/support/media/${mid}`,
    { method: 'HEAD', headers: { Authorization: `Bearer ${t}` } });
  assert.equal(head.status, 200);
  await new Promise(r => setTimeout(r, 100));
  assert.ok(await mediaRow(mid), 'HEAD نباید پاک کند');

  const got = await h.download(`/api/admin/support/media/${mid}`, { token: t });
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'image/png');
  assert.equal(got.headers.get('content-length'), String(PNG.length));
  assert.ok(got.buffer.equals(PNG), 'همان بایت‌ها');
  assert.ok(await gone(mid), 'گیرنده که گرفت، رسانه از سرور می‌رود');

  //  دوباره ⇒ ۴۰۴ِ media_gone، از هر دو در
  const again = await h.get(`/api/admin/support/media/${mid}`, { token: t });
  assert.equal(again.status, 404);
  assert.equal(again.body.error.code, 'media_gone');
  const fromPc = await h.get(`/api/pump/device/support/media/${mid}`, { token: d.token });
  assert.equal(fromPc.status, 404);
  assert.equal(fromPc.body.error.code, 'media_gone');

  //  پیام می‌ماند و شناسه‌اش همان — فرستنده رسانه را در دفترِ خودش پیدا می‌کند
  const after = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(after.body.messages.find(x => x.kind === 'image').mediaId, mid);
});

test('۲) مدیر پیامِ صوتی می‌فرستد؛ پمپ که گرفت پاک می‌شود — گوشیِ صاحب هم همان در را دارد', async () => {
  const d = await pumpDevice('pc-media-2');
  const t = await adminToken();
  await h.post('/api/pump/device/support/messages', { body: 'سلام' }, { token: d.token });
  const tid = await threadIdOf(d.stationId);

  const up = await h.raw('POST', `/api/admin/support/threads/${tid}/media`, OGG,
    { token: t, headers: { 'Content-Type': 'audio/ogg; codecs=opus' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  assert.equal(up.body.kind, 'audio');
  assert.equal(up.body.mime, 'audio/ogg');
  const mid = up.body.mediaId;
  assert.equal((await mediaRow(mid)).uploader, 'admin');

  const sent = await h.post(`/api/admin/support/threads/${tid}/messages`,
    { kind: 'audio', mediaId: mid, body: '' }, { token: t });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  assert.equal(sent.body.message.mediaId, mid);
  assert.equal(sent.body.message.kind, 'audio');

  const th = await h.get('/api/pump/device/support/thread', { token: d.token });
  assert.equal(th.body.thread.lastMessage, '🎤 پیامِ صوتی');
  assert.equal(th.body.thread.unreadUser, 1);
  const msg = th.body.messages.find(x => x.kind === 'audio');
  assert.equal(msg.mediaId, mid);
  assert.equal(msg.sender, 'admin');

  //  مدیرِ فرستنده رسانهٔ خودش را می‌گیرد ⇒ پاک نمی‌شود
  const own = await h.download(`/api/admin/support/media/${mid}`, { token: t });
  assert.equal(own.status, 200);
  await new Promise(r => setTimeout(r, 100));
  assert.ok(await mediaRow(mid));

  const got = await h.download(`/api/pump/device/support/media/${mid}`, { token: d.token });
  assert.equal(got.status, 200);
  assert.equal(got.headers.get('content-type'), 'audio/ogg');
  assert.ok(got.buffer.equals(OGG));
  assert.ok(await gone(mid), 'پمپ که گرفت، از سرور می‌رود');

  //  ویدیو از درِ گوشیِ صاحب (توکنِ حساب) — همان رشته، همان قاعده
  const owner = await h.newUser('صاحبِ رسانه', 'pump');
  const join = await h.post('/api/pump/device/join-code', {}, { token: d.token });
  const claimed = await h.post('/api/pump/claim', { code: join.body.code }, { token: owner.accessToken });
  assert.equal(claimed.status, 201, JSON.stringify(claimed.body));
  const vid = await h.raw('POST', '/api/pump/support/media', Buffer.from('fake-mp4'),
    { token: owner.accessToken, headers: { 'Content-Type': 'video/mp4' } });
  assert.equal(vid.status, 201, JSON.stringify(vid.body));
  const vs = await h.post('/api/pump/support/messages',
    { kind: 'video', mediaId: vid.body.mediaId, body: 'فیلمِ خطا' }, { token: owner.accessToken });
  assert.equal(vs.status, 201, JSON.stringify(vs.body));
  assert.equal(vs.body.thread.id, tid, 'همان گفت‌وگو');
  assert.equal(vs.body.thread.lastMessage, '🎥 ویدیو · فیلمِ خطا');
  const gv = await h.download(`/api/admin/support/media/${vid.body.mediaId}`, { token: t });
  assert.equal(gv.status, 200);
  assert.ok(await gone(vid.body.mediaId));
});

test('۳) هر پمپ فقط رسانهٔ رشتهٔ خودش — و فقط رسانه‌ای که خودش فرستاده در پیامش', async () => {
  const a = await pumpDevice('pc-media-a');
  const b = await pumpDevice('pc-media-b');
  const t = await adminToken();

  const up = await h.raw('POST', '/api/pump/device/support/media', PNG,
    { token: a.token, headers: { 'Content-Type': 'image/jpeg' } });
  assert.equal(up.status, 201);
  const mid = up.body.mediaId;

  const peek = await h.get(`/api/pump/device/support/media/${mid}`, { token: b.token });
  assert.equal(peek.status, 404);
  assert.equal(peek.body.error.code, 'media_gone');
  assert.ok(await mediaRow(mid), 'پمپِ دیگر نه می‌بیند نه پاک می‌کند');

  const steal = await h.post('/api/pump/device/support/messages',
    { kind: 'image', mediaId: mid }, { token: b.token });
  assert.equal(steal.status, 400);
  assert.equal(steal.body.error.code, 'bad_media');

  //  رسانهٔ مدیر در پیامِ پمپ نمی‌نشیند
  await h.post('/api/pump/device/support/messages', { body: 'x' }, { token: a.token });
  const tid = await threadIdOf(a.stationId);
  const adm = await h.raw('POST', `/api/admin/support/threads/${tid}/media`, PNG,
    { token: t, headers: { 'Content-Type': 'image/png' } });
  const mix = await h.post('/api/pump/device/support/messages',
    { kind: 'image', mediaId: adm.body.mediaId }, { token: a.token });
  assert.equal(mix.status, 400);
  assert.equal(mix.body.error.code, 'bad_media');

  //  نوعِ پیام با رسانه نمی‌خواند، یا شناسه‌ای نیست
  const wrongKind = await h.post('/api/pump/device/support/messages',
    { kind: 'audio', mediaId: mid }, { token: a.token });
  assert.equal(wrongKind.status, 400);
  assert.equal(wrongKind.body.error.code, 'bad_media');
  const noId = await h.post('/api/pump/device/support/messages', { kind: 'image' }, { token: a.token });
  assert.equal(noId.status, 400);
  assert.equal(noId.body.error.code, 'bad_media');

  //  بی توکن هیچ
  const anon = await h.get(`/api/pump/device/support/media/${mid}`);
  assert.equal(anon.status, 401);
});

test('۴) فقط عکس، ویدیو و صدا — و نه بزرگ‌تر از ۲۵ مگابایت', async () => {
  const d = await pumpDevice('pc-media-4');
  for (const mime of ['application/pdf', 'text/plain', 'application/octet-stream']) {
    const r = await h.raw('POST', '/api/pump/device/support/media', Buffer.from('abc'),
      { token: d.token, headers: { 'Content-Type': mime } });
    assert.equal(r.status, 400, mime);
    assert.equal(r.body.error.code, 'bad_media', mime);
  }
  const empty = await h.raw('POST', '/api/pump/device/support/media', Buffer.alloc(0),
    { token: d.token, headers: { 'Content-Type': 'image/png' } });
  assert.equal(empty.status, 400);
  assert.equal(empty.body.error.code, 'bad_media');

  const big = await h.raw('POST', '/api/pump/device/support/media', Buffer.alloc(25 * 1024 * 1024 + 1),
    { token: d.token, headers: { 'Content-Type': 'video/mp4' } });
  assert.equal(big.status, 413);
  assert.equal(big.body.error.code, 'too_large');

  const edge = await h.raw('POST', '/api/pump/device/support/media', Buffer.alloc(25 * 1024 * 1024),
    { token: d.token, headers: { 'Content-Type': 'video/mp4' } });
  assert.equal(edge.status, 201, 'درست ۲۵ مگابایت پذیرفته است');

});

test('۵) رشتهٔ دکان رسانه نمی‌گیرد', async () => {
  const u = await h.newUser('دکان‌دار');
  const sent = await h.post('/api/support/messages', { body: 'سلام' }, { token: u.accessToken });
  assert.equal(sent.status, 201, JSON.stringify(sent.body));
  const tid = sent.body.thread?.id
    || (await one(`SELECT id FROM support_threads WHERE app='shop' AND user_id=$1`, [u.user.id])).id;
  const t = await adminToken();
  const r = await h.raw('POST', `/api/admin/support/threads/${tid}/media`, PNG,
    { token: t, headers: { 'Content-Type': 'image/png' } });
  assert.equal(r.status, 400);
  assert.equal(r.body.error.code, 'media_not_supported');
});

test('۶) پاک‌سازی: کهنه و یتیمِ کهنه می‌روند؛ تازه و یتیمِ تازه می‌مانند', async () => {
  const d = await pumpDevice('pc-media-6');
  const HOUR = 3600 * 1000;
  const DAY = 24 * HOUR;

  async function upload() {
    const r = await h.raw('POST', '/api/pump/device/support/media', PNG,
      { token: d.token, headers: { 'Content-Type': 'image/png' } });
    assert.equal(r.status, 201);
    return r.body.mediaId;
  }
  const oldSent = await upload();
  await h.post('/api/pump/device/support/messages', { kind: 'image', mediaId: oldSent }, { token: d.token });
  const freshSent = await upload();
  await h.post('/api/pump/device/support/messages', { kind: 'image', mediaId: freshSent }, { token: d.token });
  const orphanOld = await upload();
  const orphanFresh = await upload();
  //  رسانهٔ پیامی که دو ساعت پیش رفت ولی گیرنده هنوز نگرفته — باید بماند
  const sentTwoHoursAgo = await upload();
  await h.post('/api/pump/device/support/messages', { kind: 'image', mediaId: sentTwoHoursAgo }, { token: d.token });

  const age = (id, ms) => query('UPDATE support_media SET created_at=$2 WHERE id=$1', [id, now() - ms]);
  await age(oldSent, (relay.relayDays() + 1) * DAY);
  await age(orphanOld, 2 * HOUR);
  await age(sentTwoHoursAgo, 2 * HOUR);
  await age(orphanFresh, 10 * 60 * 1000);

  const out = await relay.sweep();
  assert.equal(out.supportMedia, 2, JSON.stringify(out));
  assert.equal(await mediaRow(oldSent), null);
  assert.equal(await mediaRow(orphanOld), null);
  assert.ok(await mediaRow(freshSent));
  assert.ok(await mediaRow(orphanFresh));
  assert.ok(await mediaRow(sentTwoHoursAgo));

  const again = await relay.sweep();
  assert.equal(again.supportMedia, 0);
});

test('۷) رشته که پاک شود، رسانه‌اش هم می‌رود (CASCADE)', async () => {
  const d = await pumpDevice('pc-media-7');
  const r = await h.raw('POST', '/api/pump/device/support/media', PNG,
    { token: d.token, headers: { 'Content-Type': 'image/png' } });
  const tid = await threadIdOf(d.stationId);
  await query('DELETE FROM support_threads WHERE id=$1', [tid]);
  assert.equal(await mediaRow(r.body.mediaId), null);
});

test('۴) ⛔ SVG (اسکریپت‌دار) رسانه نیست، و هر رسانه با nosniff و CSPِ sandbox می‌رود', async () => {
  const d = await pumpDevice('pc-svg-1');
  const svg = Buffer.from('<svg xmlns="http://www.w3.org/2000/svg"><script>alert(1)</script></svg>');
  const bad = await h.raw('POST', '/api/pump/device/support/media', svg, { token: d.token, headers: { 'Content-Type': 'image/svg+xml' } });
  assert.equal(bad.status, 400, JSON.stringify(bad.body));
  const up = await h.raw('POST', '/api/pump/device/support/media', PNG, { token: d.token, headers: { 'Content-Type': 'image/png' } });
  assert.equal(up.status, 201, JSON.stringify(up.body));
  const r = await fetch(`${h.base()}/api/pump/device/support/media/${up.body.mediaId}`, { headers: { Authorization: `Bearer ${d.token}` } });
  assert.equal(r.status, 200);
  assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
  assert.match(r.headers.get('content-security-policy') || '', /sandbox/);
});
