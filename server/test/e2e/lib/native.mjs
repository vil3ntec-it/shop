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
 *   • کارهای دکان (کالا، ورودِ انبار، فروش، قرض، صندوق، مرجوعی) سطر به سطر از
 *     `WarehouseEngine.kt`، `SalesEngine.kt`، `DebtorEngine.kt` و `CashShift.kt` برگردانده
 *     شده‌اند (نامِ تابعِ کاتلین کنارِ هر کدام).
 *   • «حالتِ هواپیما»: `offline = true` ⇒ هیچ درخواستی بیرون نمی‌رود (همان `Backend.isOnline`).
 *   • پس از بازیابیِ سرور (§۹.۵): دوران، دوباره‌فرستادن و ساختنِ دوباره از Snapshot همان
 *     `sync/v1/V1Restore.kt` و `core/sync/RestoreReplay.kt` (`restorePlan`، `replayAbove`).
 */
import { createRequire } from 'node:module';
import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';

const require = createRequire(import.meta.url);
const ROOT = path.join(import.meta.dirname, '..', '..', '..', '..');
export const core = require(path.join(ROOT, 'license', 'sync-core.js'));
export const MODELS_KT = path.join(ROOT, 'android-native', 'app', 'src', 'main', 'java', 'ir', 'vil3ntec', 'tohid', 'data', 'Models.kt');
export const APP_VERSION = JSON.parse(fs.readFileSync(path.join(ROOT, 'server', 'package.json'), 'utf8')).version;

/* ---- §۹.۵ — قرینهٔ سطر به سطرِ `core/sync/RestoreReplay.kt` و `sync/v1/V1Restore.kt` ---- */

/** `RestoreReplay.Epoch.from` */
export function epochFrom(e) {
  if (!e || typeof e !== 'object' || typeof e.id !== 'string' || !e.id) return null;
  const base = Number(e.base);
  const head = Number(e.head);
  if (!Number.isInteger(base) || !Number.isInteger(head) || base < 0 || head < 0) return null;
  return { id: e.id, base, head };
}

/** `RestoreReplay.plan` ⇒ {epoch, restore, base} */
export function restorePlan(known, cursor, server, sentAbove = () => false) {
  if (!server) return { epoch: known, restore: false, base: 0 };
  const base = Math.min(server.base, server.head);
  const rotated = known !== '' && known !== server.id;
  const behind = cursor > server.head;
  if (!rotated && !behind) return { epoch: server.id, restore: false, base };
  return { epoch: server.id, restore: cursor > base || sentAbove(base), base };
}

