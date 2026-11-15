package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.ui.AppNav
import ir.vil3ntec.tohid.ui.screens.DeskMenu
import ir.vil3ntec.tohid.ui.screens.ProductListModel
import org.junit.After
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 *  PERF-DESK — سنجهٔ دستیِ پنجرهٔ کامپیوتر ([DeskShell]) با دفترِ بزرگ: نخستین قاب، بی‌کاریِ خانه، رفتن به هر بخشِ منوی
 *  کناری، تایپ در جست‌وجوی سریع (Ctrl+K) و چرخِ ماوس روی بلندترین فهرست. فقط با `TOHID_DESK_SPEED=1`:
 *
 *    TOHID_DESK_SPEED=1 TOHID_BIG_LEDGER_HEAP=3g gradle -p desktop test --tests '*DeskSpeedBenchTest*' --rerun …
 *
 *  `TOHID_DESK_SPEED_SALES` اندازهٔ دیگر؛ خروجی stdout و `desktop/build/desk-speed.txt`. عددِ این‌جا رَسترِ نرم‌افزاریِ Skia
 *  روی JVM ِ همین ماشین است (همان رَستری که ویندوزِ بی کارتِ گرافیکِ سازگار می‌گیرد)؛ کارتِ گرافیک کشیدن را ارزان‌تر می‌کند
 *  ولی شمارِ قاب و ترکیب همان است.
 */
class DeskSpeedBenchTest {

  private fun wanted(): Boolean = System.getenv("TOHID_DESK_SPEED")?.isNotBlank() == true

  @After fun release() { ShopStore.index(ShopData()); ShopStore.salesIndex(ShopData()) }

  private fun load(): String = runCatching { File("/proc/loadavg").readText().split(' ').take(3).joinToString(" ") }.getOrDefault("?")
  private fun ms(ns: Long) = ns / 1e6
  private fun f(x: Double) = "%.1f".format(Locale.ROOT, x)

  private val log = StringBuilder()
  private fun say(s: String) { println(s); log.appendLine(s); write() }
  private fun write() { runCatching { File(System.getProperty("user.dir"), "build/desk-speed.txt").apply { parentFile.mkdirs() }.writeText(log.toString()) } }

  /** GC از آغازِ آزمون: شمار و جمعِ زمان (میلی‌ثانیه) */
  private fun gc(): String {
    val beans = java.lang.management.ManagementFactory.getGarbageCollectorMXBeans()
    return beans.joinToString(" · ") { "${it.name} ${it.collectionCount}× ${it.collectionTime}ms" }
  }

  private fun heapMb(): Long { System.gc(); val rt = Runtime.getRuntime(); return (rt.totalMemory() - rt.freeMemory()) / 1_000_000 }

  @Test fun `پنجرهٔ کامپیوتر با دفترِ بزرگ — قاب، بی‌کاری، بخش‌ها، تایپ، چرخ`() {
    assumeTrue("فقط با TOHID_DESK_SPEED", wanted())
    val sales = System.getenv("TOHID_DESK_SPEED_SALES")?.trim()?.toIntOrNull()?.coerceIn(1_000, 500_000) ?: ScreenSpeed.FULL.sales
    val only = System.getenv("TOHID_DESK_SPEED_ONLY")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.toSet()
    val heap0 = heapMb()
    val d = ScreenSpeed.shop(ScreenSpeed.FULL.copy(sales = sales))
    val rt = Runtime.getRuntime()
    say("[desk-speed] products=${d.products.size} debtors=${d.debtors.size} debtTx=${d.transactions.size} sales=${d.sales.size} saleItems=${d.saleItems.size} " +
      "moves=${d.stockMovements.size} · ${rt.availableProcessors()} cores · JVM ${System.getProperty("java.version")} · max heap ${rt.maxMemory() / 1_000_000} MB · " +
      "window ${DeskShell.LAPTOP_W}×${DeskShell.LAPTOP_H} dp · load ${load()}")
    say("[heap] ledger in memory ≈ ${heapMb() - heap0} MB")
    logic(d)
    val store = DeskShell.loadedStore(d)
    //  نشستِ باز (صاحب‌کار وارد شده است) روی سرورِ ساختگیِ 127.0.0.1 و «بی شبکه»: هیچ درخواستی از این ماشین بیرون نمی‌رود
    ir.vil3ntec.tohid.desktop.TestBackend().use { backend ->
      backend.online = false
      DeskShell(store).use { s ->
        val l0 = load()
        val open = s.open()
        val settle = s.run { settle(maxMs = 6_000) }
        say("[open] first frame ${f(ms(open))} ms · settle ${f(ms(settle.wallNs))} ms (${settle.frames} frames, busy ${f(ms(settle.busyNs))} ms, max ${f(ms(settle.maxNs))}, settled=${settle.settled}) · load $l0 ⇒ ${load()}")
        say("[gc] before shell work: ${gc()}")
        say("[heap] after shell ≈ ${heapMb() - heap0} MB")
        if (only == null || "idle" in only) idle(s, "home")
        if (only == null || "sections" in only) sections(s)
        if (only == null || "unfocused" in only) unfocused(s)
        if (only == null || "palette" in only) palette(s)
        if (only == null || "till" in only) till(s)
        if (only == null || "scroll" in only) scroll(s)
        say("[gc] ${gc()}")
        say("[heap] end ≈ ${heapMb() - heap0} MB")
      }
    }
    write()
  }

