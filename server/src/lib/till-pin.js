'use strict';
/**
 * STF-04 (S46) — PIN ِ شخصیِ صندوق در سرور و «نوبتِ سنجیدهٔ صندوق» (مهاجرتِ ۶۴۰).
 *
 * ── مسئله ──────────────────────────────────────────────────────────
 * روی صندوقِ مشترک یک حساب وارد است («حسابِ دستگاه»)؛ کاربرِ صندوق با PIN ِ خودش جابه‌جا می‌شود
 * (`core/staff/TillSwitch`). PIN تا امروز فقط روی همان دستگاه سنجیده می‌شد، پس سرور نمی‌دانست چه کسی
 * پشتِ صندوق است — نامی که دستگاه می‌فرستاد ادعای بی‌سنجش بود.
 *
 * ── قاعده ──────────────────────────────────────────────────────────
 *   • اثرِ PBKDF2-SHA256 ِ PIN ِ هر عضو (`shop_till_pins`؛ ۶۰۰٬۰۰۰ دور، نمکِ ۱۶ بایتی)؛ خودِ PIN نه
 *     نوشته می‌شود، نه در سابقه می‌آید، نه برمی‌گردد. شکلِ PIN ِ تازه همان `PinGuard.newPinProblem`
 *     (۶ رقم، نه همه یکسان و نه پشتِ سرِ هم؛ رقمِ فارسی/عربی پذیرفته).
 *   • سنجش فقط در سرور و زیرِ قفلِ ردیفِ همان PIN (حدس‌های هم‌زمان شمارِ خطا را دور نمی‌زنند): هر ۵ خطا
 *     ⇒ قفلِ زمان‌دارِ سرور، ۱۵ دقیقه و هر بار دو برابر تا ۲۴ ساعت. شمارِ خطا پیش از پاسخ commit می‌شود.
 *   • ⛔ R14: شمار فقط با PIN ِ درست یا PIN ِ تازه صفر می‌شود — [unlock] و پایانِ قفل فقط قفلِ کنونی را برمی‌دارند و پلهٔ
 *     دوبرابر می‌ماند. [VOID_AFTER] (۱۰) خطا از آخرین درستی ⇒ اثر پاک (`pin_hash=''`، «باطل»): سنجش تا PIN ِ تازه نه
 *     (خودِ عضو با `PUT /me` یا صاحب/مدیر)، وگرنه مدیر با «۵ حدس، بازکردن، ۵ حدس…» PIN ِ زیردست را پیدا می‌کرد.
 *   • ⛔ R15: «PIN ندارد/باطل است» را `/verify` فقط به خودِ عضو و کسی که `members.view` دارد (صاحب/مدیر، همان که
 *     `/members` را می‌بیند) می‌گوید (۴۰۹)؛ بقیه همان پاسخِ PIN ِ غلط را می‌گیرند: ردیفِ جانشین (`pin_hash=''`، `set_by=''`)
 *     همان شمار و قفل را دارد و سنجش همان PBKDF2 (با اثرِ ساختگی، همان دور) را می‌کند.
 *     ردیف با `pin_hash=''`: `set_by` پُر ⇒ PIN ِ باطل‌شده؛ تهی ⇒ جانشینِ عضوِ بی PIN.
 *   • PIN ِ درست روی دستگاهِ **توکن** (`req.device`، نه چیزی که برنامه می‌فرستد) یک نوبت باز می‌کند و
 *     نوبتِ بازِ پیشینِ همان دستگاه را می‌بندد (`switch`)؛ PIN ِ خودِ حسابِ دستگاه فقط می‌بندد (`self`).
 *     قفلِ صندوق (`lock`)، برداشتنِ PIN (`pin_removed`) و پایانِ عمر (`expired`، ۱۲ ساعت) هم می‌بندند.
 *     پس «چه کسی در لحظهٔ t پشتِ دستگاهِ d بود» را سرور خودش می‌داند ([whoAt]).
 *   • نوبت هیچ اختیاری نمی‌دهد: سرور همچنان با توکنِ حسابِ دستگاه می‌سنجد (سقفِ بالا)، و برنامه نقشِ
 *     کاربرِ صندوق را هرگز بالاتر از آن نمی‌برد (`TillSwitch.effectiveRole`).
 *
 * ── چه کسی PIN را می‌گذارد ─────────────────────────────────────────
 *   • هر عضو PIN ِ خودش را (`PUT /me`)؛ دیگری فقط صاحب (هر کس) و مدیر (فقط نقش‌های پایین‌تر از مدیر) —
 *     همان رتبهٔ `TillSwitch.rank`. هر نوشتن (گذاشتن، برداشتن، بازکردنِ قفل) تأییدِ دوبارهٔ ورود می‌خواهد
 *     (`lib/reauth.js`)، وگرنه شاگرد روی صندوقِ صاحب قفلِ PIN ِ صاحب را باز می‌کرد و حدس را از سر می‌گرفت.
 *   • ⛔ صاحب هرگز بیرون نمی‌ماند: قفلِ PIN فقط همین سنجش را می‌بندد (ورود و توکنِ حساب دست نمی‌خورند)،
 *     زمان‌دار است، و صاحب پس از تأییدِ دوبارهٔ ورود (رمز یا کدِ ایمیل) PIN ِ تازه می‌گذارد که قفل را هم باز می‌کند.
 *
 * ⛔ پشتِ پرچمِ خاموشِ `shop.till_pin` (live_config): خاموش ⇒ جز `config` هیچ خواندن/نوشتنی از دو جدول؛ تنها استثنا
 *    [memberGone] (تعلیق/برداشتنِ عضو) که فقط PIN و نوبتِ همان عضو را پاک می‌کند و می‌بندد (SR45-3).
 */
