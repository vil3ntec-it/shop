package ir.vil3ntec.tohid.ui.components.staff

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.perm.PinGuard
import ir.vil3ntec.tohid.ui.screens.Keypad
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  صفحهٔ PINِ شخصی — STF-04 (و تأییدِ مدیر، STF-02).
 *
 *  همان صفحه‌کلیدِ قفلِ برنامه (`Keypad`ِ `AppLock`): رقم‌های لمسی، ارقامِ
 *  صفحه‌کلیدِ سخت‌افزاری (لاتین، فارسی، عربی) و Backspace. با رقمِ ششم
 *  `onComplete` صدا زده و خانه‌ها خالی می‌شوند؛ درست/غلط را صدازننده می‌گوید
 *  (`error`). قفل‌شده ⇒ صفحه‌کلید نیست و فقط پیام می‌ماند.
 */
@Composable
fun PinPad(
  title: String,
  hint: String,
  onComplete: (String) -> Unit,
  modifier: Modifier = Modifier,
  error: String? = null,
  locked: Boolean = false,
  busy: Boolean = false,
  length: Int = PinGuard.NEW_LEN,
) {
  val colors = Shop.colors
  var pin by remember { mutableStateOf("") }
  Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
    Text(title, style = MaterialTheme.typography.titleMedium, color = colors.text)
    Spacer(Modifier.height(6.dp))
    Text(
      error ?: hint,
      style = MaterialTheme.typography.bodySmall,
      color = if (error != null) colors.danger else colors.muted,
      textAlign = TextAlign.Center,
      modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
    )
    Spacer(Modifier.height(16.dp))
    //  نقطه‌ها؛ برای صفحه‌خوان «چند رقم از چند»
    Row(
      horizontalArrangement = Arrangement.spacedBy(12.dp),
      modifier = Modifier.semantics { contentDescription = "${pin.length} رقم از $length" },
    ) {
      repeat(length) { i ->
        val filled = i < pin.length
        Box(
          Modifier
            .size(if (filled) 15.dp else 13.dp)
            .clip(CircleShape)
            .background(
              when {
                error != null && pin.isEmpty() -> colors.danger
                filled -> colors.primary
                else -> colors.surface2
              },
            ),
        )
      }
    }
    if (!locked) {
      Spacer(Modifier.height(20.dp))
      Keypad(
        onDigit = { d ->
          if (busy || pin.length >= length) return@Keypad
          pin += d
          if (pin.length == length) {
            val done = pin
            pin = ""
            onComplete(done)
          }
        },
        onBack = { if (!busy && pin.isNotEmpty()) pin = pin.dropLast(1) },
      )
    }
  }
}
