package ir.vil3ntec.tohid.scan

import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanSource
import ir.vil3ntec.tohid.core.hw.ScanTarget
import ir.vil3ntec.tohid.core.i18n.DesktopPhrases
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldInvoice
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.Sale
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.ScanSaleHold
import ir.vil3ntec.tohid.data.ShopData
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  اسکن وقتی برنامه بسته است: صفِ پرونده‌ای (بازخوانی، برداشتن، بستهٔ حافظه بی تکرار)، مقصدها (انبارگردانی، ورود،
 *  فاکتورِ نگه‌داشته) و قاعدهٔ «هیچ فروشِ بی‌صدا» — بی دستگاهِ واقعی.
 */
class ScanInboxTest {

  private val dir: File = kotlin.io.path.createTempDirectory("scan-inbox").toFile()
  private val dev = "bt:AA:BB:CC:DD:EE:01"

  @After fun clean() {
    ScanInbox.foreground = false
    ScanInbox.collecting = false
    while (ScanInbox.live.tryReceive().isSuccess) Unit
    ScanInbox.handoff.value = null
    dir.deleteRecursively()
  }

  private fun fresh(): File = File(dir, "inbox-${System.nanoTime()}.log").also { ScanInbox.openForTest(it) }

  private val milk = Product("p1", "شیر", salePrice = 45.0, purchasePrice = 40.0, unit = "دانه", barcodes = listOf("4006381333931", "M1"))
  private val bread = Product("p2", "نان", salePrice = 12.5, barcodes = listOf("200000002468"))
  private val data = ShopData(products = listOf(milk, bread))

  private fun s(id: String, code: String, at: Long = 1_000) = QueuedScan(id, dev, at, code)

  @Test fun `برنامهٔ بسته - اسکن به پرونده می‌رود و پس از بازخوانی همان است`() {
    val f = fresh()
    assertTrue(ScanInbox.arrive(dev, "4006381333931\r", 1_000))
    assertTrue(ScanInbox.arrive(dev, "4006381333931", 2_000))
    assertFalse(ScanInbox.arrive(dev, "\u0003 ", 3_000))
    assertEquals(2, ScanInbox.count.value)
    //  برنامه از نو بالا آمد
    ScanInbox.openForTest(f)
    assertEquals(listOf("4006381333931", "4006381333931"), ScanInbox.pending().map { it.code })
    ScanInbox.take(setOf(ScanInbox.pending().first().id))
    ScanInbox.openForTest(f)
    assertEquals(1, ScanInbox.count.value)
  }

  @Test fun `برنامهٔ جلوی چشم - اسکنِ زنده به صفحه می‌رود ولی بستهٔ حافظه همیشه صف است`() {
    fresh()
    ScanInbox.foreground = true
    ScanInbox.collecting = true
    assertFalse(ScanInbox.arrive(dev, "200000002468", 1_000))
    assertEquals("200000002468", ScanInbox.live.tryReceive().getOrNull())
    assertEquals(0, ScanInbox.count.value)
    assertEquals(3, ScanInbox.importMemory(dev, listOf("A1", "A1", "B2,4"), 2_000))
    assertEquals(3, ScanInbox.count.value)
    //  ریشهٔ برنامه گوش نمی‌دهد (صفحهٔ قفل) ⇒ صف
    ScanInbox.collecting = false
    assertTrue(ScanInbox.arrive(dev, "200000002468", 3_000))
  }

  @Test fun `بستهٔ حافظهٔ تکراری دوباره صف نمی‌شود و دنباله‌اش می‌شود`() {
    val f = fresh()
    val batch = listOf("2026-11-01 09:00:00,4006381333931", "2026-11-01 09:00:04,200000002468", "C3")
    assertEquals(3, ScanInbox.importMemory(dev, batch, 1_000))
    assertEquals(0, ScanInbox.importMemory(dev, batch, 5_000))
    assertEquals(1, ScanInbox.importMemory(dev, batch + "D4", 9_000))
    //  پس از برداشتن و بالا آمدنِ دوباره هم
    ScanInbox.discardAll()
    ScanInbox.openForTest(f)
    assertEquals(0, ScanInbox.importMemory(dev, batch + "D4", 12_000))
    assertEquals(0, ScanInbox.count.value)
    assertTrue(ScanInbox.pending().isEmpty())
  }

