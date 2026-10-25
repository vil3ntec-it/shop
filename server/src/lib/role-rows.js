'use strict';
/**
 * A16 — ورودیِ «قاعدهٔ ردیف» (`perm-core.checkRows`) برای سه راهِ همگام‌سازی: v1، v3 و راهِ قدیم.
 *
 * صندوق‌دار از `warehouseEntries` فقط اصلاحِ موجودیِ قلمِ برگشتِ خودش را می‌نویسد و از
 * `stockMovements` فقط حرکتِ فاکتور و برگشت را. داوری در هسته است (همان JS و کاتلین و بردارها)؛
 * این‌جا فقط آنچه هسته لازم دارد گرد می‌آید:
 *   • ردیف = نمای یک‌دستِ درجِ تازه، یا null وقتی op درجِ تازه نیست: ویرایش و حذف، «insert» روی
 *     ردیفِ موجود (v1 آن را با ردیفِ ثبت‌شده — حتی حذف‌شده — یکی می‌کند)، یا شناسه‌ای که در همین
 *     دسته بیش از یک بار آمده.
 *   • قلم‌های برگشت: برگشتِ همین دسته فقط اگر واقعاً می‌نشیند (`fresh`: نه op_id ِ تکراری، نه ردشده،
 *     نه ردیفِ موجود) — و در v3 فقط از همان واحد (tx، یا بستهٔ `bundleV3`)؛ به‌علاوهٔ برگشتِ ثبت‌شده
 *     (حذف‌نشده، لغونشده) که «خودی» است اگر همین دستگاه/کاربر ثبتش کرده.
 *   • فاکتورها: درجِ همین دسته و ثبت‌شده‌ها. اصلاح‌های ثبت‌شده (`taken`): هر قلم یک اصلاح.
 */
const core = require('./perm-core');

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const isId = (v) => typeof v === 'string' && v !== '';
const uniq = (list) => [...new Set(list.filter(isId))];
const div = (v, d) => (typeof v === 'number' ? v / d : v);
const key = (table, rowId) => `${table}\u0000${rowId}`;

/** نمای یک‌دستِ درج با یکاهای برنامه (v3: Minor ÷ ۱۰۰، Milli ÷ ۱۰۰۰) */
function viewOf(table, f, v3) {
  if (!isObj(f)) return null;
  const money = (k) => (v3 ? div(f[`${k}Minor`], 100) : f[k]);
  const qty = (k) => (v3 ? div(f[`${k}Milli`], 1000) : f[k]);
  if (table === 'warehouseEntries') {
    return {
      isAdjustment: f.isAdjustment, price: money('price'), cartons: qty('cartons'), perCarton: qty('perCarton'),
      adjKind: f.adjKind, refId: f.refId, productId: f.productId, units: qty('units'),
    };
  }
  if (table === 'stockMovements') {
    //  `/api/sales/full` ِ راهِ قدیم فاکتور را با `saleId` می‌نشاند، نه `refId`
    const refId = f.refId === undefined || f.refId === null ? f.saleId : f.refId;
    return { type: f.type, qty: qty('qty'), refId, productId: f.productId };
  }
  return null;                                   //  جدولِ قاعده‌دارِ ناشناخته ⇐ بسته
}

/** یک قلمِ برگشت از فیلدهای ردیف */
function lineOf(rowId, f, own, v3) {
  const d = isObj(f) ? f : {};
  const q = v3 ? div(d.quantityMilli, 1000) : d.quantity;
  return { id: String(rowId), docId: isId(d.docId) ? d.docId : null, productId: d.productId, qty: q, own: own === true };
}

/** عددِ jsonb (یا رشتهٔ عددی) ⇐ عدد؛ نامفهوم ⇐ null */
function numOf(v) {
  if (typeof v === 'number') return v;
  if (typeof v === 'string' && v.trim() !== '') { const n = Number(v); return Number.isFinite(n) ? n : null; }
  return null;
}

/**
 * داوریِ ردیف‌های قاعده‌دارِ یک دسته.
 * @param role
 * @param items  همهٔ opهای دسته، به ترتیب: {table, rowId, insert, fresh, fields, unit}
 *               `fresh` = این op اگر برسد واقعاً اعمال می‌شود (تکراری یا ردشده نیست)
 * @param db     گردآورندهٔ همان راه (async): existing(pairs) ⇒ Set(key)، returns(ids) ⇒ قلم‌ها،
 *               sales(ids) ⇒ شناسه‌ها، taken(ids) ⇒ [{refId, productId, units}]
 * @param opts   {v3, perUnit} — v3: یکاهای صحیح؛ perUnit: برگشتِ همین دسته فقط از همان واحد
 * @returns {Promise<Map<item, {ok, reason}>>}  فقط برای opهای جدولِ قاعده‌دار
 */