const crypto = require('crypto');
const { promisify } = require('util');
const { one, many, query, tx, now, newId } = require('../db');
const audit = require('./audit');
const orders = require('./orders');
const { badRequest, forbidden, notFound, conflict, ApiError } = require('../middleware/errors');

const pbkdf2 = promisify(crypto.pbkdf2);

const FLAG = 'shop.till_pin';
const PIN_ITER = 600_000;
const MAX_FAILURES = 5;
//  R14: دو دورِ قفل (۵ + ۵) از آخرین سنجشِ درست ⇒ باطل؛ حدسِ هر کس (مدیر با بازکردنِ قفل هم) پیش از PIN ِ درستِ بعدی ≤ ۱۰ از ۱۰⁶
const VOID_AFTER = 10;
const LOCK_BASE_MS = 15 * 60 * 1000;
const LOCK_MAX_MS = 24 * 60 * 60 * 1000;
const SESSION_MS = 12 * 60 * 60 * 1000;
const MAX_RANGE_MS = 62 * 24 * 60 * 60 * 1000;
const MANAGERS = ['owner', 'manager'];
const END_REASONS = ['lock'];

const flagOf = (shopId) => orders.flagOf(shopId, FLAG);
const isTestOrDev = () => ['test', 'development'].includes(String(process.env.NODE_ENV || '').trim());

/** رتبهٔ اختیار — همان `TillSwitch.rank`: صاحب > مدیر > بقیه */
function rank(role) {
  const r = String(role || '').trim().toLowerCase();
  return r === 'owner' ? 3 : r === 'manager' ? 2 : 1;
}

/** `actor` PIN ِ عضوِ دیگرِ `target` را می‌گذارد/برمی‌دارد/بازمی‌کند؟ صاحب همه را؛ مدیر فقط پایین‌تر از خودش */
function mayManage(actor, target) {
  if (actor.role === 'owner') return true;
  return actor.role === 'manager' && rank(target.role) < rank('manager');
}

/** رقم‌های فارسی/عربی ⇒ لاتین — همان `PinGuard.normalize` */
const latinDigits = (v) => String(v).replace(/[۰-۹]/g, (d) => String(d.charCodeAt(0) - 0x06F0))
  .replace(/[٠-٩]/g, (d) => String(d.charCodeAt(0) - 0x0660));

