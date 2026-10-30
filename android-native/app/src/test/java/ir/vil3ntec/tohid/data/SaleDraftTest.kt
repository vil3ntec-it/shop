package ir.vil3ntec.tohid.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 *  POS-13 (پیش‌نویسِ فاکتور پس از بسته شدن/قطعِ برق)، POS-14 (کلیدِ یکتای
 *  فاکتور و قفلِ «ثبتِ فروش»)، PRD-18 (کالای غیرفعال فروخته نمی‌شود).
 */
class SaleDraftTest {

  private var seq = 0
  private fun newId(): String = "g${seq++}"

  private val rice = Product(id = "p1", name = "برنج", unit = "کیلو", purchasePrice = 200.0, salePrice = 300.0)
  private val oil = Product(id = "p2", name = "روغن", purchasePrice = 100.0, salePrice = 150.0, active = false)

  private fun ledger() = ShopData(
    products = listOf(rice, oil),
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    warehouseEntries = listOf(
      WarehouseEntry(id = "w1", productId = "p1", units = 100.0),
      WarehouseEntry(id = "w2", productId = "p2", units = 100.0),
    ),
  )

  private fun sell(d: ShopData, key: String? = null) =
    SalesEngine.record(d, listOf(SalesEngine.CartLine("p1", 2.0)), SalesEngine.Checkout(), "2026-10-14", 1_700_000_000_000, ::newId, saleId = key)

  /* ---------------- مرجع: رفتارِ امروزِ `record` بی کلید ---------------- */

  @Test
  fun `مرجع — بی کلید، هر ثبت فروشِ تازه با شناسهٔ تازه است (رفتارِ پیشین دست نخورده)`() {
    val first = sell(ledger()) as SalesEngine.Result.Ok
    val second = sell(first.data) as SalesEngine.Result.Ok
    assertEquals(2, second.data.sales.size)
    assertNotEquals(first.saleId, second.saleId)
  }

  /* ---------------- POS-14: کلیدِ یکتای فاکتور ---------------- */

  @Test
  fun `فروش شناسهٔ همان کلیدِ پیش‌نویس را می‌گیرد`() {
    val r = sell(ledger(), key = "01KEYSALE") as SalesEngine.Result.Ok
    assertEquals("01KEYSALE", r.saleId)
    assertEquals("01KEYSALE", r.data.sales.single().id)
    assertTrue(r.data.saleItems.all { it.saleId == "01KEYSALE" })
  }

  @Test
  fun `همان کلید دو بار ثبت نمی‌شود — دفتر دست نمی‌خورد`() {
    val once = (sell(ledger(), key = "K1") as SalesEngine.Result.Ok).data
    val again = sell(once, key = "K1")
    assertTrue(again is SalesEngine.Result.Failed)
    assertEquals(SalesEngine.ALREADY_POSTED, (again as SalesEngine.Result.Failed).message)
    assertEquals(1, once.sales.size)
  }

  /* ---------------- PRD-18 ---------------- */

  @Test
  fun `کالای غیرفعال فروختنی نیست؛ نبودِ فیلد یعنی فعال`() {
    assertTrue(SaleDraftRules.sellable(rice))
    assertFalse(SaleDraftRules.sellable(oil))
    assertFalse(SaleDraftRules.sellable(null))
    val json = Json { ignoreUnknownKeys = true }
    val old = json.decodeFromString(Product.serializer(), """{"id":"x","name":"قدیمی"}""")
    assertTrue("دفترِ قدیمی بی `active` باید فعال خوانده شود", old.active)
    val off = json.decodeFromString(Product.serializer(), """{"id":"x","name":"خاموش","active":false}""")
    assertFalse(off.active)
  }

  @Test
  fun `ثبتِ فروشِ کالای غیرفعال رد می‌شود`() {
    val r = SalesEngine.record(ledger(), listOf(SalesEngine.CartLine("p2", 1.0)), SalesEngine.Checkout(), "2026-10-14", 0, ::newId)
    assertTrue(r is SalesEngine.Result.Failed)
  }

  /* ---------------- POS-13: شکلِ پیش‌نویس ---------------- */

  @Test
  fun `پیش‌نویس رفت‌وبرگشت می‌کند`() {
    val cart = listOf(SalesEngine.CartLine("p1", 1.5, unitPrice = 280.0), SalesEngine.CartLine("f1", 1.0, 50.0, "کیسه"))
    val d = SaleDraft.of("K", cart, "d1", 42)
    val back = SaleDraftCodec.decode(SaleDraftCodec.encode(d))!!
    assertEquals(d, back)
    assertEquals(cart, back.cart())
  }

  @Test
  fun `N3 — کدهای تخفیف با پیش‌نویس می‌مانند؛ پیش‌نویسِ پیشین بی کد خوانده می‌شود`() {
    val cart = listOf(SalesEngine.CartLine("p1", 1.0))
    val d = SaleDraft.of("K", cart, null, 42, coupons = listOf("EID5", "VIP10"))
    val back = SaleDraftCodec.decode(SaleDraftCodec.encode(d))!!
    assertEquals(listOf("EID5", "VIP10"), back.coupons)
    assertEquals(listOf("EID5", "VIP10"), (SaleDraftRules.decide(back, ledger()) as DraftRestore.Offer).draft.coupons)
    //  کد بی سبد معنا ندارد
    assertEquals(emptyList<String>(), SaleDraft.of("K", emptyList(), null, 42, coupons = listOf("EID5")).coupons)
    //  پیش‌نویسِ ساختِ پیشین (بی `coupons`)
    val old = SaleDraftCodec.decode("""{"v":2,"key":"K","lines":[{"productId":"p1","quantity":1.0}],"debtorId":null,"savedAt":42}""")!!
    assertEquals(emptyList<String>(), old.coupons)
    assertEquals(cart, old.cart())
  }

