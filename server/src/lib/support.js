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
const push = require('./push');
const { badRequest, notFound } = require('../middleware/errors');

const MAX_BODY = 4000;

function shapeThread(r) {
  return {
    id: r.id,
    app: r.app,
    userId: r.user_id || '',
    shopId: r.shop_id || '',
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
async function threadFor({ app = 'shop', userId = '', shopId = '', deviceUid = '', who = '', contact = '', subject = '' }) {
  if (!userId && !deviceUid) throw badRequest('برای پشتیبانی، شناسه‌ی دستگاه یا حساب لازم است', 'identity_required');

  let row = userId
    ? await one(`SELECT * FROM support_threads WHERE app=$1 AND user_id=$2 ORDER BY updated_at DESC LIMIT 1`, [app, userId])
    : null;

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
    if ((shopId && row.shop_id !== shopId) || (who && row.who !== who) || (contact && row.contact !== contact)) {
      row = await one(
        `UPDATE support_threads SET
            shop_id = CASE WHEN $2 <> '' THEN $2 ELSE shop_id END,
            who     = CASE WHEN $3 <> '' THEN $3 ELSE who END,
            contact = CASE WHEN $4 <> '' THEN $4 ELSE contact END
          WHERE id=$1 RETURNING *`,
        [row.id, shopId, who, contact]
      );
    }
    return row;
  }

  const t = now();
  return one(
    `INSERT INTO support_threads (id, app, user_id, shop_id, device_uid, subject, who, contact,
                                  status, created_at, updated_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,'open',$9,$9) RETURNING *`,
    [newId('thr'), app, userId, shopId, deviceUid, subject.slice(0, 120), who.slice(0, 80), contact.slice(0, 120), t]
  );
}

/**
 * پیام تازه.
 *
 * `sender` یکی از user | admin | system. شمارنده‌ی خوانده‌نشده‌ی طرفِ
 * مقابل یکی بالا می‌رود — همان چیزی که نقطه‌ی قرمز روی آیکون را
 * می‌سازد.
 */
async function post(threadId, { sender = 'user', senderId = '', senderName = '', body = '', kind = 'text' }) {
  const text = String(body || '').trim();
  if (!text) throw badRequest('پیام خالی است', 'empty_message');
  if (text.length > MAX_BODY) throw badRequest('پیام خیلی بلند است', 'message_too_long');

  const thread = await one('SELECT * FROM support_threads WHERE id=$1', [threadId]);
  if (!thread) throw notFound('این گفت‌وگو پیدا نشد', 'thread_not_found');

  const t = now();
  const message = await one(
    `INSERT INTO support_messages (id, thread_id, sender, sender_id, sender_name, body, kind, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8) RETURNING *`,
    [newId('msg'), threadId, sender, senderId, senderName.slice(0, 80), text, kind, t]
  );

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
    [threadId, toAdmin, toUser, text.slice(0, 200), sender, t]
  );

  //  پوش. اگر تنظیم نشده باشد بی‌صدا رد می‌شود و پیام سر جایش می‌ماند.
  try {
    if (sender === 'user') {
      await push.sendTo({ allAdmins: true }, {
        title: 'پیام تازه‌ی پشتیبانی',
        body: `${senderName || thread.who || 'یک کاربر'}: ${text.slice(0, 90)}`,
        data: { type: 'support', threadId },
      });
    } else if (thread.user_id) {
      await push.sendTo({ userId: thread.user_id, app: thread.app }, {
        title: 'پاسخ پشتیبانی',
        body: text.slice(0, 120),
        data: { type: 'support', threadId },
      });
    }
  } catch (err) {
    console.error('[support:push]', err.message);
  }

  return shapeMessage(message);
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
async function list({ status = '', q = '', limit = 100, offset = 0 } = {}) {
  const like = `%${String(q || '').toLowerCase()}%`;
  const rows = await many(
    `SELECT t.*, u.name AS account_name, u.email AS account_email, s.name AS shop_name
       FROM support_threads t
       LEFT JOIN users u ON u.id = t.user_id
       LEFT JOIN shops s ON s.id = t.shop_id
      WHERE ($1 = '' OR t.status = $1)
        AND ($2 = '' OR lower(t.who) LIKE $3 OR lower(coalesce(u.name,'')) LIKE $3
             OR lower(coalesce(u.email,'')) LIKE $3 OR lower(t.last_message) LIKE $3)
      ORDER BY (t.unread_admin > 0) DESC, t.updated_at DESC
      LIMIT $4 OFFSET $5`,
    [String(status || ''), String(q || ''), like, limit, offset]
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
async function systemMessage({ app = 'shop', userId, shopId = '', who = '', body, kind = 'notice' }) {
  const thread = await threadFor({ app, userId, shopId, who });
  return post(thread.id, { sender: 'system', senderName: 'توحید', body, kind });
}

module.exports = {
  threadFor, post, messages, markRead, list, setStatus, unreadForAdmin, systemMessage,
  shapeThread, shapeMessage, MAX_BODY,
};
