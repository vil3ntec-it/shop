package ir.vil3ntec.tohid.ui.v3.nav

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.ime
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity

/**
 *  صفحه‌کلید باز است؟ (`ShellFrame.bar` نوارِ پایین را آن‌وقت کنار می‌گذارد.) فقط با **گذرِ** باز/بسته
 *  عوض می‌شود — نه در هر فریمِ بالا آمدنِ صفحه‌کلید — تا پوسته در حرکتِ صفحه‌کلید بازترکیب نشود.
 */
@Composable
fun rememberKeyboardOpen(): Boolean {
  val ime = WindowInsets.ime
  val density = LocalDensity.current
  val open by remember(ime, density) { derivedStateOf { ime.getBottom(density) > 0 } }
  return open
}
