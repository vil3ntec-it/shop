#!/usr/bin/env node
'use strict';
/**
 * آزمونِ انتشارِ برنامه روی کپیِ موقت — OPS-03@29 («release و migration در staging آزمایش شود»، §۲۹.۸؛ O-13 = ب:
 * سرورِ آزمایشیِ همیشگی نه، پیش از هر انتشار کپیِ موقت) و OPS-01 (انتشارِ مرحله‌ای؛ تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳).
 *
 * ورک‌فلوی «انتشارِ مرحله‌ای» (`.github/workflows/release-stage.yml`) این را **پیش از گامِ انتشار** می‌دواند: سرورِ همان
 * کامیتی که نصبی از آن ساخته شده، روی پایگاهِ موقتی که «تمرینِ ارتقا» (`upgrade-rehearsal.sh`) از نسخهٔ امروزِ سرور
 * مهاجرت داده، بالا می‌آید؛ GitHub ِ ساختگیِ محلی **همان فایل‌های واقعیِ انتشار** را زیرِ برچسبِ نسخه‌دار می‌دهد؛ و
 * راهِ کاملِ برنامه سنجیده می‌شود:
 *
 *   ۱) فایل‌ها: `version.txt` = نسخه؛ هر نصبیِ به‌روزرسانی در `SHA256SUMS.txt` با همان هش؛ امضای فهرست با کلیدِ
 *      عمومیِ سنجاق‌شدهٔ برنامه (اگر داده شد؛ همان `SHA256withECDSA` ِ `ReleaseSignature.kt`).
 *   ۲) پنل (درِ واقعیِ مدیرِ ارشد): مرحله با «فقط یک دکان» ⇒ همان دکان نسخهٔ تازه را از برچسبِ نسخه‌دار می‌گیرد،
 *      دکانِ دیگر و برنامهٔ قدیمی برچسبِ روان؛ دانلودِ هر نصبی از سرور = همان بایت‌ها و همان هشِ فهرست؛ فهرست و
 *      امضای سروشده = همان فایل‌ها.
 *   ۳) ۱۰۰٪ ⇒ دستگاهِ دلخواه هم.
 *   ۴) ایستِ خودکار: نگهبان روشن، دو دستگاهِ نسخهٔ تازه از درِ عمومیِ `POST /api/errors` خطا می‌فرستند ⇒ «بسنج» ⇒
 *      ایستاده و همه برچسبِ روان؛ دانلودِ نیمه‌کاره هنوز سرو می‌شود؛ PUT ِ مدیر ادامه می‌دهد.
 *   ۵) پایان (`DELETE`) ⇒ همه روان و برچسبِ نسخه‌دار دیگر سرو نمی‌شود.
 *
 *   DATABASE_URL=postgres://…/shop_staging node scripts/release-rehearsal.js \
 *     --artifacts <پوشه> --tag tohid-native|tohid-desktop --version 3.2.2100 [--pubkey <SPKI-base64>] [--port 4300]
 *
 * ⛔ هیچ دادهٔ واقعی و هیچ شبکهٔ بیرونی (GitHub ِ ساختگی روی 127.0.0.1). پایگاهی که نامش test/bench/staging ندارد رد
 *    می‌شود (این ابزار مدیر و پیکربندی می‌نویسد). کدِ خروج: ۰ سالم · ۲ خطای تمرین · ۱ خطای اجرا.
 * آزمون: `test/release-rehearsal.test.js`.
 */
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const http = require('node:http');
const crypto = require('node:crypto');
const { spawn, spawnSync } = require('node:child_process');

const SERVER = path.resolve(__dirname, '..');
const REPO = 'rehearsal/shop';
const SHOP = 'rehearsal-shop-1';
const OTHER_SHOP = 'rehearsal-shop-2';
const DESKTOP_UPDATERS = Object.freeze(['Tohid-Windows-Offline.exe', 'Tohid-Mac-AppleSilicon.dmg', 'Tohid-Mac-Intel.dmg', 'Tohid-Linux.deb']);
const TAGS = Object.freeze(['tohid-native', 'tohid-desktop']);
const VERSION = /^\d{1,6}(\.\d{1,6}){1,3}$/;
//  برچسبِ روانِ ساختگی: همیشه پایین‌تر از هر نسخهٔ واقعی
const ROLLING_VERSION = '0.0.1';

