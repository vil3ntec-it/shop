'use strict';
/**
 * چت پشتیبانی — یک رشته برای هر نفر.
 *
 * ── چرا این‌طور و نه «تیکت» ────────────────────────────────────────
 * تیکت یعنی کاربر باید موضوع بسازد، شماره بگیرد و پیگیری کند. کسی که
 * دکان دارد و وسط فروش گیر کرده این کار را نمی‌کند. پس یک رشته‌ی
 * همیشه‌باز: می‌نویسد، جواب می‌گیرد، تمام. مثل هر پیام‌رسانی که بلد است.
 *
 * ── مهمانِ بی‌حساب ─────────────────────────────────────────────────
 * رشته می‌تواند به شناسه‌ی دستگاه بسته باشد، نه فقط به حساب. کسی که
 * هنوز ثبت‌نام نکرده و همان‌جا گیر کرده باید بتواند بپرسد — وگرنه
 * پشتیبانی فقط به درد کسی می‌خورد که مشکلی ندارد.
 *
 * ── گوشیِ بسته ─────────────────────────────────────────────────────
 * هر پیامِ مدیر یک پوش هم می‌فرستد. اگر پوش تنظیم نشده باشد پیام گم
 * نمی‌شود؛ فقط زنگ نمی‌زند و دفعه‌ی بعد دیده می‌شود.
 */
const { query, one, many, newId, now } = require('../db');
const { notifyPanel } = require('./panel-live');
const push = require('./push');
const { ApiError, badRequest, notFound } = require('../middleware/errors');

const MAX_BODY = 4000;

/*
 *  ── رسانه: عکس، ویدیو، پیامِ صوتی — فقط در عبور ─────────────────
 *
 *  ⛔ سرور جای بایگانی نیست. هر رسانه همان لحظه که **گیرنده** (طرفِ
 *  مقابلِ فرستنده) کاملش را گرفت پاک می‌شود (`sendMedia`)، و اگر
 *  هرگز گرفته نشد `chat-relay.sweep()` می‌بردش. هر طرف نسخهٔ خودش را
 *  روی دستگاهِ خودش نگه می‌دارد.
 */
const MEDIA_KINDS = ['image', 'video', 'audio'];
const MAX_MEDIA = 25 * 1024 * 1024;
const MEDIA_PREVIEW = { image: '📷 عکس', video: '🎥 ویدیو', audio: '🎤 پیامِ صوتی' };

/** نوعِ رسانه از روی mime — فقط عکس، ویدیو و صدا. */
function mediaKindOf(mime) {
  const m = String(mime || '').toLowerCase().split(';')[0].trim();
  //  ⛔ SVG/XML «عکس» نیست — اسکریپت دارد
  if (/svg|xml|html/.test(m)) return null;
  if (/^image\/[a-z0-9.+-]+$/.test(m)) return 'image';
  if (/^video\/[a-z0-9.+-]+$/.test(m)) return 'video';
  if (/^audio\/[a-z0-9.+-]+$/.test(m)) return 'audio';
  return null;
}

/**
 * متنِ یک پیام، پیش از آن‌که جایی بنشیند.
 *
 * ⛔ **چرا این‌جا و نه با `v.text`**: `v.text` هر چیزی را که از سقف
 * بلندتر باشد **می‌بُرد** و همان بریده را برمی‌گرداند. برای یک نام یا
 * یک یادداشت اشکالی ندارد؛ برای پیامِ پشتیبانی یعنی کاربر جمله‌اش را
 * می‌نویسد، «فرستاده شد» می‌بیند، و هزار نویسهٔ آخرش — همان‌جا که
 * معمولاً توضیحِ اصلی است — بی این‌که کسی بفهمد رفته.
 *
 * نتیجه‌اش این بود که نگهبانِ `message_too_long` در `post` هیچ‌وقت
 * شلیک نمی‌کرد: هر چهار مسیر پیش از رسیدن به آن، متن را بریده بودند.
 */
function cleanBody(raw, { field = 'پیام' } = {}) {
  const text = String(raw == null ? '' : raw).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '').trim();
  if (!text) throw badRequest(`${field} خالی است`, 'empty_message');
  if (text.length > MAX_BODY) {
    throw badRequest(`${field} خیلی بلند است — حداکثر ${MAX_BODY} نویسه`, 'message_too_long');
  }
  return text;
}