/** `ReplayBook.replayAbove`: ترتیبِ نخستین فرستادن، آخرین server_seq ِ هر op_id */
export function replayAbove(sent, base) {
  const byId = new Map();
  for (const s of sent) {
    const prev = byId.get(s.id);
    byId.set(s.id, prev ? { ...prev, seq: s.seq } : s);
  }
  return [...byId.values()].filter((s) => s.seq > base);
}

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
    for (const raw of m[2].split('\n')) {
      const line = raw.replace(/\/\/.*$/, '').trim();
      if (!line) continue;
      if (/@EncodeDefault\(EncodeDefault\.Mode\.NEVER\)/.test(line)) never = true;
      const f = /^(?:@SerialName\("([^"]+)"\)\s*)?val (\w+):\s*([^=]+?)(?:\s*=\s*(.+?))?,?$/.exec(line.replace(/^@\w+(\([^)]*\))?\s+(?=@SerialName|val )/, ''));
      if (!f) continue;
      const def = f[4] !== undefined ? literal(f[4]) : null;
      fields.push({ name: f[2], key: f[1] || f[2], type: f[3].trim(), required: f[4] === undefined, def: def ? def.v : undefined, expr: def ? null : (f[4] || null), never });
      never = false;
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
   */
  constructor(E, label, { deviceId = null, seller = '' } = {}) {
    this.E = E;
    this.label = label;
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
    //  §۹.۵ (`V1Restore`): دورانِ بازیابیِ سرور، «در انتظارِ ساختنِ دوباره» و دفترچهٔ `ReplayBook`
    this.epoch = '';
    this.restorePending = false;
    this.sent = [];          //  …-sent.jsonl: {id, seq, op} ِ پذیرفته‌شده‌های خودِ دستگاه
    this.replayed = 0;
    this.rebuilt = 0;
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
      'X-App-Version': APP_VERSION,
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

  /** `ShopStore.save` ⇒ `SyncV1Engine.record`: تفاضل با سایه ⇒ صف */
  save(next) {
    const { ops } = core.diff(this.shadow, next);
    if (ops.length) this.queue.push(...ops);
    this.shadow = core.shadowOf(next);
    this.ledger = next;
    return ops.length;
  }

  /** `V1Restore.check` — هر دور: دورانِ سرور؛ بازیابی ⇒ opهای خودِ دستگاه جلوی صف و «در انتظار» */
  async checkEpoch() {
    let r;
    try { r = await this.api('GET', '/shop/sync-epoch'); } catch { return; }
    if (r.status !== 200) return;                              //  سرورِ کهنه/خاموش/خطا ⇒ رفتارِ پیشین
    const top = this.sent.reduce((m, x) => Math.max(m, x.seq), 0);
    const plan = restorePlan(this.epoch, this.cursor, epochFrom(r.body?.epoch), (base) => top > base);
    if (!plan.restore) { this.epoch = plan.epoch; return; }
    //  `OpQueue.pushFront`: جلوی صف، همان op_id، بی تکرارِ آنچه در صف هست
    const have = new Set(this.queue.map((o) => o.op_id));
    const again = replayAbove(this.sent, plan.base).filter((x) => !have.has(x.id) && have.add(x.id)).map((x) => x.op);
    this.queue.unshift(...again);
    this.replayed += again.length;
    //  `SyncStateV1.commitRestore`
    this.epoch = plan.epoch;
    this.restorePending = true;
  }

  /** `V1Restore.rebuild` + `SyncCommit.rebuild`: دفتر = Snapshot + opهای همین حالا در صف */
  async rebuild() {
    const r = await this.api('GET', '/sync/v1/snapshot');
    if (r.status !== 200) return false;
    let next = core.fromSnapshot(r.body, this.ledger);
    if (this.queue.length) next = core.applyRemote(next, this.queue).data;
    this.ledger = next;
    this.shadow = core.shadowOf(next);
    this.cursor = Number(r.body.cursor || 0);
    this.restorePending = false;
    this.rebuilt++;
    return true;
  }

  /** `SyncV1Engine.runOnce`: دوران ⇒ همهٔ صف در دسته‌ها ⇒ (در انتظار و صفِ خالی ⇒ از نو) ⇒ pull تا تهِ cursor */
  async sync() {
    if (this.offline) { this.lastError = 'offline'; return { ok: false, offline: true }; }
    try {
      await this.checkEpoch();
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
        const r = await this.api('POST', '/sync/v1/push', { device_id: this.deviceId, schema_version: core.SCHEMA_VERSION, queued: this.queue.length, ops: batch });
        if (r.status === 426) { this.holding = true; this.lastError = r.body?.message || 'upgrade'; return { ok: false, status: 426 }; }
        if (r.status !== 200) { this.lastError = `${r.status} ${r.body?.error?.code || ''}`.trim(); return { ok: false, status: r.status, body: r.body }; }
        this.holding = false;
        const res = r.body.results || [];
        const ok = new Set(res.filter((x) => x.status === 'applied' || x.status === 'duplicate').map((x) => x.op_id));
        //  `V1Restore.accepted`: پذیرفته‌شده‌ها با server_seq — پیش از بیرون رفتن از صف
        const seqOf = new Map(res.filter((x) => ok.has(x.op_id) && Number(x.server_seq) > 0).map((x) => [x.op_id, Number(x.server_seq)]));
        for (const op of batch) if (seqOf.has(op.op_id)) this.sent.push({ id: op.op_id, seq: seqOf.get(op.op_id), op });
        //  `V1Restore.isReplay`: ردِ opِ دوباره‌فرستاده ردشده‌ای نیست که کاربر ببیند
        const sentIds = new Set(this.sent.map((x) => x.id));
        const bad = res.filter((x) => !ok.has(x.op_id));
        for (const b of bad) if (!sentIds.has(b.op_id)) this.dropped.push({ ...batch.find((o) => o.op_id === b.op_id), why: b.reason || b.status });
        const gone = new Set([...ok, ...bad.map((b) => b.op_id)]);
        this.queue = this.queue.filter((o) => !gone.has(o.op_id));
      }
      if (this.restorePending && !this.queue.length) await this.rebuild();
      for (let i = 0; !this.restorePending && i < 50; i++) {
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
    this.ledger = core.applyRemote(this.ledger, ops).data;
    this.shadow = core.shadowOf(core.applyRemote(this.shadow, ops).data);
  }

  /** `SyncV1Engine.firstSync`: دفترِ خالی ⇒ Snapshot؛ وگرنه کلِ دفتر یک بار */
  async firstSync() {
    const rows = core.COLLECTIONS.reduce((s, c) => s + (this.ledger[c] || []).length, 0);
    if (rows === 0) {
      //  `restoreFromServer`: دورانِ پیش از Snapshot — دستگاهِ تازه هم بداند Snapshot ِ کدام دوران است (§۹.۵)
      const ep = await this.api('GET', '/shop/sync-epoch').catch(() => null);
      const r = await this.api('GET', '/sync/v1/snapshot');
      if (r.status !== 200) return { ok: false, status: r.status, body: r.body };
      const next = core.fromSnapshot(r.body, this.ledger);
      this.ledger = next;
      this.shadow = core.shadowOf(next);
      this.cursor = Number(r.body.cursor || 0);
      this.snapshotAt = Date.now();
      const seen = ep && ep.status === 200 ? epochFrom(ep.body?.epoch) : null;
      if (seen) { this.epoch = seen.id; this.restorePending = false; }
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
