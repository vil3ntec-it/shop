'use strict';
/**
 * بانکِ تصاویر — انبار، سه فضا، جست‌وجو، انتخاب و بررسیِ مدیر (v3 ۳۴، ۳۶.۹، ۳۷).
 * شرحِ جدول‌ها: ‎migrations/087…089‎.
 *
 *   پرچم ── `shop.image_bank` (scope = دکان یا `all`) درِ دکان را باز می‌کند؛
 *           `image_bank.central` (scope = `all`) پنلِ بانکِ مدیر را. ⛔ هر دو
 *           پیش‌فرض خاموش؛ خاموش ⇒ هیچ ردیفی نوشته نمی‌شود و همه‌چیز مثلِ امروز.
 *   فضا ─── central | shop | pending (۳۷.۲). دسترسی همین‌جا و در SQL سنجیده
 *           می‌شود، نه با پنهان‌کردنِ دکمه. عکسِ دکان بی اجازهٔ صریحِ صاحبِ دکان
 *           هرگز کپی نمی‌شود و مدیر آن را نمی‌بیند.
 *   نسخه ── ۱ اصل است و دست نمی‌خورد؛ نسخهٔ تازه جدا. نسخه‌های کوچک
 *           (thumb/display/zoom) را برنامه می‌سازد؛ سرور می‌سنجد، فرادادهٔ
 *           حساس را برمی‌دارد و یک‌بارنویس نگه می‌دارد (نشانیِ نسخه‌دار).
 *   تطبیق ─ ‎lib/image-identity.js‎: بارکدِ معتبر + مشخصاتِ همسان ⇒ قطعی؛
 *           نامِ مشابه هرگز به‌تنهایی قطعی نیست.
 *
 * ⛔ هیچ قیمت، موجودی، بدهی یا دادهٔ دکانی در جدول‌های بانک نیست؛ جست‌وجو
 *    فقط جدول‌های بانک را می‌خواند (۳۷.۹ بندِ آخر).
 * ⛔ هیچ چیزی به سرویسِ بیرونی نمی‌رود — ‎lib/image-pipeline.js‎.
 */
const crypto = require('crypto');
const db = require('../db');
const { one, many, now, tx } = db;
const T = require('./text-core');
const ID = require('./image-identity');
const bytes = require('./image-bytes');
const photos = require('./product-photos');
const { ulid } = require('./ulid');
const { stripControl } = require('./validate');
const { ApiError, badRequest, notFound, conflict } = require('../middleware/errors');

const SHOP_FLAG = 'shop.image_bank';
const CENTRAL_FLAG = 'image_bank.central';

//  همان سقف‌های عکسِ کالا (۰۴۵/۰۴۶) — عکسِ دکان فشرده می‌آید، اصلِ بانک تا ۲ مگابایت
const SHOP_MAX_BYTES = photos.MAX_BYTES;
const BANK_MAX_BYTES = photos.INBOX_MAX_BYTES;
const SHOP_MAX_IMAGES = photos.MAX_PHOTOS;
const DERIV = {
  thumb: { side: 400, bytes: 150 * 1024 },
  display: { side: 1200, bytes: SHOP_MAX_BYTES },
  zoom: { side: 2400, bytes: BANK_MAX_BYTES },
};
const SIZES = ['original', ...Object.keys(DERIV)];
const MIN_SIDE = 300;            // کوچک‌تر ⇒ نشانِ «too_small» برای بازبین
const PAGE_MAX = 50;
const CANDIDATE_MAX = 500;
const FUZZY_SCAN = 500;
const ROWS_MAX = 1000;
const ROLES = ['main', 'back', 'label', 'variant', 'promo', 'detail'];
const LICENSES = ['unknown', 'licensed', 'restricted', 'denied'];
const REPORT_KINDS = ['wrong_product', 'old_packaging', 'quality', 'license', 'other'];

// ════════════════════════════════════════════════════════════════════
//  کمک‌ها
// ════════════════════════════════════════════════════════════════════

const str = (v, max = 200) => stripControl(String(v == null ? '' : v)).trim().slice(0, max);
const json = (t, def) => { try { return JSON.parse(t); } catch { return def; } };
const sha256 = (buf) => crypto.createHash('sha256').update(buf).digest('hex');
const q1 = async (c, t, p) => (await c.query(t, p)).rows[0] || null;
const qa = async (c, t, p) => (await c.query(t, p)).rows;
const tooLarge = (max) => new ApiError(413, 'too_large', `عکس بزرگ‌تر از ${Math.round(max / 1024)} کیلوبایت است`);
const intIn = (v, lo, hi, def) => {
  const n = Number(v);
  return Number.isInteger(n) && n >= lo && n <= hi ? n : def;
};
const oneOf = (v, list, def) => (list.includes(v) ? v : def);
const clamp = (v, def) => Math.min(PAGE_MAX, Math.max(1, Math.floor(Number(v)) || def));
const placeholders = (n, from = 1) => Array.from({ length: n }, (_, i) => '$' + (i + from)).join(',');

async function flagOf(key, scope) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [key, 'all', String(scope || 'all')]
  );
  let v;
  for (const r of rows) if (r.scope === 'all') v = json(r.value, null);
  for (const r of rows) if (r.scope !== 'all') v = json(r.value, null);
  return v === true;
}
const shopEnabled = (shopId) => flagOf(SHOP_FLAG, shopId);
const centralEnabled = () => flagOf(CENTRAL_FLAG, 'all');

function fileUrl(base, id, version, size) {
  return `${base}/file/${encodeURIComponent(id)}/${version}/${size}`;
}

// ════════════════════════════════════════════════════════════════════
//  بایت‌ها و نسخه‌ها
// ════════════════════════════════════════════════════════════════════

/** سنجشِ اصل: نوع از محتوا، ابعاد، سقفِ حجم؛ نشانه‌های کیفیت برای بازبین. */
function checkOriginal(buf, max) {
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('عکس خالی است', 'image_empty');
  if (buf.length > max) throw tooLarge(max);
  const info = bytes.inspect(buf);
  const flags = [...info.warnings];
  if (Math.max(info.width, info.height) < MIN_SIDE) flags.push('too_small');
  return { info, flags };
}

async function putBlob(c, buf, info) {
  const hash = sha256(buf);
  await c.query(
    `INSERT INTO image_blobs (hash, mime, size, width, height, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7) ON CONFLICT (hash) DO NOTHING`,
    [hash, info.mime, buf.length, info.width, info.height, buf, now()]
  );
  return hash;
}

/** همان محتوا در همان دامنهٔ دید (دکانِ خود، یا central+pending) — فقط نشان. */
async function duplicatesOf(c, hash, space, ownerShopId, exceptId = '') {
  const rows = space === 'shop'
    ? await qa(c, `SELECT DISTINCT i.id FROM images i JOIN image_versions v ON v.image_id=i.id
                    WHERE v.blob_hash=$1 AND i.owner_shop_id=$2 AND i.id<>$3 LIMIT 5`, [hash, ownerShopId, exceptId])
    : await qa(c, `SELECT DISTINCT i.id FROM images i JOIN image_versions v ON v.image_id=i.id
                    WHERE v.blob_hash=$1 AND i.space IN ('central','pending') AND i.id<>$2 LIMIT 5`, [hash, exceptId]);
  return rows.map((r) => r.id);
}

const LIC0 = { source: '', holder: '', type: '', status: 'unknown', commercial: false };

/** ردیفِ images + نسخهٔ ۱ (اصل). بلاب پیش‌تر با putBlob نشسته است. */
async function insertImage(c, f) {
  const id = 'img_' + ulid();
  const t = now();
  const lic = { ...LIC0, ...(f.license || {}) };
  const dups = f.dups || await duplicatesOf(c, f.hash, f.space, f.ownerShopId || '');
  await c.query(
    `INSERT INTO images (id, space, owner_shop_id, status, role, bank_product_id, shop_product_id,
        match_status, match_reasons, license_source, license_holder, license_type, license_status,
        commercial_use, license_checked_at, origin_shop_id, origin_image_id, consent_by, consent_at,
        proposal, current_version, search_enabled, duplicate_of, quality_flags, batch_id, created_by,
        rev, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16,$17,$18,$19,$20,1,false,$21,$22,$23,$24,
             nextval('image_bank_rev'),$25,$25)`,
    [id, f.space, f.ownerShopId || '', f.status, oneOf(f.role, ROLES, 'main'), f.bankProductId || '',
      f.shopProductId || '', oneOf(f.match, ID.MATCH, 'unconfirmed'), JSON.stringify(f.reasons || []),
      str(lic.source), str(lic.holder), str(lic.type, 64), oneOf(lic.status, LICENSES, 'unknown'),
      lic.commercial === true, lic.status === 'licensed' ? t : 0,
      f.originShopId || '', f.originImageId || '', f.consentBy || '', f.consentBy ? t : 0,
      JSON.stringify(f.proposal || {}), JSON.stringify(dups), JSON.stringify(f.flags || []),
      f.batchId || '', f.createdBy || '', t]
  );
  await c.query(
    `INSERT INTO image_versions (image_id, version, kind, parent, blob_hash, orientation, tool, settings, review, created_by, created_at)
     VALUES ($1,1,'original',0,$2,$3,'','{}',$4,$5,$6)`,
    [id, f.hash, f.orientation || 0, f.versionReview || 'needs_review', f.createdBy || '', t]
  );
  return { id, duplicate_of: dups };
}

