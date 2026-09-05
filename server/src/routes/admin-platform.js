'use strict';
/**
 * بخش‌های تازه‌ی پنل مدیریت.
 *
 * از `routes/admin.js` جداست تا آن فایل هزار خطی نشود؛ هر دو زیر همان
 * `requireAdmin` سوار می‌شوند، پس قاعده‌ی دسترسی یکی است.
 *
 * اینجا هفت چیز هست که تا امروز در پنل نبود:
 *   • تخفیف روی نرخ‌های وی‌آی‌پی
 *   • کد وی‌آی‌پی، با ایمیلی که خودِ سرور می‌فرستد
 *   • بازدیدکننده‌ها — از جمله مهمان‌هایی که هنوز حساب نساخته‌اند
 *   • چت پشتیبانی
 *   • اشتراک‌هایی که دارند تمام می‌شوند
 *   • برنامه‌ها و سایت‌های دیگرِ صاحب سامانه
 *   • تنظیمات ایمیل و پوش
 */
const express = require('express');
const { one, many, now } = require('../db');
const v = require('../lib/validate');
const plans = require('../lib/plans');
const mailer = require('../lib/mailer');
const push = require('../lib/push');
const vip = require('../lib/vip-codes');
const visitors = require('../lib/visitors');
const support = require('../lib/support');
const apps = require('../lib/managed-apps');
const subs = require('../lib/subscriptions');
const audit = require('../lib/audit');
const { sanitizeFeatures } = require('../lib/features');
const { requireSuperAdmin } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { badRequest, notFound } = require('../middleware/errors');

const router = express.Router();

/* ==========================================================
   تخفیف روی پلن‌ها
   ==========================================================
   قیمتِ اصلی دست نمی‌خورد؛ تخفیف کنارش می‌نشیند و وقتی مهلتش تمام شد
   خودبه‌خود برمی‌گردد. پس مدیر لازم نیست عددِ قبلی را جایی یادداشت کند.
*/

router.put('/plans/:code/discount', async (req, res, next) => {
  try {
    const code = v.text(req.params.code, { max: 20, required: true, field: 'کد پلن' });
    const p = await plans.getPlan(code);
    if (!p) return next(notFound('پلن پیدا نشد'));

    const percent = v.integer(req.body?.percent, { field: 'درصد تخفیف', min: 0, max: 95, def: 0 });
    const price = req.body?.price === undefined || req.body?.price === null || req.body?.price === ''
      ? null : v.integer(req.body.price, { field: 'قیمت با تخفیف', min: 0, max: 1e7 });
    const label = v.text(req.body?.label, { max: 40 });
    const until = v.timestamp(req.body?.until, { def: null });

    if (price !== null && price >= p.price_afn) {
      return next(badRequest('قیمت با تخفیف باید کمتر از قیمت اصلی باشد', 'bad_discount'));
    }
    if (until !== null && until <= now()) {
      return next(badRequest('مهلت تخفیف باید در آینده باشد', 'bad_until'));
    }

    const row = await one(
      `UPDATE plans SET discount_percent=$2, discount_price=$3, discount_label=$4,
              discount_until=$5, updated_at=$6 WHERE code=$1 RETURNING *`,
      [code, percent, price, label, until, now()]
    );
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.plan_discount',
      targetType: 'plan', targetId: code, detail: { percent, price, until },
    });
    res.json({ plan: plans.shapePlan(row) });
  } catch (err) { next(err); }
});

/** برداشتن تخفیف — قیمت به همان عددِ اصلی برمی‌گردد. */
router.delete('/plans/:code/discount', async (req, res, next) => {
  try {
    const code = v.text(req.params.code, { max: 20, required: true, field: 'کد پلن' });
    const row = await one(
      `UPDATE plans SET discount_percent=0, discount_price=NULL, discount_label='',
              discount_until=NULL, updated_at=$2 WHERE code=$1 RETURNING *`,
      [code, now()]
    );
    if (!row) return next(notFound('پلن پیدا نشد'));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.plan_discount_cleared', targetId: code });
    res.json({ plan: plans.shapePlan(row) });
  } catch (err) { next(err); }
});

