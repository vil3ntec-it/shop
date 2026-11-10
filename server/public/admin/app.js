'use strict';
/**
 * پنل مدیریت.
 *
 * توکن مدیر فقط در حافظه‌ی همین صفحه (sessionStorage) می‌ماند و نوعش با
 * توکن کاربران فرق دارد؛ پس با بستن مرورگر از بین می‌رود.
 *
 * زبانه‌ها در پرونده‌های کنار، به همین ترتیب در index.html: `shops.js`، `platform.js`، `support.js`،
 * `commerce.js` و آخر `start.js` (LOADERS و راه‌اندازی). همه اسکریپتِ کلاسیک‌اند و جز `start.js` فقط
 * تعریف دارند؛ پس هنگامِ بار شدن چیزی از پروندهٔ بعدی صدا زده نمی‌شود.
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
async function call(method, path, body, extraHeaders = {}, retried = false) {
  const sent = token;
  const res = await fetch(API + path, {
    method,
    headers: {
      'Content-Type': 'application/json',
      ...(sent ? { Authorization: `Bearer ${sent}` } : {}),
      ...extraHeaders,
    },
    body: body === undefined ? undefined : JSON.stringify(body),
  });
  const text = await res.text();
  let json = null;
  try { json = text ? JSON.parse(text) : null; } catch { json = null; }
  //  ⚠️ ۴۰۱ِ خودِ ورود (رمز یا کدِ نادرست، «کدِ تأیید لازم است») نشست را نمی‌بندد؛ ⛔ ۴۰۱ ِ درخواستِ دیرِ نشستِ پیشین (پس از
  //  خروج و ورودِ دوباره روی شبکهٔ کند) هم نشستِ تازه را نمی‌بندد
  if (res.status === 401 && path !== '/admin/login') { if (token === sent) logout(); throw new Error('نشست تمام شد، دوباره وارد شوید'); }
  if (!res.ok) {
    const err = new Error(json?.error?.message || `خطای ${res.status}`);
    err.code = json?.error?.code || '';
    err.status = res.status;
    //  SEC-AUTH-12: ورودِ دومرحله‌ای اجباری است و این مدیر هنوز روشنش نکرده
    if (err.code === 'admin_2fa_enrollment_required') open2fa(err.message);
    //  SEC-AUTH-13: کارِ حساس ⇒ رمز (و کد) دوباره، بعد همان درخواست یک بارِ دیگر
    if (err.code === 'admin_reauth_required' && !retried && await askReauth()) {
      return call(method, path, body, extraHeaders, true);
    }
    throw err;
  }
  return json;
}

/** پنجرهٔ «تأییدِ دوباره» — true اگر سرور پذیرفت، false با «انصراف». */
let reauthWait = null;
function askReauth() {
  if (reauthWait) return reauthWait;
  reauthWait = new Promise((resolve) => {
    const panel = $('reauth-panel');
    const done = (ok) => {
      panel.classList.add('hidden');
      $('reauth-pass').value = '';
      $('reauth-code').value = '';
      $('btn-reauth-ok').onclick = null;
      $('btn-reauth-cancel').onclick = null;
      reauthWait = null;
      resolve(ok);
    };
    $('btn-reauth-ok').onclick = async () => {
      const body = { password: $('reauth-pass').value };
      const second = asciiDigits($('reauth-code').value);
      if (second) { if (/^\d{6}$/.test(second)) body.totp = second; else body.recovery_code = second; }
      try {
        await call('POST', '/admin/reauth', body, {}, true);
        done(true);
      } catch (err) { msg($('reauth-msg'), err.message, 'bad'); }
    };
    $('btn-reauth-cancel').onclick = () => done(false);
    msg($('reauth-msg'), '', 'muted');
    panel.classList.remove('hidden');
    panel.scrollIntoView({ block: 'center' });
    $('reauth-pass').focus();
  });
  return reauthWait;
}

function open2fa(note) {
  $('twofa-panel').classList.remove('hidden');
  $('twofa-panel').scrollIntoView({ block: 'start' });
  load2fa().then(() => { if (note) msg($('twofa-msg'), note, 'warn'); })
    .catch((err) => msg($('twofa-msg'), err.message, 'bad'));
}

/*
 *  ⛔ F0-14 — کلیدِ یکتایی برای ثبتِ اشتراک.
 *  دو بار کلیک دو دوره می‌ساخت. حالا هر «ثبت» یک کلید دارد: تا بدنه
 *  همان است و ثبت هنوز موفق نشده، همان کلید می‌رود و سرور فقط یک بار
 *  اعطا می‌کند (تکرار ⇒ پاسخِ اول). بعد از موفقیت کلید دور ریخته می‌شود.
 */
