'use strict';
/**
 * S78 (§۱۹.۱۲) — «دسترسیِ کاربر لغو/نقشِ او تغییر کند؛ کانالِ زنده و endpointهای قبلی فوراً رد شوند.»
 *
 * سه لایهٔ `src/lib/sync-v1-live.js`:
 *   ۱) `accessChanged` — بستنِ سوکت در **همان درخواستِ** لغو (برداشتن، تغییرِ نقش، خروج، …)
 *   ۲) سنجشِ دوباره پیش از هر پیامِ اثرگذار («scan») و پیش از هر خبر («changed»)، حافظهٔ ≤ ۵ ثانیه
 *      — این‌جا ۲۰۰ میلی‌ثانیه تا آزمون منتظر نماند (`SYNC_LIVE_RECHECK_MS`)
 *   ۳) بستن سرِ انقضای توکن (4403)
 * و تورِ ایمنیِ تپش (`dropRevoked`، TEN-02/#534) وقتی هوک دور زده شده.
 *
 * ⛔ بی خواب برای «رسیدن»: هر «نرسیدن» با کنترلِ مثبت روی همان گیرنده سنجیده می‌شود.
 */
process.env.SYNC_LIVE_RECHECK_MS = '200';

const test = require('node:test');
const assert = require('node:assert/strict');
const { WebSocket } = require('ws');
const h = require('./helpers');
const live = require('../src/lib/sync-v1-live');
const { query, now } = require('../src/db');
const { hashToken } = require('../src/lib/tokens');

let base = '';
const opened = [];
test.before(async () => { base = await h.start(); live.attach(h.server()); });
test.after(async () => { for (const ws of opened) { try { ws.terminate(); } catch { /* رفته */ } } await h.stop(); });

async function loginOn(u, deviceId) {
  const r = await h.post('/api/auth/login', { identifier: u.email, password: u.password, app: 'shop', device: { deviceId, name: deviceId, platform: 'test' } });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  return r.body.accessToken;
}

