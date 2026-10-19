package ir.vil3ntec.tohid.core.courier

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 *  صفِ آفلاینِ پیک (DLV-10) — همان قراردادِ `POST /api/courier/events`.
 *  مرجعِ رفتار: `server/test/delivery.test.js` و `server/test/courier-scope.test.js`.
 */
class CourierQueueTest {

  private var ids = 0
  private fun id() = "cev-test-${++ids}-xyz"

  private fun add(
    s: QueueState, a: String, type: String,
    from: String = "", reason: String = "", note: String = "",
    cash: Long? = null, cashReason: String = "", code: String = "", time: Long = 1_000L,
  ) = CourierQueue.enqueue(s, id(), a, type, time, from, reason, note, cash, cashReason, code)

  private fun expectError(code: String, block: () -> Unit) {
    try { block(); fail("باید رد می‌شد: $code") } catch (e: CourierInputError) { assertEquals(code, e.code) }
  }

  @Test fun `ترتیبِ دستگاه یکتا و فقط رو به بالاست، حتی پس از بیرون رفتنِ رویداد`() {
    var s = QueueState()
    val (s1, e1) = add(s, "A", "assigned", from = "requested"); s = s1
    val (s2, e2) = add(s, "A", "picked_up", from = "assigned"); s = s2
    assertEquals(1L, e1.deviceSeq)
    assertEquals(2L, e2.deviceSeq)
    s = CourierQueue.applyResults(s, listOf(e1, e2), listOf(EventResult(e1.eventId, "applied"), EventResult(e2.eventId, "applied")))
    assertTrue(s.pending.isEmpty())
    val (s3, e3) = add(s, "A", "moving", from = "picked_up")
    assertEquals("ترتیب دوباره خرج نمی‌شود", 3L, e3.deviceSeq)
    assertEquals(4L, s3.nextSeq)
  }

  @Test fun `بسته به ترتیبِ دستگاه و حداکثر صد`() {
    var s = QueueState()
    repeat(130) { i -> s = add(s, "A$i", "assigned").first }
    val b = CourierQueue.batch(s)
    assertEquals(100, b.size)
    assertEquals((1L..100L).toList(), b.map { it.deviceSeq })
    assertEquals(CourierQueue.MAX_BATCH, CourierQueue.batch(s, max = 5000).size)
  }

  @Test fun `پاسخ‌ها — انجام‌شده و تکراری بیرون؛ رد در «ردشده‌ها»؛ بی‌پاسخ می‌ماند`() {
    var s = QueueState()
    val evs = ArrayList<CourierEvent>()
    for (t in listOf("assigned", "picked_up", "moving", "arrived")) { val (n, e) = add(s, "A", t); s = n; evs += e }
    s = CourierQueue.applyResults(
      s, evs,
      listOf(
        EventResult(evs[0].eventId, "applied"),
        EventResult(evs[1].eventId, "duplicate"),
        EventResult(evs[2].eventId, "rejected", "order_not_ready", "سفارش هنوز آماده نیست"),
        //  پاسخِ چهارمی نیامد (قطعِ شبکه وسطِ پاسخ)
      ),
    )
    assertEquals(listOf(evs[3].eventId), s.pending.map { it.eventId })
    assertEquals(1, s.rejected.size)
    assertEquals("order_not_ready", s.rejected[0].code)
    assertEquals("moving", s.rejected[0].event.type)
    s = CourierQueue.dismissRejected(s, evs[2].eventId)
    assertTrue(s.rejected.isEmpty())
  }

  @Test fun `شکستِ کاملِ شبکه — هیچ رویدادی گم نمی‌شود و همان eventId دوباره می‌رود`() {
    var s = QueueState()
    val (n, e) = add(s, "A", "assigned"); s = n
    val after = CourierQueue.applyResults(s, listOf(e), emptyList())
    assertEquals(s, after)
    assertEquals(e.eventId, CourierQueue.batch(after).single().eventId)
  }

  @Test fun `پاسخِ بی‌شناسه با جایش در بسته جفت می‌شود`() {
    var s = QueueState()
    val (n1, e1) = add(s, "A", "assigned"); s = n1
    val (n2, e2) = add(s, "B", "assigned"); s = n2
    s = CourierQueue.applyResults(s, listOf(e1, e2), listOf(EventResult("", "applied"), EventResult("", "unchanged")))
    assertTrue(s.pending.isEmpty())
  }

  @Test fun `کدِ تحویل پس از رد در «ردشده‌ها» نگه داشته نمی‌شود`() {
    var s = QueueState()
    val (n, e) = add(s, "A", "delivered", cash = 1_240_000, code = "۱۲۳ ۴۵۶")
    s = n
    assertEquals("رقمِ فارسی ⇒ لاتین، فاصله بیرون", "123456", e.code)
    assertEquals("123456", CourierQueue.latinDigits("١٢٣abc456"))
    s = CourierQueue.applyResults(s, listOf(e), listOf(EventResult(e.eventId, "rejected", "bad_code", "کد درست نیست")))
    assertEquals("", s.rejected.single().event.code)
  }

