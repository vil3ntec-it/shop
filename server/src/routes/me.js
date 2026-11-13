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
const { forbidden } = require('../middleware/errors');

const router = express.Router();

router.use(requireUser, optionalShop);

//  «حذفِ حساب» به دستِ خودِ کاربر (Google Play) — شرح: `routes/account-deletion.js`، پیش‌فرض خاموش (`account.self_delete`)
router.use('/account/deletion', require('./account-deletion'));

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
      //  A16: نقشِ تازه در فیلدِ قدیم «staff» (نسخهٔ قدیم)، نقشِ واقعی در `actualRole`
      role: require('../lib/permissions').legacyRole(req.member.role),
      actualRole: req.member.role,
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
 *  ══ اعلان‌های من · پرداخت‌های من ═══════════════════════════════════
 *  منطق در `routes/portal.js` است (همان که پورتالِ وب هم می‌خواند)؛
 *  این‌جا فقط با هویتِ نشستِ **دکان** صدا زده می‌شود.
 */
const portal = require('./portal');
router.get('/notices', (req, res, next) => portal.noticesHandler('shop', req, res, next));
router.post('/notices/:id/read', (req, res, next) => portal.readNoticeHandler('shop', req, res, next));
router.get('/payments', (req, res, next) => portal.paymentsHandler('shop', req, res, next));
router.get('/heartbeat', (req, res, next) => portal.heartbeatHandler('shop', req, res, next));
//  ⛔ برداشته (۱۴۰۵/۰۸/۲۲، هیچ برنامه‌ای صدایشان نمی‌زد): `GET`/`PUT /settings` (شاگرد هم «همگام‌سازیِ ابری» ِ
//  کلِ دکان را عوض می‌کرد)، `POST /redeem` و `POST /errors`. همان کار برای برنامه‌ها: `/api/portal/settings`،
//  `/api/portal/redeem` (SDK)، `/api/vip/redeem` (کدِ VIP) و `/api/errors`؛ پمپ همان دستگیره‌ها را زیرِ `/api/pump` دارد.

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
  //  S117: یادداشتِ آزادِ درخواست (کاربر گاهی جزئیاتِ پرداخت را این‌جا می‌نویسد) بی شمارهٔ کاملِ کارت ذخیره می‌شود
  const note = require('../lib/ops-errors').maskCards(v.text(req.body?.note, { max: 300 }));
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
    //  O-01: دستگاهی که از آن بیرون رفت دیگر پوشِ این کس را نمی‌گیرد
    await require('../lib/push').revokeDevice({ deviceId: id, userId: req.user.id, reason: 'logout' });
    //  ⛔ S78: سوکتِ زندهٔ همان دستگاه هم در همین درخواست
    await require('../lib/sync-v1-live').accessChanged({ userId: req.user.id });
  }
  res.json({ ok: true });
});

/**
 * ویرایش حساب — نام، شمارهٔ تماس، و یک‌بار افزودنِ ایمیل.
 *
 * ⛔ «فقط ایمیل» (تصمیمِ صاحب‌کار ۱۴۰۵/۰۸/۲۳: «شماره توی برنامه بشه ثبت کرد ولی برای لاگین نه اصلن»):
 *   • شماره **فیلدِ داده** است، نه راهِ ورود: افزودن، تغییر و پاک (`""`) بی کد و بی تأییدِ دوباره؛ فقط شکلش
 *     (`validate.phone`) سنجیده می‌شود و یکتا نیست (مهاجرتِ ۸۸۰) — پس «این شماره گرفته شده» هم نیست.
 *     نیامدنِ `phone` یعنی دست نزن. حسابِ بی‌ایمیل شماره‌اش را پاک نمی‌کند (قیدِ `users_need_identifier`).
 *   • ایمیل راهِ ورود است و از این‌جا **عوض** نمی‌شود (۴۰۹ `email_locked`): کسی که یک بار توکن گرفته نباید
 *     بازیابیِ رمز را به ایمیلِ خودش ببرد.
 *   • AUTH-08 ⇐ ایمیل (درِ کدِ پیامکیِ شماره `POST /api/me/phone/code` و پرچمِ `auth.phone_add_code` رفتند ⇐
 *     به‌جایش): حسابِ **بی‌ایمیل** (کهنهٔ فقط‌شماره) ایمیل را فقط با نشستِ تازه (بندِ ۲۱.۱) و کدِ یک‌بارمصرفِ
 *     **همان ایمیل** و **همان حساب** می‌افزاید:
 *       ۱) `POST /api/me/email/code { email }` ⇒ کدِ `lib/otp` با هدفِ `email_add:<شناسهٔ حساب>`؛ «این ایمیل
 *          حسابِ دیگری دارد» این‌جا گفته نمی‌شود (بی شمارش‌پذیری).
 *       ۲) `PUT /api/me { email, emailCode }` ⇒ فقط پس از کدِ درست ۴۰۹ `email_taken`.
 *     صدازننده: «ویرایشِ حساب» ِ برنامهٔ مدیریت (`core/account/EmailClaimForm`).
 */
