'use strict';
const { getDb, newId, now } = require('../db');

function log({ actorType, actorId = '', action, targetType = '', targetId = '', detail = null, ip = '' }) {
  try {
    getDb().prepare(`
      INSERT INTO audit_log (id,actor_type,actor_id,action,target_type,target_id,detail,ip,created_at)
      VALUES (?,?,?,?,?,?,?,?,?)
    `).run(newId('aud'), actorType, actorId, action, targetType, targetId,
           detail ? JSON.stringify(detail) : '', ip, now());
  } catch (e) {
    console.error('[audit] ثبت سابقه ناموفق بود:', e.message);
  }
}

function list({ limit = 100, offset = 0, targetType = null, targetId = null } = {}) {
  const db = getDb();
  if (targetType && targetId) {
    return db.prepare(`SELECT * FROM audit_log WHERE target_type=? AND target_id=?
                       ORDER BY created_at DESC LIMIT ? OFFSET ?`)
      .all(targetType, targetId, limit, offset);
  }
  return db.prepare('SELECT * FROM audit_log ORDER BY created_at DESC LIMIT ? OFFSET ?')
    .all(limit, offset);
}

module.exports = { log, list };
