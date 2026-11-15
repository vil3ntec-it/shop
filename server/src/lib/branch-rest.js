'use strict';
/**
 * فازِ ۳ب — شعبهٔ درهای REST ِ شعبه‌شناس (`docs/BRANCHES-fa.md` §۹؛ پرچمِ خاموشِ `shop.branches`): نگه‌داشته، سفارشِ خرید،
 * نوبت‌های صندوق، صندوقِ تعارض و خبرها. همان قاعدهٔ `/shop/catalog`:
 *   ⛔ شعبه فقط از سرآیندِ سنجیدهٔ `X-Branch-Id` با دسترسیِ همین عضو (`branches.restOf`) — هرگز از بدنه یا پارامتر؛
 *      کارمند فقط شعبه‌های خودش، صاحب هر شعبه؛ نامجاز ⇒ ۴۰۳ `branch_forbidden`، بی سرآیند و بی اصلی ⇒ ۴۰۳ `branch_required`.
 *   ⛔ پرچمِ خاموش ⇒ سرآیند خوانده نمی‌شود: `req.branchId = ''` و `req.branchKey = req.shopId` (بایت‌به‌بایت همان امروز).
 * `req.branchKey` همان کلیدی است که کتابخانه‌ها جای شناسهٔ دکان می‌گیرند (`branch-key.sqlOwn`/`partitionOf`).
 */
const branches = require('./branches');
const { forbidden } = require('../middleware/errors');

/** پس از `requireUser`/`requireShop` */
async function scope(req, res, next) {
  req.branchId = await branches.restOf(req);
  req.branchKey = branches.partitionOf(req.shopId, req.branchId);
  next();
}

/** درِ سطحِ دکان یا فقط شعبهٔ اصلی در یک روترِ شعبه‌شناس ⇒ شعبهٔ دیگر ۴۰۳ `branch_unsupported` (صاحب هم) */
const mainOnly = (req, res, next) => (req.branchId
  ? next(forbidden('این کار فقط در شعبهٔ اصلی است', 'branch_unsupported')) : next());

/** سابقهٔ کارِ شعبهٔ غیرِ اصلی شعبه‌اش را دارد (اصلی ⇒ همان سابقهٔ امروز، بایت‌به‌بایت) */
const detailOf = (req, detail = {}) => (req.branchId ? { ...detail, branch: req.branchId } : detail);

module.exports = { scope, mainOnly, detailOf };
