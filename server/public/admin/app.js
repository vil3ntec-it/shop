'use strict';
/* پنل مدیریت اشتراک — منطق سمت مرورگر.
   توکن مدیر فقط در sessionStorage می‌ماند تا با بستن مرورگر پاک شود. */

const API = '/api/v1/admin';
const TOKEN_KEY = 'tohid-admin-token';

const $ = (s, r) => (r || document).querySelector(s);
const $$ = (s, r) => Array.from((r || document).querySelectorAll(s));

let FEATURES = [];
let currentUserId = null;
let currentUser = null;
let serverTimeOffset = 0;
let defaultTimezone = 'Asia/Kabul';

// ---------- ابزار ----------
const token = {
  get: () => sessionStorage.getItem(TOKEN_KEY) || '',
  set: (v) => sessionStorage.setItem(TOKEN_KEY, v),
  clear: () => sessionStorage.removeItem(TOKEN_KEY),
};

async function api(path, { method = 'GET', body = null } = {}) {
  const headers = { 'Content-Type': 'application/json' };
  const t = token.get();
  if (t) headers.Authorization = 'Bearer ' + t;

  const res = await fetch(API + path, {
    method, headers, body: body ? JSON.stringify(body) : undefined,
  });
  let data = null;
  try { data = await res.json(); } catch {}
  if (!res.ok) {
    if (res.status === 401) { token.clear(); showLogin(); }
    const e = new Error((data && data.error && data.error.message) || `خطای سرور (${res.status})`);
    e.code = data && data.error && data.error.code;
    throw e;
  }
  return data;
}

