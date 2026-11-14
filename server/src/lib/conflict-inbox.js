'use strict';
/**
 * صندوقِ سندهای ردشده برای صاحب و مدیر — `/api/shop/conflict-inbox` (DATA-SAFETY، مهاجرتِ ۳۱۵).
 *
 * سندِ پولی‌ای که سرور نپذیرفت (قرضِ بالای سقف، برگشتِ بیش از فروخته، «نیازمندِ تأیید»، سقفِ قیمت، کاری که نقش
 * اجازه ندارد) از پیش با همهٔ همراه‌هایش در `sync_v3_conflicts.loser_value` می‌نشست (#989، `conflict-payload.js`).
 * این‌جا صاحب/مدیر آن را می‌بیند و یکی از سه کار را می‌کند — هر سه فقط روی سرور تصمیم و هر سه با سابقه:
 *
 *   resubmit  «دوباره ثبت با تأییدِ من»: همان opها با شناسهٔ تازهٔ **بسته به همین ردیف** (`rs_<شناسه>_<i>`) از
 *             همان زنجیرهٔ push ِ دستگاه (v1: همهٔ نگهبان‌های `POST /api/sync/v1/push`، v3: `sync-v3.push`)، به نامِ
 *             **همان دستگاهِ سازنده** ولی با نقشِ **تأییدکننده** — پس سقف و اجازهٔ خودِ او سنجیده می‌شود (مدیرِ با سقفِ
 *             ۱۰٪ تأییدِ ۲۰٪ نمی‌دهد؛ برگشتِ بیش از فروخته برای هیچ‌کس نمی‌نشیند). برگهٔ تأیید یک ردیف در
 *             `shop_approvals` است (`nonce = cf:<شناسه>`، `key_id = 'inbox'`) — همان سابقهٔ RPT-09.
 *   fix       «اصلاح و ثبت»: برنامه فاکتور را در صندوق باز می‌کند؛ پس از ثبتِ فاکتورِ تازه همین ردیف `fixed` با
 *             شناسهٔ همان فاکتور (`detail.fixed_doc`).
 *   dismiss   «کنار بگذار»: با دلیلِ اجباری (`resolution_note`).
 * و دستگاهِ سازنده (هر نقشی) فقط ردیف‌های **خودش** را می‌بیند (`mine`) تا روی همان فاکتور «سرور نپذیرفت: …» یا
 * «صاحب دوباره ثبت کرد» بنویسد.
 *
 * ── یک بار، نه دو بار ─────────────────────────────────────────────────
 *   • شناسهٔ opها از شناسهٔ ردیف ساخته می‌شود ⇒ تلاشِ دوباره (پاسخِ گم‌شده، دو لمس، دو دستگاهِ صاحب) در خودِ
 *     دفتر «تکراری» است (op_id یکتاست)، نه فروشِ دوم.
 *   • پیش از push ردیف «در حالِ ثبت» نشان می‌خورد (`detail.resubmitting`، تا [CLAIM_MS]) ⇒ «کنار بگذار» یا
 *     «دوباره ثبت» ِ هم‌زمانِ دیگر ۴۰۹ `resubmit_in_progress` می‌گیرد؛ پس از شکست نشان برداشته می‌شود.
 *   • دستگاهِ سازنده opهای خودش را pull نمی‌کند (`device_id <> …`) ⇒ آنچه روی دفترش هست دو بار کم/زیاد نمی‌شود؛
 *     دستگاه‌های دیگر همان را می‌گیرند.
 *   • ردِ دوباره (مدیر اجازه ندارد، برگشتِ بیش از فروخته): هیچ چیزی نمی‌نشیند، ردیفِ تازه‌ای در صندوق نمی‌ماند،
 *     بستهٔ همان ردیف همان می‌ماند و دلیل در `detail.resubmit_error`.
 *
 * ⛔ فقط ردیف‌های همین دکان (`account_id` از عضویتِ توکن) و فقط گونه‌های صندوقِ سند ([BOX]).
 * ⛔ `lib/sync-v1.js` ِ مشترک با پمپ دست نمی‌خورد: زنجیرهٔ push از روترِ زندهٔ `routes/sync-v1.js` خوانده می‌شود.
 */
const { EventEmitter } = require('events');
const { one, many, query, tx, now } = require('../db');
const { badRequest, notFound, conflict: conflictError, forbidden } = require('../middleware/errors');
const { can } = require('./permissions');
const audit = require('./audit');
const maskCards = (t) => require('./ops-errors').maskCards(t);   //  S117: شمارهٔ کاملِ کارت ⇒ «[کارت]» پیش از دیتابیس و هشِ تکرار

/** گونه‌های صندوقِ سند (`staff-approval.judge`، `return-line-cap`، `return-sale-cap`) */
const BOX = Object.freeze(['perm_denied', 'approval_required', 'approval_rejected']);
const RESOLVE_PERMISSION = 'data.delete.any';
/**
 * ردی که هیچ تأییدی درستش نمی‌کند (برگشتِ بیش از فروخته‌شدهٔ قلم برای همه، صاحب هم — `return-line-cap`) — فقط «اصلاح» یا
 * «کنار بگذار». بقیه را خودِ زنجیرهٔ push با نقشِ تأییدکننده دوباره می‌سنجد (سقفِ کلِ فاکتور صاحب را نمی‌گیرد، مدیر را می‌گیرد).
 */
