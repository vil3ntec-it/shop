package ir.vil3ntec.tohid.core.i18n

/**
 *  RATE-01 — برگهٔ «ستاره بدهید» (`ui/v3/rate/RateSheet.kt`). قاعده‌ها سرِ `I18n.kt`.
 *  ⛔ قاعدهٔ فروشگاه‌ها: نه پرسشِ «دوست دارید؟»، نه شمارِ ستاره («پنج ستاره»)، نه جایزه (`docs/RULES-fa.md` «ستاره دادن»).
 */
@Suppress("unused")
object StrRate {
  val title: String get() = t("کمک کنید برنامه بهتر شود", "Help make the app better", "مرسته وکړئ چې اپ ښه شي")
  val bodyPlay: String get() = t(
    "اگر برنامه به کارتان آمده، با ستاره دادن در گوگل‌پلی کمک کنید بهتر شود 🙏",
    "If the app has been useful to you, rating it on Google Play helps make it better 🙏",
    "که اپ مو په کار درغلی، په ګوګل پلې کې په ستوري ورکولو مرسته وکړئ چې ښه شي 🙏",
  )
  val bodyBazaar: String get() = t(
    "اگر برنامه به کارتان آمده، با ستاره دادن در کافه‌بازار کمک کنید بهتر شود 🙏",
    "If the app has been useful to you, rating it on Cafe Bazaar helps make it better 🙏",
    "که اپ مو په کار درغلی، په کافه بازار کې په ستوري ورکولو مرسته وکړئ چې ښه شي 🙏",
  )
  val rate: String get() = t("ستاره می‌دهم", "Rate the app", "ستوري ورکوم")
  val later: String get() = t("بعداً", "Later", "وروسته")
  val never: String get() = t("دیگر نپرس", "Don't ask again", "بیا مه پوښتئ")
}
