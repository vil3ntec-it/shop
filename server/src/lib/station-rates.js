'use strict';
/**
 * نرخِ اتحادیه از تلگرام ⇒ برنامهٔ کامپیوترِ **همان** پمپ.
 *
 * ── خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۶) ─────────────────────────────────
 * «نرخِ اتحادیه رو توی تلگرام بنویسم — پطرول ۴۵ دیزل ۹۹ — و اتومات توی
 *  برنامهٔ نیتیوِ کامپیوتر لایف آپدیت کنه… روی حساب‌های دیگهٔ کاربران
 *  تأثیری نذاره.»
 *
 *   بات ──create(stationId, …)──▶ station_rate_cmds (pending)
 *   برنامهٔ کامپیوتر ──GET /api/pump/device/rate──▶ همان یک فرمان
 *                    ──POST …/rate/:id/ack──▶ applied | rejected ⇒ بات می‌گوید
 *
 * ⛔ **هر فرمان کلیدِ یک پمپ را دارد و فقط با توکنِ دستگاهِ همان پمپ
 * خوانده می‌شود** (`req.stationId` از خودِ توکن). هیچ پمپِ دیگری آن را
 * نمی‌بیند و هیچ نرخی روی سرور «سراسری» نیست.
 *
 * ⛔ **سرور نرخ را حساب نمی‌کند و جایی نمی‌نویسد** — فقط پیام‌رسان است.
 * نرخ فقط وقتی «نشست» که خودِ برنامه گفت نشست.
 */
const { one, many, query, newId, now } = require('../db');

/** نرخِ معقولِ یک لیتر (افغانی). بیرون از این، غلطِ تایپی است، نه نرخ. */
const MIN_RATE = 10;
const MAX_RATE = 500;
/** فرمانی که یک روز کسی نگرفت کهنه است — برنامه روشن که شد، نرخِ دیروز را نمی‌نشاند. */
const STALE_MS = 24 * 3600 * 1000;
/**
 * «درجا»: برنامهٔ کامپیوتر پرسشِ `/rate` را باز نگه می‌دارد (`?wait=`) و سرور
 * همین که بات فرمانی ساخت جواب می‌دهد — نه دقیقهٔ بعد. سقفِ انتظار کمتر از
 * مهلتِ ۲۰ ثانیه‌ایِ HttpClientِ برنامه است.
 */
const WAIT_MAX_S = 15;
/**
 * ⛔ سقفِ پرسش‌های بازِ هم‌زمانِ **یک پمپ**. یک پمپ یک کامپیوتر است و یک پرسشِ باز
 * بس است؛ چهار برای وقتی که اتصالِ قبلی هنوز بسته نشده. بیشتر از آن ⇒ پاسخِ فوری،
 * تا کسی با یک توکنِ دستگاه نتواند صدها اتصال را روی سرور باز نگه دارد.
 */
const MAX_WAITERS = 4;

/** stationId ⇒ شنونده‌های منتظر. فقط در حافظه؛ سرورِ حساب یک پروسه است. */
const waiters = new Map();

function wake(stationId) {
  const set = waiters.get(stationId);
  if (!set) return;
  waiters.delete(stationId);
  for (const fn of set) { try { fn(); } catch { /* شنوندهٔ رفته */ } }
}

/**
 * تا فرمانی برای همین پمپ ساخته شود یا `ms` بگذرد. ‎{promise, cancel}‎ —
 * `cancel` برای وقتی که برنامه اتصال را بست، تا شنونده‌ای جا نماند.
 */
function waitFor(stationId, ms) {
  let done;
  let timer;
  const promise = new Promise((resolve) => {
    done = () => {
      clearTimeout(timer);
      const set = waiters.get(stationId);
      if (set) { set.delete(done); if (!set.size) waiters.delete(stationId); }
      resolve();
    };
    timer = setTimeout(done, Math.max(0, ms));
    if (!waiters.has(stationId)) waiters.set(stationId, new Set());
    waiters.get(stationId).add(done);
  });
  return { promise, cancel: () => done() };
}

/** شمارِ شنونده‌های منتظر — فقط برای سنجه‌ها. */
function waiting(stationId) {
  const set = waiters.get(stationId);
  return set ? set.size : 0;
}

const FA = '۰۱۲۳۴۵۶۷۸۹';
const AR = '٠١٢٣٤٥٦٧٨٩';

function ascii(s) {
  return String(s || '')
    .replace(/[۰-۹٠-٩]/g, (ch) => {
      const i = FA.indexOf(ch);
      return String(i >= 0 ? i : AR.indexOf(ch));
    })
    .replace(/[٫]/g, '.')       // ممیزِ فارسی
    .replace(/[٬،]/g, ',');
}

const PETROL = '(?:پطرول|پترول|بطرول|بنزین|petrol|benzin)';
const DIESEL = '(?:دیزل|دیزِل|دیزول|گازوئیل|diesel)';
const NUM = '(\\d{1,3}(?:\\.\\d{1,2})?)';

