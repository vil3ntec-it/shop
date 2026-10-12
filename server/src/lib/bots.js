'use strict';
/**
 * ════════════════════════════════════════════════════════════════════
 *  جای باتِ هر برنامه — باتِ هر برنامه در ریپوی خودِ همان برنامه است
 * ════════════════════════════════════════════════════════════════════
 *
 *  خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۲۰): «باتِ پمپ بنزین را بیاور توی ریپوی پمپ…
 *  به شاپ ربطی نده؛ بعداً برای شاپ بات جدا می‌سازم.»
 *
 *      باتِ پمپ  ⇐  ریپوی pump-staion-yaqobi، پوشهٔ telegram-bot/
 *                   هنگامِ ساختِ سرورِ حساب ⇐ server/bots/pump/   (یا PUMP_BOT_DIR)
 *
 *  ⛔ هیچ جای سرورِ حساب `lib/telegram` را مستقیم صدا نمی‌زند؛ فقط `bots.pump()`.
 *  ⛔ بات نیست (پوشه نیامده) ⇒ جانشینِ خاموش با همان نام‌ها: سرور بالا می‌آید،
 *     هشدارها و پیام‌ها فقط به تلگرام نمی‌روند، و پنل «بات نصب نیست» می‌بیند.
 *  ⚠️ بات همان‌جا اجرا می‌شود (همان پردازه، همان دیتابیس)؛ هر چه از سرورِ حساب
 *     می‌خواهد از `req` می‌گیرد — `require` از همین پوشهٔ `src/lib`.
 */
const fs = require('fs');
const path = require('path');

const req = (p) => require(path.join(__dirname, p));

function pumpDir() {
  const list = [process.env.PUMP_BOT_DIR, path.join(__dirname, '..', '..', 'bots', 'pump')].filter(Boolean);
  for (const dir of list) {
    try { if (fs.statSync(path.join(dir, 'index.js')).isFile()) return path.resolve(dir); } catch { /* نیست */ }
  }
  return null;
}

/** جانشینِ خاموش — همان نام‌هایی که سرورِ حساب صدا می‌زند. */
function missingPump() {
  const off = async () => ({ sent: false, why: 'no_bot' });
  return {
    available: false,
    PURPOSE: 'telegram',
    ALERT_KINDS: ['stock_out', 'low_stock', 'debt'],
    relayCustomer: off,
    rateDone: async () => {},
    notifyStation: async () => {},
    notifyResolved: async () => {},
    start() {},
    stop() {},
    reload() {},
    async status() {
      return {
        configured: false, missing: true, fromEnv: false, tokenHint: '', enabled: false, running: false,
        username: '', link: '', lastError: 'باتِ پمپ روی این سرور نصب نیست (پوشهٔ bots/pump)', lastOkAt: null,
        chats: { private: 0, groups: 0, channels: 0 }, outbox: { pending: 0, sent24h: 0, failed24h: 0 }, links: {},
      };
    },
    async configure() {
      const err = new Error('باتِ پمپ روی این سرور نصب نیست');
      err.code = 'bot_missing';
      throw err;
    },
    async publicInfo() { return { username: '', link: '' }; },
  };
}

let pump = null;
/** باتِ پمپ (یک نمونه برای همهٔ سرور). */
function pumpBot() {
  if (pump) return pump;
  const dir = pumpDir();
  if (!dir) {
    pump = missingPump();
    return pump;
  }
  pump = Object.assign(require(dir)({ req }), { available: true, dir });
  return pump;
}

module.exports = { pump: pumpBot, pumpDir };
