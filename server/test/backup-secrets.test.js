'use strict';
/**
 * F0-28 — رازهای `app_config` در پشتیبانِ سرور (`lib/backup-secrets.js`). روی هر دو راه‌انداز می‌دود:
 * PGlite (پوشهٔ داده + WAL) و PostgreSQL (pg_dump/psql؛ بی این دو، بخشِ PostgreSQL با دلیل رد می‌شود).
 *
 *   • plain (پیش‌فرض) ⇒ همان امروز: هیچ تبدیلی صدا زده نمی‌شود و SQL ِ پشتیبان همان pg_dump ِ مستقیم است.
 *   • seal ⇒ هیچ تکه‌ای از راز، پاکت یا کلید در بایت‌های پشتیبان و در لاگ نیست (نشانِ کاشته‌شده جست‌وجو می‌شود)؛
 *     با همان کلید همه برمی‌گردند و کلیدِ مجوز همان است (کدهای پیشین معتبر)؛ بی کلید بازیابی موفق، رازها مهر،
 *     ۵۰۳ ِ روشن با پیامِ صاحبِ سامانه و هیچ کلیدِ تازه‌ای (حتی در NODE_ENV=test)؛ پاکتِ دست‌خورده یا کلیدِ دیگر
 *     ⇒ رد و هیچ نوشتنی (همه یا هیچ).
 * ⛔ کلید و رازها همه همین‌جا تصادفی ساخته می‌شوند؛ هیچ کدام چاپ نمی‌شود.
 */
const test = require('node:test');
const assert = require('node:assert/strict');
const crypto = require('node:crypto');
const fs = require('node:fs/promises');
const os = require('node:os');
const path = require('node:path');
const zlib = require('node:zlib');
const { Readable } = require('node:stream');
const { execFileSync } = require('node:child_process');
const h = require('./helpers');
const config = require('../src/config');
const { query, many } = require('../src/db');
const plans = require('../src/lib/plans');
const backup = require('../src/lib/backup');
const secrets = require('../src/lib/backup-secrets');
const drill = require('../src/lib/restore-drill');
const license = require('../src/lib/license');

const onPglite = String(process.env.TEST_DATABASE_URL || '').startsWith('pglite');
const hasPgDump = (() => {
  if (onPglite) return false;
  try { execFileSync(config.backup.pgDump, ['--version'], { stdio: 'ignore' }); execFileSync(config.backup.pgRestore, ['--version'], { stdio: 'ignore' }); return true; } catch { return false; }
})();
const SKIP = !onPglite && !hasPgDump ? 'pg_dump/psql روی این ماشین نیست' : false;

const BACKUP_DIR = path.join(os.tmpdir(), `shop-secrets-test-${process.pid}`);
const KEY = crypto.randomBytes(32).toString('hex');
const OTHER_KEY = crypto.randomBytes(32).toString('base64');
const MARK = `F028-PLANTED-${crypto.randomBytes(6).toString('hex')}`;
const ENV = ['BACKUP_SECRETS_KEY', 'BACKUP_SECRETS_KEY_FILE', 'LICENSE_PRIVATE_KEY', 'LICENSE_PRIVATE_KEY_FILE', 'STATION_KEY'];
const savedEnv = Object.fromEntries(ENV.map((k) => [k, process.env[k]]));

const PEM = crypto.generateKeyPairSync('ec', {
  namedCurve: 'prime256v1', privateKeyEncoding: { type: 'pkcs8', format: 'pem' }, publicKeyEncoding: { type: 'spki', format: 'pem' },
}).privateKey;
const SPKI = crypto.createPublicKey(PEM).export({ type: 'spki', format: 'der' }).toString('base64');
const LICENSE_KID = crypto.createHash('sha256').update(Buffer.from(SPKI, 'base64')).digest('hex').slice(0, 16);
/** همهٔ رازهای کاشته‌شده؛ حسابِ سرویس بلند و پرآنتروپی (بیرون از ردیف، TOAST) */
const PLANTED = {
  license_private_key: PEM,
  vapid_private: `${MARK}-vapid`,
  email_pass: `${MARK}-smtp-pass`,
  email_key: `${MARK}-mail-api`,
  sms_key: `${MARK}-sms\\key\twith-escapes`,
  push_service_account: JSON.stringify({ client_email: 'x@example.test', project_id: 'proj-test-1', private_key: `${MARK}-${crypto.randomBytes(1900).toString('base64')}` }),
  station_read_key: `${MARK}-station-${crypto.randomBytes(24).toString('base64')}`,
};

