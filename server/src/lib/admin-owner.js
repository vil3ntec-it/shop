'use strict';
/**
 * «مالکِ اصلی» ِ سرورِ حساب — مدیرِ ارشدی که هیچ مدیرِ دیگری (حتی ارشد) نمی‌تواند غیرفعال یا تنزلش
 * دهد (یافتهٔ S12 ِ ممیزیِ پنلِ مرکزی؛ قاعدهٔ «مالک هرگز بیرون نمی‌ماند»).
 *
 * کیست: ستونِ `admins.is_owner` (مهاجرتِ ۱۶۸). اگر هنوز کسی نشان نخورده (پایگاهِ تازه)، پیشین‌ترین
 * مدیرِ ارشدِ **فعال** — همان حسابِ آغازینِ سرور (`ADMIN_BOOTSTRAP_*` یا `scripts/create-admin.js`) — نشان
 * می‌خورد. در هر لحظه فقط یکی (`ux_admins_owner`).
 *
 * ⛔ نشان فقط روی ارشدِ فعال معنا دارد. مهاجرتِ ۱۶۸ پیشین‌ترین ارشد را **بی نگاه به وضعیت** نشان زد؛ روی پایگاهی که آن
 * حساب بسته بود، صاحبِ واقعی بی‌پناه می‌ماند و ارشدِ دوم می‌بستش. هیچ API ای مالک را نافعال/غیرِارشد نمی‌کند (`owner_protected`)،
 * پس نشانِ روی حسابِ نافعال فقط از همان مهاجرت یا پوسته می‌آید ⇒ در نخستین خواندن (و هنگامِ بالا آمدنِ سرور، `app.js`) به
 * پیشین‌ترین ارشدِ فعال می‌رود، زیرِ قفل و با ردیفِ سابقهٔ `admin.owner_reassigned`. ارشدِ فعالی نیست ⇒ نشان همان‌جا می‌ماند.
 *
 * راهِ بازیابیِ مالک (رمز یا TOTP گم شده) فقط از پوستهٔ همان سرور است، نه از هیچ API ای:
 *   • `ADMIN_BOOTSTRAP_PASSWORD` ِ تازه در محیط ⇒ رمز برمی‌گردد، حساب فعال و TOTP خاموش می‌شود؛
 *   • یا `node scripts/create-admin.js <نامِ مالک> <رمزِ تازه>` ⇒ همان، و نقشِ مالک ارشد می‌ماند.
 */
const { one, tx } = require('../db');

const LIVE = `role='superadmin' AND status='active'`;
const OLDEST_LIVE = `SELECT id FROM admins WHERE ${LIVE} ORDER BY created_at ASC, id ASC LIMIT 1`;

/** شناسهٔ مالک (و اگر نیست یا نافعال است، پیشین‌ترین ارشدِ فعال را مالک کن). */
async function ownerId() {
  const r = await one(`SELECT id, (${LIVE}) AS live FROM admins WHERE is_owner LIMIT 1`);
  if (r && r.live) return r.id;
  try {
    const out = await tx(async (c) => {
      await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', ['admin-owner']);
      const cur = (await c.query(`SELECT id, (${LIVE}) AS live FROM admins WHERE is_owner LIMIT 1`)).rows[0];
      if (cur && cur.live) return cur.id;
      const next = (await c.query(OLDEST_LIVE)).rows[0];
      if (!next) return cur ? cur.id : null;
      if (cur) await c.query('UPDATE admins SET is_owner = false WHERE id = $1', [cur.id]);
      await c.query('UPDATE admins SET is_owner = true WHERE id = $1', [next.id]);
      if (cur) {
        await require('./audit').log({
          actorType: 'system', action: 'admin.owner_reassigned', targetType: 'admin', targetId: next.id,
          detail: { from: cur.id, why: 'flagged_owner_not_active_superadmin' },
        }, { client: c });
      }
      return next.id;
    });
    if (out) return out;
  } catch { /* دو نوشتنِ هم‌زمان بی قفل (پایگاهِ کهنه): ایندکسِ یکتا یکی را نگه داشت */ }
  const again = await one('SELECT id FROM admins WHERE is_owner LIMIT 1');
  return again ? again.id : null;
}

module.exports = { ownerId };
