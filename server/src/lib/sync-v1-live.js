'use strict';
/**
 * `WSS /sync/live` — خبرِ «چیزی عوض شد» به دستگاه‌های دیگرِ همان حساب.
 *
 * ── چه کاری می‌کند و چه کاری نه ────────────────────────────────────
 * روی خط فقط یک پیامِ کوچک می‌رود: `{"event":"changed","cursor":N}`.
 * خودِ داده هرگز از این در نمی‌رود — برنامه بعد از این پیام `pull`
 * می‌کند. اگر سوکت قطع بود، هر ۳۰ ثانیه pull (کارِ برنامه).
 *
 * ── اتاق‌ها ─────────────────────────────────────────────────────────
 * هر حساب یک اتاق دارد (`app:kind:id`). ورود (F0-11، ۱۴۰۵/۰۷/۲۲):
 *   • راهِ درست: `POST /api/sync/v1/live/ticket` با همان توکنِ push در
 *     سرآیند ⇒ «بلیتِ» یک‌بارمصرفِ ۶۰ ثانیه‌ای ⇒ `?ticket=`. توکن دیگر
 *     در آدرس (و لاگِ پراکسی) نمی‌ماند.
 *   • سازگاری: `?token=` و `Authorization: Bearer` هنوز پذیرفته‌اند تا
 *     برنامه‌های کهنه از کار نیفتند. ⛔ تا داشبوردِ نسخه‌ها کلاینتِ
 *     کهنه نشان می‌دهد، `?token=` برداشته نشود.
 * حساب از توکن؛ `?device_id=` فقط می‌گوید خبرِ کارِ خودش را برای خودش
 * نفرستیم.
 *
 * ── لغوِ دسترسی: سه لایه، همه «بسته‌پیش‌فرض» (S78، §۱۹.۱۲) ─────────
 * تا دیروز توکن فقط هنگامِ وصل سنجیده می‌شد؛ عضوِ برداشته‌شده سوکتِ بازش
 * را نگه می‌داشت، «changed» می‌گرفت و «scan»ش به صندوقِ کامپیوترِ صاحب
 * می‌رسید. حالا (هر سه برای `?ticket=` و `?token=`ِ کهنه یکسان):
 *   ۱) `accessChanged(...)` — **تنها درِ** «دسترسی عوض شد». هر راهی که
 *      دسترسی را می‌بندد یا کم می‌کند، پس از نوشتن (و بیرون از تراکنش)
 *      همین را صدا می‌زند و منتظرش می‌ماند. سوکت‌های هدف با توکنِ خودشان
 *      همان لحظه دوباره سنجیده می‌شوند و هر کدام که دیگر همان حساب و همان
 *      نقش نیست بسته می‌شود؛ بقیه (دستگاهِ دیگر، دکانِ دیگرِ همان آدم،
 *      عضوهای دیگر، صاحب) دست نمی‌خورند. فهرستِ صدازننده‌ها پایینِ همین تابع.
 *   ۲) پیش از هر پیامِ اثرگذارِ برنامه (relay، ping) و پیش از رساندنِ هر
 *      خبر (hello، changed، relay) سنجش با حافظهٔ حداکثر ۵ ثانیه — اگر
 *      لایهٔ ۱ جایی جا ماند، سوکت حداکثر چند ثانیه بعد باز هم بسته است.
 *   ۳) سرِ انقضای توکن، سوکت بسته می‌شود؛ برنامه از راهِ همیشگی (بلیت با
 *      توکنِ تازه‌شده) برمی‌گردد. ⚠️ **فقط برای کلاینتی که می‌تواند**: برنامه با
 *      `&caps=expiry` در نشانیِ سوکت اعلام می‌کند (`LiveSocket.kt`، `license/sync-engine.js`
 *      از S78)، یا پرچمِ `sync.live_expiry_close` (پیش‌فرض خاموش؛ `all` یا همان دکان/پمپ)
 *      برای همه روشنش می‌کند. برنامه‌های کهنهٔ نصب‌شده پاسخِ بستن نمی‌دهند و تا یک دقیقه
 *      بیرونِ اتاق می‌ماندند (اسکنِ گوشی به صندوق گم می‌شد)، پس برای آن‌ها پیر شدنِ توکن به‌تنهایی
 *      — مثلِ پیش از S78 — دلیلِ بستن نیست؛ باطل‌شدن، کاربر/دستگاهِ بسته و عضویت/نقش همچنان
 *      در لایه‌های ۱ و ۲ سنجیده می‌شوند (حتی پس از پاک شدنِ ردیفِ توکنِ پیر).
 *   تورِ ایمنی (TEN-02، #534): `dropRevoked` در هر تپشِ ۳۰ثانیه‌ای هشِ توکنِ
 *      همهٔ سوکت‌های کاربری را یک‌جا می‌سنجد — سوکتِ بی‌کارِ نشستِ باطل هم می‌رود.
 * کدهای بستن (کلاینت‌ها در هر حالت با فاصلهٔ فزاینده دوباره وصل می‌شوند):
 *   4401 `session_revoked` — نشست باطل شد (خروج، برداشتن/تعلیق، رمز، دستگاه، …)
 *   4401 `access_changed`  — نشست زنده است ولی دسترسی عوض شد (نقش، دکانِ نشست)
 *   4403 `expired`         — توکنِ دسترسی پیر شد؛ با توکنِ تازه برگرد
 *   1011 `recheck_failed`  — سنجش نشد (خطای پایگاه) ⇒ بسته، نه باز
 *
 * ⚠️ روی همان سرورِ HTTP سوار می‌شود (`attach(server)`) و `app.js`
 * خودش `app.listen` را می‌پیچد، پس `index.js` و سنجه‌ها هر دو بی هیچ
 * کارِ اضافه‌ای آن را دارند.
 */
