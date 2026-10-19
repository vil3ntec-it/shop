package ir.vil3ntec.tohid.ui.screens

import ir.vil3ntec.tohid.data.AuditEntry
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.screens.ProductListModel.Sort
import ir.vil3ntec.tohid.ui.screens.ProductListModel.Status
import ir.vil3ntec.tohid.ui.screens.ProductListModel.Tab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** فهرستِ کالای کامپیوتر — کارت‌های آماری، زبانه‌ها و کارِ گروهی از دادهٔ نمونه */
class ProductListModelTest {

  private val now = 1_760_000_000_000L
  private val day = ProductListModel.DAY_MS

  //  نمونه: ۶ کالا — ۴ فعال (یکی کم، یکی تمام)، ۲ غیرفعال؛ ۲ تا در هفتهٔ اخیر ساخته شده
  private val fixture = listOf(
    Product("a", name = "چای سبز", category = "نوشیدنی", barcodes = listOf("111"), purchasePrice = 80.0, salePrice = 100.0, minStock = 5.0, createdAt = now - 2 * day),
    Product("b", name = "برنج", category = "خوراک", barcodes = listOf("222"), purchasePrice = 50.0, salePrice = 70.0, minStock = 10.0, createdAt = now - 30 * day),
    Product("c", name = "صابون", category = "بهداشتی", purchasePrice = 20.0, salePrice = 30.0, createdAt = now - 1 * day),
    Product("d", name = "روغن", category = "خوراک", barcodes = listOf("444"), purchasePrice = 200.0, salePrice = 260.0, minStock = 3.0),
    Product("e", name = "شکر قدیمی", category = "خوراک", active = false, createdAt = now - 3 * day),
    Product("f", name = "کبریت", category = "", active = false, createdAt = now - 8 * day),
  )
  private val stock = mapOf("a" to 40.0, "b" to 6.0, "c" to 0.0, "d" to 3.0, "e" to 0.0, "f" to 12.0)
  private val stockOf: (String) -> Double = { stock[it] ?: 0.0 }

  @Test
  fun `وضعیتِ هر کالا — غیرفعال، تمام، کم، فعال`() {
    val s = fixture.associate { it.id to ProductListModel.status(it, stockOf(it.id)) }
    assertEquals(Status.ACTIVE, s["a"])
    assertEquals(Status.LOW, s["b"])
    assertEquals(Status.OUT, s["c"])
    assertEquals(Status.LOW, s["d"])     //  موجودی = حدِ هشدار ⇒ کم
    assertEquals(Status.INACTIVE, s["e"]) //  غیرفعال بر «تمام» می‌چربد
    assertEquals(Status.INACTIVE, s["f"])
    //  بی حدِ هشدار، موجودیِ کم «کم‌موجود» نیست (همان DashboardEngine)
    assertEquals(Status.ACTIVE, ProductListModel.status(Product("x", minStock = 0.0), 1.0))
  }

  @Test
  fun `کارت‌های آماری از دادهٔ نمونه`() {
    val k = ProductListModel.kpis(fixture, stockOf, now, periodDays = 7)
    assertEquals(6, k.total)
    assertEquals(4, k.active)
    assertEquals(2, k.low)
    assertEquals(1, k.out)
    assertEquals(2, k.inactive)
    //  a (۲ روز) ، c (۱ روز) ، e (۳ روز)؛ f هشت روز پیش؛ d بی‌تاریخ شمرده نمی‌شود
    assertEquals(3, k.added)
    assertEquals(7, k.periodDays)
  }

  @Test
  fun `کارت‌ها روی دفترِ خالی صفرند و خطا نمی‌دهند`() {
    val k = ProductListModel.kpis(emptyList(), stockOf, now)
    assertEquals(listOf(0, 0, 0, 0, 0, 0), listOf(k.total, k.active, k.low, k.out, k.inactive, k.added))
  }

  @Test
  fun `زبانه‌ها همان شمارِ کارت‌ها را می‌دهند`() {
    val counts = ProductListModel.counts(fixture, stockOf)
    for (tab in Tab.entries) {
      val shown = ProductListModel.rows(fixture, stockOf, "", tab, null, Sort.NAME, true)
      assertEquals("زبانهٔ ${tab.label}", counts[tab], shown.size)
    }
    assertEquals(setOf("a", "b", "c", "d"), ProductListModel.rows(fixture, stockOf, "", Tab.ACTIVE, null, Sort.NAME, true).map { it.id }.toSet())
    assertEquals(setOf("b", "d"), ProductListModel.rows(fixture, stockOf, "", Tab.LOW, null, Sort.NAME, true).map { it.id }.toSet())
    assertEquals(listOf("c"), ProductListModel.rows(fixture, stockOf, "", Tab.OUT, null, Sort.NAME, true).map { it.id })
    assertEquals(setOf("e", "f"), ProductListModel.rows(fixture, stockOf, "", Tab.INACTIVE, null, Sort.NAME, true).map { it.id }.toSet())
  }

