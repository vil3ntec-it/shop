package ir.vil3ntec.tohid.core.sync

import ir.vil3ntec.tohid.data.DiskWrite
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import kotlin.math.min

/**
 *  سرور از پشتیبان برگشت؟ opهای «اعمال‌شده»ای که دیگر ندارد دوباره برود — §۹.۵
 *  («بازیابی تا آخرین پشتیبان و سپس اعمالِ opهای صف»)، سناریوی E2E-OPS-14.replay.
 *
 *  ── مشکل ─────────────────────────────────────────────────────────────
 *  پس از بازیابیِ سرور، فروشی که پس از پشتیبان رسیده بود روی سرور نیست، ولی دستگاه
 *  آن را «applied» گرفته و از صف بیرون کرده؛ cursorش هم از سرِ دفترِ سرور جلوتر است.
 *  پس هیچ‌وقت دوباره نمی‌رود. و opهای تازهٔ دستگاه‌های دیگر که شمارهٔ کوچک‌تر از
 *  cursor ِ این دستگاه می‌گیرند، هرگز به این دستگاه نمی‌رسند.
 *
 *  ── راه ──────────────────────────────────────────────────────────────
 *  سرور با هر بالا آمدن «دوران»ی تازه دارد (`server/src/lib/sync-epoch.js`): `id`،
 *  `base` (سرِ دفترِ همین حساب در لحظهٔ بالا آمدن) و `head`. دستگاه:
 *   ۱) `id` عوض شده (یا cursor از `head` جلوتر است) ⇒ opهای پذیرفته‌شدهٔ **خودش** با
 *      `server_seq > base` را با **همان op_id** جلوی صف می‌گذارد. سرور op_id را
 *      می‌شناسد ⇒ «duplicate»؛ نمی‌شناسد ⇒ دوباره «applied». هیچ opِ تازه‌ای ساخته
 *      نمی‌شود (F0-05).
 *   ۲) cursor تا `base` پایین می‌آید ⇒ opهای تازهٔ دیگران پس از بازیابی هم می‌رسند.
 *   ۳) opِ رسیده‌ای که op_idش را پیش‌تر نشانده یا فرستاده (`ReplayBook.unknown`)
 *      دوباره نشانده نمی‌شود — دستگاهِ دیگر هم opهای گم‌شده‌اش را دوباره می‌فرستد و
 *      `$inc`ِ تکراری موجودی را دو بار کم می‌کرد.
 *  بالا آمدنِ عادی (بی بازیابی): `base` = سرِ دفتر ⇒ cursor عوض نمی‌شود و opِ بالای
 *  `base` نیست ⇒ هیچ کاری.
 *
 *  منطقِ خالص، بی اندروید — همان را `SyncV1Engine`، `SyncV3Engine` و دستگاهِ آزمونِ
 *  سرتاسری (`server/test/e2e/lib/native.mjs`) می‌خوانند.
 */
object RestoreReplay {

