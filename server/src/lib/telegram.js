'use strict';
/**
 * باتِ تلگرامِ پمپ — هشدارِ «حسابِ قرض‌دار» و «مخزن»، در تلگرام.
 *
 * ── خواستهٔ صاحب مخزن (۱۴۰۵/۰۷/۱۳) ──────────────────────────────────
 * «بات تلگرام رو روی سرور بساز و برای پمپ باشد و دیگه جای نشتی نکنه و از
 *  دکان هم اون رو ول کن… هر حساب کاربری که روی سرور است و حساب پمپ داره
 *  اون‌ها بتونن برن توی بات و اون‌جا استارت رو که زدن بگه حساب کاربری‌شونو
 *  وارد کنن… و سرور پیدا کنه و به اون حساب متصل بشه و وقتی داشت تیل یارو
 *  تمام می‌شد پیام هشدار تیل بره، قرض‌داری که حسابش تموم شده و مخزن اگه
 *  تیل تموم شد پیام هشدار مخزن بره درجا… دکمه برای برنامه اندروید و
 *  آیفون… و بتونه بات رو به گروه تلگرامش وصل کنه.»
 *
 * ── راه ───────────────────────────────────────────────────────────
 *
 *   کامپیوترِ پمپ ──POST /api/pump/device/events──▶ station_events
 *                                                  └──▶ telegram_outbox ──▶ تلگرام
 *   کاربر در تلگرام: /start ⇒ ایمیل ⇒ کدِ شش‌رقمی به **همان ایمیل** ⇒ وصل
 *
 * ⛔ **هیچ دادهٔ تازه‌ای ساخته نمی‌شود.** فهرستِ هشدار همان است که برنامهٔ
 * پمپ از قبل می‌فرستاد (`StationSnapshot.Alerts` ⇒ `CloudEvents`) و همان
 * سه نوعی که پوش می‌شوند (`events.PUSH_KINDS`). قاعدهٔ جدا بنویسید و روزی
 * کارتِ قرض‌دار سرخ است و تلگرام ساکت.
 *
 * ── «جای نشتی نکنه» — پنج قید ────────────────────────────────────
 *
 * 1. **ایمیل تنها کافی نیست.** کسی که فقط ایمیلِ دیگری را بداند نباید
 *    هشدارهای پمپِ او را بگیرد؛ پس کدِ شش‌رقمی به **همان ایمیل** می‌رود
 *    (همان `otp`ِ ثبت‌نام، با `purpose='telegram'`).
 * 2. **پاسخِ «این ایمیل حساب دارد؟» یکی است** — چه باشد چه نباشد. بات
 *    نباید دفترچهٔ ایمیل‌های مشتری‌ها شود.
 * 3. **عضویت در لحظهٔ فرستادن سنجیده می‌شود**، نه فقط هنگامِ وصل شدن:
 *    کارمندی که از پمپ بیرون شد یا حسابی که بسته شد، از همان لحظه هیچ
 *    هشداری نمی‌گیرد — در خصوصی و در گروهی که او وصل کرده بود.
 * 4. **گروه فقط با نشانیِ یک‌بارمصرفِ پانزده‌دقیقه‌ای** وصل می‌شود، که فقط
 *    از گفت‌وگوی خصوصیِ **وصل‌شده** ساخته می‌شود. خودِ نشانه ذخیره نمی‌شود،
 *    فقط هشش.
 * 5. **رمزِ بات فقط روی همین سرور است** (رمزشده در `app_config` یا در
 *    محیط) — نه در برنامهٔ کامپیوتر، نه در اپِ گوشی، نه در هیچ پاسخی.
 *
 * ⚠️ هیچ `parse_mode`ی به کار نمی‌رود: نامِ قرض‌دار را کاربر نوشته، و
 * متنِ ساده یعنی هیچ نشانه‌ای از آن نام نمی‌تواند پیام را دست‌کاری کند.
 *
 * ⚠️ «گرفتنِ پیام» با long polling است، نه webhook: سرور پشتِ تونلِ
 * خانگی است و webhook یک نشانیِ عمومیِ دیگر می‌خواست. فقط **یک** فرآیند
 * باید با یک رمز بگیرد؛ دومی از تلگرام ۴۰۹ می‌گیرد و خودش صبر می‌کند.
 */
const crypto = require('crypto');
const { query, one, many, newId, now } = require('../db');
const config = require('../config');
const plans = require('./plans');
const stations = require('./stations');

const CFG_TOKEN = 'telegram_token';        // رمزشده با کلیدِ پمپ‌ها
const CFG_ENABLED = 'telegram_enabled';    // '1' | '0'
const CFG_OFFSET = 'telegram_offset';      // شمارهٔ آخرین پیامِ گرفته‌شده + ۱
const CFG_USERNAME = 'telegram_username';  // نامِ بات، برای لینک‌ها — راز نیست

/** کدِ شش‌رقمیِ همین راه — جدا از ثبت‌نام، تا هیچ‌کدام کدِ دیگری را نپذیرد. */
const PURPOSE = 'telegram';

/** همان سه نوعی که پوش می‌شوند — `events.PUSH_KINDS`. یک فهرست، نه دو. */
const ALERT_KINDS = ['stock_out', 'low_stock', 'debt'];

const LINK_TTL_MS = 15 * 60 * 1000;
const CODE_WINDOW_MS = 60 * 60 * 1000;
const CODE_MAX_PER_WINDOW = 5;
const MAX_ATTEMPTS = 8;
const GIVE_UP_MS = 24 * 3600 * 1000;
const KEEP_MS = 7 * 24 * 3600 * 1000;
/**
 * ⛔ «محدودیت هم نداشته باشه» (۱۴۰۵/۰۷/۱۴): هیچ فهرستی بریده نمی‌شود —
 * نه هشدارها، نه نتیجهٔ جست‌وجو. تلگرام یک پیام را تا ۴۰۹۶ نویسه می‌پذیرد،
 * پس متنِ بلند به چند پیامِ پشتِ سرِ هم شکسته می‌شود (`chunks`)، همیشه سرِ
 * مرزِ یک بند یا یک خط — هیچ نامی وسطش بریده نمی‌شود.
 */
const CHUNK = 3800;

/** متنِ بلند ⇒ تکه‌های ≤ CHUNK، سرِ مرزِ بند (خطِ خالی) و بعد خط. */
function chunks(text, max = CHUNK) {
  const out = [];
  let cur = '';
  const push = (piece, sep) => {
    if (!cur) { cur = piece; return; }
    if (cur.length + sep.length + piece.length <= max) { cur += sep + piece; return; }
    out.push(cur); cur = piece;
  };
  for (const block of String(text).split('\n\n')) {
    if (block.length <= max) { push(block, '\n\n'); continue; }
    //  بندی که خودش بلند است: خط‌به‌خط
    let first = true;
    for (const line of block.split('\n')) {
      const safe = line.length > max ? line.slice(0, max) : line;
      push(safe, first ? '\n\n' : '\n');
      first = false;
    }
  }
  if (cur || !out.length) out.push(cur);
  return out;
}

/** چند پیامِ پشتِ سرِ هم برای یک متنِ بلند. */
async function sendLong(chatId, text) {
  let res = null;
  for (const part of chunks(text)) {
    res = await send(chatId, part);
    if (!res.ok) break;
  }
  return res;
}

/** یک متن ⇒ چند ردیفِ صف، به ترتیب (created_at پشتِ سرِ هم). */
async function queueLong(chatId, stationId, text, t) {
  const parts = chunks(text);
  for (let i = 0; i < parts.length; i++) {
    await query(
      `INSERT INTO telegram_outbox (id, chat_id, station_id, body, next_at, created_at)
       VALUES ($1,$2,$3,$4,$5,$6)`,
      [newId('tgo'), chatId, stationId, parts[i], t, t + i]
    );
  }
}

/* ══════════════════════════════════════════════════════════════════
   درگاهِ تلگرام
   ══════════════════════════════════════════════════════════════════ */

/**
 * ⚠️ **فقط آزمون مقدار می‌دهد** — همان الگوی `push.setDeliver`. تلگرام از
 * ماشینِ آزمون در دسترس نیست؛ بی این، هر سنجه‌ای یا شبکه می‌زد یا سبزِ
 * دروغ می‌داد.
 */
let transport = null;
function setTransport(fn) { transport = typeof fn === 'function' ? fn : null; }

const bot = {
  username: '',
  running: false,
  lastError: '',
  lastOkAt: 0,
  startedAt: 0,
};

let tokenCache = null;

/** رمزِ بات: محیط جلوتر، بعد پنل. هرگز بیرون از این فایل نمی‌رود. */
async function token() {
  if (config.telegram.token) return config.telegram.token;
  if (tokenCache !== null) return tokenCache;
  const sealed = await plans.getConfig(CFG_TOKEN, '');
  tokenCache = sealed ? await stations.decryptKey(sealed) : '';
  return tokenCache;
}

async function enabled() {
  return (await plans.getConfig(CFG_ENABLED, '1')) !== '0';
}

/** بات واقعاً کار می‌کند: رمز هست و خاموش نشده. */
async function active() {
  return (await enabled()) && Boolean(await token());
}

/** هیچ متنی که به لاگ یا پنل می‌رود رمز را با خودش نمی‌برد. */
function hide(text, tok) {
  let s = String(text == null ? '' : text);
  if (tok) s = s.split(tok).join('***');
  return s.replace(/bot\d{5,}:[A-Za-z0-9_-]{20,}/g, 'bot***').slice(0, 300);
}

/**
 * یک فراخوانِ API تلگرام.
 *
 * ⚠️ هرگز استثنا بیرون نمی‌دهد: خطای شبکه همان شکلِ پاسخِ تلگرام را
 * می‌گیرد (`ok:false`, `error_code:0`)، تا صدا‌زننده یک راه برای خطا داشته
 * باشد، نه دو.
 */
async function call(method, params = {}, { tok = null, timeoutMs = 15000 } = {}) {
  const t = tok || await token();
  if (!t) return { ok: false, error_code: 0, description: 'رمزِ بات تنظیم نشده است' };
  try {
    if (transport) return await transport(method, params, t);
    const res = await fetch(`${config.telegram.apiBase}/bot${t}/${method}`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(params),
      signal: AbortSignal.any([AbortSignal.timeout(timeoutMs), stopSignal()]),
    });
    let json = null;
    try { json = await res.json(); } catch { /* پاسخِ غیرِ JSON */ }
    return json && typeof json === 'object'
      ? json
      : { ok: false, error_code: res.status, description: `پاسخِ نامعتبر (${res.status})` };
  } catch (err) {
    return { ok: false, error_code: 0, description: hide(err.cause?.message || err.message, t) };
  }
}

/** یک پیامِ متنی. ⛔ بی `parse_mode` — بالا را ببینید. */
function send(chatId, text, keyboard = null) {
  const params = {
    chat_id: String(chatId),
    text: String(text).slice(0, 4000),
    disable_web_page_preview: true,
  };
  if (keyboard) params.reply_markup = { inline_keyboard: keyboard };
  return call('sendMessage', params);
}

/** نامِ بات را از خودِ تلگرام می‌پرسد و نگه می‌دارد. */
async function refreshMe(tok = null) {
  const res = await call('getMe', {}, { tok });
  if (res.ok && res.result?.username) {
    bot.username = String(res.result.username);
    await plans.setConfig(CFG_USERNAME, bot.username);
  }
  return res;
}

