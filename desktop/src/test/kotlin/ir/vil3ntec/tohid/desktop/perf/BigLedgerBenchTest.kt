package ir.vil3ntec.tohid.desktop.perf

import ir.vil3ntec.tohid.data.AtRest
import ir.vil3ntec.tohid.data.LedgerArchive
import ir.vil3ntec.tohid.data.LedgerMerge
import ir.vil3ntec.tohid.data.LedgerSummary
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.desktop.DesktopContext
import ir.vil3ntec.tohid.sync.v1.LedgerDiff
import ir.vil3ntec.tohid.sync.v1.OpHash
import ir.vil3ntec.tohid.sync.v1.OpQueue
import ir.vil3ntec.tohid.sync.v1.SyncStateV1
import ir.vil3ntec.tohid.sync.v1.SyncV1Engine
import ir.vil3ntec.tohid.sync.v1.SyncV1Worker
import ir.vil3ntec.tohid.sync.v3.SyncChoice
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.util.Locale

/**
 *  PERF-LEDGER — «دکانی با چند سال داده باید تند و امن بماند» (`docs/PERF-LEDGER-fa.md`).
 *
 *  هر اندازه: ساختنِ دفترِ قطعی ([BigLedger]) ⇐ نوشتن روی دیسک ⇐ **بار** (`ShopStore.load`) ⇐ نخستین ذخیره (سایهٔ
 *  پایه) ⇐ **ذخیرهٔ یک فروش** (رایج‌ترین کار؛ میانه‌ی چند بار، با شکستنِ گام‌ها) ⇐ **ادغامِ `save(base, next)`**
 *  (صفحهٔ کهنه پس از نوشتنِ همتا) ⇐ **تفاضلِ همگام‌سازی** ⇐ **فشرده‌سازیِ بایگانی** ⇐ حافظه.
 *
 *  ── اجرا ──────────────────────────────────────────────────────────────
 *   • همیشه: فقط [BigLedger.SMOKE] (کوچک، زیرِ یک دقیقه) تا سنجه نپوسد — هیچ عددی را سد نمی‌کند.
 *   • سنجهٔ واقعی (دستی؛ در CI هرگز):
 *       TOHID_BIG_LEDGER=50000,200000,500000 TOHID_BIG_LEDGER_HEAP=6g \
 *         gradle -p desktop test --tests '*BigLedgerBenchTest*' …
 *     خروجی: جدولِ متنی در stdout و `desktop/build/big-ledger-bench.json`.
 *   • اندازه‌ای که جای دیسکش نیست (دفتر + فایلِ کناری + سایه) **رد می‌شود و گفته می‌شود**، نه نیمه‌کاره.
 */
class BigLedgerBenchTest {

  private val ctx = DesktopContext
  private val ledger get() = File(ctx.filesDir, "shop-data.json")
  private val engine get() = SyncV1Engine.of(ctx)
  private lateinit var account: String