function esc(s) {
  return String(s == null ? '' : s).replace(/[&<>"']/g, c =>
    ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
}
function serverNow() { return Date.now() + serverTimeOffset; }
function fmt(ms, tz) {
  if (!ms) return '—';
  try {
    return new Intl.DateTimeFormat('fa-IR', {
      timeZone: tz || defaultTimezone,
      year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit',
    }).format(new Date(ms));
  } catch { return new Date(ms).toISOString().slice(0, 16).replace('T', ' '); }
}
function fmtDate(ms, tz) {
  if (!ms) return '—';
  try {
    return new Intl.DateTimeFormat('fa-IR', {
      timeZone: tz || defaultTimezone, year: 'numeric', month: '2-digit', day: '2-digit',
    }).format(new Date(ms));
  } catch { return new Date(ms).toISOString().slice(0, 10); }
}
function msg(el, text, kind = 'ok') {
  const box = typeof el === 'string' ? $(el) : el;
  if (!box) return;
  box.innerHTML = text ? `<div class="msg msg-${kind}">${esc(text)}</div>` : '';
}
const STATE_BADGE = {
  active: ['فعال', 'b-ok'], grace: ['مهلت تمدید', 'b-warn'], pending: ['شروع نشده', 'b-warn'],
  suspended: ['معلق', 'b-warn'], expired: ['پایان‌یافته', 'b-bad'],
  cancelled: ['لغو شده', 'b-bad'], none: ['بدون اشتراک', 'b-mute'],
};
function stateBadge(state) {
  const [label, cls] = STATE_BADGE[state] || [state, 'b-mute'];
  return `<span class="badge ${cls}">${esc(label)}</span>`;
}
async function guard(btn, fn, msgSel) {
  const old = btn.textContent;
  btn.disabled = true; btn.textContent = 'صبر کنید…';
  try { await fn(); }
  catch (e) { if (msgSel) msg(msgSel, e.message, 'bad'); else alert(e.message); }
  finally { btn.disabled = false; btn.textContent = old; }
}

// ---------- ورود ----------
function showLogin() { $('#view-login').classList.remove('hidden'); $('#view-app').classList.add('hidden'); }
function showApp()   { $('#view-login').classList.add('hidden'); $('#view-app').classList.remove('hidden'); }

$('#btn-login').addEventListener('click', () => {
  guard($('#btn-login'), async () => {
    const username = $('#in-username').value.trim();
    const password = $('#in-password').value;
    if (!username || !password) throw new Error('نام کاربری و رمز عبور را وارد کنید');
    const r = await api('/login', { method: 'POST', body: { username, password } });
    token.set(r.token);
    $('#in-password').value = '';
    await boot();
  }, '#login-msg');
});
$('#in-password').addEventListener('keydown', e => { if (e.key === 'Enter') $('#btn-login').click(); });

$('#btn-logout').addEventListener('click', async () => {
  try { await api('/logout', { method: 'POST' }); } catch {}
  token.clear(); showLogin();
});

// ---------- تب‌ها ----------
$$('.tab').forEach(tab => tab.addEventListener('click', () => {
  const name = tab.dataset.tab;
  $$('.tab').forEach(t => t.classList.toggle('active', t === tab));
  $('#tab-users').classList.toggle('hidden', name !== 'users');
  $('#tab-devices').classList.toggle('hidden', name !== 'devices');
  $('#tab-audit').classList.toggle('hidden', name !== 'audit');
  $('#tab-plans').classList.toggle('hidden', name !== 'plans');
  $('#tab-requests').classList.toggle('hidden', name !== 'requests');
  $('#tab-user-detail').classList.add('hidden');
  if (name === 'devices') loadDevices();
  if (name === 'audit') loadAudit();
  if (name === 'users') loadUsers();
  if (name === 'plans') loadPlans();
  if (name === 'requests') loadRequests();
}));

$('#btn-refresh').addEventListener('click', () => {
  if (!$('#tab-user-detail').classList.contains('hidden')) openUser(currentUserId);
  else { loadStats(); loadUsers(); }
});
$('#btn-back-users').addEventListener('click', () => {
  $('#tab-user-detail').classList.add('hidden');
  $('#tab-users').classList.remove('hidden');
  loadUsers();
});
$('#btn-search').addEventListener('click', loadUsers);
$('#in-search').addEventListener('keydown', e => { if (e.key === 'Enter') loadUsers(); });

// ---------- بارگذاری ----------
async function boot() {
  const me = await api('/me');
  serverTimeOffset = me.serverTime - Date.now();
  defaultTimezone = me.defaultTimezone || 'Asia/Kabul';
  $('#admin-label').textContent = `${me.admin.name || me.admin.username} · ${me.admin.role === 'superadmin' ? 'مدیر ارشد' : 'مدیر'}`;
  showApp();

  const f = await api('/features');
  FEATURES = f.features.filter(x => !x.core);
  renderFeatureGrid();
  await loadTimezones();
  await Promise.all([loadStats(), loadUsers()]);
  startClock();
}

function startClock() {
  const tick = () => {
    try {
      $('#server-clock').textContent = 'زمان سرور: ' + new Intl.DateTimeFormat('fa-IR', {
        timeZone: defaultTimezone, hour: '2-digit', minute: '2-digit', second: '2-digit',
        year: 'numeric', month: '2-digit', day: '2-digit',
      }).format(new Date(serverNow()));
    } catch {}
  };
  tick();
  setInterval(tick, 1000);
}

async function loadStats() {
  const s = await api('/stats');
  serverTimeOffset = s.serverTime - Date.now();
  $('#stats').innerHTML = [
    ['کاربران', s.users],
    ['اشتراک فعال', s.subscriptions.active],
    ['پایان‌یافته', s.subscriptions.expired],
    ['معلق', s.subscriptions.suspended],
    ['دستگاه فعال', s.devices],
  ].map(([l, v]) => `<div class="stat"><div class="l">${l}</div><div class="v">${Number(v).toLocaleString('fa-IR')}</div></div>`).join('');
}

async function loadTimezones() {
  const r = await api('/timezones');
  $('#in-timezone').innerHTML = r.timezones.map(tz =>
    `<option value="${esc(tz.id)}" ${tz.id === r.default ? 'selected' : ''}>${esc(tz.id)} (${tz.localTime})</option>`
  ).join('');
}

async function loadUsers() {
  const q = $('#in-search').value.trim();
  const r = await api('/users?limit=100' + (q ? '&q=' + encodeURIComponent(q) : ''));
  const body = $('#users-body');
  if (!r.users.length) {
    body.innerHTML = '';
    $('#users-empty').textContent = q ? 'کاربری با این مشخصات پیدا نشد.' : 'هنوز کاربری ثبت‌نام نکرده است.';
    return;
  }
  $('#users-empty').textContent = '';
  body.innerHTML = r.users.map(u => {
    const s = u.subscription;
    const feats = (s.features || []).length;
    return `<tr>
      <td><b>${esc(u.name || '—')}</b><br><span class="muted" dir="ltr">${esc(u.id)}</span></td>
      <td dir="ltr">${esc(u.email || u.phone || '—')}</td>
      <td>${stateBadge(s.state)}${u.status === 'disabled' ? ' <span class="badge b-bad">حساب غیرفعال</span>' : ''}</td>
      <td>${s.endsAt ? fmtDate(s.endsAt, s.timezone) : '—'}</td>
      <td>${feats ? feats.toLocaleString('fa-IR') + ' قابلیت' : '<span class="muted">—</span>'}</td>
      <td>${Number(u.deviceCount).toLocaleString('fa-IR')}</td>
      <td><button class="btn btn-sm" data-open-user="${esc(u.id)}">مدیریت</button></td>
    </tr>`;
  }).join('');
  $$('[data-open-user]', body).forEach(b =>
    b.addEventListener('click', () => openUser(b.dataset.openUser)));
}

function renderFeatureGrid() {
  $('#feat-grid').innerHTML = FEATURES.map(f => `
    <label class="feat" data-feat="${esc(f.key)}">
      <input type="checkbox" value="${esc(f.key)}">
      <span>${esc(f.label)}</span>
    </label>`).join('');
  $$('#feat-grid input').forEach(cb => cb.addEventListener('change', () =>
    cb.closest('.feat').classList.toggle('on', cb.checked)));
}
function setFeatures(keys) {
  const set = new Set(keys || []);
  $$('#feat-grid input').forEach(cb => {
    cb.checked = set.has(cb.value);
    cb.closest('.feat').classList.toggle('on', cb.checked);
  });
}
function getFeatures() { return $$('#feat-grid input:checked').map(cb => cb.value); }

$('#btn-feat-all').addEventListener('click', () => setFeatures(FEATURES.map(f => f.key)));
$('#btn-feat-none').addEventListener('click', () => setFeatures([]));

// مدت: پیش‌فرض/دلخواه
$('#in-preset').addEventListener('change', () => {
  const v = $('#in-preset').value;
  $('#custom-amount-row').classList.toggle('hidden', v !== 'custom-amount');
  $('#end-date-wrap').classList.toggle('hidden', v !== 'custom-dates');
});

function durationInput() {
  const v = $('#in-preset').value;
  if (v === 'custom-dates') {
    const endDate = $('#in-end-date').value;
    if (!endDate) throw new Error('تاریخ پایان را انتخاب کنید');
    return { endDate };
  }
  if (v === 'custom-amount') {
    const amount = Number($('#in-amount').value);
    if (!Number.isInteger(amount) || amount < 1) throw new Error('مدت باید عدد صحیح مثبت باشد');
    return { amount, unit: $('#in-unit').value };
  }
  const [amount, unit] = v.split(':');
  return { amount: Number(amount), unit };
}

// ---------- جزئیات کاربر ----------
async function openUser(id) {
  currentUserId = id;
  const d = await api('/users/' + encodeURIComponent(id));
  currentUser = d;
  serverTimeOffset = d.serverTime - Date.now();

  $('#tab-users').classList.add('hidden');
  $('#tab-devices').classList.add('hidden');
  $('#tab-audit').classList.add('hidden');
  $('#tab-user-detail').classList.remove('hidden');

  const u = d.user, s = d.subscription;
  $('#ud-name').textContent = u.name || u.email || u.phone || u.id;
  $('#ud-status-badge').innerHTML = stateBadge(s.state) +
    (u.status === 'disabled' ? ' <span class="badge b-bad">حساب غیرفعال</span>' : '');
  $('#btn-toggle-user').textContent = u.status === 'disabled' ? 'فعال کردن کاربر' : 'غیرفعال کردن کاربر';

  $('#ud-summary').innerHTML = [
    ['ایمیل', u.email || '—'], ['موبایل', u.phone || '—'],
    ['شناسه', u.id], ['ثبت‌نام', fmtDate(u.createdAt)],
    ['آخرین ورود', u.lastLoginAt ? fmt(u.lastLoginAt) : 'هرگز'],
    ['دستگاه فعال', d.devices.filter(x => x.status === 'active').length.toLocaleString('fa-IR')],
  ].map(([l, v]) => `<div class="stat"><div class="l">${esc(l)}</div><div class="v" style="font-size:13px;" dir="auto">${esc(v)}</div></div>`).join('');

  // فرم را با اشتراک فعلی پر کن
  const live = d.subscriptions.find(x => x.status === 'active' || x.status === 'suspended');
  if (live) {
    $('#in-plan').value = live.plan;
    $('#in-max-devices').value = live.max_devices;
    $('#in-grace').value = live.grace_days;
    $('#in-ttl').value = live.license_ttl_days == null ? '' : live.license_ttl_days;
    $('#in-note').value = live.note || '';
    setFeatures(live.features);
    const opt = $$('#in-timezone option').find(o => o.value === live.timezone);
    if (opt) $('#in-timezone').value = live.timezone;
  } else {
    setFeatures([]);
    $('#in-note').value = '';
  }
  if (!$('#in-start-date').value) {
    $('#in-start-date').value = new Date(serverNow()).toISOString().slice(0, 10);
  }

  renderCurrentSub(live, s);
  renderDevices(d.devices);
  renderLicenses(d.licenses);
  msg('#sub-msg', ''); msg('#cur-msg', '');
}

function renderCurrentSub(live, evaluated) {
  const box = $('#current-sub');
  if (!live) {
    box.innerHTML = '<p class="muted">برای این کاربر اشتراکی ثبت نشده است.</p>';
    $$('#current-sub-panel .row button').forEach(b => b.disabled = true);
    return;
  }
  $$('#current-sub-panel .row button').forEach(b => b.disabled = false);
  const rows = [
    ['وضعیت', STATE_BADGE[evaluated.state] ? STATE_BADGE[evaluated.state][0] : evaluated.state],
    ['طرح', live.plan],
    ['شروع', fmtDate(live.starts_at, live.timezone)],
    ['پایان', fmtDate(live.ends_at, live.timezone)],
    ['منطقه زمانی', live.timezone],
    ['سقف دستگاه', String(live.max_devices)],
    ['مهلت پس از پایان', live.grace_days + ' روز'],
    ['اعتبار آفلاین مجوز', live.license_ttl_days == null ? 'تا پایان اشتراک' : live.license_ttl_days + ' روز'],
    ['قابلیت‌ها', live.features.length ? live.features.join('، ') : 'هیچ‌کدام'],
  ];
  box.innerHTML = '<div class="table-scroll"><table><tbody>' + rows.map(r =>
    `<tr><td style="color:var(--muted);width:170px;">${esc(r[0])}</td><td><b>${esc(r[1])}</b></td></tr>`
  ).join('') + '</tbody></table></div>';
  box.dataset.subId = live.id;
}

function renderDevices(devices) {
  const body = $('#ud-devices');
  if (!devices.length) {
    body.innerHTML = '<tr><td colspan="6" class="muted">هنوز دستگاهی ثبت نشده است.</td></tr>';
    return;
  }
  body.innerHTML = devices.map(d => `<tr>
    <td><b>${esc(d.name)}</b><br><span class="muted" dir="ltr">${esc(String(d.device_uid).slice(0, 16))}…</span></td>
    <td>${d.status === 'active' ? '<span class="badge b-ok">فعال</span>' : '<span class="badge b-bad">لغو شده</span>'}</td>
    <td>${fmtDate(d.created_at)}</td>
    <td>${d.last_seen_at ? fmt(d.last_seen_at) : '—'}</td>
    <td>${d.last_sync_at ? fmt(d.last_sync_at) : '—'}</td>
    <td>
      ${d.status === 'active'
        ? `<button class="btn btn-danger btn-sm" data-revoke="${esc(d.id)}">لغو دسترسی</button>`
        : `<button class="btn btn-ghost btn-sm" data-restore="${esc(d.id)}">بازگرداندن</button>`}
      <button class="btn btn-ghost btn-sm" data-del-device="${esc(d.id)}">حذف</button>
    </td>
  </tr>`).join('');

  $$('[data-revoke]', body).forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/devices/${b.dataset.revoke}/revoke`, { method: 'POST' }); await openUser(currentUserId); })));
  $$('[data-restore]', body).forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/devices/${b.dataset.restore}/restore`, { method: 'POST' }); await openUser(currentUserId); })));
  $$('[data-del-device]', body).forEach(b => b.addEventListener('click', () => {
    if (!confirm('این دستگاه برای همیشه حذف شود؟')) return;
    guard(b, async () => { await api(`/devices/${b.dataset.delDevice}`, { method: 'DELETE' }); await openUser(currentUserId); });
  }));
}