function shapeThread(r) {
  return {
    id: r.id,
    app: r.app,
    userId: r.user_id || '',
    shopId: r.shop_id || '',
    stationId: r.station_id || '',
    deviceUid: r.device_uid || '',
    subject: r.subject || '',
    who: r.who || '',
    contact: r.contact || '',
    status: r.status,
    unreadAdmin: Number(r.unread_admin),
    unreadUser: Number(r.unread_user),
    lastMessage: r.last_message || '',
    lastSender: r.last_sender || '',
    createdAt: Number(r.created_at),
    updatedAt: Number(r.updated_at),
    //  چیزهایی که فقط در فهرستِ مدیر می‌آیند
    ...(r.account_name !== undefined ? { accountName: r.account_name || '' } : {}),
    ...(r.account_email !== undefined ? { accountEmail: r.account_email || '' } : {}),
    ...(r.shop_name !== undefined ? { shopName: r.shop_name || '' } : {}),
    ...(r.station_name !== undefined ? { stationName: r.station_name || '' } : {}),
  };
}

function shapeMessage(r) {
  return {
    id: r.id,
    threadId: r.thread_id,
    sender: r.sender,
    senderId: r.sender_id || '',
    senderName: r.sender_name || '',
    body: r.body,
    kind: r.kind,
    mediaId: r.media_id || null,
    readAt: r.read_at ? Number(r.read_at) : null,
    createdAt: Number(r.created_at),
  };
}

/**
 * رشته‌ی این نفر را می‌دهد و اگر نبود می‌سازد.
 *
 * کلید شناسایی: اول حساب، بعد دستگاه. یعنی مهمانی که بعداً حساب
 * می‌سازد، همان رشته‌ی قبلی‌اش را دارد و مجبور نیست دوباره از اول
 * توضیح بدهد.
 */
async function threadFor({ app = 'shop', userId = '', shopId = '', stationId = '', deviceUid = '', who = '', contact = '', subject = '' }) {
  if (!userId && !deviceUid && !stationId) {
    throw badRequest('برای پشتیبانی، شناسه‌ی دستگاه یا حساب لازم است', 'identity_required');
  }

  /*
   *  ⚠️ برای پمپ، کلید **خودِ پمپ** است نه آدم و نه دستگاه.
   *
   *  برنامهٔ کامپیوترِ پمپ حساب ندارد و یک پمپ می‌تواند چند کامپیوتر
   *  داشته باشد. اگر کلید `device_uid` بود، هر کامپیوتر یک گفت‌وگوی
   *  جدا می‌ساخت و مدیر نمی‌فهمید هر سه یک پمپ‌اند — و صاحبِ پمپ که
   *  از گوشی می‌نویسد، جوابِ کامپیوترش را نمی‌دید.
   *
   *  پس: یک پمپ، یک گفت‌وگو. هر دری که باز شود، به همان می‌رسد.
   */
  let row = stationId
    ? await one(
        `SELECT * FROM support_threads WHERE app=$1 AND station_id=$2 ORDER BY updated_at DESC LIMIT 1`,
        [app, stationId]
      )
    : null;

  //  رشته‌ای که پیش از داشتنِ پمپ ساخته شده بود، حالا به پمپ می‌چسبد —
  //  همان کاری که برای مهمانِ بی‌حساب می‌کنیم
  if (!row && stationId && userId) {
    row = await one(
      `SELECT * FROM support_threads WHERE app=$1 AND user_id=$2 AND station_id=''
        ORDER BY updated_at DESC LIMIT 1`,
      [app, userId]
    );
    if (row) {
      row = await one(
        `UPDATE support_threads SET station_id=$2, updated_at=$3 WHERE id=$1 RETURNING *`,
        [row.id, stationId, now()]
      );
    }
  }

  if (!row && userId) {
    row = await one(
      `SELECT * FROM support_threads WHERE app=$1 AND user_id=$2 ORDER BY updated_at DESC LIMIT 1`,
      [app, userId]
    );
  }

  if (!row && deviceUid) {
    row = await one(
      `SELECT * FROM support_threads WHERE app=$1 AND device_uid=$2 AND user_id='' ORDER BY updated_at DESC LIMIT 1`,
      [app, deviceUid]
    );
    //  مهمانی که حالا حساب دارد: همان رشته به حسابش وصل می‌شود
    if (row && userId) {
      row = await one(
        `UPDATE support_threads SET user_id=$2, shop_id=$3, updated_at=$4 WHERE id=$1 RETURNING *`,
        [row.id, userId, shopId, now()]
      );
    }
  }

  if (row) {
    //  نام و دکان ممکن است از دفعه‌ی قبل عوض شده باشد
    if ((shopId && row.shop_id !== shopId) || (who && row.who !== who)
        || (contact && row.contact !== contact) || (stationId && row.station_id !== stationId)) {
      row = await one(
        `UPDATE support_threads SET
            shop_id = CASE WHEN $2 <> '' THEN $2 ELSE shop_id END,
            who     = CASE WHEN $3 <> '' THEN $3 ELSE who END,
            contact = CASE WHEN $4 <> '' THEN $4 ELSE contact END,
            station_id = CASE WHEN $5 <> '' THEN $5 ELSE station_id END
          WHERE id=$1 RETURNING *`,
        [row.id, shopId, who, contact, stationId]
      );
    }
    return row;
  }

  const t = now();
  return one(
    `INSERT INTO support_threads (id, app, user_id, shop_id, station_id, device_uid, subject, who, contact,
                                  status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,'open',$10,$10) RETURNING *`,
    [newId('thr'), app, userId, shopId, stationId, deviceUid,
     subject.slice(0, 120), who.slice(0, 80), contact.slice(0, 120), t]
  );
}

