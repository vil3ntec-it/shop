'use strict';
/**
 * دورِ دومِ فازِ تفاضلیِ `server/scripts/core-fuzz.js` (شرحِ کامل همان‌جا): هسته‌های دوقلویی که دورِ اول نداشت —
 * برگهٔ تأیید، مجوز، زنجیرهٔ هش و پرداخت این‌جا؛ جمع‌آوری، سفارشِ خرید و تابلوی گزارش در `core-fuzz-gen3.js`.
 * هر خانواده مولّد (ورودیِ واقعی + لبه) و دوندهٔ JS دارد؛ خروجیِ JS همان expect است. فقط از `core-fuzz.js` بار می‌شود.
 *
 *   approval  license/approval-core.js            ⇄ core/perm/ApprovalSlip.kt
 *   perm      license/perm-core.js                ⇄ core/perm/PermCore.kt
 *   chain     license/hash-chain.js (+ نسخهٔ سرور) ⇄ core/audit/{HashChain,PostedDocument}.kt
 *   pay       license/pay-core.js                 ⇄ core/pay/{Payment,PayFix,Shift}.kt
 */
const path = require('path');
const G = require('./core-fuzz-gen.js');

const { ROOT, MAX, S53, nearDate, baseDate } = G;

const APPROVAL = require(path.join(ROOT, 'license', 'approval-core.js'));
const PERM = require(path.join(ROOT, 'license', 'perm-core.js'));
const CHAIN = require(path.join(ROOT, 'license', 'hash-chain.js'));
const CHAIN_SERVER = require(path.join(ROOT, 'server', 'src', 'lib', 'hash-chain.js'));
const PAY = require(path.join(ROOT, 'license', 'pay-core.js'));

/* ---------------- لبه‌های مشترک ---------------- */

/** فاصله‌هایی که trim ِ JS و Kotlin برعکس می‌شناسند (BOM، U+001C..U+001F) + چند فاصلهٔ مشترک */
const PAD = ['﻿', '\u001F', '\u001C', ' ', ' ', '\t', ' ', '​', '　', '\u0085'];
function padded(r, s, p) {
  if (!r.chance(p)) return s;
  return (r.chance(0.7) ? r.pick(PAD) : '') + s + (r.chance(0.5) ? r.pick(PAD) : '');
}
/** متنی که «خالی» است یا نیست — بسته به این‌که کدام فاصله را فاصله بشماری */
const BLANKISH = ['', ' ', '﻿', '\u001F', '\u001C', ' ', '\t\n', '　', '​', '﻿\u001F'];
/** نام‌هایی که در شیءِ ساده‌ی JS کلیدِ پروتوتایپ‌اند */
const PROTO = ['__proto__', 'constructor', 'toString', 'hasOwnProperty', 'valueOf'];
/** ویژگیِ خودِ شیء، حتی «__proto__» (انتسابِ ساده پروتوتایپ را عوض می‌کرد و در JSON نمی‌آمد) */
function own(o, k, v) {
  Object.defineProperty(o, k, { value: v, enumerable: true, writable: true, configurable: true });
  return o;
}
const T0 = 1760000000000;

/* ======================= برگهٔ تأیید ======================= */

