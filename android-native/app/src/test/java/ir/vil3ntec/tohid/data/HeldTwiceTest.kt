package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.core.audit.InvoiceVoid
import ir.vil3ntec.tohid.ui.screens.SalesVoid
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S09 — لغوِ خودکارِ فروشِ دومِ «یک فاکتور در دو دستگاه» (`HeldTwice`، پرچمِ خاموشِ `shop.held_twice_cancel`)، با همان
 *  سرورِ ساختگیِ `HeldClaimTest` (قراردادِ `lib/shop-held-sync.js` و `lib/held-twice.js`).
 *
 *  پرچمِ خاموش ⇒ همان «دوتایی» ِ پیشین (بی کلیدِ فروش، بی لغو، بی گزارش). روشن ⇒ دستگاهِ دوم فروشِ **خودش** را فقط وقتی
 *  فروشِ نخست قلم‌به‌قلم و مبلغ‌به‌مبلغ در دفترش هست، از راهِ لغوِ عادی (`SalesVoid.run`) لغو می‌کند و به صندوقِ صاحب
 *  گزارش می‌دهد؛ ناهمسان یا دوپهلو ⇒ دست نمی‌خورد؛ بی اجازهٔ لغو ⇒ فقط گزارش.
 */
class HeldTwiceTest {

  private val h = HeldClaimTest()

  private fun sale(id: String, series: String, at: Long, qty: Double = 2.0, price: Double = 50.0, status: String = "completed") = Sale(
    id = id, series = series, invoiceNumber = 3, createdAt = at, total = qty * price, finalTotal = qty * price,
    paidAmount = qty * price, paymentMethod = "cash", status = status,
  )
  private fun item(sale: String, qty: Double = 2.0, price: Double = 50.0) =
    SaleItem(id = "$sale-i", saleId = sale, productId = "p1", quantity = qty, unitPrice = price, totalPrice = qty * price)

  private fun ledger(vararg pairs: Pair<Sale, SaleItem>) = ShopData(
    products = listOf(Product(id = "p1", name = "برنج")),
    sales = pairs.map { it.first }, saleItems = pairs.map { it.second },
  )

  private val base = 1_700_000_000_000L
  private fun conflict(sale: String = "B-k1") = HeldConflict("کریمی", "A1", "resumed", at = base + 5_000, sale = sale, base = base)

  @Test fun `داوری — فقط دو فروشِ یکسان از دو دستگاه؛ نرسیده صبر؛ ناهمسان، دوپهلو، برگشت‌خورده یا لغوشده دست نمی‌خورد`() {
    val now = base + 10_000
    val mine = sale("B-k1", "B1", base + 3_000) to item("B-k1")
    val first = sale("K1", "A1", base + 2_000) to item("K1")
    val d = ledger(mine, first)
    assertEquals(HeldTwice.Decision.Cancel(first.first), HeldTwice.decide(d, conflict(), now, canCancel = true))
    assertEquals(HeldTwice.Decision.Report(first.first), HeldTwice.decide(d, conflict(), now, canCancel = false))
    //  فروشِ نخست هنوز نرسیده ⇒ صبر؛ هفت روز بعد ⇒ رها
    assertEquals(HeldTwice.Decision.Wait, HeldTwice.decide(ledger(mine), conflict(), now, true))
    assertEquals(HeldTwice.Decision.Leave, HeldTwice.decide(ledger(mine), conflict(), base + 5_000 + HeldTwice.WAIT_MS + 1, true))
    //  فروشِ دوم هنوز ثبت نشده (سبد باز) ⇒ صبر
    assertEquals(HeldTwice.Decision.Wait, HeldTwice.decide(ledger(first), conflict(), now, true))
    //  مقدارِ دیگر ⇒ یکی نیستند
    assertEquals(HeldTwice.Decision.Wait, HeldTwice.decide(ledger(mine, sale("K1", "A1", base + 2_000, qty = 3.0) to item("K1", qty = 3.0)), conflict(), now, true))
    //  دو فروشِ یکسانِ دیگر ⇒ دوپهلو
    val twin = sale("K9", "C1", base + 2_500) to item("K9")
    assertEquals(HeldTwice.Decision.Leave, HeldTwice.decide(ledger(mine, first, twin), conflict(), now, true))
    //  همان سری (همین دستگاه) یا پیش از نسخهٔ دیده‌شده ⇒ نه فروشِ نخست
    assertEquals(HeldTwice.Decision.Wait, HeldTwice.decide(ledger(mine, sale("K1", "B1", base + 2_000) to item("K1")), conflict(), now, true))
    assertEquals(HeldTwice.Decision.Wait, HeldTwice.decide(ledger(mine, sale("K1", "A1", base - HeldTwice.SKEW_MS - 1) to item("K1")), conflict(), now, true))
    //  برگشت‌خورده یا از پیش لغوشده ⇒ کاربر داوری کرده
    assertEquals(HeldTwice.Decision.Leave, HeldTwice.decide(d.copy(saleReturns = listOf(SaleReturn("r1", saleId = "B-k1"))), conflict(), now, true))
    assertEquals(HeldTwice.Decision.Leave, HeldTwice.decide(ledger(sale("B-k1", "B1", base + 3_000, status = "cancelled") to item("B-k1"), first), conflict(), now, true))
  }

