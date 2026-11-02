package ir.vil3ntec.tohid.scan

import android.content.Context
import androidx.compose.runtime.mutableStateOf
import ir.vil3ntec.tohid.core.hw.MemoryImport
import ir.vil3ntec.tohid.core.hw.MemoryLine
import ir.vil3ntec.tohid.core.hw.QueuedScan
import ir.vil3ntec.tohid.core.hw.ScanJournal
import ir.vil3ntec.tohid.core.hw.ScanSource
import ir.vil3ntec.tohid.core.hw.ScanTarget
import ir.vil3ntec.tohid.core.hw.Discovery
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/**
 *  صندوقِ اسکن‌های پس‌زمینه — پروندهٔ افزودنیِ `files/scan-inbox.log` (منطقِ دفتر: `core/hw/ScanQueue.kt`).
 *
 *  هر اسکنی که از بارکدخوانِ وصل‌شدهٔ «دستگاه‌ها» (سرویسِ پیش‌روی اندروید، سینیِ کامپیوتر) می‌رسد از این‌جا می‌گذرد:
 *  - برنامه جلوی چشم است و صفحه‌ای گوش می‌دهد ⇒ همان دم به `ScanRouter` (مثلِ گوشیِ جفت‌شده)؛
 *  - وگرنه ⇒ یک سطر به ته پرونده با `fsync` و «N اسکن در انتظار» با باز شدنِ برنامه.
 *  بستهٔ حافظهٔ بارکدخوان (`BurstGate`) **همیشه** به صف می‌رود، حتی با برنامهٔ باز — صد کد یک‌باره به سبد نمی‌ریزد.
 *
 *  هر اسکن فقط وقتی برداشته می‌شود که به مقصدش رسید (`take`)؛ قطعِ برق یا بستنِ برنامه پیش از آن چیزی را گم نمی‌کند.
 *  ⛔ این‌جا هیچ فروشی ثبت نمی‌شود — فروش فقط فاکتورِ نگه‌داشته می‌شود (`ScanSaleHold`) و با «پرداخت» ِ دستی.
 */
object ScanInbox {
  const val FILE = "scan-inbox.log"

  /** برنامه جلوی چشم است — اندروید `MainActivity.onResume/onPause`، کامپیوتر پنجرهٔ پیدا */
  @Volatile var foreground: Boolean = false

  /** ریشهٔ برنامه گوش می‌دهد (`AppRoot`) — بی آن، اسکنِ زنده هم به صف می‌رود */
  @Volatile var collecting: Boolean = false

  /** اسکن‌های زنده برای `ScanRouter` — ریشهٔ برنامه می‌خواند */
  val live = Channel<String>(Channel.UNLIMITED)

  private val _count = MutableStateFlow(0)
  /** شمارِ اسکن‌های در انتظار */
  val count: StateFlow<Int> = _count.asStateFlow()

  /** بارکدخوانِ حالتِ صفحه‌کلید روی همین دستگاه دیده شد (`HidScan`) — راهنمای «حالتِ SPP» در «دستگاه‌ها» */
  val keyboardSeen = mutableStateOf(false)

  /** مقصدی که صاحب‌کار برگزید و صفحه‌اش هنوز برنداشته (`ScanHandoff`) */
  val handoff = mutableStateOf<ScanHandoff?>(null)

  private var file: File? = null
  private var state = ScanJournal.State()

  /** بار اول پرونده خوانده می‌شود؛ بعد همان حالتِ حافظه */
  @Synchronized fun load(context: Context): ScanInbox {
    if (file != null) return this
    val f = File((context.applicationContext ?: context).filesDir, FILE)
    state = ScanJournal.replay(runCatching { if (f.isFile) f.readText(Charsets.UTF_8) else null }.getOrNull())
    file = f
    _count.value = state.pending.size
    return this
  }

  /** فقط برای آزمون: پرونده‌ای جدا */
  @Synchronized fun openForTest(f: File) { file = f; state = ScanJournal.replay(if (f.isFile) f.readText() else null); _count.value = state.pending.size }

  private fun append(lines: List<String>) {
    val f = file ?: return
    if (lines.isEmpty()) return
    FileOutputStream(f, true).use { out ->
      out.write(lines.joinToString("\n", postfix = "\n").toByteArray(Charsets.UTF_8))
      out.flush()
      runCatching { out.fd.sync() }
    }
  }

  private fun compactIfNeeded() {
    val f = file ?: return
    if (!ScanJournal.shouldCompact(state)) return
    runCatching { ir.vil3ntec.tohid.data.DiskWrite.text(f, ScanJournal.compact(state)) }
      .onSuccess { state = ScanJournal.replay(f.readText(Charsets.UTF_8)) }
  }

