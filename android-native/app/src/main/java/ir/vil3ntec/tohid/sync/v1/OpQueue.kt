package ir.vil3ntec.tohid.sync.v1

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import ir.vil3ntec.tohid.data.DiskWrite
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
class OpQueue(context: Context, accountKey: String) : V1Restore.Queue {

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private val dir = context.applicationContext.filesDir
  private val safe = fileKey(accountKey)

  private val file = File(dir, "oplog-$safe.json")
  private val dropFile = File(dir, "oplog-$safe-dropped.json")
  private val evictedFile = File(dir, "oplog-$safe-dropped-evicted.txt")

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
  override fun all(): List<SyncOp> = read(file).mapNotNull { SyncOp.fromJson(it) }

  @Synchronized
  fun size(): Int {
    if (cachedSize < 0) cachedSize = read(file).size
    return cachedSize
  }

  @Synchronized
  fun droppedCount(): Int {
    if (cachedDropped < 0) cachedDropped = read(dropFile).size + evicted()
    return cachedDropped
  }

  /**
   *  ⛔ **هیچ سقفی که کهنه‌ترین‌ها را دور بریزد نیست** (`core/…/core/sync/OpBacklog.kt`): پیش‌تر بالای
   *  ۵۰٬۰۰۰ op کهنه‌ترین‌ها `queue_overflow` می‌شدند و هرگز به سرور نمی‌رسیدند. صفِ بزرگ با پیامِ
   *  `OpBacklog.warning` دیده می‌شود؛ حافظهٔ پر ⇒ نوشتن پرتاب می‌کند (`update`) و تغییر روی دفتر می‌ماند
   *  تا ثبتِ بعدی، با پیامِ «حافظه پر است» (`SyncV1Engine.record`).
   */
  fun push(ops: List<SyncOp>) {
    if (ops.isEmpty()) return
    update { current -> current + ops.map { it.toJson() } }
  }

  /**
   *  opهایی که پس از بازیابیِ سرور دوباره باید بروند — **جلوی** صف (پیش از تغییرهای
   *  تازه‌تر، به همان ترتیبِ نخستین فرستادن) و با **همان op_id** (F0-05). opی که همین
   *  حالا در صف هست دوباره نمی‌آید. §۹.۵، `core/sync/RestoreReplay.kt`.
   *
   *  @return چند op به صف آمد
   */
  override fun pushFront(ops: List<SyncOp>): Int {
    if (ops.isEmpty()) return 0
    var added = 0
    update { list ->
      val have = list.mapNotNullTo(HashSet()) { (it["op_id"] as? JsonPrimitive)?.content }
      val add = ops.filter { it.opId.isNotEmpty() && have.add(it.opId) }.map { it.toJson() }
      added = add.size
      if (add.isEmpty()) null else add + list
    }
    return added
  }

  /**
   *  خواندن، تغییر و نوشتنِ صف زیرِ یک قفل (`push`، `pushFront`)؛ null ⇒ بی نوشتن.
   *  اگر نوشتن نگرفت، `record()` سایه را جلو نمی‌برد و تغییر دفعهٔ بعد دوباره op می‌شود.
   */
  @Synchronized
  private fun update(edit: (List<JsonObject>) -> List<JsonObject>?) {
    val next = edit(read(file)) ?: return
    if (!write(file, next)) throw java.io.IOException("صفِ همگام‌سازی روی حافظه نوشته نشد")  // داده: متنِ خامِ `lastError` (ذخیره می‌شود؛ `SyncModel.classify` با نشانه‌هایش دسته می‌کند) — جزئیاتِ پشتیبانی
  }

