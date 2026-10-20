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
 *  سرور از پشتیبان برگشت؟ — §۹.۵ («بازیابی تا آخرین پشتیبان و سپس اعمالِ opهای صف»)،
 *  سناریوی E2E-OPS-14.replay.
 *
 *  ── مشکل ─────────────────────────────────────────────────────────────
 *  پس از بازیابیِ سرور، فروشی که پس از پشتیبان رسیده بود روی سرور نیست، ولی دستگاه
 *  آن را «applied» گرفته و از صف بیرون کرده؛ cursorش هم از سرِ دفترِ سرور جلوتر است.
 *  دفترِ دستگاه هم opهای دیگران را به ترتیبِ پیش از بازیابی نشانده — ترتیبی که سرورِ
 *  برگشته دیگر ندارد.
 *
 *  ── راه ──────────────────────────────────────────────────────────────
 *  سرور «دورانِ بازیابی» دارد (`server/src/lib/sync-epoch.js`): `id` فقط با بازیابی عوض
 *  می‌شود (نه با بالا آمدنِ عادی) و `base` سرِ دفترِ همین حساب در لحظهٔ بازیابی است.
 *  دستگاه، اگر `id` عوض شده و پس از `base` چیزی دیده یا فرستاده:
 *   ۱) opهای پذیرفته‌شدهٔ **خودش** با `server_seq > base` را با **همان op_id** جلوی صف
 *      می‌گذارد (`ReplayBook`) — سرور می‌شناسد ⇒ «duplicate»، نمی‌شناسد ⇒ دوباره «applied».
 *   ۲) «در انتظارِ ساختنِ دوباره» می‌ماند: Pull نمی‌کند تا صف خالی شود.
 *   ۳) دفتر را از Snapshot ِ سرور از نو می‌سازد و opهای همین حالا در صف را رویش
 *      می‌نشاند؛ سایه و cursor با آن (ژورنالِ `SyncCommit`). از این پس همه‌چیز به ترتیبِ
 *      سرور — opِ دوباره‌فرستادهٔ دستگاهِ دیگر یک بار می‌نشیند و همهٔ دستگاه‌ها یکی می‌شوند.
 *  بالا آمدنِ عادیِ سرور: همان `id` ⇒ هیچ کاری.
 *
 *  منطقِ خالص، بی اندروید — همان را `V1Restore`، `SyncV3Engine` و دستگاهِ آزمونِ
 *  سرتاسری (`server/test/e2e/lib/native.mjs`) می‌خوانند.
 */
object RestoreReplay {

  /** «دورانِ بازیابی» که سرور می‌گوید: `GET /api/shop/sync-epoch` (v1) یا `epoch` در pull ِ v3. */
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
   *  @property epoch   دورانی که از این پس نگه داشته می‌شود
   *  @property restore true ⇒ opهای خودِ دستگاه بالای `base` دوباره و دفتر از Snapshot از نو
   *  @property base    پایهٔ بازیابی
   */
  data class Plan(val epoch: String, val restore: Boolean, val base: Long)

  /**
   *  @param known     دورانی که این حساب آخرین بار دید؛ خالی ⇒ نخستین بار
   *  @param cursor    cursor ِ همین حالا
   *  @param server    دورانِ سرور؛ null ⇒ هیچ تغییری
   *  @param sentAbove آیا این دستگاه opی با `server_seq` بالای این پایه فرستاده است
   */
  fun plan(known: String, cursor: Long, server: Epoch?, sentAbove: (Long) -> Boolean = { false }): Plan {
    if (server == null) return Plan(known, false, 0)
    val base = min(server.base, server.head)
    val rotated = known.isNotEmpty() && known != server.id
    //  cursor جلوتر از سرِ دفترِ سرور فقط با برگشتنِ سرور ممکن است — حتی با همان دوران
    //  (بازیابی از پشتیبانِ بی نشان) یا نخستین بار (برنامه پس از بازیابی به‌روز شد)
    val behind = cursor > server.head
    if (!rotated && !behind) return Plan(server.id, false, base)
    //  دستگاهی که پس از پایه هیچ ندیده و نفرستاده چیزی برای جبران ندارد: opهای دوباره‌فرستادهٔ
    //  دیگران برایش تازه‌اند و یک بار می‌نشینند
    return Plan(server.id, cursor > base || sentAbove(base), base)
  }
}

/**
 *  دفترچهٔ opهای **خودِ** این دستگاه که سرور پذیرفت (applied/duplicate)، با `server_seq` —
 *  برای دوباره فرستادن پس از بازیابیِ سرور. آخرین `CAP` تا، هر حساب جدا.
 *
 *  ⚠️ پیش از بیرون رفتن از صف نوشته می‌شود؛ opِ بی شماره (`seq ≤ 0`) نوشته نمی‌شود.
 *  ⚠️ فقط افزودن (هر خط یک رکورد، با fsync)؛ خطِ نیمه‌کارهٔ قطعِ برق نادیده گرفته می‌شود.
 *  فشرده کردن با `DiskWrite` (F0-01).
 *  ⚠️ هیچ شکستی این‌جا همگام‌سازی را نمی‌خواباند: نوشتنِ ناموفق فقط یعنی آن op پس از
 *  بازیابیِ بعدی دوباره نمی‌رود — همان رفتارِ پیش از این دفترچه.
 */
