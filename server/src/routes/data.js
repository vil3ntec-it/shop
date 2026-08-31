'use strict';
/**
 * مسیرهای مستقیم داده‌های دکان.
 *
 * همان انبار رکوردی است که همگام‌سازی از آن استفاده می‌کند — نه یک نسخه‌ی
 * موازی. پس چه رکورد از راه Sync بیاید و چه از این مسیرها، یک جا نوشته
 * می‌شود و گزارش‌ها دو روایت مختلف پیدا نمی‌کنند.
 *
 * هر مسیر: shop_id از توکن، صفحه‌بندی اجباری، اعتبارسنجی ورودی،
 * و برای عملیات چندتکه‌ای (فروش) یک تراکنش واحد.
 */
const express = require('express');
const { many, one, now } = require('../db');
const v = require('../lib/validate');
const sync = require('../lib/sync');
const audit = require('../lib/audit');
const { requireUser, requireShop, requirePermission, requireDataWrite } = require('../middleware/auth');
const { badRequest, notFound, forbidden } = require('../middleware/errors');
const { can } = require('../lib/permissions');

const router = express.Router();
router.use(requireUser, requireShop);

/*
 * نوشتن — هر جور نوشتنی — پشتِ همان قاعده‌ی همگام‌سازی است.
 *
 * این مسیرها راهِ دومِ نوشتن روی دفترِ دکان‌اند. اگر فقط `/api/sync`
 * بسته می‌شد و اینجا باز می‌ماند، بستنِ آن یکی بی‌معنی بود.
 * خواندن (`GET`) دست نمی‌خورد.
 */
const writeGuard = requireDataWrite;

/** نام مسیر → نام مجموعه در برنامه. */
const ROUTES = {
  products:   'products',
  inventory:  'warehouseEntries',
  sales:      'sales',
  'sale-items': 'saleItems',
  returns:    'returns',
  debtors:    'debtors',
  payments:   'transactions',
  expenses:   'expenses',
  suppliers:  'suppliers',
  purchases:  'purchases',
};

const MAX_PAGE = 500;

/** خواندن صفحه‌ای — گوشی هرگز کل دیتابیس را یکجا نمی‌گیرد. */
async function listRecords(shopId, collection, { limit, since, cursor, includeDeleted }) {
  const table = sync.TABLES[collection];
  const rows = await many(
    `SELECT id, rev, version, created_at, updated_at, deleted, user_id, data
       FROM ${table}
      WHERE shop_id = $1
        AND rev > $2
        ${includeDeleted ? '' : 'AND deleted = false'}
        AND updated_at >= $3
      ORDER BY rev ASC
      LIMIT $4`,
    [shopId, cursor, since, limit + 1]
  );
  const hasMore = rows.length > limit;
  const page = hasMore ? rows.slice(0, limit) : rows;
  return {
    items: page.map(r => ({
      id: r.id, rev: Number(r.rev), version: r.version,
      createdAt: Number(r.created_at), updatedAt: Number(r.updated_at),
      deleted: r.deleted, userId: r.user_id, ...r.data,
    })),
    nextCursor: page.length ? Number(page[page.length - 1].rev) : cursor,
    hasMore,
  };
}

/** نوشتن یک یا چند رکورد در یک تراکنش، با ضد ثبت تکراری. */
async function writeRecords(req, changes) {
  const deviceId = v.id(req.body?.deviceId, { required: false, max: 64 });
  const operationId = v.id(req.body?.operationId || req.headers['idempotency-key'], {
    required: false, max: 80,
  });
  const { result, replayed } = await sync.runIdempotent(
    { shopId: req.shopId, userId: req.user.id, deviceId, operationId },
    () => sync.pushChanges(
      { shopId: req.shopId, userId: req.user.id, deviceId, role: req.role }, changes
    )
  );
  return { ...result, replayed };
}

function recordId(body, fallbackPrefix) {
  const given = v.id(body?.id, { required: false, max: 80 });
  if (given) return given;
  const { randomBytes } = require('crypto');
  return `${fallbackPrefix}_${randomBytes(9).toString('hex')}`;
}

