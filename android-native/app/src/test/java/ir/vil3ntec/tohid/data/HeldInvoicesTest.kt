package ir.vil3ntec.tohid.data

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
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
    override fun read(): String? = text
    override fun write(text: String) {
      if (fail) throw IOException("دیسک پر است")
      if (power.lands()) this.text = text
    }
  }

  /** همان قراردادِ `CartStore`: حافظه همان لحظه، دیسک با `replace` */
  class MemCart(val power: Power = Power(), var disk: SaleDraft? = null) : HeldCart {
    var mem: SaleDraft? = disk
    var fail = false
    override var busy = false
    override fun snapshot(): SaleDraft = mem ?: SaleDraft()
    override suspend fun replace(draft: SaleDraft?): Boolean {
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
  }

  private var now = 1_700_000_000_000L
  private fun store(disk: HeldDisk) = HeldInvoiceStore(disk, device = "A", clock = { now })

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
      assertEquals(s.book.value, again.book.value)
      val c = again.book.value.items.first { it.id == "C" }
      assertEquals("3", c.discount)
      assertEquals(3, c.number)
      assertEquals(HeldTag("A", 1, "الف", "یادداشت"), again.book.value.current)
    } finally { dir.deleteRecursively() }
  }

  @Test
  fun `فایلِ خراب کنار گذاشته می‌شود، پاک نه`() {
    val dir = Files.createTempDirectory("held").toFile()
    try {
      val file = File(dir, HeldInvoiceStore.FILE)
      file.writeText("{نیمه")
      val s = store(FileHeldDisk(file))
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
    assertEquals("B", keyOf(cart))
    assertEquals(listOf("A"), s.book.value.items.map { it.id })
    assertEquals(listOf("A"), HeldInvoiceCodec.decode(disk.text)!!.items.map { it.id })
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
