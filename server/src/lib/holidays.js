'use strict';
/**
 * LOC-04 — جدولِ تعطیلاتِ رسمی از سرور (§۶.۲۳).
 *
 * تعطیلاتِ قمری (عید فطر، عید قربان، …) با رؤیتِ ماه اعلام می‌شوند و از پیش
 * حساب‌شدنی نیستند؛ پس هیچ برنامه‌ای جدول را ثابت در خود ندارد
 * (`WeekRules.holidays` در `core/calendar/BusinessDay.kt` و `rules.holidays`
 * در `license/calendar-core.js` همین جدول را می‌گیرند). مدیرِ ارشد جدول را
 * در سرور می‌گذارد و هر کسی آن را از `GET /api/calendar/holidays` می‌خواند.
 *
 * جدول کوچک است (چند ده روز در سال) و یک‌جا عوض می‌شود، پس یک سندِ JSON در
 * `app_config` (`calendar_holidays`) است، نه جدولِ تازه با مهاجرت:
 *   { version, updatedAt, holidays: [{ date: 'YYYY-MM-DD', name: { fa, en, ps } }] }
 * نوشتن فقط با `version` ِ فعلی است (قفلِ خوش‌بینانه زیرِ قفلِ تراکنشیِ همین کلید)، تا دو
 * مدیر جدولِ هم را بی‌صدا رونویسی نکنند. `calendar_holidays` در فهرستِ سفیدِ
 * `PATCH /api/admin/config` نیست، پس راهِ دیگری برای نوشتنِ بی‌سنجش ندارد.
 */
const { one, tx, now } = require('../db');
const { badRequest, conflict } = require('../middleware/errors');
const cal = require('./calendar-core');
const audit = require('./audit');

const KEY = 'calendar_holidays';
const MAX_ENTRIES = 500;
const MAX_NAME = 80;
const LANGS = ['fa', 'en', 'ps'];

/**
 * تاریخِ میلادیِ واقعی در بازهٔ ۲۰۰۰ تا ۲۱۹۹. `jdnOfIso` سالِ «۱۴۰۵» را هم
 * میلادیِ درست می‌داند؛ این مرز تاریخِ خورشیدیِ اشتباهاً فرستاده را رد می‌کند.
 */
function isDate(iso) {
  return typeof iso === 'string' && cal.jdnOfIso(iso) !== null && iso >= '2000-01-01' && iso <= '2199-12-31';
}

const EMPTY = Object.freeze({ version: 0, updatedAt: 0, holidays: Object.freeze([]) });

/**
 * سندِ ذخیره‌شده ⇒ سندِ پاک. سندِ خراب (نسخهٔ نه‌صحیح/منفی، `holidays` ِ نافهرست) مثلِ «هنوز جدولی نیست» خوانده
 * می‌شود، نه خطا برای خواننده؛ ردیفِ خراب (نوشتهٔ دستیِ پایگاه، بازگردانی) کنار می‌رود و بقیه می‌مانند — همان
 * قاعدهٔ `core/calendar/Holidays.parse` ِ برنامه‌ها، با بردارِ مشترکِ `core/test-vectors/calendar-holidays-v1.json`.
 */
function parse(raw) {
  if (!raw) return EMPTY;
  let d;
  try { d = JSON.parse(raw); } catch { return EMPTY; }
  if (!d || typeof d !== 'object' || !Array.isArray(d.holidays) || !Number.isInteger(d.version) || d.version < 0) return EMPTY;
  const seen = new Set();
  const holidays = [];
  for (const h of d.holidays.slice(0, MAX_ENTRIES)) {
    if (!h || typeof h !== 'object' || typeof h.date !== 'string' || !isDate(h.date) || seen.has(h.date)) continue;
    seen.add(h.date);
    const src = h.name && typeof h.name === 'object' ? h.name : {};
    const name = {};
    for (const lang of LANGS) if (typeof src[lang] === 'string' && src[lang]) name[lang] = src[lang].slice(0, MAX_NAME);
    holidays.push({ date: h.date, name });
  }
  return { version: d.version, updatedAt: Number(d.updatedAt) || 0, holidays };
}

function cleanName(v, lang) {
  if (v === undefined || v === null || v === '') return '';
  if (typeof v !== 'string') throw badRequest(`نامِ ${lang} باید متن باشد`, 'holiday_invalid');
  // eslint-disable-next-line no-control-regex
  const s = v.replace(/[\u0000-\u001f\u007f]/g, '').trim();
  if (s.length > MAX_NAME) throw badRequest(`نامِ ${lang} بلندتر از ${MAX_NAME} نویسه است`, 'holiday_invalid');
  return s;
}

/**
 * سنجشِ کاملِ جدولِ ورودی ⇒ جدولِ پاک و مرتب. هر ردیفِ نادرست کلِ درخواست را
 * رد می‌کند (نه نادیده گرفتنِ بی‌صدا): تاریخِ واقعیِ میلادی، نامِ دری لازم،
 * بی تاریخِ تکراری.
 */
