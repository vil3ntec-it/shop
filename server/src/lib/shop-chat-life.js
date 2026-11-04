'use strict';
/**
 * چتِ مشتری ↔ دکان — یک رشته برای هر سفارش و چرخهٔ عمرِ آن (مهاجرتِ ۲۰۹). جدا از `lib/shop-chat.js` تا آن
 * زیرِ سقفِ اندازه بماند؛ تابع‌های آن‌جا (`rowOf`، `viewOf`، `viewSql`، `conversationView`، `blocksOf`) تزریق می‌شوند.
 *
 *   سفارش ── رشتهٔ همان سفارش (کلیدِ یکتای امروز: دکان، شماره، سفارش) ⇒ سفارشِ تازه رشتهٔ تازه.
 *            پایانِ سفارش (تحویل و پرداخت، یا پایانِ بی‌تحویل: رد، لغو، برگشت به انبار، بازپرداخت) ⇒ «بسته شد»:
 *            هر دو طرف فقط می‌خوانند. مشتری تا `issueWindowMs` (۷ روز) پس از پایان «مشکل دارم» می‌زند ⇒ **همان**
 *            رشته باز (برگشت و اختلاف)؛ پس از آن ۴۰۹ `issue_window_passed`. گذارِ تازهٔ سفارش پس از «مشکل دارم»
 *            (مثلاً بازپرداخت) ⇒ پایانِ تازه و دوباره بسته؛ رشتهٔ دوباره‌بازشدهٔ بی‌کار پس از `idleCloseMs` هم بسته.
 *   عمومی ── پرسشِ پیش از سفارش (`order_id = ''`)؛ `idleCloseMs` بی‌کاری ⇒ بسته. «گفت‌وگوی تازه» ِ مشتری (`open`
 *            بی سفارش) دورِ تازه‌ای در همان رشته می‌گشاید (`session_at`)؛ پیام‌های پیشین بالای خطِ دورِ تازه می‌مانند.
 *   سرور ── «بسته» فقط از خودِ سفارش و زمان حساب می‌شود (`lifeOf`، تابعِ خالص) و درِ نوشتن (پیام، ویرایش، رسانه)
 *            زیرِ قفلِ ردیفِ گفت‌وگو ۴۰۹ `chat_closed` می‌دهد — هیچ دکمهٔ پنهانی سد نیست. خواندن، «حذف برای من»،
 *            سنجاق، رسید، گزارش و بلاک باز می‌مانند. جداسازیِ دکان و مشتری همان `rowOf` است.
 *   صندوق ── وضعیتِ صندوقِ دکان (`status`) با رشتهٔ بسته هم‌گام می‌شود (`settle`) تا «باز»ها فقط رشته‌های زنده باشند.
 */
const config = require('../config');
const { many, query, now, tx } = require('../db');
const { notify } = require('./chat-wait');
const { badRequest, conflict, forbidden } = require('../middleware/errors');

/** تحویل‌شده — پایان فقط با پرداخت (نقد هنگامِ تحویل همان دمِ تحویل پرداخت است) */
const HANDED = Object.freeze(['delivered', 'picked_up']);
/** پایانِ بی‌تحویل یا پس از برگشت — همان وضعیت‌های پایانیِ `order-states.js` */
const ENDED = Object.freeze(['rejected', 'auto_rejected', 'cancelled_by_customer', 'cancelled', 'restocked', 'refunded_partial', 'refunded_full']);
const PAID = Object.freeze(['paid', 'partially_refunded', 'refunded']);
const SETTLE_MAX = 200;
const HISTORY_MAX = 50;
const list = (xs) => xs.map((x) => `'${x}'`).join(',');

/**
 * ستون‌های سفارشِ هر رشته برای نمای گفت‌وگو (`viewSql` با `LEFT JOIN online_orders o`): وضعیت، پرداخت و زمانِ
 * رسیدن به همین وضعیت/پرداخت (رویدادِ سفارش؛ نبود ⇒ `updated_at` ِ سفارش).
 */
const ORDER_COLS = `o.status AS order_status, o.payment_method AS order_payment_method,
      o.payment_status AS order_payment_status, o.updated_at AS order_updated_at,
      (SELECT MAX(e.created_at) FROM online_order_events e WHERE e.order_id = o.id AND e.shop_id = o.shop_id
         AND ((e.kind = 'status' AND e.to_status = o.status) OR (e.kind = 'payment' AND e.to_status IN (${list(PAID)})))) AS order_done_at`;

const num = (x) => (x === null || x === undefined ? null : Number(x));

