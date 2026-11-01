/*
 * ویترین — فرستادنِ نشانیِ کالا (WEB-20). فقط وقتی `GET /api/store/<slug>` کلیدِ `seo.productBase`
 * دارد (پرچمِ `shop.storefront.seo`) و دیر بار می‌شود، تا بارِ نخستِ صفحه سبک بماند؛
 * بردنِ `/p/<id>` به `#/p/<id>` (`fromProductPath`) در خودِ store.js است.
 */
(function () {
  'use strict';
  var t = K.t;

  /** WEB-20 — نشانیِ واقعیِ کالا (`…/p/<id>`) که واتس‌اپ و فیسبوک پیش‌نمایشش را می‌سازند. */
  window.StoreShare = function (shareBase) {
    return function shareProduct(p) {
      var url = shareBase + encodeURIComponent(p.id);
      if (navigator.share) { navigator.share({ title: p.name, url: url }).catch(function () { /* لغو */ }); return; }
      if (navigator.clipboard && navigator.clipboard.writeText) {
        navigator.clipboard.writeText(url).then(function () { K.toast(t('ps.copied')); }, function () { K.toast(url); });
        return;
      }
      K.toast(url);
    };
  };
})();
