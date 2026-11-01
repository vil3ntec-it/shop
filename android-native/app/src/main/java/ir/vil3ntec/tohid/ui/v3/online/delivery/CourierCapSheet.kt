package ir.vil3ntec.tohid.ui.v3.online.delivery

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeliveryDining
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.delivery.CourierRow
import ir.vil3ntec.tohid.core.i18n.StrKit
import ir.vil3ntec.tohid.core.i18n.StrOnline
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.V3Tone
import ir.vil3ntec.tohid.ui.v3.V3Button
import ir.vil3ntec.tohid.ui.v3.V3Card
import ir.vil3ntec.tohid.ui.v3.V3FieldKind
import ir.vil3ntec.tohid.ui.v3.V3Hue
import ir.vil3ntec.tohid.ui.v3.V3Message
import ir.vil3ntec.tohid.ui.v3.V3Sheet
import ir.vil3ntec.tohid.ui.v3.V3TextField
import ir.vil3ntec.tohid.ui.v3.V3ToggleRow
import ir.vil3ntec.tohid.ui.v3.v3Shake
import kotlinx.coroutines.launch

/** سقفِ نقد و فعال/غیرفعالِ پیک (`delivery/couriers/{id}/cap`) — فقط با دسترسیِ «پیک‌ها» (صاحب/مدیر) */
@Composable
internal fun CourierCapSheet(cr: CourierRow, repo: DeliveryRepository, onDismiss: () -> Unit, onDone: () -> Unit) {
  val scope = rememberCoroutineScope()
  var cap by remember { mutableStateOf(fieldOf(cr.cashCapMinor)) }
  var active by remember { mutableStateOf(cr.active) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val capMinor = DeliveryForms.capMinor(cap)
  V3Sheet(onDismiss = onDismiss, title = StrOnline.dlCapTitle(cr.name), detents = false) { _ ->
    V3TextField(
      cap, { cap = it.take(DeliveryForms.NUMBER_MAX) }, StrOnline.dlCapField, kind = V3FieldKind.MONEY,
      helper = StrOnline.dlCapHelper, error = if (capMinor == null) StrOnline.dlNumberRequired else null, modifier = Modifier.fillMaxWidth(),
    )
    Spacer(Modifier.height(12.dp))
    V3Card(padding = PaddingValues(0.dp)) {
      V3ToggleRow(if (active) StrOnline.dlActiveOn else StrOnline.dlActiveOff, active, { active = it }, icon = Icons.Outlined.DeliveryDining, hue = V3Hue.SKY)
    }
    err?.let { Spacer(Modifier.height(12.dp)); V3Message(V3Tone.ERROR, it, Modifier.v3Shake(it)) }
    Spacer(Modifier.height(20.dp))
    V3Button(
      StrKit.save,
      {
        busy = true
        err = null
        scope.launch {
          repo.updateCourier(cr.id, DeliveryWire.courierUpdateJson(cashCapMinor = capMinor, active = active))
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
