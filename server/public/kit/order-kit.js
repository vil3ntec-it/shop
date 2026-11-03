/*
 * کیتِ وبِ خریدار — یاورهای ثبت و پیگیریِ سفارش، دیربار: شمارهٔ موبایل، کلیدِ یکتایی، پرسشِ تأیید (dialog)،
 * دلیلِ وضعیت و پله‌های پیشرفتِ سفارش. فقط همراهِ checkout.js و order.js از store.js بار می‌شود (`K.js`)، نه در
 * بارِ نخستِ ویترین و بازارچه (بودجهٔ WEB-15، §۳.۱۰). مانندِ kit.js: ES5، بی innerHTML و هر متن با textContent.
 */
(function () {
  'use strict';
  var K = window.K;

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

  // ── پرسشِ تأیید (dialog، نه confirm ِ مرورگر) ──
  K.confirm = function (text, okText) {
    return new Promise(function (resolve) {
      var d = K.el('dialog', { class: 'confirm', attrs: { 'aria-label': text } });
      var no = K.el('button', { class: 'btn btn-ghost btn-sm', text: K.t('no'), attrs: { type: 'button' } });
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
