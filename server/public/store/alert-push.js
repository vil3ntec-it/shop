/*
 * ویترین — «روی همین دستگاه هم خبرم کن» زیرِ «خبرم کن وقتی موجود/ارزان شد» ِ برگهٔ کالا: پوشِ وب برای «خبرم کن» ِ همین
 * شماره در همین دکان (WEB-10، `lib/store-alert-push.js`، مهاجرتِ ۴۷۵). دیربار از `store/filters.js` و فقط وقتی «هشدارهای من»
 * کلیدِ `push` را داد (پرچم‌های `shop.storefront.alerts` + `shop.storefront.web_push` + `shop.customer_notify`)؛ خاموش ⇒ نه این
 * اسکریپت، نه `push-client.js`، نه کارگرِ سرویس.
 *
 *   لمسِ «روی همین دستگاه هم خبرم کن» ⇒ اجازهٔ اعلانِ مرورگر ⇒ کارگرِ `store/sw.js` با دامنهٔ همین ویترین ⇒ اشتراک با کلیدِ
 *   عمومیِ سرور ⇒ `POST …/alerts/push`. «خاموش کن» ⇒ `DELETE` ِ همان اشتراک برای همین شماره.
 *   روشن ماندن برای **همین شماره** در همین مرورگر به خاطر سپرده می‌شود؛ هر بارِ تازهٔ صفحه با همان شماره اشتراک را بی پرسش
 *   تازه می‌کند (اجازه از پیش داده شده) تا عمرِ اشتراک با تأییدِ تازه برود؛ شمارهٔ دیگری در همین مرورگر خودش باید لمس کند.
 *
 * ⛔ هرگز بی لمسِ مشتری اجازه نمی‌پرسد. هر متن فقط با textContent (`K.el`). «روشن است» فقط پس از پاسخِ سرور.
 * مرورگرِ بی پوش ⇒ هیچ بخشی.
 */
(function () {
  'use strict';
  var el = K.el; var t = K.t;

  /** ctx = { api, slug } ⇒ { refresh(key), row(key) } */
  window.StoreAlertPush = function (ctx) {
    return K.js('/store/push-client.js').then(function () { return K.part('push'); }).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return make(ctx);
    });
  };

  function make(ctx) {
    var P = window.StorePush;
    var page = P.page(ctx.slug);
    var KEY = 'store-alert-push:' + ctx.slug;   //  { p: کلیدِ مشتری (ایمیلِ تأییدشده)، e: نشانیِ اشتراک } — این مرورگر برای همین مشتری روشن است
    var busy = false; var fresh = null; var key = ''; var row = null;

    function phone() { var tk = K.ticket(); return tk ? tk.email : ''; }   //  کلیدِ مشتری («فقط ایمیل»)
    function mine() { var m = K.load(KEY, null); return m && m.p && m.e && m.p === phone() ? m.e : ''; }
    function state() {
      if (Notification.permission === 'denied') return 'denied';
      return mine() && Notification.permission === 'granted' ? 'on' : 'idle';
    }
    function attach() {
      var who = phone();
      return P.subscription(key, page).then(function (sub) {
        return ctx.api('/alerts/push', { method: 'POST', body: { subscription: sub, page: page } })
          .then(function () { K.save(KEY, { p: who, e: sub.endpoint }); });
      });
    }

    function draw() {
      if (!row) return;
      var s = state();
      row.querySelector('#ps-ap-state').textContent = t(s === 'on' ? 'ap.state.on' : (s === 'denied' ? 'np.denied' : 'ap.hint'));
      var on = row.querySelector('#ps-ap-on'); var off = row.querySelector('#ps-ap-off');
      on.hidden = s !== 'idle'; off.hidden = s !== 'on';
      on.disabled = busy; off.disabled = busy;
      on.textContent = busy ? t('np.wait') : t('ap.on');
    }

    function turnOn() {
      if (busy) return;
      busy = true; draw();
      //  ⛔ اجازه همین‌جا، در دستگیرهٔ لمس (نه پس از پاسخِ شبکه)
      P.permission().then(function (p) {
        if (p !== 'granted') { busy = false; draw(); return null; }
        return attach().then(function () {
          busy = false; fresh = fresh || Promise.resolve();
          draw();
          K.toast(t('ap.state.on'));
        });
      }).catch(function (e) {
        busy = false; draw();
        K.toast(e && e.status ? e.message : t('np.fail'));
      });
    }

    function turnOff() {
      var endpoint = mine();
      if (busy || !endpoint) return;
      busy = true; draw();
      ctx.api('/alerts/push', { method: 'DELETE', body: { endpoint: endpoint } }).then(function () {
        busy = false; K.save(KEY, null); draw();
        K.toast(t('ap.state.off'));
      }, function (e) { busy = false; draw(); K.toast(e.message); });
    }

    return {
      /** «هشدارهای من» با کلیدِ push: یک بار در هر بارِ صفحه، اگر همین مرورگر برای همین شماره روشن است، بی پرسش تازه */
      refresh: function (k) {
        key = k;
        if (fresh || !P.supported || !mine() || Notification.permission !== 'granted') return;
        fresh = attach().catch(function () { fresh = null; });
      },
      /** بخشِ «روی همین دستگاه هم» برای زیرِ دکمه‌های «خبرم کن» (مرورگرِ بی پوش ⇒ null) */
      row: function (k) {
        key = k;
        if (!P.supported) return null;
        row = el('div', { class: 'stack', attrs: { id: 'ps-ap', role: 'group', 'aria-label': t('ap.title') } },
          el('p', { class: 'muted small', attrs: { id: 'ps-ap-state' } }),
          el('button', { class: 'btn btn-block', attrs: { id: 'ps-ap-on', type: 'button' }, on: { click: turnOn } }),
          el('button', { class: 'btn btn-ghost btn-block', text: t('np.off'), attrs: { id: 'ps-ap-off', type: 'button' }, on: { click: turnOff } }));
        draw();
        return row;
      },
    };
  }
})();
