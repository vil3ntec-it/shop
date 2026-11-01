'use strict';
/**
 * چتِ مشتری ↔ دکان — هستهٔ سرور (v3 §۳۳؛ CHAT-01…15). شرحِ جدول‌ها در
 * ‎migrations/085_shop_chat.sql‎ و ‎086_shop_chat_media.sql‎.
 *
 *   پرچم ── `shop.chat` در «تنظیماتِ زنده» (scope = شناسهٔ دکان، یا `all`).
 *           ⛔ پیش‌فرض خاموش ⇒ همهٔ درها ۴۰۴ و هیچ ردیفی ساخته نمی‌شود.
 *   بیننده ─ `{ side: 'shop', shopId, userId, role }` (کارمند؛ shop_id فقط از
 *           عضویتِ توکن) یا `{ side: 'customer', shopId, phone }` (مشتری؛
 *           شمارهٔ تأییدشده از بلیتِ ویترین). هر پرسش با همین دو قفل می‌شود:
 *           شناسهٔ گفت‌وگو/پیام/رسانهٔ دیگری ⇒ همان ۴۰۴ ِ «نیست» (CHAT-09).
 *   ارسال ─ با `clientId` ِ خودِ دستگاه، زیرِ قفلِ ردیفِ گفت‌وگو: تکرار ⇒ همان
 *           پیام، نه دومی (CHAT-02/11)؛ همان شناسه با بدنهٔ دیگر ⇒ ۴۰۹.
 *   همگام ─ `after=<rev>`: هر درج/ویرایش/حذف/پنهان rev ِ تازه دارد ⇒ دستگاهِ
 *           بسته یا آفلاین همه‌چیز را، با ترتیبِ seq، از سرور می‌گیرد
 *           (CHAT-03/08/15). `wait` = نگه‌داشتنِ درخواست تا پیامِ تازه
 *           (long-poll)؛ سوکت فقط شتاب است، منبع نیست (۳۳.۲ بندِ ۷).
 *   رسید ── آب‌خطِ `delivered`/`read` ِ هر طرف فقط با اعلامِ خودِ گیرنده جلو
 *           می‌رود و عقب نمی‌رود (CHAT-04).
 *
 * ⛔ مشتری هیچ شناسه، نام یا شماره‌ای از کارمند نمی‌بیند؛ دکان شمارهٔ مشتری
 * را پوشیده می‌بیند مگر `orders.contact` داشته باشد (§۳۳.۳). IP، دستگاه و
 * بلیتِ مشتری در هیچ جدولِ چت نمی‌نشیند. متنِ پیام در لاگ و سابقهٔ عملیات
 * نوشته نمی‌شود؛ پوش هم بی متن است.
 * ⛔ رمزگذاریِ سرتاسری (E2EE) نیست و ادعا هم نمی‌شود.
 */
const crypto = require('crypto');
const express = require('express');
const config = require('../config');
const { one, many, query, newId, now, tx } = require('../db');
const { stable } = require('./idempotency');
const { can } = require('./permissions');
const chatMedia = require('./chat-media');
const { notify, listen, waiting } = require('./chat-wait');
const { ApiError, badRequest, forbidden, notFound, conflict } = require('../middleware/errors');

const FLAG_KEY = 'shop.chat';
const MAX_BODY = 4000;
const MAX_LIST = 100;
const MAX_PAGE = 200;
const MAX_WAIT_MS = 25_000;
const ORPHAN_MS = 60 * 60 * 1000;
const CLIENT_ID_RE = /^[A-Za-z0-9_.:-]{8,128}$/;
const ID_RE = /^[A-Za-z0-9_]{1,64}$/;
const KINDS = ['text', 'image', 'audio', 'file'];
const REASONS = ['spam', 'abuse', 'fraud', 'other'];
//  سنجاقِ هر طرف در هر گفت‌وگو (CHAT-14) — کم و بسته، تا فهرستِ سنجاق خودش صندوق نشود
const MAX_PINS = 5;

// ════════════════════════════════════════════════════════════════════
//  کمک‌ها
// ════════════════════════════════════════════════════════════════════

const str = (v) => (typeof v === 'string' ? v.trim() : '');
const obj = (v) => (v && typeof v === 'object' && !Array.isArray(v) ? v : {});
const asJson = (x) => {
  if (x && typeof x === 'object') return x;
  try { return JSON.parse(x) || {}; } catch { return {}; }
};
const n = (x) => Number(x || 0);
const sha256 = (x) => crypto.createHash('sha256').update(x).digest('hex');

/** متنِ پیام: نویسه‌های کنترلی می‌روند؛ بلندتر از سقف ⇒ خطا، نه بریدنِ بی‌صدا. */
function cleanText(raw) {
  const t = String(raw == null ? '' : raw).replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F\u007F]/g, '').trim();
  if (t.length > MAX_BODY) throw badRequest(`پیام خیلی بلند است — حداکثر ${MAX_BODY} نویسه`, 'message_too_long');
  return t;
}

function shortText(raw, max) {
  return String(raw == null ? '' : raw).replace(/[\u0000-\u001F\u007F]/g, '').trim().slice(0, max);
}

function intIn(v, { min = 0, max = Number.MAX_SAFE_INTEGER, def = 0 } = {}) {
  if (v === undefined || v === null || v === '') return def;
  const x = Number(v);
  if (!Number.isSafeInteger(x) || x < min || x > max) throw badRequest('عدد در بازهٔ مجاز نیست', 'bad_number');
  return x;
}

function maskPhone(p) {
  const s = String(p || '');
  if (s.length <= 7) return '•'.repeat(s.length);
  return s.slice(0, 4) + '•'.repeat(s.length - 7) + s.slice(-3);
}

const viewerKey = (v) => (v.side === 'customer' ? 'customer' : `user:${v.userId}`);
const peerOf = (side) => (side === 'customer' ? 'shop' : 'customer');
const gone = () => notFound('این گفت‌وگو پیدا نشد', 'conversation_not_found');
const isAuthor = (v, m) => m.sender === v.side && (v.side === 'customer' || m.sender_user_id === v.userId);

// ════════════════════════════════════════════════════════════════════
//  پرچم
// ════════════════════════════════════════════════════════════════════

/** پرچمِ دکان: مقدارِ خودِ دکان بر `all` جلو می‌افتد؛ فقط `true` روشن است. */
async function flagOf(shopId) {
  const rows = await many(
    'SELECT scope, value FROM live_config WHERE key=$1 AND scope IN ($2,$3)',
    [FLAG_KEY, 'all', String(shopId || '')]
  );
  const decode = (x) => { try { return JSON.parse(x); } catch { return null; } };
  let v;
  for (const r of rows) if (r.scope === 'all') v = decode(r.value);
  for (const r of rows) if (r.scope !== 'all') v = decode(r.value);
  return v === true;
}

