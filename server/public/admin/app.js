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
//  پلن‌های بخشِ پمپ — جدا، چون قیمت و مرزِ دو بخش یکی نیست
let pumpPlans = [];
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
  //  کارتِ کدِ دکان زیرِ همین تب است، پس با همین یکی می‌آید
  loadShopCodes().catch(err => console.error(err));
  fillPlanSelect($('svip-plan'));
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
  //  ⚠️ منتظرش نمی‌مانیم: پشتیبان‌ها از دیسک می‌آیند و نباید باز شدنِ
  //  صفحهٔ دکان را عقب بیندازند
  loadAccountBackups('shop', id, 'shop').catch(err => console.error(err));
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
  /*
   *  ⛔ پلن‌های **پمپ**، نه پلن‌های دکان.
   *
   *  تا دیروز هر دو کادر از `plans`ِ دکان پر می‌شدند، یعنی مدیر
   *  «۶ ماهه — ۲۰۰۰» را می‌دید و `m6` را به یک پمپ می‌داد. آن کد روی
   *  پمپ پلنِ خاموشی با فهرستِ خالی است، و فهرستِ خالی یعنی «پلنِ
   *  کامل» — پس هر اشتراکی که از پنل به پمپ داده می‌شد، عملاً
   *  وی‌آی‌پی بود. قیمتش هم قیمتِ افغانیِ دکان بود، نه دالرِ پمپ.
   */
  if (!pumpPlans.length) {
    try { pumpPlans = (await call('GET', '/admin/plans?app=pump')).plans || []; }
    catch { pumpPlans = []; }
  }
  fillPlanSelect($('pump-plan'), pumpPlans);
  fillPlanSelect($('pvip-plan'), pumpPlans);

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

/**
 *  خبرهای یک پمپ — همان چیزی که برنامه به ابر فرستاده.
 *
 *  ⚠️ فقط **دیدن**. این پنجره برای وقتی است که صاحبِ پمپ می‌گوید «خبر
 *  نگرفتم» و باید معلوم شود خبر به ابر رسیده بود یا نه.
 */
const EVENT_FA = {
  sale: 'فروش', stock_out: 'تمام شد', low_stock: 'کم مانده',
  expense: 'مصرف', debt: 'قرض', note: 'یادداشت',
};

