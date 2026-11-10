'use strict';
/**
 * PIN ِ شخصیِ صندوق و نوبت‌های سنجیدهٔ صندوق — `/api/shop/till-pin` (STF-04، S46؛ منطق در `lib/till-pin.js`).
 *
 *   GET    /config                 همیشه باز: { enabled, memberId, role, pinSet?, locked?, lockedUntil? }
 *   GET    /members                اعضای فعال با حالِ PIN (صاحب و مدیر: members.view)
 *   PUT    /me                ✱    {pin} PIN ِ خودم (بازکردنِ قفلِ خودم هم همین است)
 *   PUT    /members/:id       ✱    {pin} PIN ِ عضوِ دیگر (صاحب: همه؛ مدیر: نقش‌های پایین‌تر)
 *   DELETE /members/:id       ✱    برداشتنِ PIN و بستنِ نوبتِ بازش
 *   POST   /members/:id/unlock ✱   بازکردنِ قفلِ PIN ِ عضوِ دیگر
 *   POST   /verify                 {memberId|userId, pin} ⇒ نوبتِ تازه روی دستگاهِ همین توکن (یا null = خودِ حسابِ دستگاه)
 *   GET    /current                نوبتِ بازِ دستگاهِ همین توکن
 *   POST   /current/end            {reason:'lock'} بستنِ همان نوبت (قفلِ صندوق)
 *   GET    /sessions               نوبت‌های یک بازه (صاحب و مدیر همه؛ بقیه فقط خودشان)
 *
 * ✱ تأییدِ دوبارهٔ ورود (`lib/reauth.js`): شاگرد روی صندوقِ بازِ صاحب نه PIN ِ کسی را عوض می‌کند، نه قفلِ
 *   PIN ِ صاحب را باز می‌کند تا حدس را از سر بگیرد. صاحب با همان تأیید همیشه PIN ِ خودش را از نو می‌گذارد.
 * ⛔ سقف: سطلِ IP پیش از احراز، سطلِ هر کاربر پس از آن، و سطلِ تنگ‌ترِ `/verify`؛ قفلِ PIN جدا در خودِ سرور است.
 * ⛔ shop_id و عضو فقط از عضویتِ توکن؛ دستگاه فقط از ردیفِ توکن (`req.device`). PIN هرگز در پاسخ یا سابقه نیست.
 * ⛔ پیش‌فرض خاموش: بی `shop.till_pin` همه جز `/config` ⇒ ۴۰۳ `till_pin_disabled`.
 * ⛔ SUB-10: FREE — هویتِ کاربرِ صندوق بخشی از مجوزهای هستهٔ دکان است و پس از پایانِ اشتراک هم باید کار کند.
 */
const express = require('express');
const config = require('../config');
const v = require('../lib/validate');
const tp = require('../lib/till-pin');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');
const { forbidden } = require('../middleware/errors');

const router = express.Router();
router.use(rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'till-pin-ip' }));
const limit = rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'till-pin', key: (req) => req.user && req.user.id });
router.use(requireUser, requireShop, limit, requirePermission('data.read'));
//  حدسِ PIN: سطلِ جدای هر حسابِ دستگاه، جدا از قفلِ هر PIN در پایگاه
const verifyLimit = rateLimit({
  max: Number(process.env.RATE_TILL_PIN_VERIFY_MAX) || 20, windowMs: 60 * 1000, keyPrefix: 'till-pin-verify',
  key: (req) => `${req.shopId}:${req.user.id}`,
});

const actorOf = (req) => ({ id: req.member.id, role: req.role, userId: req.user.id });
const opts = (req) => ({ ip: clientIp(req) });
const memberParam = (req) => ({ memberId: v.id(req.params.id, { field: 'شناسه عضو' }) });

async function needFlag(req, res, next) {
  if (!(await tp.flagOf(req.shopId))) return next(forbidden('PIN ِ صندوق در سرور برای این دکان روشن نیست', 'till_pin_disabled'));
  next();
}

router.get('/config', async (req, res) => {
  res.json(await tp.config(req.shopId, actorOf(req)));
});

router.use(needFlag);

router.get('/members', requirePermission('members.view'), async (req, res) => {
  res.json(await tp.members(req.shopId));
});

router.put('/me', requireRecentAuth, async (req, res) => {
  res.json(await tp.setPin(req.shopId, actorOf(req), { memberId: req.member.id }, req.body?.pin, opts(req)));
});

router.put('/members/:id', requireRecentAuth, requirePermission('members.view'), async (req, res) => {
  res.json(await tp.setPin(req.shopId, actorOf(req), memberParam(req), req.body?.pin, opts(req)));
});

router.delete('/members/:id', requireRecentAuth, requirePermission('members.view'), async (req, res) => {
  res.json(await tp.removePin(req.shopId, actorOf(req), memberParam(req), opts(req)));
});

router.post('/members/:id/unlock', requireRecentAuth, requirePermission('members.view'), async (req, res) => {
  res.json(await tp.unlock(req.shopId, actorOf(req), memberParam(req), opts(req)));
});

router.post('/verify', verifyLimit, async (req, res) => {
  const b = req.body || {};
  const target = b.memberId ? { memberId: v.id(b.memberId, { field: 'شناسه عضو' }) } : { userId: v.id(b.userId, { field: 'شناسه کاربر' }) };
  res.json(await tp.verify(req.shopId, actorOf(req), req.device, target, b.pin, opts(req)));
});

router.get('/current', async (req, res) => {
  res.json(await tp.current(req.shopId, req.device));
});

router.post('/current/end', async (req, res) => {
  res.json(await tp.endCurrent(req.shopId, actorOf(req), req.device, req.body?.reason || 'lock', opts(req)));
});

router.get('/sessions', async (req, res) => {
  res.json(await tp.sessions(req.shopId, actorOf(req), { from: req.query.from, to: req.query.to, memberId: req.query.member }));
});

module.exports = router;
