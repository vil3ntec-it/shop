package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NewReleases
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.BuildConfig
import ir.vil3ntec.tohid.core.guide.WhatsNew
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.WhatsNewPref
import ir.vil3ntec.tohid.ui.theme.Shop

/**
 *  OPS-05 — مرزِ پلتفرمِ «چه چیزی تازه است» (خواندن و نوشتنِ `WhatsNewPref`، نسخهٔ `BuildConfig`).
 *
 *  [WhatsNewOnUpdate] در `AppRoot`: فقط با کلیدِ روشن؛ نخستین اجرا نسخهٔ نصب‌شده را «دیده‌شده» می‌گذارد و
 *  هیچ نشان نمی‌دهد؛ پس از به‌روزرسانی یادداشت‌های نسخه‌های تازه یک بار، و «فهمیدم» (یا بستن) ⇒ دیده‌شده.
 *  خاموش ⇒ هیچ خواندن و نوشتنی.
 */
@Composable
fun WhatsNewOnUpdate() {
  val context = LocalContext.current
  val current = BuildConfig.VERSION_NAME
  val pending = remember<List<WhatsNew.Note>> {
    if (!WhatsNewPref.on(context)) return@remember emptyList()
    val seen = WhatsNewPref.seen(context)
    if (seen == null) { WhatsNewPref.setSeen(context, WhatsNew.seenAfter(null, current)); emptyList() }
    else WhatsNew.pending(WhatsNew.ALL, seen, current)
  }
  var open by remember { mutableStateOf(pending.isNotEmpty()) }
  if (!open) return
  val groups = remember(pending) { pending.groupBy { WhatsNew.baseText(it.version).orEmpty() }.toList() }
  WhatsNewDialog(groups) {
    WhatsNewPref.setSeen(context, WhatsNew.seenAfter(WhatsNewPref.seen(context), current))
    open = false
  }
}

/** بخشِ «دربارهٔ برنامه»: کلیدِ نمایش (پیش‌فرض خاموش) و، با کلیدِ روشن، «دیدنِ همهٔ تغییرها» */
@Composable
fun WhatsNewSettingsRows() {
  val context = LocalContext.current
  var on by remember { mutableStateOf(WhatsNewPref.on(context)) }
  var all by remember { mutableStateOf(false) }
  Spacer(Modifier.height(12.dp))
  Text(StrSettings.whatsNewShow, style = MaterialTheme.typography.labelLarge, color = Shop.colors.text)
  Spacer(Modifier.height(6.dp))
  SegmentedChoice(
    options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
    selected = if (on) "on" else "off",
    onSelect = {
      on = it == "on"
      WhatsNewPref.setOn(context, on)
      //  روشن کردن یعنی «از همین نسخه به بعد»: یادداشت‌های گذشته ناگهان سر باز نمی‌کنند
      if (on && WhatsNewPref.seen(context) == null) WhatsNewPref.setSeen(context, WhatsNew.seenAfter(null, BuildConfig.VERSION_NAME))
    },
  )
  Text(StrSettings.whatsNewNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
  if (on) {
    Spacer(Modifier.height(8.dp))
    SettingsRow(
      icon = Icons.Filled.NewReleases,
      title = StrSettings.whatsNewAll,
      description = StrSettings.whatsNew,
      tint = Shop.colors.primary,
      onClick = { all = true },
    )
  }
  if (all) WhatsNewDialog(WhatsNew.history(WhatsNew.ALL, BuildConfig.VERSION_NAME)) { all = false }
}
