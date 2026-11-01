'use strict';
/**
 * مالیِ مرکزی — ماژولِ ۴.۵ ِ پنلِ مرکزیِ مالک (`lib/central-finance.js`، مهاجرتِ ۱۷۱).
 *
 *   GET  /api/admin/finance/summary         درآمدِ بازه: جمعِ هر ارز، روز/هفته/ماه/دلخواه، به تفکیکِ بخش و روش
 *   GET  /api/admin/finance/breakdown       ?by=shop|plan — صفحه‌بندیِ سمتِ سرور
 *   GET  /api/admin/finance/payments        دفترِ پرداخت‌ها (پالایش + نشانگر)
 *   GET  /api/admin/finance/payments/:id    یک پرداخت + سندها + بازنگری‌ها + سابقه
 *   GET  /api/admin/finance/adjustments     سندهای اصلاحی (بازپرداخت، اصلاح، ابطال، شمارهٔ تراکنش)
 *   POST /api/admin/finance/adjustments     سندِ اصلاحیِ تازه — مالی/ارشد + دلیل + احرازِ دوباره + Idempotency-Key
 *   GET  /api/admin/finance/receivables     بدهی‌ها
 *   GET  /api/admin/finance/pending         درخواست‌های خرید و حالِ درگاه
 *   GET  /api/admin/finance/gateway         تطبیقِ پرداختِ درگاه ⇄ شمارهٔ تراکنش (یا BLOCKED-EXTERNAL)
 *   GET  /api/admin/finance/discrepancies   مغایرت
 *   GET  /api/admin/finance/history         تاریخچهٔ کارهای مالی (audit_logs)
 *   GET  /api/admin/finance/export          CSV ِ صفحه‌به‌صفحه — فقط مالی/ارشد، با ردیفِ سابقه
 *
 * سوار در `app.js` (`/api/admin/finance`)، زیرِ `requireAdmin` ِ خودش و `routes/admin.js`؛ مرزِ نقش: `lib/admin-roles.js` (finance: همهٔ این درها؛ viewer: فقط
 * خواندن). نوشتن و خروجی در خودِ کتابخانه به مالی/ارشد بسته است (`requireFinanceWriter`) — مدیرِ عادی نه.
 * ⛔ هیچ درِ ویرایش یا حذفی این‌جا نیست: اصلاح فقط سندِ تازه است.
 */
const express = require('express');
const v = require('../lib/validate');
const fin = require('../lib/central-finance');
const reports = require('../lib/finance-reports');
const { clientIp } = require('../middleware/ratelimit');
const { requireAdmin } = require('../middleware/auth');

const router = express.Router();
//  `routes/admin.js` پیش از این می‌دود (همان `requireAdmin` و مرزِ نقش)؛ این‌جا دوباره تا روتر به تنهایی هم بسته باشد
router.use(requireAdmin);

/** مرزِ نوشتن/خروجی — مالی یا ارشد (نامش را `bridge-roles.mjs` ِ پنل هم می‌شناسد). */
function requireFinanceWriter(req, res, next) {
  try { fin.requireWriter(req); next(); } catch (err) { next(err); }
}

const noStore = (res) => res.set('Cache-Control', 'no-store');

router.get('/summary', async (req, res) => {
  noStore(res);
  res.json(await fin.summary(req.query || {}));
});

router.get('/breakdown', async (req, res) => {
  noStore(res);
  res.json(await fin.breakdown(req.query || {}));
});

router.get('/payments', async (req, res) => {
  noStore(res);
  res.json(await fin.payments(req.query || {}));
});

router.get('/payments/:id', async (req, res) => {
  noStore(res);
  res.json(await fin.paymentDetail(v.id(req.params.id, { field: 'شناسهٔ پرداخت' })));
});

router.get('/adjustments', async (req, res) => {
  noStore(res);
  res.json(await fin.adjustments(req.query || {}));
});

router.post('/adjustments', requireFinanceWriter, async (req, res) => {
  const out = await fin.createAdjustment(req, { ip: clientIp(req) });
  if (out.replayed) res.set('Idempotent-Replayed', 'true');
  res.status(out.replayed ? 200 : 201).json(out);
});

router.get('/receivables', async (req, res) => {
  noStore(res);
  res.json(await fin.receivables(req.query || {}));
});

router.get('/pending', async (req, res) => {
  noStore(res);
  res.json(await reports.pending(req.query || {}));
});

router.get('/gateway', async (req, res) => {
  noStore(res);
  res.json(await reports.gateway(req.query || {}));
});

router.get('/discrepancies', async (req, res) => {
  noStore(res);
  res.json(await reports.discrepancies());
});

router.get('/history', async (req, res) => {
  noStore(res);
  res.json(await reports.history(req.query || {}));
});

router.get('/export', requireFinanceWriter, async (req, res) => {
  noStore(res);
  res.json(await reports.exportCsv(req, req.query || {}));
});

module.exports = router;