const crypto = require('node:crypto');
const { WebSocketServer, WebSocket } = require('ws');
const { resolveToken, resolveSession, contextOfRow, appAlias } = require('./sync-v1-auth');

const PATHS = ['/sync/live', '/api/sync/live', '/api/sync/v1/live', '/api/v1/sync/v1/live'];
const PING_MS = 30_000;

/* ---------------------------------------------- بلیتِ یک‌بارمصرف (F0-11) */
const TICKET_MS = 60_000;
const TICKET_MAX = 20_000;          // سقفِ حافظه — پرتر شد، کهنه‌ترین می‌رود
/** بلیت ⇒ { token, exp } — خودِ توکن نگه داشته می‌شود تا هنگامِ وصل
 *  دوباره سنجیده شود (نشستِ باطل‌شده در این ۶۰ ثانیه هم رد شود). */
const tickets = new Map();

function issueTicket(token) {
  const now = Date.now();
  for (const [k, v] of tickets) { if (v.exp > now) break; tickets.delete(k); }
  while (tickets.size >= TICKET_MAX) tickets.delete(tickets.keys().next().value);
  const ticket = crypto.randomBytes(32).toString('base64url');
  tickets.set(ticket, { token, exp: now + TICKET_MS });
  return { ticket, expires_in: TICKET_MS / 1000 };
}

/** بلیت را مصرف می‌کند (یک بار)؛ توکنِ پشتش یا '' */
function takeTicket(ticket) {
  if (!ticket) return '';
  const e = tickets.get(ticket);
  if (!e) return '';
  tickets.delete(ticket);
  return e.exp > Date.now() ? e.token : '';
}

/* ----------------------------------------------- لغوِ دسترسی (S78) */
/** لایهٔ ۲: عمرِ حافظهٔ سنجش. ⛔ بیشتر از ۵ ثانیه نمی‌شود (آزمون کوتاه‌ترش می‌کند). */
const RECHECK_MS = Math.min(5_000, Math.max(50, Number(process.env.SYNC_LIVE_RECHECK_MS) || 5_000));
/** پس از فرمانِ بستن، اگر کلاینت پاسخِ بستن نداد (OkHttpِ کهنه)، قطعِ کامل */
const CLOSE_GRACE_MS = 2_000;
/** پیام‌های در صفِ سنجشِ یک سوکت — بیشتر ⇒ دور ریخته (سیلِ پیام) */
const QUEUE_MAX = 64;
/** setTimeout بیش از ~۲۴٫۸ روز را نمی‌پذیرد */
const MAX_TIMER_MS = 2_147_000_000;

