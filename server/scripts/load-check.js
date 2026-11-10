#!/usr/bin/env node
'use strict';
/**
 * درستی پس از آزمونِ بار (PERF-02) — دفترِ بارساز (`load-scale.mjs` ⇒ `ledger()`) در برابرِ پایگاه.
 *
 *   node scripts/load-test.mjs --db … --profile scale --check [--ledger out.json]   # درونِ سرورِ آزمون، پس از بار
 *   node scripts/load-check.js --db postgres://…/x_bench --ledger out.json          # دوباره، روی همان پایگاه
 *
 * هر سنجه جدا (✅/❌) و `ok` = همه سبز:
 *   ops.lost        هر op ِ پذیرفته (applied/duplicate/conflict/partial) در همان دکان هست؛ رد‌شده نیست؛ op ِ بی‌صاحب نیست
 *   ops.unresolved  پس از پایان‌دادنِ صف‌ها هیچ op ای بی‌سرانجام نماند (مهلت/۵۰۳ همه دوباره رفتند)
 *   ops.dup         هیچ ردیفی دو op ِ insert ندارد؛ هیچ دستگاهی یک op را دو بار در pull نگرفت
 *   pull.complete   هر دستگاه همهٔ op های دستگاه‌های دیگرِ دکانش را گرفت (cursor جا نینداخت)
 *   totals.sales    شمار و جمعِ فروشِ هر دکان = دفتر
 *   totals.stock    جمعِ حرکتِ هر کالا در هر دکان = دفتر
 *   counts          شمارِ ردیف‌های شمارشِ هر دکان = دفتر؛ و برای تازه‌ترین شمارشِ هر کالا، جمعِ **همهٔ** حرکت‌های کالا تا HLC ِ آن
 *                   (تعدیلِ شمارش، «تعدیلِ دیررسیده» ِ حرکتی که پس از شمارش با HLC ِ پیش از آن رسید) = شمرده‌شده
 *   lww.prices      بهای هر کالای ویرایش‌شده = برندهٔ HLC؛ کالای دست‌نخورده همان بهای کاشته
 *   customers       هر مشتریِ تازه با نام (برندهٔ HLC) و تلفنِ درست، و هیچ مشتریِ اضافه
 *   held            نگه‌داشته‌های زندهٔ هر دکان = دفتر
 *   orders          شمارِ سفارش‌های ویترین میانِ «پذیرفته» و «پذیرفته + نامعلوم»
 *   tenant          هیچ op، ردیف، نگه‌داشته یا سفارشی در دکانِ دیگر؛ هیچ دادهٔ دکانِ دیگری در پاسخ‌ها؛ کاوشِ ویترینِ دکانِ دیگر ۴۰۴
 *   http.5xx        هیچ ۵xx جز ۵۰۳ ِ کنترل‌شده با `Retry-After` (`db_busy` ِ استخر/دروازه، و `request_timeout` ِ `REQUEST_TIMEOUT_MS` وقتی
 *                   روشن است: کار شاید پشتِ آن تمام شد، پس op ِ آن «نامعلوم» است و با همان op_id دوباره می‌رود)؛ هیچ مهلت یا اتصالِ بریده
 * ⛔ فقط می‌خواند. دادهٔ بارساز با نشان‌هایش شناخته می‌شود (`lo-`، `ls-`/`lm-`/`lp-`/`lc-`، `hx<n>-`، `~S<n>~`).
 */

const ACCEPTED = new Set(['applied', 'duplicate', 'conflict', 'partial']);
const SHOP_OF_ID = /^l[a-z]-l[dh]-(\d+)-/;
const SHOP_OF_TAG = /~S(\d+)~/;

