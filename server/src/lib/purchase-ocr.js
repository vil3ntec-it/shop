'use strict';
/**
 * PUR-12 / AI-10 — عکسِ فاکتورِ کاغذیِ تأمین‌کننده ⇒ خط‌های متن، با Tesseract ِ نصب‌شده روی **همین سرور**.
 * رایگان و خودمیزبان: نه سرویسِ ابری، نه API ِ پولی، نه مدلِ هوشِ مصنوعی (D-16). فقط متن برمی‌گردد؛ ردیف‌ها، تطبیقِ
 * کالا و تأییدِ ردیف‌به‌ردیف کارِ برنامه است (`core/purchase/InvoiceOcr.kt`) — هیچ چیز این‌جا ثبت نمی‌شود.
 *
 *   status()            ⇒ { installed, lang, langs, missing[] } — `tesseract --list-langs`، کشِ کوتاه (نصبِ تازه بی ری‌استارت دیده می‌شود)
 *   check(buf)          ⇒ info ِ `image-bytes.inspect` — نوع از محتوا (نه پسوند)، سقفِ حجم و ابعاد
 *   read(buf)           ⇒ { lines: [{ text, conf }], lang, ms }
 *   tsvLines(tsv)       ⇒ همان خط‌ها از خروجیِ TSV (خالص، برای آزمون)
 *
 * ⛔ پرچمِ خاموشِ `shop.purchase_ocr` (دکان یا `all`): «پس از انتشار» (RELEASE-1-fa.md) — خاموش ⇒ دکمه نیست و درِ
 *    نوشتن ۴۰۳ `purchase_ocr_disabled`؛ بدنه پیش از پرچم خوانده نمی‌شود.
 * ⛔ عکس دوباره ساخته می‌شود: رمزگشایی در رشتهٔ جدا (`purchase-ocr-thread.js`، سقفِ حافظه و مهلت) ⇒ جهتِ EXIF روی
 *    پیکسل ⇒ کوچک تا `OCR_SIDE` ⇒ خاکستریِ PGM (بی هیچ متادیتا: EXIF/GPS/XMP همان‌جا می‌افتند). Tesseract هرگز
 *    بایتِ کاربر را نمی‌بیند، فقط همان PGM ِ ساختهٔ ما را.
 * ⛔ `spawn` با آرایهٔ آرگومان، هرگز پوسته؛ محیطِ کمینه (هیچ رازِ `.env` به فرزند نمی‌رسد)؛ `OMP_THREAD_LIMIT=1`
 *    (بی آن Tesseract ِ ۵ روی چند هسته گاه تا دقیقه‌ها می‌ماند — در همین سیزن دیده شد)؛ مهلت ⇒ SIGKILL؛ سقفِ خروجی.
 * ⛔ همزمانی ۱ تا ۲ (`PURCHASE_OCR_WORKERS`، پیش‌فرض ۱)؛ پر ⇒ ۵۰۳ `ocr_busy` با Retry-After، بی صف.
 * ⛔ پوشهٔ موقتِ خصوصی (`mkdtemp`، ۰۷۰۰) و فایل ۰۶۰۰، همیشه در `finally` پاک؛ هیچ متن یا عکسی پس از پاسخ نمی‌ماند
 *    و هیچ‌کدام در لاگ نمی‌آید (خطاها فقط کد دارند، نه stderr ِ Tesseract).
 * ⛔ نبودِ برنامه یا دادهٔ فارسی ⇒ خطای نوع‌دار (`ocr_not_installed` / `ocr_lang_missing`) و `missing` ِ بسته‌ها؛ برنامه
 *    پیامِ «صاحبِ سرور tesseract-ocr و tesseract-ocr-fas را نصب کند» را نشان می‌دهد.
 */
const { spawn } = require('child_process');
const fsp = require('fs/promises');
const fs = require('fs');
const os = require('os');
const path = require('path');
const { Worker } = require('worker_threads');
const bytes = require('./image-bytes');
const { flagOf } = require('./orders');
const { ApiError, badRequest } = require('../middleware/errors');

