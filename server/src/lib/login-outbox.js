'use strict';
/**
 * صفِ ارسالِ کدِ ورود و کارگرش — بی BullMQ، بی Redis.
 *
 * ── معنا عیناً همان بخشِ ۳.۴ی پرامپت ─────────────────────────────
 *   • `request-code` فوراً ۲۰۰ می‌دهد؛ ارسال این‌جا و بعداً است
 *   • هر job تا ۴ تلاش، با فاصلهٔ فزایندهٔ ۲ · ۴ · ۸ ثانیه
 *   • هر ارسال ۸ ثانیه سقفِ سخت دارد (AbortController)
 *   • مدارشکن: ۵ خطای پشتِ‌سرِهم باز می‌شود، بعد از ۳۰ ثانیه نیمه‌باز
 *   • حالت‌ها: queued → sending → sent | failed  با `reason`:
 *       email_service_timeout · email_service_error · invalid_recipient
 *       (+ `expired`: کدی که تا نوبتش منقضی شد، فرستاده نمی‌شود)
 *   • `Idempotency-Key: request_id` به سرویسِ ایمیل می‌رود
 *   • شکستِ نهایی ⇒ `alertAdmin`
 *
 * ── صف کجاست ────────────────────────────────────────────────────────
 * جدولِ `otp_outbox` در همان دیتابیس. کارگر یک حلقهٔ درون‌فرآیندی است
 * که ردیف‌های سررسیده را «می‌گیرد» (`UPDATE … WHERE status='queued'
 * RETURNING`) — پس دو کارگر یا دو تیک، یک ردیف را دو بار نمی‌فرستند.
 * سرور که بمیرد، ردیفِ `sending`ِ کهنه بعد از `STALE_MS` دوباره گرفته
 * می‌شود؛ هیچ کدی گم نمی‌شود.
 *
 * ⚠️ کد هیچ‌وقت در لاگ نمی‌آید. `request_id` و ایمیلِ ماسک‌شده کافی است.
 *
 * ⚠️ عددها از محیط خوانده می‌شوند تا سنجه‌ها با مقدارِ کوچک بدوند؛
 * معنا همان است، فقط ساعت تندتر می‌گردد.
 */
const { query, one, many, now } = require('../db');
const { notifyPanel } = require('./panel-live');
const config = require('../config');

const num = (k, d) => { const v = Number(process.env[k]); return Number.isFinite(v) && v > 0 ? v : d; };

const MAX_ATTEMPTS   = 4;
const TIMEOUT_MS     = num('LOGIN_EMAIL_TIMEOUT_MS', 8000);
const BACKOFF_MS     = num('LOGIN_EMAIL_BACKOFF_MS', 2000);
const BREAKER_FAILS  = 5;
const BREAKER_RESET  = num('LOGIN_BREAKER_RESET_MS', 30_000);
const TICK_MS        = num('LOGIN_WORKER_TICK_MS', 300);
const STALE_MS       = num('LOGIN_WORKER_STALE_MS', 20_000);
const CLAIM_LIMIT    = 50;
//  هشدارِ خودکار: بیش از ۳ ارسالِ ناموفق در ۱۰ دقیقه
const ALERT_FAILS    = 3;
const ALERT_WINDOW   = 10 * 60 * 1000;

// ---------- مدارشکن ----------

const breaker = {
  state: 'closed',      // closed | open | half
  failures: 0,
  openedAt: 0,
  /** باز است؟ (و اگر وقتِ نیمه‌باز شدن رسیده، یک تلاش را رد می‌کند) */
  isOpen() {
    if (this.state === 'open' && now() - this.openedAt >= BREAKER_RESET) this.state = 'half';
    return this.state === 'open';
  },
  success() { this.state = 'closed'; this.failures = 0; },
  fail() {
    this.failures += 1;
    if (this.state === 'half' || this.failures >= BREAKER_FAILS) {
      const wasOpen = this.state === 'open';
      this.state = 'open'; this.openedAt = now();
      if (!wasOpen) alert('breaker_open', { failures: this.failures });
    }
  },
  reset() { this.state = 'closed'; this.failures = 0; this.openedAt = 0; },
  snapshot() {
    this.isOpen();
    return { state: this.state, failures: this.failures, opened_at: this.openedAt || null, reset_after_ms: BREAKER_RESET };
  },
};

