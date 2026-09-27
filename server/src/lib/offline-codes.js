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
  };
}

/** کد را با کلیدِ همین سرور می‌سنجد ⇒ بخش‌هایش یا null. */
async function verify(code) {
  const p = parse(code);
  if (!p) return null;
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
  };
}

/**
 * صدور. ⇒ { code, offline, file }
 * `days` برای استاندارد و وی‌آی‌پی (پیش‌فرض ۳۶۵، سقف ۳۶۵۰)؛ دائمی هیچ.
 */
async function issue({ plan, computer, days = null, note = '', createdBy = '', at = now() } = {}) {
  const P = PLANS[plan];
  if (!P) throw badRequest('پلن باید استاندارد، وی‌آی‌پی یا دائمی باشد', 'bad_plan');
  const pc = parseComputer(computer);
  if (!pc) throw badRequest('کدِ کامپیوتر درست نیست — ۱۶ نویسه، همان که برنامه نشان می‌دهد', 'bad_computer');

  const issuedSec = Math.floor(at / 1000);
  let endsSec = PERMANENT;
  if (plan !== 'perm') {
    const d = days === null || days === undefined || days === '' ? P.defDays : Number(days);
    if (!Number.isInteger(d) || d < 1 || d > 3650) throw badRequest('روزها باید بینِ ۱ و ۳۶۵۰ باشد', 'bad_days');
    endsSec = issuedSec + d * 86400;
  }

  const body = Buffer.alloc(BODY_LEN);
  body[0] = VERSION;
  body[1] = P.byte;
  pc.machine.copy(body, 2);
  const serial = crypto.randomBytes(6);
  serial.copy(body, 12);
  body.writeUInt32BE(issuedSec, 18);
  body.writeUInt32BE(endsSec, 22);
  (await kidBytes()).copy(body, 26);
  const sig = await license.signBytes(Buffer.concat([DOMAIN, body]));
  if (sig.length !== 64) throw new Error('امضا ۶۴ بایت نیست');

  const code = group(b32encode(Buffer.concat([body, sig])), 5);
  const row = await one(
    `INSERT INTO pump_offline_codes (id, serial, plan, computer, issued_at, ends_at, note, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [newId('ofc'), serial.toString('hex'), plan, pc.text, issuedSec * 1000,
      endsSec === PERMANENT ? 0 : endsSec * 1000, String(note || '').slice(0, 300), createdBy, at]
  );
  const offline = shape(row);
  return { code, offline, file: fileOf(code, offline) };
}

/** محتوای فایلِ ‎.pumpkey‎ — برنامه فقط `code` را باور می‌کند؛ بقیه برای چشم است. */
function fileOf(code, o) {
  return {
    format: 'pumpyaqobi-offline-key',
    v: 1,
    code,
    plan: o.plan,
    planTitle: o.planTitle,
    computer: o.computer,
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
  if (!pc || !pc.machine.equals(p.machine)) {
    throw badRequest('این کد برای کامپیوترِ دیگری ساخته شده است', 'computer_mismatch');
  }

  const claim = await tx(async (c) => {
    let row = (await c.query('SELECT * FROM pump_offline_codes WHERE serial=$1 FOR UPDATE', [p.serial])).rows[0];
    if (!row) {
      //  کدِ امضاشدهٔ خودمان که ردیفش نیست (دیتابیسِ بازگردانده‌شده) ⇒ ثبت
      row = (await c.query(
        `INSERT INTO pump_offline_codes (id, serial, plan, computer, issued_at, ends_at, note, created_by, created_at)
         VALUES ($1,$2,$3,$4,$5,$6,'',$7,$8) RETURNING *`,
        [newId('ofc'), p.serial, p.plan, pc.text, p.issuedAt, p.endsAt, 'redeem', at]
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
  if (live && Number(live.ends_at) >= endMs && live.status === 'active') {
    return { status: 'covered', offline: shape(claim.row), granted: false };
  }

  try {
    await subs.grant(stationId, {
      plan: p.plan,
      endsAt: endMs,
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
  issue, redeem, verify, parse, list, revoke, shape, fileOf,
  parseComputer, computerOfFingerprint, computerOfBytes, b32encode, b32decode,
  PLANS, ALPHABET, TOTAL_LEN, BODY_LEN, PERMANENT,
};
