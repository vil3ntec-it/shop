package ir.vil3ntec.tohid.core.receipt

import java.security.MessageDigest
import java.security.SecureRandom

/**
 *  ساختنِ توکنِ فاکتور و نشانِ دکان — آن‌چه به JDK تکیه دارد (`SecureRandom`،
 *  `MessageDigest`)، مثلِ `Ulid`. قالب و سنجش در `ReceiptToken` (commonMain).
 */
object ReceiptTokens {

  private val random = SecureRandom()
  private val ABSOLUTE = Regex("^https?://[^/\\s]+.*$", RegexOption.IGNORE_CASE)

  /** توکنِ تازهٔ فاکتور — فقط هنگامِ ثبتِ فروش */
  fun new(): String {
    val bytes = ByteArray(ReceiptToken.TOKEN_BYTES)
    random.nextBytes(bytes)
    return ReceiptToken.encodeToken(bytes)
  }

  /** نشانِ ۸ نویسه‌ایِ دکان — بی راز، فقط برای جدا کردنِ دکان‌ها */
  fun shopTag(shopId: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest((ReceiptToken.TAG_SALT + shopId).encodeToByteArray())
    return ReceiptToken.tagOf(digest)
  }

  /** نشانیِ QR ِ این فروش، یا null اگر فروش توکن ندارد (فروشِ پیش از نسخهٔ ۲) */
  fun url(base: String, shopId: String, token: String?): String? {
    val t = ReceiptToken.normalizeToken(token) ?: return null
    //  بی دکان یا بی ریشهٔ سرور ⇒ بی QR (نشانیِ نسبی روی کاغذ به هیچ‌جا نمی‌رسد)
    if (shopId.isBlank() || !ABSOLUTE.matches(base.trim())) return null
    return ReceiptToken.url(base, shopTag(shopId), t)
  }
}