// ---------- هشدار به مدیر ----------

/** آخرین هشدارها در حافظه — پنل نشانشان می‌دهد. */
const recentAlerts = [];
let alertHandler = null;
/** برای سنجه‌ها و برای وصل کردنِ راهِ دیگرِ خبر (پوش/ایمیلِ جایگزین). */
function setAlertHandler(fn) { alertHandler = typeof fn === 'function' ? fn : null; }

function alert(kind, detail = {}) {
  const entry = { kind, at: now(), ...detail };
  recentAlerts.unshift(entry);
  if (recentAlerts.length > 50) recentAlerts.length = 50;
  //  ایمیل ماسک می‌شود؛ کد اصلاً این‌جا نیست
  console.error(`[login-mail] هشدار: ${kind} ${JSON.stringify(detail)}`);
  require('./audit').log({
    actorType: 'system', action: `login.alert.${kind}`, detail,
  }).catch(() => {});
  if (alertHandler) { try { alertHandler(entry); } catch { /* هشداردهنده خودش خراب بود */ } }
}

// ---------- صف ----------

let started = false;
let timer = null;
let ticking = false;
/** برای سنجه‌ها: چند بار واقعاً به سرویسِ ایمیل زده شد. */
let sendCalls = 0;

async function enqueue({ id, app, email }) {
  const t = now();
  await query(
    `INSERT INTO otp_outbox (id, app, email, status, attempts, max_attempts, next_attempt_at, created_at, updated_at)
     VALUES ($1,$2,$3,'queued',0,$4,$5,$5,$5)
     ON CONFLICT (id) DO UPDATE SET status='queued', attempts=0, next_attempt_at=excluded.next_attempt_at,
       locked_at=NULL, sent_at=NULL, failed_at=NULL, reason='', last_error='', updated_at=excluded.updated_at`,
    [id, app, email, MAX_ATTEMPTS, t]
  );
  //  همان لحظه یک تیک، تا کاربر منتظرِ حلقه نماند
  if (started) setImmediate(() => tick().catch(() => {}));
  return { queued: true };
}

async function statusOf(requestId) {
  const r = await one('SELECT * FROM otp_outbox WHERE id=$1', [requestId]);
  if (!r) return null;
  return {
    state: r.status, reason: r.reason || undefined, attempts: Number(r.attempts),
    sent_at: r.sent_at ? Math.floor(Number(r.sent_at) / 1000) : undefined,
    failed_at: r.failed_at ? Math.floor(Number(r.failed_at) / 1000) : undefined,
    last_error: r.last_error || undefined,
  };
}

/** یک ردیف را می‌گیرد — فقط اگر هنوز در صف باشد. دو بار گرفتن ممکن نیست. */
async function claim(id) {
  const t = now();
  const r = await query(
    `UPDATE otp_outbox SET status='sending', locked_at=$2, attempts=attempts+1, updated_at=$2
      WHERE id=$1 AND (status='queued' OR (status='sending' AND locked_at < $3)) RETURNING *`,
    [id, t, t - STALE_MS]
  );
  return r.rows[0] || null;
}

async function claimDue() {
  const t = now();
  const r = await query(
    `UPDATE otp_outbox SET status='sending', locked_at=$1, attempts=attempts+1, updated_at=$1
      WHERE id IN (
        SELECT id FROM otp_outbox
         WHERE (status='queued' AND next_attempt_at <= $1) OR (status='sending' AND locked_at < $2)
         ORDER BY next_attempt_at ASC LIMIT ${CLAIM_LIMIT})
      RETURNING *`,
    [t, t - STALE_MS]
  );
  return r.rows;
}

