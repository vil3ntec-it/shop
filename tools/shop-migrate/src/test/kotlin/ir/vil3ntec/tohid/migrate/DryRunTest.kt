package ir.vil3ntec.tohid.migrate

import ir.vil3ntec.tohid.db.ShopDb
import ir.vil3ntec.tohid.db.StockMove
import java.io.ByteArrayOutputStream
import java.io.PrintStream
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DryRunTest {

  private fun verdict(r: DryRun.Result, key: String) = r.rows.single { it.key == key }

  @Test fun cleanLedgerReconcilesExactly() {
    listOf(1L, 2L, 3L).forEach { seed ->
      val d = Fixtures.generate(seed)
      val r = DryRun.run(d)
      val notEqual = r.rows.filter { it.verdict != Verdict.EQUAL }
      assertEquals(emptyList(), notEqual, "seed $seed")
      assertEquals(emptyList(), r.anomalies.map { it.code }, "seed $seed")
      assertEquals(null, r.chain.broken)
      assertEquals(d.sales.size, r.chain.saleLinks)
      assertEquals(d.auditLog.size, r.chain.auditLinks)
      assertTrue(r.ok)
      // هر شش گروهِ گزارشِ بخشِ ۸ واقعاً سنجیده شد
      listOf("rows/", "sales/net", "pay/", "customer/", "supplier/", "stock/", "month/").forEach { p ->
        assertTrue(r.rows.any { it.key.startsWith(p) }, p)
      }
    }
  }

  @Test fun knownDefectsAreExplainedNotHidden() {
    val d = Fixtures.generate(1, anomalies = true)
    val r = DryRun.run(d)
    assertEquals(0, r.count(Verdict.UNEXPLAINED), r.rows.filter { it.verdict == Verdict.UNEXPLAINED }.toString())
    assertTrue(r.ok)
    val codes = r.anomalies.map { it.code }.toSet()
    assertEquals(
      setOf("ORPHAN_SALE_ITEM", "RETURNED_QTY_MISMATCH", "ORPHAN_RETURN", "DUPLICATE_BARCODE",
        "CANCEL_WITHOUT_REASON", "NEGATIVE_LEDGER_AMOUNT", "PLACEHOLDER_PRODUCT"),
      codes,
    )
    val p0 = d.products[0].id
    val p1 = d.products[1].id
    verdict(r, "stock/$p0").let { assertEquals(Verdict.EXPLAINED, it.verdict); assertEquals(listOf("ORPHAN_SALE_ITEM"), it.reasons) }
    verdict(r, "stock/$p1").let { assertEquals(Verdict.DOUBLE, it.verdict) }
    verdict(r, "customer/${d.debtors[0].id}").let { assertEquals(Verdict.DOUBLE, it.verdict) }
    verdict(r, "customer/${d.debtors[1].id}").let { assertEquals(Verdict.EQUAL, it.verdict) }
    verdict(r, "sales/cogs").let { assertEquals(Verdict.DOUBLE, it.verdict) }
    verdict(r, "sales/grossProfit").let {
      assertEquals(Verdict.EXPLAINED, it.verdict)
      assertEquals(listOf("DOUBLE_DECIMALS", "ORPHAN_RETURN"), it.reasons)
    }
    verdict(r, "rows/product").let { assertEquals(Verdict.EXPLAINED, it.verdict) }
    verdict(r, "rows/saleLine").let { assertEquals(Verdict.EXPLAINED, it.verdict) }
    assertEquals(1, r.rows.count { it.reasons.contains("RETURNED_QTY_MISMATCH") })
  }

  /** اگر مدلِ تازه عددی را اشتباه بسازد، گزارش باید «بی‌دلیل» بگوید — نه این‌که پنهانش کند */
  @Test fun aWrongNewNumberIsUnexplained() {
    val d = Fixtures.generate(2)
    val model = Mapper.map(d)
    val months = LegacyFigures.months(d)
    DbSettings.openInMemory().use { driver ->
      ShopDb.Schema.create(driver)
      val db = ShopDb(driver)
      Loader.load(db, model)
      val pid = model.products.first().id
      db.stockQueries.insertStockMove(
        StockMove("extra", model.shop.id, model.device.id, model.warehouse.id, pid, "receipt", 1, null, null, null, "", "2024-04-10", 0),
      )
      val rows = Reconcile.compare(LegacyFigures.of(d), LegacyFigures.of(LegacyFigures.roundInputs(d)),
        NewFigures.of(db, model, months), model.anomalies)
      val bad = rows.filter { it.verdict == Verdict.UNEXPLAINED }
      assertEquals(listOf("stock/${model.productLegacy.getValue(pid)}"), bad.map { it.key })
    }
  }

  @Test fun classifierRules() {
    val rows = Reconcile.compare(
      old = mapOf("pay/a" to 10.004, "pay/b" to 5.0, "pay/c" to 1.0, "pay/d" to 1.0, "pay/e" to 2.0),
      rounded = mapOf("pay/a" to 10.0, "pay/b" to 5.0, "pay/c" to 1.0, "pay/d" to 1.0, "pay/e" to 2.0),
      new = mapOf("pay/a" to 1000L, "pay/b" to 500L, "pay/c" to 150L, "pay/d" to 120L, "pay/f" to 1L),
      anomalies = listOf(Anomaly("X", "r", mapOf("pay/c" to 50L)), Anomaly("Y", "r", mapOf("pay/d" to 10L))),
    ).associateBy { it.key }
    assertEquals(Verdict.EQUAL, rows.getValue("pay/a").verdict)       // ۱۰٫۰۰۴ ⇐ ۱۰۰۰ به دقتِ یک‌صدم برابر است
    assertEquals(Verdict.EQUAL, rows.getValue("pay/b").verdict)
    assertEquals(Verdict.EXPLAINED, rows.getValue("pay/c").verdict)
    assertEquals(Verdict.UNEXPLAINED, rows.getValue("pay/d").verdict)  // اثرِ ناهنجاری ۱۰ است نه ۲۰
    assertEquals(listOf("Y"), rows.getValue("pay/d").reasons)
    assertEquals(listOf("ONLY_IN_OLD"), rows.getValue("pay/e").reasons)
    assertEquals(listOf("ONLY_IN_NEW"), rows.getValue("pay/f").reasons)
  }

  @Test fun dryRunWritesNothingAndReadsZipBackups() {
    val dir = Files.createTempDirectory("shop-migrate-dry").toFile()
    try {
      val json = Fixtures.backupJson(Fixtures.generate(3))
      val zip = dir.resolve("backup.zip")
      ZipOutputStream(zip.outputStream()).use { z ->
        z.putNextEntry(ZipEntry("shop-data.json")); z.write(json.toByteArray()); z.closeEntry()
        z.putNextEntry(ZipEntry("photos/p1.jpg")); z.write(byteArrayOf(1, 2, 3)); z.closeEntry()
      }
      val plain = dir.resolve("backup.json").apply { writeText(json) }
      val before = dir.listFiles()!!.associate { it.name to (it.readBytes().contentHashCode() to it.lastModified()) }

      val bytes = ByteArrayOutputStream()
      val code = cli(listOf("dry-run", zip.absolutePath), PrintStream(bytes, true, "UTF-8"))
      val report = bytes.toString("UTF-8")
      assertEquals(0, code, report)
      assertTrue(report.contains("format=zip") && report.contains("photos=1") && report.contains("RESULT: PASS"))
      assertEquals(0, cli(listOf("dry-run", plain.absolutePath), PrintStream(ByteArrayOutputStream(), true, "UTF-8")))

      val after = dir.listFiles()!!.associate { it.name to (it.readBytes().contentHashCode() to it.lastModified()) }
      assertEquals(before, after)

      // گزارشِ پیش‌فرض بی دادهٔ شخصی است
      val d = BackupReader.read(zip).data
      (d.debtors.flatMap { listOf(it.name, it.phone) } + d.products.map { it.name } + d.suppliers.map { it.name } +
        listOf("دکانِ نمونه") + d.products.map { it.id } + d.debtors.map { it.id }).forEach {
        assertFalse(report.contains(it), "report leaks: $it")
      }
    } finally {
      dir.deleteRecursively()
    }
  }

  @Test fun notABackupIsRefused() {
    val e = runCatching { BackupReader.read("""{"hello":1}""".toByteArray()) }.exceptionOrNull()
    assertTrue(e?.message.orEmpty().contains("پشتیبان"))
    // فیلدِ ناشناختهٔ نسخهٔ وب خواندن را نمی‌شکند
    val ok = BackupReader.read("""{"exportedAt":"x","future":[1],"products":[{"id":"a","what":2}]}""".toByteArray())
    assertEquals("a", ok.data.products.single().id)
  }
}
