#!/usr/bin/env node
'use strict';
/**
 * OPS-02 — پشتیبانِ دستیِ رمزدار با «عبارتِ بازیابیِ فروشگاه»، بیرون از برنامه (کامپیوترِ هر کس با Node).
 *
 *   node scripts/phrase-backup.js check <فایل>             رمزدار است؟ با عبارت یا کلیدِ دستگاه؟ ZIP ِ کامل؟ (بی عبارت)
 *   node scripts/phrase-backup.js seal  <ZIP> <خروجی>      پشتیبانِ ساده (USB، پوشه) ⇒ پاکتِ رمز با عبارت (نمکِ تازه)
 *   node scripts/phrase-backup.js open  <پاکت> <خروجی>     پاکت ⇒ همان ZIP (یا JSON ِ «پشتیبانِ فقط دفتر»)، تا «بازیابی از
 *                                                          فایل» ِ برنامه یا نسخهٔ وب آن را بگیرد
 *
 * عبارت فقط از ورودیِ استاندارد (یا `TOHID_PHRASE`) خوانده می‌شود — هرگز از خطِ فرمان (تاریخچهٔ پوسته، `ps`) — و هیچ‌جا
 * چاپ نمی‌شود. خروجی هرگز رونویسی نمی‌شود (اول `.tmp`، بعد جابه‌جایی؛ پروندهٔ موجود ⇒ خطا) و فقط برای صاحبش خواندنی است.
 *
 * ⛔ یک قرارداد با برنامه (بایت‌به‌بایت): پاکتِ `AtRestEnvelope` (MAGIC ‖ نسخه ۱ ‖ طولِ KID ‖ KID ‖ nonce ِ ۱۲ ‖
 * AES-256-GCM با سرآیند زیرِ AAD) و KID ِ `rp1.<تکرار>.<نمک>` با کلیدِ PBKDF2-HMAC-SHA256(عبارتِ استاندارد، نمک، تکرار)
 * — `android-native/…/core/atrest/{AtRestEnvelope,platform/PhraseBackup}.kt` و `core/…/core/backup/RecoveryPhrase.kt`.
 * سنجه: بردارِ Python ِ `PhraseBackupTest.kt` در `test/phrase-backup-tool.test.js`. یکی را عوض کنی، دیگری را هم.
 */
const crypto = require('node:crypto');
const fs = require('node:fs');

const MAGIC = Buffer.from([0x89, 0x54, 0x45, 0x4e, 0x43, 0x0d, 0x0a, 0x1a]);
const VERSION = 1;
const NONCE_LEN = 12;
const TAG_LEN = 16;
const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const BODY = 24;
const LENGTH = BODY + 1;
const SALT_BYTES = 16;
const ITERATIONS = 600000;
const ITERATIONS_MIN = 100000;
const ITERATIONS_MAX = 5000000;
const KID_RE = /^rp1\.(\d{6,7})\.([A-Za-z0-9_-]{22})$/;
const MAX_FILE = 2 * 1024 * 1024 * 1024 - 1;

class PhraseError extends Error {
  constructor(kind, message) { super(message); this.kind = kind; }
}

const check = (body) => ALPHABET[crypto.createHash('sha256').update(body, 'utf8').digest()[0] % 32];

/** همان `RecoveryPhrase.normalize`: رقمِ فارسی/عربی، کوچک/بزرگ، فاصله و خط‌تیره، O⇒0 و I/L⇒1؛ رقمِ وارسیِ نادرست ⇒ null */
function normalize(input) {
  let out = '';
  for (const raw of String(input || '')) {
    const code = raw.codePointAt(0);
    let c;
    if (code >= 0x06f0 && code <= 0x06f9) c = String(code - 0x06f0);
    else if (code >= 0x0660 && code <= 0x0669) c = String(code - 0x0660);
    else c = raw.toUpperCase();
    if (c === ' ' || c === '-' || c === '‌' || c === ' ' || c === '\t' || c === '\n' || c === '\r') continue;
    if (c === 'O') out += '0';
    else if (c === 'I' || c === 'L') out += '1';
    else if (c.length === 1 && ALPHABET.includes(c)) out += c;
    else return null;
    if (out.length > LENGTH) return null;
  }
  if (out.length !== LENGTH) return null;
  return check(out.slice(0, BODY)) === out[BODY] ? out : null;
}

const b64url = (b) => Buffer.from(b).toString('base64').replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, '');