async function username() {
  if (bot.username) return bot.username;
  bot.username = await plans.getConfig(CFG_USERNAME, '');
  return bot.username;
}

/* ══════════════════════════════════════════════════════════════════
   ابزارهای کوچک
   ══════════════════════════════════════════════════════════════════ */

const FA_DIGITS = '۰۱۲۳۴۵۶۷۸۹';
const AR_DIGITS = '٠١٢٣٤٥٦٧٨٩';

/** رقمِ فارسی و عربی ⇒ لاتین. کاربرِ افغان با صفحه‌کلیدِ فارسی کد را می‌زند. */
function asciiDigits(s) {
  return String(s || '').replace(/[۰-۹٠-٩]/g, (ch) => {
    const i = FA_DIGITS.indexOf(ch);
    return String(i >= 0 ? i : AR_DIGITS.indexOf(ch));
  });
}

function faNum(n) {
  return String(n).replace(/\d/g, (d) => FA_DIGITS[Number(d)]);
}

/** ایمیلِ تمیز یا رشتهٔ خالی. */
function normEmail(raw) {
  const s = String(raw || '').trim().toLowerCase();
  if (s.length > 160) return '';
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(s) ? s : '';
}

function ago(ms) {
  const d = Math.max(0, now() - Number(ms));
  if (d < 60_000) return 'همین حالا';
  if (d < 3_600_000) return `${faNum(Math.floor(d / 60_000))} دقیقه پیش`;
  if (d < 86_400_000) return `${faNum(Math.floor(d / 3_600_000))} ساعت پیش`;
  return `${faNum(Math.floor(d / 86_400_000))} روز پیش`;
}

/**
 * فرمانِ تلگرام: `/start`، `/start@MyBot abc`.
 *
 * ⚠️ فرمانی که به باتِ **دیگری** نشانی دارد (در گروهِ چندباته) مالِ ما
 * نیست و نادیده گرفته می‌شود.
 */
function parseCommand(text, me = '') {
  const m = /^\/([A-Za-z_]+)(?:@([A-Za-z0-9_]+))?(?:\s+([\s\S]*))?$/.exec(String(text || '').trim());
  if (!m) return null;
  if (m[2] && me && m[2].toLowerCase() !== me.toLowerCase()) return null;
  return { name: m[1].toLowerCase(), arg: String(m[3] || '').trim() };
}

function hashToken(raw) {
  return crypto.createHash('sha256').update(String(raw)).digest('hex');
}

/* ══════════════════════════════════════════════════════════════════
   دکمه‌ها و «صفحه‌ها»
   ══════════════════════════════════════════════════════════════════

   ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۴، با دو عکس): «محض این که چیزی
   می‌گه چندین دکمه میان زیرش و خیلی شلوغی به وجود میاره… من برای یک
   پیام منو رو باز کردم ولی بعدِ هر پیام منو هم پشتِ سرش است.»

   سه قاعده، و هر سه آزمون دارند:

   ۱) **دکمه صفحه را عوض می‌کند، پیامِ تازه نمی‌سازد.** هر دکمهٔ منو همان
      پیام را ویرایش می‌کند (`editMessageText`)، پس گفت‌وگو با هر کلیک یک
      پیام بلندتر نمی‌شود.
   ۲) **فقط یک منوی دکمه‌دار در هر گفت‌وگو.** وقتی منوی تازه‌ای فرستاده
      می‌شود، دکمه‌های منوی قبلی برداشته می‌شوند (`menu_msg_id`).
   ۳) **پیامِ هشدار، پاسخِ جست‌وجو و پیامِ «وصل شد» هیچ دکمه‌ای ندارند.**
      منو فقط با `/menu` یا دکمهٔ «منو»ی خودِ تلگرام می‌آید.

   منوی اصلی چهار دکمه است در دو ردیف — نه شش ردیفِ پیشین.
*/

/**
 * لینکِ اپِ کارمندان — همان دو نشانیِ `KarLink.ApkUrl`/`IphoneUrl`ِ برنامهٔ
 * پمپ، روی دامنهٔ خودِ پمپ.
 *
 * ⛔ فایلِ نصبِ سایتِ قدیم (`PumpYaqobi.apk`) هیچ‌وقت داده نمی‌شود —
 * قاعدهٔ ریپوی پمپ: «فایلِ نصبِ اندروید فقط یکی است».
 */
function appLinks() {
  const site = config.telegram.pumpSite;
  return {
    android: `${site}/downloads/PumpYaqobiKar.apk`,
    iphone: `${site}/kar/`,
  };
}

function downloadRow() {
  const l = appLinks();
  return [
    { text: '📱 اندروید', url: l.android },
    { text: '🍎 آیفون', url: l.iphone },
  ];
}

const BACK = { text: '‹ منو', callback_data: 'menu' };

function menuKeyboard(row) {
  if (row.kind === 'channel') return null;
  const top = [
    { text: '📊 وضعیت', callback_data: 'status' },
    { text: '📱 اپ و کدِ پمپ', callback_data: 'app' },
  ];
  if (row.kind === 'private') {
    return [top, [
      { text: '👥 گروه و کانال', callback_data: 'share' },
      { text: '⚙️ تنظیمات', callback_data: 'settings' },
    ]];
  }
  return [top, [{ text: '⚙️ تنظیمات (مدیرِ گروه)', callback_data: 'settings' }]];
}

/* ══════════════════════════════════════════════════════════════════
   گفت‌وگوها
   ══════════════════════════════════════════════════════════════════ */

function isLinked(row) {
  return Boolean(row && row.station_id && row.linked_at);
}

function titleOf(chat) {
  if (chat.title) return String(chat.title).slice(0, 120);
  return [chat.first_name, chat.last_name].filter(Boolean).join(' ').slice(0, 120);
}

async function chatRow(chatId) {
  return one('SELECT * FROM telegram_chats WHERE chat_id=$1', [String(chatId)]);
}

async function ensureChat(chat, kind) {
  const t = now();
  return one(
    `INSERT INTO telegram_chats (chat_id, kind, title, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$4)
     ON CONFLICT (chat_id) DO UPDATE SET title=excluded.title, kind=excluded.kind, updated_at=excluded.updated_at
     RETURNING *`,
    [String(chat.id), kind, titleOf(chat), t]
  );
}

async function setState(chatId, stateName, email = '') {
  await query(
    'UPDATE telegram_chats SET state=$2, pending_email=$3, updated_at=$4 WHERE chat_id=$1',
    [String(chatId), stateName, email, now()]
  );
}

/**
 * پمپ و حسابی که این گفت‌وگو به آن وصل است — **فقط اگر هنوز معتبر باشد**.
 *
 * ⛔ همان سنجشِ قیدِ ۳: عضوِ فعال، پمپِ فعال، حسابِ فعال.
 */
async function linkOf(row) {
  if (!isLinked(row)) return null;
  return one(
    `SELECT s.id AS station_id, s.name AS station_name, u.email, m.role
       FROM stations s
       JOIN station_members m ON m.station_id=s.id AND m.user_id=$2 AND m.status='active'
       JOIN users u ON u.id=m.user_id AND u.status='active'
      WHERE s.id=$1 AND s.status='active'`,
    [row.station_id, row.user_id]
  );
}

async function unlink(chatId) {
  await query(
    `UPDATE telegram_chats
        SET station_id=NULL, user_id=NULL, linked_at=NULL, state='', pending_email='', updated_at=$2
      WHERE chat_id=$1`,
    [String(chatId), now()]
  );
  await query(
    'DELETE FROM telegram_outbox WHERE chat_id=$1 AND sent_at IS NULL AND failed_at IS NULL',
    [String(chatId)]
  );
}

/** بات را از گروه بیرون کردند یا کاربر بات را بست — هیچ‌چیزی از آن نمی‌ماند. */
async function forgetChat(chatId) {
  await query('DELETE FROM telegram_outbox WHERE chat_id=$1 AND sent_at IS NULL', [String(chatId)]);
  await query('DELETE FROM telegram_chats WHERE chat_id=$1', [String(chatId)]);
}

/** گروهِ ساده که «ابرگروه» شد شناسهٔ تازه می‌گیرد؛ پیوند نباید گم شود. */
async function migrateChat(oldId, newId_) {
  const from = String(oldId);
  const to = String(newId_);
  if (!to || from === to) return;
  await query('DELETE FROM telegram_chats WHERE chat_id=$1 AND station_id IS NULL', [to]);
  await query('UPDATE telegram_chats SET chat_id=$2, updated_at=$3 WHERE chat_id=$1', [from, to, now()])
    .catch(() => {});
  await query('UPDATE telegram_outbox SET chat_id=$2 WHERE chat_id=$1 AND sent_at IS NULL', [from, to]);
}

/* ── نشاندنِ یک «صفحه» ────────────────────────────────────────── */

/**
 * آخرین پیامِ دکمه‌دارِ این گفت‌وگو را به‌یاد می‌سپارد و دکمه‌های قبلی را
 * برمی‌دارد (قاعدهٔ ۲). هر خطایی بلعیده می‌شود: پیامی که کاربر پاکش کرده
 * یا کهنه‌تر از ۴۸ ساعت است دیگر ویرایش نمی‌شود، و این نباید چیزی را بشکند.
 */
async function rememberMenu(chatId, messageId) {
  const id = Number(messageId) || 0;
  if (!id) return;
  const row = await chatRow(chatId);
  const old = row ? Number(row.menu_msg_id) || 0 : 0;
  if (old && old !== id) {
    await call('editMessageReplyMarkup', {
      chat_id: String(chatId), message_id: old, reply_markup: { inline_keyboard: [] },
    }).catch(() => {});
  }
  if (row) {
    await query('UPDATE telegram_chats SET menu_msg_id=$2 WHERE chat_id=$1', [String(chatId), id]);
  }
}

/**
 * یک صفحه (متن + دکمه): اگر از دکمه آمده، **همان پیام** ویرایش می‌شود؛
 * وگرنه پیامِ تازه، و منوی قبلی دکمه‌هایش را از دست می‌دهد.
 *
 * ⚠️ «message is not modified» خطا نیست: کاربر دوبار روی همان دکمه زده.
 */
async function show(chatId, text, keyboard, editId = 0) {
  const parts = chunks(text);
  if (parts.length > 1) {
    //  متنِ بلند: تکهٔ اول جای همان پیام، میانی‌ها پیامِ تازه، و دکمه‌ها زیرِ آخری
    if (editId) {
      await call('editMessageText', {
        chat_id: String(chatId), message_id: Number(editId), text: parts[0],
        disable_web_page_preview: true, reply_markup: { inline_keyboard: [] },
      });
    } else {
      await send(chatId, parts[0]);
    }
    for (const mid of parts.slice(1, -1)) await send(chatId, mid);
    const last = parts[parts.length - 1];
    const res = await send(chatId, last, keyboard && keyboard.length ? keyboard : null);
    if (res.ok && keyboard && keyboard.length) await rememberMenu(chatId, res.result?.message_id);
    return res;
  }
  const body = parts[0];
  const markup = { inline_keyboard: keyboard || [] };
  if (editId) {
    const res = await call('editMessageText', {
      chat_id: String(chatId), message_id: Number(editId), text: body,
      disable_web_page_preview: true, reply_markup: markup,
    });
    if (res.ok || /not modified/i.test(String(res.description || ''))) {
      if (keyboard && keyboard.length) await rememberMenu(chatId, editId);
      return res;
    }
  }
  const res = await send(chatId, body, keyboard && keyboard.length ? keyboard : null);
  if (res.ok && keyboard && keyboard.length) await rememberMenu(chatId, res.result?.message_id);
  return res;
}