  @Test fun `مقصد فقط با برداشتنِ صفحه برداشته می‌شود`() {
    fresh()
    ScanInbox.arrive(dev, "M1", 1_000)
    ScanInbox.handTo(ScanTarget.COUNT)
    assertNull(ScanHandoff.take(ScanTarget.RECEIVE))
    assertEquals(1, ScanInbox.count.value)
    val h = ScanHandoff.take(ScanTarget.COUNT)!!
    assertEquals(listOf("M1"), h.scans.map { it.code })
    assertEquals(0, ScanInbox.count.value)
    assertNull(ScanInbox.handoff.value)
  }

  @Test fun `انبارگردانی - شمار به خانه افزوده می‌شود و ناشناس جداست`() {
    val scans = listOf(s("1", "4006381333931"), s("2", "M1"), s("3", "200000002468"), s("4", "999"), s("5", CommandCard.payload(CommandCard.Command.CASH, CommandCard.Key("00".repeat(32), 1))))
    val r = ScanApply.counts(mapOf("p1" to "۳", "p9" to "1"), data, scans)
    assertEquals("5", r.counts["p1"])
    assertEquals("1", r.counts["p2"])
    assertEquals("1", r.counts["p9"])
    assertEquals(setOf("p1", "p2"), r.ids)
    assertEquals(listOf("999"), r.unknown.map { it.code })
    val rec = ScanApply.receive(data, scans + QueuedScan("6", dev, 1, "999", ScanSource.MEMORY, qty = 3))
    assertEquals(listOf(ScanApply.Received("p1", "4006381333931", 2), ScanApply.Received("p2", "200000002468", 1), ScanApply.Received(null, "999", 4)), rec)
    //  برگهٔ ورودِ رابطِ امروز: واحد و قیمتِ خرید از کالا، ناشناس ⇒ «محصولِ تازه» با همان بارکد
    assertEquals(
      listOf(ScanApply.LegacyRow("p1", "", "1", "دانه", "40"), ScanApply.LegacyRow(null, "999", "1", "دانه", "")),
      ScanApply.legacyRows(data, listOf(s("1", "M1"), s("2", "999"))),
    )
  }

  @Test fun `فاکتورِ فروش - همان کد N بار مقدارِ N و جمع به واحدِ کوچک`() {
    val scans = listOf(s("1", "4006381333931"), s("2", "4006381333931"), s("3", "M1"), s("4", "200000002468"), s("5", "999"))
    val plan = ScanSaleHold.plan(data, scans)
    val inv = plan.invoices.single()
    assertEquals(listOf("p1" to 3, "p2" to 1), inv.lines.map { it.productId to it.qty })
    //  ۳ × ۴۵ + ۱ × ۱۲٫۵ (گرد به افغانی، همان صندوق) = ۱۴۸ افغانی
    assertEquals(135_00L, inv.lines[0].totalMinor)
    assertEquals(inv.lines.sumOf { it.totalMinor }, inv.totalMinor)
    assertEquals(listOf("999"), plan.unknown.map { it.code })
    assertEquals(setOf("1", "2", "3", "4", "5"), inv.scanIds)
    //  شناسهٔ پایدار: همان اسکن‌ها همان فاکتور
    assertEquals(inv.id, ScanSaleHold.plan(data, scans).invoices.single().id)
    val held = ScanSaleHold.held(inv, 7)
    assertEquals(listOf(SaleDraft.Line("p1", 3.0), SaleDraft.Line("p2", 1.0)), held.lines)
    assertEquals(ScanSaleHold.LABEL, held.label)
  }

