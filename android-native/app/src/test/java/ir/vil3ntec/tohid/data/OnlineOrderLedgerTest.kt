package ir.vil3ntec.tohid.data

import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.SyncOp
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  ORD-20 — فاکتورِ سریِ `W` که سرور هنگامِ تحویلِ سفارشِ اینترنتی در دفترِ دکان
 *  می‌گذارد، روی دستگاه:
 *    • با همان `applyRemote` ِ Sync v1 می‌نشیند و موجودی را **یک بار** کم می‌کند؛
 *    • در فهرستِ فروش `W-1` و «اینترنتی» است؛
 *    • هیچ opِ پژواکی نمی‌سازد (دستگاه آن را دوباره به سرور نمی‌فرستد)؛
 *    • صندوق آن را لغو یا «اصلاح در سبد» (= دوباره زدن) نمی‌کند؛
 *    • سندِ برگشتِ سرور موجودی را برمی‌گرداند و فاکتور را ویرایش نمی‌کند.
 *  شکلِ opها همان است که `server/src/lib/order-ledger.js` می‌نویسد؛ قرینهٔ
 *  سرور با pullِ واقعی در `server/test/order-ledger.test.js`.
 */
class OnlineOrderLedgerTest {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", salePrice = 120.0, purchasePrice = 90.0)
  private fun ledger() = ShopData(
    products = listOf(rice),
    warehouseEntries = listOf(WarehouseEntry(id = "we1", productId = "p1", units = 10.0)),
    nextInvoiceNo = 7,
  )

