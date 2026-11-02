'use strict';
/**
 * «حسابِ من» ِ ویترین (CRM-13) — خریدِ آنلاین، خریدِ حضوری و ماندهٔ حسابِ قرض در همین دکان. فقط خواندن.
 *
 * ⛔ پیش‌فرض خاموش: پرچمِ `shop.storefront.account_history` ِ همان دکان (`live_config`). خاموش ⇒ این درها
 *    نیستند (همان پاسخِ مسیرِ ناشناخته) و `features.accountHistory` در `GET /api/store/:slug` نمی‌آید.
 * ⛔ هویت = شمارهٔ تأییدشده (بلیت یا نشستِ حساب؛ همان `sf.verifiedPhone` ِ «سفارش‌های من»)؛ shop_id فقط از slug.
 * ⛔ پیوند با دفترِ دکان فقط با تطبیقِ **دقیقِ** شماره: `validate.phone` + `phoneKey` ِ هر دو سو برابر (نه چند رقمِ
 *    آخر، نه شمارهٔ دوم). پیش‌گزینشِ SQL با ۹ رقمِ آخر فقط ردیف‌ها را کم می‌کند؛ داوری همین‌جا در JS است.
 *    قرض‌دارِ پاک‌شده یا ادغام‌شده (`mergedInto`) نامزد نیست.
 * ⛔ فقط آنچه مالِ خودِ مشتری است: تاریخ، شماره و جمعِ فاکتور، شمارِ قلم، نوعِ پرداخت، پیوندِ فاکتورِ عمومی
 *    (`/r/…`، فقط با QR ِ روشنِ دکان)، مبلغ و ماندهٔ حساب — هرگز نامِ فروشنده یا کارمند، قیمتِ خرید، یادداشت،
 *    نام/شناسهٔ قرض‌دار، شناسهٔ ردیف یا دستگاه.
 * • داده از دفترِ همگام‌سازی (`sync_rows` و، با v3 ِ روشن، `sync_v3_rows`) — همان راهِ `receipt-public`؛
 *   ردیفِ دو دفتر با یک شناسه ⇒ تازه‌ترین `updated_at`.
 * • خریدِ حضوری = فروشِ لغونشدهٔ همان قرض‌دار، بی فروشِ سفارشِ آنلاین (`orderId`، سریِ O/W، `channel: online`)
 *   که در «خریدهای آنلاین» هست.
 * • مانده = Σ «give» − Σ بقیه به واحدِ کوچک (همان `DebtBook.creditCheck`): مثبت = مشتری بدهکار، منفی = طلبکار.
 * • صفحه‌بندی: نشانگر = شمارِ ردیف‌های دیده‌شده (هیچ شناسه‌ای بیرون نمی‌رود). سقفِ هر خواندن MAX_ROWS ردیف
 *   از هر دفتر؛ بیش از آن ⇒ مانده `null`.
 * • امتیازِ وفاداری (PRM-13) هنوز نیست ⇒ هیچ کلیدِ امتیازی در پاسخ نیست.
 */
const config = require('../config');
const { many } = require('../db');
const orders = require('./orders');
const schema = require('./sync-v3-schema');
const { toLegacy } = require('./sync-v3-to-v1');
const { toMinor } = require('./sync-v3-convert');
const validate = require('./validate');
const receipts = require('./receipt-token');
const profiles = require('./receipt-profile');
const { badRequest } = require('../middleware/errors');

const FLAG = 'shop.storefront.account_history';
const PAGE = 10;
const MAX_PAGE = 50;
const MAX_DEBTORS = 10;
const MAX_ROWS = 20000;
const ACC = "app='shop' AND account_kind='shop' AND account_id=$1";
const STORES = Object.freeze([{ fmt: 'v1', table: 'sync_rows' }, { fmt: 'v3', table: 'sync_v3_rows' }]);
const ONLINE_SERIES = new Set(['O', 'W']);
//  «فاکتور #۱۲» یا «فاکتور #B1-12» در شرحِ ردیفِ قرض — همان `DebtBook.invoiceLabelOf`
const INVOICE = /فاکتور\s*#\s*(?:([A-Z][1-9][0-9]{0,3})-)?(\d+)/;
const ISO_DATE = /^\d{4}-\d{2}-\d{2}$/;

const flagOn = (shopId) => orders.flagOf(shopId, FLAG);
const stores = () => STORES.filter((s) => s.fmt === 'v1' || config.syncV3.enabled);
const str = (v) => (typeof v === 'string' ? v.trim() : '');
const num = (v) => (Number.isFinite(Number(v)) ? Number(v) : 0);

