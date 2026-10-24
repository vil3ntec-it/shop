package ir.vil3ntec.tohid.ui.v3.online.delivery

import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.DeskConfig
import ir.vil3ntec.tohid.core.delivery.DeskPerms
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.core.delivery.SettleRow
import ir.vil3ntec.tohid.core.i18n.I18n
import ir.vil3ntec.tohid.core.i18n.Lang
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.model.OrderCustomerDto
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderStates
import ir.vil3ntec.tohid.ui.theme.V3Status
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.online.status.OnlineStatus
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** برگه‌ها و عددهای میز، فرم‌های سپردن و تحویل و تسویه، و یک رنگ‌بندیِ وضعیت با برچسب */
class DeliveryModelTest {
  @After fun reset() { I18n.source = { Lang.FA } }

  private val full = DeskConfig(readable = true, entitled = true, enabled = true, perms = DeskPerms(view = true, assign = true, handover = true, couriers = true, settle = true))
  private val ali = CourierRow("c1", name = "علی", cashCapMinor = 100_000, cashHeldMinor = 30_000, liveExpectedMinor = 20_000)
  private val full2 = CourierRow("c2", name = "رحیم", cashCapMinor = 50_000, cashHeldMinor = 50_000)
  private val off = CourierRow("c3", name = "کریم", active = false)
  private fun order(id: String, status: String, fulfillment: String = "delivery", at: Long = 1) =
    OrderDto(id = id, orderNo = id.hashCode().toLong() and 0xFFF, status = status, fulfillment = fulfillment, createdAt = at, paymentMethod = "cod", totalMinor = 12_000)

  private val desk = DeskData(
    couriers = listOf(ali, full2, off),
    live = listOf(Mission("m1", orderId = "o2", orderNo = 2, mode = "shop_courier", courierId = "c1", status = "moving", live = true)),
    orders = listOf(order("o1", "accepted"), order("o2", "packed"), order("o3", "ready_for_pickup", "pickup"), order("o4", "picking", at = 0)),
  )

  @Test
  fun `برگه‌ها و برگهٔ آغازین`() {
    assertEquals(4, DeliveryModel.tabs(full).size)
    assertEquals(3, DeliveryModel.tabs(full.copy(perms = DeskPerms(view = true))).size)
    assertEquals(DeliveryModel.ASSIGN, DeliveryModel.initialTab(DeliveryModel.EXTERNAL, full.copy(perms = DeskPerms(view = true))))
    assertEquals(DeliveryModel.COURIERS, DeliveryModel.initialTab(DeliveryModel.COURIERS, full))
    assertEquals(DeliveryModel.EXTERNAL, DeliveryModel.initialTab(DeliveryModel.EXTERNAL, null))
    assertEquals(DeliveryModel.ASSIGN, DeliveryModel.initialTab(null, full))
  }

  @Test
  fun `عددهای میز`() {
    //  o2 مأموریتِ زنده دارد؛ o3 حضوری است؛ کهنه‌تر اول
    assertEquals(listOf("o4", "o1"), desk.toAssign.map { it.id })
    assertEquals(listOf("o3"), desk.pickups.map { it.id })
    val s = DeliveryModel.stats(desk)
    assertEquals(2, s.ready)
    assertEquals(1, s.live)
    assertEquals(80_000L, s.heldMinor)
    assertEquals(1, s.atCap)
    assertEquals(2, DeliveryModel.count(DeliveryModel.ASSIGN, desk))
    assertEquals(3, DeliveryModel.count(DeliveryModel.COURIERS, desk))
  }

  @Test
  fun `برچسبِ پیک با متن نه فقط رنگ`() {
    assertEquals(StrOnline.dlReadyState to V3Tone.SUCCESS, DeliveryModel.courierState(ali))
    assertEquals(StrOnline.dlAtCap to V3Tone.WARNING, DeliveryModel.courierState(full2))
    assertEquals(StrOnline.dlOverCap to V3Tone.ERROR, DeliveryModel.courierState(full2.copy(liveExpectedMinor = 1)))
    assertEquals(StrOnline.dlInactive to null, DeliveryModel.courierState(off))
    assertEquals("علی", DeliveryModel.who(desk.live[0], desk))
    assertTrue(DeliveryModel.how(desk.live[0].copy(externalRef = "R-9"), desk).endsWith("R-9"))
  }

