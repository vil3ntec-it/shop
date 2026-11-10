package ir.vil3ntec.tohid.sync.v3

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.zip.GZIPInputStream

/**
 *  SYN-04: بدنهٔ push ِ v3 که `HttpV3Transport` پس از `gzip_push` می‌فرستد = gzip ِ ساده ِ همان متنِ UTF-8
 *  (همان که سرور با `Content-Encoding: gzip` باز می‌کند؛ `server/test/sync-v3-gzip-push.test.js`).
 */
class V3GzipTest {
  @Test fun packIsPlainGzipOfTheSameUtf8TextAndMuchSmaller() {
    val text = "{\"ops\":[" + (1..200).joinToString(",") { "{\"op_id\":\"o$it\",\"table\":\"products\",\"fields\":{\"name\":\"چایِ سبز $it\"}}" } + "]}"
    val packed = V3Gzip.pack(text)
    assertEquals(0x1f, packed[0].toInt() and 0xff)
    assertEquals(0x8b, packed[1].toInt() and 0xff)
    assertEquals(text, GZIPInputStream(packed.inputStream()).readBytes().decodeToString())
    assertTrue("${packed.size} از ${text.encodeToByteArray().size}", packed.size * 4 < text.encodeToByteArray().size)
    assertTrue(text.length >= V3Gzip.MIN_CHARS)
  }
}
