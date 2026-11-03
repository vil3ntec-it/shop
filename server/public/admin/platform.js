'use strict';
/**
 * پنلِ مدیر — کاربران، اشتراک‌ها، درخواست‌های خرید، پلن‌ها، کانالِ آزمایشی، پشتیبان‌ها، سلامتِ سرور، همگام‌سازی و امنیت، سابقه. از `app.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان.
 *
 * اسکریپتِ کلاسیک: پس از `app.js` و به ترتیبِ index.html بار می‌شود و ابزارهای همان را به کار می‌برد
 * ($، el، call، msg، fa، dateTime، token…). فقط تعریف دارد؛ هنگامِ بار شدن چیزی اجرا نمی‌شود
 * (دکمه‌ها را `start.js` پس از DOMContentLoaded وصل می‌کند).
 */
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
      const extra = askOptionalReason(u.status === 'active' ? 'این حساب بسته شود؟' : 'این حساب باز شود؟');
      if (!extra) return;
      await call('POST', `/admin/users/${u.id}/status`,
        { status: u.status === 'active' ? 'disabled' : 'active', ...extra });
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
    ok.onclick = async () => {
      const extra = askOptionalReason('این درخواستِ خرید تأیید شود؟');
      if (!extra) return;
      await call('POST', `/admin/purchase-requests/${r.id}/approve`, extra); await loadRequests(); await loadStats();
    };
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

  loadBeta().catch(err => msg($('beta-msg'), err.message, 'bad'));
}

// ---------- کانالِ آزمایشی (F0-27، فقط مدیرِ ارشد) ----------
let adminRole = '';
function setRole(role) {
  adminRole = role || '';
  $('beta-panel').classList.toggle('hidden', adminRole !== 'superadmin');
}

/** «a, b» یا هر خط یکی ⇒ فهرستِ بی‌تکرار؛ سرور خودش شناسهٔ ناجور را رد می‌کند */
const idList = (text) => [...new Set(String(text || '').split(/[\s,،]+/).map(s => s.trim()).filter(Boolean))];

async function loadBeta() {
  if (adminRole !== 'superadmin') return;
  const out = await call('GET', '/admin/release/beta');
  $('beta-shops').value = out.shops.ids.join('\n');
  $('beta-devices').value = out.devices.ids.join('\n');
  const meta = (m) => `${fa(m.ids.length)} شناسه` +
    (m.updatedAt ? ` — آخرین تغییر ${dateTime(m.updatedAt)}${m.updatedBy ? `، ${m.updatedBy}` : ''}` : '');
  $('beta-shops-meta').textContent = meta(out.shops);
  $('beta-devices-meta').textContent = meta(out.devices);
}

async function saveBeta() {
  const node = $('beta-msg');
  const shops = idList($('beta-shops').value);
  const devices = idList($('beta-devices').value);
  //  همان قاعدهٔ سرور (‎routes/releases.js‎) — پیش از فرستادن، با نامِ شناسهٔ ناجور
  const bad = [...shops, ...devices].filter(x => !/^[A-Za-z0-9_-]{1,80}$/.test(x));
  if (bad.length) return msg(node, `شناسهٔ نامعتبر: ${bad.slice(0, 3).join('، ')} — فقط حروفِ انگلیسی، رقم، - و _`, 'bad');
  try {
    const out = await call('PUT', '/admin/release/beta', {
      shops,
      devices,
      reason: $('beta-reason').value.trim(),
    });
    $('beta-reason').value = '';
    msg(node, out.changed.length ? 'ذخیره شد — از همین حالا اثر دارد.' : 'تغییری نبود.', 'ok');
    await loadBeta();
  } catch (err) { msg(node, err.message, 'bad'); }
}

