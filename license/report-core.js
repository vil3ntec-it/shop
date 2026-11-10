/* ==========================================================
   فروشگاه من | هستهٔ گزارش (report-core) — RPT-01/02/03/06/09/11
   ----------------------------------------------------------
   خالص: نه DOM، نه fetch، نه localStorage، نه ساعتِ دستگاه. ورودی همان
   دفترِ وب است (`tohid-shop-data-v1`: sales، saleItems، returns، …) و
   خروجی فقط عددِ صحیح.

   ── قاعده‌ها ────────────────────────────────────────────────────
   • پول: عددِ صحیحِ واحدِ کوچک (۱ افغانی = ۱۰۰). مقدار: هزارم (milli).
     تبدیل از دفتر یک بار، با Math.round — همان کارِ Kotlin.
   • درصد: «بیسیس‌پوینت» (۱۰۰ = ۱٪)، گرد به نزدیک‌ترین، نیمه رو به بالا.
     null یعنی مخرج صفر یا منفی بود.
   • روزِ کاری (بخشِ ۳.۲ ـ زمان): رویدادِ صندوق (فاکتور، برگشتی، سابقه)
     با `clock.date(createdAt)` روزش معلوم می‌شود — روزی که از نیمه‌شب
     رد شود مالِ روزِ شروعش است. بی `clock` همان `date`ِ ذخیره‌شده
     (رفتارِ امروزِ ReportEngine). سندِ دفتری (قرض، خرید، پرداخت) همیشه
     `date`ِ خودش را دارد، چون کاربر انتخابش کرده.
   • برگشتی در روزِ **خودش** اثر می‌کند، نه روزِ فاکتور — روزِ بسته‌شده
     دیگر عوض نمی‌شود (همان قاعدهٔ ReportEngine.sales).
   • همان آزمون‌های مرجع (`core/test-vectors/report-v1.json`) روی
     `core/report/ReportCore.kt` هم می‌دوند؛ هر دو باید عیناً برابر باشند.
   ========================================================== */