const NO_APPROVAL = new Set(['return_exceeds_sold']);
const STATUSES = Object.freeze(['open', 'closed', 'all']);
const CLAIM_MS = 60 * 1000;
const MAX_NOTE = 200;
const LIST_MAX = 200;
const LIST_ITEMS = 5;
const RUN_TIMEOUT_MS = 30 * 1000;
const ACC = `app='shop' AND account_kind='shop' AND account_id=$1`;
//  همان کدهای ردِ `staff-approval.DENY_CODE` (آن پرونده سرِ سقفِ اندازه است؛ این‌جا فقط خوانده می‌شود)
const DENY_CODE = () => require('./staff-approval').DENY_CODE;

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const str = (v) => (typeof v === 'string' ? v : '');
const intOrNull = (v) => (Number.isFinite(Number(v)) && v !== null && v !== '' ? Math.round(Number(v)) : null);

function requireResolver(actor) {
  if (!actor || !can(actor.role, RESOLVE_PERMISSION)) throw forbidden('فقط صاحب دکان یا مدیر این صندوق را می‌بیند و می‌بندد', 'resolve_not_allowed');
}

/** متنِ کوتاهِ یک‌خطی؛ نویسهٔ کنترلی ⇒ ۴۰۰ */
function note(v, { required = false, field = 'دلیل' } = {}) {
  if (v !== undefined && v !== null && typeof v !== 'string') throw badRequest(`${field} باید متن باشد`, 'bad_note');
  const s = str(v).trim();
  if (/[\u0000-\u001f\u007f]/.test(s)) throw badRequest(`${field} یک خط باشد`, 'bad_note');
  if (required && s.length < 2) throw badRequest(`${field} را بنویسید`, 'note_required');
  if (s.length > MAX_NOTE) throw badRequest(`${field} حداکثر ${MAX_NOTE} نویسه`, 'bad_note');
  return maskCards(s);
}

/** «sales/<id>» یا خودِ شناسه ⇒ «sales/<id>» */
function docRef(v) {
  if (v === undefined || v === null || v === '') return '';
  if (typeof v !== 'string') throw badRequest('شناسهٔ فاکتورِ تازه درست نیست', 'bad_fixed_doc');
  const id = v.startsWith('sales/') ? v.slice(6) : v;
  if (!/^[A-Za-z0-9_.-]{1,80}$/.test(id)) throw badRequest('شناسهٔ فاکتورِ تازه درست نیست', 'bad_fixed_doc');
  return `sales/${id}`;
}

/** دلیلِ ردِ همان سند — همان کدی که دستگاه گرفت */
function reasonOf(r) {
  const d = isObj(r.detail) ? r.detail : {};
  if (str(d.reason)) return d.reason;
  if (r.kind === 'approval_required') return 'approval_required';
  if (r.kind === 'approval_rejected') return str(d.slip_error) || 'approval_invalid';
  if (d.reason_core === 'exceeds_sold') return 'return_exceeds_sold';
  if (d.reason_core === 'exceeds_sale') return 'return_exceeds_sale';
  return DENY_CODE()[d.perm] || 'perm_denied';
}

const payloadOf = (r) => (isObj(r.loser_value) && Array.isArray(r.loser_value.ops) ? r.loser_value : { v: 1, path: '', ops: [] });
const claimed = (r, t) => Number(isObj(r.detail) ? r.detail.resubmitting : 0) > t - CLAIM_MS;

// ---------- فاکتور از بستهٔ صندوق ----------

/** مبلغ (یک‌صدمِ افغانی) و مقدار (هزارم): v3 `<نام>Minor`/`<نام>Milli`، v1 همان عددِ دفتر */
function minor(path, f, key) {
  if (path === 'v3') return intOrNull(f[`${key}Minor`]);
  const v = Number(f[key]);
  return Number.isFinite(v) && f[key] !== null && f[key] !== '' ? Math.round(v * 100) : null;
}
function milli(path, f, key) {
  if (path === 'v3') return intOrNull(f[`${key}Milli`]);
  const v = Number(f[key]);
  return Number.isFinite(v) && f[key] !== null && f[key] !== '' ? Math.round(v * 1000) : null;
}

