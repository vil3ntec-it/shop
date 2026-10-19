package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File

/**
 *  صفِ opها — «سه روز آفلاین هم گم نمی‌شود».
 *
 *  ── چرا فایل و نه SharedPreferences ────────────────────────────────
 *  صفِ سه روزِ یک دکانِ شلوغ چند هزار op است. `SharedPreferences` کلِ
 *  محتوا را در حافظه نگه می‌دارد و با هر `apply()` کلِ فایل را دوباره
 *  می‌نویسد؛ برای یک صفِ رشدکننده جای درستی نیست.
 *
 *  ⛔ **opی که سرور `applied` یا `duplicate` نگفته از صف بیرون نمی‌رود.**
 *  `rejected` بیرون می‌رود — وگرنه صف تا ابد قفل می‌ماند — ولی بی‌صدا
 *  نه: دلیلش در دفترِ کنار (`…-dropped.json`) می‌نشیند و در «حالِ
 *  همگام‌سازی» دیده می‌شود.
 *
 *  ⚠️ نوشتن اتمی است (فایلِ کناری، بعد جابه‌جایی): اگر وسطِ نوشتن باتری
 *  تمام شود، صف نصفه نمی‌ماند.
 */
class OpQueue(context: Context, accountKey: String) {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val dir = context.applicationContext.filesDir
  private val safe = fileKey(accountKey)

  private val file = File(dir, "oplog-$safe.json")
  private val dropFile = File(dir, "oplog-$safe-dropped.json")

  /*
   *  ── شمارشِ کش‌شده، عمداً ────────────────────────────────────────
   *  ⚠️ **قاعدهٔ سرعت.** `size()` در هر `publish()` خوانده می‌شود، یعنی
   *  با هر ذخیرهٔ دفتر. خواندن و تجزیهٔ کلِ فایلِ صف در آن مسیر، برای
   *  دکانی که سه روز آفلاین بوده، یعنی چند مگابایت تجزیه با هر ردیفی
   *  که فروشنده می‌زند.
   *  ⛔ هر نوشتنی روی فایل باید کش را باطل کند — کارِ خودِ `write()`.
   */
  @Volatile private var cachedSize: Int = -1
  @Volatile private var cachedDropped: Int = -1

  @Synchronized
  fun all(): List<SyncOp> = read(file).mapNotNull { SyncOp.fromJson(it) }

  @Synchronized
  fun size(): Int {
    if (cachedSize < 0) cachedSize = read(file).size
    return cachedSize
  }

  @Synchronized
  fun droppedCount(): Int {
    if (cachedDropped < 0) cachedDropped = read(dropFile).size
    return cachedDropped
  }

  @Synchronized
  fun push(ops: List<SyncOp>) {
    if (ops.isEmpty()) return
    val list = read(file).toMutableList()
    list += ops.map { it.toJson() }
    if (list.size > MAX) {
      //  سقفِ ایمنی. کهنه‌ترین می‌رود **و ثبت می‌شود**، نه بی‌صدا.
      val cut = list.subList(0, list.size - MAX).toList()
      repeat(cut.size) { list.removeAt(0) }
      dropRaw(cut, "queue_overflow")
    }
    //  اگر نگرفت، `record()` سایه را جلو نمی‌برد و تغییر دفعهٔ بعد دوباره op می‌شود
    if (!write(file, list)) throw java.io.IOException("صفِ همگام‌سازی روی حافظه نوشته نشد")
  }

  /**
   *  opهایی که پس از بازیابیِ سرور دوباره باید بروند — **جلوی** صف (پیش از تغییرهای
   *  تازه‌تر، به همان ترتیبِ نخستین فرستادن) و با **همان op_id** (F0-05). opی که همین
   *  حالا در صف هست دوباره نمی‌آید. §۹.۵، `core/sync/RestoreReplay.kt`.
   *
   *  @return چند op به صف آمد
   */
  @Synchronized
  fun pushFront(ops: List<SyncOp>): Int {
    if (ops.isEmpty()) return 0
    val list = read(file)
    val have = list.mapNotNullTo(HashSet()) { (it["op_id"] as? JsonPrimitive)?.content }
    val add = ops.filter { it.opId.isNotEmpty() && have.add(it.opId) }.map { it.toJson() }
    if (add.isEmpty()) return 0
    if (!write(file, add + list)) throw java.io.IOException("صفِ همگام‌سازی روی حافظه نوشته نشد")
    return add.size
  }

