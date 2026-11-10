/* ==========================================================
   فروشگاه من | «گزارشِ تازه» در وب — صفحهٔ گزارش و کارت‌های خانه
   ----------------------------------------------------------
   RPT-01/02/03/06/09/11 و v3 §۱۹.۵ بند ۲، روی همان هستهٔ مشترک
   (`report-core.js` + `report-board.js`، دوقلوی `core/report/*.kt`).

   پشتِ تنظیمِ «گزارشِ تازه» ِ همین مرورگر (`localStorage`، همگام نمی‌شود). تصمیمِ ۳-۲۲ (۱۴۰۵/۰۸/۱۹):
   **پیش‌فرض روشن**؛ مرورگری که خودش خاموش کرده (`'0'`) خاموش می‌ماند. خاموش ⇒ همان صفحهٔ گزارش و داشبوردِ
   پیشین، بی هیچ تغییری. دفتر و فرمتِ داده دست نمی‌خورد؛ این فایل فقط می‌خواند.

   دفتر و ابزارهای صفحه (رفتن به صفحه، CSV، چاپ، قالبِ پول و تاریخ) را
   `window.tohidReportEnv()`ِ index.html می‌دهد — نسخهٔ دومی از آن‌ها این‌جا نیست.

   RPT-03: روشِ بهای سود و حاشیه همان `COST` ِ report-board (دوقلوی `CostPolicy`): پیش‌فرض و مصوب میانگینِ موزون
   با `costApproved`؛ «قیمت خرید روی قلم» انتخابِ همین مرورگر (`tohid-report-cost`) و همیشه «تقریبی».
   RPT-11: «اکسل» = ‎.xlsx‎ ِ واقعی (`TohidOwnerExport.report`، عدد عدد می‌ماند) و «PDF» = فایلِ مستقیم (`report-pdf.js`،
   دیربار با نخستین بار) — هر دو از همان برگهٔ CSV و چاپ.
   RPT-13 (§۵.۲): کلید فقط از `keymap.js` ِ ساخته‌شده از keymap.json — Ctrl+P چاپ، Ctrl+E اکسل، − / + دورهٔ قبل/بعد
   (`report-period-core.js` = دوقلوی `ReportPeriod`؛ بازه‌های وب: امروز/۷ روز ⇒ روز، این ماه ⇒ ماهِ میلادی، «همه» نه)، Alt+C
   «مقایسه با دورهٔ پیش» (Ctrl+Shift+C ِ کامپیوتر در مرورگر بازرسیِ عنصر است) و Enter روی ردیف ⇒ ریزِ فاکتورها
   (`invoicesOf`/`dayInvoices` ِ report-core، جمع = همان ردیف)؛ Esc ریز را می‌بندد. کادرِ نوشتن و پنجرهٔ باز کلید نمی‌گیرند.
   RPT-08: زبانهٔ «ساعت و روز» (`sales-heatmap-core.js` = دوقلوی `SalesHeatmap`) — جدولِ ۷ × ۲۴ با عددِ هر خانه (رنگ فقط کمک)،
   با ساعتِ همین دستگاه و روزِ کاری از نیمه‌شب (وب تنظیمِ پایانِ روز و آغازِ هفتهٔ دکان را ندارد ⇒ شنبه).
   ========================================================== */
