package ir.vil3ntec.tohid.data

import android.content.Context
import ir.vil3ntec.tohid.core.backup.BackupStamp
import ir.vil3ntec.tohid.core.backup.ZipTail
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.intOrNull
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 *  OPS-02 — دستگاهیِ مُهرِ پشتیبان (`bundle.json`) و نگه‌داشتِ تهِ ZIP برای `ZipTail.complete` (`BackupBundle`).
 *  منطق در `core/backup/BackupIntegrity.kt` (خالص).
 */
object BackupStampIo {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }

  /** `bundle.json` ِ همین دستگاه: نسخهٔ قالب و صاحبِ دفترِ روی میز (`LedgerOwner`؛ خالی = بی‌صاحب) */
  fun of(context: Context): String = buildJsonObject {
    put("formatVersion", JsonPrimitive(BackupStamp.FORMAT_VERSION))
    put("user", JsonPrimitive(runCatching { LedgerOwner.currentUser(context) }.getOrDefault("")))
    put("shop", JsonPrimitive(runCatching { LedgerOwner.currentShop(context) }.getOrDefault("")))
  }.toString()

  /** خواندنِ `bundle.json` (حداکثر ۶۴KB)؛ ناخوانا ⇒ پرتاب (`BackupBundle.read`: «خراب») */
  fun read(input: InputStream): BackupStamp.Stamp {
    val bytes = ByteArrayOutputStream()
    val buf = ByteArray(4096)
    while (true) {
      val n = input.read(buf, 0, buf.size)
      if (n <= 0) break
      bytes.write(buf, 0, n)
      if (bytes.size() > 64 * 1024) error("bundle too big")
    }
    val o = json.parseToJsonElement(bytes.toString(Charsets.UTF_8.name())) as JsonObject
    fun s(k: String) = (o[k] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
    val v = (o["formatVersion"] as? JsonPrimitive)?.intOrNull ?: error("no version")
    return BackupStamp.Stamp(v, s("user"), s("shop"))
  }

  /** جریانی که آخرین بایت‌هایش را برای رکوردِ پایانیِ ZIP نگه می‌دارد */
  class Tap(private val src: InputStream) : InputStream() {
    private val ring = ZipTail.Tail()
    private val one = ByteArray(1)

    override fun read(): Int {
      val b = src.read()
      if (b >= 0) { one[0] = b.toByte(); ring.add(one, 0, 1) }
      return b
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
      val n = src.read(b, off, len)
      if (n > 0) ring.add(b, off, n)
      return n
    }

    /** تا تهِ فایل (فهرستِ پایانیِ ZIP که خواننده به آن نمی‌رسد) */
    fun drain() {
      val buf = ByteArray(8 * 1024)
      while (read(buf, 0, buf.size) >= 0) Unit
    }

    fun tail(): ByteArray = ring.bytes()
  }
}
