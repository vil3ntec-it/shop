'use strict';
/**
 * بانکِ تصاویر — پنلِ مدیر (v3 ۳۷.۳). شرح: ‎lib/image-bank.js‎.
 *
 *   مدیر (هر نقش):  کالای بانک، بارگذاری به pending، ویرایشِ pending، نسخه و
 *                   نسخهٔ کوچک، ورودِ گروهی (دسته، فهرستِ دسته‌ها، فایل، ردیف‌ها،
 *                   گزارش)، صف و `/review` (صف با جزئیات)؛ `/status` (روشن یا نه)
 *                   حتی با پرچمِ خاموش
 *   مدیرِ ارشد:     تأیید، رد، بایگانی، روشن/خاموش در جست‌وجو — هر بار با
 *                   دلیلِ نوشته و یک ردیفِ سابقه (الگوی ADMIN-CONTROL-PLANE)
 *
 * ⛔ پیش‌فرض خاموش: بی `image_bank.central` (scope = all) همه‌چیز ۴۰۳ است.
 * ⛔ عکسِ اختصاصیِ دکان این‌جا دیده نمی‌شود؛ فقط کپیِ پیشنهادی با اجازهٔ صریح.
 */
const express = require('express');
const bank = require('../lib/image-bank');
const upload = require('../lib/image-upload');
const audit = require('../lib/audit');
const { requireReason } = require('../lib/admin-access');
const { requireAdmin, requireSuperAdmin } = require('../middleware/auth');
const { clientIp } = require('../middleware/ratelimit');
const { forbidden, notFound, badRequest } = require('../middleware/errors');
const { many } = require('../db');

const router = express.Router();
const BASE = '/api/admin/image-bank';
const raw = express.raw({ type: () => true, limit: bank.DERIV.zoom.bytes + 1024 });
//  بدنهٔ خام + سنجشِ `X-Content-SHA256` اگر کلاینت فرستاده باشد (IMG-14)
const bufOf = (req) => {
  const buf = Buffer.isBuffer(req.body) ? req.body : Buffer.alloc(0);
  upload.verifyDigest(buf, req.get('x-content-sha256'));
  return buf;
};
const num = (x) => {
  const n = Number(x);
  if (!Number.isInteger(n) || n < 1 || n > 100000) throw badRequest('نسخه درست نیست', 'bad_version');
  return n;
};

router.use(requireAdmin);

/**
 * پنل پیش از هر چیز می‌پرسد بانک روشن است یا نه — بی ۴۰۳، تا زبانهٔ پنل با
 * پرچمِ خاموش پیامِ روشن بدهد نه خطای قرمز (WP-2، پنلِ بانک).
 */
router.get('/status', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json({
    enabled: await bank.centralEnabled(), rows_max: bank.ROWS_MAX, roles: bank.ROLES, licenses: bank.LICENSES,
    resume: await upload.resumeEnabled(),
  });
});

router.use(async (req, res, next) => {
  if (!(await bank.centralEnabled())) return next(forbidden('بانکِ مرکزیِ تصاویر روشن نیست', 'image_bank_disabled'));
  next();
});

//  دلیلِ نوشته (۳ تا ۲۰۰ نویسه) — همان `requireReason`ِ ADMIN-CONTROL-PLANE
const reasonOf = requireReason;
const log = (req, action, targetId, detail = {}) => audit.log({
  actorType: 'admin', userId: req.admin.id, action, targetType: 'image', targetId, ip: clientIp(req), detail,
});

/* ------------------------------------------------------------ کالای بانک */

router.post('/products', async (req, res) => {
  const p = bank.productInput(req.body || {});
  const id = await bank.tx((c) => bank.insertProduct(c, p, req.admin.id));
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.bank_product_created', targetType: 'bank_product', targetId: id });
  res.status(201).json({ id });
});

router.patch('/products/:id', async (req, res) => {
  const p = bank.productInput(req.body || {});
  await bank.tx((c) => bank.updateProduct(c, String(req.params.id).slice(0, 40), p));
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.bank_product_updated', targetType: 'bank_product', targetId: req.params.id });
  res.json({ id: req.params.id });
});

router.get('/search', async (req, res) => {
  res.json(await bank.search(req.query || {}, { base: BASE, all: true }));
});

/* ------------------------------------------------------------ عکس‌ها */

router.post('/images', raw, async (req, res) => {
  const out = await bank.adminUpload(req.admin.id, bufOf(req), req.query || {});
  await log(req, 'admin.bank_image_uploaded', out.id, { duplicate_of: out.duplicate_of });
  res.status(201).json(out);
});

router.get('/queue', async (req, res) => {
  res.json(await bank.queue(req.query || {}));
});

