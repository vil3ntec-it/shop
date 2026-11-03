/*
 * ویترین — گفت‌وگوی مشتری با دکان (v3 §۳۳؛ یک رشته برای هر سفارش، `lib/shop-chat-life.js`).
 * دیربار: فقط با دکمهٔ «پرسش از دکان» ِ سرِ ویترین (`features.chat`) یا کلیدِ `chat` ِ پیگیریِ سفارش (`order.js`)
 * بار می‌شود؛ پرچمِ خاموشِ `shop.chat` ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/chat.<زبان>.json`).
 *
 *   رشته ── هر سفارش رشتهٔ خودش؛ پرسشِ پیش از سفارش «گفت‌وگوی عمومی». رشته‌های پیشینِ همین شماره بالای رشتهٔ
 *           کنونی جمع‌شده می‌مانند (`<details>`)؛ پیام‌های دورِ پیشینِ گفت‌وگوی عمومی هم.
 *   بسته ── سفارشِ تمام‌شده ⇒ فقط خواندن؛ تا مهلتِ سرور «مشکل دارم» همان رشته را باز می‌کند. عمومیِ بی‌کار ⇒
 *           «گفت‌وگوی تازه». تصمیم فقط با سرور (`life`)؛ ۴۰۹ ِ سرور ⇒ خواندنِ دوباره، نه حدس.
 * ⛔ هر متن فقط با textContent (`K.el`)؛ عکس با بلیت خوانده و data: می‌شود (CSP: img-src 'self' data:).
 * ⛔ شماره فقط با کدِ یک‌بارمصرف (`/verify`)؛ بلیتِ منقضی ⇒ همان فرمِ تأیید.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;
  var WAIT = 20000;
  var inst = null;

  window.StoreChat = function (ctx) {
    if (inst) return inst;
    var api = ctx.api;
    var base = '/api/store/' + encodeURIComponent(ctx.slug);
    var conv = null; var cursor = 0; var msgs = {}; var loop = 0; var want = null; var sheet = null;
    //  واژه‌نامه پیش از ساختنِ برگه (برگه هنوز نیست ⇒ ترجمهٔ دوباره لازم نیست؛ متن‌ها با t() از همین واژه‌نامه)
    var ready = K.part('chat', $('chat-sheet'));

    function build() {
      if (sheet) return;
      sheet = el('div', { class: 'sheet modal chat', attrs: { id: 'chat-sheet', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': 'ct-title' }, hidden: true },
        el('div', { class: 'sheet-head' },
          el('h2', { attrs: { id: 'ct-title' }, text: t('ct.title') }),
          el('button', { class: 'btn btn-icon btn-ghost', attrs: { id: 'ct-close', type: 'button', 'aria-label': t('close') }, on: { click: function () { K.closeSheet(); } } }, K.icon('close'))),
        el('form', { class: 'stack', attrs: { id: 'ct-verify', novalidate: '' }, hidden: true },
          el('p', { class: 'muted', text: t('ct.v.title') }),
          el('label', { class: 'field' }, el('span', { text: t('ct.v.phone') }), el('input', { attrs: { id: 'ct-phone', type: 'tel', inputmode: 'tel', autocomplete: 'tel', dir: 'ltr' } })),
          el('button', { class: 'btn btn-block', attrs: { id: 'ct-vsend', type: 'button' }, text: t('ct.v.send'), on: { click: sendCode } }),
          el('label', { class: 'field', attrs: { id: 'ct-coderow' }, hidden: true }, el('span', { text: t('ct.v.code') }), el('input', { attrs: { id: 'ct-code', inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr' } })),
          el('button', { class: 'btn btn-accent btn-block', attrs: { id: 'ct-vcheck', type: 'submit' }, text: t('ct.v.check'), hidden: true }),
          el('div', { class: 'note', attrs: { id: 'ct-vmsg', role: 'status' }, hidden: true })),
        el('div', { class: 'ct-body', attrs: { id: 'ct-body', 'aria-live': 'polite' } },
          el('details', { class: 'ct-prev', attrs: { id: 'ct-prev' }, hidden: true }, el('summary', { attrs: { id: 'ct-prev-s' } }), el('div', { attrs: { id: 'ct-prev-list' } })),
          el('details', { class: 'ct-prev', attrs: { id: 'ct-old' }, hidden: true }, el('summary', { attrs: { id: 'ct-old-s' } }), el('ol', { class: 'ct-list', attrs: { id: 'ct-old-list' } })),
          el('ol', { class: 'ct-list', attrs: { id: 'ct-list' } }),
          el('p', { class: 'muted small', attrs: { id: 'ct-empty' }, text: t('ct.empty'), hidden: true })),
        el('div', { class: 'note', attrs: { id: 'ct-state', role: 'status' }, hidden: true }),
        el('button', { class: 'btn btn-block', attrs: { id: 'ct-issue', type: 'button' }, text: t('ct.issue'), hidden: true, on: { click: issue } }),
        el('button', { class: 'btn btn-block', attrs: { id: 'ct-new', type: 'button' }, text: t('ct.new'), hidden: true, on: { click: function () { start({}); } } }),
        el('form', { class: 'ct-form', attrs: { id: 'ct-form', novalidate: '' }, hidden: true },
          el('textarea', { class: 'input', attrs: { id: 'ct-text', rows: '2', maxlength: '4000', 'aria-label': t('ct.text'), placeholder: t('ct.text') } }),
          el('button', { class: 'btn btn-accent', attrs: { id: 'ct-send', type: 'submit' }, text: t('ct.send') })),
        el('div', { class: 'note', attrs: { id: 'ct-msg', role: 'alert' }, hidden: true }));
      document.body.appendChild(sheet);
      $('ct-verify').onsubmit = function (e) { e.preventDefault(); checkCode(); };
      $('ct-form').onsubmit = function (e) { e.preventDefault(); send(); };
    }

    function path(id, rest) { return '/chat/conversations/' + encodeURIComponent(id) + (rest || ''); }
    function fail(e) {
      if (e && e.status === 401) { K.setTicket(null); verify(); return; }
      K.note($('ct-msg'), 'err', (e && e.message) || t('ct.err'));
    }

    // ── تأییدِ شماره (بلیتِ ویترین) ──
    function verify() {
      loop++;
      $('ct-verify').hidden = false; $('ct-body').hidden = true; $('ct-form').hidden = true;
      $('ct-issue').hidden = true; $('ct-new').hidden = true; $('ct-state').hidden = true;
      setTimeout(function () { $('ct-phone').focus(); }, 80);
    }
    function sendCode() {
      var b = $('ct-vsend'); b.disabled = true;
      api('/verify/start', { method: 'POST', body: { phone: $('ct-phone').value } }).then(function (r) {
        $('ct-coderow').hidden = false; $('ct-vcheck').hidden = false;
        K.note($('ct-vmsg'), 'info', t('v.sent') + (r.devCode ? ' ' + r.devCode : ''));
        $('ct-code').focus();
      }).catch(function (e) { K.note($('ct-vmsg'), 'err', e.code === 'bad_request' ? t('err.bad_phone') : e.message); })
        .then(function () { b.disabled = false; });
    }
    function checkCode() {
      var b = $('ct-vcheck'); b.disabled = true;
      api('/verify/check', { method: 'POST', body: { phone: $('ct-phone').value, code: $('ct-code').value } }).then(function (r) {
        K.setTicket(r);
        $('ct-code').value = ''; $('ct-verify').hidden = true; $('ct-vmsg').hidden = true;
        start(want || {});
      }).catch(function (e) { K.note($('ct-vmsg'), 'err', e.message); }).then(function () { b.disabled = false; });
    }

    // ── باز کردنِ رشته ──
    function start(body) {
      want = body;
      $('ct-msg').hidden = true;
      if (!K.ticket()) { verify(); return Promise.resolve(); }
      $('ct-verify').hidden = true; $('ct-body').hidden = false;
      return api('/chat/conversations', { method: 'POST', body: body }).then(function (r) { show(r.conversation, true); }).catch(fail);
    }
    function title(c) { return c.orderNo ? t('ct.order', { n: K.fa(c.orderNo, 0) }) : t('ct.general'); }
    function show(c, fresh) {
      if (fresh || !conv || conv.id !== c.id) { conv = c; cursor = 0; msgs = {}; K.clear($('ct-list')); K.clear($('ct-old-list')); prev(); }
      conv = c;
      $('ct-title').textContent = title(c);
      state();
      if (fresh) poll(++loop);
    }
    function state() {
      var l = conv.life || { state: 'open' };
      var box = $('ct-state');
      var closed = l.state === 'closed';
      $('ct-issue').hidden = !(closed && l.canIssue);
      $('ct-new').hidden = !(closed && l.canRestart);
      $('ct-form').hidden = !conv.canSend;
      if (closed && l.kind === 'general') K.note(box, 'warn', t('ct.st.idle'));
      else if (closed) K.note(box, 'warn', t('ct.st.order') + ' ' + (l.canIssue ? t('ct.st.issue', { d: K.when(l.issueUntil) }) : t('ct.st.late')));
      else if (l.state === 'reopened') K.note(box, 'info', t('ct.st.reopened'));
      else if (!conv.canSend) K.note(box, 'warn', t('ct.st.blocked'));
      else box.hidden = true;
    }

    // ── رشته‌های پیشینِ همین شماره (جمع‌شده) ──
    function prev() {
      var me = conv.id;
      api('/chat/conversations?limit=50').then(function (r) {
        var others = (r.conversations || []).filter(function (c) { return c.id !== me && (c.seq > 0 || c.orderNo); });
        var d = $('ct-prev'); d.hidden = !others.length; d.open = false;
        $('ct-prev-s').textContent = t('ct.prev', { n: K.fa(others.length, 0) });
        var list = K.clear($('ct-prev-list'));
        others.forEach(function (c) {
          var ol = el('ol', { class: 'ct-list' });
          var det = el('details', { class: 'ct-prev' }, el('summary', { text: title(c) + t('sep') + badge(c) }), ol);
          det.addEventListener('toggle', function () {
            if (!det.open || ol.childNodes.length) return;
            api(path(c.id, '/messages?after=0&limit=200')).then(function (x) { x.messages.forEach(function (m) { put(ol, m, c.id); }); }).catch(fail);
          });
          list.appendChild(det);
        });
      }).catch(function () { $('ct-prev').hidden = true; });
    }
    function badge(c) {
      var s = c.life ? c.life.state : 'open';
      return t(s === 'closed' ? 'ct.closed' : s === 'reopened' ? 'ct.reopened' : 'ct.live');
    }

    // ── پیام‌ها ──
    function put(list, m, cid) {
      if (m.hidden) { var gone = msgs[m.id]; if (gone && gone.parentNode) gone.parentNode.removeChild(gone); return; }
      var li = el('li', { class: 'ct-msg ' + (m.sender === 'customer' ? 'mine' : 'shop') });
      if (m.deleted) li.appendChild(el('p', { class: 'muted', text: t('ct.deleted') }));
      else {
        if (m.kind === 'image' && m.media) li.appendChild(photo(cid, m.media));
        if (m.body) li.appendChild(el('p', { text: m.body }));
      }
      li.appendChild(el('small', { class: 'muted num', text: K.clock(m.createdAt) }));
      var old = msgs[m.id];
      if (old && old.parentNode) old.parentNode.replaceChild(li, old); else list.appendChild(li);
      msgs[m.id] = li;
    }
    function photo(cid, media) {
      var img = el('img', { class: 'ct-img', attrs: { alt: t('ct.photo'), width: '200', height: '150' } });
      var btn = el('button', { class: 'ct-imgbtn', attrs: { type: 'button', 'aria-label': t('ct.photo.big') } }, img);
      var load = function (id) {
        var tk = K.ticket();
        return fetch(base + path(cid, '/media/' + encodeURIComponent(id)), { headers: tk ? { 'X-Store-Ticket': tk.ticket } : {}, credentials: 'omit', cache: 'no-store' })
          .then(function (res) { if (!res.ok) throw new Error(res.status); return res.blob(); })
          .then(function (b) { return new Promise(function (ok) { var fr = new FileReader(); fr.onload = function () { ok(fr.result); }; fr.readAsDataURL(b); }); })
          .then(function (src) { if (/^data:image\/(jpeg|png|webp);/.test(src)) img.src = src; });
      };
      load(media.thumbId || media.id).catch(function () { img.alt = t('ct.err'); });
      btn.onclick = function () { btn.classList.add('big'); if (media.thumbId) load(media.id).catch(function () { /* همان پیش‌نمایش */ }); };
      return btn;
    }
    function render(list) {
      var l = conv.life || {};
      var cut = l.kind === 'general' && l.sessionAt ? l.sessionAt : 0;
      list.forEach(function (m) { put(cut && m.createdAt < cut ? $('ct-old-list') : $('ct-list'), m, conv.id); });
      var old = $('ct-old-list').childNodes.length;
      $('ct-old').hidden = !old;
      $('ct-old-s').textContent = t('ct.old', { n: K.fa(old, 0) });
      $('ct-empty').hidden = $('ct-list').childNodes.length > 0;
      var last = $('ct-list').lastChild;
      if (last && last.scrollIntoView) last.scrollIntoView({ block: 'end' });
    }
    function poll(my) {
      if (my !== loop || K.sheetOpen() !== sheet || !conv) return;
      var id = conv.id;
      api(path(id, '/messages?after=' + cursor + '&wait=' + (cursor ? WAIT : 0))).then(function (r) {
        if (my !== loop || !conv || conv.id !== id) return;
        if (r.conversation) { conv = r.conversation; state(); }
        cursor = r.cursor;
        render(r.messages || []);
        var seq = conv.seq || 0;
        if (r.messages && r.messages.length) api(path(id, '/receipts'), { method: 'POST', body: { deliveredSeq: seq, readSeq: seq } }).catch(function () { /* بعدی */ });
        poll(my);
      }).catch(function (e) {
        if (my !== loop) return;
        if (e.status === 401) { fail(e); return; }
        setTimeout(function () { poll(my); }, 5000);
      });
    }

    function send() {
      var box = $('ct-text'); var body = box.value.trim();
      if (!body || !conv) return;
      var b = $('ct-send'); b.disabled = true;
      var key = 'ct-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10);
      api(path(conv.id, '/messages'), { method: 'POST', body: { clientId: key, body: body } }).then(function (r) {
        box.value = ''; $('ct-msg').hidden = true;
        put($('ct-list'), r.message, conv.id); $('ct-empty').hidden = true;
      }).catch(function (e) {
        if (e.code === 'chat_closed' || e.code === 'chat_blocked') reload();
        fail(e);
      }).then(function () { b.disabled = false; });
    }
    function issue() {
      var b = $('ct-issue'); b.disabled = true;
      api(path(conv.id, '/issue'), { method: 'POST', body: {} }).then(function (r) {
        conv = r.conversation; state(); $('ct-msg').hidden = true; $('ct-text').focus();
      }).catch(function (e) { reload(); fail(e); }).then(function () { b.disabled = false; });
    }
    function reload() { if (conv) api(path(conv.id)).then(function (r) { conv = r.conversation; state(); }).catch(function () { /* همان */ }); }

    function openWith(body) {
      return ready.then(function () {
        build();
        K.openSheet(sheet, function () { loop++; });
        return start(body);
      });
    }

    inst = {
      general: function () { return openWith({}); },
      order: function (orderNo) { return openWith({ orderNo: orderNo }); },
      /** کارتِ «گفت‌وگو با دکان» در صفحهٔ پیگیری — فقط با کلیدِ `chat` ِ پیگیری */
      entry: function (o) { ready.then(function () { card(o); }); },
    };
    function card(o) {
      var box = $('o-chat');
      if (!box) {
        box = el('div', { class: 'card card-lg stack', attrs: { id: 'o-chat' } },
          el('h2', { text: t('ct.title') }), el('p', { class: 'muted', text: t('ct.entry') }),
          el('button', { class: 'btn btn-block', attrs: { id: 'o-chat-open', type: 'button' }, text: t('ct.open') }));
        var at = $('o-subs');
        at.parentNode.insertBefore(box, at);
      }
      box.hidden = false;
      $('o-chat-open').onclick = function () { inst.order(o.orderNo); };
    }
    return inst;
  };
})();
