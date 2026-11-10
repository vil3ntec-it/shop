'use strict';
/**
 * CRM-15 — اجرای سرورِ `customers.contact` (شماره و نشانیِ مشتری): کمترین دسترسی، از خودِ سرور.
 *
 * مجوز در هستهٔ مشترک است (`perm-core.js`: سه نقشِ قدیم، صندوق‌دار و حسابدار allow؛ انباردار و جمع‌آور deny؛ `full`،
 * صاحب برای هر نقش یا کاربر می‌بندد یا باز می‌کند). تا #904 فقط صفحهٔ برنامه شماره را می‌پوشاند؛ از این‌جا سرور هم
 * به اصلِ بی مجوز شماره و نشانی نمی‌دهد:
 *
 *   • درهای REST (`/api/debtors…`، صندوقِ تعارض) ⇐ شماره پوشیده با همان قالبِ برنامه (`core/text/ContactMask`:
 *     هر رقم جز دو رقمِ آخر «•») و نشانی نه.
 *   • pull، Snapshot و حذف‌شده‌های همگام‌سازی (راهِ قدیم، v1، v3) ⇐ این فیلدها **نیستند** (نه پوشیده): دستگاه مقدارِ
 *     پوشیده را هرگز در دفترش نمی‌نشاند تا با آن جست‌وجو یا «تکراری» بسنجد.
 *   • ⛔ نوشتن (push ِ v1/v3، راهِ قدیم و PUT ِ REST) از همان اصل: فیلدِ تماسِ ردیفی که سرور **دارد** هرگز عوض یا
 *     پاک نمی‌شود — مقدارِ پوشیده، خالی یا نبودن همه «بی‌تغییر»اند (`ignored` در پاسخ). ردیفِ تازه شمارهٔ نوشتهٔ خودِ
 *     کارمند را می‌گیرد، مقدارِ پوشیده را نه. پس پوشاندن در خواندن راهی برای برگرداندنِ «••56» روی شمارهٔ واقعی نمی‌گذارد.
 *
 * پمپ (`accountKind: 'station'`) و هر حسابِ بیرونِ دکان هرگز پالایش نمی‌شود و هیچ پرسشی نمی‌خورد.
 * صاحبِ دکان همیشه می‌بیند (بی پرسش). شمارهٔ سفارش و گفت‌وگو: `ordersContact` (همان `orders.contact` ِ نقش و این مجوز).
 */
const core = require('./perm-core');
const { can } = require('./permissions');
const { SHOP } = require('./tenancy');

/** بخشِ دکان با حسابِ دکان (حسابِ پمپ `station` است؛ `accountKind` ِ دکان همان نامِ بخش است) */
const isShopSync = (ctx) => Boolean(ctx && ctx.app === SHOP.app && ctx.accountKind === SHOP.app && ctx.accountId);

const KEY = 'customers.contact';
const DOT = '•';
/** فیلدهای تماسِ هر جدولِ دفتر (`Debtor`: شماره، شمارهٔ دوم CRM-02، نشانیِ تحویل و پینِ آن CRM-09) */
const FIELDS = Object.freeze({ debtors: Object.freeze(['phone', 'phone2', 'deliveryAddress', 'deliveryPin']) });
const PHONES = new Set(['phone', 'phone2']);
const ND = /^\p{Nd}$/u;

const hasOwn = (o, k) => o !== null && typeof o === 'object' && Object.prototype.hasOwnProperty.call(o, k);
const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** همان `ContactMask.phone` ِ برنامه: هر رقم (یونیکد، واحدِ UTF-16 مثلِ `Char.isDigit`) جز [keep] رقمِ آخر ⇐ «•» */
function maskPhone(text, keep = 2) {
  const s = String(text ?? '');
  let digits = 0;
  for (let i = 0; i < s.length; i++) if (ND.test(s[i])) digits++;
  let seen = 0;
  let out = '';
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (ND.test(ch)) { seen++; out += seen > digits - keep ? ch : DOT; } else out += ch;
  }
  return out;
}

/** مقدارِ پوشیده (خروجیِ همین پوشاندن یا صفحهٔ برنامه) — هرگز به‌جای شمارهٔ واقعی نمی‌نشیند */
function isMasked(v) {
  if (typeof v === 'string') return v.includes(DOT);
  if (isObj(v)) return Object.values(v).some(isMasked);
  return false;
}

