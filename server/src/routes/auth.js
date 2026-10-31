'use strict';
/**
 * ورود و ثبت‌نام.
 *
 * سه راه ورود پشتیبانی می‌شود و هر سه به یک user_id می‌رسند:
 *   ۱) شماره + کد یک‌بارمصرف
 *   ۲) حساب گوگل
 *   ۳) ایمیل یا شماره + رمز عبور
 *
 * ثبت‌نامِ تازه سه پله دارد و شماره‌ی موبایل نمی‌خواهد — `/register/start`،
 * `/register/verify` و `/register/complete`. مسیر قدیمیِ `/register`
 * سر جایش است تا نسخه‌های قدیمیِ برنامه از کار نیفتند.
 *
 * ایمیل و شماره فقط «راه ورود»اند؛ شناسه‌ی اصلی همیشه user_id است.
 * حساب‌ها هرگز خودبه‌خود در هم ادغام نمی‌شوند.
 */
const express = require('express');
const { query, one, tx, newId, now } = require('../db');
const config = require('../config');
const v = require('../lib/validate');
const pw = require('../lib/password');
const tokens = require('../lib/tokens');
const otp = require('../lib/otp');
const google = require('../lib/google');
const geo = require('../lib/geo');
const loginAlerts = require('../lib/login-alerts');
const terms = require('../lib/terms');
const audit = require('../lib/audit');
const reauth = require('../lib/reauth');
const { membershipOf } = require('../lib/shops');
const staffCodes = require('../lib/staff-codes');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireAnyUser } = require('../middleware/auth');
const { badRequest, unauthorized, forbidden, conflict, tooMany, notFound } = require('../middleware/errors');

const router = express.Router();

const authLimit = rateLimit({ max: config.rateLimit.authMax, keyPrefix: 'auth' });
/*
 *  تازه‌سازی و خروج — تا دیروز هیچ سقفی نداشتند.
 *
 *  هر دو بی‌توکن‌اند (توکن در بدنه می‌آید) و هر دو به دیتابیس می‌زنند،
 *  پس یک حلقهٔ ساده می‌توانست سرور را مشغول نگه دارد بی‌آنکه حتی حسابی
 *  داشته باشد. حدس زدنِ خودِ توکن شدنی نیست — ۳۲ بایتِ تصادفی است — ولی
 *  «شدنی نیست» جوابِ «چرا سقف ندارد» نیست.
 *
 *  سقف عمداً از ورود بازتر است: یک گوشی ساعتی یک بار تازه می‌کند و یک
 *  دکانِ چند‌گوشی‌ای پشتِ یک اینترنت نباید به هم بخورد.
 */
const sessionLimit = rateLimit({ max: config.rateLimit.sessionMax, keyPrefix: 'session' });
//  کد شاگرد خودش رمز است، پس مثل رمز محدود می‌شود
const joinLimit = rateLimit({ max: config.rateLimit.joinMax, keyPrefix: 'staff-login' });
/**
 * کلیدِ سطلِ کد همان مقصد است — شماره یا ایمیل. تا دیروز فقط شماره بود، پس ثبت‌نامِ ایمیلی روی IP
 * جمع می‌شد و یک اینترنت مشترک (کافی‌نت، دکانِ چند نفره) همه را با هم می‌بست.
 * شماره با کلیدِ یکتا (RSK-07): ‎0700…‎، ‎+93700…‎، ‎0093700…‎ و ‎+9300700…‎ یک سطل‌اند، نه چهار.
 */
function destinationKey(req) {
  const raw = String(req.body?.phone || req.body?.email || req.body?.destination || '').trim();
  if (!raw) return null;
  if (raw.includes('@')) return raw.toLowerCase();
  try { return v.phoneKey(v.phone(raw)) || null; } catch { return raw.replace(/\D/g, '') || null; }
}

const otpLimit = rateLimit({ max: config.rateLimit.otpMax, keyPrefix: 'otp', key: destinationKey });

//  ⛔ زدنِ کد سطلِ خودش را دارد — شرح بالای `verifyMax` در `config.js`.
//  کلید همان ایمیل/شماره است، مثلِ `otpLimit`.
const verifyLimit = rateLimit({ max: config.rateLimit.verifyMax, keyPrefix: 'otp-verify', key: destinationKey });

/*
 *  ⛔ F0-16 — سقفِ دوم برای دو درِ «آیا این ایمیل حساب دارد؟»
 *  (`register/start` و `password/forgot`)، به‌ازای هر **IP**. `otpLimit`
 *  به‌ازای ایمیل است و جلوی امتحانِ هزار ایمیلِ گوناگون از یک جا را نمی‌گرفت.
 */
const probeLimit = rateLimit({ max: config.rateLimit.probeIpMax, keyPrefix: 'account-probe' });

/*
 *  ⛔ سقفِ «به‌ازای هر نشست» برای جاهایی که با یک توکنِ باز رمز سنجیده
 *  می‌شود (`/password`ِ «رمزِ فعلی» و `/reauth`) — یک سطلِ مشترک، تا حدس
 *  زدن میانِ این دو پخش نشود. قفلِ «شناسه + IP» با عوض کردنِ IP دور زده
 *  می‌شد؛ این یکی به خودِ توکن بسته است (بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷، یافتهٔ ۵).
 */
const passwordSessionLimit = rateLimit({
  max: config.rateLimit.passwordSessionMax, keyPrefix: 'password-session',
  key: (req) => (req.tokenRow?.token_hash ? `t:${req.tokenRow.token_hash}` : null),
});

/** کارِ `password/forgot` با تأخیرِ یکسان (`config.otp.uniformMs`) — F0-16. */
async function evenly(work) {
  const floor = Number(config.otp.uniformMs) || 0;
  if (floor <= 0) return work();
  const started = Date.now();
  let settled = false;
  let failure = null;
  const job = work().then(
    (out) => { settled = true; return out; },
    (err) => { settled = true; failure = err; return null; },
  );
  await Promise.race([job, new Promise((r) => setTimeout(r, floor))]);
  const left = floor - (Date.now() - started);
  if (left > 0) await new Promise((r) => setTimeout(r, left));
  if (!settled) {
    //  ایمیلِ کند پشتِ پاسخ ادامه می‌دهد؛ پاسخ دیگر منتظرش نمی‌ماند
    job.then(() => { if (failure) console.error('[auth] بازیابیِ رمز:', failure.message); });
    return null;
  }
  if (failure) throw failure;
  return job;
}

//  خطاهایی که فقط حسابِ **موجود** می‌گرفت (چون فقط برای او کد ساخته
//  می‌شود) و پس وجودِ حساب را لو می‌دادند — F0-16
const QUIET_RESET_ERRORS = new Set(['otp_resend_wait', 'otp_daily_limit', 'delivery_failed']);

// ---------- کمکی‌ها ----------

/** ثبت تلاش ورود — پایه‌ی قفل موقت حساب. */
async function noteAttempt(scope, identifier, ip, ok) {
  await query(
    'INSERT INTO login_attempts (scope, identifier, ip, ok, created_at) VALUES ($1,$2,$3,$4,$5)',
    [scope, identifier, ip, ok, now()]
  );
}

/**
 *  ورودِ درست، ردِ تلاش‌های ناموفق را پاک می‌کند.
 *
 *  بی این، شمارنده فقط با گذشتِ زمان خالی می‌شد: کسی که هفت بار رمز را
 *  غلط زده و هشتمی را درست زده، هنوز یک تلاشِ ناموفق با قفل فاصله دارد.
 *  یعنی همان کاربرِ سالم، ساعتی بعد با یک اشتباهِ ساده قفل می‌شد — در
 *  حالی که همین حالا ثابت کرده صاحبِ حساب است.
 *
 *  امنیتی از دست نمی‌رود: کسی که رمز را دارد، قفل هم جلویش را نمی‌گرفت.
 */
async function clearAttempts(scope, identifier) {
  await query(
    'DELETE FROM login_attempts WHERE scope=$1 AND identifier=$2 AND ok=false',
    [scope, identifier]
  );
}

