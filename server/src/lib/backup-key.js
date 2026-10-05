'use strict';
/**
 * ══ کلیدِ بکاپِ هر پمپ (۲.۱۱.۲۲) ═══════════════════════════════════════════
 *
 * خواستهٔ صاحب مخزن: «بکاپ روی کامپیوترِ جدید قابلِ بازیابی باشد، ولی حسابِ
 * جدید و آزمایشیِ دوباره راهی برای دیدنِ بکاپِ قبلی نشود… حتی آفلاین.»
 *
 * برنامهٔ پمپ هر بکاپ را با کلیدِ **همان پمپ** رمز می‌کند (AES-GCM). کلید
 * این‌جا ساخته می‌شود و فقط به کسی داده می‌شود که عضوِ همان پمپ است (توکنِ
 * دستگاهِ همان پمپ، یا حسابی که در همان پمپ عضو است). حسابِ تازه پمپِ تازه
 * دارد، پس کلیدِ پمپِ قبلی را هرگز نمی‌گیرد — و بکاپ برایش باز نمی‌شود.
 *
 * ⛔ **کلید ذخیره نمی‌شود**: HMACِ رازِ سرور روی شناسهٔ پمپ است — همیشه همان،
 * هیچ جدولی، و پشتیبانِ دیتابیس آن را لو نمی‌دهد. ⛔ رازِ سرور (`API_SECRET`)
 * هرگز عوض نمی‌شود (قاعدهٔ سرورِ خانگی)؛ عوض شدنش یعنی هیچ بکاپی باز نمی‌شود.
 */
const { createHmac } = require('node:crypto');
const config = require('../config');

const VERSION = 1;

function keyFor(stationId) {
  const secret = config.secrets.api || config.secrets.jwt;
  if (!secret || !stationId) return null;
  return createHmac('sha256', secret)
    .update(`pump-backup-key|v${VERSION}|${stationId}`)
    .digest('base64');
}

/** پاسخِ هر دو در — یک شکل. */
function reply(stationId) {
  const key = keyFor(stationId);
  if (!key) return null;
  return { ok: true, stationId, key, version: VERSION };
}

module.exports = { keyFor, reply, VERSION };
