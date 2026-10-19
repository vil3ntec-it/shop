'use strict';
/**
 * سلامتِ سرور در پنلِ مدیر — v3 §۳۸.۸، OPS-01@38.
 *
 *   ۱) فقط مدیر؛ بی توکن ۴۰۱، کاربرِ دکان ۴۰۱/۴۰۳.
 *   ۲) API، دیتابیس، صف‌ها، همگام‌سازی، دیسک و پشتیبان — همه با وضعیتِ
 *      ok/warn/down/unknown و وضعیتِ کل = بدترینِ آن‌ها.
 *   ۳) صفِ گیرکرده (قدیمی‌تر از ۵ دقیقه) هشدار می‌شود و پنهان نمی‌ماند.
 *   ۴) ⛔ هیچ راز، نشانیِ دیتابیس، مسیرِ پوشه، متنِ پیام یا خطای کاربر در پاسخ نیست.
 *   ۵) ⛔ شکستِ یک بخش (مثلاً پوشهٔ پشتیبانِ خراب) فقط همان بخش را `unknown`
 *      می‌کند؛ پاسخ ۲۰۰ می‌ماند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const h = require('./helpers');
const config = require('../src/config');
const { query, newId, now } = require('../src/db');
const health = require('../src/lib/system-health');

let admin = '';
const STATUSES = ['ok', 'warn', 'down', 'unknown'];

test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'admin','مدیر',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Admin!12345'), now()]
  );
  admin = (await h.post('/api/admin/login', { username: 'admin', password: 'Admin!12345' })).body.token;
  assert.ok(admin, 'ورودِ مدیر');
});
test.after(async () => { await h.stop(); });

const get = (token) => h.get('/api/admin/system/health', { token });

test('worst: وضعیتِ کل بدترینِ بخش‌هاست', () => {
  assert.equal(health.worst([]), 'ok');
  assert.equal(health.worst(['ok', 'ok']), 'ok');
  assert.equal(health.worst(['ok', 'unknown']), 'unknown');
  assert.equal(health.worst(['unknown', 'warn', 'ok']), 'warn');
  assert.equal(health.worst(['warn', 'down', 'ok']), 'down');
});

test('فقط مدیر: بی توکن ۴۰۱، کاربرِ دکان راه ندارد', async () => {
  assert.equal((await get(null)).status, 401);
  const u = await h.newUser('دکان‌دارِ کنجکاو', 'shop');
  const r = await get(u.accessToken);
  assert.ok([401, 403].includes(r.status), String(r.status));
});

test('OPS-01: API، دیتابیس، صف‌ها، همگام‌سازی، دیسک و پشتیبان با وضعیت', async () => {
  const r = await get(admin);
  assert.equal(r.status, 200, JSON.stringify(r.body));
  assert.equal(r.headers.get('cache-control'), 'no-store');
  const b = r.body;
  assert.ok(STATUSES.includes(b.status));
  for (const part of [b.api, b.database, b.queues.email, b.queues.telegram, b.sync, b.storage, b.backups]) {
    assert.ok(STATUSES.includes(part.status), JSON.stringify(part));
  }
  assert.equal(b.api.status, 'ok');
  assert.match(b.api.version, /^\d+\.\d+\.\d+/);
  assert.equal(b.database.status === 'ok' || b.database.status === 'warn', true);
  assert.equal(typeof b.database.latencyMs, 'number');
  assert.equal(typeof b.queues.email.queued, 'number');
  assert.equal(typeof b.queues.telegram.pending, 'number');
  assert.equal(typeof b.sync.clientErrors24h, 'number');
  assert.equal(typeof b.sync.conflicts24h, 'number');
  assert.equal(typeof b.storage.freePct, 'number');
  assert.ok(b.storage.freeBytes <= b.storage.totalBytes);
  assert.equal(typeof b.backups.count, 'number');
  assert.equal(typeof b.backups.encryption, 'boolean');
  assert.equal(b.status, health.worst([b.api.status, b.database.status, b.queues.email.status,
    b.queues.telegram.status, b.sync.status, b.storage.status, b.backups.status]));
});

test('صفِ گیرکرده هشدار می‌شود؛ خطا و تعارضِ ۲۴ ساعت شمرده می‌شود', async () => {
  const t = now();
  await query(
    `INSERT INTO telegram_outbox (id, chat_id, body, next_at, created_at) VALUES ($1,'123456','متنِ خصوصیِ پیام',$2,$2)`,
    [newId('tg'), t - 10 * 60 * 1000]
  );
  await query(
    `INSERT INTO client_errors (id, app, message, stack, at, created_at) VALUES ($1,'shop','خطای خصوصیِ کاربر','x',$2,$2)`,
    [newId('err'), t - 1000]
  );
  const b = (await get(admin)).body;
  assert.equal(b.queues.telegram.pending, 1);
  assert.equal(b.queues.telegram.status, 'warn');
  assert.ok(b.queues.telegram.oldestAgeMs >= 10 * 60 * 1000 - 5000);
  assert.equal(b.sync.clientErrors24h, 1);
  assert.notEqual(b.status, 'ok', 'صفِ گیرکرده وضعیتِ کل را سالم نشان نمی‌دهد');
  const text = JSON.stringify(b);
  assert.ok(!text.includes('متنِ خصوصیِ پیام'), 'متنِ پیام');
  assert.ok(!text.includes('خطای خصوصیِ کاربر'), 'متنِ خطا');
  assert.ok(!text.includes('123456'), 'شناسهٔ گفت‌وگو');
});

test('⛔ هیچ راز، نشانی یا مسیری در پاسخ نیست', async () => {
  const prev = config.backup.passphrase;
  config.backup.passphrase = 'Backup-Passphrase-Never-Shown-42';
  try {
    const b = (await get(admin)).body;
    assert.equal(b.backups.encryption, true);
    const text = JSON.stringify(b);
    //  هر مقدارِ رشته‌ایِ config که نامش راز یا نشانی را می‌رساند
    const secrets = [];
    const walk = (o, key = '') => {
      if (!o || typeof o !== 'object') return;
      for (const [k, v] of Object.entries(o)) {
        if (typeof v === 'string' && v.length >= 6 && /secret|pass|token|key|url|dsn|smtp|dir|path/i.test(`${key}.${k}`)) secrets.push(v);
        else if (v && typeof v === 'object') walk(v, `${key}.${k}`);
      }
    };
    walk(config);
    assert.ok(secrets.includes('Backup-Passphrase-Never-Shown-42'));
    for (const s of secrets) assert.ok(!text.includes(s), `مقدارِ config در پاسخ آمد (${s.length} نویسه)`);
    assert.ok(!/postgres(ql)?:\/\//i.test(text));
    assert.ok(!text.includes(os.homedir()));
  } finally {
    config.backup.passphrase = prev;
  }
});

test('⛔ پوشهٔ پشتیبانِ خراب فقط همان بخش را «نامعلوم» می‌کند، نه کلِ پاسخ را', async () => {
  const prev = config.backup.dir;
  const file = path.join(fs.mkdtempSync(path.join(os.tmpdir(), 'health-')), 'not-a-dir');
  fs.writeFileSync(file, 'x');
  config.backup.dir = path.join(file, 'sub');
  try {
    const r = await get(admin);
    assert.equal(r.status, 200, JSON.stringify(r.body));
    assert.equal(r.body.storage.status, 'unknown');
    assert.equal(r.body.backups.status, 'unknown');
    assert.equal(r.body.database.status === 'ok' || r.body.database.status === 'warn', true);
    assert.ok(['unknown', 'warn', 'down'].includes(r.body.status));
  } finally {
    config.backup.dir = prev;
  }
});
