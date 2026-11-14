'use strict';
/**
 * PRD-07 — قاعدهٔ واحدِ کالا در سرور؛ همتای `data/ProductDraftRules` ِ برنامه (`unitLocked` و ضریبِ تازهٔ دقیق)، به همان ترتیب:
 *   unit_locked          واحدِ پایهٔ کالایی که ورود، فروش، خرید یا حرکت دارد عوض می‌شود. ورود و فروش به همان واحد نوشته
 *                        شده‌اند و با واحدِ تازه بی‌صدا دوباره خوانده می‌شدند (۴۸ کیلو ⇒ «۴۸ گرام»). همان واحد با فاصله یا
 *                        ي/ك (`purchase-units.key` = `SimilarNames.exactKey`)، واحدِ کهنهٔ خالی، واحدِ خالیِ op و کالای تازه آزاد.
 *   unit_factor_inexact  ضریبِ واحدِ فروشِ دومِ **تازه** (≠ ضریبِ ذخیره) که دقیقاً به هزارمِ واحدِ پایه نیست (`exactMilli` =
 *                        `UnitSale.exactMilli`: ۱/۲۴، ۰٫۰۰۰۵، نامثبت، بیرون از سقف) — فقط وقتی کالا واحدِ فروشِ دوم دارد.
 *                        ضریبِ کهنهٔ دست‌نخورده (همان عدد) هرگز رد نمی‌شود؛ `null` یعنی همان ضریبِ ذخیره.
 * ⛔ ۲-۱۶ ِ صاحب‌کار (R2 الف) — دو راهِ دوگامی بسته (همان پرچم؛ برنامه همان را در `ProductDraftRules` می‌سنجد):
 *   (۱) «واحد را خالی کن، سپس واحدِ تازه»: واحدِ خالی/null ِ op روی کالای گردش‌داری که واحدِ ذخیره دارد = تغییرِ واحد ⇒ `unit_locked`.
 *       واحدِ خالیِ **کهنه** (پیش از قاعده) که تازه نوشته می‌شود همان آزاد می‌ماند.
 *   (۲) «ضریبِ ناجور بی واحدِ فروشِ دوم، سپس فقط واحدِ فروش»: ضریبِ ذخیره فقط وقتی «کهنهٔ دست‌نخورده» است که کالا **همان وقت**
 *       واحدِ فروشِ دوم داشته؛ روشن کردنِ واحدِ فروش روی ضریبِ خوابیده (یا بی ضریب) ضریبِ تازه است ⇒ `unit_factor_inexact`.
 * برای همه، صاحب دکان هم: قاعدهٔ داده است نه مجوز (برنامه هم برای همه می‌سنجد).
 *
 * پرچمِ `shop.product_unit_rules` (`live_config`، دکان یا `all`، فقط `true`؛ همان `orders.flagOf`). ⛔ پیش‌فرض خاموش ⇒ همان
 * امروز؛ op ِ کالایی که نه واحد دارد نه ضریب (`touches`) پرچم را هم نمی‌پرسد.
 *
 * دو راهِ دستگاه:
 *   v3  `authorize` ِ `sync-v3-apply.js` (`judgeV3`) درونِ تراکنش و قفلِ push: ردیفِ ذخیره و گردش از `sync_v3_rows`
 *       (opهای پیشینِ همان push پیش‌تر نشسته‌اند). op ِ پلِ v1 (`origin` = v1) در v1 سنجیده شده بود.
 *   v1  میان‌افزارِ فقط‌دکانِ `newer-fields.v1NewerFields` (`v1Guard`) پس از پالایش، بیرونِ تراکنش: ردیفِ ذخیره و گردش از
 *       `sync_rows` **به‌علاوهٔ opهای پیشینِ همان دسته** (حالِ کالا و ورود/فروشِ تازه) ⇒ یک دسته دورِ قاعده نمی‌زند؛ op ِ ردشده
 *       حالِ کالا را عوض نمی‌کند. ⛔ ‎lib/sync-v1.js‎ و ‎routes/sync-v1.js‎ ِ مشترک با پمپ دست نمی‌خورند.
 *   قدیم `legacyGuard` (`/api/sync`، برنامهٔ کهنه، F0-02؛ د۱۸) پیش از `sync.pushChanges`، بیرونِ تراکنش، همان قاعده روی **ردیفِ
 *       کامل**: حالِ کالا از جدولِ قدیمِ `products` (همان ردیفی که جایگزین می‌شود)، گردش از جدول‌های قدیم **و** دفترِ v1 و
 *       ردیف‌های پیشینِ همان دسته. ردشده ⇐ تعارضِ همان ردیف با دادهٔ سرور (`conflicts`، مثلِ `stale`) تا برنامهٔ کهنه برگردد؛
 *       بقیهٔ دسته می‌نشیند. ⛔ ‎lib/sync.js‎ دست نخورد.
 */
