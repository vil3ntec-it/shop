package ir.vil3ntec.tohid.core.stock

/**
 *  فروشِ پیش از شمارش که پس از ثبتِ شمارش می‌رسد — INV-10 (§۶.۵، S20)، همزادِ `license/count-absorb-core.js`.
 *
 *  دستگاهِ A آفلاین ۳ فروخت (۱۰:۰۰)؛ دستگاهِ B ساعتِ ۱۰:۳۰ قفسه را شمرد: ۱۷ (عددِ درست، پس از همان فروش). B فروشِ A را
 *  نداشت، پس «اصلاحِ شمارش» ِ او −۳ است (۲۰ ⇐ ۱۷). وقتی فروشِ A می‌رسد، جمعِ دفتر ۱۴ می‌شود: همان ۳ دو بار کم شد.
 *  حرکتی که لحظهٔ خودش (نه لحظهٔ رسیدن) پیش از لحظهٔ شمردنِ همان کالا یا هم‌لحظه با آن است در عددِ شمرده هست؛ پس
 *  موجودی را دوباره عوض نمی‌کند — ولی در دفتر، گزارش، پول و همه‌جای دیگر همان می‌ماند.
 *
 *  «ردیفِ شمارش» دانسته‌اش را با خودش می‌برد: [Line.seenMilli] = جمعِ نشانه‌دارِ حرکت‌هایی از همان کالا که دستگاهِ
 *  شمارنده **هنگامِ ثبت** داشت و در پنجرهٔ شمارش بودند ([seen]). هر دستگاه و سرور همان پنجره را روی حرکت‌هایی که
 *  امروز دارد می‌شمارد؛ تفاوت = حرکت‌های پیش از شمارشی که شمارنده ندیده بود ⇒ «جذب‌شده» ([Gap.gapMilli]) و موجودی =
 *  جمعِ دفتر − همان. ورودی فقط فیلدهای خودِ سندها است ⇒ هر گره، به هر ترتیبی که سندها برسند، یک عدد می‌دهد.
 *
 *  ── قاعدهٔ دقیق ────────────────────────────────────────────────────────
 *  ردیفِ حاکم بر کالا: دیرترین [Line.countedAt] (برابر ⇒ شناسهٔ بزرگ‌تر)، **همهٔ** ردیف‌های شمارش با هم (ردیفِ بی لحظه
 *  با `countedAt = createdAt` و `seenMilli = null`). فقط ردیفِ حاکمِ «معتبر» جذب می‌کند ([trusted]):
 *    • `seenMilli` دارد؛ `createdAt − WINDOW_MS < countedAt ≤ createdAt` (لحظه پس از ساختِ خودِ سند نیست)؛
 *    • `correctedAt` ندارد (سرورِ v3 ساعتِ شمارنده را بیش از [SKEW_MS] کج ندیده است)؛
 *    • `serverAt` (ساختِ همان سند روی سرور، فقط در سرور) ⇒ `countedAt ≤ serverAt + SKEW_MS`.
 *  حرکتِ عضوِ پنجرهٔ ردیفِ L ([member]): همان کالا، نه انتقالِ میانِ مکان‌ها، نه حرکتِ خودِ L (`refId == L.id`)،
 *    `L.countedAt − WINDOW_MS < at ≤ L.countedAt` (ساعتِ دستگاهِ سازنده)، و اگر سرور زمانِ تصحیح‌شده گذاشته
 *    (`correctedAt`، v3) ⇒ `correctedAt < L.createdAt` (حرکتی که پس از ساختِ سندِ شمارش ساخته شده، با ساعتِ عقب‌کشیده
 *    هم، جذب نمی‌شود).
 *  ⛔ هرگز دو بار: فقط ردیفِ حاکم جذب می‌کند؛ حرکت با شناسه یکتا (تکرار ⇒ نخستین).
 *
 *  مقدار هزارم (عددِ صحیح، سرریز ⇒ خطا). Kotlinِ خالص در commonMain (§۴۰.۳).
 */
object CountAbsorb {

  /** نسخهٔ بردارهای مشترک (`license/test-vectors/count-absorb-v1.json`) */
  const val VECTORS_FORMAT = "count-absorb-v1"

  /** پنجرهٔ شمارش: حرکتِ کهنه‌تر از ۳۰ روز پیش از لحظهٔ شمردن جذب نمی‌شود (همان مرزِ `catalog-manual`) */
  const val WINDOW_MS: Long = 30L * 86_400_000

  /** همان نگهبانِ ساعتِ سرور و موتورِ v3 (`hlc.SKEW_MS`، `ClockSkew.SKEW_MS`) */
  const val SKEW_MS: Long = 5L * 60_000

