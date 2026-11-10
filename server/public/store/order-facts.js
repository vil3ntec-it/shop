/*
 * ویترین — خلاصهٔ صفحهٔ تأیید/پیگیری (CHK-20، §۱۹.۲ بندِ ۸): «دکانِ مسئول، روشِ تحویل، زمان … و لینکِ پیگیری».
 * شماره، وضعیت، اقلام، کرایه و «مانده (نقد هنگامِ تحویل)» از پیش در `order.js` بود؛ این‌جا آن‌چه نبود:
 *   • دکانِ مسئول (نامِ همان ویترین)، ناحیهٔ ارسال (`zoneName`) و بازهٔ برگزیدهٔ تحویل/دریافت (`scheduledFor` تا
 *     `slotEndsAt`، CHK-12) — همان متنِ گزینهٔ فرمِ ثبت: روز در منطقهٔ زمانیِ دکان و ساعت با `K.hm`؛
 *   • «کپیِ نشانیِ پیگیری»: فقط `#/o/<توکن>` روی همین نشانیِ ویترین (بی `?c=`/`?lang=`)؛ کلیپ‌بورد نشد ⇒ همان نشانی
 *     در کادرِ فقط‌خواندنی و برگزیده. هشدار کنارِ دکمه: هر دارندهٔ نشانی سفارش را می‌بیند و تا قبول لغوش می‌کند.
 * دیربار از `order.js` (فقط صفحهٔ سفارش)، با واژه‌نامهٔ `kit/i18n/facts.<زبان>.json`.
 *
 * ⛔ هر متن فقط با textContent (`K.el`)؛ نامِ دکان و ناحیه متنِ دکان‌اند. هیچ دادهٔ تازه‌ای از سرور نمی‌خواهد.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  window.StoreOrderFacts = function (ctx) {
    //  ریشه فقط گره‌های خودِ همین بخش: `translate` ِ کلِ `#view-order` عنوانِ «سفارشِ شمارهٔ …» را به «سفارش» برمی‌گرداند
    return K.part('facts', $('o-facts')).then(function () { return make(ctx); });
  };

  /** دقیقهٔ روزِ `at` در منطقهٔ زمانیِ دکان — همان `startMin` ِ بازه */
  function minuteOf(at, tz) {
    try {
      var p = new Intl.DateTimeFormat('en-GB', { timeZone: tz, hour: '2-digit', minute: '2-digit', hourCycle: 'h23' }).formatToParts(new Date(at));
      var n = function (type) { return Number((p.filter(function (x) { return x.type === type; })[0] || {}).value); };
      var m = n('hour') * 60 + n('minute');
      return Number.isFinite(m) ? m : null;
    } catch (e) { return null; }
  }

  /** «سه‌شنبه ۱۸ عقرب، ۲۳:۰۰–۲۳:۵۹» — همان روز و ساعتِ گزینهٔ فرمِ ثبت (`checkout.js`)؛ نادانسته ⇒ '' */
  function slotText(o, tz) {
    var from = Number(o.scheduledFor);
    if (!Number.isFinite(from) || from <= 0) return '';
    var start = minuteOf(from, tz);
    if (start === null) return '';
    var to = Number(o.slotEndsAt);
    var h = K.hm(start) + (Number.isFinite(to) && to > from ? '–' + K.hm(start + Math.round((to - from) / 60000)) : '');
    var d = '';
    try { d = new Date(from).toLocaleDateString(K.lang === 'en' ? 'en-GB' : 'fa-AF', { timeZone: tz, weekday: 'long', day: 'numeric', month: 'long' }); } catch (e) { d = ''; }
    return d ? t('of.slot', { d: d, h: h }) : h;
  }

  /** نشانیِ پیگیریِ همین سفارش روی همین ویترین (میزبانِ دکان یا `/store/<slug>`) */
  function linkOf(token) {
    return location.origin + location.pathname + '#/o/' + encodeURIComponent(token);
  }

  function make(ctx) {
    var store = ctx.store || {};
    var tz = (store.status && store.status.timezone) || 'Asia/Kabul';
    var link = null;

    function facts(o) {
      var dl = $('o-facts');
      if (!dl) {
        dl = el('dl', { class: 'ps-specs', attrs: { id: 'o-facts' } });
        $('o-meta').parentNode.insertBefore(dl, $('o-meta').nextSibling);
      }
      var rows = [['of.shop', store.title || '']];
      if (o.fulfillment === 'delivery' && o.zoneName) rows.push(['of.zone', o.zoneName]);
      var when = slotText(o, tz);
      if (when) rows.push([o.fulfillment === 'pickup' ? 'of.when.pickup' : 'of.when.delivery', when]);
      K.clear(dl);
      rows.forEach(function (r) {
        if (r[1]) dl.append(el('dt', { text: t(r[0]) }), el('dd', { text: r[1], attrs: { 'data-f': r[0] } }));
      });
      dl.hidden = !dl.firstChild;
    }

    function copyBox() {
      if (link) return link;
      link = el('div', { class: 'stack', attrs: { id: 'o-link' } },
        el('button', { class: 'btn btn-ghost btn-block', text: t('of.link'), attrs: { id: 'o-link-copy', type: 'button' } }),
        el('label', { class: 'field', hidden: true, attrs: { id: 'o-link-row' } }, el('span', { text: t('of.link.l') }),
          el('input', { attrs: { id: 'o-link-url', type: 'url', readonly: '', dir: 'ltr' } })),
        el('div', { class: 'note', hidden: true, attrs: { id: 'o-link-msg', role: 'status' } }),
        el('p', { class: 'muted small', text: t('of.link.hint'), attrs: { id: 'o-link-hint' } }));
      $('o-msg').parentNode.appendChild(link);   //  تهِ کارتِ کارها، پس از «این صفحه با همین نشانی دوباره باز می‌شود…»
      return link;
    }

    function linkPart(token) {
      var box = copyBox();
      box.hidden = false;
      if (box.getAttribute('data-for') === token) return;
      box.setAttribute('data-for', token);
      $('o-link-row').hidden = true;
      $('o-link-msg').hidden = true;
      var url = linkOf(token);
      $('o-link-url').value = url;
      $('o-link-copy').onclick = function () {
        var shown = function () {
          $('o-link-row').hidden = false;
          $('o-link-url').focus();
          $('o-link-url').select();
          K.note($('o-link-msg'), 'warn', t('of.link.no'));
        };
        if (!(navigator.clipboard && navigator.clipboard.writeText)) { shown(); return; }
        navigator.clipboard.writeText(url).then(function () { K.note($('o-link-msg'), 'ok', t('of.link.ok')); }, shown);
      };
    }

    //  خطای پیگیری این دو را پنهان می‌کند (`fail` ِ order.js)
    return { render: function (o, token) { facts(o); linkPart(token); } };
  }
})();
