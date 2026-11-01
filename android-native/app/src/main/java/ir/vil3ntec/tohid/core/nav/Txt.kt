package ir.vil3ntec.tohid.core.nav

import ir.vil3ntec.tohid.core.i18n.I18n

/**
 *  یک نوشتهٔ سه‌زبانه (فارسی، انگلیسی، پشتو) برای جاهایی که نوشته «داده» است نه
 *  کد — عنوانِ ماژول، نامِ گروهِ «بیشتر»، نامِ حالتِ برنامه. همان قاعدهٔ `t()`ِ
 *  جدولِ نوشته‌ها (`core/i18n`): فارسی اجباری، جاافتاده ⇒ فارسی، و زبانِ همان
 *  لحظه هر بار که `text` خوانده شود.
 */
data class Txt(val fa: String, val en: String? = null, val ps: String? = null) {
  val text: String get() = I18n.pick(I18n.lang, fa, en, ps)
}