/** فیلدهای تماسِ این جدول یا null */
function fieldsOf(table) {
  return typeof table === 'string' && hasOwn(FIELDS, table) ? FIELDS[table] : null;
}

/* ----------------------------------------------------------------- مجوز */

/**
 * آیا این اصل شماره و نشانیِ مشتری را می‌بیند؟ صاحب همیشه (بی پرسش)؛ نقشِ ناشناخته نه؛ بقیه با دو لایه.
 * `run` = اتصالِ تراکنشِ صدازننده (اختیاری).
 */
async function allowed({ shopId, role, userId }, run = null) {
  if (role === 'owner') return true;
  if (!shopId || typeof role !== 'string' || core.defaultMode(role, KEY) === null) return false;
  const layers = await require('./staff-perms').layersFor(shopId, role, userId, run);
  return core.allows(role, layers, KEY);
}

/** بافتِ همگام‌سازی (`req.sync`) ⇐ پنهان؟ فقط حسابِ دکان؛ پمپ همیشه false و بی پرسش */
async function hiddenForSync(ctx) {
  if (!isShopSync(ctx)) return false;
  return !(await allowed({ shopId: ctx.accountId, role: ctx.role, userId: ctx.userId }));
}

/** درخواستِ REST یا همگام‌سازی ⇐ پنهان؟ یک بار برای هر درخواست (لایه‌های `requireShop` هم از همان حافظه) */
async function hiddenForReq(req) {
  if (typeof req._contactHidden === 'boolean') return req._contactHidden;
  let hidden;
  if (req.sync) hidden = await hiddenForSync(req.sync);
  else if (req.member) {
    const role = req.member.role;
    if (role === 'owner') hidden = false;
    else if (core.defaultMode(role, KEY) === null) hidden = true;
    else hidden = !core.allows(role, await require('./staff-perms').layersOfReq(req), KEY);
  } else hidden = true;
  req._contactHidden = hidden;
  return hidden;
}

/**
 * شمارهٔ کاملِ سفارش/گفت‌وگو: `orders.contact` ِ نقش (سقفِ امروز: صاحب و مدیر) **و** `customers.contact`. نقشی که
 * `orders.contact` ندارد بی هیچ پرسشی نه؛ پس پیش‌فرضِ همه همان امروز است و فقط بستنِ صاحب (لایهٔ نقش یا کاربر) اثر دارد.
 * @param who {shopId, role, userId}
 */
async function ordersContact(who, run = null) {
  if (!who || !can(who.role, 'orders.contact')) return false;
  return allowed(who, run);
}

/** میان‌افزار: دری که شمارهٔ مشتری را می‌خواند یا با آن جست‌وجو می‌کند — پس از `requirePermission('orders.contact')` */
function requireOrdersContact(req, res, next) {
  hiddenForReq(req).then((hidden) => {
    if (!hidden) return next();
    const { forbidden } = require('../middleware/errors');
    return next(forbidden('صاحب دکان دیدنِ شمارهٔ مشتریان را برای شما بسته است', 'permission_denied'));
  }).catch(next);
}

/* ------------------------------------------------------------- خواندن */

/** ردیف بی فیلدهای تماس (همگام‌سازی). بی فیلدِ تماس ⇐ همان شیء */
function omit(table, data) {
  const f = fieldsOf(table);
  if (!f || !isObj(data) || !f.some((k) => hasOwn(data, k))) return data;
  const out = { ...data };
  for (const k of f) delete out[k];
  return out;
}

/** ردیف برای REST: شماره پوشیده، نشانی نه */
function mask(table, data) {
  const f = fieldsOf(table);
  if (!f || !isObj(data) || !f.some((k) => hasOwn(data, k))) return data;
  const out = { ...data };
  for (const k of f) {
    if (!hasOwn(out, k)) continue;
    if (PHONES.has(k) && typeof out[k] === 'string') out[k] = maskPhone(out[k]);
    else delete out[k];
  }
  return out;
}

/** opهای pull (v1: `fields` و `row` ِ «زنده‌کردنِ کامل»؛ v3: `effect.set`) — آیا یکی جدولِ تماس است؟ */
function touches(list, key = 'table') {
  return Array.isArray(list) && list.some((x) => x && fieldsOf(x[key]));
}

