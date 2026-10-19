/* ==========================================================
   فروشگاه من | سیم‌کشیِ پرداخت و صندوق — PAY-01..07، 11..15، 19 (v3 A4)
   ----------------------------------------------------------
   هم‌زادِ `android-native/.../data/PayFlow.kt`. هر دو روی همان هستهٔ
   `license/pay-core.js` / `core/pay/*.kt` و همان بردارِ مرجع
   (`core/test-vectors/pay-v1.json`) سنجیده می‌شوند؛ یکی را عوض کنی، دیگری هم.

   خالص است: نه DOM، نه ساعت، نه ذخیره. دفتر و ساعت را صفحه می‌دهد.

   ── پشتِ پرچم ─────────────────────────────────────────────────────
   هیچ‌کدام از این‌ها بی پرچمِ `tohid-pay-v2` (وب) / `ShopPolicy.payV2`
   (برنامه) صدا زده نمی‌شود؛ پیش‌فرض خاموش است و فروش و صندوقِ امروز
   (`shiftTotals` / `CashShift`) دست نمی‌خورد.

   ── شکلِ داده: فقط افزودن ────────────────────────────────────────
   فروش همان فیلدهای امروز را با همان معنا دارد (`paymentMethod`،
   `paidAmount`، `remaining`، `debtGiven`) و سه فیلدِ تازه:
     payments      [{method, kind, amountMinor, ref, status, verification}]
     tenderedMinor دریافتیِ کل (نقد + غیرنقد)
     changeMinor   پولِ پس (فقط از نقد)
   برنامهٔ کهنه فیلدِ تازه را نمی‌شناسد و همان عددِ امروز را می‌بیند
   (فروشِ کیف‌پولی در صندوقِ کهنه نقد شمرده می‌شود — بردارِ legacyShift ۲).

   رویدادِ صندوق ردیفِ «سابقه» است (`auditLog`)، با `refId` = شناسهٔ
   `shift_open`: `cash_in`، `cash_out`، `no_sale` با `amount` (افغانی)،
   `reason`، `note`، `by`، `approvedBy`. بستن همان `shift_close` است
   (`amount` = نقدِ شمرده، همان معنای امروز) به‌اضافهٔ `z`: گزارشِ Zِ هسته
   در لحظهٔ بستن — لغوِ بعدی آن را عوض نمی‌کند (بردارِ legacyShift ۳).
   ========================================================== */
