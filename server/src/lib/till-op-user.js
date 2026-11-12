'use strict';
/**
 * S46 (STF-04) — «کاربرِ سنجیدهٔ صندوق» برای هر op ِ نشستهٔ دکان (مهاجرتِ ۷۹۰، پرچمِ خاموشِ `shop.till_op_user`).
 *
 * ── مسئله ──────────────────────────────────────────────────────────
 * سرور هر op را به نامِ حسابِ دستگاه (توکن) می‌نشاند؛ نوبت‌های سنجیدهٔ صندوق (`lib/till-pin.js`، مهاجرتِ ۶۴۰) می‌گفتند
 * «چه کسی پشتِ کدام دستگاه بود»، ولی سند آن را نداشت. نامی که برنامه روی سند می‌گذارد (`seller`، `till_user`) ادعای
 * بی‌سنجش است و همان می‌ماند.
 *
 * ── قاعده ──────────────────────────────────────────────────────────
 *   • هنگامِ **رسیدنِ** push ([begin]): دستگاه = ردیفِ **توکن** (`tokens.device_id`، نه `device_id` ِ بدنه) و نوبتِ بازِ
 *     همان دستگاه در همان لحظه (`till-pin.sessionAt`). پس از پاسخِ موتور، برای هر op که «نشست» یک ردیف در
 *     `shop_till_op_users` ([record]) — op ِ ردشده و تکراری نه؛ کلیدِ (shop_id, op_id) و `ON CONFLICT DO NOTHING` ⇒ op ِ
 *     دوباره‌فرستاده ردیفِ دوم نمی‌سازد.
 *   • عضو فقط وقتی نوشته می‌شود که سرور خودش دیده باشد ([judge])؛ وگرنه NULL (ناشناخته) با دلیل:
 *       pin_off         `shop.till_pin` خاموش
 *       no_device       توکن به دستگاهی بسته نیست
 *       no_session      نوبتِ بازی روی آن دستگاه نبود (خودِ حسابِ دستگاه، یا صندوقی که بی اینترنت باز شد)
 *       before_session  op به ادعای خودش پیش از آغازِ نوبتِ کنونی ساخته شد (کاربرِ پیشین؟)
 *       late            op بیش از [LATE_MS] (۳۰ دقیقه، همان «دیر ثبت‌شده» ِ تصمیمِ ۲-۲۵) دور از لحظهٔ رسیدن ساخته شد:
 *                       فروشِ بی اینترنت و تعویضی که سرور ندید
 *     زمانِ ادعاییِ op فقط می‌تواند نوشتن را به NULL برگرداند — هرگز عضوِ دیگری جز همان که سرور پشتِ همان دستگاه سنجید.
 *   • هیچ دست زدنی به op (fields، hash)، `oplog`، `sync_rows`، `sync_v3_*` یا موتور: Sync v1 ِ مشترک با پمپ
 *     (`lib/sync-v1.js`، `routes/sync-v1.js`) دست نمی‌خورد — v1 از سرِ `till-guard.v1Guard` در میان‌افزارِ فقط‌دکانِ `staff-approval.v1Guard`
 *     ([v1Hook]: پیچیدنِ `res.json`، همان شیوهٔ `v1-reject`) و v3 از `routes/sync-v3.js` ِ فقط‌دکان ([v3]).
 *   • ⛔ بهترین‌کوشش: خطای این‌جا هرگز push را نمی‌شکند و پاسخ را عوض نمی‌کند — ردیفِ نانوشته یعنی «ناشناخته»، نه حدس.
 *   • خواندن ([list]، [forRow]): صاحب و مدیر همه را؛ بقیه فقط opهایی که خودشان کاربرِ سنجیده‌شان بودند (همان `sessions`).
 *
 * ⛔ پشتِ پرچمِ خاموشِ `shop.till_op_user` (live_config): خاموش ⇒ جز خواندنِ همان پرچم (فقط برای دستهٔ op‌دار) هیچ
 *    خواندن/نوشتنی؛ پمپ و حسابِ غیرِدکان اصلاً نمی‌رسند.
 */
const { many, query, now } = require('../db');
const orders = require('./orders');
const tp = require('./till-pin');
const { badRequest } = require('../middleware/errors');
const { SHOP } = require('./tenancy');

