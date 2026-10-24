package ir.vil3ntec.tohid.ui.v3.settings.sections

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrSettings
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3Type

/**
 *  تأییدِ بازیابی — برگه، نه پنجره: هشدارِ امروز، «الان ⇐ پس از بازیابی» برای هر
 *  بخش (کاهش به رنگِ هشدار، نه رنگِ تنها) و شمارِ عکس‌های فایل. بازیابی یک‌طرفه
 *  است، پس دکمهٔ خطر؛ نسخهٔ ایمنیِ وضعِ فعلی نگه داشته می‌شود.
 */
@Composable
internal fun RestoreSheet(current: ShopData, incoming: ShopData, photos: Int, onConfirm: () -> Unit, onDismiss: () -> Unit) {
  val c = PhoneV3.colors
  //  بستن همیشه `onDismiss` ِ برگه را صدا می‌زند؛ این نشان می‌گوید بستن برای «بازیابی» بود
  var confirmed by remember { mutableStateOf(false) }
  V3Sheet(onDismiss = { if (confirmed) onConfirm() else onDismiss() }, title = StrSettings.restoreQ) { closer ->
    Text(StrSettings.restoreWarn, style = V3Type.callout, color = c.inkMuted, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    Spacer(Modifier.height(14.dp))
    V3Card {
      val lines = listOf(
        Str.products to (current.products.size to incoming.products.size),
        Str.invoices to (current.sales.size to incoming.sales.size),
        Str.debtors to (current.debtors.size to incoming.debtors.size),
        Str.expenses to (current.expenses.size to incoming.expenses.size),
        StrSettings.warehouseEntries to (current.warehouseEntries.size to incoming.warehouseEntries.size),
      )
      lines.forEachIndexed { i, (label, counts) ->
        if (i > 0) V3Divider(start = 16.dp)
        val (now, next) = counts
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
          Text(label, style = V3Type.body, color = c.ink, modifier = Modifier.weight(1f))
          Text(
            "$now ← $next",
            style = V3Type.subhead,
            color = if (next < now) PhoneV3Roles.toneIcon(c, ir.vil3ntec.tohid.ui.theme.V3Tone.WARNING) else c.inkMuted,
          )
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Text(
      if (photos > 0) StrSettings.restorePhotos(photos) else StrSettings.restoreNoPhotos,
      style = V3Type.footnote,
      color = c.inkMuted,
      textAlign = TextAlign.Center,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(18.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3Button(StrKit.cancel, { closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.NEUTRAL)
      V3Button(StrSettings.restore, { confirmed = true; closer.close() }, Modifier.weight(1f), kind = V3ButtonKind.DANGER, haptic = true)
    }
  }
}
