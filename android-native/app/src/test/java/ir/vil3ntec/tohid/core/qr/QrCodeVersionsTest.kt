package ir.vil3ntec.tohid.core.qr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 *  QR ِ نسخهٔ ۱ تا ۶ (فاکتورِ نسخهٔ ۲) — ماتریس‌ها بیت‌به‌بیت همان
 *  `core/test-vectors/receipt-qr-v1.json` اند. همان بردارها را
 *  `server/test/receipt-qr-v2.test.js` با خوانندهٔ مستقلِ zxing می‌خواند؛ هنگامِ ساختنشان
 *  با رمزگذارِ مستقلِ segno (با لایه‌گذاریِ استانداردِ ۷.۴.۱۰) هم یکی بودند.
 */
class QrCodeVersionsTest {

  private val v: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/receipt-qr-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/receipt-qr-v1.json پیدا نشد")
  }

  @Test fun `ماتریس‌ها همان بردارند؛ نسخه کوچک‌ترین نسخهٔ جاگیر`() {
    val cases = v.getValue("cases").jsonArray.map { it.jsonObject }
    assertEquals(setOf(1, 4, 5, 6), cases.map { it.getValue("version").jsonPrimitive.int }.toSet())
    for (c in cases) {
      val text = c.getValue("text").jsonPrimitive.content
      val rows = c.getValue("rows").jsonArray.map { it.jsonPrimitive.content }
      assertEquals(text, c.getValue("version").jsonPrimitive.int, QrCode.versionFor(text))
      val m = QrCode.encode(text)
      assertEquals(text, 17 + 4 * QrCode.versionFor(text), m.size)
      assertEquals(text, rows, m.map { r -> r.joinToString("") { if (it) "1" else "0" } })
    }
  }

  @Test fun `ظرفیتِ حالتِ بایت در سطحِ M`() {
    assertEquals(listOf(14, 26, 42, 62, 84, 106), (1..6).map { QrCode.capacity(it) })
    assertEquals(106, QrCode.MAX_BYTES)
    assertEquals(6, QrCode.versionFor("x".repeat(106)))
    assertThrows(IllegalArgumentException::class.java) { QrCode.encode("x".repeat(107)) }
  }
}