const FLAG = 'shop.till_op_user';
const LATE_MS = 30 * 60 * 1000;
const MAX_RANGE_MS = 62 * 24 * 60 * 60 * 1000;
const MAX_LIST = 1000;
const MAX_ROW = 200;
const MANAGERS = ['owner', 'manager'];
//  v3: «conflict» و «partial» هم نشسته‌اند (در دفترِ op، با فیلدِ بازنده) — مثلِ «applied» ِ v1 با `conflicts`
const ACCEPTED_V3 = new Set(['applied', 'partial', 'conflict']);

const flagOf = (shopId) => orders.flagOf(shopId, FLAG);
const isShop = (ctx) => !!(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);
const text = (v, max) => (typeof v === 'string' && v && v.length <= max ? v : '');

/** دستگاهِ ردیفِ همین توکن (`devices.id`) — همان که `till-pin.verify` نوبت را رویش باز کرد؛ بی ردیف ⇒ '' */
async function tokenDevice(req, ctx) {
  const { tokenOf } = require('./sync-v1-auth');
  const row = await require('./tokens').verify(tokenOf(req), 'access', null, { anyScope: true });
  if (!row || !row.device_id || String(row.subject_id) !== String(ctx.userId || '')) return '';
  return String(row.device_id);
}

/** هنگامِ رسیدنِ push: پرچمِ خاموش ⇒ null. وگرنه همهٔ آنچه سرور خودش می‌داند (هرگز چیزی از بدنه) */
async function begin(req) {
  const ctx = req.sync;
  if (!isShop(ctx) || !(await flagOf(ctx.accountId))) return null;
  const at = now();
  const deviceId = await tokenDevice(req, ctx);
  const pinOn = await tp.flagOf(ctx.accountId);
  const session = pinOn && deviceId ? await tp.sessionAt(ctx.accountId, deviceId, at) : null;
  return { shopId: ctx.accountId, userId: String(ctx.userId || ''), deviceId, pinOn, session, at };
}

/** کاربرِ سنجیده برای یک op (زمانِ ادعاییِ ساختنش [opTs]) یا NULL با دلیل */
function judge(b, opTs) {
  if (!b.pinOn) return { basis: 'pin_off' };
  if (!b.deviceId) return { basis: 'no_device' };
  const s = b.session;
  if (!s) return { basis: 'no_session' };
  const ts = Number(opTs) || 0;
  if (ts < s.started_at) return { basis: 'before_session' };
  if (Math.abs(b.at - ts) > LATE_MS) return { basis: 'late' };
  return { basis: 'session', memberId: s.till_member_id, sessionId: s.id };
}

/** نوشتنِ ردیف‌ها — یک پرسش، تکراری ⇒ هیچ */
async function record(b, path, items) {
  if (!b || !items.length) return 0;
  const cols = { op: [], table: [], row: [], type: [], member: [], session: [], basis: [], ts: [] };
  for (const x of items) {
    const j = judge(b, x.ts);
    cols.op.push(x.opId); cols.table.push(x.table); cols.row.push(x.rowId); cols.type.push(x.type);
    cols.member.push(j.memberId || null); cols.session.push(j.sessionId || null); cols.basis.push(j.basis);
    cols.ts.push(Number.isSafeInteger(Number(x.ts)) && Number(x.ts) > 0 ? Number(x.ts) : null);
  }
  const r = await query(
    `INSERT INTO shop_till_op_users (shop_id, op_id, path, table_name, row_id, op_type, device_id, device_user_id,
                                     till_member_id, till_session_id, basis, op_ts, at)
     SELECT $1, x.op_id, $2, x.table_name, x.row_id, x.op_type, $3, $4, x.member, x.session, x.basis, x.op_ts, $5
       FROM unnest($6::text[], $7::text[], $8::text[], $9::text[], $10::text[], $11::text[], $12::text[], $13::bigint[])
            AS x(op_id, table_name, row_id, op_type, member, session, basis, op_ts)
     ON CONFLICT (shop_id, op_id) DO NOTHING`,
    [b.shopId, path, b.deviceId, b.userId, b.at,
      cols.op, cols.table, cols.row, cols.type, cols.member, cols.session, cols.basis, cols.ts],
  );
  return r.rowCount || 0;
}

