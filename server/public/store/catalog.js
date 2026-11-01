/*
 * ویترین — بخش‌های دیربارِ کاتالوگ: ردیف‌های «پیشنهادِ همین دکان»، «ارزان‌تر از قیمتِ دکان» و «تازه در ویترین»
 * (WEB-02) و راه‌اندازیِ «دیده‌شده‌های اخیر» (WEB-13، `recent.js`). پس از بارِ نخست و هم‌زمان با کالاها بار می‌شود
 * (`loadMore` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ سقفِ ۷۵KB بماند (WEB-15، §۳.۱۰)؛ کارت، دسته، جست‌وجو،
 * برگهٔ کالا و سبد همان store.js است. نیامد (شبکه) ⇒ همان کاتالوگ، بی این ردیف‌ها.
 *
 * ⛔ فقط دادهٔ واقعیِ سرور؛ نبود ⇒ بخش پنهان. متن فقط با textContent.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /**
   * ctx = { api, slug, store (getter), rail, card, items, recent } از store.js.
   * برمی‌گرداند { picks, seen }: `picks()` ردیف‌ها را پر می‌کند و هرگز رد نمی‌شود؛ `seen(p)` کالای دیده‌شده.
   */
  window.StoreCatalog = function (ctx) {
    var api = ctx.api; var rail = ctx.rail;

    //  WEB-13: فقط با کلیدِ `recent` ِ سرور؛ کالایی که پیش از رسیدنِ recent.js دیده شد در صف می‌ماند
    var recent = null;
    if (ctx.recent) {
      recent = new Promise(function (ok, no) {
        document.head.appendChild(el('script', { attrs: { src: '/store/recent.js' }, on: { load: ok, error: no } }));
      }).then(function () { return window.StoreRecent.start({ slug: ctx.slug, api: api, card: ctx.card, items: ctx.items }); });
      recent.catch(function () { recent = null; });
    }

    /** WEB-02: پرفروش (یا تازه)، «ارزان‌تر» و تازه‌ها — فقط دادهٔ واقعیِ سرور (#301)؛ نبود ⇒ هیچ */
    function picks() {
      //  سرورِ بی #301 این مسیر را ندارد؛ #301 همراهِ `store.delivery` می‌آید
      if (!ctx.store.delivery) return Promise.resolve();
      return api('/highlights').then(function (r) {
        var best = r.bestsellers || []; var fresh = r.newArrivals || [];
        rail('picks', best.length ? best : fresh);
        $('picks-why').textContent = best.length ? t('picks.best', { d: K.fa(r.windowDays || 30, 0) }) : t('picks.new');
        rail('deals', r.deals || []);
        rail('fresh', best.length ? fresh.filter(function (p) { return !best.some(function (b) { return b.id === p.id; }); }) : []);
      }).catch(function () { $('picks').hidden = true; $('deals').hidden = true; $('fresh').hidden = true; });
    }

    return {
      picks: picks,
      seen: function (p) { if (recent) recent.then(function (r) { r.seen(p); }, function () { /* بی فهرستِ اخیر */ }); },
    };
  };
})();
