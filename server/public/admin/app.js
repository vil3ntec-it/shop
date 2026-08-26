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
async function loadPlans() {
  const out = await call('GET', '/admin/plans');
  plans = out.plans;
  const sel = $('in-plan');
  sel.innerHTML = '';
  for (const p of plans) {
    const o = el('option', null, `${p.title} — ${fa(p.price)}`);
    o.value = p.code;
    sel.appendChild(o);
  }
  const custom = el('option', null, 'سفارشی (با روز)');
  custom.value = 'custom';
  sel.appendChild(custom);

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
  shops: loadShops, users: loadUsers, subs: loadSubs,
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