async function addVersion(c, img, buf, info, { tool = '', settings = {}, review = 'needs_review', by = '' }) {
  const hash = await putBlob(c, buf, info);
  const last = await q1(c, 'SELECT MAX(version) AS v FROM image_versions WHERE image_id=$1', [img.id]);
  const version = Number(last?.v || 0) + 1;
  await c.query(
    `INSERT INTO image_versions (image_id, version, kind, parent, blob_hash, orientation, tool, settings, review, created_by, created_at)
     VALUES ($1,$2,'processed',$3,$4,$5,$6,$7,$8,$9,$10)`,
    [img.id, version, img.current_version, hash, info.orientation || 0, str(tool, 64),
      JSON.stringify(settings || {}).slice(0, 2000), review, by, now()]
  );
  await c.query(`UPDATE images SET rev=nextval('image_bank_rev'), updated_at=$2 WHERE id=$1`, [img.id, now()]);
  return { image_id: img.id, version, hash };
}

/**
 * نسخهٔ کوچک: نوع/ابعاد سنجیده، کوچک‌تر از اصل، همان نسبتِ ابعاد (۲٪)، بی
 * چرخشِ EXIF، فراداده برداشته. یک‌بارنویس: تغییر یعنی نسخهٔ تازه.
 */
async function putDerivative(c, img, version, size, buf) {
  const spec = DERIV[size];
  if (!spec) throw badRequest('اندازه فقط thumb، display یا zoom', 'bad_size');
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('عکس خالی است', 'image_empty');
  if (buf.length > spec.bytes) throw tooLarge(spec.bytes);
  const ver = await q1(c,
    `SELECT v.version, v.orientation, b.width, b.height FROM image_versions v
       JOIN image_blobs b ON b.hash=v.blob_hash WHERE v.image_id=$1 AND v.version=$2`, [img.id, version]);
  if (!ver) throw notFound('این نسخه نیست');
  const info = bytes.inspect(buf);
  if (info.orientation > 1) {
    throw badRequest('نسخهٔ کوچک باید پیش از فرستادن چرخانده شده باشد (EXIF orientation)', 'orientation_not_applied');
  }
  const swap = Number(ver.orientation) >= 5 && Number(ver.orientation) <= 8;
  const W = swap ? Number(ver.height) : Number(ver.width);
  const H = swap ? Number(ver.width) : Number(ver.height);
  if (Math.max(info.width, info.height) > spec.side) {
    throw badRequest(`بزرگ‌ترین ضلعِ ${size} حداکثر ${spec.side} پیکسل`, 'derivative_too_big');
  }
  if (info.width > W || info.height > H) throw badRequest('نسخهٔ کوچک از اصل بزرگ‌تر است', 'derivative_upscaled');
  if (Math.abs(info.width / info.height - W / H) / (W / H) > 0.02) {
    throw badRequest('نسبتِ ابعادِ نسخهٔ کوچک با اصل نمی‌خواند', 'aspect_mismatch');
  }
  const clean = bytes.stripMetadata(buf);
  const hash = await putBlob(c, clean, info);
  const had = await q1(c, 'SELECT blob_hash FROM image_derivatives WHERE image_id=$1 AND version=$2 AND size=$3',
    [img.id, version, size]);
  if (had) {
    if (had.blob_hash === hash) return { size, version, hash, unchanged: true };
    throw conflict('این اندازه برای این نسخه ثبت شده است — تغییر یعنی نسخهٔ تازه', 'derivative_exists');
  }
  await c.query('INSERT INTO image_derivatives (image_id, version, size, blob_hash, created_at) VALUES ($1,$2,$3,$4,$5)',
    [img.id, version, size, hash, now()]);
  await c.query(`UPDATE images SET rev=nextval('image_bank_rev'), updated_at=$2 WHERE id=$1`, [img.id, now()]);
  return { size, version, hash, width: info.width, height: info.height, stripped: clean.length !== buf.length };
}

async function readFile(imageId, version, size) {
  const r = size === 'original'
    ? await one(`SELECT b.data, b.mime, b.hash FROM image_versions v JOIN image_blobs b ON b.hash=v.blob_hash
                  WHERE v.image_id=$1 AND v.version=$2`, [imageId, version])
    : await one(`SELECT b.data, b.mime, b.hash FROM image_derivatives d JOIN image_blobs b ON b.hash=d.blob_hash
                  WHERE d.image_id=$1 AND d.version=$2 AND d.size=$3`, [imageId, version, size]);
  return r ? { data: Buffer.from(r.data), mime: r.mime, hash: r.hash } : null;
}

async function imageRow(id) {
  if (!/^img_[0-9A-Z]{26}$/.test(String(id || ''))) return null;
  return one('SELECT * FROM images WHERE id=$1', [id]);
}

async function derivativesOf(ids) {
  if (!ids.length) return new Map();
  const rows = await many(
    `SELECT image_id, version, size FROM image_derivatives WHERE image_id IN (${placeholders(ids.length)})`, ids);
  const m = new Map();
  for (const r of rows) {
    const k = r.image_id + '@' + r.version;
    if (!m.has(k)) m.set(k, new Set());
    m.get(k).add(r.size);
  }
  return m;
}

function urlsOf(base, id, version, have) {
  const out = {};
  for (const s of Object.keys(DERIV)) out[s] = have && have.has(s) ? fileUrl(base, id, version, s) : null;
  return out;
}

// ════════════════════════════════════════════════════════════════════
//  هویتِ کالای بانک
// ════════════════════════════════════════════════════════════════════

function productInput(b) {
  const name = str(b.name, 200);
  if (!name) throw badRequest('نامِ کالا لازم است', 'name_required');
  const rawG = str(b.gtin != null ? b.gtin : b.barcode, 32);
  const g = ID.gtin(rawG);
  if (rawG && !g.valid) throw badRequest('بارکد معتبر نیست (رقمِ کنترل نمی‌خواند)', 'bad_gtin');
  const alt = (Array.isArray(b.alt_names) ? b.alt_names : String(b.alt_names || '').split('|'))
    .map((x) => str(x, 200)).filter(Boolean).slice(0, 20);
  const p = {
    gtin: g.valid ? g.digits : '', gtin14: g.gtin14, sku: str(b.sku, 64), name, alt_names: alt,
    brand: str(b.brand, 100), category: str(b.category, 100), variant: str(b.variant, 100),
    size_value: str(b.size_value != null ? b.size_value : b.size, 20),
    size_unit: str(b.size_unit != null ? b.size_unit : b.unit, 20),
    pack_count: intIn(b.pack_count, 0, 100000, 0), packaging: str(b.packaging, 100),
  };
  return { ...p, ...ID.keysOf(p) };
}

const PRODUCT_COLS = ['gtin', 'gtin14', 'sku', 'name', 'alt_names', 'brand', 'category', 'variant', 'size_value',
  'size_unit', 'pack_count', 'packaging', 'name_key', 'brand_key', 'category_key', 'variant_key', 'packaging_key',
  'size_base', 'size_dim', 'search_key'];

async function insertProduct(c, p, by) {
  if (p.gtin14 && await q1(c, 'SELECT id FROM bank_products WHERE gtin14=$1', [p.gtin14])) {
    throw conflict('کالایی با همین بارکد در بانک هست', 'gtin_exists');
  }
  const id = 'bp_' + ulid();
  const t = now();
  const vals = PRODUCT_COLS.map((k) => (k === 'alt_names' ? JSON.stringify(p.alt_names) : p[k]));
  await c.query(
    `INSERT INTO bank_products (id, ${PRODUCT_COLS.join(',')}, created_by, created_at, updated_at)
     VALUES ($1, ${placeholders(PRODUCT_COLS.length, 2)}, $${PRODUCT_COLS.length + 2}, $${PRODUCT_COLS.length + 3}, $${PRODUCT_COLS.length + 3})`,
    [id, ...vals, by || '', t]
  );
  return id;
}

async function updateProduct(c, id, p) {
  if (p.gtin14 && await q1(c, 'SELECT id FROM bank_products WHERE gtin14=$1 AND id<>$2', [p.gtin14, id])) {
    throw conflict('کالایی با همین بارکد در بانک هست', 'gtin_exists');
  }
  const sets = PRODUCT_COLS.map((k, i) => `${k}=$${i + 2}`).join(',');
  const vals = PRODUCT_COLS.map((k) => (k === 'alt_names' ? JSON.stringify(p.alt_names) : p[k]));
  const r = await q1(c, `UPDATE bank_products SET ${sets}, updated_at=$${PRODUCT_COLS.length + 2} WHERE id=$1 RETURNING id`,
    [id, ...vals, now()]);
  if (!r) throw notFound('کالای بانک پیدا نشد');
  await c.query(`UPDATE images SET rev=nextval('image_bank_rev') WHERE bank_product_id=$1`, [id]);
}

function productOut(p) {
  return {
    id: p.id, gtin: p.gtin, sku: p.sku, name: p.name, alt_names: json(p.alt_names, []), brand: p.brand,
    category: p.category, variant: p.variant, size_value: p.size_value, size_unit: p.size_unit,
    pack_count: Number(p.pack_count), packaging: p.packaging, status: p.status,
  };
}

