'use strict';
/**
 * بازگرداندنِ تعارضِ همگام‌سازی **یک بار** — `POST /api/admin/sync/conflicts/:id/restore` (یافتهٔ ۱.۵۰.۷۴ ِ ممیزیِ پنلِ مرکزی).
 *
 * چرا جدا: خودِ بازگرداندن (`lib/sync-v1.js` ⇒ `restoreConflict`، مشترکِ دکان و پمپ، دست‌نخورده) تراکنشِ خودش را دارد و
 * `restored_at` را پس از opِ تازه می‌نویسد؛ پس «یک بار» را نمی‌شود در همان تراکنش گرفت. سه گام:
 *   ۱) تراکنشِ کوتاه — کلیدِ یکتایی (همان `lib/idempotency.js`: قفل، «همان کلید + همان بدنه ⇒ همان پاسخ»، «بدنهٔ دیگر ⇒ ۴۰۹»)،
 *      بعد قفلِ همان تعارض: نبود ⇒ ۴۰۴؛ `restored_at` پُر ⇒ ۴۰۹ `already_restored`؛ نشانِ زندهٔ کسِ دیگر ⇒ ۴۰۹
 *      `restore_in_progress`؛ وگرنه نشان (`sync_conflict_restore_claims`، مهاجرتِ ۴۹۵) می‌نشیند.
 *   ۲) خودِ کار (`run`)، بیرون از هر تراکنشِ این‌جا. شکست ⇒ نشان برداشته می‌شود و همان خطا بالا می‌رود (تلاشِ دوباره آزاد).
 *   ۳) تراکنشِ پایان — نشان برداشته و پاسخ زیرِ همان کلید ثبت می‌شود.
 *
 * ⛔ شناسه فقط رقم و یک شکل: `restoreConflict` آن را با `Number(id)` می‌خواند، پس «05»، «5e0»، «5.0»، «+5» همه ۵ بودند.
 * ⛔ نشانِ فرآیندِ مُرده (افتادنِ سرور میانِ گامِ ۲) پس از `STALE_MS` کنار می‌رود؛ آن‌وقت هم اگر `restored_at` پُر شده بود ۴۰۹ است.
 * ⛔ کلید به مدیر (و «انجام‌دهنده»ٔ کلیدِ سرویس) بسته است؛ اثرِ انگشت = روش + نشانی (پس شناسهٔ تعارض) + بدنه (پس دلیل).
 */
const crypto = require('crypto');
const { tx, query, now } = require('../db');
const idempotency = require('./idempotency');
const { badRequest, notFound, conflict } = require('../middleware/errors');

const ID_RE = /^[1-9]\d{0,14}$/;
const STALE_MS = 10 * 60 * 1000;

/** شناسهٔ تعارض به همان یک شکل، وگرنه ۴۰۰ `bad_id`. */
function conflictIdOf(raw) {
  const s = typeof raw === 'string' ? raw : '';
  if (!ID_RE.test(s)) throw badRequest('شناسهٔ تعارض معتبر نیست (فقط رقم، بی صفرِ آغاز)', 'bad_id');
  return s;
}

/**
 * @param {import('express').Request} req  مدیرِ ارشد (`req.admin`) و سرآیندِ اختیاریِ `Idempotency-Key`
 * @param {string} id   خروجیِ `conflictIdOf`
 * @param {() => Promise<object>} run  خودِ بازگرداندن؛ پاسخِ ‎{ ok: true, ...out }‎ از همین ساخته می‌شود
 * @returns {Promise<{ response: object, out: object|null, replayed: boolean }>}
 */
async function restoreOnce(req, id, run) {
  //  کلیدِ سرویسِ پل یک «مدیر» است برای همهٔ کاربرانِ پنل ⇒ «انجام‌دهنده» هم در دامنهٔ کلید
  const idem = idempotency.fromRequest(req, `sync-restore:${req.admin.id}:${req.actor?.id || ''}`);
  const token = crypto.randomBytes(12).toString('hex');
  const replay = await tx(async (c) => {
    if (idem) {
      await idempotency.lock(c, idem);
      const prev = await idempotency.find(idem, idempotency.viaClient(c));
      if (prev) return prev.response || {};
    }
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`sync-restore:${id}`]);
    const cf = (await c.query('SELECT id, restored_at FROM sync_conflicts WHERE id=$1', [id])).rows[0];
    if (!cf) throw notFound('این تعارض پیدا نشد', 'conflict_not_found');
    if (cf.restored_at !== null && cf.restored_at !== undefined) {
      throw conflict('این تعارض پیش‌تر برگردانده شد؛ فهرست را تازه کنید', 'already_restored');
    }
    const t = now();
    //  نشان‌های فرآیندهای مُرده جمع نمی‌شوند (هر کدام فقط یک ردیف، و فقط پس از کهنه شدن)
    await c.query('DELETE FROM sync_conflict_restore_claims WHERE claimed_at < $1 AND conflict_id <> $2', [t - STALE_MS, id]);
    const held = (await c.query('SELECT claimed_at FROM sync_conflict_restore_claims WHERE conflict_id=$1', [id])).rows[0];
    if (held && Number(held.claimed_at) > t - STALE_MS) {
      throw conflict('همین تعارض همین حالا در حالِ بازگرداندن است', 'restore_in_progress');
    }
    await c.query(
      `INSERT INTO sync_conflict_restore_claims (conflict_id, token, admin_id, idem_key, claimed_at) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (conflict_id) DO UPDATE SET token=EXCLUDED.token, admin_id=EXCLUDED.admin_id,
              idem_key=EXCLUDED.idem_key, claimed_at=EXCLUDED.claimed_at`,
      [id, token, String(req.admin.id || ''), idem ? idem.key : '', t]
    );
    return null;
  });
  if (replay) return { response: replay, out: null, replayed: true };

  let out;
  try {
    out = await run();
  } catch (err) {
    //  شکست ⇒ راه برای تلاشِ دوباره باز؛ برداشتنِ نشان که خودش شکست بخورد، پس از `STALE_MS` کهنه می‌شود
    await query('DELETE FROM sync_conflict_restore_claims WHERE conflict_id=$1 AND token=$2', [id, token]).catch(() => {});
    throw err;
  }
  const response = { ok: true, ...(out || {}) };
  try {
    await tx(async (c) => {
      await c.query('DELETE FROM sync_conflict_restore_claims WHERE conflict_id=$1 AND token=$2', [id, token]);
      if (idem) await idempotency.save(c, idem, { tenantId: String(out?.ctx?.accountId || ''), resultId: id, response });
    });
  } catch (err) {
    //  کار انجام شده و `restored_at` پُر است ⇒ پاسخِ راست همان «شد» است، نه ۵xx (که پنل را به تلاشِ دوباره می‌کشاند).
    //  بی ثبتِ کلید، تکرارِ همان کلید ۴۰۹ `already_restored` می‌گیرد — باز هم هیچ opِ دومی.
    console.warn(`[sync-restore] پایانِ بازگرداندنِ ${id} ثبت نشد: ${err.message}`);
  }
  return { response, out: out || null, replayed: false };
}

module.exports = { conflictIdOf, restoreOnce, STALE_MS, ID_RE };
