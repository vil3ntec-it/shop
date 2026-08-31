'use strict';
/**
 * همگام‌سازی تفاضلی دفتر دکان.
 *
 * روش کار:
 *   - هر رکورد شناسه‌ی یکتای خودش را دارد؛ پس دو نفر که آفلاین فروخته‌اند
 *     رکورد یکدیگر را پاک نمی‌کنند و فروش‌ها کنار هم جمع می‌شوند.
 *   - هر دکان یک شمارنده‌ی سراسری rev دارد. هر نوشتن، rev تازه می‌گیرد.
 *   - گوشی فقط رکوردهای با rev بزرگ‌تر از آخرین rev دیده‌شده را می‌گیرد؛
 *     پس بعد از یک هفته آفلاین بودن هم کل دفتر دانلود نمی‌شود.
 *
 * تعارض:
 *   داوری با updated_at خود رکورد است (آخرین ویرایش برنده). نسخه‌ی
 *   قدیمی‌تر رد می‌شود ولی داده‌ی سرور پاک نمی‌شود و همان رکورد در پاسخ
 *   به گوشی برمی‌گردد تا خودش را اصلاح کند.
 */
const { one, many, tx, newId, now } = require('../db');
const { badRequest, forbidden } = require('../middleware/errors');
const { can } = require('./permissions');

/** نام مجموعه در برنامه → جدول دیتابیس. هر چیز بیرون این فهرست رد می‌شود. */
const TABLES = {
  products:         'products',
  warehouseEntries: 'inventory',
  sales:            'sales',
  saleItems:        'sale_items',
  returns:          'sale_returns',
  debtors:          'debtors',
  transactions:     'payments',
  expenses:         'expenses',
  suppliers:        'suppliers',
  purchases:        'purchases',
  supplierPayments: 'supplier_payments',
  stockMovements:   'stock_movements',
  priceHistory:     'price_history',
  auditLog:         'shop_audit_log',
};
const COLLECTIONS = Object.keys(TABLES);

const MAX_BATCH = 2000;
const MAX_RECORD_BYTES = 64 * 1024;
const DEFAULT_LIMIT = 1000;
const MAX_LIMIT = 5000;

/** گرفتن یک بازه‌ی rev برای این دسته از تغییرات. */
async function reserveRevs(client, shopId, count) {
  await client.query(
    'INSERT INTO shop_rev (shop_id, last_rev) VALUES ($1, 0) ON CONFLICT (shop_id) DO NOTHING',
    [shopId]
  );
  const { rows } = await client.query(
    'UPDATE shop_rev SET last_rev = last_rev + $2 WHERE shop_id = $1 RETURNING last_rev',
    [shopId, count]
  );
  const end = Number(rows[0].last_rev);
  return end - count;           // revها از start+1 تا start+count
}

async function currentRev(shopId) {
  const r = await one('SELECT last_rev FROM shop_rev WHERE shop_id=$1', [shopId]);
  return r ? Number(r.last_rev) : 0;
}

/**
 * نوشتن تغییرات یک دستگاه.
 *
 * @param {object} ctx {shopId, userId, deviceId, role}
 * @param {Array}  changes [{collection,id,updatedAt,deleted,data}]
 */