const { key } = require('./purchase-units');

const FLAG = 'shop.product_unit_rules';
const LOCKED = 'unit_locked';
const INEXACT = 'unit_factor_inexact';
/** همان `PackBarcodes.MAX_SIZE_MILLI` */
const MAX_MILLI = 1_000_000_000;
/** گردشِ کالا = همان چهار فهرستِ `unitLocked` */
const HISTORY = Object.freeze(['warehouseEntries', 'saleItems', 'stockMovements', 'purchases']);

const isObj = (v) => v !== null && typeof v === 'object' && !Array.isArray(v);
const has = (o, k) => isObj(o) && Object.prototype.hasOwnProperty.call(o, k);
const filled = (v) => typeof v === 'string' && v.trim() !== '';
/** عدد یا متنِ عددی (همان خوانشِ `toMilli` ِ کاتالوگ)؛ وگرنه NaN */
function numOf(v) {
  if (typeof v === 'string' && v.trim() !== '') return Number(v);
  return typeof v === 'number' ? v : NaN;
}

/** همان `UnitSale.factorMilli`: ضریب ⇒ هزارمِ واحدِ پایه (گردِ نیمه به بالا)؛ نامثبت، NaN یا بیرون از سقف ⇒ null */
function factorMilli(f) {
  if (typeof f !== 'number' || !Number.isFinite(f) || f <= 0) return null;
  const m = Math.round(f * 1000);
  return m >= 1 && m <= MAX_MILLI ? m : null;
}

/** همان `UnitSale.exactMilli`: فقط ضریبی که دقیقاً به هزارم نوشته می‌شود */
function exactMilli(f) {
  const m = factorMilli(f);
  return m !== null && Math.abs(f * 1000 - m) <= 1e-9 * m ? m : null;
}

/**
 * ⛔ بازبینیِ ۴۰ (SR40-1): واحدِ **نامتنی** (عدد، بولی، آرایه، شیء، `$inc` ِ v1) — برنامه هرگز نمی‌فرستدش و v1/v3 آن را همان‌طور
 * می‌نشانند ⇒ همیشه «تغییرِ واحد» است (پیش‌تر نه op ِ واحددار بود نه تغییر: ۴۸ «دانه» ⇒ ۴۸ «7» در یک op). null و متن همان قاعدهٔ برنامه.
 */
const oddUnit = (fields) => has(fields, 'unit') && fields.unit !== null && typeof fields.unit !== 'string';

/** op ِ کالا که واحد (خالی هم، ۲-۱۶)، واحدِ فروشِ دوم یا ضریب می‌نویسد؟ (بی آن هیچ پرسشی نیست) */
const touches = (fields) => isObj(fields)
  && (has(fields, 'unit') || filled(fields.saleUnit) || (has(fields, 'unitFactor') && fields.unitFactor !== null));

/**
 * @param fields  فیلدهای op (درج یا ویرایش)
 * @param before  دادهٔ ذخیرهٔ همان کالا یا null (کالای تازه)
 * @param moved   `async () => boolean` — گردش دارد؟ فقط وقتی واحد واقعاً عوض شود پرسیده می‌شود
 * @returns {Promise<string>} دلیلِ رد یا ''
 */
