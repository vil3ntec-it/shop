package ir.vil3ntec.tohid.core.qr

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 *  `core/test-vectors/qr-large-v1.json` (نسخهٔ ۷ تا ۱۳، QR ِ حساب‌پی) دقیقاً همین رمزگذار است؛
 *  `server/test/qr-large.test.js` همان ماتریس‌ها را با zxing ِ مستقل می‌خواند. و بردارِ فاکتور
 *  (`receipt-qr-v1.json`) با سقفِ تازه هم بیت‌به‌بیت همان می‌ماند.
 */
class QrLargeVectorsTest {

  private fun file(name: String): File =
    listOf("test-vectors/$name", "core/test-vectors/$name", "../core/test-vectors/$name").map(::File).firstOrNull { it.isFile }
      ?: error("$name پیدا نشد")

  /** خواندنِ ساده‌ی بردار (هسته کتابخانهٔ JSON ندارد): متن، نسخه و ردیف‌ها به ترتیب */
  private fun cases(name: String): List<Triple<String, Int, List<String>>> {
    val text = file(name).readText()
    return Regex(""""text":\s*"([^"]+)",\s*"version":\s*(\d+),\s*"rows":\s*\[([^\]]*)]""").findAll(text).map { m ->
      Triple(m.groupValues[1], m.groupValues[2].toInt(), Regex(""""([01]+)"""").findAll(m.groupValues[3]).map { it.groupValues[1] }.toList())
    }.toList()
  }

  private fun rows(text: String, max: Int) = QrCode.encode(text, max).map { r -> r.joinToString("") { if (it) "1" else "0" } }

  @Test
  fun largeVectorsAreThisEncoder() {
    val list = cases("qr-large-v1.json")
    assertEquals(setOf(7, 9, 10, 12, 13), list.map { it.second }.toSet())
    for ((text, version, expected) in list) {
      assertEquals(version, QrCode.versionFor(text, QrCode.LARGE_MAX_VERSION), text)
      assertEquals(expected, rows(text, QrCode.LARGE_MAX_VERSION), text)
    }
  }

  @Test
  fun receiptVectorsUnchangedByTheLargerLimit() {
    val list = cases("receipt-qr-v1.json")
    assertTrue(list.size >= 5)
    for ((text, version, expected) in list) {
      assertEquals(version, QrCode.versionFor(text), text)
      assertEquals(expected, rows(text, QrCode.MAX_VERSION), text)
      assertEquals(expected, rows(text, QrCode.LARGE_MAX_VERSION), text)
    }
  }
}
