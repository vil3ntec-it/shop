/*
 * کیتِ وبِ خریدار — یاورهای ثبت و پیگیریِ سفارش، دیربار: شمارهٔ موبایل، کلیدِ یکتایی، پرسشِ تأیید (dialog)،
 * دلیلِ وضعیت و پله‌های پیشرفتِ سفارش. فقط همراهِ checkout.js و order.js از store.js بار می‌شود (`K.js`)، نه در
 * بارِ نخستِ ویترین و بازارچه (بودجهٔ WEB-15، §۳.۱۰). مانندِ kit.js: ES5، بی innerHTML و هر متن با textContent.
 */
(function () {
  'use strict';
  var K = window.K;

  /**
   * WEB-02/CHK-11 (ممیزیِ ویترین ۱۴۰۵/۰۸/۱۷، مشکلِ ۶): نشانی و «مسیر روی نقشه» ِ دکان (`store.place`، مهاجرتِ ۲۱۵؛ پیوند را
   * سرور می‌سازد) برای سربرگ، «دریافت از دکان» و پیگیریِ سفارشِ دریافتی. نبود ⇒ null. فقط پیوندِ https.
   */
  K.ICONS.pin = K.ICONS.pin || 'M12 21s-7-6.2-7-12a7 7 0 0 1 14 0c0 5.8-7 12-7 12z M12 11.5a2.5 2.5 0 1 0 0-5 2.5 2.5 0 0 0 0 5z';
  K.place = function (s) {
    var p = s && s.place;
    if (!p || (!p.text && !p.map)) return null;
    return K.el('p', { class: 'place' }, K.icon('pin'), p.text ? K.el('span', { text: p.text }) : null,
      /^https:\/\//.test(p.map || '') ? K.el('a', { text: K.t('sh.map'), attrs: { href: p.map, target: '_blank', rel: 'noopener' } }) : null);
  };

  K.normPhone = function (v) {
    var s = String(v || '').replace(/[۰-۹]/g, function (d) { return String(d.charCodeAt(0) - 0x06F0); })
      .replace(/[٠-٩]/g, function (d) { return String(d.charCodeAt(0) - 0x0660); }).replace(/[\s\-().]/g, '').replace(/^00/, '+');
    if (!s) return '';
    if (s.charAt(0) === '0') s = '+93' + s.slice(1);
    else if (s.charAt(0) !== '+') s = '+93' + s;
    return s;
  };

  /** کلیدِ یکتایی تا وقتی بدنه همان است می‌ماند (زدنِ دوباره پس از قطعِ شبکه). */
  K.keyFor = function (scope, body) {
    var json = JSON.stringify(body);
    var name = 'key:' + scope;
    var p = K.loadS(name);
    if (p && p.json === json) return p.key;
    var rnd = window.crypto && crypto.randomUUID ? crypto.randomUUID()
      : Date.now().toString(36) + Math.random().toString(36).slice(2) + Math.random().toString(36).slice(2);
    var key = 'web-' + rnd;
    K.saveS(name, { json: json, key: key });
    return key;
  };
  K.dropKey = function (scope) { K.saveS('key:' + scope, null); };

  /**
   * CHK-14/24: «با کدام اسکناس…» ِ ثبت و پیگیری — همان قاعدهٔ سرور (`delivery-prefs.checkTender`): بیش از جمع، افغانیِ
   * کامل، پول خرد کمتر از یک اسکناسِ ۱۰۰۰ (بردارِ `core/test-vectors/cash-tender-v1.json`). [cur] (پس از ثبت) اسکناسِ کنونی:
   * هنوز درست و بیرون از فهرست ⇒ افزوده (`PrefsRules.tenders`). جمعِ صفر یا منفی ⇒ هیچ.
   */
  var NOTE_MINOR = 100000;
  K.tenders = function (total, cur) {
    var out = [];
    if (!(total > 0)) return out;
    [10000, 50000, 100000].forEach(function (u) {
      var x = Math.ceil(total / u) * u;
      if (x === total) x += u;
      if (x - total < NOTE_MINOR && out.indexOf(x) < 0) out.push(x);
    });
    if (cur > 0 && cur % 100 === 0 && cur >= total && cur - total < NOTE_MINOR && out.indexOf(cur) < 0) out.push(cur);
    return out.sort(function (a, b) { return a - b; });
  };

  // ── پرسشِ تأیید (dialog، نه confirm ِ مرورگر) ──
  //  [noText]: برچسبِ دکمهٔ «نه» (پیش‌فرض «نه»)؛ «نه» همیشه گزینهٔ بی‌خطر است و فوکوس می‌گیرد (Esc هم همان)
  K.confirm = function (text, okText, noText) {
    return new Promise(function (resolve) {
      var d = K.el('dialog', { class: 'confirm', attrs: { 'aria-label': text } });
      var no = K.el('button', { class: 'btn btn-ghost btn-sm', text: noText || K.t('no'), attrs: { type: 'button' } });
      var yes = K.el('button', { class: 'btn btn-danger btn-sm', text: okText || K.t('yes'), attrs: { type: 'button' } });
      d.append(K.el('p', { text: text }), K.el('div', { class: 'row' }, no, yes));
      document.body.appendChild(d);
      var done = function (v) { d.close(); d.remove(); resolve(v); };
      no.addEventListener('click', function () { done(false); });
      yes.addEventListener('click', function () { done(true); });
      d.addEventListener('cancel', function (e) { e.preventDefault(); done(false); });
      d.showModal();
      no.focus();
    });
  };

  // ── وضعیتِ سفارش برای خریدار (نام و رنگِ وضعیت، K.order و K.orderKind، در kit.js) ──
  K.reason = function (r) { return r && K.has('reason.' + r) ? K.t('reason.' + r) : ''; };
  /** پله‌های پیشرفتِ سفارش، هر کدام done | now | todo | stop. */
  K.orderSteps = function (o) {
    var pickup = o.fulfillment === 'pickup';
    var steps = [
      { label: 'step.placed', keys: ['new'] },
      { label: 'step.accepted', keys: ['accepted'] },
      { label: 'step.prep', keys: ['picking', 'packed'] },
      pickup ? { label: 'step.ready', keys: ['ready_for_pickup'] } : { label: 'step.way', keys: ['out_for_delivery', 'delivery_failed', 'retry_scheduled'] },
      pickup ? { label: 'step.picked', keys: ['picked_up', 'return_requested', 'refunded_partial', 'refunded_full'] }
        : { label: 'step.delivered', keys: ['delivered', 'return_requested', 'refunded_partial', 'refunded_full'] },
    ];
    var at = {};
    (o.timeline || []).forEach(function (e) { at[e.status] = e.at; });
    //  ردشده/لغوشده/برگشته = همان «err» ِ K.orderKind (یک فهرست، در kit.js)
    var stopped = K.orderKind(o.status) === 'err';
    var idx = 0;
    steps.forEach(function (s, i) { s.keys.forEach(function (k) { if (at[k] || o.status === k) idx = Math.max(idx, i); }); });
    var out = steps.map(function (s, i) {
      var t = null; s.keys.forEach(function (k) { if (at[k]) t = at[k]; });
      var state = i < idx ? 'done' : (i === idx ? (stopped ? 'done' : 'now') : 'todo');
      if (i === idx && ['delivered', 'picked_up'].indexOf(o.status) >= 0) state = 'done';
      return { label: K.t(s.label), at: t, state: state };
    });
    //  سفارشِ ردشده/لغوشده: پله‌های بعدی نیست، فقط همان پایان
    return stopped ? out.slice(0, idx + 1).concat([{ label: K.order(o.status), at: at[o.status] || o.updatedAt, state: 'stop' }]) : out;
  };
})();
