package ir.vil3ntec.tohid.sync.v1

import ir.vil3ntec.tohid.data.DiskWrite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.security.MessageDigest

/**
 *  PERF-LEDGER — سایه و cursor ِ Sync v1 در **یک فایل**، یک نوشتن (`ShadowStore`، پشتِ پرچمِ `shop.sync_shadow_file`).
 *
 *  ── قالب (UTF-8) ──────────────────────────────────────────────────────
 *      TOHID-SYNC-SHADOW 1\n
 *      <بایت‌های متنِ سایه، همان `toString`/`ShadowText`>
 *      \n
 *      {"v":1,"gen":…,"cursor":…,"bytes":…,"sha":"…","check":"…"}\n
 *
 *  `sha` = SHA-256 ِ بایت‌های سایه (همان `SyncCommit.sha` ِ متن) و `check` = SHA-256 ِ `v|gen|cursor|bytes|sha`: cursor و
 *  سایه با هم سنجیده می‌شوند. هر ناهمخوانی (سرِ پرونده، اندازه، سطرِ پایان، دو اثرِ انگشت) ⇒ [Corrupt]، هرگز «خالی».
 *
 *  ── نوشتن ─────────────────────────────────────────────────────────────
 *  همان `DiskWrite.stream(keepPrevious = true)` ِ دفتر: کناری ⇐ `fsync` ⇐ `.prev` (پیوندِ سخت به نسخهٔ پیشین) ⇐
 *  جابه‌جاییِ اتمی ⇐ `fsync` ِ پوشه. خواننده یا جفتِ (سایه، cursor) ِ پیشین را می‌بیند یا جفتِ تازه را — هرگز یکی از
 *  این و دیگری از آن. متن تکه‌تکه رمز می‌شود ([CHUNK] نویسه، هرگز میانِ جفتِ UTF-16) و اثرِ انگشت در همان گذر.
 */
internal class ShadowFile(val file: File) {

  /** یک جفتِ سالم: سایه و cursor ِ یک نوشتن، با شمارهٔ نوشتن ([gen]) و اثرِ انگشتِ سایه ([sha]) */
  class Pair(val shadow: String, val cursor: Long, val gen: Long, val sha: String)

  /** فایل هست ولی سالم نیست — هرگز «سایهٔ خالی» خوانده نمی‌شود. [part]: کدام سنجش (برای پشتیبانی) */
  class Corrupt(part: String, name: String) :
    IOException("سایهٔ همگام‌سازی خراب است ($part): $name")  // داده: متنِ خامِ `lastError` (ذخیره می‌شود؛ `SyncModel.classify` با نشانه‌هایش دسته می‌کند) — جزئیاتِ پشتیبانی

  /** خوانده‌شده، و از کدام نسخه (`fromPrevious` ⇒ نسخهٔ یک نوشتن پیش‌تر) */
  class Loaded(val pair: Pair, val fromPrevious: Boolean)

  val previous: File get() = DiskWrite.previousOf(file)

  /** @return اثرِ انگشتِ سایهٔ نوشته‌شده */
  @Throws(IOException::class)
  fun write(shadow: String, cursor: Long, gen: Long): String {
    var sha = ""
    DiskWrite.stream(file, keepPrevious = true) { out ->
      out.write(MAGIC)
      val md = MessageDigest.getInstance("SHA-256")
      var bytes = 0L
      var i = 0
      while (i < shadow.length) {
        var end = minOf(shadow.length, i + CHUNK)
        //  جفتِ UTF-16 دو نیم نمی‌شود — همان بایت‌های رمزِ یک‌جای کلِ متن
        if (end < shadow.length && shadow[end - 1].isHighSurrogate()) end--
        val b = shadow.substring(i, end).encodeToByteArray()
        md.update(b, 0, b.size)
        out.write(b)
        bytes += b.size
        i = end
      }
      sha = hex(md.digest())
      out.write(NL)
      out.write(trailer(gen, cursor, bytes, sha).encodeToByteArray())
      out.write(NL)
    }
    return sha
  }

  /** فقط همین فایل (نه `.prev`). @throws Corrupt فایل هست و سالم نیست؛ `null` ⇒ نیست */
  @Throws(IOException::class)
  fun readMain(): Pair? = readOne(file)