/**
 * «پطرول ۴۵ دیزل ۹۹» · «نرخ اتحادیه پطرول ۷۹ . دیزل ۸۰» · «نرخ جدید پطرول ۷۹ . دیزل»
 * ⇒ ‎{petrol, diesel}‎ (هر کدام عدد یا ‎null‎) — یا ‎null‎ اگر این پیامِ نرخ نیست.
 *
 * ⚠️ «پیامِ نرخ» یعنی دستِ‌کم یک تیل **با عددش**. نامِ تیلِ بی عدد (مثلِ
 * «دیزل» در مثالِ سوم) فقط نادیده گرفته می‌شود، نه صفر.
 * ⚠️ عددِ بیرون از ‎[10, 500]‎ یعنی غلطِ تایپی ⇒ ‎{bad: [...]}‎، تا بات بگوید
 * کدام، نه این‌که بی‌صدا چیزی بنشاند.
 */
function parse(raw) {
  const text = ascii(raw).toLowerCase();
  if (text.length > 160) return null;
  const grab = (word) => {
    const m = new RegExp(`${word}\\s*[:=\\-–]?\\s*${NUM}`, 'i').exec(text);
    return m ? Number(m[1]) : null;
  };
  const petrol = grab(PETROL);
  const diesel = grab(DIESEL);
  if (petrol === null && diesel === null) return null;
  const bad = [];
  const ok = (v, name) => {
    if (v === null) return null;
    if (!(v >= MIN_RATE && v <= MAX_RATE)) { bad.push(name); return null; }
    return v;
  };
  const out = { petrol: ok(petrol, 'petrol'), diesel: ok(diesel, 'diesel') };
  if (bad.length) out.bad = bad;
  return out;
}

function shape(r) {
  if (!r) return null;
  return {
    id: r.id,
    petrol: r.petrol === null || r.petrol === undefined ? null : Number(r.petrol),
    diesel: r.diesel === null || r.diesel === undefined ? null : Number(r.diesel),
    by: r.by_name || '',
    at: Number(r.created_at),
    status: r.status,
  };
}

/**
 * فرمانِ تازه برای **یک** پمپ. فرمان‌های در صفِ همان پمپ کنار می‌روند —
 * آخرین حرف مرجع است («۴۵» و بعد «۴۶» یعنی ۴۶، نه هر دو پشتِ سرِ هم).
 */
async function create(stationId, { petrol = null, diesel = null, chatId = '', by = '' } = {}) {
  if (petrol === null && diesel === null) return null;
  const t = now();
  await query(
    `UPDATE station_rate_cmds SET status='superseded', done_at=$2
      WHERE station_id=$1 AND status='pending'`, [stationId, t]);
  const row = shape(await one(
    `INSERT INTO station_rate_cmds (id, station_id, petrol, diesel, status, chat_id, by_name, created_at)
     VALUES ($1,$2,$3,$4,'pending',$5,$6,$7) RETURNING *`,
    [newId('rate'), stationId, petrol, diesel, String(chatId || ''), String(by || '').slice(0, 80), t]
  ));
  //  برنامهٔ منتظرِ همین پمپ همین حالا جواب می‌گیرد
  wake(stationId);
  return row;
}

/** فرمانِ در صفِ همین پمپ — یا ‎null‎. کهنه‌ها همین‌جا کنار می‌روند. */
async function pending(stationId) {
  const t = now();
  await query(
    `UPDATE station_rate_cmds SET status='superseded', done_at=$2, note='کهنه شد'
      WHERE station_id=$1 AND status='pending' AND created_at < $3`,
    [stationId, t, t - STALE_MS]);
  return shape(await one(
    `SELECT * FROM station_rate_cmds WHERE station_id=$1 AND status='pending'
      ORDER BY created_at DESC LIMIT 1`, [stationId]));
}

/**
 * «نشست» یا «ننشست» — فقط برای فرمانی که مالِ **همین** پمپ است و هنوز در
 * صف است. خروجی: ردیفِ کامل (برای خبر دادن به بات) یا ‎null‎.
 */
async function ack(stationId, id, { applied = true, note = '' } = {}) {
  const row = await one(
    `UPDATE station_rate_cmds SET status=$3, done_at=$4, note=$5
      WHERE id=$1 AND station_id=$2 AND status='pending' RETURNING *`,
    [String(id || ''), stationId, applied ? 'applied' : 'rejected', now(), String(note || '').slice(0, 200)]);
  return row || null;
}

async function recent(stationId, limit = 10) {
  return (await many(
    `SELECT * FROM station_rate_cmds WHERE station_id=$1 ORDER BY created_at DESC LIMIT $2`,
    [stationId, limit])).map(shape);
}

module.exports = { parse, create, pending, ack, recent, waitFor, waiting, MIN_RATE, MAX_RATE, STALE_MS, WAIT_MAX_S, MAX_WAITERS };
