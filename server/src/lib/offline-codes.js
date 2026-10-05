'use strict';
/**
 * ══ کدِ اشتراکِ آفلاین — بسته به یک کامپیوتر ══════════════════════════
 *
 * خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۵): «برای کسانی که نت ندارن هم اشتراک
 * بدم… سه نوع کد… یک گیرنده برای برنامه تا کد رو بزنم درجا قفل‌ها باز بشه
 * و بدون نت هم اشتراک داده بشه، و اگه یارو اینترنت پیدا کرد… سرور همون کد
 * رو ببینه و بگه آره این حساب اشتراک داره.»
 *
 *   ۱) برنامه «کدِ کامپیوتر» نشان می‌دهد (۱۶ نویسه، از MachineGuidِ ویندوز)
 *   ۲) صاحبِ سامانه همان را با پلن در پنل می‌زند ⇒ این‌جا امضا می‌شود
 *   ۳) کد (یا فایلِ ‎.pumpkey‎) در برنامه زده می‌شود ⇒ بی اینترنت سنجیده
 *      می‌شود و قفل‌ها همان لحظه باز
 *   ۴) روزی که آن کامپیوتر به حسابی بند شد ⇒ برنامه کد را به ‎/redeem‎
 *      می‌فرستد و اشتراک روی پمپِ همان حساب می‌نشیند
 *
 * ── شکلِ دودوییِ کد (نسخهٔ ۱) — ⛔ برنامهٔ پمپ (‎OfflineKey.cs‎) مو‌به‌مو
 *    همین را می‌خواند؛ عوض کردنِ یک بایت یعنی همهٔ کدهای صادرشده باطل:
 *
 *      0      نسخه = 1
 *      1      پلن: 1 استاندارد · 2 وی‌آی‌پی · 3 دائمی
 *      2..11  کامپیوتر: ۷۵ بیتِ نخستِ SHA-256ِ اثرِ انگشت (۵ بیتِ آخر صفر)
 *      12..17 سریال (تصادفی)
 *      18..21 صدور — ثانیهٔ یونیکس، big-endian
 *      22..25 پایان — ثانیهٔ یونیکس؛ 0xFFFFFFFF = دائمی
 *      26..33 ‎kid‎ — ۸ بایتِ نخستِ SHA-256ِ کلیدِ عمومی (همان ‎kid‎ِ مجوز)
 *      34..97 امضای ES256 (r‖s) روی ‎SHA-256("PYOC1\0" ‖ بایت‌های ۰..۳۳)
 *
 *    ⇒ ۹۸ بایت ⇒ ۱۵۷ نویسهٔ base32ِ کراکفورد، پنج‌تا‌پنج‌تا با «-».
 *
 * ⚠️ پیشوندِ «PYOC1» جدا کردنِ دامنه است: همین کلید مجوزِ JWS هم امضا
 * می‌کند و هیچ امضایی از این‌جا نباید آن‌جا معنایی داشته باشد.
 *
 * ── نسخهٔ ۲ (۱۴۰۵/۰۷/۲۰) — «کوتاه‌تر، برای هر کامپیوتر و هر حساب متفاوت،
 *    یک بار استفاده» — از امروز فقط همین صادر می‌شود؛ نسخهٔ ۱ همچنان
 *    پذیرفته است (کدهای صادرشده باطل نمی‌شوند):
 *
 *      0      نسخه = 2
 *      1      پلن
 *      2..9   کامپیوتر: ۶۴ بیتِ نخستِ همان بایت‌های کامپیوترِ نسخهٔ ۱
 *      10..13 حساب: ۴ بایتِ نخستِ SHA-256("pump-yaqobi|offline-acct|v2|" ‖ شناسهٔ کاربر)؛
 *             صفر = هر حسابی (کامپیوتری که هرگز آنلاین نشده)
 *      14..17 سریال (تصادفی)
 *      18..19 روزِ صدور — روز از ۲۰۲۴/۰۱/۰۱ِ UTC
 *      20..21 روزِ پایان (انحصاری)؛ 0xFFFF = دائمی
 *      22..85 امضای ES256 روی SHA-256("PYOC2\0" ‖ بایت‌های ۰..۲۱)
 *
 *    ⇒ ۸۶ بایت ⇒ ۱۳۸ نویسه. ‎kid‎ در کد نیست: برنامه هر کلیدِ داخلِ خودش را
 *    می‌آزماید (یکی یا دو کلید). امضا کوتاه‌تر از ۶۴ بایت نمی‌شود بی
 *    رمزنگاریِ دست‌ساز — و آن را نمی‌سازیم.
 */