/* ── خصوصی ─────────────────────────────────────────────────────── */

const WELCOME =
  'سلام 👋 این باتِ هشدارهای پمپ است.\n\n'
  + 'هر وقت حسابِ قرض‌داری تمام شود یا کم بماند، یا مخزن ته بکشد، همین‌جا خبر می‌گیرید — '
  + 'حتی وقتی برنامه بسته است.\n\n'
  + '✉️ برای وصل شدن، ایمیلِ حسابِ پمپتان را بفرستید (همان که در برنامهٔ پمپ با آن وارد شده‌اید).\n'
  + 'یک کدِ شش‌رقمی به همان ایمیل می‌رود؛ کد را همین‌جا بفرستید و تمام.';

/** راهنما — برای کسی که هنوز وصل نیست هم هست. */
const HELP =
  '📖 راهنمای بات\n\n'
  + '• ✉️ وصل شدن: ایمیلِ حسابِ پمپ را بفرستید و کدِ شش‌رقمیِ ایمیل را همین‌جا بزنید.\n'
  + '• 🔎 جست‌وجو: نامِ قرض‌دار را بنویسید تا حالِ حسابش بیاید (در گروه: /find نام).\n'
  + '• 📊 وضعیت: هشدارهای بازِ همین حالا و موجودیِ مخزن.\n'
  + '• 📱 اپ و کدِ پمپ: لینکِ اپِ اندروید و آیفون، و کدی که کارمندان در اپ می‌زنند.\n'
  + '• 👥 گروه و کانال: هشدارها در گروه یا کانالِ تلگرامِ شما هم بیاید.\n'
  + '• ⚙️ تنظیمات: فقط «تمام شد» بیاید، یا جدا شدن.\n\n'
  + 'منو همیشه با /menu یا دکمهٔ «منو»ی پایینِ صفحه می‌آید.';

function welcomeKeyboard() {
  return [downloadRow(), [{ text: '❓ راهنما', callback_data: 'help' }]];
}

/** وقتی گفت‌وگو دیگر به هیچ پمپی وصل نیست — همان خوش‌آمد، با دلیلش. */
async function notLinked(row, editId = 0) {
  const was = isLinked(row);
  if (was) await unlink(row.chat_id);
  if (row.kind === 'private') {
    await setState(row.chat_id, 'email');
    return show(row.chat_id,
      (was ? 'این گفت‌وگو دیگر به هیچ پمپی وصل نیست — حساب از آن پمپ بیرون شده است.\n\n' : '')
      + WELCOME, welcomeKeyboard(), editId);
  }
  return send(row.chat_id,
    'این گروه به هیچ پمپی وصل نیست. در گفت‌وگوی خصوصی با بات، «👥 گروه و کانال» را بزنید.');
}

/** هشدارها کجا می‌آیند — همان قاعدهٔ `alertChats`، به زبانِ آدم. */
async function whereText(row) {
  const g = await groupOfUser(row);
  if (g) return `📣 هشدارها در «${g}» می‌آیند، نه این‌جا. (جدا کردنِ گروه ⇐ هشدارها دوباره این‌جا.)`;
  return row.only_out ? '🔕 فقط هشدارهای «تمام شد» می‌آید.' : '🔔 همهٔ هشدارها می‌آید: «تمام شد» و «کم مانده».';
}

async function menuText(row, link) {
  const state = await require('./pump-state').get(link.station_id);
  const open = state ? state.alerts.length : 0;
  return `⛽ پمپِ «${link.station_name}»\n`
    + (open ? `🚨 ${faNum(open)} هشدارِ باز — «📊 وضعیت» را بزنید.\n` : '✅ همین حالا هشدارِ بازی نیست.\n')
    + await whereText(row)
    + (row.kind === 'private'
      ? '\n\n🔎 برای جست‌وجو، نامِ قرض‌دار را همین‌جا بنویسید.'
      : (row.kind === 'group' ? '\n\n🔎 جست‌وجو: /find نامِ قرض‌دار' : ''));
}

async function sendMenu(row, lead = '', editId = 0) {
  const link = await linkOf(row);
  if (!link) return notLinked(row, editId);
  const text = (lead ? `${lead}\n\n` : '') + await menuText(row, link);
  //  ⚠️ در کانال دکمه نمی‌گذاریم: هر خواننده‌ای می‌دیدش
  if (row.kind === 'channel') return send(row.chat_id, text);
  return show(row.chat_id, text, menuKeyboard(row), editId);
}

/* ── وضعیت: حالِ زنده، نه فقط تاریخچه ─────────────────────────── */

const STALE_MS = 30 * 60 * 1000;

function fmtNum(n) {
  const v = Math.round(Number(n) || 0);
  return (v < 0 ? '−' : '') + faNum(Math.abs(v).toLocaleString('en-US')).replace(/,/g, '٬');
}

function alertLine(a) {
  const out = a.s === 'out';
  const who = String(a.k || '').startsWith('tank-') ? '🛢️' : '👤';
  return `${who}${out ? '🔴' : '🟡'} ${a.t || a.n || ''}`;
}

/**
 * متنِ «وضعیت».
 *
 * ⛔ **راستش را می‌گوید**: اگر برنامهٔ کامپیوتر هرگز حالش را نفرستاده (نسخهٔ
 * کهنه، یا به سرور بند نشده) یا مدتی است خبری نداده، همین را می‌گوید —
 * نه «هیچ هشداری نیست». سکوتِ برنامه با «همه‌چیز خوب است» یکی نیست.
 */
async function statusText(link) {
  const state = await require('./pump-state').get(link.station_id);
  const head = `📊 پمپِ «${link.station_name}»\n`;
  if (!state) {
    const list = await many(
      `SELECT kind, title, data, created_at FROM station_events
        WHERE station_id=$1 AND kind = ANY($2::text[])
        ORDER BY created_at DESC LIMIT 8`,
      [link.station_id, ALERT_KINDS]
    );
    const lines = list.map(e => `${iconOf(e)} ${e.title || ''} — ${ago(e.created_at)}`);
    return head
      + '⚠️ برنامهٔ کامپیوترِ این پمپ هنوز حالِ زنده‌اش را به سرور نفرستاده است — '
      + 'برنامهٔ پمپ را به‌روز کنید و مطمئن شوید با حسابِ همین پمپ وارد شده است.\n\n'
      + (lines.length ? `آخرین هشدارهایی که آمده:\n${lines.join('\n')}` : 'هنوز هیچ هشداری نیامده است.');
  }

  const parts = [head];
  const age = now() - state.updatedAt;
  parts.push(age > STALE_MS
    ? `⚠️ برنامهٔ کامپیوتر ${ago(state.updatedAt)} آخرین بار خبر داد — شاید بسته است.`
    : `🖥️ برنامهٔ کامپیوتر ${ago(state.updatedAt)} خبر داد.`);

  const tanks = tankLines(state.tank);
  if (tanks.length) parts.push('', '🛢️ مخزن:', ...tanks);

  parts.push('');
  if (state.alerts.length) {
    parts.push(`🚨 هشدارهای باز (${faNum(state.alerts.length)}):`);
    for (const a of state.alerts) parts.push(alertLine(a));
  } else {
    parts.push('✅ همین حالا هیچ هشدارِ بازی نیست.');
  }
  const owe = oweLines(state.owe);
  if (owe.length) parts.push('', '💼 بدهیِ پمپ به شرکت‌ها:', ...owe);
  const todo = actionLines(state.alerts.map(a => a.a));
  if (todo.length) parts.push('', '📋 دستورِ کار:', ...todo);
  return parts.join('\n');
}

/** هر مخزن در یک خط: چند لیتر مانده و حدِ هشدار — نه فقط یک عدد. */
function tankLines(tank) {
  const out = [];
  for (const [key, label] of [['petrol', 'پطرول'], ['diesel', 'دیزل']]) {
    const t = tank?.[key];
    if (!t) continue;
    const mark = t.low ? '🔴' : (t.near ? '🟡' : '🟢');
    const word = Number(t.show) <= 0 ? 'خالی است' : (t.low ? 'کم است' : (t.near ? 'نزدیکِ حد است' : 'پُر است'));
    const lim = Number(t.threshold) > 0 ? ` (حدِ هشدار ${fmtNum(t.threshold)} لیتر)` : '';
    out.push(`${mark} ${label}: ${fmtNum(t.show)} لیتر مانده — ${word}${lim}`);
  }
  return out;
}

/** بدهیِ پمپ به هر شرکت — همان الباقی‌ای که برنامه حساب کرده. */
function oweLines(owe) {
  return (Array.isArray(owe) ? owe : []).map((o) => {
    const parts = [];
    if (Number(o.afn) > 0) parts.push(`${fmtNum(o.afn)} افغانی`);
    if (Number(o.usd) > 0) parts.push(`${fmtNum(o.usd)} دالر`);
    return `▫️ ${o.n}: ${parts.join(' · ')}`;
  });
}

async function sendStatus(row, editId = 0) {
  const link = await linkOf(row);
  if (!link) return notLinked(row, editId);
  const kb = row.kind === 'channel' ? null
    : [[{ text: '🔄 تازه کن', callback_data: 'status' }, BACK]];
  if (!kb) return sendLong(row.chat_id, await statusText(link));
  return show(row.chat_id, await statusText(link), kb, editId);
}

/* ── جست‌وجوی قرض‌دار ─────────────────────────────────────────── */

/** نامِ قابلِ مقایسه: «ي/ك» عربی، نیم‌فاصله و فاصلهٔ اضافه یکی می‌شوند. */
function normName(s) {
  return String(s || '')
    .replace(/[يى]/g, 'ی').replace(/ك/g, 'ک').replace(/[ۀة]/g, 'ه')
    .replace(/[‌‍ً-ٟ]/g, '')
    .replace(/\s+/g, ' ').trim().toLowerCase();
}

const ST_LABEL = {
  out: '🔴 تمام شد — اضافه نده',
  low: '🟡 کم مانده',
  ok: '🟢 موجودی دارد',
};

function debtorLines(d) {
  const lines = [`👤 ${d.n}`];
  for (const [st, val, label, unit] of [
    [d.sp, d.p, 'پطرول', 'لیتر'], [d.sd, d.d, 'دیزل', 'لیتر'], [d.sm, d.m, 'پول', 'افغانی'],
  ]) {
    if (!ST_LABEL[st]) continue;
    lines.push(`   ${label}: ${ST_LABEL[st]} · الباقی ${fmtNum(val)} ${unit}`);
  }
  if (lines.length === 1) lines.push('   هنوز هیچ حسابی ندارد.');
  return lines.join('\n');
}

/**
 * «بات دنبالِ چیزی نمی‌گرده» — حالا نامِ قرض‌دار را در خلاصه‌ای که
 * برنامهٔ کامپیوتر فرستاده می‌گردد.
 *
 * ⛔ **هیچ عددی این‌جا حساب نمی‌شود**: حال و الباقی همان است که کارتِ
 * قرض‌دار در برنامه نشان می‌دهد (`StationSnapshot`). اگر برنامه فهرست را
 * نفرستاده، همین گفته می‌شود — «پیدا نشد»ِ دروغ نه.
 */
