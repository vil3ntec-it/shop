'use strict';
/**
 * زبانهٔ «عملیات» پنلِ مدیر — ماژولِ جدا تا `app.js` بزرگ‌تر نشود.
 *
 * پس از `app.js` بار می‌شود و ابزارهای همان را به کار می‌برد ($، el، call،
 * fa، dateTime، msg و `LOADERS`). هر بخشِ تازهٔ عملیات (نسخهٔ دستگاه‌ها،
 * سوءاستفاده و پیامک، زنجیرهٔ هش، تطبیقِ فروش) یک تابعِ `load*` است که در
 * `OPS_PARTS` می‌نشیند؛ شکستِ یکی فقط پیامِ همان بخش را قرمز می‌کند.
 *
 * ⛔ همه درونِ یک تابعِ بی‌نام: اسکریپت‌های کلاسیک یک حوزهٔ سراسری دارند و یک
 *    `const`ِ هم‌نام با `app.js` کلِ این فایل را بی‌صدا از کار می‌انداخت.
 */
(() => {

  // ---------- سرویس‌های بیرونی — OPS-04@29 ----------
  const PROVIDER_FA = { live: 'زنده', mock: 'آزمایشی', incomplete: 'ناقص', off: 'خاموش', none: 'ندارد', unknown: 'نامعلوم' };
  const PROVIDER_CLS = { live: 'b-ok', mock: 'b-warn', incomplete: 'b-bad', off: 'b-mute', none: 'b-mute', unknown: 'b-mute' };
  const PROVIDER_NAME = {
    sms: 'کدِ ورودِ شماره', email: 'ایمیل', push: 'پوش', payment: 'پرداختِ آنلاین', image_ai: 'پردازشِ عکس با مدل',
  };
  const PROVIDER_MODE = {
    log: 'فقط لاگِ سرور', sms: 'سرویسِ پیامک', webhook: 'وب‌هوک', whatsapp: 'واتس‌اپ',
    smtp: 'SMTP', api: 'سرویسِ API', fcm: 'FCM', cod: 'نقد هنگامِ تحویل',
  };

  function providerNote(p) {
    if (p.state === 'mock') return 'به هیچ گوشی یا صندوقی نمی‌رسد؛ فقط در لاگِ سرور چاپ می‌شود';
    if (p.state === 'incomplete') return `کم است: ${(p.missing || []).join('، ') || '—'}`;
    if (p.state === 'off') return 'خاموش است؛ از «ایمیل و پوش» روشن می‌شود';
    if (p.id === 'payment') return `پرداختِ آنلاین نیست (تصمیمِ ${p.decision || 'D-08'})؛ ویترین دکمهٔ پرداخت ندارد`;
    if (p.id === 'image_ai') return `provider ندارد: ${(p.steps || []).join('، ')}`;
    if (p.state === 'unknown') return 'تنظیم خوانده نشد';
    return 'وصل و کامل';
  }

  async function loadProviders() {
    const body = $('ops-providers');
    const note = $('ops-prov-msg');
    body.innerHTML = '';
    msg(note, '');
    const out = await call('GET', '/admin/system/providers');
    for (const p of out.providers) {
      const tr = el('tr');
      tr.dataset.provider = p.id;
      tr.appendChild(el('td', '', PROVIDER_NAME[p.id] || p.id));
      tr.appendChild(el('td', '', PROVIDER_MODE[p.mode] || p.mode || '—'));
      const st = el('td');
      st.appendChild(el('span', `badge ${PROVIDER_CLS[p.state] || 'b-mute'}`, PROVIDER_FA[p.state] || '—'));
      tr.appendChild(st);
      tr.appendChild(el('td', 'muted', providerNote(p)));
      body.appendChild(tr);
    }
    const c = out.counts || {};
    const warn = [];
    if (c.mock) warn.push(`${fa(c.mock)} سرویس آزمایشی است و به کاربر چیزی نمی‌رساند`);
    if (c.incomplete) warn.push(`${fa(c.incomplete)} سرویس ناقص است`);
    if (!out.production) warn.push('سرور در حالتِ production نیست: کدِ راهِ «لاگ» در خودِ پاسخ برمی‌گردد');
    if (warn.length) msg(note, warn.join(' · '), c.incomplete ? 'bad' : 'warn');
    $('ops-prov-checked').textContent = `بررسی: ${dateTime(out.checkedAt)}`;
  }

  // ---------- نسخهٔ دستگاه‌ها — OPS-04 ----------
  const APP_FA = { shop: 'دکان', pump: 'پمپ' };
  const VER_FA = { current: 'به‌روز', outdated: 'کهنه', below_min: 'زیرِ حداقل', unknown: 'نامعلوم' };
  const VER_CLS = { current: 'b-ok', outdated: 'b-warn', below_min: 'b-bad', unknown: 'b-mute' };
  const APP_STATUS_FA = { ok: 'بی هشدار', warn: 'کهنه دارد', down: 'زیرِ حداقل دارد' };

  async function loadVersions() {
    const body = $('ops-versions');
    const note = $('ops-ver-msg');
    body.innerHTML = '';
    msg(note, '');
    const out = await call('GET', '/admin/system/versions');
    const alerts = [];
    for (const a of out.apps) {
      const name = APP_FA[a.app] || a.app;
      if (a.belowMin) alerts.push(`${fa(a.belowMin)} دستگاهِ ${name} زیرِ حداقل نسخهٔ ${a.minVersion} است`);
      if (a.latest && a.outdated) alerts.push(`${fa(a.outdated)} دستگاهِ ${name} پایین‌تر از آخرین نسخهٔ ${a.latest}`);
      const head = el('tr', 'ops-ver-app');
      head.dataset.app = a.app;
      const td = el('td');
      td.colSpan = 5;
      td.appendChild(el('strong', '', `${name} — ${fa(a.devices)} دستگاهِ فعال`));
      td.appendChild(el('span', 'muted', ` · آخرین نسخه: ${a.latest || 'ثبت نشده'} · حداقل: ${a.minVersion || 'ندارد'}`
        + `${a.versionsTruncated ? ` · فقط ${fa(a.versions.length)} نسخهٔ پرشمار` : ''} `));
      td.appendChild(el('span', `badge ${HEALTH_CLS[a.status] || 'b-mute'}`, `● ${APP_STATUS_FA[a.status] || '—'}`));
      head.appendChild(td);
      body.appendChild(head);
      if (!a.versions.length) {
        const tr = el('tr');
        const empty = el('td', 'muted', 'در این بازه دستگاهی همگام نشده است');
        empty.colSpan = 5;
        tr.appendChild(empty);
        body.appendChild(tr);
      }
      for (const x of a.versions) {
        const tr = el('tr');
        tr.dataset.app = a.app;
        tr.dataset.version = x.version;
        tr.appendChild(el('td', '', name));
        tr.appendChild(el('td', '', x.version || '—'));
        tr.appendChild(el('td', '', fa(x.devices)));
        tr.appendChild(el('td', '', dateTime(x.lastSeenAt)));
        const st = el('td');
        st.appendChild(el('span', `badge ${VER_CLS[x.state] || 'b-mute'}`, VER_FA[x.state] || '—'));
        tr.appendChild(st);
        body.appendChild(tr);
      }
    }
    if (alerts.length) msg(note, `⚠️ ${alerts.join(' · ')}`, out.status === 'down' ? 'bad' : 'warn');
    $('ops-ver-checked').textContent = `دستگاه‌های همگام‌شده در ${fa(out.activeDays)} روزِ گذشته · بررسی: ${dateTime(out.checkedAt)}`
      + (out.truncated ? ' · فهرست بریده شد (پرشمارترین‌ها)' : '');
  }

  // ---------- زنجیرهٔ هشِ سابقه — SEC-02 ----------
  //  فقط با دکمه (سنجش هزینه دارد) و فقط مدیرِ ارشد؛ همان سنجشِ scripts/verify-chain.js
  async function verifyChain() {
    const note = $('ops-chain-msg');
    const btn = $('btn-ops-chain');
    msg(note, '');
    btn.disabled = true;
    try {
      const chain = $('ops-chain-name').value.trim();
      const out = await call('POST', '/admin/system/chain-verify', { rows: $('ops-chain-rows').checked, ...(chain ? { chain } : {}) });
      const R = out.reasons || {};
      if (!out.total) {
        msg(note, out.enabled ? 'زنجیره‌ای نیست.' : 'زنجیره‌ای نیست — پرچمِ security / hashchain.audit خاموش است.', 'warn');
      } else if (out.ok) {
        msg(note, `✔ هر ${fa(out.total)} زنجیره سالم است${out.rows ? ' (با ردیف‌های سابقه)' : ' (بی سنجشِ ردیف‌های سابقه)'}.`, 'ok');
      } else {
        msg(note, `✖ ${fa(out.brokenCount)} زنجیره از ${fa(out.total)} شکسته است.`, 'bad');
      }
      const body = $('ops-chains');
      body.innerHTML = '';
      for (const r of [...out.broken, ...out.healthy]) {
        const tr = el('tr');
        tr.dataset.chain = r.chain;
        tr.appendChild(el('td', '', r.chain));
        const st = el('td');
        st.appendChild(el('span', `badge ${r.ok ? 'b-ok' : 'b-bad'}`, r.ok ? 'سالم' : 'شکسته'));
        tr.appendChild(st);
        tr.appendChild(el('td', '', fa(r.length)));
        tr.appendChild(el('td', r.ok ? 'muted' : '', r.ok
          ? `سر: ${r.head.slice(0, 16)}…`
          : `نخستین حلقهٔ شکسته #${fa(r.seq)}: ${R[r.reason] || r.reason} (${r.reason})`));
        body.appendChild(tr);
      }
      $('ops-chain-checked').textContent = `بررسی: ${dateTime(out.checkedAt)}`
        + (out.truncated ? ` · فقط ${fa(out.healthy.length)} زنجیرهٔ سالم از ${fa(out.okCount)} نشان داده شد` : '');
    } finally {
      btn.disabled = false;
    }
  }

  function chainRole() {
    const sup = adminRole === 'superadmin';
    $('ops-chain-tools').classList.toggle('hidden', !sup);
    $('ops-chain-super').classList.toggle('hidden', sup);
    $('ops-recon-tools').classList.toggle('hidden', !sup);
    $('ops-recon-super').classList.toggle('hidden', sup);
    $('ops-abuse-tools').classList.toggle('hidden', !sup);
    $('ops-abuse-super').classList.toggle('hidden', sup);
    $('ops-sms-tools').classList.toggle('hidden', !sup);
    $('ops-sms-super').classList.toggle('hidden', sup);
  }

  // ---------- مصرف و هزینهٔ پیامک — OPS-09 ----------
  async function loadSms() {
    const out = await call('GET', `/admin/system/sms-usage?days=${encodeURIComponent($('ops-sms-days').value)}`);
    $('ops-sms-unit').value = String(out.unitCostMinor);
    $('ops-sms-unit-note').textContent = out.unitCostMinor ? `= ${afn(out.unitCostMinor)} برای هر تکه` : 'بها ثبت نشده — هزینه ۰ حساب می‌شود';
    const t = out.total;
    const cards = $('ops-sms-totals');
    cards.innerHTML = '';
    for (const [label, value] of [
      ['تلاش', fa(t.attempts)], ['فرستاده', fa(t.sent)], ['ناموفق', fa(t.failed)], ['آزمایشی (لاگ)', fa(t.mock)],
      ['راهِ دیگر', fa(t.other)], ['تکه', fa(t.parts)], ['هزینه', afn(t.costMinor)],
    ]) {
      const c = el('div', 'stat');
      c.appendChild(el('div', 'l', label));
      c.appendChild(el('div', 'v', value));
      cards.appendChild(c);
    }
    const body = $('ops-sms');
    body.innerHTML = '';
    if (!out.shops.length) {
      const tr = el('tr');
      const c = el('td', 'muted', 'در این بازه پیامکی ثبت نشده است');
      c.colSpan = 8;
      tr.appendChild(c);
      body.appendChild(tr);
    }
    for (const r of out.shops) {
      const tr = el('tr');
      tr.dataset.shop = r.shopId;
      tr.appendChild(el('td', '', r.shopId ? (r.shopName || r.shopId) : 'بی دکان (ورود، ثبت‌نام، …)'));
      tr.appendChild(el('td', '', fa(r.attempts)));
      tr.appendChild(el('td', '', fa(r.sent)));
      tr.appendChild(el('td', '', fa(r.failed)));
      tr.appendChild(el('td', '', fa(r.mock)));
      tr.appendChild(el('td', '', fa(r.other)));
      tr.appendChild(el('td', '', afn(r.costMinor)));
      tr.appendChild(el('td', '', dateTime(r.lastAt)));
      body.appendChild(tr);
    }
    $('ops-sms-checked').textContent = `${fa(out.days)} روزِ گذشته${out.truncated ? ' · فهرست بریده شد' : ''}`;
  }

  async function saveSmsCost() {
    const raw = $('ops-sms-unit').value.trim();
    if (!/^\d+$/.test(raw)) { msg($('ops-sms-msg'), 'بها باید عددِ صحیح به واحدِ خُرد باشد (۱۰۰ = یک افغانی)', 'bad'); return; }
    if (!confirm(`بهای هر تکهٔ پیامک ${afn(Number(raw))} شود؟ (گذشته عوض نمی‌شود)`)) return;
    const out = await call('PUT', '/admin/system/sms-cost', { unitCostMinor: Number(raw) });
    msg($('ops-sms-msg'), `ذخیره شد: ${afn(out.before)} ⇐ ${afn(out.unitCostMinor)}`, 'ok');
    await loadSms();
  }

  // ---------- گزارشِ سوءاستفاده — OPS-09 ----------
  //  با دکمه (دیدنش سابقه می‌نویسد)؛ فقط شمار، نه متنِ شکایت
  const CAT_FA = {
    not_delivered: 'نرسید', wrong_item: 'کالای اشتباه', damaged: 'آسیب‌دیده', overcharged: 'گران‌فروشی', fraud: 'کلاه‌برداری',
    rude: 'بی‌احترامی', other: 'دیگر', counterfeit: 'تقلبی', prohibited: 'ممنوع', misleading: 'گمراه‌کننده',
    offensive: 'زننده', wrong_price: 'قیمتِ نادرست',
  };
  async function loadAbuse() {
    const out = await call('GET', `/admin/trust/report?days=${encodeURIComponent($('ops-abuse-days').value)}`);
    const t = out.totals;
    const cards = $('ops-abuse-totals');
    cards.innerHTML = '';
    for (const [label, value] of [['پرونده', t.cases], ['در جریان', t.pending], ['واردشده', t.upheld], ['ردشده', t.dismissed], ['شدید', t.high], ['نشان‌دار', t.flagged]]) {
      const c = el('div', 'stat');
      c.appendChild(el('div', 'l', label));
      c.appendChild(el('div', 'v', fa(value)));
      cards.appendChild(c);
    }
    $('ops-abuse-cats').textContent = out.categories.length
      ? out.categories.map((c) => `${CAT_FA[c.category] || c.category}: ${fa(c.count)}`).join(' · ')
      : '';
    const body = $('ops-abuse');
    body.innerHTML = '';
    if (!out.shops.length) {
      const tr = el('tr');
      const c = el('td', 'muted', 'در این بازه شکایت یا گزارشی نیست');
      c.colSpan = 8;
      tr.appendChild(c);
      body.appendChild(tr);
    }
    for (const r of out.shops) {
      const tr = el('tr');
      tr.dataset.shop = r.shopId;
      tr.appendChild(el('td', '', r.shopName || r.shopId));
      tr.appendChild(el('td', '', `${fa(r.cases)} (${fa(r.complaints)} شکایت، ${fa(r.reports)} گزارش)`));
      tr.appendChild(el('td', '', fa(r.pending)));
      const up = el('td');
      up.appendChild(el('span', `badge ${r.upheld ? 'b-bad' : 'b-mute'}`, fa(r.upheld)));
      tr.appendChild(up);
      tr.appendChild(el('td', '', fa(r.dismissed)));
      tr.appendChild(el('td', '', fa(r.high)));
      tr.appendChild(el('td', '', `${fa(r.flagged)} نشان‌دار · ${fa(r.activeSanctions)} جریمهٔ فعال`));
      tr.appendChild(el('td', '', dateTime(r.lastAt)));
      body.appendChild(tr);
    }
    $('ops-abuse-checked').textContent = `${fa(out.days)} روزِ گذشته${out.truncated ? ' · فهرست بریده شد' : ''}`;
  }

  // ---------- تطبیقِ فروشِ آنلاین — E2E-ADM-15 ----------
  //  پول به واحدِ خُرد می‌آید؛ فقط برای نمایش به افغانی (÷۱۰۰) — هیچ حسابی این‌جا نیست
  const afn = (minor) => `${(Number(minor || 0) / 100).toLocaleString('fa-AF', { maximumFractionDigits: 2 })} افغانی`;
  const ISSUE_FA = {
    refund_not_closed: 'بازپرداختِ کامل ولی پرونده باز', refund_without_ledger: 'برگشت بی ردیفِ دفتر',
    refund_partial_mismatch: 'جزئی ولی کامل پرداخت شده', refund_full_mismatch: 'کامل ولی کمتر پرداخت شده',
    refund_exceeds_total: 'بازپرداخت بیش از مبلغ', refund_on_wrong_status: 'بازپرداخت روی وضعیتِ نادرست',
  };
  const dayMs = (v, end) => {
    if (!v) return '';
    const d = new Date(`${v}T00:00:00`);
    return String(d.getTime() + (end ? 86_400_000 : 0));
  };

  async function loadRecon() {
    const q = new URLSearchParams();
    const from = dayMs($('ops-recon-from').value, false);
    const to = dayMs($('ops-recon-to').value, true);
    if (from) q.set('from', from);
    if (to) q.set('to', to);
    const out = await call('GET', `/admin/system/reconciliation?${q}`);
    const t = out.totals;
    const cards = $('ops-recon-totals');
    cards.innerHTML = '';
    for (const [label, value] of [
      ['فروشِ آنلاین', `${fa(t.salesCount)} سفارش · ${afn(t.salesMinor)}`],
      ['بازپرداخت', `${fa(t.refundsCount)} مورد · ${afn(t.refundsMinor)}`],
      ['خالص', afn(t.netMinor)],
      ['لغو و رد', fa(t.cancellations)],
      ['نقدِ گرفته‌شدهٔ پیک', afn(t.courierCollectedMinor)],
      ['نقدِ تسویه‌نشده (اکنون)', afn(t.courierHeldMinor)],
      ['کسری / اضافهٔ تسویه', `${afn(t.shortageMinor)} / ${afn(t.excessMinor)}`],
      ['کارمزد', out.commission?.defined ? '—' : 'تعریف نشده'],
    ]) {
      const c = el('div', 'stat');
      c.appendChild(el('div', 'l', label));
      c.appendChild(el('div', 'v', value));
      cards.appendChild(c);
    }
    const body = $('ops-recon');
    body.innerHTML = '';
    if (!out.shops.length) {
      const tr = el('tr');
      const c = el('td', 'muted', 'در این بازه فروش یا تسویه‌ای نیست');
      c.colSpan = 9;
      tr.appendChild(c);
      body.appendChild(tr);
    }
    for (const r of out.shops) {
      const tr = el('tr');
      tr.dataset.shop = r.shopId;
      tr.appendChild(el('td', '', r.shopName || r.shopId));
      tr.appendChild(el('td', '', `${fa(r.sales.count)} سفارش · ${afn(r.sales.totalMinor)}`));
      tr.appendChild(el('td', '', fa(r.cancellations)));
      tr.appendChild(el('td', '', `${fa(r.refunds.count)} مورد · ${afn(r.refunds.totalMinor)}`));
      tr.appendChild(el('td', '', afn(r.netMinor)));
      tr.appendChild(el('td', '', `${afn(r.courier.collectedMinor)} · تسویه‌نشده ${afn(r.courier.heldMinor)}`));
      tr.appendChild(el('td', '', r.courier.settlements
        ? `${fa(r.courier.settlements)} تسویه · دریافت ${afn(r.courier.receivedMinor)}${r.courier.shortageMinor ? ` · کسری ${afn(r.courier.shortageMinor)}` : ''}${r.courier.excessMinor ? ` · اضافه ${afn(r.courier.excessMinor)}` : ''}`
        : '—'));
      //  کارمزد هنوز تعریف نشده (تصمیمِ صاحبِ محصول) — نه صفرِ ساختگی
      tr.appendChild(el('td', 'muted', r.commission == null ? 'تعریف نشده' : afn(r.commission)));
      const iss = el('td');
      if (r.issues.length) {
        iss.appendChild(el('span', 'badge b-bad', `${fa(r.issues.length)} اختلاف`));
        iss.appendChild(el('div', 'muted', r.issues.map((i) => `#${fa(i.orderNo)} ${ISSUE_FA[i.code] || i.code}`).join('، ')));
      } else iss.appendChild(el('span', 'badge b-ok', 'بی اختلاف'));
      tr.appendChild(iss);
      body.appendChild(tr);
    }
    $('ops-recon-checked').textContent = `${dateTime(out.window.from)} تا ${dateTime(out.window.to)} · کارمزد: تعریف نشده (تصمیمِ صاحبِ محصول)`
      + (out.truncated ? ' · فهرست بریده شد' : '');
  }

  document.addEventListener('DOMContentLoaded', () => {
    $('btn-ops-chain').onclick = () => verifyChain().catch((err) => msg($('ops-chain-msg'), err.message, 'bad'));
    $('btn-ops-sms').onclick = () => { msg($('ops-sms-msg'), ''); loadSms().catch((err) => msg($('ops-sms-msg'), err.message, 'bad')); };
    $('btn-ops-sms-cost').onclick = () => saveSmsCost().catch((err) => msg($('ops-sms-msg'), err.message, 'bad'));
    $('btn-ops-abuse').onclick = () => { msg($('ops-abuse-msg'), ''); loadAbuse().catch((err) => msg($('ops-abuse-msg'), err.message, 'bad')); };
    $('btn-ops-recon').onclick = () => { msg($('ops-recon-msg'), ''); loadRecon().catch((err) => msg($('ops-recon-msg'), err.message, 'bad')); };
  });

  // ---------- بارِ زبانه ----------
  const OPS_PARTS = [
    [loadProviders, 'ops-prov-msg'],
    [loadVersions, 'ops-ver-msg'],
  ];

  async function loadOps() {
    chainRole();
    await Promise.all(OPS_PARTS.map(([fn, box]) => fn().catch((err) => msg($(box), err.message, 'bad'))));
  }

  LOADERS.ops = loadOps;
})();