class RehearsalError extends Error {}
const fail = (msg) => { throw new RehearsalError(msg); };
const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');

/** نصبی‌هایی که برنامه برای به‌روزرسانی برمی‌دارد (همان `ReleaseFeed.androidInstaller` و `UpdaterDesk.assetName`) */
function updaterFiles(tag, names) {
  if (tag === 'tohid-native') return names.filter((n) => /^Tohid-Native-\d+(\.\d+)+\.apk(\.beta)?$/.test(n));
  return DESKTOP_UPDATERS.filter((n) => names.includes(n));
}

/** «<هش>  <نام>» ⇒ Map(نام ⇒ هش) */
function parseSums(text) {
  const out = new Map();
  for (const line of String(text).split(/\r?\n/)) {
    const m = /^([0-9a-f]{64})\s+\*?(\S.*)$/i.exec(line.trim());
    if (m) out.set(m[2].trim(), m[1].toLowerCase());
  }
  return out;
}

/** امضای ECDSA ِ فهرست (base64) با کلیدِ عمومیِ SPKI (base64 یا PEM) — همان `ReleaseSignature.verify` */
function verifySignature(sumsBuf, sigText, pubkey) {
  const raw = String(pubkey || '').trim();
  const key = raw.includes('BEGIN')
    ? crypto.createPublicKey(raw)
    : crypto.createPublicKey({ key: Buffer.from(raw.replace(/\s+/g, ''), 'base64'), format: 'der', type: 'spki' });
  return crypto.verify('sha256', sumsBuf, key, Buffer.from(String(sigText).trim(), 'base64'));
}

/**
 * پوشهٔ فایل‌های انتشار ⇒ { version, files: [{name, path, size, sha}], sums, sig, updaters }. هر ناسازگاری ⇒ خطای تمرین.
 * `requireAll`: برای کامپیوتر هر چهار نصبی لازم است (ورک‌فلو)؛ آزمون می‌تواند یکی بدهد.
 */
function loadArtifacts(dir, { tag, version, pubkey = '', requireAll = true }) {
  if (!TAGS.includes(tag)) fail(`برچسب یکی از ${TAGS.join('، ')}`);
  if (!VERSION.test(String(version || ''))) fail(`نسخهٔ نادرست: «${version}»`);
  const names = fs.readdirSync(dir).filter((n) => fs.statSync(path.join(dir, n)).isFile()).sort();
  const read = (n) => (names.includes(n) ? fs.readFileSync(path.join(dir, n)) : null);
  const vtxt = read('version.txt');
  if (!vtxt) fail('version.txt نیست');
  const v = vtxt.toString('utf8').trim().split(/\r?\n/)[0].trim();
  if (v !== version) fail(`version.txt (${v}) با نسخهٔ خواسته‌شده (${version}) نمی‌خواند`);
  const sumsBuf = read('SHA256SUMS.txt');
  if (!sumsBuf) fail('SHA256SUMS.txt نیست');
  const sums = parseSums(sumsBuf);
  const updaters = updaterFiles(tag, names);
  if (!updaters.length) fail('هیچ نصبیِ به‌روزرسانی در فایل‌ها نیست');
  if (tag === 'tohid-native' && updaters.length !== 1) fail(`یک نصبیِ اندروید لازم است، ${updaters.length} آمد`);
  if (tag === 'tohid-native' && !updaters[0].includes(`-${version}.apk`)) fail(`نامِ نصبی (${updaters[0]}) نسخهٔ ${version} را ندارد`);
  if (tag === 'tohid-desktop' && requireAll && updaters.length !== DESKTOP_UPDATERS.length) {
    fail(`نصبی‌های کامپیوتر ناقص‌اند: ${DESKTOP_UPDATERS.filter((n) => !updaters.includes(n)).join('، ')}`);
  }
  const files = [];
  for (const n of names) {
    const buf = fs.readFileSync(path.join(dir, n));
    files.push({ name: n, path: path.join(dir, n), size: buf.length, sha: sha256(buf) });
  }
  for (const n of updaters) {
    const f = files.find((x) => x.name === n);
    if (!sums.has(n)) fail(`${n} در SHA256SUMS.txt نیست`);
    if (sums.get(n) !== f.sha) fail(`هشِ ${n} با SHA256SUMS.txt نمی‌خواند`);
  }
  const sigBuf = read('SHA256SUMS.txt.sig');
  if (pubkey) {
    if (!sigBuf) fail('کلیدِ عمومیِ به‌روزرسانی داده شد ولی SHA256SUMS.txt.sig نیست — برنامه این انتشار را نمی‌پذیرد');
    let ok = false;
    try { ok = verifySignature(sumsBuf, sigBuf.toString('utf8'), pubkey); } catch (e) { fail(`کلیدِ عمومی یا امضا خوانده نشد: ${e.message}`); }
    if (!ok) fail('امضای SHA256SUMS.txt با کلیدِ عمومیِ برنامه نمی‌خواند');
  }
  return { tag, version, files, sums, sumsBuf, sig: sigBuf, updaters };
}