/**
 *  اگر پشت سر هم اشتباه زده شده، چند دقیقه صبر لازم است.
 *
 *  ── چه چیزی عوض شد و چرا ──────────────────────────────────────────
 *  تا دیروز فقط **شناسه** شمرده می‌شد، از هر کجا که آمده باشد. یعنی
 *  هر کسی که ایمیلِ شما را می‌دانست، با هشت رمزِ غلط از گوشیِ خودش
 *  حسابِ شما را یک ربع می‌بست. برای بستنِ حسابِ یک رقیب — یا آزارِ یک
 *  نفر — هشت کلیک لازم بود. قفلی که قرار بود از حساب محافظت کند،
 *  خودش سلاحِ علیهِ همان حساب شده بود.
 *
 *  حالا دو شمارنده، با دو کار:
 *
 *    ۱) **شناسه + IP** (`lockoutTries`، هشت): کسی که دارد رمز را حدس
 *       می‌زند بسته می‌شود — و فقط خودش. صاحبِ حساب از گوشیِ خودش
 *       دست‌نخورده وارد می‌شود.
 *
 *    ۲) **شناسه، از همه‌جا** (`lockoutGlobalTries`، چهل): پشتوانه‌ی
 *       حمله‌ی پخش‌شده روی چند IP. عمداً خیلی بالاتر است، چون همین
 *       شمارنده است که می‌تواند سوءاستفاده شود. رسیدن به چهل از یک
 *       IP ممکن نیست: محدودیتِ نرخ (`authMax`، ده در ربع ساعت) زودتر
 *       جلویش را می‌گیرد. پس چهل یعنی چند IP واقعاً هماهنگ — که دیگر
 *       بستنِ حساب، درست‌ترین کار است.
 *
 *  ⚠️ `ip` که نیاید، فقط شمارنده‌ی دوم می‌ماند — یعنی همان رفتارِ
 *  سخت‌گیرِ قبلی، نه بازتر از آن.
 *  ──────────────────────────────────────────────────────────────────
 */
async function assertNotLocked(scope, identifier, ip = '') {
  const since = now() - config.rateLimit.lockoutMs;

  if (ip) {
    const mine = await one(
      `SELECT COUNT(*)::int AS n FROM login_attempts
        WHERE scope=$1 AND identifier=$2 AND ip=$3 AND ok=false AND created_at > $4`,
      [scope, identifier, ip, since]
    );
    if (mine.n >= config.rateLimit.lockoutTries) {
      throw tooMany('تلاش ناموفق زیاد بود، چند دقیقه بعد دوباره امتحان کنید', 'locked_out');
    }
  }

  const all = await one(
    `SELECT COUNT(*)::int AS n FROM login_attempts
      WHERE scope=$1 AND identifier=$2 AND ok=false AND created_at > $3`,
    [scope, identifier, since]
  );
  if (all.n >= config.rateLimit.lockoutGlobalTries) {
    throw tooMany('تلاش ناموفق زیاد بود، چند دقیقه بعد دوباره امتحان کنید', 'locked_out');
  }
}

/** ثبت یا به‌روزرسانی دستگاه. */
async function upsertDevice(userId, device, ip) {
  const uid = v.id(device?.deviceId || device?.uid || '', { field: 'شناسه دستگاه', required: false, max: 64 });
  if (!uid) return null;
  const t = now();
  const row = await one(
    `INSERT INTO devices (id, user_id, device_uid, name, platform, status, created_at, last_seen_at, last_ip)
     VALUES ($1,$2,$3,$4,$5,'active',$6,$6,$7)
     ON CONFLICT (user_id, device_uid) DO UPDATE SET
       name = COALESCE(NULLIF(excluded.name,''), devices.name),
       platform = COALESCE(NULLIF(excluded.platform,''), devices.platform),
       last_seen_at = excluded.last_seen_at,
       last_ip = excluded.last_ip
     RETURNING *`,
    [newId('dev'), userId, uid,
      v.text(device?.name, { max: 80 }), v.text(device?.platform, { max: 40 }), t, ip]
  );
  return row;
}

/**
 * بخشی که این درخواست از آن آمده.
 *
 * خواستهٔ صاحب مخزن: «شاپ و پمپ ربطی به هم نداشته باشند، حتی یک ذره.»
 * پس توکن به بخشش مهر می‌خورد و در بخشِ دیگر اصلاً پیدا نمی‌شود.
 *
 * ⚠️ نیامدنش یعنی `shop` — هر برنامه‌ای که امروز در دستِ کاربران است
 * چیزی نمی‌فرستد و مالِ بخشِ دکان است. پس هیچ‌کس بیرون نمی‌افتد.
 *
 * ⚠️ **خودِ قاعده یک جاست**: `lib/tenancy.appOfRequest`. تا دیروز
 * این‌جا فقط `req.body.app` خوانده می‌شد و هدرِ `X-App-Id` — که
 * برنامهٔ پمپ روی **هر** درخواستی دارد — نادیده می‌رفت. اپِ کارمندان
 * که با گوگل وارد می‌شود بدنه‌اش `app` ندارد، پس نشستش به بخشِ دکان
 * مهر می‌خورد و همان لحظه `/api/pump/me` می‌گفت «چنین نشستی نیست».
 */
const appOf = require('../lib/tenancy').appOfRequest;

/**
 *  نسخه‌ی برنامه‌ای که این درخواست از آن آمده.
 *
 *  فقط ثبت می‌شود، هیچ تصمیمی با آن گرفته نمی‌شود — نه دری باز می‌کند
 *  و نه می‌بندد. پس دروغ گفتنش هم چیزی به دستِ کسی نمی‌دهد و لازم
 *  نیست به آن اعتماد شود.
 *
 *  به کار می‌آید وقتی گزارشی می‌رسد و باید فهمید آن گوشی چه نسخه‌ای
 *  داشت، و برای دیدنِ اینکه چند نفر هنوز زیرِ `min_app_version`اند.
 *
 *  خالی یعنی «نگفت»؛ نسخه‌های امروزِ دستِ کاربر چیزی نمی‌فرستند.
 */
function appVersionOf(req) {
  return v.text(req.body?.appVersion || req.body?.app_version, { max: 32 });
}

/** ساخت نشست تازه (توکن دسترسی + توکن تازه‌سازی). */
async function issueSession(user, device, ip, app = 'shop', appVersion = '') {
  //  پیش از ثبتِ دستگاه: آیا این دستگاه/شبکه برای این حساب تازه است؟ (بندِ ۲۱.۱)
  const seen = await loginAlerts.before(user, device?.deviceId || device?.uid, ip, app);
  const dev = await upsertDevice(user.id, device, ip);
  await loginAlerts.after(seen, { user, device: dev, ip, app });
  const access = await tokens.issue({
    kind: 'access', subjectId: user.id, deviceId: dev?.id || null,
    ttlMs: config.tokens.accessTtlMs, app, appVersion,
  });
  const refresh = await tokens.issue({
    kind: 'refresh', subjectId: user.id, deviceId: dev?.id || null,
    ttlMs: config.tokens.refreshTtlMs, app, appVersion,
  });
  await query('UPDATE users SET last_login_at=$2 WHERE id=$1', [user.id, now()]);
  //  ورود خودش احراز است: تا چند دقیقه کارهای حساس بی پرسشِ دوباره (`lib/reauth`)
  await reauth.markFresh({ accessToken: access.token, ip });
  return {
    accessToken: access.token,
    accessExpiresAt: access.expiresAt,
    refreshToken: refresh.token,
    refreshExpiresAt: refresh.expiresAt,
    deviceId: dev?.id || null,
  };
}

function publicUser(u) {
  return {
    id: u.id, name: u.name || '', email: u.email || null, phone: u.phone || null,
    createdAt: Number(u.created_at), hasPassword: !!u.password_hash,
  };
}