const v1Item = (raw) => {
  const v = require('./sync-v1').validateOp(SHOP.app, raw);
  if (!v.ok) return null;
  return { opId: v.op.op_id, table: v.op.table, rowId: v.op.row_id, type: v.op.type, ts: v.op.ts };
};

/**
 * Sync v1 (از سرِ `till-guard.v1Guard` در `staff-approval.v1Guard`، میان‌افزارِ فقط‌دکان پیش از موتورِ مشترک): نوبت را می‌گیرد و
 * `res.json` را می‌پیچد؛ پاسخِ موتور (پس از commit) ⇒ ردیف برای هر «applied»، بعد همان پاسخِ دست‌نخورده.
 * جاگذاری: میان‌افزارهای بعدی (`v1-reject.rejectOps`) پاسخ‌ها را پیش از رسیدن به این‌جا سرِ جای همین فهرست
 * برمی‌گردانند؛ op_id ِ هر جایگاه هم سنجیده می‌شود (ناجور ⇒ هیچ، یعنی ناشناخته). ⛔ پس از `newer-fields`/`contact.v1Guard`:
 * op ای که آن‌ها پیش از موتور برمی‌دارند پاسخِ ساختگی («applied» ِ `contact_ignored`) را بیرونِ همین پیچش می‌گیرد، نه این‌جا.
 */
async function v1Hook(req, res) {
  const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
  if (!ops || !ops.length) return;
  let b;
  try { b = await begin(req); } catch (err) { console.error('[till-op-user] نوبتِ صندوق خوانده نشد:', err.message); return; }
  if (!b) return;
  const seen = ops.map(v1Item);
  const send = res.json.bind(res);
  res.json = (body) => {
    const items = [];
    if (body && body.ok && Array.isArray(body.results)) {
      seen.forEach((x, i) => {
        const r = body.results[i];
        if (x && r && r.status === 'applied' && String(r.op_id) === x.opId) items.push(x);
      });
    }
    if (!items.length) return send(body);
    record(b, 'v1', items)
      .catch((err) => console.error('[till-op-user] کاربرِ صندوق ثبت نشد (v1):', err.message))
      .then(() => send(body))
      .catch((err) => console.error('[till-op-user] پاسخِ push فرستاده نشد:', err.message));
    return res;
  };
}

function v3Item(raw) {
  if (!raw || typeof raw !== 'object') return null;
  const opId = text(raw.op_id, 80);
  const table = text(raw.table, 64);
  const rowId = text(raw.row_id, 80);
  if (!opId || !table || !rowId) return null;
  return { opId, table, rowId, type: text(raw.kind, 16), ts: require('./staff-approval').wallOf(raw.hlc) || 0 };
}

/** Sync v3 (`routes/sync-v3.js` پس از push؛ `ops`/`results` هم‌ترتیب، پیش از `mergeResults`) */
async function v3(b, ops, results, { format = 'v3' } = {}) {
  if (!b || !Array.isArray(ops) || !Array.isArray(results)) return 0;
  const items = [];
  ops.forEach((raw, i) => {
    const r = results[i];
    if (!r || !ACCEPTED_V3.has(r.status)) return;
    //  op ِ v2 ای که فقط مانده یا نشانِ محلی داشت چیزی در دفتر ننشاند
    if (Array.isArray(r.converted) && !r.converted.length) return;
    const x = format === 'v2' ? v1Item(raw) : v3Item(raw);
    if (x && String(r.op_id) === x.opId) items.push(x);
  });
  try { return await record(b, 'v3', items); } catch (err) {
    console.error('[till-op-user] کاربرِ صندوق ثبت نشد (v3):', err.message);
    return 0;
  }
}

// ════════════════════════════════════════════════════════════════════ خواندن

const NAME = (u) => `COALESCE(NULLIF(${u}.name,''), ${u}.phone, '')`;
const SELECT = `SELECT o.*, m.role AS till_role, ${NAME('u')} AS till_name, ${NAME('du')} AS device_user_name, d.name AS device_name
    FROM shop_till_op_users o
    LEFT JOIN shop_members m ON m.id = o.till_member_id AND m.shop_id = o.shop_id
    LEFT JOIN users u ON u.id = m.user_id
    LEFT JOIN users du ON du.id = o.device_user_id
    LEFT JOIN devices d ON d.id = o.device_id AND d.user_id = o.device_user_id`;

