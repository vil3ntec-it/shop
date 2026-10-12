'use strict';
/**
 * ══ کدِ اشتراکِ آفلاینِ فروشگاه — بسته به یک کامپیوتر، یک بار ══════════
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۲۰): «کد افلاین اشتراک باشه که کسی اگه نت
 * هم نداشت بتونه از برنامه اشتراک بگیره؛ برای هر کامپیوتر یک ایدی درست
 * بشه و از سرور به اون ادرس کد داده میشه و روی همون کد فقط کار کنه و یک
 * بار بیشتر هم استفاده نشه.»
 *
 *   ۱) برنامهٔ «فروشگاه من» «شناسهٔ کامپیوتر» نشان می‌دهد (۱۶ نویسه)
 *   ۲) پنل همان را با مدت می‌زند ⇒ این‌جا با کلیدِ مجوز امضا می‌شود
 *   ۳) کد در برنامه زده می‌شود ⇒ بی اینترنت سنجیده می‌شود و اشتراک باز
 *   ۴) برنامه آنلاین شد ⇒ ‎POST /api/vip/offline-code‎ ⇒ اشتراکِ دکانِ همان حساب
 *
 * ── شکلِ دودویی — ⛔ ‎OfflineCode.kt‎ی برنامه مو‌به‌مو همین را می‌خواند:
 *
 *      0      نسخه = 1
 *      1      پلن: 1 زمان‌دار · 3 دائمی
 *      2..9   کامپیوتر: ۶۴ بیتِ نخستِ SHA-256("tohid-shop|offline-pc|v1|" ‖ اثرِ انگشت)
 *      10..13 حساب: ۴ بایتِ نخستِ SHA-256("tohid-shop|offline-acct|v1|" ‖ شناسهٔ کاربر)؛
 *             صفر = هر حسابی
 *      14..17 سریال (تصادفی)
 *      18..19 روزِ صدور — روز از ۲۰۲۴/۰۱/۰۱ِ UTC
 *      20..21 روزِ پایان (انحصاری)؛ 0xFFFF = دائمی
 *      22..85 امضای ES256 (r‖s) روی SHA-256("TSOC1\0" ‖ بایت‌های ۰..۲۱)
 *
 *    ⇒ ۸۶ بایت ⇒ ۱۳۸ نویسهٔ base32ِ کراکفورد، پنج‌تا‌پنج‌تا با «-».
 *
 * ⛔ پیشوندِ «TSOC1» دامنه را جدا می‌کند: کدِ آفلاینِ پمپ («PYOC2») با
 *    همین کلید امضا می‌شود و همان شکل را دارد — بی این پیشوند، کدِ پمپ در
 *    برنامهٔ فروشگاه هم می‌نشست.
 * ⛔ «یک بار» دو نگهبان دارد: سرور (یک کد ⇒ یک دکان؛ ‎code_used_elsewhere‎)
 *    و خودِ برنامه (سریالِ برداشته یا جایگزین‌شده دوباره نمی‌نشیند).
 */
const crypto = require('crypto');
const { one, many, query, tx, newId, now } = require('../db');
const license = require('./license');
const pump = require('./offline-codes');
const { ApiError, badRequest, notFound, conflict } = require('../middleware/errors');

const VERSION = 1;
const BODY = 22;
const TOTAL = BODY + 64;
const DOMAIN = Buffer.from('TSOC1\0', 'latin1');
const DAY = 86_400_000;
const EPOCH_DAY = Date.UTC(2024, 0, 1) / DAY;
const PERMANENT = 0xFFFF;
/** دائمی روی سرور = پنجاه سال (همان قاعدهٔ پمپ) */
const PERM_YEARS = 50;

const PLANS = Object.freeze({
  sub: { byte: 1, title: 'اشتراکِ زمان‌دار', defDays: 365 },
  perm: { byte: 3, title: 'دائمی', defDays: 0 },
});
const PLAN_OF_BYTE = { 1: 'sub', 3: 'perm' };

function accountTag(userId) {
  if (!userId) return Buffer.alloc(4);
  return crypto.createHash('sha256').update('tohid-shop|offline-acct|v1|' + userId, 'utf8').digest().subarray(0, 4);
}

/** اثرِ انگشتِ برنامه ⇒ «شناسهٔ کامپیوتر» — فقط آزمون؛ سرور اثرِ انگشت نمی‌بیند. */
function computerOfFingerprint(fp) {
  const h = crypto.createHash('sha256').update('tohid-shop|offline-pc|v1|' + fp, 'utf8').digest();
  const m = Buffer.from(h.subarray(0, 10));
  m[9] &= 0xE0;
  return pump.computerOfBytes(m);
}