/** null = خوب؛ 'length' | 'weak' — همان `PinGuard.newPinProblem` */
function pinProblem(pin) {
  if (typeof pin !== 'string') return 'length';
  const p = latinDigits(pin);
  if (!/^\d{6}$/.test(p)) return 'length';
  if (/^(\d)\1{5}$/.test(p)) return 'weak';
  const d = [...p].map(Number);
  const step = (k) => d.every((x, i) => i === 0 || x - d[i - 1] === k);
  return step(1) || step(-1) ? 'weak' : null;
}

/** PIN ِ زده‌شده شکلِ سنجیدنی دارد؟ — همان `PinGuard.isWellFormed` (۴ تا ۶ رقم) */
const wellFormed = (pin) => typeof pin === 'string' && /^\d{4,6}$/.test(latinDigits(pin));

/** دورهای PBKDF2 ِ هش‌های تازه؛ فقط در آزمون/توسعه با `TILL_PIN_ITER` کمتر (هرگز در تولید) */
function pinIter() {
  const v = Number(process.env.TILL_PIN_ITER);
  return isTestOrDev() && Number.isSafeInteger(v) && v >= 1000 ? v : PIN_ITER;
}

async function hashPin(pin) {
  const salt = crypto.randomBytes(16);
  const iter = pinIter();
  const h = await pbkdf2(latinDigits(pin), salt, iter, 32, 'sha256');
  return `pbkdf2-sha256$${iter}$${salt.toString('base64')}$${h.toString('base64')}`;
}

async function pinMatches(pin, stored) {
  const parts = String(stored || '').split('$');
  if (parts.length !== 4 || parts[0] !== 'pbkdf2-sha256' || !wellFormed(pin)) return false;
  const iter = Number(parts[1]);
  const floor = isTestOrDev() ? 1000 : 100_000;
  if (!Number.isSafeInteger(iter) || iter < floor || iter > 10_000_000) return false;
  const want = Buffer.from(parts[3], 'base64');
  if (want.length !== 32) return false;
  const got = await pbkdf2(latinDigits(pin), Buffer.from(parts[2], 'base64'), iter, 32, 'sha256');
  return crypto.timingSafeEqual(got, want);
}

//  R15: اثرِ ساختگی برای ردیفِ بی PIN — همان کارِ [pinMatches] (همان دورِ [pinIter]) تا زمانِ پاسخ «ندارد» را نگوید؛ نتیجه‌اش دور ریخته می‌شود
const DUMMY = { salt: crypto.randomBytes(16).toString('base64'), want: crypto.randomBytes(32).toString('base64') };
const dummyHash = () => `pbkdf2-sha256$${pinIter()}$${DUMMY.salt}$${DUMMY.want}`;

/** ردیفی با اثرِ واقعی (نه جانشینِ بی PIN، نه PIN ِ باطل‌شده) */
const usable = (row) => Boolean(row && row.pin_hash);
/** PIN ِ این ردیف باطل شده ([VOID_AFTER])، نه هرگز گذاشته‌نشده */
const voided = (row) => Boolean(row && !row.pin_hash && row.set_by);

/** «PIN ندارد» یا «باطل شد» — فقط برای خودِ عضو و صاحب/مدیر (R15) */
function notSetError(row) {
  return voided(row)
    ? conflict('این PIN پس از ۱۰ سنجشِ غلط باطل شد؛ PIN ِ تازه بگذارید.', 'till_pin_invalidated')
    : conflict('این عضو هنوز PIN ِ صندوق در سرور ندارد', 'till_pin_not_set');
}

/** قفلِ پس از هر ۵ خطا: ۱۵ دقیقه، و هر بار دو برابر تا ۲۴ ساعت */
const lockMs = (failures) => Math.min(LOCK_MAX_MS, LOCK_BASE_MS * 2 ** (failures / MAX_FAILURES - 1));

function lockedError(until, t) {
  const e = new ApiError(423, 'till_pin_locked', 'این PIN چند بار اشتباه زده شد و فعلاً بسته است؛ بعداً بکوشید، یا صاحبِ دکان قفل را باز کند.');
  e.data = { lockedUntil: Number(until), retryAfter: Math.max(1, Math.ceil((Number(until) - t) / 1000)) };
  return e;
}
function wrongError(left) {
  const e = forbidden('PIN درست نیست.', 'till_pin_wrong');
  e.data = { attemptsLeft: left };
  return e;
}

