'use strict';
/**
 * سابقه‌ی عملیات حساس.
 *
 * هرگز رمز، کد یک‌بارمصرف یا توکن اینجا نوشته نمی‌شود — فقط اینکه
 * چه کسی، در کدام دکان، چه کاری کرد.
 */
const { query, newId, now } = require('../db');
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

async function log(entry) {
  const {
    shopId = '', actorType = 'user', userId = '', action,
    targetType = '', targetId = '', detail = {}, ip = '',
  } = entry || {};
  if (!action) return null;
  const id = newId('aud');
  const clean = scrub(detail);
  const at = now();
  let r;
  try {
    //  پرچمِ زنجیره (STF-09) با همین INSERT برمی‌گردد — خاموش ⇒ هیچ پرسشِ دیگری
    r = await query(
      `INSERT INTO audit_logs (id, shop_id, actor_type, user_id, action, target_type, target_id, detail, ip, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
       RETURNING ${chain.FLAG_SQL} AS hashchain`,
      [id, shopId, actorType, userId, action, targetType, targetId, JSON.stringify(clean), ip, at]
    );
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
