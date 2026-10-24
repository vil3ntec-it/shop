package ir.vil3ntec.tohid.ui.v3.onboarding

/** دو راهِ «ساختِ دکان»: صاحبِ دکان می‌سازد، شاگرد با کد می‌پیوندد */
enum class ShopChoice { OWNER, STAFF }

object ShopSetupRules {
  /** نامی که بی نامِ نوشته‌شده به سرور می‌رود — دادهٔ دکان است، همان واژهٔ پیشین (نه نوشتهٔ صفحه) */
  const val DEFAULT_SHOP_NAME = "دکان من"

  fun nameToCreate(typed: String): String = typed.trim().ifBlank { DEFAULT_SHOP_NAME }
}