const FLAG = 'shop.purchase_ocr';
const int = (v, def, lo, hi) => Math.min(hi, Math.max(lo, Math.floor(Number(v)) || def));
const LIMITS = Object.freeze({
  MAX_BYTES: 6 * 1024 * 1024,     // سقفِ بدنه؛ برنامه JPEG ِ حداکثر ۲۰۰۰ پیکسلی و زیرِ ۲ مگابایت می‌فرستد
  MAX_SIDE: 8000,                 // ضلعِ سرآیند (image-bytes خودش ۱۰٬۰۰۰ و ۴۰ مگاپیکسل را می‌بندد)
  MAX_PIXELS: 16000000,           // همان سقفِ رمزگشای image-codec
  MIN_SIDE: 200,                  // کوچک‌تر ⇒ متنی خوانده نمی‌شود
  OCR_SIDE: 2400,                 // ضلعِ بلندِ عکسی که به Tesseract می‌رسد (هرگز بزرگ‌تر نمی‌شود)
  //  مهلتِ Tesseract: زیرِ مهلتِ خواندنِ برنامه (`ApiConfig.READ_TIMEOUT_MS` ِ ۳۰ ثانیه) تا خطای نوع‌دار به برنامه برسد
  OCR_TIMEOUT_MS: int(process.env.PURCHASE_OCR_TIMEOUT_MS, 24000, 1000, 24000),
  PREP_TIMEOUT_MS: 4000,
  PREP_HEAP_MB: 256,
  STDOUT_MAX: 2 * 1024 * 1024,
  LINES_MAX: 400,
  LINE_CHARS: 300,
  LANGS_TTL_MS: 60000,
  LANGS_TIMEOUT_MS: 5000,
  WORKERS: int(process.env.PURCHASE_OCR_WORKERS, 1, 1, 2),
});
/** بسته‌هایی که صاحبِ سرور نصب می‌کند (نامِ Debian/Ubuntu؛ Alpine: tesseract-ocr و tesseract-ocr-data-fas) */
const PACKAGES = Object.freeze({ bin: 'tesseract-ocr', fas: 'tesseract-ocr-fas' });

const enabled = (shopId) => flagOf(shopId, FLAG);

/** مسیرِ برنامه: `TESSERACT_BIN`، وگرنه جای پیش‌فرضِ نصابِ ویندوز (سرورِ خانگی)، وگرنه `tesseract` ِ PATH */
function binary() {
  const env = String(process.env.TESSERACT_BIN || '').trim();
  if (env) return env;
  if (process.platform === 'win32') {
    const win = path.join(process.env.ProgramFiles || 'C:\\Program Files', 'Tesseract-OCR', 'tesseract.exe');
    if (fs.existsSync(win)) return win;
  }
  return 'tesseract';
}

/** محیطِ فرزند: فقط آنچه Tesseract لازم دارد — نه DATABASE_URL، نه کلید، نه رازِ دیگری از `.env` */
function childEnv() {
  const env = { OMP_THREAD_LIMIT: '1', LANG: 'C.UTF-8', LC_ALL: 'C.UTF-8' };
  for (const k of ['PATH', 'Path', 'SystemRoot', 'TESSDATA_PREFIX']) if (process.env[k]) env[k] = process.env[k];
  return env;
}

/**
 * اجرای یک فرمان با مهلت و سقفِ خروجی. ⇒ { code, out } یا Error با `code`:
 * `ENOENT` (برنامه نیست)، `timeout`، `too_much_output`، `spawn_failed`.
 */
