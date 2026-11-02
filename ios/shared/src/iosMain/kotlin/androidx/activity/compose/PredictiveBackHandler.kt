package androidx.activity.compose

import androidx.activity.BackEventCompat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

/** shimِ iOS — همان کامپیوتر: «بازگشت» ِ بی پیش‌نما روی همان `BackHandler` (‎BackHandler.kt‎) */
@Composable
fun PredictiveBackHandler(enabled: Boolean = true, onBack: suspend (progress: Flow<BackEventCompat>) -> Unit) {
  val scope = rememberCoroutineScope()
  val current = rememberUpdatedState(onBack)
  BackHandler(enabled) { scope.launch { current.value(emptyFlow()) } }
}