/** پاسخ استاندارد ورود — همراه دکان و نقش، تا برنامه بداند کجاست. */
async function loginPayload(user, session) {
  const member = await membershipOf(user.id);
  //  کاربرِ چنددکانی: فهرست هم می‌آید تا برنامه بپرسد «کدام دکان؟» و با
  //  `POST /api/shop/active` نشست را به آن ببندد (بندِ ۲۹.۲ قاعدهٔ ۱)
  const all = member ? await require('../lib/shops').membershipsOf(user.id) : [];
  return {
    user: publicUser(user),
    shop: member ? {
      id: member.shop_id, name: member.shop_name || '', role: member.role,
    } : null,
    shops: all.map((m) => ({ id: m.shop_id, name: m.shop_name || '', role: m.role })),
    ...session,
  };
}

// ---------- ورود شاگرد، فقط با کد ----------
/**
 * صاحب دکان کد را به شاگردش می‌دهد؛ شاگرد همان کد را در صفحه‌ی ورود
 * می‌زند و داخل است. نه ایمیل، نه شماره، نه رمز.
 *
 * ── چرا این‌طور ────────────────────────────────────────────────────
 * تا امروز شاگرد باید اول خودش حساب می‌ساخت (ایمیل یا شماره) و بعد کد
 * را می‌زد. یعنی صاحب دکان باید یک مرحله‌ی اضافه هم توضیح می‌داد، و
 * شاگردی که ایمیل ندارد اصلاً وارد نمی‌شد.
 *
 * ── همان دستگاه، همان حساب ─────────────────────────────────────────
 * شناسه‌ی دستگاه با شناسه‌ی دکان یک هویت پایدار می‌سازد
 * (`user_identities` با provider='staff'). پس شاگردی که فردا دوباره
 * وارد می‌شود همان حساب قبلی را می‌گیرد، نه یک عضو تازه — وگرنه هر
 * ورود یک صندلی از ظرفیت دکان می‌خورد و کد هم زودتر تمام می‌شد.
 *
 * ── امنیت ──────────────────────────────────────────────────────────
 * کد خودش رمز است، پس مثل رمز با آن رفتار می‌شود: محدودیت نرخ روی
 * همین مسیر، و صاحب دکان هر وقت بخواهد کد را باطل یا عوض می‌کند و
 * دسترسی همان لحظه بسته می‌شود.
 */
router.post(['/staff', '/staff-login'], joinLimit, async (req, res, next) => {
  try {
    const rawCode = v.text(req.body?.code, { max: 40, required: true, field: 'کد شاگرد' });
    const name = v.text(req.body?.name, { max: 80 });
    const device = req.body?.device || {};
    const deviceUid = v.id(device?.uid || device?.deviceId || '', {
      field: 'شناسه دستگاه', required: false, max: 64,
    });
    const ip = clientIp(req);

    const shopId = await staffCodes.shopIdOf(rawCode);
    const subject = deviceUid ? `${shopId}:${deviceUid}` : '';

    //  همان دستگاه پیش از این آمده؟ همان حساب را برمی‌گردانیم
    let user = null;
    if (subject) {
      const found = await one(
        `SELECT u.* FROM user_identities i JOIN users u ON u.id = i.user_id
          WHERE i.provider='staff' AND i.subject=$1`, [subject]
      );
      if (found) user = found;
    }

    const t = now();
    if (!user) {
      user = await one(
        `INSERT INTO users (id, name, kind, status, created_at, updated_at)
         VALUES ($1,$2,'staff','active',$3,$3) RETURNING *`,
        [newId('usr'), name || 'شاگرد', t]
      );
      if (subject) {
        await query(
          `INSERT INTO user_identities (id, user_id, provider, subject, created_at)
           VALUES ($1,$2,'staff',$3,$4) ON CONFLICT (provider, subject) DO NOTHING`,
          [newId('idn'), user.id, subject, t]
        );
      }
    } else if (name && name !== user.name) {
      await query('UPDATE users SET name=$2, updated_at=$3 WHERE id=$1', [user.id, name, t]);
      user.name = name;
    }

    if (user.status !== 'active') {
      return next(forbidden('این حساب غیرفعال شده است', 'user_disabled'));
    }

    /*
     *  عضوِ فعالِ همین دکان است؟ کد دوباره خرج نمی‌شود.
     *
     *  بدون این، هر بار باز کردن برنامه یک استفاده از کد می‌خورد و
     *  کدی که «یک بار مصرف» ساخته شده، دفعه‌ی دوم کار نمی‌کرد.
     */
    const member = await membershipOf(user.id);
    if (!member || member.shop_id !== shopId) {
      await staffCodes.redeem(rawCode, user.id, ip);
    }

    //  کدِ شاگرد مالِ دفترِ دکان است، پس نشستش هم همان بخش است.
    //  بخشِ پمپ راهِ پیوستنِ خودش را دارد.
    const session = await issueSession(user, { ...device, uid: deviceUid }, ip, 'shop');
    res.status(201).json(await loginPayload(user, session));
  } catch (err) { next(err); }
});

// ---------- ثبت‌نام با رمز ----------
router.post('/register', authLimit, async (req, res, next) => {
  if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));

  /*
   *  ── این در بسته شد ────────────────────────────────────────────────
   *  این مسیر حساب را **بدونِ تأییدِ ایمیل** می‌ساخت. یعنی هر کسی با
   *  ایمیلِ هر کسِ دیگری حساب می‌گرفت، و برنامه‌ای که مسیرِ سه‌پله را
   *  پیدا نمی‌کرد هم به همین‌جا می‌افتاد. قرار این شد: کد نیامد،
   *  ثبت‌نامی هم نیست.
   *
   *  مسیر خودش می‌ماند (نسخه‌های قدیمِ برنامه هنوز صدایش می‌زنند) ولی
   *  جوابش روشن است: از راهِ سه‌پله برو.
   *  ──────────────────────────────────────────────────────────────────
   */
  return next(forbidden(
    'ثبت‌نام بدونِ تأییدِ ایمیل ممکن نیست — برنامه را به‌روز کنید',
    'verification_required',
  ));
});



/* ==========================================================
   ثبت‌نام سه‌مرحله‌ای — فقط با ایمیل
   ----------------------------------------------------------
   قرار صاحب مخزن: شماره‌ی موبایل از ثبت‌نام برداشته شد. «شماره نباشد،
   همان ایمیل بس است» — چون پیامک پول دارد، به کشور بند است و کاربری که
   شماره‌اش عوض می‌شود حسابش را گم می‌کند. ایمیل هیچ‌کدام را ندارد.

   سه پله:
     ۱) `/register/start`     نام، ایمیل، رمز و تکرارش → کد به ایمیل
     ۲) `/register/verify`    کد شش‌رقمی → «بلیت ثبت‌نام»
     ۳) `/register/complete`  بلیت + لوکیشن + پذیرش شرایط → حساب و نشست

   ── چرا حساب در پله‌ی سوم ساخته می‌شود ─────────────────────────────
   اگر در پله‌ی دوم ساخته می‌شد، هر کسی که کد را می‌زد و پنجره را می‌بست
   یک حسابِ نیم‌بند بی‌شرایط‌پذیرفته روی سرور جا می‌گذاشت، و ایمیلش هم
   «قبلاً ثبت شده» می‌شد؛ یعنی دفعه‌ی بعد اصلاً نمی‌توانست ثبت‌نام کند.
   حالا تا پله‌ی سوم تمام نشود، در جدول `users` چیزی نیست — فقط یک بلیتِ
   کوتاه‌عمر که ایمیلِ تأییدشده را نگه می‌دارد.
   ========================================================== */

const REGISTER_TICKET_TTL_MS = 20 * 60 * 1000;

/** ایمیل و رمز را با هم می‌سنجد — همان بررسی در هر سه پله. */
function registrationInput(body) {
  const email = v.email(body?.email, { required: true });
  const password = typeof body?.password === 'string' ? body.password : '';
  const confirm = typeof body?.passwordConfirm === 'string' ? body.passwordConfirm
    : (typeof body?.confirm === 'string' ? body.confirm : null);
  const weak = pw.checkStrength(password);
  if (weak) throw badRequest(weak, 'weak_password');
  //  تکرار رمز اگر آمده باشد سنجیده می‌شود. برنامه خودش هم می‌سنجد، ولی
  //  سنجشِ سمت گوشی فقط برای زودتر خبر دادن است، نه برای اطمینان.
  if (confirm !== null && confirm !== password) {
    throw badRequest('رمز و تکرارش یکی نیست', 'password_mismatch');
  }
  return { email, password, name: v.text(body?.name, { max: 80 }) };
}