function backoffMs(attempt) { return BACKOFF_MS * Math.pow(2, Math.max(0, attempt - 1)); }

/**
 * رفت.
 *
 * ⛔ **`via: 'log'` رفتن نیست.** با راهِ ارسالِ «log» هیچ ایمیلی از این
 * کامپیوتر بیرون نمی‌رود؛ کد فقط در لاگِ سرور چاپ می‌شود. ولی `mailer.send`
 * آن‌جا هم بی استثنا برمی‌گشت، پس ردیف `sent` مهر می‌خورد و میزِ «ورودها»
 * سبزِ پررنگ نشان می‌داد — برای کدی که هیچ‌وقت فرستاده نشده. صاحبِ سامانه
 * دقیقاً همین را دید: برنامه «کد فرستاده شد»، پنل «رفت»، و صندوقِ ایمیل
 * خالی.
 *
 * حالا حالش همان `sent` می‌ماند (چیزی برای تلاشِ دوباره نیست — سرورِ ایمیلی
 * در کار نیست)، ولی `reason` می‌گوید `log_only` و هر جایی که این ردیف را
 * نشان می‌دهد باید همان را بگوید، نه «رفت».
 */
async function markSent(id, reason = '') {
  notifyPanel('logins');
  const t = now();
  await query(
    `UPDATE otp_outbox SET status='sent', sent_at=$2, locked_at=NULL, reason=$3, last_error='', updated_at=$2 WHERE id=$1`,
    [id, t, String(reason || '')]
  );
}
async function markFailed(id, reason, lastError = '') {
  notifyPanel('logins');
  const t = now();
  await query(
    `UPDATE otp_outbox SET status='failed', failed_at=$2, locked_at=NULL, reason=$3, last_error=$4, updated_at=$2 WHERE id=$1`,
    [id, t, reason, String(lastError || '').slice(0, 300)]
  );
}
async function requeue(id, attempt, reason, lastError = '') {
  notifyPanel('logins');
  const t = now();
  await query(
    `UPDATE otp_outbox SET status='queued', next_attempt_at=$2, locked_at=NULL, reason=$3, last_error=$4, updated_at=$5 WHERE id=$1`,
    [id, t + backoffMs(attempt), reason, String(lastError || '').slice(0, 300), t]
  );
}

// ---------- خودِ ارسال ----------

/** قالبِ ایمیل: کد در عنوان، RTL، متنِ ساده هم همراهش. */
function buildMail({ app, email, code, requestId }) {
  const { APPS } = require('./login-codes');
  const name = (APPS[app] || {}).displayName || 'برنامه';
  const spaced = code.split('').join(' ');
  const subject = `کد ورود ${name}: ${code}`;
  const text = [
    `کد ورود ${name}: ${code}`,
    '',
    'این کد ۵ دقیقه اعتبار دارد و فقط یک بار کار می‌کند.',
    'اگر شما درخواست نکرده‌اید، این ایمیل را نادیده بگیرید — هیچ حسابی ساخته نمی‌شود.',
  ].join('\n');
  const html = `<div dir="rtl" style="background:#f8fafc;padding:28px 12px;font-family:Vazirmatn,Tahoma,Arial,sans-serif">
  <div style="max-width:520px;margin:0 auto;background:#fff;border-radius:18px;border:1px solid #e2e8f0;padding:28px 24px;color:#0f172a">
    <h1 style="margin:0 0 12px;font-size:20px">کد ورود ${name}</h1>
    <p style="margin:0 0 8px;font-size:15px;line-height:2;color:#334155">این کد را در برنامه بزنید:</p>
    <div style="margin:24px 0;text-align:center">
      <div dir="ltr" style="display:inline-block;font-size:34px;letter-spacing:10px;font-weight:700;color:#0f172a;background:#f1f5f9;border-radius:14px;padding:16px 26px;font-family:monospace">${spaced}</div>
    </div>
    <div style="font-size:14px;line-height:2;color:#475569">این کد <b>۵ دقیقه</b> اعتبار دارد و فقط یک بار کار می‌کند.</div>
    <p style="margin:20px 0 0;font-size:12px;color:#94a3b8;line-height:2">اگر شما درخواست نکرده‌اید، این ایمیل را نادیده بگیرید — هیچ حسابی ساخته نمی‌شود.</p>
  </div>
</div>`;
  return {
    to: email, subject, text, html, fromName: name,
    replyTo: process.env.EMAIL_REPLY_TO || 'vil3ntec@gmail.com',
    idempotencyKey: requestId,
  };
}

