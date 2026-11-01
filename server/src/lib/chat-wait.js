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
 * ⛔ درونِ همین فرایند: سرورِ خانگی تک‌نمونه‌ای است (مثلِ سقفِ نرخ در `middleware/ratelimit`).
 */

/** شناسهٔ گفت‌وگو ⇒ مجموعهٔ بیدارکننده‌ها */
const waiters = new Map();

/** هر چیزی در گفت‌وگو عوض شد (پیام، رسید، وضعیت، بلاک) ⇒ همهٔ منتظرها بیدار. */
function notify(convId) {
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

module.exports = { notify, listen, waiting, goneSignal };
