package ir.vil3ntec.tohid.core.marketing

import ir.vil3ntec.tohid.core.hw.Labels
import ir.vil3ntec.tohid.core.hw.MonoImage
import ir.vil3ntec.tohid.core.qr.QrCode
import ir.vil3ntec.tohid.core.receipt.ReceiptBlock
import ir.vil3ntec.tohid.core.receipt.ReceiptDoc
import ir.vil3ntec.tohid.core.receipt.ReceiptMetrics
import ir.vil3ntec.tohid.core.receipt.TextStyle

/**
 *  پیوندِ کمپین و QR ِ دکان — سمتِ برنامهٔ مدیریت (MKT-10، MKT-11؛ بخشِ ۶.۲۰).
 *
 *  سرور (`server/src/lib/campaigns.js`، `/api/shop/storefront/campaigns`) داورِ نهایی
 *  است؛ این‌جا فقط همان قاعده‌ها پیش از فرستادن، و آن‌چه برای چاپ لازم است:
 *    • نامِ پیوند: یک خط، بی نویسهٔ کنترلی، ۱ تا ۶۰ نویسه (همان `nameOf` ِ سرور)
 *    • منبع: یکی از هفت کلیدِ ثابتِ سرور؛ کلیدِ ناشناختهٔ سرورِ تازه‌تر ⇒ «دیگر»
 *    • نشانیِ عمومی: ریشهٔ سرور + `/store/go/<کد>` (همان که QR می‌برد)
 *    • برگهٔ چاپ: کاغذِ رسید (۵۸/۸۰ میلی‌متر) و برگهٔ A4 ِ دیوار — هر دو با همان
 *      بلوک‌های فاکتورِ نسخهٔ ۲ (`ReceiptDoc`)، پس نوشته را قلمِ برنامه می‌کشد و QR را
 *      همان رمزگذارِ آزموده‌شدهٔ `core/qr`.
 *
 *  خالص است: نه شبکه، نه ساعت، نه android.* / java.* (برنامهٔ آیفون هم همین را می‌گیرد).
 */
object CampaignLinks {

  const val ROOT = "/shop/storefront/campaigns"

  /** همان `MAX_NAME` ِ سرور */
  const val NAME_MAX = 60

  /** کلیدِ یکتاییِ دو QR ِ ثابتِ MKT-10 — زدنِ دوباره همان پیوند را برمی‌گرداند */
  const val WALL_CLIENT_ID = "qr-wall"
  const val RECEIPT_CLIENT_ID = "qr-receipt"

  enum class Source(val key: String, val label: String) {
    QR_WALL("qr_wall", "QR ِ دیوار و پیشخوان"),
    QR_RECEIPT("qr_receipt", "QR ِ رسید"),
    SOCIAL("social", "شبکهٔ اجتماعی"),
    MESSAGING("messaging", "واتس‌اپ و تلگرام"),
    SMS("sms", "پیامک"),
    PRINT("print", "تراکت و کارت"),
    OTHER("other", "دیگر");

    companion object {
      fun of(key: String?): Source = entries.firstOrNull { it.key == key } ?: OTHER
    }
  }

  /* ------------------------------ نام ------------------------------ */

  private fun isControl(c: Char): Boolean =
    c.code <= 0x1F || c.code == 0x7F || c.code == 0x2028 || c.code == 0x2029

  /** همان پاک‌سازیِ سرور: نویسهٔ کنترلی ⇒ فاصله، فاصله‌های پیاپی ⇒ یکی، دو سر بریده */
  fun normalizeName(raw: String): String {
    val sb = StringBuilder(raw.length)
    var space = false
    for (c in raw) {
      val ws = isControl(c) || c.isWhitespace()
      if (ws) { space = true; continue }
      if (space && sb.isNotEmpty()) sb.append(' ')
      space = false
      sb.append(c)
    }
    return sb.toString()
  }

