/**
 * افزونهٔ بخشِ «سفارش‌ها»ی وب — نشانگرِ «باز / بسته» با هشدارِ قطعِ اتصال (ORD-03) و
 * «عقب انداختنِ» سفارش (ORD-07).
 *
 * ⛔ پیش‌فرض خاموش: `license/orders-ui.js` این فایل را فقط وقتی بار می‌کند که `GET /orders/config`
 * بگوید `presence` (پرچمِ `shop.orders.presence`) یا `delay` (پرچمِ `shop.orders.delay`). هر بخش
 * جدا هم همان را دوباره می‌سنجد؛ پرچمِ خاموش ⇒ نه نشانگر، نه دکمه.
 * ⛔ داوری با سرور است: «باز / بسته» همان `presence` ِ `summary` است که مشتری در ویترین می‌بیند؛ زمانِ
 * تازهٔ آماده‌شدن را سرور از زمانِ ذخیره‌شدهٔ خودش می‌سازد (این‌جا فقط «چند دقیقه»). وضعیت‌های مجازِ
 * «عقب انداختن» را هم `/config` می‌گوید.
 * ⛔ «عقب انداختن» با کلیدِ یکتایی (یک بار برای هر پنجره) و `expectedReadyAt` (زمانی که کاربر دید):
 * «تأیید» ِ دوباره پس از قطعِ نت دو بار عقب نمی‌برد و کارِ هم‌زمانِ دیگری بی‌صدا رونویسی نمی‌شود.
 *
 * بخشِ بالایی («هسته») خالص است و در Node هم بار می‌شود (`server/test/orders-desk-core.test.js`).
 */
