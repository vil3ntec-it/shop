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
 * هر حساب یک اتاق دارد (`app:kind:id`). ورود با همان توکنی که push
 * می‌زند — `?token=` (مرورگر نمی‌تواند سرآیند بگذارد) یا
 * `Authorization: Bearer`. حساب از توکن؛ `?device_id=` فقط می‌گوید
 * خبرِ کارِ خودش را برای خودش نفرستیم.
 *
 * ⚠️ روی همان سرورِ HTTP سوار می‌شود (`attach(server)`) و `app.js`
 * خودش `app.listen` را می‌پیچد، پس `index.js` و سنجه‌ها هر دو بی هیچ
 * کارِ اضافه‌ای آن را دارند.
 */
const { WebSocketServer, WebSocket } = require('ws');
const { resolveToken, appAlias } = require('./sync-v1-auth');

const PATHS = ['/sync/live', '/api/sync/live', '/api/sync/v1/live', '/api/v1/sync/v1/live'];
const PING_MS = 30_000;

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

function join(ctx, ws, deviceId) {
  const key = roomKey(ctx);
  ws.sync = { key, deviceId, ctx };
  ws.isAlive = true;
  if (!rooms.has(key)) rooms.set(key, new Set());
  rooms.get(key).add(ws);

  ws.on('pong', () => { ws.isAlive = true; });
  ws.on('message', (raw) => {
    let msg = null;
    try { msg = JSON.parse(String(raw)); } catch { return; }
    if (msg && (msg.type === 'ping' || msg.event === 'ping')) {
      headOf(ctx).then(cursor => send(ws, { event: 'pong', cursor, serverTime: Date.now() }));
      return;
    }
    //  پیامِ دستگاه به دستگاه‌های دیگرِ همان حساب — فقط رخدادهای شناخته
    //  (گوشی به‌عنوانِ بارکدخوانِ کامپیوتر). هیچ داده‌ای ذخیره نمی‌شود.
    if (msg && msg.type === 'relay' && RELAY_EVENTS.has(msg.event)) {
      const payload = cleanRelay(msg);
      if (payload) relay(ctx, payload, deviceId);
    }
  });
  ws.on('close', () => {
    const room = rooms.get(key);
    if (room) { room.delete(ws); if (!room.size) rooms.delete(key); }
  });
  ws.on('error', () => { try { ws.terminate(); } catch { /* بسته شد */ } });

  headOf(ctx).then(cursor => send(ws, { event: 'hello', cursor, device_id: deviceId, serverTime: Date.now() }));
}

/**
 * خبر به دستگاه‌های دیگرِ همان حساب.
 * @returns {number} چند سوکت خبر گرفت
 */
function broadcast(ctx, cursor, exceptDevice = '') {
  const room = rooms.get(roomKey(ctx));
  if (!room) return 0;
  let n = 0;
  for (const ws of room) {
    if (exceptDevice && ws.sync && ws.sync.deviceId === exceptDevice) continue;
    send(ws, { event: 'changed', cursor });
    n++;
  }
  return n;
}

/**
 * رخدادهایی که دستگاه می‌تواند برای دستگاه‌های دیگرِ حسابش بفرستد.
 *   scan   — گوشی بارکد خواند ⇒ کامپیوتری که «گوشی به‌عنوانِ بارکدخوان»
 *            را با همان کدِ جفت‌شدن باز کرده، آن را در سبد می‌گذارد
 *   photos — عکسِ کالایی عوض شد (سرور خودش هم می‌فرستد)
 */
const RELAY_EVENTS = new Set(['scan', 'photos']);

function cleanRelay(msg) {
  if (msg.event === 'scan') {
    const code = String(msg.code || '').trim();
    if (!code || code.length > 64 || /[\u0000-\u001f]/.test(code)) return null;
    return { event: 'scan', code, pair: String(msg.pair || '').slice(0, 12), at: Date.now() };
  }
  if (msg.event === 'photos') return { event: 'photos', rev: Number(msg.rev) || 0 };
  return null;
}

/** فرستادنِ یک رخدادِ دلخواه (از فهرستِ بالا) به دستگاه‌های دیگرِ حساب */
function relay(ctx, payload, exceptDevice = '') {
  const room = rooms.get(roomKey(ctx));
  if (!room) return 0;
  let n = 0;
  for (const ws of room) {
    if (exceptDevice && ws.sync && ws.sync.deviceId === exceptDevice) continue;
    send(ws, payload);
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
    const token = m ? m[1].trim() : (url.searchParams.get('token') || '');
    const deviceId = String(url.searchParams.get('device_id') || req.headers['x-device'] || '').slice(0, 64);

    resolveToken(token).then((ctx) => {
      if (!ctx || !ctx.accountId) return reject(socket, 401, 'Unauthorized');
      const claimed = appAlias(url.searchParams.get('app')) || appAlias(req.headers['x-app']);
      if (claimed && claimed !== ctx.app) return reject(socket, 403, 'Forbidden');
      wss.handleUpgrade(req, socket, head, (ws) => join(ctx, ws, deviceId));
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

module.exports = { attach, broadcast, relay, listeners, roomKey, PATHS, PING_MS, RELAY_EVENTS };
