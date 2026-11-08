'use strict';
/**
 * STF-09 — زنجیرهٔ هشِ سابقهٔ عملیاتِ دستگاه (`auditLog`) روی سرور: قالبِ «tohid-audit-chain-v1».
 *
 * دوقلوی `core/audit/AuditChain.kt` (همان یکسان‌سازی، همان doc، همان سنجش) روی `hash-chain.js` ِ همین سرور
 * (همان SHA-256 و canonicalJson ِ v1؛ هیچ رمزنگاریِ تازه‌ای نیست). هر دو با
 * `core/test-vectors/audit-chain-v1.json` (سازنده: `scripts/audit-chain-vectors.js`) سنجیده می‌شوند.
 *
 * ── لنگرِ سرور ────────────────────────────────────────────────────────
 * ردیف‌های `auditLog` از همان راهِ همیشگیِ همگام‌سازی (`sync_rows`) می‌رسند و این‌جا هیچ چیزی نوشته
 * نمی‌شود. رونوشت خودش شاهد است: حذف در `sync_rows` نرم است (`deleted_at`، داده می‌ماند) و
 * `field_seq` می‌گوید هر فیلد آخرین بار کِی نوشته شد. کنارش سرِ گزارش‌شدهٔ هر زنجیره (`device_audit_anchors`،
 * مهاجرتِ ۴۱۰، `lib/device-audit-anchor.js`) — دستگاه تا کجا نوشته بود، همگام‌شده یا نه. پس پس از همگام‌سازی:
 *   ویرایش ⇐ hash_mismatch · حذف ⇐ deleted · جابه‌جایی/درج ⇐ prev_mismatch/seq_gap/fork
 *   بازنویسیِ فیلدِ مُهر یا حلقه پس از رسیدن ⇐ rewritten (بریدن یا دست‌کاریِ دُمِ همگام‌شده)
 *   ردیفِ شمارهٔ لنگر با هشِ دیگر ⇐ anchor_mismatch · دو هش برای یک شمارهٔ لنگر ⇐ fork · سرِ عقب‌رفته ⇐ rollback
 *   دُمِ زیرِ لنگر که نرسید ⇐ truncated — وقتی دورهٔ تازه‌ترِ همان دستگاه هست (دورهٔ کهنه هرگز ادامه نمی‌یابد،
 *   همان قاعدهٔ `AuditChain.resume`) یا لنگر بیش از [GRACE_MS] پیر است؛ وگرنه «در راه» (`pending`، صفِ دستگاه).
 *   دورهٔ تازه از ۱ (بازیابی، نصبِ دوباره) شکست نیست؛ دورهٔ کهنه تا لنگرِ خودش سنجیده می‌شود.
 * سنجش فقط خواندنی است: `npm run verify-chain -- --device-audit [<دکان>]`.
 * ⛔ `lib/sync-v1.js` و `routes/sync-v1.js` دست نمی‌خورند (مشترک با پمپ).
 */
const hc = require('./hash-chain');
const db = require('../db');

const FORMAT = 'tohid-audit-chain-v1';
const V = 1;
const STAMP = Object.freeze(['userId', 'userRole', 'deviceId']);
const LINK = Object.freeze(['chain', 'chainSeq', 'prev', 'hash', 'chainV']);
const HEAD = Object.freeze(['id', 'type', 'date', 'refId', 'createdAt', 'amount', 'amountMinor']);
const NOT_DETAIL = new Set([...HEAD, ...STAMP, ...LINK]);
const MAX_CHAIN = 200;
const PAGE = 2000;
/** ردیف‌های زیرِ لنگرِ دورهٔ جاری تا این‌قدر «در راه»اند (صفِ دستگاه، بی‌اینترنتی) پیش از «بریده» */
const GRACE_MS = 3 * 24 * 60 * 60 * 1000;

/** دلیلِ هر شکست، برای چاپ */
const REASONS = Object.freeze({
  bad_link: 'پیوندِ ردیف شکلِ درست ندارد',
  seq_gap: 'ردیفی از میانه نیست (پاک شده)',
  fork: 'دو ردیف با یک شماره',
  prev_mismatch: 'پیوند با ردیفِ پیشین نمی‌خواند (جابه‌جایی یا درج)',
  bad_doc: 'ردیف خوانا نیست',
  hash_mismatch: 'ردیف پس از ثبت ویرایش شده',
  truncated: 'دُمِ زنجیره بریده شده',
  anchor_mismatch: 'با لنگر نمی‌خواند',
  deleted: 'ردیفِ زنجیره‌دار حذف شده',
  rewritten: 'مُهر یا پیوندِ ردیف پس از رسیدن بازنویسی شده',
  rollback: 'دستگاه سرِ زنجیره را عقب‌تر از لنگرِ پیشین گزارش کرد',
});