// ════════════════════════════════════════════════════════════════════
//  گفت‌وگو
// ════════════════════════════════════════════════════════════════════

/** ردیفِ گفت‌وگو از دیدِ بیننده — یا ۴۰۴ (نبودن و مالِ دیگری بودن یک جواب‌اند). */
async function rowOf(viewer, id, { c = null, lock = false } = {}) {
  const cid = String(id || '');
  if (!ID_RE.test(cid)) throw gone();
  const args = [cid, viewer.shopId];
  let sql = 'SELECT * FROM shop_chat_conversations WHERE id=$1 AND shop_id=$2';
  if (viewer.side === 'customer') { args.push(viewer.phone); sql += ' AND customer_phone=$3'; }
  if (lock) sql += ' FOR UPDATE';
  const row = c ? (await c.query(sql, args)).rows[0] : await one(sql, args);
  if (!row) throw gone();
  return row;
}

/** بلاک‌های (دکان، شماره) — روی هر شکلِ همان شماره (RSK-07): شکلِ دیگرِ همان گوشی بلاکِ دکان را دور نمی‌زند. */
async function blocksOf(shopId, phone, c = null) {
  const sql = 'SELECT blocker FROM shop_chat_blocks WHERE shop_id=$1 AND customer_phone = ANY($2::text[])';
  const args = [shopId, require('./validate').phoneForms(phone)];
  const rows = c ? (await c.query(sql, args)).rows : await many(sql, args);
  return { shop: rows.some((r) => r.blocker === 'shop'), customer: rows.some((r) => r.blocker === 'customer') };
}

/** پرسشِ نمای گفت‌وگو — شمارِ نخوانده، شمارهٔ سفارش، نامِ دکان، بلاک‌ها و آخرین پیام. */
function viewSql(viewer) {
  const mine = viewer.side === 'shop' ? 'shop' : 'customer';
  return `SELECT c.*, s.name AS shop_name, o.order_no,
      (SELECT COUNT(*)::int FROM shop_chat_messages m
        WHERE m.conversation_id = c.id AND m.sender = '${peerOf(mine)}'
          AND m.deleted_at IS NULL AND m.seq > c.${mine}_read_seq) AS unread,
      EXISTS (SELECT 1 FROM shop_chat_blocks b WHERE b.shop_id = c.shop_id
               AND b.customer_phone = c.customer_phone AND b.blocker = 'shop') AS blocked_by_shop,
      EXISTS (SELECT 1 FROM shop_chat_blocks b WHERE b.shop_id = c.shop_id
               AND b.customer_phone = c.customer_phone AND b.blocker = 'customer') AS blocked_by_customer,
      (SELECT json_build_object('kind', m.kind, 'body', LEFT(m.body, 80), 'sender', m.sender,
                                'deleted', m.deleted_at IS NOT NULL, 'at', m.created_at)
         FROM shop_chat_messages m WHERE m.conversation_id = c.id ORDER BY m.seq DESC LIMIT 1) AS last,
      (SELECT COALESCE(json_agg(json_build_object('messageId', p.message_id, 'seq', pm.seq, 'at', p.created_at)
                                ORDER BY p.created_at DESC, p.message_id), '[]'::json)
         FROM shop_chat_pins p JOIN shop_chat_messages pm ON pm.id = p.message_id
        WHERE p.conversation_id = c.id AND p.side = '${mine}' AND pm.deleted_at IS NULL) AS pinned
    FROM shop_chat_conversations c
    JOIN shops s ON s.id = c.shop_id
    LEFT JOIN online_orders o ON o.id = c.order_id AND o.shop_id = c.shop_id`;
}

function conversationView(viewer, r) {
  const last = r.last ? asJson(r.last) : null;
  const base = {
    id: r.id,
    status: r.status,
    seq: n(r.seq),
    rev: n(r.rev),
    unread: n(r.unread),
    //  فقط سنجاق‌های طرفِ خودِ بیننده (CHAT-14) — بی متن؛ تازه‌ترین اول
    pinned: (Array.isArray(r.pinned) ? r.pinned : Object.values(asJson(r.pinned)))
      .map((x) => ({ messageId: x.messageId, seq: n(x.seq), pinnedAt: n(x.at) })),
    orderNo: r.order_no == null ? null : Number(r.order_no),
    lastMessage: last ? {
      kind: last.kind, sender: last.sender, at: n(last.at),
      ...(last.deleted ? { deleted: true } : { preview: last.body || '' }),
    } : null,
    lastMessageAt: r.last_message_at == null ? null : n(r.last_message_at),
    createdAt: n(r.created_at),
    updatedAt: n(r.updated_at),
  };
  if (viewer.side === 'customer') {
    return {
      ...base,
      shop: { name: r.shop_name || '' },
      canSend: !r.blocked_by_shop,
      blockedShop: !!r.blocked_by_customer,
      receipts: {
        peerDeliveredSeq: n(r.shop_delivered_seq), peerReadSeq: n(r.shop_read_seq),
        myDeliveredSeq: n(r.customer_delivered_seq), myReadSeq: n(r.customer_read_seq),
      },
    };
  }
  const contact = can(viewer.role, 'orders.contact');
  return {
    ...base,
    orderId: r.order_id || null,
    customer: {
      name: r.customer_name || '',
      phoneMasked: maskPhone(r.customer_phone),
      ...(contact ? { phone: r.customer_phone } : {}),
    },
    blockedCustomer: !!r.blocked_by_shop,
    customerBlockedShop: !!r.blocked_by_customer,
    canSend: !r.blocked_by_customer,
    receipts: {
      peerDeliveredSeq: n(r.customer_delivered_seq), peerReadSeq: n(r.customer_read_seq),
      myDeliveredSeq: n(r.shop_delivered_seq), myReadSeq: n(r.shop_read_seq),
    },
  };
}

/** ردیفِ کاملِ نما (ستون‌های گفت‌وگو + شمارش‌ها) — همان قفلِ `rowOf`. */
async function viewRow(viewer, id) {
  const row = await rowOf(viewer, id);
  return one(`${viewSql(viewer)} WHERE c.id = $1 AND c.shop_id = $2`, [row.id, viewer.shopId]);
}

async function viewOf(viewer, id) {
  return conversationView(viewer, await viewRow(viewer, id));
}

function encodeCursor(at, id) { return Buffer.from(`${at}:${id}`).toString('base64url'); }
function decodeCursor(raw) {
  if (!raw) return null;
  const m = /^(\d{1,16}):([A-Za-z0-9_]{1,64})$/.exec(Buffer.from(String(raw), 'base64url').toString('utf8'));
  if (!m) throw badRequest('نشانهٔ صفحه نامعتبر است', 'bad_cursor');
  return { at: Number(m[1]), id: m[2] };
}

