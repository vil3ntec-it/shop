'use strict';
/**
 * «دورانِ» همگام‌سازی (sync epoch) — §۹.۵ «بازیابی تا آخرین پشتیبان و سپس اعمالِ opهای صف»،
 * سناریوی E2E-OPS-14.replay.
 *
 * ── مشکل ───────────────────────────────────────────────────────────────────
 * سرور از پشتیبان برمی‌گردد ⇒ `oplog` و دنبالهٔ `server_seq` به لحظهٔ پشتیبان برمی‌گردند.
 * دستگاهی که پس از پشتیبان فروخته بود، opهایش را «اعمال‌شده» گرفته و از صف بیرون کرده،
 * و cursorش از سرِ دفترِ سرور جلوتر است. پس هیچ‌وقت دوباره نمی‌فرستد ⇒ فروش گم می‌شود.
 * مقایسهٔ سادهٔ «head < cursor» بس نیست: دستگاهِ دیگری که زودتر برسد head را دوباره
 * بالا می‌برد و نشانه پاک می‌شود.
 *
 * ── راه ────────────────────────────────────────────────────────────────────
 * هر بار که سرور بالا می‌آید (`createApp`) یک «دورانِ» تازه در `app_config` می‌نشیند:
 *   { id, seq: بیشینهٔ server_seq ِ oplog, seq_v3: همان برای sync_v3_ops, at, reason }
 * بازیابی همیشه با بالا آمدنِ دوبارهٔ سرور است (PGlite: پوشهٔ تازه؛ pg: دیتابیسِ تازه)،
 * پس دورانِ پس از بازیابی همیشه تازه است، هر راهی که بازیابی رفته باشد.
 * «پایه»ی هر حساب = بیشینهٔ server_seq ِ همان حساب که ≤ seq ِ دوران است، یعنی سرِ دفترِ
 * آن حساب در لحظهٔ بالا آمدن. چون پوش‌های یک حساب پشتِ قفلِ همان حساب‌اند
 * (`pg_advisory_xact_lock` در sync-v1 / sync-v3 / order-ledger)، هر opِ این حساب با
 * server_seq ≤ پایه در دیتابیس هست و هر opِ «اعمال‌شده» با شمارهٔ بزرگ‌تر شاید نه.
 *
 * دستگاه (SyncV1Engine / SyncV3Engine / NativeDevice ِ آزمونِ سرتاسری) دوران را می‌خواند؛
 * عوض شده ⇒ opهای پذیرفته‌شدهٔ خودش با server_seq > پایه را با **همان op_id** دوباره
 * می‌فرستد (تکراری‌ها «duplicate» می‌شوند) و cursor را تا پایه پایین می‌آورد. بالا آمدنِ
 * عادی (بی بازیابی): پایه = سرِ دفتر ⇒ هیچ کاری نمی‌ماند.
 *
 * ⛔ فقط دکان. `lib/sync-v1.js` و مسیرهای پمپ دست نمی‌خورند؛ دورانِ v1 از درِ جدای
 *    `GET /api/shop/sync-epoch` و دورانِ v3 در پاسخِ pull ِ خودِ v3 می‌رود.
 * ⛔ خاموش‌کن: `SYNC_EPOCH_ENABLED=false` ⇒ در ۴۰۴ و v3 بی دوران ⇒ برنامه همان رفتارِ پیشین.
 *    چرخاندن در بالا آمدن همیشه هست (بی‌ضرر) تا روشن کردنِ دوباره درست کار کند.
 */
const crypto = require('crypto');
const { one, query, now } = require('../db');

const KEY = 'sync.epoch';

async function maxSeq(table) {
  try {
    const r = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table}`);
    return Number(r && r.m) || 0;
  } catch {
    //  دیتابیسِ آزمونی بی همان جدول — دوران بی آن هم ساخته می‌شود
    return 0;
  }
}

/**
 * دورانِ تازه — در بالا آمدنِ سرور، پیش از پذیرفتنِ هر درخواست.
 * @returns {Promise<{id:string, seq:number, seq_v3:number, at:number, reason:string}>}
 */
async function rotate(reason = 'boot') {
  const epoch = {
    id: `ep_${Date.now().toString(36)}${crypto.randomBytes(6).toString('hex')}`,
    seq: await maxSeq('oplog'),
    seq_v3: await maxSeq('sync_v3_ops'),
    at: now(),
    reason: String(reason).slice(0, 32),
  };
  await query(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1,$2,$3)
     ON CONFLICT (key) DO UPDATE SET value = excluded.value, updated_at = excluded.updated_at`,
    [KEY, JSON.stringify(epoch), epoch.at]
  );
  return epoch;
}

/** دورانِ همین حالا؛ نبود (دیتابیسی که هنوز بالا نیامده) ⇒ همین‌جا ساخته می‌شود. */
async function current() {
  const row = await one('SELECT value FROM app_config WHERE key = $1', [KEY]);
  if (row) {
    try {
      const e = JSON.parse(row.value);
      if (e && typeof e.id === 'string' && e.id) return e;
    } catch { /* خراب ⇒ تازه */ }
  }
  return rotate('missing');
}

/**
 * دورانِ یک حساب: پایه (سرِ دفترِ حساب در لحظهٔ دوران) و سرِ دفترِ همین حالا.
 * @param ctx  `req.sync` ِ `requireSyncAccount` — {app, accountKind, accountId}
 * @param {'v1'|'v3'} line
 * @returns {Promise<{id:string, at:number, base:number, head:number}>}
 */
async function forAccount(ctx, line = 'v1') {
  const e = await current();
  const table = line === 'v3' ? 'sync_v3_ops' : 'oplog';
  const bound = Number(line === 'v3' ? e.seq_v3 : e.seq) || 0;
  const args = [ctx.app, ctx.accountKind, ctx.accountId];
  const acc = 'app=$1 AND account_kind=$2 AND account_id=$3';
  const base = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table} WHERE ${acc} AND server_seq <= $4`, [...args, bound]);
  const head = await one(`SELECT COALESCE(MAX(server_seq),0)::bigint AS m FROM ${table} WHERE ${acc}`, args);
  return { id: e.id, at: Number(e.at) || 0, base: Number(base.m) || 0, head: Number(head.m) || 0 };
}

module.exports = { rotate, current, forAccount, KEY };