const crypto = require('crypto');
const { one, many, query, tx, newId, now } = require('../db');
const license = require('./license');
const subs = require('./subscriptions').pump;
const { ApiError, badRequest, notFound, conflict } = require('../middleware/errors');

const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTVWXYZ';
const VERSION = 1;
const BODY_LEN = 34;
const TOTAL_LEN = BODY_LEN + 64;
const DOMAIN = Buffer.from('PYOC1\0', 'latin1');
const PERMANENT = 0xFFFFFFFF;
const DAY = 86_400_000;

const V2 = 2;
const BODY2 = 22;
const TOTAL2 = BODY2 + 64;
const DOMAIN2 = Buffer.from('PYOC2\0', 'latin1');
const EPOCH_DAY = Date.UTC(2024, 0, 1) / DAY;
const PERMANENT2 = 0xFFFF;

/** شناسهٔ کاربر ⇒ ۴ بایتِ «حساب» در کد (هم‌آهنگ با ‎OfflineKey.AccountTag‎ی برنامه). */
function accountTag(userId) {
  if (!userId) return Buffer.alloc(4);
  return crypto.createHash('sha256').update('pump-yaqobi|offline-acct|v2|' + userId, 'utf8').digest().subarray(0, 4);
}

/**
 * بدنهٔ نسخهٔ ۲ — خالص (آزمون‌ها و ابزارِ بردارِ آزمونِ برنامه هم از همین می‌سازند).
 * ⇒ Buffer(22)
 */
function body2({ plan, machine, account, serial, issuedDay, endDay }) {
  const b = Buffer.alloc(BODY2);
  b[0] = V2;
  b[1] = PLANS[plan].byte;
  Buffer.from(machine).copy(b, 2, 0, 8);
  Buffer.from(account).copy(b, 10, 0, 4);
  Buffer.from(serial).copy(b, 14, 0, 4);
  b.writeUInt16BE(issuedDay, 18);
  b.writeUInt16BE(endDay, 20);
  return b;
}

/** بدنه + امضا ⇒ کدِ پنج‌تا‌پنج‌تا. `sign(buf) ⇒ Buffer(64)` */
async function encode2(body, sign) {
  const sig = await sign(Buffer.concat([DOMAIN2, body]));
  if (sig.length !== 64) throw new Error('امضا ۶۴ بایت نیست');
  return group(b32encode(Buffer.concat([body, sig])), 5);
}

const PLANS = Object.freeze({
  std: { byte: 1, title: 'استاندارد', defDays: 365 },
  vip: { byte: 2, title: 'وی‌آی‌پی', defDays: 365 },
  perm: { byte: 3, title: 'دائمی', defDays: 0 },
});
const PLAN_OF_BYTE = { 1: 'std', 2: 'vip', 3: 'perm' };

/* ── base32ِ کراکفورد ─────────────────────────────────────────────── */

function b32encode(buf) {
  let bits = 0, value = 0, out = '';
  for (const b of buf) {
    value = (value << 8) | b; bits += 8;
    while (bits >= 5) { out += ALPHABET[(value >>> (bits - 5)) & 31]; bits -= 5; }
    value &= (1 << bits) - 1;
  }
  if (bits > 0) out += ALPHABET[(value << (5 - bits)) & 31];
  return out;
}

/** نویسه ⇒ عدد؛ O ⇒ 0 و I/L ⇒ 1 (خطای خواندن)، بقیه ⇒ -1. */
function charValue(ch) {
  const c = ch.toUpperCase();
  if (c === 'O') return 0;
  if (c === 'I' || c === 'L') return 1;
  return ALPHABET.indexOf(c);
}

/** فقط حرف و رقم می‌ماند (خط تیره، فاصله، خطِ تازه دور ریخته می‌شوند). */
function clean(raw) {
  return String(raw || '')
    //  رقمِ فارسی و عربی هم پذیرفته است — کد از پیامِ واتساپ چسبانده می‌شود
    .replace(/[۰-۹]/g, d => String('۰۱۲۳۴۵۶۷۸۹'.indexOf(d)))
    .replace(/[٠-٩]/g, d => String('٠١٢٣٤٥٦٧٨٩'.indexOf(d)))
    .replace(/[^0-9A-Za-z]/g, '');
}

