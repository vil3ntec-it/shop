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

    // ── MKT-01/09 و CHK-04: تخفیف (پرچمِ `shop.storefront.promos`؛ ‏`GET /promos` ِ ۴۰۴ ⇒ خاموش، همان فرمِ امروز) ──
    //  حساب فقط از سرور (`/promos/quote` = همان حسابِ ثبت)؛ تخفیفِ دیده‌شده در `expectedTotalMinor` می‌رود و
    //  تا پیش‌نمایشِ همین سبد نرسیده «ثبت» بسته است. کدی که اعمال نشود ⇒ پیامِ روشن، نه سفارشِ بی‌صدا.
    var promos;                 // undefined = در راه، false = خاموش، وگرنه پاسخِ GET /promos
    var code = '';              // کدی که مشتری زد
    var quoted = null; var qKey = ''; var qTimer = null; var skew = 0;

    // ── CHK-07/12: زمانِ تحویل (فقط با `store.delivery.scheduling[روش]`، پرچمِ `shop.storefront.zones`) ──
    //  بازه‌ها، ظرفیت و هزینهٔ بازه فقط از `GET /slots`؛ هزینهٔ بازهٔ «ارسال» روی هزینهٔ ناحیه می‌آید («رایگان از…» آن را
    //  برنمی‌دارد) و در `expectedTotalMinor` می‌رود. «هرچه زودتر» فقط با `allowAsap`؛ نبود ⇒ تا بازه برگزیده نشده «ثبت» بسته.
    //  پاسخِ `slot_*` ِ سرور ⇒ بازه‌های تازهٔ همان پاسخ. خاموش ⇒ همان فرمِ امروز، بی `slotStartsAt`.
    var LOC = K.lang === 'en' ? 'en-GB' : 'fa-AF';
    var slots = {};             // روش ⇒ undefined = نخواسته، null = در راه، false = نیامد، وگرنه پاسخِ GET /slots
    var slotAt = 0;             // `startsAt` ِ برگزیده؛ ۰ = هرچه زودتر / هیچ
    var drawn = null;
    function scheduled(f) { var d = c.store.delivery; return Boolean(f && d && d.scheduling && d.scheduling[f]); }
    function slotOf(f) {
      var r = slots[f];
      return r && slotAt ? r.slots.filter(function (s) { return s.startsAt === slotAt && s.available; })[0] || null : null;
    }
    function slotFee(f) { var s = f === 'delivery' && slotOf(f); return s ? s.feeMinor : 0; }
    function needSlot(f) { var r = slots[f]; return scheduled(f) && r !== false && (!r || (!r.allowAsap && !slotOf(f))); }
    function loadSlots(f) {
      if (slots[f] !== undefined) return;
      slots[f] = null;
      api('/slots?fulfillment=' + f).then(function (r) { slots[f] = r; }, function () { slots[f] = false; }).then(renderCheckout);
    }
    function slotLabel(s, r, f) {
      var d = '';
      try { d = new Date(s.startsAt).toLocaleDateString(LOC, { timeZone: r.timezone, weekday: 'long', day: 'numeric', month: 'long' }); } catch (e) { d = s.date; }
      var x = t('slot.opt', { d: d, h: K.hm(s.startMin) + '–' + K.hm(s.endMin) });
      if (f === 'delivery' && s.feeMinor) x += ' ' + t('slot.fee', { m: K.money(s.feeMinor) });
      return !s.available ? x + ' — ' + t('slot.full') : s.few ? x + ' — ' + t('slot.few') : x;
    }
    function renderSlots(f) {
      var on = scheduled(f);
      $('slot-box').hidden = !on;
      if (!on) return;
      loadSlots(f);
      var r = slots[f]; var sel = $('slot-pick');
      if (!slotOf(f)) slotAt = 0;
      if (drawn !== r || sel.getAttribute('data-f') !== f) {
        drawn = r;
        sel.setAttribute('data-f', f);
        K.clear(sel).appendChild(el('option', { attrs: { value: '' }, text: t(r && r.allowAsap ? 'slot.asap' : 'slot.pick') }));
        (r ? r.slots : []).forEach(function (s) {
          sel.appendChild(el('option', { attrs: { value: String(s.startsAt), disabled: s.available ? null : '' }, text: slotLabel(s, r, f) }));
        });
      }
      sel.disabled = !r;
      sel.value = slotAt ? String(slotAt) : '';
      if (r && !r.allowAsap && !r.slots.some(function (s) { return s.available; })) K.note($('slot-msg'), 'warn', t('slot.none'));
      else $('slot-msg').hidden = true;
    }
    function promoKey() { return JSON.stringify([ids().map(function (id) { return [id, c.cart[id].qtyMilli, c.cart[id].priceMinor]; }), code]); }
    function discount() { return promos && quoted && quoted.key === promoKey() ? quoted.discountMinor : 0; }
    function pending() { return promos === undefined || Boolean(promos && ids().length && !(quoted && quoted.key === promoKey())); }
    function promoNote(kind, text) { K.note($('promo-msg'), kind, text); }
    function reason(st) { var s = st && st.status; return K.has('promo.st.' + s) ? t('promo.st.' + s) : t('promo.st.unknown'); }
    function loadPromos() {
      if (promos !== undefined) return;
      api('/promos').then(function (r) { promos = r; skew = (r.serverTime || Date.now()) - Date.now(); })
        .catch(function () { promos = false; }).then(renderCheckout);
    }
    function requote() {
      if (!promos || !ids().length) return;
      var key = promoKey();
      if (key === qKey) return;
      qKey = key;
      clearTimeout(qTimer);
      qTimer = setTimeout(function () {
        var body = { lines: ids().map(function (id) { return { productId: id, qtyMilli: c.cart[id].qtyMilli }; }) };
        if (code) body.promoCode = code;
        api('/promos/quote', { method: 'POST', body: body }).then(function (r) {
          if (key !== promoKey()) return;
          if (code && !(r.code && r.code.status === 'applied')) { var why = reason(r.code); code = ''; qKey = ''; renderCheckout(); promoNote('warn', why); return; }
          quoted = { key: key, discountMinor: r.discountMinor || 0, applied: r.applied || [] };
          renderCheckout();
        }).catch(function (e) { qKey = ''; promoNote('err', e.message); });
      }, 250);
    }
    //  MKT-09: «تا پایان …» — پایانِ همان روزِ `endsOn` به وقتِ کابل، با ساعتِ سرور
    function leftText(endsOn) {
      var ms = Date.parse(endsOn + 'T23:59:59+04:30') - (Date.now() + skew);
      var d = Math.floor(ms / 864e5); var h = Math.floor(ms / 36e5);
      if (d >= 1) return t('promo.days', { n: K.fa(d, 0) });
      if (h >= 1) return t('promo.hours', { n: K.fa(h, 0) });
      return t('promo.mins', { n: K.fa(Math.max(1, Math.ceil(ms / 6e4)), 0) });
    }
    function renderPromos() {
      $('promo-box').hidden = !promos;
      if (!promos) return;
      var ul = K.clear($('promo-auto'));
      promos.promos.forEach(function (p) {
        ul.appendChild(el('li', { text: p.endsOn ? p.name + ' — ' + leftText(p.endsOn) : p.name }));
      });
      $('promo-row').hidden = !promos.codes || Boolean(code);
      $('promo-clear').hidden = !code;
      if (code && quoted && quoted.key === promoKey()) promoNote('ok', t('promo.ok', { code: code, d: K.money(quoted.discountMinor) }));
      else if (!code && $('promo-msg').className.indexOf('ok') >= 0) $('promo-msg').hidden = true;
      requote();
    }

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
      var sub = subtotal(); var fee = feeFor(f) + slotFee(f);
      $('t-subtotal').textContent = K.money(sub);
      var unknown = f === 'delivery' && c.zones() && !c.zone;
      $('t-fee').textContent = f === 'delivery' && !unknown ? (fee > 0 ? K.money(fee) : t('free')) : '—';
      $('t-fee-label').textContent = f === 'pickup' ? t('pickup') : t('ck.fee');
      var disc = discount();
      $('t-disc-label').hidden = $('t-disc').hidden = !(disc > 0);
      $('t-disc').textContent = '−' + K.money(disc);
      $('t-total').textContent = K.money(sub - disc + fee);
      $('address-box').hidden = f !== 'delivery';
      $('addr-extra').hidden = !c.store.addressExtra;
      var min = c.minFor(f);
      var below = min > sub;
      if (below) K.note($('min-order'), 'warn', t('ck.min', { m: K.money(min) })); else $('min-order').hidden = true;
      renderZone(f);
      renderSlots(f);
      renderVerify();
      var ok = verifiedFor($('c-phone').value);
      var btn = $('submit-order');
      btn.disabled = closed() || !ids().length || !f || below || unknown || hasBad() || hasShort() || !ok || pending() || needSlot(f);
      btn.textContent = t(quoteNote ? 'ck.accept' : 'ck.submit', { sum: K.money(sub - disc + fee) });
      renderPromos();
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
        expectedTotalMinor: subtotal() - discount() + feeFor(f) + slotFee(f),
        //  NTF-03: پیامِ وضعیتِ سفارش به همین زبان
        lang: K.lang,
      };
      if (c.campaign) body.campaign = c.campaign;
      if (slotOf(f)) body.slotStartsAt = slotAt;
      if (code) body.promoCode = code;   //  MKT-01: سرور در همان تراکنش می‌سنجد؛ اعمال نشد ⇒ ۴۰۹ `promo_code_rejected`   //  MKT-11: پیوندِ کمپین/QR ِ همین نشست
      if (f === 'delivery') {
        body.address = { text: $('a-text').value.trim(), landmark: $('a-landmark').value.trim(), area: $('a-area').value.trim(), gateColor: $('a-gate').value.trim() };
        //  CHK-08 (پرچمِ `shop.storefront.address_extra`): شمارهٔ دوم و Plus Code — نادرست ⇒ ۴۰۰ ِ سرور با پیامِ روشن
        if (c.store.addressExtra) { body.address.phone2 = $('a-phone2').value.trim(); body.address.plusCode = $('a-plus').value.trim(); }
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
        if (e.code === 'promo_code_rejected') {
          code = ''; quoted = null; qKey = '';
          K.note(msg, 'err', reason(d.promo));
        } else if (e.code === 'price_changed' && d.quote) {
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
        } else if (/^slot_/.test(e.code) && Array.isArray(d.slots) && slots[body.fulfillment]) {
          slots[body.fulfillment].slots = d.slots; slotAt = 0; drawn = null;
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
      loadPromos();
      //  ظرفیتِ بازه‌ها هر بار تازه
      slots = {}; drawn = null;
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
    $('slot-pick').addEventListener('change', function () { slotAt = Number($('slot-pick').value) || 0; renderCheckout(); });
    var applyCode = function () {
      var v = $('promo-code').value.trim();
      if (!v) return;
      code = v.slice(0, 32); quoted = null; $('promo-code').value = ''; renderCheckout();
    };
    $('promo-apply').addEventListener('click', applyCode);
    $('promo-code').addEventListener('keydown', function (e) { if (e.key === 'Enter') { e.preventDefault(); applyCode(); } });
    $('promo-clear').addEventListener('click', function () { code = ''; quoted = null; $('promo-msg').hidden = true; renderCheckout(); });
    //  شمارشِ معکوس فقط وقتی برگهٔ ثبت جلوی چشم است
    setInterval(function () { if (promos && !$('view-checkout').hidden) renderPromos(); }, 30000);

    return { open: openCheckout, render: renderCheckout };
  };
})();
