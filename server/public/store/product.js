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

  /** `ctx.rail(id, list)` از store.js (ردیفِ کارت). برمی‌گرداند `detail(r)`؛ `r` تهی ⇒ همه پاک و پنهان. */
  window.StoreProduct = function (ctx) {
    var rail = ctx.rail;
    return function detail(r) {
      var x = r ? r.product : {};
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
