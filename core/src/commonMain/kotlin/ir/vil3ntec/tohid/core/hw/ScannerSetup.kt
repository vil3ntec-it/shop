package ir.vil3ntec.tohid.core.hw

/**
 *  HW-12 — راه‌اندازیِ آسانِ بارکدخوان: «برگهٔ تنظیم» و «آزمونِ بارکدخوان».
 *
 *  ⛔ **کدِ تنظیمِ سازنده‌ها ساخته نمی‌شود.** بارکدهای «پیشوند/پسوند/Enter» ِ هانیول، زبرا، داتالاجیک،
 *  نیولند و… برای هر سازنده (و گاه هر مدل) جداست و هیچ‌کدام از دفترچهٔ رسمی سنجیده نشد؛ کدِ حدسی
 *  ممکن است بارکدخوان را به حالتِ دیگری ببرد. پس [ScannerSheet.VENDORS] امروز خالی است و هر سطرِ آینده
 *  بی منبع (نام و نسخهٔ دفترچه) پذیرفته نمی‌شود. به‌جایش برنامه **پیشوند و پسوندِ کنونیِ خودِ بارکدخوان**
 *  را از اسکنِ یک بارکدِ آزمونِ شناخته ([ScannerDetect.TEST_CODE]) پیدا می‌کند و در همان تنظیمِ
 *  بارکدخوانِ HW-05 (`ScanPrefs`: `scan_prefix`/`scan_suffix`) می‌نویسد.
 */
object ScannerDetect {

  /** محتوای Code 128 ِ آزمون: فقط حرف و رقمِ ASCII، تا چیدمانِ صفحه‌کلید و Shift کم‌ترین اثر را داشته باشد */
  const val TEST_CODE = "TOHID2468"

  /** بی نویسهٔ تازه این‌قدر سکوت ⇒ اسکنِ بی Enter پایان یافته */
  const val QUIET_MS = 800L

  data class Result(
    val ok: Boolean,
    val prefix: String = "",
    val suffix: String = "",
    /** بارکدخوان پس از کد Enter زد */
    val enter: Boolean = false,
    val message: String,
  )

  private fun visible(s: String) = s.all { it.code in 33..126 }
  private val persian = Regex("[\\u0600-\\u06FF]")

  /**
   *  [typed] همهٔ نویسه‌هایی که بارکدخوان پیش از Enter (یا سکوت) فرستاد؛ [enter] = Enter آمد.
   *  هر چه پیش از [expected] آمد پیشوند است و هر چه پس از آن پسوند.
   */
  fun detect(typed: String, enter: Boolean, expected: String = TEST_CODE): Result {
    if (typed.isEmpty()) return Result(false, message = "چیزی نرسید — کادر را انتخاب کنید و بارکدِ آزمون را اسکن کنید")
    val at = typed.indexOf(expected)
    if (at < 0) {
      return Result(false, message = when {
        persian.containsMatchIn(typed) -> "حروفِ فارسی رسید — زبانِ صفحه‌کلیدِ دستگاه را انگلیسی کنید و دوباره اسکن کنید"
        typed.contains(expected, ignoreCase = true) -> "حروف بزرگ و کوچک جابه‌جا رسید — Caps Lock را خاموش کنید"
        else -> "«$typed» رسید، نه کدِ آزمون — همان بارکدِ آزمونِ برگه یا صفحه را اسکن کنید"
      })
    }
    val prefix = typed.substring(0, at)
    val suffix = typed.substring(at + expected.length)
    if (prefix.length > MAX_LEN || suffix.length > MAX_LEN) {
      return Result(false, prefix, suffix, enter, "پیشوند یا پسوند بیش از $MAX_LEN نویسه است — در دفترچهٔ بارکدخوان کوتاهش کنید")
    }
    if (!visible(prefix) || !visible(suffix)) {
      return Result(false, prefix, suffix, enter, "پیشوند یا پسوند نویسهٔ نادیدنی (فاصله، Tab…) دارد — برنامه آن را نمی‌شناسد؛ در دفترچهٔ بارکدخوان بردارید")
    }
    if (!enter && suffix.isEmpty()) {
      return Result(false, prefix, suffix, false, "بارکدخوان پس از کد Enter نمی‌زند و پسوندی هم ندارد — از دفترچهٔ خودش «Enter/CR پس از کد» را روشن کنید")
    }
    val parts = buildList {
      if (prefix.isNotEmpty()) add("پیشوندِ «$prefix»")
      if (suffix.isNotEmpty()) add("پسوندِ «$suffix»")
      add(if (enter) "Enter در پایان" else "بی Enter")
    }
    val what = if (prefix.isEmpty() && suffix.isEmpty()) "بی پیشوند و پسوند، با Enter — همان تنظیمِ پیش‌فرض" else parts.joinToString("، ")
    return Result(true, prefix, suffix, enter, "پیدا شد: $what")
  }

