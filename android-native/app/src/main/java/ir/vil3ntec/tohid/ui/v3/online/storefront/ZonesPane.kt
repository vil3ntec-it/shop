package ir.vil3ntec.tohid.ui.v3.online.storefront

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.ToggleOn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.OnlineFmt
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.core.storefront.StorefrontDelivery
import ir.vil3ntec.tohid.core.storefront.StorefrontOwner
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.PhoneV3Roles
import ir.vil3ntec.tohid.ui.v3.LocalV3Toasts
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.V3AddChip
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3Divider
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3ListRow
import ir.vil3ntec.tohid.ui.v3.V3Section
import ir.vil3ntec.tohid.ui.v3.V3Segmented
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow

/** کادرهای «ارسال و زمان» همان‌طور که کاربر نوشته — سنجش و بدنه با `StorefrontDelivery.check` */
data class ZonesForm(
  val zones: List<StorefrontDelivery.ZoneDraft> = emptyList(),
  val rules: List<StorefrontDelivery.RuleDraft> = emptyList(),
  val asap: Boolean = true,
  val slotDays: String = "7",
)

/**
 *  برگهٔ «ارسال و زمان» (پرچمِ جدای `zones`): هر ناحیه یک کارت (نام، چندضلعی | دایره، هزینه، حداقل،
 *  «رایگان از…»، فعال) و هر بازه یک کارت (ارسال | دریافت، روزها، ساعت، ظرفیت، مهلت، فعال). برداشتن
 *  با «برگرداندن»؛ خطا زیرِ همان کادر (کلیدهای `z0.name` …)؛ «ذخیره» نوارِ چسبانِ پایین است.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ZonesPane(form: ZonesForm, errors: Map<String, String>, onChange: (ZonesForm) -> Unit, pad: PaddingValues) {
  val toasts = LocalV3Toasts.current
  Column(
    Modifier.fillMaxSize().verticalScroll(rememberScrollState())
      .padding(start = 16.dp, end = 16.dp, bottom = pad.calculateBottomPadding()),
  ) {
    V3Section(StrOnline.dzZones, footer = StrOnline.dzZonesHelp) {
      if (form.zones.isEmpty()) V3ListRow(StrOnline.dzNoZones, tile = false, chevron = false)
      form.zones.forEachIndexed { i, z ->
        if (i > 0) V3Divider()
        ZoneCard(i, z, errors, onChange = { n -> onChange(form.copy(zones = form.zones.toMutableList().also { it[i] = n })) }) {
          val before = form
          onChange(form.copy(zones = form.zones.filterIndexed { j, _ -> j != i }))
          toasts?.undo(StrOnline.dzZoneRemoved) { onChange(before) }
        }
      }
      V3Divider()
      FlowRow(Modifier.padding(12.dp)) { V3AddChip(StrOnline.dzAddZone) { onChange(form.copy(zones = form.zones + StorefrontDelivery.ZoneDraft())) } }
    }

    V3Section(StrOnline.dzSlots, footer = StrOnline.dzSlotsHelp) {
      V3ToggleRow(StrOnline.dzAsap, form.asap, { onChange(form.copy(asap = it)) }, icon = Icons.Outlined.Schedule, hue = V3Hue.AMBER)
      V3Divider()
      V3TextField(
        form.slotDays, { onChange(form.copy(slotDays = it)) }, StrOnline.dzDays, kind = V3FieldKind.NUMBER,
        error = errors["slotDays"], modifier = Modifier.fillMaxWidth().padding(12.dp),
      )
      if (form.rules.isEmpty()) { V3Divider(); V3ListRow(StrOnline.dzNoRules, tile = false, chevron = false) }
      form.rules.forEachIndexed { i, r ->
        V3Divider()
        RuleCard(i, r, errors, onChange = { n -> onChange(form.copy(rules = form.rules.toMutableList().also { it[i] = n })) }) {
          val before = form
          onChange(form.copy(rules = form.rules.filterIndexed { j, _ -> j != i }))
          toasts?.undo(StrOnline.dzRuleRemoved) { onChange(before) }
        }
      }
      V3Divider()
      FlowRow(Modifier.padding(12.dp)) { V3AddChip(StrOnline.dzAddRule) { onChange(form.copy(rules = form.rules + StorefrontDelivery.RuleDraft())) } }
    }
    Spacer(Modifier.height(24.dp))
  }
}

@Composable
private fun ZoneCard(i: Int, z: StorefrontDelivery.ZoneDraft, errors: Map<String, String>, onChange: (StorefrontDelivery.ZoneDraft) -> Unit, onRemove: () -> Unit) {
  val k = "z$i"
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    V3TextField(z.name, { onChange(z.copy(name = it)) }, StrOnline.dzZoneN(i + 1) + " — " + StrOnline.dzName, error = errors["$k.name"], modifier = Modifier.fillMaxWidth())
    V3Segmented(listOf(StrOnline.dzPolygon, StrOnline.dzRadius), if (z.kind == "radius") 1 else 0, { onChange(z.copy(kind = if (it == 1) "radius" else "polygon")) })
    if (z.kind == "radius") {
      Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        V3TextField(z.center, { onChange(z.copy(center = it)) }, StrOnline.dzCenter, placeholder = "34.53, 69.17", error = errors["$k.center"], modifier = Modifier.weight(1.6f))
        V3TextField(z.radiusKm, { onChange(z.copy(radiusKm = it)) }, StrOnline.dzRadiusKm, kind = V3FieldKind.NUMBER, error = errors["$k.radius"], modifier = Modifier.weight(1f))
      }
    } else {
      V3TextField(z.polygon, { onChange(z.copy(polygon = it)) }, StrOnline.dzPoints, placeholder = "34.50, 69.12", error = errors["$k.polygon"], singleLine = false, modifier = Modifier.fillMaxWidth())
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(z.fee, { onChange(z.copy(fee = it)) }, StrOnline.deliveryFee, kind = V3FieldKind.MONEY, error = errors["$k.fee"], modifier = Modifier.weight(1f))
      V3TextField(z.min, { onChange(z.copy(min = it)) }, StrOnline.sfMinOrder, kind = V3FieldKind.MONEY, error = errors["$k.min"], modifier = Modifier.weight(1f))
    }
    V3TextField(z.free, { onChange(z.copy(free = it)) }, StrOnline.dzFree, kind = V3FieldKind.MONEY, error = errors["$k.free"], modifier = Modifier.fillMaxWidth())
  }
  V3ToggleRow(StrOnline.dzZoneActive, z.active, { onChange(z.copy(active = it)) }, icon = Icons.Outlined.ToggleOn, hue = V3Hue.MINT)
  V3ListRow(StrOnline.dzRemoveZone, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = onRemove)
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RuleCard(i: Int, r: StorefrontDelivery.RuleDraft, errors: Map<String, String>, onChange: (StorefrontDelivery.RuleDraft) -> Unit, onRemove: () -> Unit) {
  val k = "r$i"
  Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
    Text(StrOnline.dzRuleN(i + 1), style = V3Type.subhead, color = PhoneV3.colors.ink)
    V3Segmented(listOf(StrOnline.delivery, StrOnline.sfPickupToggle), if (r.fulfillment == "pickup") 1 else 0, { onChange(r.copy(fulfillment = if (it == 1) "pickup" else "delivery")) })
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      StorefrontOwner.DISPLAY_DAYS.forEach { day ->
        val on = day in r.weekdays
        V3Chip(OnlineFmt.dayName(day), on, { onChange(r.copy(weekdays = if (on) r.weekdays - day else r.weekdays + day)) })
      }
    }
    errors["$k.days"]?.let { Text(it, style = V3Type.footnote, color = PhoneV3Roles.errorText(PhoneV3.colors)) }
    V3TextField(r.range, { onChange(r.copy(range = it)) }, StrOnline.dzRange, placeholder = "10:00-12:00", error = errors["$k.range"], modifier = Modifier.fillMaxWidth())
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      V3TextField(r.capacity, { onChange(r.copy(capacity = it)) }, StrOnline.dzCapacity, kind = V3FieldKind.NUMBER, error = errors["$k.capacity"], modifier = Modifier.weight(1f))
      V3TextField(r.cutoff, { onChange(r.copy(cutoff = it)) }, StrOnline.dzCutoff, kind = V3FieldKind.NUMBER, error = errors["$k.cutoff"], modifier = Modifier.weight(1f))
    }
  }
  V3ToggleRow(StrOnline.dzRuleActive, r.active, { onChange(r.copy(active = it)) }, icon = Icons.Outlined.ToggleOn, hue = V3Hue.MINT)
  V3ListRow(StrOnline.dzRemoveRule, icon = Icons.Outlined.DeleteOutline, danger = true, chevron = false, onClick = onRemove)
}
