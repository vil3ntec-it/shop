package ir.vil3ntec.tohid.core.receipt

/**
 *  توکن و نشانیِ QRِ هر فاکتور (فاکتورِ نسخهٔ ۲) — همتای `server/src/lib/receipt-token.js`.
 *  هر دو با `core/test-vectors/receipt-v1.json` سنجیده می‌شوند؛ رفتارِ یکی را بی دیگری و
 *  بی فایلِ مرجع عوض نکنید.
 *
 *    QR = `<نشانیِ سرورِ برنامه>/r/<نشانِ دکان>/<توکن>`
 *
 *  • توکن: ۱۲۸ بیتِ تصادفی به ۲۶ نویسهٔ Crockford base32 ِ کوچک — عددِ ۱۲۸ بیتی،
 *    بزرگ‌مرتبه اول، دو بیتِ صفر در سر؛ پس نویسهٔ اول همیشه `0`..`7` است. هنگامِ **ثبتِ**
 *    فروش ساخته می‌شود (`ReceiptTokens.new`) و روی `Sale.receiptToken` همگام می‌شود؛
 *    فروشِ v3 فقط‌افزودنی است، پس فروشِ کهنه هرگز توکنِ تازه نمی‌گیرد.
 *  • نشانِ دکان: ۸ نویسهٔ اولِ همان base32 از `sha256("vill3n.receipt.shop.v1:" + shopId)`
 *    (`ReceiptTokens.shopTag`، بی اینترنت). سرور فقط فروشِ همان دکان را با آن برمی‌گرداند.
 */
object ReceiptToken {

  const val ALPHABET = "0123456789abcdefghjkmnpqrstvwxyz"
  const val TOKEN_LENGTH = 26
  const val TOKEN_BYTES = 16
  const val TAG_LENGTH = 8
  const val TAG_BYTES = 5
  const val TAG_SALT = "vill3n.receipt.shop.v1:"
  const val PATH_PREFIX = "/r/"

  private val TOKEN_RE = Regex("^[0-7][0-9a-hjkmnp-tv-z]{25}$")
  private val TAG_RE = Regex("^[0-9a-hjkmnp-tv-z]{8}$")

  /** بایت‌ها ⇒ [chars] نویسهٔ base32، بزرگ‌مرتبه اول؛ بیت‌های کم‌شده صفرِ سرند. */
  fun encode(bytes: ByteArray, chars: Int): String {
    val pad = chars * 5 - bytes.size * 8
    require(pad in 0..4) { "بیت‌ها در این تعداد نویسه جا نمی‌شوند" }
    fun bit(i: Int): Int {
      val j = i - pad
      if (j < 0) return 0
      return (bytes[j ushr 3].toInt() ushr (7 - (j and 7))) and 1
    }
    val out = StringBuilder(chars)
    for (c in 0 until chars) {
      var v = 0
      for (k in 0 until 5) v = (v shl 1) or bit(c * 5 + k)
      out.append(ALPHABET[v])
    }
    return out.toString()
  }

  fun encodeToken(bytes: ByteArray): String {
    require(bytes.size == TOKEN_BYTES) { "توکن ۱۶ بایت است" }
    return encode(bytes, TOKEN_LENGTH)
  }

  /** متنِ اسکن‌شده ⇒ توکنِ یکدست، یا null. اسکنرِ «همه بزرگ» هم پذیرفته است. */
  fun normalizeToken(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { TOKEN_RE.matches(it) }

  fun normalizeTag(raw: String?): String? = raw?.trim()?.lowercase()?.takeIf { TAG_RE.matches(it) }

  /** نشانِ دکان از ۵ بایتِ اولِ sha256 (`ReceiptTokens.shopTag` هش را می‌سازد) */
  fun tagOf(digest: ByteArray): String = encode(digest.copyOf(TAG_BYTES), TAG_LENGTH)

  /** مسیرِ صفحهٔ عمومی: `/r/<نشان>/<توکن>` */
  fun path(tag: String, token: String): String = "$PATH_PREFIX$tag/$token"

  /** نشانیِ کاملِ QR — ریشه بی `/` ِ آخر */
  fun url(base: String, tag: String, token: String): String = base.trimEnd('/') + path(tag, token)

  /**
   *  نشانیِ اسکن‌شده ⇒ (نشان، توکن)، یا null — برای اسکنرِ «برگشت» (#318) که فروش را
   *  روی خودِ دستگاه با `receiptToken` پیدا می‌کند. میزبان سنجیده نمی‌شود (نسخهٔ خانگی
   *  و ابری)، فقط شکلِ مسیر.
   */
  fun parse(scanned: String?): Pair<String, String>? {
    val s = scanned?.trim() ?: return null
    val at = s.lastIndexOf(PATH_PREFIX).takeIf { it >= 0 } ?: return null
    val parts = s.substring(at + PATH_PREFIX.length).substringBefore('?').substringBefore('#').trimEnd('/').split('/')
    if (parts.size != 2) return null
    val tag = normalizeTag(parts[0]) ?: return null
    val token = normalizeToken(parts[1]) ?: return null
    return tag to token
  }
}