/**
 * فهرستِ گفت‌وگوها — تازه‌ترین بالا، صفحه‌بندیِ کلیدی.
 * کارمند فقط گفت‌وگوهایی را می‌بیند که پیامی دارند یا از روی سفارش باز شده‌اند.
 * جست‌وجو با شماره فقط برای کسی که شمارهٔ کامل را می‌بیند (وگرنه شماره را حدس می‌زد).
 */
async function list(viewer, { cursor, limit, status, q } = {}) {
  const lim = intIn(limit, { min: 1, max: MAX_LIST, def: 30 });
  const args = [viewer.shopId];
  const where = ['c.shop_id = $1'];
  if (viewer.side === 'customer') {
    args.push(viewer.phone); where.push(`c.customer_phone = $${args.length}`);
  } else {
    where.push("(c.seq > 0 OR c.order_id <> '')");
    const st = str(status);
    if (st && st !== 'all') {
      if (!['open', 'closed'].includes(st)) throw badRequest('وضعیت نامعتبر است', 'bad_status');
      args.push(st); where.push(`c.status = $${args.length}`);
    }
    const term = shortText(q, 60);
    if (term) {
      const like = `%${term.replace(/[\\%_]/g, (ch) => `\\${ch}`)}%`;
      args.push(like);
      const byName = `c.customer_name ILIKE $${args.length} ESCAPE '\\'`;
      const digits = term.replace(/\D/g, '');
      if (can(viewer.role, 'orders.contact') && digits.length >= 4) {
        args.push(`%${digits}%`);
        where.push(`(${byName} OR c.customer_phone LIKE $${args.length})`);
      } else {
        where.push(byName);
      }
    }
  }
  const cur = decodeCursor(cursor);
  if (cur) {
    args.push(cur.at, cur.id);
    where.push(`(c.updated_at < $${args.length - 1} OR (c.updated_at = $${args.length - 1} AND c.id < $${args.length}))`);
  }
  args.push(lim + 1);
  const rows = await many(
    `${viewSql(viewer)} WHERE ${where.join(' AND ')} ORDER BY c.updated_at DESC, c.id DESC LIMIT $${args.length}`,
    args
  );
  const page = rows.slice(0, lim);
  const more = rows.length > lim;
  const tail = page[page.length - 1];
  return {
    conversations: page.map((r) => conversationView(viewer, r)),
    next: more && tail ? encodeCursor(n(tail.updated_at), tail.id) : null,
  };
}

/**
 * باز کردنِ گفت‌وگو (یا گرفتنِ همانِ موجود).
 *   مشتری: با دکان، یا دربارهٔ سفارشِ خودش (`orderNo` — سفارشِ همین دکان و همین شماره).
 *   کارمند: فقط از روی یک سفارشِ همین دکان (`orderId`) — دکان سرِ خود به شماره‌ای پیام نمی‌دهد.
 */
async function open(viewer, body) {
  const b = obj(body);
  let phone;
  let orderId = '';
  let name = '';
  if (viewer.side === 'customer') {
    phone = viewer.phone;
    name = shortText(b.name, 60);
    if (b.orderNo !== undefined && b.orderNo !== null && b.orderNo !== '') {
      const no = intIn(b.orderNo, { min: 1 });
      const o = await one(
        'SELECT id FROM online_orders WHERE shop_id=$1 AND order_no=$2 AND customer_phone=$3',
        [viewer.shopId, no, phone]
      );
      if (!o) throw notFound('این سفارش پیدا نشد', 'order_not_found');
      orderId = o.id;
    }
  } else {
    const oid = str(b.orderId);
    if (!oid) throw badRequest('گفت‌وگوی دکان فقط از روی یک سفارش باز می‌شود', 'order_required');
    const o = await one('SELECT id, customer_phone, customer_name FROM online_orders WHERE shop_id=$1 AND id=$2', [viewer.shopId, oid]);
    if (!o) throw notFound('این سفارش پیدا نشد', 'order_not_found');
    if (!o.customer_phone) throw badRequest('این سفارش شمارهٔ مشتری ندارد', 'no_customer_phone');
    phone = o.customer_phone;
    orderId = o.id;
    name = shortText(o.customer_name, 60);
  }
  const find = () => one(
    'SELECT id FROM shop_chat_conversations WHERE shop_id=$1 AND customer_phone=$2 AND order_id=$3',
    [viewer.shopId, phone, orderId]
  );
  let row = await find();
  let created = false;
  if (!row) {
    //  مشتریِ بسته‌شده گفت‌وگوی تازه نمی‌سازد (ردیفِ هرز نمی‌ماند)
    if (viewer.side === 'customer' && (await blocksOf(viewer.shopId, phone)).shop) {
      throw forbidden('این دکان پیامِ شما را نمی‌پذیرد', 'chat_blocked');
    }
    const t = now();
    const ins = await one(
      `INSERT INTO shop_chat_conversations (id, shop_id, customer_phone, customer_name, order_id, created_at, updated_at)
       VALUES ($1,$2,$3,$4,$5,$6,$6)
       ON CONFLICT (shop_id, customer_phone, order_id) DO NOTHING RETURNING id`,
      [newId('sch'), viewer.shopId, phone, name, orderId, t]
    );
    created = !!ins;
    row = ins || await find();
  } else if (viewer.side === 'customer' && name) {
    await query("UPDATE shop_chat_conversations SET customer_name=$2 WHERE id=$1 AND customer_name=''", [row.id, name]);
  }
  return { conversation: await viewOf(viewer, row.id), created };
}

async function setStatus(viewer, id, status) {
  const st = str(status);
  if (!['open', 'closed'].includes(st)) throw badRequest('وضعیت نامعتبر است', 'bad_status');
  const row = await rowOf(viewer, id);
  if (row.status !== st) {
    await query('UPDATE shop_chat_conversations SET status=$2, updated_at=$3 WHERE id=$1', [row.id, st, now()]);
    notify(row.id);
  }
  return viewOf(viewer, row.id);
}

// ════════════════════════════════════════════════════════════════════
//  پیام
// ════════════════════════════════════════════════════════════════════

function deliveryStatus(seq, conv, sender) {
  const peer = peerOf(sender);
  if (n(conv[`${peer}_read_seq`]) >= seq) return 'read';
  if (n(conv[`${peer}_delivered_seq`]) >= seq) return 'delivered';
  return 'sent';
}

