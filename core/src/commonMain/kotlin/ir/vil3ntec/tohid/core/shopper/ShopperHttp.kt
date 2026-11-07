package ir.vil3ntec.tohid.core.shopper

/*
 *  برنامهٔ خریدِ مشتری (O-04 گزینهٔ «ب»، مرحلهٔ ۱) — مرزِ شبکه.
 *
 *  core شبکه ندارد: این‌جا فقط شکلِ درخواست و پاسخ است. فرستندهٔ واقعی بیرون از core می‌نشیند:
 *  `customer-android/.../HttpAndroid.kt` (HttpURLConnection)، آیفون در مرحلهٔ ۲ (NSURLSession)، و در آزمون جعلی
 *  (`ShopperApiTest`) یا JDK (`ShopperLiveTest`). نشانیِ سرور (`https://api…`) هم با فرستنده است؛ این‌جا فقط مسیر.
 */

/** یک درخواست: روش، مسیر از ریشهٔ سرور (`/api/store/…?…`)، سرآیندها و بدنهٔ JSON. */
data class ShopperRequest(
  val method: String,
  val path: String,
  val headers: Map<String, String> = emptyMap(),
  val body: String? = null,
)

/** پاسخ: کد، بدنهٔ خام (JSON یا خالی) و سرآیندها (نامِ سرآیند بی‌حساس به بزرگی). */
class ShopperResponse(val status: Int, val body: String, val headers: Map<String, String> = emptyMap()) {
  fun header(name: String): String? = headers.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value
}

/**
 *  فرستنده. هم‌گام است (از نخِ پس‌زمینه صدا بزنید). نرسیدن به سرور ⇒ هر استثنایی؛ [ShopperApi] آن را خطای
 *  `network` می‌کند. ⛔ ریدایرکت را خودش دنبال نکند: `store_moved` ِ ویترین را [ShopperApi] می‌خواند.
 */
fun interface ShopperHttp {
  fun send(request: ShopperRequest): ShopperResponse
}

/** ساختنِ مسیر: کدگذاریِ درصدیِ UTF-8 (همان `encodeURIComponent` ِ وب) بی `java.net`. */
object ShopperUrl {
  private const val HEX = "0123456789ABCDEF"

  private fun unreserved(b: Int): Boolean =
    b in 'A'.code..'Z'.code || b in 'a'.code..'z'.code || b in '0'.code..'9'.code ||
      b == '-'.code || b == '_'.code || b == '.'.code || b == '~'.code

  fun encode(s: String): String = buildString {
    for (byte in s.encodeToByteArray()) {
      val b = byte.toInt() and 0xFF
      if (unreserved(b)) append(b.toChar()) else append('%').append(HEX[b shr 4]).append(HEX[b and 0x0F])
    }
  }

  /** `?a=1&b=…` — جفتِ تهی یا null نمی‌آید؛ هیچ جفتی ⇒ رشتهٔ خالی */
  fun query(vararg pairs: Pair<String, String?>): String {
    val kept = pairs.filter { !it.second.isNullOrEmpty() }
    if (kept.isEmpty()) return ""
    return kept.joinToString("&", prefix = "?") { encode(it.first) + "=" + encode(it.second!!) }
  }
}
