package ir.vil3ntec.tohid.core.sale

import kotlin.test.Test
import kotlin.test.assertEquals

/** POS-18 — لیستِ پیش‌فرضِ مشتری، انتخابِ دستی برنده، Ctrl+R */
class PriceListTest {

  @Test fun customerDefaultAppliesUnlessCashierChoseByHand() {
    assertEquals(PriceList.WHOLESALE, PriceList.afterCustomer(PriceList.RETAIL, chosenByHand = false, customerList = "wholesale"))
    assertEquals(PriceList.RETAIL, PriceList.afterCustomer(PriceList.RETAIL, chosenByHand = true, customerList = "wholesale"))
    //  مشتریِ خرده پس از مشتریِ عمده ⇒ خرده
    assertEquals(PriceList.RETAIL, PriceList.afterCustomer(PriceList.WHOLESALE, chosenByHand = false, customerList = ""))
    //  برداشتنِ مشتری ⇒ همان لیستِ امروز
    assertEquals(PriceList.WHOLESALE, PriceList.afterCustomer(PriceList.WHOLESALE, chosenByHand = false, customerList = null))
  }

  @Test fun ctrlRToggles() {
    assertEquals(PriceList.WHOLESALE, PriceList.next(PriceList.RETAIL))
    assertEquals(PriceList.RETAIL, PriceList.next(PriceList.WHOLESALE))
    assertEquals(PriceList.WHOLESALE, PriceList.next("unknown"))
  }

  @Test fun repriceReturnsNormalisedList() {
    assertEquals(PriceList.RETAIL, PriceList.reprice(emptyList(), "vip").list)
  }

  private val p = PriceList.Prices(10_000, 9_000)

  @Test fun cartLineFollowsListUnlessManual() {
    assertEquals(9_000L, PriceList.cartUnit(p, null, PriceList.WHOLESALE))
    assertEquals(null, PriceList.cartUnit(p, 9_000, PriceList.RETAIL))
    assertEquals(null, PriceList.cartUnit(p, 10_000, PriceList.RETAIL))
    //  فیِ دستی (نه خرده، نه عمده) در هر دو لیست همان
    assertEquals(8_500L, PriceList.cartUnit(p, 8_500, PriceList.WHOLESALE))
    assertEquals(8_500L, PriceList.cartUnit(p, 8_500, PriceList.RETAIL))
    //  کالای بی عمده: عمده همان خرده ⇒ null
    assertEquals(null, PriceList.cartUnit(PriceList.Prices(5_000, 0), null, PriceList.WHOLESALE))
    assertEquals(true, PriceList.isManual(PriceList.Prices(5_000, 0), 0))
  }

  @Test fun stateMachine() {
    val s1 = PriceList.onCustomer(PriceList.FRESH, "wholesale")
    assertEquals(PriceList.State(PriceList.WHOLESALE, false), s1)
    val s2 = PriceList.toggle(s1)
    assertEquals(PriceList.State(PriceList.RETAIL, true), s2)
    //  پس از انتخابِ دستی، مشتریِ عمدهٔ دیگر لیست را عوض نمی‌کند
    assertEquals(s2, PriceList.onCustomer(s2, "wholesale"))
  }
}