async function reasonOf(fields, before, moved) {
  if (!isObj(fields)) return '';
  const b = isObj(before) ? before : null;
  //  ۲-۱۶ (۱): کلیدِ `unit` با خالی/null همان «خالی کردن» است
  const blanked = has(fields, 'unit') && (fields.unit === null || (typeof fields.unit === 'string' && !filled(fields.unit)));
  const changed = () => oddUnit(fields) || blanked || (filled(fields.unit) && key(fields.unit) !== key(b.unit));
  if (b && filled(b.unit) && changed() && await moved()) return LOCKED;
  const setsFactor = has(fields, 'unitFactor') && fields.unitFactor !== null;
  const saleUnit = has(fields, 'saleUnit') && fields.saleUnit !== null ? fields.saleUnit : b?.saleUnit;
  if (!filled(saleUnit)) return '';
  //  ۲-۱۶ (۲): ضریبِ ذخیره فقط وقتی «کهنه» است که واحدِ فروشِ دوم همان وقت روشن بود
  const live = Boolean(b) && filled(b.saleUnit);
  if (!setsFactor && live) return '';
  const f = setsFactor ? numOf(fields.unitFactor) : numOf(b?.unitFactor);
  if (!setsFactor && Number.isNaN(f)) return '';   //  بی ضریبِ ذخیره: همان امروز (برنامه خودش «ضریب لازم است» می‌گوید)
  if (live && f === numOf(b.unitFactor)) return '';
  return exactMilli(f) === null ? INEXACT : '';
}

const on = (shopId, run = null) => require('./orders').flagOf(shopId, FLAG, run);

/** گردشِ کالا در دفترِ دکان (`sync_rows` یا `sync_v3_rows`)؛ ردیفِ حذف‌شده نه */
function movedIn(run, table, shopId, productIds) {
  return run(
    `SELECT DISTINCT data->>'productId' AS pid FROM ${table === 'v3' ? 'sync_v3_rows' : 'sync_rows'}
      WHERE app='shop' AND account_kind='shop' AND account_id=$1 AND table_name = ANY($2::text[])
        AND deleted_at IS NULL AND data->>'productId' = ANY($3::text[])`,
    [shopId, HISTORY, productIds]
  ).then((r) => new Set(r.rows.map((x) => x.pid)));
}

/** v3 (`authorize`): دلیلِ رد یا null */
async function judgeV3(c, ctx, op, row) {
  if (op.table !== 'products' || (op.kind !== 'insert' && op.kind !== 'update') || !touches(op.fields)) return null;
  const run = (sql, params) => c.query(sql, params);
  if (!(await on(ctx.accountId, run))) return null;
  const before = row && isObj(row.data) ? row.data : null;
  const why = await reasonOf(op.fields, before, async () => (await movedIn(run, 'v3', ctx.accountId, [op.row_id])).has(op.row_id));
  return why || null;
}

/** همان سنجشِ شکلِ `validateOp` ِ v1 (`OP_ID_RE`، `TYPES`، `row_id` ≤ ۸۰) */
const OP_ID_RE = /^[A-Za-z0-9_-]{1,64}$/;
const TYPES = Object.freeze(['insert', 'update', 'delete']);
const isInc = (v) => isObj(v) && Object.keys(v).length === 1 && Number.isFinite(v.$inc);

/**
 * خوانشِ همان op که v1 می‌نشاند — همان نام‌های `normalize` و همان `tableOf` ِ v1، مثلِ `purchase-units.v1Guard`.
 * ⛔ نه `validateOp` و نه شیءِ تازه از کلیدهای `fields` (CodeQL: remote-property-injection در `applyOp` ِ `sync-v1.js`).
 * opی که v1 به‌هر‌حال رد می‌کند ⇒ null (داوری نمی‌شود، دلیلِ v1 همان می‌ماند).
 */
function v1OpOf(raw) {
  if (!isObj(raw)) return null;
  const opId = raw.op_id ?? raw.opId ?? '';
  const rowId = raw.row_id ?? raw.rowId ?? raw.id ?? '';
  const type = raw.type ?? raw.op_type ?? raw.opType ?? '';
  if (typeof opId !== 'string' || !OP_ID_RE.test(opId) || typeof rowId !== 'string' || !rowId || rowId.length > 80) return null;
  if (!TYPES.includes(type)) return null;
  const v1 = require('./sync-v1');
  const rawTable = raw.table ?? raw.table_name ?? raw.tableName ?? '';
  const table = v1.tableOf('shop', rawTable);
  if (!table) return null;
  let fields = {};
  if (type === 'delete') {
    const f = raw.fields;
    if (f !== undefined && f !== null && !(isObj(f) && !Object.keys(f).length)) return null;
  } else {
    fields = raw.fields;
    if (!isObj(fields)) return null;
    const keys = Object.keys(fields);
    if (type === 'insert' && !keys.length) return null;
    for (const k of keys) {
      if (!k || k.length > 64 || k.startsWith('$')) return null;
      const v = fields[k];
      if (isObj(v) && Object.keys(v).some((x) => x.startsWith('$')) && !(type === 'update' && isInc(v))) return null;
    }
    if (Buffer.byteLength(JSON.stringify(fields), 'utf8') > v1.MAX_FIELDS_BYTES) return null;
  }
  const hash = raw.hash ? String(raw.hash) : '';
  if (hash && hash !== v1.hashOf({ table: rawTable, row_id: rowId, type, fields })) return null;
  return { op_id: opId, row_id: rowId, type, table, fields: type === 'delete' ? null : fields };
}