/** آیا این ایمیل از قبل حساب دارد. */
async function assertEmailFree(email) {
  const clash = await one('SELECT id FROM users WHERE email=$1 LIMIT 1', [email]);
  if (clash) throw conflict('این ایمیل از قبل ثبت شده است', 'already_registered');
}

// ---------- پله‌ی یک: نام، ایمیل، رمز ----------
router.post('/register/start', probeLimit, otpLimit, async (req, res, next) => {
  if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));

  const { email, name } = registrationInput(req.body);
  /*
   *  ⛔ F0-16 — ایمیلِ دارای حساب **همان** پاسخ و **همان** ایمیلِ کد را
   *  می‌گیرد. «از قبل ثبت شده» فقط پس از کدِ درست (`/register/verify`) گفته
   *  می‌شود، یعنی فقط به خودِ صاحبِ صندوق. پیش‌تر همین‌جا ۴۰۹ می‌آمد و هر
   *  کسی می‌فهمید کدام ایمیل حساب دارد. کلاینتِ قدیمی چیزی کم ندارد: همان
   *  `already_registered` را یک پله دیرتر می‌گیرد.
   *  راهِ برگشت: `SIGNUP_LEGACY_CONFLICT=1`.
   */
  if (config.signupLegacyConflict) await assertEmailFree(email);

  const out = await otp.request(email, { purpose: 'register', ip: clientIp(req), app: appOf(req) });
  res.status(201).json({
    ok: true,
    email,
    name,
    step: 'verify',
    ...out,
  });
});

// ---------- پله‌ی دو: کد شش‌رقمی ----------
/**
 * کد درست بود؟ یک بلیت بیست‌دقیقه‌ای برمی‌گردد.
 *
 * بلیت خودش یک توکن است (`kind='register'`) و موضوعش همان ایمیل. یعنی
 * پله‌ی سوم دیگر کد نمی‌خواهد و کاربر لازم نیست موقع گرفتن لوکیشن دوباره
 * دنبال ایمیلش بگردد.
 */
router.post('/register/verify', verifyLimit, async (req, res, next) => {
  if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));

  const email = v.email(req.body?.email, { required: true });
  await assertNotLocked('otp', email, clientIp(req));

  try {
    await otp.verify(email, req.body?.code, { purpose: 'register' });
  } catch (err) {
    await noteAttempt('otp', email, clientIp(req), false);
    return next(err);
  }
  await noteAttempt('otp', email, clientIp(req), true);
  //  ⛔ F0-16: فقط **پس از** کدِ درست — کدِ غلط برای ایمیلِ دارای حساب و
  //  بی‌حساب یک خطا دارد
  await assertEmailFree(email);

  const ticket = await tokens.issue({
    kind: 'register', subjectId: `email:${email}`, ttlMs: REGISTER_TICKET_TTL_MS,
  });
  res.json({
    ok: true,
    email,
    step: 'location',
    ticket: ticket.token,
    ticketExpiresAt: ticket.expiresAt,
    terms: { version: terms.VERSION, title: terms.TITLE, sections: terms.SECTIONS },
  });
});

// ---------- پله‌ی سه: لوکیشن و شرایط ----------
/**
 * اینجا حساب ساخته می‌شود.
 *
 * لوکیشن اگر بیاید ثبت می‌شود و لوکیشن‌های بی‌نامِ همین دستگاه هم به
 * حساب تازه می‌چسبند. اگر گوشی اجازه‌ی لوکیشن نداده باشد، ثبت‌نام باز هم
 * تمام می‌شود — نبودنِ لوکیشن نباید کسی را پشت در بگذارد.
 *
 * پذیرش شرایط ولی اجباری است: بدون آن حسابی ساخته نمی‌شود.
 */
router.post('/register/complete', authLimit, async (req, res, next) => {
  if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));

  const row = await tokens.verify(String(req.body?.ticket || ''), 'register');
  if (!row) return next(unauthorized('مهلت ثبت‌نام تمام شد، از اول شروع کنید', 'register_ticket_invalid'));
  const ticketEmail = String(row.subject_id || '').replace(/^email:/, '');

  const { email, password, name } = registrationInput({ ...req.body, email: ticketEmail });
  await assertEmailFree(email);

  const accepted = req.body?.terms?.accepted ?? req.body?.termsAccepted;
  if (accepted !== true) return next(badRequest('برای ساختن حساب باید شرایط و ضوابط را بپذیرید', 'terms_required'));
  const termsVersion = v.text(req.body?.terms?.version || terms.VERSION, { max: 20 });

  const ip = clientIp(req);
  const hash = await pw.hashPassword(password);
  const t = now();
  const user = await one(
    `INSERT INTO users (id, name, email, password_hash, status, created_at, updated_at,
                        terms_version, terms_accepted_at)
     VALUES ($1,$2,$3,$4,'active',$5,$5,$6,$5) RETURNING *`,
    [newId('usr'), name, email, hash, t, termsVersion]
  );

  //  بلیت خرج شد؛ با همان بلیت نمی‌شود حساب دوم ساخت
  await tokens.revoke(req.body.ticket, 'register');

  const deviceUid = v.id(req.body?.device?.uid || req.body?.device?.deviceId || '', {
    field: 'شناسه دستگاه', required: false, max: 64,
  });
  let location = null;
  try {
    location = await geo.record({ deviceUid, userId: user.id, ip }, req.body?.location);
    await geo.claimDevice(deviceUid, user.id);
  } catch (err) {
    //  لوکیشنِ خراب نباید ثبت‌نامِ تمام‌شده را باطل کند
    if (err.code !== 'bad_location' && err.code !== 'device_required') throw err;
  }

  const session = await issueSession(user, req.body?.device, ip, appOf(req), appVersionOf(req));
  await audit.log({
    actorType: 'user', userId: user.id, action: 'auth.register',
    detail: { method: 'email_3step', terms: termsVersion, located: !!location }, ip,
  });
  res.status(201).json({ created: true, location, ...(await loginPayload(user, session)) });
});

/** متن شرایط و ضوابط — یک‌جا برای وب، اندروید و پنل. */
router.get('/terms', (req, res) => {
  res.json({ version: terms.VERSION, title: terms.TITLE, sections: terms.SECTIONS });
});

// ---------- ورود با رمز ----------
router.post('/login', authLimit, async (req, res, next) => {
  const raw = String(req.body?.identifier || req.body?.email || req.body?.phone || '').trim();
  if (!raw) return next(badRequest('ایمیل یا شماره موبایل لازم است', 'identifier_required'));
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  //  رمزِ خالی اصلاً درخواستِ ورود نیست. زودتر ردش می‌کنیم تا نه جای
  //  قفل را بگیرد و نه یک scrypt بی‌خود اجرا شود. چیزی هم لو نمی‌دهد:
  //  جواب به این ربطی ندارد که آن ایمیل حساب دارد یا نه.
  if (!password) return next(badRequest('رمز عبور لازم است', 'password_required'));

  const isEmail = raw.includes('@');
  const identifier = isEmail ? v.email(raw, { required: true }) : v.phone(raw, { required: true });
  await assertNotLocked('user', identifier, clientIp(req));

  const user = await one(
    isEmail ? 'SELECT * FROM users WHERE email=$1' : 'SELECT * FROM users WHERE phone=$1',
    [identifier]
  );
  //  حساب نبود؟ باز هم همان‌قدر وقت می‌گیرد — شرحش سرِ `pw.burnTime`
  let ok = false;
  if (user && user.password_hash) ok = await pw.verifyPassword(password, user.password_hash);
  else await pw.burnTime(password);

  await noteAttempt('user', identifier, clientIp(req), ok);
  if (!ok) return next(unauthorized('ایمیل/شماره یا رمز درست نیست', 'bad_credentials'));
  await clearAttempts('user', identifier);
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const session = await issueSession(user, req.body?.device, clientIp(req), appOf(req), appVersionOf(req));
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.login', detail: { method: 'password' }, ip: clientIp(req) });
  res.json(await loginPayload(user, session));
});