function b32decode(str, byteLen) {
  const s = clean(str);
  if (s.length !== Math.ceil(byteLen * 8 / 5)) return null;
  const out = Buffer.alloc(byteLen);
  let bits = 0, value = 0, i = 0;
  for (const ch of s) {
    const v = charValue(ch);
    if (v < 0) return null;
    value = (value << 5) | v; bits += 5;
    if (bits >= 8) {
      if (i < byteLen) out[i++] = (value >>> (bits - 8)) & 0xff;
      bits -= 8;
      value &= (1 << bits) - 1;
    }
  }
  //  بیت‌های پرکنِ ته باید صفر باشند، وگرنه دو نوشته یک کد می‌شدند
  if (i !== byteLen || value !== 0) return null;
  return out;
}

function group(s, n) {
  return s.match(new RegExp(`.{1,${n}}`, 'g')).join('-');
}

/* ── کدِ کامپیوتر ─────────────────────────────────────────────────── */

function checkOf(values) {
  let sum = 0;
  values.forEach((v, i) => { sum += (i + 1) * v; });
  return sum % 29;
}

/** ۱۰ بایتِ کامپیوتر ⇒ «XXXX-XXXX-XXXX-XXXX» (۱۵ نویسه + یک نویسهٔ سنجش). */
function computerOfBytes(machine) {
  const data = b32encode(machine).slice(0, 15);
  const values = [...data].map(charValue);
  return group(data + ALPHABET[checkOf(values)], 4);
}

/**
 * اثرِ انگشتِ برنامه (‎CloudConfig.MachineFingerprint‎، «m-…») ⇒ کدِ کامپیوتر.
 * فقط برای آزمون‌ها — سرور هیچ‌وقت اثرِ انگشت نمی‌بیند.
 */
function computerOfFingerprint(fp) {
  const h = crypto.createHash('sha256').update('pump-yaqobi|offline-pc|v1|' + fp, 'utf8').digest();
  const m = Buffer.from(h.subarray(0, 10));
  m[9] &= 0xE0;
  return computerOfBytes(m);
}

/** ورودیِ آدم ⇒ {text, machine} یا null (نویسهٔ سنجش جور نیست / طول غلط). */
function parseComputer(raw) {
  const s = clean(raw).toUpperCase();
  if (s.length !== 16) return null;
  const values = [...s].map(charValue);
  if (values.some(v => v < 0)) return null;
  if (checkOf(values.slice(0, 15)) !== values[15]) return null;
  const data = values.slice(0, 15).map(v => ALPHABET[v]).join('');
  //  ۱۵ نویسه = ۷۵ بیت ⇒ ۱۰ بایت با ۵ بیتِ صفر در ته
  const machine = b32decode(data + '0', 10);
  if (!machine) return null;
  return { text: group(data + ALPHABET[values[15]], 4), machine };
}

/* ── خودِ کد ──────────────────────────────────────────────────────── */

async function kidBytes() {
  return Buffer.from(await license.keyId(), 'hex').subarray(0, 8);
}

/** کد ⇒ بخش‌هایش. امضا این‌جا سنجیده نمی‌شود. */
function parse(code) {
  if (clean(code).length === Math.ceil(TOTAL2 * 8 / 5)) return parse2(code);
  const buf = b32decode(code, TOTAL_LEN);
  if (!buf || buf[0] !== VERSION) return null;
  const plan = PLAN_OF_BYTE[buf[1]];
  if (!plan) return null;
  const issued = buf.readUInt32BE(18);
  const ends = buf.readUInt32BE(22);
  return {
    plan,
    machine: buf.subarray(2, 12),
    serial: buf.subarray(12, 18).toString('hex'),
    issuedAt: issued * 1000,
    endsAt: ends === PERMANENT ? 0 : ends * 1000,
    permanent: ends === PERMANENT,
    kid: buf.subarray(26, 34).toString('hex'),
    body: buf.subarray(0, BODY_LEN),
    sig: buf.subarray(BODY_LEN, TOTAL_LEN),
    version: 1,
    account: Buffer.alloc(4),
  };
}

function parse2(code) {
  const buf = b32decode(code, TOTAL2);
  if (!buf || buf[0] !== V2) return null;
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
    endsAt: endDay === PERMANENT2 ? 0 : (EPOCH_DAY + endDay) * DAY,
    permanent: endDay === PERMANENT2,
    kid: '',
    body: buf.subarray(0, BODY2),
    sig: buf.subarray(BODY2, TOTAL2),
    version: 2,
  };
}

