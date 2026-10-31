/**
 * «دستگاهِ برنامهٔ دکان» در سطحِ پروتکل — همان کاری که برنامهٔ اندروید/کامپیوتر می‌کند،
 * بی رابطِ کاربری.
 *
 * از کجا «همان» است:
 *   • شکلِ ردیف‌ها از خودِ `android-native/.../data/Models.kt` خوانده می‌شود (همهٔ فیلدها با
 *     پیش‌فرضشان، `@SerialName`، و `@EncodeDefault(NEVER)`؛ همان `encodeDefaults = true` ِ
 *     `LedgerDiff`). فیلدی که این‌جا نوشته شود و در مدل نباشد خطاست ⇒ جابه‌جایی پیدا می‌شود.
 *   • تفاضل، هش، `$inc`، اعمالِ opِ رسیده و Snapshot با `license/sync-core.js` — قرینهٔ
 *     دقیقِ `sync/v1/LedgerDiff.kt` و `OpHash.kt` (همان قرارداد؛ `SyncV1CoreTest.kt` و
 *     `server/test/sync-client.test.js` هر دو را می‌سنجند).
 *   • push/pull/snapshot با همان بدنه و سرآیندهای `SyncV1Client.kt` و `HttpEngine.kt`
 *     (`X-App` · `X-Device` · `X-App-Version` · `X-Request-Id`)؛ ترتیبِ کار همان
 *     `SyncV1Engine.runOnce`/`firstSync`/`SyncCommit.absorb`.
 *   • کارهای دکان (کالا، ورودِ انبار، فروش، قرض، صندوق، مرجوعی، فروشِ تحویلِ سفارش) سطر به
 *     سطر از `WarehouseEngine.kt`، `SalesEngine.kt`، `DebtorEngine.kt`، `CashShift.kt` و
 *     `OrderFulfillment.kt` برگردانده شده‌اند (نامِ تابعِ کاتلین کنارِ هر کدام).
 *   • «حالتِ هواپیما»: `offline = true` ⇒ هیچ درخواستی بیرون نمی‌رود (همان `Backend.isOnline`).
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const ROOT = path.join(import.meta.dirname, '..', '..', '..', '..');
export const core = require(path.join(ROOT, 'license', 'sync-core.js'));
export const MODELS_KT = path.join(ROOT, 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'data', 'Models.kt');
/**
 * `X-App-Version` ِ پیش‌فرضِ دستگاه‌ها: گوشیِ `3.2.N` بالاتر از کفِ همهٔ فیلدهای
 * `server/src/lib/newer-fields.js` (A00) — همان قالبی که برنامهٔ واقعی می‌فرستد.
 */
export const APP_VERSION = '3.2.9999';

/* ═══════════════════════════ Models.kt ⇒ شکلِ ردیف ═══════════════════════════ */

function literal(src) {
  const s = src.trim();
  if (s === 'null') return { v: null };
  if (s === 'true' || s === 'false') return { v: s === 'true' };
  if (/^"(?:[^"\\]|\\.)*"$/.test(s)) return { v: JSON.parse(s) };
  if (/^-?\d+(\.\d+)?[LlfF]?$/.test(s)) return { v: Number(s.replace(/[LlfF]$/, '')) };
  if (/^emptyList\(\)$|^listOf\(\)$/.test(s)) return { v: [] };
  if (/^emptyMap\(\)$/.test(s)) return { v: {} };
  return null;   //  عبارتِ دیگری (مثلاً ثابتِ DEFAULT_*) — فقط در ShopData
}

/** همهٔ `@Serializable data class` ها ⇒ {Class: [{name, key, def, required, never}]} */
export function parseModels(file = MODELS_KT) {
  const text = fs.readFileSync(file, 'utf8').replace(/\/\*[\s\S]*?\*\//g, '');
  const out = {};
  const re = /@Serializable\s+data class (\w+)\(([\s\S]*?)\n\)/g;
  let m;
  while ((m = re.exec(text))) {
    const fields = [];
    let never = false;
    let serial = null;
    let aliases = [];
    for (const raw of m[2].split('\n')) {
      const line = raw.replace(/\/\/.*$/, '').trim();
      if (!line) continue;
      if (/@EncodeDefault\(EncodeDefault\.Mode\.NEVER\)/.test(line)) never = true;
      //  حاشیه‌نویسی می‌تواند روی خطِ خودش باشد (`@SerialName("amount")` بالای `val`)
      const sn = /@SerialName\("([^"]+)"\)/.exec(line);
      if (sn) serial = sn[1];
      const jn = /@JsonNames\(([^)]*)\)/.exec(line);
      if (jn) aliases = [...jn[1].matchAll(/"([^"]+)"/g)].map((x) => x[1]);
      const f = /(?:^|\s)val (\w+):\s*([^=]+?)(?:\s*=\s*(.+?))?,?$/.exec(line);
      if (!f) continue;
      const def = f[3] !== undefined ? literal(f[3]) : null;
      fields.push({ name: f[1], key: serial || f[1], aliases, type: f[2].trim(), required: f[3] === undefined, def: def ? def.v : undefined, expr: def ? null : (f[3] || null), never });
      never = false;
      serial = null;
      aliases = [];
    }
    out[m[1]] = fields;
  }
  return out;
}

export const MODELS = parseModels();

/** ردیفی که `kotlinx` با `encodeDefaults = true` می‌نویسد (پس از `jsNumbers`) */
export function row(cls, values) {
  const fields = MODELS[cls];
  if (!fields) throw new Error(`مدلِ ${cls} در Models.kt نیست`);
  const known = new Set(fields.map((f) => f.name));
  for (const k of Object.keys(values)) if (!known.has(k)) throw new Error(`${cls}.${k} در Models.kt نیست — شکلِ ردیفِ برنامه عوض شده است`);
  const o = {};
  for (const f of fields) {
    let v = values[f.name] !== undefined ? values[f.name] : f.def;
    if (v === undefined) {
      if (f.required) throw new Error(`${cls}.${f.name} لازم است`);
      throw new Error(`${cls}.${f.name}: پیش‌فرضِ «${f.expr}» خوانده نشد`);
    }
    if (f.never && (v === null || v === f.def)) continue;
    o[f.key] = Array.isArray(v) ? v.slice() : v;
  }
  return o;
}

/** دفترِ تازهٔ برنامه (`ShopData()`): مجموعه‌ها خالی، فهرست‌های پیش‌فرض از خودِ Models.kt */
export function emptyLedger() {
  const text = fs.readFileSync(MODELS_KT, 'utf8');
  const list = (name) => {
    const m = new RegExp(`val ${name} = (?:listOf\\(([^)]*)\\)|emptyList<String>\\(\\))`).exec(text);
    return m && m[1] ? [...m[1].matchAll(/"([^"]*)"/g)].map((x) => x[1]) : [];
  };
  const d = {};
  for (const c of core.COLLECTIONS) d[c] = [];
  d.expenseCategories = list('DEFAULT_EXPENSE_CATEGORIES');
  d.productCategories = list('DEFAULT_PRODUCT_CATEGORIES');
  d.productUnits = list('DEFAULT_UNITS');
  d.nextInvoiceNo = 1;
  return d;
}

/* ═══════════════ گذر از درِ مدل: `LedgerDiff.fromJson` ⇐ `toJson` ═══════════════ */

