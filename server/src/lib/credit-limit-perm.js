'use strict';
/**
 * ⛔ CRM-03 (§۱۰.۱ «تغییرِ سقفِ اعتبار») — مجوزِ `customers.credit_limit` در سرور (پرچمِ خاموشِ `shop.credit_limit_perm`).
 *
 * تا امروز هر نویسندهٔ دفترِ مشتری سقفِ اعتبار (`Debtor.creditLimit`) را بالا می‌برد، برمی‌داشت یا پایین می‌آورد و سرور
 * فقط ردیف را می‌نشاند. با پرچمِ روشن، **هر راهی** که سقفِ مشتریِ موجود را عوض می‌کند با هستهٔ مشترکِ مجوز
 * (`perm-core` = `PermCore.kt`، بردارِ `perm-v1.json`) و لایه‌های همین دکان سنجیده می‌شود:
 *   v3            op ِ `debtors` (`creditLimitMinor` یا `creditLimit`) در `sync-v3-apply.authorize` ⇒ رد و صندوقِ تعارض
 *   v1            همان op در `v1Guard` (از `staff-role-v1.offRole`؛ ⛔ `lib/sync-v1.js` دست نمی‌خورد) ⇒ رد و صندوقِ تعارض
 *   راهِ قدیم `sync.pushChanges` (`/api/sync/push`) ⇒ ۴۰۳ و کلِ دسته برمی‌گردد
 * صندوقِ تعارض (`perm_denied`) همان راهِ «دوباره ثبت با تأییدِ من» ِ صاحب/مدیر است (`conflict-inbox`): همان op با نقشِ
 * تأییدکننده دوباره سنجیده می‌شود، پس هیچ ویرایشی بی‌صدا گم نمی‌شود.
 *
 * قاعده (خالص، `need`):
 *   • فقط **تغییر**: سقفِ ردیفِ موجودِ سرور (نبودن = بی سقف) ≠ سقفِ تازه. فیلدی که در op نیست (v1/v3) دست نمی‌خورد؛
 *     راهِ قدیم کلِ ردیف را جایگزین می‌کند، پس نبودنِ کلید همان «برداشتنِ سقف» است.
 *   • **مشتریِ تازه** (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳ #۲۵، بستنِ دورزدن): همان سنجش با «پیشین» = سقفِ پیش‌فرضِ دکان
 *     ([DEFAULT_MINOR]؛ دکان هنوز تنظیمی برایش ندارد ⇒ صفر). درجِ تازه بی فیلدِ سقف = بی سقف (بی‌نهایت). پس کارمندِ «ممنوع»
 *     مشتریِ تازه را فقط با سقفِ صفر می‌سازد و سقفِ دیگر ⇒ همان رد و صندوقِ تعارض، که مدیر/صاحب آن‌جا «دوباره ثبت با تأییدِ
 *     من» می‌زند (تأییدِ مدیرِ سنجیده در سرور). درجِ روی ردیفِ موجود ویرایش است؛ `update` ِ بی ردیف سنجیده نمی‌شود.
 *   • مبلغ = سقفِ تازه به Minor؛ برداشتنِ سقف یا عددِ نادرست ⇒ بی‌نهایت (بیش از هر `maxAmount`، بسته‌به‌خطا).
 *   • حالت و سقفِ `maxAmount` از لایه‌ها (نقش، نقشِ سفارشی، کاربر)؛ «نیازمند تأیید» ندارد ⇒ بیش از سقف = ممنوع.
 * صاحبِ دکان هرگز سنجیده نمی‌شود. نقش فقط از عضویتِ توکن (`ctx.role`)، هرگز از بدنه یا فیلدِ op.
 * پرچمِ خاموش (پیش‌فرض) یا opِ بی سقف ⇒ هیچ خواندنی (جز خودِ پرچم برای opِ سقف‌دار) و پاسخ‌ها همان امروز.
 * opِ ثبت‌شده (همان op_id) را خودِ همگام‌سازی «تکراری» جواب می‌دهد — نه داوری، نه صندوق.
 */
const core = require('./perm-core');

const PERM = 'customers.credit_limit';
const FLAG = 'shop.credit_limit_perm';
const REASON = 'credit_limit_not_allowed';
const INF = Number.MAX_SAFE_INTEGER;
/** سقفِ پیش‌فرضِ مشتریِ تازه (Minor) — همان `CreditLimitPerm.NEW_DEFAULT_MINOR` ِ برنامه */
const DEFAULT_MINOR = 0;
const MAX_ROWS = 500;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const has = (o, k) => isObj(o) && Object.prototype.hasOwnProperty.call(o, k);

/** پرچمِ دکانِ همین درخواست — یک بار برای هر دسته */
function on(ctx, run = null) {
  if (!ctx || !ctx.accountId || ctx.role === 'owner') return Promise.resolve(false);
  if (ctx._creditLimitPerm === undefined) ctx._creditLimitPerm = require('./orders').flagOf(ctx.accountId, FLAG, run);
  return ctx._creditLimitPerm;
}

