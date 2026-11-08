package ir.vil3ntec.tohid.data

/**
 *  MKT-06 — «چرخهٔ مصرف» ِ کالا: هر چند روز یک بار مشتری معمولاً دوباره می‌خرد (چای ۳۰، برنج ۹۰…).
 *
 *  فیلدِ اختیاریِ `Product.consumptionDays` (روزِ صحیحِ ۱ تا [MAX])؛ فرمِ ویرایشِ کالای گوشیِ نسخهٔ ۳ («بیشتر») و پنلِ
 *  ویرایشِ کامپیوتر می‌نویسندش. همان صفِ همگام‌سازی (v1/v3) آن را می‌برد و ویترین در نمای عمومیِ کالا نشانش می‌دهد
 *  (`server/src/lib/storefront.js` ‹`cycleOf`›، همان سقف) تا برنامهٔ خرید کارتِ «وقتِ دوباره خریدن؟» بسازد.
 *  ⛔ هیچ ربطی به قیمت ندارد؛ خالی ⇒ `null` (نوشته نمی‌شود) و کالای بی چرخه بایت‌به‌بایت همان ردیفِ پیشین است.
 *  ⛔ جدا از `WarehouseEngine` (پس از ویرایشِ موفقِ همان کالا، روی همان دفتر) تا موتور دست نخورد. آزمون: `ProductCycleTest`.
 */
object ProductCycle {
  const val MAX = 365

  /** نتیجهٔ خواندنِ کادر: خالی ⇒ [Clear]، عددِ درست ⇒ [Days]، بقیه ⇒ [Bad] */
  sealed interface Parsed
  data object Clear : Parsed
  data class Days(val days: Int) : Parsed
  data object Bad : Parsed

  /** رقمِ فارسی/عربی هم پذیرفته است؛ اعشار، صفر، منفی یا بیش از [MAX] نه */
  fun parse(text: String): Parsed {
    val t = ir.vil3ntec.tohid.latinDigits(text).trim()
    if (t.isEmpty()) return Clear
    if (!t.all { it in '0'..'9' } || t.length > 3) return Bad
    val n = t.toInt()
    return if (n in 1..MAX) Days(n) else Bad
  }

  fun text(days: Int?): String = days?.takeIf { it in 1..MAX }?.toString().orEmpty()

  /** کادرِ خراب؟ — برای خطای درجا */
  fun bad(text: String): Boolean = parse(text) == Bad

  /** چرخهٔ همین کالا را روی دفتر می‌نشاند؛ کادرِ خراب یا بی‌تغییر ⇒ همان دفتر (بی op ِ تازه) */
  fun apply(d: ShopData, productId: String, text: String): ShopData {
    val days = when (val p = parse(text)) {
      Clear -> null
      is Days -> p.days
      Bad -> return d
    }
    val cur = d.products.firstOrNull { it.id == productId } ?: return d
    if (cur.consumptionDays == days) return d
    return d.copy(products = d.products.map { if (it.id == productId) it.copy(consumptionDays = days) else it })
  }
}
