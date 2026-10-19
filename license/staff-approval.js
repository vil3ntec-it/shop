/* ==========================================================
   فروشگاه من | PINِ شخصی و «درخواستِ تأیید» — STF-02، STF-04 (بخشِ ۱۰.۱/۱۰.۲)
   ----------------------------------------------------------
   همتای وبِ `ApproverVault` و `ApprovalDialog`ِ برنامه‌ها:

   • هر کاربر روی همین مرورگر یک PINِ شش‌رقمی دارد؛ فقط نمک و اثرِ
     PBKDF2-SHA256ِ آن (۲۱۰ هزار دور) در localStorage می‌ماند و هرگز همگام
     نمی‌شود. پس از ۵ خطا قفل؛ فقط PINِ تازه (پس از ورودِ آنلاین) بازش می‌کند.
   • صاحب دکان و مدیر جفت‌کلیدِ ECDSA P-256 هم دارند؛ کلیدِ خصوصی با AES-GCM و
     کلیدی که از همان PIN ساخته می‌شود بسته است («PIN فقط کلید را باز می‌کند»)
     و کلیدِ عمومی یک بار در سرور ثبت می‌شود (`POST /api/v1/shop/approver-keys`).
   • «برگهٔ امضاشده» همان `TohidApproval.payload` است؛ WebCrypto امضای خامِ
     ۶۴ بایتی می‌دهد و سرور هر دو شکل (خام و DER) را می‌خواند.

   سدِ واقعی سرور است: برگه را با کلیدِ ثبت‌شده، اختیارِ خودِ تأییدکننده و
   nonceِ یک‌بارمصرف می‌سنجد (`server/src/lib/staff-approval.js`).
   ========================================================== */