/** v1: opهای کالا که واحد/ضریب دارند — همگام، تا دستهٔ بی آن‌ها بی هیچ پرسشی بگذرد */
function v1Hits(ops) {
  if (!Array.isArray(ops) || !ops.length) return [];
  const out = [];
  ops.forEach((raw, i) => {
    if (!isObj(raw) || !touches(raw.fields)) return;
    const o = v1OpOf(raw);
    if (o && o.table === 'products' && (o.type === 'insert' || o.type === 'update')) out.push(i);
  });
  return out;
}

/** v1 (پس از پالایشِ `v1NewerFields`): opهای ردشده از دسته بیرون و پاسخشان سرِ جایشان (`v1-reject`) */
async function v1Guard(req, res) {
  const ctx = req.sync;
  //  همان الگوی نگهبان‌های دیگرِ v1 (`staff-approval`، `purchase-units`): فقط فهرستِ op، وگرنه هیچ (CodeQL: loop bound)
  const ops = Array.isArray(req.body?.ops) ? req.body.ops : null;
  if (!ops || !ops.length || !(await on(ctx.accountId))) return;
  const all = ops.map(v1OpOf);
  const ids = [...new Set(all.filter((o) => o && o.table === 'products').map((o) => o.row_id))];
  const { query } = require('../db');
  const stored = new Map((await query(
    `SELECT row_id, data FROM sync_rows WHERE app='shop' AND account_kind='shop' AND account_id=$1
        AND table_name='products' AND row_id = ANY($2::text[])`, [ctx.accountId, ids]
  )).rows.map((r) => [r.row_id, isObj(r.data) ? { ...r.data } : null]));
  let dbMoved = null;
  const movedBefore = new Set();   //  ورود/فروش/خرید/حرکتِ همین دسته، پیش از op
  const rejected = new Map();
  for (let i = 0; i < all.length; i++) {
    const o = all[i];
    if (!o || o.type === 'delete' || !isObj(o.fields)) continue;
    if (HISTORY.includes(o.table) && typeof o.fields.productId === 'string') { movedBefore.add(o.fields.productId); continue; }
    if (o.table !== 'products') continue;
    const before = stored.get(o.row_id) || null;
    const why = await reasonOf(o.fields, before, async () => {
      if (movedBefore.has(o.row_id)) return true;
      if (!dbMoved) dbMoved = await movedIn(query, 'v1', ctx.accountId, ids);
      return dbMoved.has(o.row_id);
    });
    if (why) { rejected.set(i, { op_id: o.op_id, status: 'rejected', reason: why }); continue; }
    //  حالِ پس از op فقط با همان سه کلیدِ ثابتی که `reasonOf` می‌خواند — ⛔ نه `{...o.fields}` (RULES: شیء از کلیدهای درخواست)
    const f = o.fields;
    const b = before || {};
    stored.set(o.row_id, {
      unit: has(f, 'unit') ? f.unit : b.unit,
      saleUnit: has(f, 'saleUnit') ? f.saleUnit : b.saleUnit,
      unitFactor: has(f, 'unitFactor') ? f.unitFactor : b.unitFactor,
    });
  }
  if (!rejected.size) return;
  //  ⛔ همان `v1-reject.rejectOps`، ولی فهرستِ همان درخواست **در جا** کوتاه می‌شود، نه `req.body.ops = …`: این نگهبان پس از
  //  `await` (بیرون از اجرای همگامِ میان‌افزار) می‌نویسد و CodeQL آن نوشتن را تا `applyOp` ِ `sync-v1.js` دنبال می‌کرد
  //  (چهار هشدارِ remote-property-injection ِ دستهٔ ۴۰؛ با برداشتنِ همین قلاب سبز شد)
  const original = ops.slice();
  let w = 0;
  for (let i = 0; i < original.length; i++) if (!rejected.has(i)) ops[w++] = original[i];
  ops.length = w;
  const send = res.json.bind(res);
  res.json = (body) => {
    if (body && body.ok && Array.isArray(body.results)) {
      const rest = body.results.slice();
      const merged = [];
      for (let i = 0; i < original.length; i++) merged.push(rejected.has(i) ? rejected.get(i) : rest.shift());
      body.results = merged.filter(Boolean).concat(rest);
    }
    return send(body);
  };
}

