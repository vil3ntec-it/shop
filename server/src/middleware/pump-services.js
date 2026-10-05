'use strict';
/*
 *  ⛔ خدماتِ سرورِ پمپ فقط برای پلنی که دارد (۱۴۰۵/۰۷/۲۰) — ‎lib/pump-services.js‎
 *
 *  «استاندارد… اطلاعاتِ تانکِ تیلش به سرور نیاید و اصلاً به سرور وصل حتی
 *  نشه، ولی از سرور اشتراک بتونه دریافت کنه.» برنامه خودش این درها را
 *  نمی‌زند؛ این‌جا همان قاعده روی سرور هم هست تا برنامهٔ دست‌خورده دورش نزند.
 *
 *  ⛔ گرفتنِ اشتراک، پشتیبانی، کلیدِ بکاپ، و **دیدن و پس گرفتنِ** بکاپ‌هایی
 *  که از قبل روی سرورند هیچ‌وقت این‌جا بسته نمی‌شوند — دادهٔ خودِ مشتری است.
 */
const svc = require('../lib/pump-services');
const { forbidden } = require('./errors');

const MESSAGE = 'این کار در پلنِ شما نیست (خدماتِ سرور) — برای وی‌آی‌پی، یا تمدیدِ خدماتِ دائمی، با پشتیبانی تماس بگیرید.';

/**
 * @param {{keys?: string[]}} [opts] یکی از این کلیدها لازم است؛ نبودش ⇒ هر کلیدِ خدماتِ سرور.
 */
function requirePumpServices({ keys = null } = {}) {
  return async (req, res, next) => {
    try {
      const stationId = req.stationId || '';
      if (!stationId) return next();
      const ent = await require('../lib/entitlement').pump.entitlementOf(stationId);
      const ok = keys ? keys.some(k => ent.features.includes(k)) : svc.hasOnline(ent.features);
      if (!ok) return next(forbidden(MESSAGE, 'plan_no_services'));
      next();
    } catch (err) { next(err); }
  };
}

module.exports = { requirePumpServices, MESSAGE };
