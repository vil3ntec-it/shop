package androidx.activity.compose

import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState

/** shimِ iOS ِ `rememberLauncherForActivityResult` — همان شکلِ کامپیوتر (‎desktop/src/shim‎) */
class ManagedActivityResultLauncher<I, O> internal constructor(
  private val contract: ActivityResultContract<I, O>,
  private val result: () -> ((O) -> Unit),
) {
  fun launch(input: I) = contract.run(input) { out -> result()(out) }
  fun launch(input: I, options: Any?) = launch(input)
  fun unregister() {}
}

@Composable
fun <I, O> rememberLauncherForActivityResult(
  contract: ActivityResultContract<I, O>,
  onResult: (O) -> Unit,
): ManagedActivityResultLauncher<I, O> {
  val latest = rememberUpdatedState(onResult)
  return remember(contract::class) { ManagedActivityResultLauncher(contract) { latest.value } }
}
