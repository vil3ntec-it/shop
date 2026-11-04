'use strict';
/**
 * O-18، پیش‌نیاز: کلیدِ امضای مجوز در production **هرگز بی‌صدا از نو ساخته نمی‌شود**.
 *
 * کلیدِ تازه یعنی مجوزِ همهٔ نصب‌ها و هر کدِ اشتراکِ آفلاینی که با کلیدِ پیشین امضا شده رد می‌شود (برنامه‌ها کلیدِ
 * عمومی را سنجاق می‌کنند). پس production (و بی NODE_ENV) بی کلید: یک خطِ روشنِ خطا، ۵۰۳ ِ `license_key_missing` روی
 * صدورِ مجوز و کد، و بقیهٔ سرور سرِ کار. development/test همان ساختِ خودکارِ پیشین را دارند.
 * ⛔ هیچ کلیدی در خروجیِ آزمون چاپ نمی‌شود؛ فقط نبودنِ `BEGIN` و تکه‌های کلید سنجیده می‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { execFileSync } = require('child_process');
const h = require('./helpers');
const { query, one } = require('../src/db');
const plans = require('../src/lib/plans');
const license = require('../src/lib/license');

const ENV = ['NODE_ENV', 'LICENSE_PRIVATE_KEY', 'LICENSE_PRIVATE_KEY_FILE'];
const saved = Object.fromEntries(ENV.map((k) => [k, process.env[k]]));
const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'lic-key-'));

test.before(async () => { await h.start(); });
test.after(async () => {
  for (const k of ENV) { if (saved[k] === undefined) delete process.env[k]; else process.env[k] = saved[k]; }
  license._reset();
  fs.rmSync(tmp, { recursive: true, force: true });
  await h.stop();
});

const newPem = () => crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1',
  privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' },
}).privateKey;
const spkiOf = (pem) => crypto.createPublicKey(pem).export({ type: 'spki', format: 'der' }).toString('base64');
const kidOf = (pem) => crypto.createHash('sha256').update(Buffer.from(spkiOf(pem), 'base64')).digest('hex').slice(0, 16);
const dbKey = async () => (await one("SELECT value FROM app_config WHERE key='license_private_key'"))?.value || '';
/** هیچ تکه‌ای از کلیدِ خصوصی (سرخط یا بدنهٔ base64) در متن نیست */
function noSecret(text, pem) {
  assert.doesNotMatch(text, /BEGIN|PRIVATE KEY/);
  const body = pem.replace(/-----[^-]+-----/g, '').replace(/\s+/g, '');
  for (let i = 0; i + 24 <= body.length; i += 24) assert.ok(!text.includes(body.slice(i, i + 24)), 'تکه‌ای از کلید چاپ شد');
}

/** محیطِ هر آزمون از صفر: بی کلید در محیط، فایل و پایگاه‌داده؛ کشِ کلید خالی */
async function scene(env, { keepDb = false } = {}) {
  for (const k of ENV) delete process.env[k];
  for (const [k, v] of Object.entries(env)) process.env[k] = v;
  if (!keepDb) await query("DELETE FROM app_config WHERE key IN ('license_private_key','license_public_key')");
  license._reset();
}
function logger() {
  const lines = [];
  const at = (kind) => (...a) => lines.push({ kind, text: a.join(' ') });
  return { lines, log: at('log'), info: at('log'), warn: at('warn'), error: at('error') };
}

test('production بی کلید: کلیدِ تازه ساخته نمی‌شود؛ مجوز و کدِ آفلاین ۵۰۳ ِ license_key_missing؛ بقیهٔ سرور سرِ کار', async () => {
  const u = await h.newUser('بی کلید');
  await h.post('/api/shop', { name: 'دکانِ بی کلید' }, { token: u.accessToken });
  //  بی NODE_ENV هم production است (همان قاعدهٔ SEC-AUTH-06)
  for (const env of [{ NODE_ENV: 'production' }, {}]) {
    await scene(env);
    const pk = await h.get('/api/license/public-key');
    assert.equal(pk.status, 503, JSON.stringify(env));
    assert.equal(pk.body.error.code, 'license_key_missing');
    const s = await h.post('/api/license/sync', { device: { uid: 'DEV-NOKEY' } }, { token: u.accessToken });
    assert.equal(s.status, 503);
    assert.equal(s.body.error.code, 'license_key_missing');
    await assert.rejects(license.signBytes(Buffer.from('کد')), (e) => e.status === 503 && e.code === 'license_key_missing');
    //  سنجشِ کدِ آفلاین هم همان را می‌گوید، نه «کد نامعتبر است»
    await assert.rejects(license.verifyBytes(Buffer.from('کد'), Buffer.alloc(64)), (e) => e.code === 'license_key_missing');
    assert.equal(await dbKey(), '', 'production هرگز کلیدِ تازه نمی‌سازد');
    assert.equal((await h.get('/api/health')).status, 200);
    assert.equal((await h.get('/api/ready')).status, 200, 'نبودِ کلید سرور را «ناآماده» نمی‌کند (فروش و همگام‌سازی کار می‌کنند)');
    assert.equal((await h.get('/api/shop', { token: u.accessToken })).status, 200);
  }
});

