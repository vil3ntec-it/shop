'use strict';
/**
 * پنلِ مدیر — فروش، پرداخت‌ها، تخفیف‌ها و کمپین، مرکزِ اعلان. از `app.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان.
 *
 * اسکریپتِ کلاسیک: پس از `app.js` و به ترتیبِ index.html بار می‌شود و ابزارهای همان را به کار می‌برد
 * ($، el، call، msg، fa، dateTime، token…). فقط تعریف دارد؛ هنگامِ بار شدن چیزی اجرا نمی‌شود
 * (دکمه‌ها را `start.js` پس از DOMContentLoaded وصل می‌کند).
 */
/* ══════════════════════════════════════════════════════════════════
   فروش · پرداخت · تخفیف · مرکزِ اعلان   (بخش‌های ۱۱.۳.۲ · ۱۱.۳.۳ · ۱۱.۴)
   ------------------------------------------------------------------
   ⛔ هیچ قیمتی این‌جا نوشته نمی‌شود — همه از سرور می‌آید.
   ⛔ هر درخواستی که به دو بخش می‌خورد `app` را همراه می‌برد.
   ══════════════════════════════════════════════════════════════════ */

const APP_FA = { shop: 'دکان', pump: 'پمپ‌بنزین', both: 'هر دو' };
const CUR_FA = { AFN: 'افغانی', USD: 'دالر' };
const METHOD_FA = { cash: 'نقد', hawala: 'حواله', exchange: 'صرافی' };
const NSTATUS_FA = {
  draft: 'پیش‌نویس', scheduled: 'زمان‌بندی‌شده', sending: 'در حالِ ارسال',
  sent: 'فرستاده شد', failed: 'ناموفق',
};
const DSTATUS_FA = {
  queued: 'در صف', sent: 'فرستاده شد', delivered: 'تحویل شد', read: 'خوانده شد', error: 'خطا',
};
const CHANNEL_FA = { inapp: 'داخلِ برنامه', push: 'پوش', email: 'ایمیل' };

/** پولِ هر ارز، با واحدِ خودش — هیچ‌وقت دو ارز با هم جمع نمی‌شوند. */
function money(byCurrency) {
  const parts = Object.entries(byCurrency || {}).filter(([, v]) => v);
  if (!parts.length) return '—';
  return parts.map(([cur, val]) => `${fa(val)} ${CUR_FA[cur] || cur}`).join(' · ');
}

function statCard(label, value) {
  const d = el('div', 'stat');
  d.appendChild(el('div', 'l', label));
  d.appendChild(el('div', 'v', value));
  return d;
}

/** یک ردیفِ جدول از چند خانه — همان کارِ تکراریِ همهٔ بخش‌ها. */
function tr(cells) {
  const row = el('tr');
  for (const c of cells) {
    if (c instanceof Node) { const td = el('td'); td.appendChild(c); row.appendChild(td); }
    else row.appendChild(el('td', null, c === undefined || c === null || c === '' ? '—' : String(c)));
  }
  return row;
}

function emptyRow(body, cols, text) {
  const row = el('tr');
  const td = el('td', 'muted', text);
  td.colSpan = cols;
  row.appendChild(td);
  body.appendChild(row);
}

// ---------- فروش ----------
let salesCache = null;

async function loadSales() {
  const out = await call('GET', '/admin/sales/summary');
  salesCache = out;
  const box = $('sales-stats');
  box.innerHTML = '';
  for (const [period, label] of [['today', 'امروز'], ['month', 'این ماه'], ['year', 'امسال']]) {
    box.appendChild(statCard(`${label} — دکان`, money(out.revenue[period].shop)));
    box.appendChild(statCard(`${label} — پمپ`, money(out.revenue[period].pump)));
  }
  box.appendChild(statCard('اشتراکِ فعالِ دکان', fa(out.counts.shop.active)));
  box.appendChild(statCard('اشتراکِ فعالِ پمپ', fa(out.counts.pump.active)));

  const series = $('sales-series');
  series.innerHTML = '';
  for (const m of out.series) {
    series.appendChild(tr([m.month, money(m.shop), money(m.pump), fa(m.payments)]));
  }
  await Promise.all([loadSalesSubs(), loadExpiring(), loadDebts(), loadDownloads()]);
}

