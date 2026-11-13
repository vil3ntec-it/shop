'use strict';
/**
 * میزِ مدیر برای دفترِ کدهای یک‌بارمصرف («کدهای شش‌رقمی»: فهرست بی خودِ کد، ارسالِ دوبارهٔ همان کد، نمایشِ
 * ثبت‌شده) — از `lib/otp.js` جدا شد تا زیرِ سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (پوشاندن و
 * بازکردنِ کد، راه‌های ارسال) تزریق می‌شوند. ⛔ «فقط ایمیل» (۱۴۰۵/۰۸/۲۳): ردیفِ کهنهٔ شماره‌ای دوباره فرستاده نمی‌شود.
 */
const { query, one, many, now } = require('../db');

module.exports = ({
  mask, unseal, senders, sender, isEmail, overDailyMax,
}) => {
/**
 * چند ده کدِ آخرِ همین دفتر، با حالشان.
 *
 * ⛔ **خودِ کد در فهرست نمی‌آید** — همان قاعدهٔ `login_requests`. نمایش یک
 * کارِ جدا و ثبت‌شده است (`reveal`)، وگرنه هر تازه شدنِ صفحه یک ردیفِ
 * «کد دیده شد» برای هر مشتری می‌ساخت و آن دفتر بی‌معنا می‌شد.
 */
async function listRequests({ destination = '', app = '', purpose = '', limit = 50 } = {}) {
  const args = [];
  const where = [];
  if (destination) { args.push(String(destination).trim().toLowerCase()); where.push(`lower(destination) = $${args.length}`); }
  //  نصبِ کهنه `app` ندارد، پس ردیفِ بی‌برنامه با فیلترِ برنامه هم می‌آید —
  //  وگرنه کدهای پیش از این مهاجرت برای همیشه ناپدید می‌شدند
  if (app) { args.push(String(app)); where.push(`(app = $${args.length} OR app = '')`); }
  if (purpose) { args.push(String(purpose)); where.push(`purpose = $${args.length}`); }
  args.push(Math.min(200, Number(limit) || 50));

  const rows = await many(
    `SELECT id, purpose, destination, app, attempts, max_attempts, expires_at, consumed_at,
            created_at, ip, via, sent_at, (code_sealed IS NOT NULL) AS has_code
       FROM otp_codes
       ${where.length ? `WHERE ${where.join(' AND ')}` : ''}
      ORDER BY created_at DESC
      LIMIT $${args.length}`,
    args
  );
  return rows.map(shapeRow);
}

/** یک ردیفِ دفتر به شکلی که میزِ مدیر می‌خواند — **بی خودِ کد**. */
function shapeRow(r) {
  const t = now();
  const hasCode = 'has_code' in r ? Boolean(r.has_code) : r.code_sealed != null;
  return {
    id: r.id,
    purpose: r.purpose,
    app: r.app || '',
    destination: r.destination,
    masked_destination: mask(r.destination),
    attempts: Number(r.attempts || 0),
    max_attempts: Number(r.max_attempts || 0),
    created_at: Number(r.created_at),
    expires_at: Number(r.expires_at),
    consumed_at: r.consumed_at ? Number(r.consumed_at) : null,
    sent_at: r.sent_at ? Number(r.sent_at) : null,
    via: r.via || '',
    //  ⛔ `log` یعنی هیچ ایمیلی بیرون نرفته و کد فقط در لاگ چاپ شده
    log_only: (r.via || '') === 'log',
    active: !r.consumed_at && Number(r.expires_at) > t && Number(r.attempts || 0) < Number(r.max_attempts || 0),
    can_reveal: hasCode && !r.consumed_at && Number(r.expires_at) > t,
  };
}

/**
 * فرستادنِ **دوبارهٔ همان کد** — نه کدِ تازه.
 *
 * ⛔ **همان کد، و این عمدی است.** اگر کدِ تازه می‌ساختیم، کدی که همین
 * حالا دستِ مشتری است باطل می‌شد — یعنی مدیر برای کمک کردن، کارِ او را
 * خراب می‌کرد. مهرومومِ روی همان ردیف برای همین دو کار نگه داشته می‌شود.
 *
 * ⛔ **و با رباتِ `log` اصلاً تلاش نمی‌کند.** فرستادنِ دوباره به رباتِ
 * تنظیم‌نشده یعنی یک ردیفِ «فرستادم» و صفر ایمیل — همان «کلکِ دروغ»ی که
 * یک بار کاربر را ساعت‌ها دنبالِ ایمیلی فرستاد که هیچ‌وقت نرفته بود.
 *
 * ⛔ **مهلتِ «ارسالِ دوباره» را هم دست نمی‌زند**: آن مهلت برای جلوگیری از
 * کوبیدنِ دکمه توسطِ **کاربر** است، نه برای خودِ مدیر. و چون کدِ تازه‌ای
 * ساخته نمی‌شود، سقفِ روزانه هم تکان نمی‌خورد.
 *
 * @returns {{ok:true, via:string} | {ok:false, error:string, message:string}}
 */
async function resend(id) {
  const row = await one('SELECT * FROM otp_codes WHERE id=$1', [String(id || '').slice(0, 80)]);
  if (!row) return null;
  //  ⛔ «فقط ایمیل»: کدِ کهنهٔ شماره‌ای (پیش از ۱۴۰۵/۰۸/۲۳) هرگز پیامک نمی‌شود
  if (!isEmail(row.destination)) {
    return { ok: false, error: 'email_only', message: 'کد فقط به ایمیل فرستاده می‌شود؛ این ردیفِ کهنه به شماره بود' };
  }

  const t = now();
  if (row.consumed_at || Number(row.expires_at) <= t) {
    return { ok: false, error: 'code_unavailable', message: 'این کد دیگر زنده نیست — کدِ تازه بفرستید' };
  }
  const code = unseal(row.code_sealed);
  if (!code) {
    return { ok: false, error: 'code_unavailable', message: 'خودِ کد دیگر نگه داشته نشده — کدِ تازه بفرستید' };
  }

  const send = await sender(row.destination);
  if (send === senders.log) {
    return {
      ok: false,
      error: 'delivery_not_configured',
      message: 'رباتِ ارسال تنظیم نیست، پس این کد به دستِ کسی نمی‌رسد. SMTP را تنظیم کنید.',
    };
  }
  if (await overDailyMax(row.destination, t)) {
    return { ok: false, error: 'otp_daily_limit', message: 'امروز درخواست کد برای این نشانی زیاد بوده است' };
  }

  try {
    await send(row.destination, code, `کد ورود شما: ${code}`, { app: row.app || '', purpose: row.purpose || 'login' });
  } catch (err) {
    return { ok: false, error: 'delivery_failed', message: String(err?.message || err).slice(0, 200) };
  }

  const via = (await require('./mailer').current()).provider;
  //  ⚠️ `sent_at` جلو می‌رود تا میزِ مدیر بداند آخرین تلاش کِی بود
  await query('UPDATE otp_codes SET via=$2, sent_at=$3 WHERE id=$1', [row.id, String(via || ''), now()])
    .catch(() => {});
  return { ok: true, via: String(via || '') };
}

/** یک ردیف با شناسه — بی خودِ کد. */
async function requestById(id) {
  const r = await one('SELECT * FROM otp_codes WHERE id=$1', [String(id || '').slice(0, 80)]);
  return r ? shapeRow(r) : null;
}

/**
 * کد را به مدیر نشان بده — برای وقتی ایمیلِ مشتری واقعاً خراب است و باید
 * تلفنی گفته شود.
 *
 * ⛔ فقط کدِ **زنده**، و ثبتِ رخداد کارِ خودِ مسیر است (همان‌جا که
 * `requireSuperAdmin` هم هست) — این تابع هیچ HTTPی نمی‌داند.
 */
async function reveal(id) {
  const row = await one('SELECT * FROM otp_codes WHERE id=$1', [String(id || '').slice(0, 80)]);
  if (!row) return null;
  const t = now();
  const expired = Boolean(row.consumed_at) || Number(row.expires_at) <= t;
  const code = expired ? '' : unseal(row.code_sealed);
  return {
    expired,
    code,
    expires_in: expired ? 0 : Math.max(0, Math.round((Number(row.expires_at) - t) / 1000)),
    request: shapeRow(row),
  };
}

  return { listRequests, requestById, reveal, resend };
};