  /** کارهای رشتهٔ صفحه که پوستهٔ کامپیوتر با هر عوض شدنِ دفتر یا هر کلید می‌کند — میانهٔ گرم */
  private fun logic(d: ShopData) {
    val today = ir.vil3ntec.tohid.todayIso()
    fun t(name: String, block: () -> Any?) {
      val cold = PosSpeed.nanos { block() }
      repeat(2) { block() }
      val sampler = StackSampler(Thread.currentThread())
      val xs = List(5) { PosSpeed.nanos { block() } }.sorted()
      sampler.close()
      say("[logic] %-36s cold %8s ms · warm median %8s ms".format(Locale.ROOT, name, f(ms(cold)), f(ms(xs[2]))))
      if (xs[2] > 20_000_000) log.append(sampler.report(5))
    }
    t("DashboardEngine.board (header)") { ir.vil3ntec.tohid.data.DashboardEngine.board(d, today, 7) }
    t("CashShift.current (status bar)") { ir.vil3ntec.tohid.data.CashShift.current(d) }
    t("today sales filter (status bar)") { d.sales.filter { it.status != "cancelled" && it.date == today } }
    t("HomeTrend.of (desk home)") { ir.vil3ntec.tohid.ui.v3.shell.HomeTrend.of(d.sales, today, System.currentTimeMillis()) }
    t("DashboardNumbers.of (home view)") { ir.vil3ntec.tohid.ui.v3.shell.DashboardNumbers.of(d, today) }
    val q = System.getenv("TOHID_DESK_SPEED_QUERY") ?: "برنج"
    for (i in 1..q.length) t("SearchIndex.search '${q.take(i)}'") { ir.vil3ntec.tohid.ui.v3.shell.SearchIndex.search(d, q.take(i)) }
    t("SearchIndex.search '154'") { ir.vil3ntec.tohid.ui.v3.shell.SearchIndex.search(d, "154") }
    val idx = ShopStore.index(d)
    val stockOf: (String) -> Double = { idx.stock(it) }
    t("ProductListModel.rows '' (products)") { ProductListModel.rows(d.products, stockOf, "", ProductListModel.Tab.ALL, null, ProductListModel.Sort.NAME, true) }
    t("ProductListModel.rows '${q.take(2)}'") { ProductListModel.rows(d.products, stockOf, q.take(2), ProductListModel.Tab.ALL, null, ProductListModel.Sort.NAME, true) }
    t("ProductListModel.kpis+counts") { ProductListModel.kpis(d.products, stockOf, System.currentTimeMillis()); ProductListModel.counts(d.products, stockOf) }
  }

  private fun idle(s: DeskShell, where: String) {
    val l0 = load()
    val i = s.run { idle(3_000) }
    say("[idle:$where] ${f(i.framesPerSec)} frames/s · ${f(i.recompPerSec)} recompositions/s · busy ${f(ms(i.busyNs))} ms in ${i.ms} ms · load $l0 ⇒ ${load()}")
    if (i.top.isNotEmpty()) say("    top: " + i.top.joinToString(" · ") { "${it.first}×${it.second}" })
  }

  /** صندوق باز، پنجره بی فوکوس (صاحب‌کار در برنامهٔ دیگری است): هیچ قابی نباید کشیده شود */
  private fun unfocused(s: DeskShell) {
    s.run { AppNav.go("sale"); settle(maxMs = 2_500) }
    s.run { focus(false) }
    idle(s, "sale-unfocused")
    s.run { focus(true); settle(maxMs = 1_000) }
  }

