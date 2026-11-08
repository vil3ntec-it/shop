'use strict';
/**
 * فروشِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد — INV-10 (§۶.۵، S20)، سمتِ دفترِ موجودیِ سرور.
 *
 * دستگاهِ A آفلاین ۳ فروخت (۱۰:۰۰)، دستگاهِ B ساعتِ ۱۰:۳۰ ۱۷ شمرد (ردیفِ انبارِ −۳)؛ فروشِ A ساعتِ ۱۱:۰۰ رسید و
 * دفترِ سرور ۱۴ می‌داد. همان قاعدهٔ برنامه (`license/count-absorb-core.js` = `count-absorb-core.js` ِ همین پوشه، بایت‌به‌بایت؛
 * قاعدهٔ دقیق سرِ `core/stock/CountAbsorb.kt`) این‌جا روی همان سندها:
 *   • `collect` — درونِ `catalog.ingestTx` (همان خواندنِ افزایشیِ دفترِ همگام‌سازیِ v1 **یا** v3؛ `sync-v1.js` دست
 *     نمی‌خورد): ردیفِ شمارش (`warehouseEntries` با `countedAt`/`seenMilli`، یا ردیفِ قدیمیِ «انبارگردانی» بی لحظه) ⇒
 *     `stock_count_marks` (با `server_at` = ساختِ همان ردیف روی سرور)، و هر `stockMovements` ⇒ `stock_device_events`.
 *   • `settle` — پس از خواندن: برای هر کالای دست‌خورده «جذب‌شده» از نو حساب و تفاوتش با سهمِ پیشین (`stock_sources`،
 *     کلیدِ `ca:<کالا>`) یک حرکتِ افزودنیِ `adjustment` با دلیلِ `count_absorb` و یادداشتِ «پیش از شمارش ثبت شده بود
 *     — در شمارش آمده است» می‌شود. فروشِ دیررسیده خودش همان‌طور در دفتر، گزارش و پول می‌ماند.
 * نگهبانِ ساعت (فقط سرور): ردیفی که لحظه‌اش بیش از ۵ دقیقه پس از ساختِ خودِ سندش روی سرور است جذب نمی‌کند
 * (`serverAt`)؛ v3: زمانِ تصحیح‌شدهٔ سرور (`correctedAt`) روی ردیف و حرکت (همان سندها برای دستگاه‌ها).
 * ⛔ پرچمِ `shop.count_absorb` (پیش‌فرض خاموش): خاموش ⇒ هر سهمِ پیشین با حرکتِ برعکس صفر می‌شود و حرکتِ تازه‌ای نیست.
 */
const absorb = require('./count-absorb-core');
const stock = require('./stock');

const FLAG_KEY = 'shop.count_absorb';
const REASON = 'count_absorb';
const LABEL = 'پیش از شمارش ثبت شده بود — در شمارش آمده است';
const COUNT_NOTE = 'انبارگردانی';     //  داده: سرِ یادداشتِ ردیفِ شمارش (`WarehouseEngine.stockCount`، نسخهٔ وب)

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (v === null || v === undefined ? 0 : Number(v));
const int = (v) => (Number.isSafeInteger(v) ? v : null);
const big = (v) => (v === null || v === undefined ? null : Number(v));
const isDeleted = (r) => r.deleted_at !== null && r.deleted_at !== undefined;

function decode(v) {
  if (v === null || v === undefined) return {};
  if (typeof v !== 'string') return v;
  try { return JSON.parse(v) || {}; } catch { return {}; }
}

/** همان `CountAbsorbWiring.isCountLine` ِ برنامه */
function isCountLine(d) {
  return d.isAdjustment === true && !!str(d.productId) &&
    (int(d.countedAt) !== null || d.adjKind === 'count' || String(d.notes || '').startsWith(COUNT_NOTE));
}

async function oldProducts(c, table, shopId, ids) {
  if (!ids.length) return [];
  return (await c.query(`SELECT product_id FROM ${table} WHERE shop_id=$1 AND row_id = ANY($2::text[])`, [shopId, ids]))
    .rows.map((r) => r.product_id);
}

/**
 * یک صفحه از دفترِ همگام‌سازی ⇒ نگه‌داشتنِ ردیف‌های شمارش و حرکت‌ها. ⇒ کالاهای دست‌خورده (Set)
 * `rd.qty` همان خوانندهٔ دو قالبِ `catalog.js` است (v1 اعشاری، v3 `qtyMilli`).
 */
