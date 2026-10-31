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

const { badRequest: badInput, conflict } = require('../middleware/errors');

/**
 * کلیدهایی که فقط خودِ سرور می‌نویسد (`setOnce`) — پنل نه می‌نویسد نه پاک می‌کند: کفِ «فروشِ
 * تحویل» (`order-ledger.FLOOR_KEY`) اگر عوض یا پاک شود، تحویل‌های کهنه دوباره فروش می‌گیرند.
 */
const RESERVED = Object.freeze(['shop.orders.handover_floor']);

function writable(k) {
  if (RESERVED.includes(k)) throw conflict('این کلید را خودِ سرور یک بار می‌گذارد و از پنل عوض یا پاک نمی‌شود', 'reserved_key');
  return k;
}

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
  //  ⚠️ نوشتنی که وسطِ همین خواندن تمام شد نسخهٔ تازه‌تر را گذاشته — عقبش نبر
  return raise(scope, v);
}

/**
 * ⛔ نسخهٔ حافظه فقط بالا می‌رود: دو نوشتنِ هم‌زمان می‌توانند به ترتیبِ وارونه
 * تمام شوند (اولی ۵ را دیرتر از دومی ۶ بنویسد)، و پمپی که ۶ را دیده نباید ۵ ببیند.
 */
function raise(scope, v) {
  if (!versions.has(scope) || versions.get(scope) < v) versions.set(scope, v);
  return versions.get(scope);
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

/*
 *  فاز ۳ ِ پنلِ مرکزی — تاریخچه (مهاجرتِ ۱۶۷): هر نوشتن و پاک کردن، در **همان تراکنش**، یک ردیف در
 *  `live_config_history`: چه کسی، کی، پیشین ⇒ تازه، دلیل. پس هیچ درِ نوشتنی (پرچم، پمپ، کانالِ آزمایشی،
 *  کلیدِ یک‌بارهٔ سرور) بی ردی نمی‌ماند. `meta` = { reason, actor }؛ هر دو اختیاری (درهای پیشین).
 */
async function recordHistory(client, { scope, key, action, oldValue, newValue, by, meta }) {
  const actor = (meta && meta.actor) || require('./admin-service').currentActor() || {};
  await client.query(
    `INSERT INTO live_config_history (id, scope, key, action, old_value, new_value, changed_by, actor, reason, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10)`,
    [db.newId('lch'), scope, key, action, oldValue ?? null, newValue ?? null, String(by || '').slice(0, 80),
      JSON.stringify(actor), String((meta && meta.reason) || '').slice(0, 200), now()]
  );
}

/** تاریخچهٔ یک scope (و اگر داده شد، یک کلید)، تازه‌ترین اول؛ `before` = برای صفحهٔ بعد. */
async function history({ scope = '', key = '', limit = 50, before = 0 } = {}) {
  const s = scope ? checkScope(scope) : '';
  const k = key ? checkKey(key) : '';
  const lim = Math.min(Math.max(1, Number(limit) || 50), 200);
  const b = Number(before) > 0 ? Number(before) : Number.MAX_SAFE_INTEGER;
  const rows = await many(
    `SELECT * FROM live_config_history
      WHERE ($1 = '' OR scope = $1) AND ($2 = '' OR key = $2) AND created_at < $3
      ORDER BY created_at DESC, id DESC LIMIT $4`,
    [s, k, b, lim]
  );
  return rows.map((r) => ({
    id: r.id, scope: r.scope, key: r.key, action: r.action,
    oldValue: r.old_value === null ? null : decode(r.old_value),
    newValue: r.new_value === null ? null : decode(r.new_value),
    changedBy: r.changed_by, actor: typeof r.actor === 'string' ? decode(r.actor) : r.actor,
    reason: r.reason, at: Number(r.created_at),
  }));
}

async function set(scope, key, value, by = '', meta = {}) {
  const s = checkScope(scope);
  const k = writable(checkKey(key));
  if (value === undefined) throw badInput('مقدار لازم است', 'bad_value');
  const text = JSON.stringify(value);
  if (text.length > MAX_VALUE) throw badInput(`مقدار حداکثر ${MAX_VALUE} نویسه`, 'value_too_long');
  let version = 0;
  await tx(async (client) => {
    const exists = await client.query('SELECT value FROM live_config WHERE scope=$1 AND key=$2 FOR UPDATE', [s, k]);
    if (!exists.rows.length) {
      const c = await client.query('SELECT COUNT(*)::int AS n FROM live_config WHERE scope=$1', [s]);
      if (c.rows[0].n >= MAX_KEYS) throw badInput(`حداکثر ${MAX_KEYS} کلید برای هر scope`, 'too_many_keys');
    }
    await client.query(
      `INSERT INTO live_config (scope, key, value, updated_at, updated_by) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (scope, key) DO UPDATE SET value=excluded.value, updated_at=excluded.updated_at, updated_by=excluded.updated_by`,
      [s, k, text, now(), String(by || '').slice(0, 80)]
    );
    await recordHistory(client, {
      scope: s, key: k, action: 'set', oldValue: exists.rows.length ? exists.rows[0].value : null, newValue: text, by, meta,
    });
    version = await bump(client, s);
  });
  raise(s, version);
  return { scope: s, key: k, value, version };
}

/**
 * فقط اگر کلید نیست بنویس (کارِ سیستم، نه پنل) — دو نوشتنِ هم‌زمان ⇒ نخستین می‌ماند؛ هست ⇒ هیچ،
 * بی بالا بردنِ نسخه. سقفِ کلیدها را نمی‌سنجد (کلیدِ سیستمیِ یک‌باره). ⇒ { inserted }
 */
async function setOnce(scope, key, value, by = '') {
  const s = checkScope(scope);
  const k = checkKey(key);
  const text = JSON.stringify(value);
  if (value === undefined || text.length > MAX_VALUE) throw badInput('مقدار معتبر نیست', 'bad_value');
  let version = 0;
  const inserted = await tx(async (client) => {
    const r = await client.query(
      `INSERT INTO live_config (scope, key, value, updated_at, updated_by) VALUES ($1,$2,$3,$4,$5)
       ON CONFLICT (scope, key) DO NOTHING RETURNING key`,
      [s, k, text, now(), String(by || '').slice(0, 80)]
    );
    if (!r.rows.length) return false;
    await recordHistory(client, { scope: s, key: k, action: 'set_once', oldValue: null, newValue: text, by });
    version = await bump(client, s);
    return true;
  });
  if (inserted) raise(s, version);
  return { inserted };
}

async function remove(scope, key, by = '', meta = {}) {
  const s = checkScope(scope);
  const k = writable(checkKey(key));
  let removed = false;
  let version = await scopeVersion(s);
  await tx(async (client) => {
    const r = await client.query('DELETE FROM live_config WHERE scope=$1 AND key=$2 RETURNING value', [s, k]);
    removed = r.rows.length > 0;
    if (removed) {
      await recordHistory(client, { scope: s, key: k, action: 'remove', oldValue: r.rows[0].value, newValue: null, by, meta });
      version = await bump(client, s);
    }
  });
  raise(s, version);
  return { scope: s, key: k, removed, version };
}

/** فقط برای سنجه‌ها: حافظه را خالی کن تا از دیتابیس خوانده شود. */
function _forget() { versions.clear(); }

module.exports = { ALL, KEY_RE, MAX_VALUE, MAX_KEYS, RESERVED, versionOf, snapshot, list, set, setOnce, remove, history, checkScope, checkKey, _forget };