const NONCE_CH = 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789_-';
function nonceOf(r) {
  const ok = (n) => { let s = ''; for (let i = 0; i < n; i++) s += NONCE_CH[r.int(0, NONCE_CH.length - 1)]; return s; };
  return r.weighted([
    [8, () => ok(r.int(16, 64))],
    [1, () => ok(r.pick([15, 65, 0, 1]))],
    [1, () => ok(20) + r.pick(['\n', ' ', ' ', '.', '۱', '﻿'])],
    [1, () => r.pick(['\n' + ok(20), ok(10) + '+' + ok(10), 'نونس-نونس-نونس-نونس'])],
  ]);
}
function slipText(r, base, max) {
  return r.weighted([
    [8, () => padded(r, base, 0.1)],
    [1, () => r.pick(['', 'a\nb', 'x\r', 'y'.repeat(max), 'y'.repeat(max + 1)])],
    [1, () => r.pick(PROTO)],
  ]);
}
/** عددِ برگه: صحیحِ درست، لبهٔ ۲^۵۳، یا (فقط وقتی `loose`) نوعِ نادرست */
function slipNum(r, good, edges, loose) {
  return r.weighted([
    [7, good],
    [2, () => r.pick(edges)],
    [1, () => r.pick([2 ** 60, S53, 1e21, MAX])],
    [loose ? 1 : 0, () => r.pick(['1000', 1.5, true, -0.5, [], {}])],
  ]);
}
function slipOf(r, loose) {
  const iat = slipNum(r, () => T0 + r.int(0, 3e10), [0, -1, 1, MAX - 1000], loose);
  const s = {
    v: r.weighted([[9, 1], [1, () => r.pick([0, 2, 4294967297, -4294967295, 1e21])], [loose ? 1 : 0, () => r.pick(['1', 1.5, null, true])]]),
    shop: slipText(r, 'shp_test', 80),
    doc: slipText(r, r.pick(['sales/s-', 'returns/r-', 'saleItems/i-']) + r.int(1, 99), 200),
    perm: r.weighted([[8, () => r.pick(APPROVAL.PERMS)], [1, () => r.pick(['sales.Discount', 'returns', '', ' sales.cancel'])], [1, () => r.pick(PROTO)]]),
    by: slipText(r, 'usr_manager', 80),
    key: slipText(r, 'ak_7ea833d85856fc32ba38d95e', 80),
    iat,
    exp: typeof iat === 'number' ? r.weighted([
      [7, () => iat + r.int(1, APPROVAL.TTL_MAX_MS)],
      [2, () => iat + r.pick([0, -1, APPROVAL.TTL_MAX_MS, APPROVAL.TTL_MAX_MS + 1, 3600000])],
      [1, () => r.pick([MAX, 2 ** 60, iat + 2 ** 53])],
    ]) : T0,
    nonce: nonceOf(r),
    reason: r.weighted([[6, () => padded(r, 'مشتریِ همیشگی', 0.1)], [4, () => r.pick(BLANKISH)], [1, () => r.pick(['a\nb', 'x'.repeat(200), 'x'.repeat(201), '\u001Fتخفیف'])]]),
    sig: r.weighted([[8, () => 'MEUCIQ' + r.int(0, 1e9)], [1, ''], [1, 'x'.repeat(201)]]),
  };
  const pct = r.weighted([[5, () => r.int(0, 10000)], [2, null], [2, undefined], [1, () => slipNum(r, () => r.int(0, 10000), [-1, 10001, 0, 10000], loose)]]);
  const amount = r.weighted([[5, () => r.int(0, 1e9)], [2, null], [2, undefined], [1, () => slipNum(r, () => r.int(0, 1e9), [-1, 0, MAX, S53], loose)]]);
  if (pct !== undefined) s.pct_bp = pct;
  if (amount !== undefined) s.amount = amount;
  if (loose && r.chance(0.15)) {
    const k = r.pick(['shop', 'doc', 'perm', 'by', 'key', 'nonce', 'reason', 'sig', 'v', 'iat', 'exp']);
    if (r.chance(0.5)) delete s[k]; else s[k] = r.pick([5, null, true, [], { a: 1 }]);
  }
  return s;
}
const approval = {
  'approval.check': (r) => ({ slip: r.chance(0.97) ? slipOf(r, true) : r.pick([null, [], 'slip', 5]) }),
  'approval.covers': (r) => {
    const slip = slipOf(r, false);
    const num = (v, near) => r.weighted([[3, undefined], [1, null], [5, () => (typeof v === 'number' && Number.isSafeInteger(v) ? Math.max(0, v + r.int(-near, near)) : r.int(0, near))], [1, () => r.pick([0, -5, 10001, 2 ** 60, S53])]]);
    const need = {
      perm: r.chance(0.85) ? slip.perm : r.pick(APPROVAL.PERMS),
      doc: r.chance(0.85) ? slip.doc : 'sales/s-0',
    };
    const pct = num(slip.pct_bp, 200);
    const amount = num(slip.amount, 5000);
    if (pct !== undefined) need.pct_bp = pct;
    if (amount !== undefined) need.amount = amount;
    const S = APPROVAL.SKEW_MS;
    //  نیاز را همیشه سرور/برنامه با Long می‌سازد: بیرون از ±۲^۶۲ (iat ِ ۱e21) همان لبهٔ ۲^۶۰
    const lng = (v) => (Math.abs(v) <= 2 ** 62 ? v : Math.sign(v) * 2 ** 60);
    if (r.chance(0.8) && typeof slip.iat === 'number') {
      need.at = lng(r.weighted([
        [6, () => slip.iat + r.int(-S - 1000, Math.max(0, Math.min(slip.exp - slip.iat, 1e9)) + S + 1000)],
        [2, () => r.pick([slip.iat - S, slip.iat - S - 1, slip.exp + S, slip.exp + S + 1])],
        [1, () => r.pick([2 ** 60, 0, -1])],
      ]));
    }
    return { slip, need };
  },
};
function runApproval(fn, x) {
  if (fn === 'approval.check') {
    const code = APPROVAL.check(x.slip);
    return { check: code, payload: code === null ? APPROVAL.payload(x.slip) : null, evalReq: code === null ? APPROVAL.evalReq(x.slip) : null };
  }
  return APPROVAL.covers(x.slip, x.need);
}

/* ======================= مجوز ======================= */

