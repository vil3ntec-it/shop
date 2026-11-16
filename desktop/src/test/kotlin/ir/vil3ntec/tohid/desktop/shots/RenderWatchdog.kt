package ir.vil3ntec.tohid.desktop.shots

import org.junit.Assume
import org.junit.rules.TestRule
import org.junit.runner.Description
import org.junit.runners.model.Statement
import java.io.File
import java.lang.management.ManagementFactory
import java.lang.management.ThreadInfo

/**
 *  مهلتِ هر آزمونِ کشیدن + رونوشتِ همهٔ نخ‌ها هنگامِ گیر — کارِ «(گزارش)» ِ desktop-shots.yml تقریباً یک بار از سه بار
 *  تا سقفِ ۴۰ دقیقه بی هیچ خروجی می‌ماند (روی کامپیوترِ خودی هرگز).
 *
 *  آزمون در نخِ daemon ِ خودش اجرا می‌شود؛ اگر تا [limitMs] تمام نشد:
 *    • همهٔ نخ‌ها با stack ِ کامل، قفل‌های گرفته و منتظرش، و بن‌بست‌های `ThreadMXBean.findDeadlockedThreads` در
 *      `thread-dump-<آزمون>.txt` ِ پوشهٔ `TOHID_RENDER_OUT` (artifact ِ همان کار) و stderr؛
 *    • آزمون با همان متن شکست می‌خورد (CI پیام و stack را چاپ می‌کند) و
 *    • آزمون‌های بعدیِ همین کلاس کنار می‌روند: نخِ گیرکرده قفلش را رها نمی‌کند و هر آزمونِ بعدی همان‌جا می‌ماند.
 *  نخِ daemon ⇒ JVM ِ آزمون با نخِ گیرکرده هم بسته می‌شود.
 */
class RenderWatchdog(private val limitMs: Long, private val out: () -> File? = { RenderReport.out }) : TestRule {

  companion object {
    /** کلاسِ آزمون ⇐ نامِ آزمونی که در آن گیر کرد (هر کلاس جدا؛ نمونهٔ قاعده با هر آزمون تازه ساخته می‌شود) */
    private val stuck = java.util.concurrent.ConcurrentHashMap<String, String>()

    /** همهٔ نخ‌ها، بی سقفِ ۸ قابِ `ThreadInfo.toString` */
    fun dump(): String = buildString {
      val mx = ManagementFactory.getThreadMXBean()
      val dead = runCatching { mx.findDeadlockedThreads() }.getOrNull()?.toSet().orEmpty()
      append("بن‌بست: ").append(if (dead.isEmpty()) "ندارد" else dead.joinToString()).append('\n')
      mx.dumpAllThreads(mx.isObjectMonitorUsageSupported, mx.isSynchronizerUsageSupported).forEach { appendThread(it, it.threadId in dead) }
    }

    private fun StringBuilder.appendThread(t: ThreadInfo, deadlocked: Boolean) {
      append('\n').append('"').append(t.threadName).append("\" #").append(t.threadId).append(' ').append(t.threadState)
      if (deadlocked) append(" [DEADLOCK]")
      t.lockName?.let { append(" on ").append(it) }
      t.lockOwnerName?.let { append(" owned by \"").append(it).append("\" #").append(t.lockOwnerId) }
      append('\n')
      val monitors = t.lockedMonitors.groupBy { it.lockedStackDepth }
      t.stackTrace.forEachIndexed { i, f ->
        append("\tat ").append(f).append('\n')
        monitors[i]?.forEach { m -> append("\t- locked ").append(m).append('\n') }
      }
      t.lockedSynchronizers.takeIf { it.isNotEmpty() }?.let { append("\tLocked synchronizers: ").append(it.joinToString()).append('\n') }
    }
  }

  override fun apply(base: Statement, description: Description): Statement = object : Statement() {
    override fun evaluate() {
      val cls = description.className ?: ""
      stuck[cls]?.let { Assume.assumeTrue("آزمونِ پیشینِ همین کلاس گیر کرد ($it) — نخش هنوز قفل را دارد", false) }
      var failure: Throwable? = null
      val name = description.methodName ?: description.displayName
      val runner = Thread({
        try { base.evaluate() } catch (t: Throwable) { failure = t }
      }, "render-test").apply { isDaemon = true }
      runner.start()
      runner.join(limitMs)
      if (runner.isAlive) {
        stuck[cls] = name
        val text = dump()
        val file = out()?.let { o ->
          runCatching { o.mkdirs(); File(o, "thread-dump-${name.replace(Regex("[^\\p{L}\\p{N}._-]+"), "_").take(80)}.txt").apply { writeText(text) } }.getOrNull()
        }
        System.err.println("RENDER گیر کرد: $name پس از ${limitMs / 1000}s\n$text")
        throw AssertionError("کشیدنِ «$name» پس از ${limitMs / 1000}s تمام نشد${file?.let { " — رونوشتِ نخ‌ها: ${it.path}" } ?: ""}\n" + text.take(60_000))
      }
      failure?.let { throw it }
    }
  }
}