  @Test fun `ورودیِ نادرست پیش از صف رد می‌شود`() {
    val s = QueueState()
    expectError("bad_event_id") { CourierQueue.enqueue(s, "short", "A", "assigned", 0) }
    expectError("bad_event_type") { add(s, "A", "returned") }
    expectError("bad_event_type") { add(s, "A", "withdrawn") }
    expectError("reason_required") { add(s, "A", "rejected") }
    expectError("bad_reason") { add(s, "A", "rejected", reason = "lazy") }
    expectError("note_required") { add(s, "A", "failed", reason = "other") }
    expectError("bad_cash") { add(s, "A", "delivered", cash = -1) }
    expectError("bad_reason") { add(s, "A", "delivered", cash = 1, cashReason = "tip") }
    expectError("invalid_transition") { add(s, "A", "delivered", from = "requested") }
    expectError("assignment_not_found") { add(s, " ", "assigned") }
    val (n, e) = add(s, "A", "assigned")
    expectError("event_key_reused") { CourierQueue.enqueue(n, e.eventId, "A", "picked_up", 0) }
  }

  @Test fun `فقط «تحویل» کد و نقد می‌برد`() {
    val (_, e) = add(QueueState(), "A", "arrived", cash = 500, code = "123456", cashReason = "other")
    assertNull(e.cashCollectedMinor)
    assertEquals("", e.code)
    assertEquals("", e.cashReason)
  }

  @Test fun `وضعیتِ پیش‌بینی‌شده با صف، فقط با گذارهای مجاز`() {
    var s = QueueState()
    s = add(s, "A", "assigned").first
    s = add(s, "A", "picked_up").first
    s = add(s, "A", "delivered").first   // میان‌برِ مجاز
    s = add(s, "B", "picked_up").first   // B هنوز «درخواست» است ⇒ گذارِ نامجاز نادیده
    assertEquals("delivered", CourierQueue.projectedStatus("requested", "A", s))
    assertEquals("requested", CourierQueue.projectedStatus("requested", "B", s))
    assertEquals("moving", CourierQueue.projectedStatus("moving", "C", s))
  }

  @Test fun `ماشین همان سرور است`() {
    assertTrue(CourierFlow.canSend("requested", "assigned"))
    assertTrue(CourierFlow.canSend("failed", "moving"))
    assertFalse("برگشت با دکان است", CourierFlow.canSend("failed", "returned"))
    assertFalse(CourierFlow.canSend("delivered", "failed"))
    assertFalse(CourierFlow.canSend("assigned", "arrived"))
    assertEquals(listOf("assigned", "rejected"), CourierFlow.actions("requested"))
    assertEquals(listOf("delivered", "failed"), CourierFlow.actions("arrived"))
    assertEquals(listOf("moving"), CourierFlow.actions("failed"))
    assertTrue(CourierFlow.actions("delivered").isEmpty())
    for (st in CourierFlow.STATUSES) for (a in CourierFlow.actions(st)) assertTrue("$st→$a", CourierFlow.canSend(st, a))
    assertTrue(CourierFlow.isTerminal("returned"))
    assertFalse(CourierFlow.isTerminal("failed"))
  }

  private fun card(status: String) = MissionCard(
    id = "A", orderNo = 7, status = status, landmark = "نزدیکِ مسجد", addressText = "کوچهٔ سوم",
    lat = 34.5, lng = 69.1, customerName = "احمد", phoneMasked = "0791•••567", leaveWith = "همسایه",
  )

  @Test fun `نشانی پس از پایان پنهان — حتی وقتی پایان هنوز در صف است`() {
    var s = QueueState()
    val live = MissionView.forDisplay(card("arrived"), s)
    assertEquals("نزدیکِ مسجد", live.landmark)
    assertEquals("0791•••567", live.phoneMasked)
    s = add(s, "A", "delivered", cash = 0).first
    val done = MissionView.forDisplay(card("arrived"), s)
    assertEquals("delivered", done.status)
    assertEquals("", done.landmark)
    assertEquals("", done.addressText)
    assertNull(done.lat)
    assertEquals("", done.customerName)
    assertEquals("", done.phoneMasked)
    //  و آن‌چه روی گوشی نگه داشته می‌شود
    val kept = MissionView.forStorage(listOf(card("returned"), card("moving")))
    assertEquals("", kept[0].landmark)
    assertEquals("نزدیکِ مسجد", kept[1].landmark)
  }

  @Test fun `شماره هرگز کامل نمی‌ماند — همان قالبِ سرور`() {
    assertEquals("0791•••567", MissionView.maskPhone("0791•••567"))
    assertEquals("0791•••567", MissionView.maskPhone("0791234567"))
    assertEquals("+937•••••567", MissionView.maskPhone("+93791234567"))
    assertEquals("", MissionView.maskPhone(""))
  }

  @Test fun `صف بی‌پایان رشد نمی‌کند`() {
    var s = QueueState()
    repeat(CourierQueue.MAX_PENDING) { s = s.copy(pending = s.pending + CourierEvent("cev-fill-$it", "A", "assigned", 0, it.toLong())) }
    expectError("queue_full") { add(s, "A", "assigned") }
  }
}