/** نوعِ فیلد ⇒ مدلِ تو در تو (`List<SalePayment>?`، `Address?`) یا null */
function nestedModel(type) {
  const l = /^List<(\w+)>\??$/.exec(type);
  if (l && MODELS[l[1]]) return { list: true, cls: l[1] };
  const o = /^(\w+)\??$/.exec(type);
  if (o && MODELS[o[1]]) return { list: false, cls: o[1] };
  return null;
}

/**
 * یک ردیف همان‌طور که برنامه پس از `decodeFromJsonElement` (با `ignoreUnknownKeys`) و
 * `encodeToJsonElement` (با `encodeDefaults`) می‌بیند: کلیدِ ناشناختهٔ مدل می‌افتد، پیش‌فرض‌ها
 * پر می‌شوند، `@EncodeDefault(NEVER)` با مقدارِ پیش‌فرض نوشته نمی‌شود، `@JsonNames` خوانده می‌شود.
 */
export function project(cls, src, keep = null) {
  const out = {};
  for (const f of MODELS[cls]) {
    //  ساختِ قدیمی‌تر فیلدِ تازه‌ترِ مدل را ندارد ([oldModelOf])
    if (keep && !keep(f.key)) continue;
    let v = src[f.key];
    if (v === undefined) for (const a of f.aliases || []) if (src[a] !== undefined) { v = src[a]; break; }
    if (v === undefined) v = f.def;
    if (v === undefined) continue;
    const nm = nestedModel(f.type);
    if (nm && v !== null) v = nm.list ? (Array.isArray(v) ? v.map((x) => project(nm.cls, x)) : v) : project(nm.cls, v);
    if (f.never && (v === null || JSON.stringify(v) === JSON.stringify(f.def))) continue;
    out[f.key] = v;
  }
  return out;
}

/** مدلِ ردیف‌های هر مجموعه از `ShopData` (کلیدِ `@SerialName`، مثلاً `returns`) */
export const COLLECTION_MODEL = Object.fromEntries((MODELS.ShopData || [])
  .map((f) => [f.key, (/^List<(\w+)>$/.exec(f.type) || [])[1]]).filter(([, c]) => c && MODELS[c]));

/**
 * همهٔ ردیف‌های مجموعه‌ها از درِ مدل؛ بقیهٔ کلیدهای دفتر دست نمی‌خورند. [keepOf]: مدلِ ساختی
 * قدیمی‌تر از `Models.kt` ِ امروز (مجموعه ⇒ پالایهٔ کلید، یا null یعنی همان مدلِ امروز).
 */
export function projectLedger(tree, keepOf = null) {
  const out = { ...tree };
  for (const c of core.COLLECTIONS) {
    if (!Array.isArray(tree[c]) || !COLLECTION_MODEL[c]) continue;
    const keep = keepOf ? keepOf(c) : null;
    out[c] = tree[c].map((r) => project(COLLECTION_MODEL[c], r, keep));
  }
  return out;
}

/** فهرستِ منجمدِ تورِ سرور: مدلِ دفترِ ساخت‌های پیش از A00، هر فیلد با کفِ نسخه‌اش */
const newerFields = require(path.join(ROOT, 'server', 'src', 'lib', 'newer-fields.js'));

/**
 * مدلِ دفترِ ساختِ نصب‌شدهٔ پیش از A00 با شمارهٔ [version]: فقط فیلدهایی که آن ساخت می‌شناخت
 * (`newer-fields.knows`) — نه `Models.kt` ِ امروز، که فیلدهای پس از A00 (`orderId`، `payments`، …)
 * را هم دارد و ساختِ قدیمی را «داناتر» از آن‌چه بود نشان می‌داد.
 */
function oldModelOf(version) {
  const client = newerFields.clientOf(version);
  return (table) => (newerFields.PRE_A00[table] ? (key) => newerFields.knows(client, table, key) : null);
}

/*
 * رفتارِ همین لحظهٔ `LedgerDiff.kt` ِ برنامه — از خودِ کدِ کاتلین خوانده می‌شود تا شبیه‌سازی از
 * برنامه جدا نیفتد (و آزمونِ سرتاسری با برگشتِ هر یک از این قاعده‌ها در کاتلین قرمز شود):
 *   • `applyRemote` خروجی را از درِ مدل رد می‌کند (دفتر و سایه هر دو)؛
 *   • A00 سایهٔ Snapshot (`SyncV1Engine.writeShadow` با `modelTree(`) از درِ مدل است؛ پیش از
 *     آن خام بود — ردیف‌های سرور با کلیدهایی که دفتر نمی‌تواند نگه دارد؛
 *   • A00 `changedFields` با `isModelField(`: کلیدی که مدل نمی‌شناسد هرگز `null` نمی‌شود؛
 *   • A00 `diff` با `modelView(`: ردیفی که خام فرق دارد به زبانِ مدل سنجیده می‌شود؛
 *   • A00 `SyncV1Client.push` با `"caps"`: نشانِ `a00` که سرور با آن تورِ ساخت‌های پیشین را
 *     کنار می‌گذارد (`server/src/lib/newer-fields.js`). دستگاهِ «وب» (`raw`) `client: "web"` می‌فرستد.
 * سایه هیچ‌وقت کلیدی را که مدل نمی‌شناسد نگه نمی‌دارد (بازبینیِ #341، B1).
 */