async function loadSalesSubs() {
  const q = new URLSearchParams();
  if ($('sal-app').value) q.set('app', $('sal-app').value);
  if ($('sal-status').value) q.set('status', $('sal-status').value);
  if ($('sal-city').value.trim()) q.set('city', $('sal-city').value.trim());
  const out = await call('GET', `/admin/sales/subscriptions?${q}`);
  const body = $('sales-subs');
  body.innerHTML = '';
  if (!out.subscriptions.length) return emptyRow(body, 11, 'اشتراکی نیست');
  for (const r of out.subscriptions) {
    const acts = el('div', 'row');
    if (!r.permanent) {
      const perm = el('button', 'btn btn-ghost btn-sm', 'دائمی');
      perm.onclick = async () => {
        if (!confirm(`اشتراکِ «${r.tenantName}» دائمی شود؟`)) return;
        try {
          await call('POST', `/admin/${r.app === 'pump' ? 'pump/' : ''}subscriptions/${r.id}/permanent`, {});
          msg($('sales-msg'), 'دائمی شد.', 'ok');
          await loadSalesSubs();
        } catch (err) { msg($('sales-msg'), err.message, 'bad'); }
      };
      acts.appendChild(perm);
    }
    const disc = el('button', 'btn btn-ghost btn-sm', 'تخفیف');
    disc.onclick = async () => {
      const percent = prompt('چند درصد تخفیف؟');
      if (!percent) return;
      const reason = prompt('دلیلِ تخفیف (اجباری):');
      if (!reason) return;
      try {
        await call('POST', `/admin/${r.app === 'pump' ? 'pump/' : ''}subscriptions/${r.id}/discount`,
          { percent: Number(percent), reason });
        msg($('sales-msg'), 'تخفیف ثبت شد.', 'ok');
        await loadSalesSubs();
      } catch (err) { msg($('sales-msg'), err.message, 'bad'); }
    };
    acts.appendChild(disc);
    const addon = el('button', 'btn btn-ghost btn-sm', 'افزونه');
    addon.onclick = async () => {
      const feature = prompt('کلیدِ قابلیت (مثلاً barcode):');
      if (!feature) return;
      try {
        await call('POST', `/admin/${r.app === 'pump' ? 'pump/' : ''}subscriptions/${r.id}/addons`,
          { feature, price: 0 });
        msg($('sales-msg'), 'افزونه اضافه شد.', 'ok');
      } catch (err) { msg($('sales-msg'), err.message, 'bad'); }
    };
    acts.appendChild(addon);

    body.appendChild(tr([
      APP_FA[r.app], r.tenantName, r.ownerName || r.ownerEmail, r.city,
      r.planTitle, badge(r.status), date(r.endsAt),
      r.permanent ? 'دائمی ✓' : fa(r.daysLeft),
      r.price === null ? '—' : `${fa(r.price)} ${CUR_FA[r.currency] || r.currency}`,
      fa(r.paid), acts,
    ]));
  }
}

async function loadExpiring() {
  const days = Number($('exp-days').value) || 7;
  const out = await call('GET', `/admin/sales/expiring?days=${days}`);
  const body = $('sales-expiring');
  body.innerHTML = '';
  if (!out.expiring.length) return emptyRow(body, 5, 'هیچ اشتراکی رو به پایان نیست');
  for (const r of out.expiring) {
    body.appendChild(tr([
      APP_FA[r.app], r.shop_name || r.station_name || r.name || '—',
      r.owner_name || r.owner_email || '—', r.plan, fa(r.daysLeft),
    ]));
  }
}