  @Test fun `هیچ فروشِ بی‌صدا - فاکتورِ فروخته دوباره ساخته نمی‌شود و ساختن فروش ثبت نمی‌کند`() {
    val scans = listOf(s("1", "M1"), s("2", "M1"))
    val plan = ScanSaleHold.plan(data, scans)
    assertTrue(data.sales.isEmpty())
    val sold = data.copy(sales = listOf(Sale(plan.invoices.single().id)))
    val again = ScanSaleHold.plan(sold, scans)
    assertTrue(again.invoices.isEmpty())
    assertEquals(setOf("1", "2"), again.settled)
    //  هیچ پروندهٔ اسکن، نگه‌داشتن یا «دستگاه‌ها» راهِ ثبتِ فروش ندارد — فروش فقط از پنلِ پرداختِ صندوق، یا (راهِ «ب»،
    //  کارتِ فرمانِ روشن) فقط از `ScanCashSale` پشتِ دروازهٔ `ScanSaleRule.why`
    val root = DesktopPhrases.repo().resolve("android-native/app/src/main/java/ir/vil3ntec/tohid")
    listOf(
      "scan/ScanInbox.kt", "scan/ScanApply.kt", "scan/ScanServiceAndroid.kt", "scan/ScanCashAndroid.kt", "data/ScanSaleHold.kt",
      "hw/DeviceLinks.kt", "ui/components/devices/DeviceHubUi.kt", "ui/components/devices/CommandCardAndroid.kt",
    ).forEach { rel ->
      val text = root.resolve(rel).readText()
      assertFalse("$rel فروش ثبت می‌کند", text.contains("SalesEngine.record") || text.contains(".recordSale("))
    }
    val cash = root.resolve("data/ScanCashSale.kt").readText()
    assertEquals(1, Regex("""SalesEngine\.record\(""").findAll(cash).count())
    assertTrue(cash.contains("ScanSaleRule.why(batch, gate, missing.size)") && cash.contains("if (!gate.cardOn || pending.isEmpty()) return Plan()"))
    assertTrue(cash.contains("payment = SalesEngine.Payment.CASH"))
  }

  @Test fun `نگه‌داشتنِ فاکتورِ اسکن - شمارهٔ آزاد، بی تکرار و با سقف`() {
    val inv = HeldInvoice("scan-1", 0, ScanSaleHold.LABEL, listOf(SaleDraft.Line("p1", 2.0)))
    val r = HeldRules.park(HeldBook(), inv) as HeldRules.Result.Ok
    assertEquals(1, r.number)
    val again = HeldRules.park(r.book, inv) as HeldRules.Result.Ok
    assertEquals(r.book, again.book)
    val full = HeldBook(items = (1..HeldRules.MAX_OPEN - 1).map { HeldInvoice("h$it", it, lines = listOf(SaleDraft.Line("p1", 1.0))) })
    assertTrue(HeldRules.park(full, inv) is HeldRules.Result.Refused)
    assertTrue(HeldRules.park(HeldBook(), inv.copy(lines = emptyList())) is HeldRules.Result.Refused)
  }

  @Test fun `مانیفست - سرویسِ پیش‌روی connectedDevice و اجازه‌های بلوتوثِ بی مکان`() {
    val m = DesktopPhrases.repo().resolve("android-native/app/src/main/AndroidManifest.xml").readText()
    assertTrue(m.contains("android:name=\".scan.ScanCaptureService\""))
    assertTrue(Regex("""ScanCaptureService"[^>]*foregroundServiceType="connectedDevice"""").containsMatchIn(m))
    listOf("FOREGROUND_SERVICE", "FOREGROUND_SERVICE_CONNECTED_DEVICE", "BLUETOOTH_CONNECT").forEach {
      assertTrue(it, m.contains("android:name=\"android.permission.$it\""))
    }
    assertTrue(Regex("""BLUETOOTH_SCAN"\s+android:usesPermissionFlags="neverForLocation"""").containsMatchIn(m))
    assertFalse(m.contains("READ_SMS"))
    assertFalse(m.contains("ACCESS_BACKGROUND_LOCATION"))
    //  کامپیوتر سرویسِ اندروید را نمی‌سازد؛ جایگزین دارد
    val desk = DesktopPhrases.repo().resolve("desktop/build.gradle.kts").readText()
    assertTrue(desk.contains("\"scan/ScanServiceAndroid.kt\"") && desk.contains("\"hw/DeviceSignalsAndroid.kt\""))
  }
}