  @Test
  fun `جست‌وجو، دسته و مرتب‌سازی با هم`() {
    //  بارکد با ارقامِ فارسی
    assertEquals(listOf("b"), ProductListModel.rows(fixture, stockOf, "۲۲۲", Tab.ALL, null, Sort.NAME, true).map { it.id })
    assertEquals(listOf("a"), ProductListModel.rows(fixture, stockOf, "نوشیدنی", Tab.ALL, null, Sort.NAME, true).map { it.id })
    //  دستهٔ «خوراک» ∩ فعال
    assertEquals(setOf("b", "d"), ProductListModel.rows(fixture, stockOf, "", Tab.ACTIVE, "خوراک", Sort.NAME, true).map { it.id }.toSet())
    //  «بی‌دسته»
    assertEquals(listOf("f"), ProductListModel.rows(fixture, stockOf, "", Tab.ALL, "", Sort.NAME, true).map { it.id })
    //  مرتب به قیمتِ فروش، نزولی
    assertEquals(listOf("d", "a", "b", "c"), ProductListModel.rows(fixture, stockOf, "", Tab.ACTIVE, null, Sort.SELL, false).map { it.id })
    //  مرتب به موجودی
    assertEquals(listOf("c", "d", "b", "a"), ProductListModel.rows(fixture, stockOf, "", Tab.ACTIVE, null, Sort.STOCK, true).map { it.id })
    //  دسته‌ها: «بی‌دسته» آخر
    assertEquals(listOf("بهداشتی", "خوراک", "نوشیدنی", ""), ProductListModel.categories(fixture))
  }

  @Test
  fun `پیش‌نمایش و نتیجهٔ فعال‌کردن — ردیفِ بی‌تغییر جدا گفته می‌شود`() {
    val preview = ProductListModel.previewActive(fixture, setOf("a", "e", "f"), active = true)
    assertEquals(3, preview.size)
    assertEquals(setOf("e", "f"), preview.filter { it.changes }.map { it.id }.toSet())
    assertFalse(preview.first { it.id == "a" }.changes)
    val ok = ProductListModel.resultActive(preview, true, error = null)
    assertTrue(ok.all { it.ok })
    assertEquals("فعال شد", ok.first { it.id == "e" }.note)
    assertTrue(ok.first { it.id == "a" }.note.startsWith("بی‌تغییر"))
    //  شکستِ کلِ کار ⇒ همهٔ ردیف‌ها ناموفق، با همان پیام
    val failed = ProductListModel.resultActive(preview, true, error = "چیزی عوض نشد")
    assertTrue(failed.none { it.ok })
    assertTrue(failed.all { it.note == "چیزی عوض نشد" })
  }

  @Test
  fun `پیش‌نمایشِ حذف فروشِ قبلی را هشدار می‌دهد و فقط انتخاب‌شده‌ها را می‌گیرد`() {
    val sold = mapOf("a" to 3.0)
    val preview = ProductListModel.previewDelete(fixture, setOf("a", "c"), { sold[it] ?: 0.0 })
    assertEquals(listOf("a", "c"), preview.map { it.id })
    assertTrue(preview[0].note.contains("فاکتورهای قبلی"))
    assertFalse(preview[1].note.contains("فاکتورهای قبلی"))
    assertEquals(listOf(true, true), ProductListModel.resultDelete(preview, null).map { it.ok })
    assertEquals(listOf(false, false), ProductListModel.resultDelete(preview, "کالا پیدا نشد").map { it.ok })
  }

  @Test
  fun `دلیلِ حذف فقط کنارِ ردیف‌های همین حذف می‌نشیند`() {
    val d = ShopData(
      auditLog = listOf(
        AuditEntry("1", type = "delete_product", refId = "a", notes = "حذف محصول «چای سبز»", createdAt = now),
        AuditEntry("2", type = "delete_product", refId = "z", notes = "حذف قبلی", createdAt = now - 5),
        AuditEntry("3", type = "product_edit", refId = "a", notes = "ویرایش", createdAt = now),
      ),
    )
    val out = ProductListModel.withDeleteReason(d, setOf("a"), now, "  تاریخ گذشته  ")
    assertEquals("حذف محصول «چای سبز» — دلیل: تاریخ گذشته", out.auditLog[0].notes)
    assertEquals("حذف قبلی", out.auditLog[1].notes)
    assertEquals("ویرایش", out.auditLog[2].notes)
    assertEquals(d, ProductListModel.withDeleteReason(d, setOf("a"), now, "   "))
  }
}