  /** طول به نویسهٔ یونیکد (نه واحدِ UTF-16) — همان `[...s].length` ِ سرور */
  fun codePoints(s: String): Int {
    var n = 0
    var i = 0
    while (i < s.length) {
      i += if (s[i].isHighSurrogate() && i + 1 < s.length && s[i + 1].isLowSurrogate()) 2 else 1
      n++
    }
    return n
  }

  /** خطای کادرِ نام؛ null یعنی درست */
  fun nameError(raw: String): String? {
    val s = normalizeName(raw)
    return when {
      s.isEmpty() -> "نامِ پیوند را بنویسید"
      codePoints(s) > NAME_MAX -> "نامِ پیوند حداکثر $NAME_MAX نویسه"
      else -> null
    }
  }

  /* ----------------------------- نشانی ----------------------------- */

  /** ریشهٔ سرور (بی `/` ِ آخر) + مسیرِ پیوند؛ مسیر فقط همان شکلِ `/store/go/<کد>` */
  fun publicUrl(base: String, path: String): String? {
    if (!PATH_RE.matches(path)) return null
    val root = base.trim().trimEnd('/')
    if (!root.startsWith("https://") && !root.startsWith("http://")) return null
    return root + path
  }

  /** نشانیِ کوتاه برای زیرِ QR — بی `https://` */
  fun displayUrl(url: String): String = url.removePrefix("https://").removePrefix("http://")

  private val PATH_RE = Regex("^/store/go/[0-9A-HJKMNP-TV-Z]{10}$")

  /* ------------------------------ چاپ ------------------------------ */

  const val CAPTION = "سفارشِ اینترنتی از همین دکان"
  const val SCAN_HINT = "با دوربینِ گوشی اسکن کنید"

  /** کاغذِ رسید: نامِ دکان، جمله، QR و نشانیِ کوتاه */
  fun slip(storeName: String, url: String, widthDots: Int): ReceiptDoc {
    val m = ReceiptMetrics.of(widthDots)
    return ReceiptDoc(widthDots, m, blocks(storeName, url, m.gap))
  }

  /** پهنای برگهٔ A4 با ۸ نقطه در میلی‌متر (همان `Labels.A4.sheet`) */
  val A4_WIDTH: Int get() = Labels.A4.PAGE_W * Labels.DOTS_PER_MM / 10
  val A4_HEIGHT: Int get() = Labels.A4.PAGE_H * Labels.DOTS_PER_MM / 10

  /** اندازه‌های برگهٔ دیوار — QR ِ حدودِ ۱۲ تا ۱۴ سانتی‌متر، نوشتهٔ دورخوان */
  val POSTER = ReceiptMetrics(pad = 120, title = 120, total = 96, body = 72, small = 56, qrModule = 28, ruleThick = 6, dashOn = 24, dashOff = 16, gap = 48)

  /** برگهٔ A4 ِ دیوار — همان بلوک‌ها با اندازه‌های درشت */
  fun poster(storeName: String, url: String): ReceiptDoc = ReceiptDoc(A4_WIDTH, POSTER, blocks(storeName, url, POSTER.gap * 2))

  /** تصویرِ برگه ⇒ صفحهٔ کاملِ A4 (وسطِ عمودی)؛ بلندتر از برگه ⇒ همان تصویر */
  fun page(image: MonoImage): MonoImage {
    if (image.width != A4_WIDTH || image.height >= A4_HEIGHT) return image
    val out = MonoImage(A4_WIDTH, A4_HEIGHT)
    out.blit(image, 0, (A4_HEIGHT - image.height) / 2)
    return out
  }

  private fun blocks(storeName: String, url: String, gap: Int): List<ReceiptBlock> = buildList {
    val name = normalizeName(storeName)
    if (name.isNotEmpty()) add(ReceiptBlock.Text(name, TextStyle.TITLE))
    add(ReceiptBlock.Text(CAPTION, TextStyle.BODY))
    add(ReceiptBlock.Gap(gap))
    add(ReceiptBlock.Qr(url, QrCode.encode(url)))
    add(ReceiptBlock.Text(SCAN_HINT, TextStyle.BODY))
    add(ReceiptBlock.Text(displayUrl(url), TextStyle.SMALL))
  }
}
