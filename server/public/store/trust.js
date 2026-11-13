/*
 * ویترین — «گزارشِ مشکل» (ABUSE-01، v3 §۳۸.۶): شکایت از دکان یا یکی از سفارش‌های خودِ خریدار (پایینِ صفحهٔ دکان) و
 * گزارشِ یک کالا (برگهٔ کالا) ⇐ `POST /api/store/<slug>/trust/complaints|reports` ِ `routes/trust-store.js`.
 * فقط با `features.trust` (پرچمِ خاموشِ `market.trust`) و دیربار از catalog.js؛ پرچمِ خاموش ⇒ نه اسکریپت، نه واژه‌نامه
 * (`kit/i18n/trust.<زبان>.json`)، نه هیچ گره‌ای.
 *
 *   • ثبت فقط با شمارهٔ تأییدشده (همان بلیتِ مشترکِ ویترین، `K.ticket()`)؛ نبود یا کهنه (۴۰۱) ⇒ همین‌جا کد و پس از
 *     تأیید همان فرستادن.
 *   • سقفِ نرخ سمتِ سرور است (حافظه‌ای به‌ازای IP و شماره، روزانهٔ ماندگار)؛ ۴۲۹ ⇒ پیامِ خودش و دکمه دوباره باز —
 *     این‌جا هیچ تلاشِ خودکاری نیست. تکرارِ همان موضوعِ باز ⇒ همان پرونده (`duplicate`).
 *   • پیگیری با توکنِ همان پرونده که فقط روی همین دستگاه می‌ماند (`store-trust:<slug>`، حداکثر ۱۰)؛ وضعیت، نتیجه و
 *     پاسخِ دکان هر بار از `GET …/trust/cases/<توکن>` تازه می‌شود.
 *   • مدرک (v3 §۳۸.۶ «پرونده با شواهد»): پروندهٔ «باز»/«در حالِ بررسی» در «گزارش‌های من» دکمهٔ «افزودنِ عکس» دارد ⇒
 *     `StoreChatPhoto.prepare` (JPEG ِ تازه از پیکسل زیرِ `limits.imageMax` ِ `GET …/trust/config` ⇒ بی EXIF و مکان) ⇒ بدنهٔ خام به
 *     `POST …/trust/cases/<توکن>/evidence` با همان توکن. مرز سرور است: نوع از بایت، سقفِ حجم پیش از خواندن، پاک کردنِ EXIF،
 *     حداکثر `evidenceMax` برای هر پرونده، همان بایت ⇒ همان ردیف، سطلِ IP ِ بارگذاری؛ ۴۱۳/۴۱۵/۴۰۹/۴۲۹ ⇒ پیامِ خودش.
 * ⛔ همهٔ متن با textContent (`K.el`)؛ توکن نه در نشانی می‌رود نه در صفحه دیده می‌شود؛ نام و شمارهٔ خریدار به دکان نمی‌رسد.
 */
