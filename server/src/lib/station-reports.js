'use strict';
/**
 * عکس و PDF از **خودِ** برنامهٔ کامپیوترِ پمپ، برای بات — بی ذخیره روی سرور.
 *
 * ── خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۲۱) ─────────────────────────────────
 * «همان عکس‌هایی باشد که در برنامه است… مخزن همان باشد… حسابِ فلانی را
 *  بگیرم با PDF و کیو‌آرش… کیفیت افت نکند» و «در سرور ذخیره نشود».
 *
 *   بات ──request(stationId, kind, arg)──▶ صفِ حافظه (همین پروسه)
 *   برنامهٔ کامپیوتر ──GET /api/pump/device/rate (reqs)──▶ همان درخواست
 *                    ──POST /api/pump/device/report/:id──▶ فایل‌ها ⇒ بات ⇒ تلگرام
 *
 * ⛔ **هیچ چیز نوشته نمی‌شود** — نه دیتابیس، نه دیسک. فایل از بدنهٔ درخواست
 *    مستقیم به همان `Promise`ی می‌رسد که بات منتظرش است و بعد رها می‌شود.
 * ⛔ هر درخواست مالِ **یک** پمپ است و فقط با توکنِ دستگاهِ همان پمپ برداشته و
 *    جواب داده می‌شود (`stationId` از توکن، نه از بدنه).
 * ⛔ نقاشیِ تازه این‌جا نیست: عکس و PDF را خودِ برنامه می‌کشد، پس هرگز با
 *    برنامه فرق نمی‌کند. سرور فقط پیام‌رسان است.
 */
const crypto = require('crypto');

/** کارهایی که برنامه می‌شناسد. */
const KINDS = new Set(['tanks', 'debtor']);
/** برنامه تا این مدت جواب نداد ⇒ «کامپیوتر جواب نداد». */
const TIMEOUT_MS = 45_000;
/** سقفِ درخواست‌های بازِ یک پمپ — تا کسی با دکمه‌زدنِ پیاپی صف نسازد. */
const MAX_OPEN = 4;
/** سقفِ فایل‌ها: شمار و حجمِ هر کدام (بایت، پس از base64). */
const MAX_FILES = 4;
const MAX_BYTES = 6 * 1024 * 1024;
const MIMES = new Set(['image/png', 'image/jpeg', 'application/pdf']);

/** id ⇒ { id, stationId, kind, arg, at, taken, resolve, reject, timer } */
const open = new Map();

function wake(stationId) {
  try { require('./station-rates').wake(stationId); } catch { /* بی شنونده */ }
}

function countOf(stationId) {
  let n = 0;
  for (const r of open.values()) if (r.stationId === stationId) n++;
  return n;
}

function finish(id) {
  const r = open.get(id);
  if (!r) return null;
  open.delete(id);
  clearTimeout(r.timer);
  return r;
}

/**
 * درخواست از برنامهٔ همین پمپ ⇒ ‎{ files: [{name, mime, data: Buffer}], caption }‎.
 * خطاها `code` دارند: `busy` (صفِ پر)، `timeout` (کامپیوتر جواب نداد)،
 * `failed` (برنامه گفت نشد — `message` همان پیامِ برنامه).
 */
function request(stationId, kind, arg = {}, { timeoutMs = TIMEOUT_MS } = {}) {
  if (!KINDS.has(kind)) return Promise.reject(Object.assign(new Error('کارِ ناشناخته'), { code: 'bad_kind' }));
  if (countOf(stationId) >= MAX_OPEN) {
    return Promise.reject(Object.assign(new Error('چند درخواست هنوز در راه است؛ کمی صبر کنید'), { code: 'busy' }));
  }
  const id = `rq_${crypto.randomBytes(9).toString('base64url')}`;
  return new Promise((resolve, reject) => {
    const timer = setTimeout(() => {
      if (finish(id)) reject(Object.assign(new Error('کامپیوترِ پمپ جواب نداد'), { code: 'timeout' }));
    }, Math.max(1000, timeoutMs));
    timer.unref?.();
    open.set(id, { id, stationId, kind, arg: arg && typeof arg === 'object' ? arg : {}, at: Date.now(), taken: 0, resolve, reject, timer });
    //  برنامهٔ منتظرِ همین پمپ همین حالا جواب می‌گیرد
    wake(stationId);
  });
}