// ════════════════════════════════════════════════════════════════════
//  جست‌وجو (۳۷.۵، ۳۷.۹)
// ════════════════════════════════════════════════════════════════════

const likeEsc = (s) => s.replace(/[\\%_]/g, (m) => '\\' + m);
const KIND = ['barcode_exact', 'sku_exact', 'name_exact', 'name_words', 'spelling_tolerant'];
const variantCols = (p) => [p.brand_key, p.variant_key, p.size_dim + ':' + Number(p.size_base), Number(p.pack_count), p.packaging_key].join('/');
const DISTINGUISH = [['brand', 'brand_key'], ['variant', 'variant_key'], ['size', 'size_base'], ['pack_count', 'pack_count'], ['packaging', 'packaging_key']];

function cursorOf(o) { return Buffer.from(JSON.stringify({ o })).toString('base64url'); }
function offsetOf(c) {
  if (!c) return 0;
  const v = json(Buffer.from(String(c), 'base64url').toString(), null);
  return v && Number.isInteger(v.o) && v.o >= 0 && v.o <= CANDIDATE_MAX ? v.o : 0;
}

/**
 * جست‌وجوی بانک. ترتیب: بارکدِ دقیق ← SKUِ دقیق ← نامِ کامل ← همهٔ کلمه‌ها
 * ← تحملِ غلطِ املایی؛ «محبوبیت» فقط درونِ هر رده (۳۷.۵). هیچ امتیازِ عددی
 * بیرون نمی‌رود — فقط دلیل؛ و `certain` فقط برای بارکد/SKUِ دقیق.
 * opts.base: پیشوندِ نشانیِ فایل؛ opts.all: مدیر (بی شرطِ «تأییدشده»).
 */
async function search(params, opts = {}) {
  const qRaw = str(params.q, 100);
  const key = T.searchKey(qRaw);
  const tokens = key ? [...new Set(key.split(' ').filter(Boolean))].slice(0, 6) : [];
  const digits = T.asciiDigits(qRaw).replace(/[\s-]/g, '');
  const bc = str(params.barcode, 32) || (/^\d{8,14}$/.test(digits) ? digits : '');
  const g = ID.gtin(bc);
  const sku = str(params.sku, 64) || qRaw;
  const nameKey = T.compareKey(qRaw);
  const limit = clamp(params.limit, 20);
  const offset = offsetOf(params.cursor);

  //  فیلترها با «?» نوشته می‌شوند و هر پرس‌وجو شماره‌های خودش را می‌گیرد
  const filters = [];
  if (params.brand) filters.push(['p.brand_key = ?', T.compareKey(params.brand)]);
  if (params.category) filters.push(['p.category_key = ?', T.compareKey(params.category)]);
  if (params.packaging) filters.push(['p.packaging_key = ?', T.compareKey(params.packaging)]);
  const hasImage = params.has_image === true || params.has_image === '1' || params.has_image === 'true';
  if (!tokens.length && !bc && !filters.length && !hasImage) {
    return { items: [], next_cursor: null, bank_rev: await bankRev(), as_of: now(), live: true, barcode_valid: null };
  }
  const render = (args) => {
    const w = ["p.status='active'"];
    for (const [sql, v] of filters) { args.push(v); w.push(sql.replace('?', '$' + args.length)); }
    if (hasImage) {
      w.push(`EXISTS (SELECT 1 FROM images i WHERE i.bank_product_id=p.id AND i.space='central'
                AND i.status='APPROVED' AND i.search_enabled)`);
    }
    return w.join(' AND ');
  };
  const like = (args, t) => { args.push('%' + likeEsc(t) + '%'); return `p.search_key LIKE $${args.length} ESCAPE '\\'`; };

  const cols = 'p.id, p.name, p.alt_names, p.brand, p.name_key, p.brand_key, p.variant_key, p.size_base, p.size_dim, p.pack_count, p.packaging_key';
  const pop = `COALESCE((SELECT SUM(i.use_count) FROM images i WHERE i.bank_product_id=p.id AND i.space='central'),0)`;
  const args = [g.gtin14, sku, nameKey];
  const where = render(args);
  const match = [`($1::text <> '' AND p.gtin14 = $1::text)`, `($2::text <> '' AND p.sku = $2::text)`];
  if (tokens.length) match.push('(' + tokens.map((t) => like(args, t)).join(' AND ') + ')');
  else if (!bc) match.push('true');                                  // مرور با فیلتر
  const rows = await many(
    `SELECT ${cols},
        CASE WHEN $1::text <> '' AND p.gtin14 = $1::text THEN 0 WHEN $2::text <> '' AND p.sku = $2::text THEN 1
             WHEN $3::text <> '' AND p.name_key = $3::text THEN 2 ELSE 3 END AS tier, ${pop} AS pop
       FROM bank_products p WHERE ${where} AND (${match.join(' OR ')})
      ORDER BY tier, pop DESC, p.name_key, p.id LIMIT ${CANDIDATE_MAX}`, args);
  let cands = rows.map((r) => ({ ...r, tier: Number(r.tier) }));

  //  تحملِ غلطِ املایی — محدود به FUZZY_SCAN ردیف و بودجهٔ هستهٔ مشترکِ متن
  const longest = tokens.slice().sort((a, b) => b.length - a.length)[0] || '';
  if (cands.length < limit + offset && longest.length >= 4) {
    const seen = new Set(cands.map((r) => r.id));
    const fargs = [];
    const fwhere = render(fargs);
    const more = await many(
      `SELECT ${cols}, 4 AS tier, ${pop} AS pop FROM bank_products p
        WHERE ${fwhere} AND ${like(fargs, longest.slice(0, 2))}
        ORDER BY pop DESC, p.name_key, p.id LIMIT ${FUZZY_SCAN}`, fargs);
    for (const r of more) {
      if (seen.has(r.id)) continue;
      const text = [r.name, ...json(r.alt_names, []), r.brand].join(' ');
      if (T.fuzzyMatches(text, qRaw)) cands.push({ ...r, tier: 4 });
    }
  }
  cands = cands.slice(0, CANDIDATE_MAX);

  //  نامِ یکسان ولی نوعِ متفاوت ⇒ جدا و «مبهم» (IMG-BANK-05)
  const groups = new Map();
  for (const r of cands) {
    if (!groups.has(r.name_key)) groups.set(r.name_key, []);
    groups.get(r.name_key).push(r);
  }

  const page = cands.slice(offset, offset + limit);
  const ids = page.map((r) => r.id);
  const products = ids.length
    ? await many(`SELECT * FROM bank_products WHERE id IN (${placeholders(ids.length)})`, ids) : [];
  const byId = new Map(products.map((p) => [p.id, p]));
  const imgs = ids.length ? await many(
    `SELECT id, bank_product_id, role, current_version, status, search_enabled, space, license_status FROM images
      WHERE bank_product_id IN (${placeholders(ids.length)})
        AND ${opts.all ? "space IN ('central','pending')" : "space='central' AND status='APPROVED' AND search_enabled"}
      ORDER BY (role='main') DESC, use_count DESC, id`, ids) : [];
  const derivs = await derivativesOf(imgs.map((i) => i.id));

  const items = page.map((r) => {
    const p = byId.get(r.id);
    const group = groups.get(r.name_key) || [];
    const vk = variantCols(r);
    const diff = new Set();
    for (const o of group) {
      if (o.id === r.id || variantCols(o) === vk) continue;
      for (const [label, col] of DISTINGUISH) if (String(o[col]) !== String(r[col])) diff.add(label);
    }
    const reasons = [KIND[r.tier]];
    if (r.tier >= 2 && tokens.some((t) => r.brand_key && r.brand_key.includes(t))) reasons.push('brand_match');
    return {
      product: productOut(p),
      match: { kind: KIND[r.tier], certain: r.tier <= 1, reasons },
      ambiguous: diff.size > 0,
      distinguish: [...diff],
      images: imgs.filter((i) => i.bank_product_id === r.id).slice(0, 6).map((i) => ({
        id: i.id, role: i.role, version: Number(i.current_version),
        ...(opts.all ? { space: i.space, status: i.status, license_status: i.license_status } : {}),
        urls: urlsOf(opts.base || '', i.id, Number(i.current_version), derivs.get(i.id + '@' + i.current_version)),
      })),
    };
  });
  const next = offset + limit < cands.length ? cursorOf(offset + limit) : null;
  return {
    items, next_cursor: next, bank_rev: await bankRev(), as_of: now(), live: true,
    barcode_valid: bc ? g.valid : null,
  };
}

/** نسخهٔ بانک: هر تغییرِ تأیید/بایگانی/نسخه آن را بالا می‌برد ⇒ cacheِ برنامه باطل. */
async function bankRev() {
  const r = await one('SELECT last_value, is_called FROM image_bank_rev');
  return r && r.is_called ? Number(r.last_value) : 0;
}

// ════════════════════════════════════════════════════════════════════
//  دکان: عکسِ خود، دسترسی، انتخاب و نمایش (۳۷.۶، ۳۷.۷)
// ════════════════════════════════════════════════════════════════════

