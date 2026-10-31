package ir.vil3ntec.tohid.data

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.IOException
import java.nio.file.Files

/**
 *  POS-12 — نگه‌داشتنِ فاکتور و چند فاکتورِ هم‌زمان روی یک دستگاه.
 *
 *  جابه‌جایی وضعیت را نگه می‌دارد، پس از بسته شدن برمی‌گردد، سقف دارد،
 *  پایانِ فروش فاکتورش را می‌برد، دور انداختن فقط همان یکی را، و قطعِ برق
 *  در هر لحظهٔ جابه‌جایی هیچ فاکتوری را گم نمی‌کند.
 */
class HeldInvoicesTest {

  /* ---------------- دیسک و سبدِ آزمون: «برق» پس از n نوشتن می‌رود ---------------- */

  /** شمارندهٔ مشترکِ نوشتن‌ها میانِ دو فایل؛ پس از `cut` نوشتن‌ها به دیسک نمی‌رسند (برق رفته) */
  class Power(var cut: Int = Int.MAX_VALUE) {
    var writes = 0
    fun lands(): Boolean = writes++ < cut
  }

  class MemDisk(val power: Power = Power(), var text: String? = null) : HeldDisk {
    var fail = false
    /** پیش از هر نوشتن — «کاربر» وسطِ جابه‌جایی کاری می‌کند */
    var onWrite: (() -> Unit)? = null
    override fun read(): String? = text
    override fun write(text: String) {
      onWrite?.invoke()
      if (fail) throw IOException("دیسک پر است")
      if (power.lands()) this.text = text
    }
  }

  /** همان قراردادِ `CartStore`: حافظه همان لحظه، دیسک با `replace` */
  class MemCart(val power: Power = Power(), var disk: SaleDraft? = null) : HeldCart {
    var mem: SaleDraft? = disk
    var fail = false
    override var busy = false
    override var recovering = false
    /** همان قفلِ `CartStore`: `scan`/`post` وسطِ جابه‌جایی رد می‌شوند */
    var frozen = false
    val sold = mutableListOf<String>()
    /** پیش از هر `replace` (می‌تواند معلق بماند) */
    var onReplace: (suspend () -> Unit)? = null
    override fun freeze(on: Boolean) { frozen = on }
    override fun snapshot(): SaleDraft = mem ?: SaleDraft()
    override suspend fun replace(draft: SaleDraft?): Boolean {
      onReplace?.invoke()
      mem = draft?.takeIf { it.lines.isNotEmpty() }
      if (fail) return false
      if (power.lands()) disk = mem
      return true
    }
    /** افزودن در صندوق — مثلِ `CartStore.set`: نخستین قلم کلید می‌سازد */
    fun put(key: String, vararg lines: Pair<String, Double>, debtor: String? = null) {
      mem = SaleDraft(key = key, lines = lines.map { SaleDraft.Line(it.first, it.second) }, debtorId = debtor)
      disk = mem
    }
    /** اسکن — مثلِ `CartStore.set`؛ قفل ⇒ رد */
    fun scan(productId: String): Boolean {
      if (frozen) return false
      val now = mem ?: SaleDraft(key = "NEW")
      mem = now.copy(lines = now.lines + SaleDraft.Line(productId, 1.0)); disk = mem
      return true
    }
    /** F12 — مثلِ `CartStore.post`؛ قفل ⇒ رد */
    fun post(): Boolean {
      val key = mem?.key
      if (frozen || key == null) return false
      sold += key; mem = null; disk = null
      return true
    }
  }

  private var now = 1_700_000_000_000L
  private fun store(disk: HeldDisk) = HeldInvoiceStore(disk, device = { "A" }, clock = { now })

  private val rice = Product(id = "p1", name = "برنج", salePrice = 300.0)
  private val oil = Product(id = "p2", name = "روغن", salePrice = 150.0)
  private val tea = Product(id = "p3", name = "چای", salePrice = 90.0)
  private fun shop() = ShopData(
    products = listOf(rice, oil, tea),
    debtors = listOf(Debtor(id = "d1", name = "کریمی")),
    warehouseEntries = listOf("p1", "p2", "p3").map { WarehouseEntry(id = "w$it", productId = it, units = 100.0) },
  )

