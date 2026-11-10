#!/usr/bin/env node
'use strict';
/**
 * S86 — بازسازیِ مانده‌های موجودیِ یک دکان از دفترِ حرکت‌ها (`stock.rebuild`)، با `--catalog` کالاهای کاتالوگ
 * (`catalog_products`) از دفترِ دستگاه‌ها (`catalog-products.rebuildProducts`) و با `--reserve` نگه‌داشتهٔ موجودیِ
 * سفارش‌های باز (`stock_reservations` ِ O-06 ب، `stock-reserve.rebuild`).
 *
 *   node scripts/stock-rebuild.js --shop <شناسهٔ دکان>               فقط نشان می‌دهد (هیچ نوشتنی)
 *   node scripts/stock-rebuild.js --shop <شناسه> --apply [--by <نام>]   مانده = جمعِ دفتر، با سابقهٔ `stock.rebuild`
 *   … --catalog                                                   کاتالوگ هم (تعداد، قیمتِ نمایش، وضعیت؛ سابقهٔ `catalog.rebuild`)
 *   … --reserve                                                   نگه‌داشته‌ها هم (گم/پایان/گذشته/تحویل/ناهم‌اندازه؛ سابقهٔ `reserve.rebuild`)
 *   … --json                                                      خروجیِ ماشینی
 *
 * دفترها (`stock_moves`، دفترِ همگام‌سازی) هرگز دست نمی‌خورند. پیش از `--apply` پشتیبان بگیرید (`npm run backup`).
 * کدِ خروج: ۰ همه با منبع می‌خوانند (یا با `--apply` درست شدند) · ۲ ناهمخوانی (بی `--apply`) · ۱ خطا
 */

function parseArgs(argv) {
  const out = { shop: '', apply: false, by: '', json: false, catalog: false, reserve: false };
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i];
    if (a === '--apply') out.apply = true;
    else if (a === '--json') out.json = true;
    else if (a === '--catalog') out.catalog = true;
    else if (a === '--reserve') out.reserve = true;
    else if (a === '--shop' && argv[i + 1]) out.shop = String(argv[++i]);
    else if (a === '--by' && argv[i + 1]) out.by = String(argv[++i]);
    else throw new Error(`گزینهٔ ناشناخته: ${a}`);
  }
  if (!out.shop) throw new Error('--shop <شناسهٔ دکان> لازم است');
  return out;
}

/** ⇒ کدِ خروج. `print` برای آزمون جایگزین می‌شود. */
async function main(argv, print = (s) => console.log(s)) {
  const opts = parseArgs(argv);
  const { one } = require('../src/db');
  if (!(await one('SELECT 1 AS x FROM shops WHERE id=$1', [opts.shop]))) throw new Error(`دکانِ ${opts.shop} پیدا نشد`);
  const stock = require('../src/lib/stock');
  const r = await stock.rebuild(opts.shop, { apply: opts.apply, by: opts.by || 'cli' });
  const cat = opts.catalog
    ? await require('../src/lib/catalog-products').rebuildProducts(opts.shop, { apply: opts.apply, by: opts.by || 'cli' }) : null;
  const res = opts.reserve
    ? await require('../src/lib/stock-reserve').rebuild(opts.shop, { apply: opts.apply, by: opts.by || 'cli' }) : null;
  const ok = r.ok && (!cat || cat.ok) && (!res || res.ok);
  if (opts.json) {
    print(JSON.stringify(cat || res ? { stock: r, ...(cat ? { catalog: cat } : {}), ...(res ? { reserve: res } : {}) } : r, null, 2));
    return ok ? 0 : 2;
  }
  if (!r.fixed.length) {
    print(`✔ ${opts.shop} — مانده‌ها با دفتر می‌خوانند (${r.moves} حرکت)`);
  } else {
    print(`${r.applied ? '✔ درست شد' : '✖ ناهمخوان'}: ${r.fixed.length} مانده (${r.moves} حرکت)`);
    for (const f of r.fixed.slice(0, 200)) print(`  ${f.productId} @ ${f.location}: ${f.levelMilli} ⇒ ${f.ledgerMilli}`);
    if (!r.applied) print('برای درست کردن همان دستور را با --apply بزنید (پیش از آن پشتیبان بگیرید).');
  }
  if (cat && !cat.changes.length) {
    print(`✔ کاتالوگ (${cat.source}) — ${cat.count.source} کالا با دفترِ دستگاه‌ها می‌خواند`);
  } else if (cat) {
    print(`${cat.applied ? '✔ کاتالوگ درست شد' : '✖ کاتالوگ ناهمخوان'}: ${cat.changes.length} کالا (${cat.count.projection} ⇒ ${cat.count.source})`);
    for (const ch of cat.changes.slice(0, 200)) print(`  ${ch.productId}: ${ch.kind}${ch.fields ? ` (${ch.fields.join('، ')})` : ''}`);
    if (!cat.applied) print('برای درست کردن همان دستور را با --apply بزنید (پیش از آن پشتیبان بگیرید).');
  }
  if (res && !res.changes.length) {
    print(`✔ نگه‌داشته‌ها — ${res.count.projection} نگه‌داشته با دفتر و سفارش‌ها می‌خواند`);
  } else if (res) {
    print(`${res.applied ? '✔ نگه‌داشته‌ها درست شد' : '✖ نگه‌داشته‌ها ناهمخوان'}: ${res.changes.length} (${res.count.projection} ⇒ ${res.count.source})`);
    for (const ch of res.changes.slice(0, 200)) {
      print(`  ${ch.kind} ${ch.orderId}#${ch.lineNo} ${ch.productId}: ${ch.qtyMilli}${ch.toMilli !== undefined ? ` ⇒ ${ch.toMilli}` : ''}`);
    }
    if (!res.applied) print('برای درست کردن همان دستور را با --apply بزنید (پیش از آن پشتیبان بگیرید).');
  }
  return ok ? 0 : 2;
}

if (require.main === module) {
  const { closeDb } = require('../src/db');
  main(process.argv.slice(2))
    .then(async (code) => { await closeDb(); process.exit(code); })
    .catch(async (err) => {
      console.error('✖', err.message);
      try { await closeDb(); } catch { /* بسته بود */ }
      process.exit(1);
    });
}

module.exports = { main, parseArgs };
