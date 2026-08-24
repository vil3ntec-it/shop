'use strict';
/**
 * همگام‌سازی تفاضلی داده‌های دکان.
 *
 * روش کار:
 *   - هر رکورد برنامه (فروش، محصول، مصرف …) با شناسه‌ی یکتای خودش ذخیره می‌شود.
 *   - سرور برای هر دکان یک شمارنده‌ی سراسری `rev` دارد. هر رکوردی که نوشته
 *     می‌شود rev تازه می‌گیرد.
 *   - دستگاه فقط رکوردهای با rev بزرگ‌تر از آخرین rev دیده‌شده را می‌گیرد.
 *     پس بعد از یک هفته آفلاین بودن هم فقط تفاوت‌ها رد و بدل می‌شود، نه کل دفتر.
 *
 * حل تعارض:
 *   رکوردهای این برنامه تقریباً همیشه «فقط افزوده می‌شوند» (فروش، تراکنش،
 *   ورودی انبار). برای مواردی که ویرایش می‌شوند (محصول، قرض‌دار) قانون
 *   «آخرین ویرایش برنده است» بر اساس updatedAt کلاینت اعمال می‌شود.
 *   چون شناسه‌ها یکتا هستند، دو نفر که آفلاین کار کرده‌اند رکوردهای
 *   یکدیگر را پاک نمی‌کنند — فروش‌ها کنار هم جمع می‌شوند.
 */
const { getDb, now } = require('../db');

/** مجموعه‌هایی که همگام می‌شوند. هر چیزی خارج از این فهرست نادیده گرفته می‌شود. */
const COLLECTIONS = [
  'debtors', 'transactions',
  'expenses',
  'products', 'warehouseEntries',
  'sales', 'saleItems', 'returns',
  'suppliers', 'purchases', 'supplierPayments',
  'stockMovements', 'priceHistory', 'auditLog',
];

const MAX_BATCH = 2000;          // سقف رکورد در هر درخواست
const MAX_RECORD_BYTES = 64 * 1024;

class SyncError extends Error {
  constructor(message, code = 'sync_error', status = 400) {
    super(message); this.code = code; this.status = status; this.expose = true;
  }
}

function nextRev(shopId, n = 1) {
  const db = getDb();
  db.prepare(`INSERT INTO shop_rev (shop_id,last_rev) VALUES (?,0)
              ON CONFLICT(shop_id) DO NOTHING`).run(shopId);
  const row = db.prepare('SELECT last_rev FROM shop_rev WHERE shop_id=?').get(shopId);
  const start = row.last_rev;
  db.prepare('UPDATE shop_rev SET last_rev=? WHERE shop_id=?').run(start + n, shopId);
  return start;   // revها از start+1 تا start+n استفاده می‌شوند
}

function currentRev(shopId) {
  const row = getDb().prepare('SELECT last_rev FROM shop_rev WHERE shop_id=?').get(shopId);
  return row ? row.last_rev : 0;
}

/**
 * نوشتن تغییرات یک دستگاه.
 * @param {Array} changes [{collection, id, updatedAt, deleted, data}]
 * @returns {{applied:number, skipped:number, rev:number, conflicts:Array}}
 */
function pushChanges(shopId, deviceId, userId, changes) {
  if (!Array.isArray(changes)) throw new SyncError('قالب تغییرات نامعتبر است');
  if (changes.length > MAX_BATCH) {
    throw new SyncError(`حداکثر ${MAX_BATCH} رکورد در هر درخواست`, 'batch_too_large', 413);
  }

  const db = getDb();
  const getExisting = db.prepare(
    'SELECT updated_at, deleted FROM shop_records WHERE shop_id=? AND collection=? AND record_id=?'
  );
  const upsert = db.prepare(`
    INSERT INTO shop_records (shop_id,collection,record_id,rev,updated_at,deleted,device_id,user_id,data)
    VALUES (@shop_id,@collection,@record_id,@rev,@updated_at,@deleted,@device_id,@user_id,@data)
    ON CONFLICT(shop_id,collection,record_id) DO UPDATE SET
      rev=excluded.rev, updated_at=excluded.updated_at, deleted=excluded.deleted,
      device_id=excluded.device_id, user_id=excluded.user_id, data=excluded.data
  `);

  let applied = 0, skipped = 0;
  const conflicts = [];

  const tx = db.transaction(() => {
    let rev = nextRev(shopId, changes.length);
    for (const ch of changes) {
      const collection = String(ch && ch.collection || '');
      const recordId = String(ch && ch.id || '');
      if (!COLLECTIONS.includes(collection) || !recordId) { skipped++; continue; }

      const updatedAt = Number(ch.updatedAt) || now();
      const deleted = ch.deleted ? 1 : 0;
      const json = JSON.stringify(ch.data === undefined ? null : ch.data);
      if (json.length > MAX_RECORD_BYTES) { skipped++; continue; }

      const prev = getExisting.get(shopId, collection, recordId);
      // آخرین ویرایش برنده است. مساوی بودن هم رد می‌شود تا نوشتن بی‌دلیل نشود.
      if (prev && prev.updated_at >= updatedAt) {
        skipped++;
        if (prev.deleted !== deleted) {
          conflicts.push({ collection, id: recordId, reason: 'stale_write' });
        }
        continue;
      }

      rev += 1;
      upsert.run({
        shop_id: shopId, collection, record_id: recordId, rev,
        updated_at: updatedAt, deleted, device_id: deviceId, user_id: userId, data: json,
      });
      applied++;
    }
    // اگر بعضی رکوردها رد شدند، revهای رزروشده‌ی اضافی مشکلی ایجاد نمی‌کنند
    // (فقط شماره‌ها پرش دارند) ولی شمارنده نباید عقب برود.
    const last = db.prepare('SELECT last_rev FROM shop_rev WHERE shop_id=?').get(shopId).last_rev;
    if (rev > last) db.prepare('UPDATE shop_rev SET last_rev=? WHERE shop_id=?').run(rev, shopId);
  });
  tx();

  return { applied, skipped, conflicts, rev: currentRev(shopId) };
}