const KEYS = PERM.keys();
const roleOf = (r) => r.weighted([[9, () => r.pick(PERM.ROLES)], [1, () => r.pick(['', 'Owner', 'courier', 'admin', ...PROTO])]]);
const keyOf = (r) => r.weighted([[9, () => r.pick(KEYS)], [1, () => r.pick(['', 'sales.Discount', 'sales', 'nope.write', ...PROTO])]]);
function capValue(r) {
  return r.weighted([
    [5, () => r.int(0, 100)],
    [2, () => r.int(0, 1e7)],
    [1, () => r.pick([0.5, 12.5, 100.0001, 101, 1e300, -0])],
    [1, () => r.pick([-1, -0.0001, null, '5', true, [], {}])],
  ]);
}
function overrideOf(r) {
  return r.weighted([
    [8, () => {
      const o = {};
      if (r.chance(0.8)) o.mode = r.weighted([[8, () => r.pick(PERM.MODES)], [1, () => r.pick(['', 'ALLOW', 'none', null, 5])]]);
      if (r.chance(0.5)) {
        const caps = {};
        for (const n of ['maxPct', 'maxAmount']) if (r.chance(0.5)) caps[n] = capValue(r);
        if (r.chance(0.1)) own(caps, r.pick(['maxpct', 'max_amount', '__proto__', 'other']), capValue(r));
        o.caps = r.chance(0.95) ? caps : r.pick([null, [], 'x', 5]);
      }
      if (r.chance(0.05)) own(o, r.pick(['extra', 'Mode', '__proto__']), 1);
      return o;
    }],
    [1, () => r.pick([null, [], 'deny', 5, true, [{ mode: 'allow' }]])],
  ]);
}
function layerOf(r) {
  if (r.chance(0.05)) return r.pick([null, 5, 'x', [], [{}]]);
  const L = {};
  for (let i = r.int(0, 4); i > 0; i--) own(L, keyOf(r), overrideOf(r));
  return L;
}
function layersOf(r) {
  if (r.chance(0.1)) return null;
  const out = [];
  for (let i = r.int(0, 3); i > 0; i--) out.push(layerOf(r));
  return out;
}
function reqOf(r) {
  if (r.chance(0.25)) return undefined;
  const q = {};
  if (r.chance(0.7)) q.pct = r.weighted([[5, () => r.int(0, 100)], [2, () => r.int(0, 10000) / 100], [1, () => r.pick([0, -1, 1e300, 100.5])]]);
  if (r.chance(0.7)) q.amount = r.weighted([[5, () => r.int(0, 1e7)], [1, () => r.pick([0, -1, MAX, 1e300])]]);
  return q;
}
const TABLES = ['warehouseEntries', 'stockMovements', 'sales', 'saleItems', 'products', 'safeEntries', 'auditLog', 'returns', 'purchases', '', 'Sales', ...PROTO];
function rowRefs(r) {
  const P = ['p1', 'p2', 'p3'];
  const qty = () => r.weighted([[6, () => r.pick([1, 2, 3, 0.5, 0.25, 5])], [2, () => r.pick([0.1, 0.2, 0.3, 0.7])], [1, () => r.pick([0, -1, 1e300])]]);
  const refs = { returns: [], sales: [], taken: [], saleItems: [], cancelled: [], moved: [] };
  for (let i = 1, n = r.int(0, 4); i <= n; i++) {
    const l = { id: r.chance(0.95) ? 'r' + i : '', productId: r.pick(P), qty: qty(), own: r.chance(0.8) };
    if (r.chance(0.6)) l.docId = 'd' + r.int(1, 2);
    refs.returns.push(l);
  }
  for (let i = 1, n = r.int(0, 2); i <= n; i++) refs.sales.push('s' + i);
  for (let i = 1, n = r.int(0, 4); i <= n; i++) {
    const it = { id: 'i' + i, saleId: 's' + r.int(1, 2), productId: r.pick(P) };
    if (r.chance(0.95)) it.qty = qty();
    refs.saleItems.push(it);
  }
  if (refs.sales.length && r.chance(0.5)) refs.cancelled.push(r.pick(refs.sales));
  for (let i = 0, n = r.int(0, 2); i < n; i++) {
    const t = { refId: r.pick(['r1', 'r2', 'd1', 'd2', '']), productId: r.pick(P) };
    if (r.chance(0.8)) t.units = r.pick([1, -1, 0.5, 2, -3]);
    refs.taken.push(t);
  }
  for (let i = 0, n = r.int(0, 3); i < n; i++) {
    const m = { refId: r.pick(['s1', 's2', 'r1', 'r2', 'd1', '']), productId: r.pick(P), type: r.pick(['sale', 'sale_cancel', 'customer_return', 'damage', 'transfer']) };
    if (r.chance(0.9)) m.qty = r.pick([-1, 1, -0.5, 2, -2, 0.1, -0.2]);
    refs.moved.push(m);
  }
  return refs;
}
function permRow(r) {
  const P = ['p1', 'p2', 'p3', '', 'p9'];
  return r.weighted([
    [5, () => {
      const kind = r.weighted([[8, () => r.pick(['customer_return', 'damage'])], [1, () => r.pick(['sale', '', 'Damage'])]]);
      const neg = kind === 'damage';
      const o = { isAdjustment: r.weighted([[9, true], [1, () => r.pick([false, 'true', 1, null])]]), adjKind: kind, refId: r.pick(['r1', 'r2', 'd1', 'd2', 'r9', '']), productId: r.pick(P) };
      o.units = r.weighted([[7, () => (neg ? -1 : 1) * r.pick([1, 2, 0.5, 0.25, 3])], [1, () => r.pick([0, -0, 1e300])], [1, () => (neg ? 1 : -1) * r.pick([1, 2])]]);
      for (const k of ['price', 'cartons', 'perCarton']) if (r.chance(0.15)) o[k] = r.pick([0, null, 5, '0', false, -0, 0.0]);
      return o;
    }],
    [5, () => {
      const type = r.weighted([[9, () => r.pick(['sale', 'sale_cancel', 'customer_return', 'damage'])], [1, () => r.pick(['transfer', '', 'Sale', ...PROTO])]]);
      const neg = type === 'sale' || type === 'damage';
      return {
        type, refId: r.pick(['s1', 's2', 'r1', 'r2', 'd1', 's9', '']), productId: r.pick(P),
        qty: r.weighted([[7, () => (neg ? -1 : 1) * r.pick([1, 2, 0.5, 0.1, 0.2, 0.3, 3])], [1, () => r.pick([0, 1e300])], [1, () => (neg ? 1 : -1)]]),
      };
    }],
    [1, () => r.pick([null, [], 'row', 5])],
  ]);
}
const perm = {
  'perm.resolve': (r) => {
    const x = { role: roleOf(r), layers: layersOf(r), key: keyOf(r) };
    const q = reqOf(r);
    if (q) x.req = q;
    return x;
  },
  'perm.effective': (r) => ({ role: roleOf(r), layers: layersOf(r) }),
  'perm.validate': (r) => ({ role: roleOf(r), key: keyOf(r), o: overrideOf(r) }),
  'perm.discountBp': (r) => {
    const v = () => r.weighted([
      [4, () => r.int(0, 1e7)], [2, () => r.int(-1000, 1000)], [2, () => r.int(0, MAX)],
      [2, () => r.pick([0, 1, 49, 50, 51, 99, 100, MAX, -MAX, 922337203685478, 922337203685477, 1e15, 2 ** 52, 549755813888])],
    ]);
    return { d: v(), t: v() };
  },
  'perm.rows': (r) => {
    const rows = [];
    for (let i = r.int(1, 4); i > 0; i--) {
      if (r.chance(0.03)) { rows.push(r.pick([null, 5, 'x'])); continue; }
      const table = r.weighted([[8, () => r.pick(['warehouseEntries', 'stockMovements'])], [2, () => r.pick(TABLES)]]);
      rows.push({ table, row: permRow(r) });
    }
    return { role: r.weighted([[8, 'cashier'], [2, () => roleOf(r)]]), rows, refs: rowRefs(r) };
  },
  'perm.write': (r) => ({ role: roleOf(r), table: r.pick(TABLES) }),
};
function runPerm(fn, x) {
  switch (fn) {
    case 'perm.resolve': return {
      resolve: PERM.resolve(x.role, x.layers, x.key), evaluate: PERM.evaluate(x.role, x.layers, x.key, x.req), allows: PERM.allows(x.role, x.layers, x.key),
    };
    case 'perm.effective': return PERM.effective(x.role, x.layers);
    case 'perm.validate': return PERM.validate(x.role, x.key, x.o);
    case 'perm.discountBp': return { bp: PERM.discountBp(x.d, x.t), pct: PERM.discountPct(x.d, x.t) };
    case 'perm.rows': return PERM.checkRows(x.role, x.rows, x.refs);
    case 'perm.write': return { canWrite: PERM.canWrite(x.role, x.table), writable: PERM.writable(x.role), rowRule: PERM.rowRule(x.role, x.table) };
    default: throw new Error('fn: ' + fn);
  }
}

