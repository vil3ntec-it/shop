package ir.vil3ntec.tohid.core.legal

import ir.vil3ntec.tohid.core.text.DariText

/**
 *  «حریمِ خصوصی» و «شرایط و ضوابط» درونِ خودِ برنامه‌ها (نه صفحهٔ بیرونی): برنامهٔ دکان (اندروید، کامپیوتر، آیفون، حالتِ پیک)
 *  و برنامهٔ خرید (اندروید، آیفون) همین متن را بی اینترنت نشان می‌دهند؛ سرور همان را در `GET /legal/privacy|terms` (HTML ِ
 *  عمومی برای فهرستِ Google Play و App Store) و `GET /api/auth/terms` می‌دهد.
 *
 *  ⛔ متن این‌جا نوشته نمی‌شود: منبعِ یکتا `core/legal/legal-texts.json` است و `LegalTexts{Fa,En,Ps}.kt` را
 *  `node tools/legal/generate.mjs` می‌سازد (`server/test/legal-texts.test.js` کهنه ماندنِ رونوشت را می‌گیرد).
 *  دری (`prs`) نسخهٔ جدا ندارد: همان فارسی از لایهٔ واژهٔ [DariText]، مثلِ همهٔ نوشته‌های برنامه.
 *
 *  نشانه‌گذاریِ متن فقط دو چیز است و [spans] آن را می‌خواند: `**پررنگ**` و `[نوشته](/مسیرِ-نسبیِ-همان-سرور)`.
 */
enum class LegalKind(val slug: String) {
  PRIVACY("privacy"),
  TERMS("terms");

  companion object {
    fun of(slug: String?): LegalKind? = entries.firstOrNull { it.slug == slug }
  }
}

class LegalSection(val id: String, val title: String, val paras: List<String>, val items: List<String>) {
  internal fun map(f: (String) -> String) = LegalSection(id, f(title), paras.map(f), items.map(f))
}

class LegalDoc(
  val kind: LegalKind,
  val version: String,
  /** نامِ کوتاه برای ردیف و دکمه («حریمِ خصوصی»، «شرایط و ضوابط») */
  val short: String,
  val title: String,
  /** خطِ زیرِ عنوان (تاریخِ بازبینی)؛ شرایط ندارد ⇒ «نسخهٔ …» */
  val lead: String,
  val sections: List<LegalSection>,
) {
  internal fun map(f: (String) -> String) = LegalDoc(kind, version, f(short), f(title), f(lead), sections.map { it.map(f) })
}

/** [version] با `{v}`؛ [language] نامِ گزینشگرِ زبانِ صفحهٔ وب؛ [heading] سرِ دو ردیفِ «حریمِ خصوصی» و «شرایط» در تنظیمات */
class LegalLabels(val version: String, val language: String, val heading: String) {
  /** «نسخهٔ ۱» — رقمِ فارسی برای فارسی، دری و پشتو */
  fun version(v: String, rtl: Boolean): String = version.replace("{v}", if (rtl) Legal.localDigits(v) else v)
}

/** یک زبانِ ساخته‌شده (`LegalTexts{Fa,En,Ps}.kt`) */
internal interface LegalTextSet {
  val labels: LegalLabels
  val privacy: LegalDoc
  val terms: LegalDoc
}

/** یک تکهٔ متن: پررنگ یا پیوند به مسیرِ نسبیِ همان سرور (`/portal/#delete-account`) */
data class LegalSpan(val text: String, val bold: Boolean = false, val link: String? = null)

object Legal {
  /** همان نسخه‌ای که ثبت‌نام می‌فرستد و سرور روی حساب می‌نویسد (`users.terms_version`) */
  const val TERMS_VERSION: String = LegalTextsFa.TERMS_VERSION
  const val PRIVACY_VERSION: String = LegalTextsFa.PRIVACY_VERSION

  private fun set(lang: String?): LegalTextSet = when (lang) {
    "en" -> LegalTextsEn
    "ps" -> LegalTextsPs
    else -> LegalTextsFa
  }

  /** کدِ زبانِ برنامه (`fa`، `prs`، `en`، `ps`)؛ ناشناخته ⇒ فارسی */
  fun doc(kind: LegalKind, lang: String?): LegalDoc {
    val base = set(lang).let { if (kind == LegalKind.PRIVACY) it.privacy else it.terms }
    return if (lang == "prs") base.map(DariText::of) else base
  }

  fun labels(lang: String?): LegalLabels = set(lang).labels.let {
    if (lang == "prs") LegalLabels(DariText.of(it.version), DariText.of(it.language), DariText.of(it.heading)) else it
  }

  /** راست‌به‌چپ؟ (فارسی، دری، پشتو) */
  fun rtl(lang: String?): Boolean = lang != "en"

  /** خطِ زیرِ عنوان: تاریخِ بازبینی، یا «نسخهٔ …» برای شرایط */
  fun subtitle(doc: LegalDoc, lang: String?): String = doc.lead.ifBlank { labels(lang).version(doc.version, rtl(lang)) }

  private val MARK = Regex("""\*\*([^*\n]+)\*\*|\[([^\]\n]+)]\((/[A-Za-z0-9/_#.?=&-]*)\)""")

  /** متن ⇒ تکه‌ها؛ تکهٔ ساده هم یک [LegalSpan] است، پس چسباندنِ `text` ِ همه همان [plain] است */
  fun spans(s: String): List<LegalSpan> {
    val out = ArrayList<LegalSpan>()
    var at = 0
    for (m in MARK.findAll(s)) {
      if (m.range.first > at) out += LegalSpan(s.substring(at, m.range.first))
      val bold = m.groups[1]?.value
      out += if (bold != null) LegalSpan(bold, bold = true) else LegalSpan(m.groupValues[2], link = m.groupValues[3])
      at = m.range.last + 1
    }
    if (at < s.length) out += LegalSpan(s.substring(at))
    return out
  }

  /** همان متن بی نشانه‌گذاری */
  fun plain(s: String): String = spans(s).joinToString("") { it.text }

  internal fun localDigits(s: String): String = buildString { for (c in s) append(if (c in '0'..'9') '۰' + (c - '0') else c) }
}
