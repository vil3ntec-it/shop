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
import ir.vil3ntec.tohid.core.i18n.StrCustomerPrices
import ir.vil3ntec.tohid.core.sale.CustomerPrice
import ir.vil3ntec.tohid.data.CustomerPrices
import ir.vil3ntec.tohid.data.DebtorEngine
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopRole
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.todayIso
import ir.vil3ntec.tohid.ui.components.pos.CustomerPricePrefs
import kotlinx.coroutines.launch

/**
 *  POS-18 — دکمهٔ «قیمتِ خاصِ این مشتری» در پروندهٔ مشتری: گوشیِ نسخهٔ ۳ (`DebtorFileExtras`) و کامپیوتر
 *  (`DeskCustomers`)، همان یک جزء.
 *
 *  ── پرچم ── فقط با «قیمتِ خاصِ مشتری» روشن (`CustomerPricePrefs`، پیش‌فرض **خاموش**)؛ خاموش ⇒ هیچ چیز کشیده نمی‌شود.
 *  ── مجوز ── `CustomerPrice.canEdit` (همان کسی که قیمت می‌گذارد)؛ بی اجازه فهرست فقط خواندنی است و نوشتن هم رد می‌شود.
 *  ── نوشتن ── ⛔ روی دفترِ همین لحظهٔ دیسک (`ShopStore.update`)، نه `d` ِ صفحه — همان راهِ `CrmPricingButton`؛ هر
 *  تغییر یک ردیفِ سابقه با نامِ کاربر (`CustomerPrices`).
 */
@Composable
fun CustomerPricesButton(store: ShopStore, d: ShopData, debtorId: String, onMessage: (String) -> Unit, modifier: Modifier = Modifier) {
  val context = LocalContext.current
  val on = remember { CustomerPricePrefs.on(context) }
  if (!on) return
  val debtor = d.debtors.find { it.id == debtorId } ?: return
  val allowed = remember { CustomerPrice.canEdit(ShopRole.current(context), ShopRole.layers(context)) }
  val by = remember { runCatching { ir.vil3ntec.tohid.sync.SyncStore(context).accountName }.getOrDefault("") }
  val scope = rememberCoroutineScope()
  var open by remember { mutableStateOf(false) }
  OutlinedButton(onClick = { open = true }, modifier = modifier) { Text(StrCustomerPrices.summary(debtor.specialPrices.orEmpty().size)) }
  if (!open) return
  val today = todayIso()

  fun write(done: String, change: (ShopData) -> DebtorEngine.Result) {
    scope.launch {
      var failed: String? = null
      val wrote = store.update { cur ->
        when (val r = change(cur)) {
          is DebtorEngine.Result.Failed -> { failed = r.message; null }
          is DebtorEngine.Result.Ok -> r.data.takeIf { it !== cur }
        }
      }
      when {
        failed != null -> onMessage(failed!!)
        wrote == false -> onMessage(StrCustomerPrices.notSaved)
        else -> onMessage(done)
      }
    }
  }

  CustomerPricesDialog(
    d, debtor, allowed, today,
    onDismiss = { open = false },
    onSave = { replacing, entry ->
      write(StrCustomerPrices.saved) { cur -> CustomerPrices.save(cur, debtorId, replacing, entry, allowed, by, today, System.currentTimeMillis(), ::newId) }
    },
    onRemove = { productId ->
      write(StrCustomerPrices.removed) { cur -> CustomerPrices.remove(cur, debtorId, productId, allowed, by, today, System.currentTimeMillis(), ::newId) }
    },
  )
}