function renderLicenses(licenses) {
  const body = $('#ud-licenses');
  if (!licenses.length) {
    body.innerHTML = '<tr><td colspan="7" class="muted">هنوز License صادر نشده است.</td></tr>';
    return;
  }
  const badge = { active: ['فعال', 'b-ok'], superseded: ['جایگزین‌شده', 'b-mute'], revoked: ['باطل‌شده', 'b-bad'] };
  body.innerHTML = licenses.map(l => {
    const [lbl, cls] = badge[l.status] || [l.status, 'b-mute'];
    return `<tr>
      <td dir="ltr">${esc(String(l.id).slice(0, 14))}…</td>
      <td>${esc(l.device_name || '—')}</td>
      <td>${fmt(l.issued_at)}</td>
      <td>${fmtDate(l.ends_at)}</td>
      <td>${l.features.length ? l.features.length.toLocaleString('fa-IR') : '۰'}</td>
      <td><span class="badge ${cls}">${lbl}</span></td>
      <td>${l.status === 'active' ? `<button class="btn btn-danger btn-sm" data-revoke-lic="${esc(l.id)}">ابطال</button>` : ''}</td>
    </tr>`;
  }).join('');
  $$('[data-revoke-lic]', body).forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/licenses/${b.dataset.revokeLic}/revoke`, { method: 'POST' }); await openUser(currentUserId); })));
}

// ---------- عملیات اشتراک ----------
$('#btn-save-sub').addEventListener('click', () => {
  guard($('#btn-save-sub'), async () => {
    const dur = durationInput();
    const ttl = $('#in-ttl').value.trim();
    const body = Object.assign({
      plan: $('#in-plan').value.trim() || 'custom',
      startDate: $('#in-start-date').value || undefined,
      timezone: $('#in-timezone').value,
      features: getFeatures(),
      maxDevices: Number($('#in-max-devices').value) || 1,
      graceDays: Number($('#in-grace').value) || 0,
      licenseTtlDays: ttl === '' ? null : Number(ttl),
      note: $('#in-note').value.trim(),
    }, dur);
    await api(`/users/${encodeURIComponent(currentUserId)}/subscription`, { method: 'POST', body });
    msg('#sub-msg', 'اشتراک ثبت شد.', 'ok');
    await openUser(currentUserId);
    await loadStats();
  }, '#sub-msg');
});

function currentSubId() {
  const id = $('#current-sub').dataset.subId;
  if (!id) throw new Error('اشتراکی برای این کاربر ثبت نشده است');
  return id;
}

$('#btn-renew').addEventListener('click', () => {
  guard($('#btn-renew'), async () => {
    const [amount, unit] = $('#in-renew-preset').value.split(':');
    await api(`/subscriptions/${currentSubId()}/renew`, { method: 'POST', body: { amount: Number(amount), unit } });
    msg('#cur-msg', 'اشتراک تمدید شد.', 'ok');
    await openUser(currentUserId); await loadStats();
  }, '#cur-msg');
});

[['#btn-suspend', 'suspended', 'اشتراک معلق شد.'],
 ['#btn-activate', 'active', 'اشتراک فعال شد.'],
 ['#btn-cancel-sub', 'cancelled', 'اشتراک لغو شد.']].forEach(([sel, status, okMsg]) => {
  $(sel).addEventListener('click', () => {
    if (status === 'cancelled' && !confirm('اشتراک این کاربر لغو شود؟ اطلاعات کاربر حذف نمی‌شود.')) return;
    guard($(sel), async () => {
      await api(`/subscriptions/${currentSubId()}/status`, { method: 'POST', body: { status } });
      msg('#cur-msg', okMsg, 'ok');
      await openUser(currentUserId); await loadStats();
    }, '#cur-msg');
  });
});

$('#btn-toggle-user').addEventListener('click', () => {
  guard($('#btn-toggle-user'), async () => {
    const next = currentUser.user.status === 'disabled' ? 'active' : 'disabled';
    await api(`/users/${encodeURIComponent(currentUserId)}/status`, { method: 'POST', body: { status: next } });
    await openUser(currentUserId);
  });
});

// ---------- تب دستگاه‌ها و سابقه ----------
async function loadDevices() {
  const r = await api('/devices?limit=200');
  const body = $('#devices-body');
  if (!r.devices.length) { body.innerHTML = '<tr><td colspan="6" class="muted">دستگاهی ثبت نشده است.</td></tr>'; return; }
  body.innerHTML = r.devices.map(d => `<tr>
    <td><b>${esc(d.user_name || '—')}</b><br><span class="muted" dir="ltr">${esc(d.user_email || d.user_phone || '')}</span></td>
    <td>${esc(d.name)}<br><span class="muted" dir="ltr">${esc(String(d.device_uid).slice(0, 14))}…</span></td>
    <td>${d.status === 'active' ? '<span class="badge b-ok">فعال</span>' : '<span class="badge b-bad">لغو شده</span>'}</td>
    <td>${d.last_seen_at ? fmt(d.last_seen_at) : '—'}</td>
    <td>${d.last_sync_at ? fmt(d.last_sync_at) : '—'}</td>
    <td>${d.status === 'active' ? `<button class="btn btn-danger btn-sm" data-grevoke="${esc(d.id)}">لغو دسترسی</button>` : ''}</td>
  </tr>`).join('');
  $$('[data-grevoke]', body).forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/devices/${b.dataset.grevoke}/revoke`, { method: 'POST' }); await loadDevices(); })));
}

