/*
 * ویترین — «درخواستِ برگشت» (RET-11، تصمیمِ ۱۳ «ب»). دیربار و فقط وقتی لازم است:
 *   order.js ── وقتی پیگیری کلیدِ `returnRequest` دارد (پرچمِ `shop.storefront.return_requests` و سفارشِ تحویل‌شده):
 *               وضعیتِ درخواست‌ها و فرم (قلم و اندازه، دلیل + متن، عکسِ اختیاری، روشِ رساندن).
 *   acct.js ─── «سفارش‌های من» با `features.returnRequests`: درخواست‌های همین حساب با وضعیت.
 * پرچمِ خاموش ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/returns.<زبان>.json`)، نه هیچ گره‌ای.
 *
 * ⛔ هر متنِ مشتری و دکان فقط با textContent (`K.el`) — هرگز HTML.
 * ⛔ فقط با نشستِ حساب (سرور هم ۴۰۱ می‌دهد)؛ بی نشست ⇒ پیام و پیوندِ «ورود به حساب».
 * ⛔ عکس از همان راهِ امنِ گفت‌وگو (`store/chat-photo.js` ِ دیربار: JPEG ِ تازه از بوم، بی EXIF، زیرِ سقفِ سرور)؛
 *    سرور نوع و اندازه را از خودِ بایت دوباره می‌سنجد. مهلت، اقلام و «می‌شود/نمی‌شود» همیشه با سرور است.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var LOC = K.lang === 'en' ? 'en-GB' : 'fa-AF';
  var REASONS = [{ code: 'damaged', label: 'rr.r.damaged' }, { code: 'wrong_item', label: 'rr.r.wrong_item' },
    { code: 'expired', label: 'rr.r.expired' }, { code: 'not_as_described', label: 'rr.r.not_as_described' },
    { code: 'changed_mind', label: 'rr.r.changed_mind' }, { code: 'other', label: 'rr.r.other' }];
  var METHODS = { drop_off: { label: 'rr.m.drop_off', after: 'rr.a.drop_off' }, courier: { label: 'rr.m.courier', after: 'rr.a.courier' } };
  var STATUS = { pending: { kind: 'info', label: 'rr.s.pending' }, accepted: { kind: 'ok', label: 'rr.s.accepted' },
    completed: { kind: 'ok', label: 'rr.s.completed' }, rejected: { kind: 'warn', label: 'rr.s.rejected' } };
  var WHY = { late: { label: 'rr.why.late' }, open: { label: 'rr.why.open' }, max: { label: 'rr.why.max' }, none_left: { label: 'rr.why.none_left' } };

  function reasonText(code) {
    for (var i = 0; i < REASONS.length; i++) if (REASONS[i].code === code) return t(REASONS[i].label);
    return t('rr.r.other');
  }
  function day(iso) {
    try { return new Date(iso + 'T12:00:00Z').toLocaleDateString(LOC, { dateStyle: 'medium', timeZone: 'UTC' }); } catch (e) { return iso; }
  }
  function newKey() {
    var a = new Uint8Array(12);
    (window.crypto || window.msCrypto).getRandomValues(a);
    return 'rr-' + Array.prototype.map.call(a, function (b) { return ('0' + b.toString(16)).slice(-2); }).join('');
  }
  /** نوشتهٔ کادرِ اندازه (رقمِ فارسی/عربی، ٫) ⇒ هزارم؛ نادرست ⇒ ۰ */
  function milli(text) {
    var s = String(text || '').trim().replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[٫,]/g, '.');
    var n = Number(s);
    return /^\d+(\.\d+)?$/.test(s) && n > 0 ? Math.round(n * 1000) : 0;
  }
  /** فایلِ برگزیده ⇒ base64 ِ JPEG ِ کوچک‌شده (همان `StoreChatPhoto.prepare` ِ گفت‌وگو) زیرِ سقفِ سرور */
  function shrink(file, maxBytes) {
    return K.js('/store/chat-photo.js').then(function () {
      return window.StoreChatPhoto.prepare(file, { imageMax: maxBytes });
    }).then(function (p) { return window.StoreChatPhoto.dataUrl(p.main); }).then(function (u) { return u.slice(u.indexOf(',') + 1); });
  }
  function pill(st) {
    var s = STATUS[st] || STATUS.pending;
    return el('span', { class: 'pill ' + s.kind, text: t(s.label) });
  }

  /** `ctx` از order.js یا acct.js: `api(path, opts)` */
  window.StoreReturns = function (ctx) {
    var api = ctx.api;
    var dict = K.part('returns', $('o-rr'));
    var cur = { token: '', key: newKey() };

    function box() {
      var b = $('o-rr');
      if (!b) {
        b = el('section', { class: 'card card-lg stack', attrs: { id: 'o-rr', 'aria-labelledby': 'rr-h' }, hidden: true });
        var at = $('o-rate');
        at.parentNode.insertBefore(b, at.nextSibling);
      }
      return b;
    }

    /** یک درخواست: وضعیت، زمان، شمارِ قلم و عکس، و گامِ بعد یا دلیلِ دکان */
    function row(r, extra) {
      var sub = K.when(r.createdAt) + t('sep') + t('rr.n', { n: K.fa(r.items !== undefined ? r.items : r.lines.length, 0) }) +
        (r.photos ? t('sep') + t('rr.ph', { n: K.fa(r.photos, 0) }) : '');
      var li = el('li', { class: 'stack' }, el('span', { class: 'spread' }, el('b', { text: extra || reasonText(r.reason) }), pill(r.status)),
        el('small', { class: 'muted', text: sub }));
      if (r.status === 'accepted' && METHODS[r.method]) li.appendChild(el('small', { text: t(METHODS[r.method].after) }));
      if (r.decisionNote) li.appendChild(el('small', { text: t('rr.note', { r: r.decisionNote }) }));
      return li;
    }

    function form(v, refresh) {
      var f = el('form', { class: 'stack', attrs: { id: 'rr-form', novalidate: '' } });
      var picks = [];
      var items = el('fieldset', { class: 'plain-set stack' }, el('legend', { class: 'label-lg', text: t('rr.items') }));
      v.lines.forEach(function (l) {
        if (l.leftMilli <= 0) return;
        var name = el('span', { class: 'grow' }, el('b', { text: l.name }), ' ', el('small', { class: 'muted', text: t('rr.of', { n: K.qty(l.leftMilli) + (l.unit ? ' ' + l.unit : '') }) }));
        if (l.blocked) { items.appendChild(el('div', { class: 'spread' }, name, el('span', { class: 'pill off', text: t('rr.blocked') }))); return; }
        var chk = el('input', { attrs: { type: 'checkbox', 'data-line': String(l.lineNo) } });
        var qty = el('input', { class: 'input', attrs: { type: 'text', inputmode: 'decimal', dir: 'ltr', size: '6', value: K.qty(l.leftMilli), 'aria-label': t('rr.qty', { name: l.name }) } });
        picks.push({ line: l, chk: chk, qty: qty });
        items.appendChild(el('div', { class: 'spread' }, el('label', { class: 'check grow' }, chk, name), qty));
      });
      var reason = el('select', { attrs: { id: 'rr-reason' } });
      REASONS.forEach(function (x) { if (v.reasons.indexOf(x.code) >= 0) reason.appendChild(el('option', { attrs: { value: x.code }, text: t(x.label) })); });
      var text = el('textarea', { attrs: { id: 'rr-text', maxlength: String((v.limits && v.limits.textMax) || 1000) } });
      var photos = el('input', { attrs: { id: 'rr-photos', type: 'file', accept: 'image/*', multiple: '' } });
      var seg = el('div', { class: 'seg', attrs: { role: 'radiogroup', 'aria-label': t('rr.method') } });
      v.methods.forEach(function (m, i) {
        if (!METHODS[m]) return;
        var r = el('input', { attrs: { type: 'radio', name: 'rr-method', value: m } });
        if (i === 0) r.checked = true;
        seg.appendChild(el('label', {}, r, el('span', { text: t(METHODS[m].label) })));
      });
      var msg = el('div', { class: 'note', attrs: { id: 'rr-msg', role: 'status' }, hidden: true });
      var send = el('button', { class: 'btn btn-accent', attrs: { type: 'submit', id: 'rr-send' }, text: t('rr.send') });
      f.append(items,
        el('label', { class: 'field' }, el('span', { text: t('rr.reason') }), reason),
        el('label', { class: 'field' }, el('span', { text: t('rr.text') }), text),
        el('label', { class: 'field' }, el('span', { text: t('rr.photos', { n: K.fa((v.limits && v.limits.photos) || 3, 0) }) }), photos),
        el('fieldset', { class: 'plain-set stack' }, el('legend', { class: 'label-lg', text: t('rr.method') }), seg),
        el('p', { class: 'muted small', text: t('rr.refund') }), send, msg);
      f.addEventListener('submit', function (ev) {
        ev.preventDefault();
        var lines = [];
        var bad = false;
        picks.forEach(function (p) {
          if (!p.chk.checked) return;
          var q = milli(p.qty.value);
          p.qty.setAttribute('aria-invalid', String(!q || q > p.line.leftMilli));
          if (!q || q > p.line.leftMilli) bad = true; else lines.push({ lineNo: p.line.lineNo, qtyMilli: q });
        });
        if (bad || !lines.length) { K.note(msg, 'err', t('rr.pick')); return; }
        var m = f.querySelector('input[name="rr-method"]:checked');
        var files = Array.prototype.slice.call(photos.files || [], 0, (v.limits && v.limits.photos) || 3);
        send.disabled = true;
        K.note(msg, 'info', t('rr.sending'));
        var path = '/orders/' + encodeURIComponent(cur.token) + '/return-requests';
        api(path, { method: 'POST', body: { lines: lines, reason: reason.value, text: text.value.trim(), method: m ? m.value : 'drop_off', requestKey: cur.key } })
          .then(function (r) {
            var id = r.request.id;
            //  عکس‌ها یکی‌یکی؛ شکستِ یکی درخواست را پس نمی‌گیرد
            return files.reduce(function (p, file) {
              return p.then(function () { return shrink(file, (v.limits && v.limits.photoBytes) || 600 * 1024); })
                .then(function (b64) { return api(path + '/' + encodeURIComponent(id) + '/photos', { method: 'POST', body: { imageBase64: b64 } }); })
                .catch(function () { /* عکسِ نرسیده؛ درخواست سرِ جایش است */ });
            }, Promise.resolve());
          })
          .then(function () { cur.key = newKey(); K.toast(t('rr.sent')); refresh(); })
          .catch(function (e) { send.disabled = false; K.note(msg, 'err', e.message); });
      });
      return f;
    }

    /** صفحهٔ پیگیری: `o.returnRequest` ِ همان پاسخ؛ `refresh()` پیگیری را از نو می‌خواند */
    function order(o, token, refresh) {
      return dict.then(function (ok) {
        var b = box();
        var v = o.returnRequest;
        if (!ok || !v) { b.hidden = true; return; }
        if (cur.token !== token) { cur.token = token; cur.key = newKey(); }
        K.clear(b);
        b.appendChild(el('h2', { attrs: { id: 'rr-h' }, text: t('rr.title') }));
        if (v.requests.length) {
          var ul = el('ul', { class: 'sum-lines', attrs: { id: 'rr-list' } });
          v.requests.forEach(function (r) { ul.appendChild(row(r)); });
          b.appendChild(ul);
        }
        if (v.canRequest && v.lastDay) b.appendChild(el('p', { class: 'muted small', text: t('rr.deadline', { d: day(v.lastDay) }) }));
        if (!v.canRequest) {
          var w = WHY[v.why];
          if (w) b.appendChild(el('p', { class: 'muted', attrs: { id: 'rr-why' }, text: t(w.label, { n: K.fa(v.days, 0) }) }));
        } else if (!K.sess()) {
          b.append(el('p', { class: 'muted', attrs: { id: 'rr-why' }, text: t('rr.signin') }),
            el('a', { class: 'btn', attrs: { href: '#/orders', id: 'rr-signin' }, text: t('rr.signin.go') }));
        } else {
          var start = el('button', { class: 'btn', attrs: { type: 'button', id: 'rr-start' }, text: t('rr.open') });
          start.addEventListener('click', function () { start.replaceWith(form(v, refresh)); });
          b.appendChild(start);
        }
        b.hidden = false;
      });
    }

    /** «سفارش‌های من»: درخواست‌های همین حساب، هر کدام با پیوند به صفحهٔ همان سفارش */
    function mine(into) {
      return Promise.all([dict, api('/account/return-requests')]).then(function (r) {
        var list = r[1].requests || [];
        var old = $('rr-mine');
        if (old) old.remove();
        if (!r[0] || !list.length) return;
        var ul = el('ul', { class: 'sum-lines' });
        list.forEach(function (x) {
          var li = row(x, t('od.no', { n: K.fa(x.orderNo, 0) }));
          if (x.token) li.appendChild(el('a', { attrs: { href: '#/o/' + encodeURIComponent(x.token) }, text: reasonText(x.reason) }));
          ul.appendChild(li);
        });
        into.appendChild(el('section', { class: 'stack', attrs: { id: 'rr-mine' } }, el('h3', { text: t('rr.mine') }), ul));
      }).catch(function () { /* بی فهرست؛ حساب همان */ });
    }

    return { order: order, mine: mine };
  };
})();
