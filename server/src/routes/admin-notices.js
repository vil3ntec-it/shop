'use strict';
/**
 * مرکزِ اعلان — سمتِ مدیر (`/api/admin/notices` و `/api/admin/notice-templates`).
 *
 * زیرِ همان `requireAdmin`ِ `routes/admin.js` می‌نشیند؛ خودش هم دارد تا
 * به تنهایی هم بسته باشد. منطق در `lib/notices.js` است.
 */
const express = require('express');
const v = require('../lib/validate');
const notices = require('../lib/notices');
const audit = require('../lib/audit');
const mailer = require('../lib/mailer');
const { requireAdmin } = require('../middleware/auth');
const { rateLimit } = require('../middleware/ratelimit');
const { badRequest } = require('../middleware/errors');

const router = express.Router();
router.use(requireAdmin);

/* ---------- قالب‌ها ---------- */

router.get('/notice-templates', async (req, res, next) => {
  try {
    res.json({
      templates: (await notices.listTemplates()).map(t => ({
        key: t.key, app: t.app, title: t.title, body: t.body, channels: t.channels,
        editable: !!t.editable, updatedAt: Number(t.updated_at),
      })),
      //  متغیرهایی که در متن کار می‌کنند — پنل همین را کنارِ ویرایشگر می‌گذارد
      variables: ['{نام}', '{برنامه}', '{روز-مانده}', '{قیمت}', '{کد-تخفیف}', '{دکان}', '{پمپ}', '{پلن}'],
    });
  } catch (err) { next(err); }
});

router.put('/notice-templates/:key', async (req, res, next) => {
  try {
    const app = v.oneOf(req.body?.app || req.query?.app, ['shop', 'pump', 'both'], { field: 'بخش', def: 'both' });
    const row = await notices.saveTemplate(req.params.key, app, req.body || {});
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.notice_template', targetId: `${row.key}/${row.app}` });
    res.json({ template: { key: row.key, app: row.app, title: row.title, body: row.body, channels: row.channels } });
  } catch (err) { next(err); }
});

/* ---------- اعلان‌ها ---------- */

router.get('/notices', async (req, res, next) => {
  try {
    const system = req.query?.system === undefined || req.query?.system === '' ? null : v.bool(req.query.system, false);
    const rows = await notices.list({
      status: v.text(req.query?.status, { max: 20 }),
      app: v.oneOf(req.query?.app, ['shop', 'pump', 'both'], { field: 'بخش', def: '' }),
      system,
      limit: v.integer(req.query?.limit, { min: 1, max: 500, def: 100 }),
      offset: v.integer(req.query?.offset, { min: 0, max: 1e6, def: 0 }),
    });
    res.json({ notices: rows.map(notices.shapeNotice) });
  } catch (err) { next(err); }
});

/**
 * ساختن. `templateKey` اگر بیاید و عنوان/متن نیامده باشد، از قالب پر
 * می‌شود. `send: true` همان لحظه می‌فرستد.
 */
router.post('/notices', async (req, res, next) => {
  try {
    const body = { ...(req.body || {}) };
    if (body.templateKey && (!body.title || !body.body)) {
      const tpl = await notices.templateFor(body.templateKey, body.app === 'pump' ? 'pump' : 'shop');
      if (!tpl) return next(badRequest('قالب پیدا نشد', 'template_not_found'));
      body.title = body.title || tpl.title;
      body.body = body.body || tpl.body;
      body.channels = body.channels || tpl.channels;
    }
    const row = await notices.create(body, { createdBy: req.admin.id });
    let sent = null;
    if (body.send === true) sent = await notices.send(row.id, { by: req.admin.id });
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.notice_created', targetType: 'notice', targetId: row.id });
    res.status(201).json({ notice: notices.shapeNotice(await notices.get(row.id)), sent });
  } catch (err) { next(err); }
});

router.get('/notices/:id', async (req, res, next) => {
  try {
    const row = await notices.get(v.id(req.params.id));
    if (!row) return res.status(404).json({ error: { code: 'notice_not_found', message: 'اعلان پیدا نشد' } });
    res.json({ notice: notices.shapeNotice(row) });
  } catch (err) { next(err); }
});

router.put('/notices/:id', async (req, res, next) => {
  try {
    const row = await notices.update(v.id(req.params.id), req.body || {});
    res.json({ notice: notices.shapeNotice(row) });
  } catch (err) { next(err); }
});

