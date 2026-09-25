'use strict';
/**
 * خبرها — «هر اتفاقی که در برنامه می‌افتد، به سرور برود و سرور، وقتی
 * برنامه بسته هم هست، آن را برساند».
 *
 * ── چه چیزی نبود ───────────────────────────────────────────────────
 * هشدارهای برنامه (کالای تمام‌شده، قرض‌دار، پشتیبان) همه از روی دفترِ
 * محلی و روی همان گوشی حساب می‌شدند. یعنی صاحب دکانی که خانه بود
 * نمی‌دانست شاگردش چه فروخته یا چه کالایی تمام شده — تا وقتی خودش
 * برنامه را باز کند و همگام‌سازی شود.
 *
 * حالا خبرها روی سرور می‌نشینند و هر عضو می‌تواند بخواندشان.
 * ──────────────────────────────────────────────────────────────────
 *
 * ## چرا «رویداد» و نه «هشدار»
 *
 * هشدار چیزی است که از روی وضعیتِ فعلی حساب می‌شود («این کالا تمام
 * است») و هر بار از نو ساخته می‌شود. رویداد چیزی است که **اتفاق
 * افتاده** («کریم ساعت ۳ این را فروخت») و تاریخ دارد. صاحب دکان دومی
 * را می‌خواهد: می‌خواهد بداند در نبودش چه گذشت.
 *
 * ## دو دفتر، یک منطق — از ۱۴۰۵/۰۶/۳۱
 *
 * ⛔ **تا امروز این فقط برای دکان بود.** بخشِ پمپ هیچ دفترِ خبری روی
 * ابر نداشت: «اضافه برد» و «کم مانده» فقط روی سرورِ خانگی می‌نشستند و
 * گوشیِ کارمند هر پانزده دقیقه از **همان شبکه** می‌پرسید. صاحبِ پمپی
 * که بیرون بود هیچ‌وقت خبر نمی‌گرفت.
 *
 * پس همان کاری که `subscriptions.js` و `vip-codes.js` کردند: یک منطق،
 * دو جدول. نامِ جدولِ هر بخش از `lib/tenancy.js` می‌آید و کپی‌ای در
 * کار نیست — وگرنه روزی یکی اصلاح می‌شود و دیگری نه.
 */
const { query, one, many, newId, now } = require('../db');
const push = require('./push');
const tenancy = require('./tenancy');
const { badRequest } = require('../middleware/errors');

/** نوع‌هایی که می‌شناسیم. هر چیز دیگری رد می‌شود. */
const KINDS = ['sale', 'stock_out', 'low_stock', 'expense', 'debt', 'note'];

/**
 * ⛔ **کدام خبر ارزشِ بیدار کردنِ گوشی را دارد.**
 *
 * خواستهٔ صاحب مخزن: «هر اتفاقی که در برنامه بیفتد — کم‌بودی یا هر چه —
 * به سرور برود و سرور، وقتی برنامه‌ها بسته هم هستند، پیام را برایشان
 * بفرستد، اگر کاربر نت داشت.»
 *
 * ولی **همه‌ی** خبرها را پوش کردن یعنی هر فروشِ شاگرد یک زنگ روی گوشیِ
 * صاحبِ دکان. روزِ اول جالب است، روزِ دوم اعلان‌ها را خاموش می‌کند — و
 * آن‌وقت خبرِ مهم هم دیگر نمی‌رسد. پس فقط این سه:
 *
 *   • `stock_out` کالا (یا تیلِ مخزن) تمام شد
 *   • `low_stock` دارد تمام می‌شود — هنوز وقت هست سفارش داد
 *   • `debt`      قرض از حد گذشت — هرچه دیرتر، سخت‌تر وصول می‌شود
 *
 * همان سه‌تایی که `Watchman`ِ برنامهٔ دکان و `StationSnapshot.Alerts`ِ
 * برنامهٔ پمپ هر دو می‌پایند. فروش و مصرف و یادداشت در فهرست می‌نشینند
 * و با باز شدنِ برنامه دیده می‌شوند.
 */
