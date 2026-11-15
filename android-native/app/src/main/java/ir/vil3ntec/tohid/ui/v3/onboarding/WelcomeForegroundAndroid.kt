package ir.vil3ntec.tohid.ui.v3.onboarding

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState

/**
 *  ساعتِ صفحهٔ ورودِ قدیمی (`WelcomeClock.kt`): ریشهٔ دروازه‌ها (`AuthGate`، `ShopSetupGate`) که `LocalLifecycleOwner` آن‌جا همیشه هست —
 *  فعالیتِ اندروید، پنجرهٔ کامپیوتر، نمای آیفون. برنامه دست‌کم RESUMED (پیش‌رو، صفحهٔ گوشی روشن) ⇒ [LocalWelcomeForeground] روشن؛
 *  پس‌زمینه ⇒ موج‌ها و حلقه هیچ قابی نمی‌خواهند.
 */
@Composable
internal fun ProvideWelcomeForeground(content: @Composable () -> Unit) {
  val state by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
  CompositionLocalProvider(LocalWelcomeForeground provides state.isAtLeast(Lifecycle.State.RESUMED), content = content)
}