/**
 * صفِ بررسی برای پنل (IMG-02، IMG-PRO-07، IMG-BANK-17، IMG-09): همان `queue` با
 * جزئیاتِ هر عکس — وضعیت و دلیلِ تطبیق، کالای وصل، کالاهای ممکنِ تطبیقِ مبهم
 * با SKU/بارکد/نوع/اندازه، نشانِ تکراری و کیفیت، مجوز، نشانیِ فایلِ نسخهٔ جاری و
 * شمارِ نسخه‌های تازهٔ منتظرِ بررسی (`versions_pending`، IMG-07، IMG-PRO-05).
 * فقط‌خواندنی؛ همان صفحه‌بندیِ `queue` (حداکثر ۵۰).
 */
router.get('/review', async (req, res) => {
  const q = await bank.queue(req.query || {});
  const details = await Promise.all(q.items.map((i) => bank.adminDetail(i.id, BASE)));
  const candIds = [...new Set(details.flatMap((d) => d?.proposal?.candidates || []))].map(String).slice(0, 500);
  const cands = candIds.length ? await many('SELECT * FROM bank_products WHERE id = ANY($1::text[])', [candIds]) : [];
  const byId = new Map(cands.map((p) => [p.id, bank.productOut(p)]));
  //  نسخه‌های منتظرِ بررسی جز نسخهٔ جاری (IMG-07، IMG-PRO-05): نسخهٔ تازهٔ عکسِ منتشرشده بی‌صدا جاری
  //  نمی‌شود، پس مدیر باید ببیند که چیزی منتظرِ مقایسه و پذیرش/رد است
  const ids = q.items.map((i) => i.id);
  const waiting = ids.length ? await many(
    `SELECT v.image_id, COUNT(*)::int AS n FROM image_versions v JOIN images i ON i.id = v.image_id
      WHERE v.image_id = ANY($1::text[]) AND v.review = 'needs_review' AND v.version <> i.current_version
      GROUP BY v.image_id`, [ids]) : [];
  const pendingOf = new Map(waiting.map((w) => [w.image_id, Number(w.n)]));
  res.set('Cache-Control', 'no-store');
  res.json({
    items: q.items.map((i, k) => {
      const d = details[k] || {};
      const v = (d.versions || []).find((x) => x.version === d.current_version) || {};
      return {
        id: i.id, status: i.status, space: i.space, role: i.role, from_shop: i.from_shop, batch_id: i.batch_id,
        created_at: i.created_at, match_status: d.match_status || '', match_reasons: d.match_reasons || [],
        quality_flags: i.quality_flags, duplicate_of: i.duplicate_of, license: d.license || null,
        product: d.product || null,
        candidates: (d.proposal?.candidates || []).map((id) => byId.get(String(id)) || { id: String(id), missing: true }),
        version: d.current_version || null,
        versions_pending: pendingOf.get(i.id) || 0,
        file: v.urls ? { url: v.urls.thumb || v.urls.original, mime: v.mime, width: v.width, height: v.height } : null,
      };
    }),
    next_cursor: q.next_cursor,
  });
});

router.get('/images/:id', async (req, res) => {
  const d = await bank.adminDetail(String(req.params.id).slice(0, 40), BASE);
  if (!d) throw notFound('عکس پیدا نشد');
  res.json(d);
});

router.patch('/images/:id', async (req, res) => {
  const out = await bank.adminPatch(req.admin.id, req.params.id, req.body || {});
  await log(req, 'admin.bank_image_edited', req.params.id, out);
  res.json(out);
});

router.post('/images/:id/versions', raw, async (req, res) => {
  const out = await bank.adminVersion(req.admin.id, req.params.id, bufOf(req), req.query || {});
  await log(req, 'admin.bank_image_version', req.params.id, { version: out.version });
  res.status(201).json(out);
});

router.put('/images/:id/v/:version/:size', raw, async (req, res) => {
  res.json(await bank.adminDerivative(req.params.id, num(req.params.version), req.params.size, bufOf(req)));
});

router.post('/images/:id/approve', requireSuperAdmin, async (req, res) => {
  const reason = reasonOf(req);
  const out = await bank.approve(req.admin.id, req.params.id, { reason, version: req.body?.version });
  await log(req, 'admin.bank_image_approved', req.params.id, { reason, version: out.version });
  res.json(out);
});

router.post('/images/:id/reject', requireSuperAdmin, async (req, res) => {
  const reason = reasonOf(req);
  const out = await bank.reject(req.admin.id, req.params.id, { reason, version: req.body?.version });
  await log(req, 'admin.bank_image_rejected', req.params.id, { reason, version: req.body?.version ?? null });
  res.json(out);
});

router.post('/images/:id/archive', requireSuperAdmin, async (req, res) => {
  const reason = reasonOf(req);
  const out = await bank.archive(req.admin.id, req.params.id, { reason });
  await log(req, 'admin.bank_image_archived', req.params.id, { reason });
  res.json(out);
});

