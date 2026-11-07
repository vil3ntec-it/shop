package ir.vil3ntec.tohid.core.pos

/**
 *  چیدمانِ کاشی‌های صندوقِ **همین دستگاه** (POS-06، §۶.۱ «قابل چیدن برای هر دستگاه»): کالاهای سنجاق‌شده، به ترتیبِ
 *  سنجاق، سرِ شبکهٔ کالا می‌نشینند — در «همه» و در هر دسته — و بقیه همان ترتیبِ امروز (پرفروش‌ترین اول) را دارند.
 *
 *  ── پیش‌فرض: بی سنجاق ⇒ همان شبکهٔ امروز ─────────────────────────
 *  سنجاق فقط با کارِ صریحِ کاربر روی همان کالا (نگه داشتنِ کاشی ⇐ «سنجاق در صفحهٔ اول») ساخته می‌شود؛ تنظیمی
 *  نیست که روشن شود. هنگامِ جست‌وجو ترتیب همان جورِ جست‌وجوست (سنجاق اثر ندارد). سنجاق مالِ همین دستگاه است و
 *  همگام نمی‌شود (صندوقِ دیگر چیدمانِ خودش را دارد). کالای پاک‌شده یا غیرفعال خودبه‌خود دیده نمی‌شود.
 *
 *  Kotlin خالص است — بی java.*، بی android.*، بی کتابخانه.
 */
object TillPins {

  /** سقفِ سنجاق — دو صفحهٔ کاشیِ گوشی */
  const val MAX = 24

  /** سنجاق یا برداشتنِ سنجاقِ [id]؛ تازه ته فهرست می‌رود. پُر ⇒ بی تغییر */
  fun toggle(pins: List<String>, id: String): List<String> = when {
    id.isBlank() -> pins
    id in pins -> pins - id
    pins.size >= MAX -> pins
    else -> pins + id
  }

  /** نتیجهٔ [toggle] برای پیامِ دکمه: سنجاق شد، برداشته شد، صفحهٔ اول پُر بود (بی تغییر)، یا هیچ */
  enum class Change { PINNED, UNPINNED, FULL, NONE }

  /** همان تصمیمِ [toggle] پیش از انجام — `toggle(before, id)` دقیقاً همین را می‌کند */
  fun change(before: List<String>, id: String): Change = when {
    id.isBlank() -> Change.NONE
    id in before -> Change.UNPINNED
    before.size >= MAX -> Change.FULL
    else -> Change.PINNED
  }

  /** سنجاق‌شده‌ای [id] اول شود (جابه‌جاییِ چیدمان)؛ سنجاق‌نشده ⇒ بی تغییر */
  fun first(pins: List<String>, id: String): List<String> = if (id in pins) listOf(id) + (pins - id) else pins

  /** شناسه‌های [ids] با سنجاق‌شده‌ها اول (به ترتیبِ سنجاق) و بقیه به همان ترتیب */
  fun arrange(ids: List<String>, pins: List<String>): List<String> {
    if (pins.isEmpty()) return ids
    val present = ids.toSet()
    val head = pins.filter { it in present }
    if (head.isEmpty()) return ids
    val pinned = head.toSet()
    return head + ids.filter { it !in pinned }
  }

  /**
   *  همان [arrange] روی خودِ کالاها (صندوقِ کامپیوتر، `DeskSale`): سنجاق‌شده‌های حاضر به ترتیبِ سنجاق اول، بقیه به
   *  همان ترتیب؛ هیچ کالایی گم یا تکرار نمی‌شود. بی سنجاقِ حاضر ⇒ همان فهرست.
   */
  fun <T> arrangeBy(items: List<T>, pins: List<String>, id: (T) -> String): List<T> {
    if (pins.isEmpty()) return items
    val byId = LinkedHashMap<String, T>()
    items.forEach { byId.getOrPut(id(it)) { it } }
    val head = pins.distinct().mapNotNull { byId[it] }
    if (head.isEmpty()) return items
    val pinned = head.map(id).toSet()
    return head + items.filter { id(it) !in pinned }
  }

  /** نوشتن روی دستگاه: هر شناسه یک خط */
  fun encode(pins: List<String>): String = pins.joinToString("\n")

  /** خواندن از دستگاه: خالی/تکراری کنار، حداکثر [MAX] */
  fun decode(text: String?): List<String> =
    text.orEmpty().split('\n').map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
}
