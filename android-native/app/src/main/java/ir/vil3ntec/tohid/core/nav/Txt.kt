package ir.vil3ntec.tohid.core.nav

/**
 *  یک نوشتهٔ سه‌زبانه (فارسی، انگلیسی، پشتو) برای جاهایی که نوشته «داده» است نه
 *  کد — عنوانِ ماژول، برچسبِ مسیر، نامِ بخشِ «بیشتر».
 *
 *  همان شکلِ `t(fa, en, ps)`ِ جدولِ نوشته‌ها (`core/i18n`، PR #348): فارسی اجباری،
 *  جاافتاده ⇒ فارسی. زبانِ همین لحظه از `UiLang` خوانده می‌شود، پس `Txt` را
 *  می‌شود در فهرستِ ثابت نگه داشت و هر بار که `text` خوانده شود زبانِ تازه را
 *  می‌دهد.
 */
data class Txt(val fa: String, val en: String? = null, val ps: String? = null) {
  val text: String get() = UiLang.pick(fa, en, ps)
}

/**
 *  زبانِ صفحه برای `Txt` و `StrV3`. تا جدولِ نوشته‌های `core/i18n` ادغام شود
 *  همیشه فارسی است (رفتارِ امروز)؛ پس از ادغام، `code` به `I18n.lang.code` وصل
 *  می‌شود و هیچ صفحه‌ای عوض نمی‌شود.
 */
object UiLang {
  @Volatile var code: () -> String = { "fa" }

  fun pick(fa: String, en: String?, ps: String?): String = when (code()) {
    "en" -> en ?: fa
    "ps" -> ps ?: fa
    else -> fa
  }
}
