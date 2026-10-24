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

  // ---------- بارِ زبانه ----------
  const OPS_PARTS = [
    [loadProviders, 'ops-prov-msg'],
    [loadVersions, 'ops-ver-msg'],
  ];

  async function loadOps() {
    await Promise.all(OPS_PARTS.map(([fn, box]) => fn().catch((err) => msg($(box), err.message, 'bad'))));
  }

  LOADERS.ops = loadOps;
})();