async function searchText(link, q) {
  const query_ = normName(q);
  if (query_.length < 2) return '🔎 دستِ‌کم دو حرف از نامِ قرض‌دار را بنویسید.';
  const state = await require('./pump-state').get(link.station_id);
  if (!state || !Array.isArray(state.debtors)) {
    return '🔎 برنامهٔ کامپیوترِ این پمپ هنوز فهرستِ قرض‌داران را به سرور نفرستاده است — '
      + 'برنامهٔ پمپ را به‌روز و روشن کنید؛ چند دقیقه بعد جست‌وجو کار می‌کند.';
  }
  const hits = state.debtors.filter(d => normName(d.n).includes(query_));
  if (!hits.length) return `🔎 «${String(q).trim().slice(0, 60)}» در قرض‌دارانِ پمپِ «${link.station_name}» پیدا نشد.`;
  //  دقیق‌ترین اول: نامِ برابر، بعد آن‌که با همین آغاز می‌شود
  hits.sort((a, b) => {
    const ra = normName(a.n) === query_ ? 0 : (normName(a.n).startsWith(query_) ? 1 : 2);
    const rb = normName(b.n) === query_ ? 0 : (normName(b.n).startsWith(query_) ? 1 : 2);
    return ra - rb || a.n.localeCompare(b.n, 'fa');
  });
  const count = hits.length > 1 ? ` — ${faNum(hits.length)} نفر` : '';
  return `🔎 پمپِ «${link.station_name}»${count}\n\n${hits.map(debtorLines).join('\n\n')}`
    + `\n\n🕒 ${ago(state.debtorsAt || state.updatedAt)}`;
}

/* ── اپ و کدِ پمپ ────────────────────────────────────────────── */

/**
 * «سرور و برنامه برای هر حساب یک کدِ هشت‌رقمی ساخته‌اند که به کارمندان
 * بدهند تا اپِ اندروید و آیفون را برای همان حساب باز کنند» — همان کدِ
 * دسترسیِ پمپ (`station-access`). بات فقط نشانش می‌دهد؛ ساختن و عوض
 * کردنش همان‌جای همیشگی است (پروفایلِ برنامهٔ کامپیوتر).
 *
 * ⛔ در گروه کد روی پیام نوشته نمی‌شود: فقط به مدیرِ گروه، در پنجرهٔ
 * خصوصیِ همان کلیک (`show_alert`) — کسی که تازه به گروه آمده نباید با یک
 * نگاه کلیدِ حساب‌های پمپ را ببیند.
 */
async function accessCodeOf(stationId) {
  const access = require('./station-access');
  return access.format(await access.ensure(stationId));
}

async function sendApp(row, editId = 0) {
  const link = await linkOf(row);
  if (!link) return notLinked(row, editId);
  const common = '📱 اپِ کارمندانِ پمپ\n\n'
    + '۱) اپ را نصب کنید: اندروید (فایلِ نصب) یا آیفون (در Safari باز کنید و «افزودن به صفحهٔ اصلی»).\n'
    + '۲) کدِ پمپ را در اپ بزنید — حساب‌های همین پمپ باز می‌شود و با پمپ‌های دیگر قاطی نمی‌شود.\n'
    + '۳) رمزِ اپ همان رمزِ برنامهٔ کامپیوتر است.';
  if (row.kind === 'private') {
    const code = await accessCodeOf(link.station_id);
    return show(row.chat_id,
      `${common}\n\n🔑 کدِ پمپِ «${link.station_name}»:  ${code}\n\n`
      + '⚠️ این کد را فقط به کارمندانِ همین پمپ بدهید. عوض کردنش: برنامهٔ کامپیوتر ← پروفایل.',
      [downloadRow(), [BACK]], editId);
  }
  return show(row.chat_id,
    `${common}\n\n🔑 کدِ پمپ را مدیرِ گروه با دکمهٔ زیر می‌بیند (فقط برای خودش نشان داده می‌شود).`,
    [downloadRow(), [{ text: '🔑 کدِ پمپ (مدیر)', callback_data: 'code' }, BACK]], editId);
}

/* ── گروه و کانال ─────────────────────────────────────────────── */

/**
 * نشانیِ یک‌بارمصرفِ «افزودن به گروه» (قیدِ ۴) و راهِ کانال — یک صفحه.
 *
 * `t.me/<بات>?startgroup=<نشانه>` ⇒ کاربر گروه را برمی‌گزیند ⇒ تلگرام در
 * همان گروه `/start <نشانه>` را برای بات می‌فرستد.
 */
async function sendShare(row, editId = 0) {
  const link = await linkOf(row);
  if (!link) return notLinked(row, editId);
  const name = await username();
  if (!name) return show(row.chat_id, 'نامِ بات هنوز از تلگرام نیامده است؛ چند لحظه بعد دوباره بزنید.', [[BACK]], editId);

  const raw = crypto.randomBytes(18).toString('base64url');
  const t = now();
  await query(
    `INSERT INTO telegram_link_tokens (token_hash, station_id, user_id, expires_at, created_at)
     VALUES ($1,$2,$3,$4,$5)`,
    [hashToken(raw), row.station_id, row.user_id, t + LINK_TTL_MS, t]
  );
  //  نشانه‌های کهنه همان‌جا جارو می‌شوند — این جدول نباید بزرگ شود
  await query('DELETE FROM telegram_link_tokens WHERE expires_at < $1', [t - LINK_TTL_MS]);

  return show(row.chat_id,
    `👥 هشدارهای پمپِ «${link.station_name}» در گروه یا کانالِ شما:\n\n`
    + 'گروه: دکمهٔ «افزودن به گروه» ⇒ گروه را برگزینید ⇒ تمام، بات خودش خبر می‌دهد.\n'
    + '⏳ این دکمه پانزده دقیقه و فقط یک بار کار می‌کند.\n\n'
    + 'کانال: دکمهٔ «افزودن به کانال» ⇒ کانال را برگزینید ⇒ بات را «مدیر» کنید '
    + '(فقط «ارسالِ پیام» کافی است).\n\n'
    + '⚙️ در گروه، فقط مدیرانِ گروه تنظیمات را عوض می‌کنند.',
    [
      [{ text: '➕ افزودن به گروه', url: `https://t.me/${name}?startgroup=${raw}` }],
      [{ text: '📣 افزودن به کانال', url: `https://t.me/${name}?startchannel&admin=post_messages` }],
      [BACK],
    ], editId);
}

/* ── تنظیمات ──────────────────────────────────────────────────── */

async function sendSettings(row, editId = 0, lead = '') {
  const link = await linkOf(row);
  if (!link) return notLinked(row, editId);
  const text = (lead ? `${lead}\n\n` : '') + `⚙️ تنظیماتِ این ${row.kind === 'private' ? 'گفت‌وگو' : 'گروه'}\n\n`
    + (row.only_out
      ? '🔕 الان فقط هشدارهای «تمام شد» می‌آید.'
      : '🔔 الان همهٔ هشدارها می‌آید: «تمام شد» و «کم مانده».');
  return show(row.chat_id, text, [
    [{ text: row.only_out ? '🔔 «کم مانده» را هم بفرست' : '🔕 فقط «تمام شد» را بفرست', callback_data: 'toggle' }],
    [{ text: row.kind === 'private' ? '🔌 جدا شدن از پمپ' : '🔌 جدا کردنِ این گروه', callback_data: 'unlink' }],
    [BACK],
  ], editId);
}

function askUnlink(row, editId = 0) {
  return show(row.chat_id,
    row.kind === 'private'
      ? 'این گفت‌وگو از پمپ جدا شود؟ دیگر هیچ هشداری این‌جا نمی‌آید. (گروه‌هایی که وصل کرده‌اید جدا وصل‌اند.)'
      : 'این گروه از پمپ جدا شود؟ دیگر هیچ هشداری این‌جا نمی‌آید.',
    [[
      { text: '🔌 بله، جدا کن', callback_data: 'unlink_yes' },
      { text: 'نه', callback_data: 'settings' },
    ]], editId);
}

/**
 * گامِ ایمیل.
 *
 * ⛔ **پاسخ برای ایمیلِ ناموجود همان است** (قیدِ ۲). کد فقط وقتی واقعاً
 * می‌رود که حساب هست و پمپ دارد.
 */
async function onEmail(row, text) {
  const email = normEmail(text);
  if (!email) {
    return send(row.chat_id,
      'این نشانیِ ایمیل درست نیست. ایمیلِ حسابِ پمپتان را بفرستید؛ مثلاً name@gmail.com');
  }

  const t = now();
  let count = Number(row.code_requests) || 0;
  let windowAt = Number(row.window_at) || 0;
  if (t - windowAt > CODE_WINDOW_MS) { count = 0; windowAt = t; }
  if (count >= CODE_MAX_PER_WINDOW) {
    return send(row.chat_id, 'درخواستِ کد از این گفت‌وگو زیاد شد. یک ساعت بعد دوباره امتحان کنید.');
  }

  //  ⛔ «رفت» با «در لاگ چاپ شد» یکی نیست: روی سرورِ واقعی بی سرویسِ
  //  ایمیل، کد به دستِ هیچ‌کس نمی‌رسد — پس راست می‌گوییم.
  const mailer = require('./mailer');
  const mc = await mailer.current();
  if (mc.provider === 'log' && config.env === 'production') {
    return send(row.chat_id,
      'سرویسِ ایمیلِ سرور هنوز تنظیم نشده و کد به ایمیل نمی‌رسد. لطفاً به مدیرِ سامانه بگویید.');
  }

  await query(
    `UPDATE telegram_chats SET state='code', pending_email=$2, code_requests=$3, window_at=$4, updated_at=$5
      WHERE chat_id=$1`,
    [row.chat_id, email, count + 1, windowAt, t]
  );

  const user = await one("SELECT id FROM users WHERE email=$1 AND status='active'", [email]);
  const member = user ? await stations.membershipOf(user.id) : null;
  if (!member) {
    /*
     *  ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳): «سرور کد را ارسال نکرد.» ایمیلی
     *  که حسابِ پمپ ندارد تا امروز **هیچ** نامه‌ای نمی‌گرفت و کاربر نمی‌دانست
     *  چرا. پاسخِ بات همان می‌ماند (قیدِ ۲ — دفترچهٔ ایمیل‌ها نشویم)، ولی خودِ
     *  صندوقِ همان ایمیل می‌گوید چه شد: فقط صاحبِ همان صندوق آن را می‌بیند.
     */
    tellNoAccount(email, Boolean(user)).catch((e) => console.error('[telegram] نامهٔ «حساب نیست» نرفت:', e.message));
  }
  const other = [[{ text: '✉️ ایمیلِ دیگر', callback_data: 'reset' }]];
  if (member) {
    try {
      await require('./otp').request(email, { purpose: PURPOSE, app: 'pump' });
    } catch (err) {
      if (err.code === 'otp_resend_wait') {
        return show(row.chat_id, `${err.message}. اگر کدِ قبلی رسیده، همان را بفرستید.`, other);
      }
      if (err.code === 'otp_daily_limit') return send(row.chat_id, err.message);
      console.error('[telegram] کد نرفت:', err.message);
      return send(row.chat_id,
        'کد به ایمیل فرستاده نشد. کمی بعد دوباره امتحان کنید؛ اگر باز هم نشد، به مدیرِ سامانه بگویید.');
    }
  }

  return show(row.chat_id,
    `اگر «${email}» حسابِ پمپ داشته باشد، یک کدِ شش‌رقمی همین حالا به آن رفت `
    + '(پوشهٔ اسپم را هم ببینید).\n\n🔢 کد را همین‌جا بفرستید.', other);
}

