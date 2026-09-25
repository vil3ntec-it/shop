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
const MAX_LINES = 15;

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
   دکمه‌ها
   ══════════════════════════════════════════════════════════════════ */

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
    { text: '📱 اپِ اندروید', url: l.android },
    { text: '🍎 آیفون', url: l.iphone },
  ];
}

function menuKeyboard(row) {
  const kb = [
    [{ text: '📊 وضعیت و آخرین هشدارها', callback_data: 'status' }],
    downloadRow(),
  ];
  if (row.kind === 'private') kb.push([{ text: '👥 وصل کردنِ گروهِ تلگرام', callback_data: 'group' }]);
  kb.push([{
    text: row.only_out ? '🔔 «کم مانده» را هم بفرست' : '🔕 فقط «تمام شد» را بفرست',
    callback_data: 'toggle',
  }]);
  if (row.kind === 'private') kb.push([{ text: '🔌 قطعِ اتصال', callback_data: 'unlink' }]);
  return kb;
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

/* ── خصوصی ─────────────────────────────────────────────────────── */

const WELCOME =
  'سلام 👋 این باتِ هشدارهای پمپ است.\n\n'
  + 'هر وقت حسابِ قرض‌داری تمام شود یا کم بماند، یا مخزن ته بکشد، همین‌جا خبر می‌گیرید — '
  + 'حتی وقتی برنامه بسته است.\n\n'
  + '✉️ برای وصل شدن، ایمیلِ حسابِ پمپتان را بفرستید (همان که در برنامهٔ پمپ با آن وارد شده‌اید).\n'
  + 'یک کدِ شش‌رقمی به همان ایمیل می‌رود؛ کد را همین‌جا بفرستید و تمام.';

async function sendMenu(row, lead = '') {
  const link = await linkOf(row);
  if (!link) {
    if (isLinked(row)) await unlink(row.chat_id);
    if (row.kind === 'private') {
      await setState(row.chat_id, 'email');
      return send(row.chat_id,
        (isLinked(row) ? 'این گفت‌وگو دیگر به هیچ پمپی وصل نیست — حساب از آن پمپ بیرون شده است.\n\n' : '')
        + WELCOME, [downloadRow()]);
    }
    return send(row.chat_id,
      'این گروه به هیچ پمپی وصل نیست. در گفت‌وگوی خصوصی با بات، «👥 وصل کردنِ گروهِ تلگرام» را بزنید.');
  }
  const text = (lead ? `${lead}\n\n` : '')
    + `⛽ پمپِ «${link.station_name}»\n`
    + (row.only_out ? '🔕 فقط هشدارهای «تمام شد» می‌آید.' : '🔔 همهٔ هشدارها می‌آید: «تمام شد» و «کم مانده».');
  return send(row.chat_id, text, menuKeyboard(row));
}

async function sendStatus(row) {
  const link = await linkOf(row);
  if (!link) return sendMenu(row);
  const list = await many(
    `SELECT kind, title, data, created_at FROM station_events
      WHERE station_id=$1 AND kind = ANY($2::text[])
      ORDER BY created_at DESC LIMIT 8`,
    [link.station_id, ALERT_KINDS]
  );
  const lines = list.map(e => `${iconOf(e)} ${e.title || ''} — ${ago(e.created_at)}`);
  const text = `📊 پمپِ «${link.station_name}»\n\n`
    + (lines.length ? `آخرین هشدارها:\n${lines.join('\n')}` : 'هنوز هیچ هشداری نیامده است. ✅');
  return send(row.chat_id, text, menuKeyboard(row));
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
  if (member) {
    try {
      await require('./otp').request(email, { purpose: PURPOSE, app: 'pump' });
    } catch (err) {
      if (err.code === 'otp_resend_wait') {
        return send(row.chat_id, `${err.message}. اگر کدِ قبلی رسیده، همان را بفرستید.`,
          [[{ text: '✉️ ایمیلِ دیگر', callback_data: 'reset' }]]);
      }
      if (err.code === 'otp_daily_limit') return send(row.chat_id, err.message);
      console.error('[telegram] کد نرفت:', err.message);
      return send(row.chat_id,
        'کد به ایمیل فرستاده نشد. کمی بعد دوباره امتحان کنید؛ اگر باز هم نشد، به مدیرِ سامانه بگویید.');
    }
  }

  return send(row.chat_id,
    `اگر «${email}» حسابِ پمپ داشته باشد، یک کدِ شش‌رقمی همین حالا به آن رفت `
    + '(پوشهٔ اسپم را هم ببینید).\n\n🔢 کد را همین‌جا بفرستید.',
    [[{ text: '✉️ ایمیلِ دیگر', callback_data: 'reset' }]]);
}

/** گامِ کد. ⛔ حسابِ ناموجود هم فقط «کد درست نیست» می‌گیرد. */
async function onCode(row, text) {
  const digits = asciiDigits(text).replace(/\D/g, '');
  if (digits.length !== 6) {
    return send(row.chat_id, 'کدِ شش‌رقمیِ ایمیل را بفرستید — یا «✉️ ایمیلِ دیگر» را بزنید.',
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
    if (cmd.name === 'status' && linked) return sendStatus(row);
    if (cmd.name === 'stop' && linked) return askUnlink(row);
    if (linked) return sendMenu(row);
    await setState(row.chat_id, 'email');
    return send(row.chat_id, WELCOME, [downloadRow()]);
  }

  if (linked) return sendMenu(row);
  //  ایمیلِ تازه در گامِ کد یعنی «این یکی را می‌خواهم»
  if (row.state === 'code' && !normEmail(text)) return onCode(row, text);
  row = { ...row, state: 'email' };
  return onEmail(row, text);
}

function askUnlink(row) {
  return send(row.chat_id,
    'این گفت‌وگو از پمپ جدا شود؟ دیگر هیچ هشداری این‌جا نمی‌آید. (گروه‌هایی که وصل کرده‌اید جدا وصل‌اند.)',
    [[
      { text: '🔌 بله، جدا کن', callback_data: 'unlink_yes' },
      { text: 'نه', callback_data: 'menu' },
    ]]);
}

/* ── گروه ─────────────────────────────────────────────────────── */

/**
 * نشانیِ یک‌بارمصرفِ «افزودن به گروه» (قیدِ ۴).
 *
 * `t.me/<بات>?startgroup=<نشانه>` ⇒ کاربر گروه را برمی‌گزیند ⇒ تلگرام در
 * همان گروه `/start <نشانه>` را برای بات می‌فرستد.
 */
async function groupLink(row) {
  const link = await linkOf(row);
  if (!link) return sendMenu(row);
  const name = await username();
  if (!name) return send(row.chat_id, 'نامِ بات هنوز از تلگرام نیامده است؛ چند لحظه بعد دوباره بزنید.');

  const raw = crypto.randomBytes(18).toString('base64url');
  const t = now();
  await query(
    `INSERT INTO telegram_link_tokens (token_hash, station_id, user_id, expires_at, created_at)
     VALUES ($1,$2,$3,$4,$5)`,
    [hashToken(raw), row.station_id, row.user_id, t + LINK_TTL_MS, t]
  );
  //  نشانه‌های کهنه همان‌جا جارو می‌شوند — این جدول نباید بزرگ شود
  await query('DELETE FROM telegram_link_tokens WHERE expires_at < $1', [t - LINK_TTL_MS]);

  return send(row.chat_id,
    `👥 گروهی که می‌خواهید هشدارهای پمپِ «${link.station_name}» در آن بیاید:\n\n`
    + '۱) دکمهٔ زیر را بزنید\n۲) گروه را برگزینید\n۳) تمام — بات خودش خبر می‌دهد.\n\n'
    + '⏳ این دکمه پانزده دقیقه و فقط یک بار کار می‌کند.',
    [[{ text: '➕ افزودن به گروه', url: `https://t.me/${name}?startgroup=${raw}` }]]);
}

async function onGroup(chat, cmd) {
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
        'این دکمه باطل یا کهنه شده است. در گفت‌وگوی خصوصی با بات دوباره «👥 وصل کردنِ گروهِ تلگرام» را بزنید.');
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
    return sendMenu(await chatRow(row.chat_id),
      '✅ این گروه وصل شد. هشدارهای پمپ از این به بعد همین‌جا برای همه می‌آید.\n'
      + 'برای جدا کردن، کافی است بات را از گروه بیرون کنید.');
  }

  if (cmd.name === 'status' && isLinked(row)) return sendStatus(row);
  if (['start', 'menu', 'help'].includes(cmd.name)) return sendMenu(row);
  return null;
}

