package ir.vil3ntec.tohid.core.hw

import ir.vil3ntec.tohid.core.audit.Sha256

/**
 *  صفِ اسکن‌های در انتظار — «برنامه بسته است، کسی اسکن می‌کند، گم نمی‌شود» (درخواستِ صاحب‌کار ۱۴۰۵/۰۸/۱۱).
 *
 *  بارکدخوانِ SPP/BLE یا درگاهِ COM که برنامه وصل نگهش داشته (سرویسِ پیش‌روی اندروید، سینیِ کامپیوتر) هر اسکن را
 *  وقتی کسی جلوی برنامه نیست این‌جا می‌گذارد؛ با باز شدنِ برنامه «N اسکن در انتظار» پرسیده می‌شود و هر اسکن فقط
 *  پس از رسیدن به مقصدش (انبارگردانی، ورودِ کالا، فاکتورِ فروشِ نگه‌داشته) «برداشته» می‌شود.
 *
 *  ── دفترِ افزودنی (journal) ────────────────────────────────────────
 *  هر رویداد یک سطر است و فقط به تهِ پرونده افزوده می‌شود: ‎`S` اسکن، ‎`C` برداشته، ‎`K` کلیدِ تکرار ِ برداشته‌ها، ‎`M`
 *  اثرِ آخرین بستهٔ حافظهٔ هر بارکدخوان. هر سطر جمعِ آزمونِ خودش را دارد؛ سطرِ نیمه‌نوشته (قطعِ برق وسطِ نوشتن)
 *  یا دست‌خورده کنار می‌رود و بقیه سالم می‌مانند. فشرده‌سازی (`compact`) فقط اسکن‌های مانده، کلیدها و اثرها را
 *  دوباره می‌نویسد (نوشتنِ اتمیِ پرونده با مرزِ پلتفرم است).
 *
 *  ── تکرار ──────────────────────────────────────────────────────────
 *  کلیدِ هر اسکن (دستگاه، زمان، کد): زمانِ خودِ بارکدخوان اگر گفت (حافظه با تاریخ)، وگرنه زمانِ رسیدن. اسکنی که
 *  کلیدش پیش‌تر آمده (حتی برداشته شده) دوباره صف نمی‌شود. حافظهٔ بی تاریخ با `MemoryImport` سنجیده می‌شود.
 */
enum class ScanSource { LIVE, MEMORY }

data class QueuedScan(
  val id: String,
  /** نشانیِ دستگاه (`bt:…`) */
  val device: String,
  /** زمانِ رسیدن به برنامه (میلی‌ثانیه) */
  val atMs: Long,
  val code: String,
  val source: ScanSource = ScanSource.LIVE,
  /** شمارِ همین کد در یک سطرِ حافظه («کد,۳») — اسکنِ زنده همیشه ۱ */
  val qty: Int = 1,
  /** زمانی که خودِ بارکدخوان نوشته (حافظهٔ با تاریخ)، به همان شکلِ نرمال‌شده */
  val deviceTime: String? = null,
) {
  /** کلیدِ تکرار: (دستگاه، زمان، کد) */
  val key: String get() = ScanJournal.key(device, deviceTime ?: atMs.toString(), code)
}

object ScanJournal {
  /** بلندترین کدی که پذیرفته می‌شود — بارکدِ دوبعدیِ معمول کمتر است؛ بیشتر ⇒ خطای خواندن */
  const val MAX_CODE = 256
  /** شمارِ کلیدهای برداشته که برای جلوگیری از تکرار نگه داشته می‌شود */
  const val KEEP_KEYS = 5_000

  private const val T = '\t'

  /** فقط نویسه‌های دیدنی (بی کنترل، بی تب و خطِ تازه) — کدی که پس از پاک‌سازی خالی است پذیرفته نیست */
  fun clean(code: String): String = code.filter { it.code >= 32 && it != T }.trim().take(MAX_CODE)

  fun key(device: String, time: String, code: String): String = Discovery.key(device) + "|" + time + "|" + code

  /** جمعِ آزمونِ سطر: ۸ رقمِ هگزِ SHA-256 — برای پیدا کردنِ سطرِ نیمه‌نوشته، نه امنیت */
  private fun sum(body: String): String = Sha256.hexOf(body).take(8)

  private fun seal(body: String): String = body + T + sum(body)

  private fun open(line: String): List<String>? {
    val at = line.lastIndexOf(T)
    if (at <= 0) return null
    val body = line.substring(0, at)
    return if (sum(body) == line.substring(at + 1)) body.split(T) else null
  }

  fun scan(s: QueuedScan): String = seal(
    listOf("S", s.id, clean(s.device), s.atMs.toString(), s.source.name, s.qty.coerceAtLeast(1).toString(), s.deviceTime.orEmpty(), clean(s.code))
      .joinToString(T.toString()),
  )

  fun taken(id: String): String = seal("C" + T + id)

  fun memory(device: String, size: Int, hash: String): String = seal(listOf("M", clean(device), size.toString(), hash).joinToString(T.toString()))

  private fun keyLine(key: String): String = seal("K" + T + key)