// ---------- کد یک‌بارمصرف ----------
/**
 * مقصد کد: شماره یا ایمیل.
 *
 * هر دو یک راه دارند — کاربر چیزی را که بلد است می‌زند و کد به همان
 * می‌رود. برای شماره پیامک یا واتساپ، برای ایمیل سرویس ایمیل؛ کدام
 * کدام است را خود سرور تصمیم می‌گیرد، نه برنامه.
 */
function destinationOf(body) {
  const raw = String(body?.phone || body?.email || body?.destination || '').trim();
  if (!raw) throw badRequest('شماره یا ایمیل لازم است', 'destination_required');
  return raw.includes('@')
    ? { kind: 'email', value: v.email(raw, { required: true }) }
    : { kind: 'phone', value: v.phone(raw, { required: true }) };
}

router.post('/otp/request', otpLimit, async (req, res, next) => {
  const to = destinationOf(req.body);
  const out = await otp.request(to.value, { purpose: 'login', ip: clientIp(req), app: appOf(req) });
  res.json({ ok: true, [to.kind]: to.value, destination: to.value, ...out });
});

router.post('/otp/verify', verifyLimit, async (req, res, next) => {
  const to = destinationOf(req.body);
  await assertNotLocked('otp', to.value, clientIp(req));

  try {
    await otp.verify(to.value, req.body?.code, { purpose: 'login' });
  } catch (err) {
    await noteAttempt('otp', to.value, clientIp(req), false);
    return next(err);
  }
  await noteAttempt('otp', to.value, clientIp(req), true);
  //  کدِ درست هم مثل رمزِ درست، ردِ تلاش‌های ناموفق را پاک می‌کند
  await clearAttempts('otp', to.value);

  let user = await one(
    to.kind === 'email' ? 'SELECT * FROM users WHERE email=$1' : 'SELECT * FROM users WHERE phone=$1',
    [to.value]
  );
  let created = false;
  if (!user) {
    if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));
    const t = now();
    user = await one(
      to.kind === 'email'
        ? `INSERT INTO users (id, name, email, status, created_at, updated_at)
           VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`
        : `INSERT INTO users (id, name, phone, status, created_at, updated_at)
           VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`,
      [newId('usr'), v.text(req.body?.name, { max: 80 }), to.value, t]
    );
    created = true;
  }
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const session = await issueSession(user, req.body?.device, clientIp(req), appOf(req), appVersionOf(req));
  await audit.log({ actorType: 'user', userId: user.id, action: created ? 'auth.register' : 'auth.login', detail: { method: 'otp' }, ip: clientIp(req) });
  res.status(created ? 201 : 200).json({ created, ...(await loginPayload(user, session)) });
});

// ---------- ورود با گوگل ----------
/*
 *  ⛔ بندِ ۲۹.۲ قاعدهٔ ۵ و AUTH-04 — «برابر بودنِ ایمیل به‌تنهایی مجوزِ ادغام نیست».
 *
 *  تا دیروز اگر شناسهٔ گوگل (`sub`) هنوز به حسابی وصل نبود، حسابی که همان
 *  ایمیل را داشت **بی هیچ پرسشی** برداشته و گوگل به آن چسبانده می‌شد. یعنی
 *  هر کسی که یک حسابِ گوگل با نشانیِ ایمیلِ شما داشت (دامنهٔ ایمیلی که دستِ
 *  دیگری افتاده، حسابِ Workspaceِ بازیافته…) بی رمز وارد دکانِ شما می‌شد.
 *
 *  حالا وصل شدن فقط وقتی است که روشِ **قبلیِ** همان حساب هم ثابت شود: رمزِ
 *  حساب (`password`) یا کدی که به ایمیلِ حساب رفته (`code`، از
 *  `POST /google/link-code`). بی آن، پاسخ ۴۰۹ `google_link_required` است و
 *  برنامه همان‌جا رمز یا کد را می‌خواهد. کسی که گوگلش از قبل وصل است (یافتن
 *  با `sub`) هیچ تغییری نمی‌بیند.
 *
 *  بازگشت: `GOOGLE_EMAIL_AUTOLINK=1` رفتارِ قبلی را برمی‌گرداند (فقط برای
 *  اضطرار؛ هر اتصال هنوز در سابقه `auth.identity_linked` می‌نشیند).
 */
const LINK_METHODS_MESSAGE = 'این ایمیل از قبل حساب دارد. برای وصل کردنِ گوگل به همان حساب، رمزِ حساب را بزنید یا کدی را که به همین ایمیل می‌فرستیم وارد کنید.';

/*
 *  ⛔ بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷ — گوگلِ «ایمیلِ تأییدنشده».
 *
 *  گوگل اجازه می‌دهد حسابی با نشانیِ ایمیلِ کسِ دیگری ساخته شود بی آن‌که آن
 *  ایمیل تأیید شود؛ توکنش `email_verified=false` دارد و هیچ چیزی دربارهٔ
 *  مالکیتِ ایمیل ثابت نمی‌کند. تا امروز چنین توکنی (۱) برای ایمیلِ کسی که
 *  هنوز حساب نداشت **حساب می‌ساخت** — مهاجم پیش از قربانی جا را می‌گرفت و
 *  پس از «رمزِ فراموش‌شده»ی قربانی هم با همان گوگل وارد می‌شد — و (۲) برای
 *  حسابِ موجود «این ایمیل حساب دارد + روش‌هایش» را می‌گفت و کدِ وصل را به
 *  صندوقِ قربانی می‌فرستاد (بمبارانِ ایمیل).
 *
 *  حالا ساختن و وصل کردن فقط با ایمیلِ تأییدشده است. پاسخِ تأییدنشده برای
 *  ایمیلِ دارای حساب و بی‌حساب **یکی** است. شناسه‌ای که از قبل وصل است
 *  (یافتن با `sub`) همان‌طور وارد می‌شود؛ «رمزِ فراموش‌شده» شناسه‌های
 *  تأییدنشده را جدا می‌کند (`unlinkUnverifiedGoogle`).
 */
const GOOGLE_UNVERIFIED_MESSAGE = 'ایمیلِ این حسابِ گوگل نزدِ خودِ گوگل تأیید نشده است، پس با آن نمی‌شود حساب ساخت یا به حسابی وصل شد. اول ایمیل را در حسابِ گوگل تأیید کنید، یا با ایمیل و رمز/کدِ ایمیل وارد شوید.';

/** حسابِ موجودی که این ایمیل را دارد و هنوز این گوگل به آن وصل نیست — فقط برای ایمیلِ تأییدشده. */
async function accountOfGoogleEmail(profile) {
  if (!profile.email || !profile.emailVerified) return null;
  return one('SELECT * FROM users WHERE email=$1', [profile.email]);
}

/**
 *  شناسه‌های گوگلی که تأییدِ ایمیلشان ثابت نیست (`email_verified` نه `true`:
 *  تأییدنشده یا ردیفِ قدیمیِ نامعلوم) از حساب جدا می‌شوند — پس از اینکه
 *  کسی مالکیتِ ایمیلِ حساب را با کد ثابت کرد. گوگلِ قدیمیِ صاحبِ حساب که
 *  دوباره با توکنِ تأییدشده وارد شده `true` است و می‌ماند؛ آن‌که جدا شد با
 *  رمزِ تازه دوباره وصل می‌شود.
 */
async function unlinkUnverifiedGoogle(userId, ip, reason) {
  const gone = await query(
    `DELETE FROM user_identities
      WHERE user_id=$1 AND provider='google' AND email_verified IS NOT TRUE`, [userId]
  );
  if (gone.rowCount) {
    await audit.log({
      actorType: 'user', userId, action: 'auth.identity_unlinked',
      targetType: 'user', targetId: userId, detail: { provider: 'google', reason, count: gone.rowCount }, ip,
    });
  }
  return gone.rowCount;
}