/* ── ورودی ─────────────────────────────────────────────────────── */

async function onMessage(m) {
  const chat = m.chat;
  if (!chat || chat.type === 'channel') return null;
  if (m.migrate_to_chat_id) return migrateChat(chat.id, m.migrate_to_chat_id);

  const cmd = parseCommand(m.text, await username());
  if (chat.type === 'group' || chat.type === 'supergroup') return onGroup(chat, cmd);
  if (chat.type !== 'private') return null;
  if (typeof m.text !== 'string' || !m.text.trim()) return null;
  return onPrivate(chat, cmd, m.text);
}

async function onCallback(q) {
  await call('answerCallbackQuery', { callback_query_id: q.id });
  const chat = q.message?.chat;
  if (!chat) return null;
  const row = await chatRow(chat.id);
  if (!row) return null;

  switch (q.data) {
    case 'menu': return sendMenu(row);
    case 'status': return sendStatus(row);
    case 'group': return row.kind === 'private' ? groupLink(row) : null;
    case 'toggle': {
      if (!isLinked(row)) return sendMenu(row);
      await query('UPDATE telegram_chats SET only_out = NOT only_out, updated_at=$2 WHERE chat_id=$1',
        [row.chat_id, now()]);
      return sendMenu(await chatRow(row.chat_id), '✔️ ذخیره شد.');
    }
    case 'unlink': return row.kind === 'private' && isLinked(row) ? askUnlink(row) : null;
    case 'unlink_yes': {
      if (row.kind !== 'private') return null;
      await unlink(row.chat_id);
      await setState(row.chat_id, 'email');
      return send(row.chat_id,
        '🔌 جدا شد. هر وقت خواستید دوباره وصل شوید، ایمیلِ حسابِ پمپ را بفرستید.', [downloadRow()]);
    }
    case 'reset': {
      if (row.kind !== 'private' || isLinked(row)) return null;
      await setState(row.chat_id, 'email');
      return send(row.chat_id, '✉️ ایمیلِ حسابِ پمپ را بفرستید.');
    }
    default: return null;
  }
}