const CLOSE = Object.freeze({
  revoked: Object.freeze({ code: 4401, reason: 'session_revoked' }),   // همان کدِ #534
  changed: Object.freeze({ code: 4401, reason: 'access_changed' }),
  expired: Object.freeze({ code: 4403, reason: 'expired' }),
  failed: Object.freeze({ code: 1011, reason: 'recheck_failed' }),
});

/** همهٔ سوکت‌های زنده — هدفِ `accessChanged` از همین‌جا پیدا می‌شود */
const sockets = new Set();
/** سوکت ⇒ توکنِ خامش؛ بیرون از `ws.sync` تا در هیچ لاگ و بازرسی‌ای نیفتد */
const secrets = new WeakMap();
/** هر `accessChanged` یک واحد؛ وصلی که وسطش «دسترسی عوض شد» دید، پیش از
 *  نخستین کار دوباره سنجیده می‌شود (فاصلهٔ resolveToken تا join) */
let epoch = 0;

/** اتاقِ هر حساب: کلید ⇒ مجموعهٔ سوکت‌ها */
const rooms = new Map();

function roomKey(ctx) { return `${ctx.app}:${ctx.accountKind}:${ctx.accountId}`; }

function reject(socket, status, text) {
  try {
    socket.write(`HTTP/1.1 ${status} ${text}\r\nConnection: close\r\nContent-Length: 0\r\n\r\n`);
  } catch { /* سوکت رفته */ }
  socket.destroy();
}

async function headOf(ctx) {
  const { status } = require('./sync-v1');
  try { return (await status(ctx)).head; } catch { return 0; }
}

function send(ws, obj) {
  if (ws.readyState === WebSocket.OPEN) {
    try { ws.send(JSON.stringify(obj)); } catch { /* بسته شد */ }
  }
}

/** همان حساب، همان کاربر، همان نقش، همان راه — وگرنه دسترسیِ این سوکت دیگر آن نیست که بود */
function sameAccess(was, now) {
  return !!now && !!now.accountId &&
    now.app === was.app && now.accountKind === was.accountKind && now.accountId === was.accountId &&
    (now.userId || '') === (was.userId || '') && (now.role || '') === (was.role || '') &&
    (now.via || '') === (was.via || '');
}

function expiredNow(s) {
  const exp = Number(s.ctx.expiresAt) || 0;
  return exp > 0 && Date.now() > exp;
}

/** پرچمِ `sync.live_expiry_close`: مقدارِ خودِ دکان/پمپ بر `all` جلو می‌افتد؛ فقط `true` روشن است */
const EXPIRY_FLAG = 'sync.live_expiry_close';
async function expiryForced(accountId) {
  const rows = await require('../db').many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [EXPIRY_FLAG, 'all', String(accountId || '')]
  );
  const decode = (x) => { try { return JSON.parse(x); } catch { return null; } };
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

/**
 * بافتِ امروزِ سوکت. همان `resolveToken` ِ درهای HTTP — جز برای برنامهٔ کهنه (بی `caps=expiry` و
 * بی پرچم) با توکنِ پیر: آن‌جا پیری دلیلِ بستن نیست، پس فقط «باطل شد؟» از ردیفِ توکن (اگر هنوز
 * پاک نشده) و کاربر/دستگاه/عضویت/نقش از ردیفِ لحظهٔ وصل (`contextOfRow`).
 */
