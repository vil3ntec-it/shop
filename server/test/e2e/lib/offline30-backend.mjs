/**
 * سرورِ آزمونِ «۳۰ روز بی اینترنت» (SYN-01) — همان سرورِ واقعیِ مخزن، برای `Offline30DaysDesktopTest` ِ کامپیوتر.
 *
 *   node server/test/e2e/lib/offline30-backend.mjs <خروجی.json>
 *
 * • همان پایهٔ سناریوهای سرتاسری (`harness.mjs` ⇐ `createApp`، جدولِ schema، سوکتِ زنده) روی PGlite ِ در حافظه؛
 *   `E2E_DATABASE_URL=postgres://…` ⇒ همان روی PostgreSQL (پایگاهِ جدا؛ schema ِ public از نو ساخته می‌شود).
 * • **ساعتِ سرور** دست‌گردان است: `Date` ِ همین فرایند (و هر `new Date()` ِ بی آرگومان) با «جابه‌جاییِ» قابلِ
 *   تنظیم — توکن، مجوز، اشتراک، کد و برگه همه با همین ساعت سنجیده می‌شوند، درست مثلِ سروری که ۳۰ روز گذشته است.
 * • **شبکهٔ دکان** یک پراکسیِ HTTP ِ جلوی سرور است (دستگاه‌های آزمون فقط آن را می‌بینند): `up` (رد شدن)، `down`
 *   (هر اتصال همان دم بسته — قطعیِ اینترنت)، و «بریدن در میانهٔ push»: n-امین push ِ بعدی یا پیش از رسیدن به سرور
 *   بریده می‌شود (`request`) یا پس از اعمال، بی پاسخ (`response` — بدترین حالت: سرور گرفت، دستگاه نفهمید).
 * • درِ کنترل (`ctl`، فقط 127.0.0.1، جدا از پراکسی): ساعت، شبکه، ساختنِ دکان/صاحب/شاگرد و ورودِ دو دستگاه، پرچم،
 *   نگاه به `oplog` (شمارِ op ِ هر دستگاه و تکراری‌ها)، ورودِ دوباره، باطل کردنِ توکنِ تازه‌سازی و کدِ آفلاین.
 * • نشانی‌ها در `<خروجی.json>`؛ با `POST /stop` یا بسته شدنِ stdin خاموش می‌شود.
 *
 * ⛔ فقط برای آزمون: ساعتِ دست‌گردان و پراکسی هرگز در کدِ سرور نیستند؛ این پرونده جزوِ `src/` نیست.
 */
import fs from 'node:fs';
import http from 'node:http';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);
const DAY = 24 * 3600 * 1000;

/* ── ساعتِ دست‌گردان — پیش از نخستین `require` ِ کدِ سرور ── */
const RealDate = Date;
let offsetMs = 0;
class ShiftedDate extends RealDate {
  constructor(...a) { if (a.length === 0) super(RealDate.now() + offsetMs); else super(...a); }
  static now() { return RealDate.now() + offsetMs; }
}
globalThis.Date = ShiftedDate;

const out = process.argv[2];
if (!out) { console.error('کاربرد: node offline30-backend.mjs <خروجی.json>'); process.exit(2); }

const { boot } = await import('./harness.mjs');
const { NativeDevice } = await import('./native.mjs');
const E = await boot({ name: 'offline30' });

/* ── شبکهٔ دکان: پراکسی ── */
let mode = 'up';
/** بریدنِ push: { left: چند push ِ دیگر رد شود، kind: request|response، down: پس از بریدن شبکه هم برود } */
let cut = null;
const stats = { requests: 0, pushes: 0, cutRequest: 0, cutResponse: 0, refused: 0 };
const target = new URL(E.apiBase);

const proxy = http.createServer((req, res) => {
  stats.requests++;
  if (mode === 'down') { stats.refused++; req.socket.destroy(); return; }
  const isPush = req.method === 'POST' && /\/sync\/v1\/push(\?|$)/.test(req.url);
  let armed = null;
  if (isPush) {
    stats.pushes++;
    //  `down`: پس از بریدن شبکه هم می‌رود — تلاشِ خودکارِ دوبارهٔ `HttpURLConnection` (POST هم، `sun.net.http.retryPost`)
    //  همان دسته را بی‌صدا دوباره نفرستد و خودِ موتور در دورِ بعد از همان جا ادامه دهد
    if (cut && cut.left-- <= 0) { armed = cut.kind; if (cut.down) mode = 'down'; cut = null; }
  }
  if (armed === 'request') { stats.cutRequest++; req.resume(); req.socket.destroy(); return; }
  const up = http.request({
    host: target.hostname, port: target.port, method: req.method, path: req.url,
    headers: { ...req.headers, host: target.host },
  }, (r) => {
    if (armed === 'response') {
      //  سرور کارش را کرد؛ پاسخ تا ته خوانده و دور ریخته می‌شود، اتصالِ دستگاه بی پاسخ بسته
      stats.cutResponse++;
      r.resume();
      r.on('end', () => req.socket.destroy());
      return;
    }
    res.writeHead(r.statusCode, r.headers);
    r.pipe(res);
  });
  up.on('error', () => req.socket.destroy());
  req.pipe(up);
});
await new Promise((r) => proxy.listen(0, '127.0.0.1', r));
const api = `http://127.0.0.1:${proxy.address().port}`;

