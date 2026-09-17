'use strict';
/**
 * خبرهای دکان — «هر پیامی که در برنامه‌ی شاگرد می‌آید، برای صاحب دکان هم بیاید».
 *
 * ── چه چیزی نبود ───────────────────────────────────────────────────
 * هشدارهای برنامه (کالای تمام‌شده، قرض‌دار، پشتیبان) همه از روی دفترِ
 * محلی و روی همان گوشی حساب می‌شدند. یعنی صاحب دکانی که خانه بود
 * نمی‌دانست شاگردش چه فروخته یا چه کالایی تمام شده — تا وقتی خودش
 * برنامه را باز کند و همگام‌سازی شود.
 *
 * حالا خبرها روی سرور می‌نشینند و هر عضو دکان می‌تواند بخواندشان.
 * ──────────────────────────────────────────────────────────────────
 *
 * ## چرا «رویداد» و نه «هشدار»
 *
 * هشدار چیزی است که از روی وضعیتِ فعلی حساب می‌شود («این کالا تمام
 * است») و هر بار از نو ساخته می‌شود. رویداد چیزی است که **اتفاق
 * افتاده** («کریم ساعت ۳ این را فروخت») و تاریخ دارد. صاحب دکان دومی
 * را می‌خواهد: می‌خواهد بداند در نبودش چه گذشت.
 */
const { query, one, many, newId, now } = require('../db');
const push = require('./push');
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
 *   • `stock_out` کالا تمام شد — مشتری آمده و دست خالی می‌رود
 *   • `low_stock` دارد تمام می‌شود — هنوز وقت هست سفارش داد
 *   • `debt`      قرض از حد گذشت — هرچه دیرتر، سخت‌تر وصول می‌شود
 *
 * همان سه‌تایی که `Watchman`ِ برنامه هم روی گوشی می‌پاید. فروش و مصرف
 * و یادداشت در فهرست می‌نشینند و با باز شدنِ برنامه دیده می‌شوند.
 */
const PUSH_KINDS = ['stock_out', 'low_stock', 'debt'];

const PUSH_TITLE = {
  stock_out: 'کالا تمام شد',
  low_stock: 'کالا رو به اتمام',
  debt: 'قرضِ از حد گذشته',
};

const MAX_BATCH = 50;
const MAX_TEXT = 300;

function clean(text, max = MAX_TEXT) {
  return String(text == null ? '' : text).trim().slice(0, max);
}

/**
 * ثبت چند خبر از یک دستگاه.
 *
 * `clientId` اختیاری است ولی مهم: گوشی‌ای که آفلاین بوده و صفِ خبرها را
 * یک‌جا می‌فرستد، اگر پاسخ را نگیرد دوباره می‌فرستد. با این شناسه، ردیف
 * تکراری ساخته نمی‌شود و صاحب دکان یک فروش را دو بار نمی‌بیند.
 */
async function record(ctx, items) {
  const { shopId, userId = '', userName = '' } = ctx;
  if (!Array.isArray(items)) throw badRequest('قالب خبرها درست نیست', 'bad_events');
  if (items.length > MAX_BATCH) {
    throw badRequest(`حداکثر ${MAX_BATCH} خبر در هر درخواست`, 'batch_too_large');
  }

  const t = now();
  const saved = [];
  for (const raw of items) {
    const kind = clean(raw?.kind, 24);
    if (!KINDS.includes(kind)) continue;         // ناشناخته بی‌سروصدا رد می‌شود

    const row = await one(
      `INSERT INTO shop_events
         (id, shop_id, user_id, user_name, kind, title, body, data, client_id, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8::jsonb,$9,$10)
       ON CONFLICT (shop_id, client_id) WHERE client_id <> '' DO NOTHING
       RETURNING *`,
      [
        newId('evt'), shopId, userId, clean(userName, 80), kind,
        clean(raw?.title, 120), clean(raw?.body),
        JSON.stringify(raw?.data && typeof raw.data === 'object' ? raw.data : {}),
        clean(raw?.clientId, 80),
        Number(raw?.at) || t,
      ]
    );
    if (row) saved.push(shape(row));
  }

  await notify(shopId, userId, saved);
  return { saved: saved.length, events: saved };
}

/**
 * بیدار کردنِ گوشی‌های همان دکان.
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
async function notify(shopId, userId, saved) {
  const worthy = saved.filter(e => PUSH_KINDS.includes(e.kind));
  if (!worthy.length) return { sent: 0, skipped: 'nothing_worthy' };
  try {
    const first = worthy[0];
    const more = worthy.length - 1;
    return await push.sendTo(
      { shopId, app: 'shop', exceptUserId: userId },
      {
        title: PUSH_TITLE[first.kind] || 'خبرِ دکان',
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

function shape(r) {
  return {
    id: r.id,
    kind: r.kind,
    title: r.title,
    body: r.body,
    data: r.data,
    userId: r.user_id,
    userName: r.user_name,
    at: Number(r.created_at),
  };
}

/**
 * خبرهای دکان، تازه‌ترین اول.
 *
 * @param since فقط خبرهای بعد از این زمان — تا گوشی هر بار کل تاریخ را
 *   دانلود نکند.
 */
async function list(shopId, { since = 0, limit = 50 } = {}) {
  const cap = Math.min(Math.max(1, Number(limit) || 50), 200);
  const from = Math.max(0, Number(since) || 0);
  const rows = await many(
    `SELECT * FROM shop_events
      WHERE shop_id=$1 AND created_at > $2
      ORDER BY created_at DESC LIMIT $3`,
    [shopId, from, cap]
  );
  return rows.map(shape);
}

/** تا کجا خوانده‌ام — نقطه‌ی قرمزِ زنگ از همین می‌آید. */
async function seenAt(shopId, userId) {
  const r = await one(
    'SELECT seen_at FROM shop_event_reads WHERE shop_id=$1 AND user_id=$2',
    [shopId, userId]
  );
  return r ? Number(r.seen_at) : 0;
}

async function markSeen(shopId, userId, at = now()) {
  await query(
    `INSERT INTO shop_event_reads (shop_id, user_id, seen_at) VALUES ($1,$2,$3)
     ON CONFLICT (shop_id, user_id) DO UPDATE SET seen_at = GREATEST(shop_event_reads.seen_at, excluded.seen_at)`,
    [shopId, userId, Number(at) || now()]
  );
}

async function unreadCount(shopId, userId) {
  const at = await seenAt(shopId, userId);
  const r = await one(
    `SELECT COUNT(*)::int AS n FROM shop_events
      WHERE shop_id=$1 AND created_at > $2 AND user_id <> $3`,
    [shopId, at, userId]
  );
  return r ? r.n : 0;
}

module.exports = { record, list, seenAt, markSeen, unreadCount, notify, KINDS, PUSH_KINDS };
