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
  /* ── e-syncdiag: «چه شد» ِ کارتِ وضعیت (`SyncModel.reasonLine`) و بخشِ «سرور» (`SyncModel.serverNote`) ── */
  val reasonOffline: String get() = t("اینترنتِ این دستگاه وصل نیست", "This device has no internet", "دې وسیلې انټرنېټ نه لري")
  val reasonSignIn: String get() = t("ورود منقضی شده — دوباره وارد شوید", "Sign-in expired — sign in again", "ننوتل پای ته رسېدلی — بیا ننوځئ")
  val reasonServerError: String get() = t("سرور خطا داد — خودش دوباره می‌کوشد", "The server returned an error — it retries by itself", "سرور تېروتنه ورکړه — پخپله بیا هڅه کوي")
  val reasonUnreachable: String get() = t("همگام‌سازی به سرور نرسید — خودش دوباره می‌کوشد", "Sync didn't reach the server — it retries by itself", "همغږي سرور ته ونه رسېده — پخپله بیا هڅه کوي")
  val reasonOldServer: String get() = t(
    "سرورِ دکان قدیمی است — نسخهٔ برنامه تازه‌تر است؛ سرور را به‌روز کنید",
    "The shop server is out of date — the app is newer; update the server",
    "د دوکان سرور زوړ دی — اپ نوی دی؛ سرور تازه کړئ",
  )
  val reasonNoShop: String get() = t("این حساب دکان ندارد", "This account has no shop", "دا حساب دوکان نه لري")
  val reasonDevice: String get() = t("گره در خودِ برنامهٔ این دستگاه است، نه در سرور", "The problem is in the app on this device, not on the server", "ستونزه د دې وسیلې په اپ کې ده، نه په سرور کې")
  val reasonRefused: String get() = t("سرور درخواستِ این دستگاه را نپذیرفت", "The server refused this device's request", "سرور د دې وسیلې غوښتنه ونه منله")
  /** پیامِ سادهٔ خودِ سرور (`SyncModel.plain`) — داده، بی ترجمه */
  fun reasonRefusedSaid(message: Any): String = t("سرور نپذیرفت: ${asIs(message)}", "The server refused: $message", "سرور ونه منله: $message")
  val reasonUnknown: String get() = t(
    "علتش روشن نیست — «جزئیات» را برای پشتیبانی بفرستید",
    "The cause isn't clear — send “Details” to support",
    "لامل یې څرګند نه دی — «جزئیات» ملاتړ ته ولېږئ",
  )
  fun reasonRejectedOld(n: Any): String = t(
    "${asIs(n)} تغییر را سرور نشناخت — سرورِ دکان قدیمی است؛ سرور را به‌روز کنید",
    "The server didn't recognise $n ${pl(n, "change", "changes")} — the shop server is out of date; update it",
    "سرور $n ${pl(n, "بدلون", "بدلونونه")} ونه پېژندل — د دوکان سرور زوړ دی؛ سرور تازه کړئ",
  )
  fun reasonRejectedLarge(n: Any): String = t(
    "${asIs(n)} تغییر برای سرور بیش از اندازه بزرگ بود؛ بقیه رفتند",
    "$n ${pl(n, "change was", "changes were")} too large for the server; the rest went through",
    "$n ${pl(n, "بدلون", "بدلونونه")} د سرور لپاره ډېر لوی وو؛ نور ولاړل",
  )
  fun reasonRejectedInbox(n: Any): String = t(
    "${asIs(n)} تغییر را سرور نپذیرفت — در «سندهای ردشده» ببینید",
    "The server didn't accept $n ${pl(n, "change", "changes")} — see “Rejected documents”",
    "سرور $n ${pl(n, "بدلون", "بدلونونه")} ونه منل — په «رد شوي اسناد» کې یې وګورئ",
  )
  fun reasonRejected(n: Any): String = t(
    "${asIs(n)} تغییر را سرور نپذیرفت؛ بقیه رفتند",
    "The server didn't accept $n ${pl(n, "change", "changes")}; the rest went through",
    "سرور $n ${pl(n, "بدلون", "بدلونونه")} ونه منل؛ نور ولاړل",
  )
  /** زیرِ «در دسترس است» ِ بخشِ «سرور»: سرور پاسخ داد و نپذیرفت — همان چرای کارتِ وضعیت */
  fun serverRefusing(reason: Any): String = t("ولی تغییرها را نمی‌پذیرد: ${asIs(reason)}", "but it isn't accepting changes: $reason", "خو بدلونونه نه مني: $reason")
  /** زیرِ «در دسترس است»: سرور به سنجش جواب داد ولی دورِ همگام‌سازی نه، یا علتش روشن نیست */
  val serverNotSyncing: String get() = t("ولی همگام‌سازی با آن کامل نشد", "but sync with it didn't finish", "خو ورسره همغږي بشپړه نه شوه")
  val supportNotified: String get() = t("پشتیبانی خبردار شد", "Support has been told", "ملاتړ ته خبر ورکړل شو")
  val supportPending: String get() = t(
    "جزئیات برای پشتیبانی فرستاده می‌شود",
    "The details will be sent to support",
    "جزئیات به ملاتړ ته ولېږل شي",
  )
}