async function shopUpload(shopId, userId, buf, { productId = '', role = 'main' } = {}) {
  const { info, flags } = checkOriginal(buf, SHOP_MAX_BYTES);
  const pid = productId ? photos.checkId(productId) : '';
  return tx(async (c) => {
    const n = await q1(c, 'SELECT COUNT(*)::int AS n FROM images WHERE owner_shop_id=$1', [shopId]);
    if (Number(n?.n || 0) >= SHOP_MAX_IMAGES) throw badRequest('سقفِ عکس‌های این دکان پر است', 'quota');
    const hash = await putBlob(c, buf, info);
    const out = await insertImage(c, {
      space: 'shop', ownerShopId: shopId, status: 'APPROVED', role, shopProductId: pid, hash,
      orientation: info.orientation, flags, createdBy: userId, versionReview: 'approved',
      license: { source: 'shop_own', holder: shopId, type: 'shop_own_photo', status: 'unknown' },
    });
    return { ...out, version: 1, width: info.width, height: info.height, mime: info.mime, quality_flags: flags };
  });
}

/**
 * آیا این دکان این فایل را می‌بیند؟
 *   shop     فقط صاحبش (همهٔ نسخه‌ها و اصل)
 *   central  تأییدشده و فعال: نسخه‌های تأییدشده، بی اصل؛ بایگانی/غیرفعال: فقط
 *            نسخه‌ای که همین دکان پیش‌تر سنجاق کرده (سابقه نمی‌شکند)
 *   pending  هرگز
 */
async function shopCanRead(shopId, img, version, size) {
  if (img.space === 'shop') return img.owner_shop_id === shopId;
  if (img.space !== 'central' || size === 'original') return false;
  const pinned = await one(
    `SELECT 1 FROM shop_product_images WHERE shop_id=$1
        AND ((image_id=$2 AND version=$3) OR (central_image_id=$2 AND central_version=$3)) LIMIT 1`,
    [shopId, img.id, version]);
  if (pinned) return true;
  if (img.status !== 'APPROVED' || !img.search_enabled) return false;
  const v = await one('SELECT review FROM image_versions WHERE image_id=$1 AND version=$2', [img.id, version]);
  return !!v && v.review === 'approved';
}

async function ownImage(c, shopId, id) {
  const img = await q1(c, 'SELECT * FROM images WHERE id=$1 FOR UPDATE', [id]);
  if (!img || img.space !== 'shop' || img.owner_shop_id !== shopId) return null;
  return img;
}

async function shopVersion(shopId, userId, id, buf) {
  const { info } = checkOriginal(buf, SHOP_MAX_BYTES);
  return tx(async (c) => {
    const img = await ownImage(c, shopId, id);
    if (!img) throw notFound('عکس پیدا نشد');
    return addVersion(c, img, buf, info, { tool: 'client', review: 'approved', by: userId });
  });
}

async function shopDerivative(shopId, id, version, size, buf) {
  return tx(async (c) => {
    const img = await ownImage(c, shopId, id);
    if (!img) throw notFound('عکس پیدا نشد');
    return putDerivative(c, img, version, size, buf);
  });
}

async function shopList(shopId, { productId = '', cursor = '', limit = 50, base = '' } = {}) {
  const args = [shopId];
  let sql = 'SELECT * FROM images WHERE owner_shop_id=$1';
  if (productId) { args.push(photos.checkId(productId)); sql += ` AND shop_product_id=$${args.length}`; }
  if (cursor) { args.push(String(cursor).slice(0, 40)); sql += ` AND id < $${args.length}`; }
  const lim = clamp(limit, PAGE_MAX);
  const rows = await many(`${sql} ORDER BY id DESC LIMIT ${lim}`, args);
  const derivs = await derivativesOf(rows.map((r) => r.id));
  const props = rows.length ? await many(
    `SELECT origin_image_id, id, space, status FROM images WHERE origin_shop_id=$1
       AND origin_image_id IN (${placeholders(rows.length, 2)})`, [shopId, ...rows.map((r) => r.id)]) : [];
  return {
    items: rows.map((r) => ({
      id: r.id, product_id: r.shop_product_id, role: r.role, version: Number(r.current_version),
      quality_flags: json(r.quality_flags, []), duplicate_of: json(r.duplicate_of, []),
      urls: { original: fileUrl(base, r.id, Number(r.current_version), 'original'),
        ...urlsOf(base, r.id, Number(r.current_version), derivs.get(r.id + '@' + r.current_version)) },
      proposals: props.filter((p) => p.origin_image_id === r.id)
        .map((p) => ({ id: p.id, status: p.status })),
    })),
    next_cursor: rows.length === lim ? rows[rows.length - 1].id : null,
  };
}

/**
 * انتخابِ منبعِ عکسِ یک کالای دکان (۳۷.۶، ۳۷.۷).
 *   central  عکسِ مرکزیِ تأییدشده و فعال، سنجاق به نسخه. تطبیقِ غیرِ قطعی
 *            (بی بارکدِ یکسان، یا مشخصهٔ ناهمسان) ⇒ ۴۰۹ مگر `confirm: true`؛
 *            بارکدِ متفاوت ⇒ ۴۰۹ حتی با confirm. بی image_id ⇒ بازگشت به
 *            آخرین عکسِ مرکزیِ همین کالا (IMG-BANK-16).
 *   shop     عکسِ خودِ دکان — آخرین عکسِ مرکزی برای بازگشت می‌ماند.
 *   none     بی عکس (placeholder).
 */
async function select(shopId, userId, body) {
  const productId = photos.checkId(body.product_id);
  const source = oneOf(body.source, ['central', 'shop', 'none'], '');
  if (!source) throw badRequest('منبع: central، shop یا none', 'bad_source');
  return tx(async (c) => {
    const prev = await q1(c, 'SELECT * FROM shop_product_images WHERE shop_id=$1 AND product_id=$2 FOR UPDATE',
      [shopId, productId]);
    const row = {
      source, image_id: '', version: 0, match_status: 'unconfirmed', confirmed_by: '',
      central_image_id: prev?.central_image_id || '', central_version: Number(prev?.central_version || 0),
      central_match: prev?.central_match || '', central_confirmed_by: prev?.central_confirmed_by || '',
      bank_product_id: prev?.bank_product_id || '',
    };
    let classification = null;
    if (source === 'central') {
      const imageId = str(body.image_id, 40) || row.central_image_id;
      if (!imageId) throw badRequest('عکسِ مرکزی انتخاب نشده', 'image_required');
      const img = await q1(c, 'SELECT * FROM images WHERE id=$1', [imageId]);
      if (!img || img.space !== 'central') throw notFound('عکس پیدا نشد');
      if (img.status !== 'APPROVED' || !img.search_enabled) {
        throw conflict('این عکس دیگر در بانک قابلِ انتخاب نیست', 'image_not_selectable');
      }
      const reverting = !body.image_id && imageId === row.central_image_id;
      const version = body.version != null ? intIn(body.version, 1, 100000, 0)
        : (reverting ? row.central_version : Number(img.current_version));
      const v = await q1(c, 'SELECT review FROM image_versions WHERE image_id=$1 AND version=$2', [imageId, version]);
      if (!v || v.review !== 'approved' || version > Number(img.current_version)) {
        throw conflict('این نسخهٔ عکس تأیید نشده است', 'version_not_approved');
      }
      const bp = await q1(c, 'SELECT * FROM bank_products WHERE id=$1', [img.bank_product_id]);
      if (!bp) throw conflict('عکس به کالای بانک وصل نیست', 'product_missing');
      const expect = body.expect && typeof body.expect === 'object' ? body.expect : null;
      //  بازگشت به همان عکسِ مرکزی: تطبیقِ همان بار (با تأییدش) معتبر است
      classification = reverting && !expect && row.central_match
        ? { status: row.central_match, reasons: ['previous_selection'], diffs: [], by: row.central_confirmed_by }
        : ID.classify({ ...bp, alt_names: json(bp.alt_names, []) }, expect || {});
      if (classification.reasons.includes('barcode_different')) {
        throw new ApiError(409, 'barcode_mismatch', 'بارکدِ کالای شما با کالای این عکس فرق دارد');
      }
      if (classification.status !== 'exact' && body.confirm !== true) {
        const e = new ApiError(409, 'needs_confirmation', 'تطبیق قطعی نیست — مشخصات را ببینید و تأیید کنید');
        e.details = classification;
        throw e;
      }
      const confirmedBy = classification.status === 'exact' ? '' : (classification.by || userId);
      Object.assign(row, {
        image_id: imageId, version, match_status: classification.status, confirmed_by: confirmedBy,
        central_image_id: imageId, central_version: version, central_match: classification.status,
        central_confirmed_by: confirmedBy, bank_product_id: bp.id,
      });
      if (!reverting && prev?.image_id !== imageId) {
        await c.query('UPDATE images SET use_count=use_count+1 WHERE id=$1', [imageId]);
      }
    } else if (source === 'shop') {
      const img = await ownImage(c, shopId, str(body.image_id, 40));
      if (!img) throw notFound('عکس پیدا نشد');
      const version = body.version != null ? intIn(body.version, 1, 100000, 0) : Number(img.current_version);
      const v = await q1(c, 'SELECT 1 FROM image_versions WHERE image_id=$1 AND version=$2', [img.id, version]);
      if (!v) throw notFound('این نسخه نیست');
      Object.assign(row, { image_id: img.id, version, match_status: 'exact' });
    }
    await c.query(
      `INSERT INTO shop_product_images (shop_id, product_id, source, image_id, version, match_status, confirmed_by,
          central_image_id, central_version, central_match, central_confirmed_by, bank_product_id, updated_by, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14)
       ON CONFLICT (shop_id, product_id) DO UPDATE SET source=EXCLUDED.source, image_id=EXCLUDED.image_id,
          version=EXCLUDED.version, match_status=EXCLUDED.match_status, confirmed_by=EXCLUDED.confirmed_by,
          central_image_id=EXCLUDED.central_image_id, central_version=EXCLUDED.central_version,
          central_match=EXCLUDED.central_match, central_confirmed_by=EXCLUDED.central_confirmed_by,
          bank_product_id=EXCLUDED.bank_product_id, updated_by=EXCLUDED.updated_by, updated_at=EXCLUDED.updated_at`,
      [shopId, productId, row.source, row.image_id, row.version, row.match_status, row.confirmed_by,
        row.central_image_id, row.central_version, row.central_match, row.central_confirmed_by, row.bank_product_id,
        userId, now()]);
    return { product_id: productId, ...row, classification };
  });
}

