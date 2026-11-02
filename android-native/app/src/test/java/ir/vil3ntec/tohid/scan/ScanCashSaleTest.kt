package ir.vil3ntec.tohid.scan

import ir.vil3ntec.tohid.core.hw.CommandCard
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanSaleRule
import ir.vil3ntec.tohid.core.hw.ScanSource
import ir.vil3ntec.tohid.data.CashShift
import ir.vil3ntec.tohid.data.Debtor
import ir.vil3ntec.tohid.data.HeldBook
import ir.vil3ntec.tohid.data.HeldRules
import ir.vil3ntec.tohid.data.PayFlow
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ScanCashSale
import ir.vil3ntec.tohid.data.ScanSaleHold
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.WarehouseEntry
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 *  راهِ «ب» — «فروشِ نقدی با کارتِ فرمان»: فقط نقد، همان `SalesEngine.record` ِ صندوق با گردکردنِ PAY-09، بازگشت به
 *  فاکتورِ نگه‌داشته (راهِ «الف») با صندوقِ بسته، بی اجازه یا کدِ ناشناس، تنظیمِ خاموش ⇒ هیچ، و بستهٔ حافظهٔ دوباره ⇒
 *  هیچ فروشِ دوم.
 */
class ScanCashSaleTest {

  private val dir: File = kotlin.io.path.createTempDirectory("scan-cash").toFile()
  private val dev = "bt:AA:BB:CC:DD:EE:07"

  @After fun clean() {
    ScanInbox.foreground = false
    ScanInbox.collecting = false
    dir.deleteRecursively()
  }

  private val key = CommandCard.newKey(ByteArray(32) { (it * 7 + 1).toByte() }, 1)
  private val cash = CommandCard.payload(CommandCard.Command.CASH, key)
  private val fresh = CommandCard.payload(CommandCard.Command.NEW, key)
  private val undo = CommandCard.payload(CommandCard.Command.UNDO, key)
  private val commands: (String) -> CommandCard.Command? = { CommandCard.parse(it, key) }

  private val a = Product(id = "p1", name = "شیر", unit = "عدد", purchasePrice = 30.0, salePrice = 41.0, barcodes = listOf("4006381333931"))
  private val b = Product(id = "p2", name = "نان", unit = "عدد", purchasePrice = 5.0, salePrice = 12.5, barcodes = listOf("200000002468"))
  private val ledger = ShopData(
    products = listOf(a, b),
    debtors = listOf(Debtor(id = "d1", name = "احمد")),
    warehouseEntries = listOf(WarehouseEntry(id = "w1", productId = "p1", units = 100.0), WarehouseEntry(id = "w2", productId = "p2", units = 100.0)),
  )

  private val open = ScanSaleRule.Gate(cardOn = true, tillOpen = true, canSell = true)
  private val till = ScanCashSale.Till(seller = "کریم", cashRounding = 500, blockExpired = false)

  private fun seq(vararg codes: String) = codes.mapIndexed { i, c -> QueuedScan("s$i", dev, 1_000L + i * 2_000L, c) }

  private var n = 0
  private fun newId() = "id${n++}"

  private fun record(d: ShopData, r: ScanCashSale.Ready): ShopData {
    val res = ScanCashSale.record(d, r, till, "2026-11-02", 1_762_000_000_000, ::newId, "A")
    assertTrue("ثبت نشد: $res", res is SalesEngine.Result.Ok)
    return (res as SalesEngine.Result.Ok).data
  }

  @Test fun `تنظیمِ خاموش - هیچ ثبت، هیچ نگه‌داشتن، هیچ برداشتن`() {
    val plan = ScanCashSale.plan(ledger, seq("4006381333931", cash), open.copy(cardOn = false), commands)
    assertEquals(ScanCashSale.Plan(), plan)
  }

