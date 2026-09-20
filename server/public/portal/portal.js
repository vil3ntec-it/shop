'use strict';
/**
 * پورتالِ مشتری — بخشِ ۱۴.
 *
 * ورود: ایمیل + کدِ شش‌رقمی (`/api/auth/otp/request` و `/otp/verify`)، با
 * انتخابِ برنامه (دکان یا پمپ) تا نشست به بخشِ درست مهر بخورد. بعد از
 * ورود همه‌چیز از `/api/portal/*` می‌آید — با توکن، نه با شناسه‌ای که
 * صفحه بفرستد.
 *
 * توکن در `sessionStorage` است: با بستنِ مرورگر می‌رود.
 */
const API = '/api';
const TOKEN_KEY = 'vill3n-portal-token';
const THEME_KEY = 'vill3n-portal-theme';

const $ = (id) => document.getElementById(id);
const el = (tag, cls, text) => {
  const n = document.createElement(tag);
  if (cls) n.className = cls;
  if (text !== undefined) n.textContent = text;
  return n;
};

let token = sessionStorage.getItem(TOKEN_KEY) || '';
let me = null;

async function call(method, path, body) {
  const res = await fetch(API + path, {
    method,
    headers: { 'Content-Type': 'application/json', ...(token ? { Authorization: `Bearer ${token}` } : {}) },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  if (res.status === 401 && token) { logout(); throw new Error('نشست تمام شد، دوباره وارد شوید'); }
  if (!res.ok) throw new Error(json?.error?.message || `خطای ${res.status}`);
  return json;
}

const fa = (n) => Number(n || 0).toLocaleString('fa-AF');
function date(ms) {
  if (!ms) return '—';
  return new Date(Number(ms)).toLocaleDateString('fa-AF', { year: 'numeric', month: '2-digit', day: '2-digit' });
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
const APP_FA = { shop: 'دکان', pump: 'پمپ‌بنزین' };
const METHOD_FA = { cash: 'نقد', hawala: 'حواله', exchange: 'صرافی' };
const CUR_FA = { AFN: 'افغانی', USD: 'دالر' };

/* ---------- تم ---------- */
function applyTheme(t) {
  if (t) document.documentElement.setAttribute('data-theme', t);
  else document.documentElement.removeAttribute('data-theme');
}
function toggleTheme() {
  const cur = document.documentElement.getAttribute('data-theme')
    || (window.matchMedia('(prefers-color-scheme: dark)').matches ? 'dark' : 'light');
  const next = cur === 'dark' ? 'light' : 'dark';
  try { localStorage.setItem(THEME_KEY, next); } catch { /* حافظهٔ بسته */ }
  applyTheme(next);
}

/* ---------- ورود ---------- */
async function requestCode() {
  const node = $('login-msg');
  const email = $('in-email').value.trim();
  if (!email.includes('@')) { msg(node, 'ایمیل درست نیست.', 'bad'); return; }
  try {
    await call('POST', '/auth/otp/request', { email, app: $('in-app').value });
    $('code-box').classList.remove('hidden');
    $('in-code').focus();
    msg(node, 'کد به ایمیلتان رفت. همان شش رقم را این‌جا بزنید.', 'ok');
  } catch (err) { msg(node, err.message, 'bad'); }
}

async function verifyCode() {
  const node = $('login-msg');
  try {
    const out = await call('POST', '/auth/otp/verify', {
      email: $('in-email').value.trim(),
      code: $('in-code').value.trim(),
      app: $('in-app').value,
      device: { deviceId: deviceId(), name: 'پورتال وب', platform: 'web' },
    });
    token = out.accessToken;
    sessionStorage.setItem(TOKEN_KEY, token);
    show();
    await refresh();
  } catch (err) { msg(node, err.message, 'bad'); }
}

/** شناسهٔ همین مرورگر — تا هر ورود یک «دستگاه» تازه نسازد. */
function deviceId() {
  let id = '';
  try { id = localStorage.getItem('vill3n-portal-device') || ''; } catch { /* بسته */ }
  if (!id) {
    id = `web-${Math.random().toString(36).slice(2, 10)}${Date.now().toString(36)}`;
    try { localStorage.setItem('vill3n-portal-device', id); } catch { /* بسته */ }
  }
  return id;
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

/* ---------- بارگذاری ---------- */
async function refresh() {
  me = await call('GET', '/portal/me');
  $('who').textContent = me.user.name ? `سلام، ${me.user.name}` : (me.user.email || 'پورتال مشتری');
  renderSubs();
  await Promise.all([loadNotices(), loadDownloads()]);
}

function renderSubs() {
  const box = $('subs-list');
  box.innerHTML = '';
  if (!me.memberships.length) {
    const p = el('div', 'panel');
    p.appendChild(el('h2', null, 'هنوز دکان یا پمپی روی این حساب نیست'));
    p.appendChild(el('p', 'muted', 'اول در برنامه دکان یا پمپتان را بسازید؛ بعد این‌جا اشتراک و پرداخت‌ها را می‌بینید.'));
    box.appendChild(p);
    return;
  }
  for (const m of me.memberships) {
    const s = m.subscription;
    const p = el('div', 'panel sub-card');
    const head = el('div', 'sub-head');
    head.appendChild(el('b', null, `${APP_FA[m.app]} — ${m.name || '—'}`));
    const badge = el('span', `badge ${s.color === 'green' ? 'b-ok' : s.color === 'yellow' ? 'b-warn' : 'b-bad'}`, s.label);
    head.appendChild(badge);
    p.appendChild(head);
    if (!s.permanent) {
      const bar = el('div', `bar ${s.color}`);
      const fill = el('i');
      fill.style.width = `${s.progress}%`;
      bar.appendChild(fill);
      p.appendChild(bar);
    }
    const meta = el('div', 'sub-meta');
    meta.appendChild(el('span', null, `پلن: ${s.plan || '—'}`));
    meta.appendChild(el('span', null, `شروع: ${date(s.startsAt)}`));
    meta.appendChild(el('span', null, s.permanent ? 'پایان: ندارد' : `پایان: ${date(s.endsAt)}`));
    if (m.addons && m.addons.length) meta.appendChild(el('span', null, `افزونه‌ها: ${m.addons.map(a => a.feature).join('، ')}`));
    p.appendChild(meta);

    //  Cloud Sync — انتخابِ خودِ مشتری
    const sw = el('label', 'switch');
    const cb = el('input'); cb.type = 'checkbox'; cb.checked = !!m.cloudSync; cb.id = `cloud-${m.app}`;
    cb.onchange = async () => {
      try {
        await call('PUT', '/portal/settings', { app: m.app, cloudSync: cb.checked });
        m.cloudSync = cb.checked;
      } catch (err) { cb.checked = !cb.checked; alert(err.message); }
    };
    sw.appendChild(cb);
    sw.appendChild(el('span', null, 'همگام‌سازیِ ابری (Cloud Sync) — داده‌ام رمزشده روی سرور هم بماند'));
    p.appendChild(sw);

    const acts = el('div', 'row');
    const renew = el('a', 'btn btn-sm', 'تمدید / تماس با پشتیبانی');
    renew.href = '#'; renew.onclick = (e) => { e.preventDefault(); openTab('support'); };
    acts.appendChild(renew);
    p.appendChild(acts);
    box.appendChild(p);
  }
}

async function loadPayments() {
  const out = await call('GET', '/portal/payments');
  const body = $('payments-body');
  body.innerHTML = '';
  for (const p of out.payments) {
    const tr = el('tr');
    tr.appendChild(el('td', null, date(p.paidAt)));
    tr.appendChild(el('td', null, APP_FA[p.app] || p.app));
    tr.appendChild(el('td', null, `${fa(p.amount)} ${CUR_FA[p.currency] || p.currency}`));
    tr.appendChild(el('td', null, METHOD_FA[p.method] || p.method));
    const rn = el('td', null, p.receiptNo || '—'); rn.dir = 'ltr';
    tr.appendChild(rn);
    const td = el('td');
    const a = el('button', 'btn btn-sm', 'رسید');
    a.onclick = () => openReceipt(p.id);
    td.appendChild(a);
    tr.appendChild(td);
    body.appendChild(tr);
  }
  if (!out.payments.length) {
    const tr = el('tr'); const td = el('td', 'muted', 'هنوز پرداختی ثبت نشده است'); td.colSpan = 6; tr.appendChild(td); body.appendChild(tr);
  }
}

/** رسید با توکن گرفته می‌شود (لینکِ ساده توکن ندارد)، بعد در پنجرهٔ تازه باز می‌شود. */
async function openReceipt(id) {
  const res = await fetch(`${API}/portal/payments/${id}/receipt`, { headers: { Authorization: `Bearer ${token}` } });
  const html = await res.text();
  const w = window.open('', '_blank');
  if (!w) { alert('مرورگر پنجرهٔ تازه را بست؛ اجازهٔ پاپ‌آپ بدهید.'); return; }
  w.document.open(); w.document.write(html); w.document.close();
}

async function loadDevices() {
  const out = await call('GET', '/portal/devices');
  const body = $('devices-body');
  body.innerHTML = '';
  for (const d of out.devices) {
    const tr = el('tr');
    tr.appendChild(el('td', null, (d.name || '—') + (d.current ? ' (همین)' : '')));
    tr.appendChild(el('td', null, d.platform || '—'));
    tr.appendChild(el('td', null, dateTime(d.last_seen_at)));
    const st = el('td'); st.appendChild(el('span', `badge ${d.status === 'active' ? 'b-ok' : 'b-bad'}`, d.status === 'active' ? 'فعال' : 'آزادشده'));
    tr.appendChild(st);
    const td = el('td');
    if (d.status === 'active' && !d.current) {
      const b = el('button', 'btn btn-danger btn-sm', 'آزاد کردن');
      b.onclick = async () => {
        if (!confirm('این دستگاه از حساب جدا شود؟')) return;
        try { await call('DELETE', `/portal/devices/${d.id}`); await loadDevices(); }
        catch (err) { msg($('devices-msg'), err.message, 'bad'); }
      };
      td.appendChild(b);
    }
    tr.appendChild(td);
    body.appendChild(tr);
  }
}

async function loadSupport() {
  const out = await call('GET', '/portal/support');
  const box = $('chat-box');
  box.innerHTML = '';
  if (!out.messages.length) box.appendChild(el('p', 'muted', 'هر پرسش یا مشکلی داشتید همین‌جا بنویسید — پاسخ می‌دهیم.'));
  for (const m of out.messages) {
    const b = el('div', `bubble ${m.sender === 'user' ? 'me' : 'them'}`, m.body);
    b.appendChild(el('span', 't', `${m.sender === 'user' ? 'شما' : (m.senderName || 'پشتیبانی')} · ${dateTime(m.createdAt)}`));
    box.appendChild(b);
  }
  box.scrollTop = box.scrollHeight;
}

async function sendChat() {
  const body = $('chat-in').value.trim();
  if (!body) return;
  try {
    await call('POST', '/portal/support/messages', { body });
    $('chat-in').value = '';
    await loadSupport();
  } catch (err) { msg($('chat-msg'), err.message, 'bad'); }
}

async function loadNotices() {
  const out = await call('GET', '/portal/notices');
  const pill = $('notice-count');
  pill.textContent = fa(out.unread);
  pill.classList.toggle('hidden', !out.unread);
  const box = $('notices-list');
  box.innerHTML = '';
  if (!out.notices.length) box.appendChild(el('p', 'muted', 'اعلانی نیست.'));
  for (const n of out.notices) {
    const d = el('div', `notice ${n.status === 'read' ? '' : 'unread'}`);
    d.appendChild(el('b', null, n.title || 'اعلان'));
    d.appendChild(el('div', null, n.body));
    d.appendChild(el('span', 't', dateTime(n.createdAt)));
    if (n.status !== 'read') {
      const b = el('button', 'btn btn-ghost btn-sm', 'خواندم');
      b.style.marginTop = '6px';
      b.onclick = async () => { await call('POST', `/portal/notices/${n.id}/read`); await loadNotices(); };
      d.appendChild(b);
    }
    box.appendChild(d);
  }
}

async function loadDownloads() {
  const out = await call('GET', '/portal/downloads');
  const box = $('downloads-list');
  box.innerHTML = '';
  for (const d of out.downloads) {
    const row = el('div', 'dl');
    const left = el('div');
    left.appendChild(el('b', null, d.title));
    left.appendChild(el('div', 'muted', d.version ? `نسخهٔ ${d.version}` : 'نسخه‌ای ثبت نشده'));
    if (d.notes) left.appendChild(el('div', 'muted', d.notes));
    row.appendChild(left);
    if (d.url) { const a = el('a', 'btn btn-sm', 'دانلود'); a.href = d.url; a.target = '_blank'; a.rel = 'noopener'; row.appendChild(a); }
    else row.appendChild(el('span', 'muted', 'لینک هنوز نیست'));
    box.appendChild(row);
  }
  for (const a of out.apps || []) {
    const row = el('div', 'dl');
    row.appendChild(el('b', null, a.title));
    const l = el('a', 'btn btn-ghost btn-sm', 'باز کردن'); l.href = a.url; l.target = '_blank'; l.rel = 'noopener';
    row.appendChild(l);
    box.appendChild(row);
  }
}

const LOADERS = {
  subs: async () => renderSubs(), payments: loadPayments, devices: loadDevices,
  support: loadSupport, notices: loadNotices, downloads: loadDownloads,
};
function openTab(name) {
  for (const tab of document.querySelectorAll('.tab')) tab.classList.toggle('active', tab.dataset.tab === name);
  for (const key of Object.keys(LOADERS)) $(`tab-${key}`).classList.toggle('hidden', key !== name);
  LOADERS[name]().catch(err => console.error(err));
}

document.addEventListener('DOMContentLoaded', () => {
  try { applyTheme(localStorage.getItem(THEME_KEY) || ''); } catch { /* بسته */ }
  $('btn-theme').onclick = toggleTheme;
  $('btn-request').onclick = requestCode;
  $('btn-verify').onclick = verifyCode;
  $('in-email').addEventListener('keydown', (e) => { if (e.key === 'Enter') requestCode(); });
  $('in-code').addEventListener('keydown', (e) => { if (e.key === 'Enter') verifyCode(); });
  $('btn-logout').onclick = async () => {
    try { await call('POST', '/auth/logout', {}); } catch { /* بی‌اهمیت */ }
    logout();
  };
  $('btn-refresh').onclick = () => refresh().catch(err => alert(err.message));
  $('btn-chat-send').onclick = sendChat;
  $('chat-in').addEventListener('keydown', (e) => { if (e.key === 'Enter') sendChat(); });
  for (const tab of document.querySelectorAll('.tab')) tab.onclick = () => openTab(tab.dataset.tab);
  if (token) {
    show();
    refresh().catch(() => logout());
  }
});