/** هر تکه‌ای که نباید هیچ‌جا دیده شود: نشان، هر خطِ بدنهٔ PEM، هر دو کلید */
const pemLines = PEM.split('\n').filter((l) => l && !l.startsWith('-----'));
const NEEDLES = [MARK, ...pemLines, 'PRIVATE KEY', KEY, OTHER_KEY];
function noNeedle(buf, where) {
  const b = Buffer.isBuffer(buf) ? buf : Buffer.from(String(buf));
  for (const n of NEEDLES) assert.equal(b.indexOf(Buffer.from(n)), -1, `${where}: تکه‌ای از راز یا کلید پیدا شد`);
}

//  همهٔ لاگ‌های این پرونده؛ آخرین آزمون در آن‌ها راز، پاکت و کلید را جست‌وجو می‌کند
const logged = [];
const orig = { log: console.log, info: console.info, warn: console.warn, error: console.error };
for (const k of Object.keys(orig)) console[k] = (...a) => { logged.push(a.map(String).join(' ')); orig[k](...a); };

function setEnv(env) {
  for (const k of ENV) delete process.env[k];
  for (const [k, v] of Object.entries(env)) process.env[k] = v;
  license._reset();
}
async function plant() {
  await query("DELETE FROM app_config WHERE key LIKE 'sealed:%' OR key LIKE 'sealed.orphan:%'");
  for (const [k, v] of Object.entries(PLANTED)) await plans.setConfig(k, v);
  await plans.setConfig('license_public_key', SPKI);
  license._reset();
}
const secretRows = async (q = (t, p) => query(t, p)) =>
  Object.fromEntries((await q('SELECT key, value FROM app_config WHERE key = ANY($1::text[])', [secrets.SECRET_KEYS])).rows.map((r) => [r.key, r.value]));

/** اثرِ انگشتِ زنده پس از آرام شدنِ نوشتن‌های بی await (سابقه‌ها) */
async function settle() {
  const fp = () => drill.fingerprint((t, p) => query(t, p));
  let a = await fp();
  for (let i = 0; i < 40; i++) {
    await new Promise((r) => setTimeout(r, 25));
    const b = await fp();
    if (b.digest === a.digest) return;
    a = b;
  }
}
const sealedBackup = async (extra = {}) => {
  config.backup.secrets = 'seal';
  try { return await backup.run({ kind: 'manual', ...extra }); } finally { config.backup.secrets = 'plain'; }
};

test.before(async () => {
  await h.start();
  config.backup.dir = BACKUP_DIR;
  config.backup.passphrase = '';
  config.backup.secrets = 'plain';
  setEnv({});
  await plant();
});
test.after(async () => {
  for (const k of Object.keys(orig)) console[k] = orig[k];
  for (const k of ENV) { if (savedEnv[k] === undefined) delete process.env[k]; else process.env[k] = savedEnv[k]; }
  config.backup.secrets = 'plain';
  config.backup.passphrase = '';
  license._reset();
  await h.stop();
  await fs.rm(BACKUP_DIR, { recursive: true, force: true });
});

/* ───────────── پاکت ───────────── */

test('پاکت: برمی‌گردد؛ هر نویسهٔ دست‌خورده ⇒ رد؛ کلیدِ دیگر ⇒ wrong_key؛ جابه‌جا به نامِ دیگر ⇒ رد؛ قالبِ کلید', () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  const k = secrets.keyState();
  assert.equal(k.state, 'ok');
  assert.match(k.kid, /^[0-9a-f]{16}$/);
  assert.ok(!k.kid.includes(KEY.slice(0, 16)), 'kid برگشت‌ناپذیر است، نه تکهٔ کلید');
  const env = secrets.sealValue(k, 'sms_key', PLANTED.sms_key);
  assert.ok(!env.includes(MARK));
  assert.equal(secrets.openValue(k, 'sms_key', env), PLANTED.sms_key);
  assert.notEqual(secrets.sealValue(k, 'sms_key', PLANTED.sms_key), env, 'IV ِ تازه برای هر مهر');
  for (let i = 0; i < env.length; i++) {
    if (env[i] === '.') continue;
    const bad = env.slice(0, i) + (env[i] === 'A' ? 'B' : 'A') + env.slice(i + 1);
    assert.throws(() => secrets.openValue(k, 'sms_key', bad), (e) => ['tampered', 'wrong_key', 'bad_envelope'].includes(e.code), `نویسهٔ ${i}`);
  }
  assert.throws(() => secrets.openValue(k, 'email_pass', env), (e) => e.code === 'tampered', 'پاکت به نامِ خودش بسته است');
  setEnv({ BACKUP_SECRETS_KEY: OTHER_KEY });
  assert.throws(() => secrets.openValue(secrets.keyState(), 'sms_key', env), (e) => e.code === 'wrong_key');
  //  قالب‌ها: ۶۴ hex، base64 ِ ۳۲ بایت، عبارتِ ≥ ۳۲ نویسه؛ کوتاه ⇒ weak؛ فایل؛ فایلِ نبود ⇒ unreadable
  for (const [raw, state] of [[KEY, 'ok'], [OTHER_KEY, 'ok'], ['x'.repeat(32), 'ok'], ['short-passphrase', 'weak']]) {
    setEnv({ BACKUP_SECRETS_KEY: raw });
    assert.equal(secrets.keyState().state, state, raw.length);
  }
  setEnv({ BACKUP_SECRETS_KEY_FILE: path.join(os.tmpdir(), `nope-${process.pid}`) });
  assert.equal(secrets.keyState().state, 'unreadable');
  setEnv({});
  assert.equal(secrets.keyState().state, 'missing');
  assert.deepEqual(secrets.SECRET_KEYS.slice().sort(), Object.keys(PLANTED).sort(), 'هر رازِ شناخته‌شدهٔ app_config مهر می‌شود');
});