/** GitHub ِ ساختگی: برچسبِ روان (۰.۰.۱ ِ ساختگی) و برچسبِ نسخه‌دار با فایل‌های واقعی */
function stubGitHub(art) {
  const versioned = `${art.tag}-v${art.version}`;
  const rollingInstaller = art.tag === 'tohid-native' ? `Tohid-Native-${ROLLING_VERSION}.apk` : art.updaters[0];
  const assets = new Map();
  let id = 100;
  const add = (tag, name, bytes, file = '') => { const a = { id: ++id, tag, name, size: bytes, file }; assets.set(String(a.id), a); return a; };
  const rolling = [add(art.tag, rollingInstaller, 0), add(art.tag, 'version.txt', 0)];
  const fake = { [rollingInstaller]: Buffer.from('rehearsal-rolling-installer'), 'version.txt': Buffer.from(`${ROLLING_VERSION}\n`) };
  for (const a of rolling) a.size = fake[a.name].length;
  const staged = art.files.map((f) => add(versioned, f.name, f.size, f.path));
  const shape = (a) => ({ id: a.id, name: a.name, size: a.size, updated_at: '2026-11-13T00:00:00Z' });
  const server = http.createServer((req, res) => {
    const u = decodeURIComponent(String(req.url).split('?')[0]);
    const json = (body) => { res.setHeader('Content-Type', 'application/json'); res.end(JSON.stringify(body)); };
    if (u === `/repos/${REPO}/releases/tags/${art.tag}`) return json({ body: 'روانِ ساختگی', published_at: '2026-11-01T00:00:00Z', assets: rolling.map(shape) });
    if (u === `/repos/${REPO}/releases/tags/${versioned}`) return json({ body: 'مرحله‌ای', published_at: '2026-11-13T00:00:00Z', assets: staged.map(shape) });
    const m = /^\/repos\/[^/]+\/[^/]+\/releases\/assets\/(\d+)$/.exec(u);
    const a = m && assets.get(m[1]);
    if (a) {
      res.setHeader('Content-Type', 'application/octet-stream');
      if (a.file) return fs.createReadStream(a.file).pipe(res);
      return res.end(fake[a.name]);
    }
    res.statusCode = 404;
    res.end('{}');
  });
  return { server, versioned, rollingInstaller };
}

/** درخواستِ JSON/بایت به سرورِ تمرین */
async function call(base, method, p, { token = '', body = null, headers = {}, raw = false } = {}) {
  const h = { ...headers };
  if (token) h.Authorization = `Bearer ${token}`;
  if (body !== null) h['Content-Type'] = 'application/json';
  const res = await fetch(`${base}${p}`, { method, headers: h, body: body === null ? undefined : JSON.stringify(body), signal: AbortSignal.timeout(10 * 60 * 1000) });
  if (raw) return { status: res.status, bytes: Buffer.from(await res.arrayBuffer()) };
  const text = await res.text();
  let json = null;
  try { json = JSON.parse(text); } catch { json = { text: text.slice(0, 300) }; }
  return { status: res.status, body: json };
}