/** نامه به همان صندوق: «با این ایمیل حسابِ پمپی نیست» — همان مدلِ ایمیلِ پمپ. */
async function tellNoAccount(email, hasUser) {
  const mailer = require('./mailer');
  const templates = require('./mail-templates');
  const title = 'این ایمیل به هیچ پمپی وصل نیست';
  const body = (hasUser
    ? 'کسی در باتِ تلگرامِ پمپ خواست با همین ایمیل وصل شود، ولی این حساب هنوز عضوِ هیچ پمپی نیست.'
    : 'کسی در باتِ تلگرامِ پمپ خواست با همین ایمیل وصل شود، ولی با این ایمیل هیچ حسابی ساخته نشده است.')
    + '\n\nاول در برنامهٔ پمپ با همین ایمیل حساب بسازید و پمپتان را بسازید، بعد دوباره در بات همین ایمیل را بفرستید.'
    + '\n\nاگر شما نبودید، این نامه را نادیده بگیرید.';
  const html = templates.messageHtml({ app: 'pump', title, body })
    || mailer.card({ title, lead: body });
  await mailer.send({ to: email, subject: title, text: `${title}\n\n${body}`, html, fromName: templates.brandOf('pump') || undefined });
}

/** گامِ کد. ⛔ حسابِ ناموجود هم فقط «کد درست نیست» می‌گیرد. */
async function onCode(row, text) {
  const digits = asciiDigits(text).replace(/\D/g, '');
  if (digits.length !== 6) {
    return show(row.chat_id, 'کدِ شش‌رقمیِ ایمیل را بفرستید — یا «✉️ ایمیلِ دیگر» را بزنید.',
      [[{ text: '✉️ ایمیلِ دیگر', callback_data: 'reset' }]]);
  }
  const email = row.pending_email;
  const user = email ? await one("SELECT id FROM users WHERE email=$1 AND status='active'", [email]) : null;
  const member = user ? await stations.membershipOf(user.id) : null;
  if (!member) return send(row.chat_id, '❌ کد درست نیست.');

  try {
    await require('./otp').verify(email, digits, { purpose: PURPOSE });
  } catch (err) {
    if (err.code === 'otp_wrong') return send(row.chat_id, '❌ کد درست نیست. دوباره بفرستید.');
    await setState(row.chat_id, 'email');
    return send(row.chat_id,
      err.code === 'otp_locked'
        ? 'تلاشِ نادرست زیاد شد. ایمیل را دوباره بفرستید تا کدِ تازه برود.'
        : 'مهلتِ این کد تمام شده است. ایمیل را دوباره بفرستید.');
  }

  const t = now();
  await query(
    `UPDATE telegram_chats
        SET station_id=$2, user_id=$3, linked_at=$4, state='', pending_email='', updated_at=$4
      WHERE chat_id=$1`,
    [row.chat_id, member.station_id, user.id, t]
  );
  await require('./audit').log({
    actorType: 'user', userId: user.id, action: 'pump.telegram_link',
    targetType: 'station', targetId: member.station_id, detail: { kind: 'private' },
  });
  return sendMenu(await chatRow(row.chat_id),
    '✅ وصل شد. از این به بعد هشدارهای همین پمپ این‌جا می‌آید — حتی وقتی برنامه بسته است.');
}

async function onPrivate(chat, cmd, text) {
  let row = await ensureChat(chat, 'private');
  const linked = isLinked(row);

  if (cmd) {
    if (cmd.name === 'cancel') {
      await setState(row.chat_id, linked ? '' : 'email');
      return linked ? sendMenu(row) : send(row.chat_id, 'باشد. هر وقت خواستید ایمیلِ حسابِ پمپ را بفرستید.');
    }
    if (cmd.name === 'help') {
      return show(row.chat_id, HELP, linked ? [[BACK]] : welcomeKeyboard());
    }
    if (linked) {
      if (cmd.name === 'status') return sendStatus(row);
      if (cmd.name === 'code' || cmd.name === 'app') return sendApp(row);
      if (cmd.name === 'group' || cmd.name === 'channel') return sendShare(row);
      if (cmd.name === 'settings') return sendSettings(row);
      if (cmd.name === 'stop') return askUnlink(row);
      if (cmd.name === 'find') return onSearch(row, cmd.arg);
      return sendMenu(row);
    }
    await setState(row.chat_id, 'email');
    return show(row.chat_id, WELCOME, welcomeKeyboard());
  }

  //  ⛔ وصل‌شده: هر نوشته‌ای جست‌وجو است، نه یک منوی دیگر (قاعدهٔ ۳).
  if (linked) return onSearch(row, text);
  //  ایمیلِ تازه در گامِ کد یعنی «این یکی را می‌خواهم»
  if (row.state === 'code' && !normEmail(text)) return onCode(row, text);
  row = { ...row, state: 'email' };
  return onEmail(row, text);
}

async function onSearch(row, q) {
  const link = await linkOf(row);
  if (!link) return notLinked(row);
  if (!String(q || '').trim()) {
    return send(row.chat_id, row.kind === 'private'
      ? '🔎 نامِ قرض‌دار را بنویسید.'
      : '🔎 بعد از /find نامِ قرض‌دار را بنویسید؛ مثلاً: /find کریم');
  }
  return sendLong(row.chat_id, await searchText(link, q));
}

/* ── دسترسی در گروه ───────────────────────────────────────────── */

/*
 *  ⛔ گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۴): «چرا توی بات هر کسی می‌تونه
 *  دست‌کاری کنه؟» تا امروز هر عضوِ گروه می‌توانست «فقط تمام شد» را روشن
 *  کند و هشدارِ همه را ساکت کند. حالا در گروه هر چیزی که **عوض** می‌کند
 *  (تنظیمات، جدا کردن، دیدنِ کدِ پمپ) فقط مالِ این دو نفر است:
 *    • مدیر یا سازندهٔ همان گروه (از خودِ تلگرام پرسیده می‌شود)
 *    • همان حسابی که گروه را وصل کرد (گفت‌وگوی خصوصیِ وصل‌شده‌اش)
 *  دیدنِ وضعیت و جست‌وجو برای همه باز است — گروه، گروهِ کارکنان است.
 */
const adminCache = new Map();
const ADMIN_TTL_MS = 60 * 1000;

async function canManage(row, from) {
  if (row.kind === 'private') return true;
  const uid = from?.id;
  if (!uid) return false;
  const own = await one(
    `SELECT 1 FROM telegram_chats WHERE chat_id=$1 AND kind='private' AND user_id=$2 AND linked_at IS NOT NULL`,
    [String(uid), row.user_id || '']
  );
  if (own) return true;
  const key = `${row.chat_id}:${uid}`;
  const hit = adminCache.get(key);
  if (hit && now() - hit.at < ADMIN_TTL_MS) return hit.ok;
  const res = await call('getChatMember', { chat_id: String(row.chat_id), user_id: uid });
  const ok = Boolean(res.ok && ['creator', 'administrator'].includes(res.result?.status));
  adminCache.set(key, { ok, at: now() });
  if (adminCache.size > 1000) adminCache.delete(adminCache.keys().next().value);
  return ok;
}

/**
 * کانال — تلگرام در کانال هیچ `/start`ی نمی‌فرستد؛ فقط `my_chat_member`
 * می‌آید با **کسی که بات را مدیر کرد** (`from`). پس ملاک همان است: اگر
 * گفت‌وگوی خصوصیِ همان شخص با بات به پمپی وصل است، کانال به همان پمپ وصل
 * می‌شود. ⛔ کسِ دیگری نمی‌تواند کانالی را به پمپِ شما ببندد.
 */
async function onChannelAdmin(u) {
  const chat = u.chat;
  const who = u.from?.id ? await chatRow(u.from.id) : null;
  const owner = who && who.kind === 'private' ? await linkOf(who) : null;
  if (!owner) {
    //  کسی که وصل نیست: فقط به خودش می‌گوییم، نه در کانال
    if (u.from?.id) {
      await send(u.from.id,
        `بات مدیرِ کانالِ «${titleOf(chat)}» شد، ولی شما هنوز به هیچ پمپی وصل نیستید. `
        + 'اول همین‌جا ایمیلِ حسابِ پمپ را بفرستید و وصل شوید، بعد بات را یک بار از کانال بردارید و دوباره مدیر کنید.');
    }
    return null;
  }
  const row = await ensureChat(chat, 'channel');
  const t = now();
  await query(
    `UPDATE telegram_chats SET station_id=$2, user_id=$3, linked_at=$4, updated_at=$4 WHERE chat_id=$1`,
    [row.chat_id, who.station_id, who.user_id, t]
  );
  await require('./audit').log({
    actorType: 'user', userId: who.user_id, action: 'pump.telegram_link',
    targetType: 'station', targetId: who.station_id, detail: { kind: 'channel' },
  });
  await send(row.chat_id, `✅ این کانال به پمپِ «${owner.station_name}» وصل شد. هشدارهای پمپ از این به بعد همین‌جا منتشر می‌شود.`);
  //  ⚠️ بی دکمه: خبرِ «وصل شد» یک خبر است، نه منو (قاعدهٔ ۳)
  return send(who.chat_id,
    `✅ کانالِ «${titleOf(chat)}» به پمپِ «${owner.station_name}» وصل شد. از این پس هشدارها در همان کانال می‌آید، نه این‌جا.\nبرای جدا کردن، بات را از مدیرانِ کانال بردارید — هشدارها دوباره همین‌جا می‌آید.`);
}

async function onGroup(chat, cmd, from) {
  const row = await ensureChat(chat, 'group');
  if (!cmd) return null;                         // گفت‌وگوی گروه مالِ ما نیست

  if (cmd.name === 'start' && cmd.arg) {
    const t = now();
    const tok = await one(
      `UPDATE telegram_link_tokens SET used_at=$2
        WHERE token_hash=$1 AND used_at IS NULL AND expires_at > $2
        RETURNING station_id, user_id`,
      [hashToken(cmd.arg), t]
    );
    if (!tok) {
      return send(row.chat_id,
        'این دکمه باطل یا کهنه شده است. در گفت‌وگوی خصوصی با بات دوباره «👥 گروه و کانال» را بزنید.');
    }
    const probe = { ...row, station_id: tok.station_id, user_id: tok.user_id, linked_at: t };
    if (!(await linkOf(probe))) {
      return send(row.chat_id, 'حسابی که این دکمه را ساخت دیگر عضوِ آن پمپ نیست.');
    }
    await query(
      `UPDATE telegram_chats SET station_id=$2, user_id=$3, linked_at=$4, updated_at=$4 WHERE chat_id=$1`,
      [row.chat_id, tok.station_id, tok.user_id, t]
    );
    await require('./audit').log({
      actorType: 'user', userId: tok.user_id, action: 'pump.telegram_link',
      targetType: 'station', targetId: tok.station_id, detail: { kind: 'group' },
    });
    const priv = await one(
      `SELECT chat_id FROM telegram_chats
        WHERE kind='private' AND user_id=$1 AND station_id=$2 AND linked_at IS NOT NULL`,
      [tok.user_id, tok.station_id]
    );
    //  ⚠️ بی دکمه: خبر است، نه منو. و می‌گوید چرا خصوصی ساکت می‌شود (alertChats).
    if (priv) {
      await send(priv.chat_id,
        `✅ گروهِ «${titleOf(chat)}» وصل شد. از این پس هشدارها در همان گروه می‌آید، نه این‌جا.\n`
        + 'اگر گروه را جدا کنید، هشدارها دوباره همین‌جا می‌آید.');
    }
    return sendMenu(await chatRow(row.chat_id),
      '✅ این گروه وصل شد. هشدارهای پمپ از این به بعد همین‌جا برای همه می‌آید.\n'
      + 'تنظیمات فقط در دستِ مدیرانِ گروه است.');
  }

  if (!isLinked(row)) {
    return ['start', 'menu', 'help'].includes(cmd.name) ? notLinked(row) : null;
  }
  if (cmd.name === 'status') return sendStatus(row);
  if (cmd.name === 'find') return onSearch(row, cmd.arg);
  if (cmd.name === 'code' || cmd.name === 'app') return sendApp(row);
  if (cmd.name === 'settings') {
    if (!(await canManage(row, from))) return send(row.chat_id, '⛔ تنظیماتِ بات فقط در دستِ مدیرانِ گروه است.');
    return sendSettings(row);
  }
  if (['start', 'menu', 'help'].includes(cmd.name)) return sendMenu(row);
  return null;
}