async function currentCtx(ws) {
  const s = ws.sync;
  if (s.expiryClose || !s.row || !expiredNow(s)) return resolveToken(secrets.get(ws) || '');
  const { one: first } = require('../db');
  const r = s.row;
  const t = await first('SELECT revoked_at FROM tokens WHERE token_hash=$1', [s.tokenHash]);
  if (t && t.revoked_at) return null;
  //  خودِ نشست هنوز زنده است؟ همان دستگاه (بی شناسهٔ دستگاه: همان کس در همان بخش) دست‌کم یک توکنِ
  //  باطل‌نشده و پیرنشده دارد — خروج، خروج از همه و بیرون‌کردنِ دستگاه همه را باطل می‌کنند، حتی وقتی
  //  ردیفِ توکنِ پیرِ خودِ سوکت پاک شده (`pruneExpired`) یا خروج با توکنِ تازه‌تری زده شده
  const args = [r.subject_id, r.app || 'shop', Date.now()];
  let sql = `SELECT 1 FROM tokens WHERE subject_id=$1 AND COALESCE(app,'shop')=$2 AND revoked_at IS NULL AND expires_at > $3`;
  if (r.device_id) { args.push(r.device_id); sql += ` AND device_id=$${args.length}`; }
  if (!await first(`${sql} LIMIT 1`, args)) return null;
  return contextOfRow(r);
}

/** از اتاق و فهرست بیرون — بعد از این هیچ خبری به این سوکت نمی‌رسد */
function forget(ws) {
  const s = ws.sync;
  if (!s) return;
  if (s.expiry) { clearTimeout(s.expiry); s.expiry = null; }
  sockets.delete(ws);
  secrets.delete(ws);
  const room = rooms.get(s.key);
  if (room) { room.delete(ws); if (!room.size) rooms.delete(s.key); }
}

/** بستن با کد و دلیل؛ همان لحظه از اتاق بیرون، و اگر کلاینت جواب نداد، قطعِ کامل */
function kill(ws, why) {
  const s = ws.sync;
  if (!s || s.dead) return;
  s.dead = true;
  forget(ws);
  try { ws.close(why.code, why.reason); } catch { /* رفته */ }
  const t = setTimeout(() => { try { ws.terminate(); } catch { /* رفته */ } }, CLOSE_GRACE_MS);
  if (t.unref) t.unref();
}

/**
 * سنجشِ دوبارهٔ یک سوکت با توکنِ خودش (`currentCtx`: همان `resolveToken`ِ درهای HTTP).
 * سنجش‌های هم‌زمانِ یک «نسل» یکی می‌شوند. ناجور ⇒ بسته (4401/4403)؛
 * خطا ⇒ بسته (1011). هرگز رد نمی‌شود (reject).
 */
function check(ws) {
  const s = ws.sync;
  if (s.dead) return Promise.resolve(false);
  if (s.inflight && s.inflightGen === s.gen) return s.inflight;
  const gen = s.gen;
  const p = (async () => {
    let now = null;
    try { now = await currentCtx(ws); } catch { kill(ws, CLOSE.failed); return false; }
    if (s.dead) return false;
    if (!now) { kill(ws, s.expiryClose && expiredNow(s) ? CLOSE.expired : CLOSE.revoked); return false; }
    if (!sameAccess(s.ctx, now)) { kill(ws, CLOSE.changed); return false; }
    //  نتیجه‌ای که پیش از یک «دسترسی عوض شد» شروع شده بود، حافظه نمی‌شود
    if (gen === s.gen) { s.okGen = gen; s.okUntil = Date.now() + RECHECK_MS; }
    return true;
  })();
  s.inflight = p;
  s.inflightGen = gen;
  p.then(() => { if (s.inflight === p) s.inflight = null; });
  return p;
}

/** لایهٔ ۲: آیا این سوکت همین حالا اجازهٔ کار دارد؟ (حافظهٔ ≤ ۵ ثانیه) */
async function allowed(ws) {
  const s = ws.sync;
  for (let i = 0; i < 3; i++) {
    if (s.dead) return false;
    if (s.okGen === s.gen && s.okUntil > Date.now()) return true;
    const gen = s.gen;
    if (!(await check(ws))) return false;
    if (gen === s.gen) return true;
  }
  return false;   // دسترسی پشتِ‌سرِهم عوض می‌شود — این پیام نه؛ پیامِ بعدی دوباره می‌سنجد
}