function messageView(viewer, r, conv) {
  const base = { id: r.id, seq: n(r.seq), rev: n(r.rev), sender: r.sender, createdAt: n(r.created_at) };
  if (r.hidden) return { ...base, hidden: true };
  if (r.deleted_at) return { ...base, deleted: true, deletedAt: n(r.deleted_at) };
  const v = {
    ...base,
    kind: r.kind,
    body: r.body,
    replyTo: r.reply_to || null,
    meta: asJson(r.meta),
    editedAt: r.edited_at == null ? null : n(r.edited_at),
    mine: isAuthor(viewer, r),
    media: r.media_id ? {
      id: r.media_id,
      mime: r.media_mime || null,
      size: r.media_size == null ? null : n(r.media_size),
      thumbId: r.thumb_id || null,
      thumbSize: r.thumb_size == null ? null : n(r.thumb_size),
    } : null,
  };
  //  رسید فقط برای پیامِ طرفِ خودِ بیننده معنا دارد
  if (r.sender === viewer.side) {
    v.clientId = r.client_id;
    v.status = deliveryStatus(n(r.seq), conv, r.sender);
  }
  //  ⛔ شناسهٔ کارمند فقط درونِ دکان
  if (viewer.side === 'shop' && r.sender === 'shop') v.senderUserId = r.sender_user_id;
  return v;
}

function metaOf(raw, kind) {
  const m = obj(raw);
  const out = {};
  if (kind === 'audio' && m.durationMs !== undefined) out.durationMs = intIn(m.durationMs, { min: 1, max: 15 * 60 * 1000 });
  if (kind === 'image') {
    if (m.width !== undefined) out.width = intIn(m.width, { min: 1, max: 20000 });
    if (m.height !== undefined) out.height = intIn(m.height, { min: 1, max: 20000 });
  }
  return out;
}

/** ارسال — یک بار با هر `clientId`، هر چند بار که دستگاه دوباره بفرستد. */
async function send(viewer, id, body) {
  const b = obj(body);
  const clientId = str(b.clientId);
  if (!CLIENT_ID_RE.test(clientId)) throw badRequest('شناسهٔ یکتای پیام (clientId) لازم است', 'client_id_required');
  const kind = b.kind === undefined ? 'text' : b.kind;
  if (!KINDS.includes(kind)) throw badRequest('نوعِ پیام نامعتبر است', 'bad_kind');
  const text = cleanText(b.body);
  if (kind === 'text' && !text) throw badRequest('پیام خالی است', 'empty_message');
  const replyTo = str(b.replyTo) || null;
  const mediaId = str(b.mediaId) || null;
  const thumbId = str(b.thumbId) || null;
  if (kind === 'text' && (mediaId || thumbId)) throw badRequest('پیامِ متنی رسانه ندارد', 'bad_media');
  if (kind !== 'text' && !mediaId) throw badRequest('رسانهٔ این پیام نیامده', 'bad_media');
  if (thumbId && kind !== 'image') throw badRequest('پیش‌نمایش فقط برای عکس است', 'bad_media');
  const meta = metaOf(b.meta, kind);
  const hash = sha256(stable({ kind, body: text, replyTo, mediaId, thumbId, meta }));

  const out = await tx(async (c) => {
    const conv = await rowOf(viewer, id, { c, lock: true });
    const prev = (await c.query(
      'SELECT * FROM shop_chat_messages WHERE conversation_id=$1 AND sender=$2 AND client_id=$3',
      [conv.id, viewer.side, clientId]
    )).rows[0];
    if (prev) {
      if (prev.request_hash !== hash) throw conflict('این شناسهٔ پیام پیش‌تر با متنِ دیگری فرستاده شده', 'client_id_reused');
      return { conv, row: prev, replayed: true };
    }
    const bl = await blocksOf(conv.shop_id, conv.customer_phone, c);
    if (viewer.side === 'customer' && bl.shop) throw forbidden('این دکان پیامِ شما را نمی‌پذیرد', 'chat_blocked');
    if (viewer.side === 'shop' && bl.customer) throw forbidden('مشتری پیامِ این دکان را بسته است', 'chat_blocked_by_customer');
    if (replyTo) {
      const r = (await c.query('SELECT 1 FROM shop_chat_messages WHERE id=$1 AND conversation_id=$2', [replyTo, conv.id])).rows[0];
      if (!r) throw badRequest('پیامی که به آن پاسخ داده شده پیدا نشد', 'bad_reply');
    }
    for (const [mid, role] of [[mediaId, 'main'], [thumbId, 'thumb']]) {
      if (!mid) continue;
      //  فقط رسانه‌ای که همین طرف در همین گفت‌وگو بارگذاری کرده و نوعش می‌خواند
      const m = (await c.query(
        'SELECT kind FROM shop_chat_media WHERE id=$1 AND conversation_id=$2 AND uploader=$3 AND role=$4',
        [mid, conv.id, viewer.side, role]
      )).rows[0];
      if (!m || (role === 'main' ? m.kind !== kind : m.kind !== 'image')) {
        throw badRequest('رسانهٔ این پیام پیدا نشد یا نوعش نمی‌خواند', 'bad_media');
      }
    }
    const t = now();
    const ctr = (await c.query(
      `UPDATE shop_chat_conversations
          SET seq = seq + 1, rev = rev + 1, last_message_at = $2, last_sender = $3, updated_at = $2, status = 'open'
        WHERE id = $1 RETURNING seq, rev`,
      [conv.id, t, viewer.side]
    )).rows[0];
    const row = (await c.query(
      `INSERT INTO shop_chat_messages (id, conversation_id, shop_id, seq, rev, sender, sender_user_id, client_id,
          request_hash, kind, body, reply_to, media_id, thumb_id, meta, created_at)
       VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12,$13,$14,$15,$16) RETURNING *`,
      [newId('scm'), conv.id, conv.shop_id, n(ctr.seq), n(ctr.rev), viewer.side,
        viewer.side === 'shop' ? viewer.userId : '', clientId, hash, kind, text, replyTo, mediaId, thumbId,
        JSON.stringify(meta), t]
    )).rows[0];
    return { conv, row, replayed: false };
  });

  if (!out.replayed) {
    notify(out.conv.id);
    await pushNew(out.conv, out.row);
  }
  const full = await messageRow(out.row.id, viewer);
  return { message: messageView(viewer, full, await rowOf(viewer, id)), replayed: out.replayed };
}

/**
 * پوشِ پیامِ تازهٔ مشتری به دستگاه‌های دکان — یک بار برای هر پیام (تکرارِ
 * ارسال به این‌جا نمی‌رسد) و **بی متن**: محتوای چت از FCM نمی‌گذرد.
 * مشتریِ ویترین هنوز کانالِ پوش ندارد (قدمِ بعد: web-push ِ ویترین).
 */
async function pushNew(conv, row) {
  if (row.sender !== 'customer') return;
  try {
    await require('./push').sendTo({ shopId: conv.shop_id, app: 'shop' }, {
      title: 'پیامِ تازهٔ مشتری',
      body: 'یک پیامِ تازه در چتِ دکان دارید',
      data: { type: 'shop_chat', conversationId: conv.id },
    });
  } catch (err) {
    console.error('[shop-chat:push]', err.message);
  }
}

