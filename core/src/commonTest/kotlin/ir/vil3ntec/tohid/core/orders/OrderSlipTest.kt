package ir.vil3ntec.tohid.core.orders

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/**
 *  ORD-10: برگهٔ سفارش پس از قبول — ردیف‌ها، مبلغِ دقیقِ سرور، حریمِ کاغذ، و «یک قبول، یک برگه».
 */
class OrderSlipTest {

  /** نوشته‌های نگهبان: هر ردیف نامِ کلیدش را دارد تا ترتیب و مقدار سنجیده شود */
  private object Tx : OrderSlip.Texts {
    override fun title(orderNo: String) = "title:$orderNo"
    override val test = "test"
    override val delivery = "delivery"
    override val pickup = "pickup"
    override fun scheduled(at: String) = "scheduled:$at"
    override fun readyBy(at: String) = "ready:$at"
    override fun customer(name: String) = "customer:$name"
    override fun landmark(text: String) = "landmark:$text"
    override val cod = "cod"
    override val paid = "paid"
    override val unpaid = "unpaid"
    override fun collect(amount: String) = "collect:$amount"
    override fun line(name: String, qty: String, total: String) = "line:$name|$qty|$total"
    override fun lineNote(note: String) = "note:$note"
    override fun subtotal(amount: String) = "subtotal:$amount"
    override fun discount(amount: String) = "discount:$amount"
    override fun deliveryFee(amount: String) = "fee:$amount"
    override fun total(amount: String) = "total:$amount"
    override fun orderNote(note: String) = "orderNote:$note"
    override fun printedAt(at: String) = "printed:$at"
  }

  private fun order(
    fulfillment: String = "delivery",
    paymentMethod: String = "cod",
    paymentStatus: String = "unpaid",
    isTest: Boolean = false,
    discountMinor: Long = 0,
    deliveryFeeMinor: Long = 5_000,
    note: String = "",
    scheduledFor: Long? = null,
    readyAt: Long? = null,
  ) = OrderSlip.Order(
    orderNo = 42, fulfillment = fulfillment, paymentMethod = paymentMethod, paymentStatus = paymentStatus,
    customerName = " مریم ", landmark = " پشتِ مسجدِ جامع ", note = note,
    scheduledFor = scheduledFor, readyAt = readyAt, isTest = isTest,
    lines = listOf(
      OrderSlip.Line("برنج", "کیلو", 1_500, 12_000, 18_000, note = "دانه‌بلند"),
      OrderSlip.Line("روغن", "", 2_000, 33_333, 66_666),
    ),
    subtotalMinor = 84_666, discountMinor = discountMinor, deliveryFeeMinor = deliveryFeeMinor,
    totalMinor = 84_666 - discountMinor + deliveryFeeMinor,
  )

  private val time: (Long) -> String = { "t$it" }

  @Test
  fun deliveryCodSlipHasTheServerAmountsInPaperOrder() {
    val rows = OrderSlip.rows(order(readyAt = 900L), Tx, printedAt = 1_000L, time = time)
    assertEquals(
      listOf(
        "title:42", "delivery", "ready:t900", "customer:مریم", "landmark:پشتِ مسجدِ جامع", "cod", "collect:896.66",
        OrderSlip.RULE,
        "line:برنج|1.5 کیلو|180", "note:دانه‌بلند",
        "line:روغن|2|666.66",
        OrderSlip.RULE,
        "subtotal:846.66", "fee:50", "total:896.66", "printed:t1000",
      ),
      rows.map { it.text },
    )
    assertEquals(OrderSlip.Kind.TITLE, rows.first().kind)
    assertTrue(rows.first().center)
    assertEquals(OrderSlip.Kind.HEAD, rows.first { it.text.startsWith("total:") }.kind)
  }