/* ======================= زنجیرهٔ هش ======================= */

const JSTR = ['', 'a', 'B', 'z', 'ä', 'é', 'ﬀ', '𝒳', '😀', 'سلام', 'ي', '"', '\\', '\b\f\n\r\t', '\u0000', '\u001f', '\u007f', '  ',
  ' ', '﻿', '\u0085', '\ud83d', '\ude00', '\udc00x', '</script>', '۱۲۳'];
const JKEYS = ['a', 'b', 'B', 'id', 'qty', 'amount', 'total', '', 'ä', '𝒳', 'ﬀ', '__proto__', '10', '2', 'aa', 'a\u0000', 'z', 'Z', '\ud800'];
function jstr(r) { let s = ''; for (let i = r.int(0, 3); i > 0; i--) s += r.pick(JSTR); return s; }
function jnum(r) {
  return r.weighted([[6, () => r.int(-1e6, 1e6)], [2, () => r.pick([0, -0, 1, -1, MAX, -MAX, S53, -S53, 2 ** 60, 1e21])], [1, () => r.pick([1.5, -0.5, 0.1, 1e-7, 123.456])]]);
}
function jval(r, depth) {
  return r.weighted([
    [1, null], [1, () => r.chance(0.5)], [4, () => jnum(r)], [4, () => jstr(r)],
    [depth > 0 ? 2 : 0, () => { const a = []; for (let i = r.int(0, 3); i > 0; i--) a.push(jval(r, depth - 1)); return a; }],
    [depth > 0 ? 3 : 0, () => { const o = {}; for (let i = r.int(0, 4); i > 0; i--) own(o, r.pick(JKEYS), jval(r, depth - 1)); return o; }],
  ]);
}
const HEX = '0123456789abcdef';
function hex64(r) { let s = ''; for (let i = 0; i < 64; i++) s += HEX[r.int(0, 15)]; return s; }
/** سندِ ثبت‌شدهٔ معتبر (همهٔ مبلغ‌ها صحیح) — گاهی با لبه‌ای که canonicalJson رد می‌کند */
function postedDoc(r, i, bad) {
  const lines = [];
  for (let k = r.int(0, 3); k > 0; k--) {
    const l = { productId: 'p' + r.int(1, 3), qty: r.pick([1, 2, 1000, -1, 0]), unitPrice: r.int(0, 50000), amount: r.int(0, 1e6) };
    if (r.chance(0.2)) l.discount = r.pick([0, 100, null]);
    if (r.chance(0.1)) l.name = jstr(r);
    lines.push(l);
  }
  const d = {
    id: r.weighted([[9, 'inv-' + i], [1, () => r.pick([null, 7, ''])]]),
    series: 'A', no: i, type: 'sale', status: r.weighted([[8, 'posted'], [1, 'draft'], [1, () => r.pick(['Posted', '', null])]]),
    postedAt: T0 + i * 60000, lines, total: r.int(0, 1e7),
  };
  if (r.chance(0.5)) d.payments = [{ method: 'cash', amount: r.int(0, 1e6) }, ...(r.chance(0.3) ? [{ method: 'hesabpay', amount: r.int(0, 1e5), ref: 'tx' }] : [])];
  if (r.chance(0.3)) d.discount = r.pick([0, 500, null]);
  if (r.chance(0.2)) d.reverses = r.pick([null, 'inv-1']);
  if (r.chance(0.15)) d.note = jstr(r);
  if (r.chance(0.1)) d.tax = r.pick([0, 15, '15']);
  if (bad || r.chance(0.05)) {
    const t = r.pick(['qty', 'amount', 'total', 'meta']);
    if (t === 'meta') d.meta = jval(r, 2);
    else if (t === 'total') d.total = r.pick([1.5, S53, '100', true]);
    else if (lines.length) lines[0][t] = r.pick([0.5, 2 ** 60, '1', null]);
  }
  return d;
}
function chainOf(r) {
  const entries = [];
  for (let i = 1, n = r.int(0, 5); i <= n; i++) {
    const doc = postedDoc(r, i, false);
    try { entries.push(CHAIN.append(entries, doc)); } catch (e) { entries.push({ seq: i, prevHash: entries.length ? entries[entries.length - 1].hash : CHAIN.GENESIS, hash: hex64(r), doc }); }
  }
  for (const e of entries) if (r.chance(0.3)) delete e.seq;
  const k = entries.length ? r.int(0, entries.length - 1) : -1;
  r.weighted([
    [4, () => {}],
    [2, () => { if (k >= 0) entries[k].doc = Object.assign({}, entries[k].doc, { total: entries[k].doc.total + 1 }); }],
    [1, () => { if (entries.length > 1) { const j = r.int(0, entries.length - 1); const t = entries[k]; entries[k] = entries[j]; entries[j] = t; } }],
    [1, () => { if (k >= 0) entries.splice(k, 1); }],
    [1, () => { if (k >= 0) entries[k].seq = r.pick([0, k + 2, k, -1, 2 ** 60]); }],
    [1, () => { if (k >= 0) entries[k].prevHash = r.pick([entries[k].prevHash.toUpperCase(), '', hex64(r), entries[k].prevHash + '0']); }],
    [1, () => { if (k >= 0) entries[k].hash = r.pick([entries[k].hash.slice(0, 63), hex64(r), entries[k].hash.toUpperCase()]); }],
    [1, () => { if (k >= 0) entries[k].doc = postedDoc(r, k + 1, true); }],
    [1, () => { if (k >= 0) entries[k] = r.pick([null, {}]); }],
  ]);
  return entries;
}
const chain = {
  'chain.canonical': (r) => ({
    v: jval(r, 3),
    prev: r.weighted([[6, CHAIN.GENESIS], [3, () => hex64(r)], [1, () => r.pick(['', 'x', CHAIN.GENESIS.slice(1), 'A'.repeat(64), CHAIN.GENESIS + '\n'])]]),
  }),
  'chain.verify': (r) => ({ entries: chainOf(r) }),
  'chain.reverse': (r) => {
    const orig = postedDoc(r, r.int(1, 9), r.chance(0.1));
    const text = (base) => r.weighted([[7, () => padded(r, base, 0.1)], [3, () => r.pick(BLANKISH)]]);
    const opts = {
      id: text('rev-1'), reason: text('اشتباه در فاکتور'), by: text('usr_owner'),
      no: r.weighted([[8, () => r.int(1, 999)], [2, () => r.pick([0, -1, MAX, S53, 2 ** 60])]]),
      postedAt: r.weighted([[8, () => T0 + r.int(0, 1e9)], [2, () => r.pick([0, -1, MAX, -MAX, S53, -S53])]]),
    };
    const existing = [];
    for (let i = r.int(0, 2); i > 0; i--) existing.push({ id: 'rev-' + r.int(0, 9), reverses: r.pick([orig.id, 'inv-x', null, 'inv-1']), status: 'posted' });
    return { orig, opts, existing };
  },
  'chain.guard': (r) => {
    const before = r.chance(0.9) ? postedDoc(r, 1, false) : null;
    const after = before && r.chance(0.4) ? JSON.parse(JSON.stringify(before)) : postedDoc(r, 1, r.chance(0.1));
    if (before && after && r.chance(0.3)) after.status = before.status;
    return { before, after };
  },
};
function attempt(f) { try { return { value: f(), error: null }; } catch (e) { return { value: null, error: e.code || 'error' }; } }
function runChainWith(C, fn, x) {
  switch (fn) {
    case 'chain.canonical': {
      const j = attempt(() => C.canonicalJson(x.v));
      const h = attempt(() => C.entryHash(x.prev, x.v));
      return { json: j.value, error: j.error, hash: h.value, hashError: h.error };
    }
    case 'chain.verify': {
      const v = C.verifyEntries(x.entries);
      return v.ok ? { ok: true, length: v.length, head: v.head } : { ok: false, index: v.index, reason: v.reason };
    }
    case 'chain.reverse': {
      const out = attempt(() => C.reverseDoc(x.orig, Object.assign({}, x.opts, { existing: x.existing })));
      return { doc: out.value, error: out.error };
    }
    case 'chain.guard': {
      const g = attempt(() => C.guardEdit(x.before, x.after));
      return g.error ? { error: g.error } : g.value;
    }
    default: throw new Error('fn: ' + fn);
  }
}
const runChain = (fn, x) => runChainWith(CHAIN, fn, x);
/** نسخهٔ سرور (`server/src/lib/hash-chain.js`، با crypto ِ Node) فقط canonicalJson/entryHash/verifyEntries دارد */
function runChainServer(fn, x) {
  return fn === 'chain.canonical' || fn === 'chain.verify' ? runChainWith(CHAIN_SERVER, fn, x) : undefined;
}

