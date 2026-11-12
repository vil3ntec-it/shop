package ir.vil3ntec.tohid.desktop

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
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 *  S10 — «قطعِ برق وسطِ فروش» با **کشتنِ واقعیِ فرایند** (`kill -9` روی لینوکس/مک، `TerminateProcess` = `taskkill /F`
 *  روی ویندوز؛ `Process.destroyForcibly`)، نه استثنای ساختگی ِ `ScenarioS10Test`.
 *
 *  فرایندِ جدا ([S10KillChild]، همان JVM و همان کلاس‌های برنامه) پشتِ هم می‌فروشد با همان مسیرِ برنامه: هر اسکن پیش‌نویس
 *  (`SaleDraftCodec` + `DiskWrite`)، F12 ⇒ `SalePost.run` ⇒ `SalesEngine.record` ⇒ نوشتنِ دفتر با `DiskWrite` ⇒ پاک‌کردنِ
 *  پیش‌نویس. آزمون آن را در لحظهٔ تصادفی (بذرِ ثابت) می‌کشد، دفتر و پیش‌نویس را مثلِ برنامهٔ تازه‌باز می‌خواند و دوباره راه
 *  می‌اندازد. هر بار: دفتر خوانا؛ هر فاکتور کامل (۴ قلم، موجودی، پرداخت)؛ هیچ فاکتوری دو بار؛ پیش‌نویس یا هیچ، یا «ثبت شده»،
 *  یا همان ردیف‌های فاکتورِ ناتمام — و پس از «بازیابی» همان کلید یک بار ثبت می‌شود.
 *
 *  نیست: قطعِ برقِ واقعی (کش‌های دیسک و `system_reset` ِ QEMU)؛ فقط مرگِ فرایند.
 */
class S10KillDesktopTest {

  @Test
  fun `S10 — کشتنِ واقعیِ فرایند وسطِ فروش ⇒ دفتر خوانا، فاکتور کامل یا پیش‌نویسِ سالم، هرگز دوتا`() {
    val dir = Files.createTempDirectory("s10kill").toFile()
    try {
      val ledger = File(dir, S10KillChild.LEDGER)
      DiskWrite.text(ledger, S10KillChild.json.encodeToString(ShopData.serializer(), S10KillChild.start))
      val rnd = Random(20261112)
      var killedMidSale = 0
      var lastSales = 0
      repeat(ROUNDS) { round ->
        val posted = AtomicInteger(0)
        val acked = HashSet<String>()
        val p = child(dir, "r$round")
        val reader = Thread {
          runCatching {
            p.inputStream.bufferedReader().forEachLine {
              if (it.startsWith("posted ")) { synchronized(acked) { acked += it.removePrefix("posted ") }; posted.incrementAndGet() }
            }
          }
        }.apply { isDaemon = true; start() }
        //  تا دست‌کم یک فاکتورِ همین دور ثبت شود، بعد مرگ در لحظهٔ تصادفی
        val until = System.nanoTime() + TimeUnit.SECONDS.toNanos(60)
        while (posted.get() < 1 && p.isAlive && System.nanoTime() < until) Thread.sleep(5)
        assertTrue("دورِ $round: فرایندِ فروش راه نیفتاد", posted.get() >= 1)
        Thread.sleep(rnd.nextLong(1, 120))
        p.destroyForcibly()
        assertTrue(p.waitFor(30, TimeUnit.SECONDS))
        reader.join(5_000)

        //  مثلِ برنامهٔ تازه‌باز: دفتر خراب نیست
        val d = S10KillChild.json.decodeFromString(ShopData.serializer(), ledger.readText())
        val keys = d.sales.map { it.id }
        assertEquals("دورِ $round: فاکتورِ دوتایی", keys.size, keys.toSet().size)
        assertTrue("دورِ $round: فاکتورِ ثبت‌شده گم شد", d.sales.size >= lastSales)
        //  «ثبت شد» ِ دیده‌شده هرگز با مرگِ فرایند نمی‌رود
        val lost = synchronized(acked) { acked.filter { it !in keys } }
        assertTrue("دورِ $round: فاکتورِ «ثبت شد» گم شد: $lost", lost.isEmpty())
        lastSales = d.sales.size
        S10KillChild.check(d, "دورِ $round")
        val draftFile = File(dir, S10KillChild.DRAFT)
        val decision = SaleDraftRules.decide(SaleDraftCodec.decode(if (draftFile.exists()) draftFile.readText() else null), d)
        when (decision) {
          DraftRestore.None -> Unit
          is DraftRestore.Posted -> assertTrue(decision.key in keys)
          is DraftRestore.Offer -> {
            killedMidSale++
            assertTrue("دورِ $round: پیش‌نویسِ فاکتورِ ثبت‌شده", decision.draft.key !in keys)
            val got = decision.draft.lines.map { it.productId to it.quantity }
            assertEquals("دورِ $round: ردیف‌های پیش‌نویس", S10KillChild.lines.take(got.size), got)
          }
        }
      }
      //  دورِ آخر: بازیابیِ پیش‌نویس و چند فاکتورِ دیگر، بی مرگ ⇒ همه یک بار
      val p = child(dir, "end", stopAfter = 2)
      assertTrue(p.waitFor(60, TimeUnit.SECONDS))
      assertEquals(0, p.exitValue())
      val d = S10KillChild.json.decodeFromString(ShopData.serializer(), ledger.readText())
      assertEquals(d.sales.size, d.sales.map { it.id }.toSet().size)
      S10KillChild.check(d, "دورِ آخر")
      assertTrue("کمتر از پیش", d.sales.size >= lastSales + 2)
      assertTrue("هیچ مرگی وسطِ فاکتور نیفتاد ($killedMidSale)", killedMidSale >= 1)
    } finally {
      dir.deleteRecursively()
    }
  }

