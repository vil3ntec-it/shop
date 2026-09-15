'use strict';
/**
 * پنل مدیریت.
 *
 * توکن مدیر فقط در حافظه‌ی همین صفحه (sessionStorage) می‌ماند و نوعش با
 * توکن کاربران فرق دارد؛ پس با بستن مرورگر از بین می‌رود.
 */
const API = '/api';
const TOKEN_KEY = 'shop-admin-token';

const $ = (id) => document.getElementById(id);
const el = (tag, cls, text) => {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined) n.textContent = text;
  return n;
};

let token = sessionStorage.getItem(TOKEN_KEY) || '';
let plans = [];
let currentShop = null;

// ---------- ابزار ----------
async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  if (res.status === 401) { logout(); throw new Error('نشست تمام شد، دوباره وارد شوید'); }
  if (!res.ok) throw new Error(json?.error?.message || `خطای ${res.status}`);
  return json;
}

const fa = (n) => Number(n || 0).toLocaleString('fa-AF');
function date(ms) {
  if (!ms) return '—';
  return new Date(Number(ms)).toLocaleDateString('fa-AF', {
    year: 'numeric', month: '2-digit', day: '2-digit',
  });
}
function dateTime(ms) {
  if (!ms) return '—';
  const d = new Date(Number(ms));
  return `${d.toLocaleDateString('fa-AF')} ${d.toLocaleTimeString('fa-AF', { hour: '2-digit', minute: '2-digit' })}`;
}
function msg(node, text, kind = 'ok') {
  node.innerHTML = '';
  if (!text) return;
  node.appendChild(el('div', `msg msg-${kind}`, text));
}
const STATUS_FA = {
  active: 'فعال', suspended: 'معلق', expired: 'تمام‌شده',
  cancelled: 'لغوشده', pending: 'در انتظار', none: 'بدون اشتراک',
  disabled: 'غیرفعال', removed: 'حذف‌شده', trial: 'آزمایشی',
};
const ROLE_FA = { owner: 'صاحب دکان', manager: 'مدیر', staff: 'شاگرد' };
//  همان نقش‌ها، با واژه‌های بخشِ پمپ — «شاگرد» در پمپ‌بنزین «کارمند» است
const PUMP_ROLE_FA = { owner: 'صاحب پمپ', manager: 'مدیر', staff: 'کارمند' };
function badge(status) {
  const cls = status === 'active' ? 'b-ok'
    : status === 'trial' ? 'b-warn'
    : status === 'suspended' || status === 'pending' ? 'b-warn'
      : status === 'expired' || status === 'cancelled' || status === 'disabled' ? 'b-bad' : 'b-mute';
  return el('span', `badge ${cls}`, STATUS_FA[status] || status || '—');
}