/**
 * دودآزماییِ کاملِ انتشار روی سرورِ در حالِ کار (پیکربندیِ `RELEASE_API_BASE` همان GitHub ِ ساختگی). ⇐ گزارشِ خط‌به‌خط.
 * `token` = نشستِ مدیرِ ارشد. سرور باید برای همین برچسب هیچ مرحله‌ای نداشته باشد.
 */
async function smoke({ url, token, art, pubkey = '', log = console.log }) {
  const base = String(url).replace(/\/$/, '');
  const tag = art.tag;
  const vt = `${tag}-v${art.version}`;
  const ok = (msg) => log(`✔ ${msg}`);
  const must = (cond, msg) => { if (!cond) fail(msg); ok(msg); };
  const latest = async (q) => (await call(base, 'GET', `/api/releases/${tag}/latest${q}`)).body;
  const stagePut = async (body) => {
    const r = await call(base, 'PUT', '/api/admin/release/stage', { token, body: { tag, version: art.version, ...body, reason: 'آزمونِ انتشار روی کپیِ موقت' } });
    if (r.status !== 200) fail(`PUT ِ مرحله ${r.status}: ${JSON.stringify(r.body).slice(0, 200)}`);
    return r.body.stages[tag];
  };

  must((await call(base, 'GET', '/api/ready')).status === 200, 'سرورِ تازه روی پایگاهِ مهاجرت‌شده آماده است (/api/ready)');
  const g = await call(base, 'GET', '/api/admin/release/stage', { token });
  must(g.status === 200 && g.body.stages && g.body.stages[tag] === null, `پنل: هنوز هیچ مرحله‌ای برای ${tag} نیست`);

  //  ۱) فقط یک دکان
  await stagePut({ percent: 0, shops: [SHOP] });
  const mine = await latest(`?staged=1&shop=${SHOP}&device=rehearsal-dev-1`);
  must(mine.tag === vt && mine.channel === 'staged' && mine.version === art.version,
    `دکانِ فهرست نسخهٔ ${art.version} را از ${vt} می‌گیرد (${mine.tag}/${mine.channel}/${mine.version})`);
  for (const n of art.updaters) must((mine.assets || []).some((a) => a.name === n), `پاسخِ نسخه نصبیِ ${n} را دارد`);
  for (const n of art.updaters) {
    const f = art.files.find((x) => x.name === n);
    const r = await call(base, 'GET', `/api/releases/${vt}/file/${encodeURIComponent(n)}`, { raw: true });
    must(r.status === 200 && sha256(r.bytes) === f.sha && art.sums.get(n) === f.sha,
      `دانلودِ ${n} از سرور = همان بایت‌ها و همان هشِ SHA256SUMS.txt (${f.sha.slice(0, 12)}…)`);
  }
  const sums = await call(base, 'GET', `/api/releases/${vt}/file/SHA256SUMS.txt`, { raw: true });
  must(sums.status === 200 && sums.bytes.equals(art.sumsBuf), 'SHA256SUMS.txt ِ سروشده همان فایلِ انتشار است');
  if (art.sig) {
    const sig = await call(base, 'GET', `/api/releases/${vt}/file/SHA256SUMS.txt.sig`, { raw: true });
    must(sig.status === 200 && sig.bytes.equals(art.sig), 'SHA256SUMS.txt.sig ِ سروشده همان امضای انتشار است');
    if (pubkey) must(verifySignature(sums.bytes, sig.bytes.toString('utf8'), pubkey), 'امضای فهرستِ سروشده با کلیدِ سنجاق‌شدهٔ برنامه درست است');
  }
  const other = await latest(`?staged=1&shop=${OTHER_SHOP}&device=rehearsal-dev-2`);
  must(other.tag === tag && other.version === ROLLING_VERSION, `دکانِ بیرونِ فهرست همان برچسبِ روان (${other.tag}/${other.version})`);
  const old = await latest(`?shop=${SHOP}&device=rehearsal-dev-1`);
  must(old.tag === tag && old.version === ROLLING_VERSION, 'برنامهٔ قدیمی (بی staged=1) همان برچسبِ روان');

  //  ۲) همه
  await stagePut({ percent: 100 });
  const any = await latest('?staged=1&device=rehearsal-any-7');
  must(any.tag === vt && any.version === art.version, '۱۰۰٪: دستگاهِ دلخواه هم نسخهٔ تازه');

  //  ۳) ایستِ خودکار با خطای واقعی از درِ عمومی
  const gp = await call(base, 'PUT', '/api/admin/release/stage/guard', { token, body: { enabled: true, minErrorDevices: 2, reason: 'آزمونِ انتشار' } });
  must(gp.status === 200 && gp.body.settings && gp.body.settings.enabled === true, 'نگهبانِ ایستِ خودکار روشن شد');
  for (const d of ['rehearsal-crash-1', 'rehearsal-crash-2']) {
    const e = await call(base, 'POST', '/api/errors', {
      body: { app: 'shop', message: 'تمرینِ انتشار: خطای ساختگی', stack: 'at Rehearsal.kt:1' },
      headers: { 'X-Device': d, 'X-App-Version': art.version },
    });
    if (e.status !== 202) fail(`POST /api/errors ${e.status}`);
  }
  const chk = await call(base, 'POST', '/api/admin/release/stage/guard/check', { token, body: {} });
  const res = chk.body && chk.body.results && chk.body.results[tag];
  must(chk.status === 200 && res && res.halted === true, `دو دستگاهِ خطادار ⇒ ایستِ خودکار (${res && res.decision ? res.decision.reason : chk.status})`);
  const after = await latest(`?staged=1&shop=${SHOP}&device=rehearsal-any-7`);
  must(after.tag === tag && after.version === ROLLING_VERSION, 'پس از ایست همه (حتی دکانِ فهرست) برچسبِ روان');
  const partial = await call(base, 'GET', `/api/releases/${vt}/file/${encodeURIComponent(art.updaters[0])}`, { raw: true });
  must(partial.status === 200, 'دانلودِ نیمه‌کاره پس از ایست هنوز سرو می‌شود');
  const resumed = await stagePut({ percent: 5 });
  must(!resumed.halted, 'PUT ِ مدیر ایست را برمی‌دارد (ادامه با ۵٪)');
  await call(base, 'PUT', '/api/admin/release/stage/guard', { token, body: { enabled: false } });

  //  ۴) پایان
  const end = await call(base, 'DELETE', '/api/admin/release/stage', { token, body: { tag, reason: 'پایانِ تمرین' } });
  must(end.status === 200 && end.body.removed === true, 'پایانِ مرحله (DELETE)');
  const fin = await latest(`?staged=1&shop=${SHOP}&device=rehearsal-dev-1`);
  must(fin.tag === tag && fin.version === ROLLING_VERSION, 'پس از پایان همه برچسبِ روان');
  const gone = await call(base, 'GET', `/api/releases/${vt}/file/${encodeURIComponent(art.updaters[0])}`, { raw: true });
  must(gone.status === 404, 'برچسبِ نسخه‌دار پس از پایان دیگر سرو نمی‌شود');
  return true;
}

