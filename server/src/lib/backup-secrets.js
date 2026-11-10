'use strict';
/**
 * F0-28 — رازهای `app_config` در پشتیبانِ سرور مهر می‌شوند. پشتِ `BACKUP_SECRETS=seal`؛ پیش‌فرض `plain` = رفتارِ امروز
 * بی کم‌وکاست (هیچ تبدیلی در مسیر نیست). تا امروز هر کس فایلِ پشتیبان را داشت، کلیدِ خصوصیِ امضای مجوز را هم داشت: یعنی
 * توانِ ساختنِ مجوز و کدِ آفلاین برای هر دستگاه — به‌علاوهٔ کلیدِ پوشِ وب، رمز و کلیدِ ایمیل، کلیدِ پیامک، حسابِ سرویسِ پوش و
 * کلیدِ رمزِ خواندنِ پمپ‌ها (`SECRET_KEYS`).
 *
 * ── مهر (در خودِ پشتیبان، نه در دیتابیسِ زنده) ─────────────────────────────────────────────
 * هر ردیفِ راز در پشتیبان `sealed:<نام>` می‌شود با پاکتِ AES-256-GCM: `sbk1.<kid>.<iv>.<بدنه>.<برچسب>`، AAD = نسخه + kid + نام
 * (پاکت به نامِ خودش بسته است؛ جابه‌جا کردنش میانِ ردیف‌ها رد می‌شود). کلید از `BACKUP_SECRETS_KEY` یا
 * `BACKUP_SECRETS_KEY_FILE` (۶۴ hex، base64 ِ ۳۲ بایت، یا عبارتِ ≥ ۳۲ نویسه — همان قالبِ `HESABPAY_CRED_KEY`)؛ ⛔ هرگز در
 * دیتابیس یا پشتیبان نیست. فقط kid (۱۶ نویسه از HMAC ِ کلید، برگشت‌ناپذیر) در پاکت و meta می‌آید تا کلیدِ درست پیدا شود.
 * شمارِ ردیف‌ها همان می‌ماند ⇒ manifest و تمرینِ بازیابی همان.
 *   • pg_dump ⇒ `sealStream`: فقط بلوکِ `COPY public.app_config`؛ هر بایتِ دیگر همان. بلوک پیدا نشد یا شمار با snapshot
 *     نخواند ⇒ پشتیبان نمی‌نشیند (`backup.js`).
 *   • PGlite ⇒ `sealPglite`: رونوشتِ در حافظه ⇒ همان جابه‌جایی ⇒ `VACUUM FULL app_config` (heap و TOAST ِ تازه) ⇒ چرخاندنِ
 *     WAL تا هیچ صفحهٔ WAL ِ پیش از مهر در tar نماند. این **سنجیده** می‌شود (`walCheck`، نشانیِ هر صفحه)، نه فرض: بی آن
 *     مقدارِ خام در WAL ِ پوشهٔ داده می‌ماند. سه دور و باز مانده ⇒ شکست، نه پشتیبانِ نشت‌دار.
 *
 * ── باز کردن (`unseal`) ─────────────────────────────────────────────────────────────────
 * پس از `backup.restore`، در تمرین، و در هر راه‌اندازیِ سرور پیش از نخستین درخواست (`startup`):
 *   • کلید هست ⇒ همهٔ پاکت‌ها اول سنجیده می‌شوند؛ یکی دست‌خورده (`tampered`) یا با کلیدِ دیگر (`wrong_key`) ⇒ **هیچ** چیز
 *     نوشته نمی‌شود. همه سالم ⇒ در یک تراکنش به نامِ اصلی. ردیفِ اصلیِ ناخالی‌ای که در این میان نشسته روی‌نویسی نمی‌شود؛
 *     پاکتِ کهنه `sealed.orphan:<نام>:<هش>` می‌ماند.
 *   • کلید نیست ⇒ بازیابی موفق، رازها مهر و بی‌استفاده، پیامِ بلند (نام‌ها و kid، هرگز مقدار) که دقیقاً چه بگذارد. هیچ کلیدِ
 *     تازه‌ای ساخته نمی‌شود: مجوز ۵۰۳ ِ روشن (`license.js`)؛ برای `station_read_key` یک ردیفِ خالی (`PLACEHOLDER_KEYS`) جلوی
 *     ساختنِ کلیدِ تازه در `stations.js` را می‌گیرد (`INSERT … DO NOTHING` ⇒ «ساخته نشد») — کدِ پمپ دست نخورد، فقط داده.
 *     کلیدِ پوشِ وب (`station-chat.js`) بی ردیف جفتِ تازه می‌سازد: اشتراک‌های پوشِ مرورگر دوباره ثبت می‌شوند، قفلی نیست.
 * ⛔ هیچ مقدار، پاکت یا کلیدی در لاگ، سابقه، خطا یا meta نیست.
 */