async function check(db, L) {
  const many = async (sql, params) => (await db.query(sql, params)).rows;
  const ids = L.shops.map((s) => s.shopId);
  const idx = new Map(L.shops.map((s) => [s.shopId, s.index]));
  const checks = [];
  const add = (name, ok, detail, samples) => checks.push({ name, ok, detail, ...(samples && samples.length ? { samples: samples.slice(0, 8) } : {}) });
  const ACC = `app='shop' AND account_kind='shop' AND account_id = ANY($1)`;

  // ── op ها ──
  const srv = await many(`SELECT op_id, account_id, device_id, status, kind, table_name, row_id, server_seq, (effect IS NOT NULL) AS eff
                            FROM sync_v3_ops WHERE ${ACC} AND op_id LIKE 'lo-%'`, [ids]);
  const byOp = new Map();
  for (const r of srv) { const a = byOp.get(r.op_id); if (a) a.push(r); else byOp.set(r.op_id, [r]); }
  const ledgerOps = new Map(L.ops.map(([id, shop, st]) => [id, { shop, st }]));
  const lost = [], storedRejected = [], phantom = [], unresolved = [], wrongShop = [];
  const statusMix = {};
  for (const [id, { shop, st }] of ledgerOps) {
    const rows = byOp.get(id) || [];
    const own = rows.find((r) => idx.get(r.account_id) === shop);
    if (rows.some((r) => idx.get(r.account_id) !== shop)) wrongShop.push(id);
    if (ACCEPTED.has(st)) { if (!own) lost.push(id); else statusMix[own.status] = (statusMix[own.status] || 0) + 1; }
    else if (st === 'rejected') { if (own) storedRejected.push(id); }
    else unresolved.push(`${id}:${st}`);
  }
  for (const id of byOp.keys()) if (!ledgerOps.has(id)) phantom.push(id);
  const accepted = [...ledgerOps.values()].filter((o) => ACCEPTED.has(o.st)).length;
  const rejected = [...ledgerOps.values()].filter((o) => o.st === 'rejected').length;
  add('ops.lost', !lost.length && !storedRejected.length && !phantom.length,
    `${ledgerOps.size} op در دفتر، ${accepted} پذیرفته (روی سرور: ${JSON.stringify(statusMix)})، ${rejected} رد؛ گم‌شده ${lost.length}، رد ولی نوشته ${storedRejected.length}، بی‌صاحب ${phantom.length}`,
    [...lost, ...storedRejected, ...phantom]);
  add('ops.unresolved', !unresolved.length && !(L.drained && L.drained.left),
    `بی‌سرانجام ${unresolved.length}، در صف مانده ${(L.drained && L.drained.left) || 0}`, unresolved);

  const dupRows = await many(`SELECT account_id, table_name, row_id, count(*)::int AS n FROM sync_v3_ops
                                WHERE ${ACC} AND op_id LIKE 'lo-%' AND kind = 'insert' GROUP BY 1, 2, 3 HAVING count(*) > 1`, [ids]);
  const pullDup = L.devices.reduce((s, d) => s + (d.dup || 0), 0);
  add('ops.dup', !dupRows.length && !pullDup, `ردیفِ دو insert ${dupRows.length}، op ِ تکراری در pull ${pullDup}`,
    dupRows.map((r) => `${r.table_name}/${r.row_id}×${r.n}`));

  // ── pull ِ هر دستگاه ──
  const byShop = new Map();
  for (const r of srv) if (r.eff) { const s = idx.get(r.account_id); (byShop.get(s) || byShop.set(s, []).get(s)).push(r); }
  //  «جاافتاده» = زیرِ cursor ِ پایانیِ دستگاه ولی هرگز نرسید (cursor از رویش پرید: خطای سرور)؛ «نرسیده» = بالای آن (دستگاه تا سر نرفت)
  let skipped = 0, behind = 0, expected = 0, foreignSeen = 0;
  const missSamples = [];
  for (const d of L.devices) {
    const seen = new Set(d.seen);
    for (const id of d.seen) { const m = SHOP_OF_ID.exec(id); if (m && Number(m[1]) !== d.shop) foreignSeen++; }
    for (const r of byShop.get(d.shop) || []) {
      if (r.device_id === d.id) continue;
      expected++;
      if (seen.has(r.op_id)) continue;
      if (Number(r.server_seq) <= Number(d.cursor)) skipped++; else behind++;
      if (missSamples.length < 8) missSamples.push(`${d.id}@${d.cursor}⇐${r.op_id}@${r.server_seq}`);
    }
  }
  add('pull.complete', !skipped && !behind,
    `${L.devices.length} دستگاه، ${expected} op ِ دستگاهِ دیگر باید می‌رسید؛ جاافتاده زیرِ cursor ${skipped}، نرسیده بالای cursor ${behind}`, missSamples);

  // ── جمع‌های هر دکان ──
  const sales = await many(`SELECT account_id, count(*)::int AS n, COALESCE(sum((data->>'finalTotalMinor')::bigint), 0)::bigint AS t
                              FROM sync_v3_rows WHERE ${ACC} AND table_name = 'sales' AND row_id LIKE 'ls-%' GROUP BY 1`, [ids]);
  const saleSrv = new Map(sales.map((r) => [idx.get(r.account_id), { n: r.n, t: Number(r.t) }]));
  const saleBad = [];
  let saleN = 0, saleT = 0;
  for (const sh of L.shops) {
    const want = (L.sales && L.sales[sh.index]) || { n: 0, totalMinor: 0 };
    const got = saleSrv.get(sh.index) || { n: 0, t: 0 };
    saleN += want.n; saleT += want.totalMinor;
    if (got.n !== want.n || got.t !== want.totalMinor) saleBad.push(`دکانِ ${sh.index}: ${got.n}/${got.t} ≠ ${want.n}/${want.totalMinor}`);
  }
  add('totals.sales', !saleBad.length, `${L.shops.length} دکان، ${saleN} فروش، جمع ${saleT}؛ ناجور ${saleBad.length}`, saleBad);

  const moves = await many(`SELECT account_id, data->>'productId' AS pid, COALESCE(sum((data->>'qtyMilli')::bigint), 0)::bigint AS q
                              FROM sync_v3_rows WHERE ${ACC} AND table_name = 'stockMovements' AND (row_id LIKE 'ls-%' OR row_id LIKE 'lm-%')
                             GROUP BY 1, 2`, [ids]);
  const mv = new Map(moves.map((r) => [`${idx.get(r.account_id)}|${r.pid}`, Number(r.q)]));
  const mvBad = [];
  let mvN = 0;
  const wantMv = new Map();
  for (const [s, m] of Object.entries(L.moves || {})) for (const [pid, q] of Object.entries(m)) wantMv.set(`${s}|${pid}`, q);
  for (const k of new Set([...mv.keys(), ...wantMv.keys()])) {
    mvN++;
    if ((mv.get(k) || 0) !== (wantMv.get(k) || 0)) mvBad.push(`${k}: ${mv.get(k) || 0} ≠ ${wantMv.get(k) || 0}`);
  }
  add('totals.stock', !mvBad.length, `${mvN} (دکان، کالا)؛ ناجور ${mvBad.length}`, mvBad);

  // ── شمارش (انبارگردانی) ──
  const cRows = await many(`SELECT account_id, count(*)::int AS n FROM sync_v3_rows
                             WHERE ${ACC} AND table_name = 'stockCounts' AND row_id LIKE 'lk-%' GROUP BY 1`, [ids]);
  const cAt = await many(`SELECT c.account_id, c.pid, c.row_id, c.counted, c.row_hlc,
         COALESCE((SELECT sum((m.data->>'qtyMilli')::bigint) FROM sync_v3_rows m
                    WHERE m.app = 'shop' AND m.account_kind = 'shop' AND m.account_id = c.account_id AND m.table_name = 'stockMovements'
                      AND m.inserted AND m.data->>'productId' = c.pid AND m.row_hlc <= c.row_hlc), 0)::bigint AS at,
         (SELECT count(*)::int FROM sync_v3_rows l
           WHERE l.app = 'shop' AND l.account_kind = 'shop' AND l.account_id = c.account_id AND l.table_name = 'stockMovements'
             AND l.inserted AND l.data->>'productId' = c.pid AND l.data->>'type' = 'count_adjust_late') AS late
    FROM (SELECT DISTINCT ON (account_id, data->>'productId') account_id, data->>'productId' AS pid, row_id, row_hlc,
                 (data->>'countedQtyMilli')::bigint AS counted
            FROM sync_v3_rows WHERE ${ACC} AND table_name = 'stockCounts' AND inserted AND row_id LIKE 'lk-%'
           ORDER BY account_id, data->>'productId', row_hlc DESC) c`, [ids]);
  const kBad = [];
  const kSrv = new Map(cRows.map((r) => [idx.get(r.account_id), r.n]));
  let kWant = 0, kLate = 0;
  for (const sh of L.shops) {
    const want = (L.counts && L.counts[sh.index]) || 0;
    kWant += want;
    if ((kSrv.get(sh.index) || 0) !== want) kBad.push(`دکانِ ${sh.index}: ${kSrv.get(sh.index) || 0} ردیفِ شمارش ≠ ${want}`);
  }
  for (const r of cAt) {
    kLate += r.late;
    if (Number(r.at) !== Number(r.counted)) kBad.push(`${idx.get(r.account_id)}|${r.pid}: تا ${r.row_hlc} ${r.at} ≠ شمرده ${r.counted} (${r.row_id})`);
  }
  add('counts', !kBad.length, `${kWant} ردیفِ شمارش، ${cAt.length} (دکان، کالا) ِ شمرده؛ ${kLate} تعدیلِ دیررسیده؛ ناجور ${kBad.length}`, kBad);

  // ── LWW ِ بها ──
  const priceRows = await many(`SELECT account_id, row_id, (data->>'salePriceMinor')::bigint AS p FROM sync_v3_rows
                                  WHERE ${ACC} AND table_name = 'products'`, [ids]);
  const price = new Map(priceRows.map((r) => [`${idx.get(r.account_id)}|${r.row_id}`, r.p === null ? null : Number(r.p)]));
  const pBad = [];
  let edited = 0, untouched = 0;
  for (const [s, m] of Object.entries(L.prices || {})) for (const [pid, w] of Object.entries(m)) {
    edited++;
    if (price.get(`${s}|${pid}`) !== w.minor) pBad.push(`ویرایش ${s}|${pid}: ${price.get(`${s}|${pid}`)} ≠ ${w.minor}`);
  }
  for (const [s, m] of Object.entries(L.seedPrices || {})) for (const [pid, minor] of Object.entries(m)) {
    untouched++;
    if (price.get(`${s}|${pid}`) !== minor) pBad.push(`دست‌نخورده ${s}|${pid}: ${price.get(`${s}|${pid}`)} ≠ ${minor}`);
  }
  add('lww.prices', !pBad.length, `${edited} کالای ویرایش‌شده، ${untouched} دست‌نخورده؛ ناجور ${pBad.length}`, pBad);

  // ── مشتری ──
  const cust = await many(`SELECT account_id, row_id, data->>'name' AS name, data->>'phone' AS phone FROM sync_v3_rows
                             WHERE ${ACC} AND table_name = 'debtors' AND row_id LIKE 'lc-%'`, [ids]);
  const cSrv = new Map(cust.map((r) => [`${idx.get(r.account_id)}|${r.row_id}`, r]));
  const cBad = [];
  let cWant = 0;
  for (const [s, m] of Object.entries(L.debtors || {})) for (const [row, w] of Object.entries(m)) {
    if (!w.inserted) continue;
    cWant++;
    const g = cSrv.get(`${s}|${row}`);
    if (!g) cBad.push(`${s}|${row}: نیست`);
    else if (g.name !== w.name || g.phone !== w.phone) cBad.push(`${s}|${row}: ${g.name}/${g.phone} ≠ ${w.name}/${w.phone}`);
  }
  if (cSrv.size !== cWant) cBad.push(`روی سرور ${cSrv.size} مشتریِ بارساز، در دفتر ${cWant}`);
  add('customers', !cBad.length, `${cWant} مشتریِ تازه؛ ناجور ${cBad.length}`, cBad);

  // ── نگه‌داشته ──
  const heldRows = await many(`SELECT shop_id, id, deleted FROM shop_held_invoices WHERE shop_id = ANY($1) AND id LIKE 'hx%'`, [ids]);
  const hBad = [];
  const hLive = new Map();
  for (const r of heldRows) {
    const m = /^hx(\d+)-/.exec(r.id);
    if (!m || Number(m[1]) !== idx.get(r.shop_id)) hBad.push(`نگه‌داشتهٔ ${r.id} در دکانِ ${idx.get(r.shop_id)}`);
    if (!r.deleted) hLive.set(`${idx.get(r.shop_id)}|${r.id}`, true);
  }
  let hWant = 0, hUnknown = 0;
  for (const [s, x] of Object.entries(L.held || {})) {
    const unk = new Set(x.unknown);
    hUnknown += unk.size;
    for (const id of x.live) { hWant++; if (!hLive.has(`${s}|${id}`)) hBad.push(`${s}|${id}: باید زنده باشد`); }
    for (const id of x.gone) if (hLive.has(`${s}|${id}`)) hBad.push(`${s}|${id}: باید رفته باشد`);
    for (const k of hLive.keys()) if (k.startsWith(`${s}|`) && !x.live.includes(k.slice(s.length + 1)) && !unk.has(k.slice(s.length + 1))) hBad.push(`${k}: زندهٔ بی‌دفتر`);
  }
  add('held', !hBad.length, `${hWant} نگه‌داشتهٔ زنده (روی سرور ${hLive.size})، ${hUnknown} با سرانجامِ نامعلوم؛ ناجور ${hBad.length}`, hBad);

  // ── سفارشِ ویترین ──
  const ord = await many(`SELECT shop_id, customer_name, count(*)::int AS n FROM online_orders WHERE shop_id = ANY($1) GROUP BY 1, 2`, [ids]);
  const oBad = [];
  const oSrv = new Map();
  for (const r of ord) {
    const m = SHOP_OF_TAG.exec(r.customer_name || '');
    if (m && Number(m[1]) !== idx.get(r.shop_id)) oBad.push(`سفارشِ «${r.customer_name}» در دکانِ ${idx.get(r.shop_id)}`);
    oSrv.set(idx.get(r.shop_id), (oSrv.get(idx.get(r.shop_id)) || 0) + r.n);
  }
  let oOk = 0;
  for (const [s, c] of Object.entries(L.checkouts || {})) {
    oOk += c.ok;
    const got = oSrv.get(Number(s)) || 0;
    if (got < c.ok || got > c.ok + c.unknown) oBad.push(`دکانِ ${s}: ${got} سفارش، پذیرفته ${c.ok} + نامعلوم ${c.unknown}`);
  }
  add('orders', !oBad.length, `${oOk} سفارشِ پذیرفته (روی سرور ${[...oSrv.values()].reduce((a, b) => a + b, 0)})؛ ناجور ${oBad.length}`, oBad);

  // ── جدایی دکان‌ها ──
  const rowsX = await many(`SELECT account_id, row_id FROM sync_v3_rows WHERE ${ACC} AND row_id ~ '^l[a-z]-l[dh]-[0-9]+-'`, [ids]);
  const tBad = [];
  for (const r of rowsX) { const m = SHOP_OF_ID.exec(r.row_id); if (Number(m[1]) !== idx.get(r.account_id)) tBad.push(`ردیفِ ${r.row_id} در دکانِ ${idx.get(r.account_id)}`); }
  for (const r of srv) { const m = SHOP_OF_ID.exec(r.op_id); if (m && Number(m[1]) !== idx.get(r.account_id)) tBad.push(`op ِ ${r.op_id} در دکانِ ${idx.get(r.account_id)}`); }
  for (const id of wrongShop) tBad.push(`op ِ ${id} در دکانِ دیگر هم هست`);
  const f = L.foreign || { count: 0, samples: [] };
  const x = L.xt || { probes: 0, violations: 0, samples: [] };
  add('tenant', !tBad.length && !foreignSeen && !f.count && !x.violations && !oBad.some((s) => s.startsWith('سفارشِ')),
    `${rowsX.length} ردیف و ${srv.length} op ِ بارساز: بیرون از دکانِ خود ${tBad.length}؛ op ِ دکانِ دیگر در pull ${foreignSeen}؛ دادهٔ دکانِ دیگر در پاسخ ${f.count}؛ کاوشِ ویترین ${x.probes}، نشت ${x.violations}`,
    [...tBad, ...f.samples.map((s) => JSON.stringify(s)), ...x.samples.map((s) => JSON.stringify(s))]);

  // ── کدهای پاسخ ──
  const bad5 = [], netErr = [];
  let n503 = 0, nDeadline = 0;
  for (const [label, codes] of Object.entries(L.http || {})) for (const [c, n] of Object.entries(codes)) {
    if (c === '503:db_busy') n503 += n;
    else if (c === '503:request_timeout') nDeadline += n;
    else if (/^5\d\d/.test(c)) bad5.push(`${label} ${c}×${n}`);
    else if (!/^\d{3}$/.test(c)) netErr.push(`${label} ${c}×${n}`);
  }
  add('http.5xx', !bad5.length && !netErr.length, `۵۰۳ ِ db_busy ${n503}${nDeadline ? `، ۵۰۳ ِ request_timeout ${nDeadline}` : ''}؛ ۵xx ِ دیگر ${bad5.length}؛ مهلت/اتصالِ بریده ${netErr.length}`, [...bad5, ...netErr]);

  return { ok: checks.every((c) => c.ok), checks };
}

module.exports = { check };

if (require.main === module) {
  const argv = process.argv.slice(2);
  const arg = (k) => { const i = argv.indexOf(k); return i >= 0 ? argv[i + 1] : undefined; };
  const url = arg('--db'), file = arg('--ledger');
  if (!url || !file || /^pglite:/i.test(url)) {
    console.error('node scripts/load-check.js --db postgres://… --ledger <file> (PGlite ِ در حافظه را فقط خودِ load-test.mjs --check می‌بیند)');
    process.exit(2);
  }
  (async () => {
    const { Client } = require('pg');
    const c = new Client({ connectionString: url });
    await c.connect();
    try {
      const out = await check({ query: (t, p) => c.query(t, p) }, JSON.parse(require('fs').readFileSync(file, 'utf8')));
      for (const k of out.checks) {
        console.log(`${k.ok ? '✅' : '❌'} ${k.name}: ${k.detail}`);
        for (const s of k.samples || []) console.log(`     ${s}`);
      }
      process.exitCode = out.ok ? 0 : 1;
    } finally { await c.end(); }
  })().catch((e) => { console.error(e); process.exit(2); });
}
