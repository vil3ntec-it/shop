package ir.vil3ntec.tohid.core.shopper

//  از `ShopperText.kt` جدا شد تا آن زیرِ سقفِ اندازه بماند (`tools/quality`)؛ همان بسته و همان نام ⇒ کاربرها دست نخوردند.

/** زبانِ برنامهٔ خرید — همان چهار زبانِ برنامهٔ دکان؛ دری = فارسی + لایهٔ واژهٔ `DariText`. */
enum class ShopperLang(val code: String, val rtl: Boolean, val native: String) {
  FA("fa", true, "فارسی"),
  PRS("prs", true, "دری"),
  EN("en", false, "English"),
  PS("ps", true, "پښتو");

  companion object {
    /** ناشناخته یا خالی ⇒ فارسی */
    fun of(code: String?): ShopperLang = entries.firstOrNull { it.code == code } ?: FA
  }
}