  /**
   *  یک اسکنِ زندهٔ بارکدخوانِ [device] رسید. برنامه جلوی چشم ⇒ `live`؛ وگرنه صف.
   *  @return `true` اگر به صف رفت
   */
  @Synchronized fun arrive(device: String, raw: String, now: Long = System.currentTimeMillis()): Boolean {
    val code = ScanJournal.clean(raw)
    if (code.isEmpty()) return false
    if (foreground && collecting && live.trySend(code).isSuccess) return false
    return queue(listOf(QueuedScan(newId(), device, now, code)))
  }

  /** بستهٔ حافظهٔ بارکدخوان: فقط سطرهای تازه (اثرِ بستهٔ پیشین و کلیدِ تاریخ‌دارها)؛ شمارِ افزوده */
  @Synchronized fun importMemory(device: String, lines: List<String>, now: Long = System.currentTimeMillis()): Int {
    val items = lines.mapNotNull(MemoryLine::parse)
    if (items.isEmpty()) return 0
    val fresh = MemoryImport.fresh(items, state.memory[Discovery.key(device)])
    //  کلیدِ تکرار پایدار در هر واردکردن: همان (زمان، کد) چند بار در یک بسته ⇒ «#۱»، «#۲»… (از آغازِ کلِ بسته شمرده)؛
    //  سطرِ بی تاریخ زمانِ رسیدن + جایش در بسته را می‌گیرد تا دو اسکنِ یک کد یکی نشوند
    val seen = HashMap<String, Int>()
    val times = items.map { m -> m.time?.let { t -> val k = t + "|" + m.code; val n = seen[k] ?: 0; seen[k] = n + 1; if (n == 0) t else "$t#$n" } }
    val start = items.size - fresh.size
    val scans = fresh.mapIndexed { i, m -> QueuedScan(newId(), device, now + i, m.code, ScanSource.MEMORY, m.qty, times[start + i]) }
    val before = state.pending.size
    queue(scans, Triple(device, items.size, MemoryImport.hash(items)))
    return state.pending.size - before
  }

  /** [mark] = اثرِ بستهٔ حافظه (دستگاه، شمار، اثر) که کنارِ اسکن‌ها نوشته می‌شود */
  private fun queue(scans: List<QueuedScan>, mark: Triple<String, Int, String>? = null): Boolean {
    if (file == null) return false
    var next = state
    val lines = ArrayList<String>()
    scans.forEach { s -> if (ScanJournal.accepts(next, s)) { next = ScanJournal.add(next, s); lines += ScanJournal.scan(s.copy(code = ScanJournal.clean(s.code))) } }
    if (mark != null) {
      lines += ScanJournal.memory(mark.first, mark.second, mark.third)
      next = next.copy(memory = next.memory + (Discovery.key(mark.first) to (mark.second to mark.third)))
    }
    if (lines.isEmpty()) return false
    runCatching { append(lines) }.onFailure { return false }
    state = next
    _count.value = state.pending.size
    return true
  }

  @Synchronized fun pending(): List<QueuedScan> = state.pending

  /** اسکن‌هایی که به مقصد رسیدند — دیگر پرسیده نمی‌شوند */
  @Synchronized fun take(ids: Set<String>) {
    if (ids.isEmpty()) return
    runCatching { append(ids.map(ScanJournal::taken)) }.onFailure { return }
    state = ScanJournal.take(state, ids)
    _count.value = state.pending.size
    compactIfNeeded()
  }

  /** «کنار گذاشتن» به درخواستِ صاحب‌کار — همه برداشته می‌شوند و کلیدشان می‌ماند تا دوباره نیایند */
  fun discardAll() = take(pending().map { it.id }.toSet())

  /** مقصدِ برگزیده برای صفحه‌اش آماده می‌شود؛ اسکن‌ها فقط با `ScanHandoff.take` برداشته می‌شوند */
  fun handTo(target: ScanTarget) {
    val list = pending()
    if (list.isNotEmpty()) handoff.value = ScanHandoff(target, list)
  }

  private fun newId(): String = UUID.randomUUID().toString().replace("-", "").take(16)
}

/** اسکن‌هایی که به صفحهٔ انبارگردانی یا ورودِ کالا سپرده شدند */
data class ScanHandoff(val target: ScanTarget, val scans: List<QueuedScan>, val at: Long = System.currentTimeMillis()) {
  companion object {
    /** صفحه‌ای که در این مدت برنداشت (راهی که هنوز اسکن نمی‌گیرد) ⇒ اسکن‌ها دوباره پرسیده می‌شوند */
    const val EXPIRE_MS = 60_000L

    /** صفحهٔ همان مقصد برمی‌دارد: اسکن‌ها برداشته می‌شوند و همین برمی‌گردد؛ مقصدِ دیگر ⇒ `null` */
    fun take(target: ScanTarget): ScanHandoff? {
      val h = ScanInbox.handoff.value?.takeIf { it.target == target } ?: return null
      ScanInbox.handoff.value = null
      ScanInbox.take(h.scans.map { it.id }.toSet())
      return h
    }
  }
}
