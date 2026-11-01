package ir.vil3ntec.tohid.core.storefront

/**
 *  انتشارِ گروهیِ کالا در ویترین و بازار (M2 ِ `docs/MARKETPLACE-CONTRACT-fa.md`) —
 *  منطقِ خالصِ سمتِ برنامهٔ مدیریت برای `PUT /api/shop/storefront/products`.
 *
 *  ⛔ منبعِ حقیقت سرور است: همهٔ مقدارها و «زنده بودن» را دوباره می‌سنجد. این‌جا
 *  فقط برای این است که برنامه درخواستِ نادرست نفرستد، فهرستِ بزرگ را به بخش‌های
 *  پذیرفتنی بشکند و هر بخش را با **همان** کلیدِ یکتایی دوباره بفرستد (قطعِ شبکه ⇒
 *  تکرارِ بی‌اثر).
 *
 *  خالص است — نه شبکه، نه زمانِ سیستم، نه android.* و نه java.* — تا اندروید، کامپیوتر
 *  و آیفون همین را بگیرند.
 */
object BulkPublish {

  /** همان `MAX_BULK` ِ سرور */
  const val MAX_ITEMS = 500
  /** سرور بدنه را تا ۲ مگابایت می‌پذیرد؛ با حاشیه */
  const val MAX_BODY_BYTES = 1_500_000
  const val MAX_PRICE_MINOR = 100_000_000_000L
  const val MAX_QTY_MILLI = 10_000_000L
  const val MAX_DESCRIPTION = 2000
  private val ID_RE = Regex("^[A-Za-z0-9_.:-]{1,80}$")

  /**
   *  یک تغییر برای یک کالا. فیلدِ null (یا `set…=false`) یعنی «نفرست» ⇒ سرور همان
   *  قبلی را نگه می‌دارد؛ پس «همه را منتشر کن» قیمتِ آنلاین و سقفِ پیشین را پاک نمی‌کند.
   */
  data class Change(
    val productId: String,
    val published: Boolean? = null,
    val setOnlinePrice: Boolean = false,
    val onlinePriceMinor: Long? = null,
    val setMaxQty: Boolean = false,
    val maxQtyMilli: Long? = null,
    val description: String? = null,
  )

  /** خطای فارسی برای کاربر، یا null وقتی درست است (همان بازه‌های سرور). */
  fun validate(c: Change): String? = when {
    !ID_RE.matches(c.productId) -> "شناسهٔ کالا معتبر نیست"
    c.published == null && !c.setOnlinePrice && !c.setMaxQty && c.description == null -> "تغییری نیامده است"
    c.setOnlinePrice && c.onlinePriceMinor != null && c.onlinePriceMinor !in 0..MAX_PRICE_MINOR -> "قیمتِ آنلاین در بازهٔ مجاز نیست"
    c.setMaxQty && c.maxQtyMilli != null && c.maxQtyMilli !in 1..MAX_QTY_MILLI -> "سقفِ هر سفارش در بازهٔ مجاز نیست"
    else -> null
  }

  /** اندازهٔ تقریبیِ یک قلم در JSON (بایتِ UTF-8؛ حرفِ فارسی تا ۲ بایت، با گریز تا ۶). */
  fun estimateBytes(c: Change): Int {
    var n = 40 + c.productId.length
    if (c.published != null) n += 20
    if (c.setOnlinePrice) n += 40
    if (c.setMaxQty) n += 35
    c.description?.let { d -> n += 20 + d.sumOf { ch -> if (ch.code < 0x80) (if (ch == '"' || ch == '\\') 2 else 1) else 6 } }
    return n
  }

  /**
   *  فهرستِ تغییر ⇒ بخش‌های پذیرفتنی برای سرور. شناسهٔ تکراری ⇒ آخرین تغییر می‌ماند
   *  (سرور تکرار را رد می‌کند)؛ توضیح به سقفِ سرور بریده می‌شود؛ ترتیب حفظ می‌شود.
   */
  fun chunks(changes: List<Change>): List<List<Change>> {
    val last = LinkedHashMap<String, Change>()
    for (c in changes) {
      last.remove(c.productId)
      last[c.productId] = c.copy(description = c.description?.trim()?.take(MAX_DESCRIPTION))
    }
    val out = mutableListOf<List<Change>>()
    var cur = mutableListOf<Change>()
    var bytes = 20
    for (c in last.values) {
      val b = estimateBytes(c) + 1
      if (cur.isNotEmpty() && (cur.size >= MAX_ITEMS || bytes + b > MAX_BODY_BYTES)) {
        out += cur; cur = mutableListOf(); bytes = 20
      }
      cur += c; bytes += b
    }
    if (cur.isNotEmpty()) out += cur
    return out
  }

  /** کلیدِ یکتاییِ پایدار برای بخشِ `index` از یک کار — همان کلید در هر بار فرستادن. */
  fun keyOf(batchId: String, index: Int): String {
    val safe = batchId.filter { it.isLetterOrDigit() && it.code < 0x80 || it == '-' || it == '_' }.take(100)
    require(safe.length >= 4) { "شناسهٔ کار کوتاه است" }
    return "sfb-$safe-$index"
  }

