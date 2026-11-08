package ir.vil3ntec.tohid.core.sale

import ir.vil3ntec.tohid.core.perm.PermCore

/**
 *  «قیمتِ خاصِ مشتری» — POS-18 (§۶.۱): برای مشتریِ برگزیده و کالای برگزیده قیمتِ جدا (مثلاً چای برای عمده‌خرِ همیشگی
 *  ۹۵ به جای ۱۰۰)، با بازهٔ اعتبارِ اختیاری. پول عددِ صحیح به یک‌صدمِ افغانی؛ قیمت برای **واحدِ پایهٔ** کالاست
 *  (مدلِ لیستِ قیمت، `PriceList.Prices`، بسته و واحدِ دوم ندارد).
 *
 *  ── ترتیبِ قیمتِ ردیف (همین یک تابع، [resolve]؛ گوشی و کامپیوتر از همین) ──
 *   ۱) قیمتِ دستیِ ردیف — فقط اگر نقش اجازه دارد ([resolve] ‹manualAllowed›)؛
 *   ۲) قیمتِ خاصِ همین مشتری برای همین کالا، اگر امروز در بازه است ([active])؛
 *   ۳) لیستِ پیش‌فرضِ مشتری (`Debtor.priceList`) — مگر صندوق‌دار لیستِ همین فاکتور را خودش عوض کرده باشد
 *      (`PriceList.State.byHand`، قاعدهٔ پیشینِ POS-18: انتخابِ دستیِ لیست برای همین فاکتور برنده است)؛
 *   ۴) لیستِ برگزیدهٔ فاکتور (`PriceList.State.list`)؛
 *   ۵) خرده.
 *  لیستی که برای کالا قیمتی جز خرده نمی‌دهد (عمدهٔ صفر) «خرده» است. تخفیفِ خودکار، تخفیفِ فاکتور، سقفِ تغییرِ قیمت و
 *  «تخفیفِ پنهان» (`SaleCaps`/`price-cap.js`) و برگهٔ تأیید **روی همین قیمت** مثلِ امروز می‌نشینند: قیمتِ خاص قیمتِ
 *  فهرست نیست، پس کاهشش زیرِ خرده/عمده همان کاهشِ ردیف است و از هیچ سقفی نمی‌گذرد.
 *
 *  ── ردیفِ سبد ([reprice]) ──
 *  ردیفی که فیش خرده، عمده یا همان قیمتِ خاصی است که صندوق خودش نشانده («از منبع») با مشتری/لیستِ تازه دوباره قیمت
 *  می‌خورد؛ فیِ دیگر دستی است. ردیفِ «ثابت» (قیمتِ لیستِ بستهٔ کامل، POS-21) هرگز دست نمی‌خورد — قیمتِ خاصِ واحد
 *  جای قیمتِ بسته را نمی‌گیرد. منطقِ خالص؛ بردارها: `core/test-vectors/customer-price-v1.json`.
 */
object CustomerPrice {

  const val MANUAL = "manual"
  const val SPECIAL = "special"
  const val CUSTOMER_LIST = "customer_list"
  const val INVOICE_LIST = "invoice_list"
  const val RETAIL = "retail"

  /** بیشترین ردیفِ قیمتِ خاصِ یک مشتری — فیلدِ قرض‌دار در یک op ِ همگام‌سازی (سقفِ ۶۴KB) جا شود */
  const val MAX_ENTRIES = 200

  /** بیشترین قیمت (یک‌صدم) — همان بازهٔ امنِ پول */
  const val MAX_PRICE_MINOR = 100_000_000_000L

  /** یک قیمتِ خاص: کالا، قیمتِ واحدِ پایه و بازهٔ اختیاری (`yyyy-MM-dd`، دو سر داخل) */
  data class Entry(val productId: String, val priceMinor: Long, val from: String? = null, val to: String? = null)

  private val DATE = Regex("^\\d{4}-\\d{2}-\\d{2}$")

  fun validDate(s: String?): Boolean = s == null || DATE.matches(s)

  /** امروز ([date]) در بازه است؟ سرِ خالی = باز؛ تاریخِ نادرست (ردیف یا امروز) ⇒ نه */
  fun active(e: Entry, date: String): Boolean {
    if (!DATE.matches(date) || !validDate(e.from) || !validDate(e.to)) return false
    if (e.priceMinor <= 0 || e.priceMinor > MAX_PRICE_MINOR) return false
    if (e.from != null && date < e.from) return false
    if (e.to != null && date > e.to) return false
    return true
  }

  /** قیمتِ خاصِ در کارِ این کالا امروز؛ هر کالا یک ردیف ([check])، پس نخستینِ فعال */
  fun find(entries: List<Entry>, productId: String, date: String): Entry? =
    entries.firstOrNull { it.productId == productId && productId.isNotEmpty() && active(it, date) }

  /** درستیِ یک ردیف پیش از نوشتن: `null` ⇒ درست؛ وگرنه کدِ رد */
  fun check(e: Entry, others: List<Entry>): String? = when {
    e.productId.isBlank() -> "product"
    e.priceMinor <= 0 || e.priceMinor > MAX_PRICE_MINOR -> "price"
    !validDate(e.from) || !validDate(e.to) -> "date"
    e.from != null && e.to != null && e.to < e.from -> "range"
    others.any { it.productId == e.productId } -> "duplicate"
    others.size >= MAX_ENTRIES -> "full"
    else -> null
  }

  /** فیِ ردیف و منبعش */
  data class Price(val unitMinor: Long, val source: String)