function body({ plan, machine, account, serial, issuedDay, endDay }) {
  const b = Buffer.alloc(BODY);
  b[0] = VERSION;
  b[1] = PLANS[plan].byte;
  Buffer.from(machine).copy(b, 2, 0, 8);
  Buffer.from(account).copy(b, 10, 0, 4);
  Buffer.from(serial).copy(b, 14, 0, 4);
  b.writeUInt16BE(issuedDay, 18);
  b.writeUInt16BE(endDay, 20);
  return b;
}

async function encode(b, sign) {
  const sig = await sign(Buffer.concat([DOMAIN, b]));
  if (sig.length !== 64) throw new Error('امضا ۶۴ بایت نیست');
  return pump.b32encode(Buffer.concat([b, sig])).match(/.{1,5}/g).join('-');
}

function parse(code) {
  const buf = pump.b32decode(code, TOTAL);
  if (!buf || buf[0] !== VERSION) return null;
  const plan = PLAN_OF_BYTE[buf[1]];
  if (!plan) return null;
  const issuedDay = buf.readUInt16BE(18);
  const endDay = buf.readUInt16BE(20);
  return {
    plan,
    machine: buf.subarray(2, 10),
    account: buf.subarray(10, 14),
    serial: buf.subarray(14, 18).toString('hex'),
    issuedAt: (EPOCH_DAY + issuedDay) * DAY,
    endsAt: endDay === PERMANENT ? 0 : (EPOCH_DAY + endDay) * DAY,
    permanent: endDay === PERMANENT,
    body: buf.subarray(0, BODY),
    sig: buf.subarray(BODY, TOTAL),
  };
}

async function verify(code) {
  const p = parse(code);
  if (!p) return null;
  return (await license.verifyBytes(Buffer.concat([DOMAIN, p.body]), p.sig)) ? p : null;
}

function shape(row) {
  return {
    id: row.id,
    serial: row.serial,
    plan: row.plan,
    planTitle: PLANS[row.plan]?.title || row.plan,
    computer: row.computer,
    issuedAt: Number(row.issued_at),
    endsAt: Number(row.ends_at) || 0,
    permanent: !Number(row.ends_at),
    note: row.note,
    status: row.status,
    revokedAt: row.revoked_at ? Number(row.revoked_at) : null,
    accountEmail: row.account_email || '',
    redeemedShopId: row.redeemed_shop_id || '',
    redeemedAt: row.redeemed_at ? Number(row.redeemed_at) : null,
    createdAt: Number(row.created_at),
  };
}

/** صدور ⇒ { code, offline }. `days` برای زمان‌دار (پیش‌فرض ۳۶۵، سقف ۳۶۵۰)؛ دائمی هیچ. */
async function issue({ plan, computer, account = '', days = null, note = '', createdBy = '', at = now() } = {}) {
  const P = PLANS[plan];
  if (!P) throw badRequest('نوعِ کد باید «زمان‌دار» یا «دائمی» باشد', 'bad_plan');
  const pc = pump.parseComputer(computer);
  if (!pc) throw badRequest('شناسهٔ کامپیوتر درست نیست — ۱۶ نویسه، همان که برنامه نشان می‌دهد', 'bad_computer');

  let user = null;
  const email = String(account || '').trim().toLowerCase();
  if (email) {
    user = await one('SELECT id, email FROM users WHERE lower(email)=$1', [email]);
    if (!user) throw notFound('حسابی با این ایمیل نیست', 'account_not_found');
  }

  const today = Math.floor(at / DAY) - EPOCH_DAY;
  let endDay = PERMANENT;
  if (plan !== 'perm') {
    const d = days === null || days === undefined || days === '' ? P.defDays : Number(days);
    if (!Number.isInteger(d) || d < 1 || d > 3650) throw badRequest('روزها باید بینِ ۱ و ۳۶۵۰ باشد', 'bad_days');
    endDay = today + d + 1;
  }

  for (let attempt = 0; ; attempt++) {
    const serial = crypto.randomBytes(4);
    const b = body({ plan, machine: pc.machine, account: accountTag(user?.id), serial, issuedDay: today, endDay });
    const code = await encode(b, license.signBytes);
    try {
      const row = await one(
        `INSERT INTO shop_offline_codes (id, serial, plan, computer, issued_at, ends_at, note, created_by, created_at,
                                         account_user_id, account_email)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11) RETURNING *`,
        [newId('sfc'), serial.toString('hex'), plan, pc.text, (EPOCH_DAY + today) * DAY,
          endDay === PERMANENT ? 0 : (EPOCH_DAY + endDay) * DAY, String(note || '').slice(0, 300), createdBy, at,
          user?.id || '', user?.email || '']
      );
      return { code, offline: shape(row) };
    } catch (err) {
      if (attempt < 3 && /unique|duplicate/i.test(String(err.message))) continue;
      throw err;
    }
  }
}

async function list({ limit = 100 } = {}) {
  const rows = await many(`SELECT * FROM shop_offline_codes ORDER BY created_at DESC LIMIT $1`,
    [Math.min(Math.max(Number(limit) || 100, 1), 500)]);
  return rows.map(shape);
}

