package ir.vil3ntec.tohid.desktop

import androidx.compose.ui.test.junit4.createComposeRule
import ir.vil3ntec.tohid.core.i18n.StrProduct
import ir.vil3ntec.tohid.data.SalesEngine
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/**
 *  POS-28 در کادرِ ردیفِ گوشیِ نسخهٔ ۳ ([LineSheetHarness]): کالای سریال‌دار ⇒ کادرِ «سریال‌ها» (همان `posLineSerials` ⇒
 *  `CartSerials.set` ِ `CartLineDialog`)؛ بی آن کالای سریال‌دار در گوشی فروخته نمی‌شد (`SerialSale.problem` ِ ثبت ردش می‌کند).
 */
class LineSerialsUiTest {

  @get:Rule val rule = createComposeRule()
  private lateinit var s: LineSheetHarness

  @Before fun up() { s = LineSheetHarness(rule) }
  @After fun down() = s.close()

  @Test fun aSerialTrackedItemTakesItsSerialsOnThePhoneLineSheet() {
    s.open(SalesEngine.CartLine(s.phone.id, 2.0))
    s.h.await(StrProduct.serials, substring = true)
    s.type(StrProduct.serials, "SN-1001\nSN-1002")
    s.save()
    s.h.awaitThat { s.line()?.serials == listOf("SN-1001", "SN-1002") }
    assertEquals(2.0, s.line()!!.quantity, 0.0)
  }

  @Test fun anOrdinaryItemHasNoSerialField() {
    s.open(SalesEngine.CartLine(s.rice.id, 1.0))
    assertFalse(s.h.has(StrProduct.serials, substring = true))
  }
}