/** کد را با کلیدِ همین سرور می‌سنجد ⇒ بخش‌هایش یا null. */
async function verify(code) {
  const p = parse(code);
  if (!p) return null;
  if (p.version === 2) {
    const ok2 = await license.verifyBytes(Buffer.concat([DOMAIN2, p.body]), p.sig);
    return ok2 ? p : null;
  }
  if (p.kid !== (await kidBytes()).toString('hex')) return null;
  const ok = await license.verifyBytes(Buffer.concat([DOMAIN, p.body]), p.sig);
  return ok ? p : null;
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
    redeemedStationId: row.redeemed_station_id || '',
    redeemedAt: row.redeemed_at ? Number(row.redeemed_at) : null,
    createdAt: Number(row.created_at),
    accountEmail: row.account_email || '',
    version: Number(row.version) || 1,
  };
}

/**
 * صدور. ⇒ { code, offline, file }
 * `days` برای استاندارد و وی‌آی‌پی (پیش‌فرض ۳۶۵، سقف ۳۶۵۰)؛ دائمی هیچ.
 */
async function issue({ plan, computer, account = '', days = null, note = '', createdBy = '', at = now() } = {}) {
  const P = PLANS[plan];
  if (!P) throw badRequest('پلن باید استاندارد، وی‌آی‌پی یا دائمی باشد', 'bad_plan');
  const pc = parseComputer(computer);
  if (!pc) throw badRequest('کدِ کامپیوتر درست نیست — ۱۶ نویسه، همان که برنامه نشان می‌دهد', 'bad_computer');

  //  «برای هر حساب متفاوت» — ایمیلِ حساب ⇒ شناسهٔ کاربر ⇒ ۴ بایتِ کد.
  //  خالی ⇒ هر حسابی (کامپیوتری که هرگز آنلاین نشده و حسابی ندارد).
  let user = null;
  const email = String(account || '').trim().toLowerCase();
  if (email) {
    user = await one('SELECT id, email FROM users WHERE lower(email)=$1', [email]);
    if (!user) throw notFound('حسابی با این ایمیل نیست', 'account_not_found');
  }

  const today = Math.floor(at / DAY) - EPOCH_DAY;
  let endDay = PERMANENT2;
  if (plan !== 'perm') {
    const d = days === null || days === undefined || days === '' ? P.defDays : Number(days);
    if (!Number.isInteger(d) || d < 1 || d > 3650) throw badRequest('روزها باید بینِ ۱ و ۳۶۵۰ باشد', 'bad_days');
    //  دستِ‌کم d روزِ کامل: پایان نیمه‌شبِ پس از روزِ d‌ام
    endDay = today + d + 1;
  }

  for (let attempt = 0; ; attempt++) {
    const serial = crypto.randomBytes(4);
    const body = body2({
      plan, machine: pc.machine, account: accountTag(user?.id), serial, issuedDay: today, endDay,
    });
    const code = await encode2(body, license.signBytes);
    try {
      const row = await one(
        `INSERT INTO pump_offline_codes (id, serial, plan, computer, issued_at, ends_at, note, created_by, created_at,
                                         account_user_id, account_email, version)
         VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,2) RETURNING *`,
        [newId('ofc'), serial.toString('hex'), plan, pc.text, (EPOCH_DAY + today) * DAY,
          endDay === PERMANENT2 ? 0 : (EPOCH_DAY + endDay) * DAY, String(note || '').slice(0, 300), createdBy, at,
          user?.id || '', user?.email || '']
      );
      const offline = shape(row);
      return { code, offline, file: fileOf(code, offline) };
    } catch (err) {
      //  سریالِ تکراری (یک در چهار میلیارد) ⇒ سریالِ تازه
      if (attempt < 3 && /unique|duplicate/i.test(String(err.message))) continue;
      throw err;
    }
  }
}

/** محتوای فایلِ ‎.pumpkey‎ — برنامه فقط `code` را باور می‌کند؛ بقیه برای چشم است. */
function fileOf(code, o) {
  return {
    format: 'pumpyaqobi-offline-key',
    v: 2,
    code,
    plan: o.plan,
    planTitle: o.planTitle,
    computer: o.computer,
    account: o.accountEmail,
    issuedAt: o.issuedAt,
    endsAt: o.endsAt,
    permanent: o.permanent,
    note: o.note,
  };
}