function kidOf(salt, iterations = ITERATIONS) {
  if (!Buffer.isBuffer(salt) || salt.length !== SALT_BYTES) throw new PhraseError('bad_input', 'نمک ۱۶ بایت');
  if (!Number.isInteger(iterations) || iterations < ITERATIONS_MIN || iterations > ITERATIONS_MAX) throw new PhraseError('bad_input', 'تکرار بیرون از بازه');
  return `rp1.${iterations}.${b64url(salt)}`;
}

function parseKid(kid) {
  const m = KID_RE.exec(String(kid || ''));
  if (!m) return null;
  const iterations = Number(m[1]);
  if (iterations < ITERATIONS_MIN || iterations > ITERATIONS_MAX) return null;
  const salt = Buffer.from(m[2].replace(/-/g, '+').replace(/_/g, '/'), 'base64');
  return salt.length === SALT_BYTES ? { iterations, salt } : null;
}

const deriveKey = (canonical, salt, iterations) => crypto.pbkdf2Sync(Buffer.from(canonical, 'utf8'), salt, iterations, 32, 'sha256');

const isSealed = (b) => Buffer.isBuffer(b) && b.length >= MAGIC.length && b.subarray(0, MAGIC.length).equals(MAGIC);

/** سرآیندِ پاکت ⇐ {kid, nonceAt, bodyAt} یا PhraseError ِ `malformed`/`unsupported` */
function parse(b) {
  if (!isSealed(b)) throw new PhraseError('malformed', 'این فایل پاکتِ رمز نیست');
  if (b.length < MAGIC.length + 2) throw new PhraseError('malformed', 'سرآیندِ پاکت ناقص است');
  const ver = b[MAGIC.length];
  if (ver !== VERSION) throw new PhraseError('unsupported', `نسخهٔ پاکت ${ver} شناخته نیست`);
  const kidLen = b[MAGIC.length + 1];
  const kidAt = MAGIC.length + 2;
  const nonceAt = kidAt + kidLen;
  const bodyAt = nonceAt + NONCE_LEN;
  if (kidLen === 0 || b.length < bodyAt + TAG_LEN) throw new PhraseError('malformed', 'پاکت بریده است');
  return { kid: b.subarray(kidAt, nonceAt).toString('latin1'), nonceAt, bodyAt };
}

function sealWith(plain, kid, key, nonce = crypto.randomBytes(NONCE_LEN)) {
  const kidBytes = Buffer.from(kid, 'latin1');
  const header = Buffer.concat([MAGIC, Buffer.from([VERSION, kidBytes.length]), kidBytes, nonce]);
  const cipher = crypto.createCipheriv('aes-256-gcm', key, nonce, { authTagLength: TAG_LEN });
  cipher.setAAD(header);
  return Buffer.concat([header, cipher.update(plain), cipher.final(), cipher.getAuthTag()]);
}

/** پشتیبانِ ساده ⇒ پاکت با عبارت (نمکِ تازه) */
function seal(plain, typedPhrase, { iterations = ITERATIONS, salt = crypto.randomBytes(SALT_BYTES), nonce } = {}) {
  const canonical = normalize(typedPhrase);
  if (!canonical) throw new PhraseError('key_missing', 'عبارتِ بازیابی درست نوشته نشده است');
  const kid = kidOf(salt, iterations);
  return sealWith(plain, kid, deriveKey(canonical, salt, iterations), nonce);
}

/** پاکت ⇒ بایت‌های ساده؛ `key_missing` (عبارتِ نادرست یا پاکتِ کلیدِ دستگاه)، `auth_failed` (عبارتِ دیگر یا دست‌خورده) */
function open(sealed, typedPhrase) {
  //  همان ترتیبِ `PhraseBackup.open`: عبارت، بعد پاکت، بعد KID
  const canonical = normalize(typedPhrase);
  if (!canonical) throw new PhraseError('key_missing', 'عبارتِ بازیابی درست نوشته نشده است');
  const p = parse(sealed);
  const kdf = parseKid(p.kid);
  if (!kdf) throw new PhraseError('key_missing', 'این پشتیبان با کلیدِ دستگاه رمز شده، نه با عبارتِ بازیابی؛ فقط همان دستگاه بازش می‌کند');
  const key = deriveKey(canonical, kdf.salt, kdf.iterations);
  try {
    const d = crypto.createDecipheriv('aes-256-gcm', key, sealed.subarray(p.nonceAt, p.bodyAt), { authTagLength: TAG_LEN });
    d.setAAD(sealed.subarray(0, p.bodyAt));
    d.setAuthTag(sealed.subarray(sealed.length - TAG_LEN));
    return Buffer.concat([d.update(sealed.subarray(p.bodyAt, sealed.length - TAG_LEN)), d.final()]);
  } catch {
    throw new PhraseError('auth_failed', 'عبارت با این پشتیبان نمی‌خواند یا فایل دست‌خورده/خراب است');
  }
}

