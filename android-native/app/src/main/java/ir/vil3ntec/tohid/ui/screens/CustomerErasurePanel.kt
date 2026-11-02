package ir.vil3ntec.tohid.ui.screens

// i18n: سه‌زبانه

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.crm.CustomerErasure
import ir.vil3ntec.tohid.core.i18n.StrErasure
import ir.vil3ntec.tohid.core.net.ApiClient
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.repo.Backend
import ir.vil3ntec.tohid.data.repo.CustomerErasureRepository
import ir.vil3ntec.tohid.ui.theme.Shop

/*
 *  SEC-10 — «حذفِ دادهٔ این مشتری» در پروندهٔ مشتری (رابطِ امروز: `DebtorsLegacy` گوشی و `DeskCustomers` کامپیوتر).
 *  فقط صاحبِ واردشده، شمارهٔ قابلِ حذف و پاسخِ روشنِ سرور. حالت فقط در `CustomerErasureController`.
 */

/** درِ سرور و حالِ حذف برای صاحبِ واردشده؛ وگرنه null (هیچ دکمه‌ای) */
@Composable
internal fun rememberErasureApi(): ApiClient? {
  val context = LocalContext.current
  val owner = ShopRole.current(context) == ShopRole.OWNER && Backend.isReady(context)
  return remember(context, owner) { if (owner) Backend.api(context) else null }
}

/** دکمهٔ «حذفِ دادهٔ این مشتری» و برگه‌اش؛ [phone] شمارهٔ همین مشتری در دفتر */
@Composable
fun CustomerErasureButton(phone: String?, modifier: Modifier = Modifier) {
  val api = rememberErasureApi() ?: return
  if (phone == null || !CustomerErasure.usable(phone)) return
  val src = remember(api) { CustomerErasureRepository(api) }
  LaunchedEffect(src) { CustomerErasureLive.ensure(src) }
  if (!CustomerErasureLive.gate.visible) return
  //  حالت بالای برگه: بستنِ برگه درخواستِ در جریان را نمی‌بُرد
  val scope = rememberCoroutineScope()
  val c = remember(src, phone) { CustomerErasureController(src, phone, { api.reauth.confirm() }, scope) }
  var open by remember(phone) { mutableStateOf(false) }
  OutlinedButton(onClick = { c.reset(); open = true }, modifier = modifier) { Text(StrErasure.action, color = Shop.colors.danger) }
  if (open) CustomerErasureSheet(c, onClose = { if (!c.busy) { c.reset(); open = false } })
}

private val DIGITS = KeyboardOptions(keyboardType = KeyboardType.Number)

@Composable
private fun CustomerErasureSheet(c: CustomerErasureController, onClose: () -> Unit) {
  LaunchedEffect(c) { if (c.step is CustomerErasure.Step.Idle) c.preview() }
  val colors = Shop.colors
  TohidBottomSheet(title = StrErasure.action, onDismiss = onClose) {
    SheetBody {
      when (val s = c.step) {
        is CustomerErasure.Step.Idle, is CustomerErasure.Step.Previewing -> {
          Text(StrErasure.previewing, color = colors.muted)
          TohidLoadingState(rows = 2)
        }
        is CustomerErasure.Step.Preview -> {
          Text(StrErasure.previewTitle, fontWeight = FontWeight.Bold, color = colors.text)
          Text(StrErasure.intro, style = MaterialTheme.typography.bodySmall, color = colors.text)
          PreviewResult(s.result)
          s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TohidDangerButton(StrErasure.proceed, onClick = { c.proceed() }, enabled = CustomerErasure.canProceed(s.result))
            TohidSecondaryButton(StrErasure.cancel, onClick = onClose)
          }
        }
        is CustomerErasure.Step.Confirm -> {
          Text(StrErasure.confirmTitle, fontWeight = FontWeight.Bold, color = colors.danger)
          Text(StrErasure.intro, style = MaterialTheme.typography.bodySmall, color = colors.text)
          Text(StrErasure.total(s.preview.total.toString()), color = colors.text)
          Text(StrErasure.confirmBody, color = colors.text)
          TohidTextField(s.typed, c::type, label = StrErasure.confirmLabel, keyboardOptions = DIGITS)
          Text(StrErasure.reauthHint, style = MaterialTheme.typography.labelSmall, color = colors.muted2)
          s.note?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.danger) }
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TohidDangerButton(StrErasure.eraseNow, onClick = { c.erase() }, enabled = c.confirmOk)
            TohidSecondaryButton(StrErasure.cancel, onClick = onClose)
          }
        }
        is CustomerErasure.Step.Reauth, is CustomerErasure.Step.Erasing -> {
          Text(StrErasure.erasing, color = colors.muted)
          TohidLoadingState(rows = 2)
        }
        is CustomerErasure.Step.Done -> {
          Text(StrErasure.doneTitle, fontWeight = FontWeight.Bold, color = colors.success)
          Text(StrErasure.done(s.result.total.toString()), color = colors.text)
          for (l in erasureCountLines(s.result)) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
          Text(StrErasure.keptTitle, style = MaterialTheme.typography.labelLarge, color = colors.muted)
          for (l in erasureKeptLines) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
          TohidButton(StrErasure.close, onClick = onClose)
        }
        is CustomerErasure.Step.Failed -> {
          Text(s.message, color = colors.danger)
          Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            TohidSecondaryButton(StrErasure.proceed, onClick = { c.preview() })
            TohidSecondaryButton(StrErasure.close, onClick = onClose)
          }
        }
      }
    }
  }
}

/** پیش‌نمایش: سفارشِ باز، هیچ، یا شمارش‌ها؛ و «چه می‌ماند» */
@Composable
private fun PreviewResult(r: CustomerErasure.Result) {
  val colors = Shop.colors
  when {
    r.openOrders > 0 -> Text(StrErasure.openOrders(r.openOrders.toString()), color = colors.danger)
    r.total == 0 -> Text(StrErasure.nothing, color = colors.muted)
    else -> {
      for (l in erasureCountLines(r)) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
      Text(StrErasure.total(r.total.toString()), fontWeight = FontWeight.SemiBold, color = colors.text)
    }
  }
  Text(StrErasure.keptTitle, style = MaterialTheme.typography.labelLarge, color = colors.muted)
  for (l in erasureKeptLines) Text(l, style = MaterialTheme.typography.bodySmall, color = colors.text)
}