function validate(list) {
  if (!Array.isArray(list)) throw badRequest('holidays باید فهرست باشد', 'holiday_invalid');
  if (list.length > MAX_ENTRIES) throw badRequest(`بیش از ${MAX_ENTRIES} روز`, 'holiday_invalid');
  const seen = new Set();
  const out = list.map((h, i) => {
    if (!h || typeof h !== 'object') throw badRequest(`ردیفِ ${i + 1} نادرست است`, 'holiday_invalid');
    const date = typeof h.date === 'string' ? h.date.trim() : '';
    if (!isDate(date)) throw badRequest(`ردیفِ ${i + 1}: تاریخِ «${String(h.date).slice(0, 20)}» درست نیست (میلادیِ YYYY-MM-DD، ۲۰۰۰ تا ۲۱۹۹)`, 'holiday_invalid');
    if (seen.has(date)) throw badRequest(`تاریخِ ${date} دو بار آمده است`, 'holiday_invalid');
    seen.add(date);
    const src = h.name && typeof h.name === 'object' ? h.name : {};
    const name = {};
    for (const lang of LANGS) {
      const s = cleanName(src[lang], lang);
      if (s) name[lang] = s;
    }
    if (!name.fa) throw badRequest(`ردیفِ ${i + 1}: نامِ دری لازم است`, 'holiday_invalid');
    return { date, name };
  });
  return out.sort((a, b) => (a.date < b.date ? -1 : a.date > b.date ? 1 : 0));
}

async function get() {
  const r = await one('SELECT value FROM app_config WHERE key=$1', [KEY]);
  return parse(r && r.value);
}

/** فقط روزهای میانِ `from` و `to` (هر دو اختیاری، شاملِ خودشان؛ `from` پس از `to` ⇒ ۴۰۰). */
function range(doc, { from = '', to = '' } = {}) {
  for (const [field, iso] of [['from', from], ['to', to]]) {
    if (iso && !isDate(iso)) throw badRequest(`${field} باید تاریخِ YYYY-MM-DD باشد`, 'holiday_invalid');
  }
  if (from && to && from > to) throw badRequest('from پس از to است', 'holiday_invalid');
  return doc.holidays.filter((h) => (!from || h.date >= from) && (!to || h.date <= to));
}

const same = (a, b) => JSON.stringify(a) === JSON.stringify(b);

/**
 * جایگزینیِ کلِ جدول. `expectedVersion` باید همان نسخهٔ فعلی باشد؛ وگرنه ۴۰۹ و هیچ چیزی نوشته نمی‌شود. ⇒ سندِ تازه
 * با `unchanged`.
 *
 *  • یک‌باره: جدولی که با جدولِ فعلی یکی است (پس از سنجش و مرتب‌سازی) هیچ چیزی نمی‌نویسد و همان سند را با
 *    `unchanged: true` برمی‌گرداند — با هر نسخه‌ای. تکرارِ درخواستی که پاسخش گم شد (برنامه هنوز نسخهٔ کهنه را دارد)
 *    ۴۰۹ ِ دروغ نمی‌گیرد، نسخه بالا نمی‌رود (ETag ِ برنامه‌ها همان) و سابقهٔ دومی نمی‌نشیند. جدولِ دیگر با نسخهٔ کهنه
 *    همچنان ۴۰۹ است، پس هیچ نوشته‌ای بی‌صدا رونویسی نمی‌شود.
 *  • ⛔ قفلِ تراکنشیِ همین کلید پیش از خواندن: `FOR UPDATE` روی ردیفی که هنوز نیست چیزی را قفل نمی‌کند و دو نخستین
 *    نوشتنِ هم‌زمان هر دو «نسخهٔ ۱» می‌شدند (فقط PostgreSQL؛ PGlite یک اتصال است).
 *  • سابقهٔ `admin.holidays_updated` (دلیل، شمارِ روزها، افزوده و برداشته) در **همان تراکنش**: جدولی بی سابقه نمی‌ماند.
 *
 * `meta` = { userId, reason, ip } (دلیل را در‌ِ مسیر با `requireReason` می‌سنجد).
 */
async function replace(list, expectedVersion, meta = {}) {
  const holidays = validate(list);
  if (!Number.isInteger(expectedVersion) || expectedVersion < 0) {
    throw badRequest('version (نسخهٔ فعلیِ جدول) لازم است', 'holiday_invalid');
  }
  return tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', [`app-config:${KEY}`]);
    const r = await c.query('SELECT value FROM app_config WHERE key=$1 FOR UPDATE', [KEY]);
    const cur = parse(r.rows[0] && r.rows[0].value);
    if (same(cur.holidays, holidays)) return { ...cur, unchanged: true };
    if (cur.version !== expectedVersion) {
      throw conflict('جدولِ تعطیلات در این میان عوض شده است؛ دوباره بخوانید و بنویسید', 'holiday_version');
    }
    const at = now();
    const doc = { version: cur.version + 1, updatedAt: at, holidays };
    await c.query(
      `INSERT INTO app_config (key,value,updated_at) VALUES ($1,$2,$3)
       ON CONFLICT (key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at`,
      [KEY, JSON.stringify(doc), at],
    );
    const before = new Set(cur.holidays.map((h) => h.date));
    const after = new Set(holidays.map((h) => h.date));
    await audit.log({
      actorType: 'admin', userId: meta.userId || '', action: 'admin.holidays_updated', ip: meta.ip || '',
      targetType: 'app_config', targetId: KEY,
      detail: {
        version: doc.version, count: holidays.length, reason: meta.reason || '',
        added: holidays.filter((h) => !before.has(h.date)).length,
        removed: cur.holidays.filter((h) => !after.has(h.date)).length,
      },
    }, { client: c });
    return { ...doc, unchanged: false };
  });
}

module.exports = { KEY, MAX_ENTRIES, get, range, validate, replace, parse };