/** بات را از گروه بیرون کردند، یا کاربر بات را بست. */
async function onMembership(u) {
  const status = u.new_chat_member?.status;
  if (status === 'left' || status === 'kicked') return forgetChat(u.chat.id);
  return null;
}

/** یک «به‌روزرسانی»ِ تلگرام. هر خطا همین‌جا می‌ماند و بقیه را نمی‌شکند. */
async function handleUpdate(u) {
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

/** متنِ یک پیام برای یک دسته هشدار — یک پیام، نه یکی برای هر هشدار. */
function formatAlert(stationName, items) {
  const shown = items.slice(0, MAX_LINES);
  const lines = shown.map(e => `${iconOf(e)} ${e.title || e.body || ''}`);
  if (items.length > shown.length) lines.push(`… و ${faNum(items.length - shown.length)} هشدارِ دیگر`);
  return `🚨 هشدارِ پمپِ «${stationName}»\n\n${lines.join('\n')}`;
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
    const chats = await many(
      `SELECT c.chat_id, c.only_out, s.name AS station_name
         FROM telegram_chats c
         JOIN stations s ON s.id=c.station_id AND s.status='active'
         JOIN station_members m ON m.station_id=c.station_id AND m.user_id=c.user_id AND m.status='active'
         JOIN users u ON u.id=c.user_id AND u.status='active'
        WHERE c.station_id=$1 AND c.linked_at IS NOT NULL`,
      [stationId]
    );
    const t = now();
    let queued = 0;
    for (const c of chats) {
      const items = c.only_out ? worthy.filter(isOut) : worthy;
      if (!items.length) continue;
      await query(
        `INSERT INTO telegram_outbox (id, chat_id, station_id, body, next_at, created_at)
         VALUES ($1,$2,$3,$4,$5,$5)`,
        [newId('tgo'), c.chat_id, stationId, formatAlert(c.station_name, items), t]
      );
      queued += 1;
    }
    if (queued) kick();
    return { queued };
  } catch (err) {
    console.error('[telegram:notify]', hide(err.message));
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

/** راه‌اندازی — از `index.js`. تا رمز داده نشده، فقط هر چند ثانیه نگاه می‌کند. */
function start() {
  if (bot.running) return;
  bot.running = true;
  bot.startedAt = now();
  stopper = new AbortController();

  (async () => {
    let backoff = 2000;
    let prepared = '';
    while (bot.running) {
      try {
        if (!(await active())) { await sleep(10_000); continue; }
        const tok = await token();
        if (prepared !== tok) {
          //  webhookِ جامانده نمی‌گذارد getUpdates کار کند (۴۰۹)
          await call('deleteWebhook', { drop_pending_updates: false });
          const me = await refreshMe();
          if (!me.ok) throw new Error(me.description || 'رمزِ بات پذیرفته نشد');
          prepared = tok;
        }
        await pollOnce();
        backoff = 2000;
      } catch (err) {
        if (!bot.running) break;
        bot.lastError = hide(err.message);
        await sleep(backoff);
        backoff = Math.min(backoff * 2, 60_000);
      }
    }
  })();

  outboxTimer = setInterval(() => { flushOutbox().catch(() => {}); }, 5000);
  outboxTimer.unref?.();
}

function stop() {
  bot.running = false;
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
           COUNT(*) FILTER (WHERE kind='group' AND linked_at IS NOT NULL)::int AS groups
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
    chats: { private: chats?.private || 0, groups: chats?.groups || 0 },
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
  tokenCache = null;
  bot.username = '';
  bot.lastError = '';
  bot.lastOkAt = 0;
}

module.exports = {
  PURPOSE, ALERT_KINDS,
  setTransport, handleUpdate, pollOnce, flushOutbox, notifyStation,
  start, stop, reload, status, configure, publicInfo, appLinks,
  formatAlert, parseCommand, asciiDigits, normEmail,
  _reset,
};