const crypto = require('crypto');
const fs = require('fs');
const zlib = require('zlib');
const { Transform, Readable } = require('stream');

const SECRET_KEYS = Object.freeze([
  'email_key', 'email_pass', 'license_private_key', 'push_service_account', 'sms_key', 'station_read_key', 'vapid_private',
]);
const SECRET_SET = new Set(SECRET_KEYS);
const SEALED = 'sealed:';
const ORPHAN = 'sealed.orphan:';
/** مصرف‌کننده‌ای که نبودِ ردیف را «بساز» و ردیفِ خالی را «ساخته نشد» می‌خواند (`stations.js` ⇐ `secretKey`) */
const PLACEHOLDER_KEYS = Object.freeze(['station_read_key']);
const ENVELOPE = 'sbk1';
const KID_RE = /^[0-9a-f]{16}$/;
const OWNER_DOC = 'docs/spec/OWNER-ACTIONS-fa.md §۳';

/** 'plain' | 'seal' | 'invalid' (هر چیزِ دیگر — مثلاً «omit» — پشتیبان را رد می‌کند، نه بی‌صدا plain) */
function mode() {
  const m = String(require('../config').backup.secrets ?? '').trim().toLowerCase();
  if (!m || m === 'plain') return 'plain';
  return m === 'seal' ? 'seal' : 'invalid';
}

/**
 * ۶۴ hex یا base64 ِ ۳۲ بایت ⇒ همان بایت‌ها؛ عبارتِ ≥ ۳۲ نویسه ⇒ scrypt (نه SHA-256 ِ تند: kid در پاکت و meta است و با
 * هشِ تند حدس زدنِ عبارت بیرون از سرور ارزان می‌شد). یک بار در هر اجرا (`derived`).
 */
const derived = new Map();
function keyFrom(raw) {
  const s = String(raw || '').trim();
  if (!s) return null;
  if (/^[0-9a-fA-F]{64}$/.test(s)) return Buffer.from(s, 'hex');
  if (/^[A-Za-z0-9+/]{43}=$/.test(s) || /^[A-Za-z0-9_-]{43}$/.test(s)) {
    const b = Buffer.from(s.replace(/-/g, '+').replace(/_/g, '/'), 'base64');
    if (b.length === 32) return b;
  }
  if (s.length < 32) return null;
  const memo = crypto.createHash('sha256').update(s, 'utf8').digest('hex');
  if (!derived.has(memo)) {
    derived.set(memo, crypto.scryptSync(s, 'shop-backup-secrets:v1', 32, { N: 1 << 15, r: 8, p: 1, maxmem: 64 * 1024 * 1024 }));
  }
  return derived.get(memo);
}
const kidOf = (key) => crypto.createHmac('sha256', key).update('shop-backup-secrets:kid:v1').digest('hex').slice(0, 16);

/** کلیدی که این متن می‌دهد (برای آزمون و ابزار) — `{ state, key, kid }` */
function keyStateFor(raw, source = 'env') {
  const key = keyFrom(raw);
  return key ? { state: 'ok', key, kid: kidOf(key), source } : { state: 'weak', key: null, kid: '', source };
}

/** کلید از محیط: 'ok' | 'missing' | 'weak' | 'unreadable'. ⛔ هیچ چیز از خودِ کلید بیرون نمی‌رود جز kid */
function keyState() {
  const env = String(process.env.BACKUP_SECRETS_KEY || '').trim();
  if (env) return keyStateFor(env, 'env');
  const file = String(process.env.BACKUP_SECRETS_KEY_FILE || '').trim();
  if (!file) return { state: 'missing', key: null, kid: '', source: '' };
  let raw = '';
  try { raw = fs.readFileSync(file, 'utf8'); } catch { return { state: 'unreadable', key: null, kid: '', source: 'file' }; }
  return keyStateFor(raw, 'file');
}

function sealErr(code, message = 'پاکتِ رازِ پشتیبان باز نشد') {
  const e = new Error(message);
  e.code = code;
  return e;
}

const aad = (kid, name) => Buffer.from(`shop-backup-secret:v1:${kid}:${name}`, 'utf8');
/** base64url ِ یکتا: هر نویسهٔ دیگری که به همان بایت‌ها برسد (بیت‌های بی‌کارِ آخر) دست‌کاری است */
function b64(s, len) {
  const b = Buffer.from(String(s), 'base64url');
  if (b.toString('base64url') !== s || (len != null && b.length !== len)) throw sealErr('tampered');
  return b;
}

function sealValue(k, name, plain) {
  const iv = crypto.randomBytes(12);
  const c = crypto.createCipheriv('aes-256-gcm', k.key, iv, { authTagLength: 16 });
  c.setAAD(aad(k.kid, name));
  const body = Buffer.concat([c.update(String(plain), 'utf8'), c.final()]);
  return [ENVELOPE, k.kid, iv.toString('base64url'), body.toString('base64url'), c.getAuthTag().toString('base64url')].join('.');
}