/**
 * پیام تازه.
 *
 * `sender` یکی از user | admin | system. شمارنده‌ی خوانده‌نشده‌ی طرفِ
 * مقابل یکی بالا می‌رود — همان چیزی که نقطه‌ی قرمز روی آیکون را
 * می‌سازد.
 */
async function post(threadId, { sender = 'user', senderId = '', senderName = '', body = '', kind = 'text', mediaId = null }) {
  const text = String(body || '').trim();
  const isMedia = MEDIA_KINDS.includes(kind);
  if (!text && !isMedia) throw badRequest('پیام خالی است', 'empty_message');
  if (text.length > MAX_BODY) throw badRequest('پیام خیلی بلند است', 'message_too_long');

  const thread = await one('SELECT * FROM support_threads WHERE id=$1', [threadId]);
  if (!thread) throw notFound('این گفت‌وگو پیدا نشد', 'thread_not_found');

  /*
   *  پیامِ رسانه‌ای فقط به رسانه‌ای اشاره می‌کند که در **همین** رشته
   *  بارگذاری شده و نوعش با پیام می‌خواند. بی این، شناسهٔ رسانهٔ
   *  پمپِ دیگری در پیامِ این پمپ می‌نشست.
   */
  let media = null;
  if (isMedia) {
    const mid = String(mediaId || '').trim();
    //  ⚠️ و فقط رسانه‌ای که **همین طرف** فرستاده؛ وگرنه رسانهٔ طرفِ دیگر
    //  در پیامِ این طرف می‌نشست و قاعدهٔ «گیرنده که گرفت پاک شود» وارونه
    media = mid && await one(
      'SELECT id, mime FROM support_media WHERE id=$1 AND thread_id=$2 AND uploader=$3',
      [mid, threadId, sender === 'admin' ? 'admin' : 'user']
    );
    if (!media || mediaKindOf(media.mime) !== kind) {
      throw badRequest('رسانهٔ این پیام پیدا نشد یا نوعش نمی‌خواند', 'bad_media');
    }
  }
  const preview = isMedia ? (text ? `${MEDIA_PREVIEW[kind]} · ${text}` : MEDIA_PREVIEW[kind]) : text;

  const t = now();
  const message = await one(
    `INSERT INTO support_messages (id, thread_id, sender, sender_id, sender_name, body, kind, media_id, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9) RETURNING *`,
    [newId('msg'), threadId, sender, senderId, senderName.slice(0, 80), text, kind, media ? media.id : null, t]
  );

  //  صندوقِ پشتیبانیِ پنل زنده است: پیامِ تازه وسطِ باز بودنِ همان گفت‌وگو می‌نشیند
  notifyPanel('support');

  const toAdmin = sender === 'user' ? 1 : 0;
  const toUser = sender === 'user' ? 0 : 1;
  await query(
    `UPDATE support_threads SET
        unread_admin = unread_admin + $2,
        unread_user  = unread_user + $3,
        last_message = $4, last_sender = $5,
        status = CASE WHEN status='closed' THEN 'open' ELSE status END,
        updated_at = $6
      WHERE id=$1`,
    [threadId, toAdmin, toUser, preview.slice(0, 200), sender, t]
  );

  //  پوش. اگر تنظیم نشده باشد بی‌صدا رد می‌شود و پیام سر جایش می‌ماند.
  try {
    if (sender === 'user') {
      await push.sendTo({ allAdmins: true }, {
        title: 'پیام تازه‌ی پشتیبانی',
        body: `${senderName || thread.who || 'یک کاربر'}: ${preview.slice(0, 90)}`,
        data: { type: 'support', threadId },
      });
    } else if (thread.user_id || thread.station_id) {
      /*
       *  ⚠️ `station_id` هم گیرنده است و بی آن نیمی از پمپ‌ها ساکت
       *  می‌ماندند: پمپی که با کدِ شش‌رقمی فعال شده `user_id` ندارد،
       *  پس جوابِ مدیر و خبرِ پایانِ اشتراکش به هیچ دستگاهی نمی‌رسید —
       *  فقط دفعهٔ بعد که کسی برنامه را باز می‌کرد دیده می‌شد.
       */
      await push.sendTo({
        userId: thread.user_id || '',
        stationId: thread.station_id || '',
        app: thread.app,
      }, {
        title: 'پاسخ پشتیبانی',
        body: preview.slice(0, 120),
        data: { type: 'support', threadId },
      });
    }
  } catch (err) {
    console.error('[support:push]', err.message);
  }

  return shapeMessage(message);
}