(function () {
  'use strict';
  //  همان دسته‌های `lib/market-trust.js` (COMPLAINT / REPORT)
  var COMPLAINT = ['not_delivered', 'wrong_item', 'damaged', 'overcharged', 'fraud', 'rude', 'other'];
  var REPORT = ['counterfeit', 'prohibited', 'misleading', 'offensive', 'wrong_price', 'other'];
  var MAX_BODY = 2000;
  var KEEP = 10;
  var TOKEN = /^tc_[0-9a-f]{24}\.[A-Za-z0-9_-]{32}$/;
  var PID = /^[A-Za-z0-9_.:-]{1,80}$/;

  /** رقمِ فارسی/عربی ⇐ لاتین؛ فاصله و «#» بیرون */
  function digits(v) {
    return String(v == null ? '' : v).replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s#]/g, '');
  }

  /**
   * بدنهٔ درخواست از فرم ⇒ `{ body }` یا `{ problem }` ('category' | 'text' | 'order' | 'product'). همان قاعده‌های
   * `lib/market-trust.file`: دستهٔ شناخته، متن تا ۲۰۰۰ نویسه، شمارهٔ سفارش عددِ صحیحِ مثبت (خالی ⇒ دربارهٔ خودِ دکان)،
   * کالای درست. «دیگر» بی شرح چیزی به رسیدگی نمی‌گوید ⇒ شرح لازم.
   */
  function draft(kind, f) {
    var cats = kind === 'report' ? REPORT : COMPLAINT;
    var category = String(f.category || '');
    if (cats.indexOf(category) < 0) return { problem: 'category' };
    var text = String(f.text || '').trim();
    if (text.length > MAX_BODY || (category === 'other' && !text)) return { problem: 'text' };
    var b = { category: category, body: text };
    if (kind === 'report') {
      if (!PID.test(String(f.productId || ''))) return { problem: 'product' };
      b.productId = String(f.productId);
      return { body: b };
    }
    var no = digits(f.orderNo);
    if (!no) { b.subject = 'shop'; return { body: b }; }
    if (!/^[1-9][0-9]{0,14}$/.test(no)) return { problem: 'order' };
    b.subject = 'order';
    b.orderNo = Number(no);
    return { body: b };
  }

  /** توکن‌های همین دستگاه: تازه‌ترین اول، بی تکرار، فقط شکلِ درست، حداکثر ۱۰ */
  function keep(list, token) {
    return [token].concat(Array.isArray(list) ? list : []).filter(function (x, i, a) {
      return typeof x === 'string' && TOKEN.test(x) && a.indexOf(x) === i;
    }).slice(0, KEEP);
  }

  var lib = { COMPLAINT: COMPLAINT, REPORT: REPORT, MAX_BODY: MAX_BODY, KEEP: KEEP, digits: digits, draft: draft, keep: keep };
  if (typeof module === 'object' && module.exports) { module.exports = lib; return; }

  var $ = K.$; var el = K.el; var t = K.t;
  var OPEN = ['open', 'in_review'];

  /** پیامِ خطای بارگذاری از کدِ سرور (واژه‌نامهٔ همین بخش، سپس کیت) */
  function evErr(status, code) {
    return new Error(K.has('tr.ev.err.' + code) ? t('tr.ev.err.' + code) : K.has('err.' + code) ? t('err.' + code)
      : status === 429 ? t('err.rate') : t('err.generic'));
  }

  /**
   * ctx = { api, slug, into } از catalog.js: بخشِ «گزارشِ مشکل با این دکان» تهِ [into] می‌نشیند. برمی‌گرداند
   * Promise<{ seen(p) } | null> — `seen` دکمهٔ «گزارشِ این کالا» را در برگهٔ باز می‌گذارد. هرگز رد نمی‌شود.
   */
  window.StoreTrust = function (ctx) {
    var api = ctx.api;
    var SAVE = 'store-trust:' + ctx.slug;
    var BASE = '/api/store/' + encodeURIComponent(ctx.slug);
    var lim = null;   //  `GET …/trust/config` ِ همان سرور، یک بار
    return K.part('trust', $('tr-box')).then(function (ok) {
      if (!ok || !ctx.into || $('tr-box')) return null;
      var mine = el('ul', { class: 'stack small', attrs: { id: 'tr-mine', 'aria-label': t('tr.mine') } });
      var box = el('details', { class: 'more-box section', attrs: { id: 'tr-box' } }, el('summary', { text: t('tr.shop') }),
        el('p', { class: 'muted small', text: t('tr.p') }), form('complaint', 'c', null), mine);
      ctx.into.appendChild(box);
      box.addEventListener('toggle', function () { if (box.open) track(); });

      /** پرونده‌های همین دستگاه، هر بار تازه از سرور؛ پروندهٔ پیدانشده (دکانِ دیگر، کهنه) از فهرست می‌رود */
      function track() {
        var list = keep(K.load(SAVE, []), '');
        if (!list.length) { K.clear(mine); return; }
        lim = lim || api('/trust/config').then(function (r) { return r.limits || {}; }, function () { lim = null; return {}; });
        Promise.all([lim].concat(list.map(function (tk) {
          return api('/trust/cases/' + encodeURIComponent(tk)).then(function (r) { return r.case; }, function (e) {
            return e.status === 404 || e.status === 410 ? 'gone' : null;
          });
        }))).then(function (all) {
          var limits = all[0]; var cases = all.slice(1);
          var left = list.filter(function (tk, i) { return cases[i] !== 'gone'; });
          if (left.length !== list.length) K.save(SAVE, left);
          K.clear(mine);
          cases.forEach(function (c) { if (c && c !== 'gone') mine.appendChild(row(c, limits)); });
          if (mine.firstChild) mine.insertBefore(el('li', { class: 'muted', text: t('tr.mine') }), mine.firstChild);
        });
      }

      function row(c, limits) {
        var cat = t((c.kind === 'report' ? 'tr.r.' : 'tr.c.') + c.category);
        var parts = [cat, t('tr.s.' + (c.status || 'open'))];
        if (c.outcome) parts.push(t('tr.o.' + c.outcome));
        var li = el('li', { class: 'card', attrs: { 'data-status': c.status || '' } },
          el('div', null, el('b', { text: parts.join(t('sep')) })), el('div', { class: 'muted', text: K.when(c.createdAt) }));
        (c.history || []).filter(function (h) { return h.type === 'shop_response' && h.body; }).slice(-1).forEach(function (h) {
          li.appendChild(el('p', { text: t('tr.reply', { text: h.body }) }));
        });
        evidence(c, li, limits || {});
        return li;
      }

      /**
       * «N عکس پیوست شد» با «دیدنِ عکس‌ها» (به خواست: `GET …/evidence/<شناسه>` ⇒ data: ِ همین صفحه، توکن در DOM نه) و، فقط
       * برای پروندهٔ باز، «افزودنِ عکس» تا سقفِ سرور.
       */
      function evidence(c, li, limits) {
        var ids = (Array.isArray(c.evidence) ? c.evidence : []).map(function (e) { return String(e && e.id); });
        var open = OPEN.indexOf(c.status || 'open') >= 0 && TOKEN.test(String(c.token || ''));
        var max = limits.evidenceMax || 5;
        if (!ids.length && !open) return;
        var state = el('span', { class: 'tr-ev-n' });
        var view = el('button', { class: 'btn btn-ghost btn-sm tr-ev-view', text: t('tr.ev.view'), attrs: { type: 'button' } });
        var pics = el('div', { class: 'stack tr-ev-pics', hidden: true });
        var input = el('input', { class: 'tr-ev-file', hidden: true, attrs: { type: 'file', accept: 'image/jpeg,image/png,image/webp', 'aria-label': t('tr.ev.add') } });
        var add = el('button', { class: 'btn btn-ghost btn-sm tr-ev-add', text: t('tr.ev.add'), attrs: { type: 'button' } });
        //  `K.note` کلاس را از نو می‌نویسد ⇒ نشانه با data
        var msg = el('div', { class: 'note', hidden: true, attrs: { role: 'status', 'aria-live': 'polite', 'data-ev': 'msg' } });
        var draw = function () {
          state.textContent = ids.length ? t('tr.ev.n', { n: K.fa(ids.length, 0) }) : '';
          view.hidden = !ids.length || !pics.hidden;
          add.hidden = ids.length >= max;
        };
        view.addEventListener('click', function () {
          view.disabled = true;
          Promise.all(ids.map(function (id) { return picture(c.token, id).then(null, function () { return ''; }); })).then(function (urls) {
            K.clear(pics);
            urls.forEach(function (u) { if (u) pics.appendChild(el('img', { class: 'am-thumb', attrs: { src: u, alt: t('tr.ev.alt') } })); });
            pics.hidden = !pics.firstChild;
            if (pics.hidden) K.note(msg, 'err', t('err.generic'));
            view.disabled = false;
            draw();
          });
        });
        add.addEventListener('click', function () { input.click(); });
        input.addEventListener('change', function () {
          var f = input.files && input.files[0];
          input.value = '';
          if (!f) return;
          add.disabled = true;
          K.note(msg, 'info', t('tr.ev.sending'));
          K.js('/store/chat-photo.js').then(function () {
            return window.StoreChatPhoto.prepare(f, { imageMax: limits.imageMax || 1536 * 1024, thumbMax: 16 * 1024 });
          }).then(function (p) { return p.main; }, function () { throw evErr(415, 'unsupported_media'); })
            .then(function (blob) { return upload(c.token, blob); })
            .then(function (r) {
              if (!r.reused && ids.indexOf(String(r.evidenceId)) < 0) { ids.push(String(r.evidenceId)); K.clear(pics); pics.hidden = true; }
              K.note(msg, 'ok', t(r.reused ? 'tr.ev.same' : 'tr.ev.done'));
            }, function (e) { K.note(msg, 'err', e.message); })
            .then(function () { add.disabled = false; draw(); });
        });
        draw();
        li.appendChild(el('div', { class: 'muted small row wrap-row' }, state, view));
        li.appendChild(pics);
        if (open) li.appendChild(el('div', { class: 'row wrap-row' }, add, input, el('small', { class: 'muted', text: t('tr.ev.hint', { n: K.fa(max, 0) }) })));
        li.appendChild(msg);
      }

      /** یک عکسِ پیوست ⇒ data: (CSP ِ ویترین img-src blob: ندارد) */
      function picture(tk, id) {
        return fetch(BASE + '/trust/cases/' + encodeURIComponent(tk) + '/evidence/' + encodeURIComponent(id), { credentials: 'omit', cache: 'no-store' })
          .then(function (r) { if (!r.ok) throw new Error(String(r.status)); return r.blob(); })
          .then(function (b) {
            return new Promise(function (ok, no) { var rd = new FileReader(); rd.onload = function () { ok(String(rd.result)); }; rd.onerror = no; rd.readAsDataURL(b); });
          });
      }

      /** بدنهٔ خامِ عکس با توکنِ همان پرونده — بی بلیت، بی نشست (توکن خودش اجازه است) */
      function upload(tk, blob) {
        return fetch(BASE + '/trust/cases/' + encodeURIComponent(tk) + '/evidence', {
          method: 'POST', body: blob, credentials: 'omit', cache: 'no-store',
          headers: { Accept: 'application/json', 'Accept-Language': K.lang, 'Content-Type': 'application/octet-stream' },
        }).then(function (r) {
          return r.json().catch(function () { return {}; }).then(function (b) {
            if (r.ok) return b;
            throw evErr(r.status, (b && b.error && b.error.code) || '');
          });
        }, function () { throw new Error(t('err.network')); });
      }

      /** فرمِ شکایت (`complaint`) یا گزارشِ کالا (`report`)؛ [id] پیشوندِ شناسه‌ها؛ [pid] کالای برگه */
      function form(kind, id, pid) {
        var p = function (s) { return 'tr-' + id + '-' + s; };
        var cats = kind === 'report' ? REPORT : COMPLAINT;
        var sel = el('select', { attrs: { id: p('cat') } }, el('option', { text: t('tr.pick'), attrs: { value: '' } }));
        cats.forEach(function (c) { sel.appendChild(el('option', { text: t((kind === 'report' ? 'tr.r.' : 'tr.c.') + c), attrs: { value: c } })); });
        var order = kind === 'report' ? null : el('input', { attrs: { id: p('order'), inputmode: 'numeric', dir: 'ltr', maxlength: '16', autocomplete: 'off' } });
        var text = el('textarea', { attrs: { id: p('text'), maxlength: String(MAX_BODY) } });
        var go = el('button', { class: 'btn btn-soft btn-sm', text: t('tr.send'), attrs: { type: 'button', id: p('send') } });
        var msg = el('div', { class: 'note', hidden: true, attrs: { id: p('msg'), role: 'status', 'aria-live': 'polite' } });
        var vbox = null;
        var wrap = el('div', { class: 'stack' },
          el('label', { class: 'field' }, el('span', { text: t('tr.cat') }), sel),
          order ? el('label', { class: 'field' }, el('span', { text: t('tr.order') }), order) : null,
          el('label', { class: 'field' }, el('span', { text: t('tr.text') }), text), go, msg);

        function send() {
          var d = draft(kind, { category: sel.value, text: text.value, orderNo: order ? order.value : '', productId: pid });
          if (d.problem) { K.note(msg, 'err', t('tr.bad.' + d.problem)); return; }
          if (!K.ticket()) { verify(); return; }
          go.disabled = true;
          K.note(msg, '', '');
          api(kind === 'report' ? '/trust/reports' : '/trust/complaints', { method: 'POST', body: d.body }).then(function (r) {
            K.save(SAVE, keep(K.load(SAVE, []), r.case && r.case.token));
            K.note(msg, 'ok', t(r.duplicate ? 'tr.dup' : 'tr.done'));
            sel.value = ''; text.value = ''; if (order) order.value = '';
            track();
          }).catch(function (e) {
            //  بلیتِ کهنه یا لغوشده ⇒ کدِ تازه و همان فرستادن
            if (e.code === 'verification_required') { K.setTicket(null); verify(); return; }
            K.note(msg, 'err', e.message);
          }).then(function () { go.disabled = false; });
        }

        function verify() {
          K.note(msg, 'info', t('tr.verify'));
          if (vbox) { vbox.hidden = false; vbox.querySelector('input').focus(); return; }
          var phone = el('input', { attrs: { id: p('phone'), type: 'tel', inputmode: 'tel', autocomplete: 'tel', dir: 'ltr', maxlength: '20' } });
          var code = el('input', { attrs: { id: p('code'), inputmode: 'numeric', autocomplete: 'one-time-code', dir: 'ltr', maxlength: '8', 'aria-label': t('v.code'), placeholder: t('v.code') } });
          var start = el('button', { class: 'btn btn-soft btn-sm', text: t('v.send'), attrs: { type: 'button', id: p('vsend') } });
          var check = el('button', { class: 'btn btn-sm', text: t('v.check'), attrs: { type: 'button', id: p('vok') } });
          var codeRow = el('div', { class: 'code-row', hidden: true, attrs: { id: p('vrow') } }, code, check);
          vbox = el('div', { class: 'stack', attrs: { id: p('verify') } },
            el('label', { class: 'field' }, el('span', { text: t('phone.l') }), phone), start, codeRow);
          wrap.insertBefore(vbox, msg);
          var num = '';
          start.addEventListener('click', function () {
            start.disabled = true;
            K.js('/kit/order-kit.js').then(function () {
              num = K.normPhone(phone.value);
              if (!num) throw new Error(t('v.need'));
              return api('/verify/start', { method: 'POST', body: { phone: num } });
            }).then(function (r) {
              codeRow.hidden = false;
              K.note(msg, 'info', t('v.sent') + (r.devCode ? ' ' + t('v.dev', { code: r.devCode }) : ''));
              code.focus();
            }).catch(function (e) { K.note(msg, e.code === 'verification_unavailable' ? 'warn' : 'err', e.message); })
              .then(function () { start.disabled = false; });
          });
          check.addEventListener('click', function () {
            check.disabled = true;
            api('/verify/check', { method: 'POST', body: { phone: num, code: digits(code.value) } }).then(function (r) {
              K.setTicket(r);
              code.value = '';
              vbox.hidden = true;
              send();
            }).catch(function (e) { K.note(msg, 'err', e.message); }).then(function () { check.disabled = false; });
          });
          phone.focus();
        }

        go.addEventListener('click', send);
        return wrap;
      }

      return {
        /** برگهٔ کالا: یک بخشِ «گزارشِ این کالا» برای همان کالا (برگهٔ پیشین پاک) */
        seen: function (pr) {
          var old = $('tr-ps');
          if (old) old.parentNode.removeChild(old);
          var anchor = $('ps-share');
          if (!pr || !PID.test(String(pr.id || '')) || !anchor) return;
          anchor.parentNode.insertBefore(el('details', { class: 'more-box', attrs: { id: 'tr-ps' } },
            el('summary', { text: t('tr.product') }), form('report', 'r', pr.id)), anchor.nextSibling);
        },
      };
    }).catch(function () { return null; });
  };
})();