/** کدام عکس برای این کالا نمایش داده می‌شود — منبع همیشه صریح است. */
async function resolve(shopId, productId, base = '') {
  const pid = photos.checkId(productId);
  const row = await one('SELECT * FROM shop_product_images WHERE shop_id=$1 AND product_id=$2', [shopId, pid]);
  if (!row) {
    //  رفتارِ امروز: عکسِ همگام‌شدهٔ ۰۴۵ — هیچ عکسِ مرکزی خودکار نمی‌نشیند
    const legacy = await one(
      `SELECT hash FROM product_photos WHERE app='shop' AND account_id=$1 AND product_id=$2 AND data IS NOT NULL`, [shopId, pid]);
    return legacy
      ? { product_id: pid, source: 'legacy_photo', hash: legacy.hash, url: `/api/sync/v1/photos/${pid}`, placeholder: false }
      : { product_id: pid, source: 'none', placeholder: true };
  }
  const central = row.central_image_id
    ? { image_id: row.central_image_id, version: Number(row.central_version) } : null;
  if (row.source === 'none' || !row.image_id) {
    return { product_id: pid, source: 'none', placeholder: true, central };
  }
  const img = await one('SELECT id, status, search_enabled, current_version FROM images WHERE id=$1', [row.image_id]);
  const version = Number(row.version);
  const derivs = await derivativesOf([row.image_id]);
  const out = {
    product_id: pid, source: row.source, image_id: row.image_id, version, match_status: row.match_status,
    confirmed_by_user: !!row.confirmed_by, placeholder: false, central,
    urls: urlsOf(base, row.image_id, version, derivs.get(row.image_id + '@' + version)),
  };
  if (row.source === 'central' && img) {
    out.archived = img.status === 'ARCHIVED' || !img.search_enabled;
    out.update_available = !out.archived && Number(img.current_version) > version ? Number(img.current_version) : 0;
  }
  return out;
}

/**
 * پیشنهادِ عکسِ خودِ دکان به بانکِ مرکزی (IMG-BANK-08) — فقط با اجازهٔ صریح
 * (`consent: true`) و فقط صاحبِ دکان. یک کپی در pending می‌نشیند؛ عکسِ دکان
 * خصوصی می‌ماند. مجوز «نامعلوم» است تا مدیر بررسی کند.
 */
async function propose(shopId, userId, imageId, body) {
  if (body.consent !== true) throw badRequest('اجازهٔ صریحِ اشتراکِ عکس با بانکِ مرکزی لازم است', 'consent_required');
  return tx(async (c) => {
    const img = await ownImage(c, shopId, imageId);
    if (!img) throw notFound('عکس پیدا نشد');
    const open = await q1(c,
      `SELECT id, status FROM images WHERE origin_shop_id=$1 AND origin_image_id=$2 AND status IN ('DRAFT','NEEDS_REVIEW','APPROVED')`,
      [shopId, img.id]);
    if (open) return { id: open.id, status: open.status, existing: true };
    const v = await q1(c, 'SELECT blob_hash, orientation FROM image_versions WHERE image_id=$1 AND version=$2',
      [img.id, Number(img.current_version)]);
    const p = body.product && typeof body.product === 'object' ? body.product : {};
    const proposal = {};
    for (const k of ['name', 'barcode', 'brand', 'category', 'variant', 'size_value', 'size_unit', 'pack_count', 'packaging', 'note']) {
      if (p[k] != null && p[k] !== '') proposal[k] = str(p[k], 200);
    }
    const bankProductId = str(body.bank_product_id, 40);
    if (bankProductId && !await q1(c, 'SELECT 1 FROM bank_products WHERE id=$1', [bankProductId])) {
      throw notFound('کالای بانک پیدا نشد');
    }
    const out = await insertImage(c, {
      space: 'pending', status: 'NEEDS_REVIEW', role: img.role, bankProductId,
      match: 'unconfirmed', reasons: ['shop_proposal'], hash: v.blob_hash, orientation: v.orientation,
      flags: json(img.quality_flags, []), createdBy: userId, proposal,
      originShopId: shopId, originImageId: img.id, consentBy: userId,
      license: { source: 'shop:' + shopId, holder: shopId, type: 'shop_own_photo', status: 'unknown' },
    });
    //  نسخه‌های کوچکِ همان نسخه هم (همان بلاب‌ها، بی کپیِ بایت)
    await c.query(
      `INSERT INTO image_derivatives (image_id, version, size, blob_hash, created_at)
       SELECT $1, 1, size, blob_hash, $4 FROM image_derivatives WHERE image_id=$2 AND version=$3`,
      [out.id, img.id, Number(img.current_version), now()]);
    return { id: out.id, status: 'NEEDS_REVIEW', duplicate_of: out.duplicate_of, existing: false };
  });
}

async function report(shopId, userId, img, body) {
  const kind = oneOf(body.kind, REPORT_KINDS, '');
  if (!kind) throw badRequest('نوعِ گزارش درست نیست', 'bad_kind');
  const id = 'irp_' + ulid();
  await one(`INSERT INTO image_reports (id, image_id, shop_id, kind, note, created_by, created_at)
             VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id`, [id, img.id, shopId, kind, str(body.note, 500), userId, now()]);
  return { id, kind };
}

// ════════════════════════════════════════════════════════════════════
//  مدیر: بارگذاری، ویرایشِ pending، تأیید/رد/بایگانی (۳۷.۳)
// ════════════════════════════════════════════════════════════════════

function licenseInput(b, def = LIC0) {
  return {
    source: b.license_source != null ? str(b.license_source) : def.source,
    holder: b.license_holder != null ? str(b.license_holder) : def.holder,
    type: b.license_type != null ? str(b.license_type, 64) : def.type,
    status: oneOf(b.license_status, LICENSES, def.status || 'unknown'),
    commercial: b.commercial_use != null ? (b.commercial_use === true || b.commercial_use === 'true' || b.commercial_use === '1') : def.commercial,
  };
}

async function adminUpload(adminId, buf, q) {
  const { info, flags } = checkOriginal(buf, BANK_MAX_BYTES);
  return tx(async (c) => {
    const bankProductId = str(q.bank_product_id, 40);
    if (bankProductId && !await q1(c, 'SELECT 1 FROM bank_products WHERE id=$1', [bankProductId])) {
      throw notFound('کالای بانک پیدا نشد');
    }
    const hash = await putBlob(c, buf, info);
    const out = await insertImage(c, {
      space: 'pending', status: 'NEEDS_REVIEW', role: q.role, bankProductId, hash, orientation: info.orientation,
      //  پیوندِ صریحِ مدیر = تأییدِ انسانی
      match: bankProductId ? 'exact' : 'unconfirmed', reasons: bankProductId ? ['admin_linked'] : [],
      flags, createdBy: adminId, license: licenseInput(q),
    });
    return { ...out, status: 'NEEDS_REVIEW', width: info.width, height: info.height, quality_flags: flags };
  });
}

async function adminImage(c, id, lock = true) {
  const img = await q1(c, `SELECT * FROM images WHERE id=$1${lock ? ' FOR UPDATE' : ''}`, [id]);
  //  ⛔ فضای دکان برای مدیر وجود ندارد (خصوصی)
  if (!img || img.space === 'shop') return null;
  return img;
}

async function adminPatch(adminId, id, b) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img) throw notFound('عکس پیدا نشد');
    if (img.space !== 'pending' || !['DRAFT', 'NEEDS_REVIEW'].includes(img.status)) {
      throw conflict('فقط عکسِ در انتظارِ بررسی ویرایش می‌شود', 'not_pending');
    }
    const lic = licenseInput(b, {
      source: img.license_source, holder: img.license_holder, type: img.license_type,
      status: img.license_status, commercial: img.commercial_use,
    });
    let bankProductId = img.bank_product_id;
    let match = img.match_status;
    let reasons = json(img.match_reasons, []);
    if (b.bank_product_id != null) {
      bankProductId = str(b.bank_product_id, 40);
      if (bankProductId && !await q1(c, 'SELECT 1 FROM bank_products WHERE id=$1', [bankProductId])) {
        throw notFound('کالای بانک پیدا نشد');
      }
      match = bankProductId ? 'exact' : 'unconfirmed';
      reasons = bankProductId ? ['admin_linked'] : [];
    }
    await c.query(
      `UPDATE images SET bank_product_id=$2, match_status=$3, match_reasons=$4, role=$5, license_source=$6,
          license_holder=$7, license_type=$8, license_status=$9, commercial_use=$10,
          license_checked_at=CASE WHEN $9='licensed' THEN $11 ELSE license_checked_at END,
          rev=nextval('image_bank_rev'), updated_at=$11 WHERE id=$1`,
      [id, bankProductId, match, JSON.stringify(reasons), oneOf(b.role, ROLES, img.role), lic.source, lic.holder,
        lic.type, lic.status, lic.commercial, now()]);
    return { id, bank_product_id: bankProductId, match_status: match, license_status: lic.status, commercial_use: lic.commercial };
  });
}

