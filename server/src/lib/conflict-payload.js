'use strict';
/**
 * DATA-SAFETY — سندِ پولیِ ردشده **کامل** در صندوقِ تعارض (`sync_v3_conflicts.loser_value`).
 *
 * ── اشکال ──────────────────────────────────────────────────────────────
 * فاکتوری که سرور رد می‌کرد (قرضِ بالای سقف، تخفیف یا برگشتی که نقش نمی‌گذارد، …) با همهٔ قلم‌هایش فقط روی
 * همان دستگاه می‌ماند (دفتر و `oplog-…-dropped.json`)؛ صندوقِ تعارض فقط خلاصه داشت (گونه، مبلغ، سقف، قرض‌دار،
 * شناسه‌ها) — نه قلم‌ها. آن دستگاه که پیش از بستنِ تعارض پاک یا از نو ساخته می‌شد، قلم‌ها برای همیشه می‌رفتند.
 *
 * ── حالا ────────────────────────────────────────────────────────────────
 * همان ردیفِ صندوق `loser_value = { v: 1, path, ops: [...] }` دارد: خودِ سند و همهٔ همراه‌هایش (قلم، حرکتِ
 * انبار، تراکنش، سابقه) با همهٔ فیلدها — در همان دسته، و همراهی که در دستهٔ بعدِ همان دستگاه برای سندِ
 * نگه‌داشته رسید به همان ردیف افزوده می‌شود ([append]). صاحب از هر دستگاهی همان را در
 * `GET /api/sync/v3/conflicts` می‌بیند. ⛔ جدا از دکانِ دیگر: نوشتن و خواندن فقط با `account_id` ِ خودِ همان
 * درخواست. ⛔ گونه‌های صندوقِ سند (`perm_denied`، `approval_*`) «برگرداندنی» نیستند (`REVERTIBLE` ِ
 * `sync-v3.js`)، پس `loser_value` هرگز op ِ برگرداندن نمی‌سازد. `lib/sync-v1.js` ِ مشترک با پمپ دست نمی‌خورد:
 * v1 از نگهبانِ فقط‌دکانِ `staff-approval.v1Guard` و v3 از `sync-v3.push` می‌آید.
 */
const { one, query } = require('../db');

/** سقفِ اندازه — همان سقفِ یک دستهٔ push (۲۵۶KB)؛ بیشتر ⇒ تا جایی که جا شود و `truncated` */
const MAX_BYTES = 256 * 1024;
const MAX_OPS = 1000;
const KEYS = ['op_id', 'table', 'row_id', 'type', 'kind', 'fields', 'ts', 'hlc', 'tx', 'base'];

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);

/** فقط کلیدهای خودِ op (نه نتیجهٔ داوری یا هشِ میانی) */
function pick(op) {
  const out = {};
  if (!isObj(op)) return out;
  for (const k of KEYS) if (op[k] !== undefined) out[k] = op[k];
  return out;
}

/** ops ⇒ بستهٔ صندوق؛ بی تکرارِ op_id، با سقفِ بایت و شمار */
function payload(path, ops, prev = null) {
  const list = [];
  const seen = new Set();
  let bytes = 0;
  let truncated = !!(prev && prev.truncated);
  const all = [...(prev && Array.isArray(prev.ops) ? prev.ops : []), ...(Array.isArray(ops) ? ops : [])];
  for (const raw of all) {
    const op = pick(raw);
    const id = typeof op.op_id === 'string' ? op.op_id : '';
    if (id && seen.has(id)) continue;
    const size = Buffer.byteLength(JSON.stringify(op), 'utf8');
    if (list.length >= MAX_OPS || bytes + size > MAX_BYTES) { truncated = true; continue; }
    if (id) seen.add(id);
    bytes += size;
    list.push(op);
  }
  return { v: 1, path: String(path || prev?.path || ''), ops: list, ...(truncated ? { truncated: true } : {}) };
}

/** همراه‌های سندی که پیش‌تر در صندوق نشسته (دستهٔ بعدِ همان دستگاه) — فقط ردیفِ همین دکان */
async function append(ctx, id, path, ops) {
  if (!id || !Array.isArray(ops) || !ops.length || !ctx || !ctx.accountId) return;
  const row = await one(
    `SELECT loser_value FROM sync_v3_conflicts WHERE id=$1 AND app='shop' AND account_kind='shop' AND account_id=$2`,
    [String(id), ctx.accountId]
  );
  if (!row) return;
  const prev = isObj(row.loser_value) && Array.isArray(row.loser_value.ops) ? row.loser_value : null;
  await query(
    `UPDATE sync_v3_conflicts SET loser_value=$3::jsonb WHERE id=$1 AND app='shop' AND account_kind='shop' AND account_id=$2`,
    [String(id), ctx.accountId, JSON.stringify(payload(path, ops, prev))]
  );
}

/** گردآوریِ همراه‌ها برای هر سند در یک دسته (`v1Guard`) */
function collector() {
  const by = new Map();
  return {
    add(parent, op) { if (!by.has(parent)) by.set(parent, []); by.get(parent).push(op); },
    of(parent) { return by.get(parent) || []; },
    held() { return [...by.keys()].filter((p) => p.held && p.boxId); },
  };
}

module.exports = { payload, append, collector, MAX_BYTES, MAX_OPS };