  private fun obj(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject

  /** همان opهای سرور برای سفارشِ #12 با یک کیلو و نیم برنج و یک قلمِ آزاد */
  private val saleOps = listOf(
    SyncOp("w_1", 1, "sales", "w-o1", SyncOp.INSERT, obj(
      """{"invoiceNumber":1,"total":185,"discountType":"amount","discountValue":10,"discount":10,"finalTotal":175,
         "paymentMethod":"cash","debtorId":null,"paidAmount":175,"remaining":0,"status":"completed","debtGiven":0,
         "debtSettled":0,"createdAt":1760000000000,"date":"2026-10-18","syncStatus":"synced","cancelReason":"",
         "seller":"","series":"W","channel":"online","orderId":"o1","orderNo":12}"""
    )),
    SyncOp("w_2", 1, "saleItems", "w-o1-1", SyncOp.INSERT, obj(
      """{"saleId":"w-o1","productId":"p1","name":"برنج","quantity":1.5,"unitPrice":120,"purchasePrice":90,
         "totalPrice":180,"returnedQty":0}"""
    )),
    SyncOp("w_3", 1, "saleItems", "w-o1-2", SyncOp.INSERT, obj(
      """{"saleId":"w-o1","productId":"","name":"کیسه","quantity":1,"unitPrice":5,"purchasePrice":0,
         "totalPrice":5,"returnedQty":0}"""
    )),
    SyncOp("w_4", 1, "stockMovements", "w-o1-1-m", SyncOp.INSERT, obj(
      """{"productId":"p1","type":"sale","qty":-1.5,"date":"2026-10-18","notes":"فاکتور #W-1","refId":"w-o1",
         "createdAt":1760000000000}"""
    )),
    SyncOp("w_5", 1, "auditLog", "w-o1-a", SyncOp.INSERT, obj(
      """{"type":"sale","date":"2026-10-18","refId":"w-o1","notes":"ثبت فروش فاکتور #W-1","createdAt":1760000000000,"amount":0}"""
    )),
  )

  private val returnOps = listOf(
    SyncOp("w_r1", 2, "returns", "w-o1-1-r", SyncOp.INSERT, obj(
      """{"saleId":"w-o1","saleItemId":"w-o1-1","productId":"p1","quantity":1.5,"amount":170.27,"reason":"",
         "date":"2026-10-19","createdAt":1760100000000}"""
    )),
    SyncOp("w_r2", 2, "saleItems", "w-o1-1", SyncOp.UPDATE, obj("""{"returnedQty":{"${'$'}inc":1.5}}""")),
    SyncOp("w_r3", 2, "stockMovements", "w-o1-1-rm", SyncOp.INSERT, obj(
      """{"productId":"p1","type":"customer_return","qty":1.5,"date":"2026-10-19","notes":"مرجوعی فاکتور #W-1",
         "refId":"w-o1-1-r","createdAt":1760100000000}"""
    )),
  )

  @Test
  fun `فاکتورِ W با pull می‌نشیند، موجودی یک بار کم می‌شود و پژواکی نمی‌سازد`() {
    val before = LedgerDiff.toJson(ledger())
    val tree = LedgerDiff.applyRemote(before, saleOps)
    val d = LedgerDiff.fromJson(tree)

    val sale = d.sales.single()
    assertEquals("W-1", sale.invoiceLabel())
    assertEquals("online", sale.channel)
    assertTrue(sale.isOnlineOrder())
    assertEquals(175.0, sale.finalTotal, 0.0001)
    assertEquals(8.5, ShopStore.stock(d, "p1"), 0.0001)
    //  شمارندهٔ فاکتورِ خودِ دستگاه دست نمی‌خورد
    assertEquals(7, d.nextInvoiceNo)

    //  سایه و دفتر یکی‌اند ⇒ دستگاه چیزی از این فاکتور به سرور پس نمی‌فرستد
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(tree), LedgerDiff.toJson(d)).size)
  }

  @Test
  fun `صندوق فاکتورِ اینترنتی را لغو نمی‌کند ولی مرجوعیِ قلمی مجاز است`() {
    val d = LedgerDiff.fromJson(LedgerDiff.applyRemote(LedgerDiff.toJson(ledger()), saleOps))
    val r = SalesEngine.cancel(d, "w-o1", "2026-10-19", 1L, { "x" }, reason = "ویرایش در سبد")
    assertTrue("$r", r is SalesEngine.Result.Failed)
    assertEquals(SalesEngine.ONLINE_ORDER_LOCKED, (r as SalesEngine.Result.Failed).message)

    var seq = 0
    val back = SalesEngine.recordReturn(d, "w-o1", mapOf("w-o1-1" to 0.5), "", "2026-10-19", 2L, { "t${seq++}" })
    assertTrue("$back", back is SalesEngine.Result.Ok)
    assertEquals(9.0, ShopStore.stock((back as SalesEngine.Result.Ok).data, "p1"), 0.0001)
  }

  @Test
  fun `سندِ برگشتِ سرور موجودی را برمی‌گرداند و فاکتور را ویرایش نمی‌کند`() {
    val sold = LedgerDiff.applyRemote(LedgerDiff.toJson(ledger()), saleOps)
    val tree = LedgerDiff.applyRemote(sold, returnOps)
    val d = LedgerDiff.fromJson(tree)
    assertEquals(10.0, ShopStore.stock(d, "p1"), 0.0001)
    assertEquals("completed", d.sales.single().status)
    assertEquals(LedgerDiff.fromJson(sold).sales.single(), d.sales.single())
    assertEquals(1.5, d.saleItems.first { it.id == "w-o1-1" }.returnedQty, 0.0001)
    assertEquals(1, d.saleReturns.size)
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(tree), LedgerDiff.toJson(d)).size)
  }

  @Test
  fun `فاکتورِ صندوق کانال ندارد و فیلدِ تازه opِ تازه نمی‌سازد`() {
    var seq = 0
    val r = SalesEngine.record(
      ledger(), listOf(SalesEngine.CartLine("p1", 1.0)), SalesEngine.Checkout(), "2026-10-19", 3L, { "s${seq++}" }, series = "A1",
    ) as SalesEngine.Result.Ok
    val sale = r.data.sales.single()
    assertFalse(sale.isOnlineOrder())
    val json = LedgerDiff.toJson(r.data)
    val row = json["sales"].toString()
    assertFalse("channel نوشته نمی‌شود: $row", row.contains("channel"))
    assertEquals(0, LedgerDiff.diff(LedgerDiff.shadowOf(json), LedgerDiff.toJson(LedgerDiff.fromJson(json))).size)
  }
}
