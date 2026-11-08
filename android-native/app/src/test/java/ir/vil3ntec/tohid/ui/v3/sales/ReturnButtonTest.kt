package ir.vil3ntec.tohid.ui.v3.sales

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ReturnLeft
import ir.vil3ntec.tohid.data.SaleReturn
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import ir.vil3ntec.tohid.ui.v3.sales.history.HistoryModel
import ir.vil3ntec.tohid.ui.v3.sales.history.RowLookup
import ir.vil3ntec.tohid.ui.v3.sales.returns.ReturnModel
import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

/**
 *  دکمهٔ «برگشت» در فهرستِ فاکتورها — همان حسابِ ماندهٔ پنجرهٔ برگشت (`ReturnLeft` ⇐ `ReturnCap`، #995، سفرِ JB5).
 *
 *  پیش‌تر دکمه با شمارندهٔ قلم (`SaleItem.returnedQty`) تصمیم می‌گرفت: برگشتِ کاملِ دستگاهِ دیگر که ردیفش با همگام‌سازی
 *  رسیده ولی شمارندهٔ این دستگاه نشمرده بود دکمه را روشن می‌گذاشت و پنجرهٔ برگشت خالی باز می‌شد. حالا هر سه جا — فهرستِ
 *  گوشی/کامپیوترِ نسخهٔ ۳ (`RowLookup`)، ردیفِ تک (`HistoryModel.row`) و فهرستِ فاکتورهای کامپیوتر (`SalesHistory`) —
 *  همان `ReturnLeft` را می‌خوانند. سرخ ⇐ سبز (PR).
 */
class ReturnButtonTest {

  private var seq = 0
  private fun newId(): String = "rb${seq++}"
  private val oil = Product(id = "p1", name = "روغن", unit = "عدد", purchasePrice = 400.0, salePrice = 500.0)

  /** فاکتورِ ۲ روغن به ۵۰۰ */
  private fun sold(): ShopData {
    val d = ShopData(products = listOf(oil), warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 10.0)))
    return (SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 2.0)), SalesEngine.Checkout(), "2026-11-08", 1_790_000_000_000L, ::newId) as SalesEngine.Result.Ok).data
  }

  /** ردیفِ برگشتی که از دستگاهِ دیگر رسیده؛ شمارندهٔ قلمِ این دستگاه دست نخورده */
  private fun fromOtherDevice(d: ShopData, qty: Double): ShopData {
    val item = d.saleItems.single()
    return d.copy(saleReturns = d.saleReturns + SaleReturn(id = "other-${seq++}", saleId = item.saleId, saleItemId = item.id, productId = "p1", quantity = qty, amount = qty * 500))
  }

  /** هر راهی که دکمه را می‌سازد — همه باید یکی بگویند */
  private fun buttons(d: ShopData): List<Boolean> {
    val s = d.sales.single()
    val look = RowLookup(d)
    return listOf(
      HistoryModel.canReturn(d, s),
      look.canReturn(s),
      HistoryModel.row(d, s, returnsV2 = false).canReturn,
      HistoryModel.row(d, s, returnsV2 = true, look = look).canReturn,
      ReturnLeft.canReturn(d, s),
      ReturnLeft.Index(d).canReturn(s),
    )
  }

  @Test fun `برگشتِ کاملِ دستگاهِ دیگر — دکمه خاموش، همان که پنجرهٔ برگشت چیزی برای برگشت ندارد`() {
    val d = fromOtherDevice(sold(), 2.0)
    assertEquals(0.0, d.saleItems.single().returnedQty, 0.0)
    assertEquals("پنجرهٔ برگشت", emptyList<Any>(), ReturnModel.lines(d, d.sales.single()))
    assertEquals(List(6) { false }, buttons(d))
  }

  @Test fun `برگشتِ تکه‌ایِ دستگاهِ دیگر — دکمه روشن، همان مانده`() {
    val d = fromOtherDevice(sold(), 1.0)
    assertEquals(1.0, ReturnLeft.left(d, d.saleItems.single()), 0.0)
    assertEquals(List(6) { true }, buttons(d))
  }

  @Test fun `فاکتورِ تازه روشن، لغوشده خاموش`() {
    val d = sold()
    assertEquals(List(6) { true }, buttons(d))
    val cancelled = d.copy(sales = d.sales.map { it.copy(status = "cancelled") })
    assertEquals(List(6) { false }, buttons(cancelled))
  }

  @Test fun `شمارندهٔ همین دستگاه هم شمرده می‌شود`() {
    val d0 = sold()
    val d = d0.copy(saleItems = d0.saleItems.map { it.copy(returnedQty = 2.0) })
    assertEquals(List(6) { false }, buttons(d))
  }

  /** ⛔ هیچ دکمهٔ برگشتی دیگر با شمارندهٔ تنها تصمیم نمی‌گیرد */
  @Test fun `هیچ دکمهٔ فهرستِ فاکتور با شمارندهٔ قلم تصمیم نمی‌گیرد`() {
    val root = mainSources()
    val files = listOf(
      "ir/vil3ntec/tohid/ui/v3/sales/history/HistoryModel.kt",
      "ir/vil3ntec/tohid/ui/v3/sales/history/HistoryBook.kt",
      "ir/vil3ntec/tohid/ui/screens/SalesHistory.kt",
    )
    val offenders = files.filter { withoutComments(File(root, it).readText()).contains("SalesEngine.returnable(") }
    assertEquals(emptyList<String>(), offenders)
  }

  private fun mainSources(): File {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      for (rel in listOf("src/main/java", "app/src/main/java", "android-native/app/src/main/java")) {
        val f = File(dir, "$rel/ir/vil3ntec/tohid/ui/screens/SalesHistory.kt")
        if (f.isFile) return File(dir, rel)
      }
      dir = dir.parentFile
    }
    fail("کدِ اندروید پیدا نشد (از ${File("").absolutePath})")
    error("unreachable")
  }

  private fun withoutComments(src: String): String =
    src.replace(Regex("""/\*[\s\S]*?\*/"""), " ").replace(Regex("""//[^\n]*"""), " ")
}
