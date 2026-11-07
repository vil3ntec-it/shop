package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierArea
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.i18n.StrCourierArea
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.PhoneV3
import ir.vil3ntec.tohid.ui.theme.V3ButtonKind
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3Chip
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.V3Type
import ir.vil3ntec.tohid.ui.v3.online.DraftTag
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/**
 *  فرمِ پیک (`delivery/couriers/{id}/cap`) — فقط با دسترسیِ «پیک‌ها» (صاحب/مدیر): سقفِ نقد، فعال/غیرفعال و
 *  محدودهٔ خدمت (S83): تراشهٔ ناحیه‌های ارسالِ ویترین (`areaChoices` ِ سرور) و نامِ دیگری که در «ناحیه» ِ
 *  نشانیِ سفارش نوشته می‌شود. هیچ ناحیه = همه‌جا (رفتارِ پیشین). محدوده فقط اگر عوض شد فرستاده می‌شود.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun CourierCapSheet(cr: CourierRow, choices: List<String>, repo: DeliveryRepository, onDismiss: () -> Unit, onDone: () -> Unit) {
  val c = PhoneV3.colors
  val scope = rememberCoroutineScope()
  var cap by remember { mutableStateOf(fieldOf(cr.cashCapMinor)) }
  var active by remember { mutableStateOf(cr.active) }
  var areas by remember { mutableStateOf(cr.areas) }
  var other by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val capMinor = DeliveryForms.capMinor(cap)
  val full = areas.size >= CourierArea.MAX_AREAS
  val canAdd = !full && CourierArea.key(other).isNotEmpty() && !CourierArea.has(areas, other)
  fun addOther() { if (canAdd) { areas = CourierArea.toggle(areas, other); other = "" } }
  //  تراشه‌ها: ناحیه‌های ویترین، سپس نام‌های دیگرِ همین پیک (هر نام یک بار)
  val chips = CourierArea.clean(choices + areas)

  V3Sheet(onDismiss = onDismiss, title = StrCourierArea.sheetTitle(cr.name), detents = false) { _ ->
    V3TextField(
      cap, { cap = it.take(DeliveryForms.NUMBER_MAX) }, StrOnline.dlCapField, kind = V3FieldKind.MONEY,
      helper = StrOnline.dlCapHelper, error = if (capMinor == null) StrOnline.dlNumberRequired else null, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    V3Card(padding = PaddingValues(0.dp)) {
      V3ToggleRow(if (active) StrOnline.dlActiveOn else StrOnline.dlActiveOff, active, { active = it }, icon = Icons.Outlined.DeliveryDining, hue = V3Hue.SKY)
    }
    Spacer(Modifier.height(18.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      Text(StrCourierArea.section, style = V3Type.subhead, color = c.ink, modifier = Modifier.weight(1f))
      if (areas.isEmpty()) DraftTag(StrCourierArea.everywhere, tone = V3Tone.SUCCESS)
    }
    Spacer(Modifier.height(4.dp))
    Text(StrCourierArea.helper, style = V3Type.footnote, color = c.inkMuted)
    if (chips.isNotEmpty()) {
      Spacer(Modifier.height(10.dp))
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        chips.forEach { name ->
          val on = CourierArea.has(areas, name)
          V3Chip(name, on, { if (on || !full) areas = CourierArea.toggle(areas, name) })
        }
      }
    }
    Spacer(Modifier.height(10.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
      V3TextField(
        other, { other = it.take(CourierArea.MAX_LEN) }, StrCourierArea.otherField, modifier = Modifier.weight(1f),
        helper = StrCourierArea.otherHelper, enabled = !full, imeAction = ImeAction.Done, onImeAction = ::addOther,
      )
      Spacer(Modifier.width(8.dp))
      V3Button(StrKit.add, ::addOther, kind = V3ButtonKind.SECONDARY, enabled = canAdd, height = 44.dp)
    }
    if (full) { Spacer(Modifier.height(8.dp)); V3Message(V3Tone.INFO, StrCourierArea.tooMany(CourierArea.MAX_AREAS)) }
    err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrKit.save,
      {
        busy = true
        err = null
        scope.launch {
          val body = DeliveryWire.courierUpdateJson(cashCapMinor = capMinor, active = active, areas = areas.takeIf { it != cr.areas })
          repo.updateCourier(cr.id, body)
            .onSuccess { onDone() }
            .onFailure { err = it.userMessage }
          busy = false
        }
      },
      Modifier.fillMaxWidth(),
      enabled = capMinor != null && !busy,
      loading = busy,
      haptic = true,
    )
  }
}
