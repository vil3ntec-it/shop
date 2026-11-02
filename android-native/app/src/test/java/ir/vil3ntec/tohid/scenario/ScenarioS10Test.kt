package ir.vil3ntec.tohid.scenario

import ir.vil3ntec.tohid.data.DiskWrite
import ir.vil3ntec.tohid.data.DraftRestore
import ir.vil3ntec.tohid.data.Product
import ir.vil3ntec.tohid.data.SaleDraft
import ir.vil3ntec.tohid.data.SaleDraftCodec
import ir.vil3ntec.tohid.data.SaleDraftRules
import ir.vil3ntec.tohid.data.SalePost
import ir.vil3ntec.tohid.data.SalesEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.data.WarehouseEntry
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files
import kotlin.random.Random

/**
 *  S10 — قطعِ برق وسطِ فروش (POS-13، SYN-02): فاکتورِ ۴ قلمی، ۱۰۰ بار، هر بار «برق» در لحظه‌ای تصادفی می‌رود — وسطِ
 *  نوشتنِ پیش‌نویس، وسطِ نوشتنِ دفتر (نیمی از بایت‌ها در فایلِ کناری، بی جابه‌جایی) یا میانِ دفتر و پاک‌کردنِ پیش‌نویس.
 *  نوشتن همان `DiskWrite` و `SaleDraftCodec` ِ برنامه، ثبت همان `SalePost.run` + `SalesEngine.record` با کلیدِ پیش‌نویس، و
 *  بازکردن همان `SaleDraftRules.decide`.
 *
 *  ماندهٔ سناریو (pending، نه سرخ): خودِ `kill -9` (اندروید و لینوکس) و `taskkill /F` (ویندوز) روی برنامهٔ واقعی در CI، و
 *  قطعِ برقِ واقعی با `system_reset` ِ QEMU یا کامپیوترِ مرجع، اجرا نمی‌شوند؛ این آزمون مرگِ فرایند را میانِ همان
 *  نوشتن‌ها شبیه‌سازی می‌کند. op ِ صفِ همگام‌سازی در برابرِ crash را `SyncCommitTest` می‌سنجد.
 */
