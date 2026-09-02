package ir.vil3ntec.tohid.sync

import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.ShopData
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  شمارهٔ فاکتور بینِ دو گوشی.
 *
 *  `nextInvoiceNo` همگام نمی‌شود و نباید هم بشود — عددِ دو گوشی هر لحظه
 *  فرق دارد و همگام کردنش هرکدام را عقب می‌برد. راهِ درست این است که
 *  بعد از هر ادغام، شمارنده از بلندترین شماره‌ای که کسی مصرف کرده جلوتر
 *  برود. آن‌وقت شماره‌ها به هم می‌رسند و از آن لحظه تکراری ساخته نمی‌شود.
 */
class InvoiceCursorTest {

  private fun sale(id: String, number: Int) =
    Sale(id = id, invoiceNumber = number, finalTotal = 100.0, date = "2026-09-01")

  private fun change(collection: String, id: String, data: kotlinx.serialization.json.JsonObject): JsonArray =
    buildJsonArray {
      add(
        buildJsonObject {
          put("collection", JsonPrimitive(collection))
          put("id", JsonPrimitive(id))
          put("updatedAt", JsonPrimitive(1000))
          put("deleted", JsonPrimitive(false))
          put("data", data)
        }
      )
    }

  @Test
  fun `شمارنده از بلندترین شمارهٔ مصرف‌شده جلوتر می‌رود`() {
    val d = ShopData(
      sales = listOf(sale("s1", 1), sale("s2", 7), sale("s3", 3)),
      nextInvoiceNo = 4,
    )
    assertEquals(8, SyncEngine.withInvoiceCursor(d).nextInvoiceNo)
  }

  @Test
  fun `شمارنده‌ای که جلوتر است، عقب برده نمی‌شود`() {
    val d = ShopData(sales = listOf(sale("s1", 2)), nextInvoiceNo = 50)
    assertEquals(50, SyncEngine.withInvoiceCursor(d).nextInvoiceNo)
  }

  @Test
  fun `دفترِ بی‌فاکتور دست نمی‌خورد`() {
    val d = ShopData(nextInvoiceNo = 1)
    assertEquals(1, SyncEngine.withInvoiceCursor(d).nextInvoiceNo)
  }

  @Test
  fun `فاکتورِ رسیده از گوشیِ دیگر، شمارنده را جلو می‌برد`() {
    //  گوشیِ ما تا #۳ رفته؛ گوشیِ دیگر #۹ فرستاده است
    val mine = ShopData(sales = listOf(sale("s1", 3)), nextInvoiceNo = 4)
    val incoming = change(
      "sales",
      "s9",
      buildJsonObject {
        put("id", JsonPrimitive("s9"))
        put("invoiceNumber", JsonPrimitive(9))
        put("finalTotal", JsonPrimitive(100.0))
        put("date", JsonPrimitive("2026-09-01"))
      },
    )
    val merged = SyncEngine.merge(mine, incoming, null)
    assertEquals(2, merged.data.sales.size)
    assertEquals(10, merged.data.nextInvoiceNo)
  }

  /* ------------------------- ادغامِ سریع ------------------------- */

  @Test
  fun `ادغام، رکوردِ موجود را سرِ جای خودش عوض می‌کند`() {
    val base = ShopData(
      products = listOf(
        Product(id = "p1", name = "برنج"),
        Product(id = "p2", name = "روغن"),
        Product(id = "p3", name = "چای"),
      )
    )
    val incoming = change(
      "products",
      "p2",
      buildJsonObject {
        put("id", JsonPrimitive("p2"))
        put("name", JsonPrimitive("روغن آفتاب‌گردان"))
      },
    )
    val merged = SyncEngine.merge(base, incoming, null)
    //  ترتیب نباید به هم بخورد — وگرنه هر همگام‌سازی فهرست را می‌ریزد
    assertEquals(listOf("p1", "p2", "p3"), merged.data.products.map { it.id })
    assertEquals("روغن آفتاب‌گردان", merged.data.products[1].name)
    assertEquals(1, merged.touched)
  }

  @Test
  fun `رکوردِ تازه ته صف می‌نشیند`() {
    val base = ShopData(products = listOf(Product(id = "p1", name = "برنج")))
    val incoming = change(
      "products",
      "p9",
      buildJsonObject {
        put("id", JsonPrimitive("p9"))
        put("name", JsonPrimitive("نمک"))
      },
    )
    val merged = SyncEngine.merge(base, incoming, null)
    assertEquals(listOf("p1", "p9"), merged.data.products.map { it.id })
  }

  @Test
  fun `تغییرِ خودمان که برگشته، تغییر حساب نمی‌شود`() {
    val base = ShopData(products = listOf(Product(id = "p1", name = "برنج")))
    val same = change(
      "products",
      "p1",
      kotlinx.serialization.json.Json { encodeDefaults = true }
        .encodeToJsonElement(Product.serializer(), base.products[0])
        as kotlinx.serialization.json.JsonObject,
    )
    val merged = SyncEngine.merge(base, same, null)
    assertEquals(0, merged.touched)
    assertTrue(merged.data.products.single().name == "برنج")
  }
}
