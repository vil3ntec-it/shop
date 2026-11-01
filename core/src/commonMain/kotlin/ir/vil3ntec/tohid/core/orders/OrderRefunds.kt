package ir.vil3ntec.tohid.core.orders

/*
 *  بازپرداختِ سفارشِ آنلاینِ تحویل‌شده ⇒ سندِ برگشت در دفترِ دکان (RET-11، FIN-01، DATA-04).
 *
 *  ── شکافی که این پرونده می‌بندد ───────────────────────────────────────
 *  سفارشِ تحویل‌شده یک فروشِ عادیِ `o-<سفارش>` در دفترِ دکان دارد (`OrderFulfillment`).
 *  بازپرداختِ آن (کامل، جزئی یا پولِ برگشتهٔ درگاه) تا امروز هیچ اثری در دفتر نداشت: فروش
 *  کامل می‌ماند و گزارش و صندوق بیش از واقع نشان می‌دادند.
 *
 *  ── قرارداد ─────────────────────────────────────────────────────────
 *  سرور (`server/src/lib/order-ledger.js` ⇐ `refundEventsOf`) برای هر سفارش فهرستِ «رویدادهای
 *  بازپرداخت» را با کلیدِ پایدار می‌دهد:
 *    `r<seq>` ── هر ردیفِ دفترِ بازپرداخت (`online_order_refunds`، پرچمِ `shop.orders.refunds`)
 *    `full` ─── «بازپرداختِ کامل» بی ردیفِ دفتر (گذارِ ساده) یا پولِ برگشتهٔ درگاه
 *               (`payment_status = refunded`، PR ِ حساب‌پی) — هر دو **یک** کلید: دو بار نه.
 *  مبلغِ هر رویداد را سرور با سقفِ «کلِ سفارش» داده؛ این‌جا دوباره با سقفِ «ماندهٔ همان فروش
 *  در دفتر» (مرجوعی‌های دستیِ پیشین هم کم می‌شوند) بریده می‌شود.
 *
 *  ── یکتایی (دو دستگاه، تلاشِ دوباره، Sync v1) ──────────────────────────
 *  هر رویداد یک سندِ برگشت با شناسهٔ ثابتِ [docIdOf] و یک ردیفِ سابقه با [markIdOf] است
 *  (حتی وقتی چیزی برای برگشت نمانده: مبلغِ صفر ⇒ فقط سابقه). هر دستگاه **همان** ردیف‌ها را
 *  می‌سازد و Sync v1 روی هم می‌نشاندشان ⇒ یک بار. هیچ شمارندهٔ `$inc` (`returnedQty`) دست
 *  نمی‌خورد. سرور ردیفِ سابقه را می‌بیند و سفارش از فهرستِ «بازپرداختِ ثبت‌نشده» می‌رود.
 *
 *  ── کالا («کالا به انبار برگشت؟») ──────────────────────────────────────
 *  پیش‌فرض «نه» (سفارشِ تحویل‌شده: کالا پیشِ مشتری است). فقط قلم‌هایی که کاربر می‌زند به
 *  موجودی برمی‌گردند — هر قلم یک بار ([restockIdOf]). بازپرداختِ بی پرسش (سرور، دستگاهِ دیگر)
 *  ⇒ «برگشتِ کالا؟» تا کاربر جواب دهد ([pendingKeys]، تصمیم با [decisionIdOf]).
 *
 *  خالص است — نه شبکه، نه زمانِ سیستم، نه android.* — تا اندروید، کامپیوتر و آیفون همین را بگیرند.
 */
object OrderRefunds {

  /** کلیدِ «بازپرداختِ کامل» (گذارِ ساده یا پولِ برگشتهٔ درگاه) */
  const val FULL = "full"

  /** همان `REFUND_SOURCES` ِ سرور: ردیفِ دفتر، گذارِ «بازپرداختِ کامل»، بازپرداختِ درگاه */
  val SOURCES: List<String> = listOf("ledger", "status", "gateway")

  private val KEY = Regex("^(full|r[1-9][0-9]{0,8})$")

  /** کلیدِ ردیفِ دفترِ بازپرداخت با شمارهٔ [seq] */
  fun ledgerKey(seq: Int): String = "r$seq"

  fun isKey(key: String): Boolean = KEY.matches(key)

  /** سندِ برگشتِ رویدادِ [key] ِ سفارشِ [orderId] (`SaleReturn.docId`) */
  fun docIdOf(orderId: String, key: String): String = "o-$orderId-rf-$key"

  /** ردیفِ سابقه = نشانِ «این رویداد در دفتر نشست» (سرور همین را در `auditLog` می‌جوید) */
  fun markIdOf(orderId: String, key: String): String = docIdOf(orderId, key) + "-a"

