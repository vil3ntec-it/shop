'use strict';
/**
 * پنلِ مدیر — کدِ شش‌رقمیِ دکان، پشتیبانِ هر حساب، پشتیبانی و پیام، ایمیل و تلگرام و پوش. از `app.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان.
 *
 * اسکریپتِ کلاسیک: پس از `app.js` و به ترتیبِ index.html بار می‌شود و ابزارهای همان را به کار می‌برد
 * ($، el، call، msg، fa، dateTime، token…). فقط تعریف دارد؛ هنگامِ بار شدن چیزی اجرا نمی‌شود
 * (دکمه‌ها را `start.js` پس از DOMContentLoaded وصل می‌کند).
 */
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
      phone: $(`${prefix}-phone`) ? $(`${prefix}-phone`).value.trim() : '',   //  فقط پمپ («فقط ایمیل»: VIP ِ دکان بی پیامک)
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
    if ($(`${prefix}-phone`)) $(`${prefix}-phone`).value = '';
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
 * دلیلِ یک دسترسیِ حساس — لغو یا کوتاه‌تر از ۳ نویسه ⇒ null (کاری انجام نمی‌شود).
 */
function askReason(question) {
  const r = prompt(question);
  if (r === null) return null;
  const s = String(r).trim();
  if (s.length < 3) { alert('دلیل لازم است (دست‌کم ۳ نویسه).'); return null; }
  return s.slice(0, 200);
}

/**
 * دانلودِ یک پشتیبان.
 *
 * ⚠️ با یک لینکِ ساده نمی‌شود: مسیر توکنِ مدیر می‌خواهد و مرورگر
 * سرآیندِ `Authorization` را روی لینک نمی‌گذارد. پس فایل را خودمان
 * می‌گیریم و به‌صورتِ `blob` به کاربر می‌دهیم.
 */
async function downloadBackup(app, tenantId, b) {
  //  ⛔ دانلود = خروجیِ کلِ دادهٔ خصوصیِ این حساب ⇒ دلیلِ نوشته؛ فقط مدیرِ ارشد
  //  (سرور می‌سنجد و ردیفِ سابقه می‌نویسد — ADMIN-CONTROL-PLANE.md)
  const reason = askReason('دلیلِ گرفتنِ پشتیبانِ این حساب را بنویسید (در سابقه می‌ماند):');
  if (!reason) return;
  const res = await fetch(`${API}/admin/accounts/${app}/${tenantId}/backups/${b.id}?reason=${encodeURIComponent(reason)}`, {
    headers: { Authorization: `Bearer ${token}` },
  });
  if (!res.ok) {
    let text = 'گرفتنِ فایل نشد';
    try { const j = await res.json(); if (j?.error?.message) text = j.error.message; } catch { /* بدنهٔ غیرِ JSON */ }
    alert(text);
    return;
  }
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
  //  ⚠️ کارتِ تلگرام جدا بار می‌شود: خطای آن نباید ایمیل و پوش را خالی بگذارد
  loadTelegram().catch((err) => msg($('tg-msg'), err.message, 'bad'));
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

/* ==========================================================
   باتِ تلگرامِ پمپ
   ⛔ رمز هرگز برنمی‌گردد — کادرِ خالی یعنی «دست نخورد».
   ========================================================== */
async function loadTelegram() {
  const out = await call('GET', '/admin/telegram');
  const t = out.telegram || out;
  $('tg-enabled').checked = !!t.enabled;
  $('tg-token').value = '';
  $('tg-token').placeholder = t.configured
    ? `${t.tokenHint}${t.fromEnv ? ' (از محیطِ سرور)' : ''} — خالی = دست نخورد`
    : '123456:ABC… — خالی = دست نخورد';
  const link = $('tg-link');
  if (t.link) {
    link.href = t.link;
    link.textContent = `@${t.username}`;
    link.classList.remove('hidden');
  } else {
    link.classList.add('hidden');
  }
  $('tg-state').textContent = !t.configured
    ? '⚠️ رمزِ بات داده نشده — هیچ هشداری به تلگرام نمی‌رود'
    : !t.enabled ? '⚠️ خاموش'
    : `${t.lastError ? `⚠️ ${t.lastError}` : '✅ روشن'} · ${fa(t.chats?.private || 0)} گفت‌وگو · `
      + `${fa(t.chats?.groups || 0)} گروه · ${fa(t.chats?.channels || 0)} کانال · ${fa(t.outbox?.sent24h || 0)} پیام در ۲۴ ساعت`
      + (t.outbox?.pending ? ` · ${fa(t.outbox.pending)} در صف` : '');
}

async function saveTelegram() {
  const node = $('tg-msg');
  try {
    const patch = { enabled: $('tg-enabled').checked };
    const tok = $('tg-token').value.trim();
    if (tok) patch.token = tok;
    const out = await call('PUT', '/admin/telegram', patch);
    msg(node, out.warning || 'ذخیره شد.', out.warning ? 'warn' : 'ok');
    await loadTelegram();
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