  @Test
  fun `پیش‌نویسِ قدیمی (آرایهٔ وب و برنامهٔ پیشین) خوانده می‌شود و کلید ندارد`() {
    val legacy = SaleDraftCodec.decode("""[{"productId":"p1","quantity":2},{"productId":"p2","quantity":1,"extra":true}]""")!!
    assertNull(legacy.key)
    assertEquals(listOf("p1", "p2"), legacy.lines.map { it.productId })
    assertEquals(2.0, legacy.lines[0].quantity, 0.0)
  }

  @Test
  fun `پیش‌نویسِ خراب یا خالی خطا نمی‌دهد`() {
    assertNull(SaleDraftCodec.decode(null))
    assertNull(SaleDraftCodec.decode(""))
    assertNull(SaleDraftCodec.decode("{\"v\":2,\"lines\":[{\"productId\":"))
    assertNull(SaleDraftCodec.decode("42"))
  }

  /* ---------------- POS-13: تصمیمِ بازیابی ---------------- */

  @Test
  fun `بی پیش‌نویس یا سبدِ خالی ⇒ چیزی پرسیده نمی‌شود`() {
    assertEquals(DraftRestore.None, SaleDraftRules.decide(null, ledger()))
    assertEquals(DraftRestore.None, SaleDraftRules.decide(SaleDraft(key = "K"), ledger()))
  }

  @Test
  fun `پیش‌نویسی که فروشش ثبت شده دوباره پیشنهاد نمی‌شود — برق میانِ دفتر و پاک‌کردن رفته بود`() {
    val posted = (sell(ledger(), key = "K9") as SalesEngine.Result.Ok).data
    val draft = SaleDraft.of("K9", listOf(SalesEngine.CartLine("p1", 2.0)), null, 1)
    assertEquals(DraftRestore.Posted("K9"), SaleDraftRules.decide(draft, posted))
  }

  @Test
  fun `بازیابی کالای حذف‌شده و غیرفعال را کنار می‌گذارد و قلمِ آزاد را نگه می‌دارد`() {
    val draft = SaleDraft.of(
      "K", listOf(
        SalesEngine.CartLine("p1", 2.0),
        SalesEngine.CartLine("p2", 1.0),          // غیرفعال
        SalesEngine.CartLine("gone", 1.0),        // حذف‌شده
        SalesEngine.CartLine("f1", 1.0, 50.0, "کیسه"),
      ), "missing-debtor", 1,
    )
    val r = SaleDraftRules.decide(draft, ledger()) as DraftRestore.Offer
    assertEquals(listOf("p1", "f1"), r.draft.lines.map { it.productId })
    assertEquals(2, r.dropped)
    assertEquals("K", r.draft.key)
    assertNull("قرض‌دارِ حذف‌شده نباید برگردد", r.draft.debtorId)
  }

  @Test
  fun `اگر هیچ ردیفی فروختنی نماند چیزی پیشنهاد نمی‌شود`() {
    val draft = SaleDraft.of("K", listOf(SalesEngine.CartLine("p2", 1.0)), null, 1)
    assertEquals(DraftRestore.None, SaleDraftRules.decide(draft, ledger()))
  }

  /* ---------------- POS-14: قفلِ «ثبتِ فروش» ---------------- */

  @Test
  fun `ثبتِ موفق — پیش‌نویس فقط پس از نوشتنِ دفتر پاک می‌شود`() = runBlocking {
    val post = SalePost()
    val order = mutableListOf<String>()
    val out = post.run("K", { key -> sell(ledger(), key) }, { order += "save"; true }) { order += "done:$it" }
    assertTrue(out is SalePost.Outcome.Done)
    assertEquals("K", (out as SalePost.Outcome.Done).ok.saleId)
    assertEquals(listOf("save", "done:K"), order)
    assertFalse(post.busy.value)
  }

  @Test
  fun `دفتر نوشته نشد ⇒ سبد سرِ جایش می‌ماند`() = runBlocking {
    var cleared = false
    val out = SalePost().run("K", { key -> sell(ledger(), key) }, { false }) { cleared = true }
    assertEquals(SalePost.Outcome.NotSaved, out)
    assertFalse(cleared)
  }

  @Test
  fun `ثبتِ ردشده نه می‌نویسد نه پاک می‌کند`() = runBlocking {
    var saved = false
    val out = SalePost().run("K", { SalesEngine.Result.Failed("نه") }, { saved = true; true }) { }
    assertEquals(SalePost.Outcome.Refused("نه"), out)
    assertFalse(saved)
  }

  @Test
  fun `ضربهٔ دوم در میانهٔ ثبت نادیده گرفته می‌شود — یک فروش، نه دو`() = runBlocking {
    val post = SalePost()
    val gate = CompletableDeferred<Unit>()
    var saves = 0
    val first = async { post.run("K", { key -> sell(ledger(), key) }, { saves++; gate.await(); true }) { } }
    while (!post.busy.value) yield()
    //  پنج ضربهٔ دیگر روی Enter/F9 پیش از پایانِ ثبت
    repeat(5) {
      assertEquals(SalePost.Outcome.Busy, post.run("K", { key -> sell(ledger(), key) }, { saves++; true }) { })
    }
    gate.complete(Unit)
    assertTrue(first.await() is SalePost.Outcome.Done)
    assertEquals(1, saves)
    assertFalse("پس از پایان، دکمه دوباره باز است", post.busy.value)
  }
}