test('راه‌اندازی بی کلید: یک خطِ روشنِ خطا، بی کلید، با kid ِ کلیدِ پیشین', async () => {
  await scene({ NODE_ENV: 'production' });
  const old = newPem();
  //  گامِ ۶ ِ OWNER-ACTIONS: ردیفِ خصوصی پاک شد، عمومی ماند؛ بعد متغیر گم شد
  await plans.setConfig('license_public_key', spkiOf(old));
  const log = logger();
  const r = await license.startupCheck(log);
  assert.equal(r.ok, false);
  assert.equal(r.code, 'license_key_missing');
  const errors = log.lines.filter((l) => l.kind === 'error');
  assert.equal(errors.length, 1, 'دقیقاً یک خطِ خطا');
  assert.match(errors[0].text, /license_key_missing/);
  assert.match(errors[0].text, /LICENSE_PRIVATE_KEY/);
  assert.ok(errors[0].text.includes(`kid=${kidOf(old)}`), 'kid ِ کلیدِ پیشین برای پیدا کردنش');
  noSecret(log.lines.map((l) => l.text).join('\n'), old);
  assert.equal(await dbKey(), '');
});

test('بی کلیدِ خصوصی، با کلیدِ عمومیِ مانده: مجوز و کدهای آفلاینِ پیشین (فروشگاه، پمپ ۱ و ۲) سنجیده می‌شوند؛ فقط امضا ۵۰۳', async () => {
  const shopOffline = require('../src/lib/shop-offline-codes');
  const pumpOffline = require('../src/lib/offline-codes');
  const old = newPem();
  //  کدها و مجوز با کلیدِ پیشین، وقتی هنوز بود (سرورِ خانگی/دسکتاپ بی NODE_ENV)
  await scene({ LICENSE_PRIVATE_KEY: old });
  const pc = pumpOffline.parseComputer(pumpOffline.computerOfFingerprint(`win-${'c'.repeat(32)}`));
  const shopCode = await shopOffline.encode(shopOffline.body({ plan: 'sub', machine: pc.machine, account: Buffer.alloc(4),
    serial: crypto.randomBytes(4), issuedDay: 1000, endDay: 5000 }), license.signBytes);
  const pump2 = await pumpOffline.encode2(pumpOffline.body2({ plan: 'vip', machine: pc.machine, account: Buffer.alloc(4),
    serial: crypto.randomBytes(4), issuedDay: 1000, endDay: 5000 }), license.signBytes);
  const v1 = Buffer.alloc(pumpOffline.BODY_LEN);
  v1[0] = 1; v1[1] = 2;
  pc.machine.copy(v1, 2);
  crypto.randomBytes(6).copy(v1, 12);
  v1.writeUInt32BE(Math.floor(Date.now() / 1000), 18);
  v1.writeUInt32BE(Math.floor(Date.now() / 1000) + 30 * 86400, 22);
  Buffer.from(await license.keyId(), 'hex').subarray(0, 8).copy(v1, 26);
  const pump1 = pumpOffline.b32encode(Buffer.concat([v1, await license.signBytes(Buffer.concat([Buffer.from('PYOC1\0', 'latin1'), v1]))]));
  const lic = await license.issue({ deviceUid: 'D-1', accountId: 'u1', subscriptionEndsAt: Date.now() + 86400000 });

  //  گامِ ۶ ِ OWNER-ACTIONS انجام شد و بعد متغیر گم شد: نه محیط، نه ردیفِ خصوصی؛ فقط `license_public_key`
  for (const env of [{ NODE_ENV: 'production' }, {}]) {
    await scene(env);
    await plans.setConfig('license_public_key', spkiOf(old));
    assert.equal((await shopOffline.verify(shopCode))?.plan, 'sub', 'کدِ آفلاینِ فروشگاه');
    assert.equal((await pumpOffline.verify(pump2))?.version, 2, 'کدِ آفلاینِ پمپ ۲');
    assert.equal((await pumpOffline.verify(pump1))?.version, 1, 'کدِ آفلاینِ پمپ ۱ (kid از کلیدِ عمومی)');
    const [hd, pl, sg] = lic.token.split('.');
    assert.equal(await license.verifyBytes(Buffer.from(`${hd}.${pl}`), Buffer.from(sg, 'base64url')), true, 'امضای مجوز');
    assert.equal(await license.verifyBytes(Buffer.from('دست‌کاری'), Buffer.from(sg, 'base64url')), false);
    const pk = await h.get('/api/license/public-key');
    assert.equal(pk.status, 200, 'کلیدِ عمومیِ درست هنوز داده می‌شود');
    assert.equal(pk.body.publicKey, spkiOf(old));
    await assert.rejects(license.signBytes(Buffer.from('x')), (e) => e.code === 'license_key_missing');
    assert.equal(await dbKey(), '', 'هنوز هیچ کلیدِ تازه‌ای ساخته نشد');
  }
});