const EMAIL_ADD = (userId) => `email_add:${userId}`;

const emailLocked = (res) => res.status(409).json({
  error: { code: 'email_locked', message: 'ایمیلِ ثبت‌شده از این‌جا عوض نمی‌شود — با پشتیبانی تماس بگیرید' },
});
const emailTaken = (res) => res.status(409).json({
  error: { code: 'email_taken', message: 'این ایمیل روی حسابِ دیگری ثبت شده' },
});

//  هر حساب سطلِ خودش را دارد (کنارِ فاصله و سقفِ روزانهٔ خودِ `otp.request`)
const emailCodeLimit = require('../middleware/ratelimit').rateLimit({
  max: require('../config').rateLimit.otpMax, keyPrefix: 'email-add',
  key: (req) => (req.user ? req.user.id : null),
});

router.post('/email/code', emailCodeLimit, async (req, res, next) => {
  try {
    if (req.user.email) return emailLocked(res);
    await reauth.assertFresh(req);
    const email = v.email(req.body?.email, { required: true });
    const out = await require('../lib/otp').request(email, {
      purpose: EMAIL_ADD(req.user.id), ip: require('../middleware/ratelimit').clientIp(req), app: 'shop',
    });
    res.json({
      ok: true, email, sent: out.sent === true, expiresAt: out.expiresAt, resendSeconds: out.resendSeconds,
      ...(out.devCode ? { devCode: out.devCode } : {}),
    });
  } catch (err) { next(err); }
});

