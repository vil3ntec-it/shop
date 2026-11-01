'use strict';
/**
 * «من» — حساب، دکان، نقش، دسترسی‌ها و اشتراک.
 *
 * برنامه بعد از هر ورود و هر همگام‌سازی همین را می‌خواند و تصمیم قفل/باز
 * بودن قابلیت‌ها را از روی همین می‌گیرد؛ نه از روی ساعت گوشی.
 */
const express = require('express');
const { one, many, query, now } = require('../db');
const v = require('../lib/validate');
const { requireUser, optionalShop } = require('../middleware/auth');
const { entitlementOf } = require('../lib/entitlement');
const { FEATURES } = require('../lib/features');
const subs = require('../lib/subscriptions');
const plans = require('../lib/plans');
const { publicUser } = require('./auth');
const audit = require('../lib/audit');
const reauth = require('../lib/reauth');

const router = express.Router();

router.use(requireUser, optionalShop);

router.get('/', async (req, res) => {
  const ent = req.shopId ? await entitlementOf(req.shopId) : null;
  //  STF-01..03: فهرستِ قدیمی با لایه‌های دکان (هرگز بیش از نقش) + `policy` برای هستهٔ برنامه
  const pol = req.member
    ? await require('../lib/staff-perms').policyOf(req.shopId, req.member)
    : { permissions: [], policy: null };
  res.json({
    user: publicUser(req.user),
    shop: req.member ? {
      id: req.member.shop_id,
      name: req.member.shop_name || '',
      role: req.member.role,
      isOwner: req.member.role === 'owner',
    } : null,
    permissions: pol.permissions,
    policy: pol.policy,
    entitlement: ent ? {
      source: ent.source, features: ent.features,
      subscription: ent.subscription, trial: ent.trial,
    } : null,
    //  انتخابِ خودِ مشتری — همگام‌سازیِ ابری روشن است یا نه
    settings: { cloudSync: req.shopId ? await portal.cloudSyncOf('shop', req.shopId) : false },
    serverTime: now(),
  });
});

/*
 *  ══ اعلان‌های من · پرداخت‌های من · تنظیماتِ من ══════════════════════
 *  منطق در `routes/portal.js` است (همان که پورتالِ وب هم می‌خواند)؛
 *  این‌جا فقط با هویتِ نشستِ **دکان** صدا زده می‌شود.
 */
const portal = require('./portal');
router.get('/notices', (req, res, next) => portal.noticesHandler('shop', req, res, next));
router.post('/notices/:id/read', (req, res, next) => portal.readNoticeHandler('shop', req, res, next));
router.get('/payments', (req, res, next) => portal.paymentsHandler('shop', req, res, next));
router.get('/settings', (req, res, next) => portal.settingsHandler('shop', req, res, next));
router.put('/settings', (req, res, next) => portal.saveSettingsHandler('shop', req, res, next));
router.post('/redeem', (req, res, next) => portal.redeemHandler('shop', req, res, next));
router.get('/heartbeat', (req, res, next) => portal.heartbeatHandler('shop', req, res, next));
router.post('/errors', (req, res, next) => portal.reportErrorHandler('shop', req, res, next));

/** وضعیت اشتراک — همیشه با ساعت سرور. */
async function subscriptionHandler(req, res) {
  if (!req.shopId) {
    return res.json({
      status: 'none', phase: 'none', active: false, source: 'none',
      features: [], serverTime: now(), shop: null,
    });
  }
  await subs.expireDue();
  const ent = await entitlementOf(req.shopId);
  res.json({
    shop: { id: req.shopId, name: req.member.shop_name || '' },
    status: ent.subscription.status,
    //  SUB-01 (docs/SUB-STATES-fa.md): مرحلهٔ رفتاری — «مهلت» (grace) از «فعال» جدا؛ فقط افزوده
    phase: ent.subscription.phase,
    active: ent.subscription.active,
    source: ent.source,
    plan: ent.subscription.plan || '',
    startsAt: ent.subscription.startsAt,
    endsAt: ent.subscription.endsAt,
    graceEndsAt: ent.subscription.graceEndsAt ?? 0,
    daysLeft: ent.subscription.daysLeft,
    trial: ent.trial,
    features: ent.features,
    catalog: FEATURES,
    serverTime: now(),
  });
}

