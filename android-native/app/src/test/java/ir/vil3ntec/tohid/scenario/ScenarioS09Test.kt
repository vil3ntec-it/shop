package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.HeldExtras
import ir.vil3ntec.tohid.data.HeldInvoiceStore
import ir.vil3ntec.tohid.data.HeldInvoicesTest
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  S09 ⌨ — نگه‌داشتن و بازیابیِ فاکتور (POS-12) روی همان راهِ صندوق (`HeldInvoiceStore` با دیسک و سبدِ آزمونِ
 *  `HeldInvoicesTest`). در برنامه پشتِ تنظیمِ خاموشِ «چند فاکتورِ هم‌زمان».
 *
 *  ماندهٔ سناریو (pending، نه سرخ):
 *   • بازیابی روی **دستگاهِ دیگر** نیست: فاکتورهای نگه‌داشته فقط در فایلِ همین دستگاه‌اند (جدول و op ِ همگام‌سازی ندارند)،
 *     پس Shift+F6 روی دستگاه ۲ «کریمی» را نمی‌بیند.
 *   • قفلِ «در دستِ دستگاه ۲» روی دستگاه ۱ نیست (همان نبودِ همگام‌سازی).
 *  آنچه هست و این‌جا سبز است: نگه‌داشتن با نام، فروشِ دیگر در همان صندوق، بازگشتِ فاکتور با همهٔ ردیف‌ها و تخفیف،
 *  و فهرستِ کهنه‌تر از ۲۴ ساعت برای مدیر.
 */
class ScenarioS09Test {

  private var now = 1_800_000_000_000L
  private val products = (1..6).map { Product(id = "p$it", name = "کالای $it", salePrice = 100.0 * it) }
  private val d = ShopData(
    products = products,
    warehouseEntries = products.map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = 50.0) },
  )

  private fun done(o: HeldInvoiceStore.Outcome) = o as? HeldInvoiceStore.Outcome.Done ?: error("رد شد: $o")

  @Test
  fun `S09 — فاکتورِ ۵ قلمی با نامِ «آقای کریمی» نگه داشته و پس از فروشِ دیگر با همهٔ ردیف‌ها و تخفیف برمی‌گردد`() = runBlocking<Unit> {
    val cart = HeldInvoicesTest.MemCart()
    val device1 = HeldInvoiceStore(HeldInvoicesTest.MemDisk(), device = { "دستگاه-۱" }, clock = { now })
    cart.put("K1", "p1" to 1.0, "p2" to 2.0, "p3" to 3.0, "p4" to 1.0, "p5" to 4.0)
    //  F6 و نام
    done(device1.hold(cart, HeldExtras(discount = "50", discountAmount = true), label = "آقای کریمی"))
    assertTrue(cart.snapshot().lines.isEmpty())
    assertEquals("آقای کریمی", device1.book.value.items.single().label)

    //  یک فروشِ دیگر کامل می‌شود
    cart.put("K2", "p6" to 1.0)
    assertTrue(cart.post())

    //  Shift+F6 و «کریمی»
    val back = done(device1.resume(cart, device1.book.value.items.single { it.label.contains("کریمی") }.id, HeldExtras(), d))
    assertEquals("K1", cart.snapshot().key)
    assertEquals(
      listOf("p1" to 1.0, "p2" to 2.0, "p3" to 3.0, "p4" to 1.0, "p5" to 4.0),
      cart.snapshot().lines.map { it.productId to it.quantity },
    )
    assertEquals(HeldExtras(discount = "50", discountAmount = true), back.extras)
    assertEquals(listOf("K2"), cart.sold)
  }

  @Test
  fun `S09 — فاکتورهای نگه‌داشتهٔ کهنه‌تر از ۲۴ ساعت به مدیر نشان داده می‌شوند`() = runBlocking<Unit> {
    val cart = HeldInvoicesTest.MemCart()
    val s = HeldInvoiceStore(HeldInvoicesTest.MemDisk(), device = { "دستگاه-۱" }, clock = { now })
    cart.put("OLD", "p1" to 1.0); done(s.hold(cart, HeldExtras(), label = "کریمی"))
    now += (HeldRules.STALE_HOURS + 1) * 3_600_000L
    cart.put("NEW", "p2" to 1.0); done(s.hold(cart, HeldExtras(), label = "رحیمی"))
    assertEquals(listOf("OLD"), HeldRules.stale(s.book.value, now).map { it.id })
  }
}
