'use strict';
/**
 * تنظیم‌های شعبه‌ایِ دکان (مهاجرتِ ۹۴۶، `shop_branch_settings`؛ `docs/BRANCHES-fa.md` §۱۰) — یک ردیف برای هر دکان، فقط صاحب
 * می‌نویسد (`PUT /api/shop/branches/settings`). بی ردیف ⇒ هر دو خاموش (همان رفتارِ پیش از ۹۴۶).
 *
 *   staffOtherStock   POS-16/S17: کارمند (نه صاحب و مدیر — آن دو همیشه می‌بینند، همان Square/Shopify/Lightspeed) عددِ موجودیِ
 *                     همان کالا در شعبه‌های دیگر را از صندوق/قیمت‌پرسی ببیند. پیش‌فرض خاموش.
 *   transferNegative  INV-11: ارسالِ میانِ شعبه بیش از موجودیِ سرورِ مبدأ. خاموش ⇒ ۴۰۹ `insufficient_stock` و هیچ ردیف؛ روشن ⇒
 *                     حرکتِ «ثبت‌شده» با وظیفهٔ `negative_stock` برای همان شعبه (هرگز بی‌صدا). پیش‌فرض خاموش.
 *
 * ⛔ دکان فقط از عضویتِ توکن (فراخوان)؛ این‌جا هیچ شناسه‌ای از بدنه خوانده نمی‌شود. کلیدِ ناشناخته ⇒ ۴۰۰.
 */
const { one, query, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const DEFAULTS = Object.freeze({ staffOtherStock: false, transferNegative: false });
const FIELDS = Object.freeze({ staffOtherStock: 'staff_other_stock', transferNegative: 'transfer_negative' });

async function get(shopId, c = null) {
  const sql = 'SELECT staff_other_stock, transfer_negative FROM shop_branch_settings WHERE shop_id=$1';
  const r = c ? (await c.query(sql, [shopId])).rows[0] : await one(sql, [shopId]);
  if (!r) return { ...DEFAULTS };
  return { staffOtherStock: r.staff_other_stock === true, transferNegative: r.transfer_negative === true };
}

/** فقط کلیدهای آمده عوض می‌شوند (بولی؛ چیزِ دیگر ⇒ ۴۰۰) ⇒ { before, after } */
async function set(shopId, body, byUserId) {
  const b = body !== null && typeof body === 'object' && !Array.isArray(body) ? body : null;
  if (!b) throw badRequest('تنظیم‌ها لازم است', 'bad_settings');
  const keys = Object.keys(b);
  if (!keys.length) throw badRequest('تنظیم‌ها لازم است', 'bad_settings');
  for (const k of keys) {
    if (!Object.prototype.hasOwnProperty.call(FIELDS, k)) throw badRequest('این تنظیم شناخته نیست', 'unknown_setting');
    if (typeof b[k] !== 'boolean') throw badRequest('مقدارِ تنظیم فقط روشن یا خاموش است', 'bad_setting');
  }
  const before = await get(shopId);
  const after = { ...before };
  for (const k of keys) after[k] = b[k];
  await query(
    `INSERT INTO shop_branch_settings (shop_id, staff_other_stock, transfer_negative, updated_by, updated_at)
     VALUES ($1,$2,$3,$4,$5)
     ON CONFLICT (shop_id) DO UPDATE SET staff_other_stock=excluded.staff_other_stock,
       transfer_negative=excluded.transfer_negative, updated_by=excluded.updated_by, updated_at=excluded.updated_at`,
    [shopId, after.staffOtherStock, after.transferNegative, String(byUserId || '').slice(0, 80), now()]
  );
  return { before, after };
}

module.exports = { DEFAULTS, get, set };