/** سفارش تمام شده؟ ⇒ دلیل (`completed` | `ended`) یا ''. */
function doneReason(r) {
  const st = r.order_status;
  if (!st) return 'ended';   //  سفارش دیگر نیست
  if (ENDED.includes(st)) return 'ended';
  if (HANDED.includes(st) && (r.order_payment_method === 'cod' || PAID.includes(r.order_payment_status))) return 'completed';
  return '';
}

/**
 * چرخهٔ عمرِ یک رشته — تابعِ خالص روی ردیفِ نما (ستون‌های گفت‌وگو + `ORDER_COLS`).
 * ⇒ `{ kind, state: open|closed|reopened, reason, closedAt, issueUntil, reopenedAt, sessionAt, canIssue, canRestart }`
 */
function lifeOf(r, { at = now(), issueMs = config.shopChat.issueWindowMs, idleMs = config.shopChat.idleCloseMs } = {}) {
  const last = num(r.last_message_at) || 0;
  const issueAt = num(r.issue_opened_at);
  const sessionAt = num(r.session_at);
  const out = { kind: r.order_id ? 'order' : 'general', state: 'open', reason: '', closedAt: null, issueUntil: null, reopenedAt: issueAt, sessionAt };
  if (!r.order_id) {
    const until = Math.max(last, sessionAt || 0, num(r.created_at) || 0) + idleMs;
    if (at >= until) Object.assign(out, { state: 'closed', reason: 'idle', closedAt: until });
    return { ...out, reopenedAt: null, canIssue: false, canRestart: out.state === 'closed' };
  }
  const reason = doneReason(r);
  if (reason) {
    const doneAt = num(r.order_done_at) ?? num(r.order_updated_at) ?? num(r.updated_at) ?? at;
    out.issueUntil = r.order_status ? doneAt + issueMs : null;
    if (issueAt !== null && issueAt >= doneAt) {
      const until = Math.max(last, issueAt) + idleMs;
      if (at >= until) Object.assign(out, { state: 'closed', reason: 'idle', closedAt: until });
      else out.state = 'reopened';
    } else {
      Object.assign(out, { state: 'closed', reason, closedAt: doneAt });
    }
  } else if (issueAt !== null) {
    out.state = 'reopened';   //  «مشکل دارم» و سفارش هنوز در کار (مثلاً درخواستِ برگشت)
  }
  const canIssue = out.state === 'closed' && out.issueUntil !== null && at < out.issueUntil;
  return { ...out, canIssue, canRestart: false };
}

/**
 * نمای `life` برای هر طرف: دکان «مشکل دارم» نمی‌زند و دورِ تازهٔ گفت‌وگوی عمومی را نمی‌گشاید؛ `issueUntil` برای دکان فقط
 * تا وقتی مهلت باز است (پس از آن null) — برنامه ساعتِ خودش را نمی‌خواهد.
 */
function viewLife(viewer, l) {
  return viewer.side === 'customer' ? l : { ...l, canIssue: false, canRestart: false, issueUntil: l.canIssue ? l.issueUntil : null };
}

const closedError = (l) => Object.assign(
  conflict(l.kind === 'general'
    ? 'این گفت‌وگو پس از چند روز بی‌پیامی بسته شد؛ برای پرسشِ تازه «گفت‌وگوی تازه» را بزنید'
    : 'گفت‌وگوی این سفارش بسته شد و فقط خواندنی است', 'chat_closed'),
  { data: { life: l } }
);

