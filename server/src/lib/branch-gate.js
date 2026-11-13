'use strict';
/**
 * نگهبانِ شعبه برای درهایی که هنوز شعبه نمی‌شناسند (فازِ ۱؛ `docs/BRANCHES-fa.md` §۵).
 *
 * این درها فقط دادهٔ **شعبهٔ اصلی** را دارند (حسابِ امروزِ دکان): Sync v1 (`routes/sync-v1.js`، مشترک با پمپ و
 * دست‌نخورده)، راهِ قدیمِ همگام‌سازی (`/sync`، `/shop/sync`)، همهٔ `/shop/*` جز `/shop/branches` و `/shop/approval-requests`
 * (شعبه‌شناس)، `/events` و
 * `/me/backups` (پشتیبانِ کاملِ دفترِ دکان). بیرون: «دکانِ من» (`GET /shop`، `/shop/me`، سطحِ دکان؛ فازِ ۲). پس با پرچمِ روشنِ `shop.branches`:
 *   ⛔ عضوی که شعبهٔ اصلی را ندارد (کارمندِ فقط شعبهٔ دیگر) ⇒ ۴۰۳ `branch_main_only` — هرگز دادهٔ اصلی را نمی‌خواند
 *      و دادهٔ شعبهٔ خودش را در اصلی نمی‌نویسد.
 *   ⛔ هر ادعای شعبهٔ غیرِ اصلی (`X-Branch-Id`) روی این درها ⇒ ۴۰۳ `branch_unsupported` (حتی صاحب): وگرنه دستگاهی
 *      که خیال می‌کند در شعبهٔ ب است دفترش را در اصلی می‌نشاند. شعبهٔ دیگر فقط از Sync v3 (`routes/sync-v3.js`).
 *
 * ⛔ پرچمِ خاموش، بی توکن، توکنِ نادرست یا حسابِ پمپ ⇒ `next()` بی هیچ اثری: خودِ در همان پاسخِ امروز را می‌دهد.
 * ⛔ راهِ تند: نه ادعا و نه هیچ انتسابِ شعبه در هیچ دکانی ⇒ همه شعبهٔ اصلی را دارند ⇒ بی احراز و بی پرسشِ دیگر.
 *    فقط وقتی لازم است احراز را روی **نسخهٔ سایه**ٔ درخواست (`Object.create(req)`) می‌زند — همان میان‌افزارهای
 *    مشترک (`requireSyncAccount`، `requireUser`/`requireShop`) فقط صدا زده می‌شوند، نه عوض؛ شکستشان به `next()` و
 *    خودِ در می‌رسد (همان ۴۰۱ ِ امروز).
 */
const branches = require('./branches');
const { forbidden } = require('../middleware/errors');
const { SHOP } = require('./tenancy');

/** کدام نوع در: 'v1' | 'rest' | null (شعبه‌شناس یا بیرون از دکان) */
function kindOf(p, method = 'GET') {
  const path = String(p || '');
  //  ساختنِ دکانِ تازهٔ خودش یا پیوستن به دکانی با کد: دادهٔ هیچ شعبه‌ای از این دکان خوانده/نوشته نمی‌شود
  if (method === 'POST' && /^\/shop(\/create|\/join|\/staff\/join)?\/?$/.test(path)) return null;
  if (/^\/sync\/v3(\/|$)/.test(path)) return null;
  if (/^\/sync\/v1(\/|$)/.test(path)) return 'v1';
  //  درهای شعبه‌شناس: خودشان دسترسیِ شعبه را می‌سنجند
  if (/^\/shop\/(branches|approval-requests)(\/|$)/.test(path)) return null;
  //  کلیدِ تأییدکنندهٔ خودِ عضو (STF-02): سطحِ دکان، بی دادهٔ هیچ شعبه — مدیرِ فقط شعبهٔ دیگر هم باید بتواند ثبت/باطل کند
  if (/^\/shop\/approver-keys(\/|$)/.test(path)) return null;
  //  فازِ ۲: «دکانِ من» (`GET /shop`، `/shop/me`) فقط سطحِ دکان است (دکان، نقش، مجوز، اشتراک) — برنامهٔ کارمندِ فقط شعبهٔ
  //  دیگر نقش و دکانش را از همین می‌گیرد (وگرنه صاحب فرض می‌کرد). فقط خواندن؛ نوشتن روی همین نشانی‌ها نگهبان دارد.
  if (method === 'GET' && /^\/shop(\/me)?\/?$/.test(path)) return null;
  if (/^\/(shop|sync|events)(\/|$)/.test(path)) return 'rest';
  if (/^\/me\/backups(\/|$)/.test(path)) return 'rest';
  return null;
}

function hasBearer(req) {
  return /^Bearer\s+\S/i.test(String((req.headers && req.headers.authorization) || ''));
}

/** پاسخِ سایه: هر صدا بی اثر (میان‌افزارِ احراز شکستش را با `next(err)` می‌گوید، نه با نوشتن در پاسخ) */
function shadowRes() {
  const locals = {};
  return new Proxy({}, { get: (t, k) => (k === 'locals' ? locals : () => t) });
}

function run(mw, probe) {
  return new Promise((resolve) => {
    try {
      Promise.resolve(mw(probe, shadowRes(), (err) => resolve(!err))).catch(() => resolve(false));
    } catch { resolve(false); }
  });
}

/** هویتِ درخواست روی نسخهٔ سایه ⇒ `{shopId, member}` یا null */
async function who(req, kind) {
  const probe = Object.create(req);
  if (kind === 'v1') {
    const { requireSyncAccount } = require('./sync-v1-auth');
    if (!(await run(requireSyncAccount, probe))) return null;
    const s = probe.sync;
    if (!s || s.app !== SHOP.app || s.accountKind !== SHOP.app || !s.accountId) return null;
    return { shopId: s.accountId, member: { role: s.role, user_id: s.userId } };
  }
  const { requireUser, requireShop } = require('../middleware/auth');
  if (!(await run(requireUser, probe))) return null;
  if (!(await run(requireShop, probe))) return null;
  if (!probe.shopId || !probe.member) return null;
  return { shopId: probe.shopId, member: probe.member };
}

async function gate(req, res, next) {
  try {
    const kind = kindOf(req.path, req.method);
    if (!kind || !hasBearer(req)) return next();
    const claim = branches.claimOf(req);
    if (claim === null && !(await branches.anyAssignments())) return next();
    const w = await who(req, kind);
    if (!w || !(await branches.enabled(w.shopId))) return next();
    if (claim !== null && branches.idOf(claim) !== branches.MAIN) {
      return next(forbidden('این در فقط شعبهٔ اصلی را دارد؛ شعبهٔ دیگر از همگام‌سازیِ v3', 'branch_unsupported'));
    }
    const access = await branches.accessOf(w.shopId, w.member);
    if (!branches.allows(access, branches.MAIN)) {
      return next(forbidden('این در دادهٔ شعبهٔ اصلی است و در دسترسیِ شما نیست', 'branch_main_only'));
    }
    next();
  } catch (err) { next(err); }
}

module.exports = { gate, kindOf };