/**
 *  سقفِ کدِ وصل به‌ازای «ایمیلِ توکن + IP». کلیدِ `otpLimit` از بدنه می‌آید
 *  و این مسیر ایمیلی در بدنه ندارد (فقط توکن)، پس پس از خواندنِ توکن شمرده می‌شود.
 */
const linkCodeLimit = rateLimit({
  max: config.rateLimit.linkCodeMax, keyPrefix: 'google-link-code',
  key: (req) => (req.googleProfile?.email ? `${req.googleProfile.email.toLowerCase()}|${clientIp(req)}` : null),
});
async function readGoogleProfile(req, res, next) {
  req.googleProfile = await google.verifyIdToken(req.body?.idToken || req.body?.id_token);
  next();
}

/**
 *  اثباتِ روشِ قبلیِ حساب — رمز یا کدِ ایمیل. هر دو پشتِ همان قفلِ تلاشِ
 *  ناموفقِ ورودِ عادی‌اند، پس این در راهِ تازه‌ای برای حدس زدنِ رمز نیست.
 *  @returns {Promise<'password'|'email_code'|null>}
 */
async function proveExistingMethod(user, body, ip) {
  const password = typeof body?.password === 'string' ? body.password : '';
  const code = String(body?.code || body?.emailCode || '').trim();
  if (password) {
    await assertNotLocked('user', user.email, ip);
    let ok = false;
    if (user.password_hash) ok = await pw.verifyPassword(password, user.password_hash);
    else await pw.burnTime(password);
    await noteAttempt('user', user.email, ip, ok);
    if (!ok) throw unauthorized('رمزِ همین حساب درست نیست', 'bad_credentials');
    await clearAttempts('user', user.email);
    return 'password';
  }
  if (code) {
    await assertNotLocked('otp', user.email, ip);
    try {
      await otp.verify(user.email, code, { purpose: 'link' });
    } catch (err) {
      await noteAttempt('otp', user.email, ip, false);
      throw err;
    }
    await noteAttempt('otp', user.email, ip, true);
    await clearAttempts('otp', user.email);
    return 'email_code';
  }
  return null;
}

function linkRequired(res, user) {
  return res.status(409).json({
    error: {
      code: 'google_link_required',
      message: LINK_METHODS_MESSAGE,
      //  فقط به دارندهٔ توکنِ معتبرِ گوگلِ همین ایمیل گفته می‌شود — کسی که
      //  ایمیل را نمی‌تواند ثابت کند به این‌جا نمی‌رسد
      link: {
        email: user.email,
        methods: [...(user.password_hash ? ['password'] : []), 'email_code'],
      },
    },
  });
}

router.post('/google', authLimit, async (req, res, next) => {
  const idToken = req.body?.idToken || req.body?.id_token;
  const profile = await google.verifyIdToken(idToken);
  const ip = clientIp(req);

  //  اول با شناسه‌ی گوگل — حسابی که گوگلش از قبل وصل است همان‌طور وارد می‌شود
  let user = await one(
    `SELECT u.* FROM user_identities i JOIN users u ON u.id = i.user_id
      WHERE i.provider='google' AND i.subject=$1`,
    [profile.sub]
  );

  let linkedVia = '';
  if (user) {
    //  ردیفِ قدیمی (پیش از ستون) با هر ورودِ تأییدشده «تأییدشده» می‌شود
    if (profile.emailVerified) {
      await query(
        `UPDATE user_identities SET email_verified=true
          WHERE provider='google' AND subject=$1 AND email_verified IS NOT TRUE`, [profile.sub]
      );
    }
  } else {
    //  ساختن یا وصل کردن فقط با ایمیلِ تأییدشده — پیش از هر جست‌وجو، تا
    //  پاسخ برای ایمیلِ دارای حساب و بی‌حساب یکی باشد
    if (profile.email && !profile.emailVerified) {
      return next(forbidden(GOOGLE_UNVERIFIED_MESSAGE, 'google_email_unverified'));
    }
    const existing = await accountOfGoogleEmail(profile);
    if (existing) {
      if (config.google.legacyEmailLink && profile.emailVerified) {
        linkedVia = 'email_auto';
      } else {
        linkedVia = await proveExistingMethod(existing, req.body, ip);
        if (!linkedVia) return linkRequired(res, existing);
      }
      user = existing;
    }
  }

  let created = false;
  if (!user) {
    if (!config.allowRegistration) return next(forbidden('ثبت‌نام روی این سرور بسته است', 'registration_closed'));
    if (!profile.email) return next(badRequest('حساب گوگل ایمیل ندارد', 'google_no_email'));
    const t = now();
    user = await one(
      `INSERT INTO users (id, name, email, status, created_at, updated_at)
       VALUES ($1,$2,$3,'active',$4,$4) RETURNING *`,
      [newId('usr'), profile.name || '', profile.email, t]
    );
    created = true;
  }
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const linked = await query(
    `INSERT INTO user_identities (id, user_id, provider, subject, email, email_verified, created_at)
     VALUES ($1,$2,'google',$3,$4,$5,$6) ON CONFLICT (provider, subject) DO NOTHING`,
    [newId('idn'), user.id, profile.sub, profile.email || '', !!profile.emailVerified, now()]
  );
  if (linkedVia && linked.rowCount) {
    await audit.log({
      actorType: 'user', userId: user.id, action: 'auth.identity_linked',
      targetType: 'user', targetId: user.id, detail: { provider: 'google', via: linkedVia }, ip,
    });
  }

  const session = await issueSession(user, req.body?.device, ip, appOf(req), appVersionOf(req));
  await audit.log({ actorType: 'user', userId: user.id, action: created ? 'auth.register' : 'auth.login', detail: { method: 'google' }, ip });
  res.status(created ? 201 : 200).json({ created, ...(await loginPayload(user, session)) });
});

/**
 *  کدِ اثبات برای وصل کردنِ گوگل — به ایمیلِ **خودِ حساب** می‌رود.
 *
 *  فقط کسی که توکنِ معتبرِ گوگل با همان ایمیل دارد می‌تواند بخواهدش، و کد
 *  `purpose='link'` دارد: نه برای ورود کار می‌کند و نه برای بازیابیِ رمز.
 *  حسابی که نیست یا گوگلش از قبل وصل است کدی نمی‌گیرد (پاسخ همان ۲۰۰ است).
 *  ⛔ گوگلِ تأییدنشده هم کدی نمی‌گیرد و همان پاسخِ «حساب نیست» را می‌بیند
 *  (`accountOfGoogleEmail`)؛ و سقفِ «ایمیل + IP» (`linkCodeLimit`) دارد.
 */
router.post('/google/link-code', authLimit, otpLimit, readGoogleProfile, linkCodeLimit, async (req, res) => {
  const profile = req.googleProfile;
  const already = await one(
    `SELECT 1 FROM user_identities WHERE provider='google' AND subject=$1`, [profile.sub]
  );
  const existing = already ? null : await accountOfGoogleEmail(profile);
  let out = {};
  if (existing) {
    out = await otp.request(existing.email, { purpose: 'link', ip: clientIp(req), app: appOf(req) });
  }
  res.json({
    ok: true, sent: !!existing, email: profile.email || null,
    resendSeconds: Math.ceil(config.otp.resendMs / 1000),
    ...(out.devCode ? { devCode: out.devCode } : {}),
  });
});

