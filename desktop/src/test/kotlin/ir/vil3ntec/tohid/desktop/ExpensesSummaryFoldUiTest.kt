package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.performClick
import ir.vil3ntec.tohid.core.i18n.StrMoney
import ir.vil3ntec.tohid.data.Expense
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.v3.SummaryFold
import ir.vil3ntec.tohid.ui.v3.V3DataRowSpec
import ir.vil3ntec.tohid.ui.v3.money.MoneyText
import ir.vil3ntec.tohid.ui.v3.summaryFoldStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  p0r3 — گزارشِ صاحب‌کار (عکسِ «مصارف»): خلاصهٔ بسته «۱٬۲۶۶» و سه ردیف (دو مصرفِ امروز، یکی دیروز)، ولی پس از باز کردنِ خلاصه
 *  «۱٬۰۰۰ · ۱ ردیف» و فقط «کرایه». این آزمون همان را با مسیرِ واقعیِ `expenses` (پوسته، `ShellContext`) می‌سنجد: باز و بسته کردنِ
 *  خلاصه نه ماه را عوض می‌کند، نه دسته را، نه ردیف‌ها را — جمع، شمار و فهرست همان می‌مانند.
 */
@OptIn(ExperimentalTestApi::class)
class ExpensesSummaryFoldUiTest {

  @get:Rule val rule = createComposeRule()

  private lateinit var h: RouteUiHarness

  @Before fun up() {
    h = RouteUiHarness(rule)
    SummaryFold.save(summaryFoldStore(DesktopContext), "expenses", true)
  }

  @After fun down() {
    DesktopContext.getSharedPreferences(ir.vil3ntec.tohid.ui.components.common.ViewModePref.PREFS, 0).edit().remove(SummaryFold.key("expenses")).commit()
    h.close()
  }

  private val header = SemanticsMatcher.keyIsDefined(SemanticsProperties.StateDescription) and hasClickAction() and
    SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)

  private fun rows() = rule.onAllNodesWithTag(V3DataRowSpec.ROW_TAG, useUnmergedTree = true).fetchSemanticsNodes().size

  @Test fun expandingTheSummaryKeepsTheMonthTotalsAndRows() {
    val today = todayIso()
    //  «دیروز» در همان ماه (روزِ نخستِ ماه ⇒ همان امروز، تا مرزِ ماه آزمون را دوپهلو نکند)
    val yesterday = if (today.endsWith("-01")) today else ir.vil3ntec.tohid.yesterdayIso()
    val d = ShopData(
      expenses = listOf(
        Expense(id = "e1", category = "متفرقه", amount = 200.0, date = today, createdAt = 3),
        Expense(id = "e2", category = "برق", amount = 66.0, date = today, createdAt = 2),
        Expense(id = "e3", category = "کرایه", amount = 1000.0, date = yesterday, createdAt = 1),
      ),
    )
    h.show("expenses", d)
    h.awaitThat { rows() == 3 }
    val total = MoneyText.afn(1266.0)
    assertTrue("بسته: جمعِ ماه", h.has(total, substring = true))

    rule.onNode(header).performClick()
    rule.waitForIdle()
    h.awaitThat { h.has(StrMoney.rows(3)) || h.has(StrMoney.rows(1)) }
    assertTrue("باز: همان جمع (نه فقط دیروز)", h.has(total))
    assertTrue("باز: همان شمارِ ماه", h.has(StrMoney.rows(3)))
    assertEquals("باز: همان سه ردیف", 3, rows())

    //  دوباره بسته و باز ⇒ همان
    rule.onNode(header).performClick()
    rule.waitForIdle()
    rule.onNode(header).performClick()
    rule.waitForIdle()
    assertEquals(3, rows())
    assertTrue(h.has(total, substring = true))
  }
}