/** پلن‌ها و شماره‌ی واتساپ — برای صفحه‌ی اشتراک. */
async function plansHandler(req, res) {
  const cfg = await plans.allConfig();
  res.json({
    plans: await plans.listPlans(),
    whatsapp: { number: cfg.whatsapp_number || '', message: cfg.whatsapp_message || '' },
    currency: cfg.currency || 'افغانی',
    //  F0-13: همان عددِ `entitlement` (`plans.trialDaysOf`)، نه خواندنِ جدا با `|| 0`
    trialDays: await plans.trialDaysOf('shop'),
  });
}

/**
 * تپشِ هر پانزده دقیقه — بندِ ۲۰.۷ پرامپت.
 *
 * یک درخواستِ ارزان که همهٔ آن‌چه برنامه لازم دارد را یک‌جا می‌دهد:
 * وضعیتِ اشتراک، روزهای مانده، قابلیت‌ها، آخرین نسخه و زمانِ سرور.
 * برنامه نتیجه را کش می‌کند و بخشِ «اشتراکِ من» از همان می‌خواند — پس
 * صفحهٔ اشتراک آفلاین هم چیزی برای نشان دادن دارد.
 */
router.get('/heartbeat', async (req, res, next) => {
  try {
    const ent = req.shopId ? await entitlementOf(req.shopId) : null;
    const sub = ent?.subscription || {};
    const endsMs = Number(sub.endsAt || 0);
    res.json({
      ok: true,
      user: { id: req.user.id, email: req.user.email || null, name: req.user.name || '' },
      subscription: {
        status: ent?.active ? 'active' : (sub.status || 'none'),
        plan: sub.plan || null,
        ends_at: endsMs ? Math.floor(endsMs / 1000) : null,
        days_left: endsMs ? Math.max(0, Math.ceil((endsMs - now()) / 86400000)) : 0,
        permanent: Boolean(sub.permanent) || Boolean(ent?.active && !endsMs),
        source: ent?.source || '',
      },
      features: ent?.features || [],
      server_time: now(),
    });
  } catch (err) { next(err); }
});

router.get('/subscription', subscriptionHandler);
router.get('/plans', plansHandler);

/**
 * ثبت درخواست خرید — مدیر بعد از دریافت پول فعالش می‌کند.
 *
 * نامِ فیلد هم `plan` پذیرفته می‌شود هم `planCode`: نسخه‌ی وب از روز اول
 * `planCode` می‌فرستاد و اینجا فقط `plan` خوانده می‌شد، یعنی هر درخواستِ
 * خریدی که از سایت می‌آمد با «پلن لازم است» رد می‌شد. نامِ درست `plan`
 * است ولی نسخه‌های منتشرشده‌ی وب روی گوشیِ مردم‌اند و به‌روز نمی‌شوند،
 * پس هر دو نام قبول است.
 */
async function purchaseRequestHandler(req, res) {
  if (!req.shopId) return res.status(403).json({ error: { code: 'no_shop', message: 'اول دکان بسازید' } });
  const planCode = v.text(req.body?.plan ?? req.body?.planCode, { max: 20, required: true, field: 'پلن' });
  const note = v.text(req.body?.note, { max: 300 });
  const { newId } = require('../db');
  const row = await one(
    `INSERT INTO purchase_requests (id, shop_id, user_id, plan_code, note, status, created_at)
     VALUES ($1,$2,$3,$4,$5,'pending',$6) RETURNING *`,
    [newId('preq'), req.shopId, req.user.id, planCode, note, now()]
  );
  await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'subscription.requested', detail: { plan: planCode } });
  res.status(201).json({ request: { id: row.id, plan: row.plan_code, status: row.status } });
}
router.post('/purchase-request', purchaseRequestHandler);

/** دستگاه‌های این حساب. */
router.get('/devices', async (req, res) => {
  const rows = await many(
    'SELECT id, device_uid, name, platform, status, created_at, last_seen_at FROM devices WHERE user_id=$1 ORDER BY last_seen_at DESC NULLS LAST',
    [req.user.id]
  );
  res.json({ devices: rows });
});

