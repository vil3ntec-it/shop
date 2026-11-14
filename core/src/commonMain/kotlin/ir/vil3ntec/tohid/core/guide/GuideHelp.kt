package ir.vil3ntec.tohid.core.guide

/**
 *  OPS-06 — دکمهٔ «راهنما» ِ گوشی و کامپیوتر (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۱۶: متنِ آماده حالا، ویدیو بعداً).
 *
 *  متن همان [ScreenGuides] ِ تأییدشده است که F1 ِ کامپیوتر هم نشان می‌دهد؛ درونِ برنامه است، پس بی اینترنت باز می‌شود.
 *  زبان: [GuideText.of] (انگلیسی؛ دری = فارسی از `DariText`؛ گامِ پشتوی نانوشته ⇐ فارسی). دکمه فقط روی **صفحهٔ ریشهٔ**
 *  بخشی که راهنمای آماده دارد ([forRoute])؛ ویدیو فقط وقتی راهنما ویدیو دارد ([View.video]) — بی آن هیچ دکمهٔ مرده‌ای نیست.
 */
object GuideHelp {

  /** آنچه برگهٔ راهنما نشان می‌دهد، به یک زبان */
  data class View(val screen: String, val title: String, val steps: List<String>, val video: GuideVideo?)

  fun view(g: ScreenGuide, lang: String): View = View(g.screen, g.title.of(lang), g.steps.map { it.of(lang) }, g.video)

  /**
   *  پاره‌های مسیرِ باز (`RoutePaths.parse(...).first`) ⇒ راهنمای همان صفحه؛ فقط مسیرِ یک‌پاره (ریشهٔ بخش)، با همان
   *  نام‌های دیگرِ نسخهٔ ۳ ([ScreenGuides.screenOf]). زیرصفحه، مسیرِ خالی یا بخشِ بی راهنمای آماده ⇒ `null`.
   */
  fun forRoute(segments: List<String>): ScreenGuide? =
    segments.singleOrNull()?.let { ScreenGuides.forScreen(ScreenGuides.screenOf(it)) }
}