  private fun keyOf(c: MemCart) = c.snapshot().key
  private fun done(o: HeldInvoiceStore.Outcome) = o as? HeldInvoiceStore.Outcome.Done ?: error("رد شد: $o")
  private fun refused(o: HeldInvoiceStore.Outcome) = (o as? HeldInvoiceStore.Outcome.Refused ?: error("باید رد می‌شد: $o")).message

  /* ---------------- نگه‌داشتن و بازگرداندن ---------------- */

  @Test
  fun `نگه‌داشتن — صندوق خالی می‌شود و فاکتور با همهٔ چیزش می‌ماند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("K1", "p1" to 2.0, "p2" to 1.0, debtor = "d1")
    val o = done(s.hold(cart, HeldExtras(discount = "10"), label = "  آقای   کریمی ", note = "پولش را می‌آورد"))
    assertTrue(o.message, o.message.contains("کریمی"))
    assertEquals(HeldExtras(), o.extras)
    assertTrue("صندوق خالی", cart.snapshot().lines.isEmpty())
    val h = s.book.value.items.single()
    assertEquals("K1", h.id)
    assertEquals(1, h.number)
    assertEquals("آقای کریمی", h.label)
    assertEquals("پولش را می‌آورد", h.note)
    assertEquals("10", h.discount)
    assertEquals("d1", h.debtorId)
    assertEquals(listOf("p1" to 2.0, "p2" to 1.0), h.lines.map { it.productId to it.quantity })
    assertEquals(now, h.heldAt)
    assertEquals("A", h.device)
  }

  @Test
  fun `N3 — کدهای تخفیف با فاکتورِ نگه‌داشته می‌روند و با همان برمی‌گردند، نه با فاکتورِ دیگر`() {
    val a = SaleDraft(key = "A", lines = listOf(SaleDraft.Line("p1", 1.0)), coupons = listOf("EID5"))
    val held = HeldRules.hold(HeldBook(), a, HeldExtras(), "الف", null, now, "A") as HeldRules.Result.Ok
    assertEquals(listOf("EID5"), held.book.items.single().coupons)
    //  فاکتورِ «ب» با کدِ خودش جاری است؛ «الف» برمی‌گردد و «ب» با کدش نگه داشته می‌شود
    val b = SaleDraft(key = "B", lines = listOf(SaleDraft.Line("p2", 1.0)), coupons = listOf("VIP10"))
    val back = HeldRules.resume(held.book, "A", b, HeldExtras(), now, "A") as HeldRules.Result.Ok
    assertEquals(listOf("EID5"), back.cart!!.coupons)
    assertEquals(listOf("VIP10"), back.book.items.single { it.id == "B" }.coupons)
    //  نگه‌داشتهٔ نسخهٔ پیش (بی `coupons`) بی کد برمی‌گردد
    val old = HeldInvoiceCodec.decode("""{"items":[{"id":"O","number":3,"lines":[{"productId":"p3","quantity":1}]}]}""")!!
    assertEquals(emptyList<String>(), old.items.single().draft().coupons)
  }

  @Test
  fun `سبدِ خالی نگه داشته نمی‌شود`() = runBlocking<Unit> {
    val s = store(MemDisk())
    assertEquals(HeldRules.EMPTY, refused(s.hold(MemCart(), HeldExtras())))
    assertTrue(s.book.value.items.isEmpty())
  }

  @Test
  fun `جابه‌جایی وضعیتِ هر فاکتور را نگه می‌دارد`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    //  «الف» نگه داشته شد، «ب» در صندوق
    cart.put("A", "p1" to 2.0, debtor = "d1")
    done(s.hold(cart, HeldExtras("5"), "الف", "منتظر"))
    cart.put("B", "p2" to 3.0)
    //  بازگشت به «الف» ⇒ «ب» خودکار نگه داشته می‌شود، با تخفیفِ خودش
    val back = done(s.resume(cart, "A", HeldExtras("7"), shop()))
    assertEquals(HeldExtras("5"), back.extras)
    assertEquals("A", keyOf(cart))
    assertEquals(listOf("p1" to 2.0), cart.snapshot().lines.map { it.productId to it.quantity })
    assertEquals("d1", cart.snapshot().debtorId)
    val b = s.book.value.items.single()
    assertEquals("B", b.id)
    assertEquals("7", b.discount)
    assertEquals(listOf("p2" to 3.0), b.lines.map { it.productId to it.quantity })
    //  و دوباره «ب» ⇒ «الف» با همان شماره، نام و یادداشت برمی‌گردد به فهرست
    val again = done(s.resume(cart, "B", HeldExtras("9"), shop()))
    assertEquals(HeldExtras("7"), again.extras)
    val a = s.book.value.items.single()
    assertEquals("A", a.id)
    assertEquals(1, a.number)
    assertEquals("الف", a.label)
    assertEquals("منتظر", a.note)
    assertEquals("9", a.discount)
  }

  @Test
  fun `شماره تا فاکتور باز است ثابت می‌ماند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))            // #1
    cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras()))            // #2
    cart.put("C", "p3" to 1.0)
    assertEquals(3, HeldRules.activeNumber(s.book.value, cart.snapshot()))   // برگهٔ «جاری» #3 است
    done(s.resume(cart, "A", HeldExtras()))
    assertEquals(mapOf("B" to 2, "C" to 3), s.book.value.items.associate { it.id to it.number })
    assertEquals(1, HeldRules.activeNumber(s.book.value, cart.snapshot()))
    done(s.resume(cart, "C", HeldExtras()))
    assertEquals(mapOf("A" to 1, "B" to 2), s.book.value.items.associate { it.id to it.number })
    assertEquals(3, HeldRules.activeNumber(s.book.value, cart.snapshot()))
    //  شمارهٔ آزادشده دوباره به کار می‌رود
    done(s.discard("A"))
    done(s.hold(cart, HeldExtras()))
    cart.put("D", "p1" to 1.0)
    assertEquals(1, HeldRules.activeNumber(s.book.value, cart.snapshot()))
  }

  @Test
  fun `بعدی و قبلی به ترتیبِ شماره و دوری`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("C", "p3" to 1.0)                                               // #3 جاری
    done(s.step(cart, +1, HeldExtras()))
    assertEquals("A", keyOf(cart))                                           // پس از #3 ⇒ #1
    done(s.step(cart, +1, HeldExtras()))
    assertEquals("B", keyOf(cart))
    done(s.step(cart, -1, HeldExtras()))
    assertEquals("A", keyOf(cart))
    done(s.step(cart, -1, HeldExtras()))
    assertEquals("C", keyOf(cart))                                           // پیش از #1 ⇒ #3
    assertEquals(setOf("A", "B"), s.book.value.items.map { it.id }.toSet())
    //  نگه‌داشته‌ای نیست ⇒ رد، صندوق دست نمی‌خورد
    val lone = store(MemDisk()); val c2 = MemCart(); c2.put("Z", "p1" to 1.0)
    refused(lone.step(c2, +1, HeldExtras()))
    assertEquals("Z", keyOf(c2))
  }

  /* ---------------- سقف ---------------- */

  @Test
  fun `حداکثر نه فاکتورِ باز با پیامِ روشن`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    for (i in 1 until HeldRules.MAX_OPEN) { cart.put("K$i", "p1" to i.toDouble()); done(s.hold(cart, HeldExtras())) }
    assertEquals((1 until HeldRules.MAX_OPEN).toList(), s.book.value.items.map { it.number })
    cart.put("K9", "p2" to 1.0)
    assertEquals(HeldRules.MAX_OPEN, HeldRules.activeNumber(s.book.value, cart.snapshot()))
    assertEquals(HeldRules.FULL, refused(s.hold(cart, HeldExtras())))
    assertEquals("صندوق دست نخورد", "K9", keyOf(cart))
    assertEquals(HeldRules.MAX_OPEN - 1, s.book.value.items.size)
    //  جابه‌جایی در سقف هم کار می‌کند — یکی می‌رود، یکی می‌آید
    done(s.resume(cart, "K3", HeldExtras()))
    assertEquals("K3", keyOf(cart))
    assertEquals(HeldRules.MAX_OPEN - 1, s.book.value.items.size)
    assertEquals((1..HeldRules.MAX_OPEN).toList() - 3, s.book.value.items.map { it.number })
  }

  /* ---------------- پایانِ فروش، دور انداختن، کهنه‌ها ---------------- */

  @Test
  fun `پایانِ فروش فاکتورش را از فهرست می‌برد`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 2.0); done(s.hold(cart, HeldExtras(), "الف", null))
    cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras(), "ب", null))
    done(s.resume(cart, "A", HeldExtras(), shop()))
    assertEquals("A", s.book.value.current?.id)
    //  همان کاری که `CartStore.post` می‌کند: فروش با کلیدِ فاکتور ثبت و سبد خالی
    val r = SalesEngine.record(shop(), cart.snapshot().cart(), SalesEngine.Checkout(), "2026-10-19", now, { "x" + (now++) }, saleId = "A")
    val ledger = (r as SalesEngine.Result.Ok).data
    cart.replace(null)
    s.prune(ledger)
    assertNull("برچسبِ فاکتورِ فروش‌شده رفت", s.book.value.current)
    assertEquals(listOf("B"), s.book.value.items.map { it.id })
    //  فاکتوری که جای دیگری فروش شد (کلیدش در دفتر) هم از فهرست می‌رود
    s.prune(ledger.copy(sales = ledger.sales + ledger.sales.first().copy(id = "B")))
    assertTrue(s.book.value.items.isEmpty())
  }

  @Test
  fun `دور انداختن فقط همان یکی را می‌برد`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("C", "p3" to 1.0)
    assertTrue(done(s.discard("A")).message.contains("دور انداخته"))
    assertEquals(listOf("B"), s.book.value.items.map { it.id })
    assertEquals("صندوق دست نخورد", "C", keyOf(cart))
    assertEquals(HeldRules.GONE, refused(s.discard("A")))
  }

  @Test
  fun `نام و یادداشت عوض می‌شوند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    done(s.rename("A", " میز ۴ ", "x".repeat(500)))
    val h = s.book.value.items.single()
    assertEquals("میز ۴", h.label)
    assertEquals(HeldRules.NOTE_MAX, h.note.length)
  }

  @Test
  fun `کهنه‌ها دیده و پاک می‌شوند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    now += 25 * 3_600_000L
    cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras()))
    assertEquals(listOf("A"), HeldRules.stale(s.book.value, now).map { it.id })
    assertTrue(done(s.dropStale()).message.startsWith("1 "))
    assertEquals(listOf("B"), s.book.value.items.map { it.id })
    refused(s.dropStale())
  }

  @Test
  fun `کالای غیرفعال هنگامِ بازگرداندن می‌افتد، قلمِ آزاد می‌ماند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.mem = SaleDraft(key = "A", lines = listOf(SaleDraft.Line("p1", 1.0), SaleDraft.Line("p2", 2.0), SaleDraft.Line("free1", 1.0, 50.0, "کیسه")), debtorId = "gone")
    done(s.hold(cart, HeldExtras()))
    val d = shop().copy(products = listOf(rice, oil.copy(active = false)))
    //  فهرست خودش کوتاه نمی‌شود
    s.prune(d)
    assertEquals(3, s.book.value.items.single().lines.size)
    val o = done(s.resume(cart, "A", HeldExtras(), d))
    assertTrue(o.message, o.message.contains("1 ردیف"))
    assertEquals(listOf("p1", "free1"), cart.snapshot().lines.map { it.productId })
    assertNull("مشتریِ پاک‌شده جدا شد", cart.snapshot().debtorId)
  }

  @Test
  fun `فاکتوری که هیچ کالای فروختنی ندارد نگه‌داشته می‌ماند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p9" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("B", "p1" to 1.0)
    assertEquals(HeldRules.NOTHING_LEFT, refused(s.resume(cart, "A", HeldExtras(), shop())))
    assertEquals("B", keyOf(cart))
    assertEquals(listOf("A"), s.book.value.items.map { it.id })
  }

  @Test
  fun `در حینِ ثبتِ فروش جابه‌جایی نمی‌شود`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 1.0)
    cart.busy = true
    assertEquals(HeldInvoiceStore.BUSY, refused(s.hold(cart, HeldExtras())))
    assertEquals("A", keyOf(cart))
  }

  /* ---------------- بستن و باز کردنِ برنامه ---------------- */

  @Test
  fun `پس از بستن و باز کردن همه برمی‌گردند`() = runBlocking<Unit> {
    val dir = Files.createTempDirectory("held").toFile()
    try {
      val file = File(dir, HeldInvoiceStore.FILE)
      val cart = MemCart()
      val s = store(FileHeldDisk(file))
      cart.put("A", "p1" to 2.0, debtor = "d1"); done(s.hold(cart, HeldExtras("10"), "الف", "یادداشت"))
      cart.put("B", "p2" to 1.0); done(s.hold(cart, HeldExtras()))
      cart.put("C", "p3" to 4.0)
      done(s.resume(cart, "A", HeldExtras("3")))
      //  «باز کردنِ دوباره»: نمونهٔ تازه از همان فایل
      val again = store(FileHeldDisk(file))
      //  ساختن چیزی نمی‌خواند (نه هنگامِ کشیدنِ صفحه)؛ نخستین کار می‌خواند
      assertTrue(again.book.value.items.isEmpty())
      again.load()
      assertEquals(s.book.value, again.book.value)
      val c = again.book.value.items.first { it.id == "C" }
      assertEquals("3", c.discount)
      assertEquals(3, c.number)
      assertEquals(HeldTag("A", 1, "الف", "یادداشت"), again.book.value.current)
    } finally { dir.deleteRecursively() }
  }

  @Test
  fun `فایلِ خراب کنار گذاشته می‌شود، پاک نه`() = runBlocking<Unit> {
    val dir = Files.createTempDirectory("held").toFile()
    try {
      val file = File(dir, HeldInvoiceStore.FILE)
      file.writeText("{نیمه")
      val s = store(FileHeldDisk(file))
      s.load()
      assertTrue(s.book.value.items.isEmpty())
      assertTrue("نسخهٔ خراب ماند", dir.listFiles()!!.any { it.name.startsWith("${HeldInvoiceStore.FILE}.bad-") && it.readText() == "{نیمه" })
    } finally { dir.deleteRecursively() }
  }

  @Test
  fun `دیسکِ پر — هیچ چیز جابه‌جا نمی‌شود`() = runBlocking<Unit> {
    val disk = MemDisk(); val cart = MemCart(); val s = store(disk)
    cart.put("A", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("B", "p2" to 1.0)
    disk.fail = true
    assertEquals(HeldInvoiceStore.NOT_SAVED, refused(s.hold(cart, HeldExtras())))
    assertEquals("B", keyOf(cart))
    disk.fail = false
    //  سبد نوشته نشد ⇒ همه سرِ جای خود
    cart.fail = true
    assertEquals(HeldInvoiceStore.NOT_SAVED, refused(s.resume(cart, "A", HeldExtras())))
    assertFalse("قفلِ سبد پس از شکست هم باز می‌شود", cart.frozen)
    assertEquals("B", keyOf(cart))
    assertEquals(listOf("A"), s.book.value.items.map { it.id })
    assertEquals(listOf("A"), HeldInvoiceCodec.decode(disk.text)!!.items.map { it.id })
  }

  @Test
  fun `روشِ پرداخت و دریافتی با فاکتور می‌روند و برمی‌گردند`() = runBlocking<Unit> {
    val cart = MemCart(); val s = store(MemDisk())
    cart.put("A", "p1" to 2.0, debtor = "d1")
    done(s.hold(cart, HeldExtras("5", SalesEngine.Payment.CREDIT, " 200 ")))
    cart.put("B", "p2" to 1.0)
    val back = done(s.resume(cart, "A", HeldExtras("", SalesEngine.Payment.CASH, "")))
    assertEquals(HeldExtras("5", SalesEngine.Payment.CREDIT, "200"), back.extras)
    assertEquals(HeldExtras("", SalesEngine.Payment.CASH, ""), done(s.resume(cart, "B", HeldExtras())).extras)
    //  نگه‌داشتهٔ نسخهٔ پیش (بی روشِ پرداخت) ⇒ `null`: صفحه از مشتری می‌سازد
    val old = HeldInvoiceCodec.decode("""{"items":[{"id":"O","number":3,"lines":[{"productId":"p3","quantity":1}],"debtorId":"d1"}]}""")!!
    assertNull(HeldRules.resume(old, "O", SaleDraft(), HeldExtras(), now, "A").let { (it as HeldRules.Result.Ok).extras.payment })
  }

  /**
   *  POS-20 — تخفیفِ کلِ فاکتور به مبلغ با فاکتور می‌رود و برمی‌گردد. بی آن «۵۰۰» ِ مبلغ پس از
   *  جابه‌جایی درصد خوانده و ۱۰۰٪ می‌شد. درصدی (و نگه‌داشتهٔ نسخهٔ پیش) بایت‌به‌بایت همان امروز.
   */
  @Test
  fun `تخفیفِ مبلغیِ فاکتور با فاکتور می‌رود و برمی‌گردد و درصدی همان شکلِ امروز است`() = runBlocking<Unit> {
    val disk = MemDisk(); val cart = MemCart(); val s = store(disk)
    cart.put("A", "p1" to 2.0)
    done(s.hold(cart, HeldExtras(" 500 ", discountAmount = true)))
    assertTrue(disk.text!!.contains("\"discountAmount\":true"))
    cart.put("B", "p2" to 1.0)
    done(s.hold(cart, HeldExtras("10")))
    //  درصدی: کلیدِ تازه اصلاً نوشته نمی‌شود — فقط همان یکِ «A»
    assertEquals(1, Regex("discountAmount").findAll(disk.text!!).count())
    assertEquals(HeldInvoiceCodec.encode(HeldBook(items = listOf(HeldInvoice(id = "X", number = 1, discount = "10")))),
      """{"v":1,"items":[{"id":"X","number":1,"label":"","lines":[],"debtorId":null,"discount":"10","note":"","heldAt":0,"device":"","payment":"","paid":"","coupons":[]}],"current":null}""")
    assertEquals(HeldExtras("500", discountAmount = true), done(s.resume(cart, "A", HeldExtras())).extras)
    assertEquals(HeldExtras("10"), done(s.resume(cart, "B", HeldExtras("700", discountAmount = true))).extras)
    //  همان که با «B» کنار رفت: «A» با مبلغش نگه داشته شد
    assertTrue(HeldInvoiceCodec.decode(disk.text)!!.items.single { it.id == "A" }.let { it.discount == "700" && it.discountAmount })
    //  کادرِ خالی واحد ندارد
    cart.put("C", "p1" to 1.0)
    done(s.hold(cart, HeldExtras("  ", discountAmount = true)))
    assertFalse(HeldInvoiceCodec.decode(disk.text)!!.items.single { it.id == "C" }.discountAmount)
    //  نگه‌داشتهٔ نسخهٔ پیش ⇒ درصد
    val old = HeldInvoiceCodec.decode("""{"items":[{"id":"O","number":3,"lines":[{"productId":"p3","quantity":1}],"discount":"5"}]}""")!!
    assertEquals(HeldExtras("5"), (HeldRules.resume(old, "O", SaleDraft(), HeldExtras(), now, "A") as HeldRules.Result.Ok).extras)
  }

  /** نسیهٔ بی‌مشتری نسیه می‌ماند (ثبت خودش مشتری می‌خواهد)؛ نبودِ روش ⇒ مشتری دارد ⇒ نسیه */
  @Test
  fun `روشِ پرداخت پس از جابه‌جایی`() {
    val credit = SalesEngine.Payment.CREDIT; val cash = SalesEngine.Payment.CASH
    assertEquals(credit, HeldRules.paymentAfterSwitch(credit, null))
    assertEquals(credit, HeldRules.paymentAfterSwitch(credit, "d1"))
    assertEquals(cash, HeldRules.paymentAfterSwitch(cash, "d1"))
    assertEquals(credit, HeldRules.paymentAfterSwitch(null, "d1"))
    assertEquals(cash, HeldRules.paymentAfterSwitch(null, null))
  }

  /**
   *  X→Y→X′: `StateFlow` نشانیِ تازهٔ X′ را دارد ولی `collectAsState` (برابر با X) آن را
   *  نمی‌فرستد و صفحه X را نگه می‌دارد — مقایسهٔ نشانی F12 را برای همیشه رد می‌کرد.
   */
  @Test
  fun `سبدِ کشیده‌شده با برابریِ مقدار سنجیده می‌شود`() {
    val x = listOf(SalesEngine.CartLine("p1", 2.0), SalesEngine.CartLine("free", 1.0, 50.0, "کیسه"))
    val y = x + SalesEngine.CartLine("p2", 1.0)
    val x2 = y.dropLast(1).map { it.copy() }
    val flow = kotlinx.coroutines.flow.MutableStateFlow(x)
    flow.value = y; flow.value = x2
    assertTrue("نشانی تازه است", flow.value !== x)
    assertTrue(HeldRules.shownIsCurrent(x, flow.value))
    assertFalse(HeldRules.shownIsCurrent(x, y))
    assertFalse(HeldRules.shownIsCurrent(x, listOf(SalesEngine.CartLine("p1", 3.0), x[1])))
  }

  /** اسکن و F12 که وسطِ جابه‌جایی (هنگامِ هر نوشتن و عوض کردنِ سبد) می‌رسند به هیچ فاکتوری نمی‌رسند */
  @Test
  fun `اسکن و F12 وسطِ جابه‌جایی رد می‌شوند و هیچ فاکتوری خراب نمی‌شود`() = runBlocking<Unit> {
    for (op in listOf("hold", "resume")) {
      val disk = MemDisk(); val cart = MemCart(); val s = store(disk)
      cart.put("T", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
      cart.put("A", "p2" to 2.0)
      val tries = mutableListOf<Boolean>()
      val user = { tries += cart.scan("p3"); tries += cart.post(); Unit }
      disk.onWrite = user
      cart.onReplace = { user() }
      if (op == "hold") done(s.hold(cart, HeldExtras())) else done(s.resume(cart, "T", HeldExtras()))
      disk.onWrite = null; cart.onReplace = null
      assertTrue(op, tries.size >= 4)
      assertTrue("$op: همه رد شدند", tries.none { it })
      assertTrue(op, cart.sold.isEmpty())
      val a = s.book.value.items.single { it.id == "A" }
      assertEquals(op, listOf(SaleDraft.Line("p2", 2.0)), a.lines)
      if (op == "resume") {
        assertEquals(op, "T", keyOf(cart))
        assertEquals(op, listOf(SaleDraft.Line("p1", 1.0)), cart.snapshot().lines)
      } else assertTrue(op, cart.snapshot().lines.isEmpty())
      //  پس از جابه‌جایی قفل باز است
      assertTrue(op, cart.scan("p3")); assertTrue(op, cart.post())
    }
  }

  /** صفحه وسطِ جابه‌جایی بسته شد (رفتن به صفحهٔ دیگر، چرخشِ گوشی): کار تا آخر می‌رود */
  @Test
  fun `لغوِ صفحه وسطِ جابه‌جایی فاکتورِ جاری را گم نمی‌کند`() = runBlocking<Unit> {
    val disk = MemDisk(); val cart = MemCart(); val s = store(disk)
    cart.put("T", "p1" to 1.0); done(s.hold(cart, HeldExtras()))
    cart.put("A", "p2" to 2.0)
    val entered = CompletableDeferred<Unit>(); val gate = CompletableDeferred<Unit>()
    cart.onReplace = { entered.complete(Unit); gate.await() }
    val job = launch { s.resume(cart, "T", HeldExtras()) }
    entered.await(); job.cancel(); gate.complete(Unit); job.join()
    cart.onReplace = null
    assertEquals("T", keyOf(cart))
    assertFalse(cart.frozen)
    assertEquals(listOf("A"), s.book.value.items.map { it.id })
    assertEquals(listOf("A"), HeldInvoiceCodec.decode(disk.text)!!.items.map { it.id })
    //  نوشتنِ بعدی از حافظه «الف» را نگه می‌دارد
    done(s.hold(cart, HeldExtras()))
    assertEquals(listOf("A", "T"), HeldInvoiceCodec.decode(disk.text)!!.items.map { it.id }.sorted())
  }

  /** پرسشِ بازیابیِ پیش‌نویس بی‌پاسخ: «دور انداختن» نباید فاکتوری را ببرد که نگه‌داشته هم هست */
  @Test
  fun `reconcile تا پاسخِ پرسشِ بازیابی صبر می‌کند`() = runBlocking<Unit> {
    val twin = HeldBook(items = listOf(HeldInvoice("X", 1, "ایکس", listOf(SaleDraft.Line("p1", 1.0)))))
    //  دور انداختن: سبد خالی می‌شود و X نگه‌داشته می‌ماند
    run {
      val disk = MemDisk(text = HeldInvoiceCodec.encode(twin)); val s = store(disk)
      val cart = MemCart().apply { put("X", "p1" to 1.0); recovering = true }
      s.reconcile(cart)
      assertEquals(listOf("X"), s.book.value.items.map { it.id })
      cart.mem = null; cart.recovering = false
      s.reconcile(cart)
      assertEquals(listOf("X"), s.book.value.items.map { it.id })
    }
    //  ادامهٔ همان: X فقط در سبد، با همان شماره و نام
    run {
      val s = store(MemDisk(text = HeldInvoiceCodec.encode(twin)))
      val cart = MemCart().apply { put("X", "p1" to 1.0); recovering = true }
      s.reconcile(cart)
      cart.recovering = false
      s.reconcile(cart)
      assertTrue(s.book.value.items.isEmpty())
      assertEquals(HeldTag("X", 1, "ایکس"), s.book.value.current)
    }
  }

  /**
   *  قطعِ برق پس از هر تعداد نوشتن در میانِ نگه‌داشتن و جابه‌جایی: پس از
   *  بالا آمدن (`reconcile`) هر فاکتور **دقیقاً یک بار** هست — یا در صندوق
   *  یا در فهرست — با همهٔ ردیف‌هایش.
   */
  @Test
  fun `قطعِ برق در هر لحظهٔ جابه‌جایی هیچ فاکتوری را گم یا دوتا نمی‌کند`() = runBlocking<Unit> {
    for (op in listOf("hold", "resume")) for (cut in 0..4) {
      val power = Power()
      val disk = MemDisk(power); val cart = MemCart(power); val s = store(disk)
      cart.put("A", "p1" to 2.0); done(s.hold(cart, HeldExtras(), "الف", null))
      cart.put("B", "p2" to 3.0)
      power.writes = 0; power.cut = cut
      if (op == "hold") s.hold(cart, HeldExtras()) else s.resume(cart, "A", HeldExtras())
      //  بالا آمدن: فقط آنچه به دیسک رسید
      val restarted = MemCart(Power(), cart.disk)
      val back = HeldInvoiceStore(MemDisk(text = disk.text), clock = { now })
      back.reconcile(restarted)
      val open = back.book.value.items.map { it.id to it.lines } +
        listOfNotNull(restarted.snapshot().takeIf { it.lines.isNotEmpty() }?.let { it.key!! to it.lines })
      val where = "$op/cut=$cut"
      assertEquals(where, listOf("A", "B"), open.map { it.first }.sorted())
      assertEquals(where, listOf(SaleDraft.Line("p1", 2.0)), open.first { it.first == "A" }.second)
      assertEquals(where, listOf(SaleDraft.Line("p2", 3.0)), open.first { it.first == "B" }.second)
      //  شماره‌ها یکتا
      val numbers = back.book.value.items.map { it.number } + listOfNotNull(HeldRules.activeNumber(back.book.value, restarted.snapshot()))
      assertEquals(where, numbers.size, numbers.toSet().size)
    }
  }

  @Test
  fun `reconcile — فاکتورِ هم‌کلید فقط در صندوق می‌ماند، با همان شماره و نام`() {
    val book = HeldBook(items = listOf(
      HeldInvoice("A", 1, "الف", listOf(SaleDraft.Line("p1", 1.0))),
      HeldInvoice("B", 2, "ب", listOf(SaleDraft.Line("p2", 1.0))),
    ))
    val active = SaleDraft(key = "B", lines = listOf(SaleDraft.Line("p2", 1.0)))
    val next = HeldRules.reconcile(book, active)
    assertEquals(listOf("A"), next.items.map { it.id })
    assertEquals(HeldTag("B", 2, "ب"), next.current)
    assertEquals(2, HeldRules.activeNumber(next, active))
    //  چیزی برای یکی کردن نیست ⇒ همان
    assertEquals(next, HeldRules.reconcile(next, active))
    assertEquals(book, HeldRules.reconcile(book, SaleDraft()))
  }

  @Test
  fun `کدک — شکلِ ناشناخته و خالی`() {
    assertEquals(HeldBook(), HeldInvoiceCodec.decode(null))
    assertEquals(HeldBook(), HeldInvoiceCodec.decode(""))
    assertNull(HeldInvoiceCodec.decode("[1,2"))
    //  فیلدِ تازهٔ نسخه‌های بعد (مثلاً قفلِ دستگاهِ دیگر) نسخهٔ کنونی را نمی‌شکند
    val b = HeldInvoiceCodec.decode("""{"v":2,"items":[{"id":"A","number":1,"lines":[{"productId":"p1","quantity":1}],"holder":"dev2"}],"x":1}""")
    assertNotNull(b)
    assertEquals("A", b!!.items.single().id)
    val round = HeldBook(items = listOf(HeldInvoice("A", 1, "الف", listOf(SaleDraft.Line("p1", 1.5, 20.0)), "d1", "5", "n", 9, "S1")), current = HeldTag("Z", 2))
    assertEquals(round, HeldInvoiceCodec.decode(HeldInvoiceCodec.encode(round)))
  }
}