  /**
   *  دستهٔ بعدی — با سقفِ خودِ سرور: ۲۰۰ op یا ۲۵۶ کیلوبایت.
   *
   *  ⚠️ سقف بر حسبِ **بایت** است نه نویسه: حرفِ فارسی در UTF-8 دو بایت
   *  است و شمردنِ طولِ رشته روی دفترِ فارسی نزدیک به نصفِ اندازهٔ واقعی
   *  را می‌شمرد.
   *  ⚠️ اگر خودِ یک op از سقف بزرگ‌تر باشد باز هم تنها می‌رود؛ دستهٔ
   *  خالی یعنی صفی که تا ابد گیر کرده.
   *  ⚠️ یک ثبت (همهٔ opهای یک `LedgerDiff.diff`، یک `ts`) وسطِ دو دسته بریده
   *  نمی‌شود ([cut])، مگر کلِ دسته همان یک ثبت باشد.
   */
  @Synchronized
  fun batch(maxOps: Int = MAX_OPS, maxBytes: Int = MAX_BYTES): List<SyncOp> {
    val ops = ArrayList<SyncOp>()
    val sizes = ArrayList<Int>()
    for (raw in read(file)) {
      if (ops.size > maxOps) break                 //  یکی بیشتر: تا ببینیم ثبتِ آخر بریده شد یا نه
      val op = SyncOp.fromJson(raw) ?: continue
      ops += op
      sizes += raw.toString().toByteArray(Charsets.UTF_8).size + 1
    }
    return cut(ops, sizes, maxOps, maxBytes)
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
    //  ⛔ DATA-SAFETY: opی که سرور رد کرد تغییرِ واقعیِ دکان است که به سرور نرسید — دیگر پس از ۲۰۰ تا بی‌صدا
    //  از دفتر نمی‌افتد. سقف فقط برای حافظه است ([DROP_BOOK_MAX]) و آنچه بیرون رفت شمرده می‌شود ([evicted])
    if (book.size > DROP_BOOK_MAX) {
      val over = book.size - DROP_BOOK_MAX
      repeat(over) { book.removeAt(0) }
      evictedFile.writeText((evicted() + over).toString())
    }
    check(write(dropFile, book)) { ir.vil3ntec.tohid.core.i18n.StrSync.rejectedBookNotWritten }
  }

  /** چند op ِ ردشده از دفترِ کنار بیرون رفت (فقط بالای [DROP_BOOK_MAX]) — در «حالِ همگام‌سازی» با همان شمار */
  fun evicted(): Int = runCatching { evictedFile.readText().trim().toInt() }.getOrDefault(0)

  @Synchronized
  fun dropped(): List<JsonObject> = read(dropFile)

  /** نشانِ «تصمیمِ صندوق نشست» ِ همین حساب، کنارِ همین دفترِ کنار (`SetAsideMarks`؛ با `SyncV1Keys.move` جابه‌جا می‌شود) */
  fun settledMarks(): SetAsideMarks = File(dir, SetAsideMarks.v1Name(safe)).let { f -> SetAsideMarks({ f.takeIf { it.isFile }?.readText() }, { DiskWrite.text(f, it) }) }

  @Synchronized
  fun clear() { write(file, emptyList()) }

  /**
   *  فایلِ خراب «خالی» فرض نمی‌شود: پیش‌تر `push` بعدی رویش می‌نوشت و همهٔ
   *  opهای مانده می‌رفتند. حالا کنار گذاشته می‌شود (`*.corrupt-<زمان>`)
   *  تا از دست نرود و بشود از پشتیبان یا گزارشِ خطا درش آورد.
   *
   *  ⛔ DATA-SAFETY: نبودن یا خرابیِ فایلِ اصلی ⇒ اول فایلِ کناری (`.tmp`): نوشتنِ ساختِ پیشین روی ویندوز
   *  اصل را پاک و بعد کناری را جابه‌جا می‌کرد — بسته شدن میانِ این دو کلِ صف را در کناری جا می‌گذاشت و
   *  خوانندهٔ بعدی صف را خالی می‌دید (سایه پیش‌تر جلو رفته بود ⇒ آن تغییرها هرگز به سرور نمی‌رسیدند).
   */
  private fun read(f: File): List<JsonObject> {
    val main = if (f.exists()) parse(f) else null
    if (main != null) return main
    val side = File(f.parentFile, "${f.name}.tmp")
    val pending = if (side.isFile) parse(side) else null
    if (f.exists()) runCatching { f.renameTo(File(f.parentFile, "${f.name}.corrupt-${System.currentTimeMillis()}")) }
    if (pending == null) return emptyList()
    //  کناریِ کامل همان صفِ آخر است ⇒ سرِ جای اصل می‌نشیند (نوشتنِ بعدی دیگر رویش نمی‌نویسد)
    runCatching { DiskWrite.text(f, JsonArray(pending).toString()) }
    return pending
  }

