/*
 * ویترین — «گزارشِ مشکل» با عکس (NTF-07) و امتیاز و نظرِ سفارش (NTF-09) در صفحهٔ پیگیری.
 * فقط وقتی پاسخِ پیگیری کلیدِ `issueReports` یا `review` دارد بار می‌شود (`order.js`)؛ پرچم‌های خاموش ⇒ نه کلید،
 * نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/feedback.<زبان>.json`) و نه هیچ گرهٔ تازه‌ای در صفحه.
 *
 * ⛔ هر متنِ مشتری و پاسخِ دکان فقط با textContent (`K.el`) — هرگز HTML.
 * ⛔ عکس پیش از فرستادن در همین مرورگر دوباره JPEG می‌شود (بلندترین ضلع ۱۲۸۰، بی فراداده و زیرِ سقفِ سرور)؛
 *    سرور باز هم نوع، اندازه و ابعاد را از خودِ بایت‌ها می‌سنجد. تصمیمِ «می‌شود/نمی‌شود» همیشه با سرور است.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var SIDE = 1280;
  var KINDS = [{ kind: 'missing_item', label: 'fb.k.missing' }, { kind: 'wrong_item', label: 'fb.k.wrong' },
    { kind: 'damaged', label: 'fb.k.damaged' }, { kind: 'quality', label: 'fb.k.quality' },
    { kind: 'late', label: 'fb.k.late' }, { kind: 'other', label: 'fb.k.other' }];
  var REVIEW_NOTE = { pending: ['info', 'fb.rv.pending'], approved: ['ok', 'fb.rv.approved'], rejected: ['warn', 'fb.rv.rejected'] };

  function kindLabel(k) {
    for (var i = 0; i < KINDS.length; i++) if (KINDS[i].kind === k) return t(KINDS[i].label);
    return t('fb.k.other');
  }
  function newKey() {
    var a = new Uint8Array(12);
    (window.crypto || window.msCrypto).getRandomValues(a);
    return 'fb-' + Array.prototype.map.call(a, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
  }

  /** فایلِ برگزیده ⇒ base64 ِ JPEG ِ کوچک‌شده (زیرِ `maxBytes`)؛ data: چون CSP ِ ویترین blob: ندارد */
  function shrink(file, maxBytes) {
    return new Promise(function (ok, no) {
      var r = new FileReader();
      r.onerror = function () { no(new Error(t('fb.ph.bad'))); };
      r.onload = function () {
        var img = new Image();
        img.onerror = function () { no(new Error(t('fb.ph.bad'))); };
        img.onload = function () {
          var s = Math.min(1, SIDE / Math.max(img.naturalWidth || 1, img.naturalHeight || 1));
          var c = document.createElement('canvas');
          c.width = Math.max(1, Math.round(img.naturalWidth * s));
          c.height = Math.max(1, Math.round(img.naturalHeight * s));
          c.getContext('2d').drawImage(img, 0, 0, c.width, c.height);
          var q = 0.82; var out = c.toDataURL('image/jpeg', q);
          while (out.length * 3 / 4 > maxBytes && q > 0.35) { q -= 0.15; out = c.toDataURL('image/jpeg', q); }
          if (out.length * 3 / 4 > maxBytes) return no(new Error(t('fb.ph.bad')));
          ok(out.slice(out.indexOf(',') + 1));
        };
        img.src = String(r.result || '');
      };
      r.readAsDataURL(file);
    });
  }

  /** `ctx` از order.js: `api(path, opts)`؛ ⇒ وعده‌ای از `{render}` (پس از رسیدنِ واژه‌نامه) */
  window.StoreFeedback = function (ctx) {
    var api = ctx.api;
    var box = $('o-fb');
    if (!box) {
      box = el('div', { class: 'card card-lg stack', attrs: { id: 'o-fb' }, hidden: true });
      var at = $('o-rate');
      at.parentNode.insertBefore(box, at.nextSibling);
    }
    var cur = { token: '', o: null };

    function path(token, rest) { return '/orders/' + encodeURIComponent(token) + rest; }
    function refresh() {
      return api(path(cur.token, '')).then(function (r) { update(r.order); });
    }

    function build() {
      K.clear(box);
      //  ── گزارشِ مشکل ──
      var kind = el('select', { attrs: { id: 'fb-kind' } });
      KINDS.forEach(function (x) { kind.appendChild(el('option', { attrs: { value: x.kind }, text: t(x.label) })); });
      var send = el('button', { class: 'btn btn-accent', attrs: { type: 'submit', id: 'fb-send' }, text: t('fb.is.send') });
      var form = el('form', { class: 'stack', attrs: { id: 'fb-form', novalidate: '' } },
        el('label', { class: 'field' }, el('span', { text: t('fb.is.kind') }), kind),
        el('label', { class: 'field' }, el('span', { text: t('fb.is.text') }), el('textarea', { attrs: { id: 'fb-text', maxlength: '1000', required: '' } })),
        el('label', { class: 'field' }, el('span', { attrs: { id: 'fb-ph-l' } }),
          el('input', { attrs: { id: 'fb-photos', type: 'file', accept: 'image/jpeg,image/png,image/webp', multiple: '' } })),
        send);
      box.appendChild(el('section', { class: 'stack', attrs: { id: 'fb-is' }, hidden: true },
        el('h2', { text: t('fb.is.title') }), el('ul', { class: 'sum-lines', attrs: { id: 'fb-list' } }), form,
        el('div', { class: 'note', attrs: { id: 'fb-msg', role: 'status' }, hidden: true })));
      var key = newKey();
      form.addEventListener('submit', function (ev) {
        ev.preventDefault();
        var o = cur.o; var lim = (o && o.issueReports && o.issueReports.limits) || {};
        var files = Array.prototype.slice.call($('fb-photos').files || [], 0, lim.photos || 3);
        send.disabled = true;
        K.note($('fb-msg'), 'info', t('fb.is.sending'));
        api(path(cur.token, '/issues'), { method: 'POST', body: { kind: kind.value, text: $('fb-text').value.trim(), requestKey: key } })
          .then(function (r) {
            var id = r.issue.id;
            //  عکس‌ها یکی‌یکی؛ شکستِ یکی گزارش را پس نمی‌گیرد
            return files.reduce(function (p, f) {
              return p.then(function () { return shrink(f, lim.photoBytes || 600 * 1024); })
                .then(function (b64) { return api(path(cur.token, '/issues/' + encodeURIComponent(id) + '/photos'), { method: 'POST', body: { imageBase64: b64 } }); });
            }, Promise.resolve());
          })
          .then(function () {
            key = newKey(); form.reset();
            K.note($('fb-msg'), 'ok', t('fb.is.sent'));
            return refresh();
          })
          .catch(function (e) { K.note($('fb-msg'), 'err', e.message); refresh().catch(function () {}); })
          .then(function () { send.disabled = false; });
      });

      //  ── نظر ──
      var pick = { stars: 0 };
      var stars = el('div', { class: 'chips', attrs: { role: 'group', 'aria-label': t('fb.rv.title') } });
      var rsend = el('button', { class: 'btn btn-accent', attrs: { type: 'button', id: 'fb-rv-send', disabled: '' }, text: t('fb.rv.send') });
      [1, 2, 3, 4, 5].forEach(function (n) {
        stars.appendChild(el('button', { class: 'chip plain', attrs: { type: 'button', 'aria-pressed': 'false', 'aria-label': t('fb.rv.stars', { n: K.fa(n, 0) }) },
          text: K.fa(n, 0) + ' ★', on: { click: function () {
            pick.stars = n; rsend.disabled = false;
            Array.prototype.forEach.call(stars.children, function (c, i) { c.setAttribute('aria-pressed', String(i === n - 1)); });
          } } }));
      });
      box.appendChild(el('section', { class: 'stack', attrs: { id: 'fb-rv' }, hidden: true },
        el('h2', { text: t('fb.rv.title') }),
        el('div', { class: 'stack', attrs: { id: 'fb-rv-form' } }, stars,
          el('label', { class: 'field' }, el('span', { text: t('fb.rv.comment') }), el('textarea', { attrs: { id: 'fb-rv-text', maxlength: '500' } })),
          rsend),
        el('div', { class: 'note', attrs: { id: 'fb-rv-msg', role: 'status' }, hidden: true })));
      rsend.onclick = function () {
        rsend.disabled = true;
        api(path(cur.token, '/review'), { method: 'POST', body: { stars: pick.stars, comment: $('fb-rv-text').value.trim() } })
          .then(function () { return refresh(); })
          .catch(function (e) { rsend.disabled = false; K.note($('fb-rv-msg'), 'err', e.message); });
      };
    }

    function update(o) {
      cur.o = o;
      var ir = o.issueReports; var rv = o.review;
      box.hidden = !ir && !(rv && (rv.canReview || rv.review));
      $('fb-is').hidden = !ir;
      if (ir) {
        var ul = K.clear($('fb-list'));
        (ir.issues || []).forEach(function (x) {
          var done = x.status === 'resolved';
          ul.appendChild(el('li', { class: 'stack' },
            el('span', { class: 'grow' }, el('b', { text: kindLabel(x.kind) }), ' ', el('span', { class: 'pill' + (done ? ' ok' : ''), text: t(done ? 'fb.is.done' : 'fb.is.open') }), ' ',
              el('small', { class: 'muted', text: K.when(x.createdAt) + (x.photos ? t('sep') + t('fb.is.photoN', { n: K.fa(x.photos, 0) }) : '') })),
            el('span', { text: x.text }),
            x.reply ? el('small', { text: t('fb.is.reply', { r: x.reply }) }) : null));
        });
        $('fb-form').hidden = !ir.canReport;
        $('fb-ph-l').textContent = t('fb.is.photos', { n: K.fa((ir.limits && ir.limits.photos) || 3, 0) });
        if (!ir.canReport && (ir.issues || []).length) K.note($('fb-msg'), 'info', t('fb.is.max'));
      }
      $('fb-rv').hidden = !(rv && (rv.canReview || rv.review));
      if (rv) {
        $('fb-rv-form').hidden = !rv.canReview;
        var n = rv.review && REVIEW_NOTE[rv.review.status];
        if (n) K.note($('fb-rv-msg'), n[0], t(n[1]));
      }
    }

    function render(o, token) {
      if (cur.token !== token) { cur.token = token; build(); }
      update(o);
    }

    return K.part('feedback', $('o-fb')).then(function (ok) {
      if (!ok) throw new Error('i18n');
      return { render: render };
    });
  };
})();