async function adminVersion(adminId, id, buf, b) {
  const { info } = checkOriginal(buf, BANK_MAX_BYTES);
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img) throw notFound('عکس پیدا نشد');
    if (img.status === 'ARCHIVED' || img.status === 'REJECTED') throw conflict('عکس بایگانی/رد شده است', 'not_editable');
    return addVersion(c, img, buf, info, { tool: str(b.tool, 64) || 'manual', settings: {}, review: 'needs_review', by: adminId });
  });
}

async function adminDerivative(id, version, size, buf) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img) throw notFound('عکس پیدا نشد');
    return putDerivative(c, img, version, size, buf);
  });
}

/**
 * تأیید (فقط مدیرِ ارشد، با دلیل — لایهٔ مسیر). دو حالت:
 *   pending  ⇒ central: مجوزِ `licensed` + استفادهٔ تجاری، کالای بانکِ فعال و
 *               تطبیقِ قطعی لازم است (IMG-10، IMG-BANK-02)
 *   central  ⇒ نسخهٔ تازه (`version`) جاری می‌شود؛ دکان‌های سنجاق‌شده عوض
 *               نمی‌شوند و فقط «به‌روزرسانیِ موجود» می‌بینند (IMG-BANK-09)
 */
async function approve(adminId, id, { reason, version }) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img) throw notFound('عکس پیدا نشد');
    const t = now();
    if (img.space === 'pending') {
      if (!['DRAFT', 'NEEDS_REVIEW'].includes(img.status)) throw conflict('این عکس در انتظارِ بررسی نیست', 'not_pending');
      if (img.license_status !== 'licensed' || !img.commercial_use) {
        throw conflict('مجوزِ استفادهٔ تجاری ثبت نشده — عکسِ با مجوزِ نامعلوم منتشر نمی‌شود', 'license_required');
      }
      const bp = img.bank_product_id && await q1(c, `SELECT 1 FROM bank_products WHERE id=$1 AND status='active'`, [img.bank_product_id]);
      if (!bp) throw conflict('عکس به کالای بانک وصل نیست', 'product_required');
      if (img.match_status !== 'exact') throw conflict('تطبیقِ عکس با کالا قطعی نیست', 'match_unconfirmed');
      const v = version != null ? intIn(version, 1, 100000, 0) : Number(img.current_version);
      const vr = v && await q1(c, 'SELECT review FROM image_versions WHERE image_id=$1 AND version=$2', [id, v]);
      if (!vr || vr.review === 'rejected') throw conflict('این نسخه نیست یا رد شده است', 'version_rejected');
      await c.query(`UPDATE image_versions SET review='approved' WHERE image_id=$1 AND version=$2`, [id, v]);
      await c.query(
        `UPDATE images SET space='central', status='APPROVED', search_enabled=true, current_version=$5, reviewed_by=$2,
            reviewed_at=$3, review_note=$4, rev=nextval('image_bank_rev'), updated_at=$3 WHERE id=$1`, [id, adminId, t, reason, v]);
      return { id, space: 'central', status: 'APPROVED', version: v };
    }
    if (img.status !== 'APPROVED') throw conflict('عکس بایگانی شده است', 'not_approved');
    const ver = intIn(version, 1, 100000, 0);
    const row = ver && await q1(c, 'SELECT review FROM image_versions WHERE image_id=$1 AND version=$2', [id, ver]);
    if (!row) throw badRequest('کدام نسخه؟', 'version_required');
    if (row.review === 'rejected') throw conflict('این نسخه رد شده است', 'version_rejected');
    await c.query(`UPDATE image_versions SET review='approved' WHERE image_id=$1 AND version=$2`, [id, ver]);
    await c.query(
      `UPDATE images SET current_version=$2, reviewed_by=$3, reviewed_at=$4, review_note=$5,
          rev=nextval('image_bank_rev'), updated_at=$4 WHERE id=$1`, [id, ver, adminId, t, reason]);
    return { id, space: 'central', status: 'APPROVED', version: ver };
  });
}

/** رد: pending ⇒ REJECTED؛ یا یک نسخهٔ تازهٔ عکسِ مرکزی. هیچ چیزی پاک نمی‌شود. */
async function reject(adminId, id, { reason, version }) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img) throw notFound('عکس پیدا نشد');
    const t = now();
    if (version != null) {
      const ver = intIn(version, 2, 100000, 0);
      if (!ver || ver === Number(img.current_version)) throw conflict('نسخهٔ جاری رد نمی‌شود — بایگانی کنید', 'version_current');
      const r = await q1(c, `UPDATE image_versions SET review='rejected' WHERE image_id=$1 AND version=$2 RETURNING version`, [id, ver]);
      if (!r) throw notFound('این نسخه نیست');
      await c.query(`UPDATE images SET rev=nextval('image_bank_rev'), updated_at=$2 WHERE id=$1`, [id, t]);
      return { id, version: ver, review: 'rejected' };
    }
    if (img.space !== 'pending' || !['DRAFT', 'NEEDS_REVIEW'].includes(img.status)) {
      throw conflict('این عکس در انتظارِ بررسی نیست', 'not_pending');
    }
    await c.query(
      `UPDATE images SET status='REJECTED', reviewed_by=$2, reviewed_at=$3, review_note=$4,
          rev=nextval('image_bank_rev'), updated_at=$3 WHERE id=$1`, [id, adminId, t, reason]);
    return { id, status: 'REJECTED' };
  });
}

/** بایگانی: انتخابِ تازه می‌ایستد، از جست‌وجو بیرون؛ سنجاق‌های قبلی کار می‌کنند. */
async function archive(adminId, id, { reason }) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img || img.space !== 'central') throw notFound('عکسِ مرکزی پیدا نشد');
    if (img.status === 'ARCHIVED') return { id, status: 'ARCHIVED', unchanged: true };
    await c.query(
      `UPDATE images SET status='ARCHIVED', search_enabled=false, reviewed_by=$2, reviewed_at=$3, review_note=$4,
          rev=nextval('image_bank_rev'), updated_at=$3 WHERE id=$1`, [id, adminId, now(), reason]);
    return { id, status: 'ARCHIVED' };
  });
}

async function setSearch(adminId, id, enabled) {
  return tx(async (c) => {
    const img = await adminImage(c, id);
    if (!img || img.space !== 'central' || img.status !== 'APPROVED') throw notFound('عکسِ مرکزیِ تأییدشده پیدا نشد');
    await c.query(`UPDATE images SET search_enabled=$2, rev=nextval('image_bank_rev'), updated_at=$3 WHERE id=$1`,
      [id, !!enabled, now()]);
    return { id, search_enabled: !!enabled };
  });
}

async function adminDetail(id, base) {
  const img = await one(`SELECT * FROM images WHERE id=$1 AND space<>'shop'`, [id]);
  if (!img) return null;
  const versions = await many(
    `SELECT v.version, v.kind, v.parent, v.tool, v.review, v.created_by, v.created_at, v.orientation,
            b.mime, b.size, b.width, b.height, b.hash
       FROM image_versions v JOIN image_blobs b ON b.hash=v.blob_hash WHERE v.image_id=$1 ORDER BY v.version`, [id]);
  const derivs = await derivativesOf([id]);
  const product = img.bank_product_id ? await one('SELECT * FROM bank_products WHERE id=$1', [img.bank_product_id]) : null;
  return {
    id: img.id, space: img.space, status: img.status, role: img.role, current_version: Number(img.current_version),
    search_enabled: img.search_enabled, match_status: img.match_status, match_reasons: json(img.match_reasons, []),
    license: { source: img.license_source, holder: img.license_holder, type: img.license_type,
      status: img.license_status, commercial_use: img.commercial_use, checked_at: Number(img.license_checked_at) },
    origin: img.origin_shop_id ? { shop_id: img.origin_shop_id, consent_by: img.consent_by, consent_at: Number(img.consent_at) } : null,
    proposal: json(img.proposal, {}), duplicate_of: json(img.duplicate_of, []), quality_flags: json(img.quality_flags, []),
    product: product ? productOut(product) : null, batch_id: img.batch_id, review_note: img.review_note,
    versions: versions.map((v) => ({
      version: v.version, kind: v.kind, parent: v.parent, tool: v.tool, review: v.review, mime: v.mime,
      size: Number(v.size), width: v.width, height: v.height, hash: v.hash, orientation: v.orientation,
      urls: { original: fileUrl(base, id, v.version, 'original'), ...urlsOf(base, id, v.version, derivs.get(id + '@' + v.version)) },
    })),
  };
}