function envelopeKid(v) {
  const p = String(v || '').split('.');
  return p.length === 5 && p[0] === ENVELOPE && KID_RE.test(p[1]) ? p[1] : '';
}

/** پاکت ⇒ متنِ خام؛ هر شکستی خطایی با `code` ِ 'bad_envelope' | 'wrong_key' | 'tampered' (بی تکه‌ای از پاکت) */
function openValue(k, name, v) {
  const p = String(v || '').split('.');
  if (p.length !== 5 || p[0] !== ENVELOPE || !KID_RE.test(p[1])) throw sealErr('bad_envelope');
  if (p[1] !== k.kid) throw sealErr('wrong_key');
  try {
    const d = crypto.createDecipheriv('aes-256-gcm', k.key, b64(p[2], 12), { authTagLength: 16 });
    d.setAAD(aad(p[1], name));
    d.setAuthTag(b64(p[4], 16));
    return Buffer.concat([d.update(b64(p[3])), d.final()]).toString('utf8');
  } catch { throw sealErr('tampered'); }
}

/** kid ِ کلیدِ مجوز (همان `license.js`) از PEM ِ خصوصی؛ از کلیدِ **عمومی** است، راز نیست */
function licenseKidOf(pem) {
  try {
    const spki = crypto.createPublicKey(crypto.createPrivateKey(pem)).export({ type: 'spki', format: 'der' });
    return crypto.createHash('sha256').update(spki).digest('hex').slice(0, 16);
  } catch { return null; }
}

function orphanName(name, value, taken) {
  const base = `${ORPHAN}${name}:${crypto.createHash('sha256').update(String(value)).digest('hex').slice(0, 12)}`;
  let n = base;
  for (let i = 2; taken.has(n); i++) n = `${base}:${i}`;
  taken.add(n);
  return n;
}

/**
 * رو به پشتیبان: ردیف‌های راز (ناخالی) ⇒ `sealed:<نام>`؛ پاکتِ کهنهٔ هم‌نامی که هنوز باز نشده ⇒ یتیم (تا دو کلیدِ یکسان
 * در پشتیبان نباشد). `rows` همهٔ ردیف‌های راز، `sealed:*` و `sealed.orphan:*`؛ `taken` همهٔ کلیدهای جدول.
 * @returns {Map<string,{key:string,value:string}>} کلیدِ کنونی ⇒ ردیفِ تازه
 */
function planSeal(rows, k, taken) {
  const sealing = new Set(rows.filter((r) => SECRET_SET.has(r.key) && r.value).map((r) => r.key));
  const plan = new Map();
  for (const r of rows) {
    if (SECRET_SET.has(r.key) && r.value) plan.set(r.key, { key: SEALED + r.key, value: sealValue(k, r.key, r.value) });
    else if (r.key.startsWith(SEALED) && sealing.has(r.key.slice(SEALED.length))) {
      plan.set(r.key, { key: orphanName(r.key.slice(SEALED.length), r.value, taken), value: r.value });
    }
  }
  return plan;
}

function reportOf(plan, rows) {
  const keys = [...plan.values()].filter((x) => x.key.startsWith(SEALED)).map((x) => x.key.slice(SEALED.length)).sort();
  const lic = rows.find((r) => r.key === 'license_private_key' && r.value);
  return { sealed: keys.length, keys, orphaned: plan.size - keys.length, licenseKid: lic ? licenseKidOf(lic.value) : null };
}

/* ───────────── متنِ COPY ِ pg_dump ───────────── */

const UNESC = { b: '\b', f: '\f', n: '\n', r: '\r', t: '\t', v: '\v' };
/** یک فیلدِ متنیِ COPY ⇒ متن (`\N` ⇒ null). گریزِ عددی را pg_dump نمی‌نویسد ⇒ رد، نه حدسِ غلط */
function copyDecode(s) {
  if (s === '\\N') return null;
  return s.replace(/\\(.)/gs, (_m, ch) => {
    if (/[0-7x]/.test(ch)) throw sealErr('copy_format', 'قالبِ بلوکِ app_config ِ pg_dump شناخته نیست');
    return UNESC[ch] ?? ch;
  });
}
const copyEscape = (s) => String(s).replace(/\\/g, '\\\\').replace(/\n/g, '\\n').replace(/\r/g, '\\r').replace(/\t/g, '\\t')
  .replace(/\x08/g, '\\b').replace(/\f/g, '\\f').replace(/\v/g, '\\v');

const HEAD = Buffer.from('\nCOPY public.app_config (');
const NL = 0x0a;

function parseHeader(line) {
  const m = /^COPY public\.app_config \((.*)\) FROM stdin;$/.exec(line);
  if (!m) throw sealErr('copy_format', 'سرخطِ بلوکِ app_config ِ pg_dump شناخته نیست');
  const cols = m[1].split(',').map((c) => c.trim().replace(/^"(.*)"$/, '$1'));
  const key = cols.indexOf('key');
  const value = cols.indexOf('value');
  if (key < 0 || value < 0) throw sealErr('copy_format', 'ستون‌های key/value در بلوکِ app_config نیست');
  return { key, value, n: cols.length };
}