function asObject(x) {
  if (typeof x === 'string') { try { x = JSON.parse(x); } catch { return {}; } }
  return x && typeof x === 'object' && !Array.isArray(x) ? x : {};
}

/** کلیدِ دقیقِ شماره، یا '' (نامعتبر) */
function phoneKeyOf(v) {
  try { return validate.phoneKey(validate.phone(v) || ''); } catch { return ''; }
}

/** ۹ رقمِ آخرِ کلید — فقط پیش‌گزینشِ SQL */
const tailOf = (key) => key.replace(/\D/g, '').slice(-9);

/**
 * ردیف‌های زندهٔ یک جدولِ همین دکان از هر دو دفتر؛ یک شناسه در دو دفتر ⇒ تازه‌ترین. هر ردیف `{id, d}` با
 * پول به واحدِ کوچک از `minor(f)` (v1 اعشاری، v3 صحیح).
 */
async function liveRows(shopId, table, where, params) {
  const best = new Map();
  let truncated = false;
  for (const s of stores()) {
    const live = s.fmt === 'v3' ? 'inserted' : 'true';
    const rows = await many(
      `SELECT row_id, data, deleted_at, updated_at, ${live} AS live FROM ${s.table}
        WHERE ${ACC} AND table_name='${table}' AND ${where} LIMIT ${MAX_ROWS + 1}`,
      [shopId, ...params]
    );
    if (rows.length > MAX_ROWS) truncated = true;
    for (const r of rows) {
      const prev = best.get(r.row_id);
      if (!prev || Number(r.updated_at) > prev.at) best.set(r.row_id, { at: Number(r.updated_at), r, fmt: s.fmt });
    }
  }
  const out = [];
  for (const [id, { r, fmt }] of best) {
    if (r.deleted_at !== null && r.deleted_at !== undefined) continue;
    if (r.live === false) continue;
    const raw = asObject(r.data);
    const d = fmt === 'v3' ? toLegacy(schema.specOf(table) || { money: [], qty: [] }, raw) : raw;
    const minor = (f) => {
      const v = fmt === 'v3' ? raw[`${f}Minor`] : toMinor(raw[f]);
      return Number.isSafeInteger(v) ? v : 0;
    };
    out.push({ id, d, minor });
  }
  if (truncated) out.truncated = true;
  return out;
}

/** قرض‌دارهای همین دکان با **همین** شماره (دقیق) — شناسه‌ها، حداکثر MAX_DEBTORS */
async function debtorsOf(shopId, phone) {
  const key = phoneKeyOf(phone);
  if (!key) return [];
  const digits = `regexp_replace(translate(COALESCE(data->>'phone', ''), '۰۱۲۳۴۵۶۷۸۹٠١٢٣٤٥٦٧٨٩', '01234567890123456789'), '[^0-9]', '', 'g')`;
  const rows = await liveRows(shopId, 'debtors', `right(${digits}, 9) = $2`, [tailOf(key)]);
  return rows
    .filter((r) => !str(r.d.mergedInto) && phoneKeyOf(r.d.phone) === key)
    .map((r) => r.id).sort().slice(0, MAX_DEBTORS);
}

function pageOf(list, { cursor, limit } = {}) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  let from = 0;
  if (cursor !== undefined && cursor !== null && cursor !== '') {
    if (!/^\d{1,6}$/.test(String(cursor))) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    from = Number(cursor);
  }
  const items = list.slice(from, from + lim);
  return { items, nextCursor: from + lim < list.length ? String(from + lim) : null };
}

const isoOr = (v) => (ISO_DATE.test(str(v)) ? str(v) : null);
const atOf = (d) => { const n = num(d.createdAt); return n > 0 ? n : null; };

function invoiceNoOfSale(s) {
  if (s.invoiceNumber === null || s.invoiceNumber === undefined || s.invoiceNumber === '') return null;
  const series = str(s.series);
  return series ? `${series}-${s.invoiceNumber}` : String(s.invoiceNumber);
}

function invoiceNoOfNotes(notes) {
  const m = INVOICE.exec(String(notes || ''));
  return m ? (m[1] ? `${m[1]}-${Number(m[2])}` : String(Number(m[2]))) : null;
}

const isOnline = (s) => Boolean(str(s.orderId)) || ONLINE_SERIES.has(str(s.series)) || s.channel === 'online';
const kindOf = (s) => (s.paymentMethod === 'credit' ? 'credit' : (!s.paymentMethod || s.paymentMethod === 'cash') ? 'cash' : 'other');