const MESSAGE_COLS = `m.*, mm.mime AS media_mime, mm.size AS media_size, tm.size AS thumb_size,
  (h.message_id IS NOT NULL) AS hidden`;
const MESSAGE_JOINS = `LEFT JOIN shop_chat_media mm ON mm.id = m.media_id AND mm.conversation_id = m.conversation_id
  LEFT JOIN shop_chat_media tm ON tm.id = m.thumb_id AND tm.conversation_id = m.conversation_id`;

async function messageRow(messageId, viewer) {
  return one(
    `SELECT ${MESSAGE_COLS} FROM shop_chat_messages m ${MESSAGE_JOINS}
       LEFT JOIN shop_chat_hidden h ON h.message_id = m.id AND h.viewer = $2
      WHERE m.id = $1`,
    [messageId, viewerKey(viewer)]
  );
}

/**
 * تغییرها پس از `after` (rev) — همان راهِ بازیابی پس از بسته شدنِ برنامه، قطعِ
 * اینترنت یا افتادنِ سوکت. `wait` (میلی‌ثانیه، حداکثر ۲۵ ثانیه): اگر چیزی تازه
 * نبود، تا رسیدنِ پیام/رسید یا پایانِ مهلت صبر کن. `signal` (قطعِ اتصال) ⇒ صبر
 * همان دم آزاد و پاسخ `null` (کسی برای شنیدن نیست).
 */
async function changes(viewer, id, { after, limit, wait, signal = null } = {}) {
  const a = intIn(after, { min: 0, def: 0 });
  const lim = intIn(limit, { min: 1, max: MAX_PAGE, def: 100 });
  const w = Math.min(MAX_WAIT_MS, intIn(wait, { min: 0, max: 60_000, def: 0 }));
  const conv = await rowOf(viewer, id);
  const fetch = () => many(
    `SELECT ${MESSAGE_COLS} FROM shop_chat_messages m ${MESSAGE_JOINS}
       LEFT JOIN shop_chat_hidden h ON h.message_id = m.id AND h.viewer = $3
      WHERE m.conversation_id = $1 AND m.rev > $2 ORDER BY m.rev ASC LIMIT $4`,
    [conv.id, a, viewerKey(viewer), lim + 1]
  );
  //  ⛔ گوش دادن پیش از خواندن (`chat-wait.js`): پیامِ میانِ خواندن و صبر گم نمی‌شود (CHAT-15)
  const ear = w > 0 ? listen(conv.id, w, signal) : null;
  let rows;
  try {
    rows = await fetch();
    if (ear && !rows.length) {
      await ear.promise;
      if (signal && signal.aborted) return null;
      rows = await fetch();
    }
  } finally {
    if (ear) ear.cancel();
  }
  const page = rows.slice(0, lim);
  const fresh = await viewRow(viewer, conv.id);
  return {
    conversation: conversationView(viewer, fresh),
    messages: page.map((r) => messageView(viewer, r, fresh)),
    cursor: page.length ? n(page[page.length - 1].rev) : a,
    hasMore: rows.length > lim,
  };
}

async function lockedMessage(c, conv, messageId) {
  const mid = String(messageId || '');
  if (!ID_RE.test(mid)) throw notFound('این پیام پیدا نشد', 'message_not_found');
  const m = (await c.query(
    'SELECT * FROM shop_chat_messages WHERE id=$1 AND conversation_id=$2 FOR UPDATE', [mid, conv.id]
  )).rows[0];
  if (!m) throw notFound('این پیام پیدا نشد', 'message_not_found');
  return m;
}

async function bumpRev(c, convId) {
  return n((await c.query('UPDATE shop_chat_conversations SET rev = rev + 1 WHERE id=$1 RETURNING rev', [convId])).rows[0].rev);
}

/** ویرایشِ پیامِ خود، در پنجرهٔ `editWindowMs`. تاریخچهٔ ویرایش نگه داشته نمی‌شود. */
async function edit(viewer, id, messageId, body) {
  const text = cleanText(obj(body).body);
  const out = await tx(async (c) => {
    const conv = await rowOf(viewer, id, { c, lock: true });
    const m = await lockedMessage(c, conv, messageId);
    if (m.deleted_at) throw notFound('این پیام پاک شده است', 'message_not_found');
    if (!isAuthor(viewer, m)) throw forbidden('فقط نویسندهٔ پیام می‌تواند ویرایشش کند', 'not_author');
    if (now() - n(m.created_at) > config.shopChat.editWindowMs) throw forbidden('مهلتِ ویرایشِ این پیام گذشته است', 'edit_window_passed');
    if (m.kind === 'text' && !text) throw badRequest('پیام خالی است', 'empty_message');
    if (m.body === text) return { changed: false };
    const rev = await bumpRev(c, conv.id);
    await c.query('UPDATE shop_chat_messages SET body=$2, edited_at=$3, rev=$4 WHERE id=$1', [m.id, text, now(), rev]);
    return { changed: true, convId: conv.id };
  });
  if (out.changed) notify(out.convId);
  const conv = await rowOf(viewer, id);
  return { message: messageView(viewer, await messageRow(String(messageId), viewer), conv) };
}

/**
 * حذف.
 *   `me`  ⇒ فقط از نمای همین بیننده (همهٔ دستگاه‌های همان مشتری یا همان کارمند).
 *   `all` ⇒ متن و رسانه همان لحظه از سرور می‌روند و فقط سنگِ قبر (شناسه، seq،
 *           زمان، فرستنده) می‌ماند تا همهٔ دستگاه‌ها — آنلاین یا بعداً — پاکش کنند.
 *           نویسنده در پنجرهٔ `deleteWindowMs`؛ `chat.moderate` هر پیامِ طرفِ
 *           دکان را هر وقت. پیامِ مشتری را دکان «برای همه» پاک نمی‌کند (بلاک و
 *           گزارش راهِ آن است). رونوشتِ پیامِ گزارش‌شده در گزارش می‌ماند.
 */