class ReplayBook(private val file: File, private val cap: Int = CAP) {

  /** یک opِ پذیرفته‌شده: `body` همان چیزی است که دوباره فرستاده می‌شود */
  data class Sent(val id: String, val seq: Long, val body: JsonObject)

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  private var ids: HashSet<String>? = null
  private var top = 0L
  private var lines = 0

  /** opهای خودِ دستگاه که سرور پذیرفت — **پیش از** `ack`ِ صف */
  @Synchronized
  fun sent(items: List<Sent>) {
    val ok = items.filter { it.seq > 0 && ID.matches(it.id) }
    if (ok.isEmpty()) return
    val set = loaded()
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
    if (!append(text)) return
    ok.forEach { set += it.id; if (it.seq > top) top = it.seq }
    lines += ok.size
    if (lines > cap + cap / 2) { compact(); ids = null }
  }

  /** پیش‌تر پذیرفته شده بود؟ (ردِ دوباره‌فرستاده «انجام‌شده» است، نه ردشده‌ای که کاربر ببیند) */
  @Synchronized
  fun wasSent(id: String): Boolean = id.isNotEmpty() && id in loaded()

  /** opی با شمارهٔ بالای این پایه هست؟ */
  @Synchronized
  fun hasAbove(base: Long): Boolean { loaded(); return top > base }

  /**
   *  opهای پذیرفته‌شده با `server_seq > base` به ترتیبِ نخستین فرستادن. همان op اگر دو بار
   *  پذیرفته شده (پس از بازیابیِ پیشین)، آخرین شماره‌اش حساب است.
   */
  @Synchronized
  fun replayAbove(base: Long): List<Sent> {
    val byId = LinkedHashMap<String, Sent>()
    for (line in read()) {
      val s = parse(line) ?: continue
      val prev = byId[s.id]
      byId[s.id] = if (prev == null) s else prev.copy(seq = s.seq)
    }
    return byId.values.filter { it.seq > base }
  }

  @Synchronized
  fun count(): Int { loaded(); return lines }

  private fun loaded(): HashSet<String> {
    ids?.let { return it }
    val set = HashSet<String>()
    var n = 0
    var max = 0L
    for (line in read()) {
      val s = parse(line) ?: continue
      set += s.id
      n++
      if (s.seq > max) max = s.seq
    }
    lines = n
    top = max
    ids = set
    return set
  }

  private fun parse(line: String): Sent? = runCatching {
    val o = json.parseToJsonElement(line) as? JsonObject ?: return null
    val id = (o["id"] as? JsonPrimitive)?.content ?: return null
    val seq = (o["q"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
    val body = o["op"] as? JsonObject ?: return null
    if (seq <= 0 || !ID.matches(id)) null else Sent(id, seq, body)
  }.getOrNull()

  private fun read(): List<String> =
    runCatching { if (file.exists()) file.readLines(Charsets.UTF_8).filter { it.isNotBlank() } else emptyList() }
      .getOrDefault(emptyList())

  private fun append(text: String): Boolean = runCatching {
    file.parentFile?.mkdirs()
    //  خطِ نیمه‌کارهٔ قطعِ برق: رکوردِ تازه از سرِ خطِ تازه، نه چسبیده به آن
    val lead = if (file.length() > 0 && lastByte() != '\n'.code) "\n" else ""
    FileOutputStream(file, true).use { out ->
      out.write((lead + text).toByteArray(Charsets.UTF_8))
      out.fd.sync()
    }
  }.isSuccess

  private fun lastByte(): Int = RandomAccessFile(file, "r").use { it.seek(it.length() - 1); it.read() }

  /** آخرین `cap` خط می‌ماند؛ شکست ⇒ همان فایل، و تلاشِ دوباره در افزودنِ بعدی */
  private fun compact() {
    runCatching {
      val keep = read().takeLast(cap)
      DiskWrite.text(file, keep.joinToString("") { it + "\n" })
    }
  }

  companion object {
    /** opهای خودِ دستگاه: یک دکانِ پرکار روزی چند هزار op — بیش از یک هفته */
    const val CAP = 10_000

    /** op_idِ v1 و v3 — بی فاصله و بی خطِ تازه */
    private val ID = Regex("^[A-Za-z0-9_.:-]{1,128}$")

    fun inDir(dir: File, name: String): ReplayBook = ReplayBook(File(dir, "$name-sent.jsonl"))
  }
}