const idemKeys = new Map();
function newIdemKey() {
  if (window.crypto && typeof window.crypto.randomUUID === 'function') return window.crypto.randomUUID();
  const rnd = new Uint32Array(4);
  (window.crypto || window.msCrypto).getRandomValues(rnd);
  return `k-${Date.now().toString(36)}-${[...rnd].map(n => n.toString(36)).join('')}`;
}
function idemKeyFor(name, body) {
  const sig = JSON.stringify(body);
  const cur = idemKeys.get(name);
  if (cur && cur.sig === sig) return cur.key;
  const key = newIdemKey();
  idemKeys.set(name, { sig, key });
  return key;
}
/** ثبتِ پولی با کلیدِ یکتایی و قفلِ دکمه تا پاسخ برسد. */
const idemBusy = new Set();
async function callOnce(name, method, path, body) {
  if (idemBusy.has(name)) return null;
  idemBusy.add(name);
  try {
    const out = await call(method, path, body, { 'Idempotency-Key': idemKeyFor(name, body) });
    idemKeys.delete(name);
    return out;
  } finally {
    idemBusy.delete(name);
  }
}

/*
 *  دلیلِ اختیاری (ADMIN-CONTROL-PLANE.md، G2) — فقط در سابقهٔ سرور می‌نشیند.
 *  خالی ⇒ هیچ کلیدی در بدنه نمی‌رود و درخواست همان قبلی است.
 */
