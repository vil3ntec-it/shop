package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOrdersHub
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** «سفارش‌های آنلاین»: برگه‌ها به نامِ صاحب‌کار، کارتِ صف و کارتِ «آماده‌سازی و تحویل» */
class OrdersHubModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val manager = OrderPermsDto(create = true, manage = true, contact = true)
  private fun order(status: String, fulfillment: String = "delivery", phone: String = "+93 79•••4567") =
    OrderDto(id = "o", orderNo = 7, status = status, fulfillment = fulfillment, customer = OrderCustomerDto("کریم", phone), itemsCount = 5,
      itemsPreview = listOf("برنج", "روغن", "شکر"))

  @Test
  fun `برگه‌ها به نامِ صاحب‌کار؛ «زمان‌بندی‌شده» فقط با سفارش یا وقتی برگزیده است`() {
    assertEquals(listOf("new", "preparing", "ready", "on_the_way", "done"), OrdersHubModel.tabs(emptyMap(), "new"))
    assertEquals(listOf("جدید", "در حالِ آماده‌سازی", "آمادهٔ تحویل", "در راه", "تمام‌شده"), OrdersHubModel.tabs(emptyMap(), "new").map(StrOrdersHub::tab))
    assertTrue("scheduled" in OrdersHubModel.tabs(mapOf("scheduled" to 1), "new"))
    assertTrue("پیوندِ کهنه به همان برگه بی‌راه نمی‌ماند", "scheduled" in OrdersHubModel.tabs(emptyMap(), "scheduled"))
  }

  @Test
  fun `کارتِ صف — اقلام با نام، روشِ تحویل به زبانِ ساده`() {
    assertEquals("برنج، روغن، شکر و 2 قلمِ دیگر", OrdersHubModel.items(order("new")))
    assertEquals("برنج", OrdersHubModel.items(order("new").copy(itemsCount = 1, itemsPreview = listOf("برنج"))))
    assertEquals("سرورِ کهنه بی نام ⇒ همان شمار", "5 قلم", OrdersHubModel.items(order("new").copy(itemsPreview = emptyList())))
    assertEquals("با پیک", OrdersHubModel.method(order("new")))
    assertEquals("خودش می‌آید", OrdersHubModel.method(order("new", "pickup")))
  }

  @Test
  fun `«بسته شد» از قبول‌شده دو گام، از جمع‌آوری یک گام؛ بی دسترسی هیچ`() {
    assertEquals(listOf("picking", "packed"), OrdersHubModel.prep(order("accepted"), manager, true).pack)
    assertEquals(listOf("packed"), OrdersHubModel.prep(order("picking"), manager, true).pack)
    assertNull(OrdersHubModel.prep(order("new"), manager, true).pack)
    assertNull(OrdersHubModel.prep(order("accepted"), OrderPermsDto(), true).pack)
    assertTrue(OrdersHubModel.prep(order("ready_for_pickup", "pickup"), manager, true).packed)
  }

  @Test
  fun `پس از بسته‌بندی — پیک برای «ارسال»، «خودش می‌آید» برای حضوری`() {
    assertEquals("out_for_delivery", OrdersHubModel.prep(order("packed"), manager, true).dispatch)
    assertEquals("ready_for_pickup", OrdersHubModel.prep(order("packed", "pickup"), manager, true).dispatch)
    assertNull(OrdersHubModel.prep(order("picking"), manager, true).dispatch)
    assertNull(OrdersHubModel.prep(order("packed"), OrderPermsDto(), true).dispatch)
  }

  @Test
  fun `عکس برای مشتری فقط با چتِ روشن، شمارهٔ مشتری و سفارشِ باز`() {
    assertTrue(OrdersHubModel.prep(order("picking"), manager, chatOn = true).photo)
    assertFalse(OrdersHubModel.prep(order("picking"), manager, chatOn = false).photo)
    assertFalse(OrdersHubModel.prep(order("picking", phone = ""), manager, chatOn = true).photo)
    assertFalse(OrdersHubModel.prep(order("delivered"), manager, chatOn = true).photo)
    assertTrue("تمام‌شده بی هیچ کار ⇒ کارت نمی‌آید", OrdersHubModel.prep(order("cancelled"), manager, true).isEmpty)
  }
}