function bitsHex(x) {
  const b = Buffer.alloc(8);
  b.writeDoubleBE(x);
  return b.toString('hex');
}

function isPlain(v) { return v !== null && typeof v === 'object' && !Array.isArray(v); }

/** درختِ JSON ⇐ درختِ hash-chain (عددِ ناصحیح ⇐ `$f`) */
function norm(v) {
  if (v === null || v === undefined) return null;
  if (typeof v === 'number') {
    if (!Number.isFinite(v)) throw new hc.HashChainError('bad_value', 'عددِ نامتناهی');
    return Number.isSafeInteger(v) ? (v === 0 ? 0 : v) : { $f: bitsHex(v) };
  }
  if (typeof v === 'string' || typeof v === 'boolean') return v;
  if (Array.isArray(v)) return v.map(norm);
  if (isPlain(v)) {
    const out = {};
    for (const k of Object.keys(v)) out[k] = norm(v[k]);
    return out;
  }
  throw new hc.HashChainError('bad_value', `نوعِ ${typeof v} در ردیف نمی‌آید`);
}

function empty(v) {
  return v === null || v === undefined || v === '' || v === 0 || v === false
    || (Array.isArray(v) && v.length === 0) || (isPlain(v) && Object.keys(v).length === 0);
}

/** «خالی» در هیچ شیئی نمی‌ماند؛ عضوِ فهرست جایش را نگه می‌دارد */
function prune(v) {
  if (Array.isArray(v)) return v.map(prune);
  if (isPlain(v)) {
    const out = {};
    for (const k of Object.keys(v)) { const p = prune(v[k]); if (!empty(p)) out[k] = p; }
    return out;
  }
  return v;
}

function detail(row) {
  const m = {};
  for (const k of Object.keys(row || {})) if (!NOT_DETAIL.has(k)) m[k] = norm(row[k]);
  return prune(m);
}

function detailHash(row) { return hc.sha256Hex(hc.canonicalJson(detail(row))); }

/** همان `sync-v3-convert.toMinor` / `Money.fromLegacy` */
function amountMinor(row) {
  if (Number.isSafeInteger(row.amountMinor)) return row.amountMinor;
  if (typeof row.amount !== 'number' || !Number.isFinite(row.amount)) return null;
  const v = Math.round(row.amount * 100);
  return Number.isSafeInteger(v) ? v : null;
}

function level(row) {
  const lv = norm(row.chainV);
  return Number.isSafeInteger(lv) && lv >= 1 ? lv : 1;
}

/** بدنهٔ هش‌شدهٔ ردیف — بی متنِ جزئیات */
function doc(row) {
  const m = { v: V, kind: 'audit', chain: norm(row.chain), seq: norm(row.chainSeq) };
  const lv = level(row);
  if (lv > 1) m.lv = lv;
  for (const k of ['id', 'type', 'date', 'refId', 'createdAt']) m[k] = norm(row[k]);
  m.amountMinor = amountMinor(row);
  for (const k of STAMP) m[k] = norm(row[k]);
  m.detailHash = detailHash(row);
  return prune(m);
}

function hashOf(row) {
  if (typeof row.prev !== 'string') throw new hc.HashChainError('bad_prev', 'prev نیست');
  return hc.entryHash(row.prev, doc(row));
}

const chainOk = (c) => typeof c === 'string' && c.length > 0 && c.length <= MAX_CHAIN && !/[\t\n\r]/.test(c);

/** حلقهٔ تازه پسِ `head` — برای بردار و آزمون (برنامه همین را در `AuditChain.linked` دارد) */
function linked(row, head, lv = 1) {
  const seq = head.seq + 1;
  const base = { ...row, chain: head.chain, chainSeq: seq, prev: head.hash };
  delete base.hash;
  delete base.chainV;
  if (lv > 1) base.chainV = lv;
  const hash = hashOf(base);
  return { row: { ...base, hash }, head: { chain: head.chain, seq, hash } };
}

function stamped(row, { userId, userRole, deviceId }) {
  const out = { ...row };
  if (typeof userId === 'string' && userId.trim()) out.userId = userId;
  if (typeof userRole === 'string' && userRole.trim()) out.userRole = userRole;
  out.deviceId = deviceId;
  return out;
}

class BadLink extends Error {}

