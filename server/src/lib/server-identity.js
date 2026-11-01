'use strict';
/**
 * هویت و توانِ همین سرور — `GET /api/v1/config/server` (پرچمِ برنامهٔ `app.multi_server`).
 *
 * برنامه‌ها با این می‌فهمند به **کدام** سرور وصل‌اند (نه فقط به کدام نشانی) و این سرور چه دارد:
 *
 *   { kind: 'tohid-server', serverId: 'srv_…', name, version, wire: {min, max}, api: ['v1'],
 *     features: [...], publicUrl, time }
 *
 * ⛔ **بی راز.** فقط همین فهرستِ سفید بیرون می‌رود؛ چیزی از `app_config` جز `server_id` (و
 * `brand_name` که خودش عمومی است) خوانده نمی‌شود. شناسه راز نیست — هر کس نشانی را دارد می‌بیندش —
 * و فقط برای «همان سرور است یا نه» به کار می‌آید.
 *
 * `serverId` یک بار ساخته می‌شود (نخستین راه‌اندازی، `INSERT … ON CONFLICT DO NOTHING` تا دو فرآیندِ
 * هم‌زمان یک شناسه بگیرند) و در `app_config` می‌ماند: پشتیبان و بازگردانیِ همان دفتر روی
 * کامپیوترِ دیگر همان شناسه را دارد؛ سرورِ تازه با دفترِ تازه شناسهٔ تازه. پنلِ مدیر نمی‌تواند
 * عوضش کند (`PATCH /api/admin/config` فهرستِ سفید دارد).
 *
 * `wire` بازهٔ نسخهٔ قراردادِ برنامه است که این سرور می‌پذیرد. روزی که قراردادی شکسته شد، `max`
 * بالا می‌رود و `min` فقط وقتی که برنامهٔ قدیمی دیگر پشتیبانی نشود (`core/server/ServerInfo.kt`).
 */
const crypto = require('node:crypto');
const config = require('../config');
const { query, one, now } = require('../db');

const KEY = 'server_id';
const ID = /^srv_[a-z0-9]{16,40}$/;
const WIRE = Object.freeze({ min: 1, max: 1 });
const KIND = 'tohid-server';

let cached = null;

function newServerId() {
  return `srv_${crypto.randomBytes(12).toString('hex')}`;
}

/**
 * شناسهٔ پایدارِ این سرور. اگر مقدارِ دفتر خراب باشد (دستِ کسی به جدول خورده)، شناسهٔ تازه
 * جایش می‌نشیند و هشدار می‌رود — برنامه‌ها آن را «سرورِ دیگر» می‌بینند و وصل نمی‌شوند، که
 * درست همان است: دیگر نمی‌شود گفت همان سرور است.
 */
async function serverId() {
  if (cached) return cached;
  await query(
    `INSERT INTO app_config (key, value, updated_at) VALUES ($1, $2, $3) ON CONFLICT (key) DO NOTHING`,
    [KEY, newServerId(), now()]
  );
  let row = await one('SELECT value FROM app_config WHERE key=$1', [KEY]);
  if (!row || !ID.test(String(row.value || ''))) {
    console.warn('[server-id] شناسهٔ سرور در app_config خراب بود؛ شناسهٔ تازه ساخته شد');
    await query('UPDATE app_config SET value=$2, updated_at=$3 WHERE key=$1', [KEY, newServerId(), now()]);
    row = await one('SELECT value FROM app_config WHERE key=$1', [KEY]);
  }
  cached = String(row.value);
  return cached;
}

/** فقط آزمون: حافظهٔ فرآیند را خالی می‌کند (همان کارِ «راه‌اندازیِ دوباره») */
function forgetCache() { cached = null; }

/** نشانیِ عمومیِ اعلام‌شده (`SERVER_URL` یا `PUBLIC_URL`) — فقط اگر واقعاً نشانیِ http(s) باشد */
function publicUrl() {
  const raw = String(config.serverUrl || '').trim().replace(/\/+$/, '');
  if (!raw) return '';
  try {
    const u = new URL(raw);
    if ((u.protocol !== 'https:' && u.protocol !== 'http:') || u.username || u.password || u.search || u.hash) return '';
    return raw;
  } catch { return ''; }
}

/** آنچه این سرور دارد — فهرستِ سفید، از پیکربندی؛ هیچ‌کدام راز نیست */
function features() {
  const out = ['health', 'config.server', 'auth.password', 'shop', 'pump', 'sync.v1', 'live', 'plans.public'];
  if (config.otp.provider !== 'off') out.push('auth.email_code');
  if ((config.google.clientIds || []).some(Boolean)) out.push('auth.google');
  if (config.allowRegistration) out.push('registration');
  if (config.syncV3.enabled) out.push('sync.v3');
  if (config.tls && (config.tls.certFile || config.tls.pfxFile)) out.push('tls.direct');
  return out;
}

async function displayName() {
  const fromEnv = String(process.env.SERVER_NAME || '').trim();
  if (fromEnv) return fromEnv.slice(0, 60);
  const brand = await one(`SELECT value FROM app_config WHERE key='brand_name'`).catch(() => null);
  return String(brand?.value || '').trim().slice(0, 60) || 'سرورِ فروشگاه';
}

async function info() {
  return {
    ok: true,
    kind: KIND,
    serverId: await serverId(),
    name: await displayName(),
    version: require('../../package.json').version,
    wire: { ...WIRE },
    api: ['v1'],
    features: features(),
    publicUrl: publicUrl(),
    time: now(),
  };
}

module.exports = { serverId, info, features, publicUrl, forgetCache, WIRE, KIND, ID };
