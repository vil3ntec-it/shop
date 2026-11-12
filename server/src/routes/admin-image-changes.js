'use strict';
/**
 * IMG-PRO-17 در پنلِ مرکزی (توانایی ‎image-changes.v1‎ ِ پل؛ `console-image-changes.js` ِ ریپوی server) — دسته‌های «تغییرِ
 * دسته‌ایِ عکس» ِ یک دکان و برگرداندنشان از سوی پشتیبانی. منطق: همان `lib/image-changes.js` ِ درِ دکان، بی کپیِ دوم.
 *
 *   GET  /api/admin/shops/:id/images/changes[?limit=1..50]   ⇒ { shopId, enabled, changes }            مدیر/ارشد/بیننده
 *   GET  /api/admin/shops/:id/images/changes/:cid            ⇒ { shopId, change (+ items: پیش و پسِ هر کالا) }
 *   POST /api/admin/shops/:id/images/changes/:cid/rollback   {reason} ⇒ { change, already }                 فقط ارشد
 *
 * ⛔ مالی ۴۰۳ (`lib/admin-roles.js`، پیش‌فرض‌بسته)؛ بیننده فقط می‌خواند؛ دکانِ ناموجود ‎404 shop_not_found‎ و دستهٔ دکانِ دیگر
 *    ‎404 change_not_found‎ (هر پرسش با `shop_id` ِ همین نشانی).
 * ⛔ بانکِ خاموش (`shop.image_bank` ِ همین دکان): فهرست `enabled: false` و خالی؛ جزئیات و برگرداندن ‎403 image_bank_disabled‎.
 * ⛔ جزئیات (پیش و پسِ عکسِ هر کالا) وقتی پرچمِ `security / admin.support_grant` روشن است اجازهٔ زندهٔ `read_private` ِ همین
 *    درخواست‌کننده روی همین دکان می‌خواهد (همان الگوی `grantForExport`؛ پیش‌فرض، صاحب‌کار می‌تواند عوض کند).
 * ⛔ برگرداندن دادهٔ دکان را می‌نویسد ⇒ **همیشه** اجازهٔ زندهٔ `restore` (به نامِ یک آدم؛ کارِ خودکارِ پنل ‎403 human_actor_required‎)،
 *    دلیلِ ۳ تا ۲۰۰، `Idempotency-Key` با `lib/admin-write-once.js` (اجازه درونِ همان «یک بار»: بازپخش اجازهٔ دوم نمی‌خورد)،
 *    و سابقه‌های `image.restored` / `image.changes_rolled_back` با نوعِ «مدیر» و شناسهٔ اجازه در **همان** تراکنشِ برگرداندن.
 *    ‎409 changed_since‎ ⇒ هیچ چیز برنمی‌گردد (همان قاعدهٔ درِ دکان). اعمالِ دسته از این‌جا نیست — کارِ برنامهٔ خودِ دکان است.
 * ⛔ سوار روی `/shops/:id` ِ `routes/admin.js` (کنارِ `admin-shop-state` و `admin-access-grants`)؛ سقفِ IP پیش از احراز همان‌جا
 *    (`admin-image-changes-ip`)؛ سقفِ نوشتنِ هر مدیر/انجام‌دهنده این‌جا.
 */
const express = require('express');
const access = require('../lib/support-access');
const bank = require('../lib/image-bank');
const changes = require('../lib/image-changes');
const writeOnce = require('../lib/admin-write-once');
const v = require('../lib/validate');
const { requireReason } = require('../lib/admin-access');
const { ApiError, badRequest, forbidden, notFound } = require('../middleware/errors');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireSuperAdmin } = require('../middleware/auth');

const router = express.Router({ mergeParams: true });

const writeLimit = rateLimit({
  max: 30, windowMs: 10 * 60 * 1000, keyPrefix: 'admin-image-changes-write',
  key: (req) => `${req.admin?.id || 'x'}:${req.actor?.id || ''}`,
});

const cidOf = (req) => v.id(req.params.cid, { field: 'شناسهٔ دسته', max: 40 });

async function enabledShop(req) {
  const shop = await access.shopOf(req.params.id);
  return { shop, enabled: !!(await bank.shopEnabled(shop.id)) };
}
const disabled = () => forbidden('بانکِ تصاویر برای این دکان روشن نیست', 'image_bank_disabled');

/** «پیدا نشد» ِ کتابخانه (دسته‌ای از دکانِ دیگر هم همین) ⇒ کدِ روشنِ همین در */
const missing = (e) => {
  if (e instanceof ApiError && e.status === 404) throw notFound('این دسته در این دکان پیدا نشد', 'change_not_found');
  throw e;
};

router.get('/images/changes', async (req, res) => {
  const raw = req.query?.limit;
  let limit = 20;
  if (raw !== undefined) {
    if (typeof raw !== 'string' || !/^\d{1,3}$/.test(raw) || +raw < 1 || +raw > 50) {
      throw badRequest('limit باید عددِ ۱ تا ۵۰ باشد', 'bad_limit');
    }
    limit = +raw;
  }
  const { shop, enabled } = await enabledShop(req);
  res.set('Cache-Control', 'no-store');
  if (!enabled) return res.json({ shopId: shop.id, enabled: false, changes: [] });
  const out = await changes.list(shop.id, { limit });
  res.json({ shopId: shop.id, enabled: true, changes: out.changes });
});

router.get('/images/changes/:cid', async (req, res) => {
  const cid = cidOf(req);
  const { shop, enabled } = await enabledShop(req);
  if (!enabled) throw disabled();
  if (await access.enforced()) {
    access.requester(req);
    await access.useGrant(req, shop.id, 'read_private', { what: `image_changes ${cid}` });
  }
  const out = await changes.detail(shop.id, cid).catch(missing);
  res.set('Cache-Control', 'no-store');
  res.json({ shopId: shop.id, change: out.change });
});

router.post('/images/changes/:cid/rollback', requireSuperAdmin, writeLimit, async (req, res) => {
  const cid = cidOf(req);
  const { shop, enabled } = await enabledShop(req);
  access.requester(req);
  const reason = requireReason(req);
  if (!enabled) throw disabled();
  const out = await writeOnce.writeOnce(req, { tenantId: shop.id, resultId: cid }, async () => {
    const grant = await access.useGrant(req, shop.id, 'restore', { what: `image_changes ${cid} rollback` });
    return changes.rollback(shop.id, String(req.admin.id), cid, { reason },
      { ip: clientIp(req), actorType: 'admin', grant }).catch(missing);
  });
  writeOnce.send(res, out);
});

module.exports = router;