const PUSH_KINDS = ['stock_out', 'low_stock', 'debt'];

const MAX_BATCH = 50;
const MAX_TEXT = 300;

function clean(text, max = MAX_TEXT) {
  return String(text == null ? '' : text).trim().slice(0, max);
}

/**
 * بستنِ همین منطق روی دفترِ خبرِ یک بخش.
 * @param {object} T یکی از ثابت‌های lib/tenancy.js
 */
function build(T) {
  const TBL = T.eventsTable;          // shop_events | station_events
  const READS = T.eventReadsTable;    // shop_event_reads | station_event_reads
  const KEY = T.tenantKey;            // shop_id | station_id
  const DEV = T.eventDeviceColumn;    // '' | device_uid

  function shape(r) {
    return {
      id: r.id,
      kind: r.kind,
      title: r.title,
      body: r.body,
      data: r.data,
      userId: r.user_id,
      userName: r.user_name,
      deviceUid: r.device_uid || '',
      at: Number(r.created_at),
    };
  }

  /**
   * ثبت چند خبر از یک دستگاه.
   *
   * `clientId` اختیاری است ولی مهم: گوشی‌ای که آفلاین بوده و صفِ خبرها را
   * یک‌جا می‌فرستد، اگر پاسخ را نگیرد دوباره می‌فرستد. با این شناسه، ردیف
   * تکراری ساخته نمی‌شود و صاحب دکان یک فروش را دو بار نمی‌بیند.
   */
  async function record(ctx, items) {
    const tenantId = ctx.tenantId ?? ctx.shopId ?? ctx.stationId;
    const { userId = '', userName = '', deviceUid = '' } = ctx;
    if (!tenantId) throw badRequest(T.notFoundMessage, T.notFoundCode);
    if (!Array.isArray(items)) throw badRequest('قالب خبرها درست نیست', 'bad_events');
    if (items.length > MAX_BATCH) {
      throw badRequest(`حداکثر ${MAX_BATCH} خبر در هر درخواست`, 'batch_too_large');
    }

    const t = now();
    const saved = [];
    for (const raw of items) {
      const kind = clean(raw?.kind, 24);
      if (!KINDS.includes(kind)) continue;         // ناشناخته بی‌سروصدا رد می‌شود

      const cols = ['id', KEY, 'user_id', 'user_name', 'kind', 'title', 'body', 'data',
        'client_id', 'created_at'];
      const args = [
        newId('evt'), tenantId, userId, clean(userName, 80), kind,
        clean(raw?.title, 120), clean(raw?.body),
        JSON.stringify(raw?.data && typeof raw.data === 'object' ? raw.data : {}),
        clean(raw?.clientId, 80),
        Number(raw?.at) || t,
      ];
      if (DEV) { cols.push(DEV); args.push(clean(deviceUid, 120)); }
      const holes = cols.map((c, i) => (c === 'data' ? `$${i + 1}::jsonb` : `$${i + 1}`));

      const row = await one(
        `INSERT INTO ${TBL} (${cols.join(', ')})
         VALUES (${holes.join(',')})
         ON CONFLICT (${KEY}, client_id) WHERE client_id <> '' DO NOTHING
         RETURNING *`,
        args
      );
      if (row) saved.push(shape(row));
    }

    await notify(tenantId, userId, saved);
    return { saved: saved.length, events: saved };
  }

  /**
   * بیدار کردنِ گوشی‌های همان حساب.
   *
   * ⚠️ **یک پیام برای یک دسته، نه یکی برای هر خبر.** گوشی‌ای که آفلاین
   * بوده صفِ بیست خبر را یک‌جا می‌فرستد؛ بی این، بیست زنگ پشتِ سرِ هم
   * می‌خورد.
   *
   * ⚠️ **کسی که خودش این کار را کرده خبر نمی‌گیرد** (`exceptUserId`).
   * فروشنده‌ای که کالا را تمام کرده، لازم نیست روی گوشیِ خودش زنگ
   * بشنود.
   *
   * ⚠️ **هیچ‌وقت استثنا بیرون نمی‌دهد.** ثبتِ خبر کارِ اصلی است و پوش
   * رفاه؛ نرسیدنِ زنگ نباید باعث شود خبر اصلاً ثبت نشود.
   */
  async function notify(tenantId, userId, saved) {
    const worthy = saved.filter(e => PUSH_KINDS.includes(e.kind));
    if (!worthy.length) return { sent: 0, skipped: 'nothing_worthy' };
    /*
     *  ⛔ باتِ تلگرام — فقط پمپ، و با **همین** فهرستِ ارزشمند. قاعدهٔ جدا
     *  نیست: همان سه نوعی که پوش می‌شوند. خودش هیچ‌وقت استثنا بیرون
     *  نمی‌دهد، و پوش را هم منتظر نمی‌گذارد (فقط در صف می‌نشاند).
     */
    if (T.app === 'pump') await require('./telegram').notifyStation(tenantId, worthy);
    try {
      const first = worthy[0];
      const more = worthy.length - 1;
      return await push.sendTo(
        { [T.app === 'pump' ? 'stationId' : 'shopId']: tenantId, app: T.app, exceptUserId: userId },
        {
          title: T.eventTitles[first.kind] || `خبرِ ${T.label}`,
          body: more > 0
            ? `${first.title || first.body || ''} و ${more} خبرِ دیگر`
            : (first.title || first.body || ''),
          channel: 'events',
          data: { type: 'event', kind: first.kind, count: String(worthy.length) },
        }
      );
    } catch (err) {
      console.error('[events:push]', err.message);
      return { sent: 0, error: err.message };
    }
  }

  /**
   * خبرها، تازه‌ترین اول.
   *
   * @param since فقط خبرهای بعد از این زمان — تا گوشی هر بار کل تاریخ را
   *   دانلود نکند.
   */
  async function list(tenantId, { since = 0, limit = 50 } = {}) {
    const cap = Math.min(Math.max(1, Number(limit) || 50), 200);
    const from = Math.max(0, Number(since) || 0);
    const rows = await many(
      `SELECT * FROM ${TBL}
        WHERE ${KEY}=$1 AND created_at > $2
        ORDER BY created_at DESC LIMIT $3`,
      [tenantId, from, cap]
    );
    return rows.map(shape);
  }

  /** تا کجا خوانده‌ام — نقطه‌ی قرمزِ زنگ از همین می‌آید. */
  async function seenAt(tenantId, userId) {
    const r = await one(
      `SELECT seen_at FROM ${READS} WHERE ${KEY}=$1 AND user_id=$2`,
      [tenantId, userId]
    );
    return r ? Number(r.seen_at) : 0;
  }

  async function markSeen(tenantId, userId, at = now()) {
    await query(
      `INSERT INTO ${READS} (${KEY}, user_id, seen_at) VALUES ($1,$2,$3)
       ON CONFLICT (${KEY}, user_id)
       DO UPDATE SET seen_at = GREATEST(${READS}.seen_at, excluded.seen_at)`,
      [tenantId, userId, Number(at) || now()]
    );
  }

  async function unreadCount(tenantId, userId) {
    const at = await seenAt(tenantId, userId);
    const r = await one(
      `SELECT COUNT(*)::int AS n FROM ${TBL}
        WHERE ${KEY}=$1 AND created_at > $2 AND user_id <> $3`,
      [tenantId, at, userId]
    );
    return r ? r.n : 0;
  }

  return { record, list, seenAt, markSeen, unreadCount, notify, shape, tenancy: T };
}

const shop = build(tenancy.SHOP);
const pump = build(tenancy.PUMP);

module.exports = {
  //  بخشِ دکان، همان‌جا که همیشه بود — کدِ قدیمی دست‌نخورده کار می‌کند
  ...shop,
  build,
  shop,
  pump,
  forApp: (app) => (app === 'pump' ? pump : shop),
  KINDS, PUSH_KINDS,
};
