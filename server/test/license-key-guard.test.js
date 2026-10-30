'use strict';
/**
 * SUB-04 / F0-28 — کلیدِ امضای مجوز بی‌صدا عوض نمی‌شود.
 *
 * برنامه‌ها کلیدِ عمومی را بارِ اول از سرور می‌گیرند و نگه می‌دارند. کلیدِ تازه یعنی مجوزِ **همهٔ** نصب‌ها رد
 * می‌شود. صاحب محصول برای بیرون بردنِ کلیدِ خصوصی از `app_config` (پشتیبان‌ها) آن را در `LICENSE_PRIVATE_KEY`
 * می‌گذارد و بعد ردیفِ `license_private_key` را پاک می‌کند (OWNER-ACTIONS-fa.md بخشِ ۳). تا ۲.۱۴.۴ دو خطا
 * بی‌صدا همه را قفل می‌کرد:
 *   ۱) ردیفِ خصوصی پاک و متغیر گم ⇒ سرور کلیدِ **تازه** می‌ساخت و می‌نشاند.
 *   ۲) متغیرِ محیط با کلیدِ دیگری پر شده ⇒ سرور با همان امضا می‌کرد.
 * حالا وقتی کلیدِ عمومیِ ثبت‌شده هست، هر دو صدورِ مجوز را با خطای روشن می‌بندند (نصب‌ها تا پایانِ مجوزِ ده‌روزه‌شان
 * کار می‌کنند) مگر `LICENSE_ALLOW_NEW_KEY=1` (چرخاندنِ عمدی). نصبِ تازه همچنان کلید را خودش می‌سازد.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('crypto');
const h = require('./helpers');
const { query, one } = require('../src/db');

const PRIV = 'license_private_key';
const PUB = 'license_public_key';
const cfg = async (key) => (await one('SELECT value FROM app_config WHERE key=$1', [key]))?.value ?? null;
const pem = () => crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1', privateKeyEncoding: { type: 'pkcs8', format: 'pem' },
  publicKeyEncoding: { type: 'spki', format: 'pem' },
}).privateKey;
const spki = (p) => crypto.createPublicKey(crypto.createPrivateKey(p)).export({ type: 'spki', format: 'der' }).toString('base64');

/** نمونهٔ تازهٔ ماژول (بی کلیدِ در حافظه) — مثلِ روشن شدنِ دوبارهٔ سرور. */
function fresh() {
  delete require.cache[require.resolve('../src/lib/license')];
  return require('../src/lib/license');
}
function env(vars, fn) {
  const before = {};
  for (const k of Object.keys(vars)) { before[k] = process.env[k]; if (vars[k] === null) delete process.env[k]; else process.env[k] = vars[k]; }
  const restore = () => { for (const k of Object.keys(vars)) { if (before[k] === undefined) delete process.env[k]; else process.env[k] = before[k]; } };
  return Promise.resolve().then(fn).finally(restore);
}
const NO_ENV = { LICENSE_PRIVATE_KEY: null, LICENSE_ALLOW_NEW_KEY: null };

test.before(async () => { await env(NO_ENV, () => h.start()); });
test.after(async () => { await h.stop(); });

test('SUB-04 ۱) ردیفِ خصوصی پاک و متغیر نیست ⇒ کلیدِ تازه ساخته نمی‌شود؛ /api/license/public-key خطا، کلیدِ عمومی همان', async () => {
  await env(NO_ENV, async () => {
    const old = pem();
    await query(`INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,0), ($3,$4,0)
                 ON CONFLICT (key) DO UPDATE SET value=excluded.value`, [PUB, spki(old), PRIV, old]);
    //  گامِ ۶ ِ صاحب محصول بی گامِ ۴: ردیفِ خصوصی پاک، متغیر نیست
    await query('DELETE FROM app_config WHERE key=$1', [PRIV]);

    const r = await h.get('/api/license/public-key');
    assert.ok(r.status >= 500, `${r.status} ${JSON.stringify(r.body)}`);
    assert.equal(await cfg(PUB), spki(old), 'کلیدِ عمومیِ نصب‌ها عوض نشد');
    assert.equal(await cfg(PRIV), null, 'هیچ کلیدِ خصوصیِ تازه‌ای ننشست');

    await assert.rejects(fresh().publicKey(), (e) => e.code === 'license_key_missing');
    assert.equal(await cfg(PUB), spki(old));
  });
});

test('SUB-04 ۲) چرخاندنِ عمدی با LICENSE_ALLOW_NEW_KEY=1 ⇒ کلیدِ تازه، هر دو ردیف به‌روز', async () => {
  await env({ ...NO_ENV, LICENSE_ALLOW_NEW_KEY: '1' }, async () => {
    const was = await cfg(PUB);
    const lic = fresh();
    const pub = await lic.publicKey();
    assert.notEqual(pub, was);
    assert.equal(await cfg(PUB), pub);
    assert.equal(spki(await cfg(PRIV)), pub);
  });
});