/* ───────────── تبدیلِ جریانِ pg_dump ───────────── */

test('sealStream: فقط ردیف‌های رازِ بلوکِ app_config عوض می‌شوند؛ هر بایتِ دیگر همان؛ با هر برشِ جریان', async () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  const k = secrets.keyState();
  const esc = (s) => s.replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/\t/g, '\\t').replace(/\r/g, '\\r');
  const before = '--\n-- PostgreSQL database dump\n--\n\nCOPY public.aaa (id, data) FROM stdin;\n1\tlicense_private_key\n\\.\n\n\n';
  const block = [
    'brand_name\tدکانِ من\t1',
    `license_private_key\t${esc(PEM)}\t2`,
    'email_pass\t\t3',
    `sms_key\t${esc(PLANTED.sms_key)}\t4`,
    'sealed:sms_key\tsbk1.0000000000000000.AAAAAAAAAAAAAAAA.AA.AAAAAAAAAAAAAAAAAAAAAA\t5',
    'station_read_key\t\t6',
    'vapid_public\tpub\t7',
  ];
  const after = '\\.\n\n\nCOPY public.zzz (id) FROM stdin;\nsms_key\n\\.\n\n-- PostgreSQL database dump complete\n';
  const text = `${before}COPY public.app_config (key, value, updated_at) FROM stdin;\n${block.join('\n')}\n${after}`;
  const src = Buffer.from(text);
  for (const size of [1, 2, 3, 7, 25, 64, 4096, src.length]) {
    const t = secrets.sealStream(k);
    const parts = [];
    for (let i = 0; i < src.length; i += size) parts.push(src.subarray(i, i + size));
    const out = [];
    for await (const c of Readable.from(parts).pipe(t)) out.push(c);
    const got = Buffer.concat(out).toString('utf8');
    assert.ok(got.startsWith(`${before}COPY public.app_config (key, value, updated_at) FROM stdin;\n`), `پیش از بلوک (${size})`);
    assert.ok(got.endsWith(after), `پس از بلوک (${size})`);
    const rows = got.slice(got.indexOf('FROM stdin;\n', before.length) + 12, got.length - after.length).split('\n').filter(Boolean);
    assert.equal(rows.length, block.length, 'شمارِ ردیف همان');
    const by = Object.fromEntries(rows.map((r) => { const f = r.split('\t'); return [f[0], f]; }));
    assert.deepEqual(by.brand_name, block[0].split('\t'), 'ردیفِ بی‌راز بایت‌به‌بایت همان');
    assert.deepEqual(by.email_pass, ['email_pass', '', '3'], 'رازِ خالی دست نمی‌خورد');
    assert.deepEqual(by.station_read_key, ['station_read_key', '', '6']);
    assert.equal(by.vapid_public[1], 'pub', 'کلیدِ عمومی راز نیست');
    assert.ok(!by.license_private_key && !by.sms_key, 'نامِ اصلیِ راز در پشتیبان نیست');
    assert.equal(secrets.openValue(k, 'license_private_key', by['sealed:license_private_key'][1]), PEM);
    assert.equal(by['sealed:license_private_key'][2], '2', 'updated_at همان');
    assert.equal(secrets.openValue(k, 'sms_key', by['sealed:sms_key'][1]), PLANTED.sms_key, 'گریزهای COPY درست باز شد');
    const orphan = Object.keys(by).find((x) => x.startsWith('sealed.orphan:sms_key:'));
    assert.ok(orphan, 'پاکتِ کهنهٔ هم‌نام یتیم می‌شود، نه دو کلیدِ یکسان');
    assert.equal(by[orphan][1], block[4].split('\t')[1]);
    assert.ok(!got.includes(MARK) && !pemLines.some((l) => got.includes(l)), `هیچ تکه‌ای از راز (${size})`);
    assert.equal(t.report.found, true);
    assert.equal(t.report.sealed, 2);
    assert.equal(t.report.orphaned, 1);
    assert.deepEqual(t.report.keys, ['license_private_key', 'sms_key']);
    assert.equal(t.report.licenseKid, LICENSE_KID);
  }
  const none = secrets.sealStream(k);
  const out = [];
  for await (const c of Readable.from([Buffer.from(before)]).pipe(none)) out.push(c);
  assert.equal(Buffer.concat(out).toString(), before, 'بی بلوک ⇒ همان بایت‌ها');
  assert.equal(none.report.found, false);
});

