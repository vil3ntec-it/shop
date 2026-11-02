package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.audit.Sha256

/**
 *  حافظهٔ بارکدخوان — «گوشی خاموش بود و کسی اسکن کرد» (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱).
 *
 *  بیشترِ بارکدخوان‌های بلوتوثی حالتِ «انبار/ذخیره» دارند: بی وصل، کدها را در حافظهٔ خودشان نگه می‌دارند و با
 *  وصلِ دوباره (یا با بارکدِ «فرستادنِ داده» ِ دفترچه‌شان) همه را پشتِ هم می‌فرستند. این بسته از دو چیز شناخته
 *  می‌شود: سطرها در کمتر از [BurstGate.GAP_MS] پشتِ هم می‌رسند (دستِ آدم دو اسکن را این‌قدر نزدیک نمی‌زند).
 *  بسته **هرگز** یک‌راست به سبد نمی‌رود — حتی با برنامهٔ باز — و فقط به صفِ در انتظار می‌رود تا صاحب‌کار مقصد را
 *  بگوید.
 *
 *  تکرار: سطرِ با تاریخِ خودِ بارکدخوان با کلیدِ (دستگاه، زمان، کد) ِ `ScanJournal` سنجیده می‌شود. بسته‌ی بی تاریخ
 *  با اثرِ آخرین بستهٔ همان دستگاه (`MemoryImport`): بستهٔ عینِ قبلی ⇒ هیچ؛ بسته‌ای که با همان آغاز شده (بارکدخوان
 *  حافظه را پاک نکرد و ادامه داد) ⇒ فقط دنبالهٔ تازه.
 */
object BurstGate {
  /** فاصلهٔ دو سطر که هنوز «یک بسته» است */
  const val GAP_MS = 150L

  /** آنچه دروازه بیرون می‌دهد: سطرِ زنده، یا یک بستهٔ کامل از حافظه */
  sealed interface Out {
    data class Live(val line: String, val atMs: Long) : Out
    data class Burst(val lines: List<String>, val atMs: Long) : Out
  }

  /**
   *  حالتِ دروازهٔ یک دستگاه. هر سطر تا [GAP_MS] نگه داشته می‌شود تا روشن شود تنهاست (زنده) یا سرِ یک بسته.
   *  خواننده پس از هر سطر `offer` و در هر مهلتِ خواندن `tick` را صدا می‌زند.
   */
  class Gate {
    private val held = ArrayList<String>()
    private var firstAt = 0L
    private var lastAt = 0L

    val waiting: Boolean get() = held.isNotEmpty()

    fun offer(line: String, now: Long): List<Out> {
      val out = if (held.isNotEmpty() && now - lastAt > GAP_MS) flush() else emptyList()
      if (held.isEmpty()) firstAt = now
      held += line
      lastAt = now
      return out
    }

    /** زمان گذشت: سطرِ مانده‌ای که [GAP_MS] تنها ماند بیرون می‌رود */
    fun tick(now: Long): List<Out> = if (held.isNotEmpty() && now - lastAt > GAP_MS) flush() else emptyList()

    /** قطع شد: هرچه مانده همین حالا */
    fun flush(): List<Out> {
      if (held.isEmpty()) return emptyList()
      val out: Out = if (held.size == 1) Out.Live(held[0], firstAt) else Out.Burst(held.toList(), firstAt)
      held.clear()
      return listOf(out)
    }
  }
}

/** یک سطرِ حافظه: کد، شمار و زمانِ خودِ بارکدخوان (اگر نوشت) */
data class MemoryItem(val code: String, val qty: Int = 1, val time: String? = null)

object MemoryLine {
  private val SEPS = charArrayOf(',', ';', '\t', '|')
  private val DATE_TIME = Regex("""^(\d{4})[-/.](\d{1,2})[-/.](\d{1,2})[ T]+(\d{1,2}):(\d{2})(?::(\d{2}))?$""")

  /** «۲۰۲۶/۱۱/۰۲ ۹:۵:۰۳» ⇒ ‎`2026-11-02T09:05:03`؛ نادرست ⇒ `null` */
  fun time(raw: String): String? {
    val m = DATE_TIME.matchEntire(raw.trim()) ?: return null
    val (y, mo, d, h, mi) = m.destructured
    val s = m.groupValues[6].ifEmpty { "00" }
    fun p(v: String) = v.padStart(2, '0')
    if (mo.toInt() !in 1..12 || d.toInt() !in 1..31 || h.toInt() > 23 || mi.toInt() > 59 || s.toInt() > 59) return null
    return "$y-${p(mo)}-${p(d)}T${p(h)}:$mi:$s"
  }

  /**
   *  سطرِ خامِ حافظه ⇒ کد. شکل‌های رایج: «کد»، «کد,شمار»، «کد,تاریخ زمان»، «تاریخ زمان,کد»، «کد,شمار,تاریخ زمان»
   *  (جداکننده‌های , ; تب |). شمار فقط عددِ ۱ تا ۹۹۹۹؛ هر چیزِ دیگر ⇒ کلِ سطر خودِ کد است (کدی که ویرگول دارد
   *  شکسته نمی‌شود). سطرِ خالی ⇒ `null`.
   */
  fun parse(raw: String): MemoryItem? {
    val line = ScanJournal.clean(raw.replace('\t', ','))
    if (line.isEmpty()) return null
    val parts = line.split(*SEPS).map { it.trim() }.filter { it.isNotEmpty() }
    if (parts.size < 2) return MemoryItem(line)
    var time: String? = null
    var qty: Int? = null
    val rest = ArrayList<String>()
    parts.forEach { p ->
      val t = if (time == null) time(p) else null
      val n = p.toIntOrNull()?.takeIf { it in 1..9999 && p.length <= 4 }
      when {
        t != null -> time = t
        n != null && qty == null && rest.isNotEmpty() -> qty = n
        else -> rest += p
      }
    }
    //  فقط یک تکهٔ کد ماند ⇒ شکستن درست بود؛ وگرنه کلِ سطر یک کد است
    if (rest.size != 1) return MemoryItem(line)
    return MemoryItem(rest[0], qty ?: 1, time)
  }
}

object MemoryImport {
  /** اثرِ بسته (یا آغازِ آن): کدها و شمارها پشتِ هم */
  fun hash(items: List<MemoryItem>): String = Sha256.hexOf(items.joinToString("\n") { "${it.code}\u0001${it.qty}\u0001${it.time.orEmpty()}" }).take(16)

  /**
   *  از بستهٔ تازهٔ [items] کدام‌ها تازه‌اند، با دانستنِ آخرین بستهٔ همان دستگاه ([last] = شمار و اثر):
   *  - عینِ بستهٔ قبلی ⇒ هیچ
   *  - با بستهٔ قبلی آغاز شده ⇒ فقط دنباله
   *  - بقیه ⇒ همه (کلیدِ زمان‌دارها جدا در `ScanJournal` سنجیده می‌شود)
   */
  fun fresh(items: List<MemoryItem>, last: Pair<Int, String>?): List<MemoryItem> {
    if (last == null || last.first <= 0 || last.first > items.size) return items
    return if (hash(items.subList(0, last.first)) == last.second) items.subList(last.first, items.size) else items
  }
}