test('SUB-04 ۳) LICENSE_PRIVATE_KEY همان کلیدِ ثبت‌شده ⇒ کار می‌کند؛ کلیدِ دیگر ⇒ صدور بسته تا چرخاندنِ عمدی', async () => {
  const stored = await cfg(PRIV);
  const pub = await cfg(PUB);
  await env({ ...NO_ENV, LICENSE_PRIVATE_KEY: stored }, async () => {
    const lic = fresh();
    assert.deepEqual(await lic.checkKeyContinuity(), { ok: true });
    assert.equal(await lic.publicKey(), pub);
  });

  const other = pem();
  await env({ ...NO_ENV, LICENSE_PRIVATE_KEY: other }, async () => {
    const lic = fresh();
    const c = await lic.checkKeyContinuity();
    assert.equal(c.ok, false);
    assert.equal(c.code, 'license_key_mismatch');
    await assert.rejects(lic.publicKey(), (e) => e.code === 'license_key_mismatch');
    await assert.rejects(lic.issue({ deviceUid: 'dev-1', accountId: 'acc-1', plan: 'm1', subscriptionEndsAt: Date.now() + 1e9 }),
      (e) => e.code === 'license_key_mismatch', 'هیچ مجوزی با کلیدِ ناهمخوان امضا نمی‌شود');
    assert.equal(await cfg(PUB), pub, 'کلیدِ عمومیِ ثبت‌شده دست نخورد');
  });

  await env({ ...NO_ENV, LICENSE_PRIVATE_KEY: other, LICENSE_ALLOW_NEW_KEY: '1' }, async () => {
    const lic = fresh();
    assert.equal((await lic.checkKeyContinuity()).ok, true);
    assert.equal(await lic.publicKey(), spki(other));
    assert.equal(await cfg(PUB), spki(other), 'چرخاندنِ عمدی کلیدِ عمومیِ ثبت‌شده را هم عوض می‌کند');
  });
});

test('SUB-04 ۴) متغیرِ محیط روی نصبِ بی کلیدِ ثبت‌شده ⇒ کلیدِ عمومی ثبت می‌شود تا گم شدنِ بعدیِ متغیر گرفته شود', async () => {
  await query('DELETE FROM app_config WHERE key IN ($1,$2)', [PUB, PRIV]);
  const k = pem();
  await env({ ...NO_ENV, LICENSE_PRIVATE_KEY: k }, async () => {
    assert.deepEqual(await fresh().checkKeyContinuity(), { ok: true });
  });
  assert.equal(await cfg(PUB), spki(k));
  assert.equal(await cfg(PRIV), null, 'کلیدِ خصوصیِ محیط هرگز در app_config نوشته نمی‌شود');
  await env(NO_ENV, async () => {
    await assert.rejects(fresh().publicKey(), (e) => e.code === 'license_key_missing');
  });
});

test('SUB-04 ۵) نصبِ تازه (هیچ کلیدی) ⇒ همان رفتارِ همیشه: یک بار ساخته و نگه داشته می‌شود', async () => {
  await query('DELETE FROM app_config WHERE key IN ($1,$2)', [PUB, PRIV]);
  await env(NO_ENV, async () => {
    const a = await fresh().publicKey();
    assert.equal(await cfg(PUB), a);
    assert.equal(await fresh().publicKey(), a, 'روشن شدنِ دوباره همان کلید');
  });
});

test('SUB-04 ۶) نصبِ تازه و چند درخواستِ هم‌زمان ⇒ یک کلید، همان که ثبت شد', async () => {
  await query('DELETE FROM app_config WHERE key IN ($1,$2)', [PUB, PRIV]);
  await env(NO_ENV, async () => {
    const lic = fresh();
    const all = await Promise.all(Array.from({ length: 8 }, () => lic.publicKey()));
    assert.equal(new Set(all).size, 1, 'همه یک کلید');
    assert.equal(await cfg(PUB), all[0]);
    assert.equal(spki(await cfg(PRIV)), all[0], 'کلیدِ نشسته همان است که امضا می‌کند');
  });
});

test('SUB-04 ۷) PEM ِ یک‌خطی با \\n ِ نوشتاری در LICENSE_PRIVATE_KEY همان کلید است', async () => {
  const k = pem();
  await env({ ...NO_ENV, LICENSE_PRIVATE_KEY: k.trim().replace(/\n/g, '\\n') }, async () => {
    const lic = fresh();
    assert.equal(await lic.publicKey(), spki(k));
  });
});