function splitTabs(line) {
  const out = [];
  let at = 0;
  for (;;) {
    const i = line.indexOf(0x09, at);
    if (i === -1) { out.push(line.subarray(at)); return out; }
    out.push(line.subarray(at, i));
    at = i + 1;
  }
}

/**
 * جریانِ خروجیِ pg_dump (قالبِ plain) ⇒ همان، با ردیف‌های رازِ `app_config` مهرشده. خطوطِ دیگر و ردیف‌های دیگرِ همان
 * بلوک بایت‌به‌بایت همان‌اند. `t.report`: `{ found, sealed, keys, orphaned, licenseKid }` (فقط نام و شمار).
 */
function sealStream(k) {
  let state = 'scan';
  let carry = Buffer.alloc(0);
  let cols = null;
  const lines = [];
  const report = { found: false, sealed: 0, keys: [], orphaned: 0, licenseKid: null };

  function finishBlock() {
    const parsed = lines.map((line) => {
      const f = splitTabs(line);
      if (f.length !== cols.n) throw sealErr('copy_format', 'ردیفِ بلوکِ app_config شمارِ ستونِ درست ندارد');
      return { f, key: copyDecode(f[cols.key].toString('utf8')) };
    });
    const taken = new Set(parsed.map((p) => p.key));
    const rows = parsed
      .filter((p) => SECRET_SET.has(p.key) || p.key.startsWith(SEALED) || p.key.startsWith(ORPHAN))
      .map((p) => ({ key: p.key, value: copyDecode(p.f[cols.value].toString('utf8')) || '' }));
    const plan = planSeal(rows, k, taken);
    Object.assign(report, reportOf(plan, rows));
    const out = parsed.map((p, i) => {
      const to = plan.get(p.key);
      if (!to) return lines[i];
      const f = p.f.slice();
      f[cols.key] = Buffer.from(copyEscape(to.key));
      f[cols.value] = Buffer.from(copyEscape(to.value));
      return Buffer.concat(f.flatMap((x, i) => (i ? [Buffer.from('\t'), x] : [x])));
    });
    return out.length ? Buffer.concat(out.flatMap((l) => [l, Buffer.from('\n')])) : Buffer.alloc(0);
  }

  const t = new Transform({
    transform(chunk, _enc, cb) {
      try {
        if (state === 'done') return cb(null, chunk);
        let data = carry.length ? Buffer.concat([carry, chunk]) : chunk;
        carry = Buffer.alloc(0);
        const out = [];
        if (state === 'scan') {
          const i = data.indexOf(HEAD);
          if (i === -1) {
            const keep = Math.min(data.length, HEAD.length - 1);
            carry = Buffer.from(data.subarray(data.length - keep));
            return cb(null, data.subarray(0, data.length - keep));
          }
          const eol = data.indexOf(NL, i + 1);
          if (eol === -1) { carry = Buffer.from(data.subarray(i)); return cb(null, data.subarray(0, i)); }
          cols = parseHeader(data.subarray(i + 1, eol).toString('utf8'));
          report.found = true;
          out.push(data.subarray(0, eol + 1));
          data = data.subarray(eol + 1);
          state = 'block';
        }
        let at = 0;
        for (;;) {
          const eol = data.indexOf(NL, at);
          if (eol === -1) break;
          const line = data.subarray(at, eol);
          at = eol + 1;
          if (line.length === 2 && line[0] === 0x5c && line[1] === 0x2e) {   // «\.»
            out.push(finishBlock(), Buffer.from('\\.\n'), data.subarray(at));
            state = 'done';
            return cb(null, Buffer.concat(out));
          }
          lines.push(Buffer.from(line));
        }
        carry = Buffer.from(data.subarray(at));
        return cb(null, Buffer.concat(out));
      } catch (err) { return cb(err); }
    },
    flush(cb) {
      if (state === 'block') return cb(sealErr('copy_format', 'بلوکِ app_config ِ pg_dump نیمه ماند'));
      return cb(null, carry);
    },
  });
  t.report = report;
  return t;
}

/* ───────────── PGlite: پوشهٔ داده ───────────── */

const lsnOf = (s) => { const [hi, lo] = String(s).split('/'); return (BigInt(`0x${hi}`) << 32n) | BigInt(`0x${lo}`); };