/* ───────────── plain = همان امروز ───────────── */

test('plain (پیش‌فرض): هیچ تبدیلی صدا زده نمی‌شود؛ رازها مثلِ امروز در پشتیبان؛ meta بی secrets؛ SQL همان pg_dump ِ مستقیم', { skip: SKIP }, async () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });   // کلید هست ولی حالت plain است ⇒ هیچ مهری
  const calls = [];
  const saved = { sealStream: secrets.sealStream, sealPglite: secrets.sealPglite };
  secrets.sealStream = (...a) => { calls.push('stream'); return saved.sealStream(...a); };
  secrets.sealPglite = (...a) => { calls.push('pglite'); return saved.sealPglite(...a); };
  const savedPass = config.backup.passphrase;
  config.backup.passphrase = 'plain-mode-passphrase-for-this-test';   // بی سابقهٔ «بی رمز» ⇒ دیتابیس آرام می‌ماند
  try {
    await settle();
    const out = await backup.run({ kind: 'manual' });
    assert.deepEqual(calls, [], 'plain هیچ تبدیلی ندارد');
    const meta = JSON.parse(await fs.readFile(`${out.file}.meta.json`, 'utf8'));
    assert.equal(meta.secrets, undefined, 'meta ِ plain همان کلیدهای امروز');
    assert.equal(out.secrets, undefined);
    const raw = await backup.decode(out.file);
    assert.ok(raw.includes(Buffer.from(MARK)), 'plain: راز مثلِ امروز در پشتیبان است (دقیقاً همان خطرِ F0-28)');
    if (!onPglite) {
      //  همان pg_dump با همان گزینه‌ها؛ فقط سطرِ تصادفیِ `\restrict` ِ pg_dump ِ تازه کنار می‌رود
      const direct = execFileSync(config.backup.pgDump, [config.db.url, '--no-owner', '--no-privileges', '--format=plain', '--encoding=UTF8'], { maxBuffer: 1 << 30 });
      const norm = (b) => b.toString('utf8').split('\n').filter((l) => !/^\\(un)?restrict /.test(l)).join('\n');
      assert.equal(norm(raw), norm(direct), 'plain: بایت‌به‌بایت همان pg_dump');
    }
  } finally {
    Object.assign(secrets, saved);
    config.backup.passphrase = savedPass;
  }
});

/* ───────────── seal ───────────── */

let SEALED_FILE = '';

test('seal: در بایت‌های پشتیبان هیچ راز، تکهٔ PEM یا کلیدی نیست (PGlite: WAL هم)؛ meta فقط نام و kid', { skip: SKIP }, async () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  await plant();
  const out = await sealedBackup();
  SEALED_FILE = out.file;
  noNeedle(await fs.readFile(out.file), 'فایل');
  const raw = await backup.decode(out.file);
  noNeedle(raw, onPglite ? 'tar ِ پوشهٔ داده (heap، TOAST، WAL)' : 'SQL');
  assert.ok(raw.includes(Buffer.from('sealed:license_private_key')));
  assert.equal(raw.indexOf(Buffer.from('sbk1.')) >= 0, true, 'پاکت‌ها در پشتیبان‌اند');
  const meta = JSON.parse(await fs.readFile(`${out.file}.meta.json`, 'utf8'));
  noNeedle(JSON.stringify(meta), 'meta');
  assert.equal(meta.secrets.mode, 'seal');
  assert.equal(meta.secrets.sealed, secrets.SECRET_KEYS.length);
  assert.deepEqual(meta.secrets.keys, secrets.SECRET_KEYS.slice().sort());
  assert.equal(meta.secrets.kid, secrets.keyState().kid);
  assert.equal(meta.secrets.licenseKid, LICENSE_KID);
  assert.equal(out.secrets.sealed, secrets.SECRET_KEYS.length);
  assert.deepEqual(await secretRows(), PLANTED, 'دیتابیسِ زنده دست نخورد');
});