/**
 * «پشتیبانِ فقط دفتر» ِ برنامه (`BackupRestore.writeLedger`، `….json.sealed` وقتی رمزِ پشتیبانِ دستی روشن است): JSON ِ یک شیء،
 * همان فایلی که نسخهٔ وب می‌خواند ⇐ {ok, keys} یا {ok:false}.
 */
function jsonLedger(b) {
  if (!Buffer.isBuffer(b) || !b.toString('utf8', 0, 64).trimStart().startsWith('{')) return { ok: false };
  try {
    const v = JSON.parse(b.toString('utf8'));
    return v && typeof v === 'object' && !Array.isArray(v) ? { ok: true, keys: Object.keys(v).length } : { ok: false };
  } catch {
    return { ok: false };
  }
}

/**
 * ZIP ِ کامل؟ — همان قاعدهٔ `ZipTail.complete` ِ برنامه: رکوردِ پایانی (END) هست و شمارِ قلم‌های فهرستِ مرکزی با آن
 * می‌خواند (ZIP ِ بریده سرِ مرزِ قلم هم «ناقص» است) ⇐ {ok, entries} یا {ok:false, reason}.
 */
function zipComplete(b) {
  if (!Buffer.isBuffer(b) || b.length < 22) return { ok: false, reason: 'too_short' };
  const from = Math.max(0, b.length - 22 - 0xffff);
  let end = -1;
  for (let i = b.length - 22; i >= from; i--) if (b.readUInt32LE(i) === 0x06054b50) { end = i; break; }
  if (end < 0) return { ok: false, reason: 'no_end_record' };
  const entries = b.readUInt16LE(end + 10);
  const cdSize = b.readUInt32LE(end + 12);
  const cdAt = b.readUInt32LE(end + 16);
  if (cdAt + cdSize > end) return { ok: false, reason: 'directory_outside' };
  let at = cdAt;
  for (let n = 0; n < entries; n++) {
    if (at + 46 > end || b.readUInt32LE(at) !== 0x02014b50) return { ok: false, reason: 'directory_short' };
    at += 46 + b.readUInt16LE(at + 28) + b.readUInt16LE(at + 30) + b.readUInt16LE(at + 32);
  }
  return at === cdAt + cdSize ? { ok: true, entries } : { ok: false, reason: 'directory_size' };
}

/* ------------------------------------------------------------------ خطِ فرمان */

/** ورودی: یک بار باز، اندازه و نوع از همان دستگیره (fstat) و خواندن از همان ⇐ بی پنجرهٔ «بررسی سپس خواندن» (CodeQL) */
function readFile(path) {
  const fd = fs.openSync(path, 'r');
  try {
    const st = fs.fstatSync(fd);
    if (!st.isFile()) throw new PhraseError('bad_input', `فایل نیست: ${path}`);
    if (st.size > MAX_FILE) throw new PhraseError('bad_input', 'فایل بزرگ‌تر از ۲ گیگابایت است');
    return fs.readFileSync(fd);
  } finally {
    fs.closeSync(fd);
  }
}

const exists = (path) => new PhraseError('bad_input', `خروجی از پیش هست و رونویسی نمی‌شود: ${path}`);

/** پروندهٔ تازه با 'wx' (ساختنِ انحصاری، اتمی) ⇐ نوشتن و fsync؛ شکست ⇒ پروندهٔ نیمه پاک می‌شود */
function writeExclusive(path, bytes) {
  const fd = fs.openSync(path, 'wx', 0o600);
  try {
    fs.writeSync(fd, bytes);
    fs.fsyncSync(fd);
  } catch (e) {
    fs.closeSync(fd);
    fs.unlinkSync(path);
    throw e;
  }
  fs.closeSync(fd);
}

/**
 *  خروجی بی رونویسی و بی پنجرهٔ «بررسی سپس نوشتن» (CodeQL ۲۸۹): `.tmp` ِ تازه، fsync، پیوندِ سخت به نامِ نهایی
 *  (پروندهٔ موجود ⇒ EEXIST ِ اتمی)، پاک کردنِ `.tmp`. دیسکِ بی پیوندِ سخت (FAT/exFAT ِ USB) ⇒ ساختنِ انحصاری مستقیم.
 */