/** opهای pull ِ v1 بی فیلدهای تماس */
function omitV1Ops(ops) {
  if (!touches(ops)) return ops;
  return ops.map((o) => {
    if (!o || !fieldsOf(o.table)) return o;
    const out = { ...o };
    if (isObj(o.fields)) out.fields = omit(o.table, o.fields);
    if (isObj(o.row)) out.row = omit(o.table, o.row);
    return out;
  });
}

/** اثرِ opِ pull ِ v3 (`{set}`، `{set, revived}`؛ `inc` و `deleted` بی داده) بی فیلدهای تماس */
function omitV3Effect(table, effect) {
  if (!fieldsOf(table) || !isObj(effect) || !isObj(effect.set)) return effect;
  const set = omit(table, effect.set);
  return set === effect.set ? effect : { ...effect, set };
}

/** opهای pull ِ v3 (`{table, effect}`) بی فیلدهای تماس */
function omitV3Ops(ops) {
  if (!touches(ops)) return ops;
  return ops.map((o) => {
    const effect = o ? omitV3Effect(o.table, o.effect) : null;
    return o && effect !== o.effect ? { ...o, effect } : o;
  });
}

/** Snapshot: `{جدول: [{id, data}]}` */
function omitTables(tables) {
  if (!isObj(tables)) return tables;
  let out = tables;
  for (const t of Object.keys(FIELDS)) {
    if (!Array.isArray(tables[t])) continue;
    if (out === tables) out = { ...tables };
    out[t] = tables[t].map((r) => (r && isObj(r.data) ? { ...r, data: omit(t, r.data) } : r));
  }
  return out;
}

/** فهرستِ حذف‌شده‌های v1: `[{table, row_id, data}]` */
function omitRows(rows) {
  if (!touches(rows)) return rows;
  return rows.map((r) => (r && isObj(r.data) && fieldsOf(r.table) ? { ...r, data: omit(r.table, r.data) } : r));
}

/** یک مقدارِ صندوقِ تعارض (`field` یک فیلد، یا «*» با کلِ ردیف) */
function maskValue(table, field, value) {
  const f = fieldsOf(table);
  if (!f) return value;
  if (f.includes(field)) return PHONES.has(field) && typeof value === 'string' ? maskPhone(value) : null;
  return isObj(value) ? mask(table, value) : value;
}

/** ردیفِ صندوقِ تعارضِ v1/v3 (`table`، `field`، `loser_value`، `winner_value`، `detail`) */
function maskConflict(cf) {
  if (!cf || !fieldsOf(cf.table)) return cf;
  const out = { ...cf };
  if (hasOwn(out, 'loser_value')) out.loser_value = maskValue(cf.table, cf.field, cf.loser_value);
  if (hasOwn(out, 'winner_value')) out.winner_value = maskValue(cf.table, cf.field, cf.winner_value);
  if (isObj(out.detail) && typeof out.detail.phone === 'string') out.detail = { ...out.detail, phone: maskPhone(out.detail.phone) };
  return out;
}

/* -------------------------------------------------------------- نوشتن */

/*
 *  ⛔ نوشتن از اصلِ بی مجوز **هیچ شیءِ تازه‌ای از کلیدهای کاربر نمی‌سازد** (نه `{...fields}`، نه `obj[k] = …` با کلیدِ
 *  درخواست): فقط کلیدهای ثابتِ `FIELDS` از همان op/ردیف پاک یا نوشته می‌شوند، و opی که نامِ فیلدِ ناامن دارد
 *  (`isSafeFieldName`: `__proto__`، `constructor`، `prototype` یا بیرون از `[A-Za-z_][A-Za-z0-9_]{0,63}`) کلاً رد
 *  می‌شود (`bad_field_name`، همان نامِ ردِ کلیدِ نادرست در ‎lib/sync-v1.js‎ و ‎lib/sync-v3.js‎) — CodeQL
 *  js/remote-property-injection (#914).
 */
const SAFE_FIELD = /^[A-Za-z_][A-Za-z0-9_]{0,63}$/;
const RESERVED = new Set(['__proto__', 'constructor', 'prototype']);
function isSafeFieldName(k) {
  return typeof k === 'string' && SAFE_FIELD.test(k) && !RESERVED.has(k);
}
/** همهٔ نام‌های فیلدِ این شیء امن‌اند؟ */
function safeFields(fields) {
  return isObj(fields) && Object.keys(fields).every(isSafeFieldName);
}