  /**
   *  ترتیبِ بالا. [manualMinor] = فیِ دستیِ ردیف (`null` ⇒ دستی نیست)؛ [special] = ردیفِ قیمتِ خاصِ همین کالا
   *  (فعال بودنش همین‌جا سنجیده می‌شود)؛ [customerList] = لیستِ پیش‌فرضِ مشتری (`null` ⇒ بی مشتری؛ `""` ⇒ خرده).
   */
  fun resolve(
    prices: PriceList.Prices,
    manualMinor: Long?,
    manualAllowed: Boolean,
    special: Entry?,
    customerList: String?,
    invoice: PriceList.State,
    date: String,
  ): Price {
    if (manualMinor != null && manualAllowed) return Price(manualMinor, MANUAL)
    if (special != null && active(special, date)) return Price(special.priceMinor, SPECIAL)
    val byCustomer = customerList != null && !invoice.byHand
    val u = PriceList.unitMinor(prices, if (byCustomer) customerList!! else invoice.list)
    return when {
      u == prices.retailMinor -> Price(prices.retailMinor, RETAIL)
      byCustomer -> Price(u, CUSTOMER_LIST)
      else -> Price(u, INVOICE_LIST)
    }
  }

  /**
   *  ردیفِ سبد: [unitMinor] `null` = قیمتِ کالا (خرده)؛ [specialMinor] = قیمتِ خاصی که صندوق روی همین ردیف نشانده؛
   *  [fixed] = قیمتِ منبعِ دیگر (لیستِ بسته) که دست نمی‌خورد.
   */
  data class Line(
    val key: String,
    val productId: String,
    val prices: PriceList.Prices,
    val unitMinor: Long?,
    val specialMinor: Long? = null,
    val fixed: Boolean = false,
  )

  data class Repriced(
    val lines: List<Line>,
    /** ردیف‌هایی که فیشان عوض شد */
    val changed: Int,
    /** ردیف‌های دستی که عمداً دست نخوردند (فقط آن‌ها که منبعِ تازه فیِ دیگری می‌داد) */
    val keptManual: Int,
    /** ردیف‌هایی که حالا قیمتِ خاص دارند */
    val special: Int,
  )

  /** این ردیف «از منبع» است (خرده، عمده یا قیمتِ خاصی که صندوق نشانده)؟ */
  fun fromSource(l: Line): Boolean =
    (l.specialMinor != null && l.unitMinor == l.specialMinor) || !PriceList.isManual(l.prices, l.unitMinor)

  /** همهٔ سبد با مشتری/لیستِ امروز؛ [entries] = قیمت‌های خاصِ مشتریِ همین فاکتور (بی مشتری ⇒ خالی) */
  fun reprice(
    lines: List<Line>,
    entries: List<Entry>,
    customerList: String?,
    invoice: PriceList.State,
    date: String,
    manualAllowed: Boolean,
  ): Repriced {
    var changed = 0
    var kept = 0
    var special = 0
    val out = lines.map { l ->
      if (l.fixed) return@map l
      val cur = l.unitMinor ?: l.prices.retailMinor
      val manual = if (fromSource(l)) null else l.unitMinor
      val entry = find(entries, l.productId, date)
      val p = resolve(l.prices, manual, manualAllowed, entry, customerList, invoice, date)
      if (p.source == MANUAL) {
        val target = resolve(l.prices, null, false, entry, customerList, invoice, date)
        if (target.unitMinor != cur) kept++
        return@map l
      }
      if (p.source == SPECIAL) special++
      if (p.unitMinor != cur) changed++
      val next = l.copy(
        unitMinor = if (p.source == RETAIL) null else p.unitMinor,
        specialMinor = if (p.source == SPECIAL) p.unitMinor else null,
      )
      if (next == l) l else next
    }
    return Repriced(out, changed, kept, special)
  }

  /**
   *  منبعِ قیمتِ قلمِ ثبت‌شده برای رسید و سابقه: `"special"` فقط وقتی فیِ ثبت‌شده همان قیمتِ خاصی است که صندوق روی
   *  ردیف نشانده بود؛ هر فیِ دیگر (دستی پس از آن، لیست) ⇒ `null` (هیچ فیلدی نوشته نمی‌شود).
   */
  fun stamp(specialMinor: Long?, soldUnitMinor: Long): String? =
    if (specialMinor != null && specialMinor == soldUnitMinor) SPECIAL else null

  /**
   *  چه کسی قیمتِ خاص می‌گذارد: همان کسی که قیمت می‌گذارد — نقشی که کالا (قیمتِ فروش) **و** مشتری را می‌نویسد
   *  (`PermCore.canWrite`) و `sales.price` اش «مجاز» است (نه «با تأیید»، نه «ممنوع»؛ صاحب برای هر نقش یا کاربر می‌بندد).
   *  پیش‌فرض: صاحب، مدیر و شاگرد بله؛ صندوق‌دار، انباردار، جمع‌آور و حسابدار نه. صاحب (نقشِ خالیِ دستگاهِ تنها) بله؛
   *  نقشِ ناشناخته نه.
   */
  fun canEdit(role: String, layers: List<Any?>?): Boolean {
    val r = role.trim().lowercase()
    if (r.isEmpty() || r == "owner") return true
    if (r !in PermCore.ROLES) return false
    return PermCore.canWrite(r, "products") && PermCore.canWrite(r, "debtors") &&
      PermCore.evaluate(r, layers, "sales.price").decision == "allow"
  }
}