/** گردش در جدول‌های راهِ قدیم (نامِ مجموعه ⇐ جدول، همان `TABLES` ِ ‎lib/sync.js‎) */
const LEGACY_HISTORY = Object.freeze({ warehouseEntries: 'inventory', saleItems: 'sale_items', stockMovements: 'stock_movements', purchases: 'purchases' });

/**
 * راهِ قدیم (`/api/sync`): دسته ⇒ `null` (هیچ ردی) یا `{ keep, conflicts }` — `keep` همان ردیف‌ها بی ردشده‌ها (همان ترتیب) و
 * `conflicts` به شکلِ تعارضِ `pushChanges` (`collection`، `id`، `reason`، و دادهٔ سرور اگر ردیف هست). دستهٔ بی کالای واحددار
 * پرچم را هم نمی‌پرسد.
 */
async function legacyGuard(shopId, changes) {
  //  دستهٔ بیش از سقف را خودِ `pushChanges` با ۴۰۰ رد می‌کند
  if (!Array.isArray(changes) || !changes.length || changes.length > require('./sync').MAX_BATCH) return null;
  const live = (ch) => isObj(ch) && ch.deleted !== true && isObj(ch.data);
  const prod = (ch) => live(ch) && ch.collection === 'products' && typeof ch.id === 'string' && ch.id && ch.id.length <= 80;
  if (!changes.some((ch) => prod(ch) && touches(ch.data)) || !(await on(shopId))) return null;
  const { query } = require('../db');
  const ids = [...new Set(changes.filter(prod).map((ch) => ch.id))];
  const stored = new Map((await query(
    'SELECT id, updated_at, version, deleted, data FROM products WHERE shop_id=$1 AND id = ANY($2::text[])', [shopId, ids]
  )).rows.map((r) => [r.id, r]));
  const state = new Map([...stored].map(([id, r]) => [id, isObj(r.data) ? r.data : null]));
  let dbMoved = null;
  const movedDb = async () => {
    if (dbMoved) return dbMoved;
    const old = await query(
      Object.values(LEGACY_HISTORY).map((t) => `SELECT data->>'productId' AS pid FROM ${t}
        WHERE shop_id=$1 AND deleted = false AND data->>'productId' = ANY($2::text[])`).join(' UNION '),
      [shopId, ids]
    );
    dbMoved = new Set([...old.rows.map((x) => x.pid), ...(await movedIn(query, 'v1', shopId, ids))]);
    return dbMoved;
  };
  const movedBefore = new Set();
  const conflicts = [];
  const keep = [];
  for (const ch of changes) {
    if (live(ch) && LEGACY_HISTORY[ch.collection] && typeof ch.data.productId === 'string') movedBefore.add(ch.data.productId);
    if (!prod(ch) || !touches(ch.data)) { keep.push(ch); continue; }
    const before = state.get(ch.id) || null;
    const why = await reasonOf(ch.data, before, async () => movedBefore.has(ch.id) || (await movedDb()).has(ch.id));
    if (!why) {
      const d = ch.data;
      state.set(ch.id, { unit: d.unit, saleUnit: d.saleUnit, unitFactor: d.unitFactor });
      keep.push(ch);
      continue;
    }
    const r = stored.get(ch.id);
    conflicts.push(r ? {
      collection: 'products', id: ch.id, reason: why,
      serverUpdatedAt: Number(r.updated_at), serverVersion: r.version, deleted: r.deleted === true,
      data: r.deleted ? null : r.data,
    } : { collection: 'products', id: ch.id, reason: why, data: null });
  }
  return conflicts.length ? { keep, conflicts } : null;
}

module.exports = { FLAG, LOCKED, INEXACT, HISTORY, factorMilli, exactMilli, touches, reasonOf, judgeV3, v1OpOf, v1Hits, v1Guard, legacyGuard };