  /** همان سقفِ `ScanConfig` */
  const val MAX_LEN = 8

  /** نوشتنِ نتیجه روی تنظیمِ کنونی — آستانه و کمینهٔ طول دست نمی‌خورند؛ نتیجهٔ نادرست ⇒ همان تنظیم */
  fun apply(base: ScanConfig, r: Result): ScanConfig =
    if (!r.ok) base.normalized() else base.copy(prefix = r.prefix, suffix = r.suffix).normalized()

  /** نتیجه با تنظیمِ کنونی فرق دارد؟ (برای «ذخیره»؛ برابر ⇒ «همین تنظیم درست است») */
  fun changes(base: ScanConfig, r: Result): Boolean = r.ok && apply(base, r) != base.normalized()
}

/**
 *  محتوای «برگهٔ راه‌اندازیِ بارکدخوان» — مستقل از کاغذ (چاپگرِ حرارتی یا صفحه).
 */
object ScannerSheet {

  enum class Kind { CODE128, EAN13 }

  data class Code(val label: String, val kind: Kind, val data: String) {
    /** ماژول‌های بارکد؛ کدی که رمز نمی‌شود ⇒ `null` */
    fun modules(): BooleanArray? = runCatching {
      when (kind) {
        Kind.CODE128 -> Code128.modules(data)
        Kind.EAN13 -> if (Ean13.isValid(data)) Ean13.modules(data) else null
      }
    }.getOrNull()
  }

  /** کدهای تنظیمِ یک سازنده — فقط با منبعِ نوشته (نام و نسخهٔ دفترچهٔ رسمی) */
  data class Vendor(val brand: String, val source: String, val codes: List<Code>)

  data class Sheet(val title: String, val steps: List<String>, val codes: List<Code>, val vendors: List<Vendor>, val note: String)

  /**
   *  ⛔ خالی، عمداً: هیچ کدِ سازنده‌ای از دفترچهٔ رسمی سنجیده نشد (بالای [ScannerDetect]). سطرِ تازه فقط با
   *  [Vendor.source] ِ پُر و کدی که رمز می‌شود؛ [build] بقیه را کنار می‌گذارد.
   */
  val VENDORS: List<Vendor> = emptyList()

  /** EAN-13 با پیشوندِ ۲۰۰ (بازهٔ «کدِ داخلیِ فروشگاه»، مالِ هیچ کالای واقعی نیست) */
  val SAMPLE_EAN: String = "200000002468".let { it + Ean13.checkDigit(it) }

  fun build(vendors: List<Vendor> = VENDORS): Sheet = Sheet(
    title = "برگهٔ راه‌اندازیِ بارکدخوان",
    steps = listOf(
      "۱. در «دستگاه‌ها ⇐ بارکدخوان‌ها» دکمهٔ «آزمونِ بارکدخوان» را بزنید.",
      "۲. بارکدِ آزمونِ پایین را اسکن کنید؛ برنامه پیشوند، پسوند و Enter ِ بارکدخوان را خودش پیدا می‌کند.",
      "۳. اگر گفت «Enter نمی‌زند»، از دفترچهٔ خودِ بارکدخوان «Enter/CR پس از کد» را روشن کنید و دوباره بیازمایید.",
    ),
    codes = listOf(
      Code("آزمونِ پیشوند و پسوند — Code 128", Kind.CODE128, ScannerDetect.TEST_CODE),
      Code("آزمونِ خواندنِ EAN-13 (کدِ داخلی)", Kind.EAN13, SAMPLE_EAN),
    ),
    vendors = vendors.filter { v -> v.source.isNotBlank() && v.codes.isNotEmpty() && v.codes.all { it.modules() != null } },
    note = "کدِ تنظیمِ سازنده‌ها این‌جا نیست: هر سازنده کدِ خودش را دارد و برنامه کدِ حدسی چاپ نمی‌کند. " +
      "«پیشوند/پسوند/Enter» را از دفترچهٔ همان مدل تنظیم کنید.",
  )
}
