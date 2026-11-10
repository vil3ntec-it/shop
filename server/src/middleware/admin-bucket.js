'use strict';
/**
 * سطلِ جدای ترافیکِ **اثبات‌شدهٔ** مدیر و پلِ پنلِ خانگی — کنارِ سطلِ عمومیِ «هر IP» (`generalMax`، `app.js`).
 *
 * چرا: پلِ پنل از 127.0.0.1 و بی `X-Forwarded-For` می‌آید، پس کارت‌های داشبورد، صفحه‌های مدیر و درخواست‌های کلیدِ
 * سرویس همه سطلِ عمومیِ همان یک IP را می‌خوردند و چند دقیقه کارِ تند در کنسول صاحبِ سامانه را از پنلِ خودش بیرون
 * می‌انداخت («سقفِ نرخِ سرورِ حساب پر شده»، ۱۴۰۵/۰۷/۱۰). قاعده‌ها (`test/admin-bucket.test.js`):
 *   • فقط درهای مدیر (`/api[/v1]/admin/*`)، بیرون از ورود، تأییدِ دوباره، دومرحله‌ای و مدیرِ پمپ (همان سقفِ پیشین).
 *   • فقط اعتبارنامه‌ای که **خودِ سرور** درست می‌داند: کلیدِ سرویسِ فعال (`vsk_…`، هشِ SHA-256 در `admin_service_keys`)
 *     یا نشستِ زندهٔ مدیرِ فعال (`tokens` ِ نوعِ admin). سطل به‌ازای همان کلید / همان مدیر (`adminProvenMax`)، نه IP —
 *     چرخاندنِ XFF سطلِ تازه نمی‌سازد و بی‌سقف هم نیست.
 *   • ساختگی، باطل، منقضی، توکنِ کاربر یا بی‌توکن ⇒ **همان** سطلِ عمومیِ IP، مثلِ پیش.
 *   • ⛔ سقفِ IP پیش از احراز: پرسیدنِ پایگاه برای اعتبارنامهٔ ناشناخته سطلِ IP ِ خودش را دارد (`adminProbeMax`؛
 *     درست‌ها پس داده می‌شوند، فقط شکست‌ها می‌مانند). پر شد، یا پایگاه خطا داد ⇒ بی پرسش، سطلِ عمومی. درست‌ها ده
 *     دقیقه در حافظه (فقط هشِ SHA-256 ِ اعتبارنامه).
 *   • این فقط «کدام سطل» است، نه اجازه: `requireAdmin` هر درخواست را دوباره و کامل می‌سنجد و شکستش همان دم
 *     اعتبارنامه را از این حافظه برمی‌دارد (`forget`) — ابطال، خروج و غیرفعال شدن بی‌درنگ.
 *   • `RATE_ADMIN_BUCKET=0` ⇒ همان رفتارِ پیشین.
 */
const { createHash } = require('node:crypto');
const config = require('../config');
const { rateLimit, refund } = require('./ratelimit');
const { bearerToken } = require('../lib/bearer');

const ADMIN = /^\/api\/(?:v1\/)?admin\//;
const EXCLUDED = /^\/api\/(?:v1\/)?admin\/(?:login|reauth|2fa|pump)(?:\/|$)/;
//  ابطال/خروج/غیرفعال شدن را `forget` همان دم می‌گیرد (هر نمونه در نخستین شکستِ خودش)؛ TTL فقط کهنگیِ حافظه است
const TTL_MS = 10 * 60 * 1000;
const MAX_CACHED = 2000;
const PRINCIPAL = Symbol('adminBucket');

/** sha256(اعتبارنامه) ⇒ { who, until } — خودِ اعتبارنامه هرگز نگه داشته نمی‌شود */
const proven = new Map();
const digest = (token) => createHash('sha256').update(token).digest('hex');

/** مسیرِ رمزگشوده، `//` یکی، حروفِ کوچک (مسیریابیِ express به بزرگی و کوچکی حساس نیست) — شک ⇒ نه */
function eligible(req) {
  let p;
  try { p = decodeURIComponent(req.path || ''); } catch { return false; }
  p = p.replace(/\/{2,}/g, '/').toLowerCase();
  return ADMIN.test(p) && !EXCLUDED.test(p);
}

/** کلیدِ سرویسِ فعال ⇒ `svc:<id>`؛ نشستِ زندهٔ مدیرِ فعال ⇒ `adm:<id>`؛ دیگر هیچ. */
async function resolve(token) {
  const svc = require('../lib/admin-service');
  if (svc.isServiceKey(token)) {
    const key = await svc.verify(token);
    return key ? `svc:${key.id}` : null;
  }
  const row = await require('../lib/tokens').verify(token, 'admin');
  if (!row) return null;
  const a = await require('../db').one('SELECT status FROM admins WHERE id=$1', [row.subject_id]);
  return a && a.status === 'active' ? `adm:${row.subject_id}` : null;
}

function cached(h) {
  const e = proven.get(h);
  if (!e) return null;
  if (e.until > Date.now()) return e.who;
  proven.delete(h);
  return null;
}
function remember(h, who) {
  if (proven.size >= MAX_CACHED) proven.delete(proven.keys().next().value);
  proven.set(h, { who, until: Date.now() + TTL_MS });
}
/** `requireAdmin` نپذیرفت ⇒ از درخواستِ بعد همان سطلِ IP */
function forget(token) {
  if (typeof token === 'string' && token) proven.delete(digest(token));
}
/** میان‌افزارِ سطلِ عمومی را می‌پیچد؛ هر چه اثبات نشد همان `general` است. */
function withAdminBucket(general) {
  const probe = rateLimit({ max: config.rateLimit.adminProbeMax, keyPrefix: 'admin-probe', refundable: true });
  const own = rateLimit({ max: config.rateLimit.adminProvenMax, keyPrefix: 'admin-proven', key: (req) => req[PRINCIPAL] });
  return function adminBucket(req, res, next) {
    if (!config.rateLimit.adminBucket || !eligible(req)) return general(req, res, next);
    const token = bearerToken(req.headers.authorization);
    if (!token) return general(req, res, next);
    const h = digest(token);
    const use = (who) => { req[PRINCIPAL] = who; return own(req, res, next); };
    const who = cached(h);
    if (who) return use(who);
    return probe(req, res, (err) => {
      if (err) {
        if (err.status !== 429) return next(err);
        res.removeHeader('Retry-After');
        return general(req, res, next);
      }
      resolve(token).then((found) => {
        if (!found) return general(req, res, next);
        refund(req, 'admin-probe');
        remember(h, found);
        return use(found);
      }, () => general(req, res, next)).catch(next);
    });
  };
}

module.exports = { withAdminBucket, forget };