/** کارهای یک سوکت به ترتیبِ رسیدن؛ صفِ پر ⇒ دور ریخته */
function enqueue(ws, job) {
  const s = ws.sync;
  if (s.dead || s.queued >= QUEUE_MAX) return;
  s.queued++;
  s.chain = s.chain.then(job).catch(() => { /* یک پیامِ خراب صف را نمی‌بندد */ }).then(() => { s.queued--; });
}

/** رساندنِ یک خبر به یک سوکت — فقط اگر هنوز همان دسترسی را دارد */
function deliver(ws, payload) {
  allowed(ws).then((ok) => { if (ok) send(ws, payload); }, () => { /* بسته */ });
}

/**
 * لایهٔ ۳: سرِ انقضای توکن بسته شود (4403) — همان قاعدهٔ درهای HTTP (توکنِ پیر ۴۰۱) — فقط
 * برای سوکتی که `expiryClose` دارد (`caps=expiry` یا پرچمِ `sync.live_expiry_close`).
 * کلاینت‌های این ریپو (`LiveSocket.kt`، `license/sync-engine.js`) پس از آن با توکنِ تازه برمی‌گردند.
 * ⚠️ کلاینت‌های پمپ در ریپوی دیگرند؛ تا `caps=expiry` نفرستند، رفتارشان همان پیش از S78 است.
 */
function armExpiry(ws) {
  const s = ws.sync;
  const exp = Number(s.ctx.expiresAt) || 0;
  if (!exp || s.dead || !s.expiryClose) return;
  const left = exp - Date.now();
  if (left <= 0) { kill(ws, CLOSE.expired); return; }
  s.expiry = setTimeout(() => {
    s.expiry = null;
    if (Date.now() >= exp) kill(ws, CLOSE.expired); else armExpiry(ws);
  }, Math.min(left, MAX_TIMER_MS));
  if (s.expiry.unref) s.expiry.unref();
}

/**
 * @param {{row?: object|null, expiryClose?: boolean}} [opt] ردیفِ توکن در لحظهٔ وصل (برای سنجشِ
 *        برنامهٔ کهنه پس از پیری) و اینکه لایهٔ ۳ برای این سوکت روشن است یا نه
 */
function join(ctx, ws, deviceId, token, seenEpoch, { row = null, expiryClose = false } = {}) {
  const key = roomKey(ctx);
  const s = {
    key, deviceId, ctx, expiryClose: !!expiryClose,
    row: row ? {
      subject_id: row.subject_id, device_id: row.device_id || null, app: row.app || 'shop',
      scope: row.scope || null, shop_id: row.shop_id || null, expires_at: row.expires_at,
    } : null,
    //  برای سنجشِ دسته‌ایِ تپش (`dropRevoked`)؛ کامپیوترِ پمپ توکنِ کاربری ندارد
    tokenHash: ctx.via === 'user' ? require('./tokens').hashToken(token) : '',
    //  همین الان با `resolveToken` سنجیده شد
    gen: 0, okGen: 0, okUntil: Date.now() + RECHECK_MS,
    inflight: null, inflightGen: -1,
    chain: Promise.resolve(), queued: 0,
    expiry: null, dead: false,
  };
  ws.sync = s;
  ws.isAlive = true;
  secrets.set(ws, token);
  //  میانِ سنجش و همین‌جا دسترسی‌ای عوض شد ⇒ پیش از نخستین کار دوباره سنجیده شود
  if (seenEpoch !== epoch) s.gen++;
  sockets.add(ws);
  if (!rooms.has(key)) rooms.set(key, new Set());
  rooms.get(key).add(ws);
  armExpiry(ws);

  ws.on('pong', () => { ws.isAlive = true; });
  ws.on('message', (raw) => {
    let msg = null;
    try { msg = JSON.parse(String(raw)); } catch { return; }
    if (!msg || typeof msg !== 'object') return;
    const ping = msg.type === 'ping' || msg.event === 'ping';
    //  پیامِ دستگاه به دستگاه‌های دیگرِ همان حساب — فقط رخدادهای شناخته
    //  (گوشی به‌عنوانِ بارکدخوانِ کامپیوتر). هیچ داده‌ای ذخیره نمی‌شود.
    const payload = !ping && msg.type === 'relay' && RELAY_EVENTS.has(msg.event) ? cleanRelay(msg) : null;
    if (!ping && !payload) return;
    enqueue(ws, async () => {
      if (!(await allowed(ws))) return;
      if (ping) {
        const cursor = await headOf(s.ctx);
        send(ws, { event: 'pong', cursor, serverTime: Date.now() });
        return;
      }
      relay(s.ctx, payload, deviceId);
    });
  });
  ws.on('close', () => { s.dead = true; forget(ws); });
  ws.on('error', () => { try { ws.terminate(); } catch { /* بسته شد */ } });

  if (s.dead) return;   // توکن همان لحظهٔ وصل پیر بود
  allowed(ws).then(async (ok) => {
    if (!ok) return;
    const cursor = await headOf(ctx);
    send(ws, { event: 'hello', cursor, device_id: deviceId, serverTime: Date.now() });
  }, () => { /* بسته */ });
}

