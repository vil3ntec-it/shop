package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.legal.Legal
import ir.vil3ntec.tohid.core.legal.LegalDoc
import ir.vil3ntec.tohid.core.legal.LegalKind

/**
 *  شرایط و ضوابط استفاده از برنامه — همان متنِ سرور (`GET /api/auth/terms`) و صفحهٔ عمومیِ `/legal/terms`، از منبعِ یکتای
 *  `core/legal/legal-texts.json` ([Legal]؛ پیش‌تر رونوشتِ دستیِ فارسی همین‌جا بود). `VERSION` هنگامِ پذیرش روی حساب
 *  ثبت می‌شود، پس بعداً معلوم است کاربر کدام متن را دیده — متن که عوض شد، نسخهٔ منبع یک واحد جلو می‌رود.
 *  عنوان و بندها به زبانِ همان لحظهٔ برنامه‌اند (فارسی، دری، انگلیسی، پشتو).
 */
object Terms {

  const val VERSION = Legal.TERMS_VERSION

  fun doc(): LegalDoc = Legal.doc(LegalKind.TERMS, I18n.lang.code)

  val TITLE: String get() = doc().title

  /** عنوانِ بند و متنش */
  val SECTIONS: List<Pair<String, String>> get() = doc().sections.map { it.title to (it.paras + it.items).joinToString("\n\n") }
}