module.exports = function lifeFor({ rowOf, viewOf, viewSql, conversationView, blocksOf }) {
  /** ردیفِ گفت‌وگو با ستون‌های سفارش — درونِ تراکنشِ نوشتن، پس از قفلِ ردیف */
  async function lifeIn(c, convId) {
    const sql = `SELECT c.*, ${ORDER_COLS} FROM shop_chat_conversations c
      LEFT JOIN online_orders o ON o.id = c.order_id AND o.shop_id = c.shop_id WHERE c.id = $1`;
    const r = c ? (await c.query(sql, [convId])).rows[0] : (await many(sql, [convId]))[0];
    return lifeOf(r);
  }

  /** درِ نوشتن (پیام، ویرایش، رسانه): رشتهٔ بسته ⇒ ۴۰۹ `chat_closed` برای هر دو طرف. */
  async function assertOpen(c, convId) {
    const l = await lifeIn(c, convId);
    if (l.state === 'closed') throw closedError(l);
  }

  /**
   * «مشکل دارم» ِ مشتری: فقط رشتهٔ سفارشِ بسته و فقط تا `issueUntil` ⇒ همان رشته باز. باز بودن ⇒ همان (بی‌اثر).
   * زیرِ قفلِ ردیف؛ دکان یک پوشِ بی‌متن می‌گیرد.
   */
  async function issue(viewer, id) {
    if (viewer.side !== 'customer') throw forbidden('«مشکل دارم» کارِ مشتری است', 'customer_only');
    const out = await tx(async (c) => {
      const conv = await rowOf(viewer, id, { c, lock: true });
      if (!conv.order_id) throw badRequest('«مشکل دارم» فقط برای گفت‌وگوی یک سفارش است', 'issue_order_only');
      if ((await blocksOf(conv.shop_id, conv.customer_phone, c)).shop) throw forbidden('این دکان پیامِ شما را نمی‌پذیرد', 'chat_blocked');
      const l = await lifeIn(c, conv.id);
      if (l.state !== 'closed') return { conv, changed: false };
      if (!l.canIssue) throw conflict('مهلتِ «مشکل دارم» برای این سفارش گذشته است', 'issue_window_passed');
      const t = now();
      await c.query("UPDATE shop_chat_conversations SET issue_opened_at=$2, status='open', rev = rev + 1, updated_at=$2 WHERE id=$1", [conv.id, t]);
      return { conv, changed: true };
    });
    if (out.changed) {
      notify(out.conv.id);
      try {
        await require('./push').sendTo({ shopId: out.conv.shop_id, app: 'shop' }, {
          title: 'مشتری «مشکل دارم» زد',
          body: 'گفت‌وگوی یک سفارشِ تمام‌شده دوباره باز شد',
          data: { type: 'shop_chat', conversationId: out.conv.id },
        });
      } catch (err) {
        console.error('[shop-chat:issue-push]', err.message);
      }
    }
    return { conversation: await viewOf(viewer, out.conv.id), reopened: out.changed };
  }

  /** `open` ِ مشتری بی سفارش روی رشتهٔ عمومیِ بسته ⇒ دورِ تازه در همان رشته (`session_at`). */
  async function restart(viewer, convId) {
    if (viewer.side !== 'customer') return;
    await tx(async (c) => {
      await rowOf(viewer, convId, { c, lock: true });
      const l = await lifeIn(c, convId);
      if (l.kind !== 'general' || l.state !== 'closed') return;
      await c.query("UPDATE shop_chat_conversations SET session_at=$2, status='open', rev = rev + 1, updated_at=$2 WHERE id=$1", [convId, now()]);
    });
    notify(convId);
  }

  /**
   * صندوقِ دکان: رشته‌های بسته‌ای که هنوز «باز»‌اند ⇒ `status = closed` (همان قاعدهٔ `lifeOf`، بی منطقِ دوم در SQL).
   * فقط همین دکان و حداکثر `SETTLE_MAX` ردیف در هر بار.
   */
  async function settle(shopId) {
    const rows = await many(
      `SELECT c.*, ${ORDER_COLS} FROM shop_chat_conversations c
        LEFT JOIN online_orders o ON o.id = c.order_id AND o.shop_id = c.shop_id
        WHERE c.shop_id = $1 AND c.status = 'open' ORDER BY c.updated_at ASC LIMIT ${SETTLE_MAX}`,
      [shopId]
    );
    const at = now();
    const ids = rows.filter((r) => lifeOf(r, { at }).state === 'closed').map((r) => r.id);
    if (ids.length) await query("UPDATE shop_chat_conversations SET status='closed' WHERE shop_id=$1 AND id = ANY($2::text[]) AND status='open'", [shopId, ids]);
  }

  /**
   * رشته‌های پیشینِ همین مشتری در همین دکان (سفارش‌ها و گفت‌وگوی عمومی) — تازه‌ترین بالا، بی خودِ این رشته.
   * فقط کارمندِ همین دکان (`rowOf` ⇒ ۴۰۴ برای دکانِ دیگر)؛ مشتری فهرستِ خودش را از `GET /conversations` دارد.
   */
  async function history(viewer, id) {
    const conv = await rowOf(viewer, id);
    const args = [viewer.shopId, require('./validate').phoneForms(conv.customer_phone), conv.id];
    let who = 'c.customer_phone = ANY($2::text[])';
    if (conv.customer_id) { args.push(conv.customer_id); who = `(${who} OR c.customer_id = $4)`; }
    const head = viewSql(viewer, args);   //  کلیدِ بیننده پس از $3/$4 (CHAT-08)
    const rows = await many(
      `${head} WHERE c.shop_id = $1 AND ${who} AND c.id <> $3 AND (c.seq > 0 OR c.order_id <> '')
        ORDER BY c.created_at DESC, c.id DESC LIMIT ${HISTORY_MAX}`,
      args
    );
    return { conversations: rows.map((r) => conversationView(viewer, r)) };
  }

  return { ORDER_COLS, lifeOf, viewLife, assertOpen, issue, restart, settle, history };
};

module.exports.lifeOf = lifeOf;
module.exports.ORDER_COLS = ORDER_COLS;
module.exports.HANDED = HANDED;
module.exports.ENDED = ENDED;