// ---------- مسیرهای عمومی هر مجموعه ----------
for (const [path, collection] of Object.entries(ROUTES)) {
  router.get(`/${path}`, async (req, res) => {
    const limit = v.integer(req.query?.limit, { min: 1, max: MAX_PAGE, def: 100, field: 'limit' });
    const since = v.integer(req.query?.since, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0, field: 'since' });
    const cursor = v.integer(req.query?.cursor, { min: 0, max: Number.MAX_SAFE_INTEGER, def: 0, field: 'cursor' });
    const includeDeleted = v.bool(req.query?.includeDeleted, false);
    res.json(await listRecords(req.shopId, collection, { limit, since, cursor, includeDeleted }));
  });

  router.get(`/${path}/:id`, async (req, res, next) => {
    const id = v.id(req.params.id);
    const row = await one(
      `SELECT id, rev, version, created_at, updated_at, deleted, user_id, data
         FROM ${sync.TABLES[collection]} WHERE shop_id=$1 AND id=$2`,
      [req.shopId, id]
    );
    if (!row || row.deleted) return next(notFound('این رکورد پیدا نشد'));
    res.json({
      id: row.id, rev: Number(row.rev), version: row.version,
      createdAt: Number(row.created_at), updatedAt: Number(row.updated_at),
      userId: row.user_id, ...row.data,
    });
  });

  router.post(`/${path}`, writeGuard, async (req, res, next) => {
    const data = v.payload(req.body?.data ?? req.body, { field: 'داده' });
    const id = recordId(req.body, collection.slice(0, 3));
    delete data.id; delete data.deviceId; delete data.operationId;
    const out = await writeRecords(req, [{
      collection, id, updatedAt: v.timestamp(req.body?.updatedAt, { def: now() }), data,
    }]);
    res.status(201).json({ id, ...out });
  });

  router.put(`/${path}/:id`, writeGuard, async (req, res) => {
    const id = v.id(req.params.id);
    const data = v.payload(req.body?.data ?? req.body, { field: 'داده' });
    delete data.id; delete data.deviceId; delete data.operationId;
    const out = await writeRecords(req, [{
      collection, id, updatedAt: v.timestamp(req.body?.updatedAt, { def: now() }), data,
    }]);
    res.json({ id, ...out });
  });

  router.delete(`/${path}/:id`, writeGuard, async (req, res, next) => {
    const id = v.id(req.params.id);
    if (!can(req.role, 'data.delete.own')) return next(forbidden('اجازه‌ی حذف ندارید', 'permission_denied'));
    const out = await writeRecords(req, [{ collection, id, updatedAt: now(), deleted: true }]);
    if (out.conflicts?.some(c => c.reason === 'delete_not_allowed')) {
      return next(forbidden('فقط رکوردهای خودتان را می‌توانید حذف کنید', 'permission_denied'));
    }
    res.json({ id, ...out });
  });
}

/**
 * ثبت فروش کامل.
 *
 * فروش، اقلام آن، حرکت انبار و پرداخت/بدهی همه در یک تراکنش نوشته
 * می‌شوند: یا همه ثبت می‌شوند یا هیچ‌کدام. پس هیچ‌وقت فروشی بدون قلم
 * یا انباری بدون فروش نمی‌ماند.
 */
router.post('/sales/full', writeGuard, async (req, res, next) => {
  const sale = v.payload(req.body?.sale, { field: 'فروش' });
  const items = Array.isArray(req.body?.items) ? req.body.items : [];
  const movements = Array.isArray(req.body?.stockMovements) ? req.body.stockMovements : [];
  const payment = req.body?.payment ? v.payload(req.body.payment, { field: 'پرداخت' }) : null;

  if (!items.length) return next(badRequest('فروش بدون قلم ثبت نمی‌شود', 'empty_sale'));

  const saleId = recordId(req.body?.sale, 'sale');
  const t = v.timestamp(req.body?.updatedAt, { def: now() });

  const changes = [{ collection: 'sales', id: saleId, updatedAt: t, data: { ...sale, id: saleId } }];
  for (const it of items) {
    const id = recordId(it, 'item');
    changes.push({
      collection: 'saleItems', id, updatedAt: t,
      data: { ...v.payload(it, { field: 'قلم فروش' }), id, saleId },
    });
  }
  for (const mv of movements) {
    const id = recordId(mv, 'mv');
    changes.push({
      collection: 'stockMovements', id, updatedAt: t,
      data: { ...v.payload(mv, { field: 'حرکت انبار' }), id, saleId },
    });
  }
  if (payment) {
    const id = recordId(payment, 'tx');
    changes.push({
      collection: 'transactions', id, updatedAt: t, data: { ...payment, id, saleId },
    });
  }

  const out = await writeRecords(req, changes);
  await audit.log({
    shopId: req.shopId, userId: req.user.id, action: 'sale.created',
    targetType: 'sale', targetId: saleId, detail: { items: items.length },
  });
  res.status(201).json({ saleId, ...out });
});

module.exports = router;
module.exports.ROUTES = ROUTES;