/** بستهٔ صندوق ⇒ فاکتور/برگشت برای نمایش و «اصلاح و ثبت» (بی نام؛ نام‌ها در [names]) */
function invoiceOf(r) {
  const p = payloadOf(r);
  const path = p.path === 'v3' ? 'v3' : 'v1';
  const fields = (o) => (isObj(o.fields) ? o.fields : {});
  const kindOf = (o) => str(o.type) || str(o.kind);
  const head = p.ops.find((o) => o.table === r.table_name && o.row_id === r.row_id) || p.ops[0] || {};
  const h = fields(head);
  const saleId = r.table_name === 'sales' ? r.row_id : str(h.saleId);
  const items = p.ops.filter((o) => o.table === 'saleItems' && kindOf(o) === 'insert' && (!saleId || str(fields(o).saleId) === saleId))
    .map((o) => {
      const f = fields(o);
      return {
        id: o.row_id, productId: str(f.productId), name: str(f.name),
        qtyMilli: milli(path, f, 'quantity'), unitPriceMinor: minor(path, f, 'unitPrice'), totalMinor: minor(path, f, 'totalPrice'),
      };
    });
  const returns = p.ops.filter((o) => o.table === 'returns' && kindOf(o) === 'insert').map((o) => {
    const f = fields(o);
    return { id: o.row_id, saleItemId: str(f.saleItemId), productId: str(f.productId), name: '', qtyMilli: milli(path, f, 'quantity'), amountMinor: minor(path, f, 'amount') };
  });
  const tables = {};
  for (const o of p.ops) tables[o.table] = (tables[o.table] || 0) + 1;
  const isSale = r.table_name === 'sales';
  const sumOf = (list, k) => list.reduce((n, x) => n + (Number.isFinite(x[k]) ? x[k] : 0), 0);
  return {
    path: p.path || '', truncated: Boolean(p.truncated), ops: p.ops.length, tables,
    doc: { table: r.table_name, rowId: r.row_id, saleId: saleId || '' },
    number: intOrNull(h.invoiceNumber), createdAt: intOrNull(h.createdAt) || null, date: str(h.date),
    paymentMethod: isSale ? str(h.paymentMethod) : '', debtorId: str(h.debtorId), seller: str(h.seller), status: str(h.status),
    totalMinor: isSale ? minor(path, h, 'total') : null, discountMinor: isSale ? minor(path, h, 'discount') : null,
    finalMinor: isSale ? minor(path, h, 'finalTotal') : null, paidMinor: isSale ? minor(path, h, 'paidAmount') : null,
    remainingMinor: isSale ? minor(path, h, 'remaining') : null, debtMinor: isSale ? minor(path, h, 'debtGiven') : null,
    amountMinor: isSale ? (minor(path, h, 'finalTotal') ?? sumOf(items, 'totalMinor')) : sumOf(returns, 'amountMinor'),
    items, returns,
  };
}

/** نامِ کاربر، دستگاه، کالا و قرض‌دار برای چند ردیف — هر کدام یک پرس‌وجو */
async function names(shopId, rows, invoices) {
  const users = new Set();
  const devices = new Set();
  const products = new Set();
  const debtors = new Set();
  rows.forEach((r, i) => {
    const d = isObj(r.detail) ? r.detail : {};
    if (str(d.requester)) users.add(d.requester);
    if (r.resolved_by) users.add(r.resolved_by);
    if (r.loser_device) devices.add(r.loser_device);
    for (const it of [...invoices[i].items, ...invoices[i].returns]) if (it.productId && !it.name) products.add(it.productId);
    if (invoices[i].debtorId) debtors.add(invoices[i].debtorId);
  });
  const out = { users: new Map(), devices: new Map(), products: new Map(), debtors: new Map() };
  if (users.size) {
    for (const u of await many('SELECT id, name FROM users WHERE id = ANY($1::text[])', [[...users]])) out.users.set(u.id, u.name || '');
  }
  if (devices.size && users.size) {
    const list = await many('SELECT device_uid, name, platform FROM devices WHERE device_uid = ANY($1::text[]) AND user_id = ANY($2::text[])', [[...devices], [...users]]);
    for (const d of list) out.devices.set(d.device_uid, d.name || d.platform || '');
  }
  async function rowNames(table, ids, into) {
    if (!ids.size) return;
    const list = [...ids];
    for (const t of ['sync_rows', 'sync_v3_rows']) {
      const left = list.filter((id) => !into.has(id));
      if (!left.length) return;
      const got = await many(`SELECT row_id, data->>'name' AS name FROM ${t} WHERE ${ACC} AND table_name=$2 AND row_id = ANY($3::text[])`, [shopId, table, left]);
      for (const g of got) if (g.name) into.set(g.row_id, g.name);
    }
  }
  await rowNames('products', products, out.products);
  await rowNames('debtors', debtors, out.debtors);
  return out;
}

