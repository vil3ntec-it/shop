package ir.vil3ntec.tohid.core.sync

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlin.math.max
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
 *   ۱) opهای پذیرفته‌شدهٔ **خودش** و opهای دیگران که **گرفته بود** (`ReplayBook`) با
 *      `server_seq > base` را **به ترتیبِ همان شماره** و با **همان op_id** جلوی صف می‌گذارد —
 *      سرور می‌شناسد ⇒ «duplicate»، نمی‌شناسد ⇒ دوباره «applied». نخستین دستگاهی که برسد
 *      پنجره را به ترتیبِ پیش از بازیابی می‌نشاند (درج پیش از `$inc`)، حتی برای دستگاهی که برنگردد.
 *   ۲) «در انتظارِ ساختنِ دوباره» می‌ماند: Pull نمی‌کند تا صف خالی شود.
 *   ۳) دفتر را «نگه‌دارِ محلی» از نو می‌سازد: Snapshot ِ سرور + ردیف‌هایی که فقط این‌جا هستند
 *      (جز بایگانی‌شده‌ها و حذف‌شده‌های سرور)؛ سایه = Snapshot ⇒ تنهاها «درج» می‌روند؛ cursor با
 *      آن (ژورنالِ `SyncCommit`). از این پس همه‌چیز به ترتیبِ سرور — opِ دوباره‌فرستادهٔ دستگاهِ
 *      دیگر یک بار می‌نشیند و همهٔ دستگاه‌ها یکی می‌شوند.
 *  بالا آمدنِ عادیِ سرور: همان `id` ⇒ هیچ کاری.
 *
 *  منطقِ خالص، بی اندروید و بی `java.*` (نگهبانِ آیفون، RULES-fa «هسته‌های مشترک»): پرونده و قفلِ
 *  دفترچه پشتِ `ReplayBook.Store`، پیاده‌سازیِ JVM در `core/sync/platform/FileReplayStore.kt`. همان را `V1Restore`، `SyncV3Engine` و دستگاهِ آزمونِ
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
 *  دفترچهٔ opهایی که سرور **پیش از بازیابی** داشت، با `server_seq` — برای دوباره فرستادن پس
 *  از بازیابیِ سرور. هر حساب جدا؛ آخرین `CAP` تا.
 *   • `sent`: opهای خودِ این دستگاه که سرور پذیرفت (applied/duplicate) — **پیش از** بیرون رفتن
 *     از صف.
 *   • `seen`: opهای دستگاه‌های دیگر که Pull آورد — **پیش از** نشاندن روی دفتر (v1). opِ خودِ
 *     سرور (`SERVER_DEVICE`: سفارشِ اینترنتی، برگرداندنِ تعارض…) نوشته نمی‌شود: پرونده‌اش
 *     روی سرور با همان بازیابی برگشته و دوباره فرستادنش موجودیِ سفارشِ نبوده را کم می‌کرد.
 *  `replayAbove` هر دو را **به ترتیبِ server_seq** و با همان op_id می‌دهد: دستگاهی که زودتر برسد
 *  همه را به همان ترتیبِ پیش از بازیابی می‌نشاند (درجِ فروش پیش از `$inc`ِ مرجوعیِ دستگاهِ دیگر
 *  روی همان قلم) و بقیه «duplicate» می‌گیرند — حتی اگر دستگاهِ سازنده هرگز برنگردد.
 *
 *  پوشش: `floor` = بالاترین شماره‌ای که دفترچه دیگر ندارد — هنگامِ ساختن cursor ِ همان لحظه
 *  (برنامه‌ای که پس از کار کردن به‌روز شد) و پس از هر فشرده کردن بالاترین شمارهٔ رفته. پایهٔ
 *  بازیابی زیرِ آن ⇒ بخشی از پنجره پوشیده نیست (`V1Restore` هشدار می‌دهد؛ ردیف‌های تنهای دستگاه
 *  با ساختنِ دوبارهٔ «نگه‌دارِ محلی» باز هم می‌مانند).
 *
 *  ⚠️ opِ بی شماره (`seq ≤ 0`) و شناسهٔ نادرست نوشته نمی‌شود.
 *  ⚠️ فقط افزودن (هر خط یک رکورد، با fsync)؛ خطِ نیمه‌کارهٔ قطعِ برق نادیده گرفته می‌شود.
 *  فشرده کردن با جایگزینیِ اتمیِ `Store.replace` (`DiskWrite`، F0-01).
 *  ⚠️ هیچ شکستی این‌جا همگام‌سازی را نمی‌خواباند: نوشتنِ ناموفق فقط یعنی آن op پس از
 *  بازیابیِ بعدی دوباره نمی‌رود — همان رفتارِ پیش از این دفترچه.
 */