  @Test fun `لغو از راهِ عادی — وضعیتِ «لغوشده» با دلیلِ ثابت و بازگشتِ موجودی؛ فروشِ نخست دست نمی‌خورد`() {
    val d = ledger(sale("B-k1", "B1", base + 3_000) to item("B-k1"), sale("K1", "A1", base + 2_000) to item("K1"))
    val r = SalesVoid.run(d, "B-k1", true, InvoiceVoid.Kind.VOID, HeldTwice.REASON, "2026-11-14", base + 20_000, { "n1" }, "usr", "x")
    assertTrue(r is SalesVoid.Outcome.Ok)
    val after = (r as SalesVoid.Outcome.Ok).data
    assertEquals("cancelled", after.sales.first { it.id == "B-k1" }.status)
    assertEquals(HeldTwice.REASON, after.sales.first { it.id == "B-k1" }.cancelReason)
    assertEquals("completed", after.sales.first { it.id == "K1" }.status)
  }

  /** دو دستگاهِ بی‌اینترنت یک نگه‌داشته را باز می‌کنند و هر دو می‌فروشند؛ «ب» پس از وصل «دوتایی» می‌گیرد */
  private fun twoDevices(auto: Boolean): Triple<HeldClaimTest.Server, HeldClaimTest.Device, String> = runBlocking {
    val server = HeldClaimTest.Server().apply { autoCancel = auto }
    val a = h.Device(server, "dev-a", "A1")
    val b = h.Device(server, "dev-b", "B1")
    a.now += 1000; a.cart.put("K2", "p1" to 2.0)
    assertTrue(a.store.hold(a.cart, HeldExtras(), label = "کریمی") is HeldInvoiceStore.Outcome.Done)
    a.sync(); b.sync()
    b.online = false
    b.now += 1000
    val off = b.engine.claim("K2")
    assertTrue(off is HeldClaim.Rekey)
    assertTrue(b.store.resume(b.cart, (off as HeldClaim.Rekey).id, HeldExtras()) is HeldInvoiceStore.Outcome.Done)
    a.now += 2000; server.now = a.now
    assertEquals(HeldClaim.Free, a.engine.claim("K2"))
    a.sync()
    b.online = true
    b.sync()
    Triple(server, b, off.id)
  }

  @Test fun `پرچمِ خاموش — همان دوتاییِ پیشین، بی کلیدِ فروش و بی گزارش`() = runBlocking<Unit> {
    val (server, b, key) = twoDevices(auto = false)
    val c = b.engine.conflicts().getValue("K2")
    assertEquals(HeldConflict("کریمی", "A1", "resumed", c.at), c)
    assertFalse(b.engine.autoCancel)
    assertTrue(b.engine.twiceOpen().isEmpty())
    val text = HeldSyncEngine.encodeState(b.state.s)
    for (k in listOf("autoCancel", "\"sale\"", "kept", "reported")) assertFalse("«$k» نوشته نشد: $text", text.contains(k))
    var cancelled = 0
    HeldTwice.sweep(b.engine, { ShopData() }, true, b.now, "dev-b") { cancelled++; null }
    assertEquals(0, cancelled)
    assertTrue(server.twice.isEmpty())
    assertNotEquals("K2", key)
  }