  /** انتقالِ میانِ مکان‌ها جمعِ موجودی را عوض نمی‌کند (`StockLedger.TRANSFER_TYPES`) */
  val TRANSFER_TYPES: Set<String> = setOf("transfer_out", "transfer_in", "transfer")

  /** یک حرکتِ موجودی (`stockMovements`): نشانه‌دار، لحظه با ساعتِ دستگاهِ سازنده */
  data class Event(
    val id: String,
    val productId: String,
    val at: Long,
    val qtyMilli: Long,
    val type: String = "",
    val refId: String? = null,
    /** زمانِ تصحیح‌شدهٔ سرورِ v3 (`sync-v3-time`)؛ نوشتهٔ دستگاه نیست */
    val correctedAt: Long? = null,
  )

  /** یک ردیفِ شمارش (ردیفِ انبارِ «انبارگردانی») */
  data class Line(
    val id: String,
    val productId: String,
    val countedAt: Long,
    /** دانستهٔ شمارنده ([seen])؛ `null` ⇒ ردیفِ بی لحظه (قدیمی یا وب) — هیچ جذب نمی‌کند */
    val seenMilli: Long?,
    val createdAt: Long,
    val correctedAt: Long? = null,
    /** ساختِ سند روی سرور — فقط سرور می‌داند */
    val serverAt: Long? = null,
  )

  data class Gap(val productId: String, val lineId: String, val gapMilli: Long)

  private fun plus(a: Long, b: Long): Long {
    val r = a + b
    if (((a xor r) and (b xor r)) < 0) throw ArithmeticException("count-absorb: overflow")
    return r
  }

  private fun inWindow(e: Event, productId: String, countedAt: Long, createdAt: Long): Boolean =
    e.productId == productId && e.type !in TRANSFER_TYPES && e.at > 0 &&
      e.at > countedAt - WINDOW_MS && e.at <= countedAt &&
      (e.correctedAt == null || e.correctedAt < createdAt)

  /** آیا [e] در پنجرهٔ ردیفِ [l] است */
  fun member(e: Event, l: Line): Boolean = e.refId != l.id && inWindow(e, l.productId, l.countedAt, l.createdAt)

  /** آیا [l] جذب می‌کند */
  fun trusted(l: Line): Boolean =
    l.seenMilli != null && l.countedAt > 0 && l.createdAt > 0 &&
      l.countedAt <= l.createdAt && l.countedAt > l.createdAt - WINDOW_MS &&
      l.correctedAt == null &&
      (l.serverAt == null || l.countedAt <= l.serverAt + SKEW_MS)

  private fun unique(events: List<Event>): List<Event> {
    val ids = HashSet<String>()
    return events.filter { ids.add(it.id) }
  }

  /** دستگاهِ شمارنده هنگامِ ثبت: جمعِ حرکت‌هایی از [productId] که همین حالا دارد و در پنجره‌اند */
  fun seen(events: List<Event>, productId: String, countedAt: Long, createdAt: Long): Long =
    unique(events).fold(0L) { s, e -> if (inWindow(e, productId, countedAt, createdAt)) plus(s, e.qtyMilli) else s }

  /** ردیفِ حاکمِ هر کالا: دیرترین لحظه، برابر ⇒ شناسهٔ بزرگ‌تر */
  fun governing(lines: List<Line>): Map<String, Line> {
    val out = LinkedHashMap<String, Line>()
    for (l in lines) {
      if (l.productId.isEmpty()) continue
      val cur = out[l.productId]
      if (cur == null || l.countedAt > cur.countedAt || (l.countedAt == cur.countedAt && l.id > cur.id)) out[l.productId] = l
    }
    return out
  }

  /** جذب‌شدهٔ هر کالا (فقط ناصفر): موجودی = جمعِ دفتر − `gapMilli` */
  fun gaps(lines: List<Line>, events: List<Event>): Map<String, Gap> {
    val heads = governing(lines).filterValues(::trusted)
    if (heads.isEmpty()) return emptyMap()
    //  یک بار گروه‌بندی: فقط حرکت‌های کالاهای دارای ردیفِ معتبر (برگهٔ کاملِ دکان × دفترِ بزرگ، نه ضربِ آن دو)
    val byProduct = HashMap<String, ArrayList<Event>>()
    for (e in unique(events)) if (e.productId in heads) byProduct.getOrPut(e.productId) { ArrayList() }.add(e)
    val out = LinkedHashMap<String, Gap>()
    for ((pid, g) in heads) {
      val sum = byProduct[pid].orEmpty().fold(0L) { s, e -> if (member(e, g)) plus(s, e.qtyMilli) else s }
      val gap = plus(sum, -(g.seenMilli ?: 0L))
      if (gap != 0L) out[pid] = Gap(pid, g.id, gap)
    }
    return out
  }
}
