package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import ir.vil3ntec.tohid.core.i18n.StrWhy
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.ui.components.pos.PriceWhyPrefs
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  POS-22 در کادرِ ردیفِ گوشیِ نسخهٔ ۳ ([LineSheetHarness]): «چرا این قیمت؟» (همان `priceWhyOn` ⇒ `PriceWhyLine.rows` ⇒
 *  `PriceWhyPanel` ِ `CartLineDialog`، با تخفیف‌های خودکارِ همین سبد)؛ تنظیمِ خاموش ⇒ بی پنل.
 */
class LinePriceWhyUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var s: LineSheetHarness
  private var before = true

  @Before fun up() {
    PriceWhyPrefs.load(DesktopContext)
    before = PriceWhyPrefs.on.value
    s = LineSheetHarness(rule)
  }

  @After fun down() { s.close(); PriceWhyPrefs.set(DesktopContext, before) }

  @Test fun whyThisPriceShowsTheStepsWhenOn() {
    PriceWhyPrefs.set(DesktopContext, true)
    s.open(SalesEngine.CartLine(s.rice.id, 2.0))
    s.h.await(StrWhy.title)
    assertTrue("گامِ «قیمتِ فهرست»", s.h.has(StrWhy.list))
    assertTrue("جمعِ ردیف", s.h.has(StrWhy.total))
  }

  @Test fun whyThisPriceIsHiddenWhenOff() {
    PriceWhyPrefs.set(DesktopContext, false)
    s.open(SalesEngine.CartLine(s.rice.id, 2.0))
    assertFalse(s.h.has(StrWhy.title))
  }
}