/** عددِ سقف ⇒ Minor: null = بی سقف؛ نادرست ⇒ NaN */
function minorLimit(v, afn) {
  if (v === null || v === undefined || v === '') return null;
  const n = typeof v === 'number' ? v : typeof v === 'string' ? Number(v) : NaN;
  return Number.isFinite(n) ? Math.round(afn ? n * 100 : n) : NaN;
}

/**
 * سقفِ یک ردیف/op ⇒ `{present, limit}` (`limit` Minor یا null). v3 اول `creditLimitMinor`، بعد `creditLimit` ِ افغانی.
 * @param full راهِ قدیم: ردیفِ کامل ⇒ کلیدِ نبوده = بی سقف
 */
function limitOf(fields, { shape = 'v1', full = false } = {}) {
  if (!isObj(fields)) return { present: false, limit: null };
  if (shape === 'v3' && has(fields, 'creditLimitMinor')) return { present: true, limit: minorLimit(fields.creditLimitMinor, false) };
  if (has(fields, 'creditLimit')) return { present: true, limit: minorLimit(fields.creditLimit, true) };
  return { present: full, limit: null };
}

const same = (a, b) => (a === null && b === null) || (Number.isFinite(a) && a === b);

/**
 * نیازِ یک نوشتن: `prev` = دادهٔ ردیفِ موجودِ سرور (یا null = مشتریِ تازه)، `next` = `limitOf` ِ همان op.
 * مشتریِ تازه: «پیشین» = [DEFAULT_MINOR] و فیلدِ نبوده = بی سقف (درجِ تازه کلِ ردیف است).
 * ⇒ null (تغییری در سقف نیست) یا `{perm, req:{amount}, amount, from, to, fresh}`
 */
function need(prev, next, shape = 'v1') {
  if (!isObj(prev)) return needNew(next);
  if (!next.present) return null;
  const from = limitOf(prev, { shape }).limit;
  if (same(from, next.limit)) return null;
  const amount = next.limit === null || !Number.isSafeInteger(next.limit) || next.limit < 0 ? INF : next.limit;
  return { perm: PERM, req: { amount }, amount, from: Number.isFinite(from) ? from : null, to: Number.isFinite(next.limit) ? next.limit : null };
}

/** CRM-03 #۲۵ — مشتریِ تازه با سقفی جز پیش‌فرضِ دکان (بی سقف هم) ⇒ همان نیاز؛ سقفِ پیش‌فرض ⇒ null */
function needNew(next) {
  const to = next.present ? next.limit : null;
  if (same(DEFAULT_MINOR, to)) return null;
  const amount = to === null || !Number.isSafeInteger(to) || to < 0 ? INF : to;
  return { perm: PERM, req: { amount }, amount, from: DEFAULT_MINOR, to: Number.isFinite(to) ? to : null, fresh: true };
}

/** داوری با لایه‌ها ⇒ null یا ردِ `{reason, box, detail}` */
function decide(ctx, layers, n, path) {
  const d = core.evaluate(ctx.role, layers, PERM, n.req);
  if (d.decision === 'allow') return null;
  return {
    reason: REASON, box: 'perm_denied',
    detail: {
      perm: PERM, decision: 'deny', reason_core: d.reason, reason: REASON, requester: ctx.userId || '', role: ctx.role || '',
      amount: n.to, from: n.from, ...(d.cap ? { cap: d.cap, limit: d.limit } : {}), ...(n.fresh ? { fresh: true } : {}), path,
    },
  };
}

// ---------- v3 ----------

/**
 * `sync-v3-apply.authorize`: op ِ `debtors` ِ سقف‌دار روی مشتریِ موجود، یا درجِ مشتریِ تازه (#۲۵) ⇒ null یا رد (با صندوق).
 * ردیفِ حذف‌شده با داده هم «موجود» است (درج روی آن برگرداندن است)؛ ردیفِ بی داده ⇒ درج همان مشتریِ تازه.
 */
async function judgeV3(c, ctx, op, row, { origin = 'v3' } = {}) {
  if (!isObj(op) || op.table !== 'debtors' || (op.kind !== 'insert' && op.kind !== 'update')) return null;
  const next = limitOf(op.fields, { shape: 'v3' });
  const prev = row && isObj(row.data) ? row.data : null;
  const fresh = op.kind === 'insert' && !prev;
  if (!fresh && (!next.present || !prev)) return null;
  if (!(await on(ctx, (s, p) => c.query(s, p)))) return null;
  const n = need(prev, next, 'v3');
  if (!n) return null;
  const layers = await require('./staff-approval').requesterLayers(ctx, c);
  return decide(ctx, layers, n, origin);
}

// ---------- v1 ----------