/** دکمهٔ صفحهٔ دکان: همین دکان به فهرست افزوده می‌شود (بقیه دست نمی‌خورند) */
async function addShopToBeta(id, btn) {
  btn.disabled = true;
  try {
    const cur = await call('GET', '/admin/release/beta');
    if (!cur.shops.ids.includes(id)) {
      await call('PUT', '/admin/release/beta', { shops: [...cur.shops.ids, id], reason: 'از صفحهٔ دکان' });
    }
    btn.textContent = 'در کانالِ آزمایشی است';
  } catch (err) {
    btn.disabled = false;
    btn.textContent = `نشد: ${err.message}`;
  }
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
  showBackupWarning(out.encryption);
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

// ---------- سلامتِ سرور (§۳۸.۸، OPS-01@38) ----------
const HEALTH_FA = { ok: 'سالم', warn: 'هشدار', down: 'خراب', unknown: 'نامعلوم' };
const HEALTH_CLS = { ok: 'b-ok', warn: 'b-warn', down: 'b-bad', unknown: 'b-mute' };
//  وضعیت همیشه متن + رنگ + نقطه — نه فقط رنگ
const healthBadge = (s) => el('span', `badge ${HEALTH_CLS[s] || 'b-mute'}`, `● ${HEALTH_FA[s] || '—'}`);
const numOr = (v) => (v === null || v === undefined ? '—' : fa(v));
function ago(ms) {
  if (ms === null || ms === undefined) return '—';
  if (ms < 60_000) return `${fa(Math.round(ms / 1000))} ثانیه`;
  if (ms < 3_600_000) return `${fa(Math.round(ms / 60_000))} دقیقه`;
  if (ms < 172_800_000) return `${fa(Math.round(ms / 3_600_000))} ساعت`;
  return `${fa(Math.round(ms / 86_400_000))} روز`;
}

/**
 * تأخیرِ همگام‌سازی (OPS-04) — فقط وقتی سرور `lag` بفرستد (پرچمِ `ops.sync_lag` ِ scope ‏`ops`)؛
 * نبودش ⇒ کارت همان پیشین. فقط عدد، بی شناسهٔ دستگاه یا دکان.
 */
function syncLagLine(lag) {
  if (!lag) return '';
  if (lag.status === 'unknown') return ' · تأخیر: نامعلوم';
  return ` · ${numOr(lag.devices)} دستگاهِ فعال، ${numOr(lag.behindDevices)} عقب (بیشینه ${numOr(lag.maxBehindOps)} تغییر)`
    + `، ${numOr(lag.queuedOps)} تغییر در صفِ دستگاه‌ها، ${numOr(lag.stale24h)} بی همگام‌سازی بیش از ۲۴ ساعت`
    + (lag.stuck ? `، ${numOr(lag.stuck)} گیرکرده (آنلاین ولی بی دریافت)` : '')
    + (lag.lastSyncAgeP50Ms == null ? '' : ` · میانهٔ آخرین همگام‌سازی ${ago(lag.lastSyncAgeP50Ms)} پیش`);
}

async function loadSystemHealth() {
  const box = $('sys-health');
  const overall = $('sys-overall');
  box.innerHTML = '';
  let s;
  try { s = await call('GET', '/admin/system/health'); } catch (err) {
    overall.className = 'badge b-mute';
    overall.textContent = '● نامعلوم';
    box.appendChild(el('div', 'muted', err.message));
    return;
  }
  overall.className = `badge ${HEALTH_CLS[s.status] || 'b-mute'}`;
  overall.textContent = `● ${HEALTH_FA[s.status] || '—'}`;
  $('sys-checked').textContent = `بررسی: ${dateTime(s.checkedAt)}`;
  const q = s.queues || {};
  const cards = [
    ['API', s.api, `نسخهٔ ${s.api?.version || '—'} · روشن از ${ago((s.api?.uptimeSeconds ?? 0) * 1000)} پیش`],
    ['دیتابیس', s.database, s.database?.latencyMs == null ? '—' : `${fa(s.database.latencyMs)} میلی‌ثانیه`],
    ['صفِ ایمیلِ ورود', q.email, `${numOr(q.email?.queued)} در صف · ${numOr(q.email?.failed24h)} ناموفق (۲۴ ساعت)`
      + (q.email?.worker === 'down' ? ' · کارگزار خاموش' : '')],
    ['صفِ تلگرام', q.telegram, `${numOr(q.telegram?.pending)} در صف · قدیمی‌ترین ${ago(q.telegram?.oldestAgeMs)}`],
    ['همگام‌سازی (۲۴ ساعت)', s.sync, `${numOr(s.sync?.clientErrors24h)} خطای برنامه · ${numOr(s.sync?.conflicts24h)} تعارض`
      + syncLagLine(s.sync?.lag)],
    ['فضای دیسک', s.storage, s.storage?.freePct == null ? '—' : `${fa(s.storage.freePct)}٪ آزاد`],
    ['پشتیبانِ سرور', s.backups, !s.backups?.enabled ? 'خودکار خاموش است'
      : s.backups?.last ? `آخرین: ${ago(s.backups.ageMs)} پیش${s.backups.encryption ? '' : ' · بی‌رمز'}` : 'هنوز پشتیبانی نیست'],
  ];
  for (const [label, part, line] of cards) {
    const card = el('div', 'stat');
    card.appendChild(el('div', 'l', label));
    const v = el('div', 'v');
    v.appendChild(healthBadge(part?.status));
    card.appendChild(v);
    card.appendChild(el('div', 'l', line));
    box.appendChild(card);
  }
}

// ---------- همگام‌سازی و امنیت ----------
async function loadHealth() {
  loadSystemHealth().catch(() => { /* بخشِ سلامت خودش پیامش را نشان می‌دهد */ });
  const app = $('health-app').value;
  const account = $('health-account').value.trim();
  const q = new URLSearchParams({ app, limit: '200', ...(account ? { account } : {}) });

  const devBody = $('health-devices');
  devBody.innerHTML = '';
  try {
    const out = await call('GET', `/admin/sync/status?${q}`);
    if (!out.devices.length) emptyRow(devBody, 7, 'دستگاهی نیست');
    for (const d of out.devices) {
      const tr = el('tr');
      const lag = Math.max(0, Number(d.head || 0) - Number(d.cursor || 0));
      [d.account_id, d.device_id, d.app_version || '—', dateTime(Number(d.last_seen_at || 0)),
        String(d.queued_count || 0), String(lag), String(d.conflicts || 0)].forEach(t => tr.appendChild(el('td', null, t)));
      //  حسابی که تعارض دارد ⇒ با یک کلیک فهرستِ تعارض‌هایش
      if (Number(d.conflicts || 0) > 0) {
        tr.style.cursor = 'pointer';
        tr.title = 'نمایشِ تعارض‌ها';
        tr.onclick = () => { $('health-account').value = d.account_id; loadHealth(); };
      }
      devBody.appendChild(tr);
    }
  } catch (err) { emptyRow(devBody, 7, err.message); }

  const cBody = $('health-conflicts');
  cBody.innerHTML = '';
  if (!account) emptyRow(cBody, 6, 'شناسهٔ حساب را بنویسید یا روی ردیفِ دستگاهِ دارای تعارض بزنید');
  else {
    try {
      const out = await call('GET', `/admin/sync/conflicts?${q}`);
      if (!out.conflicts.length) emptyRow(cBody, 6, 'تعارضی نیست');
      for (const c of out.conflicts) {
        const tr = el('tr');
        //  مقدارِ بازنده (کنارگذاشته) در برابرِ برنده — مدیر می‌بیند چه چیزی جا ماند
        const val = v => (v === null || v === undefined) ? '—' : JSON.stringify(v).slice(0, 60);
        [dateTime(Number(c.at || 0)), c.table_name, `${c.row_id} · ${c.field}`, c.loser_device || '—',
          `کنار رفت: ${val(c.loser_value)} — ماند: ${val(c.winner_value)}`].forEach(t => tr.appendChild(el('td', null, String(t))));
        const td = el('td');
        const btn = el('button', 'btn btn-ghost btn-sm', 'برگرداندن');
        btn.onclick = async () => {
          //  ⛔ بازگرداندن دلیلِ نوشته می‌خواهد و فقط کارِ مدیرِ ارشد است (سرور می‌سنجد)
          const reason = askReason('نسخهٔ کنارگذاشته‌شدهٔ این ردیف برگردانده شود؟ دلیلش را بنویسید (در سابقه می‌ماند):');
          if (!reason) return;
          try { await call('POST', `/admin/sync/conflicts/${encodeURIComponent(c.id)}/restore`, { reason }); msg($('health-msg'), 'برگردانده شد.', 'ok'); loadHealth(); }
          catch (err) { msg($('health-msg'), err.message, 'bad'); }
        };
        td.appendChild(btn); tr.appendChild(td);
        cBody.appendChild(tr);
      }
    } catch (err) { emptyRow(cBody, 6, err.message); }
  }

  const eBody = $('health-errors');
  eBody.innerHTML = '';
  try {
    const out = await call('GET', `/admin/sync/errors?app=${encodeURIComponent(app)}&limit=100`);
    if (!out.errors.length) emptyRow(eBody, 5, 'خطایی گزارش نشده');
    for (const e of out.errors) {
      const tr = el('tr');
      [dateTime(Number(e.at || 0)), e.app, e.app_version || e.version || '—', e.device_id || '—', (e.message || '').slice(0, 200)]
        .forEach(t => tr.appendChild(el('td', null, String(t))));
      eBody.appendChild(tr);
    }
  } catch (err) { emptyRow(eBody, 5, err.message); }

  const lBody = $('health-logins');
  lBody.innerHTML = '';
  try {
    const out = await call('GET', `/admin/logins?app=${encodeURIComponent(app)}&limit=100`);
    const reqs = (out.requests && out.requests.requests) || out.requests || [];
    if (!reqs.length) emptyRow(lBody, 7, 'درخواستی نیست');
    for (const r of reqs) {
      const tr = el('tr');
      const state = r.locked ? 'قفل (تلاشِ زیاد)' : r.consumed_at ? 'وارد شد' : r.active ? 'در انتظار' : 'منقضی';
      [dateTime(r.created_at), r.app, r.masked_email || '', r.device_name || r.device_id || '—', r.ip || '—',
        `${r.code_attempts}/${r.max_attempts}`, state].forEach(t => tr.appendChild(el('td', null, String(t))));
      if (r.locked || r.code_attempts >= r.max_attempts) tr.style.color = 'var(--bad, #c0392b)';
      lBody.appendChild(tr);
    }
  } catch (err) { emptyRow(lBody, 7, err.message); }
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