test('seal ⇒ تمرین با همان کلید: داده برابر، همهٔ رازها برگشت، همان کلیدِ مجوز (امضای پیشین سنجیده می‌شود)', { skip: SKIP }, async () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  await plant();
  const lic = await license.issue({ deviceUid: 'F028-DEV', accountId: 'u-f028', subscriptionEndsAt: Date.now() + 86400000 });
  config.backup.passphrase = 'seal-and-passphrase-together';
  let out;
  try {
    await settle();
    out = await sealedBackup();
    assert.equal(out.encrypted, true);
    const r = await drill.drill({ file: out.file });
    assert.equal(r.ok, true, JSON.stringify(r.restore?.diffs?.slice(0, 5)));
    assert.equal(r.secrets.unsealed, secrets.SECRET_KEYS.length);
    assert.equal(r.secrets.pending, secrets.SECRET_KEYS.length);
    assert.equal(r.secrets.licenseKey, 'same');
    assert.equal(r.secrets.ok, true);
    noNeedle(JSON.stringify(r), 'خروجیِ تمرین');
    //  همان مقدارها، و همان کلید: امضای مجوزِ پیش از پشتیبان با کلیدِ برگشته سنجیده می‌شود
    const iso = await drill.restoreIsolated(out.file);
    try {
      const u = await secrets.unseal(secrets.poolConn(iso.pool));
      assert.equal(u.unsealed, secrets.SECRET_KEYS.length);
      assert.deepEqual(await secretRows(iso.q), PLANTED);
      assert.equal((await iso.q("SELECT count(*)::int AS n FROM app_config WHERE key LIKE 'sealed:%'")).rows[0].n, 0);
      const [hd, pl, sg] = lic.token.split('.');
      const pub = crypto.createPublicKey((await secretRows(iso.q)).license_private_key);
      assert.equal(crypto.verify('sha256', Buffer.from(`${hd}.${pl}`), { key: pub, dsaEncoding: 'ieee-p1363' }, Buffer.from(sg, 'base64url')), true);
    } finally { await iso.close(); }
  } finally { config.backup.passphrase = ''; }
});

test('seal ⇒ تمرین بی کلید: بازیابی موفق، رازها مهر و بی‌استفاده، پیامِ روشن، هیچ کلیدی ساخته نشد', { skip: SKIP }, async () => {
  setEnv({});
  const r = await drill.drill({ file: SEALED_FILE });
  assert.equal(r.ok, true, 'داده کامل برگشت');
  assert.equal(r.secrets.missingKey, true);
  assert.equal(r.secrets.unsealed, 0);
  assert.equal(r.secrets.pending, secrets.SECRET_KEYS.length);
  assert.equal(r.secrets.licenseKey, 'sealed');
  assert.equal(r.secrets.ok, false);
  assert.match(r.secrets.message, /BACKUP_SECRETS_KEY/);
  assert.ok(r.secrets.message.includes(secrets.keyStateFor(KEY).kid), 'kid ِ کلیدِ لازم برای پیدا کردنش');
  noNeedle(JSON.stringify(r), 'خروجیِ تمرین');
  const iso = await drill.restoreIsolated(SEALED_FILE);
  try {
    assert.deepEqual(await secretRows(iso.q), {}, 'هیچ رازی بی کلید نشست');
    assert.equal((await iso.q("SELECT count(*)::int AS n FROM app_config WHERE key LIKE 'sealed:%'")).rows[0].n, secrets.SECRET_KEYS.length);
  } finally { await iso.close(); }
});

test('دست‌کاری و کلیدِ دیگر: پاکتِ عوض‌شده یا کلیدِ دیگر ⇒ رد و هیچ رازی نوشته نمی‌شود (همه یا هیچ)', { skip: SKIP }, async () => {
  const iso = await drill.restoreIsolated(SEALED_FILE);
  const sealedNow = async () => (await iso.q("SELECT key, value FROM app_config WHERE key LIKE 'sealed:%' ORDER BY key")).rows;
  try {
    const original = await sealedNow();
    setEnv({ BACKUP_SECRETS_KEY: OTHER_KEY });
    const wrong = await secrets.unseal(secrets.poolConn(iso.pool));
    assert.equal(wrong.code, 'wrong_key');
    assert.equal(wrong.unsealed, 0);
    assert.deepEqual(await secretRows(iso.q), {});
    assert.deepEqual(await sealedNow(), original);
    setEnv({ BACKUP_SECRETS_KEY: KEY });
    const v = original.find((x) => x.key === 'sealed:email_pass').value;
    const i = v.length - 30;
    await iso.q("UPDATE app_config SET value = $1 WHERE key = 'sealed:email_pass'", [v.slice(0, i) + (v[i] === 'A' ? 'B' : 'A') + v.slice(i + 1)]);
    const bad = await secrets.unseal(secrets.poolConn(iso.pool));
    assert.equal(bad.code, 'tampered');
    assert.deepEqual(bad.rejected.map((x) => x.name), ['email_pass']);
    assert.deepEqual(await secretRows(iso.q), {}, 'شش پاکتِ سالم هم نوشته نشدند');
    noNeedle(JSON.stringify([wrong, bad]), 'خروجیِ رد');
  } finally { await iso.close(); }
  if (onPglite) return;
  //  PostgreSQL: خودِ فایلِ پشتیبان دست‌کاری شد ⇒ restore داده را می‌نشاند ولی هیچ رازی نه
  const sql = zlib.gunzipSync(await fs.readFile(SEALED_FILE)).toString('utf8');
  const at = sql.indexOf('sealed:sms_key\tsbk1.') + 'sealed:sms_key\tsbk1.'.length + 40;
  const broken = `${SEALED_FILE}.tampered.sql.gz`;
  await fs.writeFile(broken, zlib.gzipSync(sql.slice(0, at) + (sql[at] === 'A' ? 'B' : 'A') + sql.slice(at + 1)));
  const target = await drill.restoreIsolated(broken, { keep: false });
  try {
    //  restoreIsolated خودش باز نمی‌کند؛ همان راهِ `backup.restore`
    const r = await secrets.unseal(secrets.poolConn(target.pool));
    assert.equal(r.code, 'tampered');
    assert.deepEqual(await secretRows(target.q), {});
  } finally { await target.close(); }
});