async function loadStationEvents(id) {
  const body = $('pump-events');
  if (!body) return;
  body.innerHTML = '';
  const d = await call('GET', `/admin/pump/stations/${id}/events?limit=50`);
  for (const e of d.events || []) {
    const tr = el('tr');
    tr.appendChild(el('td', null, dateTime(e.at)));
    tr.appendChild(el('td', null, EVENT_FA[e.kind] || e.kind));
    tr.appendChild(el('td', null, e.title || e.body || '—'));
    //  کامپیوترِ پمپ حساب ندارد، پس شناسهٔ دستگاهش نشان داده می‌شود
    tr.appendChild(el('td', 'muted', e.userName || e.deviceUid || '—'));
    body.appendChild(tr);
  }
  if (!(d.events || []).length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'هنوز خبری از برنامهٔ این پمپ نرسیده است');
    td.colSpan = 4;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

async function openStation(id) {
  const d = await call('GET', `/admin/pump/stations/${id}`);
  currentStation = d;
  $('pump-detail').classList.remove('hidden');
  loadAccountBackups('pump', id, 'pump').catch(err => console.error(err));
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

  loadStationEvents(id).catch(err => console.error(err));

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

/*
 *  کدِ پمپ — حالا همان تابعِ مشترکِ پایین را صدا می‌زند.
 *  پیش از این یک کپیِ کامل این‌جا بود و بخشِ دکان اصلاً نداشتش.
 */
const loadPumpCodes = () =>
  loadCodes({ path: '/admin/pump/vip-codes', bodyId: 'pvip-body', msgId: 'pvip-msg' });

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
function fillPlanSelect(sel, list) {
  if (!sel) return;
  const keep = sel.value;
  sel.innerHTML = '';
  for (const p of (list || plans)) {
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
  fillPlanSelect($('svip-plan'));
  renderPlanTable($('plans-body'), plans, 'shop', $('plans-msg'));

  //  و پلن‌های پمپ، در جدولِ خودشان — قیمتِ دالری و مرزِ خودش
  const pout = await call('GET', '/admin/plans?app=pump');
  pumpPlans = pout.plans;
  renderPlanTable($('pump-plans-body'), pumpPlans, 'pump', $('pump-plans-msg'));

  const cfg = out.config || {};
  $('cfg-trial').value = cfg.trial_days || '';
  $('cfg-wa').value = cfg.whatsapp_number || '';
  $('cfg-currency').value = cfg.currency || '';
  $('cfg-pump-currency').value = cfg.pump_currency || '';
  $('cfg-pump-trial').value = cfg.pump_trial_days || '';
  $('cfg-wamsg').value = cfg.whatsapp_message || '';
}

/**
 * جدولِ ویرایشِ پلن‌های یک بخش.
 *
 * ⚠️ `app` در نشانی می‌رود، وگرنه `PATCH /admin/plans/<code>` پیش‌فرضش
 * دکان است و ویرایشِ پلنِ پمپ یا ۴۰۴ می‌گیرد یا — بدتر — پلنِ هم‌کدِ
 * دکان را عوض می‌کند.
 */
function renderPlanTable(body, list, app, msgNode) {
  if (!body) return;
  const UNIT = { day: 'روز', week: 'هفته', month: 'ماه', year: 'سال' };
  body.innerHTML = '';
  for (const p of list) {
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
        await call('PATCH', `/admin/plans/${p.code}?app=${app}`, {
          title: title.value, price: Number(price.value),
          badge: badgeIn.value, active: active.checked,
        });
        msg(msgNode, `پلن ${p.title} ذخیره شد.`, 'ok');
      } catch (err) { msg(msgNode, err.message, 'bad'); }
    };
    tr.appendChild(el('td')).appendChild(save);
    body.appendChild(tr);
  }
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
/* ==========================================================
   کدِ شش‌رقمیِ دکان — قرینهٔ همان چیزی که بخشِ پمپ دارد
   ----------------------------------------------------------
   ⛔ مسیرش (`/admin/vip-codes`) از روزِ اول روی سرور بود ولی هیچ
   دکمه‌ای در پنل نداشت. یعنی دادنِ اشتراک به یک دکان‌دار از پنل
   ممکن نبود مگر با پیدا کردنِ دکانش و تمدیدِ دستی — همان کاری که
   کدِ شش‌رقمی آمده بود جایش را بگیرد.
   ========================================================== */

/**
 * فهرستِ کدهای یک بخش.
 *
 * ⚠️ یک تابع برای هر دو بخش، نه دو کپی: روزی که ستونی اضافه شود یا
 * «باطل کردن» عوض شود، نباید یکی‌اش جا بماند.
 */
async function loadCodes({ path, bodyId, msgId }) {
  const out = await call('GET', `${path}?limit=100`);
  const body = $(bodyId);
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
        try { await call('POST', `${path}/${c.id}/revoke`, {}); await loadCodes({ path, bodyId, msgId }); }
        catch (err) { msg($(msgId), err.message, 'bad'); }
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

async function makeCode({ path, prefix, reload }) {
  const node = $(`${prefix}-msg`);
  try {
    const days = $(`${prefix}-days`).value.trim();
    const out = await call('POST', path, {
      plan: $(`${prefix}-plan`).value,
      days: days ? Number(days) : null,
      email: $(`${prefix}-email`).value.trim(),
      phone: $(`${prefix}-phone`).value.trim(),
      note: $(`${prefix}-note`).value.trim(),
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
    $(`${prefix}-email`).value = '';
    $(`${prefix}-phone`).value = '';
    $(`${prefix}-note`).value = '';
    await reload();
  } catch (err) {
    msg(node, err.message, 'bad');
  }
}

const loadShopCodes = () => loadCodes({ path: '/admin/vip-codes', bodyId: 'svip-body', msgId: 'svip-msg' });

/* ==========================================================
   پشتیبانِ هر حساب
   ----------------------------------------------------------
   ⚠️ با تبِ «پشتیبان‌ها» یکی نیست: آن یکی `pg_dump`ِ کلِ دیتابیس است
   و مالِ صاحبِ سامانه؛ این یکی فایلی است که خودِ برنامهٔ همان دکان یا
   پمپ فرستاده و با آن می‌شود همان یکی را برگرداند.
   ========================================================== */
const KB = (n) => `${fa(Math.round(Number(n || 0) / 1024))} کیلوبایت`;

async function loadAccountBackups(app, tenantId, prefix) {
  const body = $(`${prefix}-bak-body`);
  const stats = $(`${prefix}-bak-stats`);
  body.innerHTML = '';
  let out;
  try {
    out = await call('GET', `/admin/accounts/${app}/${tenantId}/backups`);
  } catch (err) {
    stats.textContent = err.message;
    return;
  }
  stats.textContent = out.backups.length
    ? `${fa(out.stats.count)} نسخه · ${KB(out.stats.usedBytes)} از ${KB(out.stats.quotaBytes)}`
      + ` · سهمِ ${out.stats.paid ? 'اشتراک‌دار' : 'بی‌اشتراک'}`
    : 'این حساب هنوز پشتیبانی نفرستاده است.';

  for (const b of out.backups) {
    const tr = el('tr');
    tr.appendChild(el('td', null, dateTime(b.createdAt)));
    tr.appendChild(el('td', null, KB(b.bytes)));
    tr.appendChild(el('td', null, b.kind === 'manual' ? 'دستی' : 'خودکار'));
    tr.appendChild(el('td', null, b.label || '—'));
    const ver = el('td', null, b.appVersion || '—');
    ver.dir = 'ltr';
    tr.appendChild(ver);

    const act = el('td', 'row');
    const dl = el('button', 'btn btn-ghost btn-sm', 'دانلود');
    dl.onclick = () => downloadBackup(app, tenantId, b);
    act.appendChild(dl);
    const rm = el('button', 'btn btn-danger btn-sm', 'حذف');
    rm.onclick = async () => {
      if (!confirm('این پشتیبان پاک شود؟ برگشت ندارد.')) return;
      try {
        await call('DELETE', `/admin/accounts/${app}/${tenantId}/backups/${b.id}`);
        await loadAccountBackups(app, tenantId, prefix);
      } catch (err) { msg($(`${prefix}-bak-msg`), err.message, 'bad'); }
    };
    act.appendChild(rm);
    tr.appendChild(act);
    body.appendChild(tr);
  }
}

/**
 * دانلودِ یک پشتیبان.
 *
 * ⚠️ با یک لینکِ ساده نمی‌شود: مسیر توکنِ مدیر می‌خواهد و مرورگر
 * سرآیندِ `Authorization` را روی لینک نمی‌گذارد. پس فایل را خودمان
 * می‌گیریم و به‌صورتِ `blob` به کاربر می‌دهیم.
 */
async function downloadBackup(app, tenantId, b) {
  const res = await fetch(`${API}/admin/accounts/${app}/${tenantId}/backups/${b.id}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) { alert('گرفتنِ فایل نشد'); return; }
  const blob = await res.blob();
  const url = URL.createObjectURL(blob);
  const a = el('a');
  a.href = url;
  a.download = b.name;
  document.body.appendChild(a);
  a.click();
  a.remove();
  //  ⚠️ بی این، هر دانلود یک نسخه از فایل را در حافظهٔ تب نگه می‌دارد
  setTimeout(() => URL.revokeObjectURL(url), 10_000);
}

/* ==========================================================
   پشتیبانی و پیامِ همگانی
   ========================================================== */
let currentThread = null;

async function loadSupport() {
  const q = new URLSearchParams({
    app: $('sup-app').value, status: $('sup-status').value, q: $('sup-q').value.trim(),
  });
  const out = await call('GET', `/admin/support/threads?${q}`);
  $('sup-unread').textContent = out.unread ? `${fa(out.unread)} پیامِ نخوانده` : '';

  const body = $('sup-body');
  body.innerHTML = '';
  for (const t of out.threads) {
    const tr = el('tr');
    tr.appendChild(el('td', null, t.accountName || t.who || t.stationName || 'مهمان'));
    tr.appendChild(el('td', null, t.app === 'pump' ? 'پمپ' : 'دکان'));
    tr.appendChild(el('td', null, (t.lastMessage || '').slice(0, 60) || '—'));
    tr.appendChild(el('td', null, t.unreadAdmin ? fa(t.unreadAdmin) : '—'));
    const st = el('td');
    st.appendChild(badge(t.status === 'closed' ? 'cancelled' : t.status === 'pending' ? 'suspended' : 'active'));
    tr.appendChild(st);
    tr.appendChild(el('td', null, dateTime(t.updatedAt)));
    const act = el('td');
    const btn = el('button', 'btn btn-sm', 'باز کردن');
    btn.onclick = () => openThread(t.id).catch(err => alert(err.message));
    act.appendChild(btn);
    tr.appendChild(act);
    body.appendChild(tr);
  }
  if (!out.threads.length) {
    const tr = el('tr');
    const td = el('td', 'muted', 'گفت‌وگویی نیست');
    td.colSpan = 7;
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

const SENDER_FA = { user: '', admin: 'شما', system: 'سامانه' };

async function openThread(id) {
  const out = await call('GET', `/admin/support/threads/${id}`);
  currentThread = out.thread;
  $('sup-detail').classList.remove('hidden');
  $('sup-title').textContent =
    `${out.thread.app === 'pump' ? 'پمپ' : 'دکان'}: `
    + (out.thread.accountName || out.thread.who || out.thread.stationName || 'مهمان')
    + (out.thread.contact ? ` — ${out.thread.contact}` : '');

  const box = $('sup-messages');
  box.innerHTML = '';
  for (const m of out.messages) {
    const line = el('div');
    line.style.cssText = 'padding:8px 10px;border-radius:8px;margin-bottom:6px;'
      + (m.sender === 'user' ? 'background:var(--surface-2);' : 'background:var(--success-tint);');
    const head = el('div', 'muted',
      `${SENDER_FA[m.sender] || m.senderName || ''} · ${dateTime(m.createdAt)}`);
    line.appendChild(head);
    const text = el('div', null, m.body);
    text.style.whiteSpace = 'pre-wrap';
    line.appendChild(text);
    box.appendChild(line);
  }
  box.scrollTop = box.scrollHeight;
  if (!out.messages.length) box.appendChild(el('p', 'muted', 'هنوز پیامی نیست'));
}

async function sendReply() {
  if (!currentThread) return;
  const node = $('sup-msg');
  const body = $('sup-reply').value.trim();
  if (!body) { msg(node, 'پیام خالی است.', 'bad'); return; }
  try {
    await call('POST', `/admin/support/threads/${currentThread.id}/messages`, { body });
    $('sup-reply').value = '';
    msg(node, 'فرستاده شد.', 'ok');
    await Promise.all([openThread(currentThread.id), loadSupport()]);
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function setThreadStatus(status) {
  if (!currentThread) return;
  try {
    await call('POST', `/admin/support/threads/${currentThread.id}/status`, { status });
    await Promise.all([openThread(currentThread.id), loadSupport()]);
  } catch (err) { msg($('sup-msg'), err.message, 'bad'); }
}

async function sendBroadcast() {
  const node = $('bc-msg');
  const body = $('bc-body').value.trim();
  if (!body) { msg(node, 'متنِ پیام خالی است.', 'bad'); return; }
  const app = $('bc-app').value;
  const where = app === 'pump' ? 'پمپ‌بنزین' : app === 'both' ? 'دکان و پمپ' : 'دکان';
  if (!confirm(`این پیام به ${where} فرستاده شود؟`)) return;
  try {
    const out = await call('POST', '/admin/support/broadcast', {
      body, app, target: $('bc-target').value, limit: Number($('bc-limit').value) || 200,
    });
    $('bc-body').value = '';
    msg(node,
      `به ${fa(out.sent)} گیرنده از ${fa(out.targets)} رفت`
      + (out.failed ? ` — ${fa(out.failed)} نرسید.` : '.'),
      out.failed ? 'warn' : 'ok');
    await loadSupport();
  } catch (err) { msg(node, err.message, 'bad'); }
}

/* ==========================================================
   ایمیل و پوش
   ----------------------------------------------------------
   ⛔ تا امروز فقط در اپِ اندرویدِ مدیریت بودند. اگر SMTP تنظیم نبود،
   `provider` روی `log` می‌ماند و کدِ شش‌رقمیِ ثبت‌نام فقط در لاگِ سرور
   چاپ می‌شد — یعنی هیچ‌کس نمی‌توانست ثبت‌نام کند و از پنل هم راهی
   برای درست کردنش نبود.
   ========================================================== */

async function loadDelivery() {
  const [mail, pushCfg] = await Promise.all([
    call('GET', '/admin/email'),
    call('GET', '/admin/push'),
  ]);
  const m = mail.email || mail;
  $('mail-provider').value = m.provider || 'log';
  $('mail-from').value = m.from || '';
  $('mail-fromname').value = m.fromName || '';
  $('mail-subject').value = m.otpSubject || '';
  $('mail-host').value = m.host || '';
  $('mail-port').value = m.port || '';
  $('mail-secure').value = m.secure || 'starttls';
  $('mail-user').value = m.user || '';
  $('mail-url').value = m.url || '';
  //  ⚠️ رمز و کلید هرگز کامل برنمی‌گردند؛ کادر خالی یعنی «دست نخورد»
  $('mail-pass').value = '';
  $('mail-key').value = '';
  /*
   *  ⚠️ «آماده» یعنی ایمیل واقعاً **می‌رود** — و اگر نمی‌رود، سرور
   *  می‌گوید دقیقاً چه چیزی کم است (`missing`)، نه یک «تنظیم نشده»ی
   *  مبهم که مدیر باید حدس بزند.
   */
  $('mail-state').textContent = m.provider === 'log'
    ? '⚠️ روی «فقط لاگ» است — هیچ ایمیلی بیرون نمی‌رود، کدها فقط در لاگِ سرور چاپ می‌شوند'
    : m.ready ? '✅ آماده'
    : `⚠️ ناقص است — ${(m.missing || []).join('، ') || 'تنظیمات کم است'}`;
  if (m.passSet) $('mail-pass').placeholder = `${m.passHint} — خالی = دست نخورد`;
  if (m.keySet) $('mail-key').placeholder = `${m.keyHint} — خالی = دست نخورد`;

  const pc = pushCfg.push || pushCfg;
  $('push-enabled').checked = !!pc.enabled;
  $('push-sa').value = '';
  $('push-state').textContent = !pc.configured
    ? '⚠️ فایلِ حساب سرویس داده نشده — پیام‌ها گم نمی‌شوند ولی زنگ نمی‌زنند'
    : `${pc.enabled ? '✅ روشن' : '⚠️ خاموش'} · ${fa(pc.devices || 0)} دستگاه`
      + (pc.project ? ` · ${pc.project}` : '');
}

async function saveMail() {
  const node = $('mail-msg');
  try {
    const patch = {
      provider: $('mail-provider').value,
      from: $('mail-from').value.trim(),
      fromName: $('mail-fromname').value.trim(),
      otpSubject: $('mail-subject').value.trim(),
      host: $('mail-host').value.trim(),
      port: $('mail-port').value.trim(),
      secure: $('mail-secure').value,
      user: $('mail-user').value.trim(),
      url: $('mail-url').value.trim(),
    };
    //  خالی یعنی «همان قبلی بماند» — وگرنه هر ذخیره رمز را پاک می‌کرد
    if ($('mail-pass').value) patch.pass = $('mail-pass').value;
    if ($('mail-key').value) patch.key = $('mail-key').value;
    await call('PUT', '/admin/email', patch);
    msg(node, 'ذخیره شد.', 'ok');
    await loadDelivery();
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function testMail() {
  const node = $('mail-msg');
  const to = $('mail-test-to').value.trim();
  if (!to) { msg(node, 'ایمیلِ آزمایشی را بنویسید.', 'bad'); return; }
  msg(node, 'در حال فرستادن…', 'warn');
  try {
    const out = await call('POST', '/admin/email/test', { to });
    msg(node, out.ok === false ? `نرفت: ${out.error || ''}` : 'رفت — صندوقتان را ببینید.',
      out.ok === false ? 'bad' : 'ok');
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function savePush() {
  const node = $('push-msg');
  try {
    const patch = { enabled: $('push-enabled').checked };
    const sa = $('push-sa').value.trim();
    if (sa) patch.serviceAccount = sa;
    await call('PUT', '/admin/push', patch);
    msg(node, 'ذخیره شد.', 'ok');
    await loadDelivery();
  } catch (err) { msg(node, err.message, 'bad'); }
}


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
}

async function saveDownloads() {
  const node = $('dl-msg');
  try {
    await call('PUT', '/admin/downloads', {
      app: $('dl-app').value, version: $('dl-version').value.trim(),
      url: $('dl-url').value.trim(), notes: $('dl-notes').value.trim(),
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
      if (!confirm('این پرداخت حذف شود؟')) return;
      try { await call('DELETE', `/admin/payments/${p.id}`); await loadPayments(); }
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
    await call('POST', '/admin/payments', {
      app: $('pay-app').value,
      tenantId: $('pay-tenant').value.trim(),
      subscriptionId: $('pay-sub').value.trim(),
      amount: Number($('pay-amount').value),
      currency: $('pay-currency').value,
      method: $('pay-method').value,
      receiptNo: $('pay-receipt').value.trim(),
      note: $('pay-note').value.trim(),
    });
    msg(node, 'ثبت شد.', 'ok');
    $('pay-amount').value = '';
    $('pay-note').value = '';
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

const LOADERS = {
  shops: loadShops, pump: loadPump, users: loadUsers, subs: loadSubs,
  sales: loadSales, payments: loadPayments, discounts: loadDiscounts, notices: loadNotices,
  requests: loadRequests, plans: loadPlans, support: loadSupport,
  delivery: loadDelivery, backups: loadBackups, audit: loadAudit,
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
  $('btn-pvip-make').onclick = () =>
    makeCode({ path: '/admin/pump/vip-codes', prefix: 'pvip', reload: loadPumpCodes });
  $('btn-svip-make').onclick = () =>
    makeCode({ path: '/admin/vip-codes', prefix: 'svip', reload: loadShopCodes });

  //  پشتیبانی و پیامِ همگانی
  $('btn-sup-search').onclick = () => loadSupport().catch(err => alert(err.message));
  $('sup-q').addEventListener('keydown', (e) => { if (e.key === 'Enter') loadSupport(); });
  $('sup-app').onchange = () => loadSupport();
  $('sup-status').onchange = () => loadSupport();
  $('btn-sup-close').onclick = () => {
    $('sup-detail').classList.add('hidden');
    currentThread = null;
  };
  $('btn-sup-send').onclick = sendReply;
  $('btn-sup-pending').onclick = () => setThreadStatus('pending');
  $('btn-sup-closed').onclick = () => setThreadStatus('closed');
  $('btn-sup-reopen').onclick = () => setThreadStatus('open');
  $('btn-bc-send').onclick = sendBroadcast;

  //  ایمیل و پوش
  $('btn-mail-save').onclick = saveMail;
  $('btn-mail-test').onclick = testMail;
  $('btn-push-save').onclick = savePush;
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
        pump_currency: $('cfg-pump-currency').value,
        pump_trial_days: $('cfg-pump-trial').value,
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

  //  فروش · پرداخت · تخفیف · مرکزِ اعلان
  $('btn-sal-filter').onclick = () => loadSalesSubs().catch(err => msg($('sales-msg'), err.message, 'bad'));
  $('btn-exp-show').onclick = () => loadExpiring().catch(err => msg($('exp-msg'), err.message, 'bad'));
  $('btn-exp-remind').onclick = remindExpiring;
  $('btn-dl-save').onclick = saveDownloads;
  $('dl-app').onchange = () => loadDownloads().catch(() => {});
  $('btn-pay-add').onclick = addPayment;
  $('btn-pay-refresh').onclick = () => loadPayments().catch(err => msg($('pay-msg'), err.message, 'bad'));
  $('btn-dc-make').onclick = makeDiscountCode;
  $('dc-app').onchange = () => fillPlanOptions().catch(() => {});
  $('btn-cmp-make').onclick = makeCampaign;
  $('btn-ph-show').onclick = () => loadPriceHistory().catch(err => alert(err.message));
  $('btn-nt-count').onclick = countAudience;
  $('btn-nt-save').onclick = () => saveNotice().catch(() => {});
  $('btn-nt-preview').onclick = previewNotice;
  $('btn-nt-test').onclick = testNotice;
  $('btn-nt-send').onclick = sendNotice;
  $('btn-nt-list').onclick = () => loadNoticeList().catch(err => msg($('nt-msg'), err.message, 'bad'));
  $('btn-nt-system').onclick = async () => {
    try {
      const out = await call('POST', '/admin/notices/run-system', {});
      msg($('nt-msg'), `${fa(out.checked)} اشتراک سنجیده شد — ${fa(out.expiring)} رو به پایان، ${fa(out.expired)} منقضی.`, 'ok');
      await loadNoticeList();
    } catch (err) { msg($('nt-msg'), err.message, 'bad'); }
  };
  $('nt-template').onchange = () => applyTemplate().catch(err => msg($('nt-msg'), err.message, 'bad'));

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