async function judge(role, items, db, { v3 = false, perUnit = false } = {}) {
  const out = new Map();
  const ruled = items.filter((it) => core.canWrite(role, it.table) && core.rowRule(role, it.table));
  if (!ruled.length) return out;

  const counted = new Map();
  for (const it of items) {
    if (it.table !== 'returns' && !core.rowRule(role, it.table)) continue;
    const k = key(it.table, it.rowId);
    counted.set(k, (counted.get(k) || 0) + 1);
  }
  const batchReturns = items.filter((it) => it.table === 'returns' && it.insert && it.fresh && isId(it.rowId));
  const existing = await db.existing([...ruled, ...batchReturns].map((it) => ({ table: it.table, rowId: it.rowId })));
  const isNew = (it) => it.insert && isId(it.rowId) && counted.get(key(it.table, it.rowId)) === 1
    && !existing.has(key(it.table, it.rowId));

  const viewed = ruled.map((it) => ({ it, row: isNew(it) ? viewOf(it.table, it.fields, v3) : null }));
  const fresh = batchReturns.filter(isNew).map((it) => ({ unit: it.unit, line: lineOf(it.rowId, it.fields, true, v3) }));
  const freshRefs = new Set(fresh.flatMap(({ line }) => [line.id, line.docId]).filter(isId));

  //  برگشت‌های ثبت‌شده‌ای که ردیفی به آن‌ها اشاره می‌کند (برگشتِ همین دسته با همان شناسه نه)
  const wanted = uniq(viewed.map((v) => (v.row ? v.row.refId : null)));
  const stored = (wanted.length ? await db.returns(wanted) : []).filter((l) => !freshRefs.has(l.id));
  const saleRefs = uniq(viewed
    .filter((v) => v.row && v.it.table === 'stockMovements' && core.DOC_MOVE_TYPES[v.row.type] === 'sale')
    .map((v) => v.row.refId));
  const batchSales = new Set(items.filter((it) => it.table === 'sales' && it.insert && it.fresh).map((it) => String(it.rowId)));
  const lookSales = saleRefs.filter((id) => !batchSales.has(id));
  const sales = [...batchSales, ...(lookSales.length ? await db.sales(lookSales) : [])];
  const candidates = uniq([...stored, ...fresh.map((f) => f.line)].flatMap((l) => [l.id, l.docId]));
  const taken = candidates.length ? await db.taken(candidates) : [];

  //  واحدها به ترتیب؛ اصلاحِ پذیرفتهٔ یک واحد قلمِ ثبت‌شده را برای واحدِ بعد می‌گیرد
  const units = new Map();
  for (const v of viewed) {
    const u = perUnit ? v.it.unit : 0;
    if (!units.has(u)) units.set(u, []);
    units.get(u).push(v);
  }
  for (const [u, list] of units) {
    const returns = fresh.filter((f) => !perUnit || f.unit === u).map((f) => f.line).concat(stored);
    const res = core.checkRows(role, list.map((v) => ({ table: v.it.table, row: v.row })), { returns, sales, taken });
    list.forEach((v, i) => {
      out.set(v.it, res[i]);
      if (res[i].ok && v.it.table === 'warehouseEntries' && v.row) {
        taken.push({ refId: v.row.refId, productId: v.row.productId, units: v.row.units });
      }
    });
  }
  return out;
}

/** گردآورندهٔ v1 (`sync_rows` و `oplog`) */
function v1Db(many, ctx, deviceId) {
  const base = `app='shop' AND account_kind='shop' AND account_id=$1`;
  return {
    async existing(pairs) {
      const rows = await many(
        `SELECT table_name, row_id FROM sync_rows WHERE ${base} AND table_name = ANY($2::text[]) AND row_id = ANY($3::text[])`,
        [ctx.accountId, uniq(pairs.map((p) => p.table)), uniq(pairs.map((p) => String(p.rowId)))]
      );
      return new Set(rows.map((r) => key(r.table_name, r.row_id)));
    },
    async returns(ids) {
      const rows = await many(
        `SELECT row_id, data FROM sync_rows WHERE ${base} AND table_name='returns' AND deleted_at IS NULL
            AND (row_id = ANY($2::text[]) OR data->>'docId' = ANY($2::text[]))`,
        [ctx.accountId, ids]
      );
      if (!rows.length) return [];
      const mine = new Set(deviceId ? (await many(
        `SELECT DISTINCT row_id FROM oplog WHERE ${base} AND table_name='returns' AND op_type='insert'
            AND device_id=$2 AND row_id = ANY($3::text[])`,
        [ctx.accountId, deviceId, rows.map((r) => r.row_id)]
      )).map((r) => r.row_id) : []);
      return rows.map((r) => ({ ...lineOf(r.row_id, r.data, mine.has(r.row_id), false), qty: numOf(r.data?.quantity) }));
    },
    async sales(ids) {
      return (await many(
        `SELECT row_id FROM sync_rows WHERE ${base} AND table_name='sales' AND deleted_at IS NULL AND row_id = ANY($2::text[])`,
        [ctx.accountId, ids]
      )).map((r) => r.row_id);
    },
    async taken(ids) {
      return (await many(
        `SELECT data FROM sync_rows WHERE ${base} AND table_name='warehouseEntries' AND deleted_at IS NULL
            AND data->>'refId' = ANY($2::text[]) AND data->>'adjKind' = ANY($3::text[])`,
        [ctx.accountId, ids, core.RETURN_ADJ_KINDS]
      )).map((r) => ({ refId: r.data.refId, productId: r.data.productId, units: numOf(r.data.units) }));
    },
  };
}

