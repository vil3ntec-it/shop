package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.todayIso
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 *  PERF-SCREEN — «هر بخش زیرِ ۱۰۰ میلی‌ثانیه» (`docs/PERFORMANCE-fa.md`، قاعده در `docs/RULES-fa.md`).
 *
 *  ── اجرا ──────────────────────────────────────────────────────────────
 *   • همیشه (CI): ۲۰ هزار کالا، ۵ هزار مشتری، ۱۰ هزار فاکتور ([ScreenSpeed.CI]، در heap ِ پیش‌فرضِ ۵۱۲ مگ) — منطقِ هر بخش
 *     ([ScreenLogic]): میانهٔ گرمِ چند بار باید زیرِ [LOGIC_BUDGET_MS] باشد. سقف نصفِ بودجهٔ ۱۰۰ میلی‌ثانیه است تا لرزشِ
 *     رانرِ CI سرخ نکند و پس‌رفتِ واقعی (گشتنِ کلِ دفتر یا مرتب کردنِ همه چیز با هر باز شدن) سرخ کند.
 *   • سنجهٔ کامل (دستی؛ در CI هرگز): `TOHID_SCREEN_SPEED=1` — ۲۰ هزار کالا و ۵۰ هزار فاکتور ([ScreenSpeed.FULL]؛
 *     `TOHID_SCREEN_SPEED_SALES=…` برای اندازهٔ دیگر)، منطق + نخستین قابِ Compose ِ هر مسیر ([ScreenFrame]) با نمونه‌برداریِ پشته:
 *       TOHID_SCREEN_SPEED=1 TOHID_BIG_LEDGER_HEAP=1800m gradle -p desktop test --tests '*ScreenSpeedBenchTest*' …
 *     خروجی: جدول در stdout و `desktop/build/screen-speed.txt`.
 *   • عددِ این‌جا JVM ِ کامپیوتر است؛ گوشیِ ارزان چند برابر کُندتر است (جدا سنجیده نشده).
 */
class ScreenSpeedBenchTest {

  companion object {
    /** سقفِ میانهٔ گرمِ منطقِ هر بخش روی دادهٔ CI — نصفِ بودجهٔ ۱۰۰ میلی‌ثانیهٔ صفحه */
    const val LOGIC_BUDGET_MS = 50.0
  }

  private fun wanted(): Boolean = System.getenv("TOHID_SCREEN_SPEED")?.isNotBlank() == true

  /** جدول‌های یک‌خانه‌ایِ `ShopStore` دفترِ بزرگ را پس از آزمون نگه ندارند (heap ِ آزمون‌های پسین) */
  @After fun release() { ShopStore.index(ShopData()); ShopStore.salesIndex(ShopData()) }

  private fun load(): String = runCatching { File("/proc/loadavg").readText().split(' ').take(3).joinToString(" ") }.getOrDefault("?")

  class Row(val name: String, val cold: Double, val median: Double, val max: Double, val load: String) {
    override fun toString() = "%-22s cold %8.1f  warm-median %8.1f  warm-max %8.1f ms   load %s".format(Locale.ROOT, name, cold, median, max, load)
  }

  private fun ms(ns: Long) = ns / 1e6

  /** نخستین بار (سرد، با همان جدول‌هایی که صفحه‌های پیشین گرم کرده‌اند) و سپس [warm] بارِ کنار گذاشته و [runs] بارِ شمرده */
  private fun logic(c: ScreenLogic.Ctx, name: String, f: (ScreenLogic.Ctx) -> Any?, warm: Int, runs: Int, sampler: Boolean, log: StringBuilder): Row {
    System.gc()
    val l0 = load()
    val cold = PosSpeed.nanos { f(c) }
    repeat(warm) { f(c) }
    val s = if (sampler) StackSampler(Thread.currentThread()) else null
    val xs = List(runs) { PosSpeed.nanos { f(c) } }.sorted()
    s?.close()
    val row = Row(name, ms(cold), ms(xs[xs.size / 2]), ms(xs.last()), "$l0 ⇒ ${load()}")
    log.appendLine("[logic] $row")
    println("[logic] $row")
    s?.let { log.append(it.report(8)) }
    return row
  }