router.delete('/devices/:id', async (req, res) => {
  const id = v.id(req.params.id, { field: 'شناسه دستگاه' });
  //  ⛔ بیرون کردنِ دستگاهِ **دیگر** همان «خروج از همه»ی تکه‌تکه است، پس
  //  همان تأییدِ دوباره را می‌خواهد (بازبینیِ امنیتی ۱۴۰۵/۰۷/۲۷، یافتهٔ ۴).
  //  دستگاهِ خودِ همین نشست (خروج) آزاد است.
  if (id !== req.tokenRow?.device_id) await reauth.assertFresh(req);
  const r = await query(`UPDATE devices SET status='revoked' WHERE id=$1 AND user_id=$2`, [id, req.user.id]);
  //  ⛔ نشست‌ها فقط وقتی دستگاه واقعاً مالِ همین کاربر بود — وگرنه هر کسی با
  //  شناسهٔ دستگاهِ دیگری نشستِ او را می‌کشت
  if (r.rowCount) {
    await query('UPDATE tokens SET revoked_at=$1 WHERE device_id=$2 AND subject_id=$3 AND revoked_at IS NULL',
      [now(), id, req.user.id]);
    //  ⛔ S78: سوکتِ زندهٔ همان دستگاه هم در همین درخواست
    await require('../lib/sync-v1-live').accessChanged({ userId: req.user.id });
  }
  res.json({ ok: true });
});

/**
 * ویرایش حساب — نام، و یک‌بار ثبت شماره.
 *
 * ── چه چیزی عوض می‌شود و چه چیزی نه ──────────────────────────────────
 * نام هر وقت خواست عوض می‌شود؛ اسم آدم نشانه‌ی هویت حساب نیست.
 *
 * ایمیل و شماره **راه ورود**اند. عوض کردنشان یعنی حساب از دست صاحبش
 * در می‌آید: کسی که یک بار توکن گرفته باشد می‌توانست ایمیل را عوض کند
 * و از آن به بعد بازیابی رمز به ایمیل خودش برود. پس هیچ‌کدام از این
 * مسیر عوض نمی‌شوند.
 *
 * ولی یک حالت مانده بود: کسی که با ایمیل ثبت‌نام کرده و شماره ندارد.
 * برای او شماره «عوض کردن» نیست، «افزودن» است — و همان یک بار.
 * ثبتش که شد، دیگر از اینجا دست نمی‌خورد.
 * ────────────────────────────────────────────────────────────────────
 */
/*
 *  ══ AUTH-08 — افزودنِ شماره فقط با کدِ همان شماره (پرچمِ `auth.phone_add_code`) ═══════
 *
 *  شماره «راهِ ورود» است: `POST /api/auth/login` با شماره و رمز، و `otp/request` +
 *  `otp/verify` با کدِ پیامکی به همان شماره، هر دو حسابی را باز می‌کنند که آن شماره را
 *  دارد. تا امروز افزودنِ شماره فقط تأییدِ دوبارهٔ **حساب** می‌خواست، نه اثباتِ مالکیتِ
 *  **شماره** — یک رقمِ غلط (یا شمارهٔ کسِ دیگر) یعنی صاحبِ آن شماره با «ورود با کد» واردِ
 *  دکانِ این حساب می‌شد، و صاحبِ واقعیِ شماره دیگر نمی‌توانست با شماره‌اش حساب بسازد.
 *
 *  با پرچمِ روشن (scope `security`، کلید `auth.phone_add_code`، پیش‌فرض خاموش):
 *    ۱) `POST /api/me/phone/code { phone }` — نشستِ تازه (بندِ ۲۱.۱) ⇒ کدِ یک‌بارمصرفِ
 *       `lib/otp.js` به همان شماره، با هدفِ `phone_add:<شناسهٔ حساب>` (بسته به حساب، شماره
 *       و هدف؛ CSPRNG، هش‌شده، سقفِ تلاش، فاصلهٔ ارسالِ دوباره، سقفِ روزانه و SEC-06).
 *       «این شماره حسابِ دیگری دارد» این‌جا گفته **نمی‌شود** (بی شمارش‌پذیری).
 *    ۲) `PUT /api/me { phone, phoneCode }` — نشستِ تازه + کدِ درستِ همان شماره و همان حساب؛
 *       فقط پس از کدِ درست ۴۰۹ `phone_taken` (فقط صاحبِ شماره می‌فهمد).
 *  پرچمِ خاموش ⇒ دقیقاً رفتارِ امروز (فقط تأییدِ دوبارهٔ حساب)، و درِ ۱ ۴۰۴.
 *  برنامهٔ مدیریت (اندروید و کامپیوتر) گامِ کد را دارد (`core/account/PhoneClaim`، `PhoneClaimStep.kt`)؛
 *  ⚠️ برنامهٔ وبِ یخ‌زده (`index.html`) `phoneCode` نمی‌فرستد — با پرچمِ روشن آن‌جا شماره افزوده نمی‌شود.
 */
