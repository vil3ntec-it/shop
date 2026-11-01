package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.ledger.AppendOnce
import ir.vil3ntec.tohid.core.pos.SpecialOrder.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-26 — سفارشِ ویژه روی دفترِ دکان: سند و وضعیت در سابقه، پیش‌پرداخت رسیدِ مشتری، تحویل فروشِ عادیِ صندوق که
 *  پیش‌پرداخت از آن کم می‌شود، لغو با بازپرداخت از دخل. همان ترکیبی که `SpecialOrderPref.record` می‌دود (`AppendOnce`
 *  روی دفترِ همین لحظه)؛ این‌جا دیسک یک متغیر است.
 */
class SpecialOrdersTest {

  private val today = "2026-11-01"
  private val t0 = 1_761_900_000_000L
  private val ahmad = Debtor(id = "d1", name = "احمد")
  private val fridge = Product(id = "p1", name = "یخچال", salePrice = 25_000.0)
  private val base = ShopData(
    debtors = listOf(ahmad),
    products = listOf(fridge),
  )
  private var seq = 0
  private fun newId() = "n${seq++}"

  private class Disk(var ledger: ShopData) {
    var writes = 0
    fun update(transform: (ShopData?) -> ShopData?): Boolean? {
      val next = transform(ledger) ?: return null
      ledger = next; writes++
      return true
    }
  }

  private fun run(disk: Disk, key: String, has: (ShopData, String) -> Boolean, build: (ShopData, String) -> SpecialOrders.Built): Any {
    var bad: SpecialOrders.Built.Bad? = null
    val out = AppendOnce.append(key, has, { cur: ShopData, k: String ->
      when (val b = build(cur, k)) {
        is SpecialOrders.Built.Ok -> b.data
        is SpecialOrders.Built.Bad -> { bad = b; null }
      }
    }) { t -> disk.update(t) }
    return bad ?: out
  }

  private fun order(total: Long = 2_500_000, deposit: Long = 500_000, productId: String? = null, item: String = "یخچالِ ۱۲ فوت", qty: Long = 1000) =
    SpecialOrders.NewOrder("d1", productId, item, qty, total, deposit, "2026-11-10")

  private fun create(disk: Disk, o: SpecialOrders.NewOrder = order(), key: String = "o1", at: Long = t0) =
    run(disk, key, SpecialOrders::hasRow) { cur, k -> SpecialOrders.create(cur, k, o, today, at, "رحیم", ::newId) }

  private fun deliver(disk: Disk, key: String = "s1", orderId: String = "o1", at: Long = t0 + 1000) =
    run(disk, key, SpecialOrders::hasSale) { cur, k -> SpecialOrders.deliver(cur, k, orderId, today, at, "رحیم", "B1", false, ::newId) }

  private fun cancel(disk: Disk, reason: String = "مشتری منصرف شد", key: String = "c1", at: Long = t0 + 1000) =
    run(disk, key, SpecialOrders::hasRow) { cur, k -> SpecialOrders.cancel(cur, k, "o1", reason, today, at, "رحیم", ::newId) }

  private fun cash(d: ShopData) = CashShift.totals(d, t0 - 1, t0 + 10_000, 0.0)

  @Test
  fun `ثبت یک سند و یک رسیدِ پیش‌پرداخت می‌سازد که نقدِ دخل و پیش‌پرداختِ حسابِ مشتری است`() {
    val disk = Disk(base)
    assertTrue(create(disk) is AppendOnce.Outcome.Wrote<*>)
    val row = disk.ledger.auditLog.single { it.id == "o1" }
    assertEquals(SpecialOrders.TYPE, row.type)
    assertEquals(500_000L, row.specialOrder?.depositMinor)
    val dep = disk.ledger.transactions.single()
    assertEquals("receive", dep.type)
    assertEquals(SpecialOrders.DEPOSIT, dep.kind)
    assertEquals("o1", dep.refId)
    assertEquals(5_000.0, dep.amount, 0.0)
    assertTrue(CashShift.isCashReceipt(dep))
    assertEquals(-5_000.0, ShopStore.debt(disk.ledger, "d1"), 0.0)
    assertEquals(5_000.0, cash(disk.ledger).expected, 0.0)
    val v = SpecialOrders.find(disk.ledger, "o1")!!
    assertEquals(Status.OPEN, v.status)
    assertEquals(500_000L, v.heldMinor)
    assertEquals("احمد", v.customer)
    assertFalse(v.review)
  }

