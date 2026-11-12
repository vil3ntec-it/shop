/*
 * ویترین — روش و مسئولِ تحویل (S71) در صفحهٔ دکان (پیش از سبد)، برگهٔ ثبت و پیگیری، و «تغییرِ روشِ تحویل» تا پیش از
 * جمع‌آوری (S72).
 * فقط وقتی سرور `features.deliveryMethod` (صفحهٔ دکان و برگهٔ ثبت) یا `deliveryMethod` (پیگیری) را داد بار می‌شود
 * (`store.js`، `checkout.js`، `order.js`)؛ پرچمِ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/method.<زبان>.json`)، نه هیچ گره‌ای.
 *
 * ⛔ روش، مسئول، هزینه و زمان را سرور می‌گوید (هرگز پیکِ VILL3N از این‌جا)؛ تغییر فقط پس از دیدنِ برگهٔ تازه
 *    (۴۰۹ `confirm_required`) و «بله» با همان جمع (`expectedTotalMinor`) ثبت می‌شود. متن فقط با textContent.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var NAME = { pickup: 'dm.m.pickup', shop_delivery: 'dm.m.shop', shop_courier: 'dm.m.courier', external: 'dm.m.external' };
  var WHO = { shop: 'dm.r.shop', customer: 'dm.r.customer' };

  function line(m) {
    var name = m.method === 'external' && m.company ? t('dm.m.company', { name: m.company }) : t(NAME[m.method] || 'dm.m.shop');
    return name + t('sep') + t(WHO[m.responsible] || 'dm.r.shop');
  }

  /** `ctx`: `api(path, opts)` و `slug` از store.js */
  window.StoreMethod = function (ctx) {
    var api = ctx.api;

    //  برگهٔ ثبت: روش و مسئولِ همان گزینهٔ برگزیده، زیرِ «ارسال/دریافت»
    function checkout(f, dm) {
      var box = $('ck-dm');
      if (!box) {
        box = el('p', { class: 'muted small', attrs: { id: 'ck-dm', role: 'status' } });
        var seg = $('seg-fulfillment');
        seg.parentNode.insertBefore(box, seg.nextSibling);
      }
      var m = f && dm ? dm[f] : null;
      box.hidden = !m;
      box.textContent = m ? t('dm.title') + ': ' + line(m) : '';
    }

    function options(c) {
      var out = [];
      if (c.pickup) out.push(['pickup', t('dm.to.pickup')]);
      (c.zones || []).forEach(function (z) {
        out.push(['z:' + z.id, t('dm.to.zone', { zone: z.name, fee: z.feeMinor ? K.money(z.feeMinor) : t('free') })]);
      });
      if (c.delivery && !(c.zones || []).length) out.push(['delivery', t('dm.to.delivery')]);
      return out;
    }

    //  پیگیری: یک خط زیرِ وضعیت؛ با `change.allowed` فرمِ کوچکِ «تغییر» (تصمیمِ آخر با سرور، زیرِ قفل)
    function order(o, token, reload) {
      var v = o.deliveryMethod;
      var box = $('o-dm');
      if (!box) {
        box = el('div', { class: 'stack section', attrs: { id: 'o-dm' } });
        var meta = $('o-meta');
        meta.parentNode.insertBefore(box, meta.nextSibling);
      }
      box.hidden = false;
      var c = v.change || {};
      var opts = c.allowed ? options(c) : [];
      var sig = JSON.stringify([token, v]);
      if (box.getAttribute('data-for') === sig) return;
      box.setAttribute('data-for', sig);
      K.clear(box);
      box.appendChild(el('p', { attrs: { id: 'o-dm-line' }, text: t('dm.title') + ': ' + line(v) }));
      var note = el('div', { class: 'note', hidden: true, attrs: { id: 'o-dm-note', role: 'status' } });
      if (!c.allowed && c.preparing) K.note(note, 'warn', t('dm.locked'));
      if (opts.length) {
        var sel = el('select', { attrs: { id: 'o-dm-to' } });
        opts.forEach(function (x) { sel.appendChild(el('option', { attrs: { value: x[0] }, text: x[1] })); });
        var addr = c.needsAddress ? el('textarea', { attrs: { id: 'o-dm-addr', maxlength: '300', placeholder: t('dm.addr') } }) : null;
        var send = el('button', { class: 'btn btn-accent', attrs: { id: 'o-dm-send', type: 'button' }, text: t('dm.send') });
        var form = el('div', { class: 'stack', hidden: true, attrs: { id: 'o-dm-form' } },
          el('label', { class: 'field' }, el('span', { text: t('dm.to') }), sel), addr ? el('label', { class: 'field' }, el('span', { text: t('dm.addr') }), addr) : null, send);
        var open = el('button', { class: 'btn btn-ghost btn-block', attrs: { id: 'o-dm-btn', type: 'button' }, text: t('dm.change') });
        open.onclick = function () { form.hidden = !form.hidden; };
        send.onclick = function () { submit(o, token, sel.value, addr ? addr.value.trim() : '', send, note, reload); };
        box.append(open, form);
      }
      box.appendChild(note);
    }

    function submit(o, token, to, text, send, note, reload) {
      var body = to === 'pickup' ? { fulfillment: 'pickup' } : { fulfillment: 'delivery', address: {} };
      if (to.indexOf('z:') === 0) body.address.zoneId = to.slice(2);
      if (text) body.address.text = text;
      if (o.scheduledFor) body.slotStartsAt = o.scheduledFor;
      var scope = 'dm:' + ctx.slug + ':' + token;
      var post = function (b) {
        return api('/orders/' + encodeURIComponent(token) + '/fulfillment', { method: 'POST', body: b, key: K.keyFor(scope, b) });
      };
      send.disabled = true;
      note.hidden = true;
      post(body).catch(function (e) {
        var q = e.data && e.data.quote;
        if ((e.code !== 'confirm_required' && e.code !== 'price_changed') || !q) throw e;
        var msg = t('dm.q', { fee: q.deliveryFeeMinor ? K.money(q.deliveryFeeMinor) : t('free'), total: K.money(q.totalMinor) }) +
          (q.eta ? ' ' + t('dm.eta', { a: K.fa(q.eta.minMinutes, 0), b: K.fa(q.eta.maxMinutes, 0) }) : '');
        return K.confirm(msg, t('dm.yes')).then(function (yes) {
          if (!yes) return null;
          var b = JSON.parse(JSON.stringify(body));
          b.expectedTotalMinor = q.totalMinor;
          return post(b);
        });
      }).then(function (r) {
        send.disabled = false;
        if (!r) return;
        K.dropKey(scope);
        $('o-dm').removeAttribute('data-for');
        reload();
        K.toast(t('dm.done'));
      }).catch(function (e) { send.disabled = false; K.note(note, 'err', e.message); });
    }

    //  صفحهٔ دکان، پیش از سبد: روش و مسئولِ هر راهی که این ویترین دارد (همان دادهٔ برگهٔ ثبت، `features.deliveryMethod`)
    function shop(dm) {
      var box = $('store-dm');
      var list = ['delivery', 'pickup'].map(function (k) { return dm && dm[k]; }).filter(Boolean);
      if (!box) {
        box = el('p', { class: 'muted small', attrs: { id: 'store-dm', role: 'note' } });
        var at = $('store-status');
        at.parentNode.insertBefore(box, at.nextSibling);
      }
      box.hidden = !list.length;
      box.textContent = list.length ? t('dm.title') + ': ' + list.map(line).join(' · ') : '';
    }

    return K.part('method', $('o-dm')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { checkout: checkout, order: order, shop: shop };
    });
  };
})();
