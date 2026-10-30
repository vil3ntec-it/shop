'use strict';
/**
 * پرچمِ همگام‌سازیِ گاوصندوق (PAY-18): `live_config` با کلیدِ `sync.safe_entries`، scope = شناسهٔ دکان
 * یا `all`؛ مقدارِ خودِ دکان بر `all` جلو می‌افتد و فقط `true` روشن است (همان قاعدهٔ `orders.flagOf`).
 * ⛔ پیش‌فرض خاموش.
 *
 * خاموش ⇒ سرور همان main است: پلهٔ ۳ِ schema ِ دکان (`sync-v1-migrations.FLAGGED`)، جدولِ
 * `safeEntries` در v1 و v3 ناشناخته (`unknown_table`)، بی قفلِ حساب، راهِ قدیم همان «رد شد» ِ
 * بی‌صدا، و `/status` و `/hello` بی این جدول. روشن ⇒ پلهٔ ۴ و نگهبانِ `safe-ledger.js`.
 * برنامه‌ها پلهٔ همان سرور را می‌فرستند (`wireSchema` ِ `LedgerDiff.kt` و `sync-core.js`)، پس
 * برنامهٔ پلهٔ ۴ روی دکانِ پرچم‌خاموش ۴۲۶ نمی‌گیرد.
 *
 * پرچم یک بار برای هر درخواستِ همگام‌سازی خوانده می‌شود و روی `ctx.safeEntries` می‌نشیند
 * (`sync-v1-auth.requireSyncAccount`)؛ هر جا `ctx` نیست (یا پرچم ندارد) یعنی خاموش.
 */
const { many } = require('../db');

const FLAG = 'sync.safe_entries';
/** جدول‌هایی که فقط با پرچمِ روشن وجود دارند */
const GATED = new Set(['safeEntries']);

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** پرچمِ همین دکان (یا `all`) */
async function enabledFor(shopId) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG, 'all', String(shopId || '')]
  );
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

/** پرچمِ روشن روی همین ctx؟ */
const on = (ctx) => ctx?.safeEntries === true;

/** این جدول برای این ctx هست؟ (جدولِ بیرون از `GATED` همیشه) */
const allowed = (table, ctx) => !GATED.has(table) || on(ctx);

module.exports = { FLAG, GATED, enabledFor, on, allowed };
