package ir.vil3ntec.tohid.core.shopper

import ir.vil3ntec.tohid.core.shopper.ShopperText.t

/**
 *  RATE-01 — برگهٔ «ستاره بدهید» ِ برنامهٔ خرید (اندروید و آیفون، `customer-android/…/ui/RatePromptShopper.kt`) — همان
 *  جمله‌های `StrRate` ِ برنامهٔ دکان؛ فایلِ جدا چون [ShopperText] به سقفِ اندازه رسیده. آزمون: `RateTextCoverageTest`.
 *  ⛔ قاعدهٔ فروشگاه‌ها: نه پرسشِ «دوست دارید؟»، نه شمارِ ستاره، نه جایزه (`docs/RULES-fa.md` «ستاره دادن»).
 */
object RateText {
  val title get() = t("کمک کنید برنامه بهتر شود", "Help make the app better", "مرسته وکړئ چې اپ ښه شي")
  /** برگه فقط Play است: App Store برگهٔ خودِ برنامه ندارد (`RateTarget.ownSheet`، قاعدهٔ ۵.۶.۱ ِ اپل) */
  val body get() = t(
    "اگر برنامه به کارتان آمده، با ستاره دادن در گوگل‌پلی کمک کنید بهتر شود 🙏",
    "If the app has been useful to you, rating it on Google Play helps make it better 🙏",
    "که اپ مو په کار درغلی، په ګوګل پلې کې په ستوري ورکولو مرسته وکړئ چې ښه شي 🙏",
  )
  val rate get() = t("ستاره می‌دهم", "Rate the app", "ستوري ورکوم")
  val later get() = t("بعداً", "Later", "وروسته")
  val never get() = t("دیگر نپرس", "Don't ask again", "بیا مه پوښتئ")
}