/* ── ورودی ─────────────────────────────────────────────────────── */

async function onMessage(m) {
  const chat = m.chat;
  if (!chat || chat.type === 'channel') return null;
  if (m.migrate_to_chat_id) return migrateChat(chat.id, m.migrate_to_chat_id);

  const cmd = parseCommand(m.text, await username());
  if (chat.type === 'group' || chat.type === 'supergroup') return onGroup(chat, cmd, m.from);
  if (chat.type !== 'private') return null;
  if (typeof m.text !== 'string' || !m.text.trim()) return null;
  return onPrivate(chat, cmd, m.text);
}

/** کارهایی که در گروه فقط مدیر (یا وصل‌کننده) می‌کند. */
const MANAGE = new Set(['settings', 'toggle', 'unlink', 'unlink_yes', 'code']);

async function onCallback(q) {
  const answer = (text = '', alert = false) => call('answerCallbackQuery', {
    callback_query_id: q.id, ...(text ? { text: String(text).slice(0, 190), show_alert: alert } : {}),
  });
  const chat = q.message?.chat;
  if (!chat) return answer();
  const row = await chatRow(chat.id);
  if (!row) return answer();
  const editId = Number(q.message?.message_id) || 0;

  if (MANAGE.has(q.data) && !(await canManage(row, q.from))) {
    return answer('⛔ فقط مدیرانِ همین گروه این را عوض می‌کنند.', true);
  }

  switch (q.data) {
    case 'menu': await answer(); return sendMenu(row, '', editId);
    case 'status': await answer(); return sendStatus(row, editId);
    case 'app': await answer(); return sendApp(row, editId);
    case 'code': {
      const link = await linkOf(row);
      if (!link) { await answer(); return notLinked(row, editId); }
      //  ⚠️ فقط برای همان کسی که زد — پنجرهٔ خصوصیِ تلگرام، نه پیامِ گروه
      return answer(`🔑 کدِ پمپِ «${link.station_name}»:  ${await accessCodeOf(link.station_id)}`, true);
    }
    case 'share':
    case 'group':
    case 'channel':
      await answer();
      return row.kind === 'private' ? sendShare(row, editId) : null;
    case 'settings': await answer(); return sendSettings(row, editId);
    case 'help':
      await answer();
      return show(row.chat_id, HELP, isLinked(row) ? [[BACK]] : welcomeKeyboard(), editId);
    case 'toggle': {
      if (!isLinked(row)) { await answer(); return notLinked(row, editId); }
      await query('UPDATE telegram_chats SET only_out = NOT only_out, updated_at=$2 WHERE chat_id=$1',
        [row.chat_id, now()]);
      await answer('✔️ ذخیره شد');
      return sendSettings(await chatRow(row.chat_id), editId, '✔️ ذخیره شد.');
    }
    case 'unlink':
      await answer();
      return isLinked(row) ? askUnlink(row, editId) : notLinked(row, editId);
    case 'unlink_yes': {
      await answer();
      await unlink(row.chat_id);
      if (row.kind !== 'private') {
        return show(row.chat_id, '🔌 این گروه از پمپ جدا شد. دیگر هیچ هشداری این‌جا نمی‌آید.', null, editId);
      }
      await setState(row.chat_id, 'email');
      return show(row.chat_id,
        '🔌 جدا شد. هر وقت خواستید دوباره وصل شوید، ایمیلِ حسابِ پمپ را بفرستید.', [downloadRow()], editId);
    }
    case 'reset': {
      await answer();
      if (row.kind !== 'private' || isLinked(row)) return null;
      await setState(row.chat_id, 'email');
      return show(row.chat_id, '✉️ ایمیلِ حسابِ پمپ را بفرستید.', null, editId);
    }
    default: return answer();
  }
}

/** بات را از گروه بیرون کردند، یا کاربر بات را بست — یا مدیرِ کانال شد. */
async function onMembership(u) {
  const status = u.new_chat_member?.status;
  if (status === 'left' || status === 'kicked') return forgetChat(u.chat.id);
  if (u.chat?.type === 'channel') {
    //  مدیر شد و می‌تواند بنویسد ⇒ وصل؛ مدیری بی «ارسالِ پیام» ⇒ فراموش
    const canPost = status === 'administrator' && u.new_chat_member?.can_post_messages !== false;
    return canPost ? onChannelAdmin(u) : forgetChat(u.chat.id);
  }
  return null;
}

/*
 *  ⛔ **هر به‌روزرسانی یک بار** — گزارشِ صاحب سامانه (۱۴۰۵/۰۷/۱۳، با عکس):
 *  «پیام‌ها را دو بار ارسال می‌کند.» ریشه در `start`/`reload` بود (پایین را
 *  ببینید)؛ این فقط کمربندِ دوم است: شمارهٔ به‌روزرسانی‌های اخیر نگه داشته
 *  می‌شود و تکراری همان‌جا رد می‌شود.
 */
const seenUpdates = new Set();
function firstTime(id) {
  if (id === undefined || id === null) return true;
  if (seenUpdates.has(id)) return false;
  seenUpdates.add(id);
  if (seenUpdates.size > 500) seenUpdates.delete(seenUpdates.values().next().value);
  return true;
}

/** یک «به‌روزرسانی»ِ تلگرام. هر خطا همین‌جا می‌ماند و بقیه را نمی‌شکند. */
async function handleUpdate(u) {
  if (!firstTime(u?.update_id)) return null;
  try {
    if (u.message) return await onMessage(u.message);
    if (u.callback_query) return await onCallback(u.callback_query);
    if (u.my_chat_member) return await onMembership(u.my_chat_member);
  } catch (err) {
    console.error('[telegram:update]', hide(err.message));
  }
  return null;
}

/* ══════════════════════════════════════════════════════════════════
   هشدار ⇒ صفِ فرستادن
   ══════════════════════════════════════════════════════════════════ */

function isOut(e) {
  return e.kind === 'stock_out' || (e.kind === 'debt' && e.data?.state === 'out');
}

function iconOf(e) {
  const out = isOut(e);
  const who = e.kind === 'debt' ? '👤' : '🛢️';
  return `${who}${out ? '🔴' : '🟡'}`;
}

/**
 * «📋 دستورِ کار» — کارهایی که همین حالا باید انجام شود.
 *
 * ⛔ هیچ قاعده‌ای این‌جا ساخته نمی‌شود: هر خط همان `a`ی است که برنامهٔ
 * پمپ کنارِ هر هشدار فرستاده (`StationSnapshot.Alerts`). این‌جا فقط کنارِ
 * هم چیده و تکراری‌ها یکی می‌شوند.
 */
function actionLines(actions) {
  const seen = new Set();
  const out = [];
  for (const a of actions) {
    const v = String(a || '').trim();
    if (!v || seen.has(v)) continue;
    seen.add(v);
    out.push(`▫️ ${v}`);
  }
  return out;
}

/** متنِ یک پیام برای یک دسته هشدار — یک پیام، نه یکی برای هر هشدار. */
function formatAlert(stationName, items) {
  const lines = items.map(e => `${iconOf(e)} ${e.title || e.body || ''}`);
  const todo = actionLines(items.map(e => e.data?.action));
  //  ⚠️ بی دکمه (قاعدهٔ ۳): هشدار خبر است، نه منو. راهِ جزئیات یک فرمان است.
  return `🚨 هشدارِ پمپِ «${stationName}»\n\n${lines.join('\n')}`
    + (todo.length ? `\n\n📋 دستورِ کار:\n${todo.join('\n')}` : '')
    + '\n\n📊 همهٔ هشدارهای باز: /status';
}

/** کلیدِ یک هشدار بی حالش — همان `pump-state.subjectOf`. */
function subjectOf(key) {
  return require('./pump-state').subjectOf(key);
}

/**
 * هشدارهایی که بسته شدند ⇒ «✅ برطرف شد» در همان گفت‌وگوها.
 *
 * خواستهٔ صاحب سامانه: «لایو آپدیت توی گروه». کارمندی که دیروز «اضافه
 * نده» شنید، باید امروز هم بشنود که آن حساب دوباره موجودی دارد — وگرنه
 * مشتری را بی‌دلیل برمی‌گرداند.
 *
 * صدا‌زننده `pump-state.publish` است. ⛔ هیچ‌وقت استثنا بیرون نمی‌دهد.
 * ⚠️ «تمام شد ⇒ کم مانده» برطرف شدن نیست، فقط عوض شدنِ حال است؛ آن را
 * خودِ `publish` کنار می‌گذارد (هشدارِ تازه‌اش جداگانه می‌رود).
 */
/**
 * گفت‌وگوهایی که هشدارِ یک پمپ به آن‌ها می‌رود — تنها جای این تصمیم.
 *
 * خواستهٔ صاحب سامانه: «وقتی گروه وصل کردم، پیام‌ها توی گروه بیاید؛ اگر
 * گروهی انتخاب نکردم، توی خودِ بات برای یارو.» پس گفت‌وگوی **خصوصیِ** کسی
 * که خودش گروه یا کانالی برای همین پمپ وصل کرده، هشدار نمی‌گیرد — همان خبر
 * در گروهش می‌آید و دو بار شنیدنش همان «تکرار» است.
 *
 * ⚠️ به‌ازای **هر نفر**، نه کلِ پمپ: عضوی که خودش گروهی وصل نکرده (شاید
 * در گروهِ صاحبِ پمپ هم نیست) هشدارش را همچنان در خصوصی می‌گیرد.
 * ⚠️ گروهی که صاحبش دیگر عضوِ پمپ نیست حساب نمی‌شود — وگرنه خصوصی ساکت
 * می‌ماند و گروه هم هیچ نمی‌گرفت.
 * ⛔ قیدِ ۳ سرِ جایش است: عضو، پمپ و حساب — همین لحظه.
 */