/* ── دکان و دو دستگاه ── */
let shop = null;

async function setup({ subscriptionDays = 120 } = {}) {
  const owner = await E.owner('off30-owner@example.com', 'صاحبِ دکان', 'دکانِ سی‌روزه');
  const admin = await E.adminToken();
  E.ok(await E.post('/api/admin/subscriptions', { shopId: owner.shopId, plan: 'pro', days: subscriptionDays, reason: 'آزمونِ ۳۰ روز' },
    admin, { 'Idempotency-Key': `off30-sub-${RealDate.now()}` }), 'اشتراک');
  const A = await E.device('off30-owner-pc', owner.email, { seller: 'صاحب' });
  const inv = E.ok(await E.post('/api/shop/staff-code', { role: 'staff', maxUses: 1 }, owner.token), 'کدِ شاگرد');
  const B = new NativeDevice(E, 'off30-staff-phone', { seller: 'علی' });
  await B.loginStaff(inv.code, 'علی');
  shop = { owner, A, B, staffCode: inv.code };
  const pk = E.ok(await E.get('/api/license/public-key'), 'کلیدِ مجوز');
  const dev = (d, extra = {}) => ({
    deviceId: d.deviceId, userId: String(d.user?.id || ''), access: d.token, refresh: d.refreshToken, ...extra,
  });
  return {
    api, shopId: owner.shopId, ownerEmail: owner.email, staffCode: inv.code, publicKey: pk.publicKey,
    owner: dev(A), staff: dev(B, { deviceKey: B.staffKey }),
    //  S76 (د۱۸): سوکتِ زندهٔ واقعی مستقیم به سرور — پراکسیِ «شبکهٔ دکان» فقط HTTP است (upgrade ندارد)
    direct: E.apiBase,
  };
}

/** `oplog` ِ همین دکان: شمارِ هر دستگاه، شناسه‌های یکتا و هر op_id ِ تکراری (نباید باشد — UNIQUE) */
async function oplog() {
  const rows = await E.db.many(
    `SELECT device_id, op_id FROM oplog WHERE app='shop' AND account_kind='shop' AND account_id=$1`, [String(shop.owner.shopId)]);
  const byDevice = {};
  const seen = new Map();
  for (const r of rows) {
    byDevice[r.device_id] = (byDevice[r.device_id] || 0) + 1;
    seen.set(r.op_id, (seen.get(r.op_id) || 0) + 1);
  }
  const duplicates = [...seen].filter(([, n]) => n > 1).map(([id]) => id);
  return { total: rows.length, distinct: seen.size, duplicates, byDevice, ids: [...seen.keys()] };
}

