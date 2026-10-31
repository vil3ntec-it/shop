'use strict';
/**
 * جست‌وجوی سراسریِ بخشِ «فروشگاه» ِ پنلِ مرکزی (پوستهٔ کنسول؛ توانایی `search.v1`).
 *
 *   GET /api/admin/search?q=&limit=     ⇒ { q, kinds, results: { shops, users, payments, orders, errors, admins? } }
 *
 * ⛔ فقط خواندن، و هر نوع فقط برای نقشی که همان داده را از درِ خودش هم می‌خواند (`lib/admin-roles.js`):
 *      superadmin/admin  ⇒ همه (مدیرها فقط برای ارشدِ **انسانی** — کلیدِ سرویس به ‎/admin/admins‎ راه ندارد)
 *      viewer            ⇒ همه جز مدیرها (هر GET برایش باز است، جز درهای «فقط ارشد»)
 *      finance           ⇒ دکان و پرداخت (همان فهرستِ FINANCE)
 * ⛔ سفارش فقط با شناسه/آغازِ شناسه یا شمارهٔ سفارش — نه نام و تلفنِ مشتری (دادهٔ شخصیِ خریدار این‌جا جست‌وجو نمی‌شود).
 * ⛔ هر نوع سقفِ خودش را دارد (۱ تا ۱۰، پیش‌فرض ۵) و `%`/`_` ِ ورودی بی‌اثر می‌شوند؛ کوتاه‌تر از ۲ نویسه ⇒ ۴۰۰.
 */
const express = require('express');
const { many } = require('../db');
const v = require('../lib/validate');
const { badRequest } = require('../middleware/errors');

const router = express.Router();

const KINDS_BY_ROLE = Object.freeze({
  superadmin: ['shops', 'users', 'payments', 'orders', 'errors', 'admins'],
  admin: ['shops', 'users', 'payments', 'orders', 'errors'],
  viewer: ['shops', 'users', 'payments', 'orders', 'errors'],
  finance: ['shops', 'payments'],
});

/** `%` و `_` و `\` ِ نوشتهٔ کاربر حرفِ معمولی‌اند، نه الگو. */
const likeOf = (q) => `%${q.toLowerCase().replace(/[\\%_]/g, (c) => `\\${c}`)}%`;

const SEARCHES = {
  shops: (q, like, n) => many(
    `SELECT s.id, s.name, s.status, u.email AS owner_email, s.created_at
       FROM shops s LEFT JOIN users u ON u.id = s.owner_user_id
      WHERE s.id = $1 OR lower(s.name) LIKE $2 ESCAPE '\\' OR lower(coalesce(u.email,'')) LIKE $2 ESCAPE '\\'
      ORDER BY s.created_at DESC LIMIT $3`, [q, like, n]),
  users: (q, like, n) => many(
    `SELECT id, name, email, status, created_at FROM users
      WHERE id = $1 OR lower(name) LIKE $2 ESCAPE '\\' OR lower(coalesce(email,'')) LIKE $2 ESCAPE '\\'
         OR coalesce(phone,'') LIKE $2 ESCAPE '\\'
      ORDER BY created_at DESC LIMIT $3`, [q, like, n]),
  payments: (q, like, n) => many(
    `SELECT id, app, tenant_id, amount, currency, method, receipt_no, paid_at FROM sub_payments
      WHERE deleted = false AND (id = $1 OR tenant_id = $1 OR lower(receipt_no) LIKE $2 ESCAPE '\\')
      ORDER BY paid_at DESC LIMIT $3`, [q, like, n]),
  orders: (q, _like, n) => many(
    `SELECT id, shop_id, order_no, status, payment_status, created_at FROM online_orders
      WHERE id = $1 OR id LIKE $2 ESCAPE '\\' OR order_no::text = $1
      ORDER BY created_at DESC LIMIT $3`, [q, `${q.replace(/[\\%_]/g, (c) => `\\${c}`)}%`, n]),
  errors: (q, like, n) => many(
    `SELECT id, app, app_version, account_id, left(message, 200) AS message, at FROM client_errors
      WHERE id = $1 OR lower(message) LIKE $2 ESCAPE '\\'
      ORDER BY at DESC LIMIT $3`, [q, like, n]),
  admins: (q, like, n) => many(
    `SELECT id, username, name, role, status, is_owner FROM admins
      WHERE id = $1 OR lower(username) LIKE $2 ESCAPE '\\' OR lower(coalesce(name,'')) LIKE $2 ESCAPE '\\'
      ORDER BY created_at LIMIT $3`, [q, like, n]),
};

router.get('/', async (req, res, next) => {
  const q = v.text(req.query?.q, { max: 80 }).trim();
  if (q.length < 2) return next(badRequest('دستِ‌کم دو نویسه بنویسید', 'query_too_short'));
  const limit = v.integer(req.query?.limit, { min: 1, max: 10, def: 5 });
  let kinds = KINDS_BY_ROLE[req.admin?.role] || [];
  if (req.service) kinds = kinds.filter((k) => k !== 'admins');
  const like = likeOf(q);
  const results = {};
  for (const k of kinds) results[k] = await SEARCHES[k](q, like, limit);
  res.json({ q, kinds, limit, results, serverTime: Date.now() });
});

module.exports = router;
module.exports.KINDS_BY_ROLE = KINDS_BY_ROLE;