/** پیوندِ ردیف؛ بی فیلدِ حلقه ⇐ null؛ بدشکل ⇐ BadLink */
function linkOf(row) {
  if (LINK.every((k) => norm(row[k]) === null)) return null;
  if (!chainOk(row.chain)) throw new BadLink();
  const seq = norm(row.chainSeq);
  if (!Number.isSafeInteger(seq) || seq < 1) throw new BadLink();
  if (typeof row.prev !== 'string' || !hc.HEX64.test(row.prev)) throw new BadLink();
  if (typeof row.hash !== 'string' || !hc.HEX64.test(row.hash)) throw new BadLink();
  const lv = norm(row.chainV);
  if (lv !== null && (!Number.isSafeInteger(lv) || lv < 1)) throw new BadLink();
  return { chain: row.chain, seq, prev: row.prev, hash: row.hash, level: lv === null ? 1 : lv };
}

const cmp = (a, b) => (a < b ? -1 : a > b ? 1 : 0);

/**
 * همان `AuditChain.verify`. ردیف‌ها شیءِ ساده‌اند (دادهٔ `auditLog`).
 * @param anchors {current, heads: {[chain]: {chain, seq, hash}}} یا null
 * @param strict  زنجیره از ۱ (سرور همیشه)
 * @param known   بالاترین سطحِ مدلِ خواننده (سرور: Infinity — همهٔ فیلدها را دارد)
 */
function verify(rows, { anchors = null, strict = false, known = 1 } = {}) {
  let legacy = 0;
  let unverified = 0;
  const bad = [];
  const groups = new Map();
  for (const r of rows) {
    let l;
    try { l = linkOf(r); } catch (e) {
      if (!(e instanceof BadLink)) throw e;
      const s = norm(r.chainSeq);
      bad.push({ chain: typeof r.chain === 'string' ? r.chain : '', seq: Number.isSafeInteger(s) ? s : 0, reason: 'bad_link', id: typeof r.id === 'string' ? r.id : null });
      continue;
    }
    if (!l) { legacy++; continue; }
    if (!groups.has(l.chain)) groups.set(l.chain, []);
    groups.get(l.chain).push([l, r]);
  }
  const results = [...groups.keys()].sort(cmp).map((chain) => {
    //  پایدار: هم‌شماره‌ها به ترتیبِ فهرست
    const list = groups.get(chain).map((x, i) => [x, i]).sort((a, b) => (a[0][0].seq - b[0][0].seq) || (a[1] - b[1])).map((x) => x[0]);
    let broken = null;
    let last = null;
    for (const [l, r] of list) {
      const id = typeof r.id === 'string' ? r.id : null;
      const fail = (seq, reason) => { broken = { chain, seq, reason, id }; };
      if (!last) {
        if (l.seq === 1 && l.prev !== hc.GENESIS) { fail(1, 'prev_mismatch'); break; }
        if (l.seq > 1 && strict) { fail(1, 'seq_gap'); break; }
      } else {
        if (l.seq === last.seq) { fail(l.seq, 'fork'); break; }
        if (l.seq !== last.seq + 1) { fail(last.seq + 1, 'seq_gap'); break; }
        if (l.prev !== last.hash) { fail(l.seq, 'prev_mismatch'); break; }
      }
      if (l.level > known) {
        unverified++;
      } else {
        let h;
        try { h = hashOf(r); } catch { fail(l.seq, 'bad_doc'); break; }
        if (h !== l.hash) { fail(l.seq, 'hash_mismatch'); break; }
      }
      last = l;
    }
    const first = list[0][0];
    const top = last || first;
    let anchorBreak = null;
    const a = !broken && anchors && anchors.heads ? anchors.heads[chain] : null;
    if (a) {
      if (a.seq > top.seq) anchorBreak = { chain, seq: top.seq + 1, reason: 'truncated', id: null };
      else if (a.seq >= first.seq && !list.some(([l]) => l.seq === a.seq && l.hash === a.hash)) anchorBreak = { chain, seq: a.seq, reason: 'anchor_mismatch', id: null };
    }
    return { chain, from: first.seq, seq: top.seq, head: top.hash, rows: list.length, broken: broken || anchorBreak };
  });
  if (anchors && anchors.heads && anchors.current) {
    const h = anchors.heads[anchors.current];
    if (h && h.seq > 0 && !groups.has(anchors.current)) {
      results.push({ chain: anchors.current, from: 0, seq: 0, head: hc.GENESIS, rows: 0, broken: { chain: anchors.current, seq: 1, reason: 'truncated', id: null } });
    }
  }
  results.sort((x, y) => cmp(x.chain, y.chain));
  const firstBroken = bad[0] || (results.find((c) => c.broken) || {}).broken || null;
  return {
    ok: bad.length === 0 && results.every((c) => !c.broken),
    chains: results,
    legacy,
    unverified,
    chained: results.reduce((n, c) => n + c.rows, 0),
    bad,
    broken: firstBroken,
  };
}

