package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.performSemanticsAction
import ir.vil3ntec.tohid.core.guide.ScreenGuides
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.ShopData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Rule
import org.junit.Test

/**
 *  OPS-06 (تصمیمِ ۱۴۰۵/۰۸/۲۳، پرسشِ ۱۶) — دکمهٔ «راهنما» ِ گوشیِ نسخهٔ ۳: صفحهٔ ریشهٔ بخشی که راهنمای آماده دارد دکمه
 *  دارد و برگه‌اش همان متنِ تأییدشدهٔ F1 است (درونِ برنامه، بی سرور)، بی ردیفِ ویدیو؛ زیرصفحه دکمه ندارد.
 */
class GuideButtonUiTest {

  @get:Rule val rule = createComposeRule()

  private val data = ShopData(debtors = listOf(Debtor(id = "d1", name = "احمد")))

  private fun guideButtons() = rule.onAllNodes(hasContentDescription(StrKit.guide) and hasClickAction()).fetchSemanticsNodes().size

  @Test fun rootPageHelpOpensTheApprovedTextWithoutVideo() {
    RouteUiHarness(rule).use { h ->
      h.show("debtors", data)
      h.awaitThat { guideButtons() > 0 }
      rule.onAllNodes(hasContentDescription(StrKit.guide) and hasClickAction())[0].performSemanticsAction(SemanticsActions.OnClick)
      rule.waitForIdle()
      val g = ScreenGuides.forScreen("debtors")!!
      h.await(StrKit.guideTitle(g.title.fa))
      for (s in g.steps) h.await(s.fa)
      h.await(StrKit.guideOffline)
      //  امروز ویدیویی نیست ⇒ هیچ دکمهٔ ویدیو
      assertFalse(h.has(StrKit.guideVideo(0).substringBefore('('), substring = true))
    }
  }

  @Test fun subPageHasNoHelpButton() {
    RouteUiHarness(rule).use { h ->
      h.show("debtors/d1", data)
      h.await("احمد")
      assertEquals(0, guideButtons())
    }
  }
}