(function () {
  'use strict';

  var STORE_KEY = 'tohid-staff-pins-v1';
  var ITER = 210000;
  var MAX_FAILURES = 5;
  var LEN = 6;
  var APPROVER_ROLES = ['owner', 'manager'];
  var enc = new TextEncoder();

  function A() { return window.TohidApproval; }
  function subtle() { return window.crypto && window.crypto.subtle; }

  // ---------- کمک‌ها ----------
  function bytes(n) { var b = new Uint8Array(n); window.crypto.getRandomValues(b); return b; }
  function b64(buf) {
    var u = buf instanceof Uint8Array ? buf : new Uint8Array(buf);
    var s = '';
    for (var i = 0; i < u.length; i++) s += String.fromCharCode(u[i]);
    return btoa(s);
  }
  function unb64(s) {
    var bin = atob(String(s || ''));
    var u = new Uint8Array(bin.length);
    for (var i = 0; i < bin.length; i++) u[i] = bin.charCodeAt(i);
    return u;
  }
  function b64url(buf) { return b64(buf).replace(/\+/g, '-').replace(/\//g, '_').replace(/=+$/, ''); }
  function hex(buf) {
    var u = new Uint8Array(buf);
    var s = '';
    for (var i = 0; i < u.length; i++) s += (u[i] < 16 ? '0' : '') + u[i].toString(16);
    return s;
  }
  function concat(a, b) {
    var out = new Uint8Array(a.length + b.byteLength);
    out.set(a, 0);
    out.set(new Uint8Array(b), a.length);
    return out;
  }
  /** ارقامِ فارسی/عربی ⇐ لاتین — همان `PinGuard.normalize` */
  function normalize(pin) {
    return String(pin || '')
      .replace(/[۰-۹]/g, function (c) { return String(c.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (c) { return String(c.charCodeAt(0) - 0x0660); });
  }
  /** همان `PinGuard.newPinProblem`: شش رقم، نه همه یکی و نه پشتِ سرِ هم */
  function newPinProblem(pin) {
    var p = normalize(pin);
    if (!/^\d{6}$/.test(p)) return 'length';
    if (/^(\d)\1+$/.test(p)) return 'weak';
    var up = true, down = true;
    for (var i = 1; i < p.length; i++) {
      if (p.charCodeAt(i) - p.charCodeAt(i - 1) !== 1) up = false;
      if (p.charCodeAt(i - 1) - p.charCodeAt(i) !== 1) down = false;
    }
    return up || down ? 'weak' : null;
  }
  /** مقایسهٔ ثابت‌زمان */
  function same(a, b) {
    if (a.length !== b.length) return false;
    var d = 0;
    for (var i = 0; i < a.length; i++) d |= a.charCodeAt(i) ^ b.charCodeAt(i);
    return d === 0;
  }

  // ---------- انبار (فقط همین مرورگر) ----------
  function readAll() {
    try { var m = JSON.parse(localStorage.getItem(STORE_KEY) || '{}'); return m && typeof m === 'object' ? m : {}; }
    catch (e) { return {}; }
  }
  function writeAll(m) { try { localStorage.setItem(STORE_KEY, JSON.stringify(m)); } catch (e) { /* پر یا بسته */ } }
  function get(userId) { var r = readAll()[userId]; return r && r.userId ? r : null; }
  function put(rec) { var m = readAll(); m[rec.userId] = rec; writeAll(m); return rec; }
  function hasKey(r) { return !!(r && r.keyId && r.sealed); }
  /** تأییدکننده‌های آماده روی این مرورگر — جز خودِ درخواست‌کننده (سرور خودتأیید را رد می‌کند) */
  function approvers(exceptUserId) {
    var m = readAll();
    return Object.keys(m).map(function (k) { return m[k]; }).filter(function (r) {
      return hasKey(r) && r.registered && APPROVER_ROLES.indexOf(r.role) >= 0 && r.userId !== exceptUserId;
    });
  }

  // ---------- رمزنگاری ----------
  function pbkdf2(pin, salt, iter) {
    return subtle().importKey('raw', enc.encode(normalize(pin)), 'PBKDF2', false, ['deriveBits']).then(function (k) {
      return subtle().deriveBits({ name: 'PBKDF2', hash: 'SHA-256', salt: salt, iterations: iter }, k, 256);
    });
  }
  function aesKey(pin, salt, iter) {
    return pbkdf2(pin, salt, iter).then(function (bits) {
      return subtle().importKey('raw', bits, 'AES-GCM', false, ['encrypt', 'decrypt']);
    });
  }

  /** ردیفِ تازه — PIN باید `newPinProblem` نداشته باشد */
  async function create(userId, name, role, pin, iter) {
    if (newPinProblem(pin)) throw new Error('weak_pin');
    var n = iter || ITER;
    var salt = bytes(16);
    var rec = {
      v: 1, userId: String(userId), name: String(name || ''), role: String(role || ''),
      salt: b64(salt), iter: n, check: hex(await pbkdf2(pin, salt, n)),
      failures: 0, locked: false, keyId: '', spki: '', keySalt: '', sealed: '', registered: false,
    };
    if (APPROVER_ROLES.indexOf(rec.role) < 0) return rec;
    var kp = await subtle().generateKey({ name: 'ECDSA', namedCurve: 'P-256' }, true, ['sign', 'verify']);
    var spki = await subtle().exportKey('spki', kp.publicKey);
    var pkcs8 = await subtle().exportKey('pkcs8', kp.privateKey);
    var keySalt = bytes(16);
    var iv = bytes(12);
    var ct = await subtle().encrypt({ name: 'AES-GCM', iv: iv }, await aesKey(pin, keySalt, n), pkcs8);
    rec.keyId = 'ak_' + hex(await subtle().digest('SHA-256', spki)).slice(0, 24);
    rec.spki = b64(spki);
    rec.keySalt = b64(keySalt);
    rec.sealed = b64(concat(iv, ct));
    return rec;
  }

  /** یک تلاش؛ قفل‌شده ⇐ همیشه ناموفق */
  async function verify(rec, pin) {
    if (rec.locked) return { rec: rec, ok: false };
    var p = normalize(pin);
    var ok = /^\d{4,6}$/.test(p) && same(hex(await pbkdf2(p, unb64(rec.salt), rec.iter)), rec.check);
    var failures = ok ? 0 : (rec.failures || 0) + 1;
    return { rec: Object.assign({}, rec, { failures: failures, locked: !ok && failures >= MAX_FAILURES }), ok: ok };
  }

  function draft(rec, shop, need, reason, now) {
    var t = now || Date.now();
    return {
      v: 1, shop: String(shop || ''), doc: need.doc, perm: need.perm,
      pct_bp: Number.isInteger(need.pct_bp) ? need.pct_bp : null,
      amount: Number.isInteger(need.amount) ? need.amount : null,
      by: rec.userId, key: rec.keyId, iat: t, exp: t + 10 * 60 * 1000,
      nonce: b64url(bytes(18)),
      reason: String(reason || '').replace(/[\r\n]+/g, ' ').trim().slice(0, 200),
    };
  }

  /** PIN ⇐ برگهٔ امضاشده (یا null). ردیفِ برگشتی (شمارِ خطا) ذخیره می‌شود. */
  async function approve(rec, pin, d) {
    if (!hasKey(rec) || d.by !== rec.userId || d.key !== rec.keyId) return { rec: rec, slip: null };
    var v = await verify(rec, pin);
    put(v.rec);
    if (!v.ok) return { rec: v.rec, slip: null };
    var all = unb64(rec.sealed);
    var pkcs8;
    try {
      pkcs8 = await subtle().decrypt({ name: 'AES-GCM', iv: all.slice(0, 12) }, await aesKey(pin, unb64(rec.keySalt), rec.iter), all.slice(12));
    } catch (e) { return { rec: v.rec, slip: null }; }
    var key = await subtle().importKey('pkcs8', pkcs8, { name: 'ECDSA', namedCurve: 'P-256' }, false, ['sign']);
    var sig = await subtle().sign({ name: 'ECDSA', hash: 'SHA-256' }, key, enc.encode(A().payload(d)));
    var slip = Object.assign({}, d, { sig: b64(sig) });
    return { rec: v.rec, slip: A().check(slip) ? null : slip };
  }

  /** ثبتِ کلیدِ عمومی در سرور (یک بار، آنلاین) */
  async function register(rec, label) {
    var sh = window.TohidShop;
    if (!sh || !sh.apiAuth) throw new Error('اتصال به سرور در دسترس نیست');
    var r = await sh.apiAuth('/api/v1/shop/approver-keys', {
      method: 'POST',
      body: { public_key: rec.spki, label: String(label || '').slice(0, 60), device_id: sh.deviceUid ? String(sh.deviceUid()).slice(0, 64) : '' },
    });
    var id = r && r.key && r.key.id;
    return put(Object.assign({}, rec, { registered: id === rec.keyId }));
  }

  // ---------- رابط ----------
  var CSS = [
    '.sa-scrim{position:fixed;inset:0;z-index:1200;display:flex;align-items:flex-end;justify-content:center;background:rgba(15,12,30,.45);}',
    '@media(min-width:640px){.sa-scrim{align-items:center;padding:20px;}}',
    '.sa-box{background:var(--surface-solid,#fff);color:var(--text,#191627);width:100%;max-width:420px;border-radius:var(--radius-lg,24px) var(--radius-lg,24px) 0 0;padding:18px 18px 22px;box-shadow:0 10px 40px rgba(0,0,0,.18);max-height:92vh;overflow:auto;}',
    '@media(min-width:640px){.sa-box{border-radius:var(--radius-lg,24px);}}',
    '.sa-box h3{margin:0 0 8px;font-size:17px;}',
    '.sa-summary{margin:8px 0 12px;font-size:14px;line-height:1.7;}',
    '.sa-pill{display:inline-flex;align-items:center;gap:6px;padding:3px 10px;border-radius:999px;font-size:12px;font-weight:700;}',
    '.sa-pill::before{content:"";width:6px;height:6px;border-radius:50%;background:currentColor;}',
    '.sa-pill.warn{color:var(--warning,#8f5a00);background:var(--warning-tint,rgba(184,119,8,.12));}',
    '.sa-pill.ok{color:var(--success,#08745e);background:var(--success-tint,rgba(15,169,138,.12));}',
    '.sa-pill.bad{color:var(--danger,#bf2c2c);background:var(--danger-tint,rgba(217,59,59,.12));}',
    '.sa-pill.neutral{color:var(--muted,#635c86);background:rgba(99,92,134,.12);}',
    '.sa-seg{display:flex;gap:4px;padding:4px;border-radius:14px;background:rgba(99,92,134,.08);margin:6px 0 10px;}',
    '.sa-seg button{flex:1;min-height:40px;border:0;border-radius:10px;background:transparent;color:var(--muted,#635c86);font:inherit;cursor:pointer;}',
    '.sa-seg button[aria-pressed="true"]{background:var(--primary-tint,rgba(124,92,255,.12));color:var(--primary,#6f50f0);font-weight:700;}',
    '.sa-pad{display:flex;flex-direction:column;align-items:center;margin-top:12px;}',
    '.sa-pad-title{font-weight:700;}',
    '.sa-pad-hint{font-size:12.5px;color:var(--muted,#635c86);margin-top:4px;text-align:center;min-height:1.6em;}',
    '.sa-pad-hint.err{color:var(--danger,#bf2c2c);}',
    '.sa-dots{display:flex;gap:12px;margin:14px 0 16px;direction:ltr;}',
    '.sa-dots i{width:13px;height:13px;border-radius:50%;background:rgba(99,92,134,.22);}',
    '.sa-dots i.on{background:var(--primary,#6f50f0);}',
    '.sa-keys{display:grid;grid-template-columns:repeat(3,64px);gap:10px;direction:ltr;}',
    '.sa-keys button{height:56px;border-radius:18px;border:1px solid var(--border,rgba(99,92,134,.2));background:var(--surface,#fff);color:var(--text,#191627);font:inherit;font-size:20px;cursor:pointer;}',
    '.sa-keys button:focus-visible,.sa-seg button:focus-visible{outline:2px solid var(--primary,#6f50f0);outline-offset:2px;}',
    '.sa-keys button:disabled,.sa-keys button[aria-disabled="true"]{opacity:.45;cursor:default;}',
    '.sa-actions{display:flex;justify-content:flex-end;gap:8px;margin-top:14px;}',
    '.co-approval{display:flex;align-items:center;gap:8px;flex-wrap:wrap;margin-top:8px;}',
    '.co-approval .sa-note{font-size:12.5px;color:var(--muted,#635c86);flex:1;min-width:140px;}',
  ].join('\n');
  function ensureCss() {
    if (document.getElementById('sa-css')) return;
    var s = document.createElement('style');
    s.id = 'sa-css';
    s.textContent = CSS;
    document.head.appendChild(s);
  }
  function el(tag, attrs, text) {
    var e = document.createElement(tag);
    if (attrs) Object.keys(attrs).forEach(function (k) { e.setAttribute(k, attrs[k]); });
    if (text != null) e.textContent = text;
    return e;
  }
  var FA = '۰۱۲۳۴۵۶۷۸۹';
  function fa(n) { return String(n).replace(/\d/g, function (d) { return FA[+d]; }); }

  /** پنجره با Esc برای بستن؛ تمرکز داخلِ پنجره */
  function modal(titleText, onClose) {
    ensureCss();
    var scrim = el('div', { class: 'sa-scrim' });
    var id = 'sa-t-' + Math.random().toString(36).slice(2);
    var box = el('div', { class: 'sa-box', role: 'dialog', 'aria-modal': 'true', 'aria-labelledby': id });
    box.appendChild(el('h3', { id: id }, titleText));
    scrim.appendChild(box);
    var prev = document.activeElement;
    var handlers = [];
    function close() {
      document.removeEventListener('keydown', onKey, true);
      scrim.remove();
      if (prev && prev.focus) { try { prev.focus(); } catch (e) { /* رفته */ } }
    }
    //  کلیدها تا پنجره باز است مالِ همین پنجره‌اند — حتی وقتی تمرکز روی دکمه‌ای بود که خاموش شد
    function onKey(e) {
      if (e.key === 'Escape') { e.preventDefault(); e.stopPropagation(); onClose(); return; }
      for (var i = 0; i < handlers.length; i++) handlers[i](e);
    }
    document.addEventListener('keydown', onKey, true);
    scrim.addEventListener('click', function (e) { if (e.target === scrim) onClose(); });
    document.body.appendChild(scrim);
    return { scrim: scrim, box: box, close: close, onKey: function (h) { handlers.push(h); } };
  }

  /**
   *  صفحهٔ PIN: نقطه‌ها، صفحه‌کلید (و ارقامِ صفحه‌کلیدِ سخت‌افزاری، فارسی هم) و Backspace.
   *  با رقمِ ششم `onComplete(pin)` و خانه‌ها خالی می‌شوند.
   */
  function pinPad(host, opts) {
    var pin = '';
    var busy = false;
    var locked = false;
    var root = el('div', { class: 'sa-pad' });
    var title = el('div', { class: 'sa-pad-title' }, opts.title || 'PIN');
    var hint = el('div', { class: 'sa-pad-hint', role: 'status', 'aria-live': 'polite' }, opts.hint || '');
    var dots = el('div', { class: 'sa-dots', 'aria-hidden': 'true' });
    for (var i = 0; i < LEN; i++) dots.appendChild(el('i'));
    var keys = el('div', { class: 'sa-keys' });
    function paint() {
      var list = dots.children;
      for (var j = 0; j < list.length; j++) list[j].classList.toggle('on', j < pin.length);
      dots.setAttribute('aria-label', fa(pin.length) + ' رقم از ' + fa(LEN));
      //  «مشغول» دکمه را خاموش نمی‌کند (تمرکز از دست نرود)؛ فقط «قفل» می‌کند
      Array.prototype.forEach.call(keys.querySelectorAll('button'), function (b) {
        b.disabled = locked;
        b.setAttribute('aria-disabled', String(busy || locked));
      });
    }
    function press(d) {
      if (busy || locked || pin.length >= LEN) return;
      pin += d;
      paint();
      if (pin.length === LEN) { var done = pin; pin = ''; paint(); opts.onComplete(done); }
    }
    function back() { if (!busy && !locked && pin) { pin = pin.slice(0, -1); paint(); } }
    ['1', '2', '3', '4', '5', '6', '7', '8', '9', 'del', '0', 'back'].forEach(function (k) {
      if (k === 'del') { keys.appendChild(el('span')); return; }
      var b = el('button', { type: 'button', 'data-pin-key': k, 'aria-label': k === 'back' ? 'پاک کردنِ یک رقم' : fa(k) }, k === 'back' ? '⌫' : fa(k));
      b.addEventListener('click', function () { if (k === 'back') back(); else press(k); });
      keys.appendChild(b);
    });
    function onKey(e) {
      //  رقمی که در کادرِ «دلیل» تایپ می‌شود مالِ همان کادر است
      if (e.target && /^(INPUT|TEXTAREA|SELECT)$/.test(e.target.tagName)) return;
      if (e.key === 'Backspace') { e.preventDefault(); back(); return; }
      var d = normalize(e.key);
      if (/^\d$/.test(d)) { e.preventDefault(); press(d); }
    }
    if (opts.modal) opts.modal.onKey(onKey);
    else root.addEventListener('keydown', onKey);
    root.appendChild(title);
    root.appendChild(hint);
    root.appendChild(dots);
    root.appendChild(keys);
    host.appendChild(root);
    paint();
    return {
      el: root,
      setHint: function (text, isError) { hint.textContent = text || ''; hint.classList.toggle('err', !!isError); },
      setTitle: function (text) { title.textContent = text; },
      setBusy: function (v) { busy = !!v; paint(); },
      setLocked: function (v) { locked = !!v; paint(); },
      focus: function () { var b = keys.querySelector('button'); if (b) b.focus(); },
    };
  }

  /**
   *  «درخواستِ تأیید» (S07/S08): تأییدکننده، دلیل، PINِ او ⇐ برگه.
   *  @returns Promise<برگه|null> — null یعنی انصراف (هیچ چیزی ثبت نمی‌شود)
   */
  function request(o) {
    return new Promise(function (resolve) {
      var done = false;
      function finish(v) { if (done) return; done = true; m.close(); resolve(v); }
      var m = modal(o.title || 'درخواستِ تأیید', function () { finish(null); });
      var list = approvers(o.requesterId || '');
      m.box.appendChild(el('span', { class: 'sa-pill warn' }, 'نیازمند تأیید'));
      m.box.appendChild(el('p', { class: 'sa-summary' }, o.summary || ''));
      var actions = el('div', { class: 'sa-actions' });
      var cancel = el('button', { type: 'button', class: 'btn btn-secondary', 'data-sa-cancel': '' }, 'انصراف');
      cancel.addEventListener('click', function () { finish(null); });
      if (!o.shopId || !list.length) {
        m.box.appendChild(el('p', { class: 'sa-pad-hint' }, !o.shopId
          ? 'این مرورگر هنوز به دکان وصل نشده است؛ یک بار آنلاین وارد شوید.'
          : 'روی این مرورگر هیچ مدیر یا صاحبِ دکانی PIN و کلیدِ تأییدِ ثبت‌شده ندارد. از «کارمندان ← PIN و کلیدِ تأیید» یک بار آنلاین بسازید.'));
        cancel.textContent = 'بستن';
        actions.appendChild(cancel);
        m.box.appendChild(actions);
        cancel.focus();
        return;
      }
      var who = list[0].userId;
      if (list.length > 1) {
        m.box.appendChild(el('div', { class: 'sa-pad-hint' }, 'تأییدکننده'));
        var seg = el('div', { class: 'sa-seg', role: 'group', 'aria-label': 'تأییدکننده' });
        list.forEach(function (r) {
          var b = el('button', { type: 'button', 'aria-pressed': String(r.userId === who), 'data-approver': r.userId },
            r.name || (r.role === 'owner' ? 'صاحب دکان' : 'مدیر'));
          b.addEventListener('click', function () {
            who = r.userId;
            Array.prototype.forEach.call(seg.children, function (x) { x.setAttribute('aria-pressed', String(x === b)); });
            refresh();
          });
          seg.appendChild(b);
        });
        m.box.appendChild(seg);
      }
      var group = el('div', { class: 'form-group' });
      var rid = 'sa-reason-' + Math.random().toString(36).slice(2);
      group.appendChild(el('label', { for: rid }, 'دلیل (در سابقه می‌ماند)'));
      var reason = el('input', { id: rid, class: 'form-input', type: 'text', maxlength: '200', autocomplete: 'off', 'data-sa-reason': '' });
      group.appendChild(reason);
      m.box.appendChild(group);
      var pad = pinPad(m.box, {
        title: 'PIN', hint: '', modal: m,
        onComplete: async function (pin) {
          var r = get(who);
          if (!r) return;
          if (!reason.value.trim()) { pad.setHint('اول دلیل را بنویسید', true); reason.focus(); return; }
          pad.setBusy(true);
          pad.setHint('در حالِ امضا…');
          var out;
          try { out = await approve(r, pin, draft(r, o.shopId, o.need, reason.value, Date.now())); }
          catch (e) { out = { rec: r, slip: null }; }
          pad.setBusy(false);
          if (out.slip) { finish(out.slip); return; }
          refresh(out.rec);
        },
      });
      function refresh(rec) {
        var r = rec || get(who);
        var name = (r && r.name) || 'تأییدکننده';
        pad.setTitle('PINِ ' + name);
        if (r && r.locked) {
          pad.setLocked(true);
          pad.setHint('PINِ ' + name + ' پس از ' + fa(MAX_FAILURES) + ' خطا قفل شد؛ با ورودِ آنلاین PINِ تازه بگذارد.', true);
        } else if (rec) {
          pad.setLocked(false);
          pad.setHint('PIN درست نیست — ' + fa(MAX_FAILURES - (r.failures || 0)) + ' تلاش مانده', true);
        } else {
          pad.setLocked(false);
          pad.setHint('دلیل را بنویسید و PINِ شش‌رقمیِ ' + name + ' را بزنید');
        }
      }
      refresh();
      actions.appendChild(cancel);
      m.box.appendChild(actions);
      reason.focus();
    });
  }

  /** PINِ تازه، دو بار. @returns Promise<string|null> */
  function askNewPin() {
    return new Promise(function (resolve) {
      var done = false;
      function finish(v) { if (done) return; done = true; m.close(); resolve(v); }
      var m = modal('PINِ تازه', function () { finish(null); });
      var first = null;
      var pad = pinPad(m.box, {
        title: 'PINِ شش‌رقمی', hint: 'نه همه یکی و نه پشتِ سرِ هم (۱۱۱۱۱۱ / ۱۲۳۴۵۶)', modal: m,
        onComplete: function (pin) {
          if (first === null) {
            if (newPinProblem(pin)) { pad.setHint('این PIN ساده است؛ شش رقمِ دیگری بزنید', true); return; }
            first = pin;
            pad.setTitle('دوباره همان PIN');
            pad.setHint('');
            return;
          }
          if (first !== pin) { first = null; pad.setTitle('PINِ شش‌رقمی'); pad.setHint('دو PIN یکی نبودند؛ از نو', true); return; }
          finish(pin);
        },
      });
      var actions = el('div', { class: 'sa-actions' });
      var cancel = el('button', { type: 'button', class: 'btn btn-secondary' }, 'انصراف');
      cancel.addEventListener('click', function () { finish(null); });
      actions.appendChild(cancel);
      m.box.appendChild(actions);
      pad.focus();
    });
  }

  /**
   *  PIN و کلیدِ تأییدِ کاربرِ همین حساب: ساختن، ثبت در سرور، و باطل کردنِ
   *  کلیدِ قبلیِ همین مرورگر. @returns Promise<ردیف|null>
   */
  async function setup(o) {
    var pin = await askNewPin();
    if (!pin) return null;
    var old = get(o.userId);
    var rec = put(await create(o.userId, o.name, o.role, pin, o.iter));
    if (!hasKey(rec)) return rec;
    var sh = window.TohidShop;
    if (old && old.registered && old.keyId && old.keyId !== rec.keyId && sh && sh.apiAuth) {
      try { await sh.apiAuth('/api/v1/shop/approver-keys/' + encodeURIComponent(old.keyId), { method: 'DELETE' }); } catch (e) { /* بعداً */ }
    }
    try { return await register(rec, o.label); } catch (e) { return rec; }
  }

  /** پنلِ «PIN و کلیدِ تأیید» در صفحهٔ کارمندان (فقط صاحب و مدیر) */
  function renderSetup(host) {
    if (!host) return;
    var sh = window.TohidShop;
    var acct = sh && sh.account;
    var shop = sh && sh.state && sh.state.shop;
    var panel = host.closest ? host.closest('[data-sa-panel]') : null;
    var role = shop && shop.role;
    if (!acct || !acct.userId || APPROVER_ROLES.indexOf(role) < 0) { if (panel) panel.hidden = true; return; }
    if (panel) panel.hidden = false;
    ensureCss();
    host.innerHTML = '';
    var r = get(acct.userId);
    var row = el('div', { class: 'co-approval' });
    var pill;
    if (!r) pill = el('span', { class: 'sa-pill neutral' }, 'PIN ندارد');
    else if (r.locked) pill = el('span', { class: 'sa-pill bad' }, 'قفل پس از ' + fa(MAX_FAILURES) + ' خطا');
    else if (r.registered) pill = el('span', { class: 'sa-pill ok' }, 'آمادهٔ تأیید');
    else pill = el('span', { class: 'sa-pill warn' }, 'کلید هنوز ثبت نشده');
    row.appendChild(pill);
    row.appendChild(el('span', { class: 'sa-note' }, ''));
    var label = (acct.label || 'مرورگر') + ' · وب';
    if (r && !r.registered && !r.locked) {
      var reg = el('button', { type: 'button', class: 'btn btn-secondary btn-sm' }, 'ثبتِ کلید');
      reg.addEventListener('click', async function () {
        reg.disabled = true;
        try { await register(r, label); } catch (e) { if (window.showToast) window.showToast((e && e.message) || 'نشد', 'error'); }
        renderSetup(host);
      });
      row.appendChild(reg);
    }
    var btn = el('button', { type: 'button', class: 'btn btn-secondary btn-sm', 'data-sa-setup': '' }, r ? 'PINِ تازه' : 'گذاشتنِ PIN');
    btn.addEventListener('click', async function () {
      await setup({ userId: acct.userId, name: acct.label || '', role: role, label: label });
      renderSetup(host);
    });
    row.appendChild(btn);
    host.appendChild(row);
  }

  window.TohidStaffApproval = {
    STORE_KEY: STORE_KEY, ITER: ITER, MAX_FAILURES: MAX_FAILURES, APPROVER_ROLES: APPROVER_ROLES,
    normalize: normalize, newPinProblem: newPinProblem,
    get: get, put: put, approvers: approvers,
    create: create, verify: verify, draft: draft, approve: approve, register: register,
    request: request, setup: setup, renderSetup: renderSetup, pinPad: pinPad, ensureCss: ensureCss,
  };
})();