test('سازگاری: کلیدِ موجود در پایگاه‌داده در production همان می‌ماند و امضا می‌کند', async () => {
  await scene({ NODE_ENV: 'production' });
  const pem = newPem();
  await plans.setConfig('license_private_key', pem);
  const pk = await h.get('/api/license/public-key');
  assert.equal(pk.status, 200);
  assert.equal(pk.body.publicKey, spkiOf(pem));
  const sig = await license.signBytes(Buffer.from('کد'));
  assert.equal(await license.verifyBytes(Buffer.from('کد'), sig), true);
  assert.equal(await dbKey(), pem, 'ردیفِ پیشین دست نخورد');
  const log = logger();
  assert.equal((await license.startupCheck(log)).ok, true);
  assert.equal(log.lines.filter((l) => l.kind !== 'log').length, 0);
  assert.ok(log.lines.some((l) => l.text.includes(`kid=${kidOf(pem)}`)));
  noSecret(log.lines.map((l) => l.text).join('\n'), pem);
});

test('کلید از LICENSE_PRIVATE_KEY (PEM ِ چندخطی و یک‌خطیِ «\\n» ِ .env) بر پایگاه‌داده پیش است؛ ناهمسانی هشدار می‌دهد', async () => {
  for (const oneLine of [false, true]) {
    const envPem = newPem();
    const dbPem = newPem();
    await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY: oneLine ? envPem.trim().replace(/\n/g, '\\n') : envPem });
    await plans.setConfig('license_private_key', dbPem);
    await plans.setConfig('license_public_key', spkiOf(dbPem));
    const pk = await h.get('/api/license/public-key');
    assert.equal(pk.status, 200);
    assert.equal(pk.body.publicKey, spkiOf(envPem));
    const log = logger();
    const r = await license.startupCheck(log);
    assert.equal(r.ok, true);
    assert.equal(r.source, 'env');
    const warns = log.lines.filter((l) => l.kind === 'warn');
    assert.equal(warns.length, 1, 'کلیدِ محیط با کلیدِ پیشینِ پایگاه‌داده فرق دارد ⇒ یک هشدار');
    assert.ok(warns[0].text.includes(kidOf(dbPem)) && warns[0].text.includes(kidOf(envPem)));
    noSecret(log.lines.map((l) => l.text).join('\n'), envPem);
  }
  //  همان کلید در محیط و پایگاه‌داده (جابه‌جاییِ درست) ⇒ هیچ هشداری
  const same = newPem();
  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY: same });
  await plans.setConfig('license_public_key', spkiOf(same));
  const log = logger();
  assert.equal((await license.startupCheck(log)).ok, true);
  assert.equal(log.lines.filter((l) => l.kind !== 'log').length, 0);
});

test('کلید از LICENSE_PRIVATE_KEY_FILE؛ فایلِ نبود یا کلیدِ خراب ⇒ ۵۰۳ ِ license_key_invalid، بی برگشت به پایگاه‌داده و بی ساختن', async () => {
  const pem = newPem();
  const file = path.join(tmp, 'license.pem');
  fs.writeFileSync(file, pem, { mode: 0o600 });
  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY_FILE: file });
  const pk = await h.get('/api/license/public-key');
  assert.equal(pk.status, 200);
  assert.equal(pk.body.publicKey, spkiOf(pem));
  assert.equal(await dbKey(), '');

  const rsa = crypto.generateKeyPairSync('rsa', { modulusLength: 2048,
    privateKeyEncoding: { type: 'pkcs8', format: 'pem' }, publicKeyEncoding: { type: 'spki', format: 'pem' } }).privateKey;
  const broken = [
    { NODE_ENV: 'production', LICENSE_PRIVATE_KEY_FILE: path.join(tmp, 'nope.pem') },
    { NODE_ENV: 'production', LICENSE_PRIVATE_KEY: 'not a key' },
    { NODE_ENV: 'production', LICENSE_PRIVATE_KEY: rsa },
    //  development هم روی کلیدِ پیکربندی‌شدهٔ خراب کلیدِ تازه نمی‌سازد
    { NODE_ENV: 'development', LICENSE_PRIVATE_KEY: 'not a key' },
  ];
  for (const env of broken) {
    const dbPem = newPem();
    await scene(env);
    await plans.setConfig('license_private_key', dbPem);
    const r = await h.get('/api/license/public-key');
    assert.equal(r.status, 503, JSON.stringify(Object.keys(env)));
    assert.equal(r.body.error.code, 'license_key_invalid');
    noSecret(JSON.stringify(r.body), rsa);
    assert.equal(await dbKey(), dbPem, 'کلیدِ پایگاه‌داده دست نخورد');
  }
});