  @Test
  fun `همان کلید دو بار ثبت نمی‌شود و ردها هیچ نمی‌نویسند`() {
    val disk = Disk(base)
    create(disk)
    assertEquals(AppendOnce.Outcome.Already, create(disk))
    assertEquals(1, disk.writes)
    assertEquals("NO_CUSTOMER", (create(disk, order().copy(debtorId = "nobody"), key = "o2") as SpecialOrders.Built.Bad).code)
    assertEquals("NOT_WHOLE", (create(disk, order(deposit = 500_050), key = "o3") as SpecialOrders.Built.Bad).code)
    assertEquals("DEPOSIT_OVER_TOTAL", (create(disk, order(deposit = 2_600_000), key = "o4") as SpecialOrders.Built.Bad).code)
    assertEquals("NO_PRODUCT", (create(disk, order(productId = "gone"), key = "o5") as SpecialOrders.Built.Bad).code)
    assertEquals(1, disk.writes)
  }

  @Test
  fun `تحویل فروشِ عادیِ صندوق است و پیش‌پرداخت از آن کم می‌شود — حسابِ مشتری صفر و دخل همان جمع`() {
    val disk = Disk(base.copy(warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 2.0))))
    create(disk, order(productId = "p1", item = "یخچال"))
    assertTrue(deliver(disk) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val sale = d.sales.single()
    assertEquals("s1", sale.id)
    assertEquals("B1", sale.series)
    assertEquals(25_000.0, sale.finalTotal, 0.0)
    assertEquals(5_000.0, sale.debtGiven, 0.0)
    assertEquals(20_000.0, sale.paidAmount, 0.0)
    assertEquals("d1", sale.debtorId)
    assertEquals(1.0, d.saleItems.single().quantity, 0.0)
    assertEquals(1, d.stockMovements.count { it.type == "sale" && it.productId == "p1" })
    val credit = d.transactions.single { it.kind == SalesEngine.SALE_CREDIT }
    assertTrue(credit.notes.startsWith("کسرِ پیش‌پرداختِ سفارشِ ویژه"))
    assertEquals(0.0, ShopStore.debt(d, "d1"), 0.0)
    //  پیش‌پرداخت (رسید) + نقدِ تحویل = جمعِ فاکتور
    assertEquals(25_000.0, cash(d).expected, 0.0)
    val v = SpecialOrders.find(d, "o1")!!
    assertEquals(Status.DELIVERED, v.status)
    assertEquals("s1", v.saleId)
    assertFalse(v.review)
    //  دوباره نه: همان کلید ⇒ همان فروش؛ کلیدِ دیگر ⇒ ردِ وضعیت
    assertEquals(AppendOnce.Outcome.Already, deliver(disk))
    assertEquals("NOT_ALLOWED", (deliver(disk, key = "s2") as SpecialOrders.Built.Bad).code)
    assertEquals(1, disk.ledger.sales.size)
  }

  @Test
  fun `شرحِ دلخواه قلمِ آزاد است و کمبودِ موجودی همان ردِ صندوق را می‌دهد`() {
    val free = Disk(base)
    create(free, order(item = "رنگِ سفارشی", qty = 3000, total = 1_000_000, deposit = 0))
    assertTrue(deliver(free) is AppendOnce.Outcome.Wrote<*>)
    val item = free.ledger.saleItems.single()
    assertEquals("رنگِ سفارشی", item.name)
    assertEquals(3.0, item.quantity, 0.0)
    assertEquals(10_000.0, item.totalPrice, 0.0)
    assertEquals("cash", free.ledger.sales.single().paymentMethod)
    assertTrue(free.ledger.stockMovements.isEmpty())

    val short = Disk(base)
    create(short, order(productId = "p1", item = "یخچال"))
    val bad = deliver(short) as SpecialOrders.Built.Bad
    assertEquals("SALE", bad.code)
    assertNotNull(bad.detail)
    assertTrue(short.ledger.sales.isEmpty())
    assertEquals(Status.OPEN, SpecialOrders.find(short.ledger, "o1")!!.status)
  }

  @Test
  fun `لغو با دلیل همهٔ پیش‌پرداخت را از دخل پس می‌دهد`() {
    val disk = Disk(base)
    create(disk)
    assertEquals("BAD_REASON", (cancel(disk, reason = " ab ") as SpecialOrders.Built.Bad).code)
    assertTrue(cancel(disk) is AppendOnce.Outcome.Wrote<*>)
    val d = disk.ledger
    val back = d.transactions.single { SpecialOrders.isRefund(it) }
    assertEquals(5_000.0, back.amount, 0.0)
    assertEquals("o1", back.refId)
    assertEquals(0.0, ShopStore.debt(d, "d1"), 0.0)
    //  رسید + بازپرداخت = هیچ نقدی در دخل نمانده
    assertEquals(0.0, cash(d).expected, 0.0)
    val events = PayFlow.ledgerEvents(d, AuditEntry(id = "open", createdAt = t0 - 1), t0 + 10_000)
    assertEquals(500_000L, events.single { it.id == "sor:${back.id}" }.rows!!.single().amount)
    val v = SpecialOrders.find(d, "o1")!!
    assertEquals(Status.CANCELLED, v.status)
    assertEquals(0L, v.heldMinor)
    assertFalse(v.review)
    assertEquals("NOT_ALLOWED", (deliver(disk) as SpecialOrders.Built.Bad).code)
  }

  @Test
  fun `آماده، و فروشِ تحویلِ لغوشده سفارش را دوباره باز می‌کند و پیش‌پرداخت به حساب برمی‌گردد`() {
    val disk = Disk(base)
    create(disk, order(item = "پرده", total = 300_000, deposit = 100_000))
    run(disk, "r1", SpecialOrders::hasRow) { cur, k -> SpecialOrders.ready(cur, k, "o1", today, t0 + 10, "رحیم") }
    assertEquals(Status.READY, SpecialOrders.find(disk.ledger, "o1")!!.status)
    assertEquals("NOT_ALLOWED", (run(disk, "r2", SpecialOrders::hasRow) { cur, k -> SpecialOrders.ready(cur, k, "o1", today, t0 + 20, "رحیم") } as SpecialOrders.Built.Bad).code)
    deliver(disk)
    val cancelled = (SalesEngine.cancel(disk.ledger, "s1", today, t0 + 5000, ::newId, "اشتباه") as SalesEngine.Result.Ok).data
    val v = SpecialOrders.find(cancelled, "o1")!!
    assertEquals(Status.READY, v.status)
    assertNull(v.saleId)
    assertEquals(-1_000.0, ShopStore.debt(cancelled, "d1"), 0.0)
    //  دوباره تحویل‌شدنی است
    val again = Disk(cancelled)
    assertTrue(deliver(again, key = "s2", at = t0 + 6000) is AppendOnce.Outcome.Wrote<*>)
    assertEquals(Status.DELIVERED, SpecialOrders.find(again.ledger, "o1")!!.status)
  }

  @Test
  fun `تحویل و لغو روی دو دستگاهِ بی‌خبر از هم ⇒ نیازمندِ بررسی`() {
    val disk = Disk(base)
    create(disk)
    val a = Disk(disk.ledger).also { deliver(it) }.ledger
    val b = Disk(disk.ledger).also { cancel(it, key = "c1", at = t0 + 900) }.ledger
    val merged = a.copy(
      auditLog = a.auditLog + b.auditLog.filter { x -> a.auditLog.none { it.id == x.id } },
      transactions = a.transactions + b.transactions.filter { x -> a.transactions.none { it.id == x.id } },
    )
    val v = SpecialOrders.find(merged, "o1")!!
    assertEquals(Status.CANCELLED, v.status)
    assertTrue(v.review)
  }

  @Test
  fun `فهرست باز و آماده را اول به ترتیبِ روزِ تحویل می‌آورد و مشتریِ ادغام‌شده دنبال می‌شود`() {
    val disk = Disk(base.copy(debtors = listOf(ahmad.copy(mergedInto = "d2"), Debtor(id = "d2", name = "احمدِ کریمی"))))
    create(disk, order().copy(due = "2026-11-20"), key = "o1")
    create(disk, order().copy(due = "2026-11-05"), key = "o2")
    create(disk, order(), key = "o3")
    cancel(disk, key = "c1")
    val list = SpecialOrders.all(disk.ledger)
    assertEquals(listOf("o2", "o3", "o1"), list.map { it.id })
    assertEquals(listOf(true, true, false), list.map { it.active })
    assertTrue(list.all { it.debtorId == "d2" && it.customer == "احمدِ کریمی" })
    assertTrue(disk.ledger.transactions.filter { it.kind == SpecialOrders.DEPOSIT }.all { it.debtorId == "d2" })
  }
}
