'use strict';
/**
 * مدیریت زمان و منطقه زمانی.
 *
 * قاعده: همه‌ی زمان‌ها در دیتابیس و داخل License به صورت epoch میلی‌ثانیه (UTC)
 * ذخیره می‌شوند. منطقه زمانی فقط برای «تفسیر تاریخ‌هایی که مدیر وارد می‌کند»
 * و «نمایش» به کار می‌رود. این کار باعث می‌شود تغییر منطقه زمانی دستگاه کاربر
 * هیچ اثری روی اعتبار اشتراک نداشته باشد.
 */

/** آفست منطقه زمانی (میلی‌ثانیه) در یک لحظه‌ی مشخص. */
function tzOffsetMs(utcMs, timeZone) {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone, hour12: false,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
  const parts = dtf.formatToParts(new Date(utcMs));
  const m = {};
  for (const p of parts) m[p.type] = p.value;
  // hour می‌تواند در بعضی محیط‌ها «24» برگردد؛ به ۰ نگاشت می‌شود
  const asIfUtc = Date.UTC(+m.year, +m.month - 1, +m.day, (+m.hour) % 24, +m.minute, +m.second);
  return asIfUtc - utcMs;
}

/**
 * یک تاریخ/ساعت «محلی» را در منطقه زمانی داده‌شده به epoch UTC تبدیل می‌کند.
 * دو بار محاسبه می‌شود تا لبه‌های تغییر ساعت تابستانی هم درست دربیایند.
 * (افغانستان DST ندارد، ولی این تابع عمومی نوشته شده است.)
 */
function zonedToUtcMs({ year, month, day, hour = 0, minute = 0, second = 0 }, timeZone) {
  const naive = Date.UTC(year, month - 1, day, hour, minute, second);
  let utc = naive - tzOffsetMs(naive, timeZone);
  utc = naive - tzOffsetMs(utc, timeZone);
  return utc;
}

/** اجزای تاریخ محلی یک لحظه در منطقه زمانی داده‌شده. */
function utcToZonedParts(utcMs, timeZone) {
  const dtf = new Intl.DateTimeFormat('en-US', {
    timeZone, hour12: false,
    year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit',
  });
  const m = {};
  for (const p of dtf.formatToParts(new Date(utcMs))) m[p.type] = p.value;
  return {
    year: +m.year, month: +m.month, day: +m.day,
    hour: (+m.hour) % 24, minute: +m.minute, second: +m.second,
  };
}

/** "2026-08-24" → اجزای تاریخ. در صورت نامعتبر بودن null. */
function parseDateOnly(s) {
  if (typeof s !== 'string') return null;
  const m = /^(\d{4})-(\d{2})-(\d{2})$/.exec(s.trim());
  if (!m) return null;
  const year = +m[1], month = +m[2], day = +m[3];
  if (month < 1 || month > 12 || day < 1 || day > 31) return null;
  // روزهای نامعتبر مثل ۳۱ فبروری را رد می‌کند
  const probe = new Date(Date.UTC(year, month - 1, day));
  if (probe.getUTCMonth() !== month - 1 || probe.getUTCDate() !== day) return null;
  return { year, month, day };
}

const UNIT_ALIASES = {
  day: 'day', days: 'day', d: 'day',
  week: 'week', weeks: 'week', w: 'week',
  month: 'month', months: 'month', m: 'month',
  year: 'year', years: 'year', y: 'year',
};

/**
 * افزودن مدت به یک لحظه، با تقویم منطقه زمانی.
 * روز/هفته حسابی ساده نیست: با تقویم محلی جمع می‌شود تا تغییر ساعت تابستانی
 * باعث جابه‌جایی یک ساعته‌ی زمان پایان نشود.
 * ماه/سال سرریز را مهار می‌کند: ۳۱ جنوری + ۱ ماه = ۲۸/۲۹ فبروری، نه ۳ مارچ.
 */
function addDuration(fromUtcMs, amount, unit, timeZone) {
  const u = UNIT_ALIASES[String(unit || '').toLowerCase()];
  if (!u) throw new Error('واحد مدت نامعتبر است: ' + unit);
  const n = Number(amount);
  if (!Number.isInteger(n) || n === 0) throw new Error('مقدار مدت باید عدد صحیح غیرصفر باشد');

  const p = utcToZonedParts(fromUtcMs, timeZone);
  let { year, month, day } = p;

  if (u === 'day' || u === 'week') {
    day += (u === 'week' ? n * 7 : n);
  } else if (u === 'month' || u === 'year') {
    const addMonths = (u === 'year' ? n * 12 : n);
    const total = (year * 12 + (month - 1)) + addMonths;
    year = Math.floor(total / 12);
    month = (total % 12) + 1;
    const lastDay = new Date(Date.UTC(year, month, 0)).getUTCDate();
    if (day > lastDay) day = lastDay;
  }

  return zonedToUtcMs({ year, month, day, hour: p.hour, minute: p.minute, second: p.second }, timeZone);
}

/** ابتدای روز محلی (00:00:00) به epoch UTC. */
function startOfZonedDay(dateOnly, timeZone) {
  return zonedToUtcMs({ ...dateOnly, hour: 0, minute: 0, second: 0 }, timeZone);
}

/** انتهای روز محلی (23:59:59.999) به epoch UTC — تاریخ پایان شامل کل آن روز است. */
function endOfZonedDay(dateOnly, timeZone) {
  return zonedToUtcMs({ ...dateOnly, hour: 23, minute: 59, second: 59 }, timeZone) + 999;
}

function isValidTimeZone(tz) {
  if (typeof tz !== 'string' || !tz) return false;
  try { new Intl.DateTimeFormat('en-US', { timeZone: tz }); return true; }
  catch { return false; }
}

/** رشته‌ی خوانا برای نمایش/لاگ. */
function formatInZone(utcMs, timeZone) {
  const p = utcToZonedParts(utcMs, timeZone);
  const pad = (v) => String(v).padStart(2, '0');
  return `${p.year}-${pad(p.month)}-${pad(p.day)} ${pad(p.hour)}:${pad(p.minute)}:${pad(p.second)}`;
}

module.exports = {
  tzOffsetMs, zonedToUtcMs, utcToZonedParts, parseDateOnly,
  addDuration, startOfZonedDay, endOfZonedDay, isValidTimeZone, formatInZone,
};
