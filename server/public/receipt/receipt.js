/*
 * فاکتورِ عمومی — `/r/<نشانِ دکان>/<توکن>`. داده از `/api/receipt/…` می‌آید و همین‌جا
 * فقط نشان داده می‌شود (عددها از سرور قالب‌خورده‌اند). هر متن با textContent
 * نوشته می‌شود، هرگز HTML. بی کوکی، بی ارجاع‌دهنده، بی کش.
 */
'use strict';
(function () {
  var MSG = {
    missing: 'هنوز ثبت نشده، کمی بعد دوباره',
    busy: 'درخواست‌ها زیاد بود؛ کمی بعد دوباره',
    offline: 'اتصال برقرار نشد؛ دوباره',
    no: 'شمارهٔ فاکتور',
    date: 'تاریخ',
    cashier: 'فروشنده',
    kind: 'نوع',
    subtotal: 'جمع اقلام',
    total: 'مبلغ نهایی',
    count: 'تعداد اقلام',
    rows: 'ردیف',
    pieces: 'عدد',
    returned: 'مرجوعی',
  };

  var $ = function (id) { return document.getElementById(id); };

  function show(id) {
    ['loading', 'missing', 'receipt'].forEach(function (x) { $(x).hidden = x !== id; });
  }

  function text(id, value) {
    var node = $(id);
    node.textContent = value || '';
    node.hidden = !value;
  }

  function bdi(value, dir) {
    var b = document.createElement('bdi');
    if (dir) b.dir = dir;
    b.textContent = value;
    return b;
  }

  function row(list, label, value, cls) {
    var wrap = document.createElement('div');
    wrap.className = 'slip-row' + (cls ? ' ' + cls : '');
    var dt = document.createElement('dt');
    dt.textContent = label;
    var dd = document.createElement('dd');
    dd.appendChild(bdi(value));
    wrap.appendChild(dt);
    wrap.appendChild(dd);
    list.appendChild(wrap);
  }

  function item(list, it) {
    var li = document.createElement('li');
    var name = document.createElement('div');
    name.className = 'slip-item-name';
    name.textContent = it.name;
    var line = document.createElement('div');
    line.className = 'slip-item-line';
    var detail = document.createElement('span');
    detail.appendChild(bdi(it.qty + (it.unit ? ' ' + it.unit : '') + ' × ' + it.unitPrice));
    var total = document.createElement('span');
    total.className = 'slip-item-total';
    total.appendChild(bdi(it.total));
    line.appendChild(detail);
    line.appendChild(total);
    li.appendChild(name);
    li.appendChild(line);
    if (it.returned) {
      var ret = document.createElement('div');
      ret.className = 'slip-item-returned';
      ret.textContent = MSG.returned + ': ' + it.returned;
      li.appendChild(ret);
    }
    list.appendChild(li);
  }

  function clear(id) {
    var node = $(id);
    while (node.firstChild) node.removeChild(node.firstChild);
    return node;
  }

  function render(r) {
    var logo = $('shop-logo');
    //  فقط تصویرِ درون‌خطیِ خودِ سرور (CSP هم جز data: و همین مبدأ چیزی نمی‌پذیرد)
    var src = typeof r.shop.logo === 'string' && /^data:image\/(png|jpeg|webp);base64,/.test(r.shop.logo) ? r.shop.logo : '';
    if (src) { logo.src = src; logo.hidden = false; } else { logo.hidden = true; }
    text('shop-name', r.shop.name);
    text('shop-address', r.shop.address);
    var phone = $('shop-phone');
    clear('shop-phone');
    if (r.shop.phone) phone.appendChild(bdi(r.shop.phone, 'ltr'));
    phone.hidden = !r.shop.phone;
    $('cancelled').hidden = !r.cancelled;

    var meta = clear('meta');
    if (r.no) row(meta, MSG.no, '#' + r.no);
    row(meta, MSG.date, [r.date, r.time].filter(Boolean).join(' — '));
    if (r.cashier) row(meta, MSG.cashier, r.cashier);
    row(meta, MSG.kind, r.kind);

    var items = clear('items');
    r.items.forEach(function (it) { item(items, it); });
    $('count').textContent = MSG.count + ': ' + r.count.lines + ' ' + MSG.rows +
      (r.count.qty ? ' · ' + r.count.qty + ' ' + MSG.pieces : '');

    var totals = clear('totals');
    row(totals, MSG.subtotal, r.subtotal);
    r.discounts.forEach(function (d) { row(totals, d.label, '−' + d.amount); });
    row(totals, MSG.total, r.total, 'is-total');

    var pays = clear('payments');
    r.payments.forEach(function (p) { row(pays, p.label, p.amount); });
    if (r.remaining) row(pays, r.remaining.label, r.remaining.amount);

    text('footer', r.shop.footer);
    document.title = r.shop.name ? 'فاکتور — ' + r.shop.name : 'فاکتور';
    show('receipt');
  }

  function fail(message) {
    $('missing-text').textContent = message;
    show('missing');
  }

  function load() {
    var m = /^\/r\/([0-9A-Za-z]{8})\/([0-9A-Za-z]{26})\/?$/.exec(location.pathname);
    if (!m) return fail(MSG.missing);
    show('loading');
    fetch('/api/receipt/' + m[1] + '/' + m[2], { credentials: 'omit', cache: 'no-store', referrerPolicy: 'no-referrer' })
      .then(function (res) {
        if (res.status === 429) return fail(MSG.busy);
        return res.json().then(function (body) {
          if (res.ok && body && body.receipt) return render(body.receipt);
          fail((body && body.error && body.error.message) || MSG.missing);
        });
      })
      .catch(function () { fail(MSG.offline); });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('retry').addEventListener('click', load);
    load();
  });
})();
