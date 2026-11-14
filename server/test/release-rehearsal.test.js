'use strict';
/**
 * OPS-03@29 — «آزمونِ انتشارِ برنامه‌ها روی کپیِ موقت» (`scripts/release-rehearsal.js`، گامِ پیش از انتشارِ ورک‌فلوی
 * `release-stage.yml`).
 *
 * آن‌چه این پرونده قفل می‌کند:
 *   ۱) فایل‌های انتشار پیش از هر چیز: نسخهٔ `version.txt`، هشِ هر نصبی در `SHA256SUMS.txt`، امضای فهرست با کلیدِ
 *      عمومیِ برنامه (همان SHA256withECDSA ِ `ReleaseSignature.kt`)، یک نصبیِ اندروید با همان نسخه، چهار نصبیِ کامپیوتر.
 *      هر ناسازگاری ⇒ خطای تمرین (کدِ ۲)، نه انتشار.
 *   ۲) دودآزماییِ کامل روی سرورِ واقعی (درهای مدیر، `/api/releases`، `/api/errors`) با GitHub ِ ساختگیِ همان فایل‌ها:
 *      فقط یک دکان ⇒ ۱۰۰٪ ⇒ ایستِ خودکار ⇒ ادامه ⇒ پایان؛ بایت‌ها، فهرست و امضای سروشده همان فایل‌ها.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const os = require('node:os');
const fs = require('node:fs');
const path = require('node:path');
const crypto = require('node:crypto');

const R = require('../scripts/release-rehearsal');

const VERSION = '3.2.900';
const keys = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });
const PUB = keys.publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
const other = crypto.generateKeyPairSync('ec', { namedCurve: 'prime256v1' });

/** پوشهٔ انتشارِ ساختگی، مثلِ خروجیِ کارِ «انتشار» ِ «توحید بومی» (آزمایشی) */
function makeDir({ tag = 'tohid-native', version = VERSION, files = null, sign = keys.privateKey, sumsFor = null } = {}) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-reh-'));
  const list = files || (tag === 'tohid-native'
    ? { [`Tohid-Native-${version}.apk.beta`]: Buffer.from(`apk-${version}-${'x'.repeat(2000)}`) }
    : Object.fromEntries(['Tohid-Windows-Offline.exe', 'Tohid-Mac-AppleSilicon.dmg', 'Tohid-Mac-Intel.dmg', 'Tohid-Linux.deb']
      .map((n) => [n, Buffer.from(`${n}-${version}`)])));
  for (const [n, b] of Object.entries(list)) fs.writeFileSync(path.join(dir, n), b);
  fs.writeFileSync(path.join(dir, 'version.txt'), `${version}\n`);
  const sums = Object.entries(sumsFor || list).map(([n, b]) => `${crypto.createHash('sha256').update(b).digest('hex')}  ${n}`).join('\n') + '\n';
  fs.writeFileSync(path.join(dir, 'SHA256SUMS.txt'), sums);
  if (sign) fs.writeFileSync(path.join(dir, 'SHA256SUMS.txt.sig'), crypto.sign('sha256', Buffer.from(sums), sign).toString('base64'));
  return dir;
}

const rejects = (fn, re) => assert.throws(fn, (e) => e instanceof R.RehearsalError && re.test(e.message));

/* ══ ۱) فایل‌ها ═══════════════════════════════════════════════════ */

test('فایل‌های درست: اندروید و کامپیوتر، با و بی کلیدِ عمومی', () => {
  const a = R.loadArtifacts(makeDir(), { tag: 'tohid-native', version: VERSION, pubkey: PUB });
  assert.deepEqual(a.updaters, [`Tohid-Native-${VERSION}.apk.beta`]);
  assert.ok(a.sig);
  const pem = keys.publicKey.export({ format: 'pem', type: 'spki' });
  assert.ok(R.loadArtifacts(makeDir(), { tag: 'tohid-native', version: VERSION, pubkey: pem }));
  const d = R.loadArtifacts(makeDir({ tag: 'tohid-desktop', version: '1.0.500' }), { tag: 'tohid-desktop', version: '1.0.500', pubkey: PUB });
  assert.equal(d.updaters.length, 4);
  //  بی کلیدِ عمومی (امروزِ مخزن: vars خالی) فقط هش سنجیده می‌شود
  assert.ok(R.loadArtifacts(makeDir({ sign: null }), { tag: 'tohid-native', version: VERSION }));
});

