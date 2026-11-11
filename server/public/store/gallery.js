/*
 * ویترین — گالریِ عکس‌های بیشترِ کالا روی برگهٔ کالا (PRD-17/27، تصمیمِ ۴-۲۲؛ پرچمِ `shop.storefront.gallery`).
 * از product.js ِ دیربار و فقط با `features.gallery` بار می‌شود؛ فهرست فقط از `GET …/products/<id>/gallery` (tag و نقش).
 *
 * ⛔ عکسِ نخست همان عکسِ امروزِ برگه است؛ هیچ عکسِ دیگری پیش از زدن یا کشیدن بار نمی‌شود (اینترنتِ کند).
 * ⛔ متن فقط با textContent؛ پاسخِ دیررس برگهٔ کالای دیگری را پر نمی‌کند؛ فهرستِ تهی یا خطا ⇒ هیچ بخشی.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var ROLE = { detail: 'gl.r.detail', back: 'gl.r.back', label: 'gl.r.label', variant: 'gl.r.variant' };
  var seq = 0;
  var cur = null;   //  { slides, at, go } ِ برگهٔ باز

  function nav() {
    var n = $('ps-gal');
    if (n) return n;
    var img = $('ps-img');
    if (!img || !img.parentNode) return null;
    n = el('nav', { class: 'ps-gal', hidden: true, attrs: { id: 'ps-gal' } });
    img.parentNode.insertBefore(n, img.nextSibling);
    //  کشیدن روی عکس: در راست‌به‌چپ عکسِ بعدی از چپ می‌آید
    var x0 = null;
    img.addEventListener('pointerdown', function (e) { x0 = e.clientX; });
    //  کشیدنِ عکس با موش «کشیدن و رها کردن» ِ مرورگر نشود (وگرنه pointerup نمی‌رسد)
    img.addEventListener('dragstart', function (e) { if (cur) e.preventDefault(); });
    img.addEventListener('pointerup', function (e) {
      if (x0 === null || !cur) return;
      var dx = e.clientX - x0; x0 = null;
      if (Math.abs(dx) < 40) return;
      var fwd = document.documentElement.dir === 'rtl' ? dx > 0 : dx < 0;
      cur.go(cur.at + (fwd ? 1 : -1));
    });
    return n;
  }

  /** ctx = { slug }. برمی‌گرداند `show(product)`؛ `product` تهی ⇒ بخش پنهان. */
  window.StoreGallery = function (ctx) {
    var base = '/api/store/' + encodeURIComponent(ctx.slug) + '/products/';
    return function show(p) {
      var my = ++seq;
      cur = null;
      var n = nav();
      if (!n) return;
      n.hidden = true;
      K.clear(n);
      if (!p || typeof p.id !== 'string') return;
      var at = base + encodeURIComponent(p.id) + '/gallery';
      K.api(at).then(function (r) {
        var items = (r && Array.isArray(r.items) ? r.items : []).filter(function (i) {
          return i && /^[0-9a-f]{12}$/.test(i.tag) && ROLE[i.role];
        });
        if (my !== seq || !items.length) return null;
        return K.part('gallery', $('ps-gal')).then(function (ok) {
          if (!ok || my !== seq) return;
          var box = $('ps-img');
          var first = { nodes: Array.prototype.map.call(box.childNodes, function (x) { return x.cloneNode(true); }), cls: box.className };
          var total = items.length + 1;
          var count = el('span', { class: 'small muted', attrs: { 'aria-live': 'polite' } });
          var dots = [];
          var state = { at: 0, go: go };
          function label(i) { return t('gl.of', { i: K.fa(i + 1, 0), n: K.fa(total, 0) }) + ' — ' + t(i ? ROLE[items[i - 1].role] : 'gl.main'); }
          function go(i) {
            if (i < 0 || i >= total || i === state.at || cur !== state) return;
            state.at = i;
            K.clear(box);
            if (i === 0) {
              first.nodes.forEach(function (x) { box.appendChild(x.cloneNode(true)); });
              box.className = first.cls;
            } else {
              var it = items[i - 1];
              box.appendChild(K.photo({ src: at + '/' + it.tag + '/display', alt: t(ROLE[it.role]), width: '300', height: '300' }, p.name));
              box.classList.add('photo');
            }
            dots.forEach(function (d, k) { if (k === i) d.setAttribute('aria-current', 'true'); else d.removeAttribute('aria-current'); });
            count.textContent = label(i);
          }
          var prev = el('button', { class: 'btn btn-icon btn-ghost', text: '‹', attrs: { type: 'button', 'aria-label': t('gl.prev') },
            on: { click: function () { go(state.at - 1); } } });
          var next = el('button', { class: 'btn btn-icon btn-ghost', text: '›', attrs: { type: 'button', 'aria-label': t('gl.next') },
            on: { click: function () { go(state.at + 1); } } });
          n.setAttribute('aria-label', t('gl.label'));
          n.appendChild(prev);
          for (var i = 0; i < total; i++) {
            dots.push(el('button', { class: 'btn btn-icon btn-ghost dot', text: K.fa(i + 1, 0), attrs: { type: 'button', 'aria-label': label(i) },
              on: { click: go.bind(null, i) } }));
          }
          dots.forEach(function (d) { n.appendChild(d); });
          n.append(next, count, el('p', { class: 'small muted', text: t('gl.hint') }));
          dots[0].setAttribute('aria-current', 'true');
          count.textContent = label(0);
          cur = state;
          n.hidden = false;
        });
      }).catch(function () { /* پرچم خاموش یا خطا ⇒ بی گالری */ });
    };
  };
})();
