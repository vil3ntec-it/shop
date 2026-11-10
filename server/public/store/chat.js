/*
 * ویترین — گفت‌وگوی مشتری با دکان (v3 §۳۳؛ یک رشته برای هر سفارش، `lib/shop-chat-life.js`).
 * دیربار: فقط با دکمهٔ «پرسش از دکان» ِ سرِ ویترین (`features.chat`) یا کلیدِ `chat` ِ پیگیریِ سفارش (`order.js`)
 * بار می‌شود؛ پرچمِ خاموشِ `shop.chat` ⇒ نه این اسکریپت، نه واژه‌نامه‌اش (`kit/i18n/chat.<زبان>.json`).
 *
 *   رشته ── هر سفارش رشتهٔ خودش؛ پرسشِ پیش از سفارش «گفت‌وگوی عمومی». رشته‌های پیشینِ همین شماره بالای رشتهٔ
 *           کنونی جمع‌شده می‌مانند (`<details>`)؛ پیام‌های دورِ پیشینِ گفت‌وگوی عمومی هم.
 *   بسته ── سفارشِ تمام‌شده ⇒ فقط خواندن؛ تا مهلتِ سرور «مشکل دارم» همان رشته را باز می‌کند. عمومیِ بی‌کار ⇒
 *           «گفت‌وگوی تازه». تصمیم فقط با سرور (`life`)؛ ۴۰۹ ِ سرور ⇒ خواندنِ دوباره، نه حدس.
 *   وضعیت ── پیامِ خودم «رفت / رسید / خوانده شد» فقط از `status` ِ سرور و رسیدِ واقعیِ دکان (`receipts.peer*Seq`)؛
 *           هرگز جلوتر و هرگز عقب (CHAT-04). پیامِ در صف فقط حبابِ «در صف» ِ جدا (`#ct-out`) دارد، هرگز «رفت».
 *   حذف ── «حذف برای من» هر پیام؛ «حذف برای همه» فقط پیامِ زندهٔ خودم، تا `deleteUntil` ِ سرور و نه در رشتهٔ بسته
 *           (#907). هر دو با پرسشِ تأیید؛ دکمه‌ها تا پاسخ خاموش (دوبار زدن یک درخواست)؛ سرور باز هم می‌سنجد (CHAT-08).
 * ⛔ هر متن فقط با textContent (`K.el`)؛ عکس با بلیت خوانده و data: می‌شود (CSP: img-src 'self' data:).
 * ⛔ شماره فقط با کدِ یک‌بارمصرف (`/verify`)؛ بلیتِ منقضی ⇒ همان فرمِ تأیید.
 * ⛔ پیامِ صوتی (CHAT-07): هیچ بایتی پیش از «پخش»؛ بایت با بلیت از همان درِ رسانه و پخش از blob: ِ همین صفحه
 *   (CSP: media-src 'self' blob:) — <audio> سرآیندِ بلیت را نمی‌فرستد و رسانهٔ چت پیوندِ امضاشده ندارد (S118).
 * ⛔ عکسِ مشتری (CHAT-05): کوچک‌کردن در `store/chat-photo.js` ِ دیربار (فقط با برگزیدنِ عکس)؛ پیش‌نمایش data: (img-src بی
 *   blob:)؛ بارگذاری با XHR برای پیشرفت، «لغو» و «تلاشِ دوباره»؛ یک `clientId` برای هر عکس و همان بایت ⇒ تکرار پیامِ دوم یا
 *   سهمِ دوم نمی‌سازد (CHAT-11)؛ بی‌اینترنت ⇒ backoff و `online` با همان بایت. سرور همه را دوباره می‌سنجد.
 *   صف ── پیامِ متنی (CHAT-02) با «فرستادن» از کادر به حبابِ «در صف» ِ `#ct-out` می‌رود و با همان `clientId` تا رسیدن می‌ماند،
 *           با «دوباره» و «لغو» و پس از بستنِ برگه (`store/chat-out.js` ِ دیربار، localStorage ِ `store-chat-out`، فقط متن)؛
 *           با گشودنِ دوبارهٔ گفت‌وگو فرستادن از سر گرفته می‌شود. تأییدِ شمارهٔ دیگر ⇒ صف پاک.
 *   کارها ── پاسخ، ویرایش، سنجاق و جست‌وجو (CHAT-14) در `store/chat-acts.js` ِ دیربار؛ نیامد ⇒ همین گفت‌وگو بی آن‌ها.
 * ⛔ صدای مشتری (CHAT-07): دکمهٔ میکروفون فقط با `limits.voice` (پرچمِ خاموشِ `shop.chat.voice`) و مرورگرِ دارای MediaRecorder؛
 *   ضبط، لغو، پیش‌شنوایی و بارگذاری در `store/chat-voice.js` ِ دیربار (همان `upload` و همان پیام با `clientId`).
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
    //  info[شناسه] = { m, cid, li, more, sp, st } — پیامِ نشان‌داده؛ acting = نوارِ کارهای بازِ یک پیام؛ inflight = حذفِ در راه
    var info = {}; var acting = null; var inflight = {};
    //  صفِ پیام‌های متنیِ نرفته (CHAT-02/11، `chat-out.js`): Q = منطق، oq = آخرین نمای صف، flying = پیامِ در راهِ همین برگه؛
    //  A = کارهای CHAT-14 (`chat-acts.js`)
    var Q = null; var oq = null; var flying = {}; var draining = false; var wake = 0; var A = null;
    var qslug = String(ctx.slug || '').toLowerCase();
    //  عکسِ در راه { conv, key, main, thumb, w, h, thumbId, mainId, xhr, wait } و سقف‌های سرور (`limits` ِ گشودن)
    var ph = null; var lim = null;
    //  opening = نوبتِ گشودنِ رشته (پاسخِ دیرِ گشودنِ پیشین نمی‌نشیند)؛ draftOf = رشته‌ای که متنِ کادر برایش نوشته شد
    var opening = 0; var draftOf = null;
    //  کمک‌های همین برگه برای `chat-acts.js`
    var H = { api: api, path: path, put: put, bar: bar, shut: shut, home: home, done: done, fail: fail, reload: reload,
      conv: function () { return conv; }, info: function () { return info; }, list: function () { return $('ct-list'); },
      note: function (m) { K.note($('ct-msg'), 'err', m); },
      adopt: function (c) { if (c && conv && conv.id === c.id) { conv = c; state(); } } };
    //  واژه‌نامه پیش از ساختنِ برگه (برگه هنوز نیست ⇒ ترجمهٔ دوباره لازم نیست؛ متن‌ها با t() از همین واژه‌نامه)
    var ready = K.part('chat', $('chat-sheet'));
    //  صف و کارها فقط با گشودنِ برگه (نه با کارتِ پیگیری)؛ نیامد ⇒ گشودنِ بعدی دوباره می‌خواهد
    var more = null;
    function extras() {
      var noop = function () { /* بی این بخش */ };
      return more || (more = Promise.all([
        Q ? null : K.js('/store/chat-out.js').then(function () { Q = window.StoreChatOut; oq = Q.forPhone(onDisk(), phone()); saveQ(); return Q.ready; }, noop),
        A ? null : K.js('/store/chat-acts.js').then(function () { A = window.StoreChatActs(H); return A.ready; }, noop),
      ]).then(function () { if (!Q || !A) more = null; }));
    }

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
          el('ol', { class: 'ct-list ct-outq', attrs: { id: 'ct-out' } }),
          el('p', { class: 'muted small', attrs: { id: 'ct-empty' }, text: t('ct.empty'), hidden: true })),
        el('div', { class: 'note', attrs: { id: 'ct-state', role: 'status' }, hidden: true }),
        el('button', { class: 'btn btn-block', attrs: { id: 'ct-issue', type: 'button' }, text: t('ct.issue'), hidden: true, on: { click: issue } }),
        el('button', { class: 'btn btn-block', attrs: { id: 'ct-new', type: 'button' }, text: t('ct.new'), hidden: true, on: { click: function () { start({}); } } }),
        el('div', { class: 'ct-ph', attrs: { id: 'ct-ph', role: 'group', 'aria-label': t('ct.ph'), 'data-st': 'idle' }, hidden: true },
          el('img', { class: 'ct-ph-img', attrs: { id: 'ct-ph-img', alt: t('ct.ph'), width: '96', height: '96' }, hidden: true }),
          el('div', { class: 'ct-ph-side' },
            el('progress', { class: 'ct-bar', attrs: { id: 'ct-ph-bar', max: '1', value: '0', 'aria-label': t('ct.ph.up') }, hidden: true }),
            el('small', { attrs: { id: 'ct-ph-st', role: 'status' } }),
            el('div', { class: 'ct-ph-acts' },
              el('button', { class: 'btn btn-accent', attrs: { id: 'ct-ph-send', type: 'button' }, text: t('ct.ph.send'), hidden: true, on: { click: function () { phGo(ph); } } }),
              el('button', { class: 'btn', attrs: { id: 'ct-ph-retry', type: 'button' }, text: t('ct.au.retry'), hidden: true, on: { click: function () { phGo(ph); } } }),
              el('button', { class: 'btn btn-ghost', attrs: { id: 'ct-ph-cancel', type: 'button' }, text: t('ct.ph.cancel'), on: { click: phDrop } })))),
        el('form', { class: 'ct-form', attrs: { id: 'ct-form', novalidate: '' }, hidden: true },
          el('button', { class: 'btn btn-icon btn-ghost', attrs: { id: 'ct-ph-add', type: 'button', 'aria-label': t('ct.ph.add') }, on: { click: function () { $('ct-file').click(); } } }, K.icon('image')),
          coarse() ? el('button', { class: 'btn btn-icon btn-ghost', attrs: { id: 'ct-ph-cam', type: 'button', 'aria-label': t('ct.ph.cam') }, on: { click: function () { $('ct-cam').click(); } } }, K.icon('camera')) : null,
          el('button', { class: 'btn btn-icon btn-ghost', attrs: { id: 'ct-vc-mic', type: 'button', 'aria-label': t('ct.au') }, hidden: true, on: { click: mic } }, K.icon('mic')),
          el('input', { attrs: { id: 'ct-file', type: 'file', accept: 'image/*' }, hidden: true, on: { change: pick } }),
          el('input', { attrs: { id: 'ct-cam', type: 'file', accept: 'image/*', capture: 'environment' }, hidden: true, on: { change: pick } }),
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
      //  ⛔ تمرکزِ دیر کادری را که مشتری همین حالا در فرم در آن است نمی‌رباید (پیش‌تر کدِ در حالِ نوشتن به کادرِ شماره می‌رفت ⇒ ۴۰۰)
      setTimeout(function () { if (!$('ct-verify').contains(document.activeElement)) $('ct-phone').focus(); }, 80);
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
        if (Q) { oq = Q.reverified(onDisk(), r.phone); saveQ(); }   //  شمارهٔ دیگر ⇒ صفِ نفرِ پیشین پاک؛ همان ⇒ دوباره در راه
        $('ct-code').value = ''; $('ct-verify').hidden = true; $('ct-vmsg').hidden = true;
        start(want || {});
      }).catch(function (e) { K.note($('ct-vmsg'), 'err', e.message); }).then(function () { b.disabled = false; });
    }

    // ── باز کردنِ رشته ──
    function start(body) {
      want = body;
      var my = ++opening;
      hold(body);
      $('ct-msg').hidden = true;
      if (!K.ticket()) { verify(); return Promise.resolve(); }
      $('ct-verify').hidden = true; $('ct-body').hidden = false;
      return api('/chat/conversations', { method: 'POST', body: body }).then(function (r) {
        if (my !== opening) return;   //  گشودنِ دیگری پس از این آغاز شد
        lim = r.limits || lim; show(r.conversation, true);
      }).catch(function (e) {
        if (my !== opening) return;
        $('ct-state').hidden = true; $('ct-body').removeAttribute('aria-busy');
        fail(e);
      });
    }
    //  ⛔ تا پاسخِ POST ِ همین رشته برگه نه پیام‌ها، سنجاق‌ها و کادرِ رشتهٔ پیشین را دارد و نه کاری از آن می‌پذیرد: `conv` ِ تهی
    //  فرستادن، عکس، صدا، «مشکل دارم» و رسید را خاموش می‌کند و `loop` ِ تازه long-poll ِ پیشین را می‌بُرد (پیش‌تر پیامِ سفارشِ #۴
    //  در همین فاصله به رشتهٔ #۳ می‌رفت). عنوان از خودِ درخواست، با «در حالِ باز کردن…».
    function hold(body) {
      hush();
      loop++; conv = null; cursor = 0; msgs = {}; info = {}; acting = null; inflight = {};
      phDrop(); $('ct-ph').hidden = true;
      if (window.StoreChatVoice) window.StoreChatVoice.drop();
      $('ct-form').hidden = true; $('ct-issue').hidden = true; $('ct-new').hidden = true;
      $('ct-prev').hidden = true; $('ct-old').hidden = true; $('ct-empty').hidden = true;
      K.clear($('ct-list')); K.clear($('ct-old-list')); K.clear($('ct-prev-list')); free();
      if (A) { A.reset(); A.sync(); }
      outs();
      $('ct-title').textContent = title(body);
      $('ct-body').setAttribute('aria-busy', 'true');
      K.note($('ct-state'), 'info', t('loading'));
    }
    function title(c) { return c.orderNo ? t('ct.order', { n: K.fa(c.orderNo, 0) }) : t('ct.general'); }
    function show(c, fresh) {
      if (fresh || !conv || conv.id !== c.id) {
        conv = c; cursor = 0; msgs = {}; info = {}; acting = null; inflight = {};
        K.clear($('ct-list')); K.clear($('ct-old-list')); prev();
        if (A) A.reset();
      }
      if (ph && ph.conv !== c.id) phDrop();   //  عکسِ آمادهٔ رشتهٔ دیگر به این رشته نمی‌رود
      if (window.StoreChatVoice) window.StoreChatVoice.drop(c.id);   //  صدا هم
      if (draftOf !== c.id) $('ct-text').value = '';   //  متنِ نیمه‌نوشتهٔ رشتهٔ دیگر به این رشته نمی‌رود
      conv = c; draftOf = c.id;
      $('ct-body').removeAttribute('aria-busy');
      $('ct-title').textContent = title(c);
      state();
      if (fresh) poll(++loop, true);
      outs(); drain();   //  صفِ همین دستگاه با هر گشودن از سر گرفته می‌شود
    }
    function state() {
      var l = conv.life || { state: 'open' };
      var box = $('ct-state');
      var closed = l.state === 'closed';
      $('ct-issue').hidden = !(closed && l.canIssue);
      $('ct-new').hidden = !(closed && l.canRestart);
      $('ct-form').hidden = !conv.canSend;
      $('ct-vc-mic').hidden = !(lim && lim.voice && canRec());
      if (closed && l.kind === 'general') K.note(box, 'warn', t('ct.st.idle'));
      else if (closed) K.note(box, 'warn', t('ct.st.order') + ' ' + (l.canIssue ? t('ct.st.issue', { d: K.when(l.issueUntil) }) : t('ct.st.late')));
      else if (l.state === 'reopened') K.note(box, 'info', t('ct.st.reopened'));
      else if (!conv.canSend) K.note(box, 'warn', t('ct.st.blocked'));
      else box.hidden = true;
      refresh();
      ticks();
      if (A) A.sync();
    }

    // ── رشته‌های پیشینِ همین شماره (جمع‌شده) ──
    function prev() {
      var me = conv.id;
      api('/chat/conversations?limit=50').then(function (r) {
        if (!conv || conv.id !== me) return;   //  رشتهٔ دیگری باز شد
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
      var old = msgs[m.id]; var was = info[m.id];
      //  همان نسخه (پاسخِ حذف و long-poll هر دو) ⇒ فقط وضعیت؛ حباب و تمرکز دست نمی‌خورند
      if (was && old && old.parentNode && !m.hidden && Number(m.rev) === Number(was.m.rev) && Boolean(m.deleted) === Boolean(was.m.deleted)) {
        if ((RANK[m.status] || 0) > (RANK[was.m.status] || 0)) was.m.status = m.status;
        if (was.sp) tick(was);
        return;
      }
      //  نوارِ بازِ همین پیام با نسخهٔ تازه بسته می‌شود؛ تمرکزِ درونِ حباب به دکمهٔ تازه (یا کادر) می‌رود
      //  کارِ در راهِ همین پیام دکمه‌اش را خاموش کرده و تمرکز به body افتاده ⇒ هنوز از آنِ همین حباب است (long-poll پیش از پاسخِ DELETE)
      var had = acting && acting.id === m.id ? acting : null;
      var focusIn = Boolean((had && (had.box.contains(document.activeElement) || (had.busy && document.activeElement === document.body))) ||
        (old && old.contains(document.activeElement)));
      if (had) acting = null;
      if (m.hidden) {
        if (old && old.parentNode) old.parentNode.removeChild(old);
        delete msgs[m.id]; delete info[m.id];
        if (focusIn) home();
        return;
      }
      var mine = m.sender === 'customer';
      var li = el('li', { class: 'ct-msg ' + (mine ? 'mine' : 'shop'), attrs: { 'data-id': m.id } });
      if (m.deleted) li.appendChild(el('p', { class: 'muted', text: t('ct.deleted') }));
      else {
        if (m.kind === 'image' && m.media) li.appendChild(photo(cid, m.media, m.sender === 'customer'));
        if (m.kind === 'audio' && m.media) li.appendChild(voice(cid, m));
        if (m.body) li.appendChild(el('p', { text: m.body }));
      }
      var x = { m: m, cid: cid, li: li, more: null, sp: null, st: was && !m.deleted ? was.st : null };
      var meta = el('div', { class: 'ct-meta' }, el('small', { class: 'muted num', text: K.clock(m.createdAt) }));
      if (mine && !m.deleted) { x.sp = el('small', { class: 'muted ct-st' }); meta.appendChild(x.sp); }
      //  کار فقط روی پیامِ همین رشته (رشته‌های پیشین فقط خواندنی‌اند)
      if (conv && cid === conv.id) {
        x.more = el('button', { class: 'ct-more', attrs: { type: 'button', 'aria-label': t('ct.act'), 'aria-expanded': 'false' },
          on: { click: function () { if (acting && acting.id === m.id) shut(true); else bar(m.id, null, '', true); } } }, K.icon('more'));
        meta.appendChild(x.more);
      }
      li.appendChild(meta);
      if (A) A.decorate(li, m, meta);
      if (old && old.parentNode) old.parentNode.replaceChild(li, old); else list.appendChild(li);
      msgs[m.id] = li; info[m.id] = x;
      if (x.sp) tick(x);
      if (focusIn) { if (x.more) x.more.focus(); else home(); }
    }

    // ── وضعیتِ پیامِ خودم (CHAT-04): «رفت» = سرور دارد؛ «رسید/خوانده شد» فقط با رسیدِ خودِ دکان؛ هرگز عقب ──
    var RANK = { sent: 1, delivered: 2, read: 3 };
    function tick(x) {
      var r = (conv && x.cid === conv.id && conv.receipts) || {};
      var seq = Number(x.m.seq) || 0;
      var s = RANK[x.m.status] ? x.m.status : 'sent';
      if (seq && seq <= (Number(r.peerReadSeq) || 0)) s = 'read';
      else if (seq && seq <= (Number(r.peerDeliveredSeq) || 0) && RANK[s] < 2) s = 'delivered';
      if (x.st && RANK[x.st] > RANK[s]) s = x.st;
      x.st = s;
      if (x.sp.getAttribute('data-st') === s) return;
      x.sp.setAttribute('data-st', s);
      x.sp.textContent = t(s === 'read' ? 'ct.mk.read' : s === 'delivered' ? 'ct.mk.delivered' : 'ct.mk.sent');
    }
    function ticks() { Object.keys(info).forEach(function (id) { if (info[id].sp) tick(info[id]); }); }

    // ── حذف (CHAT-08): نوارِ کارها درونِ همان حباب؛ پرسشِ تأیید؛ تا پاسخ همه خاموش ──
    K.ICONS.more = 'M6.5 12a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z M13.5 12a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z M20.5 12a1.5 1.5 0 1 1-3 0 1.5 1.5 0 0 1 3 0z';
    function home() { var f = $('ct-form').hidden ? $('ct-close') : $('ct-text'); if (f) f.focus(); }
    function canAll(x) {
      var l = (conv && conv.life) || {};
      return Boolean(conv) && x.cid === conv.id && x.m.sender === 'customer' && !x.m.deleted && l.state !== 'closed' &&
        (Number(x.m.deleteUntil) || 0) > Date.now();
    }
    function why(x) {
      if (x.m.sender !== 'customer' || x.m.deleted) return '';
      return ((conv && conv.life) || {}).state === 'closed' ? t('err.chat_closed') : t('err.delete_window_passed');
    }
    function shut(focus) {
      var a = acting; if (!a || a.busy) return;
      acting = null;
      if (a.box.parentNode) a.box.parentNode.removeChild(a.box);
      var x = info[a.id];
      if (x) { x.li.classList.remove('acting'); if (x.more) { x.more.setAttribute('aria-expanded', 'false'); x.more.removeAttribute('aria-controls'); if (focus) x.more.focus(); } }
    }
    /** ask: null (فهرست) | 'me' | 'all' (پرسشِ تأیید)؛ note: خطا/دلیل؛ focus: تمرکز به نخستین دکمه */
    function bar(id, ask, note, focus) {
      var x = info[id];
      if (!x || !x.more || !x.li.parentNode) return;
      if (acting && acting.id !== id) { if (acting.busy) return; shut(false); }
      if (acting && acting.busy) return;
      if (ask === 'all' && !canAll(x)) { ask = null; note = note || why(x); }
      var box = acting ? K.clear(acting.box) : el('div', { class: 'ct-acts', attrs: { id: 'ct-a-' + id, role: 'group', 'aria-label': t('ct.act') } });
      acting = { id: id, box: box, ask: ask, busy: false };
      var first;
      if (!ask) {
        first = el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-del': 'me' }, text: t('ct.del.me'), on: { click: function () { bar(id, 'me', '', true); } } });
        box.appendChild(first);
        if (canAll(x)) box.appendChild(el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-del': 'all' }, text: t('ct.del.all'), on: { click: function () { bar(id, 'all', '', true); } } }));
        if (A) A.menu(box, x);   //  پاسخ، ویرایش، سنجاق (CHAT-14) پس از حذف ⇒ نخستین کار همان «حذف برای من» می‌ماند
      } else {
        first = el('button', { class: 'btn btn-ghost btn-sm', attrs: { type: 'button', 'data-no': '' }, text: t('no'), on: { click: function () { bar(id, null, '', true); } } });
        var yes = el('button', { class: 'btn btn-danger btn-sm', attrs: { type: 'button', 'data-yes': ask }, text: t('ct.del.yes'), on: { click: function () { drop(id, ask); } } });
        box.appendChild(el('p', { class: 'ct-q', text: t(ask === 'all' ? 'ct.del.all.q' : 'ct.del.me.q') }));
        box.appendChild(el('div', { class: 'row' }, first, yes));
      }
      if (note) { var n = el('div', { attrs: { role: 'alert' } }); K.note(n, 'err', note); box.appendChild(n); }
      if (!box.parentNode) x.li.appendChild(box);
      x.li.classList.add('acting');
      x.more.setAttribute('aria-expanded', 'true');
      x.more.setAttribute('aria-controls', box.id);
      if (focus) first.focus();
    }
    //  رشته بسته شد یا مهلت گذشت (long-poll، خواندنِ دوباره) ⇒ «حذف برای همه» از نوارِ باز می‌رود، با دلیل
    function refresh() {
      if (!acting || acting.busy || acting.ask === 'me') return;
      var x = info[acting.id];
      if (!x || !x.li.parentNode) { acting = null; return; }
      var shown = acting.ask === 'all' || Boolean(acting.box.querySelector('[data-del="all"]'));
      if (shown !== canAll(x)) bar(x.m.id, acting.ask, shown ? why(x) : '', acting.box.contains(document.activeElement));
    }
    function drop(id, scope) {
      var x = info[id]; var a = acting;
      if (!x || !a || a.id !== id || a.busy) return;
      if (scope === 'all' && !canAll(x)) { bar(id, null, why(x), true); return; }
      var key = id + ':' + scope;
      if (inflight[key]) return;
      inflight[key] = true; a.busy = true;
      [].forEach.call(a.box.querySelectorAll('button'), function (b) { b.disabled = true; });
      var cid = x.cid;
      api(path(cid, '/messages/' + encodeURIComponent(id) + '?for=' + scope), { method: 'DELETE' }).then(function (r) {
        delete inflight[key]; a.busy = false;
        if (!conv || conv.id !== cid || !r.message) return;
        //  دکمهٔ خاموش‌شده تمرکز را به body می‌دهد ⇒ تمرکز به دکمهٔ کارهای سنگِ قبر، یا کادرِ نوشتن
        var lost = a.box.contains(document.activeElement) || document.activeElement === document.body;
        put(x.li.parentNode || $('ct-list'), r.message, cid);
        if (lost && !sheet.contains(document.activeElement)) { var nx = info[id]; if (nx && nx.more) nx.more.focus(); else home(); }
        $('ct-empty').hidden = $('ct-list').childNodes.length > 0;
        done(t(scope === 'all' ? 'ct.del.all.ok' : 'ct.del.me.ok'));
      }).catch(function (e) {
        delete inflight[key]; a.busy = false;
        if (e.status === 401) { if (acting === a) acting = null; fail(e); return; }
        if (!conv || conv.id !== cid) return;   //  رشتهٔ دیگری باز شد
        if (e.code === 'delete_window_passed') x.m.deleteUntil = 0;
        if (e.code === 'chat_closed') reload();
        var msg = e.status === 0 ? t('ct.del.off') : e.message;
        if (acting === a) bar(id, null, msg, true); else K.note($('ct-msg'), 'err', msg);
      });
    }
    //  «پاک شد» چند ثانیه، سپس خودش می‌رود (مگر یادداشتِ دیگری جایش نشسته باشد)
    var doneAt = 0;
    function done(text) {
      var box = $('ct-msg');
      K.note(box, 'ok', text);
      clearTimeout(doneAt);
      doneAt = setTimeout(function () { if (box.classList.contains('ok') && box.textContent === text) box.hidden = true; }, 4000);
    }
    function photo(cid, media, mine) {
      var img = el('img', { class: 'ct-img', attrs: { alt: t(mine ? 'ct.ph.mine' : 'ct.photo'), width: '200', height: '150' } });
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

    // ── پیامِ صوتی (CHAT-07): پیش از «پخش» نه <audio>، نه درخواست؛ نوعی که این مرورگر نمی‌شناسد اصلاً بار نمی‌شود ──
    //  ۴۰۴ ⇒ پاک‌شده یا سرآمده (بی «دوباره»)؛ بی‌اینترنت، ۴۲۹ یا ۵xx ⇒ «دوباره»؛ ۴۰۱ ⇒ فرمِ تأیید. <audio> ِ حبابِ برداشته
    //  (حذف برای همه) خودش می‌ایستد؛ blob اش با بارِ بعدی یا بستنِ برگه آزاد می‌شود.
    K.ICONS.play = 'M8 5.5v13l10.5-6.5z';
    K.ICONS.pause = 'M8.5 5.5v13 M15.5 5.5v13';
    var blobs = []; var probe = null;
    function free() {
      blobs = blobs.filter(function (b) { if (b.au.isConnected) return true; URL.revokeObjectURL(b.url); return false; });
    }
    function hush() { if (sheet) [].forEach.call(sheet.querySelectorAll('audio'), function (a) { a.pause(); }); free(); }
    function mmss(ms) {
      var s = Math.max(0, Math.round(ms / 1000)); var r = s % 60;
      return K.fa(Math.floor(s / 60), 0) + ':' + (r < 10 ? K.fa(0, 0) : '') + K.fa(r, 0);
    }
    function voice(cid, m) {
      var media = m.media; var known = Number((m.meta || {}).durationMs) || 0;
      var au = null; var busy = false;
      var btn = el('button', { class: 'btn btn-icon ct-play', attrs: { type: 'button', 'aria-label': t('ct.au.play') } }, K.icon('play'));
      var bar = el('progress', { class: 'ct-bar', attrs: { max: '1', value: '0', 'aria-label': t('ct.au') } });
      var time = el('span', { class: 'num ct-time', text: known ? mmss(known) : '' });
      var note = el('small', { class: 'ct-aerr', hidden: true });
      var box = el('div', { class: 'ct-voice', attrs: { role: 'group', 'aria-label': t('ct.au'), 'data-st': 'idle' } }, btn, bar, time, note);
      function mode(st, icon, label) {
        box.setAttribute('data-st', st);
        if (st === 'loading') box.setAttribute('aria-busy', 'true'); else box.removeAttribute('aria-busy');
        btn.replaceChild(K.icon(icon), btn.firstChild);
        btn.setAttribute('aria-label', label);
      }
      function stop(text, again) {
        busy = false; btn.disabled = !again;
        note.textContent = text; note.hidden = false;
        mode('err', 'play', t(again ? 'ct.au.retry' : 'ct.au.play'));
      }
      function tick() {
        var d = au && isFinite(au.duration) && au.duration > 0 ? au.duration * 1000 : known;
        var c = au && !au.ended ? au.currentTime * 1000 : 0;
        bar.value = d ? Math.min(1, c / d) : 0;
        time.textContent = (c > 0 ? mmss(c) + ' / ' : '') + (d ? mmss(d) : '');
      }
      function go() {
        var p = au.play();
        //  Safari پخش پس از fetch را بی لمسِ تازه نمی‌پذیرد ⇒ آماده، «پخش» ِ دوباره
        if (p && p.catch) p.catch(function (e) { if (e && e.name === 'NotAllowedError') mode('ready', 'play', t('ct.au.play')); });
      }
      function broke() {
        if (au && au.parentNode) au.parentNode.removeChild(au);
        au = null; free();
        stop(t('ct.au.codec'), false);
      }
      function load() {
        var h = {}; var tk = K.ticket(); var ss = K.sess();
        if (tk) h['X-Store-Ticket'] = tk.ticket;
        if (ss) h['X-Store-Session'] = ss.token;
        var no = function (s) { var e = new Error('media'); e.status = s; return e; };
        busy = true; note.hidden = true; free();
        mode('loading', 'play', t('ct.au.play'));
        fetch(base + path(cid, '/media/' + encodeURIComponent(media.id)), { headers: h, credentials: 'omit', cache: 'no-store' })
          .then(function (res) { if (!res.ok) throw no(res.status); return res.blob(); }, function () { throw no(0); })
          .then(function (b) {
            var url = URL.createObjectURL(b);
            au = el('audio', { attrs: { preload: 'auto' }, on: {
              play: function () { mode('playing', 'pause', t('ct.au.pause')); tick(); },
              pause: function () { mode('ready', 'play', t('ct.au.play')); tick(); },
              ended: tick, timeupdate: tick, durationchange: tick, error: broke,
            } });
            au.src = url;
            box.appendChild(au);
            blobs.push({ au: au, url: url });
            busy = false;
            go();
          })
          .catch(function (e) {
            var s = e && e.status;
            if (s === 401) { busy = false; mode('idle', 'play', t('ct.au.play')); fail(e); }
            else if (s === 404 || s === 410) stop(t('ct.au.gone'), false);
            else stop(t(s === 0 ? 'err.network' : s === 429 ? 'err.rate' : 'err.generic'), true);
          });
      }
      btn.addEventListener('click', function () {
        if (busy) return;
        if (au) { if (au.paused) go(); else au.pause(); return; }
        probe = probe || el('audio');
        if (media.mime && !probe.canPlayType(media.mime)) { stop(t('ct.au.codec'), false); return; }
        load();
      });
      return box;
    }
    function render(list) {
      var l = conv.life || {};
      var cut = l.kind === 'general' && l.sessionAt ? l.sessionAt : 0;
      list.forEach(function (m) { put(cut && m.createdAt < cut ? $('ct-old-list') : $('ct-list'), m, conv.id); });
      var old = $('ct-old-list').childNodes.length;
      $('ct-old').hidden = !old;
      $('ct-old-s').textContent = t('ct.old', { n: K.fa(old, 0) });
      $('ct-empty').hidden = $('ct-list').childNodes.length > 0;
      if ($('ct-out').childNodes.length) $('ct-empty').hidden = true;
      if (A) A.sync();
      var last = $('ct-out').lastChild || $('ct-list').lastChild;
      if (last && last.scrollIntoView) last.scrollIntoView({ block: 'end' });
    }
    //  نخستین خواندن بی صبر؛ پس از آن همیشه long-poll — رشتهٔ بی‌پیام (cursor همان ۰) هم پیاپی نمی‌پرسد
    function poll(my, first) {
      if (my !== loop || K.sheetOpen() !== sheet || !conv) return;
      var id = conv.id;
      //  CHAT-04: رسیدهای دانسته (`pd`/`pr`) ⇒ رسیدِ دکان که میانِ دو پرسش نشست همان دم می‌رسد، نه پس از مهلت (`chat-wait.js`)
      var rc = conv.receipts || {};
      api(path(id, '/messages?after=' + cursor + '&wait=' + (first ? 0 : WAIT) + '&pd=' + (Number(rc.peerDeliveredSeq) || 0) + '&pr=' + (Number(rc.peerReadSeq) || 0))).then(function (r) {
        if (my !== loop || !conv || conv.id !== id) return;
        if (r.conversation) { conv = r.conversation; state(); }
        cursor = r.cursor;
        render(r.messages || []);
        if (r.messages && r.messages.length) receipt(id, conv.seq || 0);
        poll(my, false);
      }).catch(function (e) {
        if (my !== loop) return;
        if (e.status === 401) { fail(e); return; }
        setTimeout(function () { poll(my, first); }, 5000);
      });
    }

    //  «خواندم» فقط وقتی برگه دیده می‌شود (CHAT-04)؛ برگهٔ پنهانِ مرورگر فقط «رسید»
    function seen() { return document.visibilityState !== 'hidden'; }
    function receipt(id, seq) {
      api(path(id, '/receipts'), { method: 'POST', body: seen() ? { deliveredSeq: seq, readSeq: seq } : { deliveredSeq: seq } })
        .then(function (r) { if (conv && r.conversation && conv.id === r.conversation.id) { conv.receipts = r.conversation.receipts; ticks(); } })
        .catch(function () { /* بعدی */ });
    }
    document.addEventListener('visibilitychange', function () {
      if (!seen() || !conv || K.sheetOpen() !== sheet) return;
      if (((conv.receipts || {}).myReadSeq || 0) < (conv.seq || 0)) receipt(conv.id, conv.seq);
    });

    // ── صفِ پیام‌های متنی (CHAT-02/11): متن از کادر به حبابِ «در صف»؛ هر گفت‌وگو فقط سرِ صفش، یکی‌یکی، با همان `clientId` ──
    function phone() { var tk = K.ticket(); var ss = K.sess(); return (tk && tk.phone) || (ss && ss.phone) || ''; }
    //  localStorage حرفِ آخر است (برگه‌های دیگرِ همین ویترین هم می‌نویسند): هر کار = خواندن ⇐ تغییر ⇐ نوشتن، و پیامِ در راهِ
    //  همین برگه «در راه» می‌ماند. «در راه» ِ برگهٔ دیگر این‌جا «در صف» است ⇒ شاید دو برگه یک پیام را بفرستند؛ همان `clientId`
    //  ⇒ سرور یکی نگه می‌دارد (`replayed`).
    function onDisk() {
      var d = Q.load(K.load(Q.KEY, null));
      Object.keys(flying).forEach(function (id) { d = Q.sending(d, id); });
      return d;
    }
    //  فقط تغییرِ واقعی نوشته می‌شود ⇒ رویدادِ `storage` میانِ دو برگه پینگ‌پنگ نمی‌شود
    function saveQ() {
      var v = oq.items.length ? Q.dump(oq) : null;
      try { if (localStorage.getItem(Q.KEY) !== v) { if (v === null) localStorage.removeItem(Q.KEY); else localStorage.setItem(Q.KEY, v); } } catch (e) { /* بی حافظه */ }
    }
    function send() {
      if (!conv) return;
      if (A && A.editing()) { A.save(); return; }
      if (!Q) { K.note($('ct-msg'), 'err', t('ct.err')); return; }
      var r = Q.add(Q.forPhone(onDisk(), phone()), { slug: qslug, convId: conv.id, body: $('ct-text').value, replyTo: A ? A.replyTo() : null }, Date.now(), Math.random);
      if (r.error) { if (r.error !== 'empty') K.note($('ct-msg'), 'err', Q.why(r.error)); return; }   //  رد ⇒ متن در کادر، هرگز بریده
      oq = r.state; saveQ();
      $('ct-text').value = ''; $('ct-msg').hidden = true;
      if (A) A.sent();
      outs(); drain();
      var b = $('ct-out').lastChild;
      if (b && b.scrollIntoView) b.scrollIntoView({ block: 'end' });
    }
    function outs() {
      var ol = $('ct-out');
      if (!Q || !ol) return;
      Q.render(ol, conv ? Q.forConv(oq, conv.id) : [], { retry: resend, cancel: unsend, home: home });
      if (ol.childNodes.length) $('ct-empty').hidden = true;
    }
    function resend(id) { oq = Q.retry(onDisk(), id); saveQ(); outs(); drain(); }
    function unsend(id) { var r = Q.cancel(onDisk(), id); oq = r.state; if (r.ok) saveQ(); outs(); }   //  در راه ⇒ لغو نمی‌شود
    function drain() {
      clearTimeout(wake);
      if (!Q || draining) return;
      oq = onDisk();
      var p = phone() ? Q.forPhone(oq, phone()) : oq;   //  ⛔ پیامِ شمارهٔ دیگر هرگز با بلیتِ این شماره نمی‌رود
      if (p !== oq) { oq = p; saveQ(); }
      var x = Q.due(oq, qslug, Date.now());
      if (!x) {
        outs();
        var at = Q.wakeAt(oq, qslug);
        if (at !== null) wake = setTimeout(drain, Math.max(250, at - Date.now()));
        return;
      }
      if (!phone()) { oq = Q.failed(oq, x.clientId, { status: 401, code: 'verification_required' }, Date.now(), 0); saveQ(); outs(); return; }
      draining = true; flying[x.clientId] = true;
      oq = Q.sending(oq, x.clientId); saveQ(); outs();
      var body = { clientId: x.clientId, body: x.body };
      if (x.replyTo) body.replyTo = x.replyTo;
      api(path(x.convId, '/messages'), { method: 'POST', body: body }).then(function (r) {
        delete flying[x.clientId];
        oq = Q.done(onDisk(), x.clientId); saveQ();
        if (conv && conv.id === x.convId && r.message) { put($('ct-list'), r.message, x.convId); $('ct-empty').hidden = true; }
      }, function (e) {
        delete flying[x.clientId];
        oq = Q.failed(onDisk(), x.clientId, e, Date.now(), Math.random()); saveQ();
        if (e.code === 'chat_closed' || e.code === 'chat_blocked') reload();
        if (Q.failureOf(e) === 'verify') fail(e);   //  همهٔ صف تا تأییدِ دوباره می‌ماند
      }).then(function () { draining = false; outs(); drain(); });
    }
    window.addEventListener('online', function () { if (Q) { oq = Q.online(onDisk()); saveQ(); drain(); } });
    window.addEventListener('storage', function (e) { if (Q && e.key === Q.KEY) { oq = onDisk(); outs(); drain(); } });

    // ── عکسِ مشتری (CHAT-05): برگزیدن ⇒ کوچک‌کردن (دیربار) و پیش‌نمایش ⇒ «فرستادن» ⇒ پیش‌نمایش، اصل، پیام ──
    //  «۱» ِ نوار فقط پس از پذیرشِ پیام؛ «لغو» بارگذاری را می‌بُرد (پیام هنوز نرفته)؛ تلاشِ دوباره همان شناسه و همان بایت را
    //  می‌فرستد ⇒ سرور همان رسانه (`reused`) و همان پیام (`replayed`) را برمی‌گرداند و سهم را پس می‌دهد.
    K.ICONS.image = 'M4 5h16v14H4z M4 16l4.5-4.5 3.5 3.5 2.5-2.5L20 17 M15.5 9.5h.01';
    K.ICONS.camera = 'M4 8h3.5L9 5.5h6L16.5 8H20v11H4z M12 16.5a3 3 0 1 0 0-6 3 3 0 0 0 0 6z';
    function coarse() { return Boolean(window.matchMedia && window.matchMedia('(pointer: coarse)').matches); }
    function phMode(st, text) {
      var box = $('ct-ph');
      box.setAttribute('data-st', st);
      if (st === 'prep' || st === 'up' || st === 'post') box.setAttribute('aria-busy', 'true'); else box.removeAttribute('aria-busy');
      $('ct-ph-bar').hidden = !(st === 'up' || st === 'post' || st === 'queued');
      $('ct-ph-send').hidden = st !== 'ready';
      $('ct-ph-retry').hidden = !(st === 'queued' || st === 'retry');
      $('ct-ph-cancel').disabled = st === 'post';
      $('ct-ph-st').textContent = text || '';
    }
    function phDrop() {
      var my = ph;
      if (!my || my.posting) return;
      ph = null; clearTimeout(my.timer);
      if (my.xhr) my.xhr.abort();
      $('ct-ph').hidden = true; $('ct-ph-img').hidden = true; $('ct-ph-img').removeAttribute('src');
    }
    function pick(e) {
      var f = e.target.files && e.target.files[0];
      e.target.value = '';
      if (!f || !conv || !conv.canSend) return;
      phDrop();
      if (ph) return;   //  پیامِ عکسِ پیشین در راه است
      var my = ph = { conv: conv.id, key: 'ct-' + Date.now().toString(36) + '-' + Math.random().toString(36).slice(2, 10), wait: 2000 };
      $('ct-ph').hidden = false; $('ct-ph-bar').value = 0;
      phMode('prep', t('ct.ph.prep'));
      K.js('/store/chat-photo.js').then(function () { return window.StoreChatPhoto.prepare(f, lim); }, function () { var n = new Error('net'); n.net = true; throw n; })
        .then(function (p) {
          if (ph !== my) return '';
          my.main = p.main; my.thumb = p.thumb; my.w = p.width; my.h = p.height;
          return window.StoreChatPhoto.dataUrl(p.thumb);
        })
        .then(function (u) {
          if (ph !== my) return;
          if (/^data:image\/jpeg;/.test(u)) { $('ct-ph-img').src = u; $('ct-ph-img').hidden = false; }
          phMode('ready');
          $('ct-ph-send').focus();
        })
        .catch(function (x) { if (ph === my) phMode('err', x && x.net ? t('err.network') : t('ct.ph.bad')); });
    }
    function upErr(s, b) {
      var e = (b && b.error) || {}; var c = e.code || '';
      var err = new Error(s === 0 ? t('err.network') : s === 429 && !K.has('err.' + c) ? t('err.rate')
        : K.has('err.' + c) ? t('err.' + c) : (K.lang === 'fa' && e.message ? e.message : t('err.generic')));
      err.status = s; err.code = c;
      return err;
    }
    //  بارگذاری با XHR (fetch پیشرفتِ فرستادن ندارد)؛ همان سرآیندهای `K.api`. status ِ ‎-1‎ = لغو، ۰ = بی‌اینترنت
    function upload(my, role, blob, prog, type) {
      return new Promise(function (ok, no) {
        var x = new XMLHttpRequest(); var tk = K.ticket(); var ss = K.sess();
        my.xhr = x;
        x.open('POST', base + path(my.conv, '/media') + (role === 'thumb' ? '?role=thumb' : ''));
        x.setRequestHeader('Accept', 'application/json');
        x.setRequestHeader('Accept-Language', K.lang);
        x.setRequestHeader('Content-Type', type || 'image/jpeg');
        if (tk) x.setRequestHeader('X-Store-Ticket', tk.ticket);
        if (ss) x.setRequestHeader('X-Store-Session', ss.token);
        x.upload.onprogress = function (e) { if (e.lengthComputable) prog(e.loaded); };
        x.onload = function () {
          my.xhr = null;
          var b = null;
          try { b = JSON.parse(x.responseText); } catch (_) { /* نه JSON */ }
          if (x.status >= 200 && x.status < 300 && b && b.mediaId) ok(b); else no(upErr(x.status, b));
        };
        x.onerror = function () { my.xhr = null; no(upErr(0)); };
        x.onabort = function () { my.xhr = null; no(upErr(-1)); };
        x.send(blob);
      });
    }
    function phGo(my) {
      if (!my || ph !== my || my.busy || !my.main) return;
      clearTimeout(my.timer);
      my.busy = true; my.queued = false;
      phMode('up');
      var total = my.thumb.size + my.main.size;
      var bar = function (n) { $('ct-ph-bar').value = Math.min(0.99, n / total); };
      var still = function () { if (ph !== my) throw upErr(-1); };
      Promise.resolve().then(function () {
        if (!my.thumbId) return upload(my, 'thumb', my.thumb, bar).then(function (r) { my.thumbId = r.mediaId; });
        return null;
      }).then(function () {
        still();
        if (!my.mainId) return upload(my, 'main', my.main, function (n) { bar(my.thumb.size + n); }).then(function (r) { my.mainId = r.mediaId; });
        return null;
      }).then(function () {
        still();
        my.posting = true; phMode('post'); bar(total);
        return api(path(my.conv, '/messages'), { method: 'POST', body: {
          clientId: my.key, kind: 'image', mediaId: my.mainId, thumbId: my.thumbId, meta: { width: my.w, height: my.h } } });
      }).then(function (r) {
        my.busy = my.posting = false;
        $('ct-ph-bar').value = 1;
        if (ph === my) phDrop();
        if (conv && conv.id === my.conv) { put($('ct-list'), r.message, my.conv); $('ct-empty').hidden = true; }
      }).catch(function (e) {
        my.busy = my.posting = false;
        if (ph !== my || e.status === -1) return;
        if (e.status === 0) {   //  بی‌اینترنت: همان صفِ متن — با `online` یا پس از کمی، با همان شناسه و همان بایت
          my.queued = true;
          phMode('queued', t('ct.queued'));
          my.wait = Math.min(60000, my.wait * 2);
          my.timer = setTimeout(function () { phAgain(my); }, my.wait);
          return;
        }
        if (e.code === 'chat_closed' || e.code === 'chat_blocked') reload();
        phMode(e.status === 401 || e.status === 429 || e.status >= 500 ? 'retry' : 'err', e.message);
        if (e.status === 401) fail(e);
      });
    }
    function phAgain(my) { if (ph === my && conv && conv.id === my.conv) phGo(my); }
    window.addEventListener('online', function () { if (ph && ph.queued) phAgain(ph); });

    // ── صدای مشتری (CHAT-07): دکمه ⇒ `store/chat-voice.js` ِ دیربار؛ همان `upload`، همان `api` و همان حباب ──
    K.ICONS.mic = 'M12 4a3 3 0 0 0-3 3v5a3 3 0 0 0 6 0V7a3 3 0 0 0-3-3z M6 11.5a6 6 0 0 0 12 0 M12 17.5V20';
    function canRec() { return Boolean(window.MediaRecorder && navigator.mediaDevices && navigator.mediaDevices.getUserMedia); }
    var vctx = { conv: function () { return conv; }, lim: function () { return lim; }, upload: upload, api: api, path: path, fail: fail, reload: reload,
      put: function (m, cid) { if (conv && conv.id === cid) { put($('ct-list'), m, cid); $('ct-empty').hidden = true; } } };
    function mic() {
      if (!conv || !conv.canSend) return;
      var b = $('ct-vc-mic'); b.disabled = true;
      K.js('/store/chat-voice.js').then(function () { return window.StoreChatVoice.open(vctx); })
        .catch(function () { K.note($('ct-msg'), 'err', t('err.network')); })
        .then(function () { b.disabled = false; });
    }
    //  پاسخِ دیرِ رشته‌ای که دیگر باز نیست نمی‌نشیند (`conv` ِ رشتهٔ دیگر را جایگزین نمی‌کند)
    function issue() {
      if (!conv) return;
      var b = $('ct-issue'); b.disabled = true; var id = conv.id;
      api(path(id, '/issue'), { method: 'POST', body: {} }).then(function (r) {
        if (!conv || conv.id !== id) return;
        conv = r.conversation; state(); $('ct-msg').hidden = true; $('ct-text').focus();
      }).catch(function (e) { if (e.status === 401 || (conv && conv.id === id)) { reload(); fail(e); } }).then(function () { b.disabled = false; });
    }
    function reload() {
      if (!conv) return;
      var id = conv.id;
      api(path(id)).then(function (r) { if (conv && conv.id === id) { conv = r.conversation; state(); } }).catch(function () { /* همان */ });
    }

    //  برگه همان دم باز می‌شود؛ رشته پس از رسیدنِ صف و کارها (بارِ نخستِ برگه، سپس از حافظهٔ `K.js`)
    function openWith(body) {
      var go = extras();
      return ready.then(function () {
        build();
        hold(body);   //  از نخستین فریم: نه رشتهٔ پیشین، نه کادرش (تا صف و کارها برسند و POST ِ همین رشته پاسخ دهد)
        K.openSheet(sheet, function () { loop++; hush(); if (window.StoreChatVoice) window.StoreChatVoice.drop(); });
        return go;
      }).then(function () {
        if (A) A.mount();
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
