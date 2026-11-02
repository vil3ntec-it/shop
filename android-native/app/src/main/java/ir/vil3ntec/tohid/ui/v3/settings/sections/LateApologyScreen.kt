package ir.vil3ntec.tohid.ui.v3.settings.sections

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrLateApology
import ir.vil3ntec.tohid.core.orders.LateApology
import ir.vil3ntec.tohid.data.repo.LateApologyRepository
import ir.vil3ntec.tohid.ui.screens.LateApologyForm
import ir.vil3ntec.tohid.ui.screens.LateApologyLive
import ir.vil3ntec.tohid.ui.screens.lateApologyApi
import ir.vil3ntec.tohid.ui.screens.lateApologyKinds
import ir.vil3ntec.tohid.ui.screens.lateApologyStamp
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.nav.RouteScope
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote
import ir.vil3ntec.tohid.ui.v3.settings.SettingsPageFrame
import kotlinx.coroutines.launch

/**
 *  `settings/late-apology` (NTF-10): همان بخشِ «پوزش و کوپنِ خودکارِ تأخیر» ِ رابطِ امروز (`LateApologySettings`) — همان
 *  حالت (`LateApologyForm`) و همان نوشتن (`LateApologyLive.set`، `SettingsPortTest`)؛ فقط رویِ کیت. ردیفش در تنظیمات
 *  فقط با پرچمِ روشنِ سرور است (`SettingsModule`)؛ نشانیِ مستقیم با پرچمِ خاموش فقط «خاموش» می‌گوید.
 */
@Composable
internal fun RouteScope.LateApologyScreen(onBack: (() -> Unit)?) {
  val api = lateApologyApi()
  val src = remember(api) { api?.let(::LateApologyRepository) }
  val s = remember(src) { src?.let(::LateApologyForm) }
  val scope = rememberCoroutineScope()
  LaunchedEffect(s) { s?.load() }
  val v = s?.view
  SettingsPageFrame(title = StrLateApology.title, subtitle = s?.subtitle() ?: StrLateApology.off, onBack = onBack) {
    if (s == null || src == null || v == null || !v.enabled) {
      item(key = "off") { SettingsNote(StrLateApology.off, Modifier.padding(top = 8.dp)) }
      return@SettingsPageFrame
    }
    item(key = "note") { SettingsNote(StrLateApology.note, Modifier.padding(vertical = 8.dp)) }
    item(key = "policy") {
      val f = s.form
      V3Section(StrLateApology.title, footer = if (s.editable) null else StrLateApology.readOnly) {
        if (!s.editable) {
          for (l in s.summary()) Text(l, style = V3Type.callout, color = PhoneV3.colors.ink, modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp))
        } else {
          val pad = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
          V3Segmented(listOf(StrLateApology.off, StrLateApology.switchOn), if (f.enabled) 1 else 0, { s.edit(f.copy(enabled = it == 1)) }, pad.fillMaxWidth())
          V3TextField(f.minutes, { s.edit(f.copy(minutes = it)) }, StrLateApology.minutes, pad.fillMaxWidth(), kind = V3FieldKind.NUMBER, error = s.error(LateApology.Bad.MINUTES))
          Text(StrLateApology.coupon, style = V3Type.subhead, color = PhoneV3.colors.inkMuted, modifier = pad)
          val kinds = lateApologyKinds
          V3Segmented(kinds.map { it.second }, kinds.indexOfFirst { it.first == f.kind }.coerceAtLeast(0), { s.edit(f.copy(kind = kinds[it].first)) }, pad.fillMaxWidth())
          if (f.kind == LateApology.AMOUNT) {
            V3TextField(f.amount, { s.edit(f.copy(amount = it)) }, StrLateApology.amount, pad.fillMaxWidth(), kind = V3FieldKind.MONEY, error = s.error(LateApology.Bad.AMOUNT))
          }
          if (f.kind == LateApology.PERCENT) {
            V3TextField(f.percent, { s.edit(f.copy(percent = it)) }, StrLateApology.percent, pad.fillMaxWidth(), kind = V3FieldKind.NUMBER, error = s.error(LateApology.Bad.PERCENT), suffix = "%")
          }
          if (f.kind != LateApology.NONE) {
            V3TextField(f.days, { s.edit(f.copy(days = it)) }, StrLateApology.days, pad.fillMaxWidth(), kind = V3FieldKind.NUMBER, error = s.error(LateApology.Bad.DAYS))
          }
          Spacer(Modifier.height(4.dp))
          V3Button(
            StrLateApology.save,
            { scope.launch { s.save { LateApologyLive.set(src, it) }?.let { toasts.show(it) } } },
            pad.fillMaxWidth(), enabled = !s.busy && s.changed, loading = s.busy,
          )
        }
      }
    }
    val recent = s.recentLines(::lateApologyStamp)
    if (recent.isNotEmpty()) {
      item(key = "recent") {
        V3Section(StrLateApology.recent) {
          for (l in recent) Text(l, style = V3Type.footnote, color = PhoneV3.colors.inkMuted, modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp))
        }
      }
    }
  }
}