  @Test
  fun `جست‌وجوی حضوری و کدِ دریافت`() {
    val o = OrderDto(id = "x", orderNo = 1042, customer = OrderCustomerDto(name = "Sara"))
    assertTrue(DeliveryModel.matches(o, "#۱۰۴"))
    assertTrue(DeliveryModel.matches(o, "sar"))
    assertTrue(DeliveryModel.matches(o, " "))
    assertFalse(DeliveryModel.matches(o, "99"))
    assertEquals(StrOnline.dlNoCodeYet, DeliveryModel.pickupState(null))
    assertEquals(StrOnline.dlCodeLocked, DeliveryModel.pickupState(Mission("m", hasCode = true, codeLocked = true)))
    assertEquals(StrOnline.dlCodeGiven, DeliveryModel.pickupState(Mission("m", hasCode = true)))
  }

  @Test
  fun `خالصِ حساب با شرکت در یک جهت`() {
    assertEquals(StrOnline.dlNetShopPays("x") to StrOnline.afn(500), DeliveryModel.net("x", 500))
    assertEquals(StrOnline.dlNetCompanyPays("x") to StrOnline.afn(500), DeliveryModel.net("x", -500))
    assertEquals("x" to StrOnline.dlSquare, DeliveryModel.net("x", 0))
    assertTrue(DeliveryModel.secondary(DeskActions.Act.FAIL))
    assertFalse(DeliveryModel.secondary(DeskActions.Act.DELIVER))
    assertEquals(listOf(StrOnline.dlRecallSent, StrOnline.dlAttempt(2)), DeliveryModel.notes(Mission("m", recallRequested = true, recallOutcome = "pending", attemptNo = 2)))
  }

  @Test
  fun `سپردنِ دستی`() {
    assertEquals(listOf("shop_courier", "external"), DeliveryForms.modes(full))
    assertEquals("shop_courier", DeliveryForms.startMode(full, listOf(ali)))
    assertEquals("external", DeliveryForms.startMode(full, listOf(off)))
    val a = DeliveryForms.Assign("shop_courier", courierId = "c2")
    assertTrue(DeliveryForms.overCap(a, desk.couriers, 1_000))
    assertFalse(DeliveryForms.assignValid(a, desk.couriers, 1_000))
    assertTrue(DeliveryForms.assignValid(a.copy(override = true), desk.couriers, 1_000))
    assertFalse(DeliveryForms.assignValid(a.copy(courierId = "c3", override = true), desk.couriers, 0))
    assertFalse(DeliveryForms.assignValid(DeliveryForms.Assign("external"), desk.couriers, 0))
    assertTrue(DeliveryForms.assignValid(DeliveryForms.Assign("external", company = "پیک‌رسان", fare = "۱۵۰"), desk.couriers, 0))
    assertFalse(DeliveryForms.assignValid(DeliveryForms.Assign("external", company = "پیک‌رسان", fare = "x"), desk.couriers, 0))
  }

  @Test
  fun `تحویل به دستِ کارمند`() {
    val m = Mission("m", mode = "shop_courier", status = "moving", live = true, expectedCollectMinor = 12_000, hasCode = true)
    val start = DeliveryForms.startAct(m)
    assertEquals("120", start.cash)
    //  تحویلِ پیکِ دکان توضیح و کد می‌خواهد (مدیرِ سپردن بی کد هم می‌تواند)
    assertTrue(DeliveryForms.noteNeeded(m, DeskActions.Act.DELIVER, start))
    assertFalse(DeliveryForms.actValid(m, DeskActions.Act.DELIVER, full.copy(perms = DeskPerms()), start.copy(note = "x")))
    assertTrue(DeliveryForms.actValid(m, DeskActions.Act.DELIVER, full, start.copy(note = "x")))
    //  نقدِ دیگر ⇐ دلیلِ نقد
    val less = start.copy(note = "x", cash = "100", code = "123456")
    assertTrue(DeliveryForms.cashDiffers(m, DeskActions.Act.DELIVER, less))
    assertFalse(DeliveryForms.actValid(m, DeskActions.Act.DELIVER, full, less))
    assertTrue(DeliveryForms.actValid(m, DeskActions.Act.DELIVER, full, less.copy(cashReason = "partial_payment")))
    //  پس‌گرفتن دلیل می‌خواهد
    assertEquals("withdrawn", DeliveryForms.reasonKey(DeskActions.Act.WITHDRAW))
    assertFalse(DeliveryForms.actValid(m, DeskActions.Act.WITHDRAW, full, DeliveryForms.Act()))
    assertTrue(DeliveryForms.actValid(m, DeskActions.Act.WITHDRAW, full, DeliveryForms.Act(reason = "reassign")))
  }