  /**
   *  اصل، و اگر خراب بود (یا نبود ولی `.prev` هست) نسخهٔ پیشین. `null` ⇒ هیچ‌کدام نیست.
   *  @throws Corrupt هر دو هستند (یا یکی هست) و هیچ‌کدام سالم نیست
   */
  @Throws(IOException::class)
  fun load(): Loaded? {
    val main = runCatching { readOne(file) }
    main.getOrNull()?.let { return Loaded(it, fromPrevious = false) }
    val prev = runCatching { readOne(previous) }
    prev.getOrNull()?.let { return Loaded(it, fromPrevious = true) }
    val error = main.exceptionOrNull() ?: prev.exceptionOrNull() ?: return null
    throw error as? IOException ?: Corrupt("read", file.name)
  }

  /** همه — اصل، نسخهٔ پیشین و کناری (خاموش کردنِ پرچم پس از نشستن در prefs، `reset`) */
  fun delete() {
    file.delete()
    previous.delete()
    File(file.absoluteFile.parentFile, file.name + ".tmp").delete()
  }

  private fun readOne(f: File): Pair? {
    if (!f.isFile) return null
    val all = f.readBytes()
    if (all.size < MAGIC.size + 2 || !startsWith(all, MAGIC)) throw Corrupt("header", f.name)
    if (all[all.size - 1] != NL_BYTE) throw Corrupt("torn", f.name)
    val start = lastIndexOf(all, NL_BYTE, all.size - 2) + 1
    if (start <= MAGIC.size) throw Corrupt("trailer", f.name)
    val t = runCatching {
      Json.parseToJsonElement(all.decodeToString(start, all.size - 1)) as JsonObject
    }.getOrNull() ?: throw Corrupt("trailer-json", f.name)
    fun num(k: String) = (t[k] as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()
    fun str(k: String) = (t[k] as? JsonPrimitive)?.takeIf { it.isString }?.content
    val v = num("v"); val gen = num("gen"); val cursor = num("cursor"); val bytes = num("bytes")
    val sha = str("sha"); val check = str("check")
    if (v != VERSION || gen == null || cursor == null || bytes == null || sha == null || check == null) {
      throw Corrupt("format", f.name)
    }
    if (check != checkOf(gen, cursor, bytes, sha)) throw Corrupt("check", f.name)
    val from = MAGIC.size
    val to = from.toLong() + bytes
    if (to + 1 != start.toLong() || all[to.toInt()] != NL_BYTE) throw Corrupt("size", f.name)
    val md = MessageDigest.getInstance("SHA-256")
    md.update(all, from, bytes.toInt())
    if (hex(md.digest()) != sha) throw Corrupt("sha", f.name)
    return Pair(all.decodeToString(from, to.toInt()), cursor, gen, sha)
  }

  companion object {
    const val VERSION = 1L
    private const val CHUNK = 64 * 1024
    private const val NL_BYTE: Byte = 10
    private val NL = byteArrayOf(NL_BYTE)
    private val MAGIC = "TOHID-SYNC-SHADOW 1\n".encodeToByteArray()

    private fun trailer(gen: Long, cursor: Long, bytes: Long, sha: String): String = buildJsonObject {
      put("v", VERSION); put("gen", gen); put("cursor", cursor); put("bytes", bytes); put("sha", sha)
      put("check", checkOf(gen, cursor, bytes, sha))
    }.toString()

    private fun checkOf(gen: Long, cursor: Long, bytes: Long, sha: String): String =
      hex(MessageDigest.getInstance("SHA-256").digest("$VERSION|$gen|$cursor|$bytes|$sha".encodeToByteArray()))

    private fun hex(b: ByteArray): String {
      val digits = "0123456789abcdef"
      val sb = StringBuilder(b.size * 2)
      for (x in b) { val v = x.toInt() and 0xff; sb.append(digits[v shr 4]).append(digits[v and 0xf]) }
      return sb.toString()
    }

    private fun startsWith(a: ByteArray, p: ByteArray): Boolean {
      for (i in p.indices) if (a[i] != p[i]) return false
      return true
    }

    private fun lastIndexOf(a: ByteArray, b: Byte, from: Int): Int {
      var i = from
      while (i >= 0) { if (a[i] == b) return i; i-- }
      return -1
    }
  }
}