/**
 * کدام فیلدهای تماس از اصلِ بی مجوز کنار می‌روند — کلیدها فقط از فهرستِ ثابتِ `FIELDS`.
 * ردیفِ موجود (`exists`) ⇐ هر فیلدِ تماس؛ ردیفِ تازه ⇐ فقط مقدارِ پوشیده.
 */
function ignoredOf(table, fields, exists) {
  const f = fieldsOf(table);
  const out = [];
  if (!f || !isObj(fields)) return out;
  for (const k of f) if (hasOwn(fields, k) && (exists || isMasked(fields[k]))) out.push(k);
  return out;
}

/** همان op، بی فیلدهای `ignored` (در جا؛ کلیدهای ثابت) ⇐ آیا چیزی ماند؟ */
function dropIn(fields, ignored) {
  for (const k of ignored) delete fields[k];
  return Object.keys(fields).length > 0;
}

/**
 * v3 (`applyOne` ِ ‎lib/sync-v3.js‎، زیرِ قفلِ حساب، پس «موجود» همان است که merge می‌بیند): فقط opِ خودِ دستگاه
 * (`v3`/`v2`) — نه پلِ v1 و نه opِ سرور — و نه حذف/لغو. فیلدها در جا کم می‌شوند (همان شیءِ op).
 * @returns {null | {op, ignored, noop?}} — null ⇐ همان op؛ `noop` ⇐ پاسخِ نهایی بی ثبت («applied» ِ تهی، یا ردِ نامِ ناامن)
 */
function guardV3(op, row, origin) {
  if ((origin !== 'v3' && origin !== 'v2') || op.kind === 'delete' || op.kind === 'cancel') return null;
  if (!fieldsOf(op.table) || !isObj(op.fields)) return null;
  if (!safeFields(op.fields)) {
    return { op, ignored: [], noop: { op_id: op.op_id, status: 'rejected', reason: 'bad_field_name' } };
  }
  const ignored = ignoredOf(op.table, op.fields, !!row);
  if (!ignored.length) return null;
  if (!dropIn(op.fields, ignored)) return { op, ignored, noop: { op_id: op.op_id, status: 'applied', server_seq: null, ignored } };
  return { op, ignored };
}

/**
 * راهِ قدیم و REST (ردیفِ کامل جایگزین می‌شود)، در جا: فیلدهای تماسِ ردیفِ موجود دقیقاً همانِ سرور می‌مانند —
 * هرچه دستگاه فرستاده (پوشیده، خالی یا هیچ) کنار؛ ردیفِ تازه فقط مقدارِ پوشیده را از دست می‌دهد.
 * @returns {object|null} همان `data`؛ null ⇐ نامِ فیلدِ ناامن (صدازننده ردیف را کنار می‌گذارد)
 */
function keepServer(table, data, serverData) {
  const f = fieldsOf(table);
  if (!f || !isObj(data)) return data;
  if (!safeFields(data)) return null;
  for (const k of f) {
    if (serverData && hasOwn(serverData, k)) data[k] = serverData[k];
    else if (hasOwn(data, k) && (serverData || isMasked(data[k]))) delete data[k];
  }
  return data;
}

/** پاسخِ دستهٔ پالایش‌شده ⇒ به ترتیبِ دستهٔ ورودی؛ opِ کوتاه‌شده `ignored` می‌گیرد (همان شکلِ `newer-fields`) */
function mergeResults(fl, results) {
  if (!Array.isArray(results) || (!fl.dropped.size && !fl.trimmed.size)) return results;
  const rest = results.map((r) => (r && fl.trimmed.has(r.op_id)
    ? { ...r, ignored: [...(Array.isArray(r.ignored) ? r.ignored : []), ...fl.trimmed.get(r.op_id)] } : r));
  const merged = [];
  for (let i = 0; i < fl.length; i++) merged.push(fl.dropped.has(i) ? fl.dropped.get(i) : rest.shift());
  return merged.filter(Boolean).concat(rest);
}