test('PostgreSQL، همان راهِ scripts/restore.js (`backup.restore`): با کلید همه باز؛ بی کلید داده نشست و رازها مهر؛ فایلِ دست‌خورده ⇒ هیچ راز', { skip: onPglite ? 'restore ِ psql فقط PostgreSQL' : SKIP }, async () => {
  const { Client } = require('pg');
  const name = `bsec_restore_${process.pid}`;
  const admin = config.db.url.replace(/\/[^/?]+(\?|$)/, '/postgres$1');
  const url = config.db.url.replace(/\/[^/?]+(\?|$)/, `/${name}$1`);
  const sql = async (u, text, params) => { const c = new Client({ connectionString: u }); await c.connect(); try { return await c.query(text, params); } finally { await c.end(); } };
  const fresh = async () => { await sql(admin, `DROP DATABASE IF EXISTS ${name}`); await sql(admin, `CREATE DATABASE ${name}`); };
  const rowsAt = () => secretRows((t, p) => sql(url, t, p));
  const sealedAt = async () => (await sql(url, "SELECT count(*)::int AS n FROM app_config WHERE key LIKE 'sealed:%'")).rows[0].n;
  try {
    setEnv({ BACKUP_SECRETS_KEY: KEY });
    await fresh();
    const ok = await backup.restore(SEALED_FILE, { databaseUrl: url });
    assert.equal(ok.secrets.unsealed, secrets.SECRET_KEYS.length);
    assert.deepEqual(await rowsAt(), PLANTED);
    assert.equal(await sealedAt(), 0);
    noNeedle(JSON.stringify(ok), 'خروجیِ restore');

    setEnv({});
    await fresh();
    const none = await backup.restore(SEALED_FILE, { databaseUrl: url });
    assert.equal(none.ok, true, 'بی کلید بازیابی موفق است');
    assert.equal(none.secrets.missingKey, true);
    assert.match(none.secrets.message, /BACKUP_SECRETS_KEY/);
    assert.deepEqual(await rowsAt(), {});
    assert.equal(await sealedAt(), secrets.SECRET_KEYS.length);

    setEnv({ BACKUP_SECRETS_KEY: KEY });
    const text = zlib.gunzipSync(await fs.readFile(SEALED_FILE)).toString('utf8');
    const at = text.indexOf('sealed:license_private_key\tsbk1.') + 'sealed:license_private_key\tsbk1.'.length + 30;
    const broken = `${SEALED_FILE}.restore-tampered.sql.gz`;
    await fs.writeFile(broken, zlib.gzipSync(text.slice(0, at) + (text[at] === 'A' ? 'B' : 'A') + text.slice(at + 1)));
    await fresh();
    const bad = await backup.restore(broken, { databaseUrl: url });
    assert.equal(bad.ok, true, 'داده نشست');
    assert.equal(bad.secrets.code, 'tampered');
    assert.deepEqual(bad.secrets.rejected.map((x) => x.name), ['license_private_key']);
    assert.deepEqual(await rowsAt(), {}, 'هیچ رازی، حتی شش پاکتِ سالم');
    assert.equal(await sealedAt(), secrets.SECRET_KEYS.length);
    noNeedle(JSON.stringify(bad), 'خروجیِ restore');
  } finally {
    await sql(admin, `DROP DATABASE IF EXISTS ${name}`).catch(() => {});
  }
});

