/* ==========================================================
   فروشگاه من | دفترِ قرض‌داران — v3 §22.2 (مرحلهٔ A، بندِ ۵) و §21.11
   ----------------------------------------------------------
   هم‌زادِ `android-native/.../data/DebtBook.kt`. هر دو با یک فایلِ بردارِ
   مرجع (`license/test-vectors/debt-book-v1.json`) سنجیده می‌شوند؛ یکی را
   عوض کنی، دیگری هم.

   برای هر برد: اقلامِ فاکتورش (کالا، مقدار/وزن، واحد، قیمتِ همان لحظه)،
   پرداخت‌هایی که رویش نشسته، باقیِ همان برد، سررسید و ماندهٔ پس از هر رویداد؛
   برای هر رسید: پیش و پس و این‌که بابتِ کدام برد بوده. و رسیدِ چاپی.

   هیچ چیزی ذخیره نمی‌کند؛ همه از `transactions`/`sales`/`saleItems` حساب
   می‌شود. پول به واحدِ کوچک با `license/money.js` (TohidMoney)؛ عددِ اعشاریِ
   دفتر فقط در مرز (`minor`) خوانده می‌شود.

   تخصیصِ پرداخت: رسیدی که فاکتوری را نام می‌برد اول بردِ همان فاکتور را
   می‌بندد؛ بقیه از قدیمی‌ترین بردِ باز؛ آنچه ماند پیش‌پرداخت است و روی بردِ
   بعدی می‌نشیند. فقط نمایش است؛ چیزی در دفتر جابه‌جا نمی‌شود.
   ========================================================== */
