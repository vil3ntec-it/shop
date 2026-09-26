package androidx.activity.compose

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/**
 *  «برگشت»ِ اندروید روی کامپیوتر: کلیدِ Esc و دکمهٔ «عقب»ِ ماوس.
 *
 *  هر `BackHandler` در یک پشته ثبت می‌شود و آخرین روشن جواب می‌دهد —
 *  همان قاعدهٔ `OnBackPressedDispatcher`. پنجره (`Main.kt`) کلید را به
 *  `BackDispatcher.dispatch()` می‌دهد؛ اگر کسی جواب نداد، برنامه کاری
 *  نمی‌کند (نه این‌که بسته شود — روی کامپیوتر بستن دکمهٔ خودش را دارد).
 */
object BackDispatcher {
  internal class Entry(var enabled: Boolean, var onBack: () -> Unit)
  private val stack = ArrayList<Entry>()

  internal fun add(e: Entry) = synchronized(stack) { stack.add(e) }
  internal fun remove(e: Entry) = synchronized(stack) { stack.remove(e) }

  /** @return `true` یعنی کسی برگشت را گرفت */
  fun dispatch(): Boolean {
    val top = synchronized(stack) { stack.lastOrNull { it.enabled } } ?: return false
    top.onBack()
    return true
  }

  fun hasHandler(): Boolean = synchronized(stack) { stack.any { it.enabled } }
}

@Composable
fun BackHandler(enabled: Boolean = true, onBack: () -> Unit) {
  val current = rememberUpdatedState(onBack)
  val entry = remember { BackDispatcher.Entry(enabled) { current.value() } }
  SideEffect { entry.enabled = enabled }
  DisposableEffect(entry) {
    BackDispatcher.add(entry)
    onDispose { BackDispatcher.remove(entry) }
  }
}
