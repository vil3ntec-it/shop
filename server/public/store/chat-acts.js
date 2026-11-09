/*
 * ویترین — پاسخ، ویرایش، سنجاق و جست‌وجوی مشتری در گفت‌وگو با دکان (CHAT-14 در وب). دیربار: `store/chat.js` آن را با
 * گشودنِ گفت‌وگو بار می‌کند (نه بارِ نخست)؛ واژه‌ها در `kit/i18n/chatacts.<زبان>.json`. نیامد ⇒ گفت‌وگو همان است و فقط
 * این چهار کار نیست. درها همان `/api/store/:slug/chat/…` (سرور همه را دوباره می‌سنجد؛ `shop-chat-web-acts.test.js`).
 *   پاسخ ── «پاسخ» ⇒ برچسبِ «پاسخ به …» بالای کادر؛ پیام با `replyTo` از همان صفِ CHAT-02 (`chat-out.js`) می‌رود؛ حباب
 *           نقلِ کوتاهِ پیامِ اصل را نشان می‌دهد و زدنش به همان پیام می‌رود.
 *   ویرایش ── فقط پیامِ متنیِ زندهٔ خودم در رشتهٔ باز و تا `editUntil` ِ سرور (ساعتِ سرور، نه ساعتِ گوشی)؛ کادر همان
 *           متن را می‌گیرد و «ذخیره» ⇒ PATCH. ویرایش در صف نمی‌رود: بی‌اینترنت ⇒ پیامِ روشن و متن در کادر می‌ماند.
 *   سنجاق ── فقط برای خودم (دکان نمی‌بیند)، حداکثر ۵؛ نوارِ «سنجاق‌شده» فقط از `conversation.pinned` ِ سرور (بی متن؛
 *           متن از حبابِ همین صفحه).
 *   جست‌وجو ── `GET …/chat/search` ِ همین دکان و همین شماره؛ نتیجهٔ همین رشته ⇒ رفتن به همان حباب.
 * ⛔ هر متن فقط با textContent (`K.el`)؛ متنِ پیام هیچ‌جا لاگ نمی‌شود.
 */