async function remindExpiring() {
  const node = $('exp-msg');
  const days = Number($('exp-days').value) || 7;
  if (!confirm(`به همهٔ اشتراک‌های زیرِ ${days} روز یادآوری برود؟`)) return;
  try {
    const out = await call('POST', '/admin/sales/expiring/remind', { days, app: 'both' });
    msg(node, `به ${fa(out.recipients)} نفر رفت — ${fa(out.sent)} موفق، ${fa(out.error)} ناموفق. گزارشش در «مرکز اعلان» است.`, 'ok');
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function loadDebts() {
  const out = await call('GET', '/admin/sales/debts');
  const body = $('sales-debts');
  body.innerHTML = '';
  if (!out.debts.length) return emptyRow(body, 6, 'بدهی‌ای نیست');
  for (const r of out.debts) {
    body.appendChild(tr([
      APP_FA[r.app], r.tenantName, r.ownerName || r.ownerEmail,
      `${fa(r.price)} ${CUR_FA[r.currency] || r.currency}`, fa(r.paid), fa(r.debt),
    ]));
  }
}

async function loadDownloads() {
  const out = await call('GET', `/downloads?app=${$('dl-app').value}`);
  const d = out.downloads[0];
  if (!d) return;
  $('dl-version').value = d.version || '';
  $('dl-url').value = d.url || '';
  $('dl-notes').value = d.notes || '';
  $('dl-min').value = d.minVersion || '';
}

async function saveDownloads() {
  const node = $('dl-msg');
  try {
    await call('PUT', '/admin/downloads', {
      app: $('dl-app').value, version: $('dl-version').value.trim(),
      url: $('dl-url').value.trim(), notes: $('dl-notes').value.trim(),
      minVersion: $('dl-min').value.trim(),
    });
    msg(node, 'ذخیره شد.', 'ok');
  } catch (err) { msg(node, err.message, 'bad'); }
}

// ---------- پرداخت‌ها ----------
async function loadPayments() {
  const app = $('pay-filter-app').value;
  const out = await call('GET', `/admin/payments${app ? `?app=${app}` : ''}`);
  const body = $('payments-body');
  body.innerHTML = '';
  if (!out.payments.length) return emptyRow(body, 8, 'پرداختی ثبت نشده است');
  for (const p of out.payments) {
    const acts = el('div', 'row');
    const receipt = el('button', 'btn btn-ghost btn-sm', 'رسید');
    receipt.onclick = () => openReceipt(p.id);
    acts.appendChild(receipt);
    const del = el('button', 'btn btn-danger btn-sm', 'حذف');
    del.onclick = async () => {
      const extra = askOptionalReason('این پرداخت حذف شود؟');
      if (!extra) return;
      try { await call('DELETE', `/admin/payments/${p.id}`, extra); await loadPayments(); }
      catch (err) { msg($('pay-msg'), err.message, 'bad'); }
    };
    acts.appendChild(del);
    body.appendChild(tr([
      date(p.paidAt), APP_FA[p.app], p.tenantName || p.tenantId,
      p.ownerName || p.ownerEmail, `${fa(p.amount)} ${CUR_FA[p.currency] || p.currency}`,
      METHOD_FA[p.method] || p.method, p.receiptNo, acts,
    ]));
  }
}

/**
 * رسید با توکنِ مدیر گرفته می‌شود و در پنجرهٔ تازه باز می‌شود — لینکِ
 * ساده توکن ندارد و ۴۰۱ می‌گیرد.
 */
async function openReceipt(id) {
  const res = await fetch(`${API}/admin/payments/${id}/receipt`, { headers: { Authorization: `Bearer ${token}` } });
  const html = await res.text();
  const w = window.open('', '_blank');
  if (!w) { alert('مرورگر پنجرهٔ تازه را بست؛ اجازهٔ پاپ‌آپ بدهید.'); return; }
  w.document.open(); w.document.write(html); w.document.close();
}

async function addPayment() {
  const node = $('pay-msg');
  try {
    const out = await callOnce('add-payment', 'POST', '/admin/payments', {   // کلیدِ یکتایی: دو کلیک ⇒ یک پرداخت (SEC-API-09)
      app: $('pay-app').value,
      tenantId: $('pay-tenant').value.trim(),
      subscriptionId: $('pay-sub').value.trim(),
      amount: Number($('pay-amount').value),
      currency: $('pay-currency').value,
      method: $('pay-method').value,
      receiptNo: $('pay-receipt').value.trim(),
      note: $('pay-note').value.trim(),
      ...reasonBody('pay-reason'),
    });
    if (!out) return; msg(node, 'ثبت شد.', 'ok');   //  null ⇒ همان ثبت هنوز در راه است
    $('pay-amount').value = '';
    $('pay-note').value = '';
    $('pay-reason').value = '';
    await loadPayments();
  } catch (err) { msg(node, err.message, 'bad'); }
}

// ---------- تخفیف‌ها و کمپین ----------
async function fillPlanOptions() {
  const sel = $('dc-plan');
  const app = $('dc-app').value;
  //  ⚠️ اگر هنوز نیامده‌اند، همین‌جا می‌آیند — تبِ تخفیف ممکن است پیش
  //  از تبِ پلن‌ها باز شود و کادرِ خالی کاربر را گمراه می‌کند.
  if (app === 'pump' && !pumpPlans.length) {
    try { pumpPlans = (await call('GET', '/admin/plans?app=pump')).plans || []; } catch { /* بی‌اهمیت */ }
  }
  if (app !== 'pump' && !plans.length) {
    try { plans = (await call('GET', '/admin/plans')).plans || []; } catch { /* بی‌اهمیت */ }
  }
  const list = app === 'pump' ? pumpPlans : plans;
  sel.innerHTML = '';
  sel.appendChild(new Option('همهٔ پلن‌ها', ''));
  for (const p of list) sel.appendChild(new Option(`${p.title} (${p.code})`, p.code));
}

async function loadDiscounts() {
  await fillPlanOptions();
  const out = await call('GET', '/admin/discount-codes');
  const body = $('dc-body');
  body.innerHTML = '';
  if (!out.codes.length) emptyRow(body, 8, 'کدی ساخته نشده است');
  for (const c of out.codes) {
    const acts = el('div', 'row');
    if (c.status === 'active') {
      const rev = el('button', 'btn btn-danger btn-sm', 'باطل');
      rev.onclick = async () => {
        if (!confirm(`کدِ ${c.code} باطل شود؟`)) return;
        try { await call('POST', `/admin/discount-codes/${c.id}/revoke`, {}); await loadDiscounts(); }
        catch (err) { msg($('dc-msg'), err.message, 'bad'); }
      };
      acts.appendChild(rev);
    }
    const code = el('span'); code.dir = 'ltr'; code.textContent = c.code;
    body.appendChild(tr([
      code, APP_FA[c.app],
      c.kind === 'percent' ? `${fa(c.value)}٪` : `${fa(c.value)} ${CUR_FA[c.currency] || c.currency}`,
      c.plan || 'همه', c.expiresAt ? date(c.expiresAt) : 'بی مهلت',
      `${fa(c.uses)}${c.maxUses ? ` از ${fa(c.maxUses)}` : ''}`,
      badge(c.status === 'active' ? 'active' : 'cancelled'), acts,
    ]));
  }
  await Promise.all([loadCampaigns(), loadPriceHistory()]);
}

async function makeDiscountCode() {
  const node = $('dc-msg');
  try {
    const until = $('dc-until').value;
    const out = await call('POST', '/admin/discount-codes', {
      app: $('dc-app').value,
      code: $('dc-code').value.trim(),
      kind: $('dc-kind').value,
      value: Number($('dc-value').value),
      plan: $('dc-plan').value,
      userId: $('dc-user').value.trim(),
      maxUses: $('dc-max').value ? Number($('dc-max').value) : null,
      expiresAt: until ? new Date(`${until}T23:59:59`).getTime() : null,
      oncePerCustomer: $('dc-once').checked,
      note: $('dc-note').value.trim(),
    });
    msg(node, `کدِ ${out.code.code} ساخته شد.`, 'ok');
    $('dc-code').value = '';
    await loadDiscounts();
  } catch (err) { msg(node, err.message, 'bad'); }
}

/** فیلترِ گیرنده از کادرهای کمپین/اعلان — همان شکلی که سرور می‌خواهد. */
function audienceOf(kind, { city = '', plan = '', days = '', user = '', expired = false, permanent = false } = {}) {
  if (kind === 'all') return { kind: 'all' };
  if (kind === 'user') return { kind: 'user', user_id: user };
  const out = { kind: 'filter' };
  if (city) out.city = city;
  if (plan) out.plan = plan;
  if (days !== '' && days !== null) out.expiring_days = Number(days);
  if (expired) out.expired = true;
  if (permanent) out.permanent = true;
  return out;
}

async function makeCampaign() {
  const node = $('cmp-msg');
  const pick = $('cmp-audience').value;
  const filter = audienceOf(pick === 'all' ? 'all' : 'filter', {
    city: $('cmp-city').value.trim(),
    days: pick === 'expiring' ? $('cmp-days').value : '',
    expired: pick === 'expired',
    permanent: pick === 'permanent',
  });
  const channels = [];
  if ($('cmp-inapp').checked) channels.push('inapp');
  if ($('cmp-email').checked) channels.push('email');
  if ($('cmp-push').checked) channels.push('push');
  const until = $('cmp-until').value;
  try {
    const out = await call('POST', '/admin/campaigns', {
      name: $('cmp-name').value.trim(),
      app: $('cmp-app').value,
      filter,
      discount: {
        kind: $('cmp-kind').value, value: Number($('cmp-value').value),
        code: $('cmp-code').value.trim(),
        expiresAt: until ? new Date(`${until}T23:59:59`).getTime() : null,
      },
      notice: { title: $('cmp-title').value.trim(), body: $('cmp-body').value, channels },
    });
    const codes = out.codes.map(c => c.code).join('، ');
    msg(node, `کمپین ساخته شد. کد: ${codes}${out.sent ? ` — به ${fa(out.sent.recipients)} نفر رفت.` : ''}`, 'ok');
    await loadDiscounts();
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function loadCampaigns() {
  const out = await call('GET', '/admin/campaigns');
  const body = $('cmp-body-list');
  body.innerHTML = '';
  if (!out.campaigns.length) return emptyRow(body, 5, 'کمپینی نیست');
  for (const c of out.campaigns) {
    const b = el('button', 'btn btn-ghost btn-sm', 'آمار');
    b.onclick = () => showCampaignStats(c.id);
    body.appendChild(tr([c.name, APP_FA[c.app], dateTime(c.createdAt),
      badge(c.status === 'active' ? 'active' : 'expired'), b]));
  }
}

async function showCampaignStats(id) {
  const box = $('cmp-stats');
  box.innerHTML = '';
  try {
    const s = await call('GET', `/admin/campaigns/${id}/stats`);
    const stats = el('div', 'stats');
    stats.appendChild(statCard('گیرنده', fa(s.recipients)));
    stats.appendChild(statCard('رفت', fa(s.sent)));
    stats.appendChild(statCard('دیده شد', fa(s.seen)));
    stats.appendChild(statCard('با کد خرید', fa(s.codeUses)));
    stats.appendChild(statCard('تمدید کردند', fa(s.renewed)));
    box.appendChild(stats);
  } catch (err) { msg(box, err.message, 'bad'); }
}

async function loadPriceHistory() {
  const out = await call('GET', `/admin/price-history?app=${$('ph-app').value}`);
  const body = $('ph-body');
  body.innerHTML = '';
  if (!out.history.length) return emptyRow(body, 5, 'قیمتی عوض نشده است');
  for (const r of out.history) {
    body.appendChild(tr([r.plan, r.prevPrice === null ? '—' : fa(r.prevPrice), fa(r.price),
      CUR_FA[r.currency] || r.currency, dateTime(r.changedAt)]));
  }
}

// ---------- مرکزِ اعلان ----------
let currentNotice = null;

function noticeChannels() {
  const out = [];
  if ($('nt-inapp').checked) out.push('inapp');
  if ($('nt-email').checked) out.push('email');
  if ($('nt-push').checked) out.push('push');
  return out.length ? out : ['inapp'];
}

function noticeAudience() {
  return audienceOf($('nt-kind').value, {
    city: $('nt-city').value.trim(),
    plan: $('nt-plan').value.trim(),
    days: $('nt-days').value,
    user: $('nt-user').value.trim(),
    expired: $('nt-expired').checked,
    permanent: $('nt-permanent').checked,
  });
}

function noticeBody() {
  const when = $('nt-when').value;
  return {
    app: $('nt-app').value,
    audience: noticeAudience(),
    channels: noticeChannels(),
    title: $('nt-title').value.trim(),
    body: $('nt-body').value,
    templateKey: $('nt-template').value,
    scheduleAt: when ? new Date(when).getTime() : null,
    repeat: $('nt-repeat').value,
  };
}

async function countAudience() {
  try {
    const out = await call('POST', '/admin/notices/audience',
      { app: $('nt-app').value, audience: noticeAudience() });
    $('nt-count').textContent = `${fa(out.count)} گیرنده`;
  } catch (err) { $('nt-count').textContent = err.message; }
}

/** ذخیرهٔ پیش‌نویس — و اگر از قبل باز است، ویرایشِ همان. */
async function saveNotice() {
  const node = $('nt-msg');
  try {
    const body = noticeBody();
    const out = currentNotice
      ? await call('PUT', `/admin/notices/${currentNotice}`, body)
      : await call('POST', '/admin/notices', body);
    currentNotice = out.notice.id;
    msg(node, 'ذخیره شد.', 'ok');
    await loadNoticeList();
    return out.notice;
  } catch (err) { msg(node, err.message, 'bad'); throw err; }
}

async function previewNotice() {
  const node = $('nt-msg');
  const box = $('nt-preview');
  box.innerHTML = '';
  try {
    await saveNotice();
    const out = await call('POST', `/admin/notices/${currentNotice}/preview`, { limit: 5 });
    msg(node, `${fa(out.recipients)} گیرنده`, 'ok');
    for (const s of out.sample) {
      const d = el('div', 'msg msg-warn');
      d.appendChild(el('b', null, s.title));
      d.appendChild(el('div', null, s.body));
      d.appendChild(el('div', 'muted', `${s.name || '—'} · ${s.email || 'بی ایمیل'}`));
      box.appendChild(d);
    }
    if (!out.sample.length) box.appendChild(el('p', 'muted', 'با این فیلتر کسی پیدا نشد.'));
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function testNotice() {
  const node = $('nt-msg');
  try {
    await saveNotice();
    const out = await call('POST', `/admin/notices/${currentNotice}/test`, { to: $('nt-test-to').value.trim() });
    msg(node, out.ok === false ? `سرورِ ایمیل نپذیرفت: ${out.error}` : 'ارسالِ آزمایشی رفت.', out.ok === false ? 'bad' : 'ok');
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function sendNotice() {
  const node = $('nt-msg');
  try {
    const notice = await saveNotice();
    if (notice.scheduleAt) { msg(node, 'زمان‌بندی شد؛ سرور خودش سرِ وقت می‌فرستد.', 'ok'); return; }
    if (!confirm('همین حالا فرستاده شود؟')) return;
    const out = await call('POST', `/admin/notices/${currentNotice}/send`, {});
    msg(node, `به ${fa(out.recipients)} گیرنده — ${fa(out.sent)} موفق، ${fa(out.error)} ناموفق.`, out.error ? 'warn' : 'ok');
    await loadNoticeList();
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function loadNoticeList() {
  const q = $('nt-filter').value;
  const out = await call('GET', `/admin/notices${q ? `?status=${q}` : ''}`);
  const body = $('nt-body-list');
  body.innerHTML = '';
  if (!out.notices.length) return emptyRow(body, 8, 'اعلانی نیست');
  for (const n of out.notices) {
    const rep = el('button', 'btn btn-ghost btn-sm', 'گزارش');
    rep.onclick = () => showNoticeReport(n.id);
    const acts = el('div', 'row');
    acts.appendChild(rep);
    const del = el('button', 'btn btn-danger btn-sm', 'حذف');
    del.onclick = async () => {
      if (!confirm('این اعلان و گزارشش حذف شود؟')) return;
      try { await call('DELETE', `/admin/notices/${n.id}`); await loadNoticeList(); }
      catch (err) { msg($('nt-msg'), err.message, 'bad'); }
    };
    acts.appendChild(del);
    const counts = n.counts || {};
    body.appendChild(tr([
      (n.system ? '⚙️ ' : '') + (n.title || '—'), APP_FA[n.app], n.audience.kind,
      (n.channels || []).map(c => CHANNEL_FA[c] || c).join('، '),
      NSTATUS_FA[n.status] || n.status,
      n.scheduleAt ? dateTime(n.scheduleAt) : '—',
      counts.recipients === undefined ? '—' : `${fa(counts.sent || 0)} از ${fa(counts.recipients)}`,
      acts,
    ]));
  }
}

async function showNoticeReport(id) {
  const box = $('nt-report');
  box.innerHTML = '';
  try {
    const out = await call('GET', `/admin/notices/${id}/report`);
    const stats = el('div', 'stats');
    for (const [k, label] of [['total', 'همه'], ['sent', 'فرستاده'], ['delivered', 'تحویل'],
      ['read', 'خوانده'], ['error', 'خطا']]) {
      stats.appendChild(statCard(label, fa(out.summary[k] || 0)));
    }
    box.appendChild(stats);
    const wrap = el('div', 'table-scroll');
    const table = el('table');
    const head = el('thead');
    head.innerHTML = '<tr><th>گیرنده</th><th>نشانی</th><th>کانال</th><th>وضعیت</th><th>خطا</th><th>زمان</th></tr>';
    table.appendChild(head);
    const tbody = el('tbody');
    for (const d of out.deliveries) {
      tbody.appendChild(tr([d.who, d.address, CHANNEL_FA[d.channel] || d.channel,
        DSTATUS_FA[d.status] || d.status, d.error, dateTime(d.sentAt || d.createdAt)]));
    }
    table.appendChild(tbody);
    wrap.appendChild(table);
    box.appendChild(wrap);
  } catch (err) { msg(box, err.message, 'bad'); }
}

async function loadTemplates() {
  const out = await call('GET', '/admin/notice-templates');
  const body = $('nt-templates');
  body.innerHTML = '';
  const sel = $('nt-template');
  sel.innerHTML = '';
  sel.appendChild(new Option('—', ''));
  for (const t of out.templates) {
    if (t.app === 'shop' || t.app === 'both') sel.appendChild(new Option(`${t.key} — ${t.title}`, t.key));

    const title = el('input'); title.type = 'text'; title.value = t.title; title.style.minWidth = '180px';
    const text = el('textarea'); text.rows = 2; text.value = t.body;
    const save = el('button', 'btn btn-sm', 'ذخیره');
    save.onclick = async () => {
      try {
        await call('PUT', `/admin/notice-templates/${encodeURIComponent(t.key)}`,
          { app: t.app, title: title.value, body: text.value });
        msg($('tpl-msg'), `قالبِ ${t.key} ذخیره شد.`, 'ok');
      } catch (err) { msg($('tpl-msg'), err.message, 'bad'); }
    };
    body.appendChild(tr([t.key, APP_FA[t.app], title, text, save]));
  }
}

/** قالبِ آماده ⇒ پر کردنِ فرم. متنِ خودِ مدیر روی‌نویسی نمی‌شود مگر بخواهد. */
async function applyTemplate() {
  const key = $('nt-template').value;
  if (!key) return;
  if (($('nt-title').value.trim() || $('nt-body').value.trim())
    && !confirm('متنِ نوشته‌شده با قالب جایگزین شود؟')) return;
  const out = await call('GET', '/admin/notice-templates');
  const app = $('nt-app').value === 'pump' ? 'pump' : 'shop';
  const t = out.templates.find(x => x.key === key && (x.app === app || x.app === 'both'))
    || out.templates.find(x => x.key === key);
  if (!t) return;
  $('nt-title').value = t.title;
  $('nt-body').value = t.body;
  for (const [id, ch] of [['nt-inapp', 'inapp'], ['nt-email', 'email'], ['nt-push', 'push']]) {
    $(id).checked = (t.channels || []).includes(ch);
  }
}

async function loadNotices() {
  currentNotice = null;
  await Promise.all([loadNoticeList(), loadTemplates()]);
}
