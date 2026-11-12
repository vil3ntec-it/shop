package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.backup.WipeAll
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  «پاک‌سازی کامل اطلاعات» — دو مرحله: (۱) هشدار و «ادامه»؛ (۲) نوشتنِ نامِ دکان، و فقط آن‌گاه «پاک کن».
 *
 *  ⛔ این کار برای هر ردیف DELETE به سرور می‌فرستد و دفترِ دکان روی **همهٔ** دستگاه‌ها و سرور خالی می‌شود
 *  (`ShopStore.save` ⇐ `record`)، پس هشدار همین را می‌گوید و یک کلیکِ اشتباه کافی نیست. دکمه را فقط صاحبِ
 *  دکان می‌بیند (`Settings`). دکانِ بی‌نام (پیش از ثبت) به جای نام همان واژهٔ دکمه («پاک کن») را می‌خواهد.
 *  قاعده‌ها در `core/backup/WipeAll` (همان که برگهٔ نسخهٔ ۳، `WipeAllSheet`، دارد).
 *
 *  [onConfirm] فقط پس از هر دو مرحله صدا زده می‌شود؛ رفتارِ همگام‌سازی همان است که بود.
 */
@Composable
fun WipeAllDialog(shopName: String, onDismiss: () -> Unit, onConfirm: () -> Unit) {
  var typing by rememberSaveable { mutableStateOf(false) }
  var typed by rememberSaveable { mutableStateOf("") }
  if (!typing) {
    AlertDialog(
      onDismissRequest = onDismiss,
      containerColor = Shop.colors.surfaceSolid,
      title = { Text(StrSettings.wipeQ, color = Shop.colors.text) },
      text = { Text(StrSettings.wipeWarn, style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted) },
      confirmButton = {
        TextButton(onClick = { typed = ""; typing = true }) { Text(StrSettings.wipeNext, color = Shop.colors.danger) }
      },
      dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } },
    )
    return
  }
  val expected = WipeAll.expected(shopName, StrSettings.wipe)
  val ok = remember(typed, expected) { WipeAll.matches(typed, expected) }
  AlertDialog(
    onDismissRequest = onDismiss,
    containerColor = Shop.colors.surfaceSolid,
    title = { Text(StrSettings.wipeNameTitle, color = Shop.colors.text) },
    text = {
      Column {
        Text(StrSettings.wipeNameHint(expected), style = MaterialTheme.typography.bodySmall, color = Shop.colors.muted)
        Spacer(Modifier.height(8.dp))
        TohidTextField(
          value = typed,
          onValueChange = { typed = it },
          label = StrSettings.wipeNameLabel,
          modifier = Modifier.fillMaxWidth(),
        )
      }
    },
    confirmButton = {
      TextButton(onClick = onConfirm, enabled = ok) {
        Text(StrSettings.wipe, color = if (ok) Shop.colors.danger else Shop.colors.muted)
      }
    },
    dismissButton = { TextButton(onClick = onDismiss) { Text(Str.cancel) } },
  )
}