  //  ── دلیلِ «در بازار نمی‌آید» ─────────────────────────────────────────

  /** همان کدهای سرور (`notLiveReasons`)، به ترتیبِ اهمیت برای صاحب دکان */
  val REASON_ORDER = listOf("hidden_listing", "product_not_found", "inactive", "no_price", "not_published")

  fun reasonText(code: String): String = when (code) {
    "not_published" -> "در ویترین گذاشته نشده"
    "inactive" -> "در دفتر غیرفعال است"
    "no_price" -> "قیمت ندارد"
    "hidden_listing" -> "سامانه با جریمه پنهانش کرده"
    "product_not_found" -> "هنوز به سرور همگام نشده"
    else -> "در بازار دیده نمی‌شود"
  }

  /**
   *  جملهٔ کوتاه برای زیرِ نامِ کالا، یا null وقتی کالا دیده می‌شود. «منتشرنشده» به‌تنهایی
   *  دلیل نشان داده نمی‌شود (خودِ کلید پیداست)؛ ولی منتشرنشدهٔ بی‌قیمت ⇒ «قیمت ندارد».
   */
  fun reasonLine(codes: List<String>): String? {
    val shown = codes.filter { it != "not_published" }.sortedBy { REASON_ORDER.indexOf(it).let { i -> if (i < 0) 99 else i } }
    if (shown.isEmpty()) return null
    return "در بازار نمی‌آید: " + shown.joinToString("، ") { reasonText(it) }
  }

  //  ── صفِ فرستادن ────────────────────────────────────────────────────

  /** نتیجهٔ یک قلم از پاسخِ سرور */
  data class ItemResult(val productId: String, val ok: Boolean, val error: String? = null, val notLive: List<String> = emptyList())

  /** پیامدِ فرستادنِ یک بخش */
  sealed class Outcome {
    data class Done(val results: List<ItemResult>) : Outcome()
    /** قطعِ شبکه، ۵xx، مهلت ⇒ همان بخش با همان کلید دوباره */
    data class Retry(val message: String) : Outcome()
    /** ۴xx ِ قطعی (دسترسی، اشتراک، بدنه) ⇒ دوباره‌فرستادن بی‌فایده است */
    data class Rejected(val message: String) : Outcome()
  }

  data class Part(val index: Int, val key: String, val items: List<Change>)

  /**
   *  حالِ یک کارِ گروهی؛ تغییرناپذیر. بخشِ رسیده دیگر فرستاده نمی‌شود؛ بخشِ «دوباره»
   *  با همان کلید می‌ماند تا کاربر دوباره بزند.
   */
  data class Queue(
    val pending: List<Part>,
    val results: List<ItemResult> = emptyList(),
    val rejected: List<Pair<Part, String>> = emptyList(),
    val lastError: String? = null,
  ) {
    val done: Boolean get() = pending.isEmpty()
    val next: Part? get() = pending.firstOrNull()

    fun after(part: Part, outcome: Outcome): Queue = when (outcome) {
      is Outcome.Done -> copy(pending = pending.filter { it.index != part.index }, results = results + outcome.results, lastError = null)
      is Outcome.Rejected -> copy(pending = pending.filter { it.index != part.index }, rejected = rejected + (part to outcome.message), lastError = outcome.message)
      is Outcome.Retry -> copy(lastError = outcome.message)
    }
  }

  /** ساختنِ صف؛ قلمِ نادرست پیش از فرستادن کنار می‌رود و دلیلش برمی‌گردد. */
  fun queueOf(batchId: String, changes: List<Change>): Pair<Queue, List<Pair<String, String>>> {
    val invalid = changes.mapNotNull { c -> validate(c)?.let { c.productId to it } }
    val bad = invalid.map { it.first }.toSet()
    val parts = chunks(changes.filter { it.productId !in bad }).mapIndexed { i, items -> Part(i, keyOf(batchId, i), items) }
    return Queue(parts) to invalid
  }

  /** جمع‌بندیِ کوتاه برای پیامِ پایانی */
  data class Summary(val ok: Int, val notLive: Int, val notFound: Int, val failed: Int, val waiting: Int) {
    val text: String get() = buildString {
      append("${fa(ok)} کالا ثبت شد")
      if (notLive > 0) append("؛ ${fa(notLive)} کالا در بازار نمی‌آید")
      if (notFound > 0) append("؛ ${fa(notFound)} کالا در سرور نبود")
      if (failed > 0) append("؛ ${fa(failed)} کالا پذیرفته نشد")
      if (waiting > 0) append("؛ ${fa(waiting)} کالا هنوز نرسیده — دوباره بفرستید")
    }
  }

  fun summarize(q: Queue): Summary = Summary(
    ok = q.results.count { it.ok },
    notLive = q.results.count { r -> r.ok && r.notLive.any { it != "not_published" } },
    notFound = q.results.count { !it.ok },
    failed = q.rejected.sumOf { it.first.items.size },
    waiting = q.pending.sumOf { it.items.size },
  )

  private fun fa(n: Int): String = buildString { for (ch in n.toString()) append(if (ch in '0'..'9') '۰' + (ch - '0') else ch) }
}
