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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.StrFx
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.pay.FxRate
import ir.vil3ntec.tohid.core.pay.FxTill
import ir.vil3ntec.tohid.core.pay.PayResult
import ir.vil3ntec.tohid.data.FxTillFlow
import ir.vil3ntec.tohid.data.FxTillPref
import ir.vil3ntec.tohid.data.ReportBridge
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.TillUser
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.sales.history.SaleTime
import ir.vil3ntec.tohid.ui.v3.settings.SettingsBlock
import ir.vil3ntec.tohid.ui.v3.settings.SettingsNote

/**
 *  PAY-10 — بخشِ «پرداخت با ارزِ نقد» در `settings/sales/payments` (فقط با «ترکیبی و شیفت»): کلیدِ خاموش/روشنِ دکان
 *  (`FxTillPref`، پیش‌فرض خاموش) و با روشن، نرخِ هر ارز (دالر برای یک، کلدار برای هزار، ریال برای یک میلیون) با منبع.
 *  هر «ذخیره» زمانِ همین لحظه و نامِ کاربرِ صندوق را می‌گذارد (`FxTillFlow.withRate` ⇐ `FxTill.rates`) و نرخِ کهنه‌تر از
 *  یک روز با نشان دیده می‌شود. بخشِ تنظیمات فقط صاحب و مدیر (`Need.MANAGE`).
 */
@Composable
internal fun FxTillSettings() {
  val context = LocalContext.current
  var on by remember { mutableStateOf(FxTillPref.on(context)) }
  var rates by remember { mutableStateOf(FxTillPref.rates(context)) }
  SettingsBlock {
    PartTitle(StrFx.settingTitle)
    V3Segmented(
      options = listOf(StrShell.switchOff, StrShell.switchOn),
      selected = if (on) 1 else 0,
      onSelect = {
        on = it == 1
        FxTillPref.set(context, on)
      },
    )
  }
  SettingsNote(StrFx.settingNote)
  if (!on) return
  val now = ReportBridge.nowMs()
  FxTillFlow.CURRENCIES.forEach { code ->
    FxRateEditor(
      code = code,
      current = rates.firstOrNull { it.currency == code },
      now = now,
      onSave = { rateText, source ->
        val by = TillUser.me(context).ifBlank { ShopRole.current(context) }
        when (val r = FxTillFlow.withRate(rates, code, rateText, source, ReportBridge.nowMs(), by)) {
          is PayResult.Ok -> { rates = r.value; FxTillPref.saveRates(context, r.value); null }
          is PayResult.Err -> if (r.field == "source") StrFx.sourceBad else StrFx.rateBad
        }
      },
      onRemove = {
        rates = FxTillFlow.remove(rates, code)
        FxTillPref.saveRates(context, rates)
      },
    )
  }
}

/** یک ارز: خطِ نرخِ فعلی (با منبع، زمان، گذارنده و نشانِ کهنه)، کادرِ نرخ و منبع، «ذخیره» و «برداشتن» */
@Composable
private fun FxRateEditor(code: String, current: FxRate?, now: Long, onSave: (String, String) -> String?, onRemove: () -> Unit) {
  val c = PhoneV3.colors
  val per = FxTill.DEFAULT_PER[code] ?: 1L
  var rateText by remember(code, current) { mutableStateOf(current?.takeIf { it.per == per }?.let { FxTill.afnText(it.rate).replace(",", "") }.orEmpty()) }
  var source by remember(code, current) { mutableStateOf(current?.source.orEmpty()) }
  var message by remember(code, current) { mutableStateOf<String?>(null) }
  SettingsBlock {
    PartTitle(FxTillFlow.name(code))
    Text(
      current?.let { StrFx.setAt(FxTill.rateLine(it.rate, it.per, it.currency), it.source, SaleTime.stamp(it.at), it.by) } ?: StrFx.none,
      style = V3Type.footnote, color = c.inkMuted,
    )
    if (current != null && !FxTillFlow.fresh(current, now)) {
      Text(StrFx.staleTag, style = V3Type.footnote, color = PhoneV3Roles.errorText(c))
    }
    Spacer(Modifier.height(10.dp))
    V3TextField(
      value = rateText,
      onValueChange = { rateText = it; message = null },
      label = StrFx.rateLabel(ir.vil3ntec.tohid.money(per.toDouble()), code),
      kind = V3FieldKind.MONEY,
      modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(8.dp))
    V3TextField(
      value = source,
      onValueChange = { source = it; message = null },
      label = StrFx.sourceLabel,
      modifier = Modifier.fillMaxWidth(),
    )
    message?.let { Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(c), modifier = Modifier.padding(top = 6.dp)) }
    Spacer(Modifier.height(10.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      V3Button(StrFx.save, { message = onSave(rateText, source) })
      if (current != null) V3Button(StrFx.remove, onRemove, kind = V3ButtonKind.SECONDARY)
    }
  }
}