/**
 * میان‌افزارِ `POST /sync/v1/push` — فقط حسابِ دکان؛ پس از `v1NewerFields` و پیش از نگهبان‌های دیگر (همه همان
 * دسته‌ای را می‌بینند که به ‎lib/sync-v1.js‎ ِ مشترک با پمپ می‌رسد؛ آن پرونده دست نمی‌خورد).
 * اصلِ بی `customers.contact`، هر opِ درج/ویرایشِ جدولِ تماس: نامِ فیلدِ ناامن ⇐ رد (`bad_field_name`)؛ فیلدِ تماسِ
 * ردیفی که در `sync_rows` هست کنار (op ِ تهی‌شده نمی‌رود و «applied» با `ignored` برمی‌گردد)؛ ردیفِ تازه فقط
 * مقدارِ پوشیده را از دست می‌دهد. ⛔ همه در جا روی همان آرایه و همان opها (`splice`، `delete` با کلیدِ ثابت) —
 * `req.body.ops` دوباره نوشته نمی‌شود و هیچ opِ تازه‌ای از دادهٔ درخواست ساخته نمی‌شود؛ اثرِ انگشت از نو.
 */
function v1Guard(req, res, next) {
  const ctx = req.sync;
  if (!isShopSync(ctx)) return next();
  (async () => {
    const ops = req.body?.ops;
    if (!Array.isArray(ops) || !ops.length) return next();
    const { tableOf, hashOf } = require('./sync-v1');
    const hits = [];
    ops.forEach((raw, i) => {
      if (!isObj(raw) || !isObj(raw.fields)) return;
      const rawTable = raw.table ?? raw.table_name ?? raw.tableName;
      const table = tableOf(SHOP.app, rawTable);
      const kind = raw.type ?? raw.op_type ?? raw.opType;
      if (!fieldsOf(table) || (kind !== 'insert' && kind !== 'update')) return;
      hits.push({ i, raw, rawTable, table, kind, rowId: String(raw.row_id ?? raw.rowId ?? raw.id ?? '') });
    });
    if (!hits.length || !(await hiddenForReq(req))) return next();
    const { many } = require('../db');
    const found = await many(
      `SELECT table_name, row_id FROM sync_rows WHERE app=$1 AND account_kind=$2 AND account_id=$3
          AND table_name = ANY($4::text[]) AND row_id = ANY($5::text[])`,
      [ctx.app, ctx.accountKind, ctx.accountId, [...new Set(hits.map((x) => x.table))], [...new Set(hits.map((x) => x.rowId))]]
    );
    const exists = new Set(found.map((r) => `${r.table_name}\u0000${r.row_id}`));
    const fl = { length: ops.length, dropped: new Map(), trimmed: new Map() };
    //  از آخر به اول: `splice` شمارهٔ opهای پیشین را جابه‌جا نمی‌کند
    for (let j = hits.length - 1; j >= 0; j--) {
      const x = hits[j];
      const opId = String(x.raw.op_id ?? x.raw.opId ?? '');
      if (!safeFields(x.raw.fields)) {
        fl.dropped.set(x.i, { op_id: opId, status: 'rejected', reason: 'bad_field_name' });
        ops.splice(x.i, 1);
        continue;
      }
      const ignored = ignoredOf(x.table, x.raw.fields, exists.has(`${x.table}\u0000${x.rowId}`));
      if (!ignored.length) continue;
      if (!dropIn(x.raw.fields, ignored)) {
        fl.dropped.set(x.i, { op_id: opId, status: 'applied', server_seq: null, ignored, reason: 'contact_ignored' });
        ops.splice(x.i, 1);
        continue;
      }
      fl.trimmed.set(opId, ignored);
      if (x.raw.hash) x.raw.hash = hashOf({ table: x.rawTable, row_id: x.rowId, type: x.kind, fields: x.raw.fields });
    }
    if (!fl.dropped.size && !fl.trimmed.size) return next();
    const send = res.json.bind(res);
    res.json = (body) => {
      if (body && body.ok && Array.isArray(body.results)) body.results = mergeResults(fl, body.results);
      return send(body);
    };
    return next();
  })().catch(next);
}

module.exports = {
  KEY, DOT, FIELDS, maskPhone, isMasked, fieldsOf,
  allowed, hiddenForSync, hiddenForReq, ordersContact, requireOrdersContact,
  omit, mask, maskValue, maskConflict, isSafeFieldName, safeFields, ignoredOf, keepServer,
  touches, omitV1Ops, omitV3Effect, omitV3Ops, omitTables, omitRows, mergeResults, v1Guard, guardV3,
};
