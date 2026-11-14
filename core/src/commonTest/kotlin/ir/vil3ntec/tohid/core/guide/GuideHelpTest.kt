package ir.vil3ntec.tohid.core.guide

import ir.vil3ntec.tohid.core.text.DariText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 *  OPS-06 (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۱۶): دکمهٔ «راهنما» ِ گوشی و کامپیوتر همان متنِ تأییدشده را به زبانِ برنامه
 *  نشان می‌دهد — بی اینترنت (متن درونِ core است)، دری از فارسی، گامِ پشتوی نانوشته ⇐ فارسی — و فقط روی صفحهٔ ریشهٔ
 *  بخشی که راهنمای آماده دارد. ویدیو «بعداً»: بی ویدیو هیچ ردیفِ ویدیویی (هیچ دکمهٔ مرده‌ای) نیست.
 */
class GuideHelpTest {

  @Test
  fun everyReadyGuideOpensInEveryLanguage() {
    for (g in ScreenGuides.ALL) {
      val fa = GuideHelp.view(g, "fa")
      val en = GuideHelp.view(g, "en")
      val prs = GuideHelp.view(g, "prs")
      val ps = GuideHelp.view(g, "ps")
      assertEquals(g.steps.map { it.fa }, fa.steps, g.screen)
      assertEquals(g.steps.map { it.en }, en.steps, g.screen)
      assertEquals(g.steps.map { DariText.of(it.fa) }, prs.steps, g.screen)
      assertEquals(g.steps.map { it.ps.ifBlank { it.fa } }, ps.steps, g.screen)
      assertEquals(g.title.ps.ifBlank { g.title.fa }, ps.title, g.screen)
      for (v in listOf(fa, en, prs, ps)) {
        assertTrue(v.title.isNotBlank() && v.steps.isNotEmpty() && v.steps.all { it.isNotBlank() }, "${g.screen}/${v.screen}")
        //  امروز هیچ راهنمایی ویدیو ندارد ⇒ ردیفِ ویدیو نیست
        assertNull(v.video, g.screen)
      }
    }
  }

  @Test
  fun buttonOnlyOnTheRootPageOfAGuidedSection() {
    assertEquals("dashboard", GuideHelp.forRoute(listOf("home"))?.screen)
    assertEquals("warehouse", GuideHelp.forRoute(listOf("stock"))?.screen)
    for (s in listOf("sale", "debtors", "receipts", "products", "purchasing", "sales", "expenses", "reports", "orders", "storefront", "promos", "team", "devices", "settings")) {
      assertEquals(s, GuideHelp.forRoute(listOf(s))?.screen, s)
    }
    //  زیرصفحه، مسیرِ خالی و بخشِ بی راهنما ⇒ دکمه نیست
    assertNull(GuideHelp.forRoute(listOf("debtors", "d1")))
    assertNull(GuideHelp.forRoute(emptyList()))
    assertNull(GuideHelp.forRoute(listOf("more")))
    assertNull(GuideHelp.forRoute(listOf("notifications")))
  }

  @Test
  fun aVideoAppearsOnlyWhenTheGuideHasOne() {
    val g = ScreenGuides.ALL.first().copy(video = GuideVideo("/guides/home.mp4", 45, 3_000_000))
    assertEquals(GuideVideo("/guides/home.mp4", 45, 3_000_000), GuideHelp.view(g, "fa").video)
  }
}
