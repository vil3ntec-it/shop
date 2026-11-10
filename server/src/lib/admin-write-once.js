'use strict';
/**
 * «یک بار» برای نوشتن‌های مدیر با `Idempotency-Key` — درخواستِ پنلِ مرکزی (۱.۵۰.۷۵، ممیزی §۳-۴)، همان الگوی
 * `lib/sync-restore-once.js` و همان `lib/idempotency.js`:
 *   • همان کلید + همان درخواست (روش + نشانی + بدنه) ⇒ همان پاسخ با `Idempotent-Replayed: true`، بی کارِ دوم؛
 *   • همان کلید + درخواستِ دیگر (بدنهٔ دیگر یا **درِ دیگر**) ⇒ ۴۰۹ `idempotency_key_reused`، بی هیچ تغییری؛
 *   • همان کلید هم‌زمان ⇒ دومی ۴۰۹ `idempotency_in_progress` (نشانِ پایگاه `admin_write_claims`، مهاجرتِ ۵۰۰ — نه حافظهٔ
 *     فرآیند، پس دو نمونهٔ سرور هم)؛
 *   • شکستِ روشن ⇒ نشان برداشته و هیچ پاسخی ثبت نمی‌شود (همان کلید دوباره قابلِ تلاش)؛
 *   • بی‌کلید ⇒ همان رفتارِ پیشین (کنسولِ وبِ خودِ سرور و پنلِ کهنه).
 *
 * چرا سه گام و نه یک تراکنش: کارها تراکنشِ خودشان را دارند (`device-block`، `image-bank`) یا تراکنش نیستند (pg_dump)؛
 * تراکنشِ تودرتو روی PGlite (یک اتصال) می‌ایستد و تراکنشِ بازِ چنددقیقه‌ای سقفِ `PG_IDLE_TX_TIMEOUT_MS` را می‌خورد.
 *
 * ⛔ کلید به مدیر **و** «انجام‌دهنده»ٔ کلیدِ سرویس بسته است (پل یک «مدیر» است برای همهٔ کاربرانِ پنل): کلیدِ مدیرِ دیگر
 *   هرگز پاسخِ کسِ دیگری را بازپخش نمی‌کند. یک دامنه برای همهٔ این درها ⇒ کلید مالِ همان یک کار است.
 * ⛔ نقش، دلیل و شکلِ ورودی **پیش از** این سنجیده می‌شوند (درخواستِ ردشده نشانی نمی‌گذارد).
 */
const crypto = require('crypto');
const { tx, query, now } = require('../db');
const idempotency = require('./idempotency');
const { conflict } = require('../middleware/errors');

//  pg_dump ِ بزرگ چند دقیقه است؛ نشانِ ماندهٔ فرآیندِ مُرده فقط **همان** کلید را تا این‌قدر می‌بندد
const STALE_MS = 30 * 60 * 1000;
const SCOPE = 'admin-write';

/** دامنهٔ کلید: مدیر (یا کلیدِ سرویس) + انجام‌دهنده. */
function scopeOf(req) {
  return `${SCOPE}:${req.admin.id}:${req.actor?.id || ''}`;
}

/**
 * @param {import('express').Request} req   مدیر (`req.admin`) و سرآیندِ اختیاریِ `Idempotency-Key`
 * @param {{ tenantId?: string, resultId?: string }} meta  برای ردیفِ کلید (پاک شدن با حذفِ حساب)
 * @param {() => Promise<object>} run  خودِ کار؛ پاسخِ JSON ِ همان در
 * @returns {Promise<{ response: object, replayed: boolean }>}
 */
async function writeOnce(req, { tenantId = '', resultId = '' } = {}, run) {
  const idem = idempotency.fromRequest(req, scopeOf(req));
  if (!idem) return { response: await run(), replayed: false };
  const token = crypto.randomBytes(12).toString('hex');
  const prev = await tx(async (c) => {
    await idempotency.lock(c, idem);
    const row = await idempotency.find(idem, idempotency.viaClient(c));
    if (row) return row;
    const t = now();
    await c.query('DELETE FROM admin_write_claims WHERE claimed_at < $1', [t - STALE_MS]);
    const held = (await c.query('SELECT request_hash FROM admin_write_claims WHERE scope=$1 AND key=$2', [idem.scope, idem.key])).rows[0];
    if (held) {
      if (held.request_hash !== idem.hash) {
        throw conflict('این کلیدِ یکتایی پیش‌تر برای درخواستِ دیگری به کار رفته است', 'idempotency_key_reused');
      }
      throw conflict('همین درخواست با همین کلید همین حالا در راه است؛ کمی بعد همان کلید را بفرستید', 'idempotency_in_progress');
    }
    await c.query('INSERT INTO admin_write_claims (scope, key, token, request_hash, claimed_at) VALUES ($1,$2,$3,$4,$5)',
      [idem.scope, idem.key, token, idem.hash, t]);
    return null;
  });
  if (prev) return { response: prev.response || {}, replayed: true };

  const release = (c) => (c ? c.query.bind(c) : query)(
    'DELETE FROM admin_write_claims WHERE scope=$1 AND key=$2 AND token=$3', [idem.scope, idem.key, token]);
  let response;
  try {
    response = await run();
  } catch (err) {
    //  برداشتنِ نشان که خودش شکست بخورد، پس از `STALE_MS` کهنه می‌شود
    await release().catch(() => {});
    throw err;
  }
  try {
    await tx(async (c) => {
      await release(c);
      await idempotency.save(c, idem, { tenantId: String(tenantId || ''), resultId: String(resultId || ''), response });
    });
  } catch (err) {
    //  کار انجام شده ⇒ پاسخِ راست همان «شد» است، نه ۵xx (که پل را به تلاشِ دوباره می‌کشاند). نشان (برگشتِ همین تراکنش)
    //  می‌ماند ⇒ همان کلید تا `STALE_MS` ۴۰۹ `idempotency_in_progress` می‌گیرد، نه کارِ دوم.
    console.warn(`[admin-write-once] پاسخِ کلید ثبت نشد: ${err.message}`);
  }
  return { response, replayed: false };
}

/** فرستادنِ پاسخ؛ بازپخش ⇒ سرآیندِ `Idempotent-Replayed: true` (همان کدِ موفقِ همان در). */
function send(res, out, status = 200) {
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  return res.status(status).json(out.response);
}

module.exports = { writeOnce, send, scopeOf, STALE_MS, SCOPE };
