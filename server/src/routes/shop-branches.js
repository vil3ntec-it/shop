'use strict';
/**
 * شعبه درونِ یک دکان — `/api/shop/branches` (فازِ ۱ ِ سرور؛ `lib/branches.js`، طراحی `docs/BRANCHES-fa.md`).
 *
 *   GET   /                 شعبه‌های این عضو: صاحب ⇐ همه (`all: true`)؛ دیگران ⇐ فقط شعبه‌های خودش (نامِ بقیه نه)
 *   POST  /                 { name } ⇐ شعبهٔ تازه — فقط صاحب
 *   PATCH /:id              { name?, status?: active|archived } — فقط صاحب؛ `main` (یا '') = شعبهٔ اصلی (فقط نام)
 *   POST  /:id              همان PATCH (فازِ ۲: برنامه‌ها PATCH نمی‌فرستند)
 *   GET   /members          اعضا و شعبه‌هایشان — فقط صاحب
 *   PUT   /members/:id      { branches: [...] } — فقط صاحب؛ [''] = پیش‌فرض (فقط اصلی)
 *   GET   /report           ?from&to&branch ⇐ خلاصهٔ هر شعبه؛ `totals` فقط برای صاحب (`lib/branch-report.js`)
 *
 * ⛔ پرچمِ `shop.branches` خاموش ⇒ **همه** همان پاسخِ مسیرِ ناشناخته (درِ تازه بی صدازننده تا فازِ ۲؛ STATUS/RPT-18).
 * ⛔ دکان فقط از عضویتِ کاربرِ توکن (`requireShop`)؛ شناسهٔ شعبه/عضو همیشه درونِ همان دکان جست‌وجو می‌شود ⇒
 *    شناسهٔ دکانِ دیگر همان «نیست».
 * ⛔ سقفِ نرخ برای هر IP پیش از احراز و پس از آن برای هر کاربر؛ نوشتن‌ها با احرازِ تازه (`requireRecentAuth`) و سابقه.
 */
const express = require('express');
const audit = require('../lib/audit');
const branches = require('../lib/branches');
const report = require('../lib/branch-report');
const { requireUser, requireShop, requirePermission } = require('../middleware/auth');
const { forbidden, notFoundHandler } = require('../middleware/errors');
const { clientIp, rateLimit } = require('../middleware/ratelimit');
const { requireRecentAuth } = require('../lib/reauth');

const router = express.Router();
router.use(rateLimit({ max: 120, windowMs: 60 * 1000, keyPrefix: 'branches-ip' }));
router.use(requireUser, requireShop);
//  پرچم پیش از هر کارِ دیگر (و پیش از خواندنِ بدنه): خاموش ⇒ همان مسیرِ ناشناخته
router.use(async (req, res, next) => {
  try {
    if (!(await branches.enabled(req.shopId))) return notFoundHandler(req, res);
    next();
  } catch (err) { next(err); }
});
router.use(rateLimit({ max: 60, windowMs: 60 * 1000, keyPrefix: 'branches', key: (req) => req.user && req.user.id }));

const isOwner = (req) => req.role === 'owner';
const needOwner = (req, res, next) => (isOwner(req) ? next()
  : next(forbidden('شعبه‌ها را فقط صاحبِ دکان می‌سازد و می‌دهد', 'owner_only')));
const log = (req, action, targetId, detail) => audit.log({
  shopId: req.shopId, userId: req.user.id, action, targetType: 'branch', targetId, detail, ip: clientIp(req),
});

router.get('/', async (req, res) => {
  const access = await branches.accessOf(req.shopId, req.member);
  const all = await branches.list(req.shopId);
  res.set('Cache-Control', 'private, no-store');
  res.json({
    enabled: true, all: access.all, mine: access.ids,
    branches: access.all ? all : all.filter((b) => access.ids.includes(b.id)),
  });
});

router.post('/', needOwner, requireRecentAuth, async (req, res) => {
  const branch = await branches.create(req.shopId, req.body?.name, req.user.id);
  await log(req, 'branch.created', branch.id, { name: branch.name });
  res.status(201).json({ branch, branches: await branches.list(req.shopId) });
});

async function updateBranch(req, res) {
  const branch = await branches.update(req.shopId, req.params.id, req.body, req.user.id);
  await log(req, 'branch.updated', branch.id || 'main', { name: branch.name, status: branch.status });
  res.json({ branch, branches: await branches.list(req.shopId) });
}
router.patch('/:id', needOwner, requireRecentAuth, updateBranch);
//  فازِ ۲: همان با POST — `HttpURLConnection` ِ برنامهٔ اندروید/کامپیوتر PATCH نمی‌فرستد
router.post('/:id', needOwner, requireRecentAuth, updateBranch);

router.get('/members', needOwner, async (req, res) => {
  res.set('Cache-Control', 'private, no-store');
  res.json({ branches: await branches.list(req.shopId), members: await branches.members(req.shopId) });
});

router.put('/members/:id', needOwner, requireRecentAuth, async (req, res) => {
  const { member, branches: ids } = await branches.setMember(req.shopId, req.params.id, req.body?.branches, req.user.id);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'branch.member_set',
    targetType: 'member', targetId: member.user_id, detail: { branches: ids }, ip: clientIp(req),
  });
  res.json({ userId: member.user_id, branches: ids, members: await branches.members(req.shopId) });
});

//  صاحب همیشه؛ دیگران با `reports.view` (نقش + لایه‌ها) و فقط شعبه‌های خودشان
const canReport = (req, res, next) => (isOwner(req) ? next() : requirePermission('reports.view')(req, res, next));

router.get('/report', canReport, require('../lib/read-replica').reads, async (req, res) => {
  const access = await branches.accessOf(req.shopId, req.member);
  let only = null;
  if (req.query && Object.prototype.hasOwnProperty.call(req.query, 'branch')) {
    const raw = req.query.branch;
    const id = typeof raw === 'string' ? branches.idOf(raw) : null;
    const visible = access.all ? (await branches.list(req.shopId)).map((b) => b.id) : access.ids;
    if (id === null || !visible.includes(id)) throw forbidden('این شعبه در دسترسیِ شما نیست', 'branch_forbidden');
    only = id;
  }
  res.set('Cache-Control', 'private, no-store');
  res.json(await report.reportFor(req.shopId, access, req.query || {}, only));
});

module.exports = router;