const KT = (...p) => fs.readFileSync(path.join(ROOT, 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'sync', 'v1', ...p), 'utf8');
const LEDGER_DIFF_KT = KT('LedgerDiff.kt');
const SYNC_V1_ENGINE_KT = KT('SyncV1Engine.kt');
const SYNC_V1_CLIENT_KT = KT('SyncV1Client.kt');
const bodyOf = (name, src = LEDGER_DIFF_KT) => {
  const at = src.indexOf(`fun ${name}(`);
  if (at < 0) return '';
  const end = src.indexOf('\n  }\n', at);
  return src.slice(at, end < 0 ? undefined : end);
};
export const KOTLIN = {
  applyRemoteShaped: /fromJson\(|modelTree\(/.test(bodyOf('applyRemote')),
  snapshotShaped: /modelTree\(/.test(bodyOf('writeShadow', SYNC_V1_ENGINE_KT)),
  keepsUnknownFields: /isModelField\(/.test(bodyOf('changedFields')),
  diffInModelTerms: /modelView\(/.test(bodyOf('diff')),
  capsA00: /put\("caps"/.test(bodyOf('push', SYNC_V1_CLIENT_KT)),
  //  S143: بازیابی از `RestoreRoute.decide` ِ core می‌گذرد و هرگز op نمی‌سازد (پیش از آن «ذخیره»ٔ عادی بود)
  restoreNoOps: restoreWiredNoOps(bodyOf('restoreBackup', fs.readFileSync(
    path.join(ROOT, 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'data', 'ShopStore.kt'), 'utf8'))),
};

/**
 * `ShopStore.restoreBackup` واقعاً بی op است؟ تنها «decide هست» کافی نبود: `writeInternal` در شاخهٔ
 * FROM_SERVER سبز می‌ماند (بازبینیِ #485). حالا: بدنه هیچ `writeInternal(`/`save(`/`record(` ندارد،
 * LOCAL حتماً `engine.resetTo(` و FROM_SERVER حتماً `engine.rebuildFromServer(` است؛ وگرنه هارنس همان
 * [NativeDevice.save] (تفاضل با سایه) را بازی می‌کند و S143 سرخ می‌شود.
 */
export function restoreWiredNoOps(body) {
  if (!/RestoreRoute\.decide\(/.test(body)) return false;
  if (/\b(writeInternal|save|record)\(/.test(body)) return false;
  const branch = (name) => {
    const at = body.indexOf(`RestoreRoute.Route.${name} ->`);
    if (at < 0) return '';
    const next = body.slice(at + 1).search(/RestoreRoute\.Route\.[A-Z_]+ ->/);
    return body.slice(at, next < 0 ? undefined : at + 1 + next);
  };
  return /^RestoreRoute\.Route\.LOCAL -> runCatching \{ engine\.resetTo\(next, staging\) \}/.test(branch('LOCAL'))
    && /^RestoreRoute\.Route\.FROM_SERVER -> try \{\s*when \(val out = engine\.rebuildFromServer\(next, pushedStaging\)\)/.test(branch('FROM_SERVER'));
}

/** ساختِ نصب‌شدهٔ پیش از A00: همان `applyRemote` ِ از درِ مدل، بی قاعده‌های تازه */
export const PRE_A00 = Object.freeze({ applyRemoteShaped: true, snapshotShaped: false, keepsUnknownFields: false, diffInModelTerms: false, capsA00: false, frozenModel: true });
/** وب: دفترِ خام، هیچ مدلی در کار نیست */
const NO_MODEL = Object.freeze({ applyRemoteShaped: false, snapshotShaped: false, keepsUnknownFields: false, diffInModelTerms: false, capsA00: false });

/** کلیدهایی که مدلِ ردیف‌های این مجموعه می‌شناسد (با `@JsonNames`)؛ null یعنی مدل ندارد */
const knownKeys = (table) => {
  const cls = COLLECTION_MODEL[table];
  return cls ? new Set(MODELS[cls].flatMap((f) => [f.key, ...(f.aliases || [])])) : null;
};

/** همان پالایشِ `changedFields` ِ کاتلین: حذفِ کلیدی که مدل نمی‌شناسد op نمی‌شود */
function dropUnknownRemovals(ops, flags = KOTLIN) {
  if (!flags.keepsUnknownFields) return ops;
  const out = [];
  for (const op of ops) {
    const known = op.type === 'update' ? knownKeys(op.table) : null;
    if (!known) { out.push(op); continue; }
    const fields = {};
    for (const [k, v] of Object.entries(op.fields || {})) if (!(v === null && !known.has(k))) fields[k] = v;
    if (Object.keys(fields).length === Object.keys(op.fields || {}).length) { out.push(op); continue; }
    if (!Object.keys(fields).length) continue;
    out.push({ ...op, fields, hash: core.hashOf({ ...op, fields }) });
  }
  return out;
}

const clone = (x) => JSON.parse(JSON.stringify(x));
const todayOf = (t) => {
  const d = new Date(t);
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
};
const money = (n) => Math.round(n).toLocaleString('en-US');
const roundHalfUp = (x) => Math.round(x);   //  `roundToLong()` ِ کاتلین برای عددهای مثبتِ این دفتر

export class NetworkDown extends Error { constructor() { super('به سرور نرسیدیم (حالتِ هواپیما)'); this.code = 'offline'; } }

/* ═══════════════════════════ دستگاه ═══════════════════════════ */

export class NativeDevice {
  /**
   * @param {object} E        هارنس (نشانیِ سرور و صندوقِ ایمیل)
   * @param {string} label    نامِ کوتاه برای گزارش
   * @param {object} [o]
   * @param {string} [o.deviceId]  `store.deviceUid`
   * @param {string} [o.seller]    نامِ فروشنده روی فاکتور
   * @param {string} [o.appVersion] سرآیندِ `X-App-Version` (گوشی `3.2.N`، کامپیوتر `1.0.N`)
   * @param {object} [o.kotlin]    قاعده‌های `LedgerDiff` ِ همین ساخت؛ پیش‌فرض همان کدِ امروز (`KOTLIN`).
   *                               `PRE_A00` = ساختِ نصب‌شدهٔ پیش از A00
   * @param {boolean} [o.raw]      دفترِ JSON ِ خام بی مدل — همان وب (`license/sync-core.js`)
   */
  constructor(E, label, { deviceId = null, seller = '', appVersion = APP_VERSION, kotlin = KOTLIN, raw = false } = {}) {
    this.E = E;
    this.label = label;
    this.appVersion = appVersion;
    this.kotlin = raw ? NO_MODEL : kotlin;
    this.raw = raw;
    //  مدلِ دفترِ همین ساخت: امروز ⇒ `Models.kt`؛ ساختِ پیش از A00 ⇒ فهرستِ منجمدِ همان نسخه
    this.keepOf = this.kotlin.frozenModel ? oldModelOf(appVersion) : null;
    this.deviceId = deviceId || `e2e-${label}-${randomUUID().slice(0, 8)}`.replace(/[^A-Za-z0-9_-]/g, '');
    this.seller = seller || label;
    this.offline = false;
    this.token = '';
    this.refreshToken = '';
    this.refreshes = 0;
    this.ledger = emptyLedger();
    this.shadow = {};
    this.queue = [];
    this.dropped = [];
    this.cursor = 0;
    this.snapshotAt = 0;
    this.lastError = '';
    this.holding = false;
    this.requests = [];
  }

  /* ---------------- شبکه ---------------- */

  headers(extra = {}) {
    return {
      'Content-Type': 'application/json',
      'X-App': 'shop',
      'X-Device': this.deviceId,
      'X-App-Version': this.appVersion,
      'X-Request-Id': randomUUID(),
      ...(this.token ? { Authorization: `Bearer ${this.token}` } : {}),
      ...extra,
    };
  }

  /** یک درخواست با سرآیندهای برنامه؛ ۴۰۱ ِ توکنِ کهنه ⇒ یک بار تازه‌سازیِ چرخشی (TokenStore) */
  async api(method, p, body, { headers = {}, retry = true } = {}) {
    if (this.offline) throw new NetworkDown();
    const url = this.E.apiBase + (p.startsWith('/api') ? p : `/api${p}`);
    this.requests.push(`${method} ${p.split('?')[0]}`);
    const r = await fetch(url, { method, headers: this.headers(headers), body: body === undefined ? undefined : JSON.stringify(body) });
    const text = await r.text();
    let json = null;
    try { json = text ? JSON.parse(text) : null; } catch { json = { raw: text.slice(0, 200) }; }
    if (r.status === 401 && retry && this.refreshToken && !/\/auth\//.test(p)) {
      if (await this.refresh()) return this.api(method, p, body, { headers, retry: false });
    }
    return { status: r.status, body: json };
  }

  /** `POST /auth/refresh` — توکنِ چرخیده جای قبلی می‌نشیند (#246) */
  async refresh() {
    if (this.offline) throw new NetworkDown();
    const r = await fetch(`${this.E.apiBase}/api/auth/refresh`, {
      method: 'POST', headers: this.headers(), body: JSON.stringify({ refreshToken: this.refreshToken }),
    });
    const b = await r.json().catch(() => null);
    if (r.status !== 200 || !b || !(b.accessToken || b.access_token)) { this.lastRefresh = { status: r.status, body: b }; return false; }
    this.token = b.accessToken || b.access_token;
    this.refreshToken = b.refreshToken || b.refresh_token || this.refreshToken;
    this.refreshes++;
    return true;
  }

  /** ورود با کدِ ایمیل (`CodeLoginRepository`): درخواست ⇐ کد از نامه ⇐ تأیید با شناسهٔ همین دستگاه */
  async login(email) {
    const at = Date.now();
    let rq = await this.api('POST', '/auth/shop/request-code', { email, device_name: this.label });
    if (rq.status === 429 && Number(rq.body?.retry_after) <= 60) {
      //  دستگاهِ دومِ همان آدم: «یک دقیقه بعد» — فاصلهٔ ارسالِ دوبارهٔ کد (RESEND_S) گذشته است
      await this.E.db.query("UPDATE login_requests SET created_at = created_at - 61000 WHERE app='shop' AND email=$1", [email.toLowerCase()]);
      rq = await this.api('POST', '/auth/shop/request-code', { email, device_name: this.label });
    }
    if (rq.status !== 200) throw new Error(`${this.label}: درخواستِ کد ${rq.status} ${JSON.stringify(rq.body)}`);
    const code = await this.E.mailCode(email, at);
    const v = await this.api('POST', '/auth/shop/verify', { request_id: rq.body.request_id, code, device_id: this.deviceId, device_name: this.label });
    if (v.status !== 200) throw new Error(`${this.label}: ورود ${v.status} ${JSON.stringify(v.body)}`);
    this.token = v.body.access_token || v.body.accessToken;
    this.refreshToken = v.body.refresh_token || v.body.refreshToken;
    this.user = v.body.user;
    this.email = email;
    return this;
  }

  /** `LogoutGuard.decide(queued)` (core/sync/SyncGuard.kt) — صفِ پُر ⇒ بسته */
  logoutVerdict() { return this.queue.length > 0 ? { block: true, pending: this.queue.length } : { block: false }; }

  /** خروجِ عادی: فقط وقتی `LogoutGuard` اجازه دهد؛ سرور هر دو توکن را باطل می‌کند */
  async logout() {
    const v = this.logoutVerdict();
    if (v.block) return { blocked: true, pending: v.pending };
    const r = await this.api('POST', '/auth/logout', { refreshToken: this.refreshToken });
    this.token = '';
    return { blocked: false, status: r.status };
  }

  /* ---------------- همگام‌سازیِ v1 ---------------- */

  /** نشانِ صریحِ بدنهٔ push: `caps: ["a00"]` ِ ساختِ A00 به بعد، یا `client: "web"` ِ وب */
  marks() {
    if (this.raw) return { client: 'web' };
    return this.kotlin.capsA00 ? { caps: ['a00'] } : {};
  }

  /** `ShopStore.save` ⇒ `SyncV1Engine.record`: تفاضل با سایه ⇒ صف */
  save(next) {
    //  A00: تفاضل به زبانِ مدل (`modelView`) — برای ردیفی که خام برابر است همان نتیجه را دارد
    const k = this.kotlin;
    const base = k.diffInModelTerms ? projectLedger(this.shadow, this.keepOf) : this.shadow;
    const ops = dropUnknownRemovals(core.diff(base, next).ops, k);
    if (ops.length) this.queue.push(...ops);
    this.shadow = core.shadowOf(next);
    this.ledger = next;
    return ops.length;
  }

  /** `SyncV1Engine.runOnce`: همهٔ صف در دسته‌ها، بعد pull تا تهِ cursor */
  async sync() {
    if (this.offline) { this.lastError = 'offline'; return { ok: false, offline: true }; }
    try {
      let guard = 0;
      while (this.queue.length && guard++ < 500) {
        //  `OpQueue.batch()`: حداکثر ۲۰۰ op و ۲۵۶KB
        const batch = [];
        let size = 2;
        for (const op of this.queue) {
          const s = Buffer.byteLength(JSON.stringify(op)) + 1;
          if (batch.length >= 200 || (batch.length && size + s > 256 * 1024)) break;
          size += s;
          batch.push(op);
        }
        const r = await this.api('POST', '/sync/v1/push', { device_id: this.deviceId, schema_version: core.SCHEMA_VERSION, queued: this.queue.length, ops: batch, ...this.marks() });
        if (r.status === 426) { this.holding = true; this.lastError = r.body?.message || 'upgrade'; return { ok: false, status: 426 }; }
        if (r.status !== 200) { this.lastError = `${r.status} ${r.body?.error?.code || ''}`.trim(); return { ok: false, status: r.status, body: r.body }; }
        this.holding = false;
        const res = r.body.results || [];
        const ok = new Set(res.filter((x) => x.status === 'applied' || x.status === 'duplicate').map((x) => x.op_id));
        const bad = res.filter((x) => !(x.status === 'applied' || x.status === 'duplicate'));
        for (const b of bad) this.dropped.push({ ...batch.find((o) => o.op_id === b.op_id), why: b.reason || b.status });
        const gone = new Set([...ok, ...bad.map((b) => b.op_id)]);
        this.queue = this.queue.filter((o) => !gone.has(o.op_id));
      }
      for (let i = 0; i < 50; i++) {
        const r = await this.api('GET', `/sync/v1/pull?device_id=${encodeURIComponent(this.deviceId)}&since=${this.cursor}`);
        if (r.status !== 200) { this.lastError = `${r.status} ${r.body?.error?.code || ''}`.trim(); return { ok: false, status: r.status, body: r.body }; }
        const ops = r.body.ops || [];
        if (ops.length) this.absorb(ops);
        this.cursor = Number(r.body.cursor || this.cursor);
        if (!r.body.has_more) break;
      }
      this.lastError = '';
      return { ok: true };
    } catch (e) {
      this.lastError = e.message;
      return { ok: false, error: e.message };
    }
  }

  /** `SyncCommit.absorb`: دفتر و سایه هر دو با همان opها جلو می‌روند */
  absorb(ops) {
    //  `LedgerDiff.applyRemote` خروجی را از درِ مدل رد می‌کند: کلیدِ ناشناخته در دفتر و سایه نمی‌ماند
    const shape = this.kotlin.applyRemoteShaped ? (x) => projectLedger(x, this.keepOf) : (x) => x;
    this.ledger = shape(core.applyRemote(this.ledger, ops).data);
    this.shadow = core.shadowOf(shape(core.applyRemote(this.shadow, ops).data));
  }

  /** `SyncV1Engine.firstSync`: دفترِ خالی ⇒ Snapshot؛ وگرنه کلِ دفتر یک بار */
  async firstSync() {
    const rows = core.COLLECTIONS.reduce((s, c) => s + (this.ledger[c] || []).length, 0);
    if (rows === 0) {
      const r = await this.api('GET', '/sync/v1/snapshot');
      if (r.status !== 200) return { ok: false, status: r.status, body: r.body };
      const next = core.fromSnapshot(r.body, this.ledger);
      //  `restoreFromServer`: دفتر = fromJson(next) (از درِ مدل)؛ سایه = `writeShadow(next)` —
      //  از A00 از درِ مدل (`modelTree`)، پیش از آن خام
      this.ledger = this.raw ? next : projectLedger(next, this.keepOf);
      this.shadow = core.shadowOf(this.kotlin.snapshotShaped ? projectLedger(next, this.keepOf) : next);
      this.cursor = Number(r.body.cursor || 0);
      this.snapshotAt = Date.now();
      return { ok: true, rows: Number(r.body.rows || 0) };
    }
    this.queue.push(...core.diff({}, this.ledger).ops);
    this.shadow = core.shadowOf(this.ledger);
    return this.sync();
  }

  /** همگام تا خالی شدنِ صف (برای گام‌هایی که «باید برسد») */
  async drain() {
    const r = await this.sync();
    if (!r.ok || this.queue.length) throw new Error(`${this.label}: همگام نشد (${this.queue.length} در صف؛ ${this.lastError || JSON.stringify(r)})`);
    return r;
  }

  /**
   * «بازیابیِ پشتیبان» = `ShopStore.restoreBackup` (S143، #455) — راه از `RestoreRoute.decide` ِ core
   * (`core/.../backup/RestoreRoute.kt`؛ یکی را عوض کنی، هر دو را). بازیابی **هرگز op نمی‌سازد**:
   *   • هرگز هم‌تراز نشده و (بی ورود یا Snapshot ِ خالی) ⇒ `SyncV1Engine.resetTo`: پشتیبان جای دفتر و خودش سایه، cursor صفر؛
   *   • هرگز هم‌تراز نشده، واردشده و Snapshot ردیف دارد ⇒ همان راهِ همگام (پایین)؛ Snapshot نیامد ⇒ `sync_first`، هیچ؛
   *   • همگام، بی ورود یا بی شبکه ⇒ هیچ چیزی عوض نمی‌شود؛
   *   • همگام ⇒ `SyncRebuild.run`: صف تا ته (این‌جا همان دورِ همیشه)، بعد Snapshot — ردیف‌ها فقط از سرور،
   *     از پشتیبان فقط آنچه همگام نمی‌شود (`fromServer`)، کفِ فاکتور (`withInvoiceCursor`)؛ سایه و
   *     cursor با همان Snapshot.
   * ساختی که بازیابی را هنوز «ذخیره» می‌کند (`KOTLIN.restoreNoOps` خاموش) ⇒ همان [save] (تفاضل با سایه).
   * @returns {Promise<{ok:boolean, route:string, rows?:number, notOnServer?:number}>}
   */
  async restore(backup) {
    if (!this.kotlin.restoreNoOps) { this.save(backup); return { ok: true, route: 'save' }; }
    const shaped = (t) => (this.kotlin.snapshotShaped ? projectLedger(t, this.keepOf) : t);
    const synced = this.cursor > 0 || this.snapshotAt > 0;
    const signedIn = !!this.token;
    //  #485: واردشده ولی هرگز همگام‌نشده ⇒ `SyncV1Engine.serverRows` (Snapshot.rows؛ نیامد ⇒ null)
    let serverRows = null;
    if (!synced && signedIn && !this.offline) {
      const s = await this.api('GET', '/sync/v1/snapshot');
      const n = s.status === 200 ? Number(s.body?.rows) : NaN;
      serverRows = Number.isInteger(n) && n >= 0 ? n : null;
    }
    //  `RestoreRoute.decide` (v1؛ v3 این‌جا نیست)
    if (!synced && (!signedIn || serverRows === 0)) {
      this.ledger = this.raw ? backup : projectLedger(backup, this.keepOf);
      this.shadow = core.shadowOf(shaped(backup));
      this.cursor = 0;
      return { ok: true, route: 'local' };
    }
    if (!synced && (serverRows === null || this.offline)) return { ok: false, route: 'sync_first' };
    if (!signedIn) return { ok: false, route: 'need_sign_in' };
    if (this.offline) return { ok: false, route: 'need_net' };
    const pushed = await this.sync();
    if (!pushed.ok) return { ok: false, route: 'from_server', error: this.lastError };
    if (this.queue.length) return { ok: false, route: 'queue_left' };
    const r = await this.api('GET', '/sync/v1/snapshot');
    if (r.status !== 200) return { ok: false, route: 'from_server', status: r.status };
    const syncedKeys = new Set([...core.COLLECTIONS, ...core.LIST_COLLECTIONS]);
    const base = emptyLedger();
    for (const [k, v] of Object.entries(backup)) if (!syncedKeys.has(k)) base[k] = v;
    const next = core.fromSnapshot(r.body, base);
    const highest = Math.max(0, ...(next.sales || []).map((s) => Number(s.invoiceNumber) || 0));
    next.nextInvoiceNo = Math.max(Number(next.nextInvoiceNo) || 1, highest + 1);
    this.ledger = this.raw ? next : projectLedger(next, this.keepOf);
    this.shadow = core.shadowOf(shaped(next));
    this.cursor = Number(r.body.cursor || 0);
    this.snapshotAt = Date.now();
    //  `RestoreRoute.notOnServer`: ردیف‌های پشتیبان که دفترِ تازه ندارد — افزوده نمی‌شوند، فقط شمرده
    const notOnServer = core.COLLECTIONS.reduce((n, t) => {
      const have = new Set((this.ledger[t] || []).map((x) => String(x.id)));
      return n + new Set((backup[t] || []).map((x) => String(x.id)).filter((id) => !have.has(id))).size;
    }, 0);
    return { ok: true, route: 'from_server', rows: Number(r.body.rows || 0), notOnServer };
  }

  /* ---------------- کارهای دکان (سطر به سطرِ موتورهای کاتلین) ---------------- */

  now() { return Date.now(); }

  /** `WarehouseEngine.addProduct` + `addEntry` (کالای تازه با ورودِ انبار) */
  stockIn({ name, price, cost, units, unit = 'عدد', category = '', barcode = '' }) {
    const d = clone(this.ledger);
    const now = this.now();
    const today = todayOf(now);
    const id = core.ulid(now);
    d.products.push(row('Product', {
      id, name, category, unit, purchasePrice: cost, salePrice: price, wholesalePrice: 0, minStock: 0,
      notes: '', barcodes: barcode ? [barcode] : [], createdAt: now, subcategory: '', supplierId: '',
      active: true, saleUnit: '', unitFactor: 1,
    }));
    if (category && !d.productCategories.includes(category)) d.productCategories.push(category);
    if (unit && !d.productUnits.includes(unit)) d.productUnits.push(unit);
    this.save(d);
    this.addEntry(id, units, cost, unit);
    return id;
  }

  /** `WarehouseEngine.addEntry` */
  addEntry(productId, units, price, unit = 'عدد') {
    const d = clone(this.ledger);
    const now = this.now();
    const date = todayOf(now);
    const id = core.ulid(now);
    d.warehouseEntries.push(row('WarehouseEntry', {
      id, productId, cartons: 0, perCarton: 0, units, unit, price, date, notes: '', createdAt: now,
      expiryDate: '', batch: '', productionDate: '',
    }));
    d.stockMovements.push(row('StockMovement', {
      id: core.ulid(now), productId, type: 'purchase_in', qty: units, date, notes: 'ثبت ورود کالا', refId: id, createdAt: now,
    }));
    if (unit && !d.productUnits.includes(unit)) d.productUnits.push(unit);
    this.save(d);
    return id;
  }

  /** `WarehouseEngine.editProduct` — فقط قیمتِ فروش */
  setSalePrice(productId, salePrice) {
    const d = clone(this.ledger);
    const now = this.now();
    const p = d.products.find((x) => x.id === productId);
    const old = p.salePrice;
    p.salePrice = salePrice;
    d.auditLog.push(row('AuditEntry', {
      id: core.ulid(now), type: 'product_edit', date: todayOf(now), refId: productId,
      notes: `ویرایش «${p.name}»: قیمت فروش ${money(old)} ⇐ ${money(salePrice)}`, createdAt: now,
    }));
    this.save(d);
  }

  /** `DebtorEngine.add` */
  addDebtor(name, phone = '') {
    const d = clone(this.ledger);
    const now = this.now();
    const id = core.ulid(now);
    d.debtors.push(row('Debtor', { id, name, phone, notes: '', createdAt: now }));
    this.save(d);
    return id;
  }

  /** `ShopStore.stock`: Σ ورودِ انبار − Σ (فروخته − برگشته)ِ فاکتورهای لغونشده */
  stock(productId, d = this.ledger) {
    const entered = d.warehouseEntries.filter((e) => e.productId === productId).reduce((s, e) => s + e.units, 0);
    const cancelled = new Set(d.sales.filter((s) => s.status === 'cancelled').map((s) => s.id));
    const sold = d.saleItems.filter((i) => i.productId === productId && !cancelled.has(i.saleId)).reduce((s, i) => s + (i.quantity - i.returnedQty), 0);
    return entered - sold;
  }

  /** `ShopStore.debt` */
  debt(debtorId, d = this.ledger) {
    return d.transactions.filter((t) => t.debtorId === debtorId).reduce((s, t) => s + (t.type === 'give' ? t.amount : -t.amount), 0);
  }

  /**
   * `SalesEngine.record`. lines: [{id, qty}]؛ payment: 'cash' | 'credit'
   * @returns {{ok:true, saleId:string, invoiceNumber:number} | {ok:false, message:string}}
   */
  sell({ lines, payment = 'cash', debtorId = null, paid = null, discountType = 'amount', discountValue = 0, saleId = null }) {
    const d = clone(this.ledger);
    const now = this.now();
    const today = todayOf(now);
    if (!lines.length) return { ok: false, message: 'سبد خرید خالی است' };
    if (saleId && d.sales.some((s) => s.id === saleId)) return { ok: false, message: 'این فاکتور یک بار ثبت شده است' };
    for (const l of lines) {
      const p = d.products.find((x) => x.id === l.id);
      if (!p) continue;
      if (!p.active) return { ok: false, message: `«${p.name}» غیرفعال است` };
      const available = this.stock(p.id, d);
      if (l.qty > available) return { ok: false, message: `از «${p.name}» فقط ${available} مانده` };
    }
    const subtotal = lines.reduce((s, l) => s + roundHalfUp(d.products.find((x) => x.id === l.id).salePrice * l.qty), 0);
    let value = discountValue > 0 ? discountValue : 0;
    let discount;
    if (discountType === 'percent') { value = Math.min(value, 100); discount = roundHalfUp(subtotal * (value / 100)); } else { value = Math.min(value, subtotal); discount = roundHalfUp(value); }
    const finalTotal = Math.max(0, subtotal - discount);
    let p0 = payment === 'cash' ? finalTotal : Number(paid ?? 0);
    if (Number.isNaN(p0) || p0 < 0) return { ok: false, message: 'مبلغ پرداختی درست نیست' };
    p0 = Math.min(p0, finalTotal);
    const remaining = Math.max(0, finalTotal - p0);
    if (debtorId && !d.debtors.some((x) => x.id === debtorId)) return { ok: false, message: 'قرض‌دار پیدا نشد' };
    if (payment === 'credit' && remaining > 0 && !debtorId) return { ok: false, message: 'برای فروش نسیه، قرض‌دار را انتخاب کنید' };
    const onCredit = payment === 'credit' && remaining > 0;
    const id = saleId || core.ulid(now);
    const invoiceNumber = d.nextInvoiceNo || 1;
    d.sales.push(row('Sale', {
      id, invoiceNumber, total: subtotal, discountType: discountType === 'percent' ? 'percent' : 'amount', discountValue: value, discount, finalTotal,
      paymentMethod: payment === 'cash' ? 'cash' : 'credit', debtorId: debtorId || null, paidAmount: p0, remaining, status: 'completed',
      debtGiven: onCredit ? remaining : 0, debtSettled: 0, createdAt: now, date: today, syncStatus: 'pending', seller: this.seller,
    }));
    for (const l of lines) {
      const p = d.products.find((x) => x.id === l.id);
      d.saleItems.push(row('SaleItem', {
        id: core.ulid(now), saleId: id, productId: p.id, quantity: l.qty, unitPrice: p.salePrice, purchasePrice: p.purchasePrice,
        totalPrice: roundHalfUp(p.salePrice * l.qty), returnedQty: 0,
      }));
      d.stockMovements.push(row('StockMovement', {
        id: core.ulid(now), productId: p.id, type: 'sale', qty: -l.qty, date: today, notes: `فاکتور #${invoiceNumber}`, refId: id, createdAt: now,
      }));
    }
    if (onCredit) {
      d.transactions.push(row('DebtTransaction', {
        id: core.ulid(now), debtorId, type: 'give', amount: remaining, date: today, notes: `فروش نسیه — فاکتور #${invoiceNumber}`, createdAt: now,
      }));
    }
    d.auditLog.push(row('AuditEntry', {
      id: core.ulid(now), type: 'sale', date: today, refId: id, notes: `ثبت فروش فاکتور #${invoiceNumber} به مبلغ ${money(finalTotal)} افغانی`, createdAt: now,
    }));
    d.nextInvoiceNo = invoiceNumber + 1;
    this.save(d);
    return { ok: true, saleId: id, invoiceNumber, finalTotal };
  }

  /**
   * `OrderFulfillment.fulfill` (+ `OrdersLive.post`): تحویلِ سفارشِ آنلاین ⇒ یک فروشِ عادی در
   * همین دفتر با شناسه‌های ثابتِ سفارش (`o-<سفارش>`، `o-<سفارش>-<قلم>`، `…-m`، `…-a`)،
   * قیمت‌های دقیقِ سفارش، هزینهٔ ارسال به‌صورتِ قلمِ آزاد، `channel = online` و روشِ پرداختِ
   * واقعی؛ سریِ `O` با شمارهٔ سفارش و زمانِ گذارِ تحویل (یکسان روی هر دستگاه). موجودی: پیش از
   * گذار (`allowShortage: false`) سیاستِ صندوق؛ پس از تحویل ثبت با کمبودِ گزارش‌شده (INV-19).
   * order: یک ردیفِ `GET /shop/orders/handover` (یا جزئیاتِ سفارش با خطِ زمانی).
   * @returns {{ok:true, saleId:string, already?:true, short?:string[]} | {ok:false, message:string}}
   */
  fulfillOrder(o, { allowShortage = true } = {}) {
    const d = clone(this.ledger);
    const saleId = `o-${o.id}`;
    const have = d.sales.find((s) => s.orderId === o.id || s.id === saleId || s.id === `w-${o.id}`);
    if (have) return { ok: true, already: true, saleId: have.id };
    if (o.isTest) return { ok: false, message: 'سفارشِ آزمایشی فروش و کسرِ موجودی ندارد' };
    const tag = `سفارش #${o.orderNo}: `;
    const lines = [...(o.lines || [])].sort((a, b) => a.lineNo - b.lineNo);
    if (!lines.length) return { ok: false, message: `قلم‌های سفارش #${o.orderNo} نیامد؛ سفارش را دوباره باز کنید` };
    //  `handedOverAt`: زمان و روزِ فاکتور = زمانِ گذارِ تحویل (نه لحظهٔ ثبت)
    const evAt = (o.events || []).filter((e) => e.kind === 'status' && ['picked_up', 'out_for_delivery', 'delivered'].includes(e.toStatus)).map((e) => e.at);
    const now = o.handedOverAt || (evAt.length ? Math.min(...evAt) : this.now());
    const today = todayOf(now);
    const byId = new Map(d.products.map((p) => [p.id, p]));
    const plan = lines.map((l) => {
      const p = l.productId ? byId.get(l.productId) || null : null;
      let q = l.qtyMilli / 1000;
      //  `SalesEngine.toStockQty`: واحدِ فروش (کارتن) ⇒ واحدِ موجودی
      if (p && l.unit && l.unit === p.saleUnit && l.unit !== p.unit && p.unitFactor > 0) q = Math.round(q * p.unitFactor * 1000) / 1000;
      return { itemId: `${saleId}-${l.lineNo}`, lineNo: l.lineNo, p, q, name: l.name, totalMinor: l.lineTotalMinor };
    });
    const need = new Map();
    for (const x of plan) {
      if (!x.p) continue;
      if (!x.p.active) return { ok: false, message: `${tag}«${x.p.name}» غیرفعال است` };
      need.set(x.p.id, (need.get(x.p.id) || 0) + x.q);
    }
    const short = [];
    for (const [pid, q] of need) {
      const available = this.stock(pid, d);
      if (q <= available + 1e-9) continue;
      const msg = `از «${byId.get(pid).name}» فقط ${available} مانده`;
      //  پیش از گذار: سیاستِ صندوق؛ پس از تحویل (کالا رفته): ثبت و گزارشِ کمبود (INV-19)
      if (!allowShortage) return { ok: false, message: tag + msg };
      short.push(msg);
    }
    const fee = Number(o.deliveryFeeMinor || 0);
    const rows = plan.map((x) => [x.itemId, x.totalMinor]);
    if (fee > 0) rows.push([`${saleId}-fee`, fee]);
    const finalTotal = o.totalMinor / 100;
    let pay;
    //  `OrderFulfillment.atCounter`: نخستین گذارِ تحویل «مشتری گرفت» ⇒ نقدِ پیشخوان (حتی سفارشِ «ارسال»)
    const first = (o.events || []).filter((e) => e.kind === 'status' && ['picked_up', 'out_for_delivery', 'delivered'].includes(e.toStatus))
      .sort((a, b) => a.at - b.at)[0];
    const atCounter = first ? first.toStatus === 'picked_up' : o.status === 'picked_up';
    if (o.paymentMethod === 'online') {
      const paid = o.paymentStatus === 'paid';
      pay = { method: 'online', rows: [{ method: 'online', kind: 'other', amount: o.totalMinor, status: paid ? 'confirmed' : 'pending', ...(paid ? { verification: 'provider' } : {}) }] };
    } else if (o.fulfillment === 'delivery' && !atCounter) {
      pay = { method: 'cod', rows: [{ method: 'cod', kind: 'cash', amount: o.totalMinor, status: 'pending', custody: 'courier' }] };
    } else {
      pay = { method: 'cash', rows: [{ method: 'cash', kind: 'cash', amount: o.totalMinor, status: 'confirmed' }] };
    }
    const digits = (s) => String(s || '').replace(/\D/g, '').slice(-9);
    const phone = digits(o.customer && o.customer.phone);
    const debtor = phone.length >= 7 ? d.debtors.find((x) => digits(x.phone) === phone) : null;
    //  سریِ O و شمارهٔ همان سفارش؛ شمارندهٔ فاکتورِ دستگاه دست نمی‌خورد
    const invoiceNumber = Number(o.orderNo);
    const label = `O-${invoiceNumber}`;
    const discount = Number(o.discountMinor || 0) / 100;
    d.sales.push(row('Sale', {
      id: saleId, invoiceNumber, total: rows.reduce((s, r) => s + r[1], 0) / 100, discountType: 'amount',
      discountValue: discount, discount, finalTotal, paymentMethod: pay.method, debtorId: debtor ? debtor.id : null,
      paidAmount: finalTotal, remaining: 0, status: 'completed', debtGiven: 0, debtSettled: 0, createdAt: now, date: today,
      syncStatus: 'pending', seller: this.seller, orderId: o.id, orderNo: o.orderNo, series: 'O', payments: pay.rows, channel: 'online',
    }));
    for (const x of plan) {
      const total = x.totalMinor / 100;
      d.saleItems.push(row('SaleItem', {
        id: x.itemId, saleId, productId: x.p ? x.p.id : '', name: x.p ? '' : (x.name || 'قلمِ سفارش'), quantity: x.q,
        unitPrice: Math.round((total / x.q) * 100) / 100, purchasePrice: x.p ? x.p.purchasePrice : 0, totalPrice: total, returnedQty: 0,
      }));
    }
    if (fee > 0) {
      d.saleItems.push(row('SaleItem', {
        id: `${saleId}-fee`, saleId, productId: '', name: 'هزینهٔ ارسال', quantity: 1, unitPrice: fee / 100, purchasePrice: 0,
        totalPrice: fee / 100, returnedQty: 0,
      }));
    }
    for (const x of plan) {
      if (!x.p) continue;
      d.stockMovements.push(row('StockMovement', {
        id: `${x.itemId}-m`, productId: x.p.id, type: 'sale', qty: -x.q, date: today,
        notes: `فاکتور #${label} — سفارشِ اینترنتی #${o.orderNo}`, refId: saleId, createdAt: now,
      }));
    }
    d.auditLog.push(row('AuditEntry', {
      id: `${saleId}-a`, type: 'sale', date: today, refId: saleId,
      notes: `ثبت فروش فاکتور #${label} — سفارشِ اینترنتی #${o.orderNo} به مبلغ ${money(finalTotal)} افغانی`, createdAt: now,
    }));
    this.save(d);
    return { ok: true, saleId, short };
  }

  /**
   * `OrderFulfillment.reverse`: سفارشِ برگشته به دکان ⇒ لغوِ فروشِ `o-<سفارش>` با شناسه‌های ثابت
   * (وضعیت، نه شمارنده ⇒ تکرار و دستگاهِ دوم بی‌اثر).
   */
  reverseOrder(o) {
    const d = clone(this.ledger);
    const saleId = `o-${o.id}`;
    const sale = d.sales.find((s) => s.id === saleId);
    if (!sale || sale.status === 'cancelled') return { ok: true, already: true, saleId };
    //  `OrderFulfillment.reverseBlock`: فاکتوری که «مرجوعیِ» دستی گرفته ⇒ لغوِ خاموش نه، بررسیِ صاحب
    if (d.saleItems.some((x) => x.saleId === saleId && x.returnedQty > 1e-9) || (d.returns || []).some((r) => r.saleId === saleId)) {
      return { ok: false, review: true, saleId, message: `سفارش #${o.orderNo} به دکان برگشت ولی برای فاکتورش پیش‌تر «مرجوعیِ» دستی ثبت شده — لغوِ خودکار نشد` };
    }
    const now = o.returnedAt || this.now();
    const today = todayOf(now);
    const reason = 'سفارشِ اینترنتی به دکان برگشت';
    const label = `${sale.series}-${sale.invoiceNumber}`;
    //  همگام‌سازیِ نیمه (حرکت یا ممیزیِ لغو از دستگاهِ دیگر رسیده) ⇒ شناسهٔ تکراری ساخته نمی‌شود
    const moved = new Set(d.stockMovements.map((m) => m.id));
    for (const it of d.saleItems.filter((x) => x.saleId === saleId && x.productId && x.quantity - x.returnedQty > 0 && !moved.has(`${x.id}-x`))) {
      d.stockMovements.push(row('StockMovement', {
        id: `${it.id}-x`, productId: it.productId, type: 'sale_cancel', qty: it.quantity - it.returnedQty, date: today,
        notes: `لغو فروش — فاکتور #${label} — ${reason}`, refId: saleId, createdAt: now,
      }));
    }
    Object.assign(sale, { status: 'cancelled', cancelReason: reason });
    if (!d.auditLog.some((a) => a.id === `${saleId}-x`)) {
      d.auditLog.push(row('AuditEntry', {
        id: `${saleId}-x`, type: 'cancel_sale', date: today, refId: saleId,
        notes: `لغو فروش فاکتور #${label} — سفارشِ اینترنتی #${o.orderNo}: ${reason}`, createdAt: now,
      }));
    }
    this.save(d);
    return { ok: true, saleId };
  }

  /**
   * `OrdersRepository.config` ِ برنامه: `caps=saleOnHandover` ⇒ سرور کفِ تحویلِ این دکان را (اگر
   * نیست) همین لحظه می‌گذارد؛ تحویلِ پیش از آن هرگز فروش نمی‌گیرد.
   */
  ordersConfig() {
    return this.api('GET', '/shop/orders/config?caps=saleOnHandover');
  }

  /** `DebtorEngine.addTransaction(kind = RECEIVE)` */
  receive(debtorId, amount) {
    const d = clone(this.ledger);
    const now = this.now();
    const debtor = d.debtors.find((x) => x.id === debtorId);
    if (!debtor) return { ok: false, message: 'قرض‌دار پیدا نشد' };
    if (!(amount > 0)) return { ok: false, message: 'مبلغ معتبر وارد کنید' };
    const id = core.ulid(now);
    d.transactions.push(row('DebtTransaction', { id, debtorId, type: 'receive', amount, date: todayOf(now), notes: '', createdAt: now }));
    d.auditLog.push(row('AuditEntry', {
      id: core.ulid(now), type: 'customer_payment', date: todayOf(now), refId: id,
      notes: `پرداخت مشتری «${debtor.name}» به مبلغ ${money(amount)} افغانی`, createdAt: now,
    }));
    this.save(d);
    return { ok: true, id };
  }

  /** `CashShift.list/current/totals` */
  shifts(d = this.ledger) {
    const closes = new Map(d.auditLog.filter((a) => a.type === 'shift_close' && a.refId).map((a) => [a.refId, a]));
    return d.auditLog.filter((a) => a.type === 'shift_open').sort((a, b) => b.createdAt - a.createdAt).map((o) => ({ open: o, close: closes.get(o.id) || null }));
  }
  currentShift(d = this.ledger) { return this.shifts(d).find((s) => !s.close) || null; }
  shiftTotals(s, now = this.now(), d = this.ledger) {
    const from = s.open.createdAt; const to = s.close ? s.close.createdAt : now;
    const inW = (t) => t >= from && t <= to;
    const sum = (a, f) => a.reduce((x, y) => x + f(y), 0);
    const t = {
      opening: s.open.amount,
      sales: sum(d.sales.filter((x) => x.status !== 'cancelled' && inW(x.createdAt)), (x) => Math.max(0, x.finalTotal - x.debtGiven)),
      receipts: sum(d.transactions.filter((x) => x.type === 'receive' && inW(x.createdAt)), (x) => x.amount),
      returns: sum(d.returns.filter((x) => inW(x.createdAt)), (x) => x.amount),
      expenses: sum(d.expenses.filter((x) => inW(x.createdAt)), (x) => x.amount),
      suppliers: sum(d.supplierPayments.filter((x) => inW(x.createdAt)), (x) => x.amount) + sum(d.purchases.filter((x) => inW(x.createdAt)), (x) => x.paidAmount),
    };
    t.expected = t.opening + t.sales + t.receipts - t.returns - t.expenses - t.suppliers;
    return t;
  }

  /** `CashShift.open` */
  openShift(opening) {
    if (this.currentShift()) return { ok: false, message: 'صندوق از قبل باز است' };
    const d = clone(this.ledger);
    const now = this.now();
    d.auditLog.push(row('AuditEntry', { id: core.ulid(now), type: 'shift_open', date: todayOf(now), amount: opening, createdAt: now, notes: `باز کردنِ صندوق با ${money(opening)} افغانی — ${this.seller}` }));
    this.save(d);
    return { ok: true };
  }

  /** `CashShift.close` — اختلاف فقط نوشته می‌شود، خودکار «درست» نمی‌شود */
  closeShift(counted, note = '') {
    const s = this.currentShift();
    if (!s) return { ok: false, message: 'صندوقِ بازی نیست' };
    const d = clone(this.ledger);
    const now = this.now();
    const t = this.shiftTotals(s, now, d);
    const diff = counted - t.expected;
    const extra = [note.trim(), this.seller].filter(Boolean).join(' — ');
    d.auditLog.push(row('AuditEntry', {
      id: core.ulid(now), type: 'shift_close', date: todayOf(now), refId: s.open.id, amount: counted, createdAt: now,
      notes: `بستنِ صندوق: مورد انتظار ${money(t.expected)}، شمرده ${money(counted)}، `
        + (Math.abs(diff) < 0.5 ? 'بی اختلاف' : diff > 0 ? `اضافه ${money(diff)}` : `کسری ${money(-diff)}`) + (extra ? ` — ${extra}` : ''),
    }));
    this.save(d);
    return { ok: true, totals: t, diff };
  }

  /** `SalesEngine.recordReturn` — quantities: {saleItemId: qty} */
  returnItems(saleId, quantities, reason = '') {
    const d = clone(this.ledger);
    const now = this.now();
    const today = todayOf(now);
    const sale = d.sales.find((s) => s.id === saleId);
    if (!sale) return { ok: false, message: 'فاکتور پیدا نشد' };
    if (sale.status === 'cancelled') return { ok: false, message: 'این فاکتور لغو شده است' };
    const ratio = sale.total > 0 && sale.discount > 0 ? Math.min(1, Math.max(0, (sale.total - sale.discount) / sale.total)) : 1;
    let total = 0;
    let any = false;
    for (const item of d.saleItems) {
      if (item.saleId !== saleId) continue;
      let q = quantities[item.id];
      if (!(q > 0)) continue;
      const allowed = Math.max(0, item.quantity - item.returnedQty);
      if (q > allowed) q = allowed;
      if (q <= 0) continue;
      const amount = item.quantity > 0 ? roundHalfUp(item.totalPrice / item.quantity * q * ratio) : 0;
      item.returnedQty += q;
      const rid = core.ulid(now);
      d.returns.push(row('SaleReturn', { id: rid, saleId, saleItemId: item.id, productId: item.productId, quantity: q, amount, reason, date: today, createdAt: now }));
      if (item.productId) {
        d.stockMovements.push(row('StockMovement', {
          id: core.ulid(now), productId: item.productId, type: 'customer_return', qty: q, date: today,
          notes: reason || `مرجوعی فاکتور #${sale.invoiceNumber}`, refId: rid, createdAt: now,
        }));
      }
      total += amount;
      any = true;
    }
    if (!any) return { ok: false, message: 'مقدار معتبری برای مرجوعی وارد نشده' };
    if (sale.debtorId && total > 0) {
      const settle = Math.min(total, Math.max(0, sale.debtGiven - sale.debtSettled));
      if (settle > 0) {
        d.transactions.push(row('DebtTransaction', { id: core.ulid(now), debtorId: sale.debtorId, type: 'receive', amount: settle, date: today, notes: `مرجوعی کالا — فاکتور #${sale.invoiceNumber}`, createdAt: now }));
        sale.debtSettled += settle;
      }
    }
    d.auditLog.push(row('AuditEntry', { id: core.ulid(now), type: 'return', date: today, refId: saleId, notes: `مرجوعی به مبلغ ${money(total)} افغانی — فاکتور #${sale.invoiceNumber}`, createdAt: now }));
    this.save(d);
    return { ok: true, amount: total };
  }
}