// ---------- تازه‌سازی نشست ----------
router.post('/refresh', sessionLimit, async (req, res, next) => {
  const token = String(req.body?.refreshToken || req.body?.refresh_token || '');
  //  `null` یعنی «هر بخشی» — بخشِ درست را از خودِ ردیف برمی‌داریم،
  //  نه از چیزی که درخواست ادعا می‌کند. وگرنه یک توکنِ تازه‌سازیِ
  //  دکان می‌توانست توکنِ دسترسیِ پمپ بسازد.
  let found = await tokens.rotateRefresh(token, { app: null });
  if (found?.stolen) {
    const n = await tokens.revokeFamily(found.row);
    await audit.log({
      actorType: 'user', userId: found.row.subject_id, action: 'auth.refresh_reused',
      detail: { sessions: n, app: found.row.app || 'shop' }, ip: clientIp(req),
    });
    return next(unauthorized('این نشست جای دیگری هم به کار رفته و بسته شد؛ دوباره وارد شوید', 'refresh_reused'));
  }
  if (!found) return next(unauthorized('نشست منقضی شده است، دوباره وارد شوید', 'invalid_token'));

  /*
   *  تازه‌سازیِ چرخشی (بندِ ۲.۵). در پنجرهٔ ارفاق از آخرین حلقهٔ زنده
   *  می‌چرخیم. ⛔ و اگر درخواستِ هم‌زمانِ دیگری همین لحظه همین توکن را
   *  چرخاند (`markRotated` ⇒ صفر)، جفتِ ما دور ریخته می‌شود و یک بار از
   *  نو — این بار از راهِ ارفاق — می‌چرخیم؛ وگرنه دو زنجیرهٔ زنده از یک
   *  توکن می‌ماند.
   */
  let access = null;
  let refreshToken = '';
  let refreshExpiresAt = 0;
  for (let attempt = 0; attempt < 8 && found; attempt++) {
    const row = found.row;
    const user = await one('SELECT * FROM users WHERE id=$1', [row.subject_id]);
    if (!user || user.status !== 'active') return next(unauthorized('حساب در دسترس نیست', 'invalid_token'));

    //  دکانِ برگزیدهٔ نشست با تازه‌سازی می‌ماند (مهاجرتِ ۰۹۷)
    const acc = await tokens.issue({
      kind: 'access', subjectId: user.id, deviceId: row.device_id,
      ttlMs: config.tokens.accessTtlMs, app: row.app || 'shop', shopId: row.shop_id || null,
    });
    const fresh = await tokens.issue({
      kind: 'refresh', subjectId: user.id, deviceId: row.device_id,
      ttlMs: config.tokens.refreshTtlMs, app: row.app || 'shop', shopId: row.shop_id || null,
    });
    if (await tokens.markRotated(found.hash, fresh.token)) {
      access = acc; refreshToken = fresh.token; refreshExpiresAt = fresh.expiresAt;
      break;
    }
    await tokens.revoke(acc.token, 'access');
    await tokens.revoke(fresh.token, 'refresh');
    found = await tokens.rotateRefresh(token, { app: null });
    if (found?.stolen) found = null;
  }
  if (!access) return next(unauthorized('نشست منقضی شده است، دوباره وارد شوید', 'invalid_token'));

  res.json({
    ok: true,
    accessToken: access.token, accessExpiresAt: access.expiresAt,
    refreshToken, refreshExpiresAt,
    //  نام‌های قراردادِ پرامپت، کنارِ نام‌های امروز
    access_token: access.token,
    access_expires_in: Math.round(config.tokens.accessTtlMs / 1000),
    refresh_token: refreshToken,
    refresh_expires_in: Math.max(0, Math.round((refreshExpiresAt - Date.now()) / 1000)),
  });
});

// ---------- خروج ----------
router.post('/logout', sessionLimit, async (req, res) => {
  const refresh = String(req.body?.refreshToken || req.body?.refresh_token || '');
  const access = (req.headers.authorization || '').replace(/^Bearer\s+/i, '').trim();
  if (refresh) await tokens.revoke(refresh, 'refresh');
  if (access) await tokens.revoke(access, 'access');
  res.json({ ok: true });
});

/**
 * خروج از همه‌ی دستگاه‌ها — **در همین بخش**.
 *
 * ⚠️ `req.appSection` از روی خودِ توکن می‌آید (`requireAnyUser` می‌نشاندش)،
 * نه از بدنه. پس «خروج از همه» در برنامهٔ دکان نشستِ پمپِ همان آدم را
 * دست نمی‌زند و برعکس — همان قاعدهٔ «دو بخش، دو دفتر».
 */
router.post('/logout-all', requireAnyUser, reauth.requireRecentAuth, async (req, res) => {
  const app = req.appSection || 'shop';
  const n = await tokens.revokeAllForSubject(req.user.id, null, app);
  await audit.log({ actorType: 'user', userId: req.user.id, action: 'auth.logout_all', detail: { sessions: n, app } });
  res.json({ ok: true, sessions: n, app });
});

/* ---------- تأییدِ دوبارهٔ هویت برای کارهای حساس (بندِ ۲۱.۱) ---------- */

/** مقصدِ کدِ تأییدِ دوباره: ایمیلِ حساب، وگرنه شمارهٔ آن. */
function reauthDestination(user) {
  return user.email || user.phone || '';
}

/**
 *  کد برای تأییدِ دوباره — فقط به ایمیل/شمارهٔ **خودِ حساب**، با
 *  `purpose='reauth'`؛ نه برای ورود کار می‌کند و نه برای بازیابی.
 */
router.post('/reauth/code', requireAnyUser, sessionLimit, async (req, res, next) => {
  const to = reauthDestination(req.user);
  if (!to) return next(badRequest('این حساب ایمیل یا شماره ندارد که کد به آن برود', 'no_destination'));
  const out = await otp.request(to, { purpose: 'reauth', ip: clientIp(req), app: req.appSection || 'shop' });
  res.json({ ok: true, sent: true, resendSeconds: Math.ceil(config.otp.resendMs / 1000), ...(out.devCode ? { devCode: out.devCode } : {}) });
});

/**
 *  تأییدِ دوباره با رمز یا کد. موفق ⇒ همین نشست تا چند دقیقه «تازه» است.
 *  همان قفلِ تلاشِ ناموفقِ ورود، پس راهِ تازه‌ای برای حدس زدنِ رمز نیست.
 */
router.post('/reauth', requireAnyUser, sessionLimit, passwordSessionLimit, async (req, res, next) => {
  const user = req.user;
  const ip = clientIp(req);
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const code = String(req.body?.code || '').trim();
  let method = '';
  if (password) {
    const id = user.email || user.phone || user.id;
    await assertNotLocked('user', id, ip);
    let ok = false;
    if (user.password_hash) ok = await pw.verifyPassword(password, user.password_hash);
    else await pw.burnTime(password);
    await noteAttempt('user', id, ip, ok);
    if (!ok) return next(unauthorized('رمز درست نیست', 'bad_credentials'));
    method = 'password';
  } else if (code) {
    const to = reauthDestination(user);
    await assertNotLocked('otp', to, ip);
    try {
      await otp.verify(to, code, { purpose: 'reauth' });
    } catch (err) {
      await noteAttempt('otp', to, ip, false);
      return next(err);
    }
    await noteAttempt('otp', to, ip, true);
    method = 'code';
  } else {
    return next(badRequest('رمز یا کد لازم است', 'reauth_proof_required'));
  }
  await reauth.markFresh({ tokenHash: req.tokenRow.token_hash, ip });
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.reauth', detail: { method }, ip });
  res.json({ ok: true, freshUntil: await reauth.freshUntil(req.tokenRow.token_hash) });
});