  private fun parse(f: File): List<JsonObject>? = runCatching {
    (json.parseToJsonElement(f.readText()) as JsonArray).mapNotNull { it as? JsonObject }
  }.getOrNull()

  /**
   *  @return نوشته شد یا نه — نوشتنِ ناموفق دیگر بی‌صدا نیست.
   *
   *  ⛔ DATA-SAFETY: همان `DiskWrite` ِ دفتر (فایلِ کناری + `fsync` + جابه‌جاییِ اتمی که روی ویندوز هم
   *  جایگزین می‌کند + `fsync` ِ پوشه). پیش‌تر کناری بی `fsync` نوشته می‌شد — قطعِ برق پس از جابه‌جایی صفِ
   *  صفربایتی جا می‌گذاشت — و روی ویندوز اصل پیش از جابه‌جایی پاک می‌شد.
   */
  private fun write(f: File, list: List<JsonObject>): Boolean {
    val ok = runCatching { DiskWrite.text(f, JsonArray(list).toString()) }.isSuccess
    if (ok) { if (f == file) cachedSize = list.size else cachedDropped = list.size + evicted() }
    return ok
  }

  companion object {
    /** بخشِ نامِ فایل از کلیدِ حساب — صف و ژورنالِ همان حساب (`SyncCommit`) */
    fun fileKey(accountKey: String): String =
      accountKey.map { if (it.isLetterOrDigit()) it else '_' }
        .joinToString("").take(48).ifBlank { "anon" }

    const val MAX_OPS = 200
    const val MAX_BYTES = 256 * 1024

    /** DATA-SAFETY: سقفِ دفترِ opهای ردشده — فقط برای حافظه؛ بیرون‌رفته‌ها شمرده می‌شوند (`evicted`) */
    const val DROP_BOOK_MAX = 5_000

    /**
     *  دسته از سرِ صف: تا [maxOps] op و [maxBytes] بایت (`sizes` اندازهٔ هر op).
     *
     *  سرِ سند (فاکتور، برگشت) و همراه‌هایش (قلم، حرکتِ انبار، تراکنش) در یک ثبت‌اند.
     *  اگر سقف وسطِ یک ثبت بیفتد، سرور ممکن بود سند را در این دسته رد کند و همراهِ بی‌نشانیِ
     *  دستهٔ بعد را بپذیرد (یا برعکس)؛ پس آن ثبت کامل به دستهٔ بعد می‌رود — مگر کلِ دسته
     *  همان یک ثبت باشد (ثبتِ بزرگ‌تر از سقف ناچار بریده می‌شود؛ دستهٔ خالی یعنی صفِ قفل).
     */
    fun cut(ops: List<SyncOp>, sizes: List<Int>, maxOps: Int = MAX_OPS, maxBytes: Int = MAX_BYTES): List<SyncOp> {
      var size = 2
      var n = 0
      while (n < ops.size && n < maxOps) {
        if (n > 0 && size + sizes[n] > maxBytes) break
        size += sizes[n]
        n++
      }
      if (n in 1 until ops.size && ops[n].ts == ops[n - 1].ts) {
        val ts = ops[n - 1].ts
        var start = n - 1
        while (start > 0 && ops[start - 1].ts == ts) start--
        if (start > 0) n = start
      }
      return ops.subList(0, n).toList()
    }
  }
}