function run(args, { timeoutMs, maxOut = LIMITS.STDOUT_MAX } = {}) {
  return new Promise((resolve, reject) => {
    let child;
    try {
      child = spawn(binary(), args, { shell: false, windowsHide: true, env: childEnv(), stdio: ['ignore', 'pipe', 'pipe'] });
    } catch (e) {
      reject(Object.assign(new Error('spawn'), { code: e && e.code === 'ENOENT' ? 'ENOENT' : 'spawn_failed' }));
      return;
    }
    const chunks = [];
    let size = 0, why = null, done = false;
    const stop = (reason) => { if (!why) { why = reason; child.kill('SIGKILL'); } };
    const timer = setTimeout(() => stop('timeout'), timeoutMs);
    child.stdout.on('data', (b) => {
      size += b.length;
      if (size > maxOut) stop('too_much_output'); else chunks.push(b);
    });
    child.stderr.on('data', () => {});   // ⛔ stderr نه نگه داشته می‌شود نه در لاگ می‌آید
    const finish = (fn, v) => { if (done) return; done = true; clearTimeout(timer); fn(v); };
    child.once('error', (e) => finish(reject, Object.assign(new Error('spawn'), { code: e && e.code === 'ENOENT' ? 'ENOENT' : 'spawn_failed' })));
    child.once('close', (code) => {
      if (why) finish(reject, Object.assign(new Error(why), { code: why }));
      else finish(resolve, { code, out: Buffer.concat(chunks).toString('utf8') });
    });
  });
}

let cache = null;   // { at, value }
/** خواننده‌ای که آزمون جایش می‌گذارد (`_setStatus`)؛ پیش‌فرض همان `tesseract --list-langs` */
async function probe() {
  try {
    const r = await run(['--list-langs'], { timeoutMs: LIMITS.LANGS_TIMEOUT_MS, maxOut: 64 * 1024 });
    const langs = r.out.split(/\r?\n/).map((s) => s.trim())
      .filter((s) => s && !/^list of available/i.test(s) && /^[A-Za-z0-9_]+$/.test(s));
    return { installed: r.code === 0 || langs.length > 0, langs };
  } catch (e) {
    return { installed: false, langs: [], timeout: e.code === 'timeout' };
  }
}

/** حالِ موتور — کشِ `LANGS_TTL_MS`؛ `fas` لازم است، `eng` اگر بود کنارش (نام‌های لاتینِ کالا) */
async function status({ fresh = false } = {}) {
  const t = Date.now();
  if (!fresh && cache && t - cache.at < LIMITS.LANGS_TTL_MS) return cache.value;
  const p = await probe();
  const fas = p.langs.includes('fas');
  const value = {
    installed: p.installed,
    langs: p.langs.filter((l) => l === 'fas' || l === 'eng'),
    //  `PURCHASE_OCR_LANG=fas` ⇒ فقط فارسی (سرخط‌های فارسی کمی بهتر؛ نامِ لاتینِ کالا بدتر) — پیش‌فرض `fas+eng` اگر eng بود
    lang: fas ? (p.langs.includes('eng') && String(process.env.PURCHASE_OCR_LANG || '').trim() !== 'fas' ? 'fas+eng' : 'fas') : null,
    missing: !p.installed ? [PACKAGES.bin, PACKAGES.fas] : fas ? [] : [PACKAGES.fas],
  };
  cache = { at: t, value };
  return value;
}

const notInstalled = (missing) => Object.assign(
  new ApiError(503, 'ocr_not_installed', 'خواندنِ عکسِ فاکتور روی این سرور نصب نیست؛ صاحبِ سرور باید tesseract-ocr و tesseract-ocr-fas را نصب کند'),
  { data: { missing } });
const langMissing = (missing) => Object.assign(
  new ApiError(503, 'ocr_lang_missing', 'دادهٔ زبانِ فارسیِ خواندنِ عکس روی این سرور نیست؛ صاحبِ سرور باید tesseract-ocr-fas را نصب کند'),
  { data: { missing } });