/** خواندنِ بایت‌به‌بایتِ یک جریان (برای `walCheck`) */
function byteQueue(stream) {
  const it = stream[Symbol.asyncIterator]();
  let chunks = [];
  let have = 0;
  let done = false;
  async function fill(n) {
    while (have < n && !done) {
      const r = await it.next();
      if (r.done) { done = true; break; }
      chunks.push(r.value);
      have += r.value.length;
    }
  }
  function take(n) {
    const b = chunks.length === 1 ? chunks[0] : Buffer.concat(chunks, have);
    chunks = n < b.length ? [b.subarray(n)] : [];
    have = b.length - n;
    return b.subarray(0, n);
  }
  return {
    async read(n) { await fill(n); return have >= n ? take(n) : null; },
    async skip(n) {
      while (n > 0) {
        await fill(Math.min(n, 1 << 20));
        if (!have) return;
        const k = Math.min(n, have);
        take(k);
        n -= k;
      }
    },
  };
}

const cstr = (b, at, len) => { const s = b.subarray(at, at + len); const z = s.indexOf(0); return (z === -1 ? s : s.subarray(0, z)).toString('utf8'); };

/**
 * هر صفحهٔ هر پروندهٔ `pg_wal/` ِ tar: یا تماماً صفر، یا نشانیِ صفحه‌اش (`xlp_pageaddr`) ≥ `floor` (یعنی پس از مهر نوشته شده).
 * صفحه‌ای با نشانیِ کمتر = WAL ِ پیش از مهر که مقدارِ خام را دارد. tar ِ نخواندنی یا بی WAL ⇒ ok=false (هرگز «سالم» ِ فرضی).
 */
async function walCheck(tarGz, { floor, blk }) {
  const q = byteQueue(Readable.from([tarGz]).pipe(zlib.createGunzip()));
  const zero = Buffer.alloc(blk);
  let walFiles = 0;
  let stale = 0;
  let longName = null;
  for (;;) {
    const hd = await q.read(512);
    if (!hd || hd.every((x) => x === 0)) break;
    const size = parseInt(cstr(hd, 124, 12).trim() || '0', 8);
    const type = hd[156] ? String.fromCharCode(hd[156]) : '0';
    let name = longName ?? cstr(hd, 0, 100);
    if (longName === null && hd.subarray(257, 262).toString('latin1') === 'ustar') {
      const pre = cstr(hd, 345, 155);
      if (pre) name = `${pre}/${name}`;
    }
    longName = null;
    const padded = Math.ceil(size / 512) * 512;
    if (type === 'L' || type === 'x') {
      const d = await q.read(padded);
      if (!d) break;
      if (type === 'L') longName = cstr(d, 0, size);
      else { const m = /\d+ path=([^\n]*)\n/.exec(d.subarray(0, size).toString('utf8')); if (m) longName = m[1]; }
      continue;
    }
    if (!(type === '0') || !/(^|\/)pg_wal\/[0-9A-F]{24}$/.test(name)) { await q.skip(padded); continue; }
    walFiles++;
    let left = size;
    while (left > 0) {
      const n = Math.min(blk, left);
      const page = await q.read(n);
      if (!page) return { ok: false, walFiles, stale, truncated: true };
      if (!(n === blk && page.equals(zero)) && (page.length < 16 || page.readBigUInt64LE(8) < floor)) stale++;
      left -= n;
    }
    await q.skip(padded - size);
  }
  return { ok: walFiles > 0 && stale === 0, walFiles, stale };
}

/**
 * پشتیبانِ PGlite (tar.gz ِ پوشهٔ داده) ⇒ همان، با رازهای مهرشده و بی هیچ صفحهٔ WAL ِ پیش از مهر.
 * @returns {Promise<{tar:Buffer, report:object}>}
 */