  @Test fun `نقدِ کامل با همان قیمت و گردکردنِ صندوق - فقط نقد و بی مشتری`() {
    val plan = ScanCashSale.plan(ledger, seq("4006381333931", "4006381333931", "4006381333931", cash), open, commands)
    val r = plan.record.single()
    assertEquals(listOf("s0", "s1", "s2", "s3"), r.scanIds.sorted())
    assertEquals(ScanSaleRule.NOTE_LIVE, r.note)
    assertEquals(123_00L, r.totalMinor)
    val d = record(ledger, r)
    val sale = d.sales.single()
    assertEquals(r.id, sale.id)
    assertEquals("cash", sale.paymentMethod)
    assertNull(sale.debtorId)
    //  PAY-09: ۱۲۳ با پلهٔ ۵ ⇒ ۱۲۵؛ ردیفِ جدای گرد و همهٔ پرداختی نقد
    assertEquals(200L, sale.roundingMinor)
    assertEquals(125.0, sale.finalTotal, 0.0)
    assertEquals(125.0, sale.paidAmount, 0.0)
    assertEquals(0.0, sale.remaining, 0.0)
    assertEquals(0.0, sale.debtGiven, 0.0)
    assertEquals("کریم", sale.seller)
    assertEquals(ScanSaleRule.NOTE_LIVE, sale.note)
    assertEquals(125.0, CashShift.saleCash(sale), 0.0)
    //  موجودی مثلِ صندوق کم شد
    assertEquals(97.0, ir.vil3ntec.tohid.data.ShopStore.stock(d, "p1"), 0.0)
    //  هر ثبتی از این راه نقد است — حتی اگر کسی Checkout ِ دیگری بخواهد، این راه نمی‌سازد
    val co = ScanCashSale.checkout(till, ScanSaleRule.NOTE_LIVE)
    assertEquals(SalesEngine.Payment.CASH, co.payment)
    assertNull(co.debtorId)
    assertNull(co.settlement)
    assertEquals(0.0, co.discountValue, 0.0)
    //  گزارشِ صندوق آن را «ثبت از اسکنر» می‌شمارد
    assertEquals(ScanCashSale.Summary(1, 125_00L, 0), ScanCashSale.summary(d, 0, Long.MAX_VALUE))
    assertEquals(12_500L, PayFlow.minor(sale.finalTotal))
  }

  @Test fun `حذفِ آخرین و فاکتورِ تازه - فقط فاکتورِ نقدخورده ثبت می‌شود و بقیه برای پرسش می‌ماند`() {
    val scans = seq("200000002468", fresh, "4006381333931", "200000002468", undo, cash, cash, undo, "4006381333931")
    val plan = ScanCashSale.plan(ledger, scans, open, commands)
    val r = plan.record.single()
    assertEquals(listOf("p1" to 1.0), r.cart.map { it.productId to it.quantity })
    assertEquals(setOf("s2", "s3", "s4", "s5"), r.scanIds)
    //  فاکتورِ «تازه» ِ بی پرداخت و فاکتورِ باز (با «نقد» ِ دوباره و «حذف» ِ بی اثرِ پیشش) برای پرسشِ «در انتظار» می‌مانند
    assertTrue(plan.drop.isEmpty())
    assertTrue(plan.hold.isEmpty())
    val left = scans.map { it.id } - r.scanIds - plan.drop
    assertEquals(listOf("s0", "s1", "s6", "s7", "s8"), left)
    //  فرمانِ بی کالا میانِ دو «نقد» سرگردان است و بی اثر برداشته می‌شود
    val stray = ScanCashSale.plan(ledger, seq("4006381333931", cash, cash, undo, fresh, "200000002468", cash), open, commands)
    assertEquals(setOf("s2", "s3", "s4"), stray.drop)
    assertEquals(2, stray.record.size)
  }

  @Test fun `بی «نقد» هیچ فروشی ثبت نمی‌شود`() {
    val plan = ScanCashSale.plan(ledger, seq("4006381333931", "200000002468"), open, commands)
    assertEquals(ScanCashSale.Plan(), plan)
  }

