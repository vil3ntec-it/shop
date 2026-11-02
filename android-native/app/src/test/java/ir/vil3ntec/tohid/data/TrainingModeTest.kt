package ir.vil3ntec.tohid.data

import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  OPS-07 — منطقِ خالصِ حالتِ آموزشی: کلید، مسیرِ دفتر، مهرِ رسید، اجازهٔ نقش و دفترِ نمونهٔ قطعی.
 *  دیسک و دفترِ واقعی در `desktop/…/TrainingLedgerTest`.
 */
class TrainingModeTest {

  @After fun off() = TrainingMode.turn(false)

  @Test fun `خاموش یعنی همان امروز — دفترِ واقعی نوشتنی، شبکه باز، مهرِ رسید دست‌نخورده`() {
    assertFalse(TrainingMode.on)
    assertTrue(TrainingMode.writable(training = false))
    assertTrue(TrainingMode.networkAllowed())
    assertNull(TrainingMode.watermark(null))
    assertEquals("المثنی", TrainingMode.watermark("المثنی"))
    assertEquals("shop-data.json", TrainingMode.ledgerPath(training = false))
  }

  @Test fun `روشن یعنی دفترِ واقعی بسته و شبکه بسته ولی دفترِ آموزشی نوشتنی`() {
    TrainingMode.turn(true)
    assertFalse("دفترِ واقعی در آموزش نوشته نمی‌شود", TrainingMode.writable(training = false))
    assertTrue("دفترِ آموزشی همیشه نوشتنی است", TrainingMode.writable(training = true))
    assertFalse(TrainingMode.networkAllowed())
  }

  @Test fun `دفترِ آموزشی مسیرِ جدا دارد و هرگز همان فایلِ واقعی نیست`() {
    assertEquals("training/shop-data.json", TrainingMode.ledgerPath(training = true))
    assertTrue(TrainingMode.ledgerPath(true) != TrainingMode.ledgerPath(false))
  }

  @Test fun `هر رسیدِ آموزشی مهرِ آموزشی دارد و مهرِ پیشین کنارش می‌ماند`() {
    TrainingMode.turn(true)
    assertEquals("آموزشی", TrainingMode.watermark(null))
    assertEquals("آموزشی", TrainingMode.watermark("  "))
    assertEquals("آموزشی · المثنی", TrainingMode.watermark("المثنی"))
  }

  @Test fun `صاحب و مدیر همیشه وارد می‌شوند و کارمند فقط با اجازه و پیک هرگز`() {
    for (role in listOf("", ShopRole.OWNER, ShopRole.MANAGER)) {
      assertTrue(role, TrainingMode.canEnter(role, staffAllowed = false))
      assertTrue(role, TrainingMode.canManage(role))
    }
    for (role in listOf(ShopRole.CASHIER, ShopRole.STAFF, ShopRole.STOREKEEPER, ShopRole.ACCOUNTANT, ShopRole.PICKER)) {
      assertFalse(role, TrainingMode.canEnter(role, staffAllowed = false))
      assertTrue(role, TrainingMode.canEnter(role, staffAllowed = true))
      assertFalse(role, TrainingMode.canManage(role))
    }
    assertFalse(TrainingMode.canEnter(ShopRole.COURIER, staffAllowed = true))
  }

  private val json = Json { encodeDefaults = true }

  @Test fun `دفترِ نمونه قطعی است — همان ورودی همان بایت‌ها`() {
    val a = json.encodeToString(TrainingSample.build("2026-11-02", 1_793_000_000_000L))
    val b = json.encodeToString(TrainingSample.build("2026-11-02", 1_793_000_000_000L))
    assertEquals(a, b)
  }

  @Test fun `دفترِ نمونه کالا و مشتری و فروشِ سازگار دارد و شناسه‌هایش هرگز شناسهٔ واقعی نیست`() {
    val d = TrainingSample.build("2026-11-02", 1_793_000_000_000L)
    assertEquals(8, d.products.size)
    assertEquals(3, d.debtors.size)
    assertEquals(3, d.sales.size)
    assertEquals(d.sales.size + 1, d.nextInvoiceNo)
    assertEquals(listOf(1, 2, 3), d.sales.map { it.invoiceNumber })
    val ids = d.products.map { it.id } + d.debtors.map { it.id } + d.sales.map { it.id } + d.saleItems.map { it.id } +
      d.warehouseEntries.map { it.id }
    assertTrue(ids.all { it.startsWith(TrainingSample.PREFIX) })
    assertEquals(ids.size, ids.toSet().size)
    for (s in d.sales) {
      val lines = d.saleItems.filter { it.saleId == s.id }
      assertTrue(lines.isNotEmpty())
      assertEquals(lines.sumOf { it.totalPrice }, s.finalTotal, 0.0001)
      assertEquals(s.finalTotal, s.paidAmount, 0.0001)
      assertEquals("2026-11-02", s.date)
    }
    //  هر قلمِ فروخته موجودیِ کافی داشت
    for (p in d.products) {
      val sold = d.saleItems.filter { it.productId == p.id }.sumOf { it.quantity }
      val came = d.warehouseEntries.filter { it.productId == p.id }.sumOf { it.units }
      assertTrue(p.name, came >= sold)
    }
  }
}
