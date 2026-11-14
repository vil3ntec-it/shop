'use strict';
/**
 * کاتالوگِ مشترکِ شعبه‌ها — فازِ ۳ ِ سرور (`docs/BRANCHES-fa.md` §۲ و §۸؛ پرچمِ خاموشِ `shop.branches`).
 *
 * تعریفِ کالا، دسته و واحد (و قیمتِ پایه، فیلدِ همان کالا) **مالِ کلِ دکان** است و یک شناسه در همهٔ راه‌ها دارد (INV-01@32):
 *   ⛔ نوشتن فقط در بخشِ شعبهٔ اصلی (همان حسابِ دکان). هر op ِ دستگاه روی این جدول‌ها در بخشِ شعبهٔ غیرِ اصلی ⇒ رد با
 *      `catalog_main_only` (صاحب هم: صاحب همیشه شعبهٔ اصلی را دارد و کاتالوگ را از همان‌جا می‌نویسد). بی رد، کالای شعبه
 *      از کاتالوگِ دکان جدا می‌افتاد و موجودیِ سرورش (`catalog_products` ِ اصلی) آن را نمی‌شناخت.
 *   ⛔ آینه: هر ردیفِ این جدول‌ها در بخشِ اصلی با op ِ سرور (`origin: 'server'`، شناسهٔ `srv.cm.<شمارهٔ اصلی>`) در بخشِ شعبه
 *      می‌نشیند — دستگاهِ شعبه همان را با pull/Snapshot ِ بخشِ خودش می‌گیرد. فقط خواندنِ اصلی؛ اصلی هرگز نوشته نمی‌شود.
 *      ردیفِ برابر دوباره نوشته نمی‌شود؛ حذفِ اصلی ⇒ حذف در شعبه؛ op ِ تکراری (همان شناسه) بی اثر.
 *   ⛔ قفل‌ها پشتِ هم، نه تودرتو: پلِ v1⇒v3 ِ اصلی زیرِ قفلِ اصلی و تمام؛ سپس نوشتن زیرِ قفلِ همان بخشِ شعبه ⇒ بی بن‌بست با
 *      push ِ اصلی (که هرگز قفلِ شعبه را نمی‌گیرد).
 *   ⛔ «مانده»ٔ کالا (`stock`، derived) هرگز آینه نمی‌شود — موجودیِ هر شعبه از دفترِ خودش (`lib/stock.js` با `branch_id`).
 */
const { one, query, now } = require('../db');
const key = require('./branch-key');

const TABLES = Object.freeze(['products', 'productCategories', 'productUnits']);
//  همان فهرست، در متنِ پرسش (گزارهٔ نمایهٔ جزئیِ `ix_sync_v3_rows_catalog_seq` ِ مهاجرتِ ۹۰۰)
const TABLES_SQL = "table_name IN ('products', 'productCategories', 'productUnits')";
const OP_PREFIX = 'srv.cm.';
const PAGE = 500;

/** op ِ دستگاه روی کاتالوگ در بخشِ شعبهٔ غیرِ اصلی ⇒ دلیلِ رد؛ وگرنه null (بخشِ اصلی و op ِ سرور همیشه null). */
function judge(ctx, op, origin = 'v3') {
  if (origin === 'server' || !ctx || !ctx.branchId || !op) return null;
  return TABLES.includes(op.table) ? 'catalog_main_only' : null;
}

/** op ِ آینه (پیامدهای op ِ دستگاه — بارکدِ تکراری و … — برایش نیست؛ همان در بخشِ اصلی سنجیده شد) */
const isMirrorOp = (op) => !!op && typeof op.op_id === 'string' && op.op_id.startsWith(OP_PREFIX);

function decode(v) {
  if (typeof v !== 'string') return v && typeof v === 'object' ? v : {};
  try { return JSON.parse(v) || {}; } catch { return {}; }
}

/** ردیفِ اصلی + ردیفِ همان شناسه در بخشِ شعبه ⇒ op ِ سرور، یا null (برابر/بی‌کار) */
function opFor(r, have, hlc, derived) {
  const { deepEqual } = require('./sync-v3-merge');
  const base = { op_id: `${OP_PREFIX}${r.updated_seq}`, table: r.table_name, row_id: r.row_id, hlc };
  const alive = !!have && (have.deleted_at === null || have.deleted_at === undefined);
  if (r.deleted_at !== null && r.deleted_at !== undefined) return alive ? { ...base, kind: 'delete' } : null;
  const fields = {};
  for (const [k, v] of Object.entries(decode(r.data))) {
    if (k && k !== 'id' && !k.startsWith('$') && k.length <= 64 && !derived.includes(k)) fields[k] = v;
  }
  if (!Object.keys(fields).length) return null;
  const now0 = have ? decode(have.data) : {};
  if (alive && Object.keys(fields).every((k) => deepEqual(now0[k], fields[k]))) return null;
  return { ...base, kind: have ? 'update' : 'insert', fields };
}