/**
 * خواندن تغییرات بعد از یک rev مشخص.
 * @returns {{changes:Array, rev:number, hasMore:boolean}}
 */
function pullChanges(shopId, sinceRev, limit = MAX_BATCH) {
  const lim = Math.min(MAX_BATCH, Math.max(1, Number(limit) || MAX_BATCH));
  const since = Math.max(0, Number(sinceRev) || 0);

  const rows = getDb().prepare(`
    SELECT collection, record_id, rev, updated_at, deleted, device_id, user_id, data
    FROM shop_records WHERE shop_id=? AND rev>? ORDER BY rev ASC LIMIT ?
  `).all(shopId, since, lim + 1);

  const hasMore = rows.length > lim;
  const page = hasMore ? rows.slice(0, lim) : rows;

  const changes = page.map(r => ({
    collection: r.collection,
    id: r.record_id,
    rev: r.rev,
    updatedAt: r.updated_at,
    deleted: !!r.deleted,
    deviceId: r.device_id,
    userId: r.user_id,
    data: safeParse(r.data),
  }));

  // rev این صفحه، نه rev کل دکان — تا صفحه‌بندی چیزی را جا نیندازد
  const rev = page.length ? page[page.length - 1].rev : since;
  return { changes, rev, hasMore, shopRev: currentRev(shopId) };
}

function safeParse(s) { try { return JSON.parse(s); } catch { return null; } }

/** تنظیمات مشترک دکان (نام فروشگاه، دسته‌ها، واحدها …). */
function getSettings(shopId) {
  const row = getDb().prepare('SELECT data, rev, updated_at FROM shop_settings WHERE shop_id=?').get(shopId);
  if (!row) return { data: {}, rev: 0, updatedAt: 0 };
  return { data: safeParse(row.data) || {}, rev: row.rev, updatedAt: row.updated_at };
}

function putSettings(shopId, data, updatedAt) {
  const cur = getSettings(shopId);
  const t = Number(updatedAt) || now();
  if (cur.updatedAt >= t) return cur;     // نسخه‌ی قدیمی‌تر نوشته نمی‌شود
  const rev = nextRev(shopId, 1) + 1;
  getDb().prepare(`INSERT INTO shop_settings (shop_id,data,rev,updated_at) VALUES (?,?,?,?)
                   ON CONFLICT(shop_id) DO UPDATE SET data=excluded.data, rev=excluded.rev, updated_at=excluded.updated_at`)
    .run(shopId, JSON.stringify(data || {}), rev, t);
  getDb().prepare('UPDATE shop_rev SET last_rev=? WHERE shop_id=? AND last_rev<?').run(rev, shopId, rev);
  return getSettings(shopId);
}

/** آمار کوتاه برای نمایش «چه کسی چه وقت همگام کرده». */
function shopStats(shopId) {
  const db = getDb();
  const perCollection = db.prepare(`
    SELECT collection, COUNT(*) n FROM shop_records
    WHERE shop_id=? AND deleted=0 GROUP BY collection
  `).all(shopId);
  const lastByUser = db.prepare(`
    SELECT user_id, MAX(updated_at) last_at, COUNT(*) n
    FROM shop_records WHERE shop_id=? GROUP BY user_id
  `).all(shopId);
  return { rev: currentRev(shopId), perCollection, lastByUser };
}

module.exports = {
  COLLECTIONS, MAX_BATCH, SyncError,
  pushChanges, pullChanges, currentRev, getSettings, putSettings, shopStats,
};