async function remove(viewer, id, messageId, scope) {
  const sc = scope === 'all' ? 'all' : scope === 'me' ? 'me' : null;
  if (!sc) throw badRequest('حذف برای من یا برای همه؟', 'bad_scope');
  const out = await tx(async (c) => {
    const conv = await rowOf(viewer, id, { c, lock: true });
    const m = await lockedMessage(c, conv, messageId);
    if (sc === 'me') {
      const ins = (await c.query(
        'INSERT INTO shop_chat_hidden (message_id, viewer, created_at) VALUES ($1,$2,$3) ON CONFLICT DO NOTHING RETURNING message_id',
        [m.id, viewerKey(viewer), now()]
      )).rows[0];
      if (!ins) return { changed: false };
      //  مشتری تنها بینندهٔ طرفِ خودش است ⇒ پیامِ پنهان‌شده سنجاقِ او هم نیست
      if (viewer.side === 'customer') {
        await c.query("DELETE FROM shop_chat_pins WHERE conversation_id=$1 AND side='customer' AND message_id=$2", [conv.id, m.id]);
      }
      const rev = await bumpRev(c, conv.id);
      await c.query('UPDATE shop_chat_messages SET rev=$2 WHERE id=$1', [m.id, rev]);
      return { changed: true, convId: conv.id };
    }
    if (m.deleted_at) return { changed: false };
    const moderator = viewer.side === 'shop' && m.sender === 'shop' && can(viewer.role, 'chat.moderate');
    if (!isAuthor(viewer, m) && !moderator) throw forbidden('این پیام را نمی‌توانید برای همه پاک کنید', 'delete_not_allowed');
    if (!moderator && now() - n(m.created_at) > config.shopChat.deleteWindowMs) {
      throw forbidden('مهلتِ حذف برای همه گذشته است', 'delete_window_passed');
    }
    const rev = await bumpRev(c, conv.id);
    const t = now();
    await c.query(
      `UPDATE shop_chat_messages SET body='', meta='{}'::jsonb, media_id=NULL, thumb_id=NULL,
              deleted_at=$2, deleted_by=$3, rev=$4 WHERE id=$1`,
      [m.id, t, viewerKey(viewer), rev]
    );
    await c.query('DELETE FROM shop_chat_pins WHERE message_id=$1', [m.id]);
    //  رسانه‌ای که دیگر هیچ پیامِ زنده‌ای به آن اشاره نمی‌کند همین حالا می‌رود
    for (const mid of [m.media_id, m.thumb_id].filter(Boolean)) {
      await c.query(
        `DELETE FROM shop_chat_media x WHERE x.id=$1 AND NOT EXISTS (
           SELECT 1 FROM shop_chat_messages y WHERE y.media_id = x.id OR y.thumb_id = x.id)`,
        [mid]
      );
    }
    return { changed: true, convId: conv.id, deletedAll: true, shopId: conv.shop_id };
  });
  if (out.changed) notify(out.convId);
  if (out.deletedAll && viewer.side === 'shop') {
    await require('./audit').log({
      shopId: out.shopId, userId: viewer.userId, action: 'chat.message_deleted',
      targetType: 'chat_message', targetId: String(messageId),
    });
  }
  const conv = await rowOf(viewer, id);
  return { message: messageView(viewer, await messageRow(String(messageId), viewer), conv) };
}

/**
 * سنجاق / برداشتنِ سنجاق (§۳۳.۱، CHAT-14). هر طرف سنجاقِ خودش را دارد: سنجاقِ
 * دکان را همهٔ کارمندانِ همان دکان می‌بینند و مشتری نه؛ سنجاقِ مشتری فقط برای
 * خودش. فقط پیامِ زندهٔ همین گفت‌وگو که از دیدِ همین بیننده پنهان نشده (پیامِ
 * گفت‌وگو یا دکانِ دیگر، پاک‌شده یا «حذف برای من» ⇒ همان ۴۰۴). حداکثر `MAX_PINS`
 * برای هر طرف، زیرِ قفلِ ردیفِ گفت‌وگو. تکرار بی‌اثر است. ⛔ چیزی به طرفِ دیگر
 * فرستاده نمی‌شود (نه پیام، نه پوش)؛ دستگاه‌های دیگرِ همان طرف با long-poll می‌فهمند.
 */
async function pin(viewer, id, messageId, on) {
  const out = await tx(async (c) => {
    const conv = await rowOf(viewer, id, { c, lock: true });
    const m = await lockedMessage(c, conv, messageId);
    if (!on) {
      const r = await c.query('DELETE FROM shop_chat_pins WHERE conversation_id=$1 AND side=$2 AND message_id=$3',
        [conv.id, viewer.side, m.id]);
      return { changed: r.rowCount > 0, convId: conv.id };
    }
    const hidden = (await c.query('SELECT 1 FROM shop_chat_hidden WHERE message_id=$1 AND viewer=$2',
      [m.id, viewerKey(viewer)])).rows[0];
    if (m.deleted_at || hidden) throw notFound('این پیام پیدا نشد', 'message_not_found');
    const have = (await c.query('SELECT message_id FROM shop_chat_pins WHERE conversation_id=$1 AND side=$2',
      [conv.id, viewer.side])).rows;
    if (have.some((x) => x.message_id === m.id)) return { changed: false, convId: conv.id };
    if (have.length >= MAX_PINS) throw conflict(`حداکثر ${MAX_PINS} پیام سنجاق می‌شود؛ اول یکی را بردارید`, 'pin_limit');
    await c.query(
      `INSERT INTO shop_chat_pins (conversation_id, message_id, side, shop_id, pinned_by_user_id, created_at)
       VALUES ($1,$2,$3,$4,$5,$6)`,
      [conv.id, m.id, viewer.side, conv.shop_id, viewer.side === 'shop' ? viewer.userId : '', now()]
    );
    return { changed: true, convId: conv.id };
  });
  if (out.changed) notify(out.convId);
  return viewOf(viewer, out.convId);
}

/** رسید: «تا seq ِ X رسید/خواندم» — فقط جلو، و نه جلوتر از آخرین پیام. */
async function receipts(viewer, id, body) {
  const b = obj(body);
  const read = intIn(b.readSeq, { min: 0, def: 0 });
  const delivered = Math.max(read, intIn(b.deliveredSeq, { min: 0, def: 0 }));
  const conv = await rowOf(viewer, id);
  const col = viewer.side === 'shop' ? 'shop' : 'customer';
  const r = await one(
    `UPDATE shop_chat_conversations
        SET ${col}_delivered_seq = GREATEST(${col}_delivered_seq, LEAST($2::bigint, seq)),
            ${col}_read_seq      = GREATEST(${col}_read_seq,      LEAST($3::bigint, seq))
      WHERE id = $1
      RETURNING ${col}_delivered_seq AS d, ${col}_read_seq AS r`,
    [conv.id, delivered, read]
  );
  if (n(r.d) !== n(conv[`${col}_delivered_seq`]) || n(r.r) !== n(conv[`${col}_read_seq`])) notify(conv.id);
  return { conversation: await viewOf(viewer, conv.id) };
}

// ════════════════════════════════════════════════════════════════════
//  رسانه
// ════════════════════════════════════════════════════════════════════

function capOf(kind, role) {
  const s = config.shopChat;
  if (role === 'thumb') return s.thumbMax;
  return kind === 'image' ? s.imageMax : kind === 'audio' ? s.audioMax : s.fileMax;
}

