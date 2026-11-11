/*
 * ویترین — «جست‌وجو با عکس» (AI-11): دکمه‌ای زیرِ کادرِ جست‌وجو؛ عکس (دوربین یا گالریِ گوشی) کوچک و JPEG می‌شود و به
 * `POST /api/store/:slug/visual-search` می‌رود. پاسخ فقط فهرستِ «مشابه» از کالاهای منتشرشدهٔ همین دکان است و هر ردیف فقط
 * پیوندی به صفحهٔ همان کالا (`#/p/<id>`). فقط با `features.visualSearch` ِ سرور (پرچمِ `shop.storefront.visual_search` و پرچمِ
 * نمایه) و پس از بارِ نخست (`catalog.js` ِ دیربار)؛ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/vsearch.<زبان>.json`)،
 * نه دکمه و نه هیچ درخواستی.
 *
 * ⛔ برچسب همیشه «مشابه»، هرگز «همان کالا»؛ هیچ کالایی خودکار انتخاب یا به سبد افزوده نمی‌شود — فقط پیوند.
 * ⛔ عکس فقط برای همین یک درخواست است: نه روی دستگاه ذخیره می‌شود، نه جای دیگر. متن فقط با textContent (`K.el`).
 * S126 (شبکه و گوشیِ ضعیف): عکسِ ردیف (`/thumb`) با `K.photo` — ۵۰۴، اتصالِ بریده یا بایتِ خراب ⇒ حرفِ نخستِ نام در همان
 * ۹۶×۹۶، بی آیکنِ شکسته (همان کارتِ ویترین)؛ مرورگرِ بی `createImageBitmap` (گوشیِ کهنه) یا شکستِ آن ⇒ همان JPEG ِ کوچک از
 * راهِ `FileReader` ⇒ `data:` ⇒ `<img>` (CSP ِ ویترین `img-src 'self' data:`؛ نه `blob:`)، نه فرستادنِ فایلِ چند مگابایتیِ دوربین.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var ID = /^[A-Za-z0-9_.:-]{1,80}$/;
  var EDGE = 1024;          // لبهٔ بزرگ‌ترِ عکسِ فرستاده
  var MAX = 600 * 1024;     // همان سقفِ عکسِ کالا در سرور (`product-photos.MAX_BYTES`)
  var TYPES = /^image\/(jpeg|png)$/;
  var LIMIT = 8;

  function clean(r) {
    return (r && Array.isArray(r.items) ? r.items : []).filter(function (x) {
      var p = x && x.product;
      return p && typeof p.id === 'string' && ID.test(p.id) && x.product_id === p.id && typeof p.name === 'string' && p.name &&
        Number.isSafeInteger(p.priceMinor);
    });
  }

  /** S126: گشودنِ عکس بی `createImageBitmap` — FileReader ⇒ `data:` ⇒ `<img>`؛ نشد ⇒ رد (و همان فایل، `shrink`) */
  function viaImage(file) {
    if (!window.FileReader) return Promise.reject(new Error('no-reader'));
    return new Promise(function (ok, no) {
      var r = new FileReader();
      r.onerror = no;
      r.onload = function () {
        var img = new Image();
        img.onload = function () { ok(img); };
        img.onerror = no;
        img.src = r.result;
      };
      r.readAsDataURL(file);
    });
  }

  /** عکس ⇒ JPEG ِ کوچک (سرور JPEG ِ progressive، WebP و HEIC را نمی‌سنجد)؛ گشوده نشد ⇒ همان فایل */
  function shrink(file) {
    var open = window.createImageBitmap
      ? createImageBitmap(file).catch(function () { return viaImage(file); })
      : viaImage(file);
    return open.then(function (img) {
      var w = img.naturalWidth || img.width; var h = img.naturalHeight || img.height;
      if (!w || !h) return file;
      var k = Math.min(1, EDGE / Math.max(w, h));
      var c = document.createElement('canvas');
      c.width = Math.max(1, Math.round(w * k));
      c.height = Math.max(1, Math.round(h * k));
      var g = c.getContext('2d');
      g.fillStyle = '#fff';
      g.fillRect(0, 0, c.width, c.height);
      g.drawImage(img, 0, 0, c.width, c.height);
      if (img.close) img.close();
      return new Promise(function (ok) { c.toBlob(function (b) { ok(b || file); }, 'image/jpeg', 0.85); });
    }, function () { return file; });
  }

  function fail(key) { var e = new Error(t(key)); e.key = key; return e; }

  function send(base, blob) {
    if (!TYPES.test(blob.type || '')) return Promise.reject(fail('vs.bad'));
    if (blob.size > MAX) return Promise.reject(fail('vs.big'));
    return fetch(base + '/visual-search?limit=' + LIMIT, {
      method: 'POST', body: blob, headers: { Accept: 'application/json', 'Content-Type': blob.type },
      credentials: 'omit', cache: 'no-store',
    }).then(function (res) {
      return res.json().catch(function () { return {}; }).then(function (body) {
        if (res.ok) return body;
        var code = (body && body.error && body.error.code) || '';
        if (res.status === 429) throw fail('err.rate');
        if (res.status === 413 || code === 'too_large') throw fail('vs.big');
        if (res.status === 400 || res.status === 422 || /^image/.test(code)) throw fail('vs.bad');
        throw fail('vs.err');
      });
    }, function () { throw fail('err.network'); });
  }

  function card(base, p) {
    var go = el('a', { class: 'btn btn-ghost', text: t('vs.open'), attrs: { href: '#/p/' + encodeURIComponent(p.id) } });
    return el('li', { class: 'banner card', attrs: { 'data-vs': p.id } },
      //  S126: عکسِ خراب یا نرسیده ⇒ حرفِ نخستِ نام در همان قاب (`K.photo`)، نه آیکنِ شکسته
      p.photo ? el('span', { class: 'thumb tile vs-img', attrs: { 'aria-hidden': 'true' } },
        K.photo({ src: base + '/products/' + encodeURIComponent(p.id) + '/thumb', alt: '', loading: 'lazy', decoding: 'async', width: '96', height: '96' }, p.name)) : null,
      el('span', { class: 'pill plain', text: t('vs.similar') }),
      el('b', { text: p.name }),
      el('span', { class: 'num', text: K.money(p.priceMinor) + (p.unit ? ' ' + t('p.per', { unit: p.unit }) : '') }),
      go);
  }

  /**
   * ctx = { base, anchor }: بخش پس از نوارِ جست‌وجو (پدرِ `anchor`) می‌نشیند. برمی‌گرداند Promise<boolean> (ساخته شد؟)
   * — هرگز رد نمی‌شود. واژه‌نامه نیامد ⇒ هیچ گره‌ای نمی‌ماند.
   */
  window.StoreVisual = function (ctx) {
    var bar = ctx.anchor && ctx.anchor.parentNode;
    if (!bar || !bar.parentNode) return Promise.resolve(false);
    var sec = el('section', { class: 'section', hidden: true, attrs: { id: 'vs', 'aria-labelledby': 'vs-title' } });
    bar.parentNode.insertBefore(sec, bar.nextSibling);
    return K.part('vsearch', $('vs')).then(function (ok) {
      if (!ok) { sec.parentNode.removeChild(sec); return false; }
      var file = el('input', { class: 'sr-only', hidden: true, attrs: { id: 'vs-file', type: 'file', accept: 'image/jpeg,image/png', tabindex: '-1', 'aria-hidden': 'true' } });
      var btn = el('button', { class: 'btn btn-ghost', attrs: { id: 'vs-btn', type: 'button' }, on: { click: function () { file.value = ''; file.click(); } } },
        K.icon('search'), t('vs.btn'));
      var msg = el('p', { class: 'small muted', attrs: { id: 'vs-msg', 'aria-live': 'polite' } });
      var ul = el('ul', { class: 'banners', attrs: { id: 'vs-list', 'aria-label': t('vs.list') } });
      var close = el('button', { class: 'btn btn-ghost', text: t('vs.close'), attrs: { id: 'vs-close', type: 'button' }, on: { click: function () { out.hidden = true; K.clear(ul); } } });
      var out = el('div', { hidden: true, attrs: { id: 'vs-out' } },
        el('div', { class: 'section-head' }, el('h2', { text: t('vs.title'), attrs: { id: 'vs-title' } }), close), msg, ul);
      var busy = false;
      file.addEventListener('change', function () {
        var f = file.files && file.files[0];
        if (!f || busy) return;
        busy = true; btn.disabled = true;
        K.clear(ul); out.hidden = false; msg.textContent = t('vs.busy');
        shrink(f).then(function (b) { return send(ctx.base, b); }).then(function (r) {
          var list = clean(r);
          list.forEach(function (x) { ul.appendChild(card(ctx.base, x.product)); });
          msg.textContent = list.length ? t('vs.notice') : t('vs.none');
        }, function (e) { msg.textContent = e.key ? e.message : t('vs.err'); })
          .then(function () { busy = false; btn.disabled = false; });
      });
      sec.appendChild(el('div', null, btn, file));
      sec.appendChild(el('p', { class: 'small muted', text: t('vs.hint') }));
      sec.appendChild(out);
      sec.hidden = false;
      return true;
    }).catch(function () { return false; });
  };
})();