/**
 * بارگذاریِ یک رسانه در یک رشته. `uploader` همان طرفی است که فرستاد —
 * و تنها طرفی که گرفتنِ رسانه پاکش **نمی‌کند**.
 */
async function putMedia({ threadId, uploader, mime, buf }) {
  const kind = mediaKindOf(mime);
  if (!kind) throw badRequest('فقط عکس، ویدیو و صدا', 'bad_media');
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('فایل خالی است', 'bad_media');
  if (buf.length > MAX_MEDIA) throw new ApiError(413, 'too_large', 'فایل بزرگ‌تر از ۲۵ مگابایت است');
  if (uploader !== 'user' && uploader !== 'admin') throw badRequest('فرستنده نامعتبر است');
  const row = await one(
    `INSERT INTO support_media (id, thread_id, uploader, mime, size, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7) RETURNING id, mime, size`,
    [newId('smd'), threadId, uploader, String(mime).toLowerCase().split(';')[0].trim().slice(0, 80), buf.length, buf, now()]
  );
  return { mediaId: row.id, kind, mime: row.mime, size: Number(row.size) };
}

/**
 * یک رسانه، از دیدِ یک طرف. `threadId` خالی یعنی مدیر (هر رشته‌ای).
 * نبود ⇒ `null` (و مسیر ۴۰۴ِ `media_gone` می‌دهد).
 */
async function getMedia({ id, threadId = null }) {
  const mid = String(id || '').trim();
  if (!mid || mid.length > 80) return null;
  return one(
    'SELECT id, thread_id, uploader, mime, size, data FROM support_media WHERE id=$1'
      + (threadId ? ' AND thread_id=$2' : ''),
    threadId ? [mid, threadId] : [mid]
  );
}

/**
 * فرستادنِ بایت‌های یک رسانه به `side` (user | admin).
 *
 * ⛔ **قاعدهٔ رله**: اگر `side` گیرنده است (رسانه را طرفِ دیگر
 * فرستاده)، ردیف همان لحظه که پاسخ کامل رفت پاک می‌شود — `finish`، نه
 * `close`: اتصالی که وسطِ کار برید چیزی نگرفته و رسانه باید بماند تا
 * دوباره بپرسد. فرستنده که رسانهٔ خودش را بگیرد چیزی پاک نمی‌شود.
 * ⚠️ `HEAD` هم به همین مسیر می‌رسد و هیچ بایتی نمی‌برد — پاک نمی‌کند.
 */
function sendMedia(req, res, m, side) {
  res.set('Content-Type', m.mime);
  res.set('Content-Length', String(m.size));
  res.set('Cache-Control', 'no-store');
  //  ⛔ رسانه هرگز صفحه نیست — حتی اگر مستقیم باز شود
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Content-Security-Policy', "default-src 'none'; media-src 'self'; img-src 'self'; sandbox");
  const recipient = m.uploader !== side;
  if (recipient && req.method === 'GET') {
    res.on('finish', () => {
      query('DELETE FROM support_media WHERE id=$1', [m.id])
        .catch((err) => console.error('[support:media-relay]', err.message));
    });
  }
  res.end(m.data);
}

/** پیام‌های یک رشته. `after` برای گرفتن فقط تازه‌ها. */
async function messages(threadId, { after = 0, limit = 200 } = {}) {
  const rows = await many(
    `SELECT * FROM support_messages WHERE thread_id=$1 AND created_at > $2
      ORDER BY created_at ASC LIMIT $3`,
    [threadId, after, limit]
  );
  return rows.map(shapeMessage);
}