function shape(r) {
  return {
    opId: r.op_id, path: r.path, table: r.table_name, rowId: r.row_id, opType: r.op_type,
    at: Number(r.at), opTs: r.op_ts != null ? Number(r.op_ts) : null, basis: r.basis,
    tillUser: r.till_member_id ? { memberId: r.till_member_id, name: r.till_name || '', role: r.till_role || '' } : null,
    device: { id: r.device_id, name: r.device_name || '' },
    deviceUser: { userId: r.device_user_id, name: r.device_user_name || '' },
  };
}

/** صاحب و مدیر: همه (یا یک عضو)؛ بقیه فقط خودشان — همان مرزِ `till-pin.sessions` */
const whoOf = (actor, memberId) => (MANAGERS.includes(actor.role) ? (memberId ? String(memberId).slice(0, 64) : null) : actor.id);

/** opهای یک بازه (حداکثر ۶۲ روز، تازه‌ترین بالا، تا [MAX_LIST]) با جمعِ هر کاربرِ صندوق در همان بازه */
async function list(shopId, actor, { from, to, memberId, table } = {}) {
  const t = now();
  const end = to === undefined || to === '' ? t : Number(to);
  const start = from === undefined || from === '' ? end - 7 * 24 * 60 * 60 * 1000 : Number(from);
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end) throw badRequest('بازه نادرست است', 'range_invalid');
  if (end - start > MAX_RANGE_MS) throw badRequest('بازه بیش از ۶۲ روز است', 'range_invalid');
  if (table !== undefined && table !== '' && !text(table, 64)) throw badRequest('جدول نادرست است', 'table_invalid');
  const who = whoOf(actor, memberId);
  const params = [shopId, start, end, who, table ? String(table) : null];
  const where = `o.shop_id=$1 AND o.at >= $2 AND o.at <= $3 AND ($4::text IS NULL OR o.till_member_id=$4)
     AND ($5::text IS NULL OR o.table_name=$5)`;
  const rows = await many(`${SELECT} WHERE ${where} ORDER BY o.at DESC, o.op_id LIMIT ${MAX_LIST + 1}`, params);
  const sums = await many(
    `SELECT s.till_member_id, s.ops, s.sales, m.role, ${NAME('u')} AS name
       FROM (SELECT o.till_member_id, COUNT(*)::int AS ops, COUNT(*) FILTER (WHERE o.table_name='sales')::int AS sales
               FROM shop_till_op_users o WHERE ${where} GROUP BY o.till_member_id) s
       LEFT JOIN shop_members m ON m.id = s.till_member_id AND m.shop_id = $1
       LEFT JOIN users u ON u.id = m.user_id
      ORDER BY s.ops DESC, s.till_member_id LIMIT 500`,
    params,
  );
  return {
    from: start, to: end, serverTime: t, truncated: rows.length > MAX_LIST,
    ops: rows.slice(0, MAX_LIST).map(shape),
    //  memberId: null ⇒ «ناشناخته» (حسابِ دستگاه یا نوبتِ نادیده) — هرگز به نامِ کسی
    byUser: sums.map((s) => ({
      memberId: s.till_member_id || null, name: s.till_member_id ? s.name || '' : '', role: s.till_member_id ? s.role || '' : '',
      ops: Number(s.ops), sales: Number(s.sales),
    })),
  };
}

/** همهٔ opهای نشستهٔ یک ردیف (مثلاً یک فاکتور) با کاربرِ سنجیده‌شان، کهنه‌ترین بالا */
async function forRow(shopId, actor, table, rowId) {
  const tb = text(table, 64);
  const id = text(rowId, 80);
  if (!tb || !id) throw badRequest('جدول و شناسهٔ ردیف لازم است', 'row_invalid');
  const who = whoOf(actor, null);
  const rows = await many(
    `${SELECT} WHERE o.shop_id=$1 AND o.table_name=$2 AND o.row_id=$3 AND ($4::text IS NULL OR o.till_member_id=$4)
      ORDER BY o.at, o.op_id LIMIT ${MAX_ROW}`,
    [shopId, tb, id, who],
  );
  return { table: tb, rowId: id, ops: rows.map(shape) };
}

module.exports = { FLAG, LATE_MS, flagOf, begin, judge, record, v1Hook, v3, list, forRow };