/** مدیرِ ارشدِ یک‌بارمصرف در پایگاهِ تمرین (پیش از روشن شدنِ سرور) ⇐ { username, password } */
async function makeAdmin() {
  const db = require('../src/db');
  const pw = require('../src/lib/password');
  const username = `rehearsal${crypto.randomBytes(3).toString('hex')}`;
  const password = `R!${crypto.randomBytes(12).toString('base64url')}`;
  await db.query(
    `INSERT INTO admins (id, username, name, password_hash, role, status, created_at)
     VALUES ($1,$2,'تمرینِ انتشار',$3,'superadmin','active',$4)`,
    [db.newId('adm'), username, await pw.hashPassword(password), db.now()]
  );
  await db.closeDb();
  return { username, password };
}

function guardDatabase(url = process.env.DATABASE_URL || '') {
  if (/^pglite:/i.test(url)) return;
  let name = '';
  try { name = decodeURIComponent(new URL(url).pathname.replace(/^\//, '')); } catch { /* پایین */ }
  if (!/(test|bench|staging)/i.test(name)) fail(`⛔ نامِ پایگاه باید test، bench یا staging داشته باشد (داده شد: «${name || url}»)`);
}

function args(argv) {
  const out = {};
  for (let i = 0; i < argv.length; i++) {
    if (argv[i].startsWith('--')) { out[argv[i].slice(2)] = argv[i + 1]; i++; }
  }
  return out;
}

async function waitReady(base, child, logFile) {
  for (let i = 0; i < 120; i++) {
    if (child.exitCode !== null) fail(`سرور بالا نیامد:\n${fs.readFileSync(logFile, 'utf8').slice(-3000)}`);
    try { if ((await fetch(`${base}/api/ready`, { signal: AbortSignal.timeout(2000) })).ok) return; } catch { /* هنوز نه */ }
    await new Promise((r) => setTimeout(r, 1000));
  }
  fail('سرور در ۱۲۰ ثانیه آماده نشد');
}

async function main() {
  const a = args(process.argv.slice(2));
  const pubkey = String(a.pubkey || process.env.TOHID_UPDATE_PUBLIC_KEY || '').trim();
  guardDatabase();
  const art = loadArtifacts(path.resolve(a.artifacts || '.'), { tag: a.tag, version: a.version, pubkey });
  console.log(`── فایل‌ها: ${art.files.length} فایل، نصبی: ${art.updaters.join('، ')}${pubkey ? '، امضا سنجیده شد' : '، بی کلیدِ عمومی (فقط هش)'}`);

  console.log('── مهاجرت‌های همین نسخه (بی‌اثر اگر تمرینِ ارتقا پیش‌تر زده)');
  const m = spawnSync(process.execPath, ['scripts/migrate.js'], { cwd: SERVER, stdio: 'inherit', env: process.env });
  if (m.status !== 0) fail('مهاجرت شکست خورد');
  const admin = await makeAdmin();

  const stub = stubGitHub(art);
  await new Promise((r) => stub.server.listen(0, '127.0.0.1', r));
  const port = Number(a.port || 4300);
  const base = `http://127.0.0.1:${port}`;
  const out = path.resolve(a.out || fs.mkdtempSync(path.join(os.tmpdir(), 'release-rehearsal-')));
  fs.mkdirSync(out, { recursive: true });
  const logFile = path.join(out, 'release-server.log');
  const child = spawn(process.execPath, ['src/index.js'], {
    cwd: SERVER,
    stdio: ['ignore', fs.openSync(logFile, 'w'), fs.openSync(logFile, 'a')],
    env: {
      ...process.env, PORT: String(port), NODE_ENV: 'test', BACKUP_ENABLED: 'false', TELEGRAM_AUTOSTART: 'false',
      API_SECRET: process.env.API_SECRET || 'rehearsal-secret-rehearsal-secret', OTP_SECRET: process.env.OTP_SECRET || 'rehearsal-otp-secret-rehearsal-ot',
      RELEASE_API_BASE: `http://127.0.0.1:${stub.server.address().port}`, RELEASE_REPO: REPO, RELEASE_GITHUB_TOKEN: '', GITHUB_TOKEN: '',
      RELEASE_CACHE_PATH: fs.mkdtempSync(path.join(os.tmpdir(), 'release-cache-')),
      RATE_GENERAL_MAX: '100000', RATE_ERRORS_MAX: '10000',
    },
  });
  try {
    await waitReady(base, child, logFile);
    const login = await call(base, 'POST', '/api/admin/login', { body: admin });
    if (login.status !== 200 || !login.body.token) fail(`ورودِ مدیرِ تمرین ${login.status}`);
    console.log(`── دودآزماییِ انتشارِ ${art.tag} ${art.version} روی کپیِ موقت`);
    await smoke({ url: base, token: login.body.token, art, pubkey });
    console.log(`✔ آزمونِ انتشارِ ${art.tag} ${art.version} روی کپیِ موقت سالم`);
  } finally {
    child.kill('SIGTERM');
    stub.server.close();
  }
}

if (require.main === module) {
  main().then(() => process.exit(0)).catch((err) => {
    console.error(`✖ ${err.message}`);
    process.exit(err instanceof RehearsalError ? 2 : 1);
  });
}

module.exports = { loadArtifacts, parseSums, verifySignature, updaterFiles, stubGitHub, smoke, RehearsalError, REPO, ROLLING_VERSION };