(function () {
  'use strict';
  var KEY = 'tohid-report-fresh';
  var VM_KEY = 'view_mode:local:report-fresh';
  var COST_KEY = 'tohid-report-cost';
  var PDF_SRC = 'license/report-pdf.js?v=1';
  var state = { tab: 'today', span: 'month', dim: 'product', sort: null, shift: 0, compare: false, drill: null };

  function core() { return window.TohidReportCore; }
  function board() { return window.TohidReportBoard; }
  /** INV-16: هستهٔ «ارزش موجودی» (`stock-value-core.js`، دوقلوی `StockAnalysis.valuation`)؛ نبودش ⇒ بی آن زبانه */
  function value() { return window.TohidStockValue; }
  /** RPT-13: «دورهٔ قبل / بعد» و «تغییر» (`report-period-core.js`)؛ نبودش ⇒ بی دکمه و بی کلیدِ دوره */
  function period() { return window.TohidReportPeriod; }
  /** RPT-08: نقشهٔ حرارتی (`sales-heatmap-core.js`)؛ نبودش ⇒ بی زبانهٔ «ساعت و روز» */
  function heat() { return window.TohidSalesHeatmap; }
  /** RPT-13: میان‌برها فقط از `keymap.js` (تنها منبع: keymap.json)؛ نبودش ⇒ بی کلید، دکمه‌ها همان */
  function keymap() { return window.TohidKeymap; }
  var MAC = /Mac|iP(hone|ad|od)/.test((window.navigator && window.navigator.platform) || '');
  function hint(id) { var K = keymap(); return K ? K.label(id, MAC) : ''; }
  /** برچسبِ کلید کنارِ نامِ دکمه (صفحه‌خوان نامِ دکمه را می‌خواند و کلید را از `aria-keyshortcuts`) */
  function kbd(id) {
    var h = hint(id);
    return h ? ' <kbd class="rf-kbd" aria-hidden="true">' + esc(h) + '</kbd>' : '';
  }
  /** دکمه با نام و برچسبِ کلیدِ `id` */
  function keyBtn(attrs, label, id) { return '<button type="button" ' + attrs + ariaKeys(id) + '>' + esc(label) + kbd(id) + '</button>'; }
  function ariaKeys(id) {
    var h = hint(id).replace(/\bCtrl\b/g, 'Control').replace(/\bCmd\b/g, 'Meta').replace(/\bOption\b/g, 'Alt').replace(/^\+$/, 'Plus').replace('\u2212', '-');
    return h ? ' aria-keyshortcuts="' + esc(h) + '"' : '';
  }
  function env() { return typeof window.tohidReportEnv === 'function' ? window.tohidReportEnv() : null; }
  function get(k) { try { return window.localStorage.getItem(k); } catch (e) { return null; } }
  function put(k, v) { try { window.localStorage.setItem(k, v); } catch (e) { /* حالتِ خصوصی: فقط همین بار */ } }

  //  تصمیمِ ۳-۲۲: نیامده (مرورگرِ تازه یا پیش از این نسخه) ⇒ روشن؛ فقط `'0'` ِ خودِ کاربر خاموش است
  function enabled() { return get(KEY) !== '0'; }

  /** RPT-03: روشِ بهای همین مرورگر (`COST.parse`: نبود ⇒ مصوب) و تأییدش برای هسته — هرگز `true` ِ ثابت */
  function cost() {
    var C = board() && board().COST;
    var m = C ? C.parse(get(COST_KEY)) : 'line';
    return { m: m, costing: m, costApproved: C ? C.approved(m) : false };
  }
  function homeOf(E) {
    var c = cost();
    return board().build(E.ledger, { date: E.today, clock: clock(), money: E.money, costing: c.costing, costApproved: c.costApproved });
  }
  function costLabel(base, m) { var C = board().COST; return C ? C.label(base, m) : base + ' (تقریبی)'; }
  /** همان `costLabel`، هر تکه در span ِ خودش تا لایهٔ زبان (`web-i18n.js`) هر کدام را جدا بشناسد */
  function labelHtml(base, m) {
    var full = costLabel(base, m);
    if (full === base) return esc(base);
    return '<span>' + esc(base) + '</span> (' + full.slice(base.length + 2, -1).split('، ').map(function (t) {
      return '<span>' + esc(t) + '</span>';
    }).join('، ') + ')';
  }
  /** `COST.explain`: شرحِ روش و (اگر مصوب نیست) چرا تقریبی است، دو جملهٔ جدا */
  function explainHtml(m) {
    var C = board().COST, def = C && C.METHODS.filter(function (x) { return x.id === m; })[0];
    if (!def) return '';
    var rest = C.explain(m).slice(def.note.length).replace(/^\s+/, '');
    return '<span>' + esc(def.note) + '</span>' + (rest ? ' <span>' + esc(rest) + '</span>' : '');
  }

  /** ساعتِ همین دستگاه: روزِ کاری از نیمه‌شب؛ فقط تفکیکِ «ساعت» از آن درمی‌آید */
  function clock() { return { offsetMinutes: -new Date().getTimezoneOffset(), dayStartMinute: 0 }; }

  function esc(s) {
    return String(s === undefined || s === null ? '' : s).replace(/[&<>"']/g, function (c) {
      return { '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c];
    });
  }

  /** «12.3٪» — یک رقمِ اعشار، بی علامتِ مثبت (همان `ReportFreshModel.percent`) */
  function percent(bp) {
    var a = bp < 0 ? -bp : bp, tenths = Math.floor((a + 5) / 10), r = tenths % 10;
    return (bp < 0 && tenths > 0 ? '-' : '') + Math.floor(tenths / 10) + (r ? '.' + r : '') + '٪';
  }

  function fmt(v, kind, E) {
    if (v === null || v === undefined) return '—';
    if (kind === 'money') return E.formatMoney(v / 100);
    if (kind === 'qty') return String(Math.round(v) / 1000);
    if (kind === 'bp') return percent(v);
    if (kind === 'flag') return v === true ? 'بله' : 'نه';
    if (kind === 'date') return E.formatDate(v);
    return String(v);
  }

  function phone() { return !!(window.matchMedia && window.matchMedia('(max-width: 599px)').matches); }
  /** VISUAL-DIRECTION §۱.۵: صفحهٔ مدیریت پیش‌فرض جدول؛ گوشی همیشه کارت */
  function viewMode() { return phone() ? 'card' : (get(VM_KEY) === 'card' ? 'card' : 'table'); }

  /** INV-16 — مثلِ «بهای میانگین» ِ برنامه (`StockCostFresh.tabs`) بیرون از تابلوی مشترک و فقط برای کسی که پول را می‌بیند */
  var STOCK_TAB = { id: 'stock-value', label: 'ارزش موجودی', ranged: false };
  /** RPT-08 — مثلِ `HeatmapModel.tabs` ِ برنامه: فقط برای کسی که پول را می‌بیند */
  var HEAT_TAB = { id: 'heatmap', label: 'ساعت و روز', ranged: true };
  function tabsFor(E) {
    var B = board();
    if (!E.money) return B.TABS.filter(function (t) { return t.id === 'debts'; });
    return B.TABS.concat(heat() ? [HEAT_TAB] : [], value() ? [STOCK_TAB] : []);
  }

  /* ---------------- RPT-13: دوره ---------------- */

  /** بازهٔ وب ⇒ واحدِ جابه‌جایی (همان `ReportKeys.unitOf` ِ کامپیوتر)؛ «همه» جابه‌جا نمی‌شود */
  var UNITS = { today: 'days', week: 'days', month: 'gmonth' };
  function unit() { return period() ? UNITS[state.span] : undefined; }
  /** بازهٔ مؤثر: `shift` دوره از بازهٔ پایه؛ نشد ⇒ همان پایه (`ReportKeys.range`) */
  function rangeOf(E) {
    var b = board().range(state.span, E.today), u = unit();
    return (state.shift && u && period().shift(u, b.from, b.to, state.shift, E.today)) || b;
  }
  /** `shift + by` ممکن است؟ نه دورهٔ آینده، نه «همه» (`ReportKeys.can`) */
  function can(E, by) {
    var b = board().range(state.span, E.today), u = unit(), to = state.shift + by;
    return !!u && to <= 0 && period().shift(u, b.from, b.to, to, E.today) !== null;
  }
  function comparable() { return (state.tab === 'sales' || state.tab === 'profit') && !!unit(); }

  /* ---------------- خانه ---------------- */

  function presetAttrs(p) {
    return ' data-tab="' + esc(p.tab) + '" data-span="' + esc(p.span) + '" data-dim="' + esc(p.dim) + '"';
  }

  function kpiHtml(k, E) {
    var value = k.kind === 'money' ? E.formatMoney(k.value / 100) + ' ؋' : String(k.value);
    var cls = k.trend === 'flat' ? 'flat' : (k.good ? 'good' : 'bad');
    var arrow = k.trend === 'up' ? '▲' : (k.trend === 'down' ? '▼' : '–');
    var delta = k.deltaText ? '<span class="d ' + cls + '"><span aria-hidden="true">' + arrow + '</span> ' + esc(k.deltaText) + '</span>' : '<span></span>';
    return '<button type="button" class="rf-kpi" data-kpi="' + esc(k.id) + '"' + presetAttrs(k.target) +
      ' aria-label="' + esc([k.title, value, k.deltaText, k.period].filter(Boolean).join('، ')) + ' — نمایشِ فهرست">' +
      '<span class="t">' + esc(k.title) + '</span><span class="v">' + esc(value) + '</span>' +
      '<span class="f">' + delta + '<span>' + esc(k.period) + '</span></span></button>';
  }

  function pillHtml(p) {
    return '<button type="button" class="rf-pill rf-' + esc(p.tone) + '" data-pill="' + esc(p.id) + '"' + presetAttrs(p.target) +
      ' aria-label="وضعیت: ' + esc(p.text) + ' — نمایشِ فهرست"><span class="dot" aria-hidden="true"></span>' + esc(p.text) + '</button>';
  }

  function openPreset(el) {
    var E = env();
    state.tab = el.getAttribute('data-tab') || 'today';
    state.span = el.getAttribute('data-span') || 'today';
    state.shift = 0; state.drill = null;
    if (el.getAttribute('data-dim')) state.dim = el.getAttribute('data-dim');
    state.sort = null;
    if (E && E.goTo) E.goTo('reports');
  }

  function renderHome() {
    var el = document.getElementById('dash-fresh');
    if (!el) return;
    var E = env(), B = board();
    if (!enabled() || !E || !B || !core()) { el.hidden = true; el.innerHTML = ''; return; }
    var home = homeOf(E);
    el.innerHTML = '<div class="rf-kpis">' + home.kpis.map(function (k) { return kpiHtml(k, E); }).join('') + '</div>' +
      '<div class="rf-pills" role="list" aria-label="وضعیتِ دکان">' + home.pills.map(pillHtml).join('') + '</div>';
    el.hidden = false;
    if (!el.__rf) {
      el.__rf = true;
      el.addEventListener('click', function (e) {
        var b = e.target.closest('[data-tab]');
        if (b && el.contains(b)) openPreset(b);
      });
    }
  }

  /* ---------------- صفحهٔ گزارش ---------------- */

  /** جدول‌های یک زبانه — همان `ReportFreshModel.view`ِ Kotlin */
  function compute(E) {
    var C = core(), B = board(), L = C.ledger(E.ledger), ck = clock(), co = cost();
    var r = rangeOf(E), dim = B.DIMS.indexOf(state.dim) >= 0 ? state.dim : 'product';
    var tab = state.tab;
    if (tab === 'today') {
      var td = C.tables('today', C.today(L, { date: E.today, clock: ck, costing: co.costing, costApproved: co.costApproved }));
      td[0].drillRows = td[0].rows.length;
      return { tables: td };
    }
    if (tab === 'sales' || tab === 'profit') {
      var opts = function (x) { return { from: x.from, to: x.to, clock: ck, costing: co.costing, costApproved: co.costApproved }; };
      var s = C.sales(L, opts(r)), t = salesView(s, dim, tab), v = { sales: s, dim: dim, tables: [t] };
      t.drillRows = t.rows.length;
      //  RPT-13 «مقایسه»: همان گزارش در یک دوره پیش از بازهٔ مؤثر (`ReportPeriod.shift(…, −1)`)
      var prev = state.compare && comparable() ? period().shift(unit(), r.from, r.to, -1, E.today) : null;
      if (prev) {
        v.prev = C.sales(L, opts(prev));
        v.tables = [compared(t, s[dim], salesView(v.prev, dim, tab), v.prev[dim], tab === 'sales' ? 'netSales' : 'grossProfit')];
      }
      return v;
    }
    if (tab === HEAT_TAB.id) {
      var H = heat(), h = H.build(H.events(L, r.from, r.to), ck.offsetMinutes, ck.dayStartMinute, 6);
      return { heat: h, range: r, tables: H.tables(h) };
    }
    if (tab === 'debts') {
      var d = C.debts(L, { asOf: E.today, clock: ck });
      return { tables: C.tables('debts', d), debts: d };
    }
    if (tab === STOCK_TAB.id) return stockValueView(value().fromLedger(E.ledger, C));
    var f = C.fraud(L, { from: r.from, to: r.to, clock: ck });
    return { tables: C.tables('fraud', f), fraud: f };
  }

  /** جدولِ تفکیکِ «فروش» یا «سود و حاشیه» از گزارشِ هسته — همان `ReportFreshModel.view` */
  function salesView(s, dim, tab) {
    var B = board(), t = core().tables('sales', s).filter(function (x) { return x.id === 'sales.' + dim; })[0];
    if (tab === 'sales') return t;
    var keys = ['name', 'netSales', 'cogs', 'grossProfit', 'marginBp'];
    var idx = keys.map(function (k) { return t.columns.map(function (c) { return c.key; }).indexOf(k); }).filter(function (i) { return i >= 0; });
    var pick = function (row) { return idx.map(function (i) { return row[i]; }); };
    return { id: 'profit', title: costLabel('سود و حاشیه به تفکیک ' + B.DIM_LABELS[dim], s.costing),
      columns: idx.map(function (i) { return t.columns[i]; }), rows: t.rows.map(pick), total: t.total ? pick(t.total) : null };
  }

  /**
   *  RPT-13 «مقایسه» در خودِ جدول (همان قاعدهٔ `ReportCompare.column` ِ برنامه): «دورهٔ قبل» و «تغییر» (این − قبل) ِ ستونِ `col`،
   *  ردیف‌به‌ردیف با کلیدِ ردیفِ هسته (نه نوشتهٔ خانه)؛ ردیفِ فقط-دورهٔ-قبل در انتها با خانه‌های خالیِ این دوره.
   */
  function compared(t, cur, p, past, col) {
    var i = t.columns.map(function (c) { return c.key; }).indexOf(col), before = {}, seen = {};
    if (i < 0) return t;
    past.forEach(function (x, j) { before[x.key] = p.rows[j][i]; });
    var pair = function (c, q) { return [q, c === null && q === null ? null : (c || 0) - (q || 0)]; };
    var rows = t.rows.map(function (row, j) {
      seen[cur[j].key] = true;
      return row.concat(pair(row[i], Object.prototype.hasOwnProperty.call(before, cur[j].key) ? before[cur[j].key] : null));
    });
    past.forEach(function (x, j) {
      if (!seen[x.key]) rows.push(t.columns.map(function (c, n) { return n === 0 ? p.rows[j][0] : null; }).concat(pair(null, p.rows[j][i])));
    });
    return { id: t.id, title: t.title, rows: rows, drillRows: t.rows.length,
      columns: t.columns.concat([{ key: 'prev', label: 'دورهٔ قبل', kind: 'money' }, { key: 'change', label: 'تغییر', kind: 'money' }]),
      total: t.total ? t.total.concat(pair(t.total[i], p.total ? p.total[i] : null)) : null };
  }

  /**
   *  INV-16 — ارزشِ موجودی به بها و به قیمتِ فروش و سودِ بالقوه: بها = میانگینِ موزونِ همین دفتر (D-23)، ارزش = جمعِ دفتر.
   *  فقط کالاهای موجودِ حساب‌شده در جدول؛ ردیفِ «جمع» همان جمعِ هسته (منفی، کیت و «حساب نشد» بیرون‌اند و زیرِ جدول گفته می‌شوند).
   */
  function stockValueView(sv) {
    var v = sv.valuation;
    var approx = !v.complete || sv.rows.some(function (r) { return r.approximate; });
    return {
      value: v,
      tables: [{
        id: STOCK_TAB.id, title: 'ارزش موجودی و سود بالقوه',
        columns: [
          { key: 'name', label: 'کالا', kind: 'text' }, { key: 'qty', label: 'موجودی', kind: 'qty' },
          { key: 'unitCost', label: 'بهای یک واحد', kind: 'money' }, { key: 'costValue', label: 'ارزش موجودی', kind: 'money' },
          { key: 'retailValue', label: 'ارزش به قیمت فروش', kind: 'money' }, { key: 'profit', label: 'سود بالقوه', kind: 'money' },
          { key: 'approx', label: 'تقریبی', kind: 'flag' },
        ],
        rows: sv.rows.map(function (r) { return [r.name, r.qtyMilli, r.unitCostMinor, r.costMinor, r.retailMinor, r.profitMinor, r.approximate]; }),
        total: ['جمع', null, null, v.costMinor, v.retailMinor, v.potentialProfitMinor, approx],
      }],
    };
  }

  /** زیرِ آمار: قاعده، و هر کم‌بود جدا (پنهان نمی‌شود؛ همان یادداشت‌های «تحلیلِ موجودی» ِ برنامه) */
  function stockValueNotes(v) {
    var out = [];
    if (v.missingCost.length) out.push(v.missingCost.length + ' کالای موجود بی بها — در سود نیامد');
    if (v.missingPrice.length) out.push(v.missingPrice.length + ' کالای موجود بی قیمت فروش');
    if (v.belowCost.length) out.push(v.belowCost.length + ' کالا قیمت فروشِ زیرِ بها دارد');
    if (v.negative.length) out.push(v.negative.length + ' کالا موجودیِ منفی دارد و در جمع نیست.');
    if (v.outOfRange.length) out.push(v.outOfRange.length + ' کالا عددِ نامعقول دارد — حساب نشد');
    return '<p class="rf-note">بها میانگینِ موزونِ متحرکِ همین دفتر است (D-23)؛ سود بالقوه فقط کالاهایی را می‌شمارد که هم بها و هم قیمت فروش دارند. فقط خواندنی؛ «تقریبی» یعنی بهای پایه معلوم نیست.</p>' +
      (out.length ? '<ul class="rf-note">' + out.map(function (x) { return '<li>' + esc(x) + '</li>'; }).join('') + '</ul>' : '');
  }

  function cardKeys(id) {
    if (id === 'today') return ['netSales', 'invoices', 'grossProfit', 'avgTicket'];
    if (id === 'profit') return ['netSales', 'cogs', 'grossProfit', 'marginBp'];
    if (id.indexOf('sales.') === 0) return ['netSales', 'invoices', 'qty', 'returns'];
    if (id.indexOf('debts.') === 0) return ['balance', 'credit', 'oldestDays'];
    if (id === 'fraud') return ['discount', 'returnCount', 'voidCount', 'lineDeleteCount', 'noSaleCount'];
    if (id === STOCK_TAB.id) return ['qty', 'costValue', 'retailValue', 'profit'];
    if (id === 'heatmap.hours' || id === 'heatmap.days') return ['count', 'net'];
    return [];
  }

  function seg(name, items, current, label) {
    return '<div class="rf-seg" role="group" aria-label="' + esc(label) + '">' + items.map(function (it) {
      return '<button type="button" data-' + name + '="' + esc(it[0]) + '" aria-pressed="' + (it[0] === current) + '">' + esc(it[1]) + '</button>';
    }).join('') + '</div>';
  }

  function stat(title, value, period) { return statHtml(esc(title), value, period); }
  function statHtml(title, value, period) {
    return '<div class="rf-stat"><span class="t">' + title + '</span><span class="v">' + esc(value) + '</span><span class="p">' + esc(period) + '</span></div>';
  }

  function periodOf(from, to, E) {
    if (from === '0000-01-01') return 'همهٔ تاریخ‌ها';
    return from === to ? E.formatDate(from) : E.formatDate(from) + ' تا ' + E.formatDate(to);
  }

  function summary(v, E) {
    var af = function (x) { return E.formatMoney(x / 100) + ' ؋'; };
    if (state.tab === 'today') {
      var home = homeOf(E);
      return '<div class="rf-kpis">' + home.kpis.map(function (k) { return kpiHtml(k, E); }).join('') + '</div>';
    }
    if (v.sales && state.tab === 'sales') {
      var t = v.sales.totals, p = periodOf(v.sales.from, v.sales.to, E);
      return '<div class="rf-stats">' + stat('فروش خالص', af(t.netSales), p) + stat('فاکتور', String(t.invoices), p) +
        stat('تخفیف', af(t.discount), p) + stat('برگشتی', af(t.returns), p) + stat('میانگین فاکتور', af(t.avgTicket), p) + '</div>' +
        compareLine(v, E);
    }
    if (v.sales) {
      //  RPT-03: روشِ بها همان که هسته با آن حساب کرد (`costing`)، نه حدسِ صفحه — همان کارت‌های `ReportFresh` ِ برنامه
      var u = v.sales.totals, q = periodOf(v.sales.from, v.sales.to, E), m = v.sales.costing, CP = board().COST;
      return '<div class="rf-stats">' + stat('فروش خالص', af(u.netSales), q) + statHtml(labelHtml('بهای تمام‌شده', m), af(u.cogs), q) +
        statHtml(labelHtml('سود ناخالص', m), af(u.grossProfit), q) + statHtml(labelHtml('حاشیه', m), u.marginBp === null ? '—' : percent(u.marginBp), q) + '</div>' +
        (CP ? '<div class="rf-bar"><span class="rf-label">روش بها</span>' +
          seg('cost', CP.METHODS.map(function (x) { return [x.id, x.label]; }), m, 'روش بها') + '</div>' : '') +
        '<p class="rf-note">' + (u.profitApproximate
          ? '<span class="rf-pill rf-warning" role="status"><span class="dot" aria-hidden="true"></span>سود تقریبی</span> '
          : '<span class="rf-pill rf-success" role="status"><span class="dot" aria-hidden="true"></span>سود قطعی</span> ') +
        explainHtml(m) +
        (u.costEstimatedLines > 0 ? ' <span>' + esc(u.costEstimatedLines + ' قلم بی بهای خرید است.') + '</span>' : '') + '</p>' +
        compareLine(v, E);
    }
    if (v.heat) return heatSummary(v.heat, E);
    if (v.debts) {
      var c = v.debts.customers, edge = v.debts.buckets[v.debts.buckets.length - 1];
      var late = c.rows.filter(function (x) { return x.buckets[x.buckets.length - 1] > 0; }).length;
      return '<div class="rf-stats">' + stat('طلب از مشتریان', af(c.totals.balance), 'تا امروز') +
        stat('مشتریانِ بدهکار', String(c.rows.filter(function (x) { return x.balance > 0; }).length), 'تا امروز') +
        stat('قرضِ بیش از ' + edge + ' روز', String(late), 'مشتری') + stat('بدهی به تأمین‌کنندگان', af(v.debts.suppliers.totals.balance), 'تا امروز') + '</div>';
    }
    if (v.value) {
      var m = v.value;
      return '<div class="rf-stats">' + stat('ارزش موجودی', af(m.costMinor), 'تا امروز') + stat('ارزش به قیمت فروش', af(m.retailMinor), 'تا امروز') +
        stat('سود بالقوه', af(m.potentialProfitMinor), 'تا امروز') + stat('حاشیه', m.marginBp === null ? '—' : percent(m.marginBp), 'تا امروز') + '</div>' +
        stockValueNotes(m);
    }
    if (v.fraud) {
      var f = v.fraud.totals, w = periodOf(v.fraud.from, v.fraud.to, E);
      return '<div class="rf-stats">' + stat('لغوِ فاکتور', String(f.voidCount), w) + stat('برگشتی', String(f.returnCount), w) +
        stat('حذفِ ردیف', String(f.lineDeleteCount), w) + stat('کشو بی فروش', String(f.noSaleCount), w) + stat('نیازمندِ تأیید', String(f.overrideCount), w) + '</div>' +
        alertsHtml(v.fraud, E);
    }
    return '';
  }

  /** RPT-13 — خطِ «نسبت به دورهٔ قبل» (همان `ReportCompareLine` ِ کامپیوتر): فروشِ خالص، فاکتورها و سودِ ناخالص با `change` */
  function compareLine(v, E) {
    if (!state.compare || !comparable()) return '';
    if (!v.prev) return '<p class="rf-note rf-compare">' + esc('دورهٔ قبلی برای مقایسه نیست.') + '</p>';
    var a = v.sales.totals, b = v.prev.totals;
    var part = function (label, cur, prev, money) {
      var c = period().change(cur, prev), sign = c.diff > 0 ? '+' : '';
      var amount = money ? sign + E.formatMoney(c.diff / 100) + ' ؋' : sign + c.diff / 100;
      return '<span>' + esc(label) + '</span> <span dir="ltr">' + esc(amount) + '</span> (' +
        (c.bp === null ? '<span>' + esc('بی پایه') + '</span>' : '<span dir="ltr">' + esc((c.bp > 0 ? '+' : '') + percent(c.bp)) + '</span>') + ')';
    };
    return '<p class="rf-note rf-compare"><span>' + esc('نسبت به ' + E.formatDate(v.prev.from) + ' تا ' + E.formatDate(v.prev.to) + ':') + '</span> ' +
      [part('فروشِ خالص', a.netSales, b.netSales, true), part('فاکتورها', a.invoices * 100, b.invoices * 100, false),
        part('سودِ ناخالص', a.grossProfit, b.grossProfit, true)].join(' · ') + '</p>';
  }

  /** RPT-08 — کارت‌های «ساعت و روز» (همان `HeatmapSummary`): پرفروش‌ترین خانه، شمارِ فروش، خالصِ مبلغ */
  function heatSummary(h, E) {
    var H = heat(), pk = h.peak;
    var peak = pk ? '<span>' + esc(H.dayName(h.weekdays[pk[0]])) + '</span> ' + esc(H.hourLabel(pk[1])) : '—';
    return '<div class="rf-stats"><div class="rf-stat"><span class="t">' + esc('پرفروش‌ترین ساعت') + '</span><span class="v">' + peak +
      '</span><span class="p">' + esc(pk ? h.maxCount + ' فروش' : 'بی فروش') + '</span></div>' +
      stat('شمارِ فروش', String(h.total.count), 'در این بازه') + stat('خالصِ مبلغ', E.formatMoney(h.total.totalMinor / 100) + ' ؋', 'فروش − برگشت') + '</div>' +
      '<p class="rf-note">' + esc('عددِ هر خانه شمارِ فروشِ همان روز و ساعت است و رنگش فقط شدتِ آن؛ برگشتی در مبلغِ ساعتِ خودش کم می‌شود و شمرده نمی‌شود. ساعت با ساعتِ همین دستگاه است و روزِ کاری از نیمه‌شب.') + '</p>';
  }

  /** نقشه: جدولِ واقعیِ ۷ × ۲۴ — سرخطِ ستون (ساعت) و ردیف (روز) برای صفحه‌خوان، عدد در هر خانه، رنگ با `level` ِ هسته */
  function gridHtml(t, h) {
    var H = heat(), cells = function (row, r) {
      return row.slice(1).map(function (v, i) {
        var lv = r >= 0 && i < H.HOURS ? H.level(h, r, i, 4) : 0;
        return '<td class="n rf-h' + lv + '">' + esc(String(v)) + '</td>';
      }).join('');
    };
    var legend = '<p class="rf-note rf-legend" aria-hidden="true"><span>' + esc('کم') + '</span>' +
      [1, 2, 3, 4].map(function (l) { return '<span class="rf-sw rf-h' + l + '"></span>'; }).join('') + '<span>' + esc('زیاد') + '</span></p>';
    return '<div class="rf-table-wrap" tabindex="0" role="region" aria-label="' + esc(t.title) + '"><table class="rf-table rf-heat" data-table-id="' + esc(t.id) + '">' +
      '<caption class="rf-sr">' + esc(t.title) + '</caption><thead><tr>' +
      t.columns.map(function (c, i) {
        //  ساعت: «08» دیده می‌شود و «08:00» خوانده (۲۴ ستون در پهنای کامپیوتر جا شوند)
        var lab = i > 0 && i <= H.HOURS ? '<span aria-hidden="true">' + esc(c.label.slice(0, 2)) + '</span><span class="rf-sr">' + esc(c.label) + '</span>' : esc(c.label);
        return '<th scope="col" class="' + (i ? 'n' : '') + '">' + lab + '</th>';
      }).join('') + '</tr></thead><tbody>' +
      t.rows.map(function (row, r) { return '<tr><th scope="row">' + esc(row[0]) + '</th>' + cells(row, r) + '</tr>'; }).join('') +
      '</tbody><tfoot><tr><th scope="row">' + esc(t.total[0]) + '</th>' + cells(t.total, -1) + '</tr></tfoot></table></div>' + legend;
  }

  /**
   *  RPT-13 — ریزِ ردیفِ `state.drill` (همان `ReportFreshModel.drill`/`dayDrill`): فاکتورهای همان ردیف، جمعِ «فروش» = همان ردیف.
   *  نامِ فاکتور همان `invoiceLabel` ِ صفحه (سری و شماره)؛ نبودش ⇒ «—».
   */
  function drillView(v, E) {
    var d = state.drill, C = core(), t = d && v.tables.filter(function (x) { return x.id === d.table; })[0];
    if (!t || !(d.index < t.drillRows)) return null;
    var row = t.rows[d.index], ck = clock(), L = C.ledger(E.ledger), raw = {}, day = t.id === 'today';
    (E.ledger.sales || []).forEach(function (x) { raw[x.id] = x; });
    var doc = function (id) { var f = window.invoiceLabel; return (raw[id] && typeof f === 'function' && f(raw[id])) || '—'; };
    var list = day ? C.dayInvoices(L, { date: row[1], clock: ck })
      : C.invoicesOf(L, { from: v.sales.from, to: v.sales.to, dim: v.dim, key: v.sales[v.dim][d.index].key, clock: ck });
    var qty = 0, sum = 0;
    list.forEach(function (x) { qty += x.qty; sum += x.sales; });
    var cols = [{ key: 'doc', label: 'فاکتور', kind: 'text' }, { key: 'qty', label: 'مقدار', kind: 'qty' }, { key: 'sales', label: 'فروش', kind: 'money' }];
    var label = fmt(row[0], t.columns[0].kind, E) || '—';
    return {
      note: day ? 'جمعِ «فروش» ِ این فاکتورها همان ستونِ «فروش» ِ همین روز است؛ برگشتی جداست.' : 'جمعِ «فروش» ِ این فاکتورها همان ستونِ «فروش» ِ ردیف است؛ برگشتی جداست.',
      table: {
        id: day ? 'today.drill' : 'sales.drill', title: 'ریزِ «' + label + '» — ' + list.length + ' فاکتور',
        columns: day ? cols : [{ key: 'date', label: 'روز', kind: 'date' }].concat(cols),
        rows: list.map(function (x) { return (day ? [] : [x.date]).concat([doc(x.saleId), x.qty, x.sales]); }),
        total: (day ? ['جمع'] : ['جمع', '']).concat([qty, sum]),
      },
    };
  }

  function drillHtml(v, E) {
    var dv = drillView(v, E);
    if (!dv) return '';
    return '<section class="rf-drill" aria-labelledby="rf-drill-h"><div class="rf-bar"><h3 class="rf-title" id="rf-drill-h" tabindex="-1">' + esc(dv.table.title) +
      '</h3><span class="sp"></span>' + keyBtn('class="btn btn-secondary btn-sm" data-act="drill-close"', 'بستن', 'global.back') + '</div><p class="rf-note">' + esc(dv.note) + '</p>' + tableHtml(dv.table, E) + '</section>';
  }

  /**
   *  RSK-10: هشدارِ رفتارِ غیرعادیِ صندوقدار در زبانهٔ ضدتقلب — همان `alertsOf` ِ تابلو که نشانِ خانه را می‌سازد
   *  (دوقلوی `CashierAlertBox` ِ برنامه، `StrRisk`). بی هشدار ⇒ هیچ کادری؛ بی اجازهٔ دیدنِ پول ⇒ هیچ (همان نشانِ خانه).
   */
  var ALERT_LABELS = { void: 'لغوِ فاکتور', discount: 'تخفیف', no_sale: 'کشو بی فروش' };
  function alertsHtml(fraud, E) {
    if (E.money === false) return '';
    var a = board().alertsOf(fraud);
    if (!a.length) return '';
    return '<div class="rf-alerts" role="status"><p class="rf-alerts-title">' + esc(a.length + ' هشدارِ رفتارِ غیرعادیِ صندوقدار') + '</p><ul>' +
      a.map(function (x) {
        return '<li>' + esc(x.key + ': ' + x.count + ' ' + (ALERT_LABELS[x.kind] || x.kind) + ' در ' + x.invoices + ' فاکتور — بقیه ' + x.peerCount + ' در ' + x.peerInvoices) + '</li>';
      }).join('') + '</ul><p class="rf-note">سنجش با بقیهٔ کاربرانِ همین بازه: دستِ کم ۳ بار و دو برابرِ نرخِ آن‌ها</p></div>';
  }

  function tableHtml(t, E) {
    var s = state.sort && state.sort.id === t.id ? state.sort : null;
    var rows = s ? board().sortRows(t.rows, s.col, s.desc) : t.rows;
    var num = function (k) { return k !== 'text' && k !== 'date'; };
    var head = t.columns.map(function (c, i) {
      var sort = s && s.col === i ? (s.desc ? 'descending' : 'ascending') : 'none';
      return '<th scope="col" aria-sort="' + sort + '" class="' + (num(c.kind) ? 'n' : '') + '"><button type="button" data-sort="' + i +
        '" data-table="' + esc(t.id) + '">' + esc(c.label) + (sort === 'none' ? '' : (sort === 'descending' ? ' ▼' : ' ▲')) + '</button></th>';
    }).join('');
    //  RPT-13: ردیفِ ریزشدنی (`drillRows`) نامش دکمه است — Tab تا آن و Enter (یا کلیک) ⇒ ریزِ فاکتورهای همان ردیف
    var body = rows.length ? rows.map(function (r) {
      var j = t.drillRows ? t.rows.indexOf(r) : -1;
      return '<tr>' + r.map(function (v, i) {
        var text = esc(fmt(v, t.columns[i].kind, E));
        if (i === 0 && j >= 0 && j < t.drillRows) {
          text = '<button type="button" class="rf-drill-btn" data-drill="' + j + '" data-table="' + esc(t.id) + '" aria-label="' +
            esc('ریزِ ' + fmt(v, t.columns[0].kind, E)) + '"' + ariaKeys('reports.drill') + '>' + text + '</button>';
        }
        return '<td class="' + (num(t.columns[i].kind) ? 'n' : '') + '">' + text + '</td>';
      }).join('') + '</tr>';
    }).join('') : '<tr><td class="rf-empty" colspan="' + t.columns.length + '">در این بازه چیزی نیست.</td></tr>';
    var foot = t.total ? '<tfoot><tr>' + t.total.map(function (v, i) {
      return '<td class="' + (num(t.columns[i].kind) ? 'n' : '') + '">' + (v === null ? '' : esc(fmt(v, t.columns[i].kind, E))) + '</td>';
    }).join('') + '</tr></tfoot>' : '';
    return '<div class="rf-table-wrap" tabindex="0" role="region" aria-label="' + esc(t.title) + '"><table class="rf-table" data-table-id="' + esc(t.id) + '">' +
      '<caption class="rf-sr">' + esc(t.title) + '</caption><thead><tr>' + head + '</tr></thead><tbody>' + body + '</tbody>' + foot + '</table></div>';
  }

  function cardsHtml(t, E) {
    if (!t.rows.length) return '<p class="rf-empty">در این بازه چیزی نیست.</p>';
    var keys = cardKeys(t.id).map(function (k) { return t.columns.map(function (c) { return c.key; }).indexOf(k); }).filter(function (i) { return i >= 0; });
    return '<div class="rf-cards" data-table-id="' + esc(t.id) + '">' + t.rows.map(function (r) {
      return '<div class="rf-card"><h4>' + esc(fmt(r[0], t.columns[0].kind, E)) + '</h4>' + keys.map(function (i) {
        return '<div class="l"><span>' + esc(t.columns[i].label) + '</span><b>' + esc(fmt(r[i], t.columns[i].kind, E)) + '</b></div>';
      }).join('') + '</div>';
    }).join('') + '</div>';
  }

  /** برگهٔ خروجی — همان `ReportFreshModel.exportRows`: عددِ `plain()` (رقمِ لاتین) تا اکسل حسابش کند */
  function exportRows(tables) {
    var C = core(), out = [];
    tables.forEach(function (t, i) {
      if (i > 0) out.push(['']);
      var p = C.plain(t);
      if (tables.length > 1) out.push([p.title]);
      out.push(p.head);
      p.rows.forEach(function (r) { out.push(r); });
      if (p.total) out.push(p.total);
    });
    return out;
  }

  var last = null;

  function renderReports() {
    var el = document.getElementById('rep-fresh');
    var E = env(), B = board();
    if (!el || !E || !B || !core()) return;
    var tabs = tabsFor(E);
    if (!tabs.some(function (t) { return t.id === state.tab; })) state.tab = tabs[0].id;
    var def = tabs.filter(function (t) { return t.id === state.tab; })[0];
    if (B.SPANS.indexOf(state.span) < 0) state.span = 'month';
    var v = compute(E);
    last = { v: v, label: def.label, name: ('گزارش-' + def.label + '-' + E.today).replace(/ /g, '-') };  // داده: نامِ پروندهٔ خروجی
    var mode = viewMode();
    var html = '<div class="rf-head"><div><h2>گزارش‌ها</h2><p>از هستهٔ مشترکِ گزارش (همان عددهای برنامهٔ نصبی).</p></div>' +
      '<div class="rf-actions">' + keyBtn('class="btn btn-secondary btn-sm" data-act="print"', 'چاپ', 'global.print') +
      '<button type="button" class="btn btn-secondary btn-sm" data-act="pdf">خروجی PDF</button>' +
      '<button type="button" class="btn btn-secondary btn-sm" data-act="csv">CSV</button>' +
      keyBtn('class="btn btn-primary btn-sm" data-act="xlsx"', 'خروجی اکسل', 'global.export') + '</div></div>' +
      '<div class="rf-bar">' + seg('rtab', tabs.map(function (t) { return [t.id, t.label]; }), state.tab, 'نوعِ گزارش') +
      (def.ranged ? seg('span', B.SPANS.map(function (s) { return [s, B.SPAN_LABELS[s]]; }), state.span, 'بازه') : '') +
      '<span class="sp"></span>' + (phone() ? '' : seg('vm', [['card', 'نمای کارتی'], ['table', 'نمای جدولی']], mode, 'نمایش')) + '</div>' +
      periodBar(def, E) +
      (state.tab === 'sales' || state.tab === 'profit'
        ? '<div class="rf-bar">' + seg('dim', B.DIMS.map(function (d) { return [d, B.DIM_LABELS[d]]; }), state.dim, 'تفکیک') + '</div>' : '') +
      summary(v, E) +
      v.tables.map(function (t) {
        return (v.tables.length > 1 ? '<h3 class="rf-title">' + esc(t.title) + '</h3>' : '') +
          (t.id === 'heatmap.grid' ? gridHtml(t, v.heat) : mode === 'table' ? tableHtml(t, E) : cardsHtml(t, E));
      }).join('') + (mode === 'table' ? drillHtml(v, E) : '');
    el.innerHTML = html;
    if (!el.__rf) {
      el.__rf = true;
      el.addEventListener('click', onClick);
      document.addEventListener('keydown', onKey);
    }
  }

  /** RPT-13 — «دورهٔ قبل / بعد» (− / +) و «مقایسه» (Alt+C) برای زبانهٔ بازه‌دار؛ «همه» دوره ندارد */
  function periodBar(def, E) {
    if (!def.ranged || !unit()) return '';
    var r = rangeOf(E);
    var step = function (by, label, id) { return keyBtn('class="btn btn-secondary btn-sm" data-period="' + by + '"' + (can(E, by) ? '' : ' disabled'), label, id); };
    return '<div class="rf-bar rf-period" role="group" aria-label="دوره">' + step(-1, 'دورهٔ قبل', 'reports.prevPeriod') +
      '<span class="rf-range">' + esc(periodOf(r.from, r.to, E)) + '</span>' + step(1, 'دورهٔ بعد', 'reports.nextPeriod') +
      (comparable() ? '<span class="rf-seg">' + keyBtn('data-compare="1" aria-pressed="' + state.compare + '"', 'مقایسه با دورهٔ پیش', 'reports.compare') + '</span>' : '') + '</div>';
  }

  /** پیامِ صفحه‌خوان پس از کارِ کلید (دوره، مقایسه): یک ناحیهٔ `status` ِ ماندگار بیرون از صفحهٔ بازکشیده */
  function announce(html) {
    var fr = document.getElementById('rep-fresh'), live = document.getElementById('rep-fresh-live');
    if (!fr || !fr.parentNode) return;
    if (!live) {
      live = document.createElement('p');
      live.id = 'rep-fresh-live'; live.className = 'rf-sr';
      live.setAttribute('role', 'status'); live.setAttribute('aria-live', 'polite');
      fr.parentNode.insertBefore(live, fr.nextSibling);
    }
    live.innerHTML = html;
  }

  /** کشیدنِ دوباره با فوکوس روی همان دکمه؛ نبود یا خاموش ⇒ نخستین دکمهٔ روشنِ دوره، یا خودِ گزارش */
  function redraw(sel) {
    renderReports();
    var fr = document.getElementById('rep-fresh'), again = sel ? fr.querySelector(sel) : null;
    if (again && again.disabled) again = fr.querySelector('button[data-period]:not([disabled])');
    if (again) again.focus();
  }

  function step(E, by) {
    if (!can(E, by)) return false;
    state.shift += by; state.drill = null;
    return true;
  }
  function stepAndSay(E, by, sel) {
    if (!step(E, by)) return;
    redraw(sel);
    var r = rangeOf(E);
    announce('<span>' + esc(periodOf(r.from, r.to, E)) + '</span>');
  }
  function toggleCompare(sel) {
    state.compare = !state.compare;
    redraw(sel);
    announce('<span>مقایسه با دورهٔ پیش</span>: <span>' + (state.compare ? 'روشن' : 'خاموش') + '</span>');
  }
  function openDrill(table, index) {
    state.drill = { table: table, index: index };
    renderReports();
    var h = document.getElementById('rf-drill-h');
    if (h) h.focus();
  }
  function closeDrill() {
    var d = state.drill;
    state.drill = null;
    redraw(d ? 'button[data-drill="' + d.index + '"][data-table="' + d.table + '"]' : null);
  }

  /**
   *  RPT-13 — کلیدهای «گزارش‌ها» در وب، فقط با صفحهٔ گزارشِ باز و گزارشِ تازهٔ روشن، و نه در کادرِ نوشتن یا پنجرهٔ باز.
   *  هر کلید با `TohidKeymap.matches` (کدِ فیزیکی؛ چیدمانِ فارسی/پشتو هم): Ctrl/Cmd+P چاپ، Ctrl/Cmd+E اکسل، − و + دوره،
   *  Alt+C مقایسه، Esc بستنِ ریز. Enter روی نامِ ردیف همان دکمهٔ «ریز» است (کارِ خودِ مرورگر).
   */
  function onKey(e) {
    var K = keymap(), E = env(), fr = document.getElementById('rep-fresh'), pg = document.getElementById('page-reports');
    if (!K || !E || !fr || fr.hidden || !last || e.defaultPrevented || e.isComposing) return;
    if ((pg && !pg.classList.contains('active')) || document.querySelector('.modal-scrim.open')) return;
    var t = e.target;
    if (t && (t.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(t.tagName || ''))) return;
    var def = tabsFor(E).filter(function (x) { return x.id === state.tab; })[0] || {};
    var ranged = !!def.ranged && !!unit(), sel = t && t.closest && t.closest('#rep-fresh') && t.hasAttribute && t.hasAttribute('data-period')
      ? 'button[data-period="' + t.getAttribute('data-period') + '"]' : null;
    var run;
    if (K.matches(e, 'global.print')) run = function () { exportAct('print', E); };
    else if (K.matches(e, 'global.export')) run = function () { exportAct('xlsx', E); };
    else if (state.drill && K.matches(e, 'global.back')) run = closeDrill;
    else if (ranged && K.matches(e, 'reports.prevPeriod')) run = function () { stepAndSay(E, -1, sel || 'button[data-period="-1"]'); };
    else if (ranged && K.matches(e, 'reports.nextPeriod')) run = function () { stepAndSay(E, 1, sel || 'button[data-period="1"]'); };
    else if (comparable() && K.matches(e, 'reports.compare')) run = function () { toggleCompare('button[data-compare]'); };
    if (!run) return;
    e.preventDefault();
    run();
  }

  /** چاپ، CSV، اکسل و PDF از همان برگه (RPT-11)؛ دکمه یا Ctrl+P / Ctrl+E */
  function exportAct(act, E, btn) {
    if (!last || !E) return;
    var rows = exportRows(last.v.tables);
    if (act === 'print') { E.printRows(rows, last.name); return; }
    if (rows.length <= 1) { if (E.showToast) E.showToast('در این گزارش داده‌ای برای خروجی وجود ندارد', 'warning'); return; }
    if (act === 'csv') E.downloadCsv(last.name + '.csv', rows);
    else if (act === 'xlsx') excel(rows, last.name, E);
    else if (act === 'pdf' && btn) pdf(rows, last, E, btn);
  }

  function onClick(e) {
    var b = e.target.closest('button');
    if (!b) return;
    var E = env();
    if (b.hasAttribute('data-rtab')) { state.tab = b.getAttribute('data-rtab'); state.sort = null; state.drill = null; }
    else if (b.hasAttribute('data-span')) { state.span = b.getAttribute('data-span'); state.shift = 0; state.drill = null; }
    else if (b.hasAttribute('data-dim')) { state.dim = b.getAttribute('data-dim'); state.sort = null; state.drill = null; }
    else if (b.hasAttribute('data-period')) { if (E) stepAndSay(E, +b.getAttribute('data-period'), 'button[data-period="' + b.getAttribute('data-period') + '"]'); return; }
    else if (b.hasAttribute('data-compare')) { toggleCompare('button[data-compare]'); return; }
    else if (b.hasAttribute('data-drill')) { openDrill(b.getAttribute('data-table'), +b.getAttribute('data-drill')); return; }
    else if (b.getAttribute('data-act') === 'drill-close') { closeDrill(); return; }
    else if (b.hasAttribute('data-vm')) put(VM_KEY, b.getAttribute('data-vm'));
    else if (b.hasAttribute('data-cost')) put(COST_KEY, b.getAttribute('data-cost'));
    else if (b.hasAttribute('data-sort')) {
      var col = +b.getAttribute('data-sort'), id = b.getAttribute('data-table');
      var t = last && last.v.tables.filter(function (x) { return x.id === id; })[0];
      var textCol = t && t.columns[col].kind === 'text';
      state.sort = state.sort && state.sort.id === id && state.sort.col === col
        ? { id: id, col: col, desc: !state.sort.desc } : { id: id, col: col, desc: !textCol };
    } else if (b.hasAttribute('data-kpi') || b.hasAttribute('data-pill')) { openPreset(b); }
    else if (b.hasAttribute('data-act')) { exportAct(b.getAttribute('data-act'), E, b); return; }
    else return;
    //  بعد از کشیدنِ دوباره، فوکوس روی همان دکمه می‌ماند (کار با کیبورد)
    var attr = ['data-rtab', 'data-span', 'data-dim', 'data-vm', 'data-cost', 'data-sort'].filter(function (a) { return b.hasAttribute(a); })[0];
    var sel = attr ? 'button[' + attr + '="' + b.getAttribute(attr) + '"]' +
      (attr === 'data-sort' ? '[data-table="' + b.getAttribute('data-table') + '"]' : '') : null;
    renderReports();
    var again = sel ? document.querySelector('#rep-fresh ' + sel) : null;
    if (again) again.focus();
  }

  /* ---------------- RPT-11: اکسل و PDF ---------------- */

  function saveBlob(blob, filename) {
    var url = URL.createObjectURL(blob), a = document.createElement('a');
    a.href = url; a.download = filename;
    document.body.appendChild(a); a.click(); a.remove();
    setTimeout(function () { URL.revokeObjectURL(url); }, 10000);
  }
  function failed(E) { if (E.showToast) E.showToast('فایل ساخته نشد', 'danger'); }

  /** همان برگهٔ CSV ⇒ ‎.xlsx‎ ِ راست‌به‌چپ، سرستونِ ثابت و عددِ عدد (`Xlsx.of` ِ برنامه) */
  function excel(rows, name, E) {
    var X = window.TohidOwnerExport;
    if (!X || typeof X.report !== 'function') { failed(E); return; }
    X.report(name, rows).then(function (u8) {
      saveBlob(new Blob([u8], { type: X.XLSX_MIME }), name + '.xlsx');
    }, function () { failed(E); });
  }

  var pdfLoad = null;
  /** `report-pdf.js` فقط با نخستین «PDF» (هر بارِ صفحه یک بار)؛ نبودش ⇒ پیامِ خطا، نه صفحهٔ خراب */
  function pdfLib() {
    if (window.TohidReportPdf) return Promise.resolve(window.TohidReportPdf);
    if (!pdfLoad) {
      pdfLoad = new Promise(function (ok, no) {
        var sc = document.createElement('script');
        sc.src = PDF_SRC;
        sc.onload = function () { if (window.TohidReportPdf) ok(window.TohidReportPdf); else no(new Error('pdf')); };
        sc.onerror = function () { pdfLoad = null; no(new Error('pdf')); };
        document.head.appendChild(sc);
      });
    }
    return pdfLoad;
  }

  /** PDF ِ A4 ِ سیاه‌وسفید با قلمِ همین صفحه (`report-pdf.js`)؛ دکمه تا پایانِ ساختن خاموش */
  function pdf(rows, at, E, btn) {
    btn.disabled = true;
    btn.setAttribute('aria-busy', 'true');
    var done = function () { btn.disabled = false; btn.removeAttribute('aria-busy'); };
    var fonts = document.fonts && document.fonts.ready ? document.fonts.ready : Promise.resolve();
    pdfLib().then(function (P) {
      return fonts.then(function () {
        var font = window.getComputedStyle(document.body).fontFamily;
        try {
          saveBlob(P.blob(rows, { title: 'گزارش‌ها — ' + at.label, sub: at.name, font: font }), at.name + '.pdf'); // داده: عنوانِ درونِ پرونده، همان برگهٔ CSV
        } catch (e) {
          if (e && e.code === 'too_many_pages') {
            if (E.showToast) E.showToast('PDF ساخته نشد: گزارش بیش از ' + e.max + ' صفحه است. بازهٔ کوتاه‌تری بگیرید یا خروجیِ Excel بگیرید.', 'danger');
          } else failed(E);
        }
      });
    }).then(done, function () { done(); failed(E); });
  }

  /** صفحهٔ گزارش: روشن ⇒ گزارشِ تازه؛ خاموش ⇒ همان گزارشِ امروز */
  function syncReports() {
    var on = enabled();
    var sw = document.getElementById('rep-fresh-toggle');
    var legacy = document.getElementById('rep-legacy');
    var fresh = document.getElementById('rep-fresh');
    if (sw) {
      sw.checked = on;
      if (!sw.__rf) { sw.__rf = true; sw.addEventListener('change', function () { setEnabled(sw.checked); }); }
    }
    if (legacy) legacy.hidden = on;
    if (fresh) {
      fresh.hidden = !on;
      if (on) renderReports(); else fresh.innerHTML = '';
    }
  }

  function setEnabled(on) {
    put(KEY, on ? '1' : '0');
    syncReports();
    renderHome();
  }

  //  index.html ِ یخ‌زده برچسبِ این هسته‌ها را ندارد ⇒ همین‌جا؛ رسید ⇒ صفحهٔ بازِ گزارش زبانه/دکمه/کلید را می‌گیرد:
  //  INV-16 «ارزش موجودی»، RPT-13 دوره و میان‌برها (keymap.js ِ ساختهٔ gen-keymap.mjs)، RPT-08 «ساعت و روز»
  [[value, 'license/stock-value-core.js?v=1'], [period, 'license/report-period-core.js?v=1'], [keymap, 'license/keymap.js?v=1'],
    [heat, 'license/sales-heatmap-core.js?v=1']].forEach(function (p) {
    if (p[0]() || !document.head) return;
    var sc = document.createElement('script');
    sc.src = p[1];
    sc.onload = function () { var fr = document.getElementById('rep-fresh'); if (fr && !fr.hidden) renderReports(); };
    document.head.appendChild(sc);
  });

  window.TohidReportUi = {
    enabled: enabled,
    setEnabled: setEnabled,
    renderHome: renderHome,
    syncReports: syncReports,
    renderReports: renderReports,
    state: state,
  };
})();