class ScenarioS10Test {

  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val products = listOf(
    Product(id = "p1", name = "برنج", unit = "کیلوگرم", salePrice = 120.0),
    Product(id = "p2", name = "روغن", unit = "عدد", salePrice = 150.0),
    Product(id = "p3", name = "چای", unit = "عدد", salePrice = 90.0),
    Product(id = "p4", name = "صابون", unit = "عدد", salePrice = 25.0),
  )
  private val d0 = ShopData(products = products, warehouseEntries = products.map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = 100.0) })
  private val lines = listOf("p1" to 2.5, "p2" to 1.0, "p3" to 3.0, "p4" to 2.0)

  private fun cartOf(l: List<Pair<String, Double>>) = l.map { SalesEngine.CartLine(it.first, it.second) }

  /** مرگِ فرایند */
  private class PowerCut : RuntimeException()

  /** دیسکی که پس از [budget] کار می‌میرد؛ نوشتنِ نیمه‌کاره فقط فایلِ کناری را نیمه می‌گذارد */
  private class Disk(var budget: Int) {
    fun write(f: File, text: String) {
      if (budget-- <= 0) {
        val half = text.toByteArray(Charsets.UTF_8).let { it.copyOf(it.size / 2) }
        File(f.parentFile, f.name + ".tmp").writeBytes(half)
        throw PowerCut()
      }
      DiskWrite.text(f, text)
    }
    fun delete(f: File) {
      if (budget-- <= 0) throw PowerCut()
      f.delete()
    }
  }

  /** یک بار: ۴ اسکن (هر کدام پیش‌نویس می‌نویسد)، F12 ⇒ نوشتنِ دفتر ⇒ پاک‌کردنِ پیش‌نویس. ۶ کار؛ برق پس از [cut] */
  private fun session(dir: File, key: String, cut: Int) = runBlocking {
    val ledger = File(dir, "shop-data.json")
    val draftFile = File(dir, "sale-draft.json")
    val disk = Disk(cut)
    var seq = 0
    try {
      val cart = ArrayList<SalesEngine.CartLine>()
      for ((pid, q) in lines) {
        cart += SalesEngine.CartLine(pid, q)
        disk.write(draftFile, SaleDraftCodec.encode(SaleDraft.of(key, cart, null, 1)))
      }
      val d = json.decodeFromString(ShopData.serializer(), ledger.readText())
      SalePost().run(key, { k -> SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-02", 5_000, { "id${seq++}" }, saleId = k) },
        { data -> disk.write(ledger, json.encodeToString(ShopData.serializer(), data)); true }) { disk.delete(draftFile) }
    } catch (_: PowerCut) {
      //  برق رفت
    }
  }

  @Test
  fun `S10 — ۱۰۰ قطعِ برق در لحظهٔ تصادفی ⇒ یا فاکتورِ کامل یا پیش‌نویسِ سالم، هرگز نیمه‌ثبت و هرگز دوتا`() {
    val rnd = Random(20261102)
    val seen = HashSet<Int>()
    repeat(100) { run ->
      val dir = Files.createTempDirectory("s10").toFile()
      try {
        val ledger = File(dir, "shop-data.json")
        DiskWrite.text(ledger, json.encodeToString(ShopData.serializer(), d0))
        val key = "S10-$run"
        val cut = rnd.nextInt(0, 7)
        seen += cut
        session(dir, key, cut)

        //  دوباره باز کن: دفتر خراب نیست
        val d = json.decodeFromString(ShopData.serializer(), ledger.readText())
        val draftFile = File(dir, "sale-draft.json")
        val draft = SaleDraftCodec.decode(if (draftFile.exists()) draftFile.readText() else null)
        val decision = SaleDraftRules.decide(draft, d)

        if (d.sales.isNotEmpty()) {
          //  فاکتورِ کامل: سند، ۴ قلم، حرکتِ کالا و پرداخت
          val sale = d.sales.single()
          assertEquals("بار $run", key, sale.id)
          assertEquals(4, d.saleItems.count { it.saleId == key })
          for ((pid, q) in lines) assertEquals(100.0 - q, ShopStore.stock(d, pid), 1e-9)
          assertEquals(sale.finalTotal, sale.paidAmount, 0.0)
          assertTrue("بار $run: $decision", decision == DraftRestore.None || decision == DraftRestore.Posted(key))
          //  همان فاکتور با همان کلید دوباره ثبت نمی‌شود ⇒ سند و op ِ تکراری نیست
          val again = SalesEngine.record(d, cartOf(lines), SalesEngine.Checkout(), "2026-11-02", 6_000, { "x" }, saleId = key)
          assertEquals("بار $run", SalesEngine.ALREADY_POSTED, (again as SalesEngine.Result.Failed).message)
        } else {
          //  هیچ چیزِ نیمه: نه قلم، نه حرکت، نه بدهی؛ موجودی همان پیشین
          assertTrue("بار $run", d.saleItems.isEmpty() && d.stockMovements.isEmpty() && d.transactions.isEmpty())
          for ((pid, _) in lines) assertEquals(100.0, ShopStore.stock(d, pid), 0.0)
          val written = minOf(cut, lines.size)
          if (written == 0) assertEquals(DraftRestore.None, decision)
          else {
            val offer = decision as DraftRestore.Offer
            assertEquals(lines.take(written), offer.draft.lines.map { it.productId to it.quantity })
            //  پس از «بازیابی» همان کلید یک بار ثبت می‌شود
            var n = 0
            val ok = SalesEngine.record(d, offer.draft.cart(), SalesEngine.Checkout(), "2026-11-02", 6_000, { "y${n++}" }, saleId = offer.draft.key)
            assertEquals("بار $run", listOf(key), (ok as SalesEngine.Result.Ok).data.sales.map { it.id })
          }
        }
      } finally {
        dir.deleteRecursively()
      }
    }
    assertEquals("هر لحظهٔ قطع دست‌کم یک بار", (0..6).toSet(), seen)
  }
}