async function revoke(id) {
  const row = await one(`UPDATE shop_offline_codes SET status='revoked', revoked_at=$2 WHERE id=$1 RETURNING *`, [id, now()]);
  if (!row) throw notFound('این کد پیدا نشد', 'not_found');
  return shape(row);
}

/**
 * برنامه آنلاین شد و کد را آورد ⇒ اشتراکِ همان دکان.
 *
 * ⛔ باطل ⇒ ۴۱۰ · دکانِ دیگر ⇒ ۴۰۹ ‎code_used_elsewhere‎ · کامپیوترِ دیگر ⇒ ‎computer_mismatch‎
 * ⛔ کد اشتراکِ بلندترِ موجود را کوتاه نمی‌کند (‎covered‎) و دوباره آوردنش روز اضافه نمی‌کند.
 */
async function redeem({ shopId, userId, code, computer, at = now() }) {
  const p = await verify(code);
  if (!p) throw badRequest('این کدِ اشتراک معتبر نیست', 'bad_offline_code');
  const pc = pump.parseComputer(computer);
  if (!pc || !pc.machine.subarray(0, 8).equals(p.machine)) {
    throw badRequest('این کد برای کامپیوترِ دیگری ساخته شده است', 'computer_mismatch');
  }
  if (p.account.some(b => b !== 0)) {
    const members = await many(
      `SELECT user_id AS u FROM shop_members WHERE shop_id=$1 AND status='active'
       UNION SELECT owner_user_id AS u FROM shops WHERE id=$1`, [shopId]);
    const ids = [userId, ...members.map(m => m.u)];
    if (!ids.some(id => accountTag(id).equals(p.account))) {
      throw conflict('این کد برای حسابِ دیگری ساخته شده است', 'account_mismatch');
    }
  }

  const claim = await tx(async (c) => {
    let row = (await c.query('SELECT * FROM shop_offline_codes WHERE serial=$1 FOR UPDATE', [p.serial])).rows[0];
    if (!row) {
      //  کدِ امضاشدهٔ خودمان که ردیفش نیست (دیتابیسِ بازگردانده‌شده) ⇒ ثبت
      row = (await c.query(
        `INSERT INTO shop_offline_codes (id, serial, plan, computer, issued_at, ends_at, created_by, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,'redeem',$7) RETURNING *`,
        [newId('sfc'), p.serial, p.plan, pc.text, p.issuedAt, p.endsAt, at]
      )).rows[0];
    }
    if (row.status === 'revoked') return { row, revoked: true };
    if (row.redeemed_shop_id && row.redeemed_shop_id !== shopId) return { row, elsewhere: true };
    if (row.redeemed_shop_id === shopId) return { row, again: true };
    row = (await c.query(
      `UPDATE shop_offline_codes SET redeemed_shop_id=$2, redeemed_user_id=$3, redeemed_at=$4 WHERE id=$1 RETURNING *`,
      [row.id, shopId, String(userId || ''), at])).rows[0];
    return { row, first: true };
  });

  if (claim.revoked) throw new ApiError(410, 'code_revoked', 'این کدِ اشتراک باطل شده است');
  if (claim.elsewhere) throw conflict('این کد پیش از این روی دکانِ دیگری ثبت شده است', 'code_used_elsewhere');
  if (claim.again) return { status: 'already', offline: shape(claim.row), granted: false };

  const endMs = p.permanent ? p.issuedAt + PERM_YEARS * 365 * DAY : p.endsAt;
  if (endMs <= at) return { status: 'expired', offline: shape(claim.row), granted: false };

  const subs = require('./subscriptions').shop;
  const live = await one(
    `SELECT * FROM subscriptions WHERE shop_id=$1 AND status='active' ORDER BY created_at DESC LIMIT 1`, [shopId]);
  if (live && Number(live.ends_at) >= endMs) return { status: 'covered', offline: shape(claim.row), granted: false };

  try {
    await subs.grant(shopId, {
      plan: p.plan === 'perm' ? 'perm' : 'offline',
      endsAt: endMs,
      note: `کدِ آفلاین ${p.serial}`,
      createdBy: 'offline-code',
    });
  } catch (err) {
    //  ثبت نشد (مثلاً اشتراکِ معلق) ⇒ کد آزاد می‌ماند تا بعد دوباره بیاید
    await query(`UPDATE shop_offline_codes SET redeemed_shop_id=NULL, redeemed_user_id='', redeemed_at=NULL WHERE id=$1`,
      [claim.row.id]);
    throw err;
  }
  return { status: 'granted', offline: shape(claim.row), granted: true };
}

module.exports = {
  issue, redeem, verify, parse, list, revoke, shape, body, encode, accountTag, computerOfFingerprint,
  PLANS, TOTAL, BODY, EPOCH_DAY, PERMANENT, DOMAIN,
};
