'use strict';
/**
 * SEC-DATA-07 (DATA-04) — برگشتِ کارمند بیش از خودِ فاکتور. هم‌نشینِ نگهبانِ `returns.create` در
 * `staff-approval.js` (`judgeGroup` ِ v3 و `v1Guard`)؛ جدا تا آن پرونده بزرگ‌تر نشود.
 *
 * ⛔ پرچمِ `shop.returns.sale_cap` (live_config، پیش‌فرض خاموش): جمعِ برگشت‌های یک فاکتور — ثبت‌شده در هر دو
 * دفتر، همین دسته و همین سند (`before` + `g.amount`، همان «سقفِ تجمعی» ِ نگهبان) — از `finalTotal` ِ خودِ فاکتور
 * به‌اضافهٔ گردِ قلم‌ها (نیم افغانی برای هر قلمِ فاکتور) بیشتر نمی‌شود؛ وگرنه `return_exceeds_sale` با صندوقِ
 * `perm_denied` (همراه‌ها با سازوکارِ خودِ نگهبان `tx_rejected`). سقفِ نقش فقط اختیارِ کاربر است، نه پولِ فاکتور:
 * شاگردِ دارای `returns.create` (یا کلاینتِ دست‌کاری‌شده با حسابِ او) زیرِ سقفش بیش از خودِ فاکتور پس می‌داد.
 * خاموش ماند چون برنامهٔ پیش از #586 هر برگشتِ تکه‌ای را جدا گرد می‌کرد (۲×۲٫۵ ⇒ ۶) و برگشتِ قانونیِ کهنه رد می‌شد.
 * فاکتوری که سرور ندارد (و در همین دستهٔ v1 هم نیست) و صاحبِ دکان (همه‌کاره؛ `v1Guard` او را اصلاً نمی‌سنجد)
 * سنجیده نمی‌شوند. آزمون: `test/return-sale-cap.test.js`.
 */
const { query } = require('../db');

const FLAG = 'shop.returns.sale_cap';
/** گردِ هر قلمِ فاکتور: نیم افغانی، به پول */
const TOL_PER_LINE = 50;

const dbq = { query: (s, p) => query(s, p) };
const isPlainObject = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const int = (v) => { const n = Number(v); return Number.isFinite(n) ? Math.round(n) : 0; };
const str = (v) => (typeof v === 'string' ? v : '');
const has = (v) => v !== null && v !== undefined && v !== '';

/**
 * جمعِ فاکتور (پول) و شمارِ قلم‌هایش — از دفترِ سرور (v3 مقدم، وگرنه v1)، وگرنه از opهای همین دستهٔ v1.
 * @param q      کلاینتِ تراکنشِ push ِ v3 (opهای پیشینِ همین دسته دیده می‌شوند) یا null
 * @param batch  opهای سنجیده‌شدهٔ v1 ِ همین دسته (نام‌های v1، افغانیِ اعشاری) یا null
 * @returns {Promise<null | {totalMinor:number, lines:number}>} null ⇒ فاکتور ناشناخته
 */
async function saleOf(q, ctx, saleId, batch = null) {
  const c = q || dbq;
  const acc = [ctx.accountId, saleId];
  let total = null;
  const v3 = (await c.query(
    `SELECT data->>'finalTotalMinor' AS t FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='sales' AND row_id=$2 AND deleted_at IS NULL`, acc
  )).rows[0];
  if (v3 && has(v3.t)) total = int(v3.t);
  if (total === null) {
    const v1 = (await c.query(
      `SELECT data->>'finalTotal' AS t FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
          AND table_name='sales' AND row_id=$2 AND deleted_at IS NULL`, acc
    )).rows[0];
    if (v1 && has(v1.t)) total = int(Number(v1.t) * 100);
  }
  const lines = new Set((await c.query(
    `SELECT row_id FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='saleItems' AND data->>'saleId' = $2 AND deleted_at IS NULL
     UNION
     SELECT row_id FROM sync_v3_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='saleItems' AND data->>'saleId' = $2 AND deleted_at IS NULL`, acc
  )).rows.map((r) => r.row_id));
  for (const op of batch || []) {
    const f = isPlainObject(op && op.fields) ? op.fields : {};
    if (total === null && op.table === 'sales' && op.row_id === saleId && op.type !== 'delete' && has(f.finalTotal)) {
      total = int(Number(f.finalTotal) * 100);
    }
    if (op.table === 'saleItems' && op.type === 'insert' && str(f.saleId) === saleId) lines.add(op.row_id);
  }
  if (total === null) return null;
  return { totalMinor: total, lines: lines.size };
}

/**
 * سندِ برگشتِ `g` (`groupReturns` ِ نگهبان: `sale`، `amount`) پس از `before` ِ ثبت‌شده/پذیرفتهٔ همان فاکتور، جمعِ
 * فاکتور را می‌گذراند؟ پرچمِ خاموش، صاحب دکان، برگشتِ بی فاکتور یا فاکتورِ ناشناخته ⇒ null (همان امروز).
 * @returns {Promise<null | {reason, box, detail}>} همان شکلِ ردِ `judge`
 */
async function over(q, ctx, g, before, { batch = null, path = 'v3' } = {}) {
  if (!g || !g.sale || ctx.role === 'owner') return null;
  const { flagOf } = require('./orders');
  if (!(await flagOf(ctx.accountId, FLAG, q ? (s, p) => q.query(s, p) : null))) return null;
  const sale = await saleOf(q, ctx, g.sale, batch);
  if (!sale) return null;
  const total = before + g.amount;
  const limit = sale.totalMinor + TOL_PER_LINE * Math.max(1, sale.lines);
  if (total <= limit) return null;
  return {
    reason: 'return_exceeds_sale', box: 'perm_denied',
    detail: {
      perm: 'returns.create', decision: 'deny', reason_core: 'exceeds_sale', requester: ctx.userId || '', role: ctx.role || '',
      amount: g.amount, cumulative: total, sale_total: sale.totalMinor, limit, path,
    },
  };
}

module.exports = { FLAG, TOL_PER_LINE, saleOf, over };