/* ── درِ کنترل ── */
const routes = {
  'POST /clock': async (b) => { offsetMs = Math.round(Number(b.days || 0) * DAY); return { now: Date.now(), offsetMs }; },
  'POST /net': async (b) => {
    mode = b.mode === 'down' ? 'down' : 'up';
    cut = Number.isInteger(b.cutPushAfter)
      ? { left: b.cutPushAfter, kind: b.cutKind === 'request' ? 'request' : 'response', down: b.thenDown === true } : null;
    return { mode, cut, stats };
  },
  'GET /stats': async () => ({ mode, cut, stats }),
  'POST /setup': async (b) => setup(b),
  'POST /flag': async (b) => { await E.flag(shop.owner.shopId, String(b.key), b.value); return { ok: true }; },
  'GET /oplog': async () => oplog(),
  /** صاحب دوباره با کدِ ایمیلی، روی **همان** دستگاه (همان شناسه) — مثلِ کسی که پس از از دست رفتنِ نشست وارد می‌شود */
  'POST /relogin-owner': async () => {
    const d = new NativeDevice(E, 'off30-owner-pc', { deviceId: shop.A.deviceId, seller: 'صاحب' });
    await d.login(shop.owner.email);
    return { access: d.token, refresh: d.refreshToken };
  },
  /** «توکنِ تازه‌سازی منقضی شد» برای همین کاربر (بی جابه‌جاییِ ساعتِ همه): همان ستونی که `tokens.find` می‌سنجد */
  'POST /expire-refresh': async (b) => {
    const r = await E.db.query(`UPDATE tokens SET expires_at=$1 WHERE subject_id=$2 AND kind='refresh' AND revoked_at IS NULL`,
      [Date.now() - 1000, String(b.userId)]);
    return { expired: r.rowCount ?? r.affectedRows ?? 0 };
  },
  /**
   * قاعده‌های «منقضی‌شدنی» ِ سرور با ساعتِ همین حالای سرور — **فقط خواندنی** (هیچ توکنی نمی‌چرخد، هیچ رازی خرج نمی‌شود):
   * توکنِ دسترسی و تازه‌سازی (`tokens.verify`)، اشتراک (`entitlementOf`، `multi_device` = شاگرد می‌نویسد) و رازِ
   * دستگاهِ شاگرد (`staff-device-keys.hasLive` ⇐ نگهبانِ `auth.staff_session_guard`).
   */
  'POST /check': async (b) => {
    const tokens = require('../../../src/lib/tokens');
    const { entitlementOf } = require('../../../src/lib/entitlement');
    const keys = require('../../../src/lib/staff-device-keys');
    const ent = await entitlementOf(shop.owner.shopId);
    const alive = async (t, kind) => (typeof t === 'string' && t ? Boolean(await tokens.verify(t, kind)) : null);
    return {
      now: Date.now(),
      access: await alive(b.access, 'access'),
      refresh: await alive(b.refresh, 'refresh'),
      entitlement: { source: ent.source, multiDevice: ent.features.includes('multi_device'), phase: ent.subscription?.phase || '' },
      staffKeyLive: b.staffUserId ? await keys.hasLive({ shopId: shop.owner.shopId, userId: String(b.staffUserId), deviceUid: String(b.staffDeviceId) }) : null,
    };
  },
  /** برگهٔ تأیید با همان `approval-core.covers` ِ سرور (قرینهٔ `ApprovalSlip.covers`) — زمانِ op، نه ساعتِ سرور */
  'POST /approval-covers': async (b) => ({ verdict: require('../../../src/lib/approval-core').covers(b.slip, b.need) }),
  /** پایانِ اشتراک «n روز پس از همین حالا»ی سرور (منفی ⇒ گذشته) — مثلِ اشتراکی که در میانهٔ قطعی تمام شد */
  'POST /subscription': async (b) => {
    const r = await E.db.query(`UPDATE subscriptions SET ends_at=$1, grace_days=$2 WHERE shop_id=$3 AND status='active'`,
      [Date.now() + Math.round(Number(b.endsInDays) * DAY), Number(b.graceDays || 0), shop.owner.shopId]);
    return { updated: r.rowCount ?? 0 };
  },
  /** کدِ اشتراکِ آفلاینِ همین کامپیوتر (پنلِ مدیر) — همان `lib/shop-offline-codes.issue` */
  'POST /offline-code': async (b) => {
    const codes = require('../../../src/lib/shop-offline-codes');
    const r = await codes.issue({ plan: 'sub', computer: String(b.computer), account: shop.owner.email, days: Number(b.days), createdBy: 'off30' });
    return { code: r.code, endsAt: r.offline?.endsAt ?? null };
  },
};

const ctl = http.createServer(async (req, res) => {
  const chunks = [];
  for await (const c of req) chunks.push(c);
  let body = {};
  try { body = chunks.length ? JSON.parse(Buffer.concat(chunks).toString('utf8')) : {}; } catch { body = {}; }
  const key = `${req.method} ${req.url.split('?')[0]}`;
  if (key === 'POST /stop') { res.end('{"ok":true}'); await stop(); return; }
  const fn = routes[key];
  try {
    if (!fn) throw Object.assign(new Error(`نامعلوم: ${key}`), { status: 404 });
    const r = await fn(body);
    res.writeHead(200, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify(r));
  } catch (e) {
    res.writeHead(e.status || 500, { 'Content-Type': 'application/json; charset=utf-8' });
    res.end(JSON.stringify({ error: String(e.message || e) }));
  }
});
await new Promise((r) => ctl.listen(0, '127.0.0.1', r));

let stopping = false;
async function stop() {
  if (stopping) return;
  stopping = true;
  proxy.closeAllConnections?.();
  proxy.close();
  ctl.closeAllConnections?.();
  ctl.close();
  E.outbox.stop();
  await E.cluster.stop().catch(() => {});
  E.api.closeAllConnections?.();
  await new Promise((r) => E.api.close(r));
  await E.db.closeDb().catch(() => {});
  process.exit(0);
}
process.stdin.on('end', stop);
process.stdin.resume();

fs.writeFileSync(out, JSON.stringify({ api, ctl: `http://127.0.0.1:${ctl.address().port}`, db: E.db.isPglite() ? 'pglite' : 'postgres' }));
console.log(`offline30: api=${api} ctl=http://127.0.0.1:${ctl.address().port} db=${E.db.isPglite() ? 'pglite' : 'postgres'}`);
