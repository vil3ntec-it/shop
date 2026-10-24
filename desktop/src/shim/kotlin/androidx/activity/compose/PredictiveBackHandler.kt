package androidx.activity.compose

import androidx.activity.BackEventCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/**
 *  برگشتِ پیش‌نمای اندروید روی کامپیوتر: انگشتی نیست، پس همان برگشتِ ساده (Esc / دکمهٔ «عقب»ِ ماوس)
 *  — `onBack` با جریانی خالی صدا زده می‌شود، یعنی «بی پیش‌نمایش، برگشت انجام شد».
 */
@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<BackEventCompat>) -> Unit) {
  val scope = rememberCoroutineScope()
  val current = rememberUpdatedState(onBack)
  BackHandler(enabled) { scope.launch { current.value(emptyFlow()) } }
}
