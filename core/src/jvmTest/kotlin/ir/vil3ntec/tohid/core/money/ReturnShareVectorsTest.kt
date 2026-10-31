package ir.vil3ntec.tohid.core.money

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  SEC-DATA-01 — `ReturnShare.amount` روی همهٔ `license/test-vectors/return-share-v1.json`؛
 *  همان فایل را `server/test/return-share.test.js` برای `returnShare` ِ `license/money.js` می‌خواند.
 *  core وابستگیِ JSON ندارد، پس هر ردیف (یک خط) با الگو خوانده می‌شود.
 */
class ReturnShareVectorsTest {

  private fun vectorText(): String {
    var dir: File? = File("").absoluteFile
    while (dir != null) {
      val f = File(dir, "license/test-vectors/return-share-v1.json")
      if (f.isFile) return f.readText()
      dir = dir.parentFile
    }
    error("license/test-vectors/return-share-v1.json پیدا نشد")
  }

  private fun num(line: String, key: String): Double =
    Regex("\"$key\": (-?[0-9.]+)").find(line)?.groupValues?.get(1)?.toDouble() ?: error("$key در $line نیست")

  private fun arr(line: String, key: String): List<Double> =
    (Regex("\"$key\": \\[([^\\]]*)\\]").find(line)?.groupValues?.get(1) ?: error("$key در $line نیست"))
      .split(',').map { it.trim() }.filter { it.isNotEmpty() }.map { it.toDouble() }

  @Test
  fun everyVectorCase() {
    val rows = vectorText().lines().filter { it.trimStart().startsWith("{ \"name\"") }
    assertTrue(rows.size >= 10, "ردیف‌ها ${rows.size}")
    for (line in rows) {
      val name = Regex("\"name\": \"([^\"]*)\"").find(line)!!.groupValues[1]
      var before = 0.0
      val got = arr(line, "steps").map { q ->
        val a = ReturnShare.amount(num(line, "lineTotal"), num(line, "quantity"), before, q, num(line, "paidRatio"))
        if (q > 0) before += q
        a
      }
      assertEquals(arr(line, "amounts"), got, name)
    }
  }
}
