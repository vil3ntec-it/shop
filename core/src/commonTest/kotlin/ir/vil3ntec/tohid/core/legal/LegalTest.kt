package ir.vil3ntec.tohid.core.legal

import ir.vil3ntec.tohid.core.text.DariText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 *  متنِ درونِ برنامهٔ «حریمِ خصوصی» و «شرایط و ضوابط» ([Legal]): هر چهار زبانِ برنامه کامل و هم‌ساختار، دری از لایهٔ واژه،
 *  نسخهٔ شرایط همان قراردادِ ثبت‌نام، و هیچ نشانیِ بیرونی (GitHub یا میزبان) در هیچ زبانی. یکی بودنِ این رونوشت با منبع را
 *  `server/test/legal-texts.test.js` می‌سنجد.
 */
class LegalTest {

  private val langs = listOf("fa", "prs", "en", "ps")
  private val letter = Regex("[\\u0600-\\u06FF]")

  private fun all(d: LegalDoc): List<String> =
    listOf(d.short, d.title, d.lead) + d.sections.flatMap { listOf(it.title) + it.paras + it.items }

  @Test fun every_language_has_the_same_sections_as_persian() {
    for (kind in LegalKind.entries) {
      val fa = Legal.doc(kind, "fa")
      assertTrue(fa.sections.size >= 7, "$kind بخش کم دارد")
      for (lang in langs) {
        val d = Legal.doc(kind, lang)
        assertEquals(fa.sections.map { it.id }, d.sections.map { it.id }, "$kind $lang")
        d.sections.forEachIndexed { i, s ->
          assertEquals(fa.sections[i].paras.size, s.paras.size, "$kind $lang ${s.id}")
          assertEquals(fa.sections[i].items.size, s.items.size, "$kind $lang ${s.id}")
          assertTrue(s.title.isNotBlank() && (s.paras + s.items).all { it.isNotBlank() }, "$kind $lang ${s.id} متنِ خالی")
        }
        assertTrue(d.title.isNotBlank() && d.short.isNotBlank())
      }
    }
  }

  @Test fun english_and_pashto_are_real_translations() {
    for (kind in LegalKind.entries) {
      val fa = all(Legal.doc(kind, "fa"))
      val en = all(Legal.doc(kind, "en"))
      val ps = all(Legal.doc(kind, "ps"))
      for (s in en) assertFalse(letter.containsMatchIn(s), "حرفِ فارسی در انگلیسی: $s")
      //  پشتو همان فارسی نیست: هیچ بند و عنوانی رونوشتِ فارسی نیست (خطِ خالیِ lead ِ شرایط بیرون)
      fa.indices.filter { fa[it].isNotBlank() }.forEach { assertNotEquals(fa[it], ps[it], "پشتو همان فارسی است: ${fa[it]}") }
    }
  }

  @Test fun dari_is_persian_through_the_word_layer() {
    val fa = Legal.doc(LegalKind.TERMS, "fa")
    val prs = Legal.doc(LegalKind.TERMS, "prs")
    assertEquals(DariText.of(fa.sections[0].title), prs.sections[0].title)
    //  «حفظ و محرمانگی اطلاعات» ⇐ «… معلومات»
    assertNotEquals(fa.sections[0].title, prs.sections[0].title)
    assertTrue(prs.sections[0].title.contains("معلومات"))
    //  نامِ برنامه در دری هم همان است
    assertTrue(Legal.doc(LegalKind.PRIVACY, "prs").title.contains("فروشگاه من"))
  }

  @Test fun unknown_language_falls_back_to_persian() {
    assertEquals(Legal.doc(LegalKind.PRIVACY, "fa").title, Legal.doc(LegalKind.PRIVACY, "de").title)
    assertEquals(Legal.doc(LegalKind.PRIVACY, "fa").title, Legal.doc(LegalKind.PRIVACY, null).title)
    assertEquals(LegalKind.TERMS, LegalKind.of("terms"))
    assertEquals(null, LegalKind.of("github"))
  }

  @Test fun terms_version_is_the_signup_contract() {
    //  همان `VERSION` ِ `server/src/lib/terms.js` که `POST /auth/register/complete` روی حساب می‌نویسد
    assertEquals("1", Legal.TERMS_VERSION)
    for (lang in langs) assertEquals(Legal.TERMS_VERSION, Legal.doc(LegalKind.TERMS, lang).version)
    assertEquals("Version 1", Legal.subtitle(Legal.doc(LegalKind.TERMS, "en"), "en"))
    assertEquals("نسخهٔ ۱", Legal.subtitle(Legal.doc(LegalKind.TERMS, "fa"), "fa"))
    assertEquals("نسخه ۱", Legal.subtitle(Legal.doc(LegalKind.TERMS, "ps"), "ps"))
    //  حریمِ خصوصی خطِ تاریخِ بازبینی دارد
    assertTrue(Legal.subtitle(Legal.doc(LegalKind.PRIVACY, "fa"), "fa").contains("۱۴۰۵/۰۸/۱۳"))
  }

  @Test fun no_language_shows_an_outside_address() {
    for (kind in LegalKind.entries) for (lang in langs) for (s in all(Legal.doc(kind, lang))) {
      assertFalse(s.contains("github", ignoreCase = true), "github: $s")
      assertFalse(s.contains("http"), "نشانیِ بیرونی: $s")
      for (span in Legal.spans(s)) span.link?.let { assertTrue(it.startsWith("/") && !it.startsWith("//"), "پیوندِ بیرونی: $it") }
    }
  }

  @Test fun markup_is_bold_and_relative_links_only() {
    val s = Legal.spans("**حساب:** نام و [پورتال](/portal/#delete-account) — تمام")
    assertEquals(listOf(LegalSpan("حساب:", bold = true), LegalSpan(" نام و "), LegalSpan("پورتال", link = "/portal/#delete-account"), LegalSpan(" — تمام")), s)
    assertEquals("حساب: نام و پورتال — تمام", Legal.plain("**حساب:** نام و [پورتال](/portal/#delete-account) — تمام"))
    assertEquals(listOf(LegalSpan("بی نشانه")), Legal.spans("بی نشانه"))
    //  پیوندِ حذفِ حساب از وب در هر سه زبان هست
    for (lang in listOf("fa", "en", "ps")) {
      val links = Legal.doc(LegalKind.PRIVACY, lang).sections.flatMap { it.items }.flatMap { Legal.spans(it) }.mapNotNull { it.link }
      assertEquals(listOf("/portal/#delete-account"), links, lang)
    }
  }
}
