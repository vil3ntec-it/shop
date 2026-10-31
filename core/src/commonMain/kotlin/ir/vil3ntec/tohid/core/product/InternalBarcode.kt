package ir.vil3ntec.tohid.core.product

import ir.vil3ntec.tohid.core.hw.Ean13
import ir.vil3ntec.tohid.core.hw.WeightBarcode
import ir.vil3ntec.tohid.core.platform.sha256

/*
 *  بارکدِ داخلی برای کالای بی‌بارکد — PRD-05 (§۶.۳، §۴.۷).
 *
 *  • فقط پیشوندهای «20» و «29» (بازهٔ داخلیِ GS1، نه کشوری)؛ EAN-13 با رقمِ کنترل.
 *  • هرگز کدی که پیشوندش با یکی از قاعده‌های ترازو (وزنی یا قیمتی، `WeightBarcode.Rule`) بخواند:
 *    قاعدهٔ «2»، «20» یا «201…» پیشوندِ ۲۰ را می‌بندد (هم‌پوشانیِ دوطرفه، محتاطانه). اگر قاعده‌ها
 *    هر دو را گرفته باشند، چیزی ساخته نمی‌شود و دلیلش گفته می‌شود — نه کدی که صندوق «وزنی» بخواند.
 *  • ده رقمِ میانی از SHA-256 ِ `seed` (شناسهٔ ثابتِ کالا، ULID): یک کالا روی هر دستگاه همان کد را
 *    می‌گیرد، پس دو دستگاهِ آفلاین برای یک کالا دو کدِ متفاوت نمی‌سازند؛ کدِ گرفته‌شده (`taken`) ⇒
 *    تلاشِ بعدی با همان قاعده. بی تصادفِ پنهان، پس آزمون و هر سکو یک جواب.
 *  منطقِ خالص (iOS هم)؛ آزمون: `InternalBarcodeTest` (commonTest).
 */
object InternalBarcode {

  /** پیشوندهای مجاز، به همین ترتیبِ ترجیح */
  val PREFIXES: List<String> = listOf("20", "29")

  /** پیشوندهایی که هیچ قاعدهٔ معتبرِ ترازو با آن‌ها هم‌پوشانی ندارد */
  fun freePrefixes(rules: List<WeightBarcode.Rule>): List<String> {
    val used = rules.filter { it.valid }.map { it.prefix }
    return PREFIXES.filter { p -> used.none { r -> p.startsWith(r) || r.startsWith(p) } }
  }

  /** همین قاعده‌ها این کد را می‌سازند؟ (۱۳ رقم، پیشوندِ ۲۰/۲۹، رقمِ کنترلِ درست) */
  fun isInternal(code: String): Boolean =
    code.length == 13 && PREFIXES.any { code.startsWith(it) } && Ean13.isValid(code)

  sealed interface Result {
    data class Ok(val code: String) : Result
    /** قاعده‌های ترازو هم ۲۰ و هم ۲۹ را گرفته‌اند */
    data object NoFreePrefix : Result
    /** همهٔ تلاش‌ها کدِ گرفته‌شده دادند (با ده رقم عملاً ناممکن؛ برای کاملی) */
    data object Exhausted : Result
  }

  /**
   *  کدِ تازه برای `seed`. `taken(code)` ⇒ این کد مالِ کالای دیگری است (بارکدهای همهٔ کالاها، و کدهایی که
   *  در همین دور به کالاهای دیگر داده شد).
   */
  fun generate(
    seed: String,
    taken: (String) -> Boolean,
    rules: List<WeightBarcode.Rule> = emptyList(),
    maxTries: Int = 64,
  ): Result {
    val prefixes = freePrefixes(rules)
    if (prefixes.isEmpty()) return Result.NoFreePrefix
    for (attempt in 0 until maxTries) {
      val code = candidate(seed, attempt, prefixes)
      if (!taken(code)) return Result.Ok(code)
    }
    return Result.Exhausted
  }

  /** نامزدِ شمارهٔ `attempt`: پیشوند از بایتِ ششم، ده رقم از ۴۰ بیتِ نخست (۲^۴۰ > ۱۰^۱۰) */
  internal fun candidate(seed: String, attempt: Int, prefixes: List<String>): String {
    val h = sha256("tohid-internal-barcode:$seed:$attempt".encodeToByteArray())
    var v = 0L
    for (i in 0 until 5) v = (v shl 8) or (h[i].toLong() and 0xFF)
    val body = (v % 10_000_000_000L).toString().padStart(10, '0')
    val first12 = prefixes[(h[5].toInt() and 0xFF) % prefixes.size] + body
    return first12 + Ean13.checkDigit(first12)
  }
}
