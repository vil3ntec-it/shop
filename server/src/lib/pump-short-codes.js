'use strict';
/**
 * 🔢 کدِ ۸ رقمیِ روزدارِ پمپ (۱۴۰۵/۰۷/۲۶)
 *
 * «کدش خیلی بزرگ نباشه در حد شش رقمی یا هشت رقمی… مشتری‌هایی که من میرم
 * پیش‌شون نت ندارن و آزمایشی رو نمی‌تونن بگیرن… مقدار روز که می‌خوام رو بزارم
 * و بعد با کد بدم به برنامه و دقیق کار کنه.»
 *
 * کد = جای‌گشتِ کلیددارِ (Feistelِ ۸ دوره روی ۲۸ بیت، HMAC-SHA256، cycle-walk
 * تا زیرِ ۱۰⁸) عددِ «روز(۹ بیت)·سریال(۵ بیت)·۱۲ بیتِ صفر». روز ۱…۳۶۵، مهلت از
 * روزِ زدن در برنامه، پلن وی‌آی‌پی، هر کد روی هر برنامه یک بار.
 *
 * ⚠️ صاحب سامانه آگاهانه پذیرفت: ۸ رقم جای امضا ندارد، پس همین راز داخلِ
 * برنامهٔ پمپ هم هست (‎ShortCode.cs‎) و دیکامپایل یعنی ساختنِ کد. کدِ بلندِ
 * امضاشده (offline-codes.js) برای اشتراکِ بزرگ همچنان هست.
 *
 * ⛔ الگوریتم مو‌به‌مو همان ‎native/PumpYaqobi.Shell/Services/ShortCode.cs‎ است.
 */
const crypto = require('crypto');
const { one, many, newId, now } = require('../db');
const { badRequest } = require('../middleware/errors');

const KEY = Buffer.from('7a77d602c8751811aad146e49b754a29886d51b0fef3d880ef84d8ed9cfb3dd6', 'hex');
const SPACE = 100000000;
const ROUNDS = 8;
const MAX_DAYS = 365;

function F(round, half) {
  const msg = Buffer.from([0x50, 0x59, 0x53, 0x43, 0x31, round, (half >> 8) & 0xff, half & 0xff]); // "PYSC1"
  const mac = crypto.createHmac('sha256', KEY).update(msg).digest();
  return ((mac[0] << 8) | mac[1]) & 0x3fff;
}

function enc1(x) {
  let l = x >> 14;
  let r = x & 0x3fff;
  for (let i = 0; i < ROUNDS; i++) {
    const nr = l ^ F(i, r);
    l = r;
    r = nr;
  }
  return (l << 14) | r;
}

function dec1(x) {
  let l = x >> 14;
  let r = x & 0x3fff;
  for (let i = ROUNDS - 1; i >= 0; i--) {
    const nl = r ^ F(i, l);
    r = l;
    l = nl;
  }
  return (l << 14) | r;
}

/** (روز، سریال ۰…۳۱) ⇒ «۸ رقم». */
function make(days, serial) {
  if (!Number.isInteger(days) || days < 1 || days > MAX_DAYS) throw new Error('days');
  let x = (days << 17) | ((serial & 0x1f) << 12);
  do x = enc1(x); while (x >= SPACE);
  return String(x).padStart(8, '0');
}

/** «۸ رقم» ⇒ {days, serial} یا null. */
function open(code) {
  const s = String(code || '').replace(/[\s-]/g, '');
  if (!/^\d{8}$/.test(s)) return null;
  let x = Number(s);
  do x = dec1(x); while (x >= SPACE);
  if ((x & 0xfff) !== 0 || x >> 26 !== 0) return null;
  const days = (x >> 17) & 0x1ff;
  if (days < 1 || days > MAX_DAYS) return null;
  return { days, serial: (x >> 12) & 0x1f };
}

const pretty = (c) => `${c.slice(0, 4)}-${c.slice(4)}`;

function shape(row) {
  return {
    id: row.id,
    code: pretty(row.code),
    days: Number(row.days),
    note: row.note,
    createdAt: Number(row.created_at),
  };
}

async function issue({ days, note = '', createdBy = '', at = now() } = {}) {
  const d = Number(days);
  if (!Number.isInteger(d) || d < 1 || d > MAX_DAYS) throw badRequest('روزها باید بینِ ۱ و ۳۶۵ باشد', 'bad_days');
  const code = make(d, crypto.randomInt(32));
  const row = await one(
    `INSERT INTO pump_short_codes (id, code, days, note, created_by, created_at)
     VALUES ($1,$2,$3,$4,$5,$6) RETURNING *`,
    [newId('psc'), code, d, String(note || '').slice(0, 300), createdBy, at]);
  return { short: shape(row) };
}

async function list({ limit = 100 } = {}) {
  const rows = await many('SELECT * FROM pump_short_codes ORDER BY created_at DESC LIMIT $1',
    [Math.min(Math.max(Number(limit) || 100, 1), 500)]);
  return rows.map(shape);
}

module.exports = { issue, list, make, open, pretty, MAX_DAYS };