async function loadAudit() {
  const r = await api('/audit?limit=200');
  const body = $('#audit-body');
  if (!r.entries.length) { body.innerHTML = '<tr><td colspan="5" class="muted">سابقه‌ای ثبت نشده است.</td></tr>'; return; }
  body.innerHTML = r.entries.map(e => `<tr>
    <td>${fmt(e.created_at)}</td>
    <td>${esc(e.actor_type)}<br><span class="muted" dir="ltr">${esc(String(e.actor_id).slice(0, 14))}</span></td>
    <td><code>${esc(e.action)}</code></td>
    <td dir="ltr">${esc(String(e.target_id).slice(0, 18))}</td>
    <td style="white-space:normal;max-width:340px;"><span class="muted">${esc(String(e.detail).slice(0, 200))}</span></td>
  </tr>`).join('');
}

// ---------- پلن‌ها و قیمت‌ها ----------
async function loadPlans() {
  const r = await api('/plans');
  const cfg = r.config || {};
  $('#cfg-trial').value = cfg.trial_days ?? 7;
  $('#cfg-wa').value = cfg.whatsapp_number ?? '';
  $('#cfg-currency').value = cfg.currency ?? 'افغانی';
  $('#cfg-wamsg').value = cfg.whatsapp_message ?? '';

  const body = $('#plans-body');
  body.innerHTML = r.plans.map(p => `<tr data-code="${esc(p.code)}">
    <td><code>${esc(p.code)}</code></td>
    <td>${esc(p.title)}</td>
    <td>${p.amount ? `${esc(String(p.amount))} ${esc(unitLabel(p.unit))}` : '<span class="muted">—</span>'}</td>
    <td><input type="number" class="p-price" value="${p.price}" min="0" style="width:110px;"></td>
    <td>${p.pricePerDay == null ? '<span class="muted">—</span>' : esc(String(p.pricePerDay))}</td>
    <td><input type="text" class="p-badge" value="${esc(p.badge)}" style="width:120px;"></td>
    <td><input type="checkbox" class="p-active" ${p.active ? 'checked' : ''}></td>
    <td><button class="btn btn-sm" data-save-plan>ذخیره</button></td>
  </tr>`).join('');

  $$('#plans-body [data-save-plan]').forEach(btn => btn.addEventListener('click', () => {
    const tr = btn.closest('tr');
    guard(btn, async () => {
      await api(`/plans/${tr.dataset.code}`, { method: 'PATCH', body: {
        price: Number($('.p-price', tr).value) || 0,
        badge: $('.p-badge', tr).value,
        active: $('.p-active', tr).checked,
      } });
      msg('#plans-msg', 'ذخیره شد.', 'ok');
      await loadPlans();
    }, '#plans-msg');
  }));
}

