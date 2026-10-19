/*
 * ویترینِ اینترنتی — صفحهٔ مشتری (فاز ۴؛ WEB، CHK، بخش‌های ۲۳، ۳۱.۱ و ۳۲.۳).
 *
 *   دیدن و سبد ── بی حساب (۲۳.۲). سبد فقط در همین مرورگر: شناسه، نام، مقدار، قیمت.
 *   ثبت ──────── فقط با شمارهٔ تأییدشده (کدِ یک‌بارمصرف) و کلیدِ یکتایی؛ کلید
 *                 تا وقتی بدنه همان است می‌ماند ⇒ زدنِ دوباره پس از قطعِ شبکه
 *                 سفارشِ دوم نمی‌سازد (CHK-19).
 *   قیمت ─────── را سرور می‌گوید؛ جمعِ دیده‌شده هم فرستاده می‌شود تا اگر عوض
 *                 شده بود، سفارش ثبت نشود و تغییر نشان داده شود (CHK-17).
 *   ⛔ بی اسکریپتِ درون‌خطی و بی `style=` (CSP)؛ همهٔ متن با textContent.
 */
(function () {
  'use strict';

  var $ = function (id) { return document.getElementById(id); };
  var fa = function (n, d) { return Number(n).toLocaleString('fa-AF', { maximumFractionDigits: d === undefined ? 2 : d }); };
  var money = function (minor) { return fa(minor / 100) + ' افغانی'; };
  var qty = function (milli) { return fa(milli / 1000, 3); };
  var store = null;
  var slug = slugOfPage();
  var KEY_CART = 'store-cart:' + slug;
  var KEY_ORDERS = 'store-orders:' + slug;
  var KEY_TICKET = 'store-ticket';
  var KEY_PENDING = 'store-pending:' + slug;

  function slugOfPage() {
    var parts = location.pathname.split('/').filter(Boolean);
    if (parts[0] === 'store' && parts[1]) return parts[1].toLowerCase();
    return location.hostname.split('.')[0].toLowerCase();
  }

  // ── حافظهٔ مرورگر (ممکن است بسته باشد؛ صفحه بی آن هم کار می‌کند) ──
  function load(key, def) {
    try { var v = JSON.parse(localStorage.getItem(key)); return v === null ? def : v; } catch (e) { return def; }
  }
  function save(key, v) { try { localStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ } }
  function loadS(key) { try { return JSON.parse(sessionStorage.getItem(key)); } catch (e) { return null; } }
  function saveS(key, v) { try { sessionStorage.setItem(key, JSON.stringify(v)); } catch (e) { /* بی حافظه */ } }

  var cart = load(KEY_CART, {});
  function saveCart() { save(KEY_CART, cart); renderCartBar(); }

  // ── API ──
  function api(path, opts) {
    opts = opts || {};
    var headers = { 'Accept': 'application/json' };
    if (opts.body) headers['Content-Type'] = 'application/json';
    var t = ticket();
    if (t) headers['X-Store-Ticket'] = t.ticket;
    if (opts.key) headers['Idempotency-Key'] = opts.key;
    return fetch('/api/store/' + encodeURIComponent(slug) + path, {
      method: opts.method || 'GET', headers: headers, body: opts.body ? JSON.stringify(opts.body) : undefined,
      credentials: 'omit', cache: 'no-store',
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (res.ok) return body;
        var e = (body && body.error) || {};
        var err = new Error(e.message || 'خطا');
        err.status = res.status; err.code = e.code || ''; err.data = e;
        throw err;
      });
    }, function () {
      var err = new Error('اتصال برقرار نشد؛ اینترنت را ببینید و دوباره بزنید');
      err.status = 0; err.code = 'network';
      throw err;
    });
  }

  function ticket() {
    var t = load(KEY_TICKET, null);
    if (!t || !t.ticket || !(t.expiresAt > Date.now())) return null;
    return t;
  }

  function show(id) {
    ['view-missing', 'view-catalog', 'view-checkout', 'view-order', 'view-orders'].forEach(function (v) {
      $(v).hidden = v !== id;
    });
    $('boot').hidden = true;
    $('cart-bar').hidden = id !== 'view-catalog' || count() === 0;
    window.scrollTo(0, 0);
  }

  function note(el, kind, text) {
    el.className = 'note ' + kind;
    el.textContent = text;
    el.hidden = !text;
  }

  // ── وضعیتِ دکان (۳۱.۱) — بی زمانِ ساختگی ──
  function clock(at, tz) {
    try {
      return new Intl.DateTimeFormat('fa-AF', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).format(new Date(at));
    } catch (e) { return ''; }
  }
  function hm(min) { return fa(Math.floor(min / 60), 0).padStart(2, '۰') + ':' + fa(min % 60, 0).padStart(2, '۰'); }

  var BADGE = {
    open_online: ['باز است · آنلاین', 'ok'],
    open_unconfirmed: ['باز است · اتصال تأیید نشده', 'warn'],
    declared_until: ['', 'ok'],
    closed: ['بسته است', 'off'],
    offline: ['موقتاً آفلاین', 'off'],
  };
  var ACCEPT = {
    accepting: 'سفارش همین حالا به دکان می‌رسد.',
    delayed: 'سفارش ثبت می‌شود، ولی ممکن است دیرتر بررسی شود.',
    later: 'سفارش ثبت می‌شود و پس از باز شدنِ دکان بررسی می‌شود.',
  };

  function renderStatus(s) {
    var box = $('store-status');
    box.textContent = '';
    if (!s) return;
    var b = BADGE[s.badge] || ['', 'warn'];
    var badge = document.createElement('span');
    badge.className = 'badge ' + b[1];
    badge.textContent = s.badge === 'declared_until'
      ? 'تا ساعتِ ' + clock(s.declaredUntil, s.timezone) + ' آنلاین اعلام شده'
      : b[0];
    box.appendChild(badge);
    var bits = [];
    bits.push(s.lastConfirmedAt
      ? 'آخرین تأییدِ اتصال: ' + clock(s.lastConfirmedAt, s.timezone)
      : 'اتصالِ دکان هنوز تأیید نشده');
    var hours = s.holiday ? 'امروز تعطیل' : (s.today && s.today.length
      ? 'امروز: ' + s.today.map(function (r) { return hm(r[0]) + '–' + hm(r[1]); }).join('، ')
      : (s.hoursSet ? 'امروز تعطیل' : ''));
    bits.push('ساعتِ دکان: ' + fa(Number(s.localTime.slice(0, 2)), 0).padStart(2, '۰') + ':' + fa(Number(s.localTime.slice(3)), 0).padStart(2, '۰') + (hours ? ' · ' + hours : ''));
    bits.forEach(function (t) { var x = document.createElement('span'); x.className = 'muted'; x.textContent = t; box.appendChild(x); });
    var acc = document.createElement('span');
    acc.textContent = ACCEPT[s.orderAcceptance] || '';
    box.appendChild(acc);
    if (s.notice) { var n = document.createElement('span'); n.textContent = s.notice; box.appendChild(n); }
  }

  // ── کاتالوگ ──
  var catalog = { q: '', category: '', cursor: null, items: {} };
  var searchTimer = 0;

  function renderCats() {
    var box = $('cats');
    box.textContent = '';
    [''].concat(store.categories || []).forEach(function (c) {
      var b = document.createElement('button');
      b.type = 'button'; b.className = 'chip'; b.textContent = c || 'همه';
      b.setAttribute('aria-pressed', String(c === catalog.category));
      b.addEventListener('click', function () { catalog.category = c; renderCats(); loadProducts(false); });
      box.appendChild(b);
    });
    box.hidden = !(store.categories || []).length;
  }

  function loadProducts(more) {
    var qs = '?limit=40' + (catalog.q ? '&q=' + encodeURIComponent(catalog.q) : '') +
      (catalog.category ? '&category=' + encodeURIComponent(catalog.category) : '') +
      (more && catalog.cursor ? '&cursor=' + encodeURIComponent(catalog.cursor) : '');
    return api('/products' + qs).then(function (r) {
      if (!more) $('products').textContent = '';
      r.items.forEach(function (p) { catalog.items[p.id] = p; $('products').appendChild(card(p)); });
      catalog.cursor = r.nextCursor;
      $('more').hidden = !r.nextCursor;
      var none = !more && !r.items.length;
      $('catalog-empty').hidden = !none;
      $('catalog-empty-text').textContent = catalog.q ? 'برای «' + catalog.q + '» چیزی پیدا نشد.' : 'هنوز کالایی در این ویترین نیست.';
      $('clear-search').hidden = !catalog.q && !catalog.category;
    }).catch(function (e) {
      $('catalog-empty').hidden = false;
      $('catalog-empty-text').textContent = e.message;
    });
  }

  function card(p) {
    var li = document.createElement('li');
    li.className = 'card';
    li.dataset.id = p.id;
    var img;
    if (p.photo) {
      img = document.createElement('img');
      img.loading = 'lazy'; img.decoding = 'async'; img.alt = p.name;
      img.width = 300; img.height = 300;
      img.src = '/api/store/' + encodeURIComponent(slug) + '/products/' + encodeURIComponent(p.id) + '/photo';
    } else {
      img = document.createElement('div');
      img.setAttribute('aria-hidden', 'true');
      img.textContent = p.name.slice(0, 1);
    }
    img.className = 'thumb';
    var h = document.createElement('h3'); h.textContent = p.name;
    var price = document.createElement('div'); price.className = 'price';
    price.textContent = money(p.priceMinor) + (p.unit ? ' / ' + p.unit : '');
    var stock = document.createElement('div'); stock.className = 'stock';
    //  ۳۲.۳: دادهٔ تازهٔ موجودی نیست ⇒ همین را می‌گوییم، نه «موجود»
    stock.textContent = p.stock && p.stock.status === 'unverified' ? 'موجودی در حال بررسی است' : '';
    var act = document.createElement('div');
    li.append(img, h, price, stock, act);
    renderStepper(act, p);
    return li;
  }

  function renderStepper(box, p) {
    box.textContent = '';
    var line = cart[p.id];
    if (!line) {
      var add = document.createElement('button');
      add.type = 'button'; add.className = 'btn btn-sm btn-block'; add.textContent = 'افزودن به سبد';
      add.setAttribute('aria-label', 'افزودنِ ' + p.name + ' به سبد');
      add.addEventListener('click', function () { setQty(p, 1000); renderStepper(box, p); });
      box.appendChild(add);
      return;
    }
    box.className = 'stepper';
    var minus = document.createElement('button');
    minus.type = 'button'; minus.className = 'btn btn-ghost icon-btn'; minus.textContent = '−';
    minus.setAttribute('aria-label', 'یکی کمتر ' + p.name);
    var out = document.createElement('output'); out.textContent = qty(line.qtyMilli);
    var plus = document.createElement('button');
    plus.type = 'button'; plus.className = 'btn icon-btn'; plus.textContent = '+';
    plus.setAttribute('aria-label', 'یکی بیشتر ' + p.name);
    minus.addEventListener('click', function () { setQty(p, line.qtyMilli - 1000); renderStepper(box, p); });
    plus.addEventListener('click', function () { setQty(p, line.qtyMilli + 1000); renderStepper(box, p); });
    box.append(minus, out, plus);
  }

  function setQty(p, milli) {
    if (p.maxQtyMilli && milli > p.maxQtyMilli) milli = p.maxQtyMilli;
    if (milli <= 0) delete cart[p.id];
    else cart[p.id] = { qtyMilli: milli, name: p.name, unit: p.unit, priceMinor: p.priceMinor, maxQtyMilli: p.maxQtyMilli || null };
    saveCart();
  }

  function count() { return Object.keys(cart).length; }
  function subtotal() {
    return Object.keys(cart).reduce(function (s, id) {
      var l = cart[id];
      //  همان گردکردنِ سرور: هر قلم به افغانیِ کامل (order-states.lineTotalMinor)
      return s + Math.floor((l.qtyMilli * l.priceMinor + 50000) / 100000) * 100;
    }, 0);
  }

  function renderCartBar() {
    var n = count();
    $('open-cart').textContent = 'سبد: ' + fa(n, 0) + ' قلم · ' + money(subtotal());
    $('cart-bar').hidden = n === 0 || $('view-catalog').hidden;
  }

  // ── سبد و ثبت ──
  function fulfillment() {
    var r = document.querySelector('input[name="fulfillment"]:checked');
    return r ? r.value : '';
  }

  function renderCheckout() {
    var ul = $('cart-lines');
    ul.textContent = '';
    Object.keys(cart).forEach(function (id) {
      var l = cart[id];
      var li = document.createElement('li');
      var name = document.createElement('span'); name.className = 'line-name';
      name.textContent = l.name + ' — ' + qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + money(l.priceMinor);
      if (l.unavailable) name.textContent += ' (دیگر در ویترین نیست)';
      var rm = document.createElement('button');
      rm.type = 'button'; rm.className = 'btn btn-ghost btn-sm'; rm.textContent = 'حذف';
      rm.setAttribute('aria-label', 'حذفِ ' + l.name + ' از سبد');
      rm.addEventListener('click', function () { delete cart[id]; saveCart(); renderCheckout(); });
      li.append(name, rm);
      ul.appendChild(li);
    });
    $('cart-empty').hidden = count() > 0;
    var fee = fulfillment() === 'delivery' ? store.deliveryFeeMinor : 0;
    $('t-subtotal').textContent = money(subtotal());
    $('t-fee').textContent = money(fee);
    $('t-fee').hidden = $('t-fee-label').hidden = fulfillment() !== 'delivery';
    $('t-total').textContent = money(subtotal() + fee);
    var short = store.minOrderMinor - subtotal();
    $('min-order').hidden = !(short > 0);
    $('min-order').textContent = short > 0 ? 'حداقلِ سفارش ' + money(store.minOrderMinor) + ' است؛ ' + money(short) + ' دیگر لازم است.' : '';
    $('address-box').hidden = fulfillment() !== 'delivery';
    renderVerify();
    $('submit-order').disabled = !count() || short > 0 || !verifiedFor($('c-phone').value) || !fulfillment();
  }

  function normPhone(v) {
    var s = String(v || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s\-().]/g, '').replace(/^00/, '+');
    if (!s) return '';
    if (s.charAt(0) === '0') s = '+93' + s.slice(1);
    else if (s.charAt(0) !== '+') s = '+93' + s;
    return s;
  }
  function verifiedFor(phone) { var t = ticket(); return Boolean(t && phone && t.phone === normPhone(phone)); }

  function renderVerify() {
    var ok = verifiedFor($('c-phone').value);
    $('verify-send').hidden = ok || !verifyAvailable;
    if (ok) note($('verify-msg'), 'ok', 'شماره تأیید شده است.');
    else if (!verifyAvailable) note($('verify-msg'), 'warn', 'تأییدِ شماره هنوز راه‌اندازی نشده؛ فعلاً برای سفارش با دکان تماس بگیرید.');
    else if ($('verify-msg').className.indexOf('ok') >= 0) $('verify-msg').hidden = true;
    if (ok) $('verify-code-row').hidden = true;
  }

  var verifyAvailable = true;

  function sendCode() {
    var phone = $('c-phone').value;
    if (!phone) { note($('verify-msg'), 'err', 'اول شمارهٔ موبایل را بنویسید.'); $('c-phone').focus(); return; }
    $('verify-send').disabled = true;
    api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
      $('verify-code-row').hidden = false;
      note($('verify-msg'), 'info', 'کد فرستاده شد.' + (r.devCode ? ' (کدِ آزمایشی: ' + r.devCode + ')' : ''));
      $('v-code').focus();
    }).catch(function (e) {
      if (e.code === 'verification_unavailable') verifyAvailable = false;
      note($('verify-msg'), 'err', e.message);
    }).then(function () {
      $('verify-send').disabled = false;
      renderCheckout();
    });
  }

  function checkCode() {
    api('/verify/check', { method: 'POST', body: { phone: $('c-phone').value, code: $('v-code').value } }).then(function (r) {
      save(KEY_TICKET, { phone: r.phone, ticket: r.ticket, expiresAt: r.expiresAt });
      $('v-code').value = '';
      renderCheckout();
    }).catch(function (e) {
      //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
      note($('verify-msg'), 'err', e.code === 'network' ? e.message : (e.message || 'کد درست نیست'));
    });
  }

  var pin = null;
  function takePin() {
    if (!navigator.geolocation) { $('pin-text').textContent = 'این مرورگر موقعیت نمی‌دهد؛ نشانی و نشانه بس است.'; return; }
    $('pin-text').textContent = 'در حالِ گرفتنِ موقعیت…';
    navigator.geolocation.getCurrentPosition(function (pos) {
      pin = { lat: Math.round(pos.coords.latitude * 1e6) / 1e6, lng: Math.round(pos.coords.longitude * 1e6) / 1e6 };
      $('pin-text').textContent = 'پین ثبت شد (' + fa(pin.lat, 5) + '، ' + fa(pin.lng, 5) + ')';
    }, function () {
      pin = null;
      $('pin-text').textContent = 'موقعیت گرفته نشد؛ نشانی و نشانه بس است.';
    }, { enableHighAccuracy: true, timeout: 10000 });
  }

  function orderBody() {
    var f = fulfillment();
    var body = {
      fulfillment: f, paymentMethod: 'cod',
      customer: { name: $('c-name').value.trim(), phone: $('c-phone').value.trim() },
      lines: Object.keys(cart).map(function (id) { return { productId: id, qtyMilli: cart[id].qtyMilli }; }),
      note: $('c-note').value.trim(),
      expectedTotalMinor: subtotal() + (f === 'delivery' ? store.deliveryFeeMinor : 0),
    };
    if (f === 'delivery') {
      body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim() };
      if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
      body.delivery = { callBeforeArrival: $('d-call').checked, leaveWith: $('d-leave').value.trim() };
    }
    return body;
  }

  /** کلید تا وقتی بدنه همان است می‌ماند (زدنِ دوباره پس از قطعِ شبکه). */
  function keyFor(body) {
    var json = JSON.stringify(body);
    var p = loadS(KEY_PENDING);
    if (p && p.json === json) return p.key;
    var rnd = (window.crypto && crypto.randomUUID) ? crypto.randomUUID()
      : Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    var key = 'web-' + rnd;
    saveS(KEY_PENDING, { json: json, key: key });
    return key;
  }

  function submitOrder(ev) {
    ev.preventDefault();
    var msg = $('checkout-msg');
    var body = orderBody();
    if (!body.customer.name) { note(msg, 'err', 'نام را بنویسید.'); $('c-name').focus(); return; }
    var btn = $('submit-order');
    btn.disabled = true;
    note(msg, 'info', 'در حالِ ثبت…');
    api('/checkout', { method: 'POST', body: body, key: keyFor(body) }).then(function (r) {
      saveS(KEY_PENDING, null);
      var list = load(KEY_ORDERS, []);
      if (!list.some(function (o) { return o.token === r.order.token; })) {
        list.unshift({ token: r.order.token, orderNo: r.order.orderNo, at: r.order.createdAt });
        save(KEY_ORDERS, list.slice(0, 30));
      }
      cart = {}; saveCart();
      $('checkout-msg').hidden = true;
      location.hash = '#/o/' + r.order.token;
    }).catch(function (e) {
      var d = e.data || {};
      if (e.code === 'price_changed' && d.quote) {
        var changed = [];
        d.quote.lines.forEach(function (l) {
          if (cart[l.productId] && cart[l.productId].priceMinor !== l.unitPriceMinor) {
            changed.push(l.name + ': ' + money(cart[l.productId].priceMinor) + ' ← ' + money(l.unitPriceMinor));
            cart[l.productId].priceMinor = l.unitPriceMinor;
          }
        });
        saveCart(); renderCheckout();
        note(msg, 'warn', 'قیمت عوض شده است. ' + changed.join('؛ ') + ' — مبلغِ تازه: ' + money(d.quote.totalMinor) + '. اگر می‌پذیرید، دوباره «ثبتِ سفارش» را بزنید.');
      } else if (e.code === 'product_unavailable' && d.unavailable) {
        d.unavailable.forEach(function (id) { if (cart[id]) cart[id].unavailable = true; });
        saveCart(); renderCheckout();
        note(msg, 'err', 'برخی کالاها دیگر در ویترین نیستند؛ آن‌ها را از سبد بردارید.');
      } else if (e.code === 'qty_over_limit') {
        note(msg, 'err', e.message + ' (حداکثر: ' + qty(d.maxQtyMilli) + ')');
      } else if (e.code === 'verification_required' || e.code === 'phone_not_verified') {
        save(KEY_TICKET, null);
        note(msg, 'err', e.message);
      } else {
        note(msg, 'err', e.message);
      }
    }).then(function () { renderCheckout(); });
  }

  // ── رسید و پیگیری ──
  var STATUS = {
    new: 'ثبت شد — منتظرِ تأییدِ دکان', accepted: 'دکان قبول کرد', rejected: 'دکان نپذیرفت',
    auto_rejected: 'بی پاسخ ماند', cancelled_by_customer: 'شما لغو کردید', picking: 'در حالِ جمع‌آوری',
    packed: 'بسته‌بندی شد', ready_for_pickup: 'آمادهٔ دریافت از دکان', out_for_delivery: 'در راه',
    picked_up: 'تحویل گرفته شد', delivered: 'تحویل شد', cancelled: 'لغو شد', delivery_failed: 'تحویل نشد',
    retry_scheduled: 'تحویلِ دوباره', returned_to_store: 'به دکان برگشت', restocked: 'به دکان برگشت',
    return_requested: 'درخواستِ مرجوعی', refunded_partial: 'بخشی بازپرداخت شد', refunded_full: 'بازپرداخت شد',
  };
  var REASON = { out_of_stock: 'ناموجود', closed: 'دکان بسته بود', busy: 'دکان شلوغ بود', out_of_area: 'بیرون از محدوده', customer_request: 'به درخواستِ مشتری', no_show: 'نیامدید', other: '' };

  function when(at) {
    try { return new Date(at).toLocaleString('fa-AF', { dateStyle: 'short', timeStyle: 'short' }); } catch (e) { return ''; }
  }

  function renderOrder(o) {
    $('o-title').textContent = 'سفارشِ شمارهٔ ' + fa(o.orderNo, 0);
    $('o-status').textContent = (STATUS[o.status] || o.status) + (o.statusReason && REASON[o.statusReason] ? ' — ' + REASON[o.statusReason] : '');
    var ul = $('o-lines'); ul.textContent = '';
    o.lines.forEach(function (l) {
      var li = document.createElement('li');
      var a = document.createElement('span'); a.className = 'line-name';
      a.textContent = l.name + ' — ' + qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + money(l.unitPriceMinor);
      var b = document.createElement('span'); b.textContent = money(l.lineTotalMinor);
      li.append(a, b); ul.appendChild(li);
    });
    var paid = o.paymentStatus === 'paid' ? o.totalMinor : 0;
    var rows = [['جمعِ کالاها', money(o.subtotalMinor)]];
    if (o.discountMinor) rows.push(['تخفیف', money(o.discountMinor)]);
    if (o.fulfillment === 'delivery') rows.push(['هزینهٔ ارسال', money(o.deliveryFeeMinor)]);
    rows.push(['مبلغِ نهایی', money(o.totalMinor)], ['پرداخت‌شده', money(paid)], ['مانده (نقد هنگامِ تحویل)', money(o.totalMinor - paid)]);
    var t = $('o-totals'); t.textContent = '';
    rows.forEach(function (r, i) {
      var a = document.createElement('span'); a.textContent = r[0];
      var b = document.createElement('span'); b.textContent = r[1];
      if (i === 0 || r[0] === 'مبلغِ نهایی') { a.className = b.className = r[0] === 'مبلغِ نهایی' ? 'grand' : ''; }
      t.append(a, b);
    });
    var tl = $('o-timeline'); tl.textContent = '';
    o.timeline.forEach(function (e) {
      var li = document.createElement('li');
      li.textContent = when(e.at) + ' — ' + (STATUS[e.status] || e.status);
      tl.appendChild(li);
    });
    $('o-cancel').hidden = !o.canCancel;
  }

  function openOrder(token) {
    show('view-order');
    $('o-msg').hidden = true;
    api('/orders/' + encodeURIComponent(token)).then(function (r) { renderOrder(r.order); }).catch(function (e) {
      $('o-title').textContent = 'سفارش';
      $('o-status').textContent = e.status === 404 ? 'این سفارش پیدا نشد.' : e.message;
      $('o-lines').textContent = ''; $('o-totals').textContent = ''; $('o-timeline').textContent = '';
      $('o-cancel').hidden = true;
    });
    $('o-cancel').onclick = function () {
      if (!window.confirm('این سفارش لغو شود؟')) return;
      api('/orders/' + encodeURIComponent(token) + '/cancel', { method: 'POST', body: {} }).then(function (r) {
        renderOrder(r.order);
      }).catch(function (e) { note($('o-msg'), 'err', e.message); });
    };
  }

  function openOrders() {
    show('view-orders');
    var ul = $('my-orders'); ul.textContent = '';
    var local = load(KEY_ORDERS, []);
    var draw = function (list) {
      ul.textContent = '';
      list.forEach(function (o) {
        var li = document.createElement('li');
        var a = document.createElement('a');
        a.href = '#/o/' + o.token;
        a.textContent = 'سفارشِ ' + fa(o.orderNo, 0) + (o.at || o.createdAt ? ' — ' + when(o.at || o.createdAt) : '');
        li.appendChild(a);
        if (o.status) { var s = document.createElement('span'); s.className = 'muted'; s.textContent = STATUS[o.status] || o.status; li.appendChild(s); }
        ul.appendChild(li);
      });
      $('my-orders-empty').hidden = list.length > 0;
    };
    draw(local);
    //  S138: با شمارهٔ تأییدشده، سفارش‌های همین شماره از هر دستگاه
    if (ticket()) api('/orders').then(function (r) { if (r.orders.length) draw(r.orders); }).catch(function () { /* همان محلی */ });
  }

  // ── مسیر ──
  function route() {
    if (!store) return;
    var h = location.hash || '#/';
    if (h.indexOf('#/o/') === 0) return openOrder(h.slice(4));
    if (h === '#/orders') return openOrders();
    if (h === '#/cart') { show('view-checkout'); return renderCheckout(); }
    show('view-catalog');
    renderCartBar();
    Object.keys(catalog.items).forEach(function (id) {
      var li = document.querySelector('#products li[data-id="' + CSS.escape(id) + '"] > div:last-child');
      if (li) renderStepper(li, catalog.items[id]);
    });
  }

  function boot() {
    if (!/^[a-z0-9][a-z0-9-]{1,30}[a-z0-9]$/.test(slug)) { show('view-missing'); return; }
    api('').then(function (r) {
      store = r.store;
      verifyAvailable = !r.verification || r.verification.available !== false;
      document.title = store.title;
      $('store-title').textContent = store.title;
      $('store-tagline').textContent = store.tagline || '';
      $('store-tagline').hidden = !store.tagline;
      if (store.contactPhone) { $('store-call').href = 'tel:' + store.contactPhone; $('store-call').hidden = false; }
      if (store.accent) document.documentElement.style.setProperty('--primary', store.accent);
      renderStatus(store.status);
      renderCats();
      $('opt-delivery').hidden = !store.fulfillment.delivery;
      $('opt-pickup').hidden = !store.fulfillment.pickup;
      var only = store.fulfillment.delivery && !store.fulfillment.pickup ? 'delivery'
        : (!store.fulfillment.delivery && store.fulfillment.pickup ? 'pickup' : '');
      if (only) document.querySelector('input[name="fulfillment"][value="' + only + '"]').checked = true;
      var t = ticket();
      if (t && !$('c-phone').value) $('c-phone').value = t.phone.replace(/^\+93/, '0');
      return loadProducts(false);
    }).then(route).catch(function (e) {
      if (e.status === 404) show('view-missing');
      else { $('boot').textContent = e.message; }
    });
  }

  document.addEventListener('DOMContentLoaded', function () {
    $('search-form').addEventListener('submit', function (e) { e.preventDefault(); catalog.q = $('q').value.trim(); loadProducts(false); });
    $('q').addEventListener('input', function () {
      clearTimeout(searchTimer);
      searchTimer = setTimeout(function () { catalog.q = $('q').value.trim(); loadProducts(false); }, 300);
    });
    $('clear-search').addEventListener('click', function () { catalog.q = ''; catalog.category = ''; $('q').value = ''; renderCats(); loadProducts(false); });
    $('more').addEventListener('click', function () { loadProducts(true); });
    Array.prototype.forEach.call(document.querySelectorAll('input[name="fulfillment"]'), function (r) { r.addEventListener('change', renderCheckout); });
    $('c-phone').addEventListener('input', renderCheckout);
    $('verify-send').addEventListener('click', sendCode);
    $('verify-check').addEventListener('click', checkCode);
    $('pin-btn').addEventListener('click', takePin);
    $('checkout-form').addEventListener('submit', submitOrder);
    window.addEventListener('hashchange', route);
    window.addEventListener('storage', function (e) { if (e.key === KEY_CART) { cart = load(KEY_CART, {}); route(); } });
    boot();
  });
})();