function writeNew(path, bytes) {
  const tmp = `${path}.tmp-${process.pid}`;
  writeExclusive(tmp, bytes);
  try {
    fs.linkSync(tmp, path);
  } catch (e) {
    if (e.code === 'EEXIST') throw exists(path);
    if (!['EPERM', 'ENOTSUP', 'EOPNOTSUPP', 'EXDEV', 'ENOSYS'].includes(e.code)) throw e;
    try {
      writeExclusive(path, bytes);
    } catch (e2) {
      throw e2.code === 'EEXIST' ? exists(path) : e2;
    }
  } finally {
    fs.unlinkSync(tmp);
  }
}

async function readPhrase() {
  if (process.env.TOHID_PHRASE) return process.env.TOHID_PHRASE;
  if (process.stdin.isTTY) process.stderr.write('عبارتِ بازیابی را بنویسید و Enter بزنید: ');
  let text = '';
  for await (const chunk of process.stdin) {
    text += chunk;
    if (text.includes('\n') || text.length > 400) break;
  }
  return text.split('\n')[0];
}

const EXIT = { bad_input: 1, key_missing: 2, auth_failed: 3, malformed: 4, unsupported: 4 };
const USAGE = 'کاربرد: phrase-backup.js check <فایل> | seal <ZIP> <خروجی> | open <پاکت> <خروجی> — عبارت از ورودیِ استاندارد';

async function main(argv) {
  const [cmd, input, output] = argv;
  if (cmd === 'check' && input) {
    const b = readFile(input);
    if (!isSealed(b)) {
      const z = zipComplete(b);
      if (!z.ok && jsonLedger(b).ok) {
        console.log('«پشتیبانِ فقط دفتر» ِ ساده (JSON، رمز ندارد)');
        return 0;
      }
      console.log(z.ok ? `پشتیبانِ ساده (رمز ندارد)، ZIP ِ کامل با ${z.entries} قلم` : `رمزدار نیست و ZIP ِ کامل هم نیست (${z.reason})`);
      return z.ok ? 0 : 4;
    }
    const p = parse(b);
    const kdf = parseKid(p.kid);
    console.log(kdf ? `رمزدار با عبارتِ بازیابی (${kdf.iterations} تکرار)` : 'رمزدار با کلیدِ دستگاه — فقط همان دستگاه بازش می‌کند');
    return 0;
  }
  if ((cmd === 'seal' || cmd === 'open') && input && output) {
    const b = readFile(input);
    const phrase = await readPhrase();
    if (cmd === 'seal') {
      if (isSealed(b)) throw new PhraseError('bad_input', 'این فایل از پیش رمزدار است');
      const z = zipComplete(b);
      if (!z.ok) throw new PhraseError('malformed', `پشتیبانِ ساده ناقص یا خراب است (${z.reason}); رمز نشد`);
      const sealed = seal(b, phrase);
      //  پیش از نوشتن با همان عبارت باز می‌شود؟ (عبارتی که درست نوشته نشد پاکتِ بی‌کلید نمی‌سازد)
      if (!open(sealed, phrase).equals(b)) throw new PhraseError('auth_failed', 'پاکتِ ساخته‌شده باز نشد؛ چیزی نوشته نشد');
      writeNew(output, sealed);
      console.log(`رمز شد: ${z.entries} قلم، ${sealed.length} بایت`);
      return 0;
    }
    const plain = open(b, phrase);
    const z = zipComplete(plain);
    const ledger = z.ok ? null : jsonLedger(plain);
    writeNew(output, plain);
    if (z.ok) console.log(`باز شد: ZIP ِ کامل با ${z.entries} قلم`);
    else if (ledger.ok) console.log('باز شد: «پشتیبانِ فقط دفتر» (JSON)');
    else console.log(`باز شد ولی نه ZIP ِ کامل است نه دفترِ JSON (${z.reason}) — نوشته شد تا بررسی شود`);
    return z.ok || ledger.ok ? 0 : 4;
  }
  console.error(USAGE);
  return 1;
}

if (require.main === module) {
  main(process.argv.slice(2)).then((code) => { process.exitCode = code; }, (err) => {
    console.error(err instanceof PhraseError ? err.message : `خطا: ${err.code || err.message}`);
    process.exitCode = err instanceof PhraseError ? EXIT[err.kind] || 1 : 1;
  });
}

module.exports = {
  PhraseError, normalize, kidOf, parseKid, deriveKey, isSealed, parse, seal, sealWith, open, zipComplete, jsonLedger, readFile, writeNew, main,
};
