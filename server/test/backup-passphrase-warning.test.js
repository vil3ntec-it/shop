'use strict';
/**
 * F0-08 — `BACKUP_PASSPHRASE` پیش‌فرض خالی است و پشتیبانِ سرور رمز ندارد.
 *
 *   • پشتیبان‌گیری **هرگز** به‌خاطرِ نبودِ رمز نمی‌ایستد
 *   • بی رمز: هر اجرا یک خبرِ سامانه‌ای (`backup.unencrypted`) در «سابقه»ی
 *     پنل و یک خطِ هشدار در لاگِ سرور می‌گذارد
 *   • `GET /api/admin/backups` می‌گوید رمز نیست، تا پنل هشدارِ قرمز بدهد
 *   • با رمز: نه هشدار، نه خبر
 *   • متنِ `privacy.html` دیگر ادعای «پشتیبانِ رمزشده روی سرور» ندارد
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const h = require('./helpers');
const config = require('../src/config');
const { query, many, newId, now } = require('../src/db');

//  روی Postgresِ واقعی پشتیبان pg_dump می‌خواهد — آن را ‎backup.test.js‎ می‌سنجد
const onPglite = String(process.env.TEST_DATABASE_URL || '').startsWith('pglite');
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

const warnings = () => many(`SELECT actor_type, detail FROM audit_logs WHERE action='backup.unencrypted' ORDER BY created_at`);

test('بی رمز: پشتیبان ساخته می‌شود و هر اجرا خبرِ «رمز ندارد» می‌گذارد', { skip: !onPglite }, async () => {
  const saved = config.backup.passphrase;
  config.backup.passphrase = '';
  try {
    const before = (await warnings()).length;
    const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    assert.equal(r.status, 201, JSON.stringify(r.body));
    assert.equal(r.body.encrypted, false);
    assert.ok(fs.existsSync(r.body.file), 'پشتیبان نایستاد');
    await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    const after = await warnings();
    assert.equal(after.length - before, 2, 'هر اجرا یک خبر');
    assert.equal(after[after.length - 1].actor_type, 'system');
  } finally { config.backup.passphrase = saved; }
});

test('پنل: بی رمز، فهرستِ پشتیبان هشدار دارد', async () => {
  const saved = config.backup.passphrase;
  config.backup.passphrase = '';
  try {
    const r = await h.get('/api/admin/backups', { token: root });
    assert.equal(r.status, 200);
    assert.equal(r.body.encryption && r.body.encryption.configured, false);
    assert.match(String(r.body.encryption.warning || ''), /BACKUP_PASSPHRASE/);
  } finally { config.backup.passphrase = saved; }
});

test('با رمز: پشتیبان رمز دارد، هشدار و خبری نیست', { skip: !onPglite }, async () => {
  const saved = config.backup.passphrase;
  config.backup.passphrase = 'a-long-test-passphrase';
  try {
    const before = (await warnings()).length;
    const r = await h.post('/api/admin/backups', { kind: 'manual' }, { token: root });
    assert.equal(r.status, 201);
    assert.equal(r.body.encrypted, true);
    assert.equal((await warnings()).length, before);
    const list = await h.get('/api/admin/backups', { token: root });
    assert.equal(list.body.encryption.configured, true);
    assert.equal(list.body.encryption.warning, '');
  } finally { config.backup.passphrase = saved; }
});

test('پنل هشدارِ قرمز را نشان می‌دهد', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', 'public', 'admin', 'index.html'), 'utf8');
  const js = fs.readFileSync(path.join(__dirname, '..', 'public', 'admin', 'app.js'), 'utf8');
  assert.match(html, /id="backup-crypto-warn"/);
  assert.match(js, /encryption/);
});

test('privacy.html ادعای «پشتیبانِ رمزشده روی سرور» ندارد و حقیقت را می‌گوید', () => {
  const html = fs.readFileSync(path.join(__dirname, '..', '..', 'privacy.html'), 'utf8');
  assert.doesNotMatch(html, /پشتیبانِ دفتر رمزشده/);
  assert.match(html, /رمزگذاری نشده/);
});
