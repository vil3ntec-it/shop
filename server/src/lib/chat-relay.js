'use strict';
/**
 * ══ سرور فقط رله است — پیام و رسانه ۱۵ روز، بعد پاک ══════════════════════
 *
 * خواستهٔ صاحب سامانه: «همهٔ پیام‌ها و غیره بعد از ۱۵ روز بروند… سرور
 * همهٔ این‌ها را ساپورت کند و این‌ها روی سرور فضایی نگیرند، این‌ها توی
 * برنامه باشند.»
 *
 * برنامهٔ کامپیوترِ پمپ هر پیام و هر رسانه را خودش پایین می‌آورد و نگه
 * می‌دارد؛ پس این‌جا فقط جای **عبور** است، نه بایگانی.
 *
 *   station_chat_messages / station_chat_media   مشتریِ کیو‌آر ↔ صاحبِ پمپ
 *   support_messages (فقط رشته‌های app='pump')    صاحبِ پمپ ↔ مدیرِ سامانه
 *
 * قاعده‌ها:
 *   • ⛔ یک عدد، یک جا: `CHAT_RELAY_DAYS`. متغیرِ محیطیِ همنام فقط برای
 *     آزمون است.
 *   • ⛔ دنباله‌ها (`seq`) دست نمی‌خورند؛ پاک کردنِ ردیف شماره را از نو
 *     نمی‌کند، پس `after=<آخرین seq>`ِ برنامه همان‌طور کار می‌کند.
 *   • رسانه‌ای که هیچ پیامی به آن اشاره نمی‌کند و بیش از یک ساعت از
 *     آمدنش گذشته (بارگذاری‌ای که هرگز فرستاده نشد) هم می‌رود.
 *   • ردیفِ رشته‌ها (`station_chat_threads`، `support_threads`) می‌ماند:
 *     کوچک است و بلاک، «تا کجا خوانده شد» و شمارندهٔ نخوانده رویش است.
 *   • رسانهٔ رفته ⇒ ۴۰۴ با کدِ `media_gone`، نه ۵۰۰.
 */
const { query, now } = require('../db');

const CHAT_RELAY_DAYS = 15;
const DAY_MS = 24 * 3600 * 1000;
const ORPHAN_MEDIA_MS = 3600 * 1000;

/** روزهای نگه‌داری. محیط فقط برای آزمون؛ مقدارِ نامعتبر ⇒ همان ۱۵. */
function relayDays() {
  const raw = process.env.CHAT_RELAY_DAYS;
  if (raw === undefined || raw === '') return CHAT_RELAY_DAYS;
  const n = Number(raw);
  return Number.isFinite(n) && n >= 0 ? n : CHAT_RELAY_DAYS;
}

/**
 * پاک‌سازی. هیچ‌وقت چیزی را که هنوز در پنجرهٔ نگه‌داری است نمی‌برد.
 * خروجی: شمارِ ردیف‌های رفته از هر جدول.
 */
async function sweep({ at = now() } = {}) {
  const cutoff = at - relayDays() * DAY_MS;

  const msgs = await query('DELETE FROM station_chat_messages WHERE created_at < $1', [cutoff]);

  //  رسانهٔ پیام‌های رفته + بارگذاری‌های بی‌پیام (یتیم) — هر دو «بی‌ارجاع»
  const media = await query(
    `DELETE FROM station_chat_media m
      WHERE m.created_at < $1
        AND NOT EXISTS (SELECT 1 FROM station_chat_messages x WHERE x.media_id = m.id)`,
    [at - ORPHAN_MEDIA_MS]
  );

  const support = await query(
    `DELETE FROM support_messages
      WHERE created_at < $1
        AND thread_id IN (SELECT id FROM support_threads WHERE app = 'pump')`,
    [cutoff]
  );

  return { chatMessages: msgs.rowCount, chatMedia: media.rowCount, supportMessages: support.rowCount };
}

/** متنِ ۴۰۴ِ `media_gone` — یک جا، تا دو در دو جمله نگویند. */
const GONE_MESSAGE = 'رسانه منقضی شد — سرور پیام و رسانه را فقط چند روز نگه می‌دارد';

module.exports = { CHAT_RELAY_DAYS, relayDays, sweep, GONE_MESSAGE };
