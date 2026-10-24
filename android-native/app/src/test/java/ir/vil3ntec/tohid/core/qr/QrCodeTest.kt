package ir.vil3ntec.tohid.core.qr

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import java.io.File

/**
 *  QRِ رسید (RET-01) — همان ماتریس‌های `core/test-vectors/qr-v1.json` که
 *  `server/test/receipt-qr.test.js` با خوانندهٔ مستقلِ zxing می‌خواند. پس اگر هر
 *  دو سبزند، رسیدِ گوشی و کامپیوتر QRِ خواندنی چاپ می‌کند.
 */
class QrCodeTest {
  private val cases = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/qr-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject.getValue("cases").jsonArray
      dir = dir.parentFile
    }
    error("core/test-vectors/qr-v1.json پیدا نشد")
  }

  @Test
  fun `همان ماتریسِ بردار`() {
    cases.forEach {
      val c = it.jsonObject
      val text = c.getValue("text").jsonPrimitive.content
      val want = c.getValue("rows").jsonArray.map { r -> r.jsonPrimitive.content }
      val got = QrCode.encode(text).map { row -> row.joinToString("") { v -> if (v) "1" else "0" } }
      assertEquals(text, want, got)
    }
  }

  @Test
  fun `بیش از ۴۲ بایت پذیرفته نمی‌شود`() {
    assertThrows(IllegalArgumentException::class.java) { QrCode.encode("x".repeat(QrCode.MAX_BYTES + 1)) }
  }
}
