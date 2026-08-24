'use strict';
/**
 * احراز هویت و Authorization.
 *
 * قاعده‌ی کلیدی: شناسه‌ی کاربر همیشه از توکن گرفته می‌شود، نه از پارامتر مسیر
 * یا بدنه‌ی درخواست. به همین دلیل کاربر نمی‌تواند با تغییر user_id به اطلاعات
 * دیگری برسد.
 */
const tokens = require('../lib/tokens');
const { getDb, now } = require('../db');
const subs = require('../lib/subscriptions');
const { unauthorized, forbidden } = require('./errors');
const { isCoreFeature } = require('../lib/features');

function bearer(req) {
  const h = req.headers.authorization || '';
  const m = /^Bearer\s+(.+)$/i.exec(h.trim());
  return m ? m[1].trim() : null;
}

/** کاربر عادی — توکن دسترسی لازم است. */
function requireUser(req, res, next) {
  const token = bearer(req);
  if (!token) return next(unauthorized());
  const row = tokens.verify(token, 'access');
  if (!row) return next(unauthorized('توکن نامعتبر یا منقضی است', 'invalid_token'));

  const user = getDb().prepare('SELECT * FROM users WHERE id = ?').get(row.subject_id);
  if (!user) return next(unauthorized('کاربر پیدا نشد', 'invalid_token'));
  if (user.status !== 'active') return next(forbidden('حساب کاربری غیرفعال است', 'account_disabled'));

  // اگر توکن به دستگاهی وابسته است، آن دستگاه باید هنوز فعال باشد
  if (row.device_id) {
    const dev = getDb().prepare('SELECT * FROM devices WHERE id = ?').get(row.device_id);
    if (!dev || dev.status !== 'active') {
      return next(forbidden('دسترسی این دستگاه لغو شده است', 'device_revoked'));
    }
    req.device = dev;
  }

  req.user = user;
  req.tokenRow = row;
  next();
}

/** مدیر سیستم. */
function requireAdmin(req, res, next) {
  const token = bearer(req);
  if (!token) return next(unauthorized());
  const row = tokens.verify(token, 'admin');
  if (!row) return next(unauthorized('توکن مدیر نامعتبر یا منقضی است', 'invalid_token'));

  const admin = getDb().prepare('SELECT * FROM admins WHERE id = ?').get(row.subject_id);
  if (!admin || admin.status !== 'active') {
    return next(forbidden('حساب مدیر غیرفعال است', 'admin_disabled'));
  }
  req.admin = admin;
  req.tokenRow = row;
  next();
}

function requireSuperAdmin(req, res, next) {
  if (!req.admin) return next(unauthorized());
  if (req.admin.role !== 'superadmin') return next(forbidden('این عملیات فقط برای مدیر ارشد مجاز است'));
  next();
}

/**
 * بررسی دوباره‌ی دسترسی در سمت سرور.
 * حتی اگر کلاینت قفل قابلیت را دور بزند، APIهای وابسته به آن قابلیت
 * اینجا رد می‌شوند — چون منبع تصمیم، دیتابیس سرور است نه License کلاینت.
 */
function requireFeature(featureKey) {
  return function (req, res, next) {
    if (!req.user) return next(unauthorized());
    if (isCoreFeature(featureKey)) return next();   // قابلیت پایه همیشه باز است

    const state = subs.evaluateUser(req.user.id, now());
    if (!state.active) {
      return next(forbidden('اشتراک شما فعال نیست', 'subscription_inactive'));
    }
    if (!state.features.includes(featureKey)) {
      return next(forbidden('این قابلیت در اشتراک شما فعال نیست', 'feature_not_allowed'));
    }
    req.subscriptionState = state;
    next();
  };
}

module.exports = { bearer, requireUser, requireAdmin, requireSuperAdmin, requireFeature };