/* ======================= پرداخت ======================= */

const METHOD_IDS = PAY.DEFAULT_METHODS.map((m) => m.id);
function methodsOf(r) {
  if (r.chance(0.65)) return undefined;
  const base = PAY.DEFAULT_METHODS.filter(() => r.chance(0.6)).map((m) => ({ id: m.id, kind: m.kind, name: m.name, needsRef: m.needsRef, limit: m.limit, enabled: m.enabled }));
  if (!base.some((m) => m.kind === 'cash') && r.chance(0.9)) base.unshift({ id: 'cash', kind: 'cash', name: 'نقد', needsRef: false, limit: null, enabled: true });
  for (const m of base) {
    if (r.chance(0.08)) m.id = padded(r, m.id, 1);
    if (r.chance(0.05)) m.id = r.pick(['', 'account', 'cash', ...PROTO]);
    if (r.chance(0.08)) m.name = r.pick(BLANKISH);
    if (r.chance(0.05)) m.kind = r.pick(['Cash', 'bank', '']);
    if (r.chance(0.1)) m.limit = r.pick([null, 1, 100000, 0, -1, 2 ** 60, MAX]);
    if (r.chance(0.1)) m.enabled = !m.enabled;
    if (r.chance(0.1)) m.needsRef = !m.needsRef;
  }
  if (r.chance(0.05)) base.push({ id: 'cash2', kind: 'cash', name: 'نقد ۲', needsRef: false, limit: null, enabled: true });
  return base;
}
const curOf = (r, def) => r.weighted([[7, def], [1, 'AFN'], [1, 'USD'], [1, ''], [1, () => r.pick(['afn', 'IRR', ...PROTO])]]);
const idText = (r, base) => r.weighted([[5, undefined], [3, () => padded(r, base, 0.15)], [2, () => r.pick(BLANKISH)]]);
const money = (r) => r.weighted([[6, () => r.int(1, 5000) * 100], [2, () => r.int(1, 999999)], [1, () => r.pick([0, -100, MAX, S53, 2 ** 60])]]);
function putOpt(o, k, v) { if (v !== undefined) o[k] = v; return o; }
function methodRef(r, list) {
  const ids = list ? list.map((m) => m.id) : METHOD_IDS;
  return r.weighted([[8, () => r.pick(ids)], [1, () => r.pick(['account', 'nope', '', ...PROTO])]]);
}
const pay = {
  'pay.settle': (r) => {
    const methods = methodsOf(r);
    const x = { total: r.weighted([[6, () => r.int(0, 500000) * r.pick([1, 100])], [1, () => r.pick([0, -1, MAX, S53, 2 ** 60])]]), tenders: [] };
    for (let i = r.int(0, 4); i > 0; i--) {
      const t = { method: methodRef(r, methods), amount: money(r) };
      putOpt(t, 'currency', curOf(r, undefined));
      putOpt(t, 'ref', r.weighted([[4, undefined], [4, () => 'TX' + r.int(1, 99999)], [2, () => r.pick(BLANKISH)]]));
      x.tenders.push(t);
    }
    putOpt(x, 'currency', curOf(r, undefined));
    putOpt(x, 'cashRounding', r.weighted([[6, undefined], [3, () => r.pick([0, 100, 500, 1000])], [1, () => r.pick([-100, 150, 2 ** 60, 1, 50])]]));
    putOpt(x, 'customerId', idText(r, 'c1'));
    putOpt(x, 'methods', methods);
    return x;
  },
  'pay.exchange': (r) => {
    const x = { from: { currency: curOf(r, 'USD'), amount: r.weighted([[6, () => r.int(1, 1e6)], [1, () => r.pick([0, -1, MAX, 2 ** 60])]]) } };
    putOpt(x, 'to', r.weighted([[5, undefined], [2, 'AFN'], [1, 'USD'], [1, ''], [1, () => r.pick(PROTO)]]));
    x.rate = r.weighted([[6, () => r.int(6000, 8000)], [1, () => r.pick([0, -1, 1, MAX, 2 ** 60])]]);
    putOpt(x, 'source', r.weighted([[6, () => padded(r, 'صرافی شهزاده', 0.1)], [3, () => r.pick(BLANKISH)], [1, undefined]]));
    putOpt(x, 'at', r.weighted([[7, () => T0 + r.int(0, 1e9)], [1, () => r.pick([0, -1, MAX, 2 ** 60])], [1, undefined]]));
    putOpt(x, 'approvedBy', r.weighted([[6, () => padded(r, 'usr_manager', 0.1)], [3, () => r.pick(BLANKISH)], [1, undefined]]));
    return x;
  },
  'pay.refund': (r) => {
    const methods = methodsOf(r);
    const payments = [];
    for (let i = r.int(0, 4); i > 0; i--) {
      const amount = r.weighted([[8, () => r.int(1, 5000) * 100], [1, () => r.pick([0, -1, 2 ** 60])]]);
      const p = { method: r.chance(0.2) ? 'account' : methodRef(r, methods), amount };
      putOpt(p, 'refunded', r.weighted([[5, undefined], [3, () => (amount > 0 && amount < 1e9 ? r.int(0, amount) : 0)], [1, () => r.pick([-1, amount + 1, 0])]]));
      p.status = r.weighted([[6, 'confirmed'], [2, 'partially_refunded'], [2, () => r.pick(PAY.STATUSES)], [1, () => r.pick(['', 'paid', ...PROTO])]]);
      payments.push(p);
    }
    const x = { payments, amount: r.weighted([[7, () => r.int(1, 5000) * 100], [1, () => r.pick([0, -1, 2 ** 60])]]), mode: r.weighted([[8, () => r.pick(['original', 'cash', 'store_credit'])], [1, () => r.pick(['', 'Cash', 'card'])]]) };
    putOpt(x, 'approvedBy', r.weighted([[6, () => padded(r, 'usr_manager', 0.1)], [3, () => r.pick(BLANKISH)], [1, undefined]]));
    putOpt(x, 'customerId', idText(r, 'c1'));
    putOpt(x, 'accountOutstanding', r.weighted([[6, undefined], [3, () => r.int(0, 300000)], [1, () => r.pick([0, -1, 2 ** 60])]]));
    putOpt(x, 'currency', curOf(r, undefined));
    let ms = methods;
    if (x.mode === 'store_credit' && r.chance(0.5)) ms = (methods || PAY.DEFAULT_METHODS.map((m) => Object.assign({}, m))).map((m) => (m.kind === 'store_credit' ? Object.assign({}, m, { enabled: true }) : m));
    putOpt(x, 'methods', ms);
    return x;
  },
  'pay.fix': (r) => {
    const methods = methodsOf(r);
    const ids = (methods || PAY.DEFAULT_METHODS).map((m) => m.id);
    const saleId = r.weighted([[9, 's1'], [1, () => r.pick(BLANKISH)]]);
    const rows = [];
    for (let i = r.int(0, 3); i > 0; i--) rows.push({ method: r.pick(ids.concat(['account'])), amount: r.weighted([[8, () => r.int(0, 5000) * 100], [1, () => r.pick([-1, 2 ** 60])]]) });
    const by = r.weighted([[7, () => padded(r, 'usr_staff', 0.15)], [2, () => r.pick(BLANKISH)], [1, undefined]]);
    const x = {
      id: r.weighted([[9, () => 'fix-' + r.int(1, 3)], [1, () => r.pick(BLANKISH)]]),
      sale: { id: saleId, rows, cancelled: r.chance(0.05), online: r.chance(0.05), returned: r.chance(0.05) },
      from: r.pick(ids.concat(['account', 'nope'])), to: r.pick(ids.concat(['account', 'nope'])),
      amount: r.weighted([[8, () => r.int(1, 3000) * 100], [1, () => r.pick([0, -1, 2 ** 60])]]),
      at: r.weighted([[9, () => T0 + r.int(0, 1e9)], [1, () => r.pick([0, -1, 2 ** 60])]]),
      decision: r.weighted([[5, 'allow'], [4, 'approve'], [1, () => r.pick(['deny', '', 'Allow'])]]),
      reason: r.weighted([[6, () => padded(r, 'اشتباه در روش', 0.15)], [2, () => r.pick(BLANKISH.concat(['ab', 'ab﻿', 'ab\u001F']))], [1, () => r.pick(['x'.repeat(120), 'x'.repeat(121), 'a\nbcd'])], [1, undefined]]),
      closed: r.chance(0.05),
      existing: [],
    };
    putOpt(x, 'by', by);
    //  تأییدکننده: گاهی همان کاربر با BOM یا U+001F (SELF_APPROVAL ِ دو سو باید یکی باشد)
    putOpt(x, 'approvedBy', r.weighted([[4, undefined], [3, () => padded(r, 'usr_manager', 0.1)], [2, () => (typeof by === 'string' ? padded(r, by.trim(), 1) : 'usr_staff')], [1, () => r.pick(BLANKISH)]]));
    putOpt(x, 'ref', r.weighted([[5, undefined], [3, () => 'TX' + r.int(1, 9999)], [1, () => r.pick(BLANKISH)], [1, () => r.pick(['x'.repeat(64), 'x'.repeat(65), 'a\nb'])]]));
    for (let i = r.int(0, 2); i > 0; i--) {
      x.existing.push({
        id: r.chance(0.3) ? x.id : 'fix-' + r.int(4, 9), saleId: r.chance(0.9) ? 's1' : 's2', from: r.pick(ids), to: r.pick(ids),
        amount: r.int(1, 30) * 100, at: T0 + r.int(-1e6, 1e9), by: 'usr_staff', approvedBy: 'usr_manager', reason: 'اشتباه در روش', ref: r.pick([null, 'TX1']),
      });
    }
    putOpt(x, 'methods', methods);
    return x;
  },
  'pay.fixApply': (r) => {
    const ids = METHOD_IDS.slice(0, 4).concat(['account']);
    const rows = [];
    for (let i = r.int(0, 3); i > 0; i--) rows.push({ method: r.pick(ids), amount: r.weighted([[8, () => r.int(0, 50) * 100], [1, () => -100]]) });
    const fixes = [];
    for (let i = r.int(0, 4); i > 0; i--) {
      fixes.push({
        id: 'f' + r.int(1, 4), saleId: r.chance(0.9) ? 's1' : 's2', from: r.pick(ids), to: r.pick(ids),
        amount: r.weighted([[8, () => r.int(1, 30) * 100], [1, () => r.pick([0, -100, 2 ** 60])]]), at: T0 + r.int(0, 5),
        by: 'u', approvedBy: 'u', reason: 'xyz', ref: null,
      });
    }
    const x = { saleId: 's1', rows, fixes };
    putOpt(x, 'until', r.weighted([[6, undefined], [3, () => T0 + r.int(0, 5)], [1, () => r.pick([0, MAX])]]));
    return x;
  },
  'pay.shift': (r) => {
    const methods = r.chance(0.8) ? undefined : methodsOf(r);
    const ids = (methods || PAY.DEFAULT_METHODS).map((m) => m.id);
    const amount = () => r.weighted([[8, () => r.int(1, 3000) * 100], [1, () => r.pick([0, -100, 2 ** 60])]]);
    const open = { id: r.weighted([[9, 'sh-1'], [1, () => r.pick(BLANKISH)]]), at: T0, float: r.weighted([[8, () => r.int(0, 5000) * 100], [1, () => r.pick([-1, 2 ** 60])]]) };
    putOpt(open, 'by', idText(r, 'ali'));
    putOpt(open, 'currency', r.weighted([[8, undefined], [1, ''], [1, () => r.pick(['USD', 'constructor'])]]));
    putOpt(open, 'threshold', r.weighted([[7, undefined], [2, () => r.pick([0, 5000, 10000])], [1, -1]]));
    if (r.chance(0.2)) open.coins = true;
    putOpt(open, 'methods', methods);
    const steps = [{ op: 'open', input: open }];
    let at = T0;
    for (let i = 1, n = r.int(0, 6); i <= n; i++) {
      at += r.int(0, 100);
      const type = r.weighted([[4, 'sale'], [1, 'refund'], [1, 'receipt'], [1, 'cash_in'], [2, 'cash_out'], [2, 'no_sale'], [0.3, 'bogus']]);
      const e = { id: r.weighted([[8, () => 'e' + i], [1, () => 'e' + r.int(1, i)], [1, () => r.pick(BLANKISH)]]), type, at: r.chance(0.95) ? at : T0 - 1 };
      if (type === 'sale' || type === 'refund') {
        e.rows = [];
        for (let k = r.weighted([[9, () => r.int(1, 3)], [1, 0]]); k > 0; k--) e.rows.push({ method: r.pick(ids.concat(['account', 'nope'])), amount: amount() });
      }
      if (type === 'receipt') { e.method = r.pick(ids.concat(['account'])); e.amount = amount(); }
      if (type === 'cash_in' || type === 'cash_out') {
        e.amount = amount();
        putOpt(e, 'reason', r.pick(['deposit', 'other', 'owner_withdrawal', 'petty_expense', '', 'x', undefined]));
        putOpt(e, 'note', r.weighted([[4, undefined], [3, 'یادداشت'], [3, () => r.pick(BLANKISH)]]));
        putOpt(e, 'by', idText(r, 'ali'));
      }
      if (type === 'no_sale') {
        //  کشو بی فروش: دلیل و تأییدکننده — «فقط BOM» در JS خالی است
        putOpt(e, 'reason', r.weighted([[5, () => padded(r, 'پول خرد', 0.15)], [3, () => r.pick(BLANKISH)], [1, undefined]]));
        putOpt(e, 'approvedBy', r.weighted([[5, () => padded(r, 'usr_manager', 0.15)], [4, () => r.pick(BLANKISH)], [1, undefined]]));
        putOpt(e, 'by', idText(r, 'ali'));
      }
      if (r.chance(0.05)) e.currency = r.pick(['', 'USD', 'AFN']);
      steps.push({ op: 'record', input: e });
    }
    steps.push({ op: 'x', input: putOpt({ at: at + 1 }, 'by', idText(r, 'ali')) });
    if (r.chance(0.7)) {
      const counts = {};
      for (const d of r.pick([[1000, 5000, 100000], [50000], [], [100, 1000]])) counts[String(d)] = r.int(0, 20);
      if (r.chance(0.05)) counts['777'] = 1;
      const c = { id: r.weighted([[9, 'z-1'], [1, () => r.pick(BLANKISH)]]), at: at + 2, counts };
      if (r.chance(0.3)) c.methodCounts = { [r.pick(ids.slice(1).concat(['cash']))]: r.int(0, 5) * 10000 };
      putOpt(c, 'by', idText(r, 'ali'));
      putOpt(c, 'reason', r.weighted([[5, undefined], [3, 'شمارش'], [2, () => r.pick(BLANKISH)]]));
      if (r.chance(0.3)) c.blind = true;
      steps.push({ op: 'close', input: c }, { op: 'z' });
    }
    return { steps };
  },
  'pay.fixClosed': (r) => {
    const day = baseDate(r);
    const shifts = [];
    for (let i = r.int(0, 3); i > 0; i--) {
      const openedAt = T0 + r.int(0, 100) * 1000;
      const w = { openedAt, day: r.weighted([[6, day], [2, () => padded(r, day, 1)], [1, () => nearDate(r, day, 1)], [1, '']]) };
      putOpt(w, 'closedAt', r.weighted([[7, () => openedAt + r.int(0, 100) * 1000], [2, null], [1, undefined]]));
      shifts.push(w);
    }
    return { saleAt: T0 + r.int(-10, 250) * 1000, saleDay: r.weighted([[6, day], [2, () => padded(r, day, 1)], [2, () => r.pick(BLANKISH)]]), shifts };
  },
};
/** شیفت: هر گام بی خودِ شیء ِ شیفت (فقط نتیجه، خطا و گزارش‌ها) */
function runShift(x) {
  let s = null;
  return x.steps.map((st) => {
    const i = st.input || {};
    const r = st.op === 'open' ? PAY.openShift(i) : st.op === 'record' ? PAY.record(s, i) : st.op === 'x' ? PAY.xReport(s, i)
      : st.op === 'close' ? PAY.closeShift(s, i) : PAY.zReport(s);
    if (r.ok && r.shift) s = r.shift;
    const o = {};
    for (const k of Object.keys(r)) if (k !== 'shift') o[k] = r[k];
    return o;
  });
}
function runPay(fn, x) {
  switch (fn) {
    case 'pay.shift': return runShift(x);
    case 'pay.settle': return { settle: PAY.settle(x), exact: PAY.exactCash(x) };
    case 'pay.exchange': return PAY.exchange(x);
    case 'pay.refund': return PAY.refund(x);
    case 'pay.fix': return PAY.payFix(x);
    case 'pay.fixApply': return PAY.payFixApply(x.saleId, x.rows, x.fixes, x.until);
    case 'pay.fixClosed': return PAY.payFixClosed(x.saleAt, x.saleDay, x.shifts);
    default: throw new Error('fn: ' + fn);
  }
}

module.exports = {
  APPROVAL, PERM, CHAIN, CHAIN_SERVER, PAY,
  approval, runApproval, perm, runPerm, chain, runChain, runChainServer, pay, runPay,
  //  کمک‌های مشترک با `core-fuzz-gen3.js`
  PAD, BLANKISH, PROTO, own, padded, putOpt, idText, T0,
};