function unitLabel(u) {
  return { day: 'روز', week: 'هفته', month: 'ماه', year: 'سال' }[u] || '';
}

$('#btn-save-cfg').addEventListener('click', () => {
  guard($('#btn-save-cfg'), async () => {
    await api('/config', { method: 'PATCH', body: {
      trial_days: Number($('#cfg-trial').value) || 0,
      whatsapp_number: $('#cfg-wa').value.trim(),
      currency: $('#cfg-currency').value.trim(),
      whatsapp_message: $('#cfg-wamsg').value.trim(),
    } });
    msg('#cfg-msg', 'تنظیمات ذخیره شد.', 'ok');
  }, '#cfg-msg');
});

// ---------- درخواست‌های خرید ----------
async function loadRequests() {
  const r = await api('/purchase-requests');
  const body = $('#requests-body');
  if (!r.requests.length) {
    body.innerHTML = '<tr><td colspan="5" class="muted">درخواستی ثبت نشده است.</td></tr>';
    return;
  }
  const badge = { pending: ['در انتظار', 'b-warn'], approved: ['تأیید شده', 'b-ok'], rejected: ['رد شده', 'b-bad'] };
  body.innerHTML = r.requests.map(q => {
    const [lbl, cls] = badge[q.status] || [q.status, 'b-mute'];
    return `<tr>
      <td><b>${esc(q.user_name || '—')}</b><br><span class="muted" dir="ltr">${esc(q.user_email || q.user_phone || '')}</span></td>
      <td><code>${esc(q.plan_code)}</code></td>
      <td>${fmt(q.created_at)}</td>
      <td><span class="badge ${cls}">${lbl}</span></td>
      <td>${q.status === 'pending'
        ? `<button class="btn btn-sm" data-approve="${esc(q.id)}">تأیید</button>
           <button class="btn btn-ghost btn-sm" data-reject="${esc(q.id)}">رد</button>` : ''}</td>
    </tr>`;
  }).join('');

  $$('#requests-body [data-approve]').forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/purchase-requests/${b.dataset.approve}/approve`, { method: 'POST' }); await loadRequests(); })));
  $$('#requests-body [data-reject]').forEach(b => b.addEventListener('click', () =>
    guard(b, async () => { await api(`/purchase-requests/${b.dataset.reject}/reject`, { method: 'POST' }); await loadRequests(); })));
}

// ---------- شروع ----------
(async () => {
  if (!token.get()) { showLogin(); return; }
  try { await boot(); } catch { token.clear(); showLogin(); }
})();