class ReplayBook(private val store: Store, private val cap: Int = CAP) {

  /**
   *  پرونده و قفلِ دفترچه — مرزِ پلتفرم (JVM: `platform/FileReplayStore`). هیچ‌کدام پرتاب نمی‌کند.
   */
  interface Store {
    /** پرونده هست و خالی نیست؛ خطای خواندن ⇒ true (بارِ نخست را دوباره ننویس) */
    fun started(): Boolean
    /** خط‌های ناخالی؛ نبودن یا خطا ⇒ خالی */
    fun lines(): List<String>
    /** افزودن با fsync؛ خطِ نیمه‌کارهٔ قطعِ برق ⇒ رکوردِ تازه از سرِ خطِ تازه */
    fun append(text: String): Boolean
    /** جایگزینیِ اتمیِ همهٔ پرونده (فایلِ کناری، بعد جابه‌جایی) */
    fun replace(text: String): Boolean
    /** همهٔ کارِ یک صدا زدن زیرِ یک قفل (بازگشت‌پذیر) */
    fun <T> locked(block: () -> T): T
  }

  /** یک op ِ پذیرفته یا گرفته‌شده: `body` همان چیزی است که دوباره فرستاده می‌شود؛ `device` فقط برای `seen` */
  data class Sent(val id: String, val seq: Long, val body: JsonObject, val device: String = "")

  private val json = Json { ignoreUnknownKeys = true; isLenient = true }
  /** op_id ⇒ آخرین server_seq */
  private var latest: HashMap<String, Long>? = null
  private var lines = 0
  private var floor = 0L

  /**
   *  نخستین بار برای این حساب (فایل نیست): `floor` = cursor ِ همین حالا — آنچه پیش از آن بود
   *  در دفترچه نیست. هر دور صدا زده می‌شود و جز بارِ نخست کاری نمی‌کند.
   */
  fun begin(cursor: Long) = store.locked {
    if (store.started()) return@locked
    if (store.append(floorLine(max(0L, cursor)))) {
      floor = max(0L, cursor)
      latest = HashMap()
      lines = 0
    }
  }

  /** opهای خودِ دستگاه که سرور پذیرفت — **پیش از** `ack`ِ صف */
  fun sent(items: List<Sent>) = store.locked { write(items) }

  /** opهای دستگاه‌های دیگر که Pull آورد — **پیش از** نشاندن روی دفتر؛ opِ خودِ سرور نه */
  fun seen(items: List<Sent>) = store.locked { write(items.filter { it.device.isNotEmpty() && it.device != SERVER_DEVICE }) }

  private fun write(items: List<Sent>) {
    val ok = items.filter { it.seq > 0 && ID.matches(it.id) }
    if (ok.isEmpty()) return
    val map = loaded()
    val text = buildString {
      for (s in ok) {
        //  ترتیبِ کلیدها ثابت: `loaded` فقط سرِ خط را می‌خواند
        append(buildJsonObject {
          put("id", JsonPrimitive(s.id))
          put("q", JsonPrimitive(s.seq))
          put("op", s.body)
        }.toString())
        append('\n')
      }
    }
    if (!store.append(text)) return
    ok.forEach { map[it.id] = it.seq }
    lines += ok.size
    if (lines > cap + cap / 2) { compact(); latest = null }
  }

  /** پیش‌تر پذیرفته یا گرفته شده بود؟ (ردِ دوباره‌فرستاده «انجام‌شده» است، نه ردشده‌ای که کاربر ببیند) */
  fun wasSent(id: String): Boolean = store.locked { id.isNotEmpty() && id in loaded() }

  /** opی که **آخرین** شماره‌اش بالای این پایه است هست؟ */
  fun hasAbove(base: Long): Boolean = store.locked { loaded().values.any { it > base } }