router.delete('/notices/:id', async (req, res, next) => {
  try {
    await notices.remove(v.id(req.params.id));
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.notice_deleted', targetId: req.params.id });
    res.json({ ok: true });
  } catch (err) { next(err); }
});

/** پیش‌نمایش: گیرنده‌ها و متنِ پرشده برای چند نفرِ اول، و HTMLِ ایمیل. */
router.post('/notices/:id/preview', async (req, res, next) => {
  try {
    res.json(await notices.preview(v.id(req.params.id), {
      limit: v.integer(req.body?.limit ?? req.query?.limit, { min: 1, max: 100, def: 20 }),
    }));
  } catch (err) { next(err); }
});

/** ارسالِ آزمایشی به نشانیِ خودِ مدیر — هیچ ردیفی در گزارش نمی‌نشیند. */
router.post('/notices/:id/test', rateLimit({ max: 20, keyPrefix: 'admin-notice-test' }), async (req, res, next) => {
  try {
    //  مدیر نشانیِ ایمیل ندارد (جدولِ admins ستونش را ندارد)؛ پس یا در
    //  بدنه می‌آید یا همان فرستندهٔ تنظیمِ ایمیل
    const to = v.text(req.body?.to, { max: 160 }) || (await mailer.current()).from;
    try {
      res.json(await notices.sendTest(v.id(req.params.id), to));
    } catch (err) {
      if (err.status) return next(err);
      //  خطای سرورِ ایمیل، خطای سرورِ ما نیست
      res.json({ ok: false, error: String(err.message || err).slice(0, 400) });
    }
  } catch (err) { next(err); }
});

/** فرستادن، همین حالا. */
router.post('/notices/:id/send', rateLimit({ max: 200, keyPrefix: 'admin-notice-send' }), async (req, res, next) => {
  try {
    const out = await notices.send(v.id(req.params.id), { by: req.admin.id });
    res.json(out);
  } catch (err) { next(err); }
});

/** زمان‌بندی (یا برداشتنش با `scheduleAt: null`). */
router.post('/notices/:id/schedule', async (req, res, next) => {
  try {
    const at = req.body?.scheduleAt === null ? null : v.timestamp(req.body?.scheduleAt, { def: null });
    if (at !== null && at <= Date.now()) return next(badRequest('زمانِ ارسال باید در آینده باشد', 'bad_schedule'));
    const row = await notices.update(v.id(req.params.id), {
      scheduleAt: at,
      repeat: v.oneOf(req.body?.repeat, ['none', 'monthly'], { field: 'تکرار', def: 'none' }),
    });
    res.json({ notice: notices.shapeNotice(row) });
  } catch (err) { next(err); }
});

/** گزارشِ ارسال — هر گیرنده، هر کانال، حالِ واقعی. */
router.get('/notices/:id/report', async (req, res, next) => {
  try {
    res.json(await notices.report(v.id(req.params.id), {
      limit: v.integer(req.query?.limit, { min: 1, max: 5000, def: 500 }),
    }));
  } catch (err) { next(err); }
});

/** پیش‌نمایشِ گیرنده‌ها بی ساختنِ اعلان — برای فرمِ پنل («۱۲ نفر»). */
router.post('/notices/audience', async (req, res, next) => {
  try {
    const app = v.oneOf(req.body?.app, ['shop', 'pump', 'both'], { field: 'بخش', def: 'shop' });
    const list = await notices.resolveAudience(app, req.body?.audience || {});
    res.json({
      count: list.length,
      recipients: list.slice(0, 200).map(r => ({
        app: r.app, userId: r.userId, tenantId: r.tenantId, name: r.name, tenantName: r.tenantName,
        email: r.email ? r.email : '', city: r.city, plan: r.plan, status: r.status, daysLeft: r.daysLeft, permanent: r.permanent,
      })),
    });
  } catch (err) { next(err); }
});

/** اجرای دستیِ زمان‌بند و بررسیِ روزانه — برای وقتی مدیر نمی‌خواهد تا فردا صبر کند. */
router.post('/notices/run-system', async (req, res, next) => {
  try {
    const out = await notices.checkSystem();
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.notice_system_run', detail: out });
    res.json(out);
  } catch (err) { next(err); }
});

module.exports = router;
