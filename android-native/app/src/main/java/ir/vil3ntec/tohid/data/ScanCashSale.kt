package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.CommandSequence
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanSaleRule

/**
 *  راهِ «ب» ِ پرداخت پس از اسکنِ پس‌زمینه — «فروشِ نقدی با کارتِ فرمان» (دستهٔ B، پیش‌فرض خاموش؛ منطقِ کارت و دنباله در
 *  `core/hw/ScanSale.kt`). خالص و مشترکِ گوشی و کامپیوتر؛ مرزِ پلتفرم (تنظیم، راز، دفتر، چاپ، همگام‌سازی) در
 *  `scan/ScanCashAndroid.kt`.
 *
 *  - فقط فاکتوری که با «نقد پرداخت شد» ِ **همین دستگاه** بسته شد و همهٔ دروازه‌ها باز است (کارت روشن، صندوق باز، کاربرِ
 *    صندوق با اجازهٔ فروش، همهٔ کدها کالای شناخته) ⇒ فروشِ **نقدی** از همان `SalesEngine.record` ِ صندوق: قیمتِ روز،
 *    تخفیفِ خودکارِ دکان، گردکردنِ نقدیِ PAY-09، موجودی، کالای منقضی، سریِ سند، زنجیره و همگام‌سازی؛ شناسه =
 *    `ScanSaleRule.key` ⇒ هرگز دو بار (POS-14). یادداشتِ فاکتور «ثبت از اسکنر» (یا «— از حافظه»).
 *  - هر چیزِ دیگر ⇒ **فاکتورِ نگه‌داشته** (راهِ «الف»، POS-12) با دلیل؛ فروش فقط با «پرداخت» ِ دستی. هرگز فروشِ بی‌صدا.
 *  - نقد فقط: این‌جا راهی برای قرض، مشتری، تخفیفِ دستی یا تسویهٔ ترکیبی نیست ([record] خودش `Checkout` ِ نقد می‌سازد).
 *  - کارتِ خاموش ⇒ [plan] هیچ نمی‌کند و همهٔ اسکن‌ها برای پرسشِ «N اسکن در انتظار» می‌مانند.
 */
object ScanCashSale {
  /** برچسبِ فاکتورِ نگه‌داشته‌ای که «نقد» خورده بود ولی ثبت نشد */
  const val HELD_LABEL = "از بارکدخوان — نقد گرفته شد"  // داده: برچسبِ ذخیره‌شدهٔ فاکتورِ نگه‌داشته

  /** فاکتورِ آمادهٔ ثبتِ نقدی */
  data class Ready(
    val id: String,
    val cart: List<SalesEngine.CartLine>,
    /** همهٔ اسکن‌های فاکتور (کالا، فرمان، کدِ نادیده) — پس از نوشتنِ دفتر برداشته می‌شوند */
    val scanIds: Set<String>,
    /** «ثبت از اسکنر» یا «ثبت از اسکنر — از حافظه» */
    val note: String,
    /** جمعِ قیمتِ روز پیش از تخفیفِ خودکار و گرد (برای نمایش) */
    val totalMinor: Long,
  )

  /** فاکتوری که به راهِ «الف» برمی‌گردد; [invoice] `null` ⇒ هیچ کالای شناخته‌ای ندارد و اسکن‌ها در صف می‌مانند */
  data class Hold(
    val invoice: ScanSaleHold.Invoice?,
    val why: ScanSaleRule.Why,
    /** کدهای بی کالا — در یادداشتِ فاکتورِ نگه‌داشته */
    val unknown: List<String>,
    val scanIds: Set<String>,
  )

  data class Plan(
    val record: List<Ready> = emptyList(),
    val hold: List<Hold> = emptyList(),
    /** اسکن‌هایی که بی اثر برداشته می‌شوند: فرمانِ بی کالا و فاکتورِ پیش‌تر ثبت‌شده (واردکردنِ دوباره) */
    val drop: Set<String> = emptySet(),
    /** کدهای فضای‌نامِ فرمان که نادیده ماندند (جعلی یا کارتِ کهنه) — در فاکتورهای همین طرح؛ یادداشت می‌شوند */
    val ignored: List<QueuedScan> = emptyList(),
  )

  /** آنچه صندوقِ همین دستگاه می‌دهد — بی روشِ پرداخت و مشتری: این راه فقط نقد است */
  data class Till(
    /** کاربرِ صندوقِ واردشده (`TillUser.me`) */
    val seller: String,
    /** PAY-09: پلهٔ گردکردنِ نقدیِ همین دستگاه (`CashRoundPref.step`) */
    val cashRounding: Long,
    /** `ShopPolicy.blockExpired` */
    val blockExpired: Boolean,
    /** POS-37: `SaleTaxPref.active` (پرچمِ سرور و تنظیمِ دکان)؛ `null` ⇒ بی مالیات، همان امروز */
    val tax: ir.vil3ntec.tohid.core.sale.SaleTax.Config? = null,
  )

