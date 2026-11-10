package ir.vil3ntec.tohid.core.pdf

import ir.vil3ntec.tohid.core.hw.MonoImage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  RPT-11: `ImagePdf` در برابرِ بردارِ `core/test-vectors/image-pdf-v1.json` — همان که `server/test/report-pdf-vectors.test.js`
 *  با `write` ِ `license/report-pdf.js` (PDF ِ مستقیمِ گزارش در وب) می‌خواند؛ بایت‌ها یکی‌اند. فایل‌خوانی فقط این‌جا (jvmTest).
 */
class ImagePdfVectorsTest {

  private val vec: JsonObject = run {
    var dir: File? = File(System.getProperty("user.dir")).absoluteFile
    while (dir != null) {
      val f = File(dir, "core/test-vectors/image-pdf-v1.json").takeIf { it.isFile } ?: File(dir, "test-vectors/image-pdf-v1.json")
      if (f.isFile) return@run Json.parseToJsonElement(f.readText()).jsonObject
      dir = dir.parentFile
    }
    error("core/test-vectors/image-pdf-v1.json پیدا نشد")
  }

  private fun cases(k: String) = vec.getValue(k).jsonArray.map { it.jsonObject }
  private fun JsonObject.s(k: String) = getValue(k).jsonPrimitive.content
  private fun JsonObject.i(k: String) = getValue(k).jsonPrimitive.int

  private fun bytes(hex: String): ByteArray = ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
  private fun hex(b: ByteArray): String = b.joinToString("") { (it.toInt() and 0xFF).toString(16).padStart(2, '0') }

  @Test
  fun ptAndTitle() {
    assertEquals(1, vec.getValue("version").jsonPrimitive.int)
    for (c in cases("pt")) assertEquals(c.s("out"), ImagePdf.pt(c.i("px"), c.i("dpi")), c.toString())
    for (c in cases("utf16")) assertEquals(c.s("out"), ImagePdf.utf16Hex(c.s("in")), c.toString())
  }

  @Test
  fun runLength() {
    val cs = cases("runLength")
    assertTrue(cs.size >= 8)
    for (c in cs) {
      assertEquals(c.s("out"), hex(ImagePdf.runLength(bytes(c.s("in")))), c.s("in").take(40))
      assertEquals(c.s("in"), hex(ImagePdf.runLengthDecode(bytes(c.s("out")))))
    }
  }

  @Test
  fun pdfBytes() {
    val cs = cases("pdf")
    assertTrue(cs.size >= 3)
    for (c in cs) {
      val pages = c.getValue("pages").jsonArray.map { it.jsonObject }.map { MonoImage(it.i("width"), it.i("height"), bytes(it.s("bits"))) }
      assertEquals(c.s("out"), hex(ImagePdf.write(pages, c.i("dpi"), c.s("title"))), c.s("name"))
    }
  }
}
