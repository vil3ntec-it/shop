'use strict';
/**
 * SEC-ART-05 / F0-28: کلیدِ خصوصیِ امضای مجوز بیرون از دیتابیس، بی برگشتِ ناامن.
 *
 *  • حالتِ env: کلید فقط از فایلِ ‎0600‎ یا محیط؛ نبودنش خطاست — نه خواندن از app_config، نه ساختنِ کلیدِ تازه.
 *  • پیش‌فرض (legacy): رفتارِ سرورهای نصب‌شده همان می‌ماند، ولی «کلیدی بوده و خصوصی‌اش گم شده» دیگر بی‌صدا
 *    کلیدِ تازه نمی‌سازد (که مجوزِ همهٔ نصب‌ها را رد می‌کرد).
 *  • `LICENSE_REVOKED_KIDS`: با کلیدِ باطل‌شده هرگز امضا نمی‌شود.
 *  • ابزارِ ‎scripts/license-key.js‎ کلید را جابه‌جا می‌کند و هرگز مقدارش را چاپ نمی‌کند.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const h = require('./helpers');
const config = require('../src/config');
const license = require('../src/lib/license');
const { one, query } = require('../src/db');
const script = require('../scripts/license-key');

const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'license-key-'));
const newPem = () => crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1', privateKeyEncoding: { type: 'pkcs8', format: 'pem' }, publicKeyEncoding: { type: 'spki', format: 'pem' },
}).privateKey;
const kidOfPem = (pem) => license.kidOf(license.spkiOf(crypto.createPrivateKey(pem)));
const saved = { ...config.license };

function setMode({ mode = 'legacy', file = '', revokedKids = [], envKey } = {}) {
  config.license.mode = mode;
  config.license.file = file;
  config.license.revokedKids = revokedKids;
  if (envKey === undefined) delete process.env.LICENSE_PRIVATE_KEY; else process.env.LICENSE_PRIVATE_KEY = envKey;
  license._reset();
}
async function dbRows() {
  const p = await one(`SELECT value FROM app_config WHERE key='license_private_key'`);
  const q = await one(`SELECT value FROM app_config WHERE key='license_public_key'`);
  return { priv: !!(p && p.value), pub: !!(q && q.value) };
}
async function clearDb() { await query(`DELETE FROM app_config WHERE key IN ('license_private_key','license_public_key')`); }
function keyFile(name, pem, mode = 0o600) {
  const f = path.join(dir, name);
  fs.writeFileSync(f, pem, { mode });
  fs.chmodSync(f, mode);
  return f;
}
const quiet = () => {};

test.before(async () => { await h.start(); });
test.after(async () => {
  Object.assign(config.license, saved);
  delete process.env.LICENSE_PRIVATE_KEY;
  license._reset();
  fs.rmSync(dir, { recursive: true, force: true });
  await h.stop();
});
test.beforeEach(async () => { await clearDb(); setMode(); });

test('env: بی کلید ⇒ خطا؛ هیچ کلیدی ساخته یا در app_config نوشته نمی‌شود', async () => {
  setMode({ mode: 'env' });
  await assert.rejects(license.publicKey(), { code: 'license_key_missing' });
  await assert.rejects(license.issue({ deviceUid: 'd', accountId: 'a' }), { code: 'license_key_missing' });
  assert.deepEqual(await dbRows(), { priv: false, pub: false });
});

test('env: کلیدِ ذخیره در app_config هرگز جایگزینِ کلیدِ نبوده نمی‌شود', async () => {
  await query(`INSERT INTO app_config (key,value,updated_at) VALUES ('license_private_key',$1,0)`, [newPem()]);
  setMode({ mode: 'env' });
  await assert.rejects(license.publicKey(), { code: 'license_key_missing' });
});

test('env: کلید از فایلِ 0600؛ فایلِ خواندنی برای دیگران پذیرفته نیست؛ دیتابیس دست نمی‌خورد', async (t) => {
  const pem = newPem();
  const good = keyFile('good.pem', pem, 0o600);
  setMode({ mode: 'env', file: good });
  assert.equal(await license.keyId(), kidOfPem(pem));
  const tok = await license.issue({ deviceUid: 'dev-1', accountId: 'acc-1' });
  const [hh, pp, ss] = tok.token.split('.');
  const ok = crypto.verify('sha256', Buffer.from(`${hh}.${pp}`), {
    key: crypto.createPublicKey(pem), dsaEncoding: 'ieee-p1363',
  }, Buffer.from(ss, 'base64url'));
  assert.ok(ok, 'مجوز با همان کلیدِ فایل امضا شد');
  assert.deepEqual(await dbRows(), { priv: false, pub: false });
  if (process.platform === 'win32') { t.skip('mode روی ویندوز معنا ندارد'); return; }
  const loose = keyFile('loose.pem', pem, 0o644);
  setMode({ mode: 'env', file: loose });
  await assert.rejects(license.publicKey(), { code: 'license_key_file_mode' });
  setMode({ mode: 'env', file: path.join(dir, 'nope.pem') });
  await assert.rejects(license.publicKey(), { code: 'license_key_file_missing' });
});

test('env: PEMِ یک‌خطی با \\n در محیط (همان که OWNER-ACTIONS می‌گوید) خوانده می‌شود', async () => {
  const pem = newPem();
  setMode({ mode: 'env', envKey: pem.trim().split('\n').join('\\n') });
  assert.equal(await license.keyId(), kidOfPem(pem));
});

test('kidِ باطل‌شده: نه از محیط، نه از app_config امضا نمی‌شود', async () => {
  const pem = newPem();
  setMode({ mode: 'env', envKey: pem, revokedKids: [kidOfPem(pem)] });
  await assert.rejects(license.issue({ deviceUid: 'd', accountId: 'a' }), { code: 'license_key_revoked' });
  await query(`INSERT INTO app_config (key,value,updated_at) VALUES ('license_private_key',$1,0)`, [pem]);
  setMode({ mode: 'legacy', revokedKids: [kidOfPem(pem)] });
  await assert.rejects(license.preflight(), { code: 'license_key_revoked' });
});

test('legacy: سرورِ تازه یک بار کلید می‌سازد (رفتارِ امروز)؛ کلیدی که بوده و گم شده ⇒ خطا، نه کلیدِ تازه', async () => {
  const first = await license.keyId();
  license._reset();
  assert.equal(await license.keyId(), first, 'همان کلید از app_config');
  assert.deepEqual(await dbRows(), { priv: true, pub: true });
  await query(`DELETE FROM app_config WHERE key='license_private_key'`);
  license._reset();
  await assert.rejects(license.publicKey(), { code: 'license_key_lost' });
  assert.deepEqual(await dbRows(), { priv: false, pub: true }, 'کلیدِ تازه‌ای نوشته نشد');
});

test('config.validate: حالتِ env بی کلید یعنی سرور بالا نمی‌آید', () => {
  const env = { ...process.env };
  try {
    process.env.LICENSE_KEY_MODE = 'env';
    config.license.mode = 'env'; config.license.file = ''; delete process.env.LICENSE_PRIVATE_KEY;
    assert.ok(config.validate().some((p) => p.includes('LICENSE_KEY_MODE=env')));
    process.env.LICENSE_KEY_MODE = 'envv';
    assert.ok(config.validate().some((p) => p.includes('LICENSE_KEY_MODE')));
  } finally {
    for (const k of ['LICENSE_KEY_MODE', 'LICENSE_PRIVATE_KEY']) { if (env[k] === undefined) delete process.env[k]; else process.env[k] = env[k]; }
  }
});

test('ابزار: export ⇒ فایلِ 0600 با همان kid؛ purge-db فقط با کلیدِ یکسان؛ هیچ خروجی‌ای کلید را ندارد', async () => {
  //  سرورِ نصب‌شده: کلید در app_config
  const kid = await license.keyId();
  const dbPem = (await one(`SELECT value FROM app_config WHERE key='license_private_key'`)).value;
  const lines = [];
  const out = (o) => lines.push(JSON.stringify(o));
  const file = path.join(dir, 'exported.pem');
  assert.equal(await script.main(['export', file], out), 0);
  if (process.platform !== 'win32') assert.equal(fs.statSync(file).mode & 0o777, 0o600);
  assert.equal(kidOfPem(fs.readFileSync(file, 'utf8')), kid);
  await assert.rejects(script.main(['export', file], quiet), /EEXIST/, 'روی فایلِ موجود نمی‌نویسد');
  //  پاک کردنِ نسخهٔ دیتابیس بی کلیدِ بیرونی ⇒ نه
  await assert.rejects(script.main(['purge-db'], quiet), /بیرون از دیتابیس/);
  //  کلیدِ دیگری در فایل ⇒ نه
  setMode({ mode: 'env', file: keyFile('other.pem', newPem()) });
  await assert.rejects(script.main(['purge-db'], quiet), /یکی نیست/);
  assert.equal((await dbRows()).priv, true);
  //  همان کلید ⇒ پاک می‌شود، عمومی می‌ماند، و سرور با فایل همان kid را دارد
  setMode({ mode: 'env', file });
  assert.equal(await script.main(['purge-db'], out), 0);
  assert.deepEqual(await dbRows(), { priv: false, pub: true });
  license._reset();
  assert.equal(await license.keyId(), kid);
  assert.equal(await script.main(['status'], out), 0);
  assert.equal(await script.main(['generate', path.join(dir, 'next.pem')], out), 0);
  const body = dbPem.split('\n').filter((l) => l && !l.startsWith('-----')).join('');
  for (const l of lines) {
    assert.ok(!l.includes('PRIVATE KEY') && !l.includes(body.slice(0, 24)), 'خروجیِ ابزار کلیدِ خصوصی را نشان داد');
  }
});
