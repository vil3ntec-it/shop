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
}