/** درخواست‌های برداشته‌نشدهٔ همین پمپ — هر کدام فقط یک بار داده می‌شود. */
function pending(stationId) {
  const out = [];
  for (const r of open.values()) {
    if (r.stationId !== stationId || r.taken) continue;
    r.taken = Date.now();
    out.push({ id: r.id, kind: r.kind, arg: r.arg });
  }
  return out;
}

/** چیزی برای این پمپ در صف هست؟ (بی برداشتن) */
function hasPending(stationId) {
  for (const r of open.values()) if (r.stationId === stationId && !r.taken) return true;
  return false;
}

function cleanName(s, mime) {
  const ext = mime === 'application/pdf' ? '.pdf' : mime === 'image/jpeg' ? '.jpg' : '.png';
  const base = String(s || 'file').replace(/[\\/:*?"<>|\u0000-\u001f]/g, '').trim().slice(0, 80) || 'file';
  return base.toLowerCase().endsWith(ext) ? base : base + ext;
}

/**
 * جوابِ برنامه. ‎body = { ok, error?, caption?, files: [{name, mime, b64}] }‎.
 * ⇒ `true` اگر درخواستی بود و به بات رسید؛ `false` اگر کهنه یا مالِ پمپِ دیگری بود.
 * ⚠️ فایلِ بدشکل خطای `bad` می‌دهد و درخواست باز می‌ماند تا مهلتش.
 */
function complete(stationId, id, body = {}) {
  const r = open.get(String(id || ''));
  if (!r || r.stationId !== stationId) return false;
  if (body.ok === false) {
    finish(r.id);
    r.reject(Object.assign(new Error(String(body.error || 'برنامه نتوانست بسازد').slice(0, 200)), { code: 'failed' }));
    return true;
  }
  const raw = Array.isArray(body.files) ? body.files : [];
  if (!raw.length || raw.length > MAX_FILES) throw Object.assign(new Error('فایلی نیامد'), { code: 'bad' });
  const files = raw.map((f) => {
    const mime = String(f?.mime || '');
    if (!MIMES.has(mime)) throw Object.assign(new Error('نوعِ فایل پذیرفته نیست'), { code: 'bad' });
    const data = Buffer.from(String(f?.b64 || ''), 'base64');
    if (!data.length || data.length > MAX_BYTES) throw Object.assign(new Error('فایل خالی یا بیش از حد بزرگ است'), { code: 'bad' });
    //  ⛔ نوعِ واقعی از سرِ خودِ فایل، نه از برچسبش
    const png = data[0] === 0x89 && data[1] === 0x50 && data[2] === 0x4e && data[3] === 0x47;
    const jpg = data[0] === 0xff && data[1] === 0xd8;
    const pdf = data.slice(0, 5).toString('latin1') === '%PDF-';
    if ((mime === 'image/png' && !png) || (mime === 'image/jpeg' && !jpg) || (mime === 'application/pdf' && !pdf)) {
      throw Object.assign(new Error('محتوای فایل با نوعش نمی‌خواند'), { code: 'bad' });
    }
    return { name: cleanName(f?.name, mime), mime, data };
  });
  finish(r.id);
  r.resolve({ files, caption: String(body.caption || '').slice(0, 900), extra: body.extra && typeof body.extra === 'object' ? body.extra : {} });
  return true;
}

/** فقط آزمون: صف را خالی می‌کند. */
function _reset() {
  for (const id of [...open.keys()]) {
    const r = finish(id);
    r?.reject(Object.assign(new Error('reset'), { code: 'reset' }));
  }
}

module.exports = { request, pending, hasPending, complete, KINDS, TIMEOUT_MS, MAX_OPEN, MAX_FILES, MAX_BYTES, _reset };