test('development و test همان ساختِ خودکار: یک بار، ماندگار، و دو درخواستِ هم‌زمان یک کلید', async () => {
  for (const env of ['development', 'test']) {
    await scene({ NODE_ENV: env });
    const a = await h.get('/api/license/public-key');
    assert.equal(a.status, 200);
    assert.ok(await dbKey(), 'کلید در پایگاه‌داده نشست');
    license._reset();
    const b = await h.get('/api/license/public-key');
    assert.equal(b.body.publicKey, a.body.publicKey, 'بارِ دوم از پایگاه‌داده، نه کلیدِ تازه');
  }
  await scene({ NODE_ENV: 'test' });
  const [x, y] = await Promise.all([license.publicKey(), license.publicKey()]);
  assert.equal(x, y, 'دو بارِ نخستِ هم‌زمان دو کلیدِ جدا نمی‌سازند');
  const stored = await dbKey();
  assert.equal(spkiOf(stored), x);
});

test('ابزارِ بیرون آوردن: همان کلید در فایلِ ۶۰۰، روی فایلِ موجود نمی‌نویسد، هرگز نمی‌سازد', async () => {
  const { exportKey } = require('../scripts/license-key');
  await scene({ NODE_ENV: 'production' });
  const pem = newPem();
  await plans.setConfig('license_private_key', pem);
  const out = path.join(tmp, 'license.env');
  const r = await exportKey(out);
  assert.deepEqual(Object.keys(r).sort(), ['kid', 'source']);
  assert.equal(r.source, 'db');
  assert.equal(r.kid, kidOf(pem));
  if (process.platform !== 'win32') assert.equal(fs.statSync(out).mode & 0o777, 0o600);
  const line = fs.readFileSync(out, 'utf8');
  assert.match(line, /^LICENSE_PRIVATE_KEY="[^\n"]+"\n$/, 'یک خطِ آمادهٔ .env');
  //  همان خط در محیط ⇒ همان کلیدِ عمومی (همان چیزی که بارگذارِ .env ِ سرور می‌خواند)
  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY: line.trim().slice('LICENSE_PRIVATE_KEY="'.length, -1) }, { keepDb: true });
  assert.equal(await license.publicKey(), spkiOf(pem));
  await assert.rejects(exportKey(out), (e) => e.code === 'EEXIST');
  assert.equal(fs.readFileSync(out, 'utf8'), line, 'فایلِ موجود دست نخورد');
  const pemOut = path.join(tmp, 'license-out.pem');
  await exportKey(pemOut, { pem: true });
  assert.equal(spkiOf(fs.readFileSync(pemOut, 'utf8')), spkiOf(pem));

  //  بی هیچ کلیدی، حتی در development: خطا، بی فایل و بی ساختن
  await scene({ NODE_ENV: 'development' });
  const none = path.join(tmp, 'none.env');
  await assert.rejects(exportKey(none), (e) => e.code === 'license_key_missing');
  assert.equal(fs.existsSync(none), false);
  assert.equal(await dbKey(), '');
});

test('ابزارِ بیرون آوردن از خطِ فرمان: فقط نشانی و kid چاپ می‌شود، هرگز کلید', () => {
  const pem = newPem();
  const out = path.join(tmp, 'cli.env');
  const run = (args) => execFileSync(process.execPath, [path.join(__dirname, '..', 'scripts', 'license-key.js'), ...args], {
    cwd: path.join(__dirname, '..'),
    env: { PATH: process.env.PATH, HOME: tmp, NODE_ENV: 'production', DATABASE_URL: 'pglite:memory', LICENSE_PRIVATE_KEY: pem },
    encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
  });
  const printed = run(['export', out]) + run(['status']);
  assert.ok(printed.includes(kidOf(pem)));
  noSecret(printed, pem);
  assert.ok(fs.existsSync(out));
});

