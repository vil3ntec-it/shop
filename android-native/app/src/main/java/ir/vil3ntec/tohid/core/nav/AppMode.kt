package ir.vil3ntec.tohid.core.nav

/**
 *  یک برنامهٔ مدیریت، دو حالت (دستورِ صاحب محصول، ۱۴۰۵/۰۷/۲۸؛ «خرید» با تصمیمِ
 *  ۱۴۰۵/۰۸/۰۸ به برنامهٔ جدای خریدِ مشتری رفت):
 *   • **دکان** — مدیریتِ دکان برای صاحب و کارمند (برنامهٔ امروز)؛
 *   • **پیک** — حالتِ پیک (امروز فقط با پیوستن با کدِ کارمند).
 *  هر حالت نوارِ پایین و خانهٔ خودش را دارد.
 */
enum class AppMode(val id: String, val title: Txt) {
  SHOP("shop", Txt("دکان", "Shop", "دوکان")),
  COURIER("courier", Txt("پیک", "Courier", "رسوونکی"));

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
)

object AppModes {
  /** حالت‌های این کاربر، به ترتیبِ نمایش؛ کسی که پیک نیست همیشه «دکان» را دارد */
  fun available(f: ModeFacts): List<AppMode> = buildList {
    if (f.shopMember || !f.courier) add(AppMode.SHOP)
    if (f.courier) add(AppMode.COURIER)
  }

  /**
   *  حالتِ همین حالا: انتخابِ ذخیره‌شده اگر هنوز در دسترس است؛ وگرنه پیک برای
   *  نشستِ پیک و دکان برای بقیه — همان رفتارِ نصب‌های پیشین (بی انتخابِ ذخیره‌شده).
   *  انتخابِ کهنهٔ «خرید» (`AppMode.of("shopper") == null`) هم همین‌جا به دکان/پیک می‌رسد.
   */
  fun resolve(saved: AppMode?, f: ModeFacts): AppMode {
    val can = available(f)
    if (saved != null && saved in can) return saved
    return if (f.courier) AppMode.COURIER else AppMode.SHOP
  }

  /** خانهٔ هر حالت */
  fun home(mode: AppMode): String = when (mode) {
    AppMode.SHOP -> "home"
    AppMode.COURIER -> "courier"
  }
}