/** «خواندم» — از طرفِ کاربر یا از طرفِ مدیر. */
async function markRead(threadId, side) {
  const column = side === 'admin' ? 'unread_admin' : 'unread_user';
  const other = side === 'admin' ? 'user' : 'admin';
  await query(`UPDATE support_threads SET ${column}=0 WHERE id=$1`, [threadId]);
  await query(
    `UPDATE support_messages SET read_at=$2 WHERE thread_id=$1 AND sender=$3 AND read_at IS NULL`,
    [threadId, now(), other]
  );
}

/** فهرست برای مدیر — تازه‌ترین و خوانده‌نشده‌ها بالا. */
async function list({ status = '', q = '', app = '', limit = 100, offset = 0 } = {}) {
  const like = `%${String(q || '').toLowerCase()}%`;
  /*
   *  ⚠️ `stations` هم `LEFT JOIN` می‌شود، وگرنه رشتهٔ یک پمپ در فهرستِ
   *  مدیر فقط یک شناسه بود: نه نامی، نه کدی. مدیری که نداند پیام از
   *  کدام پمپ است، نمی‌تواند جواب بدهد.
   */
  const rows = await many(
    `SELECT t.*, u.name AS account_name, u.email AS account_email,
            s.name AS shop_name, st.name AS station_name
       FROM support_threads t
       LEFT JOIN users u ON u.id = t.user_id
       LEFT JOIN shops s ON s.id = t.shop_id
       LEFT JOIN stations st ON st.id = t.station_id
      WHERE ($1 = '' OR t.status = $1)
        AND ($2 = '' OR lower(t.who) LIKE $3 OR lower(coalesce(u.name,'')) LIKE $3
             OR lower(coalesce(u.email,'')) LIKE $3 OR lower(t.last_message) LIKE $3
             OR lower(coalesce(st.name,'')) LIKE $3)
        AND ($6 = '' OR t.app = $6)
      ORDER BY (t.unread_admin > 0) DESC, t.updated_at DESC
      LIMIT $4 OFFSET $5`,
    [String(status || ''), String(q || ''), like, limit, offset, String(app || '')]
  );
  return rows.map(shapeThread);
}

async function setStatus(threadId, status) {
  const row = await one(
    `UPDATE support_threads SET status=$2, updated_at=$3 WHERE id=$1 RETURNING *`,
    [threadId, status, now()]
  );
  if (!row) throw notFound('این گفت‌وگو پیدا نشد', 'thread_not_found');
  return shapeThread(row);
}

/** چند پیامِ خوانده‌نشده در کل — برای نقطه‌ی قرمز روی تبِ پشتیبانی. */
async function unreadForAdmin() {
  const r = await one(`SELECT COALESCE(SUM(unread_admin),0)::int n FROM support_threads WHERE status <> 'closed'`);
  return r.n;
}

/**
 * پیام خودکار از طرف سامانه.
 *
 * برای خبرهایی مثل «اشتراکت دارد تمام می‌شود». همان رشته‌ی همیشگیِ طرف
 * را می‌گیرد تا خبر جای دیگری گم نشود.
 */
async function systemMessage({ app = 'shop', userId = '', shopId = '', stationId = '', who = '', body, kind = 'notice' }) {
  const thread = await threadFor({ app, userId, shopId, stationId, who });
  return post(thread.id, { sender: 'system', senderName: 'توحید', body, kind });
}

/**
 * خوانندهٔ بدنهٔ خامِ بارگذاری. بدنهٔ بزرگ‌تر از سقف پیش از خوانده
 * شدن رد می‌شود — با همان ۴۱۳ِ `too_large`، نه جملهٔ انگلیسیِ Express.
 */
const rawParser = require('express').raw({ type: () => true, limit: MAX_MEDIA });
function rawMedia(req, res, next) {
  const len = Number(req.headers['content-length']);
  if (Number.isFinite(len) && len > MAX_MEDIA) {
    return next(new ApiError(413, 'too_large', 'فایل بزرگ‌تر از ۲۵ مگابایت است'));
  }
  rawParser(req, res, (err) => {
    if (err && err.type === 'entity.too.large') {
      return next(new ApiError(413, 'too_large', 'فایل بزرگ‌تر از ۲۵ مگابایت است'));
    }
    if (err) return next(new ApiError(400, 'bad_media', 'فایل درست نرسید'));
    next();
  });
}

module.exports = {
  threadFor, post, messages, markRead, list, setStatus, unreadForAdmin, systemMessage,
  shapeThread, shapeMessage, MAX_BODY, cleanBody,
  MEDIA_KINDS, MAX_MEDIA, MEDIA_PREVIEW, mediaKindOf, putMedia, getMedia, sendMedia, rawMedia,
};