test('⛔ فایل‌های ناسازگار ⇒ خطای تمرین، نه انتشار', () => {
  rejects(() => R.loadArtifacts(makeDir(), { tag: 'tohid-native', version: '3.2.901' }), /version\.txt/);
  //  نصبیِ دست‌کاری‌شده پس از ساختنِ فهرست
  const t = makeDir();
  fs.appendFileSync(path.join(t, `Tohid-Native-${VERSION}.apk.beta`), 'tampered');
  rejects(() => R.loadArtifacts(t, { tag: 'tohid-native', version: VERSION }), /هشِ/);
  //  امضا با کلیدِ دیگر
  rejects(() => R.loadArtifacts(makeDir({ sign: other.privateKey }), { tag: 'tohid-native', version: VERSION, pubkey: PUB }), /امضای/);
  //  کلیدِ عمومی هست ولی امضا نیست ⇒ برنامه نمی‌پذیرد
  rejects(() => R.loadArtifacts(makeDir({ sign: null }), { tag: 'tohid-native', version: VERSION, pubkey: PUB }), /sig/);
  //  نصبی در فهرست نیست
  rejects(() => R.loadArtifacts(makeDir({ sumsFor: { 'other.apk': Buffer.from('x') } }), { tag: 'tohid-native', version: VERSION }), /SHA256SUMS/);
  //  دو نصبیِ اندروید، یا نامِ نسخهٔ دیگر
  rejects(() => R.loadArtifacts(makeDir({ files: { [`Tohid-Native-${VERSION}.apk.beta`]: Buffer.from('a'), [`Tohid-Native-${VERSION}.apk`]: Buffer.from('b') } }),
    { tag: 'tohid-native', version: VERSION }), /یک نصبی/);
  rejects(() => R.loadArtifacts(makeDir({ files: { 'Tohid-Native-3.2.1.apk.beta': Buffer.from('a') } }), { tag: 'tohid-native', version: VERSION }), /نسخهٔ/);
  //  کامپیوترِ ناقص
  rejects(() => R.loadArtifacts(makeDir({ tag: 'tohid-desktop', version: '1.0.500', files: { 'Tohid-Linux.deb': Buffer.from('d') } }),
    { tag: 'tohid-desktop', version: '1.0.500' }), /ناقص/);
  //  برچسب یا نسخهٔ ناجور
  rejects(() => R.loadArtifacts(makeDir(), { tag: 'tohid-admin', version: VERSION }), /برچسب/);
  rejects(() => R.loadArtifacts(makeDir(), { tag: 'tohid-native', version: 'v3.2.900' }), /نسخهٔ نادرست/);
});

/* ══ ۲) دودآزمایی روی سرورِ واقعی ══════════════════════════════════════ */

let h, stub, token;
test.before(async () => {
  const art = R.loadArtifacts(makeDir(), { tag: 'tohid-native', version: VERSION, pubkey: PUB });
  stub = R.stubGitHub(art);
  stub.art = art;
  await new Promise((r) => stub.server.listen(0, '127.0.0.1', r));
  process.env.RELEASE_API_BASE = `http://127.0.0.1:${stub.server.address().port}`;
  process.env.RELEASE_REPO = R.REPO;
  process.env.RELEASE_CACHE_PATH = fs.mkdtempSync(path.join(os.tmpdir(), 'rel-reh-cache-'));
  h = require('./helpers');
  await h.start();
  const { query, newId, now } = require('../src/db');
  const pw = require('../src/lib/password');
  await query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,'rehroot','ارشد',$2,'superadmin','active',$3)`,
    [newId('adm'), await pw.hashPassword('Root!12345'), now()]
  );
  token = (await h.post('/api/admin/login', { username: 'rehroot', password: 'Root!12345' })).body.token;
  assert.ok(token);
});
test.after(async () => { await h.stop(); stub.server.close(); });

test('دودآزماییِ کامل: یک دکان ⇒ همه ⇒ ایستِ خودکار ⇒ ادامه ⇒ پایان، با بایت‌ها و امضای همان انتشار', async () => {
  const lines = [];
  assert.equal(await R.smoke({ url: h.base(), token, art: stub.art, pubkey: PUB, log: (x) => lines.push(x) }), true);
  assert.ok(lines.length >= 15, lines.join('\n'));
  assert.ok(lines.some((l) => /ایستِ خودکار/.test(l)), lines.join('\n'));
  assert.ok(lines.some((l) => /امضای فهرستِ سروشده/.test(l)), lines.join('\n'));
  //  دوباره روی همان سرور (پایانِ پیشین پاکش کرد) — تکرارپذیر
  assert.equal(await R.smoke({ url: h.base(), token, art: stub.art, pubkey: PUB, log: () => {} }), true);
});

test('⛔ دودآزمایی با کلیدِ عمومیِ دیگر ⇒ خطای تمرین', async () => {
  const wrong = other.publicKey.export({ format: 'der', type: 'spki' }).toString('base64');
  await assert.rejects(R.smoke({ url: h.base(), token, art: stub.art, pubkey: wrong, log: () => {} }),
    (e) => e instanceof R.RehearsalError && /امضای فهرستِ سروشده/.test(e.message));
  //  مرحلهٔ نیمه‌کارهٔ همین اجرا پاک شود
  await h.api('DELETE', '/api/admin/release/stage', { token, body: { tag: 'tohid-native' } });
});