function reasonBody(id) {
  const s = String($(id)?.value || '').trim().slice(0, 200);
  return s ? { reason: s } : {};
}
/** پرسشِ تأیید با دلیلِ اختیاری — «لغو» ⇒ null (هیچ کاری نمی‌شود). */
function askOptionalReason(question) {
  const r = prompt(`${question}\nدلیل (اختیاری — فقط در سابقه می‌ماند):`, '');
  if (r === null) return null;
  const s = String(r).trim().slice(0, 200);
  return s ? { reason: s } : {};
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

/** رقم‌های فارسی و عربی ⇒ لاتین (کیبوردِ فارسی ۱۲۳۴۵۶ می‌نویسد) */
const asciiDigits = (s) => String(s || '').replace(/[۰-۹]/g, d => '۰۱۲۳۴۵۶۷۸۹'.indexOf(d))
  .replace(/[٠-٩]/g, d => '٠١٢٣٤٥٦٧٨٩'.indexOf(d)).replace(/\s/g, '');

// ---------- ورود ----------
async function login() {
  const node = $('login-msg');
  try {
    const body = { username: $('in-username').value.trim(), password: $('in-password').value };
    //  F0-15: شش رقم = کدِ برنامهٔ تأیید؛ بقیه = کدِ بازیابی
    const second = asciiDigits($('in-totp').value);
    if (second) { if (/^\d{6}$/.test(second)) body.totp = second; else body.recovery_code = second; }
    const out = await call('POST', '/admin/login', body);
    $('in-totp').value = '';
    $('totp-row').classList.add('hidden');
    token = out.token;
    sessionStorage.setItem(TOKEN_KEY, token);
    $('who').textContent = `${out.admin.name || out.admin.username} — ${out.admin.role === 'superadmin' ? 'مدیر ارشد' : 'مدیر'}`;
    setRole(out.admin.role);
    show();
    //  SEC-AUTH-12: اجباری و هنوز خاموش ⇒ اول روشن کردن؛ بقیهٔ پنل تا آن‌وقت بسته است
    if (out.twoFactor && out.twoFactor.required && !out.twoFactor.enabled) {
      open2fa('ورودِ دومرحله‌ای برای مدیرانِ این سرور اجباری است؛ اول آن را روشن کنید، بعد یک بار خارج و دوباره وارد شوید.');
      return;
    }
    await refresh();
  } catch (err) {
    if (err.code === 'totp_required') {
      $('totp-row').classList.remove('hidden');
      $('in-totp').focus();
      msg(node, err.message, 'warn');
      return;
    }
    if (err.code === 'totp_invalid') $('in-totp').select();
    msg(node, err.message, 'bad');
  }
}

// ---------- ورودِ دومرحله‌ای (F0-15، اختیاری) ----------
async function load2fa() {
  const st = await call('GET', '/admin/2fa');
  $('twofa-state').textContent = st.enabled
    ? `روشن است — ${fa(st.recovery_left)} کدِ بازیابی مانده.`
    : st.enforced
      ? 'خاموش است، ولی روی این سرور اجباری است: تا روشنش نکنید بقیهٔ پنل بسته می‌ماند.'
      : 'خاموش است. اجباری نیست؛ برای مدیرِ سرور پیشنهاد می‌شود.';
  $('btn-2fa-start').classList.toggle('hidden', st.enabled);
  $('btn-2fa-enable').classList.toggle('hidden', st.enabled || $('twofa-setup').classList.contains('hidden'));
  $('btn-2fa-renew').classList.toggle('hidden', !st.enabled);
  $('btn-2fa-disable').classList.toggle('hidden', !st.enabled);
  if (st.enabled) $('twofa-setup').classList.add('hidden');
  return st;
}
function showRecoveryCodes(codes) {
  $('twofa-codes').textContent = (codes || []).join('\n');
  $('twofa-codes-box').classList.toggle('hidden', !(codes || []).length);
}
async function twofa(action) {
  const node = $('twofa-msg');
  const password = $('twofa-pass').value;
  const raw = asciiDigits($('twofa-code').value);
  const code = /^\d{6}$/.test(raw) ? raw : '';
  try {
    if (action === 'start') {
      const out = await call('POST', '/admin/2fa/setup', { password });
      $('twofa-secret').textContent = out.secret.replace(/(.{4})/g, '$1 ').trim();
      $('twofa-link').href = out.otpauth_url;
      $('twofa-setup').classList.remove('hidden');
      showRecoveryCodes([]);
      msg(node, 'کلید ساخته شد؛ هنوز روشن نیست تا کدِ اول را بنویسید.', 'warn');
    } else if (action === 'enable') {
      const out = await call('POST', '/admin/2fa/enable', { code });
      showRecoveryCodes(out.recovery_codes);
      msg(node, 'ورودِ دومرحله‌ای روشن شد.', 'ok');
    } else if (action === 'renew') {
      const out = await call('POST', '/admin/2fa/recovery-codes', { password, code });
      showRecoveryCodes(out.recovery_codes);
      msg(node, 'کدهای بازیابیِ تازه ساخته شد؛ کهنه‌ها دیگر کار نمی‌کنند.', 'ok');
    } else if (action === 'disable') {
      if (!confirm('ورودِ دومرحله‌ای خاموش شود؟')) return;
      await call('POST', '/admin/2fa/disable', code ? { password, code } : { password, recovery_code: raw });
      showRecoveryCodes([]);
      msg(node, 'ورودِ دومرحله‌ای خاموش شد.', 'ok');
    }
    $('twofa-code').value = '';
    await load2fa();
  } catch (err) { msg(node, err.message, 'bad'); }
}

function logout() {
  token = '';
  sessionStorage.removeItem(TOKEN_KEY);
  $('in-totp').value = '';
  $('twofa-pass').value = '';
  $('twofa-panel').classList.add('hidden');
  showRecoveryCodes([]);
  $('app-view').classList.add('hidden');
  $('login-view').classList.remove('hidden');
}

function show() {
  $('login-view').classList.add('hidden');
  $('app-view').classList.remove('hidden');
}

// ---------- تازه‌سازی ----------
async function refresh() {
  await Promise.all([loadStats(), loadShops(), loadPlans(), loadBackupWarning()]);
}

/** F0-08: هشدارِ قرمزِ «پشتیبان رمز ندارد» — بالای همهٔ زبانه‌ها */
function showBackupWarning(encryption) {
  const box = $('backup-crypto-warn');
  const text = encryption && !encryption.configured ? (encryption.warning || 'پشتیبان‌های سرور رمز ندارند.') : '';
  box.textContent = text ? `⚠️ ${text}` : '';
  box.classList.toggle('hidden', !text);
}
async function loadBackupWarning() {
  try { showBackupWarning((await call('GET', '/admin/backups')).encryption); } catch { /* هشدار نباید پنل را بخواباند */ }
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
    //  ⛔ F0-14: همان کلیدِ یکتاییِ بخشِ دکان
    const out = await callOnce('grant-pump', 'POST', '/admin/pump/subscriptions', {
      stationId: currentStation.station.id,
      plan: $('pump-plan').value,
      days: days ? Number(days) : null,
      graceDays: Number($('pump-grace').value || 0),
      ...($('pump-max-devices').value.trim() ? { maxDevices: Number($('pump-max-devices').value) } : {}),
      features: chosenPumpFeatures(),
      note: $('pump-note').value.trim(),
      ...reasonBody('pump-reason'),
    });
    if (!out) return;
    $('pump-reason').value = '';
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
    await call('POST', `/admin/pump/subscriptions/${id}/status`, { status, ...reasonBody('pump-reason') });
    $('pump-reason').value = '';
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
