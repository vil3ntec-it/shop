'use strict';
/**
 * کلیدِ یکتایی (Idempotency-Key) — F0-14.
 *
 * ⛔ قاعده: کارِ پولیِ پنلِ مدیر که با کلید آمده، با همان کلید فقط **یک
 * بار** انجام می‌شود. کلید زیرِ قفلِ خودش (`pg_advisory_xact_lock`) و در
 * **همان تراکنشِ** خودِ کار ثبت می‌شود؛ پس دو درخواستِ هم‌زمان با یک کلید
 * پشتِ هم می‌مانند و دومی پاسخِ اولی را می‌گیرد.
 *
 *   • همان کلید + همان بدنه  ⇒ پاسخِ اول (سرآیندِ `Idempotent-Replayed: true`)
 *   • همان کلید + بدنهٔ دیگر ⇒ ۴۰۹ `idempotency_key_reused`، بی هیچ تغییری
 *   • بی‌کلید               ⇒ رفتارِ قبلی (قاعدهٔ سازگاری: پنلِ قدیمیِ باز مانده)
 */
const crypto = require('crypto');
const { one, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');

const HEADER = 'idempotency-key';
const KEY_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const KEEP_MS = 7 * 24 * 60 * 60 * 1000;

/** رشتهٔ پایدارِ JSON (کلیدها مرتب) برای اثرِ انگشتِ درخواست. */
function stable(v) {
  if (Array.isArray(v)) return `[${v.map(stable).join(',')}]`;
  if (v && typeof v === 'object') {
    return `{${Object.keys(v).sort().map(k => `${JSON.stringify(k)}:${stable(v[k])}`).join(',')}}`;
  }
  return JSON.stringify(v === undefined ? null : v);
}

/**
 * کلیدِ درخواست، یا null وقتی نیامده.
 * @param {import('express').Request} req
 * @param {string} scope  دامنهٔ کلید (کار + بخش + مدیر)
 * @returns {{scope:string,key:string,hash:string}|null}
 */
function fromRequest(req, scope) {
  const raw = typeof req.get === 'function' ? req.get(HEADER) : req.headers?.[HEADER];
  if (raw === undefined || raw === null || raw === '') return null;
  const key = String(raw).trim();
  if (!KEY_RE.test(key)) throw badRequest('کلیدِ یکتایی نامعتبر است', 'bad_idempotency_key');
  const hash = crypto.createHash('sha256')
    .update(`${req.method} ${req.baseUrl || ''}${req.path || ''}\n${stable(req.body || {})}`)
    .digest('hex');
  return { scope, key, hash };
}

/** ردیفِ ثبت‌شدهٔ همین کلید؛ بدنهٔ دیگر با همان کلید ⇒ ۴۰۹. */
async function find(idem, run = one) {
  const row = await run('SELECT * FROM idempotency_keys WHERE scope=$1 AND key=$2', [idem.scope, idem.key]);
  if (!row) return null;
  if (row.request_hash !== idem.hash) {
    throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
  }
  return row;
}

/** قفلِ همین کلید تا پایانِ تراکنش. `c` همان client ِ تراکنش است. */
async function lock(c, idem) {
  await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`idem:${idem.scope}:${idem.key}`]);
}

/** خواندن با client ِ تراکنش (به شکلِ `one`). */
function viaClient(c) {
  return async (text, params) => (await c.query(text, params)).rows[0] || null;
}

/** ثبتِ نتیجه در همان تراکنش (`c`). */
async function save(c, idem, { tenantId = '', resultId = '', response = {} } = {}) {
  const t = now();
  await c.query(
    `INSERT INTO idempotency_keys (scope, key, request_hash, tenant_id, result_id, response, created_at)
     VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7)`,
    [idem.scope, idem.key, idem.hash, tenantId, resultId, JSON.stringify(response), t]
  );
  //  کلید فقط برای دقیقه‌های دو بار کلیک و قطعِ شبکه لازم است؛ هفت روز بس است
  await c.query('DELETE FROM idempotency_keys WHERE created_at < $1', [t - KEEP_MS]);
}

module.exports = { HEADER, KEY_RE, KEEP_MS, fromRequest, find, lock, save, viaClient, stable };
