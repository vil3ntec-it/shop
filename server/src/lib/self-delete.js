'use strict';
/**
 * حذفِ حساب به دستِ **خودِ صاحبِ حساب** — درِ برنامه و پورتالِ وب (قاعدهٔ Google Play: هر برنامه‌ای که حساب
 * می‌سازد باید راهِ حذفِ حساب را هم در خودِ برنامه و هم در یک نشانیِ وب بدهد؛ `docs/GOOGLE-PLAY-fa.md`).
 *
 * ⛔ کارِ حذف همان `lib/user-delete.js` است — همان تراکنش، همان فهرستِ «چه می‌رود، چه نمی‌ماند»، همان
 *    `sync-v1-live.accessChanged` — نه منطقِ دوم. این‌جا فقط درِ خودِ کاربر و سدهایش است:
 *   • پرچمِ `account.self_delete` (scope `all`) **پیش‌فرض خاموش** ⇒ `GET` می‌گوید `enabled:false` و برنامه
 *     راهِ «از پشتیبانی بخواهید» را نشان می‌دهد؛ `POST` ۴۰۳ `self_delete_disabled` بی هیچ نوشتنی.
 *   • حسابی که پمپ دارد یا عضوِ پمپی است ⇒ ۴۰۹ `has_pump`: حذف از این در پمپ را هم می‌بُرد و راه‌های پمپ
 *     بی اجازهٔ صاحب‌کار دست نمی‌خورند (#615). چنین حسابی از پشتیبانی/پنلِ مدیر حذف می‌شود.
 *   • تأییدِ دوبارهٔ هویت در هر بخش (`reauth.assertFresh(req, { allSections: true })`): خودِ حساب را می‌گیرد.
 *   • تایپِ همان نشانیِ حساب (ایمیل ⇐ شماره ⇐ شناسه؛ همان `confirmWith` ِ پنل).
 */
const { one } = require('../db');
const userDelete = require('./user-delete');

const FLAG = 'account.self_delete';

/** پرچمِ سراسری — فقط `true` روشن است */
async function enabled() {
  const row = await one("SELECT value FROM live_config WHERE key=$1 AND scope='all'", [FLAG]);
  if (!row) return false;
  try { return JSON.parse(row.value) === true; } catch { return false; }
}

/** آن‌چه کاربر باید تایپ کند — نشانیِ خودِ همان حساب (دادهٔ خودش است، نه راز) */
function confirmValue(user, kind) {
  if (kind === 'email') return String(user.email || '').trim();
  if (kind === 'phone') return String(user.phone || '').trim();
  return String(user.id || '');
}

/** چرا این حساب از این در حذف نمی‌شود — `null` یعنی می‌شود */
function blockOf(pv) {
  if (pv.stations.length > 0 || Number(pv.memberOf?.stations || 0) > 0) return 'has_pump';
  return null;
}

/** پیش‌نمایشِ بی نوشتن برای برگهٔ برنامه و پورتال */
async function view(user) {
  const pv = await userDelete.preview(user.id);
  if (!pv) return null;
  return {
    enabled: await enabled(),
    blocked: blockOf(pv),
    confirmWith: pv.confirmWith,
    confirmValue: confirmValue({ ...pv.user, phone: user.phone }, pv.confirmWith),
    shops: pv.shops.map((s) => ({ name: s.name, otherMembers: s.otherMembers })),
    memberOfShops: Number(pv.memberOf?.shops || 0),
  };
}

/**
 * حذف. خروجی: `{ ok: true, shops }` یا `{ ok: false, code }` با `code` یکی از `self_delete_disabled`،
 * `not_found`، `has_pump`، `confirm_mismatch` (همراهِ `confirmWith`).
 * ⚠️ تأییدِ دوبارهٔ هویت را صدازننده پیش از این می‌سنجد.
 */
async function run(userId, confirm) {
  if (!(await enabled())) return { ok: false, code: 'self_delete_disabled' };
  const pv = await userDelete.preview(userId);
  if (!pv) return { ok: false, code: 'not_found' };
  const blocked = blockOf(pv);
  if (blocked) return { ok: false, code: blocked };
  const out = await userDelete.remove(userId, { confirm: String(confirm || '') });
  if (!out.ok) return out;
  return { ok: true, shops: out.shops };
}

module.exports = { FLAG, enabled, view, run };