/** گذاشتنِ PIN ِ [tm]: خودش، یا عضوی که [actor] اختیارش را دارد ([mayManage]) */
function mayWrite(actor, tm) {
  if (tm.id !== actor.id && !mayManage(actor, tm)) throw forbidden('PIN ِ این عضو را فقط صاحبِ دکان می‌گذارد', 'till_pin_forbidden');
}

function newPin(pin) {
  const problem = pinProblem(pin);
  if (problem === 'length') throw badRequest('PIN باید ۶ رقم باشد.', 'till_pin_format');
  if (problem === 'weak') throw badRequest('این PIN ساده است (همه یکسان یا پشتِ سرِ هم)؛ PIN ِ دیگری بگذارید.', 'till_pin_weak');
  return pin;
}

const NAME_SQL = `COALESCE(NULLIF(u.name,''), u.phone, '')`;

/** عضوِ فعالِ همین دکان با شناسهٔ عضو یا کاربر؛ نبود ⇒ ۴۰۴ (دکانِ دیگر هم همین) */
async function memberOf(c, shopId, { memberId, userId }, lock = false) {
  const by = memberId ? 'm.id' : 'm.user_id';
  const id = String(memberId || userId || '');
  if (!id || id.length > 64) throw notFound('این عضو در دکان نیست', 'member_not_found');
  const r = (await c.query(
    `SELECT m.id, m.user_id, m.role, ${NAME_SQL} AS name FROM shop_members m LEFT JOIN users u ON u.id = m.user_id
      WHERE ${by}=$1 AND m.shop_id=$2 AND m.status='active'${lock ? ' FOR UPDATE OF m' : ''}`,
    [id, shopId],
  )).rows[0];
  if (!r) throw notFound('این عضو در دکان نیست', 'member_not_found');
  return r;
}

const shapeMember = (m) => ({ memberId: m.id, userId: m.user_id, name: m.name || '', role: m.role });

function shapeSession(r, t = now()) {
  const expired = r.ended_at == null && Number(r.expires_at) <= t;
  return {
    id: r.id, memberId: r.till_member_id, name: r.name ?? undefined, role: r.role ?? undefined,
    deviceMemberId: r.device_member_id, deviceName: r.device_name ?? undefined,
    startedAt: Number(r.started_at), expiresAt: Number(r.expires_at),
    endedAt: r.ended_at != null ? Number(r.ended_at) : expired ? Number(r.expires_at) : null,
    endReason: r.ended_at != null ? r.end_reason : expired ? 'expired' : '',
  };
}

/** نوبتِ بازِ دستگاه را می‌بندد (کهنه ⇒ در همان پایانِ عمر، `expired`) ⇒ شمارِ بسته‌شده */
async function closeOpen(c, shopId, where, params, reason, t) {
  const r = await c.query(
    `UPDATE shop_till_sessions
        SET ended_at = CASE WHEN expires_at <= $2 THEN expires_at ELSE $2 END,
            end_reason = CASE WHEN expires_at <= $2 THEN 'expired' ELSE $3 END
      WHERE shop_id=$1 AND ended_at IS NULL AND ${where}`,
    [shopId, t, reason, ...params],
  );
  return r.rowCount || 0;
}

/** دو سنجش/بستنِ هم‌زمان روی یک دستگاه پشتِ هم (نوبتِ باز یکتاست: `ux_shop_till_sessions_open`) */
const lockDevice = (c, shopId, deviceId) => c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`till-device:${shopId}:${deviceId}`]);

const deviceOf = (device) => {
  if (!device || !device.id) throw conflict('این نشست به دستگاهِ شناخته‌ای بسته نیست؛ یک بار خارج و دوباره وارد شوید.', 'till_device_unknown');
  return device;
};

// ════════════════════════════════════════════════════════════════════

