package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.core.net.ApiFailure

/**
 *  e-syncdiag — push ای که **کلِ** درخواستش بزرگ‌تر از سقفِ بدنهٔ سرور یا پراکسیِ جلویش است (۴۱۳ ِ `body_too_large`؛ یا
 *  `batch_too_large`): سرور هیچ opی را اعمال نکرده. پیش‌تر همان دسته هر دور همان ۴۱۳ را می‌گرفت، دور می‌شکست و opهای پشتِ آن
 *  هرگز نمی‌رفتند — یک تغییرِ بیش از اندازه همهٔ صف را تا ابد نگه می‌داشت (`SyncStuckQueueDesktopTest` «۲»).
 *
 *  ⛔ دستهٔ چند‌op ⇒ نیمِ آن (`OpQueue.batch(maxOps)`)، هیچ opی کنار نمی‌رود. دسته‌ای که فقط **یک** op دارد و باز ۴۱۳ می‌گیرد
 *  یعنی خودِ همان op (پاکتِ بی op چند صد بایت است) ⇒ همان راهِ ردشده‌ها (`SyncV1Engine.pushQueue`): اول دفترِ کنار با دلیل
 *  ([REASON])، بعد بیرون از صف — نوشتنِ دفترِ کنار نشد ⇒ پرتاب و op در صف می‌ماند (گم نمی‌شود). opِ دوباره‌فرستادهٔ §۹.۵
 *  (`V1Restore.isReplay`) همان رفتارِ ردِ آن‌ها را دارد: بی‌صدا بیرون. ⛔ هر شکستِ دیگر (۵xx، نشست، اشتراک…) پرتاب می‌شود و
 *  هیچ opی کنار نمی‌رود.
 */
internal object PushTooLarge {

  /** دلیلِ دفترِ کنار — همان کدِ ۴۱۳ ِ سرور (`server/src/app.js`) */
  const val REASON = "body_too_large"

  /**
   *  @return سقفِ دستهٔ بعدی: نیمِ همین دسته، یا پس از کنار گذاشتنِ تنها op ِ ۴۱۳ دوباره [OpQueue.MAX_OPS] (و [aside] صدا زده شد)
   *  @throws ApiFailure همان [big] وقتی ۴۱۳/`batch_too_large` نیست، یا تنها op ای که `batch_too_large` گرفت (گرهِ op نیست)
   */
  fun next(big: ApiFailure, batch: List<SyncOp>, queue: OpQueue, replay: (String) -> Boolean, aside: () -> Unit): Int {
    if (big.status != 413 && big.code != "batch_too_large") throw big
    if (batch.size > 1) return (batch.size / 2).coerceAtLeast(1)
    if (big.status != 413) throw big
    val op = batch.single()
    if (!replay(op.opId)) queue.drop(batch, REASON)
    queue.ack(listOf(op.opId))
    aside()
    return OpQueue.MAX_OPS
  }
}