  /**
   *  دستهٔ بعدی — با سقفِ خودِ سرور: ۲۰۰ op یا ۲۵۶ کیلوبایت.
   *
   *  ⚠️ سقف بر حسبِ **بایت** است نه نویسه: حرفِ فارسی در UTF-8 دو بایت
   *  است و شمردنِ طولِ رشته روی دفترِ فارسی نزدیک به نصفِ اندازهٔ واقعی
   *  را می‌شمرد.
   *  ⚠️ اگر خودِ یک op از سقف بزرگ‌تر باشد باز هم تنها می‌رود؛ دستهٔ
   *  خالی یعنی صفی که تا ابد گیر کرده.
   */
  @Synchronized
  fun batch(maxOps: Int = MAX_OPS, maxBytes: Int = MAX_BYTES): List<SyncOp> {
    val out = ArrayList<SyncOp>(maxOps)
    var size = 2
    for (raw in read(file)) {
      if (out.size >= maxOps) break
      val op = SyncOp.fromJson(raw) ?: continue
      val bytes = raw.toString().toByteArray(Charsets.UTF_8).size + 1
      if (out.isNotEmpty() && size + bytes > maxBytes) break
      size += bytes
      out += op
    }
    return out
  }

  /** فقط opهای پذیرفته‌شده بیرون می‌روند. */
  @Synchronized
  fun ack(ids: Collection<String>): Int {
    if (ids.isEmpty()) return 0
    val gone = ids.toHashSet()
    val list = read(file)
    val keep = list.filter { (it["op_id"] as? JsonPrimitive)?.content !in gone }
    write(file, keep)
    return list.size - keep.size
  }

  @Synchronized
  fun drop(ops: List<SyncOp>, reason: String) = dropRaw(ops.map { it.toJson() }, reason)

  private fun dropRaw(ops: List<JsonObject>, reason: String) {
    if (ops.isEmpty()) return
    val book = read(dropFile).toMutableList()
    val now = System.currentTimeMillis()
    for (o in ops) {
      book += buildJsonObject {
        put("op", o)
        put("reason", JsonPrimitive(reason))
        put("at", JsonPrimitive(now))
      }
    }
    //  دویست تای آخر برای عیب‌یابی بس است
    while (book.size > 200) book.removeAt(0)
    write(dropFile, book)
  }

  @Synchronized
  fun dropped(): List<JsonObject> = read(dropFile)

  @Synchronized
  fun clear() { write(file, emptyList()) }

  /**
   *  فایلِ خراب «خالی» فرض نمی‌شود: پیش‌تر `push` بعدی رویش می‌نوشت و همهٔ
   *  opهای مانده می‌رفتند. حالا کنار گذاشته می‌شود (`*.corrupt-<زمان>`)
   *  تا از دست نرود و بشود از پشتیبان یا گزارشِ خطا درش آورد.
   */
  private fun read(f: File): List<JsonObject> {
    if (!f.exists()) return emptyList()
    return runCatching {
      (json.parseToJsonElement(f.readText()) as JsonArray).mapNotNull { it as? JsonObject }
    }.getOrElse {
      runCatching { f.renameTo(File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")) }
      emptyList()
    }
  }

  /** @return نوشته شد یا نه — نوشتنِ ناموفق دیگر بی‌صدا نیست */
  private fun write(f: File, list: List<JsonObject>): Boolean {
    val ok = runCatching {
      val tmp = File(f.parentFile, "${f.name}.tmp")
      tmp.writeText(JsonArray(list).toString())
      if (!tmp.renameTo(f)) { f.delete(); check(tmp.renameTo(f)) }
    }.isSuccess
    if (ok) { if (f == file) cachedSize = list.size else cachedDropped = list.size }
    return ok
  }

  companion object {
    /** بخشِ نامِ فایل از کلیدِ حساب — صف و ژورنالِ همان حساب (`SyncCommit`) */
    fun fileKey(accountKey: String): String =
      accountKey.map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("").take(48).ifBlank { "anon" }

    const val MAX = 50_000
    const val MAX_OPS = 200
    const val MAX_BYTES = 256 * 1024
  }
}
