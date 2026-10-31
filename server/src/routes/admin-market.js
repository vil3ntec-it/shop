'use strict';
/**
 * پنلِ مرکزی ۴.۶ — مدیریتِ بازار (فقط سمتِ مدیریت) — `/api/admin/market/*`؛ فقط خواندن.
 *
 *   GET /overview     دروازه‌ها، فروشنده‌ها، جریمه و پرونده‌های باز، سفارش‌های ویترین در بازه، سبدِ چنددکانی و کمیسیون «در مدل؟»
 *   GET /sellers      ?q=&state=listed|unlisted|held|sanctioned|inactive&limit=&offset=  — هر دکان با دروازه‌هایش
 *   GET /orders       ?days=|from=&to=&shopId=&group=&problem=1&limit=&offset=          — بی دادهٔ شخصیِ مشتری
 *   GET /commission   ?days=|from=&to=   — بی ستونِ کارمزد (PRِ #432) ⇒ inModel:false و دلیل، نه صفرِ ساختگی
 *   GET /history      ?limit=&before=&shopId=  — کارهای حساسِ بازار در سابقه (تصمیم، جریمه، حالت، پرچم، فهرست)
 *
 * زیرِ `requireAdmin` ِ `routes/admin.js` و مرزِ نقشِ `lib/admin-roles.js` (viewer فقط خواندن؛ finance فقط درهای
 * پولیِ همین‌جا). نوشتن (تعلیقِ فروشنده، تصمیمِ پرونده، برداشتنِ جریمه) **درِ تازه ندارد**: همان
 * `/api/admin/shops/:id/holds` (سرویسِ `market.directory`/`shop.storefront`) و `/api/admin/trust/*` (فقط ارشد).
 */
const express = require('express');
const m = require('../lib/admin-market');

const router = express.Router();

router.use((req, res, next) => { res.set('Cache-Control', 'no-store'); next(); });

router.get('/overview', async (req, res) => res.json(await m.overview(req.query || {})));
router.get('/sellers', async (req, res) => res.json(await m.sellers(req.query || {})));
router.get('/orders', async (req, res) => res.json(await m.orderList(req.query || {})));
router.get('/commission', async (req, res) => res.json(await m.commission(req.query || {})));
router.get('/history', async (req, res) => res.json(await m.history(req.query || {})));

module.exports = router;
