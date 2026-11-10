package ir.vil3ntec.tohid.sync.v3

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream

/**
 *  SYN-04 — بدنهٔ push ِ Sync v3 فشرده (gzip ِ همان متنِ JSON، مثلِ `CompressionStream('gzip')` ِ وب)؛ سرور با
 *  `Content-Encoding: gzip` بازش می‌کند (`server/src/middleware/json-body.js`، سقف روی بدنهٔ باز‌شده). فقط پس از
 *  `gzip_push` ِ pull (پرچمِ دکانِ `sync.gzip_push`) و بالای [MIN_CHARS] — `HttpV3Transport` در `SyncV3Runner.kt`.
 */
internal object V3Gzip {
  /** همان مرزِ وب (`GZIP_MIN` ِ `account-code.js`، ۱KB): بدنهٔ کوچک‌تر ساده می‌رود */
  const val MIN_CHARS = 1024

  fun pack(text: String): ByteArray {
    val out = ByteArrayOutputStream()
    GZIPOutputStream(out).use { it.write(text.encodeToByteArray()) }
    return out.toByteArray()
  }
}