  @Test fun `صندوقِ بسته یا کاربرِ بی اجازه - فاکتورِ نگه‌داشته، نه فروش`() {
    val scans = seq("4006381333931", "200000002468", cash)
    for ((gate, why) in listOf(open.copy(tillOpen = false) to ScanSaleRule.Why.TILL_CLOSED, open.copy(canSell = false) to ScanSaleRule.Why.NO_PERMISSION)) {
      val plan = ScanCashSale.plan(ledger, scans, gate, commands)
      assertTrue(plan.record.isEmpty())
      val h = plan.hold.single()
      assertEquals(why, h.why)
      val inv = h.invoice!!
      assertEquals(listOf("p1" to 1, "p2" to 1), inv.lines.map { it.productId to it.qty })
      assertEquals(setOf("s0", "s1", "s2"), h.scanIds)
      //  همان شناسهٔ فاکتور ⇒ «پرداخت» ِ دستی همان فروش را یک بار ثبت می‌کند
      assertEquals(ScanSaleHold.plan(ledger, scans, commands).invoices.single().id, inv.id)
      val held = ScanSaleHold.held(inv, 5, ScanCashSale.HELD_LABEL, "نقد گرفته شد")
      assertEquals(ScanCashSale.HELD_LABEL, held.label)
      assertTrue(HeldRules.park(HeldBook(), held) is HeldRules.Result.Ok)
    }
  }

  @Test fun `بارکدِ ناشناس یا کدِ جعلی - نگه‌داشته با فهرستِ کد و هرگز کالا`() {
    val forged = "TOHID-CMD-CASH-1-000000000001"
    val plan = ScanCashSale.plan(ledger, seq("4006381333931", "999", forged, cash), open, commands)
    val h = plan.hold.single()
    assertEquals(ScanSaleRule.Why.UNKNOWN_CODE, h.why)
    assertEquals(listOf("999"), h.unknown)
    assertEquals(listOf("p1"), h.invoice!!.lines.map { it.productId })
    assertEquals(listOf(forged), plan.ignored.map { it.code })
    //  فقط جعلی، بی کدِ ناشناس ⇒ فروش ثبت می‌شود ولی جعلی کالا نیست و یادداشت می‌شود
    val ok = ScanCashSale.plan(ledger, seq("4006381333931", forged, cash), open, commands)
    assertEquals(listOf("p1" to 1.0), ok.record.single().cart.map { it.productId to it.quantity })
    assertEquals(listOf(forged), ok.ignored.map { it.code })
  }

  @Test fun `کمبودِ موجودی - ردِ صندوق و هیچ نوشتن`() {
    val empty = ledger.copy(warehouseEntries = emptyList())
    val r = ScanCashSale.plan(empty, seq("4006381333931", cash), open, commands).record.single()
    val res = ScanCashSale.record(empty, r, till, "2026-11-02", 1, ::newId)
    assertTrue(res is SalesEngine.Result.Failed)
  }