/**
 * ⛔ لایهٔ ۱ — تنها درِ «دسترسی عوض شد». پس از نوشتنِ تغییر (بیرون از
 * تراکنش یا پس از آن) صدا بزنید و منتظرش بمانید تا سوکت در **همان
 * درخواست** بسته شود. همان لحظه حافظهٔ سنجشِ هدف‌ها باطل می‌شود (هیچ
 * پیام و خبری بی سنجشِ تازه رد نمی‌شود) و بعد هر کدام با توکنِ خودش
 * سنجیده و اگر لازم بود بسته می‌شود. هرگز خطا بیرون نمی‌دهد.
 *
 * هدف: `{ userId }` (همهٔ سوکت‌های یک کاربر، در هر دکان/پمپ) یا
 * `{ accountKind, accountId }` (همهٔ سوکت‌های یک دکان/پمپ، از جمله
 * کامپیوترهای پمپ که کاربر ندارند)؛ هر دو با هم = اشتراکشان.
 *
 * صدازننده‌ها (هر راهِ تازهٔ بستن/کم‌کردنِ دسترسی باید به این فهرست بپیوندد):
 *   lib/tokens.js          revoke · revokeFamily · revokeAllForSubject · revokeOthersForSubject ·
 *                          revokeAllForDevice — خروج، خروج از همه، تازه‌سازیِ دزدیده (refresh_reused)،
 *                          عوض‌کردن و بازیابیِ رمز، بستنِ کاربر از پنل، از کار انداختنِ دستگاه (STF-10)
 *   lib/shops.js           updateMember — تغییرِ نقش، تعلیق، برداشتن، خروجِ داوطلبانه
 *   lib/stations.js        updateMember — همان برای پمپ
 *   lib/station-devices.js revoke · revokeBoundBy — بیرون‌کردنِ کامپیوترِ پمپ
 *   lib/user-delete.js     remove — حذفِ کاملِ حساب و دکان‌ها/پمپ‌هایش
 *   routes/me.js، routes/portal.js  DELETE /devices/:id — بیرون‌کردنِ دستگاهِ خود
 *   routes/shop.js         POST /active (دکانِ نشست عوض شد) · POST /staff/join (عضو ⇒ پیک)
 *   routes/admin-pump.js   POST /stations/:id/status — خاموش‌کردنِ پمپ
 *   routes/pump-device.js  adoptFromDevice — کامپیوترِ پمپ به پمپِ دیگر رفت
 *
 * @param {{userId?: string, accountKind?: string, accountId?: string}} target
 * @returns {Promise<number>} چند سوکت بسته شد
 */
