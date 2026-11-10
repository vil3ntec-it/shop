/*
 * ویترین — بخش‌های برگهٔ کالا که از `GET /api/store/<slug>/products/<id>` می‌آیند (WEB-08):
 * توضیح، مشخصاتِ عمومی، «مشابه» و «مکمل». با نخستین بازشدنِ برگهٔ کالا و هم‌زمان با خودِ کالا
 * بار می‌شود (`loadProductView` در store.js) تا JS ِ بارِ اولِ ویترین زیرِ سقفِ ۷۵KB بماند
 * (WEB-15، §۳.۱۰)؛ خودِ برگه (نام، قیمت، موجودی، افزودن) و کارت‌ها همان store.js است.
 *
 * ⛔ متن فقط با textContent؛ هیچ دادهٔ ساختگی — نبودِ داده ⇒ بخش پنهان.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el;

  //  S70: «همین کالا در دکان‌های دیگر» (same.js ِ دیربار) فقط برای کالای پیوسته به بانک (`bankProductId`)
  var same = null; var sameP = null;
  function loadSame(slug) {
    sameP = sameP || K.js('/store/same.js').then(function () { same = window.StoreSame({ slug: slug }); return same; }, function (e) { sameP = null; throw e; });
    return sameP;
  }

  /** `ctx.rail(id, list)` از store.js (ردیفِ کارت)، `ctx.slug` دکانِ همین ویترین. برمی‌گرداند `detail(r)`؛ `r` تهی ⇒ همه پاک و پنهان. */
  window.StoreProduct = function (ctx) {
    var rail = ctx.rail; var want = 0;
    return function detail(r) {
      var x = r ? r.product : {};
      var my = ++want;
      if (same) same(null);
      //  same.js ِ دیررس برگهٔ کالای دیگری را پر نمی‌کند
      if (x.bankProductId) loadSame(ctx.slug).then(function (show) { if (my === want) show(x); }, function () { /* بی بخش */ });
      $('ps-desc').textContent = x.description || '';
      $('ps-desc').hidden = !x.description;
      var dl = K.clear($('ps-specs'));
      (x.specs || []).forEach(function (s) { dl.append(el('dt', { text: s.name }), el('dd', { text: s.value })); });
      dl.hidden = !dl.children.length;
      rail('ps-sim', r ? r.similar : []);
      rail('ps-comp', r ? r.complementary : []);
    };
  };
})();