function alertChats(stationId) {
  return many(
    `SELECT c.chat_id, c.only_out, c.announced_day, s.name AS station_name
       FROM telegram_chats c
       JOIN stations s ON s.id=c.station_id AND s.status='active'
       JOIN station_members m ON m.station_id=c.station_id AND m.user_id=c.user_id AND m.status='active'
       JOIN users u ON u.id=c.user_id AND u.status='active'
      WHERE c.station_id=$1 AND c.linked_at IS NOT NULL
        AND NOT (c.kind='private' AND EXISTS (
          SELECT 1 FROM telegram_chats g
           WHERE g.station_id=c.station_id AND g.user_id=c.user_id
             AND g.kind IN ('group','channel') AND g.linked_at IS NOT NULL))`,
    [stationId]
  );
}

/** نامِ نخستین گروه/کانالی که این نفر برای این پمپ وصل کرده — یا تهی. */
async function groupOfUser(row) {
  if (!row || row.kind !== 'private' || !row.user_id || !row.station_id) return null;
  const g = await one(
    `SELECT title FROM telegram_chats
      WHERE station_id=$1 AND user_id=$2 AND kind IN ('group','channel') AND linked_at IS NOT NULL
      ORDER BY linked_at LIMIT 1`,
    [row.station_id, row.user_id]
  );
  return g ? (g.title || 'گروهِ شما') : null;
}

async function notifyResolved(stationId, closed) {
  try {
    const items = (closed || []).filter(a => a && a.k);
    if (!items.length || !stationId) return { queued: 0 };
    if (!(await active())) return { queued: 0, skipped: 'off' };
    const chats = await alertChats(stationId);
    const t = now();
    let queued = 0;
    for (const c of chats) {
      const mine = c.only_out ? items.filter(a => a.s === 'out') : items;
      if (!mine.length) continue;
      const lines = mine.map((a) => (String(a.k).startsWith('tank-')
        ? `🛢️🟢 ${a.n || 'مخزن'} — دیگر کم نیست`
        : `👤🟢 ${a.n}${a.f ? ` — ${a.f}` : ''}: دیگر هشدار ندارد`));
      await queueLong(c.chat_id, stationId, `✅ برطرف شد — پمپِ «${c.station_name}»\n\n${lines.join('\n')}`, t);
      queued += 1;
    }
    if (queued) kick();
    return { queued };
  } catch (err) {
    console.error('[telegram:resolved]', hide(err.message));
    return { queued: 0, error: err.message };
  }
}

/**
 * هشدارهای تازهٔ یک پمپ ⇒ صفِ تلگرامِ هر گفت‌وگوی وصل‌شده.
 *
 * صدا‌زننده `events.notify` است، درست کنارِ پوش.
 *
 * ⛔ **هیچ‌وقت استثنا بیرون نمی‌دهد.** ثبتِ خبر کارِ اصلی است و تلگرام
 * رفاه — همان قاعدهٔ پوش.
 */
async function notifyStation(stationId, saved) {
  try {
    const worthy = (saved || []).filter(e => ALERT_KINDS.includes(e.kind));
    if (!worthy.length || !stationId) return { queued: 0 };
    if (!(await active())) return { queued: 0, skipped: 'off' };

    //  ⛔ قیدِ ۳: عضو، پمپ و حساب — همین لحظه
    const chats = await alertChats(stationId);
    const t = now();
    let queued = 0;
    for (const c of chats) {
      const items = c.only_out ? worthy.filter(isOut) : worthy;
      if (!items.length) continue;
      await queueLong(c.chat_id, stationId, formatAlert(c.station_name, items), t);
      queued += 1;
    }
    if (queued) kick();
    return { queued };
  } catch (err) {
    console.error('[telegram:notify]', hide(err.message));
    return { queued: 0, error: err.message };
  }
}

/* ══════════════════════════════════════════════════════════════════
   📢 اعلامیهٔ صبح — «یکم ریس‌مدلی»
   ══════════════════════════════════════════════════════════════════

   خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۴): «یک اعلامیه بده که تیل بگیرید یا به
   یارو تیل ندید یا از شرکت انقد قرض‌دار استین… یکم به فکرِ پمپ هم باش.»

   هر روز یک بار، از ساعتِ ۸ صبحِ کابل، به همان گفت‌وگوهایی که هشدار
   می‌گیرند (`alertChats` — گروهِ وصل ⇒ فقط گروه). ⛔ هیچ عددی این‌جا حساب
   نمی‌شود: مخزن، هشدارها و بدهیِ شرکت‌ها همه از حالِ زنده‌ای است که
   برنامهٔ پمپ فرستاده؛ این‌جا فقط چیده می‌شوند. */

const ANNOUNCE_HOUR = 8;
const KABUL_MS = 4.5 * 3600 * 1000;
const kabulDay = (t) => new Date(t + KABUL_MS).toISOString().slice(0, 10);
const kabulHour = (t) => new Date(t + KABUL_MS).getUTCHours();

/** حالِ یک هشدارِ قرض‌دار از کلیدش — همان پسوندهای `StationSnapshot.Alerts`. */
function levelOf(k) {
  const m = /-(out|low|w70|w90|over)$/.exec(String(k || ''));
  return m ? m[1] : '';
}

function announcementText(stationName, state, t = now()) {
  const parts = [`📢 اعلامیهٔ امروزِ پمپِ «${stationName}»`, 'صبح بخیر، تیم! اول این‌ها را ببینید:'];
  if (t - state.updatedAt > STALE_MS) {
    parts.push('', `⚠️ برنامهٔ کامپیوتر ${ago(state.updatedAt)} آخرین بار خبر داد — اول آن را روشن کنید تا عددها تازه باشند.`);
  }
  const tanks = tankLines(state.tank);
  if (tanks.length) parts.push('', '🛢️ مخزن:', ...tanks);

  const debts = state.alerts.filter(a => !String(a.k).startsWith('tank-'));
  const groups = [
    ['over', '🔴 بیشتر از حسابشان تیل/پول برده‌اند — بازپرسی شود:'],
    ['out', '🔴 حسابشان تمام شده — دیگر ندهید:'],
    ['w90', '🟡 ۹۰٪ِ حسابشان رفته — بگویید حساب را پر کنند:'],
    ['low', '🟡 حسابشان کم مانده:'],
    ['w70', '🟡 ۷۰٪ِ حسابشان رفته — متوجه باشید:'],
  ];
  const debtParts = [];
  for (const [lv, label] of groups) {
    const names = debts.filter(a => levelOf(a.k) === lv).map(a => `${a.n}${a.f ? ` (${a.f})` : ''}`);
    if (names.length) debtParts.push(label, `   ${names.join('، ')}`);
  }
  if (debtParts.length) parts.push('', '👥 قرض‌داران:', ...debtParts);

  const owe = oweLines(state.owe);
  if (owe.length) parts.push('', '💼 بدهیِ پمپ به شرکت‌ها:', ...owe);

  const todo = actionLines(state.alerts.map(a => a.a));
  if (owe.length) todo.push('▫️ بدهیِ شرکت‌ها را فراموش نکنید؛ پیش از خریدِ تازه، تسویه را برنامه بریزید.');
  if (todo.length) {
    parts.push('', '📋 دستورِ کارِ امروز:', ...todo);
    parts.push('', '💪 با دقت کار کنید — هر لیتر حساب دارد.');
  } else {
    parts.push('', '✅ همه‌چیز رو‌به‌راه است: مخزن‌ها سالم‌اند و هیچ قرض‌داری از حسابش بیرون نزده. همین‌طور ادامه بدهید.');
  }
  return parts.join('\n');
}

let lastAnnounceTry = 0;

/**
 * اعلامیهٔ صبح برای هر گفت‌وگویی که امروز هنوز نگرفته است.
 * ⛔ هیچ‌وقت استثنا بیرون نمی‌دهد. `t` فقط برای آزمون.
 */
async function announceTick(t = now()) {
  try {
    if (!(await active())) return { queued: 0, skipped: 'off' };
    if (kabulHour(t) < ANNOUNCE_HOUR) return { queued: 0, skipped: 'early' };
    const day = kabulDay(t);
    const stations = await many('SELECT station_id FROM station_live_state');
    const ps = require('./pump-state');
    let queued = 0;
    for (const { station_id: sid } of stations) {
      const chats = (await alertChats(sid)).filter(c => c.announced_day !== day);
      if (!chats.length) continue;
      const state = await ps.get(sid);
      if (!state) continue;
      for (const c of chats) {
        //  ⚠️ اول مُهر، بعد صف: دو دورِ هم‌زمان هرگز دو اعلامیه نمی‌سازند
        const took = await one(
          `UPDATE telegram_chats SET announced_day=$2
            WHERE chat_id=$1 AND announced_day IS DISTINCT FROM $2 RETURNING chat_id`,
          [c.chat_id, day]
        );
        if (!took) continue;
        await queueLong(c.chat_id, sid, announcementText(c.station_name, state, t), now());
        queued += 1;
      }
    }
    if (queued) kick();
    return { queued };
  } catch (err) {
    console.error('[telegram:announce]', hide(err.message));
    return { queued: 0, error: err.message };
  }
}

let flushing = false;
let again = false;

/**
 * فرستادنِ صف.
 *
 * - ۴۲۹: همان‌قدر که تلگرام گفت صبر، و این دور تمام.
 * - ۴۰۳ یا «گفت‌وگو نیست»: بات را بسته‌اند یا بیرون کرده‌اند ⇒ گفت‌وگو فراموش.
 * - گروهی که ابرگروه شد: شناسهٔ تازه و دوباره.
 * - بقیه (و قطعیِ اینترنت): دوباره، با فاصلهٔ دوبرابرشونده، تا هشت بار —
 *   و هشداری که یک روز نرفت دیگر کهنه است.
 */
async function flushOutbox({ limit = 25 } = {}) {
  if (flushing) { again = true; return { sent: 0, failed: 0 }; }
  flushing = true;
  let sent = 0;
  let failed = 0;
  try {
    if (!(await token())) return { sent, failed };
    const t = now();
    await query(
      `UPDATE telegram_outbox SET failed_at=$1, error=CASE WHEN error='' THEN 'کهنه شد' ELSE error END
        WHERE sent_at IS NULL AND failed_at IS NULL AND created_at < $2`,
      [t, t - GIVE_UP_MS]
    );
    const rows = await many(
      `SELECT * FROM telegram_outbox
        WHERE sent_at IS NULL AND failed_at IS NULL AND next_at <= $1
        ORDER BY created_at LIMIT $2`,
      [t, limit]
    );

    for (const r of rows) {
      const res = await send(r.chat_id, r.body);
      if (res.ok) {
        await query('UPDATE telegram_outbox SET sent_at=$2, error=\'\' WHERE id=$1', [r.id, now()]);
        sent += 1;
        bot.lastOkAt = now();
        continue;
      }
      const code = Number(res.error_code) || 0;
      const desc = hide(res.description || 'نرفت');
      const migrate = res.parameters?.migrate_to_chat_id;
      if (migrate) {
        await migrateChat(r.chat_id, migrate);
        continue;                                  // دورِ بعد با شناسهٔ تازه
      }
      if (code === 429) {
        const wait = (Number(res.parameters?.retry_after) || 5) * 1000;
        await query('UPDATE telegram_outbox SET next_at=$2, error=$3 WHERE id=$1', [r.id, now() + wait, desc]);
        break;
      }
      if (code === 403 || (code === 400 && /chat not found/i.test(desc))) {
        await query('UPDATE telegram_outbox SET failed_at=$2, error=$3 WHERE id=$1', [r.id, now(), desc]);
        await forgetChat(r.chat_id);
        failed += 1;
        continue;
      }
      const attempts = (Number(r.attempts) || 0) + 1;
      if (attempts >= MAX_ATTEMPTS) {
        await query('UPDATE telegram_outbox SET attempts=$2, failed_at=$3, error=$4 WHERE id=$1',
          [r.id, attempts, now(), desc]);
        failed += 1;
      } else {
        const backoff = Math.min(30_000 * 2 ** (attempts - 1), 30 * 60_000);
        await query('UPDATE telegram_outbox SET attempts=$2, next_at=$3, error=$4 WHERE id=$1',
          [r.id, attempts, now() + backoff, desc]);
      }
      bot.lastError = desc;
    }

    await query(
      `DELETE FROM telegram_outbox
        WHERE (sent_at IS NOT NULL AND sent_at < $1) OR (failed_at IS NOT NULL AND failed_at < $1)`,
      [t - KEEP_MS]
    );
  } catch (err) {
    bot.lastError = hide(err.message);
    console.error('[telegram:outbox]', bot.lastError);
  } finally {
    flushing = false;
  }
  if (again) { again = false; if (bot.running) kick(); }
  return { sent, failed };
}