// ---------- ورود ----------
async function login() {
  const node = $('login-msg');
  try {
    const out = await call('POST', '/admin/login', {
      username: $('in-username').value.trim(),
      password: $('in-password').value,
    });
    token = out.token;
    sessionStorage.setItem(TOKEN_KEY, token);
    $('who').textContent = `${out.admin.name || out.admin.username} — ${out.admin.role === 'superadmin' ? 'مدیر ارشد' : 'مدیر'}`;
    show();
    await refresh();
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

function logout() {
  token = '';
  sessionStorage.removeItem(TOKEN_KEY);
  $('app-view').classList.add('hidden');
  $('login-view').classList.remove('hidden');
}

function show() {
  $('login-view').classList.add('hidden');
  $('app-view').classList.remove('hidden');
}

// ---------- تازه‌سازی ----------
async function refresh() {
  await Promise.all([loadStats(), loadShops(), loadPlans()]);
}

async function loadStats() {
  const s = await call('GET', '/admin/stats');
  const box = $('stats');
  box.innerHTML = '';
  const items = [
    ['کاربران', s.users], ['دکان‌ها', s.shops], ['اعضای فعال', s.members],
    ['اشتراک فعال', s.activeSubscriptions], ['تمام‌شده', s.expiredSubscriptions],
    ['درخواست باز', s.pendingRequests],
  ];
  for (const [label, value] of items) {
    const card = el('div', 'stat');
    card.appendChild(el('b', null, fa(value)));
    card.appendChild(el('span', null, label));
    box.appendChild(card);
  }
  $('server-clock').textContent = `ساعت سرور: ${dateTime(s.serverTime)}`;
}

// ---------- دکان‌ها ----------
async function loadShops() {
  const q = encodeURIComponent($('shop-q').value.trim());
  const out = await call('GET', `/admin/shops?limit=100&q=${q}`);
  const body = $('shops-body');
  body.innerHTML = '';
  for (const s of out.shops) {
    const tr = el('tr');
    tr.appendChild(el('td', null, s.name || '—'));
    tr.appendChild(el('td', null, s.owner_name || '—'));
    const phone = el('td', null, s.owner_phone || s.owner_email || '—');
    phone.dir = 'ltr';
    tr.appendChild(phone);
    tr.appendChild(el('td', null, fa(s.members)));
    const st = el('td');
    st.appendChild(badge(s.sub_status || 'none'));
    tr.appendChild(st);
    tr.appendChild(el('td', null, s.ends_at ? date(s.ends_at) : '—'));
    const act = el('td');
    const btn = el('button', 'btn btn-sm', 'مدیریت');
    btn.onclick = () => openShop(s.id);
    act.appendChild(btn);
    tr.appendChild(act);
    body.appendChild(tr);
  }
  if (!out.shops.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'دکانی پیدا نشد');
    td.colSpan = 7;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

async function openShop(id) {
  const d = await call('GET', `/admin/shops/${id}`);
  currentShop = d;
  $('shop-detail').classList.remove('hidden');
  $('shop-title').textContent = `دکان: ${d.shop.name}`;

  const sum = $('shop-summary');
  sum.innerHTML = '';
  const ent = d.entitlement;
  const line = el('div', 'row');
  line.appendChild(badge(ent.source === 'trial' ? 'trial' : ent.subscription.status));
  line.appendChild(el('span', 'muted',
    ent.source === 'trial'
      ? `دوره آزمایشی — ${fa(ent.trial.daysLeft)} روز مانده`
      : ent.subscription.endsAt
        ? `پایان: ${date(ent.subscription.endsAt)} (${fa(ent.subscription.daysLeft)} روز مانده)`
        : 'اشتراکی ثبت نشده است'));
  line.appendChild(el('span', 'muted', `ساخت دکان: ${date(d.shop.createdAt)}`));
  sum.appendChild(line);

  const members = $('shop-members');
  members.innerHTML = '';
  for (const m of d.members) {
    const tr = el('tr');
    tr.appendChild(el('td', null, m.name || '—'));
    const ph = el('td', null, m.phone || m.email || '—');
    ph.dir = 'ltr';
    tr.appendChild(ph);
    tr.appendChild(el('td', null, ROLE_FA[m.role] || m.role));
    const st = el('td');
    st.appendChild(badge(m.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, date(m.created_at)));
    members.appendChild(tr);
  }

  const counts = $('shop-counts');
  counts.innerHTML = '';
  const LABELS = {
    products: 'محصولات', warehouseEntries: 'ورودی انبار', sales: 'فروش‌ها',
    saleItems: 'اقلام فروش', debtors: 'قرض‌داران', transactions: 'پرداخت‌ها',
    expenses: 'مصارف', suppliers: 'تأمین‌کننده', purchases: 'خریدها',
  };
  for (const [key, label] of Object.entries(LABELS)) {
    counts.appendChild(el('label', 'feat', `${label}: ${fa(d.counts[key] || 0)}`));
  }
  $('shop-detail').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

async function grantSubscription() {
  if (!currentShop) return;
  const node = $('shop-msg');
  try {
    const days = $('in-days').value.trim();
    await call('POST', '/admin/subscriptions', {
      shopId: currentShop.shop.id,
      plan: $('in-plan').value,
      days: days ? Number(days) : null,
      graceDays: Number($('in-grace').value || 0),
      note: $('in-note').value.trim(),
    });
    msg(node, 'اشتراک ثبت شد.', 'ok');
    await Promise.all([loadStats(), loadShops(), openShop(currentShop.shop.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

async function setSubStatus(status) {
  if (!currentShop) return;
  const node = $('shop-msg');
  const sub = currentShop.subscriptions?.[0];
  if (!sub) return msg(node, 'این دکان اشتراکی ندارد.', 'warn');
  try {
    await call('POST', `/admin/subscriptions/${sub.id}/status`, { status });
    msg(node, 'وضعیت اشتراک عوض شد.', 'ok');
    await Promise.all([loadStats(), loadShops(), openShop(currentShop.shop.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

/* ==========================================================
   پمپ‌بنزین‌ها
   ----------------------------------------------------------
   همان کارهای بخشِ دکان‌ها، روی `/admin/pump/…`. عمداً توابعِ جدا
   نوشته شده‌اند و نه یک تابعِ مشترکِ پارامتری: دو بخش دو دفترِ جدا
   دارند و روزی که یکی‌شان ستونی اضافه کند، آن تابعِ مشترک همان‌جا
   می‌شکند — جایی که کمترین انتظارش را داریم.
   ========================================================== */
let currentStation = null;
let pumpFeatures = [];

async function loadPumpStats() {
  const out = await call('GET', '/admin/pump/stats');
  const s = out.stats;
  const box = $('pump-stats');
  box.innerHTML = '';
  const items = [
    ['پمپ‌ها', s.stations], ['فعال', s.active_stations],
    ['اشتراک فعال', s.active_subs], ['کدِ باز', s.open_codes],
    ['فایل‌ها', s.files],
  ];
  for (const [label, value] of items) {
    const card = el('div', 'stat');
    card.appendChild(el('b', null, fa(value)));
    card.appendChild(el('span', null, label));
    box.appendChild(card);
  }
}

async function loadPump() {
  //  کاتالوگِ قابلیت‌ها یک بار، نه در هر بار باز کردنِ یک پمپ
  if (!pumpFeatures.length) {
    try {
      pumpFeatures = (await call('GET', '/admin/pump/features')).features || [];
    } catch { pumpFeatures = []; }
  }
  fillPlanSelect($('pump-plan'));
  fillPlanSelect($('pvip-plan'));

  const q = encodeURIComponent($('pump-q').value.trim());
  const out = await call('GET', `/admin/pump/stations?limit=100&q=${q}`);
  const body = $('pump-body');
  body.innerHTML = '';
  for (const s of out.stations) {
    const tr = el('tr');
    tr.appendChild(el('td', null, s.name || '—'));
    const code = el('td', null, s.code || '—');
    code.dir = 'ltr';
    tr.appendChild(code);
    tr.appendChild(el('td', null, s.owner_name || '—'));
    tr.appendChild(el('td', null, fa(s.members)));
    //  سرورِ خانگی: همان چیزی که می‌گوید برنامهٔ کامپیوتر وصل شده یا نه
    const home = el('td');
    home.appendChild(s.home_url
      ? el('span', 'badge b-ok', 'ثبت شده')
      : el('span', 'badge b-mute', 'هنوز نه'));
    tr.appendChild(home);
    const st = el('td');
    st.appendChild(badge(s.sub_status || 'none'));
    tr.appendChild(st);
    tr.appendChild(el('td', null, s.ends_at ? date(s.ends_at) : '—'));
    const act = el('td');
    const btn = el('button', 'btn btn-sm', 'مدیریت');
    btn.onclick = () => openStation(s.id);
    act.appendChild(btn);
    tr.appendChild(act);
    body.appendChild(tr);
  }
  if (!out.stations.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'پمپی پیدا نشد');
    td.colSpan = 8;
    tr.appendChild(td);
    body.appendChild(tr);
  }
  await Promise.all([loadPumpStats(), loadPumpCodes(), loadPumpUsers(), loadPumpSubs()]);
}

/* ----------------------------------------------------------
   افرادِ پمپ‌ها

   خواستهٔ صاحب مخزن: «ببینم افراد رو، اشتراک‌هاشون و غیره.»

   ⚠️ یک ردیف به ازای هر عضویت، نه هر شخص: یک نفر می‌تواند چند پمپ
   داشته باشد و حالِ اشتراکِ هر پمپ جداست.
   ---------------------------------------------------------- */
async function loadPumpUsers() {
  const q = encodeURIComponent(($('pu-q').value || '').trim());
  const out = await call('GET', `/admin/pump/users?limit=100&q=${q}`);
  const body = $('pu-body');
  body.innerHTML = '';

  for (const u of out.users) {
    const tr = el('tr');
    tr.appendChild(el('td', null, u.name || '—'));

    const contact = el('td', null, u.email || u.phone || '—');
    contact.dir = 'ltr';
    tr.appendChild(contact);

    tr.appendChild(el('td', null, u.station_name
      ? `${u.station_name} (${u.station_code})` : '—'));
    tr.appendChild(el('td', null, PUMP_ROLE_FA[u.role] || u.role || '—'));

    const sub = el('td');
    sub.appendChild(badge(u.sub_status || 'none'));
    tr.appendChild(sub);

    tr.appendChild(el('td', null, date(u.sub_ends_at)));
    body.appendChild(tr);
  }

  if (!out.users.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'کسی پیدا نشد');
    td.colSpan = 6;
    tr.appendChild(td);
    body.appendChild(tr);
  }
  $('pu-count').textContent = `${fa(out.users.length)} از ${fa(out.total)} نفر`;
}

/* ----------------------------------------------------------
   اشتراک‌های پمپ — همه، و آن‌هایی که دارند تمام می‌شوند
   ---------------------------------------------------------- */
async function loadPumpSubs(expiring = false) {
  const path = expiring ? '/admin/pump/subscriptions/expiring' : '/admin/pump/subscriptions?limit=100';
  const out = await call('GET', path);
  const rows = out.subscriptions || [];
  const body = $('psub-body');
  body.innerHTML = '';

  const day = 86400000;
  for (const s of rows) {
    /*  ⚠️ دو مسیر، دو شکلِ نام:
     *    /subscriptions          → station_name · ends_at   (ستونِ خامِ SQL)
     *    /subscriptions/expiring → tenantName   · endsAt    (نگاشت‌شده)
     *  یکی‌شان را فرض نکنید؛ هر دو را بخوانید، وگرنه یکی از دو نما خالی
     *  می‌شود و کسی هم خطایی نمی‌بیند. */
    const name = s.station_name || s.tenantName || '';
    const code = s.station_code || '';
    const endsAt = s.ends_at ?? s.endsAt ?? null;
    const startsAt = s.starts_at ?? s.startsAt ?? null;

    const tr = el('tr');
    tr.appendChild(el('td', null, name
      ? (code ? `${name} (${code})` : name)
      : (s.station_id || s.tenantId || '—')));
    tr.appendChild(el('td', null, s.plan || '—'));

    const st = el('td');
    st.appendChild(badge(s.status));
    tr.appendChild(st);

    tr.appendChild(el('td', null, date(startsAt)));
    tr.appendChild(el('td', null, date(endsAt)));

    //  روزِ مانده را خودِ صفحه حساب می‌کند تا با ساعتِ همین لحظه بخواند
    const left = endsAt ? Math.max(0, Math.ceil((Number(endsAt) - Date.now()) / day)) : null;
    tr.appendChild(el('td', null, left === null ? '—' : fa(left)));
    body.appendChild(tr);
  }

  if (!rows.length) {
    const tr = el('tr');
    const td = el('td', 'muted', expiring ? 'هیچ اشتراکی رو به پایان نیست' : 'هنوز اشتراکی نیست');
    td.colSpan = 6;
    tr.appendChild(td);
    body.appendChild(tr);
  }
  $('psub-msg').textContent = expiring
    ? 'فقط اشتراک‌هایی که نزدیکِ پایان‌اند.'
    : `${fa(rows.length)} اشتراک`;
}

async function openStation(id) {
  const d = await call('GET', `/admin/pump/stations/${id}`);
  currentStation = d;
  $('pump-detail').classList.remove('hidden');
  $('pump-title').textContent = `پمپ: ${d.station.name} (${d.station.code})`;

  const sum = $('pump-summary');
  sum.innerHTML = '';
  const ent = d.entitlement;
  const line = el('div', 'row');
  line.appendChild(badge(ent.source === 'trial' ? 'trial' : ent.subscription.status));
  line.appendChild(el('span', 'muted',
    ent.source === 'trial'
      ? `دوره آزمایشی — ${fa(ent.trial.daysLeft)} روز مانده`
      : ent.subscription.endsAt
        ? `پایان: ${date(ent.subscription.endsAt)} (${fa(ent.subscription.daysLeft)} روز مانده)`
        : 'اشتراکی ثبت نشده است'));
  line.appendChild(el('span', 'muted', `ساختِ پمپ: ${date(d.station.createdAt)}`));
  sum.appendChild(line);

  const home = el('div', 'row');
  home.appendChild(el('span', 'muted', 'سرورِ خانگی:'));
  const url = el('span', 'muted', d.station.homeUrl || 'هنوز ثبت نشده');
  url.dir = 'ltr';
  home.appendChild(url);
  if (d.station.homeSeenAt) {
    home.appendChild(el('span', 'muted', `آخرین خبر: ${dateTime(d.station.homeSeenAt)}`));
  }
  sum.appendChild(home);

  const members = $('pump-members');
  members.innerHTML = '';
  for (const m of d.members) {
    const tr = el('tr');
    tr.appendChild(el('td', null, m.name || '—'));
    const ph = el('td', null, m.phone || m.email || '—');
    ph.dir = 'ltr';
    tr.appendChild(ph);
    tr.appendChild(el('td', null, PUMP_ROLE_FA[m.role] || m.role));
    const st = el('td');
    st.appendChild(badge(m.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, date(m.created_at)));
    members.appendChild(tr);
  }

  //  چک‌باکسِ هر بخش، با همان چیزی که الان باز است تیک خورده
  const feats = $('pump-feats');
  feats.innerHTML = '';
  for (const f of pumpFeatures) {
    if (f.core) continue;           // همیشه باز است، تیکش معنا ندارد
    const wrap = el('label', 'feat');
    const box = el('input');
    box.type = 'checkbox';
    box.value = f.key;
    box.checked = ent.features.includes(f.key);
    wrap.appendChild(box);
    wrap.appendChild(el('span', null, ` ${f.label}`));
    feats.appendChild(wrap);
  }

  const files = $('pump-files');
  files.innerHTML = '';
  for (const f of d.files) {
    const tr = el('tr');
    const name = el('td', null, f.path);
    name.dir = 'ltr';
    tr.appendChild(name);
    tr.appendChild(el('td', null, `${fa(Math.max(1, Math.round(f.size / 1024)))} کیلوبایت`));
    tr.appendChild(el('td', null, dateTime(f.updatedAt)));
    files.appendChild(tr);
  }
  if (!d.files.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'هنوز چیزی از برنامهٔ این پمپ نرسیده است');
    td.colSpan = 3;
    tr.appendChild(td);
    files.appendChild(tr);
  }

  $('pump-detail').scrollIntoView({ behavior: 'smooth', block: 'start' });
}

function chosenPumpFeatures() {
  return [...$('pump-feats').querySelectorAll('input:checked')].map(i => i.value);
}

async function grantPumpSubscription() {
  if (!currentStation) return;
  const node = $('pump-msg');
  try {
    const days = $('pump-days').value.trim();
    await call('POST', '/admin/pump/subscriptions', {
      stationId: currentStation.station.id,
      plan: $('pump-plan').value,
      days: days ? Number(days) : null,
      graceDays: Number($('pump-grace').value || 0),
      features: chosenPumpFeatures(),
      note: $('pump-note').value.trim(),
    });
    msg(node, 'اشتراک ثبت شد.', 'ok');
    await Promise.all([loadPump(), openStation(currentStation.station.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

async function setPumpSubStatus(status) {
  if (!currentStation) return;
  const node = $('pump-msg');
  const id = currentStation.subscription?.id;
  if (!id) return msg(node, 'این پمپ اشتراکی ندارد.', 'warn');
  try {
    await call('POST', `/admin/pump/subscriptions/${id}/status`, { status });
    msg(node, 'وضعیت اشتراک عوض شد.', 'ok');
    await Promise.all([loadPump(), openStation(currentStation.station.id)]);
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

async function loadPumpCodes() {
  const out = await call('GET', '/admin/pump/vip-codes?limit=100');
  const body = $('pvip-body');
  body.innerHTML = '';
  for (const c of out.codes) {
    const tr = el('tr');
    //  فقط دو رقمِ آخر — خودِ کد جایی ذخیره نشده
    const hint = el('td', null, `••••${c.hint || ''}`);
    hint.dir = 'ltr';
    tr.appendChild(hint);
    tr.appendChild(el('td', null, c.plan || '—'));
    tr.appendChild(el('td', null, c.days ? fa(c.days) : '—'));
    const mail = el('td', null, c.email || '—');
    mail.dir = 'ltr';
    tr.appendChild(mail);
    const st = el('td');
    st.appendChild(badge(c.status === 'used' ? 'expired' : c.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, date(c.createdAt)));
    const act = el('td');
    if (c.status === 'active') {
      const btn = el('button', 'btn btn-danger btn-sm', 'باطل');
      btn.onclick = async () => {
        if (!confirm('این کد باطل شود؟')) return;
        try { await call('POST', `/admin/pump/vip-codes/${c.id}/revoke`, {}); await loadPumpCodes(); }
        catch (err) { msg($('pvip-msg'), err.message, 'bad'); }
      };
      act.appendChild(btn);
    }
    tr.appendChild(act);
    body.appendChild(tr);
  }
  if (!out.codes.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'هنوز کدی ساخته نشده است');
    td.colSpan = 7;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

async function makePumpCode() {
  const node = $('pvip-msg');
  try {
    const days = $('pvip-days').value.trim();
    const out = await call('POST', '/admin/pump/vip-codes', {
      plan: $('pvip-plan').value,
      days: days ? Number(days) : null,
      email: $('pvip-email').value.trim(),
      phone: $('pvip-phone').value.trim(),
      note: $('pvip-note').value.trim(),
    });
    //  کدِ خام فقط همین یک بار دیده می‌شود
    const sent = (out.emailStatus === 'sent'
      ? ' و به ایمیل فرستاده شد.'
      : out.emailStatus === 'failed' ? ` ولی ایمیل نرفت: ${out.emailError}` : '')
      + (out.smsStatus === 'sent'
      ? ' پیامک هم رفت.'
      : out.smsStatus === 'failed' ? ` ولی پیامک نرفت: ${out.smsError}` : '');
    msg(node, `کد: ${out.code} — همین حالا برش دارید، دیگر نشان داده نمی‌شود${sent}`,
      out.emailStatus === 'failed' || out.smsStatus === 'failed' ? 'warn' : 'ok');
    $('pvip-email').value = '';
    $('pvip-phone').value = '';
    $('pvip-note').value = '';
    await loadPumpCodes();
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

// ---------- کاربران ----------
async function loadUsers() {
  const q = encodeURIComponent($('user-q').value.trim());
  const out = await call('GET', `/admin/users?limit=100&q=${q}`);
  const body = $('users-body');
  body.innerHTML = '';
  for (const u of out.users) {
    const tr = el('tr');
    tr.appendChild(el('td', null, u.name || '—'));
    const ph = el('td', null, u.phone || '—');
    ph.dir = 'ltr';
    tr.appendChild(ph);
    const em = el('td', null, u.email || '—');
    em.dir = 'ltr';
    tr.appendChild(em);
    tr.appendChild(el('td', null, u.shop_name || '—'));
    tr.appendChild(el('td', null, u.role ? (ROLE_FA[u.role] || u.role) : '—'));
    const st = el('td');
    st.appendChild(badge(u.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, dateTime(u.last_login_at)));
    const act = el('td');
    const btn = el('button', `btn btn-sm ${u.status === 'active' ? 'btn-danger' : 'btn-ghost'}`,
      u.status === 'active' ? 'غیرفعال' : 'فعال');
    btn.onclick = async () => {
      await call('POST', `/admin/users/${u.id}/status`,
        { status: u.status === 'active' ? 'disabled' : 'active' });
      await loadUsers();
    };
    act.appendChild(btn);
    tr.appendChild(act);
    body.appendChild(tr);
  }
}

// ---------- اشتراک‌ها ----------
async function loadSubs() {
  const status = $('sub-filter').value;
  const out = await call('GET', `/admin/subscriptions?limit=200&status=${status}`);
  const body = $('subs-body');
  body.innerHTML = '';
  for (const s of out.subscriptions) {
    const tr = el('tr');
    tr.appendChild(el('td', null, s.shop_name || '—'));
    tr.appendChild(el('td', null, s.owner_name || '—'));
    tr.appendChild(el('td', null, s.plan));
    const st = el('td');
    st.appendChild(badge(s.state.status));
    tr.appendChild(st);
    tr.appendChild(el('td', null, date(s.starts_at)));
    tr.appendChild(el('td', null, date(s.ends_at)));
    tr.appendChild(el('td', null, `${fa(s.state.daysLeft)} روز`));
    body.appendChild(tr);
  }
}

// ---------- درخواست‌ها ----------
async function loadRequests() {
  const out = await call('GET', '/admin/purchase-requests?status=pending');
  const body = $('requests-body');
  body.innerHTML = '';
  for (const r of out.requests) {
    const tr = el('tr');
    tr.appendChild(el('td', null, r.shop_name || '—'));
    tr.appendChild(el('td', null, `${r.user_name || ''} ${r.phone || ''}`.trim()));
    tr.appendChild(el('td', null, r.plan_code));
    tr.appendChild(el('td', null, dateTime(r.created_at)));
    const act = el('td', 'row');
    const ok = el('button', 'btn btn-sm', 'تأیید');
    ok.onclick = async () => { await call('POST', `/admin/purchase-requests/${r.id}/approve`, {}); await loadRequests(); await loadStats(); };
    const no = el('button', 'btn btn-ghost btn-sm', 'رد');
    no.onclick = async () => { await call('POST', `/admin/purchase-requests/${r.id}/reject`, {}); await loadRequests(); };
    act.appendChild(ok); act.appendChild(no);
    tr.appendChild(act);
    body.appendChild(tr);
  }
}

// ---------- پلن‌ها ----------
/**
 * پر کردنِ یک فهرستِ پلن.
 *
 * سه جا لازمش داریم (اشتراکِ دکان، اشتراکِ پمپ، کدِ پمپ) و هر سه باید
 * همان فهرست را ببینند — پس یک تابع، نه سه تکهٔ کپی‌شده که روزی از هم
 * جدا بیفتند.
 */
function fillPlanSelect(sel) {
  if (!sel) return;
  const keep = sel.value;
  sel.innerHTML = '';
  for (const p of plans) {
    const o = el('option', null, `${p.title} — ${fa(p.price)}`);
    o.value = p.code;
    sel.appendChild(o);
  }
  const custom = el('option', null, 'سفارشی (با روز)');
  custom.value = 'custom';
  sel.appendChild(custom);
  if (keep) sel.value = keep;
}

async function loadPlans() {
  const out = await call('GET', '/admin/plans');
  plans = out.plans;
  fillPlanSelect($('in-plan'));

  const body = $('plans-body');
  body.innerHTML = '';
  const UNIT = { day: 'روز', week: 'هفته', month: 'ماه', year: 'سال' };
  for (const p of plans) {
    const tr = el('tr');
    tr.appendChild(el('td', null, p.code));
    const title = el('input'); title.value = p.title; title.style.width = '120px';
    tr.appendChild(el('td')).appendChild(title);
    tr.appendChild(el('td', null, `${fa(p.amount || 0)} ${UNIT[p.unit] || ''}`));
    const price = el('input'); price.type = 'number'; price.value = p.price; price.style.width = '100px';
    tr.appendChild(el('td')).appendChild(price);
    const badgeIn = el('input'); badgeIn.value = p.badge || ''; badgeIn.style.width = '110px';
    tr.appendChild(el('td')).appendChild(badgeIn);
    const active = el('input'); active.type = 'checkbox'; active.checked = p.active;
    tr.appendChild(el('td')).appendChild(active);
    const save = el('button', 'btn btn-sm', 'ذخیره');
    save.onclick = async () => {
      try {
        await call('PATCH', `/admin/plans/${p.code}`, {
          title: title.value, price: Number(price.value),
          badge: badgeIn.value, active: active.checked,
        });
        msg($('plans-msg'), `پلن ${p.title} ذخیره شد.`, 'ok');
      } catch (err) { msg($('plans-msg'), err.message, 'bad'); }
    };
    tr.appendChild(el('td')).appendChild(save);
    body.appendChild(tr);
  }

  const cfg = out.config || {};
  $('cfg-trial').value = cfg.trial_days || '';
  $('cfg-wa').value = cfg.whatsapp_number || '';
  $('cfg-currency').value = cfg.currency || '';
  $('cfg-wamsg').value = cfg.whatsapp_message || '';
}

// ---------- پشتیبان‌ها ----------
async function loadBackups() {
  const out = await call('GET', '/admin/backups');
  $('backup-dir').textContent = `مسیر: ${out.dir}${out.enabled ? '' : ' — پشتیبان‌گیری خودکار خاموش است'}`;
  const body = $('backups-body');
  body.innerHTML = '';
  const KIND = { daily: 'روزانه', weekly: 'هفتگی', monthly: 'ماهانه', manual: 'دستی' };
  for (const b of out.backups) {
    const tr = el('tr');
    tr.appendChild(el('td', null, KIND[b.kind] || b.kind));
    const f = el('td', null, b.file); f.dir = 'ltr';
    tr.appendChild(f);
    tr.appendChild(el('td', null, `${fa(Math.round(b.bytes / 1024))} کیلوبایت`));
    tr.appendChild(el('td', null, dateTime(b.createdAt)));
    tr.appendChild(el('td', null, b.encrypted ? 'بله' : 'خیر'));
    body.appendChild(tr);
  }
  if (!out.backups.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'هنوز پشتیبانی گرفته نشده است');
    td.colSpan = 5;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

// ---------- سابقه ----------
async function loadAudit() {
  const out = await call('GET', '/admin/audit?limit=200');
  const body = $('audit-body');
  body.innerHTML = '';
  for (const e of out.entries) {
    const tr = el('tr');
    tr.appendChild(el('td', null, dateTime(e.created_at)));
    tr.appendChild(el('td', null, e.actor_type === 'admin' ? 'مدیر' : 'کاربر'));
    tr.appendChild(el('td', null, e.action));
    tr.appendChild(el('td', null, `${e.target_type || ''} ${e.target_id || ''}`.trim() || '—'));
    const d = el('td', 'muted', JSON.stringify(e.detail || {}));
    d.dir = 'ltr';
    tr.appendChild(d);
    body.appendChild(tr);
  }
}

// ---------- زبانه‌ها ----------
const LOADERS = {
  shops: loadShops, pump: loadPump, users: loadUsers, subs: loadSubs,
  requests: loadRequests, plans: loadPlans, backups: loadBackups, audit: loadAudit,
};

function openTab(name) {
  for (const tab of document.querySelectorAll('.tab')) {
    tab.classList.toggle('active', tab.dataset.tab === name);
  }
  for (const key of Object.keys(LOADERS)) {
    $(`tab-${key}`).classList.toggle('hidden', key !== name);
  }
  LOADERS[name]().catch(err => console.error(err));
}

// ---------- راه‌اندازی ----------
document.addEventListener('DOMContentLoaded', () => {
  $('btn-login').onclick = login;
  $('in-password').addEventListener('keydown', (e) => { if (e.key === 'Enter') login(); });
  $('btn-logout').onclick = async () => {
    try { await call('POST', '/admin/logout', {}); } catch { /* بی‌اهمیت */ }
    logout();
  };
  $('btn-refresh').onclick = () => refresh().catch(err => alert(err.message));
  $('btn-shop-search').onclick = () => loadShops();
  $('shop-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadShops(); });
  //  پمپ‌بنزین‌ها
  $('btn-pump-search').onclick = () => loadPump();
  $('btn-pu-search').onclick = () => loadPumpUsers();
  //  دکمه بینِ «همه» و «رو به پایان» می‌چرخد تا جای اضافه نگیرد
  $('btn-psub-expiring').onclick = (e) => {
    const showAll = e.target.dataset.mode === 'expiring';
    e.target.dataset.mode = showAll ? '' : 'expiring';
    e.target.textContent = showAll ? 'رو به پایان' : 'همه';
    loadPumpSubs(!showAll);
  };
  $('pump-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadPump(); });
  $('btn-close-pump').onclick = () => {
    $('pump-detail').classList.add('hidden');
    currentStation = null;
  };
  $('btn-pump-grant').onclick = grantPumpSubscription;
  $('btn-pump-suspend').onclick = () => setPumpSubStatus('suspended');
  $('btn-pump-activate').onclick = () => setPumpSubStatus('active');
  $('btn-pump-cancel').onclick = () => {
    if (confirm('اشتراک این پمپ لغو شود؟')) setPumpSubStatus('cancelled');
  };
  $('btn-pvip-make').onclick = makePumpCode;
  $('btn-user-search').onclick = () => loadUsers();
  $('btn-sub-filter').onclick = () => loadSubs();
  $('btn-close-shop').onclick = () => { $('shop-detail').classList.add('hidden'); currentShop = null; };
  $('btn-grant').onclick = grantSubscription;
  $('btn-suspend').onclick = () => setSubStatus('suspended');
  $('btn-activate').onclick = () => setSubStatus('active');
  $('btn-cancel').onclick = () => { if (confirm('اشتراک این دکان لغو شود؟')) setSubStatus('cancelled'); };
  $('btn-save-cfg').onclick = async () => {
    try {
      await call('PATCH', '/admin/config', {
        trial_days: $('cfg-trial').value,
        whatsapp_number: $('cfg-wa').value,
        whatsapp_message: $('cfg-wamsg').value,
        currency: $('cfg-currency').value,
      });
      msg($('cfg-msg'), 'ذخیره شد.', 'ok');
    } catch (err) { msg($('cfg-msg'), err.message, 'bad'); }
  };
  $('btn-backup-now').onclick = async () => {
    msg($('backup-msg'), 'در حال گرفتن پشتیبان…', 'warn');
    try {
      const out = await call('POST', '/admin/backups', { kind: 'manual' });
      msg($('backup-msg'), `پشتیبان ساخته شد (${fa(Math.round(out.bytes / 1024))} کیلوبایت).`, 'ok');
      await loadBackups();
    } catch (err) { msg($('backup-msg'), err.message, 'bad'); }
  };
  for (const tab of document.querySelectorAll('.tab')) {
    tab.onclick = () => openTab(tab.dataset.tab);
  }

  if (token) {
    call('GET', '/admin/me')
      .then(async (out) => {
        $('who').textContent = `${out.admin.name || out.admin.username} — ${out.admin.role === 'superadmin' ? 'مدیر ارشد' : 'مدیر'}`;
        show();
        await refresh();
      })
      .catch(() => logout());
  }
});
