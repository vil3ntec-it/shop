'use strict';
/**
 * پولِ نسیه بی صاحب (صاحب‌کار ۱۴۰۵/۰۸/۱۳: «فروشِ نسیه بی انتخابِ قرض‌دار ثبت می‌شود و بدهی به هیچ‌کس نمی‌رود»).
 *
 * برنامهٔ تازه چنین فاکتوری نمی‌سازد: هر سهمِ «به حساب» مشتریِ موجود و فعال می‌خواهد (`CreditDebtor` ِ برنامه، روی
 * `SalesEngine.record` ِ همهٔ صندوق‌ها). برنامهٔ کهنه، وبِ قدیم یا دستگاهی که هنوز به‌روز نشده شاید بسازد.
 *
 * ⛔ رد نمی‌شود: فروشِ واقعی است (کالا رفته، شاید بخشی نقد گرفته شده) و ردِ op ِ v1 یا تعارضِ راهِ قدیم یعنی گم شدنش
 *    در دستگاهِ کهنه. ⛔ دادهٔ کهنه هم دست نمی‌خورد (هیچ نوشتنی در دفتر). فقط **نشان**:
 *   - سابقهٔ `sale.credit_no_debtor` برای صاحب (یک بار برای هر فاکتور، `audit.log` با `once`) پس از هر push ِ v1
 *     (`routes/sync-v1.js`) و راهِ قدیمِ `/api/sync` (`lib/sync.js`)؛
 *   - بررسیِ فقط‌خواندنیِ `orphan.credit_no_debtor` در `verify-data` برای دفترِ موجود.
 * اصلاح با صاحب است: مشتری را روی همان فاکتور بگذارد (یا با سندِ معکوس).
 */
const { many } = require('../db');
const audit = require('./audit');
const { SHOP } = require('./tenancy');

const ACTION = 'sale.credit_no_debtor';
/** نیم پول: کمتر از آن «مانده» نیست (همان آستانهٔ `verify-data`) */
const EPS = 0.004;
const MAX_ROWS = 200;
const MAX_EVENTS = 20;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const num = (v) => (typeof v === 'number' && Number.isFinite(v) ? v : 0);

/**
 * فاکتوری که سهمِ «به حساب» دارد ولی مشتری ندارد؟ `debtGiven` > ۰، یا «نسیه» با مانده، یا ردیفِ پرداختِ `account`
 * (پرداختِ ترکیبی) — و `debtorId` خالی. لغوشده و حذف‌شده نه. @param {object} s دادهٔ فاکتور (شکلِ دفترِ برنامه)
 */
function orphanCredit(s) {
  if (!isObj(s) || s.status === 'cancelled') return false;
  if (typeof s.debtorId === 'string' && s.debtorId.trim() !== '') return false;
  const account = Array.isArray(s.payments) && s.payments.some((p) => isObj(p) && p.kind === 'account' && num(p.amount) > 0);
  return num(s.debtGiven) > EPS || ((s.paymentMethod === 'credit' || account) && num(s.remaining) > EPS);
}

/** یک سابقه برای هر فاکتورِ بی‌صاحب (`once`)؛ شکستِ سابقه هرگز push را نمی‌خواباند */
async function flag(shopId, rows, { path, deviceId = '', userId = '', ip = '' }) {
  let n = 0;
  for (const { id, data } of rows) {
    if (!orphanCredit(data)) continue;
    if (++n > MAX_EVENTS) break;
    try {
      await audit.log({
        shopId, actorType: 'user', userId, ip, once: true,
        action: ACTION, targetType: 'sale', targetId: String(id).slice(0, 128),
        detail: {
          problem: 'credit_no_debtor', path, deviceId: String(deviceId || '').slice(0, 64),
          invoice: data.invoiceNumber ?? null, debtGiven: num(data.debtGiven), remaining: num(data.remaining),
          paymentMethod: String(data.paymentMethod || '').slice(0, 24),
          text: 'فاکتورِ نسیه بی مشتری: بدهی به حسابِ کسی نرفت — مشتری را روی همان فاکتور بگذارید',
        },
      });
    } catch (err) {
      console.error('[credit-debtor] سابقه ثبت نشد:', err.message);
    }
  }
  return n;
}

/**
 * پس از push ِ v1 ِ دکان: فاکتورهای همین دسته (درج یا ویرایش) را از `sync_rows` ِ نشسته می‌خواند — op ِ ویرایش
 * نیمه است و شکلِ نهایی فقط آن‌جاست. پمپ و دستهٔ بی فاکتور ⇒ هیچ پرسشی.
 */