  /** پیشوندِ همهٔ سندهای بازپرداختِ یک سفارش */
  fun docPrefixOf(orderId: String): String = "o-$orderId-rf-"

  /** کلیدِ رویداد از شناسهٔ سند — یا null */
  fun keyOfDoc(orderId: String, docId: String?): String? {
    val prefix = docPrefixOf(orderId)
    if (docId == null || !docId.startsWith(prefix)) return null
    return docId.removePrefix(prefix).takeIf { isKey(it) }
  }

  /** تصمیمِ «کالا به انبار برگشت؟» برای رویدادِ [key] (ردیفِ سابقه؛ «نه» هم یک تصمیم است) */
  fun decisionIdOf(orderId: String, key: String): String = "o-$orderId-rs-$key"

  /** ورودِ انبارِ اصلاحیِ قلمِ [lineNo] — هر قلم حداکثر یک بار به موجودی برمی‌گردد */
  fun restockIdOf(orderId: String, lineNo: Int): String = "o-$orderId-$lineNo-rs"

  /** حرکتِ انبارِ همان برگشت */
  fun restockMoveIdOf(orderId: String, lineNo: Int): String = restockIdOf(orderId, lineNo) + "m"

  /**
   *  ردیفِ برگشتِ همان قلم (`SaleReturn` با مبلغِ صفر و مقدار): گزارش بهای کالای برگشته را در روزِ
   *  برگشت پس می‌دهد و برگشتِ دستیِ بعدی همان مقدار را دوباره برنمی‌گرداند
   */
  fun restockReturnIdOf(orderId: String, lineNo: Int): String = restockIdOf(orderId, lineNo) + "r"

  /** یک رویدادِ بازپرداخت از سرور — [at] زمانِ سرور، روزِ گزارش و صندوق همان است */
  data class Event(val key: String, val amountMinor: Long, val at: Long, val source: String = "")

  /** کاری که دفترِ دکان برای رویدادِ [key] می‌کند: برگشتِ [amountMinor] (صفر ⇒ فقط سابقه) */
  data class Step(val key: String, val amountMinor: Long, val at: Long, val source: String)

  /** ترتیبِ رویدادها: زمان؛ هم‌زمان ⇒ ردیف‌های دفتر به ترتیبِ شماره، بعد `full` (همان ترتیبِ سرور) */
  private fun rank(key: String): Long = if (key == FULL) Long.MAX_VALUE else key.drop(1).toLong()

  /**
   *  رویدادهای هنوز ننشستهٔ [events] ⇒ گام‌ها. [finalMinor]: مبلغِ نهاییِ فروش؛
   *  [returnedMinor]: جمعِ برگشت‌های همین فروش در دفتر (دستی و آنلاین)؛ [applied]: کلیدهایی که
   *  سندشان هست. کلیدِ تکراری یا بدشکل ⇒ یک بار / هیچ؛ مبلغِ منفی ⇒ صفر. جمعِ گام‌ها هرگز از
   *  ماندهٔ فروش بیشتر نمی‌شود.
   */
  fun plan(finalMinor: Long, returnedMinor: Long, applied: Set<String>, events: List<Event>): List<Step> {
    val seen = HashSet<String>()
    val fresh = events.filter { isKey(it.key) && seen.add(it.key) && it.key !in applied }
      .sortedWith(compareBy<Event>({ it.at }, { rank(it.key) }))
    var left = maxOf(0L, finalMinor - maxOf(0L, returnedMinor))
    return fresh.map { e ->
      val take = minOf(left, maxOf(0L, e.amountMinor))
      left -= take
      Step(e.key, take, e.at, e.source)
    }
  }

  /** قلمِ فروشِ تحویل از نگاهِ «برگشتِ کالا؟» — [remaining]: مقدارِ هنوز برنگشته به واحدِ موجودی */
  data class Line(val lineNo: Int, val productId: String, val remaining: Double, val restocked: Boolean = false)

  /** قلم‌هایی که واقعاً به موجودی برمی‌گردند: فقط زده‌شده‌ها، کالادار، مانده‌دار و هنوز برنگشته */
  fun restockLines(lines: List<Line>, chosen: Set<Int>): List<Line> =
    lines.filter { it.lineNo in chosen && it.productId.isNotBlank() && !it.restocked && it.remaining > 1e-9 }

  /** رویدادهای بازپرداختِ نشسته (با مبلغ) که هنوز «کالا به انبار برگشت؟» نگرفته‌اند */
  fun pendingKeys(paidKeys: Collection<String>, decided: Set<String>): List<String> =
    paidKeys.filter { isKey(it) && it !in decided }.distinct().sortedBy { rank(it) }
}