/**
 * یک ردیفِ گرفته‌شده را می‌فرستد و حالتش را می‌نویسد.
 * @returns {'sent'|'failed'|'retry'|'expired'|'skipped'}
 */
async function deliver(row) {
  const codes = require('./login-codes');
  const mailer = require('./mailer');
  const attempt = Number(row.attempts);

  const rec = await codes.requestById(row.id);
  const t = now();
  if (!rec || rec.consumed_at || rec.superseded_at || Number(rec.expires_at) < t) {
    //  کدِ منقضی فرستاده نمی‌شود — ایمیلِ کدِ قدیمی فقط گیج می‌کند
    await markFailed(row.id, 'expired');
    return 'expired';
  }
  const code = codes.unseal(rec.code_sealed);
  if (!code) { await markFailed(row.id, 'expired', 'code_unavailable'); return 'expired'; }

  if (breaker.isOpen()) {
    if (attempt >= Number(row.max_attempts)) {
      await markFailed(row.id, 'email_service_error', 'breaker_open');
      alert('otp_email_failed', { request_id: row.id, app: row.app, email: codes.mask(row.email), reason: 'email_service_error' });
      return 'failed';
    }
    await requeue(row.id, attempt, 'email_service_error', 'breaker_open');
    return 'retry';
  }

  const ctrl = new AbortController();
  const timeout = setTimeout(() => ctrl.abort(), TIMEOUT_MS);
  try {
    sendCalls += 1;
    const out = await mailer.send({ ...buildMail({ app: row.app, email: row.email, code, requestId: row.id }), signal: ctrl.signal });
    breaker.success();
    //  «log» یعنی هیچ ایمیلی بیرون نرفت — همان‌جا نوشته می‌شود، نه اینکه سبزِ ساده بماند
    await markSent(row.id, out && out.via === 'log' ? 'log_only' : '');
    return 'sent';
  } catch (err) {
    if (err && err.invalidRecipient) {
      //  گیرنده غلط است — تلاشِ دوباره فایده ندارد و مدارشکن هم بی‌گناه است
      await markFailed(row.id, 'invalid_recipient', err.message);
      return 'failed';
    }
    breaker.fail();
    const reason = err && (err.name === 'AbortError' || ctrl.signal.aborted) ? 'email_service_timeout' : 'email_service_error';
    if (attempt >= Number(row.max_attempts)) {
      await markFailed(row.id, reason, err && err.message);
      alert('otp_email_failed', { request_id: row.id, app: row.app, email: codes.mask(row.email), reason });
      await checkFailureRate();
      return 'failed';
    }
    await requeue(row.id, attempt, reason, err && err.message);
    return 'retry';
  } finally {
    clearTimeout(timeout);
  }
}