async function flagV1(ctx, deviceId, ops, { ip = '' } = {}) {
  try {
    if (!ctx || ctx.app !== SHOP.app || ctx.accountKind !== SHOP.app || !ctx.accountId || !Array.isArray(ops)) return 0;
    const { tableOf } = require('./sync-v1');
    const ids = new Set();
    const inserted = new Set();
    for (const r of ops) {
      if (!isObj(r) || tableOf(SHOP.app, r.table ?? r.table_name ?? r.tableName) !== 'sales') continue;
      const id = String(r.row_id ?? r.rowId ?? r.id ?? '');
      if (id && ids.size < MAX_ROWS) ids.add(id);
      if (id && (r.type ?? r.op_type ?? r.opType) === 'insert') inserted.add(id);
    }
    //  STF-03: تخفیفِ خودکار و مصرفِ امتیاز با تعریف‌های دکان ⇒ فقط سابقهٔ بررسی (پرچمِ خاموش؛ ‎lib/promo-loyalty-review.js‎)
    await require('./promo-loyalty-review').v1(ctx, deviceId, ops, { ip });
    //  CRM-03: ردیفِ «give» ِ دیررسیده بی فاکتورِ همین دسته هم بررسی می‌شود (پرچمِ خاموش؛ ‎lib/credit-limit-review.js‎)
    if (!ids.size) {
      await require('./credit-limit-review').v1(ctx, deviceId, [], inserted, { ip, ops });
      return 0;
    }
    const rows = await many(
      `SELECT row_id AS id, data FROM sync_rows
        WHERE app=$1 AND account_kind=$1 AND account_id=$2 AND table_name='sales' AND row_id = ANY($3::text[]) AND deleted_at IS NULL`,
      [SHOP.app, ctx.accountId, [...ids]]
    );
    //  CRM-03: فروشِ نسیهٔ تازه، ویرایشِ بالابرندهٔ قرض یا تراکنشِ دیررسیده بیش از سقفِ اعتبار ⇒ فقط سابقهٔ بررسی
    //  (پرچمِ خاموش؛ ‎lib/credit-limit-review.js‎)
    await require('./credit-limit-review').v1(ctx, deviceId, rows, inserted, { ip, ops });
    return await flag(ctx.accountId, rows, { path: 'v1', deviceId, userId: ctx.userId || '', ip });
  } catch (err) {
    console.error('[credit-debtor] بررسیِ push نشد:', err.message);
    return 0;
  }
}

/** راهِ قدیمِ `/api/sync`: ردیف‌ها کامل‌اند؛ فقط آن‌هایی که نشستند (نه تعارض و نه حذف) */
async function flagLegacy({ shopId, userId = '', deviceId = '', ip = '', path = 'legacy', role = '' }, changes, conflicts = []) {
  try {
    if (!shopId || !Array.isArray(changes)) return 0;
    const refused = new Set((conflicts || []).filter((c) => c && c.collection === 'sales').map((c) => String(c.id)));
    const rows = changes
      .filter((ch) => isObj(ch) && ch.collection === 'sales' && ch.deleted !== true && isObj(ch.data) && !refused.has(String(ch.id)))
      .slice(0, MAX_ROWS)
      .map((ch) => ({ id: String(ch.id), data: ch.data }));
    const txRefused = new Set((conflicts || []).filter((c) => c && c.collection === 'transactions').map((c) => String(c.id)));
    const txs = changes.filter((ch) => isObj(ch) && ch.collection === 'transactions' && ch.deleted !== true && isObj(ch.data)
      && !txRefused.has(String(ch.id))).slice(0, MAX_ROWS).map((ch) => ch.data);
    //  CRM-03: همان بررسیِ سقفِ اعتبار (پرچمِ خاموش؛ ‎lib/credit-limit-review.js‎) — درجِ تازه و تراکنشِ دیررسیده
    if ((rows.length || txs.length) && role) await require('./credit-limit-review').legacy({ shopId, role, userId, deviceId, ip, path }, rows, { txs });
    //  STF-03: تخفیفِ خودکار و مصرفِ امتیاز (پرچمِ خاموش؛ ‎lib/promo-loyalty-review.js‎)
    if (role) {
      await require('./promo-loyalty-review').legacy({ shopId, role, userId, deviceId, ip, path }, changes,
        new Set((conflicts || []).filter(Boolean).map((c) => `${c.collection}/${c.id}`)));
    }
    return rows.length ? await flag(shopId, rows, { path, deviceId, userId, ip }) : 0;
  } catch (err) {
    console.error('[credit-debtor] بررسیِ راهِ قدیم نشد:', err.message);
    return 0;
  }
}

module.exports = { ACTION, orphanCredit, flagV1, flagLegacy };