/** صاحب + دو شاگرد (M و N) در یک دکان؛ صاحب با دو دستگاه (گوشی و کامپیوتر) */
async function shopWithStaff(label, tag) {
  const owner = await h.newUser(`صاحبِ ${label}`);
  const made = await h.post('/api/shop', { name: `دکانِ ${label}` }, { token: owner.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const code = (await h.get('/api/shop/staff-code', { token: owner.accessToken })).body.code;
  const staff = async (name) => {
    const u = await h.newUser(name);
    const j = await h.post('/api/shop/join', { code }, { token: u.accessToken });
    assert.ok(j.status < 300, JSON.stringify(j.body));
    return u;
  };
  const m = await staff(`M ${label}`);
  const n = await staff(`N ${label}`);
  const members = (await h.get('/api/shop/members', { token: owner.accessToken })).body.members;
  const idOf = (u) => members.find((x) => x.userId === u.user.id).id;
  return {
    shopId: made.body.shop.id, owner, m, n, mid: idOf(m), nid: idOf(n),
    ownerPhone: await loginOn(owner, `${tag}-A`), ownerDesk: await loginOn(owner, `${tag}-D`),
  };
}

/** سوکتِ زنده؛ `legacy` ⇒ راهِ برنامه‌های کهنه (`?token=`) به‌جای بلیت */
async function socket(token, deviceId, { legacy = false } = {}) {
  let cred = `token=${encodeURIComponent(token)}`;
  if (!legacy) {
    const t = await h.api('POST', '/api/sync/v1/live/ticket', { token, body: {}, headers: { 'X-App': 'shop' } });
    assert.equal(t.status, 200, JSON.stringify(t.body));
    cred = `ticket=${encodeURIComponent(t.body.ticket)}`;
  }
  const ws = new WebSocket(`${base.replace('http', 'ws')}/api/sync/v1/live?${cred}&device_id=${deviceId}&app=shop`);
  opened.push(ws);
  ws.msgs = [];
  ws.closed = null;
  const waiters = new Set();
  const poke = () => { for (const w of waiters) w(); };
  ws.on('message', (m) => { ws.msgs.push(JSON.parse(String(m))); poke(); });
  ws.on('close', (code, reason) => { ws.closed = { code, reason: String(reason) }; poke(); });
  ws.until = (pred, what, ms = 5000) => new Promise((ok, no) => {
    const check = () => { const v = pred(); if (v) { clearTimeout(t0); waiters.delete(check); ok(v); } };
    const t0 = setTimeout(() => { waiters.delete(check); no(new Error(`${deviceId}: ${what} — پیام‌ها ${JSON.stringify(ws.msgs)}`)); }, ms);
    waiters.add(check);
    check();
  });
  ws.send2 = (o) => ws.send(JSON.stringify(o));
  await new Promise((ok, no) => { ws.on('open', ok); ws.on('error', no); });
  await ws.until(() => ws.msgs.find((x) => x.event === 'hello'), 'hello');
  return ws;
}

const room = (shopId) => live.listeners({ app: 'shop', accountKind: 'shop', accountId: shopId });
const changedCount = (ws) => ws.msgs.filter((x) => x.event === 'changed').length;
let opN = 0;
async function pushFrom(token, device) {
  const id = `01JS78${String(Date.now()).slice(-8)}${String(++opN).padStart(8, '0')}`;
  const r = await h.api('POST', '/api/sync/v1/push', {
    token, headers: { 'X-App': 'shop', 'X-Device': device },
    body: { device_id: device, schema_version: 2, ops: [{ op_id: id, ts: Date.now(), table: 'products', row_id: `p-${id}`, type: 'insert', fields: { name: 'کالا', unit: 'دانه', salePrice: 10, active: true, createdAt: Date.now() } }] },
  });
  assert.equal(r.status, 200, JSON.stringify(r.body));
}
/** «scan» ِ `from` به کامپیوترِ صاحب نرسد — کنترلِ مثبت: اسکنِ گوشیِ صاحب پس از آن می‌رسد */
async function scanBlocked(from, desk, ownerPhoneWs, code) {
  if (from.readyState === WebSocket.OPEN) from.send2({ type: 'relay', event: 'scan', code, pair: '' });
  const ctl = `${code}-CTL`;
  ownerPhoneWs.send2({ type: 'relay', event: 'scan', code: ctl, pair: '' });
  await desk.until(() => desk.msgs.find((x) => x.code === ctl), 'کنترلِ مثبت: اسکنِ گوشیِ صاحب');
  assert.equal(desk.msgs.some((x) => x.code === code), false, `اسکنِ ${code} به صندوقِ کامپیوترِ صاحب رسید`);
}

test('برداشتنِ عضو ⇒ همهٔ سوکت‌هایش (بلیت و ?token= ِ کهنه) در همان درخواست بسته؛ عضوِ دیگر و صاحب دست نخورده', async () => {
  const s = await shopWithStaff('برداشتن', 'rm');
  const mTicket = await socket(s.m.accessToken, 'rm-M1');
  const mLegacy = await socket(s.m.accessToken, 'rm-M2', { legacy: true });
  const nWs = await socket(s.n.accessToken, 'rm-N');
  const desk = await socket(s.ownerDesk, 'rm-D');
  const phone = await socket(s.ownerPhone, 'rm-A');
  assert.equal(room(s.shopId), 5);

  const r = await h.post(`/api/shop/members/${s.mid}/remove`, {}, { token: s.owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  //  «در همان درخواست»: پاسخ که رسید، هر دو سوکت از اتاق بیرون‌اند (سنجشِ سمتِ سرور، بی انتظار)
  assert.equal(room(s.shopId), 3, 'سوکت‌های عضوِ برداشته‌شده هنوز در اتاق‌اند');
  for (const ws of [mTicket, mLegacy]) {
    await ws.until(() => ws.closed, 'بسته نشد');
    assert.deepEqual(ws.closed, { code: 4401, reason: 'session_revoked' });
  }
  //  عضوِ دیگر و صاحب: باز، و خبرِ تازه را می‌گیرند
  await pushFrom(s.ownerPhone, 'rm-A');
  for (const ws of [nWs, desk]) await ws.until(() => changedCount(ws) >= 1, '«changed» پس از برداشتنِ دیگری');
  assert.equal(nWs.closed, null);
  assert.equal(desk.closed, null);
  assert.equal(phone.closed, null);
  //  اسکنِ عضوِ دیگر هنوز به کامپیوتر می‌رسد
  nWs.send2({ type: 'relay', event: 'scan', code: 'N-OK', pair: '' });
  await desk.until(() => desk.msgs.find((x) => x.code === 'N-OK'), 'اسکنِ عضوِ دیگر');
  for (const ws of [nWs, desk, phone]) ws.close();
});

test('تغییرِ نقش (مدیر ⇐ شاگرد) ⇒ سوکت همان لحظه 4401 access_changed؛ وصلِ دوباره با نقشِ تازه آزاد؛ صاحب دست نخورده', async () => {
  const s = await shopWithStaff('نقش', 'role');
  assert.equal((await h.patch(`/api/shop/members/${s.mid}`, { role: 'manager' }, { token: s.owner.accessToken })).status, 200);
  const m1 = await socket(s.m.accessToken, 'role-M');
  const desk = await socket(s.ownerDesk, 'role-D');
  const r = await h.patch(`/api/shop/members/${s.mid}`, { role: 'staff' }, { token: s.owner.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(room(s.shopId), 1, 'سوکتِ مدیرِ پیشین پس از پاسخ هنوز در اتاق است');
  await m1.until(() => m1.closed, 'بسته نشد');
  assert.deepEqual(m1.closed, { code: 4401, reason: 'access_changed' });
  //  نشست زنده است: بلیتِ تازه و سوکتِ تازه، و خبر می‌رسد
  const m2 = await socket(s.m.accessToken, 'role-M');
  await pushFrom(s.ownerPhone, 'role-A');
  await m2.until(() => changedCount(m2) >= 1, '«changed» برای شاگرد پس از وصلِ دوباره');
  await desk.until(() => changedCount(desk) >= 1, '«changed» برای صاحب');
  assert.equal(desk.closed, null);
  m2.close(); desk.close();
});

test('باطل‌کردنِ یک نشست (خروج) ⇒ فقط سوکتِ همان نشست بسته؛ دستگاهِ دیگرِ همان آدم باز می‌ماند', async () => {
  const s = await shopWithStaff('خروج', 'out');
  const tok1 = await loginOn(s.m, 'out-M1');
  const tok2 = await loginOn(s.m, 'out-M2');
  const w1 = await socket(tok1, 'out-M1');
  const w2 = await socket(tok2, 'out-M2');
  const desk = await socket(s.ownerDesk, 'out-D');
  const r = await h.api('POST', '/api/auth/logout', { token: tok1, body: {} });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  await w1.until(() => w1.closed, 'سوکتِ نشستِ خارج‌شده بسته نشد');
  assert.deepEqual(w1.closed, { code: 4401, reason: 'session_revoked' });
  await pushFrom(s.ownerPhone, 'out-A');
  await w2.until(() => changedCount(w2) >= 1, 'دستگاهِ دیگرِ همان آدم «changed» نگرفت');
  assert.equal(w2.closed, null);
  w2.close(); desk.close();
});

test('انقضای توکن ⇒ سوکت سرِ همان لحظه 4403 expired (نه پیش از آن)؛ نشستِ تازه دوباره وصل می‌شود', async () => {
  const s = await shopWithStaff('انقضا', 'exp');
  const tok = await loginOn(s.m, 'exp-M');
  const left = 1200;
  await query('UPDATE tokens SET expires_at=$2 WHERE token_hash=$1', [hashToken(tok), now() + left]);
  const t0 = Date.now();
  const ws = await socket(tok, 'exp-M');
  //  پیش از انقضا باز است و کار می‌کند
  ws.send2({ type: 'ping' });
  await ws.until(() => ws.msgs.find((x) => x.event === 'pong'), 'pong پیش از انقضا');
  assert.equal(ws.closed, null);
  await ws.until(() => ws.closed, 'سرِ انقضا بسته نشد', left + 4000);
  assert.deepEqual(ws.closed, { code: 4403, reason: 'expired' });
  assert.ok(Date.now() - t0 >= left - 50, `زودتر از انقضا بسته شد (${Date.now() - t0}ms)`);
  //  همان توکن دیگر وصل نمی‌شود؛ نشستِ تازه (همان راهِ همیشگیِ برنامه) می‌شود
  const t = await h.api('POST', '/api/sync/v1/live/ticket', { token: tok, body: {}, headers: { 'X-App': 'shop' } });
  assert.equal(t.status, 401);
  const fresh = await socket(await loginOn(s.m, 'exp-M'), 'exp-M');
  assert.equal(fresh.closed, null);
  fresh.close();
});

test('هوکِ لغو دور زده شد (SQL ِ خام) ⇒ «scan» به صندوقِ صاحب نمی‌رسد، «changed» هم نه، و سوکت بسته می‌شود', async () => {
  const s = await shopWithStaff('دورزدن', 'byp');
  const mWs = await socket(s.m.accessToken, 'byp-M');
  const nWs = await socket(s.n.accessToken, 'byp-N');
  const desk = await socket(s.ownerDesk, 'byp-D');
  const phone = await socket(s.ownerPhone, 'byp-A');
  //  کنترلِ مثبت: پیش از لغو، اسکنِ M می‌رسد
  mWs.send2({ type: 'relay', event: 'scan', code: 'M-BEFORE', pair: '' });
  await desk.until(() => desk.msgs.find((x) => x.code === 'M-BEFORE'), 'اسکنِ M پیش از لغو');

  //  لغو بی `accessChanged` (راهِ فراموش‌شدهٔ آینده): نشستِ M و نقشِ N هر دو با SQL ِ خام
  await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL', [now(), s.m.user.id]);
  await query(`UPDATE shop_members SET role='courier' WHERE id=$1`, [s.nid]);
  await new Promise((r) => setTimeout(r, live.RECHECK_MS + 50));   //  حافظهٔ سنجش (این‌جا ۲۰۰ms) گذشت
  assert.equal(room(s.shopId), 4, 'پیش‌فرضِ آزمون: هوک صدا زده نشده');

  await scanBlocked(mWs, desk, phone, 'M-AFTER');
  await mWs.until(() => mWs.closed, 'سوکتِ نشستِ باطل پس از پیام بسته نشد');
  assert.deepEqual(mWs.closed, { code: 4401, reason: 'session_revoked' });
  //  «changed»: N (نقشش دیگر به دفتر راه ندارد) نمی‌گیرد و بسته می‌شود؛ صاحب می‌گیرد
  const before = changedCount(nWs);
  await pushFrom(s.ownerPhone, 'byp-A');
  await desk.until(() => changedCount(desk) >= 1, '«changed» برای صاحب');
  await nWs.until(() => nWs.closed, 'سوکتِ عضوی که دیگر دسترسی ندارد پیش از «changed» بسته نشد');
  assert.equal(changedCount(nWs), before, 'عضوِ بی‌دسترسی «changed» گرفت');
  assert.equal(nWs.closed.code, 4401);
  assert.equal(desk.closed, null);
  assert.equal(phone.closed, null);
  desk.close(); phone.close();
});

test('تورِ ایمنیِ تپش (TEN-02): نشستِ باطلِ سوکتِ بی‌کار، بی هوک و بی پیام، در تپشِ بعدی 4401 بسته', async () => {
  const s = await shopWithStaff('تپش', 'beat');
  const mWs = await socket(s.m.accessToken, 'beat-M');
  const desk = await socket(s.ownerDesk, 'beat-D');
  await query('UPDATE tokens SET revoked_at=$1 WHERE subject_id=$2 AND revoked_at IS NULL', [now(), s.m.user.id]);
  //  ≥ ۱: سوکتِ آزمونِ شکست‌خوردهٔ دیگری هم شاید باشد؛ خودِ M پایین سنجیده می‌شود
  assert.ok(await live.dropRevoked(h.server().__syncLive) >= 1);
  await mWs.until(() => mWs.closed, 'بسته نشد');
  assert.deepEqual(mWs.closed, { code: 4401, reason: 'session_revoked' });
  assert.equal(desk.closed, null);
  assert.equal(room(s.shopId), 1);
  desk.close();
});