function shapeRow(r, inv, n, { full = false, t = now() } = {}) {
  const d = isObj(r.detail) ? r.detail : {};
  const reason = reasonOf(r);
  const open = r.status === 'open';
  const label = (it) => ({ ...it, name: it.name || n.products.get(it.productId) || '' });
  const items = inv.items.map(label);
  const returns = inv.returns.map(label);
  const resubmittable = open && (inv.path === 'v1' || inv.path === 'v3') && inv.ops > 0 && !inv.truncated && !NO_APPROVAL.has(reason);
  const base = {
    id: r.id, kind: r.kind, reason, perm: str(d.perm), status: r.status, at: Number(r.at),
    doc: inv.doc, number: inv.number, path: inv.path,
    staff: { id: str(d.requester), name: n.users.get(str(d.requester)) || '', role: str(d.role) },
    device: { id: r.loser_device || '', name: n.devices.get(r.loser_device) || '' },
    amountMinor: inv.amountMinor, itemCount: inv.doc.table === 'returns' ? returns.length : (items.length || returns.length),
    items: (items.length ? items : returns).slice(0, full ? 1000 : LIST_ITEMS).map((x) => ({ name: x.name, qtyMilli: x.qtyMilli })),
    resubmittable, fixable: open && inv.doc.table === 'sales' && items.length > 0,
    busy: open && claimed(r, t),
    lastError: open && str(d.resubmit_error) ? { reason: d.resubmit_error, at: Number(d.resubmit_at) || 0 } : null,
    resolvedAt: r.resolved_at === null || r.resolved_at === undefined ? null : Number(r.resolved_at),
    resolvedBy: r.resolved_by ? { id: r.resolved_by, name: n.users.get(r.resolved_by) || '' } : null,
    note: r.resolution_note || '', fixedDoc: str(d.fixed_doc),
  };
  if (!full) return base;
  return {
    ...base,
    invoice: {
      number: inv.number, createdAt: inv.createdAt, date: inv.date, paymentMethod: inv.paymentMethod, status: inv.status,
      debtor: inv.debtorId ? { id: inv.debtorId, name: n.debtors.get(inv.debtorId) || '' } : null, seller: inv.seller,
      totalMinor: inv.totalMinor, discountMinor: inv.discountMinor, finalMinor: inv.finalMinor, paidMinor: inv.paidMinor,
      remainingMinor: inv.remainingMinor, debtMinor: inv.debtMinor, amountMinor: inv.amountMinor,
      items, returns, tables: inv.tables, truncated: inv.truncated,
    },
    //  سقف و مقدارِ همان داوری (برای «چرا»)؛ هیچ چیزِ دیگری از detail بیرون نمی‌رود
    limits: {
      cap: d.cap ?? null, limit: d.limit ?? null, amount: intOrNull(d.amount), pctBp: intOrNull(d.pct_bp),
      cumulative: intOrNull(d.cumulative), lines: Array.isArray(d.lines) ? d.lines.slice(0, 50) : [],
    },
  };
}

async function shapeRows(shopId, rows, opts = {}) {
  const invs = rows.map(invoiceOf);
  const n = await names(shopId, rows, invs);
  return rows.map((r, i) => shapeRow(r, invs[i], n, opts));
}

const SELECT = `SELECT * FROM sync_v3_conflicts WHERE ${ACC} AND kind = ANY($2::text[])`;

/** فهرستِ صاحب/مدیر: باز (پیش‌فرض)، بسته یا همه — تازه‌ترین اول */
async function list(shopId, actor, { status = 'open', limit = 100 } = {}) {
  requireResolver(actor);
  const st = STATUSES.includes(status) ? status : 'open';
  const cap = Math.min(Math.max(1, Number(limit) || 100), LIST_MAX);
  const rows = await many(
    `${SELECT} AND ($3 = 'all' OR ($3 = 'open') = (status = 'open')) ORDER BY at DESC, id DESC LIMIT $4`, [shopId, BOX, st, cap]);
  const open = await one(`SELECT COUNT(*)::int AS n FROM sync_v3_conflicts WHERE ${ACC} AND kind = ANY($2::text[]) AND status='open'`, [shopId, BOX]);
  return { conflicts: await shapeRows(shopId, rows), open: open ? open.n : 0, server_time: now() };
}

/** یک ردیف با فاکتورِ کامل و همهٔ opهای بسته (تماسِ مشتری برای اصلِ بی مجوز پوشیده) */
async function get(shopId, actor, id, { hideContact = false } = {}) {
  requireResolver(actor);
  const r = await one(`${SELECT} AND id=$3`, [shopId, BOX, String(id || '')]);
  if (!r) throw notFound('این مورد پیدا نشد', 'conflict_not_found');
  const [shaped] = await shapeRows(shopId, [r], { full: true });
  const p = payloadOf(r);
  const contact = require('./customer-contact');
  const ops = hideContact ? (p.path === 'v3' ? contact.omitV3Ops(p.ops) : contact.omitV1Ops(p.ops)) : p.ops;
  return { conflict: { ...shaped, ops }, server_time: now() };
}

/** ردیف‌های خودِ درخواست‌کننده (هر نقش) — برای نشانِ «سرور نپذیرفت» روی همان فاکتور */
// ---------- هم‌سو شدنِ دستگاهِ سازنده پس از «کنار بگذار» / «اصلاح» ----------

/** ردیفی که سند را بیرون از دفترِ سرور گذاشت — فقط این‌ها `settle` دارند */
const SETTLED = new Set(['dismissed', 'fixed']);
/** سقفِ ردیف‌های «مقدارِ امروزِ سرور» ِ یک سند (لغو: ۱؛ برگشت: فاکتور و چند قلم) */
const BASE_MAX = 200;

