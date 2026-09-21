'use strict';
/**
 * میزِ «کدِ شاگرد» در پنلِ مدیریت — بندِ ۴.۴ سندِ ریمیکِ ریپوی `server`.
 *
 *   GET  /api/admin/shops/:id/staff-codes          فهرستِ پوشیده + شمارِ شاگردها
 *   POST /api/admin/shops/:id/staff-codes/reveal   کدِ ثابت را به مدیر نشان بده
 *
 * ── خواستهٔ صاحب سامانه ────────────────────────────────────────────────
 * «سومی بخشِ کد باشه که همهٔ حساب‌ها رو نشون بده و روی هر کدوم که زدم
 * کدِ شاگردش رو ببینم و چند تا شاگرد بهش وصل است.»
 *
 * ── و چرا این فایل دو در دارد، نه یکی ──────────────────────────────────
 *
 *  ⛔ **کدِ یک‌بارمصرف هیچ‌وقت دیده نمی‌شود و نباید بشود.** از آن فقط
 *     HMACش در دیتابیس است (`lib/staff-codes.js`: «نه کسی که به
 *     دیتابیس دست پیدا کند می‌تواند وارد دکانی شود و نه حتی مدیر
 *     سامانه کد شاگرد کسی را می‌بیند»). پس فهرست فقط چهار رقمِ آخر
 *     (`code_hint`) را می‌دهد. ⛔ این قاعده را برای «راحتیِ پنل» عوض
 *     نکنید — یک پشتیبانِ دزدیده‌شده یعنی ورود به هر دکانی.
 *
 *  ✅ **ولی کدِ ثابت بازساختنی است و این عمدی است**: `deriveStanding`
 *     آن را از `shopId` و `generation` با همان رازِ سرور می‌سازد، پس
 *     هیچ‌جا ذخیره نشده و در عینِ حال هر وقت لازم شد درمی‌آید. همان
 *     کدی هم هست که صاحبِ سامانه می‌خواهد ببیند: یک کد برای همهٔ
 *     شاگردهای یک دکان.
 *
 * ── سه نگهبان، همان‌های میزِ کدها ──────────────────────────────────────
 *  ⛔ نمایش فقط `requireSuperAdmin`
 *  ⛔ و همیشه در دفترِ رخدادها می‌نشیند
 *  ⛔ و **کد هیچ‌وقت در فهرست نمی‌آید** — فقط در پاسخِ همان مسیرِ جدا.
 *     وگرنه هر باز شدنِ صفحه یک «کد دیده شد» برای هر دکان می‌ساخت و آن
 *     دفتر بی‌معنا می‌شد.
 *
 *  ⛔ **و این در چیزی نمی‌سازد.** `staffCodes.standing()` اگر کدِ ثابتی
 *     نباشد یکی می‌سازد؛ این‌جا عمداً صدا زده **نمی‌شود**. ساختنِ کد
 *     کارِ صاحبِ دکان است، نه عارضهٔ جانبیِ یک `GET`ِ مدیر.
 */
const express = require('express');
const { one, many } = require('../db');
const staffCodes = require('../lib/staff-codes');
const audit = require('../lib/audit');
const { requireSuperAdmin } = require('../middleware/auth');
const { notFound } = require('../middleware/errors');
const { clientIp } = require('../middleware/ratelimit');
const v = require('../lib/validate');

const router = express.Router({ mergeParams: true });

/** ردیفِ کدِ ثابتِ زندهٔ یک دکان — یا `null`. */
async function standingRow(shopId) {
  return one(
    `SELECT id, generation, role, created_at, used_count
       FROM staff_codes WHERE shop_id=$1 AND standing AND status='active'`,
    [shopId]
  );
}

router.get('/', async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const shop = await one('SELECT id, name FROM shops WHERE id=$1', [id]);
    if (!shop) return next(notFound('دکان پیدا نشد'));

    /*
     *  ⚠️ «شاگرد» یعنی عضوی که صاحبِ دکان نیست. شمارشِ همهٔ اعضا عددِ
     *  یکی بیشتر می‌داد و صاحبِ سامانه را گمراه می‌کرد.
     */
    const members = await many(
      `SELECT m.role, m.status FROM shop_members m WHERE m.shop_id=$1`, [id]
    );
    const students = members.filter((m) => m.role !== 'owner');

    const rows = await staffCodes.list(id);
    const live = await standingRow(id);

    res.json({
      shop: { id: shop.id, name: shop.name },
      students: {
        total: students.length,
        active: students.filter((m) => m.status === 'active').length,
        members: members.length,
      },
      //  کدِ ثابت: هست یا نیست، و چند بار از آن استفاده شده — **بی خودِ کد**
      standing: live
        ? { id: live.id, role: live.role, generation: Number(live.generation),
            createdAt: Number(live.created_at), usedCount: Number(live.used_count), revealable: true }
        : null,
      //  ⛔ کدهای یک‌بارمصرف: فقط چهار رقمِ آخر، برای همیشه
      codes: rows.map((r) => ({
        id: r.id, hint: r.code_hint, role: r.role, status: r.status,
        createdAt: Number(r.created_at), expiresAt: r.expires_at === null ? null : Number(r.expires_at),
        maxUses: Number(r.max_uses), usedCount: Number(r.used_count),
      })),
    });
  } catch (err) { next(err); }
});

router.post('/reveal', requireSuperAdmin, async (req, res, next) => {
  try {
    const id = v.id(req.params.id);
    const live = await standingRow(id);
    if (!live) {
      return next(notFound('این دکان کدِ ثابتی ندارد — صاحبش باید یک بار بسازدش', 'no_standing_code'));
    }
    const code = staffCodes.deriveStanding(id, Number(live.generation));
    await audit.log({
      actorType: 'admin', action: 'staff_code.revealed', ip: clientIp(req),
      targetType: 'staff_code', targetId: String(live.id),
      detail: { shopId: id, generation: Number(live.generation) },
    });
    res.json({ ok: true, code, role: live.role, generation: Number(live.generation) });
  } catch (err) { next(err); }
});

module.exports = router;
