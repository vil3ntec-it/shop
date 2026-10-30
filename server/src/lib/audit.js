'use strict';
/**
 * سابقه‌ی عملیات حساس.
 *
 * هرگز رمز، کد یک‌بارمصرف یا توکن اینجا نوشته نمی‌شود — فقط اینکه
 * چه کسی، در کدام دکان، چه کاری کرد.
 */
const { query, newId, now, tx } = require('../db');
const chain = require('./hash-chain-store');

const SECRET_KEYS = ['password', 'code', 'token', 'secret', 'otp', 'hash', 'idToken', 'id_token'];

function scrub(detail) {
  if (!detail || typeof detail !== 'object') return {};
  const out = {};
  for (const [k, v] of Object.entries(detail)) {
    if (SECRET_KEYS.some(s => k.toLowerCase().includes(s.toLowerCase()))) continue;
    out[k] = typeof v === 'object' && v !== null ? scrub(v) : v;
  }
  return out;
}

/**
 * @param entry.onceSince  (با `once`، اختیاری) سطرِ پیشین زودتر از این لحظه (میلی‌ثانیه) نیست ⇒ سنجش فقط
 *                         از همان‌جا روی `idx_audit_shop (shop_id, created_at)`
 * @param entry.once  فقط اگر سطری با همین (دکان، کار، هدف) نیست — سنجشِ وجود درونِ همان INSERT
 *                    (`INSERT … SELECT … WHERE NOT EXISTS`) و زیرِ قفلِ تراکنشیِ همان کلید
 *                    (`pg_advisory_xact_lock`)، تا دو اتصالِ هم‌زمان در READ COMMITTED هر دو
 *                    «نیست» نبینند؛ بود ⇒ null و هیچ نوشتنی.
 */
async function log(entry) {
  const {
    shopId = '', actorType = 'user', userId = '', action,
    targetType = '', targetId = '', detail = {}, ip = '', once = false, onceSince = null,
  } = entry || {};
  if (!action) return null;
  const id = newId('aud');
  const clean = scrub(detail);
  const at = now();
  let r;
  try {
    const params = [id, shopId, actorType, userId, action, targetType, targetId, JSON.stringify(clean), ip, at];
    const since = once && Number.isSafeInteger(onceSince) && onceSince > 0 ? onceSince : 0;
    //  پرچمِ زنجیره (STF-09) با همین INSERT برمی‌گردد — خاموش ⇒ هیچ پرسشِ دیگری
    r = once
      ? await tx(async (c) => {
        await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`audit-once:${shopId}:${action}:${targetId}`]);
        return c.query(
          `INSERT INTO audit_logs (id, shop_id, actor_type, user_id, action, target_type, target_id, detail, ip, created_at)
           SELECT $1::text, $2::text, $3::text, $4::text, $5::text, $6::text, $7::text, $8::jsonb, $9::text, $10::bigint
            WHERE NOT EXISTS (SELECT 1 FROM audit_logs WHERE shop_id = $2::text AND created_at >= $11::bigint
                                AND action = $5::text AND target_id = $7::text)
           RETURNING ${chain.FLAG_SQL} AS hashchain`,
          [...params, since]
        );
      })
      : await query(
        `INSERT INTO audit_logs (id, shop_id, actor_type, user_id, action, target_type, target_id, detail, ip, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
         RETURNING ${chain.FLAG_SQL} AS hashchain`,
        params
      );
    if (once && !r.rows.length) return null;
  } catch (err) {
    // شکست ثبت سابقه نباید خود عملیات را از کار بیندازد
    console.error('[audit] ثبت نشد:', err.message);
    return id;
  }
  //  STF-09: سابقهٔ غیرقابل‌ویرایشِ هر کاربر — فقط وقتی `security /
  //  hashchain.audit` در live_config روشن است (پیش‌فرض خاموش). بی IP.
  if (chain.flagOn(r.rows[0] && r.rows[0].hashchain)) {
    try {
      await chain.appendAudit({ id, shopId, actorType, userId, action, targetType, targetId, detail: clean, at });
    } catch (err) {
      console.error('[audit] زنجیره نوشته نشد:', err.message);
    }
  }
  return id;
}

module.exports = { log, scrub };