/** بیش از سه شکستِ نهایی در ده دقیقه ⇒ هشدار. */
let lastRateAlert = 0;
async function checkFailureRate() {
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM otp_outbox WHERE status='failed' AND reason <> 'expired' AND failed_at > $1`,
    [now() - ALERT_WINDOW]
  );
  if (r.n > ALERT_FAILS && now() - lastRateAlert > ALERT_WINDOW) {
    lastRateAlert = now();
    alert('otp_failure_rate', { failed_last_10min: r.n });
  }
}

/** یک ردیفِ مشخص — می‌گیرد و می‌فرستد. دو بارِ هم‌زمان = یک ارسال. */
async function processById(id) {
  const row = await claim(id);
  if (!row) return 'skipped';
  return deliver(row);
}

/** یک دورِ کارگر. */
async function tick() {
  if (ticking) return 0;
  ticking = true;
  try {
    const rows = await claimDue();
    if (!rows.length) return 0;
    await Promise.allSettled(rows.map(r => deliver(r).catch(err => {
      console.error('[login-mail] deliver:', err.message);
      return requeue(r.id, Number(r.attempts), 'email_service_error', err.message).catch(() => {});
    })));
    return rows.length;
  } finally {
    ticking = false;
  }
}

function start() {
  if (started) return;
  started = true;
  timer = setInterval(() => tick().catch(err => console.error('[login-mail]', err.message)), TICK_MS);
  if (timer.unref) timer.unref();
}
function stop() {
  started = false;
  if (timer) clearInterval(timer);
  timer = null;
}

/** برای `/health`: `up` یا `down` — بی هیچ دیتابیسی. */
function workerStatus() {
  return started && !breaker.isOpen() ? 'up' : 'down';
}

/** آمارِ تحویل — p50/p95، درصدِ ناموفق، مدارشکن، هشدارها. */
async function stats({ hours = 24 } = {}) {
  const since = now() - hours * 3600 * 1000;
  const rows = await many(
    `SELECT status, reason, created_at, sent_at FROM otp_outbox WHERE created_at > $1 ORDER BY created_at DESC LIMIT 2000`,
    [since]
  );
  const lat = rows.filter(r => r.status === 'sent' && r.sent_at).map(r => Number(r.sent_at) - Number(r.created_at)).sort((a, b) => a - b);
  const pct = (p) => (lat.length ? lat[Math.min(lat.length - 1, Math.floor((p / 100) * lat.length))] : null);
  const failed = rows.filter(r => r.status === 'failed' && r.reason !== 'expired').length;
  const total = rows.filter(r => r.status === 'sent' || (r.status === 'failed' && r.reason !== 'expired')).length;
  const byReason = {};
  for (const r of rows) if (r.status === 'failed') byReason[r.reason || '?'] = (byReason[r.reason || '?'] || 0) + 1;
  return {
    window_hours: hours, total: rows.length, sent: lat.length, failed,
    queued: rows.filter(r => r.status === 'queued').length,
    sending: rows.filter(r => r.status === 'sending').length,
    failure_rate: total ? Math.round((failed / total) * 1000) / 10 : 0,
    p50_ms: pct(50), p95_ms: pct(95), by_reason: byReason,
    breaker: breaker.snapshot(), worker: workerStatus(),
    alerts: recentAlerts.slice(0, 20),
    config: { timeout_ms: TIMEOUT_MS, backoff_ms: BACKOFF_MS, max_attempts: MAX_ATTEMPTS, breaker_fails: BREAKER_FAILS },
  };
}

async function sweep() {
  await query('DELETE FROM otp_outbox WHERE created_at < $1', [now() - 7 * 86400 * 1000]);
}

module.exports = {
  MAX_ATTEMPTS, TIMEOUT_MS, BACKOFF_MS, BREAKER_FAILS, BREAKER_RESET,
  breaker, enqueue, statusOf, claim, deliver, processById, tick, start, stop, workerStatus, stats, sweep,
  setAlertHandler, alert, recentAlerts, buildMail,
  /** برای سنجه‌ها */
  _sendCalls: () => sendCalls, _resetSendCalls: () => { sendCalls = 0; },
};