/* ==========================================================
   کد وی‌آی‌پی
   ========================================================== */

router.get('/vip-codes', async (req, res, next) => {
  try {
    res.json({
      codes: await vip.list({
        status: v.text(req.query?.status, { max: 20 }),
        limit: v.integer(req.query?.limit, { min: 1, max: 300, def: 100 }),
      }),
    });
  } catch (err) { next(err); }
});

/**
 * ساخت کد و — اگر ایمیل داده شده باشد — فرستادنش.
 *
 * کدِ خام فقط همین یک بار در پاسخ می‌آید. بعد از این، حتی خودِ سرور هم
 * نمی‌تواند نشانش بدهد؛ پس مدیر یا همان لحظه برش می‌دارد یا می‌گذارد
 * ایمیل کارش را بکند.
 */
router.post('/vip-codes', async (req, res, next) => {
  try {
    const email = v.text(req.body?.email, { max: 160 });
    if (email && !email.includes('@')) return next(badRequest('نشانی ایمیل درست نیست', 'bad_email'));

    const { code, row } = await vip.create({
      plan: v.text(req.body?.plan, { max: 20 }) || 'custom',
      days: req.body?.days === undefined || req.body?.days === null || req.body?.days === ''
        ? null : v.integer(req.body.days, { field: 'روزها', min: 1, max: 3650 }),
      features: req.body?.features ? sanitizeFeatures(req.body.features) : [],
      maxDevices: v.integer(req.body?.maxDevices, { field: 'تعداد دستگاه', min: 1, max: 100, def: 10 }),
      note: v.text(req.body?.note, { max: 300 }),
      email: email ? email.toLowerCase() : '',
      shopId: req.body?.shopId ? v.id(req.body.shopId) : null,
      expiresInDays: v.integer(req.body?.expiresInDays, { field: 'مهلت', min: 0, max: 365, def: 30 }),
      createdBy: req.admin.id,
    });

    //  ایمیل همین‌جا و همین حالا. نتیجه‌اش — رفت یا نرفت و چرا — در همان
    //  ردیف می‌نشیند، پس مدیر «ساخته شد» نمی‌بیند در حالی که چیزی بیرون
    //  نرفته.
    const finalRow = email ? await vip.mail(row.id, code) : row;

    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.vip_code_created',
      targetType: 'vip_code', targetId: row.id,
      detail: { plan: row.plan, days: row.days, email: email ? 'yes' : 'no' },
    });

    res.status(201).json({
      code,                       // فقط همین یک بار
      vipCode: finalRow,
      emailStatus: finalRow.emailStatus,
      emailError: finalRow.emailError,
    });
  } catch (err) { next(err); }
});

router.post('/vip-codes/:id/revoke', async (req, res, next) => {
  try {
    const row = await vip.revoke(v.id(req.params.id));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.vip_code_revoked', targetId: row.id });
    res.json({ vipCode: row });
  } catch (err) { next(err); }
});

/* ==========================================================
   بازدیدکننده‌ها
   ========================================================== */

router.get('/visitors', async (req, res, next) => {
  try {
    res.json({
      visitors: await visitors.list({
        app: v.text(req.query?.app, { max: 40 }),
        onlyGuests: v.bool(req.query?.guests, false),
        q: v.text(req.query?.q, { max: 60 }),
        limit: v.integer(req.query?.limit, { min: 1, max: 300, def: 100 }),
        offset: v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 }),
      }),
      summary: await visitors.summary({ app: v.text(req.query?.app, { max: 40 }) }),
    });
  } catch (err) { next(err); }
});

/* ==========================================================
   چت پشتیبانی
   ========================================================== */

router.get('/support/threads', async (req, res, next) => {
  try {
    res.json({
      threads: await support.list({
        status: v.text(req.query?.status, { max: 20 }),
        q: v.text(req.query?.q, { max: 60 }),
        limit: v.integer(req.query?.limit, { min: 1, max: 200, def: 100 }),
        offset: v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 }),
      }),
      unread: await support.unreadForAdmin(),
    });
  } catch (err) { next(err); }
});