  /** «دوران»ی که سرور می‌گوید: `GET /api/shop/sync-epoch` (v1) یا `epoch` در pull ِ v3. */
  data class Epoch(val id: String, val base: Long, val head: Long) {
    companion object {
      /** `{id, base, head}`؛ هر چیزِ ناقص ⇒ null (سرورِ کهنه یا خاموش ⇒ همان رفتارِ پیشین) */
      fun from(e: JsonElement?): Epoch? {
        val o = e as? JsonObject ?: return null
        val id = (o["id"] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()
        if (id.isEmpty()) return null
        val base = (o["base"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
        val head = (o["head"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
        if (base < 0 || head < 0) return null
        return Epoch(id, base, head)
      }
    }
  }

  /**
   *  @property epoch       دورانی که از این پس نگه داشته می‌شود
   *  @property cursor      cursorی که pull از آن ادامه می‌دهد
   *  @property replayAbove null ⇒ کاری نیست؛ وگرنه opهای پذیرفته‌شدهٔ خودِ دستگاه با
   *            `server_seq` بزرگ‌تر از این (یا نامعلوم) دوباره فرستاده می‌شوند
   */
  data class Plan(val epoch: String, val cursor: Long, val replayAbove: Long?) {
    val restored: Boolean get() = replayAbove != null
  }

  /**
   *  @param known  دورانی که این حساب آخرین بار دید؛ خالی ⇒ نخستین بار
   *  @param cursor cursor ِ همین حالا
   *  @param server دورانِ سرور؛ null ⇒ هیچ تغییری
   */
  fun plan(known: String, cursor: Long, server: Epoch?): Plan {
    if (server == null) return Plan(known, cursor, null)
    val rotated = known.isNotEmpty() && known != server.id
    //  cursor جلوتر از سرِ دفترِ سرور فقط با برگشتنِ سرور ممکن است — حتی با همان دوران
    //  (بازیابی بی بالا آمدنِ دوباره) یا نخستین بار (برنامه پس از بازیابی به‌روز شد)
    val behind = cursor > server.head
    if (!rotated && !behind) return Plan(server.id, cursor, null)
    val base = min(server.base, server.head)
    return Plan(server.id, min(cursor, base), base)
  }
}

/**
 *  دفترچهٔ کنارِ صف برای `RestoreReplay`، هر حساب جدا:
 *   • `…-sent.jsonl` — opهای **خودِ** این دستگاه که سرور پذیرفت (applied/duplicate)، با
 *     `server_seq`؛ برای دوباره فرستادن پس از بازیابی. آخرین `SENT_CAP` تا.
 *   • `…-seen.jsonl` — op_idِ opهای رسیده‌ای که روی دفتر نشستند؛ برای اینکه opِ دوباره
 *     فرستاده‌شدهٔ دستگاهِ دیگر دو بار ننشیند. آخرین `SEEN_CAP` تا.
 *
 *  ⚠️ فقط افزودن (هر خط یک رکورد، با fsync)؛ خطِ نیمه‌کارهٔ قطعِ برق نادیده گرفته
 *  می‌شود. فشرده کردن با `DiskWrite` (F0-01).
 *  ⚠️ هیچ شکستی این‌جا همگام‌سازی را نمی‌خواباند: نوشتنِ ناموفق فقط یعنی آن op پس از
 *  بازیابیِ بعدی دوباره نمی‌رود — همان رفتارِ پیش از این دفترچه.
 *  ⚠️ «دیده شد» فقط **پس از** نشستن روی دفتر نوشته می‌شود: اگر پیش از آن بسته شود،
 *  همان opها با cursor ِ قدیم دوباره می‌آیند و این بار نشانده می‌شوند.
 */
class ReplayBook(
  private val sentFile: File,
  private val seenFile: File,
  private val sentCap: Int = SENT_CAP,
  private val seenCap: Int = SEEN_CAP,
) {

  /** یک opِ پذیرفته‌شده: `body` همان چیزی است که دوباره فرستاده می‌شود */
  data class Sent(val id: String, val seq: Long, val body: JsonObject)

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private var known: HashSet<String>? = null
  private var sentLines = 0
  private var seenLines = 0

  /** opهای خودِ دستگاه که سرور پذیرفت — پس از `ack`ِ صف */
  @Synchronized
  fun sent(items: List<Sent>) {
    val ok = items.filter { ID.matches(it.id) }
    if (ok.isEmpty()) return
    val ids = loaded()
    val text = buildString {
      for (s in ok) {
        append(buildJsonObject {
          put("id", JsonPrimitive(s.id))
          put("q", JsonPrimitive(s.seq))
          put("op", s.body)
        }.toString())
        append('\n')
      }
    }
    if (!append(sentFile, text)) return
    ok.forEach { ids += it.id }
    sentLines += ok.size
    if (sentLines > sentCap + sentCap / 2) { sentLines = compact(sentFile, sentCap, sentLines); known = null }
  }

  /** op_idِ opهای رسیده که روی دفتر نشستند — پس از `SyncCommit.absorb` */
  @Synchronized
  fun seen(ids: Collection<String>) {
    val ok = ids.filter { ID.matches(it) }
    if (ok.isEmpty()) return
    val set = loaded()
    if (!append(seenFile, ok.joinToString("") { it + "\n" })) return
    set += ok
    seenLines += ok.size
    if (seenLines > seenCap + seenCap / 2) { seenLines = compact(seenFile, seenCap, seenLines); known = null }
  }

  @Synchronized
  fun knows(id: String): Boolean = id.isNotEmpty() && id in loaded()

  /** فقط آن‌هایی که این دستگاه نه فرستاده و نه نشانده است — ترتیب همان */
  @Synchronized
  fun <T> unknown(items: List<T>, idOf: (T) -> String): List<T> {
    if (items.isEmpty()) return items
    val set = loaded()
    if (set.isEmpty()) return items
    return items.filter { idOf(it) !in set }
  }

  /**
   *  opهای پذیرفته‌شده با `server_seq > base` (یا نامعلوم) به ترتیبِ نخستین فرستادن.
   *  همان op اگر دو بار پذیرفته شده (پس از بازیابیِ پیشین)، آخرین شماره‌اش حساب است.
   */
  @Synchronized
  fun replayAbove(base: Long): List<Sent> {
    val byId = LinkedHashMap<String, Sent>()
    for (line in lines(sentFile)) {
      val s = parse(line) ?: continue
      val prev = byId[s.id]
      byId[s.id] = if (prev == null) s else prev.copy(seq = s.seq)
    }
    return byId.values.filter { it.seq <= 0 || it.seq > base }
  }

  @Synchronized
  fun sentCount(): Int { loaded(); return sentLines }

  @Synchronized
  fun seenCount(): Int { loaded(); return seenLines }

  private fun loaded(): HashSet<String> {
    known?.let { return it }
    val set = HashSet<String>()
    var sent = 0
    for (line in lines(sentFile)) { val s = parse(line) ?: continue; set += s.id; sent++ }
    var seen = 0
    for (line in lines(seenFile)) { val id = line.trim(); if (ID.matches(id)) { set += id; seen++ } }
    sentLines = sent
    seenLines = seen
    known = set
    return set
  }

  private fun parse(line: String): Sent? = runCatching {
    val o = json.parseToJsonElement(line) as? JsonObject ?: return null
    val id = (o["id"] as? JsonPrimitive)?.content ?: return null
    val seq = (o["q"] as? JsonPrimitive)?.content?.toLongOrNull() ?: 0L
    val body = o["op"] as? JsonObject ?: return null
    if (!ID.matches(id)) null else Sent(id, seq, body)
  }.getOrNull()

  private fun lines(f: File): List<String> =
    runCatching { if (f.exists()) f.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList() }
      .getOrDefault(emptyList())

  private fun append(f: File, text: String): Boolean = runCatching {
    f.parentFile?.mkdirs()
    //  خطِ نیمه‌کارهٔ قطعِ برق: رکوردِ تازه از سرِ خطِ تازه، نه چسبیده به آن
    val lead = if (f.length() > 0 && lastByte(f) != '\n'.code) "\n" else ""
    FileOutputStream(f, true).use { out ->
      out.write((lead + text).toByteArray(Charsets.UTF_8))
      out.fd.sync()
    }
  }.isSuccess

  private fun lastByte(f: File): Int = RandomAccessFile(f, "r").use { it.seek(it.length() - 1); it.read() }

  /** آخرین `cap` خط می‌ماند؛ شکست ⇒ همان فایل، و تلاشِ دوباره در افزودنِ بعدی */
  private fun compact(f: File, cap: Int, current: Int): Int = runCatching {
    val keep = lines(f).takeLast(cap)
    DiskWrite.text(f, keep.joinToString("") { it + "\n" })
    keep.size
  }.getOrDefault(current)

  companion object {
    /** opهای خودِ دستگاه: یک دکانِ پرکار روزی چند هزار op — بیش از یک هفته */
    const val SENT_CAP = 10_000
    const val SEEN_CAP = 20_000

    /** op_idِ v1 و v3 و کلیدِ صفِ v3 — بی فاصله و بی خطِ تازه */
    private val ID = Regex("^[A-Za-z0-9_.:-]{1,128}$")

    fun inDir(dir: File, name: String): ReplayBook =
      ReplayBook(File(dir, "$name-sent.jsonl"), File(dir, "$name-seen.jsonl"))
  }
}