(function (factory) {
  'use strict';
  var isNode = typeof module !== 'undefined' && module.exports;
  var g = typeof window !== 'undefined' ? window : globalThis;
  var M = isNode ? require('./money.js') : g.TohidMoney;
  var P = isNode ? require('./pay-core.js') : g.TohidPay;
  var api = factory(M, P);
  if (isNode) module.exports = api;
  g.TohidPayFlow = api;
})(function (M, P) {
  'use strict';

  if (!M || !P) throw new Error('money.js و pay-core.js باید پیش از pay-flow.js بار شوند');

  var CASH = 'cash';
  var ACCOUNT = P.ACCOUNT;
  var AUDIT_TYPES = { cash_in: 1, cash_out: 1, no_sale: 1 };

  // ── واحد ────────────────────────────────────────────────
  /* افغانیِ دفتر ⇒ پول؛ همان `moneyFromLegacy` (Math.round(x × 100)) */
  function minor(afn) {
    var n = Number(afn);
    return M.moneyFromLegacy(isFinite(n) ? n : 0, 'AFN').minor;
  }
  function major(m) { return m / 100; }
  function has(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }
  function text(v) { return typeof v === 'string' ? v.trim() : ''; }

  /* null = خالی؛ NaN = نامعتبر؛ وگرنه عدد (افغانی) */
  function amountOf(v) {
    if (v === null || v === undefined) return null;
    if (typeof v === 'number') return isFinite(v) && v >= 0 ? v : NaN;
    var t = String(v).trim();
    if (t === '') return null;
    var n = Number(t);
    return isFinite(n) && n >= 0 ? n : NaN;
  }

  // ── روش‌ها ──────────────────────────────────────────────
  function method(id) {
    if (id === ACCOUNT) return { id: ACCOUNT, kind: ACCOUNT, name: 'به حسابِ مشتری', needsRef: false };
    for (var i = 0; i < P.DEFAULT_METHODS.length; i++) if (P.DEFAULT_METHODS[i].id === id) return P.DEFAULT_METHODS[i];
    return null;
  }
  function methodName(id) { var m = method(id); return m ? m.name : String(id || ''); }
  /* روش‌های ردیفِ پنل: روشن و غیرنقد (کیف پول، کارت، حواله) */
  function panelMethods() {
    return P.DEFAULT_METHODS.filter(function (m) { return m.enabled && m.kind !== 'cash'; });
  }

  // ── پیام‌ها (همان متنِ PayFlow.kt) ─────────────────────
  function message(r) {
    var code = r && r.error, row = r && r.method ? '«' + methodName(r.method) + '»' : '';
    switch (code) {
      case 'REF_REQUIRED': return 'شمارهٔ مرجعِ ' + (row || 'این روش') + ' لازم است';
      case 'NON_CASH_OVERPAY': return 'پرداختِ غیرنقد بیش از مبلغِ فاکتور است؛ پولِ پس فقط از نقد داده می‌شود';
      case 'METHOD_LIMIT': return 'مبلغِ ' + (row || 'این روش') + ' از سقفِ همان روش بیشتر است';
      case 'METHOD_DISABLED':
      case 'UNKNOWN_METHOD': return 'روشِ پرداخت شناخته نیست';
      case 'SHORT': return 'باقی‌مانده ' + fmt(r.remaining) + ' افغانی است — مشتری را انتخاب کنید یا مبلغ را کامل کنید';
      case 'TOTAL_MISMATCH': return 'مبلغِ پرداخت با فاکتور نمی‌خواند؛ دوباره باز کنید';
      case 'INSUFFICIENT_CASH': return 'نقدِ دخل برای این خروج کافی نیست';
      case 'REASON_REQUIRED': return 'دلیل لازم است';
      case 'NOTE_REQUIRED': return 'برای «دیگر» توضیح بنویسید';
      case 'BAD_REASON': return 'دلیل درست نیست';
      case 'APPROVAL_REQUIRED': return 'باز کردنِ کشو بی فروش فقط با تأییدِ صاحب یا مدیر';
      case 'SHIFT_CLOSED': return 'صندوقِ بازی نیست';
      case 'SHIFT_OPEN_ALREADY': return 'صندوق از قبل باز است';
      case 'INVALID_TIME': return 'ساعتِ این دستگاه از زمانِ باز شدنِ صندوق عقب‌تر است';
      case 'BAD_DENOMINATION':
      case 'INVALID_COUNT': return 'شمارِ اسکناس درست نیست';
      default: return 'مبلغ درست نیست';
    }
  }
  function fmt(m) {
    var v = major(Math.abs(m || 0));
    return (m < 0 ? '−' : '') + v.toLocaleString('en-US', { maximumFractionDigits: 2 });
  }

  // ── پنلِ پرداخت (PAY-01، 02، 04، 05، 06، 07) ───────────
  /* draft = {total: افغانی، cash: متن/عدد، rows: [{method, ref, amount}], customerId}
     ردیفِ بی مبلغ و بی مرجع نادیده گرفته می‌شود (ردیفِ خالیِ تازه). */
  function input(d, withCash) {
    d = d || {};
    var tenders = [], at = [];
    if (withCash !== false) {
      var c = amountOf(d.cash);
      if (c !== c) return { ok: false, error: 'INVALID_AMOUNT', field: 'cash' };
      if (c !== null && c > 0) { tenders.push({ method: CASH, amount: minor(c) }); at.push(-1); }
    }
    var rows = d.rows || [];
    for (var i = 0; i < rows.length; i++) {
      var r = rows[i] || {};
      var a = amountOf(r.amount), ref = text(r.ref);
      if (a !== a) return { ok: false, error: 'INVALID_AMOUNT', index: i, method: r.method };
      if (a === null || a === 0) { if (!ref) continue; return { ok: false, error: 'INVALID_AMOUNT', index: i, method: r.method }; }
      tenders.push({ method: r.method, amount: minor(a), ref: ref || undefined });
      at.push(i);
    }
    return { ok: true, at: at, input: { total: minor(d.total), tenders: tenders, customerId: text(d.customerId) || undefined } };
  }

  /* پیش‌نمایشِ زنده: همان `settle`ِ هسته؛ خطا با شمارهٔ ردیف و روش */
  function preview(d) {
    var x = input(d);
    if (!x.ok) return x;
    var s = P.settle(x.input);
    if (!s.ok) {
      var at = typeof s.index === 'number' && s.index >= 0 ? x.at[s.index] : -1;
      return { ok: false, error: s.error, index: at, method: at >= 0 ? d.rows[at].method : undefined };
    }
    return s;
  }

  /* PAY-04 — «نقدِ دقیق»: نقدی که با ردیف‌های غیرنقدِ فعلی فاکتور را می‌بندد (افغانی؛ ۰ یعنی لازم نیست) */
  function exactCash(d) {
    var x = input(d, false);
    if (!x.ok) return x;
    var r = P.exactCash(x.input);
    return r.ok ? { ok: true, amount: major(r.amount) } : r;
  }

  /* فیلدهای فروش از تسویه — همان معنای امروزِ فیلدهای قدیمی + سه فیلدِ تازه */
  function saleFields(s) {
    var toAccount = s.toAccount || 0;
    return {
      paymentMethod: toAccount > 0 ? 'credit' : 'cash',
      paidAmount: major(s.total - toAccount),
      remaining: major(toAccount),
      debtGiven: major(toAccount),
      payments: s.payments.map(function (p) {
        var o = { method: p.method, kind: p.kind, amountMinor: p.amount, status: p.status };
        if (p.ref) o.ref = p.ref;
        if (p.verification) o.verification = p.verification;
        return o;
      }),
      tenderedMinor: s.tendered,
      changeMinor: s.change,
    };
  }

  /* تسویهٔ نهایی برای ثبت: هر چیزی که فروش را نیمه‌کاره می‌گذارد رد می‌شود */
  function checkout(d) {
    var s = preview(d);
    if (!s.ok) return s;
    if (s.status === 'short') return { ok: false, error: 'SHORT', remaining: s.remaining };
    return { ok: true, settlement: s, fields: saleFields(s) };
  }

  // ── صندوق (PAY-11..15، 19) ─────────────────────────────
  function inWindow(at, from, to) { at = at || 0; return at >= from && at <= to; }

  /* ردیف‌های فروشِ یک فاکتور: `payments` اگر هست، وگرنه همان حسابِ امروز */
  function saleRows(s) {
    if (Array.isArray(s.payments) && s.payments.length) {
      return s.payments.filter(function (p) { return p && p.amountMinor > 0; })
        .map(function (p) { return { method: p.method, amount: p.amountMinor }; });
    }
    var debt = Math.max(0, Number(s.debtGiven) || 0);
    var cash = Math.max(0, (Number(s.finalTotal) || 0) - debt);
    var out = [];
    if (minor(cash) > 0) out.push({ method: CASH, amount: minor(cash) });
    if (minor(debt) > 0) out.push({ method: ACCOUNT, amount: minor(debt) });
    return out;
  }

  /* دفتر ⇒ رویدادهای هسته. ترتیب: ورودی‌ها، خروجِ ثبت‌شده در صندوق، برگشت، خروج‌های دفتر.
     هسته ترتیبِ زمانی نمی‌خواهد؛ این ترتیب فقط نمی‌گذارد خروجی که در لحظهٔ خودش
     سنجیده و پذیرفته شده، این‌جا به‌خاطرِ رویدادِ دیرتر رد شود. */
  function ledgerEvents(L, open, to) {
    var from = open.createdAt || 0, ev = [];
    function push(group, e) { ev.push({ g: group, e: e }); }
    (L.sales || []).forEach(function (s) {
      if (s.status === 'cancelled' || !inWindow(s.createdAt, from, to)) return;
      var rows = saleRows(s);
      if (rows.length) push(0, { id: 'sale:' + s.id, type: 'sale', at: s.createdAt || 0, rows: rows });
    });
    (L.transactions || []).forEach(function (t) {
      if (t.type !== 'receive' || !inWindow(t.createdAt, from, to) || !(minor(t.amount) > 0)) return;
      push(0, { id: 'rcv:' + t.id, type: 'receipt', at: t.createdAt || 0, method: CASH, amount: minor(t.amount) });
    });
    (L.auditLog || []).forEach(function (a) {
      if (!has(AUDIT_TYPES, a.type) || a.refId !== open.id) return;
      var e = { id: 'a:' + a.id, type: a.type, at: a.createdAt || 0, by: a.by || undefined };
      if (a.type === 'no_sale') { e.reason = a.reason; e.approvedBy = a.approvedBy; push(1, e); return; }
      e.amount = minor(a.amount); e.reason = a.reason; e.note = a.note || undefined;
      push(a.type === 'cash_in' ? 0 : 1, e);
    });
    (L.returns || []).forEach(function (r) {
      if (!inWindow(r.createdAt, from, to) || !(minor(r.amount) > 0)) return;
      push(2, { id: 'ret:' + r.id, type: 'refund', at: r.createdAt || 0, rows: [{ method: CASH, amount: minor(r.amount) }] });
    });
    function out(list, field, prefix, reason, note) {
      (list || []).forEach(function (x) {
        if (!inWindow(x.createdAt, from, to) || !(minor(x[field]) > 0)) return;
        push(3, { id: prefix + x.id, type: 'cash_out', at: x.createdAt || 0, amount: minor(x[field]), reason: reason, note: note });
      });
    }
    out(L.expenses, 'amount', 'exp:', 'petty_expense', undefined);
    out(L.supplierPayments, 'amount', 'sup:', 'other', 'پرداخت به تأمین‌کننده');
    out(L.purchases, 'paidAmount', 'pur:', 'other', 'پیش‌پرداختِ خرید');
    ev.sort(function (a, b) { return a.g - b.g || a.e.at - b.e.at || (a.e.id < b.e.id ? -1 : a.e.id > b.e.id ? 1 : 0); });
    return ev.map(function (x) { return x.e; });
  }

  /* شیفتِ هسته از روی دفتر. رویدادی که هسته نپذیرد (مثلاً مصرفی بیش از نقدِ دخل)
     در `skipped` می‌آید و صفحه هشدار می‌دهد — بی‌صدا گم نمی‌شود. */
  function replay(L, open, now, opts) {
    opts = opts || {};
    var o = P.openShift({
      id: open.id, at: open.createdAt || 0, float: minor(open.amount), by: open.by || undefined,
      threshold: opts.threshold,
    });
    if (!o.ok) return o;
    var shift = o.shift, skipped = [];
    ledgerEvents(L, open, now).forEach(function (e) {
      var r = P.record(shift, e);
      if (r.ok) shift = r.shift; else skipped.push({ id: e.id, type: e.type, error: r.error, amount: e.amount || 0 });
    });
    return { ok: true, shift: shift, skipped: skipped };
  }

  /* گزارشِ X (یا Zِ ذخیره‌شدهٔ صندوقِ بسته) */
  function report(L, open, close, now, opts) {
    if (close) return close.z ? { ok: true, report: close.z, stored: true } : { ok: false, error: 'LEGACY_CLOSE' };
    var r = replay(L, open, now, opts);
    if (!r.ok) return r;
    var x = P.xReport(r.shift, { at: Math.max(now, open.createdAt || 0), by: (opts || {}).by });
    return x.ok ? { ok: true, report: x.report, skipped: r.skipped, shift: r.shift } : x;
  }

  /* نقدِ مورد انتظار (افغانی) برای سرخطِ صندوق */
  function expectedCash(L, open, now, opts) {
    var r = report(L, open, null, now, opts);
    return r.ok ? major(r.report.expected[CASH] || 0) : NaN;
  }

  /* ورود/خروجِ نقد و کشو بی فروش: هسته می‌سنجد، ردیفِ سابقه برمی‌گردد */
  function moveRow(L, open, m, now, newId, opts) {
    var r = replay(L, open, now, opts);
    if (!r.ok) return r;
    var id = newId(), type = m.type;
    var e = { id: 'a:' + id, type: type, at: now, by: text(m.by) || undefined };
    var row = { id: id, type: type, refId: open.id, createdAt: now, by: text(m.by) };
    if (type === 'no_sale') {
      e.reason = text(m.reason); e.approvedBy = text(m.approvedBy);
      row.reason = e.reason; row.approvedBy = e.approvedBy; row.amount = 0;
    } else {
      var a = amountOf(m.amount);
      if (a === null || a !== a || !(minor(a) > 0)) return { ok: false, error: 'INVALID_AMOUNT' };
      e.amount = minor(a); e.reason = m.reason; e.note = text(m.note) || undefined;
      row.amount = major(e.amount); row.reason = m.reason; if (e.note) row.note = e.note;
    }
    if (!row.by) delete row.by;
    var x = P.record(r.shift, e);
    return x.ok ? { ok: true, row: row } : { ok: false, error: x.error };
  }

  /* بستن با شمارشِ اسکناس (پول ⇒ تعداد). `blind` = شمارشِ کور؛ اختلافِ بالای آستانه بی دلیل بسته نمی‌شود */
  function closeRow(L, open, c, now, newId, opts) {
    var r = replay(L, open, now, opts);
    if (!r.ok) return r;
    var id = newId();
    var res = P.closeShift(r.shift, {
      id: id, at: now, counts: c.counts || {}, methodCounts: c.methodCounts || {},
      by: text(c.by) || undefined, blind: !!c.blind, reason: text(c.reason) || undefined,
    });
    if (!res.ok) return { ok: false, error: res.error, variance: res.variance };
    var z = JSON.parse(JSON.stringify(res.report));
    var row = {
      id: id, type: 'shift_close', refId: open.id, createdAt: now,
      amount: major(z.counted[CASH] || 0), z: z,
    };
    if (text(c.by)) row.by = text(c.by);
    if (z.reason) row.reason = z.reason;
    return { ok: true, row: row, report: z, skipped: r.skipped };
  }

  /* شمارِ اسکناس ⇒ جمع (پول) — برای جمعِ زندهٔ فرمِ شمارش */
  function countTotal(counts) {
    var s = 0;
    Object.keys(counts || {}).forEach(function (k) {
      var n = Number(counts[k]);
      if (isFinite(n) && n > 0) s += Number(k) * Math.floor(n);
    });
    return s;
  }

  return {
    CASH: CASH,
    ACCOUNT: ACCOUNT,
    minor: minor,
    major: major,
    method: method,
    methodName: methodName,
    panelMethods: panelMethods,
    message: message,
    preview: preview,
    exactCash: exactCash,
    saleFields: saleFields,
    checkout: checkout,
    saleRows: saleRows,
    ledgerEvents: ledgerEvents,
    replay: replay,
    report: report,
    expectedCash: expectedCash,
    moveRow: moveRow,
    closeRow: closeRow,
    countTotal: countTotal,
  };
});
