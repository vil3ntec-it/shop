/*
 * ویترین — بزرگ‌نماییِ عکسِ کالا روی برگهٔ کالا (IMG-11). از product.js ِ دیربار و فقط برای کالای عکس‌دار بار می‌شود.
 *
 * ⛔ نسخهٔ بزرگ (`…/zoom`، ۲۴۰۰px ِ ساختهٔ سرور پشتِ پرچمِ خاموشِ `image_bank.zoom`) فقط وقتی بیننده باز می‌شود خواسته
 *    می‌شود (اینترنتِ کند)؛ نبودش (۴۰۴) یا خطا ⇒ همان عکسی که برگه نشان می‌دهد (display یا عکسِ گالری) بزرگ می‌شود.
 * ⛔ بی innerHTML، متن فقط با textContent؛ CSP ِ سخت (`style-src 'self'`): جابه‌جایی فقط با CSSOM (`style.transform`).
 * بزرگ‌نمایی: دو انگشت، چرخِ موش، دو بار زدن، دکمه‌های − و +، و صفحه‌کلید (+، −، ۰، جهت‌ها، Esc)؛ کشیدن فقط وقتی بزرگ است،
 * و هرگز بیرون از قاب. واژه‌ها در واژه‌نامهٔ جدای `zoom` (فقط با نخستین کالای عکس‌دار، نه بارِ نخستِ ویترین).
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var MIN = 1; var MAX = 4; var STEP = 1.5; var TAP = 2.5;
  var v = null;   //  بیننده (یک بار در صفحه)

  function clamp(s) { return Math.min(MAX, Math.max(MIN, s)); }

  function build() {
    if (v) return v;
    var S = { s: 1, x: 0, y: 0, last: null };
    var img = el('img', { class: 'zm-img', attrs: { alt: '', draggable: 'false' } });
    var stage = el('div', { class: 'zm-stage' }, img);
    var level = el('span', { class: 'zm-level num', attrs: { 'aria-live': 'polite' } });
    var key = function (cls, label, txt, fn) {
      return el('button', { class: 'btn btn-neutral btn-icon ' + cls, text: txt, attrs: { type: 'button', 'aria-label': label }, on: { click: fn } });
    };
    var out = key('zm-out', t('zm.out'), '−', function () { zoomAt(1 / STEP, 0, 0); });
    var inn = key('zm-in', t('zm.in'), '+', function () { zoomAt(STEP, 0, 0); });
    var reset = el('button', { class: 'btn btn-neutral btn-sm zm-reset', text: t('zm.reset'), attrs: { type: 'button' }, on: { click: function () { set(1, 0, 0); } } });
    var close = key('zm-close', t('zm.close'), '', hide);
    close.appendChild(K.icon('close'));
    var note = el('p', { class: 'zm-hint small', attrs: { 'aria-live': 'polite' } });
    var root = el('div', { class: 'zm', hidden: true, attrs: { id: 'zm', role: 'dialog', 'aria-modal': 'true', 'aria-label': t('zm.title') } },
      stage, el('div', { class: 'zm-bar' }, out, level, inn, reset, close), note);
    document.body.appendChild(root);

    //  مرزِ کشیدن: عکسِ بزرگ‌شده هرگز از قاب بیرون نمی‌رود
    function bounds(s) {
      return { x: Math.max(0, (img.offsetWidth * s - stage.clientWidth) / 2), y: Math.max(0, (img.offsetHeight * s - stage.clientHeight) / 2) };
    }
    function set(s, x, y) {
      s = clamp(s);
      var b = bounds(s);
      S.s = s; S.x = Math.min(b.x, Math.max(-b.x, x)); S.y = Math.min(b.y, Math.max(-b.y, y));
      img.style.transform = 'translate(' + S.x + 'px,' + S.y + 'px) scale(' + S.s + ')';
      level.textContent = t('zm.level', { n: K.fa(Math.round(S.s * 100), 0) });
      out.disabled = S.s <= MIN; inn.disabled = S.s >= MAX;
      stage.classList.toggle('zoomed', S.s > 1);
    }
    //  نقطهٔ c (نسبت به مرکزِ قاب) زیرِ انگشت/موش می‌ماند
    function zoomAt(f, cx, cy) {
      var s = clamp(S.s * f); var k = s / S.s;
      set(s, cx - (cx - S.x) * k, cy - (cy - S.y) * k);
    }
    function rel(x, y) { var r = stage.getBoundingClientRect(); return { x: x - r.left - r.width / 2, y: y - r.top - r.height / 2 }; }

    stage.addEventListener('wheel', function (e) {
      e.preventDefault();
      var p = rel(e.clientX, e.clientY);
      zoomAt(e.deltaY < 0 ? 1.2 : 1 / 1.2, p.x, p.y);
    }, { passive: false });

    //  یک انگشت/موش: کشیدن (فقط بزرگ) و دو بار زدن؛ دو انگشت: بزرگ‌نمایی گردِ میانه
    var pts = new Map(); var g = null; var moved = 0; var tapAt = 0;
    function list() { var a = []; pts.forEach(function (p) { a.push(p); }); return a; }
    function begin() {
      var a = list();
      if (a.length >= 2) {
        var m = rel((a[0].x + a[1].x) / 2, (a[0].y + a[1].y) / 2);
        return { pinch: true, d: Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y) || 1, s: S.s, x: S.x, y: S.y, m: m };
      }
      return a.length === 1 ? { pinch: false, px: a[0].x, py: a[0].y, x: S.x, y: S.y } : null;
    }
    stage.addEventListener('pointerdown', function (e) {
      if (stage.setPointerCapture) { try { stage.setPointerCapture(e.pointerId); } catch (x) { /* بی گرفتن */ } }
      pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (pts.size === 1) moved = 0;
      g = begin();
    });
    stage.addEventListener('pointermove', function (e) {
      if (!pts.has(e.pointerId) || !g) return;
      var p0 = pts.get(e.pointerId);
      moved += Math.abs(e.clientX - p0.x) + Math.abs(e.clientY - p0.y);
      pts.set(e.pointerId, { x: e.clientX, y: e.clientY });
      if (g.pinch) {
        var a = list();
        if (a.length < 2) return;
        var s = clamp(g.s * Math.hypot(a[0].x - a[1].x, a[0].y - a[1].y) / g.d); var k = s / g.s;
        var m = rel((a[0].x + a[1].x) / 2, (a[0].y + a[1].y) / 2);
        set(s, g.m.x - (g.m.x - g.x) * k + (m.x - g.m.x), g.m.y - (g.m.y - g.y) * k + (m.y - g.m.y));
      } else if (S.s > 1) {
        set(S.s, g.x + (e.clientX - g.px), g.y + (e.clientY - g.py));
      }
    });
    function lift(e) {
      if (!pts.has(e.pointerId)) return;
      var p = pts.get(e.pointerId);
      pts.delete(e.pointerId);
      g = begin();
      if (pts.size || moved > 12 || e.type === 'pointercancel') return;
      //  دو بار زدن ⇒ بزرگ گردِ همان نقطه، یا برگشت
      var now = Date.now();
      if (now - tapAt < 320) {
        tapAt = 0;
        if (S.s > 1) set(1, 0, 0);
        else { var c = rel(p.x, p.y); zoomAt(TAP, c.x, c.y); }
      } else tapAt = now;
    }
    stage.addEventListener('pointerup', lift);
    stage.addEventListener('pointercancel', lift);

    root.addEventListener('keydown', function (e) {
      //  برگهٔ کالای زیرین Esc و Tab را نمی‌گیرد
      e.stopPropagation();
      var k = e.key;
      if (k === 'Escape') { e.preventDefault(); hide(); return; }
      if (k === '+' || k === '=') { e.preventDefault(); zoomAt(STEP, 0, 0); return; }
      if (k === '-' || k === '_') { e.preventDefault(); zoomAt(1 / STEP, 0, 0); return; }
      if (k === '0') { e.preventDefault(); set(1, 0, 0); return; }
      var d = { ArrowLeft: [60, 0], ArrowRight: [-60, 0], ArrowUp: [0, 60], ArrowDown: [0, -60] }[k];
      if (d && S.s > 1) { e.preventDefault(); set(S.s, S.x + d[0], S.y + d[1]); return; }
      if (k === 'Tab') {
        var f = Array.prototype.filter.call(root.querySelectorAll('button'), function (b) { return !b.disabled; });
        if (!f.length) return;
        var i = f.indexOf(document.activeElement);
        e.preventDefault();
        f[(i + (e.shiftKey ? -1 : 1) + f.length) % f.length].focus();
      }
    });

    function show(src, fallback, alt, opener) {
      S.last = opener || document.activeElement;
      img.alt = alt || '';
      //  نسخهٔ بزرگ نیست (۴۰۴، پرچمِ خاموش، سرِ پرِ ساخت) ⇒ همان عکسِ برگه، با گفتنش
      img.onerror = function () {
        if (fallback && img.getAttribute('src') !== fallback) img.setAttribute('src', fallback);
        else { root.removeAttribute('aria-busy'); note.textContent = ''; }
      };
      img.onload = function () {
        root.removeAttribute('aria-busy');
        note.textContent = t(img.getAttribute('src') === src ? 'zm.hint' : 'zm.small');
        set(1, 0, 0);
      };
      root.setAttribute('aria-busy', 'true');
      note.textContent = t('zm.loading');
      img.setAttribute('src', src);
      root.hidden = false;
      set(1, 0, 0);
      close.focus();
    }
    function hide() {
      if (root.hidden) return;
      root.hidden = true;
      img.removeAttribute('src');   //  بارِ نسخهٔ بزرگ نیمه‌راه می‌ماند
      pts.clear(); g = null;
      if (S.last && S.last.focus && document.contains(S.last)) S.last.focus();
    }
    //  برگشتِ مرورگر برگهٔ کالا را می‌بندد ⇒ بیننده هم
    window.addEventListener('hashchange', hide);
    v = { show: show, hide: hide, root: root };
    return v;
  }

  /** نشانیِ نسخهٔ بزرگِ همان عکسی که برگه نشان می‌دهد (عکسِ کالا یا عکسِ گالری)؛ `?v=` همان می‌ماند */
  function zoomOf(src) {
    return String(src || '').replace(/\/(photo|display)(\?|$)/, '/zoom$2');
  }

  /** برمی‌گرداند `show(product)` (نشانی‌ها از همان عکسِ برگه)؛ `product` تهی یا بی عکس ⇒ دکمه پنهان و بیننده بسته. */
  window.StoreZoom = function () {
    var cur = null; var downAt = null;
    function opener() {
      var b = $('ps-zoom');
      if (b) return b;
      var box = $('ps-img');
      if (!box || !box.parentNode) return null;
      b = el('button', { class: 'btn btn-ghost btn-sm ps-zoom', hidden: true, attrs: { id: 'ps-zoom', type: 'button' }, on: { click: function () { open(b); } } },
        K.icon('search'), el('span', { text: t('zm.open') }));
      box.parentNode.insertBefore(b, box.nextSibling);
      //  زدنِ خودِ عکس (نه کشیدنِ گالری)
      box.addEventListener('pointerdown', function (e) { downAt = { x: e.clientX, y: e.clientY }; });
      box.addEventListener('click', function (e) {
        var far = downAt && Math.abs(e.clientX - downAt.x) + Math.abs(e.clientY - downAt.y) > 12;
        downAt = null;
        if (cur && !far && e.target && e.target.tagName === 'IMG') open(b);
      });
      return b;
    }
    function open(from) {
      var img = $('ps-img') && $('ps-img').querySelector('img');
      if (!cur || !img) return;
      var src = img.getAttribute('src');
      build().show(zoomOf(src), src, cur.name || '', from);
    }
    return function show(p) {
      cur = null;
      var b = opener();
      if (v) v.hide();
      if (b) b.hidden = true;
      if ($('ps-img')) $('ps-img').classList.remove('zoomable');
      if (!p || !p.photo || !b) return;
      cur = p;
      K.part('zoom', $('ps-zoom')).then(function (ok) {
        if (!ok || cur !== p) return;
        b.lastChild.textContent = t('zm.open');
        b.hidden = false;
        $('ps-img').classList.add('zoomable');
      });
    };
  };
})();