  @Test fun `ثبتِ دوباره نمی‌شود - شناسهٔ پایدار و بستهٔ حافظهٔ دوباره`() {
    val f = File(dir, "inbox.log").also { ScanInbox.openForTest(it) }
    val burst = listOf("4006381333931", "4006381333931,2", cash, "200000002468", cash)
    assertEquals(5, ScanInbox.importMemory(dev, burst, 10_000))
    val first = ScanCashSale.plan(ledger, ScanInbox.pending(), open, commands)
    assertEquals(2, first.record.size)
    assertTrue(first.record.all { it.note == ScanSaleRule.NOTE_MEMORY })
    //  به ترتیبِ بسته: ۳ شیر، بعد ۱ نان
    assertEquals(listOf(listOf("p1" to 3.0), listOf("p2" to 1.0)), first.record.map { r -> r.cart.map { it.productId to it.quantity } })
    var d = ledger
    first.record.forEach { d = record(d, it) }
    assertEquals(2, d.sales.size)
    assertTrue(d.sales.all { it.note == ScanSaleRule.NOTE_MEMORY })
    //  برنامه پس از نوشتن و پیش از برداشتن بسته شد ⇒ همان طرح فقط برداشتن است
    val again = ScanCashSale.plan(d, ScanInbox.pending(), open, commands)
    assertTrue(again.record.isEmpty())
    assertEquals(first.record.flatMap { it.scanIds }.toSet(), again.drop)
    assertTrue(ScanCashSale.record(d, first.record[0], till, "2026-11-02", 2, ::newId) is SalesEngine.Result.Failed)
    ScanInbox.take(again.drop)
    //  همان بسته دوباره (بارکدخوان حافظه را پاک نکرده بود) و پس از بالا آمدنِ دوبارهٔ برنامه
    assertEquals(0, ScanInbox.importMemory(dev, burst, 20_000))
    ScanInbox.openForTest(f)
    assertEquals(0, ScanInbox.importMemory(dev, burst, 30_000))
    assertEquals(ScanCashSale.Plan(), ScanCashSale.plan(d, ScanInbox.pending(), open, commands))
    assertEquals(2, d.sales.size)
    assertEquals(ScanCashSale.Summary(2, d.sales.sumOf { PayFlow.minor(it.finalTotal) }, 2), ScanCashSale.summary(d, 0, Long.MAX_VALUE))
  }

  @Test fun `صفِ اسکنِ زنده - برداشتن فقط پس از نوشتن و کارتِ کهنه نادیده`() {
    ScanInbox.openForTest(File(dir, "live.log"))
    val old = CommandCard.payload(CommandCard.Command.CASH, key)
    val next = CommandCard.rotate(key, ByteArray(32) { 9 })
    assertTrue(ScanInbox.arrive(dev, "4006381333931", 1_000))
    assertTrue(ScanInbox.arrive(dev, old, 2_000))
    val plan = ScanCashSale.plan(ledger, ScanInbox.pending(), open) { CommandCard.parse(it, next) }
    //  «نقد» ِ کارتِ پیشین فرمان نیست ⇒ فاکتور باز می‌ماند و هیچ ثبت نمی‌شود
    assertTrue(plan.record.isEmpty())
    assertEquals(CommandCard.Verdict.OLD_CARD, CommandCard.read(old, next).verdict)
    assertTrue(ScanInbox.arrive(dev, CommandCard.payload(CommandCard.Command.CASH, next), 3_000))
    val ok = ScanCashSale.plan(ledger, ScanInbox.pending(), open) { CommandCard.parse(it, next) }
    assertEquals(3, ok.record.single().scanIds.size)
    assertEquals(listOf(old), ok.ignored.map { it.code })
    assertFalse(ScanInbox.pending().isEmpty())
  }

  @Test fun `برنامهٔ باز - کدِ فرمان هرگز به صندوق یا صفحهٔ کالا نمی‌رود`() {
    ScanInbox.openForTest(File(dir, "fg.log"))
    ScanInbox.foreground = true
    ScanInbox.collecting = true
    assertFalse(ScanInbox.arrive(dev, "4006381333931", 1_000))
    assertTrue(ScanInbox.arrive(dev, cash, 2_000))
    assertTrue(ScanInbox.arrive(dev, "TOHID-CMD-CASH-9-123456789012", 3_000))
    assertEquals("4006381333931", ScanInbox.live.tryReceive().getOrNull())
    assertNull(ScanInbox.live.tryReceive().getOrNull())
    assertEquals(2, ScanInbox.pending().size)
    //  تنها و بی کالا ⇒ سرگردان و بی اثر برداشته می‌شوند
    val plan = ScanCashSale.plan(ledger, ScanInbox.pending(), open, commands)
    assertEquals(ScanInbox.pending().map { it.id }.toSet(), plan.drop)
    assertTrue(plan.record.isEmpty())
  }
}