/** دفترِ قرضِ این قرض‌دارها: مانده و ردیف‌ها، تازه‌ترین اول (ترتیبِ مانده همان `DebtBook`: تاریخ، سپس ساخت) */
async function ledgerOf(shopId, ids) {
  if (!ids.length) return { balanceMinor: null, entries: [] };
  const rows = await liveRows(shopId, 'transactions', `data->>'debtorId' = ANY($2::text[])`, [ids]);
  rows.sort((a, b) => {
    const da = str(a.d.date), db = str(b.d.date);
    if (da !== db) return da < db ? -1 : 1;
    return num(a.d.createdAt) - num(b.d.createdAt) || (a.id < b.id ? -1 : a.id > b.id ? 1 : 0);
  });
  let run = 0;
  const entries = rows.map((r) => {
    const give = r.d.type === 'give';
    const amount = r.minor('amount');
    run += give ? amount : -amount;
    const due = give ? isoOr(r.d.dueDate) : null;
    const no = invoiceNoOfNotes(r.d.notes);
    return {
      kind: give ? 'debt' : 'payment', amountMinor: amount, date: isoOr(r.d.date), at: atOf(r.d),
      ...(due ? { dueDate: due } : {}), ...(no ? { invoiceNo: no } : {}), balanceAfterMinor: run,
    };
  });
  //  بیش از سقف ⇒ مانده گفته نمی‌شود (عددِ ناقص بدتر از نبودن است)
  return { balanceMinor: rows.truncated ? null : run, entries: entries.reverse() };
}

/** خریدهای حضوریِ این قرض‌دارها، تازه‌ترین اول (بی قلم؛ شمارِ قلم فقط برای همان صفحه) */
async function purchasesOf(shopId, ids) {
  if (!ids.length) return [];
  const rows = await liveRows(shopId, 'sales', `data->>'debtorId' = ANY($2::text[])`, [ids]);
  return rows
    .filter((r) => r.d.status !== 'cancelled' && !isOnline(r.d))
    .sort((a, b) => num(b.d.createdAt) - num(a.d.createdAt) || str(b.d.date).localeCompare(str(a.d.date)) || (a.id < b.id ? 1 : -1));
}

async function linesCount(shopId, saleIds) {
  if (!saleIds.length) return new Map();
  const rows = await liveRows(shopId, 'saleItems', `data->>'saleId' = ANY($2::text[])`, [saleIds]);
  const n = new Map();
  for (const r of rows) n.set(r.d.saleId, (n.get(r.d.saleId) || 0) + 1);
  return n;
}

async function purchaseViews(shopId, sales) {
  const [count, { profile }] = await Promise.all([linesCount(shopId, sales.map((s) => s.id)), profiles.read(shopId)]);
  return sales.map((s) => {
    const tok = profile.qr ? receipts.normalizeToken(s.d.receiptToken) : null;
    const no = invoiceNoOfSale(s.d);
    return {
      ...(no ? { invoiceNo: no } : {}), date: isoOr(s.d.date), at: atOf(s.d), totalMinor: s.minor('finalTotal'),
      itemsCount: count.get(s.id) || 0, kind: kindOf(s.d), ...(tok ? { receipt: receipts.receiptPath(shopId, tok) } : {}),
    };
  });
}

async function purchasesPage(shopId, ids, opts) {
  const p = pageOf(await purchasesOf(shopId, ids), opts);
  return { items: await purchaseViews(shopId, p.items), nextCursor: p.nextCursor };
}

function onlinePage(sf, phone, opts) {
  const { cursor, limit } = opts || {};
  return require('./storefront').ordersOfPhone(sf, phone, { cursor, limit: limit || PAGE })
    .then((r) => ({ items: r.orders, nextCursor: r.nextCursor }));
}

/** نخستین صفحهٔ هر سه بخش و مانده */
async function summary(sf, phone) {
  const ids = await debtorsOf(sf.shop_id, phone);
  const [online, purchases, ledger] = await Promise.all([
    onlinePage(sf, phone, {}), purchasesPage(sf.shop_id, ids, {}), ledgerOf(sf.shop_id, ids),
  ]);
  return {
    linked: ids.length > 0,
    balanceMinor: ledger.balanceMinor,
    online, purchases,
    ledger: pageOf(ledger.entries, {}),
  };
}

const PARTS = Object.freeze(['online', 'purchases', 'ledger']);

/** صفحهٔ بعدیِ یک بخش */
async function part(sf, phone, name, opts) {
  if (name === 'online') return onlinePage(sf, phone, opts);
  const ids = await debtorsOf(sf.shop_id, phone);
  if (name === 'purchases') return purchasesPage(sf.shop_id, ids, opts);
  const ledger = await ledgerOf(sf.shop_id, ids);
  return { balanceMinor: ledger.balanceMinor, ...pageOf(ledger.entries, opts) };
}

module.exports = { FLAG, PARTS, flagOn, summary, part, debtorsOf, phoneKeyOf };
