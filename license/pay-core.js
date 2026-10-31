/* ==========================================================
   فروشگاه من | هستهٔ پرداخت و صندوق — PAY-01..07، 09..16، 19، 23
   ----------------------------------------------------------
   هم‌زادِ `android-native/.../core/pay/{Payment,Shift}.kt`. هر دو با یک
   فایلِ بردارِ مرجع (`core/test-vectors/pay-v1.json`) سنجیده می‌شوند؛
   یکی را عوض کنی، دیگری هم.

   خالص است: نه DOM، نه ساعت، نه ذخیره. هر مبلغ عددِ صحیح به واحدِ کوچک
   (پول = یک‌صدمِ افغانی) و حساب با `license/money.js`.

   ── چه دارد ─────────────────────────────────────────────────────
   - `settle`: پرداختِ ترکیبی، دریافتی و باقی‌مانده، گردکردنِ نقدی (ردیفِ جدا)،
     پرداختِ جزئی به حسابِ مشتری. پول پس فقط از نقد داده می‌شود.
   - `exactCash`: «نقدِ دقیق» با یک کلید.
   - `exchange`: ارزِ دیگر فقط به‌صورتِ تبادل با نرخ، منبع، زمان و تأییدکننده.
   - `refund`: بازپرداخت به روشِ اصلی، نقد یا اعتبارِ فروشگاه، با مجوز.
   - شیفت: باز کردن با موجودی، ورود/خروجِ نقد با دلیل، بازکردنِ کشو بی فروش،
     گزارشِ X، بستن با شمارشِ اسکناس و شمارشِ هر روش (Z)، اختلاف با دلیل.
     اختلاف هرگز خودکار اصلاح نمی‌شود.

   هنوز هیچ صفحه‌ای آن را بار نمی‌کند؛ `shiftTotals` و فروشِ `index.html`
   همان حسابِ امروز را می‌کنند (گامِ بعدی، با پرچم).
   ========================================================== */