  @Test
  fun `تحویلِ حضوری با کد یا تأییدِ دستی`() {
    val m = Mission("m", mode = "pickup", expectedCollectMinor = 0)
    val h = DeliveryForms.startHandover(m)
    assertEquals("0", h.cash)
    assertFalse(DeliveryForms.handoverValid(m, h.copy(code = "123")))
    assertTrue(DeliveryForms.handoverValid(m, h.copy(code = "123456")))
    assertFalse(DeliveryForms.handoverValid(m, h.copy(manual = true)))
    assertTrue(DeliveryForms.handoverValid(m, h.copy(manual = true, note = "کارت ملی دیدم")))
    assertTrue(DeliveryForms.handoverDiffers(m, h.copy(cash = "10")))
    assertFalse(DeliveryForms.handoverValid(m, h.copy(code = "123456", cash = "10")))
    assertFalse(DeliveryForms.handoverValid(m, h.copy(code = "123456", cash = "10", cashReason = "other")))
    assertTrue(DeliveryForms.handoverValid(m, h.copy(code = "123456", cash = "10", cashReason = "other", note = "x")))
  }

  @Test
  fun `تسویهٔ پایانِ روز`() {
    val rows = listOf(
      SettleRow("a1", expectedCollectMinor = 10_000, cashCollectedMinor = 10_000, fareMinor = 1_000),
      SettleRow("a2", expectedCollectMinor = 5_000, cashCollectedMinor = 5_000, fareMinor = 1_000),
    )
    val s = DeliveryForms.Settle()
    assertFalse(DeliveryForms.settleValid(rows, s))
    assertTrue(DeliveryForms.settleValid(rows, s.copy(received = "150")))
    assertFalse(DeliveryForms.settleNeedsReason(rows, s.copy(received = "150")))
    //  کرایه از همین نقد ⇐ باید ۱۳۰ بدهد؛ ۱۲۰ ⇐ کسری و دلیل
    val paid = s.copy(farePaid = true, received = "120")
    assertEquals(13_000L, DeliveryForms.totals(rows, paid).handoverDueMinor)
    assertTrue(DeliveryForms.settleNeedsReason(rows, paid))
    assertFalse(DeliveryForms.settleValid(rows, paid))
    assertTrue(DeliveryForms.settleValid(rows, paid.copy(reason = "شمارش")))
    assertFalse(DeliveryForms.settleValid(rows, s.copy(received = "150", handed = "x")))
    assertNull(DeliveryForms.capMinor(""))
    assertEquals(250_000L, DeliveryForms.capMinor("۲۵۰۰"))
  }

  @Test
  fun `هر وضعیت یک رنگ و یک برچسبِ ترجمه‌شده`() {
    assertEquals(V3Status.AWAITING, OnlineStatus.order("new"))
    assertEquals(V3Status.PREPARING, OnlineStatus.order("packed"))
    assertEquals(V3Status.IN_DELIVERY, OnlineStatus.order("out_for_delivery"))
    assertEquals(V3Status.DELIVERED, OnlineStatus.order("picked_up"))
    assertEquals(V3Status.CANCELLED, OnlineStatus.order("rejected"))
    assertEquals(V3Status.REVIEW, OnlineStatus.order("delivery_failed"))
    assertEquals(V3Status.INACTIVE, OnlineStatus.order("refunded_full"))
    assertEquals(V3Status.IN_DELIVERY, OnlineStatus.mission("picked_up"))
    assertEquals(V3Status.REVIEW, OnlineStatus.mission("returned"))
    assertEquals(V3Status.CANCELLED, OnlineStatus.mission("rejected"))
    //  «زمان‌دار» هشدار است، نه «در انتظارِ تأیید» (THEME-V2)؛ هشت معنا در کل
    assertEquals(V3Status.WARNING, OnlineStatus.order("scheduled"))
    assertEquals(V3Status.AWAITING, OnlineStatus.mission("requested"))
    assertEquals(V3Status.entries.toSet(), (OrderStates.STATUSES + OrderStates.TABS).map(OnlineStatus::order).toSet() + MISSIONS.map(OnlineStatus::mission))
    I18n.source = { Lang.EN }
    val persian = { s: String -> s.any { it in '؀'..'ۿ' } }
    OrderStates.STATUSES.forEach { assertFalse(it, persian(StrOnline.orderStatus(it))) }
    OrderStates.TABS.forEach { assertFalse(it, persian(StrOnline.orderTab(it))) }
    OrderStates.REASONS.values.flatten().forEach { assertFalse(it, persian(StrOnline.orderReason(it))) }
    MISSIONS.forEach { assertFalse(it, persian(StrOnline.missionStatus(it))) }
    DeskActions.Act.entries.forEach { assertFalse(it.name, persian(StrOnline.deskAction(it))) }
    listOf("shop_courier", "external", "pickup").forEach { assertFalse(it, persian(StrOnline.deliveryMode(it))) }
  }

  private companion object {
    val MISSIONS = listOf("requested", "assigned", "picked_up", "moving", "arrived", "delivered", "failed", "rejected", "withdrawn", "returned")
  }
}