test('سرورِ تازه از پشتیبانِ مهرشده بی کلید: مجوز ۵۰۳ ِ روشن، هیچ کلیدِ تازه (حتی test)، کلیدِ پمپ ساخته نمی‌شود؛ با کلید همه برمی‌گردند و کدهای پیشین معتبرند', { skip: SKIP }, async () => {
  const stations = require('../src/lib/stations');
  const shopOffline = require('../src/lib/shop-offline-codes');
  const pumpOffline = require('../src/lib/offline-codes');
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  await plant();
  //  پیش از پشتیبان: مجوز، کدِ آفلاینِ فروشگاه و رمزِ خواندنِ یک پمپ با کلیدهای امروز
  const lic = await license.issue({ deviceUid: 'F028-DEV-2', accountId: 'u-f028', subscriptionEndsAt: Date.now() + 86400000 });
  const pc = pumpOffline.parseComputer(pumpOffline.computerOfFingerprint(`win-${'f'.repeat(32)}`));
  const code = await shopOffline.encode(shopOffline.body({ plan: 'sub', machine: pc.machine, account: Buffer.alloc(4),
    serial: crypto.randomBytes(4), issuedDay: 1000, endDay: 5000 }), license.signBytes);
  stations._forgetKey();
  const pumpCipher = await stations.encryptKey('pump-read-pass');
  stations._forgetKey();
  const out = await sealedBackup();
  //  «سرورِ تازه» = همین دیتابیس با ردیف‌هایی که از این پشتیبان بی کلید برمی‌گردند
  setEnv({});
  const iso = await drill.restoreIsolated(out.file);
  let restored;
  try { restored = (await iso.q("SELECT key, value, updated_at FROM app_config WHERE key LIKE 'sealed:%'")).rows; } finally { await iso.close(); }
  await query('DELETE FROM app_config WHERE key = ANY($1::text[])', [secrets.SECRET_KEYS]);
  for (const r of restored) await query('INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)', [r.key, r.value, String(r.updated_at)]);
  license._reset();
  stations._forgetKey();

  const lines = [];
  const log = { log: (...a) => lines.push(['log', a.join(' ')]), warn: (...a) => lines.push(['warn', a.join(' ')]), error: (...a) => lines.push(['error', a.join(' ')]) };
  const s = await secrets.startup(log);
  assert.equal(s.pending, secrets.SECRET_KEYS.length);
  assert.equal(s.missingKey, true);
  const err = lines.filter(([k]) => k === 'error').map(([, t]) => t).join('\n');
  assert.match(err, /BACKUP_SECRETS_KEY/, 'پیامِ بلند: دقیقاً چه بگذارد');
  assert.ok(err.includes(secrets.keyStateFor(KEY).kid));
  for (const n of secrets.SECRET_KEYS) assert.ok(err.includes(n), n);
  noNeedle(lines.map(([, t]) => t).join('\n'), 'لاگِ راه‌اندازی');
  //  مجوز: ۵۰۳ با پیامِ صاحبِ سامانه، نه خرابی؛ هیچ کلیدی ساخته نشد حتی در NODE_ENV=test
  assert.equal(process.env.NODE_ENV, 'test');
  await assert.rejects(license.signBytes(Buffer.from('x')), (e) => e.status === 503 && e.code === 'license_key_missing' && /BACKUP_SECRETS_KEY/.test(e.message));
  const u = await h.newUser('F028 بی کلید');
  await h.post('/api/shop', { name: 'دکانِ F028' }, { token: u.accessToken });
  const sync = await h.post('/api/license/sync', { device: { uid: 'DEV-F028' } }, { token: u.accessToken });
  assert.equal(sync.status, 503);
  assert.match(sync.body.error.message, /BACKUP_SECRETS_KEY/);
  assert.deepEqual(await secretRows(), { station_read_key: '' }, 'هیچ کلیدِ تازه؛ فقط ردیفِ خالیِ جلوگیر برای کلیدِ پمپ');
  const ls = [];
  const lc = await license.startupCheck({ log: (...a) => ls.push(a.join(' ')), warn: (...a) => ls.push(a.join(' ')), error: (...a) => ls.push(a.join(' ')) });
  assert.equal(lc.ok, false);
  assert.match(ls.join('\n'), /BACKUP_SECRETS_KEY/);
  assert.equal((await h.get('/api/health')).status, 200, 'بقیهٔ سرور سرِ کار');
  //  سنجش با کلیدِ عمومیِ مانده کار می‌کند: کدها و مجوزِ پیشین هنوز معتبرند
  assert.equal((await shopOffline.verify(code))?.plan, 'sub');
  const [hd, pl, sg] = lic.token.split('.');
  assert.equal(await license.verifyBytes(Buffer.from(`${hd}.${pl}`), Buffer.from(sg, 'base64url')), true);
  //  کلیدِ پمپ: کدِ پمپ دست نخورده؛ ردیفِ خالی نمی‌گذارد کلیدِ تازه‌ای بسازد
  await assert.rejects(stations.encryptKey('new-pass'));
  assert.equal(await stations.decryptKey(pumpCipher), '');
  assert.deepEqual(await secretRows(), { station_read_key: '' });
  //  LICENSE_PRIVATE_KEY در محیط: امضا و سنجش همان دم کار می‌کنند، با همان kid
  setEnv({ LICENSE_PRIVATE_KEY: PEM });
  assert.equal(await license.keyId(), LICENSE_KID);
  assert.equal(await license.verifyBytes(Buffer.from('y'), await license.signBytes(Buffer.from('y'))), true);
  assert.deepEqual(await secretRows(), { station_read_key: '' });

  //  کلید رسید ⇒ راه‌اندازیِ بعدی همه را برمی‌گرداند
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  stations._forgetKey();
  lines.length = 0;
  const s2 = await secrets.startup(log);
  assert.equal(s2.unsealed, secrets.SECRET_KEYS.length);
  assert.deepEqual(await secretRows(), PLANTED, 'همهٔ رازها همان مقدار');
  assert.equal((await many("SELECT key FROM app_config WHERE key LIKE 'sealed:%'")).length, 0);
  noNeedle(lines.map(([, t]) => t).join('\n'), 'لاگِ باز شدن');
  license._reset();
  assert.equal(await license.keyId(), LICENSE_KID, 'همان کلیدِ مجوز');
  assert.equal(await license.verifyBytes(Buffer.from(`${hd}.${pl}`), Buffer.from(sg, 'base64url')), true);
  assert.equal((await shopOffline.verify(code))?.plan, 'sub', 'کدِ آفلاینِ پیشین معتبر');
  assert.equal(await stations.decryptKey(pumpCipher), 'pump-read-pass', 'رمزِ خواندنِ پمپ همان');
  stations._forgetKey();
});