  /** آنچه از دفتر خوانده شد */
  data class State(
    /** اسکن‌های برداشته‌نشده، به ترتیبِ رسیدن */
    val pending: List<QueuedScan> = emptyList(),
    /** کلیدِ همهٔ اسکن‌های دیده‌شده (مانده و برداشته) */
    val keys: Set<String> = emptySet(),
    /** آخرین بستهٔ حافظهٔ هر دستگاه: (شمار، اثر) */
    val memory: Map<String, Pair<Int, String>> = emptyMap(),
    /** سطرهای نادرست (نیمه‌نوشته یا دست‌خورده) */
    val broken: Int = 0,
    /** شمارِ سطرها — برای تصمیمِ فشرده‌سازی */
    val lines: Int = 0,
  )

  fun replay(text: String?): State {
    if (text.isNullOrEmpty()) return State()
    val scans = LinkedHashMap<String, QueuedScan>()
    val taken = HashSet<String>()
    val keys = LinkedHashSet<String>()
    val memory = HashMap<String, Pair<Int, String>>()
    var broken = 0
    var lines = 0
    text.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
      lines++
      val p = open(line)
      if (p == null) { broken++; return@forEach }
      when (p[0]) {
        "S" -> {
          val s = runCatching {
            QueuedScan(
              id = p[1], device = p[2], atMs = p[3].toLong(), source = ScanSource.valueOf(p[4]), qty = p[5].toInt().coerceAtLeast(1),
              deviceTime = p[6].ifEmpty { null }, code = p.subList(7, p.size).joinToString(T.toString()),
            )
          }.getOrNull()
          if (s == null || s.code.isEmpty()) broken++
          else if (s.id !in scans && s.key !in keys) { scans[s.id] = s; keys += s.key }
        }
        "C" -> taken += p[1]
        "K" -> keys += p.subList(1, p.size).joinToString(T.toString())
        "M" -> p.getOrNull(2)?.toIntOrNull()?.let { n -> memory[Discovery.key(p[1])] = n to p.getOrElse(3) { "" } } ?: broken++
        else -> broken++
      }
    }
    return State(scans.values.filter { it.id !in taken }, keys, memory, broken, lines)
  }

  /** اسکنِ تازه پذیرفتنی است؟ — کدِ خالی یا کلیدِ دیده‌شده ⇒ نه */
  fun accepts(state: State, s: QueuedScan): Boolean = clean(s.code).isNotEmpty() && s.key !in state.keys

  /** پس از افزودن (همان ترتیبِ `replay`) */
  fun add(state: State, s: QueuedScan): State =
    if (!accepts(state, s)) state else state.copy(pending = state.pending + s.copy(code = clean(s.code)), keys = state.keys + s.key, lines = state.lines + 1)

  fun take(state: State, ids: Set<String>): State = state.copy(pending = state.pending.filter { it.id !in ids }, lines = state.lines + ids.size)

  /** وقتِ فشرده‌سازی: سطرهای زیادی که دیگر کاری نمی‌کنند */
  fun shouldCompact(state: State): Boolean = state.lines > 200 && state.lines > 4 * (state.pending.size + state.memory.size + 1)

  /** متنِ تازهٔ کاملِ دفتر: اسکن‌های مانده، کلیدِ آخرین [KEEP_KEYS] برداشته و اثرِ حافظهٔ هر دستگاه */
  fun compact(state: State): String {
    val live = state.pending.map { it.key }.toSet()
    val kept = state.keys.filter { it !in live }.takeLast(KEEP_KEYS)
    return (kept.map(::keyLine) + state.memory.map { (d, m) -> memory(d, m.first, m.second) } + state.pending.map(::scan))
      .joinToString("\n", postfix = "\n")
  }
}

/** هدفِ اسکن‌های در انتظار — فروش همیشه فاکتورِ نگه‌داشته است، هرگز فروشِ ثبت‌شدهٔ بی‌تأیید */
enum class ScanTarget { COUNT, RECEIVE, SALE }

/** یک کد با شمارش، به ترتیبِ نخستین دیدن */
data class CodeCount(val code: String, val count: Int, val firstAt: Long)

object PendingScans {
  data class Summary(val scans: Int, val units: Int, val codes: Int, val devices: Int, val fromMemory: Int, val firstAt: Long?)

  fun summary(list: List<QueuedScan>): Summary = Summary(
    scans = list.size,
    units = list.sumOf { it.qty },
    codes = list.map { it.code }.distinct().size,
    devices = list.map { Discovery.key(it.device) }.distinct().size,
    fromMemory = list.count { it.source == ScanSource.MEMORY },
    firstAt = list.minOfOrNull { it.atMs },
  )

  /** همان کد N بار ⇒ یک ردیف با شمارِ N (و `qty` ِ سطرِ حافظه افزوده) */
  fun group(list: List<QueuedScan>): List<CodeCount> {
    val out = LinkedHashMap<String, CodeCount>()
    list.forEach { s ->
      val c = out[s.code]
      out[s.code] = if (c == null) CodeCount(s.code, s.qty, s.atMs) else c.copy(count = c.count + s.qty)
    }
    return out.values.toList()
  }

  /** کدهایی که کالا ندارند (برای «ثبتِ کالا») و آن‌هایی که دارند */
  fun split(groups: List<CodeCount>, known: (String) -> Boolean): Pair<List<CodeCount>, List<CodeCount>> = groups.partition { known(it.code) }
}