async function list({ limit = 100 } = {}) {
  const rows = await many(
    `SELECT * FROM pump_offline_codes ORDER BY created_at DESC LIMIT $1`,
    [Math.min(Math.max(Number(limit) || 100, 1), 500)]
  );
  return rows.map(shape);
}

async function revoke(id) {
  const row = await one(
    `UPDATE pump_offline_codes SET status='revoked', revoked_at=$2
      WHERE id=$1 RETURNING *`, [id, now()]);
  if (!row) throw notFound('این کد پیدا نشد', 'not_found');
  return shape(row);
}

/**
 * قابلیت‌هایی که این کد **همین حالا** می‌دهد — از فهرستِ خودِ پلن روی همین
 * سرور (همان که ‎subs.grant‎ برمی‌دارد)؛ پلنِ بی‌فهرست ⇒ پلنِ کامل. دائمی
 * خدماتِ سرور را فقط سالِ اولِ پس از صدورِ کد دارد.
 */
async function planKeysOf(p, at) {
  const plan = await require('./plans').getPlan(p.plan, 'pump');
  let keys = plan && Array.isArray(plan.features) && plan.features.length
    ? plan.features : require('./features').catalogOf('pump').PAID_KEYS;
  if (p.plan === 'perm' && require('./plans').endOfPeriod(p.issuedAt, 1, 'year') <= at) {
    keys = require('./pump-services').stripOnline(keys);
  }
  return keys;
}

/**
 * برنامه آنلاین شد و کد را آورد ⇒ اشتراک روی همان پمپ.
 *
 * ⛔ کدِ باطل‌شده ⇒ ۴۱۰ (برنامه آن را کنار می‌گذارد). کدِ پمپِ دیگر ⇒ ۴۰۹.
 * ⛔ کد اشتراکِ **بلندترِ** موجود را کوتاه نمی‌کند: فقط وقتی پایانِ کد از
 *    پایانِ اشتراکِ زنده دیرتر است، اشتراک عوض می‌شود.
 * ⚠️ دوباره آوردنِ همان کد روی همان پمپ بی‌خطر است (همان جواب).
 */
