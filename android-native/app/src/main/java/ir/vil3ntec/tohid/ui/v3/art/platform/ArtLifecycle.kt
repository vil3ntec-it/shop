package ir.vil3ntec.tohid.ui.v3.art.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.currentComposer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/*
 *  مرزِ پلتفرم برای صحنه‌های `ui/v3/art` (قاعدهٔ «فقط وقتی دیده می‌شود»، دستهٔ ۶۵): چرخهٔ عمرِ برنامه از
 *  `androidx.lifecycle` — همین یک‌جا، تا بقیهٔ موتورِ صحنه فقط Compose باشد (راهِ آیفون).
 */

/**
 *  آیا برنامه RESUMED است؟ صاحبِ چرخهٔ عمر را گوشی (Activity) و پنجرهٔ کامپیوتر می‌دهند؛ جایی که نیست (رسمِ بی‌پنجره در
 *  آزمون) ⇒ درست، تا همان رفتارِ همیشه بماند.
 */
@Composable
internal fun rememberResumed(): Boolean {
  val locals = currentComposer.currentCompositionLocalMap
  val owner = remember(locals) { runCatching { locals[LocalLifecycleOwner] }.getOrNull() } ?: return true
  val state by owner.lifecycle.currentStateAsState()
  return state.isAtLeast(Lifecycle.State.RESUMED)
}
