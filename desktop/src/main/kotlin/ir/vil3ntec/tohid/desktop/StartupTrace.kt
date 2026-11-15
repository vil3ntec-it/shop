package ir.vil3ntec.tohid.desktop

import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import java.io.File

/**
 *  PERF-DESK — «برنامه چقدر دیر باز شد و روی این کامپیوتر با چه موتوری می‌کشد؟» بی ابزارِ بیرونی.
 *
 *  هر بار که برنامه باز می‌شود `files/startup.txt` ِ پوشهٔ داده (ویندوز: `%LOCALAPPDATA%\Tohid\files\startup.txt`) از نو
 *  نوشته می‌شود: از آغازِ فرایند تا `main`، تا پنجره، تا نخستین قاب و تا آماده شدنِ دفتر؛ موتورِ کشیدنِ Skiko
 *  (`DIRECT3D` / `OPENGL` / `SOFTWARE_*` — نرم‌افزاری یعنی کارتِ گرافیکِ این کامپیوتر به کار نیامد و هر قاب را پردازنده
 *  می‌کشد)، نسخهٔ JVM، heap ِ بیشینه، GC و شمارِ هسته. **هیچ دادهٔ دکان یا کاربری در آن نیست**؛ فقط عدد و نامِ موتور.
 *  همان سطر در stdout هم می‌آید (اجرای دستی، CI ِ ویندوز آن را چاپ می‌کند).
 *
 *  هزینه: چند `System.currentTimeMillis` در رشتهٔ اصلی؛ نوشتنِ فایل روی رشتهٔ جدا، یک بار، پس از نخستین قاب.
 */
internal object StartupTrace {
  private val mainAt = System.currentTimeMillis()
  private val marks = LinkedHashMap<String, Long>()
  @Volatile private var written = false
  @Volatile private var renderApi: String = "?"

  /** نقطه‌ای در راهِ باز شدن (نامِ لاتینِ کوتاه) — فقط نخستین بار */
  @Synchronized fun mark(name: String) { marks.putIfAbsent(name, System.currentTimeMillis()) }

  /** نخستین قابِ پنجره کشیده شد — گزارش یک بار و روی رشتهٔ جدا */
  fun firstFrame(context: android.content.Context, api: String) {
    renderApi = api
    mark("first-frame")
    flushLater(context)
  }

  /** دفتر از دیسک خوانده شد (ممکن است پیش یا پس از نخستین قاب باشد) */
  fun ledgerReady() = mark("ledger-ready")

  /** نخستین قاب با دفترِ خوانده‌شده (خانه یا صفحهٔ خوش‌آمد، نه اسکلتِ «در حالِ بار») */
  fun ledgerFrame(context: android.content.Context) {
    mark("ledger-frame")
    if (written) flushLater(context)
  }

  private fun flushLater(context: android.content.Context) {
    Thread({ runCatching { write(context) } }, "startup-trace").apply { isDaemon = true; start() }
  }

  @Synchronized private fun write(context: android.content.Context) {
    val text = report()
    File(context.filesDir, "startup.txt").writeText(text)
    if (!written) println("[startup] " + text.replace('\n', ' '))
    written = true
  }

  @Synchronized internal fun report(): String = buildString {
    val processAt = runCatching { ProcessHandle.current().info().startInstant().orElse(null)?.toEpochMilli() }.getOrNull()
    val rt = Runtime.getRuntime()
    val gc = runCatching { java.lang.management.ManagementFactory.getGarbageCollectorMXBeans().joinToString("+") { it.name } }.getOrDefault("?")
    appendLine("version=${ir.vil3ntec.tohid.BuildConfig.VERSION_NAME}")
    appendLine("os=${System.getProperty("os.name")} ${System.getProperty("os.version")} ${System.getProperty("os.arch")}")
    appendLine("jvm=${System.getProperty("java.version")} cpus=${rt.availableProcessors()} maxHeapMB=${rt.maxMemory() / 1_048_576} gc=$gc")
    appendLine("renderApi=$renderApi")
    if (processAt != null) appendLine("process→main=${mainAt - processAt}ms")
    for ((name, at) in marks) appendLine("main→$name=${at - mainAt}ms")
  }.trimEnd()
}

/**
 *  نشانه‌های قاب برای [StartupTrace] — درونِ پنجره، در تکهٔ کوچکِ خودش (عوض شدنِ `loaded` فقط همین تکه را دوباره می‌سازد).
 */
@androidx.compose.runtime.Composable
internal fun StartupMarks(store: ir.vil3ntec.tohid.data.ShopStore, context: android.content.Context, renderApi: () -> String) {
  androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; StartupTrace.firstFrame(context, renderApi()) }
  val loaded by store.loaded.collectAsState()
  if (loaded) androidx.compose.runtime.LaunchedEffect(Unit) { androidx.compose.runtime.withFrameNanos { }; StartupTrace.ledgerFrame(context) }
}