const kb = (bytes) => `${Math.round(bytes / 1024)} کیلوبایت`;

/** بارگذاری — نوع از روی بایت؛ EXIF ِ عکس پیش از ذخیره پاک؛ تکرارِ همان بایت ⇒ همان ردیف. */
async function putMedia(viewer, id, buf, { role = 'main' } = {}) {
  const ro = role === 'thumb' ? 'thumb' : role === 'main' || !role ? 'main' : null;
  if (!ro) throw badRequest('نقشِ رسانه نامعتبر است', 'bad_media');
  const conv = await rowOf(viewer, id);
  if (viewer.side === 'customer' && (await blocksOf(conv.shop_id, conv.customer_phone)).shop) {
    throw forbidden('این دکان پیامِ شما را نمی‌پذیرد', 'chat_blocked');
  }
  if (!Buffer.isBuffer(buf) || !buf.length) throw badRequest('فایل خالی است', 'bad_media');
  const s = chatMedia.sniff(buf);
  if (!s) throw new ApiError(415, 'unsupported_media', 'فقط عکس (JPG، PNG، WebP)، پیامِ صوتی (Opus، AAC، MP3، AMR) و PDF');
  if (ro === 'thumb' && s.kind !== 'image') throw new ApiError(415, 'unsupported_media', 'پیش‌نمایش باید عکس باشد');
  const cap = capOf(s.kind, ro);
  if (buf.length > cap) throw new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${kb(cap)} است`);
  const data = s.kind === 'image' ? chatMedia.stripImageMetadata(buf, s.mime) : buf;
  const hash = sha256(data);
  const ins = await one(
    `INSERT INTO shop_chat_media (id, shop_id, conversation_id, uploader, uploader_user_id, role, kind, mime, size, sha256, data, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10,$11,$12)
     ON CONFLICT (conversation_id, uploader, role, sha256) DO NOTHING RETURNING id, kind, mime, size, role`,
    [newId('scf'), conv.shop_id, conv.id, viewer.side, viewer.side === 'shop' ? viewer.userId : '',
      ro, s.kind, s.mime, data.length, hash, data, now()]
  );
  const row = ins || await one(
    'SELECT id, kind, mime, size, role FROM shop_chat_media WHERE conversation_id=$1 AND uploader=$2 AND role=$3 AND sha256=$4',
    [conv.id, viewer.side, ro, hash]
  );
  return { mediaId: row.id, kind: row.kind, mime: row.mime, size: n(row.size), role: row.role, reused: !ins };
}

/**
 * یک رسانه از دیدِ بیننده: فقط در گفت‌وگوی خودش، و فقط اگر خودش فرستاده یا
 * پیامِ زنده‌ای به آن اشاره می‌کند. نبود ⇒ ۴۰۴ ِ `media_gone`.
 */
async function getMedia(viewer, id, mediaId) {
  const conv = await rowOf(viewer, id);
  const mid = String(mediaId || '');
  const lost = () => notFound('این فایل دیگر روی سرور نیست', 'media_gone');
  if (!ID_RE.test(mid)) throw lost();
  const m = await one('SELECT * FROM shop_chat_media WHERE id=$1 AND conversation_id=$2', [mid, conv.id]);
  if (!m) throw lost();
  if (m.uploader !== viewer.side) {
    const ref = await one(
      `SELECT 1 AS ok FROM shop_chat_messages WHERE conversation_id=$1 AND deleted_at IS NULL
          AND (media_id=$2 OR thumb_id=$2) LIMIT 1`,
      [conv.id, mid]
    );
    if (!ref) throw lost();
  }
  return { id: m.id, mime: m.mime, size: n(m.size), kind: m.kind, data: Buffer.from(m.data) };
}

/** بایت‌ها به بیننده — هرگز صفحه نمی‌شود، کش نمی‌شود؛ PDF فقط دانلود. */
function sendMedia(res, m) {
  res.set('Content-Type', m.mime);
  res.set('Content-Length', String(m.data.length));
  res.set('Cache-Control', 'private, no-store');
  res.set('X-Content-Type-Options', 'nosniff');
  res.set('Content-Security-Policy', "default-src 'none'; sandbox");
  res.set('Cross-Origin-Resource-Policy', 'same-origin');
  res.set('Content-Disposition', m.kind === 'file' ? `attachment; filename="chat-${m.id}.pdf"` : 'inline');
  res.end(m.data);
}

/** خوانندهٔ بدنهٔ خامِ بارگذاری — بزرگ‌تر از بزرگ‌ترین سقف پیش از خواندن رد می‌شود. */
function rawMedia(req, res, next) {
  const s = config.shopChat;
  const max = Math.max(s.imageMax, s.audioMax, s.fileMax, s.thumbMax);
  const tooBig = () => new ApiError(413, 'too_large', `فایل بزرگ‌تر از ${kb(max)} است`);
  const len = Number(req.headers['content-length']);
  if (Number.isFinite(len) && len > max) return next(tooBig());
  express.raw({ type: () => true, limit: max })(req, res, (err) => {
    if (err && err.type === 'entity.too.large') return next(tooBig());
    if (err) return next(badRequest('فایل درست نرسید', 'bad_media'));
    next();
  });
}

// ════════════════════════════════════════════════════════════════════
//  بلاک، گزارش، جست‌وجو
// ════════════════════════════════════════════════════════════════════

/** بلاک به‌ازای (دکان، شماره): دکان ⇒ مشتری؛ مشتری ⇒ دکان. خواندنِ گذشته آزاد می‌ماند. */
async function setBlock(viewer, id, on) {
  const conv = await rowOf(viewer, id);
  const blocker = viewer.side;
  if (on) {
    await query(
      `INSERT INTO shop_chat_blocks (shop_id, customer_phone, blocker, by_user_id, created_at)
       VALUES ($1,$2,$3,$4,$5) ON CONFLICT DO NOTHING`,
      [conv.shop_id, conv.customer_phone, blocker, viewer.side === 'shop' ? viewer.userId : '', now()]
    );
  } else {
    await query('DELETE FROM shop_chat_blocks WHERE shop_id=$1 AND customer_phone=$2 AND blocker=$3',
      [conv.shop_id, conv.customer_phone, blocker]);
  }
  if (viewer.side === 'shop') {
    await require('./audit').log({
      shopId: conv.shop_id, userId: viewer.userId, action: on ? 'chat.customer_blocked' : 'chat.customer_unblocked',
      targetType: 'chat_conversation', targetId: conv.id,
    });
  }
  notify(conv.id);
  return viewOf(viewer, conv.id);
}

/**
 * گزارشِ سوءاستفاده به مدیرِ سامانه — با رونوشتِ همان پیام در همان لحظه.
 * یک گزارش برای هر (پیام، طرف): تکرار ردیفِ دوم نمی‌سازد.
 */
async function report(viewer, id, body) {
  const b = obj(body);
  const reason = str(b.reason);
  if (!REASONS.includes(reason)) throw badRequest('دلیلِ گزارش نامعتبر است', 'bad_reason');
  const note = shortText(b.note, 500);
  const conv = await rowOf(viewer, id);
  let messageId = '';
  let snapshot = {};
  if (b.messageId) {
    messageId = str(b.messageId);
    const m = ID_RE.test(messageId) && await one(
      'SELECT * FROM shop_chat_messages WHERE id=$1 AND conversation_id=$2', [messageId, conv.id]
    );
    if (!m) throw notFound('این پیام پیدا نشد', 'message_not_found');
    if (m.sender === viewer.side) throw badRequest('پیامِ خودتان را گزارش نمی‌کنید', 'bad_report');
    snapshot = {
      sender: m.sender, senderUserId: m.sender_user_id || '', kind: m.kind, body: m.body,
      mediaId: m.media_id || '', createdAt: n(m.created_at), editedAt: m.edited_at == null ? null : n(m.edited_at),
      deleted: !!m.deleted_at,
    };
  }
  const ins = await one(
    `INSERT INTO shop_chat_reports (id, shop_id, conversation_id, message_id, reporter, reporter_user_id, reason, note, snapshot, created_at)
     VALUES ($1,$2,$3,$4,$5,$6,$7,$8,$9,$10)
     ON CONFLICT (conversation_id, message_id, reporter) DO NOTHING RETURNING id`,
    [newId('scr'), conv.shop_id, conv.id, messageId, viewer.side, viewer.side === 'shop' ? viewer.userId : '',
      reason, note, JSON.stringify(snapshot), now()]
  );
  return { reported: true, duplicate: !ins };
}

/** جست‌وجو در پیام‌های مجاز — پاک‌شده و «حذف برای من» نمی‌آیند (CHAT-14). */
async function search(viewer, q, { limit } = {}) {
  const term = shortText(q, 100);
  if (term.length < 2) throw badRequest('دست‌کم دو نویسه بنویسید', 'query_too_short');
  const lim = intIn(limit, { min: 1, max: 50, def: 30 });
  const args = [viewer.shopId, `%${term.replace(/[\\%_]/g, (ch) => `\\${ch}`)}%`, viewerKey(viewer)];
  let scope = '';
  if (viewer.side === 'customer') { args.push(viewer.phone); scope = ` AND c.customer_phone = $${args.length}`; }
  args.push(lim);
  const rows = await many(
    `SELECT m.id, m.conversation_id, m.seq, m.sender, m.sender_user_id, m.kind, m.body, m.created_at
       FROM shop_chat_messages m JOIN shop_chat_conversations c ON c.id = m.conversation_id
      WHERE m.shop_id = $1 AND c.shop_id = $1${scope} AND m.deleted_at IS NULL
        AND m.body ILIKE $2 ESCAPE '\\'
        AND NOT EXISTS (SELECT 1 FROM shop_chat_hidden h WHERE h.message_id = m.id AND h.viewer = $3)
      ORDER BY m.created_at DESC, m.id DESC LIMIT $${args.length}`,
    args
  );
  return {
    results: rows.map((r) => ({
      conversationId: r.conversation_id, id: r.id, seq: n(r.seq), sender: r.sender, kind: r.kind,
      body: String(r.body).slice(0, 200), createdAt: n(r.created_at),
      ...(viewer.side === 'shop' && r.sender === 'shop' ? { senderUserId: r.sender_user_id } : {}),
    })),
  };
}

// ════════════════════════════════════════════════════════════════════
//  گزارش‌ها برای مدیرِ سامانه
// ════════════════════════════════════════════════════════════════════

async function reportsForAdmin({ status = 'open', limit } = {}) {
  const st = ['open', 'reviewed', 'all'].includes(status) ? status : 'open';
  const lim = intIn(limit, { min: 1, max: 200, def: 50 });
  const rows = await many(
    `SELECT r.*, s.name AS shop_name FROM shop_chat_reports r JOIN shops s ON s.id = r.shop_id
      ${st === 'all' ? '' : 'WHERE r.status = $2'} ORDER BY r.created_at DESC LIMIT $1`,
    st === 'all' ? [lim] : [lim, st]
  );
  return rows.map((r) => ({
    id: r.id, shopId: r.shop_id, shopName: r.shop_name, conversationId: r.conversation_id,
    messageId: r.message_id || null, reporter: r.reporter, reason: r.reason, note: r.note,
    snapshot: asJson(r.snapshot), status: r.status, createdAt: n(r.created_at),
  }));
}

async function reviewReport(id) {
  const r = await one("UPDATE shop_chat_reports SET status='reviewed' WHERE id=$1 RETURNING id", [String(id || '')]);
  if (!r) throw notFound('این گزارش پیدا نشد', 'report_not_found');
  return { id: r.id, status: 'reviewed' };
}

// ════════════════════════════════════════════════════════════════════
//  پاک‌سازی
// ════════════════════════════════════════════════════════════════════

/**
 * ۱) رسانهٔ بارگذاری‌شده‌ای که پس از یک ساعت هنوز هیچ پیامی به آن اشاره
 *    نمی‌کند (ارسالِ لغوشده یا نیمه‌کاره) می‌رود — §۳۳.۲ بندِ ۵.
 * ۲) نگهداری: `retentionDays > 0` ⇒ پیام‌های کهنه‌تر از سرور می‌روند (نسخهٔ
 *    دستگاه‌ها می‌ماند). ⛔ پیش‌فرض ۰ = هیچ؛ عدد تصمیمِ صاحب محصول است.
 */
async function sweep({ at = now(), retentionDays = config.shopChat.retentionDays } = {}) {
  let expired = 0;
  if (Number(retentionDays) > 0) {
    const r = await query('DELETE FROM shop_chat_messages WHERE created_at < $1', [at - Number(retentionDays) * 24 * 3600 * 1000]);
    expired = r.rowCount || 0;
  }
  const o = await query(
    `DELETE FROM shop_chat_media x WHERE x.created_at < $1 AND NOT EXISTS (
       SELECT 1 FROM shop_chat_messages m WHERE m.media_id = x.id OR m.thumb_id = x.id)`,
    [at - ORPHAN_MS]
  );
  return { expired, orphans: o.rowCount || 0 };
}

module.exports = {
  FLAG_KEY, KINDS, REASONS, MAX_BODY,
  flagOf, list, open, viewOf, setStatus,
  send, changes, edit, remove, receipts,
  putMedia, getMedia, sendMedia, rawMedia,
  setBlock, report, search, pin, MAX_PINS,
  reportsForAdmin, reviewReport,
  sweep, maskPhone, waiting,
};