const deviceOfChain = (c) => c.slice(0, Math.max(0, c.lastIndexOf('/')));
const epochOfChain = (c) => { const n = Number(c.slice(c.lastIndexOf('/') + 1)); return Number.isSafeInteger(n) ? n : 0; };

/**
 * لنگرهای سرور (`device_audit_anchors`) روی رونوشت ⇐ { breaks, heads }. هر لنگر: ردیفِ همان شماره (اگر رسیده) همان
 * هش را دارد، وگرنه `anchor_mismatch`؛ `fork`/`rollback` ِ ثبت‌شده هنگامِ گزارش همان‌جا شکست است. بزرگ‌ترین لنگرِ
 * هر زنجیره جلوتر از بزرگ‌ترین ردیفِ رسیده ⇐ `truncated` اگر دوره بسته است (دورهٔ تازه‌ترِ همان دستگاه در ردیف‌ها یا
 * لنگرها) یا لنگر پیرتر از [GRACE_MS]؛ وگرنه `pending` (هنوز در صفِ دستگاه).
 */
function anchorCheck(stored, anchors, now) {
  const seen = new Map();
  const latest = new Map();
  const bump = (chain) => {
    const d = deviceOfChain(chain);
    const e = epochOfChain(chain);
    if (e > (latest.get(d) || 0)) latest.set(d, e);
  };
  for (const s of stored) {
    let l;
    try { l = linkOf(s.data || {}); } catch { continue; }
    if (!l) continue;
    if (!seen.has(l.chain)) seen.set(l.chain, new Map());
    const m = seen.get(l.chain);
    if (!m.has(l.seq)) m.set(l.seq, new Set());
    m.get(l.seq).add(l.hash);
    bump(l.chain);
  }
  const byChain = new Map();
  for (const a of anchors) {
    if (!byChain.has(a.chain)) byChain.set(a.chain, []);
    byChain.get(a.chain).push(a);
    bump(a.chain);
  }
  const breaks = [];
  const heads = {};
  for (const [chain, list] of byChain) {
    list.sort((x, y) => x.seq - y.seq);
    const rows = seen.get(chain) || new Map();
    for (const a of list) {
      if (a.problem === 'fork' || a.problem === 'rollback') breaks.push({ chain, seq: a.seq, reason: a.problem, id: null });
      const have = rows.get(a.seq);
      if (have && !have.has(a.hash)) breaks.push({ chain, seq: a.seq, reason: 'anchor_mismatch', id: null });
    }
    const last = list[list.length - 1];
    let top = 0;
    for (const k of rows.keys()) if (k > top) top = k;
    let pending = 0;
    if (last.seq > top) {
      const closed = epochOfChain(chain) < (latest.get(deviceOfChain(chain)) || 0);
      if (closed || now - last.reportedAt > GRACE_MS) breaks.push({ chain, seq: top + 1, reason: 'truncated', id: null });
      else pending = last.seq - top;
    }
    heads[chain] = { seq: last.seq, hash: last.hash, pending };
  }
  return { breaks, heads };
}

/**
 * سنجشِ رونوشتِ سرور: `[{ data, deleted, rewritten }]` (همان `sync_rows`). بر [verify] (strict، همهٔ سطح‌ها)
 * دو شکستِ فقط‌سرور می‌افزاید: ردیفِ زنجیره‌دارِ حذف‌شده (`deleted`) و فیلدِ مُهر/حلقه‌ای که پس از رسیدن
 * دوباره نوشته شد (`rewritten`)؛ و با [anchors] (ردیف‌های `device_audit_anchors`) شکست‌های لنگر ([anchorCheck]).
 * نخستین شکست به ترتیبِ (زنجیره، شماره). هر زنجیره `anchor` (بزرگ‌ترین لنگر) و `pending` دارد.
 */
