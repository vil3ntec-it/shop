package ir.vil3ntec.tohid.core.i18n

/**
 *  حالِ همگام‌سازی به زبانِ ساده، بی هیچ جزئیاتِ فنی/زیرساخت (قاعدهٔ «هیچ جزئیاتِ فنی/زیرساخت در صفحه»،
 *  `docs/RULES-fa.md`): نه نشانیِ سرور، نه نسخه، نه متنِ خامِ خطا — فقط شمارهای دادهٔ خودِ کاربر و این‌که پشتیبانی
 *  خبر دارد. جزئیاتِ فنی فقط برای پشتیبانی فرستاده می‌شود (`data/SupportDiag`). نگهبان: `InfraLeakGuardTest`.
 *
 *  قاعده‌ها سرِ `I18n.kt`.
 */
@Suppress("unused")
object StrSyncPlain {
  fun rejectedTitle(n: Any): String = t(
    "${asIs(n)} تغییر پذیرفته نشد",
    "$n ${pl(n, "change wasn't", "changes weren't")} accepted",
    "$n ${pl(n, "بدلون ونه منل شو", "بدلونونه ونه منل شول")}",
  )
  val supportNotified: String get() = t("پشتیبانی خبردار شد", "Support has been told", "ملاتړ ته خبر ورکړل شو")
  val supportPending: String get() = t(
    "جزئیات برای پشتیبانی فرستاده می‌شود",
    "The details will be sent to support",
    "جزئیات به ملاتړ ته ولېږل شي",
  )
}