router.put('/', async (req, res) => {
  const name = v.text(req.body?.name, { max: 80 });
  const rawPhone = req.body?.phone;
  //  نیامد ⇒ دست نزن؛ "" ⇒ پاک؛ وگرنه فقط شکل
  const phone = rawPhone === undefined || rawPhone === null ? undefined : (String(rawPhone).trim() ? v.phone(rawPhone) : '');
  const rawEmail = typeof req.body?.email === 'string' ? req.body.email.trim() : '';
  const email = rawEmail ? v.email(rawEmail, { required: true }) : '';

  if (email && req.user.email && email !== req.user.email) return emailLocked(res);
  const claiming = Boolean(email) && !req.user.email;
  if (claiming) {
    await reauth.assertFresh(req);
    const code = String(req.body?.emailCode ?? '').trim();
    if (!code) {
      return res.status(400).json({
        error: { code: 'email_code_required', message: 'برای افزودنِ ایمیل، کدی را که به همان ایمیل فرستاده شد وارد کنید' },
      });
    }
    //  ⛔ پیش از «این ایمیل گرفته شده»: فقط کسی که کدِ همان ایمیل را دارد آن را می‌فهمد
    await require('../lib/otp').verify(email, code, { purpose: EMAIL_ADD(req.user.id) });
    const taken = await one('SELECT id FROM users WHERE email=$1 AND id<>$2', [email, req.user.id]);
    if (taken) return emailTaken(res);
  }
  const hasEmail = claiming || Boolean(req.user.email);
  if (phone === '' && !hasEmail) {
    return res.status(400).json({ error: { code: 'email_first', message: 'پیش از پاک کردنِ شماره، ایمیلِ حساب را بیفزایید' } });
  }

  let user;
  try {
    user = await one(
      `UPDATE users SET name=$2,
              phone = CASE WHEN $3::boolean THEN NULLIF($4, '') ELSE phone END,
              email = COALESCE($5, email), updated_at=$6
       WHERE id=$1 RETURNING *`,
      [req.user.id, name, phone !== undefined, phone || '', claiming ? email : null, now()]
    );
  } catch (err) {
    //  دو درخواستِ هم‌زمان با یک ایمیل: یکتاییِ `users.email` دومی را رد می‌کند — ۴۰۹، نه ۵۰۰
    if (claiming && err && err.code === '23505') return emailTaken(res);
    throw err;
  }
  if (claiming) {
    await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'account.email_added', detail: { method: 'code' } });
  }
  if (phone !== undefined && (phone || null) !== (req.user.phone || null)) {
    await audit.log({ shopId: req.shopId, userId: req.user.id, action: 'account.phone_changed', detail: { cleared: phone === '' } });
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
      //  `apns`: برنامهٔ آیفون (docs/IOS-PLAN-fa.md §۰.۹–۰.۱۰) — از درِ FCM نمی‌رود؛ فرستنده‌اش `lib/apns.js` (کلید از محیط)
      provider: v.oneOf(req.body?.provider, ['fcm', 'webpush', 'apns'], { field: 'سرویس', def: 'fcm' }),
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
//  A16: پشتیبانِ کاملِ دفتر را فقط نقشی می‌فرستد که همهٔ دفتر را می‌نویسد (صاحب، مدیر، شاگرد)
const canUploadBackup = (req) => require('../lib/perm-core').writable(req.role) === '*';
/*
 *  ⛔ ممیزیِ ۳۵ (`security-audit-35.test.js`) — نگهبانِ پیش از روترِ مشترکِ پشتیبان (پمپ همان روتر را بی این دارد):
 *   B2  دکان و نقش **پیش از** خواندنِ بدنهٔ تا ۶۵ مگابایتی: پیش‌تر هر حسابِ ثبت‌شده (حتی بی دکان یا جمع‌آور) سرور را
 *       وادار به نگه‌داشتنِ کلِ بدنه در حافظه می‌کرد و بعد ۴۰۳ می‌گرفت. پاسخ همان است، فقط زودتر.
 *   B1  (CRM-15) پشتیبان کلِ دفتر است با شماره و نشانیِ مشتری ⇒ با پرچمِ `shop.backup_contact_guard` (پیش‌فرض خاموش؛
 *       جمع‌آور و انباردارِ امروز تنظیم و عکس را از همین پشتیبان برمی‌گردانند) فقط کسی که `customers.contact` دارد.
 */
router.use('/backups', (req, res, next) => (async () => {
  if (req.method === 'POST' && req.path === '/') {
    if (!req.shopId) return next(forbidden('برای این حساب دکانی ثبت نشده است', 'no_shop'));
    if (!canUploadBackup(req)) return next(forbidden('فرستادنِ پشتیبان در حدِ نقشِ شما نیست', 'permission_denied'));
    return next();
  }
  if (req.method !== 'GET' || req.path === '/' || !req.shopId) return next();
  if (!(await require('../lib/orders').flagOf(req.shopId, 'shop.backup_contact_guard'))) return next();
  if (!(await require('../lib/customer-contact').hiddenForReq(req))) return next();
  next(forbidden('پشتیبانِ کاملِ دفتر شمارهٔ مشتریان را دارد؛ صاحب دکان دیدنِ آن را برای شما بسته است', 'permission_denied'));
})().catch(next));
router.use('/backups', require('./account-backups').makeRouter(
  'shop',
  (req) => req.shopId || '',
  {
    actor: (req) => ({ userId: req.user ? req.user.id : '', deviceId: req.device ? req.device.id : '' }),
    canDelete: (req) => req.role === 'owner' || req.role === 'manager',
    canUpload: canUploadBackup,
  }
));

module.exports = router;
module.exports.subscriptionHandler = subscriptionHandler;
module.exports.plansHandler = plansHandler;
module.exports.purchaseRequestHandler = purchaseRequestHandler;
