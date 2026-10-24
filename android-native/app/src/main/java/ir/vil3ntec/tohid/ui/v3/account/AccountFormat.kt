package ir.vil3ntec.tohid.ui.v3.account

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrAccount
import ir.vil3ntec.tohid.core.nav.RoutePaths

/**
 *  کمک‌های خالصِ صفحه‌های حساب (آزمون: `AccountFormatTest`): تاریخ و مدت به زبانِ
 *  صفحه، نامِ نقش، و مسیرهای بخش‌های دیگر که از «حساب من» باز می‌شوند — یک جا،
 *  تا اگر گروهی نامِ مسیری را عوض کرد فقط همین‌جا عوض شود.
 */
object AccountFormat {

  /** سرور گاهی ثانیه می‌دهد و گاهی میلی‌ثانیه؛ هر دو درست خوانده می‌شوند */
  fun millis(raw: Long): Long = if (raw in 1 until 100_000_000_000L) raw * 1000 else raw

  /** `YYYY-MM-DD` ِ همان لحظه به وقتِ دستگاه */
  fun isoOf(ms: Long): String {
    val c = java.util.Calendar.getInstance().apply { timeInMillis = ms }
    val m = (c.get(java.util.Calendar.MONTH) + 1).toString().padStart(2, '0')
    val d = c.get(java.util.Calendar.DAY_OF_MONTH).toString().padStart(2, '0')
    return "${c.get(java.util.Calendar.YEAR)}-$m-$d"
  }

  /** تاریخِ عددی به تقویمِ زبانِ صفحه (فارسی/پشتو خورشیدی، انگلیسی میلادی); نامعلوم ⇒ «—» */
  fun date(raw: Long): String =
    if (raw <= 0) "—" else I18n.dateNumeric(isoOf(millis(raw)), I18n.lang, I18n.CAL_AUTO)

  /** «چقدر گذشته» (مدتِ عضویت): امروز، روز، ماه، سال و ماه — ماه ۳۰ و سال ۳۶۵ روز */
  fun span(from: Long, now: Long = System.currentTimeMillis()): String {
    if (from <= 0) return "—"
    val d = ((now - millis(from)) / 86_400_000L).coerceAtLeast(0)
    return when {
      d < 1 -> Str.today
      d < 31 -> Str.days(d)
      d < 365 -> Str.months(d / 30)
      else -> if ((d % 365) / 30 == 0L) Str.years(d / 365) else Str.yearsMonths(d / 365, (d % 365) / 30)
    }
  }

  /** نقش‌های دکان (`ShopRole`) */
  enum class Role { OWNER, MANAGER, STAFF, COURIER }

  fun roleOf(raw: String): Role = when (raw.trim().lowercase()) {
    "manager" -> Role.MANAGER
    "staff" -> Role.STAFF
    "courier" -> Role.COURIER
    else -> Role.OWNER
  }

  /** نامِ نقش در فهرستِ کارمندان: صاحب دکان / مدیر / شاگرد / پیک */
  fun teamRole(raw: String): String = when (roleOf(raw)) {
    Role.OWNER -> StrAccount.roleOwner
    Role.MANAGER -> StrAccount.roleManager
    Role.STAFF -> StrAccount.roleStaff
    Role.COURIER -> StrAccount.roleCourier
  }
}

/** مسیرهای بخش‌های دیگر که صفحه‌های حساب باز می‌کنند (پیوند با مسیر، نه import) */
object AccountLinks {
  const val NOTIFICATIONS = "notifications"
  const val SALES = "sales"
  const val PRODUCTS = "products"
  const val DEBTORS = "debtors"
  /** دستگاه‌های جانبی (چاپگر، اسکنر) — مقصدِ امروزِ ردیفِ «دستگاه‌ها» */
  const val DEVICES = "devices"
  const val SETTINGS = "settings"
  const val CODE_SIGN_IN = "auth/code"
  val STATS: String get() = RoutePaths.withQuery("reports", "tab" to "profit", "range" to "month")
  val SIGN_IN: String get() = RoutePaths.withQuery("auth", "mode" to "signin")
  val SIGN_UP: String get() = RoutePaths.withQuery("auth", "mode" to "signup")
}