  @Test fun `روشن — فروشِ دومِ همین دستگاه لغو و به صندوقِ صاحب گزارش می‌شود؛ دیدن پیش از گزارش آن را گم نمی‌کند`() = runBlocking<Unit> {
    val (server, b, key) = twoDevices(auto = true)
    assertTrue(b.engine.autoCancel)
    val c = b.engine.twiceOpen().getValue("K2")
    assertEquals("فروشِ همین دستگاه همان کلیدِ تازه است", key, c.sale)
    assertTrue(c.base > 0)

    //  دفترِ «ب»: فروشِ خودش و فروشِ «الف» (رسیده با همگام‌سازی)
    var d = ledger(sale(key, "B1", b.now - 500) to item(key), sale("K2", "A1", b.now - 400) to item("K2"))
    //  بی شبکه ⇒ لغو می‌شود، گزارش در صف
    b.online = false
    HeldTwice.sweep(b.engine, { d }, true, b.now, "dev-b") { s ->
      (SalesVoid.run(d, s.id, true, InvoiceVoid.Kind.VOID, HeldTwice.REASON, "2026-11-14", b.now, { "n1" }, null, "x") as SalesVoid.Outcome.Ok).data.also { d = it }
    }
    assertEquals("cancelled", d.sales.first { it.id == key }.status)
    val settled = b.engine.conflicts().getValue("K2")
    assertEquals("K2", settled.kept)
    assertTrue(settled.cancelled && settled.unreported)
    //  کاربر «دیدم» زد پیش از رسیدنِ گزارش ⇒ پنهان، ولی گزارش می‌ماند
    b.engine.ack("K2")
    assertTrue(b.engine.conflicts().isEmpty())
    assertEquals(setOf("K2"), b.engine.twiceOpen().keys)
    //  وصل ⇒ فقط گزارش (لغوِ دوباره نه)
    b.online = true
    var again = 0
    HeldTwice.sweep(b.engine, { d }, true, b.now, "dev-b") { again++; null }
    assertEquals(0, again)
    val (id, body) = server.twice.single()
    assertEquals("K2", id)
    assertEquals(key, body["sale_id"]!!.jsonPrimitive.content)
    assertEquals("K2", body["kept_id"]!!.jsonPrimitive.content)
    assertEquals("A1", body["kept_series"]!!.jsonPrimitive.content)
    assertEquals(JsonPrimitive(true), body["cancelled"])
    assertEquals(10_000L, body["total_minor"]!!.jsonPrimitive.content.toLong())
    assertEquals("برنج", body["items"]!!.jsonArray.single().jsonObject["name"]!!.jsonPrimitive.content)
    assertTrue("گزارش رسید و دیده شده بود ⇒ کنار", b.engine.twiceOpen().isEmpty())
    assertTrue(b.state.s.conflicts.isEmpty())
  }

  @Test fun `روشن ولی بی اجازهٔ لغو — فقط گزارش؛ ناهمسان — همان دوتاییِ پیشین`() = runBlocking<Unit> {
    val (server, b, key) = twoDevices(auto = true)
    val d = ledger(sale(key, "B1", b.now - 500) to item(key), sale("K2", "A1", b.now - 400) to item("K2"))
    HeldTwice.sweep(b.engine, { d }, false, b.now, "dev-b") { error("بی اجازه لغو نمی‌شود") }
    assertEquals(JsonPrimitive(false), server.twice.single().second["cancelled"])
    assertFalse(b.engine.conflicts().getValue("K2").cancelled)

    val (server2, b2, key2) = twoDevices(auto = true)
    val other = ledger(sale(key2, "B1", b2.now - 500) to item(key2), sale("K2", "A1", b2.now - 400, qty = 1.0) to item("K2", qty = 1.0))
    HeldTwice.sweep(b2.engine, { other }, true, b2.now, "dev-b") { error("ناهمسان لغو نمی‌شود") }
    //  شاید فروشِ نخستِ یکسان هنوز نرسیده ⇒ صبر؛ هفت روز بعد رها ⇒ همان «دوتایی» ِ پیشین برای کاربر
    assertEquals(setOf("K2"), b2.engine.twiceOpen().keys)
    HeldTwice.sweep(b2.engine, { other }, true, b2.now + HeldTwice.WAIT_MS + 1, "dev-b") { error("ناهمسان لغو نمی‌شود") }
    assertTrue(b2.engine.twiceOpen().isEmpty())
    assertEquals(setOf("K2"), b2.engine.conflicts().keys)
    assertTrue(server2.twice.isEmpty())
  }
}
