package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.core.i18n.StrDebtor
import ir.vil3ntec.tohid.data.ONLINE_SERIES
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.isOnlineOrder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  CRM-07 — در «فاکتورهای این مشتری» (گوشی: `DebtorDetail`، کامپیوتر: زبانهٔ «فاکتورها» ِ `DeskCustomers`)
 *  فاکتورِ سفارشِ اینترنتی نشانِ «اینترنتی» دارد؛ فاکتورِ صندوق همان سطرِ پیشین است.
 */
class CustomerInvoiceOnlineTest {

  @Test
  fun `فاکتورِ صندوق همان سطرِ پیشین — بی نشان`() {
    assertEquals("فاکتور #12", StrDebtor.invoiceTitle("#12", online = false, cancelled = false))
    assertEquals("فاکتور #12 (لغوشده)", StrDebtor.invoiceTitle("#12", online = false, cancelled = true))
  }

  @Test
  fun `فاکتورِ سفارشِ اینترنتی نشانِ اینترنتی دارد، لغوشده هم`() {
    assertEquals("فاکتور W-7 · اینترنتی", StrDebtor.invoiceTitle("W-7", online = true, cancelled = false))
    assertEquals("فاکتور W-7 · اینترنتی (لغوشده)", StrDebtor.invoiceTitle("W-7", online = true, cancelled = true))
    assertEquals("اینترنتی", StrDebtor.online)
  }

  @Test
  fun `کدام فاکتور اینترنتی است — کانالِ online یا سریِ W، نه فاکتورِ صندوق`() {
    assertTrue(Sale(id = "a", channel = "online").isOnlineOrder())
    assertTrue(Sale(id = "b", series = ONLINE_SERIES).isOnlineOrder())
    assertFalse(Sale(id = "c").isOnlineOrder())
    assertFalse(Sale(id = "d", channel = "pos", series = "A").isOnlineOrder())
  }
}