async function sealPglite(raw, k, { rounds = 3 } = {}) {
  const lite = await require('./pglite-create').createPglite({ dataDir: 'memory://', loadDataDir: new Blob([raw]) });
  try {
    const all = (await lite.query('SELECT key FROM app_config')).rows.map((r) => r.key);
    const rows = (await lite.query(
      `SELECT key, value FROM app_config WHERE key = ANY($1::text[]) OR key LIKE 'sealed:%' OR key LIKE 'sealed.orphan:%'`,
      [SECRET_KEYS]
    )).rows;
    const plan = planSeal(rows, k, new Set(all));
    //  اول یتیم‌ها (نامِ `sealed:<نام>` آزاد شود)، بعد مهرها — بی برخوردِ کلیدِ یکتا
    const steps = [...plan.entries()].sort(([a], [b]) => Number(!a.startsWith(SEALED)) - Number(!b.startsWith(SEALED)));
    await lite.transaction(async (t) => {
      for (const [from, to] of steps) {
        const r = await t.query('UPDATE app_config SET key = $2, value = $3 WHERE key = $1', [from, to.key, to.value]);
        if (r.affectedRows !== 1) throw sealErr('seal_failed', 'ردیفِ راز در رونوشتِ PGlite پیدا نشد');
      }
    });
    //  ⛔ SR38-1: ANALYZE ِ دوره‌ای (`pglite-stats`) نمونهٔ مقدارهای خام را در pg_statistic گذاشته ⇒ آمارِ app_config هم می‌رود
    //  (پس از بازگشت دوباره ساخته می‌شود)؛ `VACUUM FULL pg_statistic` نسخهٔ مردهٔ همان ردیف‌ها را هم از heap و TOAST می‌بَرد
    await lite.query(`DELETE FROM pg_statistic WHERE starelid = 'public.app_config'::regclass`);
    //  heap و TOAST ِ تازه؛ پرونده‌های پیشینِ app_config (با مقدارِ خام) می‌روند
    await lite.exec('VACUUM FULL app_config, pg_statistic');
    const setting = async (n) => BigInt((await lite.query('SELECT setting FROM pg_settings WHERE name = $1', [n])).rows[0].setting);
    const seg = await setting('wal_segment_size');
    const blk = Number(await setting('wal_block_size'));
    await lite.exec('CHECKPOINT');
    await lite.query('SELECT pg_switch_wal()');
    //  هر صفحهٔ WAL از آغازِ این پرونده به بعد پس از مهر نوشته شده
    const floor = (lsnOf((await lite.query('SELECT pg_current_wal_insert_lsn()::text AS l')).rows[0].l) / seg) * seg;
    for (let round = 0; round < rounds; round++) {
      //  هر پروندهٔ حلقهٔ WAL یک بار «جاری» شود: سوییچ باقیِ پرونده را با صفحه‌های تازه پر می‌کند
      const files = (await lite.query("SELECT count(*)::int AS n FROM pg_ls_waldir() WHERE name ~ '^[0-9A-F]{24}$'")).rows[0].n;
      for (let i = 0; i <= files; i++) {
        await lite.exec('CHECKPOINT');
        await lite.query('SELECT pg_switch_wal()');
      }
      await lite.exec('CHECKPOINT');
      const tar = Buffer.from(await (await lite.dumpDataDir('gzip')).arrayBuffer());
      if ((await walCheck(tar, { floor, blk })).ok) return { tar, report: reportOf(plan, rows) };
    }
    throw sealErr('wal_residue', 'WAL ِ پیش از مهر از رونوشتِ PGlite پاک نشد');
  } finally {
    await lite.close();
  }
}

/* ───────────── باز کردن ───────────── */

/** اتصالِ یک استخر (pg.Pool، استخرِ ساختگیِ PGlite ِ تمرین): پرس‌وجو + تراکنش روی **یک** اتصال */
function poolConn(pool) {
  return {
    q: (t, p) => pool.query ? pool.query(t, p) : pool.connect().then(async (c) => { try { return await c.query(t, p); } finally { c.release(); } }),
    async tx(fn) {
      const c = await pool.connect();
      try {
        await c.query('BEGIN');
        const r = await fn(c);
        await c.query('COMMIT');
        return r;
      } catch (err) {
        await c.query('ROLLBACK').catch(() => {});
        throw err;
      } finally { c.release(); }
    },
  };
}

/** دیتابیسِ همین سرور (`db.js`) */
function liveConn() {
  const db = require('../db');
  return { q: (t, p) => db.query(t, p), tx: (fn) => db.tx(fn), live: true };
}

/** نشانیِ دیگری (مقصدِ `backup.restore`) — اتصالِ تک، بسته پس از کار */
async function withUrl(url, fn) {
  const { Client } = require('pg');
  const c = new Client({ connectionString: url });
  await c.connect();
  try {
    return await fn({
      q: (t, p) => c.query(t, p),
      async tx(f) {
        await c.query('BEGIN');
        try { const r = await f(c); await c.query('COMMIT'); return r; } catch (err) { await c.query('ROLLBACK').catch(() => {}); throw err; }
      },
    });
  } finally { await c.end(); }
}

function missingMessage(names, kids, state) {
  const why = state === 'weak' ? 'BACKUP_SECRETS_KEY ِ داده‌شده کوتاه است (۶۴ hex، base64 ِ ۳۲ بایت یا ≥ ۳۲ نویسه)'
    : state === 'unreadable' ? 'پروندهٔ BACKUP_SECRETS_KEY_FILE خوانده نشد' : 'BACKUP_SECRETS_KEY روی این سرور نیست';
  return `${names.length} رازِ مهرشده از پشتیبان برگشت و هنوز مهر است (${names.join('، ')}): ${why}. تا آن، امضای مجوز و کدِ آفلاین`
    + ' ۵۰۳ است و این تنظیم‌ها کار نمی‌کنند؛ هیچ کلیدِ تازه‌ای ساخته نمی‌شود و بقیهٔ سرور کار می‌کند. همان BACKUP_SECRETS_KEY'
    + ` (یا BACKUP_SECRETS_KEY_FILE) ای را که هنگامِ گرفتنِ پشتیبان روی سرور بود (kid=${kids.join('، ') || 'نامعلوم'}) در محیطِ`
    + ` سرور بگذارید و سرور را دوباره روشن کنید (${OWNER_DOC}).`;
}

