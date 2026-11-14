'use strict';
/**
 * «هیچ جزئیاتِ فنی/زیرساخت به بیرون» (`docs/RULES-fa.md`) — درهای عمومیِ بی توکنِ `/health`، `/ready` و
 * `/api/v1/config/server` به هر کسی در اینترنت جواب می‌دهند. نسخهٔ دقیقِ سرور، مدتِ روشن بودن و حالِ کارگرِ ایمیل
 * به مهاجم می‌گوید کدام آسیب‌پذیریِ شناخته را بیازماید و کِی سرور تازه بالا آمده — و هیچ برنامه‌ای آن‌ها را لازم ندارد
 * (برنامه‌ها فقط `ok`/`server`/`database`/`service` و برای چند سرور `serverId`/`wire`/`features`/`publicUrls` را
 * می‌خوانند؛ نسخه فقط برای نمایش بود و دیگر نمایش داده نمی‌شود).
 *
 * ── چه کسی جزئیات را می‌گیرد ───────────────────────────────────────────────
 * فقط **خودِ میزبان و شبکهٔ درونی**: نشانیِ سوکت loopback یا خصوصی (۱۰/۸، ۱۷۲٫۱۶/۱۲، ۱۹۲٫۱۶۸/۱۶، fc00::/7، fe80::/10)
 * **و** بی هیچ سرآیندِ پیش‌کش (`X-Forwarded-For`، `Forwarded`، `CF-Connecting-IP`…). یعنی `update.sh` (سنجشِ نسخه
 * پس از به‌روزرسانی، از درگاهِ میزبان)، healthcheck ِ Docker و پنلِ خانگی در همان شبکه همان پاسخِ کامل را می‌گیرند،
 * ولی هر درخواستی که از Caddy/تونل/Cloudflare گذشته (سرآیندِ پیش‌کش دارد) یا از نشانیِ عمومی آمده، پاسخِ کوتاه.
 * سرآیند از بیرون جعل‌شدنی است ولی فقط به سوی «کوتاه‌تر»: افزودنِ سرآیند جزئیات را می‌بندد، نبودنش را پیش‌کش نمی‌سازد.
 * نشانیِ سوکت (`req.socket.remoteAddress`) خوانده می‌شود، نه `req.ip` — `trust proxy` آن را از سرآیند می‌سازد.
 *
 * ⛔ میدان‌هایی که برنامه‌ها برای مسیریابی/جابه‌جایی لازم دارند (`publicUrls` پشتِ `PUBLIC_BASE_URLS`، `serverId`، `wire`)
 * دست نمی‌خورند. سنجه: `test/public-detail.test.js`.
 */

/** همهٔ سرآیندهایی که یعنی «از یک پیش‌کش/تونل گذشته» */
const FORWARD_HEADERS = ['x-forwarded-for', 'forwarded', 'x-real-ip', 'cf-connecting-ip', 'true-client-ip', 'x-forwarded-host', 'cf-ray'];

/** میدان‌های `/health` و `/ready` که فقط درون‌شبکه دیده می‌شوند */
const HEALTH_DETAIL = ['version', 'uptimeSeconds', 'email_worker'];
/** میدان‌های `/config/server` که فقط درون‌شبکه دیده می‌شوند */
const IDENTITY_DETAIL = ['version'];

function v4Private(a) {
  const p = a.split('.').map(Number);
  if (p.length !== 4 || p.some((n) => !Number.isInteger(n) || n < 0 || n > 255)) return false;
  return p[0] === 127 || p[0] === 10 || (p[0] === 172 && p[1] >= 16 && p[1] <= 31) || (p[0] === 192 && p[1] === 168);
}

/** نشانیِ loopback یا شبکهٔ خصوصی (IPv4، IPv6 و IPv4 ِ نگاشته در IPv6) */
function isPrivateAddr(addr) {
  const a = String(addr || '').trim().toLowerCase().replace(/^\[|\]$/g, '').replace(/%.*$/, '');
  if (!a) return false;
  if (a === '::1') return true;
  const mapped = /^::ffff:(\d+\.\d+\.\d+\.\d+)$/.exec(a);
  if (mapped) return v4Private(mapped[1]);
  if (a.includes(':')) return /^f[cd][0-9a-f]{2}:/.test(a) || /^fe[89ab][0-9a-f]:/.test(a);
  return v4Private(a);
}

/** خودِ میزبان یا شبکهٔ درونی، بی پیش‌کش؟ */
function internalCaller(req) {
  if (!req || !isPrivateAddr(req.socket && req.socket.remoteAddress)) return false;
  const h = req.headers || {};
  return !FORWARD_HEADERS.some((k) => h[k] !== undefined);
}

function without(body, keys) {
  const out = { ...body };
  for (const k of keys) delete out[k];
  return out;
}

/** بدنهٔ `/health` و `/ready`: درون‌شبکه همان کامل؛ بیرون بی نسخه، مدتِ روشن بودن و حالِ کارگر */
function health(req, body) {
  return internalCaller(req) ? body : without(body, HEALTH_DETAIL);
}

/** بدنهٔ `/config/server`: درون‌شبکه همان کامل؛ بیرون بی نسخهٔ سرور (`publicUrls`، `serverId` و `wire` می‌مانند) */
function identity(req, body) {
  return internalCaller(req) ? body : without(body, IDENTITY_DETAIL);
}

module.exports = { internalCaller, isPrivateAddr, health, identity, FORWARD_HEADERS, HEALTH_DETAIL, IDENTITY_DETAIL };
