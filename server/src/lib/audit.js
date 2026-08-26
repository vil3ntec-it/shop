'use strict';
/**
 * سابقه‌ی عملیات حساس.
 *
 * هرگز رمز، کد یک‌بارمصرف یا توکن اینجا نوشته نمی‌شود — فقط اینکه
 * چه کسی، در کدام دکان، چه کاری کرد.
 */
const { query, newId, now } = require('../db');

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
  try {
    await query(
      `INSERT INTO audit_logs (id, shop_id, actor_type, user_id, action, target_type, target_id, detail, ip, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)`,
      [id, shopId, actorType, userId, action, targetType, targetId, JSON.stringify(scrub(detail)), ip, now()]
    );
  } catch (err) {
    // شکست ثبت سابقه نباید خود عملیات را از کار بیندازد
    console.error('[audit] ثبت نشد:', err.message);
  }
  return id;
}

module.exports = { log, scrub };