  /** همان پیکربندیِ `ShopStore.json` — بایت‌های دیسک همین‌اند */
  private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; isLenient = true }

  private fun clean() {
    AtRest.setWanted(ctx, false)
    ctx.filesDir.listFiles().orEmpty()
      .filter { it.name.startsWith("shop-data.json") || it.name.startsWith("ledger-archive.json") || it.name.startsWith("ledger-summary") || it.name.startsWith("bench-") }
      .forEach { it.deleteRecursively() }
  }

  @Before fun before() {
    clean()
    SyncChoice.set(ctx, SyncChoice.V1, 0)
    account = "bench-${System.nanoTime()}"
    SyncStateV1.setCurrentAccount(ctx, account)
    engine.rebind()
  }

  @After fun after() {
    SyncV1Worker.cancel(ctx)
    //  سایهٔ بزرگ از prefs ِ مشترکِ آزمون‌ها بیرون — وگرنه هر `apply` ِ بعدی همه را از نو می‌نوشت
    SyncStateV1(ctx, account).reset()
    OpQueue(ctx, account).clear()
    SyncStateV1.setCurrentAccount(ctx, "")
    engine.rebind()
    clean()
  }

  /* ------------------------------ سنجه ------------------------------ */

  /** بیشینهٔ حافظهٔ heap در طولِ کار، با نمونه‌برداری (هر ۲ میلی‌ثانیه) — نه جمعِ بیشینهٔ poolها */
  private class Peak : AutoCloseable {
    private val rt = Runtime.getRuntime()
    @Volatile private var run = true
    @Volatile var max = 0L
    val base: Long
    private val t: Thread
    init {
      System.gc(); System.gc()
      base = rt.totalMemory() - rt.freeMemory()
      max = base
      t = Thread {
        while (run) {
          val u = rt.totalMemory() - rt.freeMemory()
          if (u > max) max = u
          try { Thread.sleep(2) } catch (_: InterruptedException) { break }
        }
      }.apply { isDaemon = true; start() }
    }
    override fun close() { run = false; t.join() }
    val extraMb: Double get() = (max - base) / 1e6
  }

  private inline fun <T> ms(block: () -> T): Pair<T, Double> {
    val t0 = System.nanoTime()
    val v = block()
    return v to (System.nanoTime() - t0) / 1e6
  }

  private fun heapMb(): Double {
    System.gc(); System.gc()
    val rt = Runtime.getRuntime()
    return (rt.totalMemory() - rt.freeMemory()) / 1e6
  }

  private fun median(xs: List<Double>): Double = xs.sorted().let { it[it.size / 2] }

  private fun sha(s: String): String = OpHash.sha256Hex(s)

  /** یک ردیفِ نتیجه */
  private class Row(val name: String, val ms: Double, val note: String = "")

  /** ساختن و نوشتن در تابعِ جدا: دفترِ ساخته و متنش پیش از «بار» از دست می‌روند و حافظهٔ بار فقط مالِ خودش است */
  private fun prepare(size: BigLedger.Size, rows: MutableList<Row>, out: MutableMap<String, Any>): Boolean {
    val (gen, genMs) = ms { BigLedger.make(size) }
    out["counts"] = "kala=${gen.products.size} sales=${gen.sales.size} items=${gen.saleItems.size} customers=${gen.debtors.size} tx=${gen.transactions.size} moves=${gen.stockMovements.size}"
    rows += Row("generate", genMs, out["counts"].toString())
    //  ذخیرهٔ سرد: کلِ دفتر یک‌جا (همان `writeToDisk`: رمزگذاری خاموش ⇒ همان بایت‌ها)
    val (text, encMs) = ms { json.encodeToString(gen) }
    val bytes = text.toByteArray(Charsets.UTF_8).size.toLong()
    out["fileMB"] = bytes / 1e6
    rows += Row("cold write (encode)", encMs, "%.1f MB".format(Locale.ROOT, bytes / 1e6))
    val need = bytes * 5 + 300_000_000L  // دفتر + کناری + سایه (prefs و کناری‌اش) + بایگانی
    val free = ctx.filesDir.usableSpace
    val cpuOnly = System.getenv("TOHID_BIG_LEDGER_CPU_ONLY") == "1"
    if (free < need || cpuOnly) {
      out["skipped"] = if (cpuOnly) "TOHID_BIG_LEDGER_CPU_ONLY — CPU stages only" else "disk: need ${need / 1_000_000}MB, free ${free / 1_000_000}MB — CPU stages only"
      println("[big-ledger] ${size.label}: ${out["skipped"]}")
      stagesOnly(gen, rows, out)
      return false
    }
    val (_, writeMs) = ms { AtRest.writeText(ctx, ledger, text, keepPrevious = true) }
    rows += Row("cold write (disk+fsync)", writeMs)
    return true
  }

  /** دیسک بس نیست: همان گام‌های پردازنده روی دفترِ ساخته (بی دیسک، بی `ShopStore`) — گفته می‌شود که چنین است */
  private fun stagesOnly(d: ShopData, rows: MutableList<Row>, out: MutableMap<String, Any>) {
    val next = BigLedger.sell(d, 900)
    val shadowText = ir.vil3ntec.tohid.sync.v1.ShadowText.of(LedgerDiff.shadowOf(ir.vil3ntec.tohid.sync.v1.LedgerTrees().toJson(d))).text
    stages(d, next, shadowText, rows, out)
    val theirsDisk = json.decodeFromString<ShopData>(json.encodeToString(BigLedger.sell(d, 950, tag = 5)))
    val (_, mg) = ms { LedgerMerge.mergeReport(d, next, theirsDisk) }
    rows += Row("merge: LedgerMerge.mergeReport", mg)
    out["mergeMs"] = mg
  }

  private fun runSize(size: BigLedger.Size, singles: Int): Map<String, Any> {
    val rows = ArrayList<Row>()
    val out = LinkedHashMap<String, Any>()
    out["size"] = size.label
    out["sales"] = size.sales
    try {
      if (prepare(size, rows, out)) measure(size, singles, rows, out)
    } catch (e: OutOfMemoryError) {
      out["oom"] = "OutOfMemoryError after: ${rows.lastOrNull()?.name}"
      rows += Row("OUT OF MEMORY", 0.0, "after «${rows.lastOrNull()?.name}» (max heap %.0f MB)".format(Locale.ROOT, Runtime.getRuntime().maxMemory() / 1e6))
    }
    println("\n[big-ledger] ===== ${size.label} — ${out["counts"]} =====")
    println("[big-ledger] file %.1f MB · shadow %.1f MB (UTF-16) · ledger in heap %.0f MB · max heap %.0f MB".format(Locale.ROOT, (out["fileMB"] as? Double) ?: 0.0, (out["shadowMB"] as? Double) ?: 0.0, (out["heapLedgerMB"] as? Double) ?: 0.0, Runtime.getRuntime().maxMemory() / 1e6))
    for (r in rows) println("[big-ledger] %-44s %10.1f ms  %s".format(Locale.ROOT, r.name, r.ms, r.note))
    out["rows"] = rows.map { mapOf("name" to it.name, "ms" to it.ms, "note" to it.note) }
    return out
  }

  private fun measure(size: BigLedger.Size, singles: Int, rows: MutableList<Row>, out: MutableMap<String, Any>) {
    val heap0 = heapMb()

    //  ---- بار ----
    val store = ShopStore(ctx)
    val loadPeak = Peak()
    val (_, loadMs) = ms { runBlocking { store.load() } }
    loadPeak.close()
    val heapLoaded = heapMb() - heap0
    rows += Row("load", loadMs, "peak +%.0f MB".format(Locale.ROOT, loadPeak.extraMb))
    out["heapLedgerMB"] = heapLoaded
    assertEquals(size.sales, store.data.value.sales.size)

    //  ---- نخستین ذخیره: سایهٔ پایه (بی op) ----
    val (_, firstMs) = ms { runBlocking { store.save(BigLedger.sell(store.data.value, 0)) } }
    SyncV1Worker.cancel(ctx)
    rows += Row("first save (baseline shadow)", firstMs)
    val shadowChars = SyncStateV1(ctx, account).shadow.length
    out["shadowMB"] = shadowChars * 2 / 1e6

    //  ---- ذخیرهٔ یک فروش ----
    val single = ArrayList<Double>()
    var savePeak = 0.0
    repeat(singles) { i ->
      val next = BigLedger.sell(store.data.value, i + 1, credit = i % 2 == 1)
      val p = Peak()
      val (ok, t) = ms { runBlocking { store.save(next) } }
      p.close()
      SyncV1Worker.cancel(ctx)
      assertTrue("ذخیرهٔ فروش باید بنشیند", ok)
      single += t
      savePeak = maxOf(savePeak, p.extraMb)
    }
    rows += Row("single sale save (median of $singles)", median(single), "min %.0f max %.0f · peak +%.0f MB".format(Locale.ROOT, single.min(), single.max(), savePeak))
    out["singleSaveMs"] = median(single)
    out["singleSavePeakMB"] = savePeak
    val queued = OpQueue(ctx, account).all().size
    out["opsQueued"] = queued

    //  ---- گام‌های همان ذخیره، جدا (همان فراخوانی‌ها، روی همان دفتر) ----
    val cur = store.data.value
    val next = BigLedger.sell(cur, 900)
    stages(cur, next, SyncStateV1(ctx, account).shadow, rows, out)

    //  ---- ادغامِ save(base, next): صفحهٔ کهنه پس از نوشتنِ همتا ----
    val theirsDisk = json.decodeFromString<ShopData>(json.encodeToString(BigLedger.sell(cur, 950, tag = 5)))
    val mergePeak = Peak()
    val (merged, mg) = ms { LedgerMerge.mergeReport(cur, next, theirsDisk) }
    mergePeak.close()
    rows += Row("merge: LedgerMerge.mergeReport", mg, "peak +%.0f MB".format(Locale.ROOT, mergePeak.extraMb))
    assertEquals(cur.sales.size + 2, merged.data.sales.size)
    out["mergeMs"] = mg

    //  همان ادغام از درِ `ShopStore.save`: همتا (نمونهٔ دوم، کارگر) می‌نویسد، صفحه از دفترِ در دستش ذخیره می‌کند
    val held = store.data.value
    val peer = ShopStore(ctx)
    val (_, peerLoad) = ms { runBlocking { peer.load() } }
    rows += Row("  · peer load (2nd instance)", peerLoad)
    runBlocking { peer.save(BigLedger.sell(peer.data.value, 960, tag = 6)) }
    SyncV1Worker.cancel(ctx)
    val stale = BigLedger.sell(held, 970, tag = 7)
    val (okStale, staleMs) = ms { runBlocking { store.save(stale) } }
    SyncV1Worker.cancel(ctx)
    assertTrue(okStale)
    assertEquals(held.sales.size + 2, store.data.value.sales.size)
    rows += Row("merge: ShopStore.save(stale screen)", staleMs)
    out["staleSaveMs"] = staleMs

    //  ---- فشرده‌سازیِ بایگانی (دستیِ تنظیمات: compact ⇐ save) ----
    val before = store.data.value
    val (done, cm) = ms { LedgerArchive.compact(ctx, before, now = BigLedger.NOW).getOrThrow() }
    rows += Row("archive: LedgerArchive.compact", cm, "moved ${done.moved.total}")
    val (_, cs) = ms { runBlocking { store.save(done.data) } }
    SyncV1Worker.cancel(ctx)
    rows += Row("archive: save compacted ledger", cs)
    val after = ArrayList<Double>()
    repeat(singles) { i ->
      val n2 = BigLedger.sell(store.data.value, 980 + i)
      val (_, t) = ms { runBlocking { store.save(n2) } }
      SyncV1Worker.cancel(ctx)
      after += t
    }
    rows += Row("single sale save after compaction", median(after), "%.1f MB file".format(Locale.ROOT, ledger.length() / 1e6))
    out["archiveMs"] = cm
    out["singleAfterCompactMs"] = median(after)

    val (_, ld2) = ms { runBlocking { ShopStore(ctx).load() } }
    rows += Row("load after compaction", ld2)

  }

  /** گام‌های یک ذخیره، جدا — [cur] دفترِ در دست، [next] همان با یک فروشِ تازه، [shadowText] سایهٔ نشسته */
  private fun stages(cur: ShopData, next: ShopData, shadowText: String, rows: MutableList<Row>, out: MutableMap<String, Any>) {
    newPath(cur, next, rows, out)
    //  `TOHID_BIG_LEDGER_OLD=0`: گام‌های راهِ پیشین (درختِ کامل، تجزیه، toString) را نزن — دفترِ ۵۰۰هزاری در heap ِ ۵ گیگ جا نمی‌شود
    if (System.getenv("TOHID_BIG_LEDGER_OLD") == "0") { uiPath(cur, next, rows, out); return }
    val (_, kin) = ms { LedgerMerge.kinship(next, cur) }
    rows += Row("  · old: baseOf/kinship (identity sets)", kin)
    val (_, gateMs) = ms { cur.sales != next.sales || cur.saleItems != next.saleItems || cur.transactions != next.transactions }
    rows += Row("  · touchesPaid (list equals)", gateMs)
    val (enc, enc1) = ms { json.encodeToString(next) }
    rows += Row("  · old: encodeToString (whole ledger)", enc1)
    val (_, utf) = ms { enc.toByteArray(Charsets.UTF_8) }
    rows += Row("  · old: toByteArray (UTF-8 copy)", utf)
    val scratch = File(ctx.filesDir, "bench-scratch.json")
    if (scratch.exists()) scratch.delete()
    scratch.writeBytes(ByteArray(0))
    val (_, disk1) = ms { AtRest.writeText(ctx, scratch, enc, keepPrevious = true) }
    rows += Row("  · disk (tmp+fsync+.prev+rename)", disk1)
    File(ctx.filesDir, "bench-scratch.json.prev").delete(); scratch.delete()
    val (_, sum1) = ms { json.encodeToString(LedgerSummary.of(next)) }
    rows += Row("  · LedgerSummary.of + encode", sum1)
    val h1 = heapMb()
    val (tree, tj) = ms { LedgerDiff.toJson(next) }
    val treeMb = heapMb() - h1
    rows += Row("  · old: LedgerDiff.toJson", tj, "tree %.0f MB in heap".format(Locale.ROOT, treeMb))
    out["jsonTreeMB"] = treeMb
    val (shadowTree, ps) = ms { Json.parseToJsonElement(shadowText) as JsonObject }
    rows += Row("  · old: parse shadow", ps)
    val (ops, df) = ms { LedgerDiff.diff(shadowTree, tree) }
    rows += Row("  · old: LedgerDiff.diff (parsed shadow)", df, "${ops.size} ops")
    out["syncDiffMs"] = df
    val (newShadow, ss) = ms { LedgerDiff.shadowOf(tree).toString() }
    rows += Row("  · old: shadowOf(tree).toString", ss)
    val (_, sh) = ms { sha(shadowText); sha(newShadow) }
    rows += Row("  · old: sha256 ×2 (journal)", sh)
    if (SyncStateV1(ctx, account).shadow == shadowText) {
      val (_, pc) = ms { SyncStateV1(ctx, account).commitShadow(shadowText, null) }
      rows += Row("  · sync: prefs commit (shadow)", pc)
    }

    uiPath(cur, next, rows, out)
  }

  /** راهِ تازهٔ همان ذخیره (PERF-LEDGER): درختِ هم‌شیء، diff، متنِ سایه با رونوشتِ تکه‌ها، نوشتنِ تکه‌تکهٔ فایل */
  private fun newPath(cur: ShopData, next: ShopData, rows: MutableList<Row>, out: MutableMap<String, Any>) {
    val trees = ir.vil3ntec.tohid.sync.v1.LedgerTrees()
    val treeCur = trees.toJson(cur)
    val shadowCur = LedgerDiff.shadowOf(treeCur)
    val textCur = ir.vil3ntec.tohid.sync.v1.ShadowText.of(shadowCur)
    val (treeNext, t1) = ms { trees.toJson(next) }
    rows += Row("  · new: LedgerTrees.toJson (warm)", t1)
    val (ops, t2) = ms { LedgerDiff.diff(shadowCur, treeNext) }
    rows += Row("  · new: LedgerDiff.diff (shared rows)", t2, "${ops.size} ops")
    out["newDiffMs"] = t2
    val shadowNext = LedgerDiff.shadowOf(treeNext)
    val (textNext, t3) = ms { ir.vil3ntec.tohid.sync.v1.ShadowText.of(shadowNext, textCur, shadowCur) }
    rows += Row("  · new: ShadowText.of (splice)", t3)
    val (_, t4) = ms { sha(textNext.text) }
    rows += Row("  · new: sha256 ×1 (other cached)", t4)
    val sink = object : java.io.OutputStream() {
      var n = 0L
      override fun write(b: Int) { n++ }
      override fun write(b: ByteArray, off: Int, len: Int) { n += len }
    }
    val (_, t5) = ms { ir.vil3ntec.tohid.data.LedgerText.write(json, next) { b, at, n -> sink.write(b, at, n) } }
    rows += Row("  · new: LedgerText.write (stream)", t5, "%.1f MB".format(Locale.ROOT, sink.n / 1e6))
    val (_, t6) = ms { LedgerMerge.kinship(next, cur) }
    rows += Row("  · new: kinship (prefix walk)", t6)
  }

  /** رشتهٔ صفحه (main): آنچه پس از هر فروش در Compose دوباره ساخته می‌شود */
  private fun uiPath(cur: ShopData, next: ShopData, rows: MutableList<Row>, out: MutableMap<String, Any>) {
    //  (`copy` ⇒ دفترِ تازه، همان فهرست‌ها — کشِ یک‌خانه‌ایِ `index` همان کاری را می‌کند که پس از هر ذخیره)
    val (_, si) = ms { ShopStore.index(next.copy()) }
    rows += Row("ui: ShopStore.index (stock, fresh ledger)", si)
    val (_, sx) = ms { ShopStore.salesIndex(next.copy()) }
    rows += Row("ui: ShopStore.salesIndex (fresh ledger)", sx)
    //  همان `CustomerPickerModel.rows` / `ShopAlertsSource`: بدهیِ هر مشتری (پس از فروشِ نسیه: فهرستِ تراکنشِ تازه)
    val credit = BigLedger.sell(cur, 901, credit = true)
    val (_, db) = ms { credit.debtors.sumOf { ShopStore.debt(credit, it.id) } }
    rows += Row("ui: debt() for every customer (picker)", db, "${next.debtors.size} × ${next.transactions.size}")
    out["uiDebtsMs"] = db
    out["uiStockIndexMs"] = si

  }

  private fun report(results: List<Map<String, Any>>) {
    val f = File(System.getProperty("user.dir"), "build/big-ledger-bench.json")
    fun v(x: Any?): String = when (x) {
      null -> "null"
      is Number -> x.toString()
      is Map<*, *> -> x.entries.joinToString(",", "{", "}") { "\"${it.key}\":${v(it.value)}" }
      is List<*> -> x.joinToString(",", "[", "]") { v(it) }
      else -> "\"" + x.toString().replace("\\", "\\\\").replace("\"", "\\\"") + "\""
    }
    runCatching { f.parentFile.mkdirs(); f.writeText(v(results)) }
    println("[big-ledger] → ${f.absolutePath}")
  }

  /** همیشه: کوچک و زمان‌دار — فقط تا مسیرِ سنجه سالم بماند */
  @Test(timeout = 120_000) fun `سنجهٔ دفترِ بزرگ — نسخهٔ کوچکِ همیشگی`() {
    val r = runSize(BigLedger.SMOKE, singles = 2)
    assertTrue(r["skipped"] == null)
  }

  /** دستی: `TOHID_BIG_LEDGER=50000,200000,500000` */
  @Test fun `سنجهٔ دفترِ بزرگ — اندازه‌های واقعی`() {
    val want = System.getenv("TOHID_BIG_LEDGER")?.split(',')?.mapNotNull { it.trim().toIntOrNull() }.orEmpty()
    assumeTrue("فقط با TOHID_BIG_LEDGER", want.isNotEmpty())
    val singles = System.getenv("TOHID_BIG_LEDGER_SINGLES")?.toIntOrNull() ?: 3
    val results = want.map { n ->
      val size = BigLedger.SIZES.firstOrNull { it.sales == n } ?: BigLedger.Size(sales = n)
      runSize(size, singles).also { after(); before() }
    }
    report(results)
  }
}