async function accessChanged(target = {}) {
  const userId = String((target && target.userId) || '');
  const kind = String((target && target.accountKind) || '');
  const accountId = String((target && target.accountId) || '');
  if (!userId && !accountId) return 0;
  epoch++;
  const hits = [];
  for (const ws of sockets) {
    const c = ws.sync.ctx;
    if (userId && (c.userId || '') !== userId) continue;
    if (accountId && (c.accountId !== accountId || (kind && c.accountKind !== kind))) continue;
    ws.sync.gen++;   // همان لحظه: حافظهٔ سنجش باطل
    hits.push(ws);
  }
  if (!hits.length) return 0;
  try {
    const ok = await Promise.all(hits.map((ws) => check(ws)));
    return ok.filter((x) => !x).length;
  } catch {
    for (const ws of hits) kill(ws, CLOSE.failed);
    return hits.length;
  }
}

/**
 * خبر به دستگاه‌های دیگرِ همان حساب (هر کدام پس از سنجشِ لایهٔ ۲).
 * @returns {number} چند سوکت هدف بود
 */
function broadcast(ctx, cursor, exceptDevice = '') {
  return relay(ctx, { event: 'changed', cursor }, exceptDevice);
}

/**
 * رخدادهایی که دستگاه می‌تواند برای دستگاه‌های دیگرِ حسابش بفرستد.
 *   scan   — گوشی بارکد خواند ⇒ کامپیوتری که «گوشی به‌عنوانِ بارکدخوان»
 *            را با همان کدِ جفت‌شدن باز کرده، آن را در سبد می‌گذارد
 *   photos — عکسِ کالایی عوض شد (سرور خودش هم می‌فرستد)
 *   photo-request — کامپیوتر: «گوشی، عکسِ این کالا را بگیر» (عکس از ‎/photo-inbox‎ می‌آید)
 */
const RELAY_EVENTS = new Set(['scan', 'photos', 'photo-request']);

function cleanRelay(msg) {
  if (msg.event === 'scan') {
    const code = String(msg.code || '').trim();
    if (!code || code.length > 64 || /[\u0000-\u001f]/.test(code)) return null;
    return { event: 'scan', code, pair: String(msg.pair || '').slice(0, 12), at: Date.now() };
  }
  if (msg.event === 'photos') return { event: 'photos', rev: Number(msg.rev) || 0 };
  //  کامپیوتر از گوشیِ جفت‌شده عکسِ یک کالا را می‌خواهد
  if (msg.event === 'photo-request') {
    const pair = String(msg.pair || '').replace(/\D/g, '').slice(0, 6);
    if (pair.length !== 6) return null;
    return {
      event: 'photo-request', pair,
      product_id: String(msg.product_id || '').slice(0, 64),
      name: String(msg.name || '').slice(0, 120),
    };
  }
  return null;
}

/** فرستادنِ یک رخدادِ دلخواه (از فهرستِ بالا) به دستگاه‌های دیگرِ حساب */
function relay(ctx, payload, exceptDevice = '') {
  const room = rooms.get(roomKey(ctx));
  if (!room) return 0;
  let n = 0;
  for (const ws of room) {
    if (exceptDevice && ws.sync && ws.sync.deviceId === exceptDevice) continue;
    deliver(ws, payload);
    n++;
  }
  return n;
}

/** چند سوکت در اتاقِ این حساب زنده است — برای `/status` و پنل. */
function listeners(ctx) {
  const room = rooms.get(roomKey(ctx));
  return room ? room.size : 0;
}

/**
 * سوار کردن روی سرورِ HTTP. یک بار برای هر سرور.
 */