router.get('/support/threads/:id', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const row = await one('SELECT * FROM support_threads WHERE id=$1', [id]);
    if (!row) return next(notFound('این گفت‌وگو پیدا نشد', 'thread_not_found'));
    const after = v.integer(req.query?.after, { min: 0, max: 1e15, def: 0 });
    //  باز کردنِ گفت‌وگو یعنی مدیر دیدش
    if (!after) await support.markRead(id, 'admin');
    res.json({
      thread: support.shapeThread(row),
      messages: await support.messages(id, { after }),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

router.post('/support/threads/:id/messages', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const body = v.text(req.body?.body ?? req.body?.text, {
      max: support.MAX_BODY, required: true, field: 'پیام',
    });
    const message = await support.post(id, {
      sender: 'admin',
      senderId: req.admin.id,
      senderName: req.admin.name || req.admin.username,
      body,
    });
    await support.markRead(id, 'admin');
    res.status(201).json({ message });
  } catch (err) { next(err); }
});

router.post('/support/threads/:id/status', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const status = v.oneOf(req.body?.status, ['open', 'pending', 'closed'], { field: 'وضعیت' });
    res.json({ thread: await support.setStatus(id, status) });
  } catch (err) { next(err); }
});

/**
 * پیام همگانی — به همه‌ی دکان‌دارها یا به گروهی از آن‌ها.
 *
 * محدود شده به دکان‌هایی که مدیر انتخاب می‌کند؛ «همه» هم سقف دارد تا
 * یک اشتباه، هزار پیام نفرستد.
 */
router.post('/support/broadcast', rateLimit({ max: 5, keyPrefix: 'admin-broadcast' }), async (req, res, next) => {
  try {
    const body = v.text(req.body?.body, { max: support.MAX_BODY, required: true, field: 'پیام' });
    const target = v.oneOf(req.body?.target, ['expiring', 'active', 'all'], { field: 'گیرنده', def: 'expiring' });
    const limit = v.integer(req.body?.limit, { min: 1, max: 500, def: 200 });

    let owners = [];
    if (target === 'expiring') {
      const rows = await subs.expiringSoon({ withinDays: 7, includeExpired: 3, limit });
      owners = rows.map(r => ({ userId: r.ownerUserId, shopId: r.shopId, name: r.ownerName }));
    } else {
      const rows = await many(
        `SELECT s.id AS shop_id, s.owner_user_id, u.name
           FROM shops s JOIN users u ON u.id = s.owner_user_id
          WHERE s.status='active'
            AND ($1 = 'all' OR EXISTS (
                  SELECT 1 FROM subscriptions x
                   WHERE x.shop_id = s.id AND x.status='active'))
          ORDER BY s.created_at DESC LIMIT $2`,
        [target, limit]
      );
      owners = rows.map(r => ({ userId: r.owner_user_id, shopId: r.shop_id, name: r.name }));
    }

    let sent = 0;
    for (const o of owners) {
      try {
        await support.systemMessage({ userId: o.userId, shopId: o.shopId, who: o.name, body });
        sent++;
      } catch (err) {
        console.error('[broadcast]', err.message);
      }
    }
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.broadcast',
      detail: { target, sent },
    });
    res.json({ sent, targets: owners.length });
  } catch (err) { next(err); }
});

/* ==========================================================
   اشتراک‌های رو به پایان
   ========================================================== */