  /** بالاترین شماره‌ای که دفترچه دیگر ندارد (شرحِ بالا) */
  fun floor(): Long = store.locked { loaded(); floor }

  /**
   *  opهایی که آخرین `server_seq`شان بالای پایه است، **به ترتیبِ همان شماره**. همان op اگر دو
   *  بار پذیرفته شده (پس از بازیابیِ پیشین)، آخرین شماره‌اش حساب است.
   */
  fun replayAbove(base: Long): List<Sent> = store.locked {
    val byId = LinkedHashMap<String, Sent>()
    for (line in store.lines()) {
      val s = parse(line) ?: continue
      val prev = byId[s.id]
      byId[s.id] = if (prev == null) s else prev.copy(seq = s.seq)
    }
    byId.values.filter { it.seq > base }.sortedBy { it.seq }
  }

  fun count(): Int = store.locked { loaded(); lines }

  /** فقط سرِ هر خط (شناسه و شماره) — بی تجزیهٔ کاملِ هزاران op در هر بالا آمدن */
  private fun loaded(): HashMap<String, Long> {
    latest?.let { return it }
    val map = HashMap<String, Long>()
    var n = 0
    var low = 0L
    for (line in store.lines()) {
      val f = FLOOR.matchEntire(line)
      if (f != null) { f.groupValues[1].toLongOrNull()?.let { low = max(low, it) }; continue }
      val m = HEAD.find(line) ?: continue
      if (!line.endsWith("}}")) continue                       //  خطِ نیمه‌کارهٔ قطعِ برق
      val seq = m.groupValues[2].toLongOrNull() ?: continue
      if (seq <= 0) continue
      map[m.groupValues[1]] = seq
      n++
    }
    lines = n
    floor = low
    latest = map
    return map
  }

  private fun parse(line: String): Sent? = runCatching {
    val o = json.parseToJsonElement(line) as? JsonObject ?: return null
    val id = (o["id"] as? JsonPrimitive)?.content ?: return null
    val seq = (o["q"] as? JsonPrimitive)?.content?.toLongOrNull() ?: return null
    val body = o["op"] as? JsonObject ?: return null
    if (seq <= 0 || !ID.matches(id)) null else Sent(id, seq, body)
  }.getOrNull()

  private fun floorLine(seq: Long) = "{\"floor\":$seq}\n"

  /**
   *  آخرین `cap` رکورد می‌ماند و `floor` بالاترین شمارهٔ رفته می‌شود؛ شکست ⇒ همان فایل، و
   *  تلاشِ دوباره در افزودنِ بعدی.
   */
  private fun compact() {
    var low = 0L
    val entries = ArrayList<Pair<String, Long>>()
    for (line in store.lines()) {
      val f = FLOOR.matchEntire(line)
      if (f != null) { f.groupValues[1].toLongOrNull()?.let { low = max(low, it) }; continue }
      val m = HEAD.find(line) ?: continue
      if (!line.endsWith("}}")) continue
      entries += line to (m.groupValues[2].toLongOrNull() ?: continue)
    }
    if (entries.size <= cap) return
    val gone = entries.subList(0, entries.size - cap).maxOf { it.second }
    val keep = entries.subList(entries.size - cap, entries.size)
    store.replace(floorLine(max(low, gone)) + keep.joinToString("") { it.first + "\n" })
  }

  companion object {
    /** opهای خودِ دستگاه و گرفته‌شده‌ها: دکانِ پرکار با چند دستگاه روزی چند هزار op — چند روز */
    const val CAP = 20_000

    /** `SERVER_DEVICE` ِ `server/src/lib/sync-v1.js` و `sync-v3.js` */
    const val SERVER_DEVICE = "server"

    /** op_idِ v1 و v3 — بی فاصله و بی خطِ تازه */
    private val ID = Regex("^[A-Za-z0-9_.:-]{1,128}$")
    private val HEAD = Regex("^\\{\"id\":\"([A-Za-z0-9_.:-]{1,128})\",\"q\":(\\d{1,18}),\"op\":\\{")
    private val FLOOR = Regex("^\\{\"floor\":(\\d{1,18})\\}$")

    /** نامِ پروندهٔ دفترچهٔ یک حساب: `<name>-sent.jsonl` (`platform/FileReplayStore.inDir`) */
    fun fileName(name: String): String = "$name-sent.jsonl"
  }
}
