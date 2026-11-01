package ir.vil3ntec.tohid.ui.components.pos

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalShipping
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.core.i18n.StrDeliver
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.core.model.OrderDto
import ir.vil3ntec.tohid.core.orders.OrderText
import ir.vil3ntec.tohid.core.pos.DeliverLater
import ir.vil3ntec.tohid.data.repo.OrdersRepository
import ir.vil3ntec.tohid.ui.Platform
import ir.vil3ntec.tohid.ui.screens.AdaptiveSheet
import ir.vil3ntec.tohid.ui.screens.SegmentedChoice
import ir.vil3ntec.tohid.ui.screens.SettingsSection
import ir.vil3ntec.tohid.ui.screens.TohidButton
import ir.vil3ntec.tohid.ui.screens.TohidSecondaryButton
import ir.vil3ntec.tohid.ui.screens.TohidTextField
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/** متنِ هر رد با زبانِ صفحه */
fun deliverLaterProblem(e: DeliverLater.Result.Error): String = when (e.problem) {
  DeliverLater.Problem.EMPTY -> StrDeliver.emptyCart
  DeliverLater.Problem.TOO_MANY_LINES -> StrDeliver.tooManyLines
  DeliverLater.Problem.NAME -> StrDeliver.needName
  DeliverLater.Problem.PHONE -> StrDeliver.needPhone
  DeliverLater.Problem.ADDRESS -> StrDeliver.needAddress
  DeliverLater.Problem.FEE -> StrDeliver.badFee
  DeliverLater.Problem.LINE -> StrDeliver.badLine(e.line)
}

/**
 *  POS-38 — پنجرهٔ «تحویلِ بعدی»: مشتری، نشانی و هزینهٔ ارسال ⇐ سفارشِ ارسال از قلم‌های همین سبد
 *  (`DeliverLater.build`). کلیدِ یکتایی یک بار برای همین پنجره: «ثبت» ِ دوباره (قطعِ نت، دو بار زدن)
 *  همان سفارش است. پس از ثبت [onCreated] سبد را خالی می‌کند — هیچ فروشی این‌جا زده نمی‌شود.
 */
@Composable
fun DeliverLaterSheet(
  repo: OrdersRepository,
  lines: List<DeliverLater.Line>,
  onClose: () -> Unit,
  onCreated: (OrderDto) -> Unit,
) {
  val c = Shop.colors
  val scope = rememberCoroutineScope()
  val key = remember { OrdersRepository.newKey() }
  var form by remember { mutableStateOf(DeliverLater.Contact()) }
  var error by remember { mutableStateOf<String?>(null) }
  var busy by remember { mutableStateOf(false) }
  val decimal = KeyboardOptions(keyboardType = KeyboardType.Decimal)
  val preview = DeliverLater.build(lines, form)
  val subtotal = lines.sumOf { if (it.qtyMilli in 1..DeliverLater.MAX_QTY_MILLI && it.unitPriceMinor in 0..DeliverLater.MAX_PRICE_MINOR) DeliverLater.lineTotalMinor(it.qtyMilli, it.unitPriceMinor) else 0L }

  fun submit() {
    if (busy) return
    when (val r = DeliverLater.build(lines, form)) {
      is DeliverLater.Result.Error -> error = deliverLaterProblem(r)
      is DeliverLater.Result.Ok -> {
        busy = true
        error = null
        scope.launch {
          repo.create(key, r.body)
            .onSuccess { onCreated(it) }
            .onFailure { f -> error = if (f.retryable) StrDeliver.retry else f.userMessage }
          busy = false
        }
      }
    }
  }

  AdaptiveSheet(onDismissRequest = { if (!busy) onClose() }, containerColor = c.surfaceSolid) {
    val scroll = if (Platform.DESKTOP) Modifier else Modifier.verticalScroll(rememberScrollState())
    Column(Modifier.fillMaxWidth().then(scroll).padding(horizontal = 20.dp).padding(bottom = 24.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.LocalShipping, contentDescription = null, tint = c.primary)
        Spacer(Modifier.width(8.dp))
        Text(StrDeliver.title, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = c.text)
        IconButton(onClick = onClose, enabled = !busy) { Icon(Icons.Filled.Close, contentDescription = Str.close, tint = c.muted) }
      }
      Text(StrDeliver.explain, style = MaterialTheme.typography.bodySmall, color = c.muted)
      Spacer(Modifier.height(10.dp))
      TohidTextField(form.name, { form = form.copy(name = it.take(DeliverLater.MAX_NAME)) }, label = StrDeliver.name)
      TohidTextField(form.phone, { form = form.copy(phone = it.take(DeliverLater.MAX_PHONE)) }, label = StrDeliver.phone,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
      TohidTextField(form.addressText, { form = form.copy(addressText = it.take(DeliverLater.MAX_ADDRESS)) }, label = StrDeliver.address, singleLine = false)
      TohidTextField(form.landmark, { form = form.copy(landmark = it.take(DeliverLater.MAX_LANDMARK)) }, label = StrDeliver.landmark)
      TohidTextField(form.fee, { form = form.copy(fee = it.take(14)) }, label = StrDeliver.fee, keyboardOptions = decimal)
      TohidTextField(form.note, { form = form.copy(note = it.take(DeliverLater.MAX_NOTE)) }, label = StrDeliver.noteField, singleLine = false)
      Spacer(Modifier.height(8.dp))
      Text(StrDeliver.items(lines.size), style = MaterialTheme.typography.bodyMedium, color = c.text)
      Text(
        StrDeliver.total(OrderText.money((preview as? DeliverLater.Result.Ok)?.totalMinor ?: subtotal)),
        style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = c.text,
      )
      if (error != null) Text(error ?: "", color = c.danger, style = MaterialTheme.typography.bodySmall)
      Spacer(Modifier.height(10.dp))
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        TohidSecondaryButton(Str.cancel, onClick = onClose, modifier = Modifier.weight(1f), enabled = !busy)
        TohidButton(StrDeliver.submit, onClick = { submit() }, modifier = Modifier.weight(1.5f), busy = busy, enabled = !busy && lines.isNotEmpty())
      }
    }
  }
}

/** بخشِ تنظیمات — خودش نمی‌خواند و نمی‌نویسد: مقدار و نوشتن از صدازننده (`DeliverLaterPrefs`) */
@Composable
fun DeliverLaterSettings(on: Boolean, onChange: (Boolean) -> Unit) {
  SettingsSection(
    icon = Icons.Filled.LocalShipping,
    title = StrDeliver.section,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.success else Shop.colors.muted,
  ) {
    Text(StrDeliver.note, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { onChange(it == "on") },
    )
  }
}