function rejectMessage(code, rejected, k, kids) {
  const names = rejected.map((x) => x.name).join('، ');
  return code === 'wrong_key'
    ? `کلیدِ BACKUP_SECRETS_KEY ِ این سرور (kid=${k.kid}) کلیدِ این پشتیبان (kid=${kids.join('، ')}) نیست؛ هیچ رازی باز یا نوشته نشد`
      + ` (${names}). همان کلیدِ هنگامِ پشتیبان را بگذارید (${OWNER_DOC}).`
    : `${rejected.length} پاکتِ مهرشده (${names}) دست‌خورده است؛ هیچ رازی نوشته نشد (همه یا هیچ). پشتیبانِ سالمِ دیگری را برگردانید.`;
}

/**
 * پاکت‌های `sealed:*` ِ یک دیتابیس ⇒ نامِ اصلی (کلید هست) یا گزارش (نیست). هرگز throw نمی‌کند مگر خطای دیتابیس.
 * @param {{q:Function, tx:Function}} conn
 * @param {{placeholders?:boolean}} [o]  ردیفِ خالیِ `PLACEHOLDER_KEYS` بی کلید (فقط دیتابیسِ همین سرور)
 */
async function unseal(conn, { placeholders = false } = {}) {
  //  دیتابیسِ بی app_config (پشتیبانِ خالی یا کهنه در تمرین) ⇒ چیزی مهر نیست، همان رفتارِ پیشین
  if (!(await conn.q(`SELECT to_regclass('public.app_config')::text AS t`)).rows[0]?.t) return { pending: 0, unsealed: 0, names: [], kids: [] };
  const rows = (await conn.q(`SELECT key, value, updated_at FROM app_config WHERE key LIKE 'sealed:%' ORDER BY key`)).rows;
  if (!rows.length) return { pending: 0, unsealed: 0, names: [], kids: [] };
  const names = rows.map((r) => r.key.slice(SEALED.length));
  const kids = [...new Set(rows.map((r) => envelopeKid(r.value)).filter(Boolean))];
  const k = keyState();
  if (k.state !== 'ok') {
    let held = 0;
    if (placeholders) {
      for (const n of names.filter((x) => PLACEHOLDER_KEYS.includes(x))) {
        if ((await conn.q('SELECT 1 AS x FROM app_config WHERE key = $1', [n])).rows.length) continue;
        await conn.q(`INSERT INTO app_config (key, value, updated_at) VALUES ($1, '', $2) ON CONFLICT (key) DO NOTHING`, [n, Date.now()]);
        held++;
      }
    }
    return { pending: rows.length, unsealed: 0, names, kids, missingKey: true, keyState: k.state, held, message: missingMessage(names, kids, k.state) };
  }
  const opened = [];
  const rejected = [];
  for (const r of rows) {
    const name = r.key.slice(SEALED.length);
    try { opened.push({ name, sealedKey: r.key, envelope: r.value, plain: openValue(k, name, r.value), updatedAt: String(r.updated_at) }); } catch (e) { rejected.push({ name, code: e.code || 'tampered' }); }
  }
  if (rejected.length) {
    const code = rejected.some((x) => x.code === 'wrong_key') ? 'wrong_key' : 'tampered';
    return { pending: rows.length, unsealed: 0, names, kids, rejected, code, message: rejectMessage(code, rejected, k, kids) };
  }
  const done = await conn.tx(async (c) => {
    const taken = new Set((await c.query(`SELECT key FROM app_config WHERE key LIKE 'sealed.orphan:%'`)).rows.map((r) => r.key));
    let restored = 0;
    let same = 0;
    const conflicts = [];
    for (const o of opened) {
      const still = (await c.query('SELECT value FROM app_config WHERE key = $1 FOR UPDATE', [o.sealedKey])).rows[0];
      //  نمونهٔ دیگری از همین سرور (CLUSTER) همین دم بازش کرد ⇒ کاری نمانده؛ پاکتِ دیگری جایش نشسته ⇒ هیچ
      if (!still) continue;
      if (still.value !== o.envelope) throw sealErr('changed', 'پاکت‌ها در میانِ باز کردن عوض شدند');
      const cur = (await c.query('SELECT value FROM app_config WHERE key = $1 FOR UPDATE', [o.name])).rows[0];
      if (!cur || cur.value === '') {
        await c.query(
          `INSERT INTO app_config (key, value, updated_at) VALUES ($1, $2, $3::bigint)
           ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
          [o.name, o.plain, o.updatedAt]
        );
        await c.query('DELETE FROM app_config WHERE key = $1', [o.sealedKey]);
        restored++;
      } else if (cur.value === o.plain) {
        await c.query('DELETE FROM app_config WHERE key = $1', [o.sealedKey]);
        same++;
      } else {
        await c.query('UPDATE app_config SET key = $2 WHERE key = $1', [o.sealedKey, orphanName(o.name, o.envelope, taken)]);
        conflicts.push(o.name);
      }
    }
    return { restored, same, conflicts };
  });
  return { pending: rows.length, unsealed: done.restored + done.same, ...done, names, kids };
}

/** پس از `backup.restore`: همان دیتابیسِ این سرور یا هر نشانیِ دیگر */
async function unsealAt(url) {
  const config = require('../config');
  if (url === config.db.url) return unseal(liveConn());
  return withUrl(url, (conn) => unseal(conn));
}

const auditLog = (action, detail) => require('./audit').log({ actorType: 'system', action, detail }).catch(() => {});

/**
 * راه‌اندازیِ سرور (`index.js`، پس از migrate و پیش از کلیدِ مجوز و نخستین درخواست). هرگز سرور را نمی‌خواباند.
 * plain و بی پاکت ⇒ بی هیچ خطی (همان امروز).
 */
async function startup(log = console) {
  const m = mode();
  const k = keyState();
  if (m === 'invalid') log.error(`[backup-secrets] ⛔ BACKUP_SECRETS فقط plain یا seal است؛ تا درست شود پشتیبان ساخته نمی‌شود (${OWNER_DOC}).`);
  else if (m === 'seal' && k.state !== 'ok') log.error(`[backup-secrets] ⛔ BACKUP_SECRETS=seal ولی کلید آماده نیست (${k.state}); تا آن پشتیبان ساخته نمی‌شود (${OWNER_DOC}).`);
  let r;
  try {
    r = await unseal(liveConn(), { placeholders: true });
  } catch (err) {
    log.error(`[backup-secrets] ⛔ پاکت‌های رازِ پشتیبان سنجیده نشدند (${String(err?.code || 'error')}); سرور بالا می‌آید و راز‌ها مهر می‌مانند.`);
    return { ok: false, error: String(err?.code || 'error') };
  }
  if (!r.pending) return r;
  if (r.missingKey) {
    log.error(`[backup-secrets] ⛔ ${r.message}`);
    await auditLog('backup.secrets_pending', { count: r.pending, names: r.names, kids: r.kids, keyState: r.keyState });
  } else if (r.rejected) {
    log.error(`[backup-secrets] ⛔ ${r.message}`);
    await auditLog('backup.secrets_rejected', { code: r.code, names: r.rejected.map((x) => x.name), kids: r.kids });
  } else {
    log.log(`[backup-secrets] ${r.unsealed} رازِ پشتیبان باز شد و سرِ جایش نشست (${r.names.filter((n) => !r.conflicts.includes(n)).join('، ')}).`);
    if (r.conflicts.length) {
      log.warn(`[backup-secrets] ⚠️ ${r.conflicts.length} راز در این میان روی سرور عوض شده بود و روی‌نویسی نشد (${r.conflicts.join('، ')})؛`
        + ' مقدارِ پشتیبان با نامِ sealed.orphan:… ماند.');
    }
    await auditLog('backup.secrets_unsealed', { count: r.unsealed, conflicts: r.conflicts });
  }
  return r;
}

/**
 * گامِ رازها در تمرینِ بازیابی (`restore-drill.js`) روی دیتابیسِ جدا: با کلید باز می‌کند و کلیدِ مجوز را با meta می‌سنجد.
 * plain (بی پاکت و بی `meta.secrets`) ⇒ null، تا خروجیِ تمرین همان امروز بماند.
 */
async function drillStep(iso, meta) {
  const r = await unseal(poolConn(iso.pool));
  const want = meta?.secrets || null;
  if (!r.pending && !want) return null;
  const pem = (await iso.q(`SELECT value FROM app_config WHERE key = 'license_private_key'`)).rows[0]?.value || '';
  const kid = pem ? licenseKidOf(pem) : null;
  const sealedLicense = r.names.includes('license_private_key') && !(r.unsealed && !r.rejected);
  const licenseKey = sealedLicense ? 'sealed' : !pem ? 'absent' : !want?.licenseKid ? 'present' : kid === want.licenseKid ? 'same' : 'different';
  const ok = !r.missingKey && !r.rejected && (!want || r.unsealed === (want.sealed ?? r.pending)) && licenseKey !== 'different'
    && (!want?.licenseKid || licenseKey === 'same');
  return {
    mode: want?.mode || 'seal', sealed: want?.sealed ?? r.pending, pending: r.pending, unsealed: r.unsealed,
    missingKey: !!r.missingKey, code: r.code || null, rejected: r.rejected || null, conflicts: r.conflicts || [],
    kids: r.kids, licenseKey, ok, message: r.message || '',
  };
}

module.exports = {
  SECRET_KEYS, SEALED, ORPHAN, PLACEHOLDER_KEYS,
  mode, keyState, keyStateFor, sealValue, openValue, envelopeKid, licenseKidOf,
  sealStream, sealPglite, walCheck, planSeal,
  unseal, unsealAt, poolConn, liveConn, startup, drillStep,
};