/** سنجشِ بایت‌ها پیش از هر کارِ سنگین — نوع از محتوا، حجم، ابعاد */
function check(buf) {
  if (typeof buf !== 'object' || buf === null || Array.isArray(buf) || !Buffer.isBuffer(buf) || !buf.length) {
    throw badRequest('عکس خالی است', 'image_empty');
  }
  if (buf.length > LIMITS.MAX_BYTES) {
    throw new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${Math.round(LIMITS.MAX_BYTES / 1024 / 1024)} مگابایت است`);
  }
  let info;
  try { info = bytes.inspect(buf); } catch (e) {
    if (e && e.code === 'image_type') throw new ApiError(415, 'image_type', 'فقط عکسِ JPG یا PNG — نوعِ فایل از محتوا سنجیده می‌شود');
    throw e;
  }
  if (info.mime !== 'image/jpeg' && info.mime !== 'image/png') {
    throw new ApiError(415, 'image_type', 'فقط عکسِ JPG یا PNG — نوعِ فایل از محتوا سنجیده می‌شود');
  }
  if (Math.max(info.width, info.height) > LIMITS.MAX_SIDE || info.width * info.height > LIMITS.MAX_PIXELS) {
    throw new ApiError(413, 'image_too_large_dimensions', 'ابعادِ عکس بیش از حد بزرگ است');
  }
  if (Math.min(info.width, info.height) < LIMITS.MIN_SIDE) {
    throw new ApiError(422, 'image_too_small', 'عکس خیلی کوچک است و متنش خوانده نمی‌شود؛ از نزدیک‌تر عکس بگیرید');
  }
  return info;
}

/** بازسازی در رشتهٔ جدا ⇒ PGM ِ خاکستری (Buffer) */
let prepImpl = prepare;
function prepare(buf, info) {
  return new Promise((resolve, reject) => {
    let w;
    try {
      w = new Worker(path.join(__dirname, 'purchase-ocr-thread.js'), {
        workerData: { buf, info: { mime: info.mime, width: info.width, height: info.height, orientation: info.orientation }, side: LIMITS.OCR_SIDE },
        resourceLimits: { maxOldGenerationSizeMb: LIMITS.PREP_HEAP_MB },
      });
    } catch { reject(new ApiError(503, 'ocr_failed', 'عکس آماده نشد؛ دوباره بفرستید')); return; }
    let done = false;
    const end = (fn, v) => { if (done) return; done = true; clearTimeout(timer); w.terminate().catch(() => {}); fn(v); };
    const timer = setTimeout(() => end(reject, new ApiError(503, 'ocr_timeout', 'آماده کردنِ عکس دیر شد؛ عکسِ کوچک‌تری بفرستید')), LIMITS.PREP_TIMEOUT_MS);
    w.once('message', (m) => {
      if (!m || !m.ok) {
        end(reject, m && m.code === 'image_unsupported'
          ? new ApiError(415, 'image_unsupported', 'این عکس باز نشد (JPEG ِ progressive یا PNG ِ ویژه)؛ عکسِ دیگری بگیرید')
          : new ApiError(503, 'ocr_failed', 'عکس آماده نشد؛ دوباره بفرستید'));
        return;
      }
      end(resolve, Buffer.from(m.pgm.buffer, m.pgm.byteOffset, m.pgm.byteLength));
    });
    w.once('error', () => end(reject, new ApiError(503, 'ocr_failed', 'عکس آماده نشد؛ دوباره بفرستید')));
    w.once('exit', () => end(reject, new ApiError(503, 'ocr_failed', 'عکس آماده نشد؛ دوباره بفرستید')));
  });
}

/** نویسه‌های کنترلی و جهت‌نما (LRM/RLM/…) از متنِ خوانده‌شده می‌افتند؛ فاصله‌ها یکی */
function clean(s) {
  return String(s || '')
    .replace(/[\u0000-\u001f\u007f\u200e\u200f\u202a-\u202e\u2066-\u2069\ufeff]/g, '')
    .replace(/\s+/g, ' ').trim();
}

/**
 * خروجیِ `tsv` ِ Tesseract ⇒ خط‌ها به ترتیبِ خواندن (همان ترتیبِ ردیف‌های TSV؛ راست‌به‌چپ همان ترتیبِ منطقی است).
 * اطمینانِ خط = میانگینِ اطمینانِ واژه‌ها (۰ تا ۱۰۰).
 */
function tsvLines(tsv) {
  const order = [];
  const byKey = new Map();
  for (const row of String(tsv || '').split(/\r?\n/)) {
    const c = row.split('\t');
    if (c.length < 12 || c[0] !== '5') continue;
    const text = clean(c.slice(11).join(' '));
    const conf = Number(c[10]);
    if (!text) continue;
    const key = `${c[1]}:${c[2]}:${c[3]}:${c[4]}`;
    let l = byKey.get(key);
    if (!l) { l = { words: [], confs: [] }; byKey.set(key, l); order.push(key); }
    l.words.push(text);
    if (Number.isFinite(conf) && conf >= 0) l.confs.push(conf);
  }
  const out = [];
  for (const k of order) {
    const l = byKey.get(k);
    const text = l.words.join(' ').slice(0, LIMITS.LINE_CHARS);
    if (!text) continue;
    const conf = l.confs.length ? Math.round(l.confs.reduce((a, b) => a + b, 0) / l.confs.length) : 0;
    out.push({ text, conf: Math.max(0, Math.min(100, conf)) });
    if (out.length >= LIMITS.LINES_MAX) break;
  }
  return out;
}

let active = 0;
let ocrTimeoutMs = LIMITS.OCR_TIMEOUT_MS;
const busy = () => Object.assign(new ApiError(503, 'ocr_busy', 'سرور الان فاکتورِ دیگری را می‌خواند؛ چند ثانیهٔ دیگر دوباره بفرستید'), { retryAfter: 5 });

/**
 * یک عکس ⇒ خط‌ها. ترتیب: سنجش (سبک) ⇒ موتور هست؟ ⇒ جای خالیِ همزمانی ⇒ بازسازی ⇒ Tesseract ⇒ پاک کردنِ پوشه.
 * هیچ متن یا عکسی نگه داشته یا در لاگ نوشته نمی‌شود.
 */
async function read(buf) {
  const info = check(buf);
  const st = await status();
  if (!st.installed) throw notInstalled(st.missing);
  if (!st.lang) throw langMissing(st.missing);
  if (active >= LIMITS.WORKERS) throw busy();
  active++;
  let dir = null;
  const t0 = Date.now();
  try {
    const pgm = await prepImpl(buf, info);
    dir = await fsp.mkdtemp(path.join(os.tmpdir(), 'tohid-ocr-'));
    const file = path.join(dir, 'page.pgm');
    await fsp.writeFile(file, pgm, { mode: 0o600, flag: 'wx' });
    let r;
    try {
      // تنظیم مستقیم، حتی وقتی پوشهٔ TESSDATA_PREFIX فقط فایل‌های traineddata دارد.
      r = await run([
        file, 'stdout', '-l', st.lang, '--psm', '6',
        '-c', 'preserve_interword_spaces=1', '-c', 'tessedit_create_tsv=1',
      ], { timeoutMs: ocrTimeoutMs });
    } catch (e) {
      if (e.code === 'ENOENT') { cache = null; throw notInstalled([PACKAGES.bin, PACKAGES.fas]); }
      if (e.code === 'timeout') throw new ApiError(503, 'ocr_timeout', 'خواندنِ عکس بیش از حد طول کشید؛ عکسِ روشن‌تر و صاف‌تری بگیرید');
      throw new ApiError(503, 'ocr_failed', 'خواندنِ عکس نشد؛ دوباره بفرستید');
    }
    if (r.code !== 0) { cache = null; throw new ApiError(503, 'ocr_failed', 'خواندنِ عکس نشد؛ دوباره بفرستید'); }
    return { lines: tsvLines(r.out), lang: st.lang, ms: Date.now() - t0 };
  } finally {
    active--;
    if (dir) await fsp.rm(dir, { recursive: true, force: true, maxRetries: 3 }).catch(() => {});
  }
}

/** درهای آزمون — فقط از `test/` */
const _test = {
  setPrepare(fn) { prepImpl = fn || prepare; },
  setTimeoutMs(ms) { ocrTimeoutMs = ms; },
  reset() { cache = null; prepImpl = prepare; ocrTimeoutMs = LIMITS.OCR_TIMEOUT_MS; },
  active: () => active,
};

module.exports = { FLAG, LIMITS, PACKAGES, enabled, status, check, read, tsvLines, binary, _test };