(function (factory) {
  'use strict';
  var M = (typeof module !== 'undefined' && module.exports)
    ? require('./money.js')
    : (typeof window !== 'undefined' ? window.TohidMoney : globalThis.TohidMoney);
  var api = factory(M);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidPay = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidPay = api;
})(function (M) {
  'use strict';

  if (!M) throw new Error('license/money.js باید پیش از pay-core.js بار شود');

  // ── ثابت‌ها ─────────────────────────────────────────────
  var ACCOUNT = 'account';            // روشِ رزرو: باقی‌ماندهٔ پرداختِ جزئی به حسابِ مشتری
  var KINDS = ['cash', 'wallet', 'card', 'transfer', 'store_credit', 'gift_card', 'other'];
  var COUNTABLE = { cash: 1, wallet: 1, card: 1, transfer: 1, other: 1 }; // در شمارشِ صندوق
  var NOTES = [1000, 2000, 5000, 10000, 50000, 100000];  // ۱۰ تا ۱۰۰۰ افغانی، به پول
  var COINS = [100, 200, 500];                           // سکهٔ ۱، ۲ و ۵ — فقط اگر روشن باشد
  var DEFAULT_VARIANCE_THRESHOLD = 5000;                 // ۵۰ افغانی (D-18)

  var STATUSES = ['pending', 'confirmed', 'failed', 'cancelled', 'needs_review', 'refunded', 'partially_refunded'];
  var TRANSITIONS = {
    pending: ['confirmed', 'failed', 'cancelled', 'needs_review'],
    needs_review: ['confirmed', 'failed', 'cancelled'],
    confirmed: ['partially_refunded', 'refunded'],
    partially_refunded: ['partially_refunded', 'refunded'],
    failed: [], cancelled: [], refunded: [],
  };
  var REFUNDABLE = { confirmed: 1, partially_refunded: 1 };

  var MOVE_REASONS = {
    cash_in: ['deposit', 'other'],
    cash_out: ['owner_withdrawal', 'petty_expense', 'other'],
  };

  function m(id, kind, name, needsRef, limit, enabled) {
    return Object.freeze({ id: id, kind: kind, name: name, needsRef: needsRef, limit: limit, enabled: enabled });
  }

  /* PAY-06 — جدولِ پیش‌فرض؛ هر دکان می‌تواند فهرستِ خودش را بدهد (`defineMethods`).
     اعتبارِ فروشگاه و کارتِ هدیه تا دفترشان (PAY-24) خاموش‌اند. سقفِ ATOMA Pay بخشِ ۴.۲. */
  var DEFAULT_METHODS = Object.freeze([
    m('cash', 'cash', 'نقد', false, null, true),
    m('hesabpay', 'wallet', 'HesabPay', true, null, true),
    m('e_money', 'wallet', 'e& money', true, null, true),
    m('my_money', 'wallet', 'My Money', true, null, true),
    m('atoma_pay', 'wallet', 'ATOMA Pay', true, 1500000, true),
    m('m_paisa', 'wallet', 'M-Paisa', true, null, true),
    m('afpay_card', 'card', 'کارت AfPay', true, null, true),
    m('hawala', 'transfer', 'حواله', true, null, true),
    m('store_credit', 'store_credit', 'اعتبار فروشگاه', false, null, false),
    m('gift_card', 'gift_card', 'کارت هدیه', true, null, false),
  ]);

  // ── کمک‌ها ──────────────────────────────────────────────
  function fail(code, extra) {
    var r = { ok: false, error: code };
    if (extra) for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) r[k] = extra[k];
    return r;
  }
  function isInt(v) { return typeof v === 'number' && Number.isSafeInteger(v); }
  function posInt(v) { return isInt(v) && v > 0; }
  function nonNeg(v) { return isInt(v) && v >= 0; }
  function text(v) { return typeof v === 'string' ? v.trim() : ''; }
  function exactMul(a, b) {
    var r = a * b;
    if (!Number.isSafeInteger(r)) throw new RangeError('ضرب بیرون از بازهٔ دقیق: ' + a + ' × ' + b);
    return r + 0;
  }
  function add(a, b) {
    var r = a + b;
    if (!Number.isSafeInteger(r)) throw new RangeError('جمع بیرون از بازهٔ دقیق: ' + a + ' + ' + b);
    return r + 0;
  }
  function has(o, k) { return Object.prototype.hasOwnProperty.call(o, k); }

  // ── روش‌های پرداخت (PAY-06) ────────────────────────────
  function defineMethods(list) {
    if (!Array.isArray(list)) return fail('BAD_METHOD', { index: -1 });
    var seen = {}, cash = 0, out = [];
    for (var i = 0; i < list.length; i++) {
      var x = list[i] || {};
      var id = text(x.id), name = text(x.name);
      var limit = x.limit === undefined ? null : x.limit;
      if (!id || id === ACCOUNT || has(seen, id) || KINDS.indexOf(x.kind) < 0 || !name ||
          (limit !== null && !posInt(limit))) return fail('BAD_METHOD', { index: i });
      if (x.kind === 'cash') cash++;
      seen[id] = 1;
      out.push(m(id, x.kind, name, x.kind === 'cash' ? false : !!x.needsRef, limit, x.enabled !== false));
    }
    if (cash !== 1) return fail('BAD_METHOD', { index: -1 });
    return { ok: true, methods: Object.freeze(out) };
  }

  function table(list) {
    var r = list === undefined || list === null ? { ok: true, methods: DEFAULT_METHODS } : defineMethods(list);
    if (!r.ok) return r;
    var by = {}, cashId = null, creditId = null;
    r.methods.forEach(function (x) {
      by[x.id] = x;
      if (x.kind === 'cash') cashId = x.id;
      if (x.kind === 'store_credit' && creditId === null) creditId = x.id;
    });
    return { ok: true, methods: r.methods, by: by, cashId: cashId, creditId: creditId };
  }

  function kindOf(t, id) { return id === ACCOUNT ? ACCOUNT : (t.by[id] ? t.by[id].kind : null); }

  // ── وضعیتِ پرداخت (PAY-07) ─────────────────────────────
  function canTransition(from, to) {
    return has(TRANSITIONS, from) && TRANSITIONS[from].indexOf(to) >= 0;
  }
  function transition(from, to) {
    if (STATUSES.indexOf(from) < 0 || STATUSES.indexOf(to) < 0) return fail('BAD_STATUS');
    return canTransition(from, to) ? { ok: true, status: to } : fail('BAD_TRANSITION');
  }

  // ── اسکناس (PAY-03) ────────────────────────────────────
  function denominations(coins) { return (coins ? COINS.concat(NOTES) : NOTES).slice(); }
  function isDenomination(v, coins) { return NOTES.indexOf(v) >= 0 || (!!coins && COINS.indexOf(v) >= 0); }

  // ── تسویه (PAY-01، 02، 04، 05، 09، 10) ─────────────────
  function analyse(input) {
    input = input || {};
    var cur;
    try { cur = M.currency(input.currency || 'AFN'); } catch (e) { return fail('UNKNOWN_CURRENCY'); }
    if (!nonNeg(input.total)) return fail('INVALID_AMOUNT');
    var step = input.cashRounding === undefined || input.cashRounding === null ? 0 : input.cashRounding;
    if (!nonNeg(step) || (step > 0 && step % cur.stepMinor !== 0)) return fail('INVALID_STEP');
    var t = table(input.methods);
    if (!t.ok) return t;
    var tenders = input.tenders || [];
    if (!Array.isArray(tenders)) return fail('INVALID_AMOUNT');

    var cash = 0, nonCash = 0, rows = [], cashAt = -1;
    for (var i = 0; i < tenders.length; i++) {
      var x = tenders[i] || {};
      var meth = has(t.by, x.method) ? t.by[x.method] : null;
      if (!meth) return fail('UNKNOWN_METHOD', { index: i });
      if (!meth.enabled) return fail('METHOD_DISABLED', { index: i });
      if (!posInt(x.amount)) return fail('INVALID_AMOUNT', { index: i });
      if ((x.currency || cur.code) !== cur.code) return fail('CURRENCY_MISMATCH', { index: i });
      var ref = text(x.ref);
      if (meth.needsRef && !ref) return fail('REF_REQUIRED', { index: i });
      if (meth.limit !== null && x.amount > meth.limit) return fail('METHOD_LIMIT', { index: i });
      if (meth.kind === 'cash') {
        cash = add(cash, x.amount);
        if (cashAt < 0) { cashAt = rows.length; rows.push(null); }
      } else {
        nonCash = add(nonCash, x.amount);
        rows.push({ method: meth.id, kind: meth.kind, amount: x.amount, ref: ref || null, status: 'pending', verification: 'manual' });
      }
    }
    // پول پس فقط از نقد: بیش‌پرداختِ کیف پول یا کارت پس داده نمی‌شود
    if (nonCash > input.total) return fail('NON_CASH_OVERPAY');

    var cashDue = input.total - nonCash;
    var rounded = step > 0 ? exactMul(M.roundHalfUp(cashDue, step), step) : cashDue;
    // گردکردنِ نقدی فقط وقتی نقد فاکتور را می‌بندد؛ هرگز بیش از مبلغِ دقیق از مشتری نمی‌خواهد
    var rounding = (cash > 0 && step > 0 && cash >= rounded) ? rounded - cashDue : 0;
    var due = add(cashDue, rounding);
    var change = cash >= due ? cash - due : 0;
    var kept = cash - change;
    var remaining = due - kept;

    return {
      ok: true, cur: cur, t: t, total: input.total, cash: cash, nonCash: nonCash, cashDue: cashDue, rounded: rounded,
      rounding: rounding, change: change, kept: kept, remaining: remaining, rows: rows, cashAt: cashAt,
    };
  }

  function settle(input) {
    var a = analyse(input);
    if (!a.ok) return a;
    var customer = text((input || {}).customerId);
    var toAccount = customer ? a.remaining : 0;
    var payments = [];
    a.rows.forEach(function (r) {
      if (r !== null) payments.push(r);
      else if (a.kept > 0) payments.push({ method: a.t.cashId, kind: 'cash', amount: a.kept, ref: null, status: 'confirmed', verification: null });
    });
    if (toAccount > 0) payments.push({ method: ACCOUNT, kind: ACCOUNT, amount: toAccount, ref: null, status: 'confirmed', verification: null });
    return {
      ok: true,
      currency: a.cur.code,
      status: a.remaining === 0 ? 'paid' : (customer ? 'on_account' : 'short'),
      total: a.total,
      rounding: a.rounding,
      due: add(a.total, a.rounding),
      tendered: add(a.cash, a.nonCash),
      change: a.change,
      remaining: a.remaining,
      toAccount: toAccount,
      payments: payments,
    };
  }

  /* PAY-04 — نقدی که با آن فاکتور دقیق بسته می‌شود (با گردکردن، اگر روشن باشد) */
  function exactCash(input) {
    var a = analyse(input);
    if (!a.ok) return a;
    return { ok: true, amount: a.remaining === 0 ? 0 : a.rounded - a.cash };
  }

  // ── تبادلِ ارز (PAY-10) ────────────────────────────────
  /* `rate` = چند واحدِ کوچکِ ارزِ مقصد برای یک واحدِ اصلیِ ارزِ مبدأ (۷۰٫۲۵ افغانی ⇐ 7025).
     مبلغِ مقصد به پلهٔ ارزِ مقصد گرد و در خودِ رکورد ثابت می‌شود؛ نرخِ بعدی آن را عوض نمی‌کند. */
  function exchange(op) {
    op = op || {};
    var from = op.from || {}, src, dst;
    try { src = M.currency(from.currency); dst = M.currency(op.to || 'AFN'); } catch (e) { return fail('UNKNOWN_CURRENCY'); }
    if (src.code === dst.code) return fail('EXCHANGE_SAME_CURRENCY');
    if (!posInt(from.amount)) return fail('INVALID_AMOUNT');
    if (!posInt(op.rate)) return fail('INVALID_RATE');
    var fields = { source: text(op.source), at: posInt(op.at), approvedBy: text(op.approvedBy) };
    for (var k in fields) if (!fields[k]) return fail('EXCHANGE_FIELDS', { field: k });
    var amount = M.roundHalfUp(exactMul(from.amount, op.rate), exactMul(src.minorPerMajor, dst.stepMinor));
    amount = exactMul(amount, dst.stepMinor);
    return {
      ok: true,
      record: {
        from: { currency: src.code, amount: from.amount },
        to: { currency: dst.code, amount: amount },
        rate: op.rate, source: fields.source, at: op.at, approvedBy: fields.approvedBy,
      },
    };
  }

  // ── بازپرداخت (PAY-23) ─────────────────────────────────
  function refund(input) {
    input = input || {};
    var approver = text(input.approvedBy);
    if (!approver) return fail('APPROVAL_REQUIRED');
    if (!posInt(input.amount)) return fail('INVALID_AMOUNT');
    var mode = input.mode;
    if (mode !== 'original' && mode !== 'cash' && mode !== 'store_credit') return fail('BAD_MODE');
    var cur;
    try { cur = M.currency(input.currency || 'AFN'); } catch (e) { return fail('UNKNOWN_CURRENCY'); }
    var t = table(input.methods);
    if (!t.ok) return t;
    if (mode === 'store_credit') {
      if (!text(input.customerId)) return fail('CUSTOMER_REQUIRED');
      if (t.creditId === null) return fail('UNKNOWN_METHOD', { index: -1 });
    }
    var pays = input.payments || [];
    var left = [], total = 0, i;
    for (i = 0; i < pays.length; i++) {
      var p = pays[i] || {};
      if (!kindOf(t, p.method)) return fail('UNKNOWN_METHOD', { index: i });
      var refunded = p.refunded === undefined ? 0 : p.refunded;
      if (!nonNeg(p.amount) || !nonNeg(refunded) || refunded > p.amount) return fail('INVALID_AMOUNT', { index: i });
      if (STATUSES.indexOf(p.status) < 0) return fail('BAD_STATUS', { index: i });
      left.push(has(REFUNDABLE, p.status) ? p.amount - refunded : 0);
      total = add(total, left[i]);
    }
    if (input.amount > total) return fail('REFUND_EXCEEDS', { refundable: total });
    var outstanding = input.accountOutstanding === undefined || input.accountOutstanding === null
      ? Infinity : input.accountOutstanding;
    if (outstanding !== Infinity && !nonNeg(outstanding)) return fail('INVALID_AMOUNT');

    // ترتیب: اول بدهیِ همین فاکتور (مثلِ امروز)، بعد بقیه از آخر به اول
    var order = [];
    for (i = 0; i < pays.length; i++) if (pays[i].method === ACCOUNT) order.push(i);
    for (i = pays.length - 1; i >= 0; i--) if (pays[i].method !== ACCOUNT) order.push(i);

    var fallback = mode === 'store_credit' ? t.creditId : t.cashId;
    var payouts = [], at = {}, rest = input.amount, used = pays.map(function () { return 0; });
    function pay(method, amount) {
      if (amount <= 0) return;
      if (has(at, method)) { payouts[at[method]].amount = add(payouts[at[method]].amount, amount); return; }
      at[method] = payouts.length;
      payouts.push({ method: method, kind: kindOf(t, method), amount: amount });
    }
    order.forEach(function (idx) {
      var take = Math.min(rest, left[idx]);
      if (take <= 0) return;
      used[idx] = take; rest -= take;
      if (pays[idx].method === ACCOUNT) {
        var debt = Math.min(take, outstanding);
        outstanding -= debt;
        pay(ACCOUNT, debt);
        pay(fallback, take - debt); // بدهیِ تسویه‌شده: پولش جدا آمده، پس نقد یا اعتبار
      } else {
        pay(mode === 'original' ? pays[idx].method : fallback, take);
      }
    });

    return {
      ok: true,
      currency: cur.code,
      amount: input.amount,
      approvedBy: approver,
      payouts: payouts,
      payments: pays.map(function (p, k) {
        var r = (p.refunded || 0) + used[k];
        return {
          method: p.method, amount: p.amount, refunded: r,
          status: used[k] > 0 ? (r === p.amount ? 'refunded' : 'partially_refunded') : p.status,
        };
      }),
    };
  }

  // ── شیفت (PAY-11..16، 19) ──────────────────────────────
  /* هر عملیاتِ شیفت شناسهٔ پایدار (op_id) دارد (بخشِ ۱۵.۲ بندِ ۱ و ۲۹.۵ بندِ ۲):
     تکرارِ همان عملیات با همان محتوا همان نتیجه را می‌دهد و اثرِ دوم ندارد
     (`duplicate: true`)؛ همان شناسه با محتوای دیگر `ID_CONFLICT` است. */
  function canon(v) {
    if (v === null || typeof v !== 'object') return JSON.stringify(v);
    if (Array.isArray(v)) return '[' + v.map(canon).join(',') + ']';
    return '{' + Object.keys(v).sort().map(function (k) { return JSON.stringify(k) + ':' + canon(v[k]); }).join(',') + '}';
  }

  function openShift(p) {
    p = p || {};
    var id = text(p.id);
    if (!id) return fail('ID_REQUIRED');
    var cur;
    try { cur = M.currency(p.currency || 'AFN'); } catch (e) { return fail('UNKNOWN_CURRENCY'); }
    if (!nonNeg(p.at)) return fail('INVALID_TIME');
    if (!nonNeg(p.float)) return fail('INVALID_AMOUNT');
    var threshold = p.threshold === undefined ? DEFAULT_VARIANCE_THRESHOLD : p.threshold;
    if (!nonNeg(threshold)) return fail('BAD_CONFIG');
    var t = table(p.methods);
    if (!t.ok) return t;
    return {
      ok: true,
      shift: Object.freeze({
        id: id, currency: cur.code, status: 'open', openedAt: p.at, openedBy: text(p.by) || null, float: p.float,
        threshold: threshold, coins: !!p.coins, methods: t.methods, events: Object.freeze([]),
        closedAt: null, closeOp: null, z: null,
      }),
    };
  }

  function withEvent(s, e) {
    var copy = {};
    for (var k in s) if (has(s, k)) copy[k] = s[k];
    copy.events = Object.freeze(s.events.concat([Object.freeze(e)]));
    return Object.freeze(copy);
  }

  function rowsOf(t, list, allowAccount) {
    if (!Array.isArray(list) || !list.length) return fail('BAD_EVENT');
    var out = [];
    for (var i = 0; i < list.length; i++) {
      var r = list[i] || {};
      var kind = kindOf(t, r.method);
      if (!kind || (!allowAccount && kind === ACCOUNT)) return fail('UNKNOWN_METHOD', { index: i });
      if (!posInt(r.amount)) return fail('INVALID_AMOUNT', { index: i });
      out.push({ method: r.method, amount: r.amount });
    }
    return { ok: true, rows: out };
  }

  /* سنجش و یک‌دست‌کردنِ رویداد، بی نگاه به حالتِ شیفت */
  function buildEvent(s, t, e) {
    // PAY-10: شیفت یک ارز دارد؛ رویدادِ ارزِ دیگر پذیرفته نمی‌شود
    if ((e.currency || s.currency) !== s.currency) return fail('CURRENCY_MISMATCH');
    var r, ev = { id: text(e.id), type: e.type, at: e.at };
    switch (e.type) {
      case 'sale':
      case 'refund':
      case 'receipt':
        r = e.type === 'receipt' ? rowsOf(t, [{ method: e.method, amount: e.amount }], false) : rowsOf(t, e.rows, true);
        if (!r.ok) return r;
        ev.rows = r.rows;
        return { ok: true, event: ev };
      case 'cash_in':
      case 'cash_out': {
        if (!posInt(e.amount)) return fail('INVALID_AMOUNT');
        if (!e.reason) return fail('REASON_REQUIRED');
        if (MOVE_REASONS[e.type].indexOf(e.reason) < 0) return fail('BAD_REASON');
        var note = text(e.note);
        if (e.reason === 'other' && !note) return fail('NOTE_REQUIRED');
        ev.amount = e.amount; ev.reason = e.reason; ev.note = note || null; ev.by = text(e.by) || null;
        return { ok: true, event: ev };
      }
      case 'no_sale': {
        var reason = text(e.reason), approver = text(e.approvedBy);
        if (!reason) return fail('REASON_REQUIRED');
        if (!approver) return fail('APPROVAL_REQUIRED');
        ev.reason = reason; ev.by = text(e.by) || null; ev.approvedBy = approver;
        return { ok: true, event: ev };
      }
      default:
        return fail('BAD_EVENT');
    }
  }

  function record(s, e) {
    if (!s) return fail('SHIFT_CLOSED');
    e = e || {};
    if (!text(e.id)) return fail('ID_REQUIRED');
    var t = table(s.methods), b, i;
    for (i = 0; i < s.events.length; i++) {
      if (s.events[i].id !== text(e.id)) continue;
      b = buildEvent(s, t, e);
      if (!b.ok) return b;
      return canon(b.event) === canon(s.events[i]) ? { ok: true, shift: s, duplicate: true } : fail('ID_CONFLICT');
    }
    if (s.status !== 'open') return fail('SHIFT_CLOSED');
    if (!nonNeg(e.at) || e.at < s.openedAt) return fail('INVALID_TIME');
    b = buildEvent(s, t, e);
    if (!b.ok) return b;
    if (b.event.type === 'cash_out' && b.event.amount > totals(s).expected[t.cashId]) return fail('INSUFFICIENT_CASH');
    return { ok: true, shift: withEvent(s, b.event) };
  }

  function totals(s) {
    var t = table(s.methods);
    var methods = {}, moves = { in: 0, out: 0 };
    var events = { sales: 0, refunds: 0, receipts: 0, cashIn: 0, cashOut: 0, noSale: 0 };
    function bucket(id) {
      if (!has(methods, id)) methods[id] = { sales: 0, refunds: 0, receipts: 0, net: 0 };
      return methods[id];
    }
    s.events.forEach(function (e) {
      if (e.type === 'sale' || e.type === 'refund' || e.type === 'receipt') {
        events[e.type === 'sale' ? 'sales' : e.type === 'refund' ? 'refunds' : 'receipts']++;
        e.rows.forEach(function (r) {
          var b = bucket(r.method);
          if (e.type === 'sale') { b.sales = add(b.sales, r.amount); b.net = add(b.net, r.amount); }
          else if (e.type === 'receipt') { b.receipts = add(b.receipts, r.amount); b.net = add(b.net, r.amount); }
          else { b.refunds = add(b.refunds, r.amount); b.net = add(b.net, -r.amount); }
        });
      } else if (e.type === 'cash_in') { moves.in = add(moves.in, e.amount); events.cashIn++; }
      else if (e.type === 'cash_out') { moves.out = add(moves.out, e.amount); events.cashOut++; }
      else if (e.type === 'no_sale') events.noSale++;
    });
    var expected = {};
    expected[t.cashId] = add(add(s.float, has(methods, t.cashId) ? methods[t.cashId].net : 0), moves.in - moves.out);
    Object.keys(methods).forEach(function (id) {
      var kind = kindOf(t, id);
      if (id !== t.cashId && has(COUNTABLE, kind)) expected[id] = methods[id].net;
    });
    return { methods: methods, moves: moves, events: events, expected: expected, t: t };
  }

  function reportOf(s, kind, at, by) {
    var x = totals(s);
    return {
      kind: kind, shiftId: s.id, currency: s.currency, at: at, by: text(by) || null, openedAt: s.openedAt, openedBy: s.openedBy, float: s.float,
      methods: x.methods, moves: x.moves, expected: x.expected, events: x.events,
    };
  }

  /* PAY-15 — گزارشِ X: عکسِ میانِ شیفت؛ چیزی را نمی‌بندد و هر چند بار گرفتنی است */
  function xReport(s, p) {
    p = p || {};
    if (!s || s.status !== 'open') return fail('SHIFT_CLOSED');
    if (!nonNeg(p.at) || p.at < s.openedAt) return fail('INVALID_TIME');
    return { ok: true, report: reportOf(s, 'X', p.at, p.by) };
  }

  /* سنجش و یک‌دست‌کردنِ ورودیِ بستن، بی نگاه به حالتِ شیفت */
  function buildClose(s, c, id) {
    var t = table(s.methods);
    var counts = c.counts || {}, denoms = {}, cash = 0;
    var keys = Object.keys(counts), k, i;
    for (i = 0; i < keys.length; i++) {
      k = keys[i];
      var d = /^[1-9][0-9]*$/.test(k) ? Number(k) : NaN;
      if (!isDenomination(d, s.coins)) return fail('BAD_DENOMINATION', { denomination: k });
      if (!nonNeg(counts[k])) return fail('INVALID_COUNT');
      if (counts[k] > 0) { denoms[k] = counts[k]; cash = add(cash, exactMul(d, counts[k])); }
    }
    var mc = c.methodCounts || {}, methodCounts = {};
    keys = Object.keys(mc);
    for (i = 0; i < keys.length; i++) {
      k = keys[i];
      var kind = kindOf(t, k);
      if (!kind || k === t.cashId || !has(COUNTABLE, kind)) return fail('UNKNOWN_METHOD', { method: k });
      if (!nonNeg(mc[k])) return fail('INVALID_COUNT');
      methodCounts[k] = mc[k];
    }
    return {
      ok: true, t: t, cash: cash,
      op: { id: id, at: c.at, by: text(c.by) || null, counts: denoms, methodCounts: methodCounts, blind: !!c.blind, reason: text(c.reason) || null },
    };
  }

  /* PAY-13، 14، 16 — بستن با شمارشِ اسکناس و شمارشِ هر روش؛ اختلاف ثبت می‌شود، اصلاح نه */
  function closeShift(s, c) {
    c = c || {};
    if (!s) return fail('SHIFT_CLOSED');
    var id = text(c.id);
    if (!id) return fail('ID_REQUIRED');
    var b;
    if (s.status !== 'open') {
      if (!s.closeOp || s.closeOp.id !== id) return fail('SHIFT_CLOSED');
      b = buildClose(s, c, id);
      if (!b.ok) return b;
      return canon(b.op) === canon(s.closeOp) ? { ok: true, shift: s, report: s.z, duplicate: true } : fail('ID_CONFLICT');
    }
    if (!nonNeg(c.at) || c.at < s.openedAt) return fail('INVALID_TIME');
    b = buildClose(s, c, id);
    if (!b.ok) return b;
    var op = b.op, counted = {};
    counted[b.t.cashId] = b.cash;
    Object.keys(op.methodCounts).forEach(function (m) { counted[m] = op.methodCounts[m]; });
    var rep = reportOf(s, 'Z', op.at, op.by);
    var variance = {}, over = false, review = false;
    Object.keys(rep.expected).concat(Object.keys(counted)).forEach(function (m) {
      if (has(variance, m)) return;
      if (!has(counted, m)) { variance[m] = null; return; }
      var v = add(counted[m], -(has(rep.expected, m) ? rep.expected[m] : 0));
      variance[m] = v;
      if (v !== 0) review = true;
      if (Math.abs(v) > s.threshold) over = true;
    });
    /* ⛔ شمارشِ کور هرگز به‌خاطرِ اختلاف رد نمی‌شود (ردِ «بالای آستانه» پیشگوی موردِ انتظار بود)؛ needsReview برای مدیر */
    if (over && !op.reason && !op.blind) return fail('REASON_REQUIRED', { variance: variance });

    rep.counted = counted;
    rep.denominations = op.counts;
    rep.variance = variance;
    rep.reason = op.reason;
    rep.needsReview = review;
    rep.blind = op.blind;
    var copy = {};
    for (var key in s) if (has(s, key)) copy[key] = s[key];
    copy.status = 'closed';
    copy.closedAt = op.at;
    copy.closeOp = Object.freeze(op);
    copy.z = Object.freeze(rep);
    return { ok: true, shift: Object.freeze(copy), report: rep };
  }

  function zReport(s) {
    if (!s || s.status !== 'closed') return fail('SHIFT_OPEN');
    return { ok: true, report: s.z };
  }

  /* شمارشِ کور: صندوقدار نه مبلغِ مورد انتظار را می‌بیند نه اختلاف را */
  function cashierView(report) {
    var out = {};
    for (var k in report) if (has(report, k)) out[k] = report[k];
    if (report.blind) { delete out.expected; delete out.variance; }
    return out;
  }

  return {
    ACCOUNT: ACCOUNT,
    KINDS: KINDS,
    NOTES: NOTES,
    COINS: COINS,
    STATUSES: STATUSES,
    MOVE_REASONS: MOVE_REASONS,
    DEFAULT_METHODS: DEFAULT_METHODS,
    DEFAULT_VARIANCE_THRESHOLD: DEFAULT_VARIANCE_THRESHOLD,
    defineMethods: defineMethods,
    canTransition: canTransition,
    transition: transition,
    denominations: denominations,
    isDenomination: isDenomination,
    settle: settle,
    exactCash: exactCash,
    exchange: exchange,
    refund: refund,
    openShift: openShift,
    record: record,
    xReport: xReport,
    closeShift: closeShift,
    zReport: zReport,
    cashierView: cashierView,
  };
});