async function queue({ status = 'NEEDS_REVIEW', space = 'pending', cursor = '', limit = 50 } = {}) {
  const st = oneOf(status, ['DRAFT', 'PROCESSING', 'NEEDS_REVIEW', 'APPROVED', 'REJECTED', 'ARCHIVED'], 'NEEDS_REVIEW');
  const sp = oneOf(space, ['pending', 'central'], 'pending');
  const args = [sp, st];
  let sql = `SELECT id, status, space, role, bank_product_id, match_status, license_status, commercial_use,
                    origin_shop_id, duplicate_of, quality_flags, batch_id, created_at
               FROM images WHERE space=$1 AND status=$2`;
  if (cursor) { args.push(String(cursor).slice(0, 40)); sql += ` AND id > $${args.length}`; }
  const lim = clamp(limit, PAGE_MAX);
  const rows = await many(`${sql} ORDER BY id LIMIT ${lim}`, args);
  return {
    items: rows.map((r) => ({
      ...r, created_at: Number(r.created_at), duplicate_of: json(r.duplicate_of, []), quality_flags: json(r.quality_flags, []),
      from_shop: !!r.origin_shop_id, origin_shop_id: undefined,
    })),
    next_cursor: rows.length === lim ? rows[rows.length - 1].id : null,
  };
}

// ════════════════════════════════════════════════════════════════════
//  ورودِ گروهی (۳۴.۲، ۳۷.۳، IMG-01، IMG-BANK-01)
// ════════════════════════════════════════════════════════════════════

async function batchCreate(adminId, b) {
  const id = 'ibt_' + ulid();
  const lic = licenseInput(b);
  const t = now();
  await one(
    `INSERT INTO image_batches (id, license_source, license_holder, license_type, license_status, commercial_use, note,
        created_by, created_at, updated_at) VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$9) RETURNING id`,
    [id, lic.source, lic.holder, lic.type, lic.status, lic.commercial, str(b.note, 500), adminId, t]);
  return { id, status: 'open', license: lic };
}

async function batchOf(c, id, { open = true } = {}) {
  const b = await q1(c, 'SELECT * FROM image_batches WHERE id=$1', [String(id || '').slice(0, 40)]);
  if (!b) throw notFound('دسته پیدا نشد');
  if (open && b.status !== 'open') {
    throw conflict(b.status === 'paused' ? 'دسته متوقف است — اول ادامه دهید' : 'دسته بسته است', 'batch_' + b.status);
  }
  return b;
}

const baseName = (s) => str(String(s || '').split(/[\\/]/).pop(), 200);

/** یک فایل: سنجش جدا؛ خرابی فقط همان فایل را «ناموفق» می‌کند. تکرار بی‌خطر. */
async function batchFile(id, name, buf) {
  const fname = baseName(name);
  if (!fname) throw badRequest('نامِ فایل لازم است', 'name_required');
  //  بی تراکنش: بلاب idempotent است و ردیفِ فایل با ON CONFLICT — retry بی‌خطر
  const c = { query: db.query };
  {
    await batchOf(c, id);
    let row;
    try {
      const { info, flags } = checkOriginal(buf, BANK_MAX_BYTES);
      if (!bytes.extensionMatches(fname, info.mime)) flags.push('extension_mismatch');
      const hash = await putBlob(c, buf, info);
      row = { status: 'ok', error: '', hash, flags, orientation: info.orientation };
    } catch (err) {
      if (!(err instanceof ApiError)) throw err;
      row = { status: 'failed', error: err.code, hash: '', flags: [], orientation: 0 };
    }
    await c.query(
      `INSERT INTO image_batch_files (batch_id, name, blob_hash, status, error, warnings, orientation, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8)
       ON CONFLICT (batch_id, name) DO UPDATE SET blob_hash=EXCLUDED.blob_hash, status=EXCLUDED.status,
          error=EXCLUDED.error, warnings=EXCLUDED.warnings, orientation=EXCLUDED.orientation`,
      [id, fname, row.hash, row.status, row.error, JSON.stringify(row.flags), row.orientation, now()]);
    return { name: fname, status: row.status, error: row.error, quality_flags: row.flags, sha256: row.hash || sha256(buf) };
  }
}

/** CSV با سطرِ سرآیند ⇒ ردیف‌ها (row_no = شمارهٔ سطرِ فایل). نقل‌قولِ "…" پشتیبانی می‌شود. */
function parseCsv(text) {
  const lines = [];
  let row = [], field = '', q = false;
  const s = String(text || '').replace(/^﻿/, '');
  for (let i = 0; i < s.length; i++) {
    const ch = s[i];
    if (q) {
      if (ch === '"' && s[i + 1] === '"') { field += '"'; i++; } else if (ch === '"') q = false; else field += ch;
    } else if (ch === '"') q = true;
    else if (ch === ',') { row.push(field); field = ''; }
    else if (ch === '\n' || ch === '\r') {
      if (ch === '\r' && s[i + 1] === '\n') i++;
      row.push(field); lines.push(row); row = []; field = '';
    } else field += ch;
  }
  if (field || row.length) { row.push(field); lines.push(row); }
  if (!lines.length) return [];
  const head = lines[0].map((h) => h.trim().toLowerCase());
  const out = [];
  for (let n = 1; n < lines.length; n++) {
    if (lines[n].every((x) => !x.trim())) continue;
    const o = { row_no: n + 1 };
    head.forEach((h, j) => { if (h) o[h] = (lines[n][j] || '').trim(); });
    out.push(o);
  }
  return out;
}

const ROW_FIELDS = ['product_id', 'barcode', 'sku', 'name', 'brand', 'category', 'variant', 'size', 'unit',
  'pack_count', 'packaging', 'image_path', 'image_role', 'license_source', 'license_status', 'alt_names'];

async function processRow(c, batch, raw, adminId, dryRun) {
  const input = {};
  for (const k of ROW_FIELDS) if (raw[k] != null && raw[k] !== '') input[k] = str(raw[k], 200);
  if (!input.image_path && raw.image_filename) input.image_path = str(raw.image_filename, 200);
  const res = { status: 'ok', error: '', image_id: '', product_id: '', candidates: [], duplicate_of: [], match: 'unconfirmed' };
  const fail = (code) => ({ ...res, status: 'failed', error: code });

  const fname = baseName(input.image_path);
  if (!fname) return fail('image_path_required');
  const file = await q1(c, 'SELECT * FROM image_batch_files WHERE batch_id=$1 AND name=$2', [batch.id, fname]);
  if (!file) return fail('file_missing');
  if (file.status !== 'ok') return fail(file.error || 'file_failed');

  const asProduct = {
    name: input.name, gtin: input.barcode, sku: input.sku, brand: input.brand, category: input.category,
    variant: input.variant, size_value: input.size, size_unit: input.unit, pack_count: input.pack_count,
    packaging: input.packaging, alt_names: input.alt_names,
  };
  let create = null;
  let reasons = [];
  const bp = (p) => ({ ...p, alt_names: json(p.alt_names, []) });
  if (input.product_id) {
    const p = await q1(c, 'SELECT * FROM bank_products WHERE id=$1', [input.product_id]);
    if (!p) return fail('product_not_found');
    Object.assign(res, { product_id: p.id, match: 'exact' }); reasons = ['product_id'];
  } else {
    const g = ID.gtin(input.barcode);
    if (input.barcode && !g.valid) return fail('bad_barcode');
    const byKey = g.valid
      ? await qa(c, 'SELECT * FROM bank_products WHERE gtin14=$1', [g.gtin14])
      : input.sku ? await qa(c, 'SELECT * FROM bank_products WHERE sku=$1 LIMIT 5', [input.sku]) : null;
    if (byKey && byKey.length === 1) {
      //  بارکد: قاعدهٔ ۳۷.۴؛ SKU: کلیدِ خودِ مدیر — فقط مشخصهٔ ناهمسان مانع است
      const cl = ID.classify(bp(byKey[0]), { ...asProduct, gtin: g.valid ? input.barcode : byKey[0].gtin });
      if (g.valid ? cl.status === 'exact' : !cl.diffs.length) {
        Object.assign(res, { product_id: byKey[0].id, match: 'exact' }); reasons = g.valid ? ['barcode_exact'] : ['sku_exact'];
      } else {
        Object.assign(res, { status: 'ambiguous', match: 'ambiguous', candidates: [byKey[0].id] }); reasons = cl.diffs;
      }
    } else if (byKey && byKey.length > 1) {
      Object.assign(res, { status: 'ambiguous', match: 'ambiguous', candidates: byKey.map((p) => p.id) }); reasons = ['sku_many'];
    } else if (!input.name) {
      return fail(byKey ? 'name_required' : 'identity_required');
    } else {
      const same = await qa(c, `SELECT * FROM bank_products WHERE name_key=$1 AND status='active' LIMIT 10`, [T.compareKey(input.name)]);
      if (byKey || !same.length) {
        //  کالای تازه: با بارکدِ معتبر تعریفش قطعی است؛ بی بارکد نیازمندِ بررسی
        try { create = productInput(asProduct); } catch (err) { if (err instanceof ApiError) return fail(err.code); throw err; }
        Object.assign(res, { match: g.valid || input.sku ? 'exact' : 'probable', status: g.valid || input.sku ? 'ok' : 'needs_review' });
        reasons = ['new_product'];
      } else {
        const fits = same.filter((p) => ID.classify(bp(p), asProduct).status === 'probable');
        if (fits.length === 1 && same.length === 1) {
          Object.assign(res, { status: 'needs_review', product_id: fits[0].id, match: 'probable' }); reasons = ['name_same'];
        } else {
          Object.assign(res, { status: 'ambiguous', match: 'ambiguous', candidates: same.map((p) => p.id) }); reasons = ['name_many'];
        }
      }
    }
  }
  res.duplicate_of = await duplicatesOf(c, file.blob_hash, 'pending', '');
  if (res.duplicate_of.length && res.status === 'ok') res.status = 'duplicate';
  if (dryRun) return { ...res, product_id: res.product_id || (create ? 'new' : '') };

  if (create) res.product_id = await insertProduct(c, create, adminId);
  const lic = licenseInput(input, {
    source: batch.license_source, holder: batch.license_holder, type: batch.license_type,
    status: batch.license_status, commercial: batch.commercial_use,
  });
  const out = await insertImage(c, {
    space: 'pending', status: 'NEEDS_REVIEW', role: oneOf(input.image_role, ROLES, 'main'),
    bankProductId: res.match === 'ambiguous' ? '' : res.product_id, match: res.match, reasons,
    hash: file.blob_hash, orientation: Number(file.orientation), flags: json(file.warnings, []),
    createdBy: adminId, batchId: batch.id, license: lic, dups: res.duplicate_of,
    proposal: res.candidates.length ? { candidates: res.candidates } : {},
  });
  res.image_id = out.id;
  return res;
}