/** همیشه باز. پرچمِ خاموش ⇒ هیچ خواندنی از جدول‌های ۶۴۰ */
async function config(shopId, actor) {
  const enabled = await flagOf(shopId);
  const out = { enabled, memberId: actor.id, role: actor.role };
  if (!enabled) return out;
  const r = await one('SELECT pin_hash, set_by, locked_until FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [shopId, actor.id]);
  const t = now();
  const pinSet = usable(r);
  const until = pinSet ? Number(r.locked_until || 0) : 0;
  return { ...out, pinSet, invalidated: voided(r), locked: until > t, lockedUntil: until > t ? until : null };
}

/** اعضای فعال با حالِ PIN (برای «کاربرانِ صندوق» ِ صاحب و مدیر) — هرگز اثرِ PIN؛ `invalidated` ⇒ PIN ِ تازه لازم (R14) */
async function members(shopId) {
  const t = now();
  const rows = await many(
    `SELECT m.id, m.user_id, m.role, ${NAME_SQL} AS name, p.set_at, p.locked_until,
            COALESCE(p.pin_hash <> '', false) AS pin_set, COALESCE(p.pin_hash = '' AND p.set_by <> '', false) AS pin_void
       FROM shop_members m LEFT JOIN users u ON u.id = m.user_id
       LEFT JOIN shop_till_pins p ON p.shop_id = m.shop_id AND p.member_id = m.id
      WHERE m.shop_id=$1 AND m.status='active' ORDER BY m.created_at, m.id LIMIT 500`,
    [shopId],
  );
  return {
    members: rows.map((r) => {
      const until = r.pin_set ? Number(r.locked_until || 0) : 0;
      return {
        ...shapeMember(r), pinSet: Boolean(r.pin_set), invalidated: Boolean(r.pin_void),
        setAt: r.pin_set && r.set_at != null ? Number(r.set_at) : null, locked: until > t, lockedUntil: until > t ? until : null,
      };
    }),
  };
}

/** گذاشتن یا عوض کردنِ PIN ِ [target] (خودش، یا عضوی که [actor] اختیارش را دارد). قفل و شمارِ خطا پاک می‌شوند. */
async function setPin(shopId, actor, target, pin, { ip = '' } = {}) {
  newPin(pin);
  //  ⛔ SR45-2: عضو و اختیار پیش از PBKDF2 (۶۰۰٬۰۰۰ دور، بیرون از تراکنش) — وگرنه هر ۴۰۳/۴۰۴ همان کارِ سنگین را می‌کرد؛
  //  درونِ تراکنش دوباره زیرِ قفلِ ردیفِ عضو سنجیده می‌شود
  mayWrite(actor, await memberOf({ query }, shopId, target));
  const hash = await hashPin(pin);
  const t = now();
  let log;
  const m = await tx(async (c) => {
    const tm = await memberOf(c, shopId, target, true);
    mayWrite(actor, tm);
    await c.query(
      `INSERT INTO shop_till_pins (shop_id, member_id, pin_hash, failures, locked_until, set_by, set_at)
       VALUES ($1,$2,$3,0,0,$4,$5)
       ON CONFLICT (shop_id, member_id) DO UPDATE SET pin_hash=excluded.pin_hash, failures=0, locked_until=0,
         set_by=excluded.set_by, set_at=excluded.set_at`,
      [shopId, tm.id, hash, actor.id, t],
    );
    log = await audit.logIn(c, {
      shopId, userId: actor.userId, action: 'till_pin.set', targetType: 'member', targetId: tm.id,
      detail: { self: tm.id === actor.id }, ip,
    });
    return tm;
  });
  await log.afterCommit();
  return { member: shapeMember(m), pinSet: true };
}

/** برداشتنِ PIN ِ [target] و بستنِ نوبتِ بازش */
async function removePin(shopId, actor, target, { ip = '' } = {}) {
  let log;
  const out = await tx(async (c) => {
    const tm = await memberOf(c, shopId, target, true);
    if (tm.id !== actor.id && !mayManage(actor, tm)) throw forbidden('PIN ِ این عضو را فقط صاحبِ دکان برمی‌دارد', 'till_pin_forbidden');
    //  ردیفِ جانشین یا باطل‌شده هم می‌رود، ولی «برداشته شد» فقط برای اثرِ واقعی
    const r = await c.query('DELETE FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2 RETURNING pin_hash', [shopId, tm.id]);
    const removed = r.rows.filter(usable).length;
    const ended = await closeOpen(c, shopId, 'till_member_id=$4', [tm.id], 'pin_removed', now());
    log = await audit.logIn(c, {
      shopId, userId: actor.userId, action: 'till_pin.removed', targetType: 'member', targetId: tm.id,
      detail: { removed, ended }, ip,
    });
    return { member: shapeMember(tm), removed: removed > 0, ended };
  });
  await log.afterCommit();
  return out;
}

/**
 * ⛔ SR45-3: عضو تعلیق یا برداشته شد (`shops.updateMember`، **همان تراکنش** که توکن‌ها و کلیدِ دستگاهش را باطل می‌کند) ⇒
 * PIN اش پاک و نوبتِ بازش بسته (`pin_removed`). وگرنه تا ۱۲ ساعت `sessions`/[whoAt] او را «پشتِ دستگاه» نشان می‌دادند و
 * عضوِ دوباره‌فعال با همان PIN ِ کهنه برمی‌گشت. فقط پاک می‌کند و می‌بندد (هیچ ردیفِ تازه)، پس پرچم را نمی‌پرسد: PIN ِ مانده
 * از روزِ روشن بودن هم می‌رود.
 */
async function memberGone(c, shopId, memberId) {
  const r = await c.query('DELETE FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2', [shopId, memberId]);
  const ended = await closeOpen(c, shopId, 'till_member_id=$4', [memberId], 'pin_removed', now());
  return { removed: r.rowCount || 0, ended };
}

/**
 * بازکردنِ قفلِ PIN ِ [target] (نه برای خودِ عضو: او PIN ِ تازه می‌گذارد).
 * ⛔ R14: فقط قفلِ کنونی برداشته می‌شود؛ شمارِ خطا (پلهٔ دوبرابر و نزدیکی به [VOID_AFTER]) می‌ماند، و PIN ِ باطل‌شده با
 * بازکردن برنمی‌گردد (۴۰۹ `till_pin_invalidated`) — وگرنه «۵ حدس، بازکردن، ۵ حدس…» بی‌پایان بود.
 */
async function unlock(shopId, actor, target, { ip = '' } = {}) {
  let log;
  const out = await tx(async (c) => {
    const tm = await memberOf(c, shopId, target, true);
    if (!mayManage(actor, tm)) throw forbidden('قفلِ PIN ِ این عضو را فقط صاحبِ دکان باز می‌کند', 'till_pin_forbidden');
    const row = (await c.query('SELECT pin_hash, set_by, failures, locked_until FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2 FOR UPDATE', [shopId, tm.id])).rows[0];
    if (!usable(row)) throw notSetError(row);
    await c.query('UPDATE shop_till_pins SET locked_until=0 WHERE shop_id=$1 AND member_id=$2', [shopId, tm.id]);
    const failures = Number(row.failures);
    log = await audit.logIn(c, {
      shopId, userId: actor.userId, action: 'till_pin.unlocked', targetType: 'member', targetId: tm.id,
      detail: { failures, wasLocked: Number(row.locked_until) > now() }, ip,
    });
    return { member: shapeMember(tm), unlocked: true, untilInvalid: Math.max(0, VOID_AFTER - failures) };
  });
  await log.afterCommit();
  return out;
}

/**
 * سنجشِ PIN ِ [target] روی دستگاهِ توکنِ [actor] ⇒ نوبتِ تازه (یا null وقتی خودِ حسابِ دستگاه برگشت).
 * خطا (PIN ِ غلط، قفل) پس از commit ِ شمارِ خطا پرتاب می‌شود.
 * [seesAll]: درخواست‌کننده `members.view` دارد (صاحب/مدیر) — فقط او و خودِ عضو «ندارد/باطل» را می‌بینند (R15).
 */
async function verify(shopId, actor, device, target, pin, { ip = '', seesAll = false } = {}) {
  const dev = deviceOf(device);
  if (typeof pin !== 'string' || !pin.trim()) throw badRequest('PIN را بزنید.', 'till_pin_required');
  const t = now();
  let log;
  const out = await tx(async (c) => {
    const tm = await memberOf(c, shopId, target);
    const reveal = seesAll || tm.id === actor.id;
    //  ⛔ R15: برای دیگران عضوِ بی PIN یک ردیفِ جانشین دارد تا شمار و قفل و پاسخ همان PIN ِ غلط باشد (همان گام‌ها برای هر دو)
    if (!reveal) {
      await c.query(
        `INSERT INTO shop_till_pins (shop_id, member_id, pin_hash, failures, locked_until, set_by, set_at)
         VALUES ($1,$2,'',0,0,'',$3) ON CONFLICT (shop_id, member_id) DO NOTHING`,
        [shopId, tm.id, t],
      );
    }
    const row = (await c.query('SELECT * FROM shop_till_pins WHERE shop_id=$1 AND member_id=$2 FOR UPDATE', [shopId, tm.id])).rows[0];
    const real = usable(row);
    if (!real && reveal) return { error: notSetError(row) };
    if (Number(row.locked_until) > t) return { error: lockedError(row.locked_until, t) };
    const match = await pinMatches(pin, real ? row.pin_hash : dummyHash());
    if (!real || !match) {
      const failures = Number(row.failures) + 1;
      const lockNow = failures % MAX_FAILURES === 0;
      //  ⛔ R14: دهمین خطا از آخرین درستی ⇒ اثر پاک (باطل)؛ فقط PIN ِ تازه برمی‌گرداند. جانشین هم همان سابقه را می‌گیرد (R15)
      const voidNow = failures === VOID_AFTER;
      const until = lockNow ? t + lockMs(failures) : Number(row.locked_until);
      await c.query(
        `UPDATE shop_till_pins SET failures=$3, locked_until=$4, pin_hash=CASE WHEN $5::boolean THEN '' ELSE pin_hash END
          WHERE shop_id=$1 AND member_id=$2`,
        [shopId, tm.id, failures, until, voidNow],
      );
      log = await audit.logIn(c, {
        shopId, userId: actor.userId, action: voidNow ? 'till_pin.invalidated' : lockNow ? 'till_pin.locked' : 'till_pin.wrong',
        targetType: 'member', targetId: tm.id, detail: { failures, device: dev.id }, ip,
      });
      return { error: lockNow ? lockedError(until, t) : wrongError(MAX_FAILURES - (failures % MAX_FAILURES)) };
    }
    if (Number(row.failures)) await c.query('UPDATE shop_till_pins SET failures=0 WHERE shop_id=$1 AND member_id=$2', [shopId, tm.id]);
    const self = tm.id === actor.id;
    await lockDevice(c, shopId, dev.id);
    const ended = await closeOpen(c, shopId, 'device_id=$4', [dev.id], self ? 'self' : 'switch', t);
    let session = null;
    if (!self) {
      const r = (await c.query(
        `INSERT INTO shop_till_sessions (shop_id, id, till_member_id, device_member_id, device_id, device_uid, started_at, expires_at)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
        [shopId, newId('till'), tm.id, actor.id, dev.id, String(dev.device_uid || '').slice(0, 120), t, t + SESSION_MS],
      )).rows[0];
      session = shapeSession({ ...r, name: tm.name, role: tm.role }, t);
    }
    log = await audit.logIn(c, {
      shopId, userId: actor.userId, action: self ? 'till_session.self' : 'till_session.open', targetType: 'member', targetId: tm.id,
      detail: { device: dev.id, session: session ? session.id : '', ended }, ip,
    });
    return { member: shapeMember(tm), session };
  });
  if (log) await log.afterCommit();
  if (out.error) throw out.error;
  return out;
}

/** نوبتِ بازِ (و زندهٔ) دستگاهِ توکن، با نام و نقشِ کاربرِ صندوق؛ عضوِ رفته ⇒ null */
async function current(shopId, device) {
  if (!device || !device.id) return { session: null };
  const t = now();
  const r = await one(
    `SELECT s.*, ${NAME_SQL} AS name, m.role FROM shop_till_sessions s
       JOIN shop_members m ON m.id = s.till_member_id AND m.shop_id = s.shop_id AND m.status='active'
       LEFT JOIN users u ON u.id = m.user_id
      WHERE s.shop_id=$1 AND s.device_id=$2 AND s.ended_at IS NULL AND s.expires_at > $3`,
    [shopId, device.id, t],
  );
  return { session: r ? shapeSession(r, t) : null };
}

/** بستنِ نوبتِ بازِ دستگاهِ توکن (قفلِ صندوق) — فقط کم می‌کند */
async function endCurrent(shopId, actor, device, reason = 'lock', { ip = '' } = {}) {
  if (!END_REASONS.includes(reason)) throw badRequest('دلیلِ بستن نادرست است', 'till_end_reason');
  if (!device || !device.id) return { ended: 0 };
  let log;
  const ended = await tx(async (c) => {
    await lockDevice(c, shopId, device.id);
    const n = await closeOpen(c, shopId, 'device_id=$4', [device.id], reason, now());
    if (n) log = await audit.logIn(c, { shopId, userId: actor.userId, action: 'till_session.end', targetType: 'device', targetId: device.id, detail: { reason }, ip });
    return n;
  });
  if (log) await log.afterCommit();
  return { ended };
}

/** نوبت‌های یک بازه (حداکثر ۶۲ روز). صاحب و مدیر همه را (یا یک عضو)؛ بقیه فقط نوبت‌های خودشان */
async function sessions(shopId, actor, { from, to, memberId } = {}) {
  const t = now();
  const end = to === undefined || to === '' ? t : Number(to);
  const start = from === undefined || from === '' ? end - 7 * 24 * 60 * 60 * 1000 : Number(from);
  if (!Number.isSafeInteger(start) || !Number.isSafeInteger(end) || start > end) throw badRequest('بازه نادرست است', 'range_invalid');
  if (end - start > MAX_RANGE_MS) throw badRequest('بازه بیش از ۶۲ روز است', 'range_invalid');
  const who = MANAGERS.includes(actor.role) ? (memberId ? String(memberId) : null) : actor.id;
  const rows = await many(
    `SELECT s.*, ${NAME_SQL} AS name, m.role, d.name AS device_name FROM shop_till_sessions s
       LEFT JOIN shop_members m ON m.id = s.till_member_id AND m.shop_id = s.shop_id
       LEFT JOIN users u ON u.id = m.user_id
       LEFT JOIN devices d ON d.id = s.device_id
      WHERE s.shop_id=$1 AND s.started_at <= $3 AND COALESCE(s.ended_at, s.expires_at) >= $2
        AND ($4::text IS NULL OR s.till_member_id=$4)
      ORDER BY s.started_at, s.id LIMIT 2000`,
    [shopId, start, end, who],
  );
  return { from: start, to: end, serverTime: t, sessions: rows.map((r) => shapeSession(r, t)) };
}

/**
 * چه کسی (به سنجشِ سرور) در لحظهٔ [at] پشتِ دستگاهِ [deviceId] بود ⇒ شناسهٔ عضو، یا null (خودِ حسابِ دستگاه).
 * برای نسبت دادنِ کارِ آن دستگاه در آن لحظه؛ پرچمِ خاموش ⇒ null بی هیچ خواندنی.
 */
async function whoAt(shopId, deviceId, at) {
  if (!deviceId || !Number.isSafeInteger(Number(at)) || !(await flagOf(shopId))) return null;
  const r = await one(
    `SELECT till_member_id FROM shop_till_sessions
      WHERE shop_id=$1 AND device_id=$2 AND started_at <= $3 AND $3 < COALESCE(ended_at, expires_at)
      ORDER BY started_at DESC LIMIT 1`,
    [shopId, String(deviceId), Number(at)],
  );
  return r ? r.till_member_id : null;
}

module.exports = {
  FLAG, MAX_FAILURES, VOID_AFTER, SESSION_MS, LOCK_BASE_MS, flagOf, rank, mayManage, pinProblem, lockMs,
  config, members, setPin, removePin, memberGone, unlock, verify, current, endCurrent, sessions, whoAt,
};
