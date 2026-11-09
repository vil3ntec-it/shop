'use strict';
/**
 * long-poll ِ چتِ مشتری ↔ دکان — فقط شتاب؛ هر پاسخ دوباره از دیتابیس خوانده
 * می‌شود و منبع همیشه `after=<rev>` است (v3 §۳۳.۲ بندِ ۷؛ CHAT-03/15).
 *
 * ⛔ گوش دادن **پیش از** خواندن: `listen` را اول بساز، بعد تغییرها را بخوان و
 * اگر چیزی نبود منتظرِ `promise` بمان (در پایان `cancel`). پیامی که میانِ
 * «خواندنِ خالی» و «شروعِ صبر» برسد بیدارباش را گم نمی‌کند — پیش‌تر صبر پس از
 * خواندن ثبت می‌شد و چنین پیامی تا پایانِ مهلت (حداکثر ۲۵ ثانیه) دیر می‌رسید.
 * ⛔ قطعِ اتصالِ کلاینت (`goneSignal`) صبر را همان دم آزاد می‌کند؛ صبرِ آویزان نمی‌ماند.
 * ⛔ صبر درونِ همین فرایند است؛ با چند نمونهٔ سرور (`CLUSTER_ENABLED`) بیدارباش با گذرگاهِ LISTEN/NOTIFY ِ
 *    `lib/cluster.js` به نمونه‌های دیگر هم می‌رسد (فقط شناسهٔ گفت‌وگو)، و پس از وصلِ دوبارهٔ گذرگاه همه
 *    بیدار می‌شوند تا دوباره بخوانند. منبع همچنان دیتابیس است؛ بیدارباشِ گم‌شده فقط تا پایانِ مهلت دیر می‌کند.
 */
const cluster = require('./cluster');

/** شناسهٔ گفت‌وگو ⇒ مجموعهٔ بیدارکننده‌ها */
const waiters = new Map();

/** هر چیزی در گفت‌وگو عوض شد (پیام، رسید، وضعیت، بلاک) ⇒ همهٔ منتظرها بیدار. */
function notify(convId) {
  cluster.publish('chat-wait', { c: String(convId) });
  wakeHere(convId);
}
function wakeHere(convId) {
  const set = waiters.get(convId);
  if (!set) return;
  waiters.delete(convId);
  for (const wake of [...set]) wake();
}

/**
 * گوش دادن به گفت‌وگو تا `ms` میلی‌ثانیه، یا تا `signal` (قطعِ اتصال).
 * @returns {{ promise: Promise<boolean>, cancel: () => void }} — `true` یعنی بیدارباش آمد
 */
function listen(convId, ms, signal = null) {
  let set = waiters.get(convId);
  if (!set) { set = new Set(); waiters.set(convId, set); }
  const own = set;
  let settle;
  const promise = new Promise((resolve) => { settle = resolve; });
  let open = true;
  let timer = null;
  const finish = (woke) => {
    if (!open) return;
    open = false;
    clearTimeout(timer);
    if (signal) signal.removeEventListener('abort', onAbort);
    own.delete(wake);
    if (!own.size && waiters.get(convId) === own) waiters.delete(convId);
    settle(woke);
  };
  const wake = () => finish(true);
  const onAbort = () => finish(false);
  timer = setTimeout(() => finish(false), ms);
  if (timer.unref) timer.unref();
  own.add(wake);
  if (signal) {
    if (signal.aborted) finish(false);
    else signal.addEventListener('abort', onAbort, { once: true });
  }
  return { promise, cancel: () => finish(false) };
}

cluster.subscribe('chat-wait', (d) => wakeHere(String(d.c || '')));
cluster.onResync(() => { for (const id of [...waiters.keys()]) wakeHere(id); });
/** شمارِ long-poll های منتظر — برای سنجش و آزمون. */
function waiting() {
  let k = 0;
  for (const set of waiters.values()) k += set.size;
  return k;
}

/** «کلاینت رفت»: پاسخ پیش از پایانِ نوشتن بسته شد (قطعِ شبکه، بستنِ صفحه). */
function goneSignal(res) {
  const ctl = new AbortController();
  res.on('close', () => { if (!res.writableFinished) ctl.abort(); });
  return ctl.signal;
}

/**
 * CHAT-04: رسیدِ طرفِ دیگر که **میانِ دو long-poll** نشست هیچ منتظری را بیدار نکرد، و پرسشِ بعدی جز پیامِ تازه چیزی
 * نمی‌دید ⇒ «رسید/خوانده شد» تا پایانِ مهلت (۲۵ ثانیه) دیر می‌رسید. کلاینت رسیدهای دانسته‌اش را می‌فرستد (`pd`/`pr`)؛
 * این‌جا **پس از** `listen` خوانده می‌شود (رسیدِ پس از این خواندن بیدارباش دارد) و جلوتر بودنش یعنی بی صبر برگرد.
 * بی `pd`/`pr` (برنامه‌های پیشین) ⇒ `null` و همان رفتار. عددِ ناجور ⇒ همان «نمی‌دانم».
 */
const PEERS = new Set(['shop', 'customer']);
function knownOf(q) {
  if (!q || (q.pd === undefined && q.pr === undefined)) return null;
  const [d, r] = [q.pd, q.pr].map((x) => (x === undefined ? 0 : Number(x)));
  return Number.isSafeInteger(d) && Number.isSafeInteger(r) && d >= 0 && r >= 0 ? { d, r } : null;
}
/** `peer` = ستونِ طرفِ دیگر (`peerOf(viewer.side)` ِ `shop-chat.js`)؛ جز این دو هیچ */
async function peerAhead(peer, convId, known) {
  if (!known || !PEERS.has(peer)) return false;
  const r = await require('../db').one(`SELECT ${peer}_delivered_seq AS d, ${peer}_read_seq AS r FROM shop_chat_conversations WHERE id=$1`, [convId]);
  return Boolean(r) && (Number(r.d) > known.d || Number(r.r) > known.r);
}

module.exports = { notify, listen, waiting, goneSignal, knownOf, peerAhead };