router.post('/images/:id/search', requireSuperAdmin, async (req, res) => {
  const reason = reasonOf(req);
  const out = await bank.setSearch(req.admin.id, req.params.id, req.body?.enabled === true);
  await log(req, 'admin.bank_image_search', req.params.id, { reason, enabled: out.search_enabled });
  res.json(out);
});

/** فایلِ عکسِ central/pending (با اصل) — عکسِ دکان هرگز. */
router.get('/file/:id/:version/:size', async (req, res) => {
  const size = String(req.params.size);
  const img = bank.SIZES.includes(size) ? await bank.imageRow(req.params.id) : null;
  if (!img || img.space === 'shop') throw notFound('عکس پیدا نشد');
  const file = await bank.readFile(img.id, num(req.params.version), size);
  if (!file) throw notFound('این اندازه هنوز ساخته نشده', 'no_derivative');
  res.set('Cache-Control', 'private, no-cache');
  res.set('ETag', '"' + file.hash + '"');
  res.set('Content-Type', file.mime);
  res.set('X-Content-Type-Options', 'nosniff');
  res.send(file.data);
});

router.get('/reports', async (req, res) => {
  res.json({ items: await bank.reportsList(req.query || {}) });
});

/* ------------------------------------------------------------ ورودِ گروهی */

router.post('/batches', async (req, res) => {
  const out = await bank.batchCreate(req.admin.id, req.body || {});
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.bank_batch_created', targetType: 'image_batch', targetId: out.id, detail: out.license });
  res.status(201).json(out);
});

router.put('/batches/:id/files/:name', raw, async (req, res) => {
  res.json(await bank.batchFile(req.params.id, req.params.name, bufOf(req)));
});

/** فایل‌های رسیدهٔ دسته با sha256 — ادامهٔ بارگذاری پس از قطع (IMG-14، IMG-PRO-06). فقط‌خواندنی. */
router.get('/batches/:id/files', async (req, res) => {
  res.set('Cache-Control', 'no-store');
  res.json(await upload.batchFiles(req.params.id, { after: req.query?.after || '', limit: req.query?.limit }));
});

//  ⚠️ CSVِ ۱۰۰۰ ردیفی از سقفِ ۲ مگابایتیِ JSON کوچک‌تر است
router.post('/batches/:id/rows', async (req, res) => {
  const out = await bank.batchRows(req.admin.id, req.params.id, req.body || {});
  if (!out.dry_run) {
    await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.bank_batch_rows', targetType: 'image_batch', targetId: out.batch_id, detail: out.counts });
  }
  res.json(out);
});

router.post('/batches/:id/status', async (req, res) => {
  const out = await bank.batchStatus(req.admin.id, req.params.id, req.body?.status);
  await audit.log({ actorType: 'admin', userId: req.admin.id, action: 'admin.bank_batch_status', targetType: 'image_batch', targetId: out.id, detail: out });
  res.json(out);
});

/** دسته‌های اخیر با شمارِ فایل و ردیف — پیشرفتِ دسته در پنل (IMG-01). */
router.get('/batches', async (req, res) => {
  const rows = await many(
    `SELECT b.id, b.status, b.license_source, b.license_status, b.commercial_use, b.note, b.created_at, b.updated_at,
            (SELECT COUNT(*)::int FROM image_batch_files f WHERE f.batch_id = b.id) AS files,
            (SELECT COUNT(*)::int FROM image_batch_files f WHERE f.batch_id = b.id AND f.status = 'failed') AS files_failed,
            (SELECT COUNT(*)::int FROM image_batch_rows r WHERE r.batch_id = b.id) AS rows,
            (SELECT COUNT(*)::int FROM image_batch_rows r WHERE r.batch_id = b.id AND r.status = 'failed') AS rows_failed,
            (SELECT COUNT(*)::int FROM image_batch_rows r WHERE r.batch_id = b.id AND r.status = 'ambiguous') AS rows_ambiguous
       FROM image_batches b ORDER BY b.created_at DESC, b.id DESC LIMIT 30`
  );
  res.set('Cache-Control', 'no-store');
  res.json({ items: rows.map((r) => ({ ...r, created_at: Number(r.created_at), updated_at: Number(r.updated_at) })) });
});

router.get('/batches/:id/report', async (req, res) => {
  const rep = await bank.batchReport(req.params.id);
  if (!rep) throw notFound('دسته پیدا نشد');
  if (req.query?.format === 'csv') {
    res.set('Content-Type', 'text/csv; charset=utf-8');
    res.set('Content-Disposition', `attachment; filename="batch-${rep.id}-errors.csv"`);
    return res.send(bank.reportCsv(rep));
  }
  res.json(rep);
});

module.exports = router;
