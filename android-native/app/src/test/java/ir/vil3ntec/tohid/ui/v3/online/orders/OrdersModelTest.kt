package ir.vil3ntec.tohid.ui.v3.online.orders

import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.model.OrderAddressDto
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.model.OrderEventDetailDto
import ir.vil3ntec.tohid.core.model.OrderEventDto
import ir.vil3ntec.tohid.core.model.OrderPermsDto
import ir.vil3ntec.tohid.core.orders.OrderDraft
import ir.vil3ntec.tohid.ui.theme.V3Status
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** نوارِ کارها، برگهٔ آغازین، تازه‌رسیده‌ها، خطِ زمانی، برگهٔ کار و فرمِ سفارشِ دستی */
class OrdersModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val manager = OrderPermsDto(create = true, manage = true, contact = true, refund = false)
  private fun order(status: String, fulfillment: String = "delivery") = OrderDto(id = "o", orderNo = 7, status = status, fulfillment = fulfillment)

  @Test
  fun `سفارشِ جدید قبول را اصلی و رد را خطرناک دارد`() {
    val bar = OrdersModel.bar(order("new"), manager)
    assertEquals("accepted", bar.primary)
    assertTrue("rejected" in bar.danger)
    //  ردِ خودکار فقط کارِ سیستم است
    assertFalse("auto_rejected" in bar.danger + bar.others)
    assertEquals("accepted", OrdersModel.nextStep(order("new"), manager))
  }

  @Test
  fun `بی دسترسیِ مدیریت هیچ دکمه‌ای نیست`() {
    assertTrue(OrdersModel.bar(order("new"), OrderPermsDto()).isEmpty)
    assertNull(OrdersModel.nextStep(order("picking"), OrderPermsDto()))
  }

  @Test
  fun `بسته‌شده به روشِ تحویل گام می‌گیرد`() {
    assertEquals("ready_for_pickup", OrdersModel.bar(order("packed", "pickup"), manager).primary)
    assertEquals("out_for_delivery", OrdersModel.bar(order("packed", "delivery"), manager).primary)
    assertFalse("out_for_delivery" in OrdersModel.bar(order("packed", "pickup"), manager).others)
  }

  @Test
  fun `بازپرداخت فقط با دسترسیِ بازپرداخت`() {
    assertTrue(OrdersModel.bar(order("return_requested"), manager).isEmpty)
    val refund = OrdersModel.bar(order("return_requested"), manager.copy(refund = true))
    assertEquals("refunded_partial", refund.primary)
    assertEquals(listOf("refunded_full"), refund.others)
  }

  @Test
  fun `گذارهایی که می‌پرسند`() {
    assertTrue(OrdersModel.needsSheet("rejected"))
    assertTrue(OrdersModel.needsSheet("accepted"))
    assertTrue(OrdersModel.needsSheet("refunded_full"))
    assertFalse(OrdersModel.needsSheet("picking"))
    assertFalse(OrdersModel.needsSheet("delivered"))
    assertTrue(OrdersModel.danger("cancelled"))
    assertFalse(OrdersModel.danger("packed"))
  }

  @Test
  fun `برگهٔ آغازین از پیوند`() {
    assertEquals("ready", OrdersModel.initialTab("ready"))
    assertEquals("new", OrdersModel.initialTab("bogus"))
    assertEquals("new", OrdersModel.initialTab(null))
  }

  @Test
  fun `تازه‌رسیده فقط پس از بارِ نخست`() {
    val now = listOf(OrderDto(id = "a"), OrderDto(id = "b"))
    assertEquals(emptySet<String>(), OrdersModel.arrivals(null, now))
    assertEquals(setOf("b"), OrdersModel.arrivals(setOf("a"), now))
    assertEquals(emptySet<String>(), OrdersModel.arrivals(setOf("a", "b"), now))
  }

  @Test
  fun `ساعت و زمانِ زمان‌دار`() {
    val cal = java.util.Calendar.getInstance().apply { set(2026, 9, 24, 9, 5, 0) }
    val at = cal.timeInMillis
    assertEquals("09:05", OrdersModel.clock(at))
    assertEquals("09:05", OrdersModel.scheduleLabel(at, at + 3_600_000L))
    //  روزِ دیگر ⇐ تاریخِ خورشیدی هم (۲ آبانِ ۱۴۰۵)
    assertEquals("8/2 09:05", OrdersModel.scheduleLabel(at, at + 2 * 86_400_000L))
  }

  @Test
  fun `خطِ زمانی و پیوندِ نقشه و تماس`() {
    val o = OrderDto(
      id = "o", status = "accepted",
      events = listOf(
        OrderEventDto(seq = 1, kind = "created", actorType = "customer", at = 1),
        OrderEventDto(seq = 2, kind = "status", fromStatus = "new", toStatus = "accepted", actorType = "user", detail = OrderEventDetailDto(readyAt = 5), at = 2),
        OrderEventDto(seq = 3, kind = "status", fromStatus = "accepted", toStatus = "cancelled", reason = "out_of_stock", note = "تمام شد", at = 3),
      ),
    )
    val steps = OrderDetailModel.timeline(o)
    assertEquals(3, steps.size)
    assertNull(steps[0].status)
    assertEquals("مشتری", steps[0].who)
    assertEquals(V3Status.PREPARING, steps[1].status)
    assertTrue(steps[1].readyBy != null)
    assertEquals(V3Status.CANCELLED, steps[2].status)
    assertEquals("تمام شد", steps[2].note)
    assertTrue(steps[2].reason!!.isNotEmpty())

    assertNull(OrderDetailModel.mapUri(o))
    assertEquals("geo:34.5,69.1?q=34.5,69.1", OrderDetailModel.mapUri(o.copy(address = OrderAddressDto(lat = 34.5, lng = 69.1))))
    val withPhone = o.copy(customer = OrderCustomerDto(name = "x", phoneMasked = "07•• ••• 123", phone = "0700000123"))
    assertNull(OrderDetailModel.fullPhone(withPhone))
    assertEquals("0700000123", OrderDetailModel.fullPhone(withPhone.copy(canContact = true)))
  }

  @Test
  fun `برگهٔ کار دلیل و زمانِ آماده می‌خواهد`() {
    val o = order("new")
    assertTrue(OrderActionModel.asksReason("rejected"))
    assertFalse(OrderActionModel.asksReason("accepted"))
    assertTrue(OrderActionModel.asksReady("accepted"))
    assertEquals(OrderActionModel.DEFAULT_READY, OrderActionModel.readyOf("accepted", OrderActionModel.Form()))
    assertNull(OrderActionModel.readyOf("picking", OrderActionModel.Form()))

    val start = OrderActionModel.start(o, "rejected")
    assertEquals("", start.reason)
    assertFalse(OrderActionModel.valid("rejected", start))
    assertTrue(OrderActionModel.valid("rejected", start.copy(reason = "busy")))
    //  «دیگر» توضیح می‌خواهد
    assertTrue(OrderActionModel.noteMissing("rejected", start.copy(reason = "other")))
    assertTrue(OrderActionModel.valid("rejected", start.copy(reason = "other", note = "x")))
    //  «آماده ⇐ لغو» فقط «نیامد» ⇐ همان از پیش برگزیده
    assertEquals("no_show", OrderActionModel.start(order("ready_for_pickup", "pickup"), "cancelled").reason)
    assertTrue(OrderActionModel.refund("refunded_partial"))
    assertFalse(OrderActionModel.refund("delivered"))
  }

  @Test
  fun `فرمِ سفارشِ دستی همان سنجه‌های ساختن`() {
    val empty = OrderDraft.Form(lines = listOf(OrderDraft.LineInput()))
    val e = NewOrderModel.errors(empty)
    assertTrue(NewOrderModel.Field.NAME in e.fields)
    assertTrue(NewOrderModel.Field.LINES in e.fields)
    assertFalse(NewOrderModel.Field.PHONE in e.fields)

    val delivery = OrderDraft.Form(fulfillment = "delivery", name = "احمد", deliveryFee = "abc", lines = listOf(OrderDraft.LineInput("برنج", "2", "")))
    val d = NewOrderModel.errors(delivery)
    assertTrue(NewOrderModel.Field.PHONE in d.fields)
    assertTrue(NewOrderModel.Field.ADDRESS in d.fields)
    assertTrue(NewOrderModel.Field.FEE in d.fields)
    assertEquals(setOf(NewOrderModel.LineField.PRICE), d.lines[0]!!.keys)

    val ok = delivery.copy(phone = "0700", landmark = "پهلوی مسجد", deliveryFee = "۵۰", lines = listOf(OrderDraft.LineInput("برنج", "۲", "۱۲۰")))
    assertTrue(NewOrderModel.errors(ok).isEmpty)
    //  همان داوریِ `OrderDraft.build`
    assertTrue(OrderDraft.build(ok) is OrderDraft.Result.Ok)
    assertEquals(1, NewOrderModel.filled(ok))
    assertTrue(NewOrderModel.dirty(ok))
    assertFalse(NewOrderModel.dirty(OrderDraft.Form()))
    assertEquals(NewOrderModel.DEFAULT_SOURCES, NewOrderModel.sources(emptyList()))
    assertEquals(listOf("phone"), NewOrderModel.sources(listOf("phone")))
  }

  @Test
  fun `مقدارِ صفر و نامِ خالیِ قلم`() {
    val e = NewOrderModel.lineErrors(OrderDraft.LineInput("", "0", "10"))
    assertEquals(setOf(NewOrderModel.LineField.NAME, NewOrderModel.LineField.QTY), e.keys)
    assertTrue(NewOrderModel.lineErrors(OrderDraft.LineInput()).isEmpty())
    I18n.source = { Lang.EN }
    assertTrue(NewOrderModel.errors(OrderDraft.Form(lines = emptyList())).fields.values.none { it.any { c -> c in '\u0600'..'\u06FF' } })
  }
}
