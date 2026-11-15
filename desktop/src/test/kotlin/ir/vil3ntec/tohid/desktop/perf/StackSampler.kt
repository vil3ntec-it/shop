package ir.vil3ntec.tohid.desktop.perf

import java.util.Locale

/**
 *  نمونه‌برداری از پشتهٔ یک رشته (هر [everyMs] میلی‌ثانیه) — بی ابزارِ بیرونی، فقط برای یافتنِ گامِ گران در سنجه‌های دستی.
 *  «شامل» = هر تابعِ `ir.vil3ntec` که در نمونه بود (یک بار در هر نمونه)؛ «ته» = ژرف‌ترین تابعِ `ir.vil3ntec` ِ همان نمونه.
 */
internal class StackSampler(private val target: Thread, private val everyMs: Long = 1) : AutoCloseable {

  private val inclusive = HashMap<String, Int>()
  private val deepest = HashMap<String, Int>()
  @Volatile private var on = true
  var samples = 0
    private set

  private val worker = Thread {
    while (on) {
      val st = target.stackTrace
      val ours = st.filter { it.className.startsWith("ir.vil3ntec") && !it.className.startsWith("ir.vil3ntec.tohid.desktop.perf") }
      if (ours.isNotEmpty()) {
        synchronized(this) {
          samples++
          ours.map { "${it.className.substringAfterLast('.')}.${it.methodName}" }.toSet().forEach { inclusive[it] = (inclusive[it] ?: 0) + 1 }
          ours.first().let { "${it.className.substringAfterLast('.')}.${it.methodName}:${it.lineNumber}" }.let { deepest[it] = (deepest[it] ?: 0) + 1 }
        }
      }
      try { Thread.sleep(everyMs) } catch (_: InterruptedException) { return@Thread }
    }
  }.apply { isDaemon = true; start() }

  override fun close() { on = false; worker.interrupt(); worker.join(1_000) }

  @Synchronized fun report(top: Int = 10): String = buildString {
    fun line(k: String, v: Int) = appendLine("    %5d  %4.0f%%  %s".format(Locale.ROOT, v, 100.0 * v / samples.coerceAtLeast(1), k))
    appendLine("  samples=$samples — inclusive:")
    inclusive.entries.sortedByDescending { it.value }.take(top).forEach { line(it.key, it.value) }
    appendLine("  deepest:")
    deepest.entries.sortedByDescending { it.value }.take(top).forEach { line(it.key, it.value) }
  }
}
