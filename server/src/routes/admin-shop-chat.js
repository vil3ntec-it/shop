'use strict';
/**
 * گزارش‌های سوءاستفادهٔ چتِ مشتری ↔ دکان — `/api/admin/shop-chat` (v3 §۳۳.۳).
 *
 * فقط مدیرِ ارشد؛ هر دیدن و هر بستنِ گزارش در سابقهٔ عملیات می‌نشیند (§۳۹.۸).
 * مدیر فقط رونوشتِ همان پیامی را می‌بیند که خودِ یکی از دو طرف گزارش کرده —
 * نه گفت‌وگو را.
 */
const express = require('express');
const chat = require('../lib/shop-chat');
const audit = require('../lib/audit');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');

const router = express.Router();
router.use(requireAdmin, requireSuperAdmin);
const wrap = (fn) => (req, res, next) => Promise.resolve(fn(req, res, next)).catch(next);

router.get('/reports', wrap(async (req, res) => {
  const reports = await chat.reportsForAdmin({ status: String(req.query.status || 'open'), limit: req.query.limit });
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.shop_chat_reports_viewed', detail: { count: reports.length } });
  res.set('Cache-Control', 'no-store');
  res.json({ reports });
}));

router.put('/reports/:id', wrap(async (req, res) => {
  const out = await chat.reviewReport(req.params.id);
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.shop_chat_report_reviewed', targetType: 'chat_report', targetId: out.id });
  res.json(out);
}));

module.exports = router;
