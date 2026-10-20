package ir.vil3ntec.tohid.core.nav

/**
 *  یک برنامه، سه حالت (دستورِ صاحب محصول، ۱۴۰۵/۰۷/۲۸):
 *   • **دکان** — مدیریتِ دکان برای صاحب و کارمند (برنامهٔ امروز)؛
 *   • **پیک** — حالتِ پیک (امروز فقط با پیوستن با کدِ کارمند)؛
 *   • **خرید** — خریدِ مشتری از بازارچه، درونِ خودِ برنامه.
 *  هر حالت نوارِ پایین و خانهٔ خودش را دارد.
 */
enum class AppMode(val id: String, val title: Txt) {
  SHOP("shop", Txt("دکان", "Shop", "دوکان")),
  COURIER("courier", Txt("پیک", "Courier", "رسوونکی")),
  SHOPPER("shopper", Txt("خرید", "Shopping", "پېرود"));

  companion object {
    fun of(id: String?): AppMode? = entries.firstOrNull { it.id == id }
  }
}

/** آنچه کاربر واقعاً دارد — حالت‌هایی که سوییچر نشان می‌دهد از همین ساخته می‌شود */
data class ModeFacts(
  /** عضوِ یک دکان است (صاحب/کارمند، یا دفترِ دکانِ همین دستگاه را دارد) */
  val shopMember: Boolean,
  /** نشستِ پیک دارد */
  val courier: Boolean,
  /** شمارهٔ خریدارِ تأییدشده دارد — «خرید» در هر حال باز است */
  val shopperPhone: Boolean = false,
)

object AppModes {
  /** حالت‌های این کاربر، به ترتیبِ نمایش؛ «خرید» همیشه هست */
  fun available(f: ModeFacts): List<AppMode> = buildList {
    if (f.shopMember) add(AppMode.SHOP)
    if (f.courier) add(AppMode.COURIER)
    add(AppMode.SHOPPER)
  }

  /**
   *  حالتِ همین حالا: انتخابِ ذخیره‌شده اگر هنوز در دسترس است؛ وگرنه پیک برای
   *  نشستِ پیک، دکان برای عضوِ دکان، و در آخر «خرید». نصب‌های پیشین (بی
   *  انتخابِ ذخیره‌شده) همان رفتارِ امروز را می‌گیرند: پیک ⇐ پیک، بقیه ⇐ دکان.
   */
  fun resolve(saved: AppMode?, f: ModeFacts): AppMode {
    val can = available(f)
    if (saved != null && saved in can) return saved
    return when {
      f.courier -> AppMode.COURIER
      f.shopMember -> AppMode.SHOP
      else -> AppMode.SHOPPER
    }
  }

  /** خانهٔ هر حالت */
  fun home(mode: AppMode): String = when (mode) {
    AppMode.SHOP -> "home"
    AppMode.COURIER -> "courier"
    AppMode.SHOPPER -> "shopper/home"
  }
}
