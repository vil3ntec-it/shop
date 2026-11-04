package ir.vil3ntec.tohid.core.i18n

/**
 *  پوشِ گوشی (Firebase، تصمیمِ O-01) — «تنظیمات» ← «سفارشِ آنلاین» و اعلان‌هایی که خودِ برنامه از پیامِ سرور می‌سازد
 *  (`data/push/FcmPushAndroid.kt`). جدا از `StrOrderFlow` تا آن زیرِ سقفِ اندازه بماند.
 */
@Suppress("unused")
object StrPush {

  // ── یک خط برای هر حالِ `PushPolicy.State` (نگاشت در `OnlineOrdersSettingsAndroid.pushLine`) ──
  val active: String get() = t(
    "اعلانِ گوشی روشن است: برنامه بسته هم باشد، سفارشِ تازه، پرداخت و لغوِ مشتری روی گوشی خبر داده می‌شود.",
    "Phone notifications are on: even with the app closed, new orders, payments and customer cancellations ring on this phone.",
    "د تلیفون خبرتیا فعاله ده: که برنامه تړلې هم وي، نوی فرمایش، تادیه او د پېرودونکي لغوه پر تلیفون خبر ورکول کېږي.",
  )
  val notBundled: String get() = t(
    "این نسخهٔ برنامه هنوز به سرویسِ اعلانِ گوشی (Firebase) وصل نیست؛ پس از راه‌اندازی با یک به‌روزرسانی روشن می‌شود.",
    "This app version isn't connected to the phone notification service (Firebase) yet; an update will switch it on once it is set up.",
    "دا نسخه لا د تلیفون د خبرتیا له خدمت (Firebase) سره نه ده وصل؛ له جوړېدو وروسته به په یوه تازه نسخه فعاله شي.",
  )
  val flagOff: String get() = t(
    "اعلانِ گوشی با برنامهٔ بسته برای این دکان هنوز روشن نشده؛ روشن کردنش با پشتیبانی است.",
    "Phone notifications with the app closed aren't switched on for this shop yet; support switches them on.",
    "د تړلې برنامې سره د تلیفون خبرتیا لا د دې دوکان لپاره نه ده فعاله شوې؛ فعالول یې د ملاتړ کار دی.",
  )
  val serverOff: String get() = t(
    "این گوشی ثبت شد، ولی سرور هنوز برای فرستادنِ اعلانِ گوشی آماده نیست؛ این کارِ پشتیبانیِ سرور است.",
    "This phone is registered, but the server isn't ready to send phone notifications yet; server support fixes this.",
    "دا تلیفون ثبت شو، خو سرور لا د تلیفون د خبرتیا لېږلو ته چمتو نه دی؛ دا د سرور د ملاتړ کار دی.",
  )
  val noToken: String get() = t(
    "این گوشی برای اعلان ثبت نشد؛ «خدماتِ Google Play» روشن و به‌روز باشد و اینترنت وصل.",
    "This phone couldn't register for notifications; make sure Google Play services are on and up to date, and you're online.",
    "دا تلیفون د خبرتیا لپاره ثبت نه شو؛ «Google Play خدمتونه» دې فعال او تازه وي او انټرنېټ دې وصل وي.",
  )

  val allowNotifications: String get() = t("اجازهٔ اعلان", "Allow notifications", "د خبرتیا اجازه")
  val allowNotificationsHelp: String get() = t(
    "بی این اجازه، سفارشِ تازه زنگ نمی‌زند — نه با برنامهٔ باز، نه بسته.",
    "Without this permission new orders don't ring — whether the app is open or closed.",
    "بې دې اجازې نوی فرمایش زنګ نه وهي — نه پرانیستې برنامه، نه تړلې.",
  )

  // ── نامِ کانال‌های اعلانِ اندروید (تنظیماتِ گوشی ← اعلان‌های این برنامه؛ شناسه‌ها همان `message.channel` ِ سرور) ──
  val channelOrders: String get() = t("سفارش‌های آنلاین", "Online orders", "آنلاین فرمایشونه")
  val channelEvents: String get() = t("خبرهای دکان", "Shop alerts", "د دوکان خبرونه")
  val channelNotice: String get() = t("خبرهای سامانه", "Announcements", "د سامانې خبرونه")
  val channelAlerts: String get() = t("هشدارهای مهم", "Important alerts", "مهم خبرداری")
  val channelSupport: String get() = t("پشتیبانی", "Support", "ملاتړ")
}
