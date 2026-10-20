package ir.vil3ntec.tohid.core.nav

/**
 *  نیازِ یک ماژول، صفحه یا ورودی — همان سدهای امروزِ برنامه، یک‌جا نام‌گذاری‌شده:
 *  `MANAGE` = `ShopRole.canOpenSettings` (تنظیمات، کارمندان)، `AUDIT` = `canSeeAudit`،
 *  و سه پرچمِ سرور (سفارشِ زنده، ویترین، گفت‌وگو).
 */
enum class Need { MANAGE, AUDIT, ORDERS_LIVE, STOREFRONT, CHAT }

/** جای نشستنِ یک ورودیِ ماژول */
enum class Place {
  /** فهرستِ ویرایشگرِ نوارِ پایین (`NavCatalog`) */
  NAV,
  /** ردیفی در «بیشتر» */
  MORE,
  /** کاشیِ «بخش‌ها»ی خانه */
  HOME,
}

/** گروه‌های «بیشتر» به ترتیبِ نمایش؛ «تنظیمات» همیشه آخر است و از تنظیمات می‌آید */
enum class MoreGroup(val title: Txt) {
  SALES(Txt("فروش و مشتری", "Sales & customers", "پلور او پېرودونکي")),
  STOCK(Txt("کالا و خرید", "Items & purchasing", "توکي او پېرود")),
  MONEY(Txt("پول و گزارش", "Money & reports", "پیسې او راپورونه")),
  SHOP(Txt("دکان", "Shop", "دوکان")),
  ACCOUNT(Txt("اشتراک و پشتیبانی", "Subscription & support", "ګډون او ملاتړ")),
}

/**
 *  آنچه کاربرِ همین لحظه دارد: نیازهای برآورده، پرچم‌های روشنِ ماژول و حالتِ
 *  برنامه. یک بار در پوسته ساخته می‌شود و هر ورودی/صفحه با آن سنجیده می‌شود.
 */
data class Access(
  val granted: Set<Need> = emptySet(),
  val flags: Set<String> = emptySet(),
  val mode: AppMode = AppMode.SHOP,
) {
  fun allows(needs: Set<Need>, flag: String? = null): Boolean =
    granted.containsAll(needs) && (flag == null || flag in flags)

  companion object {
    /** همه‌چیز باز (آزمون‌ها و پیش‌نمایش) */
    fun all(mode: AppMode = AppMode.SHOP, flags: Set<String> = emptySet()) = Access(Need.entries.toSet(), flags, mode)
  }
}