/**
 * ردیف‌ها: پیش‌نمایش (dry_run) یا ثبت. هر ردیف تراکنشِ خودش را دارد؛ خطای
 * یکی بقیه را نمی‌خواباند. ردیفِ ثبت‌شده با همان row_no دوباره ساخته
 * نمی‌شود (retryِ امن)؛ فقط ردیفِ «ناموفق» دوباره سنجیده می‌شود.
 */
async function batchRows(adminId, id, { rows, csv, dry_run: dryRun }) {
  const list = csv != null ? parseCsv(csv) : (Array.isArray(rows) ? rows : []);
  if (!list.length) throw badRequest('ردیفی نیامد', 'rows_required');
  if (list.length > ROWS_MAX) throw badRequest(`حداکثر ${ROWS_MAX} ردیف در هر درخواست`, 'too_many_rows');
  const batch = await tx((c) => batchOf(c, id));
  const results = [];
  for (let i = 0; i < list.length; i++) {
    const raw = list[i] || {};
    const rowNo = intIn(raw.row_no, 1, 10_000_000, i + 1);
    try {
      //  توقف/لغو میانِ همین درخواست هم دیده می‌شود (ردیف به ردیف)
      const cur = await one('SELECT status FROM image_batches WHERE id=$1', [batch.id]);
      if (cur.status !== 'open') { results.push({ row_no: rowNo, status: 'skipped', error: 'batch_' + cur.status }); continue; }
      const run = dryRun ? (fn) => fn({ query: db.query }) : tx;
      const r = await run(async (c) => {
        const had = await q1(c, 'SELECT * FROM image_batch_rows WHERE batch_id=$1 AND row_no=$2', [batch.id, rowNo]);
        if (had && had.status !== 'failed' && !dryRun) {
          return { row_no: rowNo, status: had.status, error: had.error, image_id: had.image_id,
            product_id: had.product_id, candidates: json(had.candidates, []), replay: true };
        }
        const out = await processRow(c, batch, raw, adminId, !!dryRun);
        if (!dryRun) {
          await c.query(
            `INSERT INTO image_batch_rows (batch_id, row_no, status, error, image_id, product_id, candidates, input, created_at)
             VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9)
             ON CONFLICT (batch_id, row_no) DO UPDATE SET status=EXCLUDED.status, error=EXCLUDED.error,
                image_id=EXCLUDED.image_id, product_id=EXCLUDED.product_id, candidates=EXCLUDED.candidates,
                input=EXCLUDED.input, created_at=EXCLUDED.created_at`,
            [batch.id, rowNo, out.status, out.error, out.image_id, out.product_id, JSON.stringify(out.candidates),
              JSON.stringify(raw).slice(0, 4000), now()]);
        }
        return { row_no: rowNo, ...out };
      });
      results.push(r);
    } catch (err) {
      if (!(err instanceof ApiError)) console.error('[image-bank] ردیف', rowNo, err.message);
      const code = err instanceof ApiError ? err.code : 'row_error';
      if (!dryRun) {
        await one(
          `INSERT INTO image_batch_rows (batch_id, row_no, status, error, input, created_at) VALUES ($1,$2,'failed',$3,$4,$5)
           ON CONFLICT (batch_id, row_no) DO UPDATE SET status='failed', error=EXCLUDED.error RETURNING row_no`,
          [batch.id, rowNo, code, JSON.stringify(raw).slice(0, 4000), now()]).catch(() => null);
      }
      results.push({ row_no: rowNo, status: 'failed', error: code });
    }
  }
  const counts = {};
  for (const r of results) counts[r.status] = (counts[r.status] || 0) + 1;
  return { batch_id: batch.id, dry_run: !!dryRun, counts, rows: results };
}

async function batchStatus(adminId, id, status) {
  const to = oneOf(status, ['open', 'paused', 'cancelled', 'done'], '');
  if (!to) throw badRequest('وضعیت: open، paused، cancelled یا done', 'bad_status');
  return tx(async (c) => {
    const b = await batchOf(c, id, { open: false });
    if (b.status === 'cancelled' || b.status === 'done') {
      if (b.status === to) return { id: b.id, status: to, unchanged: true };
      throw conflict('دسته بسته است', 'batch_closed');
    }
    await c.query('UPDATE image_batches SET status=$2, updated_at=$3 WHERE id=$1', [b.id, to, now()]);
    let rejected = 0;
    if (to === 'cancelled') {
      //  لغو: عکس‌های هنوز بررسی‌نشدهٔ همین دسته رد (نه پاک) می‌شوند
      const r = await qa(c,
        `UPDATE images SET status='REJECTED', review_note='batch_cancelled', reviewed_by=$2, reviewed_at=$3,
            rev=nextval('image_bank_rev'), updated_at=$3
          WHERE batch_id=$1 AND space='pending' AND status IN ('DRAFT','NEEDS_REVIEW') RETURNING id`, [b.id, adminId, now()]);
      rejected = r.length;
    }
    return { id: b.id, status: to, rejected };
  });
}

async function batchReport(id) {
  const b = await one('SELECT * FROM image_batches WHERE id=$1', [String(id || '').slice(0, 40)]);
  if (!b) return null;
  const rows = await many('SELECT row_no, status, error, image_id, product_id, candidates FROM image_batch_rows WHERE batch_id=$1 ORDER BY row_no', [b.id]);
  const files = await many('SELECT name, status, error, warnings FROM image_batch_files WHERE batch_id=$1 ORDER BY name', [b.id]);
  const counts = {};
  for (const r of rows) counts[r.status] = (counts[r.status] || 0) + 1;
  const fileCounts = {};
  for (const f of files) fileCounts[f.status] = (fileCounts[f.status] || 0) + 1;
  return {
    id: b.id, status: b.status, license: { source: b.license_source, status: b.license_status, commercial_use: b.commercial_use },
    counts, files: { counts: fileCounts, failed: files.filter((f) => f.status === 'failed').map((f) => ({ name: f.name, error: f.error })) },
    rows: rows.map((r) => ({ ...r, candidates: json(r.candidates, []) })),
  };
}

function reportCsv(rep) {
  const esc = (v) => /[",\n\r]/.test(String(v)) ? '"' + String(v).replace(/"/g, '""') + '"' : String(v);
  const lines = ['row_no,status,error,image_id,product_id,candidates'];
  for (const r of rep.rows) {
    if (r.status === 'ok') continue;
    lines.push([r.row_no, r.status, r.error, r.image_id, r.product_id, r.candidates.join('|')].map(esc).join(','));
  }
  for (const f of rep.files.failed) lines.push(['file:' + f.name, 'failed', f.error, '', '', ''].map(esc).join(','));
  return '﻿' + lines.join('\r\n') + '\r\n';
}

async function reportsList({ status = 'open', limit = 50 } = {}) {
  const rows = await many(
    `SELECT id, image_id, kind, note, status, created_at FROM image_reports WHERE status=$1 ORDER BY created_at DESC LIMIT ${intIn(limit, 1, PAGE_MAX, PAGE_MAX)}`,
    [oneOf(status, ['open', 'resolved', 'dismissed'], 'open')]);
  return rows.map((r) => ({ ...r, created_at: Number(r.created_at) }));
}

module.exports = {
  SHOP_FLAG, CENTRAL_FLAG, SIZES, DERIV, ROLES, LICENSES, REPORT_KINDS, ROWS_MAX,
  flagOf, shopEnabled, centralEnabled, imageRow, readFile, fileUrl, putBlob,
  productInput, insertProduct, updateProduct, productOut,
  search, shopUpload, shopCanRead, shopVersion, shopDerivative, shopList, select, resolve, propose, report,
  adminUpload, adminPatch, adminVersion, adminDerivative, approve, reject, archive, setSearch, adminDetail, queue,
  batchCreate, batchFile, batchRows, batchStatus, batchReport, reportCsv, reportsList, parseCsv, tx,
};
