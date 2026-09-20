'use strict';
/**
 * خبر دادن به پنلِ سرورِ خانگی — «چیزی در دفترِ من عوض شد».
 *
 * ── چه اشکالی را می‌بندد ────────────────────────────────────────────────
 * گزارشِ صاحب سامانه: «من توی همون بخش مد نظر استم و هیچی نمیاد؛ باید از
 * اون بخش بیرون بشم یا از برنامه تا دوباره بیام و ببینم.»
 *
 * کدِ ورود، پیامِ پشتیبانی و اشتراک همه در دفترِ **این** سرور می‌نشینند،
 * ولی صفحه‌شان در پنلِ خانگی است. آن پنل از خودش نمی‌داند کِی چیزی این‌جا
 * عوض شد. این چند خط همان را می‌گوید — و همان لحظه، نه با نبضِ کور.
 * ──────────────────────────────────────────────────────────────────────
 *
 * ⛔ **فقط لوکال‌هاست و فقط با رازِ خودِ پنل.** نشانی و راز را پنل موقعِ
 *    بالا آوردنِ این سرور در محیط می‌گذارد (`PANEL_LIVE_URL` / `PANEL_LIVE_KEY`)
 *    و هیچ‌کدام روی دیسک نمی‌نشینند. تنظیم نشده ⇒ این ماژول کاملاً ساکت
 *    است، چون سرورِ حساب می‌تواند بی پنل هم بدود (استقرارِ داکری).
 *
 * ⛔ **هیچ‌وقت چیزی را نمی‌خواباند و هیچ‌وقت منتظر نمی‌ماند.** فراموش‌شدنی
 *    است: پنل که نشنود، دیدبانِ ده‌ثانیه‌ایِ خودش همان را می‌گیرد. پس نه
 *    `await` می‌خواهد، نه `try` دورِ صدا زدنش.
 *
 * ⚠️ و **هیچ داده‌ای نمی‌رود** — فقط نامِ موضوع. نه ایمیل، نه کد، نه
 *    شناسهٔ مشتری. پنل خودش با نشستِ خودش می‌خواند.
 */

const http = require('node:http');

/** رگبار را جمع می‌کند: صد ردیف در یک ثانیه = یک خبر */
const pending = new Map();
const WAIT_MS = 250;

let sent = 0;
let failed = 0;

function target() {
  const url = String(process.env.PANEL_LIVE_URL || '').trim();
  const key = String(process.env.PANEL_LIVE_KEY || '').trim();
  if (!url || !key) return null;
  try {
    const u = new URL(url);
    //  ⛔ بیرون از این کامپیوتر نمی‌رود، هرچه در محیط نوشته شده باشد
    if (u.hostname !== '127.0.0.1' && u.hostname !== 'localhost' && u.hostname !== '::1') return null;
    return { u, key };
  } catch { return null; }
}

function post(topics) {
  const t = target();
  if (!t) return;

  const body = Buffer.from(JSON.stringify({ topics }), 'utf8');
  const req = http.request(
    {
      host: t.u.hostname,
      port: t.u.port || 80,
      path: t.u.pathname,
      method: 'POST',
      timeout: 2000,
      headers: {
        'content-type': 'application/json',
        'content-length': body.length,
        'x-live-key': t.key,
      },
    },
    (res) => { sent++; res.resume(); }
  );
  req.on('timeout', () => req.destroy());
  req.on('error', () => { failed++; });
  req.end(body);
}

/**
 * «این موضوع عوض شد.»
 *
 * @param {string} topic یکی از موضوع‌های گذرگاهِ پنل
 *   (codes · logins · support · customers · plans · notices · sales · sync)
 */
function notifyPanel(topic) {
  const name = String(topic || '').trim();
  if (!name || pending.has(name)) return false;
  if (!target()) return false;

  const timer = setTimeout(() => {
    pending.delete(name);
    post([name]);
  }, WAIT_MS);
  timer.unref?.();
  pending.set(name, timer);
  return true;
}

/** برای آزمون — رگبارهای در راه را همین حالا می‌فرستد */
function flushPanel() {
  const names = [...pending.keys()];
  for (const t of pending.values()) clearTimeout(t);
  pending.clear();
  if (names.length) post(names);
  return names;
}

function panelStats() {
  return { configured: Boolean(target()), sent, failed, pending: pending.size };
}

module.exports = { notifyPanel, flushPanel, panelStats };
