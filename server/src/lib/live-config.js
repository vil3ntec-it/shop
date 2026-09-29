'use strict';
/**
 * «تنظیماتِ زنده» ی برنامهٔ پمپ — هر مقداری که بعداً بخواهید، بی آپدیتِ برنامه.
 *
 * ── خواستهٔ صاحب سامانه (۱۴۰۵/۰۷/۱۷) ─────────────────────────────────
 * «برنامه رو جوری کن که بعدن اگه قابلیتی خاستم بتونم راحت روش اجرا کنم…
 *  لایف اپدیت باشه… روی سرور فشاری نیاد… هیچ منطقی رو دست نزن و خراب نکن.»
 *
 *   پنل/مدیر ──set(scope, key, value)──▶ live_config (+ شمارنده)
 *   برنامهٔ کامپیوتر ──GET /api/pump/device/rate──▶ …, liveConfig: "<نسخه>"
 *                    (همان پرسشِ دقیقه‌ای که از قبل بود — درخواستِ تازه‌ای نیست)
 *                    ──GET /api/pump/device/live-config──▶ فقط وقتی نسخه عوض شده
 *
 * ⛔ **سرور هیچ مقداری را تفسیر نمی‌کند و هیچ کدی از این‌جا اجرا نمی‌شود.**
 * برنامه فقط کلیدهایی را که خودش می‌شناسد می‌خواند؛ پس نوشتنِ کلیدی که امروز
 * هیچ بخشی از برنامه نمی‌شناسد، هیچ رفتاری را عوض نمی‌کند.
 *
 * ⛔ **فشار نیست**: نسخهٔ هر scope در حافظه است و با هر نوشتن تازه می‌شود؛ پس
 * آن پرسشِ دقیقه‌ای به دیتابیس نمی‌رود. کلِ برگه فقط وقتی خوانده می‌شود که
 * نسخه عوض شده باشد.
 *
 * ⛔ **هر پمپ فقط `all` + خودش را می‌بیند** — `stationId` از توکنِ دستگاه است.
 */
const db = require('../db');
const { many, now, tx } = db;

const ALL = 'all';
const KEY_RE = /^[a-z][a-z0-9_.-]{0,63}$/;
const MAX_VALUE = 4096;
const MAX_KEYS = 200;

/** scope → نسخه؛ خالی ⇒ از دیتابیس. تنها جای نگه‌داری در حافظه. */
const versions = new Map();

const { badRequest: badInput } = require('../middleware/errors');

function checkScope(scope) {
  const s = String(scope || '').trim();
  if (!s) throw badInput('scope لازم است', 'bad_scope');
  if (s !== ALL && !/^[A-Za-z0-9_-]{1,80}$/.test(s)) throw badInput('scope نامعتبر است', 'bad_scope');
  return s;
}

function checkKey(key) {
  const k = String(key || '').trim();
  if (!KEY_RE.test(k)) throw badInput('کلید فقط حروفِ کوچکِ انگلیسی، رقم و . _ - (تا ۶۴ نویسه)', 'bad_key');
  return k;
}

async function scopeVersion(scope) {
  if (versions.has(scope)) return versions.get(scope);
  const r = await db.one('SELECT version FROM live_config_versions WHERE scope=$1', [scope]);
  const v = r ? Number(r.version) : 0;
  versions.set(scope, v);
  return v;
}

/** نسخهٔ برگهٔ یک پمپ: «همه.خودش». در سکوت، بی هیچ پرسشی از دیتابیس. */
async function versionOf(stationId) {
  const a = await scopeVersion(ALL);
  const s = stationId ? await scopeVersion(String(stationId)) : 0;
  return `${a}.${s}`;
}

function decode(text) {
  try { return JSON.parse(text); } catch { return null; }
}

/** برگهٔ کاملِ یک پمپ: `all` و بعد خودش (خودش جلو می‌افتد). */
async function snapshot(stationId) {
  const version = await versionOf(stationId);
  const rows = await many(
    'SELECT scope, key, value FROM live_config WHERE scope = $1 OR scope = $2',
    [ALL, String(stationId || ALL)]
  );
  const values = {};
  for (const r of rows) if (r.scope === ALL) values[r.key] = decode(r.value);
  for (const r of rows) if (r.scope !== ALL) values[r.key] = decode(r.value);
  return { version, values };
}

/** فهرستِ یک scope برای پنل — با زمان و نویسنده. */
async function list(scope) {
  const s = checkScope(scope);
  const rows = await many(
    'SELECT key, value, updated_at, updated_by FROM live_config WHERE scope=$1 ORDER BY key', [s]
  );
  return {
    scope: s,
    version: await scopeVersion(s),
    items: rows.map((r) => ({ key: r.key, value: decode(r.value), updatedAt: Number(r.updated_at), updatedBy: r.updated_by })),
  };
}

async function bump(client, scope) {
  const r = await client.query(
    `INSERT INTO live_config_versions (scope, version) VALUES ($1, 1)
     ON CONFLICT (scope) DO UPDATE SET version = live_config_versions.version + 1
     RETURNING version`,
    [scope]
  );
  return Number(r.rows[0].version);
}

async function set(scope, key, value, by = '') {
  const s = checkScope(scope);
  const k = checkKey(key);
  if (value === undefined) throw badInput('مقدار لازم است', 'bad_value');
  const text = JSON.stringify(value);
  if (text.length > MAX_VALUE) throw badInput(`مقدار حداکثر ${MAX_VALUE} نویسه`, 'value_too_long');
  let version = 0;
  await tx(async (client) => {
    const exists = await client.query('SELECT 1 FROM live_config WHERE scope=$1 AND key=$2', [s, k]);
    if (!exists.rows.length) {
      const c = await client.query('SELECT COUNT(*)::int AS n FROM live_config WHERE scope=$1', [s]);
      if (c.rows[0].n >= MAX_KEYS) throw badInput(`حداکثر ${MAX_KEYS} کلید برای هر scope`, 'too_many_keys');
    }
    await client.query(
      `INSERT INTO live_config (scope, key, value, updated_at, updated_by) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at, updated_by=excluded.updated_by`,
      [s, k, text, now(), String(by || '').slice(0, 80)]
    );
    version = await bump(client, s);
  });
  versions.set(s, version);
  return { scope: s, key: k, value, version };
}

async function remove(scope, key) {
  const s = checkScope(scope);
  const k = checkKey(key);
  let removed = false;
  let version = await scopeVersion(s);
  await tx(async (client) => {
    const r = await client.query('DELETE FROM live_config WHERE scope=$1 AND key=$2', [s, k]);
    removed = r.rowCount > 0;
    if (removed) version = await bump(client, s);
  });
  versions.set(s, version);
  return { scope: s, key: k, removed, version };
}

/** فقط برای سنجه‌ها: حافظه را خالی کن تا از دیتابیس خوانده شود. */
function _forget() { versions.clear(); }

module.exports = { ALL, KEY_RE, MAX_VALUE, MAX_KEYS, versionOf, snapshot, list, set, remove, _forget };