(function (factory) {
  'use strict';
  //  تقویم و روزِ کاری از `calendar-core.js` (همان `core/calendar`ِ Kotlin)؛ در مرورگر پیش از این فایل بار شود
  var cal = typeof module !== 'undefined' && module.exports ? require('./calendar-core.js')
    : (typeof window !== 'undefined' ? window.TohidCalendar : globalThis.TohidCalendar);
  if (!cal || typeof cal.businessDateAt !== 'function') throw new Error('report-core: calendar-core.js (TohidCalendar) must load first');
  //  میانگینِ موزون (RPT-03، PRD-15) با همان هستهٔ موجودی (`stock-core.js` = `core/stock/StockCore`)؛ در مرورگر پیش از این فایل
  var stock = typeof module !== 'undefined' && module.exports ? require('./stock-core.js')
    : (typeof window !== 'undefined' ? window.TohidStockCore : globalThis.TohidStockCore);
  if (!stock || typeof stock.reportReplay !== 'function') throw new Error('report-core: stock-core.js (TohidStockCore) must load first');
  var api = factory(cal, stock);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidReportCore = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidReportCore = api;
})(function (cal, stock) {
  'use strict';

  var VERSION = 1;
  var DAY_MS = 86400000;
  var DIMS = ['product', 'category', 'user', 'hour', 'payment', 'channel'];

  /* ---------------- عدد ---------------- */

  /** گردِ معمولی بی «صفرِ منفی» */
  function rnd(x) { var r = Math.round(x); return r === 0 ? 0 : r; }

  /** تقسیمِ کف برای صحیح‌ها (b > 0)، دقیق حتی وقتی تقسیمِ اعشاری لب‌به‌لب است */
  function floorDiv(a, b) {
    var q = Math.floor(a / b);
    var r = a - q * b;
    while (r < 0) { q -= 1; r += b; }
    while (r >= b) { q += 1; r -= b; }
    return q === 0 ? 0 : q;
  }

  /** a/b گرد به نزدیک‌ترین، نیمه رو به +∞ (b > 0) */
  function divRound(a, b) { return floorDiv(2 * a + b, 2 * b); }

  /** نسبت به بیسیس‌پوینت؛ null وقتی مخرج ≤ ۰ */
  function bp(num, den) { return den > 0 ? divRound(num * 10000, den) : null; }

  function num(x) {
    var n = typeof x === 'number' ? x : (typeof x === 'string' && x !== '' ? Number(x) : 0);
    return isFinite(n) ? n : 0;
  }
  function money(x) { return rnd(num(x) * 100); }
  function ms(x) { var n = Math.floor(num(x)); return n === 0 ? 0 : n; }
  function milli(x) { return rnd(num(x) * 1000); }
  /*  PRD-07: `costMinor` فقط وقتی با مقدار × بهای همان ردیف جور است (نیم‌صدم برای هر واحد + ۱)؛ نسخهٔ پیش از دستهٔ ۳۱ با
   *  ویرایشِ بها فقط `price` را می‌فرستد ⇒ ناجور ⇒ null (همتای `ReportLedger.exactValue`، DATA-SAFETY §۷) */
  function exactValue(c, qtyMilli, unitCost) {
    if (c === null) return null;
    return Math.abs(c - qtyMilli * unitCost / 1000) <= Math.abs(qtyMilli) / 2000 + 1 ? c : null;
  }
  function str(x) { return x === undefined || x === null ? '' : String(x); }
  function arr(x) { return Object.prototype.toString.call(x) === '[object Array]' ? x : []; }
  function cmp(a, b) { return a < b ? -1 : (a > b ? 1 : 0); }

  /* ---------------- تاریخ ---------------- */

  function pad(n, w) { var s = String(n); while (s.length < w) s = '0' + s; return s; }

  /** شمارهٔ روز از ۱۹۷۰ ⇐ `yyyy-MM-dd` (روی calendar-core)؛ null برای تاریخِ نامعتبر، مثلاً ۰۲-۳۰ */
  function epochDay(iso) { var j = cal.jdnOfIso(iso); return j === null ? null : j - 2440588; }

  function addDays(iso, k) { var d = cal.addDays(iso, k); return d === null ? '' : d; }

  /**
   * ساعتِ فروشگاه با اختلافِ ثابت از UTC (کابل +۲۷۰ دقیقه، بی ساعتِ تابستانی) و شروعِ
   * روزِ کاری (دقیقه از نیمه‌شب). روزِ کاری همان `businessDateAt`ِ calendar-core است.
   */
  function fixedClock(offsetMinutes, dayStartMinute) {
    var offMin = num(offsetMinutes), startMin = num(dayStartMinute), off = offMin * 60000;
    return {
      date: function (ms) { return cal.businessDateAt(ms, offMin, startMin); },
      hour: function (ms) { return Math.floor((ms + off - floorDiv(ms + off, DAY_MS) * DAY_MS) / 3600000); },
    };
  }

  function clockOf(spec) {
    if (!spec) return null;
    if (typeof spec.date === 'function') return spec;
    return fixedClock(spec.offsetMinutes, spec.dayStartMinute);
  }

  function posDate(rec, clock) { return clock && rec.createdAt > 0 ? clock.date(rec.createdAt) : rec.date; }
  function posHour(rec, clock) { return clock && rec.createdAt > 0 ? pad(clock.hour(rec.createdAt), 2) : ''; }
  function docDate(rec, clock) { return rec.date !== '' ? rec.date : (clock && rec.createdAt > 0 ? clock.date(rec.createdAt) : ''); }
  /** زمانِ مرتب‌سازی: createdAt، یا آغازِ روزِ `date` برای ردیفِ قدیمی */
  function timeOf(rec) {
    if (rec.createdAt > 0) return rec.createdAt;
    var n = epochDay(rec.date);
    return n === null ? 0 : n * DAY_MS;
  }

  /* ---------------- دفتر ⇐ شکلِ عددِ صحیح ---------------- */

  function landedOf(e) {
    var m = e.landedCostMinor;
    if (typeof m === 'number' && Math.floor(m) === m && m >= 0 && m <= 9007199254740991) return m;
    var x = money(e.landedCost);
    return x > 0 ? x : 0;
  }

  function ledger(raw) {
    if (raw && raw.__reportLedger === VERSION) return raw;
    raw = raw || {};
    var L = {
      __reportLedger: VERSION,
      products: {}, sales: [], saleById: {}, items: [], itemById: {}, itemsBySale: {},
      returns: [], debtors: {}, transactions: [], suppliers: {}, purchases: [],
      supplierPayments: [], entries: [], audit: [],
    };
    arr(raw.products).forEach(function (p) {
      L.products[str(p.id)] = { id: str(p.id), name: str(p.name), category: str(p.category).trim() };
    });
    arr(raw.sales).forEach(function (s) {
      var x = {
        id: str(s.id), total: money(s.total), discount: money(s.discount), finalTotal: money(s.finalTotal),
        method: str(s.paymentMethod), status: str(s.status), cancelled: str(s.status) === 'cancelled',
        seller: str(s.seller).trim(), cancelledBy: str(s.cancelledBy).trim(), approvedBy: str(s.approvedBy).trim(),
        channel: str(s.channel) || 'pos', date: str(s.date), createdAt: ms(s.createdAt),
      };
      L.sales.push(x); L.saleById[x.id] = x;
    });
    arr(raw.saleItems).forEach(function (i) {
      var x = {
        id: str(i.id), saleId: str(i.saleId), productId: str(i.productId), name: str(i.name),
        qty: milli(i.quantity), unitCost: money(i.purchasePrice), total: money(i.totalPrice),
        //  POS-23 — فروشندهٔ ردیف؛ خالی ⇒ فروشندهٔ فاکتور. فقط با `opts.lineSeller` دیده می‌شود
        seller: str(i.seller).trim(),
      };
      L.items.push(x); L.itemById[x.id] = x;
      (L.itemsBySale[x.saleId] = L.itemsBySale[x.saleId] || []).push(x);
    });
    arr(raw.returns).forEach(function (r) {
      L.returns.push({
        id: str(r.id), saleId: str(r.saleId), saleItemId: str(r.saleItemId), productId: str(r.productId),
        //  `by` همان فیلدِ `SaleReturn` ِ برنامه (جریانِ تازهٔ برگشت و «سابقهٔ ضدتقلب»)؛ `user` شکلِ دفترِ وب
        qty: milli(r.quantity), amount: money(r.amount), user: (str(r.by) || str(r.user)).trim(), approvedBy: str(r.approvedBy).trim(),
        date: str(r.date), createdAt: ms(r.createdAt),
      });
    });
    arr(raw.debtors).forEach(function (d) { L.debtors[str(d.id)] = str(d.name); });
    arr(raw.transactions).forEach(function (t) {
      L.transactions.push({
        id: str(t.id), debtorId: str(t.debtorId), give: str(t.type) === 'give', amount: money(t.amount),
        date: str(t.date), createdAt: ms(t.createdAt),
      });
    });
    arr(raw.suppliers).forEach(function (s) { L.suppliers[str(s.id)] = str(s.name); });
    arr(raw.purchases).forEach(function (p) {
      L.purchases.push({
        id: str(p.id), supplierId: str(p.supplierId), total: money(p.totalAmount), paid: money(p.paidAmount),
        date: str(p.date), createdAt: ms(p.createdAt),
      });
    });
    arr(raw.supplierPayments).forEach(function (p) {
      L.supplierPayments.push({
        id: str(p.id), supplierId: str(p.supplierId), amount: money(p.amount),
        date: str(p.date), createdAt: ms(p.createdAt),
      });
    });
    arr(raw.warehouseEntries).forEach(function (e) {
      L.entries.push({
        id: str(e.id), productId: str(e.productId), qty: milli(e.units), unitCost: money(e.price),
        //  هزینهٔ جانبیِ کلِ ردیف (PUR-06): `landedCostMinor` ِ دقیق، وگرنه `landedCost` ِ افغانی؛ منفی یا نادرست ⇒ ۰
        landed: landedOf(e),
        adjustment: e.isAdjustment === true, date: str(e.date), createdAt: ms(e.createdAt),
        //  ارزشِ دقیقِ ورود (`costMinor`) فقط جایی که StockLedger می‌خواند: خرید (PRD-07) و ردیفِ تولید (INV-21، `adjKind` = repack)؛
        //  نبود یا ناجور با مقدار × بها (ویرایشِ بها روی نسخهٔ کهنه‌تر) ⇒ null = همان پیشین (همتای `ReportLedger.exactValue`)
        value: e.isAdjustment !== true || str(e.adjKind) === 'repack' ? exactValue(Number.isSafeInteger(e.costMinor) ? e.costMinor : null, milli(e.units), money(e.price)) : null,
      });
    });
    arr(raw.auditLog).forEach(function (a) {
      L.audit.push({
        //  `by`/`at`/`approvedBy` همان شکلِ رویدادِ صندوقِ pay-core (#237) است؛ `user`/`createdAt` شکلِ سابقهٔ امروز
        id: str(a.id), type: str(a.type), refId: str(a.refId), user: (str(a.by) || str(a.user)).trim(),
        approvedBy: str(a.approvedBy).trim(), amount: money(a.amount), date: str(a.date),
        createdAt: ms(a.createdAt) || ms(a.at),
      });
    });
    return L;
  }

  /* ---------------- بهای تمام‌شده (RPT-03، PRD-15) ---------------- */

  /*  میانگینِ موزونِ متحرک (D-23، الف): بازپخشِ همین دفتر روی هستهٔ موجودی — `stock-core.js` ‹`reportReplay`›
   *  (شرحِ کامل همان‌جا؛ دوقلو: `ReportCore.mavg`). */
  var STOCK_WH = stock.REPORT_WH;

  /** round(a × b ÷ c) ِ هستهٔ موجودی؛ عددِ نا‌امن (بیرون از ۲^۵۳) ⇒ null، نه خطا (Kotlin: `mdr`) */
  function mdr(a, b, c) {
    try { return stock.mulDivRound(a, b, c); } catch (e) { if (e instanceof RangeError) return null; throw e; }
  }

  function mavgReplay(L) { return stock.reportReplay(L, timeOf); }

  /**
   * بهای هر قلم و برگشتی (واحدِ کوچک): 'line' = `purchasePrice` ِ قلم × مقدار (همیشه «تقریبی»)، 'mavg' = بالا.
   * `est[itemId]` = بها نامعلوم یا تخمینی؛ `approved` = همین روش مصوبِ D-23 است (`CostPolicy`)؛ 'line' هرگز قطعی نیست.
   */
  function costsOf(L, mode, approved) {
    if (mode === 'mavg') {
      var m = mavgReplay(L);
      return { mode: 'mavg', approved: approved === true, line: m.line, ret: m.ret, est: m.est };
    }
    var line = {}, ret = {}, est = {};
    L.items.forEach(function (it) { if (it.unitCost === 0) est[it.id] = true; line[it.id] = rnd(it.unitCost * it.qty / 1000); });
    L.returns.forEach(function (r) {
      var it = L.itemById[r.saleItemId];
      ret[r.id] = it ? rnd(it.unitCost * r.qty / 1000) : 0;
    });
    return { mode: 'line', approved: false, line: line, ret: ret, est: est };
  }

  /**
   * PRD-15 — بهای میانگینِ هر کالا و ارزشِ موجودیِ همین حالا از همان بازپخش (فقط خواندنی). `unitCostMinor` = بهای یک
   * واحد (موجودیِ صفر/منفی ⇒ آخرین بهای معلوم؛ هیچ ⇒ null)؛ `approximate` = موجودیِ صفر/منفی یا پایهٔ نامعلوم؛ جمع فقط
   * کالاهای مثبت (`negative` = شمارِ منفی‌ها). `opts.products` فقط همان کالاها.
   */
  function stockCost(raw, opts) {
    opts = opts || {};
    var L = ledger(raw), m = mavgReplay(L);
    var want = null;
    if (arr(opts.products).length) { want = {}; arr(opts.products).forEach(function (id) { want[str(id)] = true; }); }
    var rows = [], value = 0, negative = 0, approx = false;
    Object.keys(m.books).sort(cmp).forEach(function (pid) {
      if (want && !want[pid]) return;
      var p = stock.position(m.books[pid], pid, STOCK_WH);
      if (!p) return;
      var unit = p.qtyMilli > 0 ? mdr(p.valueMinor, 1000, p.qtyMilli)
        : (p.lastCost ? mdr(p.lastCost.num, 1000, p.lastCost.den) : null);
      var a = p.qtyMilli <= 0 || m.unknown[pid] === true;
      var prod = L.products[pid];
      rows.push({ productId: pid, name: prod ? prod.name : '', qtyMilli: p.qtyMilli, valueMinor: p.valueMinor, unitCostMinor: unit, approximate: a });
      if (p.qtyMilli > 0) { value += p.valueMinor; if (a) approx = true; }
      else if (p.qtyMilli < 0) negative += 1;
    });
    return { rows: rows, totals: { products: rows.length, valueMinor: value, negative: negative, approximate: approx } };
  }

  /* ---------------- تخصیصِ تخفیفِ فاکتور ---------------- */

  /** «بزرگ‌ترین باقی‌مانده» با پلهٔ یک واحدِ کوچک: جمعِ سهم‌ها دقیقاً D */
  function allocate(D, weights) {
    var n = weights.length, out = [], i;
    for (i = 0; i < n; i++) out.push(0);
    if (D === 0 || n === 0) return out;
    var sign = D < 0 ? -1 : 1, A = D * sign, W = 0;
    for (i = 0; i < n; i++) W += weights[i] > 0 ? weights[i] : 0;
    if (W <= 0) { out[0] = D; return out; }
    var frac = [], given = 0;
    for (i = 0; i < n; i++) {
      var w = weights[i] > 0 ? weights[i] : 0;
      var exact = A * w / W, base = Math.floor(exact);
      out[i] = base; given += base; frac.push({ i: i, f: exact - base });
    }
    frac.sort(function (a, b) { return (b.f - a.f) || (a.i - b.i); });
    var rem = A - given, k;
    for (k = 0; rem > 0; k++, rem--) out[frac[k % n].i] += 1;
    //  فقط در عددهای بسیار بزرگ (بالای ۲^۵۳) ممکن است کفِ اعشاری یکی زیادی بدهد
    for (k = n - 1; rem < 0; k = (k + n - 1) % n) if (out[frac[k].i] > 0) { out[frac[k].i] -= 1; rem++; }
    for (i = 0; i < n; i++) out[i] = out[i] * sign === 0 ? 0 : out[i] * sign;
    return out;
  }

  /* ---------------- فروش (RPT-02 + RPT-03) ---------------- */

  function blankRow(key, name) {
    return { key: key, name: name, _inv: {}, invoices: 0, qty: 0, gross: 0, discount: 0, sales: 0,
      returns: 0, netSales: 0, cogs: 0, grossProfit: 0, marginBp: null };
  }

  function finishRow(r) {
    var out = { key: r.key, name: r.name, invoices: 0, qty: r.qty, gross: r.gross, discount: r.gross - r.sales,
      sales: r.sales, returns: r.returns, netSales: r.sales - r.returns, cogs: r.cogs, grossProfit: 0, marginBp: null };
    for (var k in r._inv) if (Object.prototype.hasOwnProperty.call(r._inv, k)) out.invoices++;
    out.grossProfit = out.netSales - out.cogs;
    out.marginBp = bp(out.grossProfit, out.netSales);
    return out;
  }

  /**
   * POS-23 — «کاربر» ِ یک ردیف: با `lineSeller` (تنظیمِ خاموشِ دکان `line_seller`) فروشندهٔ ردیف و نبودش فروشندهٔ
   * فاکتور؛ بی آن همیشه فروشندهٔ فاکتور (همان پیشین). همان `userOf` ِ `ReportCore.kt`.
   */
  function userOf(item, sale, lineSeller) {
    if (lineSeller && item && item.seller !== '') return item.seller;
    return sale ? sale.seller : '';
  }

  function aggregate(L, from, to, clock, costs, withDims, lineSeller) {
    var T = { invoices: 0, returnCount: 0, gross: 0, discount: 0, sales: 0, returns: 0, cogs: 0, est: 0 };
    var acc = {};
    DIMS.forEach(function (d) { acc[d] = {}; });
    function productName(pid, fallback) { var p = L.products[pid]; return p && p.name !== '' ? p.name : fallback; }
    function categoryOf(pid) { var p = L.products[pid]; return p ? p.category : ''; }
    function row(dim, key, name) { return acc[dim][key] || (acc[dim][key] = blankRow(key, name)); }
    function keysFor(pid, itemName, sale, hour, item) {
      var user = userOf(item, sale, lineSeller === true);
      return {
        product: [pid, pid === '' ? '' : productName(pid, itemName)],
        category: [categoryOf(pid), categoryOf(pid)],
        user: [user, user],
        hour: [hour, hour],
        payment: [sale ? sale.method : '', sale ? sale.method : ''],
        channel: [sale ? sale.channel : '', sale ? sale.channel : ''],
      };
    }
    L.sales.forEach(function (s) {
      if (s.cancelled) return;
      var d = posDate(s, clock);
      if (!(d >= from && d <= to)) return;
      T.invoices++; T.gross += s.total; T.discount += s.discount; T.sales += s.finalTotal;
      var items = L.itemsBySale[s.id] || [];
      var lines = items.map(function (it) {
        return { pid: it.productId, name: it.name, qty: it.qty, gross: it.total, cost: costs.line[it.id], est: costs.est[it.id] === true, item: it };
      });
      if (!lines.length) lines.push({ pid: '', name: '', qty: 0, gross: s.total, cost: 0, est: true });
      var sum = 0;
      lines.forEach(function (ln) { sum += ln.gross; });
      var shares = allocate(sum - s.finalTotal, lines.map(function (ln) { return ln.gross; }));
      var hour = posHour(s, clock);
      lines.forEach(function (ln, i) {
        T.cogs += ln.cost;
        if (ln.est) T.est++;
        if (!withDims) return;
        var keys = keysFor(ln.pid, ln.name, s, hour, ln.item || null);
        DIMS.forEach(function (dim) {
          var r = row(dim, keys[dim][0], keys[dim][1]);
          r._inv[s.id] = true; r.qty += ln.qty; r.gross += ln.gross; r.sales += ln.gross - shares[i]; r.cogs += ln.cost;
        });
      });
    });
    L.returns.forEach(function (r) {
      var s = L.saleById[r.saleId] || null;
      if (s && s.cancelled) return;
      var d = posDate(r, clock);
      if (!(d >= from && d <= to)) return;
      var c = costs.ret[r.id] || 0;
      T.returnCount++; T.returns += r.amount; T.cogs -= c;
      if (!withDims) return;
      var it = L.itemById[r.saleItemId];
      //  POS-23: برگشت از فروشندهٔ **ردیفِ اصل** کم می‌شود (با `lineSeller`)
      var keys = keysFor(it ? it.productId : r.productId, it ? it.name : '', s, posHour(r, clock), it || null);
      DIMS.forEach(function (dim) {
        var x = row(dim, keys[dim][0], keys[dim][1]);
        x.qty -= r.qty; x.returns += r.amount; x.cogs -= c;
      });
    });
    var totals = {
      invoices: T.invoices, returnCount: T.returnCount, gross: T.gross, discount: T.discount, sales: T.sales,
      returns: T.returns, netSales: T.sales - T.returns, cogs: T.cogs, grossProfit: 0, marginBp: null,
      avgTicket: T.invoices > 0 ? divRound(T.sales, T.invoices) : 0,
      costEstimatedLines: T.est, profitApproximate: !costs.approved || T.est > 0,
    };
    totals.grossProfit = totals.netSales - totals.cogs;
    totals.marginBp = bp(totals.grossProfit, totals.netSales);
    if (!withDims) return { totals: totals };
    var out = { totals: totals };
    DIMS.forEach(function (dim) {
      var rows = [];
      for (var k in acc[dim]) if (Object.prototype.hasOwnProperty.call(acc[dim], k)) rows.push(finishRow(acc[dim][k]));
      rows.sort(dim === 'hour'
        ? function (a, b) { return cmp(a.key, b.key); }
        : function (a, b) { return (b.netSales - a.netSales) || cmp(a.key, b.key); });
      out[dim] = rows;
    });
    return out;
  }

  /**
   * RPT-02/03 — فروشِ بازهٔ `from..to` (روزِ کاری، شامل هر دو سر) به تفکیکِ
   * کالا، دسته، کاربر (فروشنده)، ساعت، روشِ پرداخت و کانال؛ با سودِ ناخالص و حاشیه.
   * جمعِ `sales` هر تفکیک دقیقاً برابرِ `totals.sales` است.
   * `opts.lineSeller === true` (POS-23، تنظیمِ خاموشِ `line_seller`): تفکیکِ «کاربر» به فروشندهٔ هر ردیف و برگشت از
   * فروشندهٔ ردیفِ اصل؛ نبودش ⇒ بیت‌به‌بیت پیشین.
   */
  function sales(raw, opts) {
    opts = opts || {};
    var L = ledger(raw);
    var r = aggregate(L, str(opts.from) || '0000-01-01', str(opts.to) || '9999-12-31', clockOf(opts.clock),
      costsOf(L, opts.costing, opts.costApproved), true, opts.lineSeller === true);
    r.from = str(opts.from) || '0000-01-01'; r.to = str(opts.to) || '9999-12-31';
    r.costing = opts.costing === 'mavg' ? 'mavg' : 'line';
    return r;
  }

  /**
   * RPT-13 — «ریز» ِ ردیفِ `key` از تفکیکِ `dim` (همان `ReportCore.invoicesOf`؛ بردار: report-drill-v1.json): هر فاکتورِ لغونشدهٔ
   * بازه با سهمِ «فروش» (پس از تخفیفِ `allocate`) و مقدارِ همان کلید؛ جمعِ سهم‌ها = `sales` و شمارشان = `invoices` ِ ردیف.
   * `dim: 'all'` = همهٔ قلم‌ها (`dayInvoices`). به ترتیبِ (روز، لحظه، شناسه).
   */
  function invoicesOf(raw, opts) {
    opts = opts || {};
    var L = ledger(raw), clock = clockOf(opts.clock), dim = str(opts.dim), key = str(opts.key), out = [];
    if (DIMS.indexOf(dim) < 0 && dim !== 'all') return out;
    var f = str(opts.from) || '0000-01-01', t = str(opts.to) || '9999-12-31', lineSeller = opts.lineSeller === true;
    L.sales.forEach(function (s) {
      var d = posDate(s, clock);
      if (s.cancelled || !(d >= f && d <= t)) return;
      var items = L.itemsBySale[s.id] || [], hour = posHour(s, clock), qty = 0, net = 0, hit = false;
      var gross = items.length ? items.map(function (i) { return i.total; }) : [s.total];
      var shares = allocate(gross.reduce(function (a, b) { return a + b; }, 0) - s.finalTotal, gross);
      gross.forEach(function (g, i) {
        var it = items[i], pid = it ? it.productId : '', p = L.products[pid];
        var k = dim === 'product' ? pid : dim === 'category' ? (p ? p.category : '') : dim === 'user' ? userOf(it, s, lineSeller)
          : dim === 'hour' ? hour : dim === 'payment' ? s.method : dim === 'all' ? key : s.channel;
        if (k === key) { hit = true; qty += it ? it.qty : 0; net += g - shares[i]; }
      });
      if (hit) out.push({ saleId: s.id, date: d, createdAt: s.createdAt, qty: qty, sales: net });
    });
    return out.sort(function (a, b) { return cmp(a.date, b.date) || (a.createdAt - b.createdAt) || cmp(a.saleId, b.saleId); });
  }

  /** RPT-13 — ریزِ یک روزِ جدولِ «امروز»: هر فاکتورِ لغونشدهٔ همان روز (همان `ReportCore.dayInvoices`) */
  function dayInvoices(raw, opts) {
    opts = opts || {};
    return epochDay(str(opts.date)) === null ? [] : invoicesOf(raw, { from: opts.date, to: opts.date, dim: 'all', key: '', clock: opts.clock });
  }

  /* ---------------- داشبوردِ امروز (RPT-01) ---------------- */

  var DELTA_KEYS = ['invoices', 'sales', 'returns', 'netSales', 'grossProfit', 'avgTicket', 'discount'];

  function delta(a, b) {
    var out = {};
    DELTA_KEYS.forEach(function (k) { out[k] = { diff: a[k] - b[k], bp: bp(a[k] - b[k], b[k]) }; });
    return out;
  }

  /** RPT-01 — امروز در برابرِ دیروز و همان روزِ هفتهٔ پیش */
  function today(raw, opts) {
    opts = opts || {};
    var L = ledger(raw), clock = clockOf(opts.clock), costs = costsOf(L, opts.costing, opts.costApproved);
    var d0 = str(opts.date) || (clock && opts.now > 0 ? clock.date(opts.now) : '');
    if (epochDay(d0) === null) throw new Error('report-core: today() needs opts.date or opts.now + opts.clock');
    function day(d) { var t = aggregate(L, d, d, clock, costs, false).totals; t.date = d; return t; }
    var t = day(d0), y = day(addDays(d0, -1)), w = day(addDays(d0, -7));
    return { date: d0, today: t, yesterday: y, lastWeek: w, vsYesterday: delta(t, y), vsLastWeek: delta(t, w) };
  }

  /* ---------------- سنِ بدهی (RPT-06) ---------------- */

  function ageing(groups, names, asOf, buckets) {
    var asOfDay = epochDay(asOf), rows = [];
    var totals = { balance: 0, open: 0, credit: 0, buckets: buckets.map(function () { return 0; }).concat([0]) };
    Object.keys(groups).sort().forEach(function (id) {
      var evs = groups[id].slice().sort(function (a, b) {
        return cmp(a.date, b.date) || (a.createdAt - b.createdAt) || (a.k - b.k) || cmp(a.id, b.id);
      });
      var lots = [], credit = 0;
      evs.forEach(function (e) {
        if (e.debit) {
          var left = e.amount, used = Math.min(credit, left);
          credit -= used; left -= used;
          if (left > 0) lots.push({ date: e.date, left: left });
        } else {
          var pay = e.amount;
          while (pay > 0 && lots.length) {
            var take = Math.min(pay, lots[0].left);
            lots[0].left -= take; pay -= take;
            if (lots[0].left === 0) lots.shift();
          }
          credit += pay;
        }
      });
      var open = 0, oldest = null, bk = totals.buckets.map(function () { return 0; });
      lots.forEach(function (l) {
        var n = epochDay(l.date);
        var age = n === null ? null : Math.max(0, asOfDay - n);
        var i = 0;
        if (age === null) i = buckets.length;
        else while (i < buckets.length && age > buckets[i]) i++;
        bk[i] += l.left; open += l.left;
        var a = age === null ? Infinity : age;
        if (oldest === null || a > oldest) oldest = a;
      });
      if (open === 0 && credit === 0) return;
      rows.push({ id: id, name: names[id] || '', balance: open - credit, open: open, credit: credit, buckets: bk,
        oldestDays: oldest === Infinity ? -1 : oldest });
      totals.balance += open - credit; totals.open += open; totals.credit += credit;
      bk.forEach(function (v, i) { totals.buckets[i] += v; });
    });
    rows.sort(function (a, b) { return (b.balance - a.balance) || cmp(a.id, b.id); });
    return { rows: rows, totals: totals };
  }

  /**
   * RPT-06 — بدهیِ مشتری‌ها (سنِ بدهی، اولین‌وارد‌اولین‌پرداخت مثلِ
   * DebtorEngine.debtSince) و طلبِ تأمین‌کنندگان (خرید منهای پرداختِ همان
   * خرید و پرداخت‌های بعدی، مثلِ getSupplierDebt). `buckets` مرزِ روزهاست:
   * [30,60,90] ⇒ ۰–۳۰، ۳۱–۶۰، ۶۱–۹۰، ۹۱+. سندِ بعد از `asOf` حساب نمی‌شود.
   * `oldestDays` = −۱ یعنی تاریخِ سند نامعتبر بود (در کهنه‌ترین ستون نشسته).
   */
  function debts(raw, opts) {
    opts = opts || {};
    var L = ledger(raw), clock = clockOf(opts.clock), asOf = str(opts.asOf);
    if (epochDay(asOf) === null) throw new Error('report-core: debts() needs opts.asOf');
    var buckets = arr(opts.buckets).length ? arr(opts.buckets).map(num) : [30, 60, 90];
    function inScope(d) { return epochDay(d) === null || d <= asOf; }
    var cust = {}, sup = {};
    L.transactions.forEach(function (t) {
      var d = docDate(t, clock);
      if (t.amount <= 0 || !inScope(d)) return;
      (cust[t.debtorId] = cust[t.debtorId] || []).push(
        { id: t.id, k: 0, debit: t.give, amount: t.amount, date: d, createdAt: t.createdAt });
    });
    L.purchases.forEach(function (p) {
      var d = docDate(p, clock), owed = p.total - p.paid;
      if (owed === 0 || !inScope(d)) return;
      (sup[p.supplierId] = sup[p.supplierId] || []).push(
        { id: p.id, k: 0, debit: owed > 0, amount: owed > 0 ? owed : -owed, date: d, createdAt: p.createdAt });
    });
    L.supplierPayments.forEach(function (p) {
      var d = docDate(p, clock);
      if (p.amount <= 0 || !inScope(d)) return;
      (sup[p.supplierId] = sup[p.supplierId] || []).push(
        { id: p.id, k: 1, debit: false, amount: p.amount, date: d, createdAt: p.createdAt });
    });
    return { asOf: asOf, buckets: buckets, customers: ageing(cust, L.debtors, asOf, buckets),
      suppliers: ageing(sup, L.suppliers, asOf, buckets) };
  }

  /* ---------------- ضدتقلب (RPT-09) ---------------- */

  /**
   * RPT-09 — به تفکیکِ کاربر: تخفیف (فروشنده)، برگشتی، لغو، حذفِ ردیف و باز کردنِ
   * کشو بی فروش (`no_sale`، همان نامِ pay-core). کاربرِ هر رویداد فقط از فیلدِ صریحش
   * می‌آید (`seller`، `returns[].by|user`، `cancelledBy`، `auditLog[].by|user`)؛ نبودش یعنی ""
   * (نامعلوم) — به گردنِ فروشندهٔ فاکتور انداخته نمی‌شود.
   * v3 S08: هر رویدادی که `approvedBy` دارد، برای کننده `overrideCount` و برای
   * تأییدکننده `approvalCount` می‌شمارد.
   */
  function fraud(raw, opts) {
    opts = opts || {};
    var L = ledger(raw), clock = clockOf(opts.clock);
    var from = str(opts.from) || '0000-01-01', to = str(opts.to) || '9999-12-31';
    var users = {};
    function inRange(d) { return d >= from && d <= to; }
    function blank(key) {
      return { key: key, invoices: 0, gross: 0, discountCount: 0, discount: 0, discountBp: null, returnCount: 0,
        returns: 0, voidCount: 0, voids: 0, lineDeleteCount: 0, lineDeletes: 0, noSaleCount: 0,
        overrideCount: 0, approvalCount: 0 };
    }
    function u(key) { return users[key] || (users[key] = blank(key)); }
    function approved(x, approver) { if (approver !== '') { x.overrideCount++; u(approver).approvalCount++; } }
    var cancelEntry = {};
    L.audit.forEach(function (a) {
      if (a.type === 'cancel_sale' && a.refId !== '' && !cancelEntry[a.refId]) cancelEntry[a.refId] = a;
    });
    L.sales.forEach(function (s) {
      if (s.cancelled) {
        var a = cancelEntry[s.id] || null;
        if (!inRange(a ? posDate(a, clock) : posDate(s, clock))) return;
        var v = u(s.cancelledBy !== '' ? s.cancelledBy : (a ? a.user : ''));
        v.voidCount++; v.voids += s.finalTotal;
        approved(v, a ? a.approvedBy : '');
        return;
      }
      if (!inRange(posDate(s, clock))) return;
      var x = u(s.seller);
      x.invoices++; x.gross += s.total;
      if (s.discount > 0) { x.discountCount++; x.discount += s.discount; }
      approved(x, s.approvedBy);
    });
    L.returns.forEach(function (r) {
      var s = L.saleById[r.saleId];
      if ((s && s.cancelled) || !inRange(posDate(r, clock))) return;
      var x = u(r.user);
      x.returnCount++; x.returns += r.amount;
      approved(x, r.approvedBy);
    });
    L.audit.forEach(function (a) {
      if ((a.type !== 'line_delete' && a.type !== 'no_sale') || !inRange(posDate(a, clock))) return;
      var x = u(a.user);
      if (a.type === 'line_delete') { x.lineDeleteCount++; x.lineDeletes += a.amount; } else x.noSaleCount++;
      approved(x, a.approvedBy);
    });
    var rows = Object.keys(users).sort().map(function (k) {
      var x = users[k];
      x.discountBp = bp(x.discount, x.gross);
      return x;
    });
    var totals = blank('');
    rows.forEach(function (x) {
      for (var k in x) if (k !== 'key' && k !== 'discountBp') totals[k] += x[k];
    });
    totals.discountBp = bp(totals.discount, totals.gross);
    return { from: from, to: to, rows: rows, totals: totals };
  }

  /* ---------------- جدول برای خروجی (RPT-11) ---------------- */

  var DIM_TITLES = {
    product: ['فروش به تفکیک کالا', 'کالا'], category: ['فروش به تفکیک دسته', 'دسته'],
    user: ['فروش به تفکیک کاربر', 'کاربر'], hour: ['فروش به تفکیک ساعت', 'ساعت'],
    payment: ['فروش به تفکیک روش پرداخت', 'روش پرداخت'], channel: ['فروش به تفکیک کانال', 'کانال'],
  };
  var UNKNOWN = { product: 'قلم آزاد', category: 'بی‌دسته', user: 'نامعلوم', hour: 'نامعلوم', payment: 'نامعلوم', channel: 'نامعلوم' };
  //  PAY (v3 A4): مقدارهای تازهٔ paymentMethod (PayFlow.legacyMethod) — همان KNOWNِ ReportTables.kt
  var KNOWN = {
    payment: {
      cash: 'نقد', credit: 'نسیه', card: 'کارت', wallet: 'کیف پول', transfer: 'حواله',
      store_credit: 'اعتبار فروشگاه', gift_card: 'کارت هدیه', other: 'سایر', mixed: 'ترکیبی',
      online: 'پرداختِ آنلاین', cod: 'نقد هنگامِ تحویل',
    },
    channel: { pos: 'حضوری', online: 'اینترنتی' },
  };
  var SALES_COLS = [
    ['invoices', 'فاکتور', 'count'], ['qty', 'مقدار', 'qty'], ['gross', 'جمع پیش از تخفیف', 'money'],
    ['discount', 'تخفیف', 'money'], ['sales', 'فروش', 'money'], ['returns', 'برگشتی', 'money'],
    ['netSales', 'فروش خالص', 'money'], ['cogs', 'بهای تمام‌شده', 'money'],
    ['grossProfit', 'سود ناخالص', 'money'], ['marginBp', 'حاشیه', 'bp'],
  ];

  function cols(list) { return list.map(function (c) { return { key: c[0], label: c[1], kind: c[2] }; }); }
  function pick(o, list) { return list.map(function (c) { return o[c[0]] === undefined ? null : o[c[0]]; }); }

  function label(dim, key) {
    if (key === '') return UNKNOWN[dim];
    if (dim === 'hour') return key + ':00';
    return (KNOWN[dim] && KNOWN[dim][key]) || key;
  }

  function salesTables(r) {
    var out = [];
    out.push({ id: 'sales.totals', title: 'خلاصهٔ فروش', columns: cols([['from', 'از', 'date'], ['to', 'تا', 'date']]
      .concat([['returnCount', 'برگشتی (بار)', 'count']]).concat(SALES_COLS.filter(function (c) { return c[0] !== 'qty'; }))
      .concat([['avgTicket', 'میانگین فاکتور', 'money'], ['profitApproximate', 'سود تقریبی', 'flag']])), rows: [], total: null });
    var t = r.totals;
    out[0].rows.push([r.from, r.to].concat(pick(t, [['returnCount']])).concat(pick(t, SALES_COLS.filter(function (c) { return c[0] !== 'qty'; })))
      .concat([t.avgTicket, t.profitApproximate]));
    DIMS.forEach(function (dim) {
      var list = r[dim];
      var tot = { qty: 0, gross: 0, discount: 0, sales: 0, returns: 0, netSales: 0, cogs: 0, grossProfit: 0 };
      list.forEach(function (x) { for (var k in tot) tot[k] += x[k]; });
      tot.invoices = t.invoices; tot.marginBp = bp(tot.grossProfit, tot.netSales);
      out.push({
        id: 'sales.' + dim, title: DIM_TITLES[dim][0],
        columns: cols([['name', DIM_TITLES[dim][1], 'text']].concat(SALES_COLS)),
        rows: list.map(function (x) { return [dim === 'product' && x.key !== '' ? (x.name !== '' ? x.name : x.key) : label(dim, x.key)].concat(pick(x, SALES_COLS)); }),
        total: ['جمع'].concat(pick(tot, SALES_COLS)),
      });
    });
    return out;
  }

  var TODAY_COLS = [['invoices', 'فاکتور', 'count'], ['sales', 'فروش', 'money'], ['returns', 'برگشتی', 'money'],
    ['netSales', 'فروش خالص', 'money'], ['grossProfit', 'سود ناخالص', 'money'], ['marginBp', 'حاشیه', 'bp'],
    ['avgTicket', 'میانگین فاکتور', 'money'], ['profitApproximate', 'سود تقریبی', 'flag']];

  function todayTables(r) {
    return [{
      id: 'today', title: 'داشبورد امروز',
      columns: cols([['day', 'روز', 'text'], ['date', 'تاریخ', 'date']].concat(TODAY_COLS)),
      rows: [['امروز', r.today], ['دیروز', r.yesterday], ['همین روز هفتهٔ پیش', r.lastWeek]].map(function (p) {
        return [p[0], p[1].date].concat(pick(p[1], TODAY_COLS));
      }),
      total: null,
    }];
  }

  function bucketLabels(b) {
    var out = [], lo = 0;
    b.forEach(function (hi) { out.push(lo + '–' + hi + ' روز'); lo = hi + 1; });
    out.push(lo + '+ روز');
    return out;
  }

  function debtTables(r) {
    var labels = bucketLabels(r.buckets);
    function one(id, title, who, part) {
      var bcols = labels.map(function (l, i) { return ['b' + i, l, 'money']; });
      var head = [['name', who, 'text'], ['balance', 'مانده', 'money'], ['credit', 'پیش‌پرداخت', 'money']]
        .concat(bcols).concat([['oldestDays', 'قدیمی‌ترین (روز)', 'count']]);
      return {
        id: id, title: title, columns: cols(head),
        rows: part.rows.map(function (x) {
          return [x.name || x.id || 'نامعلوم', x.balance, x.credit].concat(x.buckets)
            .concat([x.oldestDays < 0 ? null : x.oldestDays]);
        }),
        total: ['جمع', part.totals.balance, part.totals.credit].concat(part.totals.buckets).concat([null]),
      };
    }
    return [one('debts.customers', 'سن بدهی مشتری‌ها', 'مشتری', r.customers),
      one('debts.suppliers', 'طلب تأمین‌کنندگان', 'تأمین‌کننده', r.suppliers)];
  }

  var FRAUD_COLS = [['invoices', 'فاکتور', 'count'], ['gross', 'جمع پیش از تخفیف', 'money'],
    ['discountCount', 'تخفیف (بار)', 'count'], ['discount', 'تخفیف', 'money'], ['discountBp', 'نسبت تخفیف', 'bp'],
    ['returnCount', 'برگشتی (بار)', 'count'], ['returns', 'برگشتی', 'money'], ['voidCount', 'لغو (بار)', 'count'],
    ['voids', 'لغو', 'money'], ['lineDeleteCount', 'حذف ردیف (بار)', 'count'], ['lineDeletes', 'حذف ردیف', 'money'],
    ['noSaleCount', 'باز کردن کشو بی فروش', 'count'], ['overrideCount', 'نیازمند تأیید (بار)', 'count'],
    ['approvalCount', 'تأیید داده (بار)', 'count']];

  function fraudTables(r) {
    return [{
      id: 'fraud', title: 'ضدتقلب به تفکیک کاربر',
      columns: cols([['name', 'کاربر', 'text']].concat(FRAUD_COLS)),
      rows: r.rows.map(function (x) { return [x.key === '' ? 'نامعلوم' : x.key].concat(pick(x, FRAUD_COLS)); }),
      total: ['جمع'].concat(pick(r.totals, FRAUD_COLS)),
    }];
  }

  /** RPT-11 — گزارش ⇐ جدول‌های خام (عددِ صحیح) برای Excel/PDF/چاپ */
  function tables(kind, report) {
    if (kind === 'sales') return salesTables(report);
    if (kind === 'today') return todayTables(report);
    if (kind === 'debts') return debtTables(report);
    if (kind === 'fraud') return fraudTables(report);
    throw new Error('report-core: unknown table kind ' + kind);
  }

  /**
   * یک خانه ⇐ متنِ ساده با رقمِ لاتین (CSV و آزمون). رقمِ فارسی/پشتو و جداکنندهٔ
   * هزارگان کارِ لایهٔ نمایش است. money ⇒ «1234.50»، qty ⇒ «1.5»، bp ⇒ «12.34%»، flag ⇒ «بله»/«نه».
   */
  function cellText(v, kind) {
    if (v === null || v === undefined) return '';
    if (kind === 'flag') return v === true ? 'بله' : 'نه';
    if (kind === 'money' || kind === 'bp') {
      var neg = v < 0, a = neg ? -v : v;
      var s = String(floorDiv(a, 100)) + '.' + pad(a % 100, 2);
      return (neg ? '-' : '') + s + (kind === 'bp' ? '%' : '');
    }
    if (kind === 'qty') {
      var n = v < 0, b = n ? -v : v, frac = pad(b % 1000, 3).replace(/0+$/, '');
      return (n ? '-' : '') + String(floorDiv(b, 1000)) + (frac ? '.' + frac : '');
    }
    return String(v);
  }

  /** جدول ⇐ ماتریسِ متن: {title, head, rows, total} */
  function plain(table) {
    function line(cells) { return cells.map(function (v, i) { return cellText(v, table.columns[i].kind); }); }
    return {
      id: table.id, title: table.title, head: table.columns.map(function (c) { return c.label; }),
      rows: table.rows.map(line), total: table.total ? line(table.total) : null,
    };
  }

  return {
    VERSION: VERSION,
    ledger: ledger,
    fixedClock: fixedClock,
    allocate: allocate,
    costs: costsOf,
    stockCost: stockCost,
    today: today,
    sales: sales,
    invoicesOf: invoicesOf,
    dayInvoices: dayInvoices,
    debts: debts,
    fraud: fraud,
    tables: tables,
    plain: plain,
    cellText: cellText,
  };
});