(function () {
  'use strict';
  var $ = K.$; var el = K.el; var t = K.t;

  /** H = کمک‌های `chat.js`: api، path، conv()، info()، put، list()، adopt(conv)، bar، shut، home، done، note، fail */
  window.StoreChatActs = function (H) {
    var ready = K.part('chatacts', $('chat-sheet'));
    var reply = null; var editing = null; var label = '';

    function snip(m) {
      if (!m || m.deleted) return t('ca.gone');
      var s = String(m.body || '');
      if (!s) return t(m.kind === 'image' ? 'ca.image' : m.kind === 'audio' ? 'ca.audio' : 'ca.gone');
      return s.length > 80 ? s.slice(0, 80) + '…' : s;
    }
    function open() { var c = H.conv(); var l = (c && c.life) || {}; return Boolean(c) && c.canSend && l.state !== 'closed'; }
    function pinned(id) { return ((H.conv() || {}).pinned || []).some(function (p) { return p.messageId === id; }); }

    // ── سازه: دکمهٔ جست‌وجو در سرِ برگه، نوارِ سنجاق بالای پیام‌ها، برچسبِ پاسخ/ویرایش بالای کادر ──
    function mount() {
      if ($('ct-find-btn')) return;
      var head = $('ct-close').parentNode;
      head.insertBefore(el('button', { class: 'btn btn-icon btn-ghost', attrs: { id: 'ct-find-btn', type: 'button', 'aria-label': t('ca.find'),
        'aria-expanded': 'false', 'aria-controls': 'ct-find' }, on: { click: function () { toggle(); } } }, K.icon('search')), $('ct-close'));
      head.parentNode.insertBefore(el('form', { class: 'ct-find stack', attrs: { id: 'ct-find', role: 'search', novalidate: '' }, hidden: true,
        on: { submit: function (e) { e.preventDefault(); find(); } } },
        el('div', { class: 'row' },
          el('input', { class: 'input', attrs: { id: 'ct-find-q', type: 'search', maxlength: '100', 'aria-label': t('ca.find'), placeholder: t('ca.find') } }),
          el('button', { class: 'btn', attrs: { id: 'ct-find-go', type: 'submit' }, text: t('ca.find.go') })),
        el('p', { class: 'muted small', attrs: { id: 'ct-find-st', role: 'status' } }),
        el('ul', { class: 'ct-hits', attrs: { id: 'ct-find-list' } })), head.nextSibling);
      var body = $('ct-body');
      body.insertBefore(el('div', { class: 'ct-pins', attrs: { id: 'ct-pins', role: 'group', 'aria-label': t('ca.pins') }, hidden: true }), body.firstChild);
      var form = $('ct-form');
      form.parentNode.insertBefore(el('div', { class: 'ct-chip', attrs: { id: 'ct-chip' }, hidden: true },
        el('span', { attrs: { id: 'ct-chip-t', 'aria-live': 'polite' } }),
        el('button', { class: 'btn btn-ghost btn-sm', attrs: { id: 'ct-chip-x', type: 'button' }, text: t('ca.stop'), on: { click: function () { stop(true); $('ct-text').focus(); } } })), form);
      label = $('ct-send').textContent;
    }

    // ── پاسخ و ویرایش ──
    function chip(text) { $('ct-chip-t').textContent = text; $('ct-chip').hidden = false; }
    function stop(clear) {
      if (editing && clear) $('ct-text').value = '';
      reply = null; editing = null;
      if ($('ct-chip')) $('ct-chip').hidden = true;
      if (label) $('ct-send').textContent = label;
    }
    function startReply(x) {
      stop(true); H.shut(false);
      reply = { id: x.m.id, cid: x.cid };
      chip(t('ca.to') + ' ' + snip(x.m));
      $('ct-text').focus();
    }
    function startEdit(x) {
      stop(true); H.shut(false);
      editing = { id: x.m.id, cid: x.cid };
      chip(t('ca.editing'));
      $('ct-send').textContent = t('ca.save');
      $('ct-text').value = x.m.body || '';
      $('ct-text').focus();
    }
    function canEdit(x) {
      return open() && x.m.sender === 'customer' && x.m.kind === 'text' && !x.m.deleted && (Number(x.m.editUntil) || 0) > Date.now();
    }
    function save() {
      var e = editing; var text = $('ct-text').value.trim(); var b = $('ct-send');
      if (!e || !text || b.disabled) return;
      b.disabled = true;
      H.api(H.path(e.cid, '/messages/' + encodeURIComponent(e.id)), { method: 'PATCH', body: { body: text } }).then(function (r) {
        if (editing !== e) return;
        stop(true);
        var x = H.info()[e.id];
        if (r.message && x) H.put(x.li.parentNode || H.list(), r.message, e.cid);
        H.done(t('ca.edit.ok'));
        $('ct-text').focus();
      }).catch(function (er) {
        if (er.status === 401) { H.fail(er); return; }
        var x = H.info()[e.id];
        if (er.code === 'edit_window_passed' || er.code === 'not_author' || er.code === 'message_not_found') {
          if (x && er.code === 'edit_window_passed') x.m.editUntil = 0;
          stop(false);   //  متن در کادر می‌ماند تا اگر خواست پیامِ تازه بفرستد
        }
        if (er.code === 'chat_closed') H.reload();
        H.note(er.status === 0 ? t('ca.edit.off') : er.message);
      }).then(function () { b.disabled = false; });
    }

    // ── سنجاق ──
    function pin(x, on) {
      var a = x.li.querySelector('.ct-acts');
      if (a) [].forEach.call(a.querySelectorAll('button'), function (b) { b.disabled = true; });
      H.api(H.path(x.cid, '/pins/' + encodeURIComponent(x.m.id)), { method: on ? 'PUT' : 'DELETE' }).then(function (r) {
        H.adopt(r.conversation);
        H.shut(true);
        H.done(t(on ? 'ca.pin.ok' : 'ca.unpin.ok'));
      }).catch(function (e) {
        if (e.status === 401) { H.fail(e); return; }
        H.bar(x.m.id, null, e.status === 0 ? t('ca.pin.off') : e.message, true);
      });
    }
    function sync() {
      var box = $('ct-pins');
      if (!box) return;
      var list = (H.conv() || {}).pinned || [];
      K.clear(box);
      box.hidden = !list.length;
      if (!list.length) return;
      box.appendChild(el('small', { class: 'muted', text: t('ca.pins') }));
      list.forEach(function (p) {
        var x = H.info()[p.messageId];
        box.appendChild(el('button', { class: 'ct-pin', attrs: { type: 'button', 'data-pin': p.messageId }, text: snip(x && x.m),
          on: { click: function () { jump(p.messageId); } } }));
      });
    }
    /** رفتن به حبابِ یک پیام (اگر در صفحه هست): «پیام‌های پیشین» ِ جمع‌شده باز، پیمایش و تمرکز */
    function jump(id) {
      var x = H.info()[id];
      if (!x || !x.li.parentNode) { H.note(t('ca.far')); return false; }
      var d = x.li.closest('details');
      if (d) d.open = true;
      x.li.setAttribute('tabindex', '-1');
      x.li.classList.add('ct-hl');
      setTimeout(function () { x.li.classList.remove('ct-hl'); }, 2000);
      if (x.li.scrollIntoView) x.li.scrollIntoView({ block: 'center' });
      x.li.focus();
      return true;
    }

    // ── جست‌وجو ──
    /** stay: بستن بی بردنِ تمرکز به دکمه (تمرکز همان‌جا که `jump` گذاشت) */
    function toggle(stay) {
      var f = $('ct-find'); var b = $('ct-find-btn');
      f.hidden = !f.hidden;
      b.setAttribute('aria-expanded', String(!f.hidden));
      if (!f.hidden) $('ct-find-q').focus(); else if (stay !== true) b.focus();
    }
    function find() {
      var q = $('ct-find-q').value.trim(); var st = $('ct-find-st'); var ul = K.clear($('ct-find-list')); var b = $('ct-find-go');
      if (q.length < 2) { st.textContent = t('err.query_too_short'); return; }
      b.disabled = true; st.textContent = t('ca.finding');
      H.api('/chat/search?q=' + encodeURIComponent(q) + '&limit=30').then(function (r) {
        var c = H.conv(); var hits = r.results || [];
        st.textContent = hits.length ? t('ca.hits', { n: K.fa(hits.length, 0) }) : t('ca.none');
        hits.forEach(function (h) {
          var here = c && h.conversationId === c.id;
          var text = (h.sender === 'customer' ? t('ca.me') : t('ca.shop')) + t('sep') + snip(h);
          ul.appendChild(el('li', null, here
            ? el('button', { class: 'ct-hit', attrs: { type: 'button', 'data-hit': h.id }, text: text, on: { click: function () { if (jump(h.id)) toggle(true); } } })
            : el('span', { class: 'muted', text: text + t('sep') + t('ca.other') })));
        });
      }).catch(function (e) {
        if (e.status === 401) { H.fail(e); return; }
        st.textContent = e.message;
      }).then(function () { b.disabled = false; });
    }

    return {
      ready: ready,
      mount: mount,
      sync: sync,
      /** رشتهٔ دیگری باز شد: پاسخ/ویرایش و نتیجه‌های جست‌وجو پاک */
      reset: function () { stop(true); if ($('ct-find-list')) { K.clear($('ct-find-list')); $('ct-find-st').textContent = ''; } },
      /** دکمه‌های این کارها در نوارِ کارهای یک پیام (پس از «حذف برای من/همه») */
      menu: function (box, x) {
        if (x.m.deleted) return;
        var b = function (act, text, fn) { box.appendChild(el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-act': act }, text: text, on: { click: fn } })); };
        if (open()) b('reply', t('ca.reply'), function () { startReply(x); });
        if (canEdit(x)) b('edit', t('ca.edit'), function () { startEdit(x); });
        if (pinned(x.m.id)) b('unpin', t('ca.unpin'), function () { pin(x, false); });
        else b('pin', t('ca.pin'), function () { pin(x, true); });
      },
      /** نقلِ پیامِ اصل بالای پاسخ و «ویرایش‌شده» کنارِ زمان */
      decorate: function (li, m, meta) {
        if (m.deleted) return;
        if (m.replyTo) {
          var x = H.info()[m.replyTo];
          li.insertBefore(el('button', { class: 'ct-quote', attrs: { type: 'button', 'data-quote': m.replyTo }, text: snip(x && x.m),
            on: { click: function () { jump(m.replyTo); } } }), li.firstChild);
        }
        if (m.editedAt) meta.insertBefore(el('small', { class: 'muted', attrs: { 'data-edited': '' }, text: t('ca.edited') }), meta.firstChild.nextSibling);
      },
      editing: function () { return Boolean(editing); },
      save: save,
      /** شناسهٔ پیامی که پاسخش نوشته می‌شود (فقط در همان رشته) */
      replyTo: function () { var c = H.conv(); return reply && c && reply.cid === c.id ? reply.id : null; },
      sent: function () { if (!editing) stop(false); },
    };
  };
})();