const PHONE_FLAG_SCOPE = 'security';
const PHONE_FLAG_KEY = 'auth.phone_add_code';
const phonePurpose = (userId) => `phone_add:${userId}`;

async function phoneCodeRequired() {
  const r = await one('SELECT value FROM live_config WHERE scope=$1 AND key=$2', [PHONE_FLAG_SCOPE, PHONE_FLAG_KEY]);
  if (!r) return false;
  try { return JSON.parse(r.value) === true; } catch { return false; }
}

/** در production با راهِ پیامکِ `log` کد به هیچ‌کس نمی‌رسد ⇒ ۵۰۳ ِ صریح، نه «فرستاده شد» (همان قاعدهٔ ویترین) */
async function smsReachesPhone() {
  if (require('../config').env !== 'production') return true;
  const { provider } = await require('../lib/sms-settings').current();
  return Boolean(provider) && provider !== 'log';
}

const phoneLocked = (res) => res.status(409).json({
  error: { code: 'phone_locked', message: 'شماره ثبت‌شده عوض نمی‌شود — با پشتیبانی تماس بگیرید' },
});
const phoneTaken = (res) => res.status(409).json({
  error: { code: 'phone_taken', message: 'این شماره روی حساب دیگری ثبت شده' },
});

//  هر حساب سطلِ خودش را دارد (کنارِ فاصله و سقفِ روزانهٔ خودِ `otp.request` و SEC-06)
const phoneCodeLimit = require('../middleware/ratelimit').rateLimit({
  max: require('../config').rateLimit.otpMax, keyPrefix: 'phone-add',
  key: (req) => (req.user ? req.user.id : null),
});

router.post('/phone/code', phoneCodeLimit, async (req, res, next) => {
  try {
    if (!(await phoneCodeRequired())) {
      return res.status(404).json({ error: { code: 'phone_code_disabled', message: 'این سرور برای افزودنِ شماره کد نمی‌فرستد' } });
    }
    await reauth.assertFresh(req);
    const phone = v.phone(req.body?.phone, { required: true });
    if (req.user.phone) return phoneLocked(res);
    if (!(await smsReachesPhone())) {
      return res.status(503).json({ error: { code: 'verification_unavailable', message: 'پیامکِ تأییدِ شماره هنوز روی این سرور راه‌اندازی نشده است' } });
    }
    const out = await require('../lib/otp').request(phone, {
      purpose: phonePurpose(req.user.id), ip: require('../middleware/ratelimit').clientIp(req), app: 'shop', shopId: req.shopId || '',
    });
    res.json({
      ok: true, phone, sent: out.sent === true, expiresAt: out.expiresAt, resendSeconds: out.resendSeconds,
      ...(out.devCode ? { devCode: out.devCode } : {}),
    });
  } catch (err) { next(err); }
});

