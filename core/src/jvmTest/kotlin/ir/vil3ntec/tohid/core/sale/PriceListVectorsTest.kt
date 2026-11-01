package ir.vil3ntec.tohid.core.sale

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** POS-18 — `PriceList` روی همهٔ `core/test-vectors/price-list-v1.json` (core وابستگیِ JSON ندارد ⇒ هر ردیف با الگو) */
class PriceListVectorsTest {

  private fun vectorText(): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/price-list-v1.json")
      if (f.isFile) return f.readText()
      dir = dir.parentFile
    }
    error("core/test-vectors/price-list-v1.json پیدا نشد")
  }

  private fun str(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست")
  private fun num(line: String, key: String): Long = Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")
  private fun raw(line: String, key: String): List<String> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim() }.filter { it.isNotEmpty() }
  private fun longs(line: String, key: String) = raw(line, key).map { it.toLong() }

  private fun section(name: String): List<String> {
    val text = vectorText()
    val start = text.indexOf("\"$name\": [")
    require(start >= 0) { "$name نیست" }
    val end = text.indexOf("\n  ]", start)
    return text.substring(start, end).lines().filter { it.trimStart().startsWith("{ \"name\"") }
  }

  @Test fun unitPriceOfEveryList() {
    val rows = section("unit")
    assertTrue(rows.size >= 7)
    for (r in rows) {
      assertEquals(num(r, "want"), PriceList.unitMinor(PriceList.Prices(num(r, "retail"), num(r, "wholesale")), str(r, "list")), str(r, "name"))
    }
  }

  @Test fun repriceInvoice() {
    val rows = section("reprice")
    assertTrue(rows.size >= 4)
    for (r in rows) {
      val units = longs(r, "units"); val retail = longs(r, "retail"); val wholesale = longs(r, "wholesale")
      val manual = raw(r, "manual").map { it == "true" }
      val lines = units.indices.map { i -> PriceList.Line("l$i", PriceList.Prices(retail[i], wholesale[i]), units[i], manual[i]) }
      val got = PriceList.reprice(lines, str(r, "list"))
      val name = str(r, "name")
      assertEquals(longs(r, "want"), got.lines.map { it.unitMinor }, name)
      assertEquals(num(r, "changed").toInt(), got.changed, name)
      assertEquals(num(r, "kept").toInt(), got.keptManual, name)
    }
  }
}