/** گذاشتن یا عوض کردن رمز عبور (برای کسی که با کد وارد شده). */
router.post('/password', requireAnyUser, passwordSessionLimit, async (req, res, next) => {
  const next_ = typeof req.body?.newPassword === 'string' ? req.body.newPassword : '';
  const weak = pw.checkStrength(next_);
  if (weak) return next(badRequest(weak, 'weak_password'));

  //  ⛔ حسابِ بی‌رمز «رمزِ فعلی» ندارد که ثابتش کند؛ بی این، هر کسی با یک
  //  نشستِ باز رمز می‌گذاشت و حساب را از آنِ خودش می‌کرد (بندِ ۲۱.۱).
  //  در **هر** بخش (پمپ هم): رمز مالِ حساب است، نه مالِ یک بخش — بازبینیِ
  //  امنیتی ۱۴۰۵/۰۷/۲۷، یافتهٔ ۶. ورودِ تازه یا کدِ ایمیلِ `/reauth` کافی است.
  if (!req.user.password_hash) await reauth.assertFresh(req, { allSections: true });
  if (req.user.password_hash) {
    /*
     *  ⛔ «رمزِ فعلی» همان رمزِ ورود است، پس پشتِ همان قفلِ `/reauth` و
     *  ورود (`assertNotLocked`/`noteAttempt`، همان شناسه). تا امروز این‌جا
     *  هیچ قفلی نبود و یک نشستِ کهنه بی‌حساب رمز را حدس می‌زد (یافتهٔ ۵).
     */
    const id = req.user.email || req.user.phone || req.user.id;
    const ip = clientIp(req);
    await assertNotLocked('user', id, ip);
    const current = typeof req.body?.currentPassword === 'string' ? req.body.currentPassword : '';
    const ok = await pw.verifyPassword(current, req.user.password_hash);
    await noteAttempt('user', id, ip, ok);
    if (!ok) return next(unauthorized('رمز فعلی درست نیست', 'bad_credentials'));
  }
  const hash = await pw.hashPassword(next_);
  await query('UPDATE users SET password_hash=$2, updated_at=$3 WHERE id=$1', [req.user.id, hash, now()]);
  /*
   *  ⛔ و نشست‌های **دیگر** بسته می‌شوند.
   *
   *  تا دیروز عوض کردنِ رمز از داخلِ برنامه هیچ نشستی را نمی‌بست: کسی
   *  که رمزش را عوض می‌کرد چون گمان می‌کرد لو رفته، همان نشستِ
   *  لو‌رفته تا نود روز زنده می‌ماند. مسیرِ «رمزِ فراموش‌شده» این را از
   *  قبل درست انجام می‌داد؛ این یکی جا مانده بود.
   *
   *  نشستِ خودِ همین دستگاه می‌ماند، وگرنه کاربر با عوض کردنِ رمز از
   *  برنامهٔ خودش هم بیرون می‌افتاد.
   */
  const closed = await tokens.revokeOthersForSubject(req.user.id, {
    keepDeviceId: req.tokenRow?.device_id || null,
    keepTokenHash: req.tokenRow?.token_hash || null,
  });
  await audit.log({
    actorType: 'user', userId: req.user.id, action: 'auth.password_changed',
    detail: { closedSessions: closed },
  });
  res.json({ ok: true, closedSessions: closed });
});

/* ---------- بازیابی رمز فراموش‌شده ---------- */

/**
 * درخواست کد بازیابی.
 *
 * کد به همان ایمیلی می‌رود که کاربر زده — نه جای دیگر. اگر آن ایمیل
 * حسابی نداشته باشد، باز هم همان پاسخ موفق برمی‌گردد و کدی فرستاده
 * نمی‌شود.
 *
 * چرا این‌طور: اگر می‌گفتیم «این ایمیل حساب ندارد»، هر کسی می‌توانست
 * با امتحان کردن نشانی‌ها بفهمد چه کسانی روی این سرور حساب دارند.
 *
 * `purpose = 'reset'` جداست از کد ورود، پس کدی که برای ورود صادر شده
 * به درد عوض کردن رمز نمی‌خورد و برعکس.
 */
router.post('/password/forgot', probeLimit, otpLimit, async (req, res, next) => {
  const raw = String(req.body?.email || req.body?.identifier || '').trim();
  if (!raw) return next(badRequest('ایمیل لازم است', 'email_required'));
  const email = v.email(raw, { required: true });

  /*
   *  ⛔ F0-16 — پاسخِ یکسان **و** تأخیرِ یکسان.
   *  • «مهلتِ ارسالِ دوباره»، «سقفِ روزانه» و «ایمیل نرفت» فقط به حسابِ
   *    موجود می‌رسید (برای ناموجود کدی ساخته نمی‌شود)، پس ۴۲۹/۵۰۲ یعنی «حساب
   *    هست». حالا هر سه فقط در لاگِ سرور می‌نشینند و پاسخ همان ۲۰۰ است.
   *    کدِ قبلی هنوز معتبر است (مهلتِ کد از مهلتِ ارسالِ دوباره بلندتر است).
   *  • فرستادنِ ایمیل زمان می‌برد؛ `evenly` هر دو پاسخ را تا یک کفِ ثابت نگه
   *    می‌دارد.
   */
  const out = (await evenly(async () => {
    const user = await one('SELECT id FROM users WHERE email=$1', [email]);
    if (!user) return {};
    try {
      const sent = await otp.request(email, { purpose: 'reset', ip: clientIp(req), app: appOf(req) });
      await audit.log({ actorType: 'user', userId: user.id, action: 'auth.password_reset_requested', ip: clientIp(req) });
      return sent;
    } catch (err) {
      if (!QUIET_RESET_ERRORS.has(err && err.code)) throw err;
      console.warn(`[auth] بازیابیِ رمز بی ارسالِ تازه: ${err.code}`);
      return {};
    }
  })) || {};

  //  پاسخ برای حسابِ موجود و ناموجود یکی است. `resendSeconds` هم می‌رود
  //  تا برنامه بتواند شمارش را نشان دهد، چه حساب باشد چه نباشد.
  res.json({
    ok: true,
    sent: true,
    email,
    resendSeconds: Math.ceil(config.otp.resendMs / 1000),
    ...(out.devCode ? { devCode: out.devCode } : {}),
  });
});

/**
 * گذاشتن رمز تازه با کدی که به ایمیل رفته.
 *
 * بعد از عوض شدن رمز، همه‌ی نشست‌های باز بسته می‌شوند. اگر کسی رمز را
 * فراموش کرده چون گوشی‌اش دست دیگری افتاده، آن نشست هم باید برود.
 */
router.post('/password/reset', verifyLimit, async (req, res, next) => {
  const email = v.email(req.body?.email, { required: true });
  const password = typeof req.body?.password === 'string' ? req.body.password : '';
  const weak = pw.checkStrength(password);
  if (weak) return next(badRequest(weak, 'weak_password'));

  await assertNotLocked('otp', email, clientIp(req));
  try {
    await otp.verify(email, req.body?.code, { purpose: 'reset' });
  } catch (err) {
    await noteAttempt('otp', email, clientIp(req), false);
    return next(err);
  }
  await noteAttempt('otp', email, clientIp(req), true);

  const user = await one('SELECT * FROM users WHERE email=$1', [email]);
  if (!user) return next(notFound('حسابی با این ایمیل نیست', 'user_not_found'));
  if (user.status !== 'active') return next(forbidden('این حساب غیرفعال است', 'account_disabled'));

  const hash = await pw.hashPassword(password);
  await query('UPDATE users SET password_hash=$2, updated_at=$3 WHERE id=$1', [user.id, hash, now()]);
  await tokens.revokeAllForSubject(user.id);
  //  ⛔ صاحبِ ایمیل همین حالا مالکیت را با کد ثابت کرد: گوگلی که تأییدِ
  //  ایمیلش ثابت نیست (مثلاً کسی که پیش از او با گوگلِ تأییدنشده همین
  //  ایمیل را گرفته بود) دیگر راهی به این حساب ندارد
  await unlinkUnverifiedGoogle(user.id, clientIp(req), 'password_reset_unverified');
  await audit.log({ actorType: 'user', userId: user.id, action: 'auth.password_reset', ip: clientIp(req) });

  //  و همان‌جا واردش می‌کنیم؛ کسی که تازه رمز گذاشته نباید دوباره بزندش
  const session = await issueSession(user, req.body?.device, clientIp(req), appOf(req), appVersionOf(req));
  res.json(await loginPayload(user, session));
});

module.exports = router;
module.exports.issueSession = issueSession;
//  برای سنجه‌ها: قفل با آستانه‌های واقعی از راهِ مسیرها سنجیده نمی‌شود،
//  چون `helpers.js` عمداً آستانه‌ها را بالا می‌برد تا دویست سنجه‌ی دیگر
//  همدیگر را نبندند
module.exports.assertNotLocked = assertNotLocked;
module.exports.publicUser = publicUser;
module.exports.loginPayload = loginPayload;