async function pushChanges(ctx, changes) {
  const { shopId, userId, deviceId = '', role = 'staff' } = ctx;
  if (!Array.isArray(changes)) throw badRequest('قالب تغییرات درست نیست', 'bad_changes');
  if (changes.length > MAX_BATCH) {
    throw badRequest(`حداکثر ${MAX_BATCH} رکورد در هر درخواست`, 'batch_too_large');
  }
  if (!changes.length) {
    return { applied: 0, skipped: 0, conflicts: [], rev: await currentRev(shopId) };
  }
  if (!can(role, 'data.write')) throw forbidden('اجازه‌ی ثبت اطلاعات ندارید', 'permission_denied');

  return tx(async (c) => {
    let rev = await reserveRevs(c, shopId, changes.length);
    let applied = 0, skipped = 0;
    const conflicts = [];
    const t = now();

    for (const raw of changes) {
      const collection = String(raw?.collection || '');
      const table = TABLES[collection];
      const recordId = String(raw?.id || '');
      if (!table || !recordId || recordId.length > 80) { skipped++; continue; }

      const updatedAt = Number(raw.updatedAt) || t;
      const deleted = raw.deleted === true;
      const data = deleted ? {} : (raw.data && typeof raw.data === 'object' ? raw.data : {});
      const json = JSON.stringify(data);
      if (Buffer.byteLength(json, 'utf8') > MAX_RECORD_BYTES) { skipped++; continue; }

      // رکورد فعلی سرور را قفل می‌کنیم تا دو دستگاه همزمان روی هم ننویسند
      //
      // `data` هم خوانده می‌شود، نه فقط برای مقایسه: اگر این تغییر رد شود،
      // نسخه‌ی سرور همراه خودِ تعارض به گوشی برمی‌گردد. بدون آن، گوشی
      // می‌فهمید «رد شد» ولی نمی‌دانست چه چیزی جای آن نشسته، و چون rev
      // رکورد عوض نشده بود در pull بعدی هم نمی‌آمد — یعنی دو طرف تا ابد
      // ناهمگام می‌ماندند.
      const existing = (await c.query(
        `SELECT updated_at, deleted, version, user_id, data FROM ${table} WHERE shop_id=$1 AND id=$2 FOR UPDATE`,
        [shopId, recordId]
      )).rows[0];

      /** تعارض، همراه با آنچه سرور واقعاً دارد. */
      const conflictWith = (reason) => ({
        collection, id: recordId, reason,
        serverUpdatedAt: Number(existing.updated_at),
        serverVersion: existing.version,
        deleted: existing.deleted === true,
        data: existing.deleted ? null : existing.data,
      });

      if (existing) {
        // شاگرد فقط رکورد خودش را حذف می‌کند
        if (deleted && !can(role, 'data.delete.any') && existing.user_id !== userId) {
          conflicts.push(conflictWith('delete_not_allowed'));
          skipped++;
          continue;
        }
        // ویرایش قدیمی‌تر از چیزی که سرور دارد، نوشته نمی‌شود
        if (Number(existing.updated_at) > updatedAt) {
          conflicts.push(conflictWith('stale'));
          skipped++;
          continue;
        }
      } else if (deleted) {
        // حذف رکوردی که سرور ندیده — سنگ قبر ثبت می‌شود تا بقیه هم خبردار شوند
      }

      rev++;
      await c.query(
        `INSERT INTO ${table} (shop_id, id, rev, version, created_at, updated_at, deleted, device_id, user_id, data)
         VALUES ($1,$2,$3,1,$4,$5,$6,$7,$8,$9::jsonb)
         ON CONFLICT (shop_id, id) DO UPDATE SET
           rev = excluded.rev,
           version = ${table}.version + 1,
           updated_at = excluded.updated_at,
           deleted = excluded.deleted,
           device_id = excluded.device_id,
           user_id = CASE WHEN ${table}.user_id = '' THEN excluded.user_id ELSE ${table}.user_id END,
           data = CASE WHEN excluded.deleted THEN ${table}.data ELSE excluded.data END`,
        [shopId, recordId, rev, t, updatedAt, deleted, deviceId, userId, json]
      );
      applied++;
    }

    return { applied, skipped, conflicts, rev: await (async () => {
      const r = (await c.query('SELECT last_rev FROM shop_rev WHERE shop_id=$1', [shopId])).rows[0];
      return Number(r.last_rev);
    })() };
  });
}

/** ساخت پرس‌وجوی خواندن تفاضلی روی همه‌ی مجموعه‌ها. */
const PULL_SQL = (() => {
  const parts = COLLECTIONS.map(col => `
    SELECT '${col}'::text AS collection, id, rev, updated_at, deleted, user_id, device_id, data
      FROM ${TABLES[col]} WHERE shop_id = $1 AND rev > $2`);
  return `${parts.join(' UNION ALL ')} ORDER BY rev ASC LIMIT $3`;
})();

/**
 * گرفتن تغییرات بعد از since.
 * @returns {{changes:Array, rev:number, hasMore:boolean}}
 */
async function pullChanges(shopId, since = 0, limit = DEFAULT_LIMIT) {
  const cap = Math.min(Math.max(1, Number(limit) || DEFAULT_LIMIT), MAX_LIMIT);
  const from = Math.max(0, Number(since) || 0);
  const rows = await many(PULL_SQL, [shopId, from, cap + 1]);
  const hasMore = rows.length > cap;
  const page = hasMore ? rows.slice(0, cap) : rows;
  const head = await currentRev(shopId);

  return {
    changes: page.map(r => ({
      collection: r.collection,
      id: r.id,
      rev: Number(r.rev),
      updatedAt: Number(r.updated_at),
      deleted: r.deleted,
      userId: r.user_id,
      data: r.deleted ? null : r.data,
    })),
    rev: page.length ? Number(page[page.length - 1].rev) : head,
    hasMore,
    serverRev: head,
    serverTime: now(),
  };
}

/**
 * اجرای یک عملیات با تضمین «فقط یک بار».
 *
 * اگر گوشی به‌خاطر قطع شدن اینترنت همان درخواست را دوباره بفرستد، پاسخ
 * دفعه‌ی اول برگردانده می‌شود و هیچ رکوردی دو بار ثبت نمی‌شود.
 */
async function runIdempotent({ shopId, userId, deviceId, operationId }, fn) {
  if (!operationId) return { result: await fn(), replayed: false };

  const existing = await one(
    'SELECT response FROM sync_operations WHERE shop_id=$1 AND client_operation_id=$2',
    [shopId, operationId]
  );
  if (existing) return { result: existing.response, replayed: true };

  const result = await fn();

  try {
    await one(
      `INSERT INTO sync_operations (id, shop_id, user_id, device_id, client_operation_id, response, created_at)
       VALUES ($1,$2,$3,$4,$5,$6::jsonb,$7)
       ON CONFLICT (shop_id, client_operation_id) DO NOTHING
       RETURNING id`,
      [newId('op'), shopId, userId, deviceId || '', operationId, JSON.stringify(result), now()]
    );
  } catch (err) {
    console.error('[sync] ثبت شناسه‌ی عملیات نشد:', err.message);
  }
  return { result, replayed: false };
}

module.exports = {
  TABLES, COLLECTIONS, MAX_BATCH, MAX_LIMIT,
  pushChanges, pullChanges, currentRev, runIdempotent,
};