router.put('/', async (req, res) => {
  const name = v.text(req.body?.name, { max: 80 });
  const phone = v.phone(req.body?.phone);

  //  شماره‌ای که آمده و کاربر از قبل شماره داشته: بی‌صدا نادیده گرفته
  //  نمی‌شود، وگرنه برنامه فکر می‌کند ثبت شد
  if (phone && req.user.phone && phone !== req.user.phone) return phoneLocked(res);

  const claiming = phone && !req.user.phone;
  //  شماره «راهِ ورود» است — افزودنش کارِ حساس است (بندِ ۲۱.۱، AUTH-08)
  if (claiming) await reauth.assertFresh(req);
  const withCode = claiming && await phoneCodeRequired();
  if (withCode) {
    const code = String(req.body?.phoneCode ?? '').trim();
    if (!code) {
      return res.status(400).json({
        error: { code: 'phone_code_required', message: 'برای افزودنِ شماره، کدی را که به همان شماره فرستاده شد وارد کنید' },
      });
    }
    //  ⛔ پیش از «این شماره گرفته شده»: فقط کسی که کدِ همان شماره را دارد آن را می‌فهمد
    await require('../lib/otp').verify(phone, code, { purpose: phonePurpose(req.user.id) });
  }
  if (claiming) {
    const taken = await one('SELECT id FROM users WHERE phone=$1 AND id<>$2', [phone, req.user.id]);
    if (taken) return phoneTaken(res);
  }

  let user;
  try {
    user = await one(
      `UPDATE users SET name=$2, phone=COALESCE($3, phone), updated_at=$4
       WHERE id=$1 RETURNING *`,
      [req.user.id, name, claiming ? phone : null, now()]
    );
  } catch (err) {
    //  دو حسابِ هم‌زمان با یک شماره: یکتاییِ `users.phone` دومی را رد می‌کند — ۴۰۹، نه ۵۰۰
    if (claiming && err && err.code === '23505') return phoneTaken(res);
    throw err;
  }
  if (claiming) {
    await audit.log({
      shopId: req.shopId, userId: req.user.id, action: 'account.phone_added', detail: withCode ? { method: 'code' } : {},
    });
  }
  res.json({ user: publicUser(user) });
});

/*
 *  ══ توکنِ پوش — «برنامه بسته باشد هم خبر بگیرد» ═══════════════════
 *
 *  ⚠️ تا امروز تنها جای ثبتِ توکن `/api/support/push` بود — یعنی برای
 *  گرفتنِ خبرِ «کالا تمام شد» باید از درِ **پشتیبانی** وارد می‌شدی.
 *  اسمش هیچ ربطی به کارش نداشت، و هیچ برنامه‌ای هم پیدایش نکرده بود.
 *
 *  آن یکی سرِ جایش ماند (نسخه‌های امروزِ دستِ کاربر نباید بشکنند) ولی
 *  جای درستش این‌جاست.
 */
router.post('/push', async (req, res, next) => {
  try {
    await require('../lib/push').register({
      app: req.appSection || 'shop',
      token: v.text(req.body?.token, { max: 500, required: true, field: 'توکن پوش' }),
      provider: v.oneOf(req.body?.provider, ['fcm', 'webpush'], { field: 'سرویس', def: 'fcm' }),
      userId: req.user.id,
      //  ⚠️ دکان هم ثبت می‌شود: خبرِ «کالا تمام شد» مالِ دکان است نه
      //  مالِ یک نفر، و باید به هر گوشی‌ای که به آن دکان وصل است برسد
      shopId: req.shopId || '',
      deviceUid: req.device ? (req.device.device_uid || req.device.id) : '',
      platform: v.text(req.body?.platform, { max: 20 }),
    });
    res.status(201).json({ ok: true });
  } catch (err) { next(err); }
});

router.delete('/push', async (req, res, next) => {
  try {
    await require('../lib/push').unregister(v.text(req.body?.token || req.query?.token, {
      max: 500, required: true, field: 'توکن پوش',
    }));
    res.json({ ok: true });
  } catch (err) { next(err); }
});

/*
 *  پشتیبانِ همین دکان — `/api/me/backups`.
 *
 *  ⚠️ بالاتر `optionalShop` است، نه `requireShop`: صفحهٔ «من» برای
 *  کسی که هنوز دکان نساخته هم باز می‌شود. پس `req.shopId` می‌تواند
 *  خالی باشد و خودِ مسیرِ پشتیبان همان‌جا `no_shop` می‌گوید — نه
 *  این‌که کلِ `/me` برای او ۴۰۳ شود.
 */
router.use('/backups', require('./account-backups').makeRouter(
  'shop',
  (req) => req.shopId || '',
  {
    actor: (req) => ({ userId: req.user ? req.user.id : '', deviceId: req.device ? req.device.id : '' }),
    canDelete: (req) => req.role === 'owner' || req.role === 'manager',
  }
));

module.exports = router;
module.exports.subscriptionHandler = subscriptionHandler;
module.exports.plansHandler = plansHandler;
module.exports.purchaseRequestHandler = purchaseRequestHandler;
