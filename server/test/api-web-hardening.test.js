'use strict';
/**
 * امنیتِ API و وب (برنامهٔ امنیت §۲.۳، §۱۰، §۱۱ — شاخهٔ `claude/sec-api-web`).
 * شرحِ هر بند و جدولِ کامل: `docs/security/api-web-fa.md`.
 *
 *   SEC-API-02  بدنهٔ بزرگِ ‎/pump/device/state|report‎ فقط پس از توکنِ دستگاه خوانده می‌شود
 *   SEC-API-03  SSRF: نشانیِ سلامتِ «برنامه‌ها» فقط مدیرِ ارشد و فقط http/https
 *   SEC-API-04  `devCode` فقط با خواستِ صریح، نه با نبودِ `NODE_ENV`
 *   SEC-API-05  CSP ِ پیش‌فرضِ `/api` و CSP ِ پورتال
 *   SEC-API-06  همگام‌سازیِ قدیم: ‎constructor‎/‎__proto__‎ نامِ جدول نمی‌شوند
 *   SEC-API-07  هیچ SQL ای مقدارِ درخواست را مستقیم در متن نمی‌گذارد (نگهبانِ ایستا)
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const h = require('./helpers');
const { query, newId, now } = require('../src/db');

let root = '';
let clerk = '';

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'sec-root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'sec-root', password: 'Root!12345' })).body.token;
  const made = await h.post('/api/admin/admins', { username: 'sec-clerk', password: 'Clerk!12345', role: 'admin' }, { token: root });
  assert.equal(made.status, 201, JSON.stringify(made.body));
  clerk = (await h.post('/api/admin/login', { username: 'sec-clerk', password: 'Clerk!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

/** درخواستِ خام با Content-Type ِ JSON — تا پارسرِ JSON واقعاً سرِ کار بیاید. */
async function rawJson(p, text, headers = {}) {
  const res = await fetch(`${h.base()}${p}`, {
    method: 'POST', headers: { 'Content-Type': 'application/json', ...headers }, body: text,
  });
  let body = null;
  try { body = await res.json(); } catch { body = null; }
  return { status: res.status, body };
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
    //  ۵ مگابایت (بیش از سقفِ ۴mb): پیش از اصلاح ۴۱۳ِ پارسر بود، یعنی بدنه پیش از توکن خوانده می‌شد
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

/* ── SEC-API-03 ─────────────────────────────────────────────────────── */

test('SEC-API-03: مدیرِ معمولی برنامه با نشانیِ سلامت نمی‌سازد و ویرایش نمی‌کند', async () => {
  const made = await h.post('/api/admin/apps',
    { slug: 'ssrf-probe', title: 'پروب', healthUrl: 'http://127.0.0.1:5432/' }, { token: clerk });
  assert.equal(made.status, 403, JSON.stringify(made.body));
  const ok = await h.post('/api/admin/apps', { slug: 'ssrf-ok', title: 'سالم', url: 'https://example.com' }, { token: root });
  assert.equal(ok.status, 201, JSON.stringify(ok.body));
  const edit = await h.put(`/api/admin/apps/${ok.body.app.id}`, { healthUrl: 'http://10.0.0.1/' }, { token: clerk });
  assert.equal(edit.status, 403);
  const row = (await query('SELECT health_url FROM managed_apps WHERE id=$1', [ok.body.app.id])).rows[0];
  assert.equal(row.health_url, '', 'نشانی عوض نشد');
  //  خواندن و سنجیدنِ سلامت برای مدیرِ معمولی همان است که بود
  assert.equal((await h.get('/api/admin/apps', { token: clerk })).status, 200);
});

test('SEC-API-03: فقط http/https، بی نام‌کاربری و رمز در نشانی', async () => {
  for (const bad of ['file:///etc/passwd', 'data:text/plain,x', 'javascript:alert(1)', 'ftp://x.y/', 'http://u:p@example.com/', 'not a url']) {
    const r = await h.post('/api/admin/apps', { slug: `bad-${Math.random().toString(36).slice(2, 8)}`, healthUrl: bad }, { token: root });
    assert.equal(r.status, 400, `${bad}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.error.code, 'bad_url');
    const u = await h.post('/api/admin/apps', { slug: `badu-${Math.random().toString(36).slice(2, 8)}`, url: bad }, { token: root });
    assert.equal(u.status, 400, `url ${bad}`);
  }
  const good = await h.post('/api/admin/apps',
    { slug: 'good-health', url: 'https://example.com', healthUrl: 'https://example.com/health' }, { token: root });
  assert.equal(good.status, 201, JSON.stringify(good.body));
  assert.equal(good.body.app.healthUrl, 'https://example.com/health');
});

test('SEC-API-03: ردیفِ کهنه با طرحِ ناروا هرگز fetch نمی‌شود', async (t) => {
  await query(`INSERT INTO managed_apps (id, slug, title, kind, url, health_url, icon, color, note, status, created_at, updated_at)
               VALUES ($1,'legacy-bad','کهنه','app','', 'file:///etc/passwd','','','','active',$2,$2)`, [newId('app'), now()]);
  //  فقط همین ردیف: بقیهٔ ردیف‌ها (example.com) به اینترنت نروند
  await query(`UPDATE managed_apps SET health_url='' WHERE slug <> 'legacy-bad'`);
  const calls = [];
  t.mock.method(globalThis, 'fetch', async (u) => { calls.push(String(u)); throw new Error('no network in test'); });
  const out = await require('../src/lib/managed-apps').checkHealth({ timeoutMs: 100 });
  const row = out.find((x) => x.slug === 'legacy-bad');
  assert.equal(row.ok, false);
  assert.match(row.error, /http یا https/);
  assert.deepEqual(calls, [], 'هیچ fetch ای نرفت');
});

/* ── SEC-API-04 ─────────────────────────────────────────────────────── */

test('SEC-API-04: بی خواستِ صریح، کد در پاسخ نمی‌آید — حتی وقتی NODE_ENV ≠ production', async () => {
  const config = require('../src/config');
  const keep = { env: config.env, echo: config.otp.devEcho };
  try {
    config.env = 'development';            // همان «NODE_ENV ِ نیامده»
    config.otp.devEcho = false;
    const r = await h.post('/api/auth/register/start', { name: 'آزمون', email: 'echo-off@test.local', password: 'Passw0rd!test' });
    assert.ok(r.status < 300, JSON.stringify(r.body));
    assert.equal(r.body.devCode, undefined, 'کد در پاسخ آمد');
    config.otp.devEcho = true;
    const r2 = await h.post('/api/auth/register/start', { name: 'آزمون', email: 'echo-on@test.local', password: 'Passw0rd!test' });
    assert.match(String(r2.body.devCode || ''), /^\d{4,8}$/, 'با خواستِ صریح (آزمون/توسعه) همان کد می‌آید');
    config.env = 'production';
    const r3 = await h.post('/api/auth/register/start', { name: 'آزمون', email: 'echo-prod@test.local', password: 'Passw0rd!test' });
    assert.equal(r3.body.devCode, undefined, 'production هرگز، حتی با OTP_DEV_ECHO');
  } finally {
    config.env = keep.env;
    config.otp.devEcho = keep.echo;
  }
});

test('SEC-API-04: `devEcho` از محیط — نبودِ NODE_ENV یعنی خاموش', () => {
  const read = (env) => {
    const clean = { PATH: process.env.PATH, ENV_FILE: path.join(__dirname, 'no-such.env') };
    const r = spawnSync(process.execPath, ['-e', "process.stdout.write(String(require('./src/config').otp.devEcho))"],
      { cwd: path.join(__dirname, '..'), env: { ...clean, ...env }, encoding: 'utf8' });
    assert.equal(r.status, 0, r.stderr);
    return r.stdout;
  };
  assert.equal(read({}), 'false', 'NODE_ENV نیامده');
  assert.equal(read({ NODE_ENV: 'development' }), 'false');
  assert.equal(read({ NODE_ENV: 'production', OTP_DEV_ECHO: 'true' }), 'true', 'پرچم خوانده می‌شود؛ otp.js خودش production را می‌بندد');
  assert.equal(read({ NODE_ENV: 'development', OTP_DEV_ECHO: '1' }), 'true');
  assert.equal(read({ NODE_ENV: 'test' }), 'true');
});

/* ── SEC-API-05 ─────────────────────────────────────────────────────── */

const directive = (csp, name) => {
  const d = String(csp || '').split(';').map((s) => s.trim()).find((s) => s.split(/\s+/)[0] === name);
  return d ? d.split(/\s+/).slice(1) : null;
};

test('SEC-API-05: هر پاسخِ `/api` (JSON، ۴۰۴، خطا) CSP ِ «صفحه نیست» دارد', async () => {
  for (const p of ['/api/health', '/api/v1/config', '/api/no-such-route', '/api/me']) {
    const r = await h.get(p);
    const csp = r.headers.get('content-security-policy');
    assert.deepEqual(directive(csp, 'default-src'), ["'none'"], `${p}: ${csp}`);
    assert.deepEqual(directive(csp, 'frame-ancestors'), ["'none'"], p);
    assert.ok(directive(csp, 'sandbox'), `${p}: sandbox`);
    assert.equal(r.headers.get('x-content-type-options'), 'nosniff');
  }
});

test('SEC-API-05: پورتال CSP دارد — اسکریپت فقط از خودش، بی قاب', async () => {
  const res = await fetch(`${h.base()}/portal/`);
  assert.equal(res.status, 200);
  const csp = res.headers.get('content-security-policy');
  const script = directive(csp, 'script-src');
  assert.deepEqual(script, ["'self'"], csp);
  assert.deepEqual(directive(csp, 'frame-ancestors'), ["'none'"]);
  assert.deepEqual(directive(csp, 'connect-src'), ["'self'"]);
  assert.ok(res.headers.get('permissions-policy'));
  //  پورتال هیچ اسکریپتِ درون‌خطی ندارد که این CSP بشکندش
  const html = await res.text();
  assert.doesNotMatch(html.replace(/<script src="[^"]+"><\/script>/g, ''), /<script/i);
  assert.doesNotMatch(html, /\son[a-z]+=/i, 'دستگیرهٔ درون‌خطی در پورتال');
  //  پنل هم همان که بود
  const admin = await fetch(`${h.base()}/admin/`);
  assert.deepEqual(directive(admin.headers.get('content-security-policy'), 'script-src'), ["'self'"]);
});

/* ── SEC-API-06 ─────────────────────────────────────────────────────── */

test('SEC-API-06: همگام‌سازیِ قدیم — نامِ مجموعهٔ ‎constructor‎/‎__proto__‎ رد می‌شود، نه ۵۰۰', async () => {
  const u = await h.newUser('صاحبِ همگام', 'shop');
  await h.api('POST', '/api/shop', { token: u.accessToken, body: { name: 'دکانِ همگام' } });
  for (const collection of ['constructor', '__proto__', 'toString', 'hasOwnProperty']) {
    const r = await h.api('POST', '/api/v1/shop/sync/push', {
      token: u.accessToken,
      body: { deviceId: 'sec-dev', changes: [{ collection, id: 'x1', updatedAt: Date.now(), data: { name: 'n' } }] },
    });
    assert.equal(r.status, 200, `${collection}: ${JSON.stringify(r.body)}`);
    assert.equal(r.body.skipped, 1, collection);
    assert.equal(r.body.applied, 0);
  }
  const ok = await h.api('POST', '/api/v1/shop/sync/push', {
    token: u.accessToken,
    body: { deviceId: 'sec-dev', changes: [{ collection: 'products', id: 'p1', updatedAt: Date.now(), data: { name: 'چای' } }] },
  });
  assert.equal(ok.body.applied, 1, 'مجموعهٔ درست همان که بود');
});

/* ── SEC-API-07 ─────────────────────────────────────────────────────── */

/** همهٔ template literal های یک فایل با `${…}` هایشان (تودرتو هم). */
function templates(src) {
  const out = [];
  const n = src.length;
  function tpl(start) {
    let p = start + 1; let text = ''; const exprs = [];
    while (p < n) {
      const c = src[p];
      if (c === '\\') { text += src.slice(p, p + 2); p += 2; continue; }
      if (c === '`') return { end: p, text, exprs };
      if (c === '$' && src[p + 1] === '{') {
        let depth = 1; let q = p + 2; const s = q;
        while (q < n && depth > 0) {
          const d = src[q];
          if (d === '`') { const t = tpl(q); out.push(t); q = t.end + 1; continue; }
          if (d === "'" || d === '"') { const qt = d; q++; while (q < n && src[q] !== qt) { if (src[q] === '\\') q++; q++; } q++; continue; }
          if (d === '{') depth++; else if (d === '}') depth--;
          q++;
        }
        exprs.push(src.slice(s, q - 1)); text += '\u0000'; p = q; continue;
      }
      text += c; p++;
    }
    return { end: n, text, exprs };
  }
  let i = 0;
  while (i < n) {
    const c = src[i];
    if (c === '/' && src[i + 1] === '/') { while (i < n && src[i] !== '\n') i++; continue; }
    if (c === '/' && src[i + 1] === '*') { const e = src.indexOf('*/', i + 2); i = e < 0 ? n : e + 2; continue; }
    if (c === "'" || c === '"') { const q = c; i++; while (i < n && src[i] !== q && src[i] !== '\n') { if (src[i] === '\\') i++; i++; } i++; continue; }
    if (c === '`') { const t = tpl(i); out.push(t); i = t.end + 1; continue; }
    i++;
  }
  return out;
}

test('SEC-API-07: هیچ SQL ای `req.*`/بدنه/پرسش را مستقیم در متن نمی‌گذارد', () => {
  const SRC = path.join(__dirname, '..', 'src');
  const files = [];
  const walk = (d) => { for (const f of fs.readdirSync(d, { withFileTypes: true })) { const p = path.join(d, f.name); if (f.isDirectory()) walk(p); else if (p.endsWith('.js')) files.push(p); } };
  walk(SRC);
  const SQL = /\b(SELECT|INSERT\s+INTO|UPDATE\s+\w+\s+SET|DELETE\s+FROM|WHERE|ORDER\s+BY|LIMIT)\b/;
  //  `params` نامِ آرایهٔ پارامترهای SQL هم هست (‎$${params.length}‎) — فقط شکلِ درخواست
  const TAINT = /\breq\b|\bbody\s*(\?\.|\.|\[)|\b(query|params)\s*(\?\.|\.)(?!length\b)\w/;
  const bad = [];
  let sqlCount = 0;
  for (const f of files) {
    for (const t of templates(fs.readFileSync(f, 'utf8'))) {
      if (!SQL.test(t.text)) continue;
      sqlCount++;
      for (const e of t.exprs) if (TAINT.test(e)) bad.push(`${path.relative(SRC, f)}: \${${e.trim().slice(0, 80)}}`);
    }
  }
  assert.ok(sqlCount > 100, `اسکنر SQL ها را پیدا نکرد (${sqlCount})`);
  assert.deepEqual(bad, [], 'مقدارِ درخواست مستقیم در SQL — پارامتر ($n) بگذارید');
});

/* ── SEC-API-12 ─────────────────────────────────────────────────────── */

test('SEC-API-12: هیچ روتری یک نشانی و روش را دو بار ثبت نمی‌کند (دستگیرهٔ دوم هرگز نمی‌رسد)', () => {
  //  ⚠️ دستگیرهٔ سایه‌خورده یعنی اصلاحِ امنیتیِ آن‌جا هرگز اجرا نمی‌شود. «دروازه سپس سوار شدن»
  //  (‎router.post('/backups', gate)‎ + ‎router.use('/backups', …)‎) و ‎app.get('/')‎ ِ دومِ app.js
  //  (اولی بیرون از دامنهٔ ویترین `next()` می‌کند) تکرار نیستند و این نگهبان آن‌ها را نمی‌شمارد.
  const R = path.join(__dirname, '..', 'src', 'routes');
  const dup = [];
  for (const f of fs.readdirSync(R).filter((x) => x.endsWith('.js'))) {
    const src = fs.readFileSync(path.join(R, f), 'utf8');
    const seen = new Map();
    for (const m of src.matchAll(/(?:^|\n)\s*(?:router|r)\.(get|post|put|patch|delete|all)\(\s*('[^']+'|`[^`]+`)/g)) {
      const k = `${m[1].toUpperCase()} ${m[2]}`;
      const line = src.slice(0, m.index).split('\n').length + 1;
      if (seen.has(k)) dup.push(`${f}: ${k} (${seen.get(k)} و ${line})`); else seen.set(k, line);
    }
  }
  assert.deepEqual(dup, []);
});

test('SEC-API-12: تپشِ ‎/me/heartbeat‎ همان پاسخِ پورتال است', async () => {
  const u = await h.newUser('تپش', 'shop');
  const r = await h.get('/api/me/heartbeat', { token: u.accessToken });
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.body.app, 'shop');
  assert.ok('unreadNotices' in r.body && r.body.serverTime > 0);
  assert.equal(r.body.server_time, undefined, 'شکلِ دستگیرهٔ مرده نیست');
});