/** گردآورندهٔ v3 (`sync_v3_rows`): فقط ردیفِ درج‌شده، حذف‌نشده و لغونشده */
function v3Db(many, ctx, deviceId) {
  const base = `app='shop' AND account_id=$1`;
  const live = `inserted AND deleted_at IS NULL AND COALESCE(data->>'status', '') <> 'cancelled'`;
  return {
    async existing(pairs) {
      const rows = await many(
        `SELECT table_name, row_id FROM sync_v3_rows WHERE ${base} AND table_name = ANY($2::text[]) AND row_id = ANY($3::text[])`,
        [ctx.accountId, uniq(pairs.map((p) => p.table)), uniq(pairs.map((p) => String(p.rowId)))]
      );
      return new Set(rows.map((r) => key(r.table_name, r.row_id)));
    },
    async returns(ids) {
      const rows = await many(
        `SELECT row_id, data, created_by FROM sync_v3_rows WHERE ${base} AND table_name='returns' AND ${live}
            AND (row_id = ANY($2::text[]) OR data->>'docId' = ANY($2::text[]))`,
        [ctx.accountId, ids]
      );
      return rows.map((r) => ({
        ...lineOf(r.row_id, r.data, !!deviceId && r.created_by === deviceId, true),
        qty: numOf(r.data?.quantityMilli) === null ? null : numOf(r.data.quantityMilli) / 1000,
      }));
    },
    async sales(ids) {
      return (await many(
        `SELECT row_id FROM sync_v3_rows WHERE ${base} AND table_name='sales' AND inserted AND deleted_at IS NULL
            AND row_id = ANY($2::text[])`,
        [ctx.accountId, ids]
      )).map((r) => r.row_id);
    },
    async taken(ids) {
      return (await many(
        `SELECT data FROM sync_v3_rows WHERE ${base} AND table_name='warehouseEntries' AND ${live}
            AND data->>'refId' = ANY($2::text[]) AND data->>'adjKind' = ANY($3::text[])`,
        [ctx.accountId, ids, core.RETURN_ADJ_KINDS]
      )).map((r) => {
        const u = numOf(r.data.unitsMilli);
        return { refId: r.data.refId, productId: r.data.productId, units: u === null ? null : u / 1000 };
      });
    },
  };
}

/** گردآورندهٔ راهِ قدیم (جدول‌های `sync.js`): خودی = ثبتِ همین کاربر */
function legacyDb(many, shopId, userId, TABLES) {
  return {
    async existing(pairs) {
      const out = new Set();
      for (const table of uniq(pairs.map((p) => p.table))) {
        const ids = uniq(pairs.filter((p) => p.table === table).map((p) => String(p.rowId)));
        for (const r of await many(`SELECT id FROM ${TABLES[table]} WHERE shop_id=$1 AND id = ANY($2::text[])`, [shopId, ids])) {
          out.add(key(table, r.id));
        }
      }
      return out;
    },
    async returns(ids) {
      return (await many(
        `SELECT id, data, user_id FROM ${TABLES.returns}
          WHERE shop_id=$1 AND deleted=false AND (id = ANY($2::text[]) OR data->>'docId' = ANY($2::text[]))`,
        [shopId, ids]
      )).map((r) => ({ ...lineOf(r.id, r.data, !!userId && r.user_id === userId, false), qty: numOf(r.data?.quantity) }));
    },
    async sales(ids) {
      return (await many(
        `SELECT id FROM ${TABLES.sales} WHERE shop_id=$1 AND deleted=false AND id = ANY($2::text[])`, [shopId, ids]
      )).map((r) => r.id);
    },
    async taken(ids) {
      return (await many(
        `SELECT data FROM ${TABLES.warehouseEntries} WHERE shop_id=$1 AND deleted=false
            AND data->>'refId' = ANY($2::text[]) AND data->>'adjKind' = ANY($3::text[])`,
        [shopId, ids, core.RETURN_ADJ_KINDS]
      )).map((r) => ({ refId: r.data.refId, productId: r.data.productId, units: numOf(r.data.units) }));
    },
  };
}

module.exports = { judge, viewOf, lineOf, v1Db, v3Db, legacyDb };
