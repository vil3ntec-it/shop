'use strict';
/**
 * مسیرهای پشتیبانِ هر حساب — یک تن کد، سه در.
 *
 *   GET    …/backups        فهرستِ پشتیبان‌های همین حساب + سهمش
 *   POST   …/backups        فرستادنِ یک پشتیبانِ تازه (بدنه = خودِ فایل)
 *   GET    …/backups/:id    برگرداندنِ همان فایل
 *   DELETE …/backups/:id    پاک کردنش
 *
 * سه جا سوار می‌شود و هر سه همین را می‌گیرند:
 *   `/api/me/backups`            دکان‌دار، با توکنِ حساب
 *   `/api/pump/backups`          صاحبِ پمپ، با توکنِ حساب
 *   `/api/pump/device/backups`   برنامهٔ کامپیوترِ پمپ، با توکنِ دستگاه
 *
 * ⚠️ **شناسهٔ حساب هیچ‌وقت از درخواست خوانده نمی‌شود.** هر در `tenantOf`
 * خودش را می‌دهد و آن از میان‌افزارِ احراز هویت می‌آید (`req.shopId`،
 * `req.stationId`). همان قاعدهٔ `middleware/auth.js`: عوض کردنِ یک
 * شناسه در بدنه نباید کسی را به پوشهٔ دیگری برساند.
 *
 * ⚠️ **بدنهٔ این مسیر JSON نیست.** `express.json` سراسری فقط
 * `application/json` را می‌خواند، پس فایلِ خام بی‌دست‌خوردگی رد می‌شود
 * و همین‌جا با `express.raw` برداشته می‌شود — همان کاری که مسیرِ
 * رسانهٔ چتِ پمپ می‌کند.
 */
const express = require('express');
const { now } = require('../db');
const v = require('../lib/validate');
const backups = require('../lib/account-backups');
const config = require('../config');
const audit = require('../lib/audit');
const { clientIp } = require('../middleware/ratelimit');
const { badRequest, forbidden } = require('../middleware/errors');

/**
 * @param {string} app 'shop' | 'pump'
 * @param {(req)=>string} tenantOf شناسهٔ حساب، فقط از میان‌افزار
 * @param {object} opts
 */
function makeRouter(app, tenantOf, { actor = () => ({}) } = {}) {
  const store = backups.forApp(app);
  const router = express.Router();

  /** شناسه، یا خطایی که می‌گوید چرا این حساب دفتری ندارد. */
  function need(req) {
    const id = tenantOf(req);
    if (!id) {
      throw forbidden(
        app === 'pump' ? 'برای این حساب پمپی ثبت نشده است' : 'برای این حساب دکانی ثبت نشده است',
        app === 'pump' ? 'no_station' : 'no_shop'
      );
    }
    return id;
  }

  router.get('/', async (req, res, next) => {
    try {
      const id = need(req);
      res.json({
        backups: await store.list(id, { limit: v.integer(req.query?.limit, { min: 1, max: 500, def: 100 }) }),
        stats: await store.stats(id),
        serverTime: now(),
      });
    } catch (err) { next(err); }
  });

  router.post(
    '/',
    //  سقفِ خواننده یک پله بالاتر از سقفِ خودمان است تا پیامِ خطا از
    //  **ما** بیاید («از سقف بزرگ‌تر است») نه از خواننده که فقط
    //  `body_too_large`ِ بی‌جزئیات می‌دهد.
    express.raw({ type: () => true, limit: config.backup.account.maxBytes + 1024 * 1024 }),
    async (req, res, next) => {
      try {
        const id = need(req);
        const data = Buffer.isBuffer(req.body) ? req.body : null;
        if (!data || data.length === 0) throw badRequest('فایلِ پشتیبان خالی است', 'empty_backup');

        const out = await store.save(id, data, {
          label: v.text(req.query?.label ?? req.headers['x-backup-label'], { max: 200 }),
          kind: v.text(req.query?.kind, { max: 20 }),
          ext: v.text(req.query?.ext, { max: 10 }),
          appVersion: v.text(req.headers['x-app-version'], { max: 40 }),
          ...actor(req),
        });

        await audit.log({
          shopId: app === 'shop' ? id : '',
          userId: req.user ? req.user.id : '',
          action: `${app}.backup_uploaded`,
          detail: { bytes: out.backup.bytes, pruned: out.pruned.length },
          ip: clientIp(req),
        }).catch(() => {});

        res.status(201).json({ ...out, serverTime: now() });
      } catch (err) { next(err); }
    }
  );

  router.get('/:id', async (req, res, next) => {
    try {
      const id = need(req);
      const { row, data } = await store.read(id, v.text(req.params.id, { max: 64, required: true, field: 'شناسه' }));
      res.set('Content-Type', 'application/octet-stream');
      res.set('Content-Length', String(data.length));
      //  ⚠️ نامِ فایل را خودمان ساخته‌ایم و فقط حرف و رقم و نقطه دارد،
      //  پس هیچ‌چیزی برای فرار از گیومه در آن نیست.
      res.set('Content-Disposition', `attachment; filename="${row.name}"`);
      res.set('X-Backup-Sha256', row.sha256);
      res.set('X-Backup-Created-At', String(row.createdAt));
      res.send(data);
    } catch (err) { next(err); }
  });

  router.delete('/:id', async (req, res, next) => {
    try {
      const id = need(req);
      const gone = await store.remove(id, v.text(req.params.id, { max: 64, required: true, field: 'شناسه' }));
      await audit.log({
        shopId: app === 'shop' ? id : '',
        userId: req.user ? req.user.id : '',
        action: `${app}.backup_deleted`,
        detail: { id: gone.id },
        ip: clientIp(req),
      }).catch(() => {});
      res.json({ ok: true, backup: gone, stats: await store.stats(id), serverTime: now() });
    } catch (err) { next(err); }
  });

  return router;
}

module.exports = { makeRouter };