  private fun sections(s: DeskShell) {
    val targets = DeskMenu.groups(true, ordersLive = false, storefrontLive = false, deliveryLive = false)
      .flatMap { it.items }.filter { it.state == DeskMenu.State.LIVE }.map { it.target }.distinct()
    //  دو دور: «سرد» (نخستین بار در این JVM ⇒ بارِ کلاس و JIT هم در عدد است، همان نخستین باز کردنِ هر بخش پس از باز شدنِ
    //  برنامه) و «گرم» (همان بخش بارِ دوم — کارِ خودِ صفحه)
    for (pass in listOf("cold", "warm")) for (t in targets + "home") {
      val l0 = load()
      val sampler = StackSampler(s.thread)
      val r = s.run {
        AppNav.go(t)
        settle(maxMs = 2_500)
      }
      sampler.close()
      say("[section:$pass] %-14s first %7s ms · until still %7s ms · %3d frames · busy %7s ms · max %6s ms%s · load %s ⇒ %s".format(
        Locale.ROOT, t, f(ms(r.firstNs)), f(ms(r.wallNs)), r.frames, f(ms(r.busyNs)), f(ms(r.maxNs)), if (r.settled) "" else " (never still)", l0, load()))
      if (r.maxNs > 50_000_000) log.append(sampler.report(6))
      write()
      if (!r.settled && pass == "warm") idle(s, t)
    }
  }

  private fun palette(s: DeskShell) {
    s.run { AppNav.go("home"); settle() }
    val opened = s.run { AppNav.palette.value = true; settle() }
    say("[palette] open ${f(ms(opened.wallNs))} ms (${opened.frames} frames, busy ${f(ms(opened.busyNs))} ms)")
    val q = System.getenv("TOHID_DESK_SPEED_QUERY") ?: "برنج"
    val per = ArrayList<Long>()
    val l0 = load()
    val firsts = ArrayList<Long>()
    for (ch in q) {
      val fs = s.run { type(ch); frames(600) }
      firsts += fs.firstOrNull() ?: 0L
      per += fs.sum()
      say("[key] '${ch}' first frame ${f(ms(fs.firstOrNull() ?: 0L))} ms · busy in 600 ms ${f(ms(fs.sum()))} ms · ${fs.size} frames")
    }
    say("[palette] keystroke first-frame median ${f(ms(firsts.sorted()[firsts.size / 2]))} ms · max ${f(ms(firsts.max()))} · busy median ${f(ms(per.sorted()[per.size / 2]))} ms · max ${f(ms(per.max()))} ms · load $l0 ⇒ ${load()}")
    s.run { AppNav.palette.value = false; settle() }
  }

  /** کادرِ اسکن/جست‌وجوی صندوق (کامپیوتر: همان کادری که با باز شدنِ صندوق فوکوس دارد) */
  private fun till(s: DeskShell) {
    val opened = s.run { AppNav.go("sale"); settle() }
    say("[till] open ${f(ms(opened.wallNs))} ms (${opened.frames} frames, busy ${f(ms(opened.busyNs))} ms)")
    val q = System.getenv("TOHID_DESK_SPEED_QUERY") ?: "برنج"
    val per = ArrayList<Long>()
    val l0 = load()
    val firsts = ArrayList<Long>()
    for (ch in q) {
      val fs = s.run { type(ch); frames(600) }
      firsts += fs.firstOrNull() ?: 0L
      per += fs.sum()
      say("[till-key] '${ch}' first frame ${f(ms(fs.firstOrNull() ?: 0L))} ms · busy in 600 ms ${f(ms(fs.sum()))} ms · ${fs.size} frames")
    }
    say("[till] keystroke first-frame median ${f(ms(firsts.sorted()[firsts.size / 2]))} ms · max ${f(ms(firsts.max()))} · busy median ${f(ms(per.sorted()[per.size / 2]))} ms · load $l0 ⇒ ${load()}")
    s.run { repeat(q.length) { key(java.awt.event.KeyEvent.VK_BACK_SPACE) }; settle() }
  }

  private fun scroll(s: DeskShell) {
    for (t in listOf("products", "sales", "debtors")) {
      s.run { AppNav.go(t); settle() }
      val l0 = load()
      //  ۳۰ تیکِ چرخ، هر ۴۸ میلی‌ثانیه یکی (چرخاندنِ پیوسته)، روی میانهٔ بخشِ محتوا؛ مدتِ هر قاب در همان ۱٫۵ ثانیه
      val fs = s.run {
        frames(1_500) { i -> if (i % 3 == 0 && i / 3 < 30) wheel(DeskShell.LAPTOP_W / 2f, DeskShell.LAPTOP_H * 0.6f, 1f) }
      }.sorted()
      if (fs.isEmpty()) { say("[scroll:$t] no frames (nothing scrolled) · load $l0"); continue }
      val jank = fs.count { it > 16_700_000 }
      say("[scroll:$t] ${fs.size} frames in 1.5 s · frame p50 ${f(ms(fs[fs.size / 2]))} ms · p90 ${f(ms(fs[fs.size * 9 / 10]))} · max ${f(ms(fs.last()))} · >16.7ms: $jank · load $l0 ⇒ ${load()}")
    }
  }
}
