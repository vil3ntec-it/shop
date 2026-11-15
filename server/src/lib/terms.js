'use strict';
/**
 * شرایط و ضوابط استفاده از برنامه — قراردادِ `GET /api/auth/terms` و گامِ ثبت‌نام (`register/verify` ‹terms›،
 * `register/complete` ‹terms.version› ⇐ `users.terms_version`).
 *
 * متن دیگر این‌جا نوشته نمی‌شود: منبعِ یکتا `core/legal/legal-texts.json` است (fa، en، ps) و `legal-texts.json` ِ کنارِ
 * همین پرونده رونوشتِ ساختهٔ `node tools/legal/generate.mjs` (image ِ سرور فقط `server/` را دارد). همان متن درونِ
 * برنامه‌ها (`core/.../legal/Legal.kt`) و در صفحهٔ عمومیِ `GET /legal/terms` (`routes/legal-pages.js`) هست.
 * پاسخِ این‌جا همان فارسیِ پیشین است، بایت‌به‌بایت (`legal-texts.test.js`).
 *
 * متن که عوض شد، `terms.version` ِ منبع را یک شماره جلو ببرید: نسخه‌ای که کاربر پذیرفته روی حسابش می‌ماند.
 */
const legal = require('./legal-texts.json');

const VERSION = legal.terms.version;
const TITLE = legal.terms.fa.title;
const SECTIONS = legal.terms.fa.sections.map((s) => ({ title: s.title, body: [...s.paras, ...s.items].join('\n\n') }));

/** همان متن، یک‌تکه — برای جایی که فهرست به درد نمی‌خورد. */
function plainText() {
  return `${TITLE}\n\n` + SECTIONS.map(s => `- ${s.title}: ${s.body}`).join('\n\n');
}

module.exports = { VERSION, TITLE, SECTIONS, plainText };