test('generate: فقط سرورِ نو؛ هر ردِ کلید یا امضای پیشین ⇒ نمی‌سازد و فایلی نمی‌نویسد', async () => {
  const { generateKey, exportKey } = require('../scripts/license-key');
  const out = path.join(tmp, 'new.env');
  const refuses = async (code, what) => {
    await assert.rejects(generateKey(out), (e) => e.code === code, what);
    assert.equal(fs.existsSync(out), false, `${what}: فایلی نوشته نشد`);
    assert.equal(await dbKey(), '', `${what}: چیزی در پایگاه‌داده ننشست`);
  };
  const pem = newPem();
  const file = path.join(tmp, 'gen.pem');
  fs.writeFileSync(file, pem, { mode: 0o600 });
  await query("DELETE FROM devices WHERE platform = 'license'");
  await query('DELETE FROM pump_offline_codes');
  await query('DELETE FROM shop_offline_codes');

  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY: pem });
  await refuses('license_key_exists', 'کلید در محیط');
  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY_FILE: path.join(tmp, 'gone.pem') });
  await refuses('license_key_exists', 'نشانیِ فایل، حتی نخواندنی');
  await scene({ NODE_ENV: 'production' });
  await plans.setConfig('license_public_key', spkiOf(pem));
  await refuses('license_key_exists', 'فقط ردیفِ عمومیِ کلیدِ پیشین');
  await scene({ NODE_ENV: 'production' });
  await query(`INSERT INTO devices (id, user_id, device_uid, platform, created_at)
               SELECT 'dev_gen', id, 'GEN-1', 'license', 0 FROM users LIMIT 1`);
  await refuses('license_key_used', 'دستگاهی که با کلیدِ پیشین مجوز گرفته (جابه‌جایی)');
  await query("DELETE FROM devices WHERE id = 'dev_gen'");
  //  ردیفِ خصوصی: همان پیامِ «هست» با kid، و کلید دست نمی‌خورد
  await plans.setConfig('license_private_key', pem);
  await assert.rejects(generateKey(out), (e) => e.code === 'license_key_exists' && e.message.includes(kidOf(pem)));
  assert.equal(await dbKey(), pem);
  assert.equal(fs.existsSync(out), false);

  //  سرورِ نو (هیچ رد): کلیدِ تازه فقط در فایلِ ۶۰۰؛ نه در پایگاه‌داده، نه چاپ
  await scene({ NODE_ENV: 'production' });
  const r = await generateKey(out);
  assert.deepEqual(Object.keys(r), ['kid']);
  if (process.platform !== 'win32') assert.equal(fs.statSync(out).mode & 0o777, 0o600);
  const line = fs.readFileSync(out, 'utf8');
  assert.match(line, /^LICENSE_PRIVATE_KEY="[^\n"]+"\n$/);
  assert.equal(await dbKey(), '');
  await scene({ NODE_ENV: 'production', LICENSE_PRIVATE_KEY: line.trim().slice('LICENSE_PRIVATE_KEY="'.length, -1) });
  assert.equal((await license.startupCheck(logger())).kid, r.kid, 'همان خط در .env ⇒ همان kid');
  await assert.rejects(generateKey(path.join(tmp, 'again.env')), (e) => e.code === 'license_key_exists', 'بارِ دوم نمی‌سازد');
  await assert.rejects(exportKey(out), (e) => e.code === 'EEXIST');
});

test('generate از خطِ فرمان: پایگاه‌دادهٔ نخواندنی ⇒ نمی‌سازد؛ هیچ کلیدی چاپ نمی‌شود', () => {
  const out = path.join(tmp, 'cli-gen.env');
  let failed = null;
  try {
    //  pglite:memory بی مهاجرت ⇒ `app_config` نیست ⇒ نبودنِ کلید ثابت نمی‌شود
    execFileSync(process.execPath, [path.join(__dirname, '..', 'scripts', 'license-key.js'), 'generate', out], {
      cwd: path.join(__dirname, '..'),
      env: { PATH: process.env.PATH, HOME: tmp, NODE_ENV: 'production', DATABASE_URL: 'pglite:memory' },
      encoding: 'utf8', stdio: ['ignore', 'pipe', 'pipe'],
    });
  } catch (e) { failed = e; }
  assert.ok(failed && failed.status === 1, 'باید با کدِ ۱ بایستد');
  assert.equal(fs.existsSync(out), false);
  assert.doesNotMatch(`${failed.stdout}${failed.stderr}`, /BEGIN|PRIVATE KEY/);
});
