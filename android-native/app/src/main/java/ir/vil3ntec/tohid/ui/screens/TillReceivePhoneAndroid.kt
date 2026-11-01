package ir.vil3ntec.tohid.ui.screens

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material3.Icon
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
import androidx.compose.ui.unit.dp
import ir.vil3ntec.tohid.core.i18n.Str
import ir.vil3ntec.tohid.data.ShopData
import ir.vil3ntec.tohid.data.ShopStore
import ir.vil3ntec.tohid.money
import kotlinx.coroutines.launch

/**
 *  POS-39 — «دریافت از مشتری» در صندوقِ **گوشی**؛ همان جریانِ F7 ِ صندوقِ کامپیوتر (`DeskSale`) و همان
 *  تنظیمِ همین دستگاه (`TillReceivePref`، پیش‌فرض خاموش ⇒ هیچ دکمه‌ای نیست). فایلِ مرزیِ پلتفرم.
 *
 *  مشتریِ همین فاکتور، وگرنه انتخاب (`DebtorPicker`) ⇐ `TillReceiveDialog` پشتِ `VipGate` ِ قرض‌داران ⇐
 *  ⛔ ثبت فقط با `TillReceivePref.record` (دفترِ همین لحظهٔ دیسک؛ کلیدِ یکتا = شناسهٔ ردیفی که با زدنِ
 *  دکمه ساخته می‌شود ⇒ دو ضربه یک ردیف) ⇐ رسیدِ همان دفترِ قرض (`DebtReceiptDialog`). سبد دست نمی‌خورد.
 */
@Composable
internal fun TillReceivePhone(d: ShopData, store: ShopStore, cartDebtorId: String?, toast: (String) -> Unit) {
  val context = LocalContext.current
  val on = remember { ir.vil3ntec.tohid.data.TillReceivePref.on(context) }
  if (!on) return
  val scope = rememberCoroutineScope()
  var pick by remember { mutableStateOf(false) }
  var forId by remember { mutableStateOf<String?>(null) }
  var key by remember { mutableStateOf("") }
  var busy by remember { mutableStateOf(false) }
  var receiptOf by remember { mutableStateOf<Triple<ShopData, String, String>?>(null) }

  OutlinedButton(
    onClick = { key = newId(); if (cartDebtorId != null) forId = cartDebtorId else pick = true },
    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
  ) {
    Icon(Icons.Filled.Payments, contentDescription = null)
    Spacer(Modifier.width(6.dp))
    Text("دریافتِ وجه یا قسط از مشتری")
  }

  if (pick) {
    DebtorPicker(d = d, selected = null, onClose = { pick = false }, onPick = { id -> pick = false; forId = id })
  }
  forId?.let { id ->
    val name = d.debtors.find { it.id == id }?.name
    if (name == null) forId = null else {
      TillReceiveDialog(
        name = name,
        balanceMinor = ir.vil3ntec.tohid.data.TillReceiveFlow.balanceMinor(d, id),
        busy = busy,
        gate = { content -> VipGate(Str.debtors) { content() } },
        onDismiss = { forId = null },
        onSubmit = { amount, notes, prepay ->
          busy = true
          val k = key
          scope.launch {
            val r = ir.vil3ntec.tohid.data.TillReceivePref.record(store, id, amount, notes, k, prepay)
            busy = false
            r.show(toast, "دریافتِ ${money(ir.vil3ntec.tohid.data.PayFlow.major(amount ?: 0L))} ؋ از «$name» ثبت شد") {
              forId = null; receiptOf = Triple(store.data.value, id, k)
            }
          }
        },
      )
    }
  }
  receiptOf?.let { (data, debtorId, txId) ->
    DebtReceiptDialog(data, debtorId, txId, onDismiss = { receiptOf = null })
  }
}