  private fun ctx(d: ShopData) = ScreenLogic.Ctx(d, todayIso(), System.currentTimeMillis())

  private fun write(name: String, text: String) {
    runCatching { File(System.getProperty("user.dir"), "build/$name").apply { parentFile.mkdirs() }.writeText(text) }
  }

  private fun header(d: ShopData): String {
    val rt = Runtime.getRuntime()
    return "[screen-speed] products=${d.products.size} debtors=${d.debtors.size} debtTx=${d.transactions.size} sales=${d.sales.size} " +
      "saleItems=${d.saleItems.size} moves=${d.stockMovements.size} purchases=${d.purchases.size} · ${rt.availableProcessors()} cores · " +
      "JVM ${System.getProperty("java.version")} · max heap ${rt.maxMemory() / 1_000_000} MB · load ${load()}\n"
  }

  /** همیشه: منطقِ هر بخش روی دادهٔ CI، میانهٔ گرمِ ۷ بار (پس از ۵ بارِ گرم‌کردن) زیرِ [LOGIC_BUDGET_MS] یا سقفِ بیرون‌از‌رشته‌اش */
  @Test(timeout = 600_000) fun `منطقِ هر بخش با ۲۰ هزار کالا — زیرِ بودجه`() {
    val d = ScreenSpeed.shop(ScreenSpeed.CI)
    val c = ctx(d)
    val log = StringBuilder(header(d))
    val rows = ScreenLogic.SECTIONS.map { (name, f) -> logic(c, name, f, warm = 5, runs = 7, sampler = false, log = log) }
    write("screen-speed-ci.txt", log.toString())
    val over = rows.filter { it.median > (ScreenLogic.OFF_MAIN_BUDGET_MS[it.name] ?: LOGIC_BUDGET_MS) }
    assertTrue("بیش از سقف (رشتهٔ صفحه ${LOGIC_BUDGET_MS}ms، بیرون از آن ${ScreenLogic.OFF_MAIN_BUDGET_MS}؛ میانهٔ گرم):\n" + over.joinToString("\n"), over.isEmpty())
  }

  /** دستی: `TOHID_SCREEN_SPEED=1` — منطق و نخستین قابِ هر بخش روی دفترِ بزرگ */
  @Test fun `سنجهٔ کاملِ هر بخش — منطق و نخستین قاب`() {
    assumeTrue("فقط با TOHID_SCREEN_SPEED", wanted())
    val sales = System.getenv("TOHID_SCREEN_SPEED_SALES")?.trim()?.toIntOrNull()?.coerceIn(1_000, 500_000) ?: ScreenSpeed.FULL.sales
    val only = System.getenv("TOHID_SCREEN_SPEED_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
    val d = ScreenSpeed.shop(ScreenSpeed.FULL.copy(sales = sales))
    val log = StringBuilder(header(d))
    val c = ctx(d)
    for ((name, f) in ScreenLogic.SECTIONS) if (only == null || name in only) logic(c, name, f, warm = 2, runs = 5, sampler = true, log = log)
    write("screen-speed.txt", log.toString())
    val store = ScreenFrame.storeOf(d)
    ScreenFrame.signedIn { frames(d, store, only, log) }
  }

  private fun frames(d: ShopData, store: ShopStore, only: Set<String>?, log: StringBuilder) {
    for ((name, path) in ScreenSpeed.routes(d)) {
      if (only != null && name !in only) continue
      System.gc()
      val l0 = load()
      val cold = ScreenFrame.route(path, store)
      val s = StackSampler(ScreenFrame.worker)
      val warm = List(3) { ScreenFrame.route(path, store) }
      s.close()
      val firsts = warm.map { it.first }.sorted()
      val row = Row(name, ms(cold.first), ms(firsts[1]), ms(firsts.last()), "$l0 ⇒ ${load()}")
      log.appendLine("[frame] $row   next-frame-max ${"%.1f".format(Locale.ROOT, ms(warm.maxOf { it.nextMax }))} ms")
      log.append(s.report(8))
      println("[frame] $row")
      write("screen-speed.txt", log.toString())
    }
  }
}
