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
 *
 * ⛔ `lib/sync-v1.js` مشترک با پمپ است و دست نمی‌خورد، پس `ctx` را به `sync-v1-migrations` نمی‌رساند.
 * به جایش `routes/sync-v1.js` بقیهٔ درخواستِ دکان را زیرِ [within] می‌برد (`AsyncLocalStorage`) و
 * `currentVersion`/`upgrade` ِ بی `ctx` پرچم را از همین‌جا می‌خوانند ([ctxOf]). فهرستِ جدول‌های
 * `/status` را هم همان مسیر با [allowed] می‌پالاید. بیرون از این درخواست (پمپ، کارِ پس‌زمینه، v3
 * که `ctx` را صریح می‌دهد) هیچ چیزی در این ظرف نیست.
 */
const { AsyncLocalStorage } = require('node:async_hooks');
const { many } = require('../db');
const { SHOP } = require('./tenancy');

/** `ctx` ِ همگام‌سازیِ دکان در درخواستِ Sync v1 ِ همین لحظه */
const scope = new AsyncLocalStorage();

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

/**
 * میان‌افزارِ `routes/sync-v1.js` پس از `requireSyncAccount`: بقیهٔ درخواستِ **دکان** زیرِ `ctx` ِ خودش؛
 * پمپ (یا بی ctx) همان `next()`.
 */
function within(req, res, next) {
  const ctx = req.sync;
  return isShop(ctx) ? scope.run(ctx, next) : next();
}

/** حسابِ دکان در بخشِ دکان (حسابِ پمپ `station` است) */
const isShop = (ctx) => Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app);

/** فهرستِ جدول‌های `/status` ِ v1: جدولِ پشتِ پرچمِ خاموشِ دکان نه؛ پمپ همان */
const tablesFor = (ctx, tables) => (isShop(ctx) && Array.isArray(tables) ? tables.filter((t) => allowed(t, ctx)) : tables);

/** `ctx` ِ صریح، وگرنه `ctx` ِ درخواستِ Sync v1 ِ دکانی که همین حالا در کار است ([within])؛ هیچ ⇒ null */
const ctxOf = (ctx) => ctx ?? scope.getStore() ?? null;

module.exports = { FLAG, GATED, enabledFor, on, allowed, within, ctxOf, tablesFor };