function attach(server) {
  if (server.__syncLive) return server.__syncLive;
  const wss = new WebSocketServer({ noServer: true, maxPayload: 16 * 1024 });

  server.on('upgrade', (req, socket, head) => {
    let url;
    try { url = new URL(req.url, 'http://localhost'); } catch { return reject(socket, 400, 'Bad Request'); }
    if (!PATHS.includes(url.pathname)) return reject(socket, 404, 'Not Found');

    const h = req.headers.authorization || '';
    const m = /^Bearer\s+(.+)$/i.exec(h.trim());
    const ticket = url.searchParams.get('ticket');
    const token = ticket != null ? takeTicket(ticket)
      : m ? m[1].trim() : (url.searchParams.get('token') || '');
    const deviceId = String(url.searchParams.get('device_id') || req.headers['x-device'] || '').slice(0, 64);
    //  S78: کلاینتی که پس از 4403 با توکنِ تازه برمی‌گردد، `caps=expiry` می‌فرستد
    const caps = String(url.searchParams.get('caps') || '').slice(0, 64).split(',').map((c) => c.trim());
    const seen = epoch;

    resolveSession(token).then(async (out) => {
      const ctx = out && out.ctx;
      if (!ctx || !ctx.accountId) return reject(socket, 401, 'Unauthorized');
      const claimed = appAlias(url.searchParams.get('app')) || appAlias(req.headers['x-app']);
      if (claimed && claimed !== ctx.app) return reject(socket, 403, 'Forbidden');
      const expiryClose = caps.includes('expiry') || await expiryForced(ctx.accountId);
      wss.handleUpgrade(req, socket, head, (ws) => join(ctx, ws, deviceId, token, seen, { row: out.row, expiryClose }));
    }).catch(() => reject(socket, 500, 'Internal Server Error'));
  });

  //  تپش: سوکتی که به ping جواب نداد، رفته — بسته می‌شود تا اتاق پر از
  //  مرده نماند
  const timer = setInterval(() => {
    for (const ws of wss.clients) {
      if (ws.isAlive === false) { try { ws.terminate(); } catch { /* رفته */ } continue; }
      ws.isAlive = false;
      try { ws.ping(); } catch { /* رفته */ }
    }
    dropRevoked(wss).catch((err) => console.error('[sync-live] نشست‌های باطل:', err.message));
  }, PING_MS);
  if (timer.unref) timer.unref();
  server.on('close', () => {
    clearInterval(timer);
    for (const ws of wss.clients) { try { ws.terminate(); } catch { /* رفته */ } }
    wss.close();
  });

  server.__syncLive = wss;
  return wss;
}

/**
 * ⛔ TEN-02 (WebSocket، #534) — تورِ ایمنیِ لایهٔ ۱: سوکتِ نشستی که در سرور باطل شد (خروج، خروج
 * از همه‌جا، تعلیق یا برداشتنِ عضو، دستگاهِ ازکارافتاده — همه `tokens.revoked_at` می‌گذارند)
 * حتی اگر راهی `accessChanged` را صدا نزده باشد و سوکت بی‌کار باشد، در تپشِ بعدی بسته می‌شود.
 * هر تپش (۳۰ ثانیه) یک پرسشِ دسته‌ای برای همهٔ سوکت‌های کاربری؛ فقط **باطل‌شده** این‌جا بسته
 * می‌شود (پایانِ مهلتِ توکن کارِ لایهٔ ۳ است، با 4403). سوکتِ کامپیوترِ پمپ (توکنِ دستگاه) این‌جا
 * نیست؛ لایه‌های ۱ و ۲ آن را می‌سنجند.
 * @returns {Promise<number>} چند سوکت بسته شد
 */
async function dropRevoked(wss) {
  const open = [...wss.clients].filter((ws) => ws.sync && ws.sync.tokenHash && !ws.sync.dead);
  if (!open.length) return 0;
  const hashes = [...new Set(open.map((ws) => ws.sync.tokenHash))];
  const rows = await require('../db').many(
    'SELECT token_hash FROM tokens WHERE token_hash = ANY($1::text[]) AND revoked_at IS NOT NULL', [hashes]
  );
  if (!rows.length) return 0;
  const dead = new Set(rows.map((r) => r.token_hash));
  let n = 0;
  for (const ws of open) {
    if (!dead.has(ws.sync.tokenHash) || ws.sync.dead) continue;
    kill(ws, CLOSE.revoked);
    n++;
  }
  return n;
}

module.exports = {
  attach, broadcast, relay, listeners, roomKey, issueTicket, accessChanged, dropRevoked,
  PATHS, PING_MS, RELAY_EVENTS, TICKET_MS, RECHECK_MS, CLOSE, EXPIRY_FLAG,
};