function kick() {
  if (!bot.running) return;
  setImmediate(() => { flushOutbox().catch(() => {}); });
}

/* ══════════════════════════════════════════════════════════════════
   حلقهٔ گرفتنِ پیام
   ══════════════════════════════════════════════════════════════════ */

/** یک دورِ `getUpdates`. شمارهٔ پیام پس از **هر** پیام ذخیره می‌شود. */
async function pollOnce({ timeout = 25 } = {}) {
  const offset = Number(await plans.getConfig(CFG_OFFSET, '0')) || 0;
  const res = await call('getUpdates', {
    offset,
    timeout,
    allowed_updates: ['message', 'callback_query', 'my_chat_member'],
  }, { timeoutMs: (timeout + 15) * 1000 });
  if (!res.ok) {
    const err = new Error(hide(res.description || 'getUpdates نشد'));
    err.tg = res;
    throw err;
  }
  let next = offset;
  for (const u of res.result || []) {
    next = Math.max(next, Number(u.update_id) + 1);
    await handleUpdate(u);
    await plans.setConfig(CFG_OFFSET, String(next));
  }
  bot.lastOkAt = now();
  bot.lastError = '';
  return (res.result || []).length;
}

let stopper = new AbortController();
function stopSignal() { return stopper.signal; }

function sleep(ms) {
  return new Promise((resolve) => {
    const timer = setTimeout(resolve, ms);
    timer.unref?.();
    stopper.signal.addEventListener('abort', () => { clearTimeout(timer); resolve(); }, { once: true });
  });
}

let outboxTimer = null;

/*
 *  ⛔ **یک حلقه، نه دو** — ریشهٔ «پیام‌ها دو بار می‌روند».
 *
 *  `reload()` (ذخیرهٔ رمز یا روشن/خاموش در پنل) `stop()` و بی‌درنگ
 *  `start()` می‌زد. حلقهٔ قبلی هنوز وسطِ `getUpdates` بود؛ `start()`
 *  `bot.running` را دوباره راست می‌کرد، پس حلقهٔ قبلی هم **زنده** ادامه
 *  می‌داد — دو حلقه، یک صندوق، هر پیام دو پاسخ. حالا هر حلقه شمارهٔ نسلِ
 *  خودش را دارد و با `start()`ِ تازه کنار می‌رود.
 */
let generation = 0;

/** فرمان‌های دکمهٔ «منو»ی تلگرام — «منو نداره». */
async function setCommands() {
  await call('setMyCommands', {
    scope: { type: 'all_private_chats' },
    commands: [
      { command: 'menu', description: 'منو' },
      { command: 'status', description: 'هشدارهای باز و موجودیِ مخزن' },
      { command: 'find', description: 'جست‌وجوی قرض‌دار (یا فقط نام را بنویسید)' },
      { command: 'code', description: 'اپِ اندروید و آیفون و کدِ پمپ' },
      { command: 'group', description: 'وصل کردنِ گروه یا کانال' },
      { command: 'settings', description: 'تنظیمات' },
      { command: 'help', description: 'راهنما' },
    ],
  });
  await call('setMyCommands', {
    scope: { type: 'all_group_chats' },
    commands: [
      { command: 'status', description: 'هشدارهای باز و موجودیِ مخزن' },
      { command: 'find', description: 'جست‌وجوی قرض‌دار: /find نام' },
      { command: 'menu', description: 'منو' },
    ],
  });
  await call('setChatMenuButton', { menu_button: { type: 'commands' } });
  //  نوشتهٔ «پروفایلِ» بات — همان چیزی که پیش از Start دیده می‌شود
  await call('setMyShortDescription', {
    short_description: 'هشدارهای پمپ: حسابِ قرض‌دار تمام شد، کم مانده، مخزن ته کشید — حتی وقتی برنامه بسته است.',
  });
}

/** راه‌اندازی — از `index.js`. تا رمز داده نشده، فقط هر چند ثانیه نگاه می‌کند. */
function start() {
  if (bot.running) return;
  bot.running = true;
  bot.startedAt = now();
  stopper = new AbortController();
  const mine = ++generation;
  const alive = () => bot.running && mine === generation;

  (async () => {
    let backoff = 2000;
    let prepared = '';
    while (alive()) {
      try {
        if (!(await active())) { await sleep(10_000); continue; }
        const tok = await token();
        if (prepared !== tok) {
          //  webhookِ جامانده نمی‌گذارد getUpdates کار کند (۴۰۹)
          await call('deleteWebhook', { drop_pending_updates: false });
          const me = await refreshMe();
          if (!me.ok) throw new Error(me.description || 'رمزِ بات پذیرفته نشد');
          await setCommands().catch(() => {});
          prepared = tok;
        }
        if (!alive()) break;
        await pollOnce();
        backoff = 2000;
      } catch (err) {
        if (!alive()) break;
        bot.lastError = hide(err.message);
        await sleep(backoff);
        backoff = Math.min(backoff * 2, 60_000);
      }
    }
  })();

  outboxTimer = setInterval(() => {
    flushOutbox().catch(() => {});
    //  اعلامیهٔ صبح — دقیقه‌ای یک بار سنجیده می‌شود، روزی یک بار می‌رود
    if (now() - lastAnnounceTry >= 60_000) { lastAnnounceTry = now(); announceTick().catch(() => {}); }
  }, 5000);
  outboxTimer.unref?.();
}

function stop() {
  bot.running = false;
  generation++;
  stopper.abort();
  if (outboxTimer) clearInterval(outboxTimer);
  outboxTimer = null;
}

/** رمز عوض شد: دورِ جاری بریده می‌شود تا رمزِ تازه همان لحظه بنشیند. */
function reload() {
  tokenCache = null;
  bot.username = '';
  if (bot.running) { stop(); start(); }
}

/* ══════════════════════════════════════════════════════════════════
   پنلِ مدیر و درِ عمومی
   ══════════════════════════════════════════════════════════════════ */

/** حالِ بات برای پنل. ⛔ رمز هرگز — فقط این‌که هست و چهار نویسهٔ آخرش. */
async function status() {
  const tok = await token();
  const [chats, outbox] = await Promise.all([
    one(`SELECT
           COUNT(*) FILTER (WHERE kind='private' AND linked_at IS NOT NULL)::int AS private,
           COUNT(*) FILTER (WHERE kind='group' AND linked_at IS NOT NULL)::int AS groups,
           COUNT(*) FILTER (WHERE kind='channel' AND linked_at IS NOT NULL)::int AS channels
         FROM telegram_chats`),
    one(`SELECT
           COUNT(*) FILTER (WHERE sent_at IS NULL AND failed_at IS NULL)::int AS pending,
           COUNT(*) FILTER (WHERE sent_at IS NOT NULL AND sent_at > $1)::int AS sent,
           COUNT(*) FILTER (WHERE failed_at IS NOT NULL AND failed_at > $1)::int AS failed
         FROM telegram_outbox`, [now() - 24 * 3600 * 1000]),
  ]);
  const name = await username();
  return {
    configured: Boolean(tok),
    fromEnv: Boolean(config.telegram.token),
    tokenHint: tok ? `…${tok.slice(-4)}` : '',
    enabled: await enabled(),
    running: bot.running,
    username: name,
    link: name ? `https://t.me/${name}` : '',
    lastError: bot.lastError,
    lastOkAt: bot.lastOkAt,
    chats: { private: chats?.private || 0, groups: chats?.groups || 0, channels: chats?.channels || 0 },
    outbox: { pending: outbox?.pending || 0, sent24h: outbox?.sent || 0, failed24h: outbox?.failed || 0 },
    links: appLinks(),
  };
}

/**
 * ذخیرهٔ تنظیم از پنل.
 *
 * رمزِ تازه اول از خودِ تلگرام پرسیده می‌شود: رمزِ غلط ذخیره نمی‌شود. اگر
 * تلگرام همان لحظه در دسترس نبود، ذخیره می‌شود و بعداً امتحان می‌شود —
 * و پاسخ همین را می‌گوید.
 */
async function configure({ token: raw, clearToken = false, enabled: on } = {}) {
  const out = { checked: false, warning: '' };
  if (clearToken) {
    await plans.setConfig(CFG_TOKEN, '');
    await plans.setConfig(CFG_USERNAME, '');
  } else if (typeof raw === 'string' && raw.trim()) {
    const tok = raw.trim();
    if (!/^\d{5,}:[A-Za-z0-9_-]{30,}$/.test(tok)) {
      const e = new Error('رمزِ بات شکلِ درستی ندارد (از BotFather: «123456:ABC…»)');
      e.code = 'bad_token';
      throw e;
    }
    const me = await call('getMe', {}, { tok });
    if (me.ok) {
      out.checked = true;
      await plans.setConfig(CFG_USERNAME, String(me.result?.username || ''));
    } else if (me.error_code === 401 || me.error_code === 404) {
      const e = new Error('تلگرام این رمز را نپذیرفت');
      e.code = 'token_rejected';
      throw e;
    } else {
      out.warning = 'تلگرام همین حالا در دسترس نبود؛ رمز ذخیره شد و سرور خودش دوباره امتحان می‌کند.';
    }
    await plans.setConfig(CFG_TOKEN, await stations.encryptKey(tok));
    await plans.setConfig(CFG_OFFSET, '0');
  }
  if (typeof on === 'boolean') await plans.setConfig(CFG_ENABLED, on ? '1' : '0');
  reload();
  return out;
}

/** برای برنامهٔ پمپ و اپِ کارمندان: بات هست؟ نامش چیست؟ ⛔ هیچ رازی. */
async function publicInfo() {
  const on = await active();
  const name = on ? await username() : '';
  return { enabled: Boolean(on && name), username: name, url: name ? `https://t.me/${name}` : '' };
}

/** فقط آزمون: همه‌چیزِ حافظه از نو. */
function _reset() {
  seenUpdates.clear();
  adminCache.clear();
  tokenCache = null;
  bot.username = '';
  bot.lastError = '';
  bot.lastOkAt = 0;
}

module.exports = {
  PURPOSE, ALERT_KINDS,
  setTransport, handleUpdate, pollOnce, flushOutbox, notifyStation,
  start, stop, reload, status, configure, publicInfo, appLinks,
  formatAlert, announcementText, announceTick, parseCommand, asciiDigits, normEmail, normName, notifyResolved, searchText, chunks,
  _reset,
};