(function (factory) {
  'use strict';
  var M = (typeof module !== 'undefined' && module.exports)
    ? require('./money.js')
    : (typeof window !== 'undefined' ? window.TohidMoney : globalThis.TohidMoney);
  var api = factory(M);
  if (typeof module !== 'undefined' && module.exports) module.exports = api;
  if (typeof window !== 'undefined') window.TohidDebtBook = api;
  else if (typeof globalThis !== 'undefined') globalThis.TohidDebtBook = api;
})(function (M) {
  'use strict';

  var CUR = M.currency('AFN');
  var ZERO = M.money(0, CUR);

  function num(v) { var n = Number(v); return isFinite(n) ? n : 0; }
  /* عددِ اعشاریِ دفتر ⇒ Money (واحدِ کوچک) */
  function minorOf(v) { return M.moneyFromLegacy(num(v), CUR); }
  function min(a, b) { return M.compare(a, b) <= 0 ? a : b; }
  function pos(a) { return a.minor > 0; }

  /* «فاکتور #۱۲» یا «فاکتور #B1-12» در شرحِ ردیف — همان `CustomerEngine.invoiceLabelOf` */
  var INVOICE = /فاکتور\s*#\s*(?:([A-Z][1-9][0-9]{0,3})-)?(\d+)/;
  function invoiceLabelOf(notes) {
    var m = INVOICE.exec(String(notes || ''));
    if (!m) return null;
    var n = parseInt(m[2], 10);
    if (!isFinite(n)) return null;
    return m[1] ? m[1] + '-' + n : String(n);
  }
  /* شمارهٔ نمایشیِ فاکتور — همان `invoiceLabel`ِ index.html و `Sale.invoiceLabel` */
  function saleLabel(s) {
    if (!s || s.invoiceNumber == null) return null;
    var series = typeof s.series === 'string' ? s.series.trim() : '';
    return series ? series + '-' + s.invoiceNumber : String(s.invoiceNumber);
  }
  /* نامِ قلم — همان `SalesEngine.itemName` */
  function itemName(data, item) {
    if (!item.productId) return item.name || 'قلم آزاد';
    var p = (data.products || []).find(function (x) { return x.id === item.productId; });
    return p ? p.name : (item.name || '(محصول حذف‌شده)');
  }

  function order(a, b) {
    var da = String(a.date || ''), db = String(b.date || '');
    if (da < db) return -1;
    if (da > db) return 1;
    return num(a.createdAt) - num(b.createdAt);
  }

  function creditOf(debtor, balance, extra) {
    var lim = debtor && debtor.creditLimit;
    if (lim == null || typeof lim !== 'number' || !isFinite(lim) || lim < 0) return null;
    var limit = minorOf(lim);
    var after = M.add(balance, extra);
    var over = M.compare(after, limit) > 0;
    return {
      limitMinor: limit.minor,
      balanceMinor: balance.minor,
      afterMinor: after.minor,
      over: over,
      excessMinor: over ? M.sub(after, limit).minor : 0,
      headroomMinor: M.compare(limit, balance) > 0 ? M.sub(limit, balance).minor : 0,
    };
  }

  function book(data, debtorId, today) {
    var debtor = (data.debtors || []).find(function (x) { return x.id === debtorId; });
    if (!debtor) return null;
    var mine = (data.transactions || []).filter(function (t) { return t.debtorId === debtorId; })
      .slice().sort(order);
    var byLabel = new Map();
    (data.sales || []).forEach(function (s) {
      if (s.debtorId === debtorId && s.invoiceNumber != null) byLabel.set(saleLabel(s), s);
    });
    var itemsBySale = new Map();
    (data.saleItems || []).forEach(function (i) {
      if (!itemsBySale.has(i.saleId)) itemsBySale.set(i.saleId, []);
      itemsBySale.get(i.saleId).push(i);
    });
    var units = new Map();
    (data.products || []).forEach(function (p) { units.set(p.id, p.unit || ''); });

    var open = new Map();          // بردِ باز ⇒ باقی (Money)، به ترتیبِ زمان
    var unapplied = [];            // [رسید، باقی]
    var giveOfSale = new Map();
    var links = new Map();
    function link(receiveId, giveId, amt) {
      if (!links.has(giveId)) links.set(giveId, []);
      if (!links.has(receiveId)) links.set(receiveId, []);
      links.get(giveId).push([receiveId, amt.minor]);
      links.get(receiveId).push([giveId, amt.minor]);
    }

    var run = ZERO;
    var raws = mine.map(function (tx) {
      var give = tx.type === 'give';
      var amt = minorOf(tx.amount);
      var before = run;
      run = give ? M.add(run, amt) : M.sub(run, amt);
      var label = invoiceLabelOf(tx.notes);
      var sale = label ? (byLabel.get(label) || null) : null;

      if (pos(amt)) {
        if (give) {
          if (sale && !giveOfSale.has(sale.id)) giveOfSale.set(sale.id, tx.id);
          var left = amt;
          while (pos(left) && unapplied.length) {
            var head = unapplied.shift();
            var used = min(head[1], left);
            link(head[0], tx.id, used);
            left = M.sub(left, used);
            if (M.compare(head[1], used) > 0) unapplied.unshift([head[0], M.sub(head[1], used)]);
          }
          open.set(tx.id, left);
        } else {
          var pay = amt;
          var settle = function (gid) {
            var o = open.get(gid);
            if (!o || !pos(o) || !pos(pay)) return;
            var u = min(o, pay);
            open.set(gid, M.sub(o, u));
            link(tx.id, gid, u);
            pay = M.sub(pay, u);
          };
          if (sale && giveOfSale.has(sale.id)) settle(giveOfSale.get(sale.id));
          Array.from(open.keys()).forEach(function (gid) { if (pos(pay)) settle(gid); });
          if (pos(pay)) unapplied.push([tx.id, pay]);
        }
      }
      return { tx: tx, amount: amt, before: before, after: run, sale: sale, label: label };
    });

    var leftOver = new Map();
    unapplied.forEach(function (u) { leftOver.set(u[0], M.add(leftOver.get(u[0]) || ZERO, u[1])); });

    var entries = raws.map(function (r, i) {
      var give = r.tx.type === 'give';
      var due = give && typeof r.tx.dueDate === 'string' && r.tx.dueDate.trim() ? r.tx.dueDate.trim() : null;
      var left = give ? (open.get(r.tx.id) || ZERO) : (leftOver.get(r.tx.id) || ZERO);
      var lines = (give && r.sale) ? (itemsBySale.get(r.sale.id) || []).map(function (it) {
        return {
          name: itemName(data, it),
          quantity: num(it.quantity),
          unit: units.get(it.productId) || '',
          unitPriceMinor: minorOf(it.unitPrice).minor,
          totalMinor: minorOf(it.totalPrice).minor,
          returned: num(it.returnedQty),
        };
      }) : [];
      return {
        tx: r.tx,
        row: i + 1,
        give: give,
        amountMinor: r.amount.minor,
        balanceBeforeMinor: r.before.minor,
        balanceAfterMinor: r.after.minor,
        sale: r.sale,
        invoiceLabel: r.label,
        lines: lines,
        dueDate: due,
        openMinor: left.minor,
        allocations: links.get(r.tx.id) || [],
        overdue: !!(give && due && due < today && pos(left)),
        paid: give && !pos(left),
      };
    });

    var late = entries.filter(function (e) { return e.overdue; });
    var upcoming = entries.filter(function (e) { return e.give && e.openMinor > 0 && e.dueDate && e.dueDate >= today; })
      .sort(function (a, b) { return a.dueDate < b.dueDate ? -1 : a.dueDate > b.dueDate ? 1 : 0; });
    return {
      debtor: debtor,
      entries: entries,
      balanceMinor: run.minor,
      overdue: late,
      overdueMinor: late.reduce(function (s, e) { return M.add(s, M.money(e.openMinor, CUR)); }, ZERO).minor,
      nextDue: upcoming[0] || null,
      credit: creditOf(debtor, run, ZERO),
      entry: function (id) { return entries.find(function (e) { return e.tx.id === id; }) || null; },
    };
  }

  /* اگر این مبلغِ تازه (واحدِ کوچک) برده شود، از سقف می‌گذرد؟ `null` یعنی بی سقف */
  function creditCheck(data, debtorId, extraMinor) {
    var debtor = (data.debtors || []).find(function (x) { return x.id === debtorId; });
    if (!debtor) return null;
    var bal = (data.transactions || []).filter(function (t) { return t.debtorId === debtorId; })
      .reduce(function (acc, t) { return t.type === 'give' ? M.add(acc, minorOf(t.amount)) : M.sub(acc, minorOf(t.amount)); }, ZERO);
    return creditOf(debtor, bal, M.money(extraMinor, CUR));
  }

  var ISO = /^(\d{4})-(\d{2})-(\d{2})$/;
  function isIsoDate(s) {
    var m = ISO.exec(String(s || ''));
    if (!m) return false;
    var mo = +m[2], d = +m[3];
    return mo >= 1 && mo <= 12 && d >= 1 && d <= 31;
  }

  /*
   * رسیدِ یک ردیف — همان ردیف‌های `DebtBook.receipt`ِ کاتلین.
   * fmt = { money(minor), date(iso), qty(n), state(minor), phone?(p) } — صفحه قالبِ خودش را می‌دهد.
   */
  function receipt(data, debtorId, txId, storeName, today, fmt) {
    var b = book(data, debtorId, today);
    if (!b) return null;
    var e = b.entry(txId);
    if (!e) return null;
    var af = ' افغانی';
    var rows = [];
    rows.push(['مشتری', b.debtor.name || '']);
    if (b.debtor.phone) rows.push(['تلفن', fmt.phone ? fmt.phone(b.debtor.phone) : b.debtor.phone]);
    rows.push(['تاریخ', fmt.date(e.tx.date)]);
    if (e.invoiceLabel) rows.push(['فاکتور', '#' + e.invoiceLabel]);
    if (e.give) {
      var s = e.sale;
      if (s && e.lines.length) {
        e.lines.forEach(function (l) {
          var unit = l.unit ? ' ' + l.unit : '';
          rows.push([l.name + ' — ' + fmt.qty(l.quantity) + unit + ' × ' + fmt.money(l.unitPriceMinor), fmt.money(l.totalMinor)]);
          if (l.returned > 0) rows.push(['   مرجوعی: ' + fmt.qty(l.returned) + unit, '']);
        });
        rows.push(['جمعِ اقلام', fmt.money(minorOf(s.total).minor) + af]);
        if (num(s.discount) > 0) rows.push(['تخفیف', fmt.money(minorOf(s.discount).minor) + af]);
        rows.push(['قابلِ پرداخت', fmt.money(minorOf(s.finalTotal).minor) + af]);
        rows.push(['پرداختِ همان لحظه', fmt.money(minorOf(s.paidAmount).minor) + af]);
      }
      rows.push(['قرضِ این ردیف', fmt.money(e.amountMinor) + af]);
      if (e.tx.notes) rows.push(['شرح', e.tx.notes]);
      rows.push(['سررسید', e.dueDate ? fmt.date(e.dueDate) : 'ندارد']);
      e.allocations.forEach(function (a) {
        var r = b.entry(a[0]);
        rows.push([('پرداخت ' + (r ? fmt.date(r.tx.date) : '')).trim(), fmt.money(a[1]) + af]);
      });
      rows.push(['باقیِ این قرض', e.paid ? 'تسویه شد' : fmt.money(e.openMinor) + af]);
      if (e.overdue) rows.push(['وضعیت', 'سررسید گذشته']);
    } else {
      rows.push(['مبلغِ دریافتی', fmt.money(e.amountMinor) + af]);
      if (e.tx.notes) rows.push(['شرح', e.tx.notes]);
      rows.push(['مانده پیش از دریافت', fmt.state(e.balanceBeforeMinor)]);
      e.allocations.forEach(function (a) {
        var g = b.entry(a[0]);
        var what = g && g.invoiceLabel ? 'فاکتور #' + g.invoiceLabel : ('برد ' + (g ? fmt.date(g.tx.date) : '')).trim();
        rows.push(['بابتِ ' + what, fmt.money(a[1]) + af]);
      });
      if (e.openMinor > 0) rows.push(['پیش‌پرداخت (روی قرضِ بعدی)', fmt.money(e.openMinor) + af]);
    }
    rows.push(['مانده پس از این ردیف', fmt.state(e.balanceAfterMinor)]);
    rows.push(['ماندهٔ امروزِ حساب', fmt.state(b.balanceMinor)]);
    var kind = e.give ? 'رسیدِ قرض' : 'رسیدِ دریافت';
    var safe = String(b.debtor.name || '').replace(/[\\/:*?"<>|]/g, ' ').trim() || 'مشتری';
    return {
      title: (String(storeName || '').trim() || 'فروشگاه') + ' — ' + kind,
      name: kind + '-' + safe + '-' + e.tx.date + '-' + e.row,
      rows: rows,
    };
  }

  function receiptText(r) {
    return [r.title].concat(r.rows.map(function (kv) { return kv[1] === '' ? kv[0] : kv[0] + ': ' + kv[1]; })).join('\n');
  }

  return {
    VERSION: 1,
    minor: function (v) { return minorOf(v).minor; },
    invoiceLabelOf: invoiceLabelOf,
    saleLabel: saleLabel,
    itemName: itemName,
    book: book,
    creditCheck: creditCheck,
    isIsoDate: isIsoDate,
    receipt: receipt,
    receiptText: receiptText,
  };
});
