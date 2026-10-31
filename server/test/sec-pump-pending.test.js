'use strict';
/**
 * ⛔ کدِ پمپ — نیازمندِ اجازهٔ صاحب‌کار (شاخهٔ `claude/sec-pump-pending`).
 * بخش‌های پمپیِ برنامهٔ امنیت که از #590 و #588 بیرون آمدند تا درهای پمپ بی اجازه عوض نشوند:
 *
 *   SEC-API-02  بدنهٔ بزرگِ ‎/pump/device/state|report‎ فقط پس از سقفِ نرخ و توکنِ دستگاه خوانده می‌شود؛
 *               سقفِ ۳۴mb ِ ‎/report/‎ (که الگویش در Express 5 مرده بود) زنده است
 *   SEC-API-05  پاسخِ درهای پمپ CSP ِ «صفحه نیست» دارد
 *   SEC-AUTH-09 عوض کردنِ نقش/وضعیتِ عضوِ پمپ و تنظیمِ پمپ ردیفِ سابقه دارد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const h = require('./helpers');
const { query, many, newId, now } = require('../src/db');

test.before(async () => { await h.start(); });
test.after(async () => { await h.stop(); });

/** درخواستِ خام با Content-Type ِ JSON — تا پارسرِ JSON واقعاً سرِ کار بیاید. */
async function rawJson(p, text, headers = {}) {
  const res = await fetch(`${h.base()}${p}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: text,
  });
  let body = null;
  try { body = await res.json(); } catch { body = null; }
  return { status: res.status, body, headers: res.headers };
}

/* ── SEC-API-02 ─────────────────────────────────────────────────────── */

test('SEC-API-02: بی توکنِ دستگاه، بدنهٔ ‎/report/‎ خوانده نمی‌شود — ۴۰۱، نه ۴۰۰ِ JSON', async () => {
  for (const p of ['/api/pump/device/report/x', '/api/v1/pump/device/report/x']) {
    const r = await rawJson(p, '{bad json');
    assert.equal(r.status, 401, `${p}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'device_not_registered');
  }
});

test('SEC-API-02: بی توکنِ دستگاه، بدنهٔ ‎/state‎ خوانده نمی‌شود — نه parse، نه ۴۱۳', async () => {
  for (const p of ['/api/pump/device/state', '/api/v1/pump/device/state']) {
    assert.equal((await rawJson(p, '{bad json')).status, 401, p);
    //  ۵ مگابایت (بیش از سقفِ ۴mb): روی main ۴۱۳ ِ پارسر است، یعنی بدنه پیش از توکن خوانده می‌شود
    const big = JSON.stringify({ x: 'a'.repeat(5 * 1024 * 1024) });
    const r = await rawJson(p, big, { Authorization: 'Bearer pd_not_a_real_token' });
    assert.equal(r.status, 401, `${p}: ${JSON.stringify(r.body)}`);
  }
});

test('SEC-API-02: توکنِ ساختگی با پیشوندِ درست هم پیش از پارسر رد می‌شود', async () => {
  const r = await rawJson('/api/pump/device/report/x', '{bad', { Authorization: 'Bearer pd_forged' });
  assert.equal(r.status, 401);
});

test('SEC-API-02: دستگاهِ واقعی — گزارشِ بیش از ۲mb به مقصد می‌رسد (سقفِ ۳۴mb زنده است) و ‎/state‎ همان', async () => {
  const reports = require('../src/lib/station-reports');
  const u = await h.newUser('پمپِ گزارش', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ گزارش' }, { token: u.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const bound = await h.post('/api/pump/device/bind',
    { device: { uid: 'pc-sec-big', name: 'کامپیوتر', platform: 'windows' } }, { token: u.accessToken });
  assert.equal(bound.status, 201, JSON.stringify(bound.body));
  const dev = bound.body.deviceToken;
  try {
    const p = reports.request(made.body.station.id, 'tanks', {});
    p.catch(() => {});
    const { reqs } = (await h.get('/api/pump/device/rate', { token: dev })).body;
    //  ~۳ مگابایت PNG به base64 (۴ مگابایت متن) — بیش از سقفِ همگانی، کمتر از ۶mb ِ هر فایل
    const png = Buffer.concat([Buffer.from([0x89, 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a]), Buffer.alloc(3 * 1024 * 1024, 1)]);
    const up = await h.post(`/api/pump/device/report/${reqs[0].id}`,
      { ok: true, files: [{ name: 'big', mime: 'image/png', b64: png.toString('base64') }] }, { token: dev });
    assert.equal(up.status, 200, JSON.stringify(up.body));
    const out = await p;
    assert.ok(out.files[0].data.equals(png), 'بایت‌به‌بایت همان');
  } finally { reports._reset(); }
  //  بدنهٔ بدشکل با توکنِ درست ⇒ ۴۰۰ِ خودِ پارسر (یعنی پارسر پس از توکن سرِ کار است)
  const bad = await rawJson('/api/pump/device/state', '{bad', { Authorization: `Bearer ${dev}` });
  assert.equal(bad.status, 400);
  assert.equal(bad.body.error.code, 'bad_json');
});

/* ── SEC-API-05 (پمپ) ───────────────────────────────────────────────── */

test('SEC-API-05: هر پاسخِ درهای پمپ (JSON، ۴۰۱، ۴۰۴) CSP ِ «صفحه نیست» دارد', async () => {
  for (const p of ['/api/pump', '/api/v1/pump/device/rate', '/api/pump/public/NOPE/acct/x/chat', '/api/admin/pump/stations']) {
    const res = await fetch(`${h.base()}${p}`);
    const csp = res.headers.get('content-security-policy') || '';
    assert.match(csp, /default-src 'none'/, `${p} ⇒ ${res.status}`);
    assert.match(csp, /sandbox/, p);
  }
});

/* ── SEC-AUTH-09 ─────────────────────────────────────────────────── */

test('SEC-AUTH-09: نقش و وضعیتِ عضوِ پمپ و تنظیمِ پمپ ردیفِ سابقه دارند (کنشگر، زمانِ سرور، هدف)', async () => {
  const o = await h.newUser('صاحبِ پمپِ سابقه', 'pump');
  const made = await h.post('/api/pump', { name: 'پمپِ سابقه' }, { token: o.accessToken });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  const stationId = made.body.station.id;
  const s = await h.newUser('کارمندِ پمپ', 'pump');
  const mem = newId('mem');
  await query(`INSERT INTO station_members (id, station_id, user_id, role, status, created_at, updated_at)
               VALUES ($1,$2,$3,'staff','active',$4,$4)`, [mem, stationId, s.user.id, now()]);
  const before = now();
  const r = await h.patch(`/api/pump/members/${mem}`, { role: 'manager' }, { token: o.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  const rows = await many(`SELECT * FROM audit_logs WHERE action='pump.member_updated' AND target_id=$1`, [mem]);
  assert.equal(rows.length, 1);
  assert.equal(rows[0].user_id, o.user.id);
  assert.ok(Number(rows[0].created_at) >= before);
  const detail = typeof rows[0].detail === 'string' ? JSON.parse(rows[0].detail) : rows[0].detail;
  assert.deepEqual({ role: detail.role, status: detail.status, stationId: detail.stationId }, { role: 'manager', status: 'active', stationId });
  assert.equal((await h.patch('/api/pump', { name: 'پمپِ تازه' }, { token: o.accessToken })).status, 200);
  assert.equal((await many(`SELECT 1 FROM audit_logs WHERE action='pump.station_updated' AND target_id=$1`, [stationId])).length, 1);
});