router.get('/subscriptions/expiring', async (req, res, next) => {
  try {
    await subs.expireDue();
    res.json({
      expiring: await subs.expiringSoon({
        withinDays: v.integer(req.query?.days, { field: 'روزها', min: 1, max: 90, def: 7 }),
        includeExpired: v.integer(req.query?.expired, { field: 'روزها', min: 0, max: 90, def: 3 }),
        limit: v.integer(req.query?.limit, { min: 1, max: 300, def: 100 }),
      }),
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

/** خبر دادن دستی به کسانی که اشتراکشان نزدیک پایان است. */
router.post('/subscriptions/notify-expiring', async (req, res, next) => {
  try {
    const out = await subs.notifyExpiring();
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.expiry_notified', detail: out });
    res.json(out);
  } catch (err) { next(err); }
});

/* ==========================================================
   برنامه‌ها و سایت‌های دیگر
   ========================================================== */

router.get('/apps', async (req, res, next) => {
  try {
    res.json({ apps: await apps.list({ includeArchived: v.bool(req.query?.archived, false) }) });
  } catch (err) { next(err); }
});

router.post('/apps', async (req, res, next) => {
  try {
    const app = await apps.create(req.body || {});
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.app_created', targetId: app.id, detail: { slug: app.slug } });
    res.status(201).json({ app });
  } catch (err) { next(err); }
});

router.put('/apps/:id', async (req, res, next) => {
  try {
    const app = await apps.update(v.id(req.params.id), req.body || {});
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.app_updated', targetId: app.id });
    res.json({ app });
  } catch (err) { next(err); }
});

router.delete('/apps/:id', async (req, res, next) => {
  try {
    const app = await apps.remove(v.id(req.params.id));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.app_archived', targetId: app.id });
    res.json({ app });
  } catch (err) { next(err); }
});

/** کلید تازه. خام فقط همین یک بار برمی‌گردد. */
router.post('/apps/:id/key', requireSuperAdmin, async (req, res, next) => {
  try {
    const out = await apps.rotateKey(v.id(req.params.id));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.app_key_rotated', targetId: out.app.id });
    res.json(out);
  } catch (err) { next(err); }
});

/** سنجیدن سلامتِ همه — از سرور، نه از گوشیِ مدیر. */
router.post('/apps/health', async (req, res, next) => {
  try {
    res.json({ checked: await apps.checkHealth(), apps: await apps.list() });
  } catch (err) { next(err); }
});

/* ==========================================================
   تنظیمات ایمیل
   ========================================================== */

router.get('/email', async (req, res, next) => {
  try {
    res.json({ email: await mailer.masked() });
  } catch (err) { next(err); }
});

router.put('/email', async (req, res, next) => {
  try {
    const body = req.body || {};
    const patch = {};
    if (body.provider !== undefined) {
      patch.provider = v.oneOf(body.provider, ['log', 'smtp', 'api'], { field: 'راه ارسال' });
    }
    if (body.secure !== undefined) {
      patch.secure = v.oneOf(body.secure, ['ssl', 'starttls', 'none'], { field: 'حالت رمزنگاری' });
    }
    for (const name of ['from', 'fromName', 'host', 'user', 'url', 'otpSubject', 'otpTemplate']) {
      if (body[name] !== undefined) patch[name] = v.text(body[name], { max: 1000, field: name });
    }
    if (body.port !== undefined) patch.port = String(v.integer(body.port, { field: 'پورت', min: 1, max: 65535, def: 587 }));

    //  رمز و کلید فقط وقتی عوض می‌شوند که مدیر واقعاً چیزی نوشته باشد —
    //  چون آن‌ها را نمی‌بیند، پس نباید بتواند ندانسته پاکشان کند
    if (body.clearPass === true) patch.pass = '';
    else if (typeof body.pass === 'string' && body.pass.trim()) patch.pass = body.pass.trim();
    if (body.clearKey === true) patch.key = '';
    else if (typeof body.key === 'string' && body.key.trim()) patch.key = body.key.trim();

    const saved = await mailer.save(patch);
    await audit.log({
      actorType: 'admin', userId: req.admin.id, action: 'admin.email_settings',
      detail: { provider: saved.provider, host: saved.host, passChanged: patch.pass !== undefined },
    });
    res.json({ email: await mailer.masked() });
  } catch (err) { next(err); }
});

/**
 * آزمایش واقعی — یک ایمیل به نشانیِ خودِ مدیر.
 *
 * خطای سرویس، خطای سرورِ ما نیست: پاسخ ۲۰۰ با `ok:false` می‌آید تا
 * برنامه‌ی مدیریت بتواند متنِ خودِ سرورِ ایمیل را نشان بدهد — همان که
 * می‌گوید دقیقاً چه چیزی غلط است.
 */
router.post('/email/test', rateLimit({ max: 10, keyPrefix: 'admin-email-test' }), async (req, res, next) => {
  try {
    const to = v.text(req.body?.to, { max: 160, required: true, field: 'ایمیل' });
    if (!to.includes('@')) return next(badRequest('نشانی ایمیل درست نیست', 'bad_email'));
    try {
      const out = await mailer.send({
        to,
        subject: 'آزمایش ایمیل توحید',
        text: 'اگر این را می‌بینید، ایمیل سرور درست کار می‌کند.',
        html: mailer.card({
          title: 'ایمیل کار می‌کند',
          lead: 'این یک پیام آزمایشی از سرور توحید است.',
          body: 'حالا کد ثبت‌نام و کد اشتراک هم به همین شکل برای کاربران می‌رود.',
        }),
      });
      await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.email_test', detail: { to, via: out.via } });
      res.json({ ok: true, via: out.via, response: out.response || '' });
    } catch (err) {
      res.json({ ok: false, error: String(err.message || err).slice(0, 400) });
    }
  } catch (err) { next(err); }
});

/* ==========================================================
   تنظیمات پوش
   ========================================================== */

router.get('/push', async (req, res, next) => {
  try {
    res.json({ push: await push.masked() });
  } catch (err) { next(err); }
});

router.put('/push', requireSuperAdmin, async (req, res, next) => {
  try {
    const out = await push.save({
      enabled: req.body?.enabled === undefined ? undefined : v.bool(req.body.enabled, false),
      serviceAccount: typeof req.body?.serviceAccount === 'string' ? req.body.serviceAccount : undefined,
      clearServiceAccount: req.body?.clearServiceAccount === true,
    });
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.push_settings' });
    res.json({ push: out });
  } catch (err) {
    if (err instanceof SyntaxError) return next(badRequest('فایل حساب سرویس یک JSON درست نیست', 'bad_json'));
    if (!err.status) return next(badRequest(err.message, 'bad_service_account'));
    next(err);
  }
});

/** ثبت توکنِ پوشِ خودِ برنامه‌ی مدیریت — تا پیام کاربر به گوشیِ مدیر برسد. */
router.post('/push/register', async (req, res, next) => {
  try {
    const token = v.text(req.body?.token, { max: 500, required: true, field: 'توکن پوش' });
    await push.register({
      app: 'admin', token, adminId: req.admin.id,
      deviceUid: v.id(req.body?.deviceUid || '', { required: false, max: 64 }),
      platform: v.text(req.body?.platform, { max: 20 }),
    });
    res.status(201).json({ ok: true });
  } catch (err) { next(err); }
});

/* ==========================================================
   خلاصه‌ی خانه
   ==========================================================
   یک درخواست به‌جای هفت‌تا. صفحه‌ی خانه‌ی برنامه‌ی مدیریت روی نتِ ضعیف
   هفت بار منتظر می‌ماند؛ این‌طور یک بار.
*/
router.get('/overview', async (req, res, next) => {
  try {
    await subs.expireDue();
    const [expiring, unread, visitorSummary, appList, emailCfg, pushCfg, activeCodes] = await Promise.all([
      subs.expiringSoon({ withinDays: 7, includeExpired: 3, limit: 50 }),
      support.unreadForAdmin(),
      visitors.summary({}),
      apps.list(),
      mailer.masked(),
      push.masked(),
      one(`SELECT COUNT(*)::int n FROM vip_codes WHERE status='active'`),
    ]);
    res.json({
      expiring,
      expiringCount: expiring.filter(e => e.daysLeft >= 0).length,
      supportUnread: unread,
      visitors: visitorSummary,
      apps: appList,
      email: { ready: emailCfg.ready, provider: emailCfg.provider, missing: emailCfg.missing },
      push: { enabled: pushCfg.enabled, configured: pushCfg.configured, devices: pushCfg.devices },
      vipCodesActive: activeCodes.n,
      serverTime: now(),
    });
  } catch (err) { next(err); }
});

module.exports = router;
