#!/usr/bin/env node
'use strict';
/**
 * پشتیبان و بازگردانیِ جدای پیام و رسانهٔ چتِ مشتری ↔ دکان (CHAT-12؛ شرح در
 * ‎src/lib/chat-backup.js‎). پشتیبانِ اصلیِ سرور (`npm run backup`) همچنان همه‌چیز
 * را با هم دارد؛ این ابزار برای جدا نگه داشتن و جدا برگرداندنِ همین دو بخش است.
 *
 *   node scripts/chat-backup.js export [--dir <پوشه>] [--shop <شناسهٔ دکان>]
 *   node scripts/chat-backup.js restore-messages <فایلِ chat-messages-…>
 *   node scripts/chat-backup.js restore-media <فایلِ chat-media-…>
 *   node scripts/chat-backup.js verify [--shop <شناسهٔ دکان>]
 *
 * ترتیبِ بازگردانی: پشتیبانِ اصلی (دکان‌ها) ⇐ پیام ⇐ رسانه ⇐ verify.
 * verify ِ ناسالم ⇒ کدِ خروجِ ۲.
 */
const chatBackup = require('../src/lib/chat-backup');
const { closeDb } = require('../src/db');

const [cmd, ...rest] = process.argv.slice(2);
const opt = (name) => {
  const i = rest.indexOf(`--${name}`);
  return i >= 0 ? rest[i + 1] : null;
};
const usage = 'کاربرد: node scripts/chat-backup.js export|restore-messages <فایل>|restore-media <فایل>|verify [--dir پوشه] [--shop شناسه]';

async function main() {
  if (cmd === 'export') {
    const out = await chatBackup.exportChat({ ...(opt('dir') ? { dir: opt('dir') } : {}), shopId: opt('shop') });
    console.log(`پیام: ${out.messages.file} (${Math.round(out.messages.bytes / 1024)} کیلوبایت)`);
    console.log(`رسانه: ${out.media.file} (${Math.round(out.media.bytes / 1024)} کیلوبایت)${out.encrypted ? ' — رمزشده' : ''}`);
    console.log(JSON.stringify(out.counts));
    return 0;
  }
  if (cmd === 'restore-messages' && rest[0]) {
    console.log(JSON.stringify(await chatBackup.restoreMessages(rest[0])));
    return 0;
  }
  if (cmd === 'restore-media' && rest[0]) {
    const r = await chatBackup.restoreMedia(rest[0]);
    console.log(JSON.stringify({ restored: r.restored, existing: r.existing, corrupt: r.corrupt.length, noConversation: r.noConversation.length }));
    return r.corrupt.length || r.noConversation.length ? 2 : 0;
  }
  if (cmd === 'verify') {
    const v = await chatBackup.verify({ shopId: opt('shop') });
    console.log(JSON.stringify({ ok: v.ok, messages: v.messages, media: v.media, references: v.references,
      missing: v.missing.length, mismatched: v.mismatched.length, corrupt: v.corrupt.length }));
    return v.ok ? 0 : 2;
  }
  console.error(usage);
  return 1;
}

main()
  .then(async (code) => { await closeDb(); process.exit(code); })
  .catch(async (e) => { console.error('✖', e.message); await closeDb(); process.exit(1); });