  /**
   *  صفِ در انتظار ⇒ چه ثبت شود، چه نگه‌داشته شود، چه برداشته شود. فقط فاکتورهای «نقد»‌خورده؛ فاکتورِ باز (هنوز
   *  اسکن می‌شود) و «فاکتورِ تازه» ِ بی پرداخت برای پرسشِ «N اسکن در انتظار» می‌مانند (`ScanSaleHold.plan`).
   */
  fun plan(d: ShopData, pending: List<QueuedScan>, gate: ScanSaleRule.Gate, commandOf: (String) -> CommandCard.Command?): Plan {
    if (!gate.cardOn || pending.isEmpty()) return Plan()
    val split = CommandSequence.split(pending, ScanSaleRule.MAX_GAP_MS, commandOf)
    val record = ArrayList<Ready>()
    val hold = ArrayList<Hold>()
    val drop = LinkedHashSet<String>()
    split.stray.forEach { drop += it.id }
    for (batch in split.batches) {
      if (!batch.cashClosed) continue
      val id = ScanSaleRule.key(batch)
      val ids = batch.span.map { it.id }.toSet()
      //  همان فاکتور پیش‌تر ثبت شده (برنامه پس از نوشتن و پیش از برداشتن بسته شد، یا بسته دوباره آمد) ⇒ فقط برداشتن
      if (SaleDraftRules.posted(d, id)) { drop += ids; continue }
      val (lines, missing) = ScanSaleHold.lines(d, batch.items)
      val why = ScanSaleRule.why(batch, gate, missing.size)
      if (why == null) {
        record += Ready(id, lines.map { SalesEngine.CartLine(it.productId, it.qty.toDouble()) }, ids, ScanSaleRule.note(batch), lines.sumOf { it.totalMinor })
      } else {
        val inv = if (lines.isEmpty()) null else ScanSaleHold.Invoice(id, lines, lines.sumOf { it.totalMinor }, ids)
        hold += Hold(inv, why, missing.map { it.code }, ids)
      }
    }
    val handled = drop + record.flatMap { it.scanIds } + hold.filter { it.invoice != null }.flatMap { it.scanIds }
    val ignored = split.notes.filter { it.first == CommandSequence.Note.FOREIGN_CODE && it.second.id in handled }.map { it.second }
    return Plan(record, hold, drop, ignored)
  }

  /** `Checkout` ِ این راه — همیشه نقدِ کامل، بی مشتری، بی تخفیفِ دستی، بی تسویهٔ ترکیبی */
  fun checkout(till: Till, note: String): SalesEngine.Checkout = SalesEngine.Checkout(
    seller = till.seller,
    payment = SalesEngine.Payment.CASH,
    blockExpired = till.blockExpired,
    cashRounding = till.cashRounding,
    note = note,
    tax = till.tax, // POS-37
  )

  /**
   *  ثبتِ یک فاکتورِ آماده روی دفترِ همین لحظه ([cur]) با همان `SalesEngine.record` ِ صندوق. [promo] تخفیفِ خودکارِ
   *  دکان برای همین سبد (همان `PromoLive` ِ صندوق؛ خاموش ⇒ `null`). ردِ صندوق (کمبودِ موجودی، کالای غیرفعال، منقضی،
   *  قاعدهٔ دکان) همان پیام است و هیچ چیز نوشته نمی‌شود ⇒ نگه‌داشتن.
   */
  fun record(
    cur: ShopData,
    ready: Ready,
    till: Till,
    today: String,
    now: Long,
    newId: () -> String,
    series: String = "",
    receiptToken: String? = null,
    promo: (List<SalesEngine.CartLine>) -> PromoEngine.Outcome? = { null },
  ): SalesEngine.Result {
    if (SaleDraftRules.posted(cur, ready.id)) return SalesEngine.Result.Failed(SalesEngine.ALREADY_POSTED)
    val outcome = promo(ready.cart)
    val priced = PromoEngine.priced(ready.cart, outcome)
    return PromoEngine.stamp(
      SalesEngine.record(cur, priced, checkout(till, ready.note), today, now, newId, series, saleId = ready.id, receiptToken = receiptToken),
      outcome,
    )
  }

  /** فروش‌های «ثبت از اسکنر» در بازهٔ [from]..[to] (لغونشده) — برای گزارشِ X/Z و صندوق */
  data class Summary(val count: Int, val totalMinor: Long, val fromMemory: Int)

  fun summary(d: ShopData, from: Long, to: Long): Summary {
    val sales = d.sales.filter { it.status != "cancelled" && it.createdAt in from..to && ScanSaleRule.isScannerSale(it.note) }
    return Summary(sales.size, sales.sumOf { PayFlow.minor(it.finalTotal) }, sales.count { it.note == ScanSaleRule.NOTE_MEMORY })
  }
}