/**
 * `v1Guard` (از `staff-role-v1.offRole`): op ِ `debtors` ِ سقف‌دار روی ردیفِ موجودِ `sync_rows` ⇒ در `rejected`
 * (`credit_limit_not_allowed`) و یک ردیفِ صندوقِ تعارض با خودِ op. op ِ ثبت‌شده (oplog) ⇒ خودِ v1 «تکراری».
 * ⇒ شمارِ ردشده‌ها
 */
async function v1(ctx, valid, rejected, deviceId = '') {
  if (!ctx || ctx.role === 'owner' || !Array.isArray(valid)) return 0;
  //  #۲۵: درجِ بی فیلدِ سقف هم نامزد است (اگر مشتریِ تازه باشد = بی سقف)
  const cands = valid.filter((x) => !rejected.has(x.i) && isObj(x.op) && x.op.table === 'debtors'
    && (x.op.type === 'insert' || (x.op.type === 'update' && limitOf(x.op.fields).present))).slice(0, MAX_ROWS);
  if (!cands.length || !(await on(ctx))) return 0;
  const { many } = require('../db');
  const seen = await require('./staff-role-v1').seenOps(cands);
  const live = cands.filter((x) => !seen.has(String(x.op.op_id)));
  if (!live.length) return 0;
  //  ردیفِ حذف‌شده هم «موجود» است (درج روی آن برگرداندن است، نه مشتریِ تازه) — همان v3
  const rows = new Map((await many(
    `SELECT row_id, data FROM sync_rows WHERE app=$1 AND account_kind=$1 AND account_id=$2 AND table_name='debtors'
        AND row_id = ANY($3::text[])`,
    ['shop', ctx.accountId, [...new Set(live.map((x) => String(x.op.row_id)))]]
  )).map((r) => [r.row_id, r.data]));
  const layers = await require('./staff-perms').layersFor(ctx.accountId, ctx.role, ctx.userId);
  const sa = require('./staff-approval');
  const { payload } = require('./conflict-payload');
  let n = 0;
  //  همان ردیفِ دسته پشتِ سرِ هم: op ِ بعدی روی نتیجهٔ op ِ پذیرفتهٔ پیشین سنجیده می‌شود
  const after = new Map(rows);
  for (const x of live) {
    const id = String(x.op.row_id);
    const prev = after.get(id);
    //  `update` ِ بی ردیف چیزی برای سنجیدن ندارد (همان امروز)؛ درجِ بی ردیف مشتریِ تازه است
    const need1 = !prev && x.op.type !== 'insert' ? null : need(prev || null, limitOf(x.op.fields));
    const out = need1 ? decide(ctx, layers, need1, 'v1') : null;
    if (!out) {
      after.set(id, { ...(isObj(prev) ? prev : {}), ...x.op.fields });
      continue;
    }
    rejected.set(x.i, { op_id: String(x.op.op_id || ''), status: 'rejected', reason: REASON });
    await sa.boxV1(ctx, x.op, deviceId, { box: out.box, detail: out.detail, payload: payload('v1', [x.op]) });
    n++;
  }
  return n;
}

// ---------- REST / راهِ قدیم ----------

/** پیش از تراکنشِ `sync.pushChanges`: لایه‌ها فقط وقتی ردیفِ مشتری هست، نقش صاحب نیست و پرچم روشن است؛ وگرنه null */
async function legacyLayers({ shopId, role, userId }, changes) {
  if (!shopId || role === 'owner' || !Array.isArray(changes)) return null;
  if (!changes.some((ch) => isObj(ch) && ch.collection === 'debtors' && ch.deleted !== true && isObj(ch.data))) return null;
  if (!(await on({ accountId: shopId, role }))) return null;
  return require('./staff-perms').layersFor(shopId, role, userId);
}

/**
 * درونِ حلقهٔ `sync.pushChanges`: ردیفِ کاملِ مشتری روی ردیفِ موجود که سقف را عوض کند، یا مشتریِ تازه با سقفی جز پیش‌فرض
 * (#۲۵؛ ردیفِ کاملِ بی کلید = بی سقف) ⇒ خطای ۴۰۳ (کلِ دسته برمی‌گردد)، وگرنه null. ردیفِ حذف‌شده هم «موجود» است.
 */
function judgeLegacy(role, rowId, data, existing, layers) {
  if (!layers || !isObj(data)) return null;
  const was = existing ? (typeof existing.data === 'string' ? JSON.parse(existing.data) : existing.data) : null;
  const n = need(isObj(was) ? was : null, limitOf(data, { full: true }));
  if (!n) return null;
  const out = decide({ role }, layers, n, 'legacy');
  if (!out) return null;
  const { forbidden } = require('../middleware/errors');
  return forbidden(`تغییرِ سقفِ اعتبارِ مشتری در حدِ نقشِ شما نیست (debtors/${rowId})`, REASON);
}

module.exports = { PERM, FLAG, REASON, DEFAULT_MINOR, on, limitOf, need, needNew, decide, judgeV3, v1, legacyLayers, judgeLegacy };