/**
 * `settle` ِ یک ردیفِ بسته برای دستگاهِ سازنده (`core/sync/RejectedSettle.kt`):
 *   ops  — همهٔ opهای بسته (شناسه، جدول، ردیف، گونه)؛ دستگاه فقط آن‌هایی را برمی‌گرداند که خودش دارد؛
 *   base — مقدارِ امروزِ سرور برای هر ردیفی که opِ **غیرِ درجِ** بسته به آن دست زد (null = روی سرور نیست)، و ردیفِ
 *          op ِ درجی که سرور **دارد** (همان شناسه از راهِ دیگر نشست ⇒ دستگاه آن را بیرون نمی‌گذارد).
 * فقط خواندن؛ `lib/sync-v1.js` دست نمی‌خورد (همان جدول‌های `sync_rows`/`sync_v3_rows`، فقط همین دکان).
 */
async function settleOf(shopId, r, { hideContact = false } = {}) {
  const p = payloadOf(r);
  const ops = p.ops
    .filter((o) => isObj(o) && str(o.op_id))
    .map((o) => ({ id: o.op_id, table: str(o.table), rowId: str(o.row_id), type: str(o.type) || str(o.kind) }));
  const want = new Map();
  for (const o of ops) if (o.table && o.rowId && want.size < BASE_MAX) want.set(`${o.table}/${o.rowId}`, o.type !== 'insert');
  const base = {};
  if (want.size) {
    const tables = [...new Set([...want.keys()].map((k) => k.slice(0, k.indexOf('/'))))];
    const ids = [...new Set([...want.keys()].map((k) => k.slice(k.indexOf('/') + 1)))];
    const found = p.path === 'v3'
      ? await many(`SELECT table_name, row_id, data FROM sync_v3_rows WHERE ${ACC} AND table_name = ANY($2::text[]) AND row_id = ANY($3::text[]) AND inserted AND deleted_at IS NULL`, [shopId, tables, ids])
      : await many(`SELECT table_name, row_id, data FROM sync_rows WHERE ${ACC} AND table_name = ANY($2::text[]) AND row_id = ANY($3::text[]) AND deleted_at IS NULL`, [shopId, tables, ids]);
    //  ⛔ CRM-15: بی `customers.contact` همان شکلِ pull ِ همین اصل (شماره و نشانی نه) — opهای بسته را خودِ او ساخته و
    //  هر ردیفی را می‌توانند نام ببرند؛ بی این، «کنار بگذار» شمارهٔ کاملِ قرض‌دار را به انباردار می‌داد
    const contact = hideContact ? require('./customer-contact') : null;
    const view = (x) => {
      const data = isObj(x.data) ? x.data : {};
      return contact ? contact.omit(x.table_name, data) : data;
    };
    const have = new Map(found.map((x) => [`${x.table_name}/${x.row_id}`, view(x)]));
    for (const [k, needed] of want) {
      if (have.has(k)) base[k] = have.get(k);
      else if (needed) base[k] = null;
    }
  }
  return { path: p.path, ops, base, ...(p.truncated ? { truncated: true } : {}) };
}

async function mine(shopId, actor, { limit = 100, hideContact = false } = {}) {
  if (!actor || !actor.userId) throw forbidden();
  const cap = Math.min(Math.max(1, Number(limit) || 100), LIST_MAX);
  const rows = await many(
    `SELECT id, kind, table_name, row_id, loser_op_id, loser_device, status, at, resolved_at, resolution_note, detail,
            CASE WHEN status = ANY($5::text[]) THEN loser_value END AS loser_value
       FROM sync_v3_conflicts WHERE ${ACC} AND kind = ANY($2::text[]) AND detail->>'requester' = $3
      ORDER BY at DESC, id DESC LIMIT $4`,
    [shopId, BOX, actor.userId, cap, [...SETTLED]]);
  const conflicts = [];
  for (const r of rows) {
    conflicts.push({
      id: r.id, table: r.table_name, rowId: r.row_id, opId: r.loser_op_id, device: r.loser_device,
      reason: reasonOf(r), status: r.status, at: Number(r.at),
      resolvedAt: r.resolved_at === null ? null : Number(r.resolved_at), note: r.resolution_note || '',
      fixedDoc: str(isObj(r.detail) ? r.detail.fixed_doc : ''),
      ...(SETTLED.has(r.status) ? { settle: await settleOf(shopId, r, { hideContact }) } : {}),
    });
  }
  return { conflicts, server_time: now() };
}

/** تراکنش + سابقه در همان تراکنش؛ زنجیرهٔ هشِ سابقه (STF-09) پس از commit — همان `approval-requests.inTx` */
async function inTx(fn) {
  const done = [];
  const out = await tx((c) => fn(c, async (entry) => { done.push(await audit.logIn(c, entry)); }));
  for (const d of done) await d.afterCommit();
  return out;
}

async function shapedById(shopId, id) {
  const r = await one(`${SELECT} AND id=$3`, [shopId, BOX, id]);
  return r ? (await shapeRows(shopId, [r]))[0] : null;
}

// ---------- دوباره ثبت ----------

