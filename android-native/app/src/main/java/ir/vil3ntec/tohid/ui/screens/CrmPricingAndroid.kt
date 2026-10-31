package ir.vil3ntec.tohid.ui.screens

import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import ir.vil3ntec.tohid.core.i18n.StrFields
import ir.vil3ntec.tohid.data.CrmPricingForm
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopPolicy
import ir.vil3ntec.tohid.data.ShopStore
import kotlinx.coroutines.launch

/**
 *  CRM-08 / CRM-12 — دکمهٔ «گروه، لیستِ قیمت و رضایتِ تبلیغ» در پروندهٔ مشتری (گوشی و کامپیوتر).
 *
 *  ── پرچم ─────────────────────────────────────────────────────────────
 *  فقط با «گزارش‌های مشتری» روشن (`ShopPolicy.crmReports`، تنظیمِ همین دستگاه، **پیش‌فرض خاموش**) —
 *  همان کلیدی که شمارهٔ دوم و برچسب را باز می‌کند. خاموش ⇒ هیچ چیز کشیده نمی‌شود.
 *
 *  ── نوشتن ────────────────────────────────────────────────────────────
 *  ⛔ روی دفترِ همین لحظهٔ دیسک (`ShopStore.update`)، نه `d` ِ صفحه: ردیفی که Pull در این فاصله
 *  آورده گم نمی‌شود. ردِ هر کدام از دو گام ⇒ هیچ نوشتن و پیامِ همان رد.
 */
@Composable
fun CrmPricingButton(store: ShopStore, d: ShopData, debtorId: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { ShopPolicy.crmReports(context) }
  if (!on) return
  val debtor = d.debtors.find { it.id == debtorId } ?: return
  val scope = rememberCoroutineScope()
  var open by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { open = true }, modifier = modifier) { Text(crmPricingSummary(debtor)) }
  if (!open) return
  CrmPricingDialog(d, debtor, onDismiss = { open = false }) { input ->
    scope.launch {
      var failed: String? = null
      val wrote = store.update { cur ->
        when (val r = CrmPricingForm.save(cur, debtorId, input)) {
          is DebtorEngine.Result.Failed -> { failed = r.message; null }
          is DebtorEngine.Result.Ok -> r.data.takeIf { it !== cur }
        }
      }
      when {
        failed != null -> onMessage(failed!!)
        wrote == false -> onMessage(StrFields.crmNotSaved)
        else -> { open = false; onMessage(StrFields.crmSaved) }
      }
    }
  }
}
