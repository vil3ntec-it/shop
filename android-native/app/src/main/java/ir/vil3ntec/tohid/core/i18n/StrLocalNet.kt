package ir.vil3ntec.tohid.core.i18n

/**
 *  اجازهٔ شبکهٔ محلیِ اندروید ۱۷ برای چاپگرِ شبکه (HW-13).
 *  قاعده‌ها سرِ `I18n.kt`؛ ترجمه‌های نامطمئن در `docs/i18n-glossary.md`.
 */
@Suppress("unused")
object StrLocalNet {
  val needed: String get() = t(
    "این اندروید برای وصل شدن به چاپگرِ شبکه اجازهٔ «شبکهٔ محلی» (دستگاه‌های نزدیک) می‌خواهد.",
    "This Android version needs the \"local network\" (nearby devices) permission to reach a network printer.",
    "دا اندروید د شبکې چاپګر سره د نښلولو لپاره د «محلي شبکې» (نږدې وسیلې) اجازه غواړي.",
  )
  val allow: String get() = t("اجازه دادن", "Allow", "اجازه ورکول")
  val denied: String get() = t(
    "اجازهٔ «شبکهٔ محلی» داده نشده — در برگهٔ چاپگر «اجازه دادن» را بزنید یا از تنظیماتِ اندروید (دستگاه‌های نزدیک) اجازه دهید",
    "The \"local network\" permission isn't granted — tap \"Allow\" on the printer sheet or allow it in Android settings (nearby devices)",
    "د «محلي شبکې» اجازه نه ده ورکړل شوې — د چاپګر په پاڼه کې «اجازه ورکول» ووهئ یا د اندروید له امستنو (نږدې وسیلې) اجازه ورکړئ",
  )
}