function verifyStored(stored, { anchors = [], now = Date.now() } = {}) {
  const rep = verify(stored.map((s) => s.data || {}), { strict: true, known: Infinity });
  const extra = [];
  for (const s of stored) {
    let l;
    try { l = linkOf(s.data || {}); } catch { continue; }
    if (!l) continue;
    const id = typeof (s.data || {}).id === 'string' ? s.data.id : null;
    if (s.deleted) extra.push({ chain: l.chain, seq: l.seq, reason: 'deleted', id });
    else if (s.rewritten) extra.push({ chain: l.chain, seq: l.seq, reason: 'rewritten', id });
  }
  const anc = anchorCheck(stored, anchors, now);
  extra.push(...anc.breaks);
  const anchorOf = (chain) => (anc.heads[chain] ? { seq: anc.heads[chain].seq, hash: anc.heads[chain].hash } : null);
  const chains = rep.chains.map((c) => ({ ...c, anchor: anchorOf(c.chain), pending: anc.heads[c.chain] ? anc.heads[c.chain].pending : 0 }));
  for (const chain of Object.keys(anc.heads)) {
    if (!chains.some((c) => c.chain === chain)) {
      chains.push({ chain, from: 0, seq: 0, head: hc.GENESIS, rows: 0, broken: null, anchor: anchorOf(chain), pending: anc.heads[chain].pending });
    }
  }
  chains.sort((x, y) => cmp(x.chain, y.chain));
  const all = [...rep.bad, ...rep.chains.filter((c) => c.broken).map((c) => c.broken), ...extra]
    .sort((x, y) => cmp(x.chain, y.chain) || (x.seq - y.seq));
  return {
    ...rep, chains, extra, ok: rep.ok && extra.length === 0, broken: all[0] || null,
    pending: chains.reduce((n, c) => n + c.pending, 0),
  };
}

/** فیلدِ مُهر یا حلقه‌ای که دیرتر از نخستین نوشتنِ ردیف نوشته شد (`field_seq` ِ `sync_rows`) */
function rewrittenOf(fieldSeq) {
  if (!isPlain(fieldSeq)) return false;
  const seqs = Object.values(fieldSeq).map(Number).filter(Number.isFinite);
  if (!seqs.length) return false;
  const first = Math.min(...seqs);
  return [...STAMP, ...LINK].some((k) => k in fieldSeq && Number(fieldSeq[k]) > first);
}

/** ردیف‌های `auditLog` ِ یک دکان در `sync_rows` (حذف‌شده‌ها هم) و لنگرهایش ⇒ گزارشِ [verifyStored] */
async function verifyShop(shopId, { now = Date.now() } = {}) {
  const stored = [];
  let after = '';
  for (;;) {
    const r = await db.query(
      `SELECT row_id, data, field_seq, deleted_at FROM sync_rows
        WHERE app = 'shop' AND account_kind = 'shop' AND account_id = $1 AND table_name = 'auditLog' AND row_id > $2
        ORDER BY row_id LIMIT ${PAGE}`,
      [String(shopId), after],
    );
    for (const row of r.rows) {
      const data = typeof row.data === 'string' ? JSON.parse(row.data) : row.data;
      const fs = typeof row.field_seq === 'string' ? JSON.parse(row.field_seq) : row.field_seq;
      stored.push({ data: data || {}, deleted: row.deleted_at !== null && row.deleted_at !== undefined, rewritten: rewrittenOf(fs) });
    }
    if (r.rows.length < PAGE) break;
    after = r.rows[r.rows.length - 1].row_id;
  }
  const anchors = await require('./device-audit-anchor').anchorsOf(shopId);
  return { shopId: String(shopId), ...verifyStored(stored, { anchors, now }) };
}

/** همهٔ دکان‌هایی که دست‌کم یک ردیفِ زنجیره‌دار یا یک لنگر دارند (یا فقط [shops]) */
async function verifyAllShops({ shops = [], now = Date.now() } = {}) {
  let ids = shops.map(String);
  if (!ids.length) {
    const r = await db.query(
      `SELECT account_id AS id FROM sync_rows
        WHERE app = 'shop' AND account_kind = 'shop' AND table_name = 'auditLog' AND data ? 'hash'
       UNION SELECT shop_id AS id FROM device_audit_anchors
       ORDER BY id`,
    );
    ids = r.rows.map((x) => x.id);
  }
  const results = [];
  for (const id of ids) results.push(await verifyShop(id, { now }));
  return { ok: results.every((x) => x.ok), results };
}

module.exports = {
  FORMAT, V, STAMP, LINK, REASONS, GRACE_MS,
  norm, prune, detail, detailHash, amountMinor, doc, hashOf, linked, stamped, linkOf,
  verify, anchorCheck, verifyStored, rewrittenOf, verifyShop, verifyAllShops,
};