  @Test
  fun paidPickupHasNoCollectNoLandmarkAndShowsDiscountScheduleAndNote() {
    val rows = OrderSlip.rows(
      order(fulfillment = "pickup", paymentMethod = "online", paymentStatus = "paid", discountMinor = 4_666,
        deliveryFeeMinor = 0, note = "زنگ نزنید", scheduledFor = 5_000L),
      Tx, printedAt = 7L, time = time,
    ).map { it.text }
    assertEquals("pickup", rows[1])
    assertTrue("scheduled:t5000" in rows)
    assertTrue("paid" in rows)
    assertFalse(rows.any { it.startsWith("collect:") }, "پرداخت‌شده: «دریافت» ندارد")
    assertFalse(rows.any { it.startsWith("landmark:") }, "حضوری: نشانه روی کاغذ نیست")
    assertFalse(rows.any { it.startsWith("fee:") }, "بی هزینهٔ ارسال ردیف ندارد")
    assertTrue("discount:46.66" in rows)
    assertTrue("total:800" in rows)
    assertTrue("orderNote:زنگ نزنید" in rows)
  }

  @Test
  fun testOrderIsMarkedAndUnpaidNonCodSaysUnpaid() {
    val rows = OrderSlip.rows(order(isTest = true, paymentMethod = "online"), Tx, 0L, time).map { it.text }
    assertEquals(listOf("title:42", "test", "delivery"), rows.take(3))
    assertTrue("unpaid" in rows)
    assertFalse(rows.any { it.startsWith("collect:") })
  }

  @Test
  fun autoPrintOnlyOnThisDevicesAcceptAndOncePerOrder() {
    val id = "01JABCDEF0123456789XYZ0000"
    //  خاموش ⇒ هرگز (رفتارِ امروز)
    assertFalse(OrderAutoPrint.shouldPrint(false, "new", "accepted", id, emptyList()))
    assertTrue(OrderAutoPrint.shouldPrint(true, "new", "accepted", id, emptyList()))
    //  فقط «قبول»؛ پاسخِ «بی‌تغییر» (از قبول به قبول) نه
    for (to in listOf("rejected", "picking", "packed", "delivered")) assertFalse(OrderAutoPrint.shouldPrint(true, "new", to, id, emptyList()), to)
    assertFalse(OrderAutoPrint.shouldPrint(true, "accepted", "accepted", id, emptyList()))
    //  یک بار برای هر سفارش روی هر دستگاه
    val printed = OrderAutoPrint.remember(emptyList(), id)
    assertFalse(OrderAutoPrint.shouldPrint(true, "new", "accepted", id, printed))
    assertTrue(OrderAutoPrint.shouldPrint(true, "new", "accepted", "other-order", printed))
    //  شناسهٔ ناجور هرگز (و در ذخیره کار را خراب نمی‌کند)
    assertFalse(OrderAutoPrint.shouldPrint(true, "new", "accepted", "", emptyList()))
    assertFalse(OrderAutoPrint.shouldPrint(true, "new", "accepted", "a,b", emptyList()))
  }

  @Test
  fun printedMemoryRoundTripsIsBoundedAndIgnoresJunk() {
    assertEquals(emptyList(), OrderAutoPrint.parse(null))
    assertEquals(emptyList(), OrderAutoPrint.parse(""))
    assertEquals(listOf("a1", "b2"), OrderAutoPrint.parse(" a1 , ,b2,a1,x y,"))
    var list = emptyList<String>()
    for (i in 1..(OrderAutoPrint.MEMORY + 25)) list = OrderAutoPrint.remember(list, "o$i")
    assertEquals(OrderAutoPrint.MEMORY, list.size)
    assertEquals("o${OrderAutoPrint.MEMORY + 25}", list.last())
    assertFalse("o1" in list, "کهنه‌ترین‌ها بیرون می‌روند")
    assertEquals(list, OrderAutoPrint.parse(OrderAutoPrint.encode(list)))
    //  دوباره دیدنِ همان شناسه آن را تازه می‌کند، تکراری نمی‌سازد
    val again = OrderAutoPrint.remember(list, list.first())
    assertEquals(list.size, again.size)
    assertEquals(list.first(), again.last())
  }
}
