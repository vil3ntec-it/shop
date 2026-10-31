package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import ir.vil3ntec.tohid.core.delivery.DeliveryText
import ir.vil3ntec.tohid.core.delivery.DeskActions
import ir.vil3ntec.tohid.core.delivery.Mission
import ir.vil3ntec.tohid.data.repo.DeliveryRepository
import ir.vil3ntec.tohid.data.repo.DeliveryWire
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  «اصلاحِ نشانی» پس از ناموفق (DLV-09) — کارِ بعدی کنارِ «تلاشِ دوباره» و «برگشت».
 *  نشانیِ سفارش عوض می‌شود و کارتِ پیک نشانیِ تازه را با برچسبِ «دکان نشانی را
 *  اصلاح کرد» می‌بیند؛ وضعیت عوض نمی‌شود (پیک خودش دوباره راه می‌افتد).
 *
 *  ⛔ با نسخهٔ همان مأموریتی که کارمند دید (`expectedVersion`): اگر میانِ کار جای
 *     دیگری عوض شده باشد، سرور ۴۰۹ می‌دهد و میز تازه می‌شود، نه نوشتن روی وضعِ دیگر.
 *  ⛔ کلیدِ یکتایی همان فرم (`OnceKey`): پاسخِ گم‌شده با دوباره زدن همان کار است.
 */
@Composable
internal fun AddressFixDialog(
  m: Mission,
  repo: DeliveryRepository,
  onDismiss: () -> Unit,
  onDone: (String) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  var landmark by remember { mutableStateOf("") }
  var area by remember { mutableStateOf("") }
  var gateColor by remember { mutableStateOf("") }
  var text by remember { mutableStateOf("") }
  var keepPin by remember { mutableStateOf(true) }
  var busy by remember { mutableStateOf(false) }
  var err by remember { mutableStateOf<String?>(null) }
  val keys = remember { DeliveryRepository.OnceKey() }
  //  همان قاعدهٔ سرور (`orders.parseAddress`): ناحیه و رنگِ دروازه جای نشانی نیستند
  val valid = landmark.isNotBlank() || text.isNotBlank()

  fun send() {
    busy = true
    err = null
    val body = DeliveryWire.addressJson(landmark, area, gateColor, text, keepPin, m.version)
    scope.launch {
      repo.fixAddress(m.id, body, keys.of(body))
        .onSuccess { onDone("سفارش #${m.orderNo}: نشانی اصلاح شد؛ پیک نشانیِ تازه را می‌بیند") }
        .onFailure { f ->
          if (f.code == "assignment_changed") onDone("این مأموریت همین حالا جای دیگری عوض شد؛ میز تازه شد.")
          else err = f.userMessage
        }
      busy = false
    }
  }

  TohidBottomSheet(title = "${DeskActions.label(DeskActions.Act.FIX_ADDRESS)} — سفارش #${m.orderNo}", onDismiss = onDismiss) {
    SheetBody {
      Text(
        "${DeliveryText.mode(m.mode)} · ${DeliveryText.status(m.status)}" +
          (m.statusReason.takeIf { it.isNotEmpty() }?.let { " · ${DeliveryText.reason(it)}" } ?: ""),
        style = MaterialTheme.typography.bodySmall, color = c.muted,
      )
      Text(
        "نشانیِ درست را از مشتری بپرسید. پیک اول نشانه را می‌بیند، بعد ناحیه و رنگِ دروازه.",
        style = MaterialTheme.typography.bodySmall, color = c.text,
      )
      TohidTextField(landmark, { landmark = it.take(200) }, label = "نشانهٔ نزدیک", placeholder = "مثلاً «روبه‌روی نانوایی»")
      TohidTextField(area, { area = it.take(80) }, label = "ناحیه (اختیاری)")
      TohidTextField(gateColor, { gateColor = it.take(40) }, label = "رنگِ دروازه (اختیاری)")
      TohidTextField(text, { text = it.take(300) }, label = "نشانی (اختیاری)", singleLine = false)
      Row(verticalAlignment = Alignment.CenterVertically) {
        Checkbox(checked = keepPin, onCheckedChange = { keepPin = it })
        Text(
          if (keepPin) "پینِ مشتری بماند" else "پینِ قبلی پاک شود (پین غلط بود)",
          style = MaterialTheme.typography.bodySmall, color = c.text,
        )
      }
      if (!valid) Text("نشانه یا نشانی لازم است.", style = MaterialTheme.typography.bodySmall, color = c.muted)
      ErrorLine(err)
      TohidButton(
        DeskActions.label(DeskActions.Act.FIX_ADDRESS), onClick = { send() }, busy = busy, enabled = valid && !busy,
        modifier = Modifier.fillMaxWidth(),
      )
    }
  }
}
