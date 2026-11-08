package ir.vil3ntec.tohid.core.money

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  `ReturnCap` روی همهٔ `license/test-vectors/return-cap-v1.json`؛ همان فایل را
 *  `server/test/return-line-cap.test.js` برای هم‌زادِ سرور (`server/src/lib/return-line-cap.js`) می‌خواند.
 *  core وابستگیِ JSON ندارد، پس هر ردیف (یک خط) با الگو خوانده می‌شود.
 */
class ReturnCapVectorsTest {

  private fun vectorText(): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "license/test-vectors/return-cap-v1.json")
      if (f.isFile) return f.readText()
      dir = dir.parentFile
    }
    error("license/test-vectors/return-cap-v1.json پیدا نشد")
  }

  private fun long(line: String, key: String): Long =
    Regex("\"$key\": (-?[0-9]+)").find(line)?.groupValues?.get(1)?.toLong() ?: error("$key در $line نیست")

  private fun num(line: String, key: String): Double =
    Regex("\"$key\": (-?[0-9.]+)").find(line)?.groupValues?.get(1)?.toDouble() ?: error("$key در $line نیست")

  private fun arr(line: String, key: String): List<Long> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it.toLong() }

  private fun text(line: String, key: String): String = Regex("\"$key\": \"([^\"]*)\"").find(line)!!.groupValues[1]

  @Test
  fun everyCase() {
    val rows = vectorText().lines().filter { it.trimStart().startsWith("{ \"name\"") }
    assertTrue(rows.size >= 12, "ردیف‌ها ${rows.size}")
    for (line in rows) {
      val name = text(line, "name")
      val prior = arr(line, "prior").chunked(2).map { ReturnCap.Row(it[0], it[1]) }
      val req = arr(line, "req").let { ReturnCap.Row(it[0], it[1]) }
      val sold = long(line, "soldMilli")
      val cap = long(line, "capMinor")
      val counter = long(line, "counterMilli")
      val s = ReturnCap.state(sold, cap, prior, counter)
      assertEquals(long(line, "returnedMilli"), s.returnedMilli, "$name: returnedMilli")
      assertEquals(long(line, "returnedMinor"), s.returnedMinor, "$name: returnedMinor")
      assertEquals(long(line, "leftMilli"), s.leftMilli, "$name: leftMilli")
      assertEquals(long(line, "leftMinor"), s.leftMinor, "$name: leftMinor")
      val v = ReturnCap.check(sold, cap, prior, req, long(line, "tolMinorPerRow"), counter, long(line, "extraMinor"))
      assertEquals(text(line, "verdict"), v.name.lowercase(), name)
    }
  }

  @Test
  fun everyCap() {
    val rows = vectorText().lines().filter { it.trimStart().startsWith("{ \"capName\"") }
    assertTrue(rows.size >= 5, "ردیف‌ها ${rows.size}")
    for (line in rows) {
      assertEquals(long(line, "capMinor"), ReturnCap.capMinor(num(line, "lineTotal"), num(line, "paidRatio")), text(line, "capName"))
    }
  }
}