/** یک صفحه زیرِ قفلِ بخشِ شعبه ⇒ { mirrored, skipped, full } */
async function page(sync, main, branch, head) {
  const store = require('./sync-v3-store');
  const schema = require('./sync-v3-schema');
  const { validate } = require('./sync-v3-merge');
  const { SERVER_DEVICE, clock, applyOne } = require('./sync-v3-apply');
  const shopId = branch.shopId;
  return sync.lockedTx(branch, async (c) => {
    const st = (await c.query(
      'SELECT main_seq, main_head FROM shop_branch_mirror WHERE shop_id=$1 AND branch_id=$2', [shopId, branch.branchId]
    )).rows[0];
    const from = st ? Number(st.main_seq) : 0;
    const rows = (await c.query(
      `SELECT table_name, row_id, data, deleted_at, updated_seq FROM sync_v3_rows
        WHERE ${store.ACC} AND ${TABLES_SQL} AND updated_seq > $4 ORDER BY updated_seq LIMIT $5`,
      [...store.acc(main), from, PAGE]
    )).rows;
    const s = store.direct(c);
    const t = now();
    let mirrored = 0;
    let skipped = 0;
    for (const r of rows) {
      const raw = opFor(r, await s.load(branch, r.table_name, r.row_id), clock.tick(), (schema.specOf(r.table_name) || {}).derived || []);
      if (!raw) continue;
      const v = validate(raw, 'server');
      if (!v.ok) { skipped += 1; continue; }   //  شکلِ کهنهٔ ردیفِ اصلی: همان‌جا می‌ماند، شعبه را از کار نمی‌اندازد
      const res = await applyOne(s, branch, v.op, { deviceId: SERVER_DEVICE, origin: 'server', cursor: Infinity, t });
      if (res.status !== 'duplicate') mirrored += 1;
    }
    const full = rows.length < PAGE;
    const last = rows.length ? Number(rows[rows.length - 1].updated_seq) : from;
    await c.query(
      `INSERT INTO shop_branch_mirror (shop_id, branch_id, main_seq, main_head, rows, updated_at) VALUES ($1,$2,$3,$4,$5,$6)
       ON CONFLICT (shop_id, branch_id) DO UPDATE SET main_seq=excluded.main_seq, main_head=excluded.main_head,
         rows=shop_branch_mirror.rows + excluded.rows, updated_at=excluded.updated_at`,
      [shopId, branch.branchId, last, full ? head : (st ? Number(st.main_head) : 0), mirrored, t]
    );
    return { mirrored, skipped, full };
  }, true);
}

/**
 * کاتالوگِ اصلی ⇒ بخشِ این شعبه (پیش از pull/Snapshot ِ دستگاهِ شعبه). بی تغییر در اصلی از آخرین بار ⇒ یک پرسش.
 * @param branch بافتِ بخش (`branches.ctxFor`: `{…sync, shopId, branchId, accountId: '<دکان>#<شعبه>'}`)
 * @param rounds سقفِ صفحه‌ها در این درخواست (هر صفحه [PAGE] ردیف)؛ بیشتر ⇒ `more: true` (درخواستِ بعد ادامه می‌دهد)
 * ⇒ { mirrored, skipped, more }
 */
async function mirror(branch, { rounds = 3 } = {}) {
  if (!branch || !branch.branchId || !branch.shopId) return { mirrored: 0, skipped: 0, more: false };
  const sync = require('./sync-v3');
  const store = require('./sync-v3-store');
  const main = { ...branch, accountId: key.shopOf(branch) };
  delete main.branchId;
  delete main.shopId;
  //  دکانِ v1: opهای تازهٔ v1 ِ اصلی اول به دفترِ v3 ِ اصلی (همان پلِ هر pull ِ اصلی؛ بی تازه ⇒ یک پرسش)
  await sync.catchUp(main, 1);
  const head = await store.headSeq({ query: (s, p) => query(s, p) }, main);
  const st = await one('SELECT main_head FROM shop_branch_mirror WHERE shop_id=$1 AND branch_id=$2', [branch.shopId, branch.branchId]);
  if (st && Number(st.main_head) === head) return { mirrored: 0, skipped: 0, more: false };
  await require('./sync-v3-apply').ready();
  const out = { mirrored: 0, skipped: 0, more: true };
  for (let i = 0; i < rounds && out.more; i++) {
    const p = await page(sync, main, branch, head);
    out.mirrored += p.mirrored;
    out.skipped += p.skipped;
    out.more = !p.full;
  }
  return out;
}

/** بهترین‌کوشش برای درهای همگام‌سازی: خطای آینه pull/Snapshot ِ شعبه را نمی‌اندازد (درخواستِ بعد دوباره) */
async function mirrorSoft(branch, opts) {
  try {
    return await mirror(branch, opts);
  } catch (err) {
    console.error('[branch-catalog] آینهٔ کاتالوگ نشد:', err.message);
    return { mirrored: 0, skipped: 0, more: false, failed: true };
  }
}

module.exports = { TABLES, OP_PREFIX, PAGE, judge, isMirrorOp, opFor, mirror, mirrorSoft };