  private fun child(dir: File, tag: String, stopAfter: Int = 0): Process {
    val bin = File(System.getProperty("java.home"), "bin")
    val java = (File(bin, "java.exe").takeIf { it.isFile } ?: File(bin, "java")).path
    //  classpath ِ آزمون در پروندهٔ آرگومان (`@file`، جاوا ۹+): خطِ فرمانِ ویندوز سقفِ ۳۲K نویسه دارد
    val cp = (System.getProperty("java.class.path").split(File.pathSeparator) +
      listOf(S10KillChild::class.java, DiskWrite::class.java, Json::class.java, Unit::class.java)
        .mapNotNull { runCatching { File(it.protectionDomain.codeSource.location.toURI()).path }.getOrNull() })
      .filter { it.isNotBlank() }.distinct().joinToString(File.pathSeparator) { it.replace('\\', '/') }
    val args = File(dir, "child-$tag.args").apply { writeText("-cp \"$cp\"\n") }
    return ProcessBuilder(java, "-Xmx256m", "-Dfile.encoding=UTF-8", "@${args.path}", S10KillChild::class.java.name, dir.path, tag, stopAfter.toString())
      .redirectErrorStream(true).start()
  }

  private companion object {
    const val ROUNDS = 12
  }
}

/** فرایندِ فروش برای [S10KillDesktopTest]: پیش‌نویسِ مانده را مثلِ برنامه بازیابی می‌کند و پشتِ هم می‌فروشد */
object S10KillChild {
  const val LEDGER = "shop-data.json"
  const val DRAFT = "sale-draft.json"
  const val STOCK = 1_000_000.0
  val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }
  private val products = listOf(
    Product(id = "p1", name = "برنج", unit = "کیلوگرم", salePrice = 120.0),
    Product(id = "p2", name = "روغن", unit = "عدد", salePrice = 150.0),
    Product(id = "p3", name = "چای", unit = "عدد", salePrice = 90.0),
    Product(id = "p4", name = "صابون", unit = "عدد", salePrice = 25.0),
  )
  val start = ShopData(products = products, warehouseEntries = products.map { WarehouseEntry(id = "w${it.id}", productId = it.id, units = STOCK) })
  val lines = listOf("p1" to 2.5, "p2" to 1.0, "p3" to 3.0, "p4" to 2.0)

  /**
   *  هر فاکتور کامل: فاکتورِ تازه ۴ قلم؛ فاکتورِ بازیابی‌شده از پیش‌نویس همان ردیف‌های اسکن‌شده (پیشوندِ [lines]، دست‌کم یکی).
   *  موجودیِ هر کالا = آغاز − جمعِ قلم‌های همان کالا؛ پرداخت = جمعِ فاکتور.
   */
  fun check(d: ShopData, where: String) {
    for (s in d.sales) {
      val got = d.saleItems.filter { it.saleId == s.id }.map { it.productId to it.quantity }
      assertTrue("$where: ${s.id} بی قلم", got.isNotEmpty())
      assertEquals("$where: قلم‌های ${s.id}", lines.take(got.size), got)
      assertEquals("$where: پرداختِ ${s.id}", s.finalTotal, s.paidAmount, 0.0)
    }
    assertEquals("$where: قلمِ بی فاکتور", d.sales.map { it.id }.toSet(), d.saleItems.map { it.saleId }.toSet())
    for ((pid, q) in lines) {
      val sold = d.saleItems.count { it.productId == pid } * q
      assertEquals("$where: موجودیِ $pid", STOCK - sold, ShopStore.stock(d, pid), 1e-6)
    }
  }

  @JvmStatic
  fun main(args: Array<String>) = runBlocking {
    val dir = File(args[0])
    val tag = args[1]
    val stopAfter = args.getOrNull(2)?.toIntOrNull() ?: 0
    val ledger = File(dir, LEDGER)
    val draftFile = File(dir, DRAFT)
    var d = json.decodeFromString(ShopData.serializer(), ledger.readText())
    var seq = 0
    suspend fun post(key: String, cart: List<SalesEngine.CartLine>) {
      SalePost().run(key, { k -> SalesEngine.record(d, cart, SalesEngine.Checkout(), "2026-11-12", 5_000, { "$tag-${seq++}" }, saleId = k) },
        { data -> DiskWrite.text(ledger, json.encodeToString(ShopData.serializer(), data)); d = data; true }) { draftFile.delete() }
    }
    //  باز شدن: همان تصمیمِ برنامه دربارهٔ پیش‌نویسِ مانده
    when (val r = SaleDraftRules.decide(SaleDraftCodec.decode(if (draftFile.exists()) draftFile.readText() else null), d)) {
      is DraftRestore.Offer -> post(r.draft.key ?: "S10K-$tag-0", r.draft.cart())
      is DraftRestore.Posted -> draftFile.delete()
      DraftRestore.None -> Unit
    }
    var done = 0
    while (stopAfter == 0 || done < stopAfter) {
      val key = "S10K-$tag-${d.sales.size + 1}"
      val cart = ArrayList<SalesEngine.CartLine>()
      for ((pid, q) in lines) {
        cart += SalesEngine.CartLine(pid, q)
        DiskWrite.text(draftFile, SaleDraftCodec.encode(SaleDraft.of(key, cart, null, 1)))
      }
      post(key, cart)
      done++
      println("posted $key")
      System.out.flush()
    }
  }
}