(function (root) {
  'use strict';

  // ══════════════════════════════ هسته ══════════════════════════════

  //  «… دقیقه پیش» و کلیدِ یکتایی همان هستهٔ `orders-ui.js` (این فایل همیشه پس از آن بار می‌شود)
  const ui = typeof module !== 'undefined' && module.exports ? require('./orders-ui.js') : ((root.TohidOrders && root.TohidOrders.core) || {});
  const { ago, newKey } = ui;

  /** گزینه‌های «عقب انداختن» (دقیقه، روی زمانِ آمادهٔ فعلی) */
  const DELAY_MINUTES = [10, 15, 20, 30, 45, 60];
  const DELAY_REASON_LABELS = {
    busy: 'شلوغ', out_of_stock: 'منتظرِ کالا', courier: 'منتظرِ پیک', customer_request: 'درخواستِ مشتری', other: 'دلیلِ دیگر',
  };
  const DELAY_EVENT = 'ready_delay';

  /** «عقب انداختن» برای این سفارش و این کاربر؟ — وضعیت‌های مجاز را سرور می‌گوید (`delay` ِ `/config`) */
  function canDelay(order, cfg) {
    const d = cfg && cfg.delay;
    return !!(order && d && Array.isArray(d.statuses) && d.statuses.includes(order.status)
      && cfg.permissions && cfg.permissions.manage);
  }

  /**
   * بدنهٔ `POST /orders/:id/delay` — یا خطای فارسی. `readyAt` همان زمانی است که کاربر دید (`null` ⇒ «بی زمان»).
   * @returns {{ok:true, body:object}|{ok:false, error:string}}
   */
  function buildDelayBody(v, readyAt, key) {
    const x = v || {};
    const minutes = Number(x.minutes);
    if (!Number.isSafeInteger(minutes) || minutes < 1 || minutes > 24 * 60) return { ok: false, error: 'مدت را انتخاب کنید' };
    const reason = String(x.reason || '').trim();
    const note = String(x.note || '').trim();
    if (reason === 'other' && !note) return { ok: false, error: 'برای «دلیلِ دیگر» توضیح لازم است' };
    const body = { minutes, expectedReadyAt: readyAt == null ? null : Number(readyAt), clientKey: String(key || '') };
    if (reason) body.reason = reason;
    if (note) body.note = note.slice(0, 300);
    return { ok: true, body };
  }

  /**
   * نشانگرِ بزرگِ «باز / بسته» از `presence` ِ سرور — همان داوریِ ویترین. `null` ⇒ ویترینی نیست و
   * نشانگری هم نیست. `tone`: open | warn | closed | off. `fmt` زمان را برای «باز تا …» می‌نویسد.
   */
  function presenceView(p, fmt) {
    if (!p || typeof p !== 'object') return null;
    const f = typeof fmt === 'function' ? fmt : String;
    if (p.published === false) {
      return { tone: 'off', title: 'ویترین بسته است', sub: 'مشتری نمی‌تواند آنلاین سفارش بدهد؛ سفارشِ تلفنی و دستی همین‌جاست' };
    }
    switch (p.badge) {
      case 'open_online': return { tone: 'open', title: 'باز', sub: 'سفارش می‌گیرد' };
      case 'declared_until':
        return { tone: 'open', title: `باز تا ${f(p.declaredUntil)}`, sub: 'سفارش می‌گیرد؛ پس از آن طبقِ ساعتِ کاری' };
      case 'open_unconfirmed':
        return { tone: 'warn', title: 'باز — اتصال تأیید نشده', sub: 'مدتی است دستگاهی از دکان همگام نشده؛ مشتری «بررسی با تأخیر» می‌بیند' };
      case 'closed':
        return {
          tone: 'closed', title: 'بسته',
          sub: p.holiday ? 'امروز تعطیل است؛ سفارش برای بعد ثبت می‌شود'
            : (p.mode === 'closed' ? 'اعلامِ «بسته»؛ سفارش برای بعد ثبت می‌شود' : 'بیرون از ساعتِ کاری؛ سفارش برای بعد ثبت می‌شود'),
        };
      case 'offline':
        return {
          tone: 'closed', title: 'آفلاین',
          sub: p.mode === 'offline' ? 'اعلامِ «آفلاین»؛ سفارش برای بعد ثبت می‌شود'
            : 'هیچ دستگاهی از دکان وصل نیست؛ مشتری دکان را «موقتاً آفلاین» می‌بیند',
        };
      default: return { tone: 'warn', title: 'نامعلوم', sub: '' };
    }
  }

  /** هشدارِ «اتصال قطع است» — پرسشِ صف به سرور نرسید؛ `null` ⇒ وصل */
  function netWarning(net, nowMs) {
    if (!net || !net.down) return null;
    return {
      title: 'اتصال قطع است — سفارشِ تازه دیده نمی‌شود',
      sub: (net.lastOkAt ? `آخرین به‌روزرسانی: ${ago(net.lastOkAt, nowMs)}. ` : '') + 'با برگشتِ اینترنت صف خودش تازه می‌شود.',
    };
  }

  /** خطای پرسشِ صف «سرور نرسید» است (نه «پرچم خاموش» یا ۴xx ِ دیگر)؟ */
  const isNetDown = (e) => !!e && (e.code === 'network' || Number(e.status) >= 500);

  const core = {
    DELAY_MINUTES, DELAY_REASON_LABELS, DELAY_EVENT, canDelay, buildDelayBody, presenceView, netWarning, isNetDown,
  };
  if (typeof module !== 'undefined' && module.exports) { module.exports = core; return; }
  if (typeof document === 'undefined') return;

  // ══════════════════════════════ مرورگر ══════════════════════════════

  const st = { presence: null, net: { down: false, lastOkAt: 0 }, act: null, sending: false, api: null };

  // ── ORD-03: نشانگرِ «باز / بسته» و هشدارِ قطع ───────────────────────

  function mount(api) {
    st.api = api;
    const page = api.$('page-orders');
    const on = !!(api.cfg && api.cfg.presence);
    const box = api.$('ord-presence');
    if (!on || !page) {
      ['ord-presence', 'ord-net'].forEach((id) => { const el = api.$(id); if (el) el.remove(); });
      return;
    }
    if (!box) {
      const tabs = api.$('ord-tabs');
      const html = `<div class="ord-net" id="ord-net" role="alert" hidden></div>
        <div class="ord-presence" id="ord-presence" role="status" aria-live="polite" hidden></div>`;
      if (tabs) tabs.insertAdjacentHTML('beforebegin', html); else page.insertAdjacentHTML('beforeend', html);
    }
    paint(api);
  }

  function unmount(api) {
    ['ord-presence', 'ord-net', 'ord-delay-modal'].forEach((id) => { const el = api.$(id); if (el) el.remove(); });
    st.presence = null;
    st.act = null;
  }

  function paint(api) {
    const box = api.$('ord-presence');
    const warn = api.$('ord-net');
    if (!box || !warn) return;
    const v = presenceView(st.presence, api.when);
    box.hidden = !v;
    if (v) {
      box.className = `ord-presence p-${v.tone}`;
      box.innerHTML = `<span class="ord-presence-dot" aria-hidden="true"></span>
        <span class="ord-presence-txt"><b>${api.esc(v.title)}</b>${v.sub ? `<small>${api.esc(v.sub)}</small>` : ''}</span>`;
    }
    const w = netWarning(st.net, Date.now());
    warn.hidden = !w;
    warn.innerHTML = w ? `<b>${api.esc(w.title)}</b><small>${api.esc(w.sub)}</small>` : '';
  }

  function summary(api, s) {
    st.presence = s && s.presence !== undefined ? s.presence : null;
    st.net = { down: false, lastOkAt: Date.now() };
    paint(api);
  }

  function summaryFailed(api, e) {
    if (!isNetDown(e)) return;
    //  «باز / بسته» ِ پیشین می‌ماند؛ فقط هشدارِ قطع رویش
    st.net = { ...st.net, down: true };
    paint(api);
  }

  //  قطعِ اینترنتِ همین دستگاه ⇒ هشدار همین لحظه، نه پس از پرسشِ بعدی
  root.addEventListener('offline', () => {
    if (!st.api) return;
    st.net = { ...st.net, down: true };
    paint(st.api);
  });

  // ── ORD-07: «عقب انداختن» ─────────────────────────────────────────

  function eventLine(api, e) {
    if (!e || e.kind !== 'note' || !e.detail || e.detail.type !== DELAY_EVENT) return null;
    const reason = e.reason ? (DELAY_REASON_LABELS[e.reason] || e.reason) : '';
    const prev = e.detail.prevReadyAt ? ` · پیش‌تر ${api.esc(api.when(e.detail.prevReadyAt))}` : '';
    return `<li class="ord-ev-delay"><time>${api.esc(api.when(e.at))}</time><b>عقب افتاد: آماده تا ${api.esc(api.when(e.detail.readyAt))}</b>
        <span>${reason ? `دلیل: ${api.esc(reason)}` : ''}${prev}</span>
        ${e.note ? `<span class="ord-note">${api.esc(e.note)}</span>` : ''}</li>`;
  }

  function detailButtons(api, o) {
    return canDelay(o, api.cfg) ? '<button type="button" class="btn btn-secondary btn-sm" data-delay>عقب انداختن</button>' : '';
  }

  function detailClick(api, e) {
    if (!e.target.closest('[data-delay]')) return false;
    openDelay(api);
    return true;
  }

  function modal(api) {
    let m = api.$('ord-delay-modal');
    if (m) return m;
    document.body.insertAdjacentHTML('beforeend', `
      <div class="modal-scrim ord-modal" id="ord-delay-modal">
        <div class="modal-box" role="dialog" aria-modal="true" aria-labelledby="ord-delay-title">
          <div class="modal-head"><h3 id="ord-delay-title">عقب انداختن</h3><button type="button" class="modal-close" data-delay-close aria-label="بستن">✕</button></div>
          <div id="ord-delay-body"></div>
          <p class="ord-err" id="ord-delay-err" role="alert"></p>
          <div class="modal-actions">
            <button type="button" class="btn btn-secondary" data-delay-close>انصراف</button>
            <button type="button" class="btn btn-primary" id="ord-delay-ok">تأیید</button>
          </div>
        </div>
      </div>`);
    m = api.$('ord-delay-modal');
    m.addEventListener('click', (e) => { if (e.target === m || e.target.closest('[data-delay-close]')) close(api); });
    m.addEventListener('input', () => paintOk(api));
    m.addEventListener('change', () => paintOk(api));
    api.$('ord-delay-ok').addEventListener('click', () => send(api));
    return m;
  }

  function close(api) {
    api.closeModal(api.$('ord-delay-modal'));
    st.act = null;
  }

  /** کلیدِ یکتایی یک بار برای همین پنجره ⇒ «تأیید» ِ دوباره (قطعِ نت) دو بار عقب نمی‌برد */
  function openDelay(api) {
    const o = api.open;
    if (!o || !canDelay(o, api.cfg)) return;
    const m = modal(api);
    st.act = { id: o.id, orderNo: o.orderNo, key: newKey(), readyAt: o.readyAt == null ? null : Number(o.readyAt) };
    api.$('ord-delay-title').textContent = `عقب انداختن — سفارش #${Number(o.orderNo)}`;
    const reasons = (api.cfg.delay && api.cfg.delay.reasons) || [];
    api.$('ord-delay-body').innerHTML = `
      <p class="ord-hint">${o.readyAt ? `اکنون: آماده تا ${api.esc(api.when(o.readyAt))}` : 'هنوز زمانِ آماده ندارد — از همین حالا حساب می‌شود.'}</p>
      <div class="form-group"><label for="ord-delay-min">چقدر دیرتر</label>
        <select class="form-input" id="ord-delay-min">${DELAY_MINUTES.map((n) => `<option value="${n}"${n === 15 ? ' selected' : ''}>${n} دقیقه</option>`).join('')}</select></div>
      ${reasons.length ? `<fieldset class="ord-reasons"><legend>دلیل <small>(اختیاری)</small></legend>${reasons.map((r) => `
        <label class="ord-radio"><input type="radio" name="ord-delay-reason" value="${api.esc(r)}"> ${api.esc(DELAY_REASON_LABELS[r] || r)}</label>`).join('')}</fieldset>` : ''}
      <div class="form-group"><label for="ord-delay-note">توضیح <small>(برای «دلیلِ دیگر» لازم است)</small></label>
        <textarea class="form-textarea" id="ord-delay-note" maxlength="300"></textarea></div>`;
    api.$('ord-delay-err').textContent = '';
    m.classList.add('open');
    paintOk(api);
  }

  function input(api) {
    const r = document.querySelector('#ord-delay-body input[name="ord-delay-reason"]:checked');
    const n = api.$('ord-delay-note');
    const min = api.$('ord-delay-min');
    return { minutes: min ? min.value : '', reason: r ? r.value : '', note: n ? n.value : '' };
  }

  function paintOk(api) {
    const ok = api.$('ord-delay-ok');
    if (!ok || !st.act) return;
    ok.disabled = st.sending || !buildDelayBody(input(api), st.act.readyAt, st.act.key).ok;
  }

  async function send(api) {
    const act = st.act;
    if (!act || st.sending) return;
    const out = buildDelayBody(input(api), act.readyAt, act.key);
    if (!out.ok) { api.$('ord-delay-err').textContent = out.error; return; }
    st.sending = true;
    paintOk(api);
    try {
      const r = await api.call('/' + encodeURIComponent(act.id) + '/delay', { method: 'POST', body: out.body });
      close(api);
      if (api.open && api.open.id === act.id) api.showOrder(r.order);
      api.toast(`سفارش #${Number(r.order.orderNo)}: آماده تا ${api.when(r.order.readyAt)}`);
    } catch (e) {
      if (e && (e.code === 'ready_changed' || e.code === 'delay_not_allowed')) {
        //  کسِ دیگری زودتر عوض کرد ⇒ وضعیتِ تازه را ببیند و دوباره تصمیم بگیرد
        close(api);
        api.toast(e.code === 'ready_changed'
          ? 'زمانِ آمادهٔ این سفارش همین حالا جای دیگری عوض شد؛ زمانِ تازه را ببینید.'
          : 'این سفارش دیگر عقب انداختنی نیست؛ وضعیتِ تازه را ببینید.');
        api.reopen(act.id);
      } else {
        api.$('ord-delay-err').textContent = e && e.code === 'network'
          ? 'اتصال قطع شد. دوباره «تأیید» را بزنید — دو بار عقب نمی‌رود.'
          : ((e && e.message) || 'نشد — دوباره بزنید.');
      }
    } finally {
      st.sending = false;
      paintOk(api);
      api.refresh();
    }
  }

  root.TohidOrdersDesk = { mount, unmount, summary, summaryFailed, eventLine, detailButtons, detailClick, core };
})(typeof window !== 'undefined' ? window : globalThis);
