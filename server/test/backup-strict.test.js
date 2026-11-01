'use strict';
/**
 * SEC-07 — پشتیبانِ سرور اجباراً رمزدار (بخشِ ۱۰، «رمزگذاری»؛ دنبالهٔ F0-08).
 *
 * پشتِ پرچمِ خاموشِ `security / backup.require_encryption` در live_config:
 *
 *   • خاموش (پیش‌فرض): همان رفتارِ F0-08 — بی رمز پشتیبان ساخته می‌شود و فقط هشدار
 *   • روشن و بی BACKUP_PASSPHRASE: اجرا ناموفق (۴۰۹ `backup_passphrase_required`)،
 *     خبرِ `backup.refused_unencrypted` در سابقه، هیچ فایلِ متنِ ساده‌ای ساخته نمی‌شود
 *     و پشتیبانِ سالمِ قبلی دست نمی‌خورد
 *   • روشن و با رمز: فایل پیش از «موفق» یک بار باز می‌شود (رمزگشایی + gzip، شمارِ بایت‌ها
 *     برابرِ خروجیِ dump)؛ `verified: true`
 *   • فایلی که باز نمی‌شود: اجرا ناموفق (`backup_verify_failed`)، فایل نمی‌ماند، هرس نمی‌شود
 *     و خبرِ `backup.verify_failed`
 *
 * مسیرِ pg_dump را CIِ سرور روی Postgresِ واقعی می‌سنجد (backup.test.js)؛ این‌جا راه‌اندازِ PGlite.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const config = require('../src/config');
const db = require('../src/db');
const { query, many, newId, now } = db;

const onPglite = String(process.env.TEST_DATABASE_URL || '').startsWith('pglite');
const FLAG = { scope: 'security', key: 'backup.require_encryption' };

let root;
test.before(async () => {
  await h.start();
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'root','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  root = (await h.post('/api/admin/login', { username: 'root', password: 'Root!12345' })).body.token;
});
test.after(async () => { await h.stop(); });

const setFlag = (value) => h.put('/api/admin/pump/live-config', { ...FLAG, value }, { token: root });
const clearFlag = () => query('DELETE FROM live_config WHERE scope=$1 AND key=$2', [FLAG.scope, FLAG.key]);
const audits = (action) => many('SELECT actor_type, detail FROM audit_logs WHERE action=$1 ORDER BY created_at', [action]);
const manualDir = () => path.join(config.backup.dir, 'manual');
const filesNow = () => (fs.existsSync(manualDir()) ? fs.readdirSync(manualDir()).sort() : []);

async function withPass(pass, fn) {
  const saved = config.backup.passphrase;
  config.backup.passphrase = pass;
  try { return await fn(); } finally { config.backup.passphrase = saved; }
}

test('پرچم خاموش (پیش‌فرض): بی رمز همان رفتارِ F0-08 — ساخته می‌شود، رمز ندارد', { skip: !onPglite }, async () => {
  await clearFlag();
  await withPass('', async () => {
    const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal(r.body.encrypted, false);
    assert.ok(fs.existsSync(r.body.file));
  });
});

test('پرچم روشن و بی رمز: رد با ۴۰۹، هیچ فایلِ تازه، پشتیبانِ قبلی سالم، خبرِ سابقه', { skip: !onPglite }, async () => {
  await clearFlag();
  //  یک پشتیبانِ سالمِ قبلی (رمزدار) که نباید دست بخورد
  const prev = await withPass('a-long-test-passphrase', () => h.post('/api/admin/backups', { kind: 'manual' }, { token: root }));
  assert.equal(prev.status, 201, JSON.stringify(prev.body));
  const prevBytes = fs.readFileSync(prev.body.file);

  const set = await setFlag(true);
  assert.equal(set.status, 200, JSON.stringify(set.body));
  const before = filesNow();
  const refusedBefore = (await audits('backup.refused_unencrypted')).length;
  await withPass('', async () => {
    const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    assert.equal(r.status, 409, JSON.stringify(r.body));
    assert.equal(r.body.error && r.body.error.code, 'backup_passphrase_required');
    //  خودِ اجرای کتابخانه (همان که زمان‌بندی و scripts/backup.js می‌زنند) هم می‌ایستد
    await assert.rejects(require('../src/lib/backup').run({ kind: 'daily' }), (e) => e.code === 'backup_passphrase_required');
  });
  assert.deepEqual(filesNow(), before, 'هیچ فایلِ تازه‌ای (نه متنِ ساده، نه .part) ساخته نشد');
  assert.ok(fs.readFileSync(prev.body.file).equals(prevBytes), 'پشتیبانِ قبلی دست نخورد');
  const refused = await audits('backup.refused_unencrypted');
  assert.equal(refused.length - refusedBefore, 2, 'هر اجرای ردشده یک خبر');
  assert.equal(refused[refused.length - 1].actor_type, 'system');
  const daily = path.join(config.backup.dir, 'daily');
  assert.deepEqual(fs.existsSync(daily) ? fs.readdirSync(daily) : [], [], 'زمان‌بندی هم چیزی ننوشت');
  await clearFlag();
});

test('پرچم روشن و با رمز: پیش از «موفق» باز و سنجیده می‌شود (verified)', { skip: !onPglite }, async () => {
  await setFlag(true);
  await withPass('a-long-test-passphrase', async () => {
    const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal(r.body.encrypted, true);
    assert.equal(r.body.verified, true);
    const meta = JSON.parse(fs.readFileSync(`${r.body.file}.meta.json`, 'utf8'));
    assert.equal(meta.verified, true);
    assert.ok(meta.rawBytes > 100, 'شمارِ بایتِ بازشده ثبت شد');
    //  همان فایل با decode ِ معمولی هم باز می‌شود (بازیابی همان راه است)
    const plain = await require('../src/lib/backup').decode(r.body.file);
    assert.equal(plain.length, meta.rawBytes);
  });
  await clearFlag();
});

test('پرچم روشن: فایلی که باز نمی‌شود «موفق» نیست — پاک، بی هرس، با خبر', { skip: !onPglite }, async () => {
  await setFlag('on');
  const before = filesNow();
  const failedBefore = (await audits('backup.verify_failed')).length;
  const realDump = db.dumpPglite;
  //  خروجیِ خرابِ dump: بیش از ۱۰۰ بایت ولی gzip نیست ⇒ رمز می‌شود، ولی باز نمی‌شود
  db.dumpPglite = async () => Buffer.alloc(4096, 0x41);
  try {
    await withPass('a-long-test-passphrase', async () => {
      const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
      assert.equal(r.status, 500, JSON.stringify(r.body));
      await assert.rejects(require('../src/lib/backup').run({ kind: 'manual' }), (e) => e.code === 'backup_verify_failed');
    });
  } finally { db.dumpPglite = realDump; }
  assert.deepEqual(filesNow(), before, 'فایلِ ناسالم نماند و هیچ پشتیبانِ قبلی هرس نشد');
  const failed = await audits('backup.verify_failed');
  assert.equal(failed.length - failedBefore, 2);
  await clearFlag();
});

test('پنل: GET /backups می‌گوید رمز اجباری است یا نه (خودِ رمز هرگز)', async () => {
  await clearFlag();
  let r = await h.get('/api/admin/backups', { token: root });
  assert.equal(r.status, 200);
  assert.equal(r.body.encryption.required, false);
  await setFlag(1);
  r = await h.get('/api/admin/backups', { token: root });
  assert.equal(r.body.encryption.required, true);
  await withPass('', async () => {
    const w = await h.get('/api/admin/backups', { token: root });
    assert.match(w.body.encryption.warning, /اجباری است.*ساخته نشد/, 'بی رمز و اجباری ⇒ هشدارِ «ساخته نمی‌شود»');
  });
  assert.doesNotMatch(JSON.stringify(r.body), /a-long-test-passphrase/);
  await setFlag(false);
  r = await h.get('/api/admin/backups', { token: root });
  assert.equal(r.body.encryption.required, false, 'false یعنی خاموش');
  await clearFlag();
});

test('پرچم را فقط مدیرِ ارشد روشن می‌کند', async () => {
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'ops','کارمند',$2,'admin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Ops!123456'), now()]
  );
  const ops = (await h.post('/api/admin/login', { username: 'ops', password: 'Ops!123456' })).body.token;
  const r = await h.put('/api/admin/pump/live-config', { ...FLAG, value: true }, { token: ops });
  assert.equal(r.status, 403);
});

test('verifyFile: قالبِ مسیرِ pg_dump (gzip ⇒ AES-GCM جریانی + برچسبِ انتها) باز می‌شود؛ یک بایتِ دست‌خورده نه', async () => {
  const zlib = require('node:zlib');
  const { createCipheriv, randomBytes, scryptSync } = require('node:crypto');
  const backup = require('../src/lib/backup');
  const os = require('node:os');
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'sec07-'));
  const sql = Buffer.from('-- PostgreSQL database dump\n' + 'INSERT INTO t VALUES (1);\n'.repeat(400));
  const pass = 'a-long-test-passphrase';
  const salt = randomBytes(16);
  const iv = randomBytes(12);
  const key = scryptSync(pass, salt, 32, { N: 16384, r: 8, p: 1, maxmem: 64 * 1024 * 1024 });
  const c = createCipheriv('aes-256-gcm', key, iv);
  const body = Buffer.concat([c.update(zlib.gzipSync(sql)), c.final()]);
  const file = path.join(dir, 'shop-x.sql.gz.enc');
  fs.writeFileSync(file, Buffer.concat([Buffer.from('SHOPBK1'), salt, iv, body, c.getAuthTag()]));
  await withPass(pass, async () => {
    assert.equal(await backup.verifyFile(file, sql.length), sql.length);
    await assert.rejects(backup.verifyFile(file, sql.length + 1), /یکی نیست/, 'شمارِ بایتِ نابرابر رد می‌شود');
    const bad = fs.readFileSync(file);
    bad[60] ^= 0x01;
    fs.writeFileSync(file, bad);
    await assert.rejects(backup.verifyFile(file, sql.length), 'دست‌کاری با برچسبِ GCM لو می‌رود');
  });
  await withPass('another-passphrase', async () => {
    const plain = path.join(dir, 'shop-y.sql.gz');
    fs.writeFileSync(plain, zlib.gzipSync(sql));
    assert.equal(await backup.verifyFile(plain, sql.length), sql.length, 'بی رمز (پرچم خاموش) هم همان سنجش');
  });
  fs.rmSync(dir, { recursive: true, force: true });
});