async function redeem({ stationId, deviceUid = '', code, computer, at = now() }) {
  const p = await verify(code);
  if (!p) throw badRequest('این کدِ اشتراک معتبر نیست', 'bad_offline_code');
  const pc = parseComputer(computer);
  if (!pc || !pc.machine.subarray(0, p.machine.length).equals(p.machine)) {
    throw badRequest('این کد برای کامپیوترِ دیگری ساخته شده است', 'computer_mismatch');
  }
  //  ⛔ کدِ بسته به حساب فقط روی پمپی که آن حساب عضوش است
  if (p.account.some(b => b !== 0)) {
    const members = await many(
      `SELECT user_id FROM station_members WHERE station_id=$1 AND status='active'
       UNION SELECT owner_user_id FROM stations WHERE id=$1`, [stationId]);
    if (!members.some(m => accountTag(m.user_id || m.owner_user_id).equals(p.account))) {
      throw conflict('این کد برای حسابِ دیگری ساخته شده است', 'account_mismatch');
    }
  }

  const claim = await tx(async (c) => {
    let row = (await c.query('SELECT * FROM pump_offline_codes WHERE serial=$1 FOR UPDATE', [p.serial])).rows[0];
    if (!row) {
      //  کدِ امضاشدهٔ خودمان که ردیفش نیست (دیتابیسِ بازگردانده‌شده) ⇒ ثبت
      row = (await c.query(
        `INSERT INTO pump_offline_codes (id, serial, plan, computer, issued_at, ends_at, note, created_by, created_at, version)
         VALUES ($1,$2,$3,$4,$5,$6,'',$7,$8,$9) RETURNING *`,
        [newId('ofc'), p.serial, p.plan, pc.text, p.issuedAt, p.endsAt, 'redeem', at, p.version]
      )).rows[0];
    }
    if (row.status === 'revoked') return { row, revoked: true };
    if (row.redeemed_station_id && row.redeemed_station_id !== stationId) return { row, elsewhere: true };
    const first = !row.redeemed_station_id;
    if (first) {
      row = (await c.query(
        `UPDATE pump_offline_codes SET redeemed_station_id=$2, redeemed_device_uid=$3, redeemed_at=$4
          WHERE id=$1 RETURNING *`, [row.id, stationId, String(deviceUid || '').slice(0, 120), at]
      )).rows[0];
    }
    return { row, first };
  });

  if (claim.revoked) throw new ApiError(410, 'code_revoked', 'این کدِ اشتراک باطل شده است');
  if (claim.elsewhere) throw conflict('این کد پیش از این روی پمپِ دیگری ثبت شده است', 'code_used_elsewhere');

  const endMs = p.permanent ? p.issuedAt + 50 * 365 * DAY : p.endsAt;
  if (endMs <= at) {
    return { status: 'expired', offline: shape(claim.row), granted: false };
  }

  const live = await one(
    `SELECT * FROM station_subscriptions WHERE station_id=$1 AND status IN ('active','suspended','pending')
      ORDER BY created_at DESC LIMIT 1`, [stationId]);
  /*
   *  ⛔ «covered» یعنی «همین حالا هر چیزی را که این کد می‌دهد دارد» — نه فقط
   *  «پایانش دیرتر است» (۱۴۰۵/۰۷/۲۱). گزارشِ صاحب سامانه: برنامه از کدِ
   *  بی‌اینترنت «VIP · ۳۵۷ روز» می‌گفت و همگام‌سازی از همین سرور
   *  ‎403 plan_no_services‎ می‌گرفت: اشتراکِ زندهٔ **استاندارد** (یا دائمیِ
   *  خدمات‌تمام‌شده) پایانِ دیرتری داشت، پس کدِ وی‌آی‌پی «covered» خوانده
   *  می‌شد و خدماتِ سرور هیچ‌وقت نمی‌نشست — و برنامه هم دیگر نمی‌پرسید.
   */
  let missing = [];
  if (live && live.status === 'active') {
    const want = await planKeysOf(p, at);
    const ent = await require('./entitlement').pump.entitlementOf(stationId, at);
    missing = want.filter(k => !ent.features.includes(k));
    const svc = require('./pump-services');
    //  دائمی: جز خدماتِ سرور چیزی کم نیست ⇒ خدمات تا پایانِ کد (اگر دیرتر از
    //  پایانِ فعلیِ خدمات است)، و دائمی دائمی می‌ماند — هرگز به یک‌ساله پایین نمی‌آید
    if (svc.isPermanent(live, at) && missing.every(k => svc.ONLINE_KEYS.includes(k))) {
      const until = p.plan === 'perm' ? require('./plans').endOfPeriod(p.issuedAt, 1, 'year') : endMs;
      if (svc.hasOnline(want) && until > at && until > svc.servicesUntil(live, at)) {
        await svc.extendServices(live.id, { until }, 'offline-code', `کدِ آفلاین ${p.serial}`);
        return { status: 'services', offline: shape(claim.row), granted: true };
      }
      return { status: 'covered', offline: shape(claim.row), granted: false };
    }
    if (!missing.length && (Number(live.ends_at) >= endMs || svc.isPermanent(live, at))) {
      return { status: 'covered', offline: shape(claim.row), granted: false };
    }
  }

  //  ⛔ کد اشتراکِ بلندترِ موجود را کوتاه نمی‌کند: پلنِ پایین‌ترِ بلندتر (استاندارد
  //  تا دو سال) به پلنِ کد بالا می‌رود و همان پایانِ دیرتر را نگه می‌دارد.
  const liveEnd = live && live.status === 'active' ? Number(live.ends_at) : 0;
  const endsAt = missing.length && liveEnd > endMs ? liveEnd : endMs;

  try {
    await subs.grant(stationId, {
      plan: p.plan,
      endsAt,
      note: `کدِ آفلاین ${p.serial}`,
      createdBy: 'offline-code',
    });
  } catch (err) {
    //  ثبت نشد (مثلاً اشتراکِ معلق) ⇒ کد آزاد می‌ماند تا بعد دوباره بیاید
    if (claim.first) {
      await query(`UPDATE pump_offline_codes SET redeemed_station_id=NULL, redeemed_device_uid='', redeemed_at=NULL
                    WHERE id=$1`, [claim.row.id]);
    }
    throw err;
  }
  return { status: 'granted', offline: shape(claim.row), granted: true };
}

module.exports = {
  issue, redeem, verify, parse, list, revoke, shape, fileOf, body2, encode2, accountTag,
  parseComputer, computerOfFingerprint, computerOfBytes, b32encode, b32decode,
  PLANS, ALPHABET, TOTAL_LEN, BODY_LEN, PERMANENT, TOTAL2, BODY2, EPOCH_DAY, PERMANENT2,
};
