'use strict';
/**
 * شکایت و گزارشِ مشتری — `/api/store/:slug/trust/…` (v3 §۳۸.۶؛ ABUSE-01).
 *
 * ⛔ پیش‌فرض خاموش: بی پرچمِ `market.trust` ِ همان دکان ⇒ ۴۰۴ (همان «ویترین
 * نیست») و هیچ ردیفی ساخته نمی‌شود. ویترین لازم نیست «باز» باشد: از دکانِ
 * بسته یا معلق هم می‌شود شکایت کرد و پیگیری کرد.
 * ⛔ ثبت فقط با شمارهٔ تأییدشده (بلیتِ ویترین، `X-Store-Ticket`)؛ پیگیری با
 * توکنِ حدس‌نزدنیِ همان پرونده، بی بلیت.
 * ⛔ سقف: خواندن به‌ازای IP؛ ثبت به‌ازای IP و به‌ازای شماره (حافظه‌ای) + سقفِ
 * روزانهٔ ماندگار در دیتابیس (`config.trust.dailyPerPhone/dailyPerIp`).
 */
const express = require('express');
const config = require('../config');
const trust = require('../lib/market-trust');
const { rateLimit, clientIp } = require('../middleware/ratelimit');
const { ApiError } = require('../middleware/errors');

const router = express.Router();

const readLimit = rateLimit({ max: config.rateLimit.generalMax, keyPrefix: 'store-trust-read' });
const fileIpLimit = rateLimit({ max: config.trust.fileIpMax, keyPrefix: 'store-trust-file-ip' });
const filePhoneLimit = rateLimit({
  max: config.trust.fileMax, keyPrefix: 'store-trust-file', key: (req) => (req.trustViewer ? req.trustViewer.phone : ''),
});
const uploadLimit = rateLimit({ max: config.trust.uploadMax, keyPrefix: 'store-trust-upload' });

const store = async (req, res, next) => {
  res.set('Cache-Control', 'no-store');
  req.trustStore = await trust.storeOf(req.params.slug);
  next();
};

/**
 * شمارهٔ تأییدشده از بلیتِ ویترین. ⛔ #301 بلیت را با «نسلِ» شماره لغوشدنی
 * می‌کند و `phoneOfTicket` ِ هم‌گام را با `verifiedPhoneOfTicket` ِ ناهم‌گام عوض
 * می‌کند؛ این لایه با هر دو ترتیبِ ادغام درست است (پس از #301 فقط شاخهٔ اول).
 */
async function ticketPhone(ticket) {
  const sf = require('../lib/storefront');
  return typeof sf.verifiedPhoneOfTicket === 'function' ? sf.verifiedPhoneOfTicket(ticket) : sf.phoneOfTicket(ticket);
}

const verified = async (req, res, next) => {
  const phone = await ticketPhone(req.get('x-store-ticket') || '');
  if (!phone) throw new ApiError(401, 'verification_required', 'برای ثبتِ شکایت، شمارهٔ خود را تأیید کنید');
  req.trustViewer = { phone, ip: clientIp(req) };
  next();
};

const base = '/:slug/trust';

router.get(`${base}/config`, readLimit, store, (req, res) => {
  res.json({
    enabled: true,
    categories: { complaint: Object.keys(trust.COMPLAINT), report: Object.keys(trust.REPORT) },
    limits: { bodyMax: trust.MAX_BODY, imageMax: config.trust.imageMax, evidenceMax: config.trust.evidenceMax },
  });
});

const filer = (kind) => async (req, res) => {
  const out = await trust.file(req.trustStore, req.trustViewer, req.body, kind);
  res.status(out.duplicate ? 200 : 201).json(out);
};

router.post(`${base}/complaints`, readLimit, store, verified, fileIpLimit, filePhoneLimit, filer('complaint'));
router.post(`${base}/reports`, readLimit, store, verified, fileIpLimit, filePhoneLimit, filer('report'));

router.get(`${base}/cases`, readLimit, store, verified, async (req, res) => {
  res.json({ cases: await trust.myCases(req.trustStore, req.trustViewer) });
});

router.get(`${base}/cases/:token`, readLimit, store, async (req, res) => {
  res.json({ case: await trust.track(req.trustStore, req.params.token) });
});

router.post(`${base}/cases/:token/evidence`, readLimit, store, uploadLimit, trust.rawEvidence, async (req, res) => {
  const out = await trust.putEvidence(req.trustStore, req.params.token, req.body);
  res.status(out.reused ? 200 : 201).json(out);
});

router.get(`${base}/cases/:token/evidence/:evidenceId`, readLimit, store, async (req, res) => {
  require('../lib/shop-chat').sendMedia(res, await trust.customerEvidence(req.trustStore, req.params.token, req.params.evidenceId));
});

module.exports = router;
