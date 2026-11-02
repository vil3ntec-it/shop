package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import ir.vil3ntec.tohid.core.i18n.StrCustomerCard
import ir.vil3ntec.tohid.core.i18n.StrShell
import ir.vil3ntec.tohid.data.CustomerCardPref
import ir.vil3ntec.tohid.data.CustomerCards
import ir.vil3ntec.tohid.data.CustomerCrm
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.print.CustomerCardPrint
import ir.vil3ntec.tohid.ui.theme.Shop
import kotlinx.coroutines.launch

/**
 *  CRM-16 — بخشِ «کارت یا بارکدِ مشتری» در تنظیماتِ گوشی و کامپیوتر (`CustomerCardPref`، پیش‌فرض خاموش).
 *  عنوانش در فهرستِ `DeskSettingsLegacy` ِ `AppRootLegacy` هم هست.
 */
@Composable
fun CustomerCardSettingsWired(context: android.content.Context) {
  var on by remember { mutableStateOf(CustomerCardPref.on(context)) }
  SettingsSection(
    icon = Icons.Filled.CreditCard,
    title = StrCustomerCard.settingsTitle,
    subtitle = if (on) StrShell.switchOn else StrShell.switchOff,
    tint = if (on) Shop.colors.primary else Shop.colors.muted,
  ) {
    Text(StrCustomerCard.settingsNote, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Text(StrShell.thisDeviceOnly, style = MaterialTheme.typography.labelSmall, color = Shop.colors.muted2)
    Spacer(Modifier.height(8.dp))
    SegmentedChoice(
      options = listOf("off" to StrShell.switchOff, "on" to StrShell.switchOn),
      selected = if (on) "on" else "off",
      onSelect = { on = it == "on"; CustomerCardPref.setOn(context, on) },
    )
  }
}

/**
 *  دکمهٔ «کارتِ مشتری» در پروندهٔ مشتری (گوشی و کامپیوتر، کنارِ `CrmPricingButton`): دیدنِ کد، ساختنِ کدِ
 *  تازه، ثبتِ کدِ کارتِ چاپ‌شده، برداشتن و چاپ. خاموش ⇒ هیچ چیز کشیده نمی‌شود.
 *  ⛔ نوشتن روی دفترِ همین لحظهٔ دیسک (`ShopStore.update`)، نه `d` ِ صفحه — همان راهِ `CrmPricingButton`.
 */
@Composable
fun CustomerCardButton(store: ShopStore, d: ShopData, debtorId: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { CustomerCardPref.on(context) }
  if (!on) return
  val debtor = d.debtors.find { it.id == debtorId } ?: return
  if (!CustomerCrm.isActive(debtor)) return
  val scope = rememberCoroutineScope()
  var open by remember { mutableStateOf(false) }
  var manual by remember(debtorId) { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { open = true }, modifier = modifier) {
    Text(debtor.cardCode?.let { StrCustomerCard.current(it) } ?: StrCustomerCard.title)
  }
  if (!open) return

  fun write(done: String, change: (ShopData) -> DebtorEngine.Result) {
    if (busy) return
    busy = true
    scope.launch {
      var failed: String? = null
      val wrote = store.update { cur ->
        when (val r = change(cur)) {
          is DebtorEngine.Result.Failed -> { failed = r.message; null }
          is DebtorEngine.Result.Ok -> r.data.takeIf { it !== cur }
        }
      }
      busy = false
      when {
        failed != null -> onMessage(failed!!)
        wrote == false -> onMessage(StrCustomerCard.notSaved)
        else -> { manual = ""; onMessage(done) }
      }
    }
  }

  AlertDialog(
    onDismissRequest = { open = false },
    containerColor = Shop.colors.surfaceSolid,
    title = { Text("${StrCustomerCard.title} — ${debtor.name}", color = Shop.colors.text) },
    text = {
      Column {
        Text(
          debtor.cardCode ?: StrCustomerCard.none,
          style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Shop.colors.text,
        )
        Spacer(Modifier.height(8.dp))
        OutlinedButton(enabled = !busy, onClick = { write(StrCustomerCard.saved) { cur -> CustomerCards.assign(cur, debtorId, null) } }, modifier = Modifier.fillMaxWidth()) {
          Text(StrCustomerCard.generate)
        }
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
          value = manual, onValueChange = { manual = it.take(24) }, singleLine = true,
          label = { Text(StrCustomerCard.manual) },
          keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
          modifier = Modifier.fillMaxWidth(),
        )
        TextButton(enabled = !busy && manual.isNotBlank(), onClick = { val m = manual; write(StrCustomerCard.saved) { cur -> CustomerCards.assign(cur, debtorId, m) } }) {
          Text(StrCustomerCard.useManual)
        }
        val code = debtor.cardCode
        if (code != null) {
          TextButton(enabled = !busy, onClick = { scope.launch { onMessage(CustomerCardPrint.print(context, debtor.name, code)) } }) { Text(StrCustomerCard.print) }
          TextButton(enabled = !busy, onClick = { write(StrCustomerCard.removed) { cur -> CustomerCards.remove(cur, debtorId) } }) {
            Text(StrCustomerCard.remove, color = Shop.colors.danger)
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { open = false }) { Text(StrCustomerCard.close) } },
  )
}
