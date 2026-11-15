package ir.vil3ntec.tohid.core.i18n

/**
 *  بخشِ ساخته‌شده‌ای که برای این دکان خاموش است — نشانِ «خاموش» ِ منوی کناری و برگهٔ «چه می‌کند و چطور روشن
 *  می‌شود» (`ui/screens/DeskOff.kt`). نامِ برگه‌های پنلِ سرور («تنظیمات و پرچم‌ها»، «پرچم‌ها»، «همهٔ دکان‌ها») نوشتهٔ
 *  خودِ پنل است و در فارسی و پشتو همان، و در دری با `asIs` همان می‌ماند تا روی پنل پیدا شود؛ «فروشگاه» در دری «دکان» می‌شود (واژهٔ
 *  ایرانی در دری نمی‌ماند، `DariWordsTest` — استثنا تصمیمِ صاحب‌کار است). انگلیسی حرفِ فارسی نمی‌گیرد (`I18nCoverageTest`)
 *  و ترجمه‌اش را دارد. نشانی و نامِ میزبان هرگز (`InfraLeakGuardTest`).
 */
@Suppress("unused")
object StrDeskOff {
  val badge: String get() = t("خاموش", "Off", "بند")
  val whyOff: String get() = t("چرا خاموش است", "Why it is off", "ولې بند دی")
  val built: String get() = t(
    "این بخش در برنامه ساخته شده و آماده است، ولی برای این دکان خاموش است.",
    "This section is built and ready in the app, but it is switched off for this shop.",
    "دا برخه په اپ کې جوړه او چمتو ده، خو د دې دوکان لپاره بنده ده.",
  )
  val builtStaff: String get() = t(
    "این بخش در برنامه ساخته شده، ولی برای این دکان خاموش است یا برای نقشِ شما باز نیست.",
    "This section is built in the app, but it is switched off for this shop or not open to your role.",
    "دا برخه په اپ کې جوړه ده، خو د دې دوکان لپاره بنده ده یا ستاسو رول ته نه ده خلاصه.",
  )
  val whatTitle: String get() = t("این بخش چه می‌کند", "What this section does", "دا برخه څه کوي")
  val howTitle: String get() = t("چطور روشن می‌شود", "How to switch it on", "څنګه فعالېږي")
  val fulfilWhat: String get() = t(
    "سفارش‌های پذیرفته را جمع کنید، بسته‌بندی کنید و برای پیک یا دریافت از دکان آماده کنید.",
    "Pick and pack accepted orders and get them ready for the courier or in-store pickup.",
    "منل شوي فرمایشونه راټول او بسته کړئ او د سپارونکي یا له دوکان څخه د اخیستلو لپاره یې چمتو کړئ.",
  )
  fun partOf(whole: Any): String = t(
    "این بخش جزوِ «${asIs(whole)}» است و با روشن شدنِ همان باز می‌شود.",
    "This section is part of \"$whole\" and opens when that is switched on.",
    "دا برخه د «$whole» برخه ده او د هغې په فعالېدو خلاصېږي.",
  )
  val stepPanel: String get() = t(
    "پنلِ سرور را باز کنید و به بخشِ «فروشگاه» بروید.",
    "Open the server panel and go to the \"Shop\" module.",
    "د سرور پینل پرانیزئ او «فروشگاه» برخې ته لاړ شئ.",
  )
  val stepTab: String get() = t(
    "برگهٔ «${asIs("تنظیمات و پرچم‌ها")}» را بزنید و کارتِ «${asIs("پرچم‌ها")}» را پیدا کنید.",
    "Open the \"Settings & flags\" tab and find the \"Flags\" card.",
    "«تنظیمات و پرچم‌ها» پاڼه پرانیزئ او «پرچم‌ها» کارت ومومئ.",
  )
  val stepScope: String get() = t(
    "دامنه را روی همین دکان بگذارید، نه «${asIs("همهٔ دکان‌ها")}».",
    "Set the scope to this shop, not \"All shops\".",
    "ساحه پر همدې دوکان کېږدئ، نه «همهٔ دکان‌ها».",
  )
  fun stepTurnOn(name: Any): String = t("«${asIs(name)}» را روشن کنید.", "Switch on \"$name\".", "«$name» فعال کړئ.")
  /** فقط برای صاحبِ دکان، نوشتهٔ کوچکِ زیرِ گام‌ها — کارمند نامِ درونیِ پرچم را نمی‌بیند */
  fun flagKey(key: Any): String = t("نامِ پرچم در پنل: ${asIs(key)}", "Flag name in the panel: $key", "په پینل کې د بیرغ نوم: $key")
  val askSupport: String get() = t("پیام به پشتیبانی برای روشن کردن", "Message support to switch it on", "د فعالولو لپاره ملاتړ ته پیغام")
  val callSupport: String get() = t(
    "برای روشن کردن با پشتیبانی تماس بگیرید.",
    "Contact support to switch it on.",
    "د فعالولو لپاره له ملاتړ سره اړیکه ونیسئ.",
  )
  fun supportDraft(name: Any): String = t(
    "سلام، لطفاً «${asIs(name)}» را برای همین دکان روشن کنید.",
    "Hello, please switch on \"$name\" for this shop.",
    "سلام، مهرباني وکړئ «$name» د دې دوکان لپاره فعال کړئ.",
  )
}