test('مقدارِ تازه‌ای که در این میان نشست روی‌نویسی نمی‌شود؛ پاکتِ کهنه sealed.orphan می‌ماند و پشتیبانِ بعدی سالم است', { skip: SKIP }, async () => {
  setEnv({ BACKUP_SECRETS_KEY: KEY });
  await plant();
  const k = secrets.keyState();
  await query("INSERT INTO app_config (key, value, updated_at) VALUES ('sealed:sms_key', $1, 1)", [secrets.sealValue(k, 'sms_key', 'old-sms-value')]);
  await plans.setConfig('sms_key', 'new-sms-value');
  //  پشتیبانِ مهری در همین حال: دو «sealed:sms_key» نمی‌سازد
  const out = await sealedBackup();
  const iso = await drill.restoreIsolated(out.file);
  try {
    const u = await secrets.unseal(secrets.poolConn(iso.pool));
    assert.equal(u.code, undefined);
    assert.equal((await secretRows(iso.q)).sms_key, 'new-sms-value');
  } finally { await iso.close(); }
  const r = await secrets.unseal(secrets.liveConn());
  assert.deepEqual(r.conflicts, ['sms_key']);
  assert.equal((await secretRows()).sms_key, 'new-sms-value', 'مقدارِ تازه ماند');
  const orphan = await many("SELECT key FROM app_config WHERE key LIKE 'sealed.orphan:sms_key:%'");
  assert.equal(orphan.length >= 1, true);
  await plant();
});

test('seal بی کلید یا حالتِ ناشناخته ⇒ هیچ فایلی ساخته نمی‌شود و پشتیبان‌های قبلی دست نمی‌خورند', { skip: SKIP }, async () => {
  const dir = path.join(BACKUP_DIR, 'manual');
  const files = async () => (await fs.readdir(dir).catch(() => [])).sort();
  const before = await files();
  setEnv({});
  config.backup.secrets = 'seal';
  try {
    await assert.rejects(backup.run({ kind: 'manual' }), (e) => e.status === 409 && e.code === 'backup_secrets_key_missing' && /BACKUP_SECRETS_KEY/.test(e.message));
    setEnv({ BACKUP_SECRETS_KEY: 'too-short' });
    await assert.rejects(backup.run({ kind: 'manual' }), (e) => e.code === 'backup_secrets_key_missing');
    config.backup.secrets = 'omit';
    await assert.rejects(backup.run({ kind: 'manual' }), (e) => e.status === 409 && e.code === 'backup_secrets_mode_invalid');
  } finally { config.backup.secrets = 'plain'; }
  assert.deepEqual(await files(), before);
});

test('هیچ راز، پاکت یا کلیدی در لاگِ این پرونده و در سابقه نیست', async () => {
  const text = logged.join('\n');
  noNeedle(text, 'لاگ');
  assert.ok(!/sbk1\.[0-9a-f]{16}\./.test(text), 'هیچ پاکتی در لاگ');
  const audits = await many("SELECT action, detail FROM audit_logs WHERE action LIKE 'backup.%'");
  noNeedle(JSON.stringify(audits), 'سابقه');
  assert.ok(!/sbk1\.[0-9a-f]{16}\./.test(JSON.stringify(audits)));
});
