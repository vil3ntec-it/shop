package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.core.pay.CashKeys
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.components.pos.PosHit
import ir.vil3ntec.tohid.ui.components.pos.PosSearch
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S11 — دوبار زدنِ پرداخت و Enter ِ تصادفی (POS-14): قفلِ «ثبتِ فروش» (`SalePost`) و کلیدِ یکتای فاکتور
 *  (`SalesEngine.record` با `saleId`)، Enter در کادرِ خالی (`PosSearch.resolve`) و نگهبانِ Enter ِ پنلِ پرداخت
 *  (`CashKeys.enterArmed`، PAY-04؛ پنل پشتِ پرچمِ خاموشِ `ShopPolicy.payV2`).
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • گامِ ۲ (دو کلیک در وب با قطع و وصلِ شبکه) آزموده نشده: صندوقِ وب منجمد است و این آزمون فقط برنامه را می‌سنجد.
 *   • قاعدهٔ «Enter در کادرِ خالی پس از ≥ ۵۰۰ میلی‌ثانیه ⇒ پنلِ پرداخت» (§۵.۲) پیاده نیست: Enter در کادرِ خالی هیچ کاری
 *     نمی‌کند و پنل با F9 باز می‌شود؛ نگهبانِ پنل ۴۰۰ میلی‌ثانیه پس از باز شدن است.
 */
class ScenarioS11Test {

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلوگرم", salePrice = 120.0, barcodes = listOf("6260000000017"))
  private val d0 = ShopData(products = listOf(rice), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 50.0)))
  private val cart = listOf(SalesEngine.CartLine("p1", 2.0))
  private var seq = 0

  private fun record(d: ShopData, key: String) =
    SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 1_000, { "n${seq++}" }, saleId = key)

  @Test
  fun `S11 — پنج Enter ِ پیاپی روی پرداخت ⇒ دقیقاً یک فروش، یک شمارهٔ سند و یک رسید`() = runBlocking<Unit> {
    var ledger = d0
    val post = SalePost()
    val gate = CompletableDeferred<Unit>()
    var receipts = 0
    val first = async {
      post.run("K1", { k -> record(ledger, k) }, { data -> gate.await(); ledger = data; true }) { receipts++ }
    }
    while (!post.busy.value) yield()
    repeat(4) {
      assertEquals(SalePost.Outcome.Busy, post.run("K1", { k -> record(ledger, k) }, { data -> ledger = data; true }) { receipts++ })
    }
    gate.complete(Unit)
    assertTrue(first.await() is SalePost.Outcome.Done)
    //  Enter ِ دیرتر پس از پایانِ ثبت با همان کلید ⇒ «یک بار ثبت شده»، بی نوشتن و بی رسید
    val late = post.run("K1", { k -> record(ledger, k) }, { data -> ledger = data; true }) { receipts++ }
    assertEquals(SalePost.Outcome.Refused(SalesEngine.ALREADY_POSTED), late)
    assertEquals(1, ledger.sales.size)
    assertEquals(1, ledger.sales.mapNotNull { it.invoiceNumber }.distinct().size)
    assertEquals(1, receipts)
    assertEquals(48.0, ShopStore.stock(ledger, "p1"), 0.0)
  }

  @Test
  fun `S11 — اسکن و سه Enter ِ پشتِ سرِ هم ⇒ هیچ فروشی ثبت نمی‌شود`() {
    val index = ShopStore.barcodeIndex(d0)
    //  Enter ِ پسوندِ بارکدخوان ⇒ کالا به سبد؛ کادر خالی می‌شود
    assertEquals(PosHit.Add(rice, 1.0), PosSearch.resolve("6260000000017", index, d0.products, emptyList(), 0))
    //  دو Enter ِ دیگر در کادرِ خالی ⇒ هیچ کار
    repeat(2) { assertEquals(PosHit.None, PosSearch.resolve("", index, d0.products, emptyList(), 0)) }
    //  اگر پنل باز شده باشد، Enter ِ زیرِ ۴۰۰ میلی‌ثانیه پس از باز شدن فاکتور را نمی‌بندد
    for (ms in listOf(0L, 15L, 30L, CashKeys.ENTER_GUARD_MS - 1)) assertFalse(CashKeys.enterArmed(ms))
    assertTrue(CashKeys.enterArmed(CashKeys.ENTER_GUARD_MS))
  }
}
