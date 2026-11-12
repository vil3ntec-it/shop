package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.backup.WipeAll
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.screens.v3Text
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField

/**
 *  OPS-02 — «پاک‌سازیِ کاملِ اطلاعات» در نسخهٔ ۳: همان دو مرحلهٔ `WipeAllDialog` ِ رابطِ پیشین در یک برگه.
 *  (۱) هشدارِ «از همهٔ دستگاه‌ها و سرور، برنمی‌گردد؛ اول پشتیبان» و «ادامه»؛ (۲) نوشتنِ نامِ دکان ([expected]) و فقط
 *  آن‌گاه «پاک کن». تصمیم با `WipeAll` (core)؛ [onErase] نوشتهٔ کاربر را می‌گیرد تا `BackupController.wipe` دوباره بسنجد.
 *  ⛔ ردیف و این برگه فقط برای صاحبِ دکان (`WipeAll.visible`، `BackupScreen`).
 */
@Composable
internal fun WipeAllSheet(expected: String, onErase: (typed: String) -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  var step by remember { mutableStateOf(WipeAll.Step.WARN) }
  var typed by remember { mutableStateOf("") }
  V3Sheet(onDismiss = onDismiss, detents = false) { closer ->
    val tone = PhoneV3Roles.tone(c, V3Tone.ERROR)
    Box(
      Modifier.align(Alignment.CenterHorizontally).size(56.dp).clip(RoundedCornerShape(50)).background(tone.bg),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = tone.fg, modifier = Modifier.size(26.dp)) }
    Spacer(Modifier.height(12.dp))
    val warn = step == WipeAll.Step.WARN
    Text(
      if (warn) StrSettings.wipeQ else StrSettings.wipeNameTitle,
      style = v3Text(18, FontWeight.Bold, 26), color = c.ink, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(6.dp))
    Text(
      if (warn) StrSettings.wipeWarn else StrSettings.wipeNameHint(expected),
      style = v3Text(14, FontWeight.Medium, 21), color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth(),
    )
    if (!warn) {
      Spacer(Modifier.height(14.dp))
      V3TextField(typed, { typed = it }, StrSettings.wipeNameLabel, Modifier.fillMaxWidth())
    }
    Spacer(Modifier.height(20.dp))
    val ok = !warn && WipeAll.matches(typed, expected)
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      if (warn) {
        V3Button(StrSettings.wipeNext, { typed = ""; step = WipeAll.Step.NAME }, Modifier.weight(1f), kind = V3ButtonKind.DANGER)
      } else {
        //  دکمهٔ بسته را کنشِ دسترس‌پذیری هم می‌زند ⇒ خودِ کار هم دوباره می‌سنجد
        V3Button(StrSettings.wipe, { if (WipeAll.matches(typed, expected)) closer.close { onErase(typed) } }, Modifier.weight(1f), kind = V3ButtonKind.DANGER, enabled = ok, haptic = true)
      }
    }
  }
}