/** opهای تازه: شناسه از شناسهٔ همین ردیف (یک بار، نه دو بار)؛ v3 یک tx ِ تازه ⇒ همه یا هیچ */
function rebuilt(cf, p) {
  const key = String(cf.id).replace(/[^A-Za-z0-9]/g, '').slice(-24);
  return p.ops.map((o, i) => {
    const op = { ...o, op_id: `rs_${key}_${i}` };
    delete op.hash;
    if (p.path === 'v3') op.tx = `rs_${key}`;
    else delete op.tx;
    return op;
  });
}

/** پاسخِ ساختگی برای زنجیرهٔ میان‌افزار (قفلِ گاوصندوق تا «بسته شدن» ِ همین پاسخ می‌ماند) */
function fakeRes() {
  const res = new EventEmitter();
  Object.assign(res, { statusCode: 200, destroyed: false, writableEnded: false, headersSent: false, locals: {} });
  res.set = () => res;
  res.setHeader = () => {};
  res.status = (c) => { res.statusCode = c; return res; };
  res.close = () => {
    if (res.writableEnded) return;
    res.writableEnded = true;
    res.headersSent = true;
    setImmediate(() => { res.emit('finish'); res.emit('close'); });
  };
  return res;
}

/** همان زنجیرهٔ `POST /api/sync/v1/push` (از روترِ زنده، پس هرگز از آن جدا نمی‌افتد) روی یک درخواستِ درونی */
function runChain(chain, req) {
  const res = fakeRes();
  return new Promise((resolve, reject) => {
    let done = false;
    const timer = setTimeout(() => finish(new Error('زنجیرهٔ push بی پاسخ ماند')), RUN_TIMEOUT_MS);
    function finish(err, body) {
      if (done) return;
      done = true;
      clearTimeout(timer);
      res.close();
      if (err) reject(err); else resolve({ status: res.statusCode, body });
    }
    res.json = (body) => { finish(null, body); return res; };
    res.end = () => { finish(null, null); return res; };
    let i = 0;
    const next = (err) => {
      if (done) return;
      if (err) { finish(err); return; }
      const fn = chain[i++];
      if (!fn) { finish(new Error('زنجیرهٔ push بی پاسخ تمام شد')); return; }
      try {
        const p = fn(req, res, next);
        if (p && typeof p.then === 'function') p.then(null, (e) => finish(e));
      } catch (e) { finish(e); }
    };
    next();
  });
}

function v1Chain() {
  const router = require('../routes/sync-v1');
  const layer = router.stack.find((l) => l.route && l.route.path === '/push' && l.route.methods && l.route.methods.post);
  if (!layer) throw new Error('مسیرِ push ِ v1 پیدا نشد');
  //  `safeFlag.within` میان‌افزارِ همهٔ روتر است (پیش از هر مسیر)؛ سقف و احرازِ توکن این‌جا نه — بافت از عضویتِ همین درخواست
  return [require('./safe-flag').within, ...layer.route.stack.map((s) => s.handle)];
}

async function syncCtx(shopId, actor) {
  const shop = await one('SELECT name FROM shops WHERE id=$1', [shopId]);
  return {
    app: 'shop', accountKind: 'shop', accountId: shopId, accountName: shop ? shop.name || '' : '',
    userId: actor.userId, role: actor.role, via: 'user', deviceUid: '', expiresAt: 0,
    safeEntries: await require('./safe-flag').enabledFor(shopId),
  };
}

async function pushV1(ctx, deviceId, ops, ip) {
  const migrations = require('./sync-v1-migrations');
  //  پلهٔ schema ِ همان دستگاه (opهای بسته پیش از ارتقاء نشسته‌اند) — نه پلهٔ امروزِ سرور
  const dev = await one(
    `SELECT schema_version FROM sync_devices WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND device_id=$2`, [ctx.accountId, deviceId]);
  const schema = Number(dev && dev.schema_version) || migrations.currentVersion('shop');
  const req = {
    method: 'POST', path: '/push', url: '/push', originalUrl: '/api/sync/v1/push', query: {}, params: {},
    headers: { 'x-app-version': '', 'x-forwarded-for': '' }, socket: { remoteAddress: ip || 'conflict-inbox' }, ip: ip || '',
    body: { device_id: deviceId, schema_version: schema, ops, caps: [require('./newer-fields').CAP_A00] },
    sync: ctx, appSection: 'shop', shopId: ctx.accountId, role: ctx.role,
    get(name) { return this.headers[String(name).toLowerCase()]; },
  };
  const out = await runChain(v1Chain(), req);
  if (out.status !== 200 || !out.body || !out.body.ok) {
    throw Object.assign(conflictError('سرور این سند را دوباره نپذیرفت', 'resubmit_rejected'), { data: { reason: 'push_failed' } });
  }
  return out.body.results || [];
}

async function pushV3(ctx, deviceId, ops) {
  if (!require('../config').syncV3.enabled) throw conflictError('همگام‌سازیِ v3 روی این سرور خاموش است', 'sync_v3_off');
  const sync = require('./sync-v3');
  const safeLedger = require('./safe-ledger');
  const res = fakeRes();
  try {
    if (require('./safe-flag').on(ctx) && safeLedger.touches(ops) && !(await safeLedger.hold(res, ctx))) {
      throw conflictError('گاوصندوق الان قفل است؛ دوباره بکوشید', 'resubmit_busy');
    }
    const hideContact = await require('./customer-contact').hiddenForReq({ sync: ctx });
    const out = await sync.push(ctx, { deviceId, protocol: sync.PROTOCOL, format: 'v3', ops, appVersion: '', hideContact });
    return out.results || [];
  } finally {
    res.close();
  }
}

