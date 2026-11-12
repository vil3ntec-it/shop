'use strict';
/**
 * سمتِ دکانِ ویترین: تنظیم، اعلامِ حضور (۳۱.۱/۳۱.۲) و کالاهای «در ویترین» — از `lib/storefront.js` جدا شد تا
 * زیرِ سقفِ اندازه بماند؛ رفتار همان. وابستگی‌های همان پرونده (نمای عمومی، سنجنده‌ها، منبعِ کالا، نشانیِ
 * عمومی) تزریق می‌شوند.
 */
const { one, many, now, tx } = require('../db');
const { badRequest, notFound } = require('../middleware/errors');

module.exports = ({
  profile, statusOf, slugOf, productView, isActive, specsOf, asJson, str, obj, int, bool, text, spid, sfc,
  PRODUCT_ID_RE, PAGE, MAX_PAGE, MAX_PRICE_MINOR, MAX_QTY_MILLI, MAX_FEE_MINOR, MAX_DECLARE_MS,
}) => {
function settingsView(sf, status = null) {
  if (!sf) return null;
  const { categories: _c, ...pub } = profile(sf, [], status);
  return {
    ...pub,
    enabled: sf.enabled === true,
    notice: sf.notice || '',
    timezone: sf.timezone || 'Asia/Kabul',
    weeklyHours: asJson(sf.weekly_hours, {}),
    closedDates: asJson(sf.closed_dates, []),
    presenceMode: sf.presence_mode || 'auto',
    declaredUntil: sf.declared_until === null || sf.declared_until === undefined ? null : Number(sf.declared_until),
    //  «پرداخت پس از پذیرش» (مهاجرتِ ۲۱۰، `lib/order-flow.js`) — تنظیمِ خصوصیِ دکان، نه نمای عمومی
    payAfterAccept: sf.pay_after_accept === true,
    //  WEB-09 (مهاجرتِ ۲۴۰): «کالای ناموجود را در ویترین پنهان کن» — پیش‌فرض خاموش؛ پالایه فقط در سرور (`storefront-filters.hiding`)
    hideOutOfStock: sf.hide_out_of_stock === true,
    //  مهاجرتِ ۲۱۵ — خامِ خانه‌های تنظیم برای برنامهٔ صاحب (عمومی فقط `place`)
    pickupAddress: sf.pickup_address || '',
    pickupPin: Number.isFinite(sf.pickup_lat) && Number.isFinite(sf.pickup_lng) ? { lat: sf.pickup_lat, lng: sf.pickup_lng } : null,
    updatedAt: Number(sf.updated_at),
  };
}

async function settingsOf(shopId) {
  const sf = await one('SELECT * FROM storefronts WHERE shop_id = $1', [shopId]);
  return sf ? settingsView(sf, await statusOf(sf)) : null;
}

function timezoneOf(v) {
  const tz = str(v);
  try {
    if (!tz || tz.length > 64) throw new Error('x');
    new Intl.DateTimeFormat('en-US', { timeZone: tz });
    return tz;
  } catch {
    throw badRequest('منطقهٔ زمانی معتبر نیست', 'bad_timezone');
  }
}

/** {"0":[[480,1200]], …} — بازه‌ها به دقیقه از نیمه‌شب، بی هم‌پوشانی؛ شبِ پس از ۲۴ در روزِ بعد. */
function weeklyHoursOf(v) {
  const h = obj(v);
  const out = {};
  for (const [day, list] of Object.entries(h)) {
    if (!/^[0-6]$/.test(day) || !Array.isArray(list) || list.length > 4) throw badRequest('ساعتِ کاری معتبر نیست', 'bad_hours');
    const ranges = list.map((r) => {
      if (!Array.isArray(r) || r.length !== 2) throw badRequest('ساعتِ کاری معتبر نیست', 'bad_hours');
      const o = int(r[0], 'آغاز', { min: 0, max: 1439 });
      const c = int(r[1], 'پایان', { min: 1, max: 1440 });
      if (c <= o) throw badRequest('پایانِ بازه باید پس از آغاز باشد', 'bad_hours');
      return [o, c];
    }).sort((a, b) => a[0] - b[0]);
    for (let i = 1; i < ranges.length; i++) {
      if (ranges[i][0] < ranges[i - 1][1]) throw badRequest('بازه‌های یک روز روی هم افتاده‌اند', 'bad_hours');
    }
    out[day] = ranges;
  }
  return out;
}

function closedDatesOf(v) {
  if (!Array.isArray(v) || v.length > 60) throw badRequest('فهرستِ تعطیلی معتبر نیست', 'bad_closed_dates');
  const out = [...new Set(v.map((d) => {
    const s = str(d);
    if (!/^\d{4}-\d{2}-\d{2}$/.test(s) || Number.isNaN(Date.parse(`${s}T00:00:00Z`))) {
      throw badRequest('تاریخِ تعطیلی باید به شکلِ YYYY-MM-DD باشد', 'bad_closed_dates');
    }
    return s;
  }))].sort();
  return out;
}

async function recordEvent(c, shopId, e) {
  const r = await c.query(
    `INSERT INTO storefront_presence_events (id, shop_id, event_id, actor_id, kind, status, declared_until,
       store_timezone, detail, source_device_id, client_event_at, server_received_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9::jsonb,$10,$11,$12)
     ON CONFLICT (shop_id, event_id) DO NOTHING RETURNING id`,
    [require('./ulid').ulid(e.at), shopId, e.eventId, e.actorId || '', e.kind, e.status || '', e.declaredUntil ?? null,
      e.timezone || '', JSON.stringify(e.detail || {}), e.deviceId || '', e.clientEventAt ?? null, e.at]
  );
  return r.rows.length > 0;
}

/** پینِ دکان: null (برداشتن) یا {lat, lng} ِ درجه — هر دو با هم */
function pinOf(v) {
  if (v === null) return null;
  const p = obj(v);
  const lat = Number(p.lat); const lng = Number(p.lng);
  if (typeof p.lat !== 'number' || typeof p.lng !== 'number' || !(Math.abs(lat) <= 90) || !(Math.abs(lng) <= 180)) {
    throw badRequest('پینِ دکان معتبر نیست', 'bad_pin');
  }
  return { lat: Math.round(lat * 1e6) / 1e6, lng: Math.round(lng * 1e6) / 1e6 };
}

/** تنظیمِ ویترین — فقط فیلدهای آمده عوض می‌شوند. */
async function saveSettings(shopId, body, userId) {
  const b = obj(body);
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  const saved = await tx(async (c) => {
    await c.query('SELECT pg_advisory_xact_lock(hashtext($1))', ['storefront-slugs']);
    const cur = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0];
    const v = {
      slug: cur?.slug || '', enabled: cur?.enabled === true, title: cur?.title || '', tagline: cur?.tagline || '',
      contact_phone: cur?.contact_phone || '', accent: cur?.accent || '', notice: cur?.notice || '',
      timezone: cur?.timezone || 'Asia/Kabul',
      weekly_hours: cur ? asJson(cur.weekly_hours, {}) : {},
      closed_dates: cur ? asJson(cur.closed_dates, []) : [],
      accept_delivery: cur ? cur.accept_delivery === true : true,
      accept_pickup: cur ? cur.accept_pickup === true : true,
      delivery_fee_minor: cur ? Number(cur.delivery_fee_minor) : 0,
      min_order_minor: cur ? Number(cur.min_order_minor) : 0,
    };
    if (has('slug') || !cur) v.slug = slugOf(b.slug);
    if (has('enabled')) v.enabled = bool(b.enabled, 'روشن بودن');
    if (has('title')) v.title = text(b.title, 60);
    if (has('tagline')) v.tagline = text(b.tagline, 120);
    if (has('contactPhone')) v.contact_phone = require('./validate').phone(b.contactPhone) || '';
    if (has('notice')) v.notice = text(b.notice, 120);
    if (has('accent')) {
      const a = str(b.accent).toLowerCase();
      if (a && !/^#[0-9a-f]{6}$/.test(a)) throw badRequest('رنگ باید به شکلِ ‎#rrggbb‎ باشد', 'bad_accent');
      v.accent = a;
    }
    if (has('timezone')) v.timezone = timezoneOf(b.timezone);
    if (has('weeklyHours')) v.weekly_hours = weeklyHoursOf(b.weeklyHours);
    if (has('closedDates')) v.closed_dates = closedDatesOf(b.closedDates);
    if (has('acceptDelivery')) v.accept_delivery = bool(b.acceptDelivery, 'ارسال');
    if (has('acceptPickup')) v.accept_pickup = bool(b.acceptPickup, 'دریافت از دکان');
    if (has('deliveryFeeMinor')) v.delivery_fee_minor = int(b.deliveryFeeMinor, 'هزینهٔ ارسال', { max: MAX_FEE_MINOR });
    if (has('minOrderMinor')) v.min_order_minor = int(b.minOrderMinor, 'حداقلِ سفارش', { max: MAX_PRICE_MINOR });
    if (!v.accept_delivery && !v.accept_pickup) throw badRequest('دست‌کم یکی از ارسال یا دریافت از دکان', 'bad_fulfillment');
    if (v.enabled && !v.title) throw badRequest('برای روشن کردن، نامِ ویترین لازم است', 'title_required');

    const at = now();
    await spid.claimSlug(c, shopId, cur && cur.slug, v.slug, at);   //  slug_taken؛ S3: نشانیِ پیشین

    await c.query(
      `INSERT INTO storefronts (shop_id, slug, enabled, title, tagline, contact_phone, accent, notice, timezone,
         weekly_hours, closed_dates, accept_delivery, accept_pickup, delivery_fee_minor, min_order_minor,
         updated_by, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10::jsonb,$11::jsonb,$12,$13,$14,$15,$16,$17,$17)
       ON CONFLICT (shop_id) DO UPDATE SET slug = EXCLUDED.slug, enabled = EXCLUDED.enabled, title = EXCLUDED.title,
         tagline = EXCLUDED.tagline, contact_phone = EXCLUDED.contact_phone, accent = EXCLUDED.accent,
         notice = EXCLUDED.notice, timezone = EXCLUDED.timezone, weekly_hours = EXCLUDED.weekly_hours,
         closed_dates = EXCLUDED.closed_dates, accept_delivery = EXCLUDED.accept_delivery,
         accept_pickup = EXCLUDED.accept_pickup, delivery_fee_minor = EXCLUDED.delivery_fee_minor,
         min_order_minor = EXCLUDED.min_order_minor, updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at`,
      [shopId, v.slug, v.enabled, v.title, v.tagline, v.contact_phone, v.accent, v.notice, v.timezone,
        JSON.stringify(v.weekly_hours), JSON.stringify(v.closed_dates), v.accept_delivery, v.accept_pickup,
        v.delivery_fee_minor, v.min_order_minor, userId || '', at]
    ).then(() => require('./market-changes').record(c, shopId, { entity: 'shop', kind: cur ? 'update' : 'create', state: v, at }));   //  S6: همان تراکنش
    //  مهاجرتِ ۲۱۵: نشانی و پینِ دکان (برای مشتریِ «دریافت از دکان»)، مثلِ ۲۱۰ جدا از `v`
    if (has('pickupAddress')) await c.query('UPDATE storefronts SET pickup_address = $2 WHERE shop_id = $1', [shopId, text(b.pickupAddress, 200)]);
    if (has('pickupPin')) {
      const p = pinOf(b.pickupPin);
      await c.query('UPDATE storefronts SET pickup_lat = $2, pickup_lng = $3 WHERE shop_id = $1', [shopId, p ? p.lat : null, p ? p.lng : null]);
    }
    //  مهاجرتِ ۲۴۰ (WEB-09): مثلِ ۲۱۰ جدا از `v`؛ نیامدنِ کلید (برنامهٔ پیشین) ⇒ همان مقدارِ پیشین
    if (has('hideOutOfStock')) {
      await c.query('UPDATE storefronts SET hide_out_of_stock = $2 WHERE shop_id = $1', [shopId, bool(b.hideOutOfStock, 'پنهان کردنِ کالای ناموجود')]);
    }
    //  مهاجرتِ ۲۱۰: جدا از `v` تا به دفترِ تغییرهای بازار (S6) نرود
    if (has('payAfterAccept')) {
      await c.query('UPDATE storefronts SET pay_after_accept = $2 WHERE shop_id = $1', [shopId, bool(b.payAfterAccept, 'پرداخت پس از پذیرش')]);
    } else if (!cur) {
      //  مهاجرتِ ۲۱۱: نخستین ساختِ ویترین ⇒ پیش‌فرضِ همین دکان (تازه روشن، موجود خاموش)؛ بعد هرگز
      await c.query(
        `UPDATE storefronts SET pay_after_accept = COALESCE((SELECT pay_after_accept_default FROM shops WHERE id = $1), false)
          WHERE shop_id = $1`, [shopId]
      );
    }
    //  ۳۱.۲ بندِ ۱: تغییرِ ساعتِ کاری هم رویداد دارد
    if (has('timezone') || has('weeklyHours') || has('closedDates')) {
      await recordEvent(c, shopId, {
        eventId: `hours-${require('./ulid').ulid(at)}`, actorId: userId, kind: 'hours', timezone: v.timezone,
        detail: { weeklyHours: v.weekly_hours, closedDates: v.closed_dates }, at,
      });
    }
    return (await c.query('SELECT * FROM storefronts WHERE shop_id = $1', [shopId])).rows[0];
  });
  return settingsView(saved, await statusOf(saved));
}

/**
 * ۳۱.۱/۳۱.۲ — اعلامِ مالک: «آنلاین تا ساعتِ…»، «آنلاین»، «آفلاین»، «بسته»
 * یا «طبقِ ساعتِ کاری». `eventId` از برنامه می‌آید تا صفِ آفلاین دوباره‌فرستادن
 * را بی‌اثر کند؛ رویدادِ کهنه‌تر (زمانِ دستگاه) در تاریخچه می‌نشیند ولی
 * وضعیتِ تازه‌تر را برنمی‌گرداند. «ثبت شد» فقط پس از همین پاسخ.
 */
async function declarePresence(shopId, body, { userId = '', deviceId = '' } = {}) {
  const b = obj(body);
  const eventId = str(b.eventId);
  if (!/^[A-Za-z0-9_.:-]{8,80}$/.test(eventId)) throw badRequest('شناسهٔ رویداد لازم است', 'bad_event_id');
  const mode = str(b.mode);
  if (!['auto', 'online', 'online_until', 'offline', 'closed'].includes(mode)) throw badRequest('وضعیت معتبر نیست', 'bad_mode');
  const at = now();
  let declaredUntil = null;
  if (mode === 'online_until') {
    declaredUntil = int(b.declaredUntil, 'زمانِ پایان', { min: at + 60_000, max: at + MAX_DECLARE_MS });
  }
  const clientEventAt = b.clientEventAt === undefined || b.clientEventAt === null
    ? null : int(b.clientEventAt, 'زمانِ دستگاه', { min: 0, max: 4102444800000 });
  //  PRES-08: زمانِ سرورِ آخرین وضعی که دستگاه هنگامِ تصمیم دید (`settings.updatedAt`)؛ نبود ⇒ برنامهٔ پیشین
  const seenAt = b.seenAt === undefined || b.seenAt === null ? null : int(b.seenAt, 'وضعِ دیده‌شده', { min: 0, max: at });
  const device = text(b.deviceId, 80) || deviceId;

  const row = await tx(async (c) => {
    const sf = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1 FOR UPDATE', [shopId])).rows[0];
    if (!sf) throw notFound('هنوز ویترینی ساخته نشده است', 'storefront_missing');
    //  PRES-08: ترتیب با زمانِ دستگاه ولی هرگز دیرتر از رسیدن به سرور — ساعتِ جلوافتاده (فردا) وگرنه
    //  تا فردا هر اعلامِ دیگر را «کهنه» می‌کرد. زمانِ خام برای ممیزی دست‌نخورده نوشته می‌شود.
    const newer = clientEventAt === null ? null : (await c.query(
      `SELECT 1 FROM storefront_presence_events WHERE shop_id = $1 AND kind = 'presence'
          AND (detail->>'applied') = 'true'
          AND LEAST(COALESCE(client_event_at, server_received_at), server_received_at) > $2 LIMIT 1`,
      [shopId, Math.min(clientEventAt, at)]
    )).rows[0];
    //  PRES-08، ساعتِ جلوافتاده درونِ صفِ آفلاین: اعلامی که بی خبر از اعلامِ نشستهٔ دستگاهِ دیگری (رسیده پس از وضعِ
    //  دیده‌شده) ساخته شد آن را برنمی‌گرداند — مستقل از ساعتِ دستگاه؛ اعلام‌های صفِ همان دستگاه همدیگر را کهنه نمی‌کنند
    const unseen = newer || seenAt === null ? null : (await c.query(
      `SELECT 1 FROM storefront_presence_events WHERE shop_id = $1 AND kind = 'presence' AND (detail->>'applied') = 'true'
          AND server_received_at > $2 AND COALESCE(source_device_id, '') <> $3 LIMIT 1`,
      [shopId, seenAt, device || '']
    )).rows[0];
    const applied = !newer && !unseen;
    const inserted = await recordEvent(c, shopId, {
      eventId, actorId: userId, kind: 'presence', status: mode, declaredUntil, timezone: sf.timezone,
      detail: { applied }, deviceId: device, clientEventAt, at,
    });
    if (inserted && applied) {
      await c.query(
        'UPDATE storefronts SET presence_mode = $2, declared_until = $3, updated_by = $4, updated_at = $5 WHERE shop_id = $1',
        [shopId, mode, declaredUntil, userId || '', at]
      ).then(() => require('./market-changes').record(c, shopId, { entity: 'presence', kind: 'update', state: { mode, declaredUntil }, at }));   //  S6
    }
    const sfNow = (await c.query('SELECT * FROM storefronts WHERE shop_id = $1', [shopId])).rows[0];
    return { sf: sfNow, replayed: !inserted, applied: inserted ? applied : null };
  });
  return { status: await statusOf(row.sf), replayed: row.replayed, applied: row.applied, serverReceivedAt: at };
}

/** همهٔ کالاهای دفتر با نشانِ ویترین — برای صفحهٔ «در ویترین» ِ برنامه. */
async function staffProducts(shopId, { cursor, limit } = {}) {
  const lim = Math.min(MAX_PAGE, Math.max(1, Number.parseInt(limit, 10) || PAGE));
  const params = [shopId];
  const src = await sfc.ledgerSource(shopId, { strict: false });   //  S1: همان منبعِ ویترین
  let where = src.where;
  if (cursor) {
    if (!PRODUCT_ID_RE.test(String(cursor))) throw badRequest('نشانگرِ صفحه معتبر نیست', 'bad_cursor');
    params.push(String(cursor));
    where += ` AND r.row_id > $${params.length}`;
  }
  params.push(lim + 1);
  const rows = await many(
    `SELECT r.row_id AS id, r.data, sp.published, sp.online_price_minor, sp.max_qty_milli, sp.description, sp.specs
       FROM ${src.from}
       LEFT JOIN storefront_products sp ON sp.shop_id = r.account_id AND sp.product_id = r.row_id
      WHERE ${where} ORDER BY r.row_id LIMIT $${params.length}`,
    params
  );
  const more = rows.length > lim;
  const page = more ? rows.slice(0, lim) : rows;
  return {
    items: page.map((r) => ({
      ...productView({ ...r, online_price_minor: null }),
      published: r.published === true,
      onlinePriceMinor: r.online_price_minor === null || r.online_price_minor === undefined ? null : Number(r.online_price_minor),
      active: isActive(r.data),
      description: text(r.description, 2000),
      specs: specsOf(r.specs),
    })),
    nextCursor: more ? page[page.length - 1].id : null,
  };
}

/** گذاشتن یا برداشتنِ یک کالا از ویترین. */
async function setProduct(shopId, productId, body, userId) {
  const id = String(productId || '');
  if (!PRODUCT_ID_RE.test(id)) throw badRequest('شناسهٔ کالا معتبر نیست', 'bad_product');
  const b = obj(body);
  const published = bool(b.published, 'در ویترین');
  const nullable = (k, field, opts) => (b[k] === null || b[k] === undefined ? null : int(b[k], field, opts));
  const onlinePriceMinor = nullable('onlinePriceMinor', 'قیمتِ آنلاین', { min: 0, max: MAX_PRICE_MINOR });
  const maxQtyMilli = nullable('maxQtyMilli', 'حداکثرِ هر سفارش', { min: 1, max: MAX_QTY_MILLI });
  //  WEB-08: توضیح و مشخصاتِ عمومی — فقط اگر آمده باشند (برنامه‌های قدیمی‌تر نمی‌فرستند)
  const has = (k) => Object.prototype.hasOwnProperty.call(b, k);
  const description = has('description') ? text(b.description, 2000) : null;
  let specs = null;
  if (has('specs')) {
    if (!Array.isArray(b.specs) || b.specs.length > 20) throw badRequest('حداکثر ۲۰ مشخصه', 'bad_specs');
    specs = specsOf(b.specs);
  }
  const src = await sfc.ledgerSource(shopId);
  const exists = await one(`SELECT 1 FROM ${src.from} WHERE ${src.where} AND r.row_id = $2`, [shopId, id]);
  if (!exists) throw notFound('این کالا در دفترِ دکان نیست', 'product_not_found');
  const at = now();
  const row = await require('./market-changes').productTx(shopId, id, at, async (c) => (await c.query(   //  S6: همان تراکنش
    `INSERT INTO storefront_products (shop_id, product_id, published, online_price_minor, max_qty_milli, updated_by, updated_at,
       description, specs, published_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,COALESCE($8,''),COALESCE($9::jsonb,'[]'::jsonb),CASE WHEN $3 THEN $7::bigint END)
     ON CONFLICT (shop_id, product_id) DO UPDATE SET published = EXCLUDED.published,
       online_price_minor = EXCLUDED.online_price_minor, max_qty_milli = EXCLUDED.max_qty_milli,
       updated_by = EXCLUDED.updated_by, updated_at = EXCLUDED.updated_at,
       description = COALESCE($8, storefront_products.description),
       specs = COALESCE($9::jsonb, storefront_products.specs),
       published_at = COALESCE(storefront_products.published_at, EXCLUDED.published_at)
     RETURNING product_id, description, specs`,
    [shopId, id, published, onlinePriceMinor, maxQtyMilli, userId || '', at, description,
      specs === null ? null : JSON.stringify(specs)]
  )).rows[0], (r) => ({ published, onlinePriceMinor, maxQtyMilli, description: r.description, specs: specsOf(r.specs) }));
  return {
    productId: id, published, onlinePriceMinor, maxQtyMilli,
    description: text(row.description, 2000), specs: specsOf(row.specs),
  };
}

  return { settingsOf, saveSettings, declarePresence, staffProducts, setProduct };
};
