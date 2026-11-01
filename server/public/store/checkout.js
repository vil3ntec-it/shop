/*
 * ویترین — ثبتِ سفارش (۲۳.۳، CHK): برگهٔ ثبت، تأییدِ شماره با کد، نقطهٔ نقشه و فرستادنِ سفارش.
 * فقط با بازشدنِ سبد یا صفحهٔ ثبت بار می‌شود (`loadCheckout` در store.js) تا JS ِ بارِ اولِ ویترین
 * زیرِ سقفِ ۷۵KB بماند (WEB-15، §۳.۱۰)؛ سبد، کاتالوگ و مسیر همان store.js است.
 *
 * ⛔ قیمت را سرور می‌گوید؛ جمعِ دیده‌شده (`expectedTotalMinor`) فقط برای این است
 *    که اگر قیمت عوض شده، سفارش ثبت نشود و برگهٔ تازه روشن نشان داده شود (CHK-17).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /**
   * `c` از store.js: `api`، `slug`، `orders` (کلیدِ سفارش‌های همین دستگاه)، تابع‌های سبد و
   * نمایش، و `store` / `cart` / `cap` / `stockList` با getter (سبد در store.js عوض می‌شود).
   * `empty()` سبد و سقف‌ها را خالی و ذخیره می‌کند.
   */
  window.StoreCheckout = function (c) {
    var api = c.api; var slug = c.slug; var KEY_ORDERS = c.orders; var show = c.show;
    var ids = c.ids; var subtotal = c.subtotal; var feeFor = c.feeFor; var defaultFulfillment = c.defaultFulfillment;
    var hasBad = c.hasBad; var short = c.short; var hasShort = c.hasShort; var shortText = c.shortText;
    var thumb = c.thumb; var setQty = c.setQty; var saveCart = c.saveCart;
    var verifyAvailable = c.verifyAvailable;
    //  shop.orders.subscribers_only: ویترینِ فقط‌خواندنی (`store.ordering.accepting === false`) ⇒ «ثبت» بسته
    var closed = c.closed || function () { return false; };
    var pin = null; var quoteNote = '';
    //  CHK-07/10 (فقط با `store.delivery.zoned`): پینِ بیرون از محدوده ⇒ «دریافت از دکان» یا «خبرم کن»
    var area = null;

    function fulfillment() {
      var r = document.querySelector('input[name="fulfillment"]:checked');
      return r ? r.value : '';
    }
    function verifiedFor(phone) { var tk = K.ticket(); return Boolean(tk && phone && tk.phone === K.normPhone(phone)); }

    function renderCheckout() {
      var ul = K.clear($('cart-lines'));
      ids().forEach(function (id) {
        var l = c.cart[id];
        var li = el('li', { class: l.unavailable ? 'bad' : short(id) ? 'short' : '' }, thumb(id, l),
          el('span', { class: 'grow' }, l.name, el('small', { text: K.qty(l.qtyMilli) + ' ' + (l.unit || '') + ' × ' + K.money(l.priceMinor) + (short(id) ? ' — ' + shortText(id) : '') })),
          el('span', { class: 'amt num', text: K.money(K.lineTotal(l.qtyMilli, l.priceMinor)) }));
        if (short(id)) {
          li.appendChild(el('button', { class: 'btn btn-soft btn-sm fit', attrs: { type: 'button' }, on: { click: function () { setQty(id, c.cap[id]); renderCheckout(); } } },
            t('line.fit', { q: K.qty(c.cap[id]) })));
        }
        ul.appendChild(li);
      });
      $('cart-empty').hidden = ids().length > 0;
      var f = fulfillment();
      var sub = subtotal(); var fee = feeFor(f);
      $('t-subtotal').textContent = K.money(sub);
      var unknown = f === 'delivery' && c.zones() && !c.zone;
      $('t-fee').textContent = f === 'delivery' && !unknown ? (fee > 0 ? K.money(fee) : t('free')) : '—';
      $('t-fee-label').textContent = f === 'pickup' ? t('pickup') : t('ck.fee');
      $('t-total').textContent = K.money(sub + fee);
      $('address-box').hidden = f !== 'delivery';
      var min = c.minFor(f);
      var below = min > sub;
      if (below) K.note($('min-order'), 'warn', t('ck.min', { m: K.money(min) })); else $('min-order').hidden = true;
      renderZone(f);
      renderVerify();
      var ok = verifiedFor($('c-phone').value);
      var btn = $('submit-order');
      btn.disabled = closed() || !ids().length || !f || below || unknown || hasBad() || hasShort() || !ok;
      btn.textContent = t(quoteNote ? 'ck.accept' : 'ck.submit', { sum: K.money(sub + fee) });
    }

    function renderVerify() {
      var ok = verifiedFor($('c-phone').value);
      $('verify-send').hidden = ok || !verifyAvailable;
      if (ok) { K.note($('verify-msg'), 'ok', t('v.ok')); $('verify-code-row').hidden = true; $('verify-text').hidden = true; }
      else {
        $('verify-text').hidden = false;
        if (!verifyAvailable) {
          K.note($('verify-msg'), 'warn', c.store.contactPhone ? t('v.off.call', { phone: K.localPhone(c.store.contactPhone) }) : t('v.off.shop'));
        } else if ($('verify-msg').className.indexOf(' ok') >= 0) $('verify-msg').hidden = true;
      }
    }

    function sendCode() {
      var phone = $('c-phone').value;
      if (!phone.trim()) { K.note($('verify-msg'), 'err', t('v.need')); $('c-phone').focus(); return; }
      var b = $('verify-send'); b.disabled = true;
      api('/verify/start', { method: 'POST', body: { phone: phone } }).then(function (r) {
        $('verify-code-row').hidden = false;
        K.note($('verify-msg'), 'info', t('v.sent') + (r.devCode ? ' ' + t('v.dev', { code: r.devCode }) : ''));
        $('v-code').focus();
      }).catch(function (e) {
        if (e.code === 'verification_unavailable') verifyAvailable = false;
        K.note($('verify-msg'), e.code === 'verification_unavailable' ? 'warn' : 'err', e.code === 'bad_request' ? t('err.bad_phone') : e.message);
      }).then(function () { b.disabled = false; renderCheckout(); });
    }

    function checkCode() {
      var b = $('verify-check'); b.disabled = true;
      api('/verify/check', { method: 'POST', body: { phone: $('c-phone').value, code: $('v-code').value } }).then(function (r) {
        K.setTicket(r);
        K.account();
        $('v-code').value = '';
        renderCheckout();
      }).catch(function (e) {
        //  ۲۳.۴: خطای شبکه «کدِ اشتباه» نیست
        K.note($('verify-msg'), 'err', e.message);
      }).then(function () { b.disabled = false; });
    }

    function renderZone(f) {
      var zs = c.zones(); var box = $('zone-box');
      box.hidden = !(zs && f === 'delivery');
      if (box.hidden) return;
      var sel = $('zone-pick');
      if (sel.options.length !== zs.length + 1) {
        while (sel.options.length > 1) sel.remove(1);
        zs.forEach(function (z) {
          sel.appendChild(el('option', { attrs: { value: z.id }, text: t('zone.opt', { name: z.name, fee: z.feeMinor > 0 ? K.money(z.feeMinor) : t('free') }) }));
        });
      }
      sel.value = c.zone ? c.zone.id : '';
      $('zone-acts').hidden = !area;
      $('zone-pickup').hidden = !(area && c.store.fulfillment.pickup);
      $('zone-notify').hidden = !(area && area.notify && !area.done);
      if (area) K.note($('zone-msg'), area.done ? 'ok' : 'warn', area.done ? t('zone.notified') : t('zone.out'));
      else if (c.zone) K.note($('zone-msg'), 'ok', t('zone.in', { name: c.zone.name }));
      else K.note($('zone-msg'), 'info', t('zone.need'));
    }
    //  «کدام ناحیه؟» از سرور، بی ذخیره؛ ناحیهٔ پین جای گزینشِ دستی را می‌گیرد
    function locate() {
      if (!pin || !c.zones()) return;
      api('/delivery?lat=' + pin.lat + '&lng=' + pin.lng).then(function (r) {
        area = r.zoned && !r.inZone ? { lat: pin.lat, lng: pin.lng, notify: Boolean(r.notifyArea) } : null;
        c.setZone(r.zone || null);
      }).catch(function (e) { K.note($('zone-msg'), 'err', e.message); }).then(renderCheckout);
    }
    function notifyMe() {
      if (!area) return;
      if (!verifiedFor($('c-phone').value)) { K.note($('zone-msg'), 'warn', t('err.verification_required')); $('c-phone').focus(); return; }
      api('/area-request', { method: 'POST', body: { lat: area.lat, lng: area.lng } }).then(function () {
        area.done = true; renderCheckout();
      }).catch(function (e) { K.note($('zone-msg'), 'err', e.message); });
    }
    function takePin() {
      if (!navigator.geolocation) { $('pin-text').textContent = t('pin.none'); return; }
      $('pin-text').textContent = t('pin.wait');
      navigator.geolocation.getCurrentPosition(function (pos) {
        pin = { lat: Math.round(pos.coords.latitude * 1e6) / 1e6, lng: Math.round(pos.coords.longitude * 1e6) / 1e6 };
        $('pin-text').textContent = t('pin.ok', { m: K.fa(Math.round(pos.coords.accuracy || 0), 0) });
        $('pin-btn').textContent = t('pin.again');
        locate();
      }, function () {
        pin = null;
        $('pin-text').textContent = t('pin.fail');
      }, { enableHighAccuracy: true, timeout: 10000 });
    }

    function orderBody() {
      var f = fulfillment();
      var body = {
        fulfillment: f, paymentMethod: 'cod',
        customer: { name: $('c-name').value.trim(), phone: $('c-phone').value.trim() },
        //  CHK-17/S32: فیِ هر قلم که مشتری دید — ناهمخوان ⇒ ۴۰۹ `price_changed` حتی اگر جمع از قضا برابر بماند
        lines: ids().map(function (id) { return { productId: id, qtyMilli: c.cart[id].qtyMilli, expectedUnitPriceMinor: c.cart[id].priceMinor }; }),
        note: $('c-note').value.trim(),
        expectedTotalMinor: subtotal() + feeFor(f),
        //  NTF-03: پیامِ وضعیتِ سفارش به همین زبان
        lang: K.lang,
      };
      if (c.campaign) body.campaign = c.campaign;   //  MKT-11: پیوندِ کمپین/QR ِ همین نشست
      if (f === 'delivery') {
        body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim(), area: $('a-area').value.trim(), gateColor: $('a-gate').value.trim() };
        if (pin) { body.address.lat = pin.lat; body.address.lng = pin.lng; }
        //  بی پین ⇒ ناحیهٔ برگزیده (`source: customer`)؛ با پین ناحیه را سرور از پین می‌گوید
        else if (c.zones() && c.zone) body.address.zoneId = c.zone.id;
        body.delivery = { callBeforeArrival: $('d-call').checked, leaveWith: $('d-leave').value.trim() };
      }
      return body;
    }

    function submitOrder(ev) {
      ev.preventDefault();
      var msg = $('checkout-msg');
      var body = orderBody();
      if (!body.customer.name) { K.note(msg, 'err', t('ck.name')); $('c-name').focus(); return; }
      if (body.fulfillment === 'delivery' && !body.address.text && !body.address.landmark && !pin) {
        K.note(msg, 'err', t('ck.addr')); $('a-text').focus(); return;
      }
      var btn = $('submit-order');
      btn.disabled = true;
      K.note(msg, 'info', t('ck.sending'));
      api('/checkout', { method: 'POST', body: body, key: K.keyFor('checkout:' + slug, body) }).then(function (r) {
        K.dropKey('checkout:' + slug);
        var list = K.load(KEY_ORDERS, []) || [];
        if (!list.some(function (o) { return o.token === r.order.token; })) {
          list.unshift({ token: r.order.token, orderNo: r.order.orderNo, at: r.order.createdAt, totalMinor: r.order.totalMinor, storeTitle: c.store.title });
          K.save(KEY_ORDERS, list.slice(0, 30));
        }
        K.saveS('placed:' + r.order.token, true);
        //  فقط روی همین دستگاه، برای سفارشِ بعدی (نام و آخرین نشانی)
        K.save('store-customer', { name: body.customer.name, addressText: body.address ? body.address.text : '', landmark: body.address ? body.address.landmark : '' });
        quoteNote = ''; c.empty();
        msg.hidden = true;
        location.hash = '#/o/' + encodeURIComponent(r.order.token);
      }).catch(function (e) {
        var d = e.data || {};
        if (e.code === 'price_changed' && d.quote) {
          //  S135: تغییر روشن نشان داده می‌شود؛ بی پذیرشِ دوباره ثبت نمی‌شود
          var changed = [];
          d.quote.lines.forEach(function (l) {
            if (c.cart[l.productId] && c.cart[l.productId].priceMinor !== l.unitPriceMinor) {
              changed.push(l.name + ': ' + K.money(c.cart[l.productId].priceMinor) + ' ← ' + K.money(l.unitPriceMinor));
              c.cart[l.productId].priceMinor = l.unitPriceMinor;
            }
          });
          quoteNote = t('ck.repriced', { list: changed.length ? changed.join(t('sep')) + '. ' : '', sum: K.money(d.quote.totalMinor) });
          saveCart();
          K.note(msg, 'warn', quoteNote);
        } else if (e.code === 'out_of_stock' && c.stockList && Array.isArray(d.lines)) {
          //  E2E-MKT-02: همهٔ قلم‌های کم، هر کدام با مقدارِ موجود؛ بی پذیرشِ «کم کن» ثبت نمی‌شود
          var list = [];
          d.lines.forEach(function (x) {
            var l = c.cart[x.productId];
            if (!l) return;
            c.cap[x.productId] = Math.max(0, Math.floor(Number(x.availableMilli)) || 0);
            if (!c.cap[x.productId]) l.unavailable = true;
            list.push(l.name + ': ' + (l.unavailable ? t('line.gone') : shortText(x.productId)));
          });
          saveCart();
          K.note(msg, 'err', t('ck.short', { list: list.join(t('sep')) }));
        } else if (e.code === 'product_unavailable' && d.unavailable) {
          d.unavailable.forEach(function (id) { if (c.cart[id]) c.cart[id].unavailable = true; });
          saveCart();
          K.note(msg, 'err', e.message);
        } else if (e.code === 'store_not_accepting_orders') {
          c.store.ordering = { accepting: false };
          if (c.renderHead) c.renderHead();
          K.note(msg, 'warn', e.message);
        } else if (e.code === 'qty_over_limit') {
          K.note(msg, 'err', e.message + ' (' + t('maxq', { q: K.qty(d.maxQtyMilli) }) + ')');
        } else if (e.code === 'below_min_order') {
          K.note(msg, 'err', t('err.below_min_order') + ' (' + K.money(d.minOrderMinor) + ')');
        } else if (e.code === 'verification_required' || e.code === 'phone_not_verified') {
          K.setTicket(null);
          K.note(msg, 'err', e.message);
        } else {
          K.note(msg, 'err', e.message);
        }
      }).then(function () { if (!$('view-checkout').hidden) renderCheckout(); });
    }

    function openCheckout() {
      if (!ids().length) { location.hash = '#/'; return; }
      show('view-checkout');
      document.title = t('ck.title') + ' — ' + c.store.title;
      if (!fulfillment()) {
        var f = defaultFulfillment();
        var r = document.querySelector('input[name="fulfillment"][value="' + f + '"]');
        if (r) r.checked = true;
      }
      var tk = K.ticket();
      if (tk && !$('c-phone').value) $('c-phone').value = K.localPhone(tk.phone);
      var me = K.load('store-customer', null);
      if (me) {
        if (!$('c-name').value && me.name) $('c-name').value = me.name;
        if (!$('a-text').value && me.addressText) $('a-text').value = me.addressText;
        if (!$('a-landmark').value && me.landmark) $('a-landmark').value = me.landmark;
      }
      renderCheckout();
    }

    Array.prototype.forEach.call(document.querySelectorAll('input[name="fulfillment"]'), function (r) { r.addEventListener('change', renderCheckout); });
    $('c-phone').addEventListener('input', renderCheckout);
    $('verify-send').addEventListener('click', sendCode);
    $('verify-check').addEventListener('click', checkCode);
    $('v-code').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); checkCode(); } });
    $('pin-btn').addEventListener('click', takePin);
    $('checkout-form').addEventListener('submit', submitOrder);
    $('zone-pick').addEventListener('change', function () {
      var id = $('zone-pick').value;
      //  گزینشِ دستی جای پین را می‌گیرد: سرور با پین ناحیه را از پین می‌گوید و پینِ ناجور سفارش را رد می‌کرد
      pin = null; $('pin-text').textContent = t('pin.hint'); $('pin-btn').textContent = t('pin.btn');
      c.setZone(id ? { id: id } : null); area = null; renderCheckout();
    });
    $('zone-pickup').addEventListener('click', function () {
      var r = document.querySelector('input[name="fulfillment"][value="pickup"]');
      if (r) { r.checked = true; renderCheckout(); }
    });
    $('zone-notify').addEventListener('click', notifyMe);

    return { open: openCheckout, render: renderCheckout };
  };
})();
