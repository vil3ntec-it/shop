'use strict';
/**
 * زبانهٔ «عملیات» پنلِ مدیر — ماژولِ جدا تا `app.js` بزرگ‌تر نشود.
 *
 * پس از `app.js` بار می‌شود و ابزارهای همان را به کار می‌برد ($، el، call،
 * fa، dateTime، msg و `LOADERS`). هر بخشِ تازهٔ عملیات (نسخهٔ دستگاه‌ها،
 * سوءاستفاده و پیامک، زنجیرهٔ هش، تطبیقِ فروش) یک تابعِ `load*` است که در
 * `OPS_PARTS` می‌نشیند؛ شکستِ یکی فقط پیامِ همان بخش را قرمز می‌کند.
 */

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

// ---------- بارِ زبانه ----------
const OPS_PARTS = [
  [loadProviders, 'ops-prov-msg'],
];

async function loadOps() {
  await Promise.all(OPS_PARTS.map(([fn, box]) => fn().catch((err) => msg($(box), err.message, 'bad'))));
}

LOADERS.ops = loadOps;
