package ir.vil3ntec.tohid.core.receipt

import ir.vil3ntec.tohid.core.platform.secureRandomBytes
import ir.vil3ntec.tohid.core.platform.sha256

/**
 *  ساختنِ توکنِ فاکتور و نشانِ دکان — تصادفیِ امن و SHA-256 از سکو
 *  (`core.platform`)، مثلِ `Ulid`. قالب و سنجش در `ReceiptToken`.
 */
object ReceiptTokens {

  private val ABSOLUTE = Regex("^https?://[^/\\s]+.*$", RegexOption.IGNORE_CASE)

  /** توکنِ تازهٔ فاکتور — فقط هنگامِ ثبتِ فروش */
  fun new(): String {
    return ReceiptToken.encodeToken(secureRandomBytes(ReceiptToken.TOKEN_BYTES))
  }

  /** نشانِ ۸ نویسه‌ایِ دکان — بی راز، فقط برای جدا کردنِ دکان‌ها */
  fun shopTag(shopId: String): String {
    val digest = sha256((ReceiptToken.TAG_SALT + shopId).encodeToByteArray())
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
