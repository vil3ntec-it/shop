package ir.vil3ntec.tohid.ui.v3

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 *  PERF-SCREEN (`V3Live`): ریشهٔ برنامه — فعالیتِ اندروید (`MainActivity`) و پنجرهٔ کامپیوتر — که `LocalLifecycleOwner` آن‌جا
 *  حتماً هست: برنامه دست‌کم RESUMED (پیش‌رو، صفحهٔ گوشی روشن) ⇒ [LocalV3Foreground] روشن؛ پس‌زمینه ⇒ هیچ چیزِ زنده‌ای قاب نمی‌خواهد.
 */
@Composable
fun ProvideV3Foreground(content: @Composable () -> Unit) {
  val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  CompositionLocalProvider(LocalV3Foreground provides state.isAtLeast(Lifecycle.State.RESUMED), content = content)
}