async function collect(c, shopId, rows, rd, at) {
  const touched = new Set();
  const marks = { keep: [], drop: [] };
  const events = { keep: [], drop: [] };
  for (const r of rows) {
    if (r.table_name !== 'warehouseEntries' && r.table_name !== 'stockMovements') continue;
    const d = decode(r.data);
    const serverAt = num(r.created_at) || at;
    const createdAt = int(d.createdAt) !== null && d.createdAt > 0 ? d.createdAt : serverAt;
    if (r.table_name === 'warehouseEntries') {
      if (isDeleted(r) || !isCountLine(d)) { marks.drop.push(r.row_id); continue; }
      const moment = int(d.countedAt) !== null && d.countedAt > 0 ? d.countedAt : null;
      marks.keep.push([r.row_id, str(d.productId), moment === null ? createdAt : moment,
        moment !== null && int(d.seenMilli) !== null ? d.seenMilli : null, createdAt, int(d.correctedAt), serverAt]);
    } else {
      const pid = str(d.productId);
      if (isDeleted(r) || !pid || !(int(d.createdAt) > 0)) { events.drop.push(r.row_id); continue; }
      events.keep.push([r.row_id, pid, str(d.type).slice(0, 40), rd.qty(d, 'qty'), d.createdAt,
        typeof d.refId === 'string' ? d.refId.slice(0, 128) : null, int(d.correctedAt), serverAt]);
    }
  }
  const all = (x) => [...x.keep.map((k) => k[0]), ...x.drop];
  for (const pid of await oldProducts(c, 'stock_count_marks', shopId, all(marks))) touched.add(pid);
  for (const pid of await oldProducts(c, 'stock_device_events', shopId, all(events))) touched.add(pid);
  if (marks.drop.length) await c.query('DELETE FROM stock_count_marks WHERE shop_id=$1 AND row_id = ANY($2::text[])', [shopId, marks.drop]);
  if (events.drop.length) await c.query('DELETE FROM stock_device_events WHERE shop_id=$1 AND row_id = ANY($2::text[])', [shopId, events.drop]);
  const col = (list, i) => list.map((k) => k[i]);
  if (marks.keep.length) {
    const k = marks.keep;
    await c.query(
      `INSERT INTO stock_count_marks (shop_id, row_id, product_id, counted_at, seen_milli, created_at, corrected_at, server_at, updated_at)
       SELECT $1, * , $9 FROM unnest($2::text[], $3::text[], $4::bigint[], $5::bigint[], $6::bigint[], $7::bigint[], $8::bigint[])
       ON CONFLICT (shop_id, row_id) DO UPDATE SET product_id=excluded.product_id, counted_at=excluded.counted_at,
         seen_milli=excluded.seen_milli, created_at=excluded.created_at, corrected_at=excluded.corrected_at, updated_at=excluded.updated_at`,
      [shopId, col(k, 0), col(k, 1), col(k, 2), col(k, 3), col(k, 4), col(k, 5), col(k, 6), at]
    );
    for (const pid of col(k, 1)) touched.add(pid);
  }
  if (events.keep.length) {
    const k = events.keep;
    await c.query(
      `INSERT INTO stock_device_events (shop_id, row_id, product_id, type, qty_milli, at, ref_id, corrected_at, server_at, updated_at)
       SELECT $1, * , $10 FROM unnest($2::text[], $3::text[], $4::text[], $5::bigint[], $6::bigint[], $7::text[], $8::bigint[], $9::bigint[])
       ON CONFLICT (shop_id, row_id) DO UPDATE SET product_id=excluded.product_id, type=excluded.type, qty_milli=excluded.qty_milli,
         at=excluded.at, ref_id=excluded.ref_id, corrected_at=excluded.corrected_at, updated_at=excluded.updated_at`,
      [shopId, col(k, 0), col(k, 1), col(k, 2), col(k, 3), col(k, 4), col(k, 5), col(k, 6), col(k, 7), at]
    );
    for (const pid of col(k, 1)) touched.add(pid);
  }
  return touched;
}