const DONE = new Set(['applied', 'duplicate', 'conflict', 'partial']);

async function resubmit(shopId, actor, id, { note: raw, ip = '' } = {}) {
  requireResolver(actor);
  const why = note(raw, { field: 'یادداشت' });
  const t = now();
  const cur = await one(`${SELECT} AND id=$3`, [shopId, BOX, String(id || '')]);
  if (!cur) throw notFound('این مورد پیدا نشد', 'conflict_not_found');
  if (cur.status === 'resubmitted') return { conflict: await shapedById(shopId, cur.id), duplicate: true };
  if (cur.status !== 'open') throw conflictError('این مورد پیش‌تر بسته شده است', 'already_resolved');
  const p = payloadOf(cur);
  const reason = reasonOf(cur);
  if (p.path !== 'v1' && p.path !== 'v3') throw conflictError('این سند از راهِ همگام‌سازی نیامده؛ «اصلاح و ثبت» کنید', 'not_resubmittable');
  if (!p.ops.length || p.truncated) throw conflictError('بستهٔ این سند کامل نیست؛ «اصلاح و ثبت» کنید', 'payload_incomplete');
  if (NO_APPROVAL.has(reason)) throw conflictError('این رد با تأیید درست نمی‌شود؛ «اصلاح و ثبت» یا «کنار بگذار»', 'not_resubmittable');

  //  نشانِ «در حالِ ثبت» — فقط یکی در هر لحظه (دو دستگاهِ صاحب، دو لمس)
  const claim = await one(
    `UPDATE sync_v3_conflicts SET detail = detail || jsonb_build_object('resubmitting', $3::bigint, 'resubmitting_by', $4::text)
      WHERE ${ACC} AND id=$2 AND status='open' AND COALESCE((detail->>'resubmitting')::bigint, 0) <= $5 RETURNING *`,
    [shopId, cur.id, t, actor.userId, t - CLAIM_MS]);
  if (!claim) {
    const now2 = await one(`${SELECT} AND id=$3`, [shopId, BOX, cur.id]);
    if (now2 && now2.status === 'resubmitted') return { conflict: await shapedById(shopId, cur.id), duplicate: true };
    if (now2 && now2.status !== 'open') throw conflictError('این مورد پیش‌تر بسته شده است', 'already_resolved');
    throw conflictError('همین سند همین حالا دارد دوباره ثبت می‌شود', 'resubmit_in_progress');
  }
  const release = () => query(`UPDATE sync_v3_conflicts SET detail = detail - 'resubmitting' - 'resubmitting_by' WHERE ${ACC} AND id=$2`, [shopId, cur.id]);
  //  بستهٔ همین لحظه (همراهی که پس از خواندنِ نخست رسید هم)
  const fresh = payloadOf(claim);
  if (!fresh.ops.length || fresh.truncated) {
    await release();
    throw conflictError('بستهٔ این سند کامل نیست؛ «اصلاح و ثبت» کنید', 'payload_incomplete');
  }

  const ops = rebuilt(claim, fresh);
  const ids = ops.map((o) => o.op_id);
  const deviceId = cur.loser_device || 'server-inbox';
  let results;
  try {
    const ctx = await syncCtx(shopId, actor);
    results = fresh.path === 'v3' ? await pushV3(ctx, deviceId, ops) : await pushV1(ctx, deviceId, ops, ip);
  } catch (err) {
    await release();
    throw err;
  }
  const bad = results.filter((x) => !x || !DONE.has(x.status));
  if (bad.length || results.length < ops.length) {
    const code = (bad.find((x) => x && x.reason && x.reason !== 'tx_rejected') || bad[0] || {}).reason || 'rejected';
    //  ردِ دوباره: ردیفِ تازه‌ای که نگهبان‌ها برای همین opها ساختند نمی‌ماند؛ بستهٔ همین ردیف همان می‌ماند
    await query(`DELETE FROM sync_v3_conflicts WHERE ${ACC} AND loser_op_id = ANY($2::text[]) AND kind = ANY($3::text[]) AND id <> $4`, [shopId, ids, BOX, cur.id]);
    await query(
      `UPDATE sync_v3_conflicts SET loser_value=$3::jsonb,
              detail = (detail - 'resubmitting' - 'resubmitting_by') || jsonb_build_object('resubmit_error', $4::text, 'resubmit_at', $5::bigint, 'resubmit_by', $6::text)
        WHERE ${ACC} AND id=$2`,
      [shopId, cur.id, JSON.stringify(claim.loser_value), String(code).slice(0, 80), t, actor.userId]);
    const partial = results.some((x) => x && (x.status === 'applied' || x.status === 'partial'));
    await audit.log({
      shopId, userId: actor.userId, action: 'conflict_inbox.resubmit_rejected', targetType: 'sync_conflict', targetId: cur.id, ip,
      detail: { reason: code, original: reason, ops: ids.length, partial },
    });
    throw Object.assign(conflictError('سرور این سند را دوباره هم نپذیرفت', 'resubmit_rejected'), { data: { reason: code, partial } });
  }

  const d = isObj(cur.detail) ? cur.detail : {};
  const applied = results.filter((x) => x.status !== 'duplicate').length;
  const done = await inTx(async (c, log) => {
    const upd = (await c.query(
      `UPDATE sync_v3_conflicts SET status='resubmitted', resolved_at=$3, resolved_by=$4, resolution_op_id=$5, resolution_note=$6,
              detail = (detail - 'resubmitting' - 'resubmitting_by' - 'resubmit_error' - 'resubmit_at' - 'resubmit_by')
                       || jsonb_build_object('resubmit_ops', $7::int, 'resubmit_device', $8::text)
        WHERE ${ACC} AND id=$2 AND status='open' RETURNING id`,
      [shopId, cur.id, t, actor.userId, ids[0], why, ids.length, deviceId])).rows[0];
    if (!upd) return false;
    //  برگهٔ تأییدِ همین کار (RPT-09): یک ردیف برای هر ردیفِ صندوق، هرگز دو تا
    await c.query(
      `INSERT INTO shop_approvals (shop_id, nonce, op_id, doc, perm, pct_bp, amount, approver_id, requester_id,
                                   key_id, device_id, path, reason, issued_at, used_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'inbox',$10,$11,$12,$13,$13) ON CONFLICT (shop_id, nonce) DO NOTHING`,
      [shopId, `cf:${cur.id}`.slice(0, 120), ids[0], `${cur.table_name}/${cur.row_id}`, str(d.perm) || cur.kind,
        intOrNull(d.pct_bp), intOrNull(d.amount), actor.userId, str(d.requester), deviceId.slice(0, 64), p.path,
        why || 'دوباره ثبت از صندوقِ سندهای ردشده', t]);
    await log({
      shopId, userId: actor.userId, action: 'conflict_inbox.resubmit', targetType: 'sync_conflict', targetId: cur.id, ip,
      detail: { reason, perm: str(d.perm), doc: `${cur.table_name}/${cur.row_id}`, requester: str(d.requester), ops: ids.length, applied, path: p.path },
    });
    return true;
  });
  return { conflict: await shapedById(shopId, cur.id), duplicate: !done || applied === 0, applied };
}