/** جذب‌شدهٔ یک کالا با همان هسته (فقط ردیف‌ها و پنجرهٔ حرکت‌های همان کالا) ⇒ {gapMilli, lineId} */
async function gapOf(c, shopId, productId) {
  const lines = (await c.query(
    `SELECT row_id, counted_at, seen_milli, created_at, corrected_at, server_at FROM stock_count_marks
      WHERE shop_id=$1 AND product_id=$2`, [shopId, productId]
  )).rows.map((r) => ({
    id: r.row_id, productId, countedAt: num(r.counted_at), seenMilli: big(r.seen_milli), createdAt: num(r.created_at),
    correctedAt: big(r.corrected_at), serverAt: num(r.server_at),
  }));
  const head = absorb.governing(lines).get(productId);
  if (!head || !absorb.trusted(head)) return { gapMilli: 0, lineId: head ? head.id : '' };
  const events = (await c.query(
    `SELECT row_id, type, qty_milli, at, ref_id, corrected_at FROM stock_device_events
      WHERE shop_id=$1 AND product_id=$2 AND at > $3 AND at <= $4 ORDER BY row_id`,
    [shopId, productId, head.countedAt - absorb.WINDOW_MS, head.countedAt]
  )).rows.map((r) => ({
    id: r.row_id, productId, type: r.type, qtyMilli: num(r.qty_milli), at: num(r.at), refId: r.ref_id, correctedAt: big(r.corrected_at),
  }));
  const g = absorb.gaps([head], events).get(productId);
  return { gapMilli: g ? g.gapMilli : 0, lineId: head.id };
}

/**
 * پس از خواندنِ دفتر (همان تراکنش و قفلِ موجودی): سهمِ «جذب‌شده» ِ کالاهای دست‌خورده، و با عوض شدنِ پرچم همهٔ
 * کالاهای دارای ردیفِ شمارش یا سهمِ پیشین. ⇒ کالاهایی که حرکت گرفتند
 */
async function settle(c, shopId, settings, at, touched) {
  const on = await require('./orders').flagOf(shopId, FLAG_KEY, (s, p) => c.query(s, p));
  const st = (await c.query('SELECT count_absorb FROM catalog_sync_state WHERE shop_id=$1', [shopId])).rows[0];
  const flipped = !st || st.count_absorb !== on;
  const posted = (await c.query(
    `SELECT product_id, posted_milli, rev FROM stock_sources WHERE shop_id=$1 AND source_key = 'ca:' || product_id`, [shopId]
  )).rows;
  const prev = new Map(posted.map((r) => [r.product_id, { milli: num(r.posted_milli), rev: num(r.rev) }]));
  if (!on && !flipped && !prev.size) return [];
  let ids;
  if (flipped) {
    ids = (await c.query('SELECT DISTINCT product_id FROM stock_count_marks WHERE shop_id=$1', [shopId])).rows.map((r) => r.product_id);
  } else {
    const list = [...touched];
    ids = list.length ? (await c.query(
      'SELECT DISTINCT product_id FROM stock_count_marks WHERE shop_id=$1 AND product_id = ANY($2::text[])', [shopId, list]
    )).rows.map((r) => r.product_id) : [];
    for (const pid of list) if (prev.has(pid)) ids.push(pid);
  }
  if (flipped) ids.push(...prev.keys());
  const moved = [];
  for (const pid of [...new Set(ids)].sort()) {
    const g = on ? await gapOf(c, shopId, pid) : { gapMilli: 0, lineId: '' };
    const want = -g.gapMilli;
    const was = prev.get(pid) || { milli: 0, rev: 0 };
    const delta = want - was.milli;
    if (delta === 0) continue;
    const rev = was.rev + 1;
    await stock.postMove(c, shopId, {
      productId: pid, location: settings.deviceLocation, type: 'adjustment', qtyMilli: delta,
      mode: 'recorded', source: 'device', sourceKey: `ca:${pid}:${rev}`, refType: 'warehouseEntries', refId: g.lineId,
      actorType: 'device', actorId: '', reason: REASON, note: LABEL, at,
    }, at);
    await c.query(
      `INSERT INTO stock_sources (shop_id, source_key, product_id, posted_milli, rev, updated_at) VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (shop_id, source_key, product_id) DO UPDATE
         SET posted_milli=excluded.posted_milli, rev=excluded.rev, updated_at=excluded.updated_at`,
      [shopId, `ca:${pid}`, pid, want, rev, at]
    );
    moved.push(pid);
  }
  if (flipped) await c.query('UPDATE catalog_sync_state SET count_absorb=$2 WHERE shop_id=$1', [shopId, on]);
  return moved;
}

module.exports = { FLAG_KEY, REASON, LABEL, isCountLine, collect, settle, gapOf };