// ---------- کنار گذاشتن و اصلاح ----------

async function dismiss(shopId, actor, id, { reason, fixedDoc, ip = '' } = {}) {
  requireResolver(actor);
  const why = note(reason, { required: true });
  const doc = docRef(fixedDoc);
  const status = doc ? 'fixed' : 'dismissed';
  const t = now();
  const out = await inTx(async (c, log) => {
    const cur = (await c.query(`${SELECT} AND id=$3 FOR UPDATE`, [shopId, BOX, String(id || '')])).rows[0];
    if (!cur) throw notFound('این مورد پیدا نشد', 'conflict_not_found');
    //  همان کار دوباره (پاسخ گم شد) ⇒ همان پاسخ، بی سابقهٔ دوم
    if (cur.status === status && (cur.resolution_note || '') === why) return { id: cur.id, duplicate: true };
    if (cur.status !== 'open') throw conflictError('این مورد پیش‌تر بسته شده است', 'already_resolved');
    if (claimed(cur, t)) throw conflictError('همین سند همین حالا دارد دوباره ثبت می‌شود', 'resubmit_in_progress');
    await c.query(
      `UPDATE sync_v3_conflicts SET status=$3, resolved_at=$4, resolved_by=$5, resolution_note=$6,
              detail = (detail - 'resubmitting' - 'resubmitting_by') || $7::jsonb
        WHERE ${ACC} AND id=$2`,
      [shopId, cur.id, status, t, actor.userId, why, JSON.stringify(doc ? { fixed_doc: doc.slice(6) } : {})]);
    const d = isObj(cur.detail) ? cur.detail : {};
    await log({
      shopId, userId: actor.userId, action: doc ? 'conflict_inbox.fix' : 'conflict_inbox.dismiss', targetType: 'sync_conflict', targetId: cur.id, ip,
      detail: { reason: reasonOf(cur), note: why, doc: `${cur.table_name}/${cur.row_id}`, requester: str(d.requester), ...(doc ? { fixedDoc: doc } : {}) },
    });
    return { id: cur.id, duplicate: false };
  });
  return { conflict: await shapedById(shopId, out.id), duplicate: out.duplicate };
}

module.exports = {
  BOX, RESOLVE_PERMISSION, NO_APPROVAL, CLAIM_MS, MAX_NOTE,
  reasonOf, invoiceOf, list, get, mine, resubmit, dismiss,
  //  S44 (د۱۸، `lost-device-replay.js`): همان زنجیرهٔ زندهٔ push ِ v1 با نقشِ صاحب و دستگاهِ سازنده
  syncCtx, pushV1,
};
